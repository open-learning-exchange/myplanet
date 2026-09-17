# myPlanet refactor round — repository boundaries work orders

date: 2026-09-17 · base commit: `c33390b` (master, tag v0.71.82) ·
open PRs checked: 4075, 8175, 10993, 13287, 13355, 13415, 13604, 13657, 13848,
13928, 14427, 14650, 14883, 14893, 14960, 15108, 15198, 15226, 15266, 15267,
15412, 15519, 15559, 15808, 15820, 15824, 15825, 15951, 16270, 16594, 16623,
16624, 17187, 17222, 17254, 17255, 17262 (37 open PRs, 428 unique touched files;
every path below was checked against that list and is untouched).

round focus: reinforce repository boundaries, close cross-feature data leaks,
move data functions out of UI/data/service into repositories, Room/DAO
optimizations, and smoother repository-to-view modelling.

---

### 1. drop the two never-called MyLibraryDao queries (roadmap 1+7)

context: `MyLibraryDao.kt:157` declares
`@Query("DELETE FROM my_library WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<String>)`
and `MyLibraryDao.kt:59` declares
`@Query("SELECT * FROM my_library WHERE courseId = :courseId") suspend fun getByCourseId(courseId: String): List<MyLibrary>`.
Neither has a single caller: `myLibraryDao.deleteByIds(` and `myLibraryDao.getByCourseId(`
return zero hits across `app/src/main` and `app/src/test`, while the plural
`getByCourseIds` is the one actually used (`ResourcesRepositoryImpl.kt:93`,
`TeamsRepositoryImpl.kt:93`). Two unused Room queries keep dead surface in the
DAO that layer-boundary reviews must reason about, and the delete query is the
dangerous kind of dead code — a caller would silently look correct.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt`
(lines 59 and 157). Leave `getByCourseIds`, `countByTitle`, `getSyncable`,
`getPendingUploads`, `getPublic`, `getByLocalAddress` and every other query in
this file alone — all have live callers. Do not touch `MyLibrary.kt`,
`ResourcesRepositoryImpl.kt`, or any `MyLibraryDaoTest`-style round-trip test.

steps:
1. delete the `deleteByIds(ids: List<String>)` declaration and its `@Query` at line 157.
2. delete the `getByCourseId(courseId: String)` declaration and its `@Query` at line 59.
3. re-run `grep -rn "deleteByIds\|myLibraryDao.getByCourseId(" app/src` filtered to
   `my_library`/`MyLibrary` and confirm nothing referenced them (other DAOs do have
   their own `deleteByIds`, e.g. `UserDao.kt:21`, `NewsDao.kt:95` — leave those).
4. compile: Room validates every remaining statement at build time, so a stale
   reference fails the build rather than shipping.

acceptance: `./gradlew testDefaultDebugUnitTest` green (this is a wide run; if a
faster local loop is wanted, `./gradlew :app:kspDefaultDebugKotlin` first to prove
Room accepts the reduced DAO). User-visible behaviour: unchanged — the library
list, download and sync flows behave exactly as before.

size budget: ~4 changed lines, 1 file.

out of scope: no schema/`@Entity` changes, no new queries, no repository changes.

---

### 2. stop the download dispatcher from reaching through MainApplication (roadmap 5+9)

context: `ResourcesRepositoryImpl.downloadFiles` at `ResourcesRepositoryImpl.kt:504` fires the
download service from a global static: `MainApplication.applicationScope.launch { if
(configurationsRepository.checkServerAvailability()) { ... } }`, pulled in by
`import org.ole.planet.myplanet.MainApplication` (`ResourcesRepositoryImpl.kt:20`). A repository
that schedules work on the process-wide `Application` scope cannot move to the platform-free
core (roadmap 9) and hides its concurrency behind a singleton that tests must unmock to observe
(roadmap 5). The codebase already has an injectable scope qualifier,
`org.ole.planet.myplanet.di.ApplicationScope` (`di/ServiceModule.kt:43,53-54`), and
`ResourcesRepositoryImpl` already injects fifteen other collaborators.

files:
`app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
(lines 20, 49-67, 504);
`app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`
(fields and constructor call at lines 59-113, plus the two `downloadFiles` tests at 952-970
and 979-995);
`app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryLibrarySyncTest.kt`
(constructor call at lines 62-79);
`app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryBenchmarkTest.kt`
(fields and constructor call at lines 22-57).
Do NOT touch the `MainApplication.context` read at `ResourcesRepositoryImpl.kt:399` (six test
blocks in `ResourcesRepositoryImplTest.kt` — 259-268, 289-300, 953-971, 979-995, 1148-1160 —
redirect it, and that is a separate change), do NOT touch `MainApplication.kt`,
`di/ServiceModule.kt` (open PRs own it), `ResourcesRepository.kt` or `StoragePathResolver.kt`.

steps:
1. add `@param:ApplicationScope private val applicationScope: CoroutineScope` to the
   constructor (`ResourcesRepositoryImpl.kt:49-67`); import
   `org.ole.planet.myplanet.di.ApplicationScope` and `kotlinx.coroutines.CoroutineScope`.
   Hilt already provides this binding (`di/ServiceModule.kt:53-54`), so no module edit.
2. replace `MainApplication.applicationScope.launch` at line 504 with
   `applicationScope.launch`. Keep the `configurationsRepository.checkServerAvailability()`
   guard, the `if (urls.isNotEmpty())` nesting and the
   `DownloadUtils.openDownloadService(context, urls, false)` call exactly as they are — the
   injected `context` is the same value the class already uses elsewhere.
3. delete `import org.ole.planet.myplanet.MainApplication` (line 20) only if no other reference
   to it remains in the file after step 2; the `context` read at line 399 still needs the
   import, so in practice keep that import and confirm with
   `grep -n "MainApplication" app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`.
4. in `ResourcesRepositoryImplTest`, add a `private val applicationScope = CoroutineScope(SupervisorJob() + testDispatcher)`
   field, pass it as the new final constructor argument in `setup()`, cancel it in the existing
   teardown, and in the two `downloadFiles` tests (952-970, 979-995) delete the
   `mockkObject(MainApplication)` / `every { MainApplication.applicationScope } returns scope`
   lines with their matching `unmockkObject` and local `scope.cancel()`. Leave
   `mockkObject(DownloadUtils)` and every `MainApplication.context` block untouched.
5. add the same single `applicationScope` argument to the constructor calls in
   `ResourcesRepositoryLibrarySyncTest.kt:62-79` and `ResourcesRepositoryBenchmarkTest.kt:22-57`
   (each file's existing test-dispatcher style), and drop any now-unused `scope` local from the
   two rewritten `downloadFiles` tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green, specifically
`ResourcesRepositoryImplTest`'s `downloadFiles returns provided list directly when not null`
and `downloadFiles falls back to getAllLibrariesToSync when list is null`, plus
`ResourcesRepositoryLibrarySyncTest` and `ResourcesRepositoryBenchmarkTest`. User-visible
behaviour: after a library sync, downloads still start automatically once
`checkServerAvailability()` succeeds — verify by syncing a shelf with one item and watching the
download notification appear.

size budget: ~30 changed lines, 4 files.

out of scope: do not remove the remaining `MainApplication` usages in this file, do not move
download dispatching into `DownloadService`, do not change `DownloadUtils`.

---

### 3. delete the dead suspend twin of observeNonArchivedReportsByTeamId (roadmap 1+7)

context: `TeamDao.kt:25` declares
`suspend fun getNonArchivedReportsByTeamId(teamId: String): List<MyTeam>` whose SQL is
byte-for-byte identical to `TeamDao.kt:24`'s
`fun observeNonArchivedReportsByTeamId(teamId: String): Flow<List<MyTeam>>`. The flow
variant is the live one — `EnterprisesRepositoryImpl.kt:88` returns it — while the
suspend variant has zero production callers and is exercised only by
`TeamDaoTest.kt` (lines 110-165). Keeping a second, silently divergent copy of a
report-visibility query (the archived-status rule) is exactly the drift a
repository-boundary tightening should remove.

files:
`app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (line 25);
`app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt` (lines 110-165).
Do NOT touch `TeamDao.observeNonArchivedReportsByTeamId`, `TeamDao.getByIds`,
`TeamDao.getByDocType`, `TeamDao.getRootTeamsByTypeAndIds`,
`TeamDao.getResourceIdsByTeamId`, `TeamDao.getUpdatedTeams` (all have live callers in
`TeamsRepositoryImpl.kt:89,230,355,921,969`), and do NOT touch
`EnterprisesRepositoryImpl.kt`.

steps:
1. delete the suspend `getNonArchivedReportsByTeamId` declaration and its `@Query` at
   `TeamDao.kt:25`, leaving `observeNonArchivedReportsByTeamId` at line 24 untouched.
2. in `TeamDaoTest.kt`, delete only the four tests named for the suspend variant
   (`getNonArchivedReportsByTeamId orders by createdDate descending`,
   `... excludes archived reports`, `... keeps reports with a null status`,
   `... excludes other teams and other docTypes`, lines 110-165). The four
   `observeNonArchivedReportsByTeamId` tests at lines 46-109 already cover the same
   SQL through the surviving entry point, so coverage is not lost.
3. confirm the surviving flow tests still assert archived-exclusion and null-status
   handling (lines 67-109) so the archived-status rule stays pinned after deletion.
4. delete only the now-unused `MyTeam` fixture helper if nothing else in the file
   references it; otherwise leave `report(...)` as is.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including `TeamDaoTest` with
its remaining `observeNonArchivedReportsByTeamId` cases. User-visible behaviour:
enterprise/team reports in the team page still list newest-first and still hide
archived reports.

size budget: ~50 changed lines, 2 files.

out of scope: do not migrate `EnterprisesRepositoryImpl` from flow to suspend, do not
add indexes, no schema changes.

---

### 4. make the repository the single owner of user-id normalisation (roadmap 3+9)

context: `LifeRepositoryImpl.kt:19-21` already encodes the placeholder rule
(`private fun normalizeUserId(userId: String?): String? = userId?.takeIf { it.isNotBlank() && it != "--" }`)
and applies it on every entry point. `LifeViewModel.kt:27-31` re-implements the same
rule a second time (`raw.takeIf { it.isNotBlank() && it != "--" }`) on top of a
`getCurrentUserId().orEmpty().ifEmpty { getUserModel()?.id.orEmpty() }` fallback. Two
copies of "what counts as no user" in two layers will drift; the view-layer copy
also blocks moving the ViewModel's data access behind the repository boundary
(roadmap 9). The same placeholder literal is hard-coded in five more UI sites
(`ui/teams/courses/TeamCoursesFragment.kt:61`, `base/BaseRecyclerFragment.kt:95`,
`base/BaseDashboardFragment.kt:276,331`) — those are handled by tasks 5 and 8/10, not here.

files:
`app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`;
`app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`;
`app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt` (lines 27-31);
`app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`
(lines 52-66, 104-140).
Do NOT touch `LifeRepositoryImplTest.kt` (no constructor change here),
`BaseDashboardFragment.kt`, `DashboardPluginFragment.kt`, `MyLife.kt` or `LifeCache`.

steps:
1. on `LifeRepository` (interface is 11 lines), add
   `fun effectiveUserId(userId: String?): String?` — a pure function, no suspend.
2. in `LifeRepositoryImpl`, change the existing `private fun normalizeUserId` into
   `override fun effectiveUserId(userId: String?)` so the interface and the impl share
   one body; update the seven internal call sites (`LifeRepositoryImpl.kt:24,36,59,72,79,84,101`)
   to the new name. Do not change any of their behaviour.
3. in `LifeViewModel.resolveUserId()` (lines 27-31), keep the
   `userRepository.getCurrentUserId().ifEmpty { getUserModel()?.id }` fallback but
   delegate the blank/`"--"` decision to `lifeRepository.effectiveUserId(raw)`,
   deleting the duplicated `takeIf { it.isNotBlank() && it != "--" }`.
4. update `LifeViewModelTest` so the placeholder case (line 109, currently
   `getCurrentUserId()` returns `"--"`) still asserts the repository was called with
   `null` — the assertion value does not change, only where the rule lives.
5. confirm no other caller of the old private name exists
   (`grep -rn "normalizeUserId" app/src`).

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular
`LifeViewModelTest` (all five `loadMyLifeList` cases) and `LifeRepositoryImplTest`
(its `seedMyLifeIfEmpty` cases). User-visible behaviour: the My Life list on the
dashboard and the Life screen still seed the seven default items for a real user and
still seed nothing for the `"--"` placeholder.

size budget: ~20 changed lines, 4 files.

out of scope: do not add the `UserRepository` dependency to `LifeRepositoryImpl`, do
not touch the other `"--"` call sites, no DAO changes.

---

### 5. stop persisting the Row owner through PersonalsRepository (roadmap 1+3)

context: `PersonalsRepository.kt:25` exposes
`suspend fun getPendingPersonalUploads(userId: String): List<Personal>`, implemented at
`PersonalsRepositoryImpl.kt:69-71` as a pass-through to
`personalDao.getPendingUploads(userId)`. The only caller in the entire app is the test
at `PersonalsRepositoryImplTest.kt:154-160`; no production code path reads pending
personals through this repository. Upload-and-mark for personals already goes through
`PersonalsRepositoryImpl.kt:77` (`uploadPersonalDocument`) and
`PersonalsRepositoryImpl.kt:73` (`updatePersonalAfterSync`). A repository method with a
test-only caller is not a boundary — it is specimen code that makes the upload
workflow look wider than it is (roadmap 5) and leaks the DAO's `isUploaded = 0`
predicate into the repository's public surface.

files:
`app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt` (line 25);
`app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` (lines 69-71);
`app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`
(lines 153-161).
Do NOT touch `PersonalsRepositoryImpl.uploadPersonalDocument`,
`PersonalsRepositoryImpl.updatePersonalAfterSync`,
`PersonalsRepositoryImpl.savePersonalResource`, `PersonalDao.kt`, `Personal.kt`, or
`ui/personals/PersonalsViewModel.kt`.

steps:
1. verify once more that the only references are the interface declaration, the
   implementation and the test:
   `grep -rn "getPendingPersonalUploads" app/src`.
2. delete `getPendingPersonalUploads` from the `PersonalsRepository` interface.
3. delete the override at `PersonalsRepositoryImpl.kt:69-71`.
4. delete the now-orphaned test `getPendingPersonalUploads queries correctly`
   (lines 153-161) — the equivalent DAO predicate stays covered by
   `PersonalDao`-level tests, and `uploadPersonalDocument`/`updatePersonalAfterSync`
   tests in the same file continue to pin the upload workflow.
5. remove any import that the deletion orphans; compile to confirm the interface is
   satisfied by every other member.

acceptance: `./gradlew testDefaultDebugUnitTest` green. User-visible behaviour:
creating, uploading and deleting a personal resource on the Personals screen is
unchanged.

size budget: ~15 changed lines, 3 files.

out of scope: do not add a replacement use case or a `Result`-returning wrapper, do
not move `uploadPersonalDocument` into a service, no DAO changes.

---

### 6. replace the two printStackTrace calls in DiagnosticsRepositoryImpl with tagged logging (roadmap 8)

context: `DiagnosticsRepositoryImpl.kt:74` and `DiagnosticsRepositoryImpl.kt:93` are the
catch blocks of `saveLogToRoom` and `saveLogsToRoom`, and both end a swallowed
`Exception` with `e.printStackTrace()`. Task says data functions should not leak
platform logging into a crash-reporting repository: a bare stack trace on stderr is
lost on device, is invisible to logcat filters, and carries no tag identifying which
of the two room-write paths failed. The repository package already logs with
`android.util.Log` (`ConfigurationsRepositoryImpl.kt:4` uses `Log.e` with the tag
asserted in `ConfigurationsRepositoryImplTest.kt:148`), so this is a consistency fix
with a test-visible tag.

files:
`app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`
(lines 74, 93);
`app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt`
(lines 94-102 `saveLogToRoom returns false on exception`, lines 175-185
`saveLogsToRoom returns false on exception`).
Do NOT touch `saveLogToRoom`'s or `saveLogsToRoom`'s return values or the
`apkLogDao` / `userRepository` call sequence, and do NOT touch
`UserRepositoryImpl.kt` (open PRs own it) even though it has the same smell.

steps:
1. add a private `TAG` constant (e.g. `DiagnosticsRepository`) and
   `import android.util.Log` to `DiagnosticsRepositoryImpl.kt`.
2. replace `e.printStackTrace()` at line 74 with
   `Log.e(TAG, "Failed to save crash log to Room", e)`, keeping `false` as the return.
3. replace `e.printStackTrace()` at line 93 with
   `Log.e(TAG, "Failed to save pending logs to Room", e)`, keeping the `true` early
   return for an empty list and `false` on failure.
4. in `DiagnosticsRepositoryImplTest`, add the `mockkStatic(Log::class)` /
   `unmockkStatic(Log::class)` setup already used in
   `ConfigurationsRepositoryImplTest.kt:93,86` and assert both tags, so the new
   logging is pinned rather than assumed.
5. confirm no `printStackTrace` remains in the file:
   `grep -n "printStackTrace" app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`
   must print nothing.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including the two
`DiagnosticsRepositoryImplTest` failure-path cases, which must still assert
`assertFalse(result)` for `saveLogToRoom` and the unchanged boolean for
`saveLogsToRoom`. User-visible behaviour: unchanged — crash logs still persist and
still report success/failure exactly as today; only the failure trace becomes a
tagged logcat line.

size budget: ~12 changed lines, 2 files.

out of scope: do not introduce a logging abstraction or a new dependency, do not
change the other repositories that still call `printStackTrace`.

---

### 7. move storage-directory knowledge out of StorageBreakdownViewModel (roadmap 9+10)

context: `StorageBreakdownViewModel.kt:28` takes `@ApplicationContext private val context: Context`
and at `StorageBreakdownViewModel.kt:62` reaches into `FileUtils.getOlePath(context)`
inside `scanStorage(context)` (line 82-84). The `ole/` path is already owned by
`StoragePathResolver` (`StoragePathResolver.kt:13`,
`fun resolveOleDirectory(): File = File(FileUtils.getOlePath(context))`), which exists
precisely so callers stop reconstructing the path from a raw `Context`. Keeping the
raw-`Context` variant means the scan logic carries an Android dependency the Compose
migration (roadmap 10) and the platform-free core (roadmap 9) will have to unpick later.
The extension-to-category bucketing at `StorageBreakdownViewModel.kt:96` is pure
logic that belongs with `StorageCategories`.

files:
`app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownViewModel.kt`
(lines 3-9, 26-30, 61-62, 82-103);
`app/src/main/java/org/ole/planet/myplanet/utils/StoragePathResolver.kt`;
`app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategories.kt`;
`app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragmentTest.kt`
(lines 45-56, 122-165, 180-190).
Do NOT touch `StorageBreakdownFragment.kt`, `StorageCategoryViewModel.kt`,
`StorageCategoryDetailFragment.kt` (they only read `viewModel.uiState` and call
`loadStorage`), do NOT touch `FileUtils.kt`, and do NOT touch
`StoragePathResolverTest.kt` (the method added in step 1 is new, so the existing four
`resolveTeamAttachment` cases are unaffected).

steps:
1. add to `StoragePathResolver` (a 3-method class, `StoragePathResolver.kt:9-17`, already
   holding `@ApplicationContext private val context`) one method:
   `fun availableOverTotalMemoryFormattedString(): String = FileUtils.availableOverTotalMemoryFormattedString(context)`.
   Keep the three existing methods unchanged.
2. inject `StoragePathResolver` into `StorageBreakdownViewModel` and delete the
   `@ApplicationContext private val context: Context` parameter (`:28`) and the
   `android.content.Context` / `dagger.hilt.android.qualifiers.ApplicationContext`
   imports (`:3`, `:8`); keep `dispatcherProvider`.
3. in `loadStorage` (`:61-62`) replace
   `FileUtils.availableOverTotalMemoryFormattedString(context)` with
   `storagePathResolver.availableOverTotalMemoryFormattedString()` and replace
   `scanStorage(context)` with `scanStorage(storagePathResolver.resolveOleDirectory())`;
   then delete the private `scanStorage(context: Context)` overload at lines 82-84 so
   only the `internal open fun scanStorage(oleDir: File)` overload (`:86`) remains.
   Remove the now-unused `FileUtils` import (`:17`) if nothing else in the file uses it.
4. move the extension→index decision
   (`val index = if (ext.isEmpty()) StorageCategories.OTHER_INDEX else StorageCategories.indexOf(ext)`,
   `:96`) into `StorageCategories` as
   `fun indexFor(extension: String): Int = if (extension.isEmpty()) OTHER_INDEX else indexOf(extension)`
   and call `StorageCategories.indexFor(ext)` from `scanStorage`. `StorageCategories`
   imports only `androidx.annotation.StringRes` and `R` (`StorageCategories.kt:3-5`), so
   this stays portable.
5. update `StorageBreakdownFragmentTest` to construct the view model with a mocked
   `StoragePathResolver` instead of the mock `Context` (`createViewModel()` at
   `:52-54` and the `spyk(...)` at `:127`), stub
   `storagePathResolver.resolveOleDirectory()` in place of `FileUtils.getOlePath(any())`
   (`:124`, `:163`, `:186`) and
   `storagePathResolver.availableOverTotalMemoryFormattedString()` in place of
   `FileUtils.availableOverTotalMemoryFormattedString(any())` (`:125`, `:164`, `:187`),
   leaving the `scan runs once across multiple loadStorage calls unless forced`
   assertions (`:122-160`) intact.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular
`StorageBreakdownFragmentTest`'s `scanStorage produces identical counts and total sizes
for fixture tree` and `scan runs once across multiple loadStorage calls unless forced`.
User-visible behaviour: Settings → storage breakdown shows the same per-category sizes
and file counts, and the free-space summary line is unchanged.

size budget: ~35 changed lines, 4 files.

out of scope: do not move the scan into `ResourcesRepository`, do not change
`StorageBreakdownUiState`, do not convert the screen to Compose.

---

### 8. route settings' user lookup through SettingsViewModel instead of UserSessionManager (roadmap 3+4)

context: `SettingsActivity.kt:90` injects `UserSessionManager` as `profileDbHandler` purely
to call `getUserModel()` at three sites (`SettingsActivity.kt:194` in
`onCreatePreferences`, `:258` in `initStorageBreakdown`, `:301` in
`clearDataButtonInit`). `UserSessionManager.getUserModel()`
(`UserSessionManager.kt:28-30`) is a one-line delegate to
`userRepository.getUserModel()`, so the fragment is depending on a login/session service
for a repository read. This is the classic DI-cleanup leak the round targets (roadmap 4)
and it puts a data read in the view instead of the ViewModel (roadmap 3), which blocks
the Compose migration for the settings surface.

files:
`app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt`
(lines 33, 90, 194, 258, 301);
`app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt`;
`app/src/test/java/org/ole/planet/myplanet/ui/settings/SettingsViewModelTest.kt`
(line 48, the single `SettingsViewModel(...)` construction).
Do NOT touch `UserSessionManager.kt`, `di/ServiceModule.kt` or `di/RepositoryModule.kt`
(open PRs own those two modules), and do NOT change `SettingsViewModel`'s five existing
public methods or its existing four constructor parameters.

steps:
1. inject `UserRepository` into `SettingsViewModel` by appending it as the fifth
   constructor parameter (after `dispatcherProvider` at `SettingsViewModel.kt:23`) and
   expose `suspend fun currentUser(): UserEntity? = userRepository.getUserModel()`.
   `UserRepository` already declares `getUserModel()` (`UserRepository.kt:88`) and
   `SettingsViewModel` is a `@HiltViewModel`, so Hilt resolves it without a module edit.
2. in `SettingsActivity.kt:194`, replace `user = profileDbHandler.getUserModel()` with
   `user = viewModel.currentUser()`; this call site is already inside
   `lifecycleScope.launch`.
3. in `initStorageBreakdown` (`:258`) replace
   `val userModel = profileDbHandler.getUserModel()` with
   `val userModel = viewModel.currentUser()` — it is already inside
   `viewLifecycleOwner.lifecycleScope.launch`.
4. in `clearDataButtonInit` (`:301`) do the same replacement.
5. delete the `@Inject lateinit var profileDbHandler: UserSessionManager` field
   (`:90`) and its import (`:33`); leave `sharedPrefManager` (`:92`) and its three
   remaining uses (`:217`, `:220`, `:342`) untouched.
6. update the single `SettingsViewModel(...)` construction in `SettingsViewModelTest.kt:48`
   to pass a `mockk<UserRepository>(relaxed = true)` as the fifth argument, so the
   existing `clearAllData` test keeps compiling and passing unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including
`SettingsViewModelTest`'s `clearAllData calls clearAllData and clearPreferences on
configurationsRepository and emits clearDataEvent`. User-visible behaviour: opening
Settings still resolves the stored user before `blockGuestSwitches()`; tapping storage
breakdown as a guest still shows the guest dialog instead of the breakdown screen;
tapping reset-app as a guest still shows the guest dialog instead of the confirmation
alert.

size budget: ~15 changed lines, 3 files.

out of scope: do not remove `UserSessionManager` from `ServiceModule`, do not change
`SettingsViewModel`'s existing four parameters or its five public methods, do not move
the `sharedPrefManager` reads into the ViewModel.

---

### 9. collapse the duplicated health-upload sequence in UploadToShelfService (roadmap 5+8)

context: `UploadToShelfService.kt:70-76` (`uploadHealth`) and
`UploadToShelfService.kt:78-96` (`uploadSingleUserHealth`) run the same three-step
sequence — `getUpdatedHealth…` → `uploadHealthData(…)` → `markHealthExaminationsUploaded(…)` —
only differing in scope (all users vs. one user) and in the `OnSuccessListener`
callback. Two copies of the upload+mark ordering means a future change to the
mark-after-upload convention has two places to miss, which is exactly the workflow
duplication the sync/upload consolidation step is meant to remove. Both are already
covered test-first in `UploadToShelfServiceTest.kt:159-210`, so the refactor is safe to
pin.

files:
`app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`
(lines 70-96);
`app/src/test/java/org/ole/planet/myplanet/services/UploadToShelfServiceTest.kt`
(lines 158-215).
Do NOT touch `HealthRepository.kt` or `HealthRepositoryImpl.kt` (open PRs own them),
do NOT touch `UserDataWorker.kt` or `AutoSyncWorker.kt` (their call sites keep the same
two public method names), and do NOT change `appScope` usage or `dispatcherProvider.io`.

steps:
1. add a private `suspend fun uploadHealthRecords(records: List<HealthExamination>)`
   that performs only the two repository writes:
   `healthRepository.markHealthExaminationsUploaded(healthRepository.uploadHealthData(records))`,
   keeping the current call order exactly.
2. rewrite `uploadHealth()` (lines 70-76) to launch in `appScope` on
   `dispatcherProvider.io`, fetch with `getUpdatedHealthExaminations()` and hand the
   result to `uploadHealthRecords`.
3. rewrite `uploadSingleUserHealth(userId, listener)` (lines 78-96) to keep its
   existing `userId.isNullOrEmpty()` early return and its `try/catch` +
   `withContext(dispatcherProvider.main) { listener?.onSuccess(...) }` messages
   verbatim, but delegate the body to `uploadHealthRecords(getUpdatedHealthForUser(userId))`.
4. keep both public method names and signatures unchanged so `UserDataWorker.kt:54` and
   `AutoSyncWorker.kt:113` compile untouched.
5. no test change should be needed — `UploadToShelfServiceTest` verifies each
   repository call exactly once (`coVerify(exactly = 1)`), which the helper preserves.
   Only touch the test file if a `coVerify` ordering assertion breaks.

acceptance: `./gradlew testDefaultDebugUnitTest` green, specifically
`uploadHealth uploads and marks health records`,
`uploadSingleUserHealth returns if userId is null or empty`,
`uploadSingleUserHealth uploads data for specific user` and
`uploadSingleUserHealth handles errors gracefully` in `UploadToShelfServiceTest`.
User-visible behaviour: the sync worker still uploads health examinations and still
runs the health step from the upload-completion callback; no ordering change is
observable.

size budget: ~25 changed lines, 2 files.

out of scope: do not merge `uploadHealth` and `uploadSingleUserHealth` into one public
method, do not change the `OnSuccessListener` callback contract, do not alter the
`userSyncRepository.checkAndUploadUser` flow.

---

### 10. resolve the team-courses current user in the ViewModel, not the fragment (roadmap 3+10)

context: `TeamCoursesFragment.kt:61` computes the acting user itself with
`sharedPrefManager.getUserId().ifEmpty { "--" }` and passes the sentinel string into
`viewModel.loadCourses(teamId, currentUserId)`
(`TeamCoursesViewModel.kt:31`), which then compares it against
`teamsRepository.getTeamCreator(teamId)` at `TeamCoursesViewModel.kt:40` to decide
`canRemove`. That is two problems in one line: a UI fragment holding a
`SharedPrefManager` data read (roadmap 3), and a hand-rolled `"--"` placeholder — the
same literal `LifeRepositoryImpl.kt:20` already defines as "no user" — travelling as a
magic string through a ViewModel API (roadmap 10: hoisted, portable state). Once the
fragment calls `updateCoursesList()` from three places (`:41`, `:90`, `:168`), the
sentinel is computed three times too.

files:
`app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt`
(lines 41, 60-63, 90, 168);
`app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt`
(lines 22-45);
`app/src/test/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModelTest.kt`
(lines 36-42, 48-98, 150-165).
Do NOT touch `TeamCoursesUiState`, `removeCourse`, `addCourses`,
`getAvailableCourses`, `teamsRepository`/`coursesRepository` usage, `BaseTeamFragment.kt`,
or `CoursesRepository`/`TeamsRepository`.

steps:
1. inject `UserRepository` into `TeamCoursesViewModel` (it already injects
   `TeamsRepository` and `CoursesRepository` at `TeamCoursesViewModel.kt:24-25`) and
   resolve the actor inside `loadCourses` via
   `userRepository.getUserModel()?.id` instead of accepting a caller-supplied string.
2. change `loadCourses(teamId: String, currentUserId: String)` to
   `loadCourses(teamId: String)`; keep `canRemove = currentUserId.equals(teamCreator, ignoreCase = true)`
   semantics at line 40, with a null actor now falling through to `false` the same way
   `"--"` did. Do not add the `"--"` literal — null is the no-user state.
3. in `TeamCoursesFragment.updateCoursesList()` (lines 60-63), delete the
   `sharedPrefManager.getUserId().ifEmpty { "--" }` line and call
   `viewModel.loadCourses(teamId)`; drop the now-unused `SharedPrefManager` injection in
   that fragment if the class declares one (it currently only inherits it).
4. in `TeamCoursesViewModelTest`, drop the second argument from every `loadCourses`
   call (lines 58, 72, 84, 96, 163), stub `userRepository.getUserModel()` per case, and
   keep the existing assertions for creator / non-creator / case-insensitive match /
   empty course ids / preserved dependency edge.
5. keep the `loadCourses with empty course ids yields empty courses` case asserting an
   empty course list for a null user — its `"--"` input at line 96 becomes a null
   `getUserModel()` stub.

acceptance: `./gradlew testDefaultDebugUnitTest` green, in particular the five
`TeamCoursesViewModelTest.loadCourses` cases and
`loadCourses preserves dependency edge when getTeamCourseIds is delayed`.
User-visible behaviour: the team Courses page still shows the remove-course controls
only when the signed-in user is the team creator, and still shows them as read-only for
everyone else.

size budget: ~25 changed lines, 3 files.

out of scope: do not move the creator comparison into `TeamsRepository`, do not add a
`TeamCoursesUiState` field for the acting user, do not touch the add/remove course
paths.
