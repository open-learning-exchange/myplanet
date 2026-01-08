# Refactoring Tasks Report

This document contains 15 granular refactoring opportunities focused on naming consistency, file reorganization, and package restructuring. All tasks are designed to run in parallel without merge conflicts.

---

### 1. Rename ViewHolderCourse to CourseViewHolder in CoursesAdapter

The ViewHolder class uses prefix naming `ViewHolderCourse` instead of the Android-standard suffix pattern `CourseViewHolder`. This inconsistency affects code readability and violates naming conventions used elsewhere in the codebase (e.g., `FeedbackViewHolder`, `FinanceViewHolder`).

:codex-file-citation[codex-file-citation]{line_range_start=487 line_range_end=488 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt#L487-L488"}

:::task-stub{title="Rename ViewHolderCourse to CourseViewHolder"}
1. Open CoursesAdapter.kt
2. Rename class `ViewHolderCourse` to `CourseViewHolder`
3. Update all references to this class within the file
4. Verify compilation succeeds
:::

---

### 2. Rename ViewHolderLibrary to ResourceViewHolder in ResourcesAdapter

The ViewHolder class uses prefix naming `ViewHolderLibrary` instead of suffix pattern. Additionally, the name "Library" is inconsistent with the adapter name "Resources", creating semantic confusion.

:codex-file-citation[codex-file-citation]{line_range_start=382 line_range_end=383 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L382-L383"}

:::task-stub{title="Rename ViewHolderLibrary to ResourceViewHolder"}
1. Open ResourcesAdapter.kt
2. Rename class `ViewHolderLibrary` to `ResourceViewHolder`
3. Update all references within the file
4. Verify compilation succeeds
:::

---

### 3. Rename ViewHolderTeam to TeamViewHolder in TeamListAdapter

The ViewHolder class uses prefix naming pattern instead of the standard suffix pattern used by well-structured adapters in this codebase.

:codex-file-citation[codex-file-citation]{line_range_start=218 line_range_end=218 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamListAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamListAdapter.kt#L218-L218"}

:::task-stub{title="Rename ViewHolderTeam to TeamViewHolder"}
1. Open TeamListAdapter.kt
2. Rename class `ViewHolderTeam` to `TeamViewHolder`
3. Update all references within the file
4. Verify compilation succeeds
:::

---

### 4. Rename ViewHolderEvent to EventViewHolder in EventsAdapter

The ViewHolder class uses prefix naming pattern instead of the Android-standard suffix pattern.

:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=34 path=app/src/main/java/org/ole/planet/myplanet/ui/events/EventsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/events/EventsAdapter.kt#L34-L34"}

:::task-stub{title="Rename ViewHolderEvent to EventViewHolder"}
1. Open EventsAdapter.kt
2. Rename class `ViewHolderEvent` to `EventViewHolder`
3. Update all references within the file
4. Verify compilation succeeds
:::

---

### 5. Rename ViewHolderNews to NewsViewHolder in VoicesAdapter

The ViewHolder class uses prefix naming pattern. This file is in the voices package but the ViewHolder is named "News", creating a semantic mismatch that should also be addressed.

:codex-file-citation[codex-file-citation]{line_range_start=780 line_range_end=780 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L780-L780"}

:::task-stub{title="Rename ViewHolderNews to VoiceViewHolder"}
1. Open VoicesAdapter.kt
2. Rename class `ViewHolderNews` to `VoiceViewHolder`
3. Update all references within the file
4. Verify compilation succeeds
:::

---

### 6. Rename ViewHolderSurvey to SurveyViewHolder in SurveyAdapter

The ViewHolder class uses prefix naming pattern instead of the standard suffix pattern.

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=84 path=app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyAdapter.kt#L84-L84"}

:::task-stub{title="Rename ViewHolderSurvey to SurveyViewHolder"}
1. Open SurveyAdapter.kt
2. Rename class `ViewHolderSurvey` to `SurveyViewHolder`
3. Update all references within the file
4. Verify compilation succeeds
:::

---

### 7. Rename ActivityRepository to ActivitiesRepository for plural consistency

The repository uses singular naming while most other repositories use plural naming (CoursesRepository, EventsRepository, ResourcesRepository). Standardizing to plural improves consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ActivityRepository.kt#L1-L10"}

:::task-stub{title="Rename ActivityRepository to ActivitiesRepository"}
1. Rename ActivityRepository.kt to ActivitiesRepository.kt
2. Rename interface `ActivityRepository` to `ActivitiesRepository`
3. Rename ActivityRepositoryImpl.kt to ActivitiesRepositoryImpl.kt
4. Rename class `ActivityRepositoryImpl` to `ActivitiesRepositoryImpl`
5. Update RepositoryModule.kt binding
6. Update all injection sites across the codebase
:::

---

### 8. Rename ChatRepository to ChatsRepository for plural consistency

The repository uses singular naming while most other repositories use plural naming. Standardizing to plural improves consistency across the repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt#L1-L10"}

:::task-stub{title="Rename ChatRepository to ChatsRepository"}
1. Rename ChatRepository.kt to ChatsRepository.kt
2. Rename interface `ChatRepository` to `ChatsRepository`
3. Rename ChatRepositoryImpl.kt to ChatsRepositoryImpl.kt
4. Rename class `ChatRepositoryImpl` to `ChatsRepositoryImpl`
5. Update RepositoryModule.kt binding
6. Update all injection sites across the codebase
:::

---

### 9. Rename FeedbackRepository to FeedbacksRepository for plural consistency

The repository uses singular naming while most other repositories use plural naming (SubmissionsRepository, SurveysRepository). Standardizing to plural improves consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt#L1-L10"}

:::task-stub{title="Rename FeedbackRepository to FeedbacksRepository"}
1. Rename FeedbackRepository.kt to FeedbacksRepository.kt
2. Rename interface `FeedbackRepository` to `FeedbacksRepository`
3. Rename FeedbackRepositoryImpl.kt to FeedbacksRepositoryImpl.kt
4. Rename class `FeedbackRepositoryImpl` to `FeedbacksRepositoryImpl`
5. Update RepositoryModule.kt binding
6. Update all injection sites across the codebase
:::

---

### 10. Rename UserRepository to UsersRepository for plural consistency

The repository uses singular naming while most other repositories use plural naming. Standardizing to plural improves consistency across the repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L1-L10"}

:::task-stub{title="Rename UserRepository to UsersRepository"}
1. Rename UserRepository.kt to UsersRepository.kt
2. Rename interface `UserRepository` to `UsersRepository`
3. Rename UserRepositoryImpl.kt to UsersRepositoryImpl.kt
4. Rename class `UserRepositoryImpl` to `UsersRepositoryImpl`
5. Update RepositoryModule.kt binding
6. Update all injection sites across the codebase
:::

---

### 11. Move SimpleItemTouchHelperCallback from callback to utilities package

The SimpleItemTouchHelperCallback is a concrete implementation class, not a callback interface definition. It extends ItemTouchHelper.Callback and contains implementation logic. Moving it to utilities aligns with its actual purpose.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/callback/SimpleItemTouchHelperCallback.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/SimpleItemTouchHelperCallback.kt#L1-L66"}

:::task-stub{title="Move SimpleItemTouchHelperCallback to utilities"}
1. Move SimpleItemTouchHelperCallback.kt to utilities package
2. Update package declaration to org.ole.planet.myplanet.utilities
3. Update all import statements across the codebase
4. Verify compilation succeeds
:::

---

### 12. Split RealtimeSyncListener into separate files

The RealtimeSyncListener.kt file contains three separate concerns: a data class (TableDataUpdate), an interface (RealtimeSyncListener), and an abstract class (BaseRealtimeSyncListener). This violates single responsibility and should be split.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=44 path=app/src/main/java/org/ole/planet/myplanet/callback/RealtimeSyncListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/RealtimeSyncListener.kt#L1-L44"}

:::task-stub{title="Split RealtimeSyncListener into separate files"}
1. Extract TableDataUpdate data class to model/TableDataUpdate.kt
2. Keep RealtimeSyncListener interface in callback/RealtimeSyncListener.kt
3. Extract BaseRealtimeSyncListener to callback/BaseRealtimeSyncListener.kt
4. Update all import statements across the codebase
5. Verify compilation succeeds
:::

---

### 13. Rename ViewHolderLife to LifeViewHolder in LifeAdapter

The ViewHolder class uses prefix naming pattern instead of the Android-standard suffix pattern used by well-structured adapters.

:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=123 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt#L123-L123"}

:::task-stub{title="Rename ViewHolderLife to LifeViewHolder"}
1. Open LifeAdapter.kt
2. Rename class `ViewHolderLife` to `LifeViewHolder`
3. Update all references within the file
4. Verify compilation succeeds
:::

---

### 14. Rename ViewHolderNotifications to NotificationViewHolder in NotificationsAdapter

The ViewHolder class uses prefix naming pattern and plural form. Standard convention uses singular form with suffix pattern.

:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=48 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/notifications/NotificationsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/notifications/NotificationsAdapter.kt#L47-L48"}

:::task-stub{title="Rename ViewHolderNotifications to NotificationViewHolder"}
1. Open NotificationsAdapter.kt
2. Rename class `ViewHolderNotifications` to `NotificationViewHolder`
3. Update all references within the file
4. Verify compilation succeeds
:::

---

### 15. Rename ViewHolderPersonals to PersonalViewHolder in PersonalsAdapter

The ViewHolder class uses prefix naming pattern and plural form. Standard convention uses singular form with suffix pattern.

:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt#L95-L95"}

:::task-stub{title="Rename ViewHolderPersonals to PersonalViewHolder"}
1. Open PersonalsAdapter.kt
2. Rename class `ViewHolderPersonals` to `PersonalViewHolder`
3. Update all references within the file
4. Verify compilation succeeds
:::

---

## Summary

| Category | Count | Tasks |
|----------|-------|-------|
| ViewHolder Renaming (prefix → suffix) | 9 | #1-6, #13-15 |
| Repository Renaming (singular → plural) | 4 | #7-10 |
| File Reorganization | 2 | #11-12 |

**Parallel Execution Notes:**
- Tasks #1-6, #13-15 (ViewHolder renames) can all run in parallel - each modifies a different adapter file
- Tasks #7-10 (Repository renames) should run sequentially due to shared RepositoryModule.kt updates
- Tasks #11-12 (File moves) can run in parallel with ViewHolder tasks

**Merge Conflict Risk:** Low - each task targets distinct files with minimal overlap
