# Refactor-task scoring

## Method

Each shipped task is rated from 1–100 as **40% evidence quality, 35% expected impact, and 25% risk-adjusted feasibility**. “Evidence” rewards a concrete, working-tree-confirmed premise; “impact” covers runtime, reliability, architecture, or regression-prevention value; “feasibility” rewards bounded, behavior-preserving work with a credible test seam.

Agent credit follows the requested rule: a unique good task earns 1 point, a bad task earns 0, and each merged duplicate is worth exactly 1 point total. Duplicate shares are assigned by comparative completeness rather than list order. “Normalized” is points divided by 20 raw submissions. “Quality-weighted” is the credited rating sum divided by the maximum 2,000 rating-points available to 20 submissions.

## Agent scores

| Agent | Raw | Good premises | Bad | Credited points | Normalized | Credited rating sum | Quality-weighted |
|---|---:|---:|---:|---:|---:|---:|---:|
| Claude Opus 5.5 | 20 | 20 | 0 | 17.65 | 88.25% | 1521.95 | 76.10% |
| Codex Sol 5.6 | 20 | 20 | 0 | 20.00 | 100.00% | 1238.00 | 61.90% |
| Devin SWE 2 | 20 | 20 | 0 | 18.35 | 91.75% | 1412.40 | 70.62% |
| Copilot Kimi K3 | 20 | 15 | 5 | 14.35 | 71.75% | 1060.25 | 53.01% |
| Copilot Grok 4.5 | 20 | 20 | 0 | 18.35 | 91.75% | 1490.80 | 74.54% |
| Copilot Gemini 3.5 Flash | 20 | 20 | 0 | 19.30 | 96.50% | 1118.60 | 55.93% |

## Duplicate-credit allocation

| Merged task | Rating | Proposals | Completeness credit |
|---|---:|---|---|
| Bound text-viewer reads and parse Markdown off the main thread | 95 | Claude Opus 5.5 performance #6<br>Copilot Kimi K3 performance #3 | Claude Opus 5.5 0.65<br>Copilot Kimi K3 0.35 |
| Cache foreground-service eligibility during each download run | 84 | Claude Opus 5.5 performance #9<br>Copilot Grok 4.5 performance #8 | Claude Opus 5.5 0.55<br>Copilot Grok 4.5 0.45 |
| Route LoginActivity's repository calls through LoginViewModel | 92 | Claude Opus 5.5 boundaries #5<br>Devin SWE 2 boundaries #9 | Claude Opus 5.5 0.45<br>Devin SWE 2 0.55 |
| Hide TeamTaskDao behind a notification task-lookup boundary | 90 | Devin SWE 2 boundaries #1<br>Copilot Grok 4.5 boundaries #6 | Devin SWE 2 0.40<br>Copilot Grok 4.5 0.60 |
| Return typed results from personal-resource uploads | 92 | Copilot Grok 4.5 boundaries #4<br>Copilot Gemini 3.5 Flash boundaries #2 | Copilot Grok 4.5 0.70<br>Copilot Gemini 3.5 Flash 0.30 |
| Hide Room projections and removed-log persistence behind resource boundaries | 91 | Claude Opus 5.5 boundaries #7<br>Copilot Grok 4.5 boundaries #1 | Claude Opus 5.5 0.40<br>Copilot Grok 4.5 0.60 |
| Chunk unbounded NotificationDao IN-list reads | 92 | Claude Opus 5.5 boundaries #1<br>Devin SWE 2 boundaries #7 | Claude Opus 5.5 0.60<br>Devin SWE 2 0.40 |

## Dropped submissions

### Copilot Kimi K3 — performance task 10: Test coverage for `CrashLogStore` parse-and-load contract

The claimed missing parse/load contract is already covered by `CrashLogStoreTest`: valid logs, custom types, malformed files, empty results, retention limits, and deletion are present. A narrower unreadable-file case does not justify a standalone backlog item.

### Copilot Kimi K3 — boundaries task 5: Consolidate duplicated `OfflineActivityDao` count queries (DAO optimization)

The two count methods filter different columns (`userId` versus `userName`). Combining them behind an optional identifier would blur semantics, while the proposed nullable-OR MAX query is less index-friendly rather than an optimization.

### Copilot Kimi K3 — boundaries task 6: Add typed projection to a hot `MeetupDao` read used by `CommunityRepositoryImpl` (DAO optimization)

No cited hot projection exists: `CommunityRepositoryImpl` uses `MeetupDao.getByMeetupIds` to reconstruct full `Meetup` objects for sync and does not immediately reduce rows to ids/titles. The task says to audit for a target instead of identifying one.

### Copilot Kimi K3 — boundaries task 8: Push dictionary search/count composition fully behind `DictionaryRepository`

The ViewModel already delegates count and lookup to `DictionaryRepository`; it contains no raw-result filtering or sorting to push down. The task is conditional (“inspect … for any”) and its premise does not hold.

### Copilot Kimi K3 — boundaries task 10: Give `ReplyActivity` a dedicated `ReplyViewModel` (close the last repo-injecting activity)

The proposed ViewModel would expose a repository/edit-actions implementation and invoke `VoicesActions`, an Android dialog helper. That relocates UI coupling rather than creating a sound ViewModel boundary.

## Per-task scoring ledger

| Rating | Shipped task | Provenance |
|---:|---|---|
| 95 | Bound text-viewer reads and parse Markdown off the main thread | Claude Opus 5.5 performance #6; Copilot Kimi K3 performance #3 |
| 94 | stop reading profile-image URLs as local files in UserEntity.serialize | Claude Opus 5.5 performance #1 |
| 92 | Chunk unbounded NotificationDao IN-list reads | Claude Opus 5.5 boundaries #1; Devin SWE 2 boundaries #7 |
| 92 | Return typed results from personal-resource uploads | Copilot Grok 4.5 boundaries #4; Copilot Gemini 3.5 Flash boundaries #2 |
| 92 | Route LoginActivity's repository calls through LoginViewModel | Claude Opus 5.5 boundaries #5; Devin SWE 2 boundaries #9 |
| 91 | chunk the IN-clause reads in TeamLogDao | Devin SWE 2 boundaries #6 |
| 91 | Hide Room projections and removed-log persistence behind resource boundaries | Claude Opus 5.5 boundaries #7; Copilot Grok 4.5 boundaries #1 |
| 91 | persist the DownloadService queue in batches instead of after every file | Claude Opus 5.5 performance #2 |
| 90 | Hide TeamTaskDao behind a notification task-lookup boundary | Devin SWE 2 boundaries #1; Copilot Grok 4.5 boundaries #6 |
| 90 | Introduce `ResourceDetailViewModel` (data access out of fragment) | Copilot Kimi K3 boundaries #1 |
| 90 | Make retry queue state derive from the DAO instead of mutable process flags | Codex Sol 5.6 boundaries #5 |
| 90 | Resource sync pagination without CouchDB `skip` | Copilot Grok 4.5 performance #10 |
| 90 | strip UserRepositoryImpl's foreign DAOs behind the resources and events boundaries | Devin SWE 2 boundaries #3 |
| 89 | batch the HTML-resource reconcile pass in ResourcesRepositoryImpl | Claude Opus 5.5 performance #3 |
| 89 | route ProgressRepositoryImpl and CoursesRepositoryImpl submissions-domain access through SubmissionsRepository | Devin SWE 2 boundaries #5 |
| 89 | Upload: make `UploadRepository` network-only; finish Room upload configs for exams/submissions | Copilot Grok 4.5 boundaries #8 |
| 88 | batch member-visit and membership writes in TeamsRepositoryImpl | Devin SWE 2 performance #1 |
| 88 | Introduce `BecomeMemberViewModel` (form data access out of activity) | Copilot Kimi K3 boundaries #2 |
| 88 | Life repository: require caller `userId`, drop `SharedPrefManager` | Copilot Grok 4.5 boundaries #2 |
| 88 | move notification formatting and grouping off the main thread | Claude Opus 5.5 performance #7 |
| 88 | move ProcessUserDataActivity's sync/upload calls into a new SyncViewModel | Devin SWE 2 boundaries #10 |
| 88 | replace enterprise report read-modify-REPLACE with single UPDATE queries | Claude Opus 5.5 boundaries #8 |
| 87 | Move multi-table sync filtering behind `RealtimeSyncManager` | Codex Sol 5.6 boundaries #2 |
| 87 | move the retry find-or-enqueue decision from RetryQueue into RetryRepository | Claude Opus 5.5 boundaries #2 |
| 87 | stop deep-copying the whole exam (questions included) in StepExam.insertCourseStepsExams | Claude Opus 5.5 performance #8 |
| 86 | Avoid full-library title map on offline storage listing | Copilot Grok 4.5 performance #5 |
| 86 | delete the duplicated meetups sync write by reusing EventsSyncWriter.batchInsertMeetups | Devin SWE 2 boundaries #4 |
| 86 | stop the chat history from reloading on every return to STARTED | Claude Opus 5.5 performance #5 |
| 85 | move shelf discovery out of SyncManager into SyncRepository | Claude Opus 5.5 boundaries #3 |
| 85 | parse retry payloads straight to kotlinx instead of Gson → kotlinx | Claude Opus 5.5 performance #10 |
| 85 | Split resource-upload serialization out of `ResourcesRepository` (tighten the 79-method interface) | Copilot Kimi K3 boundaries #4 |
| 85 | Thread-safe PDF thumbnail cache with eviction recycle | Copilot Grok 4.5 performance #3 |
| 84 | Cache foreground-service eligibility during each download run | Claude Opus 5.5 performance #9; Copilot Grok 4.5 performance #8 |
| 84 | Diagnostics: drop `SharedPrefManager`; resolve planet codes via existing repositories | Copilot Grok 4.5 boundaries #3 |
| 84 | Downscale first-page PDF preview in resource viewer | Copilot Grok 4.5 performance #4 |
| 84 | Hide `SharedPreferences` behind the Life cache boundary | Codex Sol 5.6 boundaries #3 |
| 84 | let ChatViewModel resolve community config instead of ChatHistoryFragment | Claude Opus 5.5 boundaries #6 |
| 84 | Replace `CourseProgressDao`'s hand-built dynamic query with a parameterized one | Copilot Kimi K3 boundaries #9 |
| 84 | stop rebuilding the legacy EncryptedSharedPreferences on every credential call | Claude Opus 5.5 performance #4 |
| 83 | Cap per-file memory in `CrashLogStore.loadPendingLogs` | Copilot Kimi K3 performance #4 |
| 83 | Chat list append/animation without position-map thrash | Copilot Grok 4.5 performance #9 |
| 83 | Make chunked answer DAO operations stable and no-op on empty IDs | Codex Sol 5.6 boundaries #10 |
| 83 | Move remaining data calls in `AchievementFragment` into `AchievementViewModel` | Copilot Kimi K3 boundaries #3 |
| 82 | Enterprises reports: stop UI depending on raw `MyTeam` report rows | Copilot Grok 4.5 boundaries #5 |
| 82 | move the community-leaders fetch from LoginSyncManager into ConfigurationsRepository | Claude Opus 5.5 boundaries #4 |
| 82 | Normalize shelf cache values inside `SyncRepositoryImpl` | Codex Sol 5.6 boundaries #6 |
| 82 | route ActivitiesRepositoryImpl's user lookup through UserRepository | Devin SWE 2 boundaries #2 |
| 81 | Add a paged top-level query to `NewsDao` for the voices feed (DAO optimization) | Copilot Kimi K3 boundaries #7 |
| 81 | Parallelize the two independent batch lookups in `NotificationsRepositoryImpl.getJoinRequestDetailsBatch` | Copilot Kimi K3 performance #8 |
| 80 | Activities chart: move monthly aggregation out of the Fragment | Copilot Grok 4.5 boundaries #10 |
| 80 | make the My Life visibility toggle re-read the row it updated | Claude Opus 5.5 boundaries #9 |
| 79 | Create a database unit test suite for `RemovedLogDao` | Copilot Gemini 3.5 Flash boundaries #9 |
| 79 | Cut LifeCache read copies and blocking preference writes | Copilot Grok 4.5 performance #6 |
| 79 | Hoist course resource download selection out of the ViewModel | Codex Sol 5.6 boundaries #8 |
| 78 | Migrate `DictionaryRepository` and `DictionaryMapper` parsing to Kotlin Serialization | Copilot Gemini 3.5 Flash boundaries #5 |
| 78 | precompute team-match patterns outside the row loop in countTopLevelByTeams | Devin SWE 2 performance #9 |
| 78 | Replace the notifications repository’s cross-feature `News` list with a count | Codex Sol 5.6 boundaries #1 |
| 77 | Deduplicate upload-success identifiers before DAO updates | Codex Sol 5.6 boundaries #9 |
| 76 | Decouple `MyLife.kt` model from Android R resource strings | Copilot Gemini 3.5 Flash boundaries #1 |
| 76 | Make personal-resource timestamps deterministic at the repository boundary | Codex Sol 5.6 boundaries #4 |
| 76 | Settings clear-data: one repository orchestration entry | Copilot Grok 4.5 boundaries #7 |
| 75 | precompute the day-to-meetups index in CalendarFragment | Devin SWE 2 performance #2 |
| 74 | Consolidate `LifeCache` serialization from Gson to Kotlin Serialization | Copilot Gemini 3.5 Flash boundaries #4 |
| 74 | Test coverage for `NotificationsRepositoryImpl.getJoinRequestDetailsBatch` batching behavior | Copilot Kimi K3 performance #9 |
| 73 | Cap Glide decode size in finance row thumbnails | Copilot Grok 4.5 performance #1 |
| 73 | remove dead MyLibraryDao.getAll and give the test a count query | Devin SWE 2 boundaries #8 |
| 72 | Cap Glide decode size in report row thumbnails | Copilot Grok 4.5 performance #2 |
| 72 | Create a database unit test suite for `DictionaryDao` | Copilot Gemini 3.5 Flash boundaries #8 |
| 72 | drop redundant list copies and hoist a loop-invariant split in repository impls | Devin SWE 2 performance #4 |
| 71 | drop the dead pending-upload surface from PersonalsRepository | Claude Opus 5.5 boundaries #10 |
| 70 | Decouple `ConfigurationsRepository` from Android Context and R resources | Copilot Gemini 3.5 Flash boundaries #3 |
| 69 | lowercase correct choices once at write time, not per comparison | Devin SWE 2 performance #5 |
| 68 | Faster notification type resolution | Copilot Grok 4.5 performance #7 |
| 68 | Stop diagnostics persistence from printing exceptions across its boundary | Codex Sol 5.6 boundaries #7 |
| 67 | Optimize MimeType Resolution Performance and Robustness | Copilot Gemini 3.5 Flash performance #1 |
| 66 | buffer the asset stream and compile the credential regex once | Devin SWE 2 performance #10 |
| 64 | Create a comprehensive unit test suite for `DeviceNameProvider` | Copilot Gemini 3.5 Flash boundaries #10 |
| 63 | hoist styling lookups out of chat UI bind paths | Devin SWE 2 performance #6 |
| 62 | View Binding for `OnboardingAdapter` | Copilot Kimi K3 performance #7 |
| 61 | hoist getString/getColor calls out of member and user adapters | Devin SWE 2 performance #8 |
| 61 | Retry repository: remove `android.util.Log` boundary noise | Copilot Grok 4.5 boundaries #9 |
| 60 | replace the per-close-click label scan in VoicesLabelManager | Devin SWE 2 performance #3 |
| 58 | hoist getString calls out of finance, life, and exam adapters | Devin SWE 2 performance #7 |
| 57 | Flatten `item_library_grid.xml` badge FrameLayout chain | Copilot Kimi K3 performance #2 |
| 57 | Optimize `MyLifeDao` query complexity by normalizing userId in Repository | Copilot Gemini 3.5 Flash boundaries #6 |
| 55 | Flatten `row_survey.xml` list-row hierarchy | Copilot Kimi K3 performance #1 |
| 55 | Optimize `PersonalDao` query complexity by normalizing userId in Repository | Copilot Gemini 3.5 Flash boundaries #7 |
| 52 | `setHasFixedSize(true)` on stable list RecyclerViews, batch A | Copilot Kimi K3 performance #5 |
| 52 | `setHasFixedSize(true)` on stable list RecyclerViews, batch B | Copilot Kimi K3 performance #6 |
| 50 | Run the course DAO tests in the shared default Robolectric sandbox | Codex Sol 5.6 performance #10 |
| 50 | Run the exam DAO tests in the shared default Robolectric sandbox | Codex Sol 5.6 performance #9 |
| 48 | Eliminate On-Scroll Click Listener Allocations in ChatAdapter | Copilot Gemini 3.5 Flash performance #3 |
| 48 | Eliminate On-Scroll Click Listener Allocations in LifeAdapter | Copilot Gemini 3.5 Flash performance #8 |
| 46 | Eliminate Allocation Churn in HealthUsersAdapter View Binding | Copilot Gemini 3.5 Flash performance #6 |
| 46 | Optimize Interaction Handlers in SubmissionsAdapter | Copilot Gemini 3.5 Flash performance #9 |
| 45 | Eliminate On-Scroll Click Listener Allocations in UserArrayAdapter | Copilot Gemini 3.5 Flash performance #2 |
| 44 | Eliminate On-Scroll Click Listener Allocations in ServerAddressAdapter | Copilot Gemini 3.5 Flash performance #5 |
| 44 | Eliminate On-Scroll Click Listener Allocations in UsersAdapter | Copilot Gemini 3.5 Flash performance #10 |
| 44 | Run the enterprise report fragment tests in the shared default Robolectric sandbox | Codex Sol 5.6 performance #8 |
| 43 | Eliminate On-Scroll Click Listener Allocations in CheckboxAdapter | Copilot Gemini 3.5 Flash performance #4 |
| 43 | Run the resources adapter tests in the shared default Robolectric sandbox | Codex Sol 5.6 performance #7 |
| 43 | Run the voices actions tests in the shared default Robolectric sandbox | Codex Sol 5.6 performance #4 |
| 42 | Run the life adapter tests in the shared default Robolectric sandbox | Codex Sol 5.6 performance #1 |
| 42 | Run the server address adapter tests in the shared default Robolectric sandbox | Codex Sol 5.6 performance #2 |
| 41 | Run the chat adapter clipboard tests in the shared default Robolectric sandbox | Codex Sol 5.6 performance #5 |
| 40 | Run the personals adapter cache test in the shared default Robolectric sandbox | Codex Sol 5.6 performance #3 |
| 39 | Run the resources filter dialog smoke test in the shared default Robolectric sandbox | Codex Sol 5.6 performance #6 |
| 35 | Convert Sha256Utils Utility Class to Kotlin Object Singleton | Copilot Gemini 3.5 Flash performance #7 |

## Counts and checks

- **Lists received:** 12 (expected 12).
- **Raw tasks:** 120 (expected at least 120; exactly 10 in each list).
- **Merged duplicate sets:** 7 pairs, reducing 120 raw submissions to 113 candidate tasks.
- **Dropped after working-tree verification:** 5.
- **Shipped:** 108 merged backlog tasks.
- **Point-sum check:** 17.65 + 20.00 + 18.35 + 14.35 + 18.35 + 19.30 = **108.00**, exactly one point per shipped task.
- **Guesses:** “Shipped” was interpreted as included in the merged backlog, not implemented code. Near-overlaps that solve different problems in the same file were retained separately. Agent labels repeated across two URLs were treated as one benchmark participant. Ratings necessarily estimate user impact because no device benchmark or production trace was supplied; evidence scores rely on direct source/test inspection.
