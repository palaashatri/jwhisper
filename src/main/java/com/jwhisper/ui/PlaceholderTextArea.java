package com.jwhisper.ui;

import javax.swing.JTextArea;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

final class PlaceholderTextArea extends JTextArea {
    private String placeholder;
    private UiTheme theme = UiTheme.current();

    PlaceholderTextArea(String placeholder) {
        this.placeholder = placeholder;
        setLineWrap(true);
        setWrapStyleWord(true);
        ThemedControls.installUndoSupport(this);
    }

    void setTheme(UiTheme theme) {
        this.theme = theme;
        setDisabledTextColor(UiTheme.withAlpha(theme.muted(), 125));
        repaint();
    }

    void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!getText().isEmpty() || placeholder == null || placeholder.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(isEnabled() ? theme.subtle() : UiTheme.withAlpha(theme.muted(), 95));
        g2.setFont(getFont());
        Insets insets = getInsets();
        FontMetrics metrics = g2.getFontMetrics();
        int x = Math.max(insets.left, 14);
        int y = Math.max(insets.top + metrics.getAscent(), metrics.getAscent() + 12);
        int maxWidth = Math.max(120, getWidth() - x - Math.max(insets.right, 14));
        drawWrappedPlaceholder(g2, metrics, placeholder, x, y, maxWidth);
        g2.dispose();
    }

    private static void drawWrappedPlaceholder(
            Graphics2D g2,
            FontMetrics fm,
            String text,
            int x,
            int startY,
            int maxWidth
    ) {
        if (fm.stringWidth(text) <= maxWidth) {
            g2.drawString(text, x, startY);
            return;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int y = startY;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                g2.drawString(line.toString(), x, y);
                line.setLength(0);
                line.append(word);
                y += fm.getHeight();
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) {
            g2.drawString(line.toString(), x, y);
        }
    }
}
