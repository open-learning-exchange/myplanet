### Remove deprecated `android.newDsl=false` property
The build log warns that `android.newDsl=false` is deprecated with a default of `true` and will be removed in AGP 10.0. Removing this property eliminates the warning and prepares the project for the next major AGP upgrade.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=28 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L28-L28"}

:::task-stub{title="Remove deprecated android.newDsl=false property"}
1. Delete the line `android.newDsl=false` from `gradle.properties`
2. Run `./gradlew assembleLiteDebug --warning-mode all` and confirm the `newDsl` deprecation warning is gone
3. Verify both `default` and `lite` flavors still configure correctly
:::

### Remove deprecated `android.enableJetifier=true` property
The build log warns that `android.enableJetifier=true` is deprecated with a default of `false` and will be removed in AGP 10.0. Jetifier rewrites legacy support-library references; since the project already uses AndroidX exclusively, it is unnecessary overhead on every build.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L25-L25"}

:::task-stub{title="Remove deprecated android.enableJetifier=true property"}
1. Set `android.enableJetifier=false` in `gradle.properties` (or remove the line entirely)
2. Run `./gradlew assembleLiteDebug assembleDefaultDebug` and check for any `android.support` class-not-found errors
3. If a transitive dependency still needs Jetifier, identify and update that dependency instead
:::

### Migrate from KAPT to KSP for Hilt annotation processing
The build still uses `kotlin-kapt` for Hilt (`kaptLiteReleaseKotlin` task visible in logs). KAPT spawns a separate Java stub-generation step and is significantly slower than KSP. Hilt supports KSP since Dagger 2.48, and this project already uses KSP for Glide.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=7 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L1-L7"}
:codex-file-citation[codex-file-citation]{line_range_start=152 line_range_end=154 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L152-L154"}

:::task-stub{title="Migrate Hilt annotation processing from KAPT to KSP"}
1. Remove `alias(libs.plugins.legacy.kapt)` from `app/build.gradle` plugins block
2. Remove the `kapt { correctErrorTypes = true }` block
3. Change `kapt(libs.hilt.android.compiler)` usages to `ksp(libs.hilt.android.compiler)` (if any remain outside the existing `ksp` lines)
4. Add `dagger.hilt.android.internal.disableAndroidSuperclassValidation=true` and `dagger.hilt.android.internal.projectType=APP` to KSP arguments if needed
5. Run full build for both flavors and confirm Hilt-generated components are created correctly
:::

### Migrate from legacy `buildscript` classpath to plugins DSL
The root `build.gradle.kts` uses a `buildscript { dependencies { classpath(...) } }` block, which bypasses Gradle's plugin resolution caching and version catalog integration. Migrating to the `plugins {}` DSL in both the root and settings files enables better caching and configuration-cache compatibility.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=16 path=build.gradle.kts git_url="https://github.com/open-learning-exchange/myplanet/blob/master/build.gradle.kts#L3-L16"}

:::task-stub{title="Migrate root build.gradle.kts from buildscript classpath to plugins DSL"}
1. Move plugin declarations to `plugins { id(...) version ... apply false }` in root `build.gradle.kts`
2. Declare Realm, Hilt, KSP, and Kotlin plugins with `apply false` in the root plugins block
3. Remove the entire `buildscript {}` block
4. Update `settings.gradle` pluginManagement to resolve custom plugin IDs (e.g., `realm-android`)
5. Verify `./gradlew assembleLiteDebug assembleDefaultDebug` succeeds with configuration cache enabled
:::

### Enable R8 minification for release builds
The release build has `minifyEnabled = false`, meaning no code shrinking, obfuscation, or dead-code elimination occurs. Enabling R8 for release builds reduces APK size and removes unused classes from the 60+ dependencies.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=37 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L28-L37"}

:::task-stub{title="Enable R8 minification for release builds"}
1. Set `minifyEnabled = true` and `shrinkResources = true` in the `release` buildType
2. Add Realm-specific ProGuard keep rules to `proguard-rules.pro` (all `RealmObject` subclasses, Realm proxy classes)
3. Add Retrofit/Gson keep rules for serialized model classes
4. Add Hilt keep rules if not auto-included by the Hilt Gradle plugin
5. Build release APK and verify it installs and runs without `ClassNotFoundException` or reflection failures
6. Compare APK sizes before and after to quantify improvement
:::

### Deduplicate the `repositories` block between root and app module
Repositories are declared in three places: `settings.gradle` pluginManagement, root `build.gradle.kts` allprojects, and `app/build.gradle`. The app module adds JitPack, Sonatype snapshots, and Google Maven redundantly. Centralizing in `settings.gradle` `dependencyResolutionManagement` avoids redundant resolution and ensures consistency.

:codex-file-citation[codex-file-citation]{line_range_start=160 line_range_end=165 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L160-L165"}
:codex-file-citation[codex-file-citation]{line_range_start=18 line_range_end=23 path=build.gradle.kts git_url="https://github.com/open-learning-exchange/myplanet/blob/master/build.gradle.kts#L18-L23"}

:::task-stub{title="Centralize repository declarations in settings.gradle"}
1. Add a `dependencyResolutionManagement { repositories { ... } }` block in `settings.gradle` with google(), mavenCentral(), JitPack, and Sonatype snapshots
2. Remove the `repositories {}` block from `app/build.gradle`
3. Remove the `allprojects { repositories {} }` block from root `build.gradle.kts`
4. Run `./gradlew dependencies` to confirm all artifacts still resolve
:::

### Fix Kotlin compiler warnings to enable `allWarningsAsErrors`
The build emits 25+ Kotlin warnings including unnecessary safe calls, unchecked casts, deprecated API usage, and always-true conditions. Fixing these warnings and then enabling `-Werror` prevents regressions and improves code quality.

:codex-file-citation[codex-file-citation]{line_range_start=155 line_range_end=159 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L155-L159"}

:::task-stub{title="Fix Kotlin compiler warnings and enable allWarningsAsErrors"}
1. Fix unnecessary safe calls in `RealmSubmission.kt`, `NotificationsRepositoryImpl.kt`, `UploadManager.kt`, `TeamFragment.kt`, `UrlUtils.kt`, `SyncTimeLogger.kt`, `ServerDialogExtensions.kt`
2. Fix always-true elvis operators in `SurveysRepositoryImpl.kt`, `TeamsRepositoryImpl.kt`, `TransactionSyncManager.kt`
3. Fix unchecked cast in `BaseRecyclerFragment.kt` and `RealtimeSyncMixin.kt`
4. Address deprecated API usages in `DashboardElementActivity.kt`, `SecurePrefs.kt`, `UserSessionManager.kt`
5. Add `@OptIn(FlowPreview::class)` to `RealtimeSyncMixin.kt`
6. Add `allWarningsAsErrors = true` to `kotlin { compilerOptions {} }` block
7. Verify clean build with zero warnings for both flavors
:::

### Enable non-transitive R classes
The property `android.nonTransitiveRClass=false` causes every module to see every other module's R class fields, slowing down R class generation and increasing memory usage during compilation. Setting it to `true` is the modern default and speeds up resource processing.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=26 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L26-L26"}

:::task-stub{title="Enable non-transitive R classes"}
1. Set `android.nonTransitiveRClass=true` in `gradle.properties`
2. Search for any usages of `R.` that reference resources from dependency libraries (e.g., `com.google.android.material.R.`)
3. Replace those with fully qualified R class references or import the correct R class
4. Build both flavors and fix any unresolved resource reference errors
:::

### Cache CI builds with GitHub Actions build cache
The CI workflow uses `gradle/actions/setup-gradle@v6` which supports build caching, but the `settings.gradle` remote cache block requires explicit URL configuration that CI does not provide. Enabling the Gradle build cache push in CI via environment variables would make subsequent builds significantly faster.

:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=43 path=settings.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/settings.gradle#L26-L43"}
:codex-file-citation[codex-file-citation]{line_range_start=17 line_range_end=32 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L17-L32"}

:::task-stub{title="Enable remote Gradle build cache in CI"}
1. Configure `settings.gradle` to read cache URL from `GRADLE_BUILD_CACHE_URL` environment variable as fallback to local.properties
2. Add `GRADLE_BUILD_CACHE_URL` as a GitHub Actions secret or use the `gradle/actions` built-in caching with `cache-read-only: false`
3. Ensure the build workflow passes `--build-cache` flag (already enabled via `org.gradle.caching=true`)
4. Verify cache hits on second CI run by checking `FROM-CACHE` task counts
:::

### Reduce configuration time by removing `fileTree` dependency scanning
The `implementation fileTree(include: ['*.jar', '*.aar'], dir: 'libs')` line forces Gradle to scan the `libs/` directory on every configuration phase. If the directory is empty or contains no files, this is wasted work. If it has files, they should be published as proper dependencies.

:codex-file-citation[codex-file-citation]{line_range_start=181 line_range_end=181 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L181-L181"}

:::task-stub{title="Remove or replace fileTree dependency on libs/ directory"}
1. Check if `app/libs/` contains any JAR or AAR files
2. If empty, remove the `implementation fileTree(...)` line entirely
3. If files exist, publish them to a local Maven repository or replace with proper Maven coordinates
4. Verify build succeeds without the fileTree declaration
:::

### Exclude lite flavor from unnecessary resource processing
The lite flavor currently compiles the full set of 169 layout files, all drawables, and all translations identical to the default flavor. If the lite flavor is meant to be lightweight, it should use resource shrinking or flavor-specific resource overrides to exclude unused resources and reduce build time.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=52 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L39-L52"}
:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=4 path=app/src/lite/AndroidManifest.xml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/lite/AndroidManifest.xml#L1-L4"}

:::task-stub{title="Add lite-flavor source set with reduced resources"}
1. Create `app/src/lite/res/` directory for lite-specific resource overrides
2. Identify features disabled in lite mode by searching for `BuildConfig.LITE` conditionals
3. Add `resConfigs` to the lite flavor to limit included language translations if appropriate
4. Create lite-specific layout files that exclude UI components gated behind `BuildConfig.LITE`
5. Verify the lite APK is measurably smaller than the default APK after changes
:::
