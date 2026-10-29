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
import java.nio.charset.StandardCharsets;
import java.util.Locale;
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
    public static UiTheme current() {
        String override = System.getProperty("jwhisper.theme", System.getenv("JWHISPER_THEME"));
        if (override != null && !override.isBlank()) {
            return "dark".equalsIgnoreCase(override) ? darkTheme() : lightTheme();
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

    /** Pearl Ceramic & Amethyst light palette harmonized with the sculpted purple icon. */
    public static UiTheme lightTheme() {
        return new UiTheme(
                false,
                new Color(244, 242, 250), // #F4F2FA pearl-lavender canvas
                new Color(255, 255, 255), // #FFFFFF ceramic card surface
                new Color(238, 233, 250), // #EEE9FA elevated hover surface
                new Color(250, 249, 254), // #FAF9FE recessed input surface
                new Color(210, 201, 232), // #D2C9E8 control border
                new Color(224, 217, 241), // #E0D9F1 card outline
                new Color(25, 21, 40),    // #191528 deep plum-obsidian ink
                new Color(93, 87, 117),   // #5D5775 muted label ink
                new Color(140, 133, 164), // #8C85A4 placeholder ink
                new Color(228, 222, 242), // #E4DEF2 divider
                new Color(126, 66, 235),  // #7E42EB accessible amethyst accent
                new Color(102, 41, 217),  // #6629D9 deep royal violet
                new Color(238, 231, 254), // #EEE7FE soft amethyst pill fill
                new Color(195, 173, 247), // #C3ADF7 amethyst pill outline
                new Color(161, 108, 246), // #A16CF6 brand specular highlight
                Color.WHITE,
                new Color(5, 150, 105),   // #059669 emerald ready indicator
                new Color(217, 119, 6),   // #D97706 amber busy indicator
                new Color(255, 241, 243), // #FFF1F3 danger fill
                new Color(242, 184, 196), // #F2B8C4 danger border
                new Color(190, 18, 60),   // #BE123C danger text
                new Color(250, 248, 254), // #FAF8FE dropzone fill
                new Color(240, 233, 254), // #F0E9FE dropzone hover fill
                new Color(196, 184, 228), // #C4B8E4 dropzone border
                new Color(134, 73, 238)   // #8649EE dropzone hover border
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
                new Color(47, 56, 86),    // #2F3856 control border
                new Color(38, 45, 70),    // #262D46 card border
                new Color(244, 247, 252), // #F4F7FC crisp foreground (palette.json #F4F7FC)
                new Color(156, 166, 190), // #9CA6BE secondary text (matches wordmark subtitle)
                new Color(108, 118, 146), // #6C7692 placeholder text
                new Color(37, 45, 69),    // #252D45 divider
                new Color(161, 108, 246), // #A16CF6 luminous amethyst accent (palette.json #A16CF6)
                new Color(124, 58, 237),  // #7C3AED deep sculpted violet
                new Color(38, 27, 70),    // #261B46 translucent amethyst pill tint
                new Color(94, 60, 166),   // #5E3CA6 amethyst pill border
                new Color(204, 176, 255), // #CCB0FF lavender glass specular crest
                Color.WHITE,
                new Color(52, 211, 153),  // #34D399 emerald ready indicator
                new Color(251, 191, 36),  // #FBBF24 amber busy indicator
                new Color(48, 25, 37),    // #301925 danger fill
                new Color(112, 46, 72),   // #702E48 danger border
                new Color(253, 164, 175), // #FDA4AF danger text
                new Color(20, 25, 41),    // #141929 dropzone fill
                new Color(32, 25, 60),    // #20193C dropzone amethyst glow fill
                new Color(59, 68, 106),   // #3B446A dropzone border
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
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(6, 14, 6, 14));
        button.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        button.setUI(new BrandButtonUI(this, role));
        button.repaint();
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

    private static Boolean macOsDarkMode() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("mac")) {
            return null;
        }

        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(350, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                return false;
            }
            return "dark".equalsIgnoreCase(output);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
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
                g2.setColor(theme.dark ? withAlpha(theme.field, 180) : withAlpha(theme.elevatedSurface, 150));
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(withAlpha(theme.fieldBorder, 140));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                textColor = theme.subtle;
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
                        : (rollover ? theme.elevatedSurface : withAlpha(theme.elevatedSurface, theme.dark ? 150 : 190));
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
                g2.setColor(rollover ? theme.accentBorder : theme.cardBorder);
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
                g2.setColor(withAlpha(theme.accentGlow, 170));
                g2.setStroke(new BasicStroke(1.5f));
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
        protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
            // Handled in paint() for crisp centering across platforms.
        }
    }
}
