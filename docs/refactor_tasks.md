# myPlanet merged refactor backlog

Merged from 14 agent task lists (6 agents × 2 prompts + 2 agents × prompt 1), 140 raw tasks.
Every premise below was re-verified against the working tree at `022ee7d`; 16 raw tasks whose
premise did not hold were dropped (see "Dropped" at the end).

Rating = **0.40 × evidence quality + 0.35 × impact + 0.25 × risk-adjusted feasibility**, each
scored 0–100. Evidence = premise verified exactly in the tree, including line refs, caller
analysis and whether the prescribed fix actually follows. Impact = runtime/user-visible or
maintainability gain. Feasibility = small, safe, behaviour-preserving, testable.
`proposed by` lists every agent that proposed the task; the share in parentheses is duplicate
credit awarded by completeness of the submission, not by order.

---

### stop re-decrypting health examinations on the main thread
**rating:** 92
**proposed by:** claude(1.0)
**verified:** `ui/health/HealthExaminationAdapter.kt:62` decrypts each row off the main thread
inside `submitExaminations` (`dispatcherProvider.default`) but keeps only the boolean
`hasEncryptedData` (field at :42, set at :80). The click handler at :119-120 then runs
`item.examination.getEncryptedDataAsJson(user)` again — AES decrypt plus a Gson parse —
synchronously on the main thread, once per tap.
**do:** change `HealthExaminationItem.hasEncryptedData: Boolean` to `encrypted: JsonObject?`,
populate it from the value already computed at :62, and have the click handler pass
`item.encrypted` to `showAlert`. `showAlert`/`showEncryptedData` already take a `JsonObject`.
Leave `DIFF_CALLBACK` comparing what it compares today.
**notes:** ~12 lines, 1 file, no test references this adapter.

---

### move the full-page PDF render off the main thread and stop leaking the descriptor
**rating:** 89
**proposed by:** kimi(1.0)
**verified:** `ui/viewer/ResourceViewerFragment.kt:509-535` opens a `ParcelFileDescriptor`,
builds a `PdfRenderer`, `openPage(0)`, allocates a full-size `createBitmap(page.width,
page.height)` and renders it — all synchronously on the main thread. The three `close()` calls
sit after the view mutations with no `finally`/`use`, so any exception leaks the descriptor and
the renderer. `dispatcherProvider` is already injected at :105.
**do:** wrap only decode/render in `viewLifecycleOwner.lifecycleScope.launch {
withContext(dispatcherProvider.io) { … } }`; keep every view mutation on the main thread after
the bitmap returns; convert the manual closes to `.use {}`. Keep today's failure behaviour
(placeholder stays visible) — no new error UI.
**notes:** the existing `catch { e.printStackTrace() }` should become a tagged log while here.

---

### drop the two unused repository injections from BaseResourceFragment
**rating:** 89
**proposed by:** claude(1.0)
**verified:** `base/BaseResourceFragment.kt:61` and `:63` inject `SubmissionsRepository` and
`SurveysRepository`; the only occurrences in the file are those two declarations. Swept every
file that references either symbol — `BaseExamFragment`, `ExamTakingFragment`,
`UserInformationFragment`, the submissions/surveys/dashboard ViewModels — and each declares its
own, so no subclass resolves the inherited field.
**do:** delete both `@Inject lateinit var` pairs and the two now-dead imports.
**notes:** every resource, course, voices, team and recycler screen currently builds two
repositories it never calls. ~6 removed lines.

---

### hoist the per-bind click listener out of CoursesProgressAdapter (fixes wrong-course navigation)
**rating:** 88
**proposed by:** claude(1.0)
**verified:** `ui/courses/CoursesProgressAdapter.kt` `onBindViewHolder` (28-40) allocates a
fresh `holder.itemView.setOnClickListener { … }` per bind, and only inside the
`if (item.progressCurrent != null && item.progressMax != null)` branch. A recycled holder that
lands on a row without progress keeps the previous row's listener, so tapping it opens
`CourseProgressActivity` for the wrong `courseId`.
**do:** move the click handling into `CoursesProgressViewHolder.init`, resolve the row via
`bindingAdapterPosition` (bail on `NO_POSITION`), start the activity only when that row has
non-null progress values, and delete the `setOnClickListener` from `onBindViewHolder`.
**notes:** a correctness fix, not just an allocation win.

---

### use one bulk exam lookup for per-step question counts
**rating:** 87
**proposed by:** openhands-deepseek(1.0)
**verified:** `repository/CoursesRepositoryImpl.kt:142` calls
`submissionsRepository.getExamQuestionCount(step.id)` inside `rawSteps.map`, and that method
(`SubmissionsRepositoryImpl.kt:162-164`) is `examDao.getFirstByStepId(stepId)?.noOfQuestions ?: 0`
— one `LIMIT 1` query per step. `ExamDao.getByStepIds(stepIds)` already exists (`ExamDao.kt:19`).
`CoursesRepositoryImpl.kt:142` is the only caller of `getExamQuestionCount` anywhere.
**do:** fetch `examDao.getByStepIds(stepIds)` once, build `stepId -> noOfQuestions` (first row
wins, matching `LIMIT 1`), map the steps against it, and delete `getExamQuestionCount` from
`SubmissionsRepository` and its impl.
**notes:** also removes the only per-step suspension from step-item construction.

---

### remove the unused SubmissionsRepository edge from UploadManager
**rating:** 85
**proposed by:** claude(1.0)
**verified:** `services/UploadManager.kt` mentions `SubmissionsRepository` exactly twice — the
import at :27 and the constructor parameter at :56. Nothing in the class calls it. Photo
submission uploads go through the separately injected `photoUploader`, which holds its own.
**do:** delete the parameter and the import; drop the matching positional argument in
`UploadManagerTest` (keep the test's own field — `PhotoUploader(...)` still needs it).
**notes:** ~3 lines. Makes the upload orchestrator stop looking coupled to submissions.

---

### drop the unused DispatcherProvider from the voices label delegate and NewsViewModel
**rating:** 84
**proposed by:** claude(1.0)
**verified:** `ui/voices/LabelManipulator.kt:13` — `DefaultLabelManipulator` takes a
`DispatcherProvider` and never reads it; both overrides are bare pass-throughs to
`voicesRepository`. `TeamsVoicesViewModel.kt:38` is the delegation's only use of its own
`dispatcherProvider` (:34), so that parameter is fully dead there. `VoicesViewModel` must keep
its own (used at :65 and :192). `ui/voices/NewsViewModel.kt:18` injects one the class never reads.
**do:** strip the parameter from `DefaultLabelManipulator` and both delegation sites, then
remove the now-dead parameter and import from `TeamsVoicesViewModel` and `NewsViewModel`.
**notes:** 4 files, ~10 lines, plus any positional test constructors.

---

### stop double-loading team resources on every screen entry, and cancel the superseded load
**rating:** 84
**proposed by:** claude(1.0)
**verified:** `ui/teams/resources/TeamResourcesFragment.kt` calls `showLibraryList()` from
`onViewCreated` (:49) and again from `onResume` (:63); `showLibraryList` (:72) forwards to
`TeamResourcesViewModel.loadResources` (:31), an unguarded `viewModelScope.launch` running
`getTeamResources` and `isTeamLeader` concurrently. Four queries per entry, and nothing cancels
the earlier launch, so two jobs race to write `_uiState` and the adapter can be handed a stale list.
**do:** hold the load in a `loadJob` and cancel it at the top of `loadResources`; skip the
relaunch when `(teamId, userId)` matches the last request and the job is still active; route the
explicit post-add/remove refreshes (:126, :150) through a `reload()` that bypasses the guard.
**notes:** the `android.util.Log` use in `recordActivitySafely` (import at :3) is this class's
only `android.*` dependency — replacing it with `runCatching` makes the ViewModel platform-free.

---

### replace the sequential per-team chat count with one concurrent fetch
**rating:** 84
**proposed by:** grok(0.8), openhands-deepseek(0.2)
**verified:** `repository/NotificationsRepositoryImpl.kt:315-317` — `for (teamId in
notificationsById.keys) { chatCountsById[teamId] = voicesRepository.countTopLevelByTeam(teamId) }`.
One suspend query per team on every dashboard team-badge refresh.
**do:** replace the loop with `coroutineScope { keys.map { async { it to
voicesRepository.countTopLevelByTeam(it) } }.awaitAll().toMap() }`. Keep the `hasTask`/`hasChat`
logic and the empty-input early return, and add no methods to `VoicesRepository`.
**notes:** the aggregate-SQL variant openhands proposed would be better still, but its query as
written is self-contradictory and `NewsDao`'s `viewIn LIKE` pattern makes a correct `GROUP BY`
non-trivial; parallelising is the safe version of the same win.

---

### stop double-querying team transactions on every finances screen entry
**rating:** 83
**proposed by:** claude(1.0)
**verified:** `ui/enterprises/EnterprisesFinancesFragment.kt` calls `observeTransactions()` at
:273 (`onViewCreated`) and again at :356 (`onResume`) with the same arguments;
`EnterprisesFinancesViewModel.getTeamTransactions` (:71) unconditionally does
`transactionsJob?.cancel()` and relaunches the Room flow collection, so the first display
cancels the in-flight query and re-runs `getTeamTransactionsWithBalance` from scratch.
**do:** store the last-requested `(teamId, sortAscending, startDate, endDate)` tuple and return
early when it matches and `transactionsJob?.isActive == true`. Leave the
`onPause`/`resetFilterAndSort` path alone — it legitimately changes the arguments.
**notes:** keep the `onResume` hook; it is the recovery path when the flow was cancelled while
stopped. Only make the duplicate call cheap.

---

### stop re-running the patient query (and its decrypt) on every health screen resume
**rating:** 83
**proposed by:** claude(1.0)
**verified:** `ui/health/MyHealthFragment.kt` reaches `setupInitialData()` (:117 → :238-239)
which calls `viewModel.loadInitialPatient()`; `HealthViewModel.loadInitialPatient` resolves the
user and calls `selectPatient(normalizedId)`. `MyHealthFragment.onResume` (:327-332) then calls
`viewModel.selectPatient(normalizedId)` for the same id, and `selectPatient` cancels the
in-flight job and redoes both `getPatientById` and `getPatientHealthRecords` — the second of
which decrypts the stored record.
**do:** track the currently loaded/loading patient id, return early when the requested id
matches and either the job is active or `_patientDetailState` already holds that record, and
clear the field on failure so retries work. Add an explicit `refreshSelectedPatient()` for the
realtime-sync and post-edit paths.
**notes:** leave `loadPatients`/`searchPatients` alone.

---

### batch the crash-log upload acknowledgement into one transaction
**rating:** 83
**proposed by:** claude(1.0)
**verified:** `services/upload/UploadConfigs.kt` `CrashLog.markUploaded` (:223) is
`results.filter { !diagnosticsRepository.markApkLogUploaded(result.localId, result.remoteRev) }`
— one suspend call per row into `ApkLogDao.markUploaded`, a bare `UPDATE` Room runs in its own
implicit transaction. `SubmitPhotosDao` (lines 26-43) already carries the exact pattern to copy
(`markUploadedBatchInternal` + `@Transaction markUploadedBatch` + an `UploadUpdate` projection).
**do:** add the partial-update projection and a `@Transaction markUploadedBatch` to `ApkLogDao`,
expose `markApkLogsUploaded(updates): Set<String>` on `DiagnosticsRepository` returning the ids
that were not applied, and rewrite `CrashLog.markUploaded` to one batched call.
**notes:** no schema change, so no `AppDatabase` version bump. Keep the single-row `markUploaded`.

---

### give ActivitiesRepository one call for a member's visit stats
**rating:** 83
**proposed by:** claude(1.0)
**verified:** `ui/voices/VoicesActions.kt:211-231` is a UI object that takes an
`ActivitiesRepository` parameter and issues two independent queries itself —
`getOfflineVisitCount(userModel.id)` (:223) and `getLastVisit(userModel.name)` (:224) — to fill
two strings on a member-detail fragment.
**do:** add `MemberVisitStats(offlineVisitCount, lastVisit)` next to `ProfileActivityStats` and
`getMemberVisitStats(userId, userName)` to `ActivitiesRepository`, implemented from the two DAO
calls the existing accessors already use. Consume it in `showMemberDetails`, keeping the
formatting and the `"No logout record found"` fallback in the UI, and keeping the function
signature unchanged so `BaseVoicesFragment:104` and `ReplyActivity:194` need no edit.
**notes:** do not remove `getOfflineVisitCount`/`getLastVisit` — `UserProfileViewModel:139` and
`DashboardViewModel:112` still call them.

---

### have NotificationsRepository compute the team chat count itself
**rating:** 82
**proposed by:** devin(1.0)
**verified:** `ui/teams/voices/TeamsVoicesViewModel.kt:58-60` loads the whole filtered news list
and passes `newsList.size` into `notificationsRepository.updateTeamNotification(teamId,
newsList.size)`. But `getTeamNotifications` compares the stored `lastCount` against
`voicesRepository.countTopLevelByTeam(teamId)` — a different metric — and
`NotificationsRepositoryImpl` already injects `voicesRepository`.
**do:** change `updateTeamNotification(teamId: String, count: Int)` to
`updateTeamNotification(teamId: String)`, compute
`voicesRepository.countTopLevelByTeam(teamId)` inside the impl, and drop the argument at the
call site. Update `TeamsVoicesViewModelTest` and `TeamChatBadgeIntegrationTest`.
**notes:** this is also a latent badge bug — writing filtered-list size into a counter that is
later compared against a top-level count can clear or re-raise the badge wrongly.

---

### delete the dead JsonObject rating aggregate API (and keep aggregation in SQL)
**rating:** 82
**proposed by:** kimi(0.75), codex(0.25)
**verified:** `RatingsRepository` exposes `getRatings`, `getCourseRatings`, `getResourceRatings`
returning `HashMap<String?, JsonObject>`. A full sweep finds **no production caller** — the only
callers are `RatingsRepositoryImpl` itself and `RatingsRepositoryImplTest` (:55, :85, :96); the
UI already consumes the typed `RatingSummary` path. They also load every row of a type via
`RatingDao.getByType` and reduce in Kotlin, while `RatingDao.getAggregate` (:24-27) already does
`COUNT`/`AVG` in SQLite for the single-item case.
**do:** delete the three methods plus the private `RatingAggregation`/`aggregateRatings` helpers
and update the test. If a bulk typed accessor is wanted instead of plain deletion, add
`getRatingSummariesByType(type, userId): Map<String, RatingSummary>` backed by one grouped
`SELECT item, COUNT(*), AVG(rate) … GROUP BY item` plus one query for the caller's own ratings.
**notes:** codex proposed optimising these methods rather than noticing they are unreachable;
prefer deletion. `RatingDao` is claimed by an open PR — check for a `deleteByIds` name clash.

---

### tighten the personals data surface
**rating:** 82
**proposed by:** claude(1.0)
**verified:** three loose ends. `PersonalsRepository.kt:22` declares
`suspend fun getPersonalResources(userId): Flow<List<Personal>>` — a `suspend` Flow accessor,
which the project's own rule forbids — and the impl (:47-55) suspends over nothing but a null
check and a DAO flow. `PersonalsRepository.kt:27` exposes `uploadPersonalDocument`, whose only
production caller is `uploadPersonal` inside the same impl (:100). `data/room/dao/PersonalDao.kt:37`
declares `@Update suspend fun update(item: Personal)` with zero callers in `main` or `test`.
**do:** drop `suspend` from the Flow accessor, remove `uploadPersonalDocument` from the
interface while keeping it a public non-override on the impl (its tests call it on the concrete
class at `PersonalsRepositoryImplTest:173-212`), and delete `PersonalDao.update` and its import.
**notes:** `PersonalsViewModel`'s `flow { emitAll(...) }` still compiles against a non-suspend
accessor.

---

### size the Glide course covers in CoursesAdapter
**rating:** 82
**proposed by:** grok(1.0)
**verified:** `ui/courses/CoursesAdapter.kt` `bindCover` (283-316) issues
`Glide.with(context).load(model).diskCacheStrategy(ALL).signature(...).centerCrop()
.error(...).into(ivCover)` with no `.override(w, h)` anywhere in the file, so full-resolution
covers are decoded for small grid/list thumbnails.
**do:** resolve a target size in px once (from the cover `ImageView`'s layout params or a
density-based constant shared by grid and list) and add `.override(...)`. Keep
`ObjectKey(course.courseRev)`, `DiskCacheStrategy.ALL`, `centerCrop` and the error drawable.
**notes:** scroll jank plus bitmap-memory spikes; ~15-30 lines.

---

### cancel the superseded progress query when swiping course steps
**rating:** 82
**proposed by:** claude(1.0)
**verified:** `ui/courses/TakeCourseFragment.kt` `updateStepDisplay` (193-210) launches an
uncancelled `viewLifecycleOwner.lifecycleScope.launch { viewModel.getCurrentProgress(...) }`, and
is called from the page-change callback (:140), `onResume` (:151) and the step-jump path (:313).
Swiping quickly stacks one query per swipe and whichever coroutine finishes last wins the write
to `binding.courseProgress` and `currentCourseProgress`.
**do:** hold the launch in a `progressJob`, cancel it before reassigning, and null the field in
`onDestroyView`. Leave the synchronous `tvStep`/`nextStep`/`courseStepProgressBar` writes at the
top of the function outside the coroutine so they stay immediate.
**notes:** ~25 lines, 1 file.

---

### read community leaders through ConfigurationsRepository instead of re-parsing prefs in the UI
**rating:** 81
**proposed by:** claude(1.0)
**verified:** `ConfigurationsRepository.getCommunityLeaders()` already owns this read
(`ConfigurationsRepositoryImpl.kt:405-406` is exactly
`UserEntity.parseLeadersJson(sharedPrefManager.getCommunityLeaders())`). Two fragments inline
the identical expression: `ui/voices/VoicesFragment.kt:248` and
`ui/teams/voices/TeamsVoicesFragment.kt:253`.
**do:** inject `ConfigurationsRepository` into both fragments and replace both
`leadersList = …parseLeadersJson(…)` expressions with `configurationsRepository.getCommunityLeaders()`
(non-suspend, so no coroutine change). Keep `sharedPrefManager` — both still call
`setRepliedNewsId` on it. Drop the `UserEntity` import only if it becomes unused.
**notes:** `ReplyActivity.kt:151` and `TeamsRepositoryImpl.kt:983` carry the same duplicate but
are claimed by open PRs.

---

### size the Glide previews in InlineResourceAdapter
**rating:** 80
**proposed by:** grok(1.0)
**verified:** `ui/courses/InlineResourceAdapter.kt` loads previews at :209
(`showImagePreview`), :223 (`showVideoPreview`) and :256 (`showHtmlPreview`); the file contains
no `.override(` at all. Course-step resource rows therefore decode full images and video frames
into thumbnail `ImageView`s. The PDF path is already sized.
**do:** add one private preview-decode-size helper/constant in px and apply `.override(...)` to
those three loads. Do not touch mime routing, existence checks or the PDF/audio/text paths.

---

### tighten the ResourcesRepository surface: no pass-through, no pure folds, one id resolver
**rating:** 80
**proposed by:** claude(1.0)
**verified:** three boundary problems. `ResourcesRepository.kt:53` exposes `markResourceAdded`,
whose impl (:307-309) is a one-line delegate to `activitiesRepository.markResourceAdded` with no
caller outside the impl (which calls it at :297). `ResourcesRepository.kt:95` exposes
`suspend fun getFilterFacets(libraries: List<MyLibrary>)`, implemented (:612-631) as a pure
in-memory fold over a caller-supplied list — no DAO, no API, no prefs — that
`ResourcesViewModel.kt:120-121` already wraps in `withContext(dispatcherProvider.default)`.
And `ui/resources/ResourceDetailFragment.kt:38-40` re-implements the repository's own
"id or resourceId" rule (`getLibraryItemById(id) ?: getLibraryItemByResourceId(id)`).
**do:** move the facet fold into `ResourcesViewModel.getFilterFacets` verbatim and delete the
repository copy; add `resolveLibraryItem(id)` to the interface/impl and collapse
`fetchLibrary` onto it; make the impl's `markResourceAdded` private.
**notes:** `ResourcesFragment.kt:690` calls `viewModel.getFilterFacets(...)` — that signature
does not change. Leave the impl's internal `getLibraryItemByResourceId(x) ?: getLibraryItemById(x)`
sites (:313, :342-343) alone; their order is deliberately the reverse of the fragment's.

---

### replace the team-notification read-modify-write with one UPDATE
**rating:** 80
**proposed by:** claude(1.0)
**verified:** `repository/NotificationsRepositoryImpl.kt:285-299` reads the whole row
(`teamNotificationDao.findByParentAndType`, a `SELECT * … LIMIT 1`), mutates `lastCount` in
Kotlin and writes it back through `@Update` — two round trips and a lost-update window for one
integer column. `findByParentAndType`'s only caller in `app/src` is this method.
**do:** add
`@Query("UPDATE team_notification SET lastCount = :count WHERE parentId = :parentId AND type = :type")
suspend fun updateCount(...): Int` and rewrite `updateTeamNotification` as update-then-insert:
if `updateCount` returns 0, build the row exactly as today and `insert`. Delete
`findByParentAndType` and the now-callerless `@Update update` plus its import.
**notes:** compose with the "compute the count in the repository" task above — both touch
`updateTeamNotification`.

---

### move Personal.serialize out of the Room entity into the personals repository
**rating:** 79
**proposed by:** grok(1.0)
**verified:** `model/Personal.kt` imports `FileUtils` (:8), `NetworkUtils` (:9) and
`addDocumentOrigin` (:10) purely to serve `companion fun serialize(personal, customDeviceName)`
(:36-49). Its only call site is `repository/PersonalsRepositoryImpl.kt:77`, which already has
`deviceNameProvider` injected.
**do:** move the serialize body into `PersonalsRepositoryImpl` as a private function with the
same JSON fields and order, point `uploadPersonalDocument` at it, and delete the companion
method plus the model's now-unused upload/network imports.
**notes:** direct roadmap-9 win — the entity ends up platform-free.

---

### memoize the JSON getters on News
**rating:** 78
**proposed by:** devin(0.65), grok(0.35)
**verified:** `model/News.kt:84-85` — `imagesArray` always runs
`JsonUtils.gson.fromJson(images, JsonArray::class.java)`, even though the entity already carries
`@Ignore var parsedImagesArray` (:79) and `@Ignore var rawImages` (:77) for exactly this, and
`VoicesAdapter.kt:639-641` already does the invalidation-aware memoization by hand
(`if (it.parsedImagesArray == null || it.rawImages != it.images)`). Separately
`isCommunityNews` (:111-124) re-parses `viewIn` and ignores `parsedViewIn`, while
`calculateSortDate` (:129) does prefer it.
**do:** make the `imagesArray` getter return `parsedImagesArray` when it is non-null **and**
`rawImages == images`, otherwise parse once, store both, and return; keep `JsonArray()` for a
null `images`. Apply the same `parsedViewIn`-first shape to `isCommunityNews`.
**notes:** the `rawImages` guard is load-bearing — `images` is a mutable var, so a cache without
it serves stale arrays. No schema change (`@Ignore` fields are not persisted).

---

### move MyLibrary.serialize (and its MainApplication.context reach) into ResourcesRepository
**rating:** 78
**proposed by:** grok(1.0)
**verified:** `model/MyLibrary.kt:12` imports `MainApplication.Companion.context` and the
companion `serialize(personal, user)` sits at :162; its only call site is
`services/upload/UploadConfigs.kt:279`. `MyLibrary.serializeResource()` (:89) is a different API
still used by the achievements UI.
**do:** add `serializeForUpload(library, user): JsonObject` to `ResourcesRepository`, implement
it with the injected device-name source (never `MainApplication.context`), point the
`getResourcesConfig` serializer at it, and delete the companion `serialize` and the
`MainApplication` import if nothing else needs it.
**notes:** clears a hard Application-context leak from a model used by sync and upload.

---

### evaluate the SyncPerf log gate once per sync instead of per event
**rating:** 78
**proposed by:** claude(1.0)
**verified:** `Log.isLoggable("SyncPerf", Log.DEBUG)` appears at `utils/SyncTimeLogger.kt`
:71, :86, :144, :164, :181, :192 and at `services/sync/SyncManager.kt` :77, :145, :215, :224.
`logApiCall`/`logDbOperation`/`logDetail` run per API call and per DB write, so one sync performs
thousands of system-property lookups, and the tag literal is duplicated ten times.
**do:** add a `TAG` constant and an `isVerbose` field in `SyncTimeLogger` that `startLogging()`
refreshes from `Log.isLoggable(TAG, DEBUG)`; replace all six gates with `if (isVerbose)`; gate
`SyncManager.syncPerf` on `syncTimeLogger.isVerbose` and route the three inline gates through
`syncPerf { … }`. Keep `stopLogging`'s summary write unconditional.
**notes:** deliberately snapshots loggability per session — pin that in a test.

---

### collapse the notification enrichment fan-out into NotificationsRepository
**rating:** 78
**proposed by:** kimi(0.5), grok(0.3), codex(0.2)
**verified:** `ui/notifications/NotificationsViewModel.loadNotifications` (73-137) partitions
payloads by type, collects task ids and parsed task titles and join-request ids, then
orchestrates parallel `async` calls to `getTaskTeamNamesByTaskIds`,
`getTaskTeamNamesByTaskTitles`, `getJoinRequestDetailsBatch`, the single-id
`getJoinRequestDetails` fallback and `getUnreadCount`. The interface exposes all of those
narrow cross-feature reads (`NotificationsRepository.kt:19-26`) purely to serve this one screen.
**do:** add one `getEnrichedNotifications(userId, filter, isAdmin)` returning a data class with
the payloads, `taskTeamNames`, `joinRequestDetails` and `unreadCount`; move the
partition/distinct/batch/parallel logic and `parseTaskDate` into the impl. Keep string-resource
formatting and grouping in the ViewModel. Delete the superseded interface methods only after a
sweep — `getTaskDetails` and `getJoinRequestTeamId` must stay for `NotificationsFragment:124-129`.
**notes:** do not edit `getTeamNotifications` in the same change; two other tasks touch it.

---

### move submission-list enrichment behind SubmissionsRepository
**rating:** 77
**proposed by:** codex(1.0)
**verified:** `ui/submissions/SubmissionViewModel.kt:67-85` groups submissions by `parentId`,
picks the newest per group, builds a count map, calls `userRepository.getUsersByIds(userIds)`
and joins fallback submitter names — cross-feature data assembly in a ViewModel.
**do:** add a platform-free list-row projection (submission + resolved submitter name + group
count) to `SubmissionsRepository` and one operation that filters by user/type/query, groups,
selects the newest, batch-resolves fallback users and returns sorted projections. Reduce the
ViewModel to combining filter inputs and mapping projections to `SubmissionUiModel`.
**notes:** keep `UserRepository` in the ViewModel only if active-user resolution needs it.

---

### stop ChatRepository.searchChats round-tripping the chat list through the caller
**rating:** 77
**proposed by:** kimi(1.0)
**verified:** `ChatRepository.kt:24` / `ChatRepositoryImpl.kt:157` take
`chats: List<ChatHistory>` as a parameter, and `ui/chat/ChatViewModel.kt:160` passes `allChats`
— a list the ViewModel got from `getChatHistoryForUser` — back in on every keystroke. The
repository owns `ChatDao`; the list parameter is a leaked caching concern.
**do:** change the signature to `searchChats(query, mode, userName: String?)`, and inside the
impl reuse the existing `chatDao.getByUser(userName)` path before the existing
`buildPrecomputedChats`/`searchByTitle`/`fullConvoSearch` pipeline. Keep the ViewModel's
fragment-facing API unchanged so `ChatHistoryFragment` needs no edit.
**notes:** consider a debounce/caching follow-up — this moves a per-keystroke DB read into the
repository, which is the right place to memoize it.

---

### reuse the memoized shared-team name and skip the label scan in voices filtering
**rating:** 77
**proposed by:** claude(0.8), openhands-glm(0.2)
**verified:** `ui/voices/VoicesViewModel.filterNews` (98-143) calls
`JsonUtils.extractSharedTeamName(news)` per item in the label branch and again in the label
collector (~:203). That helper (`utils/JsonUtils.kt:37-55`) falls back to
`gson.fromJson(news.viewIn, JsonArray::class.java)` when `parsedViewIn` is null, even though
`VoicesAdapter` memoizes the result into `News.parsedSharedTeamName` (:644) and reads the cache
first (:310, :329). `dynamicLabelDisplayToValue` (106-114) is rebuilt over every item's labels on
every invocation *before* `labelDisplayToValue[selectedLabel]` is consulted. And `lowerQuery`
(:137) is `query.trim().lowercase()` while all three `contains` calls (139-141) already pass
`ignoreCase = true`.
**do:** read `news?.parsedSharedTeamName ?: JsonUtils.extractSharedTeamName(news)` at both
sites; build the dynamic label map only when `labelDisplayToValue[selectedLabel]` is null; use
the trimmed query directly.
**notes:** openhands-glm found the redundant `lowercase()` only.

---

### write the server-config preference blocks in one commit instead of eight
**rating:** 76
**proposed by:** claude(1.0)
**verified:** `utils/ServerConfigUtils.saveAlternativeUrl` (:91-121) fires eight consecutive
`SharedPrefManager` setters, each its own `pref.edit { }` commit, and four of them
(`setUrlUser`, `setUrlPwd`, `setProcessedAlternativeUrl`, `setIsAlternativeUrl` —
`services/SharedPrefManager.kt:166-203`) additionally call `UrlUtils.invalidateCaches()`.
`ui/sync/ProcessUserDataActivity.setUrlParts` (:120-150) does the same with seven setters and
three invalidations. `services/UserSessionManager.saveUserInfoPref` (:32-46) adds two
single-key commits before its own batched `edit`.
**do:** add one batching entry point to `SharedPrefManager` (or two narrow batched setters that
keep the keys private) that performs a single `pref.edit { }` then invalidates once; rewrite the
two call sites to one batched call each; fold `setUserId`/`setUserName` into
`saveUserInfoPref`'s existing `edit` block.
**notes:** beyond the redundant writes, the interleaved invalidations mean anything reading
`UrlUtils.header` mid-sequence rebuilds the auth header from a half-written config. Claude's
"five invalidations" count is one high for `saveAlternativeUrl` (`setCouchdbUrl` is not called
there); the rest checks out. Do not move any secret or change credential handling.

---

### peel WorkManager and SystemClock out of SyncRepositoryImpl
**rating:** 76
**proposed by:** devin(0.7), grok(0.3)
**verified:** `repository/SyncRepositoryImpl.kt` imports `androidx.work.OneTimeWorkRequest`,
`WorkInfo`, `WorkManager` (:7-9), builds the request at :68, calls
`WorkManager.getInstance(context)` at :71 and maps `WorkInfo` states at :82-88. It also reads
`SystemClock.elapsedRealtime()` at :159, :169, :176, :200, :205 and :209, and `TimeProvider`
(`utils/TimeProvider.kt:11-16`) exposes only `now()` and `sleep()`.
**do:** add `elapsedRealtime()` to `TimeProvider`/`SystemTimeProvider`/`TestTimeProvider` and
replace all six `SystemClock` calls; move the enqueue + `WorkInfo`→`SyncUiState` mapping into a
new `UserDataUploadScheduler` injected into the repository, so `Context`, `WorkManager` and
`SystemClock` all leave the file. Keep `SyncRepository`'s `Flow<SyncUiState>` API and the unique
work names unchanged.
**notes:** update `SyncRepositoryImplTest` to drop the `Context`/`SystemClock` static mocks.

---

### remove the Room upload type from PhotoUploader
**rating:** 76
**proposed by:** codex(1.0)
**verified:** `services/upload/PhotoUploader.kt:14` imports
`org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao.UploadedPhoto` and constructs it at :76
(`successfulUploads.map { UploadedPhoto(it.photoId, it.rev, it.id) }`) before handing it to
`submissionsRepository.markPhotosUploadedBatch` (:77). A DAO-owned type crossing the service
boundary.
**do:** have the repository own the successful-upload value type; map network responses to it in
`uploadSubmitPhotos` and call the repository once per non-empty batch, keeping the DAO type
private behind the repository. Preserve cancellation, the concurrency limit, attachment ordering
and the listener callbacks.
**notes:** the file already has its own `UploadedPhotoInfo` data class (:41) that can become the
repository-facing shape.

---

### filter individual surveys in Room instead of in the repository
**rating:** 76
**proposed by:** codex(1.0)
**verified:** `repository/SurveysRepositoryImpl.kt:268-270` is
`examDao.getByType("surveys").filter { !it.isTeamShareAllowed && it.teamId.isNullOrEmpty() }` —
every survey row crosses the data boundary so two flags can be checked in Kotlin.
**do:** add a DAO query constrained to `type = 'surveys' AND isTeamShareAllowed = 0 AND
(teamId IS NULL OR teamId = '')` and delegate to it with no Kotlin filter. Match Room's stored
boolean representation and preserve current row ordering.
**notes:** keep the general `getByType` — other operations still need all surveys.

---

### share one attachment-presence cache between the two enterprise adapters
**rating:** 76
**proposed by:** claude(1.0)
**verified:** `ui/enterprises/EnterprisesFinancesAdapter.kt` (:33-41, :76-81) and
`EnterprisesReportsAdapter.kt` (:27-35, :112-117) each carry a byte-identical hand-rolled
`HashMap<String, Pair<Boolean, Long>>` + `cacheTtlMs = 5000L` cache around `File.exists()`, and
each wipes it wholesale in `onCurrentListChanged`. Both screens call `submitList` on every
filter, sort and realtime-sync tick, so the blanket clear throws away a still-valid cache and
forces a synchronous `File.exists()` per visible row on the binding thread.
**do:** extract `utils/AttachmentPresenceCache` taking a `TimeProvider` and a TTL, exposing
`exists(file: File?): Boolean` (false for null, serve non-expired, else stat and store); use it
from both `bindFinanceImage` and `bindReportImage`; delete both `onCurrentListChanged` overrides
— the TTL already bounds staleness.
**notes:** do not move the stat off the binding thread in this change.

---

### align ChatDao.getByUser with the repository-owned ordering
**rating:** 75
**proposed by:** kimi(1.0)
**verified:** `data/room/dao/ChatDao.kt:12` carries `ORDER BY id DESC`, and
`ChatRepositoryImpl.getChatHistoryForUser` (:130-136) immediately re-sorts via `sortChats`
(:138-142) by `max(createdDate, updatedDate)`. The SQL ordering is dead weight that disagrees
with the real ordering and invites future callers to trust it.
**do:** remove `ORDER BY id DESC` and note in a comment that `sortChats` owns ordering (the
date columns are string-typed, so a SQL ordering would not be correct). Assert in
`ChatRepositoryImplTest` that output order follows `sortChats` given an intentionally reversed
DAO result.
**notes:** ~20 lines. Avoid adding a `deleteByIds` name here — an open PR claims it.

---

### stop the achievement UI from loading the entire library table
**rating:** 74
**proposed by:** grok(1.0)
**verified:** `ui/user/AchievementViewModel.kt:85-87` — `getAllLibraries()` proxies straight to
`resourcesRepository.getAllLibraries()` (`ResourcesRepository.kt:38`), i.e. `myLibraryDao.getAll()`,
for the `EditAchievementFragment` resource picker. `getPublicLibraryItems()` already exists
(`ResourcesRepository.kt:103`).
**do:** back the ViewModel method with `getPublicLibraryItems()`, keeping the name and the
`List<MyLibrary>` return type so the single caller and its `serializeResource()` use still compile.
**notes:** **behaviour change** — the picker would offer public items only. Confirm that is the
intent before shipping; if not, add a narrower picker query instead.

---

### replace CouchDB skip paging in the resource sync
**rating:** 74
**proposed by:** grok(1.0)
**verified:** `services/sync/SyncManager.kt:329` pages with
`_all_docs?include_docs=true&limit=$batchSize&skip=$skip`, advancing `skip` at :339, :383 and
:406 and reporting progress from it (:394-398). CouchDB `skip` is O(skip) per request, so cost
grows with the resource table.
**do:** track the last row's `id` and page with `startkey` (plus `skip=1` on restart to avoid
re-emitting the boundary row); stop when a page returns fewer rows than requested. Keep
`AdaptiveBatchProcessor`, failure handling, cleanup and the progress/`ResourceSyncPosition`
semantics ("docs processed"). URL-encode keys.
**notes:** highest ceiling in the sync path but the riskiest item here — needs careful
duplicate-insert testing. Consider extracting a private URL builder to make paging unit-testable.

---

### hoist UserInformationFragment's repository access into a ViewModel
**rating:** 74
**proposed by:** claude(1.0)
**verified:** `ui/exam/UserInformationFragment.kt` is a `BaseDialogFragment` injecting
`submissionsRepository` (:42) and `userRepository` (:44) and driving them from
`viewLifecycleOwner.lifecycleScope` at :161-163 (`userRepository.updateProfileFields`) and
:258-271 (`submissionsRepository.markSubmissionComplete`). Both writes die with the dialog on
rotation, and this is the only screen in `ui/exam/` with no ViewModel.
**do:** add `UserInformationViewModel` (`@HiltViewModel`, both repositories) exposing
`updateProfile(...)` and `markSubmissionComplete(...)` launched in `viewModelScope` and emitting
outcomes on a result flow; collect it in the fragment and keep every toast/`dismiss()` branch
there. Delete the two `@Inject` fields.
**notes:** keep `userSessionManager`, `sharedPrefManager`, `submissionsUploader` and
`createUserProfile()` in the fragment. ~90 lines.

---

### drop LifeViewModel's @ApplicationContext and its bespoke user-id fallback
**rating:** 74
**proposed by:** kimi(0.6), grok(0.4)
**verified:** `ui/life/LifeViewModel.kt:21` injects `@ApplicationContext context` solely for
`MyLife.defaultItems(userId, context::getString)` (:41), and `resolveUserId()` (:31-36)
re-implements active-user resolution as
`getCurrentUserId().orEmpty().ifEmpty { getUserModel()?.id.orEmpty() }` instead of the shared
`UserRepository.getActiveUserIdSuspending()` (`UserRepository.kt:77`) other ViewModels use.
`model/MyLife.kt` also imports generated `R` for its default-item labels.
**do:** replace `resolveUserId()` with `getActiveUserIdSuspending()`, and pass the label resolver
in from `LifeFragment` (`requireContext()::getString`) so the lambda, not a `Context`, crosses
into `loadMyLifeList` — then delete the constructor `Context`. Optionally follow up by moving
`MyLife`'s default catalogue to stable keys so the entity stops importing `R`.
**notes:** grok's variant also targets the `R` import but specifies three alternative shapes
without choosing; take the ViewModel half first, keep the entity change as a separate step.

---

### collapse NotificationsViewModel's repeated collection passes and delete the dead extension
**rating:** 74
**proposed by:** devin(0.65), codex(0.35)
**verified:** `ui/notifications/NotificationsViewModel.kt:77-83` hand-rolls a loop partitioning
`payloadNotifications` into task and join-request lists; :85-98 then build three lists and call
`distinct()` on each. And `private fun List<Notification>.markAsRead(id: String)` (:254-256) is
dead — both call sites (:186, :244) pass a `Set` and hit the `Set` overload at :257.
**do:** replace the loop with a single `partition`, accumulate the three id/title collections
into insertion-ordered unique sets instead of list-then-`distinct()` (reusing `parsedTaskDates`
for the titles), collapse the count-then-rebuild double traversals in `markSelectedAsRead`/
`deleteSelected`/`markAllAsRead`, and delete the dead extension.
**notes:** keep the parallel `async` lookup structure and the formatted notification order.

---

### stop scanning the whole tag table to build the parent→children map
**rating:** 73
**proposed by:** kimi(0.8), codex(0.2)
**verified:** `repository/TagsRepositoryImpl.getTagsWithChildren` (:17-39) calls
`getParentTags(dbType)` and then `tagDao.getAll()` — every row in `tag` — and rebuilds the child
map in Kotlin. `TagDao` has scoped queries (`getByDbAndLinkIds`, `getByIds`) but nothing scoped
for attached rows. The `if (list.isEmpty() || list.last() !== t)` guard at :29 is near-dead: each
tag is visited once per parent id, so it only ever fires for a duplicate id inside one tag's
`attachedTo`.
**do:** add `getAttachedForDb(db)` — `SELECT * FROM tag WHERE isAttached = 1 AND (:db IS NULL OR
db = :db)` — and read parents plus attached rows instead of `getAll()`. Keep the Kotlin grouping
(the multi-valued `attachedTo` JSON column cannot be joined in SQL) but replace the reference-
equality guard with an id-set guard so duplicate `attachedTo` entries behave identically.
**notes:** codex cited this as `getTagsForIds`, a function that does not exist; the line and the
smell are right, the name is not. `TagEntity` needs no change. Kimi proposed this in both
rounds (p1 #9 = the dead guard and the nested loop; p2 #6 = the scoped query); its 0.8 share is
the two submissions combined, not double credit.

---

### split file I/O from pure parsing in the dictionary seed path
**rating:** 72
**proposed by:** devin(0.55), grok(0.45)
**verified:** `repository/DictionaryRepositoryImpl.insertDictionaryData` (:41-70+) mixes
`FileUtils.checkFileExist(context, …)` (:43), `FileUtils.getStringFromFile(
FileUtils.getSDPathFromUrl(context, …))` (:53-55), the `JsonArray` parse (:56), entity mapping
(:58-66) and the DAO insert in one method holding an Android `Context`.
**do:** extract a `DictionaryAssetDataSource` (`@ApplicationContext`, exposing
`isDictionaryAssetPresent()` / `readDictionaryAssetText()`) so the repository loses `Context`
and `FileUtils`, and extract a pure `JsonArray → List<DictionaryEntity>` mapper (keeping the
existing `antonoym` key spelling). `insertDictionaryData()` then only orchestrates:
missing file → `FileMissing`, `count > 0` → `AlreadyPopulated`, else parse + `insertAll`.
**notes:** keep the `insertDictionaryData()` signature so `DictionaryViewModel` is untouched.

---

### hoist team-member load and mutations out of MembersFragment into RequestsViewModel
**rating:** 72
**proposed by:** grok(1.0)
**verified:** `ui/teams/members/MembersFragment.kt` makes eight direct repository calls —
`getJoinedMembersWithVisitInfo` (:97), `getNextLeaderCandidate` (:112, :132),
`updateTeamLeader` (:113, :134, :153), `removeMember` (:114, :140) — from
`viewLifecycleOwner.lifecycleScope`, while `RequestsViewModel` already exists for the same
screen and already injects `TeamsMembersRepository` (which owns all four of those methods).
**do:** extend `RequestsViewModel`/`RequestsUiState` with the joined-member list and leader flag,
add ViewModel methods wrapping those repository calls (plus `recordTeamActivity` where it
applies), and reduce the fragment to collecting state and calling the ViewModel; keep dialogs,
toasts and navigation in the fragment.
**notes:** the request-list paths (`fetchMembers`, `respondToRequest`) stay as they are.

---

### join meetup members in Room instead of assembling identities in Kotlin
**rating:** 72
**proposed by:** codex(1.0)
**verified:** `repository/EventsRepositoryImpl.getJoinedMembers` (:66-79) fetches ids via
`meetupDao.getMemberUserIdsByMeetupId`, filters blanks, `distinct()`s, chunks by 400, calls
`userDao.getUsersByAnyIds` per chunk and `distinctBy { it.id }`es the result — a relationship
the data query should express.
**do:** add a DAO query joining `meetup` to `users` on either identifier, returning distinct user
rows, with the null/blank membership exclusions kept in SQL; reduce `getJoinedMembers` to a blank
check plus one delegation, and drop the now-unused `UserDao` injection.
**notes:** verify duplicate membership rows still yield one displayed member.

---

### cache the version code and name lookups in VersionUtils
**rating:** 72
**proposed by:** openhands-glm(1.0)
**verified:** `utils/VersionUtils.kt` `getVersionCode` (:17) and `getVersionName` (:32) each call
`context.packageManager.getPackageInfo(context.packageName, 0)` — a PackageManager IPC — with no
caching, while the same object already caches `cachedAndroidId` (:14-15) behind `@Volatile`.
Callers include `MyPlanet.addStats` (:91-92) and `ConfigurationsRepositoryImpl` version checks.
**do:** add `@Volatile` `cachedVersionCode`/`cachedVersionName` next to `cachedAndroidId`, cache
on the success path only (a `NameNotFoundException` must keep returning `0`/`""` uncached), and
add a `@VisibleForTesting` reset mirroring `resetAndroidIdCacheForTesting`.

---

### reuse the cached system-service managers in NetworkUtils.getCurrentNetworkId
**rating:** 72
**proposed by:** openhands-glm(1.0)
**verified:** `utils/NetworkUtils.kt` already caches `connectivityManager` (:56-60) and
`wifiManager` (:62-66) through `ResettableCache`, but `getCurrentNetworkId(context)` (:221)
ignores both — `context.getSystemService(Context.CONNECTIVITY_SERVICE)` at :223 and
`context.applicationContext.getSystemService(Context.WIFI_SERVICE)` at :230.
**do:** use the cached fields in both branches, keeping the `@Suppress("DEPRECATION")` on
`connectionInfo`, and keep the `context` parameter on the signature so the caller compiles
unchanged.

---

### cache SharedPrefManager.getSavedUsers
**rating:** 72
**proposed by:** grok(1.0)
**verified:** `services/SharedPrefManager.kt:73-80` — `getSavedUsers()` runs
`gson.fromJson(usersJson, userListType)` on every call, and `setSavedUsers` (:82-84) rewrites the
JSON without keeping anything in memory. The manager is a `@Singleton` and login/team/user flows
read it repeatedly.
**do:** add a nullable in-memory cache field; serve it from `getSavedUsers` when present,
otherwise parse once and store; update it in `setSavedUsers` (and invalidate in any other path in
this file that writes the `SAVED_USERS` key).
**notes:** the cache is per-process — fine here, since `setSavedUsers` is the only writer in the
class. Do not extend this to other pref keys or to `SecurePrefs`.

---

### share the succeeded-item attachment path across UploadManager's two result branches
**rating:** 71
**proposed by:** codex(1.0)
**verified:** `services/UploadManager.uploadResource` (:173-203) contains two byte-identical
blocks — one under `UploadResult.Success` over `result.items`, one under
`UploadResult.PartialSuccess` over `result.succeeded` — each mapping ids, calling
`resourcesRepository.getLibraryItemsByIds`, building an id map and iterating `uploadAttachment`.
**do:** extract one private helper taking the succeeded items and the listener; return early for
a null listener or empty collection before allocating ids or querying; fetch and index libraries
once per result. Preserve each branch's listener message and exception behaviour.
**notes:** stops the two outcomes from drifting apart; ~60 lines including tests.

---

### extract the MyLife SharedPreferences cache into a data source (and hoist its TypeToken)
**rating:** 71
**proposed by:** devin(0.75), openhands-glm(0.25)
**verified:** `repository/LifeRepositoryImpl.kt` reads and writes a `CachedMyLifeItem` JSON cache
directly against `sharedPrefManager.rawPreferences` (:109-140), which is what pulls
`androidx.core.content.edit` and Gson into the repository, and it allocates a fresh
`object : TypeToken<List<CachedMyLifeItem>>() {}.type` on every cache hit (:112) — while
`data/room/Converters.kt` already hoists its `TypeToken`s to companion vals.
**do:** move `CachedMyLifeItem`, `MY_LIFE_CACHE_PREFIX` and the read/write logic into a
`MyLifeCacheDataSource(sharedPrefManager, gson)` exposing `get(userId)` / `set(userId, items)`,
with the type token as a single hoisted val. The repository then keeps `SharedPrefManager` only
for the `getUserId()` fallback.
**notes:** openhands-glm proposed only the token hoist, which this change subsumes.

---

### delete the dead onCreate and the never-read dialog binding in MyHealthFragment
**rating:** 70
**proposed by:** kimi(1.0)
**verified:** `ui/health/MyHealthFragment.kt:77-79` is an `onCreate` override whose body is only
`super.onCreate(...)`. `alertMyPersonalBinding` is declared at :65 and assigned at :94
(`AlertMyPersonalBinding.inflate(...)` in `onViewCreated`) — and those are its **only two**
occurrences in the file; nothing ever reads it. So every view creation on a hot dashboard tab
inflates a layout that is then discarded.
**do:** delete the empty override and delete the field and its inflation outright (plus the
import if unused). Leave `alertHealthListBinding` alone.
**notes:** kimi prescribed `by lazy`, on the belief that dialog call sites exist below line 120 —
they do not. Deletion is the correct fix and is strictly smaller.

---

### tokenize and reduce ResourcesListFilter tag matching to set membership
**rating:** 70
**proposed by:** grok(1.0)
**verified:** `ui/resources/ResourcesListFilter.kt:55-62` — `filteredList.filter { model ->
tags.any { searchTag -> model.tags.any { it.id == searchTag.id } } }`, i.e. O(models ×
searchTags × modelTags) on every keystroke and every filter apply.
**do:** precompute `searchTagIds` as a `Set` once when `tags.isNotEmpty()` and test
`model.tags.any { it.id in searchTagIds }`. Preserve the any-of semantics exactly and leave the
facet/download logic and `filterIfChanged`'s signature short-circuit alone.

---

### drop the redundant Kotlin sort and factor the repeated dedupe chain in LifeRepositoryImpl
**rating:** 70
**proposed by:** devin(0.55), codex(0.45)
**verified:** `.distinctBy { it.dedupKey() }.sortedBy { it.weight }` appears at
`repository/LifeRepositoryImpl.kt:82`, :90, :95 and :160 (four sites, not the five devin
claimed — :135 is `filter { it.isVisible }.sortedBy { it.weight }`). Both
`MyLifeDao.getByUserId` and `getVisibleByUserId` **already** end in `ORDER BY weight ASC`, and
`distinctBy` preserves encounter order, so every one of those `sortedBy` calls is a no-op.
**do:** delete the redundant `sortedBy { it.weight }` calls and extract the remaining
`distinctBy { it.dedupKey() }` into one private helper used by all sites.
**notes:** codex's step 1 asks to *add* the `ORDER BY` clauses that are already there; its
step 3 (remove the now-guaranteed Kotlin sorting) is the correct half. Devin's helper extraction
alone would have preserved the dead sort inside the helper.

---

### introduce a CommunityServicesViewModel for team-link data access
**rating:** 70
**proposed by:** grok(1.0)
**verified:** `ui/community/CommunityServicesFragment.kt` calls `teamsRepository.getTeamLinks()`
inside `viewLifecycleOwner.lifecycleScope.launch` at :60-61 and
`teamsRepository.isMember(user?.id, route.teamId)` in a click handler at :98-99 — repository
access straight from the view layer.
**do:** add a `@HiltViewModel CommunityServicesViewModel` injecting the teams repository (and
`UserRepository` if it needs the user id), exposing link state and a membership query; have the
fragment collect state and ask the ViewModel for membership. Keep routing, WebView intents and
markdown rendering in the fragment.
**notes:** needs no DI module change.

---

### use a DAO projection for the enterprise report CSV export
**rating:** 70
**proposed by:** grok(1.0)
**verified:** `repository/EnterprisesRepositoryImpl.exportReportsAsCsv` (:102-103) calls
`teamDao.getNonArchivedReportsByTeamId`, which is `SELECT * FROM teams WHERE …`
(`TeamDao.kt:24`), but the CSV only uses `startDate`, `endDate`, `createdDate`, `updatedDate`,
`beginningBalance`, `sales`, `otherIncome`, `wages`, `otherExpenses`.
**do:** add a small projection data class and a `TeamDao` query selecting just those columns with
the same non-archived filter and `ORDER BY createdDate DESC`, and switch the export to it. Leave
`observeNonArchivedReportsByTeamId` and the list UI on full entities.
**notes:** CSV text must stay byte-identical.

---

### query exact course-progress keys instead of a three-way IN cross product
**rating:** 70
**proposed by:** codex(1.0)
**verified:** `data/room/dao/CourseProgressDao.kt:25-26` —
`WHERE courseId IN (:courseIds) AND userId IN (:userIds) AND stepNum IN (:stepNums)` — returns
every combination of the three sets, not the requested tuples. The caller
(`ProgressRepositoryImpl.kt:299-306`) then groups by `Triple(courseId, userId, stepNum)` and
looks up only the tuples it wanted, so the surplus rows are pure over-read (and a cross-user row
would be paired only by that Triple guard).
**do:** deduplicate the requested tuples and query them exactly. Prefer a single bounded query
whose predicate is an OR-of-tuples (or an indexed key column) over the existing per-row
`findByCourseUserAndStep`. Preserve null-user behaviour, and delete
`getByCourseUsersAndSteps` if nothing else calls it.
**notes:** do **not** naively fan out to one query per tuple — that trades an over-read for N
round trips in a sync path.

---

### give EnterprisesRepositoryImpl an attachment-file provider instead of Context
**rating:** 68
**proposed by:** devin(1.0)
**verified:** `repository/EnterprisesRepositoryImpl.kt:20` injects `@ApplicationContext context`
and the only use in the file is :130,
`MyTeam.getAttachmentFile(context, teamId, imageName)` inside `attachTeamImage`.
**do:** add `TeamAttachmentFileProvider` (`@ApplicationContext`) exposing
`getAttachmentFile(teamId, imageName): File?`, inject it, and drop `Context` from the
repository's constructor.
**notes:** leave `MyTeam` untouched.

---

### add a composite index for the open-tasks assignee scan
**rating:** 68
**proposed by:** openhands-deepseek(1.0)
**verified:** `data/room/dao/TeamTaskDao.kt:11` — `SELECT * FROM team_tasks WHERE (status IS
NULL OR status != 'archived') AND completed = 0 AND assignee IS :userId`, a `Flow` re-run on
every dashboard open and on the teams task page. `model/TeamTask.kt:10` declares
`indices = [Index("teamId")]` only. `AppDatabase.kt:130` is `version = 12` and `RoomModule`
builds with `fallbackToDestructiveMigration(true)`.
**do:** add `Index(value = ["assignee", "completed", "status"])` alongside `Index("teamId")` and
bump `AppDatabase` to `version = 13`.
**notes:** the version bump is **mandatory** — Room rejects a changed schema at an unchanged
version. And per the project's own warning, a destructive rebuild discards unsynced local
writes, so land it in a release where pending uploads have been flushed.

---

### fix the message_placeholder misuse in CommunityLeadersAdapter
**rating:** 68
**proposed by:** kimi(1.0)
**verified:** `ui/community/CommunityLeadersAdapter.kt:41` calls
`context.getString(R.string.message_placeholder, leader)` — passing a whole `UserEntity` as the
`%s` argument (`values/strings.xml:1006` is `<string name="message_placeholder">%s</string>`).
`UserEntity.toString()` returns `"$name"` (`model/UserEntity.kt:184-186`), so the `else` branch
renders the username — exactly what the `if (leader.firstName == null)` branch already renders.
Both branches produce the same text, so the intended full-name display never happens.
**do:** replace the branch with direct composition, e.g. `if (leader.firstName == null)
leader.name else listOfNotNull(leader.firstName, leader.lastName).joinToString(" ")`, keeping
coherence with `showLeaderDetails` below. Do not edit `strings.xml`.
**notes:** kimi described the output as raw `toString()` garbage; the real symptom is milder (a
username where a full name was intended), but the dead branch is real.

---

### hoist the storage-scan state out of StorageBreakdownFragment into a ViewModel
**rating:** 68
**proposed by:** kimi(1.0)
**verified:** `ui/settings/StorageBreakdownFragment.kt` owns the scan state machine in the view
layer: `loadJob: Job?` (:45), an internal `CategoryData` with `var sizeBytes`/`var fileCount`
(:47-51), and the scan launched inline at :173-181 via `loadJob?.cancel()` +
`viewLifecycleOwner.lifecycleScope.launch`. State is lost on recreation and the scan is only
reachable through fragment tests. `ui/settings/StorageCategoryViewModel.kt` already exists as a
pattern to follow.
**do:** move `ScanResult`, `scanStorage(File)`, the free-space fetch and the
loading/empty/content tri-state behind a `StateFlow<StorageBreakdownUiState>` in a ViewModel;
make `CategoryData` immutable; reduce the fragment to collecting the flow and keeping the
`freeUpSpace()` WorkManager orchestration. Move the pure scan tests onto the ViewModel.
**notes:** compose with the `indexOf` fix below — both touch `scanStorage`.

---

### accumulate offline resource metadata in one filesystem pass
**rating:** 68
**proposed by:** codex(1.0)
**verified:** `repository/ResourcesRepositoryImpl.getOfflineResourceItems` (:856-874) groups
every matching `File` into `mutableMapOf<String, MutableList<File>>`, then maps each group again
to `sumOf { it.length() }` and `map { it.absolutePath }` — retaining `File` objects for a second
pass on a whole-tree walk.
**do:** accumulate absolute paths and a running byte total per resource id during the walk, then
convert once to `OfflineResourceItem`. Preserve the title fallback, the final title sort, the
missing-directory behaviour and `dispatcherProvider.io`.

---

### index the collection tags once when reconciling selections
**rating:** 68
**proposed by:** devin(0.65), codex(0.35)
**verified:** `ui/resources/CollectionsFragment.kt:70-73` builds
`allTags = list + childMap.values.flatten()` and then calls `allTags.find { selected.matches(it) }`
for every selected tag; `buildTagDataList` additionally runs
`selectedItemsList.any { it.matches(parentTag) }` (:135) and
`selectedItemsList.any { it.matches(childTag) }` (:142) inside the parent and child loops.
**do:** build one id-keyed map from the parent and child tags in a single pass and reconcile
`selectedItemsList` through it (preserving order and keeping the prior object when no loaded
match exists), and derive a `Set` of selected ids once before the loops for both selected checks.
Keep `TagEntity.matches` identity semantics and leave adapter submissions where they are.

---

### remove MainApplication.context from CourseDetailViewModel
**rating:** 68
**proposed by:** jules(1.0)
**verified:** `ui/courses/CourseDetailViewModel.kt:12` imports `MainApplication` and :67 uses
`MainApplication.context.getExternalFilesDir(null)` to build the markdown image base url — a
global static reached from a ViewModel.
**do:** inject `@ApplicationContext context: Context` through the constructor, use it at :67 and
drop the `MainApplication` import.

---

### give ConfigurationsRepositoryImpl a path resolver instead of Context + FileUtils
**rating:** 66
**proposed by:** devin(1.0)
**verified:** `repository/ConfigurationsRepositoryImpl.kt:249` calls
`FileUtils.getSDPathFromUrl(context, path)` and :416 builds `File(FileUtils.getOlePath(context))`,
which is what forces the `FileUtils` and `java.io.File` imports into an otherwise
configuration-only repository.
**do:** add `StoragePathResolver` (`@ApplicationContext`) with `resolveFileFromUrl(url): File`
and `resolveOleDirectory(): File`, inject it, and replace both call sites.
**notes:** the existing `FileUtils` object mocks in the test still work — the resolver delegates.

---

### inject an AppVersionProvider into DiagnosticsRepositoryImpl
**rating:** 66
**proposed by:** devin(1.0)
**verified:** `repository/DiagnosticsRepositoryImpl.kt` imports generated `BuildConfig` (:5) and
reads `BuildConfig.VERSION_NAME` at :62 and :80 when building `ApkLog` records.
**do:** add a tiny `AppVersionProvider` whose `versionName` reads `BuildConfig.VERSION_NAME`,
inject it, replace both references and drop the import.
**notes:** smallest of the "get generated Android types out of repositories" set; do it with the
other two provider extractions in one review pass.

---

### de-JSON the achievement edit flow between ViewModel and repository
**rating:** 66
**proposed by:** kimi(1.0)
**verified:** `ui/user/AchievementViewModel.kt:22-35` declares `AchievementSaveRequest` with
`achievements: JsonArray`, `references: JsonArray` and `profileFields: JsonObject`, and
`UserRepository.updateProfileFields` (:64) / `updateAchievement` (:85) take those Gson types
straight through. `EditAchievementFragment` mutates raw `JsonArray`s as its editing model.
**do:** move to typed shapes — `achievements: List<String>` (already the on-disk shape;
`Achievement.achievements` is `List<String>?` serialized via `parseStringListToJsonArray`) and a
`Map<String, String>` for profile fields — doing the `JsonArray`/`JsonObject` wrapping inside
`UserRepositoryImpl` where the Couch document is built. Replace the fragment's array
accumulators with `MutableList<String>`.
**notes:** keep a deprecated `JsonObject` overload of `updateProfileFields` while
`UserInformationFragment:163` still calls it. Largest item in this group — split if it exceeds
the size budget.

---

### short-circuit prependBaseUrlToImages when the markdown has no image markup
**rating:** 66
**proposed by:** openhands-glm(1.0)
**verified:** `utils/MarkdownUtils.prependBaseUrlToImages` (:82-90+) always allocates a
`StringBuilder` and runs `imagePattern.matcher(content)` over the whole description, then
`toString()`s a copy of the input even when nothing matched. It runs on every emission of
`CourseDetailViewModel` (:65) and `CoursesStepsViewModel` (:76), where image-free descriptions
are the common case.
**do:** after the existing null guard, `if (!content.contains("![")) return content`. Leave the
matcher loop untouched.

---

### aggregate the sync timing summary without flattening the log maps
**rating:** 66
**proposed by:** codex(0.5), devin(0.5)
**verified:** `utils/SyncTimeLogger.generateSummary` builds
`allApiCallLogs = apiCallTimes.values.flatten()` and `allDbOpLogs = dbOperationTimes.values
.flatten()` (:229-230), then walks those copies at :256-257 (`sumOf`, `count`) and :277-278, and
sums them **again** at :297 and :300 for the percentage section.
**do:** delete both `flatten()` snapshots, fold the per-endpoint/per-model lists directly for
total duration, success count and item totals, and reuse those aggregates for the percentage
section instead of re-summing. Preserve the ranking, formatting, labels, rounding and empty-log
behaviour exactly.

---

### reuse one thread-safe java.time formatter in SubmissionsRepositoryExporter
**rating:** 66
**proposed by:** kimi(1.0)
**verified:** `repository/SubmissionsRepositoryExporter.kt:34` holds
`ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }`, used
exactly once at :175 for the PDF "Generated:" stamp.
**do:** replace it with a single immutable
`DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault()).withZone(ZoneId
.systemDefault())` and format `Instant.ofEpochMilli(...)` at the call site; drop the
`SimpleDateFormat` import (check `Date`/`Locale` first).
**notes:** header text must stay `yyyy-MM-dd HH:mm`.

---

### replace the ThreadLocal SimpleDateFormat in VoicesActions with java.time
**rating:** 64
**proposed by:** kimi(1.0)
**verified:** `ui/voices/VoicesActions.kt:33` holds
`ThreadLocal.withInitial { SimpleDateFormat("MMMM dd, yyyy hh:mm a", Locale.getDefault()) }`,
used at :224 to render the member-detail "last visit" string.
**do:** replace with one `DateTimeFormatter` (system zone) and format
`Instant.ofEpochMilli(it)`; drop the `Date`/`SimpleDateFormat` imports if unused.
**notes:** month names are locale-sensitive — build the formatter lazily or read
`Locale.getDefault()` at call time so a mid-process locale change is still honoured. Touches the
same function as the `MemberVisitStats` task; sequence them.

---

### make StorageCategories.indexOf case-insensitive and drop the duplicate lookup
**rating:** 64
**proposed by:** devin(0.5), grok(0.3), openhands-glm(0.2)
**verified:** `ui/settings/StorageBreakdownFragment.kt:227-231` classifies each file as
`indexOf(ext)` and, when that returns `OTHER_INDEX`, `indexOf(ext.lowercase())` — two map
lookups for every uppercase or genuinely-other extension, on a whole-tree walk.
`StorageCategories.extensionToIndex` (`StorageCategories.kt:23-29`) is keyed by lowercase
extensions and `indexOf` (:32) does an exact lookup.
**do:** normalise inside `StorageCategories.indexOf` (`extensionToIndex[extension.lowercase()]
?: OTHER_INDEX`) and collapse the fragment to one call. Keep the empty-extension branch.
**notes:** behaviour is already correct today (the second lookup catches uppercase); this is
purely removing the redundant probe. Add a `JPG` → images test case.

---

### validate the report form with isNullOrBlank instead of a string template plus isNullOrEmpty
**rating:** 64
**proposed by:** openhands-glm(1.0)
**verified:** `ui/enterprises/EnterprisesReportsFragment.isValidReportForm` (:336-370) checks
six fields as `"${binding.summary.text}".isNullOrEmpty()` — a string template that allocates a
new `String` and then an emptiness check that can never see null.
**do:** use `binding.summary.text.isNullOrBlank()` (etc.) directly on the `CharSequence`.
**notes:** **behaviour change** — whitespace-only input becomes invalid where it is currently
accepted. That is almost certainly the intent, but call it out in the PR.

---

### normalize each chat conversation in one traversal
**rating:** 64
**proposed by:** codex(1.0)
**verified:** `repository/ChatRepositoryImpl.buildPrecomputedChats` (:144-155) traverses each
chat's conversations twice — `queries` at :151 and `responses` at :152 — and separately re-reads
`conversations[0].query` for the title at :146-149.
**do:** build the normalized question and response collections in one pass per chat and reuse the
first normalized question for the title, keeping the fallback to the normalized chat title, the
null conversation entries, the output order and the `PrecomputedChat` shapes `fullConvoSearch`
expects. Keep the work on `dispatcherProvider.default`.
**notes:** pairs naturally with the `searchChats` signature change.

---

### count passed course steps without intermediate collections
**rating:** 62
**proposed by:** codex(1.0)
**verified:** `repository/ProgressRepositoryImpl.kt:194-198` does
`courseProgressRecords.filter { it.passed }.map { it.stepNum }.toSet()` purely to take `.size`,
once per course while building completion badges.
**do:** accumulate unique passed `stepNum` values in one pass. Keep duplicates counted once,
ignore `passed == false`, and preserve the nonzero-step / valid-id / valid-title gates and the
return ordering exactly.

---

### select duplicate-user survivors without sorting each group
**rating:** 62
**proposed by:** codex(1.0)
**verified:** `repository/UserRepositoryImpl.cleanupDuplicateUsers` (:842-859) groups duplicates
by name, then `sortedWith` a comparator that only distinguishes `org.couchdb.user:` from
`guest_` (returning 0 otherwise), then `drop(1).map { it.id }` for the delete list.
**do:** replace with a single traversal that picks the same survivor (prefer an
`org.couchdb.user:` row over a `guest_` row, otherwise first-seen) and collects the other ids
directly. Keep one `deleteByIds` per group, skip singleton groups, and preserve nullable-name
grouping.
**notes:** the current comparator is not a total order; it works only because `sortedWith` is
stable. A single pass makes that explicit.

---

### make batchInsertResources a single pass over the documents
**rating:** 62
**proposed by:** devin(1.0)
**verified:** `repository/ResourcesRepositoryImpl.batchInsertResources` (:674-716) filters
`documents` into `validDocs`, maps `validDocs` again for `resourceIds`, then iterates `validDocs`
a third time to build the entities — and re-extracts `_id` per document each time.
**do:** build `validDocs` and `resourceIds` in one pass; keep the chunked DAO lookup and the
final entity-building loop as-is.
**notes:** small next to the DB work in the same method, but it is on the sync path.

---

### remove MainApplication.context from UserInformationFragment's toasts
**rating:** 62
**proposed by:** jules(1.0)
**verified:** `ui/exam/UserInformationFragment.kt` passes `MainApplication.context` to
`Utilities.toast` at :164, :167, :263, :275 and :285 — a fragment reaching for the global
application context.
**do:** use `requireContext()` at all five sites and drop the `MainApplication` import.
**notes:** guard for detached state on the async paths (:263-285 run after suspend calls), or
keep the application context there deliberately. Overlaps the ViewModel-hoist task on the same
file — sequence them.

---

### sort course titles without allocating lowercase copies
**rating:** 60
**proposed by:** codex(1.0)
**verified:** `ui/courses/CoursesViewModel.sortCourses` (:75-81) does
`courses.sortedBy { it.courseTitle.lowercase() }` / `sortedByDescending { … }`, allocating a
lowercase copy per element per sort.
**do:** sort with a case-insensitive comparator (`String.CASE_INSENSITIVE_ORDER` on
`courseTitle`). Leave date sorting and the stale-result guard in `applySort` alone.
**notes:** verify the tie ordering for case-folded-equal titles matches today's stable sort.

---

### memoize the BuildConfig-derived server lists in ServerConfigUtils
**rating:** 60
**proposed by:** openhands-glm(1.0)
**verified:** `utils/ServerConfigUtils.getTrustedServerHosts` (:140-158) and
`getChallengeServerUrls` (:159-168) rebuild `listOfNotNull(...)` over ~14 `BuildConfig`
constants with a `takeIf` per entry on every call; `getServerAddresses` (:29-43) does the same
plus 11 `context.getString(...)` lookups.
**do:** back the two context-free functions with `by lazy` vals.
**notes:** do **not** cache `getServerAddresses` — its strings are locale-dependent and the app
supports runtime locale changes, so a cached list would go stale in the wrong language. Limit
this task to the two `BuildConfig`-only functions.

---

### tokenize survey searches without a temporary split list
**rating:** 58
**proposed by:** codex(1.0)
**verified:** `ui/surveys/SurveysViewModel.filter` (:164-166) materializes
`s.split(" ").filterNot { it.isEmpty() }` and then a second normalized list on every debounced
query.
**do:** use `splitToSequence`, drop empties, normalize per token and materialize only the final
token collection. Keep the whole-query normalization and the starts-with-before-contains
buckets, and keep the work on `dispatcherProvider.default`.

---

### avoid building the full filtered list just to count it
**rating:** 58
**proposed by:** devin(1.0)
**verified:** `ui/resources/ResourcesListFilter.kt:47-48` — `countMatching` is
`filter(models, criteria, locallyOfflineIds).size`, and `filter` (:50-53) chains
`filterBySearchAndTags` into `filterByFacetsAndDownloadStatus`, allocating two lists per call.
**do:** make `countMatching` count matches without materializing the result, and evaluate the
search/tag and facet/download predicates in one pass inside `filter`.
**notes:** overlaps the tag-set task in the same file — land them together.

---

### read the community configuration as one repository snapshot
**rating:** 58
**proposed by:** codex(1.0)
**verified:** `ui/community/CommunityTabViewModel.kt:32-34` calls `getParentCode()`,
`getCommunityName()` and `getPlanetType()` as three separate repository reads
(`ConfigurationsRepositoryImpl.kt:393-403`, each a preferences read) to build one
`CommunityTabState`.
**do:** add an immutable snapshot value and one `getCommunityConfiguration()` that reads the
three together; consume it in the ViewModel while still resolving the planet code from the
active user. Keep the individual getters — other callers use them.
**notes:** codex's "internally inconsistent state" motivation is thin (the three reads happen
back-to-back with no suspension between them); the value here is the narrower boundary, not a
race fix.

---

### add a composite index on retry_operation(status, nextRetryTime, attemptCount)
**rating:** 58
**proposed by:** devin(1.0)
**verified:** `model/RetryOperation.kt:15` declares
`indices = [Index("uploadType"), Index("itemId"), Index("status")]`, while
`data/room/dao/RetryDao.kt:25-29` `getPending` filters
`status = 'pending' AND nextRetryTime <= :now AND attemptCount < maxAttempts`.
**do:** add `Index(value = ["status", "nextRetryTime", "attemptCount"])` **and bump
`AppDatabase.version`** (currently 12).
**notes:** devin's claim that `fallbackToDestructiveMigration` applies the change automatically
is wrong — Room validates the schema identity hash and throws at an unchanged version. Also
inherits the drop-and-resync data-loss caveat.

---

### drop the per-survey exam_questions precheck in hasPendingSurvey
**rating:** 58
**proposed by:** openhands-deepseek(1.0)
**verified:** `repository/SubmissionsRepositoryImpl.hasPendingSurvey` (:381-389) loops the
course's surveys calling `hasSubmission` (:176-192), which pairs
`questionDao.countByExamId(stepExamId)` with `submissionDao.countByUserParentAndType(...)` — two
queries per survey on the dashboard challenge-dialog path.
**do:** in `hasPendingSurvey` only, build the parent id directly (`"${survey.id}@$courseId"`,
matching `hasSubmission`'s construction at :184) and gate on
`submissionDao.countByUserParentAndType(userId, parentId, "survey") == 0`, keeping the
first-unanswered early return. Leave `hasSubmission` and `hasUnfinishedSurveys` byte-identical.
**notes:** **not** behaviour-neutral as claimed: for a question-less survey that *has* a
submission, today's precheck makes `hasSubmission` return false and the survey counts as
pending; after the change it would not. Decide which is correct and add a test either way.
While here, note that `hasPendingSurvey` passes `survey.courseId` where `hasUnfinishedSurveys`
passes the `courseId` argument — worth reconciling separately.

---

### remove MainApplication.context from BecomeMemberActivity
**rating:** 58
**proposed by:** jules(1.0)
**verified:** `ui/user/BecomeMemberActivity.kt:18` imports `MainApplication` and :127 passes
`MainApplication.context` to `Utilities.toast`.
**do:** pass `this` and drop the import.

---

### log the swallowed exceptions in CrashLogStore instead of printStackTrace
**rating:** 58
**proposed by:** jules(1.0)
**verified:** `utils/CrashLogStore.kt:45` and `:57` both call `e.printStackTrace()`.
**do:** replace with `Log.e(TAG, "<what failed>", e)` using a `TAG` constant.
**notes:** in a crash-log store specifically, a swallowed write failure is worth a real log line.

---

### hoist the per-row invariants out of the addStats usage-stats loop
**rating:** 56
**proposed by:** openhands-glm(1.0)
**verified:** `model/MyPlanet.getTabletUsages` (:65-81) loops `queryUsageStats` calling
`addStats(s, arr, context)`, and `addStats` (:83-99) recomputes `context.packageName` (:84),
`NetworkUtils.getCustomDeviceName(context)` (:94, a preferences read),
`NetworkUtils.getDeviceName()` (:95) and `Date().time` (:96) per row. `getTabletUsages` already
takes a `now: Long` defaulting to `System.currentTimeMillis()`.
**do:** compute the package name, custom device name and device name once before the loop, pass
them plus `now` into `addStats`, and use `now` for the `time` field.
**notes:** the win is smaller than stated — the expensive lines sit behind
`if (s.packageName == context.packageName)`, so only the app's own row pays them. The
consistent-timestamp part is the real improvement.

---

### log the download failure in DownloadUtils instead of printStackTrace
**rating:** 56
**proposed by:** jules(1.0)
**verified:** `utils/DownloadUtils.kt:181` calls `e.printStackTrace()`.
**do:** replace with a tagged `Log.e`, adding a `TAG` constant.

---

### log the sync-time parse failure in SyncTimeLogger instead of printStackTrace
**rating:** 56
**proposed by:** jules(1.0)
**verified:** `utils/SyncTimeLogger.kt:120` calls `e.printStackTrace()`.
**do:** replace with a tagged `Log.e`; the file already has a logging tag to reuse.

---

### stop hydrating full submission rows in getUniquePendingSurveys
**rating:** 55
**proposed by:** openhands-deepseek(1.0)
**verified:** `repository/SubmissionsRepositoryImpl.getUniquePendingSurveys` (:100-119) calls
`hydrateSubmissions(submissionDao.getUniquePendingSurveyCandidates(userId))` — which issues
`answerDao.getBySubmissionIds` and attaches every answer — *before* deriving exam ids,
validating them via `examDao.getByIds` and deduping down to one submission per exam. The bell
consumer only needs the deduped rows.
**do:** dedupe first (derive exam ids, validate, keep one candidate per exam) and hydrate only
the survivors.
**notes:** openhands' prescription (a new `GROUP BY parentId` count query, then filtering
candidates by nonzero pending count) does not deliver the win — the candidates are already
pending. Reorder the existing steps instead; no DAO change needed.

---

### stop the redundant exists() syscall per file in FreeSpaceWorker
**rating:** 55
**proposed by:** kimi(1.0)
**verified:** `services/FreeSpaceWorker.deleteRecursive` (:63-83) checks `file.exists()` for
every node yielded by `walkBottomUp()`, then reads `length()` and relies on `delete()`'s boolean
anyway.
**do:** drop the `exists()` guard, keeping the `length()`-before-`delete()` order, the
`deletedFiles`/`freedBytes` accounting, the every-10-files `setProgress` and the `isStopped`
early return.
**notes:** a missing file makes `length()` return 0 and `delete()` false, so the accounting is
unchanged.

---

### hoist per-bind lookups out of HealthUsersAdapter's payload path
**rating:** 52
**proposed by:** devin(1.0)
**verified:** `ui/health/HealthUsersAdapter.kt:77` runs
`payloads.filterIsInstance<List<*>>().flatten()` on every partial bind — two intermediate
collections built to test three string keys (:82, :85, :88).
**do:** iterate `payloads` once, setting three booleans when a `List<*>` element contains
`"name"`, `"userImage"` or `"joinDate"`, and preserve the empty-`diffs` fallback to
`super.onBindViewHolder`.

---

### add a (userId, isRead) composite index to the notifications table
**rating:** 52
**proposed by:** kimi(1.0)
**verified:** `model/AppNotification.kt:9` declares
`indices = [Index("userId"), Index("type")]`, while `NotificationDao.getUnreadCount` (:21)
filters `userId … AND isRead = 0` and `getNotifications` (:36) filters on user + read state and
orders by `isRead, createdAt`.
**do:** add `Index(value = ["userId", "isRead"])` **and bump `AppDatabase.version`**.
**notes:** kimi explicitly instructs *not* to bump the version, on the theory the index simply
lies dormant. That is wrong — Room compares the generated schema identity hash on open and
throws at an unchanged version, so shipping this without the bump breaks existing installs.

---

### add a (parentId, type) composite index to the team-notification table
**rating:** 52
**proposed by:** kimi(1.0)
**verified:** `model/TeamNotification.kt:12` declares `indices = [Index("type")]` only, while
`TeamNotificationDao.findByParentAndType` (:12-13) and `getByTypeAndParentIds` (:15-16) filter
on `parentId` + `type` and run on every dashboard team-badge refresh.
**do:** add `Index(value = ["parentId", "type"])` **and bump `AppDatabase.version`**.
**notes:** same required-version-bump correction as above. If the "single UPDATE" task lands
first, `findByParentAndType` disappears but `getByTypeAndParentIds` still benefits.

---

### expose resource and exam counts in CourseStepUiState
**rating:** 50
**proposed by:** devin(1.0)
**verified:** `ui/courses/CoursesStepsViewModel.kt:30-43` — `CourseStepUiState` carries
`resources`, `stepExams` and `stepSurvey` lists but no counts, and
`ui/courses/CourseStepFragment.kt:87-88` derives `state.resources.size` for the button label
while `hideTestIfNoQuestion` takes the exam list and reads `exams.size` at :171/:173.
**do:** add `resourceCount`/`examCount`/`surveyCount` to the state, set them in `loadStep` and
`refreshInlineResources`, and change the fragment (and `hideTestIfNoQuestion`'s signature) to
consume the counts.
**notes:** no runtime saving — `size` is O(1). Worth it only as an altitude/Compose-readiness
change; do not sell it as a performance fix.

---

### use repository-provided member status and counts in the team member screens
**rating:** 44
**proposed by:** devin(1.0)
**verified:** `ui/teams/members/MembersFragment.kt:99` derives leadership as
`members.any { it.user.id == currentUserId && it.isLeader }` from the list loaded on the line
above, and `ui/teams/members/RequestsFragment.kt:53` passes `uiState.members.size` to
`showNoData` even though `RequestsUiState` exposes `memberCount` (`RequestsViewModel.kt:22`,
filled from `getJoinedMemberCount` at :40).
**do:** for the `showNoData` call, decide deliberately which number the empty-state should
reflect and use it consistently.
**notes:** devin's first step — swapping the in-memory scan for
`teamsRepository.isTeamLeader(teamId, currentUserId)` — is a **regression**: the list is already
in hand, so it replaces a free scan with an extra query. Skip that half. And `uiState.members`
(requested members) and `memberCount` (joined members) are different quantities, so the swap is
a behaviour change, not a cleanup — hence the low rating.

---

### combine filterNotNull and map in ResourcesAdapter
**rating:** 42
**proposed by:** jules(1.0)
**verified:** `ui/resources/ResourcesAdapter.kt:167` — `libraryList.filterNotNull().map { … }`
allocates an intermediate list.
**do:** collapse to a single `mapNotNull { … }` pass.
**notes:** one line; the "GC pauses on large libraries" framing is overstated.

---

### count non-null voices items without allocating the filtered lists
**rating:** 40
**proposed by:** jules(1.0)
**verified:** `ui/teams/voices/TeamsVoicesFragment.kt:262`
(`realmNewsList?.filterNotNull()?.size ?: 0`) and `:273` (`it.filterNotNull().size`) both build a
list only to read its size.
**do:** use `count { it != null }` at both sites.
**notes:** the neighbouring `submitList(it.filterNotNull())` calls (:259, :267) legitimately need
the list — leave them.

---

### merge the filter and map when creating pending survey submissions
**rating:** 40
**proposed by:** jules(1.0)
**verified:** `repository/SubmissionsRepositoryImpl.kt:213` —
`chunk.filter { it !in existingUserIds }.map { userId -> Submission().apply { … } }` inside a
`chunked(500)` loop.
**do:** collapse to one `mapNotNull` pass.
**notes:** chunks are ≤500 and the DB insert dominates; strictly cosmetic.

---

### avoid the list allocations in MyHealthFragment.getDisplayName
**rating:** 40
**proposed by:** devin(1.0)
**verified:** `ui/health/MyHealthFragment.kt:339-342` builds
`listOfNotNull(firstName, middleName, lastName).map { it.trim() }.filter { it.isNotEmpty() }
.joinToString(" ")` — three intermediate collections to concatenate at most three fields.
**do:** rewrite with `buildString`, appending each non-blank trimmed part with a single space
separator, keeping the `user.name.orEmpty()` fallback when all three are blank.
**notes:** at most three elements, computed once per patient display — cosmetic.

---

### drop the no-op string concatenation in HealthExaminationAdapter
**rating:** 38
**proposed by:** openhands-glm(1.0)
**verified:** `ui/health/HealthExaminationAdapter.kt:127` and `:131` return
`value.toString() + ""`.
**do:** return `value.toString()`.
**notes:** cosmetic — modern string-concat lowering makes the runtime cost ~zero. Bundle it into
any other change in this file (e.g. the double-decrypt fix) rather than a standalone PR.

---

### replace the Date() allocations in ActivitiesRepositoryImpl with System.currentTimeMillis()
**rating:** 34
**proposed by:** jules(1.0)
**verified:** `repository/ActivitiesRepositoryImpl.kt` allocates `Date().time` at :100, :128,
:135, :164 and :256.
**do:** use `System.currentTimeMillis()` and drop the `java.util.Date` import if it becomes
unused.
**notes:** better still, use the project's injected `TimeProvider` (already used elsewhere) so
these timestamps are testable — that is the change with actual value here.

---

### drop the Calendar.getInstance() allocation in CalendarFragment
**rating:** 34
**proposed by:** kimi(1.0)
**verified:** `ui/calendar/CalendarFragment.kt:25-26` — `val calendar = Calendar.getInstance();
binding.calendarView.setDate(calendar.time)` in a 34-line fragment's `onCreateView`.
**do:** call `setDate(Date())` and swap the import.
**notes:** one allocation per view creation. Effectively free either way; listed for completeness.

---

### replace Date().time in the Personal upload serializer
**rating:** 32
**proposed by:** jules(1.0)
**verified:** `model/Personal.kt:39` — `addProperty("uploadDate", Date().time)`.
**do:** use `System.currentTimeMillis()`.
**notes:** if the "move serialize into the repository" task lands first, this disappears with it —
prefer that one.

---

### replace Date().time in FeedbackRepositoryImpl
**rating:** 32
**proposed by:** jules(1.0)
**verified:** `repository/FeedbackRepositoryImpl.kt:54` (`val timestamp = Date().time`) and
`:102` (`addProperty("time", Date().time.toString())`).
**do:** use `System.currentTimeMillis()`, or better the injected `TimeProvider`.

---

### replace the Date allocation in the CameraUtils photo filename
**rating:** 30
**proposed by:** jules(1.0)
**verified:** `utils/CameraUtils.kt:106` — `val photoFile = "${Date().time}.jpg"`.
**do:** use `System.currentTimeMillis()`.

## Dropped (premise did not hold)

Not part of the backlog. Each was checked against the tree and failed.

- **copilot-kimi p1 #7 — hoist per-bind lookups in `TeamsSelectionAdapter`.** Already done:
  `cachedTeamsLabel` memoizes the only `getString`, and the remaining `setImageResource` calls
  take compile-time constants. Cited lines 35-37 don't match, and the work order itself ends
  "if nothing hoistable is found … leave binding logic untouched" — a non-task.
- **copilot-kimi p1 #8 — replace `LifeAdapter.onItemMoveFinished`'s manual copy with in-place
  mutation.** The load-bearing claim ("the items are already detached copies made in
  `onItemMove`") is false: `dragList = currentList.toMutableList()` (:108) copies the list, not
  the items. Mutating `weight` in place would mutate the objects `ListAdapter` still holds, so
  `areContentsTheSame` (equals covers `weight`) would see no change and the reorder would not
  render. The manual copy exists for that reason (`MyLife` is not a data class, no `copy()`).
- **copilot-grok p1 #1 — buffer the download writes in `DownloadService`.** There is no missing
  buffer: `data = ByteArray(BUFFER_SIZE)` with `BUFFER_SIZE = 16 * 1024` (:67, :556) means
  `output.write(data, 0, readCount)` already writes in 16 KB chunks. A `BufferedOutputStream`
  around 16 KB writes passes them straight through.
- **copilot-grok p1 #6 — cut redundant offline work in `ResourcesRepositoryImpl`.** Half false:
  `getOfflineResourceItems` already computes `file.extension.lowercase()` exactly once per file
  (:857). Half zero-impact: `MyLibrary.isResourceOffline()` is `resourceOffline && _rev ==
  downloadedRev` (:141-143) — two field reads, no I/O — so calling it twice costs nothing.
- **openhands-deepseek #2 — skip answer hydration in the surface list mapping.** Two false
  premises: `getSubmissionsFlow` (:82-89) does not call `hydrateSubmissions` at all, and
  `Submission` is an `open class`, not a data class, so the prescribed
  `row.copy(answers = emptyList())` does not compile. (Hydration also sets `membershipDoc`, so
  skipping it isn't neutral either.)
- **openhands-deepseek #4 — drop the members/admin user re-resolution.** There is no second
  resolution: across `getJoinedMembersWithVisitInfo` (lines 975-1055) the only user access is the
  one batched call, and the admin merge is `adminFromRoomMap[admin.name] ?: admin` — already
  reusing loaded data. Zero `getUsersByIds`/`getUserById` calls in the cited range.
- **openhands-deepseek #5 — hoist the shared-prefs user read out of `getTaskTeamInfo`.**
  `TeamsRepositoryImpl.getTaskTeamInfo` (:382-389) contains no user read of any kind — it reads a
  task, parses `link`, reads a team, returns a `Triple`. Nothing to hoist.
- **openhands-deepseek #6 — lock in a single user-row read in the dashboard profile load.** The
  work order states the code "already does the right thing" and asks the executor to verify it.
  Confirmed: `getDashboardProfile` (:88-98) does one `getUserById` and one conditional
  `getOfflineLoginCount`. A no-op.
- **openhands-deepseek #9 — batch the per-course step loads.** Line 202 is the *declaration* of
  `getCourseSteps(courseId)`, not a call in a courses-list loop. The only call sites are :139
  (single course detail) and :428 (delete path). The claimed N+1 does not exist.
- **jules p2 #1 — replace the member-count list load with a count query.** Incoherent (opens on
  `RequestsViewModel`/`getJoinedMemberCount`, then pivots mid-sentence to caching
  `pendingSurveys.size`). `List.size` is an O(1) field read, and the four occurrences
  (`BellDashboardFragment` :212-213, :218-219) sit in two mutually exclusive branches.
- **jules p2 #5 — cache `exams.size` in `CourseStepFragment`.** The two uses (:171, :173) are the
  two arms of one if/else, so exactly one executes. Nothing is evaluated twice.
- **jules p2 #6 — cache `steps.size` in `TakeCourseFragment`.** No-op on an O(1) read, and
  actively harmful: a class-level `stepsCount` field can desync from `steps`.
- **jules p2 #7 — `mapNotNull` → `mapNotNullTo(mutableListOf())` in `CoursesFragment`.**
  Identical: `mapNotNull` *is* `mapNotNullTo(ArrayList())`. The stated benefit ("bypass
  intermediate list creation") does not exist.
- **jules p2 #8 — cache `categories.size` in `StorageBreakdownFragment`.** Two O(1) reads, once
  per scan, used to size two arrays.
- **jules p2 #9 — replace redundant `stepMistake.size` calls in `CoursesProgressAdapter`.**
  Already cached: `val requiredChildCount = stepMistake.size` (:49). The proposal is a rename.
- **jules p2 #10 — remove the redundant list-size query in `StorageCategoryDetailFragment`.**
  `.size` is read once at :97, and the work order concedes "the method needs it". Nothing to do.
