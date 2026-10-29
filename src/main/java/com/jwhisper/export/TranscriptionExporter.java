package com.jwhisper.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.jwhisper.transcribe.TranscriptionResult;
import com.jwhisper.transcribe.TranscriptionSegment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TranscriptionExporter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private TranscriptionExporter() {}

    public static String render(TranscriptionResult result, OutputFormat format) {
        return render(result, format, true);
    }

    public static String render(TranscriptionResult result, OutputFormat format, boolean includeTimestamps) {
        return switch (format) {
            case TXT -> result.text() + System.lineSeparator();
            case JSON -> json(result, includeTimestamps) + System.lineSeparator();
            case SRT -> subtitles(result, false);
            case VTT -> "WEBVTT\n\n" + subtitles(result, true);
        };
    }

    public static String renderBatch(List<BatchEntry> entries, OutputFormat format, boolean includeTimestamps) {
        if (entries == null || entries.isEmpty()) {
            return "";
        }
        if (entries.size() == 1) {
            return render(entries.get(0).result(), format, includeTimestamps);
        }
        return switch (format) {
            case TXT -> renderBatchTxt(entries);
            case JSON -> renderBatchJson(entries, includeTimestamps) + System.lineSeparator();
            case VTT -> renderBatchVtt(entries);
            case SRT -> renderBatchSrt(entries);
        };
    }

    private static String renderBatchTxt(List<BatchEntry> entries) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            BatchEntry entry = entries.get(i);
            if (i > 0) {
                out.append(System.lineSeparator()).append(System.lineSeparator());
            }
            out.append("=== ").append(entry.fileName()).append(" ===").append(System.lineSeparator());
            out.append(entry.result().text());
        }
        return out.append(System.lineSeparator()).toString();
    }

    private static String renderBatchJson(List<BatchEntry> entries, boolean includeTimestamps) {
        List<Map<String, Object>> payload = new ArrayList<>(entries.size());
        for (BatchEntry entry : entries) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("file", entry.fileName());
            item.put("result", toJsonPayload(entry.result(), includeTimestamps));
            payload.add(item);
        }
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize batch transcription results", e);
        }
    }

    private static String renderBatchVtt(List<BatchEntry> entries) {
        StringBuilder out = new StringBuilder("WEBVTT\n\n");
        double cumulativeOffset = 0.0;
        for (BatchEntry entry : entries) {
            out.append("NOTE ").append(entry.fileName().replace("\n", " ").replace("\r", " ")).append("\n\n");
            double maxEnd = 0.0;
            for (TranscriptionSegment segment : entry.result().segments()) {
                if (segment.text().isBlank()) continue;
                double start = cumulativeOffset + Math.max(0.0, segment.startSeconds());
                double end = cumulativeOffset + Math.max(segment.startSeconds() + 0.001, segment.endSeconds());
                maxEnd = Math.max(maxEnd, segment.endSeconds());
                out.append(timestamp(start, true))
                        .append(" --> ")
                        .append(timestamp(end, true))
                        .append('\n')
                        .append(segment.text())
                        .append("\n\n");
            }
            double audioSec = entry.result().timings() == null ? 0.0 : entry.result().timings().audioSeconds();
            cumulativeOffset += Math.max(audioSec, maxEnd);
        }
        return out.toString();
    }

    private static String renderBatchSrt(List<BatchEntry> entries) {
        StringBuilder out = new StringBuilder();
        int cue = 1;
        double cumulativeOffset = 0.0;
        for (BatchEntry entry : entries) {
            double maxEnd = 0.0;
            for (TranscriptionSegment segment : entry.result().segments()) {
                if (segment.text().isBlank()) continue;
                double start = cumulativeOffset + Math.max(0.0, segment.startSeconds());
                double end = cumulativeOffset + Math.max(segment.startSeconds() + 0.001, segment.endSeconds());
                maxEnd = Math.max(maxEnd, segment.endSeconds());
                out.append(cue++).append('\n')
                        .append(timestamp(start, false))
                        .append(" --> ")
                        .append(timestamp(end, false))
                        .append('\n')
                        .append('[').append(entry.fileName()).append("] ").append(segment.text())
                        .append("\n\n");
            }
            double audioSec = entry.result().timings() == null ? 0.0 : entry.result().timings().audioSeconds();
            cumulativeOffset += Math.max(audioSec, maxEnd);
        }
        return out.toString();
    }

    private static String json(TranscriptionResult result, boolean includeTimestamps) {
        try {
            if (includeTimestamps) {
                return JSON.writeValueAsString(result);
            }
            return JSON.writeValueAsString(toJsonPayload(result, false));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize transcription result", e);
        }
    }

    private static Object toJsonPayload(TranscriptionResult result, boolean includeTimestamps) {
        if (includeTimestamps) {
            return result;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", result.text());
        map.put("language", result.language());
        map.put("task", result.task());
        map.put("segments", List.of());
        map.put("modelId", result.modelId());
        map.put("provider", result.provider());
        map.put("timings", result.timings());
        return map;
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

    public record BatchEntry(String fileName, TranscriptionResult result) {
    }
}
