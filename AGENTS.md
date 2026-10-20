# AGENTS.md — JWhisper

JWhisper is a local-first Whisper speech-to-text runtime for the JVM (Java 17+, ONNX Runtime) with a simple Swing desktop app and a headless CLI. The goal is a reusable, correct, fast Java transcription engine; the desktop app is its reference client.

What "production-ready" means, and the evidence recorded so far, is in [`docs/READINESS.md`](docs/READINESS.md). This file tells agents how to work in the repository.

## Current state (2026-10-20)

- PR #1 is merged: FFT preprocessing, KV-cache decoding, beam search, temperature fallback, timestamp-based long-form seeking, pinned and SHA-256-verified models, a headless CLI, and CI on Linux/macOS/Windows with an end-to-end JFK transcription gate.
- **Biggest gap:** the CLI and the desktop app both call the engine with `TranscriptionOptions.defaults()`. Language, translate, beam size, timestamps and the SRT/VTT/JSON exporters exist in the code but no user can reach them. Wire options through before adding new engine features.
- No Gradle wrapper is checked in; the CI reference audio is fetched from `openai/whisper` `main` (unpinned).

## Commands

Requirements: JDK 17+, Gradle (no wrapper is checked in yet), `ffmpeg` on `PATH`.

```bash
gradle -PjwhisperRuntime=cpu clean test fatJar          # what CI runs on Linux/macOS/Windows (JDK 17 and 25)
gradle -PjwhisperRuntime=cpu cli --args="--help"
gradle -PjwhisperRuntime=cpu cli --args="clip.flac --model tiny.en --download-model --output out.txt"
./run.sh [run|build|test|jar|clean]                      # picks the apple runtime on Apple Silicon, cpu elsewhere
```

`-PjwhisperRuntime` selects the ONNX Runtime artifact: `cpu` (default), `apple` (CoreML-oriented build), `gpu` (`onnxruntime_gpu`, CUDA). Artifacts go to `build/libs/`.

End-to-end check (the CI reference gate): transcribe OpenAI's `tests/jfk.flac` with `tiny.en` and confirm the expected phrases appear.

## Layout (`src/main/java/com/jwhisper/`)

| Package | Responsibility |
|---|---|
| `whisper/` | Engine: feature extraction (FFT log-mel), tokenizer, ONNX encoder/decoder with KV cache, greedy/beam decoding, temperature fallback, timestamps, execution-provider config |
| `transcribe/` | Public API: options, jobs, listener (progress/cancel), structured results, segments, timings |
| `model/` | Catalog of pinned model revisions, download (resumable, SHA-256 verified), store, install state |
| `audio/` | ffmpeg decoding to 16 kHz mono, input validation |
| `export/` | TXT, JSON, SRT, VTT |
| `eval/` | Word error rate |
| `cli/`, `ui/` | CLI and Swing clients |
| `deps/`, `platform/` | Dependency checks (ffmpeg, ORT), paths, OS integration, build profile |

## Architecture rules

- `whisper/`, `transcribe/`, `model/`, `audio/`, `export/` must not import Swing or anything from `ui/`. The CLI and desktop app are both clients of the same core API.
- Never block the Swing EDT with inference, decoding or downloads.
- Progress and cancellation are part of the API. Cancelling must stop native ONNX inference, not just stop reading results.
- Follow OpenAI Whisper's reference behaviour for preprocessing, special tokens, timestamp rules, fallback thresholds and long-form seeking. Don't invent simplified behaviour where the reference has an explicit rule.
- Use a real FFT, reuse ONNX sessions, keep the encoder output for the whole decode, and use the KV cache. Optimise only what a benchmark shows is slow.

## Model integrity

- Every catalog entry is pinned to an immutable repository revision, with expected sizes and SHA-256 hashes where trusted hashes exist.
- Downloads are atomic and resumable; a corrupt or incompatible model never becomes selectable.
- Adding a model means adding its pinned revision, file list, hashes, licence/source and runtime compatibility to the catalog, plus a test.

## Honesty rules

- README describes only behaviour that works today.
- Don't claim GPU/CoreML acceleration because a provider is listed or a variant compiles; it needs a successful run on real hardware and a benchmark.
- Performance claims need a reproducible command and its output.
- Readiness points in `docs/READINESS.md` are awarded only with linked evidence (passing test, CI run, benchmark).
- No Android/iOS apps in this repository; keep core APIs free of desktop assumptions so other front ends stay possible.

## Git workflow and done

- Branch and open a PR unless the owner explicitly asks for a direct push to `main`.
- Before pushing: `gradle -PjwhisperRuntime=cpu clean test fatJar` passes and, for engine changes, the JFK end-to-end transcription still matches.
- New decoding or preprocessing behaviour comes with tests against reference values; new export formats with golden-file tests.
