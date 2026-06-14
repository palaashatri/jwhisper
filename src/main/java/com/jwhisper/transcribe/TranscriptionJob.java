package com.jwhisper.transcribe;

import com.jwhisper.audio.AudioJob;
import com.jwhisper.model.ModelDescriptor;

import java.nio.file.Path;

public record TranscriptionJob(ModelDescriptor model, Path modelRoot, AudioJob audioJob) {
}
