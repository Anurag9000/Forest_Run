#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ $# -lt 2 || $# -gt 3 ]]; then
  echo "Usage: $0 CANDIDATE_JSON OUTPUT_JSON [BASELINE_JSON]" >&2
  exit 2
fi
if ! command -v "${PYTHON_BIN}" >/dev/null 2>&1; then
  echo "Python interpreter '${PYTHON_BIN}' was not found." >&2
  exit 2
fi

readonly CANDIDATE_PATH="$1"
readonly OUTPUT_PATH="$2"
readonly BASELINE_PATH="${3:-}"

"${PYTHON_BIN}" - "${CANDIDATE_PATH}" "${OUTPUT_PATH}" "${BASELINE_PATH}" <<'PY'
from pathlib import Path
import sys

candidate = Path(sys.argv[1]).expanduser().resolve()
output = Path(sys.argv[2]).expanduser().resolve()
baseline = Path(sys.argv[3]).expanduser().resolve() if sys.argv[3] else None
if output == candidate:
    raise SystemExit("Aggregate output must not overwrite the candidate manifest.")
if baseline is not None and output == baseline:
    raise SystemExit("Aggregate output must not overwrite the baseline manifest.")
if baseline is not None and baseline == candidate:
    raise SystemExit("Candidate and baseline manifests must be distinct files.")
PY

preflight_args=(
  "${ROOT}/scripts/verify_strict_json_evidence.py"
  "${CANDIDATE_PATH}"
)
if [[ -n "${BASELINE_PATH}" ]]; then
  preflight_args+=("${BASELINE_PATH}")
fi
"${PYTHON_BIN}" "${preflight_args[@]}"

"${PYTHON_BIN}" "${ROOT}/scripts/validate_manifest_scenario_traces.py" \
  "${CANDIDATE_PATH}" \
  --root "${ROOT}" \
  --require-at-least-one
if [[ -n "${BASELINE_PATH}" ]]; then
  "${PYTHON_BIN}" "${ROOT}/scripts/validate_manifest_scenario_traces.py" \
    "${BASELINE_PATH}" \
    --root "${ROOT}" \
    --require-at-least-one
fi

readonly OUTPUT_DIR="$(dirname -- "${OUTPUT_PATH}")"
readonly OUTPUT_NAME="$(basename -- "${OUTPUT_PATH}")"
mkdir -p -- "${OUTPUT_DIR}"
STAGED_PATH="$(mktemp "${OUTPUT_DIR}/.${OUTPUT_NAME}.aggregate.XXXXXX.tmp")"
readonly STAGED_PATH
cleanup() {
  rm -f -- "${STAGED_PATH}"
}
trap cleanup EXIT

aggregate_args=(
  "${ROOT}/scripts/aggregate_device_acceptance.py"
  "${CANDIDATE_PATH}"
  --output "${STAGED_PATH}"
)
if [[ -n "${BASELINE_PATH}" ]]; then
  aggregate_args+=(--baseline "${BASELINE_PATH}")
fi
"${PYTHON_BIN}" "${aggregate_args[@]}"

"${PYTHON_BIN}" "${ROOT}/scripts/verify_strict_json_evidence.py" \
  "${STAGED_PATH}"
"${PYTHON_BIN}" "${ROOT}/scripts/validate_device_acceptance_aggregate.py" \
  "${STAGED_PATH}"

publish_args=(
  "${ROOT}/scripts/publish_device_acceptance_aggregate.py"
  "${CANDIDATE_PATH}"
  "${STAGED_PATH}"
  "${OUTPUT_PATH}"
)
if [[ -n "${BASELINE_PATH}" ]]; then
  publish_args+=(--baseline "${BASELINE_PATH}")
fi
"${PYTHON_BIN}" "${publish_args[@]}"
