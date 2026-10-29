package com.jwhisper.model;

import java.util.List;
import java.util.Locale;

public record ModelDescriptor(
        String id,
        String displayName,
        String repository,
        String revision,
        String description,
        long estimatedBytes,
        List<ModelFile> files,
        String architecture,
        String precision,
        String license,
        String provenance,
        String runtimeCompatibility
) {
    public ModelDescriptor {
        files = files == null ? List.of() : List.copyOf(files);
        architecture = blankDefault(architecture, "Whisper encoder-decoder");
        precision = blankDefault(precision, "FP32 ONNX");
        license = blankDefault(license, "See upstream model repository");
        provenance = blankDefault(provenance, repository + "@" + revision);
        runtimeCompatibility = blankDefault(runtimeCompatibility, "ONNX Runtime 1.26+");
    }

    public ModelDescriptor(
            String id,
            String displayName,
            String repository,
            String revision,
            String description,
            long estimatedBytes,
            List<ModelFile> files
    ) {
        this(id, displayName, repository, revision, description, estimatedBytes, files,
                "Whisper encoder-decoder", "FP32 ONNX", "MIT (upstream Whisper)",
                repository + "@" + revision, "ONNX Runtime 1.26+");
    }

    public boolean isEnglishOnly() {
        return id != null && id.toLowerCase(Locale.ROOT).endsWith(".en");
    }

    public boolean isMultilingual() {
        return !isEnglishOnly();
    }

    public String formattedSize() {
        if (estimatedBytes >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1f GB", estimatedBytes / 1_000_000_000.0);
        }
        if (estimatedBytes >= 1_000_000L) {
            return String.format(Locale.ROOT, "%d MB", Math.round(estimatedBytes / 1_000_000.0));
        }
        if (estimatedBytes > 0) {
            return String.format(Locale.ROOT, "%d KB", Math.max(1L, Math.round(estimatedBytes / 1_000.0)));
        }
        return "size unknown";
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
