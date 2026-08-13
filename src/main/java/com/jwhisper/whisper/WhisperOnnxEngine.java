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
import com.jwhisper.transcribe.TranscriptionOptions;
import com.jwhisper.transcribe.TranscriptionResult;
import com.jwhisper.transcribe.TranscriptionSegment;
import com.jwhisper.transcribe.TranscriptionTimings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

public final class WhisperOnnxEngine implements WhisperEngineAgent {
    private static final double TIMESTAMP_PRECISION_SECONDS = 0.02;

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
        if (descriptor.equals(loadedModel) && modelRoot.equals(loadedRoot)
                && encoderSession != null && decoderSession != null) {
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
                decoderWithPastInputIdsName = chooseName(decoderWithPastSession.getInputNames(), "input_ids", "input");
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
        return transcribeResult(job, listener).text();
    }

    @Override
    public TranscriptionResult transcribeResult(TranscriptionJob job, TranscriptionListener listener)
            throws WhisperException {
        long totalStart = System.nanoTime();
        canceled.set(false);
        loadModel(job.model(), job.modelRoot());
        TimingAccumulator timing = new TimingAccumulator();
        try (OrtSession.RunOptions runOptions = new OrtSession.RunOptions()) {
            activeRunOptions.set(runOptions);
            listener.onStatus("Preparing audio...");
            listener.onProgress(0.02);
            float[] samples = audioDecoder.decodeToMono16k(job.audioJob().file());
            checkCanceled();
            if (samples.length == 0) throw new WhisperException("Something went wrong. Try another file.");

            double audioSeconds = samples.length / (double) FfmpegAudioDecoder.SAMPLE_RATE;
            TranscriptionOptions requested = job.options();
            String language = requested.language();
            if (generationConfig.isMultilingual() && language == null) {
                listener.onStatus("Detecting language...");
                language = detectLanguage(samples, runOptions, timing);
            } else if (!generationConfig.isMultilingual()) {
                language = "en";
            }

            listener.onStatus("Transcribing with " + activeProvider.displayName() + "...");
            int windowSamples = featureExtractor.maxSamples();
            boolean longForm = samples.length > windowSamples;
            TranscriptionOptions decodingOptions = copyOptions(requested, language, requested.timestamps() || longForm);
            List<Long> promptTokens = initialPromptTokens(requested.initialPrompt());
            List<Long> previousTextTokens = new ArrayList<>(promptTokens);
            List<TranscriptionSegment> segments = new ArrayList<>();
            StringBuilder transcript = new StringBuilder();

            int seek = 0;
            int segmentIndex = 0;
            while (seek < samples.length) {
                checkCanceled();
                int to = Math.min(samples.length, seek + windowSamples);
                float[] window = Arrays.copyOfRange(samples, seek, to);
                double progressStart = 0.08 + 0.88 * (seek / (double) samples.length);
                double progressEnd = 0.08 + 0.88 * (to / (double) samples.length);
                List<Long> context = requested.conditionOnPreviousText() ? previousTextTokens : promptTokens;
                ChunkDecoding chunk = decodeWithFallback(
                        window, decodingOptions, context, listener,
                        progressStart, progressEnd, runOptions, timing
                );

                boolean silence = isSilence(chunk, requested);
                double seekSeconds = seek / (double) FfmpegAudioDecoder.SAMPLE_RATE;
                double windowDuration = window.length / (double) FfmpegAudioDecoder.SAMPLE_RATE;
                if (!silence && !chunk.text().isBlank()) {
                    List<TranscriptionSegment> chunkSegments = segmentize(chunk, seekSeconds, windowDuration, segmentIndex);
                    if (chunkSegments.isEmpty()) {
                        chunkSegments = List.of(new TranscriptionSegment(
                                segmentIndex, seekSeconds, seekSeconds + windowDuration,
                                chunk.text(), chunk.averageLogProbability(), chunk.noSpeechProbability()
                        ));
                    }
                    for (TranscriptionSegment segment : chunkSegments) {
                        segments.add(segment);
                        segmentIndex++;
                        listener.onTranscriptChunk(segment.text());
                    }
                    if (!transcript.isEmpty()) transcript.append(System.lineSeparator());
                    transcript.append(chunk.text());
                    if (requested.conditionOnPreviousText()) {
                        appendTextTokens(previousTextTokens, chunk.generatedTokens());
                        trimPrompt(previousTextTokens);
                    }
                }

                int advance = window.length;
                if (longForm && decodingOptions.timestamps() && chunk.lastTimestampSeconds() > 0.0) {
                    int timestampAdvance = (int) Math.round(
                            chunk.lastTimestampSeconds() * FfmpegAudioDecoder.SAMPLE_RATE
                    );
                    if (timestampAdvance > 0 && timestampAdvance <= window.length) advance = timestampAdvance;
                }
                if (advance <= 0) advance = window.length;
                seek = Math.min(samples.length, seek + advance);
            }

            listener.onProgress(1.0);
            listener.onStatus("Done.");
            long totalMillis = nanosToMillis(System.nanoTime() - totalStart);
            TranscriptionTimings timings = new TranscriptionTimings(
                    audioSeconds,
                    totalMillis,
                    nanosToMillis(timing.preprocessingNanos),
                    nanosToMillis(timing.encoderNanos),
                    nanosToMillis(timing.decodingNanos),
                    timing.decodingNanos > 0
                            ? timing.generatedTokens / (timing.decodingNanos / 1_000_000_000.0)
                            : null,
                    null
            );
            return new TranscriptionResult(
                    transcript.toString().trim(),
                    language == null ? "und" : language,
                    requested.task().wireName(),
                    segments,
                    job.model().id(),
                    activeProvider.displayName(),
                    timings
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (canceled.get()) throw new WhisperException("Canceled.", e);
            throw new WhisperException("Something went wrong. Try another file.", e);
        } catch (OrtException e) {
            if (canceled.get()) throw new WhisperException("Canceled.", e);
            throw new WhisperException("Something went wrong. Try another file.", e);
        } catch (IOException e) {
            throw new WhisperException("Something went wrong. Try another file.", e);
        } finally {
            activeRunOptions.set(null);
        }
    }

    private String detectLanguage(float[] samples, OrtSession.RunOptions runOptions, TimingAccumulator timing)
            throws OrtException, WhisperException {
        if (!generationConfig.isMultilingual() || generationConfig.languageToId().isEmpty()) return "en";
        float[] window = Arrays.copyOf(samples, Math.min(samples.length, featureExtractor.maxSamples()));
        long start = System.nanoTime();
        float[][][] features = featureExtractor.extract(window);
        timing.preprocessingNanos += System.nanoTime() - start;
        checkCanceled();
        start = System.nanoTime();
        float[][][] hidden = runEncoder(features, runOptions);
        timing.encoderNanos += System.nanoTime() - start;
        try (OnnxTensor encoderHiddenTensor = OnnxTensor.createTensor(environment, hidden)) {
            List<Long> tokens = List.of((long) generationConfig.decoderStartTokenId());
            try (OrtSession.Result result = runInitialDecoder(tokens, encoderHiddenTensor, runOptions)) {
                float[] logits = extractLogits(result);
                int bestToken = -1;
                float best = Float.NEGATIVE_INFINITY;
                for (Integer tokenId : generationConfig.languageToId().values()) {
                    if (tokenId >= 0 && tokenId < logits.length && logits[tokenId] > best) {
                        best = logits[tokenId];
                        bestToken = tokenId;
                    }
                }
                String language = generationConfig.languageForToken(bestToken);
                if (language == null) throw new WhisperException("Language detection failed for this model.");
                return language;
            }
        }
    }

    private ChunkDecoding decodeWithFallback(
            float[] samples,
            TranscriptionOptions options,
            List<Long> promptTokens,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd,
            OrtSession.RunOptions runOptions,
            TimingAccumulator timing
    ) throws OrtException, WhisperException {
        ChunkDecoding result = null;
        for (double temperature : options.temperatures()) {
            result = decodeWindow(samples, options, promptTokens, temperature, listener,
                    progressStart, progressEnd, runOptions, timing);
            boolean failed = (options.compressionRatioThreshold() != null
                    && result.compressionRatio() > options.compressionRatioThreshold())
                    || (options.logProbabilityThreshold() != null
                    && result.averageLogProbability() < options.logProbabilityThreshold());
            if (options.noSpeechThreshold() != null && options.logProbabilityThreshold() != null
                    && result.noSpeechProbability() > options.noSpeechThreshold()
                    && result.averageLogProbability() < options.logProbabilityThreshold()) {
                failed = false;
            }
            if (!failed) break;
        }
        return result;
    }

    private ChunkDecoding decodeWindow(
            float[] samples,
            TranscriptionOptions options,
            List<Long> promptTokens,
            double temperature,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd,
            OrtSession.RunOptions runOptions,
            TimingAccumulator timing
    ) throws OrtException, WhisperException {
        long started = System.nanoTime();
        float[][][] features = featureExtractor.extract(samples);
        timing.preprocessingNanos += System.nanoTime() - started;
        checkCanceled();
        started = System.nanoTime();
        float[][][] encoderHiddenStates = runEncoder(features, runOptions);
        timing.encoderNanos += System.nanoTime() - started;
        Integer languageToken = generationConfig.isMultilingual()
                ? generationConfig.languageToken(options.language())
                : null;
        List<Long> initialTokens = generationConfig.initialTokens(options, languageToken, promptTokens);

        started = System.nanoTime();
        ChunkDecoding decoded;
        try (OnnxTensor encoderHiddenTensor = OnnxTensor.createTensor(environment, encoderHiddenStates)) {
            if (temperature == 0.0 && options.beamSize() > 1) {
                decoded = decodeBeam(initialTokens, options, encoderHiddenTensor, listener,
                        progressStart, progressEnd, runOptions);
            } else {
                decoded = decodeAutoregressive(initialTokens, options, temperature, encoderHiddenTensor,
                        listener, progressStart, progressEnd, runOptions);
            }
        }
        timing.decodingNanos += System.nanoTime() - started;
        timing.generatedTokens += decoded.generatedTokens().size();
        return decoded;
    }

    private ChunkDecoding decodeAutoregressive(
            List<Long> initialTokens,
            TranscriptionOptions options,
            double temperature,
            OnnxTensor encoderHiddenTensor,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd,
            OrtSession.RunOptions runOptions
    ) throws OrtException, WhisperException {
        List<Long> tokens = new ArrayList<>(initialTokens);
        List<Long> generated = new ArrayList<>();
        double sumLogProb = 0.0;
        double noSpeech = 0.0;
        int maxLength = generationConfig.maxLength();
        OrtSession.Result initialResult = null;
        OrtSession.Result cacheResult = null;
        try {
            initialResult = runInitialDecoder(tokens, encoderHiddenTensor, runOptions);
            cacheResult = initialResult;
            noSpeech = noSpeechProbability(initialResult, tokens);
            float[] logits = extractLogits(initialResult);
            while (tokens.size() < maxLength) {
                checkCanceled();
                TokenChoice choice = chooseNextToken(logits, generated, options, temperature);
                if (choice.tokenId() == generationConfig.eosTokenId()) break;
                tokens.add((long) choice.tokenId());
                generated.add((long) choice.tokenId());
                sumLogProb += choice.logProbability();
                updateGenerationProgress(tokens.size(), generated.size(), maxLength, listener, progressStart, progressEnd);
                if (tokens.size() >= maxLength) break;

                if (decoderWithPastSession != null) {
                    OrtSession.Result next = runDecoderWithPast(
                            choice.tokenId(), encoderHiddenTensor, initialResult, cacheResult, runOptions
                    );
                    if (cacheResult != initialResult) cacheResult.close();
                    cacheResult = next;
                    logits = extractLogits(cacheResult);
                } else {
                    if (cacheResult != initialResult) cacheResult.close();
                    cacheResult = runInitialDecoder(tokens, encoderHiddenTensor, runOptions);
                    logits = extractLogits(cacheResult);
                }
            }
        } finally {
            if (cacheResult != null && cacheResult != initialResult) cacheResult.close();
            if (initialResult != null) initialResult.close();
        }
        return finishChunk(generated, sumLogProb, noSpeech, temperature);
    }

    private ChunkDecoding decodeBeam(
            List<Long> initialTokens,
            TranscriptionOptions options,
            OnnxTensor encoderHiddenTensor,
            TranscriptionListener listener,
            double progressStart,
            double progressEnd,
            OrtSession.RunOptions runOptions
    ) throws OrtException, WhisperException {
        int beamSize = options.beamSize();
        List<Beam> beams = new ArrayList<>();
        beams.add(new Beam(new ArrayList<>(initialTokens), new ArrayList<>(), 0.0, false));
        double noSpeech = 0.0;
        boolean measuredNoSpeech = false;

        for (int step = 0; step < generationConfig.maxLength(); step++) {
            checkCanceled();
            List<Beam> expanded = new ArrayList<>();
            boolean allComplete = true;
            for (Beam beam : beams) {
                if (beam.complete()) {
                    expanded.add(beam);
                    continue;
                }
                allComplete = false;
                try (OrtSession.Result result = runInitialDecoder(beam.tokens(), encoderHiddenTensor, runOptions)) {
                    if (!measuredNoSpeech) {
                        noSpeech = noSpeechProbability(result, beam.tokens());
                        measuredNoSpeech = true;
                    }
                    float[] logits = extractLogits(result);
                    for (TokenChoice choice : topChoices(logits, beam.generated(), options, beamSize)) {
                        List<Long> nextTokens = new ArrayList<>(beam.tokens());
                        List<Long> nextGenerated = new ArrayList<>(beam.generated());
                        boolean complete = choice.tokenId() == generationConfig.eosTokenId();
                        if (!complete) {
                            nextTokens.add((long) choice.tokenId());
                            nextGenerated.add((long) choice.tokenId());
                        }
                        expanded.add(new Beam(nextTokens, nextGenerated,
                                beam.sumLogProbability() + choice.logProbability(), complete));
                    }
                }
            }
            expanded.sort(Comparator.comparingDouble(Beam::score).reversed());
            beams = new ArrayList<>(expanded.subList(0, Math.min(beamSize, expanded.size())));
            updateGenerationProgress(initialTokens.size() + step, step, generationConfig.maxLength(),
                    listener, progressStart, progressEnd);
            if (allComplete || beams.stream().allMatch(Beam::complete)) break;
        }
        Beam best = beams.stream().max(Comparator.comparingDouble(Beam::score)).orElseThrow();
        return finishChunk(best.generated(), best.sumLogProbability(), noSpeech, 0.0);
    }

    private ChunkDecoding finishChunk(List<Long> generated, double sumLogProb, double noSpeech, double temperature) {
        String text = tokenizer.decode(generated);
        double averageLogProb = sumLogProb / (generated.size() + 1.0);
        double compression = compressionRatio(text);
        int timestampBegin = generationConfig.timestampBeginTokenId();
        double lastTimestamp = 0.0;
        if (timestampBegin >= 0) {
            for (Long token : generated) {
                if (token >= timestampBegin) {
                    lastTimestamp = Math.max(lastTimestamp, (token - timestampBegin) * TIMESTAMP_PRECISION_SECONDS);
                }
            }
        }
        return new ChunkDecoding(List.copyOf(generated), text, averageLogProb, noSpeech,
                temperature, compression, lastTimestamp);
    }

    private List<TranscriptionSegment> segmentize(
            ChunkDecoding chunk,
            double seekSeconds,
            double windowDuration,
            int firstIndex
    ) {
        int timestampBegin = generationConfig.timestampBeginTokenId();
        if (timestampBegin < 0) return List.of();
        List<TranscriptionSegment> result = new ArrayList<>();
        Long startToken = null;
        List<Long> textTokens = new ArrayList<>();
        int index = firstIndex;
        for (Long token : chunk.generatedTokens()) {
            if (token >= timestampBegin) {
                if (startToken != null && !textTokens.isEmpty()) {
                    double start = seekSeconds + (startToken - timestampBegin) * TIMESTAMP_PRECISION_SECONDS;
                    double end = seekSeconds + (token - timestampBegin) * TIMESTAMP_PRECISION_SECONDS;
                    end = Math.min(seekSeconds + windowDuration, Math.max(start, end));
                    String text = tokenizer.decode(textTokens);
                    if (!text.isBlank()) {
                        result.add(new TranscriptionSegment(index++, start, end, text,
                                chunk.averageLogProbability(), chunk.noSpeechProbability()));
                    }
                }
                startToken = token;
                textTokens.clear();
            } else if (!tokenizer.isSpecial(token.intValue())) {
                textTokens.add(token);
            }
        }
        if (startToken != null && !textTokens.isEmpty()) {
            double start = seekSeconds + (startToken - timestampBegin) * TIMESTAMP_PRECISION_SECONDS;
            String text = tokenizer.decode(textTokens);
            if (!text.isBlank()) {
                result.add(new TranscriptionSegment(index, start, seekSeconds + windowDuration, text,
                        chunk.averageLogProbability(), chunk.noSpeechProbability()));
            }
        }
        return result;
    }

    private boolean isSilence(ChunkDecoding chunk, TranscriptionOptions options) {
        if (options.noSpeechThreshold() == null) return false;
        boolean silence = chunk.noSpeechProbability() > options.noSpeechThreshold();
        if (options.logProbabilityThreshold() != null
                && chunk.averageLogProbability() > options.logProbabilityThreshold()) {
            silence = false;
        }
        return silence;
    }

    private List<Long> initialPromptTokens(String prompt) {
        if (prompt == null || prompt.isBlank()) return List.of();
        return tokenizer.encode(" " + prompt.trim());
    }

    private void appendTextTokens(List<Long> target, List<Long> generated) {
        int timestampBegin = generationConfig.timestampBeginTokenId();
        for (Long token : generated) {
            if ((timestampBegin < 0 || token < timestampBegin) && !tokenizer.isSpecial(token.intValue())) {
                target.add(token);
            }
        }
    }

    private void trimPrompt(List<Long> tokens) {
        int max = Math.max(1, generationConfig.maxLength() / 2 - 1);
        if (tokens.size() > max) tokens.subList(0, tokens.size() - max).clear();
    }

    private TranscriptionOptions copyOptions(TranscriptionOptions source, String language, boolean timestamps) {
        return new TranscriptionOptions(language, source.task(), timestamps, source.beamSize(), source.temperatures(),
                source.compressionRatioThreshold(), source.logProbabilityThreshold(), source.noSpeechThreshold(),
                source.conditionOnPreviousText(), source.initialPrompt());
    }

    private float[][][] runEncoder(float[][][] features, OrtSession.RunOptions runOptions)
            throws OrtException, WhisperException {
        try (OnnxTensor input = OnnxTensor.createTensor(environment, features);
             OrtSession.Result result = encoderSession.run(Map.of(encoderInputName, input), runOptions)) {
            Object value = result.get(0).getValue();
            if (value instanceof float[][][] hiddenStates) return hiddenStates;
            throw new WhisperException("Model file is invalid. Delete and re-download.");
        }
    }

    private OrtSession.Result runInitialDecoder(
            List<Long> tokens,
            OnnxTensor encoderHiddenTensor,
            OrtSession.RunOptions runOptions
    ) throws OrtException {
        long[][] inputIds = new long[1][tokens.size()];
        for (int i = 0; i < tokens.size(); i++) inputIds[0][i] = tokens.get(i);
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
                    OrtSession.Result owner = inputName.contains(".encoder.") ? initialResult : decoderCacheResult;
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
        if (value instanceof OnnxTensorLike tensor) return tensor;
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

    private float[] extractLogitsAt(OrtSession.Result result, int position) throws WhisperException, OrtException {
        OnnxValue value = result.get("logits").orElseGet(() -> result.get(0));
        Object raw = value.getValue();
        if (raw instanceof float[][][] logits && logits.length > 0 && logits[0].length > 0) {
            int safe = Math.max(0, Math.min(position, logits[0].length - 1));
            return logits[0][safe];
        }
        throw new WhisperException("Model file is invalid. Delete and re-download.");
    }

    private double noSpeechProbability(OrtSession.Result result, List<Long> tokens)
            throws WhisperException, OrtException {
        var noSpeechId = tokenizer.tokenId("<|nospeech|>");
        if (noSpeechId.isEmpty()) return 0.0;
        int sotPosition = tokens.lastIndexOf((long) generationConfig.decoderStartTokenId());
        float[] logits = extractLogitsAt(result, Math.max(0, sotPosition));
        return probability(logits, noSpeechId.getAsInt());
    }

    private Map<String, OnnxTensor> decoderInputs(OnnxTensor inputIdsTensor, OnnxTensor encoderHiddenTensor) {
        Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
        inputs.put(decoderInputIdsName, inputIdsTensor);
        inputs.put(decoderEncoderStatesName, encoderHiddenTensor);
        return inputs;
    }

    private TokenChoice chooseNextToken(
            float[] rawLogits,
            List<Long> generated,
            TranscriptionOptions options,
            double temperature
    ) {
        float[] logits = Arrays.copyOf(rawLogits, rawLogits.length);
        applyLogitRules(logits, generated, options);
        int token;
        if (temperature <= 0.0) {
            token = argmax(logits);
        } else {
            token = sample(logits, temperature);
        }
        return new TokenChoice(token, logProbability(logits, token));
    }

    private List<TokenChoice> topChoices(
            float[] rawLogits,
            List<Long> generated,
            TranscriptionOptions options,
            int count
    ) {
        float[] logits = Arrays.copyOf(rawLogits, rawLogits.length);
        applyLogitRules(logits, generated, options);
        List<Integer> ids = new ArrayList<>(logits.length);
        for (int i = 0; i < logits.length; i++) if (Float.isFinite(logits[i])) ids.add(i);
        ids.sort((a, b) -> Float.compare(logits[b], logits[a]));
        List<TokenChoice> choices = new ArrayList<>();
        for (int i = 0; i < Math.min(count, ids.size()); i++) {
            int token = ids.get(i);
            choices.add(new TokenChoice(token, logProbability(logits, token)));
        }
        return choices;
    }

    private void applyLogitRules(float[] logits, List<Long> generated, TranscriptionOptions options) {
        for (Integer tokenId : generationConfig.suppressTokens()) mask(logits, tokenId);
        if (generated.isEmpty()) {
            for (Integer tokenId : generationConfig.beginSuppressTokens()) mask(logits, tokenId);
        }
        if (!options.timestamps()) return;

        int timestampBegin = generationConfig.timestampBeginTokenId();
        if (timestampBegin < 0 || timestampBegin >= logits.length) return;
        mask(logits, generationConfig.noTimestampsTokenId());

        boolean lastWasTimestamp = !generated.isEmpty() && generated.get(generated.size() - 1) >= timestampBegin;
        boolean penultimateWasTimestamp = generated.size() < 2
                || generated.get(generated.size() - 2) >= timestampBegin;
        if (lastWasTimestamp) {
            if (penultimateWasTimestamp) maskRange(logits, timestampBegin, logits.length);
            else maskRange(logits, 0, generationConfig.eosTokenId());
        }

        long lastTimestamp = -1;
        for (Long token : generated) if (token >= timestampBegin) lastTimestamp = Math.max(lastTimestamp, token);
        if (lastTimestamp >= timestampBegin) {
            int minimum = (int) lastTimestamp + (lastWasTimestamp && !penultimateWasTimestamp ? 1 : 0);
            maskRange(logits, timestampBegin, Math.min(minimum, logits.length));
        }

        if (generated.isEmpty()) {
            maskRange(logits, 0, timestampBegin);
            int lastAllowed = timestampBegin + generationConfig.maxInitialTimestampIndex();
            if (lastAllowed + 1 < logits.length) maskRange(logits, lastAllowed + 1, logits.length);
        }

        double timestampLogSum = logSumExp(logits, timestampBegin, logits.length);
        float maxText = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < Math.min(timestampBegin, logits.length); i++) maxText = Math.max(maxText, logits[i]);
        if (timestampLogSum > maxText) maskRange(logits, 0, timestampBegin);
    }

    private int argmax(float[] logits) {
        int best = generationConfig.eosTokenId();
        float bestValue = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < logits.length; i++) {
            if (logits[i] > bestValue) {
                bestValue = logits[i];
                best = i;
            }
        }
        return best;
    }

    private int sample(float[] logits, double temperature) {
        float max = Float.NEGATIVE_INFINITY;
        for (float value : logits) if (Float.isFinite(value)) max = Math.max(max, value);
        if (!Float.isFinite(max)) return generationConfig.eosTokenId();
        double total = 0.0;
        double[] weights = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            if (Float.isFinite(logits[i])) {
                weights[i] = Math.exp((logits[i] - max) / temperature);
                total += weights[i];
            }
        }
        double target = ThreadLocalRandom.current().nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (cumulative >= target) return i;
        }
        return argmax(logits);
    }

    private double logProbability(float[] logits, int token) {
        if (token < 0 || token >= logits.length || !Float.isFinite(logits[token])) return Double.NEGATIVE_INFINITY;
        double denominator = logSumExp(logits, 0, logits.length);
        return logits[token] - denominator;
    }

    private double probability(float[] logits, int token) {
        double log = logProbability(logits, token);
        return Double.isFinite(log) ? Math.exp(log) : 0.0;
    }

    private double logSumExp(float[] logits, int from, int to) {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = Math.max(0, from); i < Math.min(to, logits.length); i++) {
            if (Float.isFinite(logits[i])) max = Math.max(max, logits[i]);
        }
        if (!Float.isFinite(max)) return Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        for (int i = Math.max(0, from); i < Math.min(to, logits.length); i++) {
            if (Float.isFinite(logits[i])) sum += Math.exp(logits[i] - max);
        }
        return max + Math.log(sum);
    }

    private static void mask(float[] logits, int token) {
        if (token >= 0 && token < logits.length) logits[token] = Float.NEGATIVE_INFINITY;
    }

    private static void maskRange(float[] logits, int from, int to) {
        Arrays.fill(logits, Math.max(0, from), Math.max(Math.max(0, from), Math.min(to, logits.length)),
                Float.NEGATIVE_INFINITY);
    }

    private double compressionRatio(String text) {
        if (text == null || text.isEmpty()) return 0.0;
        byte[] raw = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(raw);
            }
            return raw.length / (double) Math.max(1, bytes.size());
        } catch (IOException impossible) {
            return 0.0;
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

    private SessionBundle openSessionBundle(Path encoder, Path decoder, Path decoderWithPast, WhisperExecutionProvider provider)
            throws OrtException {
        OrtSession openedEncoder = null;
        OrtSession openedDecoder = null;
        OrtSession openedDecoderWithPast = null;
        try {
            openedEncoder = createSession(encoder, provider);
            openedDecoder = createSession(decoder, provider);
            if (decoderWithPast != null) openedDecoderWithPast = createSession(decoderWithPast, provider);
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
            options.setIntraOpNumThreads(resolveThreadCount("jwhisper.intraOpThreads",
                    Math.max(1, Runtime.getRuntime().availableProcessors())));
            options.setInterOpNumThreads(resolveThreadCount("jwhisper.interOpThreads", 1));
            provider.applyTo(options, providerConfig.deviceId());
            return environment.createSession(model.toString(), options);
        }
    }

    private int resolveThreadCount(String property, int fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void checkCanceled() throws WhisperException {
        if (canceled.get()) throw new WhisperException("Canceled.");
    }

    private String chooseName(Set<String> names, String preferred, String contains) throws WhisperException {
        if (names.contains(preferred)) return preferred;
        if (contains != null) {
            for (String name : names) {
                if (name.toLowerCase().contains(contains.toLowerCase())) return name;
            }
        }
        return names.stream().findFirst()
                .orElseThrow(() -> new WhisperException("Model file is invalid. Delete and re-download."));
    }

    @Override
    public void cancel() {
        canceled.set(true);
        OrtSession.RunOptions runOptions = activeRunOptions.get();
        if (runOptions != null) {
            try {
                runOptions.setTerminate(true);
            } catch (OrtException ignored) {
            }
        }
    }

    @Override
    public synchronized void close() {
        cancel();
        closeSessions();
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
        if (session == null) return;
        try {
            session.close();
        } catch (OrtException ignored) {
        }
    }

    private static long nanosToMillis(long nanos) {
        return Math.max(0L, Math.round(nanos / 1_000_000.0));
    }

    private record SessionBundle(
            OrtSession encoderSession,
            OrtSession decoderSession,
            OrtSession decoderWithPastSession,
            WhisperExecutionProvider provider
    ) {}

    private record TokenChoice(int tokenId, double logProbability) {}

    private record Beam(
            List<Long> tokens,
            List<Long> generated,
            double sumLogProbability,
            boolean complete
    ) {
        double score() {
            return sumLogProbability / (generated.size() + 1.0);
        }
    }

    private record ChunkDecoding(
            List<Long> generatedTokens,
            String text,
            double averageLogProbability,
            double noSpeechProbability,
            double temperature,
            double compressionRatio,
            double lastTimestampSeconds
    ) {}

    private static final class TimingAccumulator {
        long preprocessingNanos;
        long encoderNanos;
        long decodingNanos;
        long generatedTokens;
    }
}
