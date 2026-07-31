#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

candidate_json="$(python3 "${ROOT}/scripts/verify_main_candidate.py" --root "${ROOT}" --json)"
candidate_sha="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])' <<<"${candidate_json}")"

echo "Preparing Forest Run release evidence from main at ${candidate_sha}."
python3 "${ROOT}/scripts/prepare_play_release.py" "$@"
python3 "${ROOT}/scripts/verify_main_candidate.py" \
  --root "${ROOT}" \
  --expected-sha "${candidate_sha}"
