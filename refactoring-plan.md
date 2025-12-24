# myPlanet Refactoring Plan: Structure & Naming Improvements

This document contains 10 granular refactoring tasks focused on naming consistency, file organization, and structural improvements. All tasks are cosmetic/structural changes that can be executed in parallel without merge conflicts.

---

### Rename AdapterMeetup to MeetupAdapter

The class uses prefix naming pattern (AdapterX) instead of the standard Android suffix pattern (XAdapter), which is inconsistent with RecyclerView.Adapter naming conventions used throughout the Android ecosystem.

:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=13 path=app/src/main/java/org/ole/planet/myplanet/ui/mymeetup/AdapterMeetup.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/ui/mymeetup/AdapterMeetup.kt#L13-L13"}

:::task-stub{title="Rename AdapterMeetup to MeetupAdapter"}
1. Rename class AdapterMeetup to MeetupAdapter in app/src/main/java/org/ole/planet/myplanet/ui/mymeetup/AdapterMeetup.kt
2. Rename file AdapterMeetup.kt to MeetupAdapter.kt
3. Update all import statements and references across the codebase
:::

---

### Rename AdapterFeedback to FeedbackAdapter

The class uses prefix naming pattern (AdapterX) instead of the standard Android suffix pattern (XAdapter), causing inconsistency with best practices for RecyclerView adapters.

:codex-file-citation[codex-file-citation]{line_range_start=16 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/AdapterFeedback.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/ui/feedback/AdapterFeedback.kt#L16-L16"}

:::task-stub{title="Rename AdapterFeedback to FeedbackAdapter"}
1. Rename class AdapterFeedback to FeedbackAdapter in app/src/main/java/org/ole/planet/myplanet/ui/feedback/AdapterFeedback.kt
2. Rename file AdapterFeedback.kt to FeedbackAdapter.kt
3. Update all import statements and references across the codebase
:::

---

### Rename AdapterNotification to NotificationAdapter

The class uses prefix naming pattern (AdapterX) instead of the standard Android suffix pattern (XAdapter), which conflicts with Android framework conventions.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/notification/AdapterNotification.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/notification/AdapterNotification.kt#L17-L22"}

:::task-stub{title="Rename AdapterNotification to NotificationAdapter"}
1. Rename class AdapterNotification to NotificationAdapter in app/src/main/java/org/ole/planet/myplanet/ui/dashboard/notification/AdapterNotification.kt
2. Rename file AdapterNotification.kt to NotificationAdapter.kt
3. Update all import statements and references across the codebase
:::

---

### Rename AdapterMySubmission to MySubmissionAdapter

The class uses prefix naming pattern (AdapterX) instead of the standard Android suffix pattern (XAdapter), deviating from RecyclerView.Adapter naming conventions.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=21 path=app/src/main/java/org/ole/planet/myplanet/ui/submission/AdapterMySubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/ui/submission/AdapterMySubmission.kt#L19-L21"}

:::task-stub{title="Rename AdapterMySubmission to MySubmissionAdapter"}
1. Rename class AdapterMySubmission to MySubmissionAdapter in app/src/main/java/org/ole/planet/myplanet/ui/submission/AdapterMySubmission.kt
2. Rename file AdapterMySubmission.kt to MySubmissionAdapter.kt
3. Update all import statements and references including the static method openSurvey
:::

---

### Rename AdapterSurvey to SurveyAdapter

The class uses prefix naming pattern (AdapterX) instead of the standard Android suffix pattern (XAdapter), which is inconsistent with Android framework naming conventions.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/ui/survey/AdapterSurvey.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/ui/survey/AdapterSurvey.kt#L28-L39"}

:::task-stub{title="Rename AdapterSurvey to SurveyAdapter"}
1. Rename class AdapterSurvey to SurveyAdapter in app/src/main/java/org/ole/planet/myplanet/ui/survey/AdapterSurvey.kt
2. Rename file AdapterSurvey.kt to SurveyAdapter.kt
3. Update all import statements and references across the codebase
:::

---

### Rename userprofile TeamListAdapter to TeamMembersAdapter

Two different adapters named TeamListAdapter exist in different packages with completely different purposes, creating naming collision. The userprofile version displays User models, not TeamData, so should be renamed to reflect its actual purpose.

:codex-file-citation[codex-file-citation]{line_range_start=14 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/ui/userprofile/TeamListAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/ui/userprofile/TeamListAdapter.kt#L14-L16"}

:::task-stub{title="Rename userprofile TeamListAdapter to TeamMembersAdapter"}
1. Rename class TeamListAdapter to TeamMembersAdapter in app/src/main/java/org/ole/planet/myplanet/ui/userprofile/TeamListAdapter.kt
2. Rename file TeamListAdapter.kt to TeamMembersAdapter.kt
3. Update all import statements and references in userprofile package
4. Ensure ui/team/TeamListAdapter.kt remains unchanged
:::

---

### Rename TeamsRepository to TeamRepository

Repository uses plural naming (TeamsRepository) while most other repositories use singular naming pattern, creating inconsistency across the repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=32 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L32-L32"}

:::task-stub{title="Rename TeamsRepository to TeamRepository"}
1. Rename interface TeamsRepository to TeamRepository in app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt
2. Rename file TeamsRepository.kt to TeamRepository.kt
3. Rename class TeamsRepositoryImpl to TeamRepositoryImpl in app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt
4. Rename file TeamsRepositoryImpl.kt to TeamRepositoryImpl.kt
5. Update all dependency injection bindings in RepositoryModule
6. Update all import statements and references across the codebase
:::

---

### Rename ResourcesRepository to ResourceRepository

Repository uses plural naming (ResourcesRepository) while most other repositories use singular naming pattern, creating inconsistency across the repository layer.

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=6 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt#L6-L6"}

:::task-stub{title="Rename ResourcesRepository to ResourceRepository"}
1. Rename interface ResourcesRepository to ResourceRepository in app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt
2. Rename file ResourcesRepository.kt to ResourceRepository.kt
3. Rename class ResourcesRepositoryImpl to ResourceRepositoryImpl in app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt
4. Rename file ResourcesRepositoryImpl.kt to ResourceRepositoryImpl.kt
5. Update all dependency injection bindings in RepositoryModule
6. Update all import statements and references across the codebase
:::

---

### Move NotificationListener to callback package

Interface listener is located in ui/dashboard/notification package instead of the centralized callback package, making it harder to discover and reuse across different features.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/notification/NotificationListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/notification/NotificationListener.kt#L3-L5"}

:::task-stub{title="Move NotificationListener to callback package"}
1. Move file NotificationListener.kt from app/src/main/java/org/ole/planet/myplanet/ui/dashboard/notification/ to app/src/main/java/org/ole/planet/myplanet/callback/
2. Update package declaration from org.ole.planet.myplanet.ui.dashboard.notification to org.ole.planet.myplanet.callback
3. Update all import statements across the codebase to use new package path
:::

---

### Move ExamAnswerUtils to utilities package

Utility object is embedded in ui/exam feature package instead of the centralized utilities package, reducing discoverability and preventing reuse across different features that might need answer validation logic.

:codex-file-citation[codex-file-citation]{line_range_start=8 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamAnswerUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-structure-naming-WNysh/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamAnswerUtils.kt#L8-L8"}

:::task-stub{title="Move ExamAnswerUtils to utilities package"}
1. Move file ExamAnswerUtils.kt from app/src/main/java/org/ole/planet/myplanet/ui/exam/ to app/src/main/java/org/ole/planet/myplanet/utilities/
2. Update package declaration from org.ole.planet.myplanet.ui.exam to org.ole.planet.myplanet.utilities
3. Update all import statements across the codebase to use new package path
:::

---

## Summary

**Total Tasks**: 10
**Total Files Affected**: ~15-20 files (including implementations and usages)
**Conflict Risk**: Low (all tasks are independent)
**Implementation Type**: Pure renames and moves (no logic changes)

### Task Categories:
- **Adapter Renames** (Tasks 1-5): 5 tasks converting prefix to suffix naming
- **Duplicate Resolution** (Task 6): 1 task resolving naming collision
- **Repository Renames** (Tasks 7-8): 2 tasks standardizing singular naming
- **Package Moves** (Tasks 9-10): 2 tasks consolidating files to proper packages

### Parallelization Strategy:
All 10 tasks can be executed in parallel without merge conflicts as they operate on:
- Different files (Tasks 1-6, 9-10)
- Different repository pairs (Tasks 7-8)
- No overlapping import statements

### Recommended Execution Order (if sequential):
1. Execute Tasks 9-10 first (file moves)
2. Execute Tasks 1-6 next (adapter renames)
3. Execute Tasks 7-8 last (repository renames)

This ensures moved files are in place before renames occur, reducing potential confusion during code review.
