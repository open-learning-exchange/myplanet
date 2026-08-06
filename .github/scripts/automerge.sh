#!/usr/bin/env bash
#
# Drain the automerge queue.
#
# Repeatedly: pick the lowest-numbered open non-draft PR carrying $LABEL and
# take it through four steps, in this order:
#
#   1. merge $BASE into the PR branch          (a conflict stops the drain)
#   2. bump the version on the PR branch
#   3. push, and wait for the workflows that push triggers -- build and test
#      run against the *prepared* commit, the exact tree that is about to land
#   4. wait for $BASE to be settled and green, then squash merge
#
# Stop on the first failure; otherwise keep going until no labelled PR is left.
#
# Verification happens before the merge, not after it, and both waits sit in
# front of step 4. That ordering is the whole point: while $BASE runs test and
# release for the previous merge, this PR is already being prepared and built,
# so the two overlap instead of queueing behind each other. Nothing merges
# onto a base that is mid-release or red -- the drain just stops asking about
# it at the moment it merged and starts asking at the moment it next needs to.
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
REQUIRED_WORKFLOWS="${REQUIRED_WORKFLOWS:-}"
DELETE_BRANCH="${DELETE_BRANCH:-true}"
DRY_RUN="${DRY_RUN:-true}"
MAX_MERGES="${MAX_MERGES:-0}"
WAIT_TIMEOUT_MIN="${WAIT_TIMEOUT_MIN:-45}"
USING_PAT="${USING_PAT:-false}"

# How long to give workflow runs to show up for a commit before concluding
# that none are coming. A push only queues its runs a few seconds later.
RUN_APPEAR_TIMEOUT_SEC=300

log()     { printf '%s | %s\n' "$(date -u +%H:%M:%S)" "$*"; }
summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"; return 0; }

merged_count=0
merged_list=""
last_base_sha=""

# A PR is re-prepared when $BASE moves under it. Bounded so a base that keeps
# moving cannot spin the drain until the job times out.
MAX_REPREPARES=2
reprep_pr=""
reprep_n=0

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

# GitHub computes mergeability lazily; UNKNOWN just means "ask again". This is
# only an early, cheap "don't bother" -- the real conflict test is step 1, the
# actual merge of $BASE into the branch.
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

# Fetch the Actions runs for a commit as [{name, status, conclusion}].
runs_for() {
    gh api "repos/$REPO/actions/runs?head_sha=$1&per_page=100" \
        --jq '[.workflow_runs[] | {name, status, conclusion}]' 2>/dev/null || echo '[]'
}

runs_failed()  { jq '[.[] | select(.status == "completed" and .conclusion != "success" and .conclusion != "skipped" and .conclusion != "neutral")] | length' <<<"$1"; }
runs_pending() { jq '[.[] | select(.status != "completed")] | length' <<<"$1"; }

# Which of the comma-separated workflow names in $2 have no successful run
# yet. A blank list means "whatever ran is what we judge on".
runs_missing() {
    jq -r --arg req "$2" '
        [ ($req | split(",")[] | gsub("^\\s+|\\s+$"; "")) | select(length > 0) ] as $need
        | ([ .[] | select(.status == "completed" and .conclusion == "success") | .name ] | unique) as $green
        | [ $need[] | select(. as $n | $green | index($n) | not) ]
        | join(", ")
    ' <<<"$1"
}

# Wait for the workflows triggered by $sha. Green means: nothing failed,
# nothing still running, and every workflow named in $need has a successful
# run. Fails fast on the first failing run rather than sitting out the rest.
#
#   $need    comma-separated workflows that must have passed ('' = any)
#   $absent  what "no runs at all" means: 'fail' or 'pass'
#
# The two callers want opposite things from $absent. For a commit we just
# pushed, no runs means we never got a verdict, so merging would be merging
# blind -- fail. For the base branch, no runs means there is simply nothing
# in flight to wait for -- pass.
wait_for_runs() {
    local sha=$1 need=$2 absent=$3
    local deadline=$(( SECONDS + WAIT_TIMEOUT_MIN * 60 ))
    local appear_deadline=$(( SECONDS + RUN_APPEAR_TIMEOUT_SEC ))
    local runs total pending failed missing announced=0

    log "  waiting for workflows on ${sha:0:7} (timeout ${WAIT_TIMEOUT_MIN}m)"
    while :; do
        runs=$(runs_for "$sha")
        total=$(jq 'length' <<<"$runs")

        if [ "$total" -gt 0 ]; then
            failed=$(runs_failed "$runs")
            if [ "$failed" -ne 0 ]; then
                jq -r '.[] | select(.status == "completed" and .conclusion != "success" and .conclusion != "skipped" and .conclusion != "neutral") | "    \(.conclusion)\t\(.name)"' <<<"$runs"
                log "  $failed workflow(s) failed on ${sha:0:7}"
                return 1
            fi

            pending=$(runs_pending "$runs")
            missing=$(runs_missing "$runs" "$need")

            if [ "$pending" -eq 0 ] && [ -z "$missing" ]; then
                jq -r '.[] | "    \(.conclusion)\t\(.name)"' <<<"$runs"
                log "  all $total workflow(s) green on ${sha:0:7}"
                return 0
            fi

            if [ "$pending" -ne 0 ] && [ "$announced" -eq 0 ]; then
                log "  $pending workflow(s) still running on ${sha:0:7}"
                announced=1
            fi
        fi

        # Nothing running and something still absent: give runs the appearance
        # window to get queued, then call it.
        if [ "$total" -eq 0 ] || { [ "${pending:-0}" -eq 0 ] && [ -n "${missing:-}" ]; }; then
            if [ "$SECONDS" -ge "$appear_deadline" ]; then
                if [ "$total" -eq 0 ]; then
                    if [ "$absent" = 'pass' ]; then
                        log "  no workflow runs for ${sha:0:7} -- nothing to wait for"
                        return 0
                    fi
                    log "  no workflow runs exist for ${sha:0:7} -- refusing to merge blind"
                else
                    log "  required workflow(s) never ran on ${sha:0:7}: $missing"
                fi
                if [ "$USING_PAT" != 'true' ]; then
                    log "  a push made with GITHUB_TOKEN deliberately does not trigger workflows."
                    log "  Set the AUTOMERGE_TOKEN secret to a PAT or GitHub App token so the"
                    log "  prepared commit gets its own build and test run."
                fi
                return 1
            fi
        fi

        if [ "$SECONDS" -ge "$deadline" ]; then
            log "  timed out after ${WAIT_TIMEOUT_MIN}m waiting on ${sha:0:7}"
            return 1
        fi
        sleep 20
    done
}

# Non-blocking early exit, checked at the top of the loop: if the last merge
# has *already* reported a failure there is no point preparing another PR, so
# say so now rather than after a build. The blocking version of this question
# is asked later, right before the merge (see step 4).
base_already_failed() {
    local sha=$1 runs bad
    runs=$(runs_for "$sha")
    bad=$(jq -r '.[] | select(.status == "completed" and .conclusion != "success" and .conclusion != "skipped" and .conclusion != "neutral") | "\(.conclusion)\t\(.name)"' <<<"$runs")
    [ -n "$bad" ] || return 1
    printf '%s\n' "$bad" | sed 's/^/    /'
    log "the last merge has already failed on $BASE (${sha:0:7})"
    return 0
}

# The bump is a machine edit on top of a tree CI is about to test anyway, so
# this is a guard against a broken version.sh rather than a gate: if it ever
# rewrites something other than the two version lines, stop before pushing.
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
        log "  bump changed $bad non-version line(s)"
        return 1
    fi
    log "  bump is version-only ($from -> $to)"
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

    if [ -n "$last_base_sha" ] && base_already_failed "$last_base_sha"; then
        summary "| | | **stopped**: \`$BASE\` red after the last merge |"
        exit 1
    fi

    git fetch --quiet origin "$BASE"
    base_at_prepare=$(git rev-parse "origin/$BASE")

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

    # Next version always comes off the base branch: the base is what the
    # merge lands on, so it is what defines "next".
    git show "origin/$BASE:$GRADLE_FILE" > /tmp/base-build.gradle
    eval "$("$VERSION_SH" next /tmp/base-build.gradle | sed 's/^/new_/')"
    log "  version -> $new_name ($new_code)"

    git fetch --quiet origin "$HEAD"

    if [ "$DRY_RUN" = 'true' ]; then
        # Merge for real, locally and detached, so the report can say whether
        # step 1 would actually work. Nothing is pushed.
        git checkout --quiet --detach "origin/$HEAD"
        if git merge --quiet --no-edit "origin/$BASE"; then
            log "  dry run: merges cleanly with $BASE, would bump to $new_name,"
            log "           wait for ${REQUIRED_WORKFLOWS:-the triggered workflows}, then squash merge #$NUMBER"
            summary "| #$NUMBER | → \`$new_name\` | dry run: would merge |"
        else
            git merge --abort || true
            log "  dry run: #$NUMBER conflicts with $BASE -- needs a human"
            summary "| #$NUMBER | | dry run: **conflicts** with \`$BASE\` |"
        fi
        log "dry run stops after one PR (nothing advances)"
        break
    fi

    git checkout --quiet -B "$HEAD" "origin/$HEAD"

    # 1. Merge the base in. Every merge moves the version on the base, so a
    #    branch cut at an older version would collide on exactly the two lines
    #    the bump is about to rewrite -- which is to say every PR after the
    #    first one in a drain. Doing it first also means the tree CI tests in
    #    step 3 is the tree that lands, not an older approximation of it.
    if ! git merge --quiet --no-edit "origin/$BASE"; then
        git merge --abort || true
        log "  #$NUMBER conflicts with $BASE -- needs a human"
        summary "| #$NUMBER | | **stopped**: conflicts with \`$BASE\` |"
        exit 1
    fi

    # 2. Bump the version.
    #
    #    Every merge into master publishes a release, and release.yml takes
    #    the tag it publishes (v0.62.98) straight from versionName in
    #    app/build.gradle. So every merge needs its own new version number,
    #    or the release overwrites the previous one.
    #
    #    The bump is written here, on the PR branch, so that it becomes part
    #    of the squash commit -- one commit on master per PR, carrying both
    #    the change and the version it ships as. Bumping master directly
    #    instead would mean two commits per PR, and the first of them would
    #    start a release build at a version that was already tagged.
    #
    #    "Next" is always computed from the base, never from this branch:
    #    master is what the merge lands on, so master is what defines which
    #    number is free. A branch cut weeks ago still says 0.62.97 while
    #    master has moved to 0.62.98 -- which is also why step 1 had to merge
    #    the base in first, or this write would land on top of a stale value.
    pre_bump_sha=$(git rev-parse HEAD)
    "$VERSION_SH" apply "$GRADLE_FILE" "$new_code" "$new_name"

    # Already at the right version when a previous dispatch prepared this same
    # PR and stopped before merging it. Nothing to redo -- the branch is the
    # prepared commit, and CI has already run on it.
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

    # 3. Push and let CI judge the prepared commit.
    if [ "$merge_sha" != "$SHA" ]; then
        push_with_retry "$HEAD" \
            || { summary "| #$NUMBER | → \`$new_name\` | **stopped**: push failed |"; exit 1; }
    else
        log "  branch already merged and bumped, head unchanged at ${SHA:0:7}"
    fi

    if [ "$REQUIRE_CHECKS" = 'true' ]; then
        wait_for_runs "$merge_sha" "$REQUIRED_WORKFLOWS" fail \
            || { summary "| #$NUMBER | → \`$new_name\` | **stopped**: prepared commit not green |"; exit 1; }
    else
        log "  require_checks is off -- merging ${merge_sha:0:7} unverified"
    fi

    # 4. Merge -- but only onto a settled, green base.
    #
    #    This is the wait that used to happen right after each merge. Asking
    #    here instead is what buys the overlap: while $BASE was running test
    #    and release for the previous merge, this PR was being prepared and
    #    built, so by now that wait is usually already over. What it must not
    #    do is merge onto a base that is still mid-release or has gone red.
    if [ "$REQUIRE_CHECKS" = 'true' ]; then
        git fetch --quiet origin "$BASE"
        wait_for_runs "$(git rev-parse "origin/$BASE")" '' pass \
            || { summary "| #$NUMBER | → \`$new_name\` | **stopped**: \`$BASE\` is red |"; exit 1; }

        # If $BASE moved while this PR was building -- somebody pushed to it
        # directly, or a second drain is running -- the prepared commit was
        # built against a base that no longer exists, and the version it
        # claims may already be taken. Prepare the same PR again on the new
        # base rather than merging a stale one.
        git fetch --quiet origin "$BASE"
        base_now=$(git rev-parse "origin/$BASE")
        if [ "$base_now" != "$base_at_prepare" ]; then
            if [ "$NUMBER" = "$reprep_pr" ]; then
                reprep_n=$((reprep_n + 1))
            else
                reprep_pr="$NUMBER"; reprep_n=1
            fi
            if [ "$reprep_n" -gt "$MAX_REPREPARES" ]; then
                log "  $BASE keeps moving under #$NUMBER -- giving up after $MAX_REPREPARES re-preparations"
                summary "| #$NUMBER | → \`$new_name\` | **stopped**: \`$BASE\` moving under it |"
                exit 1
            fi
            log "  $BASE moved to ${base_now:0:7} while #$NUMBER was building -- re-preparing it"
            continue
        fi
    fi

    #    Replace the PR description with just the attribution that belongs in
    #    the commit log; the subject keeps the repo's "<title> (#<number>)"
    #    shape.
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
                reason="**stopped**: base requires checks the prepared commit has not passed"
                log "  the prepared commit has not satisfied the base's required checks."
                log "  Make sure required_workflows covers every check $BASE requires."
                ;;
        esac
        summary "| #$NUMBER | → \`$new_name\` | $reason |"
        exit 1
    fi
    log "  merged #$NUMBER"

    git fetch --quiet origin "$BASE"
    last_base_sha=$(git rev-parse "origin/$BASE")

    merged_count=$((merged_count + 1))
    merged_list="$merged_list #$NUMBER"
    summary "| #$NUMBER | → \`$new_name\` | merged as \`${last_base_sha:0:7}\` |"
done

log "done: merged $merged_count PR(s):${merged_list:- none}"
summary ""
summary "**merged $merged_count PR(s)**:${merged_list:- none}"
