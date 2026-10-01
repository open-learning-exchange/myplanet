# Refactor-round agent scoring — 2026-10-01

Companion to `refactor_tasks.md`. Base commit `286e66e0e` (all 18 branches fork from it). Open-PR file sets came from the heads of all 33 open PRs, diffed against their merge-bases (1,064 unique paths).

## Method

- **Verification**: I opened every cited file at `286e66e0e` and checked each premise with grep or a read. A task is **dropped** if its factual premise is false, or if the proposed change can't deliver the claimed benefit at real sizes (no gain, or a regression). If the premise holds but part of the spec is wrong, the task ships with a *watch out* note.
- **Rating (1–100)** = 100 × (E/10)^0.3 × (I/10)^0.45 × (F/10)^0.25, a weighted geometric mean, so a near-zero on any axis drags the score down. Weights: **evidence quality 30 %, impact 45 %, risk-adjusted feasibility 25 %**. E is how exactly the claim matched the code. I is user-visible or architectural payoff. F is blast radius, behavioural risk, test coverage, and collision with open-PR files. The floor for a correct but negligible task (I = 1) is about 30.
- **Agent points**: each shipped merged task is worth exactly 1 point. A unique task gives its single proposer 1. A duplicate is split among the distinct agents who proposed it, **weighted by completeness** (each proposer's coverage of the merged scope, 0–1, shown next to each raw id). List order doesn't matter. An agent that proposed the same task twice counts once, at its best completeness. Dropped tasks score 0. The 1/n split is shown for reference only.
- **Per-submitted** = points ÷ raw tasks submitted. **Quality-weighted** = Σ(share × rating/100), and **QW per submitted** normalizes that the same way.

## Agent scores

| agent | submitted | shipped | dropped | unique good | shared good | points (completeness) | points (1/n ref) | points / submitted | quality-weighted | QW / submitted |
|---|---|---|---|---|---|---|---|---|---|---|
| claude opus 5.5 | 30 | 30 | 0 | 25 | 5 | 27.81 | 27.03 | 0.927 | 15.72 | 0.524 |
| devin swe 2 | 30 | 27 | 3 | 22 | 5 | 24.46 | 24.50 | 0.815 | 10.47 | 0.349 |
| copilot kimi k3 | 33 | 22 | 11 | 17 | 5 | 18.43 | 18.78 | 0.558 | 7.70 | 0.233 |
| copilot grok 4.6 | 30 | 27 | 3 | 15 | 9 | 18.54 | 18.45 | 0.618 | 7.53 | 0.251 |
| copilot gemini 3.8 flash | 30 | 21 | 9 | 13 | 7 | 14.92 | 15.62 | 0.497 | 6.29 | 0.210 |
| codex sol 5.6 | 30 | 20 | 10 | 11 | 7 | 13.83 | 13.62 | 0.461 | 5.83 | 0.194 |
| **total** | 183 | 147 | 36 | 103 | 38 | 118.00 | 118.00 | | 53.54 | |

Ranked by quality-weighted points. kimi's 33 includes its three "pick exactly one" alternatives (3b, 4b, 8b). Each was a separately written proposal, so each is counted.

## Per-round counts (shipped / submitted)

| agent | perf quick wins | repository boundaries | micro-optimizations |
|---|---|---|---|
| devin swe 2 | 10 / 10 | 9 / 10 | 8 / 10 |
| codex sol 5.6 | 3 / 10 | 8 / 10 | 9 / 10 |
| claude opus 5.5 | 10 / 10 | 10 / 10 | 10 / 10 |
| copilot gemini 3.8 flash | 7 / 10 | 7 / 10 | 7 / 10 |
| copilot kimi k3 | 9 / 10 | 8 / 13 | 5 / 10 |
| copilot grok 4.6 | 8 / 10 | 9 / 10 | 10 / 10 |

## Provenance (every raw task)

| raw id | agent | outcome | merged task | rating | credit (completeness share) |
|---|---|---|---|---|---|
| P-dev-1 | dev | shipped | #17 enterprises: read attachment images off the main thread when saving a transaction or report | 61 | 1.000 |
| P-dev-2 | dev | shipped | #38 sync: run AutoSyncWorker's independent uploads concurrently | 51 | 1.000 |
| P-dev-3 | dev | shipped | #97 ui: cache formatted dates and hoist listeners in SubmissionsListAdapter and FeedbackReplyAdapter | 32 | 0.667 |
| P-dev-4 | dev | shipped | #99 ui: hoist per-bind listeners in PersonalsAdapter, StorageCategoryDetail's ResourceAdapter and ReferencesAdapter | 32 | 0.538 |
| P-dev-5 | dev | shipped | #100 ui: hoist the startSurvey listener and label strings in SurveysAdapter | 32 | 1.000 |
| P-dev-6 | dev | shipped | #110 sync: drop per-event synchronizedList wrappers in SyncTimeLogger | 30 | 1.000 |
| P-dev-7 | dev | shipped | #104 ui: hoist per-bind listeners and constant styling in ResourcesTagsAdapter | 31 | 1.000 |
| P-dev-8 | dev | shipped | #105 ui: hoist per-bind listeners in ChatHistory, Checkbox and TeamsSelection adapters | 31 | 1.000 |
| P-dev-9 | dev | shipped | #106 ui: hoist per-bind listeners in NotificationsAdapter | 31 | 1.000 |
| P-dev-10 | dev | shipped | #98 ui: cache per-bind resource lookups in Onboarding, CoursesSteps and ChatShareTarget adapters | 32 | 1.000 |
| P-cdx-1 | cdx | shipped | #10 sync: return the first successful configuration probe without waiting for the slower one | 68 | 1.000 |
| P-cdx-2 | cdx | shipped | #37 chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default | 51 | 0.194 |
| P-cdx-3 | cdx | shipped | #70 sync: snapshot SyncTimeLogger aggregates once per summary | 41 | 1.000 |
| P-cdx-4 | cdx | **dropped** | — | — | 0 |
| P-cdx-5 | cdx | **dropped** | — | — | 0 |
| P-cdx-6 | cdx | **dropped** | — | — | 0 |
| P-cdx-7 | cdx | **dropped** | — | — | 0 |
| P-cdx-8 | cdx | **dropped** | — | — | 0 |
| P-cdx-9 | cdx | **dropped** | — | — | 0 |
| P-cdx-10 | cdx | **dropped** | — | — | 0 |
| P-cld-1 | cld | shipped | #9 perf: drop the double map lookup and per-call lambda allocation in GsonUtils field getters | 69 | 1.000 |
| P-cld-2 | cld | shipped | #1 sync: skip the alternative-server probe when the primary already answered | 83 | 1.000 |
| P-cld-3 | cld | shipped | #2 viewer: extract PDF text only when Read Aloud is tapped, and drop the duplicate audio Player.Listener | 81 | 1.000 |
| P-cld-4 | cld | shipped | #12 login: stop inflating an unused server dialog and re-syncing community docs on every LoginActivity create | 67 | 1.000 |
| P-cld-5 | cld | shipped | #14 startup: copy map-tile assets off the main thread at cold start | 64 | 1.000 |
| P-cld-6 | cld | shipped | #24 startup: cache the manifest permission set and delete the dead install-permission check | 55 | 1.000 |
| P-cld-7 | cld | shipped | #47 upload: stop recounting the whole download queue three times per file | 48 | 1.000 |
| P-cld-8 | cld | shipped | #5 download: cap PDF thumbnail size and keep the cache key's stat calls off the main thread | 73 | 0.714 |
| P-cld-9 | cld | shipped | #4 chat: chunk the AI typing animation and stop scrolling the list on every character | 73 | 0.476 |
| P-cld-10 | cld | shipped | #15 dashboard: skip the no-op notification upsert on every dashboard load | 62 | 1.000 |
| P-gem-1 | gem | **dropped** | — | — | 0 |
| P-gem-2 | gem | shipped | #26 sync: HEAD-probe and always close connections in ServerUrlMapper.isUrlDirectlyReachable | 54 | 0.375 |
| P-gem-3 | gem | shipped | #76 sync: build shelf-sync request bodies with kotlinx directly | 38 | 1.000 |
| P-gem-4 | gem | shipped | #53 download: derive the 404 diagnostic URL from the request instead of regex over response.toString() | 46 | 0.412 |
| P-gem-5 | gem | shipped | #18 audio: stop sending the user to the home screen when a recording is stopped too early | 60 | 1.000 |
| P-gem-6 | gem | shipped | #63 anr: measure the reported ANR duration after the wait, not before it | 43 | 1.000 |
| P-gem-7 | gem | **dropped** | — | — | 0 |
| P-gem-8 | gem | **dropped** | — | — | 0 |
| P-gem-9 | gem | shipped | #114 data: reuse empty JsonObject/JsonArray singletons in JsonUtils | 28 | 1.000 |
| P-gem-10 | gem | shipped | #37 chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default | 51 | 0.129 |
| P-kim-1 | kim | shipped | #4 chat: chunk the AI typing animation and stop scrolling the list on every character | 73 | 0.286 |
| P-kim-2 | kim | shipped | #13 voices: chunk the typing animation in VoicesAdapterHelper | 67 | 1.000 |
| P-kim-3 | kim | shipped | #36 calendar: index meetups by local date once per emission (in CalendarViewModel) | 51 | 0.250 |
| P-kim-4 | kim | shipped | #20 reachability: cache primary-server probes in ServerReachabilityProvider | 57 | 1.000 |
| P-kim-5 | kim | shipped | #97 ui: cache formatted dates and hoist listeners in SubmissionsListAdapter and FeedbackReplyAdapter | 32 | 0.333 |
| P-kim-6 | kim | shipped | #117 life: replace LifeAdapter's getIdentifier lookup with a compile-time map | 27 | 1.000 |
| P-kim-7 | kim | shipped | #94 health: drop dead string concatenations and reorder HealthExaminationAdapter's diff | 32 | 1.000 |
| P-kim-8 | kim | shipped | #99 ui: hoist per-bind listeners in PersonalsAdapter, StorageCategoryDetail's ResourceAdapter and ReferencesAdapter | 32 | 0.462 |
| P-kim-9 | kim | shipped | #109 dashboard: configure the activities chart once, update data per emission | 30 | 1.000 |
| P-kim-10 | kim | **dropped** | — | — | 0 |
| P-grk-1 | grk | shipped | #4 chat: chunk the AI typing animation and stop scrolling the list on every character | 73 | 0.238 |
| P-grk-2 | grk | **dropped** | — | — | 0 |
| P-grk-3 | grk | shipped | #5 download: cap PDF thumbnail size and keep the cache key's stat calls off the main thread | 73 | 0.286 |
| P-grk-4 | grk | **dropped** | — | — | 0 |
| P-grk-5 | grk | shipped | #103 life: make LifeCache entries immutable and stop copying on every read | 31 | 1.000 |
| P-grk-6 | grk | shipped | #52 dictionary: drop the duplicate COUNT on DictionaryActivity open | 46 | 0.500 |
| P-grk-7 | grk | shipped | #115 courses: bind course covers with the ImageView as Glide's lifecycle owner | 27 | 1.000 |
| P-grk-8 | grk | shipped | #37 chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default | 51 | 0.258 |
| P-grk-9 | grk | shipped | #102 life: hoist LifeAdapter's per-bind listeners onto the ViewHolder | 31 | 1.000 |
| P-grk-10 | grk | shipped | #116 dictionary: assign deterministic dictionary ids instead of a UUID per word | 27 | 1.000 |
| R-dev-1 | dev | shipped | #19 courses: delete the CourseDetailProvider/RatingSummaryProvider passthroughs | 57 | 0.500 |
| R-dev-2 | dev | shipped | #31 chat: extract conversation pagination from ChatViewModel into a plain pager | 52 | 1.000 |
| R-dev-3 | dev | shipped | #57 user: route achievement calls through UserAchievementsRepository | 46 | 1.000 |
| R-dev-4 | dev | shipped | #55 surveys: move respondent JSON parsing into SurveysPublicMapper | 46 | 1.000 |
| R-dev-5 | dev | shipped | #28 courses: give the progress grid a typed row model | 53 | 1.000 |
| R-dev-6 | dev | **dropped** | — | — | 0 |
| R-dev-7 | dev | shipped | #34 enterprises: project finance reports and delete dead TeamDao queries | 52 | 1.000 |
| R-dev-8 | dev | shipped | #21 data: move resource _all_docs fetching and doc filtering from SyncManager into SyncRepository | 56 | 1.000 |
| R-dev-9 | dev | shipped | #43 teams: unify leader hand-off before member removal in RequestsViewModel | 49 | 1.000 |
| R-dev-10 | dev | shipped | #29 health: extract examination-item mapping out of HealthExaminationAdapter | 53 | 1.000 |
| R-cdx-1 | cdx | shipped | #65 notifications: read notification lists as NotificationPayload projections | 43 | 1.000 |
| R-cdx-2 | cdx | shipped | #11 enterprises: replace the finance-report read-modify-upsert with one targeted UPDATE | 67 | 0.444 |
| R-cdx-3 | cdx | shipped | #53 download: derive the 404 diagnostic URL from the request instead of regex over response.toString() | 46 | 0.588 |
| R-cdx-4 | cdx | shipped | #45 data: remove android.util.Log from DictionaryRepositoryImpl | 48 | 0.333 |
| R-cdx-5 | cdx | shipped | #50 data: remove android.util.Log from RetryRepositoryImpl | 47 | 0.360 |
| R-cdx-6 | cdx | shipped | #56 sync: route shelf-sync failures through SyncTimeLogger instead of android.util.Log | 46 | 1.000 |
| R-cdx-7 | cdx | shipped | #44 data: remove android.util.Log from DiagnosticsRepositoryImpl | 48 | 0.625 |
| R-cdx-8 | cdx | **dropped** | — | — | 0 |
| R-cdx-9 | cdx | **dropped** | — | — | 0 |
| R-cdx-10 | cdx | shipped | #67 data: share submission loading and page setup in SubmissionsRepositoryExporter | 41 | 1.000 |
| R-cld-1 | cld | shipped | #8 notifications: make sync merge and read/delete check-then-act atomic in NotificationDao | 70 | 1.000 |
| R-cld-2 | cld | shipped | #23 data: slim the retry-queue duplicate lookup and delete dead RetryRepository/RetryDao surface | 55 | 1.000 |
| R-cld-3 | cld | shipped | #11 enterprises: replace the finance-report read-modify-upsert with one targeted UPDATE | 67 | 0.556 |
| R-cld-4 | cld | shipped | #3 data: chunk the remaining unbounded IN-list DAO queries and delete dead DAO methods | 75 | 0.741 |
| R-cld-5 | cld | shipped | #6 sync: replace localized strings in the version-check callback with a typed error (fixes the never-opening server dialog) | 73 | 1.000 |
| R-cld-6 | cld | shipped | #16 health: move key generation, encryption and entity building out of HealthExaminationActivity | 62 | 1.000 |
| R-cld-7 | cld | shipped | #33 data: introduce UserLookupRepository and move single-purpose callers onto it | 52 | 1.000 |
| R-cld-8 | cld | shipped | #35 sync: split ResourcesSyncRepository so the sync layer stops depending on the full resources surface | 52 | 1.000 |
| R-cld-9 | cld | shipped | #27 courses: decide course-step downloads in ResourceDownloadCoordinator, not CoursesStepsViewModel | 53 | 1.000 |
| R-cld-10 | cld | shipped | #54 settings: hoist the guest-user check into SettingsViewModel | 46 | 1.000 |
| R-gem-1 | gem | **dropped** | — | — | 0 |
| R-gem-2 | gem | **dropped** | — | — | 0 |
| R-gem-3 | gem | **dropped** | — | — | 0 |
| R-gem-4 | gem | shipped | #74 health: let HealthViewModel own its realtime-sync subscription | 40 | 1.000 |
| R-gem-5 | gem | shipped | #48 viewer: drop Context from ResourceViewerViewModel | 48 | 1.000 |
| R-gem-6 | gem | shipped | #69 retry: let RetryQueueWorker call RetryRepository directly | 41 | 1.000 |
| R-gem-7 | gem | shipped | #36 calendar: index meetups by local date once per emission (in CalendarViewModel) | 51 | 0.143 |
| R-gem-8 | gem | shipped | #44 data: remove android.util.Log from DiagnosticsRepositoryImpl | 48 | 0.375 |
| R-gem-9 | gem | shipped | #78 notifications: replace NotificationDao.markSynced's raw CASE query with a typed partial update | 37 | 1.000 |
| R-gem-10 | gem | shipped | #42 dashboard: move the challenge-dialog earnings/progress logic into MarkdownViewModel | 50 | 1.000 |
| R-kim-1 | kim | shipped | #71 chat: narrow ChatViewModel's voices/teams dependencies to share-only interfaces | 40 | 1.000 |
| R-kim-2 | kim | shipped | #87 notifications: narrow NotificationsRepositoryImpl's voices/user dependencies | 34 | 1.000 |
| R-kim-3 | kim | shipped | #75 voices: move ReplyActivity's member-detail fetch into ReplyViewModel | 40 | 1.000 |
| R-kim-3b | kim | **dropped** | — | — | 0 |
| R-kim-4 | kim | shipped | #77 data: give CoursesRepository.getMyCoursesFlow a non-suspend signature | 37 | 1.000 |
| R-kim-4b | kim | shipped | #37 chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default | 51 | 0.097 |
| R-kim-5 | kim | **dropped** | — | — | 0 |
| R-kim-6 | kim | shipped | #72 data: extract submission upload serialization into SubmissionsUploadSerializer | 40 | 1.000 |
| R-kim-7 | kim | **dropped** | — | — | 0 |
| R-kim-8 | kim | shipped | #73 di: replace NotificationUtils' hidden entry-point singleton with an injectable binding | 40 | 1.000 |
| R-kim-8b | kim | **dropped** | — | — | 0 |
| R-kim-9 | kim | shipped | #25 tests: add ReplyViewModel.getNewsWithReplies coverage | 55 | 1.000 |
| R-kim-10 | kim | **dropped** | — | — | 0 |
| R-grk-1 | grk | shipped | #32 chat: hoist server prefs and AI-provider fetching from the chat fragments into ChatViewModel | 52 | 1.000 |
| R-grk-2 | grk | shipped | #19 courses: delete the CourseDetailProvider/RatingSummaryProvider passthroughs | 57 | 0.500 |
| R-grk-3 | grk | shipped | #93 courses: log course visits through ActivitiesRepository in TakeCourseViewModel | 32 | 1.000 |
| R-grk-4 | grk | shipped | #59 health: load patients through UserRepository in HealthViewModel | 44 | 1.000 |
| R-grk-5 | grk | shipped | #90 health: stop using the getHealthEntry pair in HealthExaminationViewModel | 33 | 1.000 |
| R-grk-6 | grk | shipped | #45 data: remove android.util.Log from DictionaryRepositoryImpl | 48 | 0.417 |
| R-grk-7 | grk | shipped | #101 life: drop the redundant IO hops in LifeViewModel | 31 | 1.000 |
| R-grk-8 | grk | shipped | #89 community: drop the redundant IO hop in CommunityServicesViewModel | 33 | 1.000 |
| R-grk-9 | grk | shipped | #50 data: remove android.util.Log from RetryRepositoryImpl | 47 | 0.400 |
| R-grk-10 | grk | **dropped** | — | — | 0 |
| M-dev-1 | dev | shipped | #3 data: chunk the remaining unbounded IN-list DAO queries and delete dead DAO methods | 75 | 0.259 |
| M-dev-2 | dev | shipped | #58 anr: mark ANRWatchdog's cross-thread fields @Volatile | 45 | 1.000 |
| M-dev-3 | dev | shipped | #46 sync: route SyncActivity's raw clock reads through the injected TimeProvider | 48 | 1.000 |
| M-dev-4 | dev | shipped | #107 code-health: fix the dead toast catch and qualified delay in utils | 30 | 1.000 |
| M-dev-5 | dev | **dropped** | — | — | 0 |
| M-dev-6 | dev | **dropped** | — | — | 0 |
| M-dev-7 | dev | shipped | #92 upload: deduplicate DownloadUtils' twin enqueue blocks | 33 | 1.000 |
| M-dev-8 | dev | shipped | #86 data: compare URL schemes against literals, not localized strings, in getMinApk | 34 | 1.000 |
| M-dev-9 | dev | shipped | #52 dictionary: drop the duplicate COUNT on DictionaryActivity open | 46 | 0.500 |
| M-dev-10 | dev | shipped | #68 notifications: remove the fixed 200 ms sleep before read-receipt broadcasts | 41 | 1.000 |
| M-cdx-1 | cdx | shipped | #36 calendar: index meetups by local date once per emission (in CalendarViewModel) | 51 | 0.286 |
| M-cdx-2 | cdx | shipped | #108 courses: make CoursesPagerAdapter.containsItem a set lookup | 30 | 1.000 |
| M-cdx-3 | cdx | shipped | #111 teams: make TeamPagerAdapter.containsItem a set lookup | 30 | 1.000 |
| M-cdx-4 | cdx | shipped | #37 chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default | 51 | 0.194 |
| M-cdx-5 | cdx | **dropped** | — | — | 0 |
| M-cdx-6 | cdx | shipped | #113 storage: pass FreeSpaceWorker's batch without a defensive toSet() copy | 29 | 1.000 |
| M-cdx-7 | cdx | shipped | #96 notifications: build join-request details without the intermediate Triple list | 32 | 1.000 |
| M-cdx-8 | cdx | shipped | #10 sync: return the first successful configuration probe without waiting for the slower one | 68 | 1.000 |
| M-cdx-9 | cdx | shipped | #62 upload: suppress duplicate SyncUiState emissions from the user-data upload scheduler | 44 | 1.000 |
| M-cdx-10 | cdx | shipped | #80 data: use the injected TimeProvider for the multi-submission report timestamp | 35 | 1.000 |
| M-cld-1 | cld | shipped | #49 data: make RemovedLogDao.getRemovedDocIds return non-null ids | 47 | 1.000 |
| M-cld-2 | cld | shipped | #85 code-health: remove redundant null-handling on non-null ids (upload, notifications, labels) | 34 | 1.000 |
| M-cld-3 | cld | shipped | #84 code-health: remove redundant null-handling in personals, ratings and login-team code | 34 | 1.000 |
| M-cld-4 | cld | shipped | #22 courses: drop Context from CourseDetailViewModel and CoursesStepsViewModel via StoragePathResolver | 55 | 1.000 |
| M-cld-5 | cld | shipped | #60 settings: show at most 10 retry previews plus a COUNT instead of loading every pending row | 44 | 1.000 |
| M-cld-6 | cld | shipped | #37 chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default | 51 | 0.323 |
| M-cld-7 | cld | shipped | #88 code-health: add a shared debounceDistinct operator and clear two FlowPreview warnings | 33 | 1.000 |
| M-cld-8 | cld | shipped | #30 viewer: move the playback-speed picker off material-dialogs and drop a redundant safe call | 53 | 1.000 |
| M-cld-9 | cld | shipped | #91 tests: clear the compiler warnings in three test files | 33 | 1.000 |
| M-cld-10 | cld | shipped | #39 test-infra: separate warm-up outliers from real class cost in the CI slow-test summary | 51 | 1.000 |
| M-gem-1 | gem | shipped | #112 ci: read release step conclusions from the jobs listing in playstore.sh | 29 | 1.000 |
| M-gem-2 | gem | **dropped** | — | — | 0 |
| M-gem-3 | gem | **dropped** | — | — | 0 |
| M-gem-4 | gem | shipped | #82 tests: set up Log static mocking once in ConfigurationsRepositoryImplTest | 35 | 1.000 |
| M-gem-5 | gem | shipped | #83 tests: set up WorkManager static mocks once in RetryQueueWorkerTest | 35 | 1.000 |
| M-gem-6 | gem | shipped | #45 data: remove android.util.Log from DictionaryRepositoryImpl | 48 | 0.250 |
| M-gem-7 | gem | shipped | #50 data: remove android.util.Log from RetryRepositoryImpl | 47 | 0.240 |
| M-gem-8 | gem | shipped | #44 data: remove android.util.Log from DiagnosticsRepositoryImpl | 48 | 0.375 |
| M-gem-9 | gem | shipped | #40 ui: hoist ThemeManager's dialog out of the singleton service | 51 | 1.000 |
| M-gem-10 | gem | **dropped** | — | — | 0 |
| M-kim-1 | kim | shipped | #66 tests: drop Robolectric and the real 2 s sleep from RetryInterceptorTest | 43 | 1.000 |
| M-kim-2 | kim | shipped | #79 code-health: collapse TimeProvider's duplicate sleep override | 35 | 1.000 |
| M-kim-3 | kim | **dropped** | — | — | 0 |
| M-kim-4 | kim | **dropped** | — | — | 0 |
| M-kim-5 | kim | **dropped** | — | — | 0 |
| M-kim-6 | kim | shipped | #81 tests: make CourseRatingUtilsTest cheap by testing pure formatting | 35 | 1.000 |
| M-kim-7 | kim | shipped | #61 test-infra: warn on individual slow test classes in test_timing_summary.py | 44 | 1.000 |
| M-kim-8 | kim | **dropped** | — | — | 0 |
| M-kim-9 | kim | shipped | #41 core: extract Utilities' pure text helpers into an Android-free object | 50 | 1.000 |
| M-kim-10 | kim | **dropped** | — | — | 0 |
| M-grk-1 | grk | shipped | #64 ci: sparse-checkout only .github/scripts in playstore.yml | 43 | 1.000 |
| M-grk-2 | grk | shipped | #7 sync: stop retrying 4xx responses in ApiClient | 73 | 1.000 |
| M-grk-3 | grk | shipped | #26 sync: HEAD-probe and always close connections in ServerUrlMapper.isUrlDirectlyReachable | 54 | 0.625 |
| M-grk-4 | grk | shipped | #50 data: remove android.util.Log from RetryRepositoryImpl | 47 | 0.400 |
| M-grk-5 | grk | shipped | #45 data: remove android.util.Log from DictionaryRepositoryImpl | 48 | 0.417 |
| M-grk-6 | grk | shipped | #51 data: strip android.util.Log from GsonUtils | 46 | 1.000 |
| M-grk-7 | grk | shipped | #118 retry: make the RetryInterceptor retry-safety check and broadcast extras cheaper | 26 | 1.000 |
| M-grk-8 | grk | shipped | #95 login: return early from LoginSyncManager.isManager | 32 | 1.000 |
| M-grk-9 | grk | shipped | #36 calendar: index meetups by local date once per emission (in CalendarViewModel) | 51 | 0.321 |
| M-grk-10 | grk | shipped | #37 chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default | 51 | 0.258 |

## Dropped (36) and why

- `P-cdx-4` (codex sol 5.6): *dedupe NotificationDao ids before chunking*. No duplicate source: the repository API takes Set<String> (markNotificationsAsRead/deleteNotifications) and getByIds is fed parsed sync ids; nothing to dedupe.
- `P-cdx-5` (codex sol 5.6): *StorageCategoryViewModel: update one row instead of recounting*. toggleItemChecked already does one pass; the O(n) list copy stays either way, so there's no gain.
- `P-cdx-6` (codex sol 5.6): *dedupe AnswerDao submission ids*. Every caller passes primary-key lists (rows.map { it.id }, submissions.map { it.id }), so the ids are already distinct.
- `P-cdx-7` (codex sol 5.6): *dedupe RemovedLogDao doc ids*. Callers pass idsToDelete.toList() (a Set) or explicit resource lists, so there are no duplicates to remove.
- `P-cdx-8` (codex sol 5.6): *dedupe MyLibraryDao resource ids*. Callers pass resourceIds.toList() of a Set and grouped.keys.toList(), so the ids are already distinct.
- `P-cdx-9` (codex sol 5.6): *ApkLogDao: reuse a distinct id set*. Each uploaded log produces one update, so the ids are distinct. The claimed 'rebuilding equivalent sets' isn't a measurable cost.
- `P-cdx-10` (codex sol 5.6): *ExamAnswerUtils: linear-time multi-select compare*. Choice lists have single-digit length, and a frequency HashMap costs at least as much as sorting four strings. The claimed O(n log n) win doesn't exist at these sizes.
- `P-gem-1` (copilot gemini 3.8 flash): *batch dictionary seeding (chunk 500) + cancel previous word search*. Room's @Insert(List) binds one row per statement, so the 999-variable limit doesn't apply. searchWord runs on a button click, not per keystroke.
- `P-gem-7` (copilot gemini 3.8 flash): *AndroidDecrypter: cache SecureRandom, drop 'redundant' arrays*. The fixed-size copy pads or truncates to the AES key/IV length and handles null, so it isn't redundant and removing it can change output. generateIv only runs for rare id/IV creation.
- `P-gem-8` (copilot gemini 3.8 flash): *TTSManager: markdown fast path + StringBuilder CSV*. The fast-path test misses -, +, _ and '1.' markers that the regexes strip, so it changes the output. The function runs once per Read Aloud tap, so it isn't a hotspot.
- `P-kim-10` (copilot kimi k3): *InlineResourceAdapter: skip redundant status resets*. The skip only fires when the same holder rebinds the same item. The spec itself clears recycled holders, and PAYLOAD_STATUS has to re-render. No redundant work remains to remove.
- `P-grk-2` (copilot grok 4.6): *ImageViewerUtils: decode fullscreen images at screen size*. Glide's into(ImageView) already downsamples to the view's measured size, which is fullscreen here.
- `P-grk-4` (copilot grok 4.6): *ResourcesPreviewLoader: buffer CSV reads*. OpenCSV's CSVReader already wraps its Reader in a BufferedReader, and only 5 rows are read.
- `R-dev-6` (devin swe 2): *NotificationsRepositoryImpl: dedupe resolveType + batch join-request fallback*. getJoinRequestDetails(null) runs once per page (details[""]), not once per notification. The two resolveType branches use different keywords and defaults, so they aren't duplicates.
- `R-cdx-8` (codex sol 5.6): *checkConfigurationUrl: use the injected AppVersionProvider*. False premise: ConfigurationsRepositoryImpl doesn't inject AppVersionProvider, so there are no 'two version sources'.
- `R-cdx-9` (codex sol 5.6): *LifeCache: bound and invalidate the memory cache*. write() updates memory and prefs together, so entries don't go stale. About 7 items per user on a device with a few users is not a memory hotspot.
- `R-gem-1` (copilot gemini 3.8 flash): *DictionaryActivity: move URL resolution into the repository*. The activity uses Constants.DICTIONARY_URL. The cited "${UrlUtils.getUrl()}/dictionary" doesn't exist.
- `R-gem-2` (copilot gemini 3.8 flash): *EnterprisesRepositoryImpl.saveReport typed update*. There's no saveReport and no balance/totalMembers/revenue anywhere in EnterprisesRepositoryImpl.
- `R-gem-3` (copilot gemini 3.8 flash): *MyLifeDao.observeAll + reactive LifeViewModel*. MyLifeDao has no getAll(), and LifeViewModel has no _lifeList/loadLifeList. The proposed unfiltered SELECT * FROM my_life is also wrong, because the list is per user.
- `R-kim-3b` (copilot kimi k3): *ProcessUserDataActivity: drop redundant repository fields*. The subclass SyncActivity calls userRepository/syncRepository directly (authenticateUser, hasAtLeastOneUser, uploadLoginData), so the fields aren't redundant.
- `R-kim-5` (copilot kimi k3): *prune dead dictionary methods*. Nothing is dead: DictionaryDao's 3 methods, the repository's 3 and the mapper's 1 all have callers.
- `R-kim-7` (copilot kimi k3): *route DictionaryActivity data access through its ViewModel*. DictionaryActivity has no DAO or repository references.
- `R-kim-8b` (copilot kimi k3): *TeamsVoicesViewModel: narrow one single-method dependency*. It injects 6 repositories, not 8, the spec names no concrete target, and the file is owned by open PR #10993.
- `R-kim-10` (copilot kimi k3): *SyncConfigurationCoordinator failure-branch tests*. SyncConfigurationCoordinatorTest (10 tests) already covers the Failure and alternative-URL branches, and getMinApk catches internally, so there's no exception branch to test.
- `R-grk-10` (copilot grok 4.6): *LeadersViewModel: drop the Default-dispatcher hop*. getCommunityLeaders() is a non-suspend SharedPreferences read plus a JSON parse, so the hop is load-bearing. Removing it moves that work onto main.
- `M-dev-5` (devin swe 2): *printStackTrace -> Log in models and base fragments*. Android routes System.err to logcat, so nothing is lost. Putting android.util.Log into model/ adds Android coupling, the opposite of the stated roadmap-9 rationale.
- `M-dev-6` (devin swe 2): *printStackTrace -> Log in the UI layer*. Android routes System.err to logcat; the change is churn with no diagnostic gain.
- `M-cdx-5` (codex sol 5.6): *ExamAnswerUtils: linear-time multi-select compare (repeat of P-cdx-10)*. Same as P-cdx-10.
- `M-gem-2` (copilot gemini 3.8 flash): *playstore-quota.sh: fetch only needed release fields*. history_limit_events doesn't exist: save_epochs uses per_page=100, and jq already selects published_at. gh --jq filters client-side, so the payload doesn't shrink.
- `M-gem-3` (copilot gemini 3.8 flash): *test_timing_summary.py: sort and truncate the slow-class report*. The script already sorts classes descending (:98) and prints % of total plus top-N tables.
- `M-gem-10` (copilot gemini 3.8 flash): *EnterprisesRepositoryImpl: wrap DAO work in withContext*. Room suspend DAOs already run off main (the project convention in CLAUDE.md). The wrappers would add redundant hops, the opposite of grok's R-7/R-8.
- `M-kim-3` (copilot kimi k3): *hoist the Gson singleton out of model toGson() (A)*. The models construct no Gson(). The cited lines are .toGson() calls to the kotlinx->Gson tree bridge.
- `M-kim-4` (copilot kimi k3): *hoist the Gson singleton out of model toGson() (B)*. Same as M-kim-3.
- `M-kim-5` (copilot kimi k3): *UploadManagerTest runtime triage*. The static mocks are already set up once in @BeforeClass/@AfterClass, and the file is owned by open PR #17694.
- `M-kim-8` (copilot kimi k3): *NetworkUtils: lazy init for isNetworkConnectedFlow*. Every member is already a lazy ResettableCache, and getDeviceName only reads Build.*. No Hilt resolution happens on that path.
- `M-kim-10` (copilot kimi k3): *TimeUtils formatter cache audit (fallback: DownloadUtilsTest)*. TimeUtils already caches through formatterFor, and the file is owned by open PR #15951. The fallback's DownloadUtilsTest legitimately needs Robolectric (Context, prefs, service start).

## Counts and checks

- Lists: **18** (6 agents × 3 prompts), as expected.
- Raw tasks: **183** (17 lists × 10 + kimi's repository list with 10 + 3 alternatives). The brief expected 180+, so this matches.
- Dropped: **36**. Raw tasks surviving verification: **147**.
- Merged and shipped: **118** tasks. 102 came from a single raw task; 16 merged 45 raw tasks.
- Point-sum check: Σ completeness points = **118.0000**, Σ 1/n points = **118.0000**, number of shipped merged tasks = **118**. Equal, as required. 147 + 36 = 183 = raw total.

## Judgment calls and guesses

- Completeness weights and the E/I/F scores are my calls from reading the code. Nothing was profiled on a device, and I didn't run the Gradle suite, so test-speed claims (M-gem-4/5, M-kim-1/6) and claude's KSP-warning citation (M-cld-1) are unmeasured.
- The 'can't deliver at real sizes' drop rule decided P-cdx-5, P-cdx-10/M-cdx-5 and P-kim-10. Negligible-but-real wins (pager set lookups, FreeSpaceWorker copy, Triple list) ship with low ratings instead.
- When a premise only partly held, the task shipped at reduced completeness with a *watch out* note rather than being dropped: P-gem-3, P-gem-5, P-gem-6, P-gem-9, P-gem-10, R-gem-8, R-gem-9, R-grk-6, M-dev-1, M-dev-4, M-grk-5, M-grk-10, M-kim-6.
- kimi's three alternatives count as submissions (33 total). If you'd rather count one per pair, its per-submitted figures rise to 18.43/30 = 0.614 and 7.70/30 = 0.257.
- When an agent proposed the same task twice (codex: getMinApk first-success P-1/M-8, ExamAnswerUtils P-10/M-5, ChatSearch P-2/M-4; grok: ChatSearch P-8/M-10), it gets credit once. Both submissions still count in the denominator.
- Open-PR ownership is each PR head diffed against its merge-base. Paths that old PRs touched but that have since been renamed or deleted were ignored.
- Agent names are taken verbatim from your list headers.

## Process notes (not scored)

- gemini's repository list cites PRs #15807, #15799 … #15778, which aren't among the 33 open PRs. Its micro list attaches invented titles to real PR numbers (e.g. #17680 'bump core-ktx'; the real PR is a TeamResourcesAdapterTest fix). Its perf list marks ChatDetailFragment, LoginActivity and SyncManager as PR-owned, but no open PR touches them.
- kimi's perf list says 34 open PRs; there are 33. Five of its repository tasks need PR-owned files (it flags this itself).
- claude's lists had zero drops. devin's and grok's misses were narrow (one wrong premise each, plus devin's printStackTrace pair).
