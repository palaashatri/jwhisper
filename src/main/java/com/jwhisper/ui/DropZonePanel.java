package com.jwhisper.ui;

import javax.swing.JPanel;
import javax.swing.TransferHandler;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

final class DropZonePanel extends JPanel {
    private final Runnable onClick;
    private final Consumer<List<Path>> onDrop;
    private UiTheme theme = UiTheme.current();
    private boolean hovering;

    DropZonePanel(Runnable onClick, Consumer<List<Path>> onDrop) {
        this.onClick = onClick;
        this.onDrop = onDrop;
        setOpaque(false);
        setPreferredSize(new Dimension(820, 132));
        setMinimumSize(new Dimension(480, 118));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Choose an audio file");
        setTransferHandler(new FileDropHandler());
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                DropZonePanel.this.onClick.run();
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

    void setTheme(UiTheme theme) {
        this.theme = theme;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int inset = 1;
        Color border = hovering ? theme.dropHoverBorder() : theme.dropBorder();
        Color fill = hovering ? theme.dropHoverFill() : theme.dropFill();
        g2.setColor(fill);
        g2.fillRoundRect(inset, inset, getWidth() - 2, getHeight() - 2, 8, 8);
        g2.setColor(border);
        g2.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{7, 5}, 0));
        g2.drawRoundRect(inset, inset, getWidth() - 3, getHeight() - 3, 8, 8);

        int centerX = getWidth() / 2;
        int iconY = getHeight() / 2 - 38;
        drawHeadphones(g2, centerX, iconY);

        String text = "Drop audio here or click to choose a file";
        g2.setColor(theme.text());
        g2.setFont(getFont().deriveFont(Font.PLAIN, 19f));
        int textWidth = g2.getFontMetrics().stringWidth(text);
        g2.drawString(text, centerX - textWidth / 2, getHeight() / 2 + 28);
        g2.dispose();
    }

    private void drawHeadphones(Graphics2D g2, int centerX, int topY) {
        g2.setColor(theme.muted());
        g2.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawArc(centerX - 23, topY, 46, 46, 12, 156);
        g2.fillRoundRect(centerX - 28, topY + 23, 10, 24, 7, 7);
        g2.fillRoundRect(centerX + 18, topY + 23, 10, 24, 7, 7);
    }

    private final class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
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
