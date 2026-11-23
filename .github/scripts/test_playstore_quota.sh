#!/usr/bin/env bash
#
#   test_playstore_quota.sh
#
# Fixture check for playstore-quota.sh under its `set -euo pipefail`: the
# quota-full case with nothing else freeing within the hour used to exit 1
# (a failed trailing `[ ... ] && ...` in build_report) and print nothing.
set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

now=1800000000
{
    printf '['
    for i in 1 2 3; do
        t=$(date -u -d "@$(( now - i * 7200 ))" +%Y-%m-%dT%H:%M:%SZ)
        [ "$i" -eq 1 ] || printf ','
        printf '{"draft":false,"published_at":"%s"}' "$t"
    done
    printf ']'
} > "$tmp/releases.json"

fail=0
for cmd in status report forecast; do
    out=$(REPO=x PLAYSTORE_DAILY_LIMIT=3 NOW_EPOCH=$now RELEASES_JSON="$tmp/releases.json" \
        "$here/playstore-quota.sh" "$cmd") || { echo "FAIL: $cmd exited non-zero"; fail=1; continue; }
    case "$out" in
        *"3 of 3 slots used"*) echo "ok: $cmd" ;;
        *) echo "FAIL: $cmd printed no full-quota report: $out"; fail=1 ;;
    esac
done
exit "$fail"
