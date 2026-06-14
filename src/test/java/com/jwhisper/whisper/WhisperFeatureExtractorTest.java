package com.jwhisper.whisper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WhisperFeatureExtractorTest {
    @Test
    void producesFiniteThreeDimensionalFeatureTensor() {
        WhisperPreprocessorConfig config = new WhisperPreprocessorConfig(
                4,
                2,
                8,
                16,
                5,
                16_000
        );
        WhisperFeatureExtractor extractor = new WhisperFeatureExtractor(config);

        float[][][] features = extractor.extract(new float[]{0.0f, 0.25f, -0.5f, 0.75f});

        assertEquals(1, features.length);
        assertEquals(4, features[0].length);
        assertEquals(5, features[0][0].length);
        for (float[][] channel : features) {
            for (float[] row : channel) {
                for (float value : row) {
                    assertTrue(Float.isFinite(value));
                }
            }
        }
    }
}
