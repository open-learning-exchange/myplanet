# Agent Scoring — Refactor Task Extraction Round

18 lists (9 agents × 2 prompts) · 181 raw tasks · 136 survived verification · merged to 81 shipped.

## Agents

| Label | Harness | Model | perf list | repo list |
|---|---|---|---|---|
| **claude·opus-5** | Claude Code | Claude Opus 5 | ✔ | ✔ (11 tasks) |
| **codex·sol-5.6** | Codex | Sol 5.6 | ✔ | ✔ |
| **copilot·grok-4.5** | GitHub Copilot | Grok 4.5 | ✔ | ✔ |
| **devin·swe-1.7** | Devin | SWE 1.7 | ✔ | ✔ |
| **openhands·kimi-k3** | OpenHands | Kimi K3 | ✔ | ✔ |
| **openhands·glm-5.2** | OpenHands | GLM 5.2 | ✔ | ✔ |
| **jules·gemini-3.1-pro / 3.6-flash** | Jules | Gemini 3.1 Pro (perf), Gemini 3.6 Flash (repo) | ✔ | ✔ |
| **qwen·coder-3.6** | Qwen | Qwen Coder 3.6 | ✔ | ✔ |
| **openhands·minimax-m2.7** | OpenHands | MiniMax M2.7 | ✔ | ✔ |

Jules is the only agent that ran **different models on the two prompts**, so its perf↔repo
gap measures Gemini 3.1 Pro against 3.6 Flash, not the prompts. Everything else holds the
model constant across both.

Three of the nine agents are OpenHands runs on different models (kimi-k3, glm-5.2,
minimax-m2.7), all committing under the same `openhands@all-hands.dev` identity. The
minimax pair is unambiguous — it ran ~4 hours after the others (10:33 and 10:47) on its own
branches. The kimi-k3 / glm-5.2 assignment is **not** established: those four branches were
pushed within 111 seconds of each other and were split by a commit-trailer difference that
is more likely a parallel-run artifact than a model marker. Treat those two rows as
interchangeable until confirmed.

The two prompts:
- **perf** — "performance quick wins and micro-optimizations that unblock bigger refactors"
- **repo** — "reinforce repository boundaries, call out cross-feature data leaks, tighten
  repository interfaces, move data functions out of UI/services into repositories"

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
| **claude·opus-5** | 21 | 0 | **13.86** | 0.660 | **10.08** | **0.480** |
| openhands·glm-5.2 | 20 | 0 | 13.65 | **0.683** | 8.33 | 0.417 |
| openhands·kimi-k3 | 20 | 2 | 13.23 | 0.662 | 8.66 | **0.433** |
| devin·swe-1.7 | 20 | 0 | 12.32 | 0.616 | 7.99 | 0.400 |
| copilot·grok-4.5 | 20 | 0 | 11.65 | 0.582 | 7.47 | 0.373 |
| codex·sol-5.6 | 20 | 0 | 10.18 | 0.509 | 7.03 | 0.352 |
| openhands·minimax-m2.7 | 20 | 15 | 2.79 | 0.140 | 1.37 | 0.068 |
| jules·gemini-3.1/3.6 | 20 | 13 | 1.85 | 0.093 | 1.23 | 0.062 |
| qwen·coder-3.6 | 20 | 15 | 1.47 | 0.074 | 0.65 | 0.033 |
| **Total** | **181** | **45** | **81.00** | — | **52.71** | — |

Point-sum check: 13.86 + 13.65 + 13.23 + 12.32 + 11.65 + 10.18 + 2.79 + 1.85 + 1.47 = **81.00** = shipped task count ✔

Adding minimax-m2.7 moved three existing rows: it took a 13th share of the survey
`notifyDataSetChanged` task (diluting eight agents slightly), a quarter share of the voices
sort task from claude·opus-5, and it corroborated one devin·swe-1.7 finding that had not
otherwise shipped as a standalone task — which is why **devin gained 0.53 net** and passed
copilot·grok-4.5.

## Which prompt was better

### Per agent

| Agent (model) | perf raw | perf /sub | perf Q/sub | repo raw | repo /sub | repo Q/sub | Better |
|---|---:|---:|---:|---:|---:|---:|:--|
| claude·opus-5 | **8.42** | **0.842** | **0.618** | 5.44 | 0.494 | 0.354 | **perf** (+70%) |
| copilot·grok-4.5 | **8.25** | **0.825** | **0.506** | 3.40 | 0.340 | 0.241 | **perf** (+143%) |
| codex·sol-5.6 | **6.29** | **0.629** | **0.428** | 3.89 | 0.389 | 0.275 | **perf** (+62%) |
| openhands·glm-5.2 | **7.30** | **0.730** | **0.452** | 6.35 | 0.635 | 0.382 | perf (+15%) |
| openhands·kimi-k3 | **6.89** | **0.689** | **0.447** | 6.34 | 0.634 | 0.419 | perf (+9%) |
| devin·swe-1.7 | 3.78 | 0.378 | 0.251 | **8.54** | **0.854** | **0.549** | **repo** (+126%) |
| openhands·minimax-m2.7 | 1.09 | 0.109 | 0.045 | **1.70** | **0.170** | **0.092** | **repo** (+56%) |
| qwen·coder-3.6 | 0.20 | 0.020 | 0.014 | **1.27** | **0.127** | **0.052** | **repo** (6×, off a tiny base) |
| jules (3.1 Pro → 3.6 Flash) | **1.52** | **0.152** | **0.100** | 0.34 | 0.034 | 0.024 | *model change, not prompt* |
| **Total** | **43.74** | **0.486** | **0.319** | 37.26 | 0.409 | 0.276 | **perf** |

Five of the eight model-constant agents did better on **perf**; devin·swe-1.7 and
openhands·minimax-m2.7 inverted, devin hardest — 8.54 raw on repo against 3.78 on perf.
Devin is the one agent
whose perf list was mostly the same idea five times over ("inject DispatcherProvider into
this ViewModel"), which merged down to a single entry, while its repo list found four
cross-repository couplings nobody else named.

The two openhands models are nearly prompt-neutral (+15% and +9%), which is what you'd expect
from an agent that grounds every claim in a file:line regardless of what it's asked to look
for.

### Overall

**perf wins on volume, not on quality-per-task.**

| | perf | repo |
|---|---:|---:|
| Tasks submitted | 90 | 91 |
| Dropped | 23 (25.6%) | 22 (24.2%) |
| Surviving raw tasks | 67 | 69 |
| **Distinct shipped tasks touched** | **49** | **42** |
| Exclusive to this prompt | 39 | 32 |
| Raw tasks per shipped task | **1.37** | 1.64 |
| Mean rating of exclusive tasks | 65.3 | 64.0 |
| Points earned | **43.74** | 37.26 |

The dropped rate and the mean rating are effectively identical (25.6% vs 24.2%; 65.3 vs 64.0).
The prompts produce equally *valuable* and equally *trustworthy* tasks. The entire gap is
**convergence**: the repo prompt made agents pile onto the same findings.

That is a property of the question, not the agents. "Where does UI touch a DAO?" has a
finite, enumerable answer — there is exactly one such class in this codebase, and six of eight
agents found it. "Where is a performance hotspot?" has no bottom; every agent went looking in a
different package and came back with something different. Hence 81 repo submissions collapsing
to 39 tasks (1.69:1) against 80 perf submissions producing 48 (1.35:1).

Overlap between the two prompts was small: **9 of 78 tasks** (12%) were proposed from both
sides — the survey `notifyDataSetChanged` fix, `DictionaryRepository`,
`HealthExaminationActivity`, the `commit()`→`apply()` sweep, the layout-listener retention,
the `viewLifecycleOwner` sweep, the unused `UploadManager` field, the ViewModel dispatcher
sweep, and the back-stack listener leaks. The two prompts are close to non-overlapping
searches of the same tree.

**Practical read:** if you run one prompt, run perf — it returns ~17% more distinct work for
the same review budget. If you run both, expect almost no wasted overlap, but budget for the
repo round being answered redundantly by everyone you ask.

Adding minimax narrowed the gap (perf led 0.537 vs 0.433 per submitted task across 8 agents;
0.486 vs 0.409 across 9) without changing the ordering. It also lifted both drop rates by
~6 points, because 15 of its 20 tasks failed verification.

### The Jules model comparison

The one clean model-vs-model datapoint in the round, same harness, same repo, adjacent prompts:

| | Gemini 3.1 Pro (perf) | Gemini 3.6 Flash (repo) |
|---|---:|---:|
| Submitted | 10 | 10 |
| Dropped | 6 | 7 |
| Raw pts | 1.52 | 0.34 |
| Quality pts | 1.00 | 0.24 |
| Unique surviving finding | 1 (`ChatApiService` main-thread body read, rated 62) | 0 |

Pro produced the round's only catch of a blocking `ResponseBody.string()` on the UI thread.
Flash produced nothing unique — its three survivors were all partial shares on tasks other
agents specified better. Both hallucinated the same class of nonexistent code, so the gap is
one of yield rather than of grounding discipline.

## Reading the scoreboard

- **claude·opus-5** leads on every measure. It is the only agent that reported a verified
  baseline (0 `RecyclerView.Adapter` subclasses, 0 `observeForever`/`GlobalScope`, 2
  `notifyDataSetChanged` sites, 1 UI→DAO leak) *before* proposing, and its per-task line
  references held on every check. It owns the single highest-rated task (85, teams-table SQL
  flow filters) and the largest block of unique findings. Its perf list alone (8.68 pts from
  10 tasks) outscores five agents' combined output across both prompts.
- **openhands·glm-5.2** wins on raw volume of *unique* findings — almost none of its tasks were
  duplicated by anyone — but they skew small (adapter color hoisting, `.toList()` removal, a
  dead import), which is why its quality-per-submitted sits below kimi-k3's despite a higher
  raw score.
- **openhands·kimi-k3** has the best quality-per-submitted of the pair: fewer, larger boundary
  moves (`TeamsInfoLookup`, enterprise reports extraction, exam-session orchestration), at the
  cost of two dropped tasks. Same harness, same repo — the two OpenHands models diverge on
  granularity, not on accuracy.
- **codex·sol-5.6** wrote the most rigorous acceptance criteria of any list and was the only
  agent to stack two tasks deliberately (SQL predicates → batch user lookup), but it duplicated
  more than the others, so its raw share is diluted.
- **devin·swe-1.7** is the strongest at exact line references (its nine-site
  `viewLifecycleOwner` sweep verified perfectly) but repeatedly overstated impact — five
  separate "move ViewModel work off the main thread" tasks whose real payoff is limited,
  because Room's suspend DAOs are already off-Main in this project.
- **openhands·minimax-m2.7** is the third OpenHands model and by far the weakest of the
  three — 15 of 20 dropped, against 2 and 0 for its stablemates. Same harness, same repo,
  same prompts: the spread across kimi-k3 (13.23), glm-5.2 (13.65) and minimax-m2.7 (2.79)
  is the largest within-harness gap in the round, and it is entirely a model effect.
  Its failure mode is distinct from jules' and qwen's, though — see below.
- **jules** and **qwen·coder-3.6** are the outliers. 13 of 20 and 15 of 20 tasks respectively
  were dropped for premises that do not hold in this codebase.

## Why the two low scorers failed

Both wrote against a generic Android codebase rather than this one. Verified counterexamples:

| Claim | Reality |
|---|---|
| qwen·coder-3.6/perf 1, 7, 9 — replace `observeForever`, `LiveData.value`→`postValue`, dedupe `observe()` | **Zero** LiveData in the codebase |
| qwen·coder-3.6/perf 2 — swap hardcoded `Dispatchers.IO` for an "existing `AppDispatchers`" | Zero hardcoded `Dispatchers.*` outside `DispatcherProvider.kt`; no `AppDispatchers` type exists |
| qwen·coder-3.6/perf 3 — `setHasStableIds(true)` "if `getItemId()` is overridden" | No adapter overrides `getItemId` → guard never satisfiable, no-op as written |
| qwen·coder-3.6/perf 4 — replace manual diff in adapters using `notifyDataSetChanged` | Zero `RecyclerView.Adapter` subclasses; all 41 adapters already use `DiffUtils.itemCallback` |
| qwen·coder-3.6/perf 8 — `adapter.submitList(newList, diff)` | Not a `ListAdapter` API; `AsyncListDiffer` already diffs off-thread |
| qwen·coder-3.6/repo 1, 9 — audit `DatabaseService`/`RealmInstance`; replace `RealmObject` returns | Realm was fully removed; none of these symbols exist |
| qwen·coder-3.6/repo 6 — extract inline `DiffUtil.ItemCallback` to `DIFF_CALLBACK` | Zero inline implementations outside `DiffUtils.kt` |
| qwen·coder-3.6/repo 8 — three named "unregistered observer" candidates | All three already unregister; the two real leaks went unnamed |
| jules·gemini-3.1-pro/perf 4 — remove `DefaultDispatcherProvider()` default args | Exactly one instance, in `DispatcherModule` — the correct place |
| jules·gemini-3.1-pro/perf 5 — delete deprecated `TTSManager` code | The only `@Deprecated` is `"Deprecated in Java"` on an override of a deprecated Android API |
| jules·gemini-3.1-pro/perf 6 — remove `readBytes().toRequestBody` OOM path | Every upload path already uses `asRequestBody`; zero matches |
| jules·gemini-3.1-pro/perf 7 — make ViewModels use `@ApplicationContext` | All three named VMs already do |
| jules·gemini-3.6-flash/repo 4 — standardise `CoursesProgressAdapter` on `DiffUtils.itemCallback` | It already uses it (`:106`) |
| jules·gemini-3.6-flash/repo 6 — inject `@ApplicationContext` into `DashboardViewModel` to drop `Context` params | It takes no `Context` parameter anywhere |
| jules·gemini-3.6-flash/repo 7 — replace hardcoded dispatchers in `MainApplication` | Zero `Dispatchers.*` in that file |
| jules·gemini-3.6-flash/repo 8, 10 — scope `SurveyFragment` collectors; cache its adapter | Already uses `collectWhenStarted`; adapter already cached behind `if (adapter == null)` |
| jules·gemini-3.6-flash/repo 9 — optimise `String.format("%02x")` in `SharedPrefManager` | No `%02x` in that file; the two real sites are `AndroidDecrypter` and `Sha256Utils` |

Two openhands·kimi-k3 tasks were also dropped on the same principle:
- **perf 4** — `CommunityLeadersAdapter:52` `findViewById` "on every bound row": it is inside
  `showLeaderDetails`, a click handler, not `onBindViewHolder`.
- **repo 9** — a six-file "raw `lifecycleScope.launch { collect }`" sweep: `VoicesFragment` and
  `ActivitiesFragment` already use `viewLifecycleOwner`, and `PersonalsFragment`,
  `SubmissionsFragment` and `FeedbackDetailActivity` contain no `lifecycleScope.launch` at all.
  Only `SettingsActivity:189` matched.

## minimax-m2.7 failed differently: right lines, wrong direction

Worth separating from jules and qwen, because the fix is not the same. minimax's **line
references were accurate** — every location it cited verified: `SurveyFragment:180,185`,
`MembersAdapter:207-216`, `VoicesFragment.sortNews:209` / `downloadResourcesForNews:190`,
`VoicesViewModel.filterNews:96`, `CoursesViewModel.sortCourses:76`,
`DashboardViewModel:119-131`, `ChatViewModel:162,174`, `TeamViewModel:102-112`. It read the
codebase. It then drew the wrong conclusion from what it read.

Three failure classes, none of them hallucination:

**Asked to verify rather than fix (4 tasks).** "Check if `TeamsTasksAdapter` uses inline
DiffUtil" — it uses `DiffUtils.itemCallback` at `:122`, which the agent could have checked
and didn't. "Audit `Dispatchers.IO` usage in the repository layer" — there is none.
"Verify `getFilterFacets` exists" — it does, in both interface and impl, already wrapped in
`withContext(dispatcherProvider.default)`. "Add KDoc explaining `STARTED` vs `RESUMED`."
These are the agent delegating its own verification step into the backlog.

**Remedy impossible or a category error (4 tasks).** `distinctUntilChanged()` on a
`StateFlow` is a documented no-op — `StateFlow` already conflates equal consecutive values,
so `BellDashboardViewModel.networkStatus` (`:41-42`) has nothing to dedupe, and the proposed
`.stateIn(...)` doesn't match the actual shape. Caching `getString(R.string.course_steps_count, n)`
in a companion object cannot work: both cited strings (`:304`, `:415`) take format
arguments that differ per row. `MemberMenuAdapter` is an `ArrayAdapter<CharSequence>` for a
two-item dialog, subclassed only to set a text colour — "convert it to
`DiffUtils.itemCallback`" applies a `RecyclerView.Adapter` API to something that isn't one.
`observeOpenedResourcesJob` is a `viewModelScope` job already cancelled before relaunch at
`:51`; `viewModelScope` cancels in `onCleared` by definition, so the proposed
`onCleared { job.cancel() }` is redundant.

**Right observation, wrong direction (4 tasks).** This is the interesting one, and it is
specific to the repo prompt. Told to "move data functions out of UI into repositories",
minimax proposed moving into `*Repository` four things that are not data access:
`VoicesViewModel.filterNews` (a search-query + label filter over an in-memory list),
`CoursesViewModel.sortCourses`/`processCourses` (which assembles `CoursesUiState`),
`DashboardViewModel.calculateIndividualProgress`/`calculateCommunityProgress` (pure
arithmetic on two `Int`s — `minOf(count,5)*2 + …`, touching no DAO), and
`TeamViewModel.applyFilters` (`teams.filter { it.name?.contains(query) }` on an
already-loaded list). Each would push UI selection state or presentation assembly *into* the
data layer — the exact inversion the round was meant to remove. codex·sol-5.6 hit the same
fork on the activity-chart aggregation and went the other way, explicitly: keep it "as a
pure ViewModel/use-layer mapper rather than polluting the repository with chart concepts."

The lesson generalises: an instruction of the form "move X out of A into B" is read by weaker
models as *"anything in A that looks like X belongs in B"*, with no test for whether the thing
is actually X. The prompt needs the negative case stated — "a function that touches no DAO,
or that depends on UI selection state, stays where it is."

## Corrections carried into the backlog

Premises that held but whose prescription was wrong; the backlog entry states the fix, not the proposal:

- **`notifyDataSetChanged` removal** — jules (both models) and openhands·glm-5.2/repo propose
  `submitList(currentList)` / "rely on `submitList`". `AsyncListDiffer` short-circuits on an
  identical list reference, so neither rebinds anything. Only the combine-into-a-row-model
  approach works. Those three lists were credited at ~⅓ the share of the complete versions.
- **openhands·kimi-k3/repo 6** — "drop the `TeamsSyncRepository` injections from both
  fragments": the field is inherited from `BaseTeamFragment:32` and two ViewModels inject it
  independently, so it cannot be deleted. Scoped down to the three call sites.
- **copilot·grok-4.5/perf 6** — `@Index("userId")` cannot serve the leading-wildcard `LIKE`
  these columns are queried with, and the required version bump destroys unsynced local writes.
  Kept at rating 28 with the remedy replaced.
- **qwen·coder-3.6/repo 3** — `initiateDownload(context, …)` would push an Android `Context`
  into a repository method. Kept at 35, redirected through `ResourceDownloadCoordinator`.
- **devin·swe-1.7/repo 9** — the claimed Dagger cycle is unproven; `Lazy<Repository>` edges
  appear elsewhere in this codebase. Rated on interface narrowing alone.
- **codex·sol-5.6/perf 4** — `PublicSurveyActivity` *does* unregister its fragment lifecycle
  callbacks (`:181`); only the back-stack listener leaks. **devin·swe-1.7/repo 5** —
  `DashboardElementActivity` already removes its listener (`:197`). Both narrowed to the
  genuinely unpaired registrations.
- **codex·sol-5.6/perf 8** — `getNextLeaderCandidate` does not load "all team memberships"; the
  DAO call is already scoped to `teamId` + `"membership"`. Only three predicates are in Kotlin,
  so the win is smaller than claimed; rated 58 rather than the ~72 the framing implied.
- **devin·swe-1.7/perf 2–7** — "runs on `Dispatchers.Main`" overstates the cost: Room's suspend
  DAOs execute off-Main through Room's own executors. The genuine main-thread work is the
  Kotlin mapping/aggregation that follows, which is what the merged entry names.

## Duplication profile

| Times proposed | Tasks | Example |
|---:|---:|---|
| 13 | 1 | Remove the two `notifyDataSetChanged()` calls (surveys) |
| 6 | 2 | `DictionaryRepository`; `HealthExaminationActivity` |
| 4 | 2 | `VoicesAdapter` drops its repository; unused `UploadManager` injection |
| 3 | 4 | `ChatHistoryFragment`; Voices→Resources images; `ActivitiesViewModel`; `viewLifecycleOwner` sweep |
| 2 | 14 | — |
| 1 | 58 | — |

58 of 81 tasks (72%) came from exactly one agent, which is why raw point totals stay close
even though the lists overlap heavily at the top: everyone found the same two or three
headline items, and the spread comes from what each found alone.
