#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SKIP_ORIGIN_MAIN_CHECK=0
EXPECTED_CANDIDATE_SHA=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-origin-main-check)
      SKIP_ORIGIN_MAIN_CHECK=1
      shift
      ;;
    --candidate-sha)
      if [[ $# -lt 2 ]]; then
        echo "--candidate-sha requires a 40-character Git commit SHA." >&2
        exit 2
      fi
      EXPECTED_CANDIDATE_SHA="$2"
      shift 2
      ;;
    *)
      echo "Unknown connected-validation argument: $1" >&2
      exit 2
      ;;
  esac
done

cd "${ROOT_DIR}"

readonly SERIAL="${ANDROID_SERIAL:-emulator-${EMULATOR_PORT:-5554}}"
readonly READINESS_TIMEOUT_SECONDS="${FOREST_RUN_EMULATOR_READINESS_TIMEOUT_SECONDS:-240}"
readonly POLL_SECONDS=5
readonly PHYSICAL_PROFILE_ANNOTATION="androidx.test.filters.LargeTest"

if [[ ! "${READINESS_TIMEOUT_SECONDS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "FOREST_RUN_EMULATOR_READINESS_TIMEOUT_SECONDS must be a positive integer." >&2
  exit 2
fi
for required_command in adb timeout git; do
  if ! command -v "${required_command}" >/dev/null 2>&1; then
    echo "Connected validation requires '${required_command}' on PATH." >&2
    exit 2
  fi
done

readonly LOCAL_CANDIDATE_SHA="$(git rev-parse --verify HEAD)"
if [[ -z "${EXPECTED_CANDIDATE_SHA}" ]]; then
  EXPECTED_CANDIDATE_SHA="${LOCAL_CANDIDATE_SHA}"
fi
if [[ ! "${EXPECTED_CANDIDATE_SHA}" =~ ^[0-9a-fA-F]{40}$ ]]; then
  echo "Connected-validation candidate SHA must be exactly 40 hexadecimal characters." >&2
  exit 2
fi
if [[ "${LOCAL_CANDIDATE_SHA}" != "${EXPECTED_CANDIDATE_SHA}" ]]; then
  echo "Connected-validation checkout does not match the requested candidate." >&2
  echo "expected=${EXPECTED_CANDIDATE_SHA}" >&2
  echo "local=${LOCAL_CANDIDATE_SHA}" >&2
  exit 1
fi
readonly INITIAL_WORKTREE_STATUS="$(git status --porcelain=v1 --untracked-files=all)"
if [[ -n "${INITIAL_WORKTREE_STATUS}" ]]; then
  echo "Connected validation requires a clean candidate worktree." >&2
  printf '%s\n' "${INITIAL_WORKTREE_STATUS}" >&2
  exit 1
fi
if [[ "${SKIP_ORIGIN_MAIN_CHECK}" -eq 0 ]]; then
  readonly ORIGIN_MAIN_SHA="$(bash scripts/verify_origin_main.sh "${ROOT_DIR}")"
  if [[ "${ORIGIN_MAIN_SHA}" != "${EXPECTED_CANDIDATE_SHA}" ]]; then
    echo "Connected-validation candidate is not canonical origin/main." >&2
    echo "expected=${EXPECTED_CANDIDATE_SHA}" >&2
    echo "origin/main=${ORIGIN_MAIN_SHA}" >&2
    exit 1
  fi
fi

export ANDROID_SERIAL="${SERIAL}"

dump_emulator_diagnostics() {
  local status=$?
  if [[ ${status} -eq 0 ]]; then
    return
  fi

  echo "Connected validation failed; collecting bounded emulator diagnostics." >&2
  adb -s "${SERIAL}" get-state || true
  adb -s "${SERIAL}" shell getprop || true
  adb -s "${SERIAL}" shell dumpsys package packages | tail -n 400 || true
  adb -s "${SERIAL}" shell dumpsys activity providers | tail -n 400 || true
  adb -s "${SERIAL}" logcat -d -t 800 || true
  exit "${status}"
}
trap dump_emulator_diagnostics EXIT

wait_for_android_services() {
  local deadline=$((SECONDS + READINESS_TIMEOUT_SECONDS))

  while (( SECONDS < deadline )); do
    if adb -s "${SERIAL}" get-state 2>/dev/null | grep -qx 'device' &&
       [[ "$(adb -s "${SERIAL}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]] &&
       adb -s "${SERIAL}" shell pm path android >/dev/null 2>&1 &&
       adb -s "${SERIAL}" shell cmd package list packages >/dev/null 2>&1 &&
       adb -s "${SERIAL}" shell settings get global device_provisioned >/dev/null 2>&1; then
      echo "Android framework, PackageManager, and Settings provider are ready on ${SERIAL}."
      return 0
    fi

    if ! adb devices | awk -v serial="${SERIAL}" 'NR > 1 && $1 == serial { found=1 } END { exit found ? 0 : 1 }'; then
      adb start-server >/dev/null 2>&1 || true
    fi
    sleep "${POLL_SECONDS}"
  done

  echo "Timed out waiting ${READINESS_TIMEOUT_SECONDS}s for Android services on ${SERIAL}." >&2
  return 1
}

timeout "${READINESS_TIMEOUT_SECONDS}s" adb -s "${SERIAL}" wait-for-device
wait_for_android_services

# HardwarePerformanceProfileTest is a physical-device evidence harness. Running
# it on SwiftShader emulators creates invalid performance evidence and starves
# its sustained-sample window, so ordinary connected CI excludes @LargeTest.
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation="${PHYSICAL_PROFILE_ANNOTATION}" \
  --no-daemon \
  --stacktrace \
  --console=plain

readonly FINAL_LOCAL_CANDIDATE_SHA="$(git rev-parse --verify HEAD)"
if [[ "${FINAL_LOCAL_CANDIDATE_SHA}" != "${EXPECTED_CANDIDATE_SHA}" ]]; then
  echo "Connected-validation HEAD changed during execution." >&2
  echo "expected=${EXPECTED_CANDIDATE_SHA}" >&2
  echo "local=${FINAL_LOCAL_CANDIDATE_SHA}" >&2
  exit 1
fi
readonly FINAL_WORKTREE_STATUS="$(git status --porcelain=v1 --untracked-files=all)"
if [[ -n "${FINAL_WORKTREE_STATUS}" ]]; then
  echo "Connected validation changed or introduced non-ignored candidate files." >&2
  printf '%s\n' "${FINAL_WORKTREE_STATUS}" >&2
  exit 1
fi
if [[ "${SKIP_ORIGIN_MAIN_CHECK}" -eq 0 ]]; then
  readonly FINAL_ORIGIN_MAIN_SHA="$(bash scripts/verify_origin_main.sh "${ROOT_DIR}")"
  if [[ "${FINAL_ORIGIN_MAIN_SHA}" != "${EXPECTED_CANDIDATE_SHA}" ]]; then
    echo "Canonical origin/main changed during connected validation." >&2
    echo "expected=${EXPECTED_CANDIDATE_SHA}" >&2
    echo "origin/main=${FINAL_ORIGIN_MAIN_SHA}" >&2
    exit 1
  fi
fi
