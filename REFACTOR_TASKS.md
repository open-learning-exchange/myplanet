### Extract DictionaryRepository from DictionaryActivity

The only UI Activity in the codebase that performs Realm queries directly. `loadDictionaryIfNeeded` and the search path both hit `realm.where(RealmDictionary::class.java)`, plus a `realm.executeTransactionAsync` block. Moving these to a new `DictionaryRepository` (extending `RealmRepository`) eliminates the last UI-layer Realm leak and lets the Activity depend on a clean suspend API.

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=115 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L55-L115"}
:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=124 path=app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/repository/RealmRepository.kt#L22-L124"}

:::task-stub{title="Move RealmDictionary access from DictionaryActivity into a new DictionaryRepository"}
1. Add `DictionaryRepository` interface and `DictionaryRepositoryImpl` (extending `RealmRepository`) under `repository/` with `suspend fun isEmpty(): Boolean`, `suspend fun upsertEntries(entries: List<DictionaryEntry>)`, and `suspend fun findByWord(word: String): RealmDictionary?`.
2. Bind the implementation in `di/RepositoryModule.kt` next to the existing `@Binds` entries.
3. Replace the `realm.where(RealmDictionary::class.java)...` reads at lines 59, 101, and 108 of `DictionaryActivity.kt` with repository calls inside `lifecycleScope.launch`.
4. Replace the `realm.executeTransactionAsync { ... createObject(RealmDictionary::class.java, ...) }` block with a single `dictionaryRepository.upsertEntries(...)` call.
5. Remove the now-unused `Realm` field and `RealmDictionary` import from `DictionaryActivity`.
:::

### Convert StorageCategoryDetailFragment.ResourceAdapter to ListAdapter

`StorageCategoryDetailFragment` declares an inner `RecyclerView.Adapter<...>` and pushes updates with two `notifyDataSetChanged()` calls. The project already ships `utils/DiffUtils.kt` with an `itemCallback<T>()` helper used elsewhere; migrating this adapter is a single-file change and removes a wholesale rebind on every filter toggle.

:codex-file-citation[codex-file-citation]{line_range_start=248 line_range_end=279 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L248-L279"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt#L1-L42"}

:::task-stub{title="Migrate StorageCategoryDetailFragment.ResourceAdapter to ListAdapter with DiffUtils.itemCallback"}
1. Change `ResourceAdapter` to `ListAdapter<ResourceItem, ResourceAdapter.ViewHolder>(DiffUtils.itemCallback { it.id })` (use whatever stable id field the resource row uses).
2. Delete the manual `items` MutableList and the inner `notifyDataSetChanged()` calls at lines 113 and 152; replace with `adapter.submitList(newList)`.
3. Drop `getItemCount` / `onBindViewHolder` index access in favor of `getItem(position)`.
4. Verify the fragment compiles without the `lateinit var adapter: ResourceAdapter` field needing other changes.
:::

### Route Constants.kt feature-flag accessors through SharedPrefManager

`Constants.kt` has three top-level helpers (`showBetaFeature`, `isBetaWifiFeatureEnabled`, `autoSynFeature`) that each open `PreferenceManager.getDefaultSharedPreferences(context)`. The codebase already has `SharedPrefManager` as the canonical wrapper; threading these through it removes ad-hoc preference reads from a utility file imported across the app.

:codex-file-citation[codex-file-citation]{line_range_start=70 line_range_end=96 path=app/src/main/java/org/ole/planet/myplanet/utils/Constants.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/utils/Constants.kt#L70-L96"}

:::task-stub{title="Move beta-feature flag reads from Constants.kt into SharedPrefManager"}
1. Add `fun isBetaFeatureEnabled(key: String, default: Boolean): Boolean` (and a typed accessor for the auto-sync flag) on `SharedPrefManager`.
2. Replace the three `PreferenceManager.getDefaultSharedPreferences(context)` blocks at lines 72–89 of `Constants.kt` with calls to the corresponding `SharedPrefManager` accessors.
3. Update call sites of `showBetaFeature` / `isBetaWifiFeatureEnabled` / `autoSynFeature` to pass `SharedPrefManager` (or fetch it via the existing `CoreDependenciesEntryPoint` if a `Context`-only call site exists).
4. Drop the now-unused `import androidx.preference.PreferenceManager` from `Constants.kt`.
:::

### Inject SharedPreferences into OfflineMapsActivity

`OfflineMapsActivity` is the only `osmdroid` consumer reaching for `PreferenceManager.getDefaultSharedPreferences(this)` directly. A one-line Hilt injection swaps that for the singleton instance already provided by `SharedPreferencesModule`.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=39 path=app/src/main/java/org/ole/planet/myplanet/ui/maps/OfflineMapsActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/ui/maps/OfflineMapsActivity.kt#L1-L39"}

:::task-stub{title="Inject Hilt SharedPreferences into OfflineMapsActivity"}
1. Annotate `OfflineMapsActivity` with `@AndroidEntryPoint`.
2. Add `@Inject lateinit var sharedPreferences: SharedPreferences`.
3. Replace `PreferenceManager.getDefaultSharedPreferences(this)` at line 17 with the injected `sharedPreferences`.
4. Remove the `import androidx.preference.PreferenceManager` line.
:::

### Inject SharedPreferences into SyncActivity preference load

`SyncActivity` reaches for `PreferenceManager.getDefaultSharedPreferences(applicationContext)` in `onCreate` even though it already extends an `@AndroidEntryPoint` activity hierarchy and other singletons are injected. Switching to Hilt-injected prefs aligns this with the rest of the activity and is local to a single line.

:codex-file-citation[codex-file-citation]{line_range_start=115 line_range_end=125 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L115-L125"}

:::task-stub{title="Replace PreferenceManager call in SyncActivity with injected SharedPreferences"}
1. Add `@Inject lateinit var sharedPreferences: SharedPreferences` to `SyncActivity` (Hilt is already in scope).
2. Replace `PreferenceManager.getDefaultSharedPreferences(applicationContext)` at line 118 with the injected field.
3. Remove the now-unused `import androidx.preference.PreferenceManager` if no other caller in the file uses it.
:::

### Resolve DownloadWorker SharedPreferences via Hilt EntryPoint

`DownloadWorker` is a `CoroutineWorker` constructed by WorkManager, so it cannot use constructor injection — but the codebase already has Hilt EntryPoints for this exact case. Fetching `SharedPreferences` via `CoreDependenciesEntryPoint` removes the last manual `context.getSharedPreferences(...)` call in `services/`.

:codex-file-citation[codex-file-citation]{line_range_start=30 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt#L30-L45"}

:::task-stub{title="Use CoreDependenciesEntryPoint to obtain SharedPreferences in DownloadWorker"}
1. Add a `sharedPreferences()` accessor to `CoreDependenciesEntryPoint` if not already exposed.
2. In `DownloadWorker`, replace the `context.getSharedPreferences(DownloadService.PREFS_NAME, Context.MODE_PRIVATE)` field at line 35 with `EntryPointAccessors.fromApplication(context, CoreDependenciesEntryPoint::class.java).sharedPreferences()`.
3. Verify no other code in `DownloadWorker` still relies on the named `DownloadService.PREFS_NAME` file; if it does, leave the legacy path and instead inject a thin wrapper.
:::

### Move MainApplication.createLog into an ApkLogRepository

`MainApplication.createLog` opens an `executeTransactionAsync` and writes `RealmApkLog` rows directly from the Application singleton. The work belongs behind a repository so crash/ANR pipelines do not depend on `MainApplication` and the Realm transaction is testable.

:codex-file-citation[codex-file-citation]{line_range_start=117 line_range_end=145 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L117-L145"}

:::task-stub{title="Extract MainApplication.createLog into ApkLogRepository"}
1. Create `ApkLogRepository` interface plus `ApkLogRepositoryImpl` (extending `RealmRepository`) with `suspend fun log(type: String, error: String = "")`.
2. Inject `UserSessionManager` and `SharedPrefManager` into the impl; move the `parentCode` / `planetCode` / `userId` / `time` / `version` population from `createLog` (lines 128–139) into the repository.
3. Bind the new repository in `di/RepositoryModule.kt`.
4. Change `MainApplication.createLog` to delegate via `EntryPointAccessors.fromApplication(...).apkLogRepository().log(type, error)`; keep the static signature so existing callers (`handleUncaughtException`, ANR watchdog, foreground hooks) compile unchanged.
5. Delete the `databaseService.executeTransactionAsync { r -> ... }` block and the `RealmApkLog` import from `MainApplication.kt`.
:::

### Inject SharedPreferences into SurveysRepositoryImpl

`SurveysRepositoryImpl` opens `context.getSharedPreferences(PREF_SURVEY_REMINDERS, Context.MODE_PRIVATE)` twice on initialization, even though Hilt could inject a named `SharedPreferences` once at construction. This is a one-file repository cleanup, no behavioral change.

:codex-file-citation[codex-file-citation]{line_range_start=38 line_range_end=55 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L38-L55"}

:::task-stub{title="Replace context.getSharedPreferences in SurveysRepositoryImpl with a Hilt-provided instance"}
1. Add a `@Provides @Named("survey_reminders")` `SharedPreferences` in `SharedPreferencesModule` keyed on `PREF_SURVEY_REMINDERS`.
2. Add the matching `@Named("survey_reminders") private val reminderPrefs: SharedPreferences` constructor parameter to `SurveysRepositoryImpl`.
3. Replace the two `context.getSharedPreferences(PREF_SURVEY_REMINDERS, ...)` reads at lines 43 and 48 with the injected `reminderPrefs`.
4. Remove the now-unused `Context` field if it has no other consumers in the class.
:::

### Move RealmMyCourse.insertMyCourses into CoursesRepositoryImpl

`RealmMyCourse.kt` exposes a static `insertMyCourses(userId, json, mRealm, spm)` that the sync layer (`CoursesRepositoryImpl.kt:521`) calls inside its own transaction. Inlining the mutation into `CoursesRepositoryImpl` (which already extends `RealmRepository`) decouples the model from `SharedPrefManager` and removes a `companion object` data-layer leak.

:codex-file-citation[codex-file-citation]{line_range_start=64 line_range_end=180 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/model/RealmMyCourse.kt#L64-L180"}
:codex-file-citation[codex-file-citation]{line_range_start=515 line_range_end=530 path=app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L515-L530"}

:::task-stub{title="Move RealmMyCourse.insertMyCourses into CoursesRepositoryImpl"}
1. Add `private fun insertMyCourse(shelfId: String, doc: JsonObject, realmTx: Realm, spm: SharedPrefManager)` to `CoursesRepositoryImpl` containing the body currently in `RealmMyCourse.insertMyCourses` (lines 68–142 of `RealmMyCourse.kt`).
2. Update the call site at `CoursesRepositoryImpl.kt:521` to call the new private function.
3. Delete `RealmMyCourse.insertMyCourses` and the public `RealmMyCourse.insert(...)` wrapper at lines 171–180.
4. Confirm via `grep -r 'RealmMyCourse.insertMyCourses\|RealmMyCourse\.insert('` that no other callers exist before deleting; keep the helpers `insertExam`, `insertSurvey`, `insertCourseStepsAttachments` private to the repo as well.
:::

### Tighten ProgressRepositoryImpl: take courseIds instead of querying RealmMyCourse

`ProgressRepositoryImpl` opens its own `RealmMyCourse` queries to derive progress, which crosses domain boundaries (courses → progress) inside the progress repository. Accepting a `List<String>` of course IDs from the caller leaves course ownership in `CoursesRepository` and keeps the progress repo focused on `RealmCourseProgress` / `RealmCourseStep`.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=65 path=app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-qZ0ob/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L28-L65"}

:::task-stub{title="Drop RealmMyCourse reads from ProgressRepositoryImpl by accepting courseIds"}
1. Change the offending function in `ProgressRepository` (around line 31–59 of `ProgressRepositoryImpl.kt`) to accept `courseIds: List<String>` instead of fetching `RealmMyCourse` internally.
2. Update the implementation to query only `RealmCourseProgress` / `RealmCourseStep` filtered by the provided ids using `RealmRepository` helpers.
3. Update the single ViewModel / repository caller (likely in `DashboardViewModel` or `ProgressViewModel`) to source course ids via `CoursesRepository` and pass them in.
4. Remove the `RealmMyCourse` import from `ProgressRepositoryImpl.kt`.
:::

### Testing

For each refactor: build both flavors with `./gradlew assembleDefaultDebug assembleLiteDebug` and exercise the affected screen manually (dictionary search, settings → storage detail, offline maps, sync, downloads, dashboard progress, survey reminders). No new automated tests are required; the changes are intentionally behavior-preserving.
