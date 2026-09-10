# myPlanet — performance quick wins (refactor round)

date: 2026-09-10
base commit: 022ee7d1f50f1453b1741aee391f3be5c619b4eb
open PRs checked (numbers): #16989 #16988 #16987 #16986 #16690 #16624 #16623 #16594 #16270 #16101 #15951 #15825 #15824 #15820 #15808 #15559 #15519 #15412 #15267 #15266 #15226 #15198 #15108 #14960 #14893 #14883 #14650 #14427 #13928 #13848 #13657 #13604 #13415 #13355 #13287 #10993 #8175 #4075

Every file cited below was opened and confirmed to exist, and was verified clear of all 39 open PRs listed above. Each task is independently mergeable in any order. No file appears in more than one task. No task adds a dependency or writes implementation code — the plan is the deliverable.

---

### 1. cache the version code and name lookups in VersionUtils (roadmap 7, also moves 9)

context: `VersionUtils.getVersionCode(context)` and `getVersionName(context)` each call `context.packageManager.getPackageInfo(context.packageName, 0)` on every invocation — a PackageManager IPC with no caching (utils/VersionUtils.kt:19 and :34). These values never change during a process lifetime, yet they are read per usage-stat row in `MyPlanet.addStats` (model/MyPlanet.kt:91-92) and again per version-check evaluation in `ConfigurationsRepositoryImpl.handleVersionEvaluation` (ConfigurationsRepositoryImpl.kt:499). The object already caches `cachedAndroidId` the same way (utils/VersionUtils.kt:14-15).

files: app/src/main/java/org/ole/planet/myplanet/utils/VersionUtils.kt — `getVersionCode(context)` (line 17) and `getVersionName(context)` (line 32). Leave `getAndroidId`, `compareVersions`, `parseApkVersionString`, and all callers untouched.

steps:
1. Add `@Volatile private var cachedVersionCode: Int? = null` and `@Volatile private var cachedVersionName: String? = null` alongside `cachedAndroidId`.
2. In `getVersionCode`, return `cachedVersionCode` if non-null; otherwise compute it, store it, and return it (guard the write with the same try/catch so a `NameNotFoundException` keeps returning 0 without caching a wrong value — only cache on the success path).
3. Apply the same memoization to `getVersionName`, caching the successful `versionName` (and caching `""` only on the success-but-empty path, not on the exception path).
4. Add `@VisibleForTesting internal fun resetVersionCacheForTesting()` mirroring `resetAndroidIdCacheForTesting` so tests can reset the cache.
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.VersionUtilsTest"` green (or the full `./gradlew testDefaultDebugUnitTest` green if no VersionUtils test exists); `VersionUtils.getVersionCode(ctx)` returns the same value before and after a second call and no longer performs a second `packageManager.getPackageInfo` (verifiable by a test that stubs `PackageManager` and asserts `getPackageInfo` is invoked once across two calls).

size budget: ~20 changed lines, 1 file
out of scope: do not change `MyPlanet.addStats` (task 2 owns that loop), do not change any caller, do not change the `getAndroidId` cache.

---

### 2. hoist per-row invariants out of the addStats usage-stats loop (roadmap 7)

context: `MyPlanet.getTabletUsages` loops over `queryUsageStats` and calls `addStats(s, arr, context)` per row (model/MyPlanet.kt:76-78). Inside `addStats`, values that are identical for every row are recomputed each iteration: `NetworkUtils.getCustomDeviceName(context)` (line 94, a SharedPreferences read), `NetworkUtils.getDeviceName()` (line 95), a fresh `Date()` allocation for `Date().time` (line 96), and `context.packageName` (line 84). The caller already receives a `now: Long` defaulting to `System.currentTimeMillis()` (line 68) that the per-row `time` field can reuse.

files: app/src/main/java/org/ole/planet/myplanet/model/MyPlanet.kt — `getTabletUsages` (line 65) and `addStats` (line 83). Do not touch `getTabletUsages`'s `UsageStatsManager` query or the `VersionUtils` calls (task 1 owns those).

steps:
1. In `getTabletUsages`, before the loop, compute once: `val packageName = context.packageName`, `val customDeviceName = NetworkUtils.getCustomDeviceName(context)`, `val deviceName = NetworkUtils.getDeviceName()`.
2. Change the loop call to `addStats(s, arr, packageName, customDeviceName, deviceName, now)`.
3. Change `addStats`'s signature to `private fun addStats(s: UsageStats, arr: JsonArray, packageName: String, customDeviceName: String, deviceName: String, time: Long)` and replace the per-row `context.packageName` (line 84), `NetworkUtils.getCustomDeviceName(context)` (line 94), `NetworkUtils.getDeviceName()` (line 95), and `Date().time` (line 96) with the passed-in values.
4. Remove the now-unused `import java.util.Date` if no other reference remains in the file (the other `Date().time` at line 60 is in a different method — leave it).
5. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the JSON array produced by `getTabletUsages` still carries the same `customDeviceName`, `deviceName`, `time`, `version`, and `versionName` fields per row (verifiable by a test that feeds a fake `UsageStats` list and asserts the emitted objects).

size budget: ~12 changed lines, 1 file
out of scope: no `UsageStatsManager` changes, no changes to `NetworkUtils`, do not touch the `getTabletUsages` overload that builds the login `postJSON`.

---

### 3. lowercase the file extension once in scanStorage instead of two map lookups (roadmap 7)

context: `StorageBreakdownFragment.scanStorage` walks every file under `oleDir` (ui/settings/StorageBreakdownFragment.kt:225). For each file it does `StorageCategories.indexOf(ext)` and, on miss, `StorageCategories.indexOf(ext.lowercase())` (lines 230-231). `StorageCategories.extensionToIndex` is keyed by lowercase extensions (ui/settings/StorageCategories.kt:38-44), so any uppercase extension (e.g. `MP4`) pays two HashMap lookups per file, and even lowercase extensions run the `indexOf` twice when they are genuinely "other". Lowercasing once and looking up once removes the redundant probe on a whole-tree file walk.

files: app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageBreakdownFragment.kt — `scanStorage(oleDir: File)` (line 217). Do not touch `StorageCategories.kt` (its keys are already lowercase by design).

steps:
1. Inside the `forEach { file -> ... }` block, replace lines 227-232 so that when `ext` is non-empty the code computes `val key = ext.lowercase()` once and then `val index = StorageCategories.indexOf(key)`.
2. Keep the `ext.isEmpty()` branch returning `StorageCategories.OTHER_INDEX` unchanged.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.settings.StorageBreakdownFragmentTest"` green (or the full suite green if no such test exists); a tree containing `video.MP4` and `note.pdf` still classifies the `.MP4` file under the videos category and `.pdf` under pdfs (verifiable by a test that points `scanStorage` at a temp dir and inspects the returned `ScanResult`).

size budget: ~4 changed lines, 1 file
out of scope: no changes to `StorageCategories`, no changes to the category row rendering in `populateCategoryRows`.

---

### 4. drop the no-op `+ ""` string concatenation in HealthExaminationAdapter (roadmap 7)

context: `HealthExaminationAdapter.checkEmpty` and `checkEmptyInt` format a non-zero numeric value with `value.toString() + ""` (ui/health/HealthExaminationAdapter.kt:127 and :131). The `+ ""` appends an empty string literal, which the compiler lowers to a `StringBuilder` append of `""` — a wasted allocation on every health-examination row bind. Both helpers are called per row in `onBindViewHolder` (lines 101, 111, 114, 115) and again in `showAlert` (lines 138-139).

files: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt — `checkEmpty` (line 126) and `checkEmptyInt` (line 130). Leave `submitExaminations`, `onBindViewHolder`, and the `DIFF_CALLBACK` untouched.

steps:
1. In `checkEmpty`, change `value.toString() + ""` to `value.toString()`.
2. In `checkEmptyInt`, change `value.toString() + ""` to `value.toString()`.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.health.HealthExaminationAdapterTest"` green (or the full suite green); a row with `temperature = 36.5` still renders `"36.5"` and a `0f` value still renders empty (verifiable by an existing or new adapter test asserting `checkEmpty(0f) == ""` and `checkEmpty(36.5f) == "36.5"`).

size budget: ~2 changed lines, 1 file
out of scope: no changes to `showAlert`, no changes to `HealthExaminationItem`, no changes to the diff callback.

---

### 5. stop double-lowercasing the search query in VoicesViewModel.filterNews (roadmap 7, also moves 10)

context: `VoicesViewModel.filterNews` computes `val lowerQuery = query.trim().lowercase()` (ui/voices/VoicesViewModel.kt:137) and then calls `contains(lowerQuery, ignoreCase = true)` on three fields (lines 139-141). The `ignoreCase = true` argument already performs a case-insensitive comparison, so the explicit `lowercase()` only allocates a second string per filter pass without changing the result. `filterNews` runs on the default dispatcher every time the query or label `StateFlow` emits (lines 58-66).

files: app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesViewModel.kt — `filterNews` (line 98). Leave `observeCommunityNews`, `createNews`, the `LabelManipulator` delegation, and the `filteredNews` combine untouched.

steps:
1. Rename `lowerQuery` to `trimmedQuery` and compute it as `query.trim()` only (drop `.lowercase()`).
2. Update the three `contains(..., ignoreCase = true)` calls to use `trimmedQuery`.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.voices.VoicesViewModelTest"` green (or the full suite green); searching for `"Hello"` still matches a news item whose message contains `"hello"` (case-insensitive match preserved by `ignoreCase = true`), verifiable by a test that sets a query with mixed case and asserts a lowercased message is returned.

size budget: ~4 changed lines, 1 file
out of scope: no changes to the label-filtering branch, no changes to `Constants.LABELS`, no new flow operators.

---

### 6. reuse the cached system-service managers in NetworkUtils.getCurrentNetworkId (roadmap 7, also moves 9)

context: `NetworkUtils` already caches `connectivityManager` and `wifiManager` via `ResettableCache` (utils/NetworkUtils.kt:56-66), but `getCurrentNetworkId(context)` ignores both and calls `context.getSystemService(Context.CONNECTIVITY_SERVICE)` and `context.applicationContext.getSystemService(Context.WIFI_SERVICE)` fresh on every invocation (lines 223 and 230). The only caller is `ConfigurationsRepositoryImpl.checkVersion` (ConfigurationsRepositoryImpl.kt:137), which runs on each version check, and the deprecated `WifiManager.connectionInfo` path additionally re-fetches the wifi service.

files: app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt — `getCurrentNetworkId(context)` (line 221). Do not touch `startListenNetworkState`, `isWifiConnected`, or the `ResettableCache` declarations.

steps:
1. Replace the local `connManager = context.getSystemService(...)` with the cached `connectivityManager` field.
2. In the pre-`S` branch, replace `context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?` with the cached `wifiManager` field (keep the `@Suppress("DEPRECATION")` on `connectionInfo`).
3. Keep the `context: Context` parameter on the signature so the off-limits caller compiles unchanged (it is now unused inside the body — add `@Suppress("UNUSED_PARAMETER")` if the compiler warns).
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.NetworkUtilsTest"` green (or the full suite green); `getCurrentNetworkId` still returns the wifi `networkId` when the active network is wifi and `-1` otherwise (verifiable by a test using Robolectric/shadow managers, mirroring the existing `NetworkUtils` tests).

size budget: ~6 changed lines, 1 file
out of scope: no changes to `ConfigurationsRepositoryImpl`, no changes to the `ResettableCache` machinery, no signature change that would touch the off-limits caller.

---

### 7. short-circuit prependBaseUrlToImages when the markdown has no image markup (roadmap 7, also moves 10)

context: `MarkdownUtils.prependBaseUrlToImages` always allocates a `StringBuilder` and runs `imagePattern.matcher(content)` over the whole description (utils/MarkdownUtils.kt:89-90), even when the content contains no `![` image syntax at all — the common case for course-step and course-detail descriptions. It is called on every emission of `CourseDetailViewModel` (ui/courses/CourseDetailViewModel.kt:65) and `CoursesStepsViewModel` (ui/courses/CoursesStepsViewModel.kt:76), so descriptions without images still pay the matcher setup and a `StringBuilder.toString()` that just copies the input.

files: app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt — `prependBaseUrlToImages` (line 82). Leave `imagePattern`, `create`, `setMarkdownText`, and `CustomLinkMovementMethod` untouched.

steps:
1. After `val content = markdownContent ?: return markdownContent.orEmpty()` (line 88), add an early return: `if (!content.contains("![")) return content`.
2. Leave the existing matcher loop unchanged (it now only runs when at least one `![` marker is present).
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.MarkdownUtilsTest"` green (or the full suite green); a description with no images returns the input string verbatim, and a description with `![alt](resources/img.png)` still gets its image src rewritten to the base URL (verifiable by existing or new tests covering both cases).

size budget: ~2 changed lines, 1 file
out of scope: no changes to the regex, no changes to the `<img>` tag format, no changes to callers.

---

### 8. hoist the Gson TypeToken out of getMyLifeForDashboard (roadmap 7, also moves 9)

context: `LifeRepositoryImpl.getMyLifeForDashboard` allocates a fresh anonymous `TypeToken<List<CachedMyLifeItem>>` on every cache hit (repository/LifeRepositoryImpl.kt:112) to deserialize the dashboard's cached my-life items. `getMyLifeForDashboard` runs on dashboard load for the logged-in user, and the same pattern is already done correctly in `Converters` (data/room/Converters.kt:62-64), which hoists its `TypeToken`s to `companion object` vals.

files: app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt — `getMyLifeForDashboard` (line 98) and the file's companion/object scope. Leave `seedMyLifeIfEmpty`, `updateMyLifeListOrder`, and the `MY_LIFE_CACHE_PREFIX` logic untouched.

steps:
1. Add a companion object (or a top-level `private val`) holding `private val cachedMyLifeListType = object : TypeToken<List<CachedMyLifeItem>>() {}.type`.
2. Replace the inline `val type = object : TypeToken<List<CachedMyLifeItem>>() {}.type` (line 112) with a reference to `cachedMyLifeListType`.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.LifeRepositoryImplTest"` green (or the full suite green); a cached my-life JSON still deserializes into the same `List<CachedMyLifeItem>` (verifiable by an existing or new test that seeds the cache and asserts `getMyLifeForDashboard` returns the expected visible items sorted by weight).

size budget: ~5 changed lines, 1 file
out of scope: no changes to `CachedMyLifeItem`, no changes to the SharedPreferences cache key scheme, no changes to the seeding path.

---

### 9. validate the report form with isNullOrBlank instead of string-template plus isNullOrEmpty (roadmap 7)

context: `EnterprisesReportsFragment.isValidReportForm` checks seven `EditText` fields by wrapping each `CharSequence` text in a string template and then calling `isNullOrEmpty()` (ui/enterprises/EnterprisesReportsFragment.kt:346, 350, 354, 358, 362, 366). `"${binding.summary.text}"` allocates a new `String` per field, and `isNullOrEmpty()` on a non-null `String` can only ever test emptiness — so the check is really "is the field blank", which `CharSequence.isNullOrBlank()` expresses directly with no intermediate allocation. The function runs on every submit button press.

files: app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt — `isValidReportForm` (line 336). Leave the `startDate`/`endDate` literal comparisons (lines 338, 342) and `showAddReportDialog`/`showEditReportDialog` untouched.

steps:
1. Replace each `"${binding.<field>.text}".isNullOrEmpty()` (six occurrences at lines 346, 350, 354, 358, 362, 366) with `binding.<field>.text.isNullOrBlank()`.
2. Leave the two date checks (`binding.startDate.text == "Start Date"`, `binding.endDate.text == "End Date"`) unchanged.
3. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; submitting the add-report dialog with a blank summary still sets `binding.summary.error` and blocks submission, while a non-blank summary passes (verifiable manually, or by a test that drives the dialog binding if one exists).

size budget: ~6 changed lines, 1 file
out of scope: no changes to the date-picker setup, no changes to `updatedReportsList`, no changes to the CSV export path.

---

### 10. memoize the BuildConfig-derived server-list builders in ServerConfigUtils (roadmap 7, also moves 9)

context: `ServerConfigUtils.getServerAddresses`, `getTrustedServerHosts`, and `getChallengeServerUrls` each rebuild an immutable list from `BuildConfig` constants on every call (utils/ServerConfigUtils.kt:29-43, 140-157, 159-168). The values are pure functions of compile-time `BuildConfig` fields (plus `R.string` lookups for the display names), so they never change within a process, yet each call re-allocates the list and its `takeIf` lambdas. `getServerAddresses` is called from `ServerDialogExtensions.setupServerListUi` and `getTrustedServerHosts` from `WebViewActivity` (already `by lazy` there, but the function itself still rebuilds).

files: app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt — `getServerAddresses` (line 29), `getTrustedServerHosts` (line 140), `getChallengeServerUrls` (line 159). Leave `getFilteredList`, `removeProtocol`, `saveAlternativeUrl`, and `getDefaultProtocol` untouched (they take runtime arguments).

steps:
1. Convert `getServerAddresses` to read from a `private val cachedServerAddresses by lazy { ... }` building the same `listOf(...)` (the `context.getString(R.string.sync_*)` calls are process-stable, so capturing `context` is safe only if the lazy does not outlive the application context — instead build it from the application context: have the function delegate to a `by lazy` that uses the passed `context.applicationContext`).
2. Convert `getTrustedServerHosts` to a `private val cachedTrustedHosts by lazy { listOfNotNull(...) }` and return it.
3. Convert `getChallengeServerUrls` to a `private val cachedChallengeUrls by lazy { listOfNotNull(...) }` and return it.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.ServerConfigUtilsTest"` green (or the full suite green); the three functions return the same lists as before (verifiable by a test that asserts `getTrustedServerHosts()` equals the expected BuildConfig-derived set and that two calls return the same instance).

size budget: ~15 changed lines, 1 file
out of scope: no changes to `pinMap`, no changes to `getDefaultProtocol`/`isLocalNetwork`, no new dependencies.
