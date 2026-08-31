#!/usr/bin/env bash
#
# Label one pull request by its diff: additions + deletions
# pick one of small, medium, large, enormous,
# and on only removals code also gets `less` alongside that size label.
set -euo pipefail

REPO="${REPO:?}"
PR="${PR:?}"

SMALL_LABEL="${SMALL_LABEL:-small}"
MEDIUM_LABEL="${MEDIUM_LABEL:-medium}"
LARGE_LABEL="${LARGE_LABEL:-large}"
ENORMOUS_LABEL="${ENORMOUS_LABEL:-enormous}"
LESS_LABEL="${LESS_LABEL:-less}"

SMALL_MAX="${SMALL_MAX:-60}"
MEDIUM_MAX="${MEDIUM_MAX:-100}"
LARGE_MAX="${LARGE_MAX:-200}"

GRADLE_FILE="${GRADLE_FILE:-app/build.gradle}"
EXCLUDE_PATHS="${EXCLUDE_PATHS:-app/src/main/res/values-*/strings.xml}"
DRY_RUN="${DRY_RUN:-false}"

FILES_JSON="${FILES_JSON:-}"
CURRENT_LABELS="${CURRENT_LABELS-}"

EDIT_RETRY_DELAYS='3 9'

log()     { printf '%s | %s\n' "$(date -u +%H:%M:%S)" "$*"; }
summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"; return 0; }

excluded() {
    local path="$1" pattern
    for pattern in $EXCLUDE_PATHS; do
        [[ "$path" == $pattern ]] && return 0
    done
    return 1
}

# Filename + additions + deletions for every changed file, WITHOUT the patch.
# The REST pulls/files endpoint ships the full unified patch for each file (and
# drops `patch` once a diff is big enough), so a listing call that asks for it
# costs megabytes on a large PR for data we never read -- and is unreliable
# exactly where it hurts. The GraphQL `pullRequest.files` connection returns only
# path/additions/deletions (no patch), so it is both smaller and dependable; we
# page through it with `--paginate`. `--field` (-F) is safe to repeat on every
# page request, and `gh api graphql --paginate` threads `--cursor` per page.
read_files() {
    if [ -n "$FILES_JSON" ]; then
        jq -r '.[] | [(.filename // .path), (.additions // 0), (.deletions // 0)] | @tsv' "$FILES_JSON"
    else
        gh api graphql --paginate \
            -F owner="${REPO%%/*}" -F name="${REPO#*/}" -F pr="$PR" \
            -f query='query($endCursor: String, $owner: String!, $name: String!, $pr: Int!) {
                repository(owner: $owner, name: $name) {
                    pullRequest(number: $pr) {
                        files(first: 100, after: $endCursor) {
                            pageInfo { hasNextPage endCursor }
                            nodes { path additions deletions }
                        }
                    }
                }
            }' \
            --jq '.data.repository.pullRequest.files.nodes[] | [.path, (.additions // 0), (.deletions // 0)] | @tsv'
    fi
}

# The version-file diff only -- never the per-file patch from the listing.
# We fetch just the two blobs (head and base) of the version file and diff them,
# instead of pulling (and discarding) every file's patch. GraphQL `object(...:path)`
# returns a Blob with `.text` (it is `null` when the file is absent or the path is
# not at that commit, e.g. a force-pushed-away base), which we treat as empty.
# Falls back to no diff (and thus no discount) if the head blob can't be read.
gradle_blob() {
    local expr="$1" out
    out=$(gh api graphql \
        -F owner="${REPO%%/*}" -F name="${REPO#*/}" -F expr="$expr" \
        -f query='query($owner: String!, $name: String!, $expr: String!) {
            repository(owner: $owner, name: $name) {
                object(expression: $expr) { ... on Blob { text } }
            }
        }' \
        --jq '.data.repository.object.text // ""' 2>/dev/null) || out=''
    printf '%s' "$out"
}

gradle_patch() {
    local head_sha="${GRADLE_HEAD_SHA:-}" base_sha="${GRADLE_BASE_SHA:-}"
    [ -n "$head_sha" ] && [ -n "$base_sha" ] || return 0
    diff -U0 <(gradle_blob "${base_sha}:${GRADLE_FILE}") <(gradle_blob "${head_sha}:${GRADLE_FILE}") 2>/dev/null || true
}

version_lines() {
    printf '%s\n' "$1" | grep -cE "^\\$2[[:space:]]*version(Code|Name)[[:space:]]*=" || true
}

main() {
    # Resolve the head/base commit OIDs for the version-file diff once.
    if [ -z "$FILES_JSON" ] && [ -z "${GRADLE_HEAD_SHA:-}" ]; then
        eval "$(gh api "repos/$REPO/pulls/$PR" --jq \
            '@sh "GRADLE_HEAD_SHA=\(.head.sha) GRADLE_BASE_SHA=\(.base.sha)"')"
        export GRADLE_HEAD_SHA GRADLE_BASE_SHA
    fi

    local changed_files
    changed_files=$(read_files)

    local adds=0 dels=0 counted=0 path file_adds file_dels
    while IFS=$'\t' read -r path file_adds file_dels; do
        [ -n "$path" ] || continue
        if excluded "$path"; then
            log "  skipping $path"
            continue
        fi
        if [ "$path" = "$GRADLE_FILE" ]; then
            local patch bump_adds bump_dels
            patch=$(gradle_patch)
            bump_adds=$(version_lines "$patch" '+')
            bump_dels=$(version_lines "$patch" '-')
            if [ $((bump_adds + bump_dels)) -gt 0 ]; then
                file_adds=$((file_adds - bump_adds))
                file_dels=$((file_dels - bump_dels))
                [ "$file_adds" -lt 0 ] && file_adds=0
                [ "$file_dels" -lt 0 ] && file_dels=0
                log "  discounting the version bump in $path (-$bump_adds/-$bump_dels)"
            elif [ -z "$patch" ] && [ $((file_adds + file_dels)) -gt 0 ]; then
                # The listing says the version file changed but we could not
                # reconstruct its diff (blob read returned null/empty -- a
                # missing contents:read scope, or a force-pushed-away base).
                # Surface it so the discount isn't silently dropped. A readable
                # diff with no version lines is the normal no-discount case.
                log "  WARNING: could not read $path diff -- not discounting the version bump (check contents:read permission)"
            fi
        fi
        adds=$((adds + file_adds))
        dels=$((dels + file_dels))
        counted=$((counted + 1))
    done <<< "$changed_files"

    local total=$((adds + dels))

    if [ "$counted" -eq 0 ] || [ "$total" -eq 0 ]; then
        log "#$PR changes nothing this counts -- leaving its labels alone"
        exit 0
    fi

    local want_size want_less wanted
    if   [ "$total" -le "$SMALL_MAX" ];  then want_size="$SMALL_LABEL"
    elif [ "$total" -le "$MEDIUM_MAX" ]; then want_size="$MEDIUM_LABEL"
    elif [ "$total" -le "$LARGE_MAX" ];  then want_size="$LARGE_LABEL"
    else                                      want_size="$ENORMOUS_LABEL"
    fi

    want_less=false
    [ "$adds" -eq 0 ] && want_less=true

    wanted="$want_size"
    $want_less && wanted="$want_size + $LESS_LABEL"
    log "#$PR is +$adds/-$dels over $counted file(s) -- $total line(s), $wanted"

    if [ -z "${CURRENT_LABELS+set}" ]; then
        CURRENT_LABELS=$(gh pr view "$PR" --repo "$REPO" --json labels --jq '.labels[].name')
    fi

    local add='' remove label
    holds() { printf '%s\n' "$CURRENT_LABELS" | grep -qxF "$1"; }
    for label in "$SMALL_LABEL" "$MEDIUM_LABEL" "$LARGE_LABEL" "$ENORMOUS_LABEL"; do
        if [ "$label" = "$want_size" ]; then
            if ! holds "$label"; then add="${add:+$add,}$label"; fi
        else
            if holds "$label"; then remove="${remove:+$remove,}$label"; fi
        fi
    done
    if $want_less; then
        if ! holds "$LESS_LABEL"; then add="${add:+$add,}$LESS_LABEL"; fi
    else
        if holds "$LESS_LABEL"; then remove="${remove:+$remove,}$LESS_LABEL"; fi
    fi

    if [ -z "$add" ] && [ -z "$remove" ]; then
        log "  already labelled $wanted -- nothing to write"
        exit 0
    fi

    log "  add: ${add:-none}   remove: ${remove:-none}"

    if [ "$DRY_RUN" = "true" ]; then
        log "  dry run -- not editing #$PR"
        exit 0
    fi

    local edit_args=()
    [ -n "$add" ]    && edit_args+=(--add-label "$add")
    [ -n "$remove" ] && edit_args+=(--remove-label "$remove")

    local delay
    for delay in $EDIT_RETRY_DELAYS ''; do
        if gh pr edit "$PR" --repo "$REPO" "${edit_args[@]}" >/dev/null 2>&1; then
            summary "\`#$PR\` +$adds/-$dels over $counted file(s) -- $total line(s) -> **$wanted**"
            log "  labelled #$PR $wanted"
            exit 0
        fi
        [ -n "$delay" ] || break
        log "  edit failed, retrying in ${delay}s"
        sleep "$delay"
    done

    log "  could not label #$PR"
    exit 1
}

main
