package com.jwhisper.whisper;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxTensorLike;
import ai.onnxruntime.OnnxValue;
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
import java.util.concurrent.atomic.AtomicReference;

public final class WhisperOnnxEngine implements WhisperEngineAgent {
    private final OrtEnvironment environment;
    private final FfmpegAudioDecoder audioDecoder;
    private final WhisperProviderConfig providerConfig;
    private final AtomicBoolean canceled = new AtomicBoolean(false);
    private final AtomicReference<OrtSession.RunOptions> activeRunOptions = new AtomicReference<>();

    private ModelDescriptor loadedModel;
    private Path loadedRoot;
    private OrtSession encoderSession;
    private OrtSession decoderSession;
    private OrtSession decoderWithPastSession;
    private String encoderInputName;
    private String decoderInputIdsName;
    private String decoderEncoderStatesName;
    private String decoderWithPastInputIdsName;
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
            Path decoderWithPast = modelRoot.resolve("onnx/decoder_with_past_model.onnx");
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

            SessionBundle sessions = createSessions(
                    encoder,
                    decoder,
                    Files.isRegularFile(decoderWithPast) ? decoderWithPast : null
            );
            encoderSession = sessions.encoderSession();
            decoderSession = sessions.decoderSession();
            decoderWithPastSession = sessions.decoderWithPastSession();
            activeProvider = sessions.provider();
            encoderInputName = chooseName(encoderSession.getInputNames(), "input_features", null);
            decoderInputIdsName = chooseName(decoderSession.getInputNames(), "input_ids", "input");
            decoderEncoderStatesName = chooseName(decoderSession.getInputNames(), "encoder_hidden_states", "encoder");
            if (decoderWithPastSession != null) {
                decoderWithPastInputIdsName = chooseName(
                        decoderWithPastSession.getInputNames(),
                        "input_ids",
                        "input"
                );
            }
            loadedModel = descriptor;
            loadedRoot = modelRoot;
        } catch (OrtException | IOException e) {
            closeSessions();
            throw new WhisperException("Model file is invalid. Delete and re-download.", e);
        }
    }

    @Override
    public String transcribe(TranscriptionJob job, TranscriptionListener listener) throws WhisperException {
        canceled.set(false);
        loadModel(job.model(), job.modelRoot());
        try (OrtSession.RunOptions runOptions = new OrtSession.RunOptions()) {
            activeRunOptions.set(runOptions);
            listener.onStatus("Preparing audio...");
            listener.onProgress(0.02);
            float[] samples = audioDecoder.decodeToMono16k(job.audioJob().file());
            checkCanceled();
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
                String text = transcribeChunk(audioChunk, listener, chunkStart, chunkEnd, runOptions);
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
            if (canceled.get()) {
                throw new WhisperException("Canceled.", e);
            }
            throw new WhisperException("Something went wrong. Try another file.", e);
        } catch (OrtException e) {
            if (canceled.get()) {
                throw new WhisperException("Canceled.", e);
            }
            throw new WhisperException("Something went wrong. Try another file.", e);
        } catch (IOException e) {
            throw new WhisperException("Something went wrong. Try another file.", e);
        } finally {
            activeRunOptions.set(null);
        }
    }

    @Override
    public void cancel() {
        canceled.set(true);
        OrtSession.RunOptions runOptions = activeRunOptions.get();
        if (runOptions != null) {
            try {
                runOptions.setTerminate(true);
            } catch (OrtException ignored) {
                // The cooperative cancellation flag still stops work at the next safe boundary.
            }
        }
    }

    @Override
    public synchronized void close() {
        cancel();
        closeSessions();
    }

    private String transcribeChunk(
            float[] samples,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd,
            OrtSession.RunOptions runOptions
    ) throws OrtException, WhisperException {
        float[][][] features = featureExtractor.extract(samples);
        checkCanceled();
        float[][][] encoderHiddenStates = runEncoder(features, runOptions);
        List<Long> tokens = new ArrayList<>(generationConfig.initialTokens());
        int initialSize = tokens.size();

        try (OnnxTensor encoderHiddenTensor = OnnxTensor.createTensor(environment, encoderHiddenStates)) {
            if (decoderWithPastSession != null) {
                decodeWithPast(tokens, initialSize, encoderHiddenTensor, listener, progressStart, progressEnd, runOptions);
            } else {
                decodeWithoutCache(tokens, initialSize, encoderHiddenTensor, listener, progressStart, progressEnd, runOptions);
            }
        }

        return tokenizer.decode(tokens.subList(initialSize, tokens.size()));
    }

    private void decodeWithoutCache(
            List<Long> tokens,
            int initialSize,
            OnnxTensor encoderHiddenTensor,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd,
            OrtSession.RunOptions runOptions
    ) throws OrtException, WhisperException {
        int maxLength = generationConfig.maxLength();
        while (tokens.size() < maxLength) {
            checkCanceled();
            float[] logits = runDecoder(tokens, encoderHiddenTensor, runOptions);
            int generated = tokens.size() - initialSize;
            int nextToken = chooseNextToken(logits, generated == 0);
            if (nextToken == generationConfig.eosTokenId()) {
                break;
            }
            tokens.add((long) nextToken);
            updateGenerationProgress(tokens.size(), generated, maxLength, listener, progressStart, progressEnd);
        }
    }

    private void decodeWithPast(
            List<Long> tokens,
            int initialSize,
            OnnxTensor encoderHiddenTensor,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd,
            OrtSession.RunOptions runOptions
    ) throws OrtException, WhisperException {
        int maxLength = generationConfig.maxLength();
        OrtSession.Result initialResult = null;
        OrtSession.Result decoderCacheResult = null;
        try {
            initialResult = runInitialDecoder(tokens, encoderHiddenTensor, runOptions);
            decoderCacheResult = initialResult;
            float[] logits = extractLogits(initialResult);

            while (tokens.size() < maxLength) {
                checkCanceled();
                int generated = tokens.size() - initialSize;
                int nextToken = chooseNextToken(logits, generated == 0);
                if (nextToken == generationConfig.eosTokenId()) {
                    break;
                }
                tokens.add((long) nextToken);
                updateGenerationProgress(tokens.size(), generated, maxLength, listener, progressStart, progressEnd);
                if (tokens.size() >= maxLength) {
                    break;
                }

                OrtSession.Result nextResult = runDecoderWithPast(
                        nextToken,
                        encoderHiddenTensor,
                        initialResult,
                        decoderCacheResult,
                        runOptions
                );
                if (decoderCacheResult != initialResult) {
                    decoderCacheResult.close();
                }
                decoderCacheResult = nextResult;
                logits = extractLogits(decoderCacheResult);
            }
        } finally {
            if (decoderCacheResult != null && decoderCacheResult != initialResult) {
                decoderCacheResult.close();
            }
            if (initialResult != null) {
                initialResult.close();
            }
        }
    }

    private void updateGenerationProgress(
            int tokenCount,
            int generated,
            int maxLength,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd
    ) {
        if (generated % 4 == 0) {
            double fraction = Math.min(1.0, tokenCount / (double) maxLength);
            listener.onProgress(progressStart + (progressEnd - progressStart) * fraction);
        }
    }

    private float[][][] runEncoder(float[][][] features, OrtSession.RunOptions runOptions)
            throws OrtException, WhisperException {
        try (OnnxTensor input = OnnxTensor.createTensor(environment, features);
             OrtSession.Result result = encoderSession.run(Map.of(encoderInputName, input), runOptions)) {
            Object value = result.get(0).getValue();
            if (value instanceof float[][][] hiddenStates) {
                return hiddenStates;
            }
            throw new WhisperException("Model file is invalid. Delete and re-download.");
        }
    }

    private float[] runDecoder(
            List<Long> tokens,
            OnnxTensor encoderHiddenTensor,
            OrtSession.RunOptions runOptions
    ) throws OrtException, WhisperException {
        try (OrtSession.Result result = runInitialDecoder(tokens, encoderHiddenTensor, runOptions)) {
            return extractLogits(result);
        }
    }

    private OrtSession.Result runInitialDecoder(
            List<Long> tokens,
            OnnxTensor encoderHiddenTensor,
            OrtSession.RunOptions runOptions
    ) throws OrtException {
        long[][] inputIds = new long[1][tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            inputIds[0][i] = tokens.get(i);
        }
        try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, inputIds)) {
            return decoderSession.run(decoderInputs(inputIdsTensor, encoderHiddenTensor), runOptions);
        }
    }

    private OrtSession.Result runDecoderWithPast(
            int lastToken,
            OnnxTensor encoderHiddenTensor,
            OrtSession.Result initialResult,
            OrtSession.Result decoderCacheResult,
            OrtSession.RunOptions runOptions
    ) throws OrtException, WhisperException {
        long[][] inputIds = new long[][]{{lastToken}};
        try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, inputIds)) {
            Map<String, OnnxTensorLike> inputs = new LinkedHashMap<>();
            for (String inputName : decoderWithPastSession.getInputNames()) {
                if (inputName.equals(decoderWithPastInputIdsName)) {
                    inputs.put(inputName, inputIdsTensor);
                } else if (inputName.equals("encoder_hidden_states")) {
                    inputs.put(inputName, encoderHiddenTensor);
                } else if (inputName.startsWith("past_key_values.")) {
                    OrtSession.Result owner = inputName.contains(".encoder.")
                            ? initialResult
                            : decoderCacheResult;
                    inputs.put(inputName, cacheTensor(owner, inputName));
                } else {
                    throw new WhisperException("Unsupported cached decoder input: " + inputName);
                }
            }
            return decoderWithPastSession.run(inputs, runOptions);
        }
    }

    private OnnxTensorLike cacheTensor(OrtSession.Result owner, String inputName) throws WhisperException {
        String outputName = "present." + inputName.substring("past_key_values.".length());
        OnnxValue value = owner.get(outputName)
                .orElseThrow(() -> new WhisperException("Cached decoder output is missing: " + outputName));
        if (value instanceof OnnxTensorLike tensor) {
            return tensor;
        }
        throw new WhisperException("Cached decoder output has an unsupported type: " + outputName);
    }

    private float[] extractLogits(OrtSession.Result result) throws WhisperException, OrtException {
        OnnxValue value = result.get("logits").orElseGet(() -> result.get(0));
        Object raw = value.getValue();
        if (raw instanceof float[][][] logits && logits.length > 0 && logits[0].length > 0) {
            return logits[0][logits[0].length - 1];
        }
        throw new WhisperException("Model file is invalid. Delete and re-download.");
    }

    private Map<String, OnnxTensor> decoderInputs(OnnxTensor inputIdsTensor, OnnxTensor encoderHiddenTensor) {
        Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
        inputs.put(decoderInputIdsName, inputIdsTensor);
        inputs.put(decoderEncoderStatesName, encoderHiddenTensor);
        return inputs;
    }

    private SessionBundle createSessions(Path encoder, Path decoder, Path decoderWithPast)
            throws OrtException, WhisperException {
        WhisperExecutionProvider requested = providerConfig.resolve(OrtEnvironment.getAvailableProviders());
        try {
            return openSessionBundle(encoder, decoder, decoderWithPast, requested);
        } catch (OrtException e) {
            if (requested != WhisperExecutionProvider.CPU && providerConfig.allowCpuFallback()) {
                return openSessionBundle(encoder, decoder, decoderWithPast, WhisperExecutionProvider.CPU);
            }
            throw new WhisperException(requested.displayName() + " provider failed. Check the runtime build and drivers.", e);
        }
    }

    private SessionBundle openSessionBundle(
            Path encoder,
            Path decoder,
            Path decoderWithPast,
            WhisperExecutionProvider provider
    ) throws OrtException {
        OrtSession openedEncoder = null;
        OrtSession openedDecoder = null;
        OrtSession openedDecoderWithPast = null;
        try {
            openedEncoder = createSession(encoder, provider);
            openedDecoder = createSession(decoder, provider);
            if (decoderWithPast != null) {
                openedDecoderWithPast = createSession(decoderWithPast, provider);
            }
            return new SessionBundle(openedEncoder, openedDecoder, openedDecoderWithPast, provider);
        } catch (OrtException e) {
            closeQuietly(openedDecoderWithPast);
            closeQuietly(openedDecoder);
            closeQuietly(openedEncoder);
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
        return names.stream().findFirst()
                .orElseThrow(() -> new WhisperException("Model file is invalid. Delete and re-download."));
    }

    private synchronized void closeSessions() {
        closeQuietly(decoderWithPastSession);
        decoderWithPastSession = null;
        closeQuietly(decoderSession);
        decoderSession = null;
        closeQuietly(encoderSession);
        encoderSession = null;
        decoderWithPastInputIdsName = null;
        loadedModel = null;
        loadedRoot = null;
        activeProvider = WhisperExecutionProvider.CPU;
    }

    private static void closeQuietly(OrtSession session) {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (OrtException ignored) {
            // Cleanup should not replace the user-facing transcription error.
        }
    }

    private record SessionBundle(
            OrtSession encoderSession,
            OrtSession decoderSession,
            OrtSession decoderWithPastSession,
            WhisperExecutionProvider provider
    ) {
    }
}
