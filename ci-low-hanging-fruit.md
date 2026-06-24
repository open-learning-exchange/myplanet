### Replace deprecated Thread.id used for TrafficStats tagging

The latest master CI build logs flag `'val id: Long' is deprecated. Deprecated in Java.` at two TrafficStats call sites that read `Thread.currentThread().id`. `Thread.threadId()` is the official replacement but only exists on API 36+, while minSdk is 26, so use `android.os.Process.myTid()` (available since API 1, returns an `Int`) instead.

:codex-file-citation[codex-file-citation]{line_range_start=198 line_range_end=198 path=app/src/main/java/org/ole/planet/myplanet/MainApplication.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/MainApplication.kt#L198-L198"}
:codex-file-citation[codex-file-citation]{line_range_start=26 line_range_end=26 path=app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt#L26-L26"}

:::task-stub{title="Replace deprecated Thread.id with Process.myTid() for TrafficStats tagging"}
1. In MainApplication.kt line 198 replace `TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())` with `TrafficStats.setThreadStatsTag(android.os.Process.myTid())`.
2. In NetworkModule.kt line 26 replace `TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())` with `TrafficStats.setThreadStatsTag(android.os.Process.myTid())`.
3. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the `'val id: Long' is deprecated` warning is gone.
:::

### Replace deprecated bundleOf in StorageCategoryDetailFragment

The build logs flag `fun bundleOf(vararg pairs: Pair<String, Any?>): Bundle` as deprecated, recommending the platform `Bundle` directly. The fragment uses it once to build arguments and once to send an empty result.

:codex-file-citation[codex-file-citation]{line_range_start=69 line_range_end=73 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L69-L73"}
:codex-file-citation[codex-file-citation]{line_range_start=248 line_range_end=248 path=app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryDetailFragment.kt#L248-L248"}

:::task-stub{title="Replace deprecated bundleOf with the platform Bundle"}
1. Replace the `bundleOf(ARG_LABEL to label, ARG_EXTENSIONS to ArrayList(extensions), ARG_ALL_KNOWN to ArrayList(allKnownExtensions))` block (lines 69-73) with `Bundle().apply { putString(ARG_LABEL, label); putStringArrayList(ARG_EXTENSIONS, ArrayList(extensions)); putStringArrayList(ARG_ALL_KNOWN, ArrayList(allKnownExtensions)) }`.
2. Replace `bundleOf()` on line 248 with `Bundle()`.
3. Remove the now-unused `import androidx.core.os.bundleOf` on line 11.
4. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the bundleOf deprecation is gone.
:::

### Replace deprecated InputMethodManager.HIDE_NOT_ALWAYS

The build logs flag `static field HIDE_NOT_ALWAYS: Int` as deprecated in the `hideKeyboard` helper. Passing `0` (no flags) hides the keyboard unconditionally and drops the deprecated constant without any other change.

:codex-file-citation[codex-file-citation]{line_range_start=244 line_range_end=247 path=app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt#L244-L247"}

:::task-stub{title="Replace deprecated HIDE_NOT_ALWAYS flag with 0"}
1. In `hideKeyboard` (ProcessUserDataActivity.kt line 246) replace `InputMethodManager.HIDE_NOT_ALWAYS` with `0`.
2. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the HIDE_NOT_ALWAYS deprecation is gone.
:::

### Drop redundant Elvis operators in UploadManager

The build logs report `Elvis operator (?:) always returns the left operand of non-nullable type 'String'` at the two `uploadAttachment` calls, because `item.remoteId` and `item.remoteRev` are already non-null `String`. The `?: ""` fallbacks are dead code.

:codex-file-citation[codex-file-citation]{line_range_start=176 line_range_end=176 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L176-L176"}
:codex-file-citation[codex-file-citation]{line_range_start=192 line_range_end=192 path=app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt#L192-L192"}

:::task-stub{title="Remove redundant ?: empty-string fallbacks in UploadManager"}
1. On line 176 change `uploadAttachment(item.remoteId ?: "", item.remoteRev ?: "", library, l)` to `uploadAttachment(item.remoteId, item.remoteRev, library, l)`.
2. On line 192 apply the identical change.
3. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the Elvis-operator warnings are gone.
:::

### Remove unnecessary safe call in CoursesAdapter

The build logs report `Unnecessary safe call on a non-null receiver of type 'String'`; `course.courseId` is already non-null, so the `?.let` indirection is redundant.

:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=82 path=app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt#L80-L82"}

:::task-stub{title="Remove unnecessary safe call on courseId in CoursesAdapter"}
1. On line 81 change `course.courseId?.let { courseIdToPosition[it] = index }` to `courseIdToPosition[course.courseId] = index`.
2. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the unnecessary-safe-call warning is gone.
:::

### Remove unnecessary safe call in PersonalsAdapter

The build logs report `Unnecessary safe call on a non-null receiver of type 'String'`; `path` is a non-null `String` in this branch (it is passed directly in the sibling branches), so `path?.let { File(it) }` can call `File(path)` directly.

:codex-file-citation[codex-file-citation]{line_range_start=80 line_range_end=83 path=app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt#L80-L83"}

:::task-stub{title="Remove unnecessary safe call on path in PersonalsAdapter"}
1. On line 82 change `Uri.fromFile(path?.let { File(it) }).toString()` to `Uri.fromFile(File(path)).toString()`.
2. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the unnecessary-safe-call warning is gone.
:::

### Remove unnecessary safe call on JsonObject in UserRepositoryImpl

The build logs report `Unnecessary safe call on a non-null receiver of type 'JsonObject'`; the receiver `act` is non-null, so `act?.get(...)` should be `act.get(...)` (the `?.asString` on the returned element stays).

:codex-file-citation[codex-file-citation]{line_range_start=1245 line_range_end=1245 path=app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L1245-L1245"}

:::task-stub{title="Remove unnecessary safe call on act in UserRepositoryImpl"}
1. On line 1245 change `act?.get("sendToNation")?.asString ?: "false"` to `act.get("sendToNation")?.asString ?: "false"`.
2. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the unnecessary-safe-call warning is gone.
:::

### Remove unnecessary safe call on View in EnterprisesReportsFragment

The build logs report `Unnecessary safe call on a non-null receiver of type 'View'`; the compiler proves `view` is non-null here, so the `?: Utilities.toast(...)` fallback is unreachable dead code.

:codex-file-citation[codex-file-citation]{line_range_start=134 line_range_end=136 path=app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt#L134-L136"}

:::task-stub{title="Remove unnecessary safe call and dead toast fallback in EnterprisesReportsFragment"}
1. On lines 134-136 replace `view?.let { Snackbar.make(it, event.message, Snackbar.LENGTH_LONG).show() } ?: Utilities.toast(requireContext(), event.message)` with `Snackbar.make(view, event.message, Snackbar.LENGTH_LONG).show()`.
2. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the unnecessary-safe-call warning is gone.
:::

### Remove always-true null guards in TeamsRepositoryImpl

The build logs report `Condition is always 'true'` at two `if (tag != null)` blocks. In both methods `tag` is guaranteed non-null by the immediately preceding `if (tag == null) { tag = RealmTeamLog(); ... }` assignment, so the guard is redundant.

:codex-file-citation[codex-file-citation]{line_range_start=1500 line_range_end=1507 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1500-L1507"}
:codex-file-citation[codex-file-citation]{line_range_start=1712 line_range_end=1718 path=app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1712-L1718"}

:::task-stub{title="Remove redundant if (tag != null) guards in TeamsRepositoryImpl"}
1. At line 1507 remove the `if (tag != null) {` guard and its matching closing brace, de-indenting the body (tag is already non-null at that point).
2. At line 1718 apply the identical removal.
3. Run `./gradlew assembleDefaultDebug --warning-mode all` and confirm the "Condition is always 'true'" warnings are gone.
:::

### Remove deprecated android.enableJetifier flag

The build logs warn `The option setting 'android.enableJetifier=true' is deprecated ... It will be removed in version 10.0 of the Android Gradle plugin`. The project is AndroidX-only, so Jetifier (support-library rewriting) is no longer needed.

:codex-file-citation[codex-file-citation]{line_range_start=25 line_range_end=25 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/gradle.properties#L25-L25"}

:::task-stub{title="Remove deprecated android.enableJetifier flag from gradle.properties"}
1. Delete the `android.enableJetifier=true` line (gradle.properties line 25).
2. Run `./gradlew assembleDefaultDebug assembleLiteDebug` and confirm both flavors still build (no legacy `com.android.support` artifact requires Jetifier).
:::

### Remove deprecated android.newDsl flag

The build logs warn `The option setting 'android.newDsl=false' is deprecated`. Deleting the line lets the flag take its default and removes the deprecated opt-out.

:codex-file-citation[codex-file-citation]{line_range_start=28 line_range_end=28 path=gradle.properties git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/gradle.properties#L28-L28"}

:::task-stub{title="Remove deprecated android.newDsl flag from gradle.properties"}
1. Delete the `android.newDsl=false` line (gradle.properties line 28).
2. Run `./gradlew assembleDefaultDebug assembleLiteDebug testDefaultDebugUnitTest` and confirm the build and unit tests still pass.
:::

### Upgrade release workflow actions off deprecated Node.js 20

The release run logs warn `Node.js 20 is deprecated. The following actions target Node.js 20 but are being forced to run on Node.js 24: dogi/sign-android-release@v5.1, dogi/upload-release-action@v2.9.0`. Pinning each to a release that already runs on Node 24 clears the warning.

:codex-file-citation[codex-file-citation]{line_range_start=59 line_range_end=59 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/.github/workflows/release.yml#L59-L59"}
:codex-file-citation[codex-file-citation]{line_range_start=138 line_range_end=138 path=.github/workflows/release.yml git_url="https://github.com/open-learning-exchange/myplanet/blob/c169558282a0294a80321f49a15313cb17879511/.github/workflows/release.yml#L138-L138"}

:::task-stub{title="Bump release workflow actions to Node.js 24 releases"}
1. Check the `dogi/sign-android-release` repo for a tag that runs on Node 24 and update the `uses:` ref on release.yml line 59 (only if such a release exists).
2. Do the same for `dogi/upload-release-action` on release.yml line 138.
3. Trigger the release workflow via workflow_dispatch and confirm the "Node.js 20 is deprecated" warning no longer appears.
:::

### Testing

Each task is verified the same way CI runs it: re-run `./gradlew testDefaultDebugUnitTest assembleDefaultDebug assembleLiteDebug --warning-mode all` and confirm the specific warning named in the task no longer appears in the output and the unit-test job stays green. The release-workflow task (Node.js 20) is verified only by a `workflow_dispatch` run of `release.yml`, since that job does not run on non-master branches.
