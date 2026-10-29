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
    }

    void setTheme(UiTheme theme) {
        this.theme = theme;
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
        g2.setColor(theme.subtle());
        g2.setFont(getFont());
        Insets insets = getInsets();
        FontMetrics metrics = g2.getFontMetrics();
        int x = Math.max(insets.left, 16);
        int y = Math.max(insets.top + metrics.getAscent(), metrics.getAscent() + 14);
        g2.drawString(placeholder, x, y);
        g2.dispose();
    }
}
