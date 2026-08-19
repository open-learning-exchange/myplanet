# Agent Scoring — Refactor Task Extraction Round

16 lists (8 agents × 2 prompts) · 161 raw tasks · 131 survived verification · merged to 78 shipped.

## Method

**Rating (per shipped task, 1–100)** = 100 × (0.40·Evidence + 0.35·Impact + 0.25·Feasibility).
- *Evidence* (0.40) — did the agent cite a location, and did it survive inspection?
  1.0 = exact file+line, verified verbatim · 0.5 = right file, no/incorrect line detail ·
  0.2 = named only a pattern, I had to locate the site myself.
- *Impact* (0.35) — hot-path/sync/scroll cost actually removed, or a boundary invariant made
  greppable and enforceable. One-off allocations and cosmetic cleanups score low.
- *Feasibility, risk-adjusted* (0.25) — diff size × blast radius × merge exposure to the
  contested files (`TeamsRepositoryImpl`, `BaseRecyclerFragment`, voices) and to the open PRs
  the docs themselves cite. A correct-but-conflicted change is discounted.

**Agent points.** Each shipped task is worth exactly **1.0 point**, split across its proposers
by **completeness** — how much of the task a reviewer could execute from that list alone
(exact lines, correct remedy, named acceptance criteria, conflict notes). Order of submission
is irrelevant. A unique correct task = 1.0; a task proposed by 12 agents splits 1.0 twelve
ways, weighted. Dropped or unverifiable tasks = 0.

Three derived scores: **raw** (Σ shares), **per-submitted** (raw ÷ tasks submitted), and
**quality-weighted** (Σ share × rating ÷ 100, then ÷ tasks submitted).

## Scoreboard

| Agent | Submitted | Dropped | Raw pts | Per-submitted | Quality pts | Quality/submitted |
|---|---:|---:|---:|---:|---:|---:|
| **claude** (perf + repo) | 21 | 0 | **14.13** | **0.673** | **10.28** | **0.489** |
| **openhands-B** (perf + repo) | 20 | 0 | 13.65 | 0.683 | 8.34 | 0.417 |
| **openhands-A** (perf + repo) | 20 | 2 | 13.25 | 0.663 | 8.67 | **0.434** |
| **devin** (perf + repo) | 20 | 0 | 11.79 | 0.590 | 7.66 | 0.383 |
| **copilot** (perf + repo) | 20 | 0 | 11.66 | 0.583 | 7.48 | 0.374 |
| **codex** (perf + repo) | 20 | 0 | 10.19 | 0.510 | 7.04 | 0.352 |
| **jules** (perf + repo) | 20 | 13 | 1.86 | 0.093 | 1.24 | 0.062 |
| **qwen** (perf + repo) | 20 | 15 | 1.47 | 0.074 | 0.65 | 0.033 |
| **Total** | **161** | **30** | **78.00** | — | — | — |

Point-sum check: 14.13 + 13.65 + 13.25 + 11.79 + 11.66 + 10.19 + 1.86 + 1.47 = **78.00** = shipped task count ✔

## Reading the table

- **claude** leads on every measure. It is the only agent that reported a verified baseline
  (0 `RecyclerView.Adapter` subclasses, 0 `observeForever`/`GlobalScope`, 2
  `notifyDataSetChanged` sites, 1 UI→DAO leak) *before* proposing, and its per-task line
  references held on every check. It also owns the single highest-rated task (85, teams-table
  SQL flow filters) and the largest block of unique findings.
- **openhands-B** wins on raw volume of *unique* findings — almost none of its tasks were
  duplicated by anyone — but they skew small (adapter color hoisting, `.toList()` removal,
  a dead import), which is why its quality-per-submitted sits below openhands-A's despite a
  higher raw score.
- **openhands-A** has the best quality-per-submitted among the openhands pair: fewer, larger
  boundary moves (`TeamsInfoLookup`, enterprise reports extraction, exam-session
  orchestration), at the cost of two dropped tasks.
- **codex** wrote the most rigorous acceptance criteria of any list and was the only agent to
  stack two tasks deliberately (SQL predicates → batch user lookup), but it duplicated more
  than the others, so its raw share is diluted.
- **devin** is the strongest at exact line references (its nine-site `viewLifecycleOwner`
  sweep verified perfectly) but repeatedly overstated impact — five separate
  "move ViewModel work off the main thread" tasks whose real payoff is limited, because Room's
  suspend DAOs are already off-Main in this project.
- **jules** and **qwen** are the outliers. 13 of 20 and 15 of 20 tasks respectively were
  dropped for premises that do not hold in this codebase.

## Why the two low scorers failed

Both wrote against a generic Android codebase rather than this one. Verified counterexamples:

| Claim | Reality |
|---|---|
| qwen/perf 1, 7, 9 — replace `observeForever`, `LiveData.value`→`postValue`, dedupe `observe()` | **Zero** LiveData in the codebase |
| qwen/perf 2 — swap hardcoded `Dispatchers.IO` for an "existing `AppDispatchers`" | Zero hardcoded `Dispatchers.*` outside `DispatcherProvider.kt`; no `AppDispatchers` type exists |
| qwen/perf 3 — `setHasStableIds(true)` "if `getItemId()` is overridden" | No adapter overrides `getItemId` → guard never satisfiable, no-op as written |
| qwen/perf 4 — replace manual diff in adapters using `notifyDataSetChanged` | Zero `RecyclerView.Adapter` subclasses; all 41 adapters already use `DiffUtils.itemCallback` |
| qwen/perf 8 — `adapter.submitList(newList, diff)` | Not a `ListAdapter` API; `AsyncListDiffer` already diffs off-thread |
| qwen/repo 1, 9 — audit `DatabaseService`/`RealmInstance`; replace `RealmObject` returns | Realm was fully removed; none of these symbols exist |
| qwen/repo 6 — extract inline `DiffUtil.ItemCallback` to `DIFF_CALLBACK` | Zero inline implementations outside `DiffUtils.kt` |
| qwen/repo 8 — three named "unregistered observer" candidates | All three already unregister; the two real leaks went unnamed |
| jules/perf 4 — remove `DefaultDispatcherProvider()` default args | Exactly one instance, in `DispatcherModule` — the correct place |
| jules/perf 5 — delete deprecated `TTSManager` code | The only `@Deprecated` is `"Deprecated in Java"` on an override of a deprecated Android API |
| jules/perf 6 — remove `readBytes().toRequestBody` OOM path | Every upload path already uses `asRequestBody`; zero matches |
| jules/perf 7 — make ViewModels use `@ApplicationContext` | All three named VMs already do |
| jules/repo 4 — standardise `CoursesProgressAdapter` on `DiffUtils.itemCallback` | It already uses it (`:106`) |
| jules/repo 6 — inject `@ApplicationContext` into `DashboardViewModel` to drop `Context` params | It takes no `Context` parameter anywhere |
| jules/repo 7 — replace hardcoded dispatchers in `MainApplication` | Zero `Dispatchers.*` in that file |
| jules/repo 8, 10 — scope `SurveyFragment` collectors; cache its adapter | Already uses `collectWhenStarted`; adapter already cached behind `if (adapter == null)` |
| jules/repo 9 — optimise `String.format("%02x")` in `SharedPrefManager` | No `%02x` in that file; the two real sites are `AndroidDecrypter` and `Sha256Utils` |

Two openhands tasks were also dropped on the same principle:
- **openhands-A/perf 4** — `CommunityLeadersAdapter:52` `findViewById` "on every bound row":
  it is inside `showLeaderDetails`, a click handler, not `onBindViewHolder`.
- **openhands-A/repo 9** — a six-file "raw `lifecycleScope.launch { collect }`" sweep:
  `VoicesFragment` and `ActivitiesFragment` already use `viewLifecycleOwner`, and
  `PersonalsFragment`, `SubmissionsFragment` and `FeedbackDetailActivity` contain no
  `lifecycleScope.launch` at all. Only `SettingsActivity:189` matched.

## Corrections carried into the backlog

Premises that held but whose prescription was wrong; the backlog entry states the fix, not the proposal:

- **`notifyDataSetChanged` removal** — jules (both lists) and openhands-B/repo propose
  `submitList(currentList)` / "rely on `submitList`". `AsyncListDiffer` short-circuits on an
  identical list reference, so neither rebinds anything. Only the combine-into-a-row-model
  approach works. Those three lists were credited at ~⅓ the share of the complete versions.
- **openhands-A/repo 6** — "drop the `TeamsSyncRepository` injections from both fragments":
  the field is inherited from `BaseTeamFragment:32` and two ViewModels inject it
  independently, so it cannot be deleted. Scoped down to the three call sites.
- **copilot/perf 6** — `@Index("userId")` cannot serve the leading-wildcard `LIKE` these
  columns are queried with, and the required version bump destroys unsynced local writes.
  Kept at rating 28 with the remedy replaced.
- **qwen/repo 3** — `initiateDownload(context, …)` would push an Android `Context` into a
  repository method. Kept at 35, redirected through `ResourceDownloadCoordinator`.
- **devin/repo 9** — the claimed Dagger cycle is unproven; `Lazy<Repository>` edges appear
  elsewhere in this codebase. Rated on interface narrowing alone.
- **codex/perf 4** — `PublicSurveyActivity` *does* unregister its fragment lifecycle callbacks
  (`:181`); only the back-stack listener leaks. **devin/repo 5** — `DashboardElementActivity`
  already removes its listener (`:197`). Both narrowed to the genuinely unpaired registrations.
- **codex/perf 8** — `getNextLeaderCandidate` does not load "all team memberships"; the DAO
  call is already scoped to `teamId` + `"membership"`. Only three predicates are in Kotlin,
  so the win is smaller than claimed; rated 58 rather than the ~72 the framing implied.
- **devin/perf 2–7** — "runs on `Dispatchers.Main`" overstates the cost: Room's suspend DAOs
  execute off-Main through Room's own executors. The genuine main-thread work is the Kotlin
  mapping/aggregation that follows, which is what the merged entry names.

## Duplication profile

| Times proposed | Tasks | Example |
|---:|---:|---|
| 12 | 1 | Remove the two `notifyDataSetChanged()` calls (surveys) |
| 6 | 2 | `DictionaryRepository`; `HealthExaminationActivity` |
| 4 | 2 | `VoicesAdapter` drops its repository; unused `UploadManager` injection |
| 3 | 4 | `ChatHistoryFragment`; Voices→Resources images; `ActivitiesViewModel`; `viewLifecycleOwner` sweep |
| 2 | 12 | — |
| 1 | 57 | — |

57 of 78 tasks (73%) came from exactly one agent, which is why raw point totals stay close
even though the lists overlap heavily at the top: everyone found the same two or three
headline items, and the spread comes from what each found alone.
