#!/usr/bin/env bash
set -euo pipefail

test_package=org.lsposed.hiddenapibypass.library.test
runner="$test_package/androidx.test.runner.AndroidJUnitRunner"
test_apk=library/build/outputs/apk/androidTest/debug/library-debug-androidTest.apk
output_dir=library/build/outputs
hiddenapi_dir=$output_dir/hiddenapi
hiddenapi_test_class=org.lsposed.hiddenapibypass.HiddenApiBypassTest
device_hiddenapi_csv=/data/data/$test_package/files/hiddenapi-flags.csv
device_hiddenapi_present_csv=/data/data/$test_package/files/hiddenapi-present-fields.csv
hiddenapi_settings=(hidden_api_policy hidden_api_policy_pre_p_apps hidden_api_policy_p_apps)
hiddenapi_original_settings=()
hiddenapi_settings_saved=0

run_instrumentation() {
  label="$1"
  shift
  log="$output_dir/androidTest-$label.txt"

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
  wait_for_package_service
}

wait_for_package_service() {
  until adb shell cmd package list packages android >/dev/null 2>&1; do
    sleep 1
  done
}

clear_test_package() {
  for attempt in {1..30}; do
    if adb shell pm clear "$test_package"; then
      return 0
    fi
    sleep 1
    wait_for_package_service
  done
  return 1
}

install_test_package() {
  for attempt in {1..30}; do
    wait_for_package_service
    if adb install --no-streaming -r -t "$test_apk"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

decode_base64() {
  if base64 --decode </dev/null >/dev/null 2>&1; then
    base64 --decode
  else
    base64 -D
  fi
}

hiddenapi_ref_for_api() {
  case "$1" in
    30|30.*) echo android-11.0.0_r1 ;;
    31|31.*) echo android-12.0.0_r1 ;;
    32|32.*) echo android-12.1.0_r1 ;;
    33|33.*) echo android-13.0.0_r1 ;;
    34|34.*) echo android-14.0.0_r1 ;;
    35|35.*) echo android-15.0.0_r1 ;;
    36.1*) echo android-16.0.0_r3 ;;
    36*) echo android-16.0.0_r1 ;;
    37*|CANARY|canary*) echo android-17.0.0_r1 ;;
    *) return 1 ;;
  esac
}

detect_hiddenapi_api_level() {
  if [ -n "${ANDROID_API_LEVEL:-}" ]; then
    printf '%s\n' "$ANDROID_API_LEVEL"
    return
  fi

  api_full="$(adb shell getprop ro.build.version.sdk_full 2>/dev/null | tr -d '\r')"
  if [ -n "$api_full" ]; then
    printf '%s\n' "$api_full"
    return
  fi

  adb shell getprop ro.build.version.sdk | tr -d '\r'
}

download_hiddenapi_flags_csv() {
  api_level="$(detect_hiddenapi_api_level)"
  if ! ref="$(hiddenapi_ref_for_api "$api_level")"; then
    echo "No prebuilts/runtime hiddenapi-flags.csv mapping for API $api_level; skipping CSV A/B test." >&2
    return 1
  fi

  mkdir -p "$hiddenapi_dir"
  host_csv="$hiddenapi_dir/hiddenapi-flags-$api_level.csv"
  if [ ! -s "$host_csv" ]; then
    url="https://android.googlesource.com/platform/prebuilts/runtime/+/$ref/appcompat/hiddenapi-flags.csv?format=TEXT"
    echo "Downloading hidden API flags for API $api_level from $ref." >&2
    curl -fsSL "$url" | decode_base64 > "$host_csv.tmp"
    mv "$host_csv.tmp" "$host_csv"
  fi

  printf '%s\n' "$host_csv"
}

copy_hiddenapi_csv_to_test_app() {
  host_csv="$1"
  adb shell "run-as $test_package sh -c 'mkdir -p files && cat > files/hiddenapi-flags.csv'" < "$host_csv"
}

save_hiddenapi_settings() {
  if [ "$hiddenapi_settings_saved" -eq 1 ]; then
    return
  fi

  hiddenapi_original_settings=()
  for key in "${hiddenapi_settings[@]}"; do
    value="$(adb shell settings get global "$key" 2>/dev/null | tr -d '\r' || true)"
    hiddenapi_original_settings+=("${value:-null}")
  done
  hiddenapi_settings_saved=1
}

restore_hiddenapi_settings() {
  if [ "$hiddenapi_settings_saved" -ne 1 ]; then
    return
  fi

  for i in "${!hiddenapi_settings[@]}"; do
    key="${hiddenapi_settings[$i]}"
    value="${hiddenapi_original_settings[$i]}"
    if [ "$value" = "null" ]; then
      adb shell settings delete global "$key" >/dev/null 2>&1 || true
    else
      adb shell settings put global "$key" "$value" >/dev/null 2>&1 || true
    fi
  done
}

set_hiddenapi_policy_disabled() {
  save_hiddenapi_settings
  for key in "${hiddenapi_settings[@]}"; do
    adb shell settings put global "$key" 0 >/dev/null
  done
}

set_hiddenapi_policy_default() {
  for key in "${hiddenapi_settings[@]}"; do
    adb shell settings delete global "$key" >/dev/null 2>&1 || true
  done
}

force_stop_test_package() {
  adb shell am force-stop "$test_package" >/dev/null 2>&1 || true
}

run_hiddenapi_csv_ab_test() {
  if ! host_csv="$(download_hiddenapi_flags_csv)"; then
    return
  fi

  clear_test_package
  copy_hiddenapi_csv_to_test_app "$host_csv"

  set_hiddenapi_policy_disabled
  force_stop_test_package
  run_instrumentation hiddenapi-csv-baseline \
    -e class "$hiddenapi_test_class#ItestExportPresentFieldsFromHiddenApiCsv" \
    -e hiddenapiCsv "$device_hiddenapi_csv" \
    -e hiddenapiPresentCsv "$device_hiddenapi_present_csv"

  set_hiddenapi_policy_default
  force_stop_test_package
  run_instrumentation hiddenapi-csv-bypass \
    -e class "$hiddenapi_test_class#ItestAllFieldsFromHiddenApiCsv" \
    -e hiddenapiCsv "$device_hiddenapi_present_csv"
}

trap restore_hiddenapi_settings EXIT

./gradlew --no-configuration-cache :library:assembleDebugAndroidTest
wait_for_boot
adb uninstall "$test_package" || true
install_test_package
clear_test_package
run_instrumentation cold
run_hiddenapi_csv_ab_test
