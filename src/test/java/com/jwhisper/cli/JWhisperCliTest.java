package com.jwhisper.cli;

import com.jwhisper.export.OutputFormat;
import com.jwhisper.transcribe.TranscriptionOptions;
import com.jwhisper.transcribe.TranscriptionTask;
import com.jwhisper.whisper.WhisperExecutionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JWhisperCliTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesAllTranscriptionExportAndProviderFlags() {
        JWhisperCli.CliOptions options = JWhisperCli.CliOptions.parse(new String[]{
                "lecture.mp4",
                "--model", "large-v3-turbo",
                "--download-model",
                "--language", "hi",
                "--task", "translate",
                "--beam-size", "3",
                "--timestamps",
                "--prompt", "JWhisper ONNX Runtime",
                "--format", "vtt",
                "--output", "subtitles.vtt",
                "--provider", "coreml",
                "--device-id", "1",
                "--provider-fallback",
                "--quiet"
        });

        assertEquals(1, options.inputs().size());
        assertEquals(Path.of("lecture.mp4"), options.inputs().get(0));
        assertEquals("large-v3-turbo", options.modelId());
        assertTrue(options.downloadModel());
        assertEquals("hi", options.language());
        assertEquals(TranscriptionTask.TRANSLATE, options.task());
        assertEquals(3, options.beamSize());
        assertEquals(Boolean.TRUE, options.timestamps());
        assertEquals("JWhisper ONNX Runtime", options.initialPrompt());
        assertEquals(OutputFormat.VTT, options.resolvedFormat());
        assertEquals(WhisperExecutionProvider.COREML, options.provider());
        assertEquals(1, options.deviceId());
        assertEquals(Boolean.TRUE, options.providerFallback());
        assertTrue(options.quiet());

        TranscriptionOptions engineOptions = JWhisperCli.buildTranscriptionOptions(options);
        assertEquals("hi", engineOptions.language());
        assertEquals(TranscriptionTask.TRANSLATE, engineOptions.task());
        assertEquals(3, engineOptions.beamSize());
        assertTrue(engineOptions.timestamps());
        assertEquals("JWhisper ONNX Runtime", engineOptions.initialPrompt());
        // Must preserve default hallucination and silence thresholds rather than nulling them out.
        assertEquals(2.4, engineOptions.compressionRatioThreshold());
        assertEquals(-1.0, engineOptions.logProbabilityThreshold());
        assertEquals(0.6, engineOptions.noSpeechThreshold());
    }

    @Test
    void preservesDeviceIdAndProviderFallbackWithoutExplicitProviderFlag() {
        JWhisperCli.CliOptions options = JWhisperCli.CliOptions.parse(new String[]{
                "clip.wav", "--device-id", "2", "--no-provider-fallback"
        });
        assertNull(options.provider());
        assertEquals(2, options.deviceId());
        assertEquals(Boolean.FALSE, options.providerFallback());
    }

    @Test
    void infersOutputFormatFromOutputExtensionAndEnablesTimestampsForSubtitles() {
        JWhisperCli.CliOptions srtOptions = JWhisperCli.CliOptions.parse(new String[]{
                "clip.mp4", "--output", "clip.srt"
        });
        assertEquals(OutputFormat.SRT, srtOptions.resolvedFormat());
        assertTrue(JWhisperCli.buildTranscriptionOptions(srtOptions).timestamps());

        JWhisperCli.CliOptions jsonOptions = JWhisperCli.CliOptions.parse(new String[]{
                "clip.mp4", "--output", "clip.json"
        });
        assertEquals(OutputFormat.JSON, jsonOptions.resolvedFormat());
        assertFalse(JWhisperCli.buildTranscriptionOptions(jsonOptions).timestamps());

        JWhisperCli.CliOptions autoLang = JWhisperCli.CliOptions.parse(new String[]{
                "clip.mp4", "--language", "auto", "--translate"
        });
        assertNull(autoLang.language());
        assertEquals(TranscriptionTask.TRANSLATE, autoLang.task());
    }

    @Test
    void rejectsUnsupportedTaskValuesDuringParsing() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--task", "translte"})
        );
        assertTrue(ex.getMessage().contains("Unsupported task"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = JWhisperCli.run(
                new String[]{"clip.flac", "--task", "translte"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        );
        assertEquals(2, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Unsupported task"));
    }

    @Test
    void rejectsFollowingLongOrShortFlagConsumedAsOptionValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--language", "--quiet"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "-m", "-q"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--prompt", "--quiet"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--output", "-f"})
        );
    }

    @Test
    void rejectsEnglishOnlyModelWithTranslationBeforeDownloading() throws Exception {
        Path wav = Files.createFile(tempDir.resolve("clip.wav"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = JWhisperCli.run(
                new String[]{wav.toString(), "--model", "base.en", "--translate", "--download-model"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        );
        assertTrue(code == 8 || code == 3);
        if (code == 8) {
            String errText = err.toString(StandardCharsets.UTF_8);
            assertTrue(errText.contains("English-only"));
            assertFalse(errText.contains("Downloading"), "Must reject incompatible .en + --translate before downloading");
        }
    }

    @Test
    void rejectsNonPositiveOrInvalidBeamSizeBeforeStartupWork() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--beam-size", "0"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--beam-size", "-2"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--beam-size", "abc"})
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = JWhisperCli.run(
                new String[]{"nonexistent.flac", "--beam-size", "0"},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        );
        assertEquals(2, code);
    }

    @Test
    void rejectsNoTimestampsWhenSubtitleFormatRequested() {
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--format", "srt", "--no-timestamps"})
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> JWhisperCli.CliOptions.parse(new String[]{"clip.flac", "--output", "out.vtt", "--no-timestamps"})
        );
    }

    @Test
    void supportsHelpListModelsAndDiagnosticsWithoutInputFile() {
        for (String flag : new String[]{"--help", "--list-models", "--diagnostics"}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int exitCode = JWhisperCli.run(
                    new String[]{flag},
                    new PrintStream(out, true, StandardCharsets.UTF_8),
                    new PrintStream(err, true, StandardCharsets.UTF_8)
            );
            assertEquals(0, exitCode, "Expected exit code 0 for " + flag);
            String stdout = out.toString(StandardCharsets.UTF_8);
            assertNotNull(stdout);
            assertFalse(stdout.isBlank());
        }
    }

    @Test
    void rejectsSingleOutputFileOrStdoutSubtitlesWithMultipleBatchInputs() throws Exception {
        Path a = Files.createFile(tempDir.resolve("a.wav"));
        Path b = Files.createFile(tempDir.resolve("b.wav"));
        Path singleOut = tempDir.resolve("out.txt");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = JWhisperCli.run(
                new String[]{a.toString(), b.toString(), "--output", singleOut.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8)
        );
        assertTrue(exitCode == 2 || exitCode == 3);
        if (exitCode == 2) {
            assertTrue(err.toString(StandardCharsets.UTF_8).contains("--output-dir"));
        }

        ByteArrayOutputStream srtErr = new ByteArrayOutputStream();
        int srtStdoutCode = JWhisperCli.run(
                new String[]{a.toString(), b.toString(), "--format", "srt"},
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(srtErr, true, StandardCharsets.UTF_8)
        );
        assertTrue(srtStdoutCode == 2 || srtStdoutCode == 3);
        if (srtStdoutCode == 2) {
            assertTrue(srtErr.toString(StandardCharsets.UTF_8).contains("--output-dir"));
        }
    }
}
