# myPlanet refactor round — generated work orders

date: 2026-10-09 · base commit: f18c50d25010913a0f85ba9d744705349f13b051 · open PRs checked: 4075 8175 10993 13287 13355 13415 13604 13657 13848 13928 14427 14650 14883 15108 15226 15266 15267 15559 15808 15820 15824 15825 15951 16594 16623 17694 17811 17832 18029 18045 18050 18076 (file-level off-limits enforced for PRs active in the last week: 13415 17694 17811 17832 18029 18045 18050 18076)

focus this round: performance quick wins — micro-optimizations that unblock bigger refactors — obvious inefficiencies removable without rewrites.

---

### 1. debounce the exams realtime collector in SurveysViewModel (roadmap 7)

context: `SurveysViewModel` collects `realtimeSyncManager.updatesFor("exams")` in its `init` block and calls `loadSurveys(...)` on every single batch update. `loadSurveys` fans out into multiple repository queries (per-team survey sources plus `getUserModel`, `getSurveyInfos`, `getSurveyFormState`), so a sync batch burst re-runs the whole screen query set once per emission. `HealthViewModel.kt` already debounces the identical realtime-sync pattern with `SYNC_REFRESH_DEBOUNCE_MS` — copy that shape.

files: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt` (`init` block at ~line 99, `loadSurveys` at ~line 107). Leave alone: `filter()` and `shouldSurveyBeVisible()` unchanged.

steps:
1. Add `kotlinx.coroutines.flow.debounce` (and `FlowPreview` opt-in if needed) to the collector, using a `private const val SYNC_REFRESH_DEBOUNCE_MS = 500L` companion constant, matching `HealthViewModel`.
2. Keep the initial `loadSurveys(...)` call in `init` un-debounced so the screen still populates immediately.
3. Remove the now-dead direct collect lambda wiring.
4. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; opening the surveys tab still shows the survey list, and a sync update burst results in at most one reload per debounce window.

size budget: ~15 changed lines, 1 file.

out of scope: do not change `loadSurveys` internals, repository signatures, or the filter logic.

---

### 2. debounce the teams realtime collector in TeamDetailFragment (roadmap 7)

context: `TeamDetailFragment.setupRealtimeSync()` collects `teamViewModel.getTeamUpdateFlow()` (`TeamViewModel.kt:65`, backed by `updatesFor("teams")`) and calls `refreshTeamDetails()` on every emission. `refreshTeamDetails()` (line 263) launches a coroutine that re-reads the user model and re-runs `loadTeamDetail(...)` — a full team reload per sync event. Other screens (`HealthViewModel`) debounce this same SharedFlow.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt` (`setupRealtimeSync` at ~line 416, `refreshTeamDetails` at line 263). Leave alone: `TeamViewModel.kt`, `ui/sync/RealtimeSyncMixin.kt`.

steps:
1. Apply `debounce(500)` (or `debounce` + `distinctUntilChanged` if emissions can repeat) to the collected flow inside `collectLatestWhenStarted`.
2. Keep the initial `loadTeamDetail`/`isTeamJoined` calls at lines 388-392 untouched so the first paint is unchanged.
3. Add the required flow imports.
4. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; team detail screen still renders and refreshes after sync, without one reload per emitted update.

size budget: ~10 changed lines, 1 file.

out of scope: do not modify `TeamViewModel`, `RealtimeSyncManager`, or add new tests.

---

### 3. debounce the chats refresh signal in ChatViewModel (roadmap 7)

context: `ChatViewModel`'s `init` collects `realtimeSyncManager.updatesFor("chats")` and emits `_refreshChatSignal` per update; `ChatHistoryFragment.kt:206` consumes it via `refreshChatHistory()` → `loadChatHistoryScreenData(userId)`. That function (line 131) re-runs `getUserById`, `getPlanetNewsMessages`, `getChatHistoryForUser`, `loadShareTargets`, plus `extractSharedViewInIds` — and nulls `searchIndex` at line 143, forcing a search re-index. A batch sync of chat docs causes redundant full reloads.

files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (`init` collect at ~line 71, `loadChatHistoryScreenData` at line 131). Leave alone: `ui/chat/ChatHistoryFragment.kt` (consumed API unchanged), `ui/chat/ChatDetailFragment.kt` (off-limits).

steps:
1. Debounce the `updatesFor("chats")` collect (e.g. `.debounce(500)`) before emitting `_refreshChatSignal`.
2. Alternatively emit into a debounced `MutableSharedFlow` — pick whichever matches surrounding code, no new abstractions.
3. Keep the initial `_refreshChatSignal.emit(Unit)` or initial `loadChatHistoryScreenData` call immediate so first load is unchanged.
4. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; chat history still refreshes after incoming chats, with at most one reload per debounce window.

size budget: ~15 changed lines, 1 file.

out of scope: do not touch the search-index implementation or the fragment.

---

### 4. scope Room DAO providers as @Singleton in RoomModule (roadmap 4+7)

context: `di/RoomModule.kt` exposes ~37 `@Provides fun provideXxxDao(database: AppDatabase)` methods, but only `provideAppDatabase` carries `@Singleton`. Every injection site therefore invokes the provider and calls `database.xxxDao()` fresh, allocating a new generated `XxxDao_Impl` (with its eagerly-created insertion/deletion/query adapters) per injection. Singleton-scoping the DAOs removes that repeated allocation and finishes the DI-scoping cleanup for this module.

files: `app/src/main/java/org/ole/planet/myplanet/di/RoomModule.kt` (all `provide*Dao` functions starting at `provideDictionaryDao`, ~line 64). Leave alone: `di/NetworkModule.kt`, `di/ServiceModule.kt`, `di/SharedPreferencesModule.kt` (off-limits via open PRs).

steps:
1. Add `javax.inject.Singleton` (already imported for `provideAppDatabase`) annotation to every `provide*Dao` method in the file.
2. Do not reorder, rename, or otherwise edit the provider bodies.
3. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; app still builds with Hilt and all DAO injections resolve (startup smoke: app opens dashboard).

size budget: ~37 added annotation lines, 1 file.

out of scope: no changes to `AppDatabase`, DAO interfaces, or other modules; do not switch providers to `@Reusable`.

---

### 5. delete the unreachable diff-refresh path from RealtimeSyncMixin (roadmap 8)

context: `ui/sync/RealtimeSyncMixin.kt` routes updates through `refreshRecyclerView()` (lines 46-49) which calls `getSyncRecyclerView()?.adapter` and casts to `callback/OnDiffRefreshListener` to invoke `refreshWithDiff()` — but no adapter in the codebase implements `OnDiffRefreshListener`, and both `shouldAutoRefresh` overrides (`ResourcesFragment.kt:800-804`, `CoursesFragment.kt:566-570`) return `false`, so the whole path is dead code guarded by a condition that can never be true.

files: `app/src/main/java/org/ole/planet/myplanet/callback/OnDiffRefreshListener.kt` (delete file), `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt` (remove `getSyncRecyclerView`, `shouldAutoRefresh`, `refreshRecyclerView`, and its call site in `onDataUpdated` at lines 39-41), `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt` (remove the `shouldAutoRefresh`/`getSyncRecyclerView` overrides at ~lines 800-806), `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt` (remove the same overrides at ~lines 566-570). Leave alone: `onDataUpdated` overrides in both fragments — they stay.

steps:
1. Delete `callback/OnDiffRefreshListener.kt`.
2. Remove `getSyncRecyclerView()`, `shouldAutoRefresh()`, and `refreshRecyclerView()` from `RealtimeSyncMixin`, and drop the guarded call inside `onDataUpdated`.
3. Remove the corresponding overrides in `ResourcesFragment` and `CoursesFragment` plus any now-unused imports.
4. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; resources and courses lists still refresh via their existing `onDataUpdated` implementations.

size budget: ~40 removed lines, 4 files.

out of scope: do not wire the diff-refresh up instead of deleting it, and do not touch other mixin methods.

---

### 6. hoist the TabLayout lookup out of DashboardActivity's global layout listener (roadmap 7)

context: `DashboardActivity.kt` installs an `OnGlobalLayoutListener` (lines 278-279) that runs `topBarVisible()` on every global layout pass — i.e. on every measure/layout of the whole window. `topBarVisible()` (lines 800-806) calls `findViewById<TabLayout>(R.id.tab_layout)` and reads `resources.configuration` each time, then writes `tabLayout.visibility`. This is per-frame work for a value that only changes on configuration change.

files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt` (listener registration ~lines 278-279, `topBarVisible()` ~lines 800-806, listener removal at ~lines 976-978). Leave alone: the rest of `onCreate` and other layout listeners.

steps:
1. Cache the `TabLayout` once (e.g. a lazy `tabLayout` field via `findViewById` or the existing view binding) instead of looking it up inside `topBarVisible()`.
2. Make `topBarVisible()` early-return when `tabLayout.visibility` already matches the orientation-derived value.
3. Call `topBarVisible()` once after view inflation so the listener can be removed, or keep the listener only if a justification exists — prefer removing it and invoking on `onConfigurationChanged` if that override exists; keep `mBottomTabLayoutHelper`/`checkNotification` logic untouched.
4. Keep the listener-removal block consistent with whatever remains.
5. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the tab bar still hides in landscape and shows in portrait after rotation.

size budget: ~25 changed lines, 1 file.

out of scope: no behavior changes to the tab layout itself, no Compose work, no other listeners.

---

### 7. replace printStackTrace with Log.e in ExamTakingFragment (roadmap 8)

context: `ExamTakingFragment.kt` calls `e.printStackTrace()` at lines 142, 177, and 664, dumping stack traces to stderr with no tag and no filtering — invisible in logcat tooling and noise in release builds. The same file already uses `android.util.Log` correctly (e.g. `Log.e` at ~line 811), so the fix is purely mechanical.

files: `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt` (lines ~142, ~177, ~664). Leave alone: `ui/exam/UserInformationFragment.kt` (covered by another task).

steps:
1. Add a `private const val TAG = "ExamTakingFragment"` in a companion object if none exists (reuse an existing TAG if present).
2. Replace each `e.printStackTrace()` with `Log.e(TAG, "<short message>", e)` using a message that names the operation that failed (e.g. answer save, exam load).
3. `android.util.Log` is already imported; do not add other imports.
4. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; exam-taking flow (answer a question, navigate questions) still works; `grep -n printStackTrace` on the file returns nothing.

size budget: ~10 changed lines, 1 file.

out of scope: do not touch `BaseExamFragment`, the `AnswerData` cache, or any listener logic.

---

### 8. replace printStackTrace with Log.e in the base fragments (roadmap 8)

context: `base/BaseTeamFragment.kt:62` and `base/BaseContainerFragment.kt` (lines 173 and 175) call `e.printStackTrace()` inside exception handlers shared by many subclasses — untagged stderr output from central UI plumbing. Neither file currently imports `android.util.Log`, so the fix adds one import plus a tag.

files: `app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt` (line ~62), `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt` (lines ~173, ~175). Leave alone: `base/BaseExamFragment.kt`, `base/BaseRecyclerFragment.kt`, `base/BaseResourceFragment.kt`.

steps:
1. In each file add `import android.util.Log`.
2. Add a `private const val TAG` in the class's companion (or reuse the simple class name string literal as the tag) matching surrounding conventions.
3. Replace each `printStackTrace()` with `Log.e(TAG, "<operation that failed>", e)`.
4. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; no `printStackTrace` remains in the two files; teams and container screens still load.

size budget: ~15 changed lines, 2 files.

out of scope: do not restructure the catch blocks or change which exceptions are swallowed.

---

### 9. clean up printStackTrace in FileUtils, KeyboardUtils, and RetryUtils (roadmap 8+9)

context: `utils/FileUtils.kt:255` and `utils/KeyboardUtils.kt:16` call `e.printStackTrace()`/`printStackTrace()` even though `FileUtils` already has `private const val TAG = "FileUtils"` and `Log.e` usage in the same file. `utils/RetryUtils.kt:31` has `lastException?.printStackTrace()` inside the generic `retry(...)` helper — `RetryUtils` is one of the few utils with zero `android.*` imports, so logging there would add an Android dependency to a platform-free file (roadmap 9 wants platform-free utilities reusable in the KMP core).

files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` (line ~255, reuse existing TAG), `app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt` (line ~16, add TAG), `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt` (line ~31). Leave alone: other utils files.

steps:
1. In `FileUtils` replace `e.printStackTrace()` with `Log.e(TAG, "<file copy failed>", e)` — no new import needed if `android.util.Log` is already imported.
2. In `KeyboardUtils` add `import android.util.Log`, a TAG constant, and replace both `e.printStackTrace()` calls.
3. In `RetryUtils` delete the `lastException?.printStackTrace()` line — the exception is already surfaced through the returned `result`; do NOT add `android.util.Log` (keeps the file platform-free).
4. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; `grep -n printStackTrace` returns nothing for all three files; `RetryUtils.kt` still has no `android.*` imports.

size budget: ~15 changed lines, 3 files.

out of scope: no behavior changes to retry timing/attempts; do not add a logging abstraction.

---

### 10. trim per-event Log.d string interpolation in UserInformationFragment (roadmap 8)

context: `ui/exam/UserInformationFragment.kt` emits `Log.d("UserInformationFragment", ...)` with eagerly-interpolated message strings at roughly lines 92, 98, 146, 153, 279, 281, 288, 290, 292, 295 — the same literal tag repeated at every site, and several interpolate values that are computed even when debug logging is stripped/disabled in release builds.

files: `app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt` (all `Log.d` sites; `import android.util.Log` already at line 8). Leave alone: `ui/exam/ExamTakingFragment.kt` (covered by another task).

steps:
1. Introduce a single `private const val TAG = "UserInformationFragment"` in a companion object and replace every literal tag.
2. Drop or downgrade the noisiest per-interaction `Log.d` calls in the submit/dismiss happy path (keep the ones guarding real error branches as `Log.e`/`Log.w` where appropriate); do not remove logging that is the only signal for a catch block.
3. Compile and run the unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest` green; user-information dialog still submits and dismisses; the file has a single TAG constant and no duplicated literal tags.

size budget: ~25 changed lines, 1 file.

out of scope: do not change dialog behavior, submission logic, or `UserProfileDbHandler` calls.
