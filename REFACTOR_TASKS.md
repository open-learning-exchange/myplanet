# Refactor Tasks: Repository Boundaries & Data Layer Cleanup

This document contains 10 granular, low-conflict tasks focused on reinforcing repository boundaries, fixing data layer issues, and moving functions to proper layers.

---

### 1. Fix manual Realm transaction control in ActivitiesRepositoryImpl

The `markResourceAdded()` and `markResourceRemoved()` methods use manual `beginTransaction()`/`commitTransaction()` instead of the safer `executeTransaction()` helper from `RealmRepository` base class. This bypasses error handling and can leave transactions in an invalid state on exceptions.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L39-L60"}

:::task-stub{title="Use executeTransaction helper in ActivitiesRepositoryImpl"}
1. Replace manual `beginTransaction()`/`commitTransaction()` in `markResourceAdded()` with `executeTransaction{}` block
2. Replace manual `beginTransaction()`/`commitTransaction()` in `markResourceRemoved()` with `executeTransaction{}` block
3. Remove redundant `isInTransaction` checks as `executeTransaction` handles this internally
:::

---

### 2. Fix ResourcesAdapter using notifyDataSetChanged instead of payload-based update

The `setOpenedResourceIds()` method uses `notifyDataSetChanged()` which rebinds all items unnecessarily. The adapter already has `DIFF_CALLBACK` and supports payloads for other operations (tags, rating, selection), so this should use a payload-based approach for consistency and performance.

:codex-file-citation[codex-file-citation]{line_range_start=212 line_range_end=215 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L212-L215"}

:::task-stub{title="Use payload-based update in ResourcesAdapter.setOpenedResourceIds"}
1. Add a new constant `OPENED_PAYLOAD = "payload_opened"` to companion object
2. Replace `notifyDataSetChanged()` with `notifyItemRangeChanged(0, currentList.size, OPENED_PAYLOAD)`
3. Handle `OPENED_PAYLOAD` in the existing `onBindViewHolder(holder, position, payloads)` override to update only the download indicator visibility
:::

---

### 3. Add proper error logging to ExamSubmissionUtils async transaction

The `executeTransactionAsync()` call has empty success and error callbacks which silently swallows failures. This makes debugging exam submission issues difficult and violates proper error handling patterns.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=54 path=app/src/main/java/org/ole/planet/myplanet/utils/ExamSubmissionUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/ExamSubmissionUtils.kt#L24-L54"}

:::task-stub{title="Add error logging to ExamSubmissionUtils.saveAnswer transaction"}
1. Add `import android.util.Log` if not present
2. Replace empty error callback `{ _ -> }` with proper logging: `{ error -> Log.e("ExamSubmissionUtils", "Failed to save answer", error) }`
3. Optionally add debug logging in success callback for transaction confirmation
:::

---

### 4. Wrap Flow collectors with repeatOnLifecycle in SubmissionDetailFragment

The `observeViewModel()` method launches 5 separate Flow collectors without `repeatOnLifecycle()`. These collectors continue running when the fragment is stopped, causing unnecessary processing and potential memory leaks.

:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=106 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailFragment.kt#L80-L106"}

:::task-stub{title="Add repeatOnLifecycle to SubmissionDetailFragment Flow collectors"}
1. Add import for `androidx.lifecycle.repeatOnLifecycle` and `androidx.lifecycle.Lifecycle`
2. Wrap the outer `lifecycleScope.launch` block with `repeatOnLifecycle(Lifecycle.State.STARTED)`
3. Consolidate the 5 separate launch blocks into a single launch with repeatOnLifecycle containing multiple collect calls using `launch` for each
:::

---

### 5. Move direct Realm query from TeamCoursesFragment to CoursesRepository

The `setupCoursesList()` method directly queries Realm with `mRealm.where(RealmMyCourse::class.java)` violating the repository pattern. This query should be moved to `CoursesRepository` to maintain proper layer separation.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt#L28-L36"}

:::task-stub{title="Move team courses query to CoursesRepository"}
1. Add method `suspend fun getCoursesByIds(courseIds: List<String>): List<RealmMyCourse>` to `CoursesRepository` interface
2. Implement the method in `CoursesRepositoryImpl` using `queryList` helper
3. Inject `CoursesRepository` into `TeamCoursesFragment` and replace direct Realm query with repository call
4. Make `setupCoursesList()` use `lifecycleScope.launch` to call the suspend function
:::

---

### 6. Wrap Flow collector with repeatOnLifecycle in DashboardActivity

The `setupDashboardDataObserver()` method collects a Flow without `repeatOnLifecycle()`. This collector continues running when the activity is in the background, triggering unnecessary realm change processing.

:codex-file-citation[codex-file-citation]{line_range_start=580 line_range_end=586 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L580-L586"}

:::task-stub{title="Add repeatOnLifecycle to DashboardActivity Flow collector"}
1. Add import for `androidx.lifecycle.repeatOnLifecycle` and `androidx.lifecycle.Lifecycle`
2. Wrap the Flow collection with `repeatOnLifecycle(Lifecycle.State.STARTED)` inside the launch block
3. Verify the dashboard still refreshes properly when returning to foreground
:::

---

### 7. Wrap Flow collector with repeatOnLifecycle in ResourcesFragment

The Flow collector for `observeOpenedResourceIds` continues running when the fragment is stopped. This causes adapter updates to fire even when the UI is not visible.

:codex-file-citation[codex-file-citation]{line_range_start=242 line_range_end=250 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L242-L250"}

:::task-stub{title="Add repeatOnLifecycle to ResourcesFragment opened resources observer"}
1. Add import for `androidx.lifecycle.repeatOnLifecycle` and `androidx.lifecycle.Lifecycle`
2. Wrap the `collect` call with `repeatOnLifecycle(Lifecycle.State.STARTED)` inside the launch block
3. Ensure the adapter update only happens when fragment is active
:::

---

### 8. Add error logging to RealmUserModel.cleanupDuplicateUsers transaction

The `cleanupDuplicateUsers()` method has a completely empty error callback on `executeTransactionAsync()`. Failed duplicate cleanup operations are silently ignored, making it impossible to diagnose issues.

:codex-file-citation[codex-file-citation]{line_range_start=484 line_range_end=510 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUserModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmUserModel.kt#L484-L510"}

:::task-stub{title="Add error logging to cleanupDuplicateUsers transaction"}
1. Replace empty error callback `{ }` with proper logging: `{ error -> Log.e("RealmUserModel", "Failed to cleanup duplicate users", error) }`
2. Add import for `android.util.Log` if not present at top of file
:::

---

### 9. Add error handler to TransactionSyncManager.syncHealthData transaction

The `databaseService.executeTransactionAsync` call for updating user health keys has no error callback. Failed health key sync operations are silently ignored which could leave user data in an inconsistent state.

:codex-file-citation[codex-file-citation]{line_range_start=96 line_range_end=102 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L96-L102"}

:::task-stub{title="Add error callback to TransactionSyncManager health sync transaction"}
1. Add error callback lambda to the `executeTransactionAsync` call
2. Log the error with appropriate tag: `Log.e("TransactionSyncManager", "Failed to sync health key/iv", error)`
3. Ensure any existing success callbacks are preserved
:::

---

### 10. Migrate HealthUsersAdapter from ArrayAdapter to ListAdapter with DiffUtil

The `HealthUsersAdapter` uses the legacy `ArrayAdapter` pattern which doesn't support efficient diff-based updates. The calling code in `MyHealthFragment` uses `notifyDataSetChanged()` which rebinds all items. Migrating to `ListAdapter` with `DiffUtils.itemCallback` would improve consistency with other adapters.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=61 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt#L1-L61"}

:::task-stub{title="Migrate HealthUsersAdapter to ListAdapter with DiffUtil"}
1. Change class to extend `ListAdapter<RealmUserModel, HealthUsersAdapter.ViewHolder>` instead of `ArrayAdapter`
2. Create inner `ViewHolder` class extending `RecyclerView.ViewHolder`
3. Add `DiffUtil.ItemCallback` using `DiffUtils.itemCallback` utility comparing by `id`
4. Convert `getView()` to `onCreateViewHolder()` and `onBindViewHolder()` pattern
5. Update `MyHealthFragment` to use `submitList()` instead of `notifyDataSetChanged()`
:::

---

## Summary

| # | Task | File | Type | Conflict Risk |
|---|------|------|------|---------------|
| 1 | Fix manual transaction control | ActivitiesRepositoryImpl.kt | Repository boundary | Low |
| 2 | Use payload-based update | ResourcesAdapter.kt | DiffUtil pattern | Low |
| 3 | Add error logging | ExamSubmissionUtils.kt | Error handling | Low |
| 4 | Add repeatOnLifecycle | SubmissionDetailFragment.kt | Threading/lifecycle | Low |
| 5 | Move query to repository | TeamCoursesFragment.kt + CoursesRepository | Repository boundary | Low |
| 6 | Add repeatOnLifecycle | DashboardActivity.kt | Threading/lifecycle | Low |
| 7 | Add repeatOnLifecycle | ResourcesFragment.kt | Threading/lifecycle | Low |
| 8 | Add error logging | RealmUserModel.kt | Error handling | Low |
| 9 | Add error callback | TransactionSyncManager.kt | Error handling | Low |
| 10 | Migrate to ListAdapter | HealthUsersAdapter.kt | DiffUtil/ListAdapter | Low |

All tasks target different files to minimize merge conflicts during parallel review.
