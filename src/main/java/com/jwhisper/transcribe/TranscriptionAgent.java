package com.jwhisper.transcribe;

import com.jwhisper.audio.AudioJob;
import com.jwhisper.deps.DependencyReport;
import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.model.ModelManagerAgent;
import com.jwhisper.whisper.WhisperEngineAgent;
import com.jwhisper.whisper.WhisperException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

public final class TranscriptionAgent implements AutoCloseable {
    private final ModelManagerAgent modelManagerAgent;
    private final DependencyReport dependencyReport;
    private final WhisperEngineAgent whisperEngineAgent;
    private final ExecutorService executorService;
    private final AtomicReference<Future<?>> currentTask = new AtomicReference<>();

    public TranscriptionAgent(
            ModelManagerAgent modelManagerAgent,
            DependencyReport dependencyReport,
            WhisperEngineAgent whisperEngineAgent
    ) {
        this.modelManagerAgent = modelManagerAgent;
        this.dependencyReport = dependencyReport;
        this.whisperEngineAgent = whisperEngineAgent;
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jwhisper-transcription");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<String> transcribe(
            AudioJob audioJob,
            ModelDescriptor model,
            TranscriptionListener listener
    ) {
        Objects.requireNonNull(listener, "listener");
        CompletableFuture<String> future = new CompletableFuture<>();
        AtomicReference<FutureTask<?>> taskRef = new AtomicReference<>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                if (!dependencyReport.canTranscribe()) {
                    throw new WhisperException(dependencyReport.firstUserMessage());
                }
                if (model == null) {
                    throw new WhisperException("No models installed.");
                }
                if (!modelManagerAgent.isInstalled(model)) {
                    throw new WhisperException("No models installed.");
                }
                TranscriptionJob job = new TranscriptionJob(model, modelManagerAgent.modelRoot(model), audioJob);
                whisperEngineAgent.loadModel(model, job.modelRoot());
                future.complete(whisperEngineAgent.transcribe(job, listener));
            } catch (Throwable e) {
                future.completeExceptionally(e);
            } finally {
                currentTask.compareAndSet(taskRef.get(), null);
            }
            return null;
        });
        taskRef.set(task);
        Future<?> previous = currentTask.getAndSet(task);
        if (previous != null && !previous.isDone()) {
            whisperEngineAgent.cancel();
            previous.cancel(true);
        }
        executorService.execute(task);
        return future;
    }

    public void cancel() {
        whisperEngineAgent.cancel();
        Future<?> task = currentTask.getAndSet(null);
        if (task != null) {
            task.cancel(true);
        }
    }

    @Override
    public void close() {
        cancel();
        whisperEngineAgent.close();
        executorService.shutdownNow();
    }
}
