#!/usr/bin/env bash
#
# Drain the automerge queue. Per PR labelled $LABEL -- those also labelled
# $PRIORITY_LABEL first, then lowest number within each tier:
#
#   1. merge $BASE into the PR branch          (a conflict relabels it and moves on)
#   2. bump the version on the PR branch
#   3. push, and wait for build + test on that prepared commit (red relabels too)
#   4. wait for $BASE to be settled and green, then squash merge
set -euo pipefail

REPO="${REPO:?}"
BASE="${BASE:?}"

# queue,priority,conflict,failing in one field: an empty slot means that label
# goes unused, a slot left off the end keeps the default below.
if [ -n "${LABELS:-}" ]; then
    label_i=0
    while IFS= read -r label_part; do
        label_part="${label_part#"${label_part%%[![:space:]]*}"}"
        label_part="${label_part%"${label_part##*[![:space:]]}"}"
        case "$label_i" in
            0) LABEL="$label_part" ;;
            1) PRIORITY_LABEL="$label_part" ;;
            2) CONFLICT_LABEL="$label_part" ;;
            3) FAILING_LABEL="$label_part" ;;
        esac
        label_i=$(( label_i + 1 ))
    done <<<"$(printf '%s' "$LABELS" | tr ',' '\n')"
fi

LABEL="${LABEL:?the queue label may not be blank -- it is the first slot of LABELS}"
CONFLICT_LABEL="${CONFLICT_LABEL-conflict}"
FAILING_LABEL="${FAILING_LABEL-failing}"
PRIORITY_LABEL="${PRIORITY_LABEL-priority}"
GRADLE_FILE="${GRADLE_FILE:?}"
VERSION_SH="${VERSION_SH:?}"
COAUTHORS_SH="${COAUTHORS_SH:?}"
QUOTA_SH="${QUOTA_SH:-}"
PLAYSTORE_SH="${PLAYSTORE_SH:-}"
REQUIRE_CHECKS="${REQUIRE_CHECKS:-true}"
REQUIRED_WORKFLOWS="${REQUIRED_WORKFLOWS:-}"
PUBLISH_JOB="${PUBLISH_JOB:-}"
PUBLISH_FAIL_MARKER="${PUBLISH_FAIL_MARKER:-playstore upload failed twice}"
DELETE_BRANCH="${DELETE_BRANCH:-true}"
DRY_RUN="${DRY_RUN:-true}"
MAX_MERGES="${MAX_MERGES:-0}"
WAIT_TIMEOUT_MIN="${WAIT_TIMEOUT_MIN:-45}"
USING_PAT="${USING_PAT:-false}"
BASE_RERUN_ATTEMPTS="${BASE_RERUN_ATTEMPTS:-1}"
case "$BASE_RERUN_ATTEMPTS" in *[!0-9]*|"") BASE_RERUN_ATTEMPTS=1 ;; esac
HEAD_RERUN_ATTEMPTS="${HEAD_RERUN_ATTEMPTS:-1}"
case "$HEAD_RERUN_ATTEMPTS" in *[!0-9]*|"") HEAD_RERUN_ATTEMPTS=1 ;; esac

RUN_APPEAR_TIMEOUT_SEC=300
RERUN_START_TIMEOUT_SEC=180
POLL_INTERVAL_SEC=20
PR_SETTLE_TIMEOUT_SEC=120
MERGE_RETRY_DELAYS='5 10 15'

self_ref="${GITHUB_WORKFLOW_REF:-}"
self_ref="${self_ref%@*}"
SELF_WORKFLOW_PATH="${self_ref#*/*/}"
SELF_WORKFLOW_PATH="${SELF_WORKFLOW_PATH:-.github/workflows/automerge.yml}"
SELF_RUN_ID="${GITHUB_RUN_ID:-}"

log()     { printf '%s | %s\n' "$(date -u +%H:%M:%S)" "$*"; }
summary() { [ -n "${GITHUB_STEP_SUMMARY:-}" ] && printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"; return 0; }

merged_count=0
merged_list=""
conflict_count=0
conflict_list=""
failing_count=0
failing_list=""
skip_numbers=""
last_base_sha=""

MAX_REPREPARES=2
reprep_pr=""
reprep_n=0
rerun_log=""

pick_pr() {
    gh pr list \
        --repo "$REPO" \
        --state open \
        --base "$BASE" \
        --label "$LABEL" \
        --limit 1000 \
        --json number,title,isDraft,headRefName,headRefOid,headRepositoryOwner,labels \
      | jq -c --arg skip "$skip_numbers" --arg prio "$PRIORITY_LABEL" '
            [ $skip | split(" ")[] | select(length > 0) | tonumber ] as $done
            | ($done | map({ key: tostring, value: true }) | from_entries) as $doneSet
            | map(select(.isDraft | not))
            | map(select(.number as $n | $doneSet | has($n | tostring) | not))
            | map(. + { priority: (
                  ($prio | length > 0)
                  and (((.labels // []) | map(.name) | index($prio)) != null)
              ) })
            | sort_by([ (if .priority then 0 else 1 end), .number ]) | first'
}

pr_state()  { gh pr view "$1" --repo "$REPO" --json state --jq '.state' 2>/dev/null || echo ''; }
pr_review() { gh pr view "$1" --repo "$REPO" --json reviewDecision --jq '.reviewDecision' 2>/dev/null || echo ''; }

check_mergeable() {
    local pr=$1 state=""
    for _ in 1 2 3 4 5; do
        state=$(gh pr view "$pr" --repo "$REPO" --json mergeable --jq '.mergeable' 2>/dev/null || echo '')
        case "$state" in MERGEABLE|CONFLICTING) break ;; esac
        sleep 5
    done
    case "$state" in
        MERGEABLE) ;;
        CONFLICTING) return 1 ;;
        *)
            log "  #$pr mergeability is ${state:-unavailable} -- letting step 1 decide" ;;
    esac
}

add_marker_label() {
    local pr=$1 label=$2 color=$3 desc=$4
    gh pr edit "$pr" --repo "$REPO" --add-label "$label" >/dev/null 2>&1 && return 0
    gh label create "$label" --repo "$REPO" \
        --color "$color" --description "$desc" >/dev/null 2>&1 || true
    gh pr edit "$pr" --repo "$REPO" --add-label "$label" >/dev/null 2>&1
}

# Drop $LABEL so the queue moves on, and mark why on the PR itself.
retire_pr() {
    local pr=$1 marker=$2 color=$3 desc=$4 what=$5

    if [ "$DRY_RUN" = 'true' ]; then
        log "  dry run: #$pr $what -- would relabel it and move on"
        summary "| #$pr | | dry run: **$what** |"
        return 0
    fi

    local marked=""
    log "  #$pr $what -- dropping '$LABEL', moving on to the next PR"
    if [ -n "$marker" ]; then
        if add_marker_label "$pr" "$marker" "$color" "$desc"; then
            marked=", added \`$marker\`"
        else
            log "  #$pr: could not add '$marker'"
        fi
    fi
    if gh pr edit "$pr" --repo "$REPO" --remove-label "$LABEL" >/dev/null 2>&1; then
        summary "| #$pr | | **$what**: dropped \`$LABEL\`$marked |"
    else
        log "  #$pr: could not remove '$LABEL' -- skipped for this drain only"
        summary "| #$pr | | **$what**: \`$LABEL\` could not be removed |"
    fi
    return 0
}

handle_conflict() {
    conflict_count=$(( conflict_count + 1 ))
    conflict_list="$conflict_list #$1"
    retire_pr "$1" "$CONFLICT_LABEL" BD8652 'merge conflict' "conflicts with \`$BASE\`"
}

handle_failing() {
    failing_count=$(( failing_count + 1 ))
    failing_list="$failing_list #$1"
    retire_pr "$1" "$FAILING_LABEL" B60205 'build or test failing on the prepared commit' \
        'failed build or test on its prepared commit'
}

wait_pr_merged() {
    local pr=$1 state="" deadline=$(( SECONDS + PR_SETTLE_TIMEOUT_SEC ))
    while :; do
        state=$(pr_state "$pr")
        [ "$state" = "OPEN" ] || break
        [ "$SECONDS" -lt "$deadline" ] || { log "  #$pr still reads as open after ${PR_SETTLE_TIMEOUT_SEC}s"; break; }
        sleep 5
    done
}

runs_for() {
    local raw
    raw=$(gh api "repos/$REPO/actions/runs?head_sha=$1&per_page=100" 2>/dev/null) \
        || { echo '[]'; return 0; }
    jq -c --arg self "$SELF_WORKFLOW_PATH" --arg run "$SELF_RUN_ID" --arg branch "${2:-}" '
        [ .workflow_runs[]?
          | select(.path | startswith(".github/workflows/"))
          | select(.path != $self)
          | select(($run | length == 0) or ((.id | tostring) != $run))
          | select(($branch | length == 0) or (.head_branch == $branch))
          | {id, name, status, conclusion} ]' <<<"$raw" 2>/dev/null || echo '[]'
}

runs_bad() {
    jq -c '
        ([ .[] | select(.status == "completed" and .conclusion == "success") | .name ] | unique) as $green
        | [ .[]
            | select(.status == "completed")
            | select(.conclusion != "success" and .conclusion != "skipped"
                     and .conclusion != "neutral" and .conclusion != "cancelled")
            | select(.name as $n | $green | index($n) | not) ]' <<<"$1"
}

runs_green()   { jq '[.[] | select(.conclusion == "success")] | length' <<<"$1"; }
runs_pending() { jq '[.[] | select(.status != "completed")] | length' <<<"$1"; }

runs_missing() {
    jq -r --arg req "$2" '
        [ ($req | split(",")[] | gsub("^\\s+|\\s+$"; "")) | select(length > 0) ] as $need
        | ([ .[] | select(.status == "completed" and .conclusion == "success") | .name ] | unique) as $green
        | [ $need[] | select(. as $n | $green | index($n) | not) ]
        | join(", ")
    ' <<<"$1"
}

wait_for_runs() {
    local sha=$1 need=$2 absent=$3 branch=${4:-}
    local deadline=$(( SECONDS + WAIT_TIMEOUT_MIN * 60 ))
    local appear_deadline=$(( SECONDS + RUN_APPEAR_TIMEOUT_SEC ))
    local runs total bad pending failed green missing announced=0

    log "  waiting for workflows on ${sha:0:7}${branch:+ ($branch)} (timeout ${WAIT_TIMEOUT_MIN}m)"
    while :; do
        runs=$(runs_for "$sha" "$branch")
        total=$(jq 'length' <<<"$runs")

        if [ "$total" -gt 0 ]; then
            bad=$(runs_bad "$runs")
            failed=$(jq 'length' <<<"$bad")
            if [ "$failed" -ne 0 ]; then
                jq -r '.[] | "    \(.conclusion)\t\(.name)"' <<<"$bad"
                log "  $failed workflow(s) failed on ${sha:0:7}"
                return 2
            fi

            pending=$(runs_pending "$runs")
            missing=$(runs_missing "$runs" "$need")

            if [ "$pending" -eq 0 ] && [ -z "$missing" ]; then
                jq -r '.[] | "    \(.conclusion)\t\(.name)"' <<<"$runs"
                green=$(runs_green "$runs")
                log "  $green of $total workflow(s) green on ${sha:0:7}, none red"
                return 0
            fi

            if [ "$pending" -ne 0 ] && [ "$announced" -eq 0 ]; then
                log "  $pending workflow(s) still running on ${sha:0:7}"
                announced=1
            fi
        fi

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
        sleep "$POLL_INTERVAL_SEC"
    done
}

rerun_count_for() {
    local id=$1 n=0 e
    for e in $rerun_log; do
        [ "$e" = "$id" ] && n=$((n + 1))
    done
    printf '%s\n' "$n"
}

wait_run_restarted() {
    local id=$1 status deadline=$(( SECONDS + RERUN_START_TIMEOUT_SEC ))
    while [ "$SECONDS" -lt "$deadline" ]; do
        status=$(gh api "repos/$REPO/actions/runs/$id" --jq '.status' 2>/dev/null || echo '')
        [ "$status" = 'completed' ] || return 0
        sleep 5
    done
    log "  run $id still reads as completed ${RERUN_START_TIMEOUT_SEC}s after the re-run request"
    return 1
}

rerun_failed_runs() {
    local sha=$1 branch=${2:-} attempts=${3:-$BASE_RERUN_ATTEMPTS} id name triggered=0
    [ "$attempts" -gt 0 ] || return 1

    while IFS=$'\t' read -r id name; do
        [ -n "$id" ] || continue
        if [ "$(rerun_count_for "$id")" -ge "$attempts" ]; then
            log "  $name failed again after $attempts re-run(s) -- taking it as real"
            continue
        fi
        if gh api -X POST "repos/$REPO/actions/runs/$id/rerun-failed-jobs" >/dev/null 2>&1 \
           || gh api -X POST "repos/$REPO/actions/runs/$id/rerun" >/dev/null 2>&1; then
            rerun_log="$rerun_log $id"
            triggered=1
            log "  re-running $name on ${sha:0:7} (run $id)"
            wait_run_restarted "$id" || true
        else
            log "  could not re-run $name (run $id) -- does the token have actions: write?"
        fi
    done < <(jq -r '.[] | "\(.id)\t\(.name)"' <<<"$(runs_bad "$(runs_for "$sha" "$branch")")")

    [ "$triggered" -eq 1 ]
}

wait_base_green() {
    local sha=$1 rc=0
    while :; do
        wait_for_runs "$sha" '' pass "$BASE" && return 0
        rc=$?
        [ "$rc" -eq 2 ] || return 1
        [ "$BASE_RERUN_ATTEMPTS" -gt 0 ] \
            && log "  ${sha:0:7} passed build and test as a PR head minutes ago -- treating this as flaky"
        rerun_failed_runs "$sha" "$BASE" || return 1
    done
}

# Cause the warn annotation later reads as failed -- ask playstore.sh whether the track actually is ok.
publish_failed() {
    local sha=$1 run_id job_id note
    [ -n "$PUBLISH_JOB" ] || return 1
    for run_id in $(gh api "repos/$REPO/actions/runs?head_sha=$sha&per_page=100" \
                      --jq '.workflow_runs[] | select(.status == "completed") | .id' 2>/dev/null || true); do
        job_id=$(gh api "repos/$REPO/actions/runs/$run_id/jobs?per_page=100" \
                   --jq ".jobs[] | select(.name == \"$PUBLISH_JOB\") | .id" 2>/dev/null | head -n 1 || true)
        [ -n "$job_id" ] || continue
        note=$(gh api "repos/$REPO/check-runs/$job_id/annotations?per_page=100" \
                 --jq "[.[] | select(.annotation_level == \"warning\") | .message | select(startswith(\"$PUBLISH_FAIL_MARKER\"))] | first" 2>/dev/null || true)
        if [ -n "$note" ] && [ "$note" != "null" ]; then
            log "  $note"
            local pend=""
            [ -n "$PLAYSTORE_SH" ] && [ -x "$PLAYSTORE_SH" ] \
                && pend=$(GITHUB_OUTPUT='' PLAYSTORE_FORCE=true "$PLAYSTORE_SH" pending 2>/dev/null || true)
            # pending=false also means "could not tell" -- only the track-read branch emits track_code
            if grep -qx 'pending=false' <<<"$pend" && grep -qE '^track_code=[0-9]+$' <<<"$pend"; then
                log "  the $BASE track carries it now -- the warning is stale, carrying on"
                return 1
            fi
            return 0
        fi
    done
    return 1
}

quota_note() {
    quota_report=""
    quota_eta=""
    [ -n "$QUOTA_SH" ] && [ -x "$QUOTA_SH" ] || return 0

    local status line
    status=$("$QUOTA_SH" status 2>/dev/null) || return 0
    eval "$(sed -n "s/^report=/quota_report=/p; s/^next_free_local=/quota_eta=/p" <<<"$status")" || return 0

    # the whole forecast: the refill rate decides whether a resume keeps pace
    while IFS= read -r line; do
        log "  $line"
    done < <("$QUOTA_SH" forecast 2>/dev/null || printf '%s\n' "$quota_report")

    [ -n "$quota_eta" ] && log "  publish it without a rebuild: ${GITHUB_SERVER_URL:-https://github.com}/$REPO/actions/workflows/playstore.yml"
    return 0
}

base_already_failed() {
    local sha=$1 runs bad
    runs=$(runs_for "$sha" "$BASE")
    bad=$(jq -r '.[] | "\(.conclusion)\t\(.name)"' <<<"$(runs_bad "$runs")")
    [ -n "$bad" ] || return 1
    printf '%s\n' "$bad" | sed 's/^/    /'
    log "the last merge has already failed on $BASE (${sha:0:7})"
    return 0
}

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

merge_with_retry() {
    local pr=$1 head_sha=$2 delay live
    shift 2

    for delay in $MERGE_RETRY_DELAYS ''; do
        merge_out=$(gh pr merge "$pr" "$@" 2>&1) && return 0

        case "$merge_out" in *"Head branch was modified"*) ;; *) return 1 ;; esac

        live=$(gh pr view "$pr" --repo "$REPO" --json headRefOid --jq '.headRefOid' 2>/dev/null || echo '')
        if [ "$live" != "$head_sha" ]; then
            log "  #$pr head moved to ${live:-unknown} -- the green commit is gone, not retrying"
            return 1
        fi

        [ -n "$delay" ] || break
        log "  merge of #$pr hit GitHub's head race, but ${head_sha:0:7} is still the head -- retrying in ${delay}s"
        sleep "$delay"
    done
    return 1
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

log "draining '$LABEL' into $BASE${PRIORITY_LABEL:+, '$PRIORITY_LABEL' first} (dry_run=$DRY_RUN)"
summary "### automerge: draining \`$LABEL\` into \`$BASE\`${PRIORITY_LABEL:+, \`$PRIORITY_LABEL\` first}"
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
        if ! wait_base_green "$last_base_sha"; then
            summary "| | | **stopped**: \`$BASE\` red after the last merge |"
            exit 1
        fi
    fi

    git fetch --quiet origin "$BASE"
    base_at_prepare=$(git rev-parse "origin/$BASE")

    if [ "$REQUIRE_CHECKS" = 'true' ] && publish_failed "$base_at_prepare"; then
        log "the release on $BASE (${base_at_prepare:0:7}) was not published -- stopping the drain"
        quota_note
        summary "| | | **stopped**: release on \`$BASE\` not published${quota_eta:+, next playstore slot around $quota_eta} |"
        exit 1
    fi

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
    PRIORITY=$(jq -r '.priority'                <<<"$pr_json")

    TIER=""
    if [ "$PRIORITY" = true ]; then TIER=" $PRIORITY_LABEL"; fi

    log "picked$TIER #$NUMBER ($HEAD @ ${SHA:0:7}): $TITLE"

    if [ "$OWNER" != "${REPO%%/*}" ]; then
        log "  #$NUMBER comes from fork $OWNER -- cannot push a version bump to it"
        summary "| #$NUMBER | | **stopped**: fork, cannot bump |"
        exit 1
    fi

    state=$(pr_state "$NUMBER")
    if [ -n "$state" ] && [ "$state" != "OPEN" ]; then
        log "  #$NUMBER is no longer open (state: $state) -- skipping"
        skip_numbers="$skip_numbers $NUMBER"
        continue
    fi

    review=$(pr_review "$NUMBER")
    case "$review" in
        REVIEW_REQUIRED|CHANGES_REQUIRED|CHANGES_REQUESTED)
            log "  #$NUMBER needs a review ($review) -- skipping, $BASE would refuse it"
            summary "| #$NUMBER | | skipped: needs a review (\`$review\`) |"
            skip_numbers="$skip_numbers $NUMBER"
            continue ;;
    esac

    if ! check_mergeable "$NUMBER"; then
        handle_conflict "$NUMBER"
        skip_numbers="$skip_numbers $NUMBER"
        continue
    fi

    git show "origin/$BASE:$GRADLE_FILE" > /tmp/base-build.gradle
    eval "$("$VERSION_SH" next /tmp/base-build.gradle | sed 's/^/new_/')"
    log "  version -> $new_name ($new_code)"

    git fetch --quiet origin "$HEAD"

    if [ "$DRY_RUN" = 'true' ]; then
        git checkout --quiet --detach "origin/$HEAD"
        if git merge --quiet --no-edit "origin/$BASE"; then
            log "  dry run: merges cleanly with $BASE, would bump to $new_name,"
            log "           wait for ${REQUIRED_WORKFLOWS:-the triggered workflows}, then squash merge #$NUMBER"
            summary "| #$NUMBER | → \`$new_name\` | dry run: would merge |"
        else
            git merge --abort 2>/dev/null || git reset --quiet --hard HEAD
            handle_conflict "$NUMBER"
            skip_numbers="$skip_numbers $NUMBER"
            continue
        fi
        log "dry run stops after one mergeable PR (nothing advances)"
        break
    fi

    git checkout --quiet -B "$HEAD" "origin/$HEAD"

    if ! git merge --quiet --no-edit "origin/$BASE"; then
        git merge --abort 2>/dev/null || git reset --quiet --hard HEAD
        git checkout --quiet --detach "origin/$BASE" || true
        handle_conflict "$NUMBER"
        skip_numbers="$skip_numbers $NUMBER"
        continue
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
    else
        log "  branch already merged and bumped, head unchanged at ${SHA:0:7}"
    fi

    if [ "$REQUIRE_CHECKS" = 'true' ]; then
        checks_rc=0
        while :; do
            checks_rc=0
            wait_for_runs "$merge_sha" "$REQUIRED_WORKFLOWS" fail "$HEAD" || checks_rc=$?
            [ "$checks_rc" -eq 2 ] || break
            [ "$HEAD_RERUN_ATTEMPTS" -gt 0 ] \
                && log "  re-running the red workflow(s) before taking this as #$NUMBER's"
            rerun_failed_runs "$merge_sha" "$HEAD" "$HEAD_RERUN_ATTEMPTS" || break
        done
        # 2 is a verdict on this PR alone; 1 is no verdict at all, which still stops.
        if [ "$checks_rc" -eq 2 ]; then
            handle_failing "$NUMBER"
            skip_numbers="$skip_numbers $NUMBER"
            continue
        elif [ "$checks_rc" -ne 0 ]; then
            summary "| #$NUMBER | → \`$new_name\` | **stopped**: no verdict on the prepared commit |"
            exit 1
        fi
    else
        log "  require_checks is off -- merging ${merge_sha:0:7} unverified"
    fi

    if [ "$REQUIRE_CHECKS" = 'true' ]; then
        git fetch --quiet origin "$BASE"
        wait_base_green "$(git rev-parse "origin/$BASE")" \
            || { summary "| #$NUMBER | → \`$new_name\` | **stopped**: \`$BASE\` is red |"; exit 1; }

        if publish_failed "$(git rev-parse "origin/$BASE")"; then
            log "the release of the last merge was not published -- stopping before #$NUMBER"
            quota_note
            summary "| #$NUMBER | → \`$new_name\` | **stopped**: release of the last merge not published${quota_eta:+, next playstore slot around $quota_eta} |"
            exit 1
        fi

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
    if ! merge_with_retry "$NUMBER" "$merge_sha" "${ARGS[@]}"; then
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
            *"base branch policy prohibits"*)
                reason="**stopped**: \`$BASE\` policy refused the merge (review? codeowner?)"
                log "  $BASE's branch protection refused this merge. The prepared commit is"
                log "  green, so the unmet requirement is a policy one -- most often a missing"
                log "  approving review, a codeowner review, or an unresolved conversation."
                log "  --auto is deliberately not used: it returns before the merge happens,"
                log "  and the drain must know the merge landed to bump the next version."
                ;;
            *"Head branch was modified"*)
                reason="**stopped**: #$NUMBER's head no longer matches what CI passed"
                log "  re-run the drain to prepare #$NUMBER on its new head."
                ;;
            *"Base branch was modified"*)
                reason="**stopped**: \`$BASE\` moved between the check and the merge"
                log "  the bump was computed against an older $BASE."
                log "  re-run the drain to prepare #$NUMBER on top of the new $BASE."
                ;;
            *"is not mergeable"*|*"required status check"*)
                reason="**stopped**: base requires checks the prepared commit has not passed"
                log "  the prepared commit has not satisfied the base's required checks."
                log "  Make sure required_workflows covers every check $BASE requires."
                ;;
        esac
        summary "| #$NUMBER | → \`$new_name\` | $reason |"
        exit 1
    fi
    log "  merged #$NUMBER"
    wait_pr_merged "$NUMBER"

    git fetch --quiet origin "$BASE"
    last_base_sha=$(git rev-parse "origin/$BASE")

    merged_count=$((merged_count + 1))
    merged_list="$merged_list #$NUMBER"
    skip_numbers="$skip_numbers $NUMBER"
    summary "| #$NUMBER | → \`$new_name\` | merged as \`${last_base_sha:0:7}\` |"
done

log "done: merged $merged_count PR(s):${merged_list:- none}"
summary ""
summary "**merged $merged_count PR(s)**:${merged_list:- none}"
if [ "$conflict_count" -ne 0 ]; then
    log "left for a human, conflicting with $BASE:$conflict_list"
    summary ""
    if [ "$DRY_RUN" = 'true' ]; then
        summary "**$conflict_count PR(s) conflict with \`$BASE\`**:$conflict_list -- a real run would drop \`$LABEL\`${CONFLICT_LABEL:+ and add \`$CONFLICT_LABEL\`} and keep draining."
    else
        summary "**$conflict_count PR(s) conflict with \`$BASE\`**:$conflict_list -- \`$LABEL\` dropped${CONFLICT_LABEL:+, \`$CONFLICT_LABEL\` added}. Resolve the conflict and re-add \`$LABEL\` to queue it again."
    fi
fi
if [ "$failing_count" -ne 0 ]; then
    log "left for a human, failing on the prepared commit:$failing_list"
    summary ""
    if [ "$DRY_RUN" = 'true' ]; then
        summary "**$failing_count PR(s) failed on their prepared commit**:$failing_list -- a real run would drop \`$LABEL\`${FAILING_LABEL:+ and add \`$FAILING_LABEL\`} and keep draining."
    else
        summary "**$failing_count PR(s) failed on their prepared commit**:$failing_list -- \`$LABEL\` dropped${FAILING_LABEL:+, \`$FAILING_LABEL\` added}. Fix the build or test and re-add \`$LABEL\` to queue it again."
    fi
fi
