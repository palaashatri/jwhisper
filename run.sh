#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

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
    gradle build
    gradle run
    ;;
  build)
    gradle build
    ;;
  test)
    gradle test
    ;;
  clean)
    gradle clean build
    ;;
  *)
    echo "Usage: ./run.sh [run|build|test|clean]"
    exit 1
    ;;
esac
