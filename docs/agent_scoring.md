# Agent scoring — myPlanet refactor task lists

## Method

- **Lists:** 12 (6 agents × 2 prompts). Prompt 1 = performance quick wins, prompt 2 = repository boundaries. (Grok's two docs are titled the opposite of their list order — provenance below follows the user's list order, not the in-file titles.)
- **Raw tasks:** 120 (10 per list).
- **Dropped (premise invalid / non-actionable, verified against master `c33390b`):** 5
  1. `jules2` — COUNT query for `RequestsFragment`: `uiState.members` is already rendered by the adapter (`RequestsFragment.kt:48` `setData(uiState.members,…)`); a count query adds a DB round-trip and removes nothing.
  2. `jules2` — COUNT query for `MembersFragment`: same flaw — `state.members` feeds `membersAdapter.updateData` (L95); the size check is free on the already-loaded list.
  3. `jules2` — notification count flow: `NotificationsViewModel.kt:48` `.map { it.size }` runs on an already-materialized in-memory `StateFlow<List>`; no DB parse is avoided.
  4. `kimi1` — JsonUtils/Gson audit: self-contradictory — its own text says nothing to change (`by lazy` already SYNCHRONIZED); the only concrete edit (drop the `parseString` import) would break its one call site; no stray `Gson()` constructions exist.
  5. `grok1` — NotificationDao `userId =` → `IS` normalization: convention already followed — every `=` site takes a non-nullable `String` param; the only `IS` site takes `String?`. Nothing to normalize.
- **Partially invalid but kept (premise corrected in backlog text):**
  - `devin1` Feedback memoize — its "stale cachedMessages" bug claim is false (`setMessages` assigns `this.messages`, whose setter already nulls the cache); kept for the real memoization half.
  - `kimi2` narrow Teams interfaces — named examples stale (EnterprisesFinancesViewModel already narrowed; EnterprisesViewModel never injects TeamsRepository); premise true for other VMs (VoicesViewModel, CommunityServicesViewModel, LoginViewModel…).
  - `grok2` ChatHistoryAdapter — mostly "consider/optional" mush; kept at low rating.
  - `jules2` MainApplication.listener — claimed "static leak" is already a `WeakReference`; still shared mutable cross-page state. Kept.
  - `grok1` Personals deviceName — `DeviceNameProvider` has no plain `getDeviceName()`; task needs a provider extension, noted in text.
- **Duplicates merged (13 raw → 6 shipped):**
  - ChatSearch `ignoreCase` cleanup — claude1 + codex1 + grok2 (grok adds bucket-concat/queryParts extras)
  - SyncTimeLogger races + dead accumulator — claude1 + devin1 (devin adds `extractProcessName` split→substringAfterLast)
  - Viewer userRepository → ResourceViewerViewModel — claude2 + devin2
  - PublicSurveyActivity extraction — kimi2 (payload builder) + grok1 (full ViewModel)
  - CoursesStepsViewModel orchestration — codex2 + grok1
  - InlineResourceAdapter FS-stat collapse — devin1 + grok2
- **Shipped:** 108 merged tasks (`refactor_tasks.md`, sorted by rating).

## Rating weights

`rating = 0.40·evidence + 0.35·impact + 0.25·feasibility` (each sub-score 0–100), where
- *evidence* = verified file/line/code claims, a concrete mechanism, and a stated verification path;
- *impact* = user-visible correctness/perf/jank payoff scaled by how hot the path is;
- *feasibility* = blast radius, diff size, test surface, PR-collision exposure, hidden-dependency risk.

## Scoring rules applied

- Each shipped merged task = 1 point split among its proposers **by proposal completeness** (depth + scope coverage of that agent's write-up), not by list order:
  - ChatSearch: claude 0.40 / grok 0.35 / codex 0.25 (claude most precise evidence; grok widest scope; codex thinner)
  - SyncTimeLogger: claude 0.60 / devin 0.40 (claude also found the dead `detailedLogs`)
  - ResourceViewer: devin 0.55 / claude 0.45 (devin's signature change removes the userId threading entirely)
  - PublicSurvey: grok 0.60 / kimi 0.40 (grok extracts orchestration, kimi payload only)
  - CoursesSteps: codex 0.50 / grok 0.50
  - InlineResourceAdapter: grok 0.55 / devin 0.45
- Unique good task = 1 to its proposer; dropped task = 0.
- Normalized = points / tasks submitted (20 per agent).
- Quality-weighted = Σ (credit × task rating / 100) — rewards high-rated tasks, not just valid ones.

## Table

| agent | submitted | dropped | raw points | normalized | quality-weighted | QW normalized |
|---|---|---|---|---|---|---|
| **codex (sol 5.6)** | 20 | 0 | 18.75 | 0.938 | 12.85 | 0.642 |
| **claude (opus 5)** | 20 | 0 | 18.45 | 0.923 | 14.45 | 0.722 |
| **kimi (k3)** | 20 | 1 | 18.40 | 0.920 | 12.75 | 0.637 |
| **devin (swe 2)** | 20 | 0 (1 partial) | 18.40 | 0.920 | 13.78 | 0.689 |
| **jules (gemini 3.1 pro)** | 20 | 3 | 17.00 | 0.850 | 9.75 | 0.488 |
| **grok (4.5)** | 20 | 1 (1 weak) | 17.00 | 0.850 | 11.72 | 0.586 |

## Point-sum check

115 valid raw tasks − 7 absorbed by merges (3→1 saves 2; five 2→1 save 1 each) = **108 shipped = 108.0 total points distributed** ✓

## Things I had to guess

- "Award duplicate credit by completeness" — I read it as: the 1 point a duplicated task is worth gets split among proposers proportionally to how complete each write-up was. If you meant "fractional share (1/n) only for proposals complete enough to count," the numbers shift only inside the six merges.
- Rating sub-scores are calibrated judgment, not measured (no benchmarks run).
- Grok's link text for list 1 pointed at the `32cb561a` branch while its href pointed at `d509e0a6`; I used the href (`docs/refactor-tasks.md`). The `32cb561a` branch's `docs/task.md` is what supplied grok list 2 anyway.
- `jules1` used a 2024-05-19 date header but its code claims all verified against today's master — treated as valid.
- "120+ raw tasks" — landed exactly at 120, inside "120+".
