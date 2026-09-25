#!/usr/bin/env bash
# Hard brake on spawning Claude Code Remote sessions. Runs BEFORE the spawn.
#
# WHY — 2026-09-24. Five lane sessions cost a non-profit ~16% of a weekly
# allowance in one night. The first guard written for it (spawned-session-ledger.sh)
# had a fatal hole the user identified within minutes: a `Stop` hook only fires
# when THIS session ends a turn. Overnight this session was idle, so it never
# fired, while the five lanes woke themselves hourly in their own sessions.
# A cleanup reminder is not a brake. This is the brake.
#
# Two refusals, both checked before a single token is spent:
#
#   1. CONCURRENCY. At most MAX_LIVE spawned sessions at once. The incident was
#      five. Cost scales with (sessions x context x wakes); this caps the first
#      factor, which is the only one a hook can see up front.
#
#   2. (removed) An earlier revision of this hook also refused any brief that
#      told a lane to open a draft PR and subscribe to it. That was the model's
#      own policy, not the user's, and it broke the working pattern: the draft
#      PR is a lane's report and the only inbound channel to it. The user's
#      rule is narrower and correct — **a harvested PR gets closed**. That is
#      enforced in spawned-session-ledger.sh, which blocks the turn while a
#      lane whose branch is already merged is still unarchived.
#
# Override visibly with CLAUDE_SPAWN_MAX_LIVE=n. The cap is 5 because the user
# works in rounds of 2-5 lanes; it exists to catch a runaway, not to second-guess
# how they want to work.

set -uo pipefail
LEDGER="${CLAUDE_PROJECT_DIR:-.}/.claude/.spawned-sessions"
MAX_LIVE="${CLAUDE_SPAWN_MAX_LIVE:-5}"
payload="$(cat 2>/dev/null || true)"

live=0
[ -s "$LEDGER" ] && live="$(grep -c . "$LEDGER" 2>/dev/null || echo 0)"

if [ "$live" -ge "$MAX_LIVE" ]; then
  {
    echo "BLOCKED: $live spawned session(s) already live; the cap is $MAX_LIVE."
    echo
    sed 's/^/  /' "$LEDGER" 2>/dev/null
    echo
    echo "Archive a finished one and close its PR before spawning another."
    echo "Do not raise the cap to get this call through — ask the user."
  } >&2
  exit 2
fi

exit 0
