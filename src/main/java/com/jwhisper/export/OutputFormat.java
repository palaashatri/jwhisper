package com.jwhisper.export;

public enum OutputFormat {
    TXT("txt"),
    JSON("json"),
    SRT("srt"),
    VTT("vtt");

    private final String extension;

    OutputFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static OutputFormat parse(String value) {
        if (value == null || value.isBlank()) return TXT;
        for (OutputFormat format : values()) {
            if (format.name().equalsIgnoreCase(value) || format.extension.equalsIgnoreCase(value)) return format;
        }
        throw new IllegalArgumentException("Unsupported output format: " + value + ". Use txt, json, srt, or vtt.");
    }
}
