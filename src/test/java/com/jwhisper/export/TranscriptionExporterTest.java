package com.jwhisper.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwhisper.transcribe.TranscriptionResult;
import com.jwhisper.transcribe.TranscriptionSegment;
import com.jwhisper.transcribe.TranscriptionTimings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptionExporterTest {
    private static TranscriptionResult result(String text) {
        return new TranscriptionResult(
                text, "en", "transcribe",
                List.of(new TranscriptionSegment(0, 1.25, 3.5, text, -0.2, 0.01)),
                "tiny.en", "CPU",
                new TranscriptionTimings(3.5, 1000, null, null, null, null, null)
        );
    }

    @Test
    void rendersSrt() {
        String text = TranscriptionExporter.render(result("Hello world."), OutputFormat.SRT);
        assertTrue(text.contains("00:00:01,250 --> 00:00:03,500"));
        assertTrue(text.contains("Hello world."));
    }

    @Test
    void rendersVtt() {
        String text = TranscriptionExporter.render(result("Hello world."), OutputFormat.VTT);
        assertTrue(text.startsWith("WEBVTT"));
        assertTrue(text.contains("00:00:01.250 --> 00:00:03.500"));
    }

    @Test
    void rendersJsonWithMetadataAndHonorsTimestampOmission() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String withTimestamps = TranscriptionExporter.render(result("Hello world."), OutputFormat.JSON, true);
        JsonNode parsedWith = mapper.readTree(withTimestamps);
        assertEquals("tiny.en", parsedWith.path("modelId").asText());
        assertEquals("en", parsedWith.path("language").asText());
        assertEquals(1, parsedWith.path("segments").size());

        String withoutTimestamps = TranscriptionExporter.render(result("Hello world."), OutputFormat.JSON, false);
        JsonNode parsedWithout = mapper.readTree(withoutTimestamps);
        assertEquals("Hello world.", parsedWithout.path("text").asText());
        assertEquals(0, parsedWithout.path("segments").size());
    }

    @Test
    void rendersValidBatchOutputsAcrossAllFormats() throws Exception {
        List<TranscriptionExporter.BatchEntry> batch = List.of(
                new TranscriptionExporter.BatchEntry("one.wav", result("First clip.")),
                new TranscriptionExporter.BatchEntry("two.wav", result("Second clip."))
        );

        String batchJson = TranscriptionExporter.renderBatch(batch, OutputFormat.JSON, true);
        JsonNode array = new ObjectMapper().readTree(batchJson);
        assertTrue(array.isArray());
        assertEquals(2, array.size());
        assertEquals("one.wav", array.get(0).path("file").asText());
        assertEquals("Second clip.", array.get(1).path("result").path("text").asText());

        String batchVtt = TranscriptionExporter.renderBatch(batch, OutputFormat.VTT, true);
        assertTrue(batchVtt.startsWith("WEBVTT"));
        assertEquals(-1, batchVtt.indexOf("WEBVTT", 6), "Batch VTT must only emit a single WEBVTT header");
        assertTrue(batchVtt.contains("NOTE one.wav"));
        assertTrue(batchVtt.contains("NOTE two.wav"));
        assertTrue(batchVtt.contains("00:00:01.250 --> 00:00:03.500"));
        assertTrue(batchVtt.contains("00:00:04.750 --> 00:00:07.000"),
                "Second file cues in combined VTT must be monotonically offset by file 1 duration (3.5s)");

        String batchSrt = TranscriptionExporter.renderBatch(batch, OutputFormat.SRT, true);
        assertFalse(batchSrt.contains("==="), "Batch SRT must not contain invalid banner headers");
        assertTrue(batchSrt.contains("1\n00:00:01,250 --> 00:00:03,500"));
        assertTrue(batchSrt.contains("2\n00:00:04,750 --> 00:00:07,000"),
                "Second file cues in combined SRT must be monotonically offset by file 1 duration (3.5s)");
    }
}
