package com.jwhisper.ui;

import com.jwhisper.audio.AudioInputAgent;
import com.jwhisper.deps.DependencyAgent;
import com.jwhisper.deps.DependencyReport;
import com.jwhisper.model.ModelDownloadService;
import com.jwhisper.model.ModelManagerAgent;
import com.jwhisper.platform.PlatformAgent;
import com.jwhisper.transcribe.TranscriptionAgent;
import com.jwhisper.whisper.UnavailableWhisperEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppBrandingAndUiTest {
    @TempDir
    Path tempDir;

    @Test
    void bundlesCompleteMultiResolutionBrandIconSetInClasspath() {
        List<Image> icons = AppIcons.windowIcons();
        assertEquals(8, icons.size(), "All 8 multi-resolution brand icons (16..512px) must be bundled in classpath");

        Set<Integer> widths = icons.stream()
                .map(img -> img.getWidth(null))
                .collect(Collectors.toSet());
        assertEquals(Set.of(16, 24, 32, 48, 64, 128, 256, 512), widths);

        BufferedImage icon256 = AppIcons.iconImage(256);
        assertNotNull(icon256);
        assertEquals(256, icon256.getWidth());
        assertTrue(icon256.getColorModel().hasAlpha(), "Bundled brand icon must preserve transparency");

        ImageIcon hiDpi40 = AppIcons.hiDpiIcon(40);
        assertNotNull(hiDpi40);
        assertEquals(40, hiDpi40.getIconWidth());
        assertEquals(40, hiDpi40.getIconHeight());
    }

    @Test
    void brandThemesAlignWithLogoPaletteAndMaintainAccessibleContrast() {
        UiTheme dark = UiTheme.darkTheme();
        assertTrue(dark.dark());
        // Exact brand tokens from assets/brand/palette.json (#A16CF6 accent, #F4F7FC foreground)
        assertEquals(new Color(0xA1, 0x6C, 0xF6), dark.accent());
        assertEquals(new Color(0xF4, 0xF7, 0xFC), dark.text());
        assertTrue(contrastRatio(dark.text(), dark.surface()) > 10.0);

        UiTheme light = UiTheme.lightTheme();
        assertFalse(light.dark());
        assertTrue(light.accent().getBlue() > light.accent().getRed());
        assertTrue(light.accent().getRed() > light.accent().getGreen());
        assertTrue(contrastRatio(light.text(), light.surface()) > 10.0);
    }

    @Test
    void attachesBrandIconsToDesktopWindowsWhenDisplayAvailable() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        SwingUtilities.invokeAndWait(() -> {
            PlatformAgent platform = new PlatformAgent();
            DependencyReport report = new DependencyReport();
            ModelManagerAgent manager = new ModelManagerAgent(tempDir, new DependencyAgent(platform));
            ModelDownloadService downloads = new ModelDownloadService(manager);
            TranscriptionAgent agent = new TranscriptionAgent(manager, report, new UnavailableWhisperEngine());

            MainWindow window = new MainWindow(manager, downloads, new AudioInputAgent(), agent, report);
            try {
                assertEquals(8, window.getIconImages().size(), "MainWindow must never fall back to the default Java icon");
                window.applyTheme(UiTheme.lightTheme());
                window.applyTheme(UiTheme.darkTheme());

                ModelManagerDialog dialog = new ModelManagerDialog(window, manager, downloads, UiTheme.darkTheme(), () -> {});
                try {
                    assertEquals(8, dialog.getIconImages().size(), "ModelManagerDialog must also carry brand window icons");
                } finally {
                    dialog.dispose();
                }
            } finally {
                window.dispose();
            }
        });
    }

    private static double contrastRatio(Color fg, Color bg) {
        double l1 = relativeLuminance(fg);
        double l2 = relativeLuminance(bg);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(Color color) {
        double r = linearize(color.getRed() / 255.0);
        double g = linearize(color.getGreen() / 255.0);
        double b = linearize(color.getBlue() / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearize(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
