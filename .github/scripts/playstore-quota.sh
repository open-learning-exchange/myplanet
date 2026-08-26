#!/usr/bin/env bash
#
#   playstore-quota.sh {status|report}   # key=value / one human line
#
# Estimates the playstore save quota from the release history: one master
# release is one save. The quota is a pool of LIMIT slots each freeing 24h
# after its own use, not a midnight reset -- 6514 was refused at 02:57 Pacific
# on 2026-08-18 after only 10 saves that Pacific day. So the next slot is the
# oldest one still in use plus 24h. Advisory, and nothing gates on it: hand
# uploads and re-runs spend slots that leave no release behind, refusals leave
# releases that hold no slot. Eastern times.
#
# LIMIT is 50: 6714 was refused on 2026-08-26 as the 51st release in its 24h
# window (run 32930850241), the 50 before it -- 6664 (Aug 25 07:58Z) through
# 6713 -- all published, each one confirmed by the drain before it picked the
# next PR. The earlier guess of 48 read that window back as "51 of 48 slots
# used" and, taking the absolute oldest release as the oldest slot still held,
# dated the next free slot two saves early.
set -euo pipefail

REPO="${REPO:?}"
LIMIT="${PLAYSTORE_DAILY_LIMIT:-50}"
QUOTA_TZ="${PLAYSTORE_QUOTA_TZ:-America/New_York}"
RELEASES_JSON="${RELEASES_JSON:-}"
NOW_EPOCH="${NOW_EPOCH:-$(date -u +%s)}"

WINDOW_SEC=$((24 * 60 * 60))

die() { echo "playstore-quota.sh: $*" >&2; exit 1; }

case "$LIMIT" in *[!0-9]*|"") die "PLAYSTORE_DAILY_LIMIT must be a number, got '$LIMIT'" ;; esac
[ "$LIMIT" -gt 0 ] || die "PLAYSTORE_DAILY_LIMIT must be greater than 0"

# Save epochs, newest first. RELEASES_JSON points tests at a captured fixture.
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
    local used=0 e
    local -a live=()
    while read -r e; do
        [ -n "$e" ] || continue
        [ "$e" -gt "$window_start" ] || continue
        used=$((used + 1))
        live+=("$e")
    done < <(save_epochs)

    local free=$((LIMIT - used))
    [ "$free" -ge 0 ] || free=0

    local next report soon=0 oldest
    if [ "$used" -lt "$LIMIT" ]; then
        next="$NOW_EPOCH"
        report="playstore save quota: $used of $LIMIT slots used -- $free free now"
    else
        # live is newest first, so the LIMIT-th newest is the oldest slot still
        # held. Anything older than that one is a release holding no slot -- a
        # refusal, or a save that has already aged out -- and waiting on it
        # would report a slot that is not coming.
        oldest=${live[$((LIMIT - 1))]}
        next=$((oldest + WINDOW_SEC))
        # slots ageing out within the hour after it: one save, or a handful?
        for e in "${live[@]}"; do
            [ "$e" -gt "$oldest" ] && [ "$e" -le $((oldest + 3600)) ] && soon=$((soon + 1))
        done
        if [ "$used" -gt "$LIMIT" ]; then
            report="playstore save quota: all $LIMIT slots used ($used releases in the window, $((used - LIMIT)) of them holding no slot)"
        else
            report="playstore save quota: $used of $LIMIT slots used"
        fi
        report="$report -- next slot around $(fmt "$next"), 24h after the oldest one in use ($(fmt "$oldest"))"
        [ "$soon" -gt 0 ] && report="$report, then $soon more within the hour"
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
