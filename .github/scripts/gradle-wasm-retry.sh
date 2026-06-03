#!/usr/bin/env bash
set -euo pipefail

if [[ $# -eq 0 ]]; then
  echo "usage: $0 <gradle args...>" >&2
  exit 2
fi

attempts="${GRADLE_RETRY_ATTEMPTS:-3}"
delay_seconds="${GRADLE_RETRY_DELAY_SECONDS:-20}"

for attempt in $(seq 1 "$attempts"); do
  if ./gradlew "$@"; then
    exit 0
  else
    status=$?
  fi

  if [[ "$attempt" == "$attempts" ]]; then
    exit "$status"
  fi

  echo "::warning::Gradle Wasm command failed on attempt $attempt/$attempts; retrying in ${delay_seconds}s"
  sleep "$delay_seconds"
  delay_seconds=$((delay_seconds * 2))
done
