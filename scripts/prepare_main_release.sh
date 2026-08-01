#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

origin_sha="$(bash "${ROOT}/scripts/verify_origin_main.sh" "${ROOT}")"
candidate_json="$(python3 "${ROOT}/scripts/verify_main_candidate.py" --root "${ROOT}" --json)"
candidate_sha="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["sha"])' <<<"${candidate_json}")"

if [[ "${candidate_sha}" != "${origin_sha}" ]]; then
  echo "Local candidate verification disagrees with origin/main." >&2
  echo "candidate=${candidate_sha}" >&2
  echo "origin/main=${origin_sha}" >&2
  exit 1
fi

python3 "${ROOT}/scripts/verify_release_source_assets.py" --root "${ROOT}"
echo "Preparing Forest Run release evidence from origin/main at ${candidate_sha}."
python3 "${ROOT}/scripts/prepare_play_release.py" "$@"
python3 "${ROOT}/scripts/verify_main_candidate.py" \
  --root "${ROOT}" \
  --expected-sha "${candidate_sha}"
final_origin_sha="$(bash "${ROOT}/scripts/verify_origin_main.sh" "${ROOT}")"

if [[ "${final_origin_sha}" != "${candidate_sha}" ]]; then
  echo "origin/main changed during release preparation." >&2
  echo "started=${candidate_sha}" >&2
  echo "finished=${final_origin_sha}" >&2
  exit 1
fi
