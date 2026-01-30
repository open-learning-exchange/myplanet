# myPlanet Code Cleanup Refactoring Tasks

This document outlines 18 granular refactoring tasks focused on internal file cleanup, naming consistency, and code organization without any architectural changes or file movements.

### Normalize import organization in DialogUtils.kt

DialogUtils.kt has imports scattered without following Android's import grouping convention (android.* → androidx.* → third-party → org.ole). The file also imports `DialogInterface` which appears unused in the visible portions of the code. Organizing imports by category improves readability and maintainability.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=23 path=app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DialogUtils.kt#L3-L23"}

:::task-stub{title="Reorganize imports in DialogUtils.kt"}
1. Group imports by category: android.* first, then androidx.*, then third-party, then org.ole.*
2. Add blank lines between import groups
3. Remove unused `android.content.DialogInterface` import if confirmed unused
4. Sort imports alphabetically within each group
:::

### Standardize member variable ordering in DownloadWorker.kt

DownloadWorker.kt mixes initialization patterns with `val` properties using direct assignment, lazy initialization, and regular fields without clear ordering. Member variables should follow a consistent pattern: constants first, then lazy-initialized fields, then regular fields, improving code organization.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=36 path=app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt#L28-L36"}

:::task-stub{title="Reorder member variables in DownloadWorker.kt"}
1. Move `preferences` field before `apiInterface` to group similar initialization patterns
2. Add comment separators for field groups (e.g., "// System services", "// Injected dependencies")
3. Ensure consistent ordering: system services → preferences → lazy-initialized API interface
:::

### Fix constant naming inconsistency in NotificationUtils.kt

NotificationUtils.kt uses proper UPPER_SNAKE_CASE for most constants but has an inconsistent local variable `a` at line 64 which should follow camelCase naming for local variables. Additionally, the channel ID string "11" is hardcoded and should be extracted as a named constant for clarity.

:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=43 path=app/src/main/java/org/ole/planet/myplanet/utils/NotificationUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/NotificationUtils.kt#L20-L43"}

:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=68 path=app/src/main/java/org/ole/planet/myplanet/utils/NotificationUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/NotificationUtils.kt#L62-L68"}

:::task-stub{title="Fix naming inconsistencies in NotificationUtils.kt"}
1. Rename local variable `a` to `builder` for clarity at line 64
2. Extract hardcoded channel ID "11" to a private const val LEGACY_CHANNEL_ID = "11"
3. Update all references to use the new constant name
:::

### Normalize companion object placement in RetryQueue.kt

RetryQueue.kt places the companion object with TAG constant before instance variables, breaking the standard Kotlin convention. The companion object should be placed at the end of the class or immediately after the primary constructor, with instance variables following constructor parameters.

:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=27 path=app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt#L17-L27"}

:::task-stub{title="Reorder companion object in RetryQueue.kt"}
1. Move companion object block from lines 21-23 to the end of the class
2. Ensure instance variables (isProcessing, mutex) remain immediately after constructor
3. Verify ordering follows: constructor → instance variables → methods → companion object
:::

### Consolidate property declarations in BaseRecyclerFragment.kt

BaseRecyclerFragment.kt mixes lateinit, nullable, and initialized properties without logical grouping. Properties like `subjects`, `languages`, `mediums`, and `levels` (similar mutable sets) are separated from each other, while `resources` and `courseLib` lack clear grouping. Organizing related properties together improves code clarity.

:codex-file-citation[codex-file-citation]{line_range_start=34 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L34-L50"}

:::task-stub{title="Reorganize properties in BaseRecyclerFragment.kt"}
1. Group mutable set properties (subjects, languages, mediums, levels) together
2. Group lateinit UI properties (recyclerView, tvMessage, tvFragmentInfo) together
3. Group nullable/optional properties (tvDelete, list, resources, courseLib) together
4. Add comment separators for each logical group
:::

### Remove inconsistent property prefixes in RealmNews.kt

RealmNews.kt inconsistently uses underscores for some properties (_id, _rev) while others use camelCase. The model mixes primary key naming with underscore prefix and regular properties. While _id and _rev may be database-specific, local Kotlin properties should maintain consistent camelCase naming throughout.

:codex-file-citation[codex-file-citation]{line_range_start=23 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/model/RealmNews.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmNews.kt#L23-L60"}

:::task-stub{title="Standardize property naming in RealmNews.kt"}
1. Add comment explaining underscore prefix for CouchDB fields (_id, _rev)
2. Ensure all other properties consistently use camelCase
3. Group properties logically: IDs first, then metadata, then content, then flags
4. Add blank lines between property groups for readability
:::

### Remove unused import from ChatDetailFragment.kt

ChatDetailFragment.kt imports `android.graphics.Typeface` at line 5 but this import appears unused in the visible code. Additionally, the fragment mixes lateinit, nullable, and initialized properties without clear organization, making it harder to understand initialization requirements.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=23 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt#L1-L23"}

:codex-file-citation[codex-file-citation]{line_range_start=63 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt#L63-L80"}

:::task-stub{title="Clean up imports and organize properties in ChatDetailFragment.kt"}
1. Remove unused `android.graphics.Typeface` import at line 5
2. Reorder properties to group: binding → lateinit UI → state variables → injected dependencies
3. Add comment markers for property groups
:::

### Normalize member ordering in OnboardingActivity.kt

OnboardingActivity.kt mixes lateinit and regular property initialization inconsistently. Properties `binding`, `mAdapter`, and `dots` use lateinit while `onBoardItems` uses direct initialization. The property `prefData` is also lateinit but initialized in onCreate. Consistent patterns improve code predictability.

:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/ui/onboarding/OnboardingActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/onboarding/OnboardingActivity.kt#L24-L31"}

:::task-stub{title="Standardize property initialization in OnboardingActivity.kt"}
1. Group lateinit properties together (binding, mAdapter, dots, prefData)
2. Group initialized properties together (onBoardItems, dotsCount)
3. Add comments explaining initialization timing for lateinit properties
4. Reorder to follow: lateinit binding → lateinit UI components → initialized data
:::

### Improve callback field naming in ANRWatchdog.kt

ANRWatchdog.kt uses generic names `tickUpdater` and `mainHandler` for Runnable and Handler fields. The naming could be more descriptive to indicate purpose. Following Kotlin conventions, callback/handler fields could use more explicit naming patterns for clarity.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/utils/ANRWatchdog.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/ANRWatchdog.kt#L12-L20"}

:::task-stub{title="Rename callback fields in ANRWatchdog.kt"}
1. Rename `tickUpdater` to `tickUpdateRunnable` for clarity
2. Consider renaming `mainHandler` to `mainThreadHandler` for specificity
3. Update all references to use new names throughout the file
:::

### Group cache fields in CoursesStepsAdapter.kt

CoursesStepsAdapter.kt declares multiple cache-related maps (`descriptionVisibilityMap`, `examQuestionCountCache`) and state variables (`currentlyVisibleStepId`) scattered without grouping. Cache fields should be grouped together and clearly marked to improve maintainability.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=23 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsAdapter.kt#L19-L23"}

:::task-stub{title="Organize cache fields in CoursesStepsAdapter.kt"}
1. Group all cache-related maps together (descriptionVisibilityMap, examQuestionCountCache)
2. Place currentlyVisibleStepId with cache fields
3. Add comment block "// Cache and state management" above the grouped fields
4. Reorder to follow: constructor parameters → cache/state fields → lifecycle methods
:::

### Standardize constructor parameter ordering in DataService.kt

DataService.kt has an extensive primary constructor followed by a secondary constructor with multiple entry point retrievals. The parameter ordering in the primary constructor mixes services and scope without clear grouping. Consistent ordering improves readability of dependency injection patterns.

:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=75 path=app/src/main/java/org/ole/planet/myplanet/data/DataService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/data/DataService.kt#L45-L75"}

:::task-stub{title="Reorder constructor parameters in DataService.kt"}
1. Group constructor parameters by type: Context → API dependencies → Database → Scope → Repositories → Services
2. Add inline comments for parameter groups in primary constructor
3. Maintain same grouping in secondary constructor's entry point retrievals
:::

### Reorganize cache fields in AdaptiveBatchProcessor.kt

AdaptiveBatchProcessor.kt places cache-related fields (`cachedCapabilities`, `lastCapabilityCheck`, `cacheValidityMs`) mixed with baseConfig without clear separation. Cache validity constant should be grouped with other cache management fields and clearly documented.

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=27 path=app/src/main/java/org/ole/planet/myplanet/services/sync/AdaptiveBatchProcessor.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/AdaptiveBatchProcessor.kt#L22-L27"}

:::task-stub{title="Group cache management fields in AdaptiveBatchProcessor.kt"}
1. Move baseConfig to top after constructor parameters
2. Group all cache-related fields together (cachedCapabilities, lastCapabilityCheck, cacheValidityMs)
3. Add comment "// Cache management" above grouped cache fields
4. Consider making cacheValidityMs a companion object constant
:::

### Improve constructor parameter naming in VoicesRepositoryImpl.kt

VoicesRepositoryImpl.kt constructor parameters lack alphabetical or logical ordering. The `gson` parameter comes after `databaseService`, breaking potential alphabetical organization. Following consistent parameter ordering conventions improves code predictability.

:codex-file-citation[codex-file-citation]{line_range_start=23 line_range_end=26 path=app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt#L23-L26"}

:::task-stub{title="Reorder constructor parameters in VoicesRepositoryImpl.kt"}
1. Reorder constructor parameters alphabetically: databaseService, gson
2. Update any documentation to reflect the new parameter order
3. Verify the parent class RealmRepository constructor call
:::

### Clarify cache field purpose in HealthExaminationAdapter.kt

HealthExaminationAdapter.kt declares `displayNameCache` as a mutable map directly in the class body. This cache field could be better organized with documentation explaining its purpose and potentially moved to a companion object if shared across instances.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt#L29-L41"}

:::task-stub{title="Document and position cache field in HealthExaminationAdapter.kt"}
1. Add inline comment explaining displayNameCache purpose
2. Position cache field after constructor parameters as first class member
3. Consider whether cache should be instance-specific or companion object
:::

### Normalize import ordering in DashboardActivity.kt

DashboardActivity.kt imports are not properly grouped by package origin. Imports from android, androidx, material, and org.ole packages are mixed together without clear separation. Following Android's import organization conventions improves code maintainability.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L3-L50"}

:::task-stub{title="Reorganize imports in DashboardActivity.kt"}
1. Group imports: android.* → androidx.* → com.google.* → com.mikepenz.* → javax.* → kotlin.* → org.ole.*
2. Add blank lines between major package groups
3. Sort imports alphabetically within each group
4. Remove any unused imports if present
:::

### Standardize property initialization pattern in ResourcesFragment.kt

ResourcesFragment.kt uses various patterns for property initialization including lateinit, nullable with backing fields, lazy delegation via `get()`, and direct initialization. The mix of `val tvAddToLib get() = binding.tvAdd` style properties with lateinit and nullable properties creates inconsistent patterns that reduce code clarity.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L60-L80"}

:::task-stub{title="Normalize property patterns in ResourcesFragment.kt"}
1. Group delegate properties (tvAddToLib, tvSelected, etc.) together with comment "// View shortcuts"
2. Group lateinit properties together with comment "// Initialized in lifecycle methods"
3. Group nullable properties together with comment "// Optional state"
4. Reorder to follow: binding → view shortcuts → lateinit properties → nullable properties → injected dependencies
:::

### Extract and organize channel constants in DownloadUtils.kt

DownloadUtils.kt defines channel ID constants at the top but uses string literals for channel names and descriptions throughout the createChannels method. These strings should be extracted as constants for consistency and maintainability. The constant naming also follows UPPER_SNAKE_CASE correctly.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=71 path=app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt#L29-L71"}

:::task-stub{title="Extract channel name constants in DownloadUtils.kt"}
1. Extract "Download Service", "Download Completion", "Background Downloads" as constants
2. Extract channel descriptions as constants (e.g., DOWNLOAD_CHANNEL_DESCRIPTION)
3. Update createChannels method to use the new constants
4. Group all channel-related constants together at the top of the object
:::

### Organize state and sync fields in SyncManager.kt

SyncManager.kt mixes state fields (isSyncing, stringArray, listener, backgroundSync, betaSync), flow fields (_syncStatus, syncStatus), and initialization jobs without clear grouping. The class would benefit from logical organization of these different categories of fields.

:codex-file-citation[codex-file-citation]{line_range_start=62 line_range_end=80 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L62-L80"}

:::task-stub{title="Organize fields by category in SyncManager.kt"}
1. Group state management fields (isSyncing, stringArray, betaSync)
2. Group callback/listener fields (listener)
3. Group job management fields (backgroundSync, initializationJob)
4. Group flow fields (_syncStatus, syncStatus)
5. Add comment headers for each group
6. Reorder to follow: constructor params → state → callbacks → jobs → flows
:::

