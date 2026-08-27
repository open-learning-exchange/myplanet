# myPlanet refactor round — data boundary reinforcement

date · 2026-08-27
base commit · 89fd72c251df68ed01094091d4de7ba7a2571ebe
open PRs checked · yes — banned-file list (`/tmp/prfiles_uniq.txt`, 418 files) used to validate EVERY file below; no cited file is on it.

This round serves roadmap **1** (finish cleaning the data layer), **3** (expand viewmodel/use-case layers), **5** (consolidate sync & upload workflow), **7** (optimize performance hotspots), and, wherever noted, advances **9** (KMP: platform-free repo core) and **10** (compose portability). Each task is independently mergeable in any order; no file appears in more than one task.

---

### 1. drop the unused ExamDao injection from NotificationsRepositoryImpl (roadmap 1, moves 9 forward)

context: `NotificationsRepositoryImpl.kt:32` declares `private val examDao: ExamDao` but `ExamDao` is never referenced anywhere else in the file — a dead cross-feature dependency the notifications data layer carries into the graph purely by legacy. It is a leaked repository-interface field (feature mis-wiring), and because `ExamDao` is an Android-Room type it is also a KMP-import blocker for the notifications repository. The repository already fulfils all its notification duties with `teamTaskDao` (used at 157, 195, 253, 262), `teamsRepository`, `voicesRepository`, `notificationDao` and `teamNotificationDao`.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — remove the `examDao: ExamDao` ctor param at line 32, the `import org.ole.planet.myplanet.data.room.dao.ExamDao` at line 10, and the constructor argument wiring.
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` — remove `private lateinit var examDao: ExamDao` (line 40), `examDao = mockk(relaxed = true)` (line 50), and the `examDao,` positional arg at line 59.
- do NOT touch `NotificationsRepository.kt` (interface), `ExamDao.kt`, or the notification enrichment methods — those are out of scope.

steps:
1. Delete the `examDao` field from the `NotificationsRepositoryImpl` constructor and its import.
2. Delete the three `examDao` references in `NotificationsRepositoryImplTest.kt`.
3. Run the repository unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green, including `NotificationsRepositoryImplTest`; `grep -rn "examDao" app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` returns nothing; notification unread-count and grouping in the app are unchanged.

size budget: ~5 changed lines across 2 files.
out of scope: no behavioral change to notification flows; do not touch the still-used `teamTaskDao`.

---

### 2. move the life-list seed-or-load data function out of LifeViewModel into LifeRepository (roadmap 3, moves 10 forward)

context: `LifeViewModel.kt:32-40` `loadMyLifeList()` hand-wires a data assembly: resolve userId from `sharedPrefManager.getUserId()` fallback to `userRepository.getUserModel()?.id`, then `getMyLifeByUserId` → `seedMyLifeIfEmpty` → re-`getMyLifeByUserId`. The ViewModel therefore reaches into three data providers and owns orchestration that the repository already half-provides (`seedMyLifeIfEmpty` at `LifeRepository.kt:10`). ViewModels should hold no persistence/seed mechanics; hoisting them keeps the UI layer portable for the compose-multiplatform north star.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt` — change `loadMyLifeList()` to call the new repository method only; drop the `userRepository` and `sharedPrefManager` ctor params/flags and their imports.
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt` — add `suspend fun getMyLifeListForUser(userId: String?, defaultItems: List<MyLife>): List<MyLife>`.
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` — implement it: effective-user resolution is given by the caller, so it seeds-if-empty using `MyLife.defaultItems` passed as `defaultItems` and returns the seeded list (reuse the existing `getMyLifeByUserId(userId, ensureLatest = true)` + `seedMyLifeIfEmpty`). Keep `common`-safe imports; no new Android imports.
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryTest.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt` — add tests for the new method (seeds when empty, returns existing otherwise).
- leave `LifeFragment.kt` unchanged (it only consumes `viewModel.myLifeList`).

steps:
1. Add `getMyLifeListForUser(userId, defaultItems)` to the `LifeRepository` interface.
2. Implement it in `LifeRepositoryImpl` reusing `seedMyLifeIfEmpty` + `getMyLifeByUserId`.
3. Rewrite `LifeViewModel.loadMyLifeList()` to build `MyLife.defaultItems(userId, context::getString)` and delegate to the repository; strip the now-unused `userRepository`/`sharedPrefManager` wiring.
4. Add unit tests; run them.

acceptance: `./gradlew testDefaultDebugUnitTest` green; My Life screen still auto-creates the seven default items on first open and preserves visibility/order on later loads.

size budget: ~35 lines across 5 files.
out of scope: do not relocate `MyLife.defaultItems`' label resolution (still Android `R.string`) — that is UI-layer string mapping, not data logic.

---

### 3. load a title projection in ResourcesRepositoryImpl.getResourceTitlesMap (roadmap 7, serves roadmap 1)

context: `ResourcesRepositoryImpl.kt:703-705` `getResourceTitlesMap()` calls `myLibraryDao.getWithResourceId()` which is `SELECT * FROM my_library` (`MyLibraryDao.kt:77`), loading every full `MyLibrary` row and then `.associate {(resourceId) to (title)}` (line 704). For a resource-heavy device this materializes hundreds of full entities to yield two columns, and it runs on every offline-resource render (`getOfflineResourceItems` calls it at line 752). It is also a pointless cross-feature data path: the repository needs only `resourceId,title`.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt` — add `@Query("SELECT resourceId, title FROM my_library")` returning `List<ResourceTitle>` plus a small `data class ResourceTitle(val resourceId: String?, val title: String?)` (Room constructor-mapped projection).
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` — change `getResourceTitlesMap()` to use the projection instead of `getWithResourceId()`.
- `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt` — cover the projection mapping (nulls, blanks).
- do NOT touch `ResourcesRepository.kt`, the bulk rating/tag methods, or `getOfflineResourceItems` beyond the one-line consumption.

steps:
1. Add the `ResourceTitle` projection class and query to `MyLibraryDao`.
2. Rewrite `getResourceTitlesMap()` to map the projection rows to `Map<String,String>`.
3. Update/extend the DAO-level and repo-level tests; run them.

acceptance: `./gradlew testDefaultDebugUnitTest` green; storage/offline screens still show correct resource titles with no functional change.

size budget: ~20 lines across 3 files.
out of scope: no schema change, no behavior change to resource downloads.

---

### 4. route DownloadRepositoryImpl's file-not-found logging through DiagnosticsRepository instead of the MainApplication static (roadmap 1+9)

context: `DownloadRepositoryImpl.kt:9` `import org.ole.planet.myplanet.MainApplication.Companion.createLog` and lines 51/53 call the application-singleton `createLog("File Not Found", ...)`. A data-layer repository reaching into the app singleton's static is a KMP-import blocker and a boundary leak — the repository's side effect (persist an apk-log row) should go through the logging repository it already has in the graph.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepositoryImpl.kt` — replace the `MainApplication.Companion.createLog` import/calls with an injected `DiagnosticsRepository` + `TimeProvider`; call `diagnosticsRepository.saveLogToRoom("File Not Found", message, timeProvider.now())`.
- `app/src/test/java/org/ole/planet/myplanet/repository/DownloadRepositoryImplTest.kt` — add the two mocks to the `DownloadRepositoryImpl(...)` ctor (8 sites) and swap the `MainApplication.createLog(...)` verifications (lines 257, 282, plus the `every { MainApplication.createLog(...) } just runs` at line 35) for `coVerify { diagnosticsRepository.saveLogToRoom(...) }`; remove the now-unused `import org.ole.planet.myplanet.MainApplication...` in the test.
- do NOT touch `DiagnosticsRepositoryImpl.kt` (task 9 owns it) or `MainApplication.kt`.

steps:
1. Add `diagnosticsRepository: DiagnosticsRepository` and `timeProvider: TimeProvider` ctor args to `DownloadRepositoryImpl`.
2. Replace the two `createLog("File Not Found", ...)` calls with `saveLogToRoom(...)`.
3. Update `DownloadRepositoryImplTest.kt` construction and assertions; run tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the "File Not Found" 404 apk-log row is still written for failed downloads.

size budget: ~12 lines across 2 files.
out of scope: do not change download retry/error semantics or the error strings returned to the UI.

---

### 5. remove android.util.Log from RetryRepositoryImpl (roadmap 1+9)

context: `RetryRepositoryImpl.kt:3` imports `android.util.Log`, `:19` declares `TAG`, and lines 122/128/133 emit `Log.w`/`Log.i` inside `safeClearQueue()`. These three messages only mirror the Boolean the method already returns (and that `SettingsViewModel.clearRetryQueue()` surfaces), so they are redundant observability and they block KMP for the retry repository (the only android.* import in the file besides the model).

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt` — delete the `android.util.Log` import, the `TAG` const, and the three `Log.*` calls (keep the `return false` / `return@withLock true` control flow intact).
- `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt` — no assertion on Log exists, so keep the existing `safeClearQueue` coverage; just confirm it still passes.
- do NOT touch `RetryQueue.kt` or `RetryQueueWorker.kt` (they already route through `RetryRepository`).

steps:
1. Remove `Log` import + `TAG`.
2. Delete the two `Log.w(...)` lines and the one `Log.i(...)` line without altering the surrounding `if`/`withLock` returns.
3. Run the retry repository tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `grep -rn "import android.util.Log" app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt` is empty; retry queue clear-on/busy behavior unchanged.

size budget: ~6 lines, 1 file.
out of scope: no new logger abstraction, no change to `safeClearQueue`'s Boolean contract.

---

### 6. collapse the double delete in PersonalsRepositoryImpl.deletePersonalResource into one row-matching DAO delete (roadmap 1+7)

context: `PersonalsRepositoryImpl.kt:62-64` `deletePersonalResource(id)` issues `personalDao.deleteByDocId(id)` and then `personalDao.deleteById(id)` — two `DELETE`s keyed on different columns of the same row (`_id` vs `id`, defined at `PersonalDao.kt:40,43`). Both exist because `updatePersonalResource` (line 66-68) already shows "either key may hold the doc" (`findByDocId(id) ?: findById(id)`). Two round-trips to delete one row is wasteful; a single `WHERE _id = :id OR id = :id` is behavior-equivalent and one statement.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt` — add `@Query("DELETE FROM my_personal WHERE _id = :id OR id = :id") suspend fun deleteByIdOrDocId(id: String): Int`.
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` — replace lines 63-64 with a single `personalDao.deleteByIdOrDocId(id)`.
- `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` — update `deletePersonalResource deletes both _id and id` (lines 138-142) to verify the single `deleteByIdOrDocId("test-id")` call.
- leave `deleteByDocId`/`deleteById` queries and `PersonalsViewModel.kt` alone.

steps:
1. Add `deleteByIdOrDocId` to `PersonalDao`.
2. Use it in `deletePersonalResource`.
3. Update the test and run the personals suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; deleting a personal/resource entry still removes it whether the key lives in `_id` or `id`.

size budget: ~6 lines across 3 files.
out of scope: no change to update/insert paths or upload serialization.

---

### 7. replace MainApplication.context with an injected Context in EnterprisesRepositoryImpl (roadmap 1+9)

context: `EnterprisesRepositoryImpl.kt:11` imports `org.ole.planet.myplanet.MainApplication` and line 128 uses `MainApplication.context` inside `attachTeamImage(...)` when calling `MyTeam.getAttachmentFile(context, teamId, imageName)`. The enterprises repository is otherwise context-free; reaching into the app singleton for a `Context` is a data-layer → singleton leak and a KMP-import blocker.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` — add `@param:ApplicationContext private val context: Context` to the constructor; replace `MainApplication.context` at line 128 with the field; drop the `MainApplication` import (line 11).
- `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt` — add a `Context` mock to the `EnterprisesRepositoryImpl(teamDao, timeProvider, dispatcherProvider, context)` construction (line 21).
- do NOT touch `MyTeam.kt` (signature of `getAttachmentFile` is unchanged) or `MainApplication.kt`.

steps:
1. Add the injected `@ApplicationContext context` ctor param.
2. Swap `MainApplication.context` → `context` and remove the import.
3. Update the test ctor; run the enterprises tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; report image attachments still write to the same team attachment file location.

size budget: ~5 lines across 2 files.
out of scope: no change to CSV export or report computation.

---

### 8. de-duplicate the two UserDataWorker upload enqueues in SyncRepositoryImpl (roadmap 5, serves roadmap 1)

context: `SyncRepositoryImpl.kt:47-75` `uploadLoginData()` and `uploadBulkData()` are near-identical: build a `OneTimeWorkRequest(UserDataWorker)` with a `KEY_UPLOAD_TYPE`, `enqueueUniqueWork("<name>", REPLACE, request)`, and `map(::mapWorkInfoToState)` over `workManager.getWorkInfoByIdFlow(...)`. They differ only in the upload-type input and the unique-work name. This duplicated sync/upload plumbing is ripe for consolidation while deliberately NOT changing the public `SyncRepository` contract.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt` — extract a private `enqueueUserDataUpload(uploadType: String, workName: String): Flow<SyncUiState>` and have both public methods call it. No new imports.
- do NOT touch `SyncRepository.kt` (interface), `UserDataWorker.kt`, or `ProcessUserDataActivity.kt`.

steps:
1. Add the private helper building the request, enqueueing, and mapping to `SyncUiState`.
2. Rewrite the two public methods as thin wrappers passing `UPLOAD_TYPE_LOGIN`+"UploadUserData_Login" and `UPLOAD_TYPE_BULK`+"UploadUserData_Bulk".
3. Run the sync repository tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the Login and Bulk upload screens still enqueue the same unique work and surface Idle/Loading/Success/Error states.

size budget: ~18 lines, 1 file.
out of scope: no change to `mapWorkInfoToState`, work policies, or upload payload content.

---

### 9. consolidate the duplicated ApkLog field wiring in DiagnosticsRepositoryImpl (roadmap 1)

context: `DiagnosticsRepositoryImpl.kt` builds the same `ApkLog` fields (`parentCode`, `createdOn=planetCode`, `version=getVersionName(context)`, `user id`) twice — once in `buildApkLog` (lines 29-46) and again inline in `saveLogsToRoom` (lines 71-79) with a separate `versionName` lookup at line 67. The two code paths have drifted (inline variant interpolates fields differently), so consolidating removes duplicated version/tenant resolution and the risk of future divergence.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt` — make `saveLogsToRoom` build each `ApkLog` via the existing `buildApkLog(spm, model?.id, pending.time, pending.type, pending.error)` (drop the inline field-wiring and the now-unused `versionName` at line 67), then `insertAll`. Keep `page=""` behavior identical.
- no test file exists for this class; add none (out of budget), but keep the method results identical.
- do NOT touch `DiagnosticsRepository.kt`, `VersionUtils.kt`, or `MainApplication.kt`.

steps:
1. Refactor `saveLogsToRoom` to map `pendingLogs` through `buildApkLog`.
2. Remove the standalone `VersionUtils.getVersionName(context)` call now folded into `buildApkLog`.
3. Run the repository unit-test module to confirm compilation/tests green.

acceptance: `./gradlew testDefaultDebugUnitTest` green; apk logs persisted on crash batch-save carry the same tenant code + version as the single-save path.

size budget: ~14 lines, 1 file.
out of scope: no change to `saveLogToRoom`, `getPendingApkLogs`, or `markApkLogUploaded`.

---

### 10. hoist the community-context assembly from CommunityTabViewModel into ConfigurationsRepository (roadmap 3, moves 9/10 forward)

context: `CommunityTabViewModel.kt` `init {}` (lines 32-42) reaches directly into three providers — `sharedPrefManager.getParentCode()/getCommunityName()` (34-35), `configurationsRepository.getPlanetType()` (36), and `userSessionManager.getUserModel()?.planetCode` (37-38) — to assemble a `CommunityTabState`. That read-gathering is a data/relationship function living in the ViewModel instead of the repository, so the UI layer stays coupled to session services. Hoisting it into `ConfigurationsRepository` (which already owns `sharedPrefManager`) centralises the community-context relationship.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt` — add `suspend fun getCommunityContext(): CommunityContext` and a small `data class CommunityContext(val planetCode: String, val parentCode: String, val communityName: String, val planetType: String?)` in the repository package (repo must not depend on UI types).
- `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` — add the `userSessionManager` ctor dep and implement `getCommunityContext()` with the same reads as the current VM body.
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt` — collapse its `init` to `configurationsRepository.getCommunityContext().let { _state.value = CommunityTabState(it.planetCode, it.parentCode, it.communityName, it.planetType) }`; drop `sharedPrefManager` + `userSessionManager` ctor params and imports.
- `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt` — add the `userSessionManager` mock to the ctor and cover `getCommunityContext`.
- do NOT touch `CommunityTabFragment.kt` (still reads `viewModel.state`), `UserSessionManager.kt`, or `SharedPrefManager.kt`.

steps:
1. Add `CommunityContext` + `getCommunityContext()` to the `ConfigurationsRepository` interface; inject/use `userSessionManager` in the impl.
2. Rewrite `CommunityTabViewModel.init` to consume the repo result and drop the session-service wiring.
3. Extend the configurations test ctor + add a `getCommunityContext` test; run tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; community tab still renders the same planetCode@parentCode pager id and fallback community-name title.

size budget: ~30 lines across 4 files.
out of scope: do not change `CommunityTabState`'s shape or the tab layout; no tabCount/planet-type logic changes.

---