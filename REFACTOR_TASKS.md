# Repository Boundary Refactor Tasks

## Round 1: Data Navigation & Layer Boundaries

---

### 1. Move getCurrentProgress from RealmCourseProgress to ProgressRepository

The companion object method `getCurrentProgress` performs direct Realm queries but a `ProgressRepository` already exists. This creates a data layer leak where UI components bypass the repository interface.

:codex-file-citation[codex-file-citation]{line_range_start=58 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt#L58-L73"}
:codex-file-citation[codex-file-citation]{line_range_start=96 line_range_end=112 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L96-L112"}

:::task-stub{title="Move getCurrentProgress to ProgressRepository"}
1. Add `suspend fun getCurrentProgress(steps: List<RealmCourseStep?>?, userId: String?, courseId: String?): Int` to ProgressRepository interface
2. Migrate logic from RealmCourseProgress.getCurrentProgress to ProgressRepositoryImpl using existing queryList pattern
3. Mark companion object method @Deprecated with replacement message
:::

---

### 2. Replace deprecated getCourseProgress call in BellDashboardViewModel

The ViewModel directly calls the deprecated `RealmCourseProgress.getCourseProgress` companion object method instead of using the injected `ProgressRepository`.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt#L41-L57"}
:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt#L43-L56"}

:::task-stub{title="Use ProgressRepository in BellDashboardViewModel"}
1. Inject ProgressRepository into BellDashboardViewModel constructor
2. Replace `RealmCourseProgress.getCourseProgress(realm, userId)` with `progressRepository.getCourseProgress(userId)`
3. Replace `RealmMyCourse.getMyCourseByUserId` call with CoursesRepository method
:::

---

### 3. Move createActivity from RealmCourseActivity to CoursesRepository

The `createActivity` companion object method performs Realm transactions directly. This data operation belongs in the repository layer to enforce separation.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=43 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCourseActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmCourseActivity.kt#L24-L43"}
:codex-file-citation[codex-file-citation]{line_range_start=220 line_range_end=232 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L220-L232"}

:::task-stub{title="Move createActivity to CoursesRepository"}
1. Add `suspend fun logCourseVisit(userId: String?, courseId: String?, courseTitle: String?, planetCode: String?, parentCode: String?)` to CoursesRepository interface
2. Implement in CoursesRepositoryImpl using executeTransaction pattern
3. Update TakeCourseFragment to call coursesRepository.logCourseVisit instead of createActivity companion method
:::

---

### 4. Move isStepCompleted from RealmSubmission to SubmissionsRepository

The UI directly calls `RealmSubmission.isStepCompleted` which performs Realm queries. SubmissionsRepository already has similar patterns and should encapsulate this check.

:codex-file-citation[codex-file-citation]{line_range_start=209 line_range_end=216 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L209-L216"}
:codex-file-citation[codex-file-citation]{line_range_start=265 line_range_end=278 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L265-L278"}

:::task-stub{title="Move isStepCompleted to SubmissionsRepository"}
1. Add `suspend fun isStepCompleted(stepId: String?, userId: String?): Boolean` to SubmissionsRepository interface
2. Implement in SubmissionsRepositoryImpl using existing query patterns
3. Mark RealmSubmission.isStepCompleted as @Deprecated with replacement message
:::

---

### 5. Replace direct Realm query in TakeCourseFragment.getCourseProgress

The private method `getCourseProgress()` calls the deprecated `RealmCourseProgress.getCourseProgress` directly. This should use the injected ProgressRepository.

:codex-file-citation[codex-file-citation]{line_range_start=372 line_range_end=380 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L372-L380"}
:codex-file-citation[codex-file-citation]{line_range_start=169 line_range_end=179 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L169-L179"}

:::task-stub{title="Use ProgressRepository in TakeCourseFragment"}
1. Inject ProgressRepository into TakeCourseFragment
2. Replace getCourseProgress() private method to call progressRepository.getCourseProgress
3. Replace getCurrentProgress companion call in setListeners and updateStepDisplay with repository method
:::

---

### 6. Remove redundant withContext(Dispatchers.Main) in TakeCourseFragment

The `setCourseData()` method uses `lifecycleScope.launch` then immediately calls `withContext(Dispatchers.Main)` which is redundant since lifecycleScope already dispatches to Main.

:codex-file-citation[codex-file-citation]{line_range_start=197 line_range_end=218 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L197-L218"}
:codex-file-citation[codex-file-citation]{line_range_start=234 line_range_end=248 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L234-L248"}

:::task-stub{title="Remove redundant Main dispatcher switches in TakeCourseFragment"}
1. Remove `withContext(Dispatchers.Main)` wrapper at lines 203-218 since lifecycleScope already uses Main
2. Remove `withContext(Dispatchers.Main)` wrapper at lines 234-247
3. Keep only the IO dispatcher switch for background Realm work
:::

---

### 7. Move existsSubmission from TakeCourseFragment companion to SubmissionsRepository

The companion object method `existsSubmission` in TakeCourseFragment performs direct Realm queries for submission checks. This is a data layer leak.

:codex-file-citation[codex-file-citation]{line_range_start=440 line_range_end=459 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L440-L459"}
:codex-file-citation[codex-file-citation]{line_range_start=382 line_range_end=401 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L382-L401"}

:::task-stub{title="Move existsSubmission to SubmissionsRepository"}
1. SubmissionsRepository already has hasSubmission - verify it covers existsSubmission use case
2. Replace existsSubmission call in checkSurveyCompletion with submissionsRepository.hasSubmission
3. Inject SubmissionsRepository if not already present and remove companion object method
:::

---

### 8. Replace direct Realm query in ExamTakingFragment submission lookup

ExamTakingFragment directly queries RealmSubmission using mRealm instead of using the injected SubmissionsRepository for finding existing submissions.

:codex-file-citation[codex-file-citation]{line_range_start=85 line_range_end=99 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L85-L99"}
:codex-file-citation[codex-file-citation]{line_range_start=344 line_range_end=350 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L344-L350"}

:::task-stub{title="Use SubmissionsRepository in ExamTakingFragment"}
1. Replace direct mRealm.where(RealmSubmission::class.java) query with submissionsRepository.getSubmissionsByParentId
2. Adjust getSubmissionsByParentId to support type filter if needed or add new repository method
3. Remove direct Realm usage for submission lookup in onViewCreated
:::

---

### 9. Add getCurrentProgress method to ProgressRepository interface

The ProgressRepository interface is missing `getCurrentProgress` which forces callers to use the deprecated companion object method. Adding this enables proper repository usage.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=17 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt#L1-L17"}
:codex-file-citation[codex-file-citation]{line_range_start=230 line_range_end=249 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L230-L249"}

:::task-stub{title="Extend ProgressRepository interface with getCurrentProgress"}
1. Add `suspend fun getCurrentProgress(courseId: String?, userId: String?): Int` to ProgressRepository interface
2. Implement using existing suspendGetCurrentProgress private method pattern in ProgressRepositoryImpl
3. Update DI module bindings if needed
:::

---

### 10. Replace getMyCourseByUserId companion call in BaseRecyclerFragment

BaseRecyclerFragment imports and uses `RealmMyCourse.getMyCourseByUserId` companion method directly instead of using CoursesRepository.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=33 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L24-L33"}
:codex-file-citation[codex-file-citation]{line_range_start=194 line_range_end=203 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L194-L203"}

:::task-stub{title="Replace getMyCourseByUserId companion in BaseRecyclerFragment"}
1. Inject CoursesRepository into BaseRecyclerFragment (or ensure child fragments inject it)
2. Add `suspend fun getMyCourses(userId: String): List<RealmMyCourse>` to CoursesRepository if not present
3. Replace companion object imports with repository calls in subclasses that use this pattern
:::

---

## Summary

| # | Task | Risk | Files Changed |
|---|------|------|---------------|
| 1 | Move getCurrentProgress to ProgressRepository | Low | 2 |
| 2 | Use ProgressRepository in BellDashboardViewModel | Low | 1 |
| 3 | Move createActivity to CoursesRepository | Low | 3 |
| 4 | Move isStepCompleted to SubmissionsRepository | Low | 2 |
| 5 | Use ProgressRepository in TakeCourseFragment | Low | 1 |
| 6 | Remove redundant Main dispatcher switches | Low | 1 |
| 7 | Move existsSubmission to SubmissionsRepository | Low | 1 |
| 8 | Use SubmissionsRepository in ExamTakingFragment | Low | 1 |
| 9 | Extend ProgressRepository interface | Low | 2 |
| 10 | Replace getMyCourseByUserId in BaseRecyclerFragment | Medium | 2-3 |

All tasks target single-feature boundaries to minimize merge conflicts during parallel PR review.
