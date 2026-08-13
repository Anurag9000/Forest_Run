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

PYTHON="${PYTHON:-python3}"
if ! command -v "$PYTHON" >/dev/null 2>&1; then
  echo "Python interpreter '$PYTHON' was not found." >&2
  exit 1
fi

ORIGIN_SHA="$(bash scripts/verify_origin_main.sh "$ROOT_DIR")"
CANDIDATE_JSON="$($PYTHON scripts/verify_main_candidate.py --root "$ROOT_DIR" --json)"
CANDIDATE_SHA="$($PYTHON -c 'import json,sys; print(json.load(sys.stdin)["sha"])' <<<"$CANDIDATE_JSON")"
if [[ "$CANDIDATE_SHA" != "$ORIGIN_SHA" ]]; then
  echo "Input-latency candidate verification disagrees with origin/main." >&2
  exit 1
fi

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
REMOTE_DIR="/sdcard/Android/data/${APP_ID}/files/input-latency-profiles"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_DIR="${1:-input-latency-profiles/${SERIAL}/${TIMESTAMP}}"
THRESHOLDS="${FOREST_RUN_INPUT_LATENCY_THRESHOLDS:-}"
THRESHOLDS_ABS=""
THRESHOLDS_SHA256=""
if [[ -n "$THRESHOLDS" ]]; then
  if [[ ! -f "$THRESHOLDS" ]]; then
    echo "Input-latency threshold manifest does not exist: $THRESHOLDS" >&2
    exit 1
  fi
  THRESHOLDS_ABS="$(cd "$(dirname "$THRESHOLDS")" && pwd)/$(basename "$THRESHOLDS")"
  THRESHOLDS_SHA256="$($PYTHON -c 'import hashlib,pathlib,sys; print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())' "$THRESHOLDS_ABS")"
fi

mkdir -p "$OUTPUT_DIR"
"${ADB_DEVICE[@]}" shell rm -rf "$REMOTE_DIR"

{
  echo "candidate_sha=$CANDIDATE_SHA"
  echo "origin_main_sha=$ORIGIN_SHA"
  echo "serial=$SERIAL"
  echo "captured_at_utc=$TIMESTAMP"
  echo "application_id=$APP_ID"
  echo "measurement_kind=app_touch_to_posted_frame"
  echo "manufacturer=$("${ADB_DEVICE[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
  echo "device=$("${ADB_DEVICE[@]}" shell getprop ro.product.device | tr -d '\r')"
  echo "sdk=$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "build_fingerprint=$("${ADB_DEVICE[@]}" shell getprop ro.build.fingerprint | tr -d '\r')"
  if [[ -n "$THRESHOLDS_ABS" ]]; then
    echo "threshold_manifest=$THRESHOLDS_ABS"
    echo "threshold_manifest_sha256=$THRESHOLDS_SHA256"
  else
    echo "threshold_manifest=(not supplied)"
  fi
} > "${OUTPUT_DIR}/device.properties"

"${ADB_DEVICE[@]}" shell dumpsys display > "${OUTPUT_DIR}/display-before.txt" 2>&1 || true
"${ADB_DEVICE[@]}" shell dumpsys input > "${OUTPUT_DIR}/input-before.txt" 2>&1 || true

set +e
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class='com.anurag9000.forestrun.HardwareInputLatencyProfileTest' \
  --no-daemon --stacktrace --console=plain \
  | tee "${OUTPUT_DIR}/instrumentation.log"
GRADLE_STATUS="${PIPESTATUS[0]}"
set -e

"${ADB_DEVICE[@]}" shell dumpsys display > "${OUTPUT_DIR}/display-after.txt" 2>&1 || true
"${ADB_DEVICE[@]}" shell dumpsys input > "${OUTPUT_DIR}/input-after.txt" 2>&1 || true
"${ADB_DEVICE[@]}" shell dumpsys package "$APP_ID" > "${OUTPUT_DIR}/package-after.txt" 2>&1 || true

if [[ "$GRADLE_STATUS" -ne 0 ]]; then
  echo "Input-latency instrumentation failed; diagnostics were retained in $OUTPUT_DIR" >&2
  exit "$GRADLE_STATUS"
fi

mkdir -p "${OUTPUT_DIR}/reports"
if ! "${ADB_DEVICE[@]}" pull "$REMOTE_DIR/." "${OUTPUT_DIR}/reports/"; then
  echo "Input-latency test completed but no report was pulled from $REMOTE_DIR" >&2
  exit 1
fi
mapfile -d '' REPORT_PATHS < <(find "${OUTPUT_DIR}/reports" -type f -name '*.json' -print0 | sort -z)
if [[ ${#REPORT_PATHS[@]} -lt 1 ]]; then
  echo "No input-latency JSON report was collected." >&2
  exit 1
fi

if [[ -n "$THRESHOLDS_ABS" ]]; then
  "$PYTHON" scripts/evaluate_input_latency_profiles.py \
    --thresholds "$THRESHOLDS_ABS" \
    "${REPORT_PATHS[@]}" \
    | tee "${OUTPUT_DIR}/acceptance.txt"
else
  {
    echo "PENDING: app/render latency was measured but no approved threshold manifest was supplied."
    echo "Set FOREST_RUN_INPUT_LATENCY_THRESHOLDS only after representative device measurements are reviewed."
  } | tee "${OUTPUT_DIR}/acceptance.txt"
fi

FINAL_LOCAL_JSON="$($PYTHON scripts/verify_main_candidate.py --root "$ROOT_DIR" --expected-sha "$CANDIDATE_SHA" --json)"
FINAL_LOCAL_SHA="$($PYTHON -c 'import json,sys; print(json.load(sys.stdin)["sha"])' <<<"$FINAL_LOCAL_JSON")"
FINAL_ORIGIN_SHA="$(bash scripts/verify_origin_main.sh "$ROOT_DIR")"
if [[ "$FINAL_LOCAL_SHA" != "$CANDIDATE_SHA" || "$FINAL_ORIGIN_SHA" != "$CANDIDATE_SHA" ]]; then
  echo "Candidate or origin/main changed during input-latency capture." >&2
  echo "started=$CANDIDATE_SHA" >&2
  echo "local=$FINAL_LOCAL_SHA" >&2
  echo "origin/main=$FINAL_ORIGIN_SHA" >&2
  exit 1
fi

printf 'Collected %d input-latency report(s) for %s in %s\n' \
  "${#REPORT_PATHS[@]}" "$CANDIDATE_SHA" "$OUTPUT_DIR"
