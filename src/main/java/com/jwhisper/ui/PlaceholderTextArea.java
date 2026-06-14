package com.jwhisper.ui;

import javax.swing.JTextArea;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

final class PlaceholderTextArea extends JTextArea {
    private final String placeholder;
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

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!getText().isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(theme.subtle());
        FontMetrics metrics = g2.getFontMetrics();
        int x = Math.max(16, (getWidth() - metrics.stringWidth(placeholder)) / 2);
        int y = Math.max(metrics.getAscent() + 16, (getHeight() + metrics.getAscent()) / 2 - 4);
        g2.drawString(placeholder, x, y);
        g2.dispose();
    }
}
