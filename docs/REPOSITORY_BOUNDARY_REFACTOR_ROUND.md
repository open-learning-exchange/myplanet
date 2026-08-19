# Repository-boundary refactor round: 10 small PRs

## Goal and review strategy

This round should reinforce the existing `UI -> ViewModel -> repository -> data source` direction without attempting a
large navigation, Compose, sync, or repository rewrite. Each numbered item below is intended to be **one PR**, so the
round fits the roughly ten-PR daily review budget.

The tasks are ordered by boundary value versus implementation risk. They deliberately use different production files
where possible. Open all ten from the same base commit; do not stack them. If a task uncovers a larger redesign, record
it separately rather than expanding that PR.

### Shared rules for every PR

- Keep UI rendering, navigation, dialogs, Android resources, and lifecycle ownership in the UI layer.
- Put database, file parsing, query composition, and persistence behind the narrowest domain repository interface.
- Let a ViewModel translate UI intents into repository calls and expose immutable state/events; do not make a
  repository depend on a ViewModel or Android view.
- Inject repository interfaces, not implementations. Do not add a binding or constructor dependency until it is used.
- Do not add `Dispatchers.*`; use the existing `DispatcherProvider`, and put blocking file/database work on its `io`
  dispatcher at the repository boundary.
- Add focused MockK/coroutines tests for the moved behavior. Avoid broad snapshot or end-to-end test changes.
- If an adapter is touched, retain `ListAdapter` and use `DiffUtils.itemCallback` or
  `DiffUtils.standardItemCallback`; do not reintroduce `notifyDataSetChanged()`.

---

## 1. Put dictionary loading and lookup behind a `DictionaryRepository`

**Current leak:** `ui/dictionary/DictionaryActivity.kt` injects `DictionaryDao`, parses the downloaded JSON, creates
`DictionaryEntity` rows, inserts them, counts them, and performs word lookup. This is the clearest direct
`UI -> DAO` boundary violation in the app. The functions to move one by one are `loadDictionaryCount()`, the
parse/insert portion of `loadDictionaryIfNeeded()`, and the `dictionaryDao.findByWord()` call in `setClickListener()`.

**Small PR shape:**

1. Add a narrow `DictionaryRepository` with `count()`, `importIfEmpty(filePath)` (or an input stream/file abstraction),
   and `findByWord(word)`; implement it with `DictionaryDao`, JSON parsing, and `DispatcherProvider.io`.
2. Add the single Hilt binding in `di/RepositoryModule.kt`.
3. Add `DictionaryViewModel` to own import/search state and errors, and leave download triggering, HTML formatting,
   toasts, and rendering in the activity.
4. Test repository parsing/DAO calls and the ViewModel's empty/imported/search states.

**Keep out:** changing the dictionary schema, download service, file format, or UI design.

**Likely files:** `repository/DictionaryRepository.kt`, `repository/DictionaryRepositoryImpl.kt`,
`ui/dictionary/DictionaryViewModel.kt`, `ui/dictionary/DictionaryActivity.kt`, `di/RepositoryModule.kt`, and focused
tests.

---

## 2. Remove repository and service dependencies from `VoicesAdapter`

**Current cross-layer leak:** `ui/voices/VoicesAdapter.kt` receives both `VoicesRepository` and
`VoicesLabelManager`. It passes the repository into `VoicesActions` for reads/edits/replies and lets row binding initiate
data work. An adapter should only bind `News` row state and emit user intents. This is also the most important adapter
boundary hotspot; its list diff already uses the shared `DiffUtils` callback and should continue to do so.

**Small PR shape:** replace the two data/service constructor arguments with narrow callbacks such as edit, reply,
label-menu, and member-details intents. Handle those intents in `VoicesFragment`/`VoicesViewModel`, where repository
calls already belong, then submit the returned updated `News` through the existing list/state path. If label rendering
needs precomputed data, add it to a small row UI model rather than allowing the adapter to fetch it.

**Acceptance checks:** no `repository` or `services` import remains in `VoicesAdapter`; no coroutine or persistence
operation starts from a view holder; the adapter remains a `ListAdapter` using `DiffUtils.standardItemCallback`; tests
verify callback emission and changed-row submission.

**Keep out:** redesigning the voice feed, splitting the large adapter, changing reply UX, or changing repository APIs
unrelated to the exact adapter calls.

**Likely files:** `ui/voices/VoicesAdapter.kt`, `ui/voices/VoicesFragment.kt`, `ui/voices/VoicesViewModel.kt`, and their
existing tests. Avoid editing `VoicesRepositoryImpl` in this PR.

---

## 3. Finish the `HealthExaminationViewModel` boundary and fix its observer lifetime

**Current leaks:** `ui/health/HealthExaminationActivity.kt` injects `HealthRepository` and
`UserSessionManager` even though it already owns a `HealthExaminationViewModel`. It calls
`healthRepository.getExaminationConditions(examination)` from a nested `lifecycleScope.launch`. It also collects
`viewModel.state` with a plain activity `lifecycleScope`, so the long-running collector remains active while the activity
is stopped.

**Small PR shape:** move current-user loading and examination-condition loading into the existing ViewModel and include
both results in its state. Replace the outer collector and nested launch with the project's lifecycle-aware
`collectLatestWhenStarted`/`collectWhenStarted` helper. The activity should only copy state into fields and render the
form. Keep encryption and form-widget code where it is unless a moved repository function already owns it.

**Acceptance checks:** the activity has no repository injection, no nested coroutine in the state collector, and no
collector that outlives `STARTED`; ViewModel tests cover loading, repository failure, and cancellation/reload behavior.

**Likely files:** `ui/health/HealthExaminationActivity.kt`, `ui/health/HealthExaminationViewModel.kt`, and
`ui/health/HealthExaminationViewModelTest.kt`.

---

## 4. Move AI-provider loading out of `ChatHistoryFragment`

**Current leak:** `ui/chat/ChatHistoryFragment.kt` injects `ChatRepository` solely so its provider-loading coroutine can
call `fetchAiProviders(serverUrl)`, then manually toggles loading/error flags on the shared `ChatViewModel`. This exposes
the repository operation and its state machine to the Fragment.

**Small PR shape:** add one `ChatViewModel.loadAiProviders(serverUrl)` intent that owns the repository call and updates
one immutable provider UI state. Have the Fragment invoke it and render/observe the result. Preserve
`SharedPrefManager` use for now if moving all session configuration would enlarge the patch; this task is specifically
about the repository leak.

**Acceptance checks:** no `ChatRepository` injection/import in `ChatHistoryFragment`; repeated calls either cancel the
previous provider job or are idempotent; tests cover success, empty providers, exception, and retry.

**Likely files:** `ui/chat/ChatHistoryFragment.kt`, `ui/chat/ChatViewModel.kt`, and `ui/chat/ChatViewModelTest.kt`.

---

## 5. Move course-rating eligibility into `TakeCourseViewModel`

**Current cross-feature leak:** `ui/courses/TakeCourseFragment.kt` injects `RatingsRepository` and implements the data
decision in `showCourseRatingDialogAndFinish()`: query a rating summary, inspect `userRating`/`existingRating`, catch
errors, and decide whether to show the ratings feature. The courses UI should ask its ViewModel for an outcome rather
than understand another feature's repository response.

**Small PR shape:** inject `RatingsRepository` into the existing `TakeCourseViewModel` and expose a narrow result such as
`RatingPromptDecision.Show` or `Skip`. Move only the summary query and eligibility/error policy. Keep creation/showing
of `RatingsFragment` and back-stack navigation in the Fragment.

**Interface tightening:** do not expose `RatingSummary` to the Fragment. If the current repository method returns more
than this decision needs, keep that repository interface unchanged for this PR and map it inside the ViewModel.

**Acceptance checks:** no repository injection in `TakeCourseFragment`; tests cover already-rated, unrated, missing
IDs, and repository failure; no navigation object enters the ViewModel.

**Likely files:** `ui/courses/TakeCourseFragment.kt`, `ui/courses/TakeCourseViewModel.kt`, and
`ui/courses/TakeCourseViewModelTest.kt`.

---

## 6. Move collection/tag loading from the Fragment into a ViewModel

**Current leak:** `ui/resources/CollectionsFragment.kt` injects `TagsRepository` and `setListAdapter()` calls
`getTagsWithChildren(dbType)` directly before building parent/child lists. This mixes a repository query and data-shape
preparation into adapter setup.

**Small PR shape:** add a feature-local `CollectionsViewModel` that calls the existing repository and exposes a state
containing parent tags plus the child map. Let the Fragment retain filtering widgets, keyboard behavior, selection, and
adapter binding. Prefer an immutable `Map<String, List<TagEntity>>` in the state rather than mutable `HashMap` output.

**Acceptance checks:** no repository injection in the Fragment; loading/error/empty states are explicit; re-creation
does not launch duplicate loads for the same `dbType`; tests cover parents with and without children.

**Keep out:** changing the repository query, tag schema, selection contract, or the existing dialog API.

**Likely files:** `ui/resources/CollectionsFragment.kt`, new `ui/resources/CollectionsViewModel.kt`, and a focused
ViewModel test. This PR should not touch `ResourcesViewModel` or `ResourcesAdapter`, reducing collision risk.

---

## 7. Put offline-storage list/delete operations behind `SettingsViewModel`

**Current leak:** `ui/settings/StorageCategoryDetailFragment.kt` injects `ResourcesRepository`, derives the OLE path,
calls `getOfflineResourceItems(...)`, and calls `deleteOfflineResources(...)`. A settings UI is thereby coupled to the
resources feature's persistence/file contract, including a raw filesystem path.

**Small PR shape:** add narrowly named operations/state to the existing `SettingsViewModel`, for example
`loadOfflineResources(extensions, allKnownExtensions)` and `deleteOfflineResources(items)`. Resolve the OLE path below
the UI boundary (preferably inside the repository implementation through an injected application-safe path provider;
if that is too broad, resolve it once in the ViewModel as an intermediate step). Expose list, deleting, empty, and
completion state to the Fragment.

**Interface tightening:** the long-term repository method should not accept a UI-derived path. In this PR, change only
the two offline-storage methods and their direct callers; do not redesign every resources method.

**Acceptance checks:** the Fragment has neither `ResourcesRepository` nor `FileUtils` data-path calls; deletion cannot
be launched twice while active; the existing inner adapter remains `ListAdapter` with `DiffUtils.itemCallback`; tests
cover extension filtering inputs and deletion completion.

**Likely files:** `ui/settings/StorageCategoryDetailFragment.kt`, `ui/settings/SettingsViewModel.kt`,
`repository/ResourcesRepository.kt`, `repository/ResourcesRepositoryImpl.kt`, and focused tests.

---

## 8. Route feedback submission through a small composer ViewModel

**Current leak:** `ui/feedback/FeedbackFragment.kt` injects `FeedbackRepository` and `UserSessionManager`, loads the user,
builds persistence arguments, calls `createAndSaveFeedback(...)`, and assumes success before showing a toast and
dismissing. Existing feedback ViewModels cover list/detail, not composition, so reusing them would mix screen scopes.

**Small PR shape:** add a `FeedbackComposerViewModel` that owns current-user lookup and the single submit call and
exposes Idle/Submitting/Saved/Error. Keep radio-button/text validation and dismissal in the Fragment. Use the existing
repository method rather than adding an unused abstraction or broadening `FeedbackRepository`.

**Acceptance checks:** no repository/session injection in the Fragment; the submit button is disabled while active;
dismiss/callback happens only on Saved; errors remain retryable; tests cover missing user, success, and failure.

**Likely files:** `ui/feedback/FeedbackFragment.kt`, new `ui/feedback/FeedbackComposerViewModel.kt`, and its test. Avoid
touching feedback list/detail files so this PR stays independent.

---

## 9. Move activity-history query and monthly aggregation out of `ActivitiesFragment`

**Current leaks:** `ui/dashboard/ActivitiesFragment.kt` injects `ActivitiesRepository` and `UserSessionManager`, queries
offline logins, and implements `computeMonthlyCounts(...)`. The chart should receive display-ready points; it should not
own a cross-feature activity query or temporal data aggregation.

**Small PR shape:** add an `ActivitiesViewModel` that obtains the current user, observes offline logins, and exposes
monthly counts for an explicit time range. Move `computeMonthlyCounts` to the repository only if it can become a useful,
well-named query contract (for example `observeMonthlyLoginCounts(userName, start, end)`); otherwise keep aggregation
as a pure ViewModel/use-layer mapper rather than polluting the repository with chart concepts. The Fragment retains
colors, `BarEntry`, labels, and chart rendering.

**Threading/observer requirement:** run aggregation on `DispatcherProvider.default`, keep Room observation behind the
repository, and use one cancelable/`flatMapLatest` observation per user/range.

**Acceptance checks:** no repository/session injection in the Fragment; no MPAndroidChart type crosses into the
ViewModel or repository; tests cover date-range exclusion, month grouping, empty results, and reload cancellation.

**Likely files:** `ui/dashboard/ActivitiesFragment.kt`, new `ui/dashboard/ActivitiesViewModel.kt`, and focused tests.

---

## 10. Remove the unused `UploadManager` injection from `ProcessUserDataActivity`

**Current DI leak:** `ui/sync/ProcessUserDataActivity.kt` imports and injects `UploadManager`, but never uses it. The same
activity does use `UploadToShelfService` for the member-upload workflow; retaining an unused second upload coordinator
makes ownership ambiguous and increases the apparent UI/service surface.

**Small PR shape:** delete only the unused `UploadManager` field and import, then run compile and the relevant sync UI
tests. Do not replace it with another dependency and do not fold the callback-based member upload into this cleanup PR.
That follow-up belongs to the later sync/upload consolidation roadmap after a single owner and result contract are
agreed.

**Acceptance checks:** production compile proves Hilt construction still succeeds; `ProcessUserDataActivity` behavior
and tests are otherwise unchanged; no speculative facade or unused code is added.

**Likely file:** `ui/sync/ProcessUserDataActivity.kt` only.

---

## Merge-conflict plan for this review round

| PR | Primary production area | Expected shared hotspot | Merge guidance |
|---:|---|---|---|
| 1 | Dictionary | `RepositoryModule.kt` | Merge first; it is the only planned new repository binding. |
| 2 | Voices | None outside voices | Do not combine with unrelated adapter cleanup. |
| 3 | Health examination | None | Independent. |
| 4 | Chat history | None | Independent. |
| 5 | Take-course flow | None | Independent; avoid other courses cleanup this round. |
| 6 | Collections dialog | None | Use a dedicated ViewModel rather than editing shared `ResourcesViewModel`. |
| 7 | Settings/offline resources | `ResourcesRepository*` | Merge after any already-open resources repository PR; otherwise independent. |
| 8 | Feedback composer | None | Use a dedicated ViewModel; do not edit list/detail ViewModels. |
| 9 | Activity history | None unless repository SQL is chosen | Prefer pure ViewModel aggregation if an interface change offers little reuse. |
| 10 | Sync activity DI | None | Safe cleanup; merge at any point. |

The main collision risks are therefore limited to PR 1's central Hilt module and PR 7's resources repository. Review
and merge those early. The other eight PRs are feature-local and can be reviewed in parallel without stacking commits.

## Deliberately deferred from this round

- Global navigation architecture and Compose migration: both create broad UI-file churn and would collide with these
  boundary extractions.
- Splitting `TeamsRepositoryImpl`, `SyncManager`, or `UploadManager`: valuable, but not low-risk review-round work.
- Blanket dispatcher changes: the production scan shows direct `Dispatchers.*` centralized in `DispatcherProvider`;
  use targeted dispatcher fixes only where work is moved above.
- Blanket adapter migration: current dynamic RecyclerView lists are already predominantly `ListAdapter` users of the
  shared `DiffUtils` helpers. Pager/static adapters should not be converted merely to create work.
- Broad listener rewrites: address the concrete non-lifecycle-aware health collector in task 3; avoid touching healthy
  lifecycle-aware collectors without a demonstrated leak.
