package com.jwhisper;

import com.jwhisper.audio.AudioInputAgent;
import com.jwhisper.deps.DependencyAgent;
import com.jwhisper.deps.DependencyReport;
import com.jwhisper.model.ModelDownloadService;
import com.jwhisper.model.ModelManagerAgent;
import com.jwhisper.platform.PlatformAgent;
import com.jwhisper.transcribe.TranscriptionAgent;
import com.jwhisper.ui.MainWindow;
import com.jwhisper.ui.UiTheme;
import com.jwhisper.whisper.UnavailableWhisperEngine;
import com.jwhisper.whisper.WhisperEngineAgent;
import com.jwhisper.whisper.WhisperOnnxEngine;

import javax.swing.SwingUtilities;

public final class JWhisperApp {
    private JWhisperApp() {
    }

    public static void main(String[] args) {
        PlatformAgent.prepareMacProperties();
        SwingUtilities.invokeLater(() -> {
            PlatformAgent platformAgent = new PlatformAgent();
            platformAgent.useSystemLookAndFeel();
            UiTheme theme = UiTheme.current();
            theme.applyGlobalDefaults();
            platformAgent.installMacMenuHandlers();

            DependencyAgent dependencyAgent = new DependencyAgent(platformAgent);
            DependencyReport dependencyReport = dependencyAgent.checkStartupDependencies();
            ModelManagerAgent modelManagerAgent = new ModelManagerAgent(platformAgent.modelDirectory(), dependencyAgent);
            ModelDownloadService downloadService = new ModelDownloadService(modelManagerAgent);
            WhisperEngineAgent whisperEngineAgent = createWhisperEngine();
            TranscriptionAgent transcriptionAgent = new TranscriptionAgent(
                    modelManagerAgent,
                    dependencyReport,
                    whisperEngineAgent
            );

            MainWindow window = new MainWindow(
                    modelManagerAgent,
                    downloadService,
                    new AudioInputAgent(),
                    transcriptionAgent,
                    dependencyReport
            );
            window.setVisible(true);
            window.showStartupMessages();
        });
    }

    private static WhisperEngineAgent createWhisperEngine() {
        try {
            return new WhisperOnnxEngine();
        } catch (Throwable e) {
            return new UnavailableWhisperEngine();
        }
    }
}
