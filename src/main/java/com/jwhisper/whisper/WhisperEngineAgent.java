package com.jwhisper.whisper;

import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.transcribe.TranscriptionJob;
import com.jwhisper.transcribe.TranscriptionListener;
import com.jwhisper.transcribe.TranscriptionResult;

import java.nio.file.Path;

public interface WhisperEngineAgent extends AutoCloseable {
    void loadModel(ModelDescriptor descriptor, Path modelRoot) throws WhisperException;

    TranscriptionResult transcribeResult(TranscriptionJob job, TranscriptionListener listener) throws WhisperException;

    default String transcribe(TranscriptionJob job, TranscriptionListener listener) throws WhisperException {
        return transcribeResult(job, listener).text();
    }

    void cancel();

    @Override
    void close();
}
