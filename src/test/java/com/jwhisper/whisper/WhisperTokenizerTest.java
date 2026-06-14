package com.jwhisper.whisper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WhisperTokenizerTest {
    @TempDir
    Path tempDir;

    @Test
    void decodesByteLevelTokensAndSkipsSpecialTokens() throws Exception {
        Path tokenizerJson = tempDir.resolve("tokenizer.json");
        Path tokenizerConfig = tempDir.resolve("tokenizer_config.json");
        Files.writeString(tokenizerJson, """
                {
                  "model": {
                    "vocab": {
                      "\\u0120Hello": 1,
                      "\\u0120world": 2,
                      "!": 3,
                      "<|endoftext|>": 50256
                    }
                  }
                }
                """);
        Files.writeString(tokenizerConfig, """
                {
                  "added_tokens_decoder": {
                    "50256": {
                      "content": "<|endoftext|>",
                      "special": true
                    }
                  }
                }
                """);

        WhisperTokenizer tokenizer = WhisperTokenizer.from(tokenizerJson, tokenizerConfig);

        assertEquals("Hello world!", tokenizer.decode(List.of(1L, 2L, 3L, 50256L)));
    }
}
