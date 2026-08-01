#!/usr/bin/env bash
set -euo pipefail

readonly ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly RELEASE_ROOT="${ROOT}/release/google-play"
readonly MACHINE_SUMMARY="${RELEASE_ROOT}/build_summary.json"
readonly HUMAN_SUMMARY="${RELEASE_ROOT}/BUILD_SUMMARY.md"

if [[ -n "${JAVA_HOME:-}" ]]; then
  java_home_binary="${JAVA_HOME}/bin/java"
  if [[ ! -x "${java_home_binary}" && -x "${java_home_binary}.exe" ]]; then
    java_home_binary="${java_home_binary}.exe"
  fi
  if [[ ! -x "${java_home_binary}" ]]; then
    echo "JAVA_HOME does not contain an executable Java runtime: ${JAVA_HOME}" >&2
    exit 2
  fi
  export PATH="$(dirname "${java_home_binary}"):${PATH}"
fi

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
python3 "${ROOT}/scripts/verify_store_graphics.py" \
  --root "${ROOT}" \
  --graphics-dir "${ROOT}/release/google-play/graphics" \
  --candidate-sha "${candidate_sha}"
python3 "${ROOT}/scripts/verify_store_metadata.py" \
  --metadata-dir "${ROOT}/release/google-play/metadata/en-US" \
  --candidate-sha "${candidate_sha}"

mkdir -p "${RELEASE_ROOT}"
summary_backup_dir="$(mktemp -d "${TMPDIR:-/tmp}/forest-run-release-summary.XXXXXX")"
restore_release_summaries() {
  local status=$?
  trap - EXIT
  if [[ ${status} -ne 0 ]]; then
    rm -f "${MACHINE_SUMMARY}" "${HUMAN_SUMMARY}"
    for filename in build_summary.json BUILD_SUMMARY.md; do
      if [[ -f "${summary_backup_dir}/${filename}" ]]; then
        mv "${summary_backup_dir}/${filename}" "${RELEASE_ROOT}/${filename}"
      fi
    done
  fi
  rm -rf "${summary_backup_dir}"
  exit "${status}"
}
trap restore_release_summaries EXIT

for summary_path in "${MACHINE_SUMMARY}" "${HUMAN_SUMMARY}"; do
  if [[ -f "${summary_path}" ]]; then
    mv "${summary_path}" "${summary_backup_dir}/$(basename "${summary_path}")"
  fi
done

echo "Preparing Forest Run release evidence from origin/main at ${candidate_sha}."
python3 "${ROOT}/scripts/prepare_play_release.py" "$@"
python3 "${ROOT}/scripts/verify_release_summary.py" \
  --root "${ROOT}" \
  --release-root "${RELEASE_ROOT}" \
  --candidate-sha "${candidate_sha}"
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
