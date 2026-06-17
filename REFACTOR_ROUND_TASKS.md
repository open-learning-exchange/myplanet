### Push isMyCourse ordering into the Realm query in getCoursesByFilter

The filtered-courses query sorts by courseTitle in Realm and then re-sorts the whole result list in memory by isMyCourse, allocating an extra list and a second sort pass. Since isMyCourse is a persisted Realm field, the secondary ordering can be done natively in the query, removing the in-memory sort entirely.

:codex-file-citation[codex-file-citation]{line_range_start=322 line_range_end=324 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L322-L324"}

:::task-stub{title="Push isMyCourse ordering into the Realm query in getCoursesByFilter"}
1. Locate the query that ends with sort("courseTitle", ASCENDING).findAll() followed by an in-memory sortedBy { it.isMyCourse }.
2. Replace the single-field sort with a multi-field Realm sort keyed on isMyCourse ascending, then courseTitle ascending.
3. Delete the intermediate sortedList and return copyFromRealm of the query results directly, keeping maxDepth 0.
4. Run the courses repository unit tests to confirm the resulting ordering is unchanged.
:::

### Remove the adapter-level ViewBinding field in ProgressGridAdapter

ProgressGridAdapter keeps the inflated RowMyProgressGridBinding as a class-level lateinit field that is overwritten on every onCreateViewHolder call, which is an unnecessary shared reference and a recycling foot-gun. The binding only needs to live inside the ViewHolder.

:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=26 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressGridAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressGridAdapter.kt#L21-L26"}
:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=54 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressGridAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressGridAdapter.kt#L51-L54"}

:::task-stub{title="Remove the adapter-level ViewBinding field in ProgressGridAdapter"}
1. Delete the class-level lateinit rowMyProgressGridBinding field.
2. Inflate RowMyProgressGridBinding as a local val inside onCreateViewHolder and pass it straight to the ViewHolderMyProgress constructor.
3. Confirm the ViewHolder already exposes the views it needs so nothing else references the removed field.
:::

### Replace the inline DiffUtil.ItemCallback with DiffUtils.itemCallback in ResourcesAdapter

ResourcesAdapter still defines a hand-written object : DiffUtil.ItemCallback<ResourceListModel> while the rest of the codebase uses the shared DiffUtils.itemCallback helper. Converting it consolidates the diff logic and matches the established convention without changing behavior.

:codex-file-citation[codex-file-citation]{line_range_start=48 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L48-L67"}

:::task-stub{title="Replace the inline DiffUtil.ItemCallback with DiffUtils.itemCallback in ResourcesAdapter"}
1. Replace the ITEM_CALLBACK object that extends DiffUtil.ItemCallback<ResourceListModel> with a call to the shared DiffUtils.itemCallback helper.
2. Pass the existing areItemsTheSame, areContentsTheSame, and getChangePayload bodies as the helper's three lambda arguments.
3. Keep the payload constants and the getChangePayload result identical so partial-bind updates stay the same.
:::

### Use the injected DispatcherProvider in BaseRecyclerFragment.deleteCourseProgress

deleteCourseProgress hard-codes kotlinx.coroutines.Dispatchers.IO even though the fragment already injects a DispatcherProvider, which breaks the project convention and prevents deterministic dispatchers in tests.

:codex-file-citation[codex-file-citation]{line_range_start=211 line_range_end=216 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L211-L216"}
:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L22-L22"}

:::task-stub{title="Use the injected DispatcherProvider in BaseRecyclerFragment.deleteCourseProgress"}
1. In deleteCourseProgress, replace launch(kotlinx.coroutines.Dispatchers.IO) with launch(dispatcherProvider.io) using the already-injected field.
2. Drop the fully-qualified Dispatchers reference from the call.
3. Confirm no other hard-coded Dispatchers.* remains in the file.
:::

### Use the injected DispatcherProvider in MainApplication.performDeferredInitialization

The deferred warm-up coroutine launches on Dispatchers.IO directly, while the rest of MainApplication already routes through the injected dispatcherProvider. Aligning this one call keeps threading consistent and testable.

:codex-file-citation[codex-file-citation]{line_range_start=237 line_range_end=243 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L237-L243"}
:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=74 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L74-L74"}

:::task-stub{title="Use the injected DispatcherProvider in MainApplication.performDeferredInitialization"}
1. In performDeferredInitialization, replace applicationScope.launch(Dispatchers.IO) with applicationScope.launch(dispatcherProvider.io).
2. Reuse the existing dispatcherProvider field already referenced elsewhere in the class.
3. Leave the unrelated isServerReachable signature untouched, and keep the Dispatchers import only if it is still referenced.
:::

### Cache the EntryPoint lookup in UrlUtils

UrlUtils.spm() calls EntryPointAccessors.fromApplication on every invocation, and each accessor (header, hostUrl, and the rest) calls spm() again, so a single property read performs repeated Hilt entry-point resolutions. Caching the resolved SharedPrefManager removes that per-access overhead.

:codex-file-citation[codex-file-citation]{line_range_start=11 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt#L11-L20"}

:::task-stub{title="Cache the EntryPoint lookup in UrlUtils"}
1. Replace the per-call spm() that invokes EntryPointAccessors.fromApplication with a lazily-initialized SharedPrefManager reference.
2. Update header, hostUrl, and the other accessors to read the cached reference instead of re-resolving.
3. Confirm SharedPrefManager is a @Singleton so the cached reference cannot go stale.
:::

### Inject the shared Gson into SharedPrefManager

SharedPrefManager is a @Singleton @Inject-constructed service that creates its own private Gson() instead of reusing the configured instance NetworkModule already provides, diverging from the app-wide serializer settings and allocating a redundant Gson.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=26 path=app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/services/SharedPrefManager.kt#L24-L26"}

:::task-stub{title="Inject the shared Gson into SharedPrefManager"}
1. Add a Gson constructor parameter so Hilt supplies the configured instance from NetworkModule.
2. Remove the private val gson = Gson() initializer and use the injected instance.
3. Build to confirm Hilt resolves Gson, which is already provided in NetworkModule.
:::

### Reuse a single injected Gson in TeamCalendarViewModel.createMeetup

createMeetup constructs two separate Gson() instances within the same method to serialize small JSON objects. Injecting one shared Gson removes the duplicate allocations and matches the DI pattern used elsewhere.

:codex-file-citation[codex-file-citation]{line_range_start=68 line_range_end=78 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarViewModel.kt#L68-L78"}

:::task-stub{title="Reuse a single injected Gson in TeamCalendarViewModel.createMeetup"}
1. Add a Gson constructor parameter to the ViewModel so Hilt provides it.
2. Replace both Gson().toJson(...) calls in createMeetup with the injected instance.
3. Confirm no other ad-hoc Gson() remains in the file.
:::

### Expose CoursesViewModel state as read-only StateFlow

CoursesViewModel publishes its backing MutableStateFlow values directly as the public coursesState and syncStatus, so callers can cast them back to mutable and write to them. Wrapping them with asStateFlow() enforces one-way data flow with no behavior change.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=53 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt#L49-L53"}

:::task-stub{title="Expose CoursesViewModel state as read-only StateFlow"}
1. Wrap the public coursesState and syncStatus with asStateFlow() over their backing MutableStateFlow fields.
2. Keep _coursesState and _syncStatus private and otherwise unchanged.
3. Confirm collectors are unaffected since emitted values are identical.
:::

### Store and remove the etAnswer TextWatcher in ExamTakingFragment

ExamTakingFragment adds an anonymous TextWatcher to binding.etAnswer that is never stored or removed, so it leaks across the fragment view lifecycle. Holding a reference and removing it in onDestroyView fixes the leak.

:codex-file-citation[codex-file-citation]{line_range_start=191 line_range_end=212 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/pensive-noether-131m41/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L191-L212"}

:::task-stub{title="Store and remove the etAnswer TextWatcher in ExamTakingFragment"}
1. Assign the anonymous TextWatcher added to binding.etAnswer to a nullable fragment field instead of an inline object.
2. In onDestroyView, call binding.etAnswer.removeTextChangedListener on the stored watcher and clear the field.
3. Verify the watcher is distinct from BaseExamFragment's existing answerTextWatcher so neither is double-registered.
:::

### Testing

Each task is isolated to a single file to avoid merge conflicts across the review round. After each change, run ./gradlew testDefaultDebugUnitTest (the suite CI enforces) and, for the adapter and fragment changes, smoke-test the affected screen in a debug build.
