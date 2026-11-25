#!/usr/bin/env bash
# Runs ./gradlew for CI via the Maven Central mirror, retrying download/network failures
# (never compile or test failures) and a stale Kotlin incremental cache once.
set -uo pipefail

init="$(dirname "${BASH_SOURCE[0]}")/../gradle/ci-mirror.init.gradle"
log="${RUNNER_TEMP:-/tmp}/gradle-ci.log"
max_retries="${GRADLE_CI_MAX_RETRIES:-3}"
backoffs=(${GRADLE_CI_BACKOFFS:-20 60 120})
args=(-I "$init" -Dorg.gradle.internal.repository.max.retries=5 -Dorg.gradle.internal.repository.initial.backoff=2000 "$@")
transient='Too Many Requests|status code (429|5[0-9][0-9])|Could not (GET|HEAD) |Read timed out|Connect timed out|Connection reset|Premature end of|Remote host terminated|Received fatal alert|Unexpected end of file'

attempt=0
incremental_retried=0
while true; do
  ./gradlew "${args[@]}" 2>&1 | tee "$log"
  status=${PIPESTATUS[0]}
  [ "$status" -eq 0 ] && exit 0

  if [ "$incremental_retried" -eq 0 ] && grep -q "Incremental compilation failed" "$log"; then
    incremental_retried=1
    echo "::warning::stale Kotlin incremental caches; retrying without them"
    rm -rf app/build/kotlin
    continue
  fi

  if grep -Eq "$transient" "$log" && [ "$attempt" -lt "$max_retries" ]; then
    wait="${backoffs[$attempt]:-${backoffs[${#backoffs[@]}-1]}}"
    attempt=$((attempt + 1))
    echo "::warning::download failed (rate limit / network); retry $attempt/$max_retries in ${wait}s"
    sleep "$wait"
    continue
  fi

  exit "$status"
done
