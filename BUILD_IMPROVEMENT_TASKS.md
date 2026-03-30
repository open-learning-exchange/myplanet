### Remove Deprecated Android Gradle Plugin Properties

The two properties `android.newDsl=false` and `android.enableJetifier=true` are both deprecated and will be
removed in AGP 10.0. Removing them eliminates the two WARNING lines emitted at configuration time, keeps the
project forward-compatible, and slightly speeds up the configuration phase by no longer running the Jetifier
transformation over dependencies.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/main/gradle.properties#L25-L25"}
:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=28 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/main/gradle.properties#L28-L28"}

:::task-stub{title="Remove deprecated android.newDsl and android.enableJetifier from gradle.properties"}
1. Open `gradle.properties` and delete the line `android.enableJetifier=true` (line 25).
2. Delete the line `android.newDsl=false` (line 28).
3. Run `./gradlew assembleDefaultDebug` and confirm the two WARNING lines are gone from the output.
4. If the build fails after removing Jetifier, identify the legacy library still requiring it, migrate or replace it, then retry.
:::

### Enable R8 Minification and Resource Shrinking for Release Builds

Both release and debug build types set `minifyEnabled = false`, which disables dead-code elimination, obfuscation,
and class-file shrinking entirely. Enabling R8 together with `shrinkResources = true` for release builds
typically reduces APK/AAB size by 30–50 % and hardens the binary against reverse-engineering without any runtime
cost.

:codex-file-citation[codex-file-citation]{line_range_start=29 line_range_end=32 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L29-L32"}

:::task-stub{title="Enable R8 minification and resource shrinking for the release build type"}
1. In `app/build.gradle`, change `minifyEnabled = false` to `minifyEnabled = true` in the `release` block (line 30).
2. Add `shrinkResources = true` directly after `minifyEnabled` in the same `release` block.
3. Verify that `proguardFiles` already references `proguard-rules.pro` (it does — line 31); add keep rules there for any Realm models, Retrofit interfaces, and Hilt entry points that fail with shrinking.
4. Build a release APK/AAB, install it on a device or emulator, and smoke-test the main user flows.
5. Capture the before/after APK size from the build output and document the reduction.
:::

### Enable Non-Transitive R Classes for Faster Incremental Builds

`android.nonTransitiveRClass=false` forces every module to regenerate a full transitive R class that contains
all resource IDs from all transitive dependencies. Setting this to `true` makes each module's R class contain
only its own resources, dramatically shrinking R.java and reducing the scope of incremental recompilation when
any resource changes.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=26 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/main/gradle.properties#L26-L26"}

:::task-stub{title="Switch android.nonTransitiveRClass to true and fix any resulting R reference errors"}
1. In `gradle.properties`, change `android.nonTransitiveRClass=false` to `android.nonTransitiveRClass=true` (line 26).
2. Run `./gradlew assembleDefaultDebug` and collect every "cannot find symbol" or "unresolved reference" compile error.
3. For each broken reference, add the correct import for the module that owns the resource (e.g. `import androidx.appcompat.R` → `import org.ole.planet.myplanet.R`).
4. Re-run the build until it is clean, then measure the incremental rebuild time on a resource-only change.
:::

### Fix Unchecked Casts in BaseRecyclerFragment and BaseRecyclerParentFragment

`BaseRecyclerFragment` casts `List<Any?>` to `List<RealmMyCourse>` without a type check, which will throw a
`ClassCastException` at runtime if the list contains items of any other type. `BaseRecyclerParentFragment`
triggers a "no cast needed" warning, indicating dead code that obscures the actual type flow. Both should be
corrected to eliminate the compiler warnings and improve type safety.

:codex-file-citation[codex-file-citation]{line_range_start=225 line_range_end=229 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L225-L229"}
:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L27-L31"}

:::task-stub{title="Replace unchecked cast in BaseRecyclerFragment and remove unnecessary cast in BaseRecyclerParentFragment"}
1. In `BaseRecyclerFragment.kt` at line 227, replace the raw unchecked cast with a safe `filterIsInstance<RealmMyCourse>()` call, or refactor the method to be generic so the cast is provably correct.
2. In `BaseRecyclerParentFragment.kt` at line 29, remove the unnecessary cast and use the type that is already guaranteed by the compiler.
3. Run `./gradlew compileLiteReleaseKotlin` and confirm both warnings are gone.
:::

### Eliminate Redundant Null-Safety Operators Across the Codebase

The compiler reports fourteen unnecessary `?.` safe-call and `?:` Elvis operators across
`RealmSubmission`, `NotificationsRepositoryImpl`, `UploadManager`, `ServerDialogExtensions`,
`TeamFragment`, `UrlUtils`, `SyncTimeLogger`, and `CoursesFragment`. These operators add
cognitive noise, imply that the receiver could be null when it cannot, and can hide real nullability
bugs introduced later if the type ever changes.

:codex-file-citation[codex-file-citation]{line_range_start=61 line_range_end=68 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L61-L68"}
:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L41-L45"}
:codex-file-citation[codex-file-citation]{line_range_start=269 line_range_end=273 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L269-L273"}
:codex-file-citation[codex-file-citation]{line_range_start=352 line_range_end=356 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L352-L356"}
:codex-file-citation[codex-file-citation]{line_range_start=116 line_range_end=120 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt#L116-L120"}
:codex-file-citation[codex-file-citation]{line_range_start=282 line_range_end=286 path=app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamFragment.kt#L282-L286"}
:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=44 path=app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt#L39-L44"}
:codex-file-citation[codex-file-citation]{line_range_start=87 line_range_end=91 path=app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt#L87-L91"}
:codex-file-citation[codex-file-citation]{line_range_start=293 line_range_end=297 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L293-L297"}

:::task-stub{title="Remove all unnecessary safe-call and always-true condition warnings flagged by the Kotlin compiler"}
1. In `RealmSubmission.kt` lines 63 and 66, replace `sub?.` with `sub.` since the object is confirmed non-null in the preceding `if (sub == null)` guard.
2. In `NotificationsRepositoryImpl.kt` line 43, remove the `?.` from `notification?.isFromServer` since `findFirst()` was already guarded against null by the surrounding `let` or `if` block.
3. In `UploadManager.kt` lines 271 and 354, remove `?.forEach` / `?.associateBy` — replace with `.forEach` / `.associateBy` on the non-null list.
4. In `ServerDialogExtensions.kt` line 118, remove the redundant `?.replace` safe call on the `String` receiver.
5. In `TeamFragment.kt` line 284, remove the unnecessary `?.contains` safe call.
6. In `UrlUtils.kt` line 41, replace `hostIp?.endsWith(...)` with `hostIp.endsWith(...)` after adding a non-null assertion or restructuring the null check above.
7. In `SyncTimeLogger.kt` line 89, remove the `?.let` wrapper around the guaranteed non-null `alternativeUrl` string.
8. In `CoursesFragment.kt` line 295, remove the dead `view != null` check inside the `submitList` callback (the `submitList` lambda only runs while the view is alive).
9. Run `./gradlew compileLiteReleaseKotlin` and confirm all fourteen warnings are gone.
:::

### Remove Always-Redundant Elvis Operators in Repository and Sync Layers

The Kotlin compiler reports that the Elvis operator `?:` always returns its left operand in five locations across
`SurveysRepositoryImpl`, `TeamsRepositoryImpl`, and `TransactionSyncManager` because the left-hand expression
is already a non-nullable type. These operators are dead code that obscure the true type contracts and should be
removed.

:codex-file-citation[codex-file-citation]{line_range_start=67 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L67-L73"}
:codex-file-citation[codex-file-citation]{line_range_start=155 line_range_end=160 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L155-L160"}
:codex-file-citation[codex-file-citation]{line_range_start=1305 line_range_end=1309 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1305-L1309"}
:codex-file-citation[codex-file-citation]{line_range_start=104 line_range_end=108 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L104-L108"}

:::task-stub{title="Delete dead Elvis fallbacks that always return the left operand"}
1. In `SurveysRepositoryImpl.kt` lines 69, 157, and 158, remove the `?: ""` fallbacks from the non-nullable `String` expressions and update the type signatures if needed.
2. In `TeamsRepositoryImpl.kt` line 1307, remove the `?: 0` fallback from the non-nullable `Long` expression.
3. In `TransactionSyncManager.kt` line 106, remove the `?: ""` fallback from the non-nullable `String` result.
4. Run `./gradlew compileLiteReleaseKotlin` and verify all five warnings are resolved.
:::

### Migrate All Callers of the Deprecated `getUserModel()` Property to the Suspend Version

`UserSessionManager` marks the synchronous `userModel` property and `getUserModelCopy()` function as
`@Deprecated` and directs callers to use the `suspend fun getUserModel()` instead. There are at least fifteen
call sites across base fragments, repositories, and `MainApplication` still using the deprecated API, blocking
the eventual removal of the blocking Realm thread access.

:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L36-L45"}
:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=84 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L80-L84"}
:codex-file-citation[codex-file-citation]{line_range_start=239 line_range_end=243 path=app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt#L239-L243"}

:::task-stub{title="Replace all deprecated synchronous getUserModel() call sites with the suspend equivalent"}
1. Run `grep -rn "\.userModel\b\|getUserModelCopy()\|profileDbHandler\.getUserModel()" app/src/main/java` to list every call site.
2. For each call site that is already inside a `lifecycleScope.launch` or `viewModelScope.launch` block, replace it with `userSessionManager.getUserModel()` (the suspend overload).
3. For call sites in synchronous contexts (e.g. `onCreateView`), wrap the logic in a `lifecycleScope.launch { }` block and move the dependent code inside.
4. After all callers are migrated, delete the deprecated `val userModel` property and `getUserModelCopy()` function from `UserSessionManager`.
5. Run the full debug build and confirm the deprecation warning for these symbols is gone.
:::

### Migrate Deprecated WiFi APIs in DashboardElementActivity

`DashboardElementActivity` uses `WifiManager.isWifiEnabled`, `WifiManager.configuredNetworks`, and
`WifiManager.enableNetwork`, all of which are deprecated since API 29. These APIs are no-ops or restricted on
Android 10+ and should be replaced with `WifiManager.disconnect()` / intent-based settings navigation for
modern devices, with the pre-Q path retained under a version guard.

:codex-file-citation[codex-file-citation]{line_range_start=185 line_range_end=220 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L185-L220"}

:::task-stub{title="Replace deprecated WifiManager APIs in DashboardElementActivity with API-level-guarded modern alternatives"}
1. In `DashboardElementActivity.kt` lines 187 and 195, replace direct `wifi.isWifiEnabled = false/true` calls with `startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))` for API 29+, keeping the old path inside `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)`.
2. At lines 214–217, guard the `configuredNetworks` / `enableNetwork` block with `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)` so it is never reached on Android 10+.
3. For API 29+ devices, implement the reconnect flow using `WifiNetworkSpecifier` + `NetworkRequest` via `ConnectivityManager.requestNetwork`.
4. Annotate the legacy branch with `@Suppress("DEPRECATION")` and an explanatory comment to make the intentional use visible.
5. Run `./gradlew lintDebug` and confirm the WiFi deprecation warnings are resolved or suppressed.
:::

### Annotate `@OptIn(FlowPreview::class)` and Fix Unchecked ListAdapter Cast in RealtimeSyncMixin

`RealtimeSyncMixin.kt` uses `Flow.debounce()`, which is marked `@FlowPreview`, without opting in to the preview
API — the compiler warns that this usage may break in a future coroutines release. On the same file, line 54
unsafely casts a `ListAdapter<*, *>` to `ListAdapter<Any, *>`, which can produce a heap-pollution warning and a
runtime `ClassCastException` if the adapter's item type is ever something other than `Any`.

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L35-L42"}
:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L52-L58"}

:::task-stub{title="Opt in to FlowPreview and remove the unchecked ListAdapter cast in RealtimeSyncMixin"}
1. Add `@OptIn(kotlinx.coroutines.FlowPreview::class)` to the `setupRealtimeSync()` function (or to the enclosing `RealtimeSyncHelper` class) to silence the preview-API warning at line 37.
2. At line 54, replace the raw `(adapter as ListAdapter<Any, *>)` cast with a helper that calls `submitList` via reflection or redefine the `OnDiffRefreshListener` interface to expose a type-safe `submitCurrentList()` method, removing the need for the cast entirely.
3. Run `./gradlew compileLiteReleaseKotlin` and confirm both warnings are resolved.
:::

### Replace Deprecated `EncryptedSharedPreferences` and `MasterKey` in SecurePrefs

`SecurePrefs.kt` imports `androidx.security.crypto.EncryptedSharedPreferences` and `androidx.security.crypto.MasterKey`, both of which are deprecated in `security-crypto` 1.1.0-alpha in favour of the Google Tink library — which the project already depends on (`com.google.crypto.tink:tink-android`). Migrating removes the deprecation warning and aligns the encryption strategy with the modern, actively maintained Tink primitives already used elsewhere in the codebase.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt#L1-L14"}

:::task-stub{title="Migrate SecurePrefs from deprecated EncryptedSharedPreferences/MasterKey to Tink-based encrypted storage"}
1. Audit all call sites of `SecurePrefs` to understand which keys are stored and whether migration of existing encrypted data is required.
2. Implement a Tink-backed replacement using `AeadConfig.register()`, `AndroidKeysetManager`, and `Aead` (already imported in `SecurePrefs.kt`) to encrypt/decrypt preference values written to a plain `SharedPreferences` file.
3. Add a one-time migration path that reads existing values via the old `EncryptedSharedPreferences` API and re-writes them through the new Tink layer, then deletes the legacy preference file.
4. Remove the `EncryptedSharedPreferences` and `MasterKey` imports and verify the file compiles without deprecation warnings.
5. Run `./gradlew compileLiteReleaseKotlin` and confirm the two deprecation warnings at lines 7–8 are gone.
:::
