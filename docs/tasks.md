Date: 2024-05-19
Base Commit: current
Open PRs Checked: could not check

---
### 1. replace Date().time with System.currentTimeMillis() in Personal model (roadmap 7+9)
context: Personal.kt:39 instantiates a full java.util.Date object just to read its time property, which is inefficient. System.currentTimeMillis() avoids this allocation.
this advances roadmap 9 (kotlin multiplatform) by removing a java-specific import (`java.util.Date`), making the core logic more platform-agnostic and easier to migrate to kotlinx-datetime in the future.
this is a necessary pre-requisite for multiplatform compatibility.
files: app/src/main/java/org/ole/planet/myplanet/model/Personal.kt. do NOT touch UserEntity or other models.
steps:
  1. Locate line 39 in Personal.kt
  2. Replace `Date().time` with `System.currentTimeMillis()`
  3. Check imports at the top of the file
  4. Remove `import java.util.Date` if it is no longer used
acceptance: ./gradlew testDefaultDebugUnitTest green; personal data still serializes with correct timestamp
size budget: ~1 changed line, 1 file
out of scope: no changes to other models. no complex refactoring.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 2. optimize non-null item count in TeamsVoicesFragment (roadmap 7)
context: TeamsVoicesFragment.kt:262 and 273 allocate intermediate lists via `.filterNotNull()` just to query their `.size`. `count { it != null }` (or similar) avoids these list allocations.
this is a small but valuable improvement for memory efficiency, directly impacting roadmap 7 (optimize remaining performance hotspots).
reducing intermediate collection allocations helps maintain smooth UI performance.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt (lines 262, 273). do NOT touch BaseVoicesFragment.
steps:
  1. Find line 262: `realmNewsList?.filterNotNull()?.size ?: 0`
  2. Replace it with `realmNewsList?.count { it != null } ?: 0`
  3. Find line 273: `it.filterNotNull().size`
  4. Replace it with `it.count { it != null }`
acceptance: ./gradlew testDefaultDebugUnitTest green; discussions count still displays correctly
size budget: ~2 changed lines, 1 file
out of scope: no changes to BaseVoicesFragment or other fragments.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 3. merge filter and map operations in SubmissionsRepositoryImpl (roadmap 7+9)
context: SubmissionsRepositoryImpl.kt:213 calls `.filter { ... }.map { ... }` which creates an intermediate list. Kotlin provides `.mapNotNull` or single-pass collection options to avoid this.
this moves roadmap 9 forward by ensuring we rely on idiomatic Kotlin standard library functions (mapNotNull) which translate cleanly in multiplatform targets.
it also improves repository performance under roadmap 7.
files: app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt (line 213). do NOT touch other repositories.
steps:
  1. Locate the `.filter { it !in existingUserIds }.map { userId ->` on line 213
  2. Replace `.filter` with `.mapNotNull`
  3. Adjust the lambda to `if (it !in existingUserIds) ... else null`
  4. Verify no compilation errors in Android Studio
acceptance: ./gradlew testDefaultDebugUnitTest green; submissions still map correctly
size budget: ~2 changed lines, 1 file
out of scope: no other repository changes. do not touch DB schema.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 4. explicitly log swallowed exception in CrashLogStore (roadmap 8)
context: CrashLogStore.kt:45 and 57 call `e.printStackTrace()` which pollutes test output. These should use `Log.e` or have an explicit `// Ignored` comment if truly ignorable.
this improves code health (roadmap 8) by moving away from raw stack traces and utilizing the standard Android logging framework.
it also makes debugging much cleaner for developers.
files: app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt (lines 45, 57). do NOT touch other utilities.
steps:
  1. Locate the `e.printStackTrace()` calls on lines 45 and 57
  2. Replace them with `Log.e(TAG, "Failed to write/read crash log", e)`
  3. Ensure a `TAG` constant exists in the class or companion object
  4. If `TAG` is missing, define it as `"CrashLogStore"`
acceptance: ./gradlew testDefaultDebugUnitTest green; crash logs errors no longer pollute stdout with stack traces
size budget: ~4 changed lines, 1 file
out of scope: no changes to exception catching logic. no changes to log reporting servers.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 5. replace Date().time with System.currentTimeMillis in FeedbackRepositoryImpl (roadmap 7+9)
context: FeedbackRepositoryImpl.kt:54 and 102 use `Date().time`, causing unnecessary allocations of Date objects during feedback processing.
this advances roadmap 9 (kotlin multiplatform) by removing a java-specific import (`java.util.Date`), making the core logic platform-agnostic.
this is a necessary pre-requisite for multiplatform compatibility.
files: app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt. do NOT touch other repository implementations.
steps:
  1. Locate `Date().time` usages on lines 54 and 102
  2. Replace them with `System.currentTimeMillis()`
  3. Clean up the `import java.util.Date` import statement if unused
  4. Verify syntax
acceptance: ./gradlew testDefaultDebugUnitTest green; feedback timestamps remain correct
size budget: ~2 changed lines, 1 file
out of scope: no changes to other repositories. do not change the UI.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 6. combine filterNotNull and map in ResourcesAdapter (roadmap 7)
context: ResourcesAdapter.kt:167 calls `libraryList.filterNotNull().map { ... }`, allocating an intermediate list. This should be merged into a single `mapNotNull` pass.
this improves performance (roadmap 7) specifically in a RecyclerView Adapter, where minimizing allocations is critical for smooth scrolling.
it prevents GC pauses when filtering large resource libraries.
files: app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapter.kt (line 167). do NOT touch ResourceDetailFragment.
steps:
  1. Locate line 167: `.filterNotNull().map {`
  2. Replace this chain with `.mapNotNull {`
  3. Ensure the lambda still returns the expected mapped type
  4. Rebuild to verify adapter compilation
acceptance: ./gradlew testDefaultDebugUnitTest green; resources list still populates correctly
size budget: ~1 changed line, 1 file
out of scope: no layout changes. do not modify view binding logic.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 7. avoid Date allocation for photo filenames in CameraUtils (roadmap 7)
context: CameraUtils.kt:106 generates photo filenames using `"${Date().time}.jpg"`, unnecessarily allocating a Date object.
this reduces object churn during camera operations (roadmap 7).
it ensures the utility function remains fast and allocation-free.
files: app/src/main/java/org/ole/planet/myplanet/utils/CameraUtils.kt. do NOT touch FileUtils.kt.
steps:
  1. Find line 106 in `CameraUtils.kt`
  2. Change `"${Date().time}.jpg"` to `"${System.currentTimeMillis()}.jpg"`
  3. Remove the `java.util.Date` import if it is not used elsewhere in the file
  4. Check the file for compilation errors
acceptance: ./gradlew testDefaultDebugUnitTest green; camera captures still generate valid timestamped filenames
size budget: ~1 changed line, 1 file
out of scope: no changes to file saving logic. no UI modifications.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 8. replace raw printStackTrace with structured logging in DownloadUtils (roadmap 8)
context: DownloadUtils.kt:181 prints stack traces directly to stderr, polluting logs. It should use `Log.e()`.
this improves overall code health (roadmap 8) and ensures our logcat output is useful rather than noisy.
proper tagged logging is essential for diagnosing production issues.
files: app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt. do NOT touch DownloadService.
steps:
  1. Locate `e.printStackTrace()` on line 181
  2. Change it to `Log.e(TAG, "Download failed", e)`
  3. Verify that `TAG` is defined in `DownloadUtils`
  4. If not, add `private const val TAG = "DownloadUtils"` to the file
acceptance: ./gradlew testDefaultDebugUnitTest green; download errors are logged via Android logger
size budget: ~2 changed lines, 1 file
out of scope: no changes to download queue logic or network calls.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 9. replace Date allocations with System.currentTimeMillis in ActivitiesRepositoryImpl (roadmap 7+9)
context: ActivitiesRepositoryImpl.kt (lines 100, 128, 135, 164, 256) instantiates `Date()` objects just to extract their `.time` property for timestamps.
this advances roadmap 9 (kotlin multiplatform) by excising `java.util.Date`, preparing the repository layer for compilation outside the JVM.
it also improves runtime performance by stopping pointless object allocations (roadmap 7).
files: app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt. do NOT touch TeamsRepositoryImpl.
steps:
  1. Use search-and-replace to find all 5 occurrences of `Date().time` in the file
  2. Replace them entirely with `System.currentTimeMillis()`
  3. Check the imports at the top of the file
  4. Delete `import java.util.Date` if it's no longer necessary
acceptance: ./gradlew testDefaultDebugUnitTest green; activity login and logout timestamps remain correct
size budget: ~6 changed lines, 1 file
out of scope: no DAO modifications. no schema changes.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.

---
### 10. use structured logging for SyncTimeLogger exceptions (roadmap 8)
context: SyncTimeLogger.kt:120 calls `e.printStackTrace()` when sync time parsing fails, which pollutes test environments. It should use Android's `Log` class.
fixing this improves code health (roadmap 8) by adopting proper Android logging standards.
it keeps unit test and terminal output clean of noisy stacktraces.
files: app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt. do NOT touch SyncManager.
steps:
  1. Find line 120 containing `e.printStackTrace()`
  2. Replace it with `Log.e(TAG, "Failed to parse sync time", e)`
  3. Ensure a `TAG` constant exists or define it
  4. Verify syntax and imports for `android.util.Log`
acceptance: ./gradlew testDefaultDebugUnitTest green; malformed sync times no longer produce un-tagged stderr output
size budget: ~2 changed lines, 1 file
out of scope: no changes to sync time formatting logic or date parsing patterns.
note: ensure all CI checks pass before merging this specific task.
note: this task is atomic and independently verifiable.
note: no extra functionality should be added.
