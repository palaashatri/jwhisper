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
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
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
    private final JProgressBar progressBar = new JProgressBar(0, 100);
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
        super(owner, "Manage models", true);
        this.modelManagerAgent = modelManagerAgent;
        this.downloadService = downloadService;
        this.theme = theme;
        this.onModelsChanged = onModelsChanged;
        this.downloadListener = state -> javax.swing.SwingUtilities.invokeLater(() -> {
            updateDownloadProgress();
            refreshModels();
            this.onModelsChanged.run();
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        refreshModels();
        pack();
        setMinimumSize(new Dimension(620, 420));
        setLocationRelativeTo(owner);
        downloadService.addListener(downloadListener);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBackground(theme.background());
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        JLabel title = new JLabel("Models");
        title.setForeground(theme.text());
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 19f));
        root.add(title, BorderLayout.NORTH);

        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedList.setCellRenderer(new ModelRenderer());
        installedList.setForeground(theme.text());
        installedList.setBackground(theme.field());
        installedList.setFixedCellHeight(30);
        installedList.addListSelectionListener(event -> updateButtons());
        JScrollPane scrollPane = new JScrollPane(installedList);
        scrollPane.setPreferredSize(new Dimension(520, 180));
        scrollPane.setBorder(BorderFactory.createLineBorder(theme.fieldBorder()));
        scrollPane.getViewport().setBackground(theme.field());

        DefaultComboBoxModel<ModelDescriptor> availableModel = new DefaultComboBoxModel<>();
        modelManagerAgent.availableModels().forEach(availableModel::addElement);
        availableCombo.setModel(availableModel);
        availableCombo.setRenderer(new ModelRenderer());
        availableCombo.setForeground(theme.text());
        availableCombo.setBackground(theme.field());
        availableCombo.setFont(availableCombo.getFont().deriveFont(Font.PLAIN, 14f));
        availableCombo.addActionListener(event -> updateButtons());

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        center.add(scrollPane, constraints);

        JPanel downloadRow = new JPanel(new GridBagLayout());
        downloadRow.setOpaque(false);
        GridBagConstraints comboConstraints = new GridBagConstraints();
        comboConstraints.gridx = 0;
        comboConstraints.gridy = 0;
        comboConstraints.weightx = 1;
        comboConstraints.fill = GridBagConstraints.HORIZONTAL;
        comboConstraints.insets = new Insets(0, 0, 0, 8);
        downloadRow.add(availableCombo, comboConstraints);
        comboConstraints.gridx = 1;
        comboConstraints.weightx = 0;
        comboConstraints.insets = new Insets(0, 0, 0, 0);
        downloadRow.add(downloadButton, comboConstraints);

        constraints.gridy = 1;
        constraints.weighty = 0;
        constraints.insets = new Insets(16, 0, 0, 0);
        center.add(downloadRow, constraints);
        root.add(center, BorderLayout.CENTER);

        progressBar.setStringPainted(false);
        progressBar.setVisible(false);
        JPanel actions = new JPanel(new BorderLayout(12, 12));
        actions.setOpaque(false);
        JPanel progressRow = new JPanel(new BorderLayout(12, 0));
        progressRow.setOpaque(false);
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        statusLabel.setForeground(theme.muted());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 13f));
        progressRow.add(statusLabel, BorderLayout.CENTER);
        progressRow.add(progressBar, BorderLayout.SOUTH);
        actions.add(progressRow, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonRow.setOpaque(false);
        theme.styleButton(defaultButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(deleteButton, UiTheme.ButtonRole.DANGER);
        theme.styleButton(closeButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(downloadButton, UiTheme.ButtonRole.PRIMARY);
        buttonRow.add(defaultButton);
        buttonRow.add(deleteButton);
        buttonRow.add(Box.createHorizontalStrut(10));
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
                "jwhisper",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            modelManagerAgent.deleteModel(descriptor);
            refreshModels();
            onModelsChanged.run();
            statusLabel.setText("Model deleted.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Something went wrong. Try again.", "jwhisper", JOptionPane.ERROR_MESSAGE);
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
            statusLabel.setText("Default model set.");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Something went wrong. Try again.", "jwhisper", JOptionPane.ERROR_MESSAGE);
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
        statusLabel.setText(state.message());
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
                String suffix = descriptor.id().equals(defaultModelId) ? "  (default)" : "";
                suffix += downloadService.stateFor(descriptor)
                        .filter(ModelDownloadState::isActive)
                        .map(state -> "  (downloading " + state.percent() + "%)")
                        .orElse("");
                label.setText(descriptor.displayName() + suffix + " - " + descriptor.description());
            }
            if (!isSelected) {
                label.setForeground(theme.text());
                label.setBackground(theme.field());
            } else {
                label.setForeground(theme.accentText());
                label.setBackground(theme.accent());
            }
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return label;
        }
    }
}
