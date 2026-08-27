Date: 2024-05-09 · Base commit: cf107ae3 · Open PRs checked: could not check

### 1. replace community voice dates size calculation with a count query (roadmap 1+7, moves 9 forward)
context: `DashboardViewModel.kt:292` uses `voicesRepository.getCommunityVoiceDates(startTime, endTime, userId)`
to get a list of unique strings by retrieving large batches of news rows, just to compute `.size` for `voiceCount`.
A dedicated count query in `VoicesRepository` and `NewsDao` can compute the exact count directly in the database.
This avoids pulling large result sets into memory, saving significant list allocations and data transfer.
It improves dashboard loading times, directly addressing the performance hotspots on the UI.
files:
app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt
app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt
app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt
app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt
steps:
1. Add `getCommunityVoiceDateCount(startTime: Long, endTime: Long, userId: String?): Int` in `VoicesRepository`.
2. Implement it in `VoicesRepositoryImpl`.
3. Create a query backed by an optimized `COUNT(DISTINCT time/86400000)` equivalent or optimized aggregation query in `NewsDao`.
4. Update `DashboardViewModel` to use the new count method instead of computing `.size`.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
dashboard challenge dialog voice count renders correctly without errors
size budget: ~20 changed lines, 4 files
out of scope: Do not change the condition logic for `evaluateChallengeDialog`.

---

### 2. eliminate repeated json parsing of chat history length in ChatViewModel (roadmap 1+7, moves 10 forward)
context: `ChatViewModel.kt:196` loads `newsConversations` into `parsedConversations`, assigns it to `allConversations`,
and later queries `allConversations.size` repeatedly to evaluate boundaries and total pagination counts.
This happens on lines 214 and 231. Storing the total size directly prevents unnecessary size tracking across states
on full list properties, reducing overhead on list evaluation.
By directly managing pagination with the size metric instead of re-evaluating the list, it simplifies memory.
files:
app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt
steps:
1. In `ChatViewModel`, cache the size of the parsed conversations in a private val `totalConversations`.
2. Ensure the caching happens precisely when the JSON string is successfully parsed and loaded.
3. Replace calls to `allConversations.size` with this cached value in `buildInitialPage`.
4. Replace calls to `allConversations.size` with this cached value in `loadMoreConversations`.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
Chat pagination boundary logic still works when clicking load more
size budget: ~10 changed lines, 1 file
out of scope: Do not rewrite the JSON parser loop or adapter bindings.

---

### 3. use repository to aggregate submissions instead of viewmodel grouping (roadmap 1+3, moves 9 forward)
context: `SubmissionViewModel.kt:84` calculates `submissionCountMap = groupedSubmissions.mapValues { it.value.size }`
entirely in memory after pulling rows from `submissionsRepository`.
Grouping and counting submissions by parentId should be done in `SubmissionsRepository` or via a specific `SubmissionDao`
group-by query to tighten layer boundaries and prevent cross-feature leaks into the UI.
This delegates business data mapping fully to the repository layer.
files:
app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt
app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt
app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt
app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt
steps:
1. Add `getSubmissionCountsByParentId(userId: String): Map<String, Int>` to `SubmissionsRepository`.
2. Implement in `SubmissionsRepositoryImpl`.
3. Create a `SubmissionDao` query returning parent IDs and counts.
4. Swap the `groupedSubmissions.mapValues` call in `SubmissionViewModel` to use this new method.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
Submissions list counts remain correct on the submissions screen
size budget: ~25 changed lines, 4 files
out of scope: Do not rewrite the whole `SubmissionViewModel` combine flow, just swap the count generation.

---

### 4. extract array size evaluation during adapter rebinding in VoicesAdapter (roadmap 7)
context: `VoicesAdapter.kt:876` and `883` loops through `imagesArray.size()` repeatedly in `onBindViewHolder`
using bounds `0 until imagesArray.size()`. Evaluating `.size()` on a `JsonArray` incurs small overhead inside a
tight loop condition and rebinding phases of `RecyclerView`.
Extracting this to a local variable improves render performance and stabilizes frame rates.
It removes multiple redundant property access evaluations inside the view binding loop.
files:
app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt
steps:
1. Identify the multiple `.size()` method calls on `imagesArray` inside `showImageLayout`.
2. Extract `imagesArray.size()` to a local `val imageCount = imagesArray.size()`.
3. Replace condition checks like `if (imagesArray.size() > 0)` with `if (imageCount > 0)`.
4. Use `imageCount` in the `for` loop boundaries instead of `imagesArray.size()`.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
voice card images render properly and smoothly without UI stutter
size budget: ~5 changed lines, 1 file
out of scope: Do not modify Glide loading configurations or adapter item types.

---

### 5. avoid repeated property evaluation in StorageBreakdownFragment recursive scanning (roadmap 7)
context: `StorageBreakdownFragment.kt:230-231` creates `LongArray(categories.size)` and `IntArray(categories.size)`.
This `.size` lookup occurs inside `scanFiles` repeatedly as it recurses, allocating arrays and evaluating size
unnecessarily on every directory node.
Storing the size in the fragment level or passing it once helps optimize memory and file traversals.
It addresses a deep recursive performance hotspot on the settings screen.
files:
app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt
steps:
1. Pre-calculate `val categoryCount = categories.size` outside the `scanFiles` function or at the fragment level.
2. Adjust `scanFiles` to leverage the cached array length if it's placed at the top level.
3. Replace the `categories.size` instantiations in `LongArray` and `IntArray` allocations with the cached value.
4. Verify other recursive loops in the module for similar `.size` redundancy.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
Storage settings continue to load correctly without regression
size budget: ~10 changed lines, 1 file
out of scope: Do not modify standard file scanning logic or directory paths.

---

### 6. consolidate take course fragment progress count (roadmap 3+7, moves 10 forward)
context: `TakeCourseFragment.kt:214` calculates `val stepsSize = steps.size` but multiple other functions in the fragment
still evaluate `steps.size` (lines 259, 308, 342, 353, 465) repeatedly instead of using the cached value or viewmodel bounds.
Using a fragment-scoped property or ensuring the viewmodel completely abstracts the count is preferable.
It prevents inconsistent bounds checking and multiple redundant list evaluations during view lifecycle events.
files:
app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt
steps:
1. Elevate `stepsSize` to a class-level variable populated when `steps` are loaded initially from the viewmodel.
2. Replace all instances of `steps.size` inside conditionals like `position >= steps.size`.
3. Replace all arguments in `setStepText(position, steps.size)` with the cached `stepsSize`.
4. Ensure the class variable is cleared in `onDestroyView`.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
ViewPager continues to navigate step boundaries smoothly
size budget: ~15 changed lines, 1 file
out of scope: Do not change layout visibility logic or adapter state.

---

### 7. optimize offline login count access in UserRepository (roadmap 1+3, moves 9 forward)
context: `UserRepositoryImpl.kt:92` calls `activitiesRepositoryLazy.get().getOfflineLoginCount(userName)`
to build the `DashboardProfile`. `UserRepository` should not depend on `ActivitiesRepository` just for a single count.
This creates cross-feature data leaks where Repositories call other Repositories improperly.
The assembly should happen at the UseCase or ViewModel level to respect layer boundaries.
files:
app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt
app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt
steps:
1. Remove `activitiesRepositoryLazy` from `UserRepositoryImpl`.
2. Update `getDashboardProfile` to only return the user name and core details.
3. Move `getOfflineLoginCount` functionality and ensure consumers use the proper `ActivitiesRepository` method directly.
4. Update UI callers to fetch from both appropriately if needed.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
dashboard still displays correct login count
size budget: ~20 changed lines, 2 files
out of scope: Do not modify `ActivitiesRepositoryImpl` or `offlineActivityDao`.

---

### 8. prevent redundant unread count aggregation in NotificationsViewModel (roadmap 3, moves 10 forward)
context: `NotificationsViewModel.kt` maps unread counts and queries `it.size` repetitively while also calling `getUnreadCount`.
We can centralize this state using direct scalar states in `NotificationsViewModel` instead of lists where only a count is needed.
This reduces IO hits and streamlines the viewmodel relationship into a cleaner state representation.
files:
app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt
app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt
steps:
1. Find list sizes mapped into counts in `NotificationsViewModel`.
2. Refactor state values to avoid list aggregations that just reduce to sizes.
3. If a flow is not available, centralize the count retrieval into a single state function.
4. Trigger the count updates efficiently in response to lifecycle events.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
notification badges reflect the correct unread count
size budget: ~15 changed lines, 2 files
out of scope: Do not change the core badge logic.

---

### 9. replace stepExams list payload in CourseStepFragment with repository count (roadmap 1+7, moves 9 forward)
context: `CourseStepFragment.kt:245` calls `getString(R.string.retake_test, stepExams.size)`.
The full `stepExams` list is loaded via `CourseStepData` just to evaluate its size for this string binding.
A targeted count query or passing the size via `CourseStepData` reduces database parsing allocation,
preventing whole list objects from being generated when only scalar counts are needed.
files:
app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt
app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt
app/src/main/java/org/ole/planet/myplanet/model/CourseStepData.kt
steps:
1. In `CoursesRepositoryImpl.getCourseStepData`, change the loading to query exam counts explicitly.
2. Add properties to populate `stepExamCount` and `stepSurveyCount` in `CourseStepData`.
3. Remove full list populations if unused elsewhere.
4. Update `CourseStepFragment` to use these counts instead of calling `.size` on lists.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
course step renders test availability counts correctly
size budget: ~20 changed lines, 3 files
out of scope: Do not change how exams are taken or launched.

---

### 10. remove crude view model data processing overhead in TeamsVoicesViewModel (roadmap 3, moves 10 forward)
context: `TeamsVoicesViewModel.kt:60` calls `notificationsRepository.updateTeamNotification(teamId, newsList.size)`.
Size checks are inefficient when the list is queried right above it, specifically when the notification
should probably update via an optimized count metric or event stream.
We should let Kotlin data classes or more efficient query comparisons handle this without relying on full list sizes.
files:
app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt
app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt
steps:
1. Navigate to `getFilteredNews` in `TeamsVoicesViewModel`.
2. Refactor the notification update block to rely on explicit count queries if the list isn't needed fully, or separate concerns.
3. Consider extracting the notification update to a separate flow logic instead of piggy-backing on `getFilteredNews`.
4. Ensure the count update operates seamlessly.
acceptance:
./gradlew testDefaultDebugUnitTest stays green
team voice updates seamlessly on db change
size budget: ~10 changed lines, 2 files
out of scope: Do not alter other team flows or list structures.
