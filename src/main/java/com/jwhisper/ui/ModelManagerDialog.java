package com.jwhisper.ui;

import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.model.ModelDownloadListener;
import com.jwhisper.model.ModelDownloadService;
import com.jwhisper.model.ModelDownloadState;
import com.jwhisper.model.ModelDownloadStatus;
import com.jwhisper.model.ModelManagerAgent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public final class ModelManagerDialog extends JDialog {
    private final ModelManagerAgent modelManagerAgent;
    private final ModelDownloadService downloadService;
    private final UiTheme theme;
    private final Runnable onModelsChanged;
    private final Consumer<ModelDescriptor> beforeDeleteModel;
    private final ModelDownloadListener downloadListener;

    private final DefaultListModel<ModelDescriptor> installedListModel = new DefaultListModel<>();
    private final JList<ModelDescriptor> installedList = new JList<>(installedListModel);
    private final JComboBox<ModelDescriptor> availableCombo = new JComboBox<>();

    private final ThemedControls.StatusDot statusDot;
    private final JLabel statusLabel = new JLabel("Ready.");
    private final ThemedControls.AmethystProgressBar progressBar;

    private final JButton downloadButton = new JButton("Download model");
    private final JButton cancelDownloadButton = new JButton("Cancel download");
    private final JButton defaultButton = new JButton("Set default");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton closeButton = new JButton("Close");
    private String defaultModelId;
    private boolean statusStickyResult;

    public ModelManagerDialog(
            Frame owner,
            ModelManagerAgent modelManagerAgent,
            ModelDownloadService downloadService,
            UiTheme theme,
            Runnable onModelsChanged
    ) {
        this(owner, modelManagerAgent, downloadService, theme, onModelsChanged, descriptor -> {});
    }

    public ModelManagerDialog(
            Frame owner,
            ModelManagerAgent modelManagerAgent,
            ModelDownloadService downloadService,
            UiTheme theme,
            Runnable onModelsChanged,
            Consumer<ModelDescriptor> beforeDeleteModel
    ) {
        super(owner, "Whisper Models — JWhisper", true);
        this.modelManagerAgent = modelManagerAgent;
        this.downloadService = downloadService;
        this.theme = theme;
        this.onModelsChanged = onModelsChanged;
        this.beforeDeleteModel = beforeDeleteModel == null ? (descriptor -> {}) : beforeDeleteModel;
        this.statusDot = new ThemedControls.StatusDot(theme.success());
        this.progressBar = new ThemedControls.AmethystProgressBar(theme);
        this.downloadListener = state -> SwingUtilities.invokeLater(() -> handleDownloadUpdate(state));

        AppIcons.applyToWindow(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        installKeyboardShortcuts();
        refreshModels();
        selectFirstUninstalledCatalogModel();
        updateDownloadProgress();
        updateButtons();

        pack();
        setMinimumSize(new Dimension(720, 480));
        setSize(new Dimension(750, 505));
        setLocationRelativeTo(owner);
        downloadService.addListener(downloadListener);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(theme.background());
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        ImageIcon icon = AppIcons.hiDpiIcon(36);
        if (icon != null) {
            header.add(new JLabel(icon), BorderLayout.WEST);
        }
        JPanel titleStack = new JPanel(new BorderLayout(0, 2));
        titleStack.setOpaque(false);
        JLabel title = new JLabel("Whisper Model Store");
        title.setForeground(theme.text());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17.5f));
        JLabel subtitle = new JLabel("Revision-pinned local Whisper ONNX models (~/.jwhisper/models)");
        subtitle.setForeground(theme.muted());
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        titleStack.add(title, BorderLayout.NORTH);
        titleStack.add(subtitle, BorderLayout.SOUTH);
        header.add(titleStack, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        ThemedControls.CardPanel card = new ThemedControls.CardPanel(theme, 14);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));

        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedList.setCellRenderer(new InstalledModelRenderer());
        installedList.setForeground(theme.text());
        installedList.setBackground(theme.field());
        installedList.setFixedCellHeight(54);
        installedList.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        installedList.addListSelectionListener(event -> updateButtons());
        installedList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    ModelDescriptor sel = installedList.getSelectedValue();
                    if (sel != null && !sel.id().equals(defaultModelId)) {
                        setDefaultModel();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(installedList);
        scrollPane.setPreferredSize(new Dimension(600, 196));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ThemedControls.styleScrollPane(scrollPane, theme);

        DefaultComboBoxModel<ModelDescriptor> availableModel = new DefaultComboBoxModel<>();
        modelManagerAgent.availableModels().forEach(availableModel::addElement);
        availableCombo.setModel(availableModel);
        availableCombo.setRenderer(new CatalogComboRenderer());
        availableCombo.setPreferredSize(new Dimension(380, 34));
        ThemedControls.styleComboBox(availableCombo, theme);
        availableCombo.addActionListener(event -> updateButtons());

        defaultButton.setPreferredSize(new Dimension(108, 28));
        deleteButton.setPreferredSize(new Dimension(86, 28));
        downloadButton.setPreferredSize(new Dimension(150, 34));
        cancelDownloadButton.setPreferredSize(new Dimension(142, 34));
        cancelDownloadButton.setVisible(false);
        closeButton.setPreferredSize(new Dimension(96, 33));

        theme.styleButton(defaultButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(deleteButton, UiTheme.ButtonRole.DANGER);
        theme.styleButton(downloadButton, UiTheme.ButtonRole.PRIMARY);
        theme.styleButton(cancelDownloadButton, UiTheme.ButtonRole.DANGER);
        theme.styleButton(closeButton, UiTheme.ButtonRole.NORMAL);

        // Installed header row: label on left, contextual Set default / Delete actions on right
        JPanel installedHeader = new JPanel(new BorderLayout(12, 0));
        installedHeader.setOpaque(false);
        JLabel installedLabel = new JLabel("Installed models");
        installedLabel.setForeground(theme.muted());
        installedLabel.setFont(installedLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        installedLabel.setLabelFor(installedList);
        installedHeader.add(installedLabel, BorderLayout.WEST);

        JPanel installedActions = new JPanel();
        installedActions.setOpaque(false);
        installedActions.setLayout(new BoxLayout(installedActions, BoxLayout.X_AXIS));
        installedActions.add(defaultButton);
        installedActions.add(Box.createHorizontalStrut(8));
        installedActions.add(deleteButton);
        installedHeader.add(installedActions, BorderLayout.EAST);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 8, 0);
        card.add(installedHeader, constraints);

        constraints.gridy = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 14, 0);
        card.add(scrollPane, constraints);

        constraints.gridy = 2;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 6, 0);
        JLabel catalogLabel = new JLabel("Download from catalog");
        catalogLabel.setForeground(theme.muted());
        catalogLabel.setFont(catalogLabel.getFont().deriveFont(Font.BOLD, 12.5f));
        catalogLabel.setLabelFor(availableCombo);
        card.add(catalogLabel, constraints);

        JPanel downloadRow = new JPanel(new GridBagLayout());
        downloadRow.setOpaque(false);
        GridBagConstraints comboConstraints = new GridBagConstraints();
        comboConstraints.gridx = 0;
        comboConstraints.gridy = 0;
        comboConstraints.weightx = 1;
        comboConstraints.fill = GridBagConstraints.HORIZONTAL;
        comboConstraints.insets = new Insets(0, 0, 0, 10);
        downloadRow.add(availableCombo, comboConstraints);

        comboConstraints.gridx = 1;
        comboConstraints.weightx = 0;
        comboConstraints.fill = GridBagConstraints.NONE;
        comboConstraints.insets = new Insets(0, 0, 0, 0);
        downloadRow.add(downloadButton, comboConstraints);

        comboConstraints.gridx = 2;
        comboConstraints.insets = new Insets(0, 8, 0, 0);
        downloadRow.add(cancelDownloadButton, comboConstraints);

        constraints.gridy = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 0, 0);
        card.add(downloadRow, constraints);
        root.add(card, BorderLayout.CENTER);

        // Footer: flush-left status + progress, flush-right Close button
        progressBar.setVisible(false);
        JPanel footer = new JPanel(new BorderLayout(14, 0));
        footer.setOpaque(false);

        JPanel statusStack = new JPanel(new BorderLayout(0, 6));
        statusStack.setOpaque(false);

        JPanel statusLine = new JPanel();
        statusLine.setOpaque(false);
        statusLine.setLayout(new BoxLayout(statusLine, BoxLayout.X_AXIS));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setForeground(theme.muted());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12.5f));
        statusLine.add(statusDot);
        statusLine.add(Box.createHorizontalStrut(8));
        statusLine.add(statusLabel);

        statusStack.add(statusLine, BorderLayout.CENTER);
        statusStack.add(progressBar, BorderLayout.SOUTH);
        footer.add(statusStack, BorderLayout.CENTER);

        JPanel closeWrap = new JPanel(new BorderLayout());
        closeWrap.setOpaque(false);
        closeWrap.add(closeButton, BorderLayout.EAST);
        footer.add(closeWrap, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        downloadButton.addActionListener(event -> downloadSelectedModel());
        cancelDownloadButton.addActionListener(event -> cancelActiveDownload());
        deleteButton.addActionListener(event -> deleteSelectedModel());
        defaultButton.addActionListener(event -> setDefaultModel());
        closeButton.addActionListener(event -> dispose());
        return root;
    }

    private void installKeyboardShortcuts() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeDialog");
        getRootPane().getActionMap().put("closeDialog", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void handleDownloadUpdate(ModelDownloadState state) {
        if (state.status() == ModelDownloadStatus.SUCCEEDED) {
            statusStickyResult = true;
            setStatus("Installed " + state.descriptor().displayName() + ".", theme.success());
            refreshModels();
            selectInstalledById(state.descriptor().id());
            selectFirstUninstalledCatalogModel();
        } else if (state.status() == ModelDownloadStatus.FAILED) {
            statusStickyResult = true;
            String err = state.errorMessage() == null ? "Download failed." : state.errorMessage();
            setStatus(state.descriptor().displayName() + ": " + err, theme.warning());
            refreshModels();
        } else if (state.status() == ModelDownloadStatus.CANCELED) {
            statusStickyResult = true;
            setStatus("Canceled " + state.descriptor().displayName() + " download.", theme.warning());
            refreshModels();
        } else {
            statusStickyResult = false;
            updateDownloadProgress();
            updateButtons();
            installedList.repaint();
            availableCombo.repaint();
        }
        onModelsChanged.run();
    }

    private void refreshModels() {
        String selectedId = installedList.getSelectedValue() == null ? null : installedList.getSelectedValue().id();
        defaultModelId = modelManagerAgent.defaultModelId().orElse(null);
        installedListModel.clear();
        List<ModelDescriptor> installed = modelManagerAgent.installedModels();
        installed.forEach(installedListModel::addElement);
        if (!installed.isEmpty()) {
            installedList.setSelectedIndex(selectedInstalledIndex(installed, selectedId));
        }
        if (installed.isEmpty() && downloadService.activeDownloads().isEmpty() && !statusStickyResult) {
            setStatus("No models installed yet — choose a model below to download.", theme.warning());
        } else if (!installed.isEmpty() && downloadService.activeDownloads().isEmpty() && !statusStickyResult) {
            setStatus(installed.size() + (installed.size() == 1 ? " model installed · Ready." : " models installed · Ready."),
                    theme.success());
        }
        updateDownloadProgress();
        updateButtons();
        availableCombo.repaint();
    }

    private void selectFirstUninstalledCatalogModel() {
        for (int i = 0; i < availableCombo.getItemCount(); i++) {
            ModelDescriptor candidate = availableCombo.getItemAt(i);
            if (candidate != null && !modelManagerAgent.isInstalled(candidate)) {
                availableCombo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectInstalledById(String modelId) {
        for (int i = 0; i < installedListModel.size(); i++) {
            if (installedListModel.get(i).id().equals(modelId)) {
                installedList.setSelectedIndex(i);
                installedList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private int selectedInstalledIndex(List<ModelDescriptor> installed, String selectedId) {
        if (selectedId != null) {
            for (int i = 0; i < installed.size(); i++) {
                if (installed.get(i).id().equals(selectedId)) {
                    return i;
                }
            }
        }
        if (defaultModelId != null) {
            for (int i = 0; i < installed.size(); i++) {
                if (installed.get(i).id().equals(defaultModelId)) {
                    return i;
                }
            }
        }
        return 0;
    }

    private void downloadSelectedModel() {
        ModelDescriptor descriptor = (ModelDescriptor) availableCombo.getSelectedItem();
        if (descriptor == null) {
            return;
        }
        if (modelManagerAgent.isInstalled(descriptor)) {
            statusStickyResult = true;
            setStatus(descriptor.displayName() + " is already installed.", theme.muted());
            return;
        }
        statusStickyResult = false;
        downloadService.startDownload(descriptor);
        updateDownloadProgress();
        updateButtons();
    }

    private void cancelActiveDownload() {
        List<ModelDownloadState> active = downloadService.activeDownloads();
        if (active.isEmpty()) {
            return;
        }
        for (ModelDownloadState state : active) {
            downloadService.cancelDownload(state.descriptor());
        }
        updateDownloadProgress();
        updateButtons();
    }

    private void deleteSelectedModel() {
        ModelDescriptor descriptor = installedList.getSelectedValue();
        if (descriptor == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Delete " + descriptor.displayName() + " (" + descriptor.formattedSize() + ") from disk?",
                "JWhisper",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                AppIcons.hiDpiIcon(48)
        );
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            beforeDeleteModel.accept(descriptor);
            modelManagerAgent.deleteModel(descriptor);
            statusStickyResult = true;
            setStatus("Deleted " + descriptor.displayName() + ".", theme.muted());
            refreshModels();
            selectFirstUninstalledCatalogModel();
            onModelsChanged.run();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not delete model. Try again.", "JWhisper",
                    JOptionPane.ERROR_MESSAGE, AppIcons.hiDpiIcon(48));
        }
    }

    private void setDefaultModel() {
        ModelDescriptor descriptor = installedList.getSelectedValue();
        if (descriptor == null || descriptor.id().equals(defaultModelId)) {
            return;
        }
        try {
            modelManagerAgent.setDefaultModel(descriptor);
            statusStickyResult = true;
            setStatus("Default model set to " + descriptor.displayName() + ".", theme.success());
            refreshModels();
            onModelsChanged.run();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not update default model. Try again.", "JWhisper",
                    JOptionPane.ERROR_MESSAGE, AppIcons.hiDpiIcon(48));
        }
    }

    private void updateDownloadProgress() {
        List<ModelDownloadState> active = downloadService.activeDownloads();
        if (active.isEmpty()) {
            progressBar.setVisible(false);
            cancelDownloadButton.setVisible(false);
            return;
        }
        ModelDownloadState state = active.get(0);
        progressBar.setVisible(true);
        progressBar.setValue(state.percent());
        cancelDownloadButton.setVisible(true);
        setStatus("Downloading " + state.descriptor().displayName() + " (" + state.percent() + "%)  ·  " + state.message(),
                theme.accent());
    }

    private void updateButtons() {
        ModelDescriptor selectedInstalledItem = installedList.getSelectedValue();
        boolean hasSelection = selectedInstalledItem != null;
        boolean isAlreadyDefault = hasSelection && selectedInstalledItem.id().equals(defaultModelId);

        ModelDescriptor selectedDownload = (ModelDescriptor) availableCombo.getSelectedItem();
        boolean selectedInstalled = selectedDownload != null && modelManagerAgent.isInstalled(selectedDownload);
        boolean selectedDownloading = selectedDownload != null && downloadService.isDownloading(selectedDownload);
        boolean anyDownloading = !downloadService.activeDownloads().isEmpty();

        downloadButton.setEnabled(selectedDownload != null && !selectedInstalled && !selectedDownloading);
        cancelDownloadButton.setVisible(anyDownloading);
        cancelDownloadButton.setEnabled(anyDownloading);
        availableCombo.setEnabled(true);
        deleteButton.setEnabled(hasSelection);
        defaultButton.setEnabled(hasSelection && !isAlreadyDefault);
    }

    private void setStatus(String text, Color dotColor) {
        statusLabel.setText(text);
        if (dotColor != null) {
            statusDot.setColor(dotColor);
        }
    }

    @Override
    public void dispose() {
        downloadService.removeListener(downloadListener);
        super.dispose();
    }

    /**
     * Two-line sculpted row renderer for installed models so model name, DEFAULT badge,
     * tabular size, language scope, and description never clip horizontally.
     */
    private final class InstalledModelRenderer implements ListCellRenderer<ModelDescriptor> {
        private final JPanel rowPanel = new JPanel(new BorderLayout(10, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (Boolean.TRUE.equals(getClientProperty("jwhisper.selected"))) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth();
                    int h = getHeight();
                    g2.setColor(theme.accentSoft());
                    g2.fillRoundRect(2, 2, w - 4, h - 4, 10, 10);
                    g2.setColor(theme.accentBorder());
                    g2.drawRoundRect(2, 2, w - 4, h - 4, 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        private final JLabel nameLabel = new JLabel();
        private final ThemedControls.BadgePill defaultPill = new ThemedControls.BadgePill("DEFAULT", theme, true);
        private final JLabel sizeLabel = new JLabel();
        private final JLabel metaLabel = new JLabel();

        private InstalledModelRenderer() {
            rowPanel.setOpaque(false);
            rowPanel.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));

            JPanel topRow = new JPanel(new BorderLayout(8, 0));
            topRow.setOpaque(false);

            JPanel titleGroup = new JPanel();
            titleGroup.setOpaque(false);
            titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.X_AXIS));
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 13.5f));
            defaultPill.setFont(defaultPill.getFont().deriveFont(Font.BOLD, 10f));
            defaultPill.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
            titleGroup.add(nameLabel);
            titleGroup.add(Box.createHorizontalStrut(8));
            titleGroup.add(defaultPill);

            sizeLabel.setFont(sizeLabel.getFont().deriveFont(Font.BOLD, 12f));
            sizeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            topRow.add(titleGroup, BorderLayout.WEST);
            topRow.add(sizeLabel, BorderLayout.EAST);

            metaLabel.setFont(metaLabel.getFont().deriveFont(Font.PLAIN, 11.5f));

            rowPanel.add(topRow, BorderLayout.NORTH);
            rowPanel.add(metaLabel, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ModelDescriptor> list,
                ModelDescriptor descriptor,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            rowPanel.putClientProperty("jwhisper.selected", isSelected);
            if (descriptor != null) {
                boolean isDefault = descriptor.id().equals(defaultModelId);
                String scope = descriptor.isMultilingual() ? "Multilingual" : "English-only";
                nameLabel.setText(descriptor.displayName());
                defaultPill.setVisible(isDefault);
                sizeLabel.setText(descriptor.formattedSize());
                metaLabel.setText(scope + "  ·  " + descriptor.description());
                rowPanel.setToolTipText(descriptor.displayName() + " — " + descriptor.provenance());
            }
            nameLabel.setForeground(theme.text());
            sizeLabel.setForeground(isSelected
                    ? (theme.dark() ? theme.accentGlow() : theme.accentStrong())
                    : theme.muted());
            metaLabel.setForeground(theme.muted());
            return rowPanel;
        }
    }

    /**
     * Concise single-line renderer for the catalog combo box so items never clip horizontally.
     */
    private final class CatalogComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ModelDescriptor descriptor) {
                String scope = descriptor.isMultilingual() ? "Multilingual" : "English";
                String stateSuffix = downloadService.stateFor(descriptor)
                        .filter(ModelDownloadState::isActive)
                        .map(s -> "  ·  Downloading " + s.percent() + "%")
                        .orElseGet(() -> modelManagerAgent.isInstalled(descriptor) ? "  ·  Installed" : "");
                String text = descriptor.displayName() + "  ·  " + scope + " (" + descriptor.formattedSize() + ")" + stateSuffix;
                label.setText(text);
                label.setToolTipText(descriptor.displayName() + " — " + descriptor.description());

                boolean installed = modelManagerAgent.isInstalled(descriptor);
                if (!isSelected) {
                    label.setForeground(installed && index >= 0 ? theme.subtle() : theme.text());
                    label.setBackground(theme.field());
                } else {
                    label.setForeground(theme.accentText());
                    label.setBackground(theme.accent());
                }
            }
            label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            return label;
        }
    }
}
