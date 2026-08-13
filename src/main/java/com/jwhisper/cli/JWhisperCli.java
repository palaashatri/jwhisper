package com.jwhisper.cli;

import com.jwhisper.audio.AudioInputAgent;
import com.jwhisper.audio.AudioValidationResult;
import com.jwhisper.deps.DependencyAgent;
import com.jwhisper.deps.DependencyReport;
import com.jwhisper.model.ModelCatalog;
import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.model.ModelManagerAgent;
import com.jwhisper.platform.PlatformAgent;
import com.jwhisper.transcribe.TranscriptionAgent;
import com.jwhisper.transcribe.TranscriptionListener;
import com.jwhisper.whisper.WhisperOnnxEngine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

public final class JWhisperCli {
    private JWhisperCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            return 2;
        }

        if (options.help()) {
            printUsage();
            return 0;
        }

        PlatformAgent platformAgent = new PlatformAgent();
        DependencyAgent dependencyAgent = new DependencyAgent(platformAgent);
        DependencyReport dependencyReport = dependencyAgent.checkStartupDependencies();
        if (!dependencyReport.canTranscribe()) {
            System.err.println(dependencyReport.firstUserMessage());
            return 3;
        }

        AudioValidationResult media = new AudioInputAgent().validate(options.input());
        if (!media.isOk()) {
            System.err.println(media.message().orElse("Unsupported media file."));
            return 4;
        }

        ModelDescriptor model = ModelCatalog.find(options.modelId()).orElse(null);
        if (model == null) {
            System.err.println("Unknown model: " + options.modelId());
            return 5;
        }

        ModelManagerAgent modelManager = new ModelManagerAgent(platformAgent.modelDirectory(), dependencyAgent);
        if (!modelManager.isInstalled(model)) {
            if (!options.downloadModel()) {
                System.err.println("Model " + model.id() + " is not installed. Re-run with --download-model.");
                return 6;
            }
            try {
                System.err.println("Downloading " + model.displayName() + "...");
                modelManager.downloadModel(model, (fraction, message) -> {
                    int percent = (int) Math.round(fraction * 100.0);
                    System.err.printf("\r[%3d%%] %s", percent, message);
                });
                System.err.println();
            } catch (Exception e) {
                System.err.println("Model download failed: " + firstMessage(e));
                return 7;
            }
        }

        try (TranscriptionAgent transcription = new TranscriptionAgent(
                modelManager,
                dependencyReport,
                new WhisperOnnxEngine()
        )) {
            String transcript = transcription.transcribe(
                    media.job().orElseThrow(),
                    model,
                    new ConsoleListener(options.quiet())
            ).join();

            if (options.output() != null) {
                Path parent = options.output().toAbsolutePath().normalize().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(options.output(), transcript + System.lineSeparator(), StandardCharsets.UTF_8);
                if (!options.quiet()) {
                    System.err.println("Transcript written to " + options.output().toAbsolutePath().normalize());
                }
            } else {
                System.out.println(transcript);
            }
            return 0;
        } catch (CompletionException e) {
            System.err.println("Transcription failed: " + firstMessage(e.getCause() == null ? e : e.getCause()));
            return 8;
        } catch (Exception e) {
            System.err.println("Transcription failed: " + firstMessage(e));
            return 8;
        }
    }

    private static String firstMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage().lines().findFirst().orElse("Unknown error");
            }
            current = current.getCause();
        }
        return "Unknown error";
    }

    private static void printUsage() {
        System.out.println("Usage: jwhisper-cli <audio-or-video> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --model <id>          Whisper model (default: tiny.en)");
        System.out.println("  --download-model      Download the selected model if missing");
        System.out.println("  --output <file>       Write transcript to a UTF-8 text file");
        System.out.println("  --quiet               Suppress progress/status output");
        System.out.println("  --help                Show this help");
    }

    private record CliOptions(
            Path input,
            String modelId,
            boolean downloadModel,
            Path output,
            boolean quiet,
            boolean help
    ) {
        private static CliOptions parse(String[] args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("Missing input media file.");
            }
            if (args.length == 1 && "--help".equals(args[0])) {
                return new CliOptions(null, "tiny.en", false, null, false, true);
            }

            Path input = Path.of(args[0]);
            String model = "tiny.en";
            boolean download = false;
            Path output = null;
            boolean quiet = false;

            for (int i = 1; i < args.length; i++) {
                switch (args[i]) {
                    case "--model" -> {
                        if (++i >= args.length) {
                            throw new IllegalArgumentException("--model requires a value.");
                        }
                        model = args[i];
                    }
                    case "--download-model" -> download = true;
                    case "--output" -> {
                        if (++i >= args.length) {
                            throw new IllegalArgumentException("--output requires a value.");
                        }
                        output = Path.of(args[i]);
                    }
                    case "--quiet" -> quiet = true;
                    case "--help" -> {
                        return new CliOptions(input, model, download, output, quiet, true);
                    }
                    default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }
            return new CliOptions(input, model, download, output, quiet, false);
        }
    }

    private static final class ConsoleListener implements TranscriptionListener {
        private final boolean quiet;
        private int lastPercent = -1;

        private ConsoleListener(boolean quiet) {
            this.quiet = quiet;
        }

        @Override
        public void onStatus(String message) {
            if (!quiet) {
                System.err.println(message);
            }
        }

        @Override
        public void onProgress(double fraction) {
            if (quiet) {
                return;
            }
            int percent = (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * 100.0);
            if (percent != lastPercent && (percent == 100 || percent - lastPercent >= 5)) {
                lastPercent = percent;
                System.err.println("Progress: " + percent + "%");
            }
        }
    }
}
