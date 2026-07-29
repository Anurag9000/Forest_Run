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

ADB="$(resolve_adb)"
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

mkdir -p "$OUTPUT_DIR"
"${ADB_DEVICE[@]}" shell rm -rf "$REMOTE_DIR"

{
  echo "serial=$SERIAL"
  echo "captured_at_utc=$TIMESTAMP"
  echo "test_selector=$TEST_SELECTOR"
  echo "manufacturer=$("${ADB_DEVICE[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
  echo "device=$("${ADB_DEVICE[@]}" shell getprop ro.product.device | tr -d '\r')"
  echo "sdk=$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "build_fingerprint=$("${ADB_DEVICE[@]}" shell getprop ro.build.fingerprint | tr -d '\r')"
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

REPORT_COUNT="$(find "${OUTPUT_DIR}/reports" -type f -name '*.json' | wc -l | tr -d ' ')"
if [[ "$REPORT_COUNT" -lt 1 ]]; then
  echo "No JSON performance reports were collected." >&2
  exit 1
fi

"${ADB_DEVICE[@]}" shell dumpsys gfxinfo "$APP_ID" > "${OUTPUT_DIR}/gfxinfo.txt" || true
"${ADB_DEVICE[@]}" shell dumpsys meminfo "$APP_ID" > "${OUTPUT_DIR}/meminfo.txt" || true
"${ADB_DEVICE[@]}" shell dumpsys display > "${OUTPUT_DIR}/display.txt" || true

echo "Collected $REPORT_COUNT performance report(s) in $OUTPUT_DIR"
