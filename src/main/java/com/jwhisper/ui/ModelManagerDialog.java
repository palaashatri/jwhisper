package com.jwhisper.ui;

import com.jwhisper.model.ModelDescriptor;
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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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
    private final Runnable onModelsChanged;
    private final DefaultListModel<ModelDescriptor> installedListModel = new DefaultListModel<>();
    private final JList<ModelDescriptor> installedList = new JList<>(installedListModel);
    private final JComboBox<ModelDescriptor> availableCombo = new JComboBox<>();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JButton downloadButton = new JButton("Download model");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton defaultButton = new JButton("Set default");
    private boolean busy;

    public ModelManagerDialog(Frame owner, ModelManagerAgent modelManagerAgent, Runnable onModelsChanged) {
        super(owner, "Manage models", true);
        this.modelManagerAgent = modelManagerAgent;
        this.onModelsChanged = onModelsChanged;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        refreshModels();
        pack();
        setMinimumSize(new Dimension(620, 420));
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        JLabel title = new JLabel("Models");
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 22f));
        root.add(title, BorderLayout.NORTH);

        installedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installedList.setCellRenderer(new ModelRenderer());
        installedList.addListSelectionListener(event -> updateButtons());
        JScrollPane scrollPane = new JScrollPane(installedList);
        scrollPane.setPreferredSize(new Dimension(520, 180));

        DefaultComboBoxModel<ModelDescriptor> availableModel = new DefaultComboBoxModel<>();
        modelManagerAgent.availableModels().forEach(availableModel::addElement);
        availableCombo.setModel(availableModel);
        availableCombo.setRenderer(new ModelRenderer());

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        center.add(scrollPane, constraints);

        JPanel downloadRow = new JPanel(new GridBagLayout());
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
        JPanel progressRow = new JPanel(new BorderLayout(12, 0));
        statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
        progressRow.add(statusLabel, BorderLayout.CENTER);
        progressRow.add(progressBar, BorderLayout.SOUTH);
        actions.add(progressRow, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonRow.add(defaultButton);
        buttonRow.add(deleteButton);
        JButton closeButton = new JButton("Close");
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
        installedListModel.clear();
        List<ModelDescriptor> installed = modelManagerAgent.installedModels();
        installed.forEach(installedListModel::addElement);
        if (!installed.isEmpty()) {
            installedList.setSelectedIndex(0);
        }
        updateButtons();
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
        setBusy(true);
        SwingWorker<Void, ProgressUpdate> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                modelManagerAgent.downloadModel(descriptor, (fraction, status) -> {
                    setProgress((int) Math.round(fraction * 100));
                    publish(new ProgressUpdate((int) Math.round(fraction * 100), status));
                });
                return null;
            }

            @Override
            protected void process(List<ProgressUpdate> chunks) {
                ProgressUpdate latest = chunks.get(chunks.size() - 1);
                progressBar.setValue(latest.percent());
                statusLabel.setText(latest.status());
            }

            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText("Model ready.");
                    refreshModels();
                    onModelsChanged.run();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                            ModelManagerDialog.this,
                            userMessage(e),
                            "jwhisper",
                            JOptionPane.ERROR_MESSAGE
                    );
                    statusLabel.setText("Download failed. Try again.");
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
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

    private void setBusy(boolean busy) {
        this.busy = busy;
        progressBar.setVisible(busy);
        if (busy) {
            progressBar.setValue(0);
            statusLabel.setText("Downloading...");
        }
        updateButtons();
    }

    private void updateButtons() {
        boolean hasSelection = installedList.getSelectedValue() != null;
        downloadButton.setEnabled(!busy);
        availableCombo.setEnabled(!busy);
        deleteButton.setEnabled(!busy && hasSelection);
        defaultButton.setEnabled(!busy && hasSelection);
    }

    private String userMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return "Download failed. Try again.";
        }
        if (message.contains("disk space")) {
            return "Not enough disk space for this model.";
        }
        if (message.contains("invalid")) {
            return "Model file is invalid. Delete and re-download.";
        }
        return "Download failed. Try again.";
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
                String suffix = modelManagerAgent.defaultModel()
                        .filter(defaultModel -> defaultModel.id().equals(descriptor.id()))
                        .map(defaultModel -> "  (default)")
                        .orElse("");
                label.setText(descriptor.displayName() + suffix + " - " + descriptor.description());
            }
            return label;
        }
    }

    private record ProgressUpdate(int percent, String status) {
    }
}
