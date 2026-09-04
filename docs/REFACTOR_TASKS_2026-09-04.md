# myPlanet refactor round — 10 independent work orders

**Date:** 2026-09-04
**Base commit:** `9ff1273dc95f8cbd3590fca12ca821454b2e27bc` (`master`, "courses: smoother progress batch deleting (fixes #16694) (#16676)")
**Open PRs checked (all 48 open, files enumerated per PR head):** 16705, 16702, 16701, 16698, 16693, 16690, 16688, 16686, 16680, 16677, 16661, 16647, 16624, 16623, 16619, 16594, 16270, 16101, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

Every `.kt` file touched by any of those 48 heads is off-limits. The resulting exclusion list covers, among others: `TeamsRepository(Impl)`, `CoursesRepository(Impl)`, `ResourcesRepository(Impl)`, `RatingsRepository(Impl)`, `HealthRepository(Impl)`, `SubmissionsRepository(Impl)`, `VoicesRepository(Impl)`, `FeedbackRepository(Impl)`, `CommunityRepository(Impl)`, `di/RepositoryModule.kt`, `di/RoomModule.kt`, `data/room/AppDatabase.kt`, and the DAOs `NotificationDao`, `CourseProgressDao`, `RatingDao`, `TagDao`, `TeamTaskDao`, `SubmissionDao`, `NewsDao`, `ExamDao`, `MeetupDao`, `FeedbackDao`, `HealthExaminationDao`, `CourseDao`, `ChatDao`, `AchievementDao`, `CertificationDao`, `OfflineActivityDao`, `RemovedLogDao`, `SyncCursorDao`. None of the tasks below touch any of them.

**Roadmap legend:** 1 data-layer cleanup · 2 global navigation · 3 viewmodel/use-case layers · 4 DI cleanup · 5 sync/upload consolidation · 6 compose migration · 7 performance · 8 code health + tests · 9 KMP-ready platform-free core (north star) · 10 portable compose (north star)

---

### 1. push the archived-report filter of the finances CSV export into SQL (roadmap 1+7)

context: `EnterprisesRepositoryImpl.exportReportsAsCsv` at `repository/EnterprisesRepositoryImpl.kt:103-105` reads **every** `teams` row for the team with `docType = 'report'` via `teamDao.getByTeamIdAndDocType(teamId, "report")`, then drops archived rows and sorts by `createdDate` in Kotlin. The identical predicate already exists in SQL one screen above: `TeamDao.observeNonArchivedReportsByTeamId` (`data/room/dao/TeamDao.kt:23`) is `WHERE teamId = :teamId AND docType = 'report' AND IFNULL(status, '') != 'archived' ORDER BY createdDate DESC`. So the export deserialises archived reports (each carries JSON-converted columns) only to throw them away, and re-sorts a list SQLite could have ordered by index.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt` (add one `@Query`, next to line 23), `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` (`exportReportsAsCsv`, lines 102-105 only), `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt` (the test at line 71, `exportReportsAsCsv calculates correct profitLoss and endingBalance`, stubs `getByTeamIdAndDocType` at line 89). Do NOT touch `EnterprisesRepository.kt`, `getReportsFlow`, `observeNonArchivedReportsByTeamId`, or any other `TeamDao` query — `TeamsRepositoryImpl` is owned by open PRs and must not be edited.

steps:
1. In `TeamDao`, add `@Query("SELECT * FROM teams WHERE teamId = :teamId AND docType = 'report' AND IFNULL(status, '') != 'archived' ORDER BY createdDate DESC") suspend fun getNonArchivedReportsByTeamId(teamId: String): List<MyTeam>` — same SQL string as line 23, `suspend` instead of `Flow`.
2. In `exportReportsAsCsv`, replace the three chained lines with `val reports = teamDao.getNonArchivedReportsByTeamId(teamId)`.
3. Delete any import that became unused in `EnterprisesRepositoryImpl.kt`.
4. In `EnterprisesRepositoryImplTest`, restub the export test on `getNonArchivedReportsByTeamId(teamId)` returning only the non-archived report, and keep asserting the same CSV body so the profit/loss and ending-balance maths stay covered.
5. Add one test asserting the export no longer calls `getByTeamIdAndDocType` (`coVerify(exactly = 0) { ... }`), which is what locks the optimisation in.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manual: open an enterprise/team with at least one archived and one active financial report, tap *Export CSV*, save the file — the CSV still holds the header line, the team name, and exactly the non-archived reports newest-first, byte-identical to before.

size budget: ~15 changed lines, 3 files.

out of scope: no schema change and no `AppDatabase` version bump (adding a `@Query` changes no table). Do not convert `getReportsFlow` or refactor the CSV string building.

---

### 2. replace the personals read-modify-write update with one targeted SQL UPDATE (roadmap 1+7)

context: `PersonalsRepositoryImpl.updatePersonalResource` (`repository/PersonalsRepositoryImpl.kt:64-71`) issues up to two SELECTs (`personalDao.findByDocId(id) ?: personalDao.findById(id)`, lines 30-34 of the DAO), mutates the loaded `Personal` in memory, then writes the whole row back with `@Update`. Editing a title therefore rewrites every column of the row — including `path`, `_rev` and `isUploaded` — from a snapshot read microseconds earlier, which is both three statements where one suffices and a lost-update window against the sync writer that touches `isUploaded`/`_rev` (`PersonalDao.updateUploadedStatus`, line 42).

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt` (add one `@Query`), `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt` (`updatePersonalResource` only), `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt` (rewrite the two tests at lines 150 and 164). Leave `findByDocId`, `findById` and `update` in the DAO alone — `savePersonalResource` and the upload path still use them; do NOT touch `PersonalsRepository.kt` or `PersonalsViewModel.kt`.

steps:
1. In `PersonalDao`, add `@Query("UPDATE my_personal SET title = COALESCE(:title, title), description = COALESCE(:description, description) WHERE _id = :id OR id = :id") suspend fun updateTitleAndDescription(id: String, title: String?, description: String?): Int` — the `WHERE` mirrors `deleteByIdOrDocId` (line 39) so both id shapes keep resolving, and `COALESCE` preserves the existing "null field means leave unchanged" contract of `PersonalUpdate`.
2. Reduce `updatePersonalResource` to a single call: `personalDao.updateTitleAndDescription(id, update.title, update.description)`.
3. Drop imports/locals in `PersonalsRepositoryImpl.kt` left unused by the change.
4. Rewrite the two existing tests to verify one `updateTitleAndDescription("test-id", "New Title", "Desc")` call and zero `findByDocId`/`findById`/`update` calls.
5. Add one test proving a `PersonalUpdate(title = "New Title")` with a null description passes `null` through, so the COALESCE contract is asserted rather than assumed.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manual: in *My Personals*, long-press/edit a personal resource, change only the title, save — the list shows the new title, the description and the file path are unchanged, and a previously uploaded item still renders as uploaded.

size budget: ~20 changed lines, 3 files.

out of scope: no change to the delete or upload paths, no new `PersonalUpdate` fields, no `AppDatabase` version bump.

---

### 3. resolve notification group labels from the adapter instead of the ViewModel (roadmap 3+8+10)

context: `NotificationsViewModel` injects `@ApplicationContext Context` purely to turn a notification type into a display label: `typeLabelFor` (`ui/notifications/NotificationsViewModel.kt:311-320`) is eight `context.getString(...)` branches, and the resolved string is then carried as data through `NotificationGroup.label` into `NotificationListItem.Header.label` (`model/NotificationListItem.kt:6`). That puts an Android resource lookup and a locale-dependent string inside the state model — the exact pattern roadmap 10 forbids in a portable ViewModel — while the sibling concern is already done right: `NotificationsAdapter` maps type → icon through `ICON_BY_TYPE`/`iconResFor` at `ui/notifications/NotificationsAdapter.kt:177-178`. A locale change also leaves the label stale until the flow re-emits.

files: `app/src/main/java/org/ole/planet/myplanet/model/NotificationListItem.kt` (drop `label` from `Header`), `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` (`NotificationGroup`, `buildNotificationGroups`, `typeLabelFor`, and the `@ApplicationContext` parameter only if nothing else needs it — lines 379-430 still format messages with it, so most likely keep it), `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt` (add the label map beside `ICON_BY_TYPE`). Do NOT touch `NotificationsRepository`/`Impl`, `NotificationsFragment.kt`, or `model/Notification.kt`.

steps:
1. Add `private val LABEL_RES_BY_TYPE: Map<String, Int>` and `internal fun labelResFor(type: String): Int` to `NotificationsAdapter.kt`, mirroring `ICON_BY_TYPE`/`iconResFor`, with the same eight string resources `typeLabelFor` uses today and `R.string.notification_group_other` as the fallback.
2. In `HeaderViewHolder.bind` (line 94) replace `binding.tvHeaderLabel.text = header.label` with `binding.tvHeaderLabel.setText(labelResFor(header.type))`.
3. Remove the `label` property from `NotificationListItem.Header`, from `NotificationsViewModel.NotificationGroup`, and from the `Header(...)`/`NotificationGroup(...)` construction sites (lines ~301 and ~453 of the ViewModel); delete `typeLabelFor`.
4. Remove the now-unused `R`/`Locale` imports from the ViewModel only if nothing else there uses them.
5. Run the unit tests; `NotificationsViewModelTest` reads headers via `filterIsInstance<NotificationListItem.Header>()` (line 281-283) and asserts on `type`, so it should compile unchanged — fix it only if it does not.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manual: open *Notifications* with several kinds pending — each group header shows the same title and icon as before, unread badges and expand/collapse still work, and switching the app language (e.g. to Spanish) re-renders the headers translated.

size budget: ~35 changed lines, 3 files.

out of scope: do not touch the message-body formatting (`formatTaskNotification`, `formatStorageNotification`, `formatJoinRequestNotification`) — that is a separate, larger extraction. No Compose rewrite here.

---

### 4. stop leaking the Room `DictionaryEntity` into the dictionary UI (roadmap 1+3+9)

context: `DictionaryRepository.findByWord` returns the Room `@Entity` itself (`repository/DictionaryRepository.kt:14`), and `DictionaryViewModel` imports it from the persistence package — `import org.ole.planet.myplanet.data.room.entity.DictionaryEntity` at `ui/dictionary/DictionaryViewModel.kt:11` — and stores it in UI state as `DictionarySearchState.Found(val entry: DictionaryEntity)` (line 24). This is the only place in the whole app where `data.room.entity` reaches outside `data/` and `repository/` (verified by grep), so closing it makes that boundary absolute and removes a Room-annotated class from the state a future KMP/Compose layer would consume.

files: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt` (add the domain type next to the existing `DictionaryLoad` sealed interface, change the return type), `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt` (`findByWord` only), `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt` (constructs a `DictionaryEntity` at line 105). Do NOT touch `data/room/entity/DictionaryEntity.kt`, `DictionaryDao.kt`, `insertDictionaryData`, or `DictionaryActivity.kt`.

steps:
1. In `DictionaryRepository.kt`, declare `data class DictionaryWord(val word: String, val meaning: String, val definition: String, val synonym: String, val antonym: String)` and change the signature to `suspend fun findByWord(word: String): DictionaryWord?`.
2. In `DictionaryRepositoryImpl.findByWord`, map the DAO result: `return dictionaryDao.findByWord(word)?.let { DictionaryWord(it.word, it.meaning, it.definition, it.synonym, it.antonym) }`.
3. In `DictionaryViewModel`, change `DictionarySearchState.Found(val entry: DictionaryWord)` and delete the `data.room.entity` import.
4. Update `DictionaryViewModelTest` to build a `DictionaryWord` instead of a `DictionaryEntity`.
5. Confirm `DictionaryActivity.renderSearchState` (lines 100-113) compiles untouched — it reads only `word`, `definition`, `synonym`, `antonym`, which the new type keeps under the same names.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `grep -rn "data.room.entity" app/src/main/java --include=*.kt | grep -v "/data/room/" | grep -v "/repository/"` returns nothing. Manual: open the *Dictionary* screen, search a word that exists — definition, synonym and antonym render exactly as before; search a nonsense word — the "not available" toast still appears.

size budget: ~25 changed lines, 4 files.

out of scope: do not add `id`, `code`, `language` or `advanceCode` to the domain type (nothing reads them), and do not touch the JSON seeding path.

---

### 5. return parsed community leaders from the configurations repository (roadmap 1+3+9)

context: `ConfigurationsRepository.getCommunityLeaders(): String` (`repository/ConfigurationsRepository.kt:17`) hands a raw CouchDB JSON blob straight out of `SharedPreferences` (`ConfigurationsRepositoryImpl.kt:404-406`), and `LeadersViewModel` does the deserialising itself — `UserEntity.parseLeadersJson(leadersString)` at `ui/community/LeadersViewModel.kt:30-35`, guarded by its own `isNotEmpty()` check. Parsing a persisted document is data-layer work: the repository interface currently exposes a serialisation format instead of a domain type, and every caller has to know the blob may be empty.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt` (the one method signature), `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` (`getCommunityLeaders` only, lines 404-406), `app/src/main/java/org/ole/planet/myplanet/ui/community/LeadersViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt` (test at line 841), `app/src/test/java/org/ole/planet/myplanet/ui/community/LeadersViewModelTest.kt` (stubs at lines 44 and 57). Do NOT touch `model/UserEntity.kt` (`parseLeadersJson` stays exactly as it is), `services/SharedPrefManager.kt`, or the three other `parseLeadersJson(sharedPrefManager.getCommunityLeaders())` call sites in `VoicesFragment`, `TeamsVoicesFragment` and `ReplyActivity` — those files belong to open PRs and keep reading prefs directly.

steps:
1. Change the interface method to `fun getCommunityLeaders(): List<UserEntity>` and add the `UserEntity` import.
2. In the impl, return `UserEntity.parseLeadersJson(sharedPrefManager.getCommunityLeaders())` — `parseLeadersJson` already returns an empty list for blank/invalid input (see `UserEntityParseLeadersTest`), so no extra guard is needed.
3. Simplify `LeadersViewModel.loadLeaders` to `_leaders.value = configurationsRepository.getCommunityLeaders()`, dropping the emptiness branch and the `UserEntity.parseLeadersJson` call; keep the `dispatcherProvider.default` launch.
4. Update the repository test to stub `sharedPrefManager.getCommunityLeaders()` with a leaders JSON string and assert the returned `List<UserEntity>` (name/id of the first entry), plus one case where the pref is `""` returning an empty list.
5. Update `LeadersViewModelTest` to stub the repository with lists instead of JSON strings.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manual: open *Community → Leaders* — the same leader rows render in the same order; on a fresh install with no community config synced yet the list is empty and does not crash.

size budget: ~40 changed lines, 5 files.

out of scope: do not make the method `suspend`, do not migrate the other three `parseLeadersJson` call sites, and do not touch `getPlanetType`/`getParentCode`/`getCommunityName`.

---

### 6. move the shelves-with-data preference cache out of SyncManager into SyncRepository (roadmap 1+5)

context: `SyncManager` owns a hand-rolled 6-hour `SharedPreferences` cache of "which shelves actually hold data": `getCachedShelvesWithData` and `cacheShelvesWithData` at `services/sync/SyncManager.kt:477-500` each re-declare the string keys `"shelves_with_data"` and `"shelves_cache_time"`, do their own TTL arithmetic (`cacheValidityHours * 60 * 60 * 1000`), and split/join a comma-separated list. That is persistence logic — key names, encoding and expiry — living in the sync orchestrator, so the two functions must agree on the keys by convention and nothing can unit-test the cache without driving a whole sync.

files: `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepository.kt` (two method declarations), `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt` (implement them; add `SharedPrefManager` and `TimeProvider` constructor parameters — both are already Hilt-provided and injected all over the codebase, so no module change is needed), `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` (delete the two private functions, call the repository from `getShelvesWithDataBatchOptimized` at lines 440-471). Do NOT touch `TransactionSyncManager.kt` or `HeavyTableSyncWorker.kt` (open PRs own them), and leave `SyncManager`'s other `sharedPrefManager` uses (e.g. line 196) alone.

steps:
1. Add `suspend fun getCachedShelvesWithData(): List<String>` and `suspend fun cacheShelvesWithData(shelves: List<String>)` to `SyncRepository`.
2. Implement both in `SyncRepositoryImpl`, moving the key names and the TTL into `private companion object` constants (`SHELVES_CACHE_KEY`, `SHELVES_CACHE_TIME_KEY`, `SHELVES_CACHE_VALIDITY_MS`) so the two halves share one definition; keep the exact same pref keys, comma encoding and 6-hour window so existing installs keep their cache.
3. Delete `getCachedShelvesWithData`/`cacheShelvesWithData` from `SyncManager` and point lines 442 and 469 at `syncRepository` (already injected at line 73).
4. Remove any `SyncManager` import left unused.
5. Add a small `SyncRepositoryImplTest` (new file) covering three cases: a fresh write round-trips; a read older than the TTL returns empty; an empty stored value returns empty.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manual: run a full manual sync twice within a few minutes — the second run reuses the cached shelf list (no second `shelf/_all_docs` fan-out in logcat under tag `SyncPerf`) and pulls the same data as before.

size budget: ~70 changed lines, 4 files (one new test file).

out of scope: do not restructure `resourceTransactionSync`/`myLibraryTransactionSync` or move the raw `apiInterface` calls out of `SyncManager` — that is a much larger job. Do not change the cache keys or the TTL value.

---

### 7. let the life repository own user-id normalisation (roadmap 3+8+9)

context: `LifeViewModel.resolveUserId()` (`ui/life/LifeViewModel.kt:30-34`) reimplements the rule the repository already applies internally: `LifeRepositoryImpl.normalizeUserId` (`repository/LifeRepositoryImpl.kt:29-31`) is the same `takeIf { it.isNotBlank() && it != "--" }`. So the `"--"` sentinel — a persistence detail — is encoded twice, and the two copies can drift. The same method also wraps repository calls in `withContext(dispatcherProvider.io)` (lines 38, 48, 58) although `LifeRepositoryImpl` performs no blocking work of its own and Room already runs `suspend` DAO queries off the main thread, so each call pays a needless dispatcher hop.

files: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`. Do NOT touch `repository/LifeRepository.kt`, `repository/LifeRepositoryImpl.kt` (its `normalizeUserId` is already correct and is the single source of truth), `ui/life/LifeFragment.kt`, `ui/life/LifeAdapter.kt`, or `base/BaseDashboardFragment.kt` (open PRs own the last one).

steps:
1. Delete `resolveUserId()` and pass the raw id straight through: `userRepository.getCurrentUserId().orEmpty().ifEmpty { userRepository.getUserModel()?.id.orEmpty() }`, letting `LifeRepositoryImpl` normalise blank/`"--"` as it already does.
2. Keep feeding the same value to `MyLife.defaultItems(userId, context::getString)` so seeded default rows carry the identical `userId` they do today.
3. Drop the three `withContext(dispatcherProvider.io)` wrappers, and remove the `dispatcherProvider` constructor parameter and its import if nothing else in the file uses it.
4. Update `LifeViewModelTest`: its existing cases at lines 58, 90, 113 and 127 already cover a normal id, an empty list, a null id and the `getUserModel()` fallback — restub them for the simplified call and add one asserting a `"--"` current-user id is forwarded verbatim to `getMyLifeByUserId` (the repository, not the ViewModel, decides what it means).
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manual: open *My Life*, toggle an item's visibility and drag to reorder — the list persists across a screen re-entry, and the dashboard tiles still reflect the visibility change. Logging in as a fresh user still seeds the default life items.

size budget: ~25 changed lines, 2 files.

out of scope: do not move `MyLife.defaultItems` or the `context::getString` label resolution (the dashboard's `DashboardPluginFragment.getMyLifeListBase` shares that call and is out of this task's reach), and do not add methods to `LifeRepository`.

---

### 8. have the two remaining free ViewModels read the user from UserRepository, not UserSessionManager (roadmap 3+4)

context: `CommunityTabViewModel` (`ui/community/CommunityTabViewModel.kt:24,35`) and `TakeCourseViewModel` (`ui/courses/TakeCourseViewModel.kt:39,109`) inject the `services` singleton `UserSessionManager` solely to call `getUserModel()`, and that method is a one-line delegation to the repository: `services/UserSessionManager.kt:38-40` is `return userRepository.getUserModel()`. `UserSessionManager` additionally owns login/logout side effects, prefs writes and resource-open counters, so a ViewModel that only wants the current user is reaching across the UI → service boundary and pulling in that whole surface as a test dependency for nothing.

files: `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModelTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt`. Do NOT touch `services/UserSessionManager.kt` itself, `ui/user/UserProfileViewModel.kt` (it only references the `KEY_RESOURCE_OPEN` constant, which is fine), or any of the fragments/activities that legitimately use the session manager for login/logout (`SettingsActivity`, `SyncActivity`, `HealthExaminationActivity`, `TakeCourseFragment`, `RequestsFragment`, `BaseContainerFragment`).

steps:
1. In `CommunityTabViewModel`, swap the `UserSessionManager` constructor parameter for `private val userRepository: UserRepository` and change line 35 to `userRepository.getUserModel()`; fix imports.
2. Do the same in `TakeCourseViewModel` for the single call at line 109. If it already injects `UserRepository`, reuse that instance instead of adding a second parameter.
3. Update both ViewModel tests to mock `UserRepository.getUserModel()` instead of `UserSessionManager.getUserModel()`, dropping the session-manager mock entirely.
4. Confirm no other member of `UserSessionManager` was in use in either ViewModel (grep the two files for `userSessionManager`) so nothing is left half-migrated.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `grep -rn "UserSessionManager" app/src/main/java/org/ole/planet/myplanet/ui --include=*ViewModel.kt` returns only the `KEY_RESOURCE_OPEN` constant references in `UserProfileViewModel`. Manual: open *Community* — the community name, parent code and planet type render as before; open a course and take a step — progress still records against the logged-in user.

size budget: ~20 changed lines, 4 files.

out of scope: do not delete or shrink `UserSessionManager`, do not migrate its fragment/activity callers, and do not add anything to `UserRepository`.

---

### 9. give `UserEntity` one effective-id accessor and use it in the health screens (roadmap 8+9)

context: the "prefer the CouchDB `_id`, fall back to the local `id`" rule is copy-pasted three times in the health feature: `ui/health/HealthViewModel.kt:79` (`val uid = if (currentUser?._id.isNullOrEmpty()) currentUser?.id else currentUser?._id`), and `ui/health/MyHealthFragment.kt:142` and `:273` with the same expression on a non-null receiver. `UserEntity` declares both columns (`model/UserEntity.kt:22-23`) but offers no accessor, so an identity rule that decides which health records a patient sees lives in the UI in triplicate — and each copy trims separately (`normalizedId = userId?.trim()` at `MyHealthFragment.kt:274` and `HealthViewModel.kt:80`).

files: `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt` (add a top-level extension property **outside** the `@Entity` class so Room maps no new column), `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt` (line 79 only), `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt` (lines 142 and 273 only), `app/src/test/java/org/ole/planet/myplanet/model/UserEntityTest.kt`. Do NOT touch `repository/HealthRepository.kt`/`HealthRepositoryImpl.kt`, `data/room/dao/HealthExaminationDao.kt`, `HealthExaminationActivity.kt` or `HealthExaminationViewModel.kt` — the health repository and DAO are owned by an open PR.

steps:
1. In `UserEntity.kt`, after the class body, add `val UserEntity.effectiveId: String? get() = _id?.takeIf { it.isNotEmpty() } ?: id` — a top-level extension, never a member property, so Room's schema is unaffected and no `@Ignore` is required.
2. Replace the expression at `HealthViewModel.kt:79` with `val uid = currentUser?.effectiveId`, leaving the existing `?.trim()` normalisation on the next line untouched.
3. Replace both `MyHealthFragment.kt` expressions with `currentUser.effectiveId` and `selected.effectiveId`.
4. Add the import in the two UI files.
5. In `UserEntityTest`, add cases for: `_id` present (returns `_id`), `_id` null (returns `id`), `_id` empty string (returns `id`) — the third is the one the old `isNullOrEmpty()` handled and a naive `?:` rewrite would break.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `grep -rn "_id.isNullOrEmpty()) " app/src/main/java/org/ole/planet/myplanet/ui` returns nothing. Manual: open *My Health* as a normal user — your own examination history loads; as a manager, use *Select patient*, pick a member, and their records load. Both must behave identically for a user synced from the server (`_id` set) and a locally created one (`_id` empty).

size budget: ~20 changed lines, 4 files.

out of scope: do not migrate the two `_id.isNullOrEmpty()` checks in `SubmissionsRepositoryImpl` (open PRs own that file), and do not change how health records are queried.

---

### 10. drop the pointless `suspend` on the enterprises reports flow accessor (roadmap 3)

context: `EnterprisesViewModel.getReportsFlow` is declared `suspend` (`ui/enterprises/EnterprisesViewModel.kt:100-102`) even though it only returns the cold `Flow` from `EnterprisesRepository.getReportsFlow`, which is correctly non-suspend (`repository/EnterprisesRepository.kt:11`). The false suspend forces the fragment to open a coroutine just to obtain the flow and then start a second collection inside it — `viewLifecycleOwner.lifecycleScope.launch { val flow = viewModel.getReportsFlow(teamId); collectLatestWhenStarted(flow) { ... } }` at `ui/enterprises/EnterprisesReportsFragment.kt:126-131` — so a `collectLatestWhenStarted` (a lifecycle-aware collector) is nested inside a plain `launch` that outlives `STARTED`. The same file also calls `teamsRepository.getTeamNameFromPrefs()` three times (lines 69, 84, 114) for one value.

files: `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt` (the `getReportsFlow` signature), `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt` (the collection at lines 126-131 and the export block at lines 79-95). Do NOT touch `repository/EnterprisesRepository.kt` or `repository/EnterprisesRepositoryImpl.kt`, and leave `exportReportsAsCsv` genuinely `suspend` on both sides. `EnterprisesFinancesFragment.kt` calls neither method — leave it alone.

steps:
1. Remove `suspend` from `EnterprisesViewModel.getReportsFlow`, keeping the return type `Flow<List<MyTeam>>`.
2. In the fragment, collapse the nested launch to a single `collectLatestWhenStarted(viewModel.getReportsFlow(teamId)) { reportList -> updatedReportsList(reportList) }`, so the collection is lifecycle-scoped end to end.
3. In the export callback (line 84), hoist the team name into one local read before `viewModel.exportReportsAsCsv(teamId, teamName)` rather than calling `getTeamNameFromPrefs()` again inside the coroutine.
4. Remove imports left unused in either file.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green. Manual: open an enterprise's *Reports* tab — the list renders and live-updates when you add, edit or archive a report; rotate the device and add a report, confirming exactly one list refresh (no duplicated rows from a leaked second collector); *Export CSV* still writes a file whose header names the team.

size budget: ~15 changed lines, 2 files.

out of scope: do not remove the `TeamsRepository` injection from the fragment (untangling that cross-feature dependency needs `EnterprisesRepository` changes, which task 1 already reserves), and do not touch `ReportEvent` handling.
