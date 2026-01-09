# Data Layer Refactoring Tasks Report

## Repository Boundaries & Data Navigation Cleanup

---

### 1. Move NewsActions singleton data operations to VoicesRepository

The `NewsActions` object acts as a data access layer in the UI package, performing direct Realm queries and transactions. This violates repository boundaries by coupling UI code directly to database operations. The singleton pattern makes testing difficult and creates hidden dependencies.

:codex-file-citation[codex-file-citation]{line_range_start=168 line_range_end=186 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsActions.kt#L168-L186"}
:codex-file-citation[codex-file-citation]{line_range_start=256 line_range_end=306 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsActions.kt#L256-L306"}

:::task-stub{title="Move NewsActions.deletePost to VoicesRepository"}
1. Add `suspend fun deletePost(newsId: String, teamName: String)` to VoicesRepository interface
2. Implement in VoicesRepositoryImpl using existing `deleteNews` and `deleteRepliesOf` patterns
3. Update NewsActions.deletePost callers to inject and use VoicesRepository
4. Remove deletePost and deleteChildPosts from NewsActions object
:::

---

### 2. Move NewsActions.editPost and postReply to VoicesRepository

The `editPost` and `postReply` methods in NewsActions perform direct Realm transactions for news updates. These operations belong in the VoicesRepository to maintain a single source of truth for news data mutations and enable proper transactional boundaries.

:codex-file-citation[codex-file-citation]{line_range_start=188 line_range_end=233 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsActions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsActions.kt#L188-L233"}

:::task-stub{title="Move NewsActions.editPost and postReply to VoicesRepository"}
1. Add `suspend fun editNews(newsId: String, message: String, imageUrls: List<String>?)` to VoicesRepository
2. Add `suspend fun createReply(parentNewsId: String, message: String, imageUrls: List<String>?)` to VoicesRepository
3. Implement both methods in VoicesRepositoryImpl using executeTransaction pattern
4. Update NewsActions.handlePositiveButton to delegate to repository methods via injected dependency
:::

---

### 3. Move DictionaryActivity data loading to new DictionaryRepository

DictionaryActivity directly accesses DatabaseService and performs Realm transactions for dictionary initialization and search. Creating a dedicated DictionaryRepository isolates this domain data and enables future testability and caching improvements.

:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L52-L93"}
:codex-file-citation[codex-file-citation]{line_range_start=105 line_range_end=131 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L105-L131"}

:::task-stub{title="Create DictionaryRepository and migrate data operations"}
1. Create DictionaryRepository interface with: `suspend fun getDictionaryCount(): Long`, `suspend fun initializeDictionary(jsonArray: JsonArray)`, `suspend fun searchWord(query: String): RealmDictionary?`
2. Create DictionaryRepositoryImpl extending RealmRepository
3. Move loadDictionaryIfNeeded logic to repository initializeDictionary method
4. Inject DictionaryRepository into DictionaryActivity and replace direct databaseService calls
:::

---

### 4. Move ExamSubmissionUtils data operations to SubmissionsRepository

ExamSubmissionUtils is a utility object that performs direct Realm transactions for exam answer saving and submission status updates. These data mutations should be encapsulated in SubmissionsRepository to maintain consistent data access patterns across the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=63 path=app/src/main/java/org/ole/planet/myplanet/utilities/ExamSubmissionUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/ExamSubmissionUtils.kt#L13-L63"}
:codex-file-citation[codex-file-citation]{line_range_start=87 line_range_end=111 path=app/src/main/java/org/ole/planet/myplanet/utilities/ExamSubmissionUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/ExamSubmissionUtils.kt#L87-L111"}

:::task-stub{title="Move ExamSubmissionUtils.saveAnswer to SubmissionsRepository"}
1. Add `suspend fun saveAnswer(submissionId: String?, questionId: String, answer: String, listAnswers: Map<String, String>?, otherText: String?, type: String, index: Int, total: Int): Boolean` to SubmissionsRepository interface
2. Implement in SubmissionsRepositoryImpl using executeTransaction for async Realm operations
3. Move createOrRetrieveAnswer, populateAnswer, updateSubmissionStatus as private methods in SubmissionsRepositoryImpl
4. Update ExamTakingFragment and other callers to use injected SubmissionsRepository
:::

---

### 5. Add getCourseProgress method to CoursesRepository

CourseProgressActivity directly queries multiple Realm models (RealmMyCourse, RealmCourseProgress, RealmStepExam, RealmSubmission) to calculate course progress. This complex cross-model query belongs in CoursesRepository to centralize course-related data access.

:codex-file-citation[codex-file-citation]{line_range_start=72 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressActivity.kt#L72-L93"}
:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt#L59-L73"}

:::task-stub{title="Add getCourseProgress to CoursesRepository"}
1. Add `suspend fun getCourseProgress(courseId: String, userId: String?): CourseProgressData?` to CoursesRepository interface
2. Create CourseProgressData data class with title, current, max, steps fields
3. Implement in CoursesRepositoryImpl, moving loadData logic from CourseProgressActivity
4. Inject CoursesRepository into CourseProgressActivity and replace direct databaseService calls
:::

---

### 6. Move ChallengeHelper.fetchVoiceDates to VoicesRepository

ChallengeHelper queries RealmNews directly through databaseService to fetch voice dates for challenge evaluation. This cross-feature data access violates domain boundaries; voices data should be accessed through VoicesRepository.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=93 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt#L84-L93"}
:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=61 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt#L52-L61"}

:::task-stub{title="Move fetchVoiceDates to VoicesRepository"}
1. Add `suspend fun getCommunityVoiceDates(startTime: Long, endTime: Long, userId: String?): List<String>` to VoicesRepository interface
2. Implement in VoicesRepositoryImpl with query filtering and date extraction
3. Inject VoicesRepository into ChallengeHelper constructor
4. Replace fetchVoiceDates calls with repository method calls
:::

---

### 7. Move AchievementFragment cross-feature library query to repository

AchievementFragment queries RealmMyLibrary directly to fetch resources associated with achievements. This creates a cross-feature dependency where user/achievement code directly accesses resource domain models. The query should be abstracted through a repository method.

:codex-file-citation[codex-file-citation]{line_range_start=171 line_range_end=207 path=app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt#L171-L207"}

:::task-stub{title="Create AchievementsRepository for cross-feature queries"}
1. Create AchievementsRepository interface with: `suspend fun getUserAchievement(userId: String, planetCode: String): RealmAchievement?`, `suspend fun getAchievementResources(resourceIds: List<String>): List<RealmMyLibrary>`
2. Create AchievementsRepositoryImpl extending RealmRepository
3. Bind in RepositoryModule
4. Inject into AchievementFragment and replace direct realm queries in loadAchievementDataAsync
:::

---

### 8. Add validateUsername to UserRepository from AuthUtils

AuthUtils.validateUsername performs a Realm query to check username existence. The UserRepository interface already has `validateUsername` defined but AuthUtils duplicates this with direct Realm access. Remove the duplication and ensure consistent usage.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=34 path=app/src/main/java/org/ole/planet/myplanet/utilities/AuthUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/AuthUtils.kt#L19-L34"}
:codex-file-citation[codex-file-citation]{line_range_start=65 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L65-L66"}

:::task-stub{title="Consolidate AuthUtils.validateUsername with UserRepository"}
1. Verify UserRepository.validateUsername implementation handles all validation rules from AuthUtils
2. Update AuthUtils.validateUsername to accept UserRepository instead of Realm parameter
3. Have AuthUtils delegate username existence check to userRepository.validateUsername
4. Update callers that pass Realm to AuthUtils to instead inject and pass UserRepository
:::

---

### 9. Migrate VoicesAdapter to ListAdapter with DiffUtils.itemCallback

VoicesAdapter uses notifyDataSetChanged for list updates which causes full rebinds and poor performance. The codebase has DiffUtils.itemCallback utility ready for use. Converting to ListAdapter with proper diffing will improve UI responsiveness.

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/utilities/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/DiffUtils.kt#L6-L14"}
:codex-file-citation[codex-file-citation]{line_range_start=42 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt#L42-L57"}

:::task-stub{title="Migrate VoicesAdapter to ListAdapter with DiffUtils"}
1. Change VoicesAdapter to extend ListAdapter<RealmNews, ViewHolder> instead of RecyclerView.Adapter
2. Create DiffUtil.ItemCallback using DiffUtils.itemCallback comparing news id and revision
3. Replace notifyDataSetChanged calls with submitList
4. Update getItemCount to use currentList.size
:::

---

### 10. Move StepsAdapter to ListAdapter with DiffUtils.itemCallback

StepsAdapter uses notifyItemChanged for individual updates but the base data list changes require manual diffing. Converting to ListAdapter provides automatic efficient diffing matching the pattern established in ChatHistoryAdapter and CoursesAdapter.

:codex-file-citation[codex-file-citation]{line_range_start=81 line_range_end=128 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt#L81-L128"}

:::task-stub{title="Migrate StepsAdapter to ListAdapter with DiffUtils"}
1. Change StepsAdapter to extend ListAdapter<RealmCourseStep, ViewHolder>
2. Create DiffUtil.ItemCallback using DiffUtils.itemCallback comparing step id
3. Define areContentsTheSame comparing stepTitle, description, noOfResources
4. Replace manual list updates with submitList calls
:::

---

## Summary

| Task | Category | Risk | Files Affected |
|------|----------|------|----------------|
| 1. NewsActions.deletePost | Repository Boundary | Low | 2 |
| 2. NewsActions.editPost/postReply | Repository Boundary | Low | 2 |
| 3. DictionaryRepository | New Repository | Low | 3 |
| 4. ExamSubmissionUtils.saveAnswer | Repository Boundary | Medium | 3 |
| 5. getCourseProgress | Repository Interface | Low | 3 |
| 6. fetchVoiceDates | Cross-Feature Leak | Low | 2 |
| 7. AchievementsRepository | New Repository | Low | 3 |
| 8. validateUsername consolidation | DI Cleanup | Low | 2 |
| 9. VoicesAdapter DiffUtil | DiffUtil Migration | Low | 1 |
| 10. StepsAdapter DiffUtil | DiffUtil Migration | Low | 1 |

All tasks are granular, avoid merge conflicts with each other, and follow established patterns in the codebase.
