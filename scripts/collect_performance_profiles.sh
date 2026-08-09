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
  echo "Performance candidate verification disagrees with origin/main." >&2
  echo "candidate=$CANDIDATE_SHA" >&2
  echo "origin/main=$ORIGIN_SHA" >&2
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
REMOTE_DIR="/sdcard/Android/data/${APP_ID}/files/performance-profiles"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUTPUT_DIR="${1:-performance-profiles/${SERIAL}/${TIMESTAMP}}"
TEST_SELECTOR="${FOREST_RUN_PROFILE_TEST:-com.anurag9000.forestrun.HardwarePerformanceProfileTest}"
THRESHOLDS="${FOREST_RUN_PERFORMANCE_THRESHOLDS:-}"
CAPTURE_PERFETTO="${FOREST_RUN_CAPTURE_PERFETTO:-0}"
PERFETTO_DURATION="${FOREST_RUN_PERFETTO_DURATION:-120s}"
PERFETTO_CATEGORIES="${FOREST_RUN_PERFETTO_CATEGORIES:-sched freq idle am wm gfx view binder_driver hal dalvik input res memory power}"
THRESHOLDS_ABS=""
THRESHOLDS_SHA256=""
if [[ -n "$THRESHOLDS" ]]; then
  if [[ ! -f "$THRESHOLDS" ]]; then
    echo "Threshold manifest does not exist: $THRESHOLDS" >&2
    exit 1
  fi
  THRESHOLDS_ABS="$(cd "$(dirname "$THRESHOLDS")" && pwd)/$(basename "$THRESHOLDS")"
  THRESHOLDS_SHA256="$($PYTHON -c 'import hashlib, pathlib, sys; print(hashlib.sha256(pathlib.Path(sys.argv[1]).read_bytes()).hexdigest())' "$THRESHOLDS_ABS")"
fi

if [[ "$CAPTURE_PERFETTO" != "0" && "$CAPTURE_PERFETTO" != "1" ]]; then
  echo "FOREST_RUN_CAPTURE_PERFETTO must be 0 or 1." >&2
  exit 1
fi
if [[ "$CAPTURE_PERFETTO" == "1" && ! "$PERFETTO_DURATION" =~ ^[1-9][0-9]*(ms|s|m|h)$ ]]; then
  echo "FOREST_RUN_PERFETTO_DURATION must be a positive perfetto duration such as 30s or 2m." >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
"${ADB_DEVICE[@]}" shell rm -rf "$REMOTE_DIR"

capture_shell_diagnostic() {
  local output_name="$1"
  shift
  {
    printf '# candidate_sha=%s\n' "$CANDIDATE_SHA"
    printf '# captured_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '# command='
    printf '%q ' "$@"
    printf '\n'
    "${ADB_DEVICE[@]}" shell "$@"
  } > "${OUTPUT_DIR}/${output_name}" 2>&1 || {
    printf 'Diagnostic command failed; inspect output above.\n' >> "${OUTPUT_DIR}/${output_name}"
    return 0
  }
}

capture_device_health_snapshot() {
  local phase="$1"
  capture_shell_diagnostic "battery-${phase}.txt" dumpsys battery
  capture_shell_diagnostic "thermalservice-${phase}.txt" dumpsys thermalservice
  capture_shell_diagnostic "power-${phase}.txt" dumpsys power
  capture_shell_diagnostic "cpuinfo-${phase}.txt" dumpsys cpuinfo
  capture_shell_diagnostic "audio-${phase}.txt" dumpsys audio
  capture_shell_diagnostic "audio-flinger-${phase}.txt" dumpsys media.audio_flinger
}

{
  echo "candidate_sha=$CANDIDATE_SHA"
  echo "origin_main_sha=$ORIGIN_SHA"
  echo "serial=$SERIAL"
  echo "captured_at_utc=$TIMESTAMP"
  echo "test_selector=$TEST_SELECTOR"
  echo "application_id=$APP_ID"
  echo "manufacturer=$("${ADB_DEVICE[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$("${ADB_DEVICE[@]}" shell getprop ro.product.model | tr -d '\r')"
  echo "device=$("${ADB_DEVICE[@]}" shell getprop ro.product.device | tr -d '\r')"
  echo "sdk=$("${ADB_DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "build_fingerprint=$("${ADB_DEVICE[@]}" shell getprop ro.build.fingerprint | tr -d '\r')"
  echo "perfetto_capture=$CAPTURE_PERFETTO"
  if [[ "$CAPTURE_PERFETTO" == "1" ]]; then
    echo "perfetto_duration=$PERFETTO_DURATION"
    echo "perfetto_categories=$PERFETTO_CATEGORIES"
  fi
  if [[ -n "$THRESHOLDS_ABS" ]]; then
    echo "threshold_manifest=$THRESHOLDS_ABS"
    echo "threshold_manifest_sha256=$THRESHOLDS_SHA256"
  else
    echo "threshold_manifest=(not supplied)"
  fi
} > "${OUTPUT_DIR}/device.properties"

capture_device_health_snapshot before

PERFETTO_HOST_PID=""
PERFETTO_REMOTE_TRACE="/data/misc/perfetto-traces/forest-run-${TIMESTAMP}.perfetto-trace"
if [[ "$CAPTURE_PERFETTO" == "1" ]]; then
  # Perfetto tracing is intentionally opt-in because tracing itself can perturb
  # the timing run. Use it for a separate diagnostic capture of scheduling,
  # CPU/frequency, graphics/input, Dalvik/GC, memory, and power activity.
  read -r -a PERFETTO_CATEGORY_ARGS <<< "$PERFETTO_CATEGORIES"
  (
    "${ADB_DEVICE[@]}" shell perfetto \
      -o "$PERFETTO_REMOTE_TRACE" \
      -t "$PERFETTO_DURATION" \
      "${PERFETTO_CATEGORY_ARGS[@]}"
  ) > "${OUTPUT_DIR}/perfetto.log" 2>&1 &
  PERFETTO_HOST_PID="$!"
fi

set +e
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$TEST_SELECTOR" \
  --no-daemon --stacktrace --console=plain \
  | tee "${OUTPUT_DIR}/instrumentation.log"
GRADLE_STATUS="${PIPESTATUS[0]}"
set -e

if [[ -n "$PERFETTO_HOST_PID" ]]; then
  if ! wait "$PERFETTO_HOST_PID"; then
    echo "Requested Perfetto capture failed; inspect ${OUTPUT_DIR}/perfetto.log" >&2
    exit 1
  fi
  if ! "${ADB_DEVICE[@]}" pull "$PERFETTO_REMOTE_TRACE" "${OUTPUT_DIR}/system-trace.perfetto-trace"; then
    echo "Requested Perfetto trace could not be pulled from $PERFETTO_REMOTE_TRACE" >&2
    exit 1
  fi
  "${ADB_DEVICE[@]}" shell rm -f "$PERFETTO_REMOTE_TRACE" || true
  if [[ ! -s "${OUTPUT_DIR}/system-trace.perfetto-trace" ]]; then
    echo "Requested Perfetto trace is empty." >&2
    exit 1
  fi
fi

capture_device_health_snapshot after
capture_shell_diagnostic "gfxinfo-framestats-after.txt" dumpsys gfxinfo "$APP_ID" framestats
capture_shell_diagnostic "meminfo-after.txt" dumpsys meminfo "$APP_ID"
capture_shell_diagnostic "procstats-after.txt" dumpsys procstats --hours 3 "$APP_ID"
capture_shell_diagnostic "package-after.txt" dumpsys package "$APP_ID"

if [[ "$GRADLE_STATUS" -ne 0 ]]; then
  echo "Physical profiling instrumentation failed; diagnostics were retained in $OUTPUT_DIR" >&2
  exit "$GRADLE_STATUS"
fi

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

# Backward-compatible names used by existing operator notes and evidence tooling.
cp "${OUTPUT_DIR}/gfxinfo-framestats-after.txt" "${OUTPUT_DIR}/gfxinfo.txt"
cp "${OUTPUT_DIR}/meminfo-after.txt" "${OUTPUT_DIR}/meminfo.txt"
"${ADB_DEVICE[@]}" shell dumpsys display > "${OUTPUT_DIR}/display.txt" || true

if [[ -n "$THRESHOLDS_ABS" ]]; then
  "$PYTHON" scripts/evaluate_performance_profiles.py \
    --thresholds "$THRESHOLDS_ABS" \
    "${REPORT_PATHS[@]}" \
    | tee "${OUTPUT_DIR}/acceptance.txt"
else
  {
    echo "PENDING: reports were collected but no acceptance manifest was supplied."
    echo "Set FOREST_RUN_PERFORMANCE_THRESHOLDS to candidate-specific measured limits."
  } | tee "${OUTPUT_DIR}/acceptance.txt"
fi

FINAL_LOCAL_JSON="$($PYTHON scripts/verify_main_candidate.py --root "$ROOT_DIR" --expected-sha "$CANDIDATE_SHA" --json)"
FINAL_LOCAL_SHA="$($PYTHON -c 'import json,sys; print(json.load(sys.stdin)["sha"])' <<<"$FINAL_LOCAL_JSON")"
FINAL_ORIGIN_SHA="$(bash scripts/verify_origin_main.sh "$ROOT_DIR")"
if [[ "$FINAL_LOCAL_SHA" != "$CANDIDATE_SHA" || "$FINAL_ORIGIN_SHA" != "$CANDIDATE_SHA" ]]; then
  echo "Candidate or origin/main changed during performance capture." >&2
  echo "started=$CANDIDATE_SHA" >&2
  echo "local=$FINAL_LOCAL_SHA" >&2
  echo "origin/main=$FINAL_ORIGIN_SHA" >&2
  exit 1
fi

echo "Collected $REPORT_COUNT performance report(s) for $CANDIDATE_SHA in $OUTPUT_DIR"
