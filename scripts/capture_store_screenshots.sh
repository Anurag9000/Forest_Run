#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB_BIN="${ADB_BIN:-$(command -v adb || true)}"
DEVICE_SERIAL="${DEVICE_SERIAL:-}"
PACKAGE_NAME="${PACKAGE_NAME:-com.yourname.forest_run.debug}"
ACTIVITY_NAME="${ACTIVITY_NAME:-com.yourname.forest_run.MainActivity}"
APK_PATH="${APK_PATH:-$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/release/google-play/screenshots/raw}"
STARTUP_TIMEOUT_S="${STARTUP_TIMEOUT_S:-15}"

if [[ -z "$ADB_BIN" || ! -x "$ADB_BIN" ]]; then
  echo "adb is unavailable. Set ADB_BIN or add adb to PATH." >&2
  exit 1
fi

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

mkdir -p "$OUTPUT_DIR"
find "$OUTPUT_DIR" -maxdepth 1 -type f -name '*.png' -delete

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
    --es debug_scenario "$scenario" >/dev/null
  wait_for_process
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
}

capture "01-opening" "OPENING_READABILITY" 2.0
capture "02-bloom" "BLOOM_SHOWCASE" 2.4
capture "03-ghost" "GHOST_READABILITY" 2.2
capture "04-rest" "REST_LOOP" 2.8
capture "05-cat" "CAT_KINDNESS" 2.1
capture "06-dog" "DOG_BUDDY" 2.2
capture "07-owl" "OWL_DIVE" 2.1
capture "08-jacaranda" "JACARANDA_PETALS" 2.0

printf 'Raw screenshots written to %s\n' "$OUTPUT_DIR"
