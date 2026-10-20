package com.jwhisper.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jwhisper.transcribe.TranscriptionResult;
import com.jwhisper.transcribe.TranscriptionSegment;

import java.util.Locale;

public final class TranscriptionExporter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private TranscriptionExporter() {}

    public static String render(TranscriptionResult result, OutputFormat format) {
        return switch (format) {
            case TXT -> result.text() + System.lineSeparator();
            case JSON -> json(result) + System.lineSeparator();
            case SRT -> subtitles(result, false);
            case VTT -> "WEBVTT\n\n" + subtitles(result, true);
        };
    }

    private static String json(TranscriptionResult result) {
        try {
            return JSON.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize transcription result", e);
        }
    }

    private static String subtitles(TranscriptionResult result, boolean vtt) {
        StringBuilder out = new StringBuilder();
        int cue = 1;
        for (TranscriptionSegment segment : result.segments()) {
            if (segment.text().isBlank()) continue;
            if (!vtt) out.append(cue++).append('\n');
            out.append(timestamp(segment.startSeconds(), vtt))
                    .append(" --> ")
                    .append(timestamp(Math.max(segment.startSeconds() + 0.001, segment.endSeconds()), vtt))
                    .append('\n')
                    .append(segment.text())
                    .append("\n\n");
        }
        return out.toString();
    }

    static String timestamp(double seconds, boolean vtt) {
        long millis = Math.max(0L, Math.round(seconds * 1000.0));
        long hours = millis / 3_600_000L;
        millis %= 3_600_000L;
        long minutes = millis / 60_000L;
        millis %= 60_000L;
        long secs = millis / 1000L;
        long ms = millis % 1000L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d%c%03d", hours, minutes, secs, vtt ? '.' : ',', ms);
    }
}
