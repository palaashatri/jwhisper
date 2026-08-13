package com.jwhisper.eval;

import java.text.Normalizer;
import java.util.Locale;

public final class WordErrorRate {
    private WordErrorRate() {}

    public static Metrics compare(String reference, String hypothesis) {
        String[] ref = words(reference);
        String[] hyp = words(hypothesis);
        int[][] d = new int[ref.length + 1][hyp.length + 1];
        for (int i = 0; i <= ref.length; i++) d[i][0] = i;
        for (int j = 0; j <= hyp.length; j++) d[0][j] = j;
        for (int i = 1; i <= ref.length; i++) {
            for (int j = 1; j <= hyp.length; j++) {
                int substitution = d[i - 1][j - 1] + (ref[i - 1].equals(hyp[j - 1]) ? 0 : 1);
                d[i][j] = Math.min(substitution, Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1));
            }
        }
        int edits = d[ref.length][hyp.length];
        double wer = ref.length == 0 ? (hyp.length == 0 ? 0.0 : 1.0) : edits / (double) ref.length;
        return new Metrics(ref.length, hyp.length, edits, wer);
    }

    private static String[] words(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}']+", " ")
                .trim();
        return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
    }

    public record Metrics(int referenceWords, int hypothesisWords, int edits, double wer) {}
}
