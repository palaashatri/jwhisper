package com.jwhisper.audio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class AudioInputAgent {
    private static final List<String> ORDERED_EXTENSIONS = List.of(
            "wav", "mp3", "m4a", "flac", "ogg", "aac",
            "mp4", "mkv", "mov", "webm", "avi", "m4v"
    );
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.copyOf(ORDERED_EXTENSIONS);

    public static List<String> supportedExtensionList() {
        return ORDERED_EXTENSIONS;
    }

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

    public BatchSelection collectMedia(List<Path> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return new BatchSelection(List.of(), List.of("Unsupported media file. Try an audio or video file."));
        }
        Set<Path> uniqueFiles = new LinkedHashSet<>();
        List<String> skipped = new ArrayList<>();
        for (Path input : inputs) {
            if (input == null) {
                continue;
            }
            if (Files.isDirectory(input)) {
                collectDirectoryMedia(input, uniqueFiles, skipped);
            } else {
                AudioValidationResult result = validate(input);
                if (result.isOk()) {
                    uniqueFiles.add(result.job().orElseThrow().file());
                } else {
                    skipped.add(input.getFileName() + ": " + result.message().orElse("Unsupported media file."));
                }
            }
        }
        List<AudioJob> jobs = uniqueFiles.stream()
                .map(path -> new AudioJob(path, null))
                .toList();
        return new BatchSelection(jobs, List.copyOf(skipped));
    }

    private void collectDirectoryMedia(Path directory, Set<Path> uniqueFiles, List<String> skipped) {
        try (Stream<Path> walk = Files.walk(directory)) {
            List<Path> mediaInFolder = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !isHiddenWithin(directory, path))
                    .filter(path -> SUPPORTED_EXTENSIONS.contains(extension(path)))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.naturalOrder())
                    .toList();
            if (mediaInFolder.isEmpty()) {
                skipped.add(directory.getFileName() + ": No supported audio or video files found in folder.");
            } else {
                uniqueFiles.addAll(mediaInFolder);
            }
        } catch (IOException e) {
            skipped.add(directory.getFileName() + ": Could not read folder.");
        }
    }

    private boolean isHiddenWithin(Path root, Path candidate) {
        Path relative = root.relativize(candidate);
        for (Path part : relative) {
            String name = part.toString();
            if (name.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record BatchSelection(List<AudioJob> jobs, List<String> skipped) {
        public BatchSelection {
            jobs = jobs == null ? List.of() : List.copyOf(jobs);
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
        }

        public boolean hasJobs() {
            return !jobs.isEmpty();
        }
    }
}
