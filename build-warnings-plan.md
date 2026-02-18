### Remove deprecated Gradle properties `android.builtInKotlin` and `android.newDsl`

AGP 9.0 has built-in Kotlin support, making the `org.jetbrains.kotlin.android` plugin optional. The properties `android.builtInKotlin=false` and `android.newDsl=false` force the old behavior and emit deprecation warnings on every build. Removing them silences the warnings and prepares for AGP 10.0 where these flags will be removed entirely.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=29 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L28-L29"}

:::task-stub{title="Remove deprecated builtInKotlin and newDsl Gradle properties"}
1. Delete line `android.builtInKotlin=false` from `gradle.properties`
2. Delete line `android.newDsl=false` from `gradle.properties`
3. Verify the build still succeeds with `./gradlew assembleDefaultDebug`
:::

### Remove deprecated `android.enableJetifier` property

Jetifier rewrites legacy support-library dependencies to use AndroidX. All dependencies in this project already use AndroidX natively, so Jetifier adds unnecessary build overhead. Removing it eliminates the deprecation warning and speeds up dependency resolution.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L25-L25"}

:::task-stub{title="Remove deprecated enableJetifier Gradle property"}
1. Delete line `android.enableJetifier=true` from `gradle.properties`
2. Run `./gradlew assembleDefaultDebug assembleLiteDebug` and confirm no resolution failures
:::

### Remove unnecessary safe calls on non-null `UserSessionManager` receiver

`profileDbHandler` is injected via `@Inject lateinit var` in `ResourcesFragment` and `CourseDetailFragment`, making it non-null after initialization. The `?.` safe-call operator is redundant and generates compiler warnings.

:codex-file-citation[codex-file-citation]{line_range_start=230 line_range_end=230 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L230-L230"}
:codex-file-citation[codex-file-citation]{line_range_start=256 line_range_end=256 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L256-L256"}
:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=44 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailFragment.kt#L44-L44"}

:::task-stub{title="Remove unnecessary safe calls on non-null UserSessionManager"}
1. In `ResourcesFragment.kt` line 230, change `profileDbHandler?.getUserModel()` to `profileDbHandler.getUserModel()`
2. In `ResourcesFragment.kt` line 256, change `profileDbHandler?.getUserModel()` to `profileDbHandler.getUserModel()`
3. In `CourseDetailFragment.kt` line 44, change `profileDbHandler?.userModel` to `profileDbHandler.userModel`
:::

### Add `@OptIn(ExperimentalCoroutinesApi::class)` to `SubmissionViewModel`

`SubmissionViewModel` uses `flatMapLatest` and `mapLatest` which are annotated with `@ExperimentalCoroutinesApi`. Without an explicit opt-in the compiler emits two warnings per usage. Adding the annotation at the class level silences both.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=40 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt#L40-L40"}
:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=44 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt#L44-L44"}

:::task-stub{title="Add @OptIn for ExperimentalCoroutinesApi in SubmissionViewModel"}
1. Add `import kotlinx.coroutines.ExperimentalCoroutinesApi` to the imports
2. Add `@OptIn(ExperimentalCoroutinesApi::class)` annotation before the `@HiltViewModel` annotation on the `SubmissionViewModel` class
:::

### Remove unnecessary safe call on non-null `RealmStepExam` in `SubmissionViewModel`

On line 59, the `?.` operator is used on a `RealmStepExam` value that is guaranteed non-null by the `filter` map entry. The safe call is redundant.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=59 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt#L59-L59"}

:::task-stub{title="Remove unnecessary safe call on non-null RealmStepExam"}
1. In `SubmissionViewModel.kt` line 59, change `exam?.name?.contains(...)` to `exam.name?.contains(...)` (keep the second `?.` since `name` is nullable)
:::

### Add `@Deprecated` annotation to `getTeamTransactions` override in `TeamsRepositoryImpl`

The interface method `TeamsRepository.getTeamTransactions` is annotated with `@Deprecated`, but the overriding implementation in `TeamsRepositoryImpl` lacks the annotation. The compiler warns that overrides of deprecated members should also be marked deprecated.

:codex-file-citation[codex-file-citation]{line_range_start=103 line_range_end=109 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt#L103-L109"}
:codex-file-citation[codex-file-citation]{line_range_start=302 line_range_end=309 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L302-L309"}

:::task-stub{title="Add @Deprecated annotation to getTeamTransactions override"}
1. Add `@Deprecated("Use getTeamTransactionsWithBalance instead", ReplaceWith("getTeamTransactionsWithBalance(teamId, startDate, endDate, sortAscending)"))` above the `override suspend fun getTeamTransactions` declaration in `TeamsRepositoryImpl.kt` at line 302
:::

### Remove redundant Elvis operator on non-null `SharedPreferences` parameter

In `ProcessUserDataActivity.saveUserInfoPref`, the `settings` parameter is typed as non-null `SharedPreferences`, so the expression `settings ?: appPreferences` always evaluates to `settings`. The Elvis branch is dead code.

:codex-file-citation[codex-file-citation]{line_range_start=302 line_range_end=303 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L302-L303"}

:::task-stub{title="Remove redundant Elvis operator in ProcessUserDataActivity"}
1. In `ProcessUserDataActivity.kt` line 303, change `this.settings = settings ?: appPreferences` to `this.settings = settings`
:::

### Simplify always-true null check on non-null `JsonArray` in `LoginSyncManager`

`JsonUtils.getJsonArray` returns `JsonArray` (non-null). The `array != null` check on line 163 is always true, making the condition misleading. Simplifying to `array.size() > 0` communicates the actual intent.

:codex-file-citation[codex-file-citation]{line_range_start=162 line_range_end=163 path=app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt#L162-L163"}
:codex-file-citation[codex-file-citation]{line_range_start=132 line_range_end=137 path=app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt#L132-L137"}

:::task-stub{title="Remove redundant null check on non-null JsonArray in LoginSyncManager"}
1. In `LoginSyncManager.kt` line 163, change `if (array != null && array.size() > 0)` to `if (array.size() > 0)`
:::

### Remove unnecessary safe call on non-null `Long` in `PlanFragment`

`RealmMyTeam.createdDate` is a primitive `Long` (non-nullable). Using `?.let` on it is unnecessary and generates a compiler warning.

:codex-file-citation[codex-file-citation]{line_range_start=249 line_range_end=249 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/PlanFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/teams/PlanFragment.kt#L249-L249"}

:::task-stub{title="Remove unnecessary safe call on non-null Long in PlanFragment"}
1. In `PlanFragment.kt` line 249, change `updatedTeam.createdDate?.let { formatDate(it) }` to `formatDate(updatedTeam.createdDate)`
:::

### Replace deprecated `adapterPosition` with `bindingAdapterPosition` in `VoicesAdapter`

`RecyclerView.ViewHolder.adapterPosition` is deprecated in favor of `bindingAdapterPosition` (or `absoluteAdapterPosition`). Since this code operates within the binding adapter's context, `bindingAdapterPosition` is the correct replacement.

:codex-file-citation[codex-file-citation]{line_range_start=332 line_range_end=332 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L332-L332"}

:::task-stub{title="Replace deprecated adapterPosition with bindingAdapterPosition"}
1. In `VoicesAdapter.kt` line 332, change `holder.adapterPosition` to `holder.bindingAdapterPosition`
:::
