#!/usr/bin/env bash
# OpenHands runs this at workspace initialization — before session-level skill
# discovery scans .agents/skills/. Submodules must be initialized by then, or
# the gitlinks are empty directories and no SKILL.md is found.
set -euo pipefail
cd "$(dirname "$0")/.."
git submodule update --init --recursive
