package com.jwhisper.audio;

import java.nio.file.Path;

public record AudioJob(Path file, String note) {
}
