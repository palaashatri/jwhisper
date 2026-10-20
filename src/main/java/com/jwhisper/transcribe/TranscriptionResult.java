package com.jwhisper.transcribe;

import java.util.List;

/** Frontend-neutral transcription result returned by the production API. */
public record TranscriptionResult(
        String text,
        String language,
        String task,
        List<TranscriptionSegment> segments,
        String modelId,
        String provider,
        TranscriptionTimings timings
) {
    public TranscriptionResult {
        text = text == null ? "" : text.trim();
        language = language == null ? "und" : language;
        task = task == null ? "transcribe" : task;
        segments = segments == null ? List.of() : List.copyOf(segments);
        modelId = modelId == null ? "unknown" : modelId;
        provider = provider == null ? "unknown" : provider;
    }

    public static TranscriptionResult plain(String text, String modelId, double audioSeconds, long totalMillis) {
        List<TranscriptionSegment> segments = text == null || text.isBlank()
                ? List.of()
                : List.of(new TranscriptionSegment(0, 0.0, Math.max(0.0, audioSeconds), text, null, null));
        return new TranscriptionResult(
                text,
                "und",
                "transcribe",
                segments,
                modelId,
                "unknown",
                new TranscriptionTimings(audioSeconds, totalMillis, null, null, null, null, null)
        );
    }
}
