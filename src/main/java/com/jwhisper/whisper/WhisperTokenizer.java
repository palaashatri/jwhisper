package com.jwhisper.whisper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class WhisperTokenizer {
    private final Map<Integer, String> idToToken;
    private final Set<Integer> specialTokenIds;
    private final Map<Integer, Integer> byteDecoder;

    private WhisperTokenizer(Map<Integer, String> idToToken, Set<Integer> specialTokenIds) {
        this.idToToken = idToToken;
        this.specialTokenIds = specialTokenIds;
        this.byteDecoder = byteDecoder();
    }

    public static WhisperTokenizer from(Path tokenizerJson, Path tokenizerConfigJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode tokenizer = mapper.readTree(tokenizerJson.toFile());
        Map<Integer, String> idToToken = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = tokenizer.path("model").path("vocab").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            idToToken.put(field.getValue().asInt(), field.getKey());
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
            if (token.startsWith("<|") && token.endsWith("|>")) {
                specialTokenIds.add(id);
            }
        });
        return new WhisperTokenizer(idToToken, specialTokenIds);
    }

    public String decode(Iterable<Long> tokenIds) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Long tokenId : tokenIds) {
            if (tokenId == null || specialTokenIds.contains(tokenId.intValue())) {
                continue;
            }
            String token = idToToken.get(tokenId.intValue());
            if (token == null || token.isBlank()) {
                continue;
            }
            appendTokenBytes(token, bytes);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void appendTokenBytes(String token, ByteArrayOutputStream bytes) {
        for (int i = 0; i < token.length(); ) {
            int codePoint = token.codePointAt(i);
            Integer decodedByte = byteDecoder.get(codePoint);
            if (decodedByte != null) {
                bytes.write(decodedByte);
            } else {
                byte[] fallback = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                bytes.writeBytes(fallback);
            }
            i += Character.charCount(codePoint);
        }
    }

    private static Map<Integer, Integer> byteDecoder() {
        int[][] ranges = {
                {'!', '~'},
                {'\u00A1', '\u00AC'},
                {'\u00AE', '\u00FF'}
        };
        Set<Integer> baseBytes = new HashSet<>();
        java.util.List<Integer> bytes = new java.util.ArrayList<>();
        java.util.List<Integer> chars = new java.util.ArrayList<>();
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
                chars.add(256 + n);
                n++;
            }
        }
        Map<Integer, Integer> decoder = new HashMap<>();
        for (int i = 0; i < bytes.size(); i++) {
            decoder.put(chars.get(i), bytes.get(i));
        }
        return decoder;
    }
}
