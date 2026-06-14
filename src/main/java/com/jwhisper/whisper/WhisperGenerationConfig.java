package com.jwhisper.whisper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WhisperGenerationConfig {
    private final int decoderStartTokenId;
    private final int eosTokenId;
    private final int padTokenId;
    private final int noTimestampsTokenId;
    private final int maxLength;
    private final List<ForcedToken> forcedDecoderIds;
    private final Set<Integer> suppressTokens;
    private final Set<Integer> beginSuppressTokens;

    private WhisperGenerationConfig(
            int decoderStartTokenId,
            int eosTokenId,
            int padTokenId,
            int noTimestampsTokenId,
            int maxLength,
            List<ForcedToken> forcedDecoderIds,
            Set<Integer> suppressTokens,
            Set<Integer> beginSuppressTokens
    ) {
        this.decoderStartTokenId = decoderStartTokenId;
        this.eosTokenId = eosTokenId;
        this.padTokenId = padTokenId;
        this.noTimestampsTokenId = noTimestampsTokenId;
        this.maxLength = maxLength;
        this.forcedDecoderIds = forcedDecoderIds;
        this.suppressTokens = suppressTokens;
        this.beginSuppressTokens = beginSuppressTokens;
    }

    public static WhisperGenerationConfig from(Path path) throws IOException {
        JsonNode root = new ObjectMapper().readTree(path.toFile());
        return new WhisperGenerationConfig(
                root.path("decoder_start_token_id").asInt(50257),
                root.path("eos_token_id").asInt(50256),
                root.path("pad_token_id").asInt(50256),
                root.path("no_timestamps_token_id").asInt(-1),
                root.path("max_length").asInt(448),
                forcedTokens(root.path("forced_decoder_ids")),
                intSet(root.path("suppress_tokens")),
                intSet(root.path("begin_suppress_tokens"))
        );
    }

    public int eosTokenId() {
        return eosTokenId;
    }

    public int maxLength() {
        return maxLength;
    }

    public Set<Integer> suppressTokens() {
        return suppressTokens;
    }

    public Set<Integer> beginSuppressTokens() {
        return beginSuppressTokens;
    }

    public List<Long> initialTokens() {
        List<Long> tokens = new ArrayList<>();
        tokens.add((long) decoderStartTokenId);
        forcedDecoderIds.stream()
                .sorted(Comparator.comparingInt(ForcedToken::position))
                .forEach(forced -> {
                    while (tokens.size() < forced.position()) {
                        tokens.add((long) padTokenId);
                    }
                    if (tokens.size() == forced.position()) {
                        tokens.add((long) forced.tokenId());
                    } else {
                        tokens.set(forced.position(), (long) forced.tokenId());
                    }
                });
        if (noTimestampsTokenId >= 0 && tokens.stream().noneMatch(token -> token == noTimestampsTokenId)) {
            tokens.add((long) noTimestampsTokenId);
        }
        return tokens;
    }

    private static List<ForcedToken> forcedTokens(JsonNode node) {
        List<ForcedToken> tokens = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return tokens;
        }
        for (JsonNode pair : node) {
            if (pair.isArray() && pair.size() >= 2 && !pair.get(1).isNull()) {
                tokens.add(new ForcedToken(pair.get(0).asInt(), pair.get(1).asInt()));
            }
        }
        return tokens;
    }

    private static Set<Integer> intSet(JsonNode node) {
        Set<Integer> values = new HashSet<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            values.add(item.asInt());
        }
        return values;
    }

    private record ForcedToken(int position, int tokenId) {
    }
}
