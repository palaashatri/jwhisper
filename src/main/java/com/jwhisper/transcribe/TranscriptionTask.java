package com.jwhisper.transcribe;

public enum TranscriptionTask {
    TRANSCRIBE("transcribe"),
    TRANSLATE("translate");

    private final String wireName;

    TranscriptionTask(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static TranscriptionTask parse(String value) {
        if (value == null || value.isBlank() || "transcribe".equalsIgnoreCase(value)) {
            return TRANSCRIBE;
        }
        if ("translate".equalsIgnoreCase(value)) {
            return TRANSLATE;
        }
        throw new IllegalArgumentException("Unsupported task: " + value + ". Use transcribe or translate.");
    }
}
