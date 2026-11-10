# Verified backlog scoring and provenance

Verification target: the supplied clean working tree at **`3d02e24a44e36d8a08b26f83cfd60bb95df569b1`**, dated 2026-11-06. The input lists generally cite the newer `2093a6224b221079f1413e2ab1f22cebec8cf736`; that branch was not substituted for the working tree. “Shipped” means delivered backlog work orders, not implemented application changes.

The count gate passed: **18 lists, 10 numbered work orders each, 180 raw submissions**. Intros, execution/merge plans, conflict tables, self-check/DoD checklists, CI findings explicitly not scheduled, and deferred/backlog prose were excluded. Internal steps were not counted as additional raw submissions. After the user authorized continuation despite signature drift, the six repeating benchmark streams were kept as below. The two Copilot models remain separate streams; Antigravity model changes and missing signatures are preserved, not silently repaired.

## Rating and credit rules

Every retained scope was checked with repository searches and source/caller/DAO reads before computing its rating. Verification citations below point to the exact working-tree commit and include source excerpts. SQL plan checks additionally used Python SQLite 3.53.1 with minimal tables and the indexes declared in the checked-in entities; these are counterexamples to blanket SQL claims, not measurements of an Android device. No CI timing numbers or speedup claims were accepted solely from an agent. The application was not modified or built; this is a static backlog audit, not a claim that a proposed implementation already passes tests.

Scores use a weighted multiplicative model, rounded once to the nearest integer: **R = 100 × (E/100)^0.40 × (I/100)^0.35 × (F/100)^0.25**. Weights: **evidence quality 40%, impact 35%, risk-adjusted feasibility 25%**. E rates evidence for the final verified scope, I estimates benefit, and F discounts implementation/behavior risks. The weights sum to 1. Bad/unshipped scope receives zero agent credit; it is not assigned a manufactured positive rating.

| Dimension | Interpretation |
| --- | --- |
| E | 90–100: directly checked implementation/caller contract; 70–89: real premise with material scope correction or a weaker causal claim. Code presence does not establish measured speed. |
| I | 90–100: data ownership or critical work completion; 75–89: broad sync/cancellation/UI correctness; 50–74: frequent path cost or meaningful boundaries; 25–49: local allocations/maintainability; 10–24: narrow cosmetic or very small cleanup. |
| F | 90–100: localized change using existing contracts; 70–89: several contracts, concurrency or state isolation; below 70: caching/invalidation, reporting, or coordination design remains material. Higher is safer/more feasible. |

Completeness is measured by the explicitly enumerated substantive scopes of each merged work order, not by prose length or source order. For a task t with k scopes, let A(t,s) be the distinct benchmark streams that actually proposed a valid solution for scope s. **credit(a,t) = (1/k) × Σs [a ∈ A(t,s)] / |A(t,s)|**. A unique complete good task earns 1; n equally complete duplicates earn 1/n each; a bad task earns 0. A complete proposal covering both scopes beats a duplicate covering only one: with those two proposers it earns 3/4 versus 1/4. All proposers remain in provenance even when only part of their proposal survives. A repeated proposal by the same stream does not create another vote. For the per-submission ledger, its stream scope credit is divided equally among that stream submissions covering the same scope.

Work orders are merged when they address the same outcome with overlapping scopes. A broader overlapping proposal can absorb narrower ones; its additional verified scope is retained. Distinct changes are not merged merely because they touch the same file: the two quota optimizations remain separate. Bundled submissions can map to more than one final work order, so points/submitted is a deduplicated output-yield measure, not a probability. The retained-submission fraction is also supplied separately. Scope granularity and equal scope weights are editorial choices, disclosed here for reproducibility.

Quality-weighted points are **Σt credit(a,t) × R(t)/100**. Both base and quality-weighted points are normalized by the **30 original submissions** for each stream, including dropped ones. “Exclusive tasks” are final tasks for which only that stream proposed any shipped scope; “shared credit” is its remaining fractional task credit.

## Agent scoring table

| ID | Benchmark stream | Submitted | Retained raw | Dropped raw | Retained/submitted | Exclusive tasks | Shared credit | Points | Points/submitted | Quality points | Quality/submitted |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E | Claude Code | 30 | 30 | 0 | 100.00% | 20 | 5.513889 | 25.513889 | 0.850463 | 20.038167 | 0.667939 |
| F | Antigravity | 30 | 30 | 0 | 100.00% | 18 | 7.141667 | 25.141667 | 0.838056 | 18.892833 | 0.629761 |
| B | Copilot / Grok | 30 | 27 | 3 | 90.00% | 17 | 4.475000 | 21.475000 | 0.715833 | 16.081167 | 0.536039 |
| A | Devin | 30 | 27 | 3 | 90.00% | 17 | 3.680556 | 20.680556 | 0.689352 | 13.239000 | 0.441300 |
| C | Copilot / Kimi | 30 | 28 | 2 | 93.33% | 15 | 4.658333 | 19.658333 | 0.655278 | 13.691833 | 0.456394 |
| D | Codex | 30 | 30 | 0 | 100.00% | 15 | 3.530556 | 18.530556 | 0.617685 | 14.847000 | 0.494900 |

Exact base totals: A = **1489/72**; B = **859/40**; C = **2359/120**; D = **6671/360**; E = **1837/72**; F = **3017/120**. Decimal columns are rounded for display; checks use exact rational arithmetic.

## Source inventory and exact signatures

| List / original document | Stream | Exact final signature line | Raw | Retained | Allocated points | Quality points | Fetched content SHA-256 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [L01](https://github.com/open-learning-exchange/myplanet/blob/devin/1794301446-perf-quick-win-tasks/docs/refactor_tasks.md) | A | signed: devin · devin (cognition ai) · normal effort | 10 | 9 | 9.000000 | 5.400000 | `5e305e48a7e11363c65556997fab44fa35ecfa162ecb64619b451780be46d460` |
| [L02](https://github.com/open-learning-exchange/myplanet/blob/docs/performance-quick-wins-2026-11-10/docs/performance-quick-wins.md) | B | copilot grok-4.7 high | 10 | 9 | 7.291667 | 5.595000 | `09b5f17fa6281b1015792cbaaa1edcedb4241b315dd0d2fee4dd4248232a663f` |
| [L03](https://github.com/open-learning-exchange/myplanet/blob/docs/perf-quick-win-tasks-2026-11-10/docs/tasks/2026-11-10-performance-quick-wins.md) | C | signed: github copilot · kimi k3 · high effort | 10 | 10 | 7.025000 | 4.974083 | `5d087e61b3bd86ffa831e86b3a9bfd98c0740b685b7c7affc8ae7e4f4edca99a` |
| [L04](https://github.com/open-learning-exchange/myplanet/blob/claude/myplanet-perf-work-orders-20261110-1a5ff50be590/docs/refactor-performance-work-orders-2026-11-10.md) | D | Signed: Codex · GPT-6 · high effort. | 10 | 10 | 8.250000 | 6.500000 | `c48c4899251040c857a41ce3568327c6b4ea5017c244efc3ec834a1f84b580aa` |
| [L05](https://github.com/open-learning-exchange/myplanet/blob/claude/funny-cannon-f5xgx9/docs/REFACTOR_TASKS_2026-11-10.md) | E | — claude code · claude opus 5.5 · low | 10 | 10 | 9.500000 | 7.985000 | `8d50e31e8e96cef94104c8417616bc9a5fa420ce2fdc67925a3e065a60e71857` |
| [L06](https://github.com/open-learning-exchange/myplanet/blob/docs/performance-quick-wins-work-orders-2026-11-10/docs/PERFORMANCE_QUICK_WINS_WORK_ORDERS_2026-11-10.md) | F | Signed: antigravity claude opus 4.6 high | 10 | 10 | 8.541667 | 6.235000 | `c3192d402df1f02119ebc0a0d5688e339738a59a825c15631201a5157f0561e7` |
| [L07](https://github.com/open-learning-exchange/myplanet/blob/devin/1794302290-repository-boundary-tasks/docs/TASKS_REPOSITORY_BOUNDARIES.md) | A | signed: devin · cognition devin (opus-class) · high effort | 10 | 8 | 5.488889 | 3.733667 | `6fbf4830372e995941c95bf2bb8ce9af1ef01cba6839678d5aa2a4e80d5e5917` |
| [L08](https://github.com/open-learning-exchange/myplanet/blob/repository-boundary-work-orders/docs/repository-boundary-work-orders.md) | B | Signed: github-copilot grok-4.7 high | 10 | 8 | 5.933333 | 4.157000 | `8127359a49a83141737513738c388c23006a31b6eee6b96a4096b5deb56d789d` |
| [L09](https://github.com/open-learning-exchange/myplanet/blob/copilot/myplanet-refactor-task-round-2026-11-10/docs/myplanet-refactor-task-round-2026-11-10.md) | C | signed: github copilot · kimi k3 · high | 10 | 9 | 9.000000 | 5.610000 | `980d94cfe2c2c9a4771465da2301653ad6fc3ac33de964cd16f4142ddecf5ba6` |
| [L10](https://github.com/open-learning-exchange/myplanet/blob/codex/repository-boundary-work-orders-2026-11-10-d02853dd0/docs/repository-boundary-work-orders-2026-11-10.md) | D | Signed: OpenAI Codex · GPT-6 · high effort. | 10 | 10 | 3.988889 | 3.568667 | `50140ca05188121c6e536a8b0795a6d00ad3bfcc8e9abcbb433061b4f9f4965d` |
| [L11](https://github.com/open-learning-exchange/myplanet/blob/claude/affectionate-feynman-4zgzq7/docs/REFACTOR_TASKS_2026-11-10.md) | E | Signed: claude code · opus 5.5 · low | 10 | 10 | 7.322222 | 5.078667 | `3537a7f4da37c8aa41d2bba2942932ac3812cce83b0398f730549c51afbec63b` |
| [L12](https://github.com/open-learning-exchange/myplanet/blob/docs/repository-boundaries-work-orders/docs/REPOSITORY_BOUNDARIES_WORK_ORDERS.md) | F | Signed: antigravity gemini 3.1 pro high | 10 | 10 | 6.266667 | 4.764500 | `01d43292e54849d2c3660b05d1b03ad30b1de07e25752cafbaaca8b119b8c941` |
| [L13](https://github.com/open-learning-exchange/myplanet/blob/devin/1794305946-refactor-round-work-orders/docs/refactor-round-work-orders-2026-11-10.md) | A | *generated by devin · cognition ai · normal effort* | 10 | 10 | 6.191667 | 4.105333 | `fcf5f6213bfa48a24998ca5eeda37f475064f9b4ebb34549f5f94d380eda586b` |
| [L14](https://github.com/open-learning-exchange/myplanet/blob/docs/myplanet-refactor-work-orders-2026-11-10/docs/refactor-work-orders-2026-11-10.md) | B | copilot grok-4.7 high | 10 | 10 | 8.250000 | 6.329167 | `8a2560714f9d05cd980cda84c5c45986c954a558118aa1fd5bd25c1231f1a98e` |
| [L15](https://github.com/open-learning-exchange/myplanet/blob/copilot/refactor-round-work-orders/docs/REFACTOR_ROUND_TASKS.md) | C | signed: copilot kimi k3 high | 10 | 9 | 3.633333 | 3.107750 | `d3c31578a039c9191d7b805697d3a0ebacb965c812d07348d62ad7aaa112a172` |
| [L16](https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-work-orders-2026-11-10-c620822f/docs/refactor-work-orders-2026-11-10.md) | D | Signed: OpenAI Codex · GPT-6 · high effort. | 10 | 10 | 6.291667 | 4.778333 | `722d0a6ba44335830a93ac6313ab095c347538f1b8442e01946831819c5224c8` |
| [L17](https://github.com/open-learning-exchange/myplanet/blob/claude/adoring-davinci-nrjvdj/docs/refactor-round-2026-11-10.md) | E | — signed: Claude Code · effort low | 10 | 10 | 8.691667 | 6.974500 | `12756e8a40a2c921500f6d089bd7747e5f3b706c8146e35f763bf0db2fbc8b0f` |
| [L18](https://github.com/open-learning-exchange/myplanet/blob/antiworkflow/docs/plan.md) | F | antigravity claude opus 4.6 high | 10 | 10 | 10.333333 | 7.893333 | `238dad017f5e4b78180b8a089ab409a4235d7d29ffb31fe06d8ecc1b34acfcbb` |

List 12 explicitly signs Gemini 3.1 Pro; lists 6 and 18 sign Claude Opus 4.6. List 17 omits its model. Devin effort/model wording varies. These are preserved as source facts. Grouping by the six repeating list positions, while retaining exact signatures, is the interpretation of the user continuation approval.

## Task ratings and complete provenance

The full executable backlog is in [refactor_tasks.md](refactor_tasks.md), sorted by descending R, then I, then E, then stable key. Each row below identifies every originating raw submission, stream credit, verified scopes, and evidence. The task ID is the join key between outputs. Supporting/read-only files in Scope are evidence locations, not a promise that every listed file must be edited.

<a id="t001"></a>

### T001 — Propagate cancellation from submission payload assembly

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **95/100** | 99 / 90 / 95 | A Devin, C Copilot / Kimi, D Codex | A=1/4; C=1/2; D=1/4 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Rethrow CancellationException from submissionPayload before ordinary payload-error fallback; mirror the existing team-lookup helper. | L10.04, L15.08 | C, D | 1/4 each | [SubmissionsPayloadBuilder.kt:110][V001]; [SubmissionsPayloadBuilder.kt:162][V002] |
| 2 | Replace payload-build printStackTrace with a controlled diagnostic while retaining the existing ordinary-error fallback contract. | L13.01, L15.08 | A, C | 1/4 each | [SubmissionsPayloadBuilder.kt:162][V002] |

<a id="t002"></a>

### T002 — Enforce the caller owner when reordering My Life items

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **94/100** | 99 / 95 / 85 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Normalize the supplied caller owner, select only stored rows belonging to that owner, and update/cache only that scope. Do not derive ownership from UI-supplied first-row data. Preserve duplicate-ID handling and unscoped NULL/empty/-- ownership semantics. | L10.01 | D | 1 each | [LifeRepositoryImpl.kt:37][V003]; [LifeRepositoryImpl.kt:39][V004]; [LifeRepositoryImpl.kt:49][V005] |

<a id="t003"></a>

### T003 — Await health uploads inside their owning workers

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **93/100** | 99 / 95 / 80 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Provide a suspend health-upload operation and await it from UserDataWorker/AutoSyncWorker child work before completion. Adapt callback bridges so they resume only after required work, preserving cancellation and existing retry/error policy rather than launching health work in applicationScope. | L17.01 | E | 1 each | [UploadToShelfService.kt:75][V006]; [UserDataWorker.kt:53][V007]; [AutoSyncWorker.kt:108][V008] |

<a id="t004"></a>

### T004 — Make stale-question cleanup bounded and atomic

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **93/100** | 99 / 90 / 88 | B Copilot / Grok, D Codex, F Antigravity | B=1/6; D=2/3; F=1/6 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Run represented-exam stale cleanup in one transaction, retaining null-exam skipping and empty-input no-op behavior. | L08.06, L10.03, L12.07 | B, D, F | 1/6 each | [QuestionDao.kt:29][V009] |
| 2 | Read stored question IDs per represented exam, subtract the full incoming keep set, then delete only stale IDs in bounded positive batches constrained to that exam. Never chunk a NOT IN keep-list; leave unrepresented exams untouched. | L10.03 | D | 1/2 each | [QuestionDao.kt:27][V010]; [QuestionDao.kt:31][V011] |

<a id="t005"></a>

### T005 — Determine dictionary readiness from persisted rows first

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **93/100** | 99 / 85 / 94 | D Codex, F Antigravity | D=1/4; F=3/4 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Under the seed lock, check whether dictionary rows already exist before requiring the import file; preserve first-import, failure, and cancellation results. | L10.05, L12.04 | D, F | 1/4 each | [DictionaryRepositoryImpl.kt:36][V012]; [DictionaryRepositoryImpl.kt:43][V013] |
| 2 | Remove the redundant DictionaryActivity loadCount launch and let loadDictionary publish the count. | L12.04 | F | 1/2 each | [DictionaryActivity.kt:60][V014]; [DictionaryViewModel.kt:39][V015] |

<a id="t006"></a>

### T006 — Bound stale course-step deletion using stale positive IDs

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **92/100** | 99 / 85 / 90 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | In one transaction, read stored step IDs for the course, subtract the complete keep set, and delete stale IDs in bounded positive batches. Preserve empty-keep delete-all semantics and course isolation; never split a NOT IN keep-list into independent deletes. | L10.02 | D | 1 each | [CourseStepDao.kt:18][V016]; [CourseStepDao.kt:21][V017] |

<a id="t007"></a>

### T007 — Parent course-step preparation under its load job

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **91/100** | 99 / 85 / 88 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Launch priority-resource preparation and next-step prefetch as structured children of loadDataJob, so a superseding step cancels their suspended preparation. Preserve downloads already handed to the independent download service and the current-step state guards. | L04.04, L16.01 | D | 1 each | [CoursesStepsViewModel.kt:64][V018]; [CoursesStepsViewModel.kt:110][V019]; [CoursesStepsViewModel.kt:125][V020] |

<a id="t008"></a>

### T008 — Make retry cancellation-transparent and remove unstructured exhaustion reporting

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **91/100** | 98 / 85 / 90 | A Devin, C Copilot / Kimi, E Claude Code | A=1/4; C=1/2; E=1/4 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Rethrow CancellationException before the generic catch, including the last-attempt case, and preserve retry predicates, count, and delay behavior. | L15.01, L17.07 | C, E | 1/4 each | [RetryUtils.kt:17][V021]; [RetryUtils.kt:20][V022] |
| 2 | Remove the exhaustion printStackTrace. If reporting is required, use a platform-independent diagnostic hook or caller-owned reporting policy; do not add android.util.Log to this otherwise Android-free helper. | L03.04, L13.01, L15.01 | A, C | 1/4 each | [RetryUtils.kt:32][V023] |

<a id="t009"></a>

### T009 — Gate the expired challenge before starting repository work

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **91/100** | 99 / 80 / 96 | B Copilot / Grok, C Copilot / Kimi | B=1/2; C=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Evaluate the existing guest, URL, and exclusive date-window conditions before launching the five challenge reads. Emit the existing no-dialog result when gated out; retain behavior for dates within the configured window. | L02.01, L03.01 | B, C | 1/2 each | [DashboardViewModel.kt:286][V024]; [DashboardViewModel.kt:299][V025]; [DashboardViewModel.kt:314][V026] |

<a id="t010"></a>

### T010 — Bound concurrent notification-read sync requests

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **91/100** | 99 / 80 / 94 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use one semaphore for the notification-read sync batch and a small fixed request limit. Retain 404 handling, successful revision collection, cancellation propagation, and the single final database acknowledgement. | L05.07 | E | 1 each | [TransactionSyncManager.kt:441][V027]; [TransactionSyncManager.kt:445][V028]; [TransactionSyncManager.kt:462][V029] |

<a id="t011"></a>

### T011 — Bound text preview input before reading whole lines

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **91/100** | 99 / 80 / 94 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Read at most a fixed character budget before selecting the first eight preview lines. Preserve line ordering, empty-file behavior, charset handling, and cache keys; ensure a giant single-line file never enters the cache in full. | L04.01 | D | 1 each | [ResourcesPreviewLoader.kt:84][V030]; [ResourcesPreviewLoader.kt:90][V031] |

<a id="t012"></a>

### T012 — Bind resource dialogs and path capture to the view lifetime

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **90/100** | 99 / 80 / 92 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Launch the resource download dialog from viewLifecycleOwner.lifecycleScope so it cannot outlive onDestroyView. | L17.06 | E | 1/2 each | [BaseContainerFragment.kt:358][V032]; [BaseContainerFragment.kt:360][V033] |
| 2 | Capture a valid context/external path before the IO hop in openHtmlResource and handle unavailable storage explicitly, avoiding File(null, child) falling back to a relative path. | L17.06 | E | 1/2 each | [BaseContainerFragment.kt:189][V034]; [BaseContainerFragment.kt:192][V035] |

<a id="t013"></a>

### T013 — Coalesce duplicate and cancel superseded team-course loads

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **90/100** | 99 / 80 / 90 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Track the active team/user request and job, coalesce an identical active request, and cancel or reject stale results when inputs change. Retain the dependency from team course IDs to getCoursesByIds and the independent creator lookup. | L16.02 | D | 1 each | [TeamCoursesViewModel.kt:31][V036]; [TeamCoursesViewModel.kt:34][V037]; [TeamCoursesViewModel.kt:36][V038] |

<a id="t014"></a>

### T014 — Preserve cancellation and explicit failure reporting during examination saves

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **90/100** | 98 / 80 / 94 | A Devin, C Copilot / Kimi, E Claude Code, F Antigravity | A=1/6; C=1/3; E=1/3; F=1/6 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Rethrow cancellation from saveExamination before sending a failed-save result; preserve ordinary failure reporting. | L03.07, L15.07, L17.08, L18.10 | C, E, F | 1/6 each | [HealthExaminationViewModel.kt:119][V039] |
| 2 | Replace the save failure printStackTrace with the existing tagged logger. | L03.07, L13.01, L15.07, L17.08 | A, C, E | 1/6 each | [HealthExaminationViewModel.kt:120][V040]; [HealthExaminationViewModel.kt:133][V041] |

<a id="t015"></a>

### T015 — Preserve cancellation and diagnostics on resource reloads

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **89/100** | 99 / 75 / 94 | A Devin, C Copilot / Kimi, E Claude Code | A=1/6; C=5/12; E=5/12 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Rethrow cancellation from loadResources before ordinary-error fallback. | L15.07, L17.03 | C, E | 1/4 each | [ResourcesViewModel.kt:105][V042]; [ResourcesViewModel.kt:109][V043] |
| 2 | Replace the loadResources stack dump with tagged reporting while preserving ordinary failure behavior. | L13.01, L15.07, L17.03 | A, C, E | 1/6 each | [ResourcesViewModel.kt:109][V043] |

<a id="t016"></a>

### T016 — Make course-load errors observable without swallowing cancellation

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **88/100** | 97 / 80 / 88 | A Devin, C Copilot / Kimi, E Claude Code | A=1/15; C=11/30; E=17/30 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace the CoursesViewModel loadCourses stack dump with tagged diagnostics while preserving its public state contract. | L03.08 | C | 1/5 each | [CoursesViewModel.kt:142][V044] |
| 2 | Replace TakeCourseViewModel.loadCourse stack dumps with tagged diagnostics. | L03.08, L13.02, L15.06, L17.08 | A, C, E | 1/15 each | [TakeCourseViewModel.kt:130][V045] |
| 3 | Rethrow cancellation in loadCourse before ordinary error handling. | L15.06, L17.08 | C, E | 1/10 each | [TakeCourseViewModel.kt:130][V045] |
| 4 | Rethrow cancellation from getRatingPromptDecision rather than returning its ordinary fallback. | L17.08 | E | 1/5 each | [TakeCourseViewModel.kt:95][V046]; [TakeCourseViewModel.kt:102][V047] |
| 5 | Move a failed ordinary course load out of Loading through an explicit existing-compatible failure state. | L17.08 | E | 1/5 each | [TakeCourseViewModel.kt:129][V048]; [TakeCourseViewModel.kt:130][V045] |

<a id="t017"></a>

### T017 — Avoid hydrating course steps for scalar title and shelf-ID reads

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **88/100** | 99 / 75 / 93 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Read bare course rows for getCourseTitleById, the title in getCourseProgress, and getMyCourseIds. Preserve blank-ID, membership-pattern, remote/local-ID, ordering, and null behavior; retain full hydration for callers that need steps. | L05.05 | E | 1 each | [CoursesRepositoryImpl.kt:444][V049]; [CoursesRepositoryImpl.kt:525][V050]; [CoursesRepositoryImpl.kt:568][V051]; [CoursesRepositoryImpl.kt:885][V052] |

<a id="t018"></a>

### T018 — Index resource and tag lookup paths on every list replacement

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **87/100** | 98 / 75 / 88 | B Copilot / Grok, E Claude Code | B=1/2; E=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Rebuild resource-ID, local-address, and tag-ID indexes whenever allResourceModels is assigned, including sorting. Replace click/selection scans and tag flattening while preserving the current first-match behavior for duplicate keys. | L02.05, L05.09 | B, E | 1/2 each | [ResourcesFragment.kt:212][V053]; [ResourcesFragment.kt:574][V054]; [ResourcesFragment.kt:583][V055]; [ResourcesFragment.kt:781][V056] |

<a id="t019"></a>

### T019 — Persist submission revision and upload-result batches transactionally

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **87/100** | 98 / 75 / 88 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Wrap per-page revision updates in one Room transaction, preserving pending-local-change and duplicate-cleanup rules. | L05.02 | E | 1/2 each | [SubmissionsRepositoryImpl.kt:811][V057] |
| 2 | Wrap markSubmissionsUploaded writes in one transaction and retain the failed-result list for missing local rows. | L05.02 | E | 1/2 each | [SubmissionsRepositoryImpl.kt:849][V058] |

<a id="t020"></a>

### T020 — Charge every PDF thumbnail a positive rounded cache weight

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **87/100** | 99 / 70 / 96 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use positive rounded-up KiB bitmap weights, with overflow-safe arithmetic. Preserve the existing cache budget and thumbnail key; verify sub-KiB and partial-KiB entries count toward eviction. | L04.07 | D | 1 each | [PdfThumbnailLoader.kt:13][V059]; [PdfThumbnailLoader.kt:14][V060] |

<a id="t021"></a>

### T021 — Use one normalized notification group key for expansion

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **86/100** | 99 / 70 / 90 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Share the existing lowercase/known-type fallback normalization between grouping and default-expansion checks. Reuse a current matching group unread count only when it corresponds to the current notification snapshot; preserve correct first-toggle behavior for unknown/mixed-case types. | L06.07 | F | 1 each | [NotificationsViewModel.kt:99][V061]; [NotificationsViewModel.kt:206][V062]; [NotificationsViewModel.kt:210][V063] |

<a id="t022"></a>

### T022 — Use grouped course-step counts for progress summaries

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **86/100** | 99 / 70 / 90 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Read course-ID/count projections instead of full CourseStep bodies when calculating per-course max/current progress. Preserve the existing step-count arithmetic, missing-course zeros, and passed-progress treatment. | L05.04 | E | 1 each | [ProgressRepositoryImpl.kt:34][V064]; [ProgressRepositoryImpl.kt:47][V065] |

<a id="t023"></a>

### T023 — Count survey questions in grouped SQL projections

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **86/100** | 99 / 70 / 92 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Add a chunked exam-ID/count projection and use it for survey-form badges, filling zero-question surveys with zero. Retain team submission mapping and avoid loading question bodies/choices merely to count them. | L05.01 | E | 1 each | [SurveysRepositoryImpl.kt:357][V066]; [QuestionDao.kt:18][V067]; [QuestionDao.kt:25][V068] |

<a id="t024"></a>

### T024 — Batch submission and answer reads for earned exam passes

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **85/100** | 97 / 80 / 75 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Fetch the relevant user attempts and their answers in bounded batches, then group by parent ID for saveEarnedExamPasses. Preserve complete-status selection, parent construction, passing-required rules, and answer-based grading. Use an exam-type-only batch API only if equivalent to the original parent/status selection; otherwise add a bounded matching query. Index required exams by step with first-valid-match semantics. | L02.02 | B | 1 each | [ProgressRepositoryImpl.kt:166][V069]; [ProgressRepositoryImpl.kt:192][V070]; [SubmissionDao.kt:26][V071]; [SubmissionsRepositoryImpl.kt:366][V072] |

<a id="t025"></a>

### T025 — Commit upload acknowledgement batches transactionally

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **85/100** | 96 / 80 / 75 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Move per-row upload acknowledgement loops behind repository/DAO batch operations and one transaction per table/batch. Preserve each config failure-list contract, row-count handling, and existing revision merge behavior; reuse existing batch APIs rather than nesting network work inside a transaction. | L05.03 | E | 1 each | [UploadConfigs.kt:58][V073]; [UploadConfigs.kt:71][V074]; [UploadConfigs.kt:87][V075]; [UploadConfigs.kt:201][V076]; [UploadConfigs.kt:228][V077] |

<a id="t026"></a>

### T026 — Overlap independent course-opening reads

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **84/100** | 97 / 75 / 80 | A Devin, B Copilot / Grok, C Copilot / Kimi, F Antigravity | A=1/8; B=7/24; C=7/24; F=7/24 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Start current-user and course reads concurrently; preserve NotFound handling and avoid publishing after cancellation. | L02.04, L06.02, L15.06 | B, C, F | 1/6 each | [TakeCourseViewModel.kt:117][V078] |
| 2 | Overlap step and progress reads after their inputs are ready; compute earned passes and blocking-step state only after those results. | L02.04, L06.02, L13.02, L15.06 | A, B, C, F | 1/8 each | [TakeCourseViewModel.kt:124][V079]; [TakeCourseViewModel.kt:127][V080] |

<a id="t027"></a>

### T027 — Batch survey question and submission existence checks

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **83/100** | 96 / 75 / 75 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace per-survey hasSubmission calls with bounded set/count queries for the requested course and user. Preserve blank/null-input exits, userId IS semantics where reached, survey parent IDs, question-count gating, status/type distinctions, and the difference between unfinished and pending checks. | L03.02 | C | 1 each | [SubmissionsRepositoryImpl.kt:209][V081]; [SubmissionsRepositoryImpl.kt:412][V082]; [SubmissionsRepositoryImpl.kt:422][V083]; [SubmissionDao.kt:34][V084] |

<a id="t028"></a>

### T028 — Batch initial automerge PR metadata reads

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **83/100** | 99 / 65 / 88 | A Devin, F Antigravity | A=1/2; F=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Fetch state, reviewDecision, and initial mergeable together for a selected PR. Reuse that snapshot for the adjacent checks, preserving unknown-mergeability polling and refreshing metadata after head/base changes. | L13.05, L18.03 | A, F | 1/2 each | [automerge.sh:104][V085]; [automerge.sh:107][V086]; [automerge.sh:502][V087]; [automerge.sh:509][V088]; [automerge.sh:518][V089] |

<a id="t029"></a>

### T029 — Avoid redundant user reads, writes, and key generation for health data

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **83/100** | 99 / 65 / 88 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Generate missing security keys only when needed, skip upsert when both existing values are already present, and defer saveKeyIv generation until the corresponding nonempty value is absent. Preserve the existing null-versus-empty policies of each method. | L18.10 | F | 1/2 each | [UserRepositoryImpl.kt:402][V090]; [UserRepositoryImpl.kt:665][V091] |
| 2 | Use ensureUserSecurityKeys as the primary patient-user read and fall back only as required by the existing null behavior, avoiding the unconditional duplicate fetch. | L18.10 | F | 1/2 each | [HealthExaminationViewModel.kt:69][V092]; [HealthExaminationViewModel.kt:73][V093] |

<a id="t030"></a>

### T030 — Prepare course-detail storage paths and markdown off main

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **83/100** | 98 / 65 / 90 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Resolve the external path on the injected IO dispatcher and rewrite course-description image markdown on the compute dispatcher. Publish state only for the active collection and retain null-directory and load-error behavior. | L04.02 | D | 1 each | [CourseDetailViewModel.kt:53][V094]; [CourseDetailViewModel.kt:68][V095] |

<a id="t031"></a>

### T031 — Load the team voice feed once and count for its read watermark

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **83/100** | 98 / 65 / 90 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Keep the discussions Flow as the feed source; replace the duplicate one-shot feed read with countTopLevelByTeam for the seen watermark. Preserve count-to-Int bounds, team identity, badge semantics, and initial empty rendering. | L05.08 | E | 1 each | [TeamsVoicesFragment.kt:123][V096]; [TeamsVoicesFragment.kt:125][V097]; [TeamsVoicesViewModel.kt:58][V098]; [VoicesRepositoryImpl.kt:573][V099] |

<a id="t032"></a>

### T032 — Measure quota epochs once per forecast

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **83/100** | 99 / 60 / 97 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Measure release epochs once per forecast and format both its report and future slots from that same snapshot instead of calling status and fetching again. | L18.01 | F | 1 each | [playstore-quota.sh:56][V100]; [playstore-quota.sh:82][V101]; [playstore-quota.sh:114][V102] |

<a id="t033"></a>

### T033 — Amortize UserRepositoryImpl test instrumentation with isolated stubs

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **82/100** | 98 / 65 / 85 | A Devin, C Copilot / Kimi | A=1/4; C=3/4 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Install recurring UrlUtils, NetworkUtils, and Log instrumentation once per class. Reset answers/calls before each test and retain the later Log.e assertions. | L13.07, L15.03 | A, C | 1/4 each | [UserRepositoryImplTest.kt:82][V103]; [UserRepositoryImplTest.kt:150][V104]; [UserRepositoryImplTest.kt:657][V105] |
| 2 | Avoid repeatedly installing SecurePrefs instrumentation in individual cases; reset and restore it without leaking stubs or weakening error-path assertions. | L15.03 | C | 1/2 each | [UserRepositoryImplTest.kt:555][V106]; [UserRepositoryImplTest.kt:580][V107] |

<a id="t034"></a>

### T034 — Apply selective resource predicates before ranked title search

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **81/100** | 98 / 60 / 90 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Filter by tag, facets, and download state before ranked title search. Preserve prefix/contains priority and stable order among surviving results for apply and countMatching. | L04.05 | D | 1 each | [ResourcesListFilter.kt:64][V108]; [ResourcesListFilter.kt:85][V109] |

<a id="t035"></a>

### T035 — Move notification grouping and flattening off main

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **81/100** | 98 / 60 / 90 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Run notification grouping and combined row flattening on the injected compute dispatcher before stateIn, preserving order, selection/expansion updates, subscription behavior, and immutable snapshots. | L04.03 | D | 1 each | [NotificationsViewModel.kt:61][V110]; [NotificationsViewModel.kt:65][V111] |

<a id="t036"></a>

### T036 — Reduce repeated LoginSyncManager test instrumentation

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **80/100** | 98 / 60 / 88 | A Devin, C Copilot / Kimi, F Antigravity | A=1/6; C=1/6; F=2/3 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove unused Android Log/Base64 instrumentation after checking indirect call paths; UrlUtils already uses java.util.Base64. Keep any needed logging assertion instrumentation. | L18.05 | F | 1/2 each | [LoginSyncManagerTest.kt:56][V112]; [LoginSyncManagerTest.kt:60][V113]; [UrlUtils.kt:78][V114] |
| 2 | Install necessary UrlUtils/AndroidDecrypter object instrumentation at class scope and reset answers, recorded calls, repositories, and coroutine state per test; use targeted teardown. | L13.06, L15.04, L18.05 | A, C, F | 1/6 each | [LoginSyncManagerTest.kt:65][V115]; [LoginSyncManagerTest.kt:69][V116]; [LoginSyncManagerTest.kt:83][V117] |

<a id="t037"></a>

### T037 — Sort resource snapshots on the compute dispatcher with one hop

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **80/100** | 98 / 60 / 88 | E Claude Code, F Antigravity | E=3/4; F=1/4 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use the injected default dispatcher for the three pure sorting entry points. | L12.08, L17.03 | E, F | 1/4 each | [ResourcesViewModel.kt:177][V118]; [ResourcesViewModel.kt:185][V119]; [ResourcesViewModel.kt:193][V120] |
| 2 | Capture sort settings on the owning thread, perform one compute hop through the synchronous helper, and publish results without racing sort-mode fields or nesting dispatcher hops. | L17.03 | E | 1/2 each | [ResourcesViewModel.kt:180][V121]; [ResourcesViewModel.kt:188][V122]; [ResourcesViewModel.kt:197][V123] |

<a id="t038"></a>

### T038 — Request only display-name metadata when resolving picked files

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **80/100** | 99 / 55 / 97 | B Copilot / Grok, D Codex | B=1/2; D=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Pass a DISPLAY_NAME-only projection to both content-provider queries. Preserve missing-column, null-cursor, URI, fallback-name, and traversal-sanitization behavior; update strict projection expectations in tests. | L04.06, L14.03, L16.08 | B, D | 1/2 each | [FileUtils.kt:217][V124]; [FileUtils.kt:243][V125] |

<a id="t039"></a>

### T039 — Inject the existing clock into public-survey recency checks

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **80/100** | 99 / 55 / 98 | C Copilot / Kimi, F Antigravity | C=1/2; F=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Inject TimeProvider, replace the wall-clock read, and use a fixed clock in launch/recency tests. Preserve the existing stale-submission event and timestamp comparison exactly. | L15.10, L18.07 | C, F | 1/2 each | [PublicSurveyViewModel.kt:62][V126]; [PublicSurveyViewModel.kt:74][V127]; [TimeModule.kt:16][V128] |

<a id="t040"></a>

### T040 — Move retry-event Android construction to the service boundary

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **79/100** | 98 / 60 / 82 | B Copilot / Grok, C Copilot / Kimi | B=5/6; C=1/6 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Give BroadcastService a retry-event operation that constructs the Intent and preserves encoded path, attempt, delay, and nonblocking emission. Keep interceptor retry/backoff policy independent of Intent construction. | L14.02 | B | 1/3 each | [RetryInterceptor.kt:62][V129]; [BroadcastService.kt:18][V130] |
| 2 | Exercise retry-event emission policy in the existing plain JVM interceptor tests without a separate one-test Robolectric class. | L14.02, L15.02 | B, C | 1/6 each | [RetryInterceptorEventTest.kt:24][V131]; [RetryInterceptorEventTest.kt:40][V132] |
| 3 | Retain actual action/extras and credential-exclusion coverage at the Android BroadcastService boundary when relocating the old test. | L14.02 | B | 1/3 each | [RetryInterceptorEventTest.kt:68][V133]; [BroadcastService.kt:18][V130] |

<a id="t041"></a>

### T041 — Load collaborators only when coauthor filtering needs them

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **79/100** | 99 / 55 / 92 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Collect/filter candidate logins first and lazily fetch the paginated collaborator set only for a surviving candidate. Preserve case normalization, bot/author/owner filtering, deduplication, and API-failure behavior once the lookup is required. | L18.02 | F | 1 each | [coauthors.sh:23][V134]; [coauthors.sh:75][V135]; [coauthors.sh:81][V136]; [coauthors.sh:93][V137] |

<a id="t042"></a>

### T042 — Rewrite course-step markdown on the compute dispatcher

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **79/100** | 99 / 55 / 92 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Resolve paths on IO, then rewrite image markdown and prepare resource subsets on the compute dispatcher before publishing active-step state. Preserve structured cancellation and existing download predicates. | L14.05 | B | 1 each | [CoursesStepsViewModel.kt:72][V138]; [CoursesStepsViewModel.kt:76][V139]; [CoursesStepsViewModel.kt:82][V140] |

<a id="t043"></a>

### T043 — Remove dead exam-test instrumentation and isolate the required device-ID mock

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **79/100** | 98 / 55 / 93 | B Copilot / Grok, D Codex, F Antigravity | B=1/4; D=1/4; F=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove the unused TextUtils static mock and stub from StepExamTest. | L14.10, L18.05 | B, F | 1/4 each | [StepExamTest.kt:23][V141]; [StepExamTest.kt:24][V142] |
| 2 | Install the required NetworkUtils instrumentation at class scope with per-test resets, or scope it only to serialization cases; preserve the exact existing device-ID answer and targeted cleanup. | L16.05, L18.05 | D, F | 1/4 each | [StepExamTest.kt:28][V143]; [StepExamTest.kt:34][V144] |

<a id="t044"></a>

### T044 — Select the publish check directly instead of listing every run jobs

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **78/100** | 98 / 60 / 80 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Find completed matching publish checks from the SHA check-run list, then read annotations using the actual check-run ID. Preserve pagination, job-name/app selection, marker filtering, track verification, and fresh-state decisions; avoid treating an Actions job ID as guaranteed interchangeable with a check-run ID. | L14.08 | B | 1 each | [automerge.sh:336][V145]; [automerge.sh:339][V146]; [automerge.sh:344][V147] |

<a id="t045"></a>

### T045 — Overlap independent chat share-target reads

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **78/100** | 98 / 55 / 90 | A Devin, F Antigravity | A=1/4; F=3/4 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Start team and enterprise summary reads concurrently. | L06.04, L13.03 | A, F | 1/4 each | [ChatViewModel.kt:193][V148] |
| 2 | Resolve the optional community ID first and overlap its summary lookup too, retaining the existing fallback TeamSummary and null-ID behavior. | L06.04 | F | 1/2 each | [ChatViewModel.kt:196][V149] |

<a id="t046"></a>

### T046 — Prepare the team course picker off main

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **78/100** | 98 / 55 / 90 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Inject DispatcherProvider into TeamCoursesViewModel and move available-course loading/mapping off the fragment main dispatcher, using IO for blocking storage preparation and default for filtering. Preserve exclusion IDs and list order. | L14.06 | B | 1 each | [TeamCoursesViewModel.kt:55][V150]; [TeamCoursesFragment.kt:109][V151] |

<a id="t047"></a>

### T047 — Branch scoped My Life queries before entering SQL

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **78/100** | 98 / 55 / 88 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Dispatch nullable/unscoped and non-null exact-user lookups to separate DAO queries for observe/get/visible/count methods. Preserve the null branch matching NULL, empty, and -- owners and its weight order; repository normalization stays unchanged. | L08.03 | B | 1 each | [MyLifeDao.kt:14][V152]; [MyLifeDao.kt:20][V153]; [LifeRepositoryImpl.kt:19][V154] |

<a id="t048"></a>

### T048 — Move team-task filtering and assignee eligibility out of the fragment

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **78/100** | 97 / 55 / 90 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Extract pure task-state/current-user filters and assignable-member selection behind TeamsTasksViewModel or a pure collaborator. Retain sort/state/user rules and have the fragment consume prepared values. | L09.06 | C | 1 each | [TeamsTasksFragment.kt:177][V155]; [TeamsTasksFragment.kt:405][V156]; [TeamsTasksViewModel.kt:24][V157] |

<a id="t049"></a>

### T049 — Drain persisted download-link buffers safely

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **77/100** | 96 / 65 / 70 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Deduplicate pending links and stop re-merging an unchanged process-lifetime buffer on every page. Serialize snapshot/merge/write/drain across both writers of the same preference so concurrent arrivals and failed writes cannot lose links; skip empty snapshots. | L05.06 | E | 1 each | [VoicesRepositoryImpl.kt:553][V158]; [MyCourse.kt:96][V159] |

<a id="t050"></a>

### T050 — Reduce repeated news-sync test setup while keeping real Room coverage

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **77/100** | 98 / 60 / 76 | B Copilot / Grok, C Copilot / Kimi, D Codex | B=2/3; C=1/6; D=1/6 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Reuse the in-memory Room database within a compatible Robolectric test-class sandbox, clearing all relevant tables and state before each case and closing only at class teardown. Verify shuffled/repeated isolation before adopting the reuse. | L14.04 | B | 1/2 each | [VoicesRepositoryNewsSyncTest.kt:79][V160]; [VoicesRepositoryNewsSyncTest.kt:98][V161] |
| 2 | Install UrlUtils/NetworkUtils object instrumentation once per class, remove the redundant in-test installation, and reset answers/calls per case. | L14.04, L15.05, L16.03 | B, C, D | 1/6 each | [VoicesRepositoryNewsSyncTest.kt:71][V162]; [VoicesRepositoryNewsSyncTest.kt:73][V163]; [VoicesRepositoryNewsSyncTest.kt:302][V164] |

<a id="t051"></a>

### T051 — Overlap independent course-detail reads

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **77/100** | 96 / 60 / 78 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use structured child coroutines for independent user, exam-count, resource, and step reads in getCourseDetailModel. Keep question-count work dependent on step IDs and rating work dependent on the user; retain flow cancellation and null-course handling. | L02.03 | B | 1 each | [CoursesRepositoryImpl.kt:130][V165]; [CoursesRepositoryImpl.kt:134][V166]; [CoursesRepositoryImpl.kt:163][V167] |

<a id="t052"></a>

### T052 — Remove Context-based notification text formatting from the ViewModel

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **77/100** | 99 / 55 / 83 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Move localized row text to a UI-owned formatter/string provider or explicit UI text descriptors. Keep grouping and raw enrichment in the ViewModel, preserve locale-aware arguments and existing formatting output, and make tests assert real text. | L11.03, L17.09 | E | 1 each | [NotificationsViewModel.kt:33][V168]; [NotificationsViewModel.kt:298][V169]; [NotificationsViewModel.kt:320][V170] |

<a id="t053"></a>

### T053 — Simplify submission-detail author dependencies and state sharing

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **77/100** | 99 / 50 / 94 | A Devin, B Copilot / Grok, D Codex, E Claude Code, F Antigravity | A=1/10; B=1/10; D=1/10; E=1/10; F=3/5 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Inject UserReadRepository for the single submission-author lookup. | L07.04, L08.10, L10.06, L11.02, L12.09 | A, B, D, E, F | 1/10 each | [SubmissionDetailViewModel.kt:30][V171]; [SubmissionDetailViewModel.kt:38][V172] |
| 2 | Map the one-shot submission detail directly into uiState and share it once, retaining null/loading/error semantics and WhileSubscribed behavior. | L12.09 | F | 1/2 each | [SubmissionDetailViewModel.kt:35][V173]; [SubmissionDetailViewModel.kt:43][V174]; [SubmissionDetailViewModel.kt:59][V175] |

<a id="t054"></a>

### T054 — Combine feedback sync identity lookups safely and use injected Gson

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **76/100** | 96 / 55 / 83 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Combine local/remote-ID matching into one bounded query with at most half the SQLite variable budget per chunk because the list is bound twice. Preserve current row-precedence and pending-local-change deduplication rather than relying on unspecified OR result order. | L18.08 | F | 1/2 each | [FeedbackRepositoryImpl.kt:107][V176]; [FeedbackRepositoryImpl.kt:110][V177]; [FeedbackDao.kt:25][V178]; [FeedbackDao.kt:33][V179] |
| 2 | Use the existing PlainGson instance for messages serialization, preserving the exact payload format. | L18.08 | F | 1/2 each | [FeedbackRepositoryImpl.kt:18][V180]; [FeedbackRepositoryImpl.kt:145][V181] |

<a id="t055"></a>

### T055 — Cache parsed voice reactions by their raw stored strings

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **76/100** | 96 / 55 / 82 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Cache reaction JSON/maps and pending-reaction maps using their raw-string inputs. Return defensive copies or immutable snapshots, invalidate on mutation, and preserve malformed/null fallbacks. | L02.07 | B | 1 each | [News.kt:125][V182]; [News.kt:133][V183]; [News.kt:147][V184]; [News.kt:173][V185] |

<a id="t056"></a>

### T056 — Avoid real drawable decoding in ImageUtils interaction tests

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **76/100** | 98 / 50 / 90 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Stub the image-resource setter on interaction-test spies while retaining clear-before-placeholder verification and resource IDs. Share static Glide instrumentation only with per-test call/answer resets and targeted teardown. Keep rendering tests separate where drawable decoding is the asserted behavior. | L14.09 | B | 1 each | [ImageUtilsTest.kt:149][V186]; [ImageUtilsTest.kt:215][V187]; [ImageUtilsTest.kt:235][V188] |

<a id="t057"></a>

### T057 — Seed a compatible default-branch debug build cache

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **75/100** | 90 / 65 / 70 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Add a master/default-branch cache producer compatible with both debug flavor keys and restore paths, reusing existing Gradle setup and failure handling. Preserve branch isolation and validate actual warm task reuse before reporting compile savings. | L14.01 | B | 1 each | [build.yml:3][V189]; [build.yml:41][V190]; [build.yml:50][V191] |

<a id="t058"></a>

### T058 — Precompute user sort keys while preserving Kotlin ordering

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **75/100** | 96 / 50 / 90 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Normalize each selected string field once per user before stable sorting. Preserve the existing invariant-locale lowercase policy, empty/null treatment, boolean/numeric branches, descending behavior, and unsupported-field behavior. | L02.09 | B | 1 each | [UserRepositoryImpl.kt:339][V192] |

<a id="t059"></a>

### T059 — Parse in-memory chat JSON on the compute dispatcher

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **75/100** | 99 / 45 / 98 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace the IO dispatcher for already-in-memory conversation JSON parsing with the injected default dispatcher. Keep pagination state publication on its owner thread and preserve malformed/empty input behavior. | L17.04 | E | 1 each | [ChatConversationPaginator.kt:19][V193]; [ChatConversationPaginator.kt:20][V194] |

<a id="t060"></a>

### T060 — Narrow course-screen user reads and remove an unused session injection

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **75/100** | 99 / 45 / 97 | A Devin, D Codex, E Claude Code, F Antigravity | A=7/36; D=7/36; E=7/36; F=5/12 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Inject UserReadRepository into TakeCourseViewModel. | L07.10, L10.08, L11.02, L12.02 | A, D, E, F | 1/12 each | [TakeCourseViewModel.kt:42][V195]; [TakeCourseViewModel.kt:117][V078] |
| 2 | Inject UserReadRepository into CoursesStepsViewModel. | L07.10, L10.08, L11.02 | A, D, E | 1/9 each | [CoursesStepsViewModel.kt:49][V196]; [CoursesStepsViewModel.kt:68][V197] |
| 3 | Remove the never-read UserSessionManager injection and import from TakeCourseFragment. | L12.02 | F | 1/3 each | [TakeCourseFragment.kt:30][V198]; [TakeCourseFragment.kt:43][V199] |

<a id="t061"></a>

### T061 — Narrow home-area identity reads to UserReadRepository

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **75/100** | 99 / 45 / 98 | A Devin, B Copilot / Grok, D Codex, E Claude Code | A=1/3; B=1/6; D=1/6; E=1/3 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Narrow CalendarViewModel to UserReadRepository; its user dependency only obtains the current user. | L07.03, L11.01 | A, E | 1/10 each | [CalendarViewModel.kt:24][V200]; [CalendarViewModel.kt:42][V201] |
| 2 | Narrow CommunityTabViewModel to UserReadRepository. | L07.03, L08.10, L11.08 | A, B, E | 1/15 each | [CommunityTabViewModel.kt:24][V202]; [CommunityTabViewModel.kt:33][V203] |
| 3 | Narrow DashboardElementViewModel to UserReadRepository. | L07.03, L08.10, L10.09, L11.01 | A, B, D, E | 1/20 each | [DashboardElementViewModel.kt:13][V204]; [DashboardElementViewModel.kt:19][V205] |
| 4 | Narrow ActivitiesViewModel to UserReadRepository. | L07.03, L10.09, L11.01 | A, D, E | 1/15 each | [ActivitiesViewModel.kt:22][V206]; [ActivitiesViewModel.kt:39][V207] |
| 5 | Narrow PersonalsViewModel to UserReadRepository. | L07.04, L08.10, L10.10, L11.01 | A, B, D, E | 1/20 each | [PersonalsViewModel.kt:31][V208]; [PersonalsViewModel.kt:35][V209] |

<a id="t062"></a>

### T062 — Narrow resource-screen identity reads to UserReadRepository

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **75/100** | 99 / 45 / 98 | A Devin, D Codex, E Claude Code, F Antigravity | A=13/36; D=13/36; E=7/36; F=1/12 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Narrow ResourcesAccessViewModel while retaining its existing guest test and resource operations. | L07.04, L10.07, L11.02 | A, D, E | 1/9 each | [ResourcesAccessViewModel.kt:21][V210]; [ResourcesAccessViewModel.kt:49][V211] |
| 2 | Narrow ResourcesViewModel, whose user dependency only resolves the current user. | L07.04, L10.10, L11.02, L12.08 | A, D, E, F | 1/12 each | [ResourcesViewModel.kt:31][V212]; [ResourcesViewModel.kt:39][V213] |
| 3 | Narrow ResourceViewerViewModel while preserving rating-prompt identity behavior. | L07.04, L10.07 | A, D | 1/6 each | [ResourceViewerViewModel.kt:30][V214]; [ResourceViewerViewModel.kt:40][V215] |

<a id="t063"></a>

### T063 — Make chunked team-log deletion atomic

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **75/100** | 99 / 45 / 97 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Annotate the chunk orchestration with Transaction, retain distinct IDs and bounded positive deletes, and return early for empty input. Verify rollback across more than one chunk. | L12.06 | F | 1 each | [TeamLogDao.kt:49][V216]; [TeamLogDao.kt:52][V217] |

<a id="t064"></a>

### T064 — Compare multi-select answers as normalized multisets

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **74/100** | 99 / 45 / 94 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace dual list sorting with frequency counts under the same locale and size checks. Preserve duplicate multiplicity, null behavior, and normalized grading results. | L06.01 | F | 1 each | [ExamAnswerUtils.kt:77][V218] |

<a id="t065"></a>

### T065 — Extract pure survey ranking and sort-date rules

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **74/100** | 98 / 45 / 93 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Extract survey search matching and adopted-survey sort-date selection into a pure tested component. Preserve normalization, prefix-before-contains ranking, stable ties, and adoptionDate fallback. | L09.07 | C | 1 each | [SurveysViewModel.kt:201][V219]; [SurveysViewModel.kt:209][V220] |

<a id="t066"></a>

### T066 — Centralize guest creation and authentication orchestration

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **73/100** | 94 / 60 / 65 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Move the repeated guest-creation/session-save sequence behind LoginViewModel and return typed outcomes to the three UI entry points. Keep UI navigation, dialogs, and lifecycle ownership at the view boundary. | L07.05 | A | 1/2 each | [GuestLoginExtensions.kt:68][V221]; [LoginActivity.kt:597][V222]; [LoginActivity.kt:639][V223] |
| 2 | Move authenticate/has-user/session-save orchestration out of SyncActivity into a reusable login-owned operation, preserving all AuthUtils callers, saved credentials, cancellation, and manager mode. | L07.05 | A | 1/2 each | [SyncActivity.kt:390][V224]; [LoginViewModel.kt:89][V225] |

<a id="t067"></a>

### T067 — Amortize reachability-worker test mocks while retaining log assertions

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **73/100** | 99 / 45 / 88 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Install logging/network/application instrumentation once per class, reset answers and calls per case, and preserve cancellation-is-silent and failure-is-logged assertions. Replace global teardown with target-specific cleanup. | L16.07 | D | 1 each | [ServerReachabilityWorkerTest.kt:47][V226]; [ServerReachabilityWorkerTest.kt:94][V227]; [ServerReachabilityWorkerTest.kt:125][V228] |

<a id="t068"></a>

### T068 — Overlap independent joined-member and current-user reads

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **73/100** | 98 / 45 / 90 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use structured child coroutines for member and current-user reads in RequestsViewModel.loadJoinedMembers, then publish one state with the existing leadership predicate. | L06.03 | F | 1 each | [RequestsViewModel.kt:67][V229] |

<a id="t069"></a>

### T069 — Use the shared Robolectric configuration for PDF thumbnail tests

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **73/100** | 98 / 45 / 88 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace the SDK-28-only pin with the project default sandbox and lightweight Application/manifest configuration, confirming the thumbnail tests do not rely on API-28 behavior. Retain IO-thread assertions and actual missing/non-PDF coverage. | L18.04 | F | 1 each | [PdfThumbnailLoaderTest.kt:21][V230]; [PdfThumbnailLoader.kt:17][V231] |

<a id="t070"></a>

### T070 — Amortize image-encoding test instrumentation and restore context per case

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **72/100** | 98 / 45 / 86 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Install the needed URI/static instrumentation once per class and reset stubs/calls per test. Continue restoring MainApplication context for every case, including failures; remove unused instrumentation only after checking its verification sites. | L16.06 | D | 1 each | [UserEntityEncodeImageTest.kt:35][V232]; [UserEntityEncodeImageTest.kt:47][V233]; [UserEntityEncodeImageTest.kt:55][V234] |

<a id="t071"></a>

### T071 — Reuse the shared image viewer and image dimensions

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **72/100** | 98 / 45 / 86 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Delegate VoicesAdapter zoomable images to ImageViewerUtils and remove the duplicate dialog implementation, preserving local/GIF/error handling. | L05.10 | E | 1/2 each | [VoicesAdapter.kt:1168][V235]; [ImageViewerUtils.kt:17][V236] |
| 2 | Resolve thumbnail dimensions and margins once per adapter display configuration and reuse them in the four image helpers. | L05.10 | E | 1/2 each | [VoicesAdapter.kt:1097][V237]; [VoicesAdapter.kt:1108][V238]; [VoicesAdapter.kt:1132][V239] |

<a id="t072"></a>

### T072 — Seed My Life once and observe a single list source

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **72/100** | 95 / 45 / 88 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use seedMyLifeIfEmpty followed by the DAO-backed Flow instead of a full get-list followed by the same observed list. Preserve default seeding, user normalization, de-duplication, and empty-state ordering; account for the seed method own count/return reads. | L11.04 | E | 1 each | [LifeViewModel.kt:38][V240]; [LifeViewModel.kt:41][V241]; [LifeRepositoryImpl.kt:127][V242] |

<a id="t073"></a>

### T073 — Filter empty community names in the DAO

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **72/100** | 99 / 40 / 97 | A Devin, E Claude Code | A=1/2; E=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Move the exact TextUtils.isEmpty name predicate into CommunityDao.getAllSorted and remove the UI filter. Preserve weight ordering and whitespace-only names; TextUtils.isEmpty does not trim whitespace. | L07.06, L11.05 | A, E | 1/2 each | [CommunityDao.kt:12][V243]; [ServerDialogExtensions.kt:298][V244] |

<a id="t074"></a>

### T074 — Return the updated meetup without re-reading it

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **72/100** | 99 / 40 / 97 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Return the mutated entity after successful synchronous upsert in toggleAttendance. Preserve blank meetup/user handling and avoid claiming a concurrency snapshot stronger than the previous implementation. | L09.08 | C | 1 each | [EventsRepositoryImpl.kt:84][V245]; [EventsRepositoryImpl.kt:93][V246]; [EventsRepositoryImpl.kt:100][V247] |

<a id="t075"></a>

### T075 — Promote the active-user ID read and narrow LifeViewModel

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **72/100** | 99 / 40 / 96 | B Copilot / Grok, F Antigravity | B=1/2; F=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Move getCurrentUserId to UserReadRepository and inject that contract into LifeViewModel. Preserve blank/-- normalization and getUserModel fallback; update the mock contract and rebuild callers rather than claiming binary compatibility automatically. | L08.08, L12.10 | B, F | 1/2 each | [LifeViewModel.kt:20][V248]; [LifeViewModel.kt:28][V249]; [UserRepository.kt:80][V250]; [UserReadRepository.kt:5][V251] |

<a id="t076"></a>

### T076 — Use the My Life visibility UPDATE result instead of a pre-read

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **72/100** | 99 / 40 / 96 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Return an affected-row count from the exact-ID visibility update and fall back to the existing unique-alias resolution only when zero rows match. Preserve the transaction, ambiguous-alias no-op, and already-equal-value behavior. | L12.05 | F | 1 each | [MyLifeDao.kt:34][V252]; [MyLifeDao.kt:40][V253]; [MyLifeDao.kt:43][V254] |

<a id="t077"></a>

### T077 — Avoid re-encoding unchanged profile images during serialization

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 90 / 60 / 60 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Memoize avatar bytes/Base64 only with a bounded lifetime and a key that distinguishes path/URI content changes, user, and access mode. Do not cache unreadable results indefinitely; retain serializer output and malformed/missing-image behavior. Prefer an explicit batch snapshot where content URIs lack trustworthy metadata. | L03.03 | C | 1 each | [UserEntity.kt:61][V255]; [UserEntity.kt:117][V256]; [SubmissionsPayloadBuilder.kt:152][V257] |

<a id="t078"></a>

### T078 — Reuse the membership fetch when assembling team members

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 90 / 50 / 80 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Fetch membership rows once for member hydration and leader IDs. Preserve pending-deletion filtering and first-seen user order; retain the separate all-document admin-membership check unless equivalence is established. | L01.01 | A | 1 each | [TeamsRepositoryImpl.kt:983][V258]; [TeamsRepositoryImpl.kt:1007][V259]; [TeamsRepositoryImpl.kt:1025][V260] |

<a id="t079"></a>

### T079 — Precompute chat sort keys and avoid revision splitting

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 94 / 45 / 87 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Decorate each chat with its parsed maximum created/updated timestamp once, stable-sort those keys, and restore the chat list. | L02.08 | B | 1/2 each | [ChatRepositoryImpl.kt:132][V261] |
| 2 | Replace split-to-first-segment in revision generation extraction with substringBefore while retaining nullable, invalid, overflow, and first-winner tie semantics. | L02.08 | B | 1/2 each | [ChatRepositoryImpl.kt:138][V262] |

<a id="t080"></a>

### T080 — Expose consumed user reads through the read contract

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 91 / 45 / 90 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Promote getUserProfile for RatingsViewModel, hasActiveUserSyncAction for MarkdownViewModel, and the sorted/search reads for HealthViewModel to UserReadRepository. Move declarations consistently rather than duplicating inherited signatures without override; retain current implementations and Hilt bindings. | L07.02 | A | 1 each | [UserRepository.kt:37][V263]; [UserRepository.kt:82][V264]; [UserRepository.kt:90][V265]; [RatingsViewModel.kt:55][V266]; [MarkdownViewModel.kt:17][V267]; [HealthViewModel.kt:109][V268] |

<a id="t081"></a>

### T081 — Resolve the active chat user through the repository boundary

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 99 / 40 / 94 | E Claude Code, F Antigravity | E=1/2; F=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove ChatHistoryFragment preference-based user-ID orchestration and let ChatViewModel resolve the current user through getUserModel. Preserve explicit-user behavior where retained, account switching, null user handling, and refresh cancellation. | L11.10, L12.03 | E, F | 1/2 each | [ChatHistoryFragment.kt:135][V269]; [ChatViewModel.kt:187][V270]; [UserReadRepository.kt:8][V271] |

<a id="t082"></a>

### T082 — Migrate four selected Mockito-based tests to the declared MockK stack

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 99 / 40 / 92 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Migrate SubmissionDetailViewModelTest, CoursesAdapterTest, ResourcesAdapterTest, and DashboardSurveysAdapterTest to MockK without weakening assertions, interaction counts, or callback capture. Retain any required Robolectric runner. | L11.06 | E | 1 each | [SubmissionDetailViewModelTest.kt:16][V272]; [CoursesAdapterTest.kt:16][V273]; [ResourcesAdapterTest.kt:11][V274]; [DashboardSurveysAdapterTest.kt:15][V275] |

<a id="t083"></a>

### T083 — Use injected scope and Activity resources in SyncActivity

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 99 / 40 / 94 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Inject the existing ApplicationScope for the post-login watcher instead of reading MainApplication.applicationScope, and use Activity resource lookup where appropriate. Preserve watcher lifetime, cancellation, and reachability behavior. | L17.02 | E | 1 each | [SyncActivity.kt:346][V276]; [SyncActivity.kt:649][V277]; [SyncActivity.kt:662][V278] |

<a id="t084"></a>

### T084 — Index test filenames once for source-based timing diagnostics

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 99 / 40 / 95 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Build one source filename index and cache content reads within a diagnostic invocation. Preserve traversal precedence for duplicate filenames and all existing warnings/thresholds. This targets the explicit test-sources option rather than claiming the current workflow enables it. | L16.10 | D | 1 each | [test_timing_summary.py:243][V279]; [test_timing_summary.py:249][V280]; [test_timing_summary.py:255][V281] |

<a id="t085"></a>

### T085 — Narrow community-service team dependencies

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 98 / 40 / 93 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Expose getTeamLinks through the share contract and inject the share and member read contracts into CommunityServicesViewModel. Keep current list membership semantics and existing Hilt implementation bindings. | L08.07 | B | 1 each | [CommunityServicesViewModel.kt:16][V282]; [CommunityServicesViewModel.kt:28][V283]; [TeamsShareRepository.kt:5][V284]; [TeamsMembersRepository.kt:7][V285] |

<a id="t086"></a>

### T086 — Remove unasserted model-test logging instrumentation

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 98 / 40 / 95 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove unused Log mocks from HealthExaminationTest and retain all malformed-data fallback assertions. | L18.05 | F | 1/2 each | [HealthExaminationTest.kt:17][V286]; [HealthExaminationTest.kt:19][V287] |
| 2 | Remove unused Log mocks from NewsTest while retaining targeted cleanup for its in-test FileUtils object mocks. | L18.05 | F | 1/2 each | [NewsTest.kt:25][V288]; [NewsTest.kt:27][V289]; [NewsTest.kt:259][V290] |

<a id="t087"></a>

### T087 — Move shelf-sync user reads onto the sync contract

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **71/100** | 97 / 40 / 94 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Move getSyncedUsers, getSyncedUserByName, and getPendingSyncUsers declarations to UserSyncRepository and rewire the three shelf-upload calls. Keep the remaining getUserByName dependency until a contract that preserves its semantics is available; update service mocks. | L07.01 | A | 1 each | [UserRepository.kt:32][V291]; [UserSyncRepository.kt:7][V292]; [UploadToShelfService.kt:35][V293]; [UploadToShelfService.kt:58][V294]; [UploadToShelfService.kt:102][V295] |

<a id="t088"></a>

### T088 — Fetch only release fields used by quota estimation

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **70/100** | 95 / 45 / 80 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Request only release draft/publication fields through a suitably paged server-side projection while preserving the current release selection, ordering, and injected RELEASES_JSON behavior. Do not drop republished releases by assuming publication and creation order are identical. | L14.07 | B | 1 each | [playstore-quota.sh:33][V296]; [playstore-quota.sh:38][V297]; [playstore-quota.sh:40][V298] |

<a id="t089"></a>

### T089 — Update one storage checkbox without a full recount

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **70/100** | 98 / 40 / 90 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Find the matching item, leave unknown IDs unchanged, copy only the list/item needed, and adjust checkedCount incrementally. Preserve behavior if duplicate resource IDs are possible, rather than assuming uniqueness without evidence. | L06.06 | F | 1 each | [StorageCategoryViewModel.kt:61][V299] |

<a id="t090"></a>

### T090 — Reduce waste in submission flow comparison and survey-exam lookup

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **70/100** | 98 / 40 / 92 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Add a nullable LIMIT 1 survey-exam lookup for isStepCompleted while retaining the list API used by courses. | L01.03 | A | 1/2 each | [ExamDao.kt:38][V300]; [SubmissionsRepositoryImpl.kt:401][V301] |
| 2 | Compare equal-sized submission lists by index instead of allocating zip pairs, retaining the same id/lastUpdateTime equality rule. | L01.03 | A | 1/2 each | [SubmissionsRepositoryImpl.kt:80][V302] |

<a id="t091"></a>

### T091 — Skip empty tag queries and share parent-tag resolution

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **70/100** | 98 / 40 / 92 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Return the correct empty list/set boundary result before querying tag names when the input is empty. | L18.09 | F | 1/2 each | [TagsRepositoryImpl.kt:57][V303]; [TagDao.kt:43][V304] |
| 2 | Share parent-tag lookup preparation between single/bulk paths without losing per-link ordering and deduplication; reuse parsed _id and attachedTo values during tag mapping. | L18.09 | F | 1/2 each | [TagsRepositoryImpl.kt:76][V305]; [TagsRepositoryImpl.kt:101][V306]; [TagsRepositoryImpl.kt:130][V307] |

<a id="t092"></a>

### T092 — Use tagged diagnostics for sync failures

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **70/100** | 97 / 40 / 90 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace TransactionSyncManager printStackTrace calls with operation-specific tagged diagnostics, retaining cancellation rethrows and existing failure results. Avoid logging credentials or full payloads. | L03.05 | C | 1 each | [TransactionSyncManager.kt:135][V308]; [TransactionSyncManager.kt:187][V309]; [TransactionSyncManager.kt:318][V310]; [TransactionSyncManager.kt:465][V311] |

<a id="t093"></a>

### T093 — Remove unused logging instrumentation from worker and download tests

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **70/100** | 95 / 40 / 94 | D Codex, E Claude Code | D=1/8; E=7/8 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove unasserted Log instrumentation from FreeSpaceWorkerTest, retaining required FileUtils isolation and targeted cleanup. | L16.04, L17.10 | D, E | 1/8 each | [FreeSpaceWorkerTest.kt:63][V312]; [FreeSpaceWorkerTest.kt:69][V313] |
| 2 | Remove unasserted Log instrumentation from RetryQueueWorkerTest, retaining WorkManager/application mocks. | L17.10 | E | 1/4 each | [RetryQueueWorkerTest.kt:65][V314]; [RetryQueueWorkerTest.kt:68][V315] |
| 3 | Remove unasserted Log instrumentation from DownloadServiceTest, retaining ContextCompat/DownloadUtils mocks. | L17.10 | E | 1/4 each | [DownloadServiceTest.kt:62][V316]; [DownloadServiceTest.kt:66][V317] |
| 4 | Remove unasserted Log instrumentation from DownloadServiceOnDownloadCompleteTest without dropping its required FileUtils/DownloadUtils teardown. | L17.10 | E | 1/4 each | [DownloadServiceOnDownloadCompleteTest.kt:44][V318]; [DownloadServiceOnDownloadCompleteTest.kt:50][V319] |

<a id="t094"></a>

### T094 — Precompute resource facet comparison keys

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **69/100** | 90 / 50 / 70 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Precompute selected language, subject, and level criteria once per filter pass. Use comparison keys proven equivalent to the existing ignoreCase matching, including non-ASCII edge cases; retain all-versus-any semantics and null behavior. | L06.05 | F | 1 each | [ResourcesListFilter.kt:98][V320]; [ResourcesListFilter.kt:107][V321]; [ResourcesListFilter.kt:116][V322] |

<a id="t095"></a>

### T095 — Separate optional personal-owner filtering and alias resolution

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **69/100** | 93 / 45 / 78 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use separate all-owner and exact-owner title-count queries so a real user ID can use its index; preserve title NOCASE and null/empty-owner behavior. | L08.04 | B | 1/2 each | [PersonalDao.kt:15][V323]; [Personal.kt:7][V324] |
| 2 | Resolve all matching local/remote IDs before bounded positive-ID delete/update operations. Preserve the current match-all behavior when the two keys identify different rows and execute multi-step writes transactionally. | L08.04 | B | 1/2 each | [PersonalDao.kt:32][V325]; [PersonalDao.kt:41][V326] |

<a id="t096"></a>

### T096 — Index voice-feed leaders without changing first-match semantics

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **69/100** | 95 / 40 / 88 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Build an adapter-lifetime index from leader name to the first matching leader, including nullable names where the current equality permits them. Retain the userModel-null guard. | L01.06 | A | 1 each | [VoicesAdapter.kt:72][V327]; [VoicesAdapter.kt:590][V328] |

<a id="t097"></a>

### T097 — Assemble ranked chat-search results in one destination

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **69/100** | 99 / 35 / 98 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Append title-prefix, title-contains, conversation-prefix, and conversation-contains buckets into one pre-sized result list; do the same for title-only results. Preserve ranking and stable order. | L04.08 | D | 1 each | [ChatSearch.kt:89][V329]; [ChatSearch.kt:110][V330] |

<a id="t098"></a>

### T098 — Use the user-read contract for Settings guest detection

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **69/100** | 99 / 35 / 98 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace SettingsViewModel UserSessionManager injection with UserReadRepository and preserve its exact guest-ID predicate. Update constructor tests and retain the existing singleton Hilt binding. | L12.01 | F | 1 each | [SettingsViewModel.kt:25][V331]; [SettingsViewModel.kt:41][V332]; [UserSessionManager.kt:28][V333] |

<a id="t099"></a>

### T099 — Remove the RecyclerView dependency from stable ID generation

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **69/100** | 99 / 35 / 99 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace the RecyclerView.NO_ID reference with a local -1L constant and remove the import. Preserve FNV hashing, overflow behavior, synchronized fallback allocation, and all caller-visible IDs. | L17.05 | E | 1 each | [StableIdGenerator.kt:3][V334]; [StableIdGenerator.kt:11][V335] |

<a id="t100"></a>

### T100 — Reuse constant voice-label membership data

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **68/100** | 99 / 35 / 96 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Cache the constant label-value set, pass already-resolved labels into visibility calculation, and short-circuit too-short lists before building a used-label set. Preserve permission checks and duplicate-label semantics. | L06.10 | F | 1 each | [VoicesLabelManager.kt:91][V336]; [VoicesLabelManager.kt:108][V337] |

<a id="t101"></a>

### T101 — Inject BroadcastService into the notification action receiver

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **68/100** | 99 / 35 / 97 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Field-inject BroadcastService through the existing AndroidEntryPoint and replace getBroadcastService(context). Preserve system/local broadcast actions, extras, threading, and receiver completion. | L09.10 | C | 1 each | [NotificationActionReceiver.kt:21][V338]; [NotificationActionReceiver.kt:110][V339]; [BroadcastService.kt:10][V340] |

<a id="t102"></a>

### T102 — Move the saved-login profile lookup into LoginViewModel

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **68/100** | 99 / 35 / 93 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Have LoginViewModel resolve the current profile/name needed by saveUsers rather than LoginActivity reading profileDbHandler on its behalf. Keep password encryption and Activity-owned UI behavior at their existing boundary. | L11.09 | E | 1 each | [LoginActivity.kt:677][V341]; [LoginActivity.kt:680][V342]; [LoginViewModel.kt:69][V343] |

<a id="t103"></a>

### T103 — Stream CSV speech formatting into one builder

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **68/100** | 99 / 35 / 97 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Build the same speech string directly without drop/map/join intermediate lists. Preserve row numbering, ragged-column fallback headers, punctuation, empty rows, and header-only output. | L06.09 | F | 1 each | [TTSManager.kt:119][V344] |

<a id="t104"></a>

### T104 — Ask the resource contract for next-step resources needing download

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **67/100** | 97 / 35 / 93 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Expose the next-step not-offline selection on ResourcesRepository, using a default implementation if the existing implementation must remain untouched. Keep the exact resourceOffline/revision rule and leave URL handoff in CoursesStepsViewModel. | L08.09 | B | 1 each | [CoursesStepsViewModel.kt:125][V020]; [ResourcesRepository.kt:76][V345]; [MyLibrary.kt:144][V346] |

<a id="t105"></a>

### T105 — Use tagged diagnostics for attachment upload failures

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **67/100** | 97 / 35 / 92 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace FileUploader.uploadDoc stack dumps with a tagged diagnostic containing safe operation context, retaining listener/result behavior and cancellation handling. | L03.06 | C | 1 each | [FileUploader.kt:64][V347] |

<a id="t106"></a>

### T106 — Expose community identifiers and title as derived state

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **65/100** | 99 / 30 / 97 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Add distinct planet/community view-in IDs and the existing planet-title fallback to CommunityTabState. Have both hosts consume the appropriate ID while preserving their different titles and filtering identities. | L11.08 | E | 1 each | [CommunityTabFragment.kt:23][V348]; [CommunityTabFragment.kt:27][V349]; [HomeCommunityDialogFragment.kt:87][V350] |

<a id="t107"></a>

### T107 — Extract export exam IDs without splitting unused suffixes

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **65/100** | 99 / 30 / 99 | D Codex, F Antigravity | D=1/2; F=1/2 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use nullable substringBefore for getExamId, preserving null, empty, plain-ID, leading-delimiter, and multiple-delimiter results. | L04.09, L06.08 | D, F | 1/2 each | [SubmissionsRepositoryExporter.kt:307][V351] |

<a id="t108"></a>

### T108 — Replace redundant TextUtils checks in health item mapping

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **65/100** | 99 / 30 / 98 | F Antigravity | F=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use Kotlin isNotEmpty and equality for the already non-null createdBy string. Preserve examiner resolution and self-examination behavior; remove only the direct TextUtils dependency. | L18.06 | F | 1 each | [HealthExaminationItemMapper.kt:19][V352]; [HealthExaminationItemMapper.kt:21][V353] |

<a id="t109"></a>

### T109 — Reuse the resource prefix-result buffer

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **65/100** | 99 / 30 / 99 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Append contains results to the private prefix-result list and return it rather than allocating a third list. Preserve empty-query list identity and prefix priority. | L16.09 | D | 1 each | [ResourcesSearchUtils.kt:7][V354]; [ResourcesSearchUtils.kt:13][V355]; [ResourcesSearchUtils.kt:21][V356] |

<a id="t110"></a>

### T110 — Reuse English course-filter resources within one configuration

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **64/100** | 98 / 30 / 93 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Cache English configuration resources and grade/subject arrays for the current fragment configuration instead of recreating them on each spinner read. Preserve canonical persisted values and invalidate on configuration/view changes. | L13.08 | A | 1 each | [CoursesFilterFragment.kt:103][V357]; [CoursesFilterFragment.kt:116][V358] |

<a id="t111"></a>

### T111 — Report PDF export failures without changing the fallback

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **64/100** | 98 / 30 / 95 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace the two silent outer export catches with safe tagged diagnostics and retain their null result, document closure, and output-file cleanup behavior. | L13.04 | A | 1 each | [SubmissionsRepositoryExporter.kt:130][V359]; [SubmissionsRepositoryExporter.kt:242][V360] |

<a id="t112"></a>

### T112 — Share task-to-team-name batch mapping

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **64/100** | 96 / 30 / 95 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Extract the common team-ID collection and name-map construction from the ID- and title-keyed notification helpers. Preserve empty-input returns, ordering, and filtering. | L01.10 | A | 1 each | [NotificationsRepositoryImpl.kt:255][V361]; [NotificationsRepositoryImpl.kt:318][V362] |

<a id="t113"></a>

### T113 — Use controlled diagnostics for malformed model JSON

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **63/100** | 96 / 30 / 88 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace Feedback and HealthExamination serializer stack dumps with controlled reporting while retaining their malformed-message/condition fallbacks. Keep Android reporting outside portable model logic when introducing a reporting seam. | L03.10 | C | 1 each | [Feedback.kt:103][V363]; [HealthExamination.kt:69][V364] |

<a id="t114"></a>

### T114 — Use a larger bounded checksum read buffer

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **62/100** | 90 / 30 / 94 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Increase the SHA-512 file-reading buffer from DEFAULT_BUFFER_SIZE to a modest larger constant such as 64 KiB. Retain the algorithm, digest representation, and missing-file behavior; compare representative file throughput before claiming a speedup. | L02.10 | B | 1 each | [Sha256Utils.kt:14][V365]; [Sha256Utils.kt:16][V366] |

<a id="t115"></a>

### T115 — Move platform binding modules into the DI package

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **60/100** | 98 / 25 / 96 | E Claude Code | E=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Move DeviceNameModule and UserDataUploadSchedulerModule bindings into an appropriately scoped di module, retaining qualifiers, scopes, implementations, and graph uniqueness; remove obsolete module annotations/imports from the implementation files. | L11.07 | E | 1 each | [DeviceNameProvider.kt:24][V367]; [UserDataUploadScheduler.kt:67][V368] |

<a id="t116"></a>

### T116 — Remove duplicate float and double parse fallbacks

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **60/100** | 95 / 25 / 96 | D Codex | D=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use the typed nullable float/double conversion once and retain zero fallbacks, special values, and numeric-string behavior. Leave integer overflow semantics unchanged. | L04.10 | D | 1 each | [JsonUtils.kt:44][V369]; [JsonUtils.kt:49][V370] |

<a id="t117"></a>

### T117 — Remove redundant Android logging from typed personal-upload results

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **59/100** | 98 / 25 / 88 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove direct Android logging where PersonalUploadResult already conveys the failure and cause to its caller. Preserve AttachmentFailed/DocumentFailed outcomes and existing fallback details. | L09.04 | C | 1 each | [PersonalsRepositoryImpl.kt:3][V371]; [PersonalsRepositoryImpl.kt:141][V372]; [PersonalsRepositoryImpl.kt:155][V373] |

<a id="t118"></a>

### T118 — Choose a deterministic representative opened-resource row

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **59/100** | 95 / 25 / 90 | B Copilot / Grok | B=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Replace bare-column GROUP BY selection with a deterministic existing primary-key representative per resource, preserving the public Flow signature and resource-ID set. Do not change the separate most-opened aggregate or infer that the present consumer displays arbitrary representative titles. | L08.05 | B | 1 each | [ResourceActivityDao.kt:26][V374]; [ResourceActivityDao.kt:29][V375] |

<a id="t119"></a>

### T119 — Remove RetryRepositoryImpl direct Android logging dependency

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **57/100** | 96 / 25 / 80 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Use the existing persisted markFailed result as the reporting boundary and remove duplicate Android logging in executeOperation. Preserve error causes/messages and retry bookkeeping. | L09.02 | C | 1 each | [RetryRepositoryImpl.kt:3][V376]; [RetryRepositoryImpl.kt:131][V377]; [RetryRepositoryImpl.kt:135][V378] |

<a id="t120"></a>

### T120 — Memoize voice label display formatting with locale-aware bounds

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **56/100** | 90 / 25 / 80 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Cache fallback formatLabelValue results by raw label and locale, with an instance-lifetime bound; retain constant-name lookups and existing whitespace/case behavior. | L01.09 | A | 1 each | [VoicesLabelManager.kt:124][V379]; [VoicesLabelManager.kt:138][V380] |

<a id="t121"></a>

### T121 — Separate News model fallbacks from Android logging

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **54/100** | 94 / 25 / 65 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove direct Android logging from News parsing and sort fallbacks, preserving false/time/empty-field behavior and arranging malformed-data diagnostics at the parsing boundary where needed. Do not claim that removing an import alone makes the full dependency graph portable. | L09.05 | C | 1 each | [News.kt:3][V381]; [News.kt:220][V382]; [News.kt:237][V383]; [News.kt:311][V384] |

<a id="t122"></a>

### T122 — Resolve fixed feedback labels once per display configuration

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **54/100** | 96 / 20 / 85 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Pre-resolve the fixed LABELS strings for FeedbackAdapter, preserving Locale.ROOT key normalization and raw-value fallback; refresh when the display configuration changes. | L01.08 | A | 1 each | [FeedbackAdapter.kt:48][V385]; [FeedbackAdapter.kt:61][V386]; [FeedbackAdapter.kt:84][V387] |

<a id="t123"></a>

### T123 — Reuse constant exam-choice data and view-scoped inflation helpers

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **53/100** | 94 / 20 / 85 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Construct the constant Other choice once and reuse it read-only or copy before mutation; cache an inflater only for the current fragment view/context lifetime. | L01.05 | A | 1 each | [ExamTakingFragment.kt:579][V388]; [ExamTakingFragment.kt:599][V389]; [ExamTakingFragment.kt:609][V390] |

<a id="t124"></a>

### T124 — Cache hostUrl with explicit invalidation

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **52/100** | 85 / 25 / 65 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Cache the derived host URL under the existing generation discipline and clear it from every cache-reset path. Preserve URL switching, fallback parsing, suffix rules, and concurrent invalidation. Verify all preference setters invalidate before eliminating preference reads. | L01.04 | A | 1 each | [UrlUtils.kt:24][V391]; [UrlUtils.kt:38][V392]; [UrlUtils.kt:83][V393] |

<a id="t125"></a>

### T125 — Cache submission-row date formatting within the display configuration

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **51/100** | 90 / 20 / 75 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Cache repeated timestamp formatting in both submission adapters, bounding the cache and invalidating it when locale/time zone or the adapter data lifetime changes. | L01.07 | A | 1 each | [SubmissionsAdapter.kt:78][V394]; [SubmissionsListAdapter.kt:49][V395] |

<a id="t126"></a>

### T126 — Remove redundant date handling and quiet malformed meetup-day stack dumps

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **51/100** | 85 / 20 / 85 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove the redundant date-format catch only after retaining the formatter fallback. Keep the JSONArray exception boundary for malformed day data, replacing its printStackTrace with controlled diagnostics or the established fallback. | L03.09 | C | 1 each | [Meetup.kt:107][V396]; [Meetup.kt:118][V397]; [TimeUtils.kt:73][V398]; [TimeUtils.kt:88][V399] |

<a id="t127"></a>

### T127 — Hoist repeated fragment resource strings within each render

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **51/100** | 98 / 15 / 98 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Resolve the untitled-course fallback before mapping picker course names. | L13.10 | A | 1/2 each | [TeamCoursesFragment.kt:147][V400] |
| 2 | Resolve the empty health label once per render/function and reuse it for the repeated fields, preserving locale changes between renders. | L13.10 | A | 1/2 each | [MyHealthFragment.kt:150][V401]; [MyHealthFragment.kt:215][V402] |

<a id="t128"></a>

### T128 — Remove CommunityRepositoryImpl direct Android logging dependency

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **50/100** | 96 / 20 / 65 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Remove the direct android.util.Log use from community sync while retaining cancellation and the false failure result. Ensure existing caller reporting is sufficient before deleting the only operation diagnostic; keep any required reporter platform-independent. | L09.01 | C | 1 each | [CommunityRepositoryImpl.kt:3][V403]; [CommunityRepositoryImpl.kt:57][V404] |

<a id="t129"></a>

### T129 — Remove HealthRepositoryImpl direct Android logging dependency safely

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **49/100** | 94 / 25 / 45 | C Copilot / Kimi | C=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Separate platform logging from health parsing/upload/encryption fallbacks. Keep useful diagnostics at an existing caller or injected platform-neutral reporter rather than silently deleting the sole indication of a failed health operation. | L09.03 | C | 1 each | [HealthRepositoryImpl.kt:3][V405]; [HealthRepositoryImpl.kt:108][V406]; [HealthRepositoryImpl.kt:138][V407]; [HealthRepositoryImpl.kt:238][V408] |

<a id="t130"></a>

### T130 — Encapsulate the plain-community TeamSummary fallback

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **48/100** | 85 / 15 / 98 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Provide a named TeamSummary factory for the exact plain-community fallback and use it from ChatViewModel, retaining all current nullable/default fields. | L07.08 | A | 1 each | [ChatViewModel.kt:201][V409]; [TeamSummary.kt:3][V410] |

<a id="t131"></a>

### T131 — Name the network reachability delay accurately

| Rating | E / I / F | All proposing streams | Allocated task points |
| --- | --- | --- | --- |
| **44/100** | 99 / 10 / 99 | A Devin | A=1 |

| Scope | Retained work | Raw provenance | Eligible streams | Task point share | Verified source |
| --- | --- | --- | --- | --- | --- |
| 1 | Rename UPLOAD_DELAY_SECONDS to REACHABILITY_DELAY_SECONDS and update its only use without changing the delay or WorkManager replacement policy. | L13.09 | A | 1 each | [NetworkMonitorWorker.kt:26][V411]; [NetworkMonitorWorker.kt:61][V412] |

## Extraction ledger: all 180 raw submissions

“Corrected” means some action or factual claim was dropped/narrowed; its exact disposition appears below. Every original task has one row. Retained rows can cite multiple merged task IDs, and all same-stream duplicate occurrences remain visible. The original title is preserved; it is not an endorsement of a rejected premise.

| Raw ID | Stream | Original task title | Disposition | Shipped IDs | Allocated points | Quality points |
| --- | --- | --- | --- | --- | --- | --- |
| L01.01 | A | fetch team memberships once in `getJoinedMembersWithVisitInfo` (roadmap 1+7) | Corrected | [T078](#t078) | 1 | 0.710000 |
| L01.02 | A | hoist shared reads out of upload serializer lambdas (roadmap 5+7) | Dropped | — | 0 | 0.000000 |
| L01.03 | A | tighten the submissions read path: LIMIT 1 exam lookup and allocation-free flow dedup (roadmap 1+7) | Retained | [T090](#t090) | 1 | 0.700000 |
| L01.04 | A | cache `UrlUtils.hostUrl` like `baseUrl`/`header` (roadmap 7) | Corrected | [T124](#t124) | 1 | 0.520000 |
| L01.05 | A | hoist the constant `"Other"` choice parse and per-button inflater in `ExamTakingFragment` (roadmap 7) | Retained | [T123](#t123) | 1 | 0.530000 |
| L01.06 | A | index `leadersList` by name in `VoicesAdapter` (roadmap 7) | Corrected | [T096](#t096) | 1 | 0.690000 |
| L01.07 | A | cache formatted dates in the two submission list adapters (roadmap 7) | Retained | [T125](#t125) | 1 | 0.510000 |
| L01.08 | A | resolve `FeedbackAdapter` label strings once instead of per bind (roadmap 7) | Retained | [T122](#t122) | 1 | 0.540000 |
| L01.09 | A | memoize `formatLabelValue` in `VoicesLabelManager` (roadmap 7) | Corrected | [T120](#t120) | 1 | 0.560000 |
| L01.10 | A | collapse the duplicated task→team-name batch helpers in `NotificationsRepositoryImpl` (roadmap 1+8) | Retained | [T112](#t112) | 1 | 0.640000 |
| L02.01 | B | skip expired challenge queries on dashboard open (roadmap 7) | Retained | [T009](#t009) | 1/2 | 0.455000 |
| L02.02 | B | batch earned-exam pass lookups when a course opens (roadmap 1+7) | Corrected | [T024](#t024) | 1 | 0.850000 |
| L02.03 | B | parallelize independent course-detail reads (roadmap 1+7) | Retained | [T051](#t051) | 1 | 0.770000 |
| L02.04 | B | overlap user, course, steps, and progress on course open (roadmap 3+7) | Retained | [T026](#t026) | 7/24 | 0.245000 |
| L02.05 | B | index library models instead of scanning the full list (roadmap 7) | Retained | [T018](#t018) | 1/2 | 0.435000 |
| L02.06 | B | cache downloaded-file hits while course-step resources scroll (roadmap 7) | Dropped | — | 0 | 0.000000 |
| L02.07 | B | cache voice reaction JSON the way images are cached (roadmap 7) | Retained | [T055](#t055) | 1 | 0.760000 |
| L02.08 | B | precompute chat sort and revision keys (roadmap 7+9) | Corrected | [T079](#t079) | 1 | 0.710000 |
| L02.09 | B | lowercase user sort keys once per sort (roadmap 7+9) | Retained | [T058](#t058) | 1 | 0.750000 |
| L02.10 | B | enlarge the checksum read buffer (roadmap 7+9) | Retained | [T114](#t114) | 1 | 0.620000 |
| L03.01 | C | Gate the expired challenge-dialog evaluation before its five database queries (roadmap 7, supports 3) | Retained | [T009](#t009) | 1/2 | 0.455000 |
| L03.02 | C | Collapse the per-survey submission-existence loops into single COUNT queries (roadmap 1+7) | Retained | [T027](#t027) | 1 | 0.830000 |
| L03.03 | C | Memoize profile-image Base64 encoding behind UserEntity.serialize (roadmap 5+7, supports 9) | Corrected | [T077](#t077) | 1 | 0.710000 |
| L03.04 | C | Replace printStackTrace with structured logging in RetryUtils (roadmap 8) | Corrected | [T008](#t008) | 1/8 | 0.113750 |
| L03.05 | C | Replace printStackTrace in TransactionSyncManager catch blocks (roadmap 5+8) | Retained | [T092](#t092) | 1 | 0.700000 |
| L03.06 | C | Replace printStackTrace in FileUploader's upload catch (roadmap 5+8) | Retained | [T105](#t105) | 1 | 0.670000 |
| L03.07 | C | Stop swallowing CancellationException in HealthExaminationViewModel.saveExamination (roadmap 3+8) | Retained | [T014](#t014) | 1/6 | 0.150000 |
| L03.08 | C | Replace printStackTrace in the two course ViewModels (roadmap 3+8) | Retained | [T016](#t016) | 7/30 | 0.205333 |
| L03.09 | C | Drop the dead try/catch and printStackTrace calls in Meetup.getHashMap (roadmap 1+8, supports 9) | Corrected | [T126](#t126) | 1 | 0.510000 |
| L03.10 | C | Replace printStackTrace in the Feedback and HealthExamination serializers (roadmap 1+8, supports 9) | Retained | [T113](#t113) | 1 | 0.630000 |
| L04.01 | D | Bound text-preview reads before splitting into lines (roadmap 7+8) | Retained | [T011](#t011) | 1 | 0.910000 |
| L04.02 | D | Prepare course-detail paths and markdown off the main thread (roadmap 3+7+8) | Retained | [T030](#t030) | 1 | 0.830000 |
| L04.03 | D | Move notification grouping and flattening onto the injected compute dispatcher (roadmap 3+7+8) | Retained | [T035](#t035) | 1 | 0.810000 |
| L04.04 | D | Cancel obsolete course-step download preparation with its load job (roadmap 3+5+7+8) | Retained | [T007](#t007) | 1/2 | 0.455000 |
| L04.05 | D | Apply selective resource filters before ranked title search (roadmap 7+8) | Retained | [T034](#t034) | 1 | 0.810000 |
| L04.06 | D | Request only display-name metadata from content providers (roadmap 7+8) | Retained | [T038](#t038) | 1/4 | 0.200000 |
| L04.07 | D | Charge every PDF thumbnail a positive cache weight (roadmap 7+8) | Retained | [T020](#t020) | 1 | 0.870000 |
| L04.08 | D | Assemble chat-search ranking buckets without intermediate concatenations (roadmap 7+8) | Retained | [T097](#t097) | 1 | 0.690000 |
| L04.09 | D | Extract export exam IDs without splitting entire parent strings (roadmap 1+7+8) | Retained | [T107](#t107) | 1/2 | 0.325000 |
| L04.10 | D | Remove repeated failed floating-point parsing in Kotlin JSON accessors (roadmap 1+7+8) | Retained | [T116](#t116) | 1 | 0.600000 |
| L05.01 | E | Count survey questions in SQL instead of loading every question row (roadmap 1+7, moves 9) | Retained | [T023](#t023) | 1 | 0.860000 |
| L05.02 | E | Batch submission rev updates and upload marks into one transaction each (roadmap 5+7, moves 9) | Retained | [T019](#t019) | 1 | 0.870000 |
| L05.03 | E | Commit each upload batch's "mark uploaded" writes in one transaction (roadmap 5+7) | Corrected | [T025](#t025) | 1 | 0.850000 |
| L05.04 | E | Count course steps in SQL for progress bars instead of loading step bodies (roadmap 1+7, moves 9) | Retained | [T022](#t022) | 1 | 0.860000 |
| L05.05 | E | Stop loading every course step just to read a course title or id (roadmap 1+7, moves 9) | Retained | [T017](#t017) | 1 | 0.880000 |
| L05.06 | E | Drain the download-link buffers after each save instead of rewriting them forever (roadmap 5+7, moves 9) | Corrected | [T049](#t049) | 1 | 0.770000 |
| L05.07 | E | Cap the notification read sync at the file's existing concurrency limit (roadmap 5+7) | Retained | [T010](#t010) | 1 | 0.910000 |
| L05.08 | E | Open Team Voices with one feed load and a SQL count for the badge watermark (roadmap 3+7, moves 10) | Retained | [T031](#t031) | 1 | 0.830000 |
| L05.09 | E | Replace O(n²) id lookups in the Resources list with a map built once per list update (roadmap 7+8) | Corrected | [T018](#t018) | 1/2 | 0.435000 |
| L05.10 | E | Use the shared zoomable-image viewer in VoicesAdapter and hoist its per-bind dimension lookups (roadmap 6+7+8, moves 10) | Retained | [T071](#t071) | 1 | 0.720000 |
| L06.01 | F | replace multi-select answer dual-list sort with frequency-map comparison (roadmap 7+8) | Retained | [T064](#t064) | 1 | 0.740000 |
| L06.02 | F | parallelize independent course, user, step, and progress reads in TakeCourseViewModel.loadCourse (roadmap 3+7) | Corrected | [T026](#t026) | 7/24 | 0.245000 |
| L06.03 | F | parallelize joined-members and current-user reads in RequestsViewModel.loadJoinedMembers (roadmap 3+7) | Retained | [T068](#t068) | 1 | 0.730000 |
| L06.04 | F | parallelize team, enterprise, and community summary queries in ChatViewModel.loadShareTargets (roadmap 3+7) | Retained | [T045](#t045) | 3/4 | 0.585000 |
| L06.05 | F | pre-normalize facet filter sets once per pass in ResourcesListFilter (roadmap 3+7) | Corrected | [T094](#t094) | 1 | 0.690000 |
| L06.06 | F | avoid full-list remap and recount on single-item toggle in StorageCategoryViewModel (roadmap 3+7) | Retained | [T089](#t089) | 1 | 0.700000 |
| L06.07 | F | reuse precomputed group unread state and normalize group keys in NotificationsViewModel.toggleGroupExpansion (roadmap 3+7+8) | Retained | [T021](#t021) | 1 | 0.860000 |
| L06.08 | F | eliminate per-word string concatenation and duplicate text measurement in SubmissionsRepositoryExporter (roadmap 1+7) | Corrected | [T107](#t107) | 1/2 | 0.325000 |
| L06.09 | F | stream CSV speech formatting into a single StringBuilder in TTSManager.formatCsvForSpeech (roadmap 7+8) | Retained | [T103](#t103) | 1 | 0.680000 |
| L06.10 | F | short-circuit label set allocations in VoicesLabelManager.updateAddLabelVisibility and cache label values (roadmap 7+8) | Retained | [T100](#t100) | 1 | 0.680000 |
| L07.01 | A | Re-scope sync-only user reads onto UserSyncRepository and rewire UploadToShelfService (roadmap 1, 4, 9) | Corrected | [T087](#t087) | 1 | 0.710000 |
| L07.02 | A | Hoist profile/sync-status reads onto UserReadRepository and slim three view models (roadmap 1, 4, 9) | Corrected | [T080](#t080) | 1 | 0.710000 |
| L07.03 | A | Point five read-only view models at UserReadRepository — batch A (roadmap 4, 9) | Corrected | [T061](#t061) | 17/60 | 0.212500 |
| L07.04 | A | Point five read-only view models at UserReadRepository — batch B (roadmap 4, 9) | Retained | [T053](#t053), [T061](#t061), [T062](#t062) | 23/45 | 0.385333 |
| L07.05 | A | Consolidate guest-login and authenticate orchestration into LoginViewModel (roadmap 3, 9) | Retained | [T066](#t066) | 1 | 0.730000 |
| L07.06 | A | Push the blank-community filter into the DAO query (roadmap 1, 7, 9) | Retained | [T073](#t073) | 1/2 | 0.360000 |
| L07.07 | A | Replace the team-notification update-then-insert pair with a single upsert (roadmap 1, 7, 9) | Dropped | — | 0 | 0.000000 |
| L07.08 | A | Narrow ChatViewModel to TeamsShareRepository + UserReadRepository and lift the community-summary fallback into the model (roadmap 1, 3, 9) | Corrected | [T130](#t130) | 1 | 0.480000 |
| L07.09 | A | Push patient-list sorting into SQLite ORDER BY (roadmap 1, 7, 9) | Dropped | — | 0 | 0.000000 |
| L07.10 | A | Point the remaining read-only course view models at UserReadRepository (roadmap 4, 9) | Retained | [T060](#t060) | 7/36 | 0.145833 |
| L08.01 | B | split user id lookups so each side can use its index (roadmap 1+7+9) | Dropped | — | 0 | 0.000000 |
| L08.02 | B | split team id lookup and member-count existence check (roadmap 1+7) | Dropped | — | 0 | 0.000000 |
| L08.03 | B | make my-life user lookups use the userId index (roadmap 1+7+9) | Retained | [T047](#t047) | 1 | 0.780000 |
| L08.04 | B | make personal title-count and id updates index-friendly (roadmap 1+7) | Corrected | [T095](#t095) | 1 | 0.690000 |
| L08.05 | B | make opened-resource observation return one stable row per resource (roadmap 1+7+8) | Corrected | [T118](#t118) | 1 | 0.590000 |
| L08.06 | B | wrap stale exam-question deletes in one transaction (roadmap 1+7) | Retained | [T004](#t004) | 1/6 | 0.155000 |
| L08.07 | B | stop community services depending on the fat teams repository (roadmap 1+4+9) | Retained | [T085](#t085) | 1 | 0.710000 |
| L08.08 | B | stop my-life from depending on the write-capable user repository (roadmap 1+3+9) | Retained | [T075](#t075) | 1/2 | 0.360000 |
| L08.09 | B | ask the resources repository which next-step files still need download (roadmap 1+3+10) | Retained | [T104](#t104) | 1 | 0.670000 |
| L08.10 | B | point read-only screens at UserReadRepository (roadmap 1+4+10) | Retained | [T053](#t053), [T061](#t061) | 4/15 | 0.202000 |
| L09.01 | C | strip android.util.Log from CommunityRepositoryImpl (roadmap 1+9) | Retained | [T128](#t128) | 1 | 0.500000 |
| L09.02 | C | strip android.util.Log from RetryRepositoryImpl (roadmap 1+9) | Retained | [T119](#t119) | 1 | 0.570000 |
| L09.03 | C | strip android.util.Log from HealthRepositoryImpl (roadmap 1+9) | Corrected | [T129](#t129) | 1 | 0.490000 |
| L09.04 | C | strip android.util.Log from PersonalsRepositoryImpl (roadmap 1+9) | Retained | [T117](#t117) | 1 | 0.590000 |
| L09.05 | C | strip android.util.Log from the News model (roadmap 1+9) | Retained | [T121](#t121) | 1 | 0.540000 |
| L09.06 | C | hoist task filtering and assignee filtering out of TeamsTasksFragment into TeamsTasksViewModel (roadmap 3+10, helps 1) | Retained | [T048](#t048) | 1 | 0.780000 |
| L09.07 | C | extract survey search matching out of SurveysViewModel into a tested pure component (roadmap 3+8) | Retained | [T065](#t065) | 1 | 0.740000 |
| L09.08 | C | drop the redundant re-query in EventsRepositoryImpl.toggleAttendance (roadmap 7+1) | Retained | [T074](#t074) | 1 | 0.720000 |
| L09.09 | C | resolve latest chat revision with a SQL MAX query instead of loading all revs (roadmap 7+1) | Dropped | — | 0 | 0.000000 |
| L09.10 | C | inject BroadcastService into NotificationActionReceiver instead of the EntryPoint lookup (roadmap 4) | Retained | [T101](#t101) | 1 | 0.680000 |
| L10.01 | D | Enforce caller ownership when reordering My Life items (roadmap 1+8) | Retained | [T002](#t002) | 1 | 0.940000 |
| L10.02 | D | Bound stale course-step deletion by actual stale IDs (roadmap 1+5+7+8) | Retained | [T006](#t006) | 1 | 0.920000 |
| L10.03 | D | Bound stale exam-question deletion within each exam (roadmap 1+5+7+8) | Retained | [T004](#t004) | 2/3 | 0.620000 |
| L10.04 | D | Propagate cancellation from submission payload assembly (roadmap 1+5+8) | Retained | [T001](#t001) | 1/4 | 0.237500 |
| L10.05 | D | Resolve dictionary readiness from persisted data before checking the seed file (roadmap 1+3+8) | Retained | [T005](#t005) | 1/4 | 0.232500 |
| L10.06 | D | Narrow submission-detail author lookup to the existing user-read contract (roadmap 1+3+4+8) | Retained | [T053](#t053) | 1/10 | 0.077000 |
| L10.07 | D | Restrict resource access and viewer identity checks to user reads (roadmap 1+3+4+8) | Retained | [T062](#t062) | 5/18 | 0.208333 |
| L10.08 | D | Limit course-taking identity dependencies to read access (roadmap 1+3+4+8) | Retained | [T060](#t060) | 7/36 | 0.145833 |
| L10.09 | D | Remove broad account capabilities from dashboard helper view models (roadmap 1+3+4+8) | Retained | [T061](#t061) | 7/60 | 0.087500 |
| L10.10 | D | Narrow personal and library screen identity dependencies (roadmap 1+3+4+8) | Retained | [T061](#t061), [T062](#t062) | 2/15 | 0.100000 |
| L11.01 | E | Narrow home-screen ViewModels from UserRepository to UserReadRepository (roadmap 1+3, moves 9) | Retained | [T061](#t061) | 4/15 | 0.200000 |
| L11.02 | E | Narrow learning/resource ViewModels from UserRepository to UserReadRepository (roadmap 1+3, moves 9) | Retained | [T053](#t053), [T060](#t060), [T062](#t062) | 22/45 | 0.368667 |
| L11.03 | E | Remove Android Context and R.string from NotificationsViewModel (roadmap 3+6, moves 10) | Retained | [T052](#t052) | 1/2 | 0.385000 |
| L11.04 | E | Stop LifeViewModel reading the My Life list twice per open (roadmap 1+3+7) | Corrected | [T072](#t072) | 1 | 0.720000 |
| L11.05 | E | Move the blank-community filter from the server dialog into CommunityDao SQL (roadmap 1+7, moves 9) | Retained | [T073](#t073) | 1/2 | 0.360000 |
| L11.06 | E | Migrate the last free Mockito unit tests to MockK (roadmap 8) | Corrected | [T082](#t082) | 1 | 0.710000 |
| L11.07 | E | Move the two Hilt modules declared outside di/ into one di/ module (roadmap 4) | Retained | [T115](#t115) | 1 | 0.600000 |
| L11.08 | E | Hoist community view ids and title into CommunityTabState (roadmap 3+6, moves 10) | Retained | [T061](#t061), [T106](#t106) | 16/15 | 0.700000 |
| L11.09 | E | Move the saved-user lookup out of LoginActivity into LoginViewModel (roadmap 3, moves 9) | Retained | [T102](#t102) | 1 | 0.680000 |
| L11.10 | E | Let ChatViewModel resolve the current chat user instead of ChatHistoryFragment reading prefs (roadmap 1+3, moves 9) | Retained | [T081](#t081) | 1/2 | 0.355000 |
| L12.01 | F | replace UserSessionManager dependency in SettingsViewModel with UserReadRepository (roadmap 1+3+4) | Retained | [T098](#t098) | 1 | 0.690000 |
| L12.02 | F | remove unused UserSessionManager injection from TakeCourseFragment and narrow TakeCourseViewModel to UserReadRepository (roadmap 1+3+4) | Retained | [T060](#t060) | 5/12 | 0.312500 |
| L12.03 | F | move active-user lookup from ChatHistoryFragment into ChatViewModel via UserReadRepository (roadmap 1+3+8) | Retained | [T081](#t081) | 1/2 | 0.355000 |
| L12.04 | F | check database population before file existence in DictionaryRepositoryImpl and remove duplicate count query in DictionaryActivity (roadmap 1+3+7) | Retained | [T005](#t005) | 3/4 | 0.697500 |
| L12.05 | F | replace pre-update SELECT with UPDATE row-count check in MyLifeDao.updateVisibility (roadmap 1+7+8) | Retained | [T076](#t076) | 1 | 0.720000 |
| L12.06 | F | wrap TeamLogDao.deleteByIds in @Transaction and short-circuit on empty input (roadmap 1+7+8) | Corrected | [T063](#t063) | 1 | 0.750000 |
| L12.07 | F | wrap QuestionDao.deleteStaleQuestions in @Transaction and guard empty input (roadmap 1+7+8) | Retained | [T004](#t004) | 1/6 | 0.155000 |
| L12.08 | F | narrow ResourcesViewModel user dependency to UserReadRepository and move in-memory sorting to default dispatcher (roadmap 1+3+7) | Retained | [T037](#t037), [T062](#t062) | 1/3 | 0.262500 |
| L12.09 | F | narrow SubmissionDetailViewModel to UserReadRepository and collapse duplicate stateIn pipeline (roadmap 1+3+7) | Retained | [T053](#t053) | 3/5 | 0.462000 |
| L12.10 | F | promote getCurrentUserId to UserReadRepository and narrow LifeViewModel off UserRepository (roadmap 1+3+4) | Corrected | [T075](#t075) | 1/2 | 0.360000 |
| L13.01 | A | Replace `printStackTrace` calls with `Log.w` in production files (roadmap 8, feeds 9/10) | Corrected | [T001](#t001), [T008](#t008), [T014](#t014), [T015](#t015) | 5/6 | 0.763333 |
| L13.02 | A | Parallelize `TakeCourseViewModel.loadCourse` and fix its `printStackTrace` (roadmap 7, 3) | Retained | [T016](#t016), [T026](#t026) | 23/120 | 0.163667 |
| L13.03 | A | Parallelize `ChatViewModel.loadShareTargets` (roadmap 7, 3) | Retained | [T045](#t045) | 1/4 | 0.195000 |
| L13.04 | A | Batch exam lookups and log swallowed catches in `SubmissionsRepositoryExporter` (roadmap 1, 7) | Corrected | [T111](#t111) | 1 | 0.640000 |
| L13.05 | A | Collapse three `gh pr view` calls into one in `automerge.sh` (roadmap 8 — workflow quick win) | Retained | [T028](#t028) | 1/2 | 0.415000 |
| L13.06 | A | Hoist static mocks out of `LoginSyncManagerTest.setup` (roadmap 8 — test speed) | Retained | [T036](#t036) | 1/6 | 0.133333 |
| L13.07 | A | Hoist static mocks out of `UserRepositoryImplTest.setup` (roadmap 8 — test speed) | Retained | [T033](#t033) | 1/4 | 0.205000 |
| L13.08 | A | Cache `englishValues` locale contexts in `CoursesFilterFragment` (roadmap 7) | Retained | [T110](#t110) | 1 | 0.640000 |
| L13.09 | A | Rename and de-stale `UPLOAD_DELAY_SECONDS` in `NetworkMonitorWorker` (roadmap 5, 8) | Corrected | [T131](#t131) | 1 | 0.440000 |
| L13.10 | A | Hoist invariant `getString`/`Calendar` calls out of hot lambdas (roadmap 7) | Corrected | [T127](#t127) | 1 | 0.510000 |
| L14.01 | B | save a master debug build cache so PR builds stop cold-compiling (roadmap 7) | Corrected | [T057](#t057) | 1 | 0.750000 |
| L14.02 | B | move the retry Intent into BroadcastService and drop the Robolectric event test (roadmap 7+9) | Retained | [T040](#t040) | 5/6 | 0.658333 |
| L14.03 | B | query only the display-name column when resolving a content URI (roadmap 7) | Retained | [T038](#t038) | 1/2 | 0.400000 |
| L14.04 | B | build the news-sync Room database once per test class (roadmap 7+8) | Retained | [T050](#t050) | 2/3 | 0.513333 |
| L14.05 | B | rewrite course-step markdown off the main dispatcher (roadmap 7+10) | Retained | [T042](#t042) | 1 | 0.790000 |
| L14.06 | B | run the team course picker off the main dispatcher (roadmap 7+10) | Retained | [T046](#t046) | 1 | 0.780000 |
| L14.07 | B | stop the playstore quota script from downloading full release documents (roadmap 7) | Retained | [T088](#t088) | 1 | 0.700000 |
| L14.08 | B | replace the automerge publish-failure N+1 with one check-run list (roadmap 7) | Retained | [T044](#t044) | 1 | 0.780000 |
| L14.09 | B | stop ImageUtils clear-tests from decoding ole_logo under a spy (roadmap 7+8) | Retained | [T056](#t056) | 1 | 0.760000 |
| L14.10 | B | drop the unused TextUtils static mock from StepExamTest (roadmap 7+8) | Retained | [T043](#t043) | 1/4 | 0.197500 |
| L15.01 | C | stop swallowing coroutine cancellation in RetryUtils.retry (roadmap 8+9) | Retained | [T008](#t008) | 3/8 | 0.341250 |
| L15.02 | C | merge the single-test Robolectric class RetryInterceptorEventTest into plain JUnit RetryInterceptorTest (roadmap 8, unblocks 9) | Corrected | [T040](#t040) | 1/6 | 0.131667 |
| L15.03 | C | hoist per-test static mocks in UserRepositoryImplTest to class-level setup (roadmap 8) | Retained | [T033](#t033) | 3/4 | 0.615000 |
| L15.04 | C | hoist per-test static mocks in LoginSyncManagerTest to class-level setup (roadmap 8) | Retained | [T036](#t036) | 1/6 | 0.133333 |
| L15.05 | C | hoist per-test static mocks in VoicesRepositoryNewsSyncTest to class-level setup (roadmap 8) | Retained | [T050](#t050) | 1/6 | 0.128333 |
| L15.06 | C | parallelize the independent loads in TakeCourseViewModel.loadCourse (roadmap 3+7+8) | Retained | [T016](#t016), [T026](#t026) | 17/40 | 0.362333 |
| L15.07 | C | replace printStackTrace with tagged logging and rethrow cancellation in two viewmodels (roadmap 8) | Retained | [T014](#t014), [T015](#t015) | 7/12 | 0.520833 |
| L15.08 | C | match SubmissionsPayloadBuilder's own cancellation pattern in submissionPayload (roadmap 1+8) | Retained | [T001](#t001) | 1/2 | 0.475000 |
| L15.09 | C | rethrow cancellation instead of Result.retry() in NetworkMonitorWorker (roadmap 5+8) | Dropped | — | 0 | 0.000000 |
| L15.10 | C | inject TimeProvider into PublicSurveyViewModel instead of System.currentTimeMillis (roadmap 3+4) | Retained | [T039](#t039) | 1/2 | 0.400000 |
| L16.01 | D | Cancel obsolete course-step download preparation with its load (roadmap 3+5+7) | Retained | [T007](#t007) | 1/2 | 0.455000 |
| L16.02 | D | Coalesce active team-course loads and cancel superseded requests (roadmap 3+7) | Retained | [T013](#t013) | 1 | 0.900000 |
| L16.03 | D | Install Voices sync test object mocks once per test class (roadmap 5+8) | Retained | [T050](#t050) | 1/6 | 0.128333 |
| L16.04 | D | Remove unnecessary logging instrumentation from free-space tests (roadmap 7+8) | Retained | [T093](#t093) | 1/8 | 0.087500 |
| L16.05 | D | Reuse class-level exam test instrumentation without leaking stubs (roadmap 1+8) | Retained | [T043](#t043) | 1/4 | 0.197500 |
| L16.06 | D | Amortize image-encoding test static mocks while restoring application state (roadmap 1+8) | Retained | [T070](#t070) | 1 | 0.720000 |
| L16.07 | D | Hoist reachability-worker test mocks while retaining logging assertions (roadmap 5+8) | Retained | [T067](#t067) | 1 | 0.730000 |
| L16.08 | D | Request only the display-name column when resolving picked files (roadmap 7+8) | Retained | [T038](#t038) | 1/4 | 0.200000 |
| L16.09 | D | Reuse the prefix-result buffer instead of allocating a third search list (roadmap 7+8) | Retained | [T109](#t109) | 1 | 0.650000 |
| L16.10 | D | Index test source filenames once during timing diagnostics (roadmap 7+8) | Retained | [T084](#t084) | 1 | 0.710000 |
| L17.01 | E | Await the health upload inside the workers instead of firing and forgetting it into the app scope (roadmap 5 + 7, moves 9) | Retained | [T003](#t003) | 1 | 0.930000 |
| L17.02 | E | Use the injected application scope in SyncActivity instead of `MainApplication` statics (roadmap 4) | Retained | [T083](#t083) | 1 | 0.710000 |
| L17.03 | E | Sort resources on the default dispatcher with one hop, and stop swallowing cancellation (roadmap 7 + 3, moves 10) | Retained | [T015](#t015), [T037](#t037) | 7/6 | 0.970833 |
| L17.04 | E | Parse chat conversations on the default dispatcher (roadmap 7, moves 9) | Retained | [T059](#t059) | 1 | 0.750000 |
| L17.05 | E | Make StableIdGenerator platform-free (roadmap 8, moves 9) | Retained | [T099](#t099) | 1 | 0.690000 |
| L17.06 | E | Bind BaseContainerFragment's view-touching coroutines to the view lifecycle (roadmap 8 + 7) | Retained | [T012](#t012) | 1 | 0.900000 |
| L17.07 | E | Stop RetryUtils.retry from retrying and swallowing a cancelled coroutine (roadmap 8, moves 9) | Retained | [T008](#t008) | 1/4 | 0.227500 |
| L17.08 | E | Give TakeCourseViewModel and HealthExaminationViewModel cancellation-safe error paths (roadmap 3 + 8) | Retained | [T014](#t014), [T016](#t016) | 9/10 | 0.798667 |
| L17.09 | E | Move notification text formatting off the ViewModel's Context (roadmap 3 + 6, moves 10) | Retained | [T052](#t052) | 1/2 | 0.385000 |
| L17.10 | E | Drop redundant `android.util.Log` static mocks from four test classes (roadmap 8, CI wall-clock) | Corrected | [T093](#t093) | 7/8 | 0.612500 |
| L18.01 | F | eliminate duplicate release API fetch in playstore quota forecast (roadmap 5+8) | Retained | [T032](#t032) | 1 | 0.830000 |
| L18.02 | F | defer collaborator API pagination until non-filtered coauthors exist (roadmap 8) | Retained | [T041](#t041) | 1 | 0.790000 |
| L18.03 | F | batch PR state, review, and mergeable checks in automerge script (roadmap 8) | Corrected | [T028](#t028) | 1/2 | 0.415000 |
| L18.04 | F | drop non-default SDK 28 Robolectric pin in PdfThumbnailLoaderTest (roadmap 7+8) | Retained | [T069](#t069) | 1 | 0.730000 |
| L18.05 | F | remove redundant per-test static and object mocks in unit tests (roadmap 7+8) | Retained | [T036](#t036), [T043](#t043), [T086](#t086) | 13/6 | 1.638333 |
| L18.06 | F | remove android TextUtils dependency from HealthExaminationItemMapper (roadmap 3+8, advances 9+10) | Corrected | [T108](#t108) | 1 | 0.650000 |
| L18.07 | F | inject TimeProvider into PublicSurveyViewModel instead of System.currentTimeMillis (roadmap 3+4+8, advances 9+10) | Corrected | [T039](#t039) | 1/2 | 0.400000 |
| L18.08 | F | combine dual ID/remoteId lookup and use injected Gson in FeedbackRepositoryImpl (roadmap 1+7, advances 9) | Corrected | [T054](#t054) | 1 | 0.760000 |
| L18.09 | F | deduplicate single-link tag lookup and short-circuit empty tag names in TagsRepositoryImpl (roadmap 1+7, advances 9) | Retained | [T091](#t091) | 1 | 0.700000 |
| L18.10 | F | skip redundant user upserts and remove TextUtils in UserRepositoryImpl and HealthExaminationViewModel (roadmap 1+3+7, advances 9) | Retained | [T014](#t014), [T029](#t029) | 7/6 | 0.980000 |

## Entire submissions dropped: 8

| Raw ID | Reason | Working-tree evidence |
| --- | --- | --- |
| L01.02 | The hotspot is real, but the proposed per-run capture/memoization cannot be scoped inside the existing serializer lambda: UploadConfigs is a singleton and its configs are val instances reused between runs. Capturing at construction risks stale user/device/server data; changing the lifecycle plumbing is expressly excluded. | [UploadConfigs.kt:32][V413]; [UploadConfigs.kt:52][V414]; [UploadConfig.kt:48][V415] |
| L02.06 | A positive filesystem-hit cache invalidated only by list/address changes and detach cannot preserve the existing fresh existence check after a file is deleted or replaced without a list change. The list/revision key does not establish file existence. | [InlineResourceAdapter.kt:164][V416]; [InlineResourceAdapter.kt:187][V417] |
| L07.07 | The proposed read-modify-Upsert does not make the natural-key update atomic: TeamNotification uses a random UUID primary key and has no unique (parentId,type) constraint. Concurrent absent-row reads still create two different primary keys; it also keeps a read plus a write rather than one operation. | [TeamNotification.kt:12][V418]; [NotificationsRepositoryImpl.kt:342][V419]; [TeamNotificationDao.kt:11][V420] |
| L07.09 | SQLite COLLATE NOCASE is not equivalent to the existing Kotlin Unicode-aware lowercase/string ordering, including null/empty and Unicode cases. The proposed SQL rewrite changes the patient-list sort contract without an equivalent collation; precomputed Kotlin keys already address the verified repeated normalization. | [UserRepositoryImpl.kt:339][V192]; [UserDao.kt:20][V421] |
| L08.01 | The blanket premise that this indexed OR prevents either index is false. With the checked-in primary/remote-ID index structure, local EXPLAIN QUERY PLAN chooses MULTI-INDEX OR for both scalar and IN lookups. A new primary-key preference also changes collision behavior without an established contract. | [UserDao.kt:11][V422]; [UserDao.kt:13][V423]; [UserEntity.kt:26][V424] |
| L08.02 | Both team-key columns are indexed; the local reproduction chooses MULTI-INDEX OR, disproving the asserted index prohibition. Splitting a user-key EXISTS likewise needs a demonstrated plan benefit and must not double-count matches. No evidence establishes the proposed new key preference. | [TeamDao.kt:21][V425]; [TeamDao.kt:35][V426]; [MyTeam.kt:20][V427]; [UserEntity.kt:26][V424] |
| L09.09 | The suggested CAST/substr query does not preserve the existing maxByOrNull/toIntOrNull contract: null candidates are removed, plain numeric revisions lack a delimiter, malformed/overflow generations differ, and ties have no equivalent first-row rule. No schema constraint guarantees the narrower input grammar. Retain the safe Kotlin extraction cleanup instead. | [ChatRepositoryImpl.kt:138][V262]; [ChatDao.kt:15][V428] |
| L15.09 | NetworkMonitorWorker.doWork calls only a synchronous scheduling function with no suspension or explicit cancellation check. Stopping a worker does not inject CancellationException into this code, and a stopped WorkManager result is ignored rather than automatically re-enqueueing itself as claimed. A defensive catch alone does not address a demonstrated cancellation bug here. | [NetworkMonitorWorker.kt:43][V429]; [NetworkMonitorWorker.kt:53][V430] |

For L08.01/L08.02, the local schema/index reproduction produced `MULTI-INDEX OR` with indexed SEARCH operations on both key columns. The user IN/OR variant did too. In contrast, the optional-parameter My Life predicate produced `SCAN my_life`, while the split exact-user query used its userId index. SQLite `NOCASE` also evaluated `Ä = ä` as false. These checks reject blanket equivalence/index claims; they do not assert an identical Android plan or measured device latency.

## Parts and claims removed from otherwise retained submissions

| Raw ID | Removed/corrected scope or claim |
| --- | --- |
| L01.01 | Discarded the unproven claim that all team-document user IDs equal membership-only user IDs; the shipped scope reuses only the two identical membership reads. |
| L01.04 | A tuple check still reads preferences on every access. Eliminating those reads requires audited invalidation and edits to reset paths, beyond the original getter-only restriction. |
| L01.06 | associateBy keeps the last duplicate and excluding null names changes current behavior; the shipped index preserves first-match and null equality. |
| L01.09 | Rejected the unbounded, locale-independent cache claim: fallback labels need not belong to Constants.LABELS and formatting reads Locale.getDefault(). |
| L02.02 | The suggested getExamSubmissionsByUser API filters type=exam, while the old parent/user/status lookup has no type predicate. Equivalence must be established or the batch query must preserve the broader selection. |
| L02.08 | maxByOrNull already evaluates its selector once per element; only the split allocation is redundant there. Comparator timestamp parsing is the repeated work. |
| L03.03 | Path alone is not a valid content cache key; content URIs and same-path file replacement require explicit invalidation or a batch-scoped snapshot. |
| L03.04 | Rejected adding android.util.Log to RetryUtils; structured reporting must preserve its existing platform-independent imports. stderr is not literally invisible to logcat. |
| L03.09 | The day-JSON catch is reachable and must remain. Removed only the claim that both catches are dead. |
| L05.03 | Use database/DAO transaction ownership rather than treating a loop inside an arbitrary service as a Room transaction. Existing batch methods must be reused where present. |
| L05.06 | Clearing before persistence under separate collection locks can lose links or race read-modify-write across writers. The shipped task requires shared serialization and failure-safe draining. |
| L05.09 | Changed associateBy-style last-wins indexing to first-wins indexing to match find; include the sorting assignment as well as initial/cached loads. |
| L06.02 | Removed the unsupported claim that concurrency doubles speed or finishes platform portability; Room scheduling and device latency must be measured. |
| L06.05 | lowercase(Locale.ROOT) membership is not generally equivalent to Kotlin equals(ignoreCase=true). Unicode folding equivalence is required before shipping the suggested set optimization. |
| L06.08 | Dropped the additive Paint.measureText(space)+measureText(word) rewrite: width additivity is not established for shaped text and can change wrapping. Only the verified parent-ID split removal is retained. |
| L07.01 | The service still calls the wide getUserByName API at line 58, as the steps acknowledge. Moving the three sync reads does not remove both user contracts. |
| L07.02 | Do not retain identical inherited declarations without override in UserRepository; that is not a valid Kotlin redeclaration. The unused getUserImageUrl promotion is omitted because no consuming production call was found. |
| L07.03 | FeedbackListViewModel already imports and injects UserReadRepository at lines 15/20, so that proposed fifth edit is dropped. |
| L07.08 | Dropped the principal narrowing edit: ChatViewModel already imports and injects UserReadRepository and TeamsShareRepository at lines 36/37/56/57. Only the independently verifiable fallback-construction cleanup remains. |
| L08.04 | Do not generalize the indexed-OR rejection to every OR query. Personal remote IDs lack the matching index shown for users/teams; preserve updates to both matches and do not replace them with first-row precedence. |
| L08.05 | The repository consumer maps these rows to resource IDs, so the proposed visible title/time instability is not established. Deterministic DAO output remains a smaller valid cleanup. |
| L09.03 | The literal delete-all-logs version suppresses sole diagnostics on swallowed failures. The valid boundary cleanup needs reporting preserved, increasing scope and lowering feasibility. |
| L11.04 | StateFlow suppresses equal values, and seedMyLifeIfEmpty itself reads/counts. Removed the claim of exactly two emissions or a guaranteed one-query total; validate the actual query-count reduction. |
| L11.06 | These are four selected tests, not the last Mockito tests: SubmissionViewModelTest also imports Mockito in this working tree. No CI/transitive-dependency failure is asserted without a build. |
| L12.06 | The existing chunked(emptyList()) already issues no SQL; the empty guard is only a small allocation/readability improvement. Transactional rollback is the substantive change. |
| L12.10 | Moving an interface method preserves rebuilt source callers but is not a universal binary-compatibility guarantee for precompiled implementations. |
| L13.01 | Rejected adding android.util.Log to RetryUtils; structured reporting must preserve its existing platform-independent imports. stderr is not literally invisible to logcat. |
| L13.04 | Dropped the alleged N exam lookups in generateMultipleSubmissionsPdf: that function does not query examDao at all; the one getById at line 79 is in generateSubmissionPdf, which the proposal excludes. Only the independently verified silent catches remain. |
| L13.09 | Dropped KEEP-to-REPLACE: every request contains the same true boolean, and ServerReachabilityWorker reads the current server URL when it runs. No stale dynamic input exists; REPLACE can repeatedly postpone or cancel useful work. |
| L13.10 | Dropped Calendar instance sharing: CalendarDay receives a mutable Calendar per row; reusing one mutable object can make all rows refer to the final date. Keep independent calendar values unless copied. |
| L14.01 | Only the current workflow structure is verified. Branches can reuse eligible same-branch/default-branch caches; GitHub does not share only default-branch caches. A missing app/build cache also does not prove cold compilation when the existing Gradle remote cache is configured. |
| L15.02 | Constructor-call verification covers interceptor policy, not the actual stored Intent extras. The merged task retains that Android-boundary coverage instead of relying only on anyConstructed<Intent>() interactions. |
| L17.10 | RetryQueueWorkerTest and DownloadServiceTest already install Log at BeforeClass, not per test. Removing their dead mock remains valid, but the alleged per-test setup savings there are dropped. |
| L18.03 | Dropped indefinite publish_failed memoization by base SHA: workflow completion, annotations, and Play Store state can change while the SHA stays fixed. A cache needs a decision-pass lifetime or explicit state/version invalidation. |
| L18.06 | This does not make the mapper fully platform-free: it still returns HealthExaminationAdapter.HealthExaminationItem and uses platform-linked models/formatting. Only the redundant direct import is removed. |
| L18.07 | PublicSurveyEvent.UploadSkipped does not exist in this checkout; the stale path emits NavigateOnward at line 76. The shipped verification uses the existing event. |
| L18.08 | The suggested chunked(SQLITE_MAX_VARIABLE_NUMBER) wrapper binds the list twice and can exceed the limit; use at most half. The existing queries are indexed according to their own schema; two calls do not establish two full-table scans. Preserve old first-match precedence when combining results. |

Additional interpretation limits apply throughout: Android-import removal alone does not prove a transitive dependency graph is KMP-ready; comparator/SQL/cache rewrites must retain current semantics; reported CI durations are source assertions, not independently measured results here. The proposed final task text includes the necessary preservation constraints without retaining input DoD checklists.

## Verification receipts

Every citation below was resolved against the same clean working tree. Excerpts show the cited line and up to two following lines, and file hashes make local evidence drift detectable. A cited import proves a direct dependency; a performance consequence additionally depends on the inspected call site and control flow described in its task/disposition.

| Receipt | Working-tree path : line | Read excerpt | File SHA-256 |
| --- | --- | --- | --- |
| V001 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt:110` | `internal suspend fun getTeamByIdOrNull(teamId: String) = try { teamsRepositoryProvider.get().getTeamById(teamId) } catch (e: CancellationException) {` | `b1300e6e77de26d9d1a99cdf384c86f595d671f25dd390f320c2e6e5ef564ad3` |
| V002 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt:162` | `} catch (e: Exception) { e.printStackTrace() }` | `b1300e6e77de26d9d1a99cdf384c86f595d671f25dd390f320c2e6e5ef564ad3` |
| V003 | `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:37` | `override suspend fun updateMyLifeListOrder(list: List<MyLife>, userId: String?) { if (list.isEmpty()) return val rawUserId = list.firstOrNull()?.userId ?: userId` | `b42a74bf53f52090966434496ecf956f4dcfbcf552fb14b1a123d34b10742c93` |
| V004 | `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:39` | `val rawUserId = list.firstOrNull()?.userId ?: userId val effectiveUserId = normalizeUserId(rawUserId) val idToIndex = buildMap(list.size) {` | `b42a74bf53f52090966434496ecf956f4dcfbcf552fb14b1a123d34b10742c93` |
| V005 | `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:49` | `val managedLives = myLifeDao.getByIds(ids) val changed = managedLives.mapNotNull { managedLife -> val index = idToIndex[managedLife._id]` | `b42a74bf53f52090966434496ecf956f4dcfbcf552fb14b1a123d34b10742c93` |
| V006 | `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt:75` | `fun uploadHealth() { appScope.launch(dispatcherProvider.io) { healthRepository.syncPendingHealthExaminations()` | `2a2d244919174bcba8dd81cb70b7ce48e818dbdbeb90aa743986321ba9b84bf3` |
| V007 | `app/src/main/java/org/ole/planet/myplanet/services/UserDataWorker.kt:53` | `runCatchingRethrowCancellation { awaitUploadCompletion { onComplete -> uploadToShelfService.uploadUserData {` | `df07464522b7591426764aaa07979ecc851b55bb7e2f2600af0476b8bf2cbeb7` |
| V008 | `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt:108` | `uploadToShelfService.uploadUserData { workerScope.launch { val status = configurationsRepository.checkHealth()` | `7bbbe9c3565459095f091d6678ee67f73131ab051c4e061eba0a73142ae287a9` |
| V009 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:29` | `suspend fun deleteStaleQuestions(questions: List<ExamQuestion>) { questions.groupBy { it.examId }.forEach { (examId, examQuestions) -> if (examId != null) deleteStaleInternal(examId, examQuestions.map { it.id }.distinct())` | `52a7092ad2adc4930daee6e47bdf0df133554a509cb90f37e5510f3855da1084` |
| V010 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:27` | `@Query("DELETE FROM exam_questions WHERE examId = :examId AND id NOT IN (:keepIds)") suspend fun deleteStaleInternal(examId: String, keepIds: List<String>)  suspend fun deleteStaleQuestions(questions: List<ExamQuestion>) {` | `52a7092ad2adc4930daee6e47bdf0df133554a509cb90f37e5510f3855da1084` |
| V011 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:31` | `if (examId != null) deleteStaleInternal(examId, examQuestions.map { it.id }.distinct()) } }` | `52a7092ad2adc4930daee6e47bdf0df133554a509cb90f37e5510f3855da1084` |
| V012 | `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt:36` | `override suspend fun insertDictionaryData(): DictionaryLoad { return withContext(dispatcherProvider.io) { if (!dictionaryFileReader.exists()) {` | `1a318bead135274f901a96d61e717998e63b72703d810b626df6421c0bd1e30f` |
| V013 | `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt:43` | `if (dictionaryDao.count() > 0) { return@withLock DictionaryLoad.AlreadyPopulated }` | `1a318bead135274f901a96d61e717998e63b72703d810b626df6421c0bd1e30f` |
| V014 | `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt:60` | `viewModel.loadCount() viewModel.loadDictionary() ` | `6903434d72eaa8417c25b8b773ca134b367a4fa6a091512566c6e405db4181b6` |
| V015 | `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt:39` | `fun loadDictionary() { viewModelScope.launch { _loadState.value = when (val result = dictionaryRepository.insertDictionaryData()) {` | `ec0bc98ea2629626a5af5e7bd163fad810a77c4e966fc91acc8c239d335f31d9` |
| V016 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt:18` | `@Query("DELETE FROM course_steps WHERE courseId = :courseId AND id NOT IN (:keepIds)") suspend fun deleteStaleInternal(courseId: String, keepIds: List<String>) @Query("DELETE FROM course_steps WHERE courseId = :courseId") suspend fun deleteByCourseId(courseId: String) ` | `740ceb986552a86b6460785aaa650e7dde41b5fbd3cc2ad71614c75ec02e8e50` |
| V017 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt:21` | `suspend fun deleteStaleSteps(courseId: String, keepIds: List<String>) { if (keepIds.isEmpty()) deleteByCourseId(courseId) else deleteStaleInternal(courseId, keepIds.distinct()) }` | `740ceb986552a86b6460785aaa650e7dde41b5fbd3cc2ad71614c75ec02e8e50` |
| V018 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:64` | `loadDataJob?.cancel() loadDataJob = viewModelScope.launch { _uiState.update { it.copy(isLoading = true) }` | `c79c75559354d09daaff4f5009df417ffd404a2effa9edf447dd2276ddec5aca` |
| V019 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:110` | `viewModelScope.launch { if (!resourcesRepository.downloadResourcesPriority(notDownloaded)) { _uiState.update {` | `c79c75559354d09daaff4f5009df417ffd404a2effa9edf447dd2276ddec5aca` |
| V020 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:125` | `viewModelScope.launch { val nextResources = resourcesRepository.getAllStepResources(nextStepId) val nextNotDownloaded = nextResources.filter { !it.isResourceOffline() }` | `c79c75559354d09daaff4f5009df417ffd404a2effa9edf447dd2276ddec5aca` |
| V021 | `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt:17` | `while (attempt < maxAttempts) { try { result = block()` | `613898023c450fd2a6c7af149b3bf56bfe13174f837eebe03336cac16edb2ed6` |
| V022 | `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt:20` | `} catch (e: Exception) { lastException = e result = null` | `613898023c450fd2a6c7af149b3bf56bfe13174f837eebe03336cac16edb2ed6` |
| V023 | `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt:32` | `lastException?.printStackTrace() return result }` | `613898023c450fd2a6c7af149b3bf56bfe13174f837eebe03336cac16edb2ed6` |
| V024 | `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt:286` | `fun evaluateChallengeDialog( userId: String?, isGuest: Boolean,` | `1f90175010ac3a1c222f3efed86613b9e01df5ef56059c05c108c00c80994998` |
| V025 | `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt:299` | `val courseDataDeferred = async { progressRepository.fetchCourseData(userId) } val voiceCountDeferred = async { voicesRepository.getCommunityVoiceDateCount(startTime, endTime, userId) } val allVoiceCountDeferred = async { voicesRepository.getCommunityVoiceDateCount(startTime, endTime, null) }` | `1f90175010ac3a1c222f3efed86613b9e01df5ef56059c05c108c00c80994998` |
| V026 | `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt:314` | `val endDate = LocalDate.of(2025, 1, 16) val shouldPrompt = today.isAfter(LocalDate.of(2024, 11, 30)) && today.isBefore(endDate) &&` | `1f90175010ac3a1c222f3efed86613b9e01df5ef56059c05c108c00c80994998` |
| V027 | `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt:441` | `suspend fun syncNotificationReads() = withContext(dispatcherProvider.io) { val pending = notificationsRepository.getPendingSyncNotifications() if (pending.isEmpty()) return@withContext` | `5ba4cf68a35a7c23f89958e97a18e39880193c97762352bb97bcafb012c5afbf` |
| V028 | `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt:445` | `val successfulSyncs = pending.map { notification -> async { if (notification.rev == null) return@async null` | `5ba4cf68a35a7c23f89958e97a18e39880193c97762352bb97bcafb012c5afbf` |
| V029 | `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt:462` | `} catch (e: CancellationException) { throw e } catch (e: Exception) {` | `5ba4cf68a35a7c23f89958e97a18e39880193c97762352bb97bcafb012c5afbf` |
| V030 | `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt:84` | `suspend fun getTextPreview(file: File): String? { return withContext(dispatcherProvider.io) { val key = CacheKey(file.absolutePath, file.lastModified(), file.length())` | `f497b90f37e36079ccf2fa0f49d8e3c37825fed6329fa6d05fa0ca19a9ed1699` |
| V031 | `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt:90` | `file.bufferedReader().useLines { it.take(8).joinToString("\n") }.takeIf { it.isNotEmpty() } } catch (e: Exception) { null` | `f497b90f37e36079ccf2fa0f49d8e3c37825fed6329fa6d05fa0ca19a9ed1699` |
| V032 | `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt:358` | `btnResources.setOnClickListener { if (resources.isNotEmpty()) { lifecycleScope.launch {` | `0938f104a1b8d697b3a2560221252e386dbb8a8f35ad06107998ebf3048aeb9a` |
| V033 | `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt:360` | `lifecycleScope.launch { showDownloadDialog(resources) }` | `0938f104a1b8d697b3a2560221252e386dbb8a8f35ad06107998ebf3048aeb9a` |
| V034 | `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt:189` | `private fun openHtmlResource(items: MyLibrary) { viewLifecycleOwner.lifecycleScope.launch { val indexExists = withContext(dispatcherProvider.io) {` | `0938f104a1b8d697b3a2560221252e386dbb8a8f35ad06107998ebf3048aeb9a` |
| V035 | `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt:192` | `val directory = File(context?.getExternalFilesDir(null), "ole/${items.resourceId}") FileUtils.resolveHtmlEntryFile(directory, items.openWhichFile)?.exists() == true }` | `0938f104a1b8d697b3a2560221252e386dbb8a8f35ad06107998ebf3048aeb9a` |
| V036 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt:31` | `fun loadCourses(teamId: String, currentUserId: String) { viewModelScope.launch { coroutineScope {` | `924058b3ff7f1db8e62b904a8224c3f8189a049f263f7e3db41c9a611adf424f` |
| V037 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt:34` | `val teamCreatorDeferred = async { teamsRepository.getTeamCreator(teamId) } val coursesDeferred = async { val courseIds = teamsRepository.getTeamCourseIds(teamId)` | `924058b3ff7f1db8e62b904a8224c3f8189a049f263f7e3db41c9a611adf424f` |
| V038 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt:36` | `val courseIds = teamsRepository.getTeamCourseIds(teamId) coursesRepository.getCoursesByIds(courseIds) }` | `924058b3ff7f1db8e62b904a8224c3f8189a049f263f7e3db41c9a611adf424f` |
| V039 | `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:119` | `} catch (e: Exception) { e.printStackTrace() _saveResult.send(false)` | `744bc562f2e6383a0bc612697909ac456805afc8622217864769eec70711f1ee` |
| V040 | `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:120` | `e.printStackTrace() _saveResult.send(false) } finally {` | `744bc562f2e6383a0bc612697909ac456805afc8622217864769eec70711f1ee` |
| V041 | `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:133` | `} catch (e: CancellationException) { throw e } catch (e: Exception) {` | `744bc562f2e6383a0bc612697909ac456805afc8622217864769eec70711f1ee` |
| V042 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:105` | `val list = withContext(dispatcherProvider.io) { try { applyCurrentSort(resourcesRepository.getResourceListModels(isMyCourseLib, modelId))` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V043 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:109` | `e.printStackTrace() null }` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V044 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt:142` | `e.printStackTrace() null }` | `76ac5619a065c1ac65eba9e1bdf45898c12264d6342379d87a7061ee5591736c` |
| V045 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:130` | `} catch (e: Exception) { e.printStackTrace() }` | `6a5d1015f3a72de30c830ba5b766a08c50352bf734260d4fb52f3980864990d7` |
| V046 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:95` | `suspend fun getRatingPromptDecision(courseId: String?, userId: String?): RatingPromptDecision { if (courseId.isNullOrEmpty() \|\| userId.isNullOrEmpty()) { return RatingPromptDecision.Skip` | `6a5d1015f3a72de30c830ba5b766a08c50352bf734260d4fb52f3980864990d7` |
| V047 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:102` | `} catch (e: Exception) { false }` | `6a5d1015f3a72de30c830ba5b766a08c50352bf734260d4fb52f3980864990d7` |
| V048 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:129` | `_uiState.value = TakeCourseUiState.Success(course, steps, userModel, progress, blockingStepIndex) } catch (e: Exception) { e.printStackTrace()` | `6a5d1015f3a72de30c830ba5b766a08c50352bf734260d4fb52f3980864990d7` |
| V049 | `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt:444` | `val courseTitle = getCourseById(courseId)?.courseTitle val stepIds = stepsList.map { it.id } val allExams = if (stepIds.isEmpty()) emptyList() else examDao.getByStepIds(stepIds)` | `8a46a45f200bab7deb758f3cc02e49b08b781b678a89af028bf4c84b8588ceb7` |
| V050 | `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt:525` | `override suspend fun getCourseTitleById(courseId: String): String? { return getCourseById(courseId)?.courseTitle }` | `8a46a45f200bab7deb758f3cc02e49b08b781b678a89af028bf4c84b8588ceb7` |
| V051 | `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt:568` | `override suspend fun getMyCourseIds(userId: String): JsonArray { val ids = JsonArray() getMyCourses(userId).mapNotNull { it.courseId }.forEach { ids.add(it) }` | `8a46a45f200bab7deb758f3cc02e49b08b781b678a89af028bf4c84b8588ceb7` |
| V052 | `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt:885` | `private suspend fun mapCourses(courses: List<MyCourse>): List<MyCourse> { if (courses.isEmpty()) return emptyList() val courseIds = courses.map { it.courseId ?: it.id }.distinct()` | `8a46a45f200bab7deb758f3cc02e49b08b781b678a89af028bf4c84b8588ceb7` |
| V053 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt:212` | `allResourceModels = cached val filteredList = viewModel.applyFilter(allResourceModels, currentFilterCriteria(), currentLocallyOfflineIds()) adapterLibrary.setLibraryList(filteredList)` | `0453e6dfcc7d0bfd2a4496caf3d5b8b6b0dff643feeb8ff3694d63c12bce6e03` |
| V054 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt:574` | `allResourceModels.find { it.item.id == item.id }?.library } selectedItems?.clear()` | `0453e6dfcc7d0bfd2a4496caf3d5b8b6b0dff643feeb8ff3694d63c12bce6e03` |
| V055 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt:583` | `val realmTag = allResourceModels.flatMap { it.tags }.find { it.id == tag.id } if (realmTag != null) { val rTag = searchTags.find { it.id == realmTag.id } ?: TagEntity().apply {` | `0453e6dfcc7d0bfd2a4496caf3d5b8b6b0dff643feeb8ff3694d63c12bce6e03` |
| V056 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt:781` | `allResourceModels = when (mode) { ResourcesViewModel.SortMode.DATE -> viewModel.toggleSortOrder(allResourceModels) ResourcesViewModel.SortMode.TITLE -> viewModel.toggleTitleSortOrder(` | `0453e6dfcc7d0bfd2a4496caf3d5b8b6b0dff643feeb8ff3694d63c12bce6e03` |
| V057 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:811` | `revUpdates.forEach { (id, rev) -> submissionDao.updateRev(id, rev) } if (submissions.isEmpty() && answers.isEmpty()) return ` | `e93db9c5723c0c9a96403dbcd5060ba7bf5a93853513e3a98d39dda3e45172c1` |
| V058 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:849` | `override suspend fun markSubmissionsUploaded(results: List<UploadedItemResult>): List<UploadedItemResult> = results.filter { submissionDao.markUploaded(it.localId, it.remoteId, it.remoteRev) == 0 } ` | `e93db9c5723c0c9a96403dbcd5060ba7bf5a93853513e3a98d39dda3e45172c1` |
| V059 | `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt:13` | `private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) { override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024 }` | `aa6c2edc4241ed1717adb0c0aa12fee605064402c8937776772f12053d316da6` |
| V060 | `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt:14` | `override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount / 1024 } ` | `aa6c2edc4241ed1717adb0c0aa12fee605064402c8937776772f12053d316da6` |
| V061 | `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:99` | `fun toggleGroupExpansion(type: String) { val isCurrentlyExpanded = when { type in _expandedGroups.value -> true` | `4b5369cb7cf2f06beb8ac10b37a979544e636382e44955c7c4a128fb1d29cabe` |
| V062 | `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:206` | `private fun isGroupDefaultExpanded(type: String, notifications: List<Notification>): Boolean { return notifications.any { it.type == type && !it.isRead } }` | `4b5369cb7cf2f06beb8ac10b37a979544e636382e44955c7c4a128fb1d29cabe` |
| V063 | `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:210` | `private fun buildNotificationGroups(notifications: List<Notification>): List<NotificationGroup> { if (notifications.isEmpty()) return emptyList() val grouped = notifications.groupBy { notif ->` | `4b5369cb7cf2f06beb8ac10b37a979544e636382e44955c7c4a128fb1d29cabe` |
| V064 | `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt:34` | `override suspend fun getCourseProgress(courseIds: List<String>, userId: String?): Map<String, CourseProgressState> = withContext(dispatcherProvider.default) { val allSteps = if (courseIds.isEmpty()) { emptyList()` | `409d4a77e8de723e5a824dcacaa7612a5be34cf0d842d9f7113a0d1b019120af` |
| V065 | `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt:47` | `val steps = stepsByCourseId[courseId] ?: emptyList() val progresses = progressesByCourseId[courseId] ?: emptyList() map[courseId] = CourseProgressState(` | `409d4a77e8de723e5a824dcacaa7612a5be34cf0d842d9f7113a0d1b019120af` |
| V066 | `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt:357` | `val questionCounts = questionDao.getByExamIds(surveyIds).groupingBy { it.examId }.eachCount()  return surveys.mapNotNull { survey ->` | `38a3f383b0a15e796b603824357d549b62e5ef6bba2d38905a398f12e55d73aa` |
| V067 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:18` | `@Query("SELECT * FROM exam_questions WHERE examId IN (:examIds)") suspend fun getByExamIdsInternal(examIds: List<String>): List<ExamQuestion> ` | `52a7092ad2adc4930daee6e47bdf0df133554a509cb90f37e5510f3855da1084` |
| V068 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:25` | `@Query("SELECT COUNT(*) FROM exam_questions WHERE examId = :examId") suspend fun countByExamId(examId: String): Int @Upsert suspend fun upsertAll(items: List<ExamQuestion>) @Query("DELETE FROM exam_questions WHERE examId = :examId AND id NOT IN (:keepIds)") suspend fun deleteStaleInternal(examId: String, keepIds: List<String>)` | `52a7092ad2adc4930daee6e47bdf0df133554a509cb90f37e5510f3855da1084` |
| V069 | `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt:166` | `steps.forEachIndexed { index, step -> val stepNum = index + 1 val exam = step?.id?.let { examsByStepId[it] } ?: return@forEachIndexed` | `409d4a77e8de723e5a824dcacaa7612a5be34cf0d842d9f7113a0d1b019120af` |
| V070 | `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt:192` | `private suspend fun requiredExamsByStepId(steps: List<CourseStep?>, courseId: String): Map<String, StepExam> { val requiredStepIds = steps.mapNotNullTo(HashSet()) { step -> step?.id?.takeIf { step.passingRequired } } val exams = examDao.getByCourseIdAndType(courseId, "courses")` | `409d4a77e8de723e5a824dcacaa7612a5be34cf0d842d9f7113a0d1b019120af` |
| V071 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt:26` | `@Query("SELECT * FROM submissions WHERE userId IS :userId AND type = 'exam'") suspend fun getExamSubmissionsByUser(userId: String?): List<Submission> @Query("SELECT * FROM submissions WHERE userId = :userId") fun observeByUserId(userId: String): Flow<List<Submission>> @Query("SELECT * FROM submissions WHERE userId = :userId AND status = 'pending' AND type = 'survey'") suspend fun getPendingSurveys(userId: String): List<Submission>` | `89adc4ccfb541a5d350d97876cea35a1eb974b74ca1d83bae1a8dd5c48cd0425` |
| V072 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:366` | `return hydrateSubmissions(submissionDao.getByParentUserAndStatus(parentId, userId, status)) } ` | `e93db9c5723c0c9a96403dbcd5060ba7bf5a93853513e3a98d39dda3e45172c1` |
| V073 | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt:58` | `markUploaded = { results -> results.filter { result -> !voicesRepository.markNewsLogUploaded(result.localId, result.remoteId, result.remoteRev)` | `e03233489ec0b3f48498a862438193db4eb954dec7570e111ef5d590f847a0cc` |
| V074 | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt:71` | `markUploaded = { results -> results.filter { result -> !progressRepository.markCourseProgressUploaded(result.localId, result.remoteId, result.remoteRev)` | `e03233489ec0b3f48498a862438193db4eb954dec7570e111ef5d590f847a0cc` |
| V075 | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt:87` | `markUploaded = { results -> results.filter { result -> !teamsSyncRepository.get().markTaskUploaded(result.localId, result.remoteId, result.remoteRev)` | `e03233489ec0b3f48498a862438193db4eb954dec7570e111ef5d590f847a0cc` |
| V076 | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt:201` | `markUploaded = { results -> // Mark each uploaded feedback; rows that no longer exist are reported as failures. results.filter { result -> !feedbackRepository.markFeedbackUploaded(result.localId, result.remoteId, result.remoteRev) }` | `e03233489ec0b3f48498a862438193db4eb954dec7570e111ef5d590f847a0cc` |
| V077 | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt:228` | `markUploaded = { results -> results.filter { result -> !submissionsRepository.markSubmitPhotosUploaded(result.localId, result.remoteId, result.remoteRev)` | `e03233489ec0b3f48498a862438193db4eb954dec7570e111ef5d590f847a0cc` |
| V078 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:117` | `val userModel = userRepository.getUserModel() val course = coursesRepository.getCourseById(courseId) if (course == null) {` | `6a5d1015f3a72de30c830ba5b766a08c50352bf734260d4fb52f3980864990d7` |
| V079 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:124` | `val steps = coursesRepository.getCourseSteps(courseId) val progressMap = progressRepository.getCourseProgress(listOf(courseId), userModel?.id) val progress = progressMap[courseId]?.current ?: 0` | `6a5d1015f3a72de30c830ba5b766a08c50352bf734260d4fb52f3980864990d7` |
| V080 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:127` | `val blockingStepIndex = getBlockingStepIndex(steps, userModel, courseId)  _uiState.value = TakeCourseUiState.Success(course, steps, userModel, progress, blockingStepIndex)` | `6a5d1015f3a72de30c830ba5b766a08c50352bf734260d4fb52f3980864990d7` |
| V081 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:209` | `override suspend fun hasSubmission( stepExamId: String?, courseId: String?,` | `e93db9c5723c0c9a96403dbcd5060ba7bf5a93853513e3a98d39dda3e45172c1` |
| V082 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:412` | `override suspend fun hasUnfinishedSurveys(courseId: String, userId: String?): Boolean { val surveys = getSurveysByCourseId(courseId) for (survey in surveys) {` | `e93db9c5723c0c9a96403dbcd5060ba7bf5a93853513e3a98d39dda3e45172c1` |
| V083 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:422` | `override suspend fun hasPendingSurvey(courseId: String, userId: String?): Boolean { val surveys = getSurveysByCourseId(courseId) for (survey in surveys) {` | `e93db9c5723c0c9a96403dbcd5060ba7bf5a93853513e3a98d39dda3e45172c1` |
| V084 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt:34` | `@Query("SELECT COUNT(*) FROM submissions WHERE userId IS :userId AND parentId = :parentId AND type = :type") suspend fun countByUserParentAndType(userId: String?, parentId: String, type: String): Int @Query("SELECT COUNT(*) FROM submissions WHERE userId IS :userId AND parentId = :parentId AND type = :type AND status != 'pending'") suspend fun countFinishedByUserParentAndType(userId: String?, parentId: String, type: String): Int @Query("SELECT COUNT(*) FROM submissions WHERE userId IS :userId AND parentId LIKE '%' \|\| :examId \|\| '%' AND status != 'pending'") suspend fun countCompletedByUserAndExamId(userId: String?, examId: String): Int` | `89adc4ccfb541a5d350d97876cea35a1eb974b74ca1d83bae1a8dd5c48cd0425` |
| V085 | `.github/scripts/automerge.sh:104` | `pr_state()  { gh pr view "$1" --repo "$REPO" --json state --jq '.state' 2>/dev/null \|\| echo ''; } pr_review() { gh pr view "$1" --repo "$REPO" --json reviewDecision --jq '.reviewDecision' 2>/dev/null \|\| echo ''; } ` | `a15b055fa02776305070653735b11bc7dc5977b5a58b063c39b6a77ad7af6726` |
| V086 | `.github/scripts/automerge.sh:107` | `check_mergeable() { local pr=$1 state="" for _ in 1 2 3 4 5; do` | `a15b055fa02776305070653735b11bc7dc5977b5a58b063c39b6a77ad7af6726` |
| V087 | `.github/scripts/automerge.sh:502` | `state=$(pr_state "$NUMBER") if [ -n "$state" ] && [ "$state" != "OPEN" ]; then log "  #$NUMBER is no longer open (state: $state) -- skipping"` | `a15b055fa02776305070653735b11bc7dc5977b5a58b063c39b6a77ad7af6726` |
| V088 | `.github/scripts/automerge.sh:509` | `review=$(pr_review "$NUMBER") case "$review" in REVIEW_REQUIRED\|CHANGES_REQUIRED\|CHANGES_REQUESTED)` | `a15b055fa02776305070653735b11bc7dc5977b5a58b063c39b6a77ad7af6726` |
| V089 | `.github/scripts/automerge.sh:518` | `if ! check_mergeable "$NUMBER"; then handle_conflict "$NUMBER" skip_numbers="$skip_numbers $NUMBER"` | `a15b055fa02776305070653735b11bc7dc5977b5a58b063c39b6a77ad7af6726` |
| V090 | `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:402` | `override suspend fun ensureUserSecurityKeys(userId: String): UserEntity? { val user = getUserById(userId) ?: return null if (user.key == null) {` | `c6d11dbf6480bd1c8239147888c74913bf862beff958c6d4f3f5895da07a1065` |
| V091 | `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:665` | `override suspend fun saveKeyIv(model: UserEntity, obj: JsonObject) { val table = "userdb-${StringUtils.toHex(model.planetCode)}-${StringUtils.toHex(model.name)}" val header = UrlUtils.basicAuthHeader(obj["name"].asString, obj["password"].asString)` | `c6d11dbf6480bd1c8239147888c74913bf862beff958c6d4f3f5895da07a1065` |
| V092 | `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:69` | `if (userId != null) { user = userRepository.getUserById(userId) pojo = healthRepository.getByIdOrUserId(userId)` | `744bc562f2e6383a0bc612697909ac456805afc8622217864769eec70711f1ee` |
| V093 | `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:73` | `val updatedUser = userRepository.ensureUserSecurityKeys(userId) if (updatedUser != null) { user = updatedUser` | `744bc562f2e6383a0bc612697909ac456805afc8622217864769eec70711f1ee` |
| V094 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt:53` | `loadJob = viewModelScope.launch { _uiState.value = CourseDetailUiState.Loading ` | `18f38e41a6af79368dc92dcb820dded201fef8c0f56fc4e8ab61d7bc4639c378` |
| V095 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt:68` | `val markdownDescription = MarkdownUtils.prependBaseUrlToImages( courseDetail.course.description, "file://${storagePathResolver.resolveExternalFilesDir()}/ole/",` | `18f38e41a6af79368dc92dcb820dded201fef8c0f56fc4e8ab61d7bc4639c378` |
| V096 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt:123` | `viewModel.observeDiscussions(getEffectiveTeamId())  viewLifecycleOwner.lifecycleScope.launch {` | `d63fc71ec125f1723d9872660dc77717a6a241266200c98f2e066eb1fa425720` |
| V097 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt:125` | `viewLifecycleOwner.lifecycleScope.launch { val realmNewsList = viewModel.getFilteredNews(getEffectiveTeamId()) showRecyclerView(realmNewsList)` | `d63fc71ec125f1723d9872660dc77717a6a241266200c98f2e066eb1fa425720` |
| V098 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt:58` | `suspend fun getFilteredNews(teamId: String): List<News?> { val newsList = voicesRepository.getFilteredNews(teamId) notificationsRepository.updateTeamNotification(teamId, newsList.size)` | `0ea38ce9135a413de7c734d455322be98182398afc0e2a1d129967dbbcf3efad` |
| V099 | `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt:573` | `override suspend fun countTopLevelByTeam(teamId: String): Long { return newsDao.countTopLevelByTeam(teamId, teamIdPattern(teamId)) }` | `0323893016cd0f313cc3eaeb8b437e4f965700e0b776838ef92256139eb663e4` |
| V100 | `.github/scripts/playstore-quota.sh:56` | `measure() { local hold_start=$((NOW_EPOCH - HOLD_SEC)) e used=0` | `dd0cf8edd3953febbd5c1bf1607b00a7eb09f38c0d36bed58c01d830330490db` |
| V101 | `.github/scripts/playstore-quota.sh:82` | `local used held free local -a live=() slot_epochs=() measure` | `dd0cf8edd3953febbd5c1bf1607b00a7eb09f38c0d36bed58c01d830330490db` |
| V102 | `.github/scripts/playstore-quota.sh:114` | `forecast() { local used held free local -a live=() slot_epochs=()` | `dd0cf8edd3953febbd5c1bf1607b00a7eb09f38c0d36bed58c01d830330490db` |
| V103 | `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:82` | `@Before fun setup() { mockkObject(UrlUtils)` | `40c7779eace8ecad493f1fe5d023c669b797da388aa4b46844cfa84098d58a0c` |
| V104 | `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:150` | `@After fun tearDown() { unmockkObject(UrlUtils)` | `40c7779eace8ecad493f1fe5d023c669b797da388aa4b46844cfa84098d58a0c` |
| V105 | `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:657` | `verify { Log.e(capture(tagSlot), capture(msgSlot), capture(errSlot)) }  assertEquals("UserRepositoryImpl", tagSlot.captured)` | `40c7779eace8ecad493f1fe5d023c669b797da388aa4b46844cfa84098d58a0c` |
| V106 | `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:555` | `mockkObject(SecurePrefs) try { every { SecurePrefs.getPassword(context, settings) } returns "p\$a@s\\s"` | `40c7779eace8ecad493f1fe5d023c669b797da388aa4b46844cfa84098d58a0c` |
| V107 | `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:580` | `mockkObject(SecurePrefs) try { every { SecurePrefs.getPassword(context, settings) } returns "plainpass123"` | `40c7779eace8ecad493f1fe5d023c669b797da388aa4b46844cfa84098d58a0c` |
| V108 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:64` | `val searched = ResourcesSearchUtils.searchLocalModels(models, criteria.searchQuery) val searchTagIds = if (criteria.searchTags.isNotEmpty()) { criteria.searchTags.mapTo(HashSet()) { it.id }` | `db44b327d340dffa98fe19fcec188722b2e9e321932ee86f3c41215b7cd1842c` |
| V109 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:85` | `return searched.asSequence() .filter { model -> searchTagIds == null \|\| model.tags.any { it.id in searchTagIds } } .filter { model -> matchesFacets(model, criteria, precomputedMediums) }` | `db44b327d340dffa98fe19fcec188722b2e9e321932ee86f3c41215b7cd1842c` |
| V110 | `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:61` | `private val groupedNotifications: StateFlow<List<NotificationGroup>> = _notifications .map { buildNotificationGroups(it) } .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())` | `4b5369cb7cf2f06beb8ac10b37a979544e636382e44955c7c4a128fb1d29cabe` |
| V111 | `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:65` | `val groupedItems: StateFlow<List<NotificationListItem>> = combine( groupedNotifications, _selectedIds, _collapsedGroups, _expandedGroups ) { groups, selected, collapsed, expanded ->` | `4b5369cb7cf2f06beb8ac10b37a979544e636382e44955c7c4a128fb1d29cabe` |
| V112 | `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt:56` | `mockkStatic(Log::class) every { Log.e(any(), any()) } returns 0 every { Log.e(any(), any(), any()) } returns 0` | `cba21670ad7fefd9000ec0c6062382e7da230c3ff71f2080033cdf376760bd9a` |
| V113 | `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt:60` | `mockkStatic(Base64::class) every { Base64.encodeToString(any(), any()) } answers { JavaBase64.getEncoder().encodeToString(firstArg<ByteArray>())` | `cba21670ad7fefd9000ec0c6062382e7da230c3ff71f2080033cdf376760bd9a` |
| V114 | `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt:78` | `fun basicAuthHeader(username: String, password: String): String { val credentials = "$username:$password".toByteArray() return "Basic ${Base64.getEncoder().encodeToString(credentials)}"` | `6a08956191e554511b64c33e1198c43a0a1b260d4830ccf575c610ea9175d3bf` |
| V115 | `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt:65` | `mockkObject(UrlUtils) every { UrlUtils.getUrl() } returns "http://mockurl" every { UrlUtils.header } returns "Basic mockheader"` | `cba21670ad7fefd9000ec0c6062382e7da230c3ff71f2080033cdf376760bd9a` |
| V116 | `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt:69` | `mockkObject(AndroidDecrypter.Companion) every { AndroidDecrypter.androidDecrypter(any(), any(), any(), any()) } returns true ` | `cba21670ad7fefd9000ec0c6062382e7da230c3ff71f2080033cdf376760bd9a` |
| V117 | `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt:83` | `@After fun tearDown() { unmockkAll()` | `cba21670ad7fefd9000ec0c6062382e7da230c3ff71f2080033cdf376760bd9a` |
| V118 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:177` | `suspend fun toggleSortOrder(list: List<ResourceListModel>): List<ResourceListModel> = withContext(dispatcherProvider.io) { sortMode = SortMode.DATE isAscending = !isAscending` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V119 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:185` | `suspend fun toggleTitleSortOrder(list: List<ResourceListModel>): List<ResourceListModel> = withContext(dispatcherProvider.io) { sortMode = SortMode.TITLE isTitleAscending = !isTitleAscending` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V120 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:193` | `suspend fun applyCurrentSort(list: List<ResourceListModel>): List<ResourceListModel> = withContext(dispatcherProvider.io) { applyCurrentSortSynchronous(list) }` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V121 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:180` | `applyCurrentSort(list).also { if (_resourcesState.value.isNotEmpty()) _resourcesState.value = it }` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V122 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:188` | `applyCurrentSort(list).also { if (_resourcesState.value.isNotEmpty()) _resourcesState.value = it }` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V123 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:197` | `private fun applyCurrentSortSynchronous(list: List<ResourceListModel>): List<ResourceListModel> { return when (sortMode) { SortMode.DATE -> {` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V124 | `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt:217` | `context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) { val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)` | `a2f100721f31215e886242ca2f6835bc958382286838fc47960b8483b883bf25` |
| V125 | `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt:243` | `val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) { val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)` | `a2f100721f31215e886242ca2f6835bc958382286838fc47960b8483b883bf25` |
| V126 | `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt:62` | `launchTime = System.currentTimeMillis() surveyStarted = true _events.send(PublicSurveyEvent.SurveyLoaded(surveyId, teamId))` | `214e556905b0b97b177f9df66f93a345ed2b37570d9697bfaf7ce454c7f22b9b` |
| V127 | `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt:74` | `if (submission == null \|\| submission.lastUpdateTime < launchTime) { _isLoading.value = false _events.send(PublicSurveyEvent.NavigateOnward)` | `214e556905b0b97b177f9df66f93a345ed2b37570d9697bfaf7ce454c7f22b9b` |
| V128 | `app/src/main/java/org/ole/planet/myplanet/di/TimeModule.kt:16` | `fun provideTimeProvider(): TimeProvider = SystemTimeProvider() }` | `8461b7d0678c53a64e065e7755c75f96f86f8636ce7d9237e6186eeaef816c63` |
| V129 | `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt:62` | `val intent = Intent(Constants.ACTION_RETRY_EVENT).apply { putExtra("url", request.url.encodedPath) putExtra("attempt", tryCount)` | `506678518e3e54b829bae4804440b359e909f9114eb1746d4bc74f99d31efb33` |
| V130 | `app/src/main/java/org/ole/planet/myplanet/services/BroadcastService.kt:18` | `fun trySendBroadcast(intent: Intent): Boolean { return _events.tryEmit(intent) }` | `0fc7da2523f0270614dae1d4c58fef15e67f4a72d77ff7dc402fe709f4b7b04a` |
| V131 | `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt:24` | `@RunWith(RobolectricTestRunner::class) @Config(manifest = Config.NONE, application = Application::class) class RetryInterceptorEventTest {` | `4c3168adf75e2f6e19e9e80693c245dc0e1d2a3e536f62a416ac3f67a0529f41` |
| V132 | `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt:40` | `fun testRetryEventUrlContainsOnlyEncodedPath() { val broadcastService = mockk<BroadcastService>(relaxed = true) val timeProvider = TestTimeProvider()` | `4c3168adf75e2f6e19e9e80693c245dc0e1d2a3e536f62a416ac3f67a0529f41` |
| V133 | `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt:68` | `val capturedIntent = intentSlot.captured val urlExtra = capturedIntent.getStringExtra("url") ` | `4c3168adf75e2f6e19e9e80693c245dc0e1d2a3e536f62a416ac3f67a0529f41` |
| V134 | `.github/scripts/coauthors.sh:23` | `collab_raw=$( gh api "repos/$REPO/collaborators?per_page=100" --paginate \ --jq '.[] \| select(.type == "User") \| .login' \| tr '[:upper:]' '[:lower:]'` | `bedd457b6205dd41a06ebd42be44c2f09bbbd409d2b4cd43deb71fd8a9023c26` |
| V135 | `.github/scripts/coauthors.sh:75` | `while IFS= read -r login; do if [ -z "$login" ]; then continue` | `bedd457b6205dd41a06ebd42be44c2f09bbbd409d2b4cd43deb71fd8a9023c26` |
| V136 | `.github/scripts/coauthors.sh:81` | `case "$l" in *'[bot]') continue ;; esac` | `bedd457b6205dd41a06ebd42be44c2f09bbbd409d2b4cd43deb71fd8a9023c26` |
| V137 | `.github/scripts/coauthors.sh:93` | `if [[ -z "${collaborators["$l"]:-}" ]]; then continue fi` | `bedd457b6205dd41a06ebd42be44c2f09bbbd409d2b4cd43deb71fd8a9023c26` |
| V138 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:72` | `val baseDirPath = withContext(dispatcherProvider.io) { storagePathResolver.resolveExternalFilesDir()?.toString() }` | `c79c75559354d09daaff4f5009df417ffd404a2effa9edf447dd2276ddec5aca` |
| V139 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:76` | `val markdownContentWithLocalPaths = MarkdownUtils.prependBaseUrlToImages( data.step.description, "file://${baseDirPath}/ole/",` | `c79c75559354d09daaff4f5009df417ffd404a2effa9edf447dd2276ddec5aca` |
| V140 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:82` | `val notDownloaded = data.resources.filter { !it.isResourceOffline() }  _uiState.value = CourseStepUiState(` | `c79c75559354d09daaff4f5009df417ffd404a2effa9edf447dd2276ddec5aca` |
| V141 | `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt:23` | `mockkStatic(TextUtils::class) every { TextUtils.isEmpty(any()) } answers { val str = arg<CharSequence?>(0)` | `450d5a8052903f4cfb1179bc3d8652591223d953f7ffadc7adb0f0c7f6be97c8` |
| V142 | `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt:24` | `every { TextUtils.isEmpty(any()) } answers { val str = arg<CharSequence?>(0) str.isNullOrEmpty()` | `450d5a8052903f4cfb1179bc3d8652591223d953f7ffadc7adb0f0c7f6be97c8` |
| V143 | `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt:28` | `mockkObject(NetworkUtils) every { NetworkUtils.getUniqueIdentifier() } returns "uniqueIdentifier" }` | `450d5a8052903f4cfb1179bc3d8652591223d953f7ffadc7adb0f0c7f6be97c8` |
| V144 | `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt:34` | `unmockkAll() } ` | `450d5a8052903f4cfb1179bc3d8652591223d953f7ffadc7adb0f0c7f6be97c8` |
| V145 | `.github/scripts/automerge.sh:336` | `publish_failed() { local sha=$1 run_id job_id note [ -n "$PUBLISH_JOB" ] \|\| return 1` | `a15b055fa02776305070653735b11bc7dc5977b5a58b063c39b6a77ad7af6726` |
| V146 | `.github/scripts/automerge.sh:339` | `for run_id in $(gh api "repos/$REPO/actions/runs?head_sha=$sha&per_page=100" \ --jq '.workflow_runs[] \| select(.status == "completed") \| .id' 2>/dev/null \|\| true); do job_id=$(gh api "repos/$REPO/actions/runs/$run_id/jobs?per_page=100" \` | `a15b055fa02776305070653735b11bc7dc5977b5a58b063c39b6a77ad7af6726` |
| V147 | `.github/scripts/automerge.sh:344` | `note=$(gh api "repos/$REPO/check-runs/$job_id/annotations?per_page=100" \ --jq "[.[] \| select(.annotation_level == \"warning\") \| .message \| select(startswith(\"$PUBLISH_FAIL_MARKER\"))] \| first" 2>/dev/null \|\| true) if [ -n "$note" ] && [ "$note" != "null" ]; then` | `a15b055fa02776305070653735b11bc7dc5977b5a58b063c39b6a77ad7af6726` |
| V148 | `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:193` | `private suspend fun loadShareTargets(parentCode: String?, communityName: String?, userId: String?): ChatShareTargets { val teams = teamsRepository.getTeamSummaries(userId) val enterprises = teamsRepository.getShareableEnterpriseSummaries(userId)` | `eb33d65f2358e81086aeaffb3be88052381470ad598eca34a1e0174d57a5a89d` |
| V149 | `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:196` | `val communityId = if (!communityName.isNullOrBlank() && !parentCode.isNullOrBlank()) { "$communityName@$parentCode" } else {` | `eb33d65f2358e81086aeaffb3be88052381470ad598eca34a1e0174d57a5a89d` |
| V150 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt:55` | `suspend fun getAvailableCourses(teamId: String): List<MyCourse> { val existingIds = teamsRepository.getTeamCourseIds(teamId).toSet() val allCourses = coursesRepository.getAllCourses()` | `924058b3ff7f1db8e62b904a8224c3f8189a049f263f7e3db41c9a611adf424f` |
| V151 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt:109` | `val availableCourses = viewModel.getAvailableCourses(teamId)  if (availableCourses.isEmpty()) {` | `b9341866ca64a021bc3171f83801188e81f162a16a6c76d30684dba57656580e` |
| V152 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:14` | `@Query("SELECT * FROM my_life WHERE (:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId) ORDER BY weight ASC") fun observeByUserId(userId: String?): Flow<List<MyLife>> ` | `94d3ceb1c89a06e86c0b99db3e8d037a105720953a6379f77801051733536712` |
| V153 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:20` | `@Query("SELECT * FROM my_life WHERE ((:userId IS NULL AND (userId IS NULL OR userId = '' OR userId = '--')) OR (:userId IS NOT NULL AND userId = :userId)) AND isVisible = 1 ORDER BY weight ASC") suspend fun getVisibleByUserId(userId: String?): List<MyLife> ` | `94d3ceb1c89a06e86c0b99db3e8d037a105720953a6379f77801051733536712` |
| V154 | `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:19` | `private fun normalizeUserId(userId: String?): String? { return userId?.takeIf { it.isNotBlank() && it != "--" } }` | `b42a74bf53f52090966434496ecf956f4dcfbcf552fb14b1a123d34b10742c93` |
| V155 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt:177` | `private suspend fun loadAssignableMembers(): List<UserEntity>? { val userList = teamsTasksViewModel.getJoinedMembers(teamId) val filteredUserList = userList.filter { user -> user.getFullName().isNotBlank() \|\| !user.name.isNullOrBlank() }` | `2df9e9e5f0eabf582751443cfd0b8336aed0cbfe19df2ac8e9ea9e19f8ca96d9` |
| V156 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt:405` | `fun filterAllTasks(tasks: List<TeamTask>): List<TeamTask> { return tasks.sortedWith(compareBy<TeamTask> { it.effectiveStatus() == TaskStatus.COMPLETED.value }.thenByDescending { it.deadline }) }` | `2df9e9e5f0eabf582751443cfd0b8336aed0cbfe19df2ac8e9ea9e19f8ca96d9` |
| V157 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksViewModel.kt:24` | `data class TaskCreatedOrUpdated(val isCreated: Boolean, val assigneeId: String?) : TaskActionEvent() data object TaskDeleted : TaskActionEvent() data class TaskAssigned(val userName: String) : TaskActionEvent()` | `7f9cafb26f88bb6f804b7bec557dd26468f3631f4b7acf21bcda6bea1e2c9f5e` |
| V158 | `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt:553` | `private fun saveConcatenatedLinksToPrefs() { val existingJsonLinks = sharedPrefManager.getConcatenatedLinks() val existingConcatenatedLinks = if (existingJsonLinks != null) {` | `0323893016cd0f313cc3eaeb8b437e4f965700e0b776838ef92256139eb663e4` |
| V159 | `app/src/main/java/org/ole/planet/myplanet/model/MyCourse.kt:96` | `fun saveConcatenatedLinksToPrefs(spm: SharedPrefManager) { val existingJsonLinks = spm.getConcatenatedLinks() val existingConcatenatedLinks = if (existingJsonLinks != null) {` | `f67ecdf80837e65d1eb05168539e8efe75133cac869bd86458b067a94f676d61` |
| V160 | `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt:79` | `db = Room.inMemoryDatabaseBuilder( RuntimeEnvironment.getApplication(), AppDatabase::class.java` | `e053f7069fa2dfd42376f9a17a2fe84b3f43b24e1458b6970a43528832fcbf51` |
| V161 | `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt:98` | `db.close() unmockkObject(UrlUtils, NetworkUtils) }` | `e053f7069fa2dfd42376f9a17a2fe84b3f43b24e1458b6970a43528832fcbf51` |
| V162 | `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt:71` | `mockkObject(UrlUtils) every { UrlUtils.getUrl() } returns "http://test.local" mockkObject(NetworkUtils)` | `e053f7069fa2dfd42376f9a17a2fe84b3f43b24e1458b6970a43528832fcbf51` |
| V163 | `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt:73` | `mockkObject(NetworkUtils) every { NetworkUtils.getUniqueIdentifier() } returns "android-id" // Non-null String return => a relaxed mock yields "" and gson.fromJson("") returns null,` | `e053f7069fa2dfd42376f9a17a2fe84b3f43b24e1458b6970a43528832fcbf51` |
| V164 | `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt:302` | `mockkObject(NetworkUtils) every { NetworkUtils.getUniqueIdentifier() } returns "test-device" ` | `e053f7069fa2dfd42376f9a17a2fe84b3f43b24e1458b6970a43528832fcbf51` |
| V165 | `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt:130` | `override fun getCourseDetailModel(courseId: String): Flow<CourseDetailModel?> { return getCourseByCourseIdFlow(courseId).map { course -> if (course == null) return@map null` | `8a46a45f200bab7deb758f3cc02e49b08b781b678a89af028bf4c84b8588ceb7` |
| V166 | `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt:134` | `val user = userRepository.get().getUserModel() val examCount = getCourseExamCount(courseId) val resources = getCourseOnlineResources(courseId)` | `8a46a45f200bab7deb758f3cc02e49b08b781b678a89af028bf4c84b8588ceb7` |
| V167 | `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt:163` | `val userId = user?.id val ratingSummary = if (userId != null) { ratingsRepository.getRatingSummary("course", courseId, userId)` | `8a46a45f200bab7deb758f3cc02e49b08b781b678a89af028bf4c84b8588ceb7` |
| V168 | `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:33` | `@ApplicationContext private val context: Context, private val dispatcherProvider: DispatcherProvider ) : ViewModel() {` | `4b5369cb7cf2f06beb8ac10b37a979544e636382e44955c7c4a128fb1d29cabe` |
| V169 | `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:298` | `context.getString(R.string.resource_notification, count) } ?: notification.message }` | `4b5369cb7cf2f06beb8ac10b37a979544e636382e44955c7c4a128fb1d29cabe` |
| V170 | `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:320` | `val userRequestedStr = context.getString(R.string.user_requested_to_join_team, requesterName, teamName) formatJoinRequestNotification( context.getString(R.string.join_request_prefix),` | `4b5369cb7cf2f06beb8ac10b37a979544e636382e44955c7c4a128fb1d29cabe` |
| V171 | `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:30` | `private val userRepository: UserRepository, savedStateHandle: SavedStateHandle ) : ViewModel() {` | `365324bc8b7d353b9341f1062cc871429d89d14a4118e66ec55c141fe14b7d0f` |
| V172 | `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:38` | `val user = submission.userId?.let { userRepository.getUserById(it) } emit(submissionsRepository.getSubmissionDetail(submission, user)) } else {` | `365324bc8b7d353b9341f1062cc871429d89d14a4118e66ec55c141fe14b7d0f` |
| V173 | `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:35` | `private val submissionDetailState: StateFlow<SubmissionDetail?> = flow { val submission = submissionsRepository.getSubmissionByRemoteIdOrParentId(submissionId) if (submission != null) {` | `365324bc8b7d353b9341f1062cc871429d89d14a4118e66ec55c141fe14b7d0f` |
| V174 | `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:43` | `}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)  val uiState: StateFlow<SubmissionDetailUiState> = submissionDetailState` | `365324bc8b7d353b9341f1062cc871429d89d14a4118e66ec55c141fe14b7d0f` |
| V175 | `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:59` | `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubmissionDetailUiState()) }` | `365324bc8b7d353b9341f1062cc871429d89d14a4118e66ec55c141fe14b7d0f` |
| V176 | `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt:107` | `override suspend fun insertFeedbackList(jsonObjects: List<JsonObject>) { val ids = jsonObjects.map { GsonUtils.getString("_id", it) } val rows = (feedbackDao.getByIds(ids) + feedbackDao.getByRemoteIds(ids)).distinctBy { it.id }` | `d75dc899d86a590ea226a6ab0ad4841ad572965f431030bb72b0c18479e86630` |
| V177 | `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt:110` | `val existingByRemoteId = rows.groupBy { it._id ?: it.id }.mapValues { (remoteId, matches) -> matches.firstOrNull { !it.isUploaded } ?: matches.firstOrNull { it.id != remoteId } ?: matches.first() }` | `d75dc899d86a590ea226a6ab0ad4841ad572965f431030bb72b0c18479e86630` |
| V178 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt:25` | `@Query("SELECT * FROM feedback WHERE id IN (:ids)") suspend fun getByIdsInternal(ids: List<String>): List<Feedback> ` | `1f642d2115f8912f6ae12f268b2ec3828e7306887bcda75b4bbb72e7100461c4` |
| V179 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt:33` | `@Query("SELECT * FROM feedback WHERE _id IN (:remoteIds)") suspend fun getByRemoteIdsInternal(remoteIds: List<String>): List<Feedback> ` | `1f642d2115f8912f6ae12f268b2ec3828e7306887bcda75b4bbb72e7100461c4` |
| V180 | `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt:18` | `private val feedbackDao: FeedbackDao, private val gson: Gson, private val timeProvider: TimeProvider ) : FeedbackRepository, FeedbackSyncWriter { ` | `d75dc899d86a590ea226a6ab0ad4841ad572965f431030bb72b0c18479e86630` |
| V181 | `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt:145` | `messages = GsonUtils.gson.toJson(GsonUtils.getJsonArray("messages", act)) isUploaded = true }` | `d75dc899d86a590ea226a6ab0ad4841ad572965f431030bb72b0c18479e86630` |
| V182 | `app/src/main/java/org/ole/planet/myplanet/model/News.kt:125` | `val reactionsJson: JsonObject get() = try { reactions?.let { GsonUtils.gson.fromJson(it, JsonObject::class.java) } ?: JsonObject()` | `3cdc70e3185b09948348f0bde2b1bade66948a7467bf15b9e6734fae6fe4b750` |
| V183 | `app/src/main/java/org/ole/planet/myplanet/model/News.kt:133` | `val reactionsMap: Map<String, List<String>> get() { val obj = reactionsJson` | `3cdc70e3185b09948348f0bde2b1bade66948a7467bf15b9e6734fae6fe4b750` |
| V184 | `app/src/main/java/org/ole/planet/myplanet/model/News.kt:147` | `val pendingReactionsMap: Map<String, String> get() = try { pendingReactions?.let { GsonUtils.gson.fromJson(it, JsonObject::class.java) }` | `3cdc70e3185b09948348f0bde2b1bade66948a7467bf15b9e6734fae6fe4b750` |
| V185 | `app/src/main/java/org/ole/planet/myplanet/model/News.kt:173` | `reactionsMap.forEach { (e, users) -> currentMap[e] = users.toMutableList() }  currentMap.forEach { (e, users) -> if (e != emoji) users.remove(userId) }` | `3cdc70e3185b09948348f0bde2b1bade66948a7467bf15b9e6734fae6fe4b750` |
| V186 | `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt:149` | `mockkStatic(Glide::class) try { every { Glide.with(any<Context>()) } returns requestManager` | `9833f5c7e5eec00685f03e7d0491af481eb16a93ac9b2299a76c3e5948be13d8` |
| V187 | `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt:215` | `mockkStatic(Glide::class) try { every { Glide.with(any<Context>()) } returns requestManager` | `9833f5c7e5eec00685f03e7d0491af481eb16a93ac9b2299a76c3e5948be13d8` |
| V188 | `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt:235` | `mockkStatic(Glide::class) try { every { Glide.with(any<Context>()) } returns requestManager` | `9833f5c7e5eec00685f03e7d0491af481eb16a93ac9b2299a76c3e5948be13d8` |
| V189 | `.github/workflows/build.yml:3` | `on: push: branches:` | `49eded1d139681dbf70a332c28e69b94e02210dbeab17ca9a026596e65704c50` |
| V190 | `.github/workflows/build.yml:41` | `- name: restore project build dir uses: actions/cache@v6 with:` | `49eded1d139681dbf70a332c28e69b94e02210dbeab17ca9a026596e65704c50` |
| V191 | `.github/workflows/build.yml:50` | `key: build-dir-build-${{ matrix.build }}-${{ github.sha }} restore-keys: \| build-dir-build-${{ matrix.build }}-` | `49eded1d139681dbf70a332c28e69b94e02210dbeab17ca9a026596e65704c50` |
| V192 | `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:339` | `private fun sortUsers(users: List<UserEntity>, fieldName: String, descending: Boolean): List<UserEntity> { fun value(value: String?) = value.orEmpty().lowercase() ` | `c6d11dbf6480bd1c8239147888c74913bf862beff958c6d4f3f5895da07a1065` |
| V193 | `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginator.kt:19` | `suspend fun parseAndBuildInitialPage(newsConversations: String?): List<ChatMessage> { val parsedConversations = withContext(dispatcherProvider.io) { if (newsConversations.isNullOrBlank()) return@withContext emptyList()` | `1b8c2bf729e5ec532110a53d161dcd492ba10eafc08b9a68aed53a908f820b51` |
| V194 | `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginator.kt:20` | `val parsedConversations = withContext(dispatcherProvider.io) { if (newsConversations.isNullOrBlank()) return@withContext emptyList() try {` | `1b8c2bf729e5ec532110a53d161dcd492ba10eafc08b9a68aed53a908f820b51` |
| V195 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:42` | `private val userRepository: UserRepository, private val ratingsRepository: RatingsRepository ) : ViewModel() {` | `6a5d1015f3a72de30c830ba5b766a08c50352bf734260d4fb52f3980864990d7` |
| V196 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:49` | `private val userRepository: UserRepository, private val resourcesRepository: ResourcesRepository, private val configurationsRepository: ConfigurationsRepository,` | `c79c75559354d09daaff4f5009df417ffd404a2effa9edf447dd2276ddec5aca` |
| V197 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:68` | `val user = userRepository.getUserModel() val data = coursesRepository.getCourseStepData(stepId ?: "", user?.id) val title = data.step.courseId?.let { coursesRepository.getCourseTitleById(it) }` | `c79c75559354d09daaff4f5009df417ffd404a2effa9edf447dd2276ddec5aca` |
| V198 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt:30` | `import org.ole.planet.myplanet.services.UserSessionManager import org.ole.planet.myplanet.ui.components.FragmentNavigator import org.ole.planet.myplanet.ui.ratings.RatingsFragment` | `931f47f1c876ac3158605341c881fd71861b15bd376b5ba7c0a0b83c1a2ebf2a` |
| V199 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt:43` | `lateinit var userSessionManager: UserSessionManager private val viewModel: TakeCourseViewModel by viewModels() private var courseId: String? = null` | `931f47f1c876ac3158605341c881fd71861b15bd376b5ba7c0a0b83c1a2ebf2a` |
| V200 | `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt:24` | `private val userRepository: UserRepository ) : ViewModel() { ` | `c30684450223868d39d54c09243bc4b221e4b72ceab14e3e797a4d9e964bacc4` |
| V201 | `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt:42` | `val userId = userRepository.getUserModel()?.id ?: return@launch teamsRepository.getMyTeamsFlow(userId) .distinctUntilChangedBy { teams -> teams.map { t -> t._id to t.name } }` | `c30684450223868d39d54c09243bc4b221e4b72ceab14e3e797a4d9e964bacc4` |
| V202 | `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt:24` | `private val userRepository: UserRepository ) : ViewModel() { ` | `239d9d10642f50993221b9da5436a6a26f0b27788543bd32abd83789ec7d5e66` |
| V203 | `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt:33` | `val user = userRepository.getUserModel() val planetCode = user?.planetCode.orEmpty() ` | `239d9d10642f50993221b9da5436a6a26f0b27788543bd32abd83789ec7d5e66` |
| V204 | `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt:13` | `private val userRepository: UserRepository, private val activitiesRepository: ActivitiesRepository, private val userSessionManager: UserSessionManager` | `700aa69b4bc9dc2e56ce28c206ddad67cc1f99780a89776b7226efd8af1b4f8f` |
| V205 | `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt:19` | `return userRepository.getUserModel() } ` | `700aa69b4bc9dc2e56ce28c206ddad67cc1f99780a89776b7226efd8af1b4f8f` |
| V206 | `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt:22` | `private val userRepository: UserRepository, private val activitiesRepository: ActivitiesRepository, private val dispatcherProvider: DispatcherProvider` | `f860bd4e3c0d3c172610a44737aea7f0ffc29ad21c7b5ca5a2eddd62e2f1984a` |
| V207 | `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt:39` | `val userName = userRepository.getUserModel()?.name ?: return@flow emitAll(activitiesRepository.getOfflineLogins(userName)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())` | `f860bd4e3c0d3c172610a44737aea7f0ffc29ad21c7b5ca5a2eddd62e2f1984a` |
| V208 | `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt:31` | `private val userRepository: UserRepository ) : ViewModel() { ` | `c17ea89203563ac1a063cea9f42bbbb053d528ce6a817b19ae1163fcd4593f62` |
| V209 | `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt:35` | `val user = userRepository.getUserModel() emitAll(personalsRepository.getPersonalResources(user?.id)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())` | `c17ea89203563ac1a063cea9f42bbbb053d528ce6a817b19ae1163fcd4593f62` |
| V210 | `app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt:21` | `private val userRepository: UserRepository ) : ViewModel() { ` | `d45e31a2b4c263ebfb92c358974ba255c778a69e3b54d0af858e271967033107` |
| V211 | `app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt:49` | `return userRepository.getUserModel()?.isGuest() ?: true } }` | `d45e31a2b4c263ebfb92c358974ba255c778a69e3b54d0af858e271967033107` |
| V212 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:31` | `private val userRepository: UserRepository, private val dispatcherProvider: DispatcherProvider ) : ViewModel() {` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V213 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:39` | `userRepository.getUserModel().also { _currentUser.value = it }` | `1afd4d5a965d9b4ed360960d5e7bc7727730c990656e73f4efebb73fcdc033ea` |
| V214 | `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt:30` | `private val userRepository: UserRepository, private val sharedPrefManager: SharedPrefManager, private val storagePathResolver: StoragePathResolver,` | `1b0204ce7c0f21621e19150385670364b06cbccd2ce9a83892f229654dd2f3e0` |
| V215 | `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt:40` | `val userId = userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() } ?: return false  if (isRatingPrompted(userId, resourceId)) {` | `1b0204ce7c0f21621e19150385670364b06cbccd2ce9a83892f229654dd2f3e0` |
| V216 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt:49` | `@Query("DELETE FROM team_log WHERE id IN (:ids)") suspend fun deleteByIdsInternal(ids: List<String>) ` | `8ad30c4c0a8e6f4cb56aa42e670de3628c1a11ac8376f4ad8f8964571dbd65d0` |
| V217 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt:52` | `suspend fun deleteByIds(ids: List<String>) { ids.distinct().chunked(900).forEach { deleteByIdsInternal(it) } }` | `8ad30c4c0a8e6f4cb56aa42e670de3628c1a11ac8376f4ad8f8964571dbd65d0` |
| V218 | `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt:77` | `private fun checkMultipleSelectAnswer( listAns: Map<String, String>?, correctChoices: List<String>?` | `9b05ee770878668103d3d475c8fea0bc9e8fd3e9877b174b54301e742b356f55` |
| V219 | `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt:201` | `private fun getSortDate(survey: StepExam): Long { return if (survey.sourceSurveyId != null) { if (survey.adoptionDate > 0) survey.adoptionDate else survey.createdDate` | `d9a4ec20cb310664c458886632c7bb91455491bb418eaa624ed6226928578808` |
| V220 | `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt:209` | `private fun filter(s: String, list: List<StepExam>): List<StepExam> { val normalizedQueryParts = s.splitToSequence(" ") .filterNot { it.isEmpty() }` | `d9a4ec20cb310664c458886632c7bb91455491bb418eaa624ed6226928578808` |
| V221 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt:68` | `val model = viewModel.createGuestUser(username) if (model == null) { toast(this@showGuestLoginDialog, getString(R.string.unable_to_login))` | `ba791a215f3356c9aac056b18578b993bec8f4edbb9c098a76a5674148f7eec6` |
| V222 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt:597` | `val model = loginViewModel.createGuestUser(user.name ?: "") if (model == null) { toast(this@LoginActivity, getString(R.string.unable_to_login))` | `0b36414ea7389f2c5fc7e52e0b3b8c43c6da870cbe72ed311cfbdc6dc7c5918b` |
| V223 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt:639` | `val model = loginViewModel.createGuestUser(username) if (model == null) { toast(this@LoginActivity, getString(R.string.unable_to_login))` | `0b36414ea7389f2c5fc7e52e0b3b8c43c6da870cbe72ed311cfbdc6dc7c5918b` |
| V224 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt:390` | `suspend fun authenticateUser(username: String?, password: String?, isManagerMode: Boolean): Boolean { return try { if (!userRepository.hasAtLeastOneUser()) {` | `0f8ec53950f238b2e3b261cc471b64481825f852576f5ad35c2842473ee86054` |
| V225 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt:89` | `suspend fun createGuestUser(username: String): UserEntity? = userRepository.createGuestUser(username)  private var communityDocsSynced = false` | `843699c8d6824b57ab3dca9aa43a800662c6108d49f16819b78d6544553c8932` |
| V226 | `app/src/test/java/org/ole/planet/myplanet/services/ServerReachabilityWorkerTest.kt:47` | `@Before fun setUp() { mockkStatic(Log::class)` | `4c237e840df388759272d710b88dc253b430661145b24b9d00261caf8cf288ca` |
| V227 | `app/src/test/java/org/ole/planet/myplanet/services/ServerReachabilityWorkerTest.kt:94` | `verify(exactly = 0) { Log.w(any<String>(), any<String>(), any<Throwable>()) } } ` | `4c237e840df388759272d710b88dc253b430661145b24b9d00261caf8cf288ca` |
| V228 | `app/src/test/java/org/ole/planet/myplanet/services/ServerReachabilityWorkerTest.kt:125` | `verify(exactly = 1) { Log.w("ServerReachabilityWorker", "doWork failed", any<IllegalStateException>()) }` | `4c237e840df388759272d710b88dc253b430661145b24b9d00261caf8cf288ca` |
| V229 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt:67` | `fun loadJoinedMembers(teamId: String) { viewModelScope.launch { val members = teamsRepository.getJoinedMembersWithVisitInfo(teamId)` | `73897cab5f7e6a2bc9393c690b71ad91149ffa82bb75917ed87c5e5cb56d82e7` |
| V230 | `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt:21` | `@Config(sdk = [28]) class PdfThumbnailLoaderTest { ` | `3db3add8377d3c84fb22650af0b47f458d8d2b5d3d872ef3a5ad543d13f81471` |
| V231 | `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt:17` | `suspend fun firstPageBitmap(file: File, dispatcherProvider: DispatcherProvider, targetWidthPx: Int): Bitmap? { if (targetWidthPx <= 0) return null return withContext(dispatcherProvider.io) {` | `aa6c2edc4241ed1717adb0c0aa12fee605064402c8937776772f12053d316da6` |
| V232 | `app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt:35` | `@Before fun setup() { MockKAnnotations.init(this)` | `30da59d8b86ee39cd20be2c7445fce22766a7786a2a3aa06ad5d3c5f513811f6` |
| V233 | `app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt:47` | `mockkStatic(Base64::class) // Mock toUri extension function mockkStatic(Uri::class)` | `30da59d8b86ee39cd20be2c7445fce22766a7786a2a3aa06ad5d3c5f513811f6` |
| V234 | `app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt:55` | `@After fun tearDown() { try {` | `30da59d8b86ee39cd20be2c7445fce22766a7786a2a3aa06ad5d3c5f513811f6` |
| V235 | `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt:1168` | `private fun showZoomableImage(context: Context, imageUrl: String) { val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) val view = LayoutInflater.from(context).inflate(R.layout.dialog_zoomable_image, null)` | `5ab91519c3084cbd99d58479c43a01954df225f35bf86f04d20187734ed52702` |
| V236 | `app/src/main/java/org/ole/planet/myplanet/utils/ImageViewerUtils.kt:17` | `fun showZoomableImage(context: Context, imagePath: String) { activeDialog?.dismiss() ` | `2825da0fec50a1aced93291fa817cdf53b1376a7c81c0ce5286d450c4f331db4` |
| V237 | `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt:1097` | `val size = binding.imgNews.context.resources.getDimensionPixelSize(R.dimen.image_thumbnail_size_120) loadGlideImage(file, binding.imgNews, size) binding.imgNews.visibility = View.VISIBLE` | `5ab91519c3084cbd99d58479c43a01954df225f35bf86f04d20187734ed52702` |
| V238 | `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt:1108` | `val size = context.resources.getDimensionPixelSize(R.dimen.image_thumbnail_size_100) val margin = (4 * context.resources.displayMetrics.density).toInt() val params = ViewGroup.MarginLayoutParams(size, size)` | `5ab91519c3084cbd99d58479c43a01954df225f35bf86f04d20187734ed52702` |
| V239 | `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt:1132` | `val size = binding.imgNews.context.resources.getDimensionPixelSize(R.dimen.image_thumbnail_size_120) loadGlideImage(imageFile, binding.imgNews, size) binding.imgNews.visibility = View.VISIBLE` | `5ab91519c3084cbd99d58479c43a01954df225f35bf86f04d20187734ed52702` |
| V240 | `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt:38` | `val initialList = lifeRepository.getMyLifeByUserId(userId, LifeItemDefaults.forUser(userId, resolveLabel)) _myLifeList.value = initialList ` | `2ab19ec45dacad4f75a391cb16779971d6d3b238d35e05aeccf138096d38a3f4` |
| V241 | `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt:41` | `lifeRepository.observeMyLifeByUserId(userId) .distinctUntilChanged() .collect { list ->` | `2ab19ec45dacad4f75a391cb16779971d6d3b238d35e05aeccf138096d38a3f4` |
| V242 | `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:127` | `override suspend fun seedMyLifeIfEmpty(userId: String?, items: List<MyLife>): List<MyLife> { val effectiveUserId = normalizeUserId(userId) return seedMutex.withLock {` | `b42a74bf53f52090966434496ecf956f4dcfbcf552fb14b1a123d34b10742c93` |
| V243 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CommunityDao.kt:12` | `@Query("SELECT * FROM community ORDER BY weight ASC") abstract suspend fun getAllSorted(): List<Community> ` | `bfbdee247f557b7fb06c640eba85481617546a6e5d21f6dba0b2b300eb1eb258` |
| V244 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt:298` | `val communities = communityRepository.getAllSorted() val nonEmptyCommunities = communities.filter { !TextUtils.isEmpty(it.name) } val adapter = ArrayAdapter(this@setupManualConfigEnabled, android.R.layout.simple_spinner_item, nonEmptyCommunities)` | `4d0ccc7a17b4ac5e1fd4161b693bb2ec90d783294805481df22ab0d4c8d11617` |
| V245 | `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt:84` | `override suspend fun toggleAttendance(meetupId: String, userId: String): Meetup? { if (meetupId.isBlank()) { return null` | `ab6d57fc1029a6f37c1e48289c96ea96732be23ee024c986b2b9e07a321a85e4` |
| V246 | `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt:93` | `val meetup = meetupDao.getByMeetupId(meetupId) ?: return null val isJoined = !meetup.userId.isNullOrEmpty() if (isJoined) {` | `ab6d57fc1029a6f37c1e48289c96ea96732be23ee024c986b2b9e07a321a85e4` |
| V247 | `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt:100` | `meetupDao.upsert(meetup)  return getMeetupById(meetupId)` | `ab6d57fc1029a6f37c1e48289c96ea96732be23ee024c986b2b9e07a321a85e4` |
| V248 | `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt:20` | `private val userRepository: UserRepository ) : ViewModel() { ` | `2ab19ec45dacad4f75a391cb16779971d6d3b238d35e05aeccf138096d38a3f4` |
| V249 | `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt:28` | `private suspend fun resolveUserId(): String? { val raw = userRepository.getCurrentUserId().orEmpty() .ifEmpty { userRepository.getUserModel()?.id.orEmpty() }` | `2ab19ec45dacad4f75a391cb16779971d6d3b238d35e05aeccf138096d38a3f4` |
| V250 | `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt:80` | `suspend fun getCurrentUserId(): String? suspend fun getConnectedCommunityCode(): String suspend fun getUserProfile(): UserEntity?` | `295ea60320bad481e14fbffebeea880d6f3aa7e331959142aaa07c685e115e19` |
| V251 | `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5` | `interface UserReadRepository { suspend fun getUserById(userId: String): UserEntity? suspend fun getUsersByIds(userIds: List<String>): List<UserEntity>` | `7ec50c52a11c9db35549797a0780e78a1d5a99b533491b11df27805cc27bba5d` |
| V252 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:34` | `@Query("SELECT _id FROM my_life WHERE _id = :id") suspend fun getIdsByIdInternal(id: String): List<String> ` | `94d3ceb1c89a06e86c0b99db3e8d037a105720953a6379f77801051733536712` |
| V253 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:40` | `@Query("UPDATE my_life SET isVisible = :isVisible WHERE _id = :id") suspend fun updateVisibilityByIdInternal(id: String, isVisible: Boolean) ` | `94d3ceb1c89a06e86c0b99db3e8d037a105720953a6379f77801051733536712` |
| V254 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:43` | `@Transaction suspend fun updateVisibility(id: String, isVisible: Boolean) { val exactMatches = getIdsByIdInternal(id)` | `94d3ceb1c89a06e86c0b99db3e8d037a105720953a6379f77801051733536712` |
| V255 | `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt:61` | `val iterationsValue = try { iterations?.takeIf { it.isNotBlank() }?.toInt() ?: 10 } catch (e: NumberFormatException) {` | `8cbb968587b7b94ec2d2b67a49df623882d0d6e426d792bc387e4d60075192b2` |
| V256 | `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt:117` | `fun encodeImageToBase64(imagePath: String?): String? { if (imagePath.isNullOrEmpty()) return null if (imagePath.startsWith("http://", ignoreCase = true) \|\| imagePath.startsWith("https://", ignoreCase = true)) {` | `8cbb968587b7b94ec2d2b67a49df623882d0d6e426d792bc387e4d60075192b2` |
| V257 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt:152` | `val userJson = payloadData.user?.serialize() ?: submission.user?.takeIf { it.isNotEmpty() }?.let { JsonParser.parseString(it).asJsonObject } if (userJson != null) {` | `b1300e6e77de26d9d1a99cdf384c86f595d671f25dd390f320c2e6e5ef564ad3` |
| V258 | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt:983` | `val teamMembers = teamDao.getByTeamIdAndDocType(teamId, "membership") .filter { !it.isDeletePending } // Filter so only the not pending members get query .mapNotNull { it.userId }` | `2e4bcba96e7f1393d8801ef4f090536a9214f53ae2a3f319cf42247fd22810af` |
| V259 | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt:1007` | `val teamUserIds = teamDao.getAllByTeamId(teamId).mapNotNull { it.userId }.toSet() val memberNames = members.mapTo(HashSet()) { it.name } val validAdmins = adminUsers.filter { admin ->` | `2e4bcba96e7f1393d8801ef4f090536a9214f53ae2a3f319cf42247fd22810af` |
| V260 | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt:1025` | `val leaderIds = teamDao.getByTeamIdAndDocType(teamId, "membership") .filter { it.isLeader } .mapNotNull { it.userId }` | `2e4bcba96e7f1393d8801ef4f090536a9214f53ae2a3f319cf42247fd22810af` |
| V261 | `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt:132` | `private fun sortChats(chats: List<ChatHistory>): List<ChatHistory> { return chats.sortedByDescending { chat -> maxOf(chat.createdDate?.toLongOrNull() ?: 0L, chat.updatedDate?.toLongOrNull() ?: 0L)` | `b01186a96e8c4020998e8e39ed548ab802760a22b0e9a594649c7e525396066a` |
| V262 | `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt:138` | `override suspend fun getLatestRev(id: String): String? { return chatDao.getRevsByDocId(id) .maxByOrNull { rev -> rev?.split("-")?.get(0)?.toIntOrNull() ?: 0 }` | `b01186a96e8c4020998e8e39ed548ab802760a22b0e9a594649c7e525396066a` |
| V263 | `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt:37` | `suspend fun getUsersSortedBy(fieldName: String, descending: Boolean): List<UserEntity> suspend fun getPendingSyncUsers(limit: Int): List<UserEntity> suspend fun searchUsers(query: String, sortField: String, descending: Boolean): List<UserEntity>` | `295ea60320bad481e14fbffebeea880d6f3aa7e331959142aaa07c685e115e19` |
| V264 | `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt:82` | `suspend fun getUserProfile(): UserEntity? suspend fun getUserImageUrl(): String? suspend fun getActiveUserIdSuspending(): String` | `295ea60320bad481e14fbffebeea880d6f3aa7e331959142aaa07c685e115e19` |
| V265 | `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt:90` | `suspend fun hasActiveUserSyncAction(): Boolean suspend fun markUserUploaded(userId: String, id: String, rev: String) suspend fun markUserKeyIvSaved(userId: String, key: String, iv: String?)` | `295ea60320bad481e14fbffebeea880d6f3aa7e331959142aaa07c685e115e19` |
| V266 | `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt:55` | `val user = userRepository.getUserProfile() _userState.value = user ` | `f81e306d57ce4fd7cfbc04c8dfcf71ec3ed942c69102789a3424742a3a3411b1` |
| V267 | `app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownViewModel.kt:17` | `return userRepository.hasActiveUserSyncAction() } ` | `237329504ed48b533fa65e366b84e0a03f35f34b21955a41ed1ce4933ed9bed9` |
| V268 | `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt:109` | `val result = if (query.isBlank()) userRepository.getUsersSortedBy(sortBy, descending) else userRepository.searchUsers(query, sortBy, descending) loadingJob.cancel() _patientList.value = result` | `74cd3c7817d6214e19a1bf0710b66da2453d4005af20232e331c62668e295dd1` |
| V269 | `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt:135` | `fun refreshChatHistory() { sharedViewModel.loadChatHistoryScreenData( sharedPrefManager.getUserId()` | `d01bc0654461d00484bbdba257d3a5954c2e28bda1cbf6f31c7c63ff8dc26590` |
| V270 | `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:187` | `private suspend fun loadCurrentUser(userId: String?): UserEntity? { if (userId.isNullOrEmpty()) { return null` | `eb33d65f2358e81086aeaffb3be88052381470ad598eca34a1e0174d57a5a89d` |
| V271 | `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:8` | `suspend fun getUserModel(): UserEntity? }` | `7ec50c52a11c9db35549797a0780e78a1d5a99b533491b11df27805cc27bba5d` |
| V272 | `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt:16` | `import org.mockito.Mockito.\`when\` import org.mockito.Mockito.mock import org.ole.planet.myplanet.model.QuestionAnswer` | `158fad9b70bb8060eeb3533294a884c0640760c83eb496d906add281e4d30db4` |
| V273 | `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesAdapterTest.kt:16` | `import org.mockito.ArgumentMatchers.any import org.mockito.ArgumentMatchers.eq import org.mockito.Mock` | `e33f9c250c11f7f8348693769a9bc082e266f24af52761971f527b83505e5ee6` |
| V274 | `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapterTest.kt:11` | `import org.mockito.Mock import org.mockito.Mockito.times import org.mockito.Mockito.verify` | `9ef314df73b55aea7c5664d2c315f50cdd02e4d5551574b5ec589c7eb02df349` |
| V275 | `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapterTest.kt:15` | `import org.mockito.Mockito.mock import org.mockito.Mockito.never import org.mockito.Mockito.verify` | `25100c184d8bcb4b23cda6fb91e14f2e04887b8b65ebfc876d4ab6b3128d17fb` |
| V276 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt:346` | `context.getString(R.string.http_protocol) -> context.getString(R.string.device_couldn_t_reach_local_server) context.getString(R.string.https_protocol) -> context.getString(R.string.device_couldn_t_reach_nation_server) else -> ""` | `0f8ec53950f238b2e3b261cc471b64481825f852576f5ad35c2842473ee86054` |
| V277 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt:649` | `MainApplication.applicationScope.launch { val canReachServer = MainApplication.isServerReachable(serverUrl) if (canReachServer) {` | `0f8ec53950f238b2e3b261cc471b64481825f852576f5ad35c2842473ee86054` |
| V278 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt:662` | `}.launchIn(MainApplication.applicationScope) } ` | `0f8ec53950f238b2e3b261cc471b64481825f852576f5ad35c2842473ee86054` |
| V279 | `.github/scripts/test_timing_summary.py:243` | `flagged = [] for elapsed, name, count, median_val, warmup, excl in classes: if count >= 5 and median_val >= 0.30:` | `fe8366c065b1e9da4d4bcfb2ac3030653b96e06b9baa5d47c94fd3fb52158a2a` |
| V280 | `.github/scripts/test_timing_summary.py:249` | `for root_dir, _, files in os.walk(args.test_sources): if target_file in files: filepath = os.path.join(root_dir, target_file)` | `fe8366c065b1e9da4d4bcfb2ac3030653b96e06b9baa5d47c94fd3fb52158a2a` |
| V281 | `.github/scripts/test_timing_summary.py:255` | `try: with open(filepath, "r", encoding="utf-8", errors="replace") as f: content = f.read()` | `fe8366c065b1e9da4d4bcfb2ac3030653b96e06b9baa5d47c94fd3fb52158a2a` |
| V282 | `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt:16` | `private val teamsRepository: TeamsRepository ) : ViewModel() { ` | `aeb92fd749c69e002baa31c58d903537e00b070cf6d2bb7528e585c4faf0f08d` |
| V283 | `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt:28` | `_teamLinks.value = teamsRepository.getTeamLinks() } }` | `aeb92fd749c69e002baa31c58d903537e00b070cf6d2bb7528e585c4faf0f08d` |
| V284 | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsShareRepository.kt:5` | `interface TeamsShareRepository { suspend fun getTeamSummaries(userId: String?): List<TeamSummary> suspend fun getShareableEnterpriseSummaries(userId: String?): List<TeamSummary>` | `9ea4862738f76894d4ee9a69f45bc5fd84c8f713886ddc43a7cc09214728600c` |
| V285 | `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt:7` | `suspend fun isMember(userId: String?, teamId: String): Boolean suspend fun isTeamLeader(teamId: String, userId: String?): Boolean suspend fun hasPendingRequest(teamId: String, userId: String?): Boolean` | `5025822d62c2afe94450aadc3f0914f51894a3625a37bed3557e4e11ad15d7d9` |
| V286 | `app/src/test/java/org/ole/planet/myplanet/model/HealthExaminationTest.kt:17` | `@Before fun setUp() { mockkStatic(Log::class)` | `a0212002a44c6bb08199629e9639ca02778cbd4b3c39ef5ce685051fa59632df` |
| V287 | `app/src/test/java/org/ole/planet/myplanet/model/HealthExaminationTest.kt:19` | `mockkStatic(Log::class) every { Log.isLoggable(any(), any()) } returns true every { Log.d(any(), any()) } returns 0` | `a0212002a44c6bb08199629e9639ca02778cbd4b3c39ef5ce685051fa59632df` |
| V288 | `app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt:25` | `@Before fun setUp() { mockkStatic(Log::class)` | `de959a84bedf308b5a4f056c95261aec1e2765261baac73a5dd4e599367850a6` |
| V289 | `app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt:27` | `mockkStatic(Log::class) every { Log.isLoggable(any(), any()) } returns true every { Log.d(any(), any()) } returns 0` | `de959a84bedf308b5a4f056c95261aec1e2765261baac73a5dd4e599367850a6` |
| V290 | `app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt:259` | `mockkObject(FileUtils) every { FileUtils.getFileNameFromUrl("/path/to/image.jpg") } returns "image.jpg" ` | `de959a84bedf308b5a4f056c95261aec1e2765261baac73a5dd4e599367850a6` |
| V291 | `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt:32` | `suspend fun getSyncedUsers(): List<UserEntity> suspend fun getUsersForHealthSync(): List<UserEntity> suspend fun getSyncedUserByName(name: String): UserEntity?` | `295ea60320bad481e14fbffebeea880d6f3aa7e331959142aaa07c685e115e19` |
| V292 | `app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt:7` | `interface UserSyncRepository { suspend fun changeUserSecurity(model: UserEntity, obj: JsonObject) suspend fun saveKeyIv(model: UserEntity, obj: JsonObject)` | `7d49e6aa4674d53527544c2be159b4bc522660ec2db1e4fe08c724299c5c2561` |
| V293 | `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt:35` | `val userModels = userRepository.getPendingSyncUsers(100)  if (userModels.isNotEmpty()) {` | `2a2d244919174bcba8dd81cb70b7ce48e818dbdbeb90aa743986321ba9b84bf3` |
| V294 | `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt:58` | `val userModel = if (userName != null) userRepository.getUserByName(userName) else null  if (userModel != null) {` | `2a2d244919174bcba8dd81cb70b7ce48e818dbdbeb90aa743986321ba9b84bf3` |
| V295 | `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt:102` | `val unmanagedUsers = userRepository.getSyncedUsers()  if (unmanagedUsers.isEmpty()) {` | `2a2d244919174bcba8dd81cb70b7ce48e818dbdbeb90aa743986321ba9b84bf3` |
| V296 | `.github/scripts/playstore-quota.sh:33` | `save_epochs() { local raw if [ -n "$RELEASES_JSON" ]; then` | `dd0cf8edd3953febbd5c1bf1607b00a7eb09f38c0d36bed58c01d830330490db` |
| V297 | `.github/scripts/playstore-quota.sh:38` | `raw=$(gh api "repos/$REPO/releases?per_page=100" 2>/dev/null) \|\| raw='[]' fi jq -r '` | `dd0cf8edd3953febbd5c1bf1607b00a7eb09f38c0d36bed58c01d830330490db` |
| V298 | `.github/scripts/playstore-quota.sh:40` | `jq -r ' [ .[]? \| select((.draft // false) \| not) \| .published_at \| fromdateiso8601 ] \| sort \| reverse \| .[]` | `dd0cf8edd3953febbd5c1bf1607b00a7eb09f38c0d36bed58c01d830330490db` |
| V299 | `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt:61` | `fun toggleItemChecked(resourceId: String) { _uiState.update { state -> var checkedCount = 0` | `62f1da90543da51ebbd561530a7d5ebe6e76dd60d5c22d7dd6d22dc8dcc8f1d6` |
| V300 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt:38` | `@Query("SELECT * FROM exams WHERE stepId = :stepId AND type = :type") suspend fun getByStepIdAndType(stepId: String, type: String): List<StepExam> @Query("SELECT * FROM exams WHERE sourceSurveyId IS NOT NULL AND _rev IS NULL") suspend fun getPendingAdoptedSurveys(): List<StepExam> @Query("SELECT * FROM exams") suspend fun getAll(): List<StepExam>` | `eb8cf5270447cf16626e445460b875191167a14bc771d4f156810ad9b411194c` |
| V301 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:401` | `?: examDao.getByStepIdAndType(stepId, "surveys").firstOrNull() ?: return true return exam.id.let {` | `e93db9c5723c0c9a96403dbcd5060ba7bf5a93853513e3a98d39dda3e45172c1` |
| V302 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:80` | `return submissionDao.observeByUserId(userId).distinctUntilChanged { old, new -> old.size == new.size && old.zip(new).all { (o, n) -> o.id == n.id && o.lastUpdateTime == n.lastUpdateTime } }` | `e93db9c5723c0c9a96403dbcd5060ba7bf5a93853513e3a98d39dda3e45172c1` |
| V303 | `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt:57` | `private suspend fun getLinkIdsForTagNames(dbType: String, tagNames: List<String>): List<String> { val matchingTags = tagDao.getByNames(tagNames) if (matchingTags.isEmpty()) {` | `23478be800e1ff34afdc17cf070c9f54b2aa18baa60102899947787d5c4cc286` |
| V304 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TagDao.kt:43` | `@Query("SELECT * FROM tag WHERE name IN (:names)") suspend fun getByNames(names: List<String>): List<TagEntity> ` | `a8ba90a86cb3492084b2f5e7c596706de45fd5ec7a3f0e75629b9ff58b65f2ff` |
| V305 | `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt:76` | `val allTagIds = links.mapNotNullTo(LinkedHashSet()) { it.tagId }.toList() if (allTagIds.isEmpty()) { return emptyMap()` | `23478be800e1ff34afdc17cf070c9f54b2aa18baa60102899947787d5c4cc286` |
| V306 | `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt:101` | `private suspend fun getLinkedTags(db: String, linkId: String): List<TagEntity> { val links = tagDao.getByDbAndLinkId(db, linkId) if (links.isEmpty()) {` | `23478be800e1ff34afdc17cf070c9f54b2aa18baa60102899947787d5c4cc286` |
| V307 | `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt:130` | `private fun createUnmanagedTag(act: JsonObject): TagEntity { val tag = TagEntity() tag.id = GsonUtils.getString("_id", act)` | `23478be800e1ff34afdc17cf070c9f54b2aa18baa60102899947787d5c4cc286` |
| V308 | `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt:135` | `e.printStackTrace() } return false` | `5ba4cf68a35a7c23f89958e97a18e39880193c97762352bb97bcafb012c5afbf` |
| V309 | `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt:187` | `e.printStackTrace() } }` | `5ba4cf68a35a7c23f89958e97a18e39880193c97762352bb97bcafb012c5afbf` |
| V310 | `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt:318` | `e.printStackTrace() val failDuration = SystemClock.elapsedRealtime() - syncStartTime Log.d("SyncPerf", "  ✗ Failed $table sync after ${failDuration}ms: ${e.message}")` | `5ba4cf68a35a7c23f89958e97a18e39880193c97762352bb97bcafb012c5afbf` |
| V311 | `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt:465` | `e.printStackTrace() null }` | `5ba4cf68a35a7c23f89958e97a18e39880193c97762352bb97bcafb012c5afbf` |
| V312 | `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt:63` | `mockkObject(FileUtils) every { FileUtils.getOlePath(any()) } returns oleDir.path ` | `a38467dd1fcb8e96f1139ff6b11fbda44ab561d7c7208a0ec005aadcc8d0ea8d` |
| V313 | `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt:69` | `mockkStatic(Log::class) every { Log.e(any(), any(), any()) } returns 0 }` | `a38467dd1fcb8e96f1139ff6b11fbda44ab561d7c7208a0ec005aadcc8d0ea8d` |
| V314 | `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt:65` | `@BeforeClass @JvmStatic fun setUpClass() {` | `7c8c1542d8e64133b9d3bf6e0dc7dc1607a65fd341094fbf7618ad3ab603b266` |
| V315 | `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt:68` | `mockkStatic(Log::class) mockkStatic(WorkManagerImpl::class) mockkStatic(WorkManager::class)` | `7c8c1542d8e64133b9d3bf6e0dc7dc1607a65fd341094fbf7618ad3ab603b266` |
| V316 | `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt:62` | `@BeforeClass fun setUpClass() { mockkStatic(ContextCompat::class)` | `53929156379e2ce0dc11868895b141fbbe3dd1c1889395435898f60b2a78472c` |
| V317 | `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt:66` | `mockkStatic(Log::class) } ` | `53929156379e2ce0dc11868895b141fbbe3dd1c1889395435898f60b2a78472c` |
| V318 | `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceOnDownloadCompleteTest.kt:44` | `@Before fun setUp() { originalSdkInt = Build.VERSION.SDK_INT` | `46fb228ca5c911913129590b8bb86f959260b10f0938b394cb548e2fa5037616` |
| V319 | `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceOnDownloadCompleteTest.kt:50` | `mockkStatic(android.util.Log::class) every { android.util.Log.d(any(), any()) } returns 0 every { android.util.Log.e(any(), any()) } returns 0` | `46fb228ca5c911913129590b8bb86f959260b10f0938b394cb548e2fa5037616` |
| V320 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:98` | `if (criteria.subjects.isNotEmpty()) { val libSubjects = library.subject if (libSubjects == null \|\| !criteria.subjects.all { critSub ->` | `db44b327d340dffa98fe19fcec188722b2e9e321932ee86f3c41215b7cd1842c` |
| V321 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:107` | `if (criteria.levels.isNotEmpty()) { val libLevels = library.level if (libLevels == null \|\| !criteria.levels.all { critLvl ->` | `db44b327d340dffa98fe19fcec188722b2e9e321932ee86f3c41215b7cd1842c` |
| V322 | `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:116` | `if (criteria.languages.isNotEmpty()) { if (!criteria.languages.any { it.equals(library.language, ignoreCase = true) }) { return false` | `db44b327d340dffa98fe19fcec188722b2e9e321932ee86f3c41215b7cd1842c` |
| V323 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt:15` | `"SELECT COUNT(*) FROM my_personal WHERE title = :title COLLATE NOCASE " + "AND (:userId IS NULL OR :userId = '' OR userId = :userId)" )` | `7676005aa28e2a8a82e678b1cf2242f678b7fc8a82ff3a47a27c69da867601f3` |
| V324 | `app/src/main/java/org/ole/planet/myplanet/model/Personal.kt:7` | `@Entity(tableName = "my_personal", indices = [Index("userId")]) open class Personal { @PrimaryKey` | `02a8a61a25a8cfbd844406d4ccb74909b529cdf6c430db8ad9a07dc8a4d77274` |
| V325 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt:32` | `@Query("DELETE FROM my_personal WHERE _id = :id OR id = :id") suspend fun deleteByIdOrDocId(id: String) ` | `7676005aa28e2a8a82e678b1cf2242f678b7fc8a82ff3a47a27c69da867601f3` |
| V326 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt:41` | `@Query("UPDATE my_personal SET title = COALESCE(:title, title), description = COALESCE(:description, description) WHERE _id = :id OR id = :id") suspend fun updateFields(id: String, title: String?, description: String?) }` | `7676005aa28e2a8a82e678b1cf2242f678b7fc8a82ff3a47a27c69da867601f3` |
| V327 | `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt:72` | `private val leadersList: List<UserEntity> ) : ListAdapter<News, RecyclerView.ViewHolder>( DiffUtils.itemCallback<News>(` | `5ab91519c3084cbd99d58479c43a01954df225f35bf86f04d20187734ed52702` |
| V328 | `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt:590` | `private fun getCurrentLeader(userModel: UserEntity?, news: News): UserEntity? { if (userModel == null) { for (leader in leadersList) {` | `5ab91519c3084cbd99d58479c43a01954df225f35bf86f04d20187734ed52702` |
| V329 | `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt:89` | `return inTitleStartQuery + inTitleContainsQuery + startsWithQuery + containsQuery } ` | `f66ea3f684eed82edc63b3bf968ff5f110c6f060726c0eff5fd310e0ccb1e7c4` |
| V330 | `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt:110` | `return startsWithQuery + containsQuery } }` | `f66ea3f684eed82edc63b3bf968ff5f110c6f060726c0eff5fd310e0ccb1e7c4` |
| V331 | `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt:25` | `private val userSessionManager: UserSessionManager ) : ViewModel() { ` | `590b1a6b74d984c38510dea2856458a850f93bfdfc6715c5aa4bfde5842c20d7` |
| V332 | `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt:41` | `suspend fun isGuest(): Boolean = userSessionManager.getUserModel()?.id?.startsWith("guest") == true  fun isCurrentlyProcessing(): Boolean {` | `590b1a6b74d984c38510dea2856458a850f93bfdfc6715c5aa4bfde5842c20d7` |
| V333 | `app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt:28` | `suspend fun getUserModel(): UserEntity? { return userRepository.getUserModel() }` | `7f7ba718dd30074688c877ee15fce041733e4a8f745ec2a24be25b766ddea00e` |
| V334 | `app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt:3` | `import androidx.recyclerview.widget.RecyclerView import java.util.WeakHashMap ` | `52ab1f730c7c4fc672644712cc80e5147bc7daecc818e31ea0062d400a78d8c9` |
| V335 | `app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt:11` | `if (key.isNullOrEmpty()) return RecyclerView.NO_ID var hash = -3750763034362895579L // FNV offset basis for (i in key.indices) {` | `52ab1f730c7c4fc672644712cc80e5147bc7daecc818e31ea0062d400a78d8c9` |
| V336 | `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt:91` | `updateAddLabelVisibility(binding, voice, canManageLabels) } ` | `098fa7d949319b084a348c2ff6068b53a3015f39df29454364515846c90d06a0` |
| V337 | `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt:108` | `private fun updateAddLabelVisibility( binding: RowNewsBinding, voice: News?,` | `098fa7d949319b084a348c2ff6068b53a3015f39df29454364515846c90d06a0` |
| V338 | `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt:21` | `@AndroidEntryPoint class NotificationActionReceiver : BroadcastReceiver() { companion object {` | `daf4ee4cf79b75950c0ae4c790cd3e3b6d846dd4012b5f1982055ea58d478d5c` |
| V339 | `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt:110` | `val broadcastService = getBroadcastService(context) broadcastService.sendBroadcast(localBroadcastIntent) } catch (e: Exception) {` | `daf4ee4cf79b75950c0ae4c790cd3e3b6d846dd4012b5f1982055ea58d478d5c` |
| V340 | `app/src/main/java/org/ole/planet/myplanet/services/BroadcastService.kt:10` | `open class BroadcastService @Inject constructor() { private val _events = MutableSharedFlow<Intent>(extraBufferCapacity = 64) val events = _events.asSharedFlow()` | `0fc7da2523f0270614dae1d4c58fef15e67f4a72d77ff7dc402fe709f4b7b04a` |
| V341 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt:677` | `val userModel = profileDbHandler.getUserModel() val userProfile = userModel?.userImage ?: "" val userName = userModel?.name` | `0b36414ea7389f2c5fc7e52e0b3b8c43c6da870cbe72ed311cfbdc6dc7c5918b` |
| V342 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt:680` | `loginViewModel.saveUsers(name, encryptedPassword, source, userProfile, userName) } }` | `0b36414ea7389f2c5fc7e52e0b3b8c43c6da870cbe72ed311cfbdc6dc7c5918b` |
| V343 | `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt:69` | `fun saveUsers(name: String?, encryptedPassword: String?, source: String, userProfile: String?, userName: String?) { viewModelScope.launch(dispatcherProvider.io) { userRepository.upsertSavedUser(name, encryptedPassword, source, userProfile, userName)` | `843699c8d6824b57ab3dca9aa43a800662c6108d49f16819b78d6544553c8932` |
| V344 | `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt:119` | `fun formatCsvForSpeech(rows: List<Array<String>>): String { if (rows.isEmpty()) return "" val header = rows.first()` | `9132f54b48eb685208709a164d8cd0c1cd62eb4f6d51d9dcd3405a8568cce79c` |
| V345 | `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt:76` | `suspend fun getAllStepResources(stepId: String?): List<MyLibrary> fun getRecentResources(userId: String): Flow<List<MyLibrary>> fun getPendingDownloads(userId: String): Flow<List<String>>` | `7470aa1471bafb4e6541d5fb4fd9507d25093c9085e97b227761e7f2c6a5404b` |
| V346 | `app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt:144` | `fun isResourceOffline(): Boolean { return resourceOffline && _rev == downloadedRev }` | `7b44e0f4ba17769ba243a24a78bcff82a57b3cb905a13cea93882cc9c4ed9b47` |
| V347 | `app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt:64` | `t.printStackTrace() params.listener.onSuccess("Unable to upload resource") }` | `f119b77d431da37687d63a477f6bb90b68ce7939a4a912600c3bfb33b52615ef` |
| V348 | `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabFragment.kt:23` | `binding.viewPager2.adapter = CommunityPagerAdapter(this@CommunityTabFragment, "${state.planetCode}@${state.parentCode}", false, state.planetType) TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position -> tab.text = (binding.viewPager2.adapter as CommunityPagerAdapter).getPageTitle(position)` | `a393e72ebec19487fc92a3bc4fe298b32ea2d30b9d9c2ceb0066db6aad97f1d1` |
| V349 | `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabFragment.kt:27` | `binding.title.text = if (state.planetCode.isEmpty()) state.communityName else state.planetCode binding.subtitle.text = state.planetType binding.llActionButtons.visibility = View.GONE` | `a393e72ebec19487fc92a3bc4fe298b32ea2d30b9d9c2ceb0066db6aad97f1d1` |
| V350 | `app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt:87` | `binding.viewPager2.adapter = CommunityPagerAdapter(this@HomeCommunityDialogFragment, "${state.communityName}@${state.parentCode}", true, state.planetType) tabLayoutMediator = TabLayoutMediator(binding.tabLayout, binding.viewPager2) { tab, position -> tab.text = (binding.viewPager2.adapter as CommunityPagerAdapter).getPageTitle(position)` | `c75fbd3700dd3bf7f3e73eccda1c46a6900d7683c7ce5267e78ec5d608e6476e` |
| V351 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:307` | `private fun getExamId(parentId: String?): String? { return if (parentId?.contains("@") == true) { parentId.split("@")[0]` | `82e9ec89daf1fa55328f1af09112a7b9739f8f44503fbf83af498aed01310ac5` |
| V352 | `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt:19` | `val createdBy = getString("createdBy", encrypted)  val (resolvedName, isSelfExamination) = if (!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)) {` | `269ad304f13fc067e169b04f4a3c66ecd1aa13d887a66ccb4607e50a728425b7` |
| V353 | `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt:21` | `val (resolvedName, isSelfExamination) = if (!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)) { val name = displayNameCache.getOrPut(createdBy) { val model = userMap[createdBy]` | `269ad304f13fc067e169b04f4a3c66ecd1aa13d887a66ccb4607e50a728425b7` |
| V354 | `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt:7` | `fun <T> searchList(list: List<T>, query: String, normalizedTitleSelector: (T) -> String?): List<T> { if (query.isEmpty()) return list ` | `1fd9c6ee8d47b605dfda225209c150be964ac9e662c2d28cd6f2aea2c1fc5883` |
| V355 | `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt:13` | `val startsWithQuery = mutableListOf<T>() val containsQuery = mutableListOf<T>() ` | `1fd9c6ee8d47b605dfda225209c150be964ac9e662c2d28cd6f2aea2c1fc5883` |
| V356 | `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt:21` | `return startsWithQuery + containsQuery } ` | `1fd9c6ee8d47b605dfda225209c150be964ac9e662c2d28cd6f2aea2c1fc5883` |
| V357 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFilterFragment.kt:103` | `private fun selectedValue(spinner: Spinner, arrayRes: Int): String { val position = spinner.selectedItemPosition return if (position <= 0) "" else englishValues(requireContext(), arrayRes).getOrElse(position) { "" }` | `a433264c6416cb76ff803892c778a8995148e82bd9078bc1ba503ec27c10f197` |
| V358 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFilterFragment.kt:116` | `fun englishValues(context: Context, arrayRes: Int): Array<String> { val english = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) } return context.createConfigurationContext(english).resources.getStringArray(arrayRes)` | `a433264c6416cb76ff803892c778a8995148e82bd9078bc1ba503ec27c10f197` |
| V359 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:130` | `} catch (e: Exception) { null }` | `82e9ec89daf1fa55328f1af09112a7b9739f8f44503fbf83af498aed01310ac5` |
| V360 | `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:242` | `} catch (e: Exception) { null }` | `82e9ec89daf1fa55328f1af09112a7b9739f8f44503fbf83af498aed01310ac5` |
| V361 | `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt:255` | `private suspend fun getTaskTeamNamesByTaskIds(taskIds: List<String>): Map<String, String> { if (taskIds.isEmpty()) return emptyMap() val map = mutableMapOf<String, String>()` | `f295400671c541c91f6d535fb93dd01a99dd40539d2e746aca48612cd218fe2e` |
| V362 | `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt:318` | `private suspend fun getTaskTeamNamesByTaskTitles(taskTitles: List<String>): Map<String, String> { if (taskTitles.isEmpty()) return emptyMap() val map = mutableMapOf<String, String>()` | `f295400671c541c91f6d535fb93dd01a99dd40539d2e746aca48612cd218fe2e` |
| V363 | `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt:103` | `err.printStackTrace() null }` | `0f4835089266ced80182ace69aa15b226a3c7741002f57783ee67828475026f0` |
| V364 | `app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt:69` | `e.printStackTrace() "" }` | `27c1bf5c86280dd7f68757bf91a3bd9df78c9d95d97e96e2db1905ca179c1433` |
| V365 | `app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt:14` | `val digest = MessageDigest.getInstance("SHA-512") FileInputStream(file).use { fis -> val buffer = ByteArray(DEFAULT_BUFFER_SIZE)` | `267c1d0856c636049c862da2937d8091cb88d3f9f7dba3bdbb56ff1601ae4830` |
| V366 | `app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt:16` | `val buffer = ByteArray(DEFAULT_BUFFER_SIZE) var read: Int while (fis.read(buffer).also { read = it } != -1) {` | `267c1d0856c636049c862da2937d8091cb88d3f9f7dba3bdbb56ff1601ae4830` |
| V367 | `app/src/main/java/org/ole/planet/myplanet/utils/DeviceNameProvider.kt:24` | `@Module @InstallIn(SingletonComponent::class) abstract class DeviceNameModule {` | `f7a2089411fe10ccf3c16007c28345cc6a55122f6b5a30ce175865363acd4fe3` |
| V368 | `app/src/main/java/org/ole/planet/myplanet/services/UserDataUploadScheduler.kt:67` | `@Module @InstallIn(SingletonComponent::class) abstract class UserDataUploadSchedulerModule {` | `bfb52c7907642fd47af65c6d6f6c9ad3dcab07a18030fcc151932e19f3ac4245` |
| V369 | `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt:44` | `fun getDouble(fieldName: String, jsonObject: JsonObject?): Double { val primitive = field(fieldName, jsonObject) as? JsonPrimitive ?: return 0.0 return primitive.doubleOrNull ?: primitive.content.toDoubleOrNull() ?: 0.0` | `2e375518fb1f878d5db61d3f63c7198cc5b05ec504f45620d355be2da0fcd00b` |
| V370 | `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt:49` | `fun getFloat(fieldName: String, jsonObject: JsonObject?): Float { val primitive = field(fieldName, jsonObject) as? JsonPrimitive ?: return 0f return primitive.floatOrNull ?: primitive.content.toFloatOrNull() ?: 0f` | `2e375518fb1f878d5db61d3f63c7198cc5b05ec504f45620d355be2da0fcd00b` |
| V371 | `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt:3` | `import android.util.Log import com.google.gson.JsonObject import java.io.File` | `2e41ff4cc64f454bab077b3b238550da392fdb0f8f8257f63cddfa3d347f93f8` |
| V372 | `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt:141` | `Log.w(TAG, "Attachment upload failed for ${personal.id}", e) return PersonalUploadResult.AttachmentFailed("Uploaded document but failed to upload attachment: ${e.message}", e) }` | `2e41ff4cc64f454bab077b3b238550da392fdb0f8f8257f63cddfa3d347f93f8` |
| V373 | `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt:155` | `Log.w(TAG, "Unable to upload personal resource ${personal.id}", e) return PersonalUploadResult.DocumentFailed("Unable to upload resource: ${e.message}", e) }` | `2e41ff4cc64f454bab077b3b238550da392fdb0f8f8257f63cddfa3d347f93f8` |
| V374 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt:26` | `@Query("SELECT MIN(title) AS title, COUNT(*) AS openCount FROM resource_activity WHERE user = :userName AND type = :type AND title IS NOT NULL AND TRIM(title) != '' GROUP BY resourceId ORDER BY openCount DESC, title ASC LIMIT 1") suspend fun getMostOpenedResource(userName: String, type: String): ResourceOpenCount? ` | `df6a62efb38f12c64ab767415559f8c4dff8fea16ef2f2322a1131d113ec76fa` |
| V375 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt:29` | `@Query("SELECT * FROM resource_activity WHERE user = :userName AND type = :type AND resourceId IS NOT NULL GROUP BY resourceId") fun observeByUserAndType(userName: String, type: String): Flow<List<ResourceActivity>> ` | `df6a62efb38f12c64ab767415559f8c4dff8fea16ef2f2322a1131d113ec76fa` |
| V376 | `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt:3` | `import android.util.Log import java.io.IOException import java.util.concurrent.atomic.AtomicBoolean` | `b9aa0f75e4aede6f4dcdbec2168d2dab980845cc38870050d85d46ab8c7a3e7e` |
| V377 | `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt:131` | `Log.w(TAG, "Network error during retry for ${operation.id}", e) RetryOperationResult.RetryableFailure(e.message, null) } catch (e: Exception) {` | `b9aa0f75e4aede6f4dcdbec2168d2dab980845cc38870050d85d46ab8c7a3e7e` |
| V378 | `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt:135` | `Log.e(TAG, "Unexpected error during retry for ${operation.id}", e) RetryOperationResult.RetryableFailure(e.message, null) }` | `b9aa0f75e4aede6f4dcdbec2168d2dab980845cc38870050d85d46ab8c7a3e7e` |
| V379 | `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt:124` | `private fun getLabel(s: String): String { return Constants.LABEL_VALUE_TO_NAME[s] ?: formatLabelValue(s) }` | `098fa7d949319b084a348c2ff6068b53a3015f39df29454364515846c90d06a0` |
| V380 | `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt:138` | `val locale = Locale.getDefault() return cleaned .trim()` | `098fa7d949319b084a348c2ff6068b53a3015f39df29454364515846c90d06a0` |
| V381 | `app/src/main/java/org/ole/planet/myplanet/model/News.kt:3` | `import android.util.Log import androidx.room.Entity import androidx.room.Ignore` | `3cdc70e3185b09948348f0bde2b1bade66948a7467bf15b9e6734fae6fe4b750` |
| V382 | `app/src/main/java/org/ole/planet/myplanet/model/News.kt:220` | `Log.w(TAG, "community section check failed", e) } return false` | `3cdc70e3185b09948348f0bde2b1bade66948a7467bf15b9e6734fae6fe4b750` |
| V383 | `app/src/main/java/org/ole/planet/myplanet/model/News.kt:237` | `Log.w(TAG, "calculateSortDate failed", e) } return time` | `3cdc70e3185b09948348f0bde2b1bade66948a7467bf15b9e6734fae6fe4b750` |
| V384 | `app/src/main/java/org/ole/planet/myplanet/model/News.kt:311` | `Log.w(TAG, "conversation parse failed", e) } }` | `3cdc70e3185b09948348f0bde2b1bade66948a7467bf15b9e6734fae6fe4b750` |
| V385 | `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt:48` | `statusText = context.getString(R.string.status) priorityText = context.getString(R.string.priority) openDateText = context.getString(R.string.open_date)` | `66f903545064886f5da6774fb6cecb50226ea9d8f83bc665ae0ba2a18b2d6a32` |
| V386 | `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt:61` | `val type = localizedLabel(context, feedback.type) val priority = localizedLabel(context, feedback.priority) binding.tvTitle.text = feedback.title` | `66f903545064886f5da6774fb6cecb50226ea9d8f83bc665ae0ba2a18b2d6a32` |
| V387 | `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt:84` | `private fun localizedLabel(context: Context, value: String?): String? { val resId = LABELS[value?.lowercase(Locale.ROOT)] ?: return value return context.getString(resId)` | `66f903545064886f5da6774fb6cecb50226ea9d8f83bc665ae0ba2a18b2d6a32` |
| V388 | `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt:579` | `val otherChoice = GsonUtils.gson.fromJson("""{"text":"Other","id":"other"}""", JsonObject::class.java)  addCompoundButton(otherChoice, false, oldAnswer)` | `64484397124e38ad0b57cec4405d5c47fedd3c19a2a034d42222f904938ce929` |
| V389 | `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt:599` | `val otherChoice = GsonUtils.gson.fromJson("""{"text":"Other","id":"other"}""", JsonObject::class.java)  addCompoundButton(otherChoice, isRadio, oldAnswer)` | `64484397124e38ad0b57cec4405d5c47fedd3c19a2a034d42222f904938ce929` |
| V390 | `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt:609` | `val inflater = LayoutInflater.from(activity) val rdBtn = inflater.inflate(R.layout.item_radio_btn, binding.groupChoices, false) as RadioButton rdBtn.text = choice` | `64484397124e38ad0b57cec4405d5c47fedd3c19a2a034d42222f904938ce929` |
| V391 | `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt:24` | `fun init(sharedPrefManager: SharedPrefManager) { synchronized(this) { generation++` | `6a08956191e554511b64c33e1198c43a0a1b260d4830ccf575c610ea9175d3bf` |
| V392 | `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt:38` | `fun invalidateCaches() { synchronized(this) { generation++` | `6a08956191e554511b64c33e1198c43a0a1b260d4830ccf575c610ea9175d3bf` |
| V393 | `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt:83` | `val hostUrl: String get() { val spm = spm()` | `6a08956191e554511b64c33e1198c43a0a1b260d4830ccf575c610ea9175d3bf` |
| V394 | `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt:78` | `binding.date.text = getFormattedDate(submission.startTime) showSubmittedBy(submission) binding.title.text = submission.examTitle` | `67c9893e54da625fbc2852cdce33a61fe59961eba8b8aaf9707e1dd8d287376b` |
| V395 | `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsListAdapter.kt:49` | `binding.tvSubmissionDate.text = TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime) binding.tvSubmissionStatus.text = submission.status ` | `33bff29a70450ba773d1092c73503465e9f522c024d04e1d21f15efacf7ef00d` |
| V396 | `app/src/main/java/org/ole/planet/myplanet/model/Meetup.kt:107` | `e.printStackTrace() } map["Meetup Time"] = checkNull(meetups.startTime) + " - " + checkNull(meetups.endTime)` | `c9ccd70fddc6e3ce9cd367c8b820785c7b0be59da1c6a0a3a57d6dc54b3e08d9` |
| V397 | `app/src/main/java/org/ole/planet/myplanet/model/Meetup.kt:118` | `e.printStackTrace() } map["Recurring Days"] = checkNull(recurringDays.toString())` | `c9ccd70fddc6e3ce9cd367c8b820785c7b0be59da1c6a0a3a57d6dc54b3e08d9` |
| V398 | `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt:73` | `private fun formatInstant(instant: Instant, fallback: String, formatter: () -> DateTimeFormatter): String = try { formatter().format(instant)` | `bec4d9484e5e5be116885b35757313e7a6556403ab3a9b5e90370bbf37bb018d` |
| V399 | `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt:88` | `fun getFormattedDate(date: Long?): String { val instant = date?.let { Instant.ofEpochMilli(it) } ?: Instant.now() return formatInstant(instant, "N/A") { defaultDateFormatter() }` | `bec4d9484e5e5be116885b35757313e7a6556403ab3a9b5e90370bbf37bb018d` |
| V400 | `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt:147` | `val names = courses.map { it.courseTitle ?: getString(R.string.untitled_course) } val adapter = CheckboxAdapter { alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = (lv.adapter as CheckboxAdapter).selectedItemsList.isNotEmpty()` | `b9341866ca64a021bc3171f83801188e81f162a16a6c76d30684dba57656580e` |
| V401 | `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt:150` | `binding.txtOtherNeed.text = getString(R.string.empty_text) binding.txtSpecialNeeds.text = getString(R.string.empty_text) binding.txtBirthPlace.text = getString(R.string.empty_text)` | `59044f910be650a94dbb4e1168d61f88044bf9cf0cdc5178bde0a9b7c7f5689a` |
| V402 | `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt:215` | `binding.txtOtherNeed.text = getString(R.string.empty_text) binding.txtSpecialNeeds.text = getString(R.string.empty_text) binding.txtBirthPlace.text = getString(R.string.empty_text)` | `59044f910be650a94dbb4e1168d61f88044bf9cf0cdc5178bde0a9b7c7f5689a` |
| V403 | `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt:3` | `import android.util.Log import com.google.gson.JsonArray import javax.inject.Inject` | `1e8c6845589d25abc89ad5b615526db9d34a8d699afa9924898602deef51b660` |
| V404 | `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt:57` | `Log.w(TAG, "syncCommunityDocs failed", e) false }` | `1e8c6845589d25abc89ad5b615526db9d34a8d699afa9924898602deef51b660` |
| V405 | `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt:3` | `import android.util.Log import com.google.gson.Gson import com.google.gson.JsonArray` | `1d1c2bfc68f351b7def5238d3cbffaf95096b920c8612d02ccabc21d0beab711` |
| V406 | `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt:108` | `Log.e("HealthRepository", "Failed to parse examination conditions", e) } }` | `1d1c2bfc68f351b7def5238d3cbffaf95096b920c8612d02ccabc21d0beab711` |
| V407 | `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt:138` | `Log.e("HealthRepository", "Failed to upload health data", e) } null` | `1d1c2bfc68f351b7def5238d3cbffaf95096b920c8612d02ccabc21d0beab711` |
| V408 | `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt:238` | `Log.e("HealthRepository", "Failed to encrypt or save user health profile", e) } healthExaminationDao.upsert(healthPojo)` | `1d1c2bfc68f351b7def5238d3cbffaf95096b920c8612d02ccabc21d0beab711` |
| V409 | `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:201` | `val community = communityId?.let { id -> teamsRepository.getTeamSummaryById(id) ?: TeamSummary( _id = id,` | `eb33d65f2358e81086aeaffb3be88052381470ad598eca34a1e0174d57a5a89d` |
| V410 | `app/src/main/java/org/ole/planet/myplanet/model/TeamSummary.kt:3` | `data class TeamSummary( val _id: String, val name: String,` | `167613561e65871fc2508bac5d9d818270bc924efc1eafc3ce63e45d66170f66` |
| V411 | `app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt:26` | `private const val UPLOAD_DELAY_SECONDS = 30L  fun start(context: Context) {` | `66506d8fa6ba10fb78a03f88c678db2fa5787d68c1aa36a527632e963bcc84d5` |
| V412 | `app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt:61` | `.setInitialDelay(UPLOAD_DELAY_SECONDS, TimeUnit.SECONDS) .build() ` | `66506d8fa6ba10fb78a03f88c678db2fa5787d68c1aa36a527632e963bcc84d5` |
| V413 | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt:32` | `@Singleton class UploadConfigs @Inject constructor( @ApplicationContext private val context: Context,` | `e03233489ec0b3f48498a862438193db4eb954dec7570e111ef5d590f847a0cc` |
| V414 | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt:52` | `val NewsActivities = RoomUploadConfig( endpoint = "myplanet_activities", modelClassName = "NewsLog",` | `e03233489ec0b3f48498a862438193db4eb954dec7570e111ef5d590f847a0cc` |
| V415 | `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfig.kt:48` | `sealed class UploadSerializer<T : Any> { data class Simple<T : Any>( val serialize: (T) -> JsonObject` | `4bbb2e169de87d25fedf5fc211fbc136b8cc7291d355e88b3ed2c45de5ace8cf` |
| V416 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt:164` | `holder.setPreviewJob(adapterScope.launch { val isDownloaded = resource.isResourceOffline() \|\| withContext(dispatcherProvider.io) { FileUtils.checkFileExist(context, UrlUtils.getUrl(resource))` | `7160e764ba87c8de32f104eb9979689f31b557f8d1003489baf5f18dfd4a2542` |
| V417 | `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt:187` | `val exists = withContext(dispatcherProvider.io) { resourceFile.exists() }  when {` | `7160e764ba87c8de32f104eb9979689f31b557f8d1003489baf5f18dfd4a2542` |
| V418 | `app/src/main/java/org/ole/planet/myplanet/model/TeamNotification.kt:12` | `@Entity(tableName = "team_notification", indices = [Index("type")]) open class TeamNotification { @PrimaryKey` | `c29159c80a4c2279d83dfdac89df6f3dab800dfb2df260bf2711c83a065ec292` |
| V419 | `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt:342` | `override suspend fun updateTeamNotification(teamId: String, count: Int) { if (teamNotificationDao.updateCount(teamId, "chat", count) == 0) { teamNotificationDao.insert(TeamNotification().apply {` | `f295400671c541c91f6d535fb93dd01a99dd40539d2e746aca48612cd218fe2e` |
| V420 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamNotificationDao.kt:11` | `@Query("UPDATE team_notification SET lastCount = :count WHERE parentId = :parentId AND type = :type") suspend fun updateCount(parentId: String, type: String, count: Int): Int ` | `d9dc6d3e55460808fe057f2f4b41ed410524f7f8942f53c5e5d1de97241b5787` |
| V421 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt:20` | `@Query("SELECT * FROM users") suspend fun getAll(): List<UserEntity> @Query("SELECT * FROM users WHERE name = :name LIMIT 1") suspend fun getByName(name: String): UserEntity? @Query("SELECT * FROM users WHERE name = :name COLLATE NOCASE LIMIT 1") suspend fun getByNameIgnoreCase(name: String): UserEntity?` | `45bd8a6e09ac38f41b3f6995eb6feecf2bc0ec94dc1e017eb0c3d7bdcce439a1` |
| V422 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt:11` | `@Query("SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1") suspend fun getById(id: String): UserEntity? @Query("SELECT * FROM users WHERE id IN (:userIds) OR _id IN (:userIds)")` | `45bd8a6e09ac38f41b3f6995eb6feecf2bc0ec94dc1e017eb0c3d7bdcce439a1` |
| V423 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt:13` | `@Query("SELECT * FROM users WHERE id IN (:userIds) OR _id IN (:userIds)") suspend fun getUsersByAnyIdsInternal(userIds: List<String>): List<UserEntity> ` | `45bd8a6e09ac38f41b3f6995eb6feecf2bc0ec94dc1e017eb0c3d7bdcce439a1` |
| V424 | `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt:26` | `@Entity(tableName = "users", indices = [Index("_id"), Index("name"), Index("planetCode")]) open class UserEntity( @PrimaryKey @JvmField var id: String = "",` | `8cbb968587b7b94ec2d2b67a49df623882d0d6e426d792bc387e4d60075192b2` |
| V425 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt:21` | `@Query("SELECT * FROM teams WHERE _id = :teamId OR teamId = :teamId LIMIT 1") suspend fun getByTeamId(teamId: String): MyTeam? @Query("SELECT * FROM teams WHERE _id = :id LIMIT 1") suspend fun getById(id: String): MyTeam? @Query("SELECT * FROM teams WHERE userId = :userId") suspend fun getByUserId(userId: String): List<MyTeam>` | `087c01c0ab877a3df7312a76cce5c07627ff1c20a3d52237078153d9247cf2b8` |
| V426 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt:35` | `@Query("SELECT COUNT(DISTINCT userId) FROM teams WHERE teamId = :teamId AND docType = :docType AND isDeletePending = 0 AND userId IS NOT NULL AND EXISTS (SELECT 1 FROM users u WHERE u.id = teams.userId OR u._id = teams.userId)") suspend fun countByTeamIdAndDocType(teamId: String, docType: String): Int @Query("SELECT COUNT(DISTINCT userId) FROM teams WHERE teamId = :teamId AND docType = 'membership' AND isDeletePending = 0 AND userId IS NOT NULL") suspend fun countActiveMembershipsByTeamId(teamId: String): Int @Query("SELECT r.* FROM teams r INNER JOIN teams t ON t._id = r.teamId WHERE r.docType = 'request' AND IFNULL(t.teamPlanetCode, '') != '' AND r.teamPlanetCode IS NOT t.teamPlanetCode") suspend fun getMisfiledRequests(): List<MyTeam>` | `087c01c0ab877a3df7312a76cce5c07627ff1c20a3d52237078153d9247cf2b8` |
| V427 | `app/src/main/java/org/ole/planet/myplanet/model/MyTeam.kt:20` | `@Entity(tableName = "teams", indices = [Index("_id"), Index("teamId"), Index("userId"), Index("type"), Index("docType")]) open class MyTeam( @PrimaryKey @JvmField var _id: String = "",` | `ec8668dce132bd7d5c030b757c0892b40e8828b3cde4601ebc40c6e221da1258` |
| V428 | `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ChatDao.kt:15` | `@Query("SELECT _rev FROM chat_history WHERE _id = :docId") suspend fun getRevsByDocId(docId: String): List<String?> ` | `f265704c224149e5d533069f689c2e6223a10f804481ab349542e2d970517e27` |
| V429 | `app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt:43` | `override suspend fun doWork(): Result { return try { scheduleServerReachabilityCheck()` | `66506d8fa6ba10fb78a03f88c678db2fa5787d68c1aa36a527632e963bcc84d5` |
| V430 | `app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt:53` | `private fun scheduleServerReachabilityCheck() { val inputData = Data.Builder() .putBoolean("network_reconnection_trigger", true)` | `66506d8fa6ba10fb78a03f88c678db2fa5787d68c1aa36a527632e963bcc84d5` |

## Counts, conservation checks, and assumptions

**Counts: 18 lists / 180 raw / 131 shipped.** Exactly 10 original work orders per list; 30 submissions per benchmark stream; **172 retained raw + 8 entirely dropped = 180**. Of the retained raw submissions, 37 have explicit correction/removal notes. The merged work orders contain 174 provenance scopes; these scopes are not recounted as raw submissions.

**Point-sum check: Σ agent points = Σ raw-ledger points = 131 = 131 shipped tasks. Every shipped task contributes exactly 1 point.**

**Quality point-sum check: Σ agent quality points = Σ raw-ledger quality points = Σ shipped ratings/100 = 9679/100 = 96.790000.**

Assumptions/judgment: the user yes authorized keeping the six repeating benchmark streams despite signature drift; it did not supply missing model names. “180+” was read as at least 180. The supplied November 6 working tree is authoritative even where November 10 lists differ. Impact/feasibility, work-order boundaries, and equal substantive-scope weights are reviewer estimates, not measured benchmarks. Invalid portions earn no scope credit, while a submission with independently valid remainder stays in the denominator and can earn credit for that remainder. No model was guessed for list 17, no speedup was inferred from an agent timing claim, and no changes were made to the application working tree.

[V001]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt#L110
[V002]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt#L162
[V003]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L37
[V004]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L39
[V005]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L49
[V006]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L75
[V007]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/UserDataWorker.kt#L53
[V008]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L108
[V009]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt#L29
[V010]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt#L27
[V011]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt#L31
[V012]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt#L36
[V013]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt#L43
[V014]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L60
[V015]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt#L39
[V016]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt#L18
[V017]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt#L21
[V018]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L64
[V019]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L110
[V020]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L125
[V021]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt#L17
[V022]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt#L20
[V023]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt#L32
[V024]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L286
[V025]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L299
[V026]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L314
[V027]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L441
[V028]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L445
[V029]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L462
[V030]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt#L84
[V031]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt#L90
[V032]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt#L358
[V033]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt#L360
[V034]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt#L189
[V035]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt#L192
[V036]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt#L31
[V037]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt#L34
[V038]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt#L36
[V039]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt#L119
[V040]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt#L120
[V041]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt#L133
[V042]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L105
[V043]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L109
[V044]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt#L142
[V045]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L130
[V046]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L95
[V047]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L102
[V048]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L129
[V049]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L444
[V050]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L525
[V051]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L568
[V052]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L885
[V053]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L212
[V054]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L574
[V055]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L583
[V056]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L781
[V057]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L811
[V058]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L849
[V059]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt#L13
[V060]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt#L14
[V061]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L99
[V062]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L206
[V063]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L210
[V064]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L34
[V065]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L47
[V066]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L357
[V067]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt#L18
[V068]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt#L25
[V069]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L166
[V070]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L192
[V071]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt#L26
[V072]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L366
[V073]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L58
[V074]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L71
[V075]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L87
[V076]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L201
[V077]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L228
[V078]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L117
[V079]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L124
[V080]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L127
[V081]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L209
[V082]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L412
[V083]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L422
[V084]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt#L34
[V085]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/automerge.sh#L104
[V086]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/automerge.sh#L107
[V087]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/automerge.sh#L502
[V088]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/automerge.sh#L509
[V089]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/automerge.sh#L518
[V090]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L402
[V091]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L665
[V092]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt#L69
[V093]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt#L73
[V094]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt#L53
[V095]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt#L68
[V096]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt#L123
[V097]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt#L125
[V098]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt#L58
[V099]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L573
[V100]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/playstore-quota.sh#L56
[V101]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/playstore-quota.sh#L82
[V102]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/playstore-quota.sh#L114
[V103]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt#L82
[V104]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt#L150
[V105]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt#L657
[V106]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt#L555
[V107]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt#L580
[V108]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt#L64
[V109]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt#L85
[V110]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L61
[V111]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L65
[V112]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt#L56
[V113]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt#L60
[V114]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt#L78
[V115]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt#L65
[V116]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt#L69
[V117]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt#L83
[V118]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L177
[V119]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L185
[V120]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L193
[V121]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L180
[V122]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L188
[V123]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L197
[V124]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt#L217
[V125]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt#L243
[V126]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt#L62
[V127]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt#L74
[V128]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/di/TimeModule.kt#L16
[V129]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt#L62
[V130]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/BroadcastService.kt#L18
[V131]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt#L24
[V132]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt#L40
[V133]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt#L68
[V134]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/coauthors.sh#L23
[V135]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/coauthors.sh#L75
[V136]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/coauthors.sh#L81
[V137]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/coauthors.sh#L93
[V138]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L72
[V139]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L76
[V140]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L82
[V141]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt#L23
[V142]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt#L24
[V143]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt#L28
[V144]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt#L34
[V145]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/automerge.sh#L336
[V146]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/automerge.sh#L339
[V147]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/automerge.sh#L344
[V148]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L193
[V149]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L196
[V150]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt#L55
[V151]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt#L109
[V152]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt#L14
[V153]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt#L20
[V154]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L19
[V155]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt#L177
[V156]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt#L405
[V157]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksViewModel.kt#L24
[V158]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L553
[V159]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/MyCourse.kt#L96
[V160]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt#L79
[V161]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt#L98
[V162]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt#L71
[V163]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt#L73
[V164]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt#L302
[V165]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L130
[V166]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L134
[V167]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L163
[V168]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L33
[V169]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L298
[V170]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt#L320
[V171]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt#L30
[V172]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt#L38
[V173]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt#L35
[V174]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt#L43
[V175]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt#L59
[V176]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt#L107
[V177]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt#L110
[V178]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt#L25
[V179]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt#L33
[V180]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt#L18
[V181]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt#L145
[V182]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/News.kt#L125
[V183]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/News.kt#L133
[V184]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/News.kt#L147
[V185]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/News.kt#L173
[V186]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt#L149
[V187]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt#L215
[V188]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt#L235
[V189]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/workflows/build.yml#L3
[V190]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/workflows/build.yml#L41
[V191]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/workflows/build.yml#L50
[V192]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L339
[V193]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginator.kt#L19
[V194]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginator.kt#L20
[V195]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L42
[V196]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L49
[V197]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L68
[V198]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L30
[V199]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L43
[V200]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt#L24
[V201]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt#L42
[V202]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt#L24
[V203]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt#L33
[V204]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt#L13
[V205]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt#L19
[V206]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt#L22
[V207]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt#L39
[V208]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt#L31
[V209]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt#L35
[V210]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt#L21
[V211]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt#L49
[V212]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L31
[V213]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L39
[V214]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt#L30
[V215]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt#L40
[V216]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt#L49
[V217]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt#L52
[V218]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt#L77
[V219]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L201
[V220]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L209
[V221]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt#L68
[V222]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L597
[V223]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L639
[V224]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L390
[V225]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt#L89
[V226]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/ServerReachabilityWorkerTest.kt#L47
[V227]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/ServerReachabilityWorkerTest.kt#L94
[V228]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/ServerReachabilityWorkerTest.kt#L125
[V229]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt#L67
[V230]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt#L21
[V231]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt#L17
[V232]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt#L35
[V233]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt#L47
[V234]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt#L55
[V235]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L1168
[V236]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ImageViewerUtils.kt#L17
[V237]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L1097
[V238]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L1108
[V239]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L1132
[V240]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt#L38
[V241]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt#L41
[V242]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L127
[V243]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/CommunityDao.kt#L12
[V244]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt#L298
[V245]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt#L84
[V246]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt#L93
[V247]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt#L100
[V248]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt#L20
[V249]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt#L28
[V250]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L80
[V251]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt#L5
[V252]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt#L34
[V253]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt#L40
[V254]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt#L43
[V255]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt#L61
[V256]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt#L117
[V257]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt#L152
[V258]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L983
[V259]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1007
[V260]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1025
[V261]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt#L132
[V262]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt#L138
[V263]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L37
[V264]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L82
[V265]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L90
[V266]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt#L55
[V267]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownViewModel.kt#L17
[V268]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt#L109
[V269]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L135
[V270]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L187
[V271]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt#L8
[V272]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt#L16
[V273]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesAdapterTest.kt#L16
[V274]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapterTest.kt#L11
[V275]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapterTest.kt#L15
[V276]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L346
[V277]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L649
[V278]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L662
[V279]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/test_timing_summary.py#L243
[V280]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/test_timing_summary.py#L249
[V281]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/test_timing_summary.py#L255
[V282]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt#L16
[V283]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt#L28
[V284]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TeamsShareRepository.kt#L5
[V285]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt#L7
[V286]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/HealthExaminationTest.kt#L17
[V287]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/HealthExaminationTest.kt#L19
[V288]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt#L25
[V289]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt#L27
[V290]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt#L259
[V291]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L32
[V292]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt#L7
[V293]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L35
[V294]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L58
[V295]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L102
[V296]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/playstore-quota.sh#L33
[V297]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/playstore-quota.sh#L38
[V298]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/.github/scripts/playstore-quota.sh#L40
[V299]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt#L61
[V300]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt#L38
[V301]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L401
[V302]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L80
[V303]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt#L57
[V304]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TagDao.kt#L43
[V305]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt#L76
[V306]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt#L101
[V307]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt#L130
[V308]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L135
[V309]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L187
[V310]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L318
[V311]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L465
[V312]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt#L63
[V313]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt#L69
[V314]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt#L65
[V315]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt#L68
[V316]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt#L62
[V317]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt#L66
[V318]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceOnDownloadCompleteTest.kt#L44
[V319]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceOnDownloadCompleteTest.kt#L50
[V320]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt#L98
[V321]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt#L107
[V322]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt#L116
[V323]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt#L15
[V324]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/Personal.kt#L7
[V325]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt#L32
[V326]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt#L41
[V327]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L72
[V328]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L590
[V329]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt#L89
[V330]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt#L110
[V331]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt#L25
[V332]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt#L41
[V333]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L28
[V334]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt#L3
[V335]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt#L11
[V336]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt#L91
[V337]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt#L108
[V338]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt#L21
[V339]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt#L110
[V340]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/BroadcastService.kt#L10
[V341]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L677
[V342]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L680
[V343]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt#L69
[V344]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt#L119
[V345]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L76
[V346]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt#L144
[V347]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt#L64
[V348]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabFragment.kt#L23
[V349]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabFragment.kt#L27
[V350]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt#L87
[V351]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt#L307
[V352]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt#L19
[V353]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt#L21
[V354]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt#L7
[V355]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt#L13
[V356]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt#L21
[V357]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFilterFragment.kt#L103
[V358]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFilterFragment.kt#L116
[V359]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt#L130
[V360]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt#L242
[V361]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L255
[V362]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L318
[V363]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt#L103
[V364]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt#L69
[V365]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt#L14
[V366]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt#L16
[V367]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/DeviceNameProvider.kt#L24
[V368]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/UserDataUploadScheduler.kt#L67
[V369]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L44
[V370]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L49
[V371]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt#L3
[V372]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt#L141
[V373]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt#L155
[V374]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt#L26
[V375]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt#L29
[V376]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt#L3
[V377]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt#L131
[V378]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt#L135
[V379]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt#L124
[V380]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt#L138
[V381]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/News.kt#L3
[V382]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/News.kt#L220
[V383]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/News.kt#L237
[V384]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/News.kt#L311
[V385]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt#L48
[V386]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt#L61
[V387]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt#L84
[V388]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L579
[V389]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L599
[V390]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L609
[V391]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt#L24
[V392]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt#L38
[V393]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt#L83
[V394]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt#L78
[V395]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsListAdapter.kt#L49
[V396]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/Meetup.kt#L107
[V397]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/Meetup.kt#L118
[V398]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt#L73
[V399]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt#L88
[V400]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt#L147
[V401]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L150
[V402]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L215
[V403]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt#L3
[V404]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt#L57
[V405]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt#L3
[V406]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt#L108
[V407]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt#L138
[V408]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt#L238
[V409]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L201
[V410]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/TeamSummary.kt#L3
[V411]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt#L26
[V412]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt#L61
[V413]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L32
[V414]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt#L52
[V415]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfig.kt#L48
[V416]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L164
[V417]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L187
[V418]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/TeamNotification.kt#L12
[V419]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L342
[V420]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamNotificationDao.kt#L11
[V421]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt#L20
[V422]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt#L11
[V423]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt#L13
[V424]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt#L26
[V425]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt#L21
[V426]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt#L35
[V427]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/model/MyTeam.kt#L20
[V428]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/data/room/dao/ChatDao.kt#L15
[V429]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt#L43
[V430]: https://github.com/open-learning-exchange/myplanet/blob/3d02e24a44e36d8a08b26f83cfd60bb95df569b1/app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt#L53
