#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB_BIN:-$(command -v adb || true)}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
PACKAGE_NAME="${PACKAGE_NAME:-com.anurag9000.forestrun.debug}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.anurag9000.forestrun.MainActivity}"
APK_PATH="${APK_PATH:-$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/release/google-play/screenshots/raw}"
STARTUP_TIMEOUT_S="${STARTUP_TIMEOUT_S:-15}"
READY_PREFIX="FOREST_RUN_SCENARIO_READY"
RUN_MODE="SCREENSHOT_CAPTURE"

if [[ -z "$ADB_BIN" || ! -x "$ADB_BIN" ]]; then
  echo "adb is unavailable. Set ADB_BIN or add adb to PATH." >&2
  exit 1
fi
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "Python interpreter '$PYTHON_BIN' is unavailable." >&2
  exit 1
fi
if ! git -C "$ROOT_DIR" diff --quiet || ! git -C "$ROOT_DIR" diff --cached --quiet; then
  echo "Screenshot evidence must be captured from a clean tracked candidate tree." >&2
  exit 1
fi

CANDIDATE_SHA="$(git -C "$ROOT_DIR" rev-parse HEAD)"
COMMIT_EPOCH="$(git -C "$ROOT_DIR" show -s --format=%ct HEAD)"

mapfile -t devices < <("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {print $1}')
if [[ ${#devices[@]} -eq 0 ]]; then
  echo "No authorized Android device is attached." >&2
  exit 1
fi

if [[ -n "$DEVICE_SERIAL" ]]; then
  if [[ ! " ${devices[*]} " =~ " ${DEVICE_SERIAL} " ]]; then
    echo "DEVICE_SERIAL=$DEVICE_SERIAL is not an attached authorized device." >&2
    printf 'Available devices: %s\n' "${devices[*]}" >&2
    exit 1
  fi
elif [[ ${#devices[@]} -eq 1 ]]; then
  DEVICE_SERIAL="${devices[0]}"
else
  echo "Multiple devices are attached; set DEVICE_SERIAL explicitly." >&2
  printf 'Available devices: %s\n' "${devices[*]}" >&2
  exit 1
fi

ADB=("$ADB_BIN" -s "$DEVICE_SERIAL")

if [[ ! -f "$APK_PATH" ]]; then
  echo "Debug APK missing at $APK_PATH. Run ./gradlew assembleDebug first." >&2
  exit 1
fi

APK_SHA256="$($PYTHON_BIN - "$APK_PATH" "$COMMIT_EPOCH" <<'PY'
import hashlib
import os
import sys
from pathlib import Path

path = Path(sys.argv[1])
commit_epoch = int(sys.argv[2])
if int(path.stat().st_mtime) < commit_epoch:
    raise SystemExit(
        f"Debug APK predates candidate commit: {path}. Rebuild from the current clean SHA."
    )
digest = hashlib.sha256()
with path.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
print(digest.hexdigest())
PY
)"

mkdir -p "$OUTPUT_DIR"
find "$OUTPUT_DIR" -maxdepth 1 -type f \( -name '*.png' -o -name '*.capture.json' \) -delete

printf 'Installing %s on %s\n' "$APK_PATH" "$DEVICE_SERIAL"
"${ADB[@]}" install -r "$APK_PATH" >/dev/null

wait_for_process() {
  local deadline=$((SECONDS + STARTUP_TIMEOUT_S))
  while (( SECONDS < deadline )); do
    if [[ -n "$("${ADB[@]}" shell pidof "$PACKAGE_NAME" 2>/dev/null | tr -d '\r')" ]]; then
      return 0
    fi
    sleep 0.25
  done
  echo "Timed out waiting for $PACKAGE_NAME to start." >&2
  return 1
}

wait_for_scenario() {
  local scenario="$1"
  local marker="$READY_PREFIX scenario=$scenario mode=$RUN_MODE"
  local deadline=$((SECONDS + STARTUP_TIMEOUT_S))
  while (( SECONDS < deadline )); do
    if "${ADB[@]}" logcat -d -v brief -s ForestRunLaunch:I 2>/dev/null | grep -Fq "$marker"; then
      printf '%s\n' "$marker"
      return 0
    fi
    sleep 0.25
  done
  echo "Timed out waiting for verified scenario marker: $marker" >&2
  "${ADB[@]}" logcat -d -v brief -s ForestRunLaunch:I 2>/dev/null >&2 || true
  return 1
}

write_capture_evidence() {
  local destination="$1"
  local scenario="$2"
  local settle_seconds="$3"
  local readiness_marker="$4"
  "$PYTHON_BIN" - \
    "$destination" \
    "$scenario" \
    "$settle_seconds" \
    "$readiness_marker" \
    "$CANDIDATE_SHA" \
    "$APK_SHA256" \
    "$DEVICE_SERIAL" \
    "$PACKAGE_NAME" \
    "$ACTIVITY_NAME" \
    "$RUN_MODE" <<'PY'
import datetime
import hashlib
import json
import struct
import sys
from pathlib import Path

(
    raw_path,
    scenario,
    settle_seconds,
    readiness_marker,
    candidate_sha,
    apk_sha256,
    device_serial,
    package_name,
    activity_name,
    run_mode,
) = sys.argv[1:]
path = Path(raw_path)
content = path.read_bytes()
if len(content) < 24 or content[:8] != b"\x89PNG\r\n\x1a\n":
    raise SystemExit(f"Invalid PNG while writing capture evidence: {path}")
width, height = struct.unpack(">II", content[16:24])
image_sha256 = hashlib.sha256(content).hexdigest()
evidence = {
    "schemaVersion": 1,
    "rawFile": path.name,
    "scenario": scenario,
    "runMode": run_mode,
    "readinessMarker": readiness_marker,
    "candidateSha": candidate_sha,
    "apkSha256": apk_sha256,
    "deviceSerial": device_serial,
    "packageName": package_name,
    "activityName": activity_name,
    "settleSeconds": float(settle_seconds),
    "capturedAtUtc": datetime.datetime.now(datetime.timezone.utc).isoformat(),
    "imageSha256": image_sha256,
    "width": width,
    "height": height,
}
sidecar = path.with_suffix(".capture.json")
sidecar.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
}

capture() {
  local name="$1"
  local scenario="$2"
  local settle_seconds="$3"
  local destination="$OUTPUT_DIR/${name}.png"

  printf 'Capturing %-18s scenario=%s\n' "$name" "$scenario"
  "${ADB[@]}" shell am force-stop "$PACKAGE_NAME"
  "${ADB[@]}" logcat -c
  "${ADB[@]}" shell am start -S -W \
    -n "$PACKAGE_NAME/$ACTIVITY_NAME" \
    --ez debug_autostart true \
    --es debug_scenario "$scenario" \
    --es run_mode "$RUN_MODE" >/dev/null
  wait_for_process
  local readiness_marker
  readiness_marker="$(wait_for_scenario "$scenario")"
  sleep "$settle_seconds"
  "${ADB[@]}" exec-out screencap -p > "$destination"

  if [[ ! -s "$destination" ]]; then
    echo "Screenshot capture produced an empty file: $destination" >&2
    exit 1
  fi
  if [[ "$(head -c 8 "$destination" | od -An -t x1 | tr -d ' \n')" != "89504e470d0a1a0a" ]]; then
    echo "Screenshot is not a valid PNG: $destination" >&2
    exit 1
  fi
  write_capture_evidence "$destination" "$scenario" "$settle_seconds" "$readiness_marker"
}

capture "01-opening" "OPENING_READABILITY" 2.0
capture "02-bloom" "BLOOM_SHOWCASE" 2.4
capture "03-ghost" "GHOST_READABILITY" 2.2
capture "04-rest" "REST_LOOP" 2.8
capture "05-cat" "CAT_KINDNESS" 2.1
capture "06-dog" "DOG_BUDDY" 2.2
capture "07-owl" "OWL_DIVE" 2.1
capture "08-jacaranda" "JACARANDA_PETALS" 2.0

printf 'Raw screenshots and capture evidence written to %s\n' "$OUTPUT_DIR"
