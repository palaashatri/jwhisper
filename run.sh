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

if [[ -x "./gradlew" ]]; then
  GRADLE_CMD="./gradlew"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
else
  echo "Gradle is required. Ensure ./gradlew is executable or install Gradle."
  exit 1
fi

case "${1:-run}" in
  run)
    "$GRADLE_CMD" "-PjwhisperRuntime=$runtime" run
    ;;
  build)
    "$GRADLE_CMD" "-PjwhisperRuntime=$runtime" build
    ;;
  test)
    "$GRADLE_CMD" "-PjwhisperRuntime=$runtime" test
    ;;
  jar)
    "$GRADLE_CMD" "-PjwhisperRuntime=$runtime" clean fatJar
    ;;
  package)
    "$GRADLE_CMD" "-PjwhisperRuntime=$runtime" packageAppImage
    ;;
  clean)
    "$GRADLE_CMD" "-PjwhisperRuntime=$runtime" clean build
    ;;
  *)
    echo "Usage: ./run.sh [run|build|test|jar|package|clean]"
    echo "Set JWHISPER_RUNTIME=cpu, apple, or gpu to choose the ONNX Runtime build."
    exit 1
    ;;
esac
