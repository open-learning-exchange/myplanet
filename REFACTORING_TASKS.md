# Refactoring Tasks: Structure and Naming Consistency

This document contains 15 granular refactoring tasks focused on naming consistency and package organization. Tasks are designed to minimize merge conflicts and can be executed in parallel where noted.

---

## Repository Naming: Singular → Plural

These 5 tasks standardize repository naming to plural form for consistency with the existing pattern (e.g., CoursesRepository, TeamsRepository, VoicesRepository).

> **Note:** Tasks 1-5 all modify `di/RepositoryModule.kt` and should be executed as a batch or sequentially to avoid merge conflicts.

---

### 1. Rename ChatRepository to ChatsRepository

The ChatRepository interface and implementation use singular naming while most other repositories use plural naming (CoursesRepository, TeamsRepository, etc.). Standardizing to plural improves consistency across the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=13 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt#L7-L13"}

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt#L12-L14"}

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt#L54-L56"}

:::task-stub{title="Rename ChatRepository to ChatsRepository"}
1. Rename `ChatRepository.kt` to `ChatsRepository.kt`
2. Rename interface `ChatRepository` to `ChatsRepository` inside the file
3. Rename `ChatRepositoryImpl.kt` to `ChatsRepositoryImpl.kt`
4. Update class name `ChatRepositoryImpl` to `ChatsRepositoryImpl`
5. Update `ChatsRepositoryImpl` to implement `ChatsRepository`
6. Update `RepositoryModule.kt` binding from `ChatRepository` to `ChatsRepository`
7. Update all import statements referencing `ChatRepository` across the codebase
8. Update all injection sites using `ChatRepository` type
:::

---

### 2. Rename FeedbackRepository to FeedbacksRepository

The FeedbackRepository uses singular naming inconsistent with the plural pattern used by most repositories in the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt#L8-L22"}

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt#L15-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=69 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt#L69-L71"}

:::task-stub{title="Rename FeedbackRepository to FeedbacksRepository"}
1. Rename `FeedbackRepository.kt` to `FeedbacksRepository.kt`
2. Rename interface `FeedbackRepository` to `FeedbacksRepository` inside the file
3. Rename `FeedbackRepositoryImpl.kt` to `FeedbacksRepositoryImpl.kt`
4. Update class name `FeedbackRepositoryImpl` to `FeedbacksRepositoryImpl`
5. Update `FeedbacksRepositoryImpl` to implement `FeedbacksRepository`
6. Update `RepositoryModule.kt` binding from `FeedbackRepository` to `FeedbacksRepository`
7. Update all import statements referencing `FeedbackRepository` across the codebase
8. Update all injection sites using `FeedbackRepository` type
:::

---

### 3. Rename LifeRepository to LivesRepository

The LifeRepository uses singular naming. Renaming to LivesRepository aligns with the plural convention used by PersonalsRepository, ResourcesRepository, and others.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt#L5-L9"}

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=7 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L7-L7"}

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=75 path=app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt#L73-L75"}

:::task-stub{title="Rename LifeRepository to LivesRepository"}
1. Rename `LifeRepository.kt` to `LivesRepository.kt`
2. Rename interface `LifeRepository` to `LivesRepository` inside the file
3. Rename `LifeRepositoryImpl.kt` to `LivesRepositoryImpl.kt`
4. Update class name `LifeRepositoryImpl` to `LivesRepositoryImpl`
5. Update `LivesRepositoryImpl` to implement `LivesRepository`
6. Update `RepositoryModule.kt` binding from `LifeRepository` to `LivesRepository`
7. Update all import statements referencing `LifeRepository` across the codebase
8. Update all injection sites using `LifeRepository` type
:::

---

### 4. Rename ProgressRepository to ProgressesRepository

The ProgressRepository uses singular naming. This is inconsistent with the established plural pattern for repositories managing collections of data.

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt#L8-L22"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L1-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=85 line_range_end=87 path=app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt#L85-L87"}

:::task-stub{title="Rename ProgressRepository to ProgressesRepository"}
1. Rename `ProgressRepository.kt` to `ProgressesRepository.kt`
2. Rename interface `ProgressRepository` to `ProgressesRepository` inside the file
3. Rename `ProgressRepositoryImpl.kt` to `ProgressesRepositoryImpl.kt`
4. Update class name `ProgressRepositoryImpl` to `ProgressesRepositoryImpl`
5. Update `ProgressesRepositoryImpl` to implement `ProgressesRepository`
6. Update `RepositoryModule.kt` binding from `ProgressRepository` to `ProgressesRepository`
7. Update all import statements referencing `ProgressRepository` across the codebase
8. Update all injection sites using `ProgressRepository` type
:::

---

### 5. Rename UserRepository to UsersRepository

The UserRepository uses singular naming while handling collections of users. Renaming to UsersRepository matches the pattern of TeamsRepository, CoursesRepository, etc.

:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L9-L71"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L1-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=113 line_range_end=115 path=app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt#L113-L115"}

:::task-stub{title="Rename UserRepository to UsersRepository"}
1. Rename `UserRepository.kt` to `UsersRepository.kt`
2. Rename interface `UserRepository` to `UsersRepository` inside the file
3. Rename `UserRepositoryImpl.kt` to `UsersRepositoryImpl.kt`
4. Update class name `UserRepositoryImpl` to `UsersRepositoryImpl`
5. Update `UsersRepositoryImpl` to implement `UsersRepository`
6. Update `RepositoryModule.kt` binding from `UserRepository` to `UsersRepository`
7. Update all import statements referencing `UserRepository` across the codebase
8. Update all injection sites using `UserRepository` type
:::

---

## Package Reorganization: Utils to Services

These tasks move manager and receiver classes from `utils/` to `services/` for better logical organization. Manager classes handle application state and services, making them a better fit for the `services/` package.

> **Note:** Tasks 6-8 touch completely different files and CAN be executed in parallel.

---

### 6. Move NotificationActionReceiver from utils to services

The NotificationActionReceiver is a BroadcastReceiver that handles notification actions. It belongs in the `services/` package alongside BroadcastService and BroadcastServiceEntryPoint for logical grouping of broadcast-related infrastructure.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=19 path=app/src/main/java/org/ole/planet/myplanet/utils/NotificationActionReceiver.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/NotificationActionReceiver.kt#L1-L19"}

:::task-stub{title="Move NotificationActionReceiver to services package"}
1. Move `utils/NotificationActionReceiver.kt` to `services/NotificationActionReceiver.kt`
2. Update package declaration from `org.ole.planet.myplanet.utils` to `org.ole.planet.myplanet.services`
3. Update all import statements referencing `org.ole.planet.myplanet.utils.NotificationActionReceiver`
4. Update AndroidManifest.xml receiver declaration if present
:::

---

### 7. Move SharedPrefManager from utils to services

The SharedPrefManager handles application preferences and state management. This is a service-layer concern and should be grouped with other service classes like UserSessionManager and ConfigurationManager.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=26 path=app/src/main/java/org/ole/planet/myplanet/utils/SharedPrefManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/SharedPrefManager.kt#L1-L26"}

:::task-stub{title="Move SharedPrefManager to services package"}
1. Move `utils/SharedPrefManager.kt` to `services/SharedPrefManager.kt`
2. Update package declaration from `org.ole.planet.myplanet.utils` to `org.ole.planet.myplanet.services`
3. Update all import statements referencing `org.ole.planet.myplanet.utils.SharedPrefManager`
:::

---

### 8. Move ThemeManager from utils to services

The ThemeManager handles application theme state and configuration. Like SharedPrefManager, this is a service-layer concern managing application-wide settings.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=11 path=app/src/main/java/org/ole/planet/myplanet/utils/ThemeManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/ThemeManager.kt#L1-L11"}

:::task-stub{title="Move ThemeManager to services package"}
1. Move `utils/ThemeManager.kt` to `services/ThemeManager.kt`
2. Update package declaration from `org.ole.planet.myplanet.utils` to `org.ole.planet.myplanet.services`
3. Update all import statements referencing `org.ole.planet.myplanet.utils.ThemeManager`
:::

---

## Package Consolidation: Base Fragments

These tasks consolidate base fragment classes from `ui/base/` into the main `base/` package. Having base classes split across two locations (`base/` and `ui/base/`) creates confusion about where to find or add base functionality.

> **Note:** Tasks 9-11 touch different files but may have interdependencies. Execute sequentially or review carefully before parallel execution.

---

### 9. Move BaseDashboardFragment from ui/base to base

The BaseDashboardFragment is in `ui/base/` while other base fragments (BaseRecyclerFragment, BaseContainerFragment) are in `base/`. Consolidating all base classes improves discoverability and consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/base/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/base/BaseDashboardFragment.kt#L1-L30"}

:::task-stub{title="Move BaseDashboardFragment to base package"}
1. Move `ui/base/BaseDashboardFragment.kt` to `base/BaseDashboardFragment.kt`
2. Update package declaration from `org.ole.planet.myplanet.ui.base` to `org.ole.planet.myplanet.base`
3. Update all import statements referencing `org.ole.planet.myplanet.ui.base.BaseDashboardFragment`
4. Verify all fragments extending BaseDashboardFragment still compile correctly
:::

---

### 10. Move BaseTeamFragment from ui/base to base

The BaseTeamFragment is located in `ui/base/` but should be in the main `base/` package with other base fragment classes for consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/base/BaseTeamFragment.kt#L1-L30"}

:::task-stub{title="Move BaseTeamFragment to base package"}
1. Move `ui/base/BaseTeamFragment.kt` to `base/BaseTeamFragment.kt`
2. Update package declaration from `org.ole.planet.myplanet.ui.base` to `org.ole.planet.myplanet.base`
3. Update all import statements referencing `org.ole.planet.myplanet.ui.base.BaseTeamFragment`
4. Verify all fragments extending BaseTeamFragment still compile correctly
:::

---

### 11. Move BaseVoicesFragment from ui/base to base

The BaseVoicesFragment is in `ui/base/` and should be consolidated with other base fragments in the main `base/` package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/base/BaseVoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/base/BaseVoicesFragment.kt#L1-L30"}

:::task-stub{title="Move BaseVoicesFragment to base package"}
1. Move `ui/base/BaseVoicesFragment.kt` to `base/BaseVoicesFragment.kt`
2. Update package declaration from `org.ole.planet.myplanet.ui.base` to `org.ole.planet.myplanet.base`
3. Update all import statements referencing `org.ole.planet.myplanet.ui.base.BaseVoicesFragment`
4. Verify all fragments extending BaseVoicesFragment still compile correctly
5. After all three ui/base fragments are moved, delete the empty `ui/base/` directory
:::

---

## Package Reorganization: Manager Classes to Services

These tasks move manager classes from feature-specific UI packages to the `services/` package where they belong logically.

> **Note:** Tasks 12-13 touch completely different files and CAN be executed in parallel.

---

### 12. Move VoicesLabelManager from ui/voices to services

The VoicesLabelManager handles label operations and interacts with VoicesRepository. While it has UI-related methods, it primarily orchestrates data operations and belongs in `services/` rather than nested within a UI package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=23 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelManager.kt#L1-L23"}

:::task-stub{title="Move VoicesLabelManager to services package"}
1. Move `ui/voices/VoicesLabelManager.kt` to `services/VoicesLabelManager.kt`
2. Update package declaration from `org.ole.planet.myplanet.ui.voices` to `org.ole.planet.myplanet.services`
3. Update all import statements referencing `org.ole.planet.myplanet.ui.voices.VoicesLabelManager`
:::

---

### 13. Move ChallengeManager from ui/dashboard to services

The ChallengeManager handles challenge evaluation logic and repository interactions. This business logic should reside in `services/` rather than being coupled to the dashboard UI package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeManager.kt#L1-L31"}

:::task-stub{title="Move ChallengeManager to services package"}
1. Move `ui/dashboard/ChallengeManager.kt` to `services/ChallengeManager.kt`
2. Update package declaration from `org.ole.planet.myplanet.ui.dashboard` to `org.ole.planet.myplanet.services`
3. Update all import statements referencing `org.ole.planet.myplanet.ui.dashboard.ChallengeManager`
:::

---

## Additional Cleanup Tasks

---

### 14. Move NavigationHelper from utils to ui/navigation

The NavigationHelper provides UI navigation functionality and should be in a UI-related package rather than the generic `utils/` package. Creating a dedicated `ui/navigation/` package groups navigation-related utilities together.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=1 path=app/src/main/java/org/ole/planet/myplanet/utils/NavigationHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/NavigationHelper.kt#L1-L1"}

:::task-stub{title="Move NavigationHelper to ui/navigation package"}
1. Create new package `ui/navigation/` if it doesn't exist
2. Move `utils/NavigationHelper.kt` to `ui/navigation/NavigationHelper.kt`
3. Update package declaration from `org.ole.planet.myplanet.utils` to `org.ole.planet.myplanet.ui.navigation`
4. Update all import statements referencing `org.ole.planet.myplanet.utils.NavigationHelper`
:::

---

### 15. Rename SurveyAdapter to SurveysAdapter

The SurveyAdapter uses singular naming but resides in the `surveys/` (plural) directory. Other adapters like CoursesAdapter, TeamsAdapter, and ResourcesAdapter use plural naming matching their directory names. Renaming for consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyAdapter.kt#L1-L15"}

:::task-stub{title="Rename SurveyAdapter to SurveysAdapter"}
1. Rename `ui/surveys/SurveyAdapter.kt` to `ui/surveys/SurveysAdapter.kt`
2. Rename class `SurveyAdapter` to `SurveysAdapter` inside the file
3. Update all import statements referencing `SurveyAdapter`
4. Update all usages of `SurveyAdapter` class name across the codebase
:::

---

## Parallel Execution Guide

| Task Group | Tasks | Can Run in Parallel With |
|------------|-------|--------------------------|
| Repository Renames | 1, 2, 3, 4, 5 | Execute as batch (all touch RepositoryModule.kt) |
| Utils → Services Moves | 6, 7, 8 | Each other, and with groups below |
| Base Fragment Moves | 9, 10, 11 | Execute sequentially (inheritance chain) |
| Manager Moves | 12, 13 | Each other, and with Utils moves |
| Additional Cleanup | 14, 15 | Each other, and with all above |

### Recommended Execution Order

1. **Batch 1** (parallel): Tasks 6, 7, 8, 12, 13, 14, 15
2. **Batch 2** (sequential): Tasks 9, 10, 11 (due to inheritance dependencies)
3. **Batch 3** (sequential or batch): Tasks 1, 2, 3, 4, 5 (all modify RepositoryModule.kt)

---

## Summary

| Category | Count | Scope |
|----------|-------|-------|
| Repository Renames (singular → plural) | 5 | 10 files + DI module |
| Utils → Services Moves | 3 | 3 files |
| Base Fragment Consolidation | 3 | 3 files + directory cleanup |
| Manager Class Moves | 2 | 2 files |
| Additional Cleanup | 2 | 2 files |
| **Total** | **15** | ~22 files affected |
