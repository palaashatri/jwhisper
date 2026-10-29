# JWhisper

JWhisper is a Java 17+ local Whisper transcription runtime built on ONNX Runtime. It ships a Swing desktop application and a headless CLI, both powered by a UI-independent JVM transcription core.

<p align="center">
  <img width="320" alt="JWhisper wordmark" src="assets/brand/wordmark.png" />
</p>

Audio stays local during transcription. Model files are downloaded once into the user's JWhisper model store (`~/.jwhisper/models`).

## Screenshots

<p align="center">
  <img width="860" alt="JWhisper desktop app in Obsidian Dark theme showing SRT subtitle transcription" src="assets/screenshots/desktop-dark-srt.png" />
</p>

<p align="center">
  <img width="424" alt="JWhisper desktop app in Pearl Light theme" src="assets/screenshots/desktop-light-txt.png" />
  <img width="424" alt="JWhisper desktop app batch transcription and JSON export" src="assets/screenshots/desktop-dark-batch.png" />
</p>

<p align="center">
  <img width="680" alt="JWhisper Whisper Model Store dialog" src="assets/screenshots/model-manager-dark.png" />
</p>

## Current Features

- Local Whisper ONNX transcription (greedy and beam-search decoding, temperature fallback, timestamp-based long-form seeking, initial prompts)
- Language selection / auto-detection and `transcribe` / `translate` (to English) tasks in both CLI and desktop UI, with English-only model guards
- Export formats: `TXT`, `SRT`, `VTT`, and `JSON` (live format switcher with edit preservation in the desktop UI; `--format` or automatic extension inference in the CLI)
- Single-file and batch multi-file / folder transcription in both CLI (`--output-dir`) and desktop UI (queue with per-file and combined batch views plus `Save all...`)
- Audio and video input (`wav`, `mp3`, `m4a`, `flac`, `ogg`, `aac`, `opus`, `wma`, `aiff`, `aif`, `caf`, `mp4`, `mkv`, `mov`, `webm`, `avi`, `m4v`) through `ffmpeg`
- Automatic first-run `tiny.en` setup in the desktop app
- Revision-pinned model downloads with cancellable/resumable partial downloads and SHA-256 verification
- Model manager dialog with background downloads, cancellation, delete, and default-model selection
- Obsidian Dark and Pearl Light brand themes (`assets/brand/palette.json`) with persisted theme toggle, macOS appearance detection, and full keyboard navigation (`Cmd/Ctrl+O`, `Cmd/Ctrl+,`, `Cmd/Ctrl+S`, `Cmd/Ctrl+1..4`, `Cmd/Ctrl+Shift+T`, `Esc`)
- Cross-platform sculpted waveform application icons across Swing windows, OS taskbar/dock, About dialog, and `jpackage` desktop app bundles
- Cooperative plus native ONNX Runtime cancellation
- CPU runtime, Apple/CoreML-oriented runtime variant, and CUDA-oriented GPU runtime variant, plus `--diagnostics` and `--list-models`
- Checked-in Gradle 9.8.2 wrapper (`./gradlew` / `gradlew.bat`)
- CI builds/tests on Windows, Linux, and macOS with JDK 17 and JDK 25
- End-to-end reference transcription gate using OpenAI Whisper's pinned `tests/jfk.flac` fixture (SHA-256 verified, TXT + SRT)

## Requirements

- Java 17 or newer (`jpackage` included in JDK 17+ is used when building desktop app images)
- `ffmpeg` on `PATH`

The Gradle wrapper (`./gradlew`) is checked into the repository, so a global Gradle installation is not required.

On macOS:

```sh
brew install ffmpeg
```

## Desktop App

```sh
./run.sh
```

On Apple Silicon Macs, `run.sh` defaults to the Apple runtime variant. On other machines it defaults to CPU.

Force a build/runtime variant:

```sh
JWHISPER_RUNTIME=cpu ./run.sh
JWHISPER_RUNTIME=apple ./run.sh
JWHISPER_RUNTIME=gpu ./run.sh
```

Force a UI theme (otherwise JWhisper remembers your last theme toggle or follows macOS dark/light appearance):

```sh
JWHISPER_THEME=dark ./run.sh
JWHISPER_THEME=light ./run.sh
```

## CLI

Inspect environment diagnostics and catalog models:

```sh
./gradlew "-PjwhisperRuntime=cpu" cli --args="--diagnostics"
./gradlew "-PjwhisperRuntime=cpu" cli --args="--list-models"
```

Transcribe with an installed model:

```sh
./gradlew "-PjwhisperRuntime=cpu" cli --args="recording.mp3 --model tiny.en"
```

Download the selected model if missing and write SRT subtitles (format is inferred automatically from `.srt`, `.vtt`, `.json`, or `.txt`, or set explicitly with `--format`):

```sh
./gradlew "-PjwhisperRuntime=cpu" cli --args="interview.mp4 --model tiny.en --download-model --output interview.srt"
```

Multilingual transcription, translation to English, beam search, timestamps, and initial prompt:

```sh
./gradlew "-PjwhisperRuntime=cpu" cli --args="speech.mp3 --model medium --language fr --translate --beam-size 3 --format vtt --output speech.en.vtt"
./gradlew "-PjwhisperRuntime=cpu" cli --args="meeting.m4a --model base.en --prompt \"JWhisper, ONNX Runtime\" --format json --timestamps --output meeting.json"
```

Batch transcribe multiple files or an entire directory tree into an output directory:

```sh
./gradlew "-PjwhisperRuntime=cpu" cli --args="recordings/ --model tiny.en --format srt --output-dir subtitles/"
```

Run `--help` for all flags (`--language`, `--task`, `--translate`, `--beam-size`, `--timestamps`, `--no-timestamps`, `--prompt`, `--format`, `--output`, `--output-dir`, `--provider`, `--device-id`, `--provider-fallback`, `--no-provider-fallback`, `--list-models`, `--diagnostics`, `--quiet`).

The CLI accepts common audio formats plus `mp4`, `mkv`, `mov`, `webm`, `avi`, and `m4v`. `ffmpeg` extracts and resamples the media audio track to Whisper's mono 16 kHz input.

## Demo Media

The repository deliberately does not commit large third-party media files. The demo script fetches/prepares them in `build/demo-media`.

Run both demos:

```sh
bash scripts/run-media-demos.sh all
```

Run just the no-speech regression:

```sh
bash scripts/run-media-demos.sh bbb
```

**Big Buck Bunny** is used as a hallucination/no-speech regression: JWhisper should not manufacture a substantial transcript from its non-dialogue soundtrack.

Run the spoken-documentary demo:

```sh
bash scripts/run-media-demos.sh rip
```

**RiP!: A Remix Manifesto** is used as a realistic documentary stress test with narration/interview speech. The script prepares a five-minute sample and transcribes it locally.

For a deterministic, small correctness gate, GitHub Actions also transcribes OpenAI Whisper's `tests/jfk.flac` fixture and requires the expected reference phrases.

## Models

Models live in:

```sh
~/.jwhisper/models
```

Built-in catalog models (`--list-models`):

- `tiny.en` — Fast English-only model (285 MB)
- `base.en` — Balanced English-only model (294 MB)
- `small.en` — More accurate English-only model (970 MB)
- `medium` — High-quality multilingual transcription and English translation (3.2 GB)
- `large-v3` — Full multilingual Whisper Large v3 (6.5 GB)
- `large-v3-turbo` — High-throughput multilingual Whisper Large v3 Turbo (4.1 GB)

Catalog models are pinned to immutable Hugging Face revisions. Interrupted `.download` files are retained so the next attempt can resume when the server supports HTTP Range requests. Files with a catalog SHA-256 must pass integrity verification before installation completes.

Because revision tracking is part of installation metadata, older unpinned installations are treated as stale and may need to be downloaded again.

## Execution Providers

JWhisper resolves execution providers at runtime, but actual availability depends on the native ONNX Runtime libraries packaged or installed for that artifact and platform.

- CPU variant: CPU fallback is always the baseline.
- Apple variant: prefers CoreML when the runtime exposes it, otherwise CPU.
- GPU variant: uses the ONNX Runtime GPU Java artifact and prefers CUDA when available.

Low-level provider overrides remain available for development/testing:

```sh
JWHISPER_PROVIDER=cpu ./run.sh
JWHISPER_PROVIDER=coreml ./run.sh
JWHISPER_PROVIDER=cuda JWHISPER_DEVICE_ID=0 ./run.sh
JWHISPER_PROVIDER=tensorrt JWHISPER_DEVICE_ID=0 ./run.sh
JWHISPER_PROVIDER=rocm JWHISPER_DEVICE_ID=0 ./run.sh
```

An override only works if that execution provider is actually present in the native ONNX Runtime build. Explicit provider requests do not silently fall back unless enabled:

```sh
JWHISPER_PROVIDER=cuda JWHISPER_PROVIDER_FALLBACK=true ./run.sh
```

## Roadmap

In priority order — each is something a user would notice:

1. **No-setup signed installers:** `jpackage` platform installers (DMG/MSI/DEB/RPM — `packageAppImage` currently builds unsigned self-contained app images) with `ffmpeg` bundled or auto-installed and macOS/Windows code signing and notarization.
2. **Live dictation:** transcribe from the microphone in near real time.
3. **Proven acceleration:** benchmark CPU vs CoreML vs CUDA (real-time factor, tokens/s, RSS and accelerator memory) on real hardware and publish reproducible numbers.
4. **A reusable `jwhisper-core` library** published as a Maven artifact for other JVM apps.

## Build, Test, and Package

```sh
./run.sh build
./run.sh test
./run.sh jar
./run.sh package
./run.sh clean
```

Build runnable fat jars directly with the Gradle wrapper:

```sh
./gradlew "-PjwhisperRuntime=cpu" fatJar
./gradlew "-PjwhisperRuntime=apple" fatJar
./gradlew "-PjwhisperRuntime=gpu" fatJar
```

Build a self-contained native desktop app image (`build/package/JWhisper.app` on macOS, `build/package/JWhisper` on Linux/Windows) bundled with the JWhisper application icon:

```sh
./gradlew "-PjwhisperRuntime=cpu" packageAppImage
```

Jar artifacts are written under `build/libs/` and packaged desktop app images under `build/package/`.

## CI

`.github/workflows/build-jars.yml` currently validates:

- CPU: Windows, Linux, macOS
- JVM compatibility: JDK 17 and JDK 25
- Apple runtime variant build/tests
- CUDA-oriented GPU runtime variant build/tests
- CLI startup on the desktop CPU matrix
- End-to-end `tiny.en` transcription of OpenAI Whisper's JFK reference audio (TXT + timed SRT validation)

Successful compilation of an accelerated variant is not treated as proof of accelerated inference. Real provider execution and performance benchmarking on target hardware remain a separate validation gate.

## Future SLOPOS desktop, PARAMA and low-memory targets

**Planned, not implemented:** JWhisper is an independently authored candidate for an optional SLOPOS Dictation transcription provider (never an always-on background service). Target supported **Linux/SLOPOS** desktop integration first; any native **PARAMA/SURYA** port depends on real kernel, runtime, graphics/input, filesystem, and native-library support. The aim is high-quality useful work on constrained hardware, not a claim that every heavy workload fits.

**Pi Zero 2 W (512 MB, quad Cortex-A53) profile:** Offer a small/quantized model only where its actual footprint and CPU kernel support are validated; stream and chunk audio, cap decoder state/temp arrays, queue length, CPU threads, and previews. The model manager must preflight weights + ONNX Runtime + Java heap/native allocations against remaining RAM, reject unsupported models before OOM, and never claim a 512 MB full-size model fits. Auto-start no model and never upload audio without explicit opt-in. Validate actual app/runtime compatibility and performance on the board; **no verified Zero 2 W or native PARAMA support is claimed here**. Installation should be optional, with no eager services or model downloads in the lightweight SLOPOS base image.
