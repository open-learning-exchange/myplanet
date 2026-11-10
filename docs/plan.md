# myPlanet Refactor Round — Work Orders
date: 2026-11-10 · base commit: `2093a6224b221079f1413e2ab1f22cebec8cf736` (`2093a62` on `master`) · open PRs checked: #18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075 (recent past-week open PRs: #18555, #18554, #18551, #18540, #18539, #18455) · workflow runs checked on `master`: test run `42782082676` (`2093a62`), release run `42782082588` (`2093a62`), build run `42781301168` (`b14b2a1`)
### 1. eliminate duplicate release API fetch in playstore quota forecast (roadmap 5+8)
context: `.github/scripts/playstore-quota.sh:117-119` runs `measure` in `forecast()` and then
immediately runs `report=$(status | sed -n "s/^report='\(.*\)'$/\1/p")`, where `status()` at
line 82 calls `measure` a second time inside a command-substitution subshell. Because
`save_epochs()` at lines 33-44 fetches `gh api "repos/$REPO/releases?per_page=100"` whenever
`RELEASES_JSON` is unset, `playstore-quota.sh forecast` makes two identical GitHub Releases API
calls and runs `jq` over 100 releases twice during every Play Store and release workflow check.
Serving roadmap 5 and 8, this trims redundant GitHub API traffic and latency from release and
publishing automation.
files:
- touch: `.github/scripts/playstore-quota.sh` (`save_epochs` at lines 33-44, `status` at lines 81-112, `forecast` at lines 114-133)
- leave alone: `.github/scripts/playstore.sh`
steps:
1. Extract a helper function `build_status_report()` in `.github/scripts/playstore-quota.sh` that formats the `report` string from the already-computed `measure` variables (`used`, `SPARSE_SLOTS`, `remaining`, `status_word`, `next_slot_in`, `sparse_48h`, `sparse_week`) without re-invoking `measure`.
2. Update `status()` (lines 81-112) to call `measure`, call `build_status_report` to populate `report`, and emit the `GITHUB_OUTPUT`-compatible key-value lines.
3. Update `forecast()` (lines 114-133) to call `measure` once, call `build_status_report` directly to obtain `report` instead of spawning `status | sed ...`, and print the single-line summary.
4. Verify with `RELEASES_JSON` fixtures that `status`, `forecast`, and `gate` produce identical output before and after the change.
acceptance: `bash -n .github/scripts/playstore-quota.sh` and
`RELEASES_JSON='[]' NOW_EPOCH=1700000000 bash .github/scripts/playstore-quota.sh forecast` and
`./gradlew testDefaultDebugUnitTest` pass; `forecast` invokes `save_epochs` once per run and
prints the same quota summary string.
size budget: ~25 changed lines, 1 file
out of scope: do not change Play Store slot thresholds (`DAILY_CAP`, `SPARSE_SLOTS`) or
`.github/scripts/playstore.sh`.
---
### 2. defer collaborator API pagination until non-filtered coauthors exist (roadmap 8)
context: `.github/scripts/coauthors.sh:23-31` unconditionally executes
`gh api "repos/$REPO/collaborators?per_page=100" --paginate` at the start of every PR merge
before checking whether any commit author or `Co-authored-by:` trailer survives the PR-author
(`$author_l`), repo-owner (`$owner_l`), and `*[bot]` filters at lines 81-89. In addition,
lines 34-35 spawn two separate `jq` processes on `$pr_json` to read `.author.login` and
`.commits[]`. Serving roadmap 8, lazily loading collaborators only when an unfiltered candidate
login reaches the collaborator check eliminates an unnecessary paginated GitHub API call on
single-author and bot PR merges.
files:
