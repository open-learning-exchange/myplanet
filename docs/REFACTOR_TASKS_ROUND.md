# myPlanet Refactor Round — 10 Low-Risk Tasks

Goal: reinforce repository boundaries between layers, call out cross-feature data leaks, and move data functions out of UI/data/service into repositories. Keep every PR small, single-purpose, and reviewable. Chosen so they touch disjoint files and will not collide during merge.

Theme coverage: DI, data layers, DiffUtil/ListAdapter (+ reuse `DiffUtils.itemCallback`), ViewModels, threading/dispatchers, long-running observers.

Scope rule for this round: each task lists the **only** files it should touch. No new code that is not called; no large multi-file refactors. Do not edit outside that set.

---

## Task 1 — Introduce a `DictionaryRepository` and pull DAO/file/JSON parsing out of `DictionaryActivity`

Why: `DictionaryActivity` injects `DictionaryDao` directly and parses a downloaded `JsonArray` file into `DictionaryEntity` rows inside the Activity. That is a data-layer leak across the UI/repository boundary.

Files to touch (only):
- new `repository/DictionaryRepository.kt` + `repository/DictionaryRepositoryImpl.kt`
- `di/RepositoryModule.kt` (bind it)
- `ui/dictionary/DictionaryActivity.kt` (replace `dictionaryDao` + file parsing with repository calls)

Move into the repository:
- `count()` → `getDictionaryCount(): Long`
- `insertAll(entities)` → `importFromJsonFile(path: String): Int` (read file + parse `JsonArray` + map to `DictionaryEntity` + insert, all inside the repo on `dispatcherProvider.io`)
- `findByWord(query)` → `findWord(query: String): DictionaryEntity?`

Acceptance:
- `DictionaryActivity` no longer imports `DictionaryDao`, `JsonArray`, `JsonUtils`, or `FileUtils`.
- The Activity only asks the repository for a count, an import, and a lookup.
- No new public methods beyond the three above; no unused code.

Merge-safety: new repository files + one Activity edit + one DI binding line. No overlap with other tasks.

---

## Task 2 — Type the rating path: return `RatingSummary` from `RatingsRepository.getRatingsById` and delete the `JsonObject` overload of `setRatings`/`showRating`

Why: `RatingsRepository.getRatingsById(...): JsonObject?` leaks JSON into `ResourceDetailFragment`, which stores `lastKnownRating: JsonObject?` and calls `BaseContainerFragment.setRatings(JsonObject?)` → `CourseRatingUtils.showRating(JsonObject?)`. A typed `RatingSummary` already exists and the course-detail path already uses it. The JSON overload is only kept alive by the resource-detail path.

Files to touch (only):
- `repository/RatingsRepository.kt` — change `getRatingsById(...): JsonObject?` to `RatingSummary?`
- `repository/RatingsRepositoryImpl.kt` — return `getRatingSummary(type, resourceId, userId)` instead of `aggregated?.toJson()`; drop the now-unused `RatingAggregation.toJson()` only if nothing else uses it
- `ui/resources/ResourceDetailFragment.kt` — `lastKnownRating: RatingSummary?`; `onRatingChanged()` calls `setRatings(RatingSummary)`
- `base/BaseContainerFragment.kt` — remove the `setRatings(JsonObject?)` overload
- `utils/CourseRatingUtils.kt` — remove the `showRating(JsonObject?)` overload

Acceptance:
- No `JsonObject` import remains in `ResourceDetailFragment`, `BaseContainerFragment`, or `CourseRatingUtils`.
- `getRatingsById` returns `RatingSummary?`; the existing `RatingSummary` fields cover average/total/userRating exactly.
- `insertRatingsFromSync(List<JsonObject>)` stays (sync boundary, acceptable).

Merge-safety: ratings + resource-detail + base rating helper only. Disjoint from Tasks 1, 3, 4, 6.

---

## Task 3 — Drop the dead `JsonObject` rating map from `CoursesUiState`/`CoursesViewModel`

Why: `CoursesUiState.map: HashMap<String?, JsonObject>` is fetched via `ratingsRepository.getCourseRatings(userId)` and threaded through `processCourses(...)` and `filterCourses(...)`, but no UI consumer reads `state.map` — `CoursesAdapter` has an unused `import JsonObject` and no rating view, and course rows do not display ratings. `refreshCourseRatings(userId)` is called from `CoursesFragment.onRatingChanged` only to trigger `adapterCourses.notifyItemChangedById(id)`, not to use the map. This is a cross-feature data leak (ratings fetched into the courses state that nobody renders) plus dead code.

Files to touch (only):
- `ui/courses/CoursesViewModel.kt` — remove `map` from `CoursesUiState`, remove the `ratingsRepository` parameter if it becomes unused, remove `refreshCourseRatings()` body's map assignment (keep the `notifyItemChangedById` trigger path in the fragment by exposing a no-op or moving the refresh to just re-fetch progress)
- `ui/courses/CoursesFragment.kt` — adjust `onRatingChanged` so it no longer calls a removed `refreshCourseRatings` that only served the dead map; keep the `notifyItemChangedById(id)` call
- `ui/courses/CoursesAdapter.kt` — remove the unused `import JsonObject`

Acceptance:
- `CoursesUiState` no longer has a `JsonObject` field.
- `CoursesAdapter` no longer imports `JsonObject`.
- Course list rendering is unchanged (no rating was shown before, none after).

Merge-safety: courses ViewModel/Fragment/Adapter only. Disjoint from Tasks 1, 2, 4–10. Confirm `ratingsRepository` removal does not conflict with Task 2 (Task 2 edits `RatingsRepository` interface/impl, not the courses VM) — safe.

---

## Task 4 — Move `VoicesAdapter`'s `submitList` shims onto a typed list and stop `VoicesFragment` from mutating the adapter's backing list

Why: `VoicesFragment` calls `imageList.clear()` and the fragment holds `imageList` used to build news; the news list itself is submitted via `VoicesAdapter.submitList` which overrides `submitList` to run `prepareSubmitList`. The adapter already extends `ListAdapter<News>` with `DiffUtils.itemCallback<News>` — good — but the fragment keeps an `AdapterDataObserver` that re-runs `showNoData` on `onChanged/onItemRangeInserted/onItemRangeRemoved`. With `ListAdapter`, `onChanged` should never fire (diffing dispatches range changes), so the observer is partly dead and partly a long-running listener. Trim it to the two range callbacks the diff actually emits.

Files to touch (only):
- `ui/voices/VoicesFragment.kt` — reduce `observer` to `onItemRangeInserted` + `onItemRangeRemoved` only; drop `onChanged` override and the now-unused `AdapterDataObserver` import if unused
- `ui/voices/VoicesAdapter.kt` — no functional change unless needed; keep `DiffUtils.itemCallback<News>`

Acceptance:
- No `onChanged` override; no behavior regression on empty-state messaging (range callbacks still update `tvMessage`).
- Observer still unregistered in `onDestroyView`.

Merge-safety: voices files only. Disjoint from all other tasks.

---

## Task 5 — Remove the unused `UploadManager` injection from `ProcessUserDataActivity`

Why: `ProcessUserDataActivity` `@Inject lateinit var uploadManager: UploadManager` but never references it (only `uploadToShelfService` and `syncRepository` are used). Dead DI field.

Files to touch (only):
- `ui/sync/ProcessUserDataActivity.kt` — delete the `@Inject` field and the `UploadManager` import

Acceptance:
- Build succeeds; no other reference to `uploadManager` in this file.
- Do not touch `UploadManager` itself or any other sync activity.

Merge-safety: single file, single deletion. Disjoint from all others.

---

## Task 6 — Route `beta_auto_download` writes through `SharedPrefManager` and stop `SettingsActivity` from holding `SharedPreferences` directly

Why: `SettingsActivity` `@Inject lateinit var defaultPref: SharedPreferences` and writes `defaultPref.edit { putBoolean("beta_auto_download", ...) }` directly. `SharedPrefManager.getBetaAutoDownload()` already exists for reads; there is no setter. This is a UI→persistence boundary leak. Add the setter and use it.

Files to touch (only):
- `services/SharedPrefManager.kt` — add `fun setBetaAutoDownload(enabled: Boolean) = pref.edit { putBoolean("beta_auto_download", enabled) }` (mirror the existing getter's key)
- `ui/settings/SettingsActivity.kt` — remove the `defaultPref` field + `SharedPreferences` import; in the `beta_auto_download` preference listener call `sharedPrefManager.setBetaAutoDownload(isChecked)`

Acceptance:
- `SettingsActivity` no longer imports `SharedPreferences` or holds `defaultPref`.
- `MainApplication`'s direct `defaultPref.getBoolean("beta_auto_download", false)` read is **not** in scope here (leave it; it is the app entry point, not UI) — call it out in the PR body as a follow-up but do not edit `MainApplication.kt`.

Merge-safety: SharedPrefManager + SettingsActivity only. Disjoint from Tasks 1–5, 7–10.

---

## Task 7 — Fix `SurveyFragment`'s `notifyDataSetChanged()` on a `ListAdapter`

Why: `SurveyFragment` calls `getAdapter().notifyDataSetChanged()` in two spots (lines ~180, ~185) while `SurveysAdapter` is a `ListAdapter<StepExam>` with `DiffUtils.itemCallback`. Calling `notifyDataSetChanged()` on a `ListAdapter` defeats diffing and is a code-health smell flagged in the audit.

Files to touch (only):
- `ui/surveys/SurveyFragment.kt` — replace the two `getAdapter().notifyDataSetChanged()` calls with `submitList(currentList)` re-submission of the already-updated list (or, better, re-fetch the surveys list and `submitList` it) so the diff runs

Acceptance:
- No `notifyDataSetChanged()` call remains in `SurveyFragment.kt`.
- `grep -r "notifyDataSetChanged" app/src/main/java/org/ole/planet/myplanet/ui/` returns 0 results after this task.
- Behavior (filter/refresh) unchanged.

Merge-safety: `SurveyFragment.kt` only. Disjoint from all other tasks.

---

## Task 8 — Tighten `RealtimeSyncManager` exposure in `ChatViewModel` and `TeamViewModel` to a narrow per-feature flow

Why: Both `ChatViewModel` and `TeamViewModel` inject the `@Singleton RealtimeSyncManager` and each filters its `dataUpdateFlow` by `it.table == "chats"` / `"teams"`. The ViewModel reaches into a shared service's internal `MutableSharedFlow` and re-implements table filtering. This is a cross-feature data leak (every chat/team ViewModel depends on the global sync event bus shape). Expose a narrow, feature-scoped Flow from a small wrapper so the ViewModels no longer reference `RealtimeSyncManager` or `TableDataUpdate` filtering.

Files to touch (only):
- new `services/sync/FeatureSyncUpdates.kt` (or extend `RealtimeSyncManager` with `fun updatesFor(table: String): Flow<TableDataUpdate>` = `dataUpdateFlow.filter { it.table == table }`)
- `ui/chat/ChatViewModel.kt` — inject the narrow accessor instead of `RealtimeSyncManager`; replace `realtimeSyncManager.dataUpdateFlow.filter { it.table == "chats" }` with the scoped flow
- `ui/teams/TeamViewModel.kt` — same for `"teams"`; `getTeamUpdateFlow()` returns the scoped flow
- `di/ServiceModule.kt` — provide the wrapper if a new class is added

Acceptance:
- Neither ViewModel imports `RealtimeSyncManager` directly nor re-filters `dataUpdateFlow`.
- Filtering logic lives in one place (the sync layer), not duplicated in two ViewModels.
- `TeamDetailFragment`'s `getTeamUpdateFlow().filter { it.table == "teams" }` still works — move that filter into the scoped accessor too so the fragment just collects `getTeamUpdateFlow()`.

Merge-safety: chat VM + team VM + team detail fragment + one sync-layer file + one DI line. Disjoint from Tasks 1–7, 9–10 (does not touch ratings, dictionary, voices, settings, survey, or upload files).

---

## Task 9 — Make `RetryInterceptor.backoff` cooperative without blocking sleeping slices (threading hygiene)

Why: `RetryInterceptor.backoff` calls `Thread.sleep(minOf(remaining, MAX_BACKOFF_SLICE_MS))` in a loop to poll `chain.call().isCanceled()`. OkHttp interceptors run on OkHttp dispatcher threads, and a 250 ms blocking sleep per slice is acceptable but the audit flagged it. Low-risk improvement: keep the cancellation check but use `chain.call()` cancellation via a shorter sleep and clear the interrupt flag cleanly (already done). This task is intentionally tiny — only normalize the sleep to a named constant and ensure `isCanceled()` is checked **before** the first sleep so a cancellation during `proceed()` doesn't burn a full slice.

Files to touch (only):
- `data/api/RetryInterceptor.kt` — check `chain.call().isCanceled()` at the top of `backoff` before sleeping; keep `MAX_BACKOFF_SLICE_MS`; no new threads/launches

Acceptance:
- No new coroutine usage (interceptor must stay synchronous).
- Cancellation is detected within one slice; existing `InterruptedException` handling preserved.
- Unit tests for `RetryInterceptor` (if present) still pass; add one tiny test if none covers cancellation.

Merge-safety: `RetryInterceptor.kt` only. Disjoint from all other tasks.

---

## Task 10 — Remove the unused `JsonObject` import from `CoursesAdapter` and audit `HealthExaminationAdapter` for any stray `notifyDataSetChanged`/direct list mutation

Why: `CoursesAdapter` imports `JsonObject` (line 22) but never uses it — dead import that also makes the JSON-leak grep noisier. Separately, confirm `HealthExaminationAdapter` (a `ListAdapter` with `DIFF_CALLBACK` via `DiffUtils.itemCallback`) has no path that calls `notifyDataSetChanged` or mutates `currentList` directly. This is a cleanup + guardrail task.

Files to touch (only):
- `ui/courses/CoursesAdapter.kt` — remove `import com.google.gson.JsonObject` (only if Task 3 has not already done it; if both are in the same round, coordinate so only one PR removes it — see ordering note below)
- `ui/health/HealthExaminationAdapter.kt` — if any direct mutation exists, replace with `submitList`; otherwise leave a one-line confirmation in the PR body that it is clean

Acceptance:
- `grep "import com.google.gson.JsonObject" CoursesAdapter.kt` returns nothing.
- `HealthExaminationAdapter` relies solely on `submitList` + `DiffUtils.itemCallback`.
- No behavior change.

Merge-safety: `CoursesAdapter.kt` (import only) + `HealthExaminationAdapter.kt` (verify/minor). **Ordering note:** if Task 3 is also submitted this round, have Task 3 remove the `CoursesAdapter` import and Task 10 only touches `HealthExaminationAdapter`. They share exactly one import line — pick one owner to avoid a trivial merge conflict.

---

## Suggested merge order (avoids the one shared line)

1. Task 5 (delete unused `UploadManager` field) — trivial, merge first
2. Task 7 (`notifyDataSetChanged` removal in `SurveyFragment`)
3. Task 1 (`DictionaryRepository`)
4. Task 6 (`beta_auto_download` via `SharedPrefManager`)
5. Task 2 (typed `RatingSummary` path)
6. Task 3 (drop dead `CoursesUiState.map`) — **this PR removes the `CoursesAdapter` JsonObject import**
7. Task 10 (`HealthExaminationAdapter` guardrail; skip the `CoursesAdapter` edit since Task 6 above did it)
8. Task 4 (`VoicesFragment` observer trim)
9. Task 8 (narrow `RealtimeSyncManager` exposure)
10. Task 9 (`RetryInterceptor.backoff` cancellation check)

All tasks touch disjoint file sets except the single `CoursesAdapter` import line, which is owned by Task 3 in this ordering.

---

## Out of scope for this round (call out in PR bodies, do not do now)

- `MainApplication` reading `defaultPref.getBoolean("beta_auto_download", ...)` directly — app entry point, not UI; follow-up.
- `CourseProgressData.steps: JsonArray` → typed step model + `ProgressGridAdapter : ListAdapter<JsonObject>` rewrite (bigger; needs a new model + repository method; schedule for next round).
- `DashboardViewModel.getCourseStatusString(JsonObject?)` — depends on `ProgressRepository.findProgressForCourse(): JsonObject?` returning JSON; needs the progress typing work above.
- `ReplyActivity`/`VoicesActions` building image `JsonObject` payloads by hand — move to a `NewsImageSerializer` helper in the voices repository later.
- `ResourceViewerFragment` managing `ExoPlayer` directly — extract a `PlayerHolder` later; not a boundary task.
- Compose migration, global navigation, sync/upload consolidation — roadmap items 2/5/6, intentionally deferred.
