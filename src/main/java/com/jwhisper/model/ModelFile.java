package com.jwhisper.model;

import java.net.URI;

public record ModelFile(String relativePath, long expectedBytes, String sha256) {
    public URI downloadUri(String repository) {
        String path = relativePath.replace(" ", "%20");
        return URI.create("https://huggingface.co/" + repository + "/resolve/main/" + path + "?download=true");
    }
}
