package com.jwhisper.platform;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class BuildProfile {
    private static final Properties PROPERTIES = load();

    private BuildProfile() {
    }

    public static String version() {
        return PROPERTIES.getProperty("version", "dev");
    }

    public static String runtimeVariant() {
        String property = System.getProperty("jwhisper.runtimeVariant");
        if (property != null && !property.isBlank()) {
            return property.toLowerCase();
        }
        return PROPERTIES.getProperty("runtimeVariant", "cpu").toLowerCase();
    }

    public static String onnxRuntimeArtifact() {
        return PROPERTIES.getProperty("onnxRuntimeArtifact", "onnxruntime");
    }

    private static Properties load() {
        Properties properties = new Properties();
        try (InputStream input = BuildProfile.class.getResourceAsStream("/com/jwhisper/build.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Defaults keep local IDE runs working before resources are processed.
        }
        return properties;
    }
}
