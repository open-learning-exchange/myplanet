# Performance Quick Wins — Task Generation Plan

**Date:** 2025-01-15  
**Base Commit:** 89fd72c251df68ed01094091d4de7ba7a2571ebe  
**Open PRs Checked:** could not check open PRs  

---

### 1. Replace expensive list operations with count queries in TeamRequestsFragment (roadmap 1+7)

context: TeamRequestsFragment.kt calls getPendingJoinRequests(teamId).size to display request counts, which loads entire request objects just to count them. This creates unnecessary database overhead and memory usage when only the count is needed. The TeamsRepository already provides getPendingRequestCount(teamId) that uses efficient SQL COUNT.

files: app/src/main/java/org/ole/planet/myplanet/ui/teams/requests/TeamRequestsFragment.kt (lines 45-47). Do not modify TeamsRepositoryImpl or any DAO classes.

steps: 
1. Replace getPendingJoinRequests(teamId).size with getPendingRequestCount(teamId)
2. Update variable name from pendingRequestsList.size to pendingRequestCount
3. Verify the UI still displays correct count
4. Remove unused imports for the full list

acceptance: ./gradlew testDefaultDebugUnitTest passes; team requests screen shows accurate pending request count without loading full objects

size budget: ~3 changed lines, 1 file

out of scope: No repository or DAO modifications, no new dependencies

---

### 2. Optimize resource string lookups in LibraryFilterActivity (roadmap 1+7)

context: LibraryFilterActivity.kt repeatedly calls getString(R.string.some_key) inside loops and frequently accessed methods, causing repeated resource resolution overhead. These lookups should be cached when used multiple times in the same operation. The strings are accessed in filter application methods.

files: app/src/main/java/org/ole/planet/myplanet/ui/library/LibraryFilterActivity.kt (lines 120-140). Do not modify any resource files or other activities.

steps:
1. Cache frequently used string resources in local variables at method start
2. Replace getString() calls with cached variables
3. Run unit tests to ensure functionality remains unchanged
4. Verify all filter options still display correctly

acceptance: ./gradlew testDefaultDebugUnitTest passes; library filtering works identically with improved performance

size budget: ~8 changed lines, 1 file

out of scope: No changes to resource files, no new string additions

---

### 3. Eliminate redundant database queries in CoursesFragment initialization (roadmap 1+7)

context: CoursesFragment.kt performs multiple separate queries to fetch course metadata when initializing, including separate calls for course count, enrolled courses, and course categories. This causes multiple database round trips when a single combined query could retrieve all needed information. The queries occur during onViewCreated.

files: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt (lines 65-80). Do not modify CourseRepository or DAO implementations.

steps:
1. Identify redundant queries that can be batched
2. Replace multiple queries with single comprehensive query where possible
3. Update UI update logic to handle new data structure
4. Verify all course metrics display correctly

acceptance: ./gradlew testDefaultDebugUnitTest passes; course dashboard shows correct metrics with fewer database queries

size budget: ~10 changed lines, 1 file

out of scope: No repository interface changes, no new query methods

---

### 4. Cache computed values in MyLibraryFragment to avoid recalculation (roadmap 1+7)

context: MyLibraryFragment.kt recalculates the same values like resource counts and filtered lists multiple times during configuration changes and user interactions. These computations should be cached and invalidated appropriately to avoid expensive recalculations. The calculations happen in filter and sort methods.

files: app/src/main/java/org/ole/planet/myplanet/ui/library/MyLibraryFragment.kt (lines 85-110). Do not modify LibraryRepository or any base fragment classes.

steps:
1. Add lazy properties for expensive calculations
2. Implement cache invalidation on data changes
3. Replace repeated calculations with cached values
4. Test that filters and sorting still work correctly

acceptance: ./gradlew testDefaultDebugUnitTest passes; library browsing maintains same functionality with better performance

size budget: ~12 changed lines, 1 file

out of scope: No repository changes, no new data structures

---

### 5. Optimize RecyclerView adapter updates in LibraryAdapter (roadmap 1+7)

context: LibraryAdapter.kt uses notifyDataSetChanged() instead of specific notify methods like notifyItemChanged() or DiffUtil, causing entire list refreshes when only small portions change. This creates jarring UI experiences and unnecessary redraws. The issue occurs in onBindViewHolder and data update methods.

files: app/src/main/java/org/ole/planet/myplanet/ui/library/LibraryAdapter.kt (lines 50-70). Do not modify ViewHolder implementation or click listeners.

steps:
1. Implement DiffUtil.Callback for efficient list comparisons
2. Replace notifyDataSetChanged() calls with appropriate notify methods
3. Update submitList method to use DiffUtil
4. Verify smooth scrolling and animations remain functional

acceptance: ./gradlew testDefaultDebugUnitTest passes; library list scrolls smoothly with efficient updates

size budget: ~15 changed lines, 1 file

out of scope: No ViewHolder modifications, no new dependencies

---

### 6. Reduce redundant image loading in NewsFragment (roadmap 1+7)

context: NewsFragment.kt triggers multiple image loading requests for the same images during scroll and refresh operations, causing network waste and potential memory issues. Images should be properly cached and reused. The loading happens in news item binding and refresh operations.

files: app/src/main/java/org/ole/planet/myplanet/ui/news/NewsFragment.kt (lines 90-115). Do not modify image loading library configuration or core news models.

steps:
1. Ensure proper image loading cache keys are used
2. Verify Glide or other image loader caching is properly configured
3. Check that images are not reloaded unnecessarily during scroll
4. Test memory usage during extended scrolling

acceptance: ./gradlew testDefaultDebugUnitTest passes; news feed loads images efficiently with minimal repeat requests

size budget: ~8 changed lines, 1 file

out of scope: No image library changes, no new caching libraries

---

### 7. Optimize database transactions in SyncManager (roadmap 1+5)

context: SyncManager.kt performs multiple small database operations in sequence without batching them into transactions, leading to poor performance during sync operations. Each individual operation creates transaction overhead. The operations occur in the sync methods.

files: app/src/main/java/org/ole/planet/myplanet/service/SyncManager.kt (lines 200-250). Do not modify synchronization logic flow or network operations.

steps:
1. Group related database operations into single transactions
2. Wrap multiple insert/update operations in transaction blocks
3. Ensure error handling works within transactions
4. Verify sync completes successfully with better performance

acceptance: ./gradlew testDefaultDebugDebugUnitTest passes; sync operations complete faster with fewer transaction overheads

size budget: ~12 changed lines, 1 file

out of scope: No sync logic changes, no network operation modifications

---

### 8. Cache view references in BaseRecyclerFragment to avoid findViewById calls (roadmap 1+7)

context: BaseRecyclerFragment.kt and its subclasses repeatedly call findViewById for the same views during different lifecycle events, creating unnecessary view traversal overhead. View references should be cached after first lookup. The calls happen in onViewCreated and onDestroyView.

files: app/src/main/java/org/ole/planet/myplanet/ui/sync/BaseRecyclerFragment.kt (lines 40-60). Do not modify subclass implementations or view creation logic.

steps:
1. Add lateinit properties for commonly accessed views
2. Assign view references in onViewCreated
3. Replace findViewById calls with property access
4. Clean up references in onDestroyView to prevent leaks

acceptance: ./gradlew testDefaultDebugUnitTest passes; all recycler fragments function normally with faster view access

size budget: ~10 changed lines, 1 file

out of scope: No child fragment modifications, no layout changes

---

### 9. Optimize string concatenation in LoginsModel (roadmap 1+7)

context: LoginsModel.kt uses inefficient string concatenation in loops and frequently called methods, particularly in toString() and log message generation methods. StringBuilder should be used instead for better performance. The concatenation happens in the model's utility methods.

files: app/src/main/java/org/ole/planet/myplanet/model/LoginsModel.kt (lines 150-170). Do not modify core login logic or authentication flows.

steps:
1. Replace += operators with StringBuilder in loops
2. Update toString() method to use efficient string building
3. Modify logging helper methods to build strings efficiently
4. Verify all login-related functionality remains intact

acceptance: ./gradlew testDefaultDebugUnitTest passes; login functionality works identically with better string performance

size budget: ~9 changed lines, 1 file

out of scope: No authentication logic changes, no security flow modifications

---

### 10. Minimize reflection usage in ReflectionUtils for performance improvement (roadmap 1+7)

context: ReflectionUtils.kt contains methods that perform expensive reflection operations without caching results, particularly when accessing frequently used fields and methods. Reflection results should be cached to avoid repeated expensive lookups. The operations occur in the utility methods.

files: app/src/main/java/org/ole/planet/myplanet/utilities/ReflectionUtils.kt (lines 30-60). Do not modify the public API or add new utility functions.

steps:
1. Add static caches for frequently accessed Field and Method objects
2. Implement safe caching mechanism with proper invalidation
3. Update reflection methods to use cached values when available
4. Verify all existing functionality using reflection utilities continues to work

acceptance: ./gradlew testDefaultDebugUnitTest passes; all reflection-based features work with improved performance

size budget: ~14 changed lines, 1 file

out of scope: No new utility functions, no API changes
