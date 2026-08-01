#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "Usage: $0 DRAFT_JSON OUTPUT_JSON SUMMARY_JSON [GENERATED_AT_UTC]" >&2
  exit 2
fi
if ! command -v "${PYTHON_BIN}" >/dev/null 2>&1; then
  echo "Python interpreter '${PYTHON_BIN}' was not found." >&2
  exit 2
fi

readonly DRAFT_PATH="$1"
readonly OUTPUT_PATH="$2"
readonly SUMMARY_PATH="$3"
readonly GENERATED_AT_UTC="${4:-}"

"${PYTHON_BIN}" "${ROOT}/scripts/verify_strict_json_evidence.py" \
  "${DRAFT_PATH}"

compiler_args=(
  "${ROOT}/scripts/compile_device_acceptance.py"
  "${DRAFT_PATH}"
  "${OUTPUT_PATH}"
  --summary-output "${SUMMARY_PATH}"
)
if [[ -n "${GENERATED_AT_UTC}" ]]; then
  compiler_args+=(--generated-at-utc "${GENERATED_AT_UTC}")
fi
"${PYTHON_BIN}" "${compiler_args[@]}"

"${PYTHON_BIN}" "${ROOT}/scripts/verify_strict_json_evidence.py" \
  "${OUTPUT_PATH}" \
  "${SUMMARY_PATH}"
"${PYTHON_BIN}" "${ROOT}/scripts/validate_device_acceptance.py" \
  "${OUTPUT_PATH}"
