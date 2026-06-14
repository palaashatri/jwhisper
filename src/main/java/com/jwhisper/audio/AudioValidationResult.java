package com.jwhisper.audio;

import java.util.Optional;

public final class AudioValidationResult {
    private final AudioJob job;
    private final String message;

    private AudioValidationResult(AudioJob job, String message) {
        this.job = job;
        this.message = message;
    }

    public static AudioValidationResult ok(AudioJob job) {
        return new AudioValidationResult(job, null);
    }

    public static AudioValidationResult error(String message) {
        return new AudioValidationResult(null, message);
    }

    public Optional<AudioJob> job() {
        return Optional.ofNullable(job);
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public boolean isOk() {
        return job != null;
    }
}
