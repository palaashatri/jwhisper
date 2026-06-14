package com.jwhisper.ui;

import com.jwhisper.audio.AudioInputAgent;
import com.jwhisper.audio.AudioJob;
import com.jwhisper.audio.AudioValidationResult;
import com.jwhisper.deps.DependencyReport;
import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.model.ModelManagerAgent;
import com.jwhisper.transcribe.TranscriptionAgent;
import com.jwhisper.transcribe.TranscriptionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import java.util.List;
import java.util.concurrent.CompletionException;

public final class MainWindow extends JFrame {
    private static final Color BACKGROUND = new Color(246, 247, 249);
    private static final Color TEXT = new Color(48, 55, 68);
    private static final Color MUTED = new Color(119, 128, 143);

    private final ModelManagerAgent modelManagerAgent;
    private final AudioInputAgent audioInputAgent;
    private final TranscriptionAgent transcriptionAgent;
    private final DependencyReport dependencyReport;

    private final JComboBox<ModelDescriptor> modelCombo = new JComboBox<>();
    private final JButton manageModelsButton = new JButton("Manage models...");
    private final PlaceholderTextArea transcriptArea = new PlaceholderTextArea("Your transcript will appear here.");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Ready.");
    private final JButton copyButton = new JButton("Copy text");
    private final JButton saveButton = new JButton("Save as file...");
    private final JButton cancelButton = new JButton("Cancel");
    private final DropZonePanel dropZone;

    public MainWindow(
            ModelManagerAgent modelManagerAgent,
            AudioInputAgent audioInputAgent,
            TranscriptionAgent transcriptionAgent,
            DependencyReport dependencyReport
    ) {
        super("jwhisper");
        this.modelManagerAgent = modelManagerAgent;
        this.audioInputAgent = audioInputAgent;
        this.transcriptionAgent = transcriptionAgent;
        this.dependencyReport = dependencyReport;
        this.dropZone = new DropZonePanel(this::chooseAudioFile, this::handleDroppedFiles);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        refreshModels();
        wireActions();
        setWorking(false);
        pack();
        setMinimumSize(new Dimension(760, 560));
        setSize(new Dimension(920, 610));
        setLocationRelativeTo(null);
    }

    public void showStartupMessages() {
        if (dependencyReport.hasIssues()) {
            JOptionPane.showMessageDialog(this, dependencyReport.firstUserMessage(), "jwhisper", JOptionPane.WARNING_MESSAGE);
        }
        if (modelCombo.getItemCount() == 0) {
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
        }
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(28, 28, 24, 28));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        root.add(content, BorderLayout.CENTER);

        content.add(sectionHeader("Model"));
        content.add(modelRow());
        content.add(Box.createVerticalStrut(24));

        content.add(sectionHeader("Audio"));
        dropZone.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(dropZone);
        content.add(Box.createVerticalStrut(24));

        content.add(sectionHeader("Transcript"));
        JScrollPane transcriptScroll = new JScrollPane(transcriptArea);
        transcriptScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        transcriptScroll.setPreferredSize(new Dimension(820, 130));
        transcriptScroll.setMinimumSize(new Dimension(480, 120));
        transcriptArea.setFont(transcriptArea.getFont().deriveFont(Font.PLAIN, 16f));
        transcriptArea.setForeground(TEXT);
        transcriptArea.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
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
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 17f));
        row.add(label, constraints);

        constraints.gridx = 1;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        modelCombo.setPreferredSize(new Dimension(250, 36));
        modelCombo.setFont(modelCombo.getFont().deriveFont(Font.PLAIN, 16f));
        row.add(modelCombo, constraints);

        constraints.gridx = 2;
        constraints.weightx = 1;
        row.add(Box.createHorizontalGlue(), constraints);

        constraints.gridx = 3;
        constraints.weightx = 0;
        manageModelsButton.setPreferredSize(new Dimension(200, 38));
        manageModelsButton.setFont(manageModelsButton.getFont().deriveFont(Font.PLAIN, 16f));
        row.add(manageModelsButton, constraints);
        return row;
    }

    private JPanel progressRow() {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setForeground(MUTED);
        progressBar.setStringPainted(false);
        progressBar.setPreferredSize(new Dimension(420, 8));
        JPanel statusStack = new JPanel(new BorderLayout(0, 6));
        statusStack.setOpaque(false);
        statusStack.add(statusLabel, BorderLayout.NORTH);
        statusStack.add(progressBar, BorderLayout.CENTER);
        row.add(statusStack, BorderLayout.CENTER);
        cancelButton.setVisible(false);
        row.add(cancelButton, BorderLayout.EAST);
        return row;
    }

    private JPanel buttonRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        copyButton.setPreferredSize(new Dimension(180, 42));
        copyButton.setFont(copyButton.getFont().deriveFont(Font.PLAIN, 16f));
        saveButton.setPreferredSize(new Dimension(220, 42));
        saveButton.setFont(saveButton.getFont().deriveFont(Font.PLAIN, 16f));
        row.add(copyButton, BorderLayout.WEST);
        row.add(saveButton, BorderLayout.EAST);
        return row;
    }

    private JPanel sectionHeader(String title) {
        JPanel panel = new JPanel(new BorderLayout(10, 6));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 20f));
        panel.add(label, BorderLayout.NORTH);
        JSeparator separator = new JSeparator();
        separator.setForeground(new Color(219, 223, 229));
        panel.add(separator, BorderLayout.SOUTH);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
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
                transcriptionAgent.close();
            }
        });
    }

    private void refreshModels() {
        DefaultComboBoxModel<ModelDescriptor> comboModel = new DefaultComboBoxModel<>();
        modelManagerAgent.installedModels().forEach(comboModel::addElement);
        modelCombo.setModel(comboModel);
        modelManagerAgent.defaultModel().ifPresent(modelCombo::setSelectedItem);
        boolean hasModels = comboModel.getSize() > 0;
        modelCombo.setEnabled(hasModels);
        if (!hasModels) {
            statusLabel.setText("No models installed.");
        } else if (!dependencyReport.hasIssues()) {
            statusLabel.setText("Ready.");
        }
    }

    private void openModelManager() {
        ModelManagerDialog dialog = new ModelManagerDialog(this, modelManagerAgent, this::refreshModels);
        dialog.setVisible(true);
        refreshModels();
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
        ModelDescriptor selectedModel = (ModelDescriptor) modelCombo.getSelectedItem();
        if (selectedModel == null) {
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

        transcriptArea.setText("");
        if (job.note() != null && !job.note().isBlank()) {
            statusLabel.setText(job.note());
        }
        setWorking(true);
        transcriptionAgent.transcribe(job, selectedModel, new SwingTranscriptionListener())
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
        dropZone.setEnabled(!working);
        manageModelsButton.setEnabled(!working);
        modelCombo.setEnabled(!working && modelCombo.getItemCount() > 0);
        progressBar.setVisible(working);
        cancelButton.setVisible(working);
        if (working) {
            progressBar.setValue(0);
            statusLabel.setText("Transcribing... this may take a moment.");
        }
        boolean hasText = !transcriptArea.getText().isBlank();
        copyButton.setEnabled(!working && hasText);
        saveButton.setEnabled(!working && hasText);
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
}
