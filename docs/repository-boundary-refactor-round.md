# myPlanet repository-boundary refactor work orders

2026-09-04 · `9ff1273dc95f8cbd3590fca12ca821454b2e27bc` · open PRs checked: 4075, 8175, 10993, 13287, 13355, 13415, 13604, 13657, 13848, 13928, 14427, 14650, 14883, 14893, 14960, 15108, 15158, 15198, 15226, 15266, 15267, 15412, 15519, 15559, 15699, 15808, 15820, 15824, 15825, 15951, 16101, 16270, 16594, 16619, 16623, 16624, 16647, 16661, 16677, 16680, 16686, 16688, 16690, 16693, 16698, 16701, 16702, 16705

---

### 1. Add a one-shot non-archived team report query for CSV export (roadmap 1+7)

context: `EnterprisesRepositoryImpl.exportReportsAsCsv` (lines 102-105) loads every report row for the team/docType into Kotlin memory, then filters archived status and sorts by `createdDate`. `TeamDao` already exposes the same filtering and ordering as a `Flow` query at line 23, but lacks a suspend one-shot equivalent. Pushing the predicate into SQL reduces memory and CPU for large enterprise teams.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (add `getNonArchivedReportsByTeamIdSorted`).
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` (call the new query; remove Kotlin `filter`/`sortedByDescending`).

neighbors to leave alone: `EnterprisesRepository.kt`, `EnterprisesViewModel.kt`, `EnterprisesReportsFragment.kt`, `MyTeam.kt`.

steps:
1. In `TeamDao.kt`, add a new `@Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = 'report' AND IFNULL(status, '') != 'archived' ORDER BY createdDate DESC") suspend fun getNonArchivedReportsByTeamIdSorted(teamId: String): List<MyTeam>` next to the existing Flow query.
2. In `EnterprisesRepositoryImpl.kt`, change `exportReportsAsCsv` to call `teamDao.getNonArchivedReportsByTeamIdSorted(teamId)` and remove the `.filter { it.status != "archived" }.sortedByDescending { it.createdDate }` chain.
3. Clean up any now-unused imports in the two changed files.
4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; enterprise reports CSV export still lists non-archived reports newest-first and produces the same content as before.

size budget: ~5 changed lines, 2 files.

out of scope: no schema or UI changes; do not alter the CSV content format.

---

### 2. Resolve current user id inside `TeamCoursesViewModel` (roadmap 3+9)

context: `TeamCoursesFragment.updateCoursesList` (lines 60-63) reaches directly into `sharedPrefManager.getUserId()` and passes the id into `TeamCoursesViewModel.loadCourses`. This is a UI/data leak that also blocks KMP, because the Fragment owns a platform `SharedPrefManager` lookup that belongs in the repository/viewmodel layer. `UserRepository.getCurrentUserId()` already provides the same value suspendably.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt` (remove `sharedPrefManager` read from `updateCoursesList`).
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt` (inject `UserRepository`; compute `currentUserId` inside `loadCourses`).

neighbors to leave alone: `BaseTeamFragment`, `SharedPrefManager`, `CoursesRepository`, `TeamsRepository`.

steps:
1. Add `private val userRepository: UserRepository` to the `TeamCoursesViewModel` constructor.
2. Change `loadCourses(teamId: String, currentUserId: String)` to `loadCourses(teamId: String)` and compute `currentUserId` from `userRepository.getCurrentUserId()` with the same `"--"` fallback.
3. In `TeamCoursesFragment.updateCoursesList`, call `viewModel.loadCourses(teamId)` and remove the `sharedPrefManager` usage.
4. Clean up imports and run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the team courses screen still lists the correct courses and the remove-course action is still gated to the team creator.

size budget: ~8 changed lines, 2 files.

out of scope: no changes to course loading logic or repository interfaces.

---

### 3. Derive the team name from fragment arguments in `EnterprisesReportsFragment` (roadmap 3+9)

context: `EnterprisesReportsFragment` calls `teamsRepository.getTeamNameFromPrefs()` twice (lines 69 and 84) to build the CSV file name and CSV content. `BaseTeamFragment` already exposes `getEffectiveTeamName()` (lines 90-92) from `team` or arguments, which is the actual team name for the current screen and does not require a repository lookup from Android preferences.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt` (replace `teamsRepository.getTeamNameFromPrefs()` with `getEffectiveTeamName()`).

neighbors to leave alone: `EnterprisesRepositoryImpl`, `EnterprisesViewModel`, `EnterprisesReportsAdapter`, `TeamsRepository`.

steps:
1. In the `exportCSV` click listener, replace `teamsRepository.getTeamNameFromPrefs()?.replace(" ", "_")` with `getEffectiveTeamName().replace(" ", "_")`.
2. In the activity-result block, replace `teamsRepository.getTeamNameFromPrefs() ?: ""` with `getEffectiveTeamName()`.
3. Remove any `teamsRepository` references that become unused in this file.
4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; exporting the enterprise CSV still uses the current team name in both the filename and the CSV header.

size budget: ~4 changed lines, 1 file.

out of scope: no CSV formatting changes or `EnterprisesRepository` edits.

---

### 4. Replace `MainApplication.applicationScope` with an injected scope in `ResourceDownloadCoordinator` (roadmap 4+9)

context: `ResourceDownloadCoordinator.startBackgroundDownload` (line 19) launches work on the global `MainApplication.applicationScope`. `ServiceModule` already provides an injectable `@ApplicationScope CoroutineScope`, and using it removes a direct `MainApplication` dependency from a service class, advancing both DI cleanup and the KMP north star.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/ResourceDownloadCoordinator.kt` (constructor and `startBackgroundDownload`).

neighbors to leave alone: `MainApplication`, `DownloadUtils`, `ConfigurationsRepository`.

steps:
1. Add `private val applicationScope: CoroutineScope` to the constructor, annotated with `@ApplicationScope`.
2. Add imports for `kotlinx.coroutines.CoroutineScope` and `org.ole.planet.myplanet.di.ApplicationScope` if needed.
3. Replace `MainApplication.applicationScope.launch` with `applicationScope.launch`.
4. Remove the now-unused `org.ole.planet.myplanet.MainApplication` import.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; background resource downloads still launch only when the server is reachable.

size budget: ~5 changed lines, 1 file.

out of scope: no changes to download scheduling or `MainApplication` itself.

---

### 5. Inject `ServerReachabilityProvider` into `SubmissionsUploader` (roadmap 4+9)

context: `SubmissionsUploader.checkAvailableServer` (lines 34-35 and 48-49) calls `MainApplication.isServerReachable(url)` static extension. `ServerReachabilityProvider` is already an injectable `@Singleton` that exposes the same `suspend fun isServerReachable(urlString: String): Boolean`, so the static `MainApplication` dependency can be removed.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/SubmissionsUploader.kt` (constructor and reachability checks).

neighbors to leave alone: `SubmissionUploadExecutor`, `ServerUrlMapper`, `UploadManager`, `MainApplication`.

steps:
1. Add `private val serverReachabilityProvider: ServerReachabilityProvider` to the constructor.
2. Import `org.ole.planet.myplanet.utils.ServerReachabilityProvider`.
3. Replace the two `MainApplication.isServerReachable(...)` calls with `serverReachabilityProvider.isServerReachable(...)`.
4. Remove the unused `org.ole.planet.myplanet.MainApplication` import.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; submission upload still checks the primary and alternative server URLs with a 15-second timeout.

size budget: ~5 changed lines, 1 file.

out of scope: no changes to timeout logic, URL mapping, or upload execution.

---

### 6. Inject the application scope into `NotificationActionReceiver` (roadmap 4+9)

context: `NotificationActionReceiver.onReceive` (lines 26-27) uses `MainApplication.applicationScope.launch` to run notification work. The receiver is already an `@AndroidEntryPoint` Hilt `BroadcastReceiver`, so it can field-inject the `@ApplicationScope CoroutineScope` and drop the `MainApplication` reference.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt` (inject scope, replace launch call).

neighbors to leave alone: `NotificationUtils`, `DashboardActivity`, `NotificationsRepository`, `MainApplication`.

steps:
1. Add `@Inject @ApplicationScope lateinit var applicationScope: CoroutineScope` to `NotificationActionReceiver`.
2. Import `kotlinx.coroutines.CoroutineScope` and `org.ole.planet.myplanet.di.ApplicationScope` if needed.
3. Replace `MainApplication.applicationScope.launch` with `applicationScope.launch`.
4. Remove the unused `org.ole.planet.myplanet.MainApplication` import.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; tapping a notification action still marks it read and navigates correctly.

size budget: ~5 changed lines, 1 file.

out of scope: no behavioral changes to notification routing or UI.

---

### 7. Remove `@ApplicationContext` from `StorageCategoryViewModel` (roadmap 3+9)

context: `StorageCategoryViewModel` (lines 36 and 52/97) holds `@ApplicationContext` only to call `FileUtils.getOlePath(context)`. The only caller, `StorageCategoryDetailFragment`, already has a `Context`, so the path can be computed once in the Fragment and passed into the ViewModel. This removes an Android `Context` field from a ViewModel, moving toward KMP.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt` (constructor and `loadResources`/`deleteItems` signatures).
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt` (compute `olePath` and pass it).

neighbors to leave alone: `ResourcesRepository`, `FileUtils`, `StorageCategories`.

steps:
1. Remove `@ApplicationContext private val context: Context` from `StorageCategoryViewModel` constructor and remove the `Context`/`ApplicationContext` imports.
2. Change `loadResources` to `fun loadResources(olePath: String, extensions: Set<String>, allKnownExtensions: Set<String>)`.
3. Change `private fun deleteItems(items: List<OfflineResourceItem>)` to accept `olePath: String`; remove the second `FileUtils.getOlePath(context)` call inside it.
4. Update `deleteSelected` and `deleteAll` to accept `olePath: String` and forward it to `deleteItems`.
5. In `StorageCategoryDetailFragment.onViewCreated`, compute `val olePath = FileUtils.getOlePath(requireContext())` and pass it to `viewModel.loadResources`, `viewModel.deleteSelected(olePath)`, and `viewModel.deleteAll(olePath)`.
6. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the storage category detail still lists offline files and delete-selected/delete-all still removes them.

size budget: ~15 changed lines, 2 files.

out of scope: no changes to repository queries or the file-deletion logic.

---

### 8. Move `R.string` resolution out of the `MyLife` model (roadmap 3+9)

context: `MyLife.kt` companion (lines 57-70) hard-codes `R.string` resource IDs, and `LifeViewModel` (line 40) injects `@ApplicationContext` only to resolve those strings with `context::getString`. Android resource references in a model block KMP and force a `Context` into the ViewModel. The labels are UI text, so the Fragment should resolve them and pass resolved strings to the ViewModel/model.

files:
- `app/src/main/java/org/ole/planet/myplanet/model/MyLife.kt` (remove `R` import and `defaultItemPairs`; change `defaultItems` signature).
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt` (remove `@ApplicationContext`; accept a labels list).
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt` (build resolved labels and pass them to `viewModel.loadMyLifeList`).

neighbors to leave alone: `LifeRepository`, `LifeAdapter`, `MyLifeDao`.

steps:
1. In `MyLife.kt`, remove the `R` import and change `defaultItems(userId, resolveLabel: (Int) -> String)` to `defaultItems(userId, labels: List<Pair<String, String>>)`, mapping the pairs to `MyLife` objects.
2. In `LifeViewModel.kt`, remove the `Context`/`ApplicationContext` fields, update `loadMyLifeList()` to `loadMyLifeList(labels: List<Pair<String, String>>)`, and call `MyLife.defaultItems(userId, labels)`.
3. In `LifeFragment.kt`, build `val labels = listOf("ic_myhealth" to getString(R.string.myhealth), "my_achievement" to getString(R.string.achievements), "ic_submissions" to getString(R.string.submission), "ic_my_survey" to getString(R.string.my_survey), "ic_references" to getString(R.string.references), "ic_calendar" to getString(R.string.calendar), "ic_mypersonals" to getString(R.string.mypersonals))` and call `viewModel.loadMyLifeList(labels)`.
4. Clean up imports and run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the My Life dashboard still shows the same default items in the same order, and reorder/visibility still persist.

size budget: ~20 changed lines, 3 files.

out of scope: no changes to `MyLifeDao` sort order or persistence logic.

---

### 9. Move `CommunityServices` repository calls into a new `CommunityServicesViewModel` (roadmap 3+9)

context: `CommunityServicesFragment` (lines 60-61 and 100-101) calls `teamsRepository.getTeamLinks()` and `teamsRepository.isMember()` directly from lifecycle scopes. A ViewModel already exists in the `community` package (`CommunityTabViewModel`) but does not cover this tab; the repository access belongs in a dedicated `CommunityServicesViewModel` so the Fragment only consumes data.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesFragment.kt` (replace direct repository calls with ViewModel calls).
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt` (new HiltViewModel; add `getTeamLinks()` and `isMember(teamId)`).

neighbors to leave alone: `BaseTeamFragment`, `TeamsRepository`, `TeamDetailFragment`, `CommunityTabViewModel`.

steps:
1. Create `CommunityServicesViewModel` with a constructor injecting `TeamsRepository` and `UserRepository`.
2. Expose `suspend fun getTeamLinks(): List<MyTeam>` that delegates to `teamsRepository.getTeamLinks()`, and `suspend fun isMember(teamId: String): Boolean` that uses `userRepository.getCurrentUserId()` and `teamsRepository.isMember(...)`.
3. In `CommunityServicesFragment`, add `private val viewModel: CommunityServicesViewModel by viewModels()` (add the `viewModels` import and `@AndroidEntryPoint` if missing).
4. Replace the direct `teamsRepository.getTeamLinks()` call with `viewModel.getTeamLinks()` and the direct `teamsRepository.isMember(...)` call with `viewModel.isMember(teamId)`.
5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; community services still display team links and open the correct team detail with the correct `isMyTeam` value.

size budget: ~35 changed lines, 2 files (1 new).

out of scope: no changes to `TeamsRepository` logic or the `TeamDetailFragment` contract.

---

### 10. Remove `MainApplication.context` from `NewsLog`, `Rating`, `SearchActivity`, and `ApkLog` serialization (roadmap 1+9)

context: `NewsLog.serialize`, `Rating.serializeRating`, `SearchActivity.serialize`, and `ApkLog.serialize` all pull `androidId`/`customDeviceName` from `MainApplication.context` or `NetworkUtils.getCustomDeviceName(MainApplication.context)`. `UploadConfigs` already has an injectable `Context` and `SharedPrefManager`, so it can compute these values once and pass them into the model serializers. This removes a global `MainApplication` dependency from four model classes, advancing the KMP core.

files:
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt` (compute `androidId` and `customDeviceName`; update serializer lambdas for `NewsActivities`, `SearchActivity`, `Rating`, and `CrashLog`).
- `app/src/main/java/org/ole/planet/myplanet/model/NewsLog.kt` (`serialize` now accepts `androidId` and `customDeviceName`; remove `MainApplication` import).
- `app/src/main/java/org/ole/planet/myplanet/model/Rating.kt` (`serializeRating` now accepts `androidId` and `customDeviceName`; remove `MainApplication.Companion.context` import).
- `app/src/main/java/org/ole/planet/myplanet/model/SearchActivity.kt` (`serialize` now accepts `androidId` and `customDeviceName`; remove `MainApplication` and `VersionUtils` imports).
- `app/src/main/java/org/ole/planet/myplanet/model/ApkLog.kt` (`serialize` now accepts `androidId` and `customDeviceName`; call `addDocumentOrigin(androidId)` and remove unused `NetworkUtils` import).

neighbors to leave alone: `UploadCoordinator`, `DocumentOrigin.kt`, `NetworkUtils`, `VersionUtils`, and the other upload config entries.

steps:
1. In `UploadConfigs`, add `private val androidId: String? = VersionUtils.getAndroidId(context)` and `private val customDeviceName: String = sharedPrefManager.getCustomDeviceName()`, then update the four serializer lambdas to pass those values.
2. Update `NewsLog.serialize`, `Rating.serializeRating`, `SearchActivity.serialize`, and `ApkLog.serialize` signatures to accept `androidId: String?` and `customDeviceName: String`, using them for `addDocumentOrigin` and the `customDeviceName` JSON property.
3. Remove `MainApplication`, `VersionUtils`, and unused `NetworkUtils` imports from the four model files.
4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; uploads of news logs, ratings, search activities, and crash logs still contain the same `androidId`, `deviceName`, and `customDeviceName` fields.

size budget: ~30 changed lines, 5 files.

out of scope: no changes to upload endpoints, repository queries, or `DocumentOrigin` defaults.
