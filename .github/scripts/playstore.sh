#!/usr/bin/env bash
#
#   playstore.sh track-code   # highest versionCode on $PLAYSTORE_TRACK
#   playstore.sh pending      # key=value: what (if anything) to publish
#
# `pending` asks the track and compares it with the newest release: the track is
# the authority, and an edit opened and deleted unsaved spends no save quota.
set -euo pipefail

REPO="${REPO:?}"
BASE="${BASE:-master}"
PACKAGE="${PLAYSTORE_PACKAGE_NAME:-org.ole.planet.myplanet}"
TRACK="${PLAYSTORE_TRACK:-internal}"
AAB_ASSET="${PLAYSTORE_AAB_ASSET:-myPlanet-lite.aab}"
VERSION_SUFFIX="${PLAYSTORE_VERSION_SUFFIX:--lite}"
RELEASE_WORKFLOW="${PLAYSTORE_RELEASE_WORKFLOW:-release.yml}"
PUBLISH_JOB="${PLAYSTORE_PUBLISH_JOB:-myPlanet release (lite)}"
PUBLISH_WARN_STEP="${PLAYSTORE_PUBLISH_WARN_STEP:-warn that the playstore upload failed}"

API="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PACKAGE"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

die()  { echo "playstore.sh: $*" >&2; exit 1; }
note() { echo "playstore.sh: $*" >&2; }

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

access_token() {
    local sa="${SERVICE_ACCOUNT_JSON:-}" email now header claims sig token
    [ -n "$sa" ] || { note "SERVICE_ACCOUNT_JSON is not set"; return 1; }

    email=$(jq -r '.client_email // empty' <<<"$sa") || return 1
    [ -n "$email" ] || { note "no client_email in the service account json"; return 1; }
    (umask 077; jq -r '.private_key // empty' <<<"$sa" > "$TMP/key.pem")
    [ -s "$TMP/key.pem" ] || { note "no private_key in the service account json"; return 1; }

    now=$(date -u +%s)
    header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
    claims=$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","iat":%s,"exp":%s}' \
        "$email" "$now" "$((now + 600))" | b64url)
    sig=$(printf '%s.%s' "$header" "$claims" | openssl dgst -sha256 -sign "$TMP/key.pem" -binary | b64url)

    token=$(curl -sS -X POST 'https://oauth2.googleapis.com/token' \
        -d 'grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer' \
        --data-urlencode "assertion=$header.$claims.$sig" \
        | jq -r '.access_token // empty') || return 1
    [ -n "$token" ] || { note "the service account did not get an access token"; return 1; }
    printf '%s\n' "$token"
}

track_code() {
    local token edit body code
    token=$(access_token) || return 1

    edit=$(curl -sS -X POST -H "Authorization: Bearer $token" -H 'Content-Length: 0' "$API/edits" \
           | jq -r '.id // empty') || return 1
    [ -n "$edit" ] || { note "could not open an edit on $PACKAGE"; return 1; }

    body=$(curl -sS -H "Authorization: Bearer $token" "$API/edits/$edit/tracks/$TRACK" || echo '{}')
    curl -sS -o /dev/null -X DELETE -H "Authorization: Bearer $token" "$API/edits/$edit" || true

    if [ -n "$(jq -r '.error.message // empty' <<<"$body")" ]; then
        note "reading the $TRACK track failed: $(jq -r '.error.message' <<<"$body")"
        return 1
    fi
    code=$(jq -r '[.releases[]?.versionCodes[]? | tonumber] | max // 0' <<<"$body") || return 1
    printf '%s\n' "$code"
}

code_for() {
    local name=$1
    [[ "$name" =~ ^([0-9]{1,3})\.([0-9]{1,2})\.([0-9]{1,2})$ ]] || return 1
    printf '%s\n' "$(( 10#${BASH_REMATCH[1]} * 10000 + 10#${BASH_REMATCH[2]} * 100 + 10#${BASH_REMATCH[3]} ))"
}

# 0 = the newest release run on $BASE warned (or cannot say), 1 = it published.
# Newest release run on $BASE, on stdout: running|published|warned|unknown.
# Only running and warned decide anything; the track decides the rest.
release_run_state() {
    local run job_id concl
    # a page and max_by, because per_page=1 is not reliably the newest run
    run=$(gh api "repos/$REPO/actions/workflows/$RELEASE_WORKFLOW/runs?branch=$BASE&event=push&per_page=20" \
            --jq '[.workflow_runs[]?] | max_by(.id) | select(. != null) | "\(.id)\t\(.status)"' 2>/dev/null) \
        || { printf 'unknown\n'; return 0; }
    [ -n "$run" ] && [ "$run" != "null" ] \
        || { note "no $RELEASE_WORKFLOW run found on $BASE"; printf 'unknown\n'; return 0; }

    local id=${run%%$'\t'*} status=${run##*$'\t'}
    if [ "$status" != 'completed' ]; then
        note "release run $id is still $status -- letting it finish"
        printf 'running\n'; return 0
    fi

    job_id=$(gh api "repos/$REPO/actions/runs/$id/jobs?per_page=100" \
               --jq ".jobs[] | select(.name == \"$PUBLISH_JOB\") | .id" 2>/dev/null | head -n 1) \
        || { printf 'unknown\n'; return 0; }
    [ -n "$job_id" ] || { note "release run $id has no '$PUBLISH_JOB' job"; printf 'unknown\n'; return 0; }

    concl=$(gh api "repos/$REPO/actions/jobs/$job_id" \
              --jq ".steps[]? | select(.name == \"$PUBLISH_WARN_STEP\") | .conclusion" 2>/dev/null | head -n 1) \
        || { printf 'unknown\n'; return 0; }
    case "$concl" in
        skipped) note "release run $id did not warn about its upload"; printf 'published\n' ;;
        '')      note "release run $id has no '$PUBLISH_WARN_STEP' step"; printf 'unknown\n' ;;
        *)       note "release run $id warned that the playstore upload failed"; printf 'warned\n' ;;
    esac
}

emit() {
    local kv key value
    for kv in "$@"; do
        key=${kv%%=*}; value=${kv#*=}
        printf '%s=%s\n' "$key" "$value"
        [ -n "${GITHUB_OUTPUT:-}" ] && printf '%s=%s\n' "$key" "$value" >> "$GITHUB_OUTPUT"
    done
    return 0
}

pending() {
    local release tag name code asset track state

    # Racing a release run mid-flight can spend two save slots on one build.
    state=$(release_run_state)
    if [ "$state" = 'running' ]; then
        emit 'pending=false'
        return 0
    fi

    release=$(gh api "repos/$REPO/releases/latest" 2>/dev/null) || { note "could not read the latest release"; emit 'pending=false'; return 0; }
    tag=$(jq -r '.tag_name // empty' <<<"$release")
    [ -n "$tag" ] || { note "the latest release has no tag"; emit 'pending=false'; return 0; }

    name="${tag#v}"
    code=$(code_for "$name") || { note "tag $tag is not v<major>.<minor>.<patch>"; emit 'pending=false'; return 0; }

    asset=$(jq -r --arg a "$AAB_ASSET" '[.assets[]? | select(.name == $a)] | length' <<<"$release")
    if [ "$asset" != '1' ]; then
        note "release $tag carries no $AAB_ASSET yet"
        emit 'pending=false'
        return 0
    fi

    if track=$(track_code); then
        note "$TRACK track is at ${track}, release $tag is $code"
        if [ "$track" -ge "$code" ]; then
            emit 'pending=false' "track_code=$track"
            return 0
        fi
    else
        if [ "$state" != 'warned' ]; then
            note "the $TRACK track is unreadable and the release run $state -- nothing to do"
            emit 'pending=false'
            return 0
        fi
        track=''
        note "could not read the $TRACK track, but the release run warned -- attempting the upload blind"
    fi

    emit 'pending=true' "tag=$tag" "version_name=${name}${VERSION_SUFFIX}" "version_code=$code" \
         "release_name=$code (${name}${VERSION_SUFFIX})" "asset=$AAB_ASSET" "track_code=${track:-unknown}"
}

case "${1:-}" in
    track-code) track_code ;;
    pending)    pending ;;
    *)          die "usage: playstore.sh {track-code|pending}" ;;
esac
