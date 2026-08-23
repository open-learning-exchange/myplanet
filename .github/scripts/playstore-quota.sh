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
# The quota behaves as a pool of LIMIT slots where each slot frees 24h after
# its own use -- not as a counter reset at midnight anywhere. 6514 was refused
# at 02:57 Pacific on 2026-08-18 with only 10 saves made that Pacific day, and
# 49 in the preceding 24h; no calendar-day reset can produce that. So the next
# slot opens 24h after the LIMIT-th newest save, and saves keep freeing at the
# cadence they were spent.
#
# It stays an estimate, and nothing gates on it -- the playstore workflow
# retries every 30 minutes whatever this says. Releases are only a proxy for
# saves: a hand upload from the Play Console, a re-run, or that retry spends a
# slot without adding a release (answer too early), while a refused upload adds
# a release without spending one (answer too late). Give or take a couple of
# slots. Times print in eastern.
set -euo pipefail

REPO="${REPO:?}"
LIMIT="${PLAYSTORE_DAILY_LIMIT:-48}"
QUOTA_TZ="${PLAYSTORE_QUOTA_TZ:-America/New_York}"
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

status() {
    local window_start=$((NOW_EPOCH - WINDOW_SEC))
    local used=0 nth="" e
    local -a live=()
    while read -r e; do
        [ -n "$e" ] || continue
        [ "$e" -gt "$window_start" ] || continue
        used=$((used + 1))
        live+=("$e")
        [ "$used" -eq "$LIMIT" ] && nth="$e"
    done < <(save_epochs)

    local free=$((LIMIT - used))
    [ "$free" -ge 0 ] || free=0

    local next report soon=0
    if [ "$used" -lt "$LIMIT" ]; then
        next="$NOW_EPOCH"
        report="playstore save quota: $used of $LIMIT slots used -- $free free now"
    else
        # The LIMIT-th newest save is holding the slot the next save needs.
        next=$((nth + WINDOW_SEC))
        # Slots whose 24h is up within the hour after that, so a drain knows
        # whether it gets one save or a handful.
        for e in "${live[@]}"; do
            [ "$e" -lt "$nth" ] && [ $((e + WINDOW_SEC)) -le $((next + 3600)) ] && soon=$((soon + 1))
        done
        report="playstore save quota: $used of $LIMIT slots used -- next slot around $(fmt "$next"), 24h after the save at $(fmt "$nth")"
        [ "$soon" -gt 0 ] && report="$report (then $soon more within the hour)"
    fi

    echo "limit=$LIMIT"
    echo "used=$used"
    echo "free=$free"
    echo "free_now=$([ "$used" -lt "$LIMIT" ] && echo true || echo false)"
    echo "next_free_epoch=$next"
    echo "next_free_iso=$(iso "$next")"
    echo "next_free_local='$(fmt "$next")'"
    echo "frees_within_hour=$soon"
    echo "report='$report'"
}

case "${1:-status}" in
    status) status ;;
    report) status | sed -n "s/^report='\(.*\)'$/\1/p" ;;
    *)      die "usage: playstore-quota.sh {status|report}" ;;
esac
