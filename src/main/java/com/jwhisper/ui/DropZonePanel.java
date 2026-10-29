package com.jwhisper.ui;

import javax.swing.JPanel;
import javax.swing.TransferHandler;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.function.Consumer;

final class DropZonePanel extends JPanel {
    private final Runnable onClick;
    private final Consumer<List<Path>> onDrop;
    private UiTheme theme = UiTheme.current();
    private boolean hovering;
    private boolean dragOver;

    DropZonePanel(Runnable onClick, Consumer<List<Path>> onDrop) {
        this.onClick = onClick;
        this.onDrop = onDrop;
        setOpaque(false);
        setPreferredSize(new Dimension(840, 128));
        setMinimumSize(new Dimension(520, 114));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 142));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Drop audio, video, or a folder of media files — or click to browse");
        setTransferHandler(new FileDropHandler());
        installDragFeedback();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (isEnabled()) {
                    DropZonePanel.this.onClick.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent event) {
                hovering = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovering = false;
                repaint();
            }
        });
    }

    private void installDragFeedback() {
        if (getDropTarget() == null) {
            return;
        }
        try {
            getDropTarget().addDropTargetListener(new DropTargetAdapter() {
                @Override
                public void dragEnter(DropTargetDragEvent dtde) {
                    dragOver = true;
                    repaint();
                }

                @Override
                public void dragExit(DropTargetEvent dte) {
                    dragOver = false;
                    repaint();
                }

                @Override
                public void drop(DropTargetDropEvent dtde) {
                    dragOver = false;
                    repaint();
                }
            });
        } catch (TooManyListenersException ignored) {
            // Swing's default TransferHandler drop target already has a listener on some platforms.
        }
    }

    void setTheme(UiTheme theme) {
        this.theme = theme;
        repaint();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        if (!enabled) {
            hovering = false;
            dragOver = false;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int arc = 16;
        boolean active = isEnabled() && (hovering || dragOver);

        Color fill = active ? theme.dropHoverFill() : theme.dropFill();
        g2.setColor(fill);
        g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);

        // Ambient sculpted amethyst glow behind the waveform
        float radius = Math.max(90f, w * 0.34f);
        Point2D center = new Point2D.Float(w / 2f, h * 0.38f);
        Color glowCenter = UiTheme.withAlpha(theme.accent(), active ? (theme.dark() ? 66 : 42) : (theme.dark() ? 32 : 20));
        Color glowEdge = UiTheme.withAlpha(theme.accent(), 0);
        g2.setPaint(new RadialGradientPaint(center, radius, new float[]{0f, 1f}, new Color[]{glowCenter, glowEdge}));
        g2.fillRoundRect(2, 2, w - 4, h - 4, arc - 2, arc - 2);

        Color border = active ? theme.dropHoverBorder() : theme.dropBorder();
        g2.setColor(border);
        if (dragOver && isEnabled()) {
            g2.setStroke(new BasicStroke(2.0f));
        } else {
            g2.setStroke(new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{7f, 5f}, 0));
        }
        g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);

        int centerX = w / 2;
        int waveCenterY = h / 2 - 22;
        drawSculptedWaveform(g2, centerX, waveCenterY, active);

        String title = isEnabled()
                ? "Drop audio, video, or a folder here — or click to browse"
                : "Transcribing locally with Whisper ONNX...";
        g2.setColor(isEnabled() ? theme.text() : theme.muted());
        g2.setFont(getFont().deriveFont(Font.BOLD, 15.5f));
        FontMetrics titleMetrics = g2.getFontMetrics();
        g2.drawString(title, centerX - titleMetrics.stringWidth(title) / 2, h / 2 + 19);

        String subtitle = "WAV • MP3 • M4A • FLAC • OGG • AAC • MP4 • MKV • MOV • WEBM • AVI   ·   100% Local Audio";
        g2.setColor(theme.subtle());
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11.5f));
        FontMetrics subMetrics = g2.getFontMetrics();
        g2.drawString(subtitle, centerX - subMetrics.stringWidth(subtitle) / 2, h / 2 + 39);

        g2.dispose();
    }

    /**
     * Paints a three-ribbon dimensional purple waveform echoing JWhisper's sculpted 3D app icon.
     */
    private void drawSculptedWaveform(Graphics2D g2, int cx, int cy, boolean active) {
        int halfSpan = 58;
        // Back deep royal violet ribbon
        drawWaveRibbon(
                g2, cx - halfSpan, cy + 2, halfSpan * 2, 17.0, 1.35, -0.55,
                2.2f,
                UiTheme.withAlpha(theme.accentStrong(), active ? 190 : 145),
                UiTheme.withAlpha(theme.accent(), active ? 175 : 130)
        );
        // Middle luminous amethyst ribbon
        drawWaveRibbon(
                g2, cx - halfSpan, cy, halfSpan * 2, 21.0, 1.55, 0.0,
                2.8f,
                theme.accentStrong(),
                theme.accent()
        );
        // Front specular lavender-glass crest ribbon
        drawWaveRibbon(
                g2, cx - halfSpan, cy - 1, halfSpan * 2, 15.5, 1.75, 0.55,
                1.9f,
                theme.accent(),
                theme.accentGlow()
        );
    }

    private void drawWaveRibbon(
            Graphics2D g2,
            int startX,
            int centerY,
            int width,
            double amplitude,
            double cycles,
            double phase,
            float strokeWidth,
            Color leftColor,
            Color rightColor
    ) {
        Path2D.Double path = new Path2D.Double();
        int steps = 56;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = startX + t * width;
            // Smooth Hann-like bell envelope so ends taper gracefully like the logo
            double envelope = Math.pow(Math.sin(Math.PI * t), 1.35);
            double y = centerY - amplitude * envelope * Math.sin(2.0 * Math.PI * cycles * t + phase);
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        g2.setPaint(new GradientPaint(startX, centerY, leftColor, startX + width, centerY, rightColor));
        g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(path);
    }

    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return isEnabled() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            dragOver = false;
            repaint();
            if (!canImport(support)) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                onDrop.accept(files.stream().map(File::toPath).toList());
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
