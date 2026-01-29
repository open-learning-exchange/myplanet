### Move direct Realm.getDefaultInstance() out of VoicesActions into VoicesRepository

VoicesActions is a UI-layer utility object that directly calls `Realm.getDefaultInstance()` to query and mutate `RealmNews` records. This breaks repository boundaries by placing data access logic in the UI layer. The existing `VoicesRepository` should expose methods for fetching a news item by ID, creating a reply, and editing a post, so VoicesActions never touches Realm directly.

:codex-file-citation[codex-file-citation]{line_range_start=167 line_range_end=184 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt#L167-L184"}
:codex-file-citation[codex-file-citation]{line_range_start=187 line_range_end=231 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt#L187-L231"}

:::task-stub{title="Move Realm access from VoicesActions to VoicesRepository"}
1. Add `getNewsById(id: String): RealmNews?` method to VoicesRepository interface and implementation
2. Add `postReply(message: String, news: RealmNews, currentUser: RealmUser, imageList: RealmList<String>?)` to VoicesRepository
3. Add `editPost(newsId: String, message: String, imagesToRemove: Set<String>, newImages: RealmList<String>?)` to VoicesRepository
4. Refactor VoicesActions.showEditAlert to accept VoicesRepository and call repository methods instead of Realm.getDefaultInstance()
5. Remove `import io.realm.Realm` from VoicesActions
:::

### Move CourseStepFragment Realm queries into CoursesRepository

CourseStepFragment.loadStepData() performs four direct `realm.where()` calls for RealmCourseStep, RealmMyLibrary, and RealmStepExam inside a `databaseService.withRealmAsync` block. The existing `CoursesRepository` should provide a method to load all step data by stepId, keeping the fragment free of data-layer concerns.

:codex-file-citation[codex-file-citation]{line_range_start=99 line_range_end=129 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt#L99-L129"}

:::task-stub{title="Extract CourseStepFragment data loading into CoursesRepository"}
1. Add a data class `CourseStepData` to CoursesRepository (or a shared model location) holding step, resources, stepExams, stepSurvey
2. Add `suspend fun getCourseStepData(stepId: String): CourseStepData` to CoursesRepository interface
3. Implement the method in CoursesRepositoryImpl using RealmRepository.withRealm to query RealmCourseStep, RealmMyLibrary, and RealmStepExam
4. Replace the `databaseService.withRealmAsync` block in CourseStepFragment.loadStepData() with a call to `coursesRepository.getCourseStepData(stepId)`
5. Remove direct Realm model imports no longer needed in CourseStepFragment
:::

### Move guest login user lookup from GuestLoginExtensions into UserRepository

GuestLoginExtensions performs a direct `realm.where(RealmUser::class.java).equalTo("name", username).findFirst()` inside the UI login flow. The existing `UserRepository` already handles user queries and should expose a `findUserByName` method so the login extension does not cross the repository boundary.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=77 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt#L60-L77"}

:::task-stub{title="Move user lookup from GuestLoginExtensions to UserRepository"}
1. Add `suspend fun findUserByName(name: String): RealmUser?` to UserRepository interface
2. Implement in UserRepositoryImpl using RealmRepository.findByField or withRealm
3. Add `suspend fun createGuestUser(username: String, settings: SharedPreferences): RealmUser?` to UserRepository interface and implement
4. Replace the direct `loginRealm.where(RealmUser::class.java)` call in GuestLoginExtensions with `userRepository.findUserByName(username)`
5. Replace RealmUser.createGuestUser call with userRepository.createGuestUser
6. Remove direct databaseService.withRealm usage from showGuestLoginDialog
:::

### Move UploadManager team upload queries into TeamsRepository

UploadManager.uploadTeams() directly queries `realm.where(RealmMyTeam::class.java).equalTo("updated", true)` and later updates `_rev` via `executeTransactionAsync`. These are pure data operations that belong in the existing `TeamsRepository`, letting UploadManager focus on network coordination only.

:codex-file-citation[codex-file-citation]{line_range_start=432 line_range_end=471 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L432-L471"}

:::task-stub{title="Move team upload data access from UploadManager to TeamsRepository"}
1. Add `suspend fun getUpdatedTeams(): List<Pair<String?, JsonObject>>` to TeamsRepository interface (returns teamId + serialized JSON)
2. Add `suspend fun markTeamUploaded(teamId: String, rev: String)` to TeamsRepository interface
3. Implement both methods in TeamsRepositoryImpl using RealmRepository helpers
4. Replace direct realm.where and executeTransactionAsync in UploadManager.uploadTeams() with repository calls
5. Remove RealmMyTeam import from UploadManager if no longer needed
:::

### Move UploadManager team activity log queries into TeamsRepository

UploadManager.uploadTeamActivities() queries `realm.where(RealmTeamLog::class.java).isNull("_rev")` and updates log records after upload. These data operations should live in TeamsRepository (or a dedicated TeamLogRepository method on TeamsRepository), keeping the upload service layer clean.

:codex-file-citation[codex-file-citation]{line_range_start=543 line_range_end=575 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L543-L575"}

:::task-stub{title="Move team activity log queries from UploadManager to TeamsRepository"}
1. Add `suspend fun getUnuploadedTeamLogs(): List<RealmTeamLog>` to TeamsRepository interface
2. Add `suspend fun updateTeamLogRevision(time: Long, user: String?, type: String?, id: String, rev: String)` to TeamsRepository interface
3. Implement both in TeamsRepositoryImpl using RealmRepository.withRealm
4. Replace direct realm.where(RealmTeamLog::class.java) calls in UploadManager.uploadTeamActivities() with repository calls
5. Remove RealmTeamLog import from UploadManager if no longer referenced
:::

### Move RealmMyCourse companion static queries into CoursesRepository

RealmMyCourse.Companion contains six static methods that perform direct Realm queries (`getCourseSteps`, `getCourseStepIds`, `getCourseByCourseId`, `getMyCourse`, `getMyCourseIds`, `getMyByUserId`). These belong in the existing CoursesRepository to enforce a single data-access boundary for course data.

:codex-file-citation[codex-file-citation]{line_range_start=149 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L149-L159"}
:codex-file-citation[codex-file-citation]{line_range_start=186 line_range_end=192 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L186-L192"}
:codex-file-citation[codex-file-citation]{line_range_start=232 line_range_end=268 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L232-L268"}

:::task-stub{title="Migrate RealmMyCourse companion query methods to CoursesRepository"}
1. Add `suspend fun getCourseSteps(courseId: String): List<RealmCourseStep>` to CoursesRepository interface
2. Add `suspend fun getCourseStepIds(courseId: String): Array<String?>` to CoursesRepository interface
3. Add `suspend fun getCourseByCourseId(courseId: String): RealmMyCourse?` to CoursesRepository interface
4. Implement all three in CoursesRepositoryImpl using RealmRepository.withRealm
5. Deprecate (with @Deprecated) the corresponding companion methods in RealmMyCourse, pointing to the repository
6. Update callers one at a time to use CoursesRepository instead of the static companion methods
:::

### Move RealmMyLibrary companion query methods into ResourcesRepository

RealmMyLibrary.Companion has `getMyLibraryByUserId`, `getIds`, `removeDeletedResource`, and `getMyLibIds` that perform direct Realm queries and transactions. The existing ResourcesRepository should own these operations to enforce a clean data boundary.

:codex-file-citation[codex-file-citation]{line_range_start=187 line_range_end=195 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L187-L195"}
:codex-file-citation[codex-file-citation]{line_range_start=207 line_range_end=241 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L207-L241"}
:codex-file-citation[codex-file-citation]{line_range_start=391 line_range_end=396 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L391-L396"}

:::task-stub{title="Migrate RealmMyLibrary companion query methods to ResourcesRepository"}
1. Add `suspend fun getLibraryByUserId(userId: String): List<RealmMyLibrary>` to ResourcesRepository interface
2. Add `suspend fun removeDeletedResources(currentIds: List<String?>)` to ResourcesRepository interface
3. Add `suspend fun getMyLibIds(userId: String): JsonArray` to ResourcesRepository interface
4. Implement all in ResourcesRepositoryImpl using RealmRepository.withRealm
5. Deprecate the corresponding companion methods in RealmMyLibrary with @Deprecated pointing to ResourcesRepository
6. Update callers incrementally to use ResourcesRepository
:::

### Replace GlobalScope.launch in UserInformationFragment with application-scoped coroutine

UserInformationFragment.checkAvailableServer() uses `GlobalScope.launch(Dispatchers.IO)` for server reachability checks and upload. GlobalScope survives the fragment but is not structured-concurrency safe, risks leaks, and bypasses DI scope. The codebase already has an `ApplicationScopeEntryPoint` providing an application-scoped CoroutineScope that should be used instead.

:codex-file-citation[codex-file-citation]{line_range_start=322 line_range_end=374 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L322-L374"}

:::task-stub{title="Replace GlobalScope with injected application-scoped CoroutineScope"}
1. Inject the application-scoped CoroutineScope into UserInformationFragment via Hilt (already available via ApplicationScopeEntryPoint or @Inject)
2. Replace `GlobalScope.launch(Dispatchers.IO)` with `applicationScope.launch(Dispatchers.IO)` in checkAvailableServer()
3. Remove `import kotlinx.coroutines.GlobalScope` from UserInformationFragment
4. Verify upload still completes after fragment is destroyed by testing survey submission flow
:::

### Replace notifyDataSetChanged in ResourcesAdapter with targeted notify

ResourcesAdapter.setOpenedResourceIds() calls `notifyDataSetChanged()` despite the adapter being a ListAdapter with DiffUtil already configured. This bypasses efficient diff calculation and causes full rebind of all visible items. A targeted `notifyItemChanged` with a payload or re-submitting the list would preserve smooth animations and performance.

:codex-file-citation[codex-file-citation]{line_range_start=208 line_range_end=211 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L208-L211"}

:::task-stub{title="Replace notifyDataSetChanged with payload-based notify in ResourcesAdapter"}
1. Define a new payload constant `OPENED_RESOURCE_PAYLOAD` in the companion object
2. In setOpenedResourceIds(), iterate currentList and call notifyItemChanged(index, OPENED_RESOURCE_PAYLOAD) for items whose opened state changed
3. Handle OPENED_RESOURCE_PAYLOAD in the existing onBindViewHolder(holder, position, payloads) to update only the download icon visibility
4. Remove the notifyDataSetChanged() call from setOpenedResourceIds()
:::

### Move RealmMyTeam companion static queries into TeamsRepository

RealmMyTeam.Companion contains `getResourceIdsByUser`, `getResourceIds`, `getUsers`, `getJoinedMember`, `getRequestedMember`, and `isTeamLeader` which all perform direct Realm queries. These cross-feature data lookups (especially `getResourceIdsByUser` which is called from RealmMyLibrary) should live in TeamsRepository to prevent cross-feature data leaks.

:codex-file-citation[codex-file-citation]{line_range_start=166 line_range_end=189 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L166-L189"}
:codex-file-citation[codex-file-citation]{line_range_start=218 line_range_end=244 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L218-L244"}
:codex-file-citation[codex-file-citation]{line_range_start=295 line_range_end=307 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyTeam.kt#L295-L307"}

:::task-stub{title="Migrate RealmMyTeam companion query methods to TeamsRepository"}
1. Add `suspend fun getResourceIdsByUser(userId: String): List<String>` to TeamsRepository interface
2. Add `suspend fun getTeamUsers(teamId: String, docType: String): List<RealmUser>` to TeamsRepository interface
3. Add `suspend fun getMyTeamsByUserId(userId: String): List<RealmMyTeam>` to TeamsRepository interface
4. Implement all in TeamsRepositoryImpl using RealmRepository.withRealm
5. Deprecate the corresponding companion methods in RealmMyTeam with @Deprecated pointing to TeamsRepository
6. Update RealmMyLibrary.getMyLibraryByUserId to accept resource IDs as a parameter instead of calling RealmMyTeam.getResourceIdsByUser directly (breaks cross-feature data leak)
:::
