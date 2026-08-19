2026-08-19 · base: 9c54a0341 · open PRs checked: 15820, 15808, 15772, 15771, 15769, 15699, 15698, 15656, 15650, 15614, 15594, 15559, 15519, 15412, 15267, 15266, 15226, 15198, 15158, 15108, 14960, 14893, 14883, 14650, 14457, 14427, 14030, 13928, 13848, 13657, 13604, 13447, 13415, 13355, 13287, 13051, 10993, 8175, 7052, 5977, 5243, 4075

### 1. cache `UrlUtils.getUrl()` and `UrlUtils.header` in `SyncManager` hot sync methods (roadmap 5, 7, 9)

context: `SyncManager.resourceTransactionSync()` calls `UrlUtils.getUrl()` and `UrlUtils.header` at the count request (`SyncManager.kt:278`), inside the resource batch `while` loop (`:305`), and for every `logApiCall` (`:285, :313, :320`); `getShelvesWithDataBatchOptimized()` repeats the pattern at `:437`. `UrlUtils.getUrl()` rebuilds the server base URL from `SharedPreferences` on each call, and `UrlUtils.header` recomputes basic auth, so resolving them repeatedly inside the longest sync loop is wasted work. Caching them also moves roadmap 9 forward by reducing churn through the platform-specific `UrlUtils` in sync code.

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt` — functions `resourceTransactionSync()` and `getShelvesWithDataBatchOptimized()`. Do not touch `UrlUtils.kt` (open-PR territory), `TransactionSyncManager`, `ApiClient`, or `SyncStatus` emissions.

steps:
  1. In `resourceTransactionSync()`, capture `val baseUrl = UrlUtils.getUrl()` and `val header = UrlUtils.header` before the total-count API call.
  2. Replace every `UrlUtils.getUrl()` interpolation and `UrlUtils.header` usage inside `resourceTransactionSync()` with those locals, including inside the batch `while` loop.
  3. In `getShelvesWithDataBatchOptimized()`, capture `val baseUrl = UrlUtils.getUrl()` and `val header = UrlUtils.header` before the `getDocuments` call and reuse them.
  4. Verify the `UrlUtils` import is still needed before removing it.
  5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; a full sync still completes successfully and `SyncPerf` log URLs are unchanged; manual smoke test of login → sync shows resources and shelf data.

size budget: ~25 changed lines, 1 file

out of scope: do not refactor sync architecture, change `UrlUtils` implementation, or alter `SyncStatus` strings.

---

### 2. cache `UrlUtils.getUrl()` in `UploadCoordinator` batch loops (roadmap 5, 7, 9)

context: `UploadCoordinator.uploadBatch()` builds `"${UrlUtils.getUrl()}/${config.endpoint}"` for every item in a batch (`UploadCoordinator.kt:160, :162, :190`), and `uploadBatchRoom()` does the same (`:383, :385, :405`). Because `UrlUtils.getUrl()` reads `SharedPreferences` and allocates a new URL string each call, resolving it per uploaded item adds overhead inside a network loop. Reducing the `UrlUtils` surface here also keeps the upload path closer to a platform-free core for roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadCoordinator.kt` — functions `uploadBatch()` and `uploadBatchRoom()`. Do not touch `UrlUtils.kt`, `UploadRepository`, `UploadConfig`, or the retry queue.

steps:
  1. In `uploadBatch()`, capture `val baseUrl = UrlUtils.getUrl()` before the `batch.forEach` loop.
  2. Replace all three `UrlUtils.getUrl()` interpolations inside `uploadBatch()` with `baseUrl`.
  3. In `uploadBatchRoom()`, capture `val baseUrl = UrlUtils.getUrl()` before its `batch.forEach` loop and replace the three URL interpolations with `baseUrl`.
  4. Verify the `UrlUtils` import is still needed before removing it.
  5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; manual upload of pending records (ratings, submissions, etc.) succeeds and server URLs in debug logs are unchanged.

size budget: ~20 changed lines, 1 file

out of scope: do not change upload orchestration, batching, or `UploadConfig` definitions.

---

### 3. remove redundant `Set -> List -> Set -> List` conversions in `NotificationsRepositoryImpl` (roadmap 1, 7, 9)

context: `markNotificationsAsRead()` at `NotificationsRepositoryImpl.kt:115` and `deleteNotifications()` at `:391` each call `notificationDao.getByIds(...).map { it.id }.toSet().toList()` before passing a `List` to the next DAO call. The `Set` intermediate is unnecessary because `getByIds` already accepts a `List` and the input is already a `Set`, so the extra allocations only slow notification read/delete paths. This is a pure data-layer cleanup that advances roadmap 1 and removes Android-independent collection churn for roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — functions `markNotificationsAsRead()` and `deleteNotifications()`. Do not modify `NotificationDao`, `NotificationsViewModel`, or `NotificationsFragment`.

steps:
  1. In `markNotificationsAsRead()`, keep the mapped IDs as a `List<String>` (`existingIds`) and pass it directly to `notificationDao.markAsRead(existingIds, Date())`.
  2. Still return `existingIds.toSet()` so the public API contract remains unchanged.
  3. In `deleteNotifications()`, keep the mapped IDs as a `List<String>` (`deletedIds`) and pass it directly to `notificationDao.deleteByIds(deletedIds)`.
  4. Return `deletedIds.toSet()` to preserve the existing return type.
  5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; marking notifications read and deleting notifications still reflects immediately in the notifications list.

size budget: ~8 changed lines, 1 file

out of scope: do not change DAO signatures, `NotificationsViewModel`, or `NotificationsFragment`.

---

### 4. avoid per-bind `findViewById` and color lookups in `ResourcesTagsAdapter` (roadmap 6, 7, 10)

context: `createCheckbox()` calls `convertView.findViewById<CheckBox>(R.id.checkbox)` for every row bind (`ResourcesTagsAdapter.kt:111`), and `ChildViewHolder.bind()` resolves `ContextCompat.getColor` for background and text color on every bind (`:94-95`). View Binding already exposes `binding.checkbox`, and the colors are static, so these are wasted lookups in a tag list that can be long. Removing the per-bind resource lookups also makes the adapter easier to lift into Compose later (roadmaps 6 and 10).

files: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesTagsAdapter.kt` — `ParentViewHolder.bind()`, `ChildViewHolder.bind()`, and `createCheckbox()`. Do not touch `TagEntity`, `TagData`, `TagsFragment`, or the row layouts.

steps:
  1. Change `createCheckbox()` to accept a `CheckBox` parameter instead of a `View`.
  2. In `ParentViewHolder.bind()` and `ChildViewHolder.bind()`, pass `binding.checkbox` directly to `createCheckbox()`.
  3. Cache the `multi_select_grey` and `daynight_textColor` colors as `ChildViewHolder` fields initialized in the holder constructor.
  4. Use the cached colors in `ChildViewHolder.bind()` instead of calling `ContextCompat.getColor` each time.
  5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the resources tag filter panel still renders checkboxes and selected colors correctly when opening/closing child tags.

size budget: ~25 changed lines, 1 file

out of scope: do not convert the view to Compose or change selection logic.

---

### 5. cache per-bind colors and drawables in `FeedbackAdapter` (roadmap 6, 7, 10)

context: `onBindViewHolder()` resolves `ContextCompat.getColor` twice (`FeedbackAdapter.kt:53-54`), `ContextCompat.getDrawable` twice (`:56-57`), and builds a fresh `ColorStateList.valueOf()` twice per row. The feedback list is scrollable, so these resource lookups and allocations repeat for every visible item. Moving the resource resolution out of `onBindViewHolder()` also keeps the binding function stateless, which helps roadmap 6/10 Compose migration.

files: `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt` — `onBindViewHolder()` and `FeedbackViewHolder`. Do not touch `FeedbackDao`, `FeedbackDetailActivity`, or `FeedbackFragment`.

steps:
  1. Add adapter-level cached `Int` fields for `mainColor` and `md_amber_500`, initialized once in `onCreateViewHolder()`.
  2. Add adapter-level cached `ColorStateList` fields for the primary and grey tints.
  3. Replace per-bind `ContextCompat.getDrawable(context, R.drawable.bg_primary)` with `setBackgroundResource(R.drawable.bg_primary)` and reuse the cached tints.
  4. Cache the formatted open date (`getFormattedDate(feedback.openTime)`) in a local variable and reuse it for `contentDescription` and `tvOpenDate`.
  5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the feedback list still shows priority/status chips with the correct tinted background, and opening a feedback row still displays the right title and date.

size budget: ~35 changed lines, 1 file

out of scope: do not change chip logic, colors, or navigation to detail.

---

### 6. cache selected/default background colors in `UserArrayAdapter` (roadmap 6, 7, 10)

context: `onBindViewHolder()` and the payload branch both call `ContextCompat.getColor` for `md_grey_300` and `transparent` on every bind or selection change (`UserArrayAdapter.kt:43-45` and `:66-68`). The user list is shown during login and member selection, so these lookups repeat frequently. Hoisting the color resolution out of the bind path also leaves the row binding simpler for roadmap 6/10.

files: `app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt` — `onBindViewHolder()`, `onBindViewHolder(holder, position, payloads)`, and `ViewHolder`. Do not touch `UserEntity`, any user ViewModel, or login fragments.

steps:
  1. Add two adapter-level `Int` color fields for the selected and default background colors.
  2. Initialize the fields in `onCreateViewHolder()` using `parent.context`.
  3. Extract the selection background logic into a small helper that uses the cached colors.
  4. Use the helper in both the full `onBindViewHolder()` and the payload-only `onBindViewHolder()` path.
  5. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the user selection screen still highlights the selected user and preserves the highlighted row on configuration changes.

size budget: ~25 changed lines, 1 file

out of scope: do not change selection behavior, click handling, or `ImageUtils` loading.

---

### 7. replace regex split with `substringAfterLast` in `PersonalsAdapter.openResource` (roadmap 7, 9)

context: `openResource()` at `PersonalsAdapter.kt:66` compiles `\\.` to a `Regex` and allocates a `TypedArray` just to read a file extension every time a user opens a personal resource. For a simple extension check, `substringAfterLast` is faster and avoids the regex allocation; it is also a pure Kotlin stdlib change that removes a JVM `Regex` dependency on this path for roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt` — `openResource()`. Do not modify `FileUtils`, `ResourceViewerActivity`, or `ResourceViewerFragment`.

steps:
  1. Replace `path?.split("\\.".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()` with `path?.substringAfterLast(".")?.lowercase()`.
  2. Update the `when` expression to match against the lowercase extension string.
  3. Remove any now-unused imports after the regex is gone (do not remove `java.io.File`; it is still used for the `mp4` case).
  4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; opening a personal item still routes `.pdf`, images, `.mp3`, and `.mp4` to the correct viewer.

size budget: ~8 changed lines, 1 file

out of scope: do not change supported file types or viewer behavior.

---

### 8. remove per-byte `String.format` in `AndroidDecrypter.bytesToHex` (roadmap 7, 9)

context: `AndroidDecrypter.bytesToHex()` at `AndroidDecrypter.kt:49` calls `String.format("%02x", b)` for every byte of encrypted output. This creates a `Formatter` per byte and is a hotspot during key/IV generation and encryption at login. Replacing it with a pre-computed lookup table removes the locale-sensitive `String.format` and keeps the utility free of formatting machinery for roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt` — `bytesToHex()`. Do not touch `hexStringToByteArray()`, `encrypt()`, `decrypt()`, PBKDF2 logic, or any public method signatures.

steps:
  1. Pre-compute a lowercase hex `CharArray` in the companion object.
  2. Rewrite `bytesToHex()` to append two chars per byte from the lookup table without calling `String.format`.
  3. Preserve the exact lowercase output by masking each byte with `0xFF` and using the high/low nibbles as indices.
  4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; login encryption still works and `AndroidDecrypter` unit tests (if any) still pass; generated hex strings are byte-identical to before.

size budget: ~12 changed lines, 1 file

out of scope: do not change encryption algorithms, PBKDF2 parameters, or the public API.

---

### 9. cache selection colors in `ServerAddressAdapter.ViewHolder` (roadmap 6, 7, 10)

context: `ServerAddressAdapter.ViewHolder.updateSelectionState()` calls `ContextCompat.getColor` for `selected_color` and `transparent` every time selection changes (`ServerAddressAdapter.kt:106-112`), including via `notifyItemChanged` payloads. These colors are static for the adapter, so resolving them on every selection update is unnecessary. Moving them to the holder constructor also keeps the per-row state centralized for roadmap 6/10.

files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt` — `ViewHolder.updateSelectionState()` and `ViewHolder` constructor. Do not touch `SyncActivity`, `LoginActivity`, or `ServerAddress` model.

steps:
  1. Add two `Int` color fields to `ViewHolder` and initialize them in the constructor from `button.context`.
  2. Use the cached colors in `updateSelectionState()` instead of `ContextCompat.getColor` calls.
  3. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the server address list still highlights the selected server and clears highlight when selection changes.

size budget: ~12 changed lines, 1 file

out of scope: do not change selection logic, click behavior, or server configuration flow.

---

### 10. cache action strings and pending color in `TeamsAdapter` (roadmap 6, 7, 10)

context: `TeamsAdapter.showActionButton()` resolves `context.getString(R.string.edit)`, `R.string.leave`, `R.string.requested`, and `R.string.request_to_join` on every bind (`TeamsAdapter.kt:84, :94, :104, :114`) and calls `ContextCompat.getColor` for the pending-request indicator (`:107`). The teams list scrolls, so these resource lookups repeat for every visible team row. Hoisting them out of the bind path also keeps the action-button binding stateless for roadmap 6/10.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamsAdapter.kt` — `showActionButton()` and adapter initialization. Do not touch `TeamDetails`, `TeamStatus`, `TeamsViewModel`, or any team repository.

steps:
  1. Add adapter-level `String` fields for the four action strings and an `Int` field for `pending_request_indicator` color.
  2. Initialize those fields in `onCreateViewHolder()` using `parent.context`.
  3. In `showActionButton()`, replace the `context.getString(...)` calls with the cached strings and replace the `ContextCompat.getColor` call with the cached color.
  4. Run `./gradlew testDefaultDebugUnitTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the teams list still shows correct edit/leave/requested/request-to-join action icons, content descriptions, and pending-request coloring.

size budget: ~30 changed lines, 1 file

out of scope: do not change team membership logic or convert to Compose.
