package com.jwhisper.deps;

public record DependencyIssue(String message, String hint, boolean blocksTranscription) {
}
