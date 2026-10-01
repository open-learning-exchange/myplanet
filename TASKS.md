# myPlanet refactor round — 10 work orders

**This file is the deliverable.** Each task is independently mergeable in any order. No file appears in more than one task. Open-PR files are off-limits.

## Open pull requests (R3) — 33, all of their files are off-limits

#17812, #17811, #17776, #17694, #17680, #17435, #17254, #17187, #16623, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13415, #13355, #13287, #10993, #8175, #4075.

Hot files already taken (do not touch): `.github/workflows/{test,build,release,labels}.yml`, `app/build.gradle`, `settings.gradle`, `gradle/libs.versions.toml`, `MainApplication.kt`, `AppDatabase.kt`, `RepositoryModule.kt`, `RoomModule.kt`, `ServiceModule.kt`, `UploadCoordinator.kt`, `TransactionSyncManager.kt`, `UserRepositoryImpl.kt`, `TeamsRepositoryImpl.kt`, `CoursesRepositoryImpl.kt`, `ResourcesRepositoryImpl.kt`, plus most teams/resources/voices/survey UI and all `values*/strings.xml`.

## CI log context (focus block)

- Master tests `36847822998`: shards ~4m53s / ~4m37s; Gradle restore ~2.1GB. Cache/setup-gradle lives in locked `test.yml` (#17812 / #17694).
- Master release `36847822969`: assemble ~3m; Play upload `continue-on-error` (quota). Node 20 deprecation on `dogi/*` actions in locked `release.yml`.
- `build.yml` does not run on master. Last branch build `36858348710` ~4m.

Unlocked workflow surface used below: `playstore.yml` only.

Hard rules for every task: under ~150 changed lines, under ~5 files, no new dependencies, no unused code, no TODO placeholders.

---

### Task 1 — Sparse-checkout playstore.yml (scripts only)

- **Roadmap:** 8 (code health). Also 7: stop cloning the whole Android tree for a job that only runs two bash scripts.
- **Files (change only these):**
  - `.github/workflows/playstore.yml`
- **Current behavior:** Job `publish` step `checkout repository code` (`actions/checkout@v7`, `fetch-depth: 1`) clones the full repo. The job then only sources `.github/scripts/playstore.sh` (`pending`, `track-code`) and `.github/scripts/playstore-quota.sh` (`report`, `status`, `forecast`). Signed AAB comes from `gh release download`, not from the checkout.
- **Change:** Restrict that checkout to `.github/scripts` (sparse-checkout / cone). Keep `fetch-depth: 1`. Set `persist-credentials: false` (the job uses `GH_TOKEN` / `AUTOMERGE_TOKEN`, not the checkout token). Do not edit the scripts, timeouts, Play action pins, or resume-automerge step.
- **Constraints:** One file; no new Actions; no unused steps; no TODOs.
- **Verify:** YAML still invokes `.github/scripts/playstore.sh pending` and `.github/scripts/playstore-quota.sh report` after checkout. `workflow_dispatch` inputs and `concurrency: myPlanet-playstore` unchanged.

---

### Task 2 — Stop ApiClient from retrying 4xx

- **Roadmap:** 5 (sync/upload). Also 7: `executeWithResult` today retries every unsuccessful response, including 401/404, three times with 2s delays (`MAX_ATTEMPTS` / `RETRY_DELAY_MS`) while `RetryInterceptor` already retries 5xx/IO.
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/data/api/ApiClient.kt`
  - `app/src/test/java/org/ole/planet/myplanet/data/api/ApiClientTest.kt`
- **Current behavior:** `executeWithRetryAndWrap` passes `shouldRetry = { resp -> resp == null || !resp.isSuccessful }` into `RetryUtils.retry`. `executeWithResult` uses that wrapper. `ApiClientTest` `executeWithResult maps an HTTP error to Error and exhausts retries` uses `httpError(500, "boom")` and expects 3 calls; `executeWithRetryAndWrap retries unsuccessful responses then succeeds` uses 503 then success.
- **Change:** Retry only null responses, IO/thrown failures, and HTTP 5xx. Return 4xx immediately. Keep 3 attempts / 2s for retryable cases. Extend `ApiClientTest` with a 404/401 case that asserts one call, and keep the 503 retry-then-success case.
- **Constraints:** Do not edit `RetryUtils.kt`, `RetryInterceptor.kt`, or repository callers (`SyncRepositoryImpl`, `ConfigurationsRepositoryImpl`). No new dependencies.
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.api.ApiClientTest`

---

### Task 3 — Pool ServerUrlMapper reachability (drop HttpURLConnection GET)

- **Roadmap:** 7. Also 9: `isUrlDirectlyReachable` and `extractBaseUrl` use `java.net.HttpURLConnection`, `android.net.Uri` / `toUri()`, and `android.util.Log`.
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`
  - `app/src/test/java/org/ole/planet/myplanet/services/sync/ServerUrlMapperTest.kt`
- **Current behavior:** `ServerUrlMapper` is `@Inject` with only `DispatcherProvider`. `isUrlDirectlyReachable` opens `URL.openConnection()` as `HttpURLConnection`, method GET, 5s timeouts, treats `code in 200..599` as up, and skips `disconnect()` if `responseCode` throws. `extractBaseUrl` uses `url.toUri()` and `Log.w`. `ServerUrlMapperTest.setUp` constructs `ServerUrlMapper(dispatcherProvider)` and covers `processUrl` (mapped San Pablo URL, unmapped host, non-default port, malformed `"invalid url"`).
- **Change:** Inject `OkHttpClient` qualified with existing `@ReachabilityHttpClient` (already provided; do not edit `NetworkModule`). Probe with HEAD (not GET), close the response, keep `200..599` and the `http://` prefix fallback. Implement `extractBaseUrl` with `java.net.URI` while preserving default-port stripping so existing `processUrl` assertions still pass. Leave `updateUrlPreferences(..., uri: Uri, ...)` on Android `Uri` (SharedPreferences API).
- **Constraints:** Do not change `NetworkModule.kt` or `UrlUtils`. Update the test constructor and add a mocked-client test for `isUrlDirectlyReachable`.
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.sync.ServerUrlMapperTest`

---

### Task 4 — RetryRepositoryImpl: injected Json, no android.util.Log

- **Roadmap:** 1 and 4. Also 9: `RetryRepositoryImpl` imports `android.util.Log` and uses `Json.parseToJsonElement` (companion default) in `executeOperation`.
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`
- **Current behavior:** Constructor is `@Inject (retryDao, apiInterface, timeProvider)`. Payload parse uses `Json.parseToJsonElement(operation.serializedPayload).jsonObject`. Fail/success paths call `Log.e` / `Log.d` / `Log.w`. `RetryRepositoryImplTest` stubs every `Log.*` overload. `getRetryQueueSnapshot` correctly uses both `getPendingCount()` (`RetryDao.getActiveCount`: pending+in_progress) and `getPending()` (due pending only) — do not collapse those queries.
- **Change:** Inject kotlinx `Json` (already provided by Hilt) and parse with that instance. Remove `android.util.Log` and the test Log stubs. Keep 409-as-success, 5xx retryable vs other terminal, IO vs unexpected, mutex/`recordFailure` behavior.
- **Constraints:** Do not edit `RetryDao.kt`, `RetryRepository.kt`, or `RepositoryModule.kt`. No new logging library.
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.RetryRepositoryImplTest`

---

### Task 5 — Dictionary seed: one file resolve, injected Json, no Log

- **Roadmap:** 1 and 4. Also 9: `DictionaryRepositoryImpl` uses `android.util.Log` and default `Json.parseToJsonElement`.
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryFileReader.kt`
  - `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`
  - `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`
- **Current behavior:** `DictionaryFileReaderImpl.exists()` and `readText()` each call `storagePathResolver.resolveFileFromUrl(Constants.DICTIONARY_URL)`. `insertDictionaryData` calls `exists()` then, inside `seedMutex`, `readText()` again. Empty/missing file is `DictionaryLoad.FileMissing` via `exists()` (`file.exists() && file.length() > 0`). Parse uses `Json.parseToJsonElement(it).jsonArray`; failures `Log.e("DictionaryRepositoryImpl", ...)`. Tests mock `exists()` then `readText()`.
- **Change:** Cache the resolved `File` inside `DictionaryFileReaderImpl` so `exists()`/`readText()` share one resolve. Inject `Json` into `DictionaryRepositoryImpl` and drop `android.util.Log`. Keep `FileMissing` / `AlreadyPopulated` / `Inserted` / `Failed` and `CancellationException` rethrow. Do not edit `DictionaryRepository.kt` or `FileUtils.kt`.
- **Constraints:** Three files. No new deps. `DictionaryMapper.mapJsonArrayToEntities` stays as-is.
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest`

---

### Task 6 — Strip android.util.Log from GsonUtils

- **Roadmap:** 1. Also 9: `GsonUtils` is shared JSON coercion used from models/sync; it still imports `android.util.Log`.
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/utils/GsonUtils.kt`
- **Current behavior:** `logFallback` calls `Log.isLoggable` / `Log.d` inside try/catch. `extractSharedTeamName` uses `Log.w` when `gson.fromJson(news.viewIn, JsonArray::class.java)` fails. `GsonUtilsNoLogStubTest` already documents that fallback must work without Log stubbing (#16652).
- **Change:** Remove `android.util.Log`. Keep `safeGet` fallbacks identical (wrong-type → default). Silent catch is enough; do not add a logger. Do not retarget `GsonUtils.gson` at Hilt.
- **Constraints:** One production file. Do not edit `GsonUtilsNoLogStubTest.kt` / `GsonUtilsTest.kt` / `GsonUtilsCoercionTest.kt` / `GsonUtilsKotlinxBridgeTest.kt` unless a compile error appears (it should not).
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.GsonUtilsNoLogStubTest --tests org.ole.planet.myplanet.utils.GsonUtilsTest --tests org.ole.planet.myplanet.utils.GsonUtilsCoercionTest --tests org.ole.planet.myplanet.utils.GsonUtilsKotlinxBridgeTest`

---

### Task 7 — RetryInterceptor: cheaper retry-safe check, cheaper broadcast extras

- **Roadmap:** 5. Also 7: every retry allocates `Intent` extras with `request.url.toString()`; `isRetrySafe` scans three `endsWith` suffixes on `encodedPath` for every POST.
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt`
  - `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt`
- **Current behavior:** `isRetrySafe` returns true for non-POST, else `READ_ONLY_POST_PATH_SUFFIXES` (`/_find`, `/_all_docs`, `/_bulk_get`) via `encodedPath.endsWith`. On retry it builds `Intent(Constants.ACTION_RETRY_EVENT)` with url/attempt/delay and `broadcastService.trySendBroadcast`. `RetryInterceptorTest` (Robolectric) verifies broadcast counts (0/1/3) and still constructs `RetryInterceptor(broadcastService, timeProvider)`.
- **Change:** Detect those three CouchDB paths from `HttpUrl.pathSegments` (last segment `find` / `all_docs` / `bulk_get`) so you do not rescan the full path string. Put `encodedPath` (not full URL with query) in the Intent extra. Keep maxRetries=3, exponential backoff, `MAX_BACKOFF_SLICE_MS`, 5xx/IO retry, non-5xx immediate return, and `BroadcastService.trySendBroadcast`.
- **Constraints:** Do not edit `BroadcastService` or `Constants`. No new deps. Tests that `verify { trySendBroadcast(any()) }` must still pass.
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.api.RetryInterceptorTest`

---

### Task 8 — LoginSyncManager.isManager: fail-fast role check

- **Roadmap:** 5 and 8.
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt`
  - `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt`
- **Current behavior:** `isManager(jsonDoc: JsonObject?)` walks every `roles` element, sets a flag on `"manager"` (ignoreCase), then ORs `jsonDoc?.get("isUserAdmin")?.asBoolean == true`. No early return if already admin. Used from `login(...)` before `userSyncRepository.saveUser`.
- **Change:** Return true immediately when `isUserAdmin` is true. Return true on the first matching `manager` role; do not scan the rest. Null/missing roles stay false unless admin. Do not change `R.string.user_verification_in_progress`, `ApplicationContext`, or the login network path.
- **Constraints:** Two files. No new deps. If tests only cover login via public API, add/adjust a case for admin-without-roles and manager-in-later-role-slot.
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.sync.LoginSyncManagerTest`

---

### Task 9 — Hoist calendar day grouping into CalendarViewModel

- **Roadmap:** 3. Also 7 and 10: `CalendarFragment.onViewCreated` rebuilds `CalendarDay` + `Instant`/`ZoneId` for every meetup on each `viewModel.meetups` emission; click filtering repeats the same conversion. Hoisting epoch-day keys keeps a future Compose calendar free of that logic (drawable/`R` stays in the fragment).
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`
  - `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt`
  - `app/src/test/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModelTest.kt`
- **Current behavior:** `CalendarViewModel.loadMeetups` collects `teamsRepository.getMyTeamsFlow(userId).distinctUntilChangedBy { ... }` and assigns `_meetups` from one-shot `eventsRepository.getMeetupsForTeams(teamIds)`. Fragment maps each `Meetup.startDate` to `CalendarDay` with `R.drawable.ic_calendar`, and `OnCalendarDayClickListener` filters with `Instant.ofEpochMilli(...).atZone(ZoneId.systemDefault()).toLocalDate()`. Tests: init aggregation, team-emission dedupe (`coVerify exactly 1` `getMeetupsForTeams`), guest user empty.
- **Change:** Expose a `StateFlow` of marked epoch-days (or `LocalDate` keys) derived once when meetups load, plus a function that returns that day's meetups. Fragment only turns those keys into `CalendarDay` + drawable and calls the VM for the agenda list. Do not put `R` or `CalendarDay` in the ViewModel. Keep `distinctUntilChangedBy` and guest-empty behavior. `flowOn` is optional; do not add `DispatcherProvider` unless tests are updated.
- **Constraints:** Three files. No new calendar library. `showAgendaDialog` may stay in the fragment.
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.calendar.CalendarViewModelTest`

---

### Task 10 — ChatSearch: require injected dispatcher, skip empty work

- **Roadmap:** 4. Also 7: `ChatSearch.search` defaults `dispatcher: CoroutineDispatcher = Dispatchers.Default`. Production `ChatViewModel` already passes `dispatcherProvider.default`; the default is a hidden Android/main-unfriendly path. Empty query still builds `TitleChat`/`ConvoChat` maps.
- **Files:**
  - `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`
  - `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt`
- **Current behavior:** `search` / `searchByTitle` / `fullConvoSearch` always `withContext(dispatcher)` after mapping every chat. Tests already pass `testDispatcher` (including `ChatSearch.search("", ChatSearchMode.TITLE, chats, testDispatcher)`).
- **Change:** Make `dispatcher` required (delete the `Dispatchers.Default` default and that import). If `query` is blank, return `emptyList()` without precompute. Keep title vs question/response ranking (`inTitleStartQuery` + `inTitleContainsQuery` + `startsWithQuery` + `containsQuery`). Do not edit `ChatViewModel.kt`.
- **Constraints:** Two files. No new deps. Ranking/normalization (`Utilities.normalizeText`) stays.
- **Verify:** `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.ChatSearchTest`

---

## Self-check (P5)

| Rule | Status |
|------|--------|
| R1 exactly 10, independently mergeable | yes |
| R2 no file in more than one task | 19 unique paths, listed once each |
| R3 no open-PR files | each path checked against the 33-PR file set |
| R4 cited types exist | opened before citing |
| R5 ~150 lines / ≤5 files / no new deps / no TODOs | yes |
| R6 no implementation code in the generating run | yes |

Roadmap 2 (global nav) and 6 (Compose screens) were skipped this round: `FragmentNavigator` is already a tiny wrapper, and Compose/UI files in the hot areas are owned by open PRs.
