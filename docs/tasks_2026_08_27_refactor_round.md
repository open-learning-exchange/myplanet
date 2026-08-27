date: 2026-08-27
base commit: 45bac8d0050286289dbb8fa9680864a16758be5f
open PRs checked: 16274, 16270, 16258, 16257, 16192, 16101, 16096, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

### 1. suppress deprecated `android.enableJetifier` AGP warning (roadmap 8)
context: The `myPlanet test` and `myPlanet release` workflow logs both emit `WARNING: The option setting 'android.enableJetifier=true' is deprecated.` AGP itself recommends adding `android.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE` to `gradle.properties`. The project still needs `android.enableJetifier=true` for legacy libraries, so we only silence the warning.
evidence: `gradle.properties:25-26` (`android.enableJetifier=true`)
files: `gradle.properties` (add one property below `android.enableJetifier=true`). Leave `app/build.gradle`, `settings.gradle`, and `AndroidManifest.xml` untouched.
steps:
  1. Add `android.sync.suppressAgpWarnings=UNSUPPORTED_PROJECT_OPTION_USE` on a new line next to `android.enableJetifier=true`.
  2. Keep `android.enableJetifier=true` exactly as-is; do not remove it.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the next CI run of `myPlanet test` or `myPlanet release` no longer prints `The option setting 'android.enableJetifier=true' is deprecated`.
size budget: ~2 changed lines, 1 file
out of scope: removing `android.enableJetifier`, migrating legacy support libraries, or touching `AndroidManifest.xml`.

---

### 2. replace indexed `JsonArray` loops and `printStackTrace` in `SyncRepositoryImpl` (roadmap 1+5+8)
context: `SyncRepositoryImpl.kt` still walks Gson `JsonArray`s with `for (i in 0 until array.size())` at `139-144` and `for (j in 0 until responseRows.size())` at `185-192`, and it swallows exceptions with `e.printStackTrace()` at `124-125` and `212-213`. `JsonArray` is iterable, so manual indexing is unnecessary, and `Log.e` makes shelf-sync failures visible in logcat.
evidence: `SyncRepositoryImpl.kt:139-144` (`for (i in 0 until array.size())`), `SyncRepositoryImpl.kt:185-192` (`for (j in 0 until responseRows.size())`), `SyncRepositoryImpl.kt:124-125` and `212-213` (`e.printStackTrace()`)
files: `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt` (`processShelfParallel`, `processShelfDataOptimizedSync`). Do not touch `TransactionSyncManager.kt`, `SyncManager.kt`, or the API/DAO layers.
steps:
  1. Rewrite the `validIds` build at `139-144` using `array.mapNotNull { if (it !is JsonNull) it.asString else null }`.
  2. Rewrite the `documentsToProcess` build at `185-192` using `responseRows.mapNotNull { ... }` with the same `has("doc")` guard.
  3. Replace both `e.printStackTrace()` calls with `Log.e(TAG, "...", e)` and add a `private const val TAG = "SyncRepositoryImpl"` inside a new `companion object`.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; sync still processes shelf documents and failures still appear in logcat.
size budget: ~25 changed lines, 1 file
out of scope: changing batch sizes, retry logic, `JsonUtils` helpers, or repository interfaces.

---

### 3. simplify `VersionUtils` with `zip` and proper logging (roadmap 7+8)
context: `VersionUtils.kt` uses `e.printStackTrace()` in `getVersionCode` (`19-20`) and `getVersionName` (`29-30`) and a manual `for (i in 0 until kotlin.math.min(...))` loop in `compareVersions` (`43-52`). The version lists are short, but `zip` eliminates the index math, and `Log.e` replaces stderr logging.
evidence: `VersionUtils.kt:19-20`, `29-30` (`e.printStackTrace()`); `VersionUtils.kt:43-52` (manual loop)
files: `app/src/main/java/org/ole/planet/myplanet/utils/VersionUtils.kt`. Leave `parseApkVersionString` unchanged.
steps:
  1. Add `android.util.Log` import and `private const val TAG = "VersionUtils"` inside the `VersionUtils` object.
  2. Replace the two `e.printStackTrace()` calls with `Log.e(TAG, "...", e)`.
  3. Rewrite `compareVersions` with `parts1.zip(parts2).forEach { (a, b) -> ... }` and keep the final `parts1.size.compareTo(parts2.size)` return.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; `VersionUtilsTest` passes and version comparison behavior is unchanged.
size budget: ~15 changed lines, 1 file
out of scope: changing version parsing or removing Android `Context`/`PackageManager` usage.

---

### 4. remove Java-stdlib loops and `Locale` from `ExamAnswerUtils` (roadmap 7+8+9)
context: `ExamAnswerUtils.kt` iterates a `JsonArray` by index at `38-46`, sorts arrays with `java.util.Arrays.sort` at `95`, and passes `Locale.getDefault()` into `lowercase()` calls at `72-90`. Kotlin can iterate `JsonArray` directly, `Array<T>.sort()` replaces `Arrays.sort`, and `String.lowercase()` with no explicit locale gives the same default-locale result while removing the `java.util.Locale` import. This shrinks the JVM-only surface and moves the file toward KMP.
evidence: `ExamAnswerUtils.kt:38-46` (`for (i in 0 until choices.size())`), `ExamAnswerUtils.kt:95-97` (`Arrays.sort(it)`), `ExamAnswerUtils.kt:71-90` (`lowercase(Locale.getDefault())`)
files: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt`. Do not touch the `choicesCache` `LinkedHashMap`/`Collections.synchronizedMap` setup.
steps:
  1. Replace the indexed `choices` loop with `for (choice in choices) { if (choice.isJsonObject) ... }`.
  2. Replace `Arrays.sort(it)` with `it.sort()` and remove the `java.util.Arrays` import.
  3. Replace all `lowercase(Locale.getDefault())` calls with `lowercase()` and remove the `java.util.Locale` import.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; `ExamAnswerUtilsTest` passes and exam choice/answer matching behaves the same.
size budget: ~25 changed lines, 1 file
out of scope: changing the cache eviction policy, `VisibleForTesting` usage, or answer matching semantics.

---

### 5. stop recreating tag lists on every `CollectionsFragment` interaction (roadmap 7+8)
context: `CollectionsFragment.kt` declares an unused `filteredList` at `38`, stores `selectedItemsList` as `ArrayList`, and rebuilds `currentTagDataList` with `.toMutableList()` at `71`, `115`, `152`, `162`, and `172` even though the list is only reassigned, never mutated in place. Dropping the dead field, using `mutableListOf`, and holding `currentTagDataList` as an immutable `List<TagData>` saves a list allocation every time the user filters, expands, or selects a tag.
evidence: `CollectionsFragment.kt:36-43` (declarations), `CollectionsFragment.kt:71` (`buildTagDataList(list).toMutableList()`), `CollectionsFragment.kt:152` and `162` and `172` (same pattern)
files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt`. Do not touch `ResourcesTagsAdapter`, `TagData`, or `CollectionsViewModel`.
steps:
  1. Remove the unused `filteredList` property and the `kotlin.collections.ArrayList` import.
  2. Change `selectedItemsList` to `MutableList<TagEntity> = mutableListOf()` and initialize it with `recentList.toMutableList()`.
  3. Change `currentTagDataList` to `List<TagData> = emptyList()` and remove all `.toMutableList()` calls that assign to it.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; collection filtering, parent/child expansion, multi-select, and the OK result still work.
size budget: ~20 changed lines, 1 file
out of scope: redesigning tag filtering or converting the dialog to Compose.

---

### 6. replace `printStackTrace` with `Log.e` in `SyncManager` (roadmap 5+8)
context: `SyncManager.kt` is the sync orchestrator and still calls `e.printStackTrace()` / `err.printStackTrace()` in five catch blocks (`218`, `383`, `406`, `417`, `553`). On Android this writes to stderr rather than logcat, making production sync failures harder to diagnose. Using `Log.e` with a tag preserves the stack trace in logcat.
evidence: `SyncManager.kt:218` (`err.printStackTrace()`), `SyncManager.kt:383`, `406`, `417`, `553` (`e.printStackTrace()`)
files: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`. Do not change `TransactionSyncManager.kt`, `UploadManager.kt`, or `SyncRepositoryImpl.kt`.
steps:
  1. Add a `companion object { private const val TAG = "SyncManager" }` at the end of `SyncManager`.
  2. Replace each `printStackTrace()` call with `Log.e(TAG, "<context> failed", e)` (or `err`), keeping the existing `Log.d("SyncPerf", ...)` and `syncTimeLogger` lines.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; sync failures still surface in logcat with the exception attached.
size budget: ~10 changed lines, 1 file
out of scope: adding new sync metrics, changing sync scheduling, or swallowing exceptions differently.

---

### 7. convert `ServerAddressAdapter` ViewHolder to ViewBinding (roadmap 6+8+10)
context: `ServerAddressAdapter.kt` inflates `R.layout.item_server_address` and then calls `findViewById` in `ViewHolder` (`100-101`) to locate the single `MaterialButton`. ViewBinding is already enabled, so the adapter can inflate `ItemServerAddressBinding` and drop the runtime lookup, reducing boilerplate and direct `R.id` usage before the row moves to Compose.
evidence: `ServerAddressAdapter.kt:100-101` (`itemView.findViewById(R.id.btn_server_address)`)
files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt`. Do not change `LoginActivity.kt`, `SyncActivity.kt`, or `item_server_address.xml`.
steps:
  1. Inflate `ItemServerAddressBinding` in `onCreateViewHolder` and pass it to the `ViewHolder`.
  2. Change `ViewHolder` to hold `val binding: ItemServerAddressBinding` instead of `itemView.findViewById`.
  3. Update `bind` and `updateSelectionState` to reference `binding.btnServerAddress`.
  4. Remove the now-unused `findViewById`/`View` imports while keeping `R.color` and `R.string` usage.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the server list in login/sync still shows names, selection color, and content description.
size budget: ~15 changed lines, 1 file
out of scope: converting the row to Compose or changing selection logic.

---

### 8. convert `LifeAdapter` ViewHolder to ViewBinding (roadmap 6+8+10)
context: `LifeAdapter.kt` holds five `findViewById` calls in `LifeViewHolder` (`135-141`) and inflates `R.layout.row_life` at `45-47`. Binding removes all `R.id` lookups, lowers null/cast risk, and keeps the adapter state in a generated binding object while it is incrementally replaced by Compose.
evidence: `LifeAdapter.kt:135-141` (`findViewById(R.id.titleTextView)`, etc.), `LifeAdapter.kt:45-47` (`LayoutInflater.from(context).inflate(R.layout.row_life, ...)`)
files: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`. Do not change `LifeFragment.kt` or `row_life.xml`.
steps:
  1. Inflate `RowLifeBinding` in `onCreateViewHolder` and pass it to the `ViewHolder`.
  2. Change `LifeViewHolder` to hold `val binding: RowLifeBinding` instead of individual `findViewById` fields.
  3. Replace `holder.title`, `holder.imageView`, `holder.dragImageButton`, `holder.visibility`, and `holder.rvItemContainer` with `binding.titleTextView`, `binding.itemImageView`, `binding.dragImageButton`, `binding.visibilityImageButton`, and `binding.rvItemParentLayout`.
  4. Remove unused widget imports (`TextView`, `ImageView`, `ImageButton`, `LinearLayout`).
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; dashboard life items still display, drag/reorder, visibility toggle, and open their fragments.
size budget: ~40 changed lines, 1 file
out of scope: rewriting drag-and-drop logic, the `transactionFragment` activity lookup, or converting the row to Compose.

---

### 9. convert `VoicesActions` dialog helper to ViewBinding (roadmap 6+8+10)
context: `VoicesActions.kt` inflates `R.layout.alert_input` and calls `findViewById` six times across `createEditDialogComponents` (`46-50`) and `showEditAlert` (`179-182`). The layout generates `AlertInputBinding`, so all lookups can be replaced with binding references, removing direct `R.id` usage from the reply/edit flow.
evidence: `VoicesActions.kt:46-50` (`findViewById(R.id.tl_input)`, etc.), `VoicesActions.kt:179-182` (`findViewById(R.id.cust_msg)`, `findViewById(R.id.alert_icon)`)
files: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt`. Do not change `VoicesFragment.kt`, `NewsViewModel.kt`, or `alert_input.xml`.
steps:
  1. Inflate `AlertInputBinding` instead of `LayoutInflater.from(context).inflate(R.layout.alert_input, null)`.
  2. Update `createEditDialogComponents` to return `EditDialogComponents(binding.tlInput, binding.etInput, binding.llAlertImage, binding.root)`.
  3. Update `showEditAlert` to use `binding.custMsg` and `binding.alertIcon`.
  4. Remove unused `findViewById`/`EditText`/`TextInputLayout`/`TextView`/`ImageView` imports.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; reply/edit news dialogs still show the title icon, message input, and image-add button.
size budget: ~20 changed lines, 1 file
out of scope: rewriting image loading, the `VoicesActions` public API, or the `VoicesFragment` ViewModel.

---

### 10. use `BottomSheetDialog.behavior` instead of internal view lookups (roadmap 6+8+10)
context: `StorageBreakdownFragment`, `StorageCategoryDetailFragment`, and `AddResourceFragment` each look up the internal `com.google.android.material.R.id.design_bottom_sheet` `FrameLayout` with `findViewById` and then call `BottomSheetBehavior.from(it)` to expand/skip collapsed. `BottomSheetDialog` exposes a `behavior` property that gives direct access to the same behavior, so the `findViewById`/`FrameLayout` dance is unnecessary and removes a direct dependency on Material's internal `R.id`.
evidence: `StorageBreakdownFragment.kt:79-85` (`findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)`), `StorageCategoryDetailFragment.kt:71-77` (same), `AddResourceFragment.kt:127-132` (same plus `setHideable(true)`)
files: `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt` (`onCreateDialog` `75-88`), `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt` (`onCreateDialog` `67-79`), `app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceFragment.kt` (`onCreateDialog` `123-135`). Do not touch the fragments' ViewModels, adapters, or `BottomSheetBehavior` constants.
steps:
  1. In each `onCreateDialog`, keep `setOnShowListener`, cast the `DialogInterface` to `BottomSheetDialog`, and use `dialog.behavior.apply { state = STATE_EXPANDED; skipCollapsed = true }`.
  2. In `AddResourceFragment`, also call `setHideable(true)` on `behavior` to preserve current behavior.
  3. Remove the `findViewById<FrameLayout>(...)` and `BottomSheetBehavior.from(it)` calls.
  4. Remove now-unused `android.widget.FrameLayout` imports and the fully-qualified `com.google.android.material.R.id.design_bottom_sheet` reference.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; storage breakdown, category detail, and add-resource bottom sheets still open fully expanded and skip the collapsed state; add-resource remains hideable.
size budget: ~25 changed lines, 3 files
out of scope: redesigning bottom sheet UX, changing peek height, or converting these fragments to Compose.

---

self-check:
- [x] exactly 10 tasks
- [x] no file appears in two tasks
- [x] every cited path was opened and confirmed to exist
- [x] every task has all 7 template sections
- [x] no task under 15 lines
- [x] no task touches a file from the open-PR list
- [x] one tasks markdown document written to `docs/`
- [ ] dedicated branch created, committed, and pushed (to be done)
- [ ] response terminates with the full URL to the markdown document on the pushed branch (to be done)
