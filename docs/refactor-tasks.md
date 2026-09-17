# myPlanet refactor round — repository-boundary work orders

Plan-only work orders for coding agents. Each task is independently mergeable in any order. No file appears in more than one task.

| Meta | Value |
|------|--------|
| **Focus** | Reinforce repository boundaries; stop cross-feature data leaks; move data work out of UI/services into repositories; Room/DAO query hygiene; smoother repository ↔ ViewModel relationships |
| **Hard blocks this round** | `di/RepositoryModule.kt` and `data/room/AppDatabase.kt` were offlimit when these tasks were written → **no new `@Binds`**, **no schema/version/index bumps**. Prefer constructor `@Inject` on existing types, package moves, or new concrete `@Inject` helpers only. |
| **Open PRs (at generation time)** | 36 open PRs were checked; touched paths were treated as off-limits. Re-check open PRs before starting a task. |

Roadmap tags: **1** data layer · **3** ViewModel/use-case · **7** performance · **9** KMP-ready core · **10** Compose-portable UI state.

---

## Task 1 — Stop static `NetworkUtils.getDeviceName()` in Personals upload serialization

**Roadmap:** **1** (data-layer cleanup). Also **9** (drops an Android-tied static from repository code).

### Problem

`PersonalsRepositoryImpl.serialize` already injects `DeviceNameProvider` for `customDeviceName`, but still calls `NetworkUtils.getDeviceName()` for `deviceName`, leaking a static utility (and device API surface) into the repository path.

### Goal

Use only `DeviceNameProvider` (or its existing device-name API) for both JSON fields so serialization no longer depends on `NetworkUtils`.

### Files (change only these)

- `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`

### Steps

1. In `serialize`, replace `NetworkUtils.getDeviceName()` with the injected `deviceNameProvider` equivalent (same value the rest of the app uses for device name).
2. Remove the unused `NetworkUtils` import.
3. Adjust/extend unit tests that assert serialized upload JSON so both `deviceName` and `customDeviceName` come from the mocked provider.

### Acceptance

- No `NetworkUtils` reference in `PersonalsRepositoryImpl`.
- Upload serialization still emits `deviceName` and `customDeviceName`.
- Existing personal upload behavior unchanged; tests green.

### Out of scope

- `PersonalsRepository` interface, UI, `DeviceNameProvider` implementation, DI modules.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.PersonalsRepositoryImplTest"
```

---

## Task 2 — Move `LifeCache` out of the repository package

**Roadmap:** **1**. Also **9** (repository package no longer hosts `SharedPreferences`).

### Problem

`LifeCache` lives under `repository/` but is a `@Singleton` SharedPreferences-backed cache, not a domain repository. That keeps `android.content.SharedPreferences` in the repository package and blurs layer boundaries.

### Goal

Relocate `LifeCache` to a data/cache package and update the single production consumer import. Keep constructor/`@Inject`/`@AppPreferences` behavior identical so **no** `RepositoryModule` change is required.

### Files

- `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt` → move to  
  `app/src/main/java/org/ole/planet/myplanet/data/cache/LifeCache.kt` (update `package`)
- `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` (import only)
- `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt` → move/update package to match (e.g. `...data.cache`)

### Steps

1. Move file; set `package org.ole.planet.myplanet.data.cache`.
2. Update `LifeRepositoryImpl` import of `LifeCache`.
3. Update test package/imports; assert cache hit/miss behavior unchanged.

### Acceptance

- No `LifeCache` type under `repository/`.
- Hilt still constructs `LifeCache` without module edits.
- `LifeRepositoryImpl` behavior unchanged.

### Out of scope

- Rewriting prefs API, `LifeRepository` methods, `LifeViewModel`, abstracting `SharedPreferences` behind a new bound interface (would need offlimit `RepositoryModule`).

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.cache.LifeCacheTest"
```

(or updated FQCN after the move)

---

## Task 3 — Peel `Context` off `DictionaryFileReaderImpl`

**Roadmap:** **1**. Also **9**.

### Problem

`DictionaryFileReaderImpl` takes `@ApplicationContext Context` and calls `FileUtils.checkFileExist(context, …)` / `getSDPathFromUrl(context, …)`. `StoragePathResolver` already centralizes those path operations.

### Goal

Inject `StoragePathResolver` (already `@Inject`) instead of `Context`. Keep `DictionaryFileReader` interface and Hilt bind target class name stable so **no** module edit is required.

### Files

- `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryFileReader.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`

### Steps

1. Replace `@ApplicationContext Context` with `StoragePathResolver`.
2. Implement `exists()` / `readText()` via `resolveFileFromUrl(Constants.DICTIONARY_URL)` (and existing `FileUtils.getStringFromFile` on the resolved `File` if still needed).
3. Update dictionary repository tests’ reader mocks/fakes if they construct the impl; interface-based tests should stay green.

### Acceptance

- No `android.content.Context` import in `DictionaryFileReader.kt`.
- Dictionary load still reads the same on-disk dictionary URL path.
- No changes to `RepositoryModule` or `DictionaryRepository` interface.

### Out of scope

- Moving reader out of `repository/` package (bind import would touch offlimit module).
- Changing `Constants.DICTIONARY_URL` or download pipeline.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest"
```

---

## Task 4 — Split PDF I/O out of `SubmissionsRepositoryExporter` data assembly

**Roadmap:** **1**. Also **9**.

### Problem

`SubmissionsRepositoryExporter` (repository package) mixes Room DAO assembly with Android PDF APIs (`PdfDocument`, `Paint`, `Canvas`, `Environment`, `Context`). That blocks a platform-free submissions core.

### Goal

Keep **data assembly** (load submission, answers, exam name, question lines) in the exporter; move **PDF rendering + file write** to a new concrete `@Inject` helper under `services/` (or `utils/pdf/`). Do **not** edit offlimit `SubmissionsRepositoryImpl` / submission DAOs — only the exporter’s internals and tests. Public method can keep the same signature so existing callers compile unchanged.

### Files

- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`
- **New:** `app/src/main/java/org/ole/planet/myplanet/services/SubmissionPdfWriter.kt` (name may vary; must be a new path not claimed by an open PR)
- `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt`

### Steps

1. Introduce a small pure model (e.g. title, status lines, Q&A lines) built only from DAOs/`TimeProvider`.
2. Move `PdfDocument` drawing and output `File` creation into `SubmissionPdfWriter`.
3. Exporter: assemble model → delegate write; delete Android graphics imports from exporter if possible.
4. Update tests to cover assembly and/or writer with temp files.

### Acceptance

- Exporter no longer imports `PdfDocument` / `Canvas` / `Paint` (writer owns them).
- `generateSubmissionPdf` still returns a `File?` with equivalent content for the same submission id.
- No edits to `SubmissionsRepositoryImpl`, `SubmissionDao`, `ExamDao`, or `RepositoryModule`.

### Out of scope

- Full KMP PDF; changing call sites in offlimit files; UI download flow.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.SubmissionsRepositoryExporterTest"
```

---

## Task 5 — Remove `MainApplication` statics from `ResourcesRepositoryImpl`

**Roadmap:** **1**. Also **9**.

### Problem

`ResourcesRepositoryImpl` uses `MainApplication.context.getExternalFilesDir` and `MainApplication.applicationScope.launch`, plus `@ApplicationContext`/`R.string` in places — classic Android globals inside a domain repository.

### Goal

Surgical removal of **`MainApplication`** usages only (stay ≪150 lines):

- Offline/html path checks via injected `StoragePathResolver` (add constructor param; Hilt needs no module).
- Replace `MainApplication.applicationScope` with existing injected `DispatcherProvider` + structured suspend/`withContext`, or inject `@ApplicationScope CoroutineScope` if that qualifier is already available without a new bind.

Do **not** redesign the whole class.

### Files

- `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`

### Steps

1. Locate every `MainApplication` reference; replace path access with `storagePathResolver.resolveOleDirectory()` / path joins equivalent to `ole/$resourceId`.
2. Replace applicationScope launch with suspend + `dispatcherProvider` (or `@ApplicationScope CoroutineScope`).
3. Update tests that mock filesystem/scope assumptions.

### Acceptance

- Zero `MainApplication` imports/usages in `ResourcesRepositoryImpl`.
- Offline reconcile/download helpers still resolve under the same ole external-files tree.
- Prefer keeping `ResourcesRepository` signatures unchanged unless a fire-and-forget call must become suspend (then update only this impl + its test).

### Out of scope

- Stripping all `Context`/`R.string` in one go; Tags/Teams/Activities cross-deps; `MyLibraryDao` schema; `RepositoryModule`.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.ResourcesRepositoryImplTest"
```

---

## Task 6 — Fix nullable `userId` SQL predicates on `NotificationDao`

**Roadmap:** **7** (performance/correctness hotspot). Also **1** (Room convention).

### Problem

Project convention: use SQL `IS` for nullable bind params so `NULL` matches. `NotificationDao` is inconsistent — e.g. some reads use `userId = :userId` while others use `userId IS :userId`.

### Goal

Normalize **all** `userId` comparisons in this DAO to `IS` where the Kotlin param is nullable or null must match. No new indices (`AppDatabase` offlimit). No entity changes.

### Files

- `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`
- **Optional new:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NotificationDaoTest.kt`

Do **not** edit `NotificationsRepositoryImplTest.kt` (owned by Task 7).

### Steps

1. Audit every `@Query` in `NotificationDao` for `userId`.
2. Change `userId = :userId` → `userId IS :userId` where the Kotlin param is `String?` or null must match.
3. Leave non-nullable filters alone.
4. Prefer a focused `NotificationDaoTest` for null vs non-null `userId` unread counts / list filters.

### Acceptance

- Nullable `userId` predicates use `IS` consistently.
- Unread counts and list filters still correct for normal string ids.
- No `AppDatabase` version bump.

### Out of scope

- `NotificationsRepository` API, ViewModel formatting, TeamTaskDao usage.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.NotificationDaoTest"
```

(or existing suite paths that exercise notification DAO queries)

---

## Task 7 — Push notification display formatting from ViewModel into repository DTOs

**Roadmap:** **3** (ViewModel/use-case). Also **1**. Soft **10** (VM drops `Context`/`R.string` for list mapping).

### Problem

`NotificationsViewModel` already calls `getEnrichedNotifications`, then re-applies Android `Context` + `R.string` in `formatNotification` to build UI `Notification` models. Display-string assembly and type resolution belong closer to the notifications boundary (repo already has `resolveType`).

### Goal

Extend `NotificationsRepository` / `EnrichedNotifications` (or a new small DTO) so the VM maps **ready-to-bind** fields without `@ApplicationContext` or `R.string`. Keep grouping/selection UI state in the VM.

### Files

- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt`
- `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`

### Steps

1. Identify strings currently formatted in the VM (`formatNotification` and helpers).
2. Produce those strings (or stable message keys + already-localized text) from the repository side so the VM can drop Context. Prefer removing Context from the VM entirely.
3. VM: map DTO → `Notification` list; delete Context field if unused.
4. Tests: repo covers formatting edge cases (task/join/team); VM covers grouping/selection only.

### Acceptance

- `NotificationsViewModel` has no `@ApplicationContext` / no `R.string` for notification body/title formatting.
- List UI parity for known types in `NotificationsRepository.KNOWN_TYPES`.
- No new Hilt modules; no DAO edits (Task 6 owns the DAO).

### Out of scope

- Removing `TeamTaskDao` / `VoicesRepository` cross-feature deps (needs offlimit Teams/Voices impls).
- Adapter/UI layout changes.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.NotificationsRepositoryImplTest"
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest"
```

---

## Task 8 — Introduce `PublicSurveyViewModel` for dual-repository orchestration

**Roadmap:** **3**. Soft **10** (logic leaves Activity for a hoisted VM).

### Problem

`PublicSurveyActivity` injects `SurveysRepository` and `SubmissionsRepository` directly and owns `uploadCompletedSubmission` / `buildPublicAnswers` / respondent sanitization — data orchestration in an Activity with no ViewModel.

### Goal

Add `@HiltViewModel PublicSurveyViewModel` that performs submission fetch, answer payload build, and `submitPublicSurvey` call via **existing** repository methods only (do **not** edit offlimit `SurveysRepository` / `SubmissionsRepository` interfaces or impls). Activity keeps navigation, toasts, fragment hosting.

### Files

- `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyActivity.kt`
- **New:** `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt`
- **New:** `app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt`

### Steps

1. Move `buildPublicAnswers`, `sanitizeRespondent`, and upload orchestration into the VM; expose a single `submitIfCompleted(surveyId, baseUrl, teamId, launchTime): Boolean` (or sealed result).
2. Activity: `by viewModels()`, call VM from `uploadCompletedSubmission`, keep Toast/navigation.
3. Unit-test VM with MockK repositories (success, missing submission, stale `lastUpdateTime`, malformed user JSON).

### Acceptance

- Activity no longer calls repository methods for upload/answer build.
- Behavior parity with current upload + navigate flow.
- No offlimit survey/submission interface changes; no new dependencies.

### Out of scope

- `ExamTakingFragment`, login/dashboard intents redesign, Compose migration of the screen.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.surveys.PublicSurveyViewModelTest"
```

---

## Task 9 — Extract course-step resource orchestration out of `CoursesStepsViewModel`

**Roadmap:** **3**. Also **1**. Soft **10** (path/`Context` work leaves the VM).

### Problem

`CoursesStepsViewModel` depends on multiple repositories and uses `Context` for external-files markdown base paths and multi-step download orchestration — cross-feature workflow embedded in the VM.

### Goal

Introduce a concrete `@Inject` helper (e.g. `CourseStepResourceCoordinator`) that owns: resolve offline markdown/base path, trigger `resourcesRepository` downloads for step resources, and any configurations lookups needed for URLs. VM keeps UI state (`StateFlow`) and course-step selection only. **Do not** edit offlimit `CoursesRepository` / `ProgressRepository` interfaces/impls — helper depends on existing APIs.

### Files

- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt`
- **New:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepResourceCoordinator.kt`  
  (or under `services/` if it must touch paths via `StoragePathResolver`; avoid Android UI types)

### Steps

1. Move Context/path + download sequencing into the coordinator; inject `StoragePathResolver` instead of raw Context where possible.
2. VM constructor: replace direct Resources/Configurations (and Context if present) with the coordinator **or** keep Courses/Progress/User and only drop Resources/Configurations/Context.
3. Update ViewModel tests to mock the coordinator.

### Acceptance

- VM no longer holds `@ApplicationContext` / raw external-files path logic.
- Step resource download/markdown base path behavior unchanged.
- No offlimit course/progress file edits; no `RepositoryModule` changes.

### Out of scope

- Merging into `ResourcesRepositoryImpl` (owned by Task 5); full take-course flow; Compose.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.CoursesStepsViewModelTest"
```

---

## Task 10 — Extract meetup loading from `CalendarViewModel` multi-repository fan-out

**Roadmap:** **3**. Soft **9** (orchestration becomes a testable, non-UI helper).

### Problem

`CalendarViewModel` coordinates `UserRepository` + `TeamsRepository` + `EventsRepository` to load meetups for the calendar. Cross-feature fan-out sits in the VM; if Events/Teams impls are offlimit, interfaces cannot grow — a dedicated loader helper is the safe boundary fix.

### Goal

Add `@Inject class CalendarMeetupsLoader` (name flexible) that takes the three existing repository interfaces and exposes one suspend method used by the VM (e.g. load meetups for current user / date range as today’s VM already does). VM only maps results to UI state.

### Files

- `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModelTest.kt`
- **New:** `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarMeetupsLoader.kt`

### Steps

1. Read current `CalendarViewModel` load path; move sequential/parallel repo calls into the loader unchanged.
2. VM injects loader only (or loader + nothing else for data).
3. Tests mock loader or repositories at loader level; VM tests assert state updates.

### Acceptance

- VM does not call more than one data collaborator for meetup fetch (the loader).
- Same meetup list/empty/error behavior as before.
- No edits to offlimit `EventsRepository*` / `TeamsRepository*`.

### Out of scope

- Team calendar (`TeamCalendarViewModel` is separate).
- Adding methods on Events/Teams repositories.
- UI calendar rendering.

### Test plan

```bash
./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.calendar.CalendarViewModelTest"
```

---

## File ownership matrix (no overlaps)

| Task | Files |
|------|--------|
| 1 | `PersonalsRepositoryImpl.kt`, `PersonalsRepositoryImplTest.kt` |
| 2 | `LifeCache.kt` (move), `LifeRepositoryImpl.kt`, `LifeCacheTest.kt` (move) |
| 3 | `DictionaryFileReader.kt`, `DictionaryRepositoryImplTest.kt` |
| 4 | `SubmissionsRepositoryExporter.kt`, `SubmissionPdfWriter.kt` (new), `SubmissionsRepositoryExporterTest.kt` |
| 5 | `ResourcesRepositoryImpl.kt`, `ResourcesRepositoryImplTest.kt` |
| 6 | `NotificationDao.kt`, optional new `NotificationDaoTest.kt` |
| 7 | `NotificationsRepository.kt`, `NotificationsRepositoryImpl.kt`, `NotificationsViewModel.kt`, `NotificationsViewModelTest.kt`, `NotificationsRepositoryImplTest.kt` |
| 8 | `PublicSurveyActivity.kt`, `PublicSurveyViewModel.kt` (new), `PublicSurveyViewModelTest.kt` (new) |
| 9 | `CoursesStepsViewModel.kt`, `CoursesStepsViewModelTest.kt`, `CourseStepResourceCoordinator.kt` (new) |
| 10 | `CalendarViewModel.kt`, `CalendarViewModelTest.kt`, `CalendarMeetupsLoader.kt` (new) |

## Constraints for agents

- Under ~150 changed lines and ~5 files per task
- No new Gradle dependencies
- No unused code, no TODO placeholders
- Re-check open PRs before editing; skip any path another open PR already owns
- Do not touch `RepositoryModule.kt` or `AppDatabase.kt` unless offlimit status has changed and the task truly requires it
