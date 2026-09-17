# Agent scoring

## Method

Each raw task was checked against the current working tree. A retained unique task earns **1 point**; a merged duplicate contributes a shared total of **1 point**. Duplicate shares were assigned by completeness (specificity, evidence, constraints, and test coverage), not list order. A rejected task earns **0**. The normalized score is points divided by 20 submitted tasks. The quality-weighted score multiplies each task credit by its merged backlog rating divided by 100, then normalizes by 20.

Ratings use **35% evidence quality, 40% expected impact, and 25% risk-adjusted feasibility**. Evidence covers whether the named code and claimed issue actually exist; impact covers user/runtime and architectural payoff; feasibility discounts behavioral, concurrency, migration, and scope risk.

## Scores

| Agent | Submitted | Rejected | Good-task points | Normalized | Quality-weighted points | Quality-weighted / submitted |
|---|---:|---:|---:|---:|---:|---:|
| Devin / SWE 2 | 20 | 0 | 18.75 | 93.75% | 12.724 | 63.62% |
| Copilot / Grok 4.5 | 20 | 0 | 18.50 | 92.50% | 13.252 | 66.26% |
| Codex / GPT-5.6 Sol | 20 | 0 | 18.15 | 90.75% | 12.742 | 63.71% |
| Claude / Opus 5 | 20 | 1 | 17.20 | 86.00% | 12.743 | 63.72% |
| Jules / Gemini 3.1 Pro | 20 | 4 | 16.00 | 80.00% | 11.300 | 56.50% |
| Copilot / Kimi K3 | 20 | 4 | 15.40 | 77.00% | 10.850 | 54.25% |

## Rejected submissions

- **Claude / Opus 5 — Performance task 7, “hoist constant string lookups out of adapter bind paths (roadmap 7)”:** Caching localized strings in adapter fields can retain stale text after a runtime locale/configuration change; the micro-saving does not justify the correctness risk.
- **Jules / Gemini 3.1 Pro — Performance task 2, “refactor applyFiltersAndUpdateUI to cache filteredList.size (roadmap 7)”:** `List.size` is already constant-time; caching it does not remove traversal or meaningful work.
- **Jules / Gemini 3.1 Pro — Performance task 6, “optimize passed steps collection overhead (roadmap 7)”:** The `HashSet` enforces distinct step numbers; the suggested `distinct()` replacement still allocates and is not an optimization.
- **Jules / Gemini 3.1 Pro — Performance task 9, “remove redundant size call in Dashboard courses check (roadmap 7)”:** Both `size` and `isEmpty()` are constant-time on the list; the claimed redundant traversal does not exist.
- **Jules / Gemini 3.1 Pro — Performance task 10, “extract member size call in MembersFragment (roadmap 7)”:** `state.members.size` is constant-time, so the claimed repeated traversal/getter cost does not exist.
- **Copilot / Kimi K3 — Performance task 6, “Remove unused `Gson` import path in `JsonUtils` fallback”:** No actionable defect was identified: `JsonUtils.gson` is used broadly and `parseString` is a valid static import; the proposal says “confirm” rather than specifying a verified change.
- **Copilot / Kimi K3 — Performance task 8, “Replace `printStackTrace()` in `News` model accessors”:** The proposed `android.util.Log` dependency would increase Android coupling in the model and contradicts the task’s platform-free rationale.
- **Copilot / Kimi K3 — Performance task 9, “Batch the storage scan into a single pass in `StorageBreakdownViewModel`”:** `scanStorage` already performs one lazy `walkTopDown()` pass; removing one sequence operator does not substantiate the claimed “batch into a single pass” improvement.
- **Copilot / Kimi K3 — Repository boundaries task 10, “`TeamsFinancesRepository`/`TeamsMembersRepository`/`TeamsNotificationsRepository`: give the split interfaces real consumers”:** The premise that no DI work is needed is false: binding `TeamsRepository` does not automatically provide each parent interface, and the work order conditionally defers its own required binding.

## Duplicate-credit audit

- **Make `SyncTimeLogger` concurrency-safe and eliminate repeated/unused summary work (86/100):** Claude / Opus 5 0.40; Codex / GPT-5.6 Sol 0.30; Devin / SWE 2 0.30; total **1.00**.
- **Remove redundant case folding and allocations from normalized chat search (78/100):** Claude / Opus 5 0.25; Codex / GPT-5.6 Sol 0.40; Copilot / Grok 4.5 0.35; total **1.00**.
- **Resolve the current user behind `ResourceViewerViewModel` (88/100):** Claude / Opus 5 0.55; Devin / SWE 2 0.45; total **1.00**.
- **Make calendar meetup loading distinct, allocation-light, and single-owner (80/100):** Copilot / Kimi K3 0.40; Copilot / Grok 4.5 0.60; total **1.00**.
- **Put course-step resource prefetch behind a repository-backed coordinator (87/100):** Copilot / Grok 4.5 0.55; Codex / GPT-5.6 Sol 0.45; total **1.00**.

## Count and point-sum checks

- **Lists / raw tasks / shipped merged tasks:** 12 / 120 / 104.
- **Rejected raw tasks:** 9; **retained raw proposals:** 111; **duplicate collapse:** 7 proposals.
- **Point-sum:** 17.20 + 18.15 + 18.50 + 15.40 + 18.75 + 16.00 = **104.00**, matching **104 shipped tasks**.
- **Guesses:** “shipped” is interpreted as retained, verified backlog entries (not implemented code); the two prompt families are labeled Performance and Repository boundaries according to their position in the request, even where a linked document title described the opposite kind of round. The malformed final URL was interpreted as the linked `.md` destination, and the Grok performance URL was interpreted as the visible linked destination rather than the obsolete URL text.
