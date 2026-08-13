# Refactor Round — 10 Low-Hanging-Fruit Tasks

Scope: reinforce repository boundaries between layers, tighten repository interfaces, move data functions out of UI/service into repositories. Keeps DI, data layers, DiffUtil/ListAdapter, ViewModels, threading/dispatchers, and long-running observers in mind. Each task is deliberately tiny and isolated so it reviews cleanly and won't collide with siblings in this merge round.

Avoid overlap guidance: each task touches a distinct file set. Do one PR per task; do not combine tasks that touch the same constructor or the same repository interface in the same round.

---

## Task 1 — Move DictionaryActivity's direct DAO access behind a repository

**Why:** `ui/dictionary/DictionaryActivity.kt` `@Inject`s `DictionaryDao` directly and calls `dictionaryDao.count()`, `insertAll()`, `findByWord()` from the Activity. The data layer rule says UI talks to repositories, never DAOs — DictionaryActivity is the only place left that breaks this.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`
- new: `repository/DictionaryRepository.kt` (interface)
- new: `repository/DictionaryRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt` (bind)
- `app/src/main/java/org/ole/planet/myplanet/di/RoomModule.kt` (already provides the DAO — reuse)

**Do:**
1. Create `DictionaryRepository` with exactly the 3 methods the Activity uses: `count()`, `insertAll(List<DictionaryEntity>)`, `findByWord(String): DictionaryEntity?`.
2. Impl injects `DictionaryDao` + `DispatcherProvider`, wraps calls in `withContext(dispatcherProvider.io)`.
3. Activity injects `DictionaryRepository` instead of `DictionaryDao`; drop the local `withContext(dispatcherProvider.io)` wrappers (now inside the repo).
4. Bind in `RepositoryModule`.

**Boundary check:** no other layer injects `DictionaryDao`. Verify with `grep -rn "DictionaryDao" app/src/main`.

**Granularity:** one repo (3 methods), one Activity. No interface changes elsewhere.

---

## Task 2 — Remove the unused ApiInterface dependency from UploadToShelfService

**Why:** `services/UploadToShelfService.kt` constructor-injects `apiInterface: ApiInterface` but never calls it (grep shows only the constructor param). It leaks the network client into a service that already goes through `userSyncRepository` / `healthRepository`. Dead coupling.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`
- `app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt` (`provideUploadToShelfService`)

**Do:**
1. Delete the `apiInterface` constructor param and its `import org.ole.planet.myplanet.data.api.ApiInterface`.
2. Drop the matching argument in `ServiceModule.provideUploadToShelfService`.

**Boundary check:** confirms UploadToShelfService only depends on repositories + DispatcherProvider + scope — pure orchestration.

**Granularity:** ~5 lines deleted across 2 files. No behavior change.

---

## Task 3 — Push direct apiInterface.uploadResource calls in UploadManager into UploadRepository

**Why:** `services/UploadManager.kt` still calls `apiInterface.uploadResource(...)` directly at lines 371 and 457 — it injects `ApiInterface` alongside repositories. The other upload network calls (`postUpload`, `putUpload`, `fetchExistingDoc`, `uploadAttachment`) already live on `UploadRepository`. This is the last two raw network calls leaking past the repository boundary in the upload path.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepository.kt` (interface — add method)
- `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt`

**Do:**
1. Add `suspend fun uploadResource(mimeType: String, rev: String?, url: String, body: RequestBody): Response<JsonObject>` to `UploadRepository` (signature mirrors the existing `uploadAttachment` helper shape).
2. Impl delegates to its `apiInterface` (it already injects it) using `FileUploader.getHeaderMap`.
3. UploadManager calls `uploadRepository.uploadResource(...)` at the two sites; remove `apiInterface` field + import from UploadManager if it becomes unused.

**Boundary check:** after the change `grep -n "apiInterface" UploadManager.kt` should return nothing.

**Granularity:** one new interface method, one Impl method, two call sites. Keep the existing `UploadRepository.uploadAttachment` untouched to avoid scope creep.

---

## Task 4 — Move apiInterface.putDoc / postDoc calls in PhotoUploader and AchievementUploader into their repositories

**Why:** `services/upload/PhotoUploader.kt` (line 43 `apiInterface.postDoc`) and `services/upload/AchievementUploader.kt` (line 33 `apiInterface.putDoc`, line 56 `apiInterface.uploadResource`) inject `ApiInterface` directly. These are type-specific uploaders that should delegate to a repository method, not the network client.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt`
- `app/src/main/java/org/ole/planet/myplanet/services/upload/AchievementUploader.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepository.kt` (+ Impl)

**Do:**
1. Add `suspend fun postDoc(url: String, jsonObject: JsonObject): Response<JsonObject>` and `suspend fun putDoc(url: String, contentType: String, body: JsonObject): Response<JsonObject>` to `UploadRepository` (these are generic doc ops the upload path needs; reuse for both uploaders).
2. For `AchievementUploader.uploadResource`, reuse the method added in Task 3 if that PR lands first; otherwise add a thin wrapper.
3. PhotoUploader / AchievementUploader drop their `apiInterface` field and call the repository.

**Boundary check:** `grep -rn "ApiInterface" app/src/main/java/org/ole/planet/myplanet/services/upload/` returns empty.

**Conflict note:** this task touches `UploadRepository` interface like Task 3. Sequence them — do Task 3 first, then Task 4 reuses its `uploadResource`. Don't run both in parallel.

---

## Task 5 — Stop FeedbackFragment from constructing data objects inline; let the repository own it

**Why:** `ui/feedback/FeedbackFragment.kt` calls `feedbackRepository.createFeedback(user, urgent, type, message, item, state)` (synchronous, builds a `Feedback`) then separately `feedbackRepository.saveFeedback(feedback)` in a `viewLifecycleOwner.lifecycleScope.launch`. The fragment is also a `@Inject` site for `feedbackRepository` + `userSessionManager`. The two-step "build then save" leaks entity-construction knowledge into the UI and splits the transaction across the lifecycle boundary.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt` (interface)
- `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackFragment.kt`

**Do:**
1. Add `suspend fun createAndSaveFeedback(user, urgent, type, message, item, state): Feedback` to `FeedbackRepository`.
2. Impl calls the existing `createFeedback` then `saveFeedback` in one `suspend` function (single coroutine hop).
3. Fragment calls only `feedbackRepository.createAndSaveFeedback(...)` inside its existing `viewLifecycleOwner.lifecycleScope.launch`; remove the local `createFeedback` call.
4. Keep the old `createFeedback` on the interface if other callers exist (check first with grep); if FeedbackFragment was the only caller, leave it as an internal helper.

**Boundary check:** fragment no longer imports `Feedback` model; only the repo builds it.

**Granularity:** one new suspend method, one fragment call-site simplified. No ViewModel introduced (keep small).

---

## Task 6 — Move CommunityServicesFragment's team-link data fetch into the existing flow / a thin repository method

**Why:** `ui/community/CommunityServicesFragment.kt` has no ViewModel and runs two `viewLifecycleOwner.lifecycleScope.launch` blocks that call `teamsRepository.getTeamLinks()` and `teamsRepository.isMember(...)` directly, then manipulates views. The fragment also does inline `Intent` + `Bundle` construction for navigation. This is a cross-feature data leak: a Community fragment depends on `TeamsRepository`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt` (+ Impl) — only if a combined read is cleaner; otherwise leave as-is.

**Do (minimal):**
1. Extract the link-loading coroutine into a single `loadTeamLinks()` suspend helper owned by the fragment that returns `List<MyTeam>`; the fragment still launches it but the data fetch is one repo call.
2. Move the `isMember` check behind the same helper so the fragment does one `launch` not two.
3. Do NOT introduce a CommunityViewModel in this task (that's a larger change) — just collapse two launches into one and remove the second `viewLifecycleOwner.lifecycleScope.launch` nesting.

**Boundary check:** confirm `CommunityServicesFragment` only injects `TeamsRepository` (already true) — flag in PR description that a future task should route community→teams through a Community read-model, but that's out of scope here.

**Granularity:** ~30 lines of fragment refactor, zero interface changes. The point of this task is to surface the leak, not fix the architecture.

---

## Task 7 — Collapse the two-step team-membership check in CommunityServicesFragment into one repository method

**Why:** Follow-on to Task 6 but kept separate so the PRs don't touch the same lines. The fragment calls `teamsRepository.isMember(user?.id, teamId)` inside a nested `viewLifecycleOwner.lifecycleScope.launch` triggered from an `setOnClickListener`. That's a per-click coroutine hop touching the repository — a classic leak of async data access into a click handler.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesFragment.kt`

**Do:**
1. Add `suspend fun isMemberWithTeamId(userId: String?, rawRoute: String): Boolean` to `TeamsRepository` that parses the route and checks membership in one suspend call (route parsing currently lives in the fragment).
2. Fragment's click listener calls this single method; the route-parsing (`segments[3]`) moves into the repo where it belongs.
3. Keep the public URL branch in the fragment (that's pure navigation, not data).

**Boundary check:** fragment no longer parses team routes; that's a data concern now owned by `TeamsRepository`.

**Conflict note:** touches `CommunityServicesFragment.kt` like Task 6 — run them in sequence, not parallel.

---

## Task 8 — Move file-preview IO out of InlineResourceAdapter into a small preview helper owned by ResourcesRepository

**Why:** `ui/courses/InlineResourceAdapter.kt` is a RecyclerView adapter doing four `withContext(dispatcherProvider.io)` blocks (PDF render, audio metadata, CSV read, text read — lines 248, 281, 305, 337). Adapters should not own IO or inject `DispatcherProvider`. It also keeps its own `bitmapCache` / `textCache`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt` (+ Impl) — add preview methods

**Do (keep it small):**
1. Add `suspend fun readFilePreview(file: File): String?` (covers CSV + text, sized by extension at call site) and `suspend fun renderPdfFirstPage(file: File): Bitmap?` and `suspend fun audioDuration(file: File): String` to `ResourcesRepository`.
2. Impl wraps the exact IO from the adapter in `withContext(dispatcherProvider.io)`.
3. Adapter injects `ResourcesRepository` (not `DispatcherProvider`); its four preview methods become one-line delegates. Keep the caches in the adapter (they're view-recycling concerns).

**Boundary check:** adapter no longer imports `DispatcherProvider`, `PdfRenderer`, `MediaMetadataRetriever`, or `CSVReader` — all moved to the repo.

**Granularity:** 3 new repo methods (small, pure IO), adapter shrinks. No ViewModel, no layout changes.

---

## Task 9 — Replace the raw DiffUtil.calculateDiff call in CoursesPagerAdapter with DiffUtils.itemCallback / submitList

**Why:** `ui/courses/CoursesPagerAdapter.kt` uses `DiffUtil.calculateDiff` directly (one of only two files that still does). The project has `utils/DiffUtils.kt` with `itemCallback` / `standardItemCallback` and the convention is `ListAdapter` + `submitList`. This is a low-risk consistency fix flagged by the DiffUtil review.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesPagerAdapter.kt`

**Do:**
1. If `CoursesPagerAdapter` extends `RecyclerView.Adapter`, convert to `ListAdapter<CourseStep, …>` (or whatever its item type is) using `DiffUtils.itemCallback` / `DiffUtils.standardItemCallback`.
2. Replace the manual `calculateDiff` + `dispatchUpdatesTo` site with `submitList(newList)`.
3. Remove now-dead `DiffUtil` import; ensure `DiffUtils` import is present.

**Boundary check:** `grep -rn "calculateDiff\|DiffUtil.calculateDiff" app/src/main/java/.../ui/` should shrink by one.

**Conflict note:** `TeamPagerAdapter.kt` is the other file with the same pattern. Do that as a separate sibling PR (same shape) — but don't bundle them; one-pager-per-PR keeps review light. If only one fits this round, pick `CoursesPagerAdapter`.

**Granularity:** single adapter file, no interface or DI changes.

---

## Task 10 — Tighten NotificationsRepository's cross-feature surface: extract task/team join-request reads behind narrower methods

**Why:** `repository/NotificationsRepositoryImpl.kt` injects `Lazy<UserRepository>`, `Lazy<TeamsRepository>`, `VoicesRepository`, plus 4 DAOs — the most cross-feature-coupled repo in the codebase (16 repository-symbol hits). The interface exposes `getJoinRequestDetails`, `getTaskTeamNamesByTaskIds`, `getJoinRequestTeamId`, `getTaskTeamName`, `getTaskTeamNamesByTaskTitles`, `getTeamNotifications` — all of which reach into team/task data to decorate notifications. That's a notifications→teams leak.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` (interface — narrow)
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt` (move the team-name resolution here)

**Do (minimal, do not over-engineer):**
1. Move `getTaskTeamName(taskTitle)` / `getTaskTeamNamesByTaskTitles(...)` / `getTaskTeamNamesByTaskIds(...)` from `NotificationsRepository` to `TeamsRepository` (these are team-name lookups by team/task id — they belong to teams).
2. Notifications callers (`NotificationsViewModel` / `NotificationsFragment`) inject `TeamsRepository` for these lookups instead of going through Notifications.
3. Leave `getJoinRequestDetails` / `getTeamNotifications` on Notifications for now (they genuinely cross-cut) — flag as a future split, out of scope.

**Boundary check:** `NotificationsRepositoryImpl` no longer needs `TeamsRepository` for task-team-name resolution; the `Lazy<TeamsRepository>` may be removable — check and remove if unused after the move.

**Conflict note:** touches `TeamsRepository` interface and `NotificationsFragment`/`NotificationsViewModel` — confirm none of the other tasks in this round touch those files. This is the largest task; if the round gets crowded, defer it.

---

## Round sequencing (to avoid merge conflicts)

Run in this order, one PR per task, max ~10 PRs/round:

1. Task 2 (delete dead `ApiInterface` in UploadToShelfService) — isolated, do first.
2. Task 1 (DictionaryRepository) — new files, low collision.
3. Task 3 (`uploadResource` on UploadRepository) — touches `UploadRepository` interface.
4. Task 4 (Photo/Achievement uploaders) — depends on Task 3's interface; touches same interface, so **after** Task 3.
5. Task 5 (Feedback create+save) — isolated fragment + repo.
6. Task 6 (CommunityServicesFragment: collapse launches) — touches the fragment.
7. Task 7 (CommunityServicesFragment: route parsing → repo) — touches the same fragment + `TeamsRepository`; **after** Task 6.
8. Task 8 (InlineResourceAdapter preview IO → repo) — isolated adapter + `ResourcesRepository`.
9. Task 9 (CoursesPagerAdapter → ListAdapter + `DiffUtils.itemCallback`) — isolated adapter.
10. Task 10 (narrow NotificationsRepository) — touches `TeamsRepository` interface like Task 7; **after** Task 7.

Parallel-safe groups (no shared files): {1, 2, 5, 8, 9}. The rest are sequential per the notes above.

---

## What this round deliberately does NOT do

- No Compose migration (Roadmap item 6 — too big for a review-light round).
- No new ViewModels introduced (Task 6 explicitly defers a CommunityViewModel).
- No `TeamsRepositoryImpl` splitting (it's 1437 lines but that's a structural refactor, not a low-hanging fruit).
- No global navigation rework (Roadmap item 2).
- No removal of the tracked `gradle.properties` secrets (security remediation — separate, sensitive round).
