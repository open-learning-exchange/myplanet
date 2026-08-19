# Repository Boundary Round — 10 Reviewable Tasks

Focus: reinforce repository boundaries, stop cross-feature data leaks, move data work out of UI one PR at a time.

Constraints for this round:

- ~1 PR/day (~10 total), each easily reviewable
- Prefer low merge-conflict surface (narrow packages, avoid co-editing mega-files in the same day)
- Low-hanging fruit only — no Compose migration, no global nav rewrite, no TeamsRepositoryImpl split
- No unused scaffolding; only wire what callers already need
- Prefer moving **existing** functions, not inventing new domains

Suggested order is conflict-safe: isolated features first, shared hubs later.

---

## Task 1 — Seal the last UI→DAO leak: `DictionaryRepository`

**Roadmap fit:** Finish cleaning the data layer / reinforce repository boundaries  
**Conflict risk:** Very low (dictionary package + DI only)  
**Size:** XS

**Why**

- `DictionaryActivity` is the only UI class that still injects a Room DAO (`DictionaryDao`) and builds `DictionaryEntity` rows itself.
- Every other domain already goes UI → repository → DAO.

**Spots**

- `ui/dictionary/DictionaryActivity.kt` — `@Inject lateinit var dictionaryDao: DictionaryDao`; `insertAll` / `count` / `findByWord`
- `data/room/dao/DictionaryDao.kt` — already has the 3 methods needed
- No `DictionaryRepository` exists today; `di/RepositoryModule.kt` + `RoomModule` already provide the DAO

**Do**

1. Add thin `DictionaryRepository` + `DictionaryRepositoryImpl` wrapping `count`, `findByWord`, `insertAll` (and optionally “parse downloaded JSON → entities” so the Activity stops touching `DictionaryEntity`).
2. Bind in `RepositoryModule`.
3. Replace DAO injection in `DictionaryActivity` with the repository.
4. Keep download/broadcast UI in the Activity (or a later ViewModel task — not this PR).

**Out of scope:** Dictionary ViewModel, download service changes.

---

## Task 2 — `HealthExaminationActivity`: stop dual-layer repository access

**Roadmap fit:** Expand ViewModel layer / repository boundary  
**Conflict risk:** Very low (`ui/health` only)  
**Size:** XS

**Why**

- Activity already has `HealthExaminationViewModel` **and** still injects `HealthRepository` for one call.
- Classic UI/data leak: ViewModel owns most paths, Activity bypasses it for conditions.

**Spots**

- `ui/health/HealthExaminationActivity.kt` — `lateinit var healthRepository`; `healthRepository.getExaminationConditions(examination)` inside `viewModel.state` collect
- `ui/health/HealthExaminationViewModel.kt` — already has `loadData` / `saveExamination` via repository

**Do**

1. Expose conditions on the ViewModel state (or a small helper method) using the existing repository call.
2. Remove `HealthRepository` injection from the Activity.
3. Keep validation/UI mapping in the Activity.

**Out of scope:** Broader health UI cleanup, Compose.

---

## Task 3 — `FeedbackFragment`: create/submit only through a ViewModel

**Roadmap fit:** Expand ViewModel layer  
**Conflict risk:** Very low (`ui/feedback` + existing repo API)  
**Size:** XS

**Why**

- List/detail already use ViewModels; create dialog still calls `feedbackRepository.createAndSaveFeedback(...)` from the Fragment.
- Repository API already exists — this is a pure boundary move.

**Spots**

- `ui/feedback/FeedbackFragment.kt` — injects `FeedbackRepository`; `validateAndSaveData()`
- `repository/FeedbackRepository.kt` — `createAndSaveFeedback(...)`
- Existing `FeedbackListViewModel` / `FeedbackDetailViewModel` (prefer a tiny dedicated submit VM or one method on list VM — pick one, don’t add dead APIs)

**Do**

1. Add a single submit entry point on a feedback ViewModel.
2. Fragment validates UI fields, then calls ViewModel; observe success/error to toast + dismiss.
3. Drop repository injection from the Fragment.

**Out of scope:** Feedback list DiffUtil, upload pipeline.

---

## Task 4 — `ChatHistoryFragment`: drop direct `ChatRepository` (VM already has the API)

**Roadmap fit:** Expand ViewModel layer / DI cleanup  
**Conflict risk:** Very low (`ui/chat`)  
**Size:** XS

**Why**

- Fragment injects `ChatRepository` only to call `fetchAiProviders`, while `ChatViewModel.fetchAiProviders(serverUrl)` already exists.
- Pure dead-path cleanup that tightens the UI→VM→repository line.

**Spots**

- `ui/chat/ChatHistoryFragment.kt` — `lateinit var chatRepository`; `checkAiProvidersIfNeeded()`
- `ui/chat/ChatViewModel.kt` — `suspend fun fetchAiProviders(serverUrl: String)`

**Do**

1. Route `checkAiProvidersIfNeeded()` through `sharedViewModel.fetchAiProviders`.
2. Remove `ChatRepository` injection from the Fragment.
3. Prefer a `viewModelScope` one-shot in the VM if the Fragment still launches a coroutine only for that call.

**Out of scope:** Chat share targets, pagination, Realtime sync.

---

## Task 5 — `ActivitiesFragment`: move offline-login query behind a ViewModel

**Roadmap fit:** Expand ViewModel layer  
**Conflict risk:** Very low (`ui/dashboard/ActivitiesFragment` + small VM)  
**Size:** XS–S

**Why**

- Fragment injects `ActivitiesRepository` and collects `getOfflineLogins` while chart rendering stays UI-local (correct).
- Data observation belongs in a ViewModel; chart mapping can stay in the Fragment.

**Spots**

- `ui/dashboard/ActivitiesFragment.kt` — `activitiesRepository.getOfflineLogins(userName)`
- `repository/ActivitiesRepository` (existing)
- Nearby: `DashboardViewModel` is already large — **prefer a tiny `ActivitiesViewModel`** to avoid conflicting with dashboard work

**Do**

1. Add `ActivitiesViewModel` exposing a `Flow`/state of login events (or monthly counts if you want zero chart logic change).
2. Fragment only collects UI state and renders the chart (`computeMonthlyCounts` can stay private UI helper).
3. Remove repository injection from the Fragment.

**Out of scope:** Chart library changes, DashboardActivity notification work.

---

## Task 6 — Remove `VoicesRepository` from `VoicesAdapter` (adapter must not own data layer)

**Roadmap fit:** Reinforce repository boundaries / cross-feature UI leak  
**Conflict risk:** Low–medium (voices package only; touch adapter + fragment/helper constructors)  
**Size:** S

**Why**

- `VoicesAdapter` still takes `VoicesRepository` and passes it into `VoicesActions.showEditAlert` for edit/reply.
- Other adapter deps are already callback-shaped (`getUserFn`, `deletePostFn`, `getLibraryResourceFn`) — repository is the leftover leak.
- Adapters should stay presentation-only.

**Spots**

- `ui/voices/VoicesAdapter.kt` — constructor `voicesRepository`; usages ~edit + reply paths
- `ui/voices/VoicesActions.kt` — edit/reply alert currently expects repository
- Call sites: `VoicesFragment`, `ReplyActivity`, `TeamsVoicesFragment` (+ helpers)
- `ui/voices/LabelManipulator.kt` already correctly wraps repo behind a small interface — same pattern

**Do**

1. Replace repository parameter with explicit callbacks / a small `VoicesEditActions` interface (edit post, create reply) implemented by Fragment/ViewModel.
2. Keep DiffUtil/`DiffUtils.itemCallback` as-is.
3. Do **not** move label manager or image markdown in this PR.

**Out of scope:** Splitting the 1000-line adapter, Compose, team voices feature changes beyond constructor wiring.

---

## Task 7 — Stop `VoicesRepository` reading `MyLibraryDao` (resources leak)

**Roadmap fit:** Finish data layer / call out cross-feature data leaks  
**Conflict risk:** Low (`VoicesRepository*` + `ResourcesRepository*` + `NewsViewModel`)  
**Size:** S

**Why**

- `VoicesRepository.getPrivateImageUrlsCreatedAfter` is the only voices API that touches library/resources tables via `MyLibraryDao`.
- Call chain is already UI-safe: `BaseDashboardFragment` → `NewsViewModel` → repository — only the ownership is wrong.

**Spots**

- `repository/VoicesRepository.kt` / `VoicesRepositoryImpl.kt` — `getPrivateImageUrlsCreatedAfter` + `myLibraryDao`
- `ui/voices/NewsViewModel.kt` — forwards the call
- `base/BaseDashboardFragment.kt` — consumer
- `repository/ResourcesRepository.kt` — natural home (`getLibraryItemByResourceId` already lives here)

**Do**

1. Move the method onto `ResourcesRepository` (impl uses existing `MyLibraryDao` query).
2. Point `NewsViewModel` at `ResourcesRepository` (or have dashboard VM call resources — keep one hop).
3. Remove method + `MyLibraryDao` from voices repository if nothing else needs it.
4. No interface methods left unused.

**Out of scope:** `TeamNotificationDao` still on voices (separate task if desired).

---

## Task 8 — `SurveysRepository`: drop direct `TeamDao` name lookup

**Roadmap fit:** Tighten repository interfaces / cross-feature leak  
**Conflict risk:** Low (`SurveysRepositoryImpl` + maybe one Teams read API)  
**Size:** S

**Why**

- Surveys impl injects `TeamDao` only to resolve `teamName` when adopting/mapping a team survey.
- Teams already expose `getTeamByIdOrTeamId` / `getTeamNamesByIds` / `getTeamLabelInfo` — surveys should not own team tables.

**Spots**

- `repository/SurveysRepositoryImpl.kt` — `teamDao.getById` / `getByTeamId` for name
- `repository/TeamsRepository.kt` — existing read APIs
- Callers of survey adopt/create paths (should not need signature churn if name is resolved inside impl via `TeamsRepository`)

**Do**

1. Replace `TeamDao` with `TeamsRepository` (or a single `getTeamName(teamId)` on teams if you want a narrower dependency).
2. Keep survey write logic in `SurveysRepository`.
3. Add/adjust unit test for the name-resolution branch if one exists nearby.

**Out of scope:** Team-owned survey list algorithms, PublicSurveyActivity ViewModel (Task 10-ish alternate).

---

## Task 9 — `ResourcesRepository.markResourceUploaded`: stop writing team links via `TeamDao`

**Roadmap fit:** Cross-feature data leak / data-layer cleanup  
**Conflict risk:** Low–medium (`ResourcesRepositoryImpl` + small Teams write API)  
**Size:** S

**Why**

- After uploading a private library item, resources upserts a `MyTeam` `resourceLink` row through `TeamDao` directly.
- That is team-membership/link domain data owned by teams, not resources.

**Spots**

- `repository/ResourcesRepositoryImpl.kt` — `markResourceUploaded` → `teamDao.upsert(MyTeam(docType = "resourceLink", ...))`
- `repository/TeamsRepository` / impl — already has `addResourceLinks` / resource-link helpers used by team UI

**Do**

1. Add a narrow teams API if needed, e.g. `createLocalResourceLink(teamId, resourceId, title, planetCode)` (only if `addResourceLinks` is not already suitable).
2. Call it from `markResourceUploaded` instead of `TeamDao`.
3. Remove `TeamDao` from `ResourcesRepositoryImpl` when unused.
4. Keep upload marking of `MyLibrary` in resources.

**Out of scope:** Full private-resource feature rewrite; `getTeamPrivateResources` can stay if it only reads library rows by team id (library table), or follow up later.

---

## Task 10 — `SurveyFragment`: stop `notifyDataSetChanged` on ListAdapter side maps

**Roadmap fit:** DiffUtil / ListAdapter health (and keeps survey UI off the data-critical path while Task 8 lands)  
**Conflict risk:** Very low (`ui/surveys` only)  
**Size:** XS–S

**Why**

- Adapter is already `ListAdapter` + `DiffUtils.itemCallback`, but `surveyInfos` / `bindingData` updates call `getAdapter().notifyDataSetChanged()`.
- That bypasses DiffUtil, causes full rebinds, and fights the house ListAdapter pattern.

**Spots**

- `ui/surveys/SurveyFragment.kt` — `setupObservers()` `notifyDataSetChanged()` on info/binding maps
- `ui/surveys/SurveysAdapter.kt` — `DiffUtils.itemCallback`

**Do**

1. Prefer payload-based `notifyItemRangeChanged(0, itemCount, PAYLOAD_*)` **or** fold info into the submitted row model and `submitList` again (match patterns in `CoursesAdapter` / `ResourcesAdapter`).
2. Keep using `org.ole.planet.myplanet.utils.DiffUtils.itemCallback`.
3. No repository changes in this PR (pairs well the day after Task 8 without touching the same files).

**Out of scope:** Survey domain logic, PublicSurvey networking.

---

# Stretch / next-round candidates (do **not** mix into the 10 above)

These are still good boundary wins but larger or higher conflict:

| Candidate | Why wait |
|-----------|----------|
| `ExamTakingFragment` / `BaseExamFragment` multi-repo orchestration → new `ExamViewModel` | Touches exam + submissions + courses + surveys; larger review |
| `CourseStepFragment` (configs/progress/resources/submissions) → `TakeCourseViewModel` | Hot file, many repos, easy conflicts with courses work |
| `PublicSurveyActivity` → ViewModel | Good follow-up after Task 8 |
| `NotificationsRepository` TeamTask/Exam DAO reads → teams/surveys APIs | Cross-cuts notifications + teams tasks |
| `VoicesRepository.updateTeamNotification` TeamNotificationDao ownership | Related to Task 7; separate PR |
| `TeamsRepositoryImpl` CourseDao/MyLibraryDao reads for team courses/resources | Mega-file; split only with dedicated plan |
| `UserRepository` MeetupDao shelf merge + fat interface trim | Auth/sync sensitive; high blast radius |
| `MembersFragment` still calling `teamsRepository` despite `RequestsViewModel` nearby | Medium; team members package busy |
| `MarkdownDialogFragment` / `CollectionsFragment` direct repos | Easy XS follow-ups if a slot opens |
| Global nav / Compose / sync-upload consolidation | Explicitly out of this round |

---

# Suggested daily merge order (minimize conflicts)

| Day | Task | Primary paths |
|-----|------|----------------|
| 1 | T1 DictionaryRepository | `ui/dictionary`, `repository/Dictionary*`, `di/RepositoryModule` |
| 2 | T2 HealthExamination VM-only | `ui/health` |
| 3 | T3 Feedback submit VM | `ui/feedback` |
| 4 | T4 ChatHistory AI providers | `ui/chat` |
| 5 | T5 ActivitiesViewModel | `ui/dashboard/Activities*` |
| 6 | T10 Survey ListAdapter payloads | `ui/surveys` |
| 7 | T7 Voices↔Resources private images | `repository/Voices*`, `repository/Resources*`, `NewsViewModel` |
| 8 | T8 Surveys drop TeamDao | `repository/Surveys*`, maybe `TeamsRepository` |
| 9 | T9 Resources drop TeamDao write | `repository/Resources*`, `TeamsRepository*` |
| 10 | T6 VoicesAdapter no repository | `ui/voices`, team voices call sites |

Notes:

- Days 1–6 barely touch each other.
- Days 7–9 all touch repositories but **different** method clusters; still serialize them (shared `TeamsRepository` / `ResourcesRepository` headers).
- Day 10 stays in UI voices after data moves settle so adapter/call-site churn does not collide with T7.

---

# PR checklist (every task)

- [ ] One concern only; title states boundary move (`refactor:` / `fix:` per house style)
- [ ] No new unused interfaces/methods
- [ ] UI does not import `data.room.dao.*`
- [ ] Cross-feature tables only accessed by the owning repository
- [ ] Prefer existing `DispatcherProvider` (no new hard-coded `Dispatchers.*`)
- [ ] List work uses `DiffUtils.itemCallback` / `submitList` (no fresh `notifyDataSetChanged`)
- [ ] Unit tests only where behavior moves (repo method move or VM new path)
- [ ] Avoid editing `TeamsRepositoryImpl.kt` unless the task is T8/T9 and the change is a **small** API addition
