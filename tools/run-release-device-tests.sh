#!/usr/bin/env bash
# Run the already-built release test APK with a bounded wait and retain device diagnostics.
set -uo pipefail
mkdir -p app/build/reports/release-device-diagnostics
timeout 10s adb logcat -b crash -c || true
timeout --kill-after=15s 8m bash tools/gradle-network-retry.sh :app:connectedReleaseAndroidTest --stacktrace &
test_pid=$!
crashed=false
while kill -0 "$test_pid" 2>/dev/null; do
  sleep 5
  timeout 10s adb logcat -b crash -d -v brief > app/build/reports/release-device-diagnostics/crash.txt 2>&1 || true
  if grep -q 'Process: com.indirgitsin.app.stable, PID:' app/build/reports/release-device-diagnostics/crash.txt; then
    echo 'Target process crashed during instrumentation; retaining diagnostics.'
    cat app/build/reports/release-device-diagnostics/crash.txt
    crashed=true
    kill -TERM "$test_pid" 2>/dev/null || true
    break
  fi
done
wait "$test_pid"
result=$?
if [ "$crashed" = true ]; then result=1; fi
# Diagnostics must not turn a failed test into success, or replace its original exit code.
timeout 20s adb logcat -d -v threadtime > app/build/reports/release-device-diagnostics/logcat.txt 2>&1 || true
timeout 20s adb shell dumpsys activity activities > app/build/reports/release-device-diagnostics/activities.txt 2>&1 || true
timeout 20s adb shell pm list instrumentation > app/build/reports/release-device-diagnostics/instrumentation.txt 2>&1 || true
exit "$result"
