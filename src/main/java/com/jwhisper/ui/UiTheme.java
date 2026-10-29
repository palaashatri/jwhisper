package com.jwhisper.ui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Brand color palette and component styling aligned with JWhisper's sculpted
 * amethyst waveform identity (assets/brand/palette.json).
 */
public record UiTheme(
        boolean dark,
        Color background,
        Color surface,
        Color elevatedSurface,
        Color field,
        Color fieldBorder,
        Color cardBorder,
        Color text,
        Color muted,
        Color subtle,
        Color separator,
        Color accent,
        Color accentStrong,
        Color accentSoft,
        Color accentBorder,
        Color accentGlow,
        Color accentText,
        Color success,
        Color warning,
        Color dangerFill,
        Color dangerBorder,
        Color dangerText,
        Color dropFill,
        Color dropHoverFill,
        Color dropBorder,
        Color dropHoverBorder
) {
    private static final String CURSOR_SYNC_KEY = "jwhisper.cursorSynced";
    private static volatile Boolean cachedMacDarkMode;
    private static volatile boolean macDarkModeProbed;

    public static UiTheme current() {
        String override = System.getProperty("jwhisper.theme", System.getenv("JWHISPER_THEME"));
        if (override != null && !override.isBlank()) {
            return "dark".equalsIgnoreCase(override) ? darkTheme() : lightTheme();
        }

        Boolean saved = loadPersistedPreference();
        if (saved != null) {
            return saved ? darkTheme() : lightTheme();
        }

        Boolean macDarkMode = macOsDarkMode();
        if (macDarkMode != null) {
            return macDarkMode ? darkTheme() : lightTheme();
        }

        Color panel = UIManager.getColor("Panel.background");
        if (panel == null || brightness(panel) < 120) {
            return darkTheme();
        }
        return lightTheme();
    }

    public static void persistPreference(boolean dark) {
        String override = System.getProperty("jwhisper.theme", System.getenv("JWHISPER_THEME"));
        if (override != null && !override.isBlank()) {
            return;
        }
        Path prefsFile = preferencesPath();
        if (prefsFile == null) {
            return;
        }
        try {
            Files.createDirectories(prefsFile.getParent());
            Properties props = new Properties();
            props.setProperty("theme", dark ? "dark" : "light");
            try (OutputStream out = Files.newOutputStream(prefsFile)) {
                props.store(out, "JWhisper UI Preferences");
            }
        } catch (IOException ignored) {
            // Non-fatal: theme still applies for the active session.
        }
    }

    /** Pearl Ceramic & Amethyst light palette harmonized with the sculpted purple icon (WCAG AA contrast). */
    public static UiTheme lightTheme() {
        return new UiTheme(
                false,
                new Color(244, 242, 250), // #F4F2FA pearl-lavender canvas
                new Color(255, 255, 255), // #FFFFFF ceramic card surface
                new Color(238, 233, 250), // #EEE9FA elevated hover surface
                new Color(250, 249, 254), // #FAF9FE recessed input surface
                new Color(202, 192, 226), // #CAC0E2 control border
                new Color(220, 212, 239), // #DCD4EF card outline
                new Color(25, 21, 40),    // #191528 deep plum-obsidian ink (~15.8:1)
                new Color(79, 72, 103),   // #4F4867 muted label ink (~8.1:1)
                new Color(103, 96, 128),  // #676080 placeholder ink (~5.4:1 WCAG AA)
                new Color(225, 218, 241), // #E1DAF1 divider
                new Color(122, 60, 232),  // #7A3CE8 accessible amethyst accent
                new Color(98, 38, 212),   // #6226D4 deep royal violet
                new Color(237, 229, 254), // #EDE5FE soft amethyst pill fill
                new Color(188, 164, 246), // #BCA4F6 amethyst pill outline
                new Color(161, 108, 246), // #A16CF6 brand specular highlight
                Color.WHITE,
                new Color(5, 150, 105),   // #059669 emerald ready indicator
                new Color(194, 102, 4),   // #C26604 amber warning indicator
                new Color(255, 241, 243), // #FFF1F3 danger fill
                new Color(240, 176, 190), // #F0B0BE danger border
                new Color(185, 16, 58),   // #B9103A danger text
                new Color(250, 248, 254), // #FAF8FE dropzone fill
                new Color(240, 233, 254), // #F0E9FE dropzone hover fill
                new Color(190, 176, 224), // #BEB0E0 dropzone border
                new Color(126, 66, 235)   // #7E42EB dropzone hover border
        );
    }

    /** Obsidian & Amethyst Glass dark palette matching assets/brand/palette.json (#101720 / #A16CF6 / #F4F7FC). */
    public static UiTheme darkTheme() {
        return new UiTheme(
                true,
                new Color(16, 21, 32),    // #101520 deep obsidian-indigo canvas (palette.json #101720)
                new Color(23, 28, 44),    // #171C2C dimensional glass card surface
                new Color(31, 38, 60),    // #1F263C elevated hover surface
                new Color(17, 22, 35),    // #111623 recessed editor/input surface
                new Color(50, 60, 92),    // #323C5C control border
                new Color(40, 48, 75),    // #28304B card border
                new Color(244, 247, 252), // #F4F7FC crisp foreground (palette.json #F4F7FC)
                new Color(169, 179, 204), // #A9B3CC secondary text (~8.9:1)
                new Color(138, 149, 179), // #8A95B3 placeholder text (~5.5:1 WCAG AA)
                new Color(38, 46, 71),    // #262E47 divider
                new Color(161, 108, 246), // #A16CF6 luminous amethyst accent (palette.json #A16CF6)
                new Color(124, 58, 237),  // #7C3AED deep sculpted violet
                new Color(40, 28, 74),    // #281C4A translucent amethyst pill tint
                new Color(98, 63, 173),   // #623FAD amethyst pill border
                new Color(204, 176, 255), // #CCB0FF lavender glass specular crest
                Color.WHITE,
                new Color(52, 211, 153),  // #34D399 emerald ready indicator
                new Color(251, 191, 36),  // #FBBF24 amber warning indicator
                new Color(48, 25, 37),    // #301925 danger fill
                new Color(118, 48, 76),   // #76304C danger border
                new Color(253, 164, 175), // #FDA4AF danger text
                new Color(20, 25, 41),    // #141929 dropzone fill
                new Color(32, 25, 60),    // #20193C dropzone amethyst glow fill
                new Color(62, 72, 112),   // #3E4870 dropzone border
                new Color(161, 108, 246)  // #A16CF6 dropzone hover border
        );
    }

    public void styleButton(JButton button, ButtonRole role) {
        int style = role == ButtonRole.PRIMARY ? Font.BOLD : Font.PLAIN;
        button.setFont(button.getFont().deriveFont(style, 13f));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        syncInteractiveCursor(button);
        if (!Boolean.TRUE.equals(button.getClientProperty(CURSOR_SYNC_KEY))) {
            button.putClientProperty(CURSOR_SYNC_KEY, Boolean.TRUE);
            button.addPropertyChangeListener("enabled", evt -> syncInteractiveCursor(button));
        }
        button.setMargin(new Insets(6, 14, 6, 14));
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        button.setUI(new BrandButtonUI(this, role));
        button.repaint();
    }

    static void syncInteractiveCursor(JComponent component) {
        component.setCursor(Cursor.getPredefinedCursor(
                component.isEnabled() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR
        ));
    }

    public void applyGlobalDefaults() {
        UIManager.put("Panel.background", background);
        UIManager.put("OptionPane.background", background);
        UIManager.put("OptionPane.messageForeground", text);
        UIManager.put("Label.foreground", text);
        UIManager.put("Separator.foreground", separator);
        UIManager.put("Separator.background", separator);

        UIManager.put("TextArea.background", field);
        UIManager.put("TextArea.foreground", text);
        UIManager.put("TextArea.caretForeground", text);
        UIManager.put("TextField.background", field);
        UIManager.put("TextField.foreground", text);
        UIManager.put("TextField.caretForeground", text);

        UIManager.put("ComboBox.background", field);
        UIManager.put("ComboBox.foreground", text);
        UIManager.put("ComboBox.selectionBackground", accent);
        UIManager.put("ComboBox.selectionForeground", accentText);
        UIManager.put("List.background", field);
        UIManager.put("List.foreground", text);
        UIManager.put("List.selectionBackground", accent);
        UIManager.put("List.selectionForeground", accentText);

        UIManager.put("Button.background", surface);
        UIManager.put("Button.foreground", text);
        UIManager.put("Button.select", elevatedSurface);
        UIManager.put("ProgressBar.background", field);
        UIManager.put("ProgressBar.foreground", accent);
        UIManager.put("ProgressBar.selectionBackground", text);
        UIManager.put("ProgressBar.selectionForeground", accentText);
        UIManager.put("ScrollPane.background", surface);
        UIManager.put("Viewport.background", field);
        UIManager.put("ToolTip.background", elevatedSurface);
        UIManager.put("ToolTip.foreground", text);
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accentBorder),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
    }

    public void styleField(JComponent component) {
        component.setForeground(text);
        component.setBackground(field);
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fieldBorder),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
    }

    static Color withAlpha(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    static Color blend(Color a, Color b, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        float inv = 1f - clamped;
        return new Color(
                Math.round(a.getRed() * inv + b.getRed() * clamped),
                Math.round(a.getGreen() * inv + b.getGreen() * clamped),
                Math.round(a.getBlue() * inv + b.getBlue() * clamped)
        );
    }

    private static Path preferencesPath() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            return null;
        }
        return Paths.get(home, ".jwhisper", "ui-preferences.properties");
    }

    private static Boolean loadPersistedPreference() {
        Path prefsFile = preferencesPath();
        if (prefsFile == null || !Files.isRegularFile(prefsFile)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(prefsFile)) {
            props.load(in);
            String val = props.getProperty("theme");
            if ("dark".equalsIgnoreCase(val)) {
                return Boolean.TRUE;
            }
            if ("light".equalsIgnoreCase(val)) {
                return Boolean.FALSE;
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private static Boolean macOsDarkMode() {
        if (macDarkModeProbed) {
            return cachedMacDarkMode;
        }
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("mac")) {
            macDarkModeProbed = true;
            cachedMacDarkMode = null;
            return null;
        }

        Boolean detected = null;
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(350, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            } else {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                detected = process.exitValue() == 0 && "dark".equalsIgnoreCase(output);
            }
        } catch (IOException ignored) {
            detected = null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            detected = null;
        }
        cachedMacDarkMode = detected;
        macDarkModeProbed = true;
        return detected;
    }

    private static int brightness(Color color) {
        return (int) Math.round((color.getRed() * 0.299) + (color.getGreen() * 0.587) + (color.getBlue() * 0.114));
    }

    public enum ButtonRole {
        NORMAL,
        PRIMARY,
        SUBTLE,
        DANGER
    }

    private static final class BrandButtonUI extends BasicButtonUI {
        private final UiTheme theme;
        private final ButtonRole role;

        private BrandButtonUI(UiTheme theme, ButtonRole role) {
            this.theme = theme;
            this.role = role;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton button = (AbstractButton) c;
            ButtonModel model = button.getModel();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = c.getWidth();
            int h = c.getHeight();
            int arc = 10;
            boolean enabled = button.isEnabled();
            boolean pressed = enabled && (model.isArmed() && model.isPressed());
            boolean rollover = enabled && model.isRollover();

            Color textColor;
            if (!enabled) {
                g2.setColor(theme.dark ? withAlpha(theme.field, 145) : withAlpha(theme.elevatedSurface, 120));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(withAlpha(theme.fieldBorder, 105));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                textColor = withAlpha(theme.muted, theme.dark ? 110 : 120);
            } else if (role == ButtonRole.PRIMARY) {
                Color top = pressed
                        ? theme.accentStrong.darker()
                        : (rollover ? blend(theme.accent, theme.accentGlow, 0.28f) : theme.accent);
                Color bottom = pressed
                        ? theme.accentStrong
                        : (rollover ? theme.accent : theme.accentStrong);
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

                // Dimensional glass rim specular highlight matching the 3D waveform icon
                g2.setColor(withAlpha(theme.accentGlow, 110));
                g2.drawLine(4, 1, w - 5, 1);
                g2.setColor(blend(theme.accentStrong, Color.BLACK, 0.18f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                textColor = theme.accentText;
            } else if (role == ButtonRole.DANGER) {
                Color fill = pressed
                        ? blend(theme.dangerFill, theme.dangerBorder, 0.35f)
                        : (rollover ? blend(theme.dangerFill, theme.dangerBorder, 0.18f) : theme.dangerFill);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(theme.dangerBorder);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                textColor = theme.dangerText;
            } else if (role == ButtonRole.SUBTLE) {
                Color fill = pressed
                        ? theme.accentSoft
                        : (rollover ? theme.elevatedSurface : withAlpha(theme.elevatedSurface, theme.dark ? 175 : 210));
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(rollover ? theme.accentBorder : theme.fieldBorder);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                textColor = rollover ? theme.text : theme.muted;
            } else {
                Color fill = pressed
                        ? theme.elevatedSurface
                        : (rollover ? blend(theme.field, theme.elevatedSurface, 0.7f) : theme.field);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(rollover ? theme.accentBorder : theme.fieldBorder);
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                textColor = theme.text;
            }

            if (button.isFocusOwner() && enabled) {
                g2.setColor(withAlpha(theme.accentGlow, 195));
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, Math.max(4, arc - 2), Math.max(4, arc - 2));
            }

            String text = button.getText();
            if (text != null && !text.isEmpty()) {
                g2.setFont(button.getFont());
                FontMetrics fm = g2.getFontMetrics();
                int textX = (w - fm.stringWidth(text)) / 2;
                int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.setColor(textColor);
                g2.drawString(text, textX, textY);
            }
            g2.dispose();
        }

        @Override
        public java.awt.Dimension getMaximumSize(JComponent c) {
            return c.getPreferredSize();
        }

        @Override
        protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
            // Handled in paint() for crisp centering across platforms.
        }
    }
}
