#!/usr/bin/env bash
#
#   playstore-quota.sh {status|report|forecast}   # key=value / one line / next slots
#
# Estimates the playstore save quota from the release history: one master
# release is one save. The quota is a pool of LIMIT slots each freeing 24h
# after its own use, not a midnight reset -- 6514 was refused at 02:57 Pacific
# on 2026-08-18 after only 10 saves that Pacific day. Advisory, and nothing
# gates on it: hand uploads and re-runs spend slots that leave no release
# behind, refusals leave releases that hold no slot, and a release published
# by hand later is dated from its release stamp rather than from the save that
# actually spent the slot -- 6714 was stamped 05:30Z on 2026-08-26 and saved at
# 08:12Z, so its slot reads as freeing 2h42m earlier than it will. Eastern
# times.
#
# LIMIT is 50: 6714 was refused on 2026-08-26 as the 51st release in its 24h
# window (run 32930850241), and the 50 before it -- 6664 (Aug 25 07:58Z)
# through 6713 -- all published.
#
# LAG exists because a slot does not come back at a crisp 24h. On 2026-08-26
# 6714 was polled to destruction to find the real edge: refused at 08:07:41Z
# and again at 08:11:16Z, accepted at 08:12:17Z (runs 32946126664,
# 32946419090, 32946497961). The binding save was 6665, release-stamped
# 08:07:27Z the day before, so the slot came back 290s after release + 24h --
# where the old crisp-24h model had said 08:07:27Z and was ~5m optimistic.
# LAG is 300s: that measurement rounded up, so the estimate errs late rather
# than walking an upload into a refusal. It applies to the window as well as
# the forecast, so a save counts as held for WINDOW + LAG and the two halves
# cannot disagree. Re-measure it the same way if the pool behaviour shifts.
#
# Slot j frees when the j-th oldest held save ages out, so the whole upcoming
# schedule is known, not just the next one -- `forecast` prints it.
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

HOLD_SEC=$((WINDOW_SEC + LAG))

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

# "now" / "in 7m" / "in 2h13m"
in_words() {
    local d=$(( $1 - NOW_EPOCH ))
    [ "$d" -le 0 ] && { printf 'now\n'; return; }
    [ "$d" -lt 3600 ] && { printf 'in %dm\n' $((d / 60)); return; }
    printf 'in %dh%02dm\n' $((d / 3600)) $(( (d % 3600) / 60 ))
}

# Fills: used held free slot_epochs[] (soonest first, one per held slot)
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

    # live is newest first, so live[held-1] is the oldest slot still held and
    # the first to come back; anything older holds no slot at all.
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

    # the upcoming schedule, soonest first, only what is still ahead of us
    local -a up=()
    for e in "${slot_epochs[@]}"; do
        [ "$e" -gt "$NOW_EPOCH" ] || continue
        up+=("$e")
        [ "${#up[@]}" -ge "$COUNT" ] && break
    done

    echo "limit=$LIMIT"
    echo "lag_seconds=$LAG"
    echo "used=$used"
    echo "held=$held"
    echo "free=$free"
    echo "free_now=$([ "$free" -gt 0 ] && echo true || echo false)"
    echo "next_free_epoch=$next"
    echo "next_free_iso=$(iso "$next")"
    echo "next_free_local='$(fmt "$next")'"
    echo "frees_within_hour=$soon"
    echo "upcoming_count=${#up[@]}"
    echo "upcoming_epochs='${up[*]-}'"
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
