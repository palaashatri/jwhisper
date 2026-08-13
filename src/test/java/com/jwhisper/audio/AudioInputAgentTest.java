package com.jwhisper.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AudioInputAgentTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsSupportedAudioExtensionsCaseInsensitively() throws Exception {
        Path audio = Files.createFile(tempDir.resolve("voice.MP3"));

        AudioValidationResult result = new AudioInputAgent().validate(audio);

        assertTrue(result.isOk());
        assertEquals(audio.toAbsolutePath().normalize(), result.job().orElseThrow().file());
    }

    @Test
    void acceptsSupportedVideoExtensionsForFfmpegAudioExtraction() throws Exception {
        Path video = Files.createFile(tempDir.resolve("BigBuckBunny.MP4"));

        AudioValidationResult result = new AudioInputAgent().validate(video);

        assertTrue(result.isOk());
        assertEquals(video.toAbsolutePath().normalize(), result.job().orElseThrow().file());
    }

    @Test
    void rejectsUnsupportedFilesWithSimpleMessage() throws Exception {
        Path text = Files.createFile(tempDir.resolve("notes.txt"));

        AudioValidationResult result = new AudioInputAgent().validate(text);

        assertTrue(result.message().orElse("").contains("Unsupported media file"));
    }

    @Test
    void droppedFilesUseTheFirstFileAndAddANote() throws Exception {
        Path first = Files.createFile(tempDir.resolve("first.wav"));
        Path second = Files.createFile(tempDir.resolve("second.wav"));

        AudioValidationResult result = new AudioInputAgent().fromDroppedFiles(List.of(first, second));

        assertTrue(result.isOk());
        assertEquals(first.toAbsolutePath().normalize(), result.job().orElseThrow().file());
        assertEquals("Using the first file.", result.job().orElseThrow().note());
    }
}
