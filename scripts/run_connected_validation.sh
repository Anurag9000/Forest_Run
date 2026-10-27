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
readonly PROCESS_TEST_CLASS="com.anurag9000.forestrun.ProcessDeathPersistenceInstrumentedTest"
readonly PROCESS_TEST_TARGET_PACKAGE="com.anurag9000.forestrun.debug"
readonly PROCESS_TEST_RUNNER_COMPONENT="com.anurag9000.forestrun.debug.test/androidx.test.runner.AndroidJUnitRunner"
PROCESS_TEST_CLEANUP_REQUIRED=0

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

  if [[ "${PROCESS_TEST_CLEANUP_REQUIRED}" -eq 1 ]]; then
    echo "Attempting to clear process-death instrumentation state after failure." >&2
    run_process_death_instrumentation cleanup 30 || true
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

run_process_death_instrumentation() {
  local stage="$1"
  local timeout_seconds="${2:-${READINESS_TIMEOUT_SECONDS}}"
  local output
  local successful_test_records
  local android_user_id

  if ! android_user_id="$(timeout 15s adb -s "${SERIAL}" shell am get-current-user 2>&1)"; then
    printf '%s\n' "${android_user_id}" >&2
    echo "Unable to determine the current Android user before '${stage}' instrumentation." >&2
    return 1
  fi
  android_user_id="${android_user_id//$'\r'/}"
  if [[ ! "${android_user_id}" =~ ^[0-9]+$ ]]; then
    echo "Android returned a non-numeric current user before '${stage}' instrumentation: ${android_user_id}" >&2
    return 1
  fi

  if ! output="$(timeout "${timeout_seconds}s" adb -s "${SERIAL}" shell am instrument --user "${android_user_id}" -w -r \
    -e class "${PROCESS_TEST_CLASS}" \
    -e forestRunProcessDeathStage "${stage}" \
    "${PROCESS_TEST_RUNNER_COMPONENT}" 2>&1)"; then
    printf '%s\n' "${output}" >&2
    echo "Instrumentation command failed during '${stage}' stage." >&2
    return 1
  fi
  output="${output//$'\r'/}"
  printf '%s\n' "${output}"

  # The am instrument client reports structured status output and may exit zero
  # even when a JUnit test fails. Require one successful test result and the
  # normal successful session terminator, while rejecting assertion/error codes.
  if grep -Eq '^INSTRUMENTATION_STATUS_CODE: -[12]$|^INSTRUMENTATION_FAILED:|^INSTRUMENTATION_RESULT: shortMsg=' <<<"${output}"; then
    echo "Instrumentation reported a failed test or aborted session during '${stage}'." >&2
    return 1
  fi
  successful_test_records="$(grep -c '^INSTRUMENTATION_STATUS_CODE: 0$' <<<"${output}" || true)"
  if ! grep -q '^INSTRUMENTATION_STATUS: numtests=1$' <<<"${output}" ||
     [[ "${successful_test_records}" -ne 1 ]] ||
     ! grep -q '^INSTRUMENTATION_CODE: -1$' <<<"${output}"; then
    echo "Instrumentation did not report exactly one passing test and a successful session during '${stage}'." >&2
    return 1
  fi
}

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

# This LargeTest is intentionally excluded above and selected only here. The
# host shell survives the target-only force-stop and verifies that no target
# process remains before a new instrumentation invocation checks durable state.
PROCESS_TEST_CLEANUP_REQUIRED=1
run_process_death_instrumentation prepare

adb -s "${SERIAL}" shell am force-stop "${PROCESS_TEST_TARGET_PACKAGE}"
if ! adb -s "${SERIAL}" get-state 2>/dev/null | grep -qx 'device'; then
  echo "Device ${SERIAL} disconnected before process-death verification." >&2
  exit 1
fi
readonly PIDOF_PATH="$(adb -s "${SERIAL}" shell command -v pidof 2>/dev/null | tr -d '\r')"
if [[ -z "${PIDOF_PATH}" ]]; then
  echo "Cannot verify target process termination: device has no pidof command." >&2
  exit 1
fi
if ! PIDOF_RESULT="$(adb -s "${SERIAL}" shell "pidof ${PROCESS_TEST_TARGET_PACKAGE}; printf '\\nPIDOF_EXIT:%s\\n' \"\$?\"" 2>/dev/null | tr -d '\r')"; then
  echo "Could not query target process state after force-stop." >&2
  exit 1
fi
if [[ "${PIDOF_RESULT}" != *$'\n'PIDOF_EXIT:* ]]; then
  echo "Could not parse target process state after force-stop." >&2
  exit 1
fi
TARGET_PROCESS_IDS="${PIDOF_RESULT%$'\n'PIDOF_EXIT:*}"
PIDOF_EXIT_CODE="${PIDOF_RESULT##*$'\n'PIDOF_EXIT:}"
if [[ ! "${PIDOF_EXIT_CODE}" =~ ^[01]$ ]]; then
  echo "pidof returned an unexpected exit code: ${PIDOF_EXIT_CODE}" >&2
  exit 1
fi
if [[ "${PIDOF_EXIT_CODE}" -eq 0 && -z "${TARGET_PROCESS_IDS}" ]]; then
  echo "pidof returned success without a target process ID." >&2
  exit 1
fi
if ! adb -s "${SERIAL}" get-state 2>/dev/null | grep -qx 'device'; then
  echo "Device ${SERIAL} disconnected while checking process termination." >&2
  exit 1
fi
if [[ -n "${TARGET_PROCESS_IDS}" ]]; then
  echo "Target process survived force-stop; refusing to claim a process boundary." >&2
  echo "package=${PROCESS_TEST_TARGET_PACKAGE} pids=${TARGET_PROCESS_IDS}" >&2
  exit 1
fi
echo "Verified ${PROCESS_TEST_TARGET_PACKAGE} has no running process after force-stop."

run_process_death_instrumentation verify
PROCESS_TEST_CLEANUP_REQUIRED=0

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
