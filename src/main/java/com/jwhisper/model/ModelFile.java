package com.jwhisper.model;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public record ModelFile(String relativePath, long expectedBytes, String sha256) {
    public URI downloadUri(String repository, String revision) {
        String path = encodePath(relativePath);
        String pinnedRevision = revision == null || revision.isBlank() ? "main" : revision;
        return URI.create("https://huggingface.co/" + repository + "/resolve/" + pinnedRevision + "/" + path + "?download=true");
    }

    public boolean hasSha256() {
        return sha256 != null && !sha256.isBlank();
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                encoded.append('/');
            }
            encoded.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }
}
