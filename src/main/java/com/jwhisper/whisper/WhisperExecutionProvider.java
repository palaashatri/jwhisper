package com.jwhisper.whisper;

import ai.onnxruntime.OrtProvider;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.providers.CoreMLFlags;

import java.util.EnumSet;
import java.util.Locale;

public enum WhisperExecutionProvider {
    AUTO("Automatic"),
    GPU("GPU"),
    CPU("CPU"),
    COREML("CoreML"),
    CUDA("CUDA"),
    ROCM("ROCm"),
    TENSORRT("TensorRT");

    private final String displayName;

    WhisperExecutionProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public void applyTo(OrtSession.SessionOptions options, int deviceId) throws ai.onnxruntime.OrtException {
        switch (this) {
            case CPU -> options.addCPU(true);
            case COREML -> options.addCoreML(EnumSet.of(CoreMLFlags.ENABLE_ON_SUBGRAPH));
            case CUDA -> options.addCUDA(deviceId);
            case ROCM -> options.addROCM(deviceId);
            case TENSORRT -> options.addTensorrt(deviceId);
            case AUTO, GPU -> options.addCPU(true);
        }
    }

    public OrtProvider ortProvider() {
        return switch (this) {
            case CPU -> OrtProvider.CPU;
            case COREML -> OrtProvider.CORE_ML;
            case CUDA -> OrtProvider.CUDA;
            case ROCM -> OrtProvider.ROCM;
            case TENSORRT -> OrtProvider.TENSOR_RT;
            case AUTO, GPU -> OrtProvider.CPU;
        };
    }

    public static WhisperExecutionProvider from(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "")) {
            case "cpu" -> CPU;
            case "apple", "applesilicon", "coreml", "ane", "metal" -> COREML;
            case "cuda", "nvidia" -> CUDA;
            case "rocm", "amd" -> ROCM;
            case "tensorrt", "trt" -> TENSORRT;
            case "gpu" -> GPU;
            case "auto" -> AUTO;
            default -> throw new IllegalArgumentException("Unknown provider: " + value);
        };
    }
}
