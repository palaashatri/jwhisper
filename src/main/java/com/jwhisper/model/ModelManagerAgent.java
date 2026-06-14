package com.jwhisper.model;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jwhisper.deps.DependencyAgent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ModelManagerAgent {
    private static final long DOWNLOAD_BUFFER_BYTES = 1024L * 1024L;
    private static final long DOWNLOAD_SAFETY_BYTES = 512L * 1024L * 1024L;

    private final Path modelsDirectory;
    private final DependencyAgent dependencyAgent;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public ModelManagerAgent(Path modelsDirectory, DependencyAgent dependencyAgent) {
        this.modelsDirectory = modelsDirectory;
        this.dependencyAgent = dependencyAgent;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public List<ModelDescriptor> availableModels() {
        return ModelCatalog.availableModels();
    }

    public List<ModelDescriptor> installedModels() {
        ModelStore store = loadStore();
        List<ModelDescriptor> installed = new ArrayList<>();
        for (ModelDescriptor descriptor : ModelCatalog.availableModels()) {
            if (store.installed.stream().anyMatch(model -> descriptor.id().equals(model.id)) && isInstalled(descriptor)) {
                installed.add(descriptor);
            } else if (isInstalled(descriptor)) {
                installed.add(descriptor);
            }
        }
        return installed;
    }

    public Optional<ModelDescriptor> defaultModel() {
        ModelStore store = loadStore();
        if (store.defaultModelId != null) {
            Optional<ModelDescriptor> selected = ModelCatalog.find(store.defaultModelId).filter(this::isInstalled);
            if (selected.isPresent()) {
                return selected;
            }
        }
        return installedModels().stream().findFirst();
    }

    public synchronized Optional<String> defaultModelId() {
        String id = loadStore().defaultModelId;
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
    }

    public synchronized void setDefaultModel(ModelDescriptor descriptor) throws IOException {
        ModelStore store = loadStore();
        if (!isInstalled(descriptor)) {
            throw new IOException("Model is not installed.");
        }
        registerInstalled(store, descriptor);
        store.defaultModelId = descriptor.id();
        saveStore(store);
    }

    public synchronized boolean isInstalled(ModelDescriptor descriptor) {
        Path root = modelRoot(descriptor);
        return descriptor.files().stream().allMatch(file -> Files.isRegularFile(root.resolve(file.relativePath())));
    }

    public Path modelRoot(ModelDescriptor descriptor) {
        return modelsDirectory.resolve(descriptor.id());
    }

    public synchronized void deleteModel(ModelDescriptor descriptor) throws IOException {
        Path root = modelRoot(descriptor);
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path path : paths) {
                    Files.deleteIfExists(path);
                }
            }
        }
        ModelStore store = loadStore();
        store.installed.removeIf(model -> descriptor.id().equals(model.id));
        if (descriptor.id().equals(store.defaultModelId)) {
            store.defaultModelId = null;
        }
        saveStore(store);
    }

    public void downloadModel(ModelDescriptor descriptor, DownloadProgressListener listener)
            throws IOException, InterruptedException {
        Files.createDirectories(modelsDirectory);
        long bytesNeeded = descriptor.estimatedBytes() + DOWNLOAD_SAFETY_BYTES;
        if (!dependencyAgent.hasDiskSpace(bytesNeeded)) {
            throw new IOException("Not enough disk space for this model.");
        }

        Path root = modelRoot(descriptor);
        Files.createDirectories(root);
        long total = Math.max(descriptor.estimatedBytes(), descriptor.files().stream().mapToLong(ModelFile::expectedBytes).sum());
        DownloadCounter counter = new DownloadCounter(total);

        for (ModelFile file : descriptor.files()) {
            downloadFile(descriptor, file, root.resolve(file.relativePath()), counter, listener);
        }

        listener.onProgress(0.96, "Checking model...");
        validateLoadable(descriptor);

        markInstalled(descriptor);
        listener.onProgress(1.0, "Model ready.");
    }

    private synchronized void markInstalled(ModelDescriptor descriptor) throws IOException {
        ModelStore store = loadStore();
        registerInstalled(store, descriptor);
        if (store.defaultModelId == null || store.defaultModelId.isBlank()) {
            store.defaultModelId = descriptor.id();
        }
        saveStore(store);
    }

    private void downloadFile(
            ModelDescriptor descriptor,
            ModelFile file,
            Path target,
            DownloadCounter counter,
            DownloadProgressListener listener
    ) throws IOException, InterruptedException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".download");
        Files.deleteIfExists(temp);

        HttpRequest request = HttpRequest.newBuilder(file.downloadUri(descriptor.repository()))
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed. Try again.");
        }

        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(file.expectedBytes());
        long fileBytes = 0;
        try (InputStream input = response.body();
             OutputStream output = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[(int) DOWNLOAD_BUFFER_BYTES];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                fileBytes += read;
                counter.add(read);
                listener.onProgress(counter.fraction(), "Downloading " + target.getFileName());
            }
        }

        long expected = file.expectedBytes() > 0 ? file.expectedBytes() : contentLength;
        if (expected > 0 && fileBytes < Math.round(expected * 0.95)) {
            Files.deleteIfExists(temp);
            throw new IOException("Download failed. Try again.");
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void validateLoadable(ModelDescriptor descriptor) throws IOException {
        Path root = modelRoot(descriptor);
        try {
            OrtEnvironment environment = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions();
                 OrtSession ignoredEncoder = environment.createSession(root.resolve("onnx/encoder_model.onnx").toString(), options);
                 OrtSession.SessionOptions decoderOptions = new OrtSession.SessionOptions();
                 OrtSession ignoredDecoder = environment.createSession(root.resolve("onnx/decoder_model.onnx").toString(), decoderOptions)) {
                // Opening both sessions is enough to catch corrupt model files.
            }
        } catch (OrtException | RuntimeException e) {
            throw new IOException("Model file is invalid. Delete and re-download.", e);
        }
    }

    private void registerInstalled(ModelStore store, ModelDescriptor descriptor) {
        store.installed.removeIf(model -> descriptor.id().equals(model.id));
        store.installed.add(new InstalledModel(descriptor.id(), Instant.now().toString(), descriptor.repository()));
    }

    private ModelStore loadStore() {
        Path path = storePath();
        if (!Files.isRegularFile(path)) {
            return new ModelStore();
        }
        try {
            ModelStore store = mapper.readValue(path.toFile(), ModelStore.class);
            if (store.installed == null) {
                store.installed = new ArrayList<>();
            }
            return store;
        } catch (IOException e) {
            return new ModelStore();
        }
    }

    private void saveStore(ModelStore store) throws IOException {
        Files.createDirectories(modelsDirectory);
        mapper.writeValue(storePath().toFile(), store);
    }

    private Path storePath() {
        return modelsDirectory.resolve("models.json");
    }

    private static final class DownloadCounter {
        private final long total;
        private long downloaded;

        private DownloadCounter(long total) {
            this.total = Math.max(total, 1L);
        }

        private void add(long bytes) {
            downloaded += bytes;
        }

        private double fraction() {
            return Math.min(0.95, downloaded / (double) total);
        }
    }
}
