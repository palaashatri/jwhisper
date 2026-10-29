package com.jwhisper.platform;

import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Desktop;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;
import java.awt.desktop.PreferencesEvent;
import java.awt.desktop.PreferencesHandler;
import java.util.function.BooleanSupplier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class PlatformAgent {
    public static void prepareMacProperties() {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "JWhisper");
        System.setProperty("apple.awt.application.name", "JWhisper");
        System.setProperty("apple.awt.application.appearance", "system");
    }

    public void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
            // Swing's fallback LAF is acceptable when the system LAF cannot be loaded.
        }
    }

    public void installMacMenuHandlers() {
        installMacMenuHandlers(null, null, null);
    }

    public void installMacMenuHandlers(Icon appIcon, Runnable preferencesAction) {
        installMacMenuHandlers(appIcon, preferencesAction, null);
    }

    public void installMacMenuHandlers(Icon appIcon, Runnable preferencesAction, BooleanSupplier allowQuitSupplier) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(new AboutHandler() {
                @Override
                public void handleAbout(AboutEvent event) {
                    runOnEdt(() -> {
                        String aboutMessage = "JWhisper " + BuildProfile.version() + "\n"
                                + "Local-first neural speech transcription powered by Whisper ONNX.\n"
                                + "Runtime profile: " + BuildProfile.runtimeVariant().toUpperCase(Locale.ROOT);
                        JOptionPane.showMessageDialog(
                                null,
                                aboutMessage,
                                "About JWhisper",
                                JOptionPane.INFORMATION_MESSAGE,
                                appIcon
                        );
                    });
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(new PreferencesHandler() {
                @Override
                public void handlePreferences(PreferencesEvent event) {
                    runOnEdt(() -> {
                        if (preferencesAction != null) {
                            preferencesAction.run();
                        } else {
                            JOptionPane.showMessageDialog(
                                    null,
                                    "Manage Whisper models from the main JWhisper window.",
                                    "JWhisper",
                                    JOptionPane.INFORMATION_MESSAGE,
                                    appIcon
                            );
                        }
                    });
                }
            });
        }
        if (allowQuitSupplier != null && desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((event, response) -> runOnEdt(() -> {
                if (allowQuitSupplier.getAsBoolean()) {
                    response.performQuit();
                } else {
                    response.cancelQuit();
                }
            }));
        }
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
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
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return "Install it with: brew install ffmpeg";
        }
        if (os.contains("win")) {
            return "Install a static ffmpeg build and add it to PATH.";
        }
        return "Install ffmpeg with your package manager.";
    }
}
