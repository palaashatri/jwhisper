package com.jwhisper.whisper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

public record WhisperPreprocessorConfig(
        int featureSize,
        int hopLength,
        int nFft,
        int nSamples,
        int nbMaxFrames,
        int samplingRate
) {
    public static WhisperPreprocessorConfig from(Path path) throws IOException {
        JsonNode root = new ObjectMapper().readTree(path.toFile());
        return new WhisperPreprocessorConfig(
                root.path("feature_size").asInt(80),
                root.path("hop_length").asInt(160),
                root.path("n_fft").asInt(400),
                root.path("n_samples").asInt(480_000),
                root.path("nb_max_frames").asInt(3000),
                root.path("sampling_rate").asInt(16_000)
        );
    }
}
