package com.jwhisper.transcribe;

public interface TranscriptionListener {
    default void onStatus(String message) {
    }

    default void onProgress(double fraction) {
    }

    default void onTranscriptChunk(String text) {
    }
}
