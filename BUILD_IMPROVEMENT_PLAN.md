### Remove deprecated Android Gradle Plugin properties from gradle.properties

The properties `android.newDsl=false` and `android.enableJetifier=true` are both deprecated and will be removed in AGP 10.0. Removing them eliminates two WARNING lines at configuration time, drops the unnecessary Jetifier transformation pass, and keeps the project forward-compatible.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/main/gradle.properties#L25-L25"}
:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=28 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/main/gradle.properties#L28-L28"}

:::task-stub{title="Remove deprecated android.enableJetifier and android.newDsl from gradle.properties"}
1. Delete the line `android.enableJetifier=true` (line 25) from `gradle.properties`.
2. Delete the line `android.newDsl=false` (line 28) from `gradle.properties`.
3. Run `./gradlew assembleDefaultDebug assembleLiteDebug --warning-mode all` and confirm both deprecation WARNINGs are gone.
4. If the build fails after removing Jetifier, identify the legacy library still requiring it, migrate or replace it, then retry.
:::

### Migrate Hilt annotation processing from KAPT to KSP

The build still uses `kotlin-kapt` for Hilt, which spawns a separate Java stub-generation step and is significantly slower than KSP. Hilt supports KSP since Dagger 2.48, and this project already uses KSP for Glide. Migrating eliminates the `kaptGenerateStubs` tasks and the unrecognized-processor-option warnings.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=7 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L1-L7"}
:codex-file-citation[codex-file-citation]{line_range_start=152 line_range_end=154 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L152-L154"}

:::task-stub{title="Replace KAPT with KSP for Hilt and remove kapt plugin"}
1. Remove `alias(libs.plugins.legacy.kapt)` from the `plugins` block in `app/build.gradle` (line 3).
2. Remove the `kapt { correctErrorTypes = true }` block (lines 152-154).
3. Verify all Hilt compiler dependencies already use `ksp(...)` (lines 189, 209 already do).
4. Add KSP arguments for Hilt if needed: `ksp { arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true") }`.
5. Run `./gradlew assembleDefaultDebug assembleLiteDebug` and confirm Hilt-generated components are created correctly and no `kapt` tasks appear.
:::

### Enable non-transitive R classes

`android.nonTransitiveRClass=false` forces every module to regenerate a full transitive R class containing all resource IDs from all transitive dependencies. Setting it to `true` shrinks the R class, reduces incremental recompilation scope, and aligns with the modern AGP default.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=26 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/main/gradle.properties#L26-L26"}

:::task-stub{title="Switch android.nonTransitiveRClass to true and fix resulting R reference errors"}
1. In `gradle.properties`, change `android.nonTransitiveRClass=false` to `android.nonTransitiveRClass=true` (line 26).
2. Run `./gradlew assembleDefaultDebug` and collect every "unresolved reference" compile error.
3. For each broken reference, add the correct import for the module that owns the resource (e.g. `com.google.android.material.R` or `androidx.appcompat.R`).
4. Re-run the build until it is clean for both flavors.
:::

### Fix unchecked cast in BaseRecyclerFragment and unnecessary cast in BaseRecyclerParentFragment

`BaseRecyclerFragment` casts `List<Any?>` to `List<RealmMyCourse>` without a type check, risking a `ClassCastException` at runtime. `BaseRecyclerParentFragment` has a compiler-flagged unnecessary cast. Both should be corrected for type safety and to clear warnings.

:codex-file-citation[codex-file-citation]{line_range_start=225 line_range_end=229 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L225-L229"}
:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=31 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerParentFragment.kt#L27-L31"}

:::task-stub{title="Replace unchecked cast in BaseRecyclerFragment and remove unnecessary cast in BaseRecyclerParentFragment"}
1. In `BaseRecyclerFragment.kt` around line 227, replace the raw unchecked cast with `filterIsInstance<RealmMyCourse>()` or refactor the method to be generic so the cast is provably correct.
2. In `BaseRecyclerParentFragment.kt` around line 29, remove the unnecessary cast and use the type already guaranteed by the compiler.
3. Run `./gradlew compileLiteReleaseKotlin` and confirm both warnings are gone.
:::

### Eliminate redundant null-safety operators and always-true Elvis operators

The compiler reports fourteen unnecessary `?.` safe-call and `?:` Elvis operators across `RealmSubmission`, `NotificationsRepositoryImpl`, `UploadManager`, `ServerDialogExtensions`, `TeamFragment`, `UrlUtils`, `SyncTimeLogger`, `CoursesFragment`, `SurveysRepositoryImpl`, `TeamsRepositoryImpl`, and `TransactionSyncManager`. These operators are dead code that obscures true type contracts.

:codex-file-citation[codex-file-citation]{line_range_start=61 line_range_end=68 path=app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/model/RealmSubmission.kt#L61-L68"}
:codex-file-citation[codex-file-citation]{line_range_start=41 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt#L41-L45"}
:codex-file-citation[codex-file-citation]{line_range_start=269 line_range_end=273 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L269-L273"}
:codex-file-citation[codex-file-citation]{line_range_start=67 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L67-L73"}
:codex-file-citation[codex-file-citation]{line_range_start=1305 line_range_end=1309 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1305-L1309"}
:codex-file-citation[codex-file-citation]{line_range_start=104 line_range_end=108 path=app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt#L104-L108"}

:::task-stub{title="Remove all unnecessary safe-call, Elvis, and always-true condition warnings"}
1. In `RealmSubmission.kt` lines 63 and 66, replace `sub?.` with `sub.` since the object is confirmed non-null by the preceding guard.
2. In `NotificationsRepositoryImpl.kt` line 43, remove the `?.` from the already-guarded non-null receiver.
3. In `UploadManager.kt` lines 271 and 354, replace `?.forEach` / `?.associateBy` with `.forEach` / `.associateBy`.
4. In `ServerDialogExtensions.kt` line 118, remove the redundant `?.replace` safe call.
5. In `TeamFragment.kt` line 284, remove the unnecessary `?.contains` safe call.
6. In `UrlUtils.kt` line 41, replace `hostIp?.endsWith(...)` with `hostIp.endsWith(...)`.
7. In `SyncTimeLogger.kt` line 89, remove the `?.let` wrapper on the guaranteed non-null string.
8. In `CoursesFragment.kt` line 295, remove the dead `view != null` check.
9. In `SurveysRepositoryImpl.kt` lines 69, 157, and 158, remove the `?: ""` fallbacks from non-nullable `String` expressions.
10. In `TeamsRepositoryImpl.kt` line 1307, remove the `?: 0` fallback from the non-nullable `Long` expression.
11. In `TransactionSyncManager.kt` line 106, remove the `?: ""` fallback from the non-nullable `String` result.
12. Run `./gradlew compileLiteReleaseKotlin` and confirm all warnings are resolved.
:::

### Add FlowPreview opt-in and fix unchecked ListAdapter cast in RealtimeSyncMixin

`RealtimeSyncMixin.kt` uses `Flow.debounce()` (marked `@FlowPreview`) without opting in, and unsafely casts a `ListAdapter<*, *>` to `ListAdapter<Any, *>` which can cause heap pollution.

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L35-L42"}
:codex-file-citation[codex-file-citation]{line_range_start=52 line_range_end=58 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L52-L58"}

:::task-stub{title="Opt in to FlowPreview and fix unchecked ListAdapter cast in RealtimeSyncMixin"}
1. Add `@OptIn(kotlinx.coroutines.FlowPreview::class)` to the function or class using `debounce()` around line 37.
2. At line 54, replace the raw `(adapter as ListAdapter<Any, *>)` cast with a `@Suppress("UNCHECKED_CAST")` annotation or refactor the interface to expose a type-safe `submitCurrentList()` method.
3. Run `./gradlew compileLiteReleaseKotlin` and confirm both warnings are resolved.
:::

### Enable R8 minification and resource shrinking for release builds

Both build types set `minifyEnabled = false`, disabling dead-code elimination, obfuscation, and resource shrinking. Enabling R8 for release builds typically reduces APK/AAB size by 30-50% and hardens the binary.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=37 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L28-L37"}

:::task-stub{title="Enable R8 minification and resource shrinking for release builds"}
1. In `app/build.gradle`, change `minifyEnabled = false` to `minifyEnabled = true` in the `release` block (line 30).
2. Add `shrinkResources = true` directly after `minifyEnabled` in the same block.
3. Add Realm-specific ProGuard keep rules to `proguard-rules.pro` (all `RealmObject` subclasses, Realm proxy classes).
4. Add Retrofit/Gson keep rules for serialized model classes.
5. Add Hilt keep rules if not auto-included by the Hilt Gradle plugin.
6. Build a release APK, install on a device or emulator, and smoke-test main user flows.
7. Compare APK sizes before and after to quantify improvement.
:::

### Migrate root build.gradle.kts from buildscript classpath to plugins DSL

The root `build.gradle.kts` uses a `buildscript { dependencies { classpath(...) } }` block, bypassing Gradle's plugin resolution caching and version catalog integration. Migrating to the `plugins {}` DSL enables better caching and configuration-cache compatibility.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=16 path=build.gradle.kts git_url="https://github.com/open-learning-exchange/myplanet/blob/main/build.gradle.kts#L3-L16"}

:::task-stub{title="Migrate root build.gradle.kts from buildscript classpath to plugins DSL"}
1. Move plugin declarations to `plugins { id(...) version ... apply false }` in root `build.gradle.kts`.
2. Declare Realm, Hilt, KSP, and Kotlin plugins with `apply false` in the root plugins block.
3. Remove the entire `buildscript {}` block.
4. Update `settings.gradle` pluginManagement to resolve custom plugin IDs (e.g. `realm-android`).
5. Verify `./gradlew assembleLiteDebug assembleDefaultDebug` succeeds.
:::

### Centralize repository declarations in settings.gradle

Repositories are declared in three places: `settings.gradle` pluginManagement, root `build.gradle.kts` allprojects, and `app/build.gradle`. Centralizing in `settings.gradle` `dependencyResolutionManagement` avoids redundant resolution and ensures consistency.

:codex-file-citation[codex-file-citation]{line_range_start=160 line_range_end=165 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L160-L165"}
:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=23 path=build.gradle.kts git_url="https://github.com/open-learning-exchange/myplanet/blob/main/build.gradle.kts#L18-L23"}

:::task-stub{title="Centralize repository declarations in settings.gradle"}
1. Add a `dependencyResolutionManagement { repositories { ... } }` block in `settings.gradle` with `google()`, `mavenCentral()`, JitPack, and Sonatype snapshots.
2. Remove the `repositories {}` block from `app/build.gradle` (lines 160-165).
3. Remove the `allprojects { repositories {} }` block from root `build.gradle.kts` (lines 18-23).
4. Run `./gradlew dependencies` to confirm all artifacts still resolve.
:::

### Remove or replace fileTree dependency on libs/ directory

The `implementation fileTree(include: ['*.jar', '*.aar'], dir: 'libs')` line forces Gradle to scan the `libs/` directory on every configuration phase. The directory contains `ChipCloud-3.0.5.aar` and `flexbox-1.0.0.aar`, which should be published as proper Maven dependencies.

:codex-file-citation[codex-file-citation]{line_range_start=181 line_range_end=181 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L181-L181"}

:::task-stub{title="Replace fileTree dependency with proper Maven coordinates or local Maven repository"}
1. Identify Maven Central or JitPack coordinates for `ChipCloud-3.0.5` and `flexbox-1.0.0`.
2. Replace `implementation fileTree(...)` with explicit `implementation(...)` declarations using Maven coordinates.
3. If no Maven coordinates exist, publish the AARs to a project-local Maven repository and reference them.
4. Remove the `app/libs/` directory once all files are replaced.
5. Verify the build succeeds without the fileTree declaration.
:::

### Migrate deprecated WiFi APIs in DashboardElementActivity

`DashboardElementActivity` uses `WifiManager.isWifiEnabled`, `configuredNetworks`, and `enableNetwork`, all deprecated since API 29 and no-ops on Android 10+. These should be replaced with modern connectivity APIs under a version guard.

:codex-file-citation[codex-file-citation]{line_range_start=185 line_range_end=220 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivity.kt#L185-L220"}

:::task-stub{title="Replace deprecated WifiManager APIs with API-level-guarded modern alternatives"}
1. Guard `wifi.isWifiEnabled = false/true` calls at lines 187 and 195 with `if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)`, using `startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))` for API 29+.
2. Guard the `configuredNetworks` / `enableNetwork` block at lines 214-217 with the same version check.
3. For API 29+ devices, implement the reconnect flow using `WifiNetworkSpecifier` + `NetworkRequest` via `ConnectivityManager.requestNetwork`.
4. Annotate the legacy branch with `@Suppress("DEPRECATION")`.
5. Run `./gradlew lintDebug` and confirm the WiFi deprecation warnings are resolved or suppressed.
:::

### Replace deprecated EncryptedSharedPreferences in SecurePrefs

`SecurePrefs.kt` uses `EncryptedSharedPreferences` and `MasterKey`, both deprecated in `security-crypto` 1.1.0-alpha. The project already depends on Tink, so migrating aligns encryption with the actively maintained library.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=14 path=app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt#L1-L14"}

:::task-stub{title="Migrate SecurePrefs from deprecated EncryptedSharedPreferences to Tink-based storage"}
1. Audit all call sites of `SecurePrefs` to understand which keys are stored and whether migration of existing encrypted data is required.
2. Implement a Tink-backed replacement using `AeadConfig.register()`, `AndroidKeysetManager`, and `Aead` to encrypt/decrypt values written to a plain `SharedPreferences` file.
3. Add a one-time migration path that reads existing values via the old API and re-writes them through the new Tink layer.
4. Remove the `EncryptedSharedPreferences` and `MasterKey` imports and verify the file compiles without deprecation warnings.
:::

### Migrate deprecated synchronous getUserModel calls to suspend version

`UserSessionManager` marks the synchronous `userModel` property and `getUserModelCopy()` as `@Deprecated`, directing callers to the `suspend fun getUserModel()`. At least fifteen call sites still use the deprecated API, blocking removal of the blocking Realm thread access.

:codex-file-citation[codex-file-citation]{line_range_start=36 line_range_end=45 path=app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt#L36-L45"}
:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=84 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L80-L84"}

:::task-stub{title="Replace all deprecated synchronous getUserModel call sites with the suspend equivalent"}
1. Run `grep -rn "\.userModel\b\|getUserModelCopy()" app/src/main/java` to list every call site.
2. For call sites already inside a coroutine scope, replace with `userSessionManager.getUserModel()` (the suspend overload).
3. For call sites in synchronous contexts, wrap the logic in a `lifecycleScope.launch { }` block.
4. After all callers are migrated, delete the deprecated `val userModel` property and `getUserModelCopy()` from `UserSessionManager`.
5. Run the full debug build and confirm the deprecation warnings are gone.
:::

### Fix remaining Kotlin compiler warnings and enable allWarningsAsErrors

After the above fixes, audit for any remaining warnings (unchecked casts, deprecated API usage, always-true conditions) and enable `-Werror` to prevent regressions.

:codex-file-citation[codex-file-citation]{line_range_start=155 line_range_end=159 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L155-L159"}

:::task-stub{title="Enable allWarningsAsErrors after fixing remaining Kotlin warnings"}
1. Run `./gradlew compileLiteReleaseKotlin 2>&1 | grep -i "warning"` to inventory all remaining warnings.
2. Fix each remaining warning by category (unchecked cast, deprecated API, unused import, etc.).
3. Add `allWarningsAsErrors = true` to the `kotlin { compilerOptions {} }` block in `app/build.gradle`.
4. Verify a clean build with zero warnings for both flavors.
:::

### Enable remote Gradle build cache in CI

The CI workflow uses `gradle/actions/setup-gradle@v6` which supports caching, but the `settings.gradle` remote cache block requires explicit URL configuration that CI does not provide. Enabling cache push in CI would make subsequent builds significantly faster.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=43 path=settings.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/settings.gradle#L26-L43"}
:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=32 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/main/.github/workflows/build.yml#L17-L32"}

:::task-stub{title="Enable remote Gradle build cache in CI"}
1. Update `settings.gradle` to read cache URL from `GRADLE_BUILD_CACHE_URL` environment variable as fallback when `local.properties` is absent.
2. Add `GRADLE_BUILD_CACHE_URL` as a GitHub Actions secret and pass it as an environment variable in `build.yml`.
3. Set `push = true` only on trusted branches (e.g. `master`, `claude/**`).
4. Verify cache hits on second CI run by checking `FROM-CACHE` task counts in build output.
:::

### Add lite-flavor source set with reduced resources

The lite flavor currently compiles the full set of 169 layout files, all drawables, and all translations identical to the default flavor. If the lite flavor is meant to be lightweight, it should use resource shrinking or flavor-specific overrides.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=52 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/main/app/build.gradle#L39-L52"}

:::task-stub{title="Add lite-flavor source set with reduced resources"}
1. Create `app/src/lite/res/` directory for lite-specific resource overrides.
2. Identify features disabled in lite mode by searching for `BuildConfig.LITE` conditionals.
3. Add `resConfigs` to the lite flavor to limit included language translations if appropriate.
4. Create lite-specific layout files that exclude UI components gated behind `BuildConfig.LITE`.
5. Verify the lite APK is measurably smaller than the default APK after changes.
:::
