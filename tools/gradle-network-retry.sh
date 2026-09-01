#!/usr/bin/env bash
# Retry only transient repository HTTP responses. Compilation/test failures remain final.
set -uo pipefail
log_file=$(mktemp)
trap 'rm -f "$log_file"' EXIT
result=1
for attempt in 1 2 3; do
  : > "$log_file"
  set +e
  ./gradlew "$@" 2>&1 | tee "$log_file"
  result=${PIPESTATUS[0]}
  set -e
  if [ "$result" -eq 0 ]; then exit 0; fi
  if ! grep -Eq 'Received status code (429|502|503|504)' "$log_file"; then exit "$result"; fi
  if [ "$attempt" -lt 3 ]; then
    delay=${GRADLE_RETRY_SLEEP_SECONDS:-20}
    echo "Transient dependency repository response; retrying Gradle in $((delay * attempt)) seconds."
    sleep $((delay * attempt))
  fi
done
exit "$result"
