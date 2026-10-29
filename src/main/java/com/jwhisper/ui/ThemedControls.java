package com.jwhisper.ui;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Custom-painted Swing controls styled around JWhisper's dimensional amethyst and
 * obsidian palette so macOS Aqua, Windows, and Linux render identically.
 */
final class ThemedControls {
    private static final String COMBO_CURSOR_KEY = "jwhisper.comboCursorSynced";
    private static final String UNDO_INSTALLED_KEY = "jwhisper.undoManager";

    private ThemedControls() {
    }

    static UndoManager installUndoSupport(JTextComponent textComponent) {
        Object existing = textComponent.getClientProperty(UNDO_INSTALLED_KEY);
        if (existing instanceof UndoManager manager) {
            return manager;
        }
        UndoManager undoManager = new UndoManager();
        textComponent.putClientProperty(UNDO_INSTALLED_KEY, undoManager);
        textComponent.getDocument().addUndoableEditListener(event -> undoManager.addEdit(event.getEdit()));

        int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask), "jwhisper-undo");
        textComponent.getInputMap().put(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcutMask | InputEvent.SHIFT_DOWN_MASK),
                "jwhisper-redo"
        );
        textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcutMask), "jwhisper-redo");

        textComponent.getActionMap().put("jwhisper-undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (textComponent.isEditable() && textComponent.isEnabled() && undoManager.canUndo()) {
                    undoManager.undo();
                }
            }
        });
        textComponent.getActionMap().put("jwhisper-redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (textComponent.isEditable() && textComponent.isEnabled() && undoManager.canRedo()) {
                    undoManager.redo();
                }
            }
        });
        return undoManager;
    }

    static void styleComboBox(JComboBox<?> combo, UiTheme theme) {
        combo.setOpaque(false);
        combo.setForeground(theme.text());
        combo.setBackground(theme.field());
        combo.setFont(combo.getFont().deriveFont(Font.PLAIN, 13f));
        combo.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));
        UiTheme.syncInteractiveCursor(combo);
        if (!Boolean.TRUE.equals(combo.getClientProperty(COMBO_CURSOR_KEY))) {
            combo.putClientProperty(COMBO_CURSOR_KEY, Boolean.TRUE);
            combo.addPropertyChangeListener("enabled", evt -> UiTheme.syncInteractiveCursor(combo));
        }
        combo.setUI(new BrandComboBoxUI(theme));
        combo.repaint();
    }

    static void styleScrollPane(JScrollPane scrollPane, UiTheme theme) {
        scrollPane.setBackground(theme.field());
        scrollPane.getViewport().setBackground(theme.field());
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(theme.fieldBorder()),
                BorderFactory.createEmptyBorder(4, 0, 4, 0)
        ));
        scrollPane.setCorner(ScrollPaneConstants.LOWER_RIGHT_CORNER, themedCorner(theme));
        scrollPane.setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, themedCorner(theme));
        scrollPane.setCorner(ScrollPaneConstants.LOWER_LEFT_CORNER, themedCorner(theme));
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        if (vertical != null) {
            vertical.setOpaque(true);
            vertical.setBackground(theme.field());
            vertical.setPreferredSize(new Dimension(10, Integer.MAX_VALUE));
            vertical.setUI(new BrandScrollBarUI(theme));
        }
        JScrollBar horizontal = scrollPane.getHorizontalScrollBar();
        if (horizontal != null) {
            horizontal.setOpaque(true);
            horizontal.setBackground(theme.field());
            horizontal.setPreferredSize(new Dimension(Integer.MAX_VALUE, 10));
            horizontal.setUI(new BrandScrollBarUI(theme));
        }
    }

    private static JPanel themedCorner(UiTheme theme) {
        JPanel corner = new JPanel();
        corner.setOpaque(true);
        corner.setFocusable(false);
        corner.setBackground(theme.field());
        return corner;
    }

    static final class CardPanel extends JPanel {
        private UiTheme theme;
        private final int arc;

        CardPanel(UiTheme theme, int arc) {
            this.theme = theme;
            this.arc = arc;
            setOpaque(false);
        }

        void setTheme(UiTheme theme) {
            this.theme = theme;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(theme.surface());
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);

            // Subtle top specular highlight echoing the sculpted ceramic/glass icon frame
            Color rim = theme.dark()
                    ? UiTheme.withAlpha(theme.accentGlow(), 22)
                    : UiTheme.withAlpha(Color.WHITE, 210);
            g2.setColor(rim);
            g2.drawLine(arc / 2, 1, Math.max(arc / 2, w - arc / 2 - 1), 1);

            g2.setColor(theme.cardBorder());
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    static final class BadgePill extends JLabel {
        private UiTheme theme;
        private boolean accentTint;

        BadgePill(String text, UiTheme theme, boolean accentTint) {
            super(text);
            this.theme = theme;
            this.accentTint = accentTint;
            setOpaque(false);
            setFocusable(false);
            setFont(getFont().deriveFont(Font.BOLD, 11f));
            setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
            applyColors();
        }

        void setTheme(UiTheme theme) {
            this.theme = theme;
            applyColors();
            repaint();
        }

        void setAccentTint(boolean accentTint) {
            this.accentTint = accentTint;
            applyColors();
            repaint();
        }

        private void applyColors() {
            setForeground(accentTint
                    ? (theme.dark() ? theme.accentGlow() : theme.accentStrong())
                    : theme.muted());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = h - 1;
            g2.setColor(accentTint ? theme.accentSoft() : theme.elevatedSurface());
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(accentTint ? theme.accentBorder() : theme.cardBorder());
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    static final class StatusDot extends JComponent {
        private Color color;

        StatusDot(Color color) {
            this.color = color;
            setFocusable(false);
            setPreferredSize(new Dimension(10, 10));
            setMinimumSize(new Dimension(10, 10));
            setMaximumSize(new Dimension(10, 10));
        }

        void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UiTheme.withAlpha(color, 65));
            g2.fillOval(0, 0, 10, 10);
            g2.setColor(color);
            g2.fillOval(2, 2, 6, 6);
            g2.dispose();
        }
    }

    static final class AmethystProgressBar extends JProgressBar {
        private UiTheme theme;

        AmethystProgressBar(UiTheme theme) {
            super(0, 100);
            this.theme = theme;
            setOpaque(false);
            setFocusable(false);
            setBorderPainted(false);
            setStringPainted(false);
            setPreferredSize(new Dimension(420, 8));
            setUI(new AmethystProgressUI());
        }

        void setTheme(UiTheme theme) {
            this.theme = theme;
            repaint();
        }

        private final class AmethystProgressUI extends BasicProgressBarUI {
            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = c.getWidth();
                int h = c.getHeight();
                int arc = h;

                g2.setColor(theme.field());
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                g2.setColor(theme.fieldBorder());
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                double fraction = getPercentComplete();
                if (isIndeterminate()) {
                    int barW = Math.max(24, w / 3);
                    long phase = (System.currentTimeMillis() / 16L) % (w + barW);
                    int x = (int) phase - barW;
                    int clampedX = Math.max(0, x);
                    int clampedW = Math.min(w - clampedX, barW - (clampedX - x));
                    if (clampedW > 2) {
                        g2.setPaint(new GradientPaint(clampedX, 0, theme.accentStrong(),
                                clampedX + clampedW, 0, theme.accentGlow()));
                        g2.fillRoundRect(clampedX, 0, clampedW, h, arc, arc);
                    }
                } else if (fraction > 0.0) {
                    int fillW = Math.max(h, (int) Math.round(w * Math.min(1.0, fraction)));
                    g2.setPaint(new GradientPaint(0, 0, theme.accentStrong(), fillW, 0, theme.accent()));
                    g2.fillRoundRect(0, 0, fillW, h, arc, arc);
                    if (fillW > 8) {
                        g2.setColor(UiTheme.withAlpha(theme.accentGlow(), 110));
                        g2.drawLine(3, 1, fillW - 4, 1);
                    }
                }
                g2.dispose();
            }
        }
    }

    static final class PlaceholderTextField extends JTextField {
        private final String placeholder;
        private UiTheme theme;
        private boolean focused;

        PlaceholderTextField(String placeholder, UiTheme theme) {
            this.placeholder = placeholder;
            this.theme = theme;
            setOpaque(false);
            setFont(getFont().deriveFont(Font.PLAIN, 13f));
            setBorder(BorderFactory.createEmptyBorder(6, 11, 6, 11));
            installUndoSupport(this);
            applyColors();
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
        }

        void setTheme(UiTheme theme) {
            this.theme = theme;
            applyColors();
            repaint();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            applyColors();
            repaint();
        }

        private void applyColors() {
            if (theme == null) {
                return;
            }
            setForeground(isEnabled() ? theme.text() : UiTheme.withAlpha(theme.muted(), 120));
            setDisabledTextColor(UiTheme.withAlpha(theme.muted(), 120));
            setCaretColor(theme.text());
            setSelectionColor(theme.accent());
            setSelectedTextColor(theme.accentText());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = 10;
            boolean enabled = isEnabled();
            g2.setColor(enabled ? theme.field() : UiTheme.withAlpha(theme.field(), 135));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(!enabled
                    ? UiTheme.withAlpha(theme.fieldBorder(), 105)
                    : (focused ? theme.accent() : theme.fieldBorder()));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();

            super.paintComponent(graphics);

            if (getText().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
                Graphics2D pg = (Graphics2D) graphics.create();
                pg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                pg.setFont(getFont());
                pg.setColor(enabled ? theme.subtle() : UiTheme.withAlpha(theme.muted(), 95));
                Insets insets = getInsets();
                FontMetrics fm = pg.getFontMetrics();
                int y = (h - fm.getHeight()) / 2 + fm.getAscent();
                pg.drawString(placeholder, insets.left, y);
                pg.dispose();
            }
        }
    }

    static final class TogglePillButton extends JComponent implements Accessible {
        private final String label;
        private UiTheme theme;
        private boolean selected;
        private boolean hovering;
        private Consumer<Boolean> onToggle;

        TogglePillButton(String label, boolean initialSelected, UiTheme theme) {
            this.label = label;
            this.selected = initialSelected;
            this.theme = theme;
            setOpaque(false);
            setFocusable(true);
            setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 12.5f));
            UiTheme.syncInteractiveCursor(this);
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
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "toggle");
            getActionMap().put("toggle", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    triggerToggle();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (isEnabled()) {
                        requestFocusInWindow();
                    }
                    triggerToggle();
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    repaint();
                }
            });
        }

        private void triggerToggle() {
            if (!isEnabled()) {
                return;
            }
            selected = !selected;
            repaint();
            if (onToggle != null) {
                onToggle.accept(selected);
            }
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            setFocusable(enabled);
            UiTheme.syncInteractiveCursor(this);
            repaint();
        }

        void setOnToggle(Consumer<Boolean> onToggle) {
            this.onToggle = onToggle;
        }

        boolean isSelected() {
            return selected;
        }

        void setSelected(boolean selected) {
            if (this.selected != selected) {
                this.selected = selected;
                repaint();
            }
        }

        void setTheme(UiTheme theme) {
            this.theme = theme;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(label) + 36, 30);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 10;
            boolean enabled = isEnabled();

            Color fill;
            Color border;
            if (!enabled) {
                fill = selected
                        ? UiTheme.withAlpha(theme.elevatedSurface(), theme.dark() ? 165 : 190)
                        : UiTheme.withAlpha(theme.field(), 145);
                border = UiTheme.withAlpha(theme.fieldBorder(), 110);
            } else {
                fill = selected
                        ? theme.accentSoft()
                        : (hovering ? theme.elevatedSurface() : theme.field());
                border = selected || hovering ? theme.accentBorder() : theme.fieldBorder();
            }

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            if (isFocusOwner() && enabled) {
                g2.setColor(UiTheme.withAlpha(theme.accentGlow(), 195));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc - 2, arc - 2);
            }

            int dotSize = 8;
            int dotX = 11;
            int dotY = (h - dotSize) / 2;
            if (selected) {
                g2.setColor(enabled ? theme.accent() : UiTheme.withAlpha(theme.muted(), 115));
                g2.fillOval(dotX, dotY, dotSize, dotSize);
            } else {
                g2.setColor(enabled ? theme.subtle() : UiTheme.withAlpha(theme.muted(), 90));
                g2.drawOval(dotX, dotY, dotSize - 1, dotSize - 1);
            }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            Color textColor = !enabled
                    ? UiTheme.withAlpha(theme.muted(), 125)
                    : (selected ? (theme.dark() ? theme.accentGlow() : theme.accentStrong()) : theme.muted());
            g2.setColor(textColor);
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label, dotX + dotSize + 7, textY);
            g2.dispose();
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    @Override
                    public AccessibleRole getAccessibleRole() {
                        return AccessibleRole.TOGGLE_BUTTON;
                    }

                    @Override
                    public String getAccessibleName() {
                        return label;
                    }

                    @Override
                    public AccessibleStateSet getAccessibleStateSet() {
                        AccessibleStateSet states = super.getAccessibleStateSet();
                        if (selected) {
                            states.add(AccessibleState.CHECKED);
                        }
                        return states;
                    }
                };
            }
            return accessibleContext;
        }
    }

    static final class SegmentedPillGroup<T> extends JPanel {
        public record Option<T>(T value, String label) {
        }

        private final List<Option<T>> options;
        private final List<SegmentCell> cells = new ArrayList<>();
        private UiTheme theme;
        private T selectedValue;
        private Consumer<T> onSelect;

        SegmentedPillGroup(List<Option<T>> options, T initialValue, UiTheme theme) {
            super(new FlowLayout(FlowLayout.LEFT, 2, 2));
            this.options = List.copyOf(options);
            this.selectedValue = initialValue;
            this.theme = theme;
            setOpaque(false);
            setFocusable(false);
            setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            for (int idx = 0; idx < this.options.size(); idx++) {
                SegmentCell cell = new SegmentCell(this.options.get(idx), idx);
                cells.add(cell);
                add(cell);
            }
        }

        void setOnSelect(Consumer<T> onSelect) {
            this.onSelect = onSelect;
        }

        T selectedValue() {
            return selectedValue;
        }

        void setSelectedValue(T value) {
            if (!Objects.equals(this.selectedValue, value)) {
                this.selectedValue = value;
                repaint();
            }
        }

        void selectAndNotify(T value) {
            if (!Objects.equals(this.selectedValue, value)) {
                this.selectedValue = value;
                repaint();
                if (onSelect != null) {
                    onSelect.accept(selectedValue);
                }
            }
        }

        void setTheme(UiTheme theme) {
            this.theme = theme;
            for (SegmentCell cell : cells) {
                cell.repaint();
            }
            repaint();
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (SegmentCell cell : cells) {
                cell.setEnabled(enabled);
            }
            repaint();
        }

        @Override
        public void setToolTipText(String text) {
            super.setToolTipText(text);
            for (SegmentCell cell : cells) {
                cell.setToolTipText(text);
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            boolean enabled = isEnabled();
            g2.setColor(enabled ? theme.field() : UiTheme.withAlpha(theme.field(), 145));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.setColor(enabled ? theme.fieldBorder() : UiTheme.withAlpha(theme.fieldBorder(), 105));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
            g2.dispose();
            super.paintComponent(graphics);
        }

        private final class SegmentCell extends JComponent implements Accessible {
            private final Option<T> option;
            private final int cellIndex;
            private boolean hovering;

            private SegmentCell(Option<T> option, int cellIndex) {
                this.option = option;
                this.cellIndex = cellIndex;
                setOpaque(false);
                setFocusable(true);
                setFont(new JLabel().getFont().deriveFont(Font.BOLD, 12f));
                UiTheme.syncInteractiveCursor(this);
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
                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "select");
                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "select");
                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prev");
                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "next");
                getActionMap().put("select", new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        activateCell();
                    }
                });
                getActionMap().put("prev", new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        moveAdjacent(-1);
                    }
                });
                getActionMap().put("next", new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        moveAdjacent(1);
                    }
                });
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (isCellEnabled()) {
                            requestFocusInWindow();
                        }
                        activateCell();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovering = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovering = false;
                        repaint();
                    }
                });
            }

            private boolean isCellEnabled() {
                return SegmentedPillGroup.this.isEnabled() && isEnabled();
            }

            private void activateCell() {
                if (!isCellEnabled()) {
                    return;
                }
                selectAndNotify(option.value());
            }

            private void moveAdjacent(int delta) {
                if (!isCellEnabled() || cells.isEmpty()) {
                    return;
                }
                int nextIdx = (cellIndex + delta + cells.size()) % cells.size();
                SegmentCell target = cells.get(nextIdx);
                target.requestFocusInWindow();
                target.activateCell();
            }

            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                setFocusable(enabled && SegmentedPillGroup.this.isEnabled());
                UiTheme.syncInteractiveCursor(this);
                repaint();
            }

            @Override
            public Dimension getPreferredSize() {
                FontMetrics fm = getFontMetrics(getFont());
                return new Dimension(fm.stringWidth(option.label()) + 22, 24);
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                boolean active = Objects.equals(selectedValue, option.value());
                boolean groupEnabled = isCellEnabled();

                if (active) {
                    if (groupEnabled) {
                        g2.setPaint(new GradientPaint(0, 0, theme.accent(), 0, h, theme.accentStrong()));
                    } else {
                        g2.setColor(UiTheme.withAlpha(theme.elevatedSurface(), theme.dark() ? 195 : 220));
                    }
                    g2.fillRoundRect(0, 0, w, h, 8, 8);
                } else if (hovering && groupEnabled) {
                    g2.setColor(theme.elevatedSurface());
                    g2.fillRoundRect(0, 0, w, h, 8, 8);
                }

                if (isFocusOwner() && groupEnabled) {
                    g2.setColor(UiTheme.withAlpha(theme.accentGlow(), 210));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(1, 1, w - 3, h - 3, 7, 7);
                }

                g2.setFont(getFont().deriveFont(active ? Font.BOLD : Font.PLAIN, 12f));
                FontMetrics fm = g2.getFontMetrics();
                Color fg;
                if (!groupEnabled) {
                    fg = UiTheme.withAlpha(theme.muted(), active ? 135 : 95);
                } else if (active) {
                    fg = theme.accentText();
                } else {
                    fg = hovering ? theme.text() : theme.muted();
                }
                g2.setColor(fg);
                int tx = (w - fm.stringWidth(option.label())) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(option.label(), tx, ty);
                g2.dispose();
            }

            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJComponent() {
                        @Override
                        public AccessibleRole getAccessibleRole() {
                            return AccessibleRole.RADIO_BUTTON;
                        }

                        @Override
                        public String getAccessibleName() {
                            return option.label();
                        }

                        @Override
                        public AccessibleStateSet getAccessibleStateSet() {
                            AccessibleStateSet states = super.getAccessibleStateSet();
                            if (Objects.equals(selectedValue, option.value())) {
                                states.add(AccessibleState.CHECKED);
                                states.add(AccessibleState.SELECTED);
                            }
                            return states;
                        }
                    };
                }
                return accessibleContext;
            }
        }
    }

    private static final class BrandComboBoxUI extends BasicComboBoxUI {
        private final UiTheme theme;

        private BrandComboBoxUI(UiTheme theme) {
            this.theme = theme;
        }

        @Override
        protected JButton createArrowButton() {
            JButton arrow = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    int cx = w / 2;
                    int cy = h / 2;
                    Path2D chevron = new Path2D.Float();
                    chevron.moveTo(cx - 4.0f, cy - 1.8f);
                    chevron.lineTo(cx, cy + 2.4f);
                    chevron.lineTo(cx + 4.0f, cy - 1.8f);
                    g2.setColor(comboBox != null && comboBox.isEnabled()
                            ? theme.muted()
                            : UiTheme.withAlpha(theme.muted(), 100));
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(chevron);
                    g2.dispose();
                }
            };
            arrow.setOpaque(false);
            arrow.setFocusable(false);
            arrow.setContentAreaFilled(false);
            arrow.setBorderPainted(false);
            arrow.setFocusPainted(false);
            arrow.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
            return arrow;
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox) {
                @Override
                protected void configurePopup() {
                    super.configurePopup();
                    setBorder(BorderFactory.createLineBorder(theme.accentBorder(), 1));
                    setBackground(theme.field());
                }

                @Override
                protected void configureList() {
                    super.configureList();
                    list.setBackground(theme.field());
                    list.setForeground(theme.text());
                    list.setSelectionBackground(theme.accent());
                    list.setSelectionForeground(theme.accentText());
                }
            };
            popup.getAccessibleContext().setAccessibleParent(comboBox);
            return popup;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();
            int arc = 10;
            boolean enabled = c.isEnabled();
            hasFocus = comboBox.hasFocus();

            g2.setColor(enabled ? theme.field() : UiTheme.withAlpha(theme.field(), 145));
            g2.fillRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(!enabled
                    ? UiTheme.withAlpha(theme.fieldBorder(), 105)
                    : (hasFocus ? theme.accent() : theme.fieldBorder()));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.dispose();

            if (!comboBox.isEditable()) {
                Rectangle bounds = rectangleForCurrentValue();
                paintCurrentValue(g, bounds, hasFocus);
            }
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            var renderer = comboBox.getRenderer();
            JList<Object> renderList = listBox;
            @SuppressWarnings("unchecked")
            var c = renderer.getListCellRendererComponent(
                    renderList,
                    comboBox.getSelectedItem(),
                    -1,
                    false,
                    false
            );
            c.setFont(comboBox.getFont());
            if (c instanceof JComponent jc) {
                jc.setOpaque(false);
            }
            c.setForeground(comboBox.isEnabled() ? theme.text() : UiTheme.withAlpha(theme.muted(), 125));
            c.setBackground(theme.field());
            currentValuePane.paintComponent(g, c, comboBox, bounds.x, bounds.y, bounds.width, bounds.height, false);
        }
    }

    private static final class BrandScrollBarUI extends BasicScrollBarUI {
        private final UiTheme theme;

        private BrandScrollBarUI(UiTheme theme) {
            this.theme = theme;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton btn = new JButton();
            btn.setFocusable(false);
            btn.setPreferredSize(new Dimension(0, 0));
            btn.setMinimumSize(new Dimension(0, 0));
            btn.setMaximumSize(new Dimension(0, 0));
            return btn;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(theme.field());
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging || isThumbRollover()
                    ? UiTheme.withAlpha(theme.accent(), 185)
                    : UiTheme.withAlpha(theme.muted(), 110));
            g2.fillRoundRect(
                    thumbBounds.x + 2,
                    thumbBounds.y + 2,
                    Math.max(4, thumbBounds.width - 4),
                    Math.max(4, thumbBounds.height - 4),
                    6,
                    6
            );
            g2.dispose();
        }
    }
}
