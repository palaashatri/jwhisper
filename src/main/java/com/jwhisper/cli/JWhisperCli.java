package com.jwhisper.cli;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtProvider;
import com.jwhisper.audio.AudioInputAgent;
import com.jwhisper.audio.AudioJob;
import com.jwhisper.audio.AudioValidationResult;
import com.jwhisper.deps.DependencyAgent;
import com.jwhisper.deps.DependencyIssue;
import com.jwhisper.deps.DependencyReport;
import com.jwhisper.export.OutputFormat;
import com.jwhisper.export.TranscriptionExporter;
import com.jwhisper.model.ModelCatalog;
import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.model.ModelManagerAgent;
import com.jwhisper.platform.BuildProfile;
import com.jwhisper.platform.PlatformAgent;
import com.jwhisper.transcribe.TranscriptionAgent;
import com.jwhisper.transcribe.TranscriptionListener;
import com.jwhisper.transcribe.TranscriptionOptions;
import com.jwhisper.transcribe.TranscriptionResult;
import com.jwhisper.transcribe.TranscriptionTask;
import com.jwhisper.whisper.WhisperExecutionProvider;
import com.jwhisper.whisper.WhisperOnnxEngine;
import com.jwhisper.whisper.WhisperProviderConfig;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
        return run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            printUsage(out);
            return 2;
        }

        if (options.help()) {
            printUsage(out);
            return 0;
        }

        PlatformAgent platformAgent = new PlatformAgent();
        DependencyAgent dependencyAgent = new DependencyAgent(platformAgent);
        ModelManagerAgent modelManager = new ModelManagerAgent(platformAgent.modelDirectory(), dependencyAgent);

        if (options.listModels()) {
            printModels(out, modelManager);
            return 0;
        }

        if (options.diagnostics()) {
            DependencyReport dependencyReport = dependencyAgent.checkStartupDependencies();
            printDiagnostics(out, platformAgent, dependencyReport, modelManager, options);
            return 0;
        }

        DependencyReport dependencyReport = dependencyAgent.checkStartupDependencies();
        if (!dependencyReport.canTranscribe()) {
            err.println(dependencyReport.firstUserMessage());
            return 3;
        }

        AudioInputAgent audioAgent = new AudioInputAgent();
        List<AudioJob> jobs;
        if (options.inputs().size() == 1 && !Files.isDirectory(options.inputs().get(0))) {
            AudioValidationResult single = audioAgent.validate(options.inputs().get(0));
            if (!single.isOk()) {
                err.println(single.message().orElse("Unsupported media file."));
                return 4;
            }
            jobs = List.of(single.job().orElseThrow());
        } else {
            AudioInputAgent.BatchSelection batch = audioAgent.collectMedia(options.inputs());
            for (String skipped : batch.skipped()) {
                if (!options.quiet()) {
                    err.println("Skipping " + skipped);
                }
            }
            if (!batch.hasJobs()) {
                err.println(batch.skipped().isEmpty()
                        ? "Unsupported media file. Try an audio or video file."
                        : batch.skipped().get(0));
                return 4;
            }
            jobs = batch.jobs();
        }

        Path effectiveOutputDir = options.outputDir();
        Path effectiveOutputFile = options.output();
        if (jobs.size() > 1 && effectiveOutputFile != null) {
            if (Files.isDirectory(effectiveOutputFile)) {
                effectiveOutputDir = effectiveOutputFile;
                effectiveOutputFile = null;
            } else {
                err.println("Cannot write multiple input files to a single --output file. Use --output-dir <directory>.");
                return 2;
            }
        }

        ModelDescriptor model = ModelCatalog.find(options.modelId()).orElse(null);
        if (model == null) {
            err.println("Unknown model: " + options.modelId());
            return 5;
        }

        if (!modelManager.isInstalled(model)) {
            if (!options.downloadModel()) {
                err.println("Model " + model.id() + " is not installed. Re-run with --download-model.");
                return 6;
            }
            try {
                if (!options.quiet()) {
                    err.println("Downloading " + model.displayName() + "...");
                }
                modelManager.downloadModel(model, (fraction, message) -> {
                    if (!options.quiet()) {
                        int percent = (int) Math.round(fraction * 100.0);
                        err.printf("\r[%3d%%] %s", percent, message);
                    }
                });
                if (!options.quiet()) {
                    err.println();
                }
            } catch (Exception e) {
                err.println("Model download failed: " + firstMessage(e));
                return 7;
            }
        }

        WhisperProviderConfig providerConfig = resolveProviderConfig(options);
        TranscriptionOptions transcriptionOptions = buildTranscriptionOptions(options);
        OutputFormat format = options.resolvedFormat();

        try (TranscriptionAgent transcription = new TranscriptionAgent(
                modelManager,
                dependencyReport,
                new WhisperOnnxEngine(providerConfig)
        )) {
            Set<Path> usedOutputPaths = new HashSet<>();
            for (int index = 0; index < jobs.size(); index++) {
                AudioJob job = jobs.get(index);
                if (jobs.size() > 1 && !options.quiet()) {
                    err.printf(Locale.ROOT, "[%d/%d] %s%n", index + 1, jobs.size(), job.file().getFileName());
                }

                TranscriptionResult result = transcription.transcribeResult(
                        job,
                        model,
                        transcriptionOptions,
                        new ConsoleListener(options.quiet(), err)
                ).join();

                String rendered = TranscriptionExporter.render(result, format);
                Path target = resolveTargetFile(job.file(), effectiveOutputFile, effectiveOutputDir, format, usedOutputPaths);
                if (target != null) {
                    Path normalizedTarget = target.toAbsolutePath().normalize();
                    Path parent = normalizedTarget.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(normalizedTarget, rendered, StandardCharsets.UTF_8);
                    if (!options.quiet()) {
                        err.println("Transcript written to " + normalizedTarget);
                    }
                } else {
                    if (jobs.size() > 1) {
                        out.println("=== " + job.file().getFileName() + " ===");
                    }
                    out.print(rendered);
                    if (!rendered.endsWith("\n")) {
                        out.println();
                    }
                }
            }
            return 0;
        } catch (CompletionException e) {
            err.println("Transcription failed: " + firstMessage(e.getCause() == null ? e : e.getCause()));
            return 8;
        } catch (Exception e) {
            err.println("Transcription failed: " + firstMessage(e));
            return 8;
        }
    }

    static TranscriptionOptions buildTranscriptionOptions(CliOptions options) {
        OutputFormat format = options.resolvedFormat();
        boolean timestamps = options.timestamps() != null
                ? options.timestamps()
                : (format == OutputFormat.SRT || format == OutputFormat.VTT);
        return TranscriptionOptions.defaults()
                .withLanguage(options.language())
                .withTask(options.task())
                .withBeamSize(options.beamSize())
                .withTimestamps(timestamps)
                .withInitialPrompt(options.initialPrompt());
    }

    static WhisperProviderConfig resolveProviderConfig(CliOptions options) {
        if (options.provider() == null) {
            return WhisperProviderConfig.fromEnvironment();
        }
        int deviceId = options.deviceId() == null ? 0 : options.deviceId();
        return WhisperProviderConfig.of(options.provider(), deviceId, options.providerFallback());
    }

    private static Path resolveTargetFile(
            Path inputFile,
            Path explicitFile,
            Path outputDir,
            OutputFormat format,
            Set<Path> usedOutputPaths
    ) {
        if (explicitFile != null) {
            return explicitFile;
        }
        if (outputDir == null) {
            return null;
        }
        String fileName = inputFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path candidate = outputDir.resolve(stem + "." + format.extension()).toAbsolutePath().normalize();
        int suffix = 2;
        while (!usedOutputPaths.add(candidate)) {
            candidate = outputDir.resolve(stem + "-" + suffix + "." + format.extension()).toAbsolutePath().normalize();
            suffix++;
        }
        return candidate;
    }

    private static void printModels(PrintStream out, ModelManagerAgent modelManager) {
        String defaultId = modelManager.defaultModel().map(ModelDescriptor::id).orElse(null);
        out.println("Available Whisper models (" + modelManager.availableModels().size() + "):");
        for (ModelDescriptor descriptor : modelManager.availableModels()) {
            boolean installed = modelManager.isInstalled(descriptor);
            String state = installed
                    ? (descriptor.id().equals(defaultId) ? "installed, default" : "installed")
                    : "not installed";
            String scope = descriptor.isMultilingual() ? "multilingual" : "english-only";
            out.printf(
                    Locale.ROOT,
                    "  %-16s %-13s %-8s [%-18s] %s%n",
                    descriptor.id(),
                    scope,
                    descriptor.formattedSize(),
                    state,
                    descriptor.description()
            );
        }
    }

    private static void printDiagnostics(
            PrintStream out,
            PlatformAgent platformAgent,
            DependencyReport dependencyReport,
            ModelManagerAgent modelManager,
            CliOptions options
    ) {
        out.println("JWhisper Diagnostics");
        out.println("  version:            " + BuildProfile.version());
        out.println("  runtimeVariant:     " + BuildProfile.runtimeVariant());
        out.println("  onnxArtifact:       " + BuildProfile.onnxRuntimeArtifact());
        out.println("  java:               " + System.getProperty("java.version", "unknown")
                + " (" + System.getProperty("java.vendor", "unknown") + ")");
        out.println("  os:                 " + System.getProperty("os.name", "unknown")
                + " / " + System.getProperty("os.arch", "unknown"));
        out.println("  modelDirectory:     " + platformAgent.modelDirectory());
        try {
            Files.createDirectories(platformAgent.modelDirectory());
            FileStore store = Files.getFileStore(platformAgent.modelDirectory());
            out.printf(Locale.ROOT, "  usableDiskSpace:    %.2f GB%n", store.getUsableSpace() / 1_000_000_000.0);
        } catch (Exception ignored) {
            out.println("  usableDiskSpace:    unavailable");
        }

        WhisperProviderConfig providerConfig = resolveProviderConfig(options);
        out.println("  requestedProvider:  " + providerConfig.describe());
        try {
            EnumSet<OrtProvider> ortProviders = OrtEnvironment.getAvailableProviders();
            out.println("  ortProviders:       " + ortProviders);
            try {
                WhisperExecutionProvider resolved = providerConfig.resolve(ortProviders);
                out.println("  resolvedProvider:   " + resolved.displayName());
            } catch (Exception e) {
                out.println("  resolvedProvider:   unavailable (" + firstMessage(e) + ")");
            }
        } catch (Throwable e) {
            out.println("  ortProviders:       unavailable (" + firstMessage(e) + ")");
        }

        out.println("  canTranscribe:      " + dependencyReport.canTranscribe());
        if (dependencyReport.hasIssues()) {
            for (DependencyIssue issue : dependencyReport.issues()) {
                out.println("  issue:              " + issue.message()
                        + (issue.hint() == null ? "" : " (" + issue.hint() + ")"));
            }
        }
        List<String> installedIds = modelManager.installedModels().stream().map(ModelDescriptor::id).toList();
        out.println("  installedModels:    " + (installedIds.isEmpty() ? "none" : String.join(", ", installedIds)));
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

    private static void printUsage(PrintStream out) {
        out.println("Usage: jwhisper-cli <audio-or-video-or-folder...> [options]");
        out.println("       jwhisper-cli --diagnostics | --list-models | --help");
        out.println();
        out.println("Options:");
        out.println("  --model <id>                  Whisper model (default: tiny.en)");
        out.println("  --download-model              Download the selected model if missing");
        out.println("  --language <code|auto>        Spoken language code, e.g. en, es, hi, ja (default: auto)");
        out.println("  --task <transcribe|translate> Decoding task (default: transcribe)");
        out.println("  --translate                   Shorthand for --task translate");
        out.println("  --beam-size <n>               Beam search width >= 1 (default: 1)");
        out.println("  --timestamps                  Include segment timestamps");
        out.println("  --no-timestamps               Disable segment timestamps (TXT/JSON only)");
        out.println("  --prompt <text>               Optional conditioning prompt / vocabulary hints");
        out.println("  --format <txt|json|srt|vtt>   Output format (default: inferred from --output or txt)");
        out.println("  --output <file>               Write single-file transcript to UTF-8 file");
        out.println("  --output-dir <dir>            Write <name>.<ext> transcript file(s) into directory");
        out.println("  --provider <name>             Execution provider: auto, cpu, coreml, cuda, rocm, tensorrt, gpu");
        out.println("  --device-id <n>               Accelerator device ID >= 0 (default: 0)");
        out.println("  --provider-fallback           Fall back to CPU if the requested provider is unavailable");
        out.println("  --list-models                 List catalog models and installation status");
        out.println("  --diagnostics                 Show runtime, provider, dependency, and model diagnostics");
        out.println("  --quiet                       Suppress progress and status messages");
        out.println("  --help                        Show this help");
    }

    static record CliOptions(
            List<Path> inputs,
            String modelId,
            boolean downloadModel,
            Path output,
            Path outputDir,
            OutputFormat explicitFormat,
            boolean quiet,
            boolean help,
            boolean diagnostics,
            boolean listModels,
            String language,
            TranscriptionTask task,
            int beamSize,
            Boolean timestamps,
            String initialPrompt,
            WhisperExecutionProvider provider,
            Integer deviceId,
            boolean providerFallback
    ) {
        OutputFormat resolvedFormat() {
            if (explicitFormat != null) {
                return explicitFormat;
            }
            if (output != null && output.getFileName() != null) {
                String name = output.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot > 0 && dot < name.length() - 1) {
                    String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                    switch (ext) {
                        case "txt" -> {
                            return OutputFormat.TXT;
                        }
                        case "json" -> {
                            return OutputFormat.JSON;
                        }
                        case "srt" -> {
                            return OutputFormat.SRT;
                        }
                        case "vtt" -> {
                            return OutputFormat.VTT;
                        }
                        default -> {
                            // Fall through to TXT default for arbitrary extensions.
                        }
                    }
                }
            }
            return OutputFormat.TXT;
        }

        static CliOptions parse(String[] args) {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("Missing input media file.");
            }

            List<Path> inputs = new ArrayList<>();
            String model = "tiny.en";
            boolean download = false;
            Path output = null;
            Path outputDir = null;
            OutputFormat explicitFormat = null;
            boolean quiet = false;
            boolean help = false;
            boolean diagnostics = false;
            boolean listModels = false;
            String language = null;
            TranscriptionTask task = TranscriptionTask.TRANSCRIBE;
            int beamSize = 1;
            Boolean timestamps = null;
            String initialPrompt = null;
            WhisperExecutionProvider provider = null;
            Integer deviceId = null;
            boolean providerFallback = false;
            boolean positionalOnly = false;

            for (int i = 0; i < args.length; i++) {
                String token = args[i];
                if (positionalOnly) {
                    inputs.add(Path.of(token));
                    continue;
                }
                if ("--".equals(token)) {
                    positionalOnly = true;
                    continue;
                }
                if (!token.startsWith("-")) {
                    inputs.add(Path.of(token));
                    continue;
                }

                switch (token) {
                    case "--model" -> model = requireOptionValue(args, ++i, "--model");
                    case "--download-model" -> download = true;
                    case "--output", "-o" -> output = Path.of(requireOptionValue(args, ++i, token));
                    case "--output-dir" -> outputDir = Path.of(requireOptionValue(args, ++i, "--output-dir"));
                    case "--format", "-f" -> explicitFormat = OutputFormat.parse(requireOptionValue(args, ++i, token));
                    case "--quiet", "-q" -> quiet = true;
                    case "--language", "-l" -> {
                        String rawLanguage = requireOptionValue(args, ++i, token);
                        language = "auto".equalsIgnoreCase(rawLanguage) ? null : rawLanguage.trim();
                    }
                    case "--task" -> task = TranscriptionTask.parse(requireOptionValue(args, ++i, "--task"));
                    case "--translate" -> task = TranscriptionTask.TRANSLATE;
                    case "--beam-size" -> beamSize = parsePositiveInt(requireOptionValue(args, ++i, "--beam-size"), "--beam-size");
                    case "--timestamps" -> timestamps = Boolean.TRUE;
                    case "--no-timestamps" -> timestamps = Boolean.FALSE;
                    case "--prompt", "--initial-prompt" -> {
                        if (++i >= args.length) {
                            throw new IllegalArgumentException(token + " requires a value.");
                        }
                        initialPrompt = args[i];
                    }
                    case "--provider" -> provider = WhisperExecutionProvider.from(requireOptionValue(args, ++i, "--provider"));
                    case "--device-id" -> deviceId = parseNonNegativeInt(requireOptionValue(args, ++i, "--device-id"), "--device-id");
                    case "--provider-fallback" -> providerFallback = true;
                    case "--diagnostics" -> diagnostics = true;
                    case "--list-models" -> listModels = true;
                    case "--help", "-h" -> help = true;
                    default -> throw new IllegalArgumentException("Unknown option: " + token);
                }
            }

            if (help || diagnostics || listModels) {
                return new CliOptions(
                        List.copyOf(inputs), model, download, output, outputDir, explicitFormat,
                        quiet, help, diagnostics, listModels, language, task, beamSize, timestamps,
                        initialPrompt, provider, deviceId, providerFallback
                );
            }

            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("Missing input media file.");
            }
            if (output != null && outputDir != null) {
                throw new IllegalArgumentException("Specify either --output or --output-dir, not both.");
            }

            CliOptions parsed = new CliOptions(
                    List.copyOf(inputs), model, download, output, outputDir, explicitFormat,
                    quiet, false, false, false, language, task, beamSize, timestamps,
                    initialPrompt, provider, deviceId, providerFallback
            );
            OutputFormat resolvedFormat = parsed.resolvedFormat();
            if (Boolean.FALSE.equals(timestamps)
                    && (resolvedFormat == OutputFormat.SRT || resolvedFormat == OutputFormat.VTT)) {
                throw new IllegalArgumentException("SRT and VTT formats require timestamps; remove --no-timestamps.");
            }
            return parsed;
        }

        private static String requireOptionValue(String[] args, int index, String optionName) {
            if (index >= args.length) {
                throw new IllegalArgumentException(optionName + " requires a value.");
            }
            String value = args[index];
            if (value == null || value.isBlank() || value.startsWith("--")) {
                throw new IllegalArgumentException(optionName + " requires a value.");
            }
            return value;
        }

        private static int parsePositiveInt(String raw, String optionName) {
            int parsed;
            try {
                parsed = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(optionName + " must be a positive integer.");
            }
            if (parsed < 1) {
                throw new IllegalArgumentException(optionName + " must be >= 1.");
            }
            return parsed;
        }

        private static int parseNonNegativeInt(String raw, String optionName) {
            int parsed;
            try {
                parsed = Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(optionName + " must be a non-negative integer.");
            }
            if (parsed < 0) {
                throw new IllegalArgumentException(optionName + " must be >= 0.");
            }
            return parsed;
        }
    }

    private static final class ConsoleListener implements TranscriptionListener {
        private final boolean quiet;
        private final PrintStream err;
        private int lastPercent = -1;

        private ConsoleListener(boolean quiet, PrintStream err) {
            this.quiet = quiet;
            this.err = err;
        }

        @Override
        public void onStatus(String message) {
            if (!quiet) {
                err.println(message);
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
                err.println("Progress: " + percent + "%");
            }
        }
    }
}
