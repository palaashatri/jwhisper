package com.jwhisper.whisper;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.jwhisper.audio.FfmpegAudioDecoder;
import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.transcribe.TranscriptionJob;
import com.jwhisper.transcribe.TranscriptionListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WhisperOnnxEngine implements WhisperEngineAgent {
    private final OrtEnvironment environment;
    private final FfmpegAudioDecoder audioDecoder;
    private final WhisperProviderConfig providerConfig;
    private final AtomicBoolean canceled = new AtomicBoolean(false);

    private ModelDescriptor loadedModel;
    private Path loadedRoot;
    private OrtSession encoderSession;
    private OrtSession decoderSession;
    private String encoderInputName;
    private String decoderInputIdsName;
    private String decoderEncoderStatesName;
    private WhisperFeatureExtractor featureExtractor;
    private WhisperGenerationConfig generationConfig;
    private WhisperTokenizer tokenizer;
    private WhisperExecutionProvider activeProvider = WhisperExecutionProvider.CPU;

    public WhisperOnnxEngine() {
        this(WhisperProviderConfig.fromEnvironment());
    }

    WhisperOnnxEngine(WhisperProviderConfig providerConfig) {
        this.environment = OrtEnvironment.getEnvironment();
        this.audioDecoder = new FfmpegAudioDecoder();
        this.providerConfig = providerConfig;
    }

    @Override
    public synchronized void loadModel(ModelDescriptor descriptor, Path modelRoot) throws WhisperException {
        if (descriptor.equals(loadedModel) && modelRoot.equals(loadedRoot) && encoderSession != null && decoderSession != null) {
            return;
        }
        closeSessions();
        try {
            Path encoder = modelRoot.resolve("onnx/encoder_model.onnx");
            Path decoder = modelRoot.resolve("onnx/decoder_model.onnx");
            if (!Files.isRegularFile(encoder) || !Files.isRegularFile(decoder)) {
                throw new WhisperException("Model file is invalid. Delete and re-download.");
            }

            featureExtractor = new WhisperFeatureExtractor(
                    WhisperPreprocessorConfig.from(modelRoot.resolve("preprocessor_config.json"))
            );
            generationConfig = WhisperGenerationConfig.from(modelRoot.resolve("generation_config.json"));
            tokenizer = WhisperTokenizer.from(
                    modelRoot.resolve("tokenizer.json"),
                    modelRoot.resolve("tokenizer_config.json")
            );

            SessionBundle sessions = createSessions(encoder, decoder);
            encoderSession = sessions.encoderSession();
            decoderSession = sessions.decoderSession();
            activeProvider = sessions.provider();
            encoderInputName = chooseName(encoderSession.getInputNames(), "input_features", null);
            decoderInputIdsName = chooseName(decoderSession.getInputNames(), "input_ids", "input");
            decoderEncoderStatesName = chooseName(decoderSession.getInputNames(), "encoder_hidden_states", "encoder");
            loadedModel = descriptor;
            loadedRoot = modelRoot;
        } catch (OrtException e) {
            closeSessions();
            throw new WhisperException("Model file is invalid. Delete and re-download.", e);
        } catch (IOException e) {
            closeSessions();
            throw new WhisperException("Model file is invalid. Delete and re-download.", e);
        }
    }

    @Override
    public String transcribe(TranscriptionJob job, TranscriptionListener listener) throws WhisperException {
        canceled.set(false);
        loadModel(job.model(), job.modelRoot());
        try {
            listener.onStatus("Preparing audio...");
            listener.onProgress(0.02);
            float[] samples = audioDecoder.decodeToMono16k(job.audioJob().file());
            if (samples.length == 0) {
                throw new WhisperException("Something went wrong. Try another file.");
            }

            listener.onStatus("Transcribing with " + activeProvider.displayName() + "... this may take a moment.");
            int chunkSize = featureExtractor.maxSamples();
            int chunks = Math.max(1, (int) Math.ceil(samples.length / (double) chunkSize));
            StringBuilder transcript = new StringBuilder();
            for (int chunk = 0; chunk < chunks; chunk++) {
                checkCanceled();
                int from = chunk * chunkSize;
                int to = Math.min(samples.length, from + chunkSize);
                float[] audioChunk = Arrays.copyOfRange(samples, from, to);
                double chunkStart = 0.10 + (0.85 * chunk / chunks);
                double chunkEnd = 0.10 + (0.85 * (chunk + 1) / chunks);
                String text = transcribeChunk(audioChunk, listener, chunkStart, chunkEnd);
                if (!text.isBlank()) {
                    if (!transcript.isEmpty()) {
                        transcript.append(System.lineSeparator());
                    }
                    transcript.append(text);
                    listener.onTranscriptChunk(text);
                }
            }
            listener.onProgress(1.0);
            listener.onStatus("Done.");
            return transcript.toString().trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WhisperException("Something went wrong. Try another file.", e);
        } catch (IOException | OrtException e) {
            throw new WhisperException("Something went wrong. Try another file.", e);
        }
    }

    @Override
    public void cancel() {
        canceled.set(true);
    }

    @Override
    public synchronized void close() {
        closeSessions();
    }

    private String transcribeChunk(
            float[] samples,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd
    ) throws OrtException, WhisperException {
        float[][][] features = featureExtractor.extract(samples);
        float[][][] encoderHiddenStates = runEncoder(features);
        List<Long> tokens = new ArrayList<>(generationConfig.initialTokens());
        int initialSize = tokens.size();
        int maxLength = generationConfig.maxLength();

        try (OnnxTensor encoderHiddenTensor = OnnxTensor.createTensor(environment, encoderHiddenStates)) {
            while (tokens.size() < maxLength) {
                checkCanceled();
                float[] logits = runDecoder(tokens, encoderHiddenTensor);
                int generated = tokens.size() - initialSize;
                int nextToken = chooseNextToken(logits, generated == 0);
                if (nextToken == generationConfig.eosTokenId()) {
                    break;
                }
                tokens.add((long) nextToken);
                if (generated % 4 == 0) {
                    double fraction = Math.min(1.0, tokens.size() / (double) maxLength);
                    listener.onProgress(progressStart + (progressEnd - progressStart) * fraction);
                }
            }
        }

        return tokenizer.decode(tokens.subList(initialSize, tokens.size()));
    }

    private float[][][] runEncoder(float[][][] features) throws OrtException, WhisperException {
        try (OnnxTensor input = OnnxTensor.createTensor(environment, features);
             OrtSession.Result result = encoderSession.run(Map.of(encoderInputName, input))) {
            Object value = result.get(0).getValue();
            if (value instanceof float[][][] hiddenStates) {
                return hiddenStates;
            }
            throw new WhisperException("Model file is invalid. Delete and re-download.");
        }
    }

    private float[] runDecoder(List<Long> tokens, OnnxTensor encoderHiddenTensor) throws OrtException, WhisperException {
        long[][] inputIds = new long[1][tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            inputIds[0][i] = tokens.get(i);
        }
        try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, inputIds);
             OrtSession.Result result = decoderSession.run(decoderInputs(inputIdsTensor, encoderHiddenTensor))) {
            Object value = result.get(0).getValue();
            if (value instanceof float[][][] logits && logits.length > 0 && logits[0].length > 0) {
                return logits[0][logits[0].length - 1];
            }
            throw new WhisperException("Model file is invalid. Delete and re-download.");
        }
    }

    private Map<String, OnnxTensor> decoderInputs(OnnxTensor inputIdsTensor, OnnxTensor encoderHiddenTensor) {
        Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
        inputs.put(decoderInputIdsName, inputIdsTensor);
        inputs.put(decoderEncoderStatesName, encoderHiddenTensor);
        return inputs;
    }

    private SessionBundle createSessions(Path encoder, Path decoder) throws OrtException, WhisperException {
        WhisperExecutionProvider requested = providerConfig.resolve(OrtEnvironment.getAvailableProviders());
        try {
            return openSessionBundle(encoder, decoder, requested);
        } catch (OrtException e) {
            if (requested != WhisperExecutionProvider.CPU && providerConfig.allowCpuFallback()) {
                return openSessionBundle(encoder, decoder, WhisperExecutionProvider.CPU);
            }
            throw new WhisperException(requested.displayName() + " provider failed. Check the runtime build and drivers.", e);
        }
    }

    private SessionBundle openSessionBundle(
            Path encoder,
            Path decoder,
            WhisperExecutionProvider provider
    ) throws OrtException {
        OrtSession openedEncoder = null;
        try {
            openedEncoder = createSession(encoder, provider);
            OrtSession openedDecoder = createSession(decoder, provider);
            return new SessionBundle(openedEncoder, openedDecoder, provider);
        } catch (OrtException e) {
            if (openedEncoder != null) {
                openedEncoder.close();
            }
            throw e;
        }
    }

    private OrtSession createSession(Path model, WhisperExecutionProvider provider) throws OrtException {
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            provider.applyTo(options, providerConfig.deviceId());
            return environment.createSession(model.toString(), options);
        }
    }

    private int chooseNextToken(float[] logits, boolean firstGeneratedToken) {
        for (Integer tokenId : generationConfig.suppressTokens()) {
            if (tokenId >= 0 && tokenId < logits.length) {
                logits[tokenId] = Float.NEGATIVE_INFINITY;
            }
        }
        if (firstGeneratedToken) {
            for (Integer tokenId : generationConfig.beginSuppressTokens()) {
                if (tokenId >= 0 && tokenId < logits.length) {
                    logits[tokenId] = Float.NEGATIVE_INFINITY;
                }
            }
        }

        int best = 0;
        float bestValue = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) {
            if (logits[i] > bestValue) {
                bestValue = logits[i];
                best = i;
            }
        }
        return best;
    }

    private void checkCanceled() throws WhisperException {
        if (canceled.get()) {
            throw new WhisperException("Canceled.");
        }
    }

    private String chooseName(Set<String> names, String preferred, String contains) throws WhisperException {
        if (names.contains(preferred)) {
            return preferred;
        }
        if (contains != null) {
            for (String name : names) {
                if (name.toLowerCase().contains(contains.toLowerCase())) {
                    return name;
                }
            }
        }
        return names.stream().findFirst().orElseThrow(() -> new WhisperException("Model file is invalid. Delete and re-download."));
    }

    private synchronized void closeSessions() {
        if (encoderSession != null) {
            try {
                encoderSession.close();
            } catch (OrtException ignored) {
                // Cleanup should not replace the user-facing transcription error.
            }
            encoderSession = null;
        }
        if (decoderSession != null) {
            try {
                decoderSession.close();
            } catch (OrtException ignored) {
                // Cleanup should not replace the user-facing transcription error.
            }
            decoderSession = null;
        }
        loadedModel = null;
        loadedRoot = null;
        activeProvider = WhisperExecutionProvider.CPU;
    }

    private record SessionBundle(
            OrtSession encoderSession,
            OrtSession decoderSession,
            WhisperExecutionProvider provider
    ) {
    }
}
