package com.jwhisper.platform;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.awt.Desktop;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
import java.awt.desktop.PreferencesEvent;
import java.awt.desktop.PreferencesHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PlatformAgent {
    public static void prepareMacProperties() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "jwhisper");
        System.setProperty("apple.awt.application.name", "jwhisper");
    }

    public void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
            // Swing's fallback LAF is acceptable when the system LAF cannot be loaded.
        }
    }

    public void installMacMenuHandlers() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(new AboutHandler() {
                @Override
                public void handleAbout(AboutEvent event) {
                    JOptionPane.showMessageDialog(
                            null,
                            "jwhisper transcribes audio locally with Whisper ONNX.",
                            "About jwhisper",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(new PreferencesHandler() {
                @Override
                public void handlePreferences(PreferencesEvent event) {
                    JOptionPane.showMessageDialog(
                            null,
                            "No preferences yet.",
                            "jwhisper",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            });
        }
    }

    public Path appDirectory() {
        return Paths.get(System.getProperty("user.home"), ".jwhisper");
    }

    public Path modelDirectory() {
        return appDirectory().resolve("models");
    }

    public void ensureAppDirectories() throws IOException {
        Files.createDirectories(modelDirectory());
    }

    public String ffmpegInstallHint() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return "Install it with: brew install ffmpeg";
        }
        if (os.contains("win")) {
            return "Install a static ffmpeg build and add it to PATH.";
        }
        return "Install ffmpeg with your package manager.";
    }
}
