package com.jwhisper.ui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.awt.Window;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and installs JWhisper's multi-resolution sculpted waveform icon set across
 * Swing windows, dialogs, HiDPI UI headers, and OS docks/taskbars.
 */
public final class AppIcons {
    private static final int[] ICON_SIZES = {16, 24, 32, 48, 64, 128, 256, 512};
    private static volatile Map<Integer, BufferedImage> cachedIcons;

    private AppIcons() {
    }

    public static List<Image> windowIcons() {
        Map<Integer, BufferedImage> icons = loadAll();
        if (icons.isEmpty()) {
            return List.of();
        }
        return List.copyOf(icons.values());
    }

    public static BufferedImage iconImage(int preferredSize) {
        Map<Integer, BufferedImage> icons = loadAll();
        if (icons.isEmpty()) {
            return null;
        }
        BufferedImage exact = icons.get(preferredSize);
        if (exact != null) {
            return exact;
        }
        BufferedImage source = null;
        for (int size : ICON_SIZES) {
            if (size >= preferredSize && icons.containsKey(size)) {
                source = icons.get(size);
                break;
            }
        }
        if (source == null) {
            List<BufferedImage> all = new ArrayList<>(icons.values());
            source = all.get(all.size() - 1);
        }
        return scaleImage(source, preferredSize);
    }

    public static ImageIcon hiDpiIcon(int logicalSize) {
        BufferedImage base = iconImage(logicalSize);
        if (base == null) {
            return null;
        }
        BufferedImage retina = iconImage(logicalSize * 2);
        if (retina != null && retina.getWidth() > base.getWidth()) {
            Image multiRes = new BaseMultiResolutionImage(0, base, retina);
            return new ImageIcon(multiRes);
        }
        return new ImageIcon(base);
    }

    public static void applyToWindow(Window window) {
        if (window == null) {
            return;
        }
        List<Image> icons = windowIcons();
        if (!icons.isEmpty()) {
            window.setIconImages(icons);
        }
    }

    public static boolean installTaskbarIcon() {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        try {
            if (!Taskbar.isTaskbarSupported()) {
                return false;
            }
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                return false;
            }
            BufferedImage dockIcon = iconImage(512);
            if (dockIcon == null) {
                return false;
            }
            taskbar.setIconImage(dockIcon);
            return true;
        } catch (UnsupportedOperationException | SecurityException ignored) {
            return false;
        }
    }

    private static Map<Integer, BufferedImage> loadAll() {
        Map<Integer, BufferedImage> existing = cachedIcons;
        if (existing != null) {
            return existing;
        }
        synchronized (AppIcons.class) {
            if (cachedIcons != null) {
                return cachedIcons;
            }
            Map<Integer, BufferedImage> loaded = new LinkedHashMap<>();
            for (int size : ICON_SIZES) {
                String resourcePath = "/com/jwhisper/icons/icon-" + size + ".png";
                try (InputStream stream = AppIcons.class.getResourceAsStream(resourcePath)) {
                    if (stream != null) {
                        BufferedImage image = ImageIO.read(stream);
                        if (image != null) {
                            loaded.put(size, image);
                        }
                    }
                } catch (IOException ignored) {
                    // Continue loading remaining icon resolutions.
                }
            }
            cachedIcons = Collections.unmodifiableMap(loaded);
            return cachedIcons;
        }
    }

    private static BufferedImage scaleImage(BufferedImage source, int targetSize) {
        if (source.getWidth() == targetSize && source.getHeight() == targetSize) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, targetSize, targetSize, null);
        g2.dispose();
        return scaled;
    }
}
