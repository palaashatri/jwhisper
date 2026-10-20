package com.jwhisper.whisper;

import com.jwhisper.transcribe.TranscriptionOptions;
import com.jwhisper.transcribe.TranscriptionTask;
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

    @Test
    void buildsMultilingualLanguageTaskAndTimestampSequence() throws Exception {
        Path config = tempDir.resolve("multilingual.json");
        Files.writeString(config, """
                {
                  "decoder_start_token_id": 50258,
                  "eos_token_id": 50257,
                  "pad_token_id": 50257,
                  "no_timestamps_token_id": 50363,
                  "max_initial_timestamp_index": 50,
                  "max_length": 448,
                  "is_multilingual": true,
                  "forced_decoder_ids": [[1, null], [2, 50359]],
                  "lang_to_id": {"<|en|>": 50259, "<|hi|>": 50276},
                  "task_to_id": {"transcribe": 50359, "translate": 50358}
                }
                """);

        WhisperGenerationConfig generationConfig = WhisperGenerationConfig.from(config);
        TranscriptionOptions translate = TranscriptionOptions.defaults()
                .withLanguage("hi")
                .withTask(TranscriptionTask.TRANSLATE)
                .withTimestamps(true);

        assertEquals(List.of(50258L, 50276L, 50358L), generationConfig.initialTokens(translate, null));
        assertEquals(50364, generationConfig.timestampBeginTokenId());
        assertEquals("hi", generationConfig.languageForToken(50276));
    }
}
