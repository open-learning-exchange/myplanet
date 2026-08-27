# myPlanet refactor round: performance quick wins

date: 2026-08-27  
base commit: 89fd72c251df68ed01094091d4de7ba7a2571ebe  
open PRs checked: 4075, 8175, 10993, 13287, 13355, 13415, 13604, 13657, 13848, 13928, 14427, 14650, 14883, 14893, 14960, 15108, 15158, 15198, 15226, 15266, 15267, 15412, 15519, 15559, 15699, 15808, 15820, 15824, 15825, 15951, 16096, 16101, 16192, 16257, 16258, 16270, 16274

Focus: roadmap item 7 (optimize remaining performance hotspots). Each task is an independently mergeable micro-optimization. Where noted, tasks also reduce platform-specific API usage and therefore move roadmap 9 (kotlin multiplatform) forward.

---

### 1. Normalize resource search query once (roadmap 7)

context: `ResourcesSearchUtils.kt:9-14` builds `normalizedQueryParts` by splitting the raw query and normalizing every part, then builds `normalizedQuery` by normalizing the whole query a second time. `Utilities.normalizeText` performs Unicode normalization and regex diacritics removal, so running it twice per search is wasteful on large resource lists. This shows up directly in resource search latency because the function is called for every keystroke while the user types, and the library list can contain hundreds of items.

files: `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt` (`searchList`, `searchLocalModels`). Do not change any call sites in the resources/adapters packages or any other search utility.

steps:
1. Compute `val normalizedQuery = Utilities.normalizeText(query)` first.
2. Derive `val normalizedQueryParts` from `normalizedQuery` with `splitToSequence(" ").filter { it.isNotEmpty() }.toList()` instead of normalizing each part separately.
3. Keep the prefix-first ranking (`startsWithQuery` / `containsQuery`) and `searchLocalModels` unchanged.
4. Confirm the existing imports still cover the functions used after the edit.
5. Run `./gradlew testDefaultDebugUnitTest` and perform a resource search to verify ranking.
6. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the resources search still ranks exact prefix matches first and contains-all-words matches second.

size budget: ~4 changed lines, 1 file.

out of scope: do not modify `Utilities.normalizeText` or add stemming/fuzzy search. The change is limited to removing the duplicate normalization pass.

---

### 2. Avoid intermediate integer lists in version comparison (roadmap 7)

context: `VersionUtils.kt:43-53` builds two `List<Int>` on every `compareVersions` call via `split(".").map { it.toInt() }`. Version checks happen at startup, during update checks, and when deciding whether a sync is required, so removing the intermediate `List<Int>` allocations is a clear micro-win. The comparison is also small enough that lazy parsing keeps the code readable and avoids the cost of building a second collection.

files: `app/src/main/java/org/ole/planet/myplanet/utils/VersionUtils.kt` (`compareVersions`). Do not change `parseApkVersionString`, `isVersionAllowed`, or `getVersionCode`.

steps:
1. Keep `parts1` and `parts2` as the string lists from `split(".")` but remove the `.map { it.toInt() }` call.
2. Parse each numeric part lazily inside the comparison loop using `parts1[i].toInt()` and `parts2[i].toInt()`.
3. Preserve the existing `-lite` suffix stripping on the first argument, `v` prefix stripping on both arguments, and the final size comparison.
4. Verify that malformed inputs still throw the same `NumberFormatException` behavior as before.
5. Run `VersionUtilsTest` and `./gradlew testDefaultDebugUnitTest`.
6. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; `VersionUtilsTest` passes and version comparison behavior is identical.

size budget: ~6 changed lines, 1 file.

out of scope: do not introduce `SemVer`/`Version` libraries or change version parsing rules. The public signature of `compareVersions` must remain unchanged.

---

### 3. Replace repeated lowercasing with case-insensitive comparisons in exam answers (roadmap 7+9)

context: `ExamAnswerUtils.kt:71-92` calls `lowercase(Locale.getDefault())` on every correct choice and on the answer string inside `any`/`map` lambdas. For exams with many choices or retry attempts this creates many short-lived strings and repeated `Locale` lookups. The answer strings are typically short ASCII text, so locale-aware lowercasing is overkill and can be replaced with locale-independent case-insensitive comparison that also removes a JVM-specific API.

files: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt` (`checkSelectAnswer`, `checkTextAnswer`, `checkMultipleSelectAnswer`, `isEqual`). Do not touch `choiceDisplayValue`, `getChoiceTextById`, or the `choicesCache`.

steps:
1. In `checkSelectAnswer`, compare `ans` against each correct choice using `equals(..., ignoreCase = true)` instead of lowercasing both sides.
2. In `checkTextAnswer`, use `ans.contains(it, ignoreCase = true)` instead of lowercasing `ans` and each correct choice.
3. In `checkMultipleSelectAnswer`, build two lowercased, sorted lists directly and compare them, removing the `toTypedArray()` and `Arrays.sort` calls.
4. Remove now-unused `java.util.Locale` and `java.util.Arrays` imports if they become unused.
5. Run `ExamAnswerUtilsTest` and `./gradlew testDefaultDebugUnitTest`.
6. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; `ExamAnswerUtilsTest` passes; exam answer checking remains case-insensitive and order-independent for multi-select questions.

size budget: ~20 changed lines, 1 file.

out of scope: do not change the public `checkCorrectAnswer` signature or the `choiceDisplayValue` cache logic.

Note: Removing `Locale.getDefault()` calls reduces Android-specific API surface and advances roadmap 9.

---

### 4. Hoist locale lookup and remove loop re-initialization in ExamQuestion (roadmap 7+9)

context: `ExamQuestion.kt:29-35` calls `Locale.getDefault()` on every iteration of `setCorrectChoiceArray`, and `ExamQuestion.kt:95-105` resets `correctChoiceList` and re-parses the `correctChoice` array inside the choices loop. Both issues repeat work for each choice and the second one clears the list on every iteration, making the parsing O(n) instead of O(1) for array-style correct choices. These paths run during every exam sync, so the repeated work is not negligible.

files: `app/src/main/java/org/ole/planet/myplanet/model/ExamQuestion.kt` (`setCorrectChoiceArray`, `insertCorrectChoice`). Do not change the constructor, `getCorrectChoice`, `setCorrectChoices`, or `serializeQuestions`.

steps:
1. In `setCorrectChoiceArray`, store `Locale.getDefault()` in a local `val locale` before the loop and reuse it for each `lowercase` call.
2. In `insertCorrectChoice`, handle the `correctChoice` JsonArray case once before the `for` loop instead of resetting the list on every iteration.
3. Keep the single-string `correctChoice` logic inside the loop.
4. Review the `Locale` import and remove it only if it becomes unused after the edits.
5. Run `./gradlew testDefaultDebugUnitTest`.
6. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; synced exams still mark the correct choices and grading works for select, select-multiple, and text questions.

size budget: ~15 changed lines, 1 file.

out of scope: do not add a new persistent column or change the `correctChoiceList` type. The model structure must remain compatible with Room.

Note: Hoisting the `Locale.getDefault()` call is a small step toward removing platform-specific APIs from the model layer for roadmap 9.

---

### 5. Guard SyncTimeLogger hot log calls with Log.isLoggable (roadmap 7+8)

context: `SyncTimeLogger.kt` calls `Log.d("SyncPerf", "...${formatElapsed(elapsed)}...")` on every sync step. The interpolated strings invoke `formatElapsed`/`formatTime` and `String.format` even when the `SyncPerf` log tag is disabled at runtime, so release builds pay the allocation cost for log output that is never displayed. Sync produces dozens of these calls per sync, so the overhead adds up quickly.

files: `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` (`startLogging`, `stopLogging`, `logProcessCompletion`, `logApiCall`, `logDbOperation`, `logDetail`). Do not change `generateSummary` or `saveSummaryToRoom`.

steps:
1. Add a `private const val TAG = "SyncPerf"` if not already present.
2. Add a private inline helper that checks `Log.isLoggable(TAG, Log.DEBUG)` before calling `Log.d`.
3. Replace the 12 `Log.d("SyncPerf", ...)` call sites with the helper, passing the message as a lambda so `formatElapsed`, `formatTime`, and string concatenation are skipped when the tag is disabled.
4. Confirm no non-logging callers are affected and imports remain correct.
5. Run `./gradlew testDefaultDebugUnitTest`.
6. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; sync performance logs still appear when `adb shell setprop log.tag.SyncPerf VERBOSE` is set, and no log string is built when the `SyncPerf` tag is disabled.

size budget: ~30 changed lines, 1 file.

out of scope: do not rewrite the log format or remove the `generateSummary` formatting. The visible output must remain identical when logging is enabled.

---

### 6. Avoid combined set allocation in DownloadService.getRemainingCount (roadmap 5+7)

context: `DownloadService.kt:176-181` builds `priorityUrls + pendingUrls` (a new set) on every call to `getRemainingCount`, which is invoked repeatedly while `processDownloadQueue` drains the download queue. Each call constructs a union set and then counts it, which is unnecessary when the same count can be obtained by summing two independent counts. This is part of the sync/upload download path, so it touches roadmap 5 as well as roadmap 7.

files: `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt` (`getRemainingCount`). Do not change `processDownloadQueue`, `initDownload`, or `cleanupProcessedUrls`.

steps:
1. Count remaining items in `priorityUrls` and `pendingUrls` separately using `count { it !in processedUrls }`.
2. Return the sum and remove the `allUrls` intermediate set.
3. Confirm that `processedUrls` is still treated as a `Set` so `count` remains O(1) per element.
4. Run `./gradlew testDefaultDebugUnitTest`.
5. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the download queue progress indicator and remaining-count display are unchanged during file downloads.

size budget: ~4 changed lines, 1 file.

out of scope: do not refactor the download queue state machine or change `processedUrls` type. This is strictly a local change to `getRemainingCount`.

---

### 7. Cache parsed messages JsonArray in Feedback (roadmap 1+7)

context: `Feedback.kt:44-77` parses the `messages` JSON string every time `messageList` or `message` is accessed. Feedback detail and list adapters read these getters repeatedly while binding rows, so re-parsing is wasteful and allocates a new `List<FeedbackReply>` each time. Because `messages` is only mutated through `setMessages`, the parsed result can be cached safely with an `@Ignore` transient field without affecting Room persistence.

files: `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt` (`messageList`, `message`, `setMessages`). Do not change `serializeFeedback` or other model fields.

steps:
1. Add an `@Ignore private var parsedMessagesArray: JsonArray? = null` field for caching.
2. Clear the cache in `setMessages` when the JSON source changes.
3. Update `messageList` and `message` to use the cached parse if present, falling back to parsing once and storing the result.
4. Keep the existing null/empty guards and the `ar[0]` vs `ar[1..]` distinction intact.
5. Run `./gradlew testDefaultDebugUnitTest` and feedback-related tests.
6. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the feedback list and detail screens still show the original message and replies, and `Feedback` Room operations are unaffected.

size budget: ~15 changed lines, 1 file.

out of scope: do not add persistent columns or change the `messages` JSON schema. The cache must be `@Ignore` so Room does not try to persist it.

---

### 8. Replace String.format with BigInteger.toString(16) in Utilities.toHex (roadmap 7)

context: `Utilities.kt:87-89` uses `String.format("%x", BigInteger(1, it))` to produce a hex string for sync database names. `String.format` incurs `Formatter` overhead on every call, and the same output can be produced directly by `BigInteger.toString(16)`. The function is used whenever sync builds per-user database names, so the small overhead is multiplied across sync operations and shows up in the sync path.

files: `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt` (`toHex`). Do not touch other `Utilities` helpers.

steps:
1. Replace `String.format("%x", BigInteger(1, it))` with `BigInteger(1, it).toString(16)`.
2. Preserve the `arg?.toByteArray()?.let { ... } ?: ""` null/empty behavior.
3. Verify that `java.math.BigInteger` remains imported correctly.
4. Run `./gradlew testDefaultDebugUnitTest`.
5. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; user sync table names (`userdb-<hex>-<hex>`) remain identical for the same `planetCode` and `name` inputs.

size budget: ~2 changed lines, 1 file.

out of scope: do not change hashing or encoding algorithms. The only change is removing the `String.format` indirection.

---

### 9. Reduce per-label allocations in VoicesLabelManager.formatLabelValue (roadmap 7+9)

context: `VoicesLabelManager.kt:124-138` chains `replace("_", " ").replace("-", " ")` and calls `Locale.getDefault()` inside the `joinToString` lambda for every word of every chip label. Voice rows can contain several labels, so the repeated regex replacements and locale lookups add up during list scrolling and reduce frame budget.

files: `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt` (`formatLabelValue`). Do not change chip rendering, the `setupAddLabelMenu` popup logic, or `reverseLabels`.

steps:
1. Combine the two `replace` calls into a single regex replace.
2. Hoist `Locale.getDefault()` into a local `val locale` before `joinToString` and reuse it for all `lowercase`/`titlecase` calls inside the lambda.
3. Keep the `replaceFirstChar` lowercase guard behavior unchanged.
4. Ensure the `Locale` and `Regex` imports are still correct after the edit.
5. Run `./gradlew testDefaultDebugUnitTest` and verify the voices screen.
6. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; voice chip labels still render as title-cased strings with `_` and `-` converted to spaces.

size budget: ~6 changed lines, 1 file.

out of scope: do not change `Constants.LABELS` or the `reverseLabels` map construction. The visible formatting must stay the same.

Note: Removing repeated `Locale.getDefault()` lookups reduces Android-specific API usage and helps roadmap 9.

---

### 10. Eliminate intermediate filter lists in NotificationsRepositoryImpl batch methods (roadmap 1+7)

context: `NotificationsRepositoryImpl.kt` builds `map { }.filter { }.distinct()` chains in `getJoinRequestDetailsBatch` and `getTaskTeamNamesByTaskTitles`/`getTaskTeamNamesByTaskIds`, and creates an intermediate `Triple` list without an initial capacity. These batch methods run whenever the notifications badge or list is refreshed, so removing the intermediate lists reduces GC pressure without changing any query shape. The repository layer is part of roadmap 1, and the reduction of throwaway collections is part of roadmap 7.

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (`getJoinRequestDetailsBatch`, `getTaskTeamNamesByTaskTitles`, `getTaskTeamNamesByTaskIds`). Do not touch DAO calls or other notification methods.

steps:
1. Replace `map { }.filter { }.distinct()` with `mapNotNull { it.teamId?.takeIf { it.isNotEmpty() } }.distinct()` (or the equivalent `userId`/`teamId` extraction per method) to drop the intermediate filter list.
2. In `getJoinRequestDetailsBatch`, pre-size `intermediateList` with `ArrayList(joinRequests.size)` and apply the same `mapNotNull`/`distinct` simplification to `userIds`.
3. Keep the repository methods' return types and the downstream `getTeamNamesByIds`/`getUsersByIds` calls unchanged.
4. Confirm that order is preserved for the resulting lists because the DAO queries depend on it.
5. Run `./gradlew testDefaultDebugUnitTest` and notification tests.
6. Confirm the change introduces no new compiler warnings.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; notification badges and team/task detail payloads still resolve correctly.

size budget: ~15 changed lines, 1 file.

out of scope: do not change `TeamsNotificationsRepository` interfaces or `TeamTaskDao` queries. The optimization stays inside `NotificationsRepositoryImpl`.
