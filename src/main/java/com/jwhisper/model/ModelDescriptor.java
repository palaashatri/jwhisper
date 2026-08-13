package com.jwhisper.model;

import java.util.List;

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

    @Override
    public String toString() {
        return displayName;
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
