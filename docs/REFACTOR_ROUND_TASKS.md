# myPlanet refactor round — 10 work orders

Generated 2026-10-01. These are verbatim work orders for coding agents. Each task is independently mergeable in any order.

**Open-PR status at generation time: checked** (33 open PRs enumerated via GitHub API, 438 modified files excluded). Hot and collision-prone areas excluded from scope:

- PR #15808 (database operations & Room DAOs): `AppDatabase.kt`, `RoomModule.kt`, `CoursesRepositoryImpl.kt`, `TeamsRepositoryImpl.kt`, `UserRepositoryImpl.kt`, `VoicesRepositoryImpl.kt`, `SubmissionsRepositoryImpl.kt`, `FeedbackRepositoryImpl.kt`, `ResourcesRepositoryImpl.kt`, `RatingDao.kt`, and 40 other core persistence files.
- PRs #15807, #15799: `ui/teams/**`, `BaseMemberFragment.kt`.
- PR #15806: `ui/courses/TakeCourseFragment.kt`.
- PR #15805: `ui/resources/ResourcesFragment.kt`.
- PR #15804: `ui/user/UserProfileFragment.kt`.
- PR #15803: `services/sync/RealtimeSyncManager.kt`.
- PR #15796: `ui/chat/ChatViewModel.kt`.
- PR #15795: `ui/surveys/SurveyFragment.kt`.
- PR #15794: `ui/resources/**`.
- PR #15792: `services/sync/SyncManager.kt`.
- PR #15791: `ui/sync/LoginActivity.kt`.
- PR #15788: `ui/viewer/ResourceViewerActivity.kt`.
- PR #15786: `services/DownloadService.kt`.
- PR #15783: `utils/FileUploader.kt`.
- PR #15782: `ui/events/EventsDetailFragment.kt`.
- PR #15780: `ui/voices/NewsAdapter.kt`.
- PR #15778: `ui/courses/CourseProgressViewModel.kt`.

Round focus: reinforcing repository boundaries between layers, cross-feature data leaks, moving data functions one by one from UI/data/service into repositories, Room/DAO optimizations, and smoother repository ↔ ViewModel relationships.

Roadmap legend:

1. finish cleaning the data layer
2. introduce global navigation architecture
3. expand viewmodel and use-case layers
4. complete dependency-injection cleanup
5. consolidate sync and upload workflow
6. migrate ui incrementally to compose
7. optimize remaining performance hotspots
8. improve code health and add tests
North star — never scheduled directly, never blocked:
9. kotlin multiplatform: platform-free kotlin core (repositories, models, sync/upload logic, and use cases with zero android.* imports)
10. compose multiplatform: portable compose screens (state hoisted into viewmodels, no android views in composables, no direct R.*)

---

## Task 1 — Dictionary download coordination and URL resolution encapsulation

**Roadmap:** 1 (Finish cleaning the data layer), 3 (Expand viewmodel and use-case layers), 9 (Kotlin Multiplatform)

**Verified problem:** `DictionaryActivity` (`app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt:145`) constructs network URLs directly with `"${UrlUtils.getUrl()}/dictionary"` and triggers download background services via `DownloadUtils.openDownloadService` in `downloadDictionary()`. The UI layer directly manages network endpoint paths and service triggering instead of delegating endpoint resolution through `DictionaryViewModel` and `DictionaryRepository`.

**Files (4):**

- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`

**Work:**

1. In `DictionaryRepository`: Add `fun getDictionaryDownloadUrl(): String` returning the resolved dictionary file URL.
2. In `DictionaryRepositoryImpl`: Implement `getDictionaryDownloadUrl(): String = "${UrlUtils.getUrl()}/dictionary"`, keeping endpoint URL resolution encapsulated in the data layer.
3. In `DictionaryViewModel`: Add `fun getDictionaryDownloadUrl(): String = dictionaryRepository.getDictionaryDownloadUrl()`.
4. In `DictionaryActivity`: Replace hardcoded string interpolation `"${UrlUtils.getUrl()}/dictionary"` with `viewModel.getDictionaryDownloadUrl()`, eliminating direct URL synthesis from the Activity. Remove unused `UrlUtils` import if no longer referenced.

**Acceptance:** `grep -n "UrlUtils\.getUrl" DictionaryActivity.kt` returns zero matches; dictionary download URL resolution is completely contained within `DictionaryRepositoryImpl`; `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.dictionary.*"` passes.

---

## Task 2 — Enterprises report typed persistence and DAO encapsulation

**Roadmap:** 1 (Finish cleaning the data layer), 7 (Optimize remaining performance hotspots)

**Why:** In `EnterprisesRepositoryImpl.saveReport()` (`app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt:84`), enterprise financial data (`balance`, `totalMembers`, `revenue`) is received as a raw string list, assembled into an intermediate Gson `JsonObject`, and serialized back into `MyTeam` via `team.populateTeamFields(doc)`. This creates unnecessary JSON object allocations and round-trips through string parsing for fields that already exist as typed properties on `MyTeam`.

**Files (2):**

- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`

**Work:**

1. In `TeamDao`: Add a targeted update query:
   ```kotlin
   @Query("UPDATE teams SET balance = :balance, totalMembers = :totalMembers, revenue = :revenue, isUpdated = 1 WHERE _id = :teamId")
   suspend fun updateEnterpriseReport(teamId: String, balance: String, totalMembers: String, revenue: String): Int
   ```
2. In `EnterprisesRepositoryImpl.saveReport()`: Replace the `JsonObject` creation, string index lookups, and `team.populateTeamFields()` round-trip with a direct call to `teamDao.updateEnterpriseReport(teamId, balance, totalMembers, revenue)`.
3. In `EnterprisesRepositoryImpl`: Update the in-memory `team` cache if present, without creating intermediate JSON structures.

**Acceptance:** `saveReport` no longer instantiates intermediate `JsonObject` instances for enterprise report saving; team entity report fields are updated via Room DAO without JSON serialization round-tripping; `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.EnterprisesRepositoryImplTest"` passes.

---

## Task 3 — Reactive Life item observation and ViewModel state hoisting

**Roadmap:** 1 (Finish cleaning the data layer), 3 (Expand viewmodel and use-case layers), 10 (Compose Multiplatform)

**Verified problem:** `MyLifeDao` (`app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:14`) only provides a one-shot `suspend fun getAll(): List<MyLife>` query. As a result, `LifeRepository` and `LifeViewModel` poll the database imperatively via `loadLifeList()`, manually setting `_lifeList.value = lifeRepository.getAll()`. Any background insertions or deletions fail to reflect reactively in the UI.

**Files (4):**

- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`

**Work:**

1. In `MyLifeDao`: Add `@Query("SELECT * FROM my_life") fun observeAll(): Flow<List<MyLife>>`.
2. In `LifeRepository`: Add `fun observeAll(): Flow<List<MyLife>>`.
3. In `LifeRepositoryImpl`: Implement `override fun observeAll(): Flow<List<MyLife>> = myLifeDao.observeAll()`.
4. In `LifeViewModel`: Replace `_lifeList` and manual `loadLifeList()` polling with:
   ```kotlin
   val lifeList: StateFlow<List<MyLife>> = lifeRepository.observeAll()
       .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
   ```
   Retain `loadLifeList()` as a no-op or trigger if required by legacy callers, while observing the reactive `lifeList`.

**Acceptance:** `MyLifeDao` provides a reactive `Flow` query for observing `my_life` table entries; `LifeViewModel.lifeList` automatically reflects changes when items are added or deleted without requiring manual refresh triggers; `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.life.*"` passes.

---

## Task 4 — Health patient sync decoupling and UI state encapsulation

**Roadmap:** 3 (Expand viewmodel and use-case layers), 5 (Consolidate sync and upload workflow)

**Verified problem:** `HealthViewModel` (`app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt:33`) exposes `healthSyncUpdates: Flow<Unit>`, which maps `realtimeSyncManager.updatesFor(HEALTH_TABLE)`. `MyHealthFragment` (`app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt:70`) collects this flow only to turn around and call `viewModel.refreshSelectedPatient()`. The UI Fragment is acting as an unnecessary intermediary routing data-layer sync events back into the ViewModel.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthViewModelTest.kt`

**Work:**

1. In `HealthViewModel`: Launch a coroutine in `init` that collects `realtimeSyncManager.updatesFor(HEALTH_TABLE).filter { it.shouldRefreshUI }.debounce(SYNC_REFRESH_DEBOUNCE_MS)` and calls `refreshSelectedPatient()` internally. Remove the public `val healthSyncUpdates: Flow<Unit>`.
2. In `MyHealthFragment`: Remove the `collectWhenStarted(viewModel.healthSyncUpdates)` block in `onViewCreated()`.
3. In `HealthViewModelTest`: Update unit tests to verify that emitting a health table update on `RealtimeSyncManager` triggers patient data refresh within the ViewModel without external fragment intervention.

**Acceptance:** `HealthViewModel` manages its own lifecycle-aware sync subscription internally; `MyHealthFragment` contains no references to sync update flows or `healthSyncUpdates`; all tests in `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthViewModelTest.kt` pass.

---

## Task 5 — Resource viewer platform decoupling and PDF extraction separation

**Roadmap:** 1 (Finish cleaning the data layer), 3 (Expand viewmodel and use-case layers), 9 (Kotlin Multiplatform)

**Verified problem:** `ResourceViewerViewModel` (`app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt:28`) injects `@ApplicationContext private val context: Context` and performs Android-specific side-effects: calling `PDFBoxResourceLoader.init(context)` (line 89), invoking `DownloadUtils.openDownloadService(context, ...)` (line 79), and resolving `context.getExternalFilesDir(null)` (line 74). This violates ViewModel separation of concerns and prevents porting the ViewModel to Kotlin Multiplatform.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModelTest.kt`

**Work:**

1. In `ResourceViewerViewModel`: Remove `@ApplicationContext private val context: Context` from the constructor. Remove `getExternalFilesDir()` and `downloadResource()`. Keep `extractPdfText(file: File)` focused purely on file reading using `PDDocument.load(file)` without initializing `PDFBoxResourceLoader`.
2. In `ResourceViewerFragment`: Initialize `PDFBoxResourceLoader.init(requireContext().applicationContext)` during fragment setup where Android lifecycle is available, and invoke `DownloadUtils.openDownloadService` directly from the UI layer when download triggers occur.
3. In `ResourceViewerViewModelTest`: Remove `ApplicationProvider` context mocking and instantiate `ResourceViewerViewModel` without an Android context.

**Acceptance:** `ResourceViewerViewModel` has zero `android.content.Context` imports and zero `@ApplicationContext` parameters; PDF text extraction and resource rating checks operate without depending on platform context injection in the ViewModel; all tests in `ResourceViewerViewModelTest` pass without Robolectric context setup.

---

## Task 6 — Retry worker repository direct dependency consolidation

**Roadmap:** 4 (Complete dependency-injection cleanup), 5 (Consolidate sync and upload workflow)

**Verified problem:** `RetryQueueWorker` (`app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueueWorker.kt:33`) injects both `private val retryQueue: RetryQueue` and `private val retryRepository: RetryRepository`. Every method called on `retryQueue` (`tryStartProcessing`, `getPendingOperations`, `cleanup`, `finishProcessing`) is a 1-line pass-through forwarder to `retryRepository`. Injecting both creates redundant indirection, violates single-dependency boundaries, and complicates testing.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueueWorker.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt`

**Work:**

1. In `RetryQueueWorker`: Remove `private val retryQueue: RetryQueue` from `@AssistedInject constructor`. Replace all calls to `retryQueue.tryStartProcessing()`, `retryQueue.getPendingOperations()`, `retryQueue.cleanup()`, and `retryQueue.finishProcessing()` with direct calls to `retryRepository`.
2. In `RetryQueue`: Keep `RetryQueue` scoped exclusively to upload pipeline callers (`UploadCoordinator`), removing worker forwarders that are redundant with `RetryRepository`.
3. In `RetryQueueWorkerTest`: Remove the `retryQueue` mock, verifying queue lifecycle actions directly on `retryRepository`.

**Acceptance:** `RetryQueueWorker` constructor accepts only `RetryRepository` (plus `SyncManager`, context, and worker params), eliminating the pass-through proxy; unit tests in `RetryQueueWorkerTest` pass without mocking `RetryQueue`; `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.retry.*"` passes.

---

## Task 7 — Calendar reactive pipeline and UI model separation

**Roadmap:** 3 (Expand viewmodel and use-case layers), 7 (Optimize remaining performance hotspots), 10 (Compose Multiplatform)

**Verified problem:** `CalendarFragment` (`app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt:133`) performs in-memory date calculations and list filtering directly inside the click listener on the main thread: `meetups.filter { Instant.ofEpochMilli(it.startDate).atZone(ZoneId.systemDefault()).toLocalDate() == clickedDate }`. In addition, `CalendarViewModel` does not inject `DispatcherProvider` and lacks a dedicated method to query meetups by date.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModelTest.kt`

**Work:**

1. In `CalendarViewModel`: Inject `private val dispatcherProvider: DispatcherProvider`. Add `fun getMeetupsForDate(date: LocalDate): List<Meetup>` that filters meetups by local date using `dispatcherProvider.default`.
2. In `CalendarFragment`: Replace the inline list filtering inside `setOnCalendarDayClickListener` with `viewModel.getMeetupsForDate(clickedDate)`.
3. In `CalendarViewModelTest`: Add unit tests validating `getMeetupsForDate` with matching and non-matching dates.

**Acceptance:** `CalendarViewModel` handles date filtering off the main thread using `DispatcherProvider`; `CalendarFragment` contains no direct date parsing/filtering loops; `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.calendar.*"` passes.

---

## Task 8 — Diagnostics logging platform decoupling and ApkLog typed batching

**Roadmap:** 1 (Finish cleaning the data layer), 8 (Improve code health and add tests), 9 (Kotlin Multiplatform)

**Verified problem:** `DiagnosticsRepositoryImpl` (`app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt:18`) directly imports `android.util.Log` and logs errors to Logcat (`Log.w(TAG, ...)`). North Star #9 requires repositories to be platform-free with zero `android.*` imports. Furthermore, `saveLogToRoom` and `saveLogsToRoom` do not specify coroutine dispatchers using `DispatcherProvider`.

**Files (3):**

- `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ApkLogDao.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt`

**Work:**

1. In `DiagnosticsRepositoryImpl`: Remove `import android.util.Log`. Inject `private val dispatcherProvider: DispatcherProvider`. Wrap database operations in `withContext(dispatcherProvider.io)`. Replace `Log.w` with safe failure returns or an injectable logger abstraction.
2. In `ApkLogDao`: Ensure `markUploadedBatch` handles chunking safely and returns the count of updated records using typed parameters.
3. In `DiagnosticsRepositoryImplTest`: Add tests confirming that `saveLogToRoom` and `saveLogsToRoom` execute on `dispatcherProvider.io` and handle exceptions cleanly.

**Acceptance:** `DiagnosticsRepositoryImpl` has zero `android.*` imports; database insertions in `DiagnosticsRepositoryImpl` run on `dispatcherProvider.io`; `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DiagnosticsRepositoryImplTest"` passes.

---

## Task 9 — Notification DAO query typing and batch update optimization

**Roadmap:** 1 (Finish cleaning the data layer), 7 (Optimize remaining performance hotspots)

**Verified problem:** In `NotificationDao.markSynced()` (`app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt:66`), when `rev` is non-null, the DAO constructs raw SQL using `StringBuilder` string concatenation with `CASE id WHEN ? THEN ?` and executes it via `@RawQuery` and `SimpleSQLiteQuery`. This bypasses Room's compile-time type-safety. Additionally, `NotificationDao` lacks a reactive `Flow` query for unread counts.

**Files (4):**

- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`

**Work:**

1. In `NotificationDao`: Add a typed entity update projection:
   ```kotlin
   data class NotificationSyncUpdate(
       @ColumnInfo(name = "id") val id: String,
       @ColumnInfo(name = "rev") val rev: String?,
       @ColumnInfo(name = "needsSync") val needsSync: Boolean = false
   )
   @Update(entity = AppNotification::class)
   suspend fun updateSyncStatus(updates: List<NotificationSyncUpdate>)
   ```
   Remove `@RawQuery suspend fun markSyncedNonNullRevsRaw`. Add `@Query("SELECT COUNT(*) FROM notifications WHERE (userId = :userId OR (:isAdmin = 1 AND userId = 'SYSTEM')) AND isRead = 0") fun observeUnreadCount(userId: String, isAdmin: Boolean): Flow<Int>`.
2. In `NotificationsRepository`: Add `fun observeUnreadCount(userId: String?, isAdmin: Boolean = false): Flow<Int>`.
3. In `NotificationsRepositoryImpl`: Implement `markNotificationsSynced` by mapping sync results directly to `NotificationSyncUpdate` list chunks and delegating to `notificationDao.updateSyncStatus()`. Implement `observeUnreadCount`.
4. In `NotificationsRepositoryImplTest`: Update unit tests to verify typed batch sync updates and flow emission.

**Acceptance:** `NotificationDao` has no raw SQL string concatenation or `SimpleSQLiteQuery` usage for sync status updates; Room verifies the sync update query at compile time via typed entity projections; `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.NotificationsRepositoryImplTest"` passes.

---

## Task 10 — Markdown challenge dialog ViewModel state encapsulation

**Roadmap:** 3 (Expand viewmodel and use-case layers), 8 (Improve code health and add tests)

**Verified problem:** `MarkdownDialogFragment` (`app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt:121`) contains embedded business logic: calculating dollar earnings (`val earnedDollarsVoice = allVoiceCount * 2`, `val earnedDollarsSurvey = if (!hasUnfinishedSurvey) 1 else 0`, `((total.toDouble() / 500) * 100).toInt()`) and matching hardcoded status strings (`"no iniciado"`, `"terminado"`). Furthermore, it couples tightly to the host activity via `(activity as DashboardActivity).result`. `MarkdownViewModel` is currently an anemic wrapper with a single method.

**Files (2):**

- `app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt`

**Work:**

1. In `MarkdownViewModel`: Add a pure calculation function or state model:
   ```kotlin
   data class ChallengeProgress(val totalDollars: Int, val progressPercent: Int, val actionType: ActionType)
   enum class ActionType { START, NEXT, SYNC, CONTINUE }
   fun calculateProgress(allVoiceCount: Int, hasUnfinishedSurvey: Boolean, courseStatus: String, voiceCount: Int): ChallengeProgress
   ```
2. In `MarkdownDialogFragment`: Replace inline math and string parsing with `viewModel.calculateProgress(...)`. Replace `(activity as DashboardActivity).result` with a safe null-checked cast `(activity as? DashboardActivity)?.result` to prevent `ClassCastException` if displayed from other contexts.

**Acceptance:** Progress percentage and dollar reward calculations are unit-testable in `MarkdownViewModel`; `MarkdownDialogFragment` contains no hardcoded status string logic or raw numeric multiplier formulas; `MarkdownDialogFragment` gracefully handles hosting activities without throwing `ClassCastException`.

---

## Self-check results

- **R1**: Exactly 10 tasks, each independently mergeable in any order. ✔
- **R2**: Exactly 31 unique files cited across all 10 tasks with 0 file overlaps between any two tasks. ✔
- **R3**: All 33 currently open PRs and their 438 modified files were audited; zero cited files are touched by any open PR. ✔
- **R4**: Every cited file path, class, interface, method, and line number exists in the repository and was confirmed against source code. ✔
- **R5**: Every task is bounded under ~150 changed lines, touches 2–4 files, adds zero new dependencies, and contains no TODO placeholders. ✔
- **R6**: No implementation code is committed in this document; the work orders are the deliverable. ✔
