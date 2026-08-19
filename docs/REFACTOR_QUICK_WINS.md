# Refactor quick-win review round

This round is deliberately limited to **10 small PRs**—roughly one day's review capacity. The tasks favor measurable performance wins and cleanup that unlocks later data-layer, DI, and ViewModel work without attempting navigation or Compose rewrites. Each task has a separate primary file set, so all 10 can be reviewed in parallel with low merge-conflict risk.

## 1. Stop full survey-list rebinds when supporting data changes

**Roadmap link:** performance, list architecture, ViewModel boundary
**Primary files:** `ui/surveys/SurveyFragment.kt`, `ui/surveys/SurveysAdapter.kt`, and `ui/surveys/SurveysViewModel.kt`

`SurveyFragment` currently mutates two maps owned outside the adapter and calls `notifyDataSetChanged()` whenever survey info or form state changes. Make the displayed row state an immutable UI model assembled by `SurveysViewModel`, submit it through the existing `ListAdapter`, and use `DiffUtils.itemCallback` to compare stable survey IDs and visible row content. Do not retain parallel mutable maps in the adapter.

**Acceptance:** auxiliary-state changes update the correct rows through `submitList`; no `notifyDataSetChanged()` remains in `SurveyFragment`; add a focused comparator/ViewModel test covering a changed count or completion date.
**Conflict guard:** keep this PR entirely inside the surveys feature and land it before any broader survey UI work.

---

## 2. Remove the persistent courses layout listener

**Roadmap link:** performance, long-running listeners
**Primary file:** `ui/courses/CoursesFragment.kt`

The courses recycler registers an anonymous `addOnLayoutChangeListener` to recalculate grid spans for every width change, but the callback cannot be explicitly removed. Replace it with a view-lifecycle-safe width-change mechanism (or retain a named listener and remove it in `onDestroyView`), coalesce posted span updates, and skip work when the calculated span count has not changed.

**Acceptance:** only one listener exists per fragment view; it is detached with that view; repeated same-width layouts do not assign a layout manager or span count again.
**Conflict guard:** do not change `CoursesAdapter`, course sorting, or course data loading in this PR.

---

## 3. Remove the duplicate persistent resources layout listener

**Roadmap link:** performance, long-running listeners
**Primary file:** `ui/resources/ResourcesFragment.kt`

Apply the same narrow lifecycle and span-update optimization to the resources recycler's anonymous layout listener. Avoid sharing a new abstraction with task 2 in this round: two local fixes keep both PRs independently reviewable and prevent a conflict in common utilities.

**Acceptance:** the listener is removed when the fragment view is destroyed; posted callbacks cannot touch a destroyed binding; unchanged widths/spans do no work.
**Conflict guard:** touch neither `ResourcesAdapter` nor repository/search behavior.

---

## 4. Make public-survey FragmentManager listeners disposable

**Roadmap link:** performance, observer/listener lifetime
**Primary file:** `ui/surveys/PublicSurveyActivity.kt`

The activity registers fragment lifecycle callbacks and an anonymous back-stack listener for its full lifetime. Store the back-stack listener as a property, unregister both callbacks during destruction, and preserve the existing upload-on-completion guards so teardown cannot start duplicate uploads.

**Acceptance:** every FragmentManager registration has a symmetric unregister; rotation/finish does not leave an old activity callback; the upload trigger is still invoked at most once.
**Conflict guard:** do not alter survey download, parsing, submission, or repository APIs. This task is independent from task 1 because it touches only the standalone activity.

---

## 5. Dispose bottom-sheet and tab mediator callbacks with the view

**Roadmap link:** performance, long-running listeners
**Primary file:** `ui/community/HomeCommunityDialogFragment.kt`

`HomeCommunityDialogFragment` installs an anonymous `BottomSheetCallback` and creates a `TabLayoutMediator` without retaining either for cleanup. Retain both objects, remove/detach them in `onDestroyView`, clear the ViewPager adapter, and avoid calling `requestLayout()` when the calculated height is already applied.

**Acceptance:** callback and mediator lifetimes match the fragment view; recreating the view does not accumulate callbacks; unchanged slide-derived heights do not request another layout.
**Conflict guard:** no changes to community repositories, pager pages, or bottom-sheet behavior thresholds.

---

## 6. Collect examination state only while the activity is started

**Roadmap link:** ViewModels, threading, long-running observers
**Primary file:** `ui/health/HealthExaminationActivity.kt`

The activity collects `viewModel.state` for its entire lifecycle and launches a new nested coroutine on every non-loading emission. Switch to the project's lifecycle-aware collection helper (or `repeatOnLifecycle`), use `collectLatest`/a single cancellable state-render job, and prevent unchanged examination state from re-querying conditions and rerunning full form initialization.

**Acceptance:** collection stops below `STARTED`; a newer state cancels stale condition loading; one logical state emission causes at most one initialization; add a focused ViewModel/state test if the deduplication belongs in the ViewModel.
**Conflict guard:** do not change health validation rules, persistence, or layouts.

---

## 7. Query a survey fallback by name instead of loading every survey

**Roadmap link:** data-layer cleanup, performance
**Primary files:** `data/room/dao/ExamDao.kt`, `repository/SurveysRepositoryImpl.kt`, and a DAO test

`getSurvey(id)` first queries by ID, then loads every survey exam and performs `firstOrNull` in Kotlin to match the name. Add a narrowly scoped Room query for type plus name (with `LIMIT 1`) and use it for the fallback. Do not add a speculative index in this PR unless a query-plan test demonstrates that it is needed.

**Acceptance:** the fallback returns the same result without materializing all survey rows; DAO tests cover ID hit, name fallback, and no match.
**Conflict guard:** this is the only task in the round that changes `ExamDao`; land independently of task 1.

---

## 8. Fetch eligible next-leader memberships in one DAO query

**Roadmap link:** data-layer cleanup, performance
**Primary files:** `data/room/dao/TeamDao.kt`, the `getNextLeaderCandidate` path in `repository/TeamsRepositoryImpl.kt`, and a DAO test

The next-leader path loads all team memberships and filters leaders, archived records, and an excluded user in memory. Add a purpose-specific DAO query that applies those predicates in SQL. Keep the existing candidate-ranking behavior unchanged; this PR should reduce the input set, not redesign leader selection.

**Acceptance:** archived/current-leader/excluded memberships never leave Room; null exclusion retains eligible members; existing ranking output is unchanged and covered by tests.
**Conflict guard:** limit `TeamsRepositoryImpl` edits to this method and avoid team UI/ViewModel files.

---

## 9. Batch next-leader user lookup to remove the N+1 query

**Roadmap link:** data-layer cleanup, repository performance
**Primary files:** `data/room/dao/UserDao.kt` (or its current DAO file), `repository/UserRepository.kt`, `repository/UserRepositoryImpl.kt`, and the same narrow `getNextLeaderCandidate` block in `repository/TeamsRepositoryImpl.kt`

After membership filtering, the next-leader path calls `getUserById` once per member. Add and expose one existing-style `WHERE id IN (...)` batch lookup, then build the same in-memory map/ranking data from that single result. This task should be based on task 8 and reviewed after it, rather than opened against the same pre-task-8 lines.

**Acceptance:** candidate selection executes one user query regardless of candidate count; missing users are ignored as today; ranking tests prove behavior is unchanged.
**Conflict guard:** explicitly stack this PR on task 8; it is the round's only intentional dependency and therefore avoids a parallel edit collision.

---

## 10. Replace `SyncTimeLogger`'s Hilt service locator with constructor injection

**Roadmap link:** DI cleanup, threading/dispatchers, sync groundwork
**Primary files:** `utils/SyncTimeLogger.kt`, its provider in `di/`, and constructor call sites in `SyncRepositoryImpl.kt`, `SyncManager.kt`, and `TransactionSyncManager.kt`

`SyncTimeLogger` is a global object that repeatedly reaches through `EntryPointAccessors` for the clock, dispatchers, preferences, and URL mapping, then launches work in the application scope. Convert it to an injected singleton with explicit dependencies and an injected application scope/dispatcher already defined by project DI. Inject a logger interface into the three sync owners; do not add unused abstractions or change logging output.

**Acceptance:** `SyncTimeLogger` no longer imports `MainApplication`, `EntryPointAccessors`, or `CoreDependenciesEntryPoint`; all async logger work uses injected scope/dispatcher; a unit test can supply a fake clock and dispatcher; sync behavior and log format stay unchanged.
**Conflict guard:** reserve sync/DI files for this PR only; do not combine it with upload consolidation or a SyncManager split.

---

## Suggested merge order

1. Merge tasks **2–7** in any order; their primary files do not overlap.
2. Merge task **8**, then rebase and merge stacked task **9**.
3. Merge task **1** after task 7 if both are active, resolving only the surveys feature boundary if necessary.
4. Merge task **10** last because it touches central sync constructors and DI wiring.

Defer global navigation, Compose migration, SyncManager/UploadManager decomposition, and broad repository/ViewModel rewrites to later rounds. Those are valuable roadmap items, but mixing them into these quick wins would increase review cost and conflict risk without improving this round's performance-per-line ratio.
