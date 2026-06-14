package com.jwhisper.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModelDownloadService implements AutoCloseable {
    private final ModelManagerAgent modelManagerAgent;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, ModelDownloadState> states = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ModelDownloadListener> listeners = new CopyOnWriteArrayList<>();

    public ModelDownloadService(ModelManagerAgent modelManagerAgent) {
        this.modelManagerAgent = modelManagerAgent;
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jwhisper-model-downloads");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void addListener(ModelDownloadListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ModelDownloadListener listener) {
        listeners.remove(listener);
    }

    public ModelDownloadState startDownload(ModelDescriptor descriptor) {
        if (modelManagerAgent.isInstalled(descriptor)) {
            ModelDownloadState ready = new ModelDownloadState(descriptor, ModelDownloadStatus.SUCCEEDED, 1.0, "Model ready.", null);
            update(ready);
            return ready;
        }

        ModelDownloadState existing = states.get(descriptor.id());
        if (existing != null && existing.isActive()) {
            return existing;
        }

        ModelDownloadState pending = new ModelDownloadState(descriptor, ModelDownloadStatus.PENDING, 0.0, "Waiting to download " + descriptor.displayName() + "...", null);
        update(pending);
        executorService.submit(() -> runDownload(descriptor));
        return pending;
    }

    public Optional<ModelDownloadState> stateFor(ModelDescriptor descriptor) {
        return Optional.ofNullable(states.get(descriptor.id()));
    }

    public List<ModelDownloadState> states() {
        return states.values().stream()
                .sorted(Comparator.comparing(state -> state.descriptor().displayName()))
                .toList();
    }

    public List<ModelDownloadState> activeDownloads() {
        return states.values().stream()
                .filter(ModelDownloadState::isActive)
                .sorted(Comparator
                        .comparingInt((ModelDownloadState state) -> state.status() == ModelDownloadStatus.RUNNING ? 0 : 1)
                        .thenComparing(state -> state.descriptor().displayName()))
                .toList();
    }

    public boolean isDownloading(ModelDescriptor descriptor) {
        return stateFor(descriptor).map(ModelDownloadState::isActive).orElse(false);
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }

    private void runDownload(ModelDescriptor descriptor) {
        try {
            update(new ModelDownloadState(descriptor, ModelDownloadStatus.RUNNING, 0.0, "Downloading " + descriptor.displayName() + "...", null));
            modelManagerAgent.downloadModel(descriptor, (fraction, status) -> update(new ModelDownloadState(
                    descriptor,
                    ModelDownloadStatus.RUNNING,
                    fraction,
                    descriptor.displayName() + ": " + status,
                    null
            )));
            update(new ModelDownloadState(descriptor, ModelDownloadStatus.SUCCEEDED, 1.0, descriptor.displayName() + " ready.", null));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            update(new ModelDownloadState(descriptor, ModelDownloadStatus.FAILED, 0.0, "Download failed. Try again.", "Download interrupted."));
        } catch (Exception e) {
            update(new ModelDownloadState(descriptor, ModelDownloadStatus.FAILED, 0.0, "Download failed. Try again.", userMessage(e)));
        }
    }

    private void update(ModelDownloadState state) {
        states.put(state.descriptor().id(), state);
        for (ModelDownloadListener listener : listeners) {
            listener.onModelDownloadChanged(state);
        }
    }

    private String userMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return "Download failed. Try again.";
        }
        if (message.contains("disk space")) {
            return "Not enough disk space for this model.";
        }
        if (message.contains("invalid")) {
            return "Model file is invalid. Delete and re-download.";
        }
        return "Download failed. Try again.";
    }
}
