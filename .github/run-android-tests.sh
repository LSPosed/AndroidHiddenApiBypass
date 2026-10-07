#!/usr/bin/env bash
set -euo pipefail

test_package=org.lsposed.hiddenapibypass.library.test
runner="$test_package/androidx.test.runner.AndroidJUnitRunner"
test_apk=library/build/outputs/apk/androidTest/debug/library-debug-androidTest.apk
output_dir=library/build/outputs
hiddenapi_dir=$output_dir/hiddenapi
hiddenapi_test_class=org.lsposed.hiddenapibypass.HiddenApiBypassTest
device_hiddenapi_csv=/data/local/tmp/hiddenapi-flags.csv
device_hiddenapi_present_csv=/data/local/tmp/hiddenapi-present-fields.csv
device_hiddenapi_present_chunk_csv=/data/local/tmp/hiddenapi-present-fields-chunk.csv
hiddenapi_chunk_lines="${HIDDENAPI_CHUNK_LINES:-10000}"

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

copy_hiddenapi_csv_to_device() {
  host_csv="$1"
  adb push "$host_csv" "$device_hiddenapi_csv"
  adb shell chmod 0644 "$device_hiddenapi_csv"
}

prepare_hiddenapi_present_csv_on_device() {
  adb shell "cat /dev/null > $device_hiddenapi_present_csv && chmod 0666 $device_hiddenapi_present_csv"
}

split_hiddenapi_present_csv() {
  host_present_csv="$1"
  chunk_dir="$2"
  rm -rf "$chunk_dir"
  mkdir -p "$chunk_dir"
  awk -v outdir="$chunk_dir" -v lines="$hiddenapi_chunk_lines" '
    NR % lines == 1 {
      if (out) close(out)
      out = sprintf("%s/chunk-%04d.csv", outdir, int((NR - 1) / lines))
    }
    { print > out }
  ' "$host_present_csv"
}

run_hiddenapi_bypass_chunks() {
  host_present_csv="$1"
  chunk_dir="$hiddenapi_dir/hiddenapi-present-chunks"
  split_hiddenapi_present_csv "$host_present_csv" "$chunk_dir"

  chunk_index=0
  for host_chunk in "$chunk_dir"/chunk-*.csv; do
    if [ ! -e "$host_chunk" ]; then
      echo "No hidden API present CSV chunks were generated." >&2
      return 1
    fi
    adb push "$host_chunk" "$device_hiddenapi_present_chunk_csv"
    adb shell chmod 0644 "$device_hiddenapi_present_chunk_csv"
    force_stop_test_package
    run_instrumentation "hiddenapi-csv-bypass-$chunk_index" \
      -e class "$hiddenapi_test_class#ItestAllFieldsFromHiddenApiCsv" \
      -e hiddenapiCsv "$device_hiddenapi_present_chunk_csv"
    chunk_index=$((chunk_index + 1))
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
  copy_hiddenapi_csv_to_device "$host_csv"
  prepare_hiddenapi_present_csv_on_device

  force_stop_test_package
  run_instrumentation hiddenapi-csv-baseline \
    --no-hidden-api-checks \
    -e class "$hiddenapi_test_class#ItestExportPresentFieldsFromHiddenApiCsv" \
    -e hiddenapiCsv "$device_hiddenapi_csv" \
    -e hiddenapiPresentCsv "$device_hiddenapi_present_csv"

  host_present_csv="$hiddenapi_dir/hiddenapi-present-fields.csv"
  adb pull "$device_hiddenapi_present_csv" "$host_present_csv"
  run_hiddenapi_bypass_chunks "$host_present_csv"
}

./gradlew --no-configuration-cache :library:assembleDebugAndroidTest
wait_for_boot
adb uninstall "$test_package" || true
install_test_package
clear_test_package
run_instrumentation cold
run_hiddenapi_csv_ab_test
