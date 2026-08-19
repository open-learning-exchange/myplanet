# Agent Scoring — Refactor Task Extraction Round

27 lists (9 agents × 3 prompts) · 271 raw tasks · 209 survived verification · merged to 127 shipped.

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

The three prompts:
- **perf** — "performance quick wins and micro-optimizations that unblock bigger refactors"
- **repo** — "reinforce repository boundaries, call out cross-feature data leaks, tighten
  repository interfaces, move data functions out of UI/services into repositories"
- **perf2** — same performance theme as `perf`, but a **structured work-order template**:
  every task must carry *context / files / steps / acceptance / size budget / out of scope*,
  cite exact file:line evidence, and be checked against the open-PR changed-file set.

`perf2` is the one prompt that constrains the *form* of the answer rather than only its
topic, and it is the best-performing prompt in the round by every measure — see below.

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
| **claude·opus-5** | 31 | 0 | **21.19** | **0.684** | **15.26** | **0.492** |
| openhands·kimi-k3 | 30 | 2 | 19.73 | 0.658 | 13.33 | **0.444** |
| codex·sol-5.6 | 30 | 0 | 19.58 | 0.653 | 12.86 | 0.429 |
| openhands·glm-5.2 | 30 | 0 | 19.00 | 0.633 | 11.49 | 0.383 |
| devin·swe-1.7 | 30 | 0 | 18.33 | 0.611 | 11.54 | 0.385 |
| copilot·grok-4.5 | 30 | 0 | 15.10 | 0.503 | 9.51 | 0.317 |
| jules·gemini-3.1/3.6 | 30 | 15 | 6.46 | 0.215 | 4.30 | 0.143 |
| openhands·minimax-m2.7 | 30 | 20 | 6.14 | 0.205 | 3.21 | 0.107 |
| qwen·coder-3.6 | 30 | 25 | 1.46 | 0.049 | 0.64 | 0.021 |
| **Total** | **271** | **62** | **127.00** | — | **72.14** | — |

Point-sum check: 21.19 + 19.73 + 19.58 + 19.00 + 18.33 + 15.10 + 6.46 + 6.14 + 1.46 = **127.00** = shipped task count ✔

**copilot·grok-4.5 carries an asterisk.** Six of its ten `perf2` tasks are near-verbatim
copies of an earlier list and earn zero — see *Contamination* below. Scored as if
independent it would sit at 20.14 raw / 0.671 per-submitted, i.e. second place. The
uncredited six are counted as *submitted* but not as *dropped*: the findings are real and
ship under codex·sol-5.6.

The `perf2` round reordered the middle of the table. codex·sol-5.6 went from last of the six
strong agents to third, kimi-k3 took second, and glm-5.2 slipped from second to fourth —
none of which was visible in the first two rounds.

## Which prompt was better

### Per agent

| Agent (model) | perf raw | perf /sub | perf Q/sub | repo raw | repo /sub | repo Q/sub | Better |
|---|---:|---:|---:|---:|---:|---:|:--|
Raw points per prompt (per-submitted in brackets):

| Agent | perf | repo | **perf2** | Best |
|---|---:|---:|---:|:--|
| claude·opus-5 | 8.11 (0.811) | 5.44 (0.494) | **7.63 (0.763)** | perf ≈ perf2 |
| codex·sol-5.6 | 6.29 (0.629) | 3.89 (0.389) | **9.40 (0.940)** | **perf2** |
| openhands·kimi-k3 | 6.83 (0.683) | 6.34 (0.634) | **6.56 (0.656)** | flat |
| openhands·glm-5.2 | 6.37 (0.637) | 6.35 (0.635) | **6.28 (0.628)** | flat |
| devin·swe-1.7 | 3.74 (0.374) | **8.54 (0.854)** | 6.05 (0.605) | repo |
| copilot·grok-4.5 | 8.25 (0.825) | 3.40 (0.340) | 3.45 (0.345)\* | perf |
| jules (3.1 Pro / 3.6 Flash / 3.1 Pro) | 1.44 (0.144) | 0.26 (0.026) | **4.76 (0.476)** | **perf2** |
| openhands·minimax-m2.7 | 1.09 (0.109) | 1.70 (0.170) | **3.35 (0.335)** | **perf2** |
| qwen·coder-3.6 | 0.19 (0.019) | 1.27 (0.127) | 0.00 (0.000) | repo |
| **Total** | **42.31 (0.470)** | 37.18 (0.409) | **47.51 (0.528)** | **perf2** |

\* copilot's perf2 figure excludes six uncredited verbatim copies.

**perf2 is the best prompt in the round**, and it is not close on the metric that matters
most — it has both the highest yield per submitted task (0.528) *and* the lowest drop rate:

| | perf | repo | perf2 |
|---|---:|---:|---:|
| Submitted | 90 | 91 | 90 |
| Dropped | 23 (25.6%) | 22 (24.2%) | **17 (18.9%)** |
| Points | 42.31 | 37.18 | **47.51** |
| Per submitted | 0.470 | 0.409 | **0.528** |

The lift is concentrated at the bottom of the table. The six strong agents were roughly
prompt-insensitive (kimi and glm are flat to three decimal places across all three), but
**jules went from 1.70 combined across perf+repo to 4.76 on perf2 alone**, and minimax
doubled. Forcing *context → files → steps → acceptance → size budget → out of scope* per
task is what separated the weak models from their own worst output: it is very hard to write
an "acceptance" line for a defect that does not exist, and the template made the absence
obvious. That is the single most actionable result in this whole exercise — **the template
is worth more than the model choice for the bottom half of the field.**

devin·swe-1.7 remains the one agent that clearly prefers `repo`; it is the agent
whose perf list was mostly the same idea five times over ("inject DispatcherProvider into
this ViewModel"), which merged down to a single entry, while its repo list found four
cross-repository couplings nobody else named.

The two openhands models are nearly prompt-neutral (+15% and +9%), which is what you'd expect
from an agent that grounds every claim in a file:line regardless of what it's asked to look
for.

## Contamination — two findings that limit what these scores mean

Both surfaced only in the third round. Neither is an accusation; both need an explanation
from whoever ran the harness before the numbers are used comparatively.

### 1. copilot·grok-4.5's perf2 list is substantially a copy of codex·sol-5.6's

Six of copilot's ten tasks match a codex task at 0.879–1.000 text similarity — multi-paragraph
prose, identical down to the "size budget: approximately 8-15 changed lines across 2 files"
and the out-of-scope wording:

| copilot perf2 | codex perf2 | similarity |
|---|---|---:|
| #3 index successful uploads | #4 | **1.000** |
| #6 snapshot watched tables | #10 | 0.999 |
| #5 reuse selection indexes | #9 | 0.985 |
| #2 remove redundant membership scan | #2 | 0.963 |
| #4 update a single notification | #8 | 0.889 |
| #1 normalize free-text answers | #1 | 0.879 |

codex pushed at 13:17:18 UTC, copilot at 13:28:59 — **12 minutes later**. Its remaining four
tasks (`VoicesViewModel` label reverse-index, `VoicesLabelManager`, `getFilterFacets`,
`refreshServerList`) are original, verified, and credited normally.

Coincidence is not a plausible explanation at 1.000 similarity on multi-paragraph text. The
live possibilities are that copilot read codex's branch (it is in the same repo, and the
prompt asks agents to check open PRs and branches), or that both were seeded with a common
draft. Either way the six copies are not evidence of copilot's own capability, so they score
zero. **If they were credited as independent, copilot would rank 2nd rather than 6th** — so
this single decision moves it four places, and you should decide it, not me.

### 2. jules carries a memory store across rounds

jules's perf2 list repeatedly justifies tasks with the phrase **"Memory states…"** — e.g.
*"Memory explicitly forbids this"*, *"Memory warns: 'When querying Room DAOs using IN
clauses…'"*, *"Memory states: 'In Android Fragments, always use
viewLifecycleOwner.lifecycleScope…'"*. Three of its ten perf2 tasks reproduce conclusions
that jules or another agent reached in **round 1** of this same benchmark:

- perf2 #3 (`commit()`→`apply()`) — already shipped from round 1.
- perf2 #6 (`response.body()?.string()` off the main thread) — this was jules's **own**
  unique round-1 find, restated verbatim.
- perf2 #10 (`viewLifecycleOwner.lifecycleScope`) — already shipped from round 1.

So jules's fourfold improvement on perf2 is partly the work-order template and partly
recall of its own earlier output. Its four genuinely new perf2 findings (`getJoinedMemberCount`,
`Locale` on `%02d`, `IN`-clause chunking, composite-ID `contains`) are strong and are credited
in full — but the round-over-round trend line for jules is not a clean capability signal.

None of the other eight agents shows cross-round recall; each round's list reads as a fresh
pass over the tree.

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

## What the third round changed about each agent

- **claude·opus-5** stayed first and produced the round's most disciplined document: it
  fetched all 42 open PR heads, diffed each against its merge-base, and published the
  126-file blocklist it then checked every task against. Its `DownloadService` task is the
  single best piece of reasoning in the whole exercise — it noticed that every `QueuedUrl` is
  built with the default `priority = 0`, therefore `maxByOrNull { it.priority }` always
  returns the sorted list's first element, therefore the entire sort-and-wrap is dead work
  computing a lexicographic minimum. No other agent reasoned about a function's *effective*
  behaviour rather than its shape.
- **codex·sol-5.6** was the round's biggest riser — last of the strong six over perf+repo,
  first on perf2 by raw points (9.40 from 10 tasks, zero dropped). Its algorithmic eye is the
  best in the field: seven of its ten tasks are genuine O(n²)→O(n) fixes with the key already
  in hand. It is also the only agent that reported honestly that it *could not* check open
  PRs (`no git remotes found`) instead of quietly claiming it had.
- **openhands·kimi-k3** found the round's best single defect — `MapTileUtils.copyAssets`
  runs on every cold start, on the main thread, against an assets directory **that does not
  exist in this repo**, so it throws into `printStackTrace()` on every launch. That is a live
  bug, not a micro-optimization, and eight other agents walked past it.
- **openhands·glm-5.2** is the most consistent agent in the benchmark: 6.37 / 6.35 / 6.28 raw
  across three different prompts. Its findings stay small but its verification never slips.
- **devin·swe-1.7** kept its `repo` bias but did well on perf2 with a coherent theme nobody
  else pursued systematically — hoisting resource lookups (`getString`, `getColor`,
  `getDrawable`) out of `onBindViewHolder` across five adapters.
- **jules** improved fourfold, with the caveat above.
- **openhands·minimax-m2.7** improved but repeated its signature failure: right lines, wrong
  conclusion. Its two `submitList(currentList.toList())` tasks propose deleting the `.toList()`
  — which would **break the refresh**, because `AsyncListDiffer` short-circuits when the
  submitted reference is identical to the current list. The `.toList()` is precisely the thing
  making those calls work.
- **qwen·coder-3.6** scored **zero on perf2** — all ten tasks cite
  `src/main/java/com/example/**.java` files (`UserService`, `ImageLoader`, `DataProcessor`,
  `LogProcessor`, `ConfigService`…). There is no `src/` directory in this repository and no
  Java source anywhere in it. This is not a wrong premise about real code; it is a task list
  written against an imagined project, with fabricated acceptance criteria ("response time
  improves by at least 30%") attached. 25 of its 30 tasks across the whole benchmark failed
  verification.

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

| Times proposed | Tasks |
|---:|---:|
| 13 | 1 |
| 6 | 2 |
| 4 | 4 |
| 3 | 6 |
| 2 | 22 |
| 1 | 92 |

92 of 127 tasks (72%) came from exactly one agent — the same ratio as at 81 tasks, held
across a third round and a third prompt. That stability is the strongest structural result
here: **agent lists overlap heavily at the top and almost not at all in the tail.** Everyone
finds the two or three headline defects; essentially all the marginal value of adding another
agent is in what only that agent sees.

Practical consequence: for a fixed review budget, running one strong agent three times on
three different prompts returns more distinct verified work than running three agents once on
the same prompt.
