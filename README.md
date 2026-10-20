# JWhisper

JWhisper is a Java 17+ local Whisper transcription project built on ONNX Runtime. It currently ships a simple Swing desktop application and a headless CLI, with the inference/core code being hardened into a reusable JVM transcription runtime.

<img width="1056" height="772" alt="JWhisper desktop application" src="https://github.com/user-attachments/assets/5ac0c99d-9440-4214-933b-324dca408659" />

Audio stays local during transcription. Model files are downloaded once into the user's JWhisper model store.

## Current Features

- Local Whisper ONNX transcription
- Audio and video input through ffmpeg
- Automatic first-run `tiny.en` setup in the desktop app
- Revision-pinned model downloads with resumable partial downloads
- SHA-256 verification for catalog files that have trusted hashes
- Model manager with background downloads
- Drag-and-drop media and file chooser
- Headless command-line transcription
- Transcript copy/save actions
- Light/dark desktop themes with macOS appearance detection
- Cooperative plus native ONNX Runtime cancellation
- CPU runtime, Apple/CoreML-oriented runtime variant, and CUDA-oriented GPU runtime variant
- CI builds/tests on Windows, Linux, and macOS with JDK 17 and JDK 25
- End-to-end reference transcription test using OpenAI Whisper's JFK fixture

The production target and the evidence required for 100/100 readiness are defined in `AGENTS.md`. The live readiness score belongs in `TRUTH.md`.

## Requirements

- Java 17 or newer
- Gradle for development builds
- ffmpeg on `PATH`

On macOS:

```sh
brew install ffmpeg gradle
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

Force a UI theme:

```sh
JWHISPER_THEME=dark ./run.sh
JWHISPER_THEME=light ./run.sh
```

## CLI

Transcribe an installed model:

```sh
gradle -PjwhisperRuntime=cpu cli --args="recording.mp3 --model tiny.en"
```

Download the selected model if it is missing and write the result to a file:

```sh
gradle -PjwhisperRuntime=cpu cli --args="interview.mp4 --model tiny.en --download-model --output transcript.txt"
```

The CLI accepts common audio formats plus `mp4`, `mkv`, `mov`, `webm`, `avi`, and `m4v`. ffmpeg extracts and resamples the media audio track to Whisper's mono 16 kHz input.

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

Catalog models are pinned to immutable Hugging Face revisions. Interrupted `.download` files are retained so the next attempt can resume when the server supports HTTP Range requests. Files with a catalog SHA-256 must pass integrity verification before installation completes.

Because revision tracking is now part of installation metadata, older unpinned installations can be treated as stale and may need to be downloaded again.

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

## Build and Test

```sh
./run.sh build
./run.sh test
./run.sh clean
```

Build runnable fat jars:

```sh
gradle -PjwhisperRuntime=cpu fatJar
gradle -PjwhisperRuntime=apple fatJar
gradle -PjwhisperRuntime=gpu fatJar
```

Artifacts are written under `build/libs/`.

## CI

`.github/workflows/build-jars.yml` currently validates:

- CPU: Windows, Linux, macOS
- JVM compatibility: JDK 17 and JDK 25
- Apple runtime variant build/tests
- CUDA-oriented GPU runtime variant build/tests
- CLI startup on the desktop CPU matrix
- End-to-end `tiny.en` transcription of OpenAI Whisper's JFK reference audio

Successful compilation of an accelerated variant is not treated as proof of accelerated inference. Real provider execution/performance validation remains a separate production gate tracked in `TRUTH.md`.
