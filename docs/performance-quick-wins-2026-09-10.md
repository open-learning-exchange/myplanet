# myPlanet performance quick-win work orders

**date:** 2026-09-10  
**base commit:** 022ee7d1f50f1453b1741aee391f3be5c619b4eb  
**open PRs checked:** #16989, #16988, #16987, #16986, #16690, #16624, #16623, #16594, #16270, #16101, #15951, #15825, #15824, #15820, #15808, #15559, #15519, #15412, #15267, #15266, #15226, #15198, #15108, #14960, #14893, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075

---

### 1. merge repeated collection passes in NotificationsViewModel (roadmap 7+3)

context: `NotificationsViewModel.kt` builds `taskNotifications` and `joinRequestNotifications` with a hand-rolled loop over `payloadNotifications` at lines 77-83, and the bulk `markSelectedAsRead`, `deleteSelected`, and `markAllAsRead` functions each traverse the notification list once to count unread items and then again to produce the updated list. These repeated passes slow bulk notification actions.

files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` only. Do not touch `NotificationsRepository`, `NotificationsFragment`, or the `Notification` model.

steps:
1. Replace the loop at lines 77-83 with a single `partition` call that splits `payloadNotifications` into task and join-request lists.
2. In `markSelectedAsRead`, `deleteSelected`, and `markAllAsRead`, combine the unread count and list update into one traversal each.
3. Remove the unused `List<Notification>.markAsRead(id: String)` extension at lines 254-256 once it is dead code.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*NotificationsViewModelTest*'` and verify bulk mark-read/delete still keeps counts and filters correct.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the notifications screen still groups, marks read, and deletes selected/all items with correct unread counts.

size budget: ~35 changed lines, 1 file

out of scope: no DAO/repository changes, no UI redesign, no new coroutine dispatchers

---

### 2. avoid intermediate lists when counting and filtering resources (roadmap 7+6)

context: `ResourcesListFilter.kt` builds the full filtered result list just to return its size in `countMatching` at line 47-48, and `filter` chains `filterBySearchAndTags` and `filterByFacetsAndDownloadStatus`, producing two separate lists for every filter operation. Resource lists can be large, so these allocations are a hotspot.

files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt` only. Leave `ResourcesSearchUtils.kt`, `ResourcesViewModel.kt`, `ResourcesFragment.kt`, and `ResourcesListFilterTest.kt` unchanged.

steps:
1. Inline `filterBySearchAndTags` and `filterByFacetsAndDownloadStatus` into a single `filter` pass inside `filter` so search results are not re-filtered into a second list.
2. Rewrite `countMatching` to call `ResourcesSearchUtils.searchLocalModels` and then `count` the matching models without allocating the final filtered list.
3. Remove the now-redundant private helper functions if they become unused.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*ResourcesListFilterTest*'`.

acceptance: `ResourcesListFilterTest` passes, `./gradlew testDefaultDebugUnitTest` green, and the resource filter count displayed to the user stays accurate for all filter combinations.

size budget: ~20 changed lines, 1 file

out of scope: no changes to `ResourcesSearchUtils`, no ViewModel/Fragment changes

---

### 3. replace nested `any` scans with set lookups in CollectionsFragment (roadmap 7+6)

context: `CollectionsFragment.kt` builds `allTags` by `list + childMap.values.flatten()` at line 70 only to find a matching tag, and `buildTagDataList` calls `selectedItemsList.any { it.matches(parentTag) }` at line 135 and `selectedItemsList.any { it.matches(childTag) }` at line 142 inside parent and child loops. With many tags or selected items this is `O(n*m)`.

files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt` only. Leave `ResourcesTagsAdapter`, `CollectionsViewModel`, and `TagEntity` unchanged.

steps:
1. Replace the `allTags` list at line 70 with a `Map<String, TagEntity>` built from `list` and `childMap.values`, and use it to reconcile selected items by id.
2. In `buildTagDataList`, derive a `Set<String>` of selected tag ids once before the parent loop, then use `selectedIds.contains(tag.id)` for both parent and child selected checks.
3. Preserve `selectedItemsList` as the source of truth; only change the lookup strategy.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*CollectionsFragmentTest*'` and open the collection filter dialog to confirm selected/expansion state still works.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the collection filter dialog shows selected tags and expand/collapse correctly with no regressions.

size budget: ~30 changed lines, 1 file

out of scope: no adapter or ViewModel changes, no feature changes

---

### 4. avoid list allocations in MyHealthFragment.getDisplayName (roadmap 7+6)

context: `MyHealthFragment.kt` `getDisplayName` at lines 339-342 creates a list, maps it, filters it, and joins it just to concatenate at most three name fields. This runs every time a patient record is displayed.

files: `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt` only. Do not touch `HealthViewModel`, `HealthExaminationAdapter`, or `HealthUsersAdapter`.

steps:
1. Rewrite `getDisplayName` to trim each of `firstName`, `middleName`, and `lastName` directly and append non-empty parts to a `StringBuilder`/`buildString` with a single space separator.
2. Keep the existing fallback to `user.name.orEmpty()` when all names are blank.
3. Run `./gradlew testDefaultDebugUnitTest` and open the health screen to verify patient names still render the same.

acceptance: `./gradlew testDefaultDebugUnitTest` green; patient display names on the health screen match the previous behavior for full, partial, and blank names.

size budget: ~20 changed lines, 1 file

out of scope: no changes to the `UserEntity` model or other health UI components

---

### 5. remove log-flattening allocations in SyncTimeLogger.generateSummary (roadmap 7+5)

context: `SyncTimeLogger.kt` `generateSummary` at lines 229-230 flattens `apiCallTimes` and `dbOperationTimes` into `allApiCallLogs` and `allDbOpLogs`, then iterates those flattened lists multiple times for `sumOf` and `count`. Each sync summary allocates two large temporary lists.

files: `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` only. Do not modify `DiagnosticsRepository`, `SyncManager`, or the log data classes.

steps:
1. Remove `val allApiCallLogs` and `val allDbOpLogs`.
2. Compute `totalApiCalls`, `totalApiTime`, and `successfulCalls` directly from `apiCallTimes.values` using `sumOf` and `count` on each per-endpoint list.
3. Compute `totalDbOps`, `totalDbTime`, and `totalDbItems` directly from `dbOperationTimes.values` the same way.
4. Use those totals for the percentage calculations at lines 296-301.
5. Run `./gradlew testDefaultDebugUnitTest --tests '*SyncTimeLoggerTest*'`.

acceptance: `SyncTimeLoggerTest` passes, `./gradlew testDefaultDebugUnitTest` green, and sync summary output remains identical in format and values.

size budget: ~20 changed lines, 1 file

out of scope: no changes to logging storage, no UI work

---

### 6. stop flattening RecyclerView payloads in HealthUsersAdapter (roadmap 7+6)

context: `HealthUsersAdapter.kt` `onBindViewHolder` at line 77 calls `payloads.filterIsInstance<List<*>>().flatten()` on every partial bind, creating two intermediate collections to check three string keys. Payloads are small but partial binds are frequent.

files: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt` only. Leave `DiffUtils`, `ItemUserBinding`, and the view holder bind methods unchanged.

steps:
1. Replace the `diffs` list with a small nested loop that iterates `payloads` once and sets boolean flags when a payload is a `List<*>` containing `"name"`, `"userImage"`, or `"joinDate"`.
2. Call `holder.bindName`, `holder.bindImage`, or `holder.bindDate` based on those flags, preserving the existing behavior when payloads is empty.
3. Run `./gradlew testDefaultDebugUnitTest` and verify the health member list still updates smoothly when names/images/dates change.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the health member list still reacts correctly to partial diffs.

size budget: ~15 changed lines, 1 file

out of scope: no changes to diff logic or view holder implementation

---

### 7. cache parsed imagesArray in News (roadmap 7+1)

context: `News.kt` `imagesArray` at lines 84-85 re-parses the `images` JSON string with Gson on every access. The model already has an unused `@Ignore` cache field `parsedImagesArray` at line 79, and `rawImages` at line 71 can track the source string. Voices UI already manually invalidates this cache in `VoicesAdapter`; centralizing it removes repeated parsing for every consumer.

files: `app/src/main/java/org/ole/planet/myplanet/model/News.kt` only. Do not change `VoicesAdapter`, `VoicesRepositoryImpl`, or `NewsDao`.

steps:
1. Rewrite the `imagesArray` getter to return `parsedImagesArray` when it is non-null and `rawImages == images`.
2. When `images` changes or `parsedImagesArray` is null, parse once with `JsonUtils.gson.fromJson`, store the result in `parsedImagesArray`, and record the source in `rawImages`.
3. Continue returning `JsonArray()` when `images` is null.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*NewsTest*'` and `./gradlew testDefaultDebugUnitTest --tests '*VoicesAdapterImagesTest*'`.

acceptance: `NewsTest` and `VoicesAdapterImagesTest` pass, `./gradlew testDefaultDebugUnitTest` green, and the voices/news feed still loads images correctly after sync.

size budget: ~15 changed lines, 1 file

out of scope: no new fields, no changes to other News accessors like `labelsArray`

---

### 8. make ResourcesRepositoryImpl.batchInsertResources single-pass (roadmap 7+1)

context: `ResourcesRepositoryImpl.kt` `batchInsertResources` at lines 674-716 filters `documents` once, maps `validDocs` to resource ids in a second pass, and then loops `validDocs` again to build `MyLibrary` instances. For large sync payloads this is three traversals of the same list.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` only. Leave `MyLibrary`, `MyLibraryDao`, `TagsRepository`, and the other repository functions unchanged.

steps:
1. Combine the initial `filter` and `map` of resource ids into one pass that builds `validDocs` and `resourceIds` together.
2. Keep the existing chunked DAO lookup to fetch `existingItems`.
3. Leave the final `forEach` that builds `librariesToUpsert` as the second pass.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*ResourcesRepositoryImplTest*'` and `./gradlew testDefaultDebugUnitTest --tests '*ResourcesRepositoryBenchmarkTest*'`.

acceptance: `ResourcesRepositoryImplTest` and `ResourcesRepositoryBenchmarkTest` pass, `./gradlew testDefaultDebugUnitTest` green, and resource sync still returns the same saved ids.

size budget: ~25 changed lines, 1 file

out of scope: no changes to `getOfflineResourceItems`, no DAO or model changes

---

### 9. make StorageCategories.indexOf case-insensitive and drop duplicate lookup (roadmap 7+6)

context: `StorageCategories.kt` `indexOf` at line 32 does an exact-key lookup, so `StorageBreakdownFragment.kt` at lines 230-231 has to call `indexOf` twice (once as-is and again with `lowercase()`) for every file with an unknown extension. This is a small per-file overhead during storage scans.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategories.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt`

Do not modify `StorageCategoryDetailFragment` or `ResourcesRepositoryImpl`.

steps:
1. Change `StorageCategories.indexOf` to normalize the incoming extension with `lowercase()` before looking it up in `extensionToIndex`.
2. In `StorageBreakdownFragment.scanStorage`, replace the two `indexOf` calls and fallback at lines 230-231 with a single `StorageCategories.indexOf(ext)` call.
3. Keep `OTHER_INDEX` handling for empty extensions unchanged.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*StorageCategoriesTest*'` and `./gradlew testDefaultDebugUnitTest --tests '*StorageBreakdownFragmentTest*'`.

acceptance: `StorageCategoriesTest` and `StorageBreakdownFragmentTest` pass, `./gradlew testDefaultDebugUnitTest` green, and the storage breakdown screen still correctly categorizes files regardless of extension casing.

size budget: ~10 changed lines, 2 files

out of scope: no changes to storage category definitions or the detail screen

---

### 10. deduplicate distinctBy/sortedBy calls in LifeRepositoryImpl (roadmap 7+1)

context: `LifeRepositoryImpl.kt` repeats `.distinctBy { it.dedupKey() }.sortedBy { it.weight }` at lines 82, 90, 95, 135, and 160. The same two collection passes are written five times, making the repository harder to maintain and slightly more error-prone.

files: `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` only. Do not touch `MyLifeDao`, `SharedPrefManager`, or `LifeViewModel`.

steps:
1. Add a private helper that takes a `List<MyLife>` and returns `distinctBy { it.dedupKey() }.sortedBy { it.weight }`.
2. Replace all five repeated call sites with the helper.
3. Keep the `getMyLifeForDashboard` visible-only filter at line 135 as a separate step that calls the helper after filtering.
4. Run `./gradlew testDefaultDebugUnitTest --tests '*LifeRepositoryImplTest*'`.

acceptance: `LifeRepositoryImplTest` passes, `./gradlew testDefaultDebugUnitTest` green, and the dashboard/life menu ordering and deduplication behavior is unchanged.

size budget: ~20 changed lines, 1 file

out of scope: no changes to seeding logic, DAO queries, or UI
