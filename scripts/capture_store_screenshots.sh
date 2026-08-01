#!/usr/bin/env bash
set -euo pipefail

readonly ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly PYTHON_BIN="${PYTHON_BIN:-python3}"
readonly DEVICE_SERIAL_REQUEST="${DEVICE_SERIAL:-}"
readonly PACKAGE_NAME="com.anurag9000.forestrun.debug"
readonly ACTIVITY_NAME="com.anurag9000.forestrun.MainActivity"
readonly APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
readonly OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/release/google-play/screenshots/raw}"
readonly STARTUP_TIMEOUT_S="${STARTUP_TIMEOUT_S:-15}"
readonly READY_PREFIX="FOREST_RUN_SCENARIO_READY"
readonly RUN_MODE="SCREENSHOT_CAPTURE"
readonly CAPTURE_COUNT=8

cd "${ROOT_DIR}"

if [[ ! "${STARTUP_TIMEOUT_S}" =~ ^[1-9][0-9]*$ ]]; then
  echo "STARTUP_TIMEOUT_S must be a positive integer." >&2
  exit 2
fi
for required_command in git "${PYTHON_BIN}"; do
  if ! command -v "${required_command}" >/dev/null 2>&1; then
    echo "Screenshot capture requires '${required_command}' on PATH." >&2
    exit 2
  fi
done

resolve_adb() {
  if [[ -n "${ADB_BIN:-}" ]]; then
    if [[ ! -x "${ADB_BIN}" ]]; then
      echo "ADB_BIN is not executable: ${ADB_BIN}" >&2
      exit 2
    fi
    printf '%s\n' "${ADB_BIN}"
    return
  fi
  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi
  if [[ -n "${ANDROID_HOME:-}" && -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
    printf '%s\n' "${ANDROID_HOME}/platform-tools/adb"
    return
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
    printf '%s\n' "${ANDROID_SDK_ROOT}/platform-tools/adb"
    return
  fi
  echo "adb is unavailable. Set ADB_BIN or configure the Android SDK." >&2
  exit 2
}

readonly ADB_BIN_RESOLVED="$(resolve_adb)"
readonly ORIGIN_SHA="$(bash scripts/verify_origin_main.sh "${ROOT_DIR}")"
readonly CANDIDATE_JSON="$(${PYTHON_BIN} scripts/verify_main_candidate.py --root "${ROOT_DIR}" --json)"
readonly CANDIDATE_SHA="$(${PYTHON_BIN} -c 'import json,sys; print(json.load(sys.stdin)["sha"])' <<<"${CANDIDATE_JSON}")"
if [[ "${CANDIDATE_SHA}" != "${ORIGIN_SHA}" ]]; then
  echo "Screenshot candidate verification disagrees with origin/main." >&2
  echo "candidate=${CANDIDATE_SHA}" >&2
  echo "origin/main=${ORIGIN_SHA}" >&2
  exit 1
fi

${PYTHON_BIN} scripts/verify_release_source_assets.py --root "${ROOT_DIR}"
./gradlew clean assembleDebug --no-daemon --stacktrace --console=plain
if [[ ! -s "${APK_PATH}" ]]; then
  echo "Exact-main debug APK is missing or empty after assembleDebug: ${APK_PATH}" >&2
  exit 1
fi
readonly APK_SHA256="$(${PYTHON_BIN} -c 'import hashlib,pathlib,sys; print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())' "${APK_PATH}")"

mapfile -t devices < <("${ADB_BIN_RESOLVED}" devices | awk 'NR > 1 && $2 == "device" { print $1 }')
if [[ ${#devices[@]} -eq 0 ]]; then
  echo "No authorized Android device is attached." >&2
  exit 1
fi

DEVICE_SERIAL_RESOLVED="${DEVICE_SERIAL_REQUEST}"
if [[ -n "${DEVICE_SERIAL_RESOLVED}" ]]; then
  if ! printf '%s\n' "${devices[@]}" | grep -Fxq "${DEVICE_SERIAL_RESOLVED}"; then
    echo "DEVICE_SERIAL=${DEVICE_SERIAL_RESOLVED} is not an attached authorized device." >&2
    printf 'Available devices: %s\n' "${devices[*]}" >&2
    exit 1
  fi
elif [[ ${#devices[@]} -eq 1 ]]; then
  DEVICE_SERIAL_RESOLVED="${devices[0]}"
else
  echo "Multiple devices are attached; set DEVICE_SERIAL explicitly." >&2
  printf 'Available devices: %s\n' "${devices[*]}" >&2
  exit 1
fi
readonly DEVICE_SERIAL_RESOLVED
readonly -a ADB=("${ADB_BIN_RESOLVED}" -s "${DEVICE_SERIAL_RESOLVED}")

mkdir -p "${OUTPUT_DIR}"
find "${OUTPUT_DIR}" -maxdepth 1 -type f \
  \( -name '*.png' -o -name '*.capture.json' -o -name 'capture-session.json' \) \
  -delete

printf 'Installing exact-main APK %s on %s\n' "${APK_PATH}" "${DEVICE_SERIAL_RESOLVED}"
"${ADB[@]}" install -r -d "${APK_PATH}" >/dev/null
if ! "${ADB[@]}" shell pm path "${PACKAGE_NAME}" 2>/dev/null | tr -d '\r' | grep -q '^package:'; then
  echo "Installed screenshot package is not visible to PackageManager: ${PACKAGE_NAME}" >&2
  exit 1
fi
"${ADB[@]}" shell pm clear "${PACKAGE_NAME}" >/dev/null

wait_for_process() {
  local deadline=$((SECONDS + STARTUP_TIMEOUT_S))
  while (( SECONDS < deadline )); do
    if [[ -n "$("${ADB[@]}" shell pidof "${PACKAGE_NAME}" 2>/dev/null | tr -d '\r')" ]]; then
      return 0
    fi
    sleep 0.25
  done
  echo "Timed out waiting for ${PACKAGE_NAME} to start." >&2
  return 1
}

wait_for_scenario() {
  local scenario="$1"
  local marker="${READY_PREFIX} scenario=${scenario} mode=${RUN_MODE}"
  local deadline=$((SECONDS + STARTUP_TIMEOUT_S))
  while (( SECONDS < deadline )); do
    if "${ADB[@]}" logcat -d -v brief -s ForestRunLaunch:I 2>/dev/null | grep -Fq "${marker}"; then
      printf '%s\n' "${marker}"
      return 0
    fi
    sleep 0.25
  done
  echo "Timed out waiting for verified scenario marker: ${marker}" >&2
  "${ADB[@]}" logcat -d -v brief -s ForestRunLaunch:I 2>/dev/null >&2 || true
  return 1
}

assert_app_foreground() {
  local resumed
  resumed="$(
    "${ADB[@]}" shell dumpsys activity activities 2>/dev/null \
      | tr -d '\r' \
      | grep -E 'mResumedActivity|topResumedActivity' \
      || true
  )"
  if [[ "${resumed}" != *"${PACKAGE_NAME}/${ACTIVITY_NAME}"* ]]; then
    echo "Screenshot app is not the resumed foreground activity." >&2
    echo "Expected ${PACKAGE_NAME}/${ACTIVITY_NAME}" >&2
    echo "Observed: ${resumed:-'(no resumed activity reported)'}" >&2
    return 1
  fi
}

write_capture_evidence() {
  local destination="$1"
  local scenario="$2"
  local settle_seconds="$3"
  local readiness_marker="$4"
  "${PYTHON_BIN}" scripts/write_screenshot_capture_evidence.py \
    --image "${destination}" \
    --scenario "${scenario}" \
    --settle-seconds "${settle_seconds}" \
    --readiness-marker "${readiness_marker}" \
    --candidate-sha "${CANDIDATE_SHA}" \
    --apk-sha256 "${APK_SHA256}" \
    --device-serial "${DEVICE_SERIAL_RESOLVED}" \
    --package-name "${PACKAGE_NAME}" \
    --activity-name "${ACTIVITY_NAME}" \
    --run-mode "${RUN_MODE}" >/dev/null
}

capture() {
  local name="$1"
  local scenario="$2"
  local settle_seconds="$3"
  local destination="${OUTPUT_DIR}/${name}.png"

  printf 'Capturing %-18s scenario=%s\n' "${name}" "${scenario}"
  "${ADB[@]}" shell am force-stop "${PACKAGE_NAME}"
  "${ADB[@]}" logcat -c
  "${ADB[@]}" shell am start -S -W \
    -n "${PACKAGE_NAME}/${ACTIVITY_NAME}" \
    --ez debug_autostart true \
    --es debug_scenario "${scenario}" \
    --es run_mode "${RUN_MODE}" >/dev/null
  wait_for_process
  local readiness_marker
  readiness_marker="$(wait_for_scenario "${scenario}")"
  sleep "${settle_seconds}"
  wait_for_process
  assert_app_foreground
  "${ADB[@]}" exec-out screencap -p > "${destination}"
  assert_app_foreground

  if [[ ! -s "${destination}" ]]; then
    echo "Screenshot capture produced an empty file: ${destination}" >&2
    exit 1
  fi
  write_capture_evidence \
    "${destination}" \
    "${scenario}" \
    "${settle_seconds}" \
    "${readiness_marker}"
}

capture "01-opening" "OPENING_READABILITY" 2.0
capture "02-bloom" "BLOOM_SHOWCASE" 2.4
capture "03-ghost" "GHOST_READABILITY" 2.2
capture "04-rest" "REST_LOOP" 2.8
capture "05-cat" "CAT_KINDNESS" 2.1
capture "06-dog" "DOG_BUDDY" 2.2
capture "07-owl" "OWL_DIVE" 2.1
capture "08-jacaranda" "JACARANDA_PETALS" 2.0

"${PYTHON_BIN}" scripts/finalize_screenshot_capture_session.py \
  --output-dir "${OUTPUT_DIR}" \
  --candidate-sha "${CANDIDATE_SHA}" \
  --origin-main-sha "${ORIGIN_SHA}" \
  --apk-sha256 "${APK_SHA256}" \
  --device-serial "${DEVICE_SERIAL_RESOLVED}" \
  --package-name "${PACKAGE_NAME}" \
  --activity-name "${ACTIVITY_NAME}" \
  --expected-count "${CAPTURE_COUNT}" >/dev/null

readonly FINAL_LOCAL_JSON="$(${PYTHON_BIN} scripts/verify_main_candidate.py --root "${ROOT_DIR}" --expected-sha "${CANDIDATE_SHA}" --json)"
readonly FINAL_LOCAL_SHA="$(${PYTHON_BIN} -c 'import json,sys; print(json.load(sys.stdin)["sha"])' <<<"${FINAL_LOCAL_JSON}")"
readonly FINAL_ORIGIN_SHA="$(bash scripts/verify_origin_main.sh "${ROOT_DIR}")"
if [[ "${FINAL_LOCAL_SHA}" != "${CANDIDATE_SHA}" || "${FINAL_ORIGIN_SHA}" != "${CANDIDATE_SHA}" ]]; then
  echo "Candidate or origin/main changed during screenshot capture." >&2
  echo "started=${CANDIDATE_SHA}" >&2
  echo "local=${FINAL_LOCAL_SHA}" >&2
  echo "origin/main=${FINAL_ORIGIN_SHA}" >&2
  exit 1
fi

printf 'Raw screenshots and capture evidence for %s written to %s\n' \
  "${CANDIDATE_SHA}" \
  "${OUTPUT_DIR}"
