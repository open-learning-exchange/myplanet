### Fix workflow_dispatch trigger nested under push in test.yml
CI logs show the test workflow only ever runs on push events; `workflow_dispatch:` is indented under `push:` instead of being a top-level trigger key, so manual dispatch is silently broken. Moving it up one level is a two-line YAML fix.

:codex-file-citation[codex-file-citation]{line_range_start=3 line_range_end=7 path=.github/workflows/test.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/test.yml#L3-L7"}

:::task-stub{title="Fix workflow_dispatch nesting in test.yml trigger block"}
1. Open `.github/workflows/test.yml` and look at the `on:` block (lines 3–7).
2. Move `workflow_dispatch:` out from under `push:` so it is a sibling of `push:` at the top level of `on:`.
3. Push the branch and confirm the workflow still triggers on push, then verify the "Run workflow" button now appears in the Actions tab.
:::

### Update dogi/sign-android-release to a Node 24 compatible version
Every release run prints `##[warning]Node.js 20 is deprecated. The following actions target Node.js 20 but are being forced to run on Node.js 24: dogi/sign-android-release@v5.1 ...`. The action is being force-run on Node 24 today and will break when GitHub removes the compatibility shim.

:codex-file-citation[codex-file-citation]{line_range_start=58 line_range_end=65 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L58-L65"}

:::task-stub{title="Bump dogi/sign-android-release past the Node 20 deprecation"}
1. Check the dogi/sign-android-release repo (and its upstream) for a release that declares `runs.using: node24`.
2. Update the `uses:` ref on the "sign release APK and AAB" step in `.github/workflows/release.yml`.
3. Trigger the release workflow via workflow_dispatch and confirm the signing step succeeds and the Node 20 warning for this action is gone.
:::

### Update dogi/upload-release-action to a Node 24 compatible version
The same Node 20 deprecation warning names `dogi/upload-release-action@v2.9.0` in both build-flavor release jobs. This is a one-line version bump on the GitHub release upload step.

:codex-file-citation[codex-file-citation]{line_range_start=136 line_range_end=144 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L136-L144"}

:::task-stub{title="Bump dogi/upload-release-action past the Node 20 deprecation"}
1. Find the newest dogi/upload-release-action tag (or upstream svenstaro/upload-release-action) built on Node 24.
2. Update the `uses:` ref on the "release APK and AAB on GitHub" step in `.github/workflows/release.yml`.
3. Run the release workflow and confirm assets still attach to the `v${VERSION}` tag and the Node 20 warning no longer lists this action.
:::

### Update dogi/upload-google-play to a Node 24 compatible version
Release logs flag `dogi/upload-google-play@v1.1.4` in the Node 20 deprecation warning, and the step itself emits runtime `DeprecationWarning: punycode` (DEP0040) and `url.parse()` (DEP0169) messages during Play Store publishing. A newer action version addresses both.

:codex-file-citation[codex-file-citation]{line_range_start=86 line_range_end=110 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L86-L110"}

:::task-stub{title="Bump dogi/upload-google-play on both playstore publish steps"}
1. Find a dogi/upload-google-play release (or upstream r0adkll/upload-google-play) that targets Node 24.
2. Update the `uses:` ref on both the "publish AAB to playstore" and "publish AAB to playstore fallback" steps.
3. On the next master release, confirm the internal-track upload succeeds and the DEP0040/DEP0169 warnings are gone from the step log.
:::

### Stop concurrent matrix jobs from racing on the Gradle cache save
The release (lite) and test jobs log `Failed to save cache entry ... ReserveCacheError: Unable to reserve cache ... another job may be creating this cache` in the Post setup-gradle step, because parallel matrix jobs write identical cache keys. Marking the secondary flavor read-only eliminates the wasted upload and the warning.

:codex-file-citation[codex-file-citation]{line_range_start=30 line_range_end=33 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L30-L33"}

:::task-stub{title="Set cache-read-only for the lite matrix job in setup-gradle"}
1. In `.github/workflows/release.yml`, add `cache-read-only: ${{ matrix.build != 'default' }}` to the `gradle/actions/setup-gradle@v6` step's `with:` block.
2. Apply the same one-line input to `.github/workflows/build.yml` so only one flavor writes the runner cache.
3. Check the Post setup-gradle log of the next run for the absence of ReserveCacheError messages.
:::

### Deduplicate versionName parsing in release.yml
The release workflow seds `versionName` out of `app/build.gradle` twice — once in the "set release version" step and again inside the build step — so the two can drift and the script is harder to read. The build step can reuse the already-exported `ANDROID_VERSION` env value.

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=36 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L35-L36"}
:codex-file-citation[codex-file-citation]{line_range_start=51 line_range_end=56 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L51-L56"}

:::task-stub{title="Reuse ANDROID_VERSION instead of re-parsing build.gradle in release.yml"}
1. In the "build release APK and AAB" step, replace the second `VERSION=$(sed ...)` line with `VERSION=${ANDROID_VERSION}`.
2. Leave the lite `-lite` suffix logic unchanged.
3. Dispatch the release workflow and confirm `ANDROID_VERSION_NAME` still resolves to the same value in the step env dump.
:::

### Remove the deprecated android.enableJetifier flag
Every CI Gradle invocation warns `WARNING: The option setting 'android.enableJetifier=true' is deprecated.` The codebase is fully AndroidX, so Jetifier is likely doing nothing but slowing dependency transformation.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L25-L25"}

:::task-stub{title="Drop android.enableJetifier=true from gradle.properties"}
1. Run `./gradlew checkJetifier` (or inspect dependencies) to confirm no legacy android.support artifacts remain.
2. Delete the `android.enableJetifier=true` line from `gradle.properties`.
3. Build both flavors locally (`assembleDefaultDebug`, `assembleLiteDebug`) and confirm CI passes without the deprecation warning.
:::

### Remove the deprecated android.newDsl=false opt-out
CI logs warn `WARNING: The option setting 'android.newDsl=false' is deprecated.` on every Gradle run; AGP is dropping this escape hatch, so the project must move off it anyway, and removing one properties line is the smallest possible probe.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=28 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L28-L28"}

:::task-stub{title="Drop android.newDsl=false from gradle.properties"}
1. Delete the `android.newDsl=false` line from `gradle.properties`.
2. Run `./gradlew assembleDefaultDebug assembleLiteDebug testDefaultDebugUnitTest` and note any new-DSL breakages.
3. If the build breaks, revert and file a follow-up issue listing the exact errors; if it passes, push and confirm the CI warning is gone.
:::

### Track down the Gradle 10 project-notation deprecation
CI logs warn twice per build that `Using a Project object as a dependency notation has been deprecated. This will fail with an error in Gradle 10.` — the stacktrace points into AGP's VariantDependenciesBuilder, so it is triggered by a plugin/legacy-DSL path rather than a repo dependency line. Identifying the source now avoids a hard failure at the Gradle 10 upgrade.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=7 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L1-L7"}

:::task-stub{title="Identify the plugin emitting the Gradle 10 project-notation deprecation"}
1. Run `./gradlew assembleDefaultDebug --warning-mode all --stacktrace` locally and capture the full deprecation stacktrace.
2. Determine whether the trigger is the legacy DSL path (`android.newDsl=false`), the realm-android plugin, or kapt configuration by toggling each in isolation.
3. Document the culprit and the fix (or upstream issue link) in a short GitHub issue so the Gradle 10 upgrade is unblocked.
:::

### Remove the redundant -Xannotation-default-target compiler flag
Every compilation in every workflow reports `The argument '-Xannotation-default-target=param-property' is redundant for the current language version 2.4.` The flag became the default in Kotlin 2.4, so the line can simply be deleted.

:codex-file-citation[codex-file-citation]{line_range_start=153 line_range_end=157 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L153-L157"}

:::task-stub{title="Delete redundant -Xannotation-default-target flag from app/build.gradle"}
1. Remove the `freeCompilerArgs.add("-Xannotation-default-target=param-property")` line from the `kotlin { compilerOptions { ... } }` block.
2. If the block becomes empty, remove the empty block too.
3. Run `./gradlew compileDefaultDebugKotlin` and confirm the redundant-argument warning disappears from the output.
:::

### Silence the kapt unrecognized-processor-options warning
kapt warns on every build: `The following options were not recognized by any processor: '[dagger.hilt.internal.useAggregatingRootProcessor, kapt.kotlin.generated, dagger.fastInit, ...]'` — Hilt runs through KSP here, so its kapt-side arguments are consumed by nothing (only Realm still uses kapt). Cleaning this up removes a misleading warning from every CI log.

:codex-file-citation[codex-file-citation]{line_range_start=150 line_range_end=152 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L150-L152"}

:::task-stub{title="Stop passing unused Hilt arguments to kapt"}
1. Reproduce the warning locally with `./gradlew kaptDefaultDebugKotlin` and note the exact option list.
2. Identify which plugin injects the dagger/hilt kapt options (Hilt Gradle plugin applies them even when Hilt uses KSP) and disable or scope them, e.g. via the Hilt plugin's kapt-related settings or by filtering kapt arguments for the Realm-only processor.
3. Rebuild and confirm the "options were not recognized" warning no longer appears while `kaptDefaultDebugKotlin` (Realm) still succeeds.
:::

### Replace deprecated Thread id usage in MainApplication
CI compilation flags `'val id: Long' is deprecated. Deprecated in Java.` at MainApplication.kt line 211, where `Thread.currentThread().id` seeds a TrafficStats tag. Switching to a fixed tag constant (or `threadId()` on API 36 toolchains) is a one-line fix.

:codex-file-citation[codex-file-citation]{line_range_start=211 line_range_end=211 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L211-L211"}

:::task-stub{title="Remove deprecated Thread.id call in MainApplication traffic tagging"}
1. In `MainApplication.kt` line 211, replace `Thread.currentThread().id.toInt()` with a stable app-defined socket tag constant (e.g. a named `THREAD_STATS_TAG` int).
2. Run `./gradlew compileDefaultDebugKotlin` and confirm the deprecation warning for this file is gone.
3. Smoke-check server reachability still works (the surrounding `isServerReachable` path).
:::

### Replace deprecated Thread id usage in NetworkModule socket factory
The same `'val id: Long' is deprecated` compiler warning fires for NetworkModule.kt line 25 inside `TaggedSocketFactory`, which tags sockets with `Thread.currentThread().id.toInt()`. A constant tag keeps StrictMode-friendly tagging without the deprecated API.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt#L25-L25"}

:::task-stub{title="Remove deprecated Thread.id call in TaggedSocketFactory"}
1. In `NetworkModule.kt` line 25, replace `Thread.currentThread().id.toInt()` in `tag()` with a fixed socket tag constant shared with the MainApplication fix.
2. Run `./gradlew compileDefaultDebugKotlin` and confirm no deprecation warning remains for NetworkModule.
3. Verify OkHttp requests still succeed in a debug build (any sync/login flow).
:::

### Opt in to FlowPreview for the ChatHistoryFragment search debounce
The build logs a preview-API warning at ChatHistoryFragment.kt line 107 because `debounce` is `@FlowPreview`; the call is intentional, so the fix is an explicit `@OptIn(FlowPreview::class)` on the enclosing function to make the acceptance visible and quiet CI.

:codex-file-citation[codex-file-citation]{line_range_start=106 line_range_end=109 path=app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L106-L109"}

:::task-stub{title="Annotate ChatHistoryFragment debounce with @OptIn(FlowPreview)"}
1. Add `@OptIn(kotlinx.coroutines.FlowPreview::class)` to the function containing the `searchBar.textChanges().debounce(300)` chain (line ~107).
2. Compile and confirm the preview-state warning for this file is gone.
3. Do not change the debounce behavior itself.
:::

### Opt in to FlowPreview for the resources search debounces
CollectionsFragment.kt line 68 and ResourcesFragment.kt line 256 both trigger the `@FlowPreview` preview-state warning for their `textChanges().debounce(...)` search pipelines. Annotating the two enclosing functions removes two of the ten recurring compiler warnings per CI run.

:codex-file-citation[codex-file-citation]{line_range_start=67 line_range_end=70 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt#L67-L70"}
:codex-file-citation[codex-file-citation]{line_range_start=254 line_range_end=258 path=app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L254-L258"}

:::task-stub{title="Annotate resources fragments' debounce chains with @OptIn(FlowPreview)"}
1. Add `@OptIn(kotlinx.coroutines.FlowPreview::class)` to the function wrapping the `etFilter.textChanges().debounce(300L)` chain in `CollectionsFragment.kt`.
2. Add the same annotation to `setupSearchTextListener()` in `ResourcesFragment.kt`.
3. Compile and confirm both preview-state warnings disappear from the Kotlin compile output.
:::

### Opt in to FlowPreview for the SurveyFragment search debounce
SurveyFragment.kt line 96 produces the same `@FlowPreview` preview-state warning for its search `debounce(300)`. One annotation on the enclosing function resolves it.

:codex-file-citation[codex-file-citation]{line_range_start=95 line_range_end=98 path=app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt#L95-L98"}

:::task-stub{title="Annotate SurveyFragment debounce with @OptIn(FlowPreview)"}
1. Add `@OptIn(kotlinx.coroutines.FlowPreview::class)` to the function containing `binding.layoutSearch.etSearch.textChanges().debounce(300)` (line ~96).
2. Compile and confirm the warning for `SurveyFragment.kt` is gone.
3. Leave the search behavior untouched.
:::

### Align Observer.onChanged parameter naming in ProcessUserDataActivity
The compiler warns twice (lines 209 and 238) that the `onChanged` override parameter is named differently from the supertype's `value`, which can break named-argument calls. Renaming the parameter — or replacing the anonymous `Observer` with a lambda — is a minimal, behavior-neutral cleanup.

:codex-file-citation[codex-file-citation]{line_range_start=208 line_range_end=212 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L208-L212"}
:codex-file-citation[codex-file-citation]{line_range_start=235 line_range_end=239 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L235-L239"}

:::task-stub{title="Rename onChanged parameter to value in ProcessUserDataActivity observers"}
1. In both anonymous `Observer<WorkInfo?>` implementations (lines ~209 and ~238), rename the `workInfo` parameter to `value` (updating body references), or convert each to a lambda observer that self-removes via a stored reference.
2. Compile and confirm both supertype-parameter-name warnings are gone.
3. Verify the WorkManager completion handling still removes the observer and branches on `SUCCEEDED` as before.
:::

### Remove the always-true null check in SyncTimeLogger
The compiler flags `Condition is always 'true'` at SyncTimeLogger.kt line 90: `alternativeAvailable` can only be true when `alternativeUrl` is non-null, so the trailing `alternativeUrl != null` in the `if` is dead logic that obscures the intent.

:codex-file-citation[codex-file-citation]{line_range_start=86 line_range_end=90 path=app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt#L86-L90"}

:::task-stub{title="Drop redundant alternativeUrl null check in SyncTimeLogger"}
1. Restructure the check at line ~90 so the smart cast carries: e.g. `if (!primaryAvailable && alternativeUrl != null && MainApplication.isServerReachable(alternativeUrl))`, removing the separate `alternativeAvailable` variable if it becomes unused.
2. Compile and confirm the always-true warning is gone.
3. Confirm fallback-URL switching behavior is unchanged (same branch taken for the same inputs).
:::

### Add ExperimentalCoroutinesApi opt-ins in flagged unit tests
The test compilation emits opt-in warnings for `Dispatchers.setMain`/`resetMain` in RealmUserTest.kt (lines 36, 51) and `UnconfinedTestDispatcher` in ChatRepositoryImplTest.kt line 49 and ChatRepositoryTest.kt line 28. Explicit `@OptIn(ExperimentalCoroutinesApi::class)` annotations quiet four recurring warnings without touching test logic.

:codex-file-citation[codex-file-citation]{line_range_start=35 line_range_end=36 path=app/src/test/java/org/ole/planet/myplanet/model/RealmUserTest.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/test/java/org/ole/planet/myplanet/model/RealmUserTest.kt#L35-L36"}
:codex-file-citation[codex-file-citation]{line_range_start=49 line_range_end=49 path=app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryImplTest.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryImplTest.kt#L49-L49"}
:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=28 path=app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryTest.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryTest.kt#L28-L28"}

:::task-stub{title="Add @OptIn(ExperimentalCoroutinesApi) to flagged test classes"}
1. Add `@OptIn(ExperimentalCoroutinesApi::class)` at class level in `RealmUserTest`, `ChatRepositoryImplTest`, and `ChatRepositoryTest` (matching the pattern other tests in the suite already use).
2. Run `./gradlew testDefaultDebugUnitTest` and confirm all tests still pass.
3. Confirm the four opt-in warnings no longer appear in the compile-test output.
:::

### Remove the redundant cast in DownloadRepositoryImplTest
The test compiler warns `No cast needed` at DownloadRepositoryImplTest.kt line 64, where the result is cast to `DownloadResult.Success` twice — once into `successResult` (which is then unused) and again inline in the assertion. Using the already-cast local removes both the warning and a dead variable.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=64 path=app/src/test/java/org/ole/planet/myplanet/repository/DownloadRepositoryImplTest.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/test/java/org/ole/planet/myplanet/repository/DownloadRepositoryImplTest.kt#L60-L64"}

:::task-stub{title="Use the existing successResult local instead of re-casting in DownloadRepositoryImplTest"}
1. Change line ~64 to assert on `successResult.body == mockResponseBody` instead of `(result as DownloadResult.Success).body`.
2. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DownloadRepositoryImplTest"` and confirm it passes.
3. Confirm the "No cast needed" warning is gone from the test compile output.
:::
