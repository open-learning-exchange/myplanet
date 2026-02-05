# Release Build Workflow Improvement Tasks

Based on analysis of GitHub Actions release build workflow run [#21719796577](https://github.com/open-learning-exchange/myplanet/actions/runs/21719796577/job/62645929847), the following tasks are recommended to improve the build process, performance, and maintainability.

---

### Enable Gradle Configuration Cache

The build logs show a suggestion to enable configuration cache which can significantly speed up build times by caching the result of the configuration phase. This is particularly beneficial for CI/CD pipelines where builds run frequently.

:codex-file-citation[codex-file-citation]{line_range_start=19 line_range_end=19 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L19-L19"}

:::task-stub{title="Enable and optimize Gradle configuration cache"}
1. Verify configuration cache is enabled in gradle.properties (org.gradle.configuration-cache=true)
2. Run local builds to identify any configuration cache compatibility issues
3. Fix any incompatible build logic or plugins that prevent caching
4. Add cache validation to CI workflow with --configuration-cache-problems=warn
5. Monitor build time improvements and document the performance gains
:::

---

### Increase Gradle JVM Heap Size for CI Builds

Build logs indicate cache misses and memory pressure warnings: "Performance may suffer from in-memory cache misses. Increase max heap size of Gradle build process to reduce cache misses." The current max heap is only 512MB in the daemon config, but gradle.properties has 4GB.

:codex-file-citation[codex-file-citation]{line_range_start=12 line_range_end=12 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/master/gradle.properties#L12-L12"}
:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=45 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L40-L45"}

:::task-stub{title="Optimize Gradle daemon memory settings for CI"}
1. Update gradle/actions/setup-gradle configuration to respect gradle.properties memory settings
2. Set GRADLE_OPTS environment variable in workflow to ensure proper heap allocation
3. Consider increasing max heap from 4GB to 6GB for release builds specifically
4. Add memory monitoring to identify optimal settings
5. Test build performance with new memory configuration
:::

---

### Implement Build Scan Publishing

Build scans provide detailed insights into build performance, dependencies, and potential optimizations. The workflow shows Gradle 9.3.1 is available but build scans are not being published.

:codex-file-citation[codex-file-citation]{line_range_start=40 line_range_end=45 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L40-L45"}

:::task-stub{title="Enable Gradle build scans for CI workflows"}
1. Add --scan flag to gradle commands in release.yml workflow
2. Configure build scan publishing with appropriate terms of service acceptance
3. Set up automatic build scan URL posting to pull requests
4. Document how to access and interpret build scan data
5. Create runbook for using build scans to diagnose slow builds
:::

---

### Add Build Time Tracking and Reporting

The workflow lacks explicit timing information for individual steps, making it difficult to identify bottlenecks. Most time appears spent in the 16:37 minute, suggesting the build step takes significant time.

:codex-file-citation[codex-file-citation]{line_range_start=54 line_range_end=63 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L54-L63"}

:::task-stub{title="Add detailed build time tracking and reporting"}
1. Add step-level timing using GitHub Actions time tracking annotations
2. Implement custom timing for assemble and bundle tasks separately
3. Add timing comparison against previous builds in PR comments
4. Create dashboard or badge showing average build times
5. Set up alerts for builds that exceed expected duration thresholds
:::

---

### Optimize Matrix Build Strategy

The workflow builds both 'default' and 'lite' flavors in parallel using a matrix strategy, but there's no optimization for the redundant work being done. Both flavors share most compilation work.

:codex-file-citation[codex-file-citation]{line_range_start=13 line_range_end=16 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L13-L16"}
:codex-file-citation[codex-file-citation]{line_range_start=33 line_range_end=43 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L33-L43"}

:::task-stub{title="Optimize matrix build to reduce redundant compilation"}
1. Analyze shared vs. flavor-specific build tasks and compilation
2. Consider building both flavors in a single job with proper task dependencies
3. Implement build cache sharing between matrix jobs if kept separate
4. Add remote build cache using GitHub Actions cache or Gradle Enterprise
5. Measure and document time savings from optimization
:::

---

### Enhance ProGuard/R8 Configuration

The release build has minification disabled which increases APK size and reduces code protection. This should be enabled for release builds with proper keep rules.

:codex-file-citation[codex-file-citation]{line_range_start=22 line_range_end=31 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L22-L31"}

:::task-stub{title="Enable and optimize R8 minification for release builds"}
1. Enable minifyEnabled = true for release build type
2. Create comprehensive ProGuard rules for all libraries and Realm models
3. Test release build thoroughly to ensure no runtime crashes
4. Add mapping file upload to Firebase Crashlytics or similar service
5. Document APK size reduction and any performance improvements
6. Set up automated testing of minified builds in CI
:::

---

### Add Automated APK/AAB Size Tracking

There's no tracking of the APK or AAB file sizes over time, which is important for monitoring application bloat and regressions.

:codex-file-citation[codex-file-citation]{line_range_start=74 line_range_end=82 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L74-L82"}

:::task-stub{title="Implement APK and AAB size tracking"}
1. Extract APK and AAB file sizes in the workflow
2. Store size metrics in GitHub Actions artifacts or external database
3. Add size comparison against previous releases in PR comments
4. Create visualization of size trends over time
5. Set up alerts for unexpected size increases above threshold
6. Document acceptable size limits for each build flavor
:::

---

### Implement Dependency Update Automation

The project has dependabot configured but there's no automated testing of dependency updates or grouping of related updates to reduce PR noise.

:codex-file-citation[codex-file-citation]{line_range_start=175 line_range_end=200 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L175-L200"}

:::task-stub{title="Enhance dependency update workflow"}
1. Configure dependabot grouping for related dependencies (AndroidX, Kotlin, Hilt)
2. Add automated build and test workflow for dependabot PRs
3. Implement security scanning for dependency vulnerabilities
4. Create weekly dependency update report
5. Set up automated merge for patch version updates that pass tests
6. Document dependency update policy and procedures
:::

---

### Add Lint Report Generation and Publishing

The build has lintOptions configured but there's no evidence of lint reports being generated or published in the workflow, making it hard to track code quality issues.

:codex-file-citation[codex-file-citation]{line_range_start=73 line_range_end=75 path=app/build.gradle git_url="https://github.com/open-learning-exchange/myplanet/blob/master/app/build.gradle#L73-L75"}

:::task-stub{title="Add Android Lint report generation and publishing"}
1. Enable lint report generation in the workflow (HTML and XML formats)
2. Upload lint reports as workflow artifacts
3. Add lint baseline file to track existing issues
4. Implement lint checks as required status check for PRs
5. Add lint issue summary to PR comments
6. Configure Danger or similar tool to highlight new lint warnings
:::

---

### Improve Signing Key Security

The signing process uses secrets correctly but could benefit from additional security measures like key rotation and audit logging.

:codex-file-citation[codex-file-citation]{line_range_start=65 line_range_end=72 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/master/.github/workflows/release.yml#L65-L72"}

:::task-stub{title="Enhance release signing security and auditability"}
1. Implement signing key rotation policy and documentation
2. Add audit logging for all signing operations
3. Set up alerts for unauthorized signing attempts
4. Consider using Google Play App Signing for additional security
5. Document key backup and recovery procedures
6. Review and limit repository secret access to essential workflows only
:::

---

## Summary

These 10 tasks focus on:
- **Performance**: Build speed optimization through caching and memory tuning
- **Observability**: Better tracking of build times, sizes, and quality metrics
- **Security**: Enhanced signing and dependency management
- **Maintainability**: Automated testing and reporting for better workflow reliability

Each task includes specific actionable steps and references to relevant code sections for easy implementation.
