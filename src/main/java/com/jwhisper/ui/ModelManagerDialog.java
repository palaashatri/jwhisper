package com.jwhisper.ui;

import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.model.ModelDownloadListener;
import com.jwhisper.model.ModelDownloadService;
import com.jwhisper.model.ModelDownloadState;
import com.jwhisper.model.ModelManagerAgent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

public final class ModelManagerDialog extends JDialog {
    private final ModelManagerAgent modelManagerAgent;
    private final ModelDownloadService downloadService;
    private final UiTheme theme;
    private final Runnable onModelsChanged;
    private final ModelDownloadListener downloadListener;
    private final DefaultListModel<ModelDescriptor> installedListModel = new DefaultListModel<>();
    private final JList<ModelDescriptor> installedList = new JList<>(installedListModel);
    private final JComboBox<ModelDescriptor> availableCombo = new JComboBox<>();
    private final ThemedControls.AmethystProgressBar progressBar;
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JButton downloadButton = new JButton("Download model");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton defaultButton = new JButton("Set default");
    private final JButton closeButton = new JButton("Close");
    private String defaultModelId;

    public ModelManagerDialog(
            Frame owner,
            ModelManagerAgent modelManagerAgent,
            ModelDownloadService downloadService,
            UiTheme theme,
            Runnable onModelsChanged
    ) {
        super(owner, "Whisper Models — JWhisper", true);
        this.modelManagerAgent = modelManagerAgent;
        this.downloadService = downloadService;
        this.theme = theme;
        this.onModelsChanged = onModelsChanged;
        this.progressBar = new ThemedControls.AmethystProgressBar(theme);
        this.downloadListener = state -> SwingUtilities.invokeLater(() -> {
            updateDownloadProgress();
            refreshModels();
            this.onModelsChanged.run();
        });
        AppIcons.applyToWindow(this);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        refreshModels();
        pack();
        setMinimumSize(new Dimension(700, 450));
        setSize(new Dimension(730, 475));
        setLocationRelativeTo(owner);
        downloadService.addListener(downloadListener);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBackground(theme.background());
        root.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        ImageIcon icon = AppIcons.hiDpiIcon(32);
        if (icon != null) {
            header.add(new JLabel(icon), BorderLayout.WEST);
        }
        JPanel titleStack = new JPanel(new BorderLayout(0, 2));
        titleStack.setOpaque(false);
        JLabel title = new JLabel("Whisper Model Store");
        title.setForeground(theme.text());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        JLabel subtitle = new JLabel("Revision-pinned, SHA-256 verified local ONNX models (~/.jwhisper/models)");
        subtitle.setForeground(theme.muted());
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        titleStack.add(title, BorderLayout.NORTH);
        titleStack.add(subtitle, BorderLayout.SOUTH);
        header.add(titleStack, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        ThemedControls.CardPanel card = new ThemedControls.CardPanel(theme, 14);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedList.setCellRenderer(new ModelRenderer(true));
        installedList.setForeground(theme.text());
        installedList.setBackground(theme.field());
        installedList.setFixedCellHeight(36);
        installedList.addListSelectionListener(event -> updateButtons());

        JScrollPane scrollPane = new JScrollPane(installedList);
        scrollPane.setPreferredSize(new Dimension(560, 195));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ThemedControls.styleScrollPane(scrollPane, theme);

        DefaultComboBoxModel<ModelDescriptor> availableModel = new DefaultComboBoxModel<>();
        modelManagerAgent.availableModels().forEach(availableModel::addElement);
        availableCombo.setModel(availableModel);
        availableCombo.setRenderer(new ModelRenderer(false));
        availableCombo.setPreferredSize(new Dimension(380, 34));
        ThemedControls.styleComboBox(availableCombo, theme);
        availableCombo.addActionListener(event -> updateButtons());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 6, 0);
        JLabel installedLabel = new JLabel("Installed models");
        installedLabel.setForeground(theme.muted());
        installedLabel.setFont(installedLabel.getFont().deriveFont(Font.BOLD, 12f));
        card.add(installedLabel, constraints);

        constraints.gridy = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(0, 0, 14, 0);
        card.add(scrollPane, constraints);

        constraints.gridy = 2;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(0, 0, 6, 0);
        JLabel catalogLabel = new JLabel("Download from catalog");
        catalogLabel.setForeground(theme.muted());
        catalogLabel.setFont(catalogLabel.getFont().deriveFont(Font.BOLD, 12f));
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
        comboConstraints.insets = new Insets(0, 0, 0, 0);
        downloadButton.setPreferredSize(new Dimension(150, 34));
        downloadRow.add(downloadButton, comboConstraints);

        constraints.gridy = 3;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 0, 0);
        card.add(downloadRow, constraints);
        root.add(card, BorderLayout.CENTER);

        progressBar.setVisible(false);
        JPanel actions = new JPanel(new BorderLayout(10, 10));
        actions.setOpaque(false);
        JPanel progressRow = new JPanel(new BorderLayout(0, 6));
        progressRow.setOpaque(false);
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setForeground(theme.muted());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12.5f));
        progressRow.add(statusLabel, BorderLayout.NORTH);
        progressRow.add(progressBar, BorderLayout.SOUTH);
        actions.add(progressRow, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonRow.setOpaque(false);
        defaultButton.setPreferredSize(new Dimension(112, 32));
        deleteButton.setPreferredSize(new Dimension(92, 32));
        closeButton.setPreferredSize(new Dimension(92, 32));
        theme.styleButton(defaultButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(deleteButton, UiTheme.ButtonRole.DANGER);
        theme.styleButton(closeButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(downloadButton, UiTheme.ButtonRole.PRIMARY);
        buttonRow.add(defaultButton);
        buttonRow.add(deleteButton);
        buttonRow.add(Box.createHorizontalStrut(8));
        buttonRow.add(closeButton);
        actions.add(buttonRow, BorderLayout.SOUTH);
        root.add(actions, BorderLayout.SOUTH);

        downloadButton.addActionListener(event -> downloadSelectedModel());
        deleteButton.addActionListener(event -> deleteSelectedModel());
        defaultButton.addActionListener(event -> setDefaultModel());
        closeButton.addActionListener(event -> dispose());
        return root;
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
        updateDownloadProgress();
        updateButtons();
    }

    private int selectedInstalledIndex(List<ModelDescriptor> installed, String selectedId) {
        if (selectedId != null) {
            for (int i = 0; i < installed.size(); i++) {
                if (installed.get(i).id().equals(selectedId)) {
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
            statusLabel.setText("Model already installed.");
            return;
        }
        downloadService.startDownload(descriptor);
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
                "Delete " + descriptor.displayName() + "?",
                "JWhisper",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                AppIcons.hiDpiIcon(48)
        );
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            modelManagerAgent.deleteModel(descriptor);
            refreshModels();
            onModelsChanged.run();
            statusLabel.setText("Deleted " + descriptor.displayName() + ".");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not delete model. Try again.", "JWhisper", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setDefaultModel() {
        ModelDescriptor descriptor = installedList.getSelectedValue();
        if (descriptor == null) {
            return;
        }
        try {
            modelManagerAgent.setDefaultModel(descriptor);
            refreshModels();
            onModelsChanged.run();
            statusLabel.setText("Default model set to " + descriptor.displayName() + ".");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not update default model. Try again.", "JWhisper", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateDownloadProgress() {
        List<ModelDownloadState> active = downloadService.activeDownloads();
        if (active.isEmpty()) {
            progressBar.setVisible(false);
            if (statusLabel.getText().startsWith("Downloading") || statusLabel.getText().contains(": Downloading")) {
                statusLabel.setText("Ready.");
            }
            return;
        }
        ModelDownloadState state = active.get(0);
        progressBar.setVisible(true);
        progressBar.setValue(state.percent());
        statusLabel.setText(state.descriptor().displayName() + " (" + state.percent() + "%) — " + state.message());
    }

    private void updateButtons() {
        boolean hasSelection = installedList.getSelectedValue() != null;
        ModelDescriptor selectedDownload = (ModelDescriptor) availableCombo.getSelectedItem();
        boolean selectedInstalled = selectedDownload != null && modelManagerAgent.isInstalled(selectedDownload);
        boolean selectedDownloading = selectedDownload != null && downloadService.isDownloading(selectedDownload);
        downloadButton.setEnabled(selectedDownload != null && !selectedInstalled && !selectedDownloading);
        availableCombo.setEnabled(true);
        deleteButton.setEnabled(hasSelection);
        defaultButton.setEnabled(hasSelection);
    }

    @Override
    public void dispose() {
        downloadService.removeListener(downloadListener);
        super.dispose();
    }

    private final class ModelRenderer extends DefaultListCellRenderer {
        private final boolean installedView;

        private ModelRenderer(boolean installedView) {
            this.installedView = installedView;
        }

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
                StringBuilder badge = new StringBuilder();
                if (descriptor.id().equals(defaultModelId)) {
                    badge.append(" [default]");
                } else if (!installedView && modelManagerAgent.isInstalled(descriptor)) {
                    badge.append(" [installed]");
                }
                badge.append(downloadService.stateFor(descriptor)
                        .filter(ModelDownloadState::isActive)
                        .map(state -> " [downloading " + state.percent() + "%]")
                        .orElse(""));
                String fullText = descriptor.displayName() + badge + "  ·  " + scope
                        + " (" + descriptor.formattedSize() + ") — " + descriptor.description();
                label.setText(fullText);
                label.setToolTipText(fullText + "  [" + descriptor.provenance() + "]");
            }
            if (!isSelected) {
                label.setForeground(theme.text());
                label.setBackground(theme.field());
            } else {
                label.setForeground(theme.accentText());
                label.setBackground(theme.accent());
            }
            label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            return label;
        }
    }
}
