package com.jwhisper.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public record UiTheme(
        boolean dark,
        Color background,
        Color surface,
        Color elevatedSurface,
        Color field,
        Color fieldBorder,
        Color text,
        Color muted,
        Color subtle,
        Color separator,
        Color accent,
        Color accentText,
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
        if (panel != null && brightness(panel) < 120) {
            return darkTheme();
        }
        return lightTheme();
    }

    public static UiTheme lightTheme() {
        return new UiTheme(
                false,
                new Color(246, 247, 249),
                new Color(246, 247, 249),
                new Color(255, 255, 255),
                new Color(252, 253, 255),
                new Color(201, 207, 217),
                new Color(45, 52, 65),
                new Color(113, 122, 138),
                new Color(149, 158, 173),
                new Color(220, 224, 230),
                new Color(25, 118, 236),
                Color.WHITE,
                new Color(248, 249, 251),
                new Color(242, 247, 255),
                new Color(198, 205, 216),
                new Color(75, 137, 232)
        );
    }

    public static UiTheme darkTheme() {
        return new UiTheme(
                true,
                new Color(29, 31, 36),
                new Color(29, 31, 36),
                new Color(37, 40, 47),
                new Color(42, 45, 53),
                new Color(77, 83, 96),
                new Color(235, 238, 244),
                new Color(165, 173, 187),
                new Color(122, 131, 147),
                new Color(70, 75, 86),
                new Color(75, 151, 255),
                Color.WHITE,
                new Color(34, 37, 43),
                new Color(40, 48, 61),
                new Color(78, 85, 99),
                new Color(91, 162, 255)
        );
    }

    public void styleButton(JButton button, ButtonRole role) {
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 13f));
        button.setMargin(new Insets(3, 10, 3, 10));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.putClientProperty("JButton.buttonType", "roundRect");

        if (role == ButtonRole.PRIMARY) {
            button.setForeground(accentText);
            button.setBackground(accent);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(accent.darker()),
                    BorderFactory.createEmptyBorder(3, 10, 3, 10)
            ));
        } else if (role == ButtonRole.DANGER) {
            button.setForeground(dark ? new Color(255, 196, 196) : new Color(150, 36, 36));
            button.setBackground(dark ? new Color(49, 38, 41) : new Color(255, 250, 250));
            button.setBorder(buttonBorder());
        } else {
            button.setForeground(text);
            button.setBackground(field);
            button.setBorder(buttonBorder());
        }
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

        UIManager.put("Button.background", field);
        UIManager.put("Button.foreground", text);
        UIManager.put("Button.select", elevatedSurface);
        UIManager.put("ProgressBar.background", separator);
        UIManager.put("ProgressBar.foreground", accent);
        UIManager.put("ProgressBar.selectionBackground", text);
        UIManager.put("ProgressBar.selectionForeground", accentText);
        UIManager.put("ScrollPane.background", background);
        UIManager.put("Viewport.background", field);
    }

    public void styleField(JComponent component) {
        component.setForeground(text);
        component.setBackground(field);
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fieldBorder),
                BorderFactory.createEmptyBorder(6, 9, 6, 9)
        ));
    }

    private Border buttonBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fieldBorder),
                BorderFactory.createEmptyBorder(3, 10, 3, 10)
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
        DANGER
    }
}
