package com.jwhisper.whisper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WhisperExecutionProviderTest {
    @Test
    void parsesUserFacingProviderAliases() {
        assertEquals(WhisperExecutionProvider.COREML, WhisperExecutionProvider.from("apple-silicon"));
        assertEquals(WhisperExecutionProvider.CUDA, WhisperExecutionProvider.from("nvidia"));
        assertEquals(WhisperExecutionProvider.ROCM, WhisperExecutionProvider.from("amd"));
        assertEquals(WhisperExecutionProvider.TENSORRT, WhisperExecutionProvider.from("trt"));
        assertEquals(WhisperExecutionProvider.GPU, WhisperExecutionProvider.from("gpu"));
        assertEquals(WhisperExecutionProvider.AUTO, WhisperExecutionProvider.from("auto"));
    }
}
