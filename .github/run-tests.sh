#!/bin/sh
# Runs the instrumented tests on the emulator started by LSPosed/android-emulator-runner.
#
# The action executes every line of its `script` input as a separate `sh -c`, so the workflow only
# calls this file and everything else lives here.
#
# The API 36/37 images do not survive a test run cleanly: the framework answers the boot checks,
# then dies while the tests run ("Failure calling service activity: Broken pipe (32)", followed by
# "Can't find service: activity/package") and does not come back on its own. The next connectedCheck
# then fails to install the test APK with "cmd: Can't find service: package" while Gradle still
# reports success, and further attempts fail the same way until the guest is rebooted.
#
# So: capture logcat from the start (a later `logcat -d` only sees what the ring buffers still
# hold, and the reboot wipes them), wait for the framework to stay up, and reboot the guest once and
# retry whenever a run did not actually produce test results. A guest reboot keeps installed
# packages and their data, which is what the second (-e load true) run reads its offset cache from.
set -u

cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
adb="$sdk/platform-tools/adb"
serial=emulator-5554
results=library/build/outputs/androidTest-results/connected/debug
diagnostics=emulator-diagnostics
logcat_full="$diagnostics/logcat-full.log"
attempts=2
logcat_pid=

framework_up() {
    "$adb" -s "$serial" shell cmd package list packages >/dev/null 2>&1 &&
        "$adb" -s "$serial" shell am get-current-user >/dev/null 2>&1
}

# Streams the log buffers into one file, so the reboot cannot take the evidence with it. The stats
# buffer is skipped on purpose: it is the bulk of the volume and carries no crash evidence.
start_logcat() {
    mkdir -p "$diagnostics"
    {
        echo "=== logcat capture started at $(date -u '+%Y-%m-%dT%H:%M:%SZ') ==="
        "$adb" -s "$serial" logcat -b main,system,crash,events -v threadtime
    } >> "$logcat_full" 2>&1 &
    logcat_pid=$!
}

stop_logcat() {
    if [ -n "$logcat_pid" ]; then
        kill "$logcat_pid" >/dev/null 2>&1 || true
        wait "$logcat_pid" 2>/dev/null || true
        logcat_pid=
    fi
}

trap 'stop_logcat' EXIT

# 0: framework is up and stayed up for a moment, 1: not up (yet), 2: guest booted but the framework
# is gone, which only a reboot fixes.
wait_for_framework() {
    booted=$("$adb" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    if [ "$booted" = "1" ] && ! framework_up; then
        return 2
    fi
    i=0
    while [ "$i" -lt 18 ]; do
        if framework_up; then
            # The services also have to survive the next few seconds, otherwise Gradle installs
            # into the window where the framework is coming back up.
            sleep 10
            framework_up && return 0
        fi
        i=$((i + 1))
        sleep 5
    done
    return 1
}

# capture_state <name>: dmesg and boot state, which logcat does not carry.
capture_state() {
    file="$diagnostics/framework-$1.txt"
    mkdir -p "$diagnostics"
    {
        echo "=== adb devices ==="
        "$adb" devices -l
        echo "=== sys.boot_completed ==="
        "$adb" -s "$serial" shell getprop sys.boot_completed
        echo "=== dmesg (tail) ==="
        "$adb" -s "$serial" shell dmesg | tail -n 500
    } > "$file" 2>&1 || true
    printf '%s\n' "$file"
}

report_crash_evidence() {
    echo "::warning::crash evidence ($logcat_full, $1):"
    grep -Ei 'FATAL EXCEPTION|beginning of crash|lowmemorykiller|lmkd|out of memory|SIGKILL|Watchdog|system_server|RescueParty' "$logcat_full" |
        tail -n 25 || true
    echo "::warning::last logcat lines before the reboot:"
    tail -n 15 "$logcat_full" || true
}

reboot_guest() {
    echo "::warning::rebooting $serial, the framework did not come back on its own"
    stop_logcat
    "$adb" -s "$serial" reboot >/dev/null 2>&1 || true
    "$adb" -s "$serial" wait-for-device >/dev/null 2>&1 || true
    start_logcat
}

# run_tests <label> <gradle argument...>
run_tests() {
    label="$1"
    slug=$(printf '%s' "$label" | tr ' ' '-')
    shift
    attempt=0
    while [ "$attempt" -lt "$attempts" ]; do
        attempt=$((attempt + 1))
        wait_for_framework
        ready=$?
        if [ "$ready" -eq 0 ]; then
            # Results of an earlier attempt must not be mistaken for this one's.
            rm -rf library/build/outputs/androidTest-results
            ./gradlew connectedCheck "$@"
            status=$?
            if ls "$results"/TEST-*.xml >/dev/null 2>&1; then
                if [ "$status" -ne 0 ]; then
                    echo "::error::$label: failing tests"
                    exit 1
                fi
                return 0
            fi
            echo "::warning::$label: attempt $attempt ran no test (the framework was not ready)"
        elif [ "$ready" -eq 2 ]; then
            echo "::warning::$label: attempt $attempt: the framework is gone while the guest is booted"
        else
            echo "::warning::$label: attempt $attempt: $serial framework services are not ready"
        fi
        report_crash_evidence "$(capture_state "$slug-$attempt")"
        reboot_guest
    done
    echo "::error::$label: no test results after $attempts attempts"
    exit 1
}

start_logcat
run_tests "first run" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
run_tests "load run" -Pandroid.testInstrumentationRunnerArguments.load=true
