package com.jwhisper.model;

@FunctionalInterface
public interface DownloadProgressListener {
    void onProgress(double fraction, String status);
}
