### Remove deprecated Gradle properties for AGP 9.0 compatibility
Three deprecated flags in `gradle.properties` emit warnings on every build and will become errors in AGP 10.0. Removing `android.builtInKotlin=false`, `android.newDsl=false`, and setting `android.enableJetifier=false` eliminates configure-time noise and prepares for the next major plugin upgrade.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L25-L25"}
:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=29 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L28-L29"}

:::task-stub{title="Remove deprecated Gradle properties"}
1. Delete `android.enableJetifier=true` from `gradle.properties` (line 25)
2. Delete `android.builtInKotlin=false` from `gradle.properties` (line 28)
3. Delete `android.newDsl=false` from `gradle.properties` (line 29)
4. Run `./gradlew assembleDefaultDebug` to verify no Jetifier-dependent libraries remain
5. If a dependency still requires Jetifier, migrate that dependency to its AndroidX equivalent instead of re-enabling the flag
:::

### Migrate away from standalone kotlin-android plugin
AGP 9.0 bundles Kotlin support natively. The explicit `kotlin-android` and `kotlin-kapt` plugin applications produce a deprecation warning and will be removed in AGP 10.0. Migrating to built-in Kotlin simplifies the plugin block and reduces configuration overhead.

:codex-file-citation[codex-file-citation]{line_range_start=2 line_range_end=3 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L2-L3"}

:::task-stub{title="Migrate to AGP built-in Kotlin support"}
1. Remove `apply plugin: 'kotlin-android'` from `app/build.gradle` (line 2)
2. Remove `apply plugin: 'kotlin-kapt'` from `app/build.gradle` (line 3)
3. Verify AGP built-in Kotlin configuration is active by checking `android { }` block settings
4. Migrate any remaining KAPT usages to KSP where possible
5. Confirm build succeeds with `./gradlew assembleLiteRelease assembleDefaultRelease`
:::

### Fix unnecessary safe calls on non-null receivers
Over 25 call sites use `?.` on receivers the compiler knows are non-null (`SharedPreferences`, `Retrofit`, `UserSessionManager`, `RealmStepExam`, `RealmMyHealth`). These mask nullability intent, disable smart-casts, and clutter the warning output.

:codex-file-citation[codex-file-citation]{line_range_start=344 line_range_end=361 path=app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt#L344-L361"}
:codex-file-citation[codex-file-citation]{line_range_start=418 line_range_end=420 path=app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt#L418-L420"}
:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=483 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L51-L483"}
:codex-file-citation[codex-file-citation]{line_range_start=476 line_range_end=479 path=app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L476-L479"}
:codex-file-citation[codex-file-citation]{line_range_start=470 line_range_end=474 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L470-L474"}

:::task-stub{title="Remove unnecessary safe calls on non-null receivers"}
1. In `BaseDashboardFragment.kt`, replace `settings?.getString(...)` with `settings.getString(...)` at lines 344, 361, and replace `settings?.let` with direct calls at lines 418, 420
2. In `UploadToShelfService.kt`, replace all `client?.create(...)` with `client.create(...)` at lines 51, 94, 292, 322, 368, 416, 483
3. In `SubmissionsRepositoryImpl.kt`, replace `exam?.` with `exam.` at lines 476-479 where the receiver is non-null
4. In `UserRepositoryImpl.kt`, replace `myHealth?.` with `myHealth.` at lines 470-474
5. In `ResourcesFragment.kt`, `VoicesFragment.kt`, `PlanFragment.kt`, `RatingsRepositoryImpl.kt`, `FileUploader.kt`, and `ChatShareTargetAdapter.kt`, apply the same safe-call removal pattern
6. Rebuild and verify zero "Unnecessary safe call" warnings remain
:::

### Add missing opt-in annotations for experimental coroutine APIs
Two call sites use `ExperimentalCoroutinesApi` (`flatMapLatest`) and `FlowPreview` (`debounce`) without the required `@OptIn` annotation. When these APIs graduate or change, the compiler will enforce the annotation, breaking the build.

:codex-file-citation[codex-file-citation]{line_range_start=114 line_range_end=114 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L114-L114"}
:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=44 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt#L44-L44"}
:codex-file-citation[codex-file-citation]{line_range_start=293 line_range_end=293 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L293-L293"}

:::task-stub{title="Add @OptIn annotations for experimental coroutine APIs"}
1. Add `@OptIn(ExperimentalCoroutinesApi::class)` to the function or class using `flatMapLatest` in `TeamsRepositoryImpl.kt` at line 114
2. Add `@OptIn(ExperimentalCoroutinesApi::class)` to the property or function using `mapLatest` in `SubmissionViewModel.kt` at line 44
3. Add `@OptIn(FlowPreview::class)` to the function using `debounce` in `VoicesFragment.kt` at line 293
4. Verify no remaining opt-in warnings with `./gradlew compileLiteReleaseKotlin 2>&1 | grep -i "opt-in"`
:::

### Resolve Kotlin annotation target warnings with explicit `@param:` or `@field:`
Sixteen injection sites emit the "annotation is currently applied to the value parameter only" warning. Kotlin 2.3+ will change the default target, so explicit target annotations are needed now to prevent future behavioral changes.

:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=49 path=app/src/main/java/org/ole/planet/myplanet/data/DataService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/data/DataService.kt#L49-L49"}
:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=43 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L43-L43"}
:codex-file-citation[codex-file-citation]{line_range_start=61 line_range_end=64 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L61-L64"}
:codex-file-citation[codex-file-citation]{line_range_start=63 line_range_end=70 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L63-L70"}

:::task-stub{title="Add explicit annotation targets to constructor parameters"}
1. In files using `@Inject` on constructor val/var parameters (`DataService.kt`, `AuthSessionUpdater.kt`, `ChatRepositoryImpl.kt`, `ConfigurationsRepositoryImpl.kt`, `ResourcesRepositoryImpl.kt`, `SurveysRepositoryImpl.kt`, `TeamsRepositoryImpl.kt`, `UserRepositoryImpl.kt`), prefix custom qualifier annotations with `@param:` or `@field:` as appropriate
2. Apply the same fix in service classes: `UploadManager.kt`, `UploadToShelfService.kt`, `UserSessionManager.kt`, `SyncManager.kt`, `ImprovedSyncManager.kt`, `TransactionSyncManager.kt`, `UploadCoordinator.kt`, `RetryQueue.kt`
3. Apply in UI classes: `NotificationsViewModel.kt`, `TeamPageConfig.kt`
4. Verify with `./gradlew compileLiteReleaseKotlin 2>&1 | grep "annotation is currently applied"` returns no matches
:::

### Replace deprecated Locale and Configuration APIs
`Locale(String)` constructor and `Resources.updateConfiguration()` are deprecated since API 24. Since minSdk is 26, the codebase can safely migrate to `Locale.forLanguageTag()` and `Context.createConfigurationContext()`.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=49 path=app/src/main/java/org/ole/planet/myplanet/base/BaseActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseActivity.kt#L43-L49"}
:codex-file-citation[codex-file-citation]{line_range_start=428 line_range_end=432 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt#L428-L432"}
:codex-file-citation[codex-file-citation]{line_range_start=24 line_range_end=24 path=app/src/main/java/org/ole/planet/myplanet/utils/LocaleUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/LocaleUtils.kt#L24-L24"}

:::task-stub{title="Migrate deprecated Locale and Configuration APIs"}
1. Replace `Locale(languageCode)` with `Locale.forLanguageTag(languageCode)` in `BaseActivity.kt:43`, `LoginActivity.kt:428`, and `LocaleUtils.kt:24`
2. Replace `resources.updateConfiguration(config, displayMetrics)` with `context.createConfigurationContext(config)` pattern in `BaseActivity.kt:49` and `LoginActivity.kt:432`
3. Remove the deprecated `config.locale = ...` assignment and use `config.setLocale(locale)` instead at `BaseActivity.kt:45`
4. Test locale switching for all supported languages (en, ar, es, fr, ne, so)
:::

### Replace deprecated WiFi and network APIs
`WifiManager.isWifiEnabled`, `ConnectivityManager.getNetworkInfo()`, `WifiConfiguration`, and `WifiInfo` are deprecated. Modern equivalents using `ConnectivityManager.NetworkCallback` and `NetworkCapabilities` should be used since minSdk 26 supports them.

:codex-file-citation[codex-file-citation]{line_range_start=175 line_range_end=208 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L175-L208"}
:codex-file-citation[codex-file-citation]{line_range_start=155 line_range_end=155 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L155-L155"}
:codex-file-citation[codex-file-citation]{line_range_start=530 line_range_end=530 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L530-L530"}
:codex-file-citation[codex-file-citation]{line_range_start=157 line_range_end=157 path=app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt#L157-L157"}

:::task-stub{title="Migrate deprecated WiFi and network APIs"}
1. Replace `ConnectivityManager.getNetworkInfo(TYPE_WIFI)` with `NetworkCapabilities`-based checks in `DashboardElementActivity.kt:175`
2. Replace `WifiManager.isWifiEnabled` setter calls with `Settings.Panel.ACTION_WIFI` intent in `DashboardElementActivity.kt:179,187` and `BaseResourceFragment.kt:155`
3. Replace `WifiManager.configuredNetworks` usage with `NetworkSuggestion` API in `DashboardElementActivity.kt:205-208`
4. Replace `WifiManager.connectionInfo` with `ConnectivityManager.getNetworkCapabilities()` in `SyncManager.kt:530` and `NetworkUtils.kt:157`
5. Verify WiFi toggle and network detection still function on API 26+ devices
:::

### Modernize WebView deprecated settings and callbacks
`WebSettings.forceDark`, `setSavePassword()`, `allowFileAccessFromFileURLs`, and the old `onReceivedError` / `onConsoleMessage` signatures are deprecated. Modern replacements exist via `WebSettingsCompat` and the newer callback overloads.

:codex-file-citation[codex-file-citation]{line_range_start=76 line_range_end=109 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt#L76-L109"}
:codex-file-citation[codex-file-citation]{line_range_start=189 line_range_end=190 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt#L189-L190"}
:codex-file-citation[codex-file-citation]{line_range_start=277 line_range_end=279 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt#L277-L279"}

:::task-stub{title="Modernize WebView settings and callbacks"}
1. Replace `WebSettings.forceDark` with `WebSettingsCompat.setAlgorithmicDarkeningAllowed()` at lines 102, 109
2. Remove `setSavePassword(false)` call at line 95 (no-op since API 18)
3. Remove `allowFileAccessFromFileURLs` and `allowUniversalAccessFromFileURLs` assignments at lines 76-77 (default is `false` since API 30, and minSdk 26 defaults are safe)
4. Replace old `onReceivedError(WebView?, Int, String?, String?)` override at line 189 with `onReceivedError(WebView, WebResourceRequest, WebResourceError)` variant
5. Replace old `onConsoleMessage(String?, Int, String?)` override at line 277 with `onConsoleMessage(ConsoleMessage)` variant
6. Add `@Deprecated` annotation to any overrides that must remain for backward compatibility
:::

### Remove redundant Elvis operators and unnecessary casts
Multiple Elvis operators (`?:`) on non-nullable `Long` types always return the left operand. Similarly, several unnecessary casts and always-true conditions add noise. Removing these makes intent clearer and eliminates compiler warnings.

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=79 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt#L55-L79"}
:codex-file-citation[codex-file-citation]{line_range_start=163 line_range_end=163 path=app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt#L163-L163"}
:codex-file-citation[codex-file-citation]{line_range_start=69 line_range_end=69 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L69-L69"}
:codex-file-citation[codex-file-citation]{line_range_start=20 line_range_end=20 path=app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt#L20-L20"}

:::task-stub{title="Remove redundant Elvis operators and casts"}
1. In `SubmissionViewModel.kt`, remove `?: 0` from `lastUpdateTime` sorts at lines 55, 67, 75, 79 since `lastUpdateTime` is non-nullable `Long`
2. In `LoginSyncManager.kt:163`, simplify the always-true condition
3. In `BaseRecyclerParentFragment.kt:69`, remove the unnecessary cast
4. In `RatingsRepositoryImpl.kt:20`, remove the unnecessary cast
5. In `ProcessUserDataActivity.kt:300`, remove the Elvis on non-nullable `SharedPreferences`
6. Rebuild and confirm zero "always returns the left operand" or "No cast needed" warnings
:::

### Replace deprecated internal APIs with repository equivalents
Several call sites still use deprecated companion-object methods (`isPlanetAvailable`, `checkVersion`, `healthAccess`, `getCourseSteps`, `getCurrentProgress`, `isMyCourse`, `getMyLibraryByUserId`, `serializeNews`) and deprecated `realmInstance` property. Repository-based replacements already exist and are referenced in the deprecation messages.

:codex-file-citation[codex-file-citation]{line_range_start=206 line_range_end=225 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L206-L225"}
:codex-file-citation[codex-file-citation]{line_range_start=479 line_range_end=479 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L479-L479"}
:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=82 path=app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt#L56-L82"}
:codex-file-citation[codex-file-citation]{line_range_start=50 line_range_end=53 path=app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmCourseProgress.kt#L50-L53"}
:codex-file-citation[codex-file-citation]{line_range_start=190 line_range_end=190 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L190-L190"}

:::task-stub{title="Migrate deprecated internal APIs to repository equivalents"}
1. Replace `DataService.isPlanetAvailable()` calls with `ConfigurationsRepository.checkServerAvailability()` in `BaseResourceFragment.kt` at lines 206, 225, 479
2. Replace `DataService.checkVersion()` with `ConfigurationsRepository.checkVersion()` in `AutoSyncWorker.kt:56`
3. Replace `DataService.healthAccess()` with `ConfigurationsRepository.checkHealth()` in `AutoSyncWorker.kt:82`
4. Replace `RealmCourseStep.getCourseSteps()` and `RealmCourseProgress.getCurrentProgress()` with `CoursesRepository` and `ProgressRepository` equivalents in `RealmCourseProgress.kt:50-53`
5. Replace `RealmMyLibrary.getMyLibraryByUserId()` with `ResourcesRepository.getLibraryByUserId()` in `RealmMyLibrary.kt:190`
6. Replace `RealmNews.serializeNews()` with `ChatRepository.serializeNews()` in `UploadConfigs.kt:206`
7. Replace `DatabaseService.realmInstance` with `withRealm`/`withRealmAsync` in `BaseRecyclerFragment.kt:90`, `BaseResourceFragment.kt:97,374`, `AddExaminationActivity.kt:84`, `RealmConnectionPool.kt:107`, `ThreadSafeRealmManager.kt:14,21`
:::
