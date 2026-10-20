# JWhisper — Product Contract

JWhisper is a production-grade, local-first Whisper implementation for the JVM with a simple desktop reference application. The project is not merely a Swing GUI around ONNX Runtime: the core deliverable is a reusable Java transcription engine with correctness, performance, portability, model integrity, and release quality strong enough for real applications.

## 1. Current Scope

- Java 17+ core runtime.
- ONNX Runtime inference.
- Desktop reference application for macOS, Windows, and Linux.
- CPU plus supported accelerated execution providers.
- Local/offline transcription after model installation.
- Model download, verification, lifecycle management, and deterministic versioning.
- Text, segment, SRT, VTT, and machine-readable transcription outputs.
- Long-form audio, language detection, timestamps, translation, prompts, and robust decoding.

Mobile applications are deliberately **not** part of this branch. However, core APIs must remain UI-independent and portable enough that Android or other frontends can reuse the engine where technically appropriate. iOS must not dictate JVM-core design; a future iOS client may share model formats, test vectors, product behavior, and possibly native inference components rather than Java bytecode.

## 2. Architecture Target

The repository must converge toward these logical layers:

```text
jwhisper-core
  audio/
  model/
  inference/
  decoding/
  transcription/
  export/
  platform/

jwhisper-cli
  command-line transcription and diagnostics

jwhisper-desktop
  Swing reference application
```

A physical Gradle multi-project split is allowed when it reduces coupling and is backed by tests. Do not perform cosmetic restructuring that delays inference correctness.

## 3. 100/100 Definition

JWhisper is 100/100 only when every mandatory gate below passes. Documentation claims must never exceed independently demonstrated behavior.

### 3.1 Whisper correctness — 25 points

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

### 3.2 Inference performance — 20 points

- No quadratic handwritten DFT/STFT path.
- Decoder uses past-key-value/KV cache after the initial decoding step.
- Encoder output is not recomputed for each generated token.
- Model sessions are reused safely.
- Cancellation terminates active native inference.
- CPU thread/session settings are explicit and benchmarked.
- Accelerator paths minimize avoidable host/device copies; I/O binding or equivalent is used where it materially improves the selected provider.
- Benchmarks report real-time factor, preprocessing time, encoder time, decode tokens/s, peak RSS, and accelerator memory where measurable.
- No performance claim is accepted without a reproducible benchmark command.

### 3.3 Model system — 15 points

- Supported catalog includes appropriate English and multilingual variants, including a fast high-quality modern model such as Whisper Turbo when a compatible verified ONNX export is available.
- Model revisions are immutable/pinned.
- Required files have expected sizes and cryptographic hashes or equivalent immutable integrity metadata.
- Downloads are atomic.
- Interrupted downloads can resume when the remote source supports range requests.
- Corrupt/incompatible models are rejected before becoming selectable.
- Model metadata records architecture, precision, provenance, revision, license/source information, and runtime compatibility.
- Delete/update/default-model operations cannot corrupt store state.

### 3.4 Product/API quality — 15 points

- Public core API is independent of Swing.
- Structured result types expose text, language, segments, timestamps, confidence/log-probability information where available, model/provider metadata, and timing data.
- Cancellation and progress are first-class API concepts.
- CLI supports file transcription, model choice, language/task selection, output format, provider selection, and diagnostics.
- Desktop UI remains simple and never blocks the EDT with inference/download work.
- Batch transcription is supported without compromising the simple single-file flow.
- Export supports TXT, JSON, SRT, and VTT.
- Errors shown to end users are actionable while diagnostic causes remain available to logs/tests.

### 3.5 Platform and packaging — 10 points

- CI builds and tests Linux x64, Windows x64, and macOS arm64/x64 where runners permit.
- CPU artifacts are validated on all desktop OSes.
- Accelerated variants are separated honestly according to ONNX Runtime native-provider packaging constraints.
- End-user releases do not require a globally installed Gradle.
- Production packages use `jlink`/`jpackage` or an equivalent self-contained runtime strategy.
- ffmpeg is either safely bundled according to licensing/distribution requirements or dependency installation is handled explicitly and consistently.
- macOS release path supports signing/notarization configuration; Windows release path supports signing configuration.

### 3.6 Tests and release engineering — 15 points

- Unit tests cover tokenizer, preprocessing, generation rules, providers, model store, download integrity, audio validation, cancellation, exporters, and core API behavior.
- Golden reference vectors cover preprocessing and tokenization.
- End-to-end ASR fixtures measure expected transcript/WER ranges.
- Long-form, silence, noisy audio, multilingual, Unicode, and malformed-model cases are covered.
- CI runs tests on every PR.
- Release workflow produces versioned artifacts and checksums.
- Dependency and license metadata are reviewable.
- `TRUTH.md` contains the only live readiness score and lists evidence for every awarded point.

## 4. Mandatory Implementation Order

Work should follow dependency order rather than UI visibility:

1. Reference-correct preprocessing and measurable FFT performance.
2. Immediate native cancellation and stable session lifecycle.
3. KV-cache decoder contract and compatible pinned model exports.
4. Full Whisper decoding behavior: language, tasks, probabilities, timestamp rules, fallbacks, prompts.
5. Long-form seek/segment engine and structured results.
6. Verified/resumable model manager and modern model catalog.
7. Core API + CLI extraction.
8. Export formats and batch workflow.
9. Cross-platform packaging and provider-specific release artifacts.
10. WER/performance matrix and production release gates.

A later task may improve UI polish, but UI work must not substitute for missing inference correctness.

## 5. Reference Behavior

For algorithmic behavior, OpenAI Whisper is the primary behavioral reference. ONNX Runtime official documentation is the primary execution-provider/API reference. Model exports must be pinned to explicit repositories and revisions and tested against reference outputs.

Do not silently invent simplified Whisper behavior when the reference implementation has explicit rules.

## 6. Performance Rules

- Feature extraction must use an actual FFT implementation or an equivalent optimized graph/native path.
- Autoregressive decoding must use cached past key/value state when the selected exported model supports it.
- Do not copy complete decoder histories through the model once incremental decoding is available.
- Do not claim GPU/CoreML acceleration based only on provider enumeration; validate that representative model graphs execute successfully and benchmark them.
- Optimize measured bottlenecks only.

## 7. Mobile Design Constraint

No Android or iOS application is to be created under this branch.

Keep future portability viable by:

- separating inference/core logic from Swing;
- keeping model/result schemas frontend-neutral;
- avoiding desktop filesystem/UI assumptions inside the core API;
- defining deterministic reference fixtures that can later validate Android/iOS implementations;
- treating iOS as potentially native Swift/Objective-C++/C/C++ inference sharing behavior and assets rather than assuming a JVM runtime.

## 8. Documentation Discipline

- `AGENTS.md` is the immutable target contract for the current production effort.
- `TRUTH.md` is the live score/evidence ledger.
- `README.md` describes only working user-facing behavior.
- A checkbox or feature statement is not evidence. Tests, benchmark output, CI results, or inspectable implementation are evidence.

## 9. Completion Rule

Do not declare 100/100 until all 100 points are backed by passing evidence on the target platforms. If a feature is implemented but unvalidated, it receives no production-readiness credit until validation is recorded in `TRUTH.md`.
