# Refactoring Tasks Report

This document contains 15 granular refactoring tasks focused on naming consistency, package reorganization, and structural improvements. All tasks are independent and can be executed in parallel without merge conflicts.

---

### 1. Rename SubmissionsRepositoryExporter to SubmissionsExporter and move to utilities

This class generates PDF files for submissions and is not a repository implementation. It should be moved to the utilities package and renamed to reflect its actual purpose as an exporter utility.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=274 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt#L1-L274"}

:::task-stub{title="Move SubmissionsRepositoryExporter to utilities"}
1. Move file from repository/ to utilities/ package
2. Rename class from SubmissionsRepositoryExporter to SubmissionsExporter
3. Update package declaration to org.ole.planet.myplanet.utilities
4. Update all imports referencing this class throughout codebase
5. Update RepositoryModule.kt if this class is bound there
:::

---

### 2. Rename Markdown.kt to MarkdownUtils.kt for consistency

The utilities package follows a consistent naming pattern with *Utils suffix (NetworkUtils, FileUtils, ImageUtils, etc.). The Markdown.kt file should follow this pattern for consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/utilities/Markdown.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utilities/Markdown.kt#L1-L40"}

:::task-stub{title="Rename Markdown.kt to MarkdownUtils.kt"}
1. Rename file from Markdown.kt to MarkdownUtils.kt
2. Update class/object name if applicable
3. Update all imports referencing this file throughout codebase
:::

---

### 3. Rename ItemTouchHelperListener to OnItemMoveListener

The callback package follows a consistent naming pattern with On* prefix (OnSyncListener, OnFilterListener, etc.). ItemTouchHelperListener should follow this pattern and have a clearer name describing its purpose.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=6 path=app/src/main/java/org/ole/planet/myplanet/callback/ItemTouchHelperListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/ItemTouchHelperListener.kt#L1-L6"}

:::task-stub{title="Rename ItemTouchHelperListener to OnItemMoveListener"}
1. Rename file from ItemTouchHelperListener.kt to OnItemMoveListener.kt
2. Rename interface from ItemTouchHelperListener to OnItemMoveListener
3. Update all implementations and references throughout codebase
:::

---

### 4. Rename OnCourseItemSelected to OnCourseItemSelectedListener

The callback package follows a consistent pattern ending with *Listener suffix. OnCourseItemSelected should follow this naming convention for consistency with OnFilterListener, OnSyncListener, etc.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=11 path=app/src/main/java/org/ole/planet/myplanet/callback/OnCourseItemSelected.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnCourseItemSelected.kt#L1-L11"}

:::task-stub{title="Rename OnCourseItemSelected to OnCourseItemSelectedListener"}
1. Rename file from OnCourseItemSelected.kt to OnCourseItemSelectedListener.kt
2. Rename interface from OnCourseItemSelected to OnCourseItemSelectedListener
3. Update all implementations and references throughout codebase
:::

---

### 5. Rename OnLibraryItemSelected to OnLibraryItemSelectedListener

The callback package follows a consistent pattern ending with *Listener suffix. OnLibraryItemSelected should follow this naming convention for consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/callback/OnLibraryItemSelected.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnLibraryItemSelected.kt#L1-L12"}

:::task-stub{title="Rename OnLibraryItemSelected to OnLibraryItemSelectedListener"}
1. Rename file from OnLibraryItemSelected.kt to OnLibraryItemSelectedListener.kt
2. Rename interface from OnLibraryItemSelected to OnLibraryItemSelectedListener
3. Update all implementations and references throughout codebase
:::

---

### 6. Rename OnSelectedMyPersonal to OnPersonalSelectedListener

This interface has an unusual naming pattern (OnSelectedMyPersonal) that differs from the standard On*Listener pattern used elsewhere in the callback package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=11 path=app/src/main/java/org/ole/planet/myplanet/callback/OnSelectedMyPersonal.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/OnSelectedMyPersonal.kt#L1-L11"}

:::task-stub{title="Rename OnSelectedMyPersonal to OnPersonalSelectedListener"}
1. Rename file from OnSelectedMyPersonal.kt to OnPersonalSelectedListener.kt
2. Rename interface from OnSelectedMyPersonal to OnPersonalSelectedListener
3. Update all implementations and references throughout codebase
:::

---

### 7. Extract ChatAdapter.OnChatItemClickListener to callback package

Inner listener interfaces should be extracted to the centralized callback package for consistency and reusability. This matches the pattern used by other listener interfaces in the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=33 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt#L33-L36"}

:::task-stub{title="Extract OnChatItemClickListener to callback package"}
1. Create new file callback/OnChatItemClickListener.kt
2. Move interface definition from ChatAdapter.kt to new file
3. Update ChatAdapter.kt to import from callback package
4. Update all implementations to use new import path
:::

---

### 8. Extract ChatHistoryAdapter.ChatHistoryItemClickListener to callback package

Inner listener interfaces should be extracted to the centralized callback package. The interface should also be renamed to follow the On* prefix pattern.

:codex-file-citation[codex-file-citation]{line_range_start=86 line_range_end=88 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt#L86-L88"}

:::task-stub{title="Extract ChatHistoryItemClickListener to callback package"}
1. Create new file callback/OnChatHistoryItemClickListener.kt
2. Rename interface from ChatHistoryItemClickListener to OnChatHistoryItemClickListener
3. Move interface definition from ChatHistoryAdapter.kt to new file
4. Update ChatHistoryAdapter.kt to import from callback package
5. Update all implementations to use new import and name
:::

---

### 9. Extract TeamsAdapter.OnClickTeamItem to callback package

Inner listener interfaces should be extracted to the centralized callback package. The interface should also be renamed to follow a clearer naming pattern.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt#L40-L42"}

:::task-stub{title="Extract OnClickTeamItem to callback package"}
1. Create new file callback/OnTeamEditListener.kt
2. Rename interface from OnClickTeamItem to OnTeamEditListener
3. Move interface definition from TeamsAdapter.kt to new file
4. Update TeamsAdapter.kt to import from callback package
5. Update all implementations to use new import and name
:::

---

### 10. Extract UserProfileAdapter.OnItemClickListener to callback package

Inner listener interfaces with generic names should be extracted and given more specific names. This interface handles user profile item clicks.

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=24 path=app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileAdapter.kt#L22-L24"}

:::task-stub{title="Extract OnItemClickListener to callback package as OnUserProfileClickListener"}
1. Create new file callback/OnUserProfileClickListener.kt
2. Rename interface from OnItemClickListener to OnUserProfileClickListener
3. Move interface definition from UserProfileAdapter.kt to new file
4. Update UserProfileAdapter.kt to import from callback package
5. Update all implementations to use new import and name
:::

---

### 11. Extract MembersAdapter.MemberActionListener to callback package

Inner listener interfaces should be extracted to the centralized callback package. This interface handles member actions like remove, make leader, and leave team.

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt#L32-L36"}

:::task-stub{title="Extract MemberActionListener to callback package"}
1. Create new file callback/OnMemberActionListener.kt
2. Rename interface from MemberActionListener to OnMemberActionListener
3. Move interface definition from MembersAdapter.kt to new file
4. Update MembersAdapter.kt to import from callback package
5. Update all implementations to use new import and name
:::

---

### 12. Extract FeedbackFragment.OnFeedbackSubmittedListener to callback package

Inner listener interfaces should be extracted to the centralized callback package for consistency with other feedback-related callbacks.

:codex-file-citation[codex-file-citation]{line_range_start=32 line_range_end=34 path=app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackFragment.kt#L32-L34"}

:::task-stub{title="Extract OnFeedbackSubmittedListener to callback package"}
1. Create new file callback/OnFeedbackSubmittedListener.kt
2. Move interface definition from FeedbackFragment.kt to new file
3. Update FeedbackFragment.kt to import from callback package
4. Update all implementations to use new import path
:::

---

### 13. Extract AudioRecorderService.AudioRecordListener to callback package

Inner listener interfaces should be extracted to the centralized callback package. This interface handles audio recording events (started, stopped, error).

:codex-file-citation[codex-file-citation]{line_range_start=156 line_range_end=160 path=app/src/main/java/org/ole/planet/myplanet/service/AudioRecorderService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/AudioRecorderService.kt#L156-L160"}

:::task-stub{title="Extract AudioRecordListener to callback package"}
1. Create new file callback/OnAudioRecordListener.kt
2. Rename interface from AudioRecordListener to OnAudioRecordListener
3. Move interface definition from AudioRecorderService.kt to new file
4. Update AudioRecorderService.kt to import from callback package
5. Update all implementations to use new import and name
:::

---

### 14. Extract ChatShareTargets data class to model package

The ChatShareTargets data class is defined inside ChatHistoryAdapter.kt but represents a data model. It should be extracted to its own file in the model package.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=33 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt#L29-L33"}

:::task-stub{title="Extract ChatShareTargets to model package"}
1. Create new file model/ChatShareTargets.kt
2. Move ChatShareTargets data class definition to new file
3. Update package declaration to org.ole.planet.myplanet.model
4. Update ChatHistoryAdapter.kt to import from model package
5. Update all usages to use new import path
:::

---

### 15. Rename VoicesLabelManager to VoicesLabelHelper

This class handles UI label setup and menu interactions, which is more characteristic of a helper class than a manager. The naming should reflect its actual purpose.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelManager.kt#L1-L30"}

:::task-stub{title="Rename VoicesLabelManager to VoicesLabelHelper"}
1. Rename file from VoicesLabelManager.kt to VoicesLabelHelper.kt
2. Rename class from VoicesLabelManager to VoicesLabelHelper
3. Update all imports and usages throughout codebase
:::

---

## Summary

| # | Task | Type | Package |
|---|------|------|---------|
| 1 | Move SubmissionsRepositoryExporter to utilities | Move + Rename | repository → utilities |
| 2 | Rename Markdown.kt to MarkdownUtils.kt | Rename | utilities |
| 3 | Rename ItemTouchHelperListener to OnItemMoveListener | Rename | callback |
| 4 | Rename OnCourseItemSelected to OnCourseItemSelectedListener | Rename | callback |
| 5 | Rename OnLibraryItemSelected to OnLibraryItemSelectedListener | Rename | callback |
| 6 | Rename OnSelectedMyPersonal to OnPersonalSelectedListener | Rename | callback |
| 7 | Extract ChatAdapter.OnChatItemClickListener | Extract | ui/chat → callback |
| 8 | Extract ChatHistoryAdapter.ChatHistoryItemClickListener | Extract + Rename | ui/chat → callback |
| 9 | Extract TeamsAdapter.OnClickTeamItem | Extract + Rename | ui/teams → callback |
| 10 | Extract UserProfileAdapter.OnItemClickListener | Extract + Rename | ui/user → callback |
| 11 | Extract MembersAdapter.MemberActionListener | Extract + Rename | ui/teams/members → callback |
| 12 | Extract FeedbackFragment.OnFeedbackSubmittedListener | Extract | ui/feedback → callback |
| 13 | Extract AudioRecorderService.AudioRecordListener | Extract + Rename | service → callback |
| 14 | Extract ChatShareTargets data class | Extract | ui/chat → model |
| 15 | Rename VoicesLabelManager to VoicesLabelHelper | Rename | ui/voices |

All tasks are independent and can be executed in parallel without merge conflicts.
