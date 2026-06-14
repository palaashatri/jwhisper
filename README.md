# jwhisper

jwhisper is a small Java 17 Swing desktop app for local Whisper ONNX audio-to-text.

It is designed to be simple on purpose: one window, drag-and-drop audio, local model storage, and offline transcription through ONNX Runtime.

## Features

- Local Whisper ONNX transcription
- Automatic first-run `tiny.en` setup
- Model manager for optional larger models, with downloads that keep running if the manager window closes
- Drag-and-drop audio and file chooser
- Transcript copy/save actions
- Light and dark UI themes with macOS appearance detection
- Main-window progress for model downloads and transcription
- macOS menu bar integration
- CPU, Apple Silicon/CoreML, CUDA, ROCm, and TensorRT provider selection

## Requirements

- Java 17 or newer
- Gradle
- ffmpeg on `PATH`

On macOS:

```sh
brew install ffmpeg gradle
```

## Run

```sh
./run.sh
```

On Apple Silicon Macs, `run.sh` defaults to the Apple/CoreML runtime variant. On other machines it defaults to CPU.

Force a runtime variant:

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

Without an override, jwhisper follows macOS light/dark appearance when running on macOS.

## Hardware Providers

Provider selection is automatic by default:

- Apple Silicon build: prefers CoreML, then CPU fallback
- GPU build: prefers CUDA, then ROCm, then TensorRT, then CPU fallback
- CPU build: uses automatic provider detection with CPU fallback

Force a provider:

```sh
JWHISPER_PROVIDER=cpu ./run.sh
JWHISPER_PROVIDER=coreml ./run.sh
JWHISPER_PROVIDER=cuda JWHISPER_DEVICE_ID=0 ./run.sh
JWHISPER_PROVIDER=rocm JWHISPER_DEVICE_ID=0 ./run.sh
JWHISPER_PROVIDER=tensorrt JWHISPER_DEVICE_ID=0 ./run.sh
```

By default, explicitly forced providers do not fall back silently. To allow fallback:

```sh
JWHISPER_PROVIDER=cuda JWHISPER_PROVIDER_FALLBACK=true ./run.sh
```

CUDA/ROCm/TensorRT require the matching system drivers and native libraries installed on the target machine. The GPU jar packages ONNX Runtime GPU Java bindings, but it does not install NVIDIA or AMD drivers for you.

## Models

Models live in:

```sh
~/.jwhisper/models
```

On first launch, jwhisper downloads and preloads `tiny.en`. Use **Manage models...** to install larger models.

Model downloads are owned by the main app, not the manager dialog. If you start a model download and close **Manage models...**, the download continues in the background, appears in the main model dropdown, and stays unavailable until it is ready. The main window shows model download progress while it runs.

## Build

Build and test:

```sh
./run.sh build
```

Build a runnable jar:

```sh
./run.sh jar
```

Choose a runtime variant when building:

```sh
JWHISPER_RUNTIME=apple ./run.sh jar
JWHISPER_RUNTIME=gpu ./run.sh jar
```

The runnable jar is written to:

```sh
build/libs/
```

## GitHub Actions

The workflow in `.github/workflows/build-jars.yml` builds:

- `jwhisper-apple-silicon-coreml`: Apple Silicon/CoreML runnable jar
- `jwhisper-gpu-cuda-rocm`: GPU runnable jar using ONNX Runtime GPU bindings

The GPU workflow builds the artifact on a standard Linux runner. GPU acceleration is used when that jar is run later on a machine with supported GPU drivers.

## Useful Commands

```sh
./run.sh test
./run.sh clean
gradle -PjwhisperRuntime=cpu build
gradle -PjwhisperRuntime=apple fatJar
gradle -PjwhisperRuntime=gpu fatJar
```
