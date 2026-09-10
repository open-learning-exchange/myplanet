2026-09-10 · 022ee7d1f · open PRs checked: 13848,13928,14427,14650,14883,14893,14960,15108,15198,15226,15266,15267,15412,15519,15559,15808,15820,15824,15825,15951,16101,16270,16594,16623,16624,16690,16986,16987,16988,16989

# myPlanet repository-boundary work orders

North star for every task where it applies: move Android, generated `R`, and `androidx` details out of the repository layer so repositories, models, and sync/upload logic can one day live in platform-free Kotlin (roadmap 9). Each task is self-contained and must not touch files claimed by any other task.

---

### 1. Move user-data upload scheduling out of SyncRepositoryImpl (roadmap 4+5+9)

context: `SyncRepositoryImpl.kt` imports `android.content.Context`, `android.os.SystemClock`, and `androidx.work.*`, then enqueues `UserDataWorker` via `WorkManager.getInstance(context)` at line 71 and reads `SystemClock.elapsedRealtime()` at lines 159, 169, 176, 200, 205, and 209 for batch timing. This ties the repository to Android scheduling and elapsed-time APIs. Extracting the scheduling into a service class and adding `elapsedRealtime()` to `TimeProvider` removes those Android imports from the repository and makes upload scheduling testable.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt` (constructor lines 39–50, `enqueueUserDataUpload`, `SystemClock` usages)
- `app/src/main/java/org/ole/planet/myplanet/utils/TimeProvider.kt` (interface and `SystemTimeProvider`)
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UserDataUploadScheduler.kt` (new)
- `app/src/test/java/org/ole/planet/myplanet/utils/TestTimeProvider.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt` (constructor setup)
- leave `UserDataWorker.kt` and all DI modules untouched

steps:
1. Add `fun elapsedRealtime(): Long` to `TimeProvider`; implement it in `SystemTimeProvider` with `SystemClock.elapsedRealtime()` and in `TestTimeProvider`.
2. Replace every `SystemClock.elapsedRealtime()` call in `SyncRepositoryImpl` with `timeProvider.elapsedRealtime()` and remove the `SystemClock`, `WorkManager`, and `Context` imports.
3. Remove `Context` from `SyncRepositoryImpl`'s constructor and add `private val userDataUploadScheduler: UserDataUploadScheduler`.
4. Create `UserDataUploadScheduler` with an `@Inject` constructor taking `Context`; move the `OneTimeWorkRequest`, `WorkManager` unique-work enqueueing, and `WorkInfo` to `SyncUiState` mapping there.
5. Update `SyncRepositoryImplTest` to remove the `Context` and `SystemClock` static mocks and provide a relaxed `UserDataUploadScheduler` mock.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the sync screen still starts login and bulk uploads; `processShelfParallel` timing logic still records durations.

size budget: ~80 changed lines, 5 files

out of scope: `android.util.Log` calls inside `SyncRepositoryImpl`; those stay for a later logging pass.

---

### 2. Extract MyLife SharedPreferences cache into a platform-free data source (roadmap 1+9)

context: `LifeRepositoryImpl.kt` imports `androidx.core.content.edit` and `com.google.gson.Gson` to read/write a `CachedMyLifeItem` JSON cache into `SharedPrefManager.rawPreferences` (lines 109–140). It also repeats `.sortedBy { it.weight }` on lists that `MyLifeDao` already returns ordered by `weight`. Moving the cache serialization behind `MyLifeCacheDataSource` removes Android SharedPreferences and Gson from the repository and eliminates redundant sorting.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` (cache read/write, `CachedMyLifeItem`, `getMyLifeByUserId`, `getVisibleMyLifeByUserId`, `seedMyLifeIfEmpty`, `getMyLifeForDashboard`)
- `app/src/main/java/org/ole/planet/myplanet/data/local/MyLifeCacheDataSource.kt` (new)
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt` (constructor setup)
- leave `MyLifeDao.kt` and `MyLife.kt` untouched

steps:
1. Create `MyLifeCacheDataSource` with `@Inject constructor(private val sharedPrefManager: SharedPrefManager, private val gson: Gson)`; move `CachedMyLifeItem`, `MY_LIFE_CACHE_PREFIX`, and the cache read/write logic there. Expose `get(userId): List<MyLife>?` and `set(userId, items)` (or equivalent).
2. Replace `LifeRepositoryImpl`'s cache access with calls to `myLifeCacheDataSource` and remove the `gson`, `androidx.core.content.edit`, and `TypeToken` imports.
3. Keep `SharedPrefManager` only for `getUserId()` fallback; remove direct `rawPreferences` access from the repository.
4. Drop `.sortedBy { it.weight }` from `getMyLifeByUserId`, `getVisibleMyLifeByUserId`, `seedMyLifeIfEmpty`, and `getMyLifeForDashboard` because `MyLifeDao` queries already use `ORDER BY weight ASC`.
5. Update `LifeRepositoryImplTest` to construct `MyLifeCacheDataSource` with its existing mocks and pass it into the repository.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the My Life dashboard preserves visibility toggles and reordering across process restarts.

size budget: ~80–100 changed lines, 3 files

out of scope: replacing `SharedPrefManager` itself or introducing a Room-backed cache; this task is a boundary extraction only.

---

### 3. Move team attachment file path resolution out of EnterprisesRepositoryImpl (roadmap 1+9)

context: `EnterprisesRepositoryImpl.kt` holds an `android.content.Context` solely so `attachTeamImage` can call `MyTeam.getAttachmentFile(context, teamId, imageName)` at line 130. The rest of the repository has no Android dependencies. Wrapping that path lookup in `TeamAttachmentFileProvider` lets `EnterprisesRepositoryImpl` drop `Context` entirely.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` (constructor lines 19–24, `attachTeamImage` lines 128–134)
- `app/src/main/java/org/ole/planet/myplanet/data/local/TeamAttachmentFileProvider.kt` (new)
- `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt` (constructor setup)
- leave `MyTeam.kt` untouched

steps:
1. Create `TeamAttachmentFileProvider` with `@Inject constructor(@ApplicationContext private val context: Context)` and a `getAttachmentFile(teamId: String, imageName: String): File?` method that delegates to `MyTeam.getAttachmentFile`.
2. Remove `Context` from `EnterprisesRepositoryImpl`'s constructor and inject `TeamAttachmentFileProvider` instead.
3. In `attachTeamImage` replace `MyTeam.getAttachmentFile(context, ...)` with `teamAttachmentFileProvider.getAttachmentFile(...)`.
4. Update `EnterprisesRepositoryImplTest` to create `TeamAttachmentFileProvider(context)` and pass it to the repository.

acceptance: `./gradlew testDefaultDebugUnitTest` green; adding a finance report with an image still writes the attachment to `team_attachments/<teamId>/<imageName>`.

size budget: ~30–40 changed lines, 3 files

out of scope: moving the actual `writeBytes` call out of the repository; keep that I/O inside `attachTeamImage` for now.

---

### 4. Move dictionary asset file access out of DictionaryRepositoryImpl (roadmap 1+9)

context: `DictionaryRepositoryImpl.kt` imports `android.content.Context` and `org.ole.planet.myplanet.utils.FileUtils` to check for and read the bundled JSON dictionary asset (lines 43–55). The repository only needs the JSON text, not file-system details. Introducing `DictionaryAssetDataSource` removes `Context` and `FileUtils` from the repository.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt` (constructor and `insertDictionaryData`)
- `app/src/main/java/org/ole/planet/myplanet/data/local/DictionaryAssetDataSource.kt` (new)
- `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt` (constructor setup)
- leave `DictionaryEntity.kt`, `DictionaryDao.kt`, and `FileUtils.kt` untouched

steps:
1. Create `DictionaryAssetDataSource` with `@Inject constructor(@ApplicationContext private val context: Context)` and methods such as `isDictionaryAssetPresent(): Boolean` and `readDictionaryAssetText(): String?` that wrap `FileUtils.checkFileExist`, `FileUtils.getSDPathFromUrl`, and `FileUtils.getStringFromFile`.
2. Remove `Context` and `FileUtils` imports and constructor parameters from `DictionaryRepositoryImpl`; inject `DictionaryAssetDataSource`.
3. Replace the `FileUtils` calls in `insertDictionaryData` with calls to the data source.
4. Update `DictionaryRepositoryImplTest` to create `DictionaryAssetDataSource(context)` and pass it to the repository.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the dictionary screen still seeds from the bundled asset and `findByWord` returns the same result.

size budget: ~40–60 changed lines, 3 files

out of scope: adding an index to `DictionaryEntity.word`; that is a separate DAO optimization.

---

### 5. Inject an AppVersionProvider into DiagnosticsRepositoryImpl (roadmap 1+9)

context: `DiagnosticsRepositoryImpl.kt` references the generated `org.ole.planet.myplanet.BuildConfig.VERSION_NAME` directly at lines 62 and 80 when building `ApkLog` records. A tiny `AppVersionProvider` abstraction isolates the generated Android build config from the repository, leaving only the provider to import `BuildConfig`.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt` (lines 62 and 80)
- `app/src/main/java/org/ole/planet/myplanet/data/local/AppVersionProvider.kt` (new)
- `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt` (constructor and assertions)
- leave `BuildConfig` generation untouched

steps:
1. Create `AppVersionProvider` with `@Inject constructor()` and a `versionName` property or function that reads `BuildConfig.VERSION_NAME`.
2. Add `private val appVersionProvider: AppVersionProvider` to `DiagnosticsRepositoryImpl`'s constructor.
3. Replace the two `BuildConfig.VERSION_NAME` references with `appVersionProvider.versionName` and remove the `BuildConfig` import.
4. Update `DiagnosticsRepositoryImplTest` to construct (or mock) `AppVersionProvider` and inject it; keep the existing version assertion using `BuildConfig.VERSION_NAME` or the provider value.

acceptance: `./gradlew testDefaultDebugUnitTest` green; crash and ANR logs still include the current app version.

size budget: ~20–35 changed lines, 3 files

out of scope: removing `UUID` or `SharedPrefManager` from `DiagnosticsRepositoryImpl`; they are not Android-only.

---

### 6. Move storage path resolution out of ConfigurationsRepositoryImpl (roadmap 1+9)

context: `ConfigurationsRepositoryImpl.kt` still calls `FileUtils.getSDPathFromUrl(context, path)` at line 249 and `File(FileUtils.getOlePath(context))` at line 416, requiring `java.io.File` and `FileUtils` imports. The repository is otherwise concerned with configuration, not file paths. A `StoragePathResolver` data source lets the repository delegate path construction.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` (constructor lines 46–50, `checkCheckSum` line 249, `clearFirstRunStorageAndSetFlag` lines 416–418)
- `app/src/main/java/org/ole/planet/myplanet/data/local/StoragePathResolver.kt` (new)
- `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt` (constructor and `FileUtils` mocks)
- leave the remaining `Context.getString`/`R.string` error paths untouched

steps:
1. Create `StoragePathResolver` with `@Inject constructor(@ApplicationContext private val context: Context)` and methods `resolveFileFromUrl(url: String): File` and `resolveOleDirectory(): File` that wrap `FileUtils.getSDPathFromUrl` and `File(FileUtils.getOlePath(...))`.
2. Add `private val storagePathResolver: StoragePathResolver` to `ConfigurationsRepositoryImpl`'s constructor.
3. Replace `FileUtils.getSDPathFromUrl(context, path)` with `storagePathResolver.resolveFileFromUrl(path)` and `File(FileUtils.getOlePath(context))` with `storagePathResolver.resolveOleDirectory()`; remove the `FileUtils` and `java.io.File` imports.
4. Update `ConfigurationsRepositoryImplTest` to create `StoragePathResolver(context)` and pass it to the repository; reuse existing `FileUtils` object mocks because the resolver still delegates to `FileUtils`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the `checkCheckSum` and first-run storage wipe flows still use the correct files.

size budget: ~50–80 changed lines, 3 files

out of scope: removing the remaining `Context.getString`/`R.string` calls; the repository still needs those for error messages.

---

### 7. Let NotificationsRepository compute the team chat count (roadmap 1+3+9)

context: `TeamsVoicesViewModel.kt` loads the full news list from `voicesRepository.getFilteredNews(teamId)` and then passes `newsList.size` into `notificationsRepository.updateTeamNotification(teamId, newsList.size)` at lines 59–60. `NotificationsRepositoryImpl` already injects `voicesRepository` and `VoicesRepository` exposes `countTopLevelByTeam(teamId)`, so the repository can compute the count itself. This prevents the UI layer from leaking voice-repo size details into the notification repository.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` (line 25)
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (line 285)
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt` (lines 59–60)
- `app/src/test/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModelTest.kt` (line 69)
- `app/src/test/java/org/ole/planet/myplanet/repository/TeamChatBadgeIntegrationTest.kt` (lines 106, 119, 134)
- leave `VoicesRepository.kt` and `VoicesRepositoryImpl.kt` untouched (open-PR files)

steps:
1. Change `NotificationsRepository.updateTeamNotification` signature from `(teamId: String, count: Int)` to `(teamId: String)`.
2. In `NotificationsRepositoryImpl.updateTeamNotification`, compute `val count = voicesRepository.countTopLevelByTeam(teamId).toInt()` and use that for `lastCount`.
3. Update `TeamsVoicesViewModel.getFilteredNews` to call `notificationsRepository.updateTeamNotification(teamId)` with no count argument.
4. Update `TeamsVoicesViewModelTest` to verify the one-argument call and remove the count parameter from `coVerify`.
5. Update `TeamChatBadgeIntegrationTest` to call the new one-argument form (the test may still use `countTopLevelByTeam` for its own assertions).

acceptance: `./gradlew testDefaultDebugUnitTest` green; opening a team voices feed still refreshes the chat notification badge with the correct top-level count.

size budget: ~30–50 changed lines, 5 files

out of scope: changing how `getFilteredNews` filters; that belongs to the voices feature.

---

### 8. Expose resource and exam counts in CourseStepUiState (roadmap 3+7+10)

context: `CourseStepFragment.kt` derives list sizes at render time: `state.resources.size` for the resources button label at line 88 and `exams.size` for the test button labels at lines 171 and 173. `CoursesStepsViewModel.kt` already owns the lists in `CourseStepUiState` (lines 30–43). Adding `resourceCount`, `examCount`, and `surveyCount` fields to the state lets the fragment consume pre-computed counts and reduces UI-side derivation, which also helps Compose portability.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` (lines 30–43 and `refreshInlineResources`)
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt` (lines 87–89 and 166–184)
- leave `CourseStep` model and repository interfaces untouched

steps:
1. Add `resourceCount: Int`, `examCount: Int`, and `surveyCount: Int` to `CourseStepUiState` with defaults derived from the list fields, and update `loadStep` and `refreshInlineResources` to set them explicitly.
2. In `CourseStepFragment`, change `btnResources.text` to use `state.resourceCount` and update `hideTestIfNoQuestion` to take `examCount` and `surveyCount` instead of the `exams` list.
3. Use the new counts for test/survey button visibility and label formatting.
4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the course step screen still shows `Resources (N)` and `Retake test (N)` with the correct numbers.

size budget: ~30–45 changed lines, 2 files

out of scope: converting the fragment to Compose; this task only moves count derivation into the ViewModel.

---

### 9. Use repository-provided member status and counts in team member screens (roadmap 3+7)

context: `MembersFragment.kt` scans the full joined-members list to determine whether the current user is a leader: `members.any { it.user.id == currentUserId && it.isLeader }` at line 99. `RequestsFragment.kt` passes `uiState.members.size` to `showNoData` at line 53 even though `RequestsUiState` already exposes `memberCount` from `getJoinedMemberCount`. Both are UI-side derivations that the repository already answers via `TeamsRepository.isTeamLeader` and `getJoinedMemberCount`.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt` (lines 97–102)
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt` (line 53)
- leave `RequestsViewModel.kt`, `TeamsRepository` interfaces, and `TeamsRepositoryImpl.kt` untouched

steps:
1. In `MembersFragment.loadMembers`, after resolving `currentUserId`, set `isLeader = teamsRepository.isTeamLeader(teamId, currentUserId)` instead of scanning `members`.
2. Keep the `members` list load for the adapter.
3. In `RequestsFragment`, replace `uiState.members.size` with `uiState.memberCount` in the `showNoData` call.
4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the members list and requests empty-state still reflect the correct leader and count.

size budget: ~10–15 changed lines, 2 files

out of scope: moving `loadMembers` into a ViewModel; that is a larger UI refactor.

---

### 10. Add a composite Room index on retry_operation(status, nextRetryTime, attemptCount) (roadmap 1+7)

context: `RetryOperation.kt` defines `indices = [Index("uploadType"), Index("itemId"), Index("status")]` at line 15, but `RetryDao.getPending` filters by `status = 'pending' AND nextRetryTime <= :now AND attemptCount < maxAttempts` at lines 25–29. A composite index on `(status, nextRetryTime, attemptCount)` lets SQLite answer that `WHERE` clause without scanning the table. `AppDatabase` uses `fallbackToDestructiveMigration`, so the index change is applied automatically on next install.

files:
- `app/src/main/java/org/ole/planet/myplanet/model/RetryOperation.kt` (line 15)
- leave `RetryDao.kt` and `RetryRepositoryImpl.kt` untouched

steps:
1. Replace the single `Index("status")` with `Index(value = ["status", "nextRetryTime", "attemptCount"])` (or add it while keeping the others if the codebase convention prefers explicit single-column indexes).
2. Verify `RetryDao.getPending` still reads `status = 'pending' AND nextRetryTime <= :now AND attemptCount < maxAttempts`.
3. Run `./gradlew testDefaultDebugUnitTest` and `./gradlew kspDefaultDebugKotlinAndroid` (or the build task that runs KSP) to confirm the schema compiles.

acceptance: `./gradlew testDefaultDebugUnitTest` green and KSP succeeds; the retry queue still loads pending operations in the same order.

size budget: ~2 changed lines, 1 file

out of scope: rewriting `getPending` SQL or adding additional covering indexes; only add the composite index that matches the current query.
