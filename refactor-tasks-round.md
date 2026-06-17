# Refactor Round — 10 Low-Risk, Reviewable Tasks

Scope this round: reinforce repository boundaries, tighten leaky repository interfaces, move
stray data functions out of UI into repositories, plus a few granular DI / dispatcher / DiffUtil
wins. Every task is a single small PR. No use cases, no Compose, no new abstractions, no unused code.

**Merge-safety (read first):** 8 of the 10 tasks touch fully disjoint files and can merge in any
order. The only overlap is **Task 2** and **Task 3**, which both edit `DashboardViewModel.kt` (in
different regions). Assign those two to the same reviewer / merge them back-to-back, or split them
across two rounds.

**Findings that are deliberately NOT tasks this round:** the `bulkInsertFromSync(realm, …)` family
leaks `io.realm.Realm` through ~13 repository interfaces, but every one is called from the single
shared `TransactionSyncManager` inside one batched transaction — converting them would collide
across all PRs and change transaction semantics, so it is out of scope here. Long-running
observers/listeners were reviewed (`DashboardActivity` tab listener + receiver, `RealmRepository`
change listener) and are already balanced/removed — no action needed.

---

### Move dictionary database logic out of DictionaryActivity into a repository

`DictionaryActivity` is the only UI class still injecting `DatabaseService` and running raw Realm
queries/transactions (count, bulk insert, word lookup) directly in the Activity. Moving this into a
small `DictionaryRepository` (extending `RealmRepository`) restores the layer boundary and is the
clearest "data function in UI" to relocate.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=29 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L28-L29"}
:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=135 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L60-L135"}

:::task-stub{title="Move DictionaryActivity Realm logic into a new DictionaryRepository"}
1. Create `repository/DictionaryRepository.kt` with suspend functions `getDictionaryCount(): Long`, `loadDictionaryIfNeeded(jsonArray: JsonArray)`, and `findWord(word: String): RealmDictionary?`.
2. Create `repository/DictionaryRepositoryImpl.kt` extending `RealmRepository`, moving the body from `DictionaryActivity` lines 60-135 and using `withRealmAsync`/`executeTransaction`; return a detached copy from `findWord`.
3. Add a single `@Binds` for it in `di/RepositoryModule.kt`.
4. In `DictionaryActivity`, replace the `@Inject DatabaseService` (lines 28-29) with `@Inject DictionaryRepository` and call the new methods; keep the displayed count text and search HTML identical.
5. Add `DictionaryRepositoryImplTest` mirroring existing repo tests and run `./gradlew testDefaultDebugUnitTest`.
:::

---

### Tighten UserRepository.getUsersSortedBy so it stops leaking io.realm.Sort

The repository interface exposes `getUsersSortedBy(fieldName, sortOrder: io.realm.Sort)`, forcing
the `DashboardViewModel` and `MyHealthFragment` to import a Realm type and pass Realm sort semantics
across the boundary. Swapping the parameter for a domain-level `descending: Boolean` keeps Realm
confined to the implementation.

:codex-file-citation[codex-file-citation]{line_range_start=5 line_range_end=5 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L5-L5"}
:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=26 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt#L26-L26"}
:codex-file-citation[codex-file-citation]{line_range_start=201 line_range_end=201 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L201-L201"}
:codex-file-citation[codex-file-citation]{line_range_start=269 line_range_end=307 path=app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L269-L307"}

:::task-stub{title="Replace io.realm.Sort parameter on UserRepository.getUsersSortedBy with a domain Boolean"}
1. In `UserRepository.kt`, change the signature at line 26 to `getUsersSortedBy(fieldName: String, descending: Boolean)` and remove `import io.realm.Sort` (line 5).
2. In `UserRepositoryImpl.kt` (around line 145) map `descending` to `io.realm.Sort.DESCENDING`/`ASCENDING` internally.
3. Update callers `DashboardViewModel.kt` line 201 (and remove its `import io.realm.Sort`, line 7) and `MyHealthFragment.kt` lines 269 and 307 to pass the boolean.
4. Update `UserRepositoryImplTest` and run `./gradlew testDefaultDebugUnitTest`. (Note: edits `DashboardViewModel.kt` — coordinate with Task 3.)
:::

---

### Stop DashboardViewModel reaching into CoursesProgressFragment for progress parsing

`DashboardViewModel.evaluateChallengeDialog` calls the static
`CoursesProgressFragment.getCourseProgress(...)` — a ViewModel depending on another feature's
Fragment for a pure data computation. It is a cross-feature leak, and that companion has no other
callers, so it can move cleanly into the progress domain.

:codex-file-citation[codex-file-citation]{line_range_start=317 line_range_end=317 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L317-L317"}
:codex-file-citation[codex-file-citation]{line_range_start=46 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressFragment.kt#L46-L56"}

:::task-stub{title="Move CoursesProgressFragment.getCourseProgress into ProgressRepository"}
1. Add a pure (no-Realm) function to `ProgressRepository`/`ProgressRepositoryImpl`, e.g. `findProgressForCourse(courseData: JsonArray, courseId: String): JsonObject?`, copying the body from `CoursesProgressFragment` lines 46-56.
2. In `DashboardViewModel.kt` line 317, call the already-injected `progressRepository` instead of `org.ole.planet.myplanet.ui.courses.CoursesProgressFragment.getCourseProgress(...)`.
3. Delete the now-unused `getCourseProgress` companion from `CoursesProgressFragment.kt`.
4. Add a small unit test for the moved function and run `./gradlew testDefaultDebugUnitTest`. (Note: edits `DashboardViewModel.kt` — coordinate with Task 2.)
:::

---

### Pass SharedPrefManager into MyPlanet.getTabletUsages instead of service-locating it

`MyPlanet.getTabletUsages` resolves a Hilt `EntryPoint` to fetch `SharedPrefManager` mid-method,
even though its sibling `getMyPlanetActivities` already receives `spm` as a parameter and the other
caller (`ActivitiesRepositoryImpl`) already injects it. Threading the dependency through removes the
service-locator and makes the function testable.

:codex-file-citation[codex-file-citation]{line_range_start=65 line_range_end=79 path=app/src/main/java/org/ole/planet/myplanet/model/MyPlanet.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/model/MyPlanet.kt#L65-L79"}
:codex-file-citation[codex-file-citation]{line_range_start=487 line_range_end=494 path=app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L487-L494"}

:::task-stub{title="Inject SharedPrefManager into MyPlanet.getTabletUsages rather than EntryPointAccessors"}
1. Change `getTabletUsages(context: Context)` (line 66) to `getTabletUsages(context: Context, spm: SharedPrefManager)` and delete the in-method `EntryPointAccessors` lookup (line 68).
2. In `getMyPlanetActivities` (line 42) pass the `spm` it already receives.
3. In `ActivitiesRepositoryImpl.kt` line 491 pass the already-injected `sharedPrefManager`.
4. Remove the now-unused `EntryPointAccessors` and `CoreDependenciesEntryPoint` imports from `MyPlanet.kt`; build and run `./gradlew testDefaultDebugUnitTest`.
:::

---

### Cache the Hilt entry point in UrlUtils instead of rebuilding it on every call

`UrlUtils.spm()` calls `EntryPointAccessors.fromApplication(...)` on every access, and the
`header`/`hostUrl` getters call it repeatedly on networking hot paths. Resolving the
`SharedPrefManager` once removes redundant entry-point construction without changing behavior.

:codex-file-citation[codex-file-citation]{line_range_start=11 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt#L11-L20"}

:::task-stub{title="Resolve SharedPrefManager once in UrlUtils via a lazy field"}
1. In `UrlUtils.kt`, replace the per-call `spm()` (lines 12-13) with `private val spm: SharedPrefManager by lazy { EntryPointAccessors.fromApplication(context, CoreDependenciesEntryPoint::class.java).sharedPrefManager() }`.
2. Update `header`, `hostUrl`, and any other `spm()` callers to read the cached `spm` (cache the manager, not the credential values, which stay read-live).
3. Build and run `./gradlew testDefaultDebugUnitTest`.
:::

---

### Cache the Hilt entry point in ThemeManager instead of resolving it per call

`ThemeManager.getSpm(context)` rebuilds the entry point on each `getCurrentThemeMode`/`setThemeMode`
call. As an `object`, it can memoize the resolved `SharedPrefManager` on first use against the
application context.

:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=15 path=app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt#L13-L15"}
:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=51 path=app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt#L47-L51"}

:::task-stub{title="Memoize SharedPrefManager in ThemeManager"}
1. In `ThemeManager.kt`, add `private var spm: SharedPrefManager? = null` and have `getSpm(context)` return the cached value or resolve once via `EntryPointAccessors` against `context.applicationContext`.
2. Leave `getCurrentThemeMode`/`setThemeMode` (lines 47-51) unchanged otherwise.
3. Build and run `./gradlew testDefaultDebugUnitTest`.
:::

---

### Use the injected DispatcherProvider in BaseRecyclerFragment.deleteCourseProgress

`BaseRecyclerFragment` already injects `dispatcherProvider`, but `deleteCourseProgress` still
hard-codes `Dispatchers.IO`. Using the injected provider keeps threading consistent and testable.

:codex-file-citation[codex-file-citation]{line_range_start=211 line_range_end=217 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L211-L217"}

:::task-stub{title="Replace hard-coded Dispatchers.IO with dispatcherProvider.io in BaseRecyclerFragment"}
1. At line 213, change `launch(kotlinx.coroutines.Dispatchers.IO)` to `launch(dispatcherProvider.io)` using the already-injected `dispatcherProvider`.
2. Drop the now-unused fully-qualified `Dispatchers.IO` reference/import.
3. Build and run `./gradlew testDefaultDebugUnitTest`.
:::

---

### Inject DispatcherProvider into ChatHistoryAdapter instead of hard-coded Dispatchers

`ChatHistoryAdapter.search` hard-codes `Dispatchers.Default` and `Dispatchers.Main`. The adapter has
a single construction site (`ChatHistoryFragment`), so a constructor parameter threads the injected
provider through cleanly.

:codex-file-citation[codex-file-citation]{line_range_start=136 line_range_end=147 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt#L136-L147"}
:codex-file-citation[codex-file-citation]{line_range_start=266 line_range_end=272 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L266-L272"}

:::task-stub{title="Thread DispatcherProvider into ChatHistoryAdapter.search"}
1. Add a `dispatcherProvider: DispatcherProvider` constructor parameter to `ChatHistoryAdapter` (line 34).
2. In `search()` (lines 136-147) replace `withContext(Dispatchers.Default)` with `withContext(dispatcherProvider.default)` and `withContext(Dispatchers.Main)` with `withContext(dispatcherProvider.main)`; remove the `kotlinx.coroutines.Dispatchers` import (line 30).
3. At the single construction site `ChatHistoryFragment.kt` (lines 266-272) pass the fragment's injected `dispatcherProvider`.
4. Build and run `./gradlew testDefaultDebugUnitTest`.
:::

---

### Replace ResourcesAdapter's hand-written DiffUtil.ItemCallback with DiffUtils.itemCallback

`ResourcesAdapter` defines an anonymous `DiffUtil.ItemCallback` by hand while the rest of the app
uses the shared `DiffUtils.itemCallback(...)` factory. Converting it removes boilerplate and matches
the codebase convention; the payload logic is preserved verbatim.

:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=68 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt#L41-L68"}

:::task-stub{title="Convert ResourcesAdapter ITEM_CALLBACK to DiffUtils.itemCallback"}
1. Add `import org.ole.planet.myplanet.utils.DiffUtils`.
2. Replace the anonymous `androidx.recyclerview.widget.DiffUtil.ItemCallback<ResourceListModel>()` (lines 48-67) with `DiffUtils.itemCallback(areItemsTheSame = …, areContentsTheSame = …, getChangePayload = …)`, keeping the existing `OPENED_RESOURCE_PAYLOAD`/`OFFLINE_STATUS_PAYLOAD` logic and the `ITEM_CALLBACK` name.
3. Leave `onBindViewHolder(..., payloads)` untouched so payload handling is unchanged.
4. Build `:app:assembleDefaultDebug` and run `./gradlew testDefaultDebugUnitTest`.
:::

---

### Replace StorageCategoryDetailFragment's ResourceDiffCallback with DiffUtils.itemCallback

The inner `ResourceAdapter` uses a bespoke `ResourceDiffCallback` class; folding it into the shared
`DiffUtils.itemCallback(...)` factory deletes a class and aligns with every other adapter.

:codex-file-citation[codex-file-citation]{line_range_start=258 line_range_end=260 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L258-L260"}
:codex-file-citation[codex-file-citation]{line_range_start=297 line_range_end=312 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/dba6b4ebfb407f9f77b78edde6bebdb68297e920/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L297-L312"}

:::task-stub{title="Inline StorageCategoryDetailFragment ResourceDiffCallback via DiffUtils.itemCallback"}
1. Add `import org.ole.planet.myplanet.utils.DiffUtils`.
2. Change the `ResourceAdapter` declaration (line 260) to pass `DiffUtils.itemCallback(areItemsTheSame = { o, n -> o.resourceId == n.resourceId }, areContentsTheSame = { o, n -> o == n }, getChangePayload = { o, n -> if (o.copy(isChecked = n.isChecked) == n) true else null })`.
3. Delete the unused `ResourceDiffCallback` class (lines 297-312).
4. Build `:app:assembleDefaultDebug` and run `./gradlew testDefaultDebugUnitTest`.
:::

---

### Testing

CI enforces `./gradlew testDefaultDebugUnitTest` (default flavor) on every push. Per task: Tasks 5
and 6 update/add repository tests (`DictionaryRepositoryImplTest`, `UserRepositoryImplTest`); Task 3
adds a small parser unit test for the moved function; Tasks 1, 2, 7, 8, 9, 10 are behavior-preserving
and only need the existing suite plus a `:app:assembleDefaultDebug` build to confirm the adapters /
fragments still compile and render. Run `./gradlew testLiteDebugUnitTest` locally if a change touches
flavor-specific paths (none of these do).
