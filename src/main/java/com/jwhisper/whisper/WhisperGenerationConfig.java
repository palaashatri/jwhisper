package com.jwhisper.whisper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwhisper.transcribe.TranscriptionOptions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WhisperGenerationConfig {
    private final int decoderStartTokenId;
    private final int eosTokenId;
    private final int padTokenId;
    private final int noTimestampsTokenId;
    private final int maxInitialTimestampIndex;
    private final int maxLength;
    private final boolean multilingual;
    private final List<ForcedToken> forcedDecoderIds;
    private final Set<Integer> suppressTokens;
    private final Set<Integer> beginSuppressTokens;
    private final Map<String, Integer> languageToId;
    private final Map<Integer, String> idToLanguage;
    private final Map<String, Integer> taskToId;

    private WhisperGenerationConfig(
            int decoderStartTokenId,
            int eosTokenId,
            int padTokenId,
            int noTimestampsTokenId,
            int maxInitialTimestampIndex,
            int maxLength,
            boolean multilingual,
            List<ForcedToken> forcedDecoderIds,
            Set<Integer> suppressTokens,
            Set<Integer> beginSuppressTokens,
            Map<String, Integer> languageToId,
            Map<String, Integer> taskToId
    ) {
        this.decoderStartTokenId = decoderStartTokenId;
        this.eosTokenId = eosTokenId;
        this.padTokenId = padTokenId;
        this.noTimestampsTokenId = noTimestampsTokenId;
        this.maxInitialTimestampIndex = maxInitialTimestampIndex;
        this.maxLength = maxLength;
        this.multilingual = multilingual;
        this.forcedDecoderIds = List.copyOf(forcedDecoderIds);
        this.suppressTokens = Set.copyOf(suppressTokens);
        this.beginSuppressTokens = Set.copyOf(beginSuppressTokens);
        this.languageToId = Map.copyOf(languageToId);
        Map<Integer, String> reverse = new HashMap<>();
        languageToId.forEach((language, id) -> reverse.put(id, language));
        this.idToLanguage = Map.copyOf(reverse);
        this.taskToId = Map.copyOf(taskToId);
    }

    public static WhisperGenerationConfig from(Path path) throws IOException {
        JsonNode root = new ObjectMapper().readTree(path.toFile());
        return new WhisperGenerationConfig(
                root.path("decoder_start_token_id").asInt(50257),
                root.path("eos_token_id").asInt(50256),
                root.path("pad_token_id").asInt(50256),
                root.path("no_timestamps_token_id").asInt(-1),
                root.path("max_initial_timestamp_index").asInt(50),
                root.path("max_length").asInt(448),
                root.path("is_multilingual").asBoolean(false),
                forcedTokens(root.path("forced_decoder_ids")),
                intSet(root.path("suppress_tokens")),
                intSet(root.path("begin_suppress_tokens")),
                tokenMap(root.path("lang_to_id"), true),
                tokenMap(root.path("task_to_id"), false)
        );
    }

    public int decoderStartTokenId() {
        return decoderStartTokenId;
    }

    public int eosTokenId() {
        return eosTokenId;
    }

    public int noTimestampsTokenId() {
        return noTimestampsTokenId;
    }

    public int timestampBeginTokenId() {
        return noTimestampsTokenId < 0 ? -1 : noTimestampsTokenId + 1;
    }

    public int maxInitialTimestampIndex() {
        return maxInitialTimestampIndex;
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean isMultilingual() {
        return multilingual;
    }

    public Set<Integer> suppressTokens() {
        return suppressTokens;
    }

    public Set<Integer> beginSuppressTokens() {
        return beginSuppressTokens;
    }

    public Map<String, Integer> languageToId() {
        return languageToId;
    }

    public String languageForToken(int tokenId) {
        return idToLanguage.get(tokenId);
    }

    public Integer languageToken(String language) throws WhisperException {
        if (language == null) return null;
        Integer token = languageToId.get(normalizeLanguage(language));
        if (token == null) {
            throw new WhisperException("Language '" + language + "' is not supported by this model.");
        }
        return token;
    }

    /** Backwards-compatible default token sequence. */
    public List<Long> initialTokens() {
        try {
            return initialTokens(TranscriptionOptions.defaults(), null);
        } catch (WhisperException impossibleForDefaults) {
            throw new IllegalStateException(impossibleForDefaults);
        }
    }

    public List<Long> initialTokens(TranscriptionOptions options, Integer detectedLanguageToken)
            throws WhisperException {
        List<Long> tokens = new ArrayList<>();
        tokens.add((long) decoderStartTokenId);

        if (multilingual && !languageToId.isEmpty()) {
            Integer languageToken = options.language() == null
                    ? detectedLanguageToken
                    : languageToken(options.language());
            if (languageToken == null) {
                languageToken = languageToId.get("en");
            }
            if (languageToken == null) {
                throw new WhisperException("Could not determine a language token for this multilingual model.");
            }
            tokens.add(languageToken.longValue());

            Integer taskToken = taskToId.get(options.task().wireName());
            if (taskToken == null) {
                throw new WhisperException("Task '" + options.task().wireName() + "' is not supported by this model.");
            }
            tokens.add(taskToken.longValue());
        } else {
            forcedDecoderIds.stream()
                    .sorted(Comparator.comparingInt(ForcedToken::position))
                    .filter(forced -> forced.tokenId() != noTimestampsTokenId)
                    .forEach(forced -> putAtPosition(tokens, forced.position(), forced.tokenId()));
        }

        if (!options.timestamps() && noTimestampsTokenId >= 0
                && tokens.stream().noneMatch(token -> token == noTimestampsTokenId)) {
            tokens.add((long) noTimestampsTokenId);
        }
        return tokens;
    }

    private static void putAtPosition(List<Long> tokens, int position, int tokenId) {
        while (tokens.size() < position) {
            tokens.add(0L);
        }
        if (tokens.size() == position) {
            tokens.add((long) tokenId);
        } else {
            tokens.set(position, (long) tokenId);
        }
    }

    private static List<ForcedToken> forcedTokens(JsonNode node) {
        List<ForcedToken> tokens = new ArrayList<>();
        if (node == null || !node.isArray()) return tokens;
        for (JsonNode pair : node) {
            if (pair.isArray() && pair.size() >= 2 && !pair.get(1).isNull()) {
                tokens.add(new ForcedToken(pair.get(0).asInt(), pair.get(1).asInt()));
            }
        }
        return tokens;
    }

    private static Set<Integer> intSet(JsonNode node) {
        Set<Integer> values = new HashSet<>();
        if (node == null || !node.isArray()) return values;
        for (JsonNode item : node) values.add(item.asInt());
        return values;
    }

    private static Map<String, Integer> tokenMap(JsonNode node, boolean language) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (node == null || !node.isObject()) return values;
        node.fields().forEachRemaining(entry -> {
            String key = language ? normalizeLanguage(entry.getKey()) : entry.getKey();
            values.put(key, entry.getValue().asInt());
        });
        return values;
    }

    private static String normalizeLanguage(String value) {
        String normalized = value.trim().toLowerCase();
        if (normalized.startsWith("<|") && normalized.endsWith("|>")) {
            normalized = normalized.substring(2, normalized.length() - 2);
        }
        return normalized;
    }

    private record ForcedToken(int position, int tokenId) {}
}
