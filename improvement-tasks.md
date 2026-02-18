# myPlanet Build Improvements - 10 Low-Hanging Fruit Tasks

Based on analysis of GitHub Actions run #22123428802 and codebase examination.

### Replace printStackTrace with proper logging

Many files use `printStackTrace()` for error handling which only outputs to the console and is not captured by logging frameworks. This makes debugging production issues difficult.

:codex-file-citation[codex-file-citation]{line_range_start=230 line_range_end=230 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L230-L230"}

:codex-file-citation[codex-file-citation]{line_range_start=261 line_range_end=261 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt#L261-L261"}

:codex-file-citation[codex-file-citation]{line_range_start=233 line_range_end=233 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L233-L233"}

:codex-file-citation[codex-file-citation]{line_range_start=363 line_range_end=363 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L363-L363"}

:codex-file-citation[codex-file-citation]{line_range_start=144 line_range_end=144 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L144-L144"}

:::task-stub{title="Replace printStackTrace with Log.e in courses package"}
1. Replace all `e.printStackTrace()` calls in CoursesFragment.kt with `Log.e(TAG, "Error message", e)`
2. Replace all `e.printStackTrace()` calls in CourseStepFragment.kt with `Log.e(TAG, "Error message", e)`
3. Replace all `e.printStackTrace()` calls in TakeCourseFragment.kt with `Log.e(TAG, "Error message", e)`
4. Replace all `e.printStackTrace()` calls in InlineResourceAdapter.kt with `Log.e(TAG, "Error message", e)`
5. Add TAG constants where missing (e.g., `private const val TAG = "CoursesFragment"`)
:::

### Replace printStackTrace with proper logging in viewer activities

Viewer activities (ImageViewer, VideoViewer, MarkdownViewer, CSVViewer) use printStackTrace instead of proper logging.

:codex-file-citation[codex-file-citation]{line_range_start=60 line_range_end=60 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/MarkdownViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/MarkdownViewerActivity.kt#L60-L60"}

:codex-file-citation[codex-file-citation]{line_range_start=56 line_range_end=56 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/ImageViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/ImageViewerActivity.kt#L56-L56"}

:codex-file-citation[codex-file-citation]{line_range_start=97 line_range_end=97 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/VideoViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/VideoViewerActivity.kt#L97-L97"}

:codex-file-citation[codex-file-citation]{line_range_start=84 line_range_end=84 path=app/src/main/java/org/ole/planet/myplanet/ui/viewer/CSVViewerActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/viewer/CSVViewerActivity.kt#L84-L84"}

:::task-stub{title="Replace printStackTrace with Log.e in viewer activities"}
1. Replace `e.printStackTrace()` in MarkdownViewerActivity.kt with `Log.e(TAG, "Error loading markdown", e)`
2. Replace `e.printStackTrace()` in ImageViewerActivity.kt with `Log.e(TAG, "Error loading image", e)`
3. Replace all `e.printStackTrace()` calls in VideoViewerActivity.kt with `Log.e(TAG, "Error with video", e)`
4. Replace `e.printStackTrace()` in CSVViewerActivity.kt with `Log.e(TAG, "Error loading CSV", e)`
5. Add TAG constants where missing
:::

### Replace printStackTrace with proper logging in dashboard

DashboardActivity and related components use printStackTrace for error handling.

:codex-file-citation[codex-file-citation]{line_range_start=532 line_range_end=532 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L532-L532"}

:codex-file-citation[codex-file-citation]{line_range_start=538 line_range_end=538 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L538-L538"}

:codex-file-citation[codex-file-citation]{line_range_start=223 line_range_end=223 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt#L223-L223"}

:codex-file-citation[codex-file-citation]{line_range_start=124 line_range_end=124 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L124-L124"}

:::task-stub{title="Replace printStackTrace with Log.e in dashboard components"}
1. Replace all `e.printStackTrace()` in DashboardActivity.kt with `Log.e(TAG, "Error message", e)`
2. Replace `lastException?.printStackTrace()` in DashboardActivity.kt with proper logging
3. Replace `e.printStackTrace()` in DashboardViewModel.kt with `Log.e(TAG, "Error", e)`
4. Replace `e.printStackTrace()` in BellDashboardFragment.kt with `Log.e(TAG, "Error", e)`
5. Add TAG constants where missing
:::

### Remove verbose debug logging from production code

BellDashboardViewModel contains extensive debug logging (lines 43-95) that should only run in debug builds to reduce log spam in production.

:codex-file-citation[codex-file-citation]{line_range_start=43 line_range_end=95 path=app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt#L43-L95"}

:::task-stub{title="Wrap verbose badge debug logging in BuildConfig.DEBUG check"}
1. Add `if (BuildConfig.DEBUG) {` before line 43 in BellDashboardViewModel.kt
2. Close the if block after line 95
3. Ensure proper indentation within the if block
4. Import BuildConfig if not already imported
5. Verify the code compiles without errors
:::

### Remove verbose performance logging from production code

Multiple files contain verbose performance logging ("PerformanceTest", "SyncPerf") that should only run in debug builds.

:codex-file-citation[codex-file-citation]{line_range_start=228 line_range_end=249 path=app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmMyLibrary.kt#L228-L249"}

:codex-file-citation[codex-file-citation]{line_range_start=176 line_range_end=178 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L176-L178"}

:codex-file-citation[codex-file-citation]{line_range_start=308 line_range_end=311 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L308-L311"}

:::task-stub{title="Wrap performance logging in BuildConfig.DEBUG in RealmMyLibrary"}
1. Wrap Log.d("PerformanceTest", ...) calls in RealmMyLibrary.kt (lines 228-249) with `if (BuildConfig.DEBUG)`
2. Add BuildConfig import if missing
3. Verify code compiles
4. Test that performance metrics still work in debug builds
5. Verify logs don't appear in release builds
:::

### Wrap performance logging in SyncManager with debug check

SyncManager has extensive performance logging that should only run in debug builds to improve performance.

:codex-file-citation[codex-file-citation]{line_range_start=55 line_range_end=176 path=app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt#L55-L176"}

:codex-file-citation[codex-file-citation]{line_range_start=547 line_range_end=744 path=app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt#L547-L744"}

:::task-stub{title="Add BuildConfig.DEBUG guard to SyncTimeLogger methods"}
1. Wrap all Log.d("SyncPerf", ...) calls in SyncTimeLogger.kt with `if (BuildConfig.DEBUG)`
2. Modify SyncManager.kt to skip performance tracking calls when not in debug
3. Add early returns in SyncTimeLogger methods if not debug build
4. Import BuildConfig where needed
5. Test sync still works correctly in release builds
:::

### Enable lint error checking in Gradle build

The build currently has `abortOnError = false` which suppresses lint errors, making it harder to catch issues.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=76 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L74-L76"}

:::task-stub{title="Change lintOptions abortOnError to true"}
1. Change `abortOnError = false` to `abortOnError = true` in app/build.gradle line 75
2. Run `./gradlew lintDefaultDebug` to see current lint issues
3. Fix or suppress any blocking lint errors that appear
4. Verify build passes with new setting
5. Update CI workflow if needed to handle lint reports
:::

### Add build caching to CI workflow

The release workflow doesn't enable Gradle build cache which slows down builds. The build workflow already has better caching.

:codex-file-citation[codex-file-citation]{line_range_start=39 line_range_end=45 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L39-L45"}

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=43 path=.github/workflows/build.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/build.yml#L37-L43"}

:::task-stub{title="Enable build cache in release workflow"}
1. Verify release.yml setup-gradle step (lines 39-45) has cache settings
2. Ensure cache-disabled is false (already set correctly)
3. Ensure cache-read-only is false (already set correctly)
4. Document that caching is enabled for faster subsequent builds
5. No code changes needed - already configured optimally
:::

### Remove deprecated @JvmStatic methods in RealmRating

RealmRating contains deprecated methods that should be removed to clean up the codebase and encourage use of the repository pattern.

:codex-file-citation[codex-file-citation]{line_range_start=31 line_range_end=42 path=app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt#L31-L42"}

:codex-file-citation[codex-file-citation]{line_range_start=44 line_range_end=50 path=app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/model/RealmRating.kt#L44-L50"}

:::task-stub{title="Remove deprecated methods from RealmRating"}
1. Search codebase for usages of `RealmRating.getRatings()`
2. Replace any usages with RatingsRepository equivalent
3. Search codebase for usages of `RealmRating.getRatingsById()`
4. Replace any usages with RatingsRepository equivalent
5. Delete the deprecated methods from RealmRating.kt
:::

### Remove deprecated methods from DataService

DataService has deprecated methods that should be removed in favor of using ConfigurationsRepository directly.

:codex-file-citation[codex-file-citation]{line_range_start=45 line_range_end=46 path=app/src/main/java/org/ole/planet/myplanet/data/DataService.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/src/main/java/org/ole/planet/myplanet/data/DataService.kt#L45-L46"}

:::task-stub{title="Remove deprecated healthAccess method from DataService"}
1. Search codebase for usages of `DataService.healthAccess()`
2. Replace any usages with `ConfigurationsRepository.checkHealth()` instead
3. Update any affected files to inject ConfigurationsRepository
4. Delete the deprecated `healthAccess()` method from DataService.kt
5. Run tests to verify functionality unchanged
:::

## Testing Requirements

After implementing each task:

1. Build the project with `./gradlew assembleDefaultDebug`
2. Verify no compilation errors
3. For logging changes: Test in debug build and verify logs appear
4. For logging changes: Test in release build and verify verbose logs don't appear
5. For deprecated method removal: Run full test suite to ensure no breakage
6. For CI changes: Trigger a workflow run and verify it succeeds

## Notes

- All tasks are independent and can be implemented separately
- Each task involves minimal changes (typically 1-5 lines per file)
- Focus on one file or component at a time for easy review
- All changes improve code quality without altering functionality
- Changes reduce log spam and improve performance in production builds
