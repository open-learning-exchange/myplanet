# Refactoring Plan: Structure & Naming Consistency

This document contains 15 granular refactoring tasks focused on file/class renaming and reorganization. All tasks can run in parallel without merge conflicts.

---

### 1. Rename ActivityRepository to ActivitiesRepository for plural consistency

Most repositories use plural naming (CoursesRepository, TeamsRepository, ResourcesRepository) but ActivityRepository uses singular form. Renaming to ActivitiesRepository aligns with the established convention.

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=11 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepository.kt#L6-L11"}

:codex-file-citation[codex-file-citation]{line_range_start=11 line_range_end=13 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepositoryImpl.kt#L11-L13"}

:::task-stub{title="Rename ActivityRepository to ActivitiesRepository"}
1. Rename ActivityRepository.kt to ActivitiesRepository.kt
2. Rename ActivityRepositoryImpl.kt to ActivitiesRepositoryImpl.kt
3. Update class names inside both files
4. Update RepositoryModule.kt bindings
5. Update all import statements referencing these classes
:::

---

### 2. Rename UserRepository to UsersRepository for plural consistency

UserRepository uses singular form while the convention is plural (TeamsRepository, CoursesRepository). Renaming ensures naming consistency across all repository interfaces.

:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L9-L14"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L1-L15"}

:::task-stub{title="Rename UserRepository to UsersRepository"}
1. Rename UserRepository.kt to UsersRepository.kt
2. Rename UserRepositoryImpl.kt to UsersRepositoryImpl.kt
3. Update class names inside both files
4. Update RepositoryModule.kt bindings
5. Update all import statements referencing these classes
:::

---

### 3. Rename FeedbackRepository to FeedbacksRepository for plural consistency

FeedbackRepository follows singular naming while most repositories use plural form. This creates an inconsistency in the repository layer naming convention.

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt#L8-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=15 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt#L15-L15"}

:::task-stub{title="Rename FeedbackRepository to FeedbacksRepository"}
1. Rename FeedbackRepository.kt to FeedbacksRepository.kt
2. Rename FeedbackRepositoryImpl.kt to FeedbacksRepositoryImpl.kt
3. Update class names inside both files
4. Update RepositoryModule.kt bindings
5. Update all import statements referencing these classes
:::

---

### 4. Rename LifeRepository to LivesRepository for plural consistency

LifeRepository uses singular form inconsistent with the plural convention used by most other repositories in the codebase (TeamsRepository, EventsRepository, etc.).

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt#L5-L8"}

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=7 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt#L7-L7"}

:::task-stub{title="Rename LifeRepository to LivesRepository"}
1. Rename LifeRepository.kt to LivesRepository.kt
2. Rename LifeRepositoryImpl.kt to LivesRepositoryImpl.kt
3. Update class names inside both files
4. Update RepositoryModule.kt bindings
5. Update all import statements referencing these classes
:::

---

### 5. Rename ProgressRepository to ProgressesRepository for plural consistency

ProgressRepository uses singular naming while the established convention is plural form for repository names across the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt#L7-L15"}

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L1-L15"}

:::task-stub{title="Rename ProgressRepository to ProgressesRepository"}
1. Rename ProgressRepository.kt to ProgressesRepository.kt
2. Rename ProgressRepositoryImpl.kt to ProgressesRepositoryImpl.kt
3. Update class names inside both files
4. Update RepositoryModule.kt bindings
5. Update all import statements referencing these classes
:::

---

### 6. Rename ChatRepository to ChatsRepository for plural consistency

ChatRepository uses singular form which is inconsistent with the plural naming convention used by most other repositories like CoursesRepository, TeamsRepository.

:codex-file-citation[codex-file-citation]{line_range_start=7 line_range_end=13 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt#L7-L13"}

:::task-stub{title="Rename ChatRepository to ChatsRepository"}
1. Rename ChatRepository.kt to ChatsRepository.kt
2. Rename ChatRepositoryImpl.kt to ChatsRepositoryImpl.kt
3. Update class names inside both files
4. Update RepositoryModule.kt bindings
5. Update all import statements referencing these classes
:::

---

### 7. Rename RealtimeSyncListener to OnRealtimeSyncListener for On-prefix consistency

Callback listeners in the codebase follow an "On" prefix convention (OnSyncListener, OnRatingChangeListener, OnFilterListener). RealtimeSyncListener breaks this pattern.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/callback/RealtimeSyncListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/RealtimeSyncListener.kt#L5-L12"}

:::task-stub{title="Rename RealtimeSyncListener to OnRealtimeSyncListener"}
1. Rename RealtimeSyncListener.kt to OnRealtimeSyncListener.kt
2. Update interface name inside the file
3. Update BaseRealtimeSyncListener to implement OnRealtimeSyncListener
4. Update all references in service/sync/ package
5. Update all import statements
:::

---

### 8. Rename BaseRealtimeSyncListener to BaseOnRealtimeSyncListener for consistency

After renaming RealtimeSyncListener, the base class should follow the same naming pattern to maintain consistency in the callback hierarchy.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=17 path=app/src/main/java/org/ole/planet/myplanet/callback/BaseRealtimeSyncListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/BaseRealtimeSyncListener.kt#L5-L17"}

:::task-stub{title="Rename BaseRealtimeSyncListener to BaseOnRealtimeSyncListener"}
1. Rename BaseRealtimeSyncListener.kt to BaseOnRealtimeSyncListener.kt
2. Update class name inside the file
3. Update implements clause to reference OnRealtimeSyncListener
4. Update all classes extending this base class
5. Update all import statements
:::

---

### 9. Rename SecurityDataListener to OnSecurityDataListener for On-prefix consistency

SecurityDataListener lacks the "On" prefix used by other listeners like OnSyncListener and OnRatingChangeListener. Adding the prefix improves naming consistency.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/callback/SecurityDataListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/SecurityDataListener.kt#L3-L5"}

:::task-stub{title="Rename SecurityDataListener to OnSecurityDataListener"}
1. Rename SecurityDataListener.kt to OnSecurityDataListener.kt
2. Update interface name inside the file
3. Update all implementing classes
4. Update all import statements
:::

---

### 10. Rename SurveyAdoptListener to OnSurveyAdoptListener for On-prefix consistency

SurveyAdoptListener lacks the "On" prefix convention. This inconsistency should be fixed to align with OnSyncListener, OnFilterListener, and other callback interfaces.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/callback/SurveyAdoptListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/SurveyAdoptListener.kt#L3-L5"}

:::task-stub{title="Rename SurveyAdoptListener to OnSurveyAdoptListener"}
1. Rename SurveyAdoptListener.kt to OnSurveyAdoptListener.kt
2. Update interface name inside the file
3. Update all implementing classes
4. Update all import statements
:::

---

### 11. Rename TeamPageListener to OnTeamPageListener for On-prefix consistency

TeamPageListener lacks the "On" prefix that other callback interfaces use. Renaming maintains the established pattern for listener interfaces.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/callback/TeamPageListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/TeamPageListener.kt#L3-L5"}

:::task-stub{title="Rename TeamPageListener to OnTeamPageListener"}
1. Rename TeamPageListener.kt to OnTeamPageListener.kt
2. Update interface name inside the file
3. Update all implementing classes (likely in ui/teams/)
4. Update all import statements
:::

---

### 12. Rename TeamUpdateListener to OnTeamUpdateListener for On-prefix consistency

TeamUpdateListener misses the "On" prefix convention used by most other listeners in the callback package. This creates naming inconsistency.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/callback/TeamUpdateListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/TeamUpdateListener.kt#L3-L5"}

:::task-stub{title="Rename TeamUpdateListener to OnTeamUpdateListener"}
1. Rename TeamUpdateListener.kt to OnTeamUpdateListener.kt
2. Update interface name inside the file
3. Update all implementing classes
4. Update all import statements
:::

---

### 13. Rename ResourcesUpdateListener to OnResourcesUpdateListener for On-prefix consistency

ResourcesUpdateListener lacks the "On" prefix used by other callback interfaces. This inconsistency should be addressed for uniform naming.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=6 path=app/src/main/java/org/ole/planet/myplanet/callback/ResourcesUpdateListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/ResourcesUpdateListener.kt#L3-L6"}

:::task-stub{title="Rename ResourcesUpdateListener to OnResourcesUpdateListener"}
1. Rename ResourcesUpdateListener.kt to OnResourcesUpdateListener.kt
2. Update interface name inside the file
3. Update all implementing classes
4. Update all import statements
:::

---

### 14. Move ConfigurationManager from data/ to service/ package

ConfigurationManager is a manager class currently placed in the data/ package. Manager classes belong in service/ package where other managers like SyncManager and UploadManager reside.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/data/ConfigurationManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/data/ConfigurationManager.kt#L1-L20"}

:::task-stub{title="Move ConfigurationManager to service package"}
1. Move ConfigurationManager.kt from data/ to service/
2. Update package declaration to org.ole.planet.myplanet.service
3. Update all import statements referencing this class
4. Verify DI module bindings still work
:::

---

### 15. Move ItemTouchHelperViewHolder from callback/ to base/ package

ItemTouchHelperViewHolder is a ViewHolder interface placed in the callback/ package. ViewHolder interfaces should be in the base/ package with other base UI classes like BaseActivity.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/callback/ItemTouchHelperViewHolder.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/ItemTouchHelperViewHolder.kt#L5-L8"}

:::task-stub{title="Move ItemTouchHelperViewHolder to base package"}
1. Move ItemTouchHelperViewHolder.kt from callback/ to base/
2. Update package declaration to org.ole.planet.myplanet.base
3. Update all import statements referencing this interface
4. Update any ViewHolder classes implementing this interface
:::

---

## Summary

| Category | Tasks | Files Affected |
|----------|-------|----------------|
| Repository Plural Naming | 6 | 12 files (interface + impl pairs) |
| Listener On-Prefix | 7 | 7 files |
| Package Reorganization | 2 | 2 files |
| **Total** | **15** | **21 files** |

All tasks are independent and can be executed in parallel without merge conflicts since each task modifies a distinct set of files.
