# Agent scoring — 18 lists / 180 raw tasks

Scoring: unique good = 1, duplicated good = 1/n (n = # agents proposing the merged task), bad = 0. Normalized = score / raw tasks submitted (30 each). Quality-weighted = credit x merged-task rating / 100.

| agent | raw | shipped | bad | dup-credit score | normalized | quality-weighted |
|---|---|---|---|---|---|---|
| devin (SWE-2) | 30 | 30 | 0 | 23.00 | 76.7% | 17.36 |
| codex (sol 5.6) | 30 | 30 | 0 | 21.92 | 73.1% | 16.74 |
| claude (opus 5.5) | 30 | 29 | 1 | 25.25 | 84.2% | 18.35 |
| gemini (3.8 flash) | 30 | 30 | 0 | 22.00 | 73.3% | 15.78 |
| kimi (k3) | 30 | 28 | 2 | 23.25 | 77.5% | 16.04 |
| grok (4.6) | 30 | 30 | 0 | 20.92 | 69.7% | 15.16 |

**Point-sum check:** raw tasks 180 = shipped 177 + dropped 3. Merged tasks: 115. Σ duplicate-credit = 136.33 — a merged task yields >1.0 total when one agent proposed it via multiple raw tasks (e.g. devin's 6-adapter sweep = 6 x 1/2). Σ quality-weighted = 99.43.

## Dropped tasks (premise failed verification)

- **repo_kimi#10** (kimi (k3)) — SyncConfigurationCoordinator failure-branch tests: SyncConfigurationCoordinatorTest.kt already exists with failure coverage (`failure reports sync failed`, `clear data` branches at :55,:121,:155)
- **repo_claude#7** (claude (opus 5.5)) — UserLookupRepository for DiagnosticsRepositoryImpl.findByUserId / FeedbackListViewModel.findActiveUserById: neither method has callers outside the repository layer (grep-verified) — the narrow interface would serve nothing
- **repo_kimi#5** (kimi (k3)) — Dictionary dead-method pruning: DictionaryRepository has exactly 3 methods (count/findByWord/insertDictionaryData), all called by DictionaryViewModel — nothing dead

## Verification caveats (kept, premise weakened)

- repo_gemini#3 — no `observeAll` exists on MyLifeDao; reframed as 'add Flow observation' (verified: suspend-only DAO + manual-refresh VM).
- repo_gemini#2 — names `saveReport`; actual method is `updateReport`. Same fix as repo_codex#2/repo_claude#3; merged into it.
- repo_gemini + data_gemini — their open-PR exclusion tables contain PR numbers/titles that don't match the shared listing the other 4 agents used (e.g. fabricated titles for #17812, #14650); evidence quality of their PR-collision claims is low.
- perf_gemini#3 — 'unbounded shelf parallelism' element dropped: a Semaphore(8) already gates inner batches at SyncRepositoryImpl.kt:221; outer map is bounded by ~9 shelf types.
- perf_gemini#9 — 'numeric fast-path' element stale: getInt/getLong/getFloat already call longOrNull/floatOrNull first.
- perf_kimi#6 — LifeAdapter already has a drawableCache; the getIdentifier cost is a cached lookup, not a per-bind resource scan.
- data_kimi#3/#4 — stated premise 'models construct their own Gson' is false (models already use the shared GsonUtils.gson); reframed as 'remove the global dependency'.
- data_kimi#8 — NetworkUtils caches are already lazy via ResettableCache; reduced to an audit task.
- data_kimi#10 — TimeUtils already caches formatters in a ConcurrentHashMap; audit-only.
- repo_devin#6 — 'dedupe resolveType' element stale: resolveType is already a single override (:413). Batch-lookup element kept.
- data_claude#10 / data_kimi#7 — warm-up flag + --warn-over already exist in test_timing_summary.py; merged into the sorted-table task.
- repo_kimi#7 — DictionaryActivity has no direct DAO access (VM-only); folded into repo_gemini#1's download-in-activity task.
- kimi alternates (repo #3b, #4b, #8b) counted inside their primary task slots: raw total stays 180.
