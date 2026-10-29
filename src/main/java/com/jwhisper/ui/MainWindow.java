package com.jwhisper.ui;

import com.jwhisper.audio.AudioInputAgent;
import com.jwhisper.audio.AudioJob;
import com.jwhisper.deps.DependencyReport;
import com.jwhisper.export.OutputFormat;
import com.jwhisper.export.TranscriptionExporter;
import com.jwhisper.model.ModelCatalog;
import com.jwhisper.model.ModelDescriptor;
import com.jwhisper.model.ModelDownloadListener;
import com.jwhisper.model.ModelDownloadService;
import com.jwhisper.model.ModelDownloadState;
import com.jwhisper.model.ModelDownloadStatus;
import com.jwhisper.model.ModelManagerAgent;
import com.jwhisper.platform.BuildProfile;
import com.jwhisper.transcribe.TranscriptionAgent;
import com.jwhisper.transcribe.TranscriptionListener;
import com.jwhisper.transcribe.TranscriptionOptions;
import com.jwhisper.transcribe.TranscriptionResult;
import com.jwhisper.transcribe.TranscriptionTask;
import com.jwhisper.transcribe.TranscriptionTimings;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionException;

public final class MainWindow extends JFrame {
    private static final String CUSTOM_LANGUAGE_SENTINEL = "__custom__";

    private final ModelManagerAgent modelManagerAgent;
    private final ModelDownloadService downloadService;
    private final AudioInputAgent audioInputAgent;
    private final TranscriptionAgent transcriptionAgent;
    private final DependencyReport dependencyReport;
    private UiTheme theme = UiTheme.current();
    private final Set<String> preloadStarted = new HashSet<>();
    private final ModelDownloadListener downloadListener = state -> SwingUtilities.invokeLater(() -> {
        refreshModels();
        handleDownloadState(state);
    });

    // Header
    private final JLabel brandTitleLabel = new JLabel("JWhisper");
    private final JLabel brandSubtitleLabel = new JLabel("Neural speech transcription");
    private final AccentUnderlineBar brandUnderline = new AccentUnderlineBar();
    private final ThemedControls.BadgePill runtimeBadge;
    private final JButton themeToggleButton = new JButton();

    // Model & Transcription Options Card
    private ThemedControls.CardPanel configCard;
    private final JLabel modelLabel = new JLabel("Model");
    private final JComboBox<ModelComboItem> modelCombo = new JComboBox<>();
    private final JButton manageModelsButton = new JButton("Manage models...");

    private final JLabel taskLabel = new JLabel("Task");
    private ThemedControls.SegmentedPillGroup<TranscriptionTask> taskGroup;
    private final JLabel languageLabel = new JLabel("Language");
    private final JComboBox<LanguageOption> languageCombo = new JComboBox<>();
    private final DefaultComboBoxModel<LanguageOption> languageComboModel = new DefaultComboBoxModel<>();
    private final JLabel beamLabel = new JLabel("Decoding");
    private final JComboBox<BeamOption> beamCombo = new JComboBox<>(new BeamOption[]{
            new BeamOption(1, "Greedy (1×)"),
            new BeamOption(2, "Beam 2"),
            new BeamOption(3, "Beam 3"),
            new BeamOption(5, "Beam 5")
    });
    private ThemedControls.TogglePillButton timestampsPill;

    private final JLabel promptLabel = new JLabel("Prompt");
    private ThemedControls.PlaceholderTextField promptField;

    // Drop zone
    private final DropZonePanel dropZone;

    // Transcript & Export Preview Card
    private ThemedControls.CardPanel transcriptCard;
    private final JLabel transcriptHeaderLabel = new JLabel("Transcript");
    private final JComboBox<BatchViewItem> batchViewCombo = new JComboBox<>();
    private ThemedControls.SegmentedPillGroup<OutputFormat> formatGroup;
    private JScrollPane transcriptScroll;
    private final PlaceholderTextArea transcriptArea = new PlaceholderTextArea(
            "Drop an audio or video file above to transcribe locally. Switch between TXT, SRT, VTT, and JSON anytime."
    );

    // Progress & Footer actions
    private JPanel rootPanel;
    private ThemedControls.StatusDot statusDot;
    private final JLabel statusLabel = new JLabel("Ready.");
    private ThemedControls.AmethystProgressBar progressBar;
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton copyButton = new JButton("Copy");
    private final JButton clearButton = new JButton("Clear");
    private final JButton saveAllButton = new JButton("Save batch...");
    private final JButton saveButton = new JButton("Save .txt...");

    // Active batch & result state (EDT-confined)
    private final Deque<AudioJob> pendingQueue = new ArrayDeque<>();
    private final List<CompletedItem> completedItems = new ArrayList<>();
    private int activeBatchTotal;
    private int activeBatchIndex;
    private boolean transcribing;
    private boolean suppressLanguagePrompt;
    private LanguageOption previousLanguageSelection;

    public MainWindow(
            ModelManagerAgent modelManagerAgent,
            ModelDownloadService downloadService,
            AudioInputAgent audioInputAgent,
            TranscriptionAgent transcriptionAgent,
            DependencyReport dependencyReport
    ) {
        super("JWhisper");
        this.modelManagerAgent = modelManagerAgent;
        this.downloadService = downloadService;
        this.audioInputAgent = audioInputAgent;
        this.transcriptionAgent = transcriptionAgent;
        this.dependencyReport = dependencyReport;
        this.runtimeBadge = new ThemedControls.BadgePill(runtimeBadgeText(), theme, true);
        this.dropZone = new DropZonePanel(this::chooseMediaFiles, this::handleDroppedPaths);

        AppIcons.applyToWindow(this);
        populateLanguageOptions();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildContent());
        modelCombo.setRenderer(new ModelComboRenderer());
        languageCombo.setRenderer(new SimpleComboRenderer());
        beamCombo.setRenderer(new SimpleComboRenderer());
        batchViewCombo.setRenderer(new SimpleComboRenderer());

        modelCombo.addActionListener(event -> handleModelSelection());
        languageCombo.addActionListener(event -> handleLanguageSelection());
        batchViewCombo.addActionListener(event -> renderCompletedOutput());

        downloadService.addListener(downloadListener);
        applyTheme(theme);
        refreshModels();
        wireActions();
        setWorking(false);
        pack();
        setMinimumSize(new Dimension(820, 620));
        setSize(new Dimension(940, 670));
        setLocationRelativeTo(null);
    }

    public void showStartupMessages() {
        if (dependencyReport.hasIssues()) {
            setStatus(dependencyReport.firstUserMessage(), theme.warning());
            JOptionPane.showMessageDialog(
                    this,
                    dependencyReport.firstUserMessage(),
                    "JWhisper",
                    JOptionPane.WARNING_MESSAGE,
                    AppIcons.hiDpiIcon(48)
            );
        }
        if (modelCombo.getItemCount() == 0) {
            startInitialTinySetup();
        }
    }

    public void openModelManagerDialog() {
        ModelManagerDialog dialog = new ModelManagerDialog(
                this,
                modelManagerAgent,
                downloadService,
                theme,
                this::refreshModels
        );
        dialog.setVisible(true);
        refreshModels();
    }

    public void applyTheme(UiTheme newTheme) {
        this.theme = newTheme;
        this.theme.applyGlobalDefaults();

        if (rootPanel != null) {
            rootPanel.setBackground(theme.background());
        }
        brandTitleLabel.setForeground(theme.text());
        brandSubtitleLabel.setForeground(theme.muted());
        brandUnderline.repaint();
        runtimeBadge.setTheme(theme);

        themeToggleButton.setText(theme.dark() ? "☀ Light" : "☾ Dark");
        themeToggleButton.setToolTipText(theme.dark() ? "Switch to Pearl Light theme" : "Switch to Obsidian Dark theme");
        theme.styleButton(themeToggleButton, UiTheme.ButtonRole.SUBTLE);

        configCard.setTheme(theme);
        for (JLabel label : List.of(modelLabel, taskLabel, languageLabel, beamLabel, promptLabel)) {
            label.setForeground(theme.muted());
        }

        ThemedControls.styleComboBox(modelCombo, theme);
        ThemedControls.styleComboBox(languageCombo, theme);
        ThemedControls.styleComboBox(beamCombo, theme);
        ThemedControls.styleComboBox(batchViewCombo, theme);

        theme.styleButton(manageModelsButton, UiTheme.ButtonRole.NORMAL);
        taskGroup.setTheme(theme);
        timestampsPill.setTheme(theme);
        promptField.setTheme(theme);

        dropZone.setTheme(theme);

        transcriptCard.setTheme(theme);
        transcriptHeaderLabel.setForeground(theme.text());
        formatGroup.setTheme(theme);

        ThemedControls.styleScrollPane(transcriptScroll, theme);
        transcriptArea.setTheme(theme);
        transcriptArea.setForeground(theme.text());
        transcriptArea.setBackground(theme.field());
        transcriptArea.setCaretColor(theme.text());
        transcriptArea.setSelectionColor(theme.accent());
        transcriptArea.setSelectedTextColor(theme.accentText());

        statusLabel.setForeground(theme.muted());
        progressBar.setTheme(theme);
        statusDot.setColor(transcribing ? theme.accent() : (dependencyReport.hasIssues() ? theme.warning() : theme.success()));

        theme.styleButton(cancelButton, UiTheme.ButtonRole.DANGER);
        theme.styleButton(copyButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(clearButton, UiTheme.ButtonRole.SUBTLE);
        theme.styleButton(saveAllButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(saveButton, UiTheme.ButtonRole.PRIMARY);

        repaint();
    }

    private JPanel buildContent() {
        rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(theme.background());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        rootPanel.add(content, BorderLayout.CENTER);

        JPanel header = buildHeader();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(header);
        content.add(Box.createVerticalStrut(14));

        configCard = buildConfigCard();
        configCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(configCard);
        content.add(Box.createVerticalStrut(14));

        dropZone.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(dropZone);
        content.add(Box.createVerticalStrut(14));

        transcriptCard = buildTranscriptCard();
        transcriptCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(transcriptCard);
        content.add(Box.createVerticalStrut(12));

        JPanel footer = buildFooter();
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(footer);

        return rootPanel;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        ImageIcon logo = AppIcons.hiDpiIcon(40);
        if (logo != null) {
            JLabel iconLabel = new JLabel(logo);
            iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
            left.add(iconLabel);
        }

        JPanel brandTextStack = new JPanel();
        brandTextStack.setOpaque(false);
        brandTextStack.setLayout(new BoxLayout(brandTextStack, BoxLayout.Y_AXIS));
        brandTitleLabel.setFont(brandTitleLabel.getFont().deriveFont(Font.BOLD, 19f));
        brandTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandSubtitleLabel.setFont(brandSubtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        brandSubtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandUnderline.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandTextStack.add(brandTitleLabel);
        brandTextStack.add(brandSubtitleLabel);
        brandTextStack.add(Box.createVerticalStrut(3));
        brandTextStack.add(brandUnderline);
        left.add(brandTextStack);
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        right.setOpaque(false);
        themeToggleButton.setPreferredSize(new Dimension(86, 28));
        right.add(runtimeBadge);
        right.add(themeToggleButton);
        header.add(right, BorderLayout.EAST);

        return header;
    }

    private ThemedControls.CardPanel buildConfigCard() {
        ThemedControls.CardPanel card = new ThemedControls.CardPanel(theme, 14);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 152));

        for (JLabel label : List.of(modelLabel, taskLabel, languageLabel, beamLabel, promptLabel)) {
            label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        }

        // Row 0: Model selector + Manage models button
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 10);
        card.add(modelLabel, c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel modelBar = new JPanel(new BorderLayout(10, 0));
        modelBar.setOpaque(false);
        modelCombo.setPreferredSize(new Dimension(280, 32));
        manageModelsButton.setPreferredSize(new Dimension(150, 32));
        modelBar.add(modelCombo, BorderLayout.CENTER);
        modelBar.add(manageModelsButton, BorderLayout.EAST);
        card.add(modelBar, c);

        // Row 1: Task | Language | Decoding (Beam) | Timestamps
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 8, 10);
        card.add(taskLabel, c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel optionsBar = new JPanel(new GridBagLayout());
        optionsBar.setOpaque(false);

        taskGroup = new ThemedControls.SegmentedPillGroup<>(
                List.of(
                        new ThemedControls.SegmentedPillGroup.Option<>(TranscriptionTask.TRANSCRIBE, "Transcribe"),
                        new ThemedControls.SegmentedPillGroup.Option<>(TranscriptionTask.TRANSLATE, "Translate → EN")
                ),
                TranscriptionTask.TRANSCRIBE,
                theme
        );

        languageCombo.setModel(languageComboModel);
        languageCombo.setPreferredSize(new Dimension(148, 30));
        previousLanguageSelection = (LanguageOption) languageCombo.getSelectedItem();

        beamCombo.setPreferredSize(new Dimension(134, 30));

        timestampsPill = new ThemedControls.TogglePillButton("Timestamps", true, theme);
        timestampsPill.setToolTipText("Generate segment timestamps (required for SRT and VTT subtitles)");

        GridBagConstraints oc = new GridBagConstraints();
        oc.gridy = 0;
        oc.anchor = GridBagConstraints.WEST;

        oc.gridx = 0;
        oc.insets = new Insets(0, 0, 0, 14);
        optionsBar.add(taskGroup, oc);

        oc.gridx = 1;
        oc.insets = new Insets(0, 0, 0, 6);
        optionsBar.add(languageLabel, oc);

        oc.gridx = 2;
        oc.insets = new Insets(0, 0, 0, 14);
        optionsBar.add(languageCombo, oc);

        oc.gridx = 3;
        oc.insets = new Insets(0, 0, 0, 6);
        optionsBar.add(beamLabel, oc);

        oc.gridx = 4;
        oc.insets = new Insets(0, 0, 0, 12);
        optionsBar.add(beamCombo, oc);

        oc.gridx = 5;
        oc.weightx = 1.0;
        oc.insets = new Insets(0, 0, 0, 0);
        optionsBar.add(timestampsPill, oc);

        card.add(optionsBar, c);

        // Row 2: Optional conditioning prompt
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 10);
        card.add(promptLabel, c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        promptField = new ThemedControls.PlaceholderTextField(
                "Optional prompt or vocabulary hints (speaker names, acronyms, punctuation style)...",
                theme
        );
        promptField.setPreferredSize(new Dimension(420, 31));
        card.add(promptField, c);

        return card;
    }

    private ThemedControls.CardPanel buildTranscriptCard() {
        ThemedControls.CardPanel card = new ThemedControls.CardPanel(theme, 14);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));

        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setOpaque(false);

        JPanel leftHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftHeader.setOpaque(false);
        transcriptHeaderLabel.setFont(transcriptHeaderLabel.getFont().deriveFont(Font.BOLD, 14f));
        batchViewCombo.setPreferredSize(new Dimension(230, 28));
        batchViewCombo.setVisible(false);
        leftHeader.add(transcriptHeaderLabel);
        leftHeader.add(batchViewCombo);
        topBar.add(leftHeader, BorderLayout.WEST);

        formatGroup = new ThemedControls.SegmentedPillGroup<>(
                List.of(
                        new ThemedControls.SegmentedPillGroup.Option<>(OutputFormat.TXT, "TXT"),
                        new ThemedControls.SegmentedPillGroup.Option<>(OutputFormat.SRT, "SRT"),
                        new ThemedControls.SegmentedPillGroup.Option<>(OutputFormat.VTT, "VTT"),
                        new ThemedControls.SegmentedPillGroup.Option<>(OutputFormat.JSON, "JSON")
                ),
                OutputFormat.TXT,
                theme
        );
        formatGroup.setOnSelect(this::handleFormatChanged);
        topBar.add(formatGroup, BorderLayout.EAST);
        card.add(topBar, BorderLayout.NORTH);

        transcriptArea.setEditable(true);
        transcriptArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        updateEditorFont(OutputFormat.TXT);

        transcriptScroll = new JScrollPane(transcriptArea);
        transcriptScroll.setPreferredSize(new Dimension(860, 175));
        transcriptScroll.setMinimumSize(new Dimension(480, 125));
        card.add(transcriptScroll, BorderLayout.CENTER);

        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);

        JPanel progressRow = new JPanel(new BorderLayout(12, 0));
        progressRow.setOpaque(false);

        statusDot = new ThemedControls.StatusDot(theme.success());
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12.5f));
        progressBar = new ThemedControls.AmethystProgressBar(theme);

        JPanel statusLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusLine.setOpaque(false);
        statusLine.add(statusDot);
        statusLine.add(statusLabel);

        JPanel statusStack = new JPanel(new BorderLayout(0, 5));
        statusStack.setOpaque(false);
        statusStack.add(statusLine, BorderLayout.NORTH);
        statusStack.add(progressBar, BorderLayout.CENTER);
        progressRow.add(statusStack, BorderLayout.CENTER);

        cancelButton.setPreferredSize(new Dimension(92, 30));
        cancelButton.setVisible(false);
        progressRow.add(cancelButton, BorderLayout.EAST);
        footer.add(progressRow, BorderLayout.NORTH);

        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);
        copyButton.setPreferredSize(new Dimension(112, 33));
        clearButton.setPreferredSize(new Dimension(82, 33));
        leftActions.add(copyButton);
        leftActions.add(clearButton);
        buttonRow.add(leftActions, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);
        saveAllButton.setPreferredSize(new Dimension(146, 33));
        saveAllButton.setVisible(false);
        saveButton.setPreferredSize(new Dimension(142, 33));
        rightActions.add(saveAllButton);
        rightActions.add(saveButton);
        buttonRow.add(rightActions, BorderLayout.EAST);

        footer.add(buttonRow, BorderLayout.SOUTH);
        return footer;
    }

    private void wireActions() {
        themeToggleButton.addActionListener(event ->
                applyTheme(theme.dark() ? UiTheme.lightTheme() : UiTheme.darkTheme()));
        manageModelsButton.addActionListener(event -> openModelManagerDialog());
        copyButton.addActionListener(event -> copyTranscript());
        clearButton.addActionListener(event -> clearTranscript());
        saveButton.addActionListener(event -> saveTranscript());
        saveAllButton.addActionListener(event -> saveBatchToDirectory());
        cancelButton.addActionListener(event -> {
            pendingQueue.clear();
            transcriptionAgent.cancel();
            setStatus("Canceled.", theme.warning());
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

    private void populateLanguageOptions() {
        List<LanguageOption> presets = List.of(
                new LanguageOption(null, "Auto-detect"),
                new LanguageOption("en", "English (en)"),
                new LanguageOption("es", "Spanish (es)"),
                new LanguageOption("fr", "French (fr)"),
                new LanguageOption("de", "German (de)"),
                new LanguageOption("hi", "Hindi (hi)"),
                new LanguageOption("ja", "Japanese (ja)"),
                new LanguageOption("zh", "Chinese (zh)"),
                new LanguageOption("pt", "Portuguese (pt)"),
                new LanguageOption("ru", "Russian (ru)"),
                new LanguageOption("ar", "Arabic (ar)"),
                new LanguageOption("it", "Italian (it)"),
                new LanguageOption("ko", "Korean (ko)"),
                new LanguageOption("nl", "Dutch (nl)"),
                new LanguageOption("tr", "Turkish (tr)"),
                new LanguageOption("pl", "Polish (pl)"),
                new LanguageOption("sv", "Swedish (sv)"),
                new LanguageOption("uk", "Ukrainian (uk)"),
                new LanguageOption("vi", "Vietnamese (vi)"),
                new LanguageOption("id", "Indonesian (id)"),
                new LanguageOption(CUSTOM_LANGUAGE_SENTINEL, "Custom ISO code...")
        );
        presets.forEach(languageComboModel::addElement);
        languageComboModel.setSelectedItem(presets.get(0));
    }

    private void handleLanguageSelection() {
        if (suppressLanguagePrompt) {
            return;
        }
        Object selected = languageCombo.getSelectedItem();
        if (!(selected instanceof LanguageOption option)) {
            return;
        }
        if (!CUSTOM_LANGUAGE_SENTINEL.equals(option.code())) {
            previousLanguageSelection = option;
            return;
        }

        String input = (String) JOptionPane.showInputDialog(
                this,
                "Enter a Whisper language code (for example: ta, bn, th, cs, el, he):",
                "Custom Language — JWhisper",
                JOptionPane.PLAIN_MESSAGE,
                AppIcons.hiDpiIcon(48),
                null,
                ""
        );
        suppressLanguagePrompt = true;
        try {
            if (input == null || input.isBlank()) {
                languageCombo.setSelectedItem(previousLanguageSelection != null
                        ? previousLanguageSelection
                        : languageComboModel.getElementAt(0));
                return;
            }
            String normalized = input.trim().toLowerCase(Locale.ROOT);
            for (int i = 0; i < languageComboModel.getSize(); i++) {
                LanguageOption existing = languageComboModel.getElementAt(i);
                if (normalized.equals(existing.code())) {
                    languageCombo.setSelectedIndex(i);
                    previousLanguageSelection = existing;
                    return;
                }
            }
            LanguageOption custom = new LanguageOption(normalized, "Custom (" + normalized + ")");
            int insertIndex = Math.max(0, languageComboModel.getSize() - 1);
            languageComboModel.insertElementAt(custom, insertIndex);
            languageCombo.setSelectedItem(custom);
            previousLanguageSelection = custom;
        } finally {
            suppressLanguagePrompt = false;
        }
    }

    private void handleFormatChanged(OutputFormat format) {
        if (format == OutputFormat.SRT || format == OutputFormat.VTT) {
            timestampsPill.setSelected(true);
            timestampsPill.setEnabled(false);
        } else if (!transcribing) {
            timestampsPill.setEnabled(true);
        }
        updateEditorFont(format);
        saveButton.setText("Save ." + format.extension() + "...");
        saveAllButton.setText("Save all ." + format.extension() + "...");
        if (!completedItems.isEmpty()) {
            renderCompletedOutput();
        }
    }

    private void updateEditorFont(OutputFormat format) {
        if (format == OutputFormat.TXT) {
            transcriptArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        } else {
            transcriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        }
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
        modelCombo.setEnabled(!transcribing && hasModels);
        syncModelCapabilities();
        if (!hasModels) {
            setStatus("No models installed.", theme.warning());
        } else if (!dependencyReport.hasIssues() && !transcribing && downloadService.activeDownloads().isEmpty()) {
            if (completedItems.isEmpty()) {
                setStatus("Ready.", theme.success());
            }
        }
    }

    private void syncModelCapabilities() {
        ModelComboItem item = selectedModelItem();
        boolean englishOnly = item != null && item.descriptor().isEnglishOnly();
        if (englishOnly) {
            taskGroup.setSelectedValue(TranscriptionTask.TRANSCRIBE);
            taskGroup.setEnabled(false);
            taskGroup.setToolTipText("English-only (.en) model selected. Install medium, large-v3, or large-v3-turbo for translation.");

            suppressLanguagePrompt = true;
            try {
                for (int i = 0; i < languageComboModel.getSize(); i++) {
                    LanguageOption opt = languageComboModel.getElementAt(i);
                    if ("en".equals(opt.code())) {
                        languageCombo.setSelectedIndex(i);
                        break;
                    }
                }
            } finally {
                suppressLanguagePrompt = false;
            }
            languageCombo.setEnabled(false);
            languageCombo.setToolTipText("English-only (.en) model selected. Install a multilingual model for other languages.");
        } else {
            taskGroup.setEnabled(!transcribing);
            taskGroup.setToolTipText("Transcribe in the spoken language or translate speech into English");
            languageCombo.setEnabled(!transcribing);
            languageCombo.setToolTipText("Select spoken language or leave on Auto-detect");
        }
    }

    private void startInitialTinySetup() {
        ModelDescriptor tinyModel = ModelCatalog.find("tiny.en").orElse(null);
        if (tinyModel == null || modelManagerAgent.isInstalled(tinyModel)) {
            refreshModels();
            return;
        }

        setStatus("Setting up tiny.en...", theme.warning());
        downloadService.startDownload(tinyModel);
        updateDownloadProgress();
    }

    private void chooseMediaFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose audio, video, or folder");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        String[] extensions = AudioInputAgent.supportedExtensionList().toArray(String[]::new);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Audio & video media (" + String.join(", ", extensions) + ")",
                extensions
        ));
        int choice = chooser.showOpenDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            File[] selected = chooser.getSelectedFiles();
            if (selected != null && selected.length > 0) {
                List<Path> paths = new ArrayList<>(selected.length);
                for (File file : selected) {
                    paths.add(file.toPath());
                }
                handleDroppedPaths(paths);
            } else if (chooser.getSelectedFile() != null) {
                handleDroppedPaths(List.of(chooser.getSelectedFile().toPath()));
            }
        }
    }

    private void handleDroppedPaths(List<Path> paths) {
        AudioInputAgent.BatchSelection selection = audioInputAgent.collectMedia(paths);
        if (!selection.hasJobs()) {
            String message = selection.skipped().isEmpty()
                    ? "Unsupported media file. Try an audio or video file."
                    : selection.skipped().get(0);
            setStatus(message, theme.warning());
            JOptionPane.showMessageDialog(this, message, "JWhisper", JOptionPane.WARNING_MESSAGE, AppIcons.hiDpiIcon(48));
            return;
        }
        startBatchTranscription(selection.jobs(), selection.skipped());
    }

    private void startBatchTranscription(List<AudioJob> jobs, List<String> skippedWarnings) {
        ModelComboItem selectedItem = selectedModelItem();
        if (selectedItem == null) {
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "No models installed.",
                    "JWhisper",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    AppIcons.hiDpiIcon(48),
                    new Object[]{"Download a model...", "Not now"},
                    "Download a model..."
            );
            if (choice == 0) {
                openModelManagerDialog();
            }
            return;
        }
        if (!selectedItem.ready()) {
            String message = selectedItem.state() != null && selectedItem.state().isActive()
                    ? selectedItem.descriptor().displayName() + " is still downloading."
                    : "That model is not ready yet.";
            setStatus(message, theme.warning());
            JOptionPane.showMessageDialog(this, message, "JWhisper", JOptionPane.INFORMATION_MESSAGE, AppIcons.hiDpiIcon(48));
            updateDownloadProgress();
            return;
        }

        pendingQueue.clear();
        pendingQueue.addAll(jobs);
        completedItems.clear();
        activeBatchTotal = jobs.size();
        activeBatchIndex = 0;

        batchViewCombo.setVisible(false);
        saveAllButton.setVisible(false);
        transcriptArea.setText("");

        if (!skippedWarnings.isEmpty()) {
            setStatus("Queued " + jobs.size() + " media file(s) (" + skippedWarnings.size() + " unsupported skipped).",
                    theme.accent());
        }
        setWorking(true);
        runNextBatchJob(selectedItem.descriptor(), buildCurrentTranscriptionOptions());
    }

    private TranscriptionOptions buildCurrentTranscriptionOptions() {
        ModelComboItem modelItem = selectedModelItem();
        boolean englishOnly = modelItem != null && modelItem.descriptor().isEnglishOnly();

        LanguageOption langOption = (LanguageOption) languageCombo.getSelectedItem();
        String language = englishOnly ? "en" : (langOption == null ? null : langOption.code());
        if (CUSTOM_LANGUAGE_SENTINEL.equals(language)) {
            language = null;
        }

        TranscriptionTask task = englishOnly ? TranscriptionTask.TRANSCRIBE : taskGroup.selectedValue();
        BeamOption beamOption = (BeamOption) beamCombo.getSelectedItem();
        int beamSize = beamOption == null ? 1 : beamOption.beamSize();

        OutputFormat format = formatGroup.selectedValue();
        boolean timestamps = timestampsPill.isSelected() || format == OutputFormat.SRT || format == OutputFormat.VTT;
        String prompt = promptField.getText();

        return TranscriptionOptions.defaults()
                .withLanguage(language)
                .withTask(task)
                .withBeamSize(beamSize)
                .withTimestamps(timestamps)
                .withInitialPrompt(prompt);
    }

    private void runNextBatchJob(ModelDescriptor model, TranscriptionOptions options) {
        AudioJob nextJob = pendingQueue.pollFirst();
        if (nextJob == null) {
            finishBatchRun();
            return;
        }
        activeBatchIndex++;
        String prefix = activeBatchTotal > 1
                ? "[" + activeBatchIndex + "/" + activeBatchTotal + "] " + nextJob.file().getFileName() + " — "
                : "";
        setStatus(prefix + "Preparing audio...", theme.accent());

        transcriptionAgent.transcribeResult(nextJob, model, options, new SwingTranscriptionListener(prefix))
                .whenComplete((result, throwable) -> SwingUtilities.invokeLater(() -> {
                    if (throwable != null) {
                        pendingQueue.clear();
                        handleTranscriptionError(throwable);
                        setWorking(false);
                        return;
                    }
                    completedItems.add(new CompletedItem(nextJob.file(), result));
                    if (!pendingQueue.isEmpty()) {
                        double completedFraction = activeBatchIndex / (double) Math.max(1, activeBatchTotal);
                        progressBar.setValue((int) Math.round(completedFraction * 100.0));
                        runNextBatchJob(model, options);
                    } else {
                        finishBatchRun();
                    }
                }));
    }

    private void finishBatchRun() {
        populateBatchSelector();
        renderCompletedOutput();
        if (completedItems.size() == 1) {
            TranscriptionResult single = completedItems.get(0).result();
            if (single.text().isBlank()) {
                setStatus("No speech detected • " + formatMetrics(single), theme.muted());
            } else {
                setStatus("Done • " + formatMetrics(single), theme.success());
            }
        } else if (completedItems.size() > 1) {
            double totalAudio = completedItems.stream()
                    .map(CompletedItem::result)
                    .map(TranscriptionResult::timings)
                    .filter(t -> t != null)
                    .mapToDouble(TranscriptionTimings::audioSeconds)
                    .sum();
            long totalMs = completedItems.stream()
                    .map(CompletedItem::result)
                    .map(TranscriptionResult::timings)
                    .filter(t -> t != null)
                    .mapToLong(TranscriptionTimings::totalMillis)
                    .sum();
            setStatus(String.format(
                    Locale.ROOT,
                    "Batch complete • %d files (%.1fs audio in %.2fs)",
                    completedItems.size(),
                    totalAudio,
                    totalMs / 1000.0
            ), theme.success());
        }
        setWorking(false);
    }

    private String formatMetrics(TranscriptionResult result) {
        List<String> parts = new ArrayList<>();
        TranscriptionTimings timings = result.timings();
        if (timings != null && timings.audioSeconds() > 0) {
            double rtf = timings.realTimeFactor();
            parts.add(String.format(
                    Locale.ROOT,
                    "%.1fs audio in %.2fs (%.2f× RTF)",
                    timings.audioSeconds(),
                    timings.totalMillis() / 1000.0,
                    rtf
            ));
        }
        if (result.language() != null && !"und".equals(result.language())) {
            parts.add("lang: " + result.language());
        }
        if (result.provider() != null && !"unknown".equals(result.provider())) {
            parts.add(result.provider());
        }
        int segCount = result.segments().size();
        parts.add(segCount + (segCount == 1 ? " segment" : " segments"));
        return String.join("  ·  ", parts);
    }

    private void populateBatchSelector() {
        DefaultComboBoxModel<BatchViewItem> model = new DefaultComboBoxModel<>();
        if (completedItems.size() > 1) {
            model.addElement(new BatchViewItem(-1, "All files (" + completedItems.size() + ")"));
            for (int i = 0; i < completedItems.size(); i++) {
                Path file = completedItems.get(i).sourceFile();
                model.addElement(new BatchViewItem(i, (i + 1) + ". " + file.getFileName()));
            }
            batchViewCombo.setModel(model);
            batchViewCombo.setSelectedIndex(0);
            batchViewCombo.setVisible(true);
            saveAllButton.setVisible(true);
        } else {
            batchViewCombo.setModel(model);
            batchViewCombo.setVisible(false);
            saveAllButton.setVisible(false);
        }
    }

    private void renderCompletedOutput() {
        if (completedItems.isEmpty()) {
            updateActionButtons();
            return;
        }
        OutputFormat format = formatGroup.selectedValue();
        BatchViewItem viewItem = (BatchViewItem) batchViewCombo.getSelectedItem();
        int selectedIndex = viewItem == null ? (completedItems.size() == 1 ? 0 : -1) : viewItem.index();

        String rendered;
        if (selectedIndex >= 0 && selectedIndex < completedItems.size()) {
            rendered = TranscriptionExporter.render(completedItems.get(selectedIndex).result(), format).stripTrailing();
        } else {
            StringBuilder combined = new StringBuilder();
            for (int i = 0; i < completedItems.size(); i++) {
                CompletedItem item = completedItems.get(i);
                if (i > 0) {
                    combined.append(System.lineSeparator()).append(System.lineSeparator());
                }
                if (completedItems.size() > 1) {
                    combined.append("=== ").append(item.sourceFile().getFileName()).append(" ===")
                            .append(System.lineSeparator());
                }
                combined.append(TranscriptionExporter.render(item.result(), format).stripTrailing());
            }
            rendered = combined.toString();
        }
        transcriptArea.setText(rendered);
        transcriptArea.setCaretPosition(0);
        updateActionButtons();
    }

    private void handleTranscriptionError(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = cause.getMessage();
        if ("Canceled.".equals(message)) {
            setStatus("Canceled.", theme.warning());
            return;
        }
        if (message == null || message.isBlank()) {
            message = "Something went wrong. Try another file.";
        }
        setStatus(message.lines().findFirst().orElse("Something went wrong. Try another file."), theme.warning());
        JOptionPane.showMessageDialog(this, message, "JWhisper", JOptionPane.ERROR_MESSAGE, AppIcons.hiDpiIcon(48));
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
        }
        updateActionButtons();
        if (!working) {
            updateDownloadProgress();
        }
    }

    private void updateActionButtons() {
        boolean hasText = !transcriptArea.getText().isBlank();
        copyButton.setEnabled(!transcribing && hasText);
        clearButton.setEnabled(!transcribing && (hasText || !completedItems.isEmpty()));
        saveButton.setEnabled(!transcribing && hasText);
        saveAllButton.setEnabled(!transcribing && completedItems.size() > 1);
    }

    private void setControlsBusy(boolean busy) {
        dropZone.setEnabled(!busy);
        manageModelsButton.setEnabled(!busy);
        modelCombo.setEnabled(!busy && modelCombo.getItemCount() > 0);
        beamCombo.setEnabled(!busy);
        promptField.setEnabled(!busy);
        OutputFormat format = formatGroup.selectedValue();
        timestampsPill.setEnabled(!busy && format != OutputFormat.SRT && format != OutputFormat.VTT);
        batchViewCombo.setEnabled(!busy);
        syncModelCapabilities();
    }

    private void handleDownloadState(ModelDownloadState state) {
        if (state.status() == ModelDownloadStatus.SUCCEEDED) {
            setStatus(state.message(), theme.success());
            preloadIfUseful(state.descriptor());
        } else if (state.status() == ModelDownloadStatus.FAILED) {
            progressBar.setVisible(false);
            setStatus(state.descriptor().displayName() + ": " + state.errorMessage(), theme.warning());
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
        setStatus("Downloading " + state.descriptor().displayName() + " (" + state.percent() + "%) — " + state.message(),
                theme.accent());
    }

    private void preloadIfUseful(ModelDescriptor descriptor) {
        if (preloadStarted.add(descriptor.id())) {
            transcriptionAgent.preload(descriptor);
        }
    }

    private void handleModelSelection() {
        syncModelCapabilities();
        ModelComboItem item = selectedModelItem();
        if (item != null && !item.ready()) {
            if (item.state() != null && item.state().isActive()) {
                setStatus(item.descriptor().displayName() + " is still downloading.", theme.warning());
            } else if (item.state() != null && item.state().status() == ModelDownloadStatus.FAILED) {
                setStatus(item.descriptor().displayName() + ": " + item.state().errorMessage(), theme.warning());
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
        setStatus("Copied " + formatGroup.selectedValue().name() + " output to clipboard.", theme.success());
    }

    private void clearTranscript() {
        completedItems.clear();
        batchViewCombo.setVisible(false);
        saveAllButton.setVisible(false);
        transcriptArea.setText("");
        setStatus("Ready.", theme.success());
        updateActionButtons();
    }

    private void saveTranscript() {
        String text = transcriptArea.getText();
        if (text.isBlank()) {
            return;
        }
        OutputFormat format = formatGroup.selectedValue();
        File suggestedFile = suggestedSingleOutputFile(format);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save " + format.name() + " transcript");
        if (suggestedFile.getParentFile() != null && suggestedFile.getParentFile().isDirectory()) {
            chooser.setCurrentDirectory(suggestedFile.getParentFile());
        }
        chooser.setSelectedFile(suggestedFile);
        chooser.setFileFilter(new FileNameExtensionFilter(
                format.name() + " (*." + format.extension() + ")",
                format.extension()
        ));

        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (Files.exists(target)) {
            int overwrite = JOptionPane.showConfirmDialog(
                    this,
                    "Overwrite existing file " + target.getFileName() + "?",
                    "JWhisper",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    AppIcons.hiDpiIcon(48)
            );
            if (overwrite != JOptionPane.OK_OPTION) {
                return;
            }
        }
        try {
            String outputContent = text.endsWith("\n") ? text : text + System.lineSeparator();
            Files.writeString(target, outputContent, StandardCharsets.UTF_8);
            setStatus("Saved to " + target.toAbsolutePath().normalize(), theme.success());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save file. Check folder permissions and try again.",
                    "JWhisper", JOptionPane.ERROR_MESSAGE, AppIcons.hiDpiIcon(48));
        }
    }

    private void saveBatchToDirectory() {
        if (completedItems.isEmpty()) {
            return;
        }
        OutputFormat format = formatGroup.selectedValue();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose folder for batch ." + format.extension() + " files");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        Path firstParent = completedItems.get(0).sourceFile().getParent();
        if (firstParent != null && Files.isDirectory(firstParent)) {
            chooser.setCurrentDirectory(firstParent.toFile());
        }
        int choice = chooser.showSaveDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }
        Path dir = chooser.getSelectedFile().toPath();
        try {
            Files.createDirectories(dir);
            Set<Path> written = new HashSet<>();
            for (CompletedItem item : completedItems) {
                String stem = fileStem(item.sourceFile());
                Path candidate = dir.resolve(stem + "." + format.extension()).toAbsolutePath().normalize();
                int suffix = 2;
                while (!written.add(candidate)) {
                    candidate = dir.resolve(stem + "-" + suffix + "." + format.extension()).toAbsolutePath().normalize();
                    suffix++;
                }
                String rendered = TranscriptionExporter.render(item.result(), format);
                Files.writeString(candidate, rendered, StandardCharsets.UTF_8);
            }
            setStatus("Saved " + written.size() + " ." + format.extension() + " files to " + dir.toAbsolutePath().normalize(),
                    theme.success());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not write batch files. Check folder permissions.",
                    "JWhisper", JOptionPane.ERROR_MESSAGE, AppIcons.hiDpiIcon(48));
        }
    }

    private File suggestedSingleOutputFile(OutputFormat format) {
        BatchViewItem viewItem = (BatchViewItem) batchViewCombo.getSelectedItem();
        CompletedItem active = null;
        if (completedItems.size() == 1) {
            active = completedItems.get(0);
        } else if (viewItem != null && viewItem.index() >= 0 && viewItem.index() < completedItems.size()) {
            active = completedItems.get(viewItem.index());
        }
        if (active != null) {
            String stem = fileStem(active.sourceFile());
            Path parent = active.sourceFile().getParent();
            if (parent != null) {
                return parent.resolve(stem + "." + format.extension()).toFile();
            }
            return new File(stem + "." + format.extension());
        }
        return new File("transcript." + format.extension());
    }

    private static String fileStem(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void setStatus(String message, java.awt.Color dotColor) {
        statusLabel.setText(message);
        if (statusDot != null && dotColor != null) {
            statusDot.setColor(dotColor);
        }
    }

    private static String runtimeBadgeText() {
        return switch (BuildProfile.runtimeVariant()) {
            case "apple" -> "APPLE • COREML";
            case "gpu" -> "GPU • CUDA";
            default -> "CPU • LOCAL ONNX";
        };
    }

    private final class SwingTranscriptionListener implements TranscriptionListener {
        private final String prefix;

        private SwingTranscriptionListener(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void onStatus(String message) {
            SwingUtilities.invokeLater(() -> setStatus(prefix + message, theme.accent()));
        }

        @Override
        public void onProgress(double fraction) {
            SwingUtilities.invokeLater(() -> {
                double overall = activeBatchTotal <= 1
                        ? fraction
                        : ((activeBatchIndex - 1) + Math.max(0.0, Math.min(1.0, fraction))) / activeBatchTotal;
                progressBar.setValue((int) Math.round(overall * 100.0));
            });
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

    /** Subtle 3px sculpted amethyst bar matching assets/brand/wordmark.png. */
    private final class AccentUnderlineBar extends JComponent {
        private AccentUnderlineBar() {
            setPreferredSize(new Dimension(54, 3));
            setMinimumSize(new Dimension(54, 3));
            setMaximumSize(new Dimension(54, 3));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, theme.accentStrong(), getWidth(), 0, theme.accentGlow()));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 3, 3);
            g2.dispose();
        }
    }

    private record CompletedItem(Path sourceFile, TranscriptionResult result) {
    }

    private record LanguageOption(String code, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record BeamOption(int beamSize, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record BatchViewItem(int index, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record ModelComboItem(ModelDescriptor descriptor, ModelDownloadState state, boolean ready) {
        @Override
        public String toString() {
            String scope = descriptor.isMultilingual() ? "Multilingual" : "English";
            if (ready) {
                return descriptor.displayName() + "  ·  " + scope + " (" + descriptor.formattedSize() + ")";
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

    private final class SimpleComboRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus
        ) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
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
            } else {
                label.setForeground(theme.accentText());
                label.setBackground(theme.accent());
            }
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return label;
        }
    }
}
