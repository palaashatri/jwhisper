package com.jwhisper.whisper;

public final class WhisperException extends Exception {
    public WhisperException(String message) {
        super(message);
    }

    public WhisperException(String message, Throwable cause) {
        super(message, cause);
    }
}
