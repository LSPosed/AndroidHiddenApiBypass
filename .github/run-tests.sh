#!/bin/sh
# Runs the instrumented tests on the emulator started by LSPosed/android-emulator-runner.
#
# The action executes every line of its `script` input as a separate `sh -c`, so the workflow only
# calls this file and everything else lives here.
#
# The API 36/37 images do not survive a test run cleanly: system_server restarts on its own while
# the tests run ("Failure calling service activity: Broken pipe (32)", then "Can't find service:
# activity/package"), and while it is coming back the next connectedCheck fails to install the test
# APK with "cmd: Can't find service: package" even though Gradle reports success. It does not
# recover on its own, so the guest has to be rebooted.
#
# A reboot on a `-read-only` AVD starts from the base image: installed packages are gone and the
# offset cache written by the first run is gone too, so the second (-e load true) run would fail
# AAtestCachedDataLoaded. Both phases therefore live in one sequence that gets restarted after any
# reboot, and no reboot happens between the run that writes the cache and the run that reads it.
#
# The wait also requires a completed boot and an unlocked user: running the tests while system_server
# is still starting makes the install fail, and running them before user 0 is unlocked
# (sys.user.0.ce_available) makes ContextImpl fail to create the app's CE cache dir, which silently
# drops the offset cache again.
#
# logcat is streamed from the start, because a `logcat -d` at failure time only sees what the ring
# buffers still hold and the reboot wipes them.
set -u

cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
adb="$sdk/platform-tools/adb"
serial=emulator-5554
results=library/build/outputs/androidTest-results/connected/debug
diagnostics=emulator-diagnostics
logcat_full="$diagnostics/logcat-full.log"
rounds=2
logcat_pid=

prop() {
    "$adb" -s "$serial" shell getprop "$1" 2>/dev/null | tr -d '\r'
}

# The drop box lives in /data/system/dropbox, is only readable by root, and its service dies with
# system_server, so it has to be copied as files. User builds refuse this and fall back to dumpsys.
enable_root_adb() {
    "$adb" -s "$serial" root >/dev/null 2>&1 || true
    "$adb" -s "$serial" wait-for-device >/dev/null 2>&1 || true
}

services_up() {
    "$adb" -s "$serial" shell cmd package list packages >/dev/null 2>&1 &&
        "$adb" -s "$serial" shell am get-current-user >/dev/null 2>&1
}

ready() {
    [ "$(prop sys.boot_completed)" = "1" ] && services_up && [ "$(prop sys.user.0.ce_available)" = "true" ]
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

# 0: booted, services up and user 0 unlocked; 1: not ready yet; 2: booted but the framework is gone,
# which only a reboot fixes.
wait_for_ready() {
    if [ "$(prop sys.boot_completed)" = "1" ] && ! services_up; then
        return 2
    fi
    i=0
    while [ "$i" -lt 24 ]; do
        if ready; then
            # Keep checking for a moment, so Gradle does not start installing into a framework that
            # is about to go away again.
            sleep 10
            ready && return 0
        fi
        i=$((i + 1))
        sleep 5
    done
    return 1
}

# capture_state <name>: dmesg, boot state and the drop box, which logcat does not carry. The drop
# box holds the watchdog and tombstone reports for a dead system_server, and both the reboot and the
# `-read-only` AVD take them away.
capture_state() {
    name="$1"
    mkdir -p "$diagnostics"
    {
        echo "=== adb devices ==="
        "$adb" devices -l
        echo "=== boot_completed / ce_available ==="
        prop sys.boot_completed
        prop sys.user.0.ce_available
        echo "=== dmesg (tail) ==="
        "$adb" -s "$serial" shell dmesg | tail -n 500
    } > "$diagnostics/framework-$name.txt" 2>&1 || true
    rm -rf "$diagnostics/dropbox"
    # Not `adb shell ... | tar`: adb shell allocates a pty, which mangles the binary stream.
    "$adb" -s "$serial" pull /data/system/dropbox "$diagnostics/dropbox" >> "$diagnostics/dropbox-$name.txt" 2>&1 || true
    "$adb" -s "$serial" shell dumpsys dropbox >> "$diagnostics/dropbox-$name.txt" 2>&1 || true
}

report_crash_evidence() {
    slug="$1"
    dropbox="$diagnostics/dropbox"
    echo "::warning::crash evidence ($logcat_full, $dropbox, $diagnostics/dropbox-$slug.txt):"
    grep -Ei 'FATAL EXCEPTION|beginning of crash|lowmemorykiller|lmkd|out of memory|SIGKILL|Watchdog|system_server|RescueParty' "$logcat_full" |
        tail -n 25 || true
    if [ -d "$dropbox" ]; then
        echo "::warning::newest drop box entries:"
        ls -t "$dropbox" | head -n 10 || true
        grep -rlE 'WATCHDOG KILLING|system_server_watchdog|native crash|native_crash|SYSTEM_TOMBSTONE|SYSTEM_RESTART|ANR in' "$dropbox" 2>/dev/null |
            tail -n 5 || true
    fi
    echo "::warning::last logcat lines before the reboot:"
    tail -n 15 "$logcat_full" || true
}

reboot_guest() {
    echo "::warning::rebooting $serial"
    stop_logcat
    "$adb" -s "$serial" reboot >/dev/null 2>&1 || true
    "$adb" -s "$serial" wait-for-device >/dev/null 2>&1 || true
    enable_root_adb
    start_logcat
}

# run_phase <label> <gradle argument...>: 0 when the tests ran and passed, 1 when the guest had to be
# rebooted, and a hard exit when the tests ran and failed.
run_phase() {
    label="$1"
    slug=$(printf '%s' "$label" | tr ' ' '-')
    shift
    wait_for_ready
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
        echo "::warning::$label: ran no test (the framework was not ready)"
    elif [ "$ready" -eq 2 ]; then
        echo "::warning::$label: the framework is gone while the guest is booted"
    else
        echo "::warning::$label: not ready (boot_completed=$(prop sys.boot_completed) ce_available=$(prop sys.user.0.ce_available))"
    fi
    capture_state "$slug"
    report_crash_evidence "$slug"
    reboot_guest
    return 1
}

enable_root_adb
start_logcat
round=0
while [ "$round" -lt "$rounds" ]; do
    round=$((round + 1))
    echo "=== round $round ==="
    if run_phase "first run" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true &&
        run_phase "load run" -Pandroid.testInstrumentationRunnerArguments.load=true; then
        exit 0
    fi
    echo "::warning::starting over, so the offset cache the load run reads is written after the reboot"
done
echo "::error::no test results after $rounds rounds"
exit 1
