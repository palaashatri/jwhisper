#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

runtime="${JWHISPER_RUNTIME:-}"
if [[ -z "$runtime" ]]; then
  if [[ "$(uname -s)" == "Darwin" && "$(uname -m)" == "arm64" ]]; then
    runtime="apple"
  else
    runtime="cpu"
  fi
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java is required. Install Java 17 or newer and try again."
  exit 1
fi

if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle is required. Install it with: brew install gradle"
  exit 1
fi

case "${1:-run}" in
  run)
    gradle -PjwhisperRuntime="$runtime" build
    gradle -PjwhisperRuntime="$runtime" run
    ;;
  build)
    gradle -PjwhisperRuntime="$runtime" build
    ;;
  test)
    gradle -PjwhisperRuntime="$runtime" test
    ;;
  jar)
    gradle -PjwhisperRuntime="$runtime" clean fatJar
    ;;
  clean)
    gradle -PjwhisperRuntime="$runtime" clean build
    ;;
  *)
    echo "Usage: ./run.sh [run|build|test|jar|clean]"
    echo "Set JWHISPER_RUNTIME=cpu, apple, or gpu to choose the ONNX Runtime build."
    exit 1
    ;;
esac
