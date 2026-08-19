# Agent Scoring — Refactor Task Extraction Round

16 lists (8 agents × 2 prompts) · 161 raw tasks · 131 survived verification · merged to 78 shipped.

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

Jules is the only agent that ran **different models on the two prompts**, so its perf↔repo
gap measures Gemini 3.1 Pro against 3.6 Flash, not the prompts. Everything else holds the
model constant across both.

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
| **claude·opus-5** | 21 | 0 | **14.13** | **0.673** | **10.28** | **0.489** |
| openhands·glm-5.2 | 20 | 0 | 13.65 | 0.683 | 8.34 | 0.417 |
| openhands·kimi-k3 | 20 | 2 | 13.25 | 0.663 | 8.67 | **0.434** |
| devin·swe-1.7 | 20 | 0 | 11.79 | 0.590 | 7.66 | 0.383 |
| copilot·grok-4.5 | 20 | 0 | 11.66 | 0.583 | 7.48 | 0.374 |
| codex·sol-5.6 | 20 | 0 | 10.19 | 0.510 | 7.04 | 0.352 |
| jules·gemini-3.1/3.6 | 20 | 13 | 1.86 | 0.093 | 1.24 | 0.062 |
| qwen·coder-3.6 | 20 | 15 | 1.47 | 0.074 | 0.65 | 0.033 |
| **Total** | **161** | **30** | **78.00** | — | **51.35** | — |

Point-sum check: 14.13 + 13.65 + 13.25 + 11.79 + 11.66 + 10.19 + 1.86 + 1.47 = **78.00** = shipped task count ✔

## Which prompt was better

### Per agent

| Agent (model) | perf raw | perf /sub | perf Q/sub | repo raw | repo /sub | repo Q/sub | Better |
|---|---:|---:|---:|---:|---:|---:|:--|
| claude·opus-5 | **8.68** | **0.868** | **0.638** | 5.45 | 0.495 | 0.355 | **perf** (+75%) |
| copilot·grok-4.5 | **8.26** | **0.826** | **0.507** | 3.40 | 0.340 | 0.241 | **perf** (+143%) |
| codex·sol-5.6 | **6.30** | **0.630** | **0.429** | 3.89 | 0.389 | 0.275 | **perf** (+62%) |
| openhands·glm-5.2 | **7.30** | **0.730** | **0.452** | 6.35 | 0.635 | 0.382 | perf (+15%) |
| openhands·kimi-k3 | **6.90** | **0.690** | **0.448** | 6.35 | 0.635 | 0.419 | perf (+9%) |
| devin·swe-1.7 | 3.79 | 0.379 | 0.252 | **8.00** | **0.800** | **0.513** | **repo** (+111%) |
| qwen·coder-3.6 | 0.20 | 0.020 | 0.014 | **1.27** | **0.127** | **0.052** | **repo** (6×, off a tiny base) |
| jules (3.1 Pro → 3.6 Flash) | **1.52** | **0.152** | **0.100** | 0.34 | 0.034 | 0.024 | *model change, not prompt* |
| **Total** | **42.95** | **0.537** | **0.355** | 35.05 | 0.433 | 0.283 | **perf** |

Five of the seven model-constant agents did better on **perf**; only devin·swe-1.7 clearly
inverted, and it did so hard — 8.00 raw on repo against 3.79 on perf. Devin is the one agent
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
| Tasks submitted | 80 | 81 |
| Dropped | 15 (18.8%) | 15 (18.5%) |
| Surviving raw tasks | 65 | 66 |
| **Distinct shipped tasks touched** | **48** | **39** |
| Exclusive to this prompt | 39 | 30 |
| Raw tasks per shipped task | **1.35** | 1.69 |
| Mean rating of exclusive tasks | 66.0 | 64.8 |
| Points earned | **42.95** | 35.05 |

The dropped rate and the mean rating are effectively identical (18.8% vs 18.5%; 66.0 vs 64.8).
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

**Practical read:** if you run one prompt, run perf — it returns ~23% more distinct work for
the same review budget. If you run both, expect almost no wasted overlap, but budget for the
repo round being answered redundantly by everyone you ask.

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
| 12 | 1 | Remove the two `notifyDataSetChanged()` calls (surveys) |
| 6 | 2 | `DictionaryRepository`; `HealthExaminationActivity` |
| 4 | 2 | `VoicesAdapter` drops its repository; unused `UploadManager` injection |
| 3 | 4 | `ChatHistoryFragment`; Voices→Resources images; `ActivitiesViewModel`; `viewLifecycleOwner` sweep |
| 2 | 12 | — |
| 1 | 57 | — |

57 of 78 tasks (73%) came from exactly one agent, which is why raw point totals stay close
even though the lists overlap heavily at the top: everyone found the same two or three
headline items, and the spread comes from what each found alone.
