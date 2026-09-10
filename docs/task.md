# myPlanet refactor round — repository boundary work orders

**Open PRs checked (38):** #4075, #8175, #10993, #13287, #13355, #13415, #13604, #13657, #13848, #13928, #14427, #14650, #14883, #14893, #14960, #15108, #15198, #15226, #15266, #15267, #15412, #15519, #15559, #15808, #15820, #15824, #15825, #15951, #16101, #16270, #16594, #16623, #16624, #16690, #16986, #16987, #16988, #16989

**Off-limits:** every path those PRs touch (~428 files). Candidates that needed locked files (`TeamsRepositoryImpl`, `CoursesRepositoryImpl`, `VoicesRepository*`, `UserRepositoryImpl`, `ActivitiesRepository*`, `AppDatabase`, `RoomModule`, `RepositoryModule`, etc.) were discarded.

**Focus:** repository boundaries, cross-feature data leaks, UI/service → repository moves, DAO tighten-ups, smoother repository ↔ ViewModel wiring.

**Rules applied:** exactly 10 independent tasks; no shared files; verified symbols only; ≤5 files / ~≤150 LOC each; no new deps/TODOs/unused code; plan only.

---

### Task 1 — Hoist team-member mutations out of `MembersFragment` into `RequestsViewModel`
**Roadmap:** 3 (ViewModel/use-case), also 1 (data-layer cleanup via UI stop-calling repos)

**Problem:** `MembersFragment` already uses `RequestsViewModel` for join-request state, but still calls `teamsRepository` (from `BaseTeamFragment`) directly for load/leave/remove/leader. That bypasses the members ViewModel boundary and keeps data orchestration in the Fragment.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`

**Existing symbols (verified):**
- `MembersFragment.loadMembers`, `handleLeaveTeam`, `handleRemoveMember`, `handleMakeLeader`
- `teamsRepository.getJoinedMembersWithVisitInfo`, `getNextLeaderCandidate`, `updateTeamLeader`, `removeMember` (via `BaseTeamFragment.teamsRepository`)
- `RequestsViewModel.fetchMembers`, `respondToRequest`, `RequestsUiState`
- `TeamsMembersRepository.getJoinedMembersWithVisitInfo`, `getNextLeaderCandidate`, `updateTeamLeader`, `removeMember`, `recordTeamActivity` (already injected into `RequestsViewModel`)

**Do not touch:** `BaseTeamFragment.kt`, `TeamsRepository*`, `TeamsMembersRepository.kt`, adapters

**Steps:**
1. Extend `RequestsViewModel` / `RequestsUiState` with joined-member list + leader flag (or a sibling members state flow).
2. Add ViewModel methods that wrap `TeamsMembersRepository.getJoinedMembersWithVisitInfo`, `getNextLeaderCandidate`, `updateTeamLeader`, `removeMember` (and `recordTeamActivity` where appropriate).
3. Rewrite `MembersFragment` member load/leave/remove/make-leader to collect ViewModel state and call ViewModel methods only; keep dialogs/toasts/navigation in the Fragment.
4. Keep request-list behavior on the existing `fetchMembers` / `respondToRequest` paths.

**Acceptance:**
- `MembersFragment` has no direct repository calls for member list/mutations.
- Leave / remove / make-leader / reload behave as before.
- Request section still works via the same ViewModel.

**9/10:** Helps 10 later by keeping Fragment free of data access (Compose-ready state).

---

### Task 2 — Move `Personal.serialize` out of the Room model into the personals repository
**Roadmap:** 1 (data-layer cleanup), also 9 (KMP: model free of upload/network helpers)

**Problem:** `Personal` (Room `@Entity`) owns upload JSON serialization (`NetworkUtils`, `FileUtils`, `addDocumentOrigin`). Only `PersonalsRepositoryImpl.uploadPersonalDocument` calls it.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/model/Personal.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`

**Existing symbols (verified):**
- `Personal.serialize(personal, customDeviceName)`
- `PersonalsRepositoryImpl.uploadPersonalDocument` (calls `Personal.serialize(..., deviceNameProvider.getCustomDeviceName())`)
- `DeviceNameProvider.getCustomDeviceName` (already injected)

**Do not touch:** `PersonalsRepository.kt` interface unless a private helper forces it; UI; upload configs

**Steps:**
1. Move serialize logic into `PersonalsRepositoryImpl` as a private/internal function (same JSON fields/order).
2. Point `uploadPersonalDocument` at the repository helper; keep using `deviceNameProvider.getCustomDeviceName()`.
3. Delete `Personal` companion `serialize` and drop now-unused model imports (`JsonObject`, `NetworkUtils`, `FileUtils`, `addDocumentOrigin`, etc.).
4. Update `PersonalsRepositoryImplTest` only if it asserts on `Personal.serialize` (same task if needed; file not shared elsewhere).

**Acceptance:**
- `Personal` has no serialize/network imports.
- Personal upload payload unchanged.
- No remaining `Personal.serialize` call sites.

**9/10:** Direct KMP model hygiene (entity stays platform-free).

---

### Task 3 — Remove Android `Context` / `R.string` from Life seeding path
**Roadmap:** 1 + 3, also 9/10

**Problem:** `MyLife.defaultItems` imports `R.string`. `LifeViewModel` injects `@ApplicationContext` only to pass `context::getString` into `lifeRepository.getMyLifeByUserId(...)`.

**Files (4):**
- `app/src/main/java/org/ole/planet/myplanet/model/MyLife.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`

**Existing symbols (verified):**
- `MyLife.defaultItems(userId, resolveLabel)`
- `MyLife.defaultItemPairs` (private)
- `LifeRepository.getMyLifeByUserId`, `seedMyLifeIfEmpty`, `getMyLifeForDashboard`
- `LifeViewModel.loadMyLifeList`, `resolveUserId`

**Do not touch:** dashboard callers of `getMyLifeForDashboard` beyond keeping the existing `seedBase: List<MyLife>` contract working

**Steps:**
1. Replace `MyLife.defaultItems` resource-id pairing with platform-free seed keys (imageId + stable key), or move default catalog into `LifeRepositoryImpl` without `R`.
2. Add a repository API that accepts already-resolved label strings **or** resolves labels via a non-Android function type supplied by the ViewModel without holding `Context` long-term (e.g. ViewModel maps keys → strings once using a string provider if already available, or keep resolution at the call edge without `@ApplicationContext` field if a better existing injection exists). Prefer: repository seeds from imageId catalog; ViewModel supplies `Map`/`List` of resolved titles built without storing Context (use `stringResource` only if already in UI—here keep resolution in VM via Android resources **without** repository/`MyLife` importing `R`).
3. Concrete preferred shape: `LifeRepository.getMyLifeByUserId(userId)` seeds internally using imageId defaults + title strings passed in as `List<Pair<String /*imageId*/, String /*title*/>>` from the ViewModel; ViewModel builds that list using `R.string` locally and drops `@ApplicationContext` if possible by using application resources through a one-shot resolve in `loadMyLifeList`.
4. Remove `R` import from `MyLife.kt`. Keep seed/insert behavior identical.

**Acceptance:**
- `MyLife` has zero `R` / Android resource imports.
- `LifeViewModel` no longer needs Context solely for default item titles (or Context usage is eliminated).
- Empty-user first load still seeds the same seven tiles.

**9/10:** Entity + repository closer to pure Kotlin; labels stay at UI edge.

---

### Task 4 — Collapse notification enrichment fan-out into `NotificationsRepository`
**Roadmap:** 1 + 3, also 7 (perf: one coordinated fetch)

**Problem:** `NotificationsViewModel.loadNotifications` pulls payloads then fans out task-id/title and join-request detail queries. That cross-entity orchestration belongs in the repository; the ViewModel should format/group only.

**Files (3):**
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`

**Existing symbols (verified):**
- `NotificationsViewModel.loadNotifications`, `formatNotification`, `parseTaskDate`
- `NotificationsRepository.getNotifications`, `getTaskTeamNamesByTaskIds`, `getTaskTeamNamesByTaskTitles`, `getJoinRequestDetailsBatch`, `getJoinRequestDetails`, `getUnreadCount`, `resolveType`
- Impl already owns `teamTaskDao` / `teamsRepository` / `voicesRepository` wiring

**Do not touch:** `TeamTaskDao.kt`, `VoicesRepository*`, `NotificationDao.kt`, Fragments

**Steps:**
1. Add a repository DTO + method (e.g. `NotificationLoadResult` / `loadNotificationPage(userId, filter, isAdmin)`) that returns payloads + unread count + task team-name map + join-request detail map (same batching `loadNotifications` already does).
2. Move the task/join partitioning and parallel `async` batch calls from the ViewModel into `NotificationsRepositoryImpl`.
3. Slim `NotificationsViewModel.loadNotifications` to one repository call, then map through existing `formatNotification` (Context/`R.string` formatting stays in VM).
4. Keep mark-read/delete/group UI state in the ViewModel.

**Acceptance:**
- ViewModel no longer calls the four enrichment APIs directly from `loadNotifications`.
- Notification list text/types/unread counts unchanged.
- No DAO file changes.

**9/10:** ViewModel stays presentation-focused (Compose-ready); data assembly is repository-owned.

---

### Task 5 — Move `MyLibrary.serialize` upload mapping into `ResourcesRepository`
**Roadmap:** 1 + 5 (upload workflow), also 9

**Problem:** `MyLibrary.serialize` lives on the entity companion and reaches `MainApplication.Companion.context` via `NetworkUtils.getCustomDeviceName(context)`. Only `UploadConfigs.getResourcesConfig` uses it.

**Files (4):**
- `app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`

**Existing symbols (verified):**
- `MyLibrary.serialize(personal: MyLibrary, user: UserEntity?)`
- `UploadConfigs.getResourcesConfig` serializer lambda
- `ResourcesRepository.markResourceUploaded` / `getPendingResourceUploads`
- Existing `DeviceNameProvider` pattern (as used by personals) — inject/use the same provider in `ResourcesRepositoryImpl` rather than `MainApplication.context`

**Do not touch:** `DeviceNameProvider.kt` unless absolutely required; other upload configs; `MyLibrary.serializeResource()` (different API, still used by achievements UI)

**Steps:**
1. Add `ResourcesRepository.serializeForUpload(library: MyLibrary, user: UserEntity?): JsonObject` (name flexible) implementing the current companion field set.
2. Implement with `DeviceNameProvider` (or `sharedPrefManager.getCustomDeviceName()` already available in related services) — **no** `MainApplication.context`.
3. Point `UploadConfigs.getResourcesConfig` serializer at the repository method.
4. Remove companion `serialize` and the `MainApplication.context` import usage tied to it from `MyLibrary.kt` if nothing else needs it.

**Acceptance:**
- Resource upload serialization path unchanged functionally.
- No `MyLibrary.serialize` call sites left.
- Entity companion no longer depends on Application context for upload JSON.

**9/10:** Clears a hard Android context leak from a core model used by sync/upload.

---

### Task 6 — Split dictionary file I/O from pure seed parsing in `DictionaryRepository`
**Roadmap:** 1, also 9

**Problem:** `DictionaryRepositoryImpl.insertDictionaryData` mixes Android `Context` file access, JSON parse, entity mapping, and DAO insert in one method.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`

**Existing symbols (verified):**
- `DictionaryRepository.insertDictionaryData`, `count`, `findByWord`
- `DictionaryRepositoryImpl` uses `FileUtils.checkFileExist`, `FileUtils.getStringFromFile`, `FileUtils.getSDPathFromUrl`, `Constants.DICTIONARY_URL`, `dictionaryDao.insertAll`
- `DictionaryLoad` sealed results
- `DictionaryViewModel.loadDictionary` (caller; do not edit unless signature break forces it — prefer keeping `insertDictionaryData()` signature)

**Do not touch:** `DictionaryActivity.kt` (open-PR locked), `RepositoryModule.kt`, `DictionaryDao.kt`

**Steps:**
1. Extract a pure function (same file/internal) that maps dictionary `JsonArray` → `List<DictionaryEntity>` (including the existing `antonoym` key typo behavior).
2. Keep file existence/read behind a thin private method; `insertDictionaryData()` orchestrates: missing file → `FileMissing`; count>0 → `AlreadyPopulated`; else parse+`insertAll`.
3. Optionally expose `seedFromJson(json: String): DictionaryLoad` on the interface for tests/KMP without requiring Context (implementation can call the pure parser). Only add if it stays within line budget and is used (tests OK).
4. Update `DictionaryRepositoryImplTest` in-task if present assertions need the pure helper.

**Acceptance:**
- Seeding behavior and `DictionaryLoad` outcomes unchanged for `DictionaryViewModel`.
- Parse/mapping has no Android types.
- Still no new Gradle dependencies.

**9/10:** Pure seed core is platform-free.

---

### Task 7 — DAO projection for enterprise report CSV export
**Roadmap:** 1 + 7 (DAO/perf), also 9

**Problem:** `EnterprisesRepositoryImpl.exportReportsAsCsv` loads full `MyTeam` rows via `teamDao.getNonArchivedReportsByTeamId` (`SELECT *`) but only needs a small finance-report field set.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`

**Existing symbols (verified):**
- `TeamDao.getNonArchivedReportsByTeamId`, `observeNonArchivedReportsByTeamId`
- `EnterprisesRepositoryImpl.exportReportsAsCsv`
- CSV fields used: `startDate`, `endDate`, `createdDate`, `updatedDate`, `beginningBalance`, `sales`, `otherIncome`, `wages`, `otherExpenses`

**Do not touch:** `MyTeam.kt` (locked), UI adapters still needing full entities for list UI, `observeNonArchivedReportsByTeamId` consumers

**Steps:**
1. Add a small projection data class (DAO file or adjacent) with the CSV columns above (+ `_id` if useful).
2. Add `TeamDao` query selecting only those columns with the same non-archived report filter/order as `getNonArchivedReportsByTeamId`.
3. Switch `exportReportsAsCsv` to the projection query; leave Flow UI path on full entities.
4. Keep CSV text format identical.

**Acceptance:**
- CSV output unchanged for the same underlying rows.
- Export path no longer materializes full `MyTeam` graphs.
- List/observe report UI untouched.

**9/10:** Narrower DB read contract; less Android model surface in export logic.

---

### Task 8 — Peel WorkManager Android types out of `SyncRepositoryImpl` upload entry points
**Roadmap:** 5 (sync/upload consolidation), also 4 (DI cleanup local to sync repo) + 9

**Problem:** `SyncRepositoryImpl.uploadLoginData` / `uploadBulkData` construct `WorkManager` / `OneTimeWorkRequest` / `WorkInfo` directly inside the repository, binding the sync repository to AndroidX Work.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`

**Existing symbols (verified):**
- `SyncRepository.uploadLoginData`, `uploadBulkData`, `SyncUiState`
- `SyncRepositoryImpl.enqueueUserDataUpload`, `mapWorkInfoToState`
- `UserDataWorker.KEY_UPLOAD_TYPE`, `UPLOAD_TYPE_LOGIN`, `UPLOAD_TYPE_BULK`, `KEY_SUCCESS_MESSAGE`

**Do not touch:** `UserDataWorker.kt`, `ServiceModule.kt`, `RepositoryModule.kt`, shelf sync logic beyond leaving it alone

**Steps:**
1. Introduce a tiny internal abstraction in these files (interface + default Android implementation constructed inside `SyncRepositoryImpl`, or a package-private collaborator created in the constructor body) that can enqueue unique work and expose a `Flow` of success/loading/error states.
2. Move WorkManager-specific code into that collaborator only.
3. Keep `SyncRepository` public API (`Flow<SyncUiState>`) unchanged.
4. Do **not** add Gradle deps; do **not** edit DI modules — manual construction is fine to avoid locked `RepositoryModule`/`ServiceModule`.

**Acceptance:**
- Login/bulk upload still enqueue the same unique work names/types.
- `SyncRepositoryImpl` methods above no longer reference WorkManager types at the orchestration site (only the collaborator does).
- Shelf/process methods unchanged.

**9/10:** Shrinks Android surface of sync repository entry points toward a platform-free core.

---

### Task 9 — Stop achievement UI from loading the entire library table
**Roadmap:** 1 + 3 + 7, also 10

**Problem:** `AchievementViewModel.getAllLibraries()` proxies `resourcesRepository.getAllLibraries()` → `myLibraryDao.getAll()` (`SELECT *` on all resources). `EditAchievementFragment.showResourceListDialog` only needs picker rows; public library list API already exists.

**Files (1):**
- `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`

**Existing symbols (verified):**
- `AchievementViewModel.getAllLibraries`
- `ResourcesRepository.getAllLibraries`, `getPublicLibraryItems`
- Caller: `EditAchievementFragment.showResourceListDialog` → `viewModel.getAllLibraries()` (caller file not modified if method name/return type stays `List<MyLibrary>`)

**Do not touch:** `EditAchievementFragment.kt` unless signature must change; `ResourcesRepository*`; `MyLibraryDao` (owned conceptually by other tasks’ avoid-list — here unused)

**Steps:**
1. Change `getAllLibraries` implementation to call `resourcesRepository.getPublicLibraryItems()` (or rename method to `getLibraryPickerItems` **only if** you also update the single caller — prefer keeping the method name to stay 1-file).
2. Confirm return type remains `List<MyLibrary>` so `serializeResource()` usage in the fragment keeps compiling without edits.
3. Leave achievement load/save paths untouched.

**Acceptance:**
- Achievement resource picker no longer triggers full-table `getAllLibraries`.
- Picker still shows selectable libraries and can serialize selected resources.
- No repository interface changes required.

**9/10:** Reduces accidental cross-feature full-table reads from a non-resources VM.

---

### Task 10 — Introduce `CommunityServicesViewModel` for team-link data access
**Roadmap:** 3, also 1

**Problem:** `CommunityServicesFragment` launches coroutines and calls `teamsRepository.getTeamLinks()` / `teamsRepository.isMember(...)` directly for service buttons.

**Files (2):**
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt` **(new)**

**Existing symbols (verified):**
- `CommunityServicesFragment.onViewCreated` link load; `setRecyclerView`
- `teamsRepository.getTeamLinks()` (`TeamsRepository`)
- `teamsRepository.isMember(userId, teamId)` (also on `TeamsMembersRepository.isMember`)
- `CommunityServicesRoute.resolve`
- `BaseTeamFragment.teamsRepository`, `user`

**Do not touch:** `TeamsRepository.kt` / Impl (locked), `CommunityServicesRoute.kt` unless required, DI modules (Hilt `@HiltViewModel` needs no `RepositoryModule` change)

**Steps:**
1. Create `@HiltViewModel CommunityServicesViewModel` injecting `TeamsRepository` and/or `TeamsMembersRepository` + `UserRepository` if needed for user id.
2. Expose `loadLinks():` state (`List<MyTeam>`) and `isMember(teamId): Boolean` (suspend/state) wrapping existing repo methods.
3. Fragment collects links state; click handler asks ViewModel for membership instead of calling repository.
4. Keep routing, WebView intents, markdown rendering in the Fragment.

**Acceptance:**
- Fragment has no direct repository calls.
- Service links empty/non-empty UI and team/external routes behave as before.
- New ViewModel only; no new Gradle deps; no unused code.

**9/10:** UI data access hoisted for future Compose port of community services.

---

## Self-check

| Rule | Status |
|------|--------|
| R1 exactly 10 independent tasks | Yes |
| R2 no file in >1 task | Yes (24 unique paths; T10 new file exclusive) |
| R3 open PRs listed; locked files avoided | Yes (38 PRs; candidates intersecting locks dropped) |
| R4 paths/symbols verified in tree | Yes |
| R5 size/deps/TODO discipline | Tasks scoped to ≤5 files and small behavior-preserving moves |
| R6 plan only, no implementation in this run | Yes |
| Roadmap tags present | Yes; 9/10 called out where true |

**Suggested merge order (optional, not required):** T9 → T2 → T7 → T1 → T10 → T6 → T3 → T4 → T5 → T8
