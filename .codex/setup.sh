#!/usr/bin/env bash
# Codex Cloud runs this once when it creates the repository environment. Make
# the shared agent skills available before Codex starts working. Keep setup
# non-fatal so a transient network outage does not prevent a task from running.
set -uo pipefail

cd "$(dirname "$0")/.." || {
  echo "setup.sh: cannot locate workspace root" >&2
  exit 1
}

git submodule update --init --recursive ||
  echo "setup.sh: submodule init failed (offline?) — continuing without uninitialized skills" >&2

exit 0
