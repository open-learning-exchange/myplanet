# Refactor Round — Repository Boundaries (11 Reviewable Tasks)

**Scope of this round:** roadmap items **1 (finish cleaning the data layer)**, **3 (expand ViewModel layer)**, **4 (DI cleanup)** and **8 (code health)**.
Explicitly **out of scope this round:** global navigation architecture (2), sync/upload consolidation (5), Compose migration (6), perf hotspots (7).

**Round constraints applied to every task below**
- ~10 PRs/day max → 11 tasks, one PR each, listed in a conflict-safe merge order
- Each PR: one concern, ≤ ~3 files of real change, reviewable in a single sitting
- No new unused code — every added interface method must have a caller in the same PR
- No hard-coded `Dispatchers.*` (project already has an injectable `DispatcherProvider` — verified: only `DispatcherProvider.kt` itself references `Dispatchers.IO/Default`)
- Lists keep `androidx.recyclerview.widget.ListAdapter` + `org.ole.planet.myplanet.utils.DiffUtils.itemCallback`

**Baseline verified on `master` @ `9c54a03`** (so reviewers know what is already clean and does not need touching):
- `RecyclerView.Adapter<…>` direct subclasses: **0** — every list is already `ListAdapter`
- Anonymous `DiffUtil.ItemCallback` implementations: **0** — 41 files already use `DiffUtils.itemCallback`
- `notifyDataSetChanged()` call sites: **2**, both in `SurveyFragment.kt` → Task 8
- `observeForever` / `GlobalScope`: **0**
- Broadcast receivers: all routed through `BroadcastService.events` + `collectWhenStarted`/`repeatOnLifecycle` — no unregister leaks
- UI classes importing `data.room.dao.*`: **1** (`DictionaryActivity`) → Task 1

---

## Task 1 — Seal the last UI→DAO leak: `DictionaryActivity`

**Roadmap:** 1 (data layer) · **Size:** XS · **Conflict risk:** very low

**Why**
`DictionaryActivity` is the **only** UI class left in the codebase that injects a Room DAO. Every other feature already goes UI → repository → DAO. Sealing this makes "UI never imports `data.room.dao.*`" an enforceable, greppable invariant.

**Spots**
- `ui/dictionary/DictionaryActivity.kt:34-35` — `@Inject lateinit var dictionaryDao: DictionaryDao`
- `ui/dictionary/DictionaryActivity.kt:127` — `dictionaryDao.insertAll(entities)` (also builds `DictionaryEntity` rows in the Activity)
- `ui/dictionary/DictionaryActivity.kt:139` — `dictionaryDao.count()`
- `ui/dictionary/DictionaryActivity.kt:148` — `dictionaryDao.findByWord(query)`
- `di/RepositoryModule.kt` — where the new binding goes

**Do**
1. Add `DictionaryRepository` + `DictionaryRepositoryImpl` with exactly three methods: `count()`, `findByWord(word)`, `insertAll(entities)` — nothing speculative.
2. Move the JSON→`DictionaryEntity` parsing (`loadDictionaryIfNeeded`) into the impl so the Activity stops constructing entities.
3. Bind in `RepositoryModule`, swap the injection in the Activity.

**Do not** add a `DictionaryViewModel` or touch the download/broadcast path in this PR.

---

## Task 2 — `ChatHistoryFragment`: delete the duplicate `ChatRepository` path

**Roadmap:** 3 + 4 · **Size:** XS · **Conflict risk:** very low (PR #15198 is on the chat *input* path, not history)

**Why**
This is a straight duplicate: the fragment already holds a ViewModel that exposes the exact same call. Two paths to the same repository method is precisely the boundary erosion this round is meant to stop.

**Spots**
- `ui/chat/ChatHistoryFragment.kt:44` — `private val sharedViewModel: ChatViewModel by activityViewModels()` (already present)
- `ui/chat/ChatHistoryFragment.kt:55` — `@Inject lateinit var chatRepository: ChatRepository` (the leak)
- `ui/chat/ChatHistoryFragment.kt:226` — `chatRepository.fetchAiProviders(serverUrl)`
- `ui/chat/ChatViewModel.kt:356-357` — `suspend fun fetchAiProviders(serverUrl)` already delegates to the same repository

**Do**
1. Point `checkAiProvidersIfNeeded()` at `sharedViewModel.fetchAiProviders(serverUrl)`.
2. Delete the `ChatRepository` injection and its import.
3. Optional in the same PR: make the VM method a `viewModelScope` one-shot exposing state, so the fragment stops launching a coroutine purely for a data call.

---

## Task 3 — Tighten interfaces: delete 5 verified-dead repository methods

**Roadmap:** 1 + 8 · **Size:** XS (pure deletion) · **Conflict risk:** low — deletions only, but touches four interface headers

**Why**
Fat interfaces are the main reason repository boundaries drift: a 50-method interface invites callers to reach for whatever is nearest. These five have **zero production call sites** — they exist only as interface + impl + test. Removing them shrinks the surface without changing behavior.

**Spots** (each confirmed: only declaration, override, and a unit test reference)
- `repository/ResourcesRepository.kt:57` + `ResourcesRepositoryImpl.kt:333` — `markAllResourcesOffline(isOffline)`
- `repository/SubmissionsRepository.kt:23` + `SubmissionsRepositoryImpl.kt:179` — `getSubmissionsByUserId(userId)`
- `repository/VoicesRepository.kt:31` + `VoicesRepositoryImpl.kt:86` — `getCommunityVisibleNews(userIdentifier)`
- `repository/VoicesRepository.kt:32` + `VoicesRepositoryImpl.kt:124` — `getNewsByTeamId(teamId)`
- `repository/VoicesRepository.kt:41` + `VoicesRepositoryImpl.kt:313` — `deleteNews(newsId)`

**Do**
1. Re-run the check right before opening the PR (open PRs may have added a caller):
   `grep -rn "markAllResourcesOffline\|getSubmissionsByUserId\|getCommunityVisibleNews\|getNewsByTeamId\|deleteNews" app/src/main --include=*.kt`
   Only declaration + override lines should come back.
2. Delete method, override, and the now-orphaned tests in `app/src/test/`.
3. Drop any DAO field that becomes unused as a result.

**Reference for follow-ups (interface sizes today):** `TeamsRepository` 65 methods, `ResourcesRepository` 50, `SubmissionsRepository` 45, `UserRepository` 43, `CoursesRepository` 37. Splitting those is a **later round** — not this one.

---

## Task 4 — `ActivitiesFragment`: move the login-history query behind a ViewModel

**Roadmap:** 3 · **Size:** XS–S · **Conflict risk:** very low (own file; deliberately **not** `DashboardViewModel`, which is hot)

**Why**
The fragment injects a repository and owns the flow subscription. Chart rendering is correctly UI-local; the data acquisition is not.

**Spots**
- `ui/dashboard/ActivitiesFragment.kt:34` — `@Inject lateinit var activitiesRepository: ActivitiesRepository`
- `ui/dashboard/ActivitiesFragment.kt:50` — `activitiesRepository.getOfflineLogins(userName)` inside `lifecycleScope.launch`
- `ui/dashboard/ActivitiesFragment.kt` `computeMonthlyCounts(...)` — pure UI aggregation, **leave it in the fragment** (it is already `internal` and unit-testable)

**Do**
1. Add a small `ActivitiesViewModel` that resolves the user name (`UserSessionManager`) and exposes the offline-login flow as state.
2. Fragment collects with `collectLatestWhenStarted` and renders; drop the repository + `UserSessionManager` injections.
3. Keep `computeMonthlyCounts` untouched so the existing test still passes.

**Do not** fold this into `DashboardViewModel` — it is already large and is touched by several open PRs.

---

## Task 5 — `HealthExaminationActivity`: kill the repo bypass *and* the non-lifecycle-aware collector

**Roadmap:** 3 + threading · **Size:** XS · **Conflict risk:** very low (note: PR #15644 touches `MyHealthFragment`, a different file)

**Why**
Two small defects in one screen, fixable in one diff. The Activity already has a ViewModel that owns the repository, yet reaches around it for one call — and does so from inside a `lifecycleScope.launch { … .collect { } }`, which is `CREATED`-scoped and keeps collecting while the Activity is in the background. Every other collector in this file already uses `collectWhenStarted`.

**Spots**
- `ui/health/HealthExaminationActivity.kt:49` — `@Inject lateinit var healthRepository: HealthRepository`
- `ui/health/HealthExaminationActivity.kt:101-102` — `lifecycleScope.launch { viewModel.state.collect { … } }` (not `STARTED`-scoped)
- `ui/health/HealthExaminationActivity.kt:110` — `healthRepository.getExaminationConditions(examination)` called from inside that collector
- `ui/health/HealthExaminationActivity.kt:119,123` — the correct pattern, already in the same file: `collectWhenStarted(viewModel.isSaving) { … }`
- `utils/FlowExtensions.kt:29` — `LifecycleOwner.collectWhenStarted`

**Do**
1. Fold `conditionsMap` into the existing `viewModel.state` (the VM already injects `HealthRepository`).
2. Convert line 101 to `collectWhenStarted(viewModel.state) { … }`.
3. Delete the `HealthRepository` injection. Validation/UI mapping stays in the Activity.

---

## Task 6 — `StorageCategoryDetailFragment`: move offline-file listing/deletion into a ViewModel

**Roadmap:** 3 · **Size:** S · **Conflict risk:** very low (`ui/settings` is quiet)

**Why**
The fragment injects `ResourcesRepository` and drives both a disk scan and a destructive delete straight from `lifecycleScope`. The scan result lives only in a fragment field, so it is re-run on every rotation, and a rotation mid-delete drops the completion handling.

**Spots**
- `ui/settings/StorageCategoryDetailFragment.kt:35` — `@Inject lateinit var resourcesRepository: ResourcesRepository`
- `ui/settings/StorageCategoryDetailFragment.kt:138` — `resourcesRepository.getOfflineResourceItems(olePath, extensions, allKnownExtensions)`
- `ui/settings/StorageCategoryDetailFragment.kt:188` — `resourcesRepository.deleteOfflineResources(olePath, toDelete)`
- `ui/settings/SettingsViewModel.kt` — already injects `ResourcesRepository`; a sibling `StorageViewModel` is the cleaner home

**Do**
1. Add a `StorageCategoryViewModel` (or extend `SettingsViewModel` if you prefer one fewer class) exposing `items` state + a `delete(items)` one-shot result.
2. Fragment only renders + `submitList`; keeps the existing `DiffUtils.itemCallback<OfflineResourceItem>`.
3. Confirm the repository methods do their file I/O under the injected `DispatcherProvider` — if not, that is a **one-line fix in the impl**, in this same PR.

---

## Task 7 — `CollectionsFragment` + `MarkdownDialogFragment`: last two trivial repo reads in UI

**Roadmap:** 3 · **Size:** XS · **Conflict risk:** very low

**Why**
Both are single-call leaks with no ViewModel at all. Grouped because each alone is a two-line diff and neither shares a file with anything else in this round.

**Spots**
- `ui/resources/CollectionsFragment.kt:34` — `@Inject lateinit var tagsRepository: TagsRepository`
- `ui/resources/CollectionsFragment.kt:91` — `tagsRepository.getTagsWithChildren(dbType)`
- `ui/components/MarkdownDialogFragment.kt:34` — `@Inject lateinit var userRepository: UserRepository`
- `ui/components/MarkdownDialogFragment.kt:126-128` — `userRepository.getActiveUserIdSuspending()` then `userRepository.hasUserSyncAction(userId)`

**Do**
1. `CollectionsFragment` → small `CollectionsViewModel` exposing the tag tree as state.
2. `MarkdownDialogFragment` → collapse the two-call sequence into **one** repository method (`hasActiveUserSyncAction()`), exposed via a tiny VM. This removes a caller-side two-step that reveals identity plumbing to the UI.
3. Drop both injections.

**Split into two PRs** if the reviewer prefers — they share no file.

---

## Task 8 — `SurveyFragment`: the codebase's only two `notifyDataSetChanged()` calls

**Roadmap:** 8 (DiffUtil/ListAdapter health) · **Size:** XS–S · **Conflict risk:** very low, but **sequence after PR #14650** (it touches survey submissions and `SubmissionsAdapter`)

**Why**
`SurveysAdapter` is already a `ListAdapter` with `DiffUtils.itemCallback`, and the main list correctly uses `submitList`. But two side-channel maps (`surveyInfos`, `bindingData`) are pushed by rebinding **every** row, which defeats DiffUtil entirely. These are the last two `notifyDataSetChanged()` calls in the whole app — closing them makes "no `notifyDataSetChanged`" a lint-able rule.

**Spots**
- `ui/surveys/SurveyFragment.kt:171-172` — the correct pattern already: `collectWhenStarted(viewModel.surveys) { … submitList(surveys) }`
- `ui/surveys/SurveyFragment.kt:177-180` — `collectWhenStarted(viewModel.surveyInfos) { … getAdapter().notifyDataSetChanged() }`
- `ui/surveys/SurveyFragment.kt:182+` — same shape for `viewModel.bindingData`
- `ui/surveys/SurveysAdapter.kt` — `DiffUtils.itemCallback`
- `ui/surveys/SurveysViewModel.kt` — where `surveys` / `surveyInfos` are produced

**Do**
1. Preferred: `combine` the survey list with `surveyInfos`/`bindingData` **in `SurveysViewModel`** into one row model, and emit a single list → one `submitList` in the fragment. This also removes UI-side data joining.
2. Fallback if the join is awkward: `notifyItemRangeChanged(0, itemCount, PAYLOAD_INFO)` + a payload branch in `onBindViewHolder`.
3. Keep `DiffUtils.itemCallback`; no repository changes in this PR.

---

## Task 9 — Cross-feature leak: `VoicesRepository` reads the library/resources table

**Roadmap:** 1 · **Size:** S · **Conflict risk:** low–medium (voices files were touched by 3 of the last 30 master commits — land this before Task 11)

**Why**
`VoicesRepositoryImpl` injects `MyLibraryDao` for exactly one method. Resources data is owned by `ResourcesRepository`; voices reading it directly is the clearest cross-feature table leak in the repository layer.

**Spots**
- `repository/VoicesRepositoryImpl.kt:40` — `private val myLibraryDao: MyLibraryDao`
- `repository/VoicesRepositoryImpl.kt:547` — `myLibraryDao.getPrivateImagesCreatedAfter(timestamp)` (the only use)
- `repository/VoicesRepository.kt` — `getPrivateImageUrlsCreatedAfter(...)` declaration
- `ui/voices/NewsViewModel.kt` — forwards the call
- `base/BaseDashboardFragment.kt` — the consumer
- `repository/ResourcesRepository.kt` — the correct owner (already holds `MyLibraryDao` and similar reads)

**Do**
1. Move the method to `ResourcesRepository` / `ResourcesRepositoryImpl` verbatim.
2. Point `NewsViewModel` (or the dashboard VM — keep it to **one** hop) at `ResourcesRepository`.
3. Delete the method + the `MyLibraryDao` field from voices.

**Known remaining leak, deliberately deferred:** `VoicesRepositoryImpl:38,253-264` still owns `TeamNotificationDao` (`updateTeamNotification`). Separate PR, next round.

---

## Task 10 — Cross-feature leak: `ResourcesRepository` **writes** team rows via `TeamDao`

**Roadmap:** 1 · **Size:** S · **Conflict risk:** low–medium (adds one narrow method to `TeamsRepository`; PRs #15656/#15662 are reshaping that interface — coordinate or land after them)

**Why**
A **write** across a feature boundary is worse than a read: after uploading a private library item, resources upserts a `MyTeam` row with `docType = "resourceLink"` — team-link data it does not own, and whose invariants live in teams.

**Spots**
- `repository/ResourcesRepositoryImpl.kt:55` — `private val teamDao: TeamDao`
- `repository/ResourcesRepositoryImpl.kt:706` — `teamDao.upsert(MyTeam(docType = "resourceLink", …))` inside `markResourceUploaded` (the only use)
- `repository/TeamsRepository.kt` — check whether an existing resource-link method already fits before adding one

**Do**
1. Reuse an existing teams resource-link method if one fits; otherwise add exactly one narrow method (e.g. `createLocalResourceLink(teamId, resourceId, title, planetCode)`) — **with its caller in the same PR**.
2. Call it from `markResourceUploaded`; keep the `MyLibrary` write in resources.
3. Delete the `TeamDao` field from `ResourcesRepositoryImpl`.

---

## Task 11 — `VoicesAdapter` must not hold a repository

**Roadmap:** 1 + 8 · **Size:** S · **Conflict risk:** medium — **schedule last.** Touches `ui/voices` + `ui/teams/voices`, both recently changed on master (`f4adebf`, and `VoicesFragment`/`TeamsVoicesFragment`/`ReplyActivity` each appear in the last 30 commits)

**Why**
An adapter holding a repository is the deepest boundary violation left in the UI: the data layer is reachable from a `ViewHolder`. Notably, the *other* dependencies of this adapter are already callback-shaped (`getUserFn`, `deletePostFn`, `getLibraryResourceFn`) and `LabelManipulator` already wraps its repository behind a small interface — so the house pattern exists; this is the one holdout.

**Spots**
- `ui/voices/VoicesAdapter.kt:37,64` — import + `private val voicesRepository: VoicesRepository`
- `ui/voices/VoicesAdapter.kt:490` and `:762` — passed into `VoicesActions` for the edit and reply paths
- `ui/voices/VoicesActions.kt` — edit/reply alert currently takes the repository
- Call sites to rewire: `ui/voices/VoicesFragment.kt:270`, `ui/voices/ReplyActivity.kt:154`, `ui/teams/voices/TeamsVoicesFragment.kt:252`
- Pattern to copy: `ui/voices/LabelManipulator.kt`

**Do**
1. Replace the repository parameter with a small `VoicesEditActions` interface (edit post, create reply) implemented by the fragment/ViewModel.
2. Rewire the three call sites; leave `DiffUtils.itemCallback` and the label manager alone.
3. Constructor-signature churn only — **no** behavior change, and **no** attempt to split the ~1000-line adapter.

---

## Suggested merge order (conflict-minimizing)

| Day | Task | Primary paths | Overlaps anything else this round? |
|-----|------|---------------|-------------------------------------|
| 1 | T1 Dictionary repository | `ui/dictionary`, new `repository/Dictionary*`, `di/RepositoryModule` | no |
| 2 | T2 ChatHistory duplicate path | `ui/chat/ChatHistoryFragment` | no |
| 3 | T4 ActivitiesViewModel | `ui/dashboard/Activities*` | no |
| 4 | T5 HealthExamination VM + collector | `ui/health/HealthExamination*` | no |
| 5 | T7 Collections + Markdown dialog | `ui/resources/CollectionsFragment`, `ui/components/MarkdownDialogFragment` | no |
| 6 | T6 Storage category VM | `ui/settings/StorageCategoryDetail*` | no |
| 7 | T8 Survey DiffUtil payloads | `ui/surveys/*` | after PR #14650 |
| 8 | T9 Voices → Resources image URLs | `repository/Voices*`, `repository/Resources*`, `NewsViewModel` | shares `Resources*` with T10 |
| 9 | T10 Resources → Teams resourceLink | `repository/Resources*`, `repository/Teams*` | after T9; coordinate with #15656/#15662 |
| 10 | T3 Delete dead interface methods | 4 interface headers + impls + tests | **last**, so the dead-code check is run against final state |
| 11 | T11 VoicesAdapter drops repository | `ui/voices`, `ui/teams/voices` | after T9 |

Days 1–6 touch **completely disjoint** files. Days 8–10 all touch repository headers but different method clusters — serialize them, do not parallelize.

---

## Open-PR conflict warnings (checked against 31 open PRs)

| Open PR | Files it touches | Affects |
|---------|------------------|---------|
| **#14650** survey submissions | `SurveysRepository{,Impl}` (adds `getAssignedSurveys` **using `teamDao`**), `ExamTakingFragment`, `SubmissionViewModel`, `SubmissionsAdapter`, `BaseDashboardFragment` | Blocks the "Surveys drops `TeamDao`" idea entirely this round — #14650 adds a *new* `teamDao` use. Also sequence **T8** after it. |
| **#15656** split `TeamsRepository` interface / **#15662** remove `UploadManager` dep | `TeamsRepository{,Impl}` | **T10** adds a method to that interface — land after, or coordinate |
| **#15699** resource rating dialog | `ResourceViewerFragment`, `ResourceViewerViewModel` (+ `RatingsRepository`, `UserRepository`) | Do **not** add a ratings/resource-viewer task this round |
| **#15650** adapter swapping | `BaseRecyclerFragment` | Keep all base-class edits out of this round |
| **#15603** sync collector leak / **#15644** textChanges flows | `SyncActivity`, `TeamFragment`, `MyHealthFragment` | T5 touches `HealthExaminationActivity` — a *different* file; verify before pushing |
| **#15226** / **#15720** large ports | wide | Ignore; they will conflict with everything regardless |

---

## Explicitly deferred (do **not** mix into this round)

| Candidate | Why it waits |
|-----------|--------------|
| `SurveysRepositoryImpl:51,82` drops `TeamDao` (team-name lookup) | Direct conflict with open PR #14650 |
| `VoicesRepositoryImpl:38,253-264` `TeamNotificationDao` ownership | Related to T9; separate PR next round |
| `NotificationsRepositoryImpl:29,31` reads `TeamTaskDao` (5 sites) + `ExamDao` | Cross-cuts notifications + teams tasks; medium blast radius |
| `TeamsRepositoryImpl:92,1348,1351` reads `MyLibraryDao`/`CourseStepDao`/`CourseDao` | 1437-line mega-file; needs its own plan |
| `UserRepositoryImpl:74-75,957,1226` reads `MyLibraryDao` + `MeetupDao` | Auth/sync sensitive |
| `CoursesRepositoryImpl:179,235,519,807,827` reads `MyLibraryDao` (5 sites) | Arguably legitimate (course↔resource join) — decide ownership before moving |
| Splitting `TeamsRepository` (65 methods) / `ResourcesRepository` (50) / `SubmissionsRepository` (45) / `UserRepository` (43) | Already in flight as #15656; a whole round on its own |
| `ExamTakingFragment` + `BaseExamFragment` (4 repositories, ~17 call sites) → `ExamViewModel` | Largest remaining UI→repository cluster; conflicts with #15559 and #14650 |
| `CourseStepFragment` (configs + progress) and `TakeCourseFragment:396` (ratings) | Hot files; course PRs open |
| `SyncActivity:123-129` / `ProcessUserDataActivity:48,61` repository injections | Sync-critical; #15603 open |
| `CommunityTabFragment:38` / `HomeCommunityDialogFragment:29` `getPlanetType()` | Trivial, but keep as filler if a slot opens |
| Global navigation, Compose migration, sync/upload consolidation, perf work | Roadmap items 2, 5, 6, 7 — out of scope by design |

---

## Per-PR review checklist

- [ ] One concern; title follows house style (`refactor:` / `fix:` + `(fixes #NNNN)`)
- [ ] No new unused interface method, class, or parameter — every addition has a caller in the same diff
- [ ] No UI file imports `org.ole.planet.myplanet.data.room.dao.*`
- [ ] A repository touches only tables its own feature owns; cross-feature access goes through the owning repository
- [ ] No new hard-coded `Dispatchers.*` — use the injected `DispatcherProvider`
- [ ] Collectors in Activities/Fragments use `collectWhenStarted` / `repeatOnLifecycle(STARTED)`
- [ ] Lists use `ListAdapter` + `DiffUtils.itemCallback` + `submitList` — no new `notifyDataSetChanged()`
- [ ] Unit tests added/updated only where behavior actually moved
- [ ] `./gradlew testDefaultDebugUnitTest` green locally before pushing
