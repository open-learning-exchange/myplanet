# myPlanet refactor tasks — performance quick wins round

- date: 2026-10-01
- base commit: 286e66e0e1daf285c34d8456deb0028016bf858a (`all: smoother importing (fixes #17814) (#17813)`, master)
- open PRs checked: 4075, 8175, 10993, 13287, 13355, 13415, 13604, 13657, 13848, 13928, 14427, 14650, 14883, 15108, 15226, 15266, 15267, 15559, 15808, 15820, 15824, 15825, 15951, 16594, 16623, 17187, 17254, 17435, 17680, 17694, 17776, 17811, 17812 — every file any of them touches is excluded from every task below.
- prior rounds: the 2026-09-24 perf-quick-wins doc (`docs/TASKS-2026-09-24-performance-quick-wins.md`) already ordered getString/getColor template hoists in `EnterprisesReportsAdapter`, `EnterprisesFinancesAdapter`, `LifeAdapter`, `HealthExaminationAdapter`, `MembersAdapter`, `HealthUsersAdapter`, `UserArrayAdapter`, `ChatShareTargetAdapter`, `ChatDetailFragment`, and `ChatAdapter`. This round deliberately scopes around those files' previously-ordered edits; where a file overlaps, the out-of-scope line says which lines to leave alone.

### 1. move enterprises attachment image reads off the main thread (roadmap 7+3)

context: tapping save on the add-transaction, add-report, and edit-report dialogs runs `FileUtils.readBytesFromUri(requireContext(), it)` — a full contentResolver stream read of the picked image — inside the `setPositiveButton` click on the main thread (EnterprisesFinancesFragment.kt:306, EnterprisesReportsFragment.kt:187 and :270). `FileUtils.getDisplayName` at :305/:186/:269 also runs a contentResolver `query` there. On slow storage this stalls the UI thread for the whole file length. Moving the read behind the viewmodel boundary also keeps android.content.Uri handling out of the click path, which helps roadmap 3.
files:
- app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesFragment.kt — the `setPositiveButton` block ~:300-320.
- app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt — the add-report submit block ~:183-205 and the edit-report submit block ~:266-286.
- do NOT touch EnterprisesViewModel.kt — its `imageName`/`imageData: ByteArray?` signature stays unchanged — and do not touch EnterprisesRepositoryImpl.
steps:
1. inject `DispatcherProvider` into both fragments (`@Inject lateinit var`, the pattern other fragments already use).
2. in each dialog's positive-button handler, capture `requireContext().applicationContext` and the selected `Uri` into locals, then wrap the read and the `viewModel.*` call in `viewLifecycleOwner.lifecycleScope.launch { ... }` (EnterprisesReportsFragment already imports `androidx.lifecycle.lifecycleScope` at :18 and uses it at :84).
3. inside the launch, compute `imageName`/`imageData` under `withContext(dispatcherProvider.io)`; call the viewmodel afterward with the same arguments as today.
4. keep `selectedImageUri`/`imageViewResult` handling and dialog dismissal behavior identical.
5. remove now-unused imports if any.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- creating a transaction or report with an attached image still stores the attachment, and the dialog closes immediately without a visible stall.
size budget: ~45 changed lines, 2 files
out of scope: no viewmodel/repository signature changes, no upload-path changes; do not add progress UI.

---

### 2. run the AutoSyncWorker upload chain concurrently instead of strictly sequentially (roadmap 5+7)

context: `AutoSyncWorker` fires 17 independent `uploadManager.upload*()` suspend calls one after another inside `workerScope.launch(dispatcherProvider.io)` (AutoSyncWorker.kt:114-130) — `uploadExamResult`, `uploadFeedback`, `uploadAchievement`, `uploadResourceActivities("")`, `uploadUserActivities`, `uploadCourseActivities`, `uploadSearchActivity`, `uploadRating`, `uploadResource`, `uploadNews`, `uploadTeams`, `uploadTeamTask`, `uploadMeetups`, `uploadAdoptedSurveys`, `uploadCrashLog`, `uploadSubmissions`, `uploadActivities(null)`. Each waits for the previous one's network round-trips; on a cold sync with a slow server the tail of the chain idles the worker for seconds. This is the cheap end of roadmap 5's sync/upload consolidation, and the supervisorScope/async shape is platform-free coroutine work that carries to roadmap 9.
files:
- app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt — the `isSyncRunning.compareAndSet` block ~:112-141.
- do NOT touch UploadManager.kt, TransactionSyncManager.kt, SyncManager.kt, or MainApplication.kt — `isSyncRunning` semantics stay exactly as they are.
steps:
1. keep the `compareAndSet(false, true)` gate and the `try/finally { isSyncRunning.set(false) }` exactly where they are.
2. inside the `try`, wrap the upload list in `supervisorScope` and launch each `uploadManager.upload*()` call as its own `async` child.
3. wrap each `async` body so a single upload's failure is captured (e.g. `runCatching` + `Log.e` with the upload name) instead of cancelling siblings; still let `CancellationException` propagate.
4. `awaitAll()` the deferreds, then call `sharedPrefManager.setLastSync(timeProvider.now())` after all complete.
5. run tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- a manual sync still uploads pending feedback/submissions end-to-end; one failing upload logs its error without aborting the others, and the outer `catch`/`onSyncFailed` path still works.
size budget: ~45 changed lines, 1 file
out of scope: no changes to UploadManager internals, batch sizes, or upload order semantics elsewhere (TaskNotificationWorker, UploadCoordinator); do not parallelize anything else in the file.

---

### 3. cache formatted dates and hoist click listeners in the submissions and feedback-reply adapters (roadmap 7)

context: `SubmissionsListAdapter.ViewHolder.bind` calls `TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)` and allocates two `setOnClickListener` lambdas on every bind (SubmissionsListAdapter.kt:35, :40-46). `FeedbackReplyAdapter.onBindViewHolder` runs `feedbackReply.date.let { getFormattedDateWithTime(it.toLong()) }` — a `String.toLong()` parse plus date-format per row per bind (FeedbackReplyAdapter.kt:21-23). Sibling adapters already carry a `dateCache` map (e.g. PersonalsAdapter.kt:23); these two were missed.
files:
- app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsListAdapter.kt — ViewHolder.bind only.
- app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackReplyAdapter.kt — onBindViewHolder only.
- do NOT touch SubmissionDetailFragment, SubmissionsFragment, or FeedbackDetailActivity.
steps:
1. in SubmissionsListAdapter add a `private val dateCache = HashMap<Long, String>()` and use `dateCache.getOrPut(submission.lastUpdateTime) { ... }`.
2. move both `setOnClickListener` calls into `ViewHolder.init`, resolving the item via `bindingAdapterPosition` + `getItem(position)` with a `RecyclerView.NO_POSITION` guard.
3. in FeedbackReplyAdapter add a `HashMap<String, String>` keyed on the raw `feedbackReply.date` string so repeated rows skip both the `toLong()` and the format.
4. run tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- submissions list still shows "#n", formatted date, status, and the sync mark; view-details and PDF buttons act on the right row; feedback reply rows still show date, user, message.
size budget: ~40 changed lines, 2 files
out of scope: no DiffUtil/payload rework, no layout changes; do not change the date format or add `payloads` handling.

---

### 4. hoist per-bind listeners and context lookups in PersonalsAdapter and the storage detail adapter (roadmap 7)

context: `PersonalsAdapter.onBindViewHolder` allocates four `setOnClickListener` lambdas per bind (imgDelete, imgEdit, itemView, imgUpload — PersonalsAdapter.kt:40-63), each needing only the bound `Personal`. `StorageCategoryDetailFragment.ResourceAdapter.onBindViewHolder` calls `FileUtils.formatSize(requireContext(), item.totalSizeBytes)` plus two `setOnClickListener` per bind (StorageCategoryDetailFragment.kt:185-196), and its payload-bind overload duplicates the two listener assignments again (:198-207).
files:
- app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt — onBindViewHolder only; the existing `dateCache` stays.
- app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt — the inner `ResourceAdapter` only.
- do NOT touch PersonalsFragment, FileUtils, or the fragment's other adapters.
steps:
1. PersonalsAdapter: move all four listeners into `PersonalsViewHolder.init`, resolving the item via `holder.bindingAdapterPosition` + `getItem()` exactly as the current lambdas already do — the logic is unchanged, only the allocation site moves.
2. ResourceAdapter: move both `setOnClickListener` calls into `ViewHolder.init` using `bindingAdapterPosition` + `getItem` + `onItemClicked`; drop the duplicated listener assignments from the payload `onBindViewHolder`, which then only sets `checkBox.isChecked`.
3. hoist the `requireContext()` call for `FileUtils.formatSize` to an adapter field or `binding.root.context` instead of per bind.
4. run tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- personal rows still open the viewer, and delete/edit/upload callbacks fire for the correct item; the storage category list still toggles checkboxes and sizes render.
size budget: ~45 changed lines, 2 files
out of scope: no behavior or visibility changes to the listeners; do not add payloads where none exist.

---

### 5. hoist the startSurvey listener and its label strings in SurveysAdapter (roadmap 7)

context: `SurveysViewHolder.bind` allocates a `startSurvey.setOnClickListener` per bind and resolves `context.getString` for `R.string.adopt_survey` / `R.string.take_survey` / `R.string.record_survey` on every bind (SurveysAdapter.kt:86-93, :102-106). The sibling `sendSurvey` listener already lives in `init` (:63-66) — `startSurvey` was left behind.
files:
- app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt — SurveysViewHolder.init and bind only.
- do NOT touch SurveyFragment, SendSurveyFragment, SubmissionsAdapter, or SurveysViewModel.
steps:
1. move `startSurvey.setOnClickListener` into `ViewHolder.init`, resolving `row`/`exam`/`teamSubmission` via `bindingAdapterPosition` + `getItem(position)` — the click uses `exam.isTeamShareAllowed`, `teamSubmission`, `exam.id`, `isTeam`, `teamId`, all reachable from the item.
2. cache the three label strings once (adapter or holder fields from `itemView.context`) and pick the cached value in `bind`.
3. keep the visibility toggles and the `questionCount == 0` hiding exactly as-is.
4. run tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- survey rows still show adopt/take/record with the same rules, and tapping start still opens the survey or triggers adopt for team-shareable exams.
size budget: ~30 changed lines, 1 file
out of scope: no changes to `sendSurvey`, the adopt flow, or `SubmissionsAdapter.openSurvey` arguments; no string-resource edits.

---

### 6. drop the per-event synchronizedList locking in SyncTimeLogger (roadmap 5+7)

context: during every sync, `logApiCall` and `logDbOperation` run `apiCallTimes.computeIfAbsent(processName) { Collections.synchronizedList(mutableListOf()) }.add(log)` and the same for `dbOperationTimes` (SyncTimeLogger.kt:170, :187). `isLogging` is set true unconditionally by `startLogging` (:68-70), so every API call and DB op in a sync pays a `computeIfAbsent` lookup plus a `synchronizedList.add` lock — hundreds of lock acquisitions on the sync hot path, and the wrappers exist only because appends may race.
files:
- app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt — the two map declarations at :37-38 and the `logApiCall`/`logDbOperation` bodies at :162-193.
- do NOT touch the DAO/repository call sites that invoke `logApiCall`/`logDbOperation`, and leave the `isVerbose`-gated `Log.d` blocks as they are.
steps:
1. change `apiCallTimes`/`dbOperationTimes` value types to a lock-free append collection (e.g. `ConcurrentLinkedQueue<ApiCallLog>` / `ConcurrentLinkedQueue<DbOperationLog>`) and append with `.add(...)` — no `Collections.synchronizedList`.
2. confirm `generateSummary` and any other readers only iterate the collections after logging stops or tolerate weakly-consistent iteration (ConcurrentLinkedQueue gives this for free).
3. remove the `java.util.Collections` import if it becomes unused.
4. run tests, including any SyncTimeLogger tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- a sync still produces the same summary lines and per-endpoint entries in the diagnostics log.
size budget: ~15 changed lines, 1 file
out of scope: no change to what gets logged, the summary format, or the `isVerbose` flag; do not touch DiagnosticsRepository.

---

### 7. hoist per-bind listeners and repeated styling in ResourcesTagsAdapter (roadmap 7)

context: the tag-drawer adapter re-allocates listeners on every bind — `tvDrawerTitle1.setOnClickListener`, `root.setOnClickListener`, `tvDrawerTitle.setOnClickListener` (ResourcesTagsAdapter.kt:79, :85, :99) — and `ChildViewHolder.bind` re-applies `setBackgroundColor`/`setTextColor` with already-cached holder fields on every bind (:97-98). `createCheckbox` also runs a `setOnCheckedChangeListener(null)` → `isChecked` → `setOnCheckedChangeListener` sequence per bind (:116-118), allocating a new lambda each time.
files:
- app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesTagsAdapter.kt — ParentViewHolder.bind, ChildViewHolder.bind, createCheckbox only.
- do NOT touch CollectionsFragment, OnTagClickListener, or `listener.hasChildren` — the map lookup at :73 is already cheap.
steps:
1. move the click listeners into `ParentViewHolder.init`/`ChildViewHolder.init`, resolving the bound `TagData` via `bindingAdapterPosition` + `getItem()` with `NO_POSITION` and `is TagData.Parent`/`Child` guards.
2. keep the `hasChildren` visibility branching in `bind` — only the listener registration moves.
3. move the child's background/text-color assignment into `ChildViewHolder.init` (the color fields are already cached).
4. in `createCheckbox`, keep the null-listener → isChecked → set-listener ordering (it prevents spurious callbacks) but install one shared listener that reads the tag via `bindingAdapterPosition`/`getItem`, or store the bound `TagEntity` on `checkBox.tag` — pick whichever diff is smaller.
5. run tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- the collections tag tree still expands/collapses parent tags, clicks still call `onTagClicked`/`onParentTagClicked`, and multi-select checkboxes still toggle `onCheckboxTagSelected` for the correct tag.
size budget: ~45 changed lines, 1 file
out of scope: no adapter/view-type restructuring, no changes to `OnTagClickListener` or `listener.hasChildren`; do not alter expand-state semantics.

---

### 8. hoist per-bind listeners and a constant drawable in chat-history, checkbox, and team-selection adapters (roadmap 7)

context: `ChatHistoryAdapter.onBindViewHolder` sets a root `setOnClickListener` per bind (:123) and `bindShareChat` re-runs `shareChat.setImageResource(R.drawable.baseline_share_24)` — a constant drawable — plus another `setOnClickListener` on every bind (:137-139). `CheckboxAdapter.onBindViewHolder` allocates a `setOnClickListener` per row (CheckboxAdapter.kt:39-49). `TeamsSelectionAdapter.bind` toggles `setOnClickListener(null)`/`setOnClickListener { onClick(item) }` per bind (TeamsSelectionAdapter.kt:42-46).
files:
- app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt — onBindViewHolder + bindShareChat only; leave the share-dialog plumbing in place.
- app/src/main/java/org/ole/planet/myplanet/ui/components/CheckboxAdapter.kt — onBindViewHolder only.
- app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsSelectionAdapter.kt — TeamSelectionViewHolder.bind only.
- do NOT touch ChatDetailFragment, ChatShareTargetAdapter, or ResourcesFragment.
steps:
1. ChatHistoryAdapter: move `shareChat.setImageResource` into `ViewHolder` init (or the row layout's `src`) and move both click listeners into `init` with `bindingAdapterPosition` + `getItem`; keep `bindShareChat`'s per-bind share-state fields where they are.
2. CheckboxAdapter: move the click listener into `ViewHolder.init` keyed on `bindingAdapterPosition`, preserving the `selectedItemsList` add/remove and `checkChangeListener?.onCheckChange()` call.
3. TeamsSelectionAdapter: install one `init` listener that no-ops when the resolved item's `_id` is in `sharedIds`, keeping the `isClickable` updates in `bind`.
4. run tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- chat history rows still open the chat and the share icon still opens the share dialog; checkbox rows still toggle; the team-selection sheet still ignores already-shared teams.
size budget: ~45 changed lines, 3 files
out of scope: no dialog/layout changes in ChatHistoryAdapter, no `DiffUtil` payload changes; do not move the `baseline_share_24` styling beyond the init/XML hoist.

---

### 9. hoist per-bind listeners in NotificationsAdapter (roadmap 7)

context: every bind allocates up to three listeners — `binding.root.setOnClickListener` for group headers (:104) and for child rows (:134, :145), plus `binding.btnMarkAsRead.setOnClickListener` (:140-143). The adapter already carries a `parsedHtmlCache` LruCache (:42), proving per-bind cost was a known concern, but the listener allocations were left.
files:
- app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsAdapter.kt — the group and child ViewHolder bind methods only.
- do NOT touch NotificationsFragment, NotificationsViewModel, or the `formatTimeDiff` relative-time strings (they change by nature and stay per-bind).
steps:
1. move the group-header click into the header ViewHolder's `init`, resolving the header via `bindingAdapterPosition` + `getItem` and calling `onToggleGroupExpansion(header.type)`.
2. move the child-row click into the child ViewHolder's `init`, calling `onToggleSelection`/`onNotificationClick` with the resolved item — keep the existing conditional that picks which callback applies.
3. move `btnMarkAsRead`'s listener into `init` similarly, preserving the already-read no-op branch (gate inside the listener or keep the null assignment — pick whichever is smaller).
4. run tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- notification groups still expand/collapse, rows still toggle selection, and mark-as-read still fires for the correct notification.
size budget: ~35 changed lines, 1 file
out of scope: no changes to `parsedHtmlCache`, `formatTimeDiff`, or notification payload logic; do not restructure the two view types.

---

### 10. cache per-bind resource lookups in onboarding, courses-steps, and chat-share-target adapters (roadmap 7)

context: `OnboardingAdapter.instantiateItem` calls `mContext.getColor(R.color.daynight_textColor)` twice per page (:28, :30). `CoursesStepsAdapter.bind` runs `context.getString(R.string.test_size, step.questionCount)` per bind (CoursesStepsAdapter.kt:53). `ChatShareTargetAdapter.onBindViewHolder` allocates an `itemView.setOnClickListener` per row for both view types (ChatShareTargetAdapter.kt:38, :41).
files:
- app/src/main/java/org/ole/planet/myplanet/ui/onboarding/OnboardingAdapter.kt — instantiateItem only.
- app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsAdapter.kt — ViewHolder.bind only.
- app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt — onBindViewHolder and the two ViewHolders only.
- in ChatShareTargetAdapter do NOT touch the `setTypeface`/`setTextColor`/`setBackgroundColor` calls in `bind` — the prior round's order owns those lines.
steps:
1. OnboardingAdapter: hoist the color to a `private val` resolved once at adapter construction.
2. CoursesStepsAdapter: hoist the `R.string.test_size` lookup — either a cached template formatted locally, or a small `Int->String` cache keyed on `questionCount`; keep the resulting text identical.
3. ChatShareTargetAdapter: install one `setOnClickListener` in each ViewHolder `init` resolving the item via `bindingAdapterPosition` + `getItem`, then delete the per-bind listener lines at :38 and :41.
4. run tests.
acceptance:
- ./gradlew testDefaultDebugUnitTest green and ./gradlew :app:assembleDefaultDebug compiles.
- onboarding pages render identical text colors, the course-steps list still shows the per-step question count, and the share-target sheet still invokes `onItemClick` per row.
size budget: ~30 changed lines, 3 files
out of scope: as noted, leave ChatShareTargetAdapter's per-bind styling calls untouched; no layout XML or resource changes anywhere.
