package com.jwhisper.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

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
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 14f));
        button.setMargin(new Insets(5, 13, 5, 13));
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.putClientProperty("JButton.buttonType", "roundRect");

        if (role == ButtonRole.PRIMARY) {
            button.setForeground(accentText);
            button.setBackground(accent);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(accent.darker()),
                    BorderFactory.createEmptyBorder(4, 12, 4, 12)
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
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        );
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
