# refactor_tasks.md — merged backlog (verified, deduplicated, rating-sorted)

Rating = 0.30·evidence + 0.35·impact + 0.35·risk-adjusted feasibility (each scored 0–100 against the working tree at 14da1ba). `agents:` lists every agent that proposed the task.

### 1. [85] Fix LifeRepositoryImpl.updateVisibility owner re-read (imageId/title-keyed rows lose their owner)
agents: claude opus 5.5 (bounds-9) | E=95 I=82 F=80
files: repository/LifeRepositoryImpl.kt:23-33, data/room/dao/MyLifeDao.kt:21-25

Verified: MyLifeDao.updateVisibility matches _id OR imageId OR title, but the re-read uses getByIds(listOf(myLifeId)) which only matches _id -> for imageId/title-keyed rows the owner falls back to sharedPrefManager.getUserId() (wrong user). Add myLifeDao.getByAnyId(id) (same OR-predicate, SELECT) and re-read with it; fall back to prefs only if the row vanished. Also replace countByUserId existence checks (:97,:126) with EXISTS.

---

### 2. [84] Chunk NotificationDao multi-id queries past the SQLite 999-variable cap + slim caller projection
agents: claude opus 5.5 (bounds-1), devin swe 2 (bounds-7) | E=95 I=80 F=78
files: data/room/dao/NotificationDao.kt:39,42,48,102; repository/NotificationsRepositoryImpl.kt; services/sync/TransactionSyncManager.kt:212

Verified: getByIds/getIdsByIds/markAsRead/deleteByIds bind IN(:ids) unchunked while markSynced (:63-100) already chunks(900); TransactionSyncManager pages notifications at 1000 > 999 vars -> sync crash. Add @RawQuery-free chunked(900) wrappers internal+chunked publics, a notification_id/relatedTo/isRead/title projection, and repo-side fixes. Also covers dropping notificationDao.update/isReadFields (devin's narrower variant).

---

### 3. [82] Skip base64 encoding for remote image URLs in UserEntity.serialize
agents: claude opus 5.5 (perf-1) | E=90 I=65 F=92
files: model/UserEntity.kt:117,163-170

Verified: encodeImageToBase64 does File(imagePath).inputStream() for any non-content:// path, including the http(s) URLs addImageUrl can store -> guaranteed FileNotFoundException + printStackTrace per serialize. Return null early for http(s) and swap printStackTrace for Log.

---

### 4. [80] Remove duplicated meetup-sync ingest via CommunitySyncWriter
agents: devin swe 2 (bounds-4) | E=95 I=62 F=85
files: repository/CommunityRepositoryImpl.kt:95-108, repository/EventsRepositoryImpl.kt:97-119, repository/CommunitySyncWriter.kt, services/sync/TransactionSyncManager.kt:103, di/RepositoryModule.kt

Verified: CommunitySyncWriter.insertMeetupsFromSync and EventsRepositoryImpl.batchInsertMeetups are near-identical (same getByMeetupIds dedup + Meetup.fromJson + upsertAll; Events adds count+try/catch). Delete the writer interface or make Events delegate; TransactionSyncManager currently calls the Community copy.

---

### 5. [80] Give reports/teams single-column DAO update paths instead of read-modify-write
agents: claude opus 5.5 (bounds-8) | E=90 I=68 F=82
files: repository/EnterprisesRepositoryImpl.kt:80-150 (archiveReport :84, attachTeamImage :128, updateTeamEntityById :140)

Verified: updateTeamEntityById does teamDao.getById + upsert of the full row for a status/imageName flip -> stale-field clobber risk if a concurrent update lands between read and write. Add @Query UPDATE ... WHERE _id=:id for the two field updates.

---

### 6. [80] Replace skip/limit _all_docs paging with keyset (_id > lastId) pagination
agents: copilot grok 4.5 (perf-10) | E=85 I=85 F=70
files: services/sync/SyncManager.kt:328

Verified: createClientAndIterate queries _all_docs?limit=$batchSize&skip=$skip per batch; CouchDB re-scans skipped docs -> O(skip) work per batch grows quadratically over large tables. Switch to startkey/endkey or _id > lastDocId keyset paging.

---

### 7. [80] Bound text-file viewer reads; parse Markdown off the main thread
agents: claude opus 5.5 (perf-6), copilot kimi k3 (perf-3) | E=90 I=68 F=82
files: ui/viewer/ResourceViewerFragment.kt:587-615 (MAX_TEXT_VIEWER_CHARS=500_000 :723)

Verified: setupTextViewer calls File.readText() on the whole file then substring()s to the cap -> reads everything anyway (OOM on large files); markwon.setMarkdown also runs on the main thread. Bounded-buffer read of the cap; move parse+apply off-main. (kimi's narrower bounded-read variant folded in.)

---

### 8. [79] Batch persisted-download-state writes in DownloadService
agents: claude opus 5.5 (perf-2) | E=92 I=68 F=80
files: services/DownloadService.kt:163-193

Verified: cleanupProcessedUrls copies+rewrites both SharedPreferences sets for every file inside processDownloadQueue's loop -> O(N^2) string-set churn per queue drain. Collect IDs through the loop, write once at the end (unchanged-content early-out).

---

### 9. [79] Batch reconcileHtmlLibraries per-resource DB churn
agents: claude opus 5.5 (perf-3) | E=92 I=66 F=82
files: repository/ResourcesRepositoryImpl.kt:724-735 -> :400

Verified: each non-zip item does its own dispatcher hop + getByResourceId + upsert in a loop. Collect items, single getByResourceIds batch read, build entity list, one upsertAll.

---

### 10. [79] Cache legacy EncryptedSharedPreferences instead of rebuilding per call
agents: claude opus 5.5 (perf-4) | E=90 I=58 F=90
files: utils/SecurePrefs.kt:152-168 (callers :212,:238,:277,:311; cachedSecureStore pattern :29,:146-149)

Verified: getLegacyEncryptedPrefs builds a MasterKey + EncryptedSharedPreferences.create (keystore ops) on every call, and silently creates a 'legacy_secure_prefs' file even for bool/int consumers. Cache once (double-checked, like cachedSecureStore) and return null without creating the file when absent.

---

### 11. [78] Stop double-delivering chat-history reloads via replay=1 SharedFlow
agents: claude opus 5.5 (perf-5) | E=88 I=62 F=85
files: ui/chat/ChatViewModel.kt:65-68,205; ui/chat/ChatHistoryFragment.kt

Verified: _chatHistoryUpdateTrigger = MutableSharedFlow(replay=1) + fragment collectWhenStarted re-delivers the last value on every STARTED. Add a generation counter to the load function; dedupe re-triggered loads.

---

### 12. [78] Remove Room projections/DAOs leaking through ResourcesRepository's public surface
agents: claude opus 5.5 (bounds-7), copilot grok 4.5 (bounds-1) | E=90 I=75 F=72
files: repository/ResourcesRepository.kt:7,64; repository/ResourcesRepositoryImpl.kt:60,535,544,595; ui: AchievementViewModel.kt:15, EditAchievementFragment.kt:44

Verified: interface imports LibraryTitleProjection (only data.room.dao import reaching ui/); getLibraryTitles returns it. Also: removedLogDao injected/used at :535,:595 and observeOpenedResourceIds is `suspend fun -> Flow` (:544). Introduce a LibraryTitle data-class, map in repo, drop the two UI room imports; move RemovedLogDao writes behind a private helper; return plain Flow. Drop dead surface (getMyLibrary, resolveLibraryItemByResourceId, updateUserLibrary, clearResourceListCache, getResourceTitlesMap - no prod callers verified) to a minimal getResourceTitlesByIds, and have callers cache title maps locally.

---

### 13. [78] Extract TeamTaskDao out of NotificationsRepositoryImpl behind a task-lookup seam
agents: devin swe 2 (bounds-1), copilot grok 4.5 (bounds-6) | E=90 I=68 F=78
files: repository/NotificationsRepositoryImpl.kt:35,244,282,346,396

Verified: NotificationsRepositoryImpl injects TeamTaskDao and queries it at four sites (getById, getByIds, getByTitles, getTasksForUserBetween). Move those behind a narrow interface (grok's TaskNotificationLookup port returning minimal DTOs is the more complete version; a concrete TeamTasksReader also works).

---

### 14. [77] Stop re-checking foreground-service eligibility on every notification tick
agents: claude opus 5.5 (perf-9), copilot grok 4.5 (perf-8) | E=88 I=60 F=85
files: services/DownloadWorker.kt:~166 (with DownloadService.kt:120-136), utils/DownloadUtils.kt:224-233

Verified: startForegroundIfEligible/canStartForegroundService runs per notification emission, and DownloadUtils.isAppInForeground does an ActivityManager.getRunningAppProcesses binder IPC each time. Check once per worker run (set flag after first success; cancel instead of warn+skip). Alternatively replace the util with ProcessLifecycleOwner STARTED/STOPPED observer (precedent at utils/Utilities.kt:38, MainApplication.kt:357).

---

### 15. [77] Consolidate RetryRepository.recordFailure into one DAO-conditional write
agents: claude opus 5.5 (bounds-2) | E=90 I=70 F=72
files: services/retry/RetryQueue.kt:26-58, repository/RetryRepository.kt (+Impl), data/room/dao/RetryDao.kt

Verified: queueFailedOperation does getExistingOperation -> updateAttempt or enqueue as separate repo calls (TOCTOU) and UpdateAttempt is the only caller of the repo's updateAttempt. Add repo.recordFailure(key,type,reason,stack) that wraps RetryDao.updateAttempt + Upsert enqueue atomically; drop getExistingOperation/enqueue/updateAttempt from the interface.

---

### 16. [77] Stop deep-copying StepExam via full toKotlinx round-trip
agents: claude opus 5.5 (perf-8) | E=90 I=55 F=88
files: model/StepExam.kt:43,60

Verified: toJsonObject builds val kExam = exam.toKotlinx().jsonObject - a full element tree - then reads only questions.size. Build a shallow JsonObject (id/rev/courseId/stepId/name/type/passingPercentage/totalMarks/createdDate/updatedDate/noOfQuestions + primitive-only fields) skipping questions.

---

### 17. [77] Chunk TeamLogDao IN-clause queries
agents: devin swe 2 (bounds-6) | E=88 I=60 F=85
files: data/room/dao/TeamLogDao.kt:15-16,24-25

Verified: getRecentTeamVisits(cutoff, teamIds) and getByRemoteIds(ids) bind unchunked lists; team ids are collected per-member across teams so >999 is plausible at scale. internal + chunked(900) wrappers.

---

### 18. [76] Pre-index meetups by day in CalendarFragment
agents: devin swe 2 (perf-2) | E=88 I=50 F=92
files: ui/calendar/CalendarFragment.kt:58-64

Verified: every date tap runs meetups.filter { Instant...toLocalDate() == clickedDate } over the whole list. Build Map<LocalDate,List<Meetup>> once when the list loads; tap becomes a map get.

---

### 19. [76] Remove dead pending-upload surface from PersonalsRepository
agents: claude opus 5.5 (bounds-10) | E=95 I=45 F=90
files: repository/PersonalsRepository.kt:25-26, PersonalsRepositoryImpl.kt:69-90, data/room/dao/PersonalDao.kt

Verified: getPendingPersonalUploads + updatePersonalAfterSync have zero production callers (tests only; updatePersonalAfterSync also used internally :155). Make updatePersonalAfterSync private, delete getPendingPersonalUploads, drop the now-unused PersonalDao.getPendingUploads.

---

### 20. [76] Return a sealed upload result from PersonalsRepository instead of loose strings
agents: copilot grok 4.5 (bounds-4), copilot gemini 3.5 flash (bounds-2) | E=85 I=65 F=80
files: repository/PersonalsRepository.kt:27,113-124; ui/personals/PersonalsViewModel.kt

Verified: uploadPersonal returns String error/status strings the VM pattern-matches; Impl imports android.util.Log. Introduce sealed class (Success/AlreadyUploaded/UploadFailed/PostUploadFailed per gemini's crisper spec); VM maps to UI strings; remove Log from the repo.

---

### 21. [75] Route ActivitiesRepositoryImpl user lookups through UserRepository
agents: devin swe 2 (bounds-2) | E=90 I=48 F=88
files: repository/ActivitiesRepositoryImpl.kt:52,62,98-102

Verified: injects userDao (line 62) and calls userDao.getByName at :100 while userRepository: Lazy<UserRepository> already sits at :52. One-line swap to userRepository.get().getUserByName; drop the DAO param.

---

### 22. [75] Parse retry payload once with kotlinx in RetryRepositoryImpl.executeOperation
agents: claude opus 5.5 (perf-10) | E=90 I=52 F=85
files: repository/RetryRepositoryImpl.kt:74,90

Verified: JsonParser.parseString (Gson) then toKotlinx() re-traverses the tree per attempt. Parse directly with Json.parseToJsonElement, drop the bridge.

---

### 23. [75] Move shelf+content discovery into SyncRepository; keep SyncManager orchestration-only
agents: claude opus 5.5 (bounds-3) | E=88 I=62 F=78
files: services/sync/SyncManager.kt:327-370 -> repository/SyncRepositoryImpl.getShelvesWithDataBatchOptimized (:449-480)

Verified: SyncManager.fetchShelfAndContent embeds Retrofit + ApiInterface + sharedPrefManager to rebuild a query the repo already has (Semaphore(8), chunked(25), concurrent single-table fetches). Cache results on the repo keyed by userId+resources/courses/shelves hash; SyncManager only reads the map.

---

### 24. [75] Replace foreign DAOs inside UserRepositoryImpl with owner-repo calls
agents: devin swe 2 (bounds-3) | E=88 I=62 F=78
files: repository/UserRepositoryImpl.kt:~986 (myLibraryDao.getByIds), ~1252 (meetupDao.getByUserId)

Verified: UserRepositoryImpl reaches into MyLibraryDao and MeetupDao. Inject lazy ResourcesRepository / MeetupRepository equivalents (or CommunityRepository per Meetup.getMyMeetUpIds usage) and delegate.

---

### 25. [74] Move repository calls out of LoginActivity / GuestLoginExtensions / ServerDialogExtensions
agents: devin swe 2 (bounds-9), claude opus 5.5 (bounds-5) | E=85 I=68 F=70
files: ui/sync/LoginActivity.kt:278,374,589,631; GuestLoginExtensions.kt:39,58,60,68; ServerDialogExtensions.kt:298

Verified: seven+ direct repo calls (configurationsRepository.checkHealth/checkServerAvailability/isServerReachable, userRepository.saveUser/getAllUsers) while loginViewModel by viewModels() already exists at :87. Route through LoginViewModel (devin's wider variant also covers Guest/ServerDialog extensions and proposes narrowing TeamsMembersRepository to remove server dialog methods).

---

### 26. [74] Move NotificationsViewModel work off the main dispatcher and cancel stale loads
agents: claude opus 5.5 (perf-7) | E=85 I=60 F=80
files: ui/notifications/NotificationsViewModel.kt:69-78,267

Verified: loadNotifications launches in viewModelScope (Main) with no job cancel on re-entry; all flows run on Default already-except combine/format run on caller; no DispatcherProvider injected. Inject DispatcherProvider, track a Job and cancel on reload, formatNotification on Default.

---

### 27. [74] Look up VoicesLabelManager labels via the reverse map it already keeps
agents: devin swe 2 (perf-3) | E=88 I=45 F=92
files: services/VoicesLabelManager.kt:82,135

Verified: onClose click does labels.firstOrNull { getLabel(it) == text } - an O(labels) string compare per click; reverseLabels map already exists. Compare the selected id to currentLabelId by id instead.

---

### 28. [73] Resolve community config inside ChatViewModel instead of reading prefs in the fragment
agents: claude opus 5.5 (bounds-6) | E=85 I=52 F=85
files: ui/chat/ChatViewModel.kt:136-142; ui/chat/ChatHistoryFragment.kt:60-70

Verified: refreshChatHistory reads sharedPrefManager for parentCode/communityName and passes them into loadChatHistoryScreenData even though the VM already takes sharedPrefManager - duplicated lookup across layers. Inject ConfigurationsRepository, call getCommunityConfiguration() inside loadChatHistoryScreenData, simplify fragment to loadChatHistoryScreenData().

---

### 29. [73] Cap CrashLogStore file reads
agents: copilot kimi k3 (perf-4) | E=85 I=52 F=85
files: utils/CrashLogStore.kt:54-65

Verified: loadPendingLogs does file.readText() uncapped per file in the crash-report dir. Skip oversized files or read a bounded prefix.

---

### 30. [73] Delete unused MyLibraryDao.getAll()
agents: devin swe 2 (bounds-8) | E=92 I=38 F=92
files: data/room/dao/MyLibraryDao.kt:25-26; test-only use ResourcesRepositoryLibrarySyncTest.kt:124

Verified: no production caller of myLibraryDao.getAll(); the one test can assert via a scoped query instead. Dead full-table scan accessor.

---

### 31. [73] Render PDF pages downscaled in the viewer instead of native size
agents: copilot grok 4.5 (perf-4) | E=85 I=62 F=75
files: ui/viewer/ResourceViewerFragment.kt:515,525

Verified: PdfRenderer pages are rasterized at getWidth()xgetHeight() native size - multi-MB bitmaps per page on dense documents. Scale to the ImageView's displayed dimensions.

---

### 32. [73] Inject TimeProvider into PersonalsRepositoryImpl
agents: codex sol 5.6 (bounds-4) | E=82 I=52 F=85
files: repository/PersonalsRepositoryImpl.kt:44 (Date().time), :96 (System.currentTimeMillis)

Verified: two clock sources in one repo. Inject TimeProvider, derive uploadDate/createdOn from it.

---

### 33. [73] Give RealtimeSyncManager a set-based updatesFor and drop UI-side filtering
agents: codex sol 5.6 (bounds-2) | E=82 I=52 F=85
files: services/sync/RealtimeSyncManager.kt:22-24; ui/sync/RealtimeSyncMixin.kt:29-38

Verified: updatesFor(table: String) filters one table; RealtimeSyncHelper reads the raw flow and filters by watchedTables.toSet() in the UI layer. Change updatesFor to take a Set<String>; fragment/mixin passes getWatchedTables().toSet().

---

### 34. [73] Move submission/exam DAO reads in Progress+Courses behind SubmissionsRepository
agents: devin swe 2 (bounds-5) | E=88 I=68 F=65
files: repository/ProgressRepositoryImpl.kt:68,72,186,189; repository/CoursesRepositoryImpl.kt:469,480,615,619,625; repository/SubmissionsRepositoryImpl.kt:52-55

Verified: both repos inject examDao/submissionDao/answerDao/questionDao while SubmissionsRepositoryImpl owns all four. Add coarse methods to SubmissionsRepository (getExamProgressForCourses, getStepSubmissions, getCourseStats variants) and delete the DAO injections.

---

### 35. [73] Take a count in updateTeamNotification instead of the full News list
agents: codex sol 5.6 (bounds-1) | E=85 I=52 F=85
files: repository/NotificationsRepository.kt:31, NotificationsRepositoryImpl.kt:366

Verified: signature accepts List<News> but only needs its size for unread-count bookkeeping. Change to Int at interface+impl+callers.

---

### 36. [73] Query library titles by ID instead of loading the whole titles map
agents: copilot grok 4.5 (perf-5) | E=85 I=60 F=75
files: repository/ResourcesRepositoryImpl.kt:851,859 (getResourceTitlesMap -> myLibraryDao.getResourceTitles)

Verified: getOfflineResourceItems calls getResourceTitlesMap() (all rows' titles) then filters by submitted ids. Add MyLibraryDao.getResourceTitlesByIds(ids) (keep the existing chunked pattern).

---

### 37. [72] Remove stale @Config(sdk=[...]) pins from 10 Robolectric tests
agents: codex sol 5.6 (perf-1..10) | E=95 I=30 F=95
files: test/: LifeAdapterTest:22, ServerAddressAdapterTest:17, PersonalsAdapterTest:22, VoicesActionsTest:29 (sdk 34); ChatAdapterTest:20 (sdk 33); ResourcesFilterFragmentTest:15, ResourcesAdapterTest:24, EnterprisesReportsFragmentTest:26, ExamDaoTest:18, CourseDaoTest:20 (sdk 32)

Verified all ten files: each pins an old SDK (32/33/34) while compileSdk=37 - Robolectric then emulates an outdated runtime. Drop the sdk pins (keep @Config(application=Application::class) where present).

---

### 38. [72] Replace O(rows x teams) nesting in VoicesRepositoryImpl.countTopLevelByTeams
agents: devin swe 2 (perf-9) | E=88 I=55 F=75
files: repository/VoicesRepositoryImpl.kt:496-502

Verified: nested loop over every team x every news row. Build per-team id sets once / single grouped pass. FLAG: file is touched by open PRs #13415/#10993.

---

### 39. [72] Parallelize sequential per-notification lookups in getJoinRequestDetailsBatch
agents: copilot kimi k3 (perf-8) | E=85 I=52 F=80
files: repository/NotificationsRepositoryImpl.kt:302-340

Verified: each join-request notification does sequential user/team lookups; notification.userId is derivable from the notification itself so the batch can resolve users in parallel / via getByIds.

---

### 40. [72] Add RemovedLogDaoTest covering chunked delete paths
agents: copilot gemini 3.5 flash (bounds-9) | E=80 I=48 F=88
files: new test/java/.../data/room/dao/RemovedLogDaoTest.kt; data/room/dao/RemovedLogDao.kt

Verified: RemovedLogDao has chunked(500) delete variants and no test file exists; chunk-boundary behavior is exactly where the 999-var class of bugs lives.

---

### 41. [72] Move LoginSyncManager.syncAdmin's config writes into ConfigurationsRepository
agents: claude opus 5.5 (bounds-4) | E=85 I=58 F=75
files: services/sync/LoginSyncManager.kt:126-168; repository/ConfigurationsRepositoryImpl.kt:406,413

Verified: syncAdmin POSTs _find for administrators + writes parentCode/communityName/communityLeaders into sharedPrefManager while ConfigurationsRepository already owns parentCode/communityName and has getCommunityLeaders(). Add saveCommunityConfig(name, parentCode, leadersJson) (or per-field setters); simplify to parentCode.isEmpty() check.

---

### 42. [71] Move computeMonthlyCounts out of ActivitiesFragment into the ViewModel
agents: copilot grok 4.5 (bounds-10) | E=80 I=55 F=78
files: ui/dashboard/ActivitiesFragment.kt:38-61; ActivitiesViewModel.kt:22

Verified: fragment collects offlineLogins then aggregates + renders; aggregation is testable VM logic. Emit monthly counts from the VM.

---

### 43. [71] Constrain Glide loads in EnterprisesFinancesAdapter
agents: copilot grok 4.5 (perf-1) | E=80 I=55 F=80
files: ui/enterprises/EnterprisesFinancesAdapter.kt:78-82

Verified: Glide load has no .override()/transform/diskCacheStrategy - full-size image decode per row bind.

---

### 44. [71] Constrain Glide loads in EnterprisesReportsAdapter
agents: copilot grok 4.5 (perf-2) | E=80 I=55 F=80
files: ui/enterprises/EnterprisesReportsAdapter.kt:115-118

Verified: same unconstrained Glide pattern as the finances adapter.

---

### 45. [71] Remove identity .map{} passes and hoist invariant work out of answer loops
agents: devin swe 2 (perf-4) | E=88 I=48 F=80
files: repository/CoursesRepositoryImpl.kt:553,554,912; repository/SubmissionsRepositoryImpl.kt:292,789,743

Verified: '.map { it }' no-ops on three sites; parentId.split('@') recomputed inside an answers loop. FLAG: both files are touched by open PRs (#16624/#15951/#15825 Courses; #17430/#17356 Submissions).

---

### 46. [71] Use ViewBinding in OnboardingAdapter instead of inflate+findViewById chain
agents: copilot kimi k3 (perf-7) | E=82 I=45 F=88
files: ui/onboarding/OnboardingAdapter.kt:24-32

Verified: onCreateViewHolder inflates the layout then 3 findViewById calls per holder; project already builds with ViewBinding enabled.

---

### 47. [71] Fix PdfThumbnailLoader cache race and unrecycled pages
agents: copilot grok 4.5 (perf-3) | E=85 I=58 F=72
files: utils/PdfThumbnailLoader.kt

Verified: cache.get outside withContext, put via ?.also (check-then-act race), LruCache has no entryRemoved -> evicted bitmaps/pages never recycled.

---

### 48. [71] Move ProcessUserDataActivity sync calls into a SyncViewModel
agents: devin swe 2 (bounds-10) | E=82 I=58 F=75
files: ui/sync/ProcessUserDataActivity.kt:193,211,254

Verified: activity calls syncRepository.uploadLoginData()/uploadBulkData() and userRepository.fetchUserSecurityData(name) directly. Add SyncViewModel exposing upload flows + fetchUserSecurityData so the activity only collects UI state.

---

### 49. [71] Remove android.util.Log from RetryRepositoryImpl
agents: copilot grok 4.5 (bounds-9) | E=88 I=32 F=95
files: repository/RetryRepositoryImpl.kt:3

Verified: Log usage in the repo layer; retry outcome already returned to callers. Delete or route through a logger seam.

---

### 50. [71] Batch per-member queries and per-row writes in TeamsRepositoryImpl
agents: devin swe 2 (perf-1) | E=90 I=70 F=55
files: repository/TeamsRepositoryImpl.kt:1027,1033,1286-1298

Verified: markMembershipsForLeave does per-member activitiesRepository.getLastVisit + getOfflineVisitCount then per-row deleteById/upsert while TeamDao.deleteByIds/upsertAll exist. Add OfflineActivityDao GROUP BY batch queries (getLastVisitsForUsers, getOfflineVisitCounts), batch the reads and the writes. FLAG: file is touched by open PRs (#16623/#15951/#15825/#15820) - land after those or expect rebase.

---

### 51. [70] Extract AchievementFragment repo calls into a ViewModel
agents: copilot kimi k3 (bounds-3) | E=80 I=52 F=78
files: ui/user/AchievementFragment.kt:90,106,205

Verified: fragment calls userRepository.getAchievementData/getUserModel and resourcesRepository.downloadResources directly. Route through a VM.

---

### 52. [70] Extract BecomeMemberActivity user ops into a ViewModel
agents: copilot kimi k3 (bounds-2) | E=80 I=55 F=75
files: ui/user/BecomeMemberActivity.kt:115,177,208,241

Verified: activity calls createMember, validateUsername (x2), cleanupDuplicateUsers directly. New BecomeMemberViewModel exposing create/validate results as state.

---

### 53. [70] Hoist per-bind getString/getColor/typeface work across adapters (devin perf-6/7/8 folded)
agents: devin swe 2 (perf-6,7,8) | E=84 I=45 F=84
files: ui/chat/ChatShareTargetAdapter.kt:48-68, ChatDetailFragment.kt:527-537, ChatAdapter.kt:75; ui/life/LifeAdapter.kt, HealthExaminationAdapter.kt, EnterprisesFinancesAdapter.kt, EnterprisesReportsAdapter.kt, UserArrayAdapter.kt, HealthUsersAdapter.kt, MembersAdapter.kt

Verified: adapters re-fetch identical resources (getString/getColor/typeface re-apply; updateButtonStyles does 4 getColor/child calls per bind). Cache at holder/adapter init. Overlaps the click-listener family on ChatAdapter/UserArrayAdapter/HealthUsersAdapter/LifeAdapter and the glide tasks on the two Enterprises adapters - sequence them on the same files.

---

### 54. [70] Key ChatAdapter animation state by message id, not position
agents: copilot grok 4.5 (perf-9) | E=85 I=55 F=72
files: ui/chat/ChatAdapter.kt:109-124,132-145

Verified: addQuery/addResponse copy the whole list per message; animatedMessages is HashMap<Int,Boolean> position-keyed, and prependMessages remaps keys on every prepend. Key by stable message id and avoid the list copy.

---

### 55. [70] Remove printStackTrace from DiagnosticsRepositoryImpl
agents: codex sol 5.6 (bounds-7) | E=80 I=38 F=92
files: repository/DiagnosticsRepositoryImpl.kt:74,93

Verified: two e.printStackTrace() calls in production paths.

---

### 56. [70] Extract ResourceDetailFragment repo calls into a ViewModel
agents: copilot kimi k3 (bounds-1) | E=80 I=55 F=75
files: ui/resources/ResourceDetailFragment.kt:60,89,96,242

Verified: fragment calls resourcesRepository.setUserLibrary/resolveLibraryItem, userRepository.getUserModel, ratingsRepository from lifecycleScope. New ResourceDetailViewModel; fragment observes state.

---

### 57. [70] Move serializeForUpload off ResourcesRepository onto the upload boundary
agents: copilot kimi k3 (bounds-4) | E=85 I=55 F=72
files: repository/ResourcesRepository.kt:139, ResourcesRepositoryImpl.kt:925; services/upload/UploadConfigs.kt:281

Verified: an upload-format serializer lives on the read-repo interface; its only consumer is UploadConfigs. Move to UploadRepository or a ResourceUploadSerializer.

---

### 58. [70] Move SettingsViewModel.clearAllData orchestration into ConfigurationsRepository
agents: copilot grok 4.5 (bounds-7) | E=80 I=45 F=85
files: ui/settings/SettingsViewModel.kt:42-45; repository/ConfigurationsRepository.kt:18,25

Verified: VM calls configurationsRepository.clearAllData() then clearPreferences() - orchestration below the layer. Offer one clearAllUserData() on the repo (or a dedicated ResetRepository).

---

### 59. [70] Buffer WebViewActivity file reads; hoist per-call regex in UserRepositoryImpl
agents: devin swe 2 (perf-10) | E=85 I=48 F=80
files: ui/viewer/WebViewActivity.kt:180; repository/UserRepositoryImpl.kt:719

Verified: unbuffered FileInputStream read loop; '"[^:]+:[^@]+@".toRegex()' compiled per call. BufferedInputStream; hoist the Regex to a companion val. FLAG: UserRepositoryImpl is touched by open PRs (#17254, #15951, ...).

---

### 60. [69] Add DictionaryDaoTest
agents: copilot gemini 3.5 flash (bounds-8) | E=80 I=38 F=90
files: new test/java/.../data/room/dao/DictionaryDaoTest.kt

Verified: no test file exists for DictionaryDao.

---

### 61. [69] Lowercase exam correctChoices once at write time
agents: devin swe 2 (perf-5) | E=85 I=52 F=72
files: model/ExamQuestion.kt:44,54; utils/ExamAnswerUtils.kt:69-91

Verified: setCorrectChoiceArray lowercases, setCorrectChoices stores raw, then three check* functions re-lowercase on every grade. Normalize at ingestion; drop the repeated lowercasing. (Edge: pre-existing stored mixed-case values - migration note.)

---

### 62. [68] Move download/resource logic out of CoursesStepsViewModel.loadStep
agents: codex sol 5.6 (bounds-8) | E=78 I=48 F=80
files: ui/courses/CoursesStepsViewModel.kt:80-105

Verified: loadStep re-filters not-downloaded resources and hand-builds URL lists twice (current + next step) - repo-level logic sitting in the VM. Push prefetch/availability into the resources/download layer.

---

### 63. [68] Return a report DTO instead of the MyTeam entity through getReportsFlow
agents: copilot grok 4.5 (bounds-5) | E=80 I=60 F=65
files: repository/EnterprisesRepository.kt:11; EnterprisesViewModel.kt:100; EnterprisesReportsFragment.kt:46,214,219

Verified: Flow<List<MyTeam>> crosses repo->VM->fragment->adapter. Introduce EnterprisesReport DTO carrying the fields the UI uses.

---

### 64. [68] Flatten item_library_grid.xml badge nesting
agents: copilot kimi k3 (perf-2) | E=80 I=48 F=78
files: res/layout/item_library_grid.xml:42-56

Verified: nested FrameLayout stack for the badge. Merge into the parent FrameLayout / ConstraintLayout.

---

### 65. [68] Hide SharedPreferences behind a storage seam inside LifeCache
agents: codex sol 5.6 (bounds-3) | E=80 I=50 F=75
files: repository/LifeCache.kt (imports SharedPreferences + edit + Gson)

Verified: LifeCache is the only repository class directly driving SharedPreferences + Gson. Introduce a small injected StringStore/serializer boundary so the cache stays platform-free.

---

### 66. [68] Extract ReplyActivity repo calls into a ViewModel
agents: copilot kimi k3 (bounds-10) | E=80 I=55 F=72
files: ui/voices/ReplyActivity.kt:59 (activitiesRepository, voicesRepository injected)

Verified: activity injects ActivitiesRepository + VoicesRepository and drives them from the UI layer. New ReplyViewModel; activity renders state.

---

### 67. [68] Flatten row_survey.xml's weighted LinearLayout chain
agents: copilot kimi k3 (perf-1) | E=80 I=48 F=78
files: res/layout/row_survey.xml

Verified: ~10 LinearLayout/layout_weight usages - nested weights cause O(depth^2) measure passes per row. Rebuild as ConstraintLayout.

---

### 68. [68] Normalize shelf-cache keys/values inside SyncRepositoryImpl
agents: codex sol 5.6 (bounds-6) | E=78 I=42 F=85
files: repository/SyncRepositoryImpl.kt (getCachedShelvesWithData/cacheShelvesWithData)

Verified: cache keyed by raw shelves/resources/courses inputs; normalize (sort/dedupe) before hashing so logically-equal lookups hit. Overlaps the shelf-discovery move - land that first.

---

### 69. [67] Return a sealed HealthResult from ConfigurationsRepository.checkHealth
agents: copilot gemini 3.5 flash (bounds-3) | E=80 I=50 F=72
files: repository/ConfigurationsRepository.kt:13, Impl:64-90 (context.getString(R.string.server_sync_successfully))

Verified: checkHealth returns a localized String built with Context+R inside the repo. Return sealed result; UI maps to strings.

---

### 70. [67] Add tests for getJoinRequestDetailsBatch
agents: copilot kimi k3 (perf-9) | E=78 I=40 F=85
files: new/extended test for NotificationsRepositoryImpl

Verified: the batch join-request path has no dedicated coverage; mock DAOs and assert single-batch resolution.

---

### 71. [67] Swap LifeCache from Gson to kotlinx.serialization
agents: copilot gemini 3.5 flash (bounds-4) | E=78 I=42 F=82
files: repository/LifeCache.kt

Verified: LifeCache serializes MyLife lists via Gson where the codebase's shared path is AllowlistFactory/kotlinx. Migrate the cache payload.

---

### 72. [67] Decouple model/MyLife.kt from Android resources
agents: copilot gemini 3.5 flash (bounds-1) | E=80 I=42 F=82
files: model/MyLife.kt:7 (import ...R), :57-68 (defaultItemPairs/defaultItems)

Verified: entity's companion imports R and resolves string resources to seed default myLife items. Move defaultItems/label resolution into the seeding layer (LifeRepositoryImpl) or a ui-side mapper.

---

### 73. [67] Make UploadRepository network-only; move Room write-backs into upload config lambdas
agents: copilot grok 4.5 (bounds-8) | E=82 I=62 F=60
files: repository/UploadRepositoryImpl.kt (markUploaded/markExamsUploaded use examDao/submissionDao)

Verified: markUploaded switches on UploadUpdateType and writes through injected ExamDao/SubmissionDao. Let each UploadConfig carry an onSuccess(item)->Unit lambda so the repo only does I/O. CONFLICT: supersedes the markUploaded-dedup task - order accordingly.

---

### 74. [66] Move row click listeners out of onBindViewHolder across 8 adapters (gemini perf-2..6,8..10 folded)
agents: copilot gemini 3.5 flash (perf-2..10) | E=80 I=30 F=90
files: UserArrayAdapter.kt:80, ChatAdapter.kt:191, CheckboxAdapter.kt:39, ServerAddressAdapter.kt:86, HealthUsersAdapter.kt:42, LifeAdapter.kt:62, SubmissionsAdapter.kt:93, UsersAdapter.kt:31

Verified all 8 sites: listeners created per bind. Move to ViewHolder init (one listener per holder) keyed on bindingAdapterPosition. Overlaps bind-resource-hoists on shared files - same pattern family, independent hunks.

---

### 75. [66] Add DeviceNameProviderTest
agents: copilot gemini 3.5 flash (bounds-10) | E=78 I=32 F=90
files: utils/DeviceNameProvider.kt:11-29 (SharedPrefDeviceNameProvider); no existing test file

Verified: SharedPrefDeviceNameProvider has name-generation/persistence logic, untested.

---

### 76. [66] Remove SharedPrefManager fallbacks from DiagnosticsRepositoryImpl
agents: copilot grok 4.5 (bounds-3) | E=80 I=50 F=70
files: repository/DiagnosticsRepositoryImpl.kt:54,57

Verified: model?.parentCode/planetCode fall back to sharedPrefManager getters. Resolve identity above the repo or drop the dual source.

---

### 77. [66] Replace resolveType's sequential contains-chains with a keyword map
agents: copilot grok 4.5 (perf-7) | E=75 I=40 F=85
files: repository/NotificationsRepositoryImpl.kt:417-442

Verified: resolveType runs chained .contains checks per notification. Single-pass keyword->type map.

---

### 78. [66] Convert Sha256Utils to a Kotlin object
agents: copilot gemini 3.5 flash (perf-7) | E=80 I=32 F=88
files: utils/Sha256Utils.kt

Verified: plain class holding a companion object of stateless methods.

---

### 79. [65] Stop copy-per-read churn in LifeCache
agents: copilot grok 4.5 (perf-6) | E=72 I=45 F=80
files: repository/LifeCache.kt:27,37

Verified: cached.map { it.copy() } on every read/write. Return the immutable list (entities are data classes; mutation risk is callers', or copy once on write). NOTE: its 'synchronous prefs write' claim was overstated - preferences.edit{} uses apply(); the copy-churn part still holds.

---

### 80. [64] Migrate DictionaryMapper/DictionaryRepositoryImpl off Gson
agents: copilot gemini 3.5 flash (bounds-5) | E=75 I=48 F=72
files: repository/DictionaryMapper.kt:9-20, DictionaryRepositoryImpl.kt

Verified: mapper consumes Gson JsonArray/GsonUtils; swap to kotlinx JsonArray per the AllowlistFactory direction.

---

### 81. [64] Require explicit userId on LifeRepository; delete SharedPrefManager fallbacks
agents: copilot grok 4.5 (bounds-2) | E=80 I=55 F=58
files: repository/LifeRepositoryImpl.kt:23-33,95-128

Verified: updateVisibility/updateMyLifeListOrder fall back to sharedPrefManager.getUserId(). Push userId through the interface from callers. CONFLICT: intersects the mylife-reread fix - land that first, then this removes the fallback.

---

### 82. [64] Consolidate duplicate userId/userName query pairs in OfflineActivityDao
agents: copilot kimi k3 (bounds-5) | E=78 I=45 F=70
files: data/room/dao/OfflineActivityDao.kt:13-16,21-27

Verified: countByUserIdAndType/countByUserNameAndType and getGlobalLastVisit/getLastVisit are duplicated pairs differing only in key column. Collapse to nullable-param single queries.

---

### 83. [63] Extend CrashLogStore tests for size-cap and corrupted-file paths
agents: copilot kimi k3 (perf-10) | E=72 I=32 F=85
files: utils/CrashLogStore.kt; test/java/.../CrashLogStoreTest.kt (exists - extend, don't recreate)

Verified: CrashLogStoreTest.kt already exists (the 'untested' claim was wrong); still reasonable to extend for the read-cap added by crashlog-cap.

---

### 84. [62] Make DAO state the single source of truth for retry in-flight status
agents: codex sol 5.6 (bounds-5) | E=72 I=58 F=58
files: repository/RetryRepositoryImpl.kt (isCurrentlyProcessing/setProcessing flags + DAO states coexist)

Verified: in-memory flags diverge from persisted retry rows. Collapse to DAO-derived state; keep retry worker logic single-sourced.

---

### 85. [61] Set setHasFixedSize(true) on six fixed-height RecyclerViews
agents: copilot kimi k3 (perf-5+6) | E=75 I=38 F=72
files: PersonalsFragment:35, ReferencesFragment:29, FeedbackListFragment:47, NotificationsFragment:65, CoursesProgressFragment:20, EnterprisesReportsFragment:120

Verified: all six set a layoutManager, none set setHasFixedSize. Guard: only safe where row heights are uniform - check item layouts per site before applying.

---

### 86. [59] Harden AnswerDao chunked helpers + boundary tests
agents: codex sol 5.6 (bounds-10) | E=62 I=30 F=85
files: data/room/dao/AnswerDao.kt:12-30

Verified: getBySubmissionIds/deleteBySubmissionIds already chunk(900) and guard empty input - the premise is mostly defensive hardening (dedupe ids, pin ordering contract, add boundary tests).

---

### 87. [57] Dedupe repeated upload results inside UploadRepositoryImpl.markUploaded
agents: codex sol 5.6 (bounds-9) | E=70 I=48 F=55
files: repository/UploadRepositoryImpl.kt:44-108

Verified: markUploaded/markExamsUploaded iterate every result; duplicate successes cause redundant writes. CONFLICT: the uploadrepo-network task removes this method entirely - ship only one of the two.

---

### 88. [55] Use parameterized/typed Room access for CourseProgressDao.getByCourseUsersAndSteps
agents: copilot kimi k3 (bounds-9) | E=65 I=50 F=52
files: data/room/dao/CourseProgressDao.kt:31-46

Verified: hand-built 'SELECT * FROM course_progress WHERE ' + joined OR clauses via SimpleSQLiteQuery. Args ARE bound (not injectable - 'injection-adjacent' claim overstated), but a typed Room query or a (courseId,userId,stepNum) IN-composite removes the dynamic builder.

---

### 89. [53] Simplify MyLifeDao's nullable-userId predicates
agents: copilot gemini 3.5 flash (bounds-6) | E=70 I=50 F=42
files: data/room/dao/MyLifeDao.kt:12-19

Verified the code exists, but the predicate's (:userId IS NULL AND userId IS NULL/''/'--') arm intentionally matches legacy unowned rows (same pattern is deliberate in PersonalDao.countByTitle :12-18). Normalizing userId to '--' in the repo would orphan NULL/'' rows. CONFLICT: directly contradicts the mylife-reread task's scope - risky, only viable alongside a data cleanup.

---

### 90. [51] Simplify PersonalDao.countByTitle's nullable-userId predicate
agents: copilot gemini 3.5 flash (bounds-7) | E=70 I=45 F=42
files: data/room/dao/PersonalDao.kt:12-18

Verified the code exists, but the file's own comment says the null/blank arm 'mirrors the original conditional userId filter' - i.e. intentional Realm-parity semantics. Changing it alters which personals count as duplicates. Same caveat class as mylifedao-predicate.

---

### 91. [49] Introduce a thin projection for MeetupDao list reads
agents: copilot kimi k3 (bounds-6) | E=45 I=42 F=60
files: data/room/dao/MeetupDao.kt; repository/MeetupRepositoryImpl.kt

Vague premise - 'audit for any query that SELECT * where a subset suffices' with no concrete call site verified. Kept as a placeholder; needs a real column analysis before it's actionable.

---

### 92. [48] Slim DictionaryViewModel / repository composition
agents: copilot kimi k3 (bounds-8) | E=42 I=40 F=62
files: ui/dictionary/ (DictionaryActivity), repository/DictionaryRepositoryImpl.kt

Vague premise - 'inspect for any logic that should be pushed to repository' with no cited violation. Kept as a placeholder pending a concrete finding.

---

## Dropped (premise failed verification)

- **perf-gemini-1** — utils/FileUtils.kt getMimeType: Premise fails its own stated constraint: FileUtils.kt IS modified by open PR #17187 (verified via git diff origin/master...17187 head: FileUtils.kt in the file list), while the gemini list asserts zero overlap with open PRs. The code problem is real but the task violates the shared no-open-PR-files rule the other 11 lists honor.
- **bounds-kimi-7** — NewsDao paged query + VoicesRepositoryImpl: Both files are under open PRs: NewsDao.kt in #15820; VoicesRepository{,Impl}.kt in #13415 and #10993 (verified via git diff). kimi's own perf list flags all three as excluded, so this task is self-inconsistent.
