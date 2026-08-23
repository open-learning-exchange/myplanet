#!/usr/bin/env bash
#
# Estimate the Google Play daily save quota for this app from the GitHub
# release history. Every release on master is one playstore save, so counting
# releases counts saves; Play answers the save past the limit with
# "Daily save quota exceeded." and never says when the next slot opens.
#
#   playstore-quota.sh status   # key=value, eval-friendly
#   playstore-quota.sh report   # just the one-line human summary
#
# The reset is guessed two ways and the earlier one wins:
#
#   reset   -- Google API daily quotas roll over at midnight Pacific
#   rolling -- the LIMIT-th newest save ages out of a rolling 24h window
#
# Both are estimates: a release whose upload failed still counts here, which
# only ever makes the answer late, never early. Times print in eastern.
set -euo pipefail

REPO="${REPO:?}"
LIMIT="${PLAYSTORE_DAILY_LIMIT:-48}"
QUOTA_TZ="${PLAYSTORE_QUOTA_TZ:-America/New_York}"
RESET_TZ="${PLAYSTORE_RESET_TZ:-America/Los_Angeles}"
RELEASES_JSON="${RELEASES_JSON:-}"
NOW_EPOCH="${NOW_EPOCH:-$(date -u +%s)}"

WINDOW_SEC=$((24 * 60 * 60))

die() { echo "playstore-quota.sh: $*" >&2; exit 1; }

case "$LIMIT" in *[!0-9]*|"") die "PLAYSTORE_DAILY_LIMIT must be a number, got '$LIMIT'" ;; esac
[ "$LIMIT" -gt 0 ] || die "PLAYSTORE_DAILY_LIMIT must be greater than 0"

# Save epochs, newest first. gh api output and a captured fixture are the same
# shape, so tests can point RELEASES_JSON at a file instead of the network.
save_epochs() {
    local raw
    if [ -n "$RELEASES_JSON" ]; then
        raw=$(cat "$RELEASES_JSON")
    else
        raw=$(gh api "repos/$REPO/releases?per_page=100" 2>/dev/null) || raw='[]'
    fi
    jq -r '
        [ .[]? | select((.draft // false) | not) | .published_at | fromdateiso8601 ]
        | sort | reverse | .[]
    ' <<<"$raw" 2>/dev/null || true
}

fmt() { TZ="$QUOTA_TZ" date -d "@$1" '+%a %b %-d %H:%M %Z'; }
iso() { date -u -d "@$1" '+%Y-%m-%dT%H:%M:%SZ'; }

# Midnight in the quota's own timezone, one day on from now. Named by the day
# 24h out rather than with a "+1 day" modifier, which date reads as an offset.
reset_epoch() {
    local tomorrow
    tomorrow=$(TZ="$RESET_TZ" date -d "@$((NOW_EPOCH + WINDOW_SEC))" +%Y-%m-%d)
    TZ="$RESET_TZ" date -d "$tomorrow 00:00" +%s
}

status() {
    local window_start=$((NOW_EPOCH - WINDOW_SEC))
    local used=0 nth="" e
    while read -r e; do
        [ -n "$e" ] || continue
        [ "$e" -gt "$window_start" ] || continue
        used=$((used + 1))
        [ "$used" -eq "$LIMIT" ] && nth="$e"
    done < <(save_epochs)

    local free=$((LIMIT - used))
    [ "$free" -ge 0 ] || free=0
    local reset rolling next report
    reset=$(reset_epoch)

    if [ "$used" -lt "$LIMIT" ]; then
        next="$NOW_EPOCH"
        rolling="$NOW_EPOCH"
        report="playstore save quota: $used of $LIMIT saves used in the last 24h -- $free slot(s) free"
    else
        # The LIMIT-th newest save leaves the window LIMIT-th first.
        rolling=$((nth + WINDOW_SEC))
        next="$reset"
        [ "$rolling" -lt "$next" ] && next="$rolling"
        report="playstore save quota: $used of $LIMIT saves used in the last 24h -- next slot around $(fmt "$next") (midnight PT reset $(fmt "$reset"), rolling 24h window $(fmt "$rolling"))"
    fi

    echo "limit=$LIMIT"
    echo "used=$used"
    echo "free=$free"
    echo "free_now=$([ "$used" -lt "$LIMIT" ] && echo true || echo false)"
    echo "next_free_epoch=$next"
    echo "next_free_iso=$(iso "$next")"
    echo "next_free_local='$(fmt "$next")'"
    echo "reset_local='$(fmt "$reset")'"
    echo "rolling_local='$(fmt "$rolling")'"
    echo "report='$report'"
}

case "${1:-status}" in
    status) status ;;
    report) status | sed -n "s/^report='\(.*\)'$/\1/p" ;;
    *)      die "usage: playstore-quota.sh {status|report}" ;;
esac
