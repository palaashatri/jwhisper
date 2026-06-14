package com.jwhisper.whisper;

import java.util.Arrays;

public final class WhisperFeatureExtractor {
    private static final double MEL_MIN_HZ = 0.0;
    private static final double MEL_MAX_HZ = 8000.0;

    private final WhisperPreprocessorConfig config;
    private final double[] hannWindow;
    private final double[][] cosTable;
    private final double[][] sinTable;
    private final float[][] melFilters;

    public WhisperFeatureExtractor(WhisperPreprocessorConfig config) {
        this.config = config;
        this.hannWindow = hannWindow(config.nFft());
        int bins = config.nFft() / 2 + 1;
        this.cosTable = new double[bins][config.nFft()];
        this.sinTable = new double[bins][config.nFft()];
        for (int k = 0; k < bins; k++) {
            for (int n = 0; n < config.nFft(); n++) {
                double angle = 2.0 * Math.PI * k * n / config.nFft();
                cosTable[k][n] = Math.cos(angle);
                sinTable[k][n] = Math.sin(angle);
            }
        }
        this.melFilters = melFilters(config.featureSize(), config.nFft(), config.samplingRate());
    }

    public int maxSamples() {
        return config.nSamples();
    }

    public float[][][] extract(float[] samples) {
        float[] chunk = new float[config.nSamples()];
        System.arraycopy(samples, 0, chunk, 0, Math.min(samples.length, chunk.length));

        int bins = config.nFft() / 2 + 1;
        float[][] logMels = new float[config.featureSize()][config.nbMaxFrames()];
        double[] power = new double[bins];
        double maxLog = -Double.MAX_VALUE;

        for (int frame = 0; frame < config.nbMaxFrames(); frame++) {
            Arrays.fill(power, 0.0);
            int frameStart = frame * config.hopLength() - config.nFft() / 2;
            for (int k = 0; k < bins; k++) {
                double real = 0.0;
                double imaginary = 0.0;
                for (int n = 0; n < config.nFft(); n++) {
                    int sampleIndex = frameStart + n;
                    double sample = sampleIndex >= 0 && sampleIndex < chunk.length ? chunk[sampleIndex] : 0.0;
                    sample *= hannWindow[n];
                    real += sample * cosTable[k][n];
                    imaginary -= sample * sinTable[k][n];
                }
                power[k] = real * real + imaginary * imaginary;
            }

            for (int mel = 0; mel < config.featureSize(); mel++) {
                double value = 0.0;
                for (int bin = 0; bin < bins; bin++) {
                    value += melFilters[mel][bin] * power[bin];
                }
                double logValue = Math.log10(Math.max(value, 1.0e-10));
                logMels[mel][frame] = (float) logValue;
                if (logValue > maxLog) {
                    maxLog = logValue;
                }
            }
        }

        float floor = (float) (maxLog - 8.0);
        for (int mel = 0; mel < config.featureSize(); mel++) {
            for (int frame = 0; frame < config.nbMaxFrames(); frame++) {
                float clamped = Math.max(logMels[mel][frame], floor);
                logMels[mel][frame] = (clamped + 4.0f) / 4.0f;
            }
        }

        return new float[][][]{logMels};
    }

    private static double[] hannWindow(int size) {
        double[] window = new double[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / size);
        }
        return window;
    }

    private static float[][] melFilters(int nMels, int nFft, int sampleRate) {
        int bins = nFft / 2 + 1;
        double[] fftFreqs = new double[bins];
        for (int i = 0; i < bins; i++) {
            fftFreqs[i] = i * sampleRate / (double) nFft;
        }

        double minMel = hzToMel(MEL_MIN_HZ);
        double maxMel = hzToMel(MEL_MAX_HZ);
        double[] melPoints = new double[nMels + 2];
        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = minMel + (maxMel - minMel) * i / (nMels + 1);
        }
        double[] melFrequencies = new double[melPoints.length];
        for (int i = 0; i < melPoints.length; i++) {
            melFrequencies[i] = melToHz(melPoints[i]);
        }

        float[][] filters = new float[nMels][bins];
        for (int mel = 0; mel < nMels; mel++) {
            double lower = melFrequencies[mel];
            double center = melFrequencies[mel + 1];
            double upper = melFrequencies[mel + 2];
            double enorm = 2.0 / (upper - lower);
            for (int bin = 0; bin < bins; bin++) {
                double freq = fftFreqs[bin];
                double left = (freq - lower) / (center - lower);
                double right = (upper - freq) / (upper - center);
                filters[mel][bin] = (float) (Math.max(0.0, Math.min(left, right)) * enorm);
            }
        }
        return filters;
    }

    private static double hzToMel(double hz) {
        double fSp = 200.0 / 3.0;
        double minLogHz = 1000.0;
        double minLogMel = minLogHz / fSp;
        double logStep = Math.log(6.4) / 27.0;
        if (hz < minLogHz) {
            return hz / fSp;
        }
        return minLogMel + Math.log(hz / minLogHz) / logStep;
    }

    private static double melToHz(double mel) {
        double fSp = 200.0 / 3.0;
        double minLogHz = 1000.0;
        double minLogMel = minLogHz / fSp;
        double logStep = Math.log(6.4) / 27.0;
        if (mel < minLogMel) {
            return mel * fSp;
        }
        return minLogHz * Math.exp(logStep * (mel - minLogMel));
    }
}
