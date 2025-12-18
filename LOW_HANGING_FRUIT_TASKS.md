# Low-Hanging Fruit Performance Tasks

10 granular, easily reviewable tasks focusing on performance quick wins and micro-optimizations.

---

### Migrate AdapterTeamCourse to ListAdapter with DiffUtil

AdapterTeamCourse extends RecyclerView.Adapter directly without using DiffUtil, causing inefficient list updates when team courses change. The adapter has a simple data model (RealmMyCourse with clear _id field) making migration straightforward using existing DiffUtils.itemCallback utility.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=24 path=app/src/main/java/org/ole/planet/myplanet/ui/team/teamCourse/AdapterTeamCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/team/teamCourse/AdapterTeamCourse.kt#L18-L24"}

:::task-stub{title="Migrate AdapterTeamCourse to ListAdapter"}
1. Change class signature from RecyclerView.Adapter to ListAdapter using DiffUtils.itemCallback
2. Replace list parameter with submitList() calls
3. Update getItemCount() to use currentList
4. Remove manual list management in adapter
5. Update TeamCourseFragment to call submitList() instead of passing list to constructor
:::

---

### Fix lifecycle scope in BellDashboardFragment polling loop

BellDashboardFragment uses lifecycleScope instead of viewLifecycleOwner.lifecycleScope for a continuous 60-second polling job, causing the coroutine to survive view destruction and potentially leak memory. The polling job checks reminders and queries SharedPreferences every minute.

:codex-file-citation[codex-file-citation]{line_range_start=234 line_range_end=242 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L234-L242"}

:::task-stub{title="Fix lifecycle scope in BellDashboardFragment"}
1. Replace lifecycleScope with viewLifecycleOwner.lifecycleScope in startReminderCheck()
2. Ensure surveyReminderJob is cancelled in onDestroyView()
3. Verify polling stops when fragment view is destroyed
:::

---

### Add debouncing to CoursesFragment search TextWatcher

CoursesFragment triggers filterCoursesAndUpdateUi() on every keystroke without debouncing, executing 3 separate Realm queries (courses, ratings, progress) per character typed. This causes UI jank when searching through large course lists.

:codex-file-citation[codex-file-citation]{line_range_start=273 line_range_end=281 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L273-L281"}

:codex-file-citation[codex-file-citation]{line_range_start=495 line_range_end=512 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L495-L512"}

:::task-stub{title="Add search debouncing to CoursesFragment"}
1. Create searchJob as Job? property
2. Cancel existing searchJob before launching new one
3. Add 300ms delay before executing filterCoursesAndUpdateUi()
4. Verify search only triggers after user stops typing
:::

---

### Fix listener recreation and lifecycle scope in TeamCalendarFragment

TeamCalendarFragment recreates the calendar click listener every onResume() instead of once in onViewCreated(), potentially creating multiple listeners. The click handler also uses lifecycleScope instead of viewLifecycleOwner.lifecycleScope and queries all team meetups on every click.

:codex-file-citation[codex-file-citation]{line_range_start=216 line_range_end=219 path=app/src/main/java/org/ole/planet/myplanet/ui/team/TeamCalendarFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/team/TeamCalendarFragment.kt#L216-L219"}

:codex-file-citation[codex-file-citation]{line_range_start=226 line_range_end=242 path=app/src/main/java/org/ole/planet/myplanet/ui/team/TeamCalendarFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/team/TeamCalendarFragment.kt#L226-L242"}

:::task-stub{title="Fix TeamCalendarFragment listener lifecycle"}
1. Remove setupCalendarClickListener() call from onResume()
2. Keep only the call in onViewCreated()
3. Replace lifecycleScope with viewLifecycleOwner.lifecycleScope in click handler
4. Verify listener is set once and uses correct lifecycle scope
:::

---

### Migrate AdapterResource refreshWithDiff to use LibraryRepository

AdapterResource uses Realm.getDefaultInstance() directly in refreshWithDiff() method instead of using the injected LibraryRepository, violating the data layer pattern. LibraryRepository already exists with getAllLibraryItems() method.

:codex-file-citation[codex-file-citation]{line_range_start=406 line_range_end=415 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/AdapterResource.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/resources/AdapterResource.kt#L406-L415"}

:::task-stub{title="Migrate AdapterResource to use LibraryRepository"}
1. Inject LibraryRepository in adapter constructor
2. Replace Realm.getDefaultInstance() call with libraryRepository.getAllLibraryItems()
3. Remove Realm import if no longer needed
4. Verify refresh functionality works correctly
:::

---

### Move user search query from MyHealthFragment to UserRepository

MyHealthFragment performs direct Realm query for user search with text filtering instead of using the already-injected UserRepository. The search query is a case-insensitive match across firstName, lastName, and name fields.

:codex-file-citation[codex-file-citation]{line_range_start=353 line_range_end=362 path=app/src/main/java/org/ole/planet/myplanet/ui/myhealth/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/myhealth/MyHealthFragment.kt#L353-L362"}

:::task-stub{title="Move user search to UserRepository"}
1. Add searchUsers(query: String) method to UserRepository interface
2. Implement method in UserRepositoryImpl with same query logic
3. Replace direct Realm query in MyHealthFragment with userRepository.searchUsers()
4. Remove mRealm usage from fragment if this is the only remaining direct query
:::

---

### Avoid full resource refresh on every onResume in ResourcesFragment

ResourcesFragment calls refreshResourcesData() on every onResume(), querying entire library and recalculating all ratings even when returning from other fragments without data changes. This causes unnecessary database operations and UI updates.

:codex-file-citation[codex-file-citation]{line_range_start=546 line_range_end=550 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L546-L550"}

:::task-stub{title="Optimize ResourcesFragment onResume refresh"}
1. Add isFirstResume boolean flag initialized to true
2. Only call refreshResourcesData() on first resume
3. Set isFirstResume to false after first call
4. Reset flag in onDestroyView() for fragment reuse
5. Verify resources update correctly on initial load
:::

---

### Migrate RvFeedbackAdapter to ListAdapter with DiffUtil

RvFeedbackAdapter in FeedbackDetailActivity extends RecyclerView.Adapter directly without DiffUtil. Converting to ListAdapter would prevent unnecessary redraws when reply list updates and follow established codebase pattern.

:codex-file-citation[codex-file-citation]{line_range_start=139 line_range_end=154 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackDetailActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackDetailActivity.kt#L139-L154"}

:::task-stub{title="Migrate RvFeedbackAdapter to ListAdapter"}
1. Change RvFeedbackAdapter from RecyclerView.Adapter to ListAdapter
2. Create DiffUtil.ItemCallback comparing FeedbackReply by date and user
3. Replace replyList parameter with submitList() calls
4. Update getItemCount() to use currentList.size
5. Update FeedbackDetailActivity to call submitList() on adapter
:::

---

### Migrate ChallengeHelper Realm queries to repositories

ChallengeHelper uses Realm.getDefaultInstance() directly in two methods for reading news, courses, and survey data. These should use existing NewsRepository and CourseRepository that are already injected via ProgressRepository pattern.

:codex-file-citation[codex-file-citation]{line_range_start=46 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-8cbfl/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt#L46-L56"}

:::task-stub{title="Migrate ChallengeHelper to use repositories"}
1. Inject CourseRepository in ChallengeHelper constructor
2. Add getCourseById(courseId: String) method to CourseRepository if not exists
3. Replace Realm.getDefaultInstance() usage with courseRepository method calls
4. Remove Realm imports if no longer needed
5. Verify challenge prompting still works correctly
:::

---

### Move username validation from BecomeMemberActivity to UserRepository

BecomeMemberActivity directly queries Realm to check username availability in usernameValidationError() and calls static RealmUserModel.cleanupDuplicateUsers(). These operations should be in UserRepository following the data layer pattern.

:::task-stub{title="Move username validation to UserRepository"}
1. Add isUsernameAvailable(username: String) method to UserRepository interface
2. Add cleanupDuplicateUsers() method to UserRepository interface
3. Implement both methods in UserRepositoryImpl with existing logic
4. Replace direct mRealm usage in BecomeMemberActivity with repository calls
5. Remove mRealm initialization and cleanup from activity
:::

---

## Summary Statistics

- **DiffUtil/ListAdapter migrations**: 3 tasks (AdapterTeamCourse, RvFeedbackAdapter)
- **Lifecycle scope fixes**: 2 tasks (BellDashboardFragment, TeamCalendarFragment)
- **Repository pattern migrations**: 4 tasks (AdapterResource, MyHealthFragment, ChallengeHelper, BecomeMemberActivity)
- **Performance optimizations**: 2 tasks (CoursesFragment debouncing, ResourcesFragment refresh)

All tasks are:
- ✅ Granular (single file or 2-3 related files)
- ✅ Low risk (no architectural changes)
- ✅ Easily reviewable (< 50 lines of changes)
- ✅ Performance-focused (immediate measurable improvement)
- ✅ Pattern-following (use existing utilities and patterns)
