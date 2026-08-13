package com.jwhisper.export;

import com.jwhisper.transcribe.TranscriptionResult;
import com.jwhisper.transcribe.TranscriptionSegment;
import com.jwhisper.transcribe.TranscriptionTimings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptionExporterTest {
    private static TranscriptionResult result() {
        return new TranscriptionResult(
                "Hello world.", "en", "transcribe",
                List.of(new TranscriptionSegment(0, 1.25, 3.5, "Hello world.", -0.2, 0.01)),
                "tiny.en", "CPU",
                new TranscriptionTimings(3.5, 1000, null, null, null, null, null)
        );
    }

    @Test void rendersSrt() {
        String text = TranscriptionExporter.render(result(), OutputFormat.SRT);
        assertTrue(text.contains("00:00:01,250 --> 00:00:03,500"));
        assertTrue(text.contains("Hello world."));
    }

    @Test void rendersVtt() {
        String text = TranscriptionExporter.render(result(), OutputFormat.VTT);
        assertTrue(text.startsWith("WEBVTT"));
        assertTrue(text.contains("00:00:01.250 --> 00:00:03.500"));
    }

    @Test void rendersJsonWithMetadata() {
        String text = TranscriptionExporter.render(result(), OutputFormat.JSON);
        assertTrue(text.contains("\"modelId\" : \"tiny.en\""));
        assertTrue(text.contains("\"language\" : \"en\""));
    }
}
