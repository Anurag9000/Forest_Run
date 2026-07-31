#!/usr/bin/env bash
set -euo pipefail

readonly SERIAL="${ANDROID_SERIAL:-emulator-${EMULATOR_PORT:-5554}}"
readonly READINESS_TIMEOUT_SECONDS="${FOREST_RUN_EMULATOR_READINESS_TIMEOUT_SECONDS:-240}"
readonly POLL_SECONDS=5

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

    if ! adb devices | grep -q "^${SERIAL}[[:space:]]"; then
      adb start-server >/dev/null 2>&1 || true
    fi
    sleep "${POLL_SECONDS}"
  done

  echo "Timed out waiting ${READINESS_TIMEOUT_SECONDS}s for Android services on ${SERIAL}." >&2
  return 1
}

adb wait-for-device
wait_for_android_services

./gradlew connectedDebugAndroidTest \
  --no-daemon \
  --stacktrace \
  --console=plain
