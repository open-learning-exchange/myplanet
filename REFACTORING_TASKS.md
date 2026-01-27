# Refactoring Tasks: Structure and Naming Consistency

This document contains 15 granular refactoring tasks focused on improving naming consistency, package organization, and code structure. Tasks are designed to minimize merge conflicts when run in parallel.

---

### 1. Rename ui/exam package to ui/exams for plural consistency

The `ui/exam` package uses singular naming while most other feature packages use plural form (courses, teams, surveys, submissions). Standardizing to plural improves consistency with the established naming convention.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L1-L50"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L1-L30"}

:::task-stub{title="Rename ui/exam to ui/exams"}
1. Rename directory from `ui/exam/` to `ui/exams/`
2. Update package declarations in ExamTakingFragment.kt and UserInformationFragment.kt
3. Update all import statements referencing `org.ole.planet.myplanet.ui.exam`
:::

---

### 2. Rename ui/calendar package to ui/calendars for plural consistency

The `ui/calendar` package contains a single file but uses singular naming. Renaming to plural aligns with the convention used by similar feature packages (events, teams, courses).

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt#L1-L30"}

:::task-stub{title="Rename ui/calendar to ui/calendars"}
1. Rename directory from `ui/calendar/` to `ui/calendars/`
2. Update package declaration in CalendarFragment.kt
3. Update all import statements referencing `org.ole.planet.myplanet.ui.calendar`
:::

---

### 3. Rename ui/dictionary package to ui/dictionaries for plural consistency

The `ui/dictionary` package uses singular naming while parallel feature packages use plural form. This inconsistency affects codebase navigability and developer expectations.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L1-L30"}

:::task-stub{title="Rename ui/dictionary to ui/dictionaries"}
1. Rename directory from `ui/dictionary/` to `ui/dictionaries/`
2. Update package declaration in DictionaryActivity.kt
3. Update AndroidManifest.xml activity reference
4. Update all import statements referencing `org.ole.planet.myplanet.ui.dictionary`
:::

---

### 4. Rename ui/user package to ui/users for plural consistency

The `ui/user` package contains 6 files handling user profiles and achievements but uses singular naming. Other user-related packages like `teams`, `courses` use plural form.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileFragment.kt#L1-L30"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt#L1-L30"}

:::task-stub{title="Rename ui/user to ui/users"}
1. Rename directory from `ui/user/` to `ui/users/`
2. Update package declarations in all 6 files (UserProfileFragment, UserProfileAdapter, UserProfileViewModel, AchievementFragment, EditAchievementFragment, BecomeMemberActivity)
3. Update AndroidManifest.xml activity reference for BecomeMemberActivity
4. Update all import statements referencing `org.ole.planet.myplanet.ui.user`
:::

---

### 5. Move BroadcastServiceEntryPoint to di package for DI convention

The `BroadcastServiceEntryPoint.kt` file is a Hilt entry point interface located in the `services/` package, but all other entry points reside in the `di/` package. This violates the established dependency injection organization pattern.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/services/BroadcastServiceEntryPoint.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/BroadcastServiceEntryPoint.kt#L1-L22"}

:::task-stub{title="Move BroadcastServiceEntryPoint to di package"}
1. Move file from `services/BroadcastServiceEntryPoint.kt` to `di/BroadcastServiceEntryPoint.kt`
2. Update package declaration from `org.ole.planet.myplanet.services` to `org.ole.planet.myplanet.di`
3. Update all import statements referencing the old location
4. Verify getBroadcastService helper function works correctly
:::

---

### 6. Move OnBaseRealtimeSyncListener to base package as abstract adapter

The `OnBaseRealtimeSyncListener` is an abstract class providing default implementations, not a pure callback interface. Abstract adapters belong in the `base/` package, not the `callback/` package which should contain only interfaces.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/callback/OnBaseRealtimeSyncListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnBaseRealtimeSyncListener.kt#L1-L31"}

:::task-stub{title="Move OnBaseRealtimeSyncListener to base package"}
1. Move file from `callback/OnBaseRealtimeSyncListener.kt` to `base/OnBaseRealtimeSyncListener.kt`
2. Update package declaration from `org.ole.planet.myplanet.callback` to `org.ole.planet.myplanet.base`
3. Update all import statements referencing the old location
4. Optionally rename to `BaseRealtimeSyncListener` to follow base class naming convention
:::

---

### 7. Rename RealmExamination to Examination (not a RealmObject)

The `RealmExamination` class uses the `Realm` prefix but is a simple `Serializable` class, not a `RealmObject`. This misleading naming creates confusion about the class's persistence behavior.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=17 path=app/src/main/java/org/ole/planet/myplanet/model/RealmExamination.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmExamination.kt#L1-L17"}

:::task-stub{title="Rename RealmExamination to Examination"}
1. Rename file from `model/RealmExamination.kt` to `model/Examination.kt`
2. Rename class from `RealmExamination` to `Examination`
3. Update all import statements and usages throughout codebase
4. Verify serialization still works correctly
:::

---

### 8. Rename ChatModel to ChatResponse for consistency

The `ChatModel` class uses `Model` suffix while other API response classes use descriptive names like `DocumentResponse`, `HealthRecord`, `ChatMessage`. Renaming to `ChatResponse` clarifies its purpose as an API response object.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=18 path=app/src/main/java/org/ole/planet/myplanet/model/ChatModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/ChatModel.kt#L1-L18"}

:::task-stub{title="Rename ChatModel to ChatResponse"}
1. Rename file from `model/ChatModel.kt` to `model/ChatResponse.kt`
2. Rename class from `ChatModel` to `ChatResponse`
3. Update all import statements and usages throughout codebase
4. Update any Retrofit response type declarations
:::

---

### 9. Rename RealmUserModel to RealmUser for naming consistency

The `RealmUserModel` class uses redundant `Model` suffix while other Realm classes use concise names like `RealmMyCourse`, `RealmMyTeam`, `RealmNews`. Removing the suffix improves consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/model/RealmUserModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmUserModel.kt#L1-L60"}

:::task-stub{title="Rename RealmUserModel to RealmUser"}
1. Rename file from `model/RealmUserModel.kt` to `model/RealmUser.kt`
2. Rename class from `RealmUserModel` to `RealmUser`
3. Update all import statements and usages throughout codebase (extensive - 100+ references expected)
4. Update Realm queries referencing the class name
5. Verify database migration is not required (class name change only)
:::

---

### 10. Rename FileUploadService to FileUploadHelper (not an Android Service)

The `FileUploadService` class does not extend Android's `Service` class but uses the `Service` suffix, creating confusion with actual Android services like `DownloadService`. Renaming to `Helper` clarifies its utility nature.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/services/FileUploadService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/FileUploadService.kt#L1-L88"}

:::task-stub{title="Rename FileUploadService to FileUploadHelper"}
1. Rename file from `services/FileUploadService.kt` to `services/FileUploadHelper.kt`
2. Rename class from `FileUploadService` to `FileUploadHelper`
3. Update all import statements and usages throughout codebase
4. Update any DI module bindings if present
:::

---

### 11. Rename AudioRecorderService to AudioRecorderHelper (not an Android Service)

The `AudioRecorderService` class does not extend Android's `Service` class but uses the `Service` suffix. This creates semantic confusion with framework services. Renaming to `Helper` or `Recorder` clarifies its purpose.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/services/AudioRecorderService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/AudioRecorderService.kt#L1-L60"}

:::task-stub{title="Rename AudioRecorderService to AudioRecorderHelper"}
1. Rename file from `services/AudioRecorderService.kt` to `services/AudioRecorderHelper.kt`
2. Rename class from `AudioRecorderService` to `AudioRecorderHelper`
3. Update all import statements and usages throughout codebase
4. Update any DI module bindings if present
:::

---

### 12. Rename UserRepository to UsersRepository for plural consistency

The `UserRepository` interface uses singular naming while most other repositories use plural form (TeamsRepository, CoursesRepository, ResourcesRepository). This inconsistency affects API discoverability.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L1-L71"}

:::task-stub{title="Rename UserRepository to UsersRepository"}
1. Rename file from `repository/UserRepository.kt` to `repository/UsersRepository.kt`
2. Rename interface from `UserRepository` to `UsersRepository`
3. Rename file from `repository/UserRepositoryImpl.kt` to `repository/UsersRepositoryImpl.kt`
4. Rename class from `UserRepositoryImpl` to `UsersRepositoryImpl`
5. Update RepositoryModule.kt binding
6. Update all injection sites and import statements
:::

---

### 13. Rename ChatRepository to ChatsRepository for plural consistency

The `ChatRepository` interface uses singular naming while peer repositories use plural form. Standardizing improves consistency across the repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt#L1-L14"}

:::task-stub{title="Rename ChatRepository to ChatsRepository"}
1. Rename file from `repository/ChatRepository.kt` to `repository/ChatsRepository.kt`
2. Rename interface from `ChatRepository` to `ChatsRepository`
3. Rename file from `repository/ChatRepositoryImpl.kt` to `repository/ChatsRepositoryImpl.kt`
4. Rename class from `ChatRepositoryImpl` to `ChatsRepositoryImpl`
5. Update RepositoryModule.kt binding
6. Update all injection sites and import statements
:::

---

### 14. Rename FeedbackRepository to FeedbacksRepository for plural consistency

The `FeedbackRepository` interface uses singular naming, inconsistent with the plural convention used by TeamsRepository, CoursesRepository, SubmissionsRepository, and others.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt#L1-L20"}

:::task-stub{title="Rename FeedbackRepository to FeedbacksRepository"}
1. Rename file from `repository/FeedbackRepository.kt` to `repository/FeedbacksRepository.kt`
2. Rename interface from `FeedbackRepository` to `FeedbacksRepository`
3. Rename file from `repository/FeedbackRepositoryImpl.kt` to `repository/FeedbacksRepositoryImpl.kt`
4. Rename class from `FeedbackRepositoryImpl` to `FeedbacksRepositoryImpl`
5. Update RepositoryModule.kt binding
6. Update all injection sites and import statements
:::

---

### 15. Rename ProgressRepository to ProgressesRepository for plural consistency

The `ProgressRepository` interface uses singular naming while the majority of repositories follow plural convention. Although "progresses" is grammatically awkward, consistency with the codebase pattern takes precedence.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt#L1-L20"}

:::task-stub{title="Rename ProgressRepository to ProgressesRepository"}
1. Rename file from `repository/ProgressRepository.kt` to `repository/ProgressesRepository.kt`
2. Rename interface from `ProgressRepository` to `ProgressesRepository`
3. Rename file from `repository/ProgressRepositoryImpl.kt` to `repository/ProgressesRepositoryImpl.kt`
4. Rename class from `ProgressRepositoryImpl` to `ProgressesRepositoryImpl`
5. Update RepositoryModule.kt binding
6. Update all injection sites and import statements
:::

---

## Parallelization Guidelines

### Safe to Run in Parallel (No Conflicts)

**Group A - UI Package Renames (isolated packages):**
- Task 1: ui/exam → ui/exams
- Task 2: ui/calendar → ui/calendars
- Task 3: ui/dictionary → ui/dictionaries
- Task 4: ui/user → ui/users

**Group B - Model Renames (different files):**
- Task 7: RealmExamination → Examination
- Task 8: ChatModel → ChatResponse
- Task 9: RealmUserModel → RealmUser

**Group C - Service Renames (different files):**
- Task 10: FileUploadService → FileUploadHelper
- Task 11: AudioRecorderService → AudioRecorderHelper

**Group D - File Moves (different destinations):**
- Task 5: BroadcastServiceEntryPoint → di/
- Task 6: OnBaseRealtimeSyncListener → base/

### Requires Sequential Execution (Shared Dependencies)

**Repository Renames (all modify RepositoryModule.kt):**
- Task 12: UserRepository → UsersRepository
- Task 13: ChatRepository → ChatsRepository
- Task 14: FeedbackRepository → FeedbacksRepository
- Task 15: ProgressRepository → ProgressesRepository

**Recommendation:** Run repository renames sequentially OR as a single combined task to avoid merge conflicts in `RepositoryModule.kt`.

---

## Summary

| Category | Count | Conflict Risk |
|----------|-------|---------------|
| UI Package Renames | 4 | Low |
| Model File Renames | 3 | Low |
| Service File Renames | 2 | Low |
| File Moves | 2 | Low |
| Repository Renames | 4 | High (shared file) |

**Total Tasks:** 15
