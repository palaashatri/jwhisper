package com.jwhisper.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelCatalogTest {
    @Test
    void catalogIncludesRequiredWhisperFilesForEveryModel() {
        assertFalse(ModelCatalog.availableModels().isEmpty());
        for (ModelDescriptor descriptor : ModelCatalog.availableModels()) {
            assertTrue(hasFile(descriptor, "onnx/encoder_model.onnx"));
            assertTrue(hasFile(descriptor, "onnx/decoder_model.onnx"));
            assertTrue(hasFile(descriptor, "tokenizer.json"));
            assertTrue(hasFile(descriptor, "generation_config.json"));
            assertTrue(hasFile(descriptor, "preprocessor_config.json"));
        }
    }

    @Test
    void baseEnglishModelIsAvailableForTheDefaultUiChoice() {
        assertTrue(ModelCatalog.find("base.en").isPresent());
        assertTrue(ModelCatalog.find("base.en").orElseThrow().isEnglishOnly());
        assertFalse(ModelCatalog.find("base.en").orElseThrow().isMultilingual());
        assertTrue(ModelCatalog.find("large-v3-turbo").orElseThrow().isMultilingual());
        assertTrue(ModelCatalog.find("tiny.en").orElseThrow().formattedSize().contains("MB"));
        assertTrue(ModelCatalog.find("large-v3-turbo").orElseThrow().formattedSize().contains("GB"));
    }

    private boolean hasFile(ModelDescriptor descriptor, String path) {
        return descriptor.files().stream().anyMatch(file -> file.relativePath().equals(path));
    }
}
