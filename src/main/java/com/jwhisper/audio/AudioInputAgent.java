package com.jwhisper.audio;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AudioInputAgent {
    private static final List<String> ORDERED_EXTENSIONS = List.of(
            "wav", "mp3", "m4a", "flac", "ogg", "aac", "opus", "wma", "aiff", "aif", "caf",
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
        List<Path> mediaInFolder = new ArrayList<>();
        int skippedBefore = skipped.size();
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(directory) && isHiddenEntry(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()
                            && !isHiddenEntry(file)
                            && SUPPORTED_EXTENSIONS.contains(extension(file))) {
                        mediaInFolder.add(file.toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    if (file.equals(directory)) {
                        throw exc;
                    }
                    if (isHiddenEntry(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path entryName = file.getFileName();
                    skipped.add((entryName != null ? entryName : file) + ": Could not read entry.");
                    return FileVisitResult.CONTINUE;
                }
            });
            mediaInFolder.sort(Comparator.naturalOrder());
            if (mediaInFolder.isEmpty() && skipped.size() == skippedBefore) {
                skipped.add(directory.getFileName() + ": No supported audio or video files found in folder.");
            } else {
                uniqueFiles.addAll(mediaInFolder);
            }
        } catch (IOException e) {
            skipped.add(directory.getFileName() + ": Could not read folder.");
        }
    }

    private boolean isHiddenEntry(Path candidate) {
        Path fileName = candidate.getFileName();
        return fileName != null && fileName.toString().startsWith(".");
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
