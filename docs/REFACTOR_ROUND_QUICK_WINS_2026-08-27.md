# myPlanet refactor round: quick-win work orders

**Date:** 2026-08-27  
**Base commit:** `45bac8d0050286289dbb8fa9680864a16758be5f`  
**Open PRs checked:** could not check open PRs (GitHub CLI had no authentication and both the GitHub API and web search were unavailable). Accordingly, these work orders avoid all `.github/workflows/**` files and the roadmap's large hot spots (`TeamsRepositoryImpl`, `SyncManager`, and `UploadManager`).  
**Workflow logs:** could not inspect the last default-branch test, release, or build logs for the same platform-access limitation; no task below claims evidence from those logs.

### 1. Avoid the intermediate map in resource searching (roadmap 7+9)

context: `ResourcesSearchUtils.searchList` builds `normalizedQueryParts` with a lazy split sequence but then materializes it before scanning titles (`app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt:7-20`). Keeping the normalized terms as a compact sequence-producing representation avoids an unnecessary transformed list while preserving search order and moves roadmap 9 forward because this utility already has no `android.*` imports.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt`, object `ResourcesSearchUtils`, function `searchList`. Leave `Utilities`, `ResourceListModel`, and all resource fragments and adapters unchanged.

steps:
1. Replace the split/filter/map/toList chain with a representation that normalizes each nonblank query term once without an intermediate mapped collection.
2. Preserve the existing exact-prefix-first grouping and all-terms containment semantics.
3. Keep the empty-query fast path returning the original list instance.
4. Remove imports only if this edit makes them unused; do not change the public signature.
5. Run the focused resource-search tests and the full default unit-test task.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ResourcesSearchUtilsTest*'` and `./gradlew testDefaultDebugUnitTest` pass. In the resources screen, an empty search remains unfiltered, prefix matches remain first, and multiword searches still require every normalized term.

size budget: about 5-12 changed lines in 1 file.

out of scope: Do not change normalization rules, ranking behavior, repositories, database queries, or UI rendering.

---

### 2. Short-circuit list-content comparison without index ranges (roadmap 7+8)

context: `Flow<List<T>>.distinctByContent` checks equal sizes and then allocates/iterates an indices range to compare corresponding elements (`app/src/main/java/org/ole/planet/myplanet/utils/FlowExtensions.kt:22-27`). A direct paired traversal is clearer and can stop on the first mismatch while retaining the flow contract; this is code-health and micro-performance work, not a navigation rewrite.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/utils/FlowExtensions.kt`, extension function `distinctByContent`. Leave all four lifecycle collection helpers and their Fragment/LifecycleOwner behavior unchanged.

steps:
1. Rework the equality predicate to pair old and new elements directly after the size guard.
2. Ensure `sameItem` is called at most once per corresponding pair and never when sizes differ.
3. Preserve order sensitivity, empty-list equality, and `distinctUntilChanged` behavior.
4. Keep the extension inline and retain its public signature.
5. Run the focused flow-extension tests and the complete default unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*FlowExtensionsTest*'` and `./gradlew testDefaultDebugUnitTest` pass. Screens collecting list flows still suppress identical consecutive lists but emit reordered, added, removed, or changed items.

size budget: about 3-8 changed lines in 1 file.

out of scope: Do not modify lifecycle state thresholds, collection cancellation semantics, callers, or introduce a new Flow abstraction.

---

### 3. Build download URLs in one pass (roadmap 5+7)

context: `DownloadUtils.downloadAllFiles` first creates a mapped `List` and then copies it into an `ArrayList` (`app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt:126-128`). Building the required mutable result directly removes one collection allocation on bulk offline-library downloads without changing the upload/download architecture.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`, object `DownloadUtils`, function `downloadAllFiles`. Leave notification-channel functions, service/work scheduling, `UrlUtils`, `DownloadService`, and `DownloadWorker` unchanged.

steps:
1. Allocate the returned `ArrayList<String>` with capacity based on `dbMyLibrary.size`.
2. Traverse `dbMyLibrary` once and append each `UrlUtils.getUrl` result in input order.
3. Preserve nullable `MyLibrary` handling by continuing to delegate every element to `UrlUtils.getUrl`.
4. Do not change the return type because existing service callers require `ArrayList<String>`.
5. Run the focused download utility tests and full default unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*DownloadUtilsTest*'` and `./gradlew testDefaultDebugUnitTest` pass. “Download all” queues the same URLs in the same order and displays unchanged progress/completion notifications.

size budget: about 4-10 changed lines in 1 file.

out of scope: Do not change URL derivation, deduplicate downloads, alter WorkManager requests, or edit notification text.

---

### 4. Collect valid notified task IDs during notification delivery (roadmap 5+7)

context: `TaskNotificationWorker.doWork` iterates pending tasks to show notifications, then traverses them again with `mapNotNull` and `filter` to build IDs (`app/src/main/java/org/ole/planet/myplanet/services/TaskNotificationWorker.kt:44-58`). Capturing valid IDs during the existing notification loop removes two collections and keeps marking coupled to the tasks actually processed.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/services/TaskNotificationWorker.kt`, class `TaskNotificationWorker`, function `doWork`. Leave `TeamsRepository`, `NotificationsRepository`, `NotificationUtils`, scheduling, and notification formatting unchanged.

steps:
1. Create a capacity-aware mutable ID collection immediately before the existing task loop.
2. After each notification is shown, append its nonblank ID when present.
3. Remove the later `mapNotNull`/`filter` pipeline and pass the collected IDs to `markTasksNotified`.
4. Preserve exception boundaries, notification order, blank-ID behavior, and `Result.success()`.
5. Run the worker-specific tests and the complete default unit-test task.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*TaskNotificationWorkerTest*'` and `./gradlew testDefaultDebugUnitTest` pass. Due-task notifications appear once as before, and only tasks with nonblank IDs are marked notified after display.

size budget: about 6-14 changed lines in 1 file.

out of scope: Do not change worker cadence, repository queries, deadline windows, storage notifications, or failure policy.

---

### 5. Reuse parsed URI user-info in server URL mapping (roadmap 1+7)

context: `ServerUrlMapper` parses URLs and separately tests raw strings for `@` while constructing mappings (`app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt:30-38` and `:59-76`). Consolidating the already-parsed URI facts avoids repeated string scans and makes URL handling easier to extract later, although roadmap 9 is not completed because this class still uses Android URI APIs.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`, class `ServerUrlMapper`, functions `extractBaseUrl` and `mapAlternativeUrl`. Leave network connection behavior, `SharedPreferences`, `DispatcherProvider`, and build-config mappings unchanged.

steps:
1. Use parsed URI user-info rather than raw `contains("@")` checks wherever the same URL has already been parsed.
2. Parse each alternative URL no more than once per mapping operation.
3. Preserve credentials, scheme, host, explicit/default port behavior, and malformed-URL fallbacks.
4. Keep I/O on `dispatcherProvider.io` and retain the existing public `UrlMapping` contract.
5. Run server URL mapper tests and the full default unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ServerUrlMapperTest*'` and `./gradlew testDefaultDebugUnitTest` pass. Login/sync maps credentialed and noncredentialed alternative server URLs exactly as before, including explicit ports.

size budget: about 10-25 changed lines in 1 file.

out of scope: Do not add endpoints, alter reachability timeouts, migrate URI libraries, or change persisted preference keys.

---

### 6. Compare decrypted payload prefixes without slicing (roadmap 7+9)

context: `AndroidDecrypter.decrypt` creates a prefix slice solely to compare the encrypted payload with the IV, then creates another slice to strip that prefix (`app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt:63-78`). An offset comparison can eliminate the comparison copy while preserving legacy ciphertext support, and the crypto helper already has zero `android.*` imports, advancing roadmap 9.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt`, class `AndroidDecrypter`, function `decrypt`. Leave `encrypt`, PBKDF2 verification, key/IV generation, cipher algorithm strings, and error behavior unchanged.

steps:
1. Replace prefix `sliceArray` comparison with a bounds-safe indexed or range comparison that allocates no prefix array.
2. Strip the IV only when every prefix byte matches the provided IV, exactly as today.
3. Preserve the legacy path that decrypts payloads containing ciphertext only.
4. Keep null handling and the current nullable return contract unchanged.
5. Run decrypter tests and the complete default unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*AndroidDecrypterTest*'` and `./gradlew testDefaultDebugUnitTest` pass. Both new-format IV-prefixed ciphertext and legacy ciphertext decrypt to the same plaintext; malformed input still returns null.

size budget: about 5-15 changed lines in 1 file.

out of scope: Do not change algorithms, padding, key derivation, exception reporting, or stored ciphertext format.

---

### 7. Generate IVs with the platform secure-random singleton (roadmap 7+9)

context: `AndroidDecrypter.generateIv` constructs a new `SecureRandom` for every IV before filling only 16 bytes (`app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt:103-112`). This task is intentionally assigned to the existing dedicated test file instead of the production file owned by task 6: validate and document a subsequent agent-safe change through a focused regression test, while keeping platform-free crypto behavior moving toward roadmap 9.

files: Touch only `app/src/test/java/org/ole/planet/myplanet/utils/AndroidDecrypterTest.kt`, class `AndroidDecrypterTest` (confirmed by repository search). Leave production `AndroidDecrypter.kt` and all authentication callers unchanged in this independently mergeable task.

steps:
1. Add a focused test that calls `generateIv` repeatedly and verifies every result is nonempty hexadecimal text of the expected 16-byte encoded length.
2. Assert independently generated values are not all identical, without relying on a fixed random value.
3. Keep the sample count small enough for deterministic, fast unit execution.
4. Use existing test libraries and conventions only; add no dependency or helper production code.
5. Run the focused test class and the complete default unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*AndroidDecrypterTest*'` and `./gradlew testDefaultDebugUnitTest` pass. The regression test proves generated IVs remain correctly shaped and nonconstant without affecting app-visible encryption behavior.

size budget: about 12-25 changed lines in 1 file.

out of scope: Do not edit production crypto, benchmark randomness, assert a specific IV, or change encryption formats.

---

### 8. Compare version components without allocating integer lists (roadmap 7+9)

context: `VersionUtils.compareVersions` splits both version strings and maps every component into boxed integer lists before comparison (`app/src/main/java/org/ole/planet/myplanet/utils/VersionUtils.kt:43-52`). Parsing components during traversal reduces transient allocations in update checks; extracting the comparison into a platform-free private helper also clarifies the seam toward roadmap 9 even though the containing object retains Android version APIs.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/utils/VersionUtils.kt`, object `VersionUtils`, functions `compareVersions` and a private parsing helper if needed. Leave `getVersionCode`, `getVersionName`, `getAndroidId`, and `parseApkVersionString` unchanged.

steps:
1. Normalize the existing `v` prefix and `-lite` suffix exactly as current behavior requires.
2. Traverse dot-separated components without first building two `List<Int>` values.
3. Preserve numeric comparison, unequal component-count ordering, and current invalid-number failure behavior.
4. Keep `compareVersions` and `isVersionAllowed` signatures unchanged.
5. Run version utility tests and the full default unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*VersionUtilsTest*'` and `./gradlew testDefaultDebugUnitTest` pass. Update gating produces unchanged results for prefixed, lite, equal, shorter, and longer versions.

size budget: about 12-30 changed lines in 1 file.

out of scope: Do not redefine semantic-version rules, accept new malformed inputs, change APK parsing, or touch package-manager code.

---

### 9. Stop flattening sync timing logs for aggregate totals (roadmap 5+7+9)

context: `SyncTimeLogger.generateSummary` flattens all API and database timing lists before summing durations (`app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt:283-290`). Nested `sumOf` produces the same totals without allocating flattened lists, improving large-sync diagnostics; the utility has no Android framework import, which directly advances roadmap 9.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt`, class `SyncTimeLogger`, function `generateSummary`. Leave logging data structures, `extractProcessName`, formatting, and all sync managers unchanged.

steps:
1. Replace each `values.flatten().sumOf` duration aggregation with nested summation over existing lists.
2. Compute each aggregate once and reuse it for percentage calculation.
3. Preserve empty-map handling, total-duration guarding, `Locale.US` formatting, and summary text.
4. Do not change log retention, synchronization, or public methods.
5. Run timing logger tests and the complete default unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*SyncTimeLoggerTest*'` and `./gradlew testDefaultDebugUnitTest` pass. Sync summaries display the same network, database, and other-processing percentages and labels for empty and populated timing data.

size budget: about 6-16 changed lines in 1 file.

out of scope: Do not alter sync execution, add metrics, change log levels, or redesign the timing model.

---

### 10. Parse local-network hosts once in server configuration (roadmap 1+7)

context: `ServerConfigUtils.isLocalNetwork` derives a host with two separate `split` calls and temporary lists before applying address checks (`app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt:72-80`). Replacing that pipeline with delimiter-based extraction removes obvious allocations in server selection; this does not claim roadmap 9 because the same object still imports Android classes.

files: Touch only `app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt`, object `ServerConfigUtils`, private function `isLocalNetwork`. Leave `saveAlternativeUrl`, pin maps, server lists, `ProcessUserDataActivity`, and `SharedPrefManager` unchanged.

steps:
1. Derive the candidate host using nonallocating substring/delimiter operations rather than chained `split` lists.
2. Preserve behavior for bare hosts, host-and-port strings, slash-containing inputs, IPv4 private ranges, localhost, loopback, and `.local` names.
3. Keep `localNetworkRegex` and `getDefaultProtocol` decision order unchanged.
4. Avoid changing URL canonicalization or accepting additional network ranges.
5. Run server configuration tests and the full default unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests '*ServerConfigUtilsTest*'` and `./gradlew testDefaultDebugUnitTest` pass. Server setup still chooses HTTP for the existing local/special hosts and HTTPS for ordinary remote hosts.

size budget: about 4-12 changed lines in 1 file.

out of scope: Do not change server pins, trusted-host lists, protocols, persistence, UI ordering, or introduce a URL parsing dependency.
