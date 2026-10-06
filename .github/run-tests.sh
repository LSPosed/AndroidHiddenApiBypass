#!/bin/sh
# Runs the instrumented tests on the emulator started by LSPosed/android-emulator-runner.
#
# The action executes every line of its `script` input as a separate `sh -c`, so the workflow only
# calls this file and everything else lives here.
#
# The action waits for sys.boot_completed and runs its own boot commands before this script, so both
# runs start right away. Host and guest memory are sampled while the tests run, and the guest state is
# captured on failure.
#
# Neither run is retried and the guest is never rebooted: a run that does not execute the tests fails
# the job. On failure the guest state is captured, because the emulator is gone by the time the
# workflow's own diagnostics step runs: logcat is streamed from the start (a `logcat -d` later only
# sees what the ring buffers still hold), and getprop, dmesg and the drop box are copied.
set -u

cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
adb="$sdk/platform-tools/adb"
serial=emulator-5554
results=library/build/outputs/androidTest-results/connected/debug
diagnostics=emulator-diagnostics
logcat_full="$diagnostics/logcat-full.log"
logcat_pid=
memory_pid=

prop() {
    "$adb" -s "$serial" shell getprop "$1" 2>/dev/null | tr -d '\r'
}

adb_is_root() {
    [ "$("$adb" -s "$serial" shell id -u 2>/dev/null | tr -d '\r')" = "0" ]
}

# The drop box lives in /data/system/dropbox and is only readable by root, so try to root adb. Images
# without a debuggable build refuse it, and then only the dumpsys fallback is available.
enable_root_adb() {
    "$adb" -s "$serial" root >/dev/null 2>&1 || true
    "$adb" -s "$serial" wait-for-device >/dev/null 2>&1 || true
    if adb_is_root; then
        echo "::notice::adb is root, the drop box can be copied"
    else
        echo "::warning::adb is not root (uid=$("$adb" -s "$serial" shell id -u 2>/dev/null | tr -d '\r')), the drop box files cannot be copied"
    fi
}

# Streams the log buffers into one file, which survives the end of the run. The stats buffer is
# skipped on purpose: it is the bulk of the volume and carries no crash evidence.
start_logcat() {
    mkdir -p "$diagnostics"
    {
        echo "=== logcat capture started at $(date -u '+%Y-%m-%dT%H:%M:%SZ') ==="
        "$adb" -s "$serial" logcat -b main,system,crash,events -v threadtime
    } >> "$logcat_full" 2>&1 &
    logcat_pid=$!
}

# Samples what the host and the guest have left while the tests run, which logcat cannot show.
start_memory_sampler() {
    mkdir -p "$diagnostics"
    while true; do
        {
            echo "=== $(date -u '+%Y-%m-%dT%H:%M:%SZ') ==="
            echo "--- host ---"
            free -m 2>/dev/null | sed -n '2p'
            ps -eo rss,comm --sort=-rss 2>/dev/null | sed -n '2,6p'
            echo "--- guest ---"
            "$adb" -s "$serial" shell 'head -n 3 /proc/meminfo' 2>/dev/null
        } >> "$diagnostics/memory.log" 2>&1
        sleep 15
    done &
    memory_pid=$!
}

stop_background_captures() {
    if [ -n "$logcat_pid" ]; then
        kill "$logcat_pid" >/dev/null 2>&1 || true
        wait "$logcat_pid" 2>/dev/null || true
        logcat_pid=
    fi
    if [ -n "$memory_pid" ]; then
        kill "$memory_pid" >/dev/null 2>&1 || true
        memory_pid=
    fi
}

trap 'stop_background_captures' EXIT

# capture_state <name>: getprop, dmesg, boot state and the drop box, which logcat does not carry.
capture_state() {
    name="$1"
    mkdir -p "$diagnostics"
    {
        echo "=== adb devices ==="
        "$adb" devices -l
        echo "=== boot_completed / ce_available ==="
        prop sys.boot_completed
        prop sys.user.0.ce_available
        echo "=== getprop ==="
        "$adb" -s "$serial" shell getprop
        echo "=== dmesg (tail) ==="
        "$adb" -s "$serial" shell dmesg | tail -n 500
    } > "$diagnostics/framework-$name.txt" 2>&1 || true
    rm -rf "$diagnostics/dropbox"
    if adb_is_root; then
        # Not `adb shell ... | tar`: adb shell allocates a pty, which mangles the binary stream.
        "$adb" -s "$serial" pull /data/system/dropbox "$diagnostics/dropbox" > "$diagnostics/dropbox-$name.txt" 2>&1 || true
    else
        echo "adb is not root, the drop box files cannot be read" > "$diagnostics/dropbox-$name.txt"
    fi
    {
        echo "=== dumpsys dropbox (only while system_server is alive) ==="
        "$adb" -s "$serial" shell dumpsys dropbox --print
    } >> "$diagnostics/dropbox-$name.txt" 2>&1 || true
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
    echo "::warning::last logcat lines:"
    tail -n 15 "$logcat_full" || true
}

fail_with_diagnostics() {
    label="$1"
    slug="$2"
    message="$3"
    capture_state "$slug"
    report_crash_evidence "$slug"
    echo "::error::$label: $message"
    exit 1
}

# run_phase <label> <gradle argument...>: exits 1 unless the tests ran, and 0 when they ran and
# passed.
run_phase() {
    label="$1"
    slug=$(printf '%s' "$label" | tr ' ' '-')
    shift
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
    fail_with_diagnostics "$label" "$slug" "produced no test results"
}

enable_root_adb
start_logcat
start_memory_sampler
run_phase "first run" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
run_phase "load run" -Pandroid.testInstrumentationRunnerArguments.load=true
