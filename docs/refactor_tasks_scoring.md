# Agent scoring: myPlanet refactor round (2026-10-09 plans, checked 2026-10-16)

Rules: a merged task carries **1 point**. A unique good task gives its proposer 1; a duplicated good task splits its point by completeness (shares shown in the backlog), and list order plays no part. Dropped tasks score 0. Quality-weighted = Σ share × rating/100.

## By agent

| agent | runs (lists) | submitted | unique shipped | shared shipped | dropped | points | points / submitted | quality-weighted | QW / submitted |
|---|---|---|---|---|---|---|---|---|---|
| claude | 4 (05, 09, 10, 12) | 40 | 29 | 12 | 0 | 35.10 | 0.878 | 16.99 | 0.425 |
| copilot | 4 (02, 03, 07, 08) | 40 | 18 | 10 | 12 | 22.45 | 0.561 | 8.50 | 0.213 |
| devin | 3 (01, 06, 11) | 30 | 19 | 6 | 5 | 21.55 | 0.718 | 7.67 | 0.256 |
| codex | 1 (04) | 10 | 8 | 2 | 0 | 8.90 | 0.890 | 4.11 | 0.411 |
| **total** | 12 | 120 | | | 17 | **88.00** | | 37.28 | |

## By run (list)

| list | prompt | agent | branch | points | quality-weighted | mean rating of shipped share | dropped |
|---|---|---|---|---|---|---|---|
| 01 | test-speed | devin | `devin/1791538043-test-workflow-tasks` | 7.15 | 2.73 | 38 | 0 |
| 02 | test-speed | copilot | `copilot/myplanet-refactor-round-8aa5978e-…` | 6.90 | 3.76 | 55 | 0 |
| 03 | test-speed | copilot | `copilot/myplanet-refactor-round-1c95aa7d-…` | 3.15 | 1.02 | 32 | 5 |
| 04 | test-speed | codex | `codex/test-workflow-plan-2026-10-09` | 8.90 | 4.11 | 46 | 0 |
| 05 | test-speed | claude | `claude/trusting-bohr-ksiybg` | 6.90 | 3.67 | 53 | 0 |
| 06 | perf | devin | `devin/1791540736-refactor-tasks-round` | 6.00 | 1.85 | 31 | 4 |
| 07 | perf | copilot | `copilot/myplanet-refactor-round-ed4179f2-…` | 8.40 | 2.50 | 30 | 1 |
| 08 | perf | copilot | `copilot/finish-cleaning-data-layer` | 4.00 | 1.22 | 30 | 6 |
| 09 | perf | claude | `claude/performance-work-orders-f32198b4` | 8.95 | 3.64 | 41 | 0 |
| 10 | perf | claude | `claude/eloquent-pascal-0vgn4e` | 9.25 | 5.84 | 63 | 0 |
| 11 | repo | devin | `devin/1791540798-refactor-tasks-round` | 8.40 | 3.09 | 37 | 1 |
| 12 | repo | claude | `claude/myplanet-repository-work-orders-…` | 10.00 | 3.84 | 38 | 0 |

## By prompt

| prompt | lists | raw | shipped merged tasks | dropped raw |
|---|---|---|---|---|
| test-speed | 5 | 50 | 33 | 5 |
| perf | 5 | 50 | 37 | 11 |
| repo | 2 | 20 | 18 | 1 |

Cross-prompt merge: P5 combines list 10 (perf) with list 11 (repo) and is counted under perf.

## Per-task provenance

| task | rating | contributors (share) |
|---|---|---|
| P1 | 79 | claude (list 10 #1, 1.00) |
| P2 | 75 | claude (list 10 #3, 1.00) |
| T1 | 74 | copilot (list 02 #5, 1.00) |
| T2 | 68 | claude (list 05 #1, 0.60), devin (list 01 #7, 0.40) |
| P3 | 65 | claude (list 10 #4, 1.00) |
| P5 | 65 | claude (list 10 #7, 0.60), devin (list 11 #1, 0.40) |
| P8 | 65 | claude (list 09 #1, 1.00) |
| P7 | 64 | claude (list 09 #3, 1.00) |
| R1 | 64 | claude (list 12 #1, 1.00) |
| T3 | 64 | copilot (list 02 #2, 0.55), claude (list 05 #2, 0.45) |
| P4 | 63 | claude (list 10 #6, 0.65), claude (list 09 #5, 0.35) |
| T4 | 61 | claude (list 05 #3, 0.55), copilot (list 02 #3, 0.45) |
| P6 | 60 | claude (list 10 #5, 1.00) |
| P9 | 60 | claude (list 10 #10, 1.00) |
| R2 | 60 | claude (list 12 #3, 1.00) |
| T11 | 59 | copilot (list 02 #1, 0.60), claude (list 05 #6, 0.40) |
| T33 | 59 | claude (list 05 #10, 1.00) |
| T5 | 59 | copilot (list 02 #4, 0.50), claude (list 05 #4, 0.50) |
| P10 | 57 | claude (list 10 #2, 1.00) |
| P11 | 56 | claude (list 10 #9, 1.00) |
| T6 | 55 | claude (list 05 #5, 0.60), copilot (list 02 #6, 0.40) |
| P15 | 53 | claude (list 09 #4, 0.60), copilot (list 07 #3, 0.40) |
| T14 | 53 | codex (list 04 #3, 0.60), devin (list 01 #3, 0.40) |
| P12 | 52 | claude (list 09 #2, 1.00) |
| P13 | 52 | claude (list 10 #8, 1.00) |
| P14 | 52 | copilot (list 07 #9, 1.00) |
| T15 | 51 | codex (list 04 #2, 1.00) |
| T20 | 51 | codex (list 04 #8, 1.00) |
| T7 | 49 | claude (list 05 #7, 0.60), copilot (list 02 #7, 0.40) |
| T10 | 48 | codex (list 04 #1, 0.30), devin (list 01 #1, 0.30), copilot (list 03 #3, 0.20), claude (list 05 #6, 0.20) |
| T16 | 47 | codex (list 04 #4, 1.00) |
| T17 | 47 | codex (list 04 #5, 1.00) |
| T18 | 47 | codex (list 04 #6, 1.00) |
| T19 | 47 | codex (list 04 #7, 1.00) |
| R3 | 46 | devin (list 11 #3, 1.00) |
| T12 | 46 | copilot (list 02 #10, 1.00) |
| T23 | 46 | devin (list 01 #2, 1.00) |
| T8 | 46 | copilot (list 02 #8, 1.00) |
| P17 | 44 | claude (list 09 #6, 1.00) |
| P18 | 44 | copilot (list 07 #7, 1.00) |
| P19 | 44 | copilot (list 08 #3, 1.00) |
| R5 | 44 | devin (list 11 #7, 1.00) |
| T9 | 44 | claude (list 05 #9, 1.00) |
| R4 | 43 | devin (list 11 #10, 1.00) |
| T13 | 41 | copilot (list 02 #9, 1.00) |
| T24 | 41 | devin (list 01 #4, 1.00) |
| T28 | 41 | devin (list 01 #8, 0.55), copilot (list 03 #10, 0.45) |
| P16 | 40 | devin (list 06 #5, 1.00) |
| T22 | 40 | codex (list 04 #10, 1.00) |
| P20 | 37 | copilot (list 08 #9, 1.00) |
| R6 | 36 | devin (list 11 #9, 1.00) |
| R8 | 36 | claude (list 12 #2, 1.00) |
| T29 | 36 | claude (list 05 #8, 1.00) |
| R7 | 35 | devin (list 11 #2, 1.00) |
| T21 | 35 | codex (list 04 #9, 1.00) |
| P21 | 34 | copilot (list 07 #8, 1.00) |
| P22 | 34 | devin (list 06 #6, 1.00) |
| R10 | 34 | claude (list 12 #6, 1.00) |
| R11 | 34 | claude (list 12 #7, 1.00) |
| R12 | 34 | claude (list 12 #8, 1.00) |
| R13 | 34 | claude (list 12 #9, 1.00) |
| R14 | 34 | claude (list 12 #10, 1.00) |
| T30 | 34 | devin (list 01 #9, 1.00) |
| T25 | 32 | copilot (list 03 #4, 0.50), devin (list 01 #5, 0.50) |
| T32 | 32 | copilot (list 03 #9, 1.00) |
| P24 | 31 | devin (list 06 #8, 1.00) |
| R9 | 31 | claude (list 12 #5, 1.00) |
| T27 | 31 | devin (list 01 #6, 1.00) |
| P25 | 30 | devin (list 06 #9, 1.00) |
| R15 | 30 | devin (list 11 #5, 1.00) |
| P23 | 29 | devin (list 06 #7, 1.00) |
| R16 | 28 | devin (list 11 #8, 1.00) |
| T26 | 26 | copilot (list 03 #5, 1.00) |
| P27 | 25 | copilot (list 07 #2, 1.00) |
| P35 | 24 | claude (list 09 #8, 1.00) |
| P36 | 24 | claude (list 09 #9, 1.00) |
| P33 | 23 | copilot (list 08 #8, 1.00) |
| R18 | 23 | claude (list 12 #4, 1.00) |
| P26 | 21 | devin (list 06 #10, 1.00) |
| P28 | 21 | copilot (list 07 #5, 1.00) |
| P37 | 21 | claude (list 09 #10, 1.00) |
| R17 | 21 | devin (list 11 #6, 1.00) |
| P29 | 20 | copilot (list 07 #4, 1.00) |
| T31 | 20 | devin (list 01 #10, 1.00) |
| P30 | 18 | copilot (list 07 #6, 1.00) |
| P32 | 18 | copilot (list 08 #5, 1.00) |
| P34 | 16 | claude (list 09 #7, 1.00) |
| P31 | 15 | copilot (list 07 #10, 1.00) |

## Dropped (premise failed against master)

| raw task(s) | proposed | why dropped |
|---|---|---|
| 03.1, 03.6, 03.7, 03.8 (copilot) | runBlocking → runTest across 17 DAO/round-trip test classes (4 batches) | The speed premise fails. None of the 17 files contains a `delay(`, so virtual time saves nothing, and Room suspend DAOs run on Room's own executors either way. The per-file `runBlocking` counts are also off by one (e.g. CourseDaoTest has 6, not 7). |
| 03.2 (copilot) | Delete LifeRepositoryTest as duplicate coverage | Not a duplicate. The file has 3 tests, not 2, and `updateMyLifeListOrder_itemNotInList_weightNotUpdated` has no counterpart in LifeRepositoryImplTest. |
| 05.6 (third part only) (claude) | Delete `RealtimeSyncManagerTest.testNotifyWithoutCollectorsDoesNotThrow` | `assertTrue(true)` after the call is a valid 'does not throw' contract that costs milliseconds; there is nothing to gain. 05.6's other two deletions ship as T10/T11. |
| 06.1 (devin) | Debounce the `exams` realtime collector in SurveysViewModel | Nothing ever emits `exams`. `RealtimeSyncManager.notifyTableUpdated` is called only with `"courses"` (CoursesRepositoryImpl :253/:370/:431), so there is no burst to debounce. (HealthViewModel's debounce is 200 ms, not the 500 ms cited.) |
| 06.2 (devin) | Debounce the `teams` collector in TeamDetailFragment | Same as 06.1: `teams` is never emitted. |
| 06.3 (devin) | Debounce the `chats` refresh signal in ChatViewModel | Same as 06.1: `chats` is never emitted. The real chat waste is the replayed signal on every onStart, which ships as P3 (list 10). |
| 06.4 (devin) | Annotate the ~37 DAO providers in RoomModule with `@Singleton` | Room's generated `AppDatabase_Impl` already memoises each DAO instance, so `database.xxxDao()` returns the same object every time. Nothing is allocated per injection. |
| 07.1 (copilot) | ResourcesCardBinder: bounded cover decode + no double stat | Glide `.into(ImageView)` already decodes at the target view's size (`iv_cover_preview` is match_parent in a fixed card), and the PDF branch never calls `cachedFileLength` first, so there is no second stat to remove. |
| 08.1 (copilot) | Shared bounded LRU date cache for 4–5 adapters | The caches are bounded by list size and live as long as the adapter. The adapters use *different* formatters (`getFormattedDate` vs `TimeUtils.formatDate`), so a shared Long-keyed cache would return the wrong format. MembersAdapter has no date cache. |
| 08.2 (copilot) | RequestsAdapter: remove the 'double dispatch' `notifyItemRangeChanged` | The range change fires only when capacity or leader status changes. Those are adapter-level state that every row renders, so it is required, not redundant. |
| 08.4 (copilot) | TeamsSelectionAdapter/ChatShareTargetAdapter: move per-bind `getString` off bind | The label is already cached (`cachedTeamsLabel`, TeamsSelectionAdapter :24/:43), and ChatShareTargetAdapter has no `getString` in bind. |
| 08.6 (copilot) | NotificationsAdapter: per-bind Locale + duplicate Html parse | `parsedHtmlCache` is already keyed by the raw text content (:159-166), so identical notifications don't re-parse, and `Locale.getDefault()` is a field read behind an existing guard. |
| 08.7 (copilot) | PersonalsAdapter: precompute ResourceType, add a missing `else` | A Kotlin `when` statement without `else` is already a no-op, and an Intent has to be built per click anyway. This is a cosmetic refactor framed as a performance fix. |
| 08.10 (claude, copilot) | MembersAdapter: replace post-submit `notifyItemRangeChanged` with diff payloads | Leader status is adapter-level state that the item diff can't see, so the range payload is the correct mechanism. The second half (`getColor` per dialog row) is negligible. |
| 11.4 (devin) | Push the guest-author filter for news uploads into NewsDao SQL | Master changed this after the plans were written: `getNewsForUpload` now builds `byId = allNews.associateBy{…}` to resolve reply parents (VoicesRepositoryImpl :39-45). A SQL guest filter would drop guest parents from that map and change reply handling, so 'semantics identical' no longer holds. |

## Counts and checks

- lists: 12 (expected 12) ✔
- raw tasks: 120 (10 per list; expected 120+) ✔
- raw tasks fully dropped: 17; 05.6 was dropped only in part (one of its three deletions)
- raw tasks shipped (contributing to ≥1 merged task): 103
- merged tasks shipped: 88 (14 of them merged from ≥2 raw tasks)
- point-sum check: Σ agent points = 88.00 = shipped merged tasks 88 ✔
- accounted check: every one of the 120 raw IDs is either credited or dropped ✔
