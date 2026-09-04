date: 2026-09-04
base commit: 9ff1273dc95f8cbd3590fca12ca821454b2e27bc
open PRs checked: 16705, 16702, 16701, 16698, 16693, 16690, 16688, 16686, 16680, 16677, 16661, 16647, 16624, 16623, 16619, 16594, 16270, 16101, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075

All files touched by the open PRs above were excluded from the tasks below.

---

### 1. Avoid intermediate collections in StorageBreakdownFragment scan and populate (roadmap 7)

context: `StorageBreakdownFragment.kt:214` uses `oleDir.walkTopDown().filter { it.isFile }.forEach { ... }` and `StorageBreakdownFragment.kt:228` uses `categories.filter { it.fileCount > 0 }.forEach { ... }`. Both `filter`/`forEach` pairs allocate intermediate lists that are immediately discarded, increasing GC pressure when scanning large offline storage directories or many categories.

files: `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt` — edit `scanStorage()` and `populateCategoryRows()` only. Do not touch `StorageCategories`, `StorageCategoryViewModel`, or any layout file.

steps:
1. Replace `oleDir.walkTopDown().filter { it.isFile }.forEach { ... }` with a `for (file in oleDir.walkTopDown()) { if (!file.isFile) continue; ... }` loop, keeping the existing extension handling and `StorageCategories.indexOf(ext)` call.
2. Replace `categories.filter { it.fileCount > 0 }.forEach { ... }` with a `for (category in categories) { if (category.fileCount <= 0) continue; ... }` loop.
3. Keep the existing row-binding logic (`ItemStorageCategoryBinding.inflate`, `FileUtils.formatSize`, click listener) unchanged.
4. Remove any imports that become unused after removing the `filter` chain.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; Settings → Storage shows the same categories, totals, and file counts, and still opens the category detail screen on tap.

size budget: ~10 changed lines, 1 file

out of scope: no background-thread or caching changes; no storage permission or categorization logic changes.

---

### 2. Build expanded-groups set in a single pass in ChatHistoryAdapter (roadmap 7)

context: `ChatHistoryAdapter.kt:154` builds `expandedGroups` as `currentFlatList.filter { it.isGroup && it.isExpanded }.map { it.title }.toMutableSet()`. This walks the share-target list twice and creates a throwaway filtered list plus a new set on every expand/collapse tap in the chat-share dialog.

files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt` — edit the share-target click handler around lines 152-160 only. Leave `generateFlatList`, `getSharedViewInIds`, and the `onBindViewHolder` implementation unchanged.

steps:
1. Replace the `filter { ... }.map { ... }.toMutableSet()` chain with a single `for` loop that adds `item.title` to a `LinkedHashSet<String>` when `item.isGroup && item.isExpanded`.
2. Preserve the existing `if (currentlyExpanded) { expandedGroups.remove(clickedItem.title) } else { expandedGroups.add(clickedItem.title) }` logic.
3. Do not modify the `currentFlatList.firstOrNull { ... }?.isExpanded` lookup above the set build.
4. Run the unit test suite.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; opening a chat history item and expanding/collapsing share-target groups keeps the same groups expanded and the dialog updates identically.

size budget: ~5 changed lines, 1 file

out of scope: no changes to `generateFlatList`, `ChatShareTargets`, or share-target data modeling.

---

### 3. Build the final server list without filter+map intermediates in ServerDialogExtensions (roadmap 7)

context: `ServerDialogExtensions.kt:132` constructs the final server list as `listOf(pinnedEntry.first) + candidates.filter { it.second != urlWithoutProtocol }.map { it.first }`. After already materializing `candidates`, it creates a filtered list, a mapped list, and a concatenated list just to place the pinned server first in the sync login spinner.

files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt` — edit `refreshServerList()` around lines 123-144 only. Do not change `protocolSemantics()` or other extension functions.

steps:
1. Replace the `listOf(...) + candidates.filter { }.map { }` expression with a single `buildList { add(pinnedEntry.first); candidates.forEach { if (it.second != urlWithoutProtocol) add(it.first) } }`.
2. Keep the `pinnedIndex` calculation and the `serverAddressAdapter?.submitList(finalList)` call unchanged.
3. Ensure `filteredList` is still submitted when `pinnedEntry` is null or `showAdditionalServers` is false.
4. Remove any imports that become unused.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; the sync login screen still shows the pinned server first, followed by the remaining servers in the original order.

size budget: ~5 changed lines, 1 file

out of scope: no changes to `ServerConfigUtils.getFilteredList`, server data sources, or adapter behavior.

---

### 4. Chain distinct/sort operations as sequences in LifeRepositoryImpl (roadmap 1, 7, 9)

context: `LifeRepositoryImpl.kt` materializes DAO results through `distinctBy { ... }.sortedBy { ... }` and `filter { ... }.sortedBy { ... }` on lists at lines 81, 86, 91, 123, and 130. Each pair creates an intermediate list before sorting, and the dashboard's "My Life" grid reads from these paths repeatedly.

files: `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` — touch `getMyLifeByUserId`, `getVisibleMyLifeByUserId`, and `getMyLifeForDashboard` only. Leave `cacheMyLifeItems`, `seedMyLifeIfEmpty`, and `dedupKey()` unchanged.

steps:
1. Convert `myLifeDao.getByUserId(...).distinctBy { it.dedupKey() }.sortedBy { it.weight }` to a single sequence chain: `asSequence().distinctBy { it.dedupKey() }.sortedBy { it.weight }.toList()`.
2. Apply the same sequence chaining to the `getVisibleByUserId` and `getMyLifeForDashboard` return paths, including the `mapNotNull`/`filter`/`sortedBy` combinations.
3. Keep all return types as `List<MyLife>` and preserve the existing deduplication and weight ordering.
4. Run the repository/unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; the dashboard "My Life" section shows the same tiles in the same order and with the same deduplication.

size budget: ~12 changed lines, 1 file

out of scope: no DAO or database schema changes; no new `distinctBy` semantics.

---

### 5. Convert filter+sort in EnterprisesRepositoryImpl to a sequence (roadmap 1, 7, 9)

context: `EnterprisesRepositoryImpl.kt:103-105` fetches all team reports, then filters out archived rows, then sorts by date: `teamDao.getByTeamIdAndDocType(teamId, "report").filter { it.status != "archived" }.sortedByDescending { it.createdDate }`. The filter and sort each copy the list; enterprise teams can have many historical reports.

files: `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt` — edit `exportReportsAsCsv()` only. Do not change `addReport`, `getReportsFlow`, `getAllReports`, or `FinanceReport`.

steps:
1. Replace the `.filter { it.status != "archived" }.sortedByDescending { it.createdDate }` chain with `asSequence().filter { it.status != "archived" }.sortedByDescending { it.createdDate }.toList()`.
2. Leave the CSV output loop (`for (report in reports)`) and all formatting unchanged.
3. Preserve the existing archive-exclusion behavior exactly.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; enterprise team financial reports export in the same order and still exclude archived rows.

size budget: ~3 changed lines, 1 file

out of scope: no new repository methods, CSV format changes, or database work.

---

### 6. Replace lowercase+endsWith GIF checks with case-insensitive endsWith in ImageViewerUtils and VoicesActions (roadmap 7)

context: `ImageViewerUtils.kt:38` and `VoicesActions.kt:93` both call `imagePath.lowercase(Locale.getDefault()).endsWith(".gif")` before choosing `asGif()`. This allocates a lowercase string on every image binding. `String.endsWith(suffix, ignoreCase = true)` is allocation-free and locale-independent.

files:
- `app/src/main/java/org/ole/planet/myplanet/utils/ImageViewerUtils.kt` — function `showZoomableImage()` only; remove the `java.util.Locale` import if it becomes unused.
- `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt` — function `addImageWithRemoveIcon()` only; keep the `Locale` import because `dateFormatter` still uses it.

steps:
1. In `ImageViewerUtils`, replace `imagePath.lowercase(Locale.getDefault()).endsWith(".gif")` with `imagePath.endsWith(".gif", ignoreCase = true)` and remove `import java.util.Locale`.
2. In `VoicesActions`, replace the same pattern in `addImageWithRemoveIcon` but keep the `Locale` import.
3. Do not change the Glide `asGif`/`load`/`error` branches.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; opening an image in the zoomable viewer and composing a voice post still load GIFs correctly and non-GIFs as static images.

size budget: ~4 changed lines, 2 files

out of scope: no new image format support or Glide configuration changes.

---

### 7. Remove distinct+concat allocations from NotificationsViewModel group ordering (roadmap 3, 7)

context: `NotificationsViewModel.kt:272-273` builds `orderedTypes` as `(TYPE_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in TYPE_ORDER }).distinct()`. Each notification refresh constructs three temporary lists and a set; `grouped.keys.filter { it !in TYPE_ORDER }` also performs O(n×m) membership checks.

files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` — edit `buildNotificationGroups()` only. Leave `formatNotification`, `resolveType`, repository calls, and the `NotificationGroup` data class unchanged.

steps:
1. Replace the `(TYPE_ORDER.filter { ... } + grouped.keys.filter { ... }).distinct()` expression with a single `LinkedHashSet<String>` pass: add `TYPE_ORDER` entries that exist in `grouped`, then add any remaining `grouped.keys` not already in `TYPE_ORDER`.
2. Keep the same ordering semantics: `TYPE_ORDER` first, then remaining group keys in their existing iteration order.
3. Leave the `groupBy { ... }` and the `mapNotNull` return block unchanged.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; the notifications panel groups and orders types identically (for example: join_request, team_join, task, chat, voice_reply, resource, storage).

size budget: ~10 changed lines, 1 file

out of scope: no notification repository, DAO, or database changes.

---

### 8. Remove double processTimes lookup in SyncTimeLogger.endProcess (roadmap 5, 7)

context: `SyncTimeLogger.kt:138-142` first checks `processTimes.containsKey(startKey)` and immediately afterward reads `processTimes[startKey] ?: return`. That is two map lookups for every timed sync or upload process. The map is a `ConcurrentHashMap` used throughout sync/upload timing.

files: `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` — edit `endProcess()` only. Do not change `startProcess`, `logProcess`, `logApiCall`, `logDbOperation`, or the diagnostics upload logic.

steps:
1. Remove the `if (!processTimes.containsKey(startKey)) { return }` guard.
2. Move `val startTime = processTimes[startKey] ?: return` to the top of `endProcess`, after computing `endTime`.
3. Keep the duration calculation, `processTimes[processName] = duration`, and `processItemCounts[processName] = itemCount` updates unchanged.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; sync and upload timing still records correct durations and missing start times are still silently ignored.

size budget: ~3 changed lines, 1 file

out of scope: no new metrics, logging format changes, or persistence changes.

---

### 9. Remove double map lookup and Date object in News model (roadmap 1, 7, 9)

context: `News.kt:194-195` first tests `map.containsKey("news")` and then fetches `map["news"]`, doing two lookups in the deserialized map. `News.kt:101` and `News.kt:167` use `Date().time` to set `editedTime` and `time`, allocating `java.util.Date` objects just to read the current timestamp. Both patterns run for every news or voice item parsed or updated.

files: `app/src/main/java/org/ole/planet/myplanet/model/News.kt` — edit `updateMessage()` and `createNews()` only. Do not change `serialize()`, `isCommunityNews`, `calculateSortDate`, or DAO callers.

steps:
1. Replace the `if (map.containsKey("news")) { val newsObj = map["news"] ... }` block with `map["news"]?.let { newsObj -> ... }` so the map is read exactly once.
2. Replace `this.editedTime = Date().time` in `updateMessage()` with `System.currentTimeMillis()`.
3. Replace `news.time = Date().time` in `createNews()` with `System.currentTimeMillis()`.
4. Remove the now-unused `import java.util.Date`.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; creating and editing voices/news still records correct timestamps and the `"news"` extended fields still deserialize correctly.

size budget: ~7 changed lines, 1 file

out of scope: no changes to `News` serialization format or Room schema.

---

### 10. Replace containsKey+set with getOrPut in TeamPagerAdapter (roadmap 7)

context: `TeamPagerAdapter.kt:35` and `TeamPagerAdapter.kt:43` both use `if (!itemIds.containsKey(page.id)) { itemIds[page.id] = nextId++ }`. This performs a `containsKey` followed by a `put` for every page during initialization and page updates. `MutableMap.getOrPut` achieves the same result with a single map lookup.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt` — edit the `init` block and `updatePages()` only. Do not touch `createFragment`, `getItemId`, `containsItem`, or `TeamPageConfig`.

steps:
1. In `init`, replace the `if (!itemIds.containsKey(page.id)) { itemIds[page.id] = nextId++ }` block with `itemIds.getOrPut(page.id) { nextId++ }`.
2. In `updatePages`, apply the same `getOrPut` replacement.
3. Keep `nextId` as a `Long` and ensure stable fragment IDs are still generated sequentially.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` passes; team detail tabs still display, `ViewPager2` still restores the correct tab state after configuration changes, and no `IllegalStateException` from duplicate fragment IDs.

size budget: ~4 changed lines, 1 file

out of scope: no changes to `TeamPageConfig`, fragment selection logic, or tab ordering.
