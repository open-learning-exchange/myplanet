#!/usr/bin/env bash
# Runs ./gradlew for CI with two hands-off safety nets, then exits with Gradle's real status.
#   1. Dependencies resolve through a Maven Central mirror first (ci-mirror.init.gradle).
#   2. A failure caused by the network (429 Too Many Requests, 5xx, timeouts, resets) is
#      retried with backoff. Any other failure — a real compile or test error — is not.
# A stale Kotlin incremental cache is retried once without app/build/kotlin, as before.
#
# Usage: .github/scripts/gradle-ci.sh <gradle args...>
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
init="$here/../gradle/ci-mirror.init.gradle"
log="${RUNNER_TEMP:-/tmp}/gradle-ci.log"
max_retries="${GRADLE_CI_MAX_RETRIES:-3}"
backoffs=(${GRADLE_CI_BACKOFFS:-20 60 120})

# Gradle's own per-request retry, on top of the wrapper's whole-build retry.
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
    echo "::warning::stale Kotlin incremental caches in the restored build dir; retrying without them"
    rm -rf app/build/kotlin
    continue
  fi

  if grep -Eq "$transient" "$log" && [ "$attempt" -lt "$max_retries" ]; then
    wait="${backoffs[$attempt]:-${backoffs[${#backoffs[@]}-1]}}"
    attempt=$((attempt + 1))
    echo "::warning::dependency download failed (rate limit / network); retry $attempt/$max_retries in ${wait}s"
    sleep "$wait"
    continue
  fi

  exit "$status"
done
