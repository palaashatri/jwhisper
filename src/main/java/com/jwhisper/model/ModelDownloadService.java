package com.jwhisper.model;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ModelDownloadService implements AutoCloseable {
    private final ModelManagerAgent modelManagerAgent;
    private final ExecutorService executorService;
    private final ConcurrentHashMap<String, ModelDownloadState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Future<?>> futures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
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

        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        AtomicReference<Future<?>> attemptFutureRef = new AtomicReference<>();
        cancelFlags.put(descriptor.id(), cancelFlag);
        ModelDownloadState pending = new ModelDownloadState(
                descriptor,
                ModelDownloadStatus.PENDING,
                0.0,
                "Waiting to download " + descriptor.displayName() + "...",
                null
        );
        update(pending);
        Future<?> future = executorService.submit(() -> runDownload(descriptor, cancelFlag, attemptFutureRef));
        attemptFutureRef.set(future);
        if (cancelFlags.get(descriptor.id()) == cancelFlag && !cancelFlag.get()) {
            futures.put(descriptor.id(), future);
        } else if (cancelFlag.get()) {
            future.cancel(true);
        }
        return pending;
    }

    public void cancelDownload(ModelDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        String id = descriptor.id();
        AtomicBoolean flag = cancelFlags.get(id);
        if (flag != null) {
            flag.set(true);
        }
        Future<?> future = futures.remove(id);
        if (future != null) {
            future.cancel(true);
        }
        AtomicReference<ModelDownloadState> canceledPublished = new AtomicReference<>();
        states.computeIfPresent(id, (key, current) -> {
            if (!current.isActive()) {
                return current;
            }
            ModelDownloadState canceled = new ModelDownloadState(
                    descriptor,
                    ModelDownloadStatus.CANCELED,
                    current.fraction(),
                    descriptor.displayName() + " download canceled (partial progress saved).",
                    null
            );
            canceledPublished.set(canceled);
            return canceled;
        });
        if (canceledPublished.get() != null) {
            notifyListeners(canceledPublished.get());
        }
    }

    public void cancelAllActive() {
        for (ModelDownloadState active : activeDownloads()) {
            cancelDownload(active.descriptor());
        }
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
        cancelAllActive();
        executorService.shutdownNow();
    }

    private void runDownload(
            ModelDescriptor descriptor,
            AtomicBoolean cancelFlag,
            AtomicReference<Future<?>> attemptFutureRef
    ) {
        String id = descriptor.id();
        try {
            if (!updateIfCurrent(
                    cancelFlag,
                    new ModelDownloadState(descriptor, ModelDownloadStatus.RUNNING, 0.0, "Downloading " + descriptor.displayName() + "...", null)
            )) {
                return;
            }
            modelManagerAgent.downloadModel(descriptor, (fraction, status) -> {
                if (!cancelFlag.get()) {
                    updateIfCurrent(
                            cancelFlag,
                            new ModelDownloadState(
                                    descriptor,
                                    ModelDownloadStatus.RUNNING,
                                    fraction,
                                    descriptor.displayName() + ": " + status,
                                    null
                            )
                    );
                }
            }, cancelFlag::get);
            if (!cancelFlag.get()) {
                updateIfCurrent(
                        cancelFlag,
                        new ModelDownloadState(descriptor, ModelDownloadStatus.SUCCEEDED, 1.0, descriptor.displayName() + " ready.", null)
                );
            }
        } catch (InterruptedException e) {
            // Clear interrupt status so the shared single-thread download executor remains usable.
            Thread.interrupted();
            if (cancelFlags.get(id) == cancelFlag) {
                ModelDownloadState prev = states.get(id);
                double prevFraction = prev == null ? 0.0 : prev.fraction();
                if (cancelFlag.get() || (prev != null && prev.status() == ModelDownloadStatus.CANCELED)) {
                    updateIfCurrent(
                            cancelFlag,
                            new ModelDownloadState(
                                    descriptor,
                                    ModelDownloadStatus.CANCELED,
                                    prevFraction,
                                    descriptor.displayName() + " download canceled (partial progress saved).",
                                    null
                            )
                    );
                } else {
                    updateIfCurrent(
                            cancelFlag,
                            new ModelDownloadState(descriptor, ModelDownloadStatus.FAILED, prevFraction, "Download interrupted.", "Download interrupted.")
                    );
                }
            }
        } catch (Exception e) {
            Thread.interrupted();
            if (cancelFlags.get(id) == cancelFlag) {
                ModelDownloadState prev = states.get(id);
                double prevFraction = prev == null ? 0.0 : prev.fraction();
                if (cancelFlag.get() || (prev != null && prev.status() == ModelDownloadStatus.CANCELED)) {
                    updateIfCurrent(
                            cancelFlag,
                            new ModelDownloadState(
                                    descriptor,
                                    ModelDownloadStatus.CANCELED,
                                    prevFraction,
                                    descriptor.displayName() + " download canceled (partial progress saved).",
                                    null
                            )
                    );
                } else {
                    updateIfCurrent(
                            cancelFlag,
                            new ModelDownloadState(descriptor, ModelDownloadStatus.FAILED, prevFraction, "Download failed. Try again.", userMessage(e))
                    );
                }
            }
        } finally {
            Future<?> attemptFuture = attemptFutureRef.get();
            if (attemptFuture != null) {
                futures.remove(id, attemptFuture);
            } else if (cancelFlags.get(id) == cancelFlag) {
                futures.remove(id);
            }
            cancelFlags.remove(id, cancelFlag);
        }
    }

    private boolean updateIfCurrent(AtomicBoolean attemptFlag, ModelDownloadState nextState) {
        String id = nextState.descriptor().id();
        AtomicBoolean published = new AtomicBoolean(false);
        states.compute(id, (key, current) -> {
            if (cancelFlags.get(id) != attemptFlag) {
                return current;
            }
            if (nextState.status() != ModelDownloadStatus.CANCELED) {
                if (attemptFlag.get() || (current != null && current.status() == ModelDownloadStatus.CANCELED)) {
                    return current;
                }
            } else if (current != null && current.status() == ModelDownloadStatus.CANCELED) {
                return current;
            }
            published.set(true);
            return nextState;
        });
        if (published.get()) {
            notifyListeners(nextState);
            return true;
        }
        return false;
    }

    private void update(ModelDownloadState state) {
        states.put(state.descriptor().id(), state);
        notifyListeners(state);
    }

    private void notifyListeners(ModelDownloadState state) {
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
        if (message.contains("integrity")) {
            return "Downloaded model failed SHA-256 verification. Try again.";
        }
        return "Download failed. Try again.";
    }
}
