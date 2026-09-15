#!/usr/bin/env bash
#
#   playstore-quota.sh {status|report|forecast}
#
# Estimates the playstore save quota from the release history: one master
# release is one save, LIMIT slots, each freeing 24h + LAG after its own use.
# Advisory -- hand uploads and refusals drift it.
set -euo pipefail

REPO="${REPO:?}"
LIMIT="${PLAYSTORE_DAILY_LIMIT:-50}"
LAG="${PLAYSTORE_SLOT_LAG:-300}"
COUNT="${PLAYSTORE_FORECAST_COUNT:-10}"
QUOTA_TZ="${PLAYSTORE_QUOTA_TZ:-America/New_York}"
RELEASES_JSON="${RELEASES_JSON:-}"
NOW_EPOCH="${NOW_EPOCH:-$(date -u +%s)}"

WINDOW_SEC=$((24 * 60 * 60))

die() { echo "playstore-quota.sh: $*" >&2; exit 1; }

num() {
    case "$2" in *[!0-9]*|"") die "$1 must be a number, got '$2'" ;; esac
}
num PLAYSTORE_DAILY_LIMIT "$LIMIT"
num PLAYSTORE_SLOT_LAG "$LAG"
num PLAYSTORE_FORECAST_COUNT "$COUNT"
[ "$LIMIT" -gt 0 ] || die "PLAYSTORE_DAILY_LIMIT must be greater than 0"
[ "$COUNT" -gt 0 ] || die "PLAYSTORE_FORECAST_COUNT must be greater than 0"

HOLD_SEC=$((WINDOW_SEC + LAG))

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

in_words() {
    local d=$(( $1 - NOW_EPOCH ))
    [ "$d" -le 0 ] && { printf 'now\n'; return; }
    [ "$d" -lt 3600 ] && { printf 'in %dm\n' $((d / 60)); return; }
    printf 'in %dh%02dm\n' $((d / 3600)) $(( (d % 3600) / 60 ))
}

# Sets: used held free slot_epochs[] (soonest first, one per held slot)
measure() {
    local hold_start=$((NOW_EPOCH - HOLD_SEC)) e
    used=0
    live=()
    while read -r e; do
        [ -n "$e" ] || continue
        [ "$e" -gt "$hold_start" ] || continue
        used=$((used + 1))
        live+=("$e")
    done < <(save_epochs)

    held=$used
    [ "$held" -le "$LIMIT" ] || held=$LIMIT
    free=$((LIMIT - used))
    [ "$free" -ge 0 ] || free=0

    # live is newest first, so live[held-1] is the oldest slot still held;
    # anything older than it holds no slot at all.
    slot_epochs=()
    local j
    for (( j = 1; j <= held; j++ )); do
        slot_epochs+=( $(( live[held - j] + HOLD_SEC )) )
    done
}

status() {
    local used held free
    local -a live=() slot_epochs=()
    measure

    local next report soon=0 e
    if [ "$free" -gt 0 ]; then
        next="$NOW_EPOCH"
        report="playstore save quota: $used of $LIMIT slots used -- $free free now"
    else
        next=${slot_epochs[0]}
        for e in "${slot_epochs[@]:1}"; do
            [ "$e" -le $((next + 3600)) ] && soon=$((soon + 1))
        done
        if [ "$used" -gt "$LIMIT" ]; then
            report="playstore save quota: all $LIMIT slots used ($used releases in the window, $((used - LIMIT)) of them holding no slot)"
        else
            report="playstore save quota: $used of $LIMIT slots used"
        fi
        report="$report -- next slot around $(fmt "$next") ($(in_words "$next")), 24h+${LAG}s after the oldest one in use"
        [ "$soon" -gt 0 ] && report="$report, then $soon more within the hour"
    fi

    echo "limit=$LIMIT"
    echo "used=$used"
    echo "held=$held"
    echo "free=$free"
    echo "next_free_epoch=$next"
    echo "next_free_local='$(fmt "$next")'"
    echo "frees_within_hour=$soon"
    echo "report='$report'"
}

forecast() {
    local used held free
    local -a live=() slot_epochs=()
    measure

    status | sed -n "s/^report='\(.*\)'$/\1/p"

    [ "$free" -gt 0 ] && echo "  $free slot(s) free right now"

    local n=0 e
    echo "  next $COUNT slots:"
    for e in "${slot_epochs[@]}"; do
        [ "$e" -gt "$NOW_EPOCH" ] || continue
        n=$((n + 1))
        printf '  %2d  %s  (%s)\n' "$n" "$(fmt "$e")" "$(in_words "$e")"
        [ "$n" -ge "$COUNT" ] && break
    done
    [ "$n" -eq 0 ] && echo "      (none held -- the pool is empty of saves)"
    return 0
}

case "${1:-status}" in
    status)   status ;;
    report)   status | sed -n "s/^report='\(.*\)'$/\1/p" ;;
    forecast) forecast ;;
    *)        die "usage: playstore-quota.sh {status|report|forecast}" ;;
esac
