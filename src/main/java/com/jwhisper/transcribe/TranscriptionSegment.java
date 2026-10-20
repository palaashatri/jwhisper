package com.jwhisper.transcribe;

/** A time-bounded piece of recognized speech. */
public record TranscriptionSegment(
        int index,
        double startSeconds,
        double endSeconds,
        String text,
        Double averageLogProbability,
        Double noSpeechProbability
) {
    public TranscriptionSegment {
        if (index < 0) throw new IllegalArgumentException("index must be >= 0");
        if (!Double.isFinite(startSeconds) || startSeconds < 0) throw new IllegalArgumentException("startSeconds must be finite and >= 0");
        if (!Double.isFinite(endSeconds) || endSeconds < startSeconds) throw new IllegalArgumentException("endSeconds must be >= startSeconds");
        text = text == null ? "" : text.trim();
    }
}
