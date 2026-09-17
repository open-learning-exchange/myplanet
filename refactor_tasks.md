# myPlanet merged refactor backlog

Rating = 0.35·evidence + 0.35·impact + 0.30·risk-adjusted feasibility, scored 1–100.
Every task below was verified against the working tree at `c33390b88` (master). `from:` records every agent that proposed it.

---

## 96 — Team task badge is computed once globally instead of per team
**from:** devin p2#2 · **files:** `repository/NotificationsRepositoryImpl.kt` (`getTeamNotifications`)
**verified:** `val hasTask = tasks.isNotEmpty()` sits *outside* the `for (teamId in teamIds)` loop and the same boolean is passed into every `TeamNotificationInfo(hasTask, hasChat)`. Confirmed `TeamTask.teamId` exists and is indexed (`model/TeamTask.kt:22`, `@Index("teamId")`), so the grouping fix needs no DAO change.
**do:** build `val taskTeamIds = tasks.mapNotNull { it.teamId }.toSet()` once, then `val hasTask = teamId in taskTeamIds` inside the loop.
**risk:** ~6 lines, one file. This is a live user-visible defect: any team shows the task badge whenever *any* team has a task due.

---

## 92 — `MyLibrary.insertMyLibrary` duplicates attachments on every re-sync
**from:** devin p1#1 · **files:** `model/MyLibrary.kt` (`insertMyLibrary`, ~184-270)
**verified:** line 210 seeds `attachmentList` from `this.attachments?.toMutableList()`, line 223 appends unconditionally, line 234 writes it back — so re-syncing the same document grows the array without bound. Separately `params.spm.getCouchdbUrl()` (line 226) is re-read from SharedPreferences per attachment.
**do:** hoist the base URL out of the `entrySet().forEach`; capture existing attachment names into a set and skip already-present keys before `add`.
**risk:** sync-path model code — verify attachment identity is keyed on `name`, not the random UUID. Unbounded local DB growth makes this worth the care.

---

## 91 — `LifeAdapter` sets contentDescription inside a touch listener
**from:** devin p1#7 · **files:** `ui/life/LifeAdapter.kt` (`onBindViewHolder`)
**verified:** line 67 assigns `contentDescription` inside `setOnTouchListener`, which fires on every MotionEvent; line 74 does the same inside `setOnClickListener`. Both strings depend only on `myLife.title`, fixed at bind time.
**do:** assign both contentDescriptions once in `onBindViewHolder` next to the existing `imageView.contentDescription`; drop them from the listeners.
**risk:** trivial. Also fixes a real accessibility defect — TalkBack has no drag label until the user first touches the control.

---

## 89 — `SyncTimeLogger`: dead accumulator, racing lists, double-summed report
**from:** claude p1#1 + codex p1#6 + devin p1#8 · **files:** `utils/SyncTimeLogger.kt`
**verified:** `detailedLogs` appears exactly 3× in the file — declared (38), cleared (73), appended (195) — and is never read, so all 12 `logDetail` call sites retain strings nothing consumes. `getOrPut(key) { mutableListOf() }` at 167/184/195 is non-atomic on a `ConcurrentHashMap` and yields an unsynchronised `ArrayList`, appended from parallel sync coroutines. `generateSummary` sums each group for `sortedByDescending` (268, 288) and again while rendering.
**do:** delete `detailedLogs` and its clear/append; back the two remaining maps with `Collections.synchronizedList` via `computeIfAbsent`; compute per-endpoint and per-model aggregates once and render from them.
**risk:** three concerns in one file — they conflict if split across PRs, so land together. Keep `logDetail`'s signature (12 callers). `extractProcessName`'s `split("/")` can also be cheapened, but `substringAfterLast` is **not** equivalent — the current code walks backwards skipping empty and `?`-prefixed segments.

---

## 88 — Sync resource cleanup re-filters a list that is already clean
**from:** claude p1#2 · **files:** `services/sync/SyncManager.kt` (`resourceTransactionSync`)
**verified:** `newIds` is declared `MutableList<String?>` (294) but only ever receives `validIds` (376), which is already `savedIds.filter { it.isNotBlank() }`. The cleanup block re-walks the whole list at 411, and its guard `validNewIds.size == newIds.size` (414) is therefore always true. The item count logged at 420 is `newIds.size - validNewIds.size` — always 0.
**do:** type `newIds` as `MutableList<String>`, drop `validNewIds`, reduce the guard to `newIds.isNotEmpty()`, and log `newIds.size`.
**risk:** low; keep the `hadBatchFailure` branch untouched. Saves a full copy of every resource id on servers holding tens of thousands.

---

## 87 — `isAlreadyShared` loads every matching news row to produce a boolean
**from:** claude p2#9 · **files:** `data/room/dao/NewsDao.kt`, `repository/VoicesRepositoryImpl.kt`
**verified:** `NewsDao:71-72` is `SELECT * FROM news WHERE newsId = :chatId`; `VoicesRepositoryImpl:76-79` hydrates every row (message, images, viewIn blobs through `Converters`) just to run `.any { ... }`. Confirmed `getByNewsId` has no other caller in `app/src/main`, so it can be replaced outright.
**do:** replace with `SELECT EXISTS(SELECT 1 FROM news WHERE newsId = :chatId AND viewIn LIKE :viewInPattern ESCAPE '\')`; build the pattern in the repository, escaping `\`, `%` and `_`.
**risk:** SQLite `LIKE` is ASCII-case-insensitive, matching the current `ignoreCase = true`. No schema/index change, so no `AppDatabase` version bump.

---

## 86 — `ConfigurationsRepository.checkVersion` takes a service instance as a parameter
**from:** claude p2#1 · **files:** `repository/ConfigurationsRepository.kt`, `…Impl.kt`, `ui/sync/SyncActivity.kt`, `ui/sync/LoginActivity.kt`, `services/AutoSyncWorker.kt`
**verified:** interface line 15 is `fun checkVersion(callback: CheckVersionCallback, spm: SharedPrefManager)`; the impl already injects its own `SharedPrefManager` and still receives `spm`, using it at line 487. Three call sites confirmed: `SyncActivity:233`, `LoginActivity:183`, `AutoSyncWorker:69`.
**do:** drop the parameter from the interface and impl, use the injected field throughout, update the three call sites and the three test call sites.
**risk:** low and mechanical; removes a `services` import from a repository interface. Two `SharedPrefManager` instances can currently reach one call.

---

## 85 — `Feedback.messageList` rebuilds on every access and can serve a stale parse
**from:** devin p1#9 · **files:** `model/Feedback.kt`
**verified:** `parsedMessages` caches the `JsonArray` but the derived `List<FeedbackReply>` is rebuilt per access, and `setMessages(JsonArray)` reassigns `messages` without invalidating `cachedMessages`.
**do:** cache the derived list alongside the raw parse and invalidate both wherever `messages` is reassigned.
**risk:** the stale-cache half is a correctness fix, not just allocation. Confirm every write path to `messages` before caching harder.

---

## 85 — Two viewer Activities reach past their ViewModel for one user id
**from:** claude p2#2 + devin p2#8 · **files:** `ui/viewer/ResourceViewerViewModel.kt`, `ResourcesExitCoordinator.kt`, `ResourceViewerActivity.kt`, `WebViewActivity.kt`
**verified:** both Activities field-inject `UserRepository` solely to hand it to `ResourcesExitCoordinator`, whose only use is `userRepository.getUserModel()?.id?.takeIf { it.isNotBlank() }`. Both already hold a `ResourceViewerViewModel`.
**do:** move the lookup into the ViewModel. Claude's variant adds a `getActiveUserId()` accessor; devin's goes further and drops `userId` from `shouldShowResourceRatingDialog` / `setRatingPrompted` entirely — prefer devin's, it removes the parameter from the seam rather than relocating it.
**risk:** low. Removes the last repository dependency from both Activities.

---

## 84 — Course cover art stats the disk on the main thread every bind
**from:** claude p1#5 · **files:** `utils/CoursesItemUtils.kt` (`bindCover`)
**verified:** line 60 runs `coverFile?.exists() == true` synchronously; `bindCover` is called from `CoursesAdapter:330`, `CoursesAdapter:378` and `TeamCoursesAdapter:45`, all bind paths. `FileExistenceCache` (5 s TTL) already exists at `FileUtils.kt:381` and is used by `EnterprisesReportsAdapter`.
**do:** hold a `FileExistenceCache` + `TimeProvider` in the object and route the check through it. Keep `bindCover`'s signature so both callers compile untouched.
**risk:** low; a cover that finishes downloading appears within the 5 s TTL or on next reload.

---

## 84 — Achievement resource picker materialises every library row
**from:** devin p2#1 · **files:** `data/room/dao/MyLibraryDao.kt`, `repository/ResourcesRepository.kt` + `Impl`, `ui/user/AchievementViewModel.kt`, `ui/user/EditAchievementFragment.kt`
**verified:** `EditAchievementFragment:413` → `AchievementViewModel:85` → `resourcesRepository.getAllLibraries()`, an unbounded `SELECT *` over ~74-property entities to populate a checkbox list that reads only `title`. Confirmed this is `getAllLibraries`'s **only** consumer (distinct from `getAllLibrariesToSync`, used by `MainApplication:396` and `SyncActivity:575`), so it can be replaced rather than added to.
**do:** add an `id,title` projection query beside the existing `ResourceTitleProjection`; refetch full entities only for the ids the user confirms.
**risk:** preserve the current unspecified row order (no `ORDER BY`) so the list looks identical.

---

## 83 — Chat search case-folds strings that are already normalised
**from:** claude p1#3 + codex p1#5 + grok p1#5 · **files:** `utils/ChatSearch.kt`
**verified:** `Utilities.normalizeText` lowercases via `str.lowercase(Locale.getDefault())` before stripping diacritics, and both query and candidates pass through it (lines 42, 49-50, 81-83, 90-91). All four comparisons still pass `ignoreCase = true` (60, 63, 97, 99), forcing the case-folding `regionMatches` path on every keystroke.
**do:** drop `ignoreCase = true` from the two `startsWith` and two `contains` calls.
**risk:** low. Grok's variant additionally replaces the four `+`-concatenated rank buckets with a single accumulation and drops the duplicate `queryParts` list — worth folding in; claude's pins the exact lines; codex's adds the mixed-case/accented regression cases.

---

## 83 — Four unused resource-open overloads on `ActivitiesRepository`
**from:** claude p2#4 · **files:** `repository/ActivitiesRepository.kt`, `…Impl.kt`
**verified:** the four declarations at lines 35-38 resolve only to `ActivitiesRepositoryImpl:196` and `:198`, both inside `getProfileActivityStats`. No caller outside the impl anywhere in `app/src/main`.
**do:** delete the four from the interface; drop `override` in the impl so they stay callable as plain members.
**risk:** very low — the test constructs the concrete class, so no test edit needed. Also lifts `UserSessionManager.KEY_RESOURCE_OPEN` out of the contract.

---

## 82 — `CoursesAdapter.removeCourses` is quadratic
**from:** codex p1#1 · **files:** `ui/courses/CoursesAdapter.kt`
**verified:** `currentList.filter { it.courseId !in courseIds }` with `courseIds: List<String>` — linear scan per item.
**do:** convert `courseIds` to a `Set` once before filtering; short-circuit the empty case through the existing `submitList` path so `onComplete` timing is unchanged.
**risk:** low; keep the completion callback firing from the `submitList` commit callback only.

---

## 82 — Diagnosis lookup and per-checkbox styling recomputed in a loop
**from:** devin p1#3 · **files:** `ui/health/HealthExaminationActivity.kt`
**verified:** `preloadCustomDiagnosis` builds `listOf(*arr)` then calls `mainList.contains(s)` inside the `conditionsMap` loop (O(n·m)); `showCheckbox` re-resolves `ContextCompat.getColorStateList`, `ContextCompat.getColor` and calls `dpToPx(8)` four times for every checkbox in `for (s in arr)`.
**do:** make `mainList` a `Set`; hoist the tint, colour and padding values above the loop.
**risk:** low, self-contained in two private methods.

---

## 81 — `getRatingsById` is a null-guard wrapper with one caller
**from:** claude p2#10 · **files:** `repository/RatingsRepository.kt`, `…Impl.kt`, `ui/resources/ResourceDetailFragment.kt`
**verified:** the impl (20-23) is nothing but `if (resourceId == null) return null` over `getRatingSummary` on the next line. Confirmed exactly one caller — `ResourceDetailFragment:268`; four other sites already call `getRatingSummary` directly.
**do:** delete both, guard the id at the call site with `val resourceId = library.resourceId ?: return@launch`.
**risk:** low; rename the existing test case to target `getRatingSummary`.

---

## 81 — Storage breakdown walks the resource tree twice
**from:** kimi p1#9 + claude p2#3 · **files:** `repository/ResourcesRepository.kt` + `Impl`, `ui/settings/StorageBreakdownViewModel.kt`
**verified:** `StorageBreakdownViewModel:94` runs `oleDir.walkTopDown().filter { it.isFile }.forEach { … }`, duplicating the identical traversal already inside `ResourcesRepositoryImpl.getOfflineResourceItems` (~860). The settings screen therefore walks the tree once for the summary and again when a category opens, and this ViewModel is the only one in `ui/` still doing its own file I/O.
**do:** prefer claude's variant — move the scan into `ResourcesRepository` behind a `getStorageBreakdown(...)` returning a small data class, and have the ViewModel call it. Kimi's variant only removes the intermediate sequence stages inside the ViewModel, which leaves the layering violation and the double walk in place.
**risk:** moderate (~120 lines, 5 files including moved tests). Keep the caller passing the `ole` path, as `getOfflineResourceItems` already does.

---

## 80 — Storage selection counters scan the list twice per emission
**from:** claude p1#8 · **files:** `ui/settings/StorageCategoryViewModel.kt`
**verified:** `checkedCount` is `items.count { it.isChecked }` and `allChecked` calls `checkedCount` then compares against `items.size` — two full scans per state read, and the fragment reads both on every emission. `toggleItemChecked` emits a new state on every checkbox tap.
**do:** make `checkedCount` a real constructor property derived from the `map` already being performed; redefine `allChecked` as a single comparison.
**risk:** low — keep both names and types so `StorageCategoryDetailFragment` compiles unchanged.

---

## 80 — `RequestsFragment` reads the session a second time for the current user
**from:** devin p2#3 · **files:** `ui/teams/members/RequestsViewModel.kt`, `RequestsFragment.kt`
**verified:** the fragment injects `UserSessionManager` only to resolve the user for `RequestsAdapter.setUser`, while `RequestsViewModel.fetchMembers` already loads `userRepository.getUserModel()` and discards everything but `user?.id`.
**do:** add `currentUser` to `RequestsUiState` (last, so positional constructors stay valid) and consume it in the existing `collectWhenStarted(viewModel.uiState)` collector.
**risk:** low. Leave `MembersUiState` and the rest of the file alone.

---

## 80 — A service reaches into `DashboardViewModel` for progress arithmetic
**from:** codex p2#1 · **files:** `services/ChallengePrompter.kt`, `ui/dashboard/DashboardViewModel.kt`
**verified:** `ChallengePrompter` imports `DashboardViewModel` (line 8), holds one (13), and calls `calculateCommunityProgress` / `calculateIndividualProgress` at 41-42 and again at 55-56 — a service depending on a UI ViewModel, inverting the layer direction.
**do:** extract the two deterministic calculations into a platform-free function/value object and have both the prompter and the ViewModel call it.
**risk:** moderate; preserve the Spanish completion check, the caps and the markdown copy exactly.

---

## 79 — Regex split and a no-op `trimIndent` on the health examination bind path
**from:** claude p1#10 · **files:** `ui/health/HealthExaminationAdapter.kt`
**verified:** line 69 derives one substring via `createdBy.split(colonRegex).dropLastWhile { it.isEmpty() }.toTypedArray().getOrNull(1)`, with `colonRegex` existing solely for it (line 176). Line 104 appends `.trimIndent()` to `getString(R.string.two_strings, …)`, and `two_strings` is `%1$s %2$s` (`values/strings.xml:1028`) — single-line, so `trimIndent` splits, inspects and rejoins to produce an identical string on every bind.
**do:** use `createdBy.substringAfter(':', "")`, delete `colonRegex`, drop the `trimIndent` at 104.
**risk:** low — leave the `trimIndent` at line 140, which formats the genuinely multi-line `vitals_format`.

---

## 79 — `UserRepositoryImpl` reads the static application context
**from:** jules p2#9 · **files:** `repository/UserRepositoryImpl.kt`
**verified:** lines 527-528 call `VersionUtils.getAndroidId(MainApplication.context)` and `NetworkUtils.getCustomDeviceName(MainApplication.context)` inside a repository.
**do:** use the injected `@ApplicationContext` (add it if absent) and drop the `MainApplication` import.
**risk:** low. Note `NetworkUtils.getCustomDeviceName` ignores its context argument entirely (see the `DeviceNameProvider` tasks) — routing it through `DeviceNameProvider` instead is the better end state.

---

## 79 — Loop-invariant JSON read in `ExamQuestion.insertCorrectChoice`
**from:** devin p1#2 · **files:** `model/ExamQuestion.kt`
**verified:** the else-branch re-evaluates `JsonUtils.getString("correctChoice", question)` on every iteration of `for (a in 0 until array.size())` although `question` is loop-invariant, and keeps assigning `correctChoiceList` instead of stopping at the first match.
**do:** hoist the lookup into a local before the loop; `break` on the first match.
**risk:** low — currently the *last* matching id wins; ids are unique, so `break` is equivalent, but confirm on your fixtures.

---

## 78 — `FeedbackRepository` exposes two internal-only builders and over-fetches for its list query
**from:** claude p2#5 · **files:** `repository/FeedbackRepository.kt` + `Impl`, `ui/feedback/FeedbackListViewModel.kt`
**verified:** `createFeedback` (line 8) and `saveFeedback` (29) are interface methods whose only callers are each other inside `createAndSaveFeedback`. `getFeedback(userModel: UserEntity?)` (24) is marked `suspend` though the body only picks a DAO `Flow`, and takes a whole `UserEntity` to read two things.
**do:** make the two builders private, and change the signature to `fun getFeedback(ownerName: String?, isManager: Boolean): Flow<List<Feedback>>` without `suspend`.
**risk:** moderate — two test files move from `coEvery` to `every`.

---

## 78 — `AddResourceFragment` injects a session manager for one lookup
**from:** devin p2#5 · **files:** `ui/resources/AddResourceViewModel.kt`, `AddResourceFragment.kt`
**verified:** the fragment injects `UserSessionManager` only to call `getUserModel()` at ~264; `AddResourceViewModel` currently injects only `PersonalsRepository`.
**do:** add `UserRepository` to the ViewModel with a `currentUser()` accessor; swap the call and delete the injection.
**risk:** low. Do not touch `AddResourceActivity.kt` — owned by an open PR.

---

## 78 — Pending-submission lookup materialises a list to take one row
**from:** codex p2#10 · **files:** `data/room/dao/SubmissionDao.kt`, `repository/SubmissionsRepositoryImpl.kt`
**verified:** `getSubmissionsByParentId(…, "pending").firstOrNull()` at lines 456 and 541. `SubmissionDao` already shows the bounded shape in `getLatestPendingByUserAndParent`.
**do:** reuse that query where its ordering matches; otherwise add one precisely ordered `LIMIT 1` query. Hydrate only the returned row.
**risk:** low-moderate — the two call sites differ in nullable user/parent semantics; compare before collapsing them onto one query.

---

## 77 — Both OkHttp clients build their own connection pool
**from:** claude p1#6 · **files:** `di/NetworkModule.kt`
**verified:** `buildOkHttpClient` constructs `ConnectionPool(MAX_REQUESTS_PER_HOST, 5, TimeUnit.MINUTES)` at line 81 and is called twice — for `@StandardHttpClient` (101) and `@ReachabilityHttpClient` (113). Both talk to the same Planet host.
**do:** hoist the pool to a single instance shared by both clients.
**risk:** low. Keep the per-client `Dispatcher` separate — sharing it would queue reachability probes behind sync traffic. Removes a full TCP+TLS handshake from each probe.

---

## 77 — `ChatRepositoryImpl` calls a static reachability check
**from:** jules p2#3 · **files:** `repository/ChatRepositoryImpl.kt`
**verified:** line 41 is `org.ole.planet.myplanet.MainApplication.isServerReachable(url)` — fully-qualified static call inside a repository. `utils/ServerReachabilityProvider.kt` already exists and is what `MainApplication` delegates to.
**do:** inject `ServerReachabilityProvider` and call it directly.
**risk:** very low; the provider is already the underlying implementation.

---

## 77 — Examiner identity resolved in the Activity, patient in the ViewModel
**from:** devin p2#4 · **files:** `ui/health/HealthExaminationViewModel.kt`, `HealthExaminationActivity.kt`
**verified:** the activity reads `userSessionManager.getUserModel()` at line 78 while `HealthExaminationViewModel` already injects `userRepository`; the activity then mixes patient (`state.user`) and examiner (`currentUser`) around lines 251/256.
**do:** add `currentUser` to `HealthExaminationState`, populate it in the load path, drop the injection.
**risk:** low — keep every `state.user` (patient) reference untouched; the two identities are easy to conflate.

---

## 76 — Pointless `async`/`await` around a single repository call
**from:** codex p1#3 · **files:** `ui/courses/CoursesViewModel.kt`
**verified:** lines 125-129 wrap one `progressRepository` call in `coroutineScope { val progressDeferred = async { … }; progressDeferred.await() }` with no sibling work.
**do:** call the repository directly inside the existing IO context; remove the `async`/`coroutineScope` imports.
**risk:** low; preserve exception handling and the single final state publication.

---

## 76 — Dead `getNotifications` on the interface, and `Date()` bypassing the injected clock
**from:** claude p2#8 · **files:** `repository/NotificationsRepository.kt`, `…Impl.kt`
**verified:** `getNotifications` (interface line 22) resolves only to `NotificationsRepositoryImpl:166` inside `getEnrichedNotifications`. The impl injects `timeProvider` yet constructs `java.util.Date()` directly at 107, 115, 128, 136 and 461.
**do:** make the method private and replace each `Date()` with `Date(timeProvider.now())`.
**risk:** low; existing tests mock the DAO, not the repository method. Conflicts with the two other tasks in this file — sequence them.

---

## 76 — `InlineResourceAdapter` re-stats the same file several times per bind
**from:** grok p1#1 + devin p1#6 · **files:** `ui/courses/InlineResourceAdapter.kt`
**verified:** `updateStatusAndPreview` calls `FileUtils.checkFileExist(context, UrlUtils.getUrl(resource))`, which resolves and stats the file, and the preview helpers plus `getFileCacheKeyIfExist` then re-resolve and re-stat the same `File` (`exists`/`lastModified`/`length`).
**do:** resolve the `File` once per bind and thread it through the helpers. Devin's variant is the tighter statement of the fix; grok's adds an adapter-scoped `FileExistenceCache` cleared on detach and list change — take devin's core and grok's cache only if profiling justifies it.
**risk:** moderate — do not disturb the preview coroutine structure or the cache-key format.

---

## 75 — Multi-select grading sorts both collections before comparing sizes
**from:** codex p1#9 · **files:** `utils/ExamAnswerUtils.kt`
**verified:** `checkMultipleSelectAnswer` lowercases and sorts both collections, then compares — even when the sizes differ and equality is impossible.
**do:** return false immediately when the sizes differ, after the existing null guard.
**risk:** low; keep the sorted comparison for the equal-size path to preserve duplicate-answer semantics.

---

## 75 — `BellDashboardViewModel` depends on a `MainApplication` static
**from:** jules p2#2 · **files:** `ui/dashboard/BellDashboardViewModel.kt`
**verified:** line 15 statically imports `MainApplication.Companion.isServerReachable`, called at line 137.
**do:** inject `ServerReachabilityProvider` and call it directly.
**risk:** very low. The static import is easy to miss in a grep for `MainApplication.` — it does not match.

---

## 75 — Health upload sequence hand-rolled twice in a service
**from:** claude p2#6 · **files:** `repository/HealthRepository.kt` + `Impl`, `services/UploadToShelfService.kt`
**verified:** `UploadToShelfService` lines 70-77 and 78-93 each run the same fetch → `uploadHealthData` → `markHealthExaminationsUploaded` sequence, differing only in the fetch. Three methods stay public on the interface purely to let a service orchestrate them.
**do:** add `syncPendingHealthExaminations()` / `…ForUser(userId)` to the repository; reduce each service body to one call; drop the three intermediates from the interface.
**risk:** moderate — `uploadHealth()` / `uploadSingleUserHealth(...)` must keep their public signatures for `AutoSyncWorker`, `UserDataWorker` and `ProcessUserDataActivity`.

---

## 75 — `WebViewActivity` re-resolves its resource directory per navigation
**from:** devin p1#5 · **files:** `ui/viewer/WebViewActivity.kt`
**verified:** `getLocalResourceDirectory` runs `getExternalFilesDir` plus two `canonicalFile` resolutions and is called from `onCreate`, `setupWebView`, `setupAssetLoader` and `checkUrlSafety` — the last firing on every `onPageStarted` / `shouldOverrideUrlLoading`.
**do:** resolve once into a field and reuse.
**risk:** **do not** weaken the `canonicalFile` containment checks — they guard against path traversal. Cache the resolved directory, not the safety decision.

---

## 74 — Course-progress JSON decoded in the ViewModel
**from:** claude p1#9 + claude p2#7 · **files:** `repository/ProgressRepository.kt` + `Impl`, `ui/courses/ProgressViewModel.kt`
**verified:** `fetchCourseData` returns a raw `JsonArray` and `ProgressViewModel` maps it itself: `obj.has("stepMistake")` followed by `obj.get("stepMistake")` (two lookups for one value), and `obj.getAsJsonObject("progress")` resolved twice for `current` and `max`. `obj.get("courseId").asString` throws on a malformed document. Target type `CoursesProgressRow` already exists.
**do:** add `getCourseProgressRows(userId): List<CoursesProgressRow>` to the repository and move the mapping there, skipping rows with a missing id rather than throwing. This supersedes the narrower "read each field once" version of the same task — the field-read fix comes free with the move.
**risk:** moderate. Leave `fetchCourseData` itself alone: `DashboardViewModel:326` consumes its `JsonArray` for a different purpose.

---

## 74 — `ActivitiesRepositoryImpl` carries a `Context` it only forwards to a function that ignores it
**from:** kimi p2#3 · **files:** `repository/ActivitiesRepositoryImpl.kt`
**verified:** `NetworkUtils.getCustomDeviceName(context)` is `fun getCustomDeviceName(context: Context): String { return sharedPrefManager.getCustomDeviceName() }` (`NetworkUtils.kt:243-245`) — the parameter is entirely unused. The repository threads a `Context` through `serializeLoginActivities` solely to supply it.
**do:** inject the existing `DeviceNameProvider` (already used by `ResourcesRepositoryImpl` and `PersonalsRepositoryImpl`) and drop the forwarded parameter.
**risk:** low, but check the other `context` uses in the file before deleting the constructor param. The dead parameter has six further call sites — leave them to their own tasks.

---

## 73 — `PersonalsRepositoryImpl` mixes a static device-name call with the injected provider
**from:** grok p2#1 · **files:** `repository/PersonalsRepositoryImpl.kt`
**verified:** line 110 is `NetworkUtils.getDeviceName()` while line 111 is `deviceNameProvider.getCustomDeviceName()` — the provider is already injected (line 23) and used one line below.
**do:** source both JSON fields from the provider.
**risk:** confirm `DeviceNameProvider` exposes a plain device-name accessor; `getDeviceName()` and `getCustomDeviceName()` return different values, so this is not a like-for-like swap if the provider lacks the former.

---

## 73 — `DialogUtils` takes a repository to call one method
**from:** devin p2#6 · **files:** `utils/DialogUtils.kt`, `ui/sync/SyncActivity.kt`, `services/AutoSyncWorker.kt`
**verified:** `getUpdateDialog` and `startDownloadUpdate` accept a `ConfigurationsRepository` purely for `checkCheckSum(path)`; both callers already hold the repository.
**do:** take `checkCheckSum: suspend (String) -> Boolean` instead and pass `configurationsRepository::checkCheckSum` at both sites.
**risk:** low; removes a repository import from a UI utility.

---

## 72 — Resource filter signature sorts tag ids on every check
**from:** codex p1#4 · **files:** `ui/resources/ResourcesListFilter.kt`
**verified:** `toSignature` does `searchTags.map { it.id }.sorted()` — an allocation plus `O(t log t)` on an interactive path, though tag order is irrelevant to matching.
**do:** represent `searchTagIds` as a `Set` built directly from `searchTags`.
**risk:** low; keep the defensive copies of the other mutable sets. Note `ResourcesListFilter.kt` is owned by open PR 17187 — re-check before starting.

---

## 72 — Resource search builds two lists and concatenates them
**from:** grok p1#4 · **files:** `repository/ResourcesRepositoryImpl.kt` (`search`)
**verified:** after the SQL `LIKE` filter the results are split into `startsWithQuery` / `containsQuery` `mutableListOf`s and returned as `startsWithQuery + containsQuery` — a third allocation.
**do:** accumulate once in rank order via `buildList`.
**risk:** low; preserve the empty-query branches and `userIdPattern` escaping. Do not introduce FTS — open PR 16624 owns that.

---

## 72 — `CourseDao.filterByTitleNormal` is a `@RawQuery` fed hand-built SQL
**from:** kimi p2#7 · **files:** `data/room/dao/CourseDao.kt`, `repository/CoursesRepositoryImpl.kt`
**verified:** `CourseDao:33-34` is `@RawQuery suspend fun filterByTitleNormal(query: SupportSQLiteQuery)`, and `CoursesRepositoryImpl.search()` string-builds the statement per token and wraps it in `SimpleSQLiteQuery`.
**do:** add a parameterised `@Query` per token and intersect by id in Kotlin, preserving the existing escape handling and ranking.
**risk:** **check open PR 15808 first** — it touches `CourseDao.kt`; kimi flags this itself. The same raw-SQL pattern exists in `ResourcesRepositoryImpl` (my_library) and is a separate job.

---

## 71 — Achievement upload acknowledges one row per network result
**from:** codex p2#9 · **files:** `services/upload/AchievementUploader.kt`, `repository/UserRepository.kt` + `Impl`, `data/room/dao/AchievementDao.kt`
**verified:** `markAchievementUploaded` is called inside the result loop, one Room update per successful document.
**do:** accumulate successful acknowledgements and flush once through a batch repository method backed by a single DAO transaction.
**risk:** moderate (4 files). Keep CV attachment upload immediately after each document — it needs that document's returned revision.

---

## 71 — Constant string resources resolved on every adapter bind
**from:** claude p1#7 · **files:** `ui/teams/members/MembersAdapter.kt`, `ui/teams/courses/TeamCoursesAdapter.kt`, `ui/enterprises/EnterprisesReportsAdapter.kt`
**verified:** `MembersAdapter` resolves `R.string.team_leader` at lines 77 and 121 and `R.string.no_visit` at 111; `TeamCoursesAdapter:62` resolves `R.string.remove`; `EnterprisesReportsAdapter:65` formats `R.string.team_financial_report` with a constructor-fixed `teamName`. `TeamsSelectionAdapter` already caches exactly this way.
**do:** hoist each into a `by lazy` field.
**risk:** low, but `ui/teams/**` is heavily contested by open PRs — re-check before starting.

---

## 70 — `DictionaryFileReaderImpl` holds a `Context` for path resolution
**from:** grok p2#3 · **files:** `repository/DictionaryFileReader.kt`
**verified:** the impl takes `@ApplicationContext Context` to call `FileUtils.checkFileExist(context, …)` / `getSDPathFromUrl(context, …)`, while `StoragePathResolver` already centralises those operations.
**do:** inject `StoragePathResolver` instead; keep the interface and bind target name stable so no module edit is needed.
**risk:** low by design — the "no `RepositoryModule` edit" constraint is what keeps it small.

---

## 70 — Carve the achievement surface out of a 45-member `UserRepository`
**from:** devin p2#7 · **files:** new `repository/UserAchievementsRepository.kt`, `UserRepository.kt`, new `di/UserAchievementsRepositoryModule.kt`, `services/upload/AchievementUploader.kt`
**verified:** `UserRepository.kt` declares exactly 45 functions; `AchievementUploader` uses two of them. The interface-segregation precedent already exists (`TeamsFinancesRepository` et al).
**do:** extract the achievement block into a parent interface that `UserRepository` extends; provide it from `UserRepository` in a new module; narrow the uploader's injected type.
**risk:** needs a new Hilt module because `RepositoryModule` is PR-owned — verify Hilt resolves it before relying on `assembleDefaultDebug`.

---

## 70 — Redundant `distinct()` when ordering notification groups
**from:** codex p1#8 · **files:** `ui/notifications/NotificationsViewModel.kt`
**verified:** `(TYPE_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in TYPE_ORDER }).distinct()` — the second list explicitly excludes everything in the first, and both sides hold unique keys, so the dedup pass is provably a no-op.
**do:** drop the `distinct()`.
**risk:** low; retain `TYPE_ORDER` precedence and the iteration order for unknown types.

---

## 70 — `LifeCache` re-parses JSON on every read
**from:** grok p1#6 · **files:** `repository/LifeCache.kt`
**verified:** `read` always does `preferences.getString(...)` then `gson.fromJson`, with no in-memory layer; `LifeRepositoryImpl.getMyLifeForDashboard` hits it on the empty-visible path, so repeated dashboard entry pays Gson each time.
**do:** add a process-local map checked before prefs, invalidated on write. Return immutable or defensive copies.
**risk:** low; keep the `@Singleton` scope and the `myLifeCache_` key prefix. Overlaps with the `LifeCache` package move — do that one first or together.

---

## 69 — Retry worker owns HTTP transport as well as queue state
**from:** codex p2#8 · **files:** `services/retry/RetryQueueWorker.kt`, `repository/RetryRepository.kt` + `Impl`
**verified:** the worker injects `ApiInterface` directly (line 41) and combines request execution with queue transitions, while `RetryRepository` already owns enqueue, pending selection, attempts and completion.
**do:** move endpoint dispatch and response classification into the repository behind a result type that distinguishes success / retryable / terminal without exposing Retrofit types.
**risk:** the largest item here (~120 lines, 3 files). Cancellation must not be recorded as a failed attempt.

---

## 69 — Per-row date formatting on every `PersonalsAdapter` bind
**from:** kimi p1#1 · **files:** `ui/personals/PersonalsAdapter.kt`
**verified:** `onBindViewHolder` calls `TimeUtils.getFormattedDate(item.date)` per row per bind; `ui/teams/TeamsAdapter.kt:62` already establishes the `dateCache.getOrPut(...)` pattern.
**do:** add a `HashMap<Long, String>` date cache and wrap the call.
**risk:** low; key on `item.date ?: 0L` if the field is nullable.

---

## 68 — Per-row date formatting on every `HealthUsersAdapter` bind
**from:** kimi p1#3 · **files:** `ui/health/HealthUsersAdapter.kt`
**verified:** `ViewHolder.bindDate` calls `TimeUtils.formatDate(user.joinDate)` on every bind including the partial-payload rebind path.
**do:** hoist a date cache to the adapter (not the ViewHolder, so it survives recycling) and wrap the call.
**risk:** low.

---

## 68 — Per-row date formatting on every `UserArrayAdapter` bind
**from:** kimi p1#2 · **files:** `ui/user/UserArrayAdapter.kt`
**verified:** `onBindViewHolder` calls `TimeUtils.formatDate(user.joinDate)` per bind; `joinDate` is immutable per row.
**do:** same date-cache treatment.
**risk:** low.

---

## 68 — `CollectionsFragment` keeps UI state in a `MainApplication` static
**from:** jules p2#4 · **files:** `ui/resources/CollectionsFragment.kt`, `MainApplication.kt`
**verified:** `MainApplication.isCollectionSwitchOn` is declared at `MainApplication.kt:114` and used only at `CollectionsFragment.kt:162` (read) and `:233` (write).
**do:** move it to a fragment field and delete the static.
**risk:** low — with exactly one reader and one writer the move is contained. Process death currently resets it anyway, so no behaviour is lost.

---

## 68 — `FeedbackListFragment` restarts a collector the ViewModel already owns
**from:** codex p2#5 · **files:** `ui/feedback/FeedbackListFragment.kt`, `FeedbackListViewModel.kt`
**verified:** `FeedbackListViewModel.loadFeedback` already collects `FeedbackRepository.getFeedback` continuously, yet the fragment injects `RealtimeSyncManager`, implements `RealtimeSyncMixin` and restarts that collector on `"feedback"` events.
**do:** delete the mixin and injection; keep the ViewModel's single repository-flow collection alive for its lifetime.
**risk:** low-moderate; ensure `refreshFeedback` cannot create parallel collectors. One of five fragments doing this — see the sibling tasks.

---

## 67 — `FeedbackAdapter` re-formats the same date every bind
**from:** kimi p1#5 · **files:** `ui/feedback/FeedbackAdapter.kt`
**verified:** `onBindViewHolder` formats `feedback.openTime` and rebuilds a multi-part `contentDescription` on every bind.
**do:** cache by `openTime` and reuse the string for both the content description and the date text.
**risk:** low.

---

## 67 — `LifeCache` lives in the repository package
**from:** grok p2#2 · **files:** `repository/LifeCache.kt` → `data/cache/LifeCache.kt`, `repository/LifeRepositoryImpl.kt`
**verified:** `LifeCache` is a `@Singleton` SharedPreferences-backed cache sitting under `repository/`, keeping `android.content.SharedPreferences` in that package.
**do:** move the file and update the single consumer import and the test package.
**risk:** very low — constructor and `@Inject` stay identical, so Hilt needs no module change. Sequence against the `LifeCache` memoisation task.

---

## 67 — `MyHealthFragment` decides refresh policy the ViewModel already owns
**from:** codex p2#7 · **files:** `ui/health/MyHealthFragment.kt`, `HealthViewModel.kt`
**verified:** `setupRealtimeSync` consumes every `dataUpdateFlow` event and conditionally calls `refreshSelectedPatient`, while the ViewModel already owns `currentPatientId`, cancellation and the repository reads.
**do:** replace the table-event collection with one explicit ViewModel refresh intent keyed on the tracked patient id; coalesce repeats while a fetch is in flight.
**risk:** moderate; preserve the selected patient on transient failure rather than clearing it.

---

## 66 — `CalendarViewModel` reloads meetups on every teams-table write
**from:** kimi p1#4 + grok p2#10 · **files:** `ui/calendar/CalendarViewModel.kt`
**verified:** `loadMeetups()` collects `getMyTeamsFlow(userId)` and on every emission rebuilds `_teamNames` and re-queries `getMeetupsForTeams(...)`, even when the team list is unchanged — duplicate DB reads on any teams write.
**do:** kimi's fix is the cheap one — `distinctUntilChanged()` on the collected list and derive `teamIds` once. Grok's proposes extracting a `CalendarMeetupsLoader` for the three-repository fan-out; that is the larger structural move and does not itself remove the redundant reload. Do kimi's first; treat grok's as the optional follow-up.
**risk:** low for the `distinctUntilChanged` version; keep the public `meetups`/`teamNames` StateFlow API unchanged.

---

## 66 — `TeamViewModel` exposes a sync table name to the UI
**from:** codex p2#4 · **files:** `ui/teams/TeamViewModel.kt`, `TeamFragment.kt`
**verified:** `getTeamUpdateFlow` passes `RealtimeSyncManager.updatesFor("teams")` straight through, although `loadTeams` already collects `TeamsRepository.getMyTeamDetailsFlow` — two refresh paths, and a storage table name leaking into the UI layer.
**do:** delete the pass-through and the fragment's collector; make every `loadTeams` branch collect the repository flow.
**risk:** moderate — `ui/teams/**` is contested by several open PRs. Verify job cancellation still prevents duplicate collectors.

---

## 66 — Five copy-pasted upload-await blocks in `UserDataWorker`
**from:** devin p2#9 · **files:** `services/UserDataWorker.kt`
**verified:** the `UPLOAD_TYPE_BULK` branch repeats the same `CompletableDeferred` + `OnSuccessListener` + `withTimeoutOrNull(30000L)` shape five times, plus once more for a `() -> Unit` callback.
**do:** extract one `awaitUploadCompletion(start: (onComplete: () -> Unit) -> Unit)` helper.
**risk:** low; leave the `UPLOAD_TYPE_LOGIN` branch's `CompletableDeferred<String>` alone — different result type.

---

## 65 — `MyTeam.serialize` filters keys into a list before removing them
**from:** codex p1#10 · **files:** `model/MyTeam.kt`
**verified:** the filter-then-remove pair appears twice — lines 176/177 and 222/223 — allocating a key list and traversing twice in a function that runs throughout sync and upload.
**do:** remove nulls in one iterator pass; apply the same shape to both branches.
**risk:** low, but use iterator removal — mutating the `JsonObject` while iterating its `keySet()` view otherwise throws.

---

## 65 — `TimeUtils` prints stack traces from list-bind paths
**from:** kimi p1#7 · **files:** `utils/TimeUtils.kt`
**verified:** `e.printStackTrace()` at seven sites including `formatInstant`, which runs inside every `getFormattedDate` / `formatDate` row bind — a parse failure prints a full trace per row per rebind.
**do:** replace with tagged `Log.w`, keeping return values identical.
**risk:** low. Note `TimeUtils` was on grok's discard list as PR-contested — re-check.

---

## 65 — `SubmissionsRepositoryExporter` mixes DAO assembly with Android PDF APIs
**from:** kimi p2#8 + grok p2#4 · **files:** `repository/SubmissionsRepositoryExporter.kt`, `repository/SubmissionsRepositoryImpl.kt`, new PDF writer
**verified:** the exporter takes `Context` as a method parameter and pulls in `PdfDocument`/`Paint`/`Canvas`; `SubmissionsRepositoryImpl` threads `@ApplicationContext` into it and into `NetworkUtils.getCustomDeviceName(context)` (which ignores the argument).
**do:** grok's is the more complete statement — split rendering and file write into a new `@Inject` helper and keep assembly in the exporter. Kimi's covers the narrower `Context`-injection cleanup and the `DeviceNameProvider` swap; fold that in.
**risk:** moderate; keep `generateSubmissionPdf`'s public signature so callers compile unchanged.

---

## 64 — `SurveyFragment` owns both sync details and reload policy
**from:** codex p2#6 · **files:** `ui/surveys/SurveyFragment.kt`, `SurveysViewModel.kt`
**verified:** the fragment injects `RealtimeSyncManager` and installs `RealtimeSyncHelper` while `SurveysViewModel.loadSurveys` separately owns team/adoptable selection.
**do:** move reload policy into the ViewModel; make `loadSurveys` cancel any prior job and retain the active scope; reload after `adoptSurvey` from inside the ViewModel.
**risk:** moderate — the stale-load-overwrites-newer-selection guard is the fiddly part and is the actual user-visible bug here.

---

## 64 — `News` model prints stack traces during feed diffing
**from:** kimi p1#8 · **files:** `model/News.kt`
**verified:** `printStackTrace()` in `isCommunityNews`, `calculateSortDate`, `createNews` and the conversation-parse catches — all on the voices list/sort path.
**do:** replace with tagged `Log.w`; leave parsed results and fallbacks unchanged.
**risk:** low.

---

## 64 — Public survey payload assembly lives in an Activity
**from:** kimi p2#5 + grok p2#8 · **files:** `ui/surveys/PublicSurveyActivity.kt`, plus a new builder or ViewModel
**verified:** `buildPublicAnswers` queries `surveysRepository.getExamQuestions(surveyId)`, zips answers by `questionId` and encodes the `selectMultiple`/`select` wire format; `sanitizeRespondent` mutates the respondent `JsonObject` — data-layer serialization in an Activity.
**do:** the two agents disagree on the destination: kimi extracts an injectable `PublicSurveyPayloadBuilder` into `repository/`, grok introduces a `PublicSurveyViewModel`. Prefer grok's — the Activity also needs the upload orchestration moved, and a ViewModel covers both; kimi's builder can live behind it if the payload logic deserves its own unit.
**risk:** moderate. Neither may edit the survey/submission interfaces — both are PR-owned.

---

## 63 — `VoicesLabelManager` swallows write failures to stderr
**from:** kimi p1#10 · **files:** `services/VoicesLabelManager.kt`
**verified:** `printStackTrace()` on the addLabel and removeLabel failure paths — background write failures invisible to logcat filtering and crash triage.
**do:** replace with tagged `Log.w`; keep the toast and retry semantics.
**risk:** low.

---

## 63 — `ResourcesPreviewLoader` redoes audio/CSV/text work per call
**from:** grok p1#8 · **files:** `utils/ResourcesPreviewLoader.kt`
**verified:** `getAudioPreview` builds a new `MediaMetadataRetriever` per call with no memoisation; `getCsvPreview` rebuilds its parser stack; `getTextPreview` reopens the file.
**do:** add a bounded LRU keyed on `absolutePath + lastModified + length`.
**risk:** low-moderate — cap the cache to avoid leaks and keep the return formats (`m:ss`, 5 CSV rows, 8 text lines) byte-identical.

---

## 63 — `SurveysRepository` returns pre-formatted display strings
**from:** kimi p2#2 · **files:** `model/SurveyInfo.kt`, `repository/SurveysRepositoryImpl.kt`, `ui/surveys/SurveysViewModel.kt`, `SurveysAdapter.kt`
**verified:** `getSurveyInfos()` formats `submissionCount` through `context.resources.getQuantityString(R.plurals.survey_taken_count, …)` and `creationDate` through `TimeUtils.formatDate`, so `SurveyInfo` carries strings — locale changes cannot re-render without a re-query.
**do:** return `Int`/`Long` raw values and move formatting into the adapter.
**risk:** moderate — four files, and the survey repository is PR-contested. Verify each field's producers and consumers first.

---

## 62 — Course-step resource orchestration sits in the ViewModel
**from:** codex p2#2 + grok p2#9 · **files:** `ui/courses/CoursesStepsViewModel.kt`, plus repository or new coordinator
**verified:** `loadStep` performs filesystem path lookup and calls `ResourceDownloadCoordinator.startBackgroundDownload` directly, and the ViewModel injects `Context` for markdown base paths, though the repository already owns `getAllStepResources` and `downloadResourcesPriority`.
**do:** the two differ on destination — codex pushes the operation behind `ResourcesRepository` (cleaner layering); grok adds a `CourseStepResourceCoordinator` helper because it treats the repository as off-limits. Prefer codex's if `ResourcesRepository` is free when you start, else grok's.
**risk:** moderate; keep `CourseStepUiState.isDownloadingResources` semantics unchanged.

---

## 62 — `ChatViewModel` translates sync-table events into refreshes
**from:** codex p2#3 · **files:** `ui/chat/ChatViewModel.kt`, `repository/ChatRepository.kt` + `Impl`
**verified:** the ViewModel injects `RealtimeSyncManager` and maps the global `"chats"` table event to a refresh, while `getChatHistoryForUser` exposes only a snapshot.
**do:** add a repository flow for one user's sorted history and drive the ViewModel from it.
**risk:** moderate — `ChatViewModel.kt` is owned by open PR 15198; claude's list excludes it for that reason. Re-check before starting. Preserve the active search query across emissions.

---

## 62 — `FileUtils.getFileExtension` allocates a `File` for a string suffix
**from:** grok p1#2 · **files:** `utils/FileUtils.kt`
**verified:** `getFileExtension` is `address?.let { File(it).extension.lowercase() } ?: ""`.
**do:** use `substringAfterLast` string operations, matching the current empty/null behaviour exactly.
**risk:** low for the extension half. Grok bundles a second change — extending `FileExistenceCache` to mean `exists && length > 0` — which alters shared cache semantics; split that out and treat it separately.

---

## 61 — `DashboardElementActivity` reaches into two services with no ViewModel
**from:** devin p2#10 · **files:** `ui/dashboard/DashboardElementActivity.kt`, new `DashboardElementViewModel.kt`
**verified:** the activity injects `ActivitiesRepository` directly and calls `profileDbHandler.getUserModel()` / `logoutAsync()` (inherited from `SyncActivity`) inside `lifecycleScope` blocks.
**do:** introduce a `@HiltViewModel` for the user lookup, the challenge-action record and logout.
**risk:** moderate; do not rename or remove `profileDbHandler` in `SyncActivity`, which still uses it.

---

## 61 — `ReplyActivity` field-injects four data collaborators
**from:** kimi p2#1 · **files:** `ui/voices/ReplyViewModel.kt`, `ReplyActivity.kt`
**verified:** the activity injects `activitiesRepository`, `sharedPrefManager`, `voicesRepository` and `userSessionManager` and calls them directly, though `ReplyViewModel` already exists with a single method.
**do:** move the session read, the two `SharedPrefManager` calls and the member-details fetch behind the ViewModel.
**risk:** moderate; `VoicesActions.showMemberDetails` needs its data dependency separated from fragment construction, which is the awkward part.

---

## 60 — `MainApplication.showDownload` is write-only state
**from:** jules p2#5 · **files:** `ui/teams/TeamDetailFragment.kt`, `MainApplication.kt`
**verified:** declared at `MainApplication.kt:115` and touched at exactly two sites — `TeamDetailFragment:311` (`= true`) and `:318` (`= false`). **No reader anywhere.** Jules describes it as coordinating page switches; it coordinates nothing.
**do:** delete the static and both assignments outright rather than relocating the field.
**risk:** low, but grep once more for reflective or generated access before deleting. `ui/teams/**` is PR-contested.

---

## 60 — `ChatHistoryAdapter` bind and diff micro-costs
**from:** grok p1#10 · **files:** `ui/chat/ChatHistoryAdapter.kt`
**verified:** `areContentsTheSame` evaluates `conversations?.firstOrNull()?.query` per side; the bind path resolves the first query again.
**do:** read the first query once per side into locals; avoid re-copying the conversation list outside the click handler.
**risk:** low, but grok's own write-up concedes the click-time `toList()` is already correct — the remaining win is small and partly speculative.

---

## 59 — `DownloadService.getNextUrl` allocates a filtered list to take a minimum
**from:** codex p1#2 · **files:** `services/DownloadService.kt`
**verified:** `urls.filter { it !in processedUrls && it.isNotBlank() }.minOrNull()` — a full intermediate list per queue pop.
**do:** use a lazy sequence or a single-pass minimum.
**risk:** low; preserve both eligibility predicates and the lexicographic ordering.

---

## 58 — `ResourcesRepositoryImpl` uses `MainApplication` statics
**from:** grok p2#5 · **files:** `repository/ResourcesRepositoryImpl.kt`
**verified:** `MainApplication.context.getExternalFilesDir(null)` at line 399 and `MainApplication.applicationScope.launch` at line 504.
**do:** resolve paths through the injected `StoragePathResolver` and replace the fire-and-forget launch with structured `withContext` on the injected dispatcher.
**risk:** the scope change is the risky half — a fire-and-forget call becoming suspend changes the caller's timing. Grok allows either that or `@ApplicationScope`; prefer the qualifier if it is already bindable.

---

## 58 — `ProcessUserDataActivity` runs a three-deep callback pyramid
**from:** kimi p2#6 · **files:** `ui/sync/ProcessUserDataActivity.kt`, new ViewModel, `services/UploadToShelfService.kt`
**verified:** the activity field-injects `syncRepository`, `userRepository` and `uploadToShelfService`, and chains `uploadSingleUserData` → `uploadSingleUserHealth` → `fetchAndLogUserSecurityData` through nested success listeners.
**do:** add a suspend counterpart on the service and a ViewModel exposing the three branches as state.
**risk:** high overlap — this edits `UploadToShelfService`, which the health-upload task also rewrites. Sequence them or merge.

---

## 57 — `Answer.valueChoicesArray` re-parses on every access
**from:** devin p1#4 · **files:** `model/Answer.kt`
**verified:** the `@get:Ignore` getter builds a fresh `JsonArray` and runs `gson.fromJson` per element on every read, and it is hit per answer on the upload path.
**do:** memoise against the `valueChoices` list it was built from.
**risk:** caching derived state on a mutable Room entity is the hazard — the invalidation check must be honest, or this reintroduces the staleness bug the `Feedback` task fixes.

---

## 56 — `TTSManager.stripMarkdown` chains ten regex replacements
**from:** grok p1#9 · **files:** `utils/TTSManager.kt`
**verified:** ten `.replace(Regex, …)` calls in sequence; the regexes are precompiled, so the remaining cost is intermediate strings and repeated full scans.
**do:** add a cheap `contains` pre-check fast path for text with no markdown markers.
**risk:** the "combine compatible replacements" half is where stripping behaviour breaks; keep the ordering and take only the fast path unless tests cover every marker.

---

## 55 — `NotificationActionReceiver` launches on a global scope
**from:** jules p2#10 · **files:** `services/NotificationActionReceiver.kt`
**verified:** line 32 is `MainApplication.applicationScope.launch` inside a `BroadcastReceiver`.
**do:** use `goAsync()` with a locally managed scope.
**risk:** `goAsync()` must be paired with `finish()` on every path or the receiver leaks — jules' step list does not say so. Get the lifecycle right or leave it.

---

## 55 — Narrow the Teams sub-interfaces onto real consumers
**from:** kimi p2#10 · **files:** `ui/enterprises/EnterprisesFinancesViewModel.kt`, `EnterprisesViewModel.kt`, `ui/notifications/NotificationsViewModel.kt`
**verified:** `TeamsRepository` extends `TeamsFinancesRepository`, `TeamsMembersRepository` and `TeamsNotificationsRepository`, but consumers still inject the whole interface.
**do:** narrow each ViewModel's constructor type to the smallest parent it actually uses.
**risk:** the task is written as an investigation ("pick after verifying actual usage"), not a specified change, and it depends on each parent having a `@Binds` in the PR-owned `RepositoryModule`. Scope it concretely before scheduling.

---

## 54 — `getEnrichedNotifications` makes several passes and an always-on DAO call
**from:** grok p1#7 · **files:** `repository/NotificationsRepositoryImpl.kt`
**verified:** the method walks payloads to split task/join lists then runs further `mapNotNull`/`distinct`/`filter` passes, and always issues `async { getUnreadCount(...) }` even when the loaded payloads already carry `isRead`.
**do:** collapse to a single pass; derive the unread tally in memory where that matches the DAO's semantics for the current filter.
**risk:** the unread-count shortcut is the dangerous part — admin and `unread`-filter cases must keep hitting the DAO or counts silently drift. Conflicts with two other tasks in this file.

---

## 53 — `MainApplication.listener` is a static fragment reference
**from:** jules p2#6 · **files:** `ui/teams/TeamDetailFragment.kt`, `TeamPagerAdapter.kt`, `MainApplication.kt`
**verified:** assigned at `TeamPagerAdapter:84`, `:87` and `TeamDetailFragment:237`; read at `TeamDetailFragment:335`, `:337`, `:339`. A static holding a Fragment is a genuine leak and breaks with multiple team pages.
**do:** resolve the active fragment from the ViewPager at click time instead.
**risk:** real lifecycle correctness win, but `ui/teams/**` is the single most PR-contested area in the repo (15951, 16623, 15825, 15820) and jules could not check open PRs. Expect a collision; re-verify ownership before starting.

---

## 52 — `SyncRepositoryImpl` hard-codes a shelf-dispatch map and logs via `android.util.Log`
**from:** kimi p2#4 · **files:** `repository/SyncRepository.kt` + `Impl`
**verified:** the impl injects `dagger.Lazy<TransactionSyncManager>` alongside four repositories and builds `shelfDispatchMap` inline; `Log.e` at two sites.
**do:** extract the map behind a small `ShelfBatchInserter` seam and surface errors instead of logging them.
**risk:** the task's own fallback ("construct internally if `RepositoryModule` is PR-blocked") undercuts the point of the extraction, and swapping `Lazy` for direct injection risks an init cycle. Low confidence as specified.

---

## 52 — Move notification display formatting behind the repository
**from:** grok p2#7 · **files:** `repository/NotificationsRepository.kt` + `Impl`, `ui/notifications/NotificationsViewModel.kt`
**verified:** the ViewModel calls `getEnrichedNotifications` then re-applies `Context` + `R.string` in `formatNotification`.
**do:** produce ready-to-bind fields from the repository so the ViewModel drops `Context`.
**risk:** this pushes `R.string` *into* the data layer, which is the opposite of the `SurveysRepository` task in this same backlog — the two encode contradictory policies. Pick one direction repo-wide before scheduling either. Also collides with two other tasks in `NotificationsRepositoryImpl`.

---

## 50 — `CoursesRepositoryImpl` holds a process-wide pending-resource buffer
**from:** kimi p2#9 · **files:** `repository/CoursesRepositoryImpl.kt`
**verified:** a `Collections.synchronizedList(mutableListOf<PendingCourseResource>())` field on a `@Singleton` repository, shared across concurrent sync runs.
**do:** thread the buffer through the parse/flush functions as a local so one run cannot leak into another.
**risk:** the task opens with "trace the lifecycle of `pendingCourseResources`" — the analysis is not done, so the change is unspecified. Do the trace first and re-scope.

---

## 50 — `DownloadService` re-reads and rewrites preference sets per file
**from:** grok p1#3 · **files:** `services/DownloadService.kt`
**verified:** after each URL, `getRemainingCount()` re-reads both StringSets and `cleanupProcessedUrls()` re-reads, mutates, writes back both, then recounts.
**do:** mirror the queues in memory, pop from memory, and persist in batches.
**risk:** highest-risk item here. Grok concedes it "may approach" the size limit, and batching persistence trades crash-safety for speed in a foreground download service — a crash mid-batch loses queue state. Needs a durability decision before coding.

---

## 48 — `ChatShareTargetAdapter` allocates a `Typeface` per header bind
**from:** devin p1#10 · **files:** `ui/chat/ChatShareTargetAdapter.kt`
**verified:** `listTitleTextView.setTypeface(null, Typeface.BOLD)` at line 51, which routes through `Typeface.create` on every rebind.
**do:** set the bold typeface once in the ViewHolder `init`.
**risk:** trivial change, negligible payoff — group headers are few.

---

## 46 — `StorageCategories.indexOf` lowercases on every lookup
**from:** jules p1#1 · **files:** `ui/settings/StorageCategories.kt`
**verified:** `extensionToIndex[extension.lowercase()] ?: OTHER_INDEX` — one allocation per call.
**do:** try the raw key first and fall back to the lowercased form.
**risk:** correct but marginal; only pays off when callers already pass lowercase extensions.

---

## 45 — `UploadConfigs` resolves a `Lazy` inside per-result loops
**from:** codex p1#7 · **files:** `services/upload/UploadConfigs.kt`
**verified:** `teamsSyncRepository.get()` at lines 89, 97, 105, 106 and 110; the calls at 97 and 110 sit inside result loops.
**do:** resolve once at the start of each `markUploaded` lambda.
**risk:** correct, but Dagger's `Lazy.get()` is a cheap field read after first call — this is readability, not measurable performance. Keep the `Lazy` to avoid reintroducing a dependency cycle.

---

## 44 — `CoursesAdapter.areAllSelected` counts when it could read a size
**from:** jules p1#5 · **files:** `ui/courses/CoursesAdapter.kt`
**verified:** `currentList.count { isMyCourseLib || !it.isMyCourse }` — when `isMyCourseLib` is true the predicate is constant, so the scan is `currentList.size`.
**do:** branch on `isMyCourseLib` before counting.
**risk:** trivially correct, negligible payoff.

---

## 42 — `TeamCalendarFragment` uses `mapTo(mutableListOf())` where `map` suffices
**from:** jules p1#8 · **files:** `ui/teams/TeamCalendarFragment.kt`
**verified:** `meetups.mapTo(mutableListOf()) { … }` feeding `eventDates.addAll(newDates)`.
**do:** use `map`.
**risk:** identical allocation behaviour; this is style, not performance. Ship only alongside other work in the file.

---

## 38 — Redundant size read in `BaseDashboardFragment`
**from:** jules p1#9 · **files:** `base/BaseDashboardFragment.kt`
**verified:** `filteredCourses.size` at line 191 then `filteredCourses.isEmpty()` at 192.
**do:** hoist a `count` local if touching the file anyway.
**risk:** both are O(1) reads on a `List` — the stated "repeated list traversals" does not happen. Cosmetic only.

---

## 35 — Three `.size` reads in `ResourcesFragment`
**from:** jules p1#2 · **files:** `ui/resources/ResourcesFragment.kt`
**verified:** `filteredList.size` on three consecutive lines (412-414).
**do:** hoist into a local if the file is open for other reasons.
**risk:** `List.size` is a field read; the claimed waste is not real. Lowest-value item in the backlog, and the file is owned by open PR 17187.
