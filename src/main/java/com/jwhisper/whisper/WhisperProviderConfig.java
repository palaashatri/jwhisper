package com.jwhisper.whisper;

import ai.onnxruntime.OrtProvider;
import com.jwhisper.platform.BuildProfile;

import java.util.EnumSet;
import java.util.Locale;

public final class WhisperProviderConfig {
    private final WhisperExecutionProvider requestedProvider;
    private final int deviceId;
    private final boolean allowCpuFallback;

    private WhisperProviderConfig(WhisperExecutionProvider requestedProvider, int deviceId, boolean allowCpuFallback) {
        this.requestedProvider = requestedProvider;
        this.deviceId = deviceId;
        this.allowCpuFallback = allowCpuFallback;
    }

    public static WhisperProviderConfig fromEnvironment() {
        String explicitProvider = firstNonBlank(
                System.getProperty("jwhisper.provider"),
                System.getenv("JWHISPER_PROVIDER")
        );
        int deviceId = parseDeviceId(firstNonBlank(
                System.getProperty("jwhisper.deviceId"),
                System.getenv("JWHISPER_DEVICE_ID")
        ));
        if (explicitProvider != null) {
            boolean fallback = Boolean.parseBoolean(firstNonBlank(
                    System.getProperty("jwhisper.providerFallback"),
                    System.getenv("JWHISPER_PROVIDER_FALLBACK"),
                    "false"
            ));
            return new WhisperProviderConfig(WhisperExecutionProvider.from(explicitProvider), deviceId, fallback);
        }

        String variant = BuildProfile.runtimeVariant();
        if ("apple".equals(variant)) {
            return new WhisperProviderConfig(WhisperExecutionProvider.COREML, deviceId, true);
        }
        if ("gpu".equals(variant)) {
            return new WhisperProviderConfig(WhisperExecutionProvider.GPU, deviceId, true);
        }
        return new WhisperProviderConfig(WhisperExecutionProvider.AUTO, deviceId, true);
    }

    public int deviceId() {
        return deviceId;
    }

    public boolean allowCpuFallback() {
        return allowCpuFallback;
    }

    public WhisperExecutionProvider requestedProvider() {
        return requestedProvider;
    }

    public WhisperExecutionProvider resolve(EnumSet<OrtProvider> availableProviders) throws WhisperException {
        return switch (requestedProvider) {
            case AUTO -> resolveAutomatic(availableProviders);
            case GPU -> resolveGpu(availableProviders);
            case CPU -> WhisperExecutionProvider.CPU;
            case COREML, CUDA, ROCM, TENSORRT -> resolveSpecific(requestedProvider, availableProviders);
        };
    }

    public String describe() {
        String fallback = allowCpuFallback ? ", CPU fallback enabled" : "";
        return requestedProvider.displayName() + " device " + deviceId + fallback;
    }

    private WhisperExecutionProvider resolveAutomatic(EnumSet<OrtProvider> availableProviders) {
        if (isAppleSilicon() && availableProviders.contains(OrtProvider.CORE_ML)) {
            return WhisperExecutionProvider.COREML;
        }
        if (availableProviders.contains(OrtProvider.CUDA)) {
            return WhisperExecutionProvider.CUDA;
        }
        if (availableProviders.contains(OrtProvider.ROCM)) {
            return WhisperExecutionProvider.ROCM;
        }
        if (availableProviders.contains(OrtProvider.TENSOR_RT)) {
            return WhisperExecutionProvider.TENSORRT;
        }
        return WhisperExecutionProvider.CPU;
    }

    private WhisperExecutionProvider resolveGpu(EnumSet<OrtProvider> availableProviders) throws WhisperException {
        if (availableProviders.contains(OrtProvider.CUDA)) {
            return WhisperExecutionProvider.CUDA;
        }
        if (availableProviders.contains(OrtProvider.ROCM)) {
            return WhisperExecutionProvider.ROCM;
        }
        if (availableProviders.contains(OrtProvider.TENSOR_RT)) {
            return WhisperExecutionProvider.TENSORRT;
        }
        if (allowCpuFallback) {
            return WhisperExecutionProvider.CPU;
        }
        throw new WhisperException("GPU provider is not available. Install the GPU build and required drivers.");
    }

    private WhisperExecutionProvider resolveSpecific(
            WhisperExecutionProvider provider,
            EnumSet<OrtProvider> availableProviders
    ) throws WhisperException {
        if (availableProviders.contains(provider.ortProvider())) {
            return provider;
        }
        if (allowCpuFallback) {
            return WhisperExecutionProvider.CPU;
        }
        throw new WhisperException(provider.displayName() + " provider is not available.");
    }

    private static boolean isAppleSilicon() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"));
    }

    private static int parseDeviceId(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
