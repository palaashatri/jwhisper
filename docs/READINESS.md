# JWhisper — Production Readiness Definition

> Moved from `AGENTS.md` §3 on 2026-10-20. This defines what "production-ready (100/100)" means. Record evidence for awarded points in the **Evidence ledger** at the bottom of this file (there was previously a planned `TRUTH.md`, which was never created). A point is awarded only with a link to a passing test, CI run or reproducible benchmark.

## Definition

JWhisper is 100/100 only when every mandatory gate below passes. Documentation claims must never exceed independently demonstrated behavior.

### 1 Whisper correctness — 25 points

- Log-mel preprocessing numerically matches trusted Whisper reference vectors within a documented tolerance.
- Tokenization matches reference behavior for multilingual UTF-8 input and special tokens.
- Language detection works for multilingual models.
- `transcribe` and `translate` tasks are supported.
- Timestamp token rules are implemented.
- No-speech probability is exposed and used.
- Greedy decoding is correct.
- Beam search is available and tested.
- Temperature fallback, average log probability, compression-ratio rejection, and repetition/hallucination controls are implemented.
- Previous-text conditioning and initial prompts are supported.
- Long-form seek/window behavior does not concatenate independent blind 30-second chunks.
- Segment timestamps are produced; word timestamps are supported where model/export capability permits.

### 2 Inference performance — 20 points

- No quadratic handwritten DFT/STFT path.
- Decoder uses past-key-value/KV cache after the initial decoding step.
- Encoder output is not recomputed for each generated token.
- Model sessions are reused safely.
- Cancellation terminates active native inference.
- CPU thread/session settings are explicit and benchmarked.
- Accelerator paths minimize avoidable host/device copies; I/O binding or equivalent is used where it materially improves the selected provider.
- Benchmarks report real-time factor, preprocessing time, encoder time, decode tokens/s, peak RSS, and accelerator memory where measurable.
- No performance claim is accepted without a reproducible benchmark command.

### 3 Model system — 15 points

- Supported catalog includes appropriate English and multilingual variants, including a fast high-quality modern model such as Whisper Turbo when a compatible verified ONNX export is available.
- Model revisions are immutable/pinned.
- Required files have expected sizes and cryptographic hashes or equivalent immutable integrity metadata.
- Downloads are atomic.
- Interrupted downloads can resume when the remote source supports range requests.
- Corrupt/incompatible models are rejected before becoming selectable.
- Model metadata records architecture, precision, provenance, revision, license/source information, and runtime compatibility.
- Delete/update/default-model operations cannot corrupt store state.

### 4 Product/API quality — 15 points

- Public core API is independent of Swing.
- Structured result types expose text, language, segments, timestamps, confidence/log-probability information where available, model/provider metadata, and timing data.
- Cancellation and progress are first-class API concepts.
- CLI supports file transcription, model choice, language/task selection, output format, provider selection, and diagnostics.
- Desktop UI remains simple and never blocks the EDT with inference/download work.
- Batch transcription is supported without compromising the simple single-file flow.
- Export supports TXT, JSON, SRT, and VTT.
- Errors shown to end users are actionable while diagnostic causes remain available to logs/tests.

### 5 Platform and packaging — 10 points

- CI builds and tests Linux x64, Windows x64, and macOS arm64/x64 where runners permit.
- CPU artifacts are validated on all desktop OSes.
- Accelerated variants are separated honestly according to ONNX Runtime native-provider packaging constraints.
- End-user releases do not require a globally installed Gradle.
- Production packages use `jlink`/`jpackage` or an equivalent self-contained runtime strategy.
- ffmpeg is either safely bundled according to licensing/distribution requirements or dependency installation is handled explicitly and consistently.
- macOS release path supports signing/notarization configuration; Windows release path supports signing configuration.

### 6 Tests and release engineering — 15 points

- Unit tests cover tokenizer, preprocessing, generation rules, providers, model store, download integrity, audio validation, cancellation, exporters, and core API behavior.
- Golden reference vectors cover preprocessing and tokenization.
- End-to-end ASR fixtures measure expected transcript/WER ranges.
- Long-form, silence, noisy audio, multilingual, Unicode, and malformed-model cases are covered.
- CI runs tests on every PR.
- Release workflow produces versioned artifacts and checksums.
- Dependency and license metadata are reviewable.
- This file's evidence ledger contains the only live readiness score and lists evidence for every awarded point.

## Evidence ledger

No points have been formally awarded yet. Evidence available as of 2026-10-20 (`main`, after PR #1 was merged):

- CI builds and tests CPU jars on Linux, macOS and Windows with JDK 17 and 25, plus Apple (CoreML) and GPU (CUDA) jar variants. Compiling an accelerated variant is not evidence that acceleration works.
- CI end-to-end gate: `tiny.en` transcribes OpenAI's `jfk.flac` reference clip and the expected phrases are checked.
- Not yet evidenced: WER on a real test set, multilingual/long-form/noisy fixtures, accelerated-provider execution, benchmarks (RTF, tokens/s, RSS), signed/notarized packages.
- Reachability gap (2026-10-20 audit): the engine implements language selection, `translate`, beam search, temperature fallback, timestamps and TXT/JSON/SRT/VTT export (`TranscriptionOptions`, `export/TranscriptionExporter`), but **neither the CLI nor the desktop app uses them** — both always run `TranscriptionOptions.defaults()`, and the CLI accepts only `--model`, `--download-model`, `--output` and `--quiet`. Points in §3.4 for CLI options and export formats can't be awarded until users can reach them.
- The CI reference fixture is downloaded from `openai/whisper` at `main`, not a pinned commit, so the end-to-end gate is not reproducible if that file changes.
