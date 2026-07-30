#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

resolve_adb() {
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
  echo "adb was not found on PATH, ANDROID_HOME, or ANDROID_SDK_ROOT" >&2
  exit 1
}

if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "Performance evidence must be captured from a clean candidate tree." >&2
  exit 1
fi

ADB="$(resolve_adb)"
PYTHON="${PYTHON:-python3}"
if ! command -v "$PYTHON" >/dev/null 2>&1; then
  echo "Python interpreter '$PYTHON' was not found." >&2
  exit 1
fi

mapfile -t CONNECTED_SERIALS < <("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')

SERIAL="${FOREST_RUN_DEVICE_SERIAL:-}"
if [[ -z "$SERIAL" ]]; then
  if [[ ${#CONNECTED_SERIALS[@]} -ne 1 ]]; then
    echo "Exactly one authorized device is required, or set FOREST_RUN_DEVICE_SERIAL." >&2
    printf 'Detected devices: %s\n' "${CONNECTED_SERIALS[*]:-(none)}" >&2
    exit 1
  fi
  SERIAL="${CONNECTED_SERIALS[0]}"
fi

if ! printf '%s\n' "${CONNECTED_SERIALS[@]}" | grep -Fxq "$SERIAL"; then
  echo "Requested device '$SERIAL' is not connected and authorized." >&2
  exit 1
fi

export ANDROID_SERIAL="$SERIAL"
ADB_DEVICE=("$ADB" -s "$SERIAL")
APP_ID="com.anurag9000.forestrun.debug"
REMOTE_DIR="/sdcard/Android/data/${APP_ID}/files/performance-profiles"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_DIR="${1:-performance-profiles/${SERIAL}/${TIMESTAMP}}"
TEST_SELECTOR="${FOREST_RUN_PROFILE_TEST:-com.anurag9000.forestrun.HardwarePerformanceProfileTest}"
CANDIDATE_SHA="$(git rev-parse HEAD)"
THRESHOLDS="${FOREST_RUN_PERFORMANCE_THRESHOLDS:-}"

mkdir -p "$OUTPUT_DIR"
"${ADB_DEVICE[@]}" shell rm -rf "$REMOTE_DIR"

{
  echo "candidate_sha=$CANDIDATE_SHA"
  echo "serial=$SERIAL"
  echo "captured_at_utc=$TIMESTAMP"
  echo "test_selector=$TEST_SELECTOR"
  echo "application_id=$APP_ID"
  echo "manufacturer=$("${ADB_DEVICE[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
  echo "device=$("${ADB_DEVICE[@]}" shell getprop ro.product.device | tr -d '\r')"
  echo "sdk=$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "build_fingerprint=$("${ADB_DEVICE[@]}" shell getprop ro.build.fingerprint | tr -d '\r')"
  if [[ -n "$THRESHOLDS" ]]; then
    echo "threshold_manifest=$(cd "$(dirname "$THRESHOLDS")" && pwd)/$(basename "$THRESHOLDS")"
    echo "threshold_manifest_sha256=$(sha256sum "$THRESHOLDS" | awk '{print $1}')"
  else
    echo "threshold_manifest=(not supplied)"
  fi
} > "${OUTPUT_DIR}/device.properties"

./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_SELECTOR" \
  --no-daemon --stacktrace --console=plain \
  | tee "${OUTPUT_DIR}/instrumentation.log"

mkdir -p "${OUTPUT_DIR}/reports"
if ! "${ADB_DEVICE[@]}" pull "$REMOTE_DIR/." "${OUTPUT_DIR}/reports/"; then
  echo "Profiling tests completed but no reports were pulled from $REMOTE_DIR" >&2
  exit 1
fi

mapfile -d '' REPORT_PATHS < <(find "${OUTPUT_DIR}/reports" -type f -name '*.json' -print0 | sort -z)
REPORT_COUNT="${#REPORT_PATHS[@]}"
if [[ "$REPORT_COUNT" -lt 1 ]]; then
  echo "No JSON performance reports were collected." >&2
  exit 1
fi

"${ADB_DEVICE[@]}" shell dumpsys gfxinfo "$APP_ID" > "${OUTPUT_DIR}/gfxinfo.txt" || true
"${ADB_DEVICE[@]}" shell dumpsys meminfo "$APP_ID" > "${OUTPUT_DIR}/meminfo.txt" || true
"${ADB_DEVICE[@]}" shell dumpsys display > "${OUTPUT_DIR}/display.txt" || true

if [[ -n "$THRESHOLDS" ]]; then
  if [[ ! -f "$THRESHOLDS" ]]; then
    echo "Threshold manifest does not exist: $THRESHOLDS" >&2
    exit 1
  fi
  "$PYTHON" scripts/evaluate_performance_profiles.py \
    --thresholds "$THRESHOLDS" \
    "${REPORT_PATHS[@]}" \
    | tee "${OUTPUT_DIR}/acceptance.txt"
else
  {
    echo "PENDING: reports were collected but no acceptance manifest was supplied."
    echo "Set FOREST_RUN_PERFORMANCE_THRESHOLDS to candidate-specific measured limits."
  } | tee "${OUTPUT_DIR}/acceptance.txt"
fi

echo "Collected $REPORT_COUNT performance report(s) for $CANDIDATE_SHA in $OUTPUT_DIR"
