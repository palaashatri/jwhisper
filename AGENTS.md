# jwhisper — AGENTS.md  
Minimal, cross‑platform Java Swing application for local Whisper ONNX audio‑to‑text.

---

## 1. Product Overview

**jwhisper** is a tiny, elegant desktop app that performs **local Whisper inference** using **ONNX Runtime**, with:

- Whisper ONNX model downloader (stored in `~/.jwhisper/models`)
- Drag‑and‑drop audio input + file chooser
- Local transcription using ONNX Runtime (CPU first; GPU optional later)
- Automatic dependency checks (ffmpeg, model files, ONNX runtime availability)
- macOS menu bar integration
- Minimalistic, “user is drunk” UI design
- 100% offline, cross‑platform (macOS, Linux, Windows)

---

## 2. High‑Level Architecture

```
jwhisper
 ├── UIAgent
 ├── ModelManagerAgent
 ├── AudioInputAgent
 ├── TranscriptionAgent
 ├── WhisperEngineAgent (ONNX)
 ├── DependencyAgent (ffmpeg, ORT, etc.)
 └── PlatformAgent (macOS menu bar, paths)
```

**Language:** Java 17+  
**UI:** Swing (system LAF)  
**Inference:** ONNX Runtime (Java bindings)  
**Models:** Whisper ONNX (tiny/base/small/medium/large-v3)

---

## 3. Agents

---

## 3.1 UIAgent

**Responsibilities**

- Owns the main window and all user interactions.
- Communicates with ModelManagerAgent, AudioInputAgent, TranscriptionAgent.
- Displays:
  - Model selector
  - “Manage models…” button
  - Large drop zone
  - Transcript output area
  - Progress + status messages

**Design Rules (Minimal + Drunk‑Proof)**

- One window only.
- Large click targets.
- No nested menus.
- No advanced settings.
- Clear, friendly language:
  - “Drop audio here”
  - “Choose file…”
  - “Download model…”

**macOS‑specific**

- Use system menu bar (`apple.laf.useScreenMenuBar=true`)
- Provide:
  - About jwhisper
  - Preferences (if needed)
  - Quit jwhisper

---

## 3.2 ModelManagerAgent

**Responsibilities**

- Manage Whisper ONNX models in `~/.jwhisper/models`.
- Maintain `models.json` metadata.
- Download models from official ONNX Whisper releases.
- Validate:
  - File size
  - Hash (if available)
  - ONNX loadability

**UI Integration**

- “Manage models…” dialog:
  - List installed models
  - Download new model
  - Delete model
  - Set default model

**Drunk‑Proof Rules**

- Never allow changing the base directory.
- Show a single progress bar during download.
- On failure, show:
  - “Download failed. Try again.”

---

## 3.3 AudioInputAgent

**Responsibilities**

- Handle drag & drop.
- Handle file chooser.
- Validate audio formats:
  - `.wav`, `.mp3`, `.m4a`, `.flac`, `.ogg`, `.aac`
- Normalize into `AudioJob`.

**Drunk‑Proof Rules**

- If multiple files dropped → use first file, show note.
- If invalid file → show:
  - “Unsupported audio file. Try a WAV or MP3.”

---

## 3.4 TranscriptionAgent

**Responsibilities**

- Orchestrate transcription using WhisperEngineAgent.
- Provide progress updates.
- Handle cancellation.
- Return final transcript.

**Flow**

1. Ensure model is selected.
2. Ensure dependencies are satisfied.
3. Create `TranscriptionJob`.
4. Call `WhisperEngineAgent.transcribe(job, listener)`.
5. Stream progress to UIAgent.

**Drunk‑Proof Rules**

- One progress bar only.
- Clear status text:
  - “Transcribing… this may take a moment.”
- On error:
  - “Something went wrong. Try another file.”

---

## 3.5 WhisperEngineAgent (ONNX)

**Responsibilities**

- Load Whisper ONNX models.
- Run inference using ONNX Runtime Java API.
- Provide streaming callbacks.

**Implementation Notes**

- Use ONNX Runtime Java bindings:
  - CPU EP by default
  - GPU EP optional (future)
- Load model from:
  - `~/.jwhisper/models/<model-id>/model.onnx`
- Provide:
  - `loadModel(ModelDescriptor)`
  - `transcribe(TranscriptionJob, Listener)`
  - `cancel()`

**Drunk‑Proof Rules**

- If ONNX Runtime missing:
  - “ONNX Runtime not available. Reinstall jwhisper.”
- If model corrupted:
  - “Model file is invalid. Delete and re-download.”

---

## 3.6 DependencyAgent

**Responsibilities**

Check and validate all runtime dependencies on startup:

### 1. **ffmpeg**
Required for:
- Audio decoding
- Resampling
- Converting to Whisper‑friendly PCM

**Check:**
- Run `ffmpeg -version`
- If missing:
  - Show dialog:
    - “ffmpeg is required. Install it and restart jwhisper.”
  - Provide platform‑specific hints:
    - macOS: `brew install ffmpeg`
    - Linux: distro package
    - Windows: link to static build

### 2. **ONNX Runtime Java**
- Ensure `onnxruntime.jar` is on classpath.
- Ensure native libs load successfully.

### 3. **Model directory**
- Ensure `~/.jwhisper/models` exists.
- Ensure permissions are correct.

### 4. **Disk space**
- Check free space before downloading models.

**Drunk‑Proof Rules**

- Never show stack traces.
- Always show one clear sentence describing the problem.

---

## 3.7 PlatformAgent

**Responsibilities**

- macOS menu bar integration.
- System LAF selection.
- Path resolution.
- OS‑specific quirks.

**macOS**

- `apple.laf.useScreenMenuBar=true`
- Set app name:
  - `System.setProperty("com.apple.mrj.application.apple.menu.about.name", "jwhisper")`

**Cross‑Platform**

- Use `Paths.get(System.getProperty("user.home"), ".jwhisper")`
- Ensure directories exist.

---

## 4. User Flows

---

### 4.1 First Run

1. App starts.
2. DependencyAgent checks:
   - ffmpeg
   - ONNX Runtime
   - Model directory
3. If no models:
   - Show message:
     - “No models installed.”
   - Button:
     - “Download a model…”

---

### 4.2 Normal Transcription Flow

1. User opens jwhisper.
2. User drops audio file OR clicks “Choose file…”.
3. AudioInputAgent validates file.
4. TranscriptionAgent starts job.
5. UI shows progress.
6. Transcript appears.
7. User clicks “Copy text” or “Save as file…”.

---

## 5. UI Guidelines

**Minimalistic**

- White/neutral background.
- One column layout.
- Large drop zone.
- Large buttons.
- No icons unless absolutely necessary.

**Text**

- Friendly, simple, human.
- Avoid technical jargon.

**Accessibility**

- Respect system font scaling.
- High contrast.
- Logical tab order.

---

## 6. Implementation Checklist

### Core
- [ ] ONNX Runtime integration
- [ ] Whisper ONNX loader
- [ ] Transcription pipeline
- [ ] ffmpeg wrapper (Java process or JNI)

### UI
- [ ] Main window
- [ ] Drop zone
- [ ] File chooser
- [ ] Transcript area
- [ ] Model manager dialog

### Platform
- [ ] macOS menu bar
- [ ] Dependency checks
- [ ] Path setup

---

## 7. Non‑Goals (v1)

- No GPU acceleration (optional later)
- No multi‑file batch processing
- No audio recording UI
- No advanced Whisper settings
- No plugin system

---

## 8. Future Extensions

- GPU EP (CUDA, CoreML, DirectML)
- Real‑time microphone transcription
- Segment timeline view
- Export to SRT/VTT
- Multi‑file batch mode
- Plugin architecture

---
