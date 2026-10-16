# Agent scoring — refactor-round merge 2026-10-16

Inputs: 12 lists / 120 raw tasks / 4 agents (devin ×3 lists, copilot ×4, claude ×4, codex ×1) across 3 prompts (test-workflow, perf, repository-boundary). Output backlog: `docs/refactor_tasks.md` — **98 merged tasks** shipped.

## Method

- Every task's premise was verified by grep/read against `master` @ `897cdbdb4` (5 commits past the lists' shared base `f18c50d`). **0 tasks dropped** — all 120 premises held at the code level.
- Duplicates merged; every proposing agent recorded on the merged entry. Same-agent near-duplicates were also merged (devin's 3 debounce tasks → 1; claude's 5 UserReadRepository tasks → 1; copilot's adapter micro-fixes → 2).
- Rating = `0.35·evidence + 0.40·impact + 0.25·risk-adjusted feasibility`, each 0–100.
  - *evidence*: verified file/line citations + whether the agent measured (timing/size) vs. asserted.
  - *impact*: real wall-time / correctness / code-size delta for a doer.
  - *feasibility*: diff size and collision risk with open-PR-owned files (flagged `⚠` in the backlog).
- Credit rules: unique good = 1; duplicate good = shared 1 split by completeness (not list order); bad = 0 (none).
- Completeness splits on the 12 duplicated tasks (sum = 1 each):
  - StepExamBenchmarkTest delete (4 agents): devin .25 / copilot .25 / codex .25 / claude .25 — equivalent proposals.
  - CourseRatingUtils smoke tests: copilot .6 / claude .4 — copilot's was a dedicated measured task; claude's was bundled.
  - Robolectric SDK pins: devin .5 / claude .5 — complementary halves (devin: 4 classes + default pin; claude: method pin + fallback).
  - MyLibraryDaoTest: devin .5 / codex .5 — devin owned the DB-share; codex owned sparse seeding.
  - UrlUtils / DownloadService / UploadConfigs / DownloadUtils mock-hoists: copilot .5 / claude .5 each — same recipe, both measured.
  - `Thread.sleep` waits (3 adapter tests): copilot .7 / devin .3 — copilot proposed the deterministic scheduler-drain fix (better + covered 2 sub-sites); devin proposed shortening the sleep.
  - FeedbackTest: claude .6 / copilot .4 — claude also caught the dead `TextUtils` mock (verified: `Feedback.kt` has no TextUtils import).
  - ResourcesListFilter: claude .6 / copilot .4 — claude added the short-circuit ordering.
  - `TeamDao.observeAll` narrowing: devin .6 / claude .4 — devin's batch-write companion is broader but collides with the flagged `TeamsRepositoryImpl`.

## Score table

| Agent | Lists | Raw submitted | Shipped entries (uniq + dup-share) | Points | Points/task | Quality-weighted Σ(credit·rating) | Q/task |
|-------|------:|--------------:|-----------------------------------:|-------:|------------:|----------------------------------:|-------:|
| claude | 4 | 50 | 29 + 4.75 | **33.75** | 0.675 | **2620.05** | 52.40 |
| copilot | 4 | 30 | 26 + 4.35 | **30.35** | **1.012** | **2263.85** | **75.46** |
| devin | 3 | 30 | 23 + 2.15 | **25.15** | 0.838 | **1748.35** | 58.28 |
| codex | 1 | 10 | 8 + 0.75 | **8.75** | 0.875 | **681.75** | 68.18 |
| **Σ** | 12 | 120 | 86 uniq + 12 dup | **98.00** | — | 7314.00 | — |

Point-sum check: 33.75 + 30.35 + 25.15 + 8.75 = **98.00** = shipped task count. ✓

## Reading

- **claude** wins raw points (most tasks, best per-file evidence: exact line numbers, measured timings, recipes) but dilutes on per-submission — 50 submissions include many small tasks.
- **copilot** wins both normalized columns — highest hit rate per task submitted; every copilot list was tightly scoped with measured timings. Docked slightly for factual slips (LifeRepositoryTest "2 tests" → actually 3; `DashboardSurveysAdapter` "raw position" → already `bindingAdapterPosition`; `MembersAdapter` dateCache absent — hedged, not wrong).
- **devin** produced the broadest boundary/architecture tasks but repeatedly proposed work in files other lists mark as open-PR-owned (`TeamsRepositoryImpl`, `DashboardActivity`, `ExamTakingFragment`, `RoomModule`, `build.yml`, voices files, `UserInformationFragment` is fine) — 8 of 30 tasks carry `⚠` collision flags, dragging feasibility.
- **codex** small but surgical — the 9 sparse-fixture tasks are all the same correct pattern; highest per-file precision, lowest variety.

## Dropped / corrected

- **Dropped: 0.** Every cited construct exists in the tree.
- Corrected facts (kept, rating adjusted): copilot's runBlocking counts (TeamTaskDaoTest 6 not 5; SubmitPhotosDaoTest 4 not 3; round-trip 6/9 not 12/17); `LifeRepositoryTest` = 3 tests; `KeyboardUtils.setupUI` creates one listener *per recursive call* (premise holds: still one listener per view in the tree).
- Flagged for sequencing (premise holds, feasibility marked down): devin-1#9 `build.yml`, devin-6#4 `RoomModule`, #6 DashboardActivity, #7 ExamTakingFragment, devin-11#1/#4/#5/#6/#7 (impl/voices/`ServerUrlMapper` files under open PRs), devin-6#1 `SurveysViewModel`.

## Guesses / caveats

- Agents' measured shard timings (14.1 s, 18.2 s, ~5 s etc.) were **not** re-measured — verified the code path only.
- Open-PR file ownership was taken from the lists' own exclusion tables, not re-checked against GitHub today.
- Completeness splits (e.g. .6/.4) are judgment calls on whose proposal a doer would rather execute.
- The `⚠` set uses the *strict* exclusion rule (all open PRs); under devin's looser "ready/merge labels only" rule several flags lift — scoring treats strict as correct since 2 of 4 agents used it.
