# Refactoring Tasks Report

## Repository Naming Consistency (Singular → Plural)

### 1. Rename UserRepository to UsersRepository

The repository uses singular naming (`UserRepository`) while the codebase convention uses plural naming for repositories that manage collections (e.g., `CoursesRepository`, `TeamsRepository`). Standardizing to plural naming improves consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L1-L67"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L1-L50"}

:::task-stub{title="Rename UserRepository to UsersRepository"}
1. Rename `UserRepository.kt` to `UsersRepository.kt`
2. Rename `UserRepositoryImpl.kt` to `UsersRepositoryImpl.kt`
3. Update interface name from `UserRepository` to `UsersRepository`
4. Update class name from `UserRepositoryImpl` to `UsersRepositoryImpl`
5. Update all references in DI modules (`RepositoryModule.kt`)
6. Update all injection sites across the codebase
:::

### 2. Rename ChatRepository to ChatsRepository

The repository uses singular naming (`ChatRepository`) inconsistent with the plural convention used by other repositories managing collections.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=13 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt#L1-L13"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt#L1-L50"}

:::task-stub{title="Rename ChatRepository to ChatsRepository"}
1. Rename `ChatRepository.kt` to `ChatsRepository.kt`
2. Rename `ChatRepositoryImpl.kt` to `ChatsRepositoryImpl.kt`
3. Update interface name from `ChatRepository` to `ChatsRepository`
4. Update class name from `ChatRepositoryImpl` to `ChatsRepositoryImpl`
5. Update all references in DI modules
6. Update all injection sites across the codebase
:::

### 3. Rename ActivityRepository to ActivitiesRepository

The repository uses singular naming (`ActivityRepository`) inconsistent with the established plural convention.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepository.kt#L1-L9"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepositoryImpl.kt#L1-L50"}

:::task-stub{title="Rename ActivityRepository to ActivitiesRepository"}
1. Rename `ActivityRepository.kt` to `ActivitiesRepository.kt`
2. Rename `ActivityRepositoryImpl.kt` to `ActivitiesRepositoryImpl.kt`
3. Update interface name from `ActivityRepository` to `ActivitiesRepository`
4. Update class name from `ActivityRepositoryImpl` to `ActivitiesRepositoryImpl`
5. Update all references in DI modules
6. Update all injection sites across the codebase
:::

### 4. Rename FeedbackRepository to FeedbacksRepository

The repository uses singular naming (`FeedbackRepository`) while managing a collection of feedback items.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt#L1-L22"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt#L1-L50"}

:::task-stub{title="Rename FeedbackRepository to FeedbacksRepository"}
1. Rename `FeedbackRepository.kt` to `FeedbacksRepository.kt`
2. Rename `FeedbackRepositoryImpl.kt` to `FeedbacksRepositoryImpl.kt`
3. Update interface name from `FeedbackRepository` to `FeedbacksRepository`
4. Update class name from `FeedbackRepositoryImpl` to `FeedbacksRepositoryImpl`
5. Update all references in DI modules
6. Update all injection sites across the codebase
:::

### 5. Rename ProgressRepository to ProgressesRepository

The repository uses singular naming (`ProgressRepository`) inconsistent with the plural convention.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=7 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt#L1-L7"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L1-L50"}

:::task-stub{title="Rename ProgressRepository to ProgressesRepository"}
1. Rename `ProgressRepository.kt` to `ProgressesRepository.kt`
2. Rename `ProgressRepositoryImpl.kt` to `ProgressesRepositoryImpl.kt`
3. Update interface name from `ProgressRepository` to `ProgressesRepository`
4. Update class name from `ProgressRepositoryImpl` to `ProgressesRepositoryImpl`
5. Update all references in DI modules
6. Update all injection sites across the codebase
:::

### 6. Rename LifeRepository to LivesRepository

The repository uses singular naming (`LifeRepository`) for managing `RealmMyLife` collection items.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt#L1-L8"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L1-L50"}

:::task-stub{title="Rename LifeRepository to LivesRepository"}
1. Rename `LifeRepository.kt` to `LivesRepository.kt`
2. Rename `LifeRepositoryImpl.kt` to `LivesRepositoryImpl.kt`
3. Update interface name from `LifeRepository` to `LivesRepository`
4. Update class name from `LifeRepositoryImpl` to `LivesRepositoryImpl`
5. Update all references in DI modules
6. Update all injection sites across the codebase
:::

## Callback Naming Consistency (Remove Duplicate Interfaces)

### 7. Consolidate duplicate TagClickListener interfaces

Two nearly identical interfaces exist: `OnTagClickListener` and `TagClickListener`. This creates confusion and violates DRY principle. Consolidate into a single interface.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=11 path=app/src/main/java/org/ole/planet/myplanet/callback/OnTagClickListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnTagClickListener.kt#L1-L11"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/callback/TagClickListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/TagClickListener.kt#L1-L10"}

:::task-stub{title="Consolidate duplicate TagClickListener interfaces"}
1. Analyze usage of both `OnTagClickListener` and `TagClickListener`
2. Choose the more comprehensive interface (likely `OnTagClickListener`)
3. Migrate all usages of the other interface to the chosen one
4. Delete the deprecated interface file
5. Update all implementing classes
:::

### 8. Consolidate duplicate SurveyAdoptListener interfaces

Two interfaces exist for survey adoption: `OnSurveyAdoptListener` and `SurveyAdoptListener`. These should be consolidated into a single interface.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/callback/OnSurveyAdoptListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnSurveyAdoptListener.kt#L1-L5"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/callback/SurveyAdoptListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/SurveyAdoptListener.kt#L1-L5"}

:::task-stub{title="Consolidate duplicate SurveyAdoptListener interfaces"}
1. Analyze usage of both `OnSurveyAdoptListener` and `SurveyAdoptListener`
2. Choose one naming convention (preferably without "On" prefix for consistency)
3. Migrate all usages to the chosen interface
4. Delete the deprecated interface file
5. Update all implementing classes
:::

## Legacy Naming Pattern Refactoring

### 9. Rename UserProfileDbHandler to UserProfileService

The class uses legacy "DbHandler" naming pattern which is inconsistent with modern service-oriented naming. It should be renamed to follow the `*Service` convention.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/service/UserProfileDbHandler.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/UserProfileDbHandler.kt#L1-L50"}

:::task-stub{title="Rename UserProfileDbHandler to UserProfileService"}
1. Rename file from `UserProfileDbHandler.kt` to `UserProfileService.kt`
2. Rename class from `UserProfileDbHandler` to `UserProfileService`
3. Update all constructor injection references
4. Update all manual instantiation sites
5. Update DI entry points if applicable
:::

## Model Package Organization (Move DTOs to data/dto)

### 10. Move ChatMessage DTO from model to data/dto

`ChatMessage` is a data class used for UI display, not a Realm entity. It should be moved to `data/dto/` package for better organization.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/model/ChatMessage.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/ChatMessage.kt#L1-L15"}

:::task-stub{title="Move ChatMessage DTO to data/dto package"}
1. Create `data/dto/` directory if it doesn't exist
2. Move `ChatMessage.kt` from `model/` to `data/dto/`
3. Update package declaration to `org.ole.planet.myplanet.data.dto`
4. Update all import statements across the codebase
:::

### 11. Move ChatRequest DTOs from model to data/dto

`ChatRequest`, `ContentData`, `ContinueChatRequest`, `Data`, and `AiProvider` are API request DTOs, not Realm entities. They should be in `data/dto/` for proper separation.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=32 path=app/src/main/java/org/ole/planet/myplanet/model/ChatRequest.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/ChatRequest.kt#L1-L32"}

:::task-stub{title="Move ChatRequest DTOs to data/dto package"}
1. Create `data/dto/` directory if it doesn't exist
2. Move `ChatRequest.kt` from `model/` to `data/dto/`
3. Update package declaration to `org.ole.planet.myplanet.data.dto`
4. Update all import statements across the codebase
:::

### 12. Move User DTO from model to data/dto

`User` is a simple data class for authentication, not a Realm entity. It belongs in the `data/dto/` or `data/auth/` package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/model/User.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/User.kt#L1-L9"}

:::task-stub{title="Move User DTO to data/dto package"}
1. Create `data/dto/` directory if it doesn't exist
2. Move `User.kt` from `model/` to `data/dto/`
3. Update package declaration to `org.ole.planet.myplanet.data.dto`
4. Update all import statements across the codebase
:::

### 13. Move Download DTO from model to data/dto

`Download` is a Parcelable data transfer class for download progress, not a Realm entity. It should be in `data/dto/`.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/model/Download.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/Download.kt#L1-L51"}

:::task-stub{title="Move Download DTO to data/dto package"}
1. Create `data/dto/` directory if it doesn't exist
2. Move `Download.kt` from `model/` to `data/dto/`
3. Update package declaration to `org.ole.planet.myplanet.data.dto`
4. Update all import statements across the codebase
:::

### 14. Move ServerAddress DTO from model to data/dto

`ServerAddress` is a simple data class for UI display, not a Realm entity. It should be moved to `data/dto/`.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=3 path=app/src/main/java/org/ole/planet/myplanet/model/ServerAddress.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/ServerAddress.kt#L1-L3"}

:::task-stub{title="Move ServerAddress DTO to data/dto package"}
1. Create `data/dto/` directory if it doesn't exist
2. Move `ServerAddress.kt` from `model/` to `data/dto/`
3. Update package declaration to `org.ole.planet.myplanet.data.dto`
4. Update all import statements across the codebase
:::

### 15. Move TeamDetails DTOs from model to data/dto

`TeamStatus` and `TeamDetails` are data classes for UI display, not Realm entities. Note: `TeamMemberStatus` in `TeamsRepository.kt` duplicates `TeamStatus` - these should be consolidated.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/model/TeamDetails.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/TeamDetails.kt#L1-L22"}
:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=24 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L20-L24"}

:::task-stub{title="Move TeamDetails DTOs to data/dto package"}
1. Create `data/dto/` directory if it doesn't exist
2. Move `TeamDetails.kt` from `model/` to `data/dto/`
3. Update package declaration to `org.ole.planet.myplanet.data.dto`
4. Consolidate duplicate `TeamStatus` and `TeamMemberStatus` classes
5. Update all import statements across the codebase
:::

---

## Summary

| Category | Count | Tasks |
|----------|-------|-------|
| Repository Naming (Singular → Plural) | 6 | Tasks 1-6 |
| Callback Consolidation | 2 | Tasks 7-8 |
| Legacy Naming Refactoring | 1 | Task 9 |
| Model → DTO Package Move | 6 | Tasks 10-15 |

**Total: 15 tasks**

### Parallelization Notes

The following task groups can be executed in parallel without merge conflicts:

- **Group A** (Repository renames): Tasks 1, 2, 3, 4, 5, 6 - each touches different repository files
- **Group B** (Callback consolidation): Tasks 7, 8 - each touches different callback files
- **Group C** (DTO moves): Tasks 10, 11, 12, 13, 14, 15 - each moves different model files
- **Task 9** (UserProfileDbHandler rename) - independent of all other tasks
