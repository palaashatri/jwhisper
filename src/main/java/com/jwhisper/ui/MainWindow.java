package com.jwhisper.ui;

import com.jwhisper.audio.AudioInputAgent;
import com.jwhisper.audio.AudioJob;
import com.jwhisper.audio.AudioValidationResult;
import com.jwhisper.deps.DependencyReport;
import com.jwhisper.model.ModelCatalog;
import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.model.ModelDownloadListener;
import com.jwhisper.model.ModelDownloadService;
import com.jwhisper.model.ModelDownloadState;
import com.jwhisper.model.ModelDownloadStatus;
import com.jwhisper.model.ModelManagerAgent;
import com.jwhisper.transcribe.TranscriptionAgent;
import com.jwhisper.transcribe.TranscriptionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;

public final class MainWindow extends JFrame {
    private final ModelManagerAgent modelManagerAgent;
    private final ModelDownloadService downloadService;
    private final AudioInputAgent audioInputAgent;
    private final TranscriptionAgent transcriptionAgent;
    private final DependencyReport dependencyReport;
    private final UiTheme theme = UiTheme.current();
    private final Set<String> preloadStarted = new HashSet<>();
    private final ModelDownloadListener downloadListener = state -> SwingUtilities.invokeLater(() -> {
        refreshModels();
        handleDownloadState(state);
    });

    private final JComboBox<ModelComboItem> modelCombo = new JComboBox<>();
    private final JButton manageModelsButton = new JButton("Manage models...");
    private final PlaceholderTextArea transcriptArea = new PlaceholderTextArea("Your transcript will appear here.");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JButton copyButton = new JButton("Copy text");
    private final JButton saveButton = new JButton("Save as file...");
    private final JButton cancelButton = new JButton("Cancel");
    private final DropZonePanel dropZone;
    private boolean transcribing;

    public MainWindow(
            ModelManagerAgent modelManagerAgent,
            ModelDownloadService downloadService,
            AudioInputAgent audioInputAgent,
            TranscriptionAgent transcriptionAgent,
            DependencyReport dependencyReport
    ) {
        super("jwhisper");
        this.modelManagerAgent = modelManagerAgent;
        this.downloadService = downloadService;
        this.audioInputAgent = audioInputAgent;
        this.transcriptionAgent = transcriptionAgent;
        this.dependencyReport = dependencyReport;
        this.dropZone = new DropZonePanel(this::chooseAudioFile, this::handleDroppedFiles);
        this.dropZone.setTheme(theme);
        this.transcriptArea.setTheme(theme);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        modelCombo.setRenderer(new ModelComboRenderer());
        modelCombo.addActionListener(event -> handleModelSelection());
        downloadService.addListener(downloadListener);
        refreshModels();
        wireActions();
        setWorking(false);
        pack();
        setMinimumSize(new Dimension(720, 520));
        setSize(new Dimension(880, 560));
        setLocationRelativeTo(null);
    }

    public void showStartupMessages() {
        if (dependencyReport.hasIssues()) {
            JOptionPane.showMessageDialog(this, dependencyReport.firstUserMessage(), "jwhisper", JOptionPane.WARNING_MESSAGE);
        }
        if (modelCombo.getItemCount() == 0) {
            startInitialTinySetup();
        }
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(theme.background());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(24, 26, 22, 26));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        root.add(content, BorderLayout.CENTER);

        content.add(sectionHeader("Model"));
        content.add(modelRow());
        content.add(Box.createVerticalStrut(22));

        content.add(sectionHeader("Audio"));
        dropZone.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(dropZone);
        content.add(Box.createVerticalStrut(22));

        content.add(sectionHeader("Transcript"));
        JScrollPane transcriptScroll = new JScrollPane(transcriptArea);
        transcriptScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        transcriptScroll.setPreferredSize(new Dimension(820, 122));
        transcriptScroll.setMinimumSize(new Dimension(480, 112));
        transcriptScroll.setBorder(BorderFactory.createLineBorder(theme.fieldBorder()));
        transcriptScroll.getViewport().setBackground(theme.field());
        transcriptArea.setFont(transcriptArea.getFont().deriveFont(Font.PLAIN, 14f));
        transcriptArea.setForeground(theme.text());
        transcriptArea.setBackground(theme.field());
        transcriptArea.setCaretColor(theme.text());
        transcriptArea.setSelectionColor(theme.accent());
        transcriptArea.setSelectedTextColor(theme.accentText());
        transcriptArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        content.add(transcriptScroll);
        content.add(Box.createVerticalStrut(12));

        content.add(progressRow());
        content.add(Box.createVerticalStrut(14));
        content.add(buttonRow());
        return root;
    }

    private JPanel modelRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 0, 12);
        constraints.anchor = GridBagConstraints.WEST;
        JLabel label = new JLabel("Whisper model:");
        label.setForeground(theme.text());
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
        row.add(label, constraints);

        constraints.gridx = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        modelCombo.setPreferredSize(new Dimension(210, 31));
        modelCombo.setFont(modelCombo.getFont().deriveFont(Font.PLAIN, 14f));
        modelCombo.setForeground(theme.text());
        modelCombo.setBackground(theme.field());
        row.add(modelCombo, constraints);

        constraints.gridx = 2;
        constraints.weightx = 1;
        row.add(Box.createHorizontalGlue(), constraints);

        constraints.gridx = 3;
        constraints.weightx = 0;
        manageModelsButton.setPreferredSize(new Dimension(156, 31));
        theme.styleButton(manageModelsButton, UiTheme.ButtonRole.NORMAL);
        row.add(manageModelsButton, constraints);
        return row;
    }

    private JPanel progressRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setForeground(theme.muted());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 13f));
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(420, 8));
        JPanel statusStack = new JPanel(new BorderLayout(0, 6));
        statusStack.setOpaque(false);
        statusStack.add(statusLabel, BorderLayout.NORTH);
        statusStack.add(progressBar, BorderLayout.CENTER);
        row.add(statusStack, BorderLayout.CENTER);
        cancelButton.setVisible(false);
        theme.styleButton(cancelButton, UiTheme.ButtonRole.NORMAL);
        row.add(cancelButton, BorderLayout.EAST);
        return row;
    }

    private JPanel buttonRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyButton.setPreferredSize(new Dimension(120, 32));
        saveButton.setPreferredSize(new Dimension(148, 32));
        theme.styleButton(copyButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(saveButton, UiTheme.ButtonRole.PRIMARY);
        row.add(copyButton, BorderLayout.WEST);
        row.add(saveButton, BorderLayout.EAST);
        return row;
    }

    private JPanel sectionHeader(String title) {
        JPanel panel = new JPanel(new BorderLayout(10, 6));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(title);
        label.setForeground(theme.muted());
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 16f));
        panel.add(label, BorderLayout.NORTH);
        JSeparator separator = new JSeparator();
        separator.setForeground(theme.separator());
        separator.setBackground(theme.separator());
        panel.add(separator, BorderLayout.SOUTH);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        return panel;
    }

    private void wireActions() {
        manageModelsButton.addActionListener(event -> openModelManager());
        copyButton.addActionListener(event -> copyTranscript());
        saveButton.addActionListener(event -> saveTranscript());
        cancelButton.addActionListener(event -> {
            transcriptionAgent.cancel();
            statusLabel.setText("Canceled.");
            setWorking(false);
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                downloadService.removeListener(downloadListener);
                downloadService.close();
                transcriptionAgent.close();
            }
        });
    }

    private void refreshModels() {
        String selectedId = selectedModelItem() == null ? null : selectedModelItem().descriptor().id();
        DefaultComboBoxModel<ModelComboItem> comboModel = new DefaultComboBoxModel<>();
        Set<String> added = new HashSet<>();
        modelManagerAgent.installedModels().forEach(descriptor -> {
            comboModel.addElement(new ModelComboItem(descriptor, null, true));
            added.add(descriptor.id());
        });
        for (ModelDownloadState state : downloadService.states()) {
            if (!added.contains(state.descriptor().id()) && state.status() != ModelDownloadStatus.SUCCEEDED) {
                comboModel.addElement(new ModelComboItem(state.descriptor(), state, false));
                added.add(state.descriptor().id());
            }
        }
        modelCombo.setModel(comboModel);
        selectModel(comboModel, selectedId);
        boolean hasModels = comboModel.getSize() > 0;
        modelCombo.setEnabled(hasModels);
        if (!hasModels) {
            statusLabel.setText("No models installed.");
        } else if (!dependencyReport.hasIssues() && !transcribing && downloadService.activeDownloads().isEmpty()) {
            statusLabel.setText("Ready.");
        }
    }

    private void openModelManager() {
        ModelManagerDialog dialog = new ModelManagerDialog(this, modelManagerAgent, downloadService, theme, this::refreshModels);
        dialog.setVisible(true);
        refreshModels();
    }

    private void startInitialTinySetup() {
        ModelDescriptor tinyModel = ModelCatalog.find("tiny.en").orElse(null);
        if (tinyModel == null || modelManagerAgent.isInstalled(tinyModel)) {
            refreshModels();
            return;
        }

        statusLabel.setText("Setting up tiny.en...");
        downloadService.startDownload(tinyModel);
        updateDownloadProgress();
    }

    private void chooseAudioFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose audio");
        chooser.setFileFilter(new FileNameExtensionFilter("Audio files", "wav", "mp3", "m4a", "flac", "ogg", "aac"));
        int choice = chooser.showOpenDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            handleAudioResult(audioInputAgent.validate(chooser.getSelectedFile().toPath()));
        }
    }

    private void handleDroppedFiles(List<Path> files) {
        handleAudioResult(audioInputAgent.fromDroppedFiles(files));
    }

    private void handleAudioResult(AudioValidationResult result) {
        if (!result.isOk()) {
            String message = result.message().orElse("Unsupported audio file. Try a WAV or MP3.");
            statusLabel.setText(message);
            JOptionPane.showMessageDialog(this, message, "jwhisper", JOptionPane.WARNING_MESSAGE);
            return;
        }
        result.job().ifPresent(this::startTranscription);
    }

    private void startTranscription(AudioJob job) {
        ModelComboItem selectedItem = selectedModelItem();
        if (selectedItem == null) {
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "No models installed.",
                    "jwhisper",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new Object[]{"Download a model...", "Not now"},
                    "Download a model..."
            );
            if (choice == 0) {
                openModelManager();
            }
            return;
        }
        if (!selectedItem.ready()) {
            String message = selectedItem.state() != null && selectedItem.state().isActive()
                    ? selectedItem.descriptor().displayName() + " is still downloading."
                    : "That model is not ready yet.";
            statusLabel.setText(message);
            JOptionPane.showMessageDialog(this, message, "jwhisper", JOptionPane.INFORMATION_MESSAGE);
            updateDownloadProgress();
            return;
        }

        transcriptArea.setText("");
        if (job.note() != null && !job.note().isBlank()) {
            statusLabel.setText(job.note());
        }
        setWorking(true);
        transcriptionAgent.transcribe(job, selectedItem.descriptor(), new SwingTranscriptionListener())
                .whenComplete((text, throwable) -> SwingUtilities.invokeLater(() -> {
                    if (throwable != null) {
                        handleTranscriptionError(throwable);
                    } else {
                        transcriptArea.setText(text == null ? "" : text);
                        statusLabel.setText(text == null || text.isBlank() ? "No speech found." : "Done.");
                    }
                    setWorking(false);
                }));
    }

    private void handleTranscriptionError(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = cause.getMessage();
        if ("Canceled.".equals(message)) {
            statusLabel.setText("Canceled.");
            return;
        }
        if (message == null || message.isBlank()) {
            message = "Something went wrong. Try another file.";
        }
        statusLabel.setText(message.lines().findFirst().orElse("Something went wrong. Try another file."));
        JOptionPane.showMessageDialog(this, message, "jwhisper", JOptionPane.ERROR_MESSAGE);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void setWorking(boolean working) {
        transcribing = working;
        setControlsBusy(working);
        cancelButton.setVisible(working);
        progressBar.setVisible(working);
        if (working) {
            progressBar.setValue(0);
            statusLabel.setText("Transcribing... this may take a moment.");
        }
        boolean hasText = !transcriptArea.getText().isBlank();
        copyButton.setEnabled(!working && hasText);
        saveButton.setEnabled(!working && hasText);
        if (!working) {
            updateDownloadProgress();
        }
    }

    private void setSetupWorking(boolean working, String status) {
        setControlsBusy(working);
        cancelButton.setVisible(false);
        progressBar.setVisible(working);
        if (working) {
            progressBar.setValue(0);
            statusLabel.setText(status);
        }
        boolean hasText = !transcriptArea.getText().isBlank();
        copyButton.setEnabled(!working && hasText);
        saveButton.setEnabled(!working && hasText);
    }

    private void setControlsBusy(boolean busy) {
        dropZone.setEnabled(!busy);
        manageModelsButton.setEnabled(!busy);
        modelCombo.setEnabled(!busy && modelCombo.getItemCount() > 0);
    }

    private void handleDownloadState(ModelDownloadState state) {
        if (state.status() == ModelDownloadStatus.SUCCEEDED) {
            statusLabel.setText(state.message());
            preloadIfUseful(state.descriptor());
        } else if (state.status() == ModelDownloadStatus.FAILED) {
            progressBar.setVisible(false);
            statusLabel.setText(state.descriptor().displayName() + ": " + state.errorMessage());
        }
        updateDownloadProgress();
    }

    private void updateDownloadProgress() {
        if (transcribing) {
            return;
        }
        List<ModelDownloadState> active = downloadService.activeDownloads();
        if (active.isEmpty()) {
            progressBar.setVisible(false);
            return;
        }
        ModelDownloadState state = active.get(0);
        progressBar.setVisible(true);
        progressBar.setValue(state.percent());
        statusLabel.setText("Model download " + state.percent() + "% - " + state.message());
    }

    private void preloadIfUseful(ModelDescriptor descriptor) {
        if (preloadStarted.add(descriptor.id())) {
            transcriptionAgent.preload(descriptor);
        }
    }

    private void handleModelSelection() {
        ModelComboItem item = selectedModelItem();
        if (item != null && !item.ready()) {
            if (item.state() != null && item.state().isActive()) {
                statusLabel.setText(item.descriptor().displayName() + " is still downloading.");
            } else if (item.state() != null && item.state().status() == ModelDownloadStatus.FAILED) {
                statusLabel.setText(item.descriptor().displayName() + ": " + item.state().errorMessage());
            }
        }
    }

    private ModelComboItem selectedModelItem() {
        Object selected = modelCombo.getSelectedItem();
        return selected instanceof ModelComboItem item ? item : null;
    }

    private void selectModel(DefaultComboBoxModel<ModelComboItem> comboModel, String preferredId) {
        String idToSelect = preferredId;
        if (idToSelect == null) {
            idToSelect = modelManagerAgent.defaultModel().map(ModelDescriptor::id).orElse(null);
        }
        if (idToSelect != null) {
            for (int i = 0; i < comboModel.getSize(); i++) {
                if (comboModel.getElementAt(i).descriptor().id().equals(idToSelect)) {
                    modelCombo.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (comboModel.getSize() > 0) {
            modelCombo.setSelectedIndex(0);
        }
    }

    private void copyTranscript() {
        String text = transcriptArea.getText();
        if (text.isBlank()) {
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        statusLabel.setText("Copied.");
    }

    private void saveTranscript() {
        String text = transcriptArea.getText();
        if (text.isBlank()) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save transcript");
        chooser.setSelectedFile(new java.io.File("transcript.txt"));
        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.writeString(chooser.getSelectedFile().toPath(), text, StandardCharsets.UTF_8);
            statusLabel.setText("Saved.");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Something went wrong. Try again.", "jwhisper", JOptionPane.ERROR_MESSAGE);
        }
    }

    private final class SwingTranscriptionListener implements TranscriptionListener {
        @Override
        public void onStatus(String message) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(message));
        }

        @Override
        public void onProgress(double fraction) {
            SwingUtilities.invokeLater(() -> progressBar.setValue((int) Math.round(fraction * 100)));
        }

        @Override
        public void onTranscriptChunk(String text) {
            SwingUtilities.invokeLater(() -> {
                if (text == null || text.isBlank()) {
                    return;
                }
                if (!transcriptArea.getText().isBlank()) {
                    transcriptArea.append(System.lineSeparator());
                }
                transcriptArea.append(text);
            });
        }
    }

    private record ModelComboItem(ModelDescriptor descriptor, ModelDownloadState state, boolean ready) {
        @Override
        public String toString() {
            if (ready) {
                return descriptor.displayName();
            }
            if (state != null && state.isActive()) {
                return descriptor.displayName() + " (downloading " + state.percent() + "%)";
            }
            if (state != null && state.status() == ModelDownloadStatus.FAILED) {
                return descriptor.displayName() + " (download failed)";
            }
            return descriptor.displayName() + " (not ready)";
        }
    }

    private final class ModelComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ModelComboItem item) {
                label.setText(item.toString());
                if (!item.ready() && (!isSelected || index < 0)) {
                    label.setForeground(theme.subtle());
                } else if (!isSelected) {
                    label.setForeground(theme.text());
                }
            }
            if (!isSelected) {
                label.setBackground(theme.field());
            }
            return label;
        }
    }
}
