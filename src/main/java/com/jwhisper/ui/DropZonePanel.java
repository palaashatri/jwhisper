package com.jwhisper.ui;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.AbstractAction;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class DropZonePanel extends JPanel implements Accessible {
    private static final DataFlavor URI_LIST_FLAVOR = createUriListFlavor();

    private final Runnable onClick;
    private final Consumer<List<Path>> onDrop;
    private UiTheme theme = UiTheme.current();
    private boolean hovering;
    private boolean dragOver;

    DropZonePanel(Runnable onClick, Consumer<List<Path>> onDrop) {
        this.onClick = onClick;
        this.onDrop = onDrop;
        setOpaque(false);
        setFocusable(true);
        setPreferredSize(new Dimension(840, 126));
        setMinimumSize(new Dimension(520, 114));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 138));
        UiTheme.syncInteractiveCursor(this);
        setToolTipText("Drop audio, video, or a folder of media files — or click / press Enter to browse");

        installDropTarget();
        installKeyboardAndFocus();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (isEnabled() && SwingUtilities.isLeftMouseButton(event)) {
                    requestFocusInWindow();
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

    private void installKeyboardAndFocus() {
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "activateDropZone");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activateDropZone");
        getActionMap().put("activateDropZone", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isEnabled()) {
                    onClick.run();
                }
            }
        });
    }

    private void installDropTarget() {
        setDropTarget(new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                handleDragEvent(dtde);
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                handleDragEvent(dtde);
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
                handleDragEvent(dtde);
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                if (dragOver) {
                    dragOver = false;
                    repaint();
                }
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                boolean wasDragOver = dragOver;
                dragOver = false;
                if (wasDragOver) {
                    repaint();
                }
                if (!isEnabled() || !supportsFileDrop(dtde.getCurrentDataFlavorsAsList())) {
                    dtde.rejectDrop();
                    return;
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                boolean success = false;
                try {
                    List<Path> droppedPaths = extractPaths(dtde.getTransferable());
                    if (!droppedPaths.isEmpty()) {
                        success = true;
                        SwingUtilities.invokeLater(() -> onDrop.accept(droppedPaths));
                    }
                } catch (Exception ignored) {
                    success = false;
                } finally {
                    dtde.dropComplete(success);
                    repaint();
                }
            }
        }, true));
    }

    private void handleDragEvent(DropTargetDragEvent dtde) {
        if (isEnabled() && supportsFileDrop(dtde.getCurrentDataFlavorsAsList())) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY);
            if (!dragOver) {
                dragOver = true;
                repaint();
            }
        } else {
            dtde.rejectDrag();
            if (dragOver) {
                dragOver = false;
                repaint();
            }
        }
    }

    private static boolean supportsFileDrop(List<DataFlavor> flavors) {
        for (DataFlavor flavor : flavors) {
            if (DataFlavor.javaFileListFlavor.equals(flavor)
                    || (URI_LIST_FLAVOR != null && URI_LIST_FLAVOR.equals(flavor))) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> extractPaths(Transferable transferable) throws Exception {
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            @SuppressWarnings("unchecked")
            List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
            return files.stream().map(File::toPath).toList();
        }
        if (URI_LIST_FLAVOR != null && transferable.isDataFlavorSupported(URI_LIST_FLAVOR)) {
            String raw = (String) transferable.getTransferData(URI_LIST_FLAVOR);
            List<Path> paths = new ArrayList<>();
            for (String line : raw.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                URI uri = URI.create(trimmed);
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    paths.add(Paths.get(uri));
                }
            }
            return paths;
        }
        return List.of();
    }

    private static DataFlavor createUriListFlavor() {
        try {
            return new DataFlavor("text/uri-list;class=java.lang.String");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    void setTheme(UiTheme theme) {
        this.theme = theme;
        repaint();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setFocusable(enabled);
        UiTheme.syncInteractiveCursor(this);
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
        boolean enabled = isEnabled();
        boolean active = enabled && (hovering || dragOver);

        Color fill = active ? theme.dropHoverFill() : theme.dropFill();
        g2.setColor(fill);
        g2.fillRoundRect(1, 1, w - 2, h - 2, arc, arc);

        // Ambient sculpted amethyst glow behind the waveform
        float radius = Math.max(90f, w * 0.34f);
        Point2D center = new Point2D.Float(w / 2f, h * 0.38f);
        int glowAlpha = !enabled
                ? (theme.dark() ? 18 : 12)
                : (active ? (theme.dark() ? 68 : 44) : (theme.dark() ? 34 : 22));
        Color glowCenter = UiTheme.withAlpha(theme.accent(), glowAlpha);
        Color glowEdge = UiTheme.withAlpha(theme.accent(), 0);
        g2.setPaint(new RadialGradientPaint(center, radius, new float[]{0f, 1f}, new Color[]{glowCenter, glowEdge}));
        g2.fillRoundRect(2, 2, w - 4, h - 4, arc - 2, arc - 2);

        Color border = !enabled
                ? UiTheme.withAlpha(theme.dropBorder(), 130)
                : (active ? theme.dropHoverBorder() : theme.dropBorder());
        g2.setColor(border);
        if (dragOver && enabled) {
            g2.setStroke(new BasicStroke(2.1f));
        } else {
            g2.setStroke(new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{7f, 5f}, 0));
        }
        g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);

        if (isFocusOwner() && enabled) {
            g2.setColor(UiTheme.withAlpha(theme.accentGlow(), 200));
            g2.setStroke(new BasicStroke(1.7f));
            g2.drawRoundRect(3, 3, w - 7, h - 7, arc - 4, arc - 4);
        }

        int centerX = w / 2;
        int waveCenterY = h / 2 - 22;
        drawSculptedWaveform(g2, centerX, waveCenterY, active, enabled);

        String title = enabled
                ? "Drop audio, video, or a folder here — or click to browse"
                : "Transcribing locally with Whisper ONNX...";
        g2.setColor(enabled ? theme.text() : theme.muted());
        g2.setFont(getFont().deriveFont(Font.BOLD, 15.5f));
        FontMetrics titleMetrics = g2.getFontMetrics();
        g2.drawString(title, centerX - titleMetrics.stringWidth(title) / 2, h / 2 + 18);

        String subtitle = "WAV · MP3 · M4A · FLAC · OGG · OPUS · AAC · MP4 · MKV · MOV · WEBM   ·   100% Local Audio";
        g2.setColor(enabled ? theme.muted() : theme.subtle());
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11.5f));
        FontMetrics subMetrics = g2.getFontMetrics();
        g2.drawString(subtitle, centerX - subMetrics.stringWidth(subtitle) / 2, h / 2 + 38);

        g2.dispose();
    }

    /**
     * Paints a three-ribbon dimensional purple waveform echoing JWhisper's sculpted 3D app icon.
     */
    private void drawSculptedWaveform(Graphics2D g2, int cx, int cy, boolean active, boolean enabled) {
        int halfSpan = 58;
        int alphaScale = enabled ? 255 : 140;
        // Back deep royal violet ribbon
        drawWaveRibbon(
                g2, cx - halfSpan, cy + 2, halfSpan * 2, 17.0, 1.35, -0.55,
                2.2f,
                UiTheme.withAlpha(theme.accentStrong(), ((active ? 190 : 145) * alphaScale) / 255),
                UiTheme.withAlpha(theme.accent(), ((active ? 175 : 130) * alphaScale) / 255)
        );
        // Middle luminous amethyst ribbon
        drawWaveRibbon(
                g2, cx - halfSpan, cy, halfSpan * 2, 21.0, 1.55, 0.0,
                2.8f,
                UiTheme.withAlpha(theme.accentStrong(), alphaScale),
                UiTheme.withAlpha(theme.accent(), alphaScale)
        );
        // Front specular lavender-glass crest ribbon
        drawWaveRibbon(
                g2, cx - halfSpan, cy - 1, halfSpan * 2, 15.5, 1.75, 0.55,
                1.9f,
                UiTheme.withAlpha(theme.accent(), alphaScale),
                UiTheme.withAlpha(theme.accentGlow(), alphaScale)
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

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPanel() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.PUSH_BUTTON;
                }

                @Override
                public String getAccessibleName() {
                    return "Choose audio, video, or folder to transcribe";
                }
            };
        }
        return accessibleContext;
    }
}
