# myPlanet refactor round — 10 work orders

Generated 2026-10-01. These are verbatim work orders for coding agents (jules, codex, copilot, devin, openhands, claude, qwen). Each task is independently mergeable in any order.

**Open-PR status at generation time: checked** (33 open PRs enumerated via GitHub API). Hot and in-flight collision areas were strictly excluded from scope:

- PR #15808 (sequence-based sync): `AppDatabase.kt`, `RoomModule.kt`, `ServiceModule.kt`, 14 DAOs, 13 repository interfaces and implementations (`Chat`, `Community`, `Courses`, `Feedback`, `Health`, `Progress`, `Ratings`, `Submissions`, `Surveys`, `Tags`, `Teams`, `User`, `Voices`)
- PR #17694 (upload mutations): `UploadRepository*`, `BulkDocsUploader`, `FileUploader`, `PhotoUploader`, `TeamsUploader`, `VoicesUploader`, `AchievementUploader`, `PersonalsRepositoryImpl*`
- PRs #17187 / #17254 / #17435 (resources): `ResourcesFragment`, `ResourcesAdapter`, `ResourcesViewModel`, `ResourcesRepositoryImpl`, `ResourceCardHelper`
- PR #17680 (sync decomposition): `SyncManager.kt`
- PR #17811 (sync UI): `SyncConfigurationCoordinator.kt`, `SyncActivity.kt`, `ProcessUserDataActivity.kt`
- PR #16623 (exam models): exam models, entities, and DAOs
- PR #15951: `TimeUtils.kt`
- PR #15825 / #15824 / #15820: `SearchActivityDao.kt`, `CoursesRepository*`, `Configuration*`
- PR #15559: `ExamTakingFragment.kt`
- PR #15267 / #15266: `ChatDetailFragment.kt`, `ChatViewModel.kt`, chat mappers
- PR #15226 / #14427: `FeedbackListViewModel.kt`, feedback UI
- PR #15108: `LoginActivity.kt`, `LoginViewModel.kt`
- PR #14883: `SubmissionsFragment.kt`
- PR #13657 / #13848: `SurveysViewModel.kt`, `SurveyFragment.kt`
- PR #13604 / #16594 / #17776: `CourseFilterController.kt`, `CourseSelectionController.kt`, `CoursesFragment.kt`, `CourseDetailFragment.kt`

## Focus of this round
Performance quick wins · micro-optimizations that unblock bigger refactors · obvious inefficiencies removable without rewrites.

## Roadmap Reference
1. finish cleaning the data layer
2. introduce global navigation architecture
3. expand viewmodel and use-case layers
4. complete dependency-injection cleanup
5. consolidate sync and upload workflow
6. migrate ui incrementally to compose
7. optimize remaining performance hotspots
8. improve code health and add tests
*North star (never scheduled directly, never blocked):*
9. kotlin multiplatform: a platform-free kotlin core — repositories, models, sync/upload logic and use cases end up with zero android.* imports
10. compose multiplatform: every compose screen from 6 stays portable — state hoisted into viewmodels, no android views inside composables, no direct R.*

---

### Task 1 — Batch Dictionary Seeding and Debounce Word Search

**Roadmap:** 1 (finish cleaning the data layer), 7 (optimize remaining performance hotspots)
**Also moves 9/10:** Partial. Moves `DictionaryRepositoryImpl` closer to 9 by isolating data mapping and chunked database persistence behind clean coroutine boundaries with no Android platform coupling.

**Why / user impact**
The offline dictionary contains thousands of terms. When seeded for the first time, inserting all entities in a single Room database transaction causes memory pressure, GC pauses, and risks SQLite bind-variable limits. Furthermore, when users type into the dictionary search bar, every keystroke launches an uncoordinated coroutine querying Room, causing UI jank and race conditions where older query responses overwrite newer ones.

**Verified problem**
1. In `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`, `insertDictionaryData()` parses the JSON array and calls `dictionaryDao.insertAll(entities)` in one massive list insertion without batch chunking.
2. In `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`, `searchWord(word: String)` triggers `viewModelScope.launch` on every invocation without cancelling the previous in-flight search `Job?`, causing race conditions and redundant SQLite queries.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`

**Change instructions**
1. In `DictionaryRepositoryImpl.kt`:
   - In `insertDictionaryData()`, chunk `entities` using `.chunked(500)` and pass each chunk to `dictionaryDao.insertAll(chunk)` within the existing `seedMutex.withLock` block.
2. In `DictionaryViewModel.kt`:
   - Introduce a private `searchJob: Job? = null` property.
   - In `searchWord(word: String)`:
     - Cancel any existing `searchJob` before launching a new search (`searchJob?.cancel()`).
     - Assign the new `viewModelScope.launch` to `searchJob`.
     - Retain trimming and empty check: if `query.isEmpty()`, set `_searchState.value = DictionarySearchState.Idle` and return.

**Acceptance**
- `insertDictionaryData()` inserts dictionary entries in chunks of 500 items.
- Rapid typing in `DictionaryViewModel.searchWord` cancels previous in-flight search coroutines, ensuring only the latest query updates `_searchState`.
- Unit tests in `DictionaryRepositoryImplTest` and `DictionaryViewModelTest` pass.

**Out of scope**
- Modifying `DictionaryDao.kt` or `DictionaryEntity.kt`.
- Room database schema changes or bumping database version in `AppDatabase.kt`.
- UI changes in `DictionaryActivity.kt`.

**Tests**
- Update or add tests in `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt` verifying chunked insertion.
- Update or add tests in `app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt` verifying that rapid consecutive `searchWord` calls cancel the prior query and deliver the final result.

**Constraints**
- Under 150 changed lines across 2 files. No new dependencies.

---

### Task 2 — Optimize Server Reachability Probing and Local Network Evaluation

**Roadmap:** 5 (consolidate sync and upload workflow), 7 (optimize remaining performance hotspots)
**Also moves 9/10:** Partial. Eliminates expensive regex invocations and replaces full GET payloads with lightweight HEAD requests during server reachability checks.

**Why / user impact**
During sync setup and connectivity monitoring, the app repeatedly probes configured server endpoints. Using full `GET` requests downloads entire response bodies over slow satellite links just to check if the server is alive, wasting bandwidth and battery. Additionally, `ServerConfigUtils` repeatedly applies regex matching on every local network URL evaluation.

**Verified problem**
1. In `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`, `isUrlDirectlyReachable(url: String)` creates an `HttpURLConnection` and issues a `GET` request (`connection.requestMethod = "GET"`). It also lacks a `try ... finally` block around `connection.disconnect()`, risking leaked sockets on timeout or read failure.
2. In `app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt`, `isLocalNetwork(url: String)` uses `localNetworkRegex = Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")` and runs `host.matches(localNetworkRegex)` on every check, allocating matcher state even for non-172 addresses.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`
- `app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt`

**Change instructions**
1. In `ServerUrlMapper.kt`:
   - In `isUrlDirectlyReachable(url: String)`:
     - Set `connection.requestMethod = "HEAD"` to avoid body downloads. If `responseCode == HttpURLConnection.HTTP_BAD_METHOD` (405), fallback to `"GET"`.
     - Ensure `connection.disconnect()` is called in a `finally` block so connections are closed even on exceptions.
2. In `ServerConfigUtils.kt`:
   - In `isLocalNetwork(url: String)`:
     - Fast-path check: check `host.startsWith("172.")` before evaluating the 172.16.0.0/12 range.
     - Parse the second octet integer (`val second = host.substringAfter("172.").substringBefore('.').toIntOrNull()`) and check `second in 16..31`, replacing regular expression matching with integer arithmetic.

**Acceptance**
- `ServerUrlMapper.isUrlDirectlyReachable` uses `HEAD` requests and guarantees connection disconnection in a `finally` block.
- `ServerConfigUtils.isLocalNetwork` correctly identifies 172.16.x.x through 172.31.x.x without regex matcher allocation.
- Existing tests in `ServerUrlMapperTest` and `ServerConfigUtilsTest` pass.

**Out of scope**
- Changing `processUrl` or preference persistence in `ServerUrlMapper`.
- Modifying `SyncManager.kt` or `TransactionSyncManager.kt`.

**Tests**
- Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.sync.ServerUrlMapperTest"`
- Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.ServerConfigUtilsTest"`

**Constraints**
- Under 100 changed lines across 2 files. No new dependencies.

---

### Task 3 — Eliminate Dual Serialization and Bound Parallelism in Shelf Sync

**Roadmap:** 5 (consolidate sync and upload workflow), 7 (optimize remaining performance hotspots)
**Also moves 9/10:** Partial. Replaces legacy Gson tree construction with direct `kotlinx.serialization` JSON building, moving `SyncRepositoryImpl` toward pure Kotlin multiplatform serialization (Roadmap 9).

**Why / user impact**
When downloading shelf items (resources, courses, meetups, teams), `SyncRepositoryImpl` runs parallel batch requests. On every batch, it serializes document IDs into a Gson `JsonObject`, then converts that Gson object into a `kotlinx.serialization.json.JsonObject` via string/element bridge, allocating two complete JSON ASTs per batch. In addition, launching unbounded parallel coroutines across all shelf types overwhelms low-end devices and slow Wi-Fi links.

**Verified problem**
1. In `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`, `processShelfDataOptimizedSync()` lines 131-143 construct a Gson `JsonObject()`, add a Gson tree via `keysObject.add("keys", gson.toJsonTree(batch))`, and call `keysObject.toKotlinx().jsonObject`.
2. In `processShelfParallel()`, `Constants.shelfDataList.mapNotNull { ... async(dispatcherProvider.io) { processShelfDataOptimizedSync(...) } }` launches all shelf data types concurrently without a concurrency gate.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`

**Change instructions**
1. In `SyncRepositoryImpl.kt`:
   - In `processShelfDataOptimizedSync()`:
     - Replace the Gson `keysObject` creation with `kotlinx.serialization.json.buildJsonObject`:
       ```kotlin
       val requestBody = buildJsonObject {
           putJsonArray("keys") {
               for (id in batch) add(JsonPrimitive(id))
           }
       }
       ```
     - Pass `requestBody` directly to `apiInterface.postDoc(...)`, eliminating `gson.toJsonTree` and `toKotlinx()`.
   - In `processShelfParallel()`:
     - Limit parallel shelf processing using a `Semaphore(2)` or running sequential processing per shelf category to prevent I/O thrashing on SQLite and network threads.

**Acceptance**
- Shelf batch requests build their JSON body using `buildJsonObject` with zero intermediate Gson AST allocations.
- Parallel shelf operations are bounded, avoiding thread starvation on low-memory devices.
- Unit tests in `SyncRepositoryImplTest` pass.

**Out of scope**
- Modifying `SyncManager.kt` or `TransactionSyncManager.kt` (off-limits under R3).
- Modifying `ApiInterface.kt`.

**Tests**
- Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.SyncRepositoryImplTest"`

**Constraints**
- Under 60 changed lines in 1 file. No new dependencies.

---

### Task 4 — Eliminate Redundant Regex Extraction in Download Error Diagnostics

**Roadmap:** 1 (finish cleaning the data layer), 7 (optimize remaining performance hotspots)
**Also moves 9/10:** Partial. Moves `DownloadRepositoryImpl` closer to 9 by removing reflection/regex-based string scanning on Android/OkHttp response structures.

**Why / user impact**
When downloading educational resources or media files, missing files (HTTP 404) trigger a diagnostics log. `DownloadRepositoryImpl` currently converts the entire OkHttp `Response` object into a debug string (`response.toString()`) and executes a regular expression to parse out the URL that was just requested, wasting CPU cycles and string allocations on every missing file.

**Verified problem**
In `app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepositoryImpl.kt`, lines 53-64:
```kotlin
if (response.code() == 404) {
    try {
        val responseString = response.toString()
        val matchResult = URL_REGEX.find(responseString)
        val extractedUrl = matchResult?.groupValues?.get(1)
        diagnosticsRepository.saveLogToRoom("File Not Found", "$extractedUrl", "${timeProvider.now()}")
    } ...
```
The requested URL is already available directly as the method parameter `url`, or via `response.raw().request.url.toString()`. Stringifying the response and running `URL_REGEX = Regex("url=([^}]*)")` is completely redundant.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepositoryImpl.kt`

**Change instructions**
1. In `DownloadRepositoryImpl.kt`:
   - In `downloadFileResponse()` when `response.code() == 404`:
     - Directly log `url` (or `response.raw().request.url.toString()`) to `diagnosticsRepository.saveLogToRoom("File Not Found", url, "${timeProvider.now()}")`.
     - Remove the `response.toString()`, `URL_REGEX.find(...)`, and fallback catch logic.
   - Remove the unused `URL_REGEX` from `companion object`.

**Acceptance**
- 404 download errors record the file URL in `diagnosticsRepository` without allocating `response.toString()` or running `URL_REGEX`.
- `URL_REGEX` is removed from `DownloadRepositoryImpl`.
- Unit tests in `DownloadRepositoryImplTest` pass.

**Out of scope**
- Modifying `DownloadService.kt` or `DownloadWorker.kt`.
- Changing `DiagnosticsRepository` or Room schemas.

**Tests**
- Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DownloadRepositoryImplTest"`

**Constraints**
- Under 30 changed lines in 1 file. No new dependencies.

---

### Task 5 — Eliminate Main-Thread File Probing and Crash Traps in AudioRecorder

**Roadmap:** 7 (optimize remaining performance hotspots), 8 (improve code health and add tests)
**Also moves 9/10:** No. Purely Android media service stabilization and main-thread I/O elimination.

**Why / user impact**
When users record voice notes in voices or team discussions, `AudioRecorder` executes a blocking loop checking disk file existence up to 100 times on the main thread for UUID-based filenames, triggering Android StrictMode disk violations. Additionally, if a user taps stop immediately after start before audio data is captured, `stopRecording()` passes the `MediaRecorder.stop()` exception directly to `MainApplication.handleUncaughtException(e)`, causing an unrecoverable application crash.

**Verified problem**
1. In `app/src/main/java/org/ole/planet/myplanet/services/AudioRecorder.kt`, `createAudioFile()` runs a `do ... while (audioFile.exists() && attempt < 100)` loop performing synchronous disk I/O on the main thread for UUID-generated file names.
2. In `stopRecording()`, catching `RuntimeException` from `recorder.stop()` re-routes to `MainApplication.handleUncaughtException(e)` instead of notifying the listener via `audioRecordListener?.onError(...)`.
3. In `forceStop()`, `myAudioRecorder?.stop()` is not wrapped in a `try-catch`, crashing if called while in an uninitialized state.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/services/AudioRecorder.kt`

**Change instructions**
1. In `AudioRecorder.kt`:
   - In `createAudioFile()`:
     - Replace the 100-attempt `audioFile.exists()` loop with a single deterministic file instantiation using `UUID.randomUUID().toString() + ".aac"`.
   - In `stopRecording()`:
     - Catch `RuntimeException` (or `IllegalStateException`) from `recorder.stop()` and `recorder.release()`, clean up `myAudioRecorder = null`, and report the issue to `audioRecordListener?.onError("Recording stopped before audio was captured")` rather than calling `MainApplication.handleUncaughtException(e)`.
   - In `forceStop()`:
     - Guard `recorder.stop()` with `try-catch` to avoid crashes when force-stopping an un-started recorder.

**Acceptance**
- `createAudioFile()` performs zero synchronous disk existence checks on the UI thread.
- Stopping a recording prematurely or calling `forceStop()` does not trigger `handleUncaughtException` or crash the app.
- Existing audio recording listeners receive proper callback events.

**Out of scope**
- Changing audio recording UI layouts or fragments.
- Changing `AudioPlayerActivity.kt` or `Media3` playback.

**Tests**
- Add unit test in `app/src/test/java/org/ole/planet/myplanet/services/AudioRecorderTest.kt` verifying premature stop handling and unique file path creation.

**Constraints**
- Under 50 changed lines in 1 file. No new dependencies.

---

### Task 6 — Fix Calculation Bug and Main-Thread Pressure in ANRWatchdog

**Roadmap:** 7 (optimize remaining performance hotspots), 8 (improve code health and add tests)
**Also moves 9/10:** No. Android main-looper watchdog timing and allocation optimization.

**Why / user impact**
`ANRWatchdog` monitors the Android main thread to log application unresponsiveness. Because of a calculation bug, the duration reported for an ANR is calculated before the delay, always reporting ~0 ms. Furthermore, the watchdog checks every `timeout / 2` (2500ms) rather than `timeout` (5000ms), causing premature false-positive ANR warnings and heavy stack trace string allocations.

**Verified problem**
In `app/src/main/java/org/ole/planet/myplanet/utils/ANRWatchdog.kt`, lines 48-78:
1. `val currentTime = SystemClock.elapsedRealtime()` is captured before `delay(timeout / 2)`. When an ANR occurs, `val duration = currentTime - lastTick` subtracts the timestamp captured *before* the delay from `lastTick`, reporting 0ms duration.
2. The check interval is `timeout / 2` (2500ms), which flags an ANR after 2500ms of main thread activity instead of the intended 5000ms (`DEFAULT_ANR_TIMEOUT`).
3. Inside the ANR reporting block, stack trace formatting allocates multiple intermediate string objects.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/utils/ANRWatchdog.kt`

**Change instructions**
1. In `ANRWatchdog.kt`:
   - In `start()` loop:
     - Record `val checkStartTime = SystemClock.elapsedRealtime()`.
     - Post `tickUpdater` to `mainHandler`.
     - `delay(timeout)` (use the full configured timeout duration so that ANRs are not reported prematurely).
     - If `isWatching && lastTick == tick`:
       - Compute `val duration = SystemClock.elapsedRealtime() - checkStartTime` so the reported blocked duration reflects the actual elapsed time.
     - Optimize stack trace formatting in `StringBuilder` to pre-size with `StringBuilder(1024)`.

**Acceptance**
- ANR reports reflect true blocked duration (`>= timeout`) instead of ~0ms.
- The watchdog respects the full `timeout` period before declaring an ANR.
- Stack trace string allocations during an ANR are minimized.

**Out of scope**
- Modifying `MainApplication.kt`.
- Changing `CrashLogStore.kt`.

**Tests**
- Add or update tests in `app/src/test/java/org/ole/planet/myplanet/utils/ANRWatchdogTest.kt` verifying correct timeout duration and non-zero duration calculation.

**Constraints**
- Under 40 changed lines in 1 file. No new dependencies.

---

### Task 7 — Cache SecureRandom and Eliminate Redundant Arrays in AndroidDecrypter

**Roadmap:** 7 (optimize remaining performance hotspots), 8 (improve code health and add tests)
**Also moves 9/10:** Yes. `AndroidDecrypter` is pure cryptography; reusing the RNG and eliminating redundant array copies prepares it for a platform-free KMP security module.

**Why / user impact**
User authentication and local credential encryption rely on `AndroidDecrypter`. Every call to `generateIv()` instantiates a new `SecureRandom()`, which incurs costly OS entropy reads from `/dev/urandom`. Furthermore, `encrypt()` allocates byte arrays and immediately performs `System.arraycopy` on newly allocated arrays from `hexStringToByteArray()`, doubling allocations on every password or PIN encryption.

**Verified problem**
1. In `app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt`, line 117: `val random = SecureRandom()` is instantiated inside `generateIv()` on every invocation.
2. Lines 24-28:
   `val ivBytes = ByteArray(ivSize)`
   `iv?.let { hexStringToByteArray(it) }?.let { System.arraycopy(it, 0, ivBytes, 0, ivBytes.size) }`
   `hexStringToByteArray` already allocates and returns a `ByteArray`. Allocating `ivBytes` and copying into it is completely redundant (and identical for `keyBytes`).

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt`

**Change instructions**
1. In `AndroidDecrypter.kt`:
   - In `companion object`:
     - Maintain a shared thread-safe `private val secureRandom = SecureRandom()` instance instead of re-instantiating `SecureRandom()` in `generateIv()`.
   - In `encrypt()`:
     - Directly use the result of `hexStringToByteArray(iv)` (padded or validated to 16 bytes only if necessary) without allocating a redundant `ByteArray(ivSize)` and performing `System.arraycopy`.
     - Directly use the result of `hexStringToByteArray(key)` without the redundant `ByteArray(32)` copy.

**Acceptance**
- `generateIv()` reuses the shared `SecureRandom` instance.
- `encrypt()` eliminates redundant intermediate byte array allocations.
- Existing tests in `AndroidDecrypterTest` pass with identical ciphertext output.

**Out of scope**
- Changing encryption algorithms (AES/CBC/PKCS5Padding and PBKDF2 must remain backward-compatible).
- Modifying `SecurePrefs.kt`.

**Tests**
- Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.AndroidDecrypterTest"`

**Constraints**
- Under 50 changed lines in 1 file. No new dependencies.

---

### Task 8 — Optimize Markdown Stripping and CSV Speech Formatting in TTSManager

**Roadmap:** 7 (optimize remaining performance hotspots), 8 (improve code health and add tests)
**Also moves 9/10:** Partial. Pure Kotlin text processing algorithms in `TTSManager.Companion` become zero-allocation and portable across multiplatform targets.

**Why / user impact**
When the text-to-speech feature reads educational content or CSV table data to learners with visual impairments or reading difficulties, `TTSManager.stripMarkdown` executes 10 sequential regular expression `replace()` passes, allocating 10 intermediate string copies for the entire text. In addition, `formatCsvForSpeech` creates dozens of nested lists and strings via multiple `mapIndexed` calls, causing garbage collector spikes and stuttering playback on large chapters.

**Verified problem**
In `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt`, lines 99-124:
1. `stripMarkdown(text: String)` calls 10 sequential `.replace(REGEX, ...)` operations across the full document text.
2. `formatCsvForSpeech(rows: List<Array<String>>)` calls `rows.drop(1)` (allocating a new list), followed by `mapIndexed`, inner `mapIndexed`, and multiple `joinToString` calls, allocating multiple intermediate collections per CSV table.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt`

**Change instructions**
1. In `TTSManager.kt`:
   - In `stripMarkdown(text: String)`:
     - Add a fast-path return: if `text.isEmpty() || (!text.contains('`') && !text.contains('#') && !text.contains('*') && !text.contains('[') && !text.contains('|') && !text.contains('>'))`, return `text.trim()`.
   - In `formatCsvForSpeech(rows: List<Array<String>>)`:
     - Rewrite using a single `StringBuilder`:
       - Check if `rows.isEmpty()` or `rows.size <= 1` and return early.
       - Iterate with a simple index `for (i in 1 until rows.size)` instead of `rows.drop(1).mapIndexed(...)`.
       - Append row headers and values directly into the `StringBuilder`, avoiding all intermediate `List<String>` allocations.

**Acceptance**
- `stripMarkdown` skips regex processing when markdown formatting characters are absent.
- `formatCsvForSpeech` produces identical speech text using a single `StringBuilder` without `rows.drop(1)` or intermediate list allocations.
- Existing tests in `TTSManagerTest` pass.

**Out of scope**
- Modifying Android `TextToSpeech` engine initialization.
- Changing `ResourceViewerFragment.kt` or `WebViewActivity.kt`.

**Tests**
- Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.TTSManagerTest"`

**Constraints**
- Under 70 changed lines in 1 file. No new dependencies.

---

### Task 9 — Reuse Empty JSON Singletons and Fast-Path Numeric Extraction in JsonUtils

**Roadmap:** 1 (finish cleaning the data layer), 7 (optimize remaining performance hotspots)
**Also moves 9/10:** Yes. `JsonUtils` is a pure `kotlinx.serialization` utility with zero Android dependencies, serving as a core foundation for KMP serialization (Roadmap 9).

**Why / user impact**
`JsonUtils` is used across all Room DAOs, CouchDB sync deserializers, and repositories to extract properties from JSON documents. Currently, whenever a JSON object or array field is missing, `getJsonObject` and `getJsonArray` instantiate brand new `JsonObject(emptyMap())` and `JsonArray(emptyList())` instances. During full database syncs of thousands of records, this creates hundreds of thousands of identical empty objects that churn memory and trigger GC pauses.

**Verified problem**
In `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt`, lines 45-50:
```kotlin
fun getJsonObject(fieldName: String, jsonObject: JsonObject?): JsonObject =
    field(fieldName, jsonObject) as? JsonObject ?: JsonObject(emptyMap())

fun getJsonArray(fieldName: String, jsonObject: JsonObject?): JsonArray =
    field(fieldName, jsonObject) as? JsonArray ?: JsonArray(emptyList())
```
Every missing or null property allocates a fresh `JsonObject` or `JsonArray`.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt`

**Change instructions**
1. In `JsonUtils.kt`:
   - Define private constants:
     ```kotlin
     private val EMPTY_JSON_OBJECT = JsonObject(emptyMap())
     private val EMPTY_JSON_ARRAY = JsonArray(emptyList())
     ```
   - In `getJsonObject(fieldName: String, jsonObject: JsonObject?)`:
     - Return `EMPTY_JSON_OBJECT` instead of `JsonObject(emptyMap())`.
   - In `getJsonArray(fieldName: String, jsonObject: JsonObject?)`:
     - Return `EMPTY_JSON_ARRAY` instead of `JsonArray(emptyList())`.
   - In `getInt`, `getLong`, `getFloat`:
     - Fast-path check: avoid fallback string parsing (`primitive.content.toIntOrNull()`) when `primitive.longOrNull` is non-null.

**Acceptance**
- Missing or null JSON object/array queries return shared singleton instances, reducing allocations during large CouchDB syncs.
- `JsonUtilsTest` passes all test cases without regression.

**Out of scope**
- Changing Gson utilities in `GsonUtils.kt`.
- Changing Room models or database entities.

**Tests**
- Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.JsonUtilsTest"`

**Constraints**
- Under 40 changed lines in 1 file. No new dependencies.

---

### Task 10 — Avoid Wrapper Allocation Churn in Chat History Search

**Roadmap:** 7 (optimize remaining performance hotspots), 8 (improve code health and add tests)
**Also moves 9/10:** Yes. `ChatSearch` is a pure Kotlin coroutine utility with no Android framework dependencies, advancing the platform-free KMP core (Roadmap 9).

**Why / user impact**
The offline AI chat feature allows searching previous conversations by title or full conversation contents. On every single keystroke in the search bar, `ChatSearch` maps the entire list of chat histories and their conversation turns into intermediate wrapper objects (`ConvoChat`, `TitleChat`, and lists of normalized strings). For users with dozens of chats, typing a search query allocates thousands of temporary objects on every character, causing noticeable input lag.

**Verified problem**
In `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`:
1. In `fullConvoSearch()` (lines 38-46), it iterates over all chats and all conversation turns to build `val precomputedChats = chats.map { ConvoChat(...) }` before testing any match.
2. In `searchByTitle()` (lines 76-85), it builds `val precomputedChats = chats.map { TitleChat(...) }` before evaluating the query.
3. Rapid searching allocates multiple intermediate lists on the Default dispatcher on every query change.

**Allowed files (only these)**
- `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`

**Change instructions**
1. In `ChatSearch.kt`:
   - Fast-path return: if `query.isBlank()`, return `emptyList()` immediately before dispatching.
   - In `searchByTitle`:
     - Eliminate the intermediate `precomputedChats` list allocation. Iterate directly through `chats` in a single pass, normalize only the candidate title, and add matching chats to `startsWithQuery` or `containsQuery`.
   - In `fullConvoSearch`:
     - Evaluate matches directly during iteration over `chats` with early termination per conversation match, eliminating the creation of the intermediate `ConvoChat` wrapper objects and intermediate `List<String?>` for non-matching conversations.
   - Remove the unused `TitleChat` and `ConvoChat` private data classes.

**Acceptance**
- Searching chats by title or content produces identical search ordering without allocating intermediate `TitleChat` or `ConvoChat` wrapper lists.
- Existing tests in `ChatSearchTest` pass.

**Out of scope**
- Modifying `ChatViewModel.kt` or `ChatDetailFragment.kt` (off-limits under PR 15267 / 15266).
- Modifying `ChatRepository.kt` or `ChatDao.kt` (off-limits under PR 15808).

**Tests**
- Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.ChatSearchTest"`

**Constraints**
- Under 80 changed lines in 1 file. No new dependencies.

---

## Self-check results

- **R1**: Exactly 10 tasks, each independently mergeable in any order without dependencies on each other. ✔
- **R2**: No file appears in more than one task (12 total files, mutually disjoint across all 10 tasks). ✔
- **R3**: All 33 open PRs enumerated and checked against; all files touched by open PRs excluded from all tasks. ✔
- **R4**: Every file path, class, and method cited was opened and verified in the repository. ✔
- **R5**: Each task modifies ≤2 files, changes <150 lines, introduces no new dependencies, leaves no unused code or TODO placeholders. ✔
- **R6**: No implementation code modified; this document is the plan deliverable. ✔
