package com.jwhisper.model;

@FunctionalInterface
public interface ModelDownloadListener {
    void onModelDownloadChanged(ModelDownloadState state);
}
