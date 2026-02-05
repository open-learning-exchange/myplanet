# Gradle Build Optimization Tasks

The following 10 tasks address warnings and deprecations from the release build log to improve build performance, reduce warnings, and prepare for future Android Gradle Plugin versions.

---

### Remove deprecated Gradle properties

The build log shows three deprecated properties that will be removed in AGP 10.0: `android.builtInKotlin=false`, `android.newDsl=false`, and `android.enableJetifier=true`. Removing these eliminates configuration warnings and aligns with modern AGP defaults.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=29 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L25-L29"}

:::task-stub{title="Remove deprecated Gradle properties"}
1. Open `gradle.properties` and remove line 25: `android.enableJetifier=true`
2. Remove line 28: `android.builtInKotlin=false`
3. Remove line 29: `android.newDsl=false`
4. Run `./gradlew clean build` to verify the build succeeds without these properties
5. If Jetifier removal causes issues, audit dependencies for AndroidX compatibility
:::

---

### Migrate to AGP built-in Kotlin support

AGP 9.0+ includes built-in Kotlin support, making the `org.jetbrains.kotlin.android` plugin redundant. Migrating reduces plugin overhead and simplifies the build configuration.

:codex-file-citation[codex-file-citation]{line_range_start=2 line_range_end=3 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L2-L3"}
:codex-file-citation[codex-file-citation]{line_range_start=98 line_range_end=99 path=gradle/libs.versions.toml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle/libs.versions.toml#L98-L99"}

:::task-stub{title="Migrate to AGP built-in Kotlin support"}
1. Remove `apply plugin: 'kotlin-android'` from `app/build.gradle` line 2
2. Remove the `kotlin-android` plugin definition from `gradle/libs.versions.toml` line 98
3. Update `build.gradle.kts` to remove `kotlin.gradle.plugin` classpath if no longer needed
4. Verify `kotlinOptions` block still works with built-in Kotlin support
5. Run full build and test suite to confirm Kotlin compilation works correctly
:::

---

### Add explicit annotation targets for Hilt qualifiers

Kotlin compiler warns that custom Hilt qualifiers like `@ApplicationContext`, `@AppPreferences`, and `@ApplicationScope` need explicit annotation targets. Adding `@param:` targets prevents future breaking changes when Kotlin changes default annotation behavior.

:codex-file-citation[codex-file-citation]{line_range_start=63 line_range_end=70 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L63-L70"}
:codex-file-citation[codex-file-citation]{line_range_start=27 line_range_end=27 path=app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt#L27-L27"}

:::task-stub{title="Add explicit annotation targets for Hilt qualifiers"}
1. Update SyncManager.kt constructor: change `@ApplicationContext` to `@param:ApplicationContext`
2. Update SyncManager.kt: change `@AppPreferences` to `@param:AppPreferences`
3. Update SyncManager.kt: change `@ApplicationScope` to `@param:ApplicationScope`
4. Apply same pattern to ChatRepositoryImpl.kt, ConfigurationsRepositoryImpl.kt, ResourcesRepositoryImpl.kt
5. Apply to SurveysRepositoryImpl.kt, TeamsRepositoryImpl.kt, UserRepositoryImpl.kt
6. Apply to AutoSyncWorker.kt, UploadManager.kt, TransactionSyncManager.kt, ImprovedSyncManager.kt
7. Apply to ChatApiService.kt and AuthSessionUpdater.kt
8. Run build to verify all annotation target warnings are resolved
:::

---

### Replace deprecated Realm realmInstance property

The `databaseService.realmInstance` property is deprecated. The recommended approach is to use `withRealm` or `withRealmAsync` for safer Realm instance management that properly handles threading and lifecycle.

:codex-file-citation[codex-file-citation]{line_range_start=90 line_range_end=90 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L90-L90"}
:codex-file-citation[codex-file-citation]{line_range_start=98 line_range_end=98 path=app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt#L98-L98"}
:codex-file-citation[codex-file-citation]{line_range_start=107 line_range_end=107 path=app/src/main/java/org/ole/planet/myplanet/services/sync/RealmConnectionPool.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/RealmConnectionPool.kt#L107-L107"}

:::task-stub{title="Replace deprecated Realm realmInstance property"}
1. Update BaseRecyclerFragment.kt line 90 to use `withRealm` block pattern
2. Update BaseResourceFragment.kt lines 98 and 365 to use `withRealm` or `withRealmAsync`
3. Update RealmConnectionPool.kt line 107 to use modern Realm instance acquisition
4. Update ThreadSafeRealmManager.kt lines 14 and 21 to use non-deprecated API
5. Update AddExaminationActivity.kt line 84 to use `withRealm` pattern
6. Ensure proper Realm instance lifecycle management in all updated locations
7. Test database operations after migration to verify correctness
:::

---

### Remove unnecessary safe calls on non-null receivers

Multiple files contain unnecessary safe call operators (`?.`) on values already confirmed to be non-null. These add runtime overhead and reduce code clarity. Removing them improves performance and readability.

:codex-file-citation[codex-file-citation]{line_range_start=307 line_range_end=346 path=app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt#L307-L346"}
:codex-file-citation[codex-file-citation]{line_range_start=470 line_range_end=474 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L470-L474"}

:::task-stub{title="Remove unnecessary safe calls on non-null receivers"}
1. In UploadToShelfService.kt, fix lines 133, 146, 176, 196, 307, 345, 388, 432, 488
2. In UserRepositoryImpl.kt, fix lines 470, 471, 474 where RealmMyHealth is non-null
3. In SubmissionsRepositoryImpl.kt, fix lines 476, 477, 479 where RealmStepExam is non-null
4. In RatingsRepositoryImpl.kt line 51, remove safe call on non-null Double
5. In FileUploader.kt line 46, remove safe call on non-null Retrofit
6. In ConfigurationsRepositoryImpl.kt line 167, remove safe call on non-null String
7. In ProcessUserDataActivity.kt line 346, remove safe call on non-null Retrofit
8. Fix remaining unnecessary safe calls in BaseTeamFragment, ChatShareTargetAdapter, PlanFragment
:::

---

### Update deprecated Android platform APIs

Several Android platform APIs are deprecated and should be updated to their modern replacements. This includes `stopForeground(Boolean)`, `getSerializable()`, WiFi management APIs, and system bar color APIs.

:codex-file-citation[codex-file-citation]{line_range_start=356 line_range_end=356 path=app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt#L356-L356"}
:codex-file-citation[codex-file-citation]{line_range_start=66 line_range_end=66 path=app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/base/BaseRecyclerFragment.kt#L66-L66"}
:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=41 path=app/src/main/java/org/ole/planet/myplanet/utils/EdgeToEdgeUtils.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/EdgeToEdgeUtils.kt#L40-L41"}

:::task-stub{title="Update deprecated Android platform APIs"}
1. In DownloadService.kt line 356, replace `stopForeground(true)` with `stopForeground(Service.STOP_FOREGROUND_REMOVE)`
2. In BaseRecyclerFragment.kt line 66, use `getSerializable(key, Class<T>)` with type parameter
3. In VoicesAdapter.kt line 334, replace `adapterPosition` with `bindingAdapterPosition`
4. In DashboardActivity.kt line 681, replace `MenuItemCompat.getActionView()` with `menuItem.actionView`
5. In EdgeToEdgeUtils.kt lines 40-41, use WindowCompat APIs for system bar colors
6. In DashboardElementActivity.kt, update WiFi APIs to use modern NetworkCallback approach
7. In BaseResourceFragment.kt line 156, redirect users to WiFi settings instead of `setWifiEnabled()`
:::

---

### Add @Deprecated annotations to deprecated method overrides

When overriding deprecated methods, the override should also be marked `@Deprecated` to suppress warnings and indicate the deprecation chain. This applies to WebView callbacks and repository methods.

:codex-file-citation[codex-file-citation]{line_range_start=184 line_range_end=185 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt#L184-L185"}
:codex-file-citation[codex-file-citation]{line_range_start=272 line_range_end=274 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt#L272-L274"}
:codex-file-citation[codex-file-citation]{line_range_start=262 line_range_end=262 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L262-L262"}

:::task-stub{title="Add @Deprecated annotations to deprecated method overrides"}
1. In WebViewActivity.kt line 184, add `@Deprecated("Deprecated in WebViewClient")` before `onReceivedError`
2. In WebViewActivity.kt line 272, add `@Deprecated("Deprecated in WebChromeClient")` before `onConsoleMessage`
3. In TeamsRepositoryImpl.kt line 262, add `@Deprecated` annotation to `getTeamTransactions` override
4. Add appropriate `@Suppress("DEPRECATION")` if the deprecated functionality is still needed
5. Consider implementing the non-deprecated alternatives where available
:::

---

### Add @Suppress annotations for unchecked casts

Several locations perform unchecked casts that generate compiler warnings. Adding explicit `@Suppress("UNCHECKED_CAST")` annotations documents that the cast is intentional and safe in context.

:codex-file-citation[codex-file-citation]{line_range_start=123 line_range_end=123 path=app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt#L123-L123"}
:codex-file-citation[codex-file-citation]{line_range_start=85 line_range_end=85 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L85-L85"}
:codex-file-citation[codex-file-citation]{line_range_start=326 line_range_end=326 path=app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt#L326-L326"}

:::task-stub{title="Add @Suppress annotations for unchecked casts"}
1. In NotificationsFragment.kt line 123, add `@Suppress("UNCHECKED_CAST")` before the Triple cast
2. In RealtimeSyncMixin.kt line 85, add `@Suppress("UNCHECKED_CAST")` before the ListAdapter cast
3. In VoicesFragment.kt line 326, add `@Suppress("UNCHECKED_CAST")` before the ArrayAdapter cast
4. Consider refactoring to avoid casts where possible using sealed classes or type-safe alternatives
5. Document why each cast is safe with inline comments if not obvious
:::

---

### Add @OptIn for experimental coroutines APIs

The `mapLatest` function used in SubmissionViewModel requires opt-in for `ExperimentalCoroutinesApi`. Adding the annotation explicitly acknowledges the experimental status and prevents compiler warnings.

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=44 path=app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt#L44-L44"}

:::task-stub{title="Add @OptIn for experimental coroutines APIs"}
1. Add `@OptIn(ExperimentalCoroutinesApi::class)` annotation to SubmissionViewModel class
2. Import `kotlinx.coroutines.ExperimentalCoroutinesApi`
3. Alternatively, add opt-in at file level with `@file:OptIn(ExperimentalCoroutinesApi::class)`
4. Review other usages of experimental coroutines APIs in the codebase
5. Consider whether to enable project-wide opt-in in build.gradle kotlinOptions
:::

---

### Enable remote Gradle build cache

The build log shows "remote cache disabled (no URL)" indicating builds are not leveraging remote caching. Enabling remote build cache can significantly reduce CI build times by reusing task outputs across builds.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=15 path=settings.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/settings.gradle#L1-L15"}

:::task-stub{title="Enable remote Gradle build cache"}
1. Set up a Gradle remote build cache server (Gradle Enterprise or custom HTTP cache)
2. Configure `GRADLE_BUILD_CACHE_URL` environment variable in CI workflows
3. Add cache configuration to `settings.gradle` for remote cache support
4. Configure read-only cache access for PR builds, read-write for main branch
5. Add build cache credentials to GitHub Actions secrets
6. Test cache hit rates and measure build time improvements
7. Document cache configuration in project README or CI documentation
:::

---

## Summary

| Task | Category | Impact |
|------|----------|--------|
| Remove deprecated Gradle properties | Configuration | Eliminates 3 deprecation warnings |
| Migrate to AGP built-in Kotlin | Configuration | Simplifies build, removes plugin overhead |
| Add annotation targets for Hilt qualifiers | Code Quality | Fixes ~25 annotation warnings |
| Replace deprecated Realm realmInstance | Code Quality | Fixes 7 deprecation warnings |
| Remove unnecessary safe calls | Code Quality | Fixes ~20 unnecessary safe call warnings |
| Update deprecated Android APIs | Code Quality | Fixes ~10 platform deprecation warnings |
| Add @Deprecated to overrides | Code Quality | Fixes 3 missing annotation warnings |
| Add @Suppress for unchecked casts | Code Quality | Fixes 3 unchecked cast warnings |
| Add @OptIn for experimental APIs | Code Quality | Fixes 1 experimental API warning |
| Enable remote build cache | Performance | Reduces CI build times significantly |

**Total warnings addressed: ~75+ compiler warnings**
