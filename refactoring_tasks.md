# Refactoring Tasks Report

This document outlines 10 focused refactoring tasks to reinforce repository boundaries and improve data layer cleanliness. Each task is designed as a small, reviewable PR.

---

### Create DictionaryRepository for DictionaryActivity data access

DictionaryActivity directly uses DatabaseService for all dictionary operations (count, search, insert). Moving these to a dedicated repository will enforce proper data access patterns and enable testing.

:codex-file-citation[codex-file-citation]{line_range_start=145 line_range_end=159 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L145-L159"}
:codex-file-citation[codex-file-citation]{line_range_start=121 line_range_end=137 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L121-L137"}

:::task-stub{title="Create DictionaryRepository with interface and implementation"}
1. Create DictionaryRepository interface with search, count, and insertFromJson methods
2. Create DictionaryRepositoryImpl extending RealmRepository
3. Inject DictionaryRepository into DictionaryActivity
4. Replace direct databaseService calls with repository methods
::::

---

### Remove direct Realm object mutation in VoicesLabelManager

VoicesLabelManager modifies RealmNews.labels directly instead of relying only on repository callbacks. This breaks the unidirectional data flow and can cause stale UI state when the repository update fails.

:codex-file-citation[codex-file-citation]{line_range_start=46 line_range_end=57 path=app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt#L46-L57"}
:codex-file-citation[codex-file-citation]{line_range_start=89 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt#L89-L95"}

:::task-stub{title="Remove direct Realm mutation in VoicesLabelManager"}
1. Remove voice.labels?.add(selectedLabel) and voice.labels?.remove(selectedLabel) calls
2. Keep only the repository callback invocations (addLabelFn/removeLabelFn)
3. Consider emitting a state refresh callback instead of mutating the object directly
::::

---

### Standardize ChatShareTargetAdapter to use DiffUtils.itemCallback

ChatShareTargetAdapter uses raw DiffUtil.ItemCallback instead of the project's DiffUtils.itemCallback utility, creating inconsistency with other adapters.

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatShareTargetAdapter.kt#L73-L80"}

:::task-stub{title="Convert ChatShareTargetAdapter to use DiffUtils.itemCallback"}
1. Replace DiffUtil.ItemCallback with DiffUtils.itemCallback in ChatShareTargetAdapter
2. Remove raw DiffUtil import if no longer needed
3. Verify adapter still compiles and DiffUtil behavior is identical
::::

---

### Standardize TeamsAdapter TeamDiffCallback to use DiffUtils.itemCallback

TeamsAdapter defines a custom TeamDiffCallback instead of using the shared DiffUtils.itemCallback pattern used by other adapters in the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt#L25"}
:codex-file-citation[codex-file-citation]{line_range_start=130 line_range_end=145 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt#L130-L145"}

:::task-stub{title="Replace TeamDiffCallback with DiffUtils.itemCallback"}
1. Find TeamDiffCallback definition in TeamsAdapter
2. Replace with DiffUtils.itemCallback using same areItemsTheSame and areContentsTheSame logic
3. Remove the now-unused TeamDiffCallback class
::::

---

### Remove unused RealmObject import from DashboardPluginFragment

DashboardPluginFragment imports io.realm.RealmObject but never uses it directly. The imports are remnants from when model casting was done differently.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=12 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt#L12"}
:codex-file-citation[codex-file-citation]{line_range_start=101 line_range_end=121 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardPluginFragment.kt#L101-L121"}

:::task-stub{title="Remove unused RealmObject import from DashboardPluginFragment"}
1. Remove import io.realm.RealmObject from DashboardPluginFragment
2. Verify code still compiles (the model casting is done on specific types, not RealmObject)
::::

---

### Move getUserModelFromDexie from BaseTeamFragment to UserRepository

BaseTeamFragment queries user model directly through profileDbHandler instead of using the UserRepository. This creates a parallel path for user data access.

:codex-file-citation[codex-file-citation]{line_range_start=46 line_range_end=49 path=app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L46-L49"}

:::task-stub{title="Replace profileDbHandler.getUserModel with UserRepository call"}
1. Verify UserRepository has getUserModel or add it
2. Inject UserRepository into BaseTeamFragment
3. Replace profileDbHandler.getUserModel() with userRepository.getUserModel()
::::

---

### Consolidate RealmResults Flow collection pattern in RealmRepository

The queryListFlow in RealmRepository has complex manual Realm lifecycle management (safeCloseRealm, emitResults, etc). This could potentially be simplified or documented better.

:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=141 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L66-L141"}

:::task-stub{title="Add KDoc documentation to queryListFlow method"}
1. Add comprehensive KDoc explaining the Flow lifecycle management
2. Document when listeners are removed and Realm is closed
3. Add @throws documentation for potential exceptions
::::

---

### Check VoicesRepositoryImpl for label-related functions

VoicesRepository already has addLabel/removeLabel but check if VoicesLabelManager properly delegates all mutations through the repository without local Realm modifications.

:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepository.kt#L45-L46"}

:::task-stub{title="Verify VoicesLabelManager uses only repository callbacks"}
1. Trace the addLabelFn and removeLabelFn callbacks to their source
2. Confirm they call VoicesRepository.addLabel/removeLabel
3. Remove any direct voice.labels mutation in VoicesLabelManager
::::

---

### Verify TransactionSyncManager bulkInsert methods are properly abstracted

TransactionSyncManager has direct Realm access for bulk inserts (bulkInsertExamsFromSync, bulkInsertTeamActivitiesFromSync, etc). Verify these belong in the appropriate repositories.

:codex-file-citation[codex-file-citation]{line_range_start=252 line_range_end=266 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L252-L266"}

:::task-stub{title="Document bulk insert responsibilities in TransactionSyncManager"}
1. List all bulkInsert methods in TransactionSyncManager
2. Verify each has a corresponding repository method
3. Add TODO comments if any bulk inserts should be moved to repositories
::::

---

### Check ResourcesRepository for missing file operation functions

FileUtils operations for SD card path handling are mixed with repository logic in DictionaryActivity. Verify ResourcesRepository covers all resource file operations properly.

:codex-file-citation[codex-file-citation]{line_range_start=109 line_range_end=119 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L109-L119"}

:::task-stub{title="Audit FileUtils usage for potential repository inclusion"}
1. List all FileUtils.getSDPathFromUrl calls in the codebase
2. Determine which belong in repositories vs utilities
3. Document any file path operations that should be moved to ResourcesRepository
::::

---

## Summary

These tasks focus on:
- **3 tasks** reinforcing repository boundaries (DictionaryRepository, UserRepository in BaseTeamFragment, VoicesRepository verification)
- **3 tasks** standardizing DiffUtil/ListAdapter usage (ChatShareTargetAdapter, TeamsAdapter, unused imports)
- **2 tasks** removing direct Realm mutations (VoicesLabelManager, DashboardPluginFragment)
- **2 tasks** documentation and audit (RealmRepository Flow, TransactionSyncManager bulk inserts)

All tasks are designed to be small, self-contained changes that avoid merge conflicts and can be reviewed independently.
