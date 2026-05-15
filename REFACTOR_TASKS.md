### Convert StorageCategoryDetailFragment.ResourceAdapter to ListAdapter

The inner `ResourceAdapter` extends `RecyclerView.Adapter` and refreshes via `notifyDataSetChanged()` after every storage filter change, re-binding every row instead of diffing. Swapping to `ListAdapter` with the existing `DiffUtils.itemCallback` helper removes the full rebind and is fully contained to one file.

:codex-file-citation[codex-file-citation]{line_range_start=248 line_range_end=272 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L248-L272"}
:codex-file-citation[codex-file-citation]{line_range_start=113 line_range_end=152 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L113-L152"}

:::task-stub{title="Migrate StorageCategoryDetailFragment.ResourceAdapter to ListAdapter with DiffUtils.itemCallback"}
1. In `StorageCategoryDetailFragment.kt`, change the inner `ResourceAdapter` to extend `ListAdapter<ResourceItem, ResourceAdapter.ViewHolder>` and pass `DiffUtils.itemCallback<ResourceItem> { it.id }` (or the appropriate stable key) as the diff callback.
2. Remove the manual `items: MutableList<ResourceItem>` field and any `getItemCount` override; rely on `getItem(position)` inside `onBindViewHolder`.
3. Replace the two `adapter.notifyDataSetChanged()` call sites (around L113 and L152) with `adapter.submitList(newList.toList())`.
4. Verify build + smoke test the storage detail screen filter actions still update the list.
:::

### Add Glide .override() sizing to VoicesAdapter image loads

`VoicesAdapter` issues 5+ unsized `Glide.with(...).load(...).into(...)` calls for news thumbnails, library images and zoomable previews, which decodes images at their native resolution and inflates memory cache pressure on long news feeds. Adding `.override(targetW, targetH)` is a localized perf win with no API surface change.

:codex-file-citation[codex-file-citation]{line_range_start=748 line_range_end=807 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L748-L807"}
:codex-file-citation[codex-file-citation]{line_range_start=830 line_range_end=866 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L830-L866"}

:::task-stub{title="Add Glide .override() sizing to VoicesAdapter image requests"}
1. In `VoicesAdapter.kt`, add a small private helper `loadGlideImage(file: File, target: ImageView, size: Int)` that wraps `Glide.with(target).load(file).override(size, size).into(target)` (preserve any existing placeholders/transforms).
2. Replace the inline Glide requests in `loadSingleImage`, `addImageToContainer`, `loadLibraryImage`, `addLibraryImageToContainer` with calls to the helper using the existing layout target size (or a resource dimen).
3. Leave `showZoomableImage` at full size or pass a larger explicit `.override(...)` value matching the dialog dimensions; do not introduce unused overloads.
4. Rebuild and scroll the Voices/News feed to confirm thumbnails still render.
:::

### Bound the unbounded user cache in VoicesAdapter

`VoicesAdapter` keeps `userCache` and `fetchingUserIds` as plain `MutableMap`s that grow for the adapter's entire lifetime and are never cleared when the recycler view detaches, which leaks Realm-derived view models on long-lived dashboards.

:codex-file-citation[codex-file-citation]{line_range_start=143 line_range_end=144 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L143-L144"}
:codex-file-citation[codex-file-citation]{line_range_start=309 line_range_end=325 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt#L309-L325"}

:::task-stub{title="Cap VoicesAdapter user cache and clear it on detach"}
1. Replace `private val userCache = mutableMapOf<...>()` with a bounded `LinkedHashMap` (e.g. `object : LinkedHashMap<String, User>(64, 0.75f, true) { override fun removeEldestEntry(e) = size > 128 }`).
2. Override `onDetachedFromRecyclerView(recyclerView)` to call `userCache.clear()` and `fetchingUserIds.clear()`.
3. Do not change any read/write call sites; keep the public API of the adapter identical.
:::

### Inject DispatcherProvider in ChatDetailFragment and remove TextWatcher leak

`ChatDetailFragment` hardcodes `Dispatchers.IO` for JSON parsing and never removes `messageTextWatcher` from the EditText, so a recreated fragment will keep firing the old listener against a destroyed binding. Both fixes are local to one file.

:codex-file-citation[codex-file-citation]{line_range_start=188 line_range_end=207 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt#L188-L207"}

:::task-stub{title="Use injected DispatcherProvider in ChatDetailFragment and detach its TextWatcher"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to `ChatDetailFragment` (it is already `@AndroidEntryPoint`).
2. Replace the `withContext(Dispatchers.IO)` at L207 with `withContext(dispatcherProvider.io)`.
3. In `onDestroyView()` (add it if absent), call `binding.editGchatMessage.removeTextChangedListener(messageTextWatcher)` before `_binding = null`.
4. No other behavioral changes.
:::

### Inject DispatcherProvider in BecomeMemberActivity

Three `withContext(Dispatchers.Main)` switches in `BecomeMemberActivity` keep the activity coupled to concrete dispatchers, blocking later test/inject improvements. The change is mechanical.

:codex-file-citation[codex-file-citation]{line_range_start=150 line_range_end=265 path=app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt#L150-L265"}

:::task-stub{title="Replace hardcoded Dispatchers.Main in BecomeMemberActivity with injected DispatcherProvider"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to `BecomeMemberActivity` (annotate with `@AndroidEntryPoint` if not already).
2. Replace the three `withContext(Dispatchers.Main)` calls (around L154, L213, L264) with `withContext(dispatcherProvider.main)`.
3. Remove the now-unused `import kotlinx.coroutines.Dispatchers` if no other references remain.
:::

### Inject DispatcherProvider in DictionaryActivity

Dictionary loading runs on a hardcoded `Dispatchers.IO`; switching to the injected provider unblocks future test seams and keeps threading consistent with the rest of the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=70 path=app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt#L60-L70"}

:::task-stub{title="Use injected DispatcherProvider in DictionaryActivity"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` (and `@AndroidEntryPoint` if missing) to `DictionaryActivity`.
2. Replace `withContext(Dispatchers.IO)` at L64 with `withContext(dispatcherProvider.io)`.
3. Remove the now-unused `kotlinx.coroutines.Dispatchers` import if applicable.
:::

### Inject DispatcherProvider in LifeFragment

`LifeFragment` performs two repository writes inside hardcoded `Dispatchers.IO` blocks. Threading those through `DispatcherProvider` is a tiny isolated edit that touches no other file.

:codex-file-citation[codex-file-citation]{line_range_start=50 line_range_end=70 path=app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L50-L70"}

:::task-stub{title="Use injected DispatcherProvider in LifeFragment"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to `LifeFragment`.
2. Replace both `withContext(Dispatchers.IO)` invocations (around L53 and L67) with `withContext(dispatcherProvider.io)`.
3. Remove the now-unused `Dispatchers` import if applicable.
:::

### Inject DispatcherProvider in SyncActivity

`SyncActivity` launches sync and file work on hardcoded `Dispatchers.IO` in three spots. Wiring `DispatcherProvider` here is a self-contained file edit and keeps the sync entrypoint consistent with the rest of the threading conventions.

:codex-file-citation[codex-file-citation]{line_range_start=525 line_range_end=560 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L525-L560"}
:codex-file-citation[codex-file-citation]{line_range_start=760 line_range_end=770 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt#L760-L770"}

:::task-stub{title="Use injected DispatcherProvider in SyncActivity"}
1. Add `@Inject lateinit var dispatcherProvider: DispatcherProvider` to `SyncActivity`.
2. Replace the three `lifecycleScope.launch(Dispatchers.IO)` / `withContext(Dispatchers.IO)` call sites (around L529, L553, L764) with the corresponding `dispatcherProvider.io` form.
3. Remove the unused `Dispatchers` import if applicable.
:::

### Cache SharedPreferences in SurveysRepositoryImpl

`SurveysRepositoryImpl` calls `context.getSharedPreferences(PREF_SURVEY_REMINDERS, MODE_PRIVATE)` five times across reminder methods, repeatedly hitting `SharedPreferencesImpl`'s lookup and lock. A `by lazy` field collapses all five into one cached instance with no behavioral change.

:codex-file-citation[codex-file-citation]{line_range_start=405 line_range_end=465 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L405-L465"}

:::task-stub{title="Cache SurveysRepositoryImpl reminder SharedPreferences in a lazy field"}
1. In `SurveysRepositoryImpl.kt`, add `private val surveyPrefs: SharedPreferences by lazy { context.getSharedPreferences(PREF_SURVEY_REMINDERS, Context.MODE_PRIVATE) }`.
2. Replace the five inline `context.getSharedPreferences(PREF_SURVEY_REMINDERS, Context.MODE_PRIVATE)` calls (around L409, L443, L451, L458, L463) with `surveyPrefs`.
3. Keep all method signatures and behavior identical; verify the file still compiles.
:::

### Use RealmRepository.executeTransaction in EventsRepositoryImpl.batchInsertMeetups

`EventsRepositoryImpl.batchInsertMeetups` opens a Realm via `withRealm { realm.executeTransaction(...) }` instead of using the base `executeTransaction { realm -> ... }` already inherited from `RealmRepository`. Aligning it removes a nested Realm instance and matches the rest of the file (L90 already does this correctly).

:codex-file-citation[codex-file-citation]{line_range_start=67 line_range_end=90 path=app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/claude/refactor-data-navigation-43fps/app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt#L67-L90"}

:::task-stub{title="Use RealmRepository.executeTransaction in EventsRepositoryImpl.batchInsertMeetups"}
1. In `EventsRepositoryImpl.kt`, replace the `withRealm { realm.executeTransaction { realmTx -> ... } }` body of `batchInsertMeetups` with `executeTransaction { realm -> ... }` from the inherited `RealmRepository`.
2. Rename the inner lambda parameter to match the rest of the file's style and remove the now-redundant `withRealm` import if it is no longer referenced elsewhere in the file.
3. Confirm there are no behavioral changes: the same insert/update logic must run inside one transaction.
:::
