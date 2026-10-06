#!/usr/bin/env bash
set -euo pipefail

test_package=org.lsposed.hiddenapibypass.library.test
runner="$test_package/androidx.test.runner.AndroidJUnitRunner"
test_apk=library/build/outputs/apk/androidTest/debug/library-debug-androidTest.apk

run_instrumentation() {
  label="$1"
  shift
  log="library/build/outputs/androidTest-$label.txt"

  set +e
  adb shell am instrument -w "$@" "$runner" > "$log" 2>&1
  exit_code=$?
  set -e
  cat "$log"

  if [ "$exit_code" -ne 0 ] || ! grep -q "OK (" "$log" || grep -q "FAILURES!!!" "$log"; then
    return 1
  fi
}

wait_for_boot() {
  adb wait-for-device
  until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 1
  done
  until adb shell cmd package list packages android >/dev/null 2>&1; do
    sleep 1
  done
}

./gradlew --no-configuration-cache :library:assembleDebugAndroidTest
wait_for_boot
adb uninstall "$test_package" || true
adb install --no-streaming -r -t "$test_apk"
adb shell pm clear "$test_package"
run_instrumentation cold
