#!/usr/bin/env bash
#
# Drain the automerge queue.
#
# Repeatedly: pick the lowest-numbered open non-draft PR carrying $LABEL,
# verify it is mergeable and green, bump the version, squash merge it, then
# wait for the workflows the merge kicks off on $BASE. Stop on the first
# failure; otherwise keep going until no labelled PR is left.
#
# Everything is driven by environment variables so the workflow stays thin.
# This script is expected to run from a copy outside the work tree (see
# automerge.yml) -- it checks out PR branches, and a running bash script
# whose file is swapped underneath it is a bad time.
#
set -euo pipefail

REPO="${REPO:?}"
BASE="${BASE:?}"
LABEL="${LABEL:?}"
GRADLE_FILE="${GRADLE_FILE:?}"
VERSION_SH="${VERSION_SH:?}"
COAUTHORS_SH="${COAUTHORS_SH:?}"
REQUIRE_CHECKS="${REQUIRE_CHECKS:-true}"
DELETE_BRANCH="${DELETE_BRANCH:-true}"
DRY_RUN="${DRY_RUN:-true}"
MAX_MERGES="${MAX_MERGES:-0}"
WAIT_TIMEOUT_MIN="${WAIT_TIMEOUT_MIN:-45}"
WAIT_FOR_BUMP_CHECKS="${WAIT_FOR_BUMP_CHECKS:-false}"
USING_PAT="${USING_PAT:-false}"

log()     { printf '%s | %s\n' "$(date -u +%H:%M:%S)" "$*"; }
summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"; return 0; }

merged_count=0
merged_list=""

# ---------------------------------------------------------------- helpers

pick_pr() {
    gh pr list \
        --repo "$REPO" \
        --state open \
        --base "$BASE" \
        --label "$LABEL" \
        --limit 100 \
        --json number,title,isDraft,headRefName,headRefOid,headRepositoryOwner \
      | jq -c 'map(select(.isDraft | not)) | sort_by(.number) | first'
}

# GitHub computes mergeability lazily; UNKNOWN just means "ask again".
check_mergeable() {
    local pr=$1 state=""
    for _ in 1 2 3 4 5; do
        state=$(gh pr view "$pr" --repo "$REPO" --json mergeable --jq '.mergeable')
        [ "$state" = "UNKNOWN" ] || break
        sleep 5
    done
    if [ "$state" != "MERGEABLE" ]; then
        log "  #$pr is not mergeable (state: $state)"
        return 1
    fi
}

# A PR whose CI is still running is not a failure, it is just not ready yet
# -- so wait it out rather than stopping the drain. This is the normal state
# for a PR that was pushed to moments ago, including by the drain's own bump
# commit when the token re-triggers CI.
check_green() {
    local pr=$1
    local deadline=$(( SECONDS + WAIT_TIMEOUT_MIN * 60 ))
    local checks bad pending announced=0

    while :; do
        checks=$(gh pr checks "$pr" --repo "$REPO" --json name,bucket 2>/dev/null || true)

        if [ -z "$checks" ] || [ "$checks" = "[]" ]; then
            log "  #$pr reports no checks -- refusing to merge blind"
            return 1
        fi

        bad=$(jq '[.[] | select(.bucket == "fail" or .bucket == "cancel")] | length' <<<"$checks")
        if [ "$bad" -ne 0 ]; then
            jq -r '.[] | select(.bucket == "fail" or .bucket == "cancel") | "    \(.bucket)\t\(.name)"' <<<"$checks"
            log "  #$pr has $bad check(s) failing"
            return 1
        fi

        pending=$(jq '[.[] | select(.bucket == "pending")] | length' <<<"$checks")
        if [ "$pending" -eq 0 ]; then
            log "  #$pr checks green"
            return 0
        fi

        # A dry run is a report, not a gate -- say what is happening and move
        # on rather than blocking for however long CI takes.
        if [ "$DRY_RUN" = 'true' ]; then
            jq -r '.[] | select(.bucket == "pending") | "    pending\t\(.name)"' <<<"$checks"
            log "  #$pr has $pending check(s) still running (dry run does not wait)"
            return 0
        fi

        if [ "$SECONDS" -ge "$deadline" ]; then
            log "  #$pr still has $pending pending check(s) after ${WAIT_TIMEOUT_MIN}m"
            return 1
        fi

        if [ "$announced" -eq 0 ]; then
            log "  #$pr has $pending check(s) running, waiting (timeout ${WAIT_TIMEOUT_MIN}m)"
            announced=1
        fi
        sleep 20
    done
}

# The pre-merge checks ran on the PR head, but we merge the SHA *after* the
# version bump, which GITHUB_TOKEN pushes deliberately do not re-run CI for.
# Carrying the green verdict across that gap is only honest if the bump
# provably changed nothing but the two version lines -- so prove it.
verify_version_only_diff() {
    local from=$1 to=$2 files bad

    files=$(git diff --name-only "$from" "$to")
    if [ "$files" != "$GRADLE_FILE" ]; then
        log "  bump touched unexpected files: ${files//$'\n'/, }"
        return 1
    fi

    bad=$(git diff --unified=0 "$from" "$to" -- "$GRADLE_FILE" \
          | grep -E '^[+-]' \
          | grep -vE '^(\+\+\+|---)' \
          | grep -vcE '^[+-][[:space:]]*version(Code|Name)[[:space:]]*=' || true)
    if [ "${bad:-0}" -ne 0 ]; then
        log "  bump changed $bad non-version line(s) -- refusing to carry the check verdict"
        return 1
    fi
    log "  bump is provably version-only ($from -> $to)"
}

push_with_retry() {
    local ref=$1
    for delay in 0 2 4 8 16; do
        [ "$delay" -eq 0 ] || sleep "$delay"
        if git push origin "$ref"; then
            return 0
        fi
    done
    log "  push of $ref failed after retries"
    return 1
}

# Wait for every workflow run triggered by $sha on the base branch.
wait_for_runs() {
    local sha=$1
    local deadline=$(( SECONDS + WAIT_TIMEOUT_MIN * 60 ))
    local appear_deadline=$(( SECONDS + 300 ))
    local runs total pending failed

    log "  waiting for workflows on ${sha:0:7} (timeout ${WAIT_TIMEOUT_MIN}m)"
    while [ "$SECONDS" -lt "$deadline" ]; do
        runs=$(gh api "repos/$REPO/actions/runs?head_sha=$sha&per_page=100" \
                 --jq '[.workflow_runs[] | {name, status, conclusion}]' 2>/dev/null || echo '[]')
        total=$(jq 'length' <<<"$runs")

        if [ "$total" -eq 0 ]; then
            if [ "$SECONDS" -ge "$appear_deadline" ]; then
                log "  no workflow runs appeared for ${sha:0:7} -- nothing to wait for"
                return 0
            fi
            sleep 20
            continue
        fi

        pending=$(jq '[.[] | select(.status != "completed")] | length' <<<"$runs")
        if [ "$pending" -eq 0 ]; then
            jq -r '.[] | "    \(.conclusion)\t\(.name)"' <<<"$runs"
            failed=$(jq '[.[] | select(.conclusion != "success" and .conclusion != "skipped" and .conclusion != "neutral")] | length' <<<"$runs")
            if [ "$failed" -ne 0 ]; then
                log "  $failed workflow(s) failed on ${sha:0:7}"
                return 1
            fi
            log "  all $total workflow(s) green on ${sha:0:7}"
            return 0
        fi
        sleep 20
    done

    log "  timed out after ${WAIT_TIMEOUT_MIN}m waiting on ${sha:0:7}"
    return 1
}

# ------------------------------------------------------------------- main

log "draining '$LABEL' into $BASE (dry_run=$DRY_RUN)"
summary "### automerge: draining \`$LABEL\` into \`$BASE\`"
summary ""
summary "| PR | version | result |"
summary "|---|---|---|"

git config user.name  'github-actions[bot]'
git config user.email '41898282+github-actions[bot]@users.noreply.github.com'

while :; do
    if [ "$MAX_MERGES" -gt 0 ] && [ "$merged_count" -ge "$MAX_MERGES" ]; then
        log "reached max_merges=$MAX_MERGES, stopping"
        summary "| | | stopped at max_merges=$MAX_MERGES |"
        break
    fi

    git fetch --quiet origin "$BASE"

    pr_json=$(pick_pr)
    if [ "$pr_json" = "null" ] || [ -z "$pr_json" ]; then
        log "no open non-draft PR labelled '$LABEL' targets $BASE -- queue empty"
        summary "| | | queue empty |"
        break
    fi

    NUMBER=$(jq -r '.number'                    <<<"$pr_json")
    TITLE=$(jq  -r '.title'                     <<<"$pr_json")
    HEAD=$(jq   -r '.headRefName'               <<<"$pr_json")
    SHA=$(jq    -r '.headRefOid'                <<<"$pr_json")
    OWNER=$(jq  -r '.headRepositoryOwner.login' <<<"$pr_json")

    log "picked #$NUMBER ($HEAD @ ${SHA:0:7}): $TITLE"

    if [ "$OWNER" != "${REPO%%/*}" ]; then
        log "  #$NUMBER comes from fork $OWNER -- cannot push a version bump to it"
        summary "| #$NUMBER | | **stopped**: fork, cannot bump |"
        exit 1
    fi

    check_mergeable "$NUMBER" || { summary "| #$NUMBER | | **stopped**: not mergeable |"; exit 1; }
    if [ "$REQUIRE_CHECKS" = 'true' ]; then
        check_green "$NUMBER" || { summary "| #$NUMBER | | **stopped**: checks failing or stuck |"; exit 1; }
    fi

    # Next version always comes off the base branch: the base is what the
    # merge lands on, so it is what defines "next".
    git show "origin/$BASE:$GRADLE_FILE" > /tmp/base-build.gradle
    eval "$("$VERSION_SH" next /tmp/base-build.gradle | sed 's/^/new_/')"
    log "  version -> $new_name ($new_code)"

    if [ "$DRY_RUN" = 'true' ]; then
        log "  dry run: would bump to $new_name and squash merge #$NUMBER"
        summary "| #$NUMBER | → \`$new_name\` | dry run, nothing changed |"
        log "dry run stops after one PR (nothing advances)"
        break
    fi

    git fetch --quiet origin "$HEAD"
    git checkout --quiet -B "$HEAD" "origin/$HEAD"

    # Bring the branch up to the current base BEFORE bumping. Every merge
    # moves the version on the base, so a branch cut at an older version
    # would otherwise collide on exactly the two lines we are about to
    # rewrite -- which is to say every PR after the first one in a drain.
    if ! git merge --quiet --no-edit "origin/$BASE"; then
        git merge --abort || true
        log "  #$NUMBER conflicts with $BASE -- needs a human"
        summary "| #$NUMBER | | **stopped**: conflicts with \`$BASE\` |"
        exit 1
    fi

    pre_bump_sha=$(git rev-parse HEAD)
    "$VERSION_SH" apply "$GRADLE_FILE" "$new_code" "$new_name"

    merge_sha="$pre_bump_sha"
    if git diff --quiet -- "$GRADLE_FILE"; then
        log "  $GRADLE_FILE already at $new_name, nothing to commit"
    else
        git add "$GRADLE_FILE"
        git commit --quiet -m "version: bump to $new_name"
        merge_sha=$(git rev-parse HEAD)
        verify_version_only_diff "$pre_bump_sha" "$merge_sha" \
            || { summary "| #$NUMBER | → \`$new_name\` | **stopped**: bump was not version-only |"; exit 1; }
    fi

    if [ "$merge_sha" != "$SHA" ]; then
        push_with_retry "$HEAD" \
            || { summary "| #$NUMBER | → \`$new_name\` | **stopped**: push failed |"; exit 1; }

        # A PAT push re-triggers CI. When the base also requires status
        # checks, merging straight away races the checks it just started.
        if [ "$WAIT_FOR_BUMP_CHECKS" = 'true' ]; then
            wait_for_runs "$merge_sha" \
                || { summary "| #$NUMBER | → \`$new_name\` | **stopped**: bump commit failed CI |"; exit 1; }
        fi
    fi

    # Replace the PR description with just the attribution that belongs in
    # the commit log. Subject keeps the repo's "<title> (#<number>)" shape.
    commit_body=$(REPO="$REPO" PR="$NUMBER" "$COAUTHORS_SH")
    if [ -n "$commit_body" ]; then
        log "  co-authors:"
        printf '%s\n' "$commit_body" | sed 's/^/    /'
    else
        log "  no co-authors to credit"
    fi

    ARGS=(--repo "$REPO" --squash --match-head-commit "$merge_sha"
          --subject "$TITLE (#$NUMBER)" --body "$commit_body")
    if [ "$DELETE_BRANCH" = 'true' ]; then
        ARGS+=(--delete-branch)
    fi
    if ! merge_out=$(gh pr merge "$NUMBER" "${ARGS[@]}" 2>&1); then
        printf '%s\n' "$merge_out" | sed 's/^/    /'
        log "  merge of #$NUMBER refused"
        reason="merge refused"
        case "$merge_out" in
            *"not authorized to push"*|*"Protected branch"*|*"Resource not accessible"*)
                reason="**stopped**: token may not merge into \`$BASE\`"
                log "  $BASE is protected and this token may not merge into it."
                if [ "$USING_PAT" = 'true' ]; then
                    log "  AUTOMERGE_TOKEN is set, so that account still lacks merge rights on $BASE."
                else
                    log "  Running on GITHUB_TOKEN. Set the AUTOMERGE_TOKEN secret to a PAT or"
                    log "  GitHub App token belonging to someone allowed to merge into $BASE."
                fi
                ;;
            *"Pull Request is not mergeable"*|*"required status check"*)
                reason="**stopped**: base requires checks the bump commit has not passed"
                log "  the bumped commit has not satisfied required checks."
                log "  Re-dispatch with wait_for_bump_checks enabled."
                ;;
        esac
        summary "| #$NUMBER | → \`$new_name\` | $reason |"
        exit 1
    fi
    log "  merged #$NUMBER"

    git fetch --quiet origin "$BASE"

    merged_count=$((merged_count + 1))
    merged_list="$merged_list #$NUMBER"

    # Whatever the merge produced on the base branch is what CI now runs on:
    # on master that is test + release, on a test branch test + build.
    base_sha=$(git rev-parse "origin/$BASE")
    if ! wait_for_runs "$base_sha"; then
        summary "| #$NUMBER | → \`$new_name\` | merged, but **workflows failed** -- stopping |"
        log "stopping: post-merge workflows failed after #$NUMBER"
        exit 1
    fi

    summary "| #$NUMBER | → \`$new_name\` | merged, workflows green |"
done

log "done: merged $merged_count PR(s):${merged_list:- none}"
summary ""
summary "**merged $merged_count PR(s)**:${merged_list:- none}"
