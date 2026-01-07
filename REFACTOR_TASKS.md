# Repository Boundary Reinforcement Tasks

### 1. TeamCoursesAdapter lacks DiffUtil entirely

This adapter uses a mutable list with no DiffUtil implementation, causing full list refreshes and poor performance. Convert to ListAdapter with DiffUtils.itemCallback for efficient updates.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=23 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt#L19-L23"}
:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesAdapter.kt#L43-L57"}

:::task-stub{title="Convert TeamCoursesAdapter to ListAdapter with DiffUtils.itemCallback"}
1. Change class signature from RecyclerView.Adapter to ListAdapter with DIFF_CALLBACK companion object
2. Replace mutable list constructor parameter with ListAdapter's internal list management
3. Add DiffUtils.itemCallback comparing RealmMyCourse by id and contents
4. Remove getItemCount override since ListAdapter handles it
:::

### 2. TeamCoursesFragment directly queries Realm bypassing repository

Fragment contains direct mRealm.where() calls to fetch courses, violating repository boundaries. The existing CoursesRepository should provide this data instead.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=35 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt#L29-L35"}

:::task-stub{title="Use CoursesRepository in TeamCoursesFragment instead of direct Realm access"}
1. Inject CoursesRepository into TeamCoursesFragment
2. Add getCoursesByIds(ids: List<String>) method to CoursesRepository interface
3. Implement getCoursesByIds in CoursesRepositoryImpl using queryList with in() clause
4. Replace mRealm.where() call in setupCoursesList() with repository method
:::

### 3. BaseDashboardFragmentPlugin directly queries RealmMyTeam

Dashboard plugin contains direct Realm query to fetch team details for navigation. This should use the existing TeamsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=53 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BaseDashboardFragmentPlugin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BaseDashboardFragmentPlugin.kt#L53-L60"}

:::task-stub{title="Use TeamsRepository in BaseDashboardFragmentPlugin for team lookup"}
1. Inject TeamsRepository into BaseDashboardFragmentPlugin
2. Replace direct mRealm.where(RealmMyTeam) with teamsRepository.getTeamById(id)
3. Launch coroutine with lifecycleScope for the repository call
4. Remove mRealm field usage from this click handler
:::

### 4. DiscussionListFragment directly accesses Realm for team data

Fragment queries RealmMyTeam directly in onCreateView instead of using TeamsRepository, creating tight coupling between UI and data layer.

:codex-file-citation[codex-file-citation]{line_range_start=107 line_range_end=120 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/discussion/DiscussionListFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/discussion/DiscussionListFragment.kt#L107-L120"}

:::task-stub{title="Use TeamsRepository in DiscussionListFragment for team lookup"}
1. Inject TeamsRepository into DiscussionListFragment
2. Replace mRealm.where(RealmMyTeam) query with teamsRepository.getTeamById(teamId)
3. Use lifecycleScope.launch to call repository suspend function
4. Update team property assignment to use repository result
:::

### 5. CoursesRepositoryImpl leaks into activity tracking domain

Repository directly manipulates RealmRemovedLog and RealmSearchActivity which belong to a separate activity tracking domain, violating single responsibility.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L12-L15"}
:codex-file-citation[codex-file-citation]{line_range_start=97 line_range_end=102 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L97-L102"}

:::task-stub{title="Extract RealmRemovedLog operations from CoursesRepositoryImpl to ActivityRepository"}
1. Add markCourseAdded(userId, courseId) method to ActivityRepository interface
2. Add markCourseRemoved(userId, courseId) method to ActivityRepository interface
3. Implement both methods in ActivityRepositoryImpl using RealmRemovedLog
4. Inject ActivityRepository into CoursesRepositoryImpl
5. Replace direct RealmRemovedLog.onAdd/onRemove calls with ActivityRepository methods
:::

### 6. ResourcesRepositoryImpl leaks into activity tracking domain

Repository directly calls RealmRemovedLog companion methods for tracking resource additions/removals instead of delegating to a dedicated tracking repository.

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=13 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L10-L13"}
:codex-file-citation[codex-file-citation]{line_range_start=91 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L91-L95"}

:::task-stub{title="Extract RealmRemovedLog operations from ResourcesRepositoryImpl to ActivityRepository"}
1. Add markResourceAdded(userId, resourceId) method to ActivityRepository interface
2. Add markResourceRemoved(userId, resourceId) method to ActivityRepository interface
3. Implement both methods in ActivityRepositoryImpl using RealmRemovedLog
4. Inject ActivityRepository into ResourcesRepositoryImpl
5. Replace direct RealmRemovedLog.onAdd/onRemove calls with ActivityRepository methods
:::

### 7. UserInformationFragment uses GlobalScope causing uncontrolled coroutine lifecycle

Fragment launches coroutine with GlobalScope which survives fragment destruction, risking memory leaks and context use-after-free. Should use viewLifecycleOwner.lifecycleScope instead.

:codex-file-citation[codex-file-citation]{line_range_start=323 line_range_end=339 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L323-L339"}

:::task-stub{title="Replace GlobalScope with viewLifecycleOwner.lifecycleScope in UserInformationFragment"}
1. Replace GlobalScope.launch with viewLifecycleOwner.lifecycleScope.launch
2. Keep Dispatchers.IO for the coroutine context
3. Remove comment about surviving fragment lifecycle
4. Verify upload completion handling works with lifecycle-aware scope
:::

### 8. TakeCourseFragment contains direct Realm queries in UI layer

Fragment performs multiple direct mRealm.where() queries for course data instead of using CoursesRepository, tightly coupling UI to data layer.

:codex-file-citation[codex-file-citation]{line_range_start=355 line_range_end=360 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L355-L360"}
:codex-file-citation[codex-file-citation]{line_range_start=390 line_range_end=396 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L390-L396"}

:::task-stub{title="Move course refresh query from TakeCourseFragment to CoursesRepository"}
1. Add getCourseById(courseId: String) suspend method to CoursesRepository interface
2. Implement getCourseById in CoursesRepositoryImpl using findByField
3. Replace mRealm.where(RealmMyCourse) in refreshCourseData() with repository call
4. Use Dispatchers.IO context for repository call instead of Dispatchers.Main
:::

### 9. CourseStepFragment contains database transaction logic in UI

Fragment directly manages Realm transactions for saving course progress instead of delegating to ProgressRepository, violating layer separation.

:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=101 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt#L80-L101"}
:codex-file-citation[codex-file-citation]{line_range_start=114 line_range_end=144 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt#L114-L144"}

:::task-stub{title="Move saveCourseProgress from CourseStepFragment to ProgressRepository"}
1. Add saveCourseProgress(userId, courseId, stepId, passed) method to ProgressRepository interface
2. Implement saveCourseProgress in ProgressRepositoryImpl with Realm transaction
3. Inject ProgressRepository into CourseStepFragment
4. Replace databaseService.executeTransactionAsync in saveCourseProgress() with repository call
:::

### 10. ExamTakingFragment holds RealmResults directly violating lifecycle safety

Fragment stores RealmResults in instance variables which can become invalid after Realm closes, risking crashes. Should use repository with copied/frozen results.

:codex-file-citation[codex-file-citation]{line_range_start=81 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L81-L95"}

:::task-stub{title="Move exam question queries from ExamTakingFragment to SurveysRepository"}
1. Add getExamQuestions(examId: String) suspend method to SurveysRepository interface
2. Implement getExamQuestions in SurveysRepositoryImpl returning copied List<RealmExamQuestion>
3. Inject SurveysRepository into ExamTakingFragment
4. Replace mRealm.where(RealmExamQuestion) with repository call in onViewCreated
5. Change questions property type from RealmResults to List
:::
