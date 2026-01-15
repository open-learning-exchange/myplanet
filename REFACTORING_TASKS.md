# Refactoring Tasks Report

Generated: 2026-01-15

This document contains 15 granular refactoring tasks focused on naming consistency and structural improvements. Tasks are designed to run in parallel without merge conflicts.

---

### 1. Rename ChatRepository to ChatsRepository for plural consistency

The repository uses singular naming while 13 other repositories use plural naming (e.g., CoursesRepository, TeamsRepository, ResourcesRepository). Standardizing to plural naming improves consistency across the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=13 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepository.kt#L1-L13"}

:::task-stub{title="Rename ChatRepository → ChatsRepository"}
1. Rename ChatRepository.kt to ChatsRepository.kt
2. Rename interface ChatRepository to ChatsRepository
3. Rename ChatRepositoryImpl.kt to ChatsRepositoryImpl.kt
4. Rename class ChatRepositoryImpl to ChatsRepositoryImpl
5. Update RepositoryModule.kt binding references
6. Update all injection sites and usages across codebase
:::

---

### 2. Rename FeedbackRepository to FeedbacksRepository for plural consistency

The repository uses singular naming inconsistent with the majority pattern. Other similar repositories like SubmissionsRepository and NotificationsRepository use plural naming.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepository.kt#L1-L22"}

:::task-stub{title="Rename FeedbackRepository → FeedbacksRepository"}
1. Rename FeedbackRepository.kt to FeedbacksRepository.kt
2. Rename interface FeedbackRepository to FeedbacksRepository
3. Rename FeedbackRepositoryImpl.kt to FeedbacksRepositoryImpl.kt
4. Rename class FeedbackRepositoryImpl to FeedbacksRepositoryImpl
5. Update RepositoryModule.kt binding references
6. Update all injection sites and usages across codebase
:::

---

### 3. Rename UserRepository to UsersRepository for plural consistency

The repository uses singular naming while the pattern across 13 other repositories is plural. UsersRepository would match TeamsRepository, CoursesRepository, etc.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L1-L67"}

:::task-stub{title="Rename UserRepository → UsersRepository"}
1. Rename UserRepository.kt to UsersRepository.kt
2. Rename interface UserRepository to UsersRepository
3. Rename UserRepositoryImpl.kt to UsersRepositoryImpl.kt
4. Rename class UserRepositoryImpl to UsersRepositoryImpl
5. Update RepositoryModule.kt binding references
6. Update all injection sites and usages across codebase
:::

---

### 4. Rename LifeRepository to LivesRepository for plural consistency

The repository uses singular naming inconsistent with the majority plural pattern. LivesRepository would match the convention used by ResourcesRepository, ActivitiesRepository, etc.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt#L1-L8"}

:::task-stub{title="Rename LifeRepository → LivesRepository"}
1. Rename LifeRepository.kt to LivesRepository.kt
2. Rename interface LifeRepository to LivesRepository
3. Rename LifeRepositoryImpl.kt to LivesRepositoryImpl.kt
4. Rename class LifeRepositoryImpl to LivesRepositoryImpl
5. Update RepositoryModule.kt binding references
6. Update all injection sites and usages across codebase
:::

---

### 5. Rename ProgressRepository to ProgressesRepository for plural consistency

The repository uses singular naming while 13 other repositories follow plural convention. This change standardizes naming across the repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepository.kt#L1-L22"}

:::task-stub{title="Rename ProgressRepository → ProgressesRepository"}
1. Rename ProgressRepository.kt to ProgressesRepository.kt
2. Rename interface ProgressRepository to ProgressesRepository
3. Rename ProgressRepositoryImpl.kt to ProgressesRepositoryImpl.kt
4. Rename class ProgressRepositoryImpl to ProgressesRepositoryImpl
5. Update RepositoryModule.kt binding references
6. Update all injection sites and usages across codebase
:::

---

### 6. Rename ViewHolderMySurvey to SubmissionsViewHolder for suffix pattern consistency

The ViewHolder class uses prefix pattern (ViewHolderX) while the majority of ViewHolders use suffix pattern (XViewHolder). Additionally, the name should match the adapter name (SubmissionsAdapter).

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt#L19-L30"}

:::task-stub{title="Rename ViewHolderMySurvey → SubmissionsViewHolder"}
1. Rename inner class ViewHolderMySurvey to SubmissionsViewHolder in SubmissionsAdapter.kt
2. Update ListAdapter type parameter reference
3. Update all usages within the adapter file
:::

---

### 7. Rename ViewHolderMyProgress to CoursesProgressViewHolder for suffix pattern consistency

The ViewHolder uses prefix naming pattern inconsistent with majority suffix pattern (XViewHolder). Name should reflect the adapter name (CoursesProgressAdapter).

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressAdapter.kt#L19-L30"}

:::task-stub{title="Rename ViewHolderMyProgress → CoursesProgressViewHolder"}
1. Rename inner class ViewHolderMyProgress to CoursesProgressViewHolder in CoursesProgressAdapter.kt
2. Update RecyclerView.ViewHolder type references
3. Update all usages within the adapter file
:::

---

### 8. Rename ViewHolderOtherInfo to ReferencesViewHolder for suffix pattern consistency

The ViewHolder uses prefix pattern and unrelated naming (OtherInfo). Should follow suffix pattern and match adapter name (ReferencesAdapter).

:codex-file-citation[codex-file-citation]{line_range_start=10 line_range_end=27 path=app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesAdapter.kt#L10-L27"}

:::task-stub{title="Rename ViewHolderOtherInfo → ReferencesViewHolder"}
1. Rename inner class ViewHolderOtherInfo to ReferencesViewHolder in ReferencesAdapter.kt
2. Update import statement at line 10
3. Update ListAdapter type parameter reference
4. Update all usages within the adapter file
:::

---

### 9. Rename ViewHolderUser to MembersViewHolder for suffix pattern consistency

The ViewHolder uses prefix pattern (ViewHolderUser) while most ViewHolders use suffix pattern. Name should match adapter name (MembersAdapter).

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt#L26-L30"}

:::task-stub{title="Rename ViewHolderUser → MembersViewHolder"}
1. Rename inner class ViewHolderUser to MembersViewHolder in MembersAdapter.kt
2. Update ListAdapter type parameter reference at line 30
3. Update all usages within the adapter file
:::

---

### 10. Rename ViewHolderTask to TeamTaskViewHolder for suffix pattern consistency

The ViewHolder uses prefix pattern (ViewHolderTask) inconsistent with majority suffix pattern. Name should match adapter name (TeamTaskAdapter).

:codex-file-citation[codex-file-citation]{line_range_start=21 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamTaskAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamTaskAdapter.kt#L21-L30"}

:::task-stub{title="Rename ViewHolderTask → TeamTaskViewHolder"}
1. Rename inner class ViewHolderTask to TeamTaskViewHolder in TeamTaskAdapter.kt
2. Update import statement at line 21
3. Update ListAdapter type parameter reference at line 30
4. Update all usages within the adapter file
:::

---

### 11. Rename ViewHolderLeader to CommunityLeadersViewHolder for suffix pattern consistency

The ViewHolder uses inconsistent prefix pattern (ViewHolderLeader). Should follow suffix pattern and match adapter name (CommunityLeadersAdapter).

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityLeadersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityLeadersAdapter.kt#L18-L30"}

:::task-stub{title="Rename ViewHolderLeader → CommunityLeadersViewHolder"}
1. Rename inner class ViewHolderLeader to CommunityLeadersViewHolder in CommunityLeadersAdapter.kt
2. Update ListAdapter type parameter reference at line 21
3. Update all usages within the adapter file
:::

---

### 12. Rename ViewHolderHealthExamination to HealthExaminationViewHolder for suffix pattern consistency

The ViewHolder uses prefix pattern (ViewHolderHealthExamination) inconsistent with majority suffix pattern (XViewHolder).

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt#L22-L30"}

:::task-stub{title="Rename ViewHolderHealthExamination → HealthExaminationViewHolder"}
1. Rename inner class ViewHolderHealthExamination to HealthExaminationViewHolder in HealthExaminationAdapter.kt
2. Update import statement at line 22
3. Update all usages within the adapter file
:::

---

### 13. Rename DiffRefreshableCallback to OnDiffRefreshListener for callback naming consistency

The interface uses "Callback" suffix while 25 other interfaces in the callback package use "Listener" suffix (e.g., OnSyncListener, OnFilterListener, OnTagClickListener).

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/callback/DiffRefreshableCallback.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/DiffRefreshableCallback.kt#L1-L5"}

:::task-stub{title="Rename DiffRefreshableCallback → OnDiffRefreshListener"}
1. Rename DiffRefreshableCallback.kt to OnDiffRefreshListener.kt
2. Rename interface DiffRefreshableCallback to OnDiffRefreshListener
3. Update all implementations and usages across codebase
:::

---

### 14. Rename ItemTouchHelperViewHolder to OnItemDragStateListener for callback naming consistency

The interface is named as a ViewHolder but is actually a callback interface for drag state events. The "ViewHolder" suffix is misleading since it's in the callback package alongside other listener interfaces.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/callback/ItemTouchHelperViewHolder.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/ItemTouchHelperViewHolder.kt#L1-L8"}

:::task-stub{title="Rename ItemTouchHelperViewHolder → OnItemDragStateListener"}
1. Rename ItemTouchHelperViewHolder.kt to OnItemDragStateListener.kt
2. Rename interface ItemTouchHelperViewHolder to OnItemDragStateListener
3. Update all implementations and usages across codebase
:::

---

### 15. Rename RealtimeSyncCoordinator to RealtimeSyncManager for service naming consistency

The class uses "Coordinator" suffix while 8 other similar classes in the service layer use "Manager" suffix (e.g., SyncManager, UploadManager, ConfigurationManager, UserSessionManager).

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/service/sync/RealtimeSyncCoordinator.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/sync/RealtimeSyncCoordinator.kt#L1-L20"}

:::task-stub{title="Rename RealtimeSyncCoordinator → RealtimeSyncManager"}
1. Rename RealtimeSyncCoordinator.kt to RealtimeSyncManager.kt
2. Rename class RealtimeSyncCoordinator to RealtimeSyncManager
3. Update companion object getInstance() references
4. Update all usages across codebase
:::

---

## Summary

| Category | Count | Tasks |
|----------|-------|-------|
| Repository Naming (singular → plural) | 5 | #1-#5 |
| ViewHolder Naming (prefix → suffix) | 7 | #6-#12 |
| Callback Naming (consistency) | 2 | #13-#14 |
| Service Naming (consistency) | 1 | #15 |
| **Total** | **15** | |

### Parallel Execution Groups

These tasks can be executed in parallel without merge conflicts:

**Group A (Repositories):** Tasks #1, #2, #3, #4, #5
**Group B (ViewHolders):** Tasks #6, #7, #8, #9, #10, #11, #12
**Group C (Callbacks):** Tasks #13, #14
**Group D (Services):** Task #15

All groups can run simultaneously as they touch different files and packages.
