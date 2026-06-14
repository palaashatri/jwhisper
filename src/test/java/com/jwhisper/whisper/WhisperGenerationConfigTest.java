package com.jwhisper.whisper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WhisperGenerationConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsInitialTokensFromDecoderStartAndForcedIds() throws Exception {
        Path config = tempDir.resolve("generation_config.json");
        Files.writeString(config, """
                {
                  "decoder_start_token_id": 50257,
                  "eos_token_id": 50256,
                  "pad_token_id": 50256,
                  "no_timestamps_token_id": 50362,
                  "max_length": 448,
                  "forced_decoder_ids": [[1, 50362]],
                  "suppress_tokens": [1, 2],
                  "begin_suppress_tokens": [220, 50256]
                }
                """);

        WhisperGenerationConfig generationConfig = WhisperGenerationConfig.from(config);

        assertEquals(List.of(50257L, 50362L), generationConfig.initialTokens());
        assertEquals(50256, generationConfig.eosTokenId());
        assertTrue(generationConfig.suppressTokens().contains(1));
        assertTrue(generationConfig.beginSuppressTokens().contains(220));
    }
}
