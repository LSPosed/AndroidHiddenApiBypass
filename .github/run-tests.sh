#!/bin/sh
# Runs the instrumented tests on the emulator started by LSPosed/android-emulator-runner.
#
# The action executes every line of its `script` input as a separate `sh -c`, so the workflow only
# calls this file and everything else lives here.
#
# On API 36+ images the framework can answer while the emulator is still under load and then crash
# and come back a minute later (the runner's own broadcasts log "Can't find service: activity" and
# "Failure calling service activity: Broken pipe"). The first connectedCheck then fails to install
# the test APK with "cmd: Can't find service: package" while Gradle still reports success. Waiting
# once is therefore not enough: require the services to stay up, and retry a run until it actually
# produced test results.
set -u

cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
adb="$sdk/platform-tools/adb"
serial=emulator-5554
results=library/build/outputs/androidTest-results/connected/debug
attempts=4

wait_for_framework() {
    i=0
    while [ "$i" -lt 24 ]; do
        if "$adb" -s "$serial" shell cmd package list packages >/dev/null 2>&1 &&
            "$adb" -s "$serial" shell am get-current-user >/dev/null 2>&1; then
            # The services also have to survive the next few seconds, otherwise Gradle installs
            # into the restart window.
            sleep 10
            if "$adb" -s "$serial" shell cmd package list packages >/dev/null 2>&1; then
                return 0
            fi
        fi
        i=$((i + 1))
        sleep 5
    done
    return 1
}

# run_tests <label> <gradle argument...>
run_tests() {
    label="$1"
    shift
    attempt=0
    while [ "$attempt" -lt "$attempts" ]; do
        attempt=$((attempt + 1))
        if ! wait_for_framework; then
            echo "::error::$label: $serial framework services are not ready after 2 minutes"
            exit 1
        fi
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
        echo "::warning::$label: attempt $attempt ran no test (the framework was not ready), retrying"
        sleep 30
    done
    echo "::error::$label: no test results after $attempts attempts"
    exit 1
}

run_tests "first run" -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
run_tests "load run" -Pandroid.testInstrumentationRunnerArguments.load=true
