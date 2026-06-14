package com.jwhisper.whisper;

import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.transcribe.TranscriptionJob;
import com.jwhisper.transcribe.TranscriptionListener;

import java.nio.file.Path;

public interface WhisperEngineAgent extends AutoCloseable {
    void loadModel(ModelDescriptor descriptor, Path modelRoot) throws WhisperException;

    String transcribe(TranscriptionJob job, TranscriptionListener listener) throws WhisperException;

    void cancel();

    @Override
    void close();
}
