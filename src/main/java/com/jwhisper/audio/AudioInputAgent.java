package com.jwhisper.audio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AudioInputAgent {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "wav", "mp3", "m4a", "flac", "ogg", "aac",
            "mp4", "mkv", "mov", "webm", "avi", "m4v"
    );

    public AudioValidationResult validate(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return AudioValidationResult.error("Unsupported media file. Try an audio or video file.");
        }
        String extension = extension(file);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            return AudioValidationResult.error("Unsupported media file. Try an audio or video file.");
        }
        return AudioValidationResult.ok(new AudioJob(file.toAbsolutePath().normalize(), null));
    }

    public AudioValidationResult fromDroppedFiles(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return AudioValidationResult.error("Unsupported media file. Try an audio or video file.");
        }
        AudioValidationResult result = validate(files.get(0));
        if (result.isOk() && files.size() > 1) {
            return AudioValidationResult.ok(new AudioJob(
                    result.job().orElseThrow().file(),
                    "Using the first file."
            ));
        }
        return result;
    }

    private String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
