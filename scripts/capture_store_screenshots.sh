#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB_BIN:-/home/anurag-basistha/Android/Sdk/platform-tools/adb}"
PACKAGE_NAME="${PACKAGE_NAME:-com.yourname.forest_run.debug}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.yourname.forest_run.MainActivity}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/release/google-play/screenshots/raw}"

if [[ ! -x "$ADB_BIN" ]]; then
  echo "adb not found or not executable at: $ADB_BIN" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"

device_count="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
if [[ "$device_count" -lt 1 ]]; then
  echo "No attached Android device detected by adb." >&2
  exit 1
fi

if [[ ! -f "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" ]]; then
  echo "Debug APK missing. Build it first with ./gradlew assembleDebug" >&2
  exit 1
fi

"$ADB_BIN" install -r "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk" >/dev/null

capture() {
  local name="$1"
  local scenario="$2"
  local wait_seconds="$3"
  echo "Capturing $name from scenario $scenario"
  "$ADB_BIN" shell am start -n "$PACKAGE_NAME/$ACTIVITY_NAME" --es debug_scenario "$scenario" >/dev/null
  sleep "$wait_seconds"
  "$ADB_BIN" exec-out screencap -p > "$OUTPUT_DIR/${name}.png"
}

capture "01-opening" "OPENING_READABILITY" 2.0
capture "02-bloom" "BLOOM_SHOWCASE" 2.4
capture "03-ghost" "GHOST_READABILITY" 2.2
capture "04-rest" "REST_LOOP" 2.8
capture "05-cat" "CAT_KINDNESS" 2.1
capture "06-dog" "DOG_BUDDY" 2.2
capture "07-owl" "OWL_DIVE" 2.1
capture "08-jacaranda" "JACARANDA_PETALS" 2.0

echo "Raw screenshots written to $OUTPUT_DIR"
