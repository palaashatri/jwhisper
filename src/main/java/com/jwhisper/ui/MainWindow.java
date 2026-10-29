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
import com.jwhisper.transcribe.TranscriptionAgent;
import com.jwhisper.transcribe.TranscriptionListener;
import com.jwhisper.transcribe.TranscriptionOptions;
import com.jwhisper.transcribe.TranscriptionResult;
import com.jwhisper.transcribe.TranscriptionTask;
import com.jwhisper.transcribe.TranscriptionTimings;

import javax.swing.AbstractAction;
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
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;

public final class MainWindow extends JFrame {
    private static final String CUSTOM_LANGUAGE_SENTINEL = "__custom__";
    private static final Set<String> WHISPER_LANGUAGE_CODES = Set.of(
            "af", "am", "ar", "as", "az", "ba", "be", "bg", "bn", "bo",
            "br", "bs", "ca", "cs", "cy", "da", "de", "el", "en", "es",
            "et", "eu", "fa", "fi", "fo", "fr", "gl", "gu", "ha", "haw",
            "he", "hi", "hr", "ht", "hu", "hy", "id", "is", "it", "ja",
            "jw", "ka", "kk", "km", "kn", "ko", "la", "lb", "ln", "lo",
            "lt", "lv", "mg", "mi", "mk", "ml", "mn", "mr", "ms", "mt",
            "my", "ne", "nl", "nn", "no", "oc", "pa", "pl", "ps", "pt",
            "ro", "ru", "sa", "sd", "si", "sk", "sl", "sn", "so", "sq",
            "sr", "su", "sv", "sw", "ta", "te", "tg", "th", "tk", "tl",
            "tr", "tt", "uk", "ur", "uz", "vi", "yi", "yo", "yue", "zh"
    );

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

    // Menu bar & items
    private JMenuBar appMenuBar;
    private JMenuItem menuOpenItem;
    private JMenuItem menuModelsItem;
    private JMenuItem menuSaveItem;
    private JMenuItem menuSaveBatchItem;
    private JMenuItem menuCopyItem;
    private JMenuItem menuClearItem;
    private JMenuItem menuThemeItem;
    private final List<JMenuItem> menuFormatItems = new ArrayList<>();

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
    private final JPanel batchSelectorWrap = new JPanel();
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
    private StatusTone currentStatusTone = StatusTone.SUCCESS;
    private ThemedControls.AmethystProgressBar progressBar;
    private final JButton cancelButton = new JButton("Cancel");
    private final JButton copyButton = new JButton("Copy");
    private final JButton clearButton = new JButton("Clear");
    private final JPanel batchSaveWrap = new JPanel();
    private final JButton saveAllButton = new JButton("Save all .txt...");
    private final JButton saveButton = new JButton("Save .txt...");

    // Active batch & result state (EDT-confined)
    private final Deque<AudioJob> pendingQueue = new ArrayDeque<>();
    private final List<CompletedItem> completedItems = new ArrayList<>();
    private final Map<OutputFormat, String> combinedBatchEdits = new EnumMap<>(OutputFormat.class);
    private final Map<OutputFormat, String> combinedSavedBaselines = new EnumMap<>(OutputFormat.class);
    private int activeBatchTotal;
    private int activeBatchIndex;
    private long activeRunGeneration;
    private boolean transcribing;
    private boolean userTimestampsPreference = true;
    private boolean hasUnsavedManualEdits;
    private boolean programmaticTextUpdate;
    private boolean suppressBatchComboEvent;
    private boolean suppressLanguagePrompt;
    private boolean languageLockedByEnglishModel;
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
        this.runtimeBadge = new ThemedControls.BadgePill(
                formatRuntimeBadge(transcriptionAgent.activeProviderDisplayName()),
                theme,
                true
        );
        this.dropZone = new DropZonePanel(this::chooseMediaFiles, this::handleDroppedPaths);

        AppIcons.applyToWindow(this);
        populateLanguageOptions();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setContentPane(buildContent());
        setJMenuBar(buildMenuBar());

        modelCombo.setRenderer(new ModelComboRenderer());
        languageCombo.setRenderer(new SimpleComboRenderer());
        beamCombo.setRenderer(new SimpleComboRenderer());
        batchViewCombo.setRenderer(new SimpleComboRenderer());

        modelCombo.addActionListener(event -> handleModelSelection());
        languageCombo.addActionListener(event -> handleLanguageSelection());
        batchViewCombo.addActionListener(event -> {
            if (!suppressBatchComboEvent) {
                renderCompletedOutput();
            }
        });

        installTranscriptDocumentListener();
        installRootKeyBindings();

        downloadService.addListener(downloadListener);
        applyTheme(theme);
        refreshModels();
        wireActions();
        setWorking(false);
        pack();
        setMinimumSize(new Dimension(920, 660));
        setSize(new Dimension(980, 710));
        setLocationRelativeTo(null);
    }

    public void showStartupMessages() {
        if (dependencyReport.hasIssues()) {
            setStatus(dependencyReport.firstUserMessage(), StatusTone.WARNING);
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
        if (transcribing) {
            setStatus("Wait for or cancel the active transcription before managing models.", StatusTone.WARNING);
            return;
        }
        ModelManagerDialog dialog = new ModelManagerDialog(
                this,
                modelManagerAgent,
                downloadService,
                theme,
                this::refreshModels,
                transcriptionAgent::unloadModelSync
        );
        dialog.setVisible(true);
        refreshModels();
        updateDownloadProgress();
    }

    public void applyTheme(UiTheme newTheme) {
        this.theme = newTheme;
        this.theme.applyGlobalDefaults();

        if (rootPanel != null) {
            rootPanel.setBackground(theme.background());
        }
        styleMenuBar();

        brandTitleLabel.setForeground(theme.text());
        brandSubtitleLabel.setForeground(theme.muted());
        brandUnderline.repaint();
        runtimeBadge.setTheme(theme);

        themeToggleButton.setText(theme.dark() ? "☀ Light" : "☾ Dark");
        themeToggleButton.setToolTipText(theme.dark()
                ? "Switch to Pearl Light theme (Cmd/Ctrl+Shift+T)"
                : "Switch to Obsidian Dark theme (Cmd/Ctrl+Shift+T)");
        theme.styleButton(themeToggleButton, UiTheme.ButtonRole.SUBTLE);
        if (menuThemeItem != null) {
            menuThemeItem.setText(theme.dark() ? "Switch to Pearl Light Theme" : "Switch to Obsidian Dark Theme");
        }

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
        statusDot.setColor(colorForTone(currentStatusTone));

        theme.styleButton(cancelButton, UiTheme.ButtonRole.DANGER);
        theme.styleButton(copyButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(clearButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(saveAllButton, UiTheme.ButtonRole.NORMAL);
        theme.styleButton(saveButton, UiTheme.ButtonRole.PRIMARY);

        repaint();
    }

    private JMenuBar buildMenuBar() {
        int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        appMenuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        menuOpenItem = new JMenuItem("Open Audio / Video...");
        menuOpenItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, shortcut));
        menuOpenItem.addActionListener(e -> {
            if (!transcribing) {
                chooseMediaFiles();
            }
        });

        menuModelsItem = new JMenuItem("Manage Whisper Models...");
        menuModelsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, shortcut));
        menuModelsItem.addActionListener(e -> openModelManagerDialog());

        menuSaveItem = new JMenuItem("Save Transcript...");
        menuSaveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut));
        menuSaveItem.addActionListener(e -> saveTranscript());

        menuSaveBatchItem = new JMenuItem("Save All Batch Files...");
        menuSaveBatchItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, shortcut | InputEvent.SHIFT_DOWN_MASK));
        menuSaveBatchItem.addActionListener(e -> saveBatchToDirectory());

        fileMenu.add(menuOpenItem);
        fileMenu.add(menuModelsItem);
        fileMenu.addSeparator();
        fileMenu.add(menuSaveItem);
        fileMenu.add(menuSaveBatchItem);

        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic(KeyEvent.VK_E);

        menuCopyItem = new JMenuItem("Copy Full Transcript");
        menuCopyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, shortcut | InputEvent.SHIFT_DOWN_MASK));
        menuCopyItem.addActionListener(e -> copyTranscript());

        menuClearItem = new JMenuItem("Clear Transcript");
        menuClearItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, shortcut));
        menuClearItem.addActionListener(e -> clearTranscript());

        editMenu.add(menuCopyItem);
        editMenu.add(menuClearItem);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);

        addFormatMenuItem(viewMenu, "Plain Text (TXT)", OutputFormat.TXT, KeyEvent.VK_1, shortcut);
        addFormatMenuItem(viewMenu, "SubRip Subtitles (SRT)", OutputFormat.SRT, KeyEvent.VK_2, shortcut);
        addFormatMenuItem(viewMenu, "WebVTT Subtitles (VTT)", OutputFormat.VTT, KeyEvent.VK_3, shortcut);
        addFormatMenuItem(viewMenu, "Structured JSON", OutputFormat.JSON, KeyEvent.VK_4, shortcut);
        viewMenu.addSeparator();

        menuThemeItem = new JMenuItem("Toggle Theme");
        menuThemeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, shortcut | InputEvent.SHIFT_DOWN_MASK));
        menuThemeItem.addActionListener(e -> toggleThemeAndPersist());
        viewMenu.add(menuThemeItem);

        appMenuBar.add(fileMenu);
        appMenuBar.add(editMenu);
        appMenuBar.add(viewMenu);
        return appMenuBar;
    }

    private void addFormatMenuItem(JMenu menu, String label, OutputFormat format, int keyCode, int mask) {
        JMenuItem item = new JMenuItem(label);
        item.setAccelerator(KeyStroke.getKeyStroke(keyCode, mask));
        item.addActionListener(e -> {
            if (!transcribing && formatGroup != null && formatGroup.isEnabled()) {
                formatGroup.selectAndNotify(format);
            }
        });
        menuFormatItems.add(item);
        menu.add(item);
    }

    private void styleMenuBar() {
        if (appMenuBar == null) {
            return;
        }
        appMenuBar.setOpaque(true);
        appMenuBar.setBackground(theme.surface());
        appMenuBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, theme.separator()));
        for (int i = 0; i < appMenuBar.getMenuCount(); i++) {
            JMenu menu = appMenuBar.getMenu(i);
            if (menu == null) {
                continue;
            }
            menu.setForeground(theme.text());
            menu.getPopupMenu().setBackground(theme.surface());
            menu.getPopupMenu().setBorder(BorderFactory.createLineBorder(theme.cardBorder()));
            for (Component child : menu.getMenuComponents()) {
                if (child instanceof JMenuItem item) {
                    item.setOpaque(true);
                    item.setBackground(theme.surface());
                    item.setForeground(theme.text());
                }
            }
        }
    }

    private void installRootKeyBindings() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelActiveWork");
        getRootPane().getActionMap().put("cancelActiveWork", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (cancelButton.isVisible() && cancelButton.isEnabled()) {
                    cancelButton.doClick();
                }
            }
        });
    }

    private void installTranscriptDocumentListener() {
        transcriptArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onTranscriptTextModified();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onTranscriptTextModified();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onTranscriptTextModified();
            }
        });
    }

    private void onTranscriptTextModified() {
        updateActionButtons();
        if (programmaticTextUpdate || transcribing) {
            return;
        }
        if (completedItems.isEmpty() || formatGroup == null) {
            hasUnsavedManualEdits = !transcriptArea.getText().isBlank();
            return;
        }
        OutputFormat format = formatGroup.selectedValue();
        BatchViewItem viewItem = (BatchViewItem) batchViewCombo.getSelectedItem();
        int selectedIndex = viewItem == null ? (completedItems.size() == 1 ? 0 : -1) : viewItem.index();
        String currentText = transcriptArea.getText();
        if (selectedIndex >= 0 && selectedIndex < completedItems.size()) {
            CompletedItem item = completedItems.get(selectedIndex);
            item.savedBaselineByFormat().computeIfAbsent(format, fmt -> defaultRenderedItem(item, fmt));
            item.editedByFormat().put(format, currentText);
        } else {
            combinedSavedBaselines.computeIfAbsent(format, this::defaultRenderedCombined);
            combinedBatchEdits.put(format, currentText);
        }
        recomputeUnsavedManualEdits();
    }

    private void recomputeUnsavedManualEdits() {
        if (completedItems.isEmpty()) {
            hasUnsavedManualEdits = !transcriptArea.getText().isBlank();
            return;
        }
        for (CompletedItem item : completedItems) {
            for (Map.Entry<OutputFormat, String> entry : item.editedByFormat().entrySet()) {
                OutputFormat fmt = entry.getKey();
                String baseline = item.savedBaselineByFormat().containsKey(fmt)
                        ? item.savedBaselineByFormat().get(fmt)
                        : defaultRenderedItem(item, fmt);
                if (!Objects.equals(entry.getValue(), baseline)) {
                    hasUnsavedManualEdits = true;
                    return;
                }
            }
        }
        for (Map.Entry<OutputFormat, String> entry : combinedBatchEdits.entrySet()) {
            OutputFormat fmt = entry.getKey();
            String baseline = combinedSavedBaselines.containsKey(fmt)
                    ? combinedSavedBaselines.get(fmt)
                    : defaultRenderedCombined(fmt);
            if (!Objects.equals(entry.getValue(), baseline)) {
                hasUnsavedManualEdits = true;
                return;
            }
        }
        hasUnsavedManualEdits = false;
    }

    private void setProgrammaticText(String text) {
        programmaticTextUpdate = true;
        try {
            transcriptArea.setText(text);
        } finally {
            programmaticTextUpdate = false;
        }
        ThemedControls.clearUndoHistory(transcriptArea);
        updateActionButtons();
    }

    private JPanel buildContent() {
        rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(theme.background());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(16, 24, 18, 24));
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
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));

        ImageIcon logo = AppIcons.hiDpiIcon(42);
        if (logo != null) {
            JLabel iconLabel = new JLabel(logo);
            iconLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
            left.add(iconLabel);
            left.add(Box.createHorizontalStrut(12));
        }

        JPanel brandTextStack = new JPanel();
        brandTextStack.setOpaque(false);
        brandTextStack.setLayout(new BoxLayout(brandTextStack, BoxLayout.Y_AXIS));
        brandTextStack.setAlignmentY(Component.CENTER_ALIGNMENT);
        brandTitleLabel.setFont(brandTitleLabel.getFont().deriveFont(Font.BOLD, 19.5f));
        brandTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandSubtitleLabel.setFont(brandSubtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        brandSubtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandUnderline.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandTextStack.add(brandTitleLabel);
        brandTextStack.add(Box.createVerticalStrut(1));
        brandTextStack.add(brandSubtitleLabel);
        brandTextStack.add(Box.createVerticalStrut(5));
        brandTextStack.add(brandUnderline);
        left.add(brandTextStack);
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        runtimeBadge.setAlignmentY(Component.CENTER_ALIGNMENT);
        themeToggleButton.setPreferredSize(new Dimension(90, 28));
        themeToggleButton.setMaximumSize(new Dimension(90, 28));
        themeToggleButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        right.add(runtimeBadge);
        right.add(Box.createHorizontalStrut(8));
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
        c.insets = new Insets(0, 0, 8, 12);
        modelLabel.setLabelFor(modelCombo);
        card.add(modelLabel, c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        JPanel modelBar = new JPanel(new BorderLayout(10, 0));
        modelBar.setOpaque(false);
        modelCombo.setPreferredSize(new Dimension(280, 32));
        manageModelsButton.setPreferredSize(new Dimension(152, 32));
        modelBar.add(modelCombo, BorderLayout.CENTER);
        modelBar.add(manageModelsButton, BorderLayout.EAST);
        card.add(modelBar, c);

        // Row 1: Task | Language | Decoding (Beam) | flush-right Timestamps
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 8, 12);
        card.add(taskLabel, c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
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
        languageCombo.setPreferredSize(new Dimension(142, 30));
        languageCombo.setMinimumSize(new Dimension(132, 30));
        languageLabel.setLabelFor(languageCombo);
        previousLanguageSelection = (LanguageOption) languageCombo.getSelectedItem();

        beamCombo.setPreferredSize(new Dimension(146, 30));
        beamCombo.setMinimumSize(new Dimension(140, 30));
        beamLabel.setLabelFor(beamCombo);

        timestampsPill = new ThemedControls.TogglePillButton("Timestamps", true, theme);
        timestampsPill.setToolTipText("Generate segment timestamps (required for SRT and VTT subtitles)");
        timestampsPill.setOnToggle(selected -> {
            userTimestampsPreference = selected;
            if (!completedItems.isEmpty()) {
                renderCompletedOutput();
            }
        });

        GridBagConstraints oc = new GridBagConstraints();
        oc.gridy = 0;
        oc.anchor = GridBagConstraints.WEST;

        oc.gridx = 0;
        oc.insets = new Insets(0, 0, 0, 12);
        optionsBar.add(taskGroup, oc);

        oc.gridx = 1;
        oc.insets = new Insets(0, 0, 0, 6);
        optionsBar.add(languageLabel, oc);

        oc.gridx = 2;
        oc.insets = new Insets(0, 0, 0, 12);
        optionsBar.add(languageCombo, oc);

        oc.gridx = 3;
        oc.insets = new Insets(0, 0, 0, 6);
        optionsBar.add(beamLabel, oc);

        oc.gridx = 4;
        oc.insets = new Insets(0, 0, 0, 10);
        optionsBar.add(beamCombo, oc);

        oc.gridx = 5;
        oc.weightx = 1.0;
        oc.anchor = GridBagConstraints.EAST;
        oc.insets = new Insets(0, 0, 0, 0);
        optionsBar.add(timestampsPill, oc);

        card.add(optionsBar, c);

        // Row 2: Optional conditioning prompt
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 12);
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
        promptLabel.setLabelFor(promptField);
        card.add(promptField, c);

        return card;
    }

    private ThemedControls.CardPanel buildTranscriptCard() {
        ThemedControls.CardPanel card = new ThemedControls.CardPanel(theme, 14);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));

        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setOpaque(false);

        JPanel leftHeader = new JPanel();
        leftHeader.setOpaque(false);
        leftHeader.setLayout(new BoxLayout(leftHeader, BoxLayout.X_AXIS));
        transcriptHeaderLabel.setFont(transcriptHeaderLabel.getFont().deriveFont(Font.BOLD, 14f));
        transcriptHeaderLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        leftHeader.add(transcriptHeaderLabel);

        batchSelectorWrap.setOpaque(false);
        batchSelectorWrap.setLayout(new BoxLayout(batchSelectorWrap, BoxLayout.X_AXIS));
        batchSelectorWrap.setAlignmentY(Component.CENTER_ALIGNMENT);
        batchViewCombo.setPreferredSize(new Dimension(245, 28));
        batchViewCombo.setMaximumSize(new Dimension(260, 28));
        batchSelectorWrap.add(Box.createHorizontalStrut(12));
        batchSelectorWrap.add(batchViewCombo);
        batchSelectorWrap.setVisible(false);
        leftHeader.add(batchSelectorWrap);
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
        updateEditorPresentation(OutputFormat.TXT);

        transcriptScroll = new JScrollPane(transcriptArea);
        transcriptScroll.setPreferredSize(new Dimension(860, 185));
        transcriptScroll.setMinimumSize(new Dimension(480, 130));
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

        JPanel statusLine = new JPanel();
        statusLine.setOpaque(false);
        statusLine.setLayout(new BoxLayout(statusLine, BoxLayout.X_AXIS));
        statusDot.setAlignmentY(Component.CENTER_ALIGNMENT);
        statusLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        statusLine.add(statusDot);
        statusLine.add(Box.createHorizontalStrut(8));
        statusLine.add(statusLabel);

        JPanel statusStack = new JPanel(new BorderLayout(0, 6));
        statusStack.setOpaque(false);
        statusStack.add(statusLine, BorderLayout.NORTH);
        statusStack.add(progressBar, BorderLayout.CENTER);
        progressRow.add(statusStack, BorderLayout.CENTER);

        cancelButton.setPreferredSize(new Dimension(96, 30));
        cancelButton.setVisible(false);
        progressRow.add(cancelButton, BorderLayout.EAST);
        footer.add(progressRow, BorderLayout.NORTH);

        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);

        JPanel leftActions = new JPanel();
        leftActions.setOpaque(false);
        leftActions.setLayout(new BoxLayout(leftActions, BoxLayout.X_AXIS));
        copyButton.setPreferredSize(new Dimension(96, 33));
        copyButton.setMaximumSize(new Dimension(96, 33));
        clearButton.setPreferredSize(new Dimension(96, 33));
        clearButton.setMaximumSize(new Dimension(96, 33));
        leftActions.add(copyButton);
        leftActions.add(Box.createHorizontalStrut(8));
        leftActions.add(clearButton);
        buttonRow.add(leftActions, BorderLayout.WEST);

        JPanel rightActions = new JPanel();
        rightActions.setOpaque(false);
        rightActions.setLayout(new BoxLayout(rightActions, BoxLayout.X_AXIS));

        batchSaveWrap.setOpaque(false);
        batchSaveWrap.setLayout(new BoxLayout(batchSaveWrap, BoxLayout.X_AXIS));
        saveAllButton.setPreferredSize(new Dimension(164, 33));
        saveAllButton.setMaximumSize(new Dimension(164, 33));
        batchSaveWrap.add(saveAllButton);
        batchSaveWrap.add(Box.createHorizontalStrut(8));
        batchSaveWrap.setVisible(false);

        saveButton.setPreferredSize(new Dimension(152, 33));
        saveButton.setMaximumSize(new Dimension(152, 33));
        rightActions.add(batchSaveWrap);
        rightActions.add(saveButton);
        buttonRow.add(rightActions, BorderLayout.EAST);

        footer.add(buttonRow, BorderLayout.SOUTH);
        return footer;
    }

    private void wireActions() {
        themeToggleButton.addActionListener(event -> toggleThemeAndPersist());
        manageModelsButton.addActionListener(event -> openModelManagerDialog());
        copyButton.addActionListener(event -> copyTranscript());
        clearButton.addActionListener(event -> clearTranscript());
        saveButton.addActionListener(event -> saveTranscript());
        saveAllButton.addActionListener(event -> saveBatchToDirectory());
        cancelButton.addActionListener(event -> handleCancelAction());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                attemptCloseWindow();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                downloadService.removeListener(downloadListener);
                downloadService.close();
                transcriptionAgent.close();
            }
        });
    }

    private void toggleThemeAndPersist() {
        UiTheme next = theme.dark() ? UiTheme.lightTheme() : UiTheme.darkTheme();
        UiTheme.persistPreference(next.dark());
        applyTheme(next);
    }

    private void handleCancelAction() {
        if (transcribing) {
            activeRunGeneration++;
            pendingQueue.clear();
            transcriptionAgent.cancel();
            setWorking(false);
            if (!completedItems.isEmpty()) {
                populateBatchSelector();
                renderCompletedOutput();
                setStatus("Canceled  ·  " + completedItems.size() + " completed file(s) retained.", StatusTone.WARNING);
            } else {
                setStatus("Canceled.", StatusTone.WARNING);
            }
        } else if (!downloadService.activeDownloads().isEmpty()) {
            downloadService.cancelAllActive();
            updateDownloadProgress();
            setStatus("Model download canceled.", StatusTone.WARNING);
        }
    }

    public boolean confirmQuitRequested() {
        boolean downloadsRunning = !downloadService.activeDownloads().isEmpty();
        if (transcribing || downloadsRunning || hasUnsavedManualEdits) {
            String message;
            if (transcribing) {
                message = "A transcription is currently running. Stop it and close JWhisper?";
            } else if (downloadsRunning) {
                message = "A model download is currently running. Cancel the download and close JWhisper?";
            } else {
                message = "You have unsaved transcript edits. Discard edits and close JWhisper?";
            }
            if (hasUnsavedManualEdits && (transcribing || downloadsRunning)) {
                message += "\nUnsaved transcript edits will also be discarded.";
            }
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    message,
                    "JWhisper",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    AppIcons.hiDpiIcon(48)
            );
            if (choice != JOptionPane.OK_OPTION) {
                return false;
            }
            activeRunGeneration++;
            pendingQueue.clear();
            transcriptionAgent.cancel();
            downloadService.cancelAllActive();
            hasUnsavedManualEdits = false;
        }
        dispose();
        return true;
    }

    private void attemptCloseWindow() {
        confirmQuitRequested();
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
                "Enter a Whisper ISO language code (for example: ta, bn, th, cs, el, he, yue):",
                "Custom Language — JWhisper",
                JOptionPane.PLAIN_MESSAGE,
                AppIcons.hiDpiIcon(48),
                null,
                ""
        );
        suppressLanguagePrompt = true;
        try {
            if (input == null || input.isBlank()) {
                revertLanguageSelection();
                return;
            }
            String normalized = input.trim().toLowerCase(Locale.ROOT);
            if (!WHISPER_LANGUAGE_CODES.contains(normalized)) {
                revertLanguageSelection();
                setStatus("Unsupported Whisper language code: '" + normalized + "'.", StatusTone.WARNING);
                JOptionPane.showMessageDialog(
                        this,
                        "'" + normalized + "' is not a supported Whisper ISO language code.\n"
                                + "Examples of valid codes: ta, bn, th, cs, el, he, fa, ur, yue.",
                        "Unsupported Language — JWhisper",
                        JOptionPane.WARNING_MESSAGE,
                        AppIcons.hiDpiIcon(48)
                );
                return;
            }
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

    private void revertLanguageSelection() {
        languageCombo.setSelectedItem(previousLanguageSelection != null
                ? previousLanguageSelection
                : languageComboModel.getElementAt(0));
    }

    private void handleFormatChanged(OutputFormat format) {
        if (format == OutputFormat.SRT || format == OutputFormat.VTT) {
            timestampsPill.setSelected(true);
            timestampsPill.setEnabled(false);
        } else {
            timestampsPill.setSelected(userTimestampsPreference);
            timestampsPill.setEnabled(!transcribing);
        }
        updateEditorPresentation(format);
        saveButton.setText("Save ." + format.extension() + "...");
        saveAllButton.setText("Save all ." + format.extension() + "...");
        if (!completedItems.isEmpty()) {
            renderCompletedOutput();
        }
    }

    private void updateEditorPresentation(OutputFormat format) {
        if (format == OutputFormat.TXT) {
            transcriptArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            transcriptArea.setLineWrap(true);
            transcriptArea.setWrapStyleWord(true);
        } else {
            transcriptArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            transcriptArea.setLineWrap(false);
            transcriptArea.setWrapStyleWord(false);
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
            if (!added.contains(state.descriptor().id())
                    && state.status() != ModelDownloadStatus.SUCCEEDED
                    && state.status() != ModelDownloadStatus.CANCELED) {
                comboModel.addElement(new ModelComboItem(state.descriptor(), state, false));
                added.add(state.descriptor().id());
            }
        }
        modelCombo.setModel(comboModel);
        selectModel(comboModel, selectedId);
        boolean hasModels = comboModel.getSize() > 0;
        modelCombo.setEnabled(!transcribing && hasModels);
        syncModelCapabilities();
        if (!hasModels && downloadService.activeDownloads().isEmpty()) {
            setStatus("No models installed  ·  Open Manage models to install one.", StatusTone.WARNING);
        } else if (!dependencyReport.hasIssues() && !transcribing && downloadService.activeDownloads().isEmpty()) {
            if (completedItems.isEmpty() && currentStatusTone != StatusTone.WARNING) {
                setStatus("Ready.", StatusTone.SUCCESS);
            }
        }
    }

    private void syncModelCapabilities() {
        ModelComboItem item = selectedModelItem();
        boolean englishOnly = item != null && item.descriptor().isEnglishOnly();
        if (englishOnly) {
            if (!languageLockedByEnglishModel && languageCombo.getSelectedItem() instanceof LanguageOption currentOpt
                    && !CUSTOM_LANGUAGE_SENTINEL.equals(currentOpt.code())) {
                previousLanguageSelection = currentOpt;
            }
            languageLockedByEnglishModel = true;
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
            if (languageLockedByEnglishModel) {
                languageLockedByEnglishModel = false;
                suppressLanguagePrompt = true;
                try {
                    revertLanguageSelection();
                } finally {
                    suppressLanguagePrompt = false;
                }
            }
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

        setStatus("Setting up tiny.en...", StatusTone.ACCENT);
        downloadService.startDownload(tinyModel);
        updateDownloadProgress();
    }

    private void chooseMediaFiles() {
        if (transcribing) {
            return;
        }
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
        if (transcribing) {
            return;
        }
        AudioInputAgent.BatchSelection selection = audioInputAgent.collectMedia(paths);
        if (!selection.hasJobs()) {
            String message = selection.skipped().isEmpty()
                    ? "Unsupported media file. Try an audio or video file."
                    : selection.skipped().get(0);
            setStatus(message, StatusTone.WARNING);
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
                    "No Whisper models are installed yet.",
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
            setStatus(message, StatusTone.WARNING);
            JOptionPane.showMessageDialog(this, message, "JWhisper", JOptionPane.INFORMATION_MESSAGE, AppIcons.hiDpiIcon(48));
            updateDownloadProgress();
            return;
        }

        if (hasUnsavedManualEdits) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "You have unsaved transcript edits. Discard edits and transcribe the new file(s)?",
                    "JWhisper",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    AppIcons.hiDpiIcon(48)
            );
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
        }

        activeRunGeneration++;
        long runGen = activeRunGeneration;

        pendingQueue.clear();
        pendingQueue.addAll(jobs);
        completedItems.clear();
        combinedBatchEdits.clear();
        combinedSavedBaselines.clear();
        hasUnsavedManualEdits = false;
        activeBatchTotal = jobs.size();
        activeBatchIndex = 0;

        batchSelectorWrap.setVisible(false);
        batchSaveWrap.setVisible(false);
        setProgrammaticText("");

        if (!skippedWarnings.isEmpty()) {
            setStatus("Queued " + jobs.size() + " media file(s) (" + skippedWarnings.size() + " unsupported skipped).",
                    StatusTone.ACCENT);
        }
        setWorking(true);
        runNextBatchJob(selectedItem.descriptor(), buildCurrentTranscriptionOptions(), runGen);
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

    private void runNextBatchJob(ModelDescriptor model, TranscriptionOptions options, long runGen) {
        if (runGen != activeRunGeneration) {
            return;
        }
        AudioJob nextJob = pendingQueue.pollFirst();
        if (nextJob == null) {
            finishBatchRun();
            return;
        }
        activeBatchIndex++;
        setProgrammaticText("");

        String prefix = activeBatchTotal > 1
                ? "[" + activeBatchIndex + "/" + activeBatchTotal + "] " + nextJob.file().getFileName() + "  ·  "
                : "";
        setStatus(prefix + "Preparing audio...", StatusTone.ACCENT);

        transcriptionAgent.transcribeResult(nextJob, model, options, new SwingTranscriptionListener(prefix, runGen))
                .whenComplete((result, throwable) -> SwingUtilities.invokeLater(() -> {
                    if (runGen != activeRunGeneration) {
                        return;
                    }
                    if (throwable != null) {
                        pendingQueue.clear();
                        setWorking(false);
                        if (!completedItems.isEmpty()) {
                            populateBatchSelector();
                            renderCompletedOutput();
                        }
                        handleTranscriptionError(throwable);
                        return;
                    }
                    completedItems.add(new CompletedItem(
                            nextJob.file(),
                            result,
                            options.timestamps(),
                            new EnumMap<>(OutputFormat.class),
                            new EnumMap<>(OutputFormat.class)
                    ));
                    if (!pendingQueue.isEmpty()) {
                        double completedFraction = activeBatchIndex / (double) Math.max(1, activeBatchTotal);
                        progressBar.setValue((int) Math.round(completedFraction * 100.0));
                        runNextBatchJob(model, options, runGen);
                    } else {
                        finishBatchRun();
                    }
                }));
    }

    private void finishBatchRun() {
        setWorking(false);
        if (!completedItems.isEmpty()) {
            String actualProvider = completedItems.get(completedItems.size() - 1).result().provider();
            runtimeBadge.setText(formatRuntimeBadge(actualProvider));
            runtimeBadge.revalidate();
            runtimeBadge.repaint();
        }
        populateBatchSelector();
        renderCompletedOutput();
        if (completedItems.size() == 1) {
            CompletedItem singleItem = completedItems.get(0);
            TranscriptionResult single = singleItem.result();
            if (single.text().isBlank()) {
                setStatus("No speech detected  ·  " + formatMetrics(single), StatusTone.MUTED);
            } else if (!warnIfTimedSubtitlesLackTimestamps()) {
                setStatus("Done  ·  " + formatMetrics(single), StatusTone.SUCCESS);
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
            if (!warnIfTimedSubtitlesLackTimestamps()) {
                setStatus(String.format(
                        Locale.ROOT,
                        "Batch complete  ·  %d files (%.1fs audio in %.2fs)",
                        completedItems.size(),
                        totalAudio,
                        totalMs / 1000.0
                ), StatusTone.SUCCESS);
            }
        }
    }

    private boolean warnIfTimedSubtitlesLackTimestamps() {
        OutputFormat format = formatGroup.selectedValue();
        if (format != OutputFormat.SRT && format != OutputFormat.VTT) {
            return false;
        }
        boolean anyMissing = completedItems.stream().anyMatch(item -> !item.timestampsCaptured());
        if (anyMissing) {
            setStatus("Timestamps were disabled during transcription — enable Timestamps and re-transcribe for timed cues.",
                    StatusTone.WARNING);
            return true;
        }
        return false;
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
        suppressBatchComboEvent = true;
        try {
            DefaultComboBoxModel<BatchViewItem> model = new DefaultComboBoxModel<>();
            if (completedItems.size() > 1) {
                for (int i = 0; i < completedItems.size(); i++) {
                    Path file = completedItems.get(i).sourceFile();
                    model.addElement(new BatchViewItem(i, (i + 1) + ". " + file.getFileName()));
                }
                model.addElement(new BatchViewItem(-1, "All files (" + completedItems.size() + " combined)"));
                batchViewCombo.setModel(model);
                batchViewCombo.setSelectedIndex(0);
                batchSelectorWrap.setVisible(true);
                batchSaveWrap.setVisible(true);
            } else {
                batchViewCombo.setModel(model);
                batchSelectorWrap.setVisible(false);
                batchSaveWrap.setVisible(false);
            }
        } finally {
            suppressBatchComboEvent = false;
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
            CompletedItem item = completedItems.get(selectedIndex);
            String edited = item.editedByFormat().get(format);
            if (edited != null) {
                rendered = edited;
            } else {
                rendered = defaultRenderedItem(item, format);
                item.savedBaselineByFormat().put(format, rendered);
            }
        } else {
            String editedCombined = combinedBatchEdits.get(format);
            if (editedCombined != null) {
                rendered = editedCombined;
            } else {
                rendered = defaultRenderedCombined(format);
                combinedSavedBaselines.put(format, rendered);
            }
        }

        setProgrammaticText(rendered);
        recomputeUnsavedManualEdits();
        transcriptArea.setCaretPosition(0);
        warnIfTimedSubtitlesLackTimestamps();
        updateActionButtons();
    }

    private String defaultRenderedItem(CompletedItem item, OutputFormat format) {
        boolean includeTimestamps = item.timestampsCaptured()
                && (format == OutputFormat.SRT || format == OutputFormat.VTT || timestampsPill.isSelected());
        return TranscriptionExporter.render(item.result(), format, includeTimestamps).stripTrailing();
    }

    private String defaultRenderedCombined(OutputFormat format) {
        boolean includeTimestamps = completedItems.stream().allMatch(CompletedItem::timestampsCaptured)
                && (format == OutputFormat.SRT || format == OutputFormat.VTT || timestampsPill.isSelected());
        List<TranscriptionExporter.BatchEntry> entries = completedItems.stream()
                .map(item -> new TranscriptionExporter.BatchEntry(
                        item.sourceFile().getFileName().toString(),
                        item.result()
                ))
                .toList();
        return TranscriptionExporter.renderBatch(entries, format, includeTimestamps).stripTrailing();
    }

    private void handleTranscriptionError(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = cause.getMessage();
        if ("Canceled.".equals(message)) {
            setStatus("Canceled.", StatusTone.WARNING);
            return;
        }
        if (message == null || message.isBlank()) {
            message = "Something went wrong. Try another file.";
        }
        setStatus(message.lines().findFirst().orElse("Something went wrong. Try another file."), StatusTone.WARNING);
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
        progressBar.setVisible(working);
        cancelButton.setVisible(working);
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
        boolean canCopy = !transcribing && hasText;
        boolean canClear = !transcribing && (hasText || !completedItems.isEmpty());
        boolean canSave = !transcribing && hasText;
        boolean canSaveBatch = !transcribing && completedItems.size() > 1;

        copyButton.setEnabled(canCopy);
        clearButton.setEnabled(canClear);
        saveButton.setEnabled(canSave);
        saveAllButton.setEnabled(canSaveBatch);

        if (menuOpenItem != null) {
            menuOpenItem.setEnabled(!transcribing);
            menuModelsItem.setEnabled(!transcribing);
            menuSaveItem.setEnabled(canSave);
            menuSaveBatchItem.setEnabled(canSaveBatch);
            menuCopyItem.setEnabled(canCopy);
            menuClearItem.setEnabled(canClear);
            for (JMenuItem fmtItem : menuFormatItems) {
                fmtItem.setEnabled(!transcribing);
            }
        }
    }

    private void setControlsBusy(boolean busy) {
        dropZone.setEnabled(!busy);
        manageModelsButton.setEnabled(!busy);
        modelCombo.setEnabled(!busy && modelCombo.getItemCount() > 0);
        beamCombo.setEnabled(!busy);
        promptField.setEnabled(!busy);
        OutputFormat format = formatGroup.selectedValue();
        timestampsPill.setEnabled(!busy && format != OutputFormat.SRT && format != OutputFormat.VTT);
        formatGroup.setEnabled(!busy);
        transcriptArea.setEditable(!busy);
        batchViewCombo.setEnabled(!busy);
        syncModelCapabilities();
    }

    private void handleDownloadState(ModelDownloadState state) {
        if (state.status() == ModelDownloadStatus.SUCCEEDED) {
            setStatus(state.message(), StatusTone.SUCCESS);
            preloadIfUseful(state.descriptor());
        } else if (state.status() == ModelDownloadStatus.FAILED) {
            progressBar.setVisible(false);
            setStatus(state.descriptor().displayName() + ": " + state.errorMessage(), StatusTone.WARNING);
        } else if (state.status() == ModelDownloadStatus.CANCELED) {
            progressBar.setVisible(false);
            setStatus("Canceled " + state.descriptor().displayName() + " download.", StatusTone.WARNING);
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
            cancelButton.setVisible(false);
            return;
        }
        ModelDownloadState state = active.get(0);
        progressBar.setVisible(true);
        progressBar.setValue(state.percent());
        cancelButton.setVisible(true);
        cancelButton.setEnabled(true);
        setStatus("Downloading " + state.descriptor().displayName() + " (" + state.percent() + "%)  ·  " + state.message(),
                StatusTone.ACCENT);
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
                setStatus(item.descriptor().displayName() + " is still downloading.", StatusTone.WARNING);
            } else if (item.state() != null && item.state().status() == ModelDownloadStatus.FAILED) {
                setStatus(item.descriptor().displayName() + ": " + item.state().errorMessage(), StatusTone.WARNING);
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
        setStatus("Copied " + formatGroup.selectedValue().name() + " output to clipboard.", StatusTone.SUCCESS);
    }

    private void clearTranscript() {
        if (transcribing) {
            return;
        }
        activeRunGeneration++;
        completedItems.clear();
        combinedBatchEdits.clear();
        combinedSavedBaselines.clear();
        hasUnsavedManualEdits = false;
        batchSelectorWrap.setVisible(false);
        batchSaveWrap.setVisible(false);
        setProgrammaticText("");
        setStatus("Ready.", StatusTone.SUCCESS);
        updateActionButtons();
    }

    private void saveTranscript() {
        String text = transcriptArea.getText();
        if (text.isBlank() || transcribing) {
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
        if (choice != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }
        Path target = ensureFormatExtension(chooser.getSelectedFile().toPath(), format);
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
            if (!completedItems.isEmpty()) {
                BatchViewItem viewItem = (BatchViewItem) batchViewCombo.getSelectedItem();
                int selectedIndex = viewItem == null ? (completedItems.size() == 1 ? 0 : -1) : viewItem.index();
                if (selectedIndex >= 0 && selectedIndex < completedItems.size()) {
                    CompletedItem item = completedItems.get(selectedIndex);
                    item.savedBaselineByFormat().put(format, text);
                } else {
                    combinedSavedBaselines.put(format, text);
                }
                recomputeUnsavedManualEdits();
            } else {
                hasUnsavedManualEdits = false;
            }
            setStatus("Saved to " + target.toAbsolutePath().normalize(), StatusTone.SUCCESS);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save file. Check folder permissions and try again.",
                    "JWhisper", JOptionPane.ERROR_MESSAGE, AppIcons.hiDpiIcon(48));
        }
    }

    private static Path ensureFormatExtension(Path rawPath, OutputFormat format) {
        String fileName = rawPath.getFileName().toString();
        String expectedSuffix = "." + format.extension();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(expectedSuffix)) {
            return rawPath;
        }
        return rawPath.resolveSibling(fileName + expectedSuffix);
    }

    private void saveBatchToDirectory() {
        if (completedItems.isEmpty() || transcribing) {
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
                String edited = item.editedByFormat().get(format);
                String rendered;
                if (edited != null) {
                    rendered = edited.endsWith("\n") ? edited : edited + System.lineSeparator();
                } else {
                    boolean includeTimestamps = item.timestampsCaptured()
                            && (format == OutputFormat.SRT || format == OutputFormat.VTT || timestampsPill.isSelected());
                    rendered = TranscriptionExporter.render(item.result(), format, includeTimestamps);
                }
                String stem = fileStem(item.sourceFile());
                Path candidate = dir.resolve(stem + "." + format.extension()).toAbsolutePath().normalize();
                int suffix = 2;
                while (true) {
                    while (Files.exists(candidate) || !written.add(candidate)) {
                        candidate = dir.resolve(stem + "-" + suffix + "." + format.extension()).toAbsolutePath().normalize();
                        suffix++;
                    }
                    try {
                        Files.writeString(
                                candidate,
                                rendered,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE
                        );
                        break;
                    } catch (FileAlreadyExistsException ignored) {
                        candidate = dir.resolve(stem + "-" + suffix + "." + format.extension()).toAbsolutePath().normalize();
                        suffix++;
                    }
                }
                if (edited != null) {
                    item.savedBaselineByFormat().put(format, edited);
                }
            }
            recomputeUnsavedManualEdits();
            setStatus("Saved " + written.size() + " ." + format.extension() + " files to " + dir.toAbsolutePath().normalize(),
                    StatusTone.SUCCESS);
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
        if (completedItems.size() > 1) {
            Path firstParent = completedItems.get(0).sourceFile().getParent();
            if (firstParent != null) {
                return firstParent.resolve("batch-transcripts." + format.extension()).toFile();
            }
            return new File("batch-transcripts." + format.extension());
        }
        return new File("transcript." + format.extension());
    }

    private static String fileStem(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void setStatus(String message, StatusTone tone) {
        currentStatusTone = tone;
        statusLabel.setText(message);
        if (statusDot != null) {
            statusDot.setColor(colorForTone(tone));
        }
    }

    private Color colorForTone(StatusTone tone) {
        return switch (tone) {
            case SUCCESS -> theme.success();
            case ACCENT -> theme.accent();
            case WARNING -> theme.warning();
            case MUTED -> theme.muted();
        };
    }

    private static String formatRuntimeBadge(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return "CPU · LOCAL ONNX";
        }
        String lower = providerName.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("unavailable")) {
            if (lower.contains("coreml")) {
                return "COREML UNAVAILABLE";
            }
            if (lower.contains("cuda")) {
                return "CUDA UNAVAILABLE";
            }
            if (lower.contains("tensorrt")) {
                return "TENSORRT UNAVAILABLE";
            }
            if (lower.contains("rocm")) {
                return "ROCM UNAVAILABLE";
            }
            if (lower.contains("gpu")) {
                return "GPU UNAVAILABLE";
            }
            return "PROVIDER UNAVAILABLE";
        }
        if (lower.contains("coreml")) {
            return "COREML · LOCAL ONNX";
        }
        if (lower.contains("cuda")) {
            return "CUDA · LOCAL ONNX";
        }
        if (lower.contains("tensorrt")) {
            return "TENSORRT · LOCAL ONNX";
        }
        if (lower.contains("rocm")) {
            return "ROCM · LOCAL ONNX";
        }
        return "CPU · LOCAL ONNX";
    }

    private enum StatusTone {
        SUCCESS,
        ACCENT,
        WARNING,
        MUTED
    }

    private final class SwingTranscriptionListener implements TranscriptionListener {
        private final String prefix;
        private final long runGen;

        private SwingTranscriptionListener(String prefix, long runGen) {
            this.prefix = prefix;
            this.runGen = runGen;
        }

        @Override
        public void onStatus(String message) {
            SwingUtilities.invokeLater(() -> {
                if (runGen == activeRunGeneration) {
                    setStatus(prefix + message, StatusTone.ACCENT);
                }
            });
        }

        @Override
        public void onProgress(double fraction) {
            SwingUtilities.invokeLater(() -> {
                if (runGen != activeRunGeneration) {
                    return;
                }
                double overall = activeBatchTotal <= 1
                        ? fraction
                        : ((activeBatchIndex - 1) + Math.max(0.0, Math.min(1.0, fraction))) / activeBatchTotal;
                progressBar.setValue((int) Math.round(overall * 100.0));
            });
        }

        @Override
        public void onTranscriptChunk(String text) {
            SwingUtilities.invokeLater(() -> {
                if (runGen != activeRunGeneration || text == null || text.isBlank()) {
                    return;
                }
                programmaticTextUpdate = true;
                try {
                    if (!transcriptArea.getText().isBlank()) {
                        transcriptArea.append(System.lineSeparator());
                    }
                    transcriptArea.append(text);
                } finally {
                    programmaticTextUpdate = false;
                }
            });
        }
    }

    /** Subtle 3px sculpted amethyst bar matching assets/brand/wordmark.png. */
    private final class AccentUnderlineBar extends JComponent {
        private AccentUnderlineBar() {
            setFocusable(false);
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

    private record CompletedItem(
            Path sourceFile,
            TranscriptionResult result,
            boolean timestampsCaptured,
            Map<OutputFormat, String> editedByFormat,
            Map<OutputFormat, String> savedBaselineByFormat
    ) {
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
