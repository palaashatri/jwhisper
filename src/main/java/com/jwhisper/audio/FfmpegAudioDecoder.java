package com.jwhisper.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class FfmpegAudioDecoder {
    public static final int SAMPLE_RATE = 16_000;

    public float[] decodeToMono16k(Path inputFile) throws IOException, InterruptedException {
        List<String> command = List.of(
                "ffmpeg",
                "-v", "error",
                "-i", inputFile.toString(),
                "-f", "f32le",
                "-ac", "1",
                "-ar", String.valueOf(SAMPLE_RATE),
                "pipe:1"
        );

        Process process = new ProcessBuilder(command).start();
        CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(() -> {
            try {
                return process.getInputStream().readAllBytes();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });

        try {
            int exitCode = process.waitFor();
            byte[] data = joinBytes(stdout);
            String error = joinString(stderr);
            if (exitCode != 0 || data.length == 0) {
                throw new IOException(error == null || error.isBlank()
                        ? "Something went wrong. Try another file."
                        : "Something went wrong. Try another file.");
            }

            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            float[] samples = new float[data.length / Float.BYTES];
            for (int i = 0; i < samples.length; i++) {
                samples[i] = buffer.getFloat();
            }
            return samples;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private byte[] joinBytes(CompletableFuture<byte[]> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    private String joinString(CompletableFuture<String> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }
}
