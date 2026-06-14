package com.jwhisper.model;

import java.util.List;
import java.util.Optional;

public final class ModelCatalog {
    private static final List<ModelFile> STANDARD_FILES = List.of(
            new ModelFile("config.json", 0, null),
            new ModelFile("generation_config.json", 0, null),
            new ModelFile("preprocessor_config.json", 0, null),
            new ModelFile("tokenizer_config.json", 0, null),
            new ModelFile("tokenizer.json", 0, null),
            new ModelFile("onnx/encoder_model.onnx", 0, null),
            new ModelFile("onnx/decoder_model.onnx", 0, null)
    );

    private static final List<ModelDescriptor> AVAILABLE = List.of(
            descriptor("tiny.en", "tiny.en", "onnx-community/whisper-tiny.en", "Fast English model", 156_000_000L),
            descriptor("base.en", "base.en", "onnx-community/whisper-base.en", "Balanced English model", 294_000_000L),
            descriptor("small.en", "small.en", "onnx-community/whisper-small.en", "More accurate English model", 970_000_000L),
            descriptor("medium", "medium", "onnx-community/whisper-medium-ONNX", "Large multilingual model", 3_200_000_000L),
            descriptor("large-v3", "large-v3", "onnx-community/whisper-large-v3-ONNX", "Most accurate multilingual model", 6_500_000_000L)
    );

    private ModelCatalog() {
    }

    public static List<ModelDescriptor> availableModels() {
        return AVAILABLE;
    }

    public static Optional<ModelDescriptor> find(String id) {
        return AVAILABLE.stream().filter(model -> model.id().equals(id)).findFirst();
    }

    private static ModelDescriptor descriptor(String id, String displayName, String repo, String description, long estimatedBytes) {
        return new ModelDescriptor(id, displayName, repo, description, estimatedBytes, STANDARD_FILES);
    }
}
