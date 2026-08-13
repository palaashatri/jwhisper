#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${JWHISPER_DEMO_DIR:-${ROOT_DIR}/build/demo-media}"
MODEL="${JWHISPER_DEMO_MODEL:-tiny.en}"
mkdir -p "${WORK_DIR}"

BBB_URL="https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4"
RIP_URL="https://archive.org/download/rip-a-remix-manifesto-8040182/RIP%20%EF%BC%9A%20A%20Remix%20Manifesto%20%5B8040182%5D.mp4"

run_cli() {
  local input="$1"
  local output="$2"
  (cd "${ROOT_DIR}" && gradle -q classes)
  local cp
  cp="$(cd "${ROOT_DIR}" && gradle -q printRuntimeClasspath)"
  java -cp "${cp}" com.jwhisper.cli.JWhisperCli \
    "${input}" \
    --model "${MODEL}" \
    --download-model \
    --output "${output}"
}

run_bbb() {
  local video="${WORK_DIR}/big-buck-bunny.mp4"
  local transcript="${WORK_DIR}/big-buck-bunny.txt"
  if [[ ! -f "${video}" ]]; then
    echo "Downloading Big Buck Bunny demo video..." >&2
    curl --fail --location --retry 3 --output "${video}.download" "${BBB_URL}"
    mv "${video}.download" "${video}"
  fi

  echo "Running no-speech / hallucination-resistance demo..." >&2
  run_cli "${video}" "${transcript}"
  echo "Big Buck Bunny transcript: ${transcript}" >&2

  local words
  words="$(wc -w < "${transcript}" | tr -d ' ')"
  echo "Detected transcript words: ${words}" >&2
  if (( words > 20 )); then
    echo "WARNING: Big Buck Bunny produced substantial text. Treat this as a hallucination-regression failure." >&2
    return 1
  fi
}

run_rip() {
  local clip="${WORK_DIR}/rip-remix-manifesto-spoken-sample.mp4"
  local transcript="${WORK_DIR}/rip-remix-manifesto-spoken-sample.txt"

  # Use a five-minute section of the openly licensed documentary rather than
  # storing a ~370 MB third-party movie in the repository. Internet Archive
  # supports HTTP access and ffmpeg normally uses range requests when seeking.
  if [[ ! -f "${clip}" ]]; then
    echo "Preparing a five-minute RiP!: A Remix Manifesto speech sample..." >&2
    ffmpeg -hide_banner -loglevel error \
      -ss 00:05:00 -t 00:05:00 \
      -i "${RIP_URL}" \
      -map 0:v:0 -map 0:a:0 \
      -c copy -movflags +faststart \
      -y "${clip}"
  fi

  echo "Running spoken-documentary transcription demo..." >&2
  run_cli "${clip}" "${transcript}"
  echo "RiP! transcript: ${transcript}" >&2

  local words
  words="$(wc -w < "${transcript}" | tr -d ' ')"
  echo "Detected transcript words: ${words}" >&2
  if (( words < 50 )); then
    echo "WARNING: The spoken documentary sample produced too little text. Treat this as a transcription-regression failure." >&2
    return 1
  fi
}

case "${1:-all}" in
  bbb|big-buck-bunny)
    run_bbb
    ;;
  rip|documentary)
    run_rip
    ;;
  all)
    run_bbb
    run_rip
    ;;
  *)
    echo "Usage: $0 [all|bbb|rip]" >&2
    exit 2
    ;;
esac
