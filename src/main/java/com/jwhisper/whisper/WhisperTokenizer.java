package com.jwhisper.whisper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WhisperTokenizer {
    private static final Pattern PRETOKEN = Pattern.compile(
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)| ?\\p{L}+[\\p{M}]*| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+"
    );

    private final Map<Integer, String> idToToken;
    private final Map<String, Integer> tokenToId;
    private final Set<Integer> specialTokenIds;
    private final Map<Integer, Integer> byteDecoder;
    private final Map<Integer, Integer> byteEncoder;
    private final Map<Pair, Integer> mergeRanks;

    private WhisperTokenizer(
            Map<Integer, String> idToToken,
            Set<Integer> specialTokenIds,
            Map<Pair, Integer> mergeRanks
    ) {
        this.idToToken = Map.copyOf(idToToken);
        Map<String, Integer> reverse = new HashMap<>();
        idToToken.forEach((id, token) -> reverse.put(token, id));
        this.tokenToId = Map.copyOf(reverse);
        this.specialTokenIds = Set.copyOf(specialTokenIds);
        this.byteDecoder = byteDecoder();
        Map<Integer, Integer> encoder = new HashMap<>();
        byteDecoder.forEach((codePoint, value) -> encoder.put(value, codePoint));
        this.byteEncoder = Map.copyOf(encoder);
        this.mergeRanks = Map.copyOf(mergeRanks);
    }

    public static WhisperTokenizer from(Path tokenizerJson, Path tokenizerConfigJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tokenizer = mapper.readTree(tokenizerJson.toFile());
        Map<Integer, String> idToToken = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = tokenizer.path("model").path("vocab").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            idToToken.put(field.getValue().asInt(), field.getKey());
        }

        Map<Pair, Integer> mergeRanks = new HashMap<>();
        JsonNode merges = tokenizer.path("model").path("merges");
        if (merges.isArray()) {
            int rank = 0;
            for (JsonNode merge : merges) {
                String left;
                String right;
                if (merge.isArray() && merge.size() >= 2) {
                    left = merge.get(0).asText();
                    right = merge.get(1).asText();
                } else {
                    String text = merge.asText();
                    int split = text.indexOf(' ');
                    if (split <= 0 || split >= text.length() - 1) {
                        rank++;
                        continue;
                    }
                    left = text.substring(0, split);
                    right = text.substring(split + 1);
                }
                mergeRanks.put(new Pair(left, right), rank++);
            }
        }

        Set<Integer> specialTokenIds = new HashSet<>();
        if (tokenizerConfigJson != null && tokenizerConfigJson.toFile().isFile()) {
            JsonNode config = mapper.readTree(tokenizerConfigJson.toFile());
            Iterator<Map.Entry<String, JsonNode>> specialFields = config.path("added_tokens_decoder").fields();
            while (specialFields.hasNext()) {
                Map.Entry<String, JsonNode> field = specialFields.next();
                if (field.getValue().path("special").asBoolean(false)) {
                    specialTokenIds.add(Integer.parseInt(field.getKey()));
                }
            }
        }
        idToToken.forEach((id, token) -> {
            if (token.startsWith("<|") && token.endsWith("|>")) specialTokenIds.add(id);
        });
        return new WhisperTokenizer(idToToken, specialTokenIds, mergeRanks);
    }

    public OptionalInt tokenId(String token) {
        Integer id = tokenToId.get(token);
        return id == null ? OptionalInt.empty() : OptionalInt.of(id);
    }

    public boolean isSpecial(int tokenId) {
        return specialTokenIds.contains(tokenId);
    }

    /** Encodes ordinary prompt text with the byte-level BPE stored in tokenizer.json. */
    public List<Long> encode(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<Long> ids = new ArrayList<>();
        Matcher matcher = PRETOKEN.matcher(text);
        while (matcher.find()) {
            String encoded = byteEncode(matcher.group());
            for (String piece : bpe(encoded)) {
                Integer id = tokenToId.get(piece);
                if (id == null) {
                    throw new IllegalArgumentException("Tokenizer vocabulary is missing BPE token: " + piece);
                }
                ids.add(id.longValue());
            }
        }
        return List.copyOf(ids);
    }

    public String decode(Iterable<Long> tokenIds) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Long tokenId : tokenIds) {
            if (tokenId == null || specialTokenIds.contains(tokenId.intValue())) continue;
            String token = idToToken.get(tokenId.intValue());
            if (token == null || token.isBlank()) continue;
            appendTokenBytes(token, bytes);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String byteEncode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = raw & 0xFF;
            int codePoint = byteEncoder.getOrDefault(unsigned, unsigned);
            out.appendCodePoint(codePoint);
        }
        return out.toString();
    }

    private List<String> bpe(String token) {
        List<String> word = token.codePoints()
                .mapToObj(cp -> new String(Character.toChars(cp)))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (word.size() < 2 || mergeRanks.isEmpty()) return word;

        while (word.size() > 1) {
            Pair best = null;
            int bestRank = Integer.MAX_VALUE;
            for (int i = 0; i < word.size() - 1; i++) {
                Pair pair = new Pair(word.get(i), word.get(i + 1));
                Integer rank = mergeRanks.get(pair);
                if (rank != null && rank < bestRank) {
                    best = pair;
                    bestRank = rank;
                }
            }
            if (best == null) break;

            List<String> merged = new ArrayList<>(word.size());
            for (int i = 0; i < word.size(); ) {
                if (i < word.size() - 1 && word.get(i).equals(best.left()) && word.get(i + 1).equals(best.right())) {
                    merged.add(best.left() + best.right());
                    i += 2;
                } else {
                    merged.add(word.get(i++));
                }
            }
            word = merged;
        }
        return word;
    }

    private void appendTokenBytes(String token, ByteArrayOutputStream bytes) {
        for (int i = 0; i < token.length(); ) {
            int codePoint = token.codePointAt(i);
            Integer decodedByte = byteDecoder.get(codePoint);
            if (decodedByte != null) {
                bytes.write(decodedByte);
            } else {
                bytes.writeBytes(new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8));
            }
            i += Character.charCount(codePoint);
        }
    }

    private static Map<Integer, Integer> byteDecoder() {
        int[][] ranges = {{'!', '~'}, {'\u00A1', '\u00AC'}, {'\u00AE', '\u00FF'}};
        Set<Integer> baseBytes = new HashSet<>();
        List<Integer> bytes = new ArrayList<>();
        List<Integer> chars = new ArrayList<>();
        for (int[] range : ranges) {
            for (int value = range[0]; value <= range[1]; value++) {
                baseBytes.add(value);
                bytes.add(value);
                chars.add(value);
            }
        }
        int n = 0;
        for (int value = 0; value < 256; value++) {
            if (!baseBytes.contains(value)) {
                bytes.add(value);
                chars.add(256 + n++);
            }
        }
        Map<Integer, Integer> decoder = new HashMap<>();
        for (int i = 0; i < bytes.size(); i++) decoder.put(chars.get(i), bytes.get(i));
        return decoder;
    }

    private record Pair(String left, String right) {}
}
