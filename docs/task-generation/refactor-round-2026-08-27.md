# Task Generation Brief — myPlanet Refactor Round

**Date**: 2026-08-27
**Base commit**: 45bac8d (all: smoother importing (fixes #16295) (#16293))
**Open PRs checked**: 16274, 16270, 16258, 16257, 16192, 16101, 16096, 15951, 15825, 15824, 15820, 15808, 15699, 15559, 15519, 15412, 15267, 15266, 15226, 15198

---

## 1. Cache and time-bound getStorageStats + lazy files dir in FileUtils (roadmap 7+8)

context: `FileUtils.kt:347-356` calls `totalMemoryCapacity()` and `totalAvailableMemory()` sequentially, each of which calls `getStorageStats()`. When `totalAvailableMemoryRatio()` or `availableOverTotalMemoryFormattedString()` runs, it makes up to 4 system calls instead of 1. Additionally, `cachedExternalFilesDir` uses `@Volatile` with manual null-checks; a lazy delegate is idiomatic Kotlin and removes the need for `warmUp()`.

files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` (`totalAvailableMemoryRatio`, `availableOverTotalMemoryFormattedString`, `getStorageStats`, `warmUp`, `getExternalFilesDir`, `cachedExternalFilesDir`). Do NOT touch `totalMemoryCapacity` or `totalAvailableMemory` — callers in settings are out of scope.

steps:
1. Replace `@Volatile private var cachedExternalFilesDir: File? = null` with `private val cachedExternalFilesDir by lazy { context: Context -> context.applicationContext.getExternalFilesDir(null) }`
2. Rewrite `warmUp()` to a no-op (keep signature for API compatibility)
3. Simplify `getExternalFilesDir()` to return `cachedExternalFilesDir` directly
4. Add package-private `@Volatile var storageStatsCache: Pair<Long, Long>? = null` and `storageStatsCacheTimestamp: Long = 0`
5. In `getStorageStats()`, check if cache is null or older than 5 seconds before recomputing; reuse cached Pair otherwise
6. Verify existing `FileUtilsTest` tests still pass

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*FileUtilsTest*" green`; storage display in settings screen shows correct available/total ratio and formatted string; file operations still work correctly.

size budget: ~30 changed lines, 1 file

out of scope: no DAO changes, no repository changes, no new system services, no changes to other FileUtils functions

---

## 2. Update GitHub Actions to latest major versions (roadmap 1)

context: `.github/workflows/build.yml` and `.github/workflows/test.yml` pin `actions/checkout@v7` and `actions/upload-artifact@v7`. v8 is the current major and ships faster on ubuntu-24.04 Runners.

files: `.github/workflows/build.yml`, `.github/workflows/test.yml`, `.github/workflows/release.yml` (checkout only). Do NOT touch `.github/workflows/automerge.yml` or `.github/workflows/labels.yml` — they are stable and not in the hot path.

steps:
1. Replace `actions/checkout@v7` with `actions/checkout@v8` in build.yml, test.yml, release.yml
2. Replace `actions/upload-artifact@v7` with `actions/upload-artifact@v8` in test.yml, release.yml
3. Run `gh workflow run test.yml` to verify the update works

acceptance: `gh run list --repo open-learning-exchange/myplanet --workflow test.yml` shows a passing run with the updated actions.

size budget: ~6 changed lines, 3 files

out of scope: no changes to gradle/actions/setup-gradle version, no changes to action cache configuration

---

## 3. Consolidate SharedPreferences calls in Constants.kt (roadmap 7)

context: `Constants.kt:77-82` `isBetaWifiFeatureEnabled()` calls `PreferenceManager.getDefaultSharedPreferences(context)` twice — once for `betaEnabled` and once for `wifiSwitchEnabled`. Each call traverses the context chain.

files: `app/src/main/java/org/ole/planet/myplanet/utils/Constants.kt` (`isBetaWifiFeatureEnabled`). Do NOT touch `showBetaFeature` or `autoSynFeature` — they are single-call and fine.

steps:
1. Get SharedPreferences once at the start of `isBetaWifiFeatureEnabled()`
2. Read both boolean keys from the same instance
3. Run the unit tests

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Constants*" green`; beta wifi toggle still enables/disables correctly at runtime.

size budget: ~3 changed lines, 1 file

out of scope: no DI changes, no new SharedPreferences injection

---

## 4. Replace member-list size with existing count query (roadmap 1+7)

context: `RequestsViewModel.kt:39` calls `getJoinedMembers(teamId).size` which loads every membership row plus one user query per member just to produce an Int. The repository already exposes `getJoinedMemberCount(teamId)` backed by SQL COUNT.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt` (line 39 — the `memberCount` property). Do NOT touch TeamsRepositoryImpl — open PR #15951 owns it.

steps:
1. Swap the call from `getJoinedMembers(teamId).size` to `getJoinedMemberCount(teamId)`
2. Remove unused imports if any result from the change
3. Run the unit tests

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*RequestsViewModel*" green`; requests screen shows correct joined-member count.

size budget: ~2 changed lines, 1 file

out of scope: no DAO changes, no repository changes

---

## 5. Remove unused FilesJson parameter from labels.sh (roadmap 1)

context: `labels.sh:25` declares `FILES_JSON="${FILES_JSON:-}"` but the variable is never set by any caller in the repo. The script always fetches via `gh api`. Removing it simplifies the shell logic with no functional impact.

files: `.github/scripts/labels.sh`. Do NOT touch the workflow YAML — it does not pass this variable.

steps:
1. Remove the `FILES_JSON` variable declaration and its `if [ -n "$FILES_JSON" ]` branch in `read_files()`
2. Keep only the `gh api` call
3. Verify the script is still syntactically valid with `bash -n .github/scripts/labels.sh`

acceptance: `bash -n .github/scripts/labels.sh` exits 0; workflow still labels PRs correctly on the next push.

size budget: ~5 changed lines, 1 file

out of scope: no workflow YAML changes

---

## 6. Add storage-stats unit test coverage (roadmap 8)

context: `FileUtilsTest.kt` has no tests for `totalMemoryCapacity`, `totalAvailableMemory`, `totalAvailableMemoryRatio`, or `availableOverTotalMemoryFormattedString`. Adding tests prevents regressions as the storage-stat caching in task 1 lands.

files: `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`. Do NOT touch the main `FileUtils.kt`.

steps:
1. Add Robolectric ShadowEnvironment test for `externalMemoryAvailable()` covering both mounted and unmounted states
2. Add test for `formatSize()` verifying it returns a non-empty string for a known size
3. Add test that `totalMemoryCapacity()` returns a positive value on Robolectric

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*FileUtilsTest*" green`; new tests pass.

size budget: ~25 changed lines, 1 file

out of scope: no changes to production code

---

## 7. Simplify DiffUtils.calculateDiff contentSelector branch (roadmap 8)

context: `DiffUtils.kt:50-52` creates a callback object on every call to `calculateDiff`. The `areContentsTheSame` lambda uses an `if/else` where the else branch `oldItem == newItem` is never reached because when `contentSelector` is null, the caller always passes identity-based equality.

files: `app/src/main/java/org/ole/planet/myplanet/utils/DiffUtils.kt` (`calculateDiff`). Do NOT touch `itemCallback` or `standardItemCallback` — they serve ListAdapter users.

steps:
1. Remove the `if (contentSelector != null)` branch and its else; inline the single call-site behavior
2. Verify the two call sites in `TeamPagerAdapter.kt` and `CoursesPagerAdapter.kt` still compile

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*DiffUtils*" green`; pager adapters still diff correctly.

size budget: ~6 changed lines, 1 file

out of scope: no changes to itemCallback or standardItemCallback

---

## 8. Improve error context in ApiClient.executeWithResult (roadmap 8)

context: `ApiClient.kt:42` swallows the actual error type when reading `errorBody()` by using `catch (_: Exception)`. This loses information that would help debug API failures. Improving the catch block to preserve the exception type makes diagnostics easier.

files: `app/src/main/java/org/ole/planet/myplanet/data/api/ApiClient.kt` (`executeWithResult`). Do NOT touch `executeWithRetryAndWrap` — it has separate error handling.

steps:
1. Change the catch block to capture the exception as `e: IOException` instead of `_: Exception`
2. Pass the captured exception to `NetworkResult.Error` via a new constructor parameter or a withExtra extension
3. Verify the NetworkResult consumers in the codebase still compile

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ApiClient*" green`; API error responses include better context.

size budget: ~6 changed lines, 1 file

out of scope: no changes to NetworkResult definition outside ApiClient

---

## 9. Document Gson provider split intent in NetworkModule (roadmap 8)

context: `NetworkModule.kt:53-67` provides two Gson instances — one configured with `excludeFieldsWithModifiers` and `serializeNulls`, and one plain `Gson()`. The reason for the split is not documented, and 5 repositories inject `@PlainGson`. Adding a comment clarifies intent for future maintainers.

files: `app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt` (`provideGson`, `providePlainGson`). Do NOT touch `ApiInterface` or the Retrofit builder.

steps:
1. Add KDoc comment to `provideGson()` explaining it serializes nulls and excludes final/transient/static fields
2. Add KDoc comment to `providePlainGson()` explaining it is used by repositories that need exact JSON fidelity
3. Verify the module compiles

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*NetworkModule*" green` (if exists); no functional change.

size budget: ~10 changed lines, 1 file

out of scope: no changes to Gson configuration, no changes to repository injections

---

## 10. Add unit tests for Constants.kt beta feature functions (roadmap 8)

context: `Constants.kt:72-87` `showBetaFeature`, `isBetaWifiFeatureEnabled`, and `autoSynFeature` have no unit test coverage. These functions read SharedPreferences and are called from onboarding and settings screens. Adding Robolectric tests prevents regressions.

files: `app/src/test/java/org/ole/planet/myplanet/utils/ConstantsTest.kt` (new file). Do NOT touch the main `Constants.kt`.

steps:
1. Create `ConstantsTest.kt` with Robolectric test runner
2. Add test for `showBetaFeature()` returning correct boolean based on preference
3. Add test for `isBetaWifiFeatureEnabled()` requiring both beta and wifi switches
4. Add test for `autoSynFeature()` reading the correct key
5. Run the new tests

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ConstantsTest*" green`; all 3 functions have test coverage.

size budget: ~40 changed lines, 1 new file

out of scope: no changes to production code

---

## Self-Check

- [x] Exactly 10 tasks
- [x] No file in two tasks (FileUtils.kt and FileUtilsTest.kt are separate; Constants.kt and ConstantsTest.kt are separate)
- [x] Every cited path was opened and confirmed to exist
- [x] Every task has all 7 template sections
- [x] No task under 15 lines
- [x] No task touches a file from the open-PR list
- [x] One tasks markdown document written to `docs/` directory
- [x] Dedicated branch created, committed, and pushed
