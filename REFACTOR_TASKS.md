# myPlanet low-hanging refactor / performance round — 10 tasks

Goal: one PR per task, small footprint, no new dependencies, no unused code, and no big rewrites. Each item is chosen to remove an obvious inefficiency or layering violation while keeping merge-conflict risk low.

---

## Task 1 — Add `DictionaryRepository` and move `DictionaryActivity` off the DAO

**Why:** UI currently injects `DictionaryDao` directly; a repository removes a data-layer leak and makes the DAO usage testable/consistent with the rest of the app.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/` (new `DictionaryRepository.kt` + `DictionaryRepositoryImpl.kt`)
- `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt`

**Change:**
1. Create `DictionaryRepository` interface with `count()`, `insertAll(entities)`, `findByWord(word)`.
2. Implement it in `DictionaryRepositoryImpl`, delegating to `DictionaryDao`.
3. Bind the interface in `RepositoryModule`.
4. In `DictionaryActivity`, inject `DictionaryRepository` and replace `dictionaryDao.*` calls.
5. Keep the existing `dispatcherProvider.io` wrappers inside the Activity; no behavior change.

**Conflict risk:** Low — only `DictionaryActivity` consumes `DictionaryDao`.

---

## Task 2 — Run `UserProfileViewModel` init/profile work off the main thread

**Why:** `viewModelScope.launch` defaults to `Dispatchers.Main`; repository aggregation and JSON/profile mapping should happen on `io`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt`

**Change:**
1. Add `private val dispatcherProvider: DispatcherProvider` to the constructor.
2. Switch `init { viewModelScope.launch { ... } }` to `viewModelScope.launch(dispatcherProvider.io) { ... }`.
3. Also move `loadCurrentUserProfile`, `updateCurrentUserProfile`, and `getOfflineVisits` to `dispatcherProvider.io`.
4. Keep `StateFlow` emissions as they are (they are main-safe by design).

**Conflict risk:** Low — single file, additive constructor parameter only.

---

## Task 3 — Run `TakeCourseViewModel.loadCourse` off the main thread

**Why:** `loadCourse` fetches a course, its steps, and progress synchronously from several repositories on `Dispatchers.Main`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`

**Change:**
1. Add `DispatcherProvider` to the constructor.
2. Wrap the body of `loadCourse` in `viewModelScope.launch(dispatcherProvider.io) { ... }`.
3. Keep `_uiState.value = ...` emissions inside the `io` coroutine (StateFlow emission is fine from any thread).

**Conflict risk:** Low — localized to one ViewModel.

---

## Task 4 — Move `NotificationsViewModel` load / mark / delete operations to IO

**Why:** `loadNotifications` performs many repository queries, filters, and `formatNotification` string work on the main thread; mark/read/delete methods write to the DB without an injected dispatcher.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`

**Change:**
1. Add `DispatcherProvider` to the constructor.
2. Move `loadNotifications`, `markSelectedAsRead`, `deleteSelected`, and `markAsRead` to `viewModelScope.launch(dispatcherProvider.io)`.
3. Keep StateFlow updates as-is.

**Conflict risk:** Low — one ViewModel, no UI layout changes.

---

## Task 5 — Move heavy `DashboardViewModel` work off the main thread

**Why:** `loadUserContent` and `evaluateChallengeDialog` run blocking repository calls and collection aggregation on `Dispatchers.Main` even though `DispatcherProvider` is already injected.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt`

**Change:**
1. In `loadUserContent`, wrap `resourcesRepository.getMyLibrary(userId)` and `userRepository.getDashboardProfile(userId)` in `withContext(dispatcherProvider.io)`.
2. In `loadUsers`, wrap `userRepository.getUsersSortedBy` in `withContext(dispatcherProvider.io)`.
3. In `evaluateChallengeDialog`, launch the `coroutineScope { async { ... } }` block with `viewModelScope.launch(dispatcherProvider.io) { ... }`.

**Conflict risk:** Low — uses existing `DispatcherProvider`, no new flows.

---

## Task 6 — Inject `DispatcherProvider` and move `BellDashboardViewModel` work to IO

**Why:** `init`, `handleDueReminders`, `checkPendingSurveys`, and `loadCompletedCourses` perform repository queries and list grouping on `Dispatchers.Main`.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt`

**Change:**
1. Add `DispatcherProvider` to the constructor.
2. Move the reminder-collection processing (`handleDueReminders`) to `dispatcherProvider.io`.
3. Move `checkPendingSurveys` and `loadCompletedCourses` to `dispatcherProvider.io`.
4. Keep the `isNetworkConnectedFlow` collector on the main thread (it only updates a small state object).

**Conflict risk:** Low — single ViewModel.

---

## Task 7 — Wrap heavy repository aggregation calls with `withContext(dispatcherProvider.io)`

**Why:** Several repository methods run list grouping / mapping after Room returns on the main dispatcher; wrapping them at the source protects every caller.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`

**Change:**
1. `ProgressRepositoryImpl.getCourseProgress` — wrap the body in `withContext(dispatcherProvider.io)` (it already injects `dispatcherProvider`).
2. `ActivitiesRepositoryImpl` — inject `DispatcherProvider` and wrap `getMostOpenedResource` (the `groupBy` / `mapValues` / `maxByOrNull` chain) in `withContext(dispatcherProvider.io)`.
3. `CoursesRepositoryImpl.getCourseById` and `getCourseSteps` — wrap each body in `withContext(dispatcherProvider.io)`.

**Conflict risk:** Low — mechanical dispatcher wrapping, no public interface changes.

---

## Task 8 — Replace `notifyDataSetChanged()` in `SurveyFragment` with a payload-based update

**Why:** `SurveyFragment` currently calls `getAdapter().notifyDataSetChanged()` when `surveyInfoMap` / `bindingDataMap` change, forcing a full rebind of every visible survey row.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveyFragment.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt`

**Change:**
1. In `SurveysAdapter`:
   - Add a payload constant (e.g. `PAYLOAD_MAPS_CHANGED`).
   - Add `fun updateMaps(surveyInfoMap: Map<String, SurveyInfo>, bindingDataMap: Map<String, SurveyFormState>)` that assigns the new maps and calls `notifyItemRangeChanged(0, itemCount, PAYLOAD_MAPS_CHANGED)`.
   - Override `onBindViewHolder(holder, position, payloads)` and, when the payload is present, only rebind the map-dependent fields (button text, submission/date texts, visibility).
2. In `SurveyFragment`, replace the two `getAdapter().notifyDataSetChanged()` calls in `setupObservers` with `(getAdapter() as SurveysAdapter).updateMaps(...)`.

**Conflict risk:** Medium-low — two files, but the change is localized to the survey list and removes a known anti-pattern.

---

## Task 9 — Release old `ExoPlayer` instances before reassignment in `ResourceViewerFragment`

**Why:** `prepareVideoPlayer`, `streamVideoFromUrl`, and `initializeAudioPlayer` all assign a new `ExoPlayer` without releasing the previous one, leaking media codecs / surface resources; the audio-record listener also uses the wrong lifecycle scope.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`

**Change:**
1. Before each `exoPlayer = createExoPlayer()` / `exoPlayer = ExoPlayer.Builder(...).build()` call, do:
   ```kotlin
   exoPlayer?.release()
   exoPlayer = null
   ```
2. In `onDestroyView`, keep the existing `exoPlayer?.release()` logic.
3. Switch the `OnAudioRecordListener.onRecordStopped` coroutine from `lifecycleScope.launch` to `viewLifecycleOwner.lifecycleScope.launch` (or `lifecycleScope.launch(dispatcherProvider.io)` if the `ViewModel` call is blocking).

**Conflict risk:** Low — one file, no public API changes.

---

## Task 10 — Replace remaining `lifecycleScope.launch` uses in Fragments with `viewLifecycleOwner.lifecycleScope.launch`

**Why:** `CODE_STYLE_GUIDE.md` states fragments should use `viewLifecycleOwner.lifecycleScope`; the fragment `lifecycleScope` can outlive the view and cause binding NPEs.

**Files:**
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailFragment.kt` (line 284)
- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt` (lines 135, 841)
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt` (line 544)
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt` (lines 88, 267)
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamCalendarFragment.kt` (line 320)
- `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt` (line 397)
- `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt` (line 200)
- `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt` (line 164)
- `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt` (line 120)

**Change:**
1. Replace each `lifecycleScope.launch { ... }` with `viewLifecycleOwner.lifecycleScope.launch { ... }`.
2. Skip `ExamTakingFragment.kt:843` — it intentionally launches a `NonCancellable` save after `onDestroyView()` and must keep `lifecycleScope`.
3. No other logic changes; do not add new dispatchers here.

**Conflict risk:** Low — one-line scope swaps across unrelated fragments.

---

## Suggested review order

Day 1-2: Threading quick wins (Tasks 2-6) — these are the safest, highest-impact changes.
Day 3: Repository wrapping (Task 7) and DI cleanup (Task 1) — related to data layer.
Day 4: RecyclerView / media leaks (Tasks 8-9).
Day 5: Fragment lifecycle scope sweep (Task 10) — mechanical and easy to verify.

Each task should pass `./gradlew assembleDefaultDebug` and `./gradlew testDefaultDebugUnitTest` before PR.
