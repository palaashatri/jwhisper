package com.jwhisper.transcribe;

/** Timing information captured for one transcription. */
public record TranscriptionTimings(
        double audioSeconds,
        long totalMillis,
        Long preprocessingMillis,
        Long encoderMillis,
        Long decodingMillis,
        Double decodeTokensPerSecond,
        Long peakRssBytes
) {
    public double realTimeFactor() {
        return audioSeconds > 0.0 ? (totalMillis / 1000.0) / audioSeconds : Double.NaN;
    }
}
