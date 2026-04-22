### Extract DictionaryRepository from DictionaryActivity

`DictionaryActivity` is the last UI file still calling `DatabaseService` directly: it opens Realms, runs `where()` queries, does bulk `createObject()` inserts, and even executes a blocking `realm.where(...).findFirst()` on the main thread inside a click listener. Move every one of these calls behind a new `DictionaryRepository` that extends `RealmRepository` so the activity only uses `@Inject` repository methods.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L99"}
:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=127 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L101-L127"}

:::task-stub{title="Extract DictionaryRepository for DictionaryActivity realm access"}
1. Create `repository/DictionaryRepository.kt` (interface) + `DictionaryRepositoryImpl.kt` extending `RealmRepository`, exposing `suspend fun count()`, `suspend fun bulkInsert(entries: JsonArray)`, `suspend fun findByWord(word: String): RealmDictionary?`.
2. Implement each method with `queryList`/`count`/`executeTransaction`/`findByField` helpers from `RealmRepository` — no raw `.where(...)` in the impl.
3. Bind the interface in `di/RepositoryModule.kt`.
4. In `DictionaryActivity.kt`, replace all four `databaseService.withRealm { ... }` and `executeTransactionAsync { ... }` call sites with `dictionaryRepository.*` suspend calls launched from `lifecycleScope`.
5. Delete `databaseService` references from the activity so the file no longer imports `DatabaseService`.
:::

### Move UploadCoordinator Realm queries into repositories

`UploadCoordinator` (a Singleton service) runs `realm.where(config.modelClass.java)` + `findAll()` and `executeTransactionAsync { realm.where(...)... }` inline, which means every `UploadConfig<*>` leaks Realm query plumbing into the service layer. Wrap both operations behind new `UploadRepository.queryPending(config)` / `markUploaded(config, ids)` methods that reuse `RealmRepository.withRealmAsync` and `executeTransaction`, so the coordinator orchestrates and the repository owns the data access.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=110 path=app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt#L84-L110"}
:codex-file-citation[codex-file-citation]{line_range_start=245 line_range_end=265 path=app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt#L245-L265"}

:::task-stub{title="Move UploadCoordinator realm queries into an UploadRepository"}
1. Add `repository/UploadRepository.kt` + impl extending `RealmRepository`, with `suspend fun <T: RealmObject> queryPending(config: UploadConfig<T>): List<T>` and `suspend fun <T: RealmObject> markUploaded(config: UploadConfig<T>, successIds: List<String>)`.
2. Implement both via `withRealmAsync` / `executeTransaction`, using `config.queryBuilder` exactly as today — no behavior change.
3. Replace the `databaseService.withRealmAsync { ... realm.where(...) ... }` block in `queryItemsToUpload` (lines 87-110) with `uploadRepository.queryPending(config)` and keep the serialization loop in the coordinator.
4. Replace the `databaseService.executeTransactionAsync { ... }` block at lines 249-265 with `uploadRepository.markUploaded(config, ids)`.
5. Remove the `databaseService: DatabaseService` constructor parameter from `UploadCoordinator` and update `di/ServiceModule` accordingly.
:::

### Relocate hasPendingSurvey from DashboardViewModel to SubmissionsRepository

`DashboardViewModel.hasPendingSurvey` is pure data orchestration — it fans `submissionsRepository.getSurveysByCourseId` into `hasSubmission` calls. That belongs in the submissions repository, not the view model. Moving it tightens the `SubmissionsRepository` interface and lets the ViewModel stop holding two repositories just to ask "does this user have a pending survey".

:codex-file-citation[codex-file-citation]{line_range_start=306 line_range_end=345 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L306-L345"}
:codex-file-citation[codex-file-citation]{line_range_start=346 line_range_end=354 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L346-L354"}

:::task-stub{title="Move hasPendingSurvey(courseId, userId) into SubmissionsRepository"}
1. Add `suspend fun hasPendingSurvey(courseId: String, userId: String?): Boolean` to `repository/SubmissionsRepository.kt`.
2. Implement it in `SubmissionsRepositoryImpl.kt` by calling the existing `getSurveysByCourseId` + `hasSubmission` internally (same loop moved).
3. In `DashboardViewModel.kt`, delete the private `hasPendingSurvey` function at lines 346-354 and replace the call at line 312 with `submissionsRepository.hasPendingSurvey(courseId, userId)`.
4. Drop the now-redundant outer `withContext(dispatcherProvider.io)` around the call since the repository already dispatches via `RealmRepository`.
:::

### Move course-completion computation into ProgressRepository

`BellDashboardViewModel.loadCompletedCourses` walks `myCourses × allProgressRecords`, groups passed steps by course, and decides completion — pure data work currently living in a ViewModel and pulling two repositories to do it. Expose `suspend fun getCompletedCourses(userId: String): List<CourseCompletion>` on `ProgressRepository` so the ViewModel only observes state.

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=81 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt#L44-L81"}

:::task-stub{title="Move course-completion logic into ProgressRepository"}
1. Add a shared data class `CourseCompletion(val courseId: String?, val courseTitle: String?)` to `model/` (or move the existing one out of `BellDashboardViewModel`).
2. Add `suspend fun getCompletedCourses(userId: String): List<CourseCompletion>` to `ProgressRepository` and implement it in `ProgressRepositoryImpl` using the existing `getProgressRecords` + the courses it already owns (or injecting `CoursesRepository` internally).
3. Replace the whole `loadCompletedCourses` body in `BellDashboardViewModel.kt` (lines 44-81) with `_completedCourses.value = progressRepository.getCompletedCourses(userId)` inside `viewModelScope.launch`.
4. Remove the `coursesRepository` dependency from `BellDashboardViewModel` if no other method uses it.
:::

### Relocate getPrivateImageUrlsCreatedAfter from ResourcesRepository to VoicesRepository

This query is called only from `NewsViewModel` (voices) via `BaseDashboardFragment`. Keeping it on `ResourcesRepository` is a cross-feature leak: voices reaches into the resources repository for a voices-only use case. Move the implementation to `VoicesRepository` and delete it from `ResourcesRepository` so feature boundaries line up with callers.

:codex-file-citation[codex-file-citation]{line_range_start=53 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L53-L55"}
:codex-file-citation[codex-file-citation]{line_range_start=297 line_range_end=304 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L297-L304"}
:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsViewModel.kt#L15-L31"}

:::task-stub{title="Move getPrivateImageUrlsCreatedAfter into VoicesRepository"}
1. Add `suspend fun getPrivateImageUrlsCreatedAfter(timestamp: Long): List<String>` to `repository/VoicesRepository.kt`.
2. Copy the `queryList(RealmMyLibrary::class.java) { equalTo("isPrivate", true).greaterThan(...).equalTo("mediaType","image") }` body from `ResourcesRepositoryImpl:297-304` into `VoicesRepositoryImpl`.
3. Delete the function declaration from `ResourcesRepository.kt:54` and the override in `ResourcesRepositoryImpl.kt:297-304`.
4. In `NewsViewModel.kt`, swap `resourcesRepository` for `voicesRepository`; update imports and the Hilt constructor params.
:::

### Remove redundant withContext(dispatcherProvider.io) calls in SurveysViewModel

`SurveysViewModel.loadSurveys` wraps every `surveysRepository.*` call in `withContext(dispatcherProvider.io)` even though the repository methods already dispatch through `RealmRepository`. The nested switches add noise and sometimes cause unnecessary thread hops. Strip the wrappers; keep one outer `launch` in `viewModelScope`.

:codex-file-citation[codex-file-citation]{line_range_start=69 line_range_end=106 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L69-L106"}
:codex-file-citation[codex-file-citation]{line_range_start=217 line_range_end=231 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L217-L231"}

:::task-stub{title="Drop redundant withContext(dispatcherProvider.io) in SurveysViewModel"}
1. In `loadSurveys` (lines 74-105) remove the four `withContext(dispatcherProvider.io) { ... }` wrappers — call `surveysRepository.*` and `userSessionManager.getUserModel()` directly inside the outer `launch`.
2. In `adoptSurvey` (lines 218-231) inline the `surveysRepository.adoptSurvey(...)` call the same way.
3. Remove the `dispatcherProvider` field if no method still uses it (grep shows `checkServerAndStartSync` still does — leave it if needed).
4. Verify no change in threading by confirming `SurveysRepositoryImpl` methods already `flowOn`/`withContext` internally.
:::

### Fix withContext + collectLatest wrapping in TeamViewModel

`TeamViewModel.loadTasks` collects a repository Flow inside `withContext(dispatcherProvider.io)`, which fights cancellation (the `collectLatest` suspends forever inside the outer `withContext` so cancellation has to race). The Flow is already `flowOn(realmDispatcher)` inside `RealmRepository.queryListFlow`, so the outer dispatch is both redundant and a correctness smell.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=47 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L39-L47"}
:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt#L55-L95"}

:::task-stub{title="Remove withContext around Flow collection in TeamViewModel"}
1. In `loadTasks` (lines 39-47), delete the outer `withContext(dispatcherProvider.io) { ... }` and collect `teamsRepository.getTasksByTeamId(teamId)` directly inside `viewModelScope.launch`.
2. In `loadTeams` (lines 55-95), remove the outer `withContext(dispatcherProvider.io)` — the branches either collect a Flow (already dispatched) or call a suspend repository method that already switches threads.
3. Keep the `withContext(dispatcherProvider.io)` inside `processTeams` only if the filtering/sorting is CPU-bound enough to warrant it; otherwise delete too.
4. Remove unused `dispatcherProvider` imports/fields if no method still uses them.
:::

### Remove redundant withContext(dispatcherProvider.io) in CoursesViewModel

Same anti-pattern in `CoursesViewModel.loadCourses` / `filterCourses`: wrapping already-dispatched `coursesRepository.*` suspend calls in `withContext(dispatcherProvider.io)`. Ranks high for review because the block is tight and the fix is mechanical.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt#L59-L88"}
:codex-file-citation[codex-file-citation]{line_range_start=90 line_range_end=106 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt#L90-L106"}

:::task-stub{title="Drop redundant withContext(dispatcherProvider.io) in CoursesViewModel"}
1. In `loadCourses` (lines 60-87) remove the outer `withContext(dispatcherProvider.io) { ... }` wrapper — keep the inner `coroutineScope { async ... async ... }` unchanged.
2. In `filterCourses` (lines 91-105) do the same.
3. If `dispatcherProvider` is no longer referenced, remove the constructor parameter and update any call sites / Hilt usage.
4. Confirm behavior parity by reading `CoursesRepositoryImpl.getAllCourses/getMyCourses/getCourseRatings/getCourseProgress/filterCourses` — all of them run on `realmDispatcher`.
:::

### Replace withContext(Dispatchers.IO) wrapping LifeRepository calls in LifeFragment

`LifeFragment.getAdapter` wraps `lifeRepository.updateVisibility` and `lifeRepository.updateMyLifeListOrder` in `withContext(Dispatchers.IO)` from the UI layer. The repo's `executeTransaction` already hops to the realm dispatcher — the fragment should just `launch` and call the repo.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=77 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L48-L77"}

:::task-stub{title="Remove Dispatchers.IO wrappers in LifeFragment repository calls"}
1. In `LifeFragment.kt` visibilityCallback (lines 52-62), drop the `withContext(Dispatchers.IO) { ... }` around `lifeRepository.updateVisibility(isVisible, id)`; keep the `launch` in `viewLifecycleOwner.lifecycleScope`.
2. In the reorderCallback (lines 66-70), drop the `withContext(Dispatchers.IO)` around `lifeRepository.updateMyLifeListOrder(list)`.
3. Remove the now-unused `import kotlinx.coroutines.Dispatchers` and `import kotlinx.coroutines.withContext` lines.
4. Sanity check `LifeRepositoryImpl.updateVisibility` and `updateMyLifeListOrder` — both should be using `executeTransaction` from `RealmRepository`.
:::

### Replace polling loop in BellDashboardFragment with a reminder Flow

`BellDashboardFragment.startReminderCheck` runs `while (isActive) { delay(60000) }` forever to poll SharedPreferences for due survey reminders, inside a Fragment. This is a long-running observer that outlives view-binding, can fire after `onDestroyView`, and puts SharedPreferences scanning in the UI layer. Move the due-reminders source to a repository Flow and `collect` from the fragment's lifecycle scope.

:codex-file-citation[codex-file-citation]{line_range_start=244 line_range_end=295 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-4hOtG/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L244-L295"}

:::task-stub{title="Move survey-reminder polling loop into a repository Flow"}
1. Add `fun dueRemindersFlow(): Flow<List<String>>` to `repository/SurveysRepository.kt` (returns comma-joined surveyIds strings whose `reminder_time_*` are due).
2. Implement with `flow { while (true) { emit(scanPrefs(...)); delay(60_000) } }.flowOn(dispatcherProvider.io)` in `SurveysRepositoryImpl`, moving the prefs scan out of the fragment.
3. In `BellDashboardFragment.kt`, delete `startReminderCheck` and `checkScheduledReminders` prefs-scanning code (lines 244-295). Replace with `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { surveysRepository.dueRemindersFlow().collect { ids -> handleDueReminders(ids) } } }`.
4. Keep the existing `submissionsRepository.getSubmissionsByIds` / `showPendingSurveysReminder` logic inside `handleDueReminders`, but delete the `preferences.edit { remove(...) }` loop since the flow owns key removal.
5. Remove `surveyReminderJob` field, the `Dispatchers.IO` import, and the `isActive` import.
:::
