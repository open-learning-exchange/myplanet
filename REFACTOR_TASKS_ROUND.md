# Performance Quick Wins - Refactor Tasks

Generated: 2026-01-09

## Overview
10 granular, low-risk micro-optimizations focused on performance quick wins that unblock bigger refactors later. Each task is designed for minimal merge conflict risk and easy review.

---

### 1. Migrate TeamCoursesAdapter to ListAdapter with DiffUtils

TeamCoursesAdapter extends RecyclerView.Adapter directly without DiffUtil support, causing full list redraws on any data change. The codebase already has a DiffUtils.itemCallback() helper that should be used.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=64 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt#L17-L64"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=38 path=app/src/main/java/org/ole/planet/myplanet/utilities/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/DiffUtils.kt#L1-L38"}

:::task-stub{title="Migrate TeamCoursesAdapter to ListAdapter"}
1. Change class to extend ListAdapter<RealmMyCourse, TeamCoursesAdapter.ViewHolder>
2. Create DiffUtil.ItemCallback using DiffUtils.itemCallback() comparing courseId and courseTitle
3. Replace list property access with currentList
4. Remove getItemCount() override as ListAdapter handles it
5. Update callers to use submitList() instead of direct list assignment
:::

---

### 2. Fix PanelSlideListener memory leak in ChatHistoryFragment

ChatHistoryOnBackPressedCallback adds a PanelSlideListener in init but never removes it, creating a memory leak. The listener should be removed when the callback is no longer needed.

:codex-file-citation[codex-file-citation]{line_range_start=404 line_range_end=423 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L404-L423"}

:::task-stub{title="Fix PanelSlideListener leak in ChatHistoryOnBackPressedCallback"}
1. Add remove() method to ChatHistoryOnBackPressedCallback that calls slidingPaneLayout.removePanelSlideListener(this)
2. Store the callback instance as a property in ChatHistoryFragment
3. Call callback.remove() in onDestroyView() before calling super
4. Verify no memory leaks with LeakCanary or profiler
:::

---

### 3. Fix Player.Listener leak in AudioPlayerActivity

AudioPlayerActivity adds a Player.Listener but never explicitly removes it before releasing the player. The listener should be removed before release to ensure proper cleanup.

:codex-file-citation[codex-file-citation]{line_range_start=110 line_range_end=120 path=app/src/main/java/org/ole/planet/myplanet/ui/reader/AudioPlayerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/reader/AudioPlayerActivity.kt#L110-L120"}

:codex-file-citation[codex-file-citation]{line_range_start=185 line_range_end=190 path=app/src/main/java/org/ole/planet/myplanet/ui/reader/AudioPlayerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/reader/AudioPlayerActivity.kt#L185-L190"}

:::task-stub{title="Fix Player.Listener leak in AudioPlayerActivity"}
1. Store the Player.Listener as a class property (private var playerListener: Player.Listener? = null)
2. Assign the listener object to this property in setupPlayer()
3. In onDestroy(), call exoPlayer?.removeListener(playerListener) before exoPlayer?.release()
4. Set playerListener = null after removal
:::

---

### 4. Replace GlobalScope with application-scoped coroutine in UserInformationFragment

GlobalScope is an anti-pattern that bypasses structured concurrency. This upload operation needs to survive fragment lifecycle, so it should use an application-scoped CoroutineScope instead.

:codex-file-citation[codex-file-citation]{line_range_start=320 line_range_end=354 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L320-L354"}

:::task-stub{title="Replace GlobalScope with application CoroutineScope"}
1. Add applicationScope: CoroutineScope to MainApplication as @Singleton with SupervisorJob + Dispatchers.IO
2. Provide applicationScope via Hilt in ServiceModule
3. Inject applicationScope into UserInformationFragment
4. Replace GlobalScope.launch with applicationScope.launch
5. Remove GlobalScope import
:::

---

### 5. Inject DataService in BaseResourceFragment instead of manual instantiation

BaseResourceFragment creates DataService instances manually in three places instead of using dependency injection. This bypasses DI and makes testing difficult.

:codex-file-citation[codex-file-citation]{line_range_start=188 line_range_end=224 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L188-L224"}

:::task-stub{title="Inject DataService in BaseResourceFragment"}
1. Add @Inject lateinit var dataService: DataService to BaseResourceFragment
2. Replace DataService(requireContext()) at line 190 with dataService
3. Replace DataService(requireActivity()) at line 314 with dataService
4. Replace DataService(context) at line 540 with dataService
5. Verify BaseResourceFragment has @AndroidEntryPoint annotation
:::

---

### 6. Replace runOnUiThread with withContext(Dispatchers.Main) in VideoViewerActivity

VideoViewerActivity uses runOnUiThread callbacks which is a legacy pattern. Since the code already uses coroutines, these should use withContext(Dispatchers.Main) for consistency and better structured concurrency.

:codex-file-citation[codex-file-citation]{line_range_start=89 line_range_end=105 path=app/src/main/java/org/ole/planet/myplanet/ui/reader/VideoViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/reader/VideoViewerActivity.kt#L89-L105"}

:::task-stub{title="Replace runOnUiThread with coroutines in VideoViewerActivity"}
1. Ensure the callback methods are called within a coroutine context
2. Replace runOnUiThread at line 91 with withContext(Dispatchers.Main)
3. Replace runOnUiThread at line 104 with withContext(Dispatchers.Main)
4. If callbacks are not in coroutine context, wrap with lifecycleScope.launch
5. Import kotlinx.coroutines.Dispatchers and kotlinx.coroutines.withContext
:::

---

### 7. Replace runOnUiThread with coroutines in BecomeMemberActivity

BecomeMemberActivity uses runOnUiThread in callback handlers. These should be replaced with coroutine-based UI updates for consistency with the rest of the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=143 line_range_end=152 path=app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt#L143-L152"}

:::task-stub{title="Replace runOnUiThread with coroutines in BecomeMemberActivity"}
1. Modify CreateUserCallback.onSuccess to use lifecycleScope.launch { } for toast
2. Modify SecurityDataListener.onSecurityDataUpdated to use lifecycleScope.launch { }
3. Move dialog dismiss and autoLoginNewMember calls inside the coroutine block
4. Remove runOnUiThread calls
5. Import androidx.lifecycle.lifecycleScope
:::

---

### 8. Create DispatcherProvider interface for testable dispatcher injection

The codebase has 150+ hardcoded Dispatchers.IO/Main usages. Creating a DispatcherProvider interface enables gradual migration and makes code testable. This is a foundational change that unblocks larger dispatcher refactors.

:codex-file-citation[codex-file-citation]{line_range_start=16 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/data/DatabaseService.kt#L16-L16"}

:::task-stub{title="Create DispatcherProvider interface for DI"}
1. Create DispatcherProvider interface in di/ package with io, main, default properties
2. Create DefaultDispatcherProvider class implementing DispatcherProvider
3. Add @Binds in ServiceModule to bind DefaultDispatcherProvider to DispatcherProvider
4. Inject DispatcherProvider in DatabaseService and replace Dispatchers.IO at line 16
5. Add TestDispatcherProvider in test sources using TestCoroutineDispatcher
:::

---

### 9. Inject DataService in AutoSyncWorker via EntryPoint pattern

AutoSyncWorker creates DataService instances manually in two places. Since workers already use the EntryPoint pattern for other dependencies, DataService should be added to the entry point.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=52 path=app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt#L52-L52"}

:codex-file-citation[codex-file-citation]{line_range_start=78 line_range_end=78 path=app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/AutoSyncWorker.kt#L78-L78"}

:::task-stub{title="Inject DataService in AutoSyncWorker via EntryPoint"}
1. Add fun dataService(): DataService to AutoSyncEntryPoint interface
2. Get dataService from entryPoint in doWork() method
3. Replace DataService(context) at line 52 with entryPoint.dataService()
4. Replace DataService(MainApplication.context) at line 78 with entryPoint.dataService()
5. Verify DataService is provided in DI module with @Singleton scope
:::

---

### 10. Convert ReferencesFragment anonymous adapter to named class with DiffUtil

ReferencesFragment uses an anonymous RecyclerView.Adapter class without DiffUtil. Converting to a named class with ListAdapter improves performance and maintainability.

:codex-file-citation[codex-file-citation]{line_range_start=42 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesFragment.kt#L42-L65"}

:::task-stub{title="Convert ReferencesFragment adapter to ListAdapter"}
1. Create ReferencesAdapter class extending ListAdapter<Reference, ReferencesAdapter.ViewHolder>
2. Use DiffUtils.itemCallback() comparing title property for item identity and equality
3. Move ViewHolderReference into ReferencesAdapter as ViewHolder inner class
4. Pass click lambda to adapter constructor for navigation handling
5. Replace anonymous adapter with ReferencesAdapter instance using submitList()
:::

---

## Summary

| # | Task | Files Changed | Risk | Impact |
|---|------|---------------|------|--------|
| 1 | TeamCoursesAdapter ListAdapter | 1-2 | Low | Performance |
| 2 | ChatHistory PanelSlideListener leak | 1 | Low | Memory |
| 3 | AudioPlayer listener leak | 1 | Low | Memory |
| 4 | GlobalScope replacement | 2-3 | Medium | Threading |
| 5 | BaseResourceFragment DI | 1 | Low | DI cleanup |
| 6 | VideoViewer runOnUiThread | 1 | Low | Threading |
| 7 | BecomeMember runOnUiThread | 1 | Low | Threading |
| 8 | DispatcherProvider interface | 3-4 | Low | DI foundation |
| 9 | AutoSyncWorker DataService DI | 1-2 | Low | DI cleanup |
| 10 | ReferencesFragment adapter | 1 | Low | Performance |

**Total PRs**: 10
**Estimated Merge Conflicts**: Minimal (each task touches isolated files)
