package com.jwhisper.model;

public record ModelDownloadState(
        ModelDescriptor descriptor,
        ModelDownloadStatus status,
        double fraction,
        String message,
        String errorMessage
) {
    public boolean isActive() {
        return status == ModelDownloadStatus.PENDING || status == ModelDownloadStatus.RUNNING;
    }

    public int percent() {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 100.0);
    }
}
