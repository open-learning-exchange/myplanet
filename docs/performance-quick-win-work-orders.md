# myPlanet performance quick-win work orders

date · 2026-08-19  
base commit · `9c54a0341557a7e7ae4bdc313fd1c97c0cc23b32`  
open PRs checked · GitHub MCP `list_pull_requests` (open) plus per-PR `get_files` for active heads including #15821, #15820, #15808, #15772, #15771, #15769, #15699, #15698, #15656, #15650, #15614, #15594, #15559, #15519, #15158, #14650, and others listed as open. Files touched by those PRs are off-limits below (notably `ExamTakingFragment.kt`, `SubmissionViewModel.kt`, `ProgressRepositoryImpl.kt`, `SubmissionsRepositoryImpl.kt`, `CoursesRepositoryImpl.kt`, `TeamsRepository*.kt`, `TransactionSyncManager.kt`, `UrlUtils.kt`, `FileUtils.kt`, `ResourcesFragment.kt`, `SyncActivity.kt`, `ConfigurationsRepositoryImpl.kt`, and related tests). Older brainstorm/on-hold PRs were treated as soft offlimits when their heads clearly rewrite the same hot paths.

### 1. normalize free-text answers once per correctness check (roadmap 7+8)

context: `ExamAnswerUtils.checkTextAnswer` lowercases `ans` inside the `correctChoices.any` predicate, so the same answer string is allocated again for every candidate at `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt:75-78`. This path is hit during exam/survey grading; existing coverage already exercises input-text correctness at `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt`.

files: Touch `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt`, specifically `ExamAnswerUtils.checkTextAnswer`, and `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt`. Leave `choicesCache`, `getChoiceTextById`, `checkSelectAnswer`, and `checkMultipleSelectAnswer` alone.

steps:
1. Compute the default locale and normalized answer once before scanning `correctChoices`.
2. Reuse the normalized answer while normalizing each candidate choice and checking containment.
3. Preserve the current null-list result, case-insensitive matching, substring behavior, and default-locale semantics.
4. Extend the existing input-text tests with multiple correct-choice candidates so the loop behavior remains covered.
5. Clean any imports made unused by the focused edit.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ExamAnswerUtilsTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that free-text answers still match correct choices without regard to case, including when the matching choice is not first.

size budget: approximately 8-15 changed lines across 2 files.

out of scope: Do not replace the existing `LruCache`, change select or multi-select comparison semantics, or introduce locale-policy changes. This task does not migrate Android dependencies and therefore does not claim roadmap 9 progress.

---

### 2. remove the redundant selection membership scan (roadmap 7+8)

context: `SelectionUtils.handleCheck` calls `selectedItems.contains(list[i])` and then `selectedItems.remove(list[i])` at `app/src/main/java/org/ole/planet/myplanet/utils/SelectionUtils.kt:4-9`, causing two linear scans when unchecking a present item. `MutableList.remove` already reports failure when the value is absent, and existing tests cover present and absent removal at `app/src/test/java/org/ole/planet/myplanet/utils/SelectionUtilsTest.kt`.

files: Touch `app/src/main/java/org/ole/planet/myplanet/utils/SelectionUtils.kt`, specifically `SelectionUtils.handleCheck`, and `app/src/test/java/org/ole/planet/myplanet/utils/SelectionUtilsTest.kt`. There are no neighboring utility functions to alter.

steps:
1. Read `list[i]` once into a local value so both branches avoid repeated indexed access.
2. On the unchecked branch, call `selectedItems.remove` directly instead of checking `contains` first.
3. Preserve the checked branch’s current append behavior, including duplicates and null elements.
4. Strengthen the existing tests to assert that only the first equal occurrence is removed when duplicates are present.
5. Retain the absent-item and null-item coverage.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.SelectionUtilsTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that checking still appends the selected value and unchecking still removes one matching value without changing an absent selection.

size budget: approximately 8-15 changed lines across 2 files.

out of scope: Do not change the public signature to a `MutableSet`, deduplicate existing selections, or alter index error behavior. Because this utility is already platform-free Kotlin, the edit preserves roadmap 9 compatibility without a separate multiplatform migration.

---

### 3. index successful uploads before reconciling local failures (roadmap 5+7)

context: Both `UploadCoordinator.updateDatabaseBatch` and `updateDatabaseBatchRoom` map failed database results back to uploaded items with `succeeded.find`, at `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt:255-261` and `:439-442`. When a batch has multiple local-update failures, reconciliation becomes quadratic even though `localId` is already the stable key used throughout the upload workflow.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt`, specifically `UploadCoordinator.updateDatabaseBatch` and `UploadCoordinator.updateDatabaseBatchRoom`. Leave `uploadBatch`, `uploadBatchRoom`, `queueRetryableFailures`, and all upload configuration types alone.

steps:
1. Build a `localId`-keyed lookup from `succeeded` once in each reconciliation function.
2. Resolve each returned failed result through the lookup instead of scanning the complete success list.
3. Preserve the order and duplicate behavior of `failedResults.mapNotNull`.
4. Keep unknown failed-result IDs ignored exactly as they are today.
5. Avoid extracting a new helper unless doing so reduces the total changed lines without widening visibility.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.UploadManagerTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that full success, partial local-update failure, and unknown failure IDs still produce the same `UploadResult` classification and counts.

size budget: approximately 6-14 changed lines in 1 file.

out of scope: Do not alter batching, retry eligibility, remote upload ordering, Room contracts, or payload preparation. This optimization consolidates the two existing reconciliation paths but does not move Android-bound orchestration into the platform-free core.

---

### 4. update a single notification in one list traversal (roadmap 3+7+10)

context: `NotificationsViewModel.markAsRead` first searches `currentList.find` and then either filters the list or calls `currentList.markAsRead`, producing a second traversal at `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:159-179`. This action runs from notification interaction and can capture the unread transition during the same transformation without a preliminary scan. Helper extensions already exist later in the same file (`markAsRead` list helpers around lines 202-205).

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`, specifically `NotificationsViewModel.markAsRead`, and `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`. Leave bulk selection methods, grouping, type resolution, and repository behavior alone.

steps:
1. Fold target detection, prior unread-state capture, and list transformation into one traversal.
2. Preserve removal from the `"unread"` filter and copy-as-read behavior for every other filter.
3. Ensure a missing or already-read notification does not decrement `_unreadCount`.
4. Keep repository confirmation through `markedIds` as the gate for local mutation.
5. Add tests for unread-filter removal, all-filter replacement, already-read input, and a missing target ID.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that opening one unread notification updates its row or removes it under the unread filter and decrements the badge exactly once.

size budget: approximately 25-50 changed lines across 2 files.

out of scope: Do not change bulk mark-all behavior, selection state, notification grouping, or repository contracts. Consolidating this state mutation in the viewmodel keeps UI state hoisted and directly supports roadmap 10 portability.

---

### 5. reuse selection indexes while rebuilding collection tag rows (roadmap 6+7)

context: `CollectionsFragment.buildTagDataList` scans `selectedItemsList` separately for every parent and every expanded child, and also calls `currentTagDataList.find` per parent, at `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt:103-117`. Expanding a large collection tree therefore multiplies selection-list scans during each rebuild, even though only tag IDs are needed for membership checks.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt`, specifically `CollectionsFragment.buildTagDataList`. Leave `filterTags`, `setListAdapter`, `onCheckboxTagSelected`, `ResourcesTagsAdapter`, and repository loading alone.

steps:
1. Derive a set of selected non-null tag IDs once at the beginning of `buildTagDataList`.
2. Use set membership for both parent and child `isSelected` values.
3. Build one lookup of current parent rows by tag ID before the parent loop instead of calling `currentTagDataList.find` per parent.
4. Preserve expansion state, row order, duplicate-ID behavior, and the collection-switch flag.
5. Keep all temporary indexes local so no stale selection or expansion cache survives a rebuild.

acceptance: `./gradlew testDefaultDebugUnitTest` passes. Verify manually that opening Collections, selecting tags, filtering, expanding parents, and toggling multi-select keeps the same checked and expanded rows while rebuilds remain responsive.

size budget: approximately 12-25 changed lines in 1 file.

out of scope: Do not migrate the dialog to Compose, replace the adapter, change tag equality, or alter repository queries. This is an incremental UI performance prerequisite for roadmap 6 rather than a direct roadmap 10 conversion.

---

### 6. snapshot watched tables once per realtime-sync subscription (roadmap 5+7)

context: `RealtimeSyncHelper.setupRealtimeSync` invokes `mixin.getWatchedTables()` and then performs list membership for every emitted update at `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt:27-37`. Watched tables are configuration for a helper instance, so repeatedly reconstructing or scanning the list adds avoidable work on the realtime event stream.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt`, specifically `RealtimeSyncHelper.setupRealtimeSync`. Leave the `RealtimeSyncMixin` interface, debounce interval, distinctness criteria, `onDataUpdated`, and `refreshRecyclerView` alone.

steps:
1. Call `mixin.getWatchedTables()` once when `setupRealtimeSync` creates the collection pipeline.
2. Convert the result to a set for constant-time table membership checks.
3. Use the snapshot in the flow filter without changing downstream operators or callback order.
4. Preserve empty-list behavior and exact string matching.
5. Keep the snapshot scoped to each setup call so separate helpers cannot share configuration accidentally.

acceptance: `./gradlew testDefaultDebugUnitTest` passes. Verify that updates for watched tables still trigger `onDataUpdated` and adapter refresh after debounce, while updates for all other tables remain ignored.

size budget: approximately 3-8 changed lines in 1 file.

out of scope: Do not change realtime event payloads, lifecycle collection, debounce policy, adapter refresh contracts, or the mixin API. The optimization consolidates sync filtering under roadmap 5 but does not itself extract Android-bound lifecycle code for roadmap 9.

---

### 7. reverse-index voice labels once while filtering and collecting chips (roadmap 3+7+10)

context: `VoicesViewModel.filterNews` and `VoicesViewModel.collectLabels` both resolve display names with `Constants.LABELS.entries.find { it.value == label }` inside nested loops over news items and label lists at `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt:104-113` and `:205-208`. Every filter or label-bar rebuild therefore rescans the label map per stored label value. Existing coverage lives at `app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesViewModelTest.kt`.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt`, specifically `filterNews` and `collectLabels`, and `app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesViewModelTest.kt`. Leave repository calls, share/delete actions, and flow wiring alone.

steps:
1. Build one reverse map from label value to display name once per `filterNews` and once per `collectLabels` call (or share a tiny private helper local to the viewmodel).
2. Replace every `Constants.LABELS.entries.find` lookup with reverse-map access, keeping `VoicesLabelManager.formatLabelValue` as the unknown-label fallback.
3. Preserve `"All"`, `"Shared Chat"`, shared-team-name labels, case-insensitive search, and empty-query short-circuit behavior.
4. Extend tests so filtering by a known label and collecting labels from mixed known/unknown values still match current results.
5. Do not cache the reverse map on the viewmodel instance unless tests prove `Constants.LABELS` is immutable for the process lifetime; prefer per-call locals.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.voices.VoicesViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that selecting a label chip and typing a search still show the same posts and label bar entries.

size budget: approximately 20-40 changed lines across 2 files.

out of scope: Do not change news schema, repository queries, Compose migration, or `Constants.LABELS` contents. Keeping filtering as platform-neutral viewmodel state transformation supports portable Compose screens under roadmap 10.

---

### 8. reverse-index label chips inside VoicesLabelManager (roadmap 7+8)

context: `VoicesLabelManager.getLabel` walks `Constants.LABELS.keys` linearly for every chip at `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt:113-119`, and `showChips` creates a fresh `ChipCloud` per label while calling `getLabel` once each (`:66-72`). Long voice threads therefore pay repeated map scans during bind. Existing tests are at `app/src/test/java/org/ole/planet/myplanet/services/VoicesLabelManagerTest.kt`.

files: Touch `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt`, specifically `getLabel` and any call sites inside `showChips` / `setupAddLabelMenu` that benefit from a shared reverse lookup, and `app/src/test/java/org/ole/planet/myplanet/services/VoicesLabelManagerTest.kt`. Leave add/remove suspend callbacks and popup-menu wiring alone except where required for the lookup change.

steps:
1. Build a value-to-display-name reverse map once per `showChips` (or lazily in the companion if `Constants.LABELS` is treated as fixed after init).
2. Resolve chip titles through the reverse map, falling back to `formatLabelValue` for unknown values.
3. Preserve close-mode delete behavior and the existing selected-label resolution in the delete listener.
4. Keep `updateAddLabelVisibility` set logic unchanged.
5. Add unit coverage for known labels, unknown raw labels, and empty label lists.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.VoicesLabelManagerTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that voice rows still show the same chip titles and that deleting a chip still removes the correct underlying label value.

size budget: approximately 15-30 changed lines across 2 files.

out of scope: Do not redesign chip UI, change label storage format, or pull Android view code into a shared KMP module in this task. The lookup itself is platform-free string work that can later move with roadmap 9.

---

### 9. build resource filter facets in one library pass (roadmap 1+7+9)

context: `ResourcesRepositoryImpl.getFilterFacets` walks the full library list four separate times to assemble languages, subjects, mediums, and levels at `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt:522-528`. Facet rebuilds run when opening resource filters and grow linearly with catalog size; a single accumulation pass removes three full scans without changing facet membership. Existing coverage is in `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`.

files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, specifically `getFilterFacets`, and `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt` if facet assertions need strengthening. Leave search SQL, shelf mutations, download URL assembly, and DAO interfaces alone.

steps:
1. Allocate four mutable sets and iterate `libraries` once.
2. Add non-blank `language` and `mediaType` values and flatten non-null `subject` / `level` lists into their sets during that pass.
3. Return the same map keys (`"languages"`, `"subjects"`, `"mediums"`, `"levels"`) with set values.
4. Preserve blank-filtering behavior for languages and mediums and empty-list handling for subjects/levels.
5. Extend or add a focused test that seeds mixed blank/non-blank facet fields and asserts exact set membership.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.ResourcesRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass. Verify that the resource filter dialog still lists the same languages, subjects, mediums, and levels for a known library sample.

size budget: approximately 15-30 changed lines across 1-2 files.

out of scope: Do not change facet key names, filter UI, Room queries, or search ranking. Single-pass aggregation is platform-free repository logic and advances the platform-free data core described by roadmap 9.

---

### 10. stop re-stripping server URLs while pinning the selected host (roadmap 7+8)

context: `SyncActivity.refreshServerList` in `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt:116-149` repeatedly applies `httpsPrefixRegex` while finding the pinned server, filtering duplicates, and computing `pinnedIndex`. With the additional-servers path enabled this is three linear passes and three regex replacements per address. The list is small but the pattern is pure avoidable work on a frequently opened sync dialog; `ServerConfigUtils.getFilteredList` already prepared the candidate list at `:117-121`.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt`, specifically `SyncActivity.refreshServerList`. Leave protocol radio handlers, `ServerConfigUtils`, adapter selection APIs, and preference reads alone except for local use of already-fetched values.

steps:
1. Compute `urlWithoutProtocol` once (already done) and strip each candidate URL once into a local structure or map before building `finalList`.
2. Find the pinned server and build the de-duplicated ordered list from the pre-stripped values.
3. Derive `pinnedIndex` from the same precomputed list instead of rescanning with fresh regex replacements.
4. Preserve pin-at-top ordering, fallback when the pinned server is absent, and the `submitList` completion callback that selects the pinned row.
5. Keep behavior identical when `showAdditionalServers` is false.

acceptance: `./gradlew testDefaultDebugUnitTest` passes. Verify manually that opening the server list with a pinned URL still places that server first when “show more” is enabled, selects it, and does not duplicate it.

size budget: approximately 15-30 changed lines in 1 file.

out of scope: Do not change server address models, PIN maps, protocol defaults, or `SyncActivity` lifecycle beyond this extension function. Avoid editing `SyncActivity.kt` itself (open PR collision). This is a focused UI-helper micro-optimization under roadmap 7 and does not schedule multiplatform extraction.

---

## self-check

- R1: exactly 10 tasks, each independently mergeable.
- R2: no production file appears in more than one task (`ExamAnswerUtils.kt`, `SelectionUtils.kt`, `UploadCoordinator.kt`, `NotificationsViewModel.kt`, `CollectionsFragment.kt`, `RealtimeSyncMixin.kt`, `VoicesViewModel.kt`, `VoicesLabelManager.kt`, `ResourcesRepositoryImpl.kt`, `ServerDialogExtensions.kt`).
- R3: open-PR collisions avoided for the cited production paths; notably skipped `ExamTakingFragment`, `SubmissionViewModel`, `ProgressRepositoryImpl`, `SubmissionsRepositoryImpl`, `CoursesRepositoryImpl`, `TeamsRepository*`, `UrlUtils`, `FileUtils`, `ResourcesFragment`, `SyncActivity.kt`, and `TransactionSyncManager`.
- R4: every path/class/function above was opened and line-checked on base `9c54a03`.
- R5: each task stays under ~150 lines and ~5 files, no new dependencies, no TODO placeholders.
- R6: this document is the deliverable; no implementation code is included.
