# Refactoring Tasks Report

15 granular, parallel-safe refactoring opportunities for myPlanet codebase.

---

### 1. Fix typo: ResourcesUpdateListner → ResourcesUpdateListener

The interface name contains a typo where "Listener" is misspelled as "Listner". This should be corrected for consistency with standard naming conventions used throughout the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=6 path=app/src/main/java/org/ole/planet/myplanet/ui/team/resources/ResourcesUpdateListner.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/team/resources/ResourcesUpdateListner.kt#L3-L6"}

:::task-stub{title="Fix ResourcesUpdateListner typo"}
1. Rename file ResourcesUpdateListner.kt to ResourcesUpdateListener.kt
2. Rename interface ResourcesUpdateListner to ResourcesUpdateListener
3. Update all references to ResourcesUpdateListner in TeamResourcesFragment and related files
:::

---

### 2. Rename ManagerSync to SyncManager for suffix-style consistency

The class uses prefix naming style (ManagerSync) which is inconsistent with other managers in the codebase like SyncManager, UploadManager, and ThemeManager that use suffix style.

:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/data/ManagerSync.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/data/ManagerSync.kt#L27-L30"}

:::task-stub{title="Rename ManagerSync to SyncManager"}
1. Rename file ManagerSync.kt to LoginSyncManager.kt (to avoid conflict with existing SyncManager.kt)
2. Rename class ManagerSync to LoginSyncManager
3. Update all references in LoginActivity and related sync files
:::

---

### 3. Rename RealmMyHealthPojo to RealmHealthExamination

The class uses outdated "Pojo" suffix which is not consistent with other Realm models. Since RealmMyHealth already exists for profile data, this class should be renamed to reflect it stores examination records.

:codex-file-citation[codex-file-citation]{line_range_start=11 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyHealthPojo.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmMyHealthPojo.kt#L11-L15"}

:::task-stub{title="Rename RealmMyHealthPojo to RealmHealthExamination"}
1. Rename file RealmMyHealthPojo.kt to RealmHealthExamination.kt
2. Rename class RealmMyHealthPojo to RealmHealthExamination
3. Update all references in myhealth package and related files
:::

---

### 4. Rename ServerAddressesModel to ServerAddress

The "Model" suffix is redundant since it's a simple data class. The plural "Addresses" is also misleading as it represents a single server address.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=3 path=app/src/main/java/org/ole/planet/myplanet/model/ServerAddressesModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/ServerAddressesModel.kt#L3-L3"}

:::task-stub{title="Rename ServerAddressesModel to ServerAddress"}
1. Rename file ServerAddressesModel.kt to ServerAddress.kt
2. Rename data class ServerAddressesModel to ServerAddress
3. Update all references in sync package and ServerAddressAdapter
:::

---

### 5. Rename TransactionData to Transaction

The "Data" suffix is vague and provides no additional meaning. The class represents a financial transaction record.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/model/TransactionData.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/TransactionData.kt#L3-L10"}

:::task-stub{title="Rename TransactionData to Transaction"}
1. Rename file TransactionData.kt to Transaction.kt
2. Rename data class TransactionData to Transaction
3. Update all references in enterprises package and TeamsRepository
:::

---

### 6. Rename SurveyBindingData to SurveyFormState

The name "BindingData" is unclear. This class holds form state for survey submissions and should be named accordingly.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=8 path=app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyBindingData.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/survey/SurveyBindingData.kt#L5-L8"}

:::task-stub{title="Rename SurveyBindingData to SurveyFormState"}
1. Rename file SurveyBindingData.kt to SurveyFormState.kt
2. Rename data class SurveyBindingData to SurveyFormState
3. Update all references in survey package
:::

---

### 7. Rename TeamData to TeamDetails

The "Data" suffix is vague. This class contains detailed team information and should be named more descriptively.

:codex-file-citation[codex-file-citation]{line_range_start=9 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/ui/team/TeamData.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/team/TeamData.kt#L9-L22"}

:::task-stub{title="Rename TeamData to TeamDetails"}
1. Rename data class TeamData to TeamDetails in TeamData.kt
2. Rename file TeamData.kt to TeamDetails.kt
3. Update all references in team package and adapters
:::

---

### 8. Rename ChatApiHelper to ChatApiService

The class handles API interactions which is a service responsibility, not just a helper. This aligns with other service-layer naming in the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatApiHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatApiHelper.kt#L18-L22"}

:::task-stub{title="Rename ChatApiHelper to ChatApiService"}
1. Rename file ChatApiHelper.kt to ChatApiService.kt
2. Rename class ChatApiHelper to ChatApiService
3. Update all injection sites and references in chat package
:::

---

### 9. Rename ChallengeHelper to ChallengeService

This class contains business logic for challenge management which makes it a service, not just a helper utility.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ChallengeHelper.kt#L28-L30"}

:::task-stub{title="Rename ChallengeHelper to ChallengeService"}
1. Rename file ChallengeHelper.kt to ChallengeService.kt
2. Rename class ChallengeHelper to ChallengeService
3. Update all references in DashboardActivity
:::

---

### 10. Move OnStartDragListener to callback package

The OnStartDragListener interface is a callback that should be in the centralized callback package alongside other listeners like OnRatingChangeListener and TeamUpdateListener.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=7 path=app/src/main/java/org/ole/planet/myplanet/ui/life/helper/OnStartDragListener.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/life/helper/OnStartDragListener.kt#L1-L7"}

:::task-stub{title="Move OnStartDragListener to callback package"}
1. Move OnStartDragListener.kt from ui/life/helper/ to callback/
2. Update package declaration to org.ole.planet.myplanet.callback
3. Update imports in LifeFragment and LifeAdapter
:::

---

### 11. Rename UserProfileDbHandler to UserProfileHandler

The "Db" prefix is an implementation detail that doesn't need to be in the class name. The service handles user profile operations abstractly.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=30 path=app/src/main/java/org/ole/planet/myplanet/service/UserProfileDbHandler.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/service/UserProfileDbHandler.kt#L28-L30"}

:::task-stub{title="Rename UserProfileDbHandler to UserProfileHandler"}
1. Rename file UserProfileDbHandler.kt to UserProfileHandler.kt
2. Rename class UserProfileDbHandler to UserProfileHandler
3. Update all injection sites and references across the codebase
:::

---

### 12. Rename ThreadSafeRealmHelper to ThreadSafeRealmManager

The class manages Realm instances across threads, which is manager behavior. The "Helper" suffix understates its responsibility.

:codex-file-citation[codex-file-citation]{line_range_start=6 line_range_end=10 path=app/src/main/java/org/ole/planet/myplanet/service/sync/ThreadSafeRealmHelper.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/service/sync/ThreadSafeRealmHelper.kt#L6-L10"}

:::task-stub{title="Rename ThreadSafeRealmHelper to ThreadSafeRealmManager"}
1. Rename file ThreadSafeRealmHelper.kt to ThreadSafeRealmManager.kt
2. Rename object ThreadSafeRealmHelper to ThreadSafeRealmManager
3. Update all references in sync package
:::

---

### 13. Rename ui/personals package to ui/personal for singular consistency

Most UI packages use singular naming (team, chat, survey, feedback) but personals uses plural. This should be standardized to singular form.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=1 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt#L1-L1"}

:::task-stub{title="Rename ui/personals package to ui/personal"}
1. Rename directory personals/ to personal/
2. Update package declarations in PersonalsFragment.kt and PersonalsAdapter.kt
3. Update all imports referencing org.ole.planet.myplanet.ui.personals
:::

---

### 14. Rename ui/ratings package to ui/rating for singular consistency

The ratings package uses plural naming while most other UI packages use singular form. This should be standardized.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=1 path=app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsFragment.kt#L1-L1"}

:::task-stub{title="Rename ui/ratings package to ui/rating"}
1. Rename directory ratings/ to rating/
2. Update package declarations in RatingsFragment.kt and RatingsViewModel.kt
3. Update all imports referencing org.ole.planet.myplanet.ui.ratings
:::

---

### 15. Rename ui/references package to ui/reference for singular consistency

The references package uses plural naming while most other UI packages use singular form. This should be standardized to match the pattern.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=1 path=app/src/main/java/org/ole/planet/myplanet/ui/references/ReferenceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/references/ReferenceFragment.kt#L1-L1"}

:::task-stub{title="Rename ui/references package to ui/reference"}
1. Rename directory references/ to reference/
2. Update package declaration in ReferenceFragment.kt
3. Update all imports referencing org.ole.planet.myplanet.ui.references
:::

---

## Parallel Execution Guide

These tasks are designed to avoid merge conflicts when executed in parallel:

| Task Group | Tasks | Rationale |
|------------|-------|-----------|
| **Group A** | 1, 4, 5 | Isolated model/interface renames with no shared dependencies |
| **Group B** | 2, 11, 12 | Service layer renames in different packages |
| **Group C** | 3, 6, 7 | UI data class renames in different packages |
| **Group D** | 8, 9, 10 | Chat/dashboard/life helper renames |
| **Group E** | 13, 14, 15 | Package directory renames (execute sequentially within group) |

**Recommended execution**: Run Groups A-D in parallel. Execute Group E last as package renames may affect import organization.
