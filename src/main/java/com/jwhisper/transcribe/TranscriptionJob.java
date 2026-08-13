package com.jwhisper.transcribe;

import com.jwhisper.audio.AudioJob;
import com.jwhisper.model.ModelDescriptor;

import java.nio.file.Path;

public record TranscriptionJob(
        ModelDescriptor model,
        Path modelRoot,
        AudioJob audioJob,
        TranscriptionOptions options
) {
    public TranscriptionJob {
        options = options == null ? TranscriptionOptions.defaults() : options;
    }

    public TranscriptionJob(ModelDescriptor model, Path modelRoot, AudioJob audioJob) {
        this(model, modelRoot, audioJob, TranscriptionOptions.defaults());
    }
}
