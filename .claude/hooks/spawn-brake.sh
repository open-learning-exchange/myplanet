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
#   2. THE BABYSIT LOOP. Refuse any brief that tells the spawned session to
#      subscribe to PR activity or open a draft PR for itself. That instruction
#      is what switches on the harness's self-re-arming, deliberately silent,
#      hourly check-in — "re-arm silently", exit condition "PR merged or closed",
#      which never fires because integration merges branches, not PRs. A lane
#      that never subscribes cannot wake itself up. This is the actual fix; the
#      concurrency cap is damage limitation.
#
# Overriding is deliberate and visible: CLAUDE_SPAWN_MAX_LIVE=n, or
# CLAUDE_SPAWN_ALLOW_BABYSIT=1. Neither is something to reach for quietly to get
# a call through.

set -uo pipefail
LEDGER="${CLAUDE_PROJECT_DIR:-.}/.claude/.spawned-sessions"
MAX_LIVE="${CLAUDE_SPAWN_MAX_LIVE:-2}"
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

if [ "${CLAUDE_SPAWN_ALLOW_BABYSIT:-0}" != "1" ] &&
   printf '%s' "$payload" | grep -qiE 'subscribe_pr_activity|draft PR'; then
  {
    echo "BLOCKED: this brief tells the spawned session to watch a PR."
    echo
    echo "That switches on the harness's PR-babysitting loop: an hourly self"
    echo "check-in that re-arms itself, is instructed to stay SILENT, and exits"
    echo "only when the PR is merged or closed. Integration merges BRANCHES and"
    echo "leaves PRs open, so it never exits. On 2026-09-24 five such sessions"
    echo "woke 9-15 times each overnight on 480-717K-token contexts."
    echo
    echo "Remove the subscribe/draft-PR instruction. A lane reports by pushing"
    echo "its branch; the integrator reads the branch. Nothing needs to watch."
  } >&2
  exit 2
fi
exit 0
