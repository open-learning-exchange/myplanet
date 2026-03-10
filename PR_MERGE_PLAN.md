# PR Merge Plan

We have evaluated all open PRs tagged with `ready to merge` that are greater than or equal to #11781. The PRs have been ranked by importance from 1 to 100 based on their diffs, memory guidelines, and overall impact on the codebase.

## 1. Sorted PRs by Importance

1. **PR #11781: all: smoother shared preferences managing (fixes #11780)**
   - **Importance:** 95/100
   - **Reasoning:** Foundational architectural refactor. Introduces a centralized `SharedPrefManager` via dependency injection (`AutoSyncEntryPoint`, etc.) to replace direct `SharedPreferences` usage across core sync services and utility classes. Due to its broad impact, this should be merged first to establish the new pattern and avoid cascading conflicts in other refactoring tasks.

2. **PR #11810: 🔒 [security fix] Implement secure password storage using EncryptedSharedPreferences and Tink**
   - **Importance:** 94/100
   - **Reasoning:** Critical security patch. Migrates to `EncryptedSharedPreferences` for password storage. Should be merged immediately after #11781 (since #11810 touches `ProcessUserDataActivity.kt` which is also modified by #11781).

3. **PR #11792: Simplify batch write-path for TransactionSync**
   - **Importance:** 90/100
   - **Reasoning:** Core sync performance and safety improvement. It removes unnecessary nesting (`withRealm { realm.executeTransactionAsync }`) in favor of direct async execution.

4. **PR #11816: ⚡ Optimize N+1 query in UploadManager team activities sync**
   - **Importance:** 88/100
   - **Reasoning:** Core sync performance optimization. Fixes N+1 database querying during uploads.

5. **PR #11815: ⚡ Optimize N+1 query in uploadSubmitPhotos by batching database reads**
   - **Importance:** 87/100
   - **Reasoning:** Core sync performance optimization. Fixes N+1 queries during photo uploads.

6. **PR #11811: Normalize UploadToShelfService DI and threading**
   - **Importance:** 85/100
   - **Reasoning:** Architecture and DI cleanup for UploadToShelfService.

7. **PR #11797: Optimize and serialize Realm Flow Dispatch in RealmRepository**
   - **Importance:** 85/100
   - **Reasoning:** Resolves concurrent database UI updates causing `IllegalStateException` crashes by properly conflating Realm Flows.

8. **PR #11809: ⚡ Optimize Course Questions Retrieval (N+1 Query)**
   - **Importance:** 82/100
   - **Reasoning:** Significant database optimization resolving N+1 queries during course retrieval.

9. **PR #11791: Add jsonUtils and sharedPrefManager tests and test dependencies**
   - **Importance:** 80/100
   - **Reasoning:** Adds foundational test dependencies (JUnit 4, Robolectric, MockK, etc.) and tests.

10. **PR #11794: Refactor BaseRecyclerParentFragment Realm queries to use Repositories**
    - **Importance:** 78/100
    - **Reasoning:** Continues the architectural migration of Realm queries from Fragments to Repositories.

11. **PR #11785: Refactor deleteCourseProgress into CoursesRepository**
    - **Importance:** 77/100
    - **Reasoning:** Migrates deletion logic into Repositories.

12. **PR #11790: Optimize Realm deletion operations in BaseRecyclerFragment**
    - **Importance:** 76/100
    - **Reasoning:** Optimizes deletion iterations in the base UI layer.

13. **PR #11803: Standardize CoroutineScope usage in UI adapters**
    - **Importance:** 75/100
    - **Reasoning:** Fixes memory leaks and scope misuse by removing CoroutineScopes from Adapters (as per memory guidelines).

14. **PR #11820: Courses smoother select all (fixes #11819)**
    - **Importance:** 72/100
    - **Reasoning:** UI functionality fix for the 'Select All' feature in Course adapters.

15. **PR #11789: fix: optimize SubmissionsAdapter ViewHolder**
    - **Importance:** 70/100
    - **Reasoning:** UI performance optimization.

16. **PR #11788: Refactor CourseDetailFragment to use CourseDetailViewModel**
    - **Importance:** 69/100
    - **Reasoning:** MVVM refactoring for course details.

17. **PR #11786: Refactor CourseProgressActivity to use ViewModel**
    - **Importance:** 68/100
    - **Reasoning:** MVVM refactoring for course progress.

18. **PR #11807: fix: wrap SurveyFragment collectors in repeatOnLifecycle**
    - **Importance:** 65/100
    - **Reasoning:** Lifecycle safety for flow collections.

19. **PR #11806: Refactor TakeCourseFragment: Inline withContext(Dispatchers.Main) and clean up imports**
    - **Importance:** 65/100
    - **Reasoning:** Coroutine cleanup and optimization.

20. **PR #11805: Refactor coroutine scope in TeamsTasksFragment**
    - **Importance:** 65/100
    - **Reasoning:** ViewLifecycleOwner coroutine scope fix.

21. **PR #11802: Fix lifecycle scope in CoursesProgressFragment**
    - **Importance:** 65/100
    - **Reasoning:** ViewLifecycleOwner coroutine scope fix.

22. **PR #11801: Refactor: Wrap PersonalsFragment collectLatest in repeatOnLifecycle**
    - **Importance:** 65/100
    - **Reasoning:** Lifecycle safety.

23. **PR #11800: Refactor NotificationsFragment to wrap flow collections in repeatOnLifecycle**
    - **Importance:** 65/100
    - **Reasoning:** Lifecycle safety.

24. **PR #11798: Refactor UserProfileFragment to combine flow observers**
    - **Importance:** 65/100
    - **Reasoning:** Optimizes multiple flow observers into a combined flow.

25. **PR #11796: Refactor UserArrayAdapter to use ListAdapter and RecyclerView**
    - **Importance:** 65/100
    - **Reasoning:** Modernizes an older ArrayAdapter to use the newer ListAdapter pattern.

26. **PR #11787: Optimize notification formatting and mark-read state updates**
    - **Importance:** 65/100
    - **Reasoning:** Performance enhancements for Regex compilation and state flows.

27. **PR #11784: Refactor TeamsAdapter: remove unused teamStatusCache local state**
    - **Importance:** 60/100
    - **Reasoning:** State cleanup.

28. **PR #11783: Refactor InlineResourceAdapter to use custom DiffUtils**
    - **Importance:** 60/100
    - **Reasoning:** DiffUtils cleanup.

---

## 2. Collision & Conflict Analysis

We checked for merge/logical collisions sequentially. Any PR that touches the same files as an earlier PR in the sorted list is flagged as having a conflict that must be resolved via rebasing.

* **#11781** (1) -> No conflicts.
* **#11810** (2) -> **⚠️ CONFLICTS with #11781**.
  * Both modify `ProcessUserDataActivity.kt`.
* **#11792** (3) -> **⚠️ CONFLICTS with #11781**.
  * Both modify `TransactionSyncManager.kt` regarding SharedPreferences usage.
* **#11816** (4) -> **⚠️ CONFLICTS with #11781**.
  * Both modify `UploadManager.kt`.
* **#11815** (5) -> **⚠️ CONFLICTS with #11781, #11816**.
  * Modifies `UploadManager.kt`.
* **#11811** (6) -> **⚠️ CONFLICTS with #11781**.
  * Both modify `ServiceModule.kt`.
* **#11797** (7) -> No conflicts.
* **#11809** (8) -> No conflicts.
* **#11791** (9) -> No conflicts.
* **#11794** (10) -> **⚠️ CONFLICTS with #11781, #11809**.
  * Both #11781 and #11794 modify `BaseRecyclerFragment.kt` and `BaseResourceFragment.kt`.
  * Both #11809 and #11794 modify `CoursesRepositoryImpl.kt`.
* **#11785** (11) -> **⚠️ CONFLICTS with #11781, #11794, #11809**.
  * Modifies `BaseRecyclerFragment.kt`, `BaseResourceFragment.kt`, `CoursesRepository.kt`, `CoursesRepositoryImpl.kt`.
* **#11790** (12) -> **⚠️ CONFLICTS with #11781, #11794, #11785**.
  * Modifies `BaseRecyclerFragment.kt`.
* **#11803** (13) -> No conflicts.
* **#11820** (14) -> **⚠️ CONFLICTS with #11790**.
  * Both modify `CoursesAdapter.kt`.
* **#11789** (15) -> No conflicts.
* **#11788** (16) -> No conflicts.
* **#11786** (17) -> No conflicts.
* **#11807** (18) -> No conflicts.
* **#11806** (19) -> No conflicts.
* **#11805** (20) -> **⚠️ CONFLICTS with #11803**.
  * Both modify `TeamsTasksFragment.kt`.
* **#11802** (21) -> No conflicts.
* **#11801** (22) -> No conflicts.
* **#11800** (23) -> No conflicts.
* **#11798** (24) -> No conflicts.
* **#11796** (25) -> **⚠️ CONFLICTS with #11803, #11805**.
  * Modifies `TeamsTasksFragment.kt`.
* **#11787** (26) -> No conflicts.
* **#11784** (27) -> No conflicts.
* **#11783** (28) -> No conflicts.

### Conclusion and Strategy
Merging all these sequentially requires care. Due to the foundational nature of PR **#11781** (SharedPref migration) and PR **#11794 / #11785** (Repository migrations), they touch many shared files (like `BaseRecyclerFragment.kt` and `UploadManager.kt`).

**Recommended merging procedure:**
1. Merge the un-conflicted PRs in isolation immediately (e.g., #11797, #11809, #11791, #11803, #11789, #11788, #11786, #11807, #11806, #11802, #11801, #11800, #11798, #11787, #11784, #11783).
2. Merge #11781 (the massive SharedPreferences refactor).
3. Rebase #11810, #11792, #11816, #11815, and #11811 on top of master (resolving the SharedPreferences and UploadManager conflicts) and merge them.
4. Rebase #11794 and #11785 on top of master (resolving `BaseRecyclerFragment` and `CoursesRepositoryImpl` conflicts) and merge them.
5. Finally, rebase the dependent UI/Adapter PRs (#11790, #11820, #11805, #11796) on top of the newly merged repositories and adapters, and merge them last.