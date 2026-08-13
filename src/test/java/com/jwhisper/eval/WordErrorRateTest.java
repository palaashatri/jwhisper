package com.jwhisper.eval;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordErrorRateTest {
    @Test void exactMatchIsZero() {
        assertEquals(0.0, WordErrorRate.compare("My fellow Americans", "my fellow americans").wer());
    }

    @Test void oneSubstitutionIsMeasured() {
        WordErrorRate.Metrics metrics = WordErrorRate.compare("one two three", "one too three");
        assertEquals(1, metrics.edits());
        assertEquals(1.0 / 3.0, metrics.wer(), 1e-9);
    }

    @Test void unicodeWordsArePreserved() {
        assertEquals(0.0, WordErrorRate.compare("नमस्ते दुनिया", "नमस्ते दुनिया").wer());
    }
}
