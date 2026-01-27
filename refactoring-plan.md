# myPlanet Refactoring Plan - 15 Granular Tasks

This document outlines 15 focused refactoring tasks for improving code organization, naming consistency, and package structure in the myPlanet Android application.

---

### Organize model package by domain (Team models)

The model package contains 65+ classes in a flat structure. Grouping team-related models into a dedicated subdirectory improves navigability and reduces cognitive load when searching for specific entities.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=65 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/model git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/model#L1-L65"}

:::task-stub{title="Move team-related models to model/team subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/model/team/`
2. Move `RealmMyTeam.kt` to the new team subdirectory
3. Move `RealmTeamLog.kt` to the new team subdirectory
4. Move `RealmTeamNotification.kt` to the new team subdirectory
5. Move `RealmTeamTask.kt` to the new team subdirectory
6. Move `TeamDetails.kt` to the new team subdirectory
7. Move `TeamNotificationInfo.kt` to the new team subdirectory
8. Update package declarations in all moved files
9. Update all imports throughout the codebase
10. Verify build succeeds
:::

---

### Organize model package by domain (Course models)

Course-related models are scattered across the flat model directory. Consolidating them into a dedicated subdirectory improves domain cohesion and makes course entities easier to locate.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=65 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/model git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/model#L1-L65"}

:::task-stub{title="Move course-related models to model/course subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/model/course/`
2. Move `RealmMyCourse.kt` to the new course subdirectory
3. Move `RealmCourseActivity.kt` to the new course subdirectory
4. Move `RealmCourseProgress.kt` to the new course subdirectory
5. Move `RealmCourseStep.kt` to the new course subdirectory
6. Move `CourseProgressData.kt` to the new course subdirectory
7. Update package declarations in all moved files
8. Update all imports throughout the codebase
9. Verify build succeeds
:::

---

### Organize model package by domain (Chat models)

Chat functionality has multiple model classes that should be colocated. Moving them to a dedicated chat subdirectory provides better separation of concerns and domain clarity.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=65 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/model git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/model#L1-L65"}

:::task-stub{title="Move chat-related models to model/chat subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/model/chat/`
2. Move `ChatMessage.kt` to the new chat subdirectory
3. Move `ChatModel.kt` to the new chat subdirectory
4. Move `ChatRequest.kt` to the new chat subdirectory
5. Move `ChatShareTargets.kt` to the new chat subdirectory
6. Move `RealmChatHistory.kt` to the new chat subdirectory
7. Move `RealmConversation.kt` to the new chat subdirectory
8. Update package declarations in all moved files
9. Update all imports throughout the codebase
10. Verify build succeeds
:::

---

### Organize model package by domain (Exam models)

Exam and examination models span multiple contexts (courses, health, general). Grouping them together improves maintainability for assessment-related functionality.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=65 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/model git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/model#L1-L65"}

:::task-stub{title="Move exam-related models to model/exam subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/model/exam/`
2. Move `RealmExamQuestion.kt` to the new exam subdirectory
3. Move `RealmExamination.kt` to the new exam subdirectory
4. Move `RealmStepExam.kt` to the new exam subdirectory
5. Move `QuestionAnswer.kt` to the new exam subdirectory
6. Update package declarations in all moved files
7. Update all imports throughout the codebase
8. Verify build succeeds
:::

---

### Organize model package by domain (Submission models)

Submission models are currently mixed with other models. Creating a dedicated subdirectory clarifies the domain boundary for submission-related data structures.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=65 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/model git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/model#L1-L65"}

:::task-stub{title="Move submission-related models to model/submission subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/model/submission/`
2. Move `RealmSubmission.kt` to the new submission subdirectory
3. Move `RealmSubmitPhotos.kt` to the new submission subdirectory
4. Move `SubmissionDetail.kt` to the new submission subdirectory
5. Move `SubmissionItem.kt` to the new submission subdirectory
6. Update package declarations in all moved files
7. Update all imports throughout the codebase
8. Verify build succeeds
:::

---

### Organize model package by domain (Health models)

Health-related models should be grouped together to separate this domain from other concerns and make health functionality easier to understand and maintain.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=65 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/model git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/model#L1-L65"}

:::task-stub{title="Move health-related models to model/health subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/model/health/`
2. Move `HealthRecord.kt` to the new health subdirectory
3. Move `RealmMyHealth.kt` to the new health subdirectory
4. Move `RealmHealthExamination.kt` to the new health subdirectory
5. Update package declarations in all moved files
6. Update all imports throughout the codebase
7. Verify build succeeds
:::

---

### Organize utils package by category (UI utilities)

The utils package contains 39 files with no organization. Grouping UI-related utilities into a subdirectory improves discoverability and reduces package clutter.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=39 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/utils git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/utils#L1-L39"}

:::task-stub{title="Move UI utilities to utils/ui subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/utils/ui/`
2. Move `DialogUtils.kt` to the new ui subdirectory
3. Move `KeyboardUtils.kt` to the new ui subdirectory
4. Move `ViewExtensions.kt` to the new ui subdirectory
5. Move `TextViewExtensions.kt` to the new ui subdirectory
6. Move `EdgeToEdgeUtils.kt` to the new ui subdirectory
7. Move `DimenUtils.kt` to the new ui subdirectory
8. Move `ImageUtils.kt` to the new ui subdirectory
9. Move `ThemeMode.kt` to the new ui subdirectory
10. Update package declarations in all moved files
11. Update all imports throughout the codebase
12. Verify build succeeds
:::

---

### Organize utils package by category (Network utilities)

Network-related utilities should be grouped together to separate infrastructure concerns and make networking utilities easier to find.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=39 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/utils git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/utils#L1-L39"}

:::task-stub{title="Move network utilities to utils/network subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/utils/network/`
2. Move `NetworkUtils.kt` to the new network subdirectory
3. Move `DownloadUtils.kt` to the new network subdirectory
4. Move `RetryUtils.kt` to the new network subdirectory
5. Move `UrlUtils.kt` to the new network subdirectory
6. Move `ServerConfigUtils.kt` to the new network subdirectory
7. Update package declarations in all moved files
8. Update all imports throughout the codebase
9. Verify build succeeds
:::

---

### Organize utils package by category (Security utilities)

Security and cryptography utilities should be isolated to clearly identify sensitive operations and improve security auditing.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=39 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/utils git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/utils#L1-L39"}

:::task-stub{title="Move security utilities to utils/security subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/utils/security/`
2. Move `SecurePrefs.kt` to the new security subdirectory
3. Move `AndroidDecrypter.kt` to the new security subdirectory
4. Move `Sha256Utils.kt` to the new security subdirectory
5. Move `AuthUtils.kt` to the new security subdirectory
6. Update package declarations in all moved files
7. Update all imports throughout the codebase
8. Verify build succeeds
:::

---

### Move ChallengeManager from ui/dashboard to services

ChallengeManager is business logic that doesn't belong in a UI package. Moving it to the services package clarifies that it's a domain service, not a UI component.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=147 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeManager.kt#L1-L147"}

:::task-stub{title="Relocate ChallengeManager to services package"}
1. Move `ChallengeManager.kt` from `ui/dashboard/` to `services/`
2. Update package declaration from `org.ole.planet.myplanet.ui.dashboard` to `org.ole.planet.myplanet.services`
3. Update imports in `DashboardActivity.kt`
4. Update imports in `BellDashboardFragment.kt`
5. Update any other files that reference ChallengeManager
6. Verify build succeeds
:::

---

### Move NavigationHelper from utils to ui/components

NavigationHelper is specifically for UI navigation and doesn't belong with general utilities. Moving it to ui/components clarifies its purpose as a UI-specific helper.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=63 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/utils/NavigationHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/utils/NavigationHelper.kt#L1-L63"}

:::task-stub{title="Relocate NavigationHelper to ui/components"}
1. Move `NavigationHelper.kt` from `utils/` to `ui/components/`
2. Update package declaration from `org.ole.planet.myplanet.utils` to `org.ole.planet.myplanet.ui.components`
3. Update imports in `BaseVoicesFragment.kt`
4. Update imports in `BaseContainerFragment.kt`
5. Update imports in `BaseExamFragment.kt`
6. Update imports in `DashboardActivity.kt`
7. Update any other files that reference NavigationHelper
8. Verify build succeeds
:::

---

### Organize services package (Create workers subdirectory)

The services package mixes Workers with Services and Managers. Creating a dedicated workers subdirectory improves structure and makes background task management clearer.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/services git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/services#L1-L20"}

:::task-stub{title="Move Worker classes to services/workers subdirectory"}
1. Create new directory `app/src/main/java/org/ole/planet/myplanet/services/workers/`
2. Move `AutoSyncWorker.kt` to the new workers subdirectory
3. Move `DownloadWorker.kt` to the new workers subdirectory
4. Move `FreeSpaceWorker.kt` to the new workers subdirectory
5. Move `NetworkMonitorWorker.kt` to the new workers subdirectory
6. Move `ServerReachabilityWorker.kt` to the new workers subdirectory
7. Move `StayOnlineWorker.kt` to the new workers subdirectory
8. Move `TaskNotificationWorker.kt` to the new workers subdirectory
9. Update package declarations in all moved files
10. Update all imports throughout the codebase
11. Verify build succeeds
:::

---

### Colocate team callbacks with team UI package

Team-related callback interfaces are in the root callback package but are only used by team UI components. Moving them colocates related code and reduces coupling.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=35 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/callback git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/callback#L1-L35"}

:::task-stub{title="Move team callbacks to ui/teams package"}
1. Move `OnTeamActionsListener.kt` from `callback/` to `ui/teams/`
2. Move `OnTeamEditListener.kt` from `callback/` to `ui/teams/`
3. Move `OnTeamPageListener.kt` from `callback/` to `ui/teams/`
4. Move `OnTeamUpdateListener.kt` from `callback/` to `ui/teams/`
5. Update package declarations from `org.ole.planet.myplanet.callback` to `org.ole.planet.myplanet.ui.teams`
6. Update all imports in team-related classes
7. Update imports in `TeamsFragment.kt`
8. Update imports in `TeamsAdapter.kt`
9. Verify build succeeds
:::

---

### Colocate chat callbacks with chat UI package

Chat-related callback interfaces are in the root callback package but are only used by chat UI components. Moving them improves cohesion and reduces unnecessary package dependencies.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=35 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/callback git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/callback#L1-L35"}

:::task-stub{title="Move chat callbacks to ui/chat package"}
1. Move `OnChatItemClickListener.kt` from `callback/` to `ui/chat/`
2. Move `OnChatHistoryItemClickListener.kt` from `callback/` to `ui/chat/`
3. Update package declarations from `org.ole.planet.myplanet.callback` to `org.ole.planet.myplanet.ui.chat`
4. Update imports in `ChatAdapter.kt`
5. Update imports in `ChatHistoryAdapter.kt`
6. Update imports in `ChatFragment.kt`
7. Verify build succeeds
:::

---

### Colocate course callback with courses UI package

Course-related callback interface is in the root callback package but is only used by course UI components. Moving it improves feature cohesion and package organization.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=35 path=/home/runner/work/myplanet/myplanet/app/src/main/java/org/ole/planet/myplanet/callback git_url="https://github.com/open-learning-exchange/myplanet/blob/HEAD/app/src/main/java/org/ole/planet/myplanet/callback#L1-L35"}

:::task-stub{title="Move course callback to ui/courses package"}
1. Move `OnCourseItemSelectedListener.kt` from `callback/` to `ui/courses/`
2. Update package declaration from `org.ole.planet.myplanet.callback` to `org.ole.planet.myplanet.ui.courses`
3. Update imports in `CoursesFragment.kt`
4. Update imports in `CoursesAdapter.kt`
5. Update imports in any other course-related classes
6. Verify build succeeds
:::

---

## Testing Strategy

After completing each task:

1. **Build verification**: Run `./gradlew assembleDefaultDebug` to ensure compilation succeeds
2. **Import validation**: Search for old package names to verify all imports were updated
3. **Git verification**: Review changed files to ensure only expected files were modified
4. **Parallelization**: Tasks are designed to modify different file sets and can be executed in parallel without merge conflicts

## Notes

- All tasks are **cosmetic/structural** improvements only
- No new classes, features, or architectural changes
- Focus on improving code organization and maintainability
- Each task is granular and can be completed independently
- Tasks minimize merge conflicts by working on separate file sets
