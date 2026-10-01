# myPlanet refactor mega plan

This plan merges the three consolidated plans, each built from 18 agent lists (6 agents × 3 prompts × 10 tasks):

| Source | Tasks | Used here as |
|---|---|---|
| Claude Opus 5.5 — `claude/happy-heisenberg-pzcy9h:docs/refactor_tasks.md` | 118 | **Base.** Every card keeps its verified line refs, steps, and warnings |
| Devin SWE 2 — `devin/1790861469-refactor-task-consolidation:docs/refactor_tasks.md` | 115 | Cross-check rating; source of tasks N1–N7 and the AppVersionProvider/LeadersViewModel merges |
| Codex Sol 5.6 — `codex/generate-task-lists-and-counts:refactor_tasks.md` | 124 | Cross-check rating only (its task bodies are boilerplate) |

**124 tasks** in 8 waves, plus **19** rejected proposals at the end. Waves run in order; within a wave, work top-down. Tasks in different waves may run in parallel when the **sequence** lines don't say otherwise.

**Rating** = 3·evidence + 5·impact + 2·feasibility (each 1–10, max 100), recomputed for every card so the numbers are comparable. **Plans** shows how each source plan rated the same task (— = not in that plan). **Ids**: `M#` is this plan's order; `C#` is the task's number in the Claude plan; `N#` is new here.

**Verified against** `master` @ `286e66e` (2026-10-01): every disputed claim and every N-card was re-checked on this commit; the other line refs come from the Claude plan. All open PRs it cites (#10993, #13657, #14427, #15808, #15825, #17187, #17776) are still open.

Repo rules that apply to every task: MockK + `runTest` + `MainDispatcherRule`/`TestDispatcherProvider`; inject `DispatcherProvider`, never `Dispatchers.*`; DAO `IS` for nullable params; no `RepositoryModule` edits while #15808 is open (use a new module, as the cards say); PR titles `scope: smoother thing doing (fixes #N)`; run `./gradlew testDefaultDebugUnitTest` (plus `assembleLiteDebug` for flavor or UI changes).

## Start here

1. **Wave 1 (M1–M6) first.** These are the only tasks that fix behaviour users can hit today: a Room crash past 999 ids, a server dialog that can never open, lost mark-as-read and finance-report writes, users thrown to the home screen, and an ANR detector that can't be trusted. All six are ready, touch different files, and can run in parallel.
2. **Then M7–M13**, the biggest latency wins: a wasted alternative-server timeout on every reachability check, a full PDF text strip on every open, an O(n²) typing animation, 4xx retries, and the slowest-probe wait. Do M9 and M14 together so the chat and voices typing animations share one chunked helper.
3. **Use the `sequence` lines before you branch.** 23 file groups have more than one task; land each group in the order shown, or rebase.
4. **Skip Waves 7–8 until unblocked.** Wave 7 needs a product or data answer first; Wave 8 waits on open PRs.
5. **Never implement the rejected list.** The two most dangerous items are near the top of the Devin and Codex plans.

## Index

| M | Task | Rating | Plans C · D · X | Wave | Status |
|---|---|---|---|---|---|
| M1 | data: chunk the remaining unbounded IN-list DAO queries and delete dead DAO methods | 75 | 75 · 84 · 89 | 1 | ready |
| M2 | sync: replace localized strings in the version-check callback with a typed error (fixes the never-opening server dialog) | 74 | 73 · 73 · 68 | 1 | ready |
| M3 | notifications: make sync merge and read/delete check-then-act atomic in NotificationDao | 71 | 70 · 71 · 84 | 1 | ready |
| M4 | enterprises: replace the finance-report read-modify-upsert with one targeted UPDATE | 68 | 67 · 77 · 71 | 1 | ready |
| M5 | audio: stop sending the user to the home screen when a recording is stopped too early | 60 | 60 · 74 · 69 | 1 | ready |
| M6 | anr: make ANRWatchdog's cross-thread state @Volatile and measure the ANR duration after the wait | 55 | 45+43 · 83 · 69 | 1 | ready |
| M7 | sync: skip the alternative-server probe when the primary already answered | 83 | 83 · 76 · 70 | 2 | ready |
| M8 | viewer: extract PDF text only when Read Aloud is tapped, and drop the duplicate audio Player.Listener | 81 | 81 · 77 · 69 | 2 | ready |
| M9 | chat: chunk the AI typing animation and stop scrolling the list on every character | 73 | 73 · 87 · 70 | 2 | ready |
| M10 | download: cap PDF thumbnail size and keep the cache key's stat calls off the main thread | 73 | 73 · 78 · 69 | 2 | ready |
| M11 | sync: stop retrying 4xx responses in ApiClient | 73 | 73 · 78 · 68 | 2 | ready |
| M12 | perf: drop the double map lookup and per-call lambda allocation in GsonUtils field getters | 70 | 69 · 79 · 68 | 2 | ready |
| M13 | sync: return the first successful configuration probe without waiting for the slower one | 69 | 68 · 78 · 70 | 2 | ready |
| M14 | voices: chunk the typing animation in VoicesAdapterHelper | 68 | 67 · 87 · 68 | 2 | ready |
| M15 | login: stop inflating an unused server dialog and re-syncing community docs on every LoginActivity create | 68 | 67 · 73 · 69 | 2 | ready |
| M16 | startup: copy map-tile assets off the main thread at cold start | 68 | 64 · 76 · 81 | 2 | ready |
| M17 | dashboard: skip the no-op notification upsert on every dashboard load | 65 | 62 · 75 · 68 | 2 | ready |
| M18 | enterprises: read attachment images off the main thread when saving a transaction or report | 63 | 61 · 84 · 81 | 2 | ready |
| M19 | startup: cache the manifest permission set and delete the dead install-permission check | 60 | 55 · 76 · 73 | 2 | ready |
| M20 | reachability: cache primary-server probes in ServerReachabilityProvider | 58 | 57 · 74 · 84 | 2 | ready |
| M21 | sync: HEAD-probe and always close connections in ServerUrlMapper.isUrlDirectlyReachable | 56 | 54 · 77 · 68 | 2 | ready |
| M22 | calendar: index meetups by local date once per emission (in CalendarViewModel) | 55 | 51 · 79 · 71 | 2 | ready |
| M23 | chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default | 55 | 51 · 84 · 73 | 2 | ready |
| M24 | dictionary: drop the duplicate COUNT on DictionaryActivity open | 55 | 46 · 77 · 69 | 2 | ready |
| M25 | upload: suppress duplicate SyncUiState emissions from the user-data upload scheduler | 52 | 44 · 75 · 68 | 2 | ready |
| M26 | upload: stop recounting the whole download queue three times per file | 50 | 48 · 73 · 68 | 2 | ready |
| M27 | notifications: remove the fixed 200 ms sleep before read-receipt broadcasts | 48 | 41 · 75 · 68 | 2 | ready |
| M28 | health: move key generation, encryption and entity building out of HealthExaminationActivity | 64 | 62 · 73 · 69 | 3 | ready |
| M29 | courses: delete the CourseDetailProvider/RatingSummaryProvider passthroughs | 63 | 57 · 74 · 69 | 3 | ready |
| M30 | courses: drop Context from CourseDetailViewModel and CoursesStepsViewModel via StoragePathResolver | 60 | 55 · 70 · 68 | 3 | ready |
| M31 | data: slim the retry-queue duplicate lookup and delete dead RetryRepository/RetryDao surface | 60 | 55 · 73 · 70 | 3 | ready |
| M32 | data: move resource _all_docs fetching and doc filtering from SyncManager into SyncRepository | 59 | 56 · 73 · 69 | 3 | ready |
| M33 | courses: decide course-step downloads in ResourceDownloadCoordinator, not CoursesStepsViewModel | 58 | 53 · 70 · 68 | 3 | ready |
| M34 | courses: give the progress grid a typed row model | 58 | 53 · 73 · 68 | 3 | ready |
| M35 | health: extract examination-item mapping out of HealthExaminationAdapter | 58 | 53 · 74 · 69 | 3 | ready |
| M36 | viewer: move the playback-speed picker off material-dialogs and drop a redundant safe call | 58 | 53 · 70 · 68 | 3 | ready |
| M37 | chat: extract conversation pagination from ChatViewModel into a plain pager | 56 | 52 · 74 · 68 | 3 | ready |
| M38 | data: introduce UserLookupRepository and move single-purpose callers onto it | 56 | 52 · — · 68 | 3 | ready |
| M39 | enterprises: project finance reports and delete dead TeamDao queries | 56 | 52 · 73 · 71 | 3 | ready |
| M40 | sync: split ResourcesSyncRepository so the sync layer stops depending on the full resources surface | 56 | 52 · 64 · 69 | 3 | ready |
| M41 | ui: hoist ThemeManager's dialog out of the singleton service | 55 | 51 · 68 · 68 | 3 | ready |
| M42 | settings: hoist the guest-user check into SettingsViewModel | 55 | 46 · 70 · 68 | 3 | ready |
| M43 | surveys: move respondent JSON parsing into SurveysPublicMapper | 55 | 46 · 72 · 68 | 3 | ready |
| M44 | user: route achievement calls through UserAchievementsRepository | 55 | 46 · 74 · 69 | 3 | ready |
| M45 | chat: hoist server prefs and AI-provider fetching from the chat fragments into ChatViewModel | 54 | 52 · 70 · 68 | 3 | ready |
| M46 | core: extract Utilities' pure text helpers into an Android-free object | 53 | 50 · 68 · 68 | 3 | ready |
| M47 | dashboard: move the challenge-dialog earnings/progress logic into MarkdownViewModel | 53 | 50 · 72 · 68 | 3 | ready |
| M48 | health: load patients through UserRepository in HealthViewModel | 53 | 44 · 64 · 68 | 3 | ready |
| M49 | settings: show at most 10 retry previews plus a COUNT instead of loading every pending row | 53 | 44 · 65 · 70 | 3 | ready |
| M50 | data: compare URL schemes against literals and read the installed version through AppVersionProvider in getMinApk | 52 | 34 · 73 · 68 | 3 | ready |
| M51 | viewer: drop Context from ResourceViewerViewModel | 51 | 48 · 70 · 69 | 3 | ready |
| M52 | dictionary: move the dictionary download request out of DictionaryActivity into DictionaryViewModel | 51 | — · 70 · 68 | 3 | ready |
| M53 | notifications: read notification lists as NotificationPayload projections | 50 | 43 · 74 · 84 | 3 | ready |
| M54 | data: share submission loading and page setup in SubmissionsRepositoryExporter | 48 | 41 · 75 · 68 | 3 | ready |
| M55 | retry: let RetryQueueWorker call RetryRepository directly | 48 | 41 · 64 · 70 | 3 | ready |
| M56 | health: stop using the getHealthEntry pair in HealthExaminationViewModel | 48 | 33 · 70 · 69 | 3 | ready |
| M57 | courses: log course visits through ActivitiesRepository in TakeCourseViewModel | 47 | 32 · 68 · 69 | 3 | ready |
| M58 | notifications: build join-request details without the intermediate Triple list | 47 | 32 · 75 · 69 | 3 | ready |
| M59 | health: let HealthViewModel own its realtime-sync subscription | 45 | 40 · 68 · 68 | 3 | ready |
| M60 | life: observe MyLife rows as a Flow instead of re-fetching after every mutation | 44 | — · 66 · 68 | 3 | ready |
| M61 | notifications: replace NotificationDao.markSynced's raw CASE query with a typed partial update | 40 | 37 · 74 · 84 | 3 | ready |
| M62 | notifications: narrow NotificationsRepositoryImpl's voices/user dependencies | 40 | 34 · 68 · 68 | 3 | ready |
| M63 | data: remove android.util.Log from DiagnosticsRepositoryImpl | 60 | 48 · 79 · 79 | 4 | ready |
| M64 | data: remove android.util.Log from DictionaryRepositoryImpl | 60 | 48 · 79 · 64 | 4 | ready |
| M65 | sync: route SyncActivity's raw clock reads through the injected TimeProvider | 60 | 48 · 74 · 68 | 4 | ready |
| M66 | data: remove android.util.Log from RetryRepositoryImpl | 58 | 47 · 79 · 80 | 4 | ready |
| M67 | data: make RemovedLogDao.getRemovedDocIds return non-null ids | 57 | 47 · 74 · 60 | 4 | ready |
| M68 | data: strip android.util.Log from GsonUtils | 55 | 46 · 79 · 68 | 4 | ready |
| M69 | sync: route shelf-sync failures through SyncTimeLogger instead of android.util.Log | 55 | 46 · 79 · 69 | 4 | ready |
| M70 | download: derive the 404 diagnostic URL from the request instead of regex over response.toString() | 55 | 46 · 77 · 69 | 4 | ready |
| M71 | data: use the injected TimeProvider for the multi-submission report timestamp | 55 | 35 · 75 · 69 | 4 | ready |
| M72 | code-health: collapse TimeProvider's duplicate sleep override | 55 | 35 · 72 · 68 | 4 | ready |
| M73 | data: dedupe ids before chunking in the remaining DAO bulk wrappers | 55 | — · 80 · 68 | 4 | ready |
| M74 | code-health: replace printStackTrace() with the app's logging, package by package | 49 | — · 76 · 65 | 4 | ready |
| M75 | sync: snapshot SyncTimeLogger aggregates once per summary | 48 | 41 · 75 · 68 | 4 | ready |
| M76 | sync: drop per-event synchronizedList wrappers in SyncTimeLogger | 43 | 30 · 75 · 68 | 4 | ready |
| M77 | sync: build shelf-sync request bodies with kotlinx directly | 42 | 38 · 77 · 69 | 4 | ready |
| M78 | tests: add ReplyViewModel.getNewsWithReplies coverage | 60 | 55 · 74 · 69 | 5 | ready |
| M79 | test-infra: separate warm-up outliers from real class cost in the CI slow-test summary | 54 | 51 · 65 · 57 | 5 | ready |
| M80 | test-infra: warn on individual slow test classes in test_timing_summary.py | 52 | 44 · 65 · 57 | 5 | ready |
| M81 | tests: drop Robolectric and the real 2 s sleep from RetryInterceptorTest | 50 | 43 · 72 · 63 | 5 | ready |
| M82 | tests: clear the compiler warnings in three test files | 50 | 33 · 68 · 55 | 5 | ready |
| M83 | ci: sparse-checkout only .github/scripts in playstore.yml | 50 | 43 · 67 · 68 | 5 | ready |
| M84 | ci: read release step conclusions from the jobs listing in playstore.sh | 39 | 29 · 68 · 68 | 5 | ready |
| M85 | tests: make CourseRatingUtilsTest cheap by testing pure formatting | 37 | 35 · 71 · 63 | 5 | ready |
| M86 | tests: set up Log static mocking once in ConfigurationsRepositoryImplTest | 37 | 35 · 70 · 63 | 5 | ready |
| M87 | tests: set up WorkManager static mocks once in RetryQueueWorkerTest | 37 | 35 · 70 · 63 | 5 | ready |
| M88 | tests: measure UploadManagerTest before restructuring it | 34 | — · 62 · 63 | 5 | ready |
| M89 | code-health: remove redundant null-handling in personals, ratings and login-team code | 52 | 34 · 70 · 68 | 6 | ready |
| M90 | code-health: remove redundant null-handling on non-null ids (upload, notifications, labels) | 52 | 34 · 70 · 68 | 6 | ready |
| M91 | code-health: add a shared debounceDistinct operator and clear two FlowPreview warnings | 50 | 33 · 71 · 60 | 6 | ready |
| M92 | community: drop the redundant dispatcher hops in CommunityServicesViewModel and LeadersViewModel | 50 | 33 · 71 · 70 | 6 | ready |
| M93 | upload: deduplicate DownloadUtils' twin enqueue blocks | 50 | 33 · 74 · 68 | 6 | ready |
| M94 | health: drop dead string concatenations and reorder HealthExaminationAdapter's diff | 47 | 32 · 74 · 69 | 6 | ready |
| M95 | login: return early from LoginSyncManager.isManager | 47 | 32 · 68 · 68 | 6 | ready |
| M96 | ui: cache formatted dates and hoist listeners in SubmissionsListAdapter and FeedbackReplyAdapter | 47 | 32 · 78 · 68 | 6 | ready |
| M97 | ui: cache per-bind resource lookups in Onboarding, CoursesSteps and ChatShareTarget adapters | 47 | 32 · 78 · 73 | 6 | ready |
| M98 | ui: hoist per-bind listeners in PersonalsAdapter, StorageCategoryDetail's ResourceAdapter and ReferencesAdapter | 47 | 32 · 78 · 63 | 6 | ready |
| M99 | ui: hoist the startSurvey listener and label strings in SurveysAdapter | 47 | 32 · 78 · 62 | 6 | ready |
| M100 | life: drop the redundant IO hops in LifeViewModel | 45 | 31 · 71 · 70 | 6 | ready |
| M101 | life: hoist LifeAdapter's per-bind listeners onto the ViewHolder | 45 | 31 · 78 · 62 | 6 | ready |
| M102 | life: make LifeCache entries immutable and stop copying on every read | 45 | 31 · 72 · 82 | 6 | ready |
| M103 | ui: hoist per-bind listeners and constant styling in ResourcesTagsAdapter | 45 | 31 · 78 · 62 | 6 | ready |
| M104 | ui: hoist per-bind listeners in ChatHistory, Checkbox and TeamsSelection adapters | 45 | 31 · 78 · 62 | 6 | ready |
| M105 | ui: hoist per-bind listeners in NotificationsAdapter | 45 | 31 · 78 · 62 | 6 | ready |
| M106 | crypto: reuse one SecureRandom in AndroidDecrypter.generateIv | 43 | — · 75 · 73 | 6 | ready |
| M107 | courses: make CoursesPagerAdapter.containsItem a set lookup | 42 | 30 · 73 · 68 | 6 | ready |
| M108 | dashboard: configure the activities chart once, update data per emission | 42 | 30 · 74 · 68 | 6 | ready |
| M109 | teams: make TeamPagerAdapter.containsItem a set lookup | 42 | 30 · 73 · 68 | 6 | ready |
| M110 | tts: skip TTSManager.stripMarkdown's regex chain when the text has no markdown characters | 42 | — · 77 · 68 | 6 | ready |
| M111 | code-health: fix the dead toast catch and qualified delay in utils | 41 | 30 · 76 · 65 | 6 | ready |
| M112 | storage: pass FreeSpaceWorker's batch without a defensive toSet() copy | 40 | 29 · 72 · 68 | 6 | ready |
| M113 | data: reuse empty JsonObject/JsonArray singletons in JsonUtils | 38 | 28 · 70 · 68 | 6 | ready |
| M114 | courses: bind course covers with the ImageView as Glide's lifecycle owner | 36 | 27 · 68 · 68 | 6 | ready |
| M115 | life: replace LifeAdapter's getIdentifier lookup with a compile-time map | 36 | 27 · 66 · 68 | 6 | ready |
| M116 | retry: make the RetryInterceptor retry-safety check and broadcast extras cheaper | 34 | 26 · 68 · 68 | 6 | ready |
| M117 | sync: run AutoSyncWorker's independent uploads concurrently | 55 | 51 · 78 · 75 | 7 | decide first |
| M118 | teams: unify leader hand-off before member removal in RequestsViewModel | 52 | 49 · 66 · 68 | 7 | decide first |
| M119 | dictionary: assign deterministic dictionary ids instead of a UUID per word | 36 | 27 · 76 · 76 | 7 | decide first |
| M120 | chat: narrow ChatViewModel's voices/teams dependencies to share-only interfaces | 45 | 40 · 68 · 68 | 8 | blocked: #15808 (and the Voices/Teams Impls) |
| M121 | data: extract submission upload serialization into SubmissionsUploadSerializer | 45 | 40 · 67 · 69 | 8 | blocked: #14427, #15808 |
| M122 | di: replace NotificationUtils' hidden entry-point singleton with an injectable binding | 45 | 40 · 69 · 69 | 8 | blocked: #15825, #15808 |
| M123 | voices: move ReplyActivity's member-detail fetch into ReplyViewModel | 45 | 40 · 68 · 69 | 8 | blocked: #10993, #17776 |
| M124 | data: give CoursesRepository.getMyCoursesFlow a non-suspend signature | 45 | 37 · 72 · 68 | 8 | blocked: #13657, #15808 |

## Wave 1 — Fix real bugs first

Crash risk, lost writes, a dialog that can never open, an ANR detector that can't be trusted, users thrown to the home screen.

### M1. data: chunk the remaining unbounded IN-list DAO queries and delete dead DAO methods

**rating 75**: evidence 9 · impact 6 · feasibility 9 · `C3`  
**plans**: Claude 75 · Devin 84 · Codex 89  
**proposed by**: claude opus 5.5 `R-cld-4` (1.00), devin swe 2 `M-dev-1` (0.35)

**verified**: minSdk 26 SQLite (API<30) caps bind variables at 999. Unchunked with unchunked callers: MyLibraryDao.getByIds/getByResourceIds/getByResourceIdsNotUserPattern (data/room/dao/MyLibraryDao.kt:35-39,154-158; callers ResourcesRepositoryImpl.kt:193,198,534,594) and QuestionDao.getByIds (QuestionDao.kt:10; ProgressRepositoryImpl.kt:186 passes every answered question id). Zero callers: QuestionDao.upsertAllBlocking, CourseStepDao.upsertAllBlocking, MyLibraryDao.getByCourseId.

**do**: Rename each @Query to ...Internal and add a same-name default wrapper: empty -> emptyList(), else distinct().chunked(900).flatMap (the file's existing getByCourseIds pattern). Delete the three dead methods. Add >900-id in-memory tests for MyLibraryDao.getByIds, getByResourceIdsNotUserPattern and QuestionDao.getByIds.

**watch out**: Do NOT wrap UserDao.getUsersByAnyIds/getGuestUsersByNames (M-dev-1's other half): callers already chunk at 400/500, and getUsersByAnyIds binds the list twice, so a 900 chunk would bind 1,800 variables.


**sequence**: MyLibraryDao / QuestionDao: **M1** → M73

---

### M2. sync: replace localized strings in the version-check callback with a typed error (fixes the never-opening server dialog)

**rating 74**: evidence 10 · impact 6 · feasibility 7 · `C6`  
**plans**: Claude 73 · Devin 73 · Codex 68  
**proposed by**: claude opus 5.5 `R-cld-5` (1.00)

**verified**: ui/sync/SyncActivity.kt:767-771 opens settingDialog() only if msg.startsWith("Config"); the message is context.getString(R.string.server_url_not_configured) = "Server URL not configured" (repository/ConfigurationsRepositoryImpl.kt:105), so the dialog never opens. getPlanetType/getParentCode/getCommunityName/clearPreferences on ConfigurationsRepository have no callers outside the Impl (other hits are sharedPrefManager.*).

**do**: Add sealed VersionCheckError (NotConfigured, VersionNotFound, UpToDate, ApkNotFoundOnServer, ConnectionFailed); change CheckVersionCallback.onError to take it; map to the same R.string ids in SyncActivity and replace the startsWith check with error == NotConfigured; AutoSyncWorker only changes signature. Make the 4 dead interface methods private. Update ConfigurationsRepositoryImplTest verifications.


**sequence**: ConfigurationsRepositoryImpl + ServerUrlMapper: **M2** → M7 → M13 → M21 → M50; SyncActivity: **M2** → M65

---

### M3. notifications: make sync merge and read/delete check-then-act atomic in NotificationDao

**rating 71**: evidence 9 · impact 6 · feasibility 7 · `C8`  
**plans**: Claude 70 · Devin 71 · Codex 84  
**proposed by**: claude opus 5.5 `R-cld-1` (1.00)

**verified**: repository/NotificationsRepositoryImpl.kt:503-519 bulkInsertFromSync reads getByIds then upsertAll in separate statements; insert(doc) :484-492 likewise, so a mark-as-read landing in between is overwritten by the server copy. markAllUnreadAsRead (:129-135), markNotificationsAsRead (:120-127), deleteNotifications (:494-501) select then write outside a transaction.

**do**: Add @Transaction default methods to NotificationDao: upsertFromServerPreservingLocal (reads a small id/isRead projection for needsSync=1 rows, chunked 900), markAllUnreadAsReadReturningIds, markExistingAsRead, deleteExisting. Make the five repository methods single DAO calls. Add a Room in-memory test that a needsSync=1,isRead=1 row stays read after a server upsert with isRead=0.


**sequence**: NotificationDao / NotificationsRepositoryImpl: **M3** → M17 → M53 → M58 → M61 → M62 → M73

---

### M4. enterprises: replace the finance-report read-modify-upsert with one targeted UPDATE

**rating 68**: evidence 9 · impact 5 · feasibility 8 · `C11`  
**plans**: Claude 67 · Devin 77 · Codex 71  
**proposed by**: claude opus 5.5 `R-cld-3` (1.00), codex sol 5.6 `R-cdx-2` (0.80)

**verified**: repository/EnterprisesRepositoryImpl.kt:55-79 updateReport builds a Gson JsonObject, loads the whole teams row via updateTeamEntityById (:129-135: getById + upsert) and rewrites every column, so a concurrent sync write is lost. TeamDao.getNonArchivedReportsByTeamId (TeamDao.kt:25) has no production caller.

**do**: Add TeamDao.updateReportFields(...) UPDATE of the 9 report columns + isUpdated=1 WHERE _id; call it from updateReport; delete updateTeamEntityById. Retarget the getNonArchivedReportsByTeamId tests to observeNonArchivedReportsByTeamId(...).first() and delete it.

**watch out**: Keep today's behaviour of attaching the image even when the row is missing unless you mean to change it (codex's spec asserts the opposite while claiming to preserve behaviour).


**sequence**: TeamDao / EnterprisesRepositoryImpl / enterprises fragments: **M4** → M18 → M39

---

### M5. audio: stop sending the user to the home screen when a recording is stopped too early

**rating 60**: evidence 7 · impact 5 · feasibility 7 · `C18`  
**plans**: Claude 60 · Devin 74 · Codex 69  
**proposed by**: copilot gemini 3.8 flash `P-gem-5` (1.00)

**verified**: services/AudioRecorder.kt:101-102 routes MediaRecorder.stop()'s RuntimeException to MainApplication.handleUncaughtException, which logs a crash and starts the HOME intent (MainApplication.kt:199-208); forceStop() (:35-41) calls stop() unguarded (used by AddResourceFragment:159).

**do**: Catch the stop/release RuntimeException in stopRecording and forceStop, release, null the recorder and report via audioRecordListener.onError instead of handleUncaughtException. Add AudioRecorderTest for premature stop.

**watch out**: The 'up to 100 disk probes' claim is overstated: the UUID loop normally does one exists(); simplifying it is optional.


---

### M6. anr: make ANRWatchdog's cross-thread state @Volatile and measure the ANR duration after the wait

**rating 55**: evidence 8 · impact 3 · feasibility 8 · `C58`  
**plans**: Claude 45+43 · Devin 83 · Codex 69  
**proposed by**: devin swe 2 `M-dev-2` (1.00), copilot gemini 3.8 flash `P-gem-6` (1.00)

**verified**: utils/ANRWatchdog.kt:25-26 isWatching/tick are plain vars written on main (tickUpdater) / caller thread (stop) and read on a default-dispatcher coroutine. utils/ANRWatchdog.kt:49-56 captures currentTime before delay(timeout/2) and reports currentTime - lastTick, so the duration excludes the wait (it reports ~timeout/2, not the ~0 ms gemini claims); detection fires at timeout/2.

**do**: One PR: mark isWatching/tick @Volatile; compute the reported duration after delay(); decide explicitly whether detection should fire at timeout/2 or the full timeout, and pin that with an ANRWatchdog test.

---

## Wave 2 — Responsiveness quick wins

Main-thread work, wasted network timeouts, and redundant per-open work. Each task is small and independently revertable.

### M7. sync: skip the alternative-server probe when the primary already answered

**rating 83**: evidence 10 · impact 7 · feasibility 9 · `C1`  
**plans**: Claude 83 · Devin 76 · Codex 70  
**proposed by**: claude opus 5.5 `P-cld-2` (1.00)

**verified**: repository/ConfigurationsRepositoryImpl.kt:185-188 always runs checkServerAvailability(alternativeUrl) after the primary; the result is read only when !primaryReachable (:190). services/sync/ServerUrlMapper.kt:102-107 (updateServerIfNecessary) does the same. On LAN-only tablets every reachability check waits out an extra timeout.

**do**: Probe the alternative only when the primary failed, in both places. Extend ServerUrlMapperTest.testUpdateServerIfNecessaryWhenPrimaryIsUp to assert the alternative URL is never probed; keep the 'falls back to alternative url' ConfigurationsRepositoryImplTest case green.


**sequence**: ConfigurationsRepositoryImpl + ServerUrlMapper: M2 → **M7** → M13 → M21 → M50

---

### M8. viewer: extract PDF text only when Read Aloud is tapped, and drop the duplicate audio Player.Listener

**rating 81**: evidence 10 · impact 7 · feasibility 8 · `C2`  
**plans**: Claude 81 · Devin 77 · Codex 69  
**proposed by**: claude opus 5.5 `P-cld-3` (1.00)

**verified**: ui/viewer/ResourceViewerFragment.kt setupPdfViewer calls extractPdfText() on every PDF open (PDFBox full-document text strip via ResourceViewerViewModel.extractPdfText) though pdfText is only read in the fabReadAloud click. initializeAudioPlayer adds a second listener that repeats createExoPlayer's onIsPlayingChanged/STATE_ENDED handling, so progress is saved twice.

**do**: Remove the eager call; in the fabReadAloud listener fill pdfText lazily on viewLifecycleOwner.lifecycleScope then speak. Delete the second player.addListener block in initializeAudioPlayer.


**sequence**: ResourceViewerFragment/ViewModel: **M8** → M36 → M51

---

### M9. chat: chunk the AI typing animation and stop scrolling the list on every character

**rating 73**: evidence 9 · impact 6 · feasibility 8 · `C4`  
**plans**: Claude 73 · Devin 87 · Codex 70  
**proposed by**: claude opus 5.5 `P-cld-9` (1.00), copilot kimi k3 `P-kim-1` (0.60), copilot grok 4.6 `P-grk-1` (0.50)

**verified**: ui/chat/ChatDetailFragment.kt:270-282 calls onUpdate(response.substring(0, i+1)) once per character with delay(10); ui/chat/ChatAdapter.kt:76-79 sets the text and calls recyclerView.scrollToPosition on every update. A 3,000-char reply = 3,000 substrings (O(n^2) copying), 3,000 relayouts, 3,000 scrolls on main.

**do**: Advance by max(1, len/200) chars per tick (clamped), keep the 10 ms tick, isActive check, final full-text emit, single onComplete and the cancel lambda. In ResponseViewHolder.bind scroll only when lineCount changes. Add a ChatDetailFragmentTest case: update count bounded, last update == full response, cancel stops updates.


**sequence**: ChatViewModel / ChatDetailFragment / ChatSearch: **M9** → M23 → M37 → M45 → M120; ChatDetailFragment typing / VoicesAdapterHelper (same fix, share the helper): **M9** → M14

---

### M10. download: cap PDF thumbnail size and keep the cache key's stat calls off the main thread

**rating 73**: evidence 9 · impact 6 · feasibility 8 · `C5`  
**plans**: Claude 73 · Devin 78 · Codex 69  
**proposed by**: claude opus 5.5 `P-cld-8` (1.00), copilot grok 4.6 `P-grk-3` (0.40)

**verified**: utils/PdfThumbnailLoader.kt:19 builds the key from file.lastModified()/length() before withContext(io), i.e. on the adapter's main-thread scope (ResourcesAdapter.kt:395, InlineResourceAdapter.kt:239). Lines 26-28 size the bitmap as page.height*scale with no cap; an OutOfMemoryError escapes catch(Exception). computePdfRenderSize (utils/PdfRenderUtils.kt:7) already caps maxDim/maxPixels.

**do**: Move key building, cache get and put into the IO block; size with computePdfRenderSize(page.width, page.height, targetWidthPx). Optionally (grok) dedupe concurrent loads of the same key with an in-flight map; a plain cache re-check does not achieve 'one render' on its own.


---

### M11. sync: stop retrying 4xx responses in ApiClient

**rating 73**: evidence 9 · impact 6 · feasibility 8 · `C7`  
**plans**: Claude 73 · Devin 78 · Codex 68  
**proposed by**: copilot grok 4.6 `M-grk-2` (1.00)

**verified**: data/api/ApiClient.kt:13-15 retries with shouldRetry = { resp == null || !resp.isSuccessful } (3 attempts, 2 s delay), so 401/404 cost ~4 s of pointless retries; RetryInterceptor already retries 5xx/IO at the OkHttp layer, so 5xx gets up to 3x3 attempts. 8 call sites.

**do**: Retry only null responses, thrown IO failures and HTTP 5xx; return 4xx immediately. Keep 3 attempts / 2 s for retryable cases. Add ApiClientTest cases: 404/401 -> one call; keep the 503-then-success case.


---

### M12. perf: drop the double map lookup and per-call lambda allocation in GsonUtils field getters

**rating 70**: evidence 9 · impact 5 · feasibility 9 · `C9`  
**plans**: Claude 69 · Devin 79 · Codex 68  
**proposed by**: claude opus 5.5 `P-cld-1` (1.00)

**verified**: utils/GsonUtils.kt:106-107 fieldElement does has()+get(); getPrimitive (:109) is not inline, so each getString/getInt/getLong/getBoolean/getFloat allocates two lambdas and boxes; getJsonElement (:185-190) repeats has()+get(). 253 qualified call sites on the sync path.

**do**: fieldElement = jsonObject?.get(fieldName); make getPrimitive private inline (crossinline only if required); single get + null check in getJsonElement. No signature or fallback changes; GsonUtils* tests must pass unchanged.


**sequence**: GsonUtils.kt: **M12** → M68

---

### M13. sync: return the first successful configuration probe without waiting for the slower one

**rating 69**: evidence 9 · impact 6 · feasibility 6 · `C10`  
**plans**: Claude 68 · Devin 78 · Codex 70  
**proposed by**: codex sol 5.6 `P-cdx-1` (1.00), codex sol 5.6 `M-cdx-8` (1.00)

**verified**: repository/ConfigurationsRepositoryImpl.kt:278-285 getMinApk launches primary and alternative checks concurrently but awaitAll() waits for the slowest (up to the 15 s withTimeout) before picking a Success.

**do**: Consume completions as they arrive; on success cancel and join the rest; on all-failure keep the deterministic URL-order fallback; rethrow cancellation. Add coroutine tests (fast alt + delayed primary; all fail in reversed order).

**watch out**: Today the primary wins when both succeed. Decide explicitly whether an earlier alternative success may now be picked (it flips isAlternativeUrl).


**sequence**: ConfigurationsRepositoryImpl + ServerUrlMapper: M2 → M7 → **M13** → M21 → M50

---

### M14. voices: chunk the typing animation in VoicesAdapterHelper

**rating 68**: evidence 9 · impact 5 · feasibility 8 · `C13`  
**plans**: Claude 67 · Devin 87 · Codex 68  
**proposed by**: copilot kimi k3 `P-kim-2` (1.00)

**verified**: ui/voices/VoicesAdapterHelper.kt:13-31 createOnAnimateTyping uses the same per-character substring + delay(10) loop; used by VoicesFragment, TeamsVoicesFragment and ReplyActivity.

**do**: Advance in chunks keeping the signature, main-dispatcher launch, isActive check and cancel lambda; add VoicesAdapterHelperTest.


**sequence**: ChatDetailFragment typing / VoicesAdapterHelper (same fix, share the helper): M9 → **M14**

---

### M15. login: stop inflating an unused server dialog and re-syncing community docs on every LoginActivity create

**rating 68**: evidence 9 · impact 5 · feasibility 8 · `C12`  
**plans**: Claude 67 · Devin 73 · Codex 69  
**proposed by**: claude opus 5.5 `P-cld-4` (1.00)

**verified**: ui/sync/LoginActivity.kt:355-359 inflates DialogServerUrlBinding and builds a MaterialDialog on each sync-icon tap; for CallerContext.LOGIN_ACTIVITY SyncConfigurationCoordinator never reads currentDialog (:77-81). :380-384 calls loginViewModel.syncCommunityDocs() on every onCreate (rotation, recreate).

**do**: Delete the four dialog lines and their imports. Add a communityDocsSynced flag in LoginViewModel so a successful sync is not repeated (failures still retry); add two LoginViewModelTest cases.


**sequence**: LoginActivity: **M15** → M89

---

### M16. startup: copy map-tile assets off the main thread at cold start

**rating 68**: evidence 10 · impact 4 · feasibility 9 · `C14`  
**plans**: Claude 64 · Devin 76 · Codex 81  
**proposed by**: claude opus 5.5 `P-cld-5` (1.00)

**verified**: ui/onboarding/OnboardingActivity.kt:68 calls MapTileUtils.copyAssets(this) in onCreate; it does external-storage exists/mkdirs/assets.open for two .mbtiles files, and no assets dir exists in app/src, so every launch throws and logs two exceptions on main.

**do**: lifecycleScope.launch(dispatcherProvider.io) { copyAssets(applicationContext) } at the same spot.


---

### M17. dashboard: skip the no-op notification upsert on every dashboard load

**rating 65**: evidence 9 · impact 4 · feasibility 9 · `C15`  
**plans**: Claude 62 · Devin 75 · Codex 68  
**proposed by**: claude opus 5.5 `P-cld-10` (1.00)

**verified**: repository/NotificationsRepositoryImpl.kt:78-118 updateCountNotification always ends with notificationDao.upsert (:114) even when message, relatedId and value are unchanged; each write invalidates every notification Flow (badge included).

**do**: Return early when existing != null, !valueChanged, message and relatedId equal. Rewrite the two 'unchanged' tests to coVerify(exactly = 0) upsert.


**sequence**: NotificationDao / NotificationsRepositoryImpl: M3 → **M17** → M53 → M58 → M61 → M62 → M73

---

### M18. enterprises: read attachment images off the main thread when saving a transaction or report

**rating 63**: evidence 9 · impact 4 · feasibility 8 · `C17`  
**plans**: Claude 61 · Devin 84 · Codex 81  
**proposed by**: devin swe 2 `P-dev-1` (1.00)

**verified**: FileUtils.readBytesFromUri + getDisplayName run inside setPositiveButton on main: ui/enterprises/EnterprisesFinancesFragment.kt:305-306, EnterprisesReportsFragment.kt:186-187 and :269-270.

**do**: Capture applicationContext and the Uri, launch on viewLifecycleOwner.lifecycleScope, read under withContext(dispatcherProvider.io), then call the unchanged viewModel methods.


**sequence**: TeamDao / EnterprisesRepositoryImpl / enterprises fragments: M4 → **M18** → M39

---

### M19. startup: cache the manifest permission set and delete the dead install-permission check

**rating 60**: evidence 9 · impact 3 · feasibility 9 · `C24`  
**plans**: Claude 55 · Devin 76 · Codex 73  
**proposed by**: claude opus 5.5 `P-cld-6` (1.00)

**verified**: base/BasePermissionActivity.kt isPermissionDeclaredInManifest calls packageManager.getPackageInfo(GET_PERMISSIONS) on every check, and requestAllPermissions makes up to 4 checks per SDK branch; base/BaseContainerFragment.kt:71 stores hasInstallPermission() in hasInstallPermissionValue (:64), which is never read (installUnknownSourcesRequestCode :63 unused).

**do**: Lazy HashSet of requestedPermissions; delete the two dead fields and the :71 call.


---

### M20. reachability: cache primary-server probes in ServerReachabilityProvider

**rating 58**: evidence 8 · impact 4 · feasibility 7 · `C20`  
**plans**: Claude 57 · Devin 74 · Codex 84  
**proposed by**: copilot kimi k3 `P-kim-4` (1.00)

**verified**: utils/ServerReachabilityProvider.kt:47-50 isPrimaryServerReachable always runs a live tryConnect, while isServerReachable uses the 30 s reachabilityCache.

**do**: Give it the same cache-then-probe flow keyed by the raw URL, without the alternative-URL fallback; test one newCall within TTL.


---

### M21. sync: HEAD-probe and always close connections in ServerUrlMapper.isUrlDirectlyReachable

**rating 56**: evidence 8 · impact 4 · feasibility 6 · `C26`  
**plans**: Claude 54 · Devin 77 · Codex 68  
**proposed by**: copilot grok 4.6 `M-grk-3` (1.00), copilot gemini 3.8 flash `P-gem-2` (0.60)

**verified**: services/sync/ServerUrlMapper.kt:116-128 opens HttpURLConnection with GET and skips disconnect() if responseCode throws; called from ConfigurationsRepositoryImpl:561.

**do**: Use HEAD and close in finally (grok: inject the existing @ReachabilityHttpClient OkHttpClient and use java.net.URI in extractBaseUrl, keeping processUrl assertions green).

**watch out**: Gemini's ServerConfigUtils.isLocalNetwork regex rewrite is not a hotspot; skip it.


**sequence**: ConfigurationsRepositoryImpl + ServerUrlMapper: M2 → M7 → M13 → **M21** → M50

---

### M22. calendar: index meetups by local date once per emission (in CalendarViewModel)

**rating 55**: evidence 8 · impact 3 · feasibility 8 · `C36`  
**plans**: Claude 51 · Devin 79 · Codex 71  
**proposed by**: copilot grok 4.6 `M-grk-9` (0.90), codex sol 5.6 `M-cdx-1` (0.80), copilot kimi k3 `P-kim-3` (0.70), copilot gemini 3.8 flash `R-gem-7` (0.40)

**verified**: ui/calendar/CalendarFragment.kt (87 lines): the day-click listener re-converts every meetup's startDate with ZoneId.systemDefault() and filters on each tap; showAgendaDialog builds a formatter per dialog.

**do**: Derive Map<LocalDate, List<Meetup>> (or epoch-day keys) once per meetups emission, preferably as CalendarViewModel state; the fragment only builds CalendarDay+drawable and looks up the tapped day; keep order and the empty-day no-op; hoist the formatter.


---

### M23. chat: stop re-normalizing the whole chat history per keystroke and drop ChatSearch's Dispatchers.Default default

**rating 55**: evidence 8 · impact 3 · feasibility 8 · `C37`  
**plans**: Claude 51 · Devin 84 · Codex 73  
**proposed by**: claude opus 5.5 `M-cld-6` (1.00), copilot grok 4.6 `P-grk-8` (0.80), codex sol 5.6 `P-cdx-2` (0.60), codex sol 5.6 `M-cdx-4` (0.60), copilot gemini 3.8 flash `P-gem-10` (0.40), copilot grok 4.6 `M-grk-10` (0.40), copilot kimi k3 `R-kim-4b` (0.30)

**verified**: utils/ChatSearch.kt fullConvoSearch/searchByTitle rebuild normalized ConvoChat/TitleChat copies of every chat on each search (fired per debounced keystroke over the same allChats, ChatViewModel.kt:139,162); :25 defaults dispatcher to Dispatchers.Default (the only Dispatchers.* use outside DispatcherProvider).

**do**: Preferred (claude): a ChatSearch.Index with lazy normalized views rebuilt when allChats changes, so only the query is normalized per call; otherwise stream without wrapper lists. Make the dispatcher required. Keep the four ranking buckets.

**watch out**: Do NOT make a blank query return emptyList() (gemini, grok M-10): ChatSearchTest 'search with empty query returns all chats' pins the current behaviour, and the VM already short-circuits blank queries.


**sequence**: ChatViewModel / ChatDetailFragment / ChatSearch: M9 → **M23** → M37 → M45 → M120

---

### M24. dictionary: drop the duplicate COUNT on DictionaryActivity open

**rating 55**: evidence 9 · impact 2 · feasibility 9 · `C52`  
**plans**: Claude 46 · Devin 77 · Codex 69  
**proposed by**: copilot grok 4.6 `P-grk-6` (1.00), devin swe 2 `M-dev-9` (1.00)

**verified**: ui/dictionary/DictionaryActivity.kt:58-59 calls loadCount() then loadDictionary(); loadDictionary already emits Populated(count()) (DictionaryViewModel.kt:41-44); two launches race to write loadState.

**do**: Remove the loadCount() call and method; repoint its test at loadDictionary.


**sequence**: Dictionary screen + repository: **M24** → M52 → M64 → M119

---

### M25. upload: suppress duplicate SyncUiState emissions from the user-data upload scheduler

**rating 52**: evidence 8 · impact 2 · feasibility 9 · `C62`  
**plans**: Claude 44 · Devin 75 · Codex 68  
**proposed by**: codex sol 5.6 `M-cdx-9` (1.00)

**verified**: services/UserDataUploadScheduler.kt:32-49 maps every WorkInfo emission (progress updates included) to SyncUiState with no distinctUntilChanged; Loading/Success are object/data classes.

**do**: Add distinctUntilChanged() after the map.


---

### M26. upload: stop recounting the whole download queue three times per file

**rating 50**: evidence 7 · impact 3 · feasibility 7 · `C47`  
**plans**: Claude 48 · Devin 73 · Codex 68  
**proposed by**: claude opus 5.5 `P-cld-7` (1.00)

**verified**: services/DownloadService.kt:179-184 getRemainingCount builds priority + pendingUrls (a new set of the whole queue) and runs at :155, :204 and :584 per file.

**do**: Count without the union; keep one getStringSet per key per call.

**watch out**: Do not drop the :204 recount blindly: URLs enqueued during a download change the count (claude's 'always matches :155' is wrong).


**sequence**: DownloadService / DownloadUtils: **M26** → M93

---

### M27. notifications: remove the fixed 200 ms sleep before read-receipt broadcasts

**rating 48**: evidence 8 · impact 2 · feasibility 7 · `C68`  
**plans**: Claude 41 · Devin 75 · Codex 68  
**proposed by**: devin swe 2 `M-dev-10` (1.00)

**verified**: services/NotificationActionReceiver.kt:102-103 withContext(main) { delay(200); ... } after the repository write has already completed.

**do**: Delete the delay; keep broadcast order and try/catch; check NotificationActionReceiverTest first.


---

## Wave 3 — Architecture seams

Moves logic out of Activities, Fragments, and adapters into ViewModels, repositories, and mappers; deletes pass-throughs; narrows interfaces.

### M28. health: move key generation, encryption and entity building out of HealthExaminationActivity

**rating 64**: evidence 9 · impact 5 · feasibility 6 · `C16`  
**plans**: Claude 62 · Devin 73 · Codex 69  
**proposed by**: claude opus 5.5 `R-cld-6` (1.00)

**verified**: ui/health/HealthExaminationActivity.kt:240-291 (saveData) generates key/IV, mutates the repository-owned UserEntity and encrypts the examination; createPojo :366-383 encrypts the profile; initExamination :117-127 decrypts with getEncryptedDataAsJson.

**do**: Decrypt once in HealthExaminationViewModel.loadData into state.savedExamination; change saveExamination to take plain sign/health and do id/key/IV/encrypt on dispatcherProvider.io before healthRepository.saveExamination. Update the three saveExamination_* tests and add a null-key round-trip test.


**sequence**: Health screens: **M28** → M35 → M48 → M56 → M59 → M94

---

### M29. courses: delete the CourseDetailProvider/RatingSummaryProvider passthroughs

**rating 63**: evidence 10 · impact 3 · feasibility 9 · `C19`  
**plans**: Claude 57 · Devin 74 · Codex 69  
**proposed by**: devin swe 2 `R-dev-1` (1.00), copilot grok 4.6 `R-grk-2` (1.00)

**verified**: ui/courses/CourseDetailProvider.kt and RatingSummaryProvider.kt are one-line wrappers over coursesRepository.getCourseDetailModel and ratingsRepository.getRatingSummary("course", ...); only CourseDetailViewModel (:38-39) and its test use them.

**do**: Inject CoursesRepository and RatingsRepository into CourseDetailViewModel, call them directly, delete both files, switch the test to repository mocks.


**sequence**: CourseDetailViewModel / CoursesStepsViewModel: **M29** → M30 → M33

---

### M30. courses: drop Context from CourseDetailViewModel and CoursesStepsViewModel via StoragePathResolver

**rating 60**: evidence 9 · impact 3 · feasibility 9 · `C22`  
**plans**: Claude 55 · Devin 70 · Codex 68  
**proposed by**: claude opus 5.5 `M-cld-4` (1.00)

**verified**: ui/courses/CourseDetailViewModel.kt:37/:69 and CoursesStepsViewModel.kt inject @ApplicationContext only for getExternalFilesDir(null); FileUtils.getExternalFilesDir (utils/FileUtils.kt:37-39) caches it; utils/StoragePathResolver.kt wraps FileUtils.

**do**: Add StoragePathResolver.resolveExternalFilesDir(); inject the resolver instead of Context in both VMs (keep the 'file://null/ole/' behaviour); update both tests.


**sequence**: CourseDetailViewModel / CoursesStepsViewModel: M29 → **M30** → M33

---

### M31. data: slim the retry-queue duplicate lookup and delete dead RetryRepository/RetryDao surface

**rating 60**: evidence 9 · impact 3 · feasibility 9 · `C23`  
**plans**: Claude 55 · Devin 73 · Codex 70  
**proposed by**: claude opus 5.5 `R-cld-2` (1.00)

**verified**: repository/RetryRepositoryImpl.kt:42-52 recordFailure uses only existing.id from RetryDao.findExisting (SELECT *, pulls serializedPayload). getPendingCount/deletePendingAndAbandonedOperations are interface methods used only inside the Impl; RetryDao.update has no caller.

**do**: Replace with findExistingId (SELECT id ... LIMIT 1); delete RetryDao.update; make the two methods private; retarget tests through getRetryQueueSnapshot/safeClearQueue.


**sequence**: RetryRepositoryImpl / RetryDao / RetryQueueWorker: **M31** → M49 → M55 → M66

---

### M32. data: move resource _all_docs fetching and doc filtering from SyncManager into SyncRepository

**rating 59**: evidence 9 · impact 4 · feasibility 6 · `C21`  
**plans**: Claude 56 · Devin 73 · Codex 69  
**proposed by**: devin swe 2 `R-dev-8` (1.00)

**verified**: services/sync/SyncManager.kt:290-439 resourceTransactionSync performs raw apiInterface.getJsonObject calls (:310, :341), total_rows parsing (:312-314) and _design/doc filtering (:367-377) inside the sync service.

**do**: Add fetchResourceCount / fetchResourceDocsPage to SyncRepository(+Impl) with the moved code (null on failure); SyncManager keeps loop control, logging, progress, checkpointing and cleanup. Add SyncRepositoryImplTest coverage.


**sequence**: SyncRepositoryImpl / SyncManager: **M32** → M40 → M69 → M77

---

### M33. courses: decide course-step downloads in ResourceDownloadCoordinator, not CoursesStepsViewModel

**rating 58**: evidence 9 · impact 3 · feasibility 8 · `C27`  
**plans**: Claude 53 · Devin 70 · Codex 68  
**proposed by**: claude opus 5.5 `R-cld-9` (1.00)

**verified**: ui/courses/CoursesStepsViewModel.kt loadStep calls configurationsRepository.checkServerAvailability() then resourcesRepository.downloadResourcesPriority(); ConfigurationsRepository is injected only for this. services/ResourceDownloadCoordinator.kt:26-34 already owns the 'check server then download' policy.

**do**: Add downloadPriorityIfReachable(items): Boolean to the coordinator (false for empty/unreachable, otherwise launch in applicationScope); call it from loadStep; drop the ConfigurationsRepository param. Retarget CoursesStepsViewModelTest; add three coordinator tests.


**sequence**: CourseDetailViewModel / CoursesStepsViewModel: M29 → M30 → **M33**

---

### M34. courses: give the progress grid a typed row model

**rating 58**: evidence 9 · impact 3 · feasibility 8 · `C28`  
**plans**: Claude 53 · Devin 73 · Codex 68  
**proposed by**: devin swe 2 `R-dev-5` (1.00)

**verified**: ui/courses/ProgressGridAdapter.kt is ListAdapter<JsonObject> reading item["percentage"].asString / item["completed"].asBoolean at bind; CourseProgressActivity.kt:52 submits data.steps.map { it.asJsonObject } (CourseProgressData.steps: JsonArray).

**do**: Add StepProgressCell(stepId, percentage, completed) plus a mapper in CourseProgressData.kt; switch the adapter/diff to it; map before submitList.


---

### M35. health: extract examination-item mapping out of HealthExaminationAdapter

**rating 58**: evidence 9 · impact 3 · feasibility 8 · `C29`  
**plans**: Claude 53 · Devin 74 · Codex 69  
**proposed by**: devin swe 2 `R-dev-10` (1.00)

**verified**: ui/health/HealthExaminationAdapter.kt:58-88 submitExaminations decrypts, resolves creator names (displayNameCache) and formats dates inside the ListAdapter.

**do**: Move the mapping into a HealthExaminationItemMapper in ui/health; keep withContext(default)/submitList in the adapter.


**sequence**: Health screens: M28 → **M35** → M48 → M56 → M59 → M94

---

### M36. viewer: move the playback-speed picker off material-dialogs and drop a redundant safe call

**rating 58**: evidence 9 · impact 3 · feasibility 8 · `C30`  
**plans**: Claude 53 · Devin 70 · Codex 68  
**proposed by**: claude opus 5.5 `M-cld-8` (1.00)

**verified**: ui/viewer/ResourceViewerFragment.kt:227 showPlaybackSpeedDialog uses com.afollestad MaterialDialog (import :55), one of the material-dialogs users keeping android.enableJetifier=true (gradle.properties:26); :124 library.id?.let on a non-null id.

**do**: Rewrite with AlertDialog.Builder(...).setSingleChoiceItems; remove the import; call saveTranslationAudioPath(library.id, ...) directly. Build both flavors.


**sequence**: ResourceViewerFragment/ViewModel: M8 → **M36** → M51

---

### M37. chat: extract conversation pagination from ChatViewModel into a plain pager

**rating 56**: evidence 9 · impact 3 · feasibility 7 · `C31`  
**plans**: Claude 52 · Devin 74 · Codex 68  
**proposed by**: devin swe 2 `R-dev-2` (1.00)

**verified**: ui/chat/ChatViewModel.kt:59-65 holds PAGE_SIZE/allConversations/loadedCount; :197-243 parseAndBuildInitialPage (Gson parse), buildInitialPage, buildMessagesSlice, loadMoreConversations, clearPaginationState.

**do**: Create ChatConversationPager with the moved bodies; keep the VM's public methods as thin delegations so fragments/tests are unchanged.


**sequence**: ChatViewModel / ChatDetailFragment / ChatSearch: M9 → M23 → **M37** → M45 → M120

---

### M38. data: introduce UserLookupRepository and move single-purpose callers onto it

**rating 56**: evidence 9 · impact 3 · feasibility 7 · `C33`  
**plans**: Claude 52 · Devin — · Codex 68  
**proposed by**: claude opus 5.5 `R-cld-7` (1.00)

**verified**: DiagnosticsRepositoryImpl (:63, :86) and FeedbackListViewModel (:35) call only getUserModel() on the ~50-method UserRepository.

**do**: Move getUserModel/getUserById/getUsersByIds into UserLookupRepository, make UserRepository extend it, provide it from a new module by delegating from UserRepository (RepositoryModule is PR-owned); retype the two consumers.


---

### M39. enterprises: project finance reports and delete dead TeamDao queries

**rating 56**: evidence 9 · impact 3 · feasibility 7 · `C34`  
**plans**: Claude 52 · Devin 73 · Codex 71  
**proposed by**: devin swe 2 `R-dev-7` (1.00)

**verified**: EnterprisesRepositoryImpl.getReportsFlow (:86-91) observes full MyTeam rows only to map toFinanceReport; TeamDao.getAll() and getNonArchivedReportsByTeamId have no production callers.

**do**: Add a FinanceReport projection Flow (isUpdated AS updated) with the same WHERE/ORDER; delete toFinanceReport and the two dead queries; move their tests.

**watch out**: Overlaps the 'targeted UPDATE' task on getNonArchivedReportsByTeamId - whichever lands second drops that part.


**sequence**: TeamDao / EnterprisesRepositoryImpl / enterprises fragments: M4 → M18 → **M39**

---

### M40. sync: split ResourcesSyncRepository so the sync layer stops depending on the full resources surface

**rating 56**: evidence 9 · impact 3 · feasibility 7 · `C35`  
**plans**: Claude 52 · Devin 64 · Codex 69  
**proposed by**: claude opus 5.5 `R-cld-8` (1.00)

**verified**: SyncRepositoryImpl uses only batchInsertMyLibrary (:55); SyncManager only batchInsertResources (:385) and removeDeletedResources (:433). ResourcesRepositoryImpl has no class-level scope (singleton only via RepositoryModule:162-164).

**do**: Extract the three methods into ResourcesSyncRepository, make ResourcesRepository extend it, provide by delegation (never a second @Binds to the Impl), retype the two consumers.


**sequence**: SyncRepositoryImpl / SyncManager: M32 → **M40** → M69 → M77

---

### M41. ui: hoist ThemeManager's dialog out of the singleton service

**rating 55**: evidence 8 · impact 3 · feasibility 8 · `C40`  
**plans**: Claude 51 · Devin 68 · Codex 68  
**proposed by**: copilot gemini 3.8 flash `M-gem-9` (1.00)

**verified**: services/ThemeManager.kt:17-45 showThemeDialog builds an AlertDialog inside a @Singleton state service; ThemeManagerTest runs under RobolectricTestRunner only for that.

**do**: Move showThemeDialog to a UI helper; keep ThemeManager to getCurrentThemeMode/setThemeMode; make ThemeManagerTest plain JUnit.


---

### M42. settings: hoist the guest-user check into SettingsViewModel

**rating 55**: evidence 9 · impact 2 · feasibility 9 · `C54`  
**plans**: Claude 46 · Devin 70 · Codex 68  
**proposed by**: claude opus 5.5 `R-cld-10` (1.00)

**verified**: ui/settings/SettingsActivity.kt SettingFragment injects UserSessionManager (:89-90) and repeats userModel?.id?.startsWith("guest") at :194/:231, :258-259, :301-302.

**do**: Add suspend isGuest() to SettingsViewModel with the exact predicate; replace the three checks; drop the field.


**sequence**: SettingsActivity: **M42** → M49

---

### M43. surveys: move respondent JSON parsing into SurveysPublicMapper

**rating 55**: evidence 9 · impact 2 · feasibility 9 · `C55`  
**plans**: Claude 46 · Devin 72 · Codex 68  
**proposed by**: devin swe 2 `R-dev-4` (1.00)

**verified**: ui/surveys/PublicSurveyViewModel.kt:81-87 parses submission.user with JsonParser (import :5) and calls publicMapper.sanitizeRespondent.

**do**: Add parseRespondent(userJson): JsonObject? to the mapper with the same null/blank/{}/malformed semantics; add mapper tests.


---

### M44. user: route achievement calls through UserAchievementsRepository

**rating 55**: evidence 9 · impact 2 · feasibility 9 · `C57`  
**plans**: Claude 46 · Devin 74 · Codex 69  
**proposed by**: devin swe 2 `R-dev-3` (1.00)

**verified**: ui/user/AchievementViewModel.kt uses achievementUpdates/initializeAchievement/updateAchievement/getAchievementData (all on UserAchievementsRepository, bound at di/RepositoryModule.kt:212) via the full UserRepository.

**do**: Inject UserAchievementsRepository for those four; keep UserRepository for getUserModel/updateProfileFields.


---

### M45. chat: hoist server prefs and AI-provider fetching from the chat fragments into ChatViewModel

**rating 54**: evidence 8 · impact 4 · feasibility 5 · `C32`  
**plans**: Claude 52 · Devin 70 · Codex 68  
**proposed by**: copilot grok 4.6 `R-grk-1` (1.00)

**verified**: ui/chat/ChatDetailFragment.kt injects SharedPrefManager (:104) and ServerUrlMapper (:107) and calls MainApplication.isServerReachable/isPrimaryServerReachable (:34-35, :604-725); ChatHistoryFragment injects SharedPrefManager (:43).

**do**: Add ensureAiProviders() and a no-arg history reload to ChatViewModel; move processServerUrl/updateServerIfNecessary/clearAlternativeUrlIfPrimaryRestored; fragments drop the injections.


**sequence**: ChatViewModel / ChatDetailFragment / ChatSearch: M9 → M23 → M37 → **M45** → M120

---

### M46. core: extract Utilities' pure text helpers into an Android-free object

**rating 53**: evidence 8 · impact 3 · feasibility 7 · `C41`  
**plans**: Claude 50 · Devin 68 · Codex 68  
**proposed by**: copilot kimi k3 `M-kim-9` (1.00)

**verified**: utils/Utilities.kt imports Handler/Toast/MimeTypeMap/Patterns; checkNA (:68), toHex (:76), normalizeText (:80), isValidEmail (:22) are pure apart from Patterns.

**do**: Move them to TextNormalizeUtils with delegating one-liners in Utilities; extend UtilitiesTest.

**watch out**: Re-implementing isValidEmail without Patterns.EMAIL_ADDRESS can change which addresses validate; pin the current behaviour with tests first.


**sequence**: Utilities.kt: **M46** → M74 → M111

---

### M47. dashboard: move the challenge-dialog earnings/progress logic into MarkdownViewModel

**rating 53**: evidence 8 · impact 3 · feasibility 7 · `C42`  
**plans**: Claude 50 · Devin 72 · Codex 68  
**proposed by**: copilot gemini 3.8 flash `R-gem-10` (1.00)

**verified**: ui/components/MarkdownDialogFragment.kt:94-97 computes earnings and progress; :115-125 matches "no iniciado"/"terminado"; :79 hard-casts (activity as DashboardActivity).

**do**: Add a pure calculateProgress(...) returning a small state model in MarkdownViewModel; use a safe cast. Add unit tests for the math.


---

### M48. health: load patients through UserRepository in HealthViewModel

**rating 53**: evidence 9 · impact 2 · feasibility 8 · `C59`  
**plans**: Claude 44 · Devin 64 · Codex 68  
**proposed by**: copilot grok 4.6 `R-grk-4` (1.00)

**verified**: repository/HealthRepositoryImpl.kt:166-179 getPatientById/getPatientsSortedBy/searchPatients only delegate to UserRepository; HealthViewModel already injects UserRepository.

**do**: Call getUserById/getUsersSortedBy/searchUsers directly (blank query -> sorted list); retarget HealthViewModelTest.


**sequence**: Health screens: M28 → M35 → **M48** → M56 → M59 → M94

---

### M49. settings: show at most 10 retry previews plus a COUNT instead of loading every pending row

**rating 53**: evidence 9 · impact 2 · feasibility 8 · `C60`  
**plans**: Claude 44 · Devin 65 · Codex 70  
**proposed by**: claude opus 5.5 `M-cld-5` (1.00)

**verified**: repository/RetryRepositoryImpl.kt:184-189 getRetryQueueSnapshot loads getPending() (SELECT * with payloads, no LIMIT); the only consumer (ui/settings/SettingsActivity.kt:130-142) shows take(10) and 'and N more'.

**do**: Add getPendingPreview(now, limit) and getPendingDueCount(now) with getPending's WHERE; add pendingDueCount to RetryQueueDetails; use them in the snapshot and dialog.


**sequence**: RetryRepositoryImpl / RetryDao / RetryQueueWorker: M31 → **M49** → M55 → M66; SettingsActivity: M42 → **M49**

---

### M50. data: compare URL schemes against literals and read the installed version through AppVersionProvider in getMinApk

**rating 52**: evidence 9 · impact 1 · feasibility 10 · `C86`  
**plans**: Claude 34 · Devin 73 · Codex 68  
**proposed by**: devin swe 2 `M-dev-8` (1.00); AppVersionProvider half: codex sol 5.6 `R-cdx-8`, devin swe 2 `M-dev-8`

**verified**: repository/ConfigurationsRepositoryImpl.kt:298-301 compares NetworkUtils.extractProtocol(url) (literal "<scheme>://") with context.getString(R.string.http_protocol/https_protocol).

**do**: Compare with "http://"/"https://" constants; keep getString only for the messages.
 Also replace context.getString(R.string.app_version) (repository/ConfigurationsRepositoryImpl.kt:323) with the injectable utils/AppVersionProvider (BuildConfig.VERSION_NAME) so the check is testable without resources.

**sequence**: ConfigurationsRepositoryImpl + ServerUrlMapper: M2 → M7 → M13 → M21 → **M50**

---

### M51. viewer: drop Context from ResourceViewerViewModel

**rating 51**: evidence 8 · impact 3 · feasibility 6 · `C48`  
**plans**: Claude 48 · Devin 70 · Codex 69  
**proposed by**: copilot gemini 3.8 flash `R-gem-5` (1.00)

**verified**: ui/viewer/ResourceViewerViewModel.kt:30 injects @ApplicationContext for getExternalFilesDir (:120), downloadResource/openDownloadService (:124-127) and PDFBoxResourceLoader.init (:132).

**do**: Move the three Android calls to ResourceViewerFragment (init PDFBox once there); keep extractPdfText as pure PDDocument work; update ResourceViewerViewModelTest.

**watch out**: Coordinate with the 'extract PDF text only on Read Aloud' task (same functions).


**sequence**: ResourceViewerFragment/ViewModel: M8 → M36 → **M51**

---

### M52. dictionary: move the dictionary download request out of DictionaryActivity into DictionaryViewModel

**rating 51**: evidence 9 · impact 2 · feasibility 7 · `new, N3`  
**plans**: Claude — · Devin 70 · Codex 68  
**proposed by**: copilot gemini 3.8 flash `R-gem-1` (1.00), copilot kimi k3 `R-kim-7` (0.60)

**verified**: ui/dictionary/DictionaryActivity.kt:84-89 builds the Download list from Constants.DICTIONARY_URL and calls DownloadUtils.openDownloadService itself; :44 matches the finished download by URL.

**do**: Expose a dictionaryDownloadUrl / requestDownload() from DictionaryViewModel; the Activity only starts the service with what the VM returns. Add a VM test.

**watch out**: Same files as #52 (duplicate COUNT) and #45 (DictionaryRepositoryImpl Log); land after #52.

**sequence**: Dictionary screen + repository: M24 → **M52** → M64 → M119

---

### M53. notifications: read notification lists as NotificationPayload projections

**rating 50**: evidence 8 · impact 2 · feasibility 8 · `C65`  
**plans**: Claude 43 · Devin 74 · Codex 84  
**proposed by**: codex sol 5.6 `R-cdx-1` (1.00)

**verified**: NotificationDao.getNotifications (:36-37) returns AppNotification; NotificationsRepositoryImpl.getNotifications (:137-159) copies 14 fields into NotificationPayload.

**do**: Select the payload columns into NotificationPayload directly; delete the copy; extend DAO/repository tests for filter and ordering.

**watch out**: The saving is mostly code: nearly every column is selected anyway.


**sequence**: NotificationDao / NotificationsRepositoryImpl: M3 → M17 → **M53** → M58 → M61 → M62 → M73

---

### M54. data: share submission loading and page setup in SubmissionsRepositoryExporter

**rating 48**: evidence 8 · impact 2 · feasibility 7 · `C67`  
**plans**: Claude 41 · Devin 75 · Codex 68  
**proposed by**: codex sol 5.6 `R-cdx-10` (1.00)

**verified**: repository/SubmissionsRepositoryExporter.kt generateSubmissionPdf and generateMultipleSubmissionsPdf duplicate answer/membership attachment and PdfDocument page setup (:47-73, :132-170).

**do**: Extract private helpers for attaching answers/membership and starting a configured page; keep filenames, page size, ordering and DAO call counts.


**sequence**: SubmissionsRepositoryExporter.kt: **M54** → M71

---

### M55. retry: let RetryQueueWorker call RetryRepository directly

**rating 48**: evidence 8 · impact 2 · feasibility 7 · `C69`  
**plans**: Claude 41 · Devin 64 · Codex 70  
**proposed by**: copilot gemini 3.8 flash `R-gem-6` (1.00)

**verified**: services/retry/RetryQueue.kt:20-85 forwards tryStartProcessing/getPendingOperations/cleanup/finishProcessing to RetryRepository; RetryQueueWorker (:42-43) injects both.

**do**: Drop RetryQueue from the worker; update RetryQueueWorkerTest.

**watch out**: RetryQueueTest is owned by #17187 - don't touch it.


**sequence**: RetryRepositoryImpl / RetryDao / RetryQueueWorker: M31 → M49 → **M55** → M66

---

### M56. health: stop using the getHealthEntry pair in HealthExaminationViewModel

**rating 48**: evidence 9 · impact 1 · feasibility 8 · `C90`  
**plans**: Claude 33 · Devin 70 · Codex 69  
**proposed by**: copilot grok 4.6 `R-grk-5` (1.00)

**verified**: ui/health/HealthExaminationViewModel.kt:63 unpacks healthRepository.getHealthEntry (= userRepository.getUserById + getByIdOrUserId, HealthRepositoryImpl.kt:41-46).

**do**: Call the two methods directly; update the getHealthEntry stubs.


**sequence**: Health screens: M28 → M35 → M48 → **M56** → M59 → M94

---

### M57. courses: log course visits through ActivitiesRepository in TakeCourseViewModel

**rating 47**: evidence 8 · impact 1 · feasibility 9 · `C93`  
**plans**: Claude 32 · Devin 68 · Codex 69  
**proposed by**: copilot grok 4.6 `R-grk-3` (1.00)

**verified**: ui/courses/TakeCourseViewModel.kt:55-56 calls coursesRepository.logCourseVisit, which only forwards to activitiesRepository.logCourseVisit (CoursesRepositoryImpl.kt:589-591).

**do**: Inject ActivitiesRepository and call it.

**watch out**: Grok says CoursesRepository.logCourseVisit has other callers; it has none, so it becomes dead (CoursesRepositoryImpl is PR-owned; leave it for now).


---

### M58. notifications: build join-request details without the intermediate Triple list

**rating 47**: evidence 8 · impact 1 · feasibility 9 · `C96`  
**plans**: Claude 32 · Devin 75 · Codex 69  
**proposed by**: codex sol 5.6 `M-cdx-7` (1.00)

**verified**: repository/NotificationsRepositoryImpl.kt:299-336 getJoinRequestDetailsBatch builds ArrayList<Triple> then walks it twice.

**do**: Index team/user maps and build the result in one pass; keep Unknown Team/User fallbacks.


**sequence**: NotificationDao / NotificationsRepositoryImpl: M3 → M17 → M53 → **M58** → M61 → M62 → M73

---

### M59. health: let HealthViewModel own its realtime-sync subscription

**rating 45**: evidence 7 · impact 2 · feasibility 7 · `C74`  
**plans**: Claude 40 · Devin 68 · Codex 68  
**proposed by**: copilot gemini 3.8 flash `R-gem-4` (1.00)

**verified**: ui/health/HealthViewModel.kt:72 exposes healthSyncUpdates; MyHealthFragment.kt:106-108 collects it only to call viewModel.refreshSelectedPatient().

**do**: Collect inside the VM and drop the public flow and fragment collector.

**watch out**: The fragment collector is lifecycle-aware; a VM collector keeps running while stopped. Use a debounce and accept that or gate it.


**sequence**: Health screens: M28 → M35 → M48 → M56 → **M59** → M94

---

### M60. life: observe MyLife rows as a Flow instead of re-fetching after every mutation

**rating 44**: evidence 8 · impact 2 · feasibility 5 · `new, N4`  
**plans**: Claude — · Devin 66 · Codex 68  
**proposed by**: copilot gemini 3.8 flash `R-gem-3` (1.00)

**verified**: data/room/dao/MyLifeDao.kt has no Flow-returning query; ui/life/LifeViewModel.kt:33-55 reloads the list inside withContext(io) after each visibility/reorder write.

**do**: Add observeByUserId(userId): Flow<List<MyLife>>; LifeRepository exposes it; LifeViewModel collects it and drops the manual reloads. Keep the label resolution and sort order.

**watch out**: Interacts with LifeCache (#103) and the IO-hop cleanup (#101): land #101 and #103 first. Gemini named a non-existent observeAll; the query must be added.

**sequence**: Life screen: **M60** → M100 → M101 → M102 → M115

---

### M61. notifications: replace NotificationDao.markSynced's raw CASE query with a typed partial update

**rating 40**: evidence 6 · impact 2 · feasibility 6 · `C78`  
**plans**: Claude 37 · Devin 74 · Codex 84  
**proposed by**: copilot gemini 3.8 flash `R-gem-9` (0.60)

**verified**: data/room/dao/NotificationDao.kt:73-113 builds UPDATE ... CASE id WHEN ? THEN ? via StringBuilder + @RawQuery.

**do**: @Update(entity = AppNotification::class) with a (id, rev, needsSync) projection.

**watch out**: Skip gemini's observeUnreadCount: it is new unused API.


**sequence**: NotificationDao / NotificationsRepositoryImpl: M3 → M17 → M53 → M58 → **M61** → M62 → M73

---

### M62. notifications: narrow NotificationsRepositoryImpl's voices/user dependencies

**rating 40**: evidence 8 · impact 2 · feasibility 3 · `C87`  
**plans**: Claude 34 · Devin 68 · Codex 68  
**proposed by**: copilot kimi k3 `R-kim-2` (1.00)

**verified**: repository/NotificationsRepositoryImpl.kt:28-33 injects full VoicesRepository (used only for countTopLevelByTeams, :388) and Lazy<UserRepository> (getUserById/getUsersByIds).

**do**: Narrow reader interfaces; could reuse UserLookupRepository for the user half.

**watch out**: Needs PR-owned VoicesRepositoryImpl/UserRepositoryImpl/RepositoryModule.


**sequence**: NotificationDao / NotificationsRepositoryImpl: M3 → M17 → M53 → M58 → M61 → **M62** → M73

---

## Wave 4 — Platform-neutral data layer

Removes android.util.Log from repositories, uses the injected TimeProvider everywhere, and routes failures to typed results or SyncTimeLogger.

### M63. data: remove android.util.Log from DiagnosticsRepositoryImpl

**rating 60**: evidence 10 · impact 2 · feasibility 10 · `C44`  
**plans**: Claude 48 · Devin 79 · Codex 79  
**proposed by**: codex sol 5.6 `R-cdx-7` (1.00), copilot gemini 3.8 flash `M-gem-8` (0.60), copilot gemini 3.8 flash `R-gem-8` (0.40)

**verified**: repository/DiagnosticsRepositoryImpl.kt Log.w in saveLogToRoom/saveLogsToRoom while both already return Boolean success.

**do**: Remove the import, calls and TAG; add DAO-exception -> false tests for both paths.

**watch out**: Skip gemini's extras: withContext(io) around suspend DAO calls is redundant, and ApkLogDao.markUploadedBatch already chunks.


---

### M64. data: remove android.util.Log from DictionaryRepositoryImpl

**rating 60**: evidence 10 · impact 2 · feasibility 10 · `C45`  
**plans**: Claude 48 · Devin 79 · Codex 64  
**proposed by**: copilot grok 4.6 `M-grk-5` (1.00), codex sol 5.6 `R-cdx-4` (0.80), copilot gemini 3.8 flash `M-gem-6` (0.60), copilot grok 4.6 `R-grk-6` (0.40)

**verified**: repository/DictionaryRepositoryImpl.kt:3,61 - Log.e is the only android.* import; failure is already returned as DictionaryLoad.Failed(e).

**do**: Drop the import and call, keep Failed(e) and CancellationException rethrow. Optional (grok): inject the Hilt-provided Json instead of the Json companion and resolve the dictionary File once in DictionaryFileReaderImpl.

**watch out**: Do not 'chunk' DictionaryDao.insertAll (R-grk-6, P-gem-1): Room's @Insert(List) binds one row at a time, so the 999-variable limit does not apply.


**sequence**: Dictionary screen + repository: M24 → M52 → **M64** → M119

---

### M65. sync: route SyncActivity's raw clock reads through the injected TimeProvider

**rating 60**: evidence 10 · impact 2 · feasibility 10 · `C46`  
**plans**: Claude 48 · Devin 74 · Codex 68  
**proposed by**: devin swe 2 `M-dev-3` (1.00)

**verified**: ui/sync/SyncActivity.kt:617 System.currentTimeMillis(), :649 and :735 Date().time, :737 Date().time inside getRelativeTime while timeProvider is already injected.

**do**: Replace the four with timeProvider.now(); drop the unused Date import.


**sequence**: SyncActivity: M2 → **M65**

---

### M66. data: remove android.util.Log from RetryRepositoryImpl

**rating 58**: evidence 10 · impact 2 · feasibility 9 · `C50`  
**plans**: Claude 47 · Devin 79 · Codex 80  
**proposed by**: copilot grok 4.6 `M-grk-4` (1.00), codex sol 5.6 `R-cdx-5` (0.90), copilot grok 4.6 `R-grk-9` (0.80), copilot gemini 3.8 flash `M-gem-7` (0.60)

**verified**: repository/RetryRepositoryImpl.kt imports android.util.Log for outcome logging in executeOperation; outcomes are already persisted (markFailed) and returned as RetryOperationResult.

**do**: Remove Log/TAG and the test's Log stubs; keep 409-success, 5xx-retryable, other-terminal, cancellation semantics. Optional (grok): parse with the injected Json.


**sequence**: RetryRepositoryImpl / RetryDao / RetryQueueWorker: M31 → M49 → M55 → **M66**

---

### M67. data: make RemovedLogDao.getRemovedDocIds return non-null ids

**rating 57**: evidence 9 · impact 2 · feasibility 10 · `C49`  
**plans**: Claude 47 · Devin 74 · Codex 60  
**proposed by**: claude opus 5.5 `M-cld-1` (1.00)

**verified**: data/room/dao/RemovedLogDao.kt:25-26 returns List<String?> (claude cites a KSP 'meaningless nullable type argument' warning from CI; not re-run here); RemovedLog.docId is nullable.

**do**: Append AND docId IS NOT NULL, return List<String>; add a RemovedLogDaoTest case with a null docId.


---

### M68. data: strip android.util.Log from GsonUtils

**rating 55**: evidence 9 · impact 2 · feasibility 9 · `C51`  
**plans**: Claude 46 · Devin 79 · Codex 68  
**proposed by**: copilot grok 4.6 `M-grk-6` (1.00)

**verified**: utils/GsonUtils.kt logFallback (Log.isLoggable/Log.d) and extractSharedTeamName (Log.w); GsonUtilsNoLogStubTest documents that fallbacks must work without Log.

**do**: Remove the import; silent fallbacks.

**watch out**: Same file as the GsonUtils inlining task (which leaves Log alone) - land them in sequence.


**sequence**: GsonUtils.kt: M12 → **M68**

---

### M69. sync: route shelf-sync failures through SyncTimeLogger instead of android.util.Log

**rating 55**: evidence 9 · impact 2 · feasibility 9 · `C56`  
**plans**: Claude 46 · Devin 79 · Codex 69  
**proposed by**: codex sol 5.6 `R-cdx-6` (1.00)

**verified**: repository/SyncRepositoryImpl.kt Log.e in processShelfParallel (:106) and processShelfDataOptimizedSync while SyncTimeLogger (with logDetail) is injected.

**do**: Replace with syncTimeLogger.logDetail; remove the import; verify the failure is logged once.


**sequence**: SyncRepositoryImpl / SyncManager: M32 → M40 → **M69** → M77

---

### M70. download: derive the 404 diagnostic URL from the request instead of regex over response.toString()

**rating 55**: evidence 9 · impact 2 · feasibility 9 · `C53`  
**plans**: Claude 46 · Devin 77 · Codex 69  
**proposed by**: codex sol 5.6 `R-cdx-3` (1.00), copilot gemini 3.8 flash `P-gem-4` (0.70)

**verified**: repository/DownloadRepositoryImpl.kt:22-24 URL_REGEX; :53-64 parses response.toString() to log the 404 URL.

**do**: Log response.raw().request.url (fall back to the url argument); delete URL_REGEX and the exception-driven branch; test a 404 and a diagnostics-write failure.


---

### M71. data: use the injected TimeProvider for the multi-submission report timestamp

**rating 55**: evidence 10 · impact 1 · feasibility 10 · `C80`  
**plans**: Claude 35 · Devin 75 · Codex 69  
**proposed by**: codex sol 5.6 `M-cdx-10` (1.00)

**verified**: repository/SubmissionsRepositoryExporter.kt multi-report header uses Instant.now() although TimeProvider is injected and used for single-report filenames.

**do**: Use Instant.ofEpochMilli(timeProvider.now()); make the exporter test assert a fixed header.


**sequence**: SubmissionsRepositoryExporter.kt: M54 → **M71**

---

### M72. code-health: collapse TimeProvider's duplicate sleep override

**rating 55**: evidence 10 · impact 1 · feasibility 10 · `C79`  
**plans**: Claude 35 · Devin 72 · Codex 68  
**proposed by**: copilot kimi k3 `M-kim-2` (1.00)

**verified**: utils/TimeProvider.kt: SystemTimeProvider.sleep repeats the interface default Thread.sleep(millis).

**do**: Delete the override.


---

### M73. data: dedupe ids before chunking in the remaining DAO bulk wrappers

**rating 55**: evidence 9 · impact 2 · feasibility 9 · `new, N1`  
**plans**: Claude — · Devin 80 · Codex 68  
**proposed by**: codex sol 5.6 `P-cdx-4`,`P-cdx-6`..`P-cdx-9` (1.00)

**verified**: These wrappers chunk the raw input without distinct(): AnswerDao.kt:18,29; MeetupDao.kt:19; RemovedLogDao.kt:20 (deleteByTypeUserAndDocsChunked, which also lacks the isEmpty fast path its siblings have); OfflineActivityDao.kt:42,47; NotificationDao.kt:44,52,64,124; MyLibraryDao.kt:166,204. Their siblings (QuestionDao.getByExamIds, MyLibraryDao :61/:72/:89) already do distinct().chunked(900).

**do**: Add distinct() (and the isEmpty early return where missing) before chunked(...). Add one DAO test per file with a list that repeats ids across a chunk boundary.

**watch out**: Gains are redundant queries only; a 900-chunk never exceeds the bind limit with or without duplicates. sumOf-returning deletes keep the same count because SQL IN already dedupes. Land after task #3 (MyLibraryDao) and #8 (NotificationDao).

**sequence**: NotificationDao / NotificationsRepositoryImpl: M3 → M17 → M53 → M58 → M61 → M62 → **M73**; MyLibraryDao / QuestionDao: M1 → **M73**

---

### M74. code-health: replace printStackTrace() with the app's logging, package by package

**rating 49**: evidence 9 · impact 2 · feasibility 6 · `new, N2`  
**plans**: Claude — · Devin 76 · Codex 65  
**proposed by**: devin swe 2 `M-dev-4`,`M-dev-5`,`M-dev-6` (1.00)

**verified**: 57 printStackTrace() calls in 25 main-source files (grep on master); on-device they go to stderr with no tag, so they never show up in the app's diagnostics/log upload.

**do**: One PR per package (utils, base, ui/health, ui/chat, ...): route to the existing logging path or delete where the error is already surfaced. No behaviour change otherwise.

**watch out**: Skip files touched by the open PRs listed in the plan header. The dead IllegalAccessException catch in Utilities.showToastIfValid is task #107; land that first.

**sequence**: Utilities.kt: M46 → **M74** → M111

---

### M75. sync: snapshot SyncTimeLogger aggregates once per summary

**rating 48**: evidence 8 · impact 2 · feasibility 7 · `C70`  
**plans**: Claude 41 · Devin 75 · Codex 68  
**proposed by**: codex sol 5.6 `P-cdx-3` (1.00)

**verified**: utils/SyncTimeLogger.kt:235-305 generateSummary re-locks and re-sums each log list several times, and sortedByDescending's comparator re-sums inside synchronized on every comparison.

**do**: Build immutable per-key aggregates once under one lock each, derive totals/sort/rows from them; keep the report text identical.

**watch out**: Coordinate with the lock-free append task (same file); land one first.


**sequence**: SyncTimeLogger.kt: **M75** → M76

---

### M76. sync: drop per-event synchronizedList wrappers in SyncTimeLogger

**rating 43**: evidence 8 · impact 1 · feasibility 7 · `C110`  
**plans**: Claude 30 · Devin 75 · Codex 68  
**proposed by**: devin swe 2 `P-dev-6` (1.00)

**verified**: utils/SyncTimeLogger.kt:170,187 computeIfAbsent { Collections.synchronizedList(...) }.add per API/DB event.

**do**: Use ConcurrentLinkedQueue values.

**watch out**: Uncontended locks cost nanoseconds next to network calls; generateSummary's synchronized blocks must change with it.


**sequence**: SyncTimeLogger.kt: M75 → **M76**

---

### M77. sync: build shelf-sync request bodies with kotlinx directly

**rating 42**: evidence 6 · impact 2 · feasibility 7 · `C76`  
**plans**: Claude 38 · Devin 77 · Codex 69  
**proposed by**: copilot gemini 3.8 flash `P-gem-3` (0.60)

**verified**: repository/SyncRepositoryImpl.kt:131-143 builds a Gson JsonObject (gson.toJsonTree) then converts it with toKotlinx() per batch.

**do**: buildJsonObject { putJsonArray("keys") { ... } } passed straight to postDoc.

**watch out**: Drop gemini's Semaphore(2): 'unbounded parallelism' is four shelf types; throttling them only slows sync.


**sequence**: SyncRepositoryImpl / SyncManager: M32 → M40 → M69 → **M77**

---

## Wave 5 — Tests and CI

Faster, less flaky suites and better CI signal.

### M78. tests: add ReplyViewModel.getNewsWithReplies coverage

**rating 60**: evidence 9 · impact 3 · feasibility 9 · `C25`  
**plans**: Claude 55 · Devin 74 · Codex 69  
**proposed by**: copilot kimi k3 `R-kim-9` (1.00)

**verified**: No ReplyViewModelTest exists under app/src/test/.../ui/voices/; ReplyViewModel.getNewsWithReplies (:14) returns Pair<News?, List<News>>.

**do**: Add tests: parent with ordered replies, missing parent, repository error.


---

### M79. test-infra: separate warm-up outliers from real class cost in the CI slow-test summary

**rating 54**: evidence 7 · impact 3 · feasibility 9 · `C39`  
**plans**: Claude 51 · Devin 65 · Codex 57  
**proposed by**: claude opus 5.5 `M-cld-10` (1.00)

**verified**: .github/scripts/test_timing_summary.py flags warm-up per test (:95, >5x class median with >=3 tests) but ranks classes by raw total (:98), so classes whose total is one warm-up test top the table.

**do**: Sum warm-up time per class, sort by elapsed minus warm-up, add a warm-up column and a total line; keep --warn-over on the raw total.

**watch out**: Call it 'first-test warm-up', not 'Robolectric': CourseRatingUtilsTest has no Robolectric runner (its spike is MockK/agent instrumentation).


**sequence**: test_timing_summary.py: **M79** → M80

---

### M80. test-infra: warn on individual slow test classes in test_timing_summary.py

**rating 52**: evidence 8 · impact 2 · feasibility 9 · `C61`  
**plans**: Claude 44 · Devin 65 · Codex 57  
**proposed by**: copilot kimi k3 `M-kim-7` (1.00)

**verified**: .github/scripts/test_timing_summary.py only warns on the shard total (--warn-over); no per-class threshold.

**do**: Print a slow-class warning row above a class threshold (e.g. 15 s); stdlib only; do not touch test.yml (PR-owned).


**sequence**: test_timing_summary.py: M79 → **M80**

---

### M81. tests: drop Robolectric and the real 2 s sleep from RetryInterceptorTest

**rating 50**: evidence 8 · impact 2 · feasibility 8 · `C66`  
**plans**: Claude 43 · Devin 72 · Codex 63  
**proposed by**: copilot kimi k3 `M-kim-1` (1.00)

**verified**: app/src/test/.../data/api/RetryInterceptorTest.kt:28-29 runs under RobolectricTestRunner; testInterruptedExceptionDuringDelay (:217-222) uses SystemTimeProvider with initialDelay=2000 and Thread.sleep(100). app/build.gradle sets returnDefaultValues = true, so RetryInterceptor's Intent construction works on the plain JVM.

**do**: Remove the runner/@Config; replace the real-time case with a fake TimeProvider whose sleep interrupts.


---

### M82. tests: clear the compiler warnings in three test files

**rating 50**: evidence 9 · impact 1 · feasibility 9 · `C91`  
**plans**: Claude 33 · Devin 68 · Codex 55  
**proposed by**: claude opus 5.5 `M-cld-9` (1.00)

**verified**: TeamChatBadgeIntegrationTest.kt:43 unopted UnconfinedTestDispatcher; ServerAddressAdapterTest.kt:54,65 useless casts; FileUtilsTest.kt:456,493 deprecations.

**do**: Add the opt-in, drop the casts, use IntentCompat.getParcelableExtra, remove or suppress the shadowOf(MimeTypeMap) line.


---

### M83. ci: sparse-checkout only .github/scripts in playstore.yml

**rating 50**: evidence 8 · impact 2 · feasibility 8 · `C64`  
**plans**: Claude 43 · Devin 67 · Codex 68  
**proposed by**: copilot grok 4.6 `M-grk-1` (1.00)

**verified**: .github/workflows/playstore.yml:54-57 checks out the full repo (fetch-depth 1) for a job that only runs .github/scripts/playstore*.sh; the AAB comes from gh release download.

**do**: sparse-checkout .github/scripts, persist-credentials: false; nothing else changes.


**sequence**: playstore.yml / playstore.sh: **M83** → M84

---

### M84. ci: read release step conclusions from the jobs listing in playstore.sh

**rating 39**: evidence 6 · impact 1 · feasibility 8 · `C112`  
**plans**: Claude 29 · Devin 68 · Codex 68  
**proposed by**: copilot gemini 3.8 flash `M-gem-1` (1.00)

**verified**: .github/scripts/playstore.sh:96-102 lists runs/$id/jobs then calls actions/jobs/$job_id for the same job's steps; the listing already includes steps[].

**do**: Select the step conclusion from the first response.

**watch out**: It is one redundant call per check, not a per-job loop.


**sequence**: playstore.yml / playstore.sh: M83 → **M84**

---

### M85. tests: make CourseRatingUtilsTest cheap by testing pure formatting

**rating 37**: evidence 5 · impact 2 · feasibility 6 · `C81`  
**plans**: Claude 35 · Devin 71 · Codex 63  
**proposed by**: copilot kimi k3 `M-kim-6` (0.50)

**verified**: app/src/test/.../utils/CourseRatingUtilsTest.kt (plain JUnit, no Robolectric) relaxed-mocks Context/TextView/AppCompatRatingBar; one test carries a ~15-20 s first-test spike.

**do**: Use kimi's fallback: extract formatting into a pure CourseRatingUtils function and test it without widget mocks.

**watch out**: Kimi's primary fix (relaxed=false) still instruments the widget classes, so it would not remove the cost.


---

### M86. tests: set up Log static mocking once in ConfigurationsRepositoryImplTest

**rating 37**: evidence 5 · impact 2 · feasibility 6 · `C82`  
**plans**: Claude 35 · Devin 70 · Codex 63  
**proposed by**: copilot gemini 3.8 flash `M-gem-4` (1.00)

**verified**: ConfigurationsRepositoryImplTest.kt:84-93 mockkStatic/unmockkStatic(Log) per test across 42 tests.

**do**: Move to class-level setup.

**watch out**: Speedup is unmeasured; class-level static mocks can leak between tests.


---

### M87. tests: set up WorkManager static mocks once in RetryQueueWorkerTest

**rating 37**: evidence 5 · impact 2 · feasibility 6 · `C83`  
**plans**: Claude 35 · Devin 70 · Codex 63  
**proposed by**: copilot gemini 3.8 flash `M-gem-5` (1.00)

**verified**: RetryQueueWorkerTest.kt:63-91 mockkObject/mockkStatic per test with unmockkAll.

**do**: Class-level mocks + clearMocks per test.

**watch out**: Speedup is unmeasured.


---

### M88. tests: measure UploadManagerTest before restructuring it

**rating 34**: evidence 4 · impact 2 · feasibility 6 · `new, N7`  
**plans**: Claude — · Devin 62 · Codex 63  
**proposed by**: copilot kimi k3 `M-kim-5` (1.00)

**verified**: app/src/test/.../services/UploadManagerTest.kt is 433 lines / 24 tests; kimi's ~45 s runtime is not reproduced here.

**do**: Read the class time from the CI slow-test summary (#39) first; only if it is a top-5 class, remove the specific Robolectric/real-time cause.

**watch out**: Measurement-gated: if it is not slow, close this task.

---

## Wave 6 — Code-health tail

Each task is tiny. Batch them into a few sweep PRs by package rather than one PR each.

### M89. code-health: remove redundant null-handling in personals, ratings and login-team code

**rating 52**: evidence 9 · impact 1 · feasibility 10 · `C84`  
**plans**: Claude 34 · Devin 70 · Codex 68  
**proposed by**: claude opus 5.5 `M-cld-3` (1.00)

**verified**: ui/personals/PersonalsFragment.kt:119/:136 personal.id ?: personal._id + if (id != null); ui/ratings/RatingsViewModel.kt:63 user.id?.takeIf; ui/sync/LoginActivity.kt:467 team._id != null (all non-null String).

**do**: Drop the dead null branches without inventing new fallbacks.


**sequence**: LoginActivity: M15 → **M89**

---

### M90. code-health: remove redundant null-handling on non-null ids (upload, notifications, labels)

**rating 52**: evidence 9 · impact 1 · feasibility 10 · `C85`  
**plans**: Claude 34 · Devin 70 · Codex 68  
**proposed by**: claude opus 5.5 `M-cld-2` (1.00)

**verified**: services/upload/UploadConfigs.kt:195 exam.id ?: "" (StepExam.id: String); NotificationsRepositoryImpl.kt:325 user.id?.let (UserEntity.id: String); services/VoicesLabelManager.kt:82-83 voiceId != null (News.id: String).

**do**: Use the ids directly.


---

### M91. code-health: add a shared debounceDistinct operator and clear two FlowPreview warnings

**rating 50**: evidence 9 · impact 1 · feasibility 9 · `C88`  
**plans**: Claude 33 · Devin 71 · Codex 60  
**proposed by**: claude opus 5.5 `M-cld-7` (1.00)

**verified**: ui/chat/ChatHistoryFragment.kt:89 .debounce(300) and ui/resources/CollectionsFragment.kt:97 .debounce(300L) without @OptIn(FlowPreview).

**do**: Add Flow<T>.debounceDistinct(ms) with the opt-in to utils/ViewExtensions.kt; use it in the two fragments; add a ViewExtensionsTest case.


---

### M92. community: drop the redundant dispatcher hops in CommunityServicesViewModel and LeadersViewModel

**rating 50**: evidence 9 · impact 1 · feasibility 9 · `C89`  
**plans**: Claude 33 · Devin 71 · Codex 70  
**proposed by**: copilot grok 4.6 `R-grk-8` (1.00)

**verified**: ui/community/CommunityServicesViewModel.kt:30-36 wraps suspend DAO-backed getTeamLinks/isMember in launch(io)/withContext(io).

**do**: Call them directly; drop DispatcherProvider if unused.
 Same for ui/community/LeadersViewModel.kt:29 viewModelScope.launch(dispatcherProvider.default) around the suspend configurationsRepository.getCommunityLeaders() (copilot grok 4.6 `R-grk-10`).

---

### M93. upload: deduplicate DownloadUtils' twin enqueue blocks

**rating 50**: evidence 9 · impact 1 · feasibility 9 · `C92`  
**plans**: Claude 33 · Devin 74 · Codex 68  
**proposed by**: devin swe 2 `M-dev-7` (1.00)

**verified**: utils/DownloadUtils.kt:146-174 openPriorityDownloadService/openDownloadService repeat get-set-merge-write; ~8 trailing blank lines at EOF.

**do**: Extract a private enqueueUrls(context, key, urls); delete trailing blanks.


**sequence**: DownloadService / DownloadUtils: M26 → **M93**

---

### M94. health: drop dead string concatenations and reorder HealthExaminationAdapter's diff

**rating 47**: evidence 8 · impact 1 · feasibility 9 · `C94`  
**plans**: Claude 32 · Devin 74 · Codex 69  
**proposed by**: copilot kimi k3 `P-kim-7` (1.00)

**verified**: ui/health/HealthExaminationAdapter.kt:126-132 value.toString() + ""; DIFF_CALLBACK :178-191 compares examination.data mid-chain.

**do**: Drop the + ""; compare data last.


**sequence**: Health screens: M28 → M35 → M48 → M56 → M59 → **M94**

---

### M95. login: return early from LoginSyncManager.isManager

**rating 47**: evidence 8 · impact 1 · feasibility 9 · `C95`  
**plans**: Claude 32 · Devin 68 · Codex 68  
**proposed by**: copilot grok 4.6 `M-grk-8` (1.00)

**verified**: services/sync/LoginSyncManager.kt:141-150 scans every role and only then checks isUserAdmin.

**do**: Return on isUserAdmin or on the first manager role.


---

### M96. ui: cache formatted dates and hoist listeners in SubmissionsListAdapter and FeedbackReplyAdapter

**rating 47**: evidence 8 · impact 1 · feasibility 9 · `C97`  
**plans**: Claude 32 · Devin 78 · Codex 68  
**proposed by**: devin swe 2 `P-dev-3` (1.00), copilot kimi k3 `P-kim-5` (0.50)

**verified**: ui/submissions/SubmissionsListAdapter.kt:35,40-46 formats the date and allocates two listeners per bind; ui/feedback/FeedbackReplyAdapter.kt:21-23 parses and formats per bind.

**do**: Add date caches (bounded for the reply adapter) and move listeners to init.


---

### M97. ui: cache per-bind resource lookups in Onboarding, CoursesSteps and ChatShareTarget adapters

**rating 47**: evidence 8 · impact 1 · feasibility 9 · `C98`  
**plans**: Claude 32 · Devin 78 · Codex 73  
**proposed by**: devin swe 2 `P-dev-10` (1.00)

**verified**: ui/onboarding/OnboardingAdapter.kt:28,30 getColor x2 per page; ui/courses/CoursesStepsAdapter.kt:53 getString per bind; ui/chat/ChatShareTargetAdapter.kt:38,41 listeners per bind.

**do**: Hoist the color, cache the template, move the listeners to init.


---

### M98. ui: hoist per-bind listeners in PersonalsAdapter, StorageCategoryDetail's ResourceAdapter and ReferencesAdapter

**rating 47**: evidence 8 · impact 1 · feasibility 9 · `C99`  
**plans**: Claude 32 · Devin 78 · Codex 63  
**proposed by**: devin swe 2 `P-dev-4` (0.70), copilot kimi k3 `P-kim-8` (0.60)

**verified**: ui/personals/PersonalsAdapter.kt:40-63 allocates four listeners per bind; ui/settings/StorageCategoryDetailFragment.kt:185-207 two per bind plus duplicates in the payload bind; ui/references/ReferencesAdapter.kt:28-36 one per bind.

**do**: Move listeners to ViewHolder init resolving the item via bindingAdapterPosition with NO_POSITION guards.


---

### M99. ui: hoist the startSurvey listener and label strings in SurveysAdapter

**rating 47**: evidence 8 · impact 1 · feasibility 9 · `C100`  
**plans**: Claude 32 · Devin 78 · Codex 62  
**proposed by**: devin swe 2 `P-dev-5` (1.00)

**verified**: ui/surveys/SurveysAdapter.kt:86 startSurvey listener per bind; :103-105 three getString per bind.

**do**: Move the listener to init; cache the labels.


---

### M100. life: drop the redundant IO hops in LifeViewModel

**rating 45**: evidence 8 · impact 1 · feasibility 8 · `C101`  
**plans**: Claude 31 · Devin 71 · Codex 70  
**proposed by**: copilot grok 4.6 `R-grk-7` (1.00)

**verified**: ui/life/LifeViewModel.kt:35,45,55 wrap suspend LifeRepository calls in withContext(io).

**do**: Call them directly; drop DispatcherProvider if unused.

**watch out**: updateVisibility/updateMyLifeListOrder also run LifeCache JSON encoding; tiny, but it moves to main.


**sequence**: Life screen: M60 → **M100** → M101 → M102 → M115

---

### M101. life: hoist LifeAdapter's per-bind listeners onto the ViewHolder

**rating 45**: evidence 8 · impact 1 · feasibility 8 · `C102`  
**plans**: Claude 31 · Devin 78 · Codex 62  
**proposed by**: copilot grok 4.6 `P-grk-9` (1.00)

**verified**: ui/life/LifeAdapter.kt:62-75 installs click/touch/visibility listeners per bind (7-row list).

**do**: Create listeners once per holder; resolve the item with bindingAdapterPosition.


**sequence**: Life screen: M60 → M100 → **M101** → M102 → M115

---

### M102. life: make LifeCache entries immutable and stop copying on every read

**rating 45**: evidence 8 · impact 1 · feasibility 8 · `C103`  
**plans**: Claude 31 · Devin 72 · Codex 82  
**proposed by**: copilot grok 4.6 `P-grk-5` (1.00)

**verified**: repository/LifeCache.kt:29-41 read() returns map { it.copy() } on every hit because CachedMyLifeItem fields are var.

**do**: Make the fields val, return the cached list, replace the mutation test with an immutability test.


**watch out**: Codex's 'a corrupt entry poisons future reads' is wrong: a parse failure returns before memoryCache is written. Bounding the map is optional (keys are per user).

**sequence**: Life screen: M60 → M100 → M101 → **M102** → M115

---

### M103. ui: hoist per-bind listeners and constant styling in ResourcesTagsAdapter

**rating 45**: evidence 8 · impact 1 · feasibility 8 · `C104`  
**plans**: Claude 31 · Devin 78 · Codex 62  
**proposed by**: devin swe 2 `P-dev-7` (1.00)

**verified**: ui/resources/ResourcesTagsAdapter.kt:79,85,99 listeners per bind; :97-98 cached colors re-applied per bind; :116-118 checkbox listener re-allocated.

**do**: Move listeners and constant colors to init; one shared checkbox listener.


---

### M104. ui: hoist per-bind listeners in ChatHistory, Checkbox and TeamsSelection adapters

**rating 45**: evidence 8 · impact 1 · feasibility 8 · `C105`  
**plans**: Claude 31 · Devin 78 · Codex 62  
**proposed by**: devin swe 2 `P-dev-8` (1.00)

**verified**: ui/chat/ChatHistoryAdapter.kt:123,137-139 (incl. constant setImageResource); ui/components/CheckboxAdapter.kt:39; ui/teams/TeamsSelectionAdapter.kt:42-46.

**do**: Move listeners and the constant drawable to init.


---

### M105. ui: hoist per-bind listeners in NotificationsAdapter

**rating 45**: evidence 8 · impact 1 · feasibility 8 · `C106`  
**plans**: Claude 31 · Devin 78 · Codex 62  
**proposed by**: devin swe 2 `P-dev-9` (1.00)

**verified**: ui/notifications/NotificationsAdapter.kt:104,134,140-145 allocate listeners per bind.

**do**: Move them to the two ViewHolders' init keeping the conditional callbacks.


---

### M106. crypto: reuse one SecureRandom in AndroidDecrypter.generateIv

**rating 43**: evidence 8 · impact 1 · feasibility 7 · `new, N5`  
**plans**: Claude — · Devin 75 · Codex 73  
**proposed by**: copilot gemini 3.8 flash `P-gem-7` (1.00)

**verified**: utils/AndroidDecrypter.kt:116-117 allocates SecureRandom() per generateIv call.

**do**: Hold one private SecureRandom in the object; leave the rest of the cipher code as it is.

**watch out**: Do NOT 'drop the redundant key/iv arrays' (:22-28): copying into fixed ByteArray(16)/ByteArray(32) defines the truncation and short-input exception behaviour of stored keys. This is security code; the gain is tiny, so keep the diff to the SecureRandom field.

---

### M107. courses: make CoursesPagerAdapter.containsItem a set lookup

**rating 42**: evidence 7 · impact 1 · feasibility 8 · `C108`  
**plans**: Claude 30 · Devin 73 · Codex 68  
**proposed by**: codex sol 5.6 `M-cdx-2` (1.00)

**verified**: ui/courses/CoursesPagerAdapter.kt:67-69 scans all steps per containsItem call (lists are tens of steps).

**do**: Maintain a membership set rebuilt in submitList.


---

### M108. dashboard: configure the activities chart once, update data per emission

**rating 42**: evidence 7 · impact 1 · feasibility 8 · `C109`  
**plans**: Claude 30 · Devin 74 · Codex 68  
**proposed by**: copilot kimi k3 `P-kim-9` (1.00)

**verified**: ui/dashboard/ActivitiesFragment.kt renderChart re-applies axis/legend/ValueFormatter on every monthlyLoginCounts emission (emissions are rare).

**do**: Split into configureChartOnce + updateChartData.


---

### M109. teams: make TeamPagerAdapter.containsItem a set lookup

**rating 42**: evidence 7 · impact 1 · feasibility 8 · `C111`  
**plans**: Claude 30 · Devin 73 · Codex 68  
**proposed by**: codex sol 5.6 `M-cdx-3` (1.00)

**verified**: ui/teams/TeamPagerAdapter.kt:62-64 pages.any per call (a dozen pages at most).

**do**: Maintain a membership set updated in updatePages.


---

### M110. tts: skip TTSManager.stripMarkdown's regex chain when the text has no markdown characters

**rating 42**: evidence 7 · impact 1 · feasibility 8 · `new, N6`  
**plans**: Claude — · Devin 77 · Codex 68  
**proposed by**: copilot gemini 3.8 flash `P-gem-8` (1.00)

**verified**: utils/TTSManager.kt:99-110 runs ten replace() passes per utterance; the regexes are already precompiled companion vals (:88-97), so only the early exit remains.

**do**: Return the trimmed text early when it contains none of `#*_[]>|-+0-9.`; add StripMarkdown tests for plain text and each regex.

**watch out**: The 'StringBuilder in formatCsvForSpeech' half is optional style.

---

### M111. code-health: fix the dead toast catch and qualified delay in utils

**rating 41**: evidence 6 · impact 1 · feasibility 9 · `C107`  
**plans**: Claude 30 · Devin 76 · Codex 65  
**proposed by**: devin swe 2 `M-dev-4` (1.00)

**verified**: utils/Utilities.kt:62 catches IllegalAccessException that Toast.show never throws; RetryUtils.kt:26 calls kotlinx.coroutines.delay fully qualified; KeyboardUtils.kt:16 and RetryUtils.kt:29 printStackTrace.

**do**: Catch Exception (log via Log.w), import delay.

**watch out**: The 'stderr is lost in release logcat' rationale is wrong: Android routes System.err to logcat; the printStackTrace swaps are cosmetic.
 The wider printStackTrace() sweep is its own task (see N2); land this one first.

**sequence**: Utilities.kt: M46 → M74 → **M111**

---

### M112. storage: pass FreeSpaceWorker's batch without a defensive toSet() copy

**rating 40**: evidence 7 · impact 1 · feasibility 7 · `C113`  
**plans**: Claude 29 · Devin 72 · Codex 68  
**proposed by**: codex sol 5.6 `M-cdx-6` (1.00)

**verified**: services/FreeSpaceWorker.kt:47-49 copies <=25 ids with toSet() before each markResourcesAsNotOffline call.

**do**: Pass the set and clear after the call returns; capture arguments in the test.

**watch out**: The copy is a cheap safety snapshot; the gain is negligible.


---

### M113. data: reuse empty JsonObject/JsonArray singletons in JsonUtils

**rating 38**: evidence 5 · impact 1 · feasibility 9 · `C114`  
**plans**: Claude 28 · Devin 70 · Codex 68  
**proposed by**: copilot gemini 3.8 flash `P-gem-9` (0.50)

**verified**: utils/JsonUtils.kt getJsonObject/getJsonArray allocate JsonObject(emptyMap())/JsonArray(emptyList()) per miss (immutable, safe to share).

**do**: private val EMPTY_* constants.

**watch out**: The 'numeric fast path' part is already there (longOrNull ?: ... short-circuits); JsonUtils is used in 10 files, not 'all DAOs'.


---

### M114. courses: bind course covers with the ImageView as Glide's lifecycle owner

**rating 36**: evidence 5 · impact 1 · feasibility 8 · `C115`  
**plans**: Claude 27 · Devin 68 · Codex 68  
**proposed by**: copilot grok 4.6 `P-grk-7` (1.00)

**verified**: utils/CoursesItemUtils.kt:111 Glide.with(context) instead of the ImageView.

**do**: Glide.with(ivCover).

**watch out**: Grok's second claim is wrong: the when() at :92-101 already prefers the measured width and only computes columns when unmeasured.


---

### M115. life: replace LifeAdapter's getIdentifier lookup with a compile-time map

**rating 36**: evidence 5 · impact 1 · feasibility 8 · `C117`  
**plans**: Claude 27 · Devin 66 · Codex 68  
**proposed by**: copilot kimi k3 `P-kim-6` (1.00)

**verified**: ui/life/LifeAdapter.kt:52-57 resources.getIdentifier(imgId, "drawable", ...) (already memoized by drawableCache, so this is lint/shrinker hygiene, not a hot path).

**do**: Map the seven known keys to R.drawable ids; keep getIdentifier as the fallback.

**watch out**: Kimi's resId=0 rule is self-contradictory: skipping setImageResource on a miss leaves a recycled holder's old icon instead of clearing it.


**sequence**: Life screen: M60 → M100 → M101 → M102 → **M115**

---

### M116. retry: make the RetryInterceptor retry-safety check and broadcast extras cheaper

**rating 34**: evidence 5 · impact 1 · feasibility 7 · `C118`  
**plans**: Claude 26 · Devin 68 · Codex 68  
**proposed by**: copilot grok 4.6 `M-grk-7` (1.00)

**verified**: data/api/RetryInterceptor.kt:27-30 endsWith over three suffixes; :62-65 Intent extras with the full URL.

**do**: pathSegments check; put encodedPath in the extra.

**watch out**: Better follow-up: ACTION_RETRY_EVENT has no receiver anywhere in app/src/main, so the broadcast itself is dead code.


---

## Wave 7 — Needs a decision before code

Do not start until the question in the card is answered.

### M117. sync: run AutoSyncWorker's independent uploads concurrently

**rating 55**: evidence 8 · impact 5 · feasibility 3 · `C38`  
**plans**: Claude 51 · Devin 78 · Codex 75  
**proposed by**: devin swe 2 `P-dev-2` (1.00)

**decide first**: Audit UploadManager's shared state and the implicit upload ordering (exam results before submissions, activities last) before parallelising.

**verified**: services/AutoSyncWorker.kt:114-130 calls 17 uploadManager.upload*() functions strictly in sequence inside the isSyncRunning gate.

**do**: supervisorScope + async per upload with per-upload runCatching (rethrow CancellationException), awaitAll, then setLastSync.

**watch out**: High risk: UploadManager shares state across uploads and some orderings may be implicit (exam results before submissions, activities last); needs an ordering audit before parallelising.


---

### M118. teams: unify leader hand-off before member removal in RequestsViewModel

**rating 52**: evidence 8 · impact 4 · feasibility 4 · `C43`  
**plans**: Claude 49 · Devin 66 · Codex 68  
**proposed by**: devin swe 2 `R-dev-9` (1.00)

**decide first**: Product: may a sole leader leave a team with no successor? Also RequestsViewModelTest is touched by open PR #17776.

**verified**: ui/teams/members/RequestsViewModel.kt leaveTeam (:76-89) promotes a candidate and removes unconditionally; removeMember on self (:91-111) emits CannotRemoveLastLeader when no candidate. Neither checks whether the actor is actually a leader.

**do**: Extract one hand-off helper used by both.

**watch out**: Blocking leaveTeam when there is no successor traps a sole member in the team (product decision), and RequestsViewModelTest is owned by open PR #17776.


---

### M119. dictionary: assign deterministic dictionary ids instead of a UUID per word

**rating 36**: evidence 7 · impact 1 · feasibility 5 · `C116`  
**plans**: Claude 27 · Devin 76 · Codex 76  
**proposed by**: copilot grok 4.6 `P-grk-10` (1.00)

**decide first**: Data: does the dictionary JSON contain repeated words? A deterministic key plus @Insert(REPLACE) would silently collapse them.

**verified**: repository/DictionaryMapper.kt:15 UUID.randomUUID() per word during the one-time seed.

**do**: Derive the id from existing fields.

**watch out**: If the JSON contains repeated words, a deterministic key plus @Insert(REPLACE) silently collapses them; check the data first.


**sequence**: Dictionary screen + repository: M24 → M52 → M64 → **M119**

---

## Wave 8 — Blocked by open PRs

Valid tasks whose files are owned by an open PR. Start them only when that PR merges or closes.

### M120. chat: narrow ChatViewModel's voices/teams dependencies to share-only interfaces

**rating 45**: evidence 8 · impact 3 · feasibility 3 · `C71`  
**plans**: Claude 40 · Devin 68 · Codex 68  
**proposed by**: copilot kimi k3 `R-kim-1` (1.00)

**blocked by**: open PR #15808 (and the Voices/Teams Impls)

**verified**: ui/chat/ChatViewModel.kt uses VoicesRepository only for getPlanetNewsMessages/isAlreadyShared/createNews (:136,:276,:279) and TeamsRepository only for three summary getters (:173-181).

**do**: Introduce narrow interfaces implemented by the existing Impls.

**watch out**: Kimi's plan edits VoicesRepositoryImpl, TeamsRepositoryImpl and RepositoryModule, all owned by open PRs; deliver via interface extension + a new module instead.


**sequence**: ChatViewModel / ChatDetailFragment / ChatSearch: M9 → M23 → M37 → M45 → **M120**

---

### M121. data: extract submission upload serialization into SubmissionsUploadSerializer

**rating 45**: evidence 8 · impact 3 · feasibility 3 · `C72`  
**plans**: Claude 40 · Devin 67 · Codex 69  
**proposed by**: copilot kimi k3 `R-kim-6` (1.00)

**blocked by**: open PR #14427, #15808

**verified**: repository/SubmissionsRepositoryImpl.kt (941 lines) getExamUploadPayload (:793) and serializeSubmission (:864).

**do**: Concrete @Inject collaborator mirroring SubmissionsRepositoryExporter.

**watch out**: File owned by open PRs #14427/#15808.


---

### M122. di: replace NotificationUtils' hidden entry-point singleton with an injectable binding

**rating 45**: evidence 8 · impact 3 · feasibility 3 · `C73`  
**plans**: Claude 40 · Devin 69 · Codex 69  
**proposed by**: copilot kimi k3 `R-kim-8` (1.00)

**blocked by**: open PR #15825, #15808

**verified**: utils/NotificationUtils.kt:96-104 caches a NotificationManager and fetches TimeProvider via EntryPointAccessors.

**do**: Provide it via Hilt; inject into callers that allow it.

**watch out**: NotificationUtils(+Test) owned by #15825, ServiceModule by #15808.


---

### M123. voices: move ReplyActivity's member-detail fetch into ReplyViewModel

**rating 45**: evidence 8 · impact 3 · feasibility 3 · `C75`  
**plans**: Claude 40 · Devin 68 · Codex 69  
**proposed by**: copilot kimi k3 `R-kim-3` (1.00)

**blocked by**: open PR #10993, #17776

**verified**: ui/voices/ReplyActivity.kt:58-59 injects ActivitiesRepository for VoicesActions.showMemberDetails (:195); VoicesRepository (:65-66) is only passed as voicesEditActions (:151).

**do**: Move the fetch into ReplyViewModel; retype the field as VoicesEditActions.

**watch out**: ReplyActivity is owned by open PRs #10993/#17776.


---

### M124. data: give CoursesRepository.getMyCoursesFlow a non-suspend signature

**rating 45**: evidence 9 · impact 2 · feasibility 4 · `C77`  
**plans**: Claude 37 · Devin 72 · Codex 68  
**proposed by**: copilot kimi k3 `R-kim-4` (1.00)

**blocked by**: open PR #13657, #15808

**verified**: repository/CoursesRepository.kt:18 and CoursesRepositoryImpl.kt:117 declare suspend fun returning a Flow.

**do**: Make it a plain fun; only DashboardViewModel.kt:189 calls it.

**watch out**: Both files are owned by open PRs #13657/#15808.


---

## Rejected proposals — do not implement

Each was proposed by at least one agent and carried by at least one source plan. All were checked on `286e66e`.

| Proposal | Carried by | Why not |
|---|---|---|
| Chunk `UserDao.getUsersByAnyIds` / `getGuestUsersByNames` at 900 | Devin 84, Codex 89 (both put it in their #1 task) | Callers already chunk at 400/500 (`UserRepositoryImpl.kt:115,1099,1104`), and `id IN (:userIds) OR _id IN (:userIds)` binds the list twice, so a 900 chunk would bind 1,800 variables and *create* the crash |
| Chunk `DictionaryDao.insertAll` | Devin 78, Codex 76 | Room's list `@Insert` executes one single-row statement per entity; the 999-bind limit does not apply |
| Make a blank ChatSearch query return `emptyList()` | Devin 84, Codex 73 | `ChatSearchTest:66` pins 'search with empty query returns all chats', and the VM already short-circuits blank queries |
| Bound shelf-sync parallelism with a new Semaphore | Codex 69 (title) | `SyncRepositoryImpl.kt:221` already has `Semaphore(8)` for the inner batches; the outer fan-out is a handful of shelf types |
| Cancel stale dictionary search jobs per keystroke | Devin 78 | `searchWord` only fires from the search button (`DictionaryActivity.kt:123-126`), not per keystroke |
| Decode fullscreen images at screen size | Devin 75, Codex 68 | `ImageViewerUtils` loads `.into(binding.photoView)` without `SIZE_ORIGINAL`, so Glide already downsamples to the view |
| Wrap ResourcesPreviewLoader's `FileReader` in a `BufferedReader` | Devin 74, Codex 69 | OpenCSV's `CSVReader` wraps a non-buffered `Reader` in a `BufferedReader` itself (re-check against the pinned 5.12.0 source before reopening) |
| Linear-time multiple-choice compare in `ExamAnswerUtils` | Devin 74, Codex 78 | It sorts a handful of answer choices; there is nothing to win |
| Delta-update `StorageCategoryViewModel.toggleItemChecked` | Devin 74, Codex 68 | It is already one O(n) pass, and the immutable state needs a new list anyway |
| Drop 'redundant' repository fields from `ProcessUserDataActivity` | Codex 68 (its leaked 'Task 3b') | The subclass `SyncActivity` uses them (`:387`, `:391`, `:505`) |
| Normalize dispatcher discipline in `EnterprisesRepositoryImpl` | Devin 62, Codex 68 | The only `withContext(io)` (`:122`) wraps real file IO; suspend DAO calls need none, so it is already correct |
| Gate `isNetworkConnectedFlow` / audit NetworkUtils' `ResettableCache` eager paths | Devin 54, Codex 78 | Already lazy via `ResettableCache` (`NetworkUtils.kt:96-114`) |
| Audit `TimeUtils` formatter caching | Devin 56, Codex 73 | `formatterFor` already caches in a `ConcurrentHashMap`; no hot uncached site was shown |
| Thread an injected Json/Gson through the model layer | Devin 64, Codex 69 | Premise wrong (models already share one `GsonUtils.gson`); it would churn 13 entity files for no runtime gain |
| InlineResourceAdapter: skip status resets on title-only payloads | Devin 68, Codex 68 | `PAYLOAD_TITLE` already only sets the title; status and address payloads must re-run the preview |
| Prune 'unused' dictionary methods | Codex 68 | Every DictionaryRepository/DictionaryDao method has a production caller |
| Add SyncConfigurationCoordinator failure-branch tests | Codex 63 | `SyncConfigurationCoordinatorTest` already has the failure case (`:55`) plus nine others |
| `playstore-quota.sh`: project fields server-side with `--jq` | Devin 67, Codex 68 | It makes one `gh api` call (`:38`) and filters with `jq` locally; `gh --jq` is also client-side |
| Gemini's extras: `ServerConfigUtils.isLocalNetwork` regex rewrite, `observeUnreadCount`, `withContext(io)` around suspend DAO calls | partly in all three | Not hot paths, new unused API, or redundant (see the warnings on M-cards for #26, #78, #44) |

## Appendix — Claude plan number → mega plan number

C1→M7, C2→M8, C3→M1, C4→M9, C5→M10, C6→M2, C7→M11, C8→M3, C9→M12, C10→M13, C11→M4, C12→M15, C13→M14, C14→M16, C15→M17, C16→M28, C17→M18, C18→M5, C19→M29, C20→M20, C21→M32, C22→M30, C23→M31, C24→M19, C25→M78, C26→M21, C27→M33, C28→M34, C29→M35, C30→M36, C31→M37, C32→M45, C33→M38, C34→M39, C35→M40, C36→M22, C37→M23, C38→M117, C39→M79, C40→M41, C41→M46, C42→M47, C43→M118, C44→M63, C45→M64, C46→M65, C47→M26, C48→M51, C49→M67, C50→M66, C51→M68, C52→M24, C53→M70, C54→M42, C55→M43, C56→M69, C57→M44, C58→M6, C59→M48, C60→M49, C61→M80, C62→M25, C63→M6, C64→M83, C65→M53, C66→M81, C67→M54, C68→M27, C69→M55, C70→M75, C71→M120, C72→M121, C73→M122, C74→M59, C75→M123, C76→M77, C77→M124, C78→M61, C79→M72, C80→M71, C81→M85, C82→M86, C83→M87, C84→M89, C85→M90, C86→M50, C87→M62, C88→M91, C89→M92, C90→M56, C91→M82, C92→M93, C93→M57, C94→M94, C95→M95, C96→M58, C97→M96, C98→M97, C99→M98, C100→M99, C101→M100, C102→M101, C103→M102, C104→M103, C105→M104, C106→M105, C107→M111, C108→M107, C109→M108, C110→M76, C111→M109, C112→M84, C113→M112, C114→M113, C115→M114, C116→M119, C117→M115, C118→M116
