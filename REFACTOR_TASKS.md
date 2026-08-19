# myPlanet Refactor Round — 10 Reviewable Tasks

Focus: reinforce repository boundaries, call out cross-feature data leaks, tighten
repository interfaces, and move data functions one by one out of UI/services into
repositories. Every task is small, single-purpose, and touches a file set disjoint
from the others wherever possible (conflict notes call out the exceptions).

Already clean (checked, nothing to do this round):
- `DispatcherProvider` discipline: zero hardcoded `Dispatchers.*` outside the provider itself; zero `runBlocking`/`GlobalScope`.
- DiffUtil adoption: 41 adapters already use `ListAdapter` + `DiffUtils.itemCallback`; zero `RecyclerView.Adapter` subclasses remain. Only `SurveyFragment` still calls `notifyDataSetChanged()` (task 3).
- Broadcast receivers: wrapped in `BroadcastService` flows + `collectWhenStarted`/`repeatOnLifecycle` everywhere that matters.
- DI: no concrete `*RepositoryImpl` injected in UI or services; all bindings go through interfaces.

## Suggested landing order (merge-conflict avoidance)

| Wave | Tasks | Why |
|------|-------|-----|
| 1 | 1, 2, 3, 8, 10 | Fully disjoint file sets — merge in any order |
| 2 | 4, 9 | Disjoint from each other and from wave 1 |
| 3 | 6, then 5, then 7 | All three touch `TeamsRepositoryImpl`; 5 and 7 also touch `di/RepositoryModule.kt`. Land strictly in this order, rebasing between each |

---

## Task 1 — Move exam-start orchestration out of `ExamTakingFragment`

**Roadmap item:** 1 (data layer) / 3 (ViewModel layer)
**Files:** `ui/exam/ExamTakingFragment.kt`, `repository/SubmissionsRepository.kt`, `repository/SubmissionsRepositoryImpl.kt`

The fragment hand-orchestrates exam session startup: it fetches pending
submissions, deletes stale ones, creates a new submission, and on failure retries
the delete+create loop (~lines 100–175, plus the `getSubmissionsByParentId(...,
"pending")` + `saveExamAnswer` flow around line 685). That is repository logic
living in UI.

**Do:** add one function to `SubmissionsRepository`, e.g.
`suspend fun startExamSession(examId, parentId, userId): Submission` that owns the
pending-check → delete-stale → create → retry sequence. Fragment calls the one
function. No new cross-feature dependencies: `SubmissionsRepositoryImpl` already
has everything it needs.

**Conflicts with:** nothing else in this round.

---

## Task 2 — Move step-prerequisite assembly out of `CourseStepFragment`

**Roadmap item:** 1 / 3
**Files:** `ui/courses/CourseStepFragment.kt`, `repository/CoursesRepository.kt`, `repository/CoursesRepositoryImpl.kt`

The fragment injects six repositories and composes per-step prerequisites inline:
`coursesRepository.isMyCourse(...)`, `submissionsRepository.hasSubmission(...,
"exam")`, `hasSubmission(..., "survey")`, `getCourseTitleById(...)` (~lines 102,
115, 243, 253, 270). That is a data-layer query assembled in the UI.

**Do:** add one function to `CoursesRepository`, e.g.
`suspend fun getStepPrerequisites(stepId, courseId, userId): StepPrerequisites`
(a small data class: isMyCourse / hasExam / hasSurvey / courseTitle).
`CoursesRepositoryImpl` already injects `submissionsRepository` and
`userRepository`, so no new cross-feature wiring is introduced. The fragment drops
its direct `SubmissionsRepository` dependency.

**Conflicts with:** nothing else in this round (CourseStepFragment ≠
CoursesFragment/TakeCourseFragment).

---

## Task 3 — Surveys: delete the last `notifyDataSetChanged()` calls

**Roadmap item:** 6 (UI) / DiffUtil-ListAdapter hygiene
**Files:** `ui/surveys/SurveyFragment.kt`, `ui/surveys/SurveysAdapter.kt`, `ui/surveys/SurveysViewModel.kt`

The last two `notifyDataSetChanged()` calls in the app (lines 180, 185) exist
because the adapter renders from two fragment-held side maps (`surveyInfoMap`,
`bindingDataMap`) fed by separate ViewModel flows, while the list itself goes
through `submitList`. Rendering data bypasses DiffUtil entirely.

**Do:** combine the three flows into one UI-state flow in `SurveysViewModel`
(e.g. `data class SurveyRow(survey, info, bindingData)` via `combine`), submit the
combined list through the existing `ListAdapter` + `DiffUtils.itemCallback`, and
delete both `notifyDataSetChanged()` calls and the fragment-held maps.

**Conflicts with:** nothing else in this round.

---

## Task 4 — Put `DictionaryActivity` behind a `DictionaryRepository`

**Roadmap item:** 1 (data layer) / 4 (DI)
**Files:** `repository/DictionaryRepository.kt` (new),
`repository/DictionaryRepositoryImpl.kt` (new), `di/RepositoryModule.kt`,
`ui/dictionary/DictionaryActivity.kt`

`DictionaryActivity` is the only UI class that injects a Room DAO
(`DictionaryDao`) directly — the last hole in the UI→repository→DAO boundary.

**Do:** create the standard interface + Impl pair wrapping the DAO's functions
(no logic change, pure pass-through), add one `@Binds` in `RepositoryModule`, and
swap the activity's injected type. Smallest possible PR that closes the boundary.

**Conflicts with:** tasks 5 and 7 on `di/RepositoryModule.kt` — land this one
first among the DI-touching tasks (it is the smallest).

---

## Task 5 — Narrow the `NotificationsRepositoryImpl` → `TeamsRepository` dependency

**Roadmap item:** 1 / 4 — tighten repository interfaces
**Files:** `repository/NotificationsRepositoryImpl.kt`, `repository/TeamsRepository.kt`, `repository/TeamsRepositoryImpl.kt`, one new interface file, `di/RepositoryModule.kt`

`NotificationsRepositoryImpl` injects the whole `TeamsRepository` (Lazy) but only
ever calls four lookup helpers: `getTeamLabelInfo`, `getJoinRequestInfo`,
`getTeamNamesByIds`, `getJoinRequestsInfo` (9 call sites, lines ~160–296). This is
a cross-feature leak: notifications depends on the entire 60-function teams
surface to render labels.

**Do:** extract a narrow interface, e.g. `TeamsInfoLookup`, with exactly those
four read-only functions; have `TeamsRepository` extend it (implementations
already exist, so `TeamsRepositoryImpl` gains only the interface in its
supertype list — no moved code); rebind `NotificationsRepositoryImpl` to depend
on `TeamsInfoLookup`. One new `@Binds` entry.

**Conflicts with:** task 6 and 7 on `TeamsRepository*` files, task 4 on
`di/RepositoryModule.kt`. Land after 6, before 7.

---

## Task 6 — Stop fragments from calling `TeamsSyncRepository` directly

**Roadmap item:** 5 (sync workflow) / boundary reinforcement
**Files:** `ui/teams/TeamDetailFragment.kt`, `ui/teams/resources/TeamResourcesFragment.kt`, `repository/TeamsRepository.kt`, `repository/TeamsRepositoryImpl.kt`

`TeamsSyncRepository` is documented as a narrow interface *for the sync
managers*, yet two fragments inject it and call `syncTeamActivities()` after
join/leave/resource operations (`TeamDetailFragment:279`,
`TeamResourcesFragment:123,191`). UI should never trigger sync-manager internals.

**Do:** expose one pass-through on `TeamsRepository` (e.g. `recordTeamActivity()`
delegating to the sync repository inside the Impl), switch the three call sites,
and drop the `TeamsSyncRepository` injections from both fragments. ViewModel
usages (`TeamViewModel`, `RequestsViewModel`) can stay for a later round — keep
this PR fragment-only so it stays tiny.

**Conflicts with:** task 5 and 7 on `TeamsRepository*` — land this one first of
the three; the diff is ~15 lines.

---

## Task 7 — Extract enterprise reports out of `TeamsRepositoryImpl`

**Roadmap item:** 1 (finish data layer) / cross-feature leak fix
**Files:** `repository/TeamsRepository.kt`, `repository/TeamsRepositoryImpl.kt`,
`repository/EnterprisesRepository.kt` (new), `repository/EnterprisesRepositoryImpl.kt` (new), `di/RepositoryModule.kt`,
`ui/enterprises/EnterprisesReportsFragment.kt` (+ `EnterprisesViewModel.kt` if it
exposes them)

`TeamsRepositoryImpl` is the largest file in the app (~1437 lines). Its
reports/finances cluster — `addReport`, `updateReport`, `archiveReport`,
`getReportsFlow`, `exportReportsAsCsv` — is consumed *only* by the enterprises
feature. The enterprises UI currently depends on the whole teams repository for
five functions.

**Do:** move the five report functions into a new `EnterprisesRepository` pair
(the Impl keeps using the same DAOs it already reaches via teams code) and repoint
the enterprises consumers. Pure mechanical move, single consumer feature.
Scope note: leave the transactions cluster (`getTeamTransactionsWithBalance`,
`createTransaction`) for a follow-up round so this diff stays reviewable.

**Conflicts with:** task 5 and 6 on `TeamsRepository*`, task 4 on
`di/RepositoryModule.kt`. Land last.

---

## Task 8 — Move examination-conditions loading out of `HealthExaminationActivity`

**Roadmap item:** 3 (ViewModel layer) / long-running observer fix
**Files:** `ui/health/HealthExaminationActivity.kt` + its ViewModel,
`repository/HealthRepository.kt` only if a signature needs adjusting

The activity collects `viewModel.state` and, *inside the collector*, launches a
nested coroutine calling `healthRepository.getExaminationConditions(examination)`
on every emission (~line 102–110) — a data call in UI plus a launch-per-emission
smell that duplicates work and can outlive the view interaction it serves.

**Do:** load `conditionsMap` inside the ViewModel as part of the state (or a
derived flow), so the collector only reads state. Delete the nested launch. No
repository changes expected beyond what already exists.

**Conflicts with:** nothing else in this round.

---

## Task 9 — Lifecycle-aware collection sweep for raw `lifecycleScope.launch { collect }`

**Roadmap item:** 8 (code health) / long-running observers
**Files (only these):** `ui/settings/SettingsActivity.kt`,
`ui/voices/VoicesFragment.kt`, `ui/dashboard/ActivitiesFragment.kt`,
`ui/personals/PersonalsFragment.kt`, `ui/submissions/SubmissionsFragment.kt`,
`ui/feedback/FeedbackDetailActivity.kt`

These UI classes collect flows in plain `lifecycleScope.launch { ... collect {} }`
with no `collectWhenStarted`/`repeatOnLifecycle`, so they keep collecting (and can
touch views) while STOPPED. ViewModels collecting in `viewModelScope` are fine and
are explicitly out of scope.

**Do:** wrap each collector in the existing `collectWhenStarted` helper (already
used across the codebase). Zero logic change, mechanical diff. The file list is
deliberately chosen to avoid every file touched by tasks 1–8 — do not expand it
in this PR; the remaining raw collectors (courses/resources/teams fragments) go in
a follow-up sweep after this round lands.

**Conflicts with:** nothing, given the fixed file list.

---

## Task 10 — Move "add/remove from myLibrary" orchestration out of `ResourceDetailFragment`

**Roadmap item:** 1 / 3
**Files:** `ui/resources/ResourceDetailFragment.kt`,
`repository/ResourcesRepository.kt`, `repository/ResourcesRepositoryImpl.kt`

The fragment repeatedly does the same two-step dance: fetch the user id via
`userRepository.getUserModel()?.id`, then call
`resourcesRepository.updateUserLibrary(id, userId, isAdd)` (lines ~60–66,
~241–247). The "current user id" lookup is data-layer knowledge leaking into UI.

**Do:** add one function to `ResourcesRepository`, e.g.
`suspend fun setUserLibrary(resourceId: String, add: Boolean): Boolean`, which
resolves the current user internally (`ResourcesRepositoryImpl` already injects
`userRepository`) and returns success/failure. Replace both call-site blocks;
fragment keeps only the UI reaction (snackbar/state refresh). The ratings read at
line ~287 stays as-is for this round.

**Conflicts with:** nothing else in this round.

---

## Summary matrix

| # | Task | Files touched | DI change | Conflict zone |
|---|------|---------------|-----------|----------------|
| 1 | Exam-start → SubmissionsRepository | ui/exam, Submissions* | no | — |
| 2 | Step prerequisites → CoursesRepository | ui/courses (step), Courses* | no | — |
| 3 | Surveys notifyDataSetChanged removal | ui/surveys | no | — |
| 4 | DictionaryRepository | ui/dictionary, new repo pair, RepositoryModule | yes | RepositoryModule |
| 5 | Narrow notifications→teams interface | Notifications*, Teams*, new interface, RepositoryModule | yes | Teams*, RepositoryModule |
| 6 | Fragments off TeamsSyncRepository | 2 teams fragments, Teams* | no | Teams* |
| 7 | Enterprises reports extraction | Teams*, new Enterprises pair, ui/enterprises, RepositoryModule | yes | Teams*, RepositoryModule |
| 8 | Health conditions → ViewModel | ui/health | no | — |
| 9 | Lifecycle collection sweep | 6 listed UI files only | no | — |
| 10 | myLibrary toggle → ResourcesRepository | ui/resources (detail), Resources* | no | — |
