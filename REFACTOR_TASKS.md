# myPlanet Refactor Round — 10 Low-Risk Quick Wins

Each task is scoped to a **single file** so the 10 PRs can be reviewed/merged in one round
without conflicting with each other. Focus: performance quick wins, micro-optimizations that
unblock later refactors, and removing obvious inefficiencies — no use-cases, no Compose, no new
unused code.

Coverage map: DiffUtil/ListAdapter (1, 2) · DI / shared Gson (3, 4, 5) · data layer & RealmRepository
query pushdown (4, 5) · long-running flow observers (6, 7, 8, 9) · per-iteration allocation (3, 10).

### Replace full-list rebind in VoicesLabelAdapter with targeted selection updates

`VoicesLabelAdapter` is a `ListAdapter`, yet `setSelectedLabel` calls `notifyDataSetChanged()`, forcing every label chip to rebind on each selection change. Tracking the selected position and notifying only the previously- and newly-selected rows (with a selection payload) removes the full rebind — the same pattern `ServerAddressAdapter` already uses.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelAdapter.kt#L29-L45"}
:codex-file-citation[codex-file-citation]{line_range_start=47 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesLabelAdapter.kt#L47-L50"}

:::task-stub{title="Use targeted notifyItemChanged in VoicesLabelAdapter"}
1. Derive the adapter position of the previously- and newly-selected label from `currentList` inside `setSelectedLabel`.
2. Replace `notifyDataSetChanged()` with `notifyItemChanged(oldPos, SELECTION_PAYLOAD)` and `notifyItemChanged(newPos, SELECTION_PAYLOAD)` using a private payload constant.
3. Add an `onBindViewHolder(holder, position, payloads)` override that, when the payload is present, only updates the background/text color instead of rebinding the whole row.
4. Run `./gradlew testDefaultDebugUnitTest` and smoke-test label selection in the Voices screen.
:::

### Reuse the shared DiffUtils.itemCallback helper in EventsDescriptionAdapter

`EventsDescriptionAdapter` is the only adapter still declaring an inline `object : DiffUtil.ItemCallback` instead of the project's `DiffUtils.itemCallback` helper that every other `ListAdapter` already uses. Switching it over is a single-spot consistency change with identical diffing behavior.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=22 path=app/src/main/java/org/ole/planet/myplanet/ui/events/EventsDescriptionAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/events/EventsDescriptionAdapter.kt#L12-L22"}

:::task-stub{title="Switch EventsDescriptionAdapter to DiffUtils.itemCallback"}
1. Import `org.ole.planet.myplanet.utils.DiffUtils`.
2. Replace the inline `object : DiffUtil.ItemCallback<DescriptionItem>` with `DiffUtils.itemCallback(areItemsTheSame = { o, n -> o.key == n.key }, areContentsTheSame = { o, n -> o == n })`.
3. Remove the now-unused `androidx.recyclerview.widget.DiffUtil` import.
4. Build and run `./gradlew testDefaultDebugUnitTest`.
:::

### Stop allocating a Gson per news entry in ChatHistoryUtils

`extractSharedViewInIds` creates a new `Gson()` inside a `flatMap` that runs for every shared news entry, rebuilding Gson's type-adapter cache on each iteration. The app already exposes a shared `JsonUtils.gson` (a plain `Gson` with identical configuration), so reusing it removes the per-iteration allocation with no behavior change.

:codex-file-citation[codex-file-citation]{line_range_start=16 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/utils/ChatHistoryUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/utils/ChatHistoryUtils.kt#L16-L31"}

:::task-stub{title="Reuse JsonUtils.gson in ChatHistoryUtils"}
1. Replace `Gson().fromJson(news.viewIn, JsonArray::class.java)` with `JsonUtils.gson.fromJson(news.viewIn, JsonArray::class.java)`.
2. Drop the `com.google.gson.Gson` import if it is no longer referenced.
3. Run `./gradlew testDefaultDebugUnitTest`.
:::

### Tidy ResourcesRepositoryImpl: shared Gson + push the delete filter into the query

`saveSearchActivity` builds a fresh `Gson()` on every search, and `removeDeletedResources` loads all matching libraries then filters `!in validCurrentIds` in memory before deleting. Reusing `JsonUtils.gson` and expressing the exclusion as a Realm predicate avoids the per-call Gson and stops materializing rows that will be discarded.

:codex-file-citation[codex-file-citation]{line_range_start=317 line_range_end=335 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L317-L335"}
:codex-file-citation[codex-file-citation]{line_range_start=468 line_range_end=478 path=app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L468-L478"}

:::task-stub{title="Micro-optimize ResourcesRepositoryImpl Gson and delete query"}
1. In `saveSearchActivity`, replace `Gson().toJson(filter)` with `JsonUtils.gson.toJson(filter)`.
2. In `removeDeletedResources`, when `validCurrentIds` is non-empty add `.not().`in`("resourceId", validCurrentIds.toTypedArray())` to the existing Realm query and delete the results directly, removing the in-memory `.filter { it.resourceId !in validCurrentIds }`.
3. Keep the existing `isNotNull`/`notEqualTo`/`equalTo` predicates and explicitly preserve the empty-`validCurrentIds` behavior (delete all matching rows).
4. Run `./gradlew testDefaultDebugUnitTest --tests "*ResourcesRepositoryImplTest"`.
:::

### Tidy UserRepositoryImpl: shared Gson + tighten the user lookup query

`updateExistingUser` constructs `com.google.gson.Gson()` once per user during sync, and the health-record lookup loads users via a raw `where().in(...).findAll()` then `copyFromRealm().filter { it.id != null }`. Reusing `JsonUtils.gson` and moving the null check into the query removes the per-call Gson and the post-copy filtering.

:codex-file-citation[codex-file-citation]{line_range_start=835 line_range_end=850 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L835-L850"}
:codex-file-citation[codex-file-citation]{line_range_start=902 line_range_end=909 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L902-L909"}

:::task-stub{title="Micro-optimize UserRepositoryImpl Gson and tighten user query"}
1. In `updateExistingUser`, replace `com.google.gson.Gson()` with `JsonUtils.gson` (keep `toJsonTree(mutableObj)`).
2. In the health-record lookup, add `.isNotNull("id")` to the existing `realm.where(RealmUser::class.java).`in`("id", userIds.toTypedArray())` query.
3. Drop the trailing `.filter { it.id != null }` so the line becomes `realm.copyFromRealm(users).associateBy { it.id ?: "" }`.
4. Run `./gradlew testDefaultDebugUnitTest --tests "*UserRepositoryImplTest"`.
:::

### Gate ChatHistoryFragment flow collection to the STARTED lifecycle

`ChatHistoryFragment` collects the continuous `screenData` and `filteredChats` StateFlows inside bare `viewLifecycleOwner.lifecycleScope.launch` blocks, so collection keeps running while the view is stopped — unlike its sibling `ChatDetailFragment`, which already wraps equivalent collectors in `repeatOnLifecycle`. Routing them through the existing `collectLatestWhenStarted` helper pauses them below STARTED.

:codex-file-citation[codex-file-citation]{line_range_start=297 line_range_end=321 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L297-L321"}

:::task-stub{title="Use collectLatestWhenStarted in ChatHistoryFragment"}
1. Import `org.ole.planet.myplanet.utils.collectLatestWhenStarted`.
2. Replace `viewLifecycleOwner.lifecycleScope.launch { sharedViewModel.screenData.collect { … } }` with `collectLatestWhenStarted(sharedViewModel.screenData) { … }`.
3. Apply the same change to the `sharedViewModel.filteredChats` collector.
4. Run `./gradlew testDefaultDebugUnitTest` and smoke-test chat-history navigation.
:::

### Gate EventsDetailFragment flow collection to the STARTED lifecycle

`EventsDetailFragment` launches four separate `viewLifecycleOwner.lifecycleScope.launch` blocks that each collect a continuous StateFlow (`meetup`, `members`, `user`, `updateSuccess`) with no lifecycle gating, so all four stay active in the background. Collecting through `collectWhenStarted` keeps them bound to the view's STARTED state.

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=94 path=app/src/main/java/org/ole/planet/myplanet/ui/events/EventsDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/events/EventsDetailFragment.kt#L73-L94"}

:::task-stub{title="Use collectWhenStarted in EventsDetailFragment"}
1. Import `org.ole.planet.myplanet.utils.collectWhenStarted`.
2. Replace each `viewLifecycleOwner.lifecycleScope.launch { viewModel.X.collect { … } }` (for `meetup`, `members`, `user`, `updateSuccess`) with `collectWhenStarted(viewModel.X) { … }`.
3. Leave any non-flow one-shot `launch` blocks unchanged.
4. Run `./gradlew testDefaultDebugUnitTest`.
:::

### Gate SubmissionsFragment combined-flow collection to STARTED

`SubmissionsFragment` collects a combined submissions/exams/counts flow inside a bare `viewLifecycleOwner.lifecycleScope.launch` + `collectLatest`, keeping the Realm-backed upstream hot while the list is off-screen. The project's `collectLatestWhenStarted` helper expresses the same collector with lifecycle gating.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=67 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsFragment.kt#L60-L67"}

:::task-stub{title="Use collectLatestWhenStarted in SubmissionsFragment"}
1. Import `org.ole.planet.myplanet.utils.collectLatestWhenStarted`.
2. Replace `viewLifecycleOwner.lifecycleScope.launch { combinedFlow.collectLatest { (submissions, exams, counts) -> … } }` with `collectLatestWhenStarted(combinedFlow) { (submissions, exams, counts) -> … }`.
3. Run `./gradlew testDefaultDebugUnitTest` and verify the submissions list still refreshes after sync.
:::

### Gate CoursesFragment state collection to STARTED

`CoursesFragment` collects the continuous `coursesState` and `syncStatus` StateFlows in bare `viewLifecycleOwner.lifecycleScope.launch` blocks, so both keep emitting into the fragment while it is stopped. The `collectLatestWhenStarted` helper keeps them aligned to the view lifecycle.

:codex-file-citation[codex-file-citation]{line_range_start=151 line_range_end=181 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L151-L181"}

:::task-stub{title="Use collectLatestWhenStarted in CoursesFragment"}
1. Import `org.ole.planet.myplanet.utils.collectLatestWhenStarted`.
2. Replace `viewLifecycleOwner.lifecycleScope.launch { viewModel.coursesState.collectLatest { … } }` with `collectLatestWhenStarted(viewModel.coursesState) { … }`, preserving the existing `if (!::adapterCourses.isInitialized) return@…` guard.
3. Apply the same change to the `viewModel.syncStatus` collector.
4. Run `./gradlew testDefaultDebugUnitTest`.
:::

### Hoist the repeated protocol-stripping Regex in ServerDialogExtensions

`ServerDialogExtensions` recompiles `Regex("^https?://")` many times, including inside `find`/`filter` lambdas that iterate the saved-server list, so the pattern is rebuilt for every server on every comparison. Compiling it once into a single `val` removes the repeated allocation with no behavior change.

:codex-file-citation[codex-file-citation]{line_range_start=118 line_range_end=141 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt#L118-L141"}
:codex-file-citation[codex-file-citation]{line_range_start=170 line_range_end=197 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt#L170-L197"}

:::task-stub{title="Compile the https-prefix Regex once in ServerDialogExtensions"}
1. Add a private file-level `val httpsPrefixRegex = Regex("^https?://")`.
2. Replace every inline `Regex("^https?://")` usage with `httpsPrefixRegex`.
3. Run `./gradlew testDefaultDebugUnitTest`.
:::

### Testing

Every task is verifiable with the same suite CI runs on each push; instrumented tests are not required for any of these changes. Each PR should keep its diff to its single named file so the round stays conflict-free.

:::task-stub{title="Validate each task"}
1. Run `./gradlew testDefaultDebugUnitTest` (CI's gate) after each change.
2. For the repository tasks (4, 5), run the targeted `--tests "*ResourcesRepositoryImplTest"` / `"*UserRepositoryImplTest"` classes.
3. For the UI tasks (1, 6–9), do a quick manual smoke-test of the affected screen (selection / navigation away and back).
:::
