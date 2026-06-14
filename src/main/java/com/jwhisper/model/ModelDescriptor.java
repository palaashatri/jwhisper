package com.jwhisper.model;

import java.util.List;

public record ModelDescriptor(
        String id,
        String displayName,
        String repository,
        String description,
        long estimatedBytes,
        List<ModelFile> files
) {
    @Override
    public String toString() {
        return displayName;
    }
}
