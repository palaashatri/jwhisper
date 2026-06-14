package com.jwhisper.whisper;

import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.transcribe.TranscriptionJob;
import com.jwhisper.transcribe.TranscriptionListener;

import java.nio.file.Path;

public final class UnavailableWhisperEngine implements WhisperEngineAgent {
    @Override
    public void loadModel(ModelDescriptor descriptor, Path modelRoot) throws WhisperException {
        throw new WhisperException("ONNX Runtime not available. Reinstall jwhisper.");
    }

    @Override
    public String transcribe(TranscriptionJob job, TranscriptionListener listener) throws WhisperException {
        throw new WhisperException("ONNX Runtime not available. Reinstall jwhisper.");
    }

    @Override
    public void cancel() {
    }

    @Override
    public void close() {
    }
}
