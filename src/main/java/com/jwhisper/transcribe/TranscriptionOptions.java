package com.jwhisper.transcribe;

import java.util.List;

/** Immutable frontend-neutral decoding/transcription options. */
public record TranscriptionOptions(
        String language,
        TranscriptionTask task,
        boolean timestamps,
        int beamSize,
        List<Double> temperatures,
        Double compressionRatioThreshold,
        Double logProbabilityThreshold,
        Double noSpeechThreshold,
        boolean conditionOnPreviousText,
        String initialPrompt
) {
    public TranscriptionOptions {
        language = language == null || language.isBlank() || "auto".equalsIgnoreCase(language)
                ? null
                : language.trim().toLowerCase();
        task = task == null ? TranscriptionTask.TRANSCRIBE : task;
        if (beamSize < 1) throw new IllegalArgumentException("beamSize must be >= 1");
        temperatures = temperatures == null || temperatures.isEmpty()
                ? List.of(0.0, 0.2, 0.4, 0.6, 0.8, 1.0)
                : List.copyOf(temperatures);
        for (Double temperature : temperatures) {
            if (temperature == null || !Double.isFinite(temperature) || temperature < 0.0) {
                throw new IllegalArgumentException("temperatures must be finite and >= 0");
            }
        }
        if (compressionRatioThreshold != null && compressionRatioThreshold <= 0.0) {
            throw new IllegalArgumentException("compressionRatioThreshold must be > 0");
        }
        if (noSpeechThreshold != null && (noSpeechThreshold < 0.0 || noSpeechThreshold > 1.0)) {
            throw new IllegalArgumentException("noSpeechThreshold must be between 0 and 1");
        }
        initialPrompt = initialPrompt == null || initialPrompt.isBlank() ? null : initialPrompt.trim();
    }

    public static TranscriptionOptions defaults() {
        return new TranscriptionOptions(
                null,
                TranscriptionTask.TRANSCRIBE,
                false,
                1,
                List.of(0.0, 0.2, 0.4, 0.6, 0.8, 1.0),
                2.4,
                -1.0,
                0.6,
                true,
                null
        );
    }

    public TranscriptionOptions withLanguage(String value) {
        return new TranscriptionOptions(value, task, timestamps, beamSize, temperatures,
                compressionRatioThreshold, logProbabilityThreshold, noSpeechThreshold,
                conditionOnPreviousText, initialPrompt);
    }

    public TranscriptionOptions withTask(TranscriptionTask value) {
        return new TranscriptionOptions(language, value, timestamps, beamSize, temperatures,
                compressionRatioThreshold, logProbabilityThreshold, noSpeechThreshold,
                conditionOnPreviousText, initialPrompt);
    }

    public TranscriptionOptions withTimestamps(boolean value) {
        return new TranscriptionOptions(language, task, value, beamSize, temperatures,
                compressionRatioThreshold, logProbabilityThreshold, noSpeechThreshold,
                conditionOnPreviousText, initialPrompt);
    }

    public TranscriptionOptions withBeamSize(int value) {
        return new TranscriptionOptions(language, task, timestamps, value, temperatures,
                compressionRatioThreshold, logProbabilityThreshold, noSpeechThreshold,
                conditionOnPreviousText, initialPrompt);
    }

    public TranscriptionOptions withInitialPrompt(String value) {
        return new TranscriptionOptions(language, task, timestamps, beamSize, temperatures,
                compressionRatioThreshold, logProbabilityThreshold, noSpeechThreshold,
                conditionOnPreviousText, value);
    }
}
