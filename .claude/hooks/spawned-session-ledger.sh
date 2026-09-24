#!/usr/bin/env bash
# Tracks Claude Code Remote sessions this session spawns, and refuses to let a
# turn end while a stale one is still alive.
#
# WHY THIS EXISTS — 2026-09-24, and it cost a non-profit 16% of a weekly
# allowance overnight.
#
# Five lane sessions were spawned for Phase 160. Each was briefed to open a
# draft PR and call `subscribe_pr_activity`, which switches on the harness's
# PR-babysitting loop: *"schedule a self check-in roughly an hour out ... then
# re-arm the next check-in. If nothing changed, do not message the user — re-arm
# silently."*
#
# Three properties of that loop combined badly:
#   1. it re-arms itself, hourly, for ever;
#   2. it is instructed to be SILENT, so nothing surfaces to the user;
#   3. its exit condition is "the PR is merged or closed" — but the integration
#      pattern merges *branches* locally and pushes, so the PRs stayed open and
#      the exit condition could never fire.
#
# Each wake reloaded a 480-717K-token context on Opus. Lane 4 woke 15 times,
# Lane 5 thirteen, Lane 1 nine — overnight, invisibly, on work that had already
# been merged hours earlier.
#
# The model promised to clean up next time. This file exists because a promise
# from the model is not a mechanism. The harness runs hooks; the model does not
# get a vote.
#
# WHAT IT DOES
#   PostToolUse  create_session   -> append the new session id to the ledger
#   PostToolUse  archive_session  -> remove that id from the ledger
#   Stop                          -> block the turn ending if a lane's BRANCH is
#                                    already merged into HEAD while the lane is
#                                    still on the ledger (harvested but not
#                                    closed), or if an entry is simply ancient
#
# THE RULE IS THE USER'S, and it is narrower than the one the model first wrote:
# **never leave a PR open once it has been harvested.** Lanes are fine. Rounds of
# 2-5 lanes are fine. Draft PRs are fine — a lane's PR is its report and the only
# inbound channel to it. What is not fine is merging a lane's branch and walking
# away, because the babysit loop's only exit is the PR being merged or closed,
# and merging a BRANCH closes nothing.
#
# So the block fires on exactly that state: this lane's branch is contained in
# HEAD, and the lane is still on the ledger. That is harvest time, and harvest
# time happens inside an active turn, which is when a Stop hook can actually
# fire. (The first version of this file keyed on elapsed hours alone, which
# could not have caught the incident that prompted it: the damage happened
# overnight while this session was idle and no Stop hook ran at all.)
#
# A fresh, unmerged entry does NOT block: a lane legitimately runs for a while
# and the turn has to end so the user can get on with their day.
#
# TO OVERRIDE, deliberately: delete the ledger, or raise CLAUDE_SPAWN_STALE_SECONDS.
# Both are visible acts. Silently editing the ledger to end a turn is the exact
# behaviour this guard exists to prevent.

set -uo pipefail

LEDGER="${CLAUDE_PROJECT_DIR:-.}/.claude/.spawned-sessions"
STALE_SECONDS="${CLAUDE_SPAWN_STALE_SECONDS:-21600}" # 6h

payload="$(cat 2>/dev/null || true)"
event="$(printf '%s' "$payload" | python3 -c '
import json,sys
try: print(json.load(sys.stdin).get("hook_event_name",""))
except Exception: print("")
' 2>/dev/null)"

# Fall back to the argument form if the payload carried no event name.
[ -z "$event" ] && event="${1:-}"

record() {
  # The created session id is the first session_… in the tool response.
  local id branch
  id="$(printf '%s' "$payload" | grep -oE 'session_[A-Za-z0-9]{6,}' | head -1)"
  [ -z "$id" ] && exit 0
  # The lane's outcome branch, so Stop can ask whether it has been harvested.
  branch="$(printf '%s' "$payload" \
            | grep -oE '"(outcome_branch|branches)"[^"]*"[^"]+"' \
            | sed -E 's/.*"([^"]+)"$/\1/' | head -1)"
  mkdir -p "$(dirname "$LEDGER")"
  grep -q "^$id " "$LEDGER" 2>/dev/null && exit 0
  printf '%s %s %s\n' "$id" "$(date +%s)" "${branch:--}" >> "$LEDGER"
  exit 0
}

forget() {
  local id
  id="$(printf '%s' "$payload" | grep -oE 'session_[A-Za-z0-9]{6,}' | head -1)"
  [ -z "$id" ] && exit 0
  [ -f "$LEDGER" ] || exit 0
  grep -v "^$id " "$LEDGER" > "$LEDGER.tmp" 2>/dev/null || true
  mv "$LEDGER.tmp" "$LEDGER" 2>/dev/null || true
  # An emptied ledger is removed so the Stop hook has nothing to read.
  [ -s "$LEDGER" ] || rm -f "$LEDGER"
  exit 0
}

check() {
  [ -s "$LEDGER" ] || exit 0
  local now stale harvested fresh id ts br age
  now="$(date +%s)"; stale=""; harvested=""; fresh=0
  while read -r id ts br; do
    [ -z "${id:-}" ] && continue
    age=$(( now - ${ts:-0} ))
    # Harvested: the lane's branch is already contained in HEAD.
    if [ -n "${br:-}" ] && [ "$br" != "-" ] &&
       git merge-base --is-ancestor "origin/$br" HEAD 2>/dev/null; then
      harvested="$harvested  $id  ($br — merged into HEAD)"$'\n'
    elif [ "$age" -ge "$STALE_SECONDS" ]; then
      stale="$stale  $id  (spawned $(( age / 3600 ))h ago)"$'\n'
    else
      fresh=$(( fresh + 1 ))
    fi
  done < "$LEDGER"

  if [ -n "$harvested" ] || [ -n "$stale" ]; then
    {
      [ -n "$harvested" ] && echo "BLOCKED: harvested lanes still open." &&
        printf '%s' "$harvested"
      [ -n "$stale" ] && echo "BLOCKED: spawned sessions running $(( STALE_SECONDS / 3600 ))h+." &&
        printf '%s' "$stale"
      echo
      echo "Each of these wakes itself hourly and reloads its whole context."
      echo "If its work is merged, archive it AND close its PR, now, in this turn:"
      echo "  mcp__Claude_Code_Remote__archive_session  session_id=<id>"
      echo "  mcp__github__update_pull_request          state=closed"
      echo
      echo "Closing the PR matters as much as archiving: the babysit loop's only"
      echo "exit condition is the PR being merged or closed, and merging a branch"
      echo "locally does not close it."
      echo
      echo "If a lane is genuinely still working, say so to the user and let them"
      echo "decide. Do not delete this ledger to get past this message."
    } >&2
    exit 2
  fi

  [ "$fresh" -gt 0 ] && echo "note: $fresh spawned session(s) still live — archive and close their PRs when their work lands."
  exit 0
}

case "$event" in
  PostToolUse)
    if printf '%s' "$payload" | grep -q 'archive_session'; then forget; else record; fi
    ;;
  Stop|SubagentStop) check ;;
  *) exit 0 ;;
esac
exit 0
