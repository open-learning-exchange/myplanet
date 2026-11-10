# Task-generation round — repository boundaries

date: 2026-11-10
base commit: 2093a6224b221079f1413e2ab1f22cebec8cf736 (`all: smoother base recycler download offer tracking (fixes #5278) (#18543)`)
open PRs checked: 4075, 8175, 10993, 13287, 13355, 13604, 13657, 13848, 13928, 14427, 14650, 14883, 15108, 15226, 15266, 15267, 15559, 15808, 15820, 15824, 15825, 15951, 16594, 17694, 17811, 17832, 18029, 18045, 18076, 18455, 18539, 18540, 18551, 18554, 18555 (35 total, all fetched and diffed)

Focus this round: reinforcing repository boundaries between layers, calling out cross-feature data leaks, tightening repository interfaces, moving data functions one by one out of UI/data/service into repositories, other DAO/Room optimizations, and smoother repository ↔ view-model relationships.

Two notes on PR hygiene for the executors:

- A month-old set of KMP draft PRs (#17694, #17832, #18029, #18045) touches most `repository/*Impl.kt` and many utils. Files also touched by those drafts are usable here (they are drafts, not recent merges) but are flagged per task — leave those drafts' additions alone and keep diffs minimal to avoid merge pain.
- `di/RepositoryModule.kt` already binds every impl to its narrow interfaces (`bindUserReadRepository(impl: UserRepositoryImpl)`, `bindTeamsMembersRepository(impl: TeamsRepositoryImpl)`, …). Narrowing a consumer's injected type therefore needs **zero DI changes**, and moving a function from `UserRepository` up onto `UserReadRepository`/`UserSyncRepository` needs **zero impl changes** — `UserRepository` extends both and `UserRepositoryImpl` implements all of them (`repository/UserRepository.kt:23`, `repository/UserRepositoryImpl.kt:86`).

---

### 1. Re-scope sync-only user reads onto UserSyncRepository and rewire UploadToShelfService (roadmap 1, 4, 9)

context: `UserRepository` is the last god-interface left (~70 suspend functions). Three of them are consumed only by shelf-upload sync: `getSyncedUsers` (UserRepository.kt:32), `getSyncedUserByName` (UserRepository.kt:34) and `getPendingSyncUsers` (UserRepository.kt:38) are all called exclusively from `services/UploadToShelfService.kt:35,102,125` — the same service that already injects `UserSyncRepository` for `checkAndUploadUser`. Moving them makes `UserRepository` strictly app-facing and lets the upload path hold one user contract instead of two.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt` — delete the three declarations at lines 32, 34, 38.
- `app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt` — add the three declarations. Neighbor caveat: open draft PR #15808 also edits this file; append the new members, do not reorder existing ones.
- `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt` — rewire the call sites at lines 35, 102, 125 (and `getUserByName` at line 58 stays on `userRepository` — leave it).

steps:
1. Delete `getSyncedUsers()`, `getSyncedUserByName(name)`, `getPendingSyncUsers(limit)` declarations from `UserRepository`.
2. Add the same three signatures to `UserSyncRepository` (they are sync-facing reads; `UserRepositoryImpl` already implements `UserSyncRepository`, so no impl or DI change is needed — `repository/UserRepositoryImpl.kt:86`, `di/RepositoryModule.kt` binds `UserSyncRepository` to the same impl).
3. In `UploadToShelfService` change `userRepository.getPendingSyncUsers(100)` → `userSyncRepository.getPendingSyncUsers(100)`, `userRepository.getSyncedUsers()` → `userSyncRepository.getSyncedUsers()`, `userRepository.getSyncedUserByName(...)` → `userSyncRepository.getSyncedUserByName(...)`.
4. Keep `userRepository.getUserByName` at line 58 on `userRepository` (it is app-facing, not sync-facing); leave the field order and imports consistent with the file's existing style.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the "upload to shelf" sync path is unchanged (it calls the same impl); `UserRepository` no longer exposes sync-only reads.

size budget: ~12 changed lines, 3 files.

out of scope: `getUsersForHealthSync` (consumed by `TransactionSyncManager`, owned by the open KMP draft PRs); any UserRepositoryImpl edits.

---

### 2. Hoist profile/sync-status reads onto UserReadRepository and slim three view models (roadmap 1, 4, 9)

context: `UserReadRepository` exposes only `getUserById`, `getUsersByIds`, `getUserModel` (UserReadRepository.kt:6-8), so consumers of any other read still inject the 70-function `UserRepository`. Four functions are pure reads that view models already use: `getUserProfile` (UserRepository.kt:82; used by `ui/ratings/RatingsViewModel.kt:55,85`), `getUserImageUrl` (UserRepository.kt:83), `hasActiveUserSyncAction` (UserRepository.kt:90; used by `ui/components/MarkdownViewModel.kt:16-17` which forwards it to `MarkdownDialogFragment.kt:119`), and the sorted/search reads `getUsersSortedBy` + `searchUsers` (UserRepository.kt:37,39; used by `ui/health/HealthViewModel.kt:109`).

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt` — add the five declarations.
- `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt` — inject `UserReadRepository` instead of `UserRepository`.
- `app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownViewModel.kt` — inject `UserReadRepository`.
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt` — inject `UserReadRepository`.

steps:
1. Add `getUserProfile()`, `getUserImageUrl()`, `hasActiveUserSyncAction()`, `getUsersSortedBy(fieldName, descending)`, `searchUsers(query, sortField, descending)` to `UserReadRepository`. Because `UserRepository : UserReadRepository` (UserRepository.kt:23) the declarations are still visible through the wide interface; do **not** remove them from `UserRepository.kt` (another work order owns that file).
2. In each of the three view models, change the constructor parameter type from `UserRepository` to `UserReadRepository` and fix the import.
3. In `HealthViewModel` the `userRepository` field serves all four call sites (109, 124, 159, 201) — all are reads, so the single swap covers it; keep `healthRepository` and `realtimeSyncManager` untouched.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; ratings profile load, markdown challenge-completion check and health patient search behave identically; the three view models can no longer reach user writes.

size budget: ~18 changed lines, 4 files.

out of scope: removing the (now redundant) declarations from `UserRepository.kt`; `RatingsRepositoryImpl` (owned by open PR #18076); the SQL-side sort fix (separate work order).

---

### 3. Point five read-only view models at UserReadRepository — batch A (roadmap 4, 9)

context: Every file below injects `UserRepository` but calls only `getUserModel()` — a function already exposed on `UserReadRepository`. Narrowing the injected type costs one import and one type name per file and removes ~65 unused surface functions per consumer: `CalendarViewModel.kt` (`userRepository.getUserModel()?.id`), `CommunityTabViewModel.kt` (`getUserModel()`), `DashboardElementViewModel.kt` (`getUserModel()`), `ActivitiesViewModel.kt` (`getUserModel()?.name`), `FeedbackListViewModel.kt` (`getUserModel()`).

files (5, all verified clean of every open PR):
- `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt`

steps:
1. In each file, change the injected constructor parameter `userRepository: UserRepository` to `userRepository: UserReadRepository`.
2. Replace the `import org.ole.planet.myplanet.repository.UserRepository` with `import org.ole.planet.myplanet.repository.UserReadRepository`, keeping import order per `docs/CODE_STYLE_GUIDE.md`.
3. Do not rename the parameter or touch the other injected repositories (`ActivitiesViewModel` also injects `activitiesRepository` — leave it).
4. Compile the module; no other edits needed — `getUserModel` is already on `UserReadRepository` (UserReadRepository.kt:8) and `RepositoryModule` binds it to `UserRepositoryImpl`.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; each screen shows the same current-user-derived data (calendar, community tab, dashboard elements, activities, feedback list).

size budget: ~15 changed lines, 5 files.

out of scope: `SurveysViewModel`/`VoicesViewModel`/`TeamsTasksViewModel`/`DashboardViewModel`/`SubmissionViewModel` (all owned by open PRs); the identical narrowing for the other view-model batch (separate work order).

---

### 4. Point five read-only view models at UserReadRepository — batch B (roadmap 4, 9)

context: Same pattern as batch A — five more view models inject the 70-function `UserRepository` purely for `getUserModel()`/`getUserById()`, both already on `UserReadRepository`: `ResourcesAccessViewModel.kt` (`getUserModel()?.isGuest()`), `ResourcesViewModel.kt` (`getUserModel()`), `PersonalsViewModel.kt` (`getUserModel()`), `SubmissionDetailViewModel.kt` (`getUserById(it)`), `ResourceViewerViewModel.kt` (`getUserModel()` twice).

files (5, all verified clean of every open PR):
- `app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt` — neighbor caveat: `ui/viewer/WebViewActivity.kt` is owned by open PR #18551; do not touch it.

steps:
1. In each file, change `userRepository: UserRepository` to `userRepository: UserReadRepository`.
2. Swap the import to `org.ole.planet.myplanet.repository.UserReadRepository`, sorted per house style.
3. Leave parameter names and all other injected repositories unchanged.
4. Compile; `RepositoryModule` already provides `UserReadRepository` from `UserRepositoryImpl` — no DI edits.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; resource access gating (guest check), resources list, personals list, submission detail author lookup and the shared resource viewer all behave identically.

size budget: ~15 changed lines, 5 files.

out of scope: batch A view models (separate work order); `TakeCourseViewModel`/`CoursesStepsViewModel`/`LifeViewModel` narrowing (separate work order); any change to `UserRepositoryImpl`.

---

### 5. Consolidate guest-login and authenticate orchestration into LoginViewModel (roadmap 3, 9)

context: The identical `createGuestUser(...) → profileDbHandler.saveUserInfoPref("", model) → onLogin()` block is written out three times — `ui/sync/GuestLoginExtensions.kt:68-74`, `ui/sync/LoginActivity.kt:597-604` and `ui/sync/LoginActivity.kt:639-647`. Separately, `SyncActivity.authenticateUser` (SyncActivity.kt:390-407) does repository orchestration inline: `userRepository.hasAtLeastOneUser()` → `userRepository.authenticateUser(...)` → `profileDbHandler.saveUserInfoPref(password, user)` with a dialog + try/catch — data-layer work living in a base activity, reachable from `AuthUtils.login` (AuthUtils.kt:19,49). Both belong behind `LoginViewModel`.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt` — add `loginAsGuest(username)` (wrapping `createGuestUser` + `saveUserInfoPref` + returning the model for `onLogin`) and `authenticate(username, password, isManagerMode): Boolean` (the hasAtLeastOneUser → authenticateUser → saveUserInfoPref sequence, minus the dialog).
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt` — replace lines 68-74 with `viewModel.loginAsGuest(username)`.
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt` — replace both duplicated blocks (597-604, 639-647) with the same VM call; also update the `AuthUtils` call sites if signatures require it.
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` — turn `authenticateUser` into a thin delegate to `loginViewModel.authenticate(...)` (or expose the VM where it is defined); keep the `server_not_configured…` alert on the caller side.
- `app/src/main/java/org/ole/planet/myplanet/utils/AuthUtils.kt` — neighbor caveat: KMP draft PRs #18045/#18029/#17832 touch this file; only adjust the `authenticateUser` call sites, leave everything else alone.

steps:
1. In `LoginViewModel`, add `suspend fun loginAsGuest(username: String): UserEntity?` that calls `userRepository.createGuestUser(username)`, then `profileDbHandler.saveUserInfoPref("", model)` on success and returns the model (null on failure). `profileDbHandler` is `UserSessionManager` — inject it or reuse the field the VM already exposes.
2. Add `suspend fun authenticate(username: String?, password: String?, isManagerMode: Boolean): Boolean` implementing the try/catch + `hasAtLeastOneUser` + `authenticateUser` + `saveUserInfoPref(password, user)` flow from `SyncActivity.kt:390-407`; return `false` on any failure and let the caller keep showing the alert/toast.
3. Replace the three guest-login blocks with `viewModel.loginAsGuest(username)` followed by the existing `onLogin()` / `saveUsers(username, "", "guest")` calls.
4. Update `SyncActivity.authenticateUser` (or its `AuthUtils` call sites) to delegate to the VM; keep the dialog string in the activity layer.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; guest login from the login dialog and from the guest-login path still land on `onLogin()` with prefs saved; member login via `AuthUtils.login` still saves credentials and greets the user; the server-not-configured alert still fires when no users exist.

size budget: ~90 changed lines, 4-5 files.

out of scope: the `loginNetworkJob` reconnect-upload block in `SyncActivity` (~l640-660) — leave it; `LoginSyncManager` (owned by KMP drafts); any `MainApplication` changes (owned by open PR #18540).

---

### 6. Push the blank-community filter into the DAO query (roadmap 1, 7, 9)

context: `CommunityDao.getAllSorted` returns every row (`SELECT * FROM community ORDER BY weight ASC`, CommunityDao.kt:12-13) and its only consumer `ui/sync/ServerDialogExtensions.kt:298-299` immediately discards blank names in memory: `communities.filter { !TextUtils.isEmpty(it.name) }`. Filtering belongs in SQL — cheaper, and removes a data-quality rule from the UI layer.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CommunityDao.kt` — the `getAllSorted` query.
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt` — drop the client-side filter.

steps:
1. Change the query to `SELECT * FROM community WHERE name IS NOT NULL AND TRIM(name) != '' ORDER BY weight ASC`.
2. In `ServerDialogExtensions.kt:298-299`, replace `val communities = communityRepository.getAllSorted(); val nonEmptyCommunities = communities.filter { !TextUtils.isEmpty(it.name) }` with a single `val communities = communityRepository.getAllSorted()` and use `communities` for the adapter.
3. Remove the now-unused `TextUtils` import if nothing else in the file uses it (check first — the file is long).
4. Leave `CommunityRepository`/`CommunityRepositoryImpl` pass-throughs unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the manual server-config spinner (`spnCloud`) lists the same non-empty community names in weight order.

size budget: ~5 changed lines, 2 files.

out of scope: `LoginViewModel.syncCommunityDocs` (also calls `getAllSorted` — benefits automatically, no edit needed); any other CommunityDao member.

---

### 7. Replace the team-notification update-then-insert pair with a single upsert (roadmap 1, 7, 9)

context: `NotificationsRepositoryImpl.updateTeamNotification` (NotificationsRepositoryImpl.kt:343-350) does a two-step write: `if (teamNotificationDao.updateCount(teamId, "chat", count) == 0) insert(TeamNotification()…)`. That is the classic non-atomic update-or-insert race; `TeamNotificationDao` already has `updateCount` (line 12), `insert(REPLACE)` (line 23) and `getByTypeAndParentIds` — Room 2.8.4 supports `@Upsert`, which expresses the same intent atomically.

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamNotificationDao.kt` — add an `@Upsert suspend fun upsert(item: TeamNotification)` next to `insert`.
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — neighbor caveat: KMP draft PRs #17694/#17832/#18029/#18045 touch this file; change only the `updateTeamNotification` body.

steps:
1. Add `@Upsert suspend fun upsert(item: TeamNotification)` to `TeamNotificationDao` (import `androidx.room.Upsert`).
2. In `updateTeamNotification`, replace the updateCount/insert pair with a read-modify-upsert: fetch the existing `TeamNotification` for `(teamId, "chat")` via `getByTypeAndParentIds("chat", listOf(teamId)).firstOrNull()`, apply `lastCount = count` (create a new entity with the same `id`/defaults when absent), then `teamNotificationDao.upsert(...)`.
3. Keep `updateCount` on the DAO only if another caller uses it — grep first; if `updateTeamNotification` was its sole caller, remove `updateCount` as well (no unused code).
4. Keep the `"chat"` literal and the entity defaults (`id = UUID.randomUUID()`, `parentId = teamId`, `type = "chat"`) identical to the current insert.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; team chat-notification counts still update on first and subsequent messages, now in a single atomic write.

size budget: ~14 changed lines, 2 files.

out of scope: `getEnrichedNotifications`'s cross-feature enrichment via `voicesRepository`/`teamsRepository` (NotificationsRepositoryImpl.kt:367,371) — bigger refactor, other PRs own those neighbors.

---

### 8. Narrow ChatViewModel to TeamsShareRepository + UserReadRepository and lift the community-summary fallback into the model (roadmap 1, 3, 9)

context: `ChatViewModel.loadShareTargets` (ChatViewModel.kt:194-211) calls `teamsRepository.getTeamSummaries`, `getShareableEnterpriseSummaries`, `getTeamSummaryById` — all three already live on the narrow `TeamsShareRepository` (TeamsShareRepository.kt:5-9) — while `getUserById` (lines 191, 297) is on `UserReadRepository`. The view model also builds a `TeamSummary(...)` fallback for a plain community name inline (lines 202-211) — domain-object construction in UI code.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` — swap `teamsRepository: TeamsRepository` → `TeamsShareRepository` and `userRepository: UserRepository` → `UserReadRepository`; call the new factory instead of the inline constructor.
- `app/src/main/java/org/ole/planet/myplanet/model/TeamSummary.kt` — add a companion factory `community(id: String, name: String)` returning the same fallback values the VM currently passes (all nullable fields null except `_id` and `name`).

steps:
1. Add `companion object { fun community(id: String, name: String) = TeamSummary(_id = id, name = name, teamType = null, …) }` to `TeamSummary` — mirror the exact nulls currently constructed in ChatViewModel.
2. In `ChatViewModel`, replace the `?: TeamSummary(...)` block in `loadShareTargets` with `?: TeamSummary.community(id, communityName ?: "")`.
3. Change the injected types to `TeamsShareRepository` + `UserReadRepository` and fix imports (RepositoryModule already binds `TeamsShareRepository` to `TeamsRepositoryImpl`).
4. Verify no other function in the VM calls the removed wide interfaces — the grep shows only the three share functions + `getUserById`, so this is safe; if an uncovered call exists, stop and leave the wide injection for that parameter only.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the chat share sheet still lists community + teams + enterprises identically; the VM can no longer mutate teams or users.

size budget: ~25 changed lines, 2 files.

out of scope: `TeamsRepositoryImpl.getTeamSummaryById` internals (owned by open PRs); `paginator`/`chatRepository` members.

---

### 9. Push patient-list sorting into SQLite ORDER BY (roadmap 1, 7, 9)

context: `UserRepositoryImpl.getUsersSortedBy`/`searchUsers` load **all** users then sort in memory via `sortUsers` — a 17-branch `when` over nullable fields (UserRepositoryImpl.kt:170-184, 339-358). This is exactly the DAO optimization the round asks for: a whitelisted column map + `ORDER BY <col> COLLATE NOCASE <dir>` does it in SQLite, matching the `@RawQuery`/`SimpleSQLiteQuery` pattern already used in `MyLibraryDao`. `HealthViewModel` is the only consumer (`HealthViewModel.kt:109`).

files:
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt` — add a `@RawQuery(observedEntities = [UserEntity::class])` method `sorted(query: SupportSQLiteQuery): List<UserEntity>` (or reuse `search`'s LIKE clause in the raw statement).
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt` — neighbor caveat: KMP draft PRs #18045/#18029/#17832/#17694 and draft #15808 all touch this file; keep edits confined to `getUsersSortedBy`/`searchUsers`/`sortUsers`.

steps:
1. Add a private whitelist map `fieldName → column` for the 17 keys in `sortUsers` (joinDate→`joinDate`, name→`name`, firstName→`firstName`, lastName→`lastName`, middleName→`middleName`, email→`email`, planetCode→`planetCode`, parentCode→`parentCode`, level→`level`, language→`language`, gender→`gender`, dob→`dob`, age→`age`, birthPlace→`birthPlace`, isUpdated→`isUpdated`, isArchived→`isArchived`); unknown keys must fall back to an unsorted `SELECT * FROM users` — never interpolate the raw field name.
2. In `UserDao` add `@RawQuery suspend fun sorted(query: SupportSQLiteQuery): List<UserEntity>` with imports `androidx.sqlite.db.SupportSQLiteQuery`, `androidx.room.RawQuery`, `SimpleSQLiteQuery`.
3. Rewrite `getUsersSortedBy` to build `SimpleSQLiteQuery("SELECT * FROM users ORDER BY " + col + " COLLATE NOCASE " + (if (descending) "DESC" else "ASC"))` (skip `COLLATE NOCASE` for the numeric fields joinDate/age/dob/isUpdated/isArchived) and call `userDao.sorted(...)`.
4. Rewrite `searchUsers` identically but with the existing `name/firstName/lastName LIKE :pattern ESCAPE '\'` predicate inlined into the raw statement via `query.bindString`; keep `sortUsers` as a private helper only if still referenced — otherwise delete it (no unused code).

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the health patient list sorts identically by every column toggle; no full-table copy is materialized for sorting.

size budget: ~60 changed lines, 2 files.

out of scope: adding an index or changing `search`'s LIKE pattern; `getUsersByIds`'s double chunking (UserRepositoryImpl.kt:108-115 vs `UserDao.getUsersByAnyIds` chunk-450 — worth doing later, but `UserRepositoryImpl` edits must stay minimal this round given the draft-PR conflicts).

---

### 10. Point the remaining read-only course view models at UserReadRepository (roadmap 4, 9)

context: `TakeCourseViewModel.kt` (calls `userRepository.getUserModel()` only) and `CoursesStepsViewModel.kt` (`userRepository.getUserModel()` at line 68) both inject the wide `UserRepository` for a single read. `CoursesStepsViewModel` additionally injects courses/resources/configurations/progress repositories — all legitimately needed; only the user dependency narrows. Same swap as batches A/B, completing the getUserModel cohort. (Batch B left these two out to stay under 5 files; this closes the set. `LifeViewModel` is deliberately excluded — it needs `getCurrentUserId`, which is only on `UserRepository` at line 80.)

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` — neighbor caveat: `CoursesViewModel.kt`/`CoursesFragment.kt` are owned by open PR #13657 — do not touch them.
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`

steps:
1. Change `userRepository: UserRepository` → `userRepository: UserReadRepository` in both files, fixing imports.
2. Leave every other injected repository (`coursesRepository`, `activitiesRepository`, `progressRepository`, `ratingsRepository`, `resourcesRepository`, `configurationsRepository`) untouched.
3. Compile; no DI changes needed.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; course step navigation (progress save, resource download) and take-course flow behave identically.

size budget: ~8 changed lines, 2 files.

out of scope: `HealthExaminationViewModel`/`AchievementViewModel`/`UserProfileViewModel`/`LearnerRegistrationViewModel`/`UserInformationViewModel`/`UserUploadViewModel` — all genuinely need wide-interface writes; `SurveysViewModel`/`DashboardViewModel`/`SubmissionViewModel`/`TeamViewModel`/`TeamsTasksViewModel`/`TeamCalendarViewModel`/`VoicesViewModel`/`EventsDetailViewModel` — all owned by open PRs.

---

signed: devin · cognition devin (opus-class) · high effort
