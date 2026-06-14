package com.jwhisper.deps;

import ai.onnxruntime.OrtEnvironment;
import com.jwhisper.platform.PlatformAgent;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class DependencyAgent {
    private final PlatformAgent platformAgent;

    public DependencyAgent(PlatformAgent platformAgent) {
        this.platformAgent = platformAgent;
    }

    public DependencyReport checkStartupDependencies() {
        DependencyReport report = new DependencyReport();
        try {
            platformAgent.ensureAppDirectories();
        } catch (IOException e) {
            report.add(new DependencyIssue(
                    "Model folder is not writable.",
                    "Check permissions for " + platformAgent.modelDirectory() + ".",
                    true
            ));
        }

        if (!isFfmpegAvailable(Duration.ofSeconds(3))) {
            report.add(new DependencyIssue(
                    "ffmpeg is required. Install it and restart jwhisper.",
                    platformAgent.ffmpegInstallHint(),
                    true
            ));
        }

        try {
            OrtEnvironment.getEnvironment();
        } catch (Throwable e) {
            report.add(new DependencyIssue(
                    "ONNX Runtime not available. Reinstall jwhisper.",
                    null,
                    true
            ));
        }

        return report;
    }

    public boolean hasDiskSpace(long bytesNeeded) {
        try {
            Files.createDirectories(platformAgent.modelDirectory());
            FileStore store = Files.getFileStore(platformAgent.modelDirectory());
            return store.getUsableSpace() > bytesNeeded;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isFfmpegAvailable(Duration timeout) {
        Process process = null;
        try {
            process = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return exited && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
