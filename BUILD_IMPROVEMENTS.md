# Release Build Improvements

This document outlines 10 improvement tasks for the myPlanet release build workflow based on analysis of the release build logs from workflow run 21639284564.

---

### Enable ProGuard/R8 code shrinking for release builds

ProGuard is currently disabled for release builds, which results in larger APK/AAB sizes and potentially exposes unnecessary code. Enabling minification would reduce app size, improve performance, and enhance security by obfuscating code.

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=25 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L22-L25"}

:::task-stub{title="Enable ProGuard/R8 for release builds"}
1. Change `minifyEnabled = false` to `minifyEnabled = true` in release buildType
2. Add proper ProGuard rules in proguard-rules.pro for Realm, Retrofit, Hilt, and other libraries
3. Test release build thoroughly to ensure no runtime crashes from over-aggressive shrinking
4. Configure ProGuard to keep debugging info with `-keepattributes SourceFile,LineNumberTable`
5. Measure APK size reduction and verify all features work correctly
:::

---

### Add resource shrinking to reduce APK size

Resource shrinking removes unused resources from the final APK, which can significantly reduce app size. This is especially valuable for an offline-first educational app that may be installed on devices with limited storage.

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=25 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L22-L25"}

:::task-stub{title="Enable resource shrinking"}
1. Add `shrinkResources = true` in the release buildType (requires minifyEnabled = true)
2. Review res/ directory to identify unused resources that will be removed
3. Test the release build to ensure all necessary resources are preserved
4. Add keep rules for resources that are accessed dynamically via reflection
5. Document the size savings in release notes
:::

---

### Implement build performance optimization with configuration cache

Gradle configuration cache can significantly speed up builds by caching the result of the configuration phase. The current gradle.properties enables it (org.gradle.configuration-cache=true), but build logs show "Consider enabling configuration cache" messages indicating it may not be fully utilized or could benefit from additional optimization.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=19 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L19-L19"}

:::task-stub{title="Optimize configuration cache usage"}
1. Verify configuration cache is working properly by checking build logs for cache hits
2. Fix any configuration cache incompatibilities in build scripts
3. Add `org.gradle.configuration-cache.problems=warn` to identify issues
4. Update custom Gradle tasks to be configuration-cache compatible
5. Measure build time improvements in CI/CD pipeline
:::

---

### Add build caching between workflow runs

The workflow currently uses Gradle setup but doesn't explicitly configure optimal caching strategies. Improving cache configuration could reduce build times from ~4 minutes to under 2 minutes.

:codex-file-citation[codex-file-citation]{line_range_start=37 line_range_end=46 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L37-L46"}

:::task-stub{title="Optimize GitHub Actions caching strategy"}
1. Add explicit caching for Gradle wrapper, dependencies, and build cache
2. Configure cache-read-only appropriately for release builds
3. Add cache for Android SDK components
4. Implement cache key versioning strategy based on dependencies
5. Monitor cache hit rates and adjust strategy accordingly
:::

---

### Parallelize APK and AAB builds

Currently, the workflow builds both APK and AAB sequentially in the same Gradle command. Separating these into parallel steps or using Gradle's parallel execution more effectively could reduce build time.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=58 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L54-L58"}

:::task-stub{title="Parallelize build tasks"}
1. Evaluate splitting APK and AAB generation into separate Gradle invocations
2. Configure Gradle parallel execution with optimal worker count
3. Use `--parallel` flag with appropriate `--max-workers` setting
4. Profile build to identify bottlenecks that can be parallelized
5. Test that parallelization doesn't cause build reproducibility issues
:::

---

### Upgrade to latest Gradle and AGP versions

While the project uses relatively recent versions (Gradle 9.3.1, AGP 9.0.0), staying on the latest stable versions ensures access to latest build performance improvements and bug fixes.

:codex-file-citation[codex-file-citation]{line_range_start=1 line_range_end=4 path=gradle/libs.versions.toml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle/libs.versions.toml#L1-L4"}

:::task-stub{title="Maintain up-to-date build tools"}
1. Set up automated monitoring for new Gradle and AGP releases
2. Create quarterly maintenance tasks to evaluate and upgrade build tools
3. Test new versions in a separate branch before merging
4. Document any breaking changes or required build script updates
5. Update CLAUDE.md with current version requirements
:::

---

### Reduce workflow run time with selective builds

The release workflow always builds both 'default' and 'lite' flavors even when changes may only affect one. Implementing smarter build triggers could save CI resources.

:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=16 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L13-L16"}

:::task-stub{title="Implement conditional flavor builds"}
1. Analyze commit messages or changed files to determine which flavors need building
2. Add workflow conditions to skip unnecessary flavor builds
3. Create path filters for lite-specific vs default-specific changes
4. Ensure full builds still run for version bumps and releases
5. Document the conditional build logic for contributors
:::

---

### Add build scan integration for build analytics

Gradle Build Scans provide detailed insights into build performance, dependency resolution, and task execution. Integrating this would help identify optimization opportunities.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=58 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L54-L58"}

:::task-stub{title="Enable Gradle Build Scans"}
1. Add `--scan` flag to Gradle build commands in workflow
2. Configure build scan publishing settings in build scripts
3. Set up build scan links to be posted to PR comments
4. Create a process for reviewing build scans for optimization opportunities
5. Train team on interpreting build scan data
:::

---

### Implement incremental builds for Kotlin compilation

The project uses kapt which can slow down builds. Migrating to KSP where possible and optimizing kapt configuration can improve build performance significantly.

:codex-file-citation[codex-file-citation]{line_range_start=225 line_range_end=228 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L225-L228"}

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=22 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L22-L22"}

:::task-stub{title="Optimize annotation processing"}
1. Audit all kapt dependencies to identify candidates for KSP migration
2. Migrate Glide from kapt to KSP (already has KSP support)
3. Ensure `kapt.incremental.apt=true` is working effectively
4. Add `kapt.use.worker.api=true` for parallel annotation processing
5. Measure build time improvements from kapt optimizations
:::

---

### Add automated APK size tracking and reporting

Monitoring APK size over time helps catch unintended bloat and ensures the app remains installable on low-end devices.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=82 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L74-L82"}

:::task-stub{title="Implement APK size monitoring"}
1. Add workflow step to extract and log APK/AAB sizes
2. Create a GitHub Action to compare size with previous releases
3. Post size comparison as PR comment for review
4. Set up alerts for significant size increases (>5%)
5. Track size metrics over time in a dashboard or file
:::

---

## Testing Section

### Validation steps for improvements

After implementing these improvements, validate them by:

1. Running a full release build locally and in CI
2. Comparing build times before and after optimizations
3. Verifying APK functionality on multiple devices
4. Checking that all features work correctly with ProGuard enabled
5. Ensuring build cache is working effectively
6. Monitoring for any build failures or regressions
7. Measuring and documenting size reductions
8. Confirming Play Store upload still works
9. Testing both default and lite flavors thoroughly
10. Validating that incremental builds work correctly
