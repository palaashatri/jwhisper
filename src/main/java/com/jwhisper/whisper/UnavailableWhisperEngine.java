package com.jwhisper.whisper;

import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.transcribe.TranscriptionJob;
import com.jwhisper.transcribe.TranscriptionListener;
import com.jwhisper.transcribe.TranscriptionResult;

import java.nio.file.Path;

public final class UnavailableWhisperEngine implements WhisperEngineAgent {
    @Override
    public void loadModel(ModelDescriptor descriptor, Path modelRoot) throws WhisperException {
        throw unavailable();
    }

    @Override
    public TranscriptionResult transcribeResult(TranscriptionJob job, TranscriptionListener listener)
            throws WhisperException {
        throw unavailable();
    }

    private WhisperException unavailable() {
        return new WhisperException("ONNX Runtime not available. Reinstall jwhisper.");
    }

    @Override public void cancel() {}
    @Override public void close() {}
}
