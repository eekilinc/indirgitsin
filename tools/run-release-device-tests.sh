#!/usr/bin/env bash
# Run the already-built release test APK with a bounded wait and retain device diagnostics.
set -uo pipefail
mkdir -p app/build/reports/release-device-diagnostics
timeout --kill-after=15s 8m ./gradlew :app:connectedReleaseAndroidTest --stacktrace
result=$?
# Diagnostics must not turn a failed test into success, or replace its original exit code.
timeout 20s adb logcat -d -v threadtime > app/build/reports/release-device-diagnostics/logcat.txt 2>&1 || true
timeout 20s adb shell dumpsys activity activities > app/build/reports/release-device-diagnostics/activities.txt 2>&1 || true
timeout 20s adb shell pm list instrumentation > app/build/reports/release-device-diagnostics/instrumentation.txt 2>&1 || true
exit "$result"
