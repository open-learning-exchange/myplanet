# Gradle Build Scans

This project is configured to publish [Gradle Build Scans](https://scans.gradle.com/) for CI/CD workflows and local builds.

## Accessing Build Scans

### GitHub Actions
When a build runs in GitHub Actions, a link to the build scan is generated at the end of the build.
*   **Job Summary**: The `setup-gradle` action adds a link to the build scan in the Job Summary page.
*   **Pull Requests**: For PR builds, a comment containing the build scan link is automatically posted to the Pull Request.
*   **Console Output**: You can also find the link in the console output of the "Build" step (look for "Publishing build scan...").

### Local Builds
To generate a build scan locally, run your Gradle command with the `--scan` flag:
```bash
./gradlew assembleDebug --scan
```
You will be presented with a link to the build scan in the terminal.

## Interpreting Build Scan Data

A Build Scan provides comprehensive insights into your build:
*   **Summary**: Overview of the build outcome, duration, and environment.
*   **Performance**: Detailed breakdown of task execution times, dependency resolution, and configuration time. Use this to identify bottlenecks.
*   **Tests**: Results of all tests, including failures and flakiness.
*   **Dependencies**: Graph of all dependencies and their versions.
*   **Console Log**: Searchable console output.

## Runbook: Diagnosing Slow Builds

If you notice CI builds or local builds are running slowly, use the Build Scan to diagnose the issue:

1.  **Open the Build Scan**: Click the link provided in the CI output or PR comment.
2.  **Check the Performance Tab**:
    *   **Task Execution**: Sort tasks by duration. Are there specific tasks taking disproportionately long?
    *   **Build Cache**: Check if tasks are being cached ("FROM-CACHE") or executed ("EXECUTED"). If cache misses are high, investigate why inputs are changing.
    *   **Configuration Time**: If configuration time is high, check which scripts or plugins are slow.
3.  **Compare Scans**:
    *   You can compare two build scans (e.g., a fast build vs. a slow build) to see differences in dependencies, task execution, and environment.
    *   Use the "Compare" feature in the build scan UI.
4.  **Network Activity**: Check if dependency downloads are slow.
5.  **Parallel Execution**: Ensure that parallel execution is enabled (check "Switches" in the Summary).

## Configuration

Build scans are configured in `settings.gradle` using the `com.gradle.develocity` plugin.
Terms of service are automatically accepted.
