# Refactoring Tasks - Structure and Naming Improvements

## Overview
15 granular refactoring tasks focused on naming consistency, package reorganization, and structural improvements. Tasks are designed to minimize merge conflicts when run in parallel.

---

### Fix DI method name mismatch in RepositoryModule

The `bindPersonalRepository` method name uses singular form but binds to `PersonalsRepository` (plural). This inconsistency creates confusion and violates naming conventions.

:codex-file-citation[codex-file-citation]{line_range_start=81 line_range_end=83 path=app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt#L81-L83"}

:::task-stub{title="Fix bindPersonalRepository → bindPersonalsRepository"}
1. Open RepositoryModule.kt
2. Rename method `bindPersonalRepository` to `bindPersonalsRepository` on line 83
3. Verify compilation succeeds
:::

---

### Rename MyDownloadService to DownloadService

The `MyDownloadService` class uses a "My" prefix which is inconsistent with other service classes in the codebase. All other services follow the pattern `*Service.kt` without prefixes.

:codex-file-citation[codex-file-citation]{line_range_start=42 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/service/MyDownloadService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/service/MyDownloadService.kt#L42-L42"}

:::task-stub{title="Rename MyDownloadService → DownloadService"}
1. Rename file MyDownloadService.kt to DownloadService.kt
2. Update class name from MyDownloadService to DownloadService
3. Update all references to this service throughout the codebase
4. Update AndroidManifest.xml if the service is registered there
:::

---

### Rename MarkdownDialog to MarkdownDialogFragment

The `MarkdownDialog` class extends `DialogFragment` but doesn't follow the `*DialogFragment` naming convention used for other dialog fragments.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=26 path=app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialog.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialog.kt#L26-L26"}

:::task-stub{title="Rename MarkdownDialog → MarkdownDialogFragment"}
1. Rename file MarkdownDialog.kt to MarkdownDialogFragment.kt
2. Update class name from MarkdownDialog to MarkdownDialogFragment
3. Update all references in ChallengeHelper.kt and any other usages
:::

---

### Move OnTagClickListener to root callback package

The `OnTagClickListener` interface is located in `ui/callback/` but should be consolidated with other callback interfaces in the root `callback/` package for consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=11 path=app/src/main/java/org/ole/planet/myplanet/ui/callback/OnTagClickListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/callback/OnTagClickListener.kt#L1-L11"}

:::task-stub{title="Move OnTagClickListener to callback/"}
1. Move file from ui/callback/OnTagClickListener.kt to callback/OnTagClickListener.kt
2. Update package declaration to org.ole.planet.myplanet.callback
3. Update imports in TagAdapter.kt and any other files referencing this interface
:::

---

### Move OnNewsItemClickListener to root callback package

The `OnNewsItemClickListener` interface is located in `ui/callback/` but should be in the root `callback/` package where all other listener interfaces reside.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=16 path=app/src/main/java/org/ole/planet/myplanet/ui/callback/OnNewsItemClickListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/callback/OnNewsItemClickListener.kt#L1-L16"}

:::task-stub{title="Move OnNewsItemClickListener to callback/"}
1. Move file from ui/callback/OnNewsItemClickListener.kt to callback/OnNewsItemClickListener.kt
2. Update package declaration to org.ole.planet.myplanet.callback
3. Update all imports referencing this interface
:::

---

### Move OnTeamActionsListener to root callback package

The `OnTeamActionsListener` interface is nested in `ui/teams/callback/` but should be consolidated in the root `callback/` package for consistency with other listener interfaces.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/callback/OnTeamActionsListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/callback/OnTeamActionsListener.kt#L1-L9"}

:::task-stub{title="Move OnTeamActionsListener to callback/"}
1. Move file from ui/teams/callback/OnTeamActionsListener.kt to callback/OnTeamActionsListener.kt
2. Update package declaration to org.ole.planet.myplanet.callback
3. Update all imports referencing this interface
:::

---

### Move OnUpdateCompleteListener to root callback package

The `OnUpdateCompleteListener` interface is nested in `ui/teams/callback/` but should be in the root `callback/` package for consistency.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=9 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/callback/OnUpdateCompleteListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/callback/OnUpdateCompleteListener.kt#L1-L9"}

:::task-stub{title="Move OnUpdateCompleteListener to callback/"}
1. Move file from ui/teams/callback/OnUpdateCompleteListener.kt to callback/OnUpdateCompleteListener.kt
2. Update package declaration to org.ole.planet.myplanet.callback
3. Update all imports referencing this interface
4. Delete empty ui/teams/callback/ directory after move
:::

---

### Move ItemTouchHelper classes to base/touchhelper package

The `ItemTouchHelperViewHolder`, `ItemTouchHelperListener`, and `SimpleItemTouchHelperCallback` classes are UI infrastructure components mixed with business logic callbacks in the `callback/` package. They should be in a dedicated `base/touchhelper/` package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/callback/ItemTouchHelperViewHolder.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/ItemTouchHelperViewHolder.kt#L1-L8"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/callback/SimpleItemTouchHelperCallback.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/callback/SimpleItemTouchHelperCallback.kt#L1-L66"}

:::task-stub{title="Move ItemTouchHelper classes to base/touchhelper/"}
1. Create new package base/touchhelper/
2. Move ItemTouchHelperViewHolder.kt to base/touchhelper/
3. Move ItemTouchHelperListener.kt to base/touchhelper/
4. Move SimpleItemTouchHelperCallback.kt to base/touchhelper/
5. Update package declarations in all three files
6. Update all imports referencing these classes
:::

---

### Move StepItem data class to model package

The `StepItem` data class is located in `ui/courses/` but as a data model it should be in the `model/` package with other data classes.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=6 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/StepItem.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/StepItem.kt#L1-L6"}

:::task-stub{title="Move StepItem to model/"}
1. Move file from ui/courses/StepItem.kt to model/StepItem.kt
2. Update package declaration to org.ole.planet.myplanet.model
3. Update imports in StepsAdapter.kt and any other files using StepItem
:::

---

### Move SubmissionItem data class to model package

The `SubmissionItem` data class is located in `ui/submissions/` but as a data model it should be in the `model/` package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionItem.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionItem.kt#L1-L8"}

:::task-stub{title="Move SubmissionItem to model/"}
1. Move file from ui/submissions/SubmissionItem.kt to model/SubmissionItem.kt
2. Update package declaration to org.ole.planet.myplanet.model
3. Update imports in SubmissionsAdapter.kt and any other files using SubmissionItem
:::

---

### Move SurveyInfo data class to model package

The `SurveyInfo` data class is located in `ui/survey/` but as a data model it should be in the `model/` package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyInfo.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyInfo.kt#L1-L8"}

:::task-stub{title="Move SurveyInfo to model/"}
1. Move file from ui/survey/SurveyInfo.kt to model/SurveyInfo.kt
2. Update package declaration to org.ole.planet.myplanet.model
3. Update imports in SurveyAdapter.kt and any other files using SurveyInfo
:::

---

### Move TeamDetails and TeamStatus data classes to model package

The `TeamDetails` and `TeamStatus` data classes are located in `ui/teams/` but as data models they should be in the `model/` package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetails.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetails.kt#L1-L22"}

:::task-stub{title="Move TeamDetails and TeamStatus to model/"}
1. Move file from ui/teams/TeamDetails.kt to model/TeamDetails.kt
2. Update package declaration to org.ole.planet.myplanet.model
3. Update imports in TeamListAdapter.kt, OnTeamActionsListener.kt, and other files
:::

---

### Extract TagData sealed class to separate model file

The `TagData` sealed class is defined inside `TagAdapter.kt` but should be extracted to its own file in the `model/` package for better organization.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/TagAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/TagAdapter.kt#L18-L31"}

:::task-stub{title="Extract TagData to model/TagData.kt"}
1. Create new file model/TagData.kt
2. Move TagData sealed class and its subclasses (Parent, Child) to the new file
3. Update package declaration to org.ole.planet.myplanet.model
4. Add import for TagData in TagAdapter.kt
5. Update imports in OnTagClickListener.kt which references TagData.Parent
:::

---

### Move NewsLabelManager to utilities package

The `NewsLabelManager` class in `ui/voices/` contains business logic and repository access. It should be in `utilities/` or `service/` package rather than a UI package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsLabelManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/NewsLabelManager.kt#L1-L30"}

:::task-stub{title="Move NewsLabelManager to utilities/"}
1. Move file from ui/voices/NewsLabelManager.kt to utilities/NewsLabelManager.kt
2. Update package declaration to org.ole.planet.myplanet.utilities
3. Update imports in NewsAdapter.kt and any other files using NewsLabelManager
:::

---

### Move ChallengeHelper to utilities package

The `ChallengeHelper` class in `ui/dashboard/` contains complex business logic with repository access. It should be in `utilities/` package rather than a UI package.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt#L1-L40"}

:::task-stub{title="Move ChallengeHelper to utilities/"}
1. Move file from ui/dashboard/ChallengeHelper.kt to utilities/ChallengeHelper.kt
2. Update package declaration to org.ole.planet.myplanet.utilities
3. Update imports in DashboardActivity.kt and any other files using ChallengeHelper
:::

---

## Task Groupings for Parallel Execution

To avoid merge conflicts, execute these task groups in parallel:

**Group A (Callback consolidation - touches callback/ and ui/callback/):**
- Move OnTagClickListener to callback/
- Move OnNewsItemClickListener to callback/
- Move OnTeamActionsListener to callback/
- Move OnUpdateCompleteListener to callback/

**Group B (Model extraction - touches model/ and ui/*/):**
- Move StepItem to model/
- Move SubmissionItem to model/
- Move SurveyInfo to model/
- Move TeamDetails to model/
- Extract TagData to model/

**Group C (Utility relocation - touches utilities/ and ui/*/):**
- Move NewsLabelManager to utilities/
- Move ChallengeHelper to utilities/

**Group D (Independent renames - isolated files):**
- Fix bindPersonalRepository method name
- Rename MyDownloadService
- Rename MarkdownDialog

**Group E (Base infrastructure - touches base/ and callback/):**
- Move ItemTouchHelper classes to base/touchhelper/
