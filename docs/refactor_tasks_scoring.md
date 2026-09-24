# Refactor round — agent scoring (12 lists, 2 prompts × 6 agents)

Base: master `14da1ba`. Every task was checked against the working tree (grep/read, static only — no Android SDK in the container, so nothing was compiled or run). Open-PR collisions were checked against the 1,032 files touched by the 31 open PRs (merge-base diffs).

## Method

- **Rating (1–100)** = `round(100 · (E/5)^0.25 · (I/5)^0.45 · (F/5)^0.30)`. E = evidence quality of the best write-up once verified (1–5), I = impact (1–5), F = risk-adjusted feasibility (1–5): `6 − risk`, minus 1 when an edited file is in a stale/on-hold/draft open PR, minus 2 when it's in an active one (17187, 17254, 17435, 15226), floor 1. The multiplicative form means a weak axis drags the score down; all-1s scores 20.
- **Ship/drop:** a task ships when its premise holds (verdict HOLDS, or PARTIAL where the core problem is real and a corrected version is written up). It's dropped when the premise is false: the problem isn't there, it's already fixed, the benefit is illusory for the stated reason, or the change is infeasible or harmful. A task whose only stated rationale is false is dropped even if the verifier spotted a different real issue in the same code; that issue is noted but not credited.
- **Points:** each shipped task is worth exactly 1 point. A unique proposer gets 1. Duplicates split the 1 **in proportion to write-up completeness** (1–5 each, judged on correctness, coverage and tests; list order ignored). The equal 1/n split is shown alongside. Dropped raw tasks score 0.
- **Fragment merge (judgment call):** codex's 10 performance tasks each remove one Robolectric SDK pin. Only two (ChatAdapterTest/33, ResourcesFilterFragmentTest/32) free a sandbox on their own, because each is the last pin of its level in its CI shard. The other eight save nothing individually, and their own "do not combine" rule prevents the saving. They ship as two cohort tasks (25, 55), each worth 1 point to codex. If each fragment were counted separately, codex·perf would score 10.0 / 10 instead of 4.0.
- **Normalised** = points ÷ 10 tasks submitted. **Quality-weighted** = Σ(share × rating/100); normalised the same way.

## Per list

| list | agent · prompt | submitted | kept raw | dropped | points (completeness) | points (equal 1/n) | per submitted | quality-weighted | QW per submitted |
|---|---|---|---|---|---|---|---|---|---|
| P1-claude | claude opus 5.5 · perf | 10 | 9 | 1 | 8.339 | 8.000 | 0.834 | 4.785 | 0.479 |
| R1-claude | claude opus 5.5 · repo | 10 | 9 | 1 | 7.875 | 7.500 | 0.787 | 4.689 | 0.469 |
| R3-devin | devin swe 2 · repo | 10 | 9 | 1 | 7.250 | 7.500 | 0.725 | 3.979 | 0.398 |
| R6-gemini | copilot gemini 3.5 flash · repo | 10 | 7 | 3 | 6.286 | 6.500 | 0.629 | 3.380 | 0.338 |
| R5-grok | copilot grok 4.5 · repo | 10 | 7 | 3 | 5.589 | 5.500 | 0.559 | 3.243 | 0.324 |
| R2-codex | codex sol 5.6 · repo | 10 | 5 | 5 | 5.000 | 5.000 | 0.500 | 2.260 | 0.226 |
| P4-kimi | copilot kimi k3 · perf | 10 | 5 | 5 | 4.375 | 4.500 | 0.438 | 2.127 | 0.213 |
| P2-codex | codex sol 5.6 · perf | 10 | 10 | 0 | 4.000 | 4.000 | 0.400 | 2.240 | 0.224 |
| R4-kimi | copilot kimi k3 · repo | 10 | 4 | 6 | 4.000 | 4.000 | 0.400 | 1.920 | 0.192 |
| P5-grok | copilot grok 4.5 · perf | 10 | 4 | 6 | 3.286 | 3.500 | 0.329 | 1.637 | 0.164 |
| P3-devin | devin swe 2 · perf | 10 | 3 | 7 | 3.000 | 3.000 | 0.300 | 1.560 | 0.156 |
| P6-gemini | copilot gemini 3.5 flash · perf | 10 | 1 | 9 | 1.000 | 1.000 | 0.100 | 0.440 | 0.044 |

## Per agent (both prompts)

| rank | agent | submitted | kept raw | dropped | points (completeness) | points (equal 1/n) | per submitted | quality-weighted | QW per submitted |
|---|---|---|---|---|---|---|---|---|---|
| 1 | claude opus 5.5 | 20 | 18 | 2 | 16.214 | 15.500 | 0.811 | 9.474 | 0.474 |
| 2 | devin swe 2 | 20 | 12 | 8 | 10.250 | 10.500 | 0.512 | 5.539 | 0.277 |
| 3 | codex sol 5.6 | 20 | 15 | 5 | 9.000 | 9.000 | 0.450 | 4.500 | 0.225 |
| 4 | copilot grok 4.5 | 20 | 11 | 9 | 8.875 | 9.000 | 0.444 | 4.880 | 0.244 |
| 5 | copilot kimi k3 | 20 | 9 | 11 | 8.375 | 8.500 | 0.419 | 4.047 | 0.202 |
| 6 | copilot gemini 3.5 flash | 20 | 8 | 12 | 7.286 | 7.500 | 0.364 | 3.820 | 0.191 |
| | **total** | 120 | 73 | 47 | 60.000 | 60.000 | | 32.260 | |

## Duplicate splits

| backlog # | task | proposers (completeness → share) | why the split |
|---|---|---|---|
| 1 | chunk the notification IN-list lookups (sync fails on API 26-30 at 1000 docs) | R1-claude#1 (5 → 0.625), R3-devin#7 (3 → 0.375) | claude found the real 1000-doc sync trigger, `deleteByIds` and the projection, with a DAO test; devin's signature-preserving DAO-only design is simpler but misses `deleteByIds` and the trigger |
| 2 | bound the text-viewer read and parse Markdown off the main thread | P1-claude#6 (5 → 0.625), P4-kimi#3 (3 → 0.375) | claude adds the fill loop, off-main Markdown parse and a test; kimi has the bounded read only, with an unnecessary byte-budget branch |
| 3 | typed upload results from PersonalsRepository.uploadPersonal | R5-grok#4 (5 → 0.714), R6-gemini#2 (2 → 0.286) | grok has the correct paths and files and catches the Success-for-failure bug; gemini gives the wrong VM path (`ui/life/`) and omits the VM, its test and the strings |
| 6 | stop ResourcesRepository leaking LibraryTitleProjection; trim internal-only surface | R1-claude#7 (5 → 0.625), R5-grok#1 (3 → 0.375) | claude is clean and also trims five internal-only methods; grok's DTO part is right and its `observeOpenedResourceIds` fix valid, but its RemovedLogDao step would undo deliberate batching |
| 12 | check foreground eligibility once per DownloadWorker run, not every 500 ms | P1-claude#9 (5 → 0.714), P5-grok#8 (2 → 0.286) | claude names the real per-tick call site, the fix and a test; grok names the right IPC but not where it is hot (the per-tick call in DownloadWorker), and its ProcessLifecycleOwner swap changes semantics |
| 19 | route LoginActivity/GuestLoginExtensions repository calls through LoginViewModel | R1-claude#5 (5 → 0.625), R3-devin#9 (3 → 0.375) | claude's call sites are accurate and it has tests; devin's step 4 (`ServerDialogExtensions` → `loginViewModel`) can't compile, and it has no test |
| 46 | move NotificationsRepositoryImpl's team-task reads behind TeamsNotificationsRepository | R3-devin#1 (3 → 0.500), R5-grok#6 (3 → 0.500) | both propose the same illusory pass-through wrapper; the corrected route comes from verification, so it's an even split |

## Provenance: all 120 raw tasks

### P1-claude — claude opus 5.5, performance

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | UserEntity image URL as file | → backlog #5 | 1.000 | 66 |
| 2 | DownloadService batched persist | → backlog #14 | 1.000 | 59 |
| 3 | batch HTML reconcile | → backlog #16 | 1.000 | 59 |
| 4 | SecurePrefs legacy cache | → backlog #21 | 1.000 | 59 |
| 5 | chat refresh generation counter | **dropped** — nothing emits a "chats" realtime update (only "courses", CoursesRepositoryImpl.kt:257/378/439); the reload-on-STARTED it removes is the only thing refreshing the phone chat list, so it would go stale | 0 | — |
| 6 | text viewer bounded read + md off-main | → backlog #2 (merged) | 0.625 | 74 |
| 7 | NotificationsViewModel off-main | → backlog #33 | 1.000 | 54 |
| 8 | StepExam shallow copy | → backlog #53 | 1.000 | 43 |
| 9 | DownloadWorker FGS check once | → backlog #12 (merged) | 0.714 | 62 |
| 10 | retry payload kotlinx | → backlog #44 | 1.000 | 48 |

### P2-codex — codex sol 5.6, performance

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | LifeAdapterTest unpin 34 | → backlog #55 (merged) | 1.000 | 40 |
| 2 | ServerAddressAdapterTest unpin 34 | → backlog #55 (merged) | 1.000 | 40 |
| 3 | PersonalsAdapterTest unpin 34 | → backlog #55 (merged) | 1.000 | 40 |
| 4 | VoicesActionsTest unpin 34 | → backlog #55 (merged) | 1.000 | 40 |
| 5 | ChatAdapterTest unpin 33 | → backlog #9 | 1.000 | 63 |
| 6 | ResourcesFilterFragmentTest unpin 32 | → backlog #8 | 1.000 | 63 |
| 7 | ResourcesAdapterTest unpin 32 | → backlog #25 (merged) | 1.000 | 58 |
| 8 | EnterprisesReportsFragmentTest unpin 32 | → backlog #25 (merged) | 1.000 | 58 |
| 9 | ExamDaoTest unpin 32 | → backlog #25 (merged) | 1.000 | 58 |
| 10 | CourseDaoTest unpin 32 | → backlog #25 (merged) | 1.000 | 58 |

### P3-devin — devin swe 2, performance

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | member-visit batching | → backlog #30 | 1.000 | 54 |
| 2 | CalendarFragment day index | **dropped** — moves one O(n) filter from each tap to each emission; no measurable win | 0 | — |
| 3 | VoicesLabelManager label map | **dropped** — the scan runs only on chip-close click; associateBy would add per-render work and change which duplicate label wins ("offer"/"Offer") | 0 | — |
| 4 | identity .map { it } + split hoist | → backlog #49 | 1.000 | 43 |
| 5 | lowercase correct choices at write | **dropped** — harmful — setCorrectChoices has zero production callers; correctChoiceList is written directly in mixed case (ExamQuestion.kt:114, CoursesRepositoryImpl.kt:800), so removing the per-compare lowercase breaks grading and ExamAnswerUtilsTest :31/:48 | 0 | — |
| 6 | chat bind styling hoists | **dropped** — two of three sites run on user action, not per bind; the third is a readability nit with no perf effect | 0 | — |
| 7 | getString hoist (finance/life/exam) | **dropped** — saves only a string-table lookup per bind (String.format still runs); miscounted sites (Finances 2×/bind not 4×); cached templates + String.format change locale semantics | 0 | — |
| 8 | getString/getColor hoist (member/user) | **dropped** — same as #7 — negligible saving, locale hazard (Arabic CDATA/LRM template), MemberMenuAdapter has a handful of rows | 0 | — |
| 9 | countTopLevelByTeams hoist | → backlog #23 | 1.000 | 59 |
| 10 | buffered WebView stream + regex const | **dropped** — WebView reads in large chunks so buffering adds nothing; regex compile is µs next to a network call | 0 | — |

### P4-kimi — copilot kimi k3, performance

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | flatten row_survey.xml | **dropped** — weighted children are 0dp in a fixed-width parent (single measure); short, non-hot list | 0 | — |
| 2 | flatten item_library_grid badge | **dropped** — not a perf win and changes behaviour — ResourcesAdapter.kt:466 sets ivDownloaded INVISIBLE for opened+offline items, which would now hide the badge circle too | 0 | — |
| 3 | text viewer bounded read | → backlog #2 (merged) | 0.375 | 74 |
| 4 | CrashLogStore per-file cap | **dropped** — store already caps at MAX_PENDING_FILES=20 (CrashLogStore.kt:17), logs are single stack traces, sweep runs on IO; cap would diverge stored vs live logs | 0 | — |
| 5 | setHasFixedSize batch A | → backlog #42 | 1.000 | 48 |
| 6 | setHasFixedSize batch B | → backlog #59 | 1.000 | 36 |
| 7 | OnboardingAdapter view binding | → backlog #52 | 1.000 | 43 |
| 8 | parallelise join-request lookups | **dropped** — two ms-scale indexed PK lookups already running inside an async alongside three others; Room serialises on low-RAM devices | 0 | — |
| 9 | join-request batch tests | → backlog #24 | 1.000 | 58 |
| 10 | CrashLogStoreTest | **dropped** — CrashLogStoreTest.kt already exists and covers cases a, b, d; list calls the field `content` (it is `error`) | 0 | — |

### P5-grok — copilot grok 4.5, performance

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | Glide override for finance thumbs | **dropped** — fixed 64dp view + centerCrop — Glide already decodes at view size and caches the transformed resource | 0 | — |
| 2 | Glide override for report thumbs | **dropped** — same as #1 (120dp) | 0 | — |
| 3 | PdfThumbnailLoader race + recycle | **dropped** — harmful — evicted bitmaps are still shown in ImageViews (onTrimMemory → evictAll), recycling would crash; the proposed re-check wouldn't stop the double render | 0 | — |
| 4 | downscale renderPdf bitmap | **dropped** — PdfRenderer page sizes are points (A4 ≈ 2 MB bitmap); scaling to screen width would usually enlarge it | 0 | — |
| 5 | offline title map by ids | → backlog #45 | 1.000 | 46 |
| 6 | LifeCache copies + blocking write | **dropped** — write already uses apply() (no commit) and there is no double toJson; copies are ~12 tiny objects | 0 | — |
| 7 | resolveType keyword scan | **dropped** — not a hotspot (a few dozen contains() per notification on open); rewrite would be readability only | 0 | — |
| 8 | ProcessLifecycleOwner FGS check | → backlog #12 (merged) | 0.286 | 62 |
| 9 | ChatAdapter id-keyed animation | → backlog #54 | 1.000 | 40 |
| 10 | resource sync keyset paging | → backlog #13 | 1.000 | 60 |

### P6-gemini — copilot gemini 3.5 flash, performance

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | getMimeType via getFileExtension | → backlog #48 | 1.000 | 44 |
| 2 | UserArrayAdapter listener in init | **dropped** — one lambda per bind is noise; list is a short saved-logins list; no correctness issue | 0 | — |
| 3 | ChatAdapter listener in init | **dropped** — perf premise false; the real stale-position issue is covered by task 54 (grok), and the named 'ChatViewHolder' doesn't exist (three nested holders) | 0 | — |
| 4 | CheckboxAdapter listener in init | **dropped** — negligible allocation; small dialog lists | 0 | — |
| 5 | ServerAddressAdapter listener in init | **dropped** — perf premise false (≤ a handful of server presets); a minor stale-position capture exists but was not the claim | 0 | — |
| 6 | HealthUsersAdapter listener in init | **dropped** — perf premise false. NB verification found a real stale-listener bug here (payload partial-bind never refreshes the captured UserEntity) — worth a separate correctness task, not credited | 0 | — |
| 7 | Sha256Utils class → object | **dropped** — one call per APK update (ConfigurationsRepositoryImpl.kt:253, not :434), not 'during sync'; breaks three mockkConstructor(Sha256Utils::class) tests not in the file list | 0 | — |
| 8 | LifeAdapter listeners in init | **dropped** — ~7-row list; no measurable allocation cost | 0 | — |
| 9 | SubmissionsAdapter listener out of updateSubmissionCount | **dropped** — no measurable win, and SubmissionsAdapter.kt is in open PR 14650 | 0 | — |
| 10 | UsersAdapter listener in init | **dropped** — negligible; no correctness issue | 0 | — |

### R1-claude — claude opus 5.5, repository boundaries

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | notification IN-list chunking | → backlog #1 (merged) | 0.625 | 94 |
| 2 | RetryRepository.recordFailure | → backlog #20 | 1.000 | 59 |
| 3 | shelf discovery → SyncRepository | → backlog #22 | 1.000 | 59 |
| 4 | leaders fetch → ConfigurationsRepository | → backlog #17 | 1.000 | 59 |
| 5 | LoginActivity → LoginViewModel | → backlog #19 (merged) | 0.625 | 59 |
| 6 | ChatViewModel community config | → backlog #40 | 1.000 | 48 |
| 7 | ResourcesRepository projection + surface | → backlog #6 (merged) | 0.625 | 66 |
| 8 | enterprise single UPDATEs | → backlog #15 | 1.000 | 59 |
| 9 | My Life visibility re-read | **dropped** — unreachable — only LifeRepositoryImpl writes my_life and every seeded row gets a UUID _id (LifeRepositoryImpl.kt:131), so the imageId/title fallback key never occurs; COUNT→EXISTS on ~12 rows saves nothing | 0 | — |
| 10 | Personals dead surface | → backlog #43 | 1.000 | 48 |

### R2-codex — codex sol 5.6, repository boundaries

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | updateTeamNotification Int | → backlog #38 | 1.000 | 50 |
| 2 | set-based updatesFor | → backlog #57 | 1.000 | 39 |
| 3 | LifeCache string store | → backlog #50 | 1.000 | 43 |
| 4 | Personals TimeProvider | → backlog #26 | 1.000 | 58 |
| 5 | retry processing flag from DAO | **dropped** — executeOperation never sets the flag; RetryQueueWorker releases it in finally. The only real wart (non-atomic check-then-set at RetryQueueWorker.kt:112-118) is in files the task forbids | 0 | — |
| 6 | normalise shelf cache | **dropped** — shelf ids come from CouchDB _all_docs and are unique; the whitespace/duplicate state is unreachable | 0 | — |
| 7 | diagnostics printStackTrace | → backlog #58 | 1.000 | 36 |
| 8 | CoursesSteps download selection | **dropped** — the duplication is one filter predicate; nothing to hoist without touching the repository interface the task forbids | 0 | — |
| 9 | dedupe upload successes | **dropped** — UploadCoordinator emits exactly one result per item; defends against input the pipeline never produces | 0 | — |
| 10 | AnswerDao chunk boundaries | **dropped** — empty short-circuit and 900-chunking already exist and are tested | 0 | — |

### R3-devin — devin swe 2, repository boundaries

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | TeamTasksReader wrapper | → backlog #46 (merged) | 0.500 | 45 |
| 2 | Activities userDao → UserRepository | → backlog #11 | 1.000 | 62 |
| 3 | UserRepo foreign DAOs | → backlog #31 | 1.000 | 54 |
| 4 | drop CommunitySyncWriter | → backlog #47 | 1.000 | 44 |
| 5 | Submissions boundary | → backlog #28 | 1.000 | 57 |
| 6 | TeamLogDao chunk | → backlog #27 | 1.000 | 58 |
| 7 | NotificationDao chunk | → backlog #1 (merged) | 0.375 | 94 |
| 8 | MyLibraryDao.getAll | → backlog #51 | 1.000 | 43 |
| 9 | LoginActivity → LoginViewModel | → backlog #19 (merged) | 0.375 | 59 |
| 10 | SyncViewModel for ProcessUserDataActivity | **dropped** — infeasible — its syncRepository/userRepository fields are used by SyncActivity, LoginActivity and BecomeMemberActivity; fetchUserSecurityData returns Unit, not a Flow | 0 | — |

### R4-kimi — copilot kimi k3, repository boundaries

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | ResourceDetailViewModel | → backlog #35 | 1.000 | 53 |
| 2 | BecomeMemberViewModel | → backlog #34 | 1.000 | 53 |
| 3 | AchievementFragment → VM | → backlog #36 | 1.000 | 50 |
| 4 | serializeForUpload move | → backlog #60 | 1.000 | 36 |
| 5 | consolidate OfflineActivityDao queries | **dropped** — the pair filters different columns (userId vs userName); an (:x IS NULL OR col = :x) form defeats the indexes — a regression | 0 | — |
| 6 | MeetupDao projection | **dropped** — CommunityRepositoryImpl reads MeetupDao only in the sync merge, which needs full rows — nothing to project | 0 | — |
| 7 | NewsDao paged query | **dropped** — the feed filters and re-sorts in Kotlin by sortDate, so SQL LIMIT/OFFSET by time returns wrong pages; no consumer in scope | 0 | — |
| 8 | Dictionary composition | **dropped** — DictionaryRepository is already three thin methods; nothing to push down | 0 | — |
| 9 | CourseProgressDao parameterised query | **dropped** — the dynamic query already binds every value via ? placeholders (no injection); the proposed IN query mixes users/courses, which CourseProgressDaoTest:57 guards against | 0 | — |
| 10 | ReplyViewModel for ReplyActivity | **dropped** — ReplyViewModel already exists and ReplyActivity uses it (ReplyActivity.kt:52); the proposed VM-builds-a-Fragment wrapper is an anti-pattern | 0 | — |

### R5-grok — copilot grok 4.5, repository boundaries

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | LibraryTitle DTO + RemovedLogDao | → backlog #6 (merged) | 0.375 | 66 |
| 2 | Life without SharedPrefManager | → backlog #18 | 1.000 | 59 |
| 3 | Diagnostics without SharedPrefManager | **dropped** — cosmetic — ConfigurationsRepository.getParentCode() just returns sharedPrefManager.getParentCode(), and no repository exposes a planet-code getter | 0 | — |
| 4 | Personals typed upload results | → backlog #3 (merged) | 0.714 | 70 |
| 5 | Enterprises report DTO | → backlog #29 | 1.000 | 54 |
| 6 | TaskNotificationLookup wrapper | → backlog #46 (merged) | 0.500 | 45 |
| 7 | Settings single clear | → backlog #41 | 1.000 | 48 |
| 8 | UploadRepository network-only | **dropped** — infeasible and harmful — needs locked UploadManager.kt (3 upload() calls), moves DAOs into a service class, and RoomUploadConfig has no guest filter (ExamResults regression) | 0 | — |
| 9 | Retry: remove android.util.Log | **dropped** — loses diagnostics (DB keeps only e.message) and contradicts docs/CODE_STYLE_GUIDE.md:624-657, which prescribes these Log.e calls | 0 | — |
| 10 | Activities aggregation → VM | → backlog #4 | 1.000 | 66 |

### R6-gemini — copilot gemini 3.5 flash, repository boundaries

| # | task | outcome | credit | rating |
|---|---|---|---|---|
| 1 | MyLife R import | → backlog #39 | 1.000 | 49 |
| 2 | Personals structured results | → backlog #3 (merged) | 0.286 | 70 |
| 3 | checkHealth result type | → backlog #56 | 1.000 | 39 |
| 4 | LifeCache → kotlinx | → backlog #32 | 1.000 | 54 |
| 5 | Dictionary → kotlinx | → backlog #37 | 1.000 | 50 |
| 6 | MyLifeDao exact-match userId | **dropped** — harmful — anonymous rows are seeded with NULL userId (LifeRepositoryImpl.kt:135); exact match hides them and seedMyLifeIfEmpty re-inserts duplicates; legacy ''/'--' rows orphaned | 0 | — |
| 7 | PersonalDao exact-match userId | **dropped** — harmful — blank userId intentionally means 'any user' for the duplicate-title check (AddResourceViewModel.kt:28/36); exact match disables it | 0 | — |
| 8 | DictionaryDaoTest | → backlog #7 | 1.000 | 63 |
| 9 | RemovedLogDaoTest | → backlog #10 | 1.000 | 63 |
| 10 | DeviceNameProviderTest | **dropped** — the 'system fallback name' behaviour it would assert doesn't exist — the class is pure delegation, and NetworkUtils.getDeviceName is already covered by NetworkUtilsTest.kt:58-70 | 0 | — |

## Counts

- lists: 12 (expected 12) · raw tasks: 120 (expected 120+; every list has exactly 10)
- raw kept: 73 · raw dropped: 47
- shipped (after merging): 60. Of those, 7 are duplicate merges (2 raw each) and 2 are codex fragment cohorts (4 raw each): 73 − 7 − 6 = 60.
- point-sum check: Σ points (completeness split) = 60.000000; Σ points (equal split) = 60.000000; shipped tasks = 60. Both sums equal the shipped count ✓

## Judgment calls and guesses

- **Duplicate credit** is split by a 1–5 completeness score. The weights are mine; the equal 1/n column gives the same agent ranking, except that codex and grok tie at 9.0.
- **Codex fragment merge:** eight single-pin removals are folded into two cohort tasks (see Method). This is the one place a raw task that holds was not credited 1:1.
- **Harmonised verdicts:** devin·repo#1 (PARTIAL) and grok·repo#6 (FALSE) are the same wrapper idea. Both ship as task 46 with the corrected route and an even split, because the leak is real.
- **Overridden verifier:** the grok·perf#8 verifier said the FGS check runs once per download. In fact `DownloadWorker.showProgressNotification` calls it every 500 ms (`DownloadWorker.kt:166`, called from `:143`), so it ships, merged into task 12.
- **Merged-task evidence score:** E uses the best write-up among the proposers.
- **Open-PR penalties:** the active/stale split comes from GitHub `updated_at` on 2026-09-24. Brainstorm, on-hold and draft PRs count as stale. PRs 17430, 17356 and 16624 no longer appear in the open list but were in the 31 given, so their files still count.
- **Impact scores** follow the verifiers' 1–5, with two deliberate raises. Task 1 goes to 5: it's silent data loss on API 26–30 devices, which dominate low-cost deployments. Task 13 goes to 4: sync wall time grows with catalogue size, and paging by `skip` also risks deleting valid resources in `removeDeletedResources`.
- **Verification is static.** The container has no Android SDK, so no task was compiled or tested. Some claims (e.g. unpinned tests passing on SDK 36) rest on analogous tests that already pass unpinned.
- **A missed bug outside every list:** gemini·perf#6's HealthUsersAdapter has a real stale-listener bug that the list didn't claim. It's noted, not added to the backlog.
