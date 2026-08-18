# refactor round — upgraded prompt + verified hint bank

date: 2026-08-18 · verified against master @ f53e466 and the ~30 open PRs on that day

three parts:

1. **the prompt** — your original, with the two upgrades (task-quality template, KMP/CMP north star). copy it as-is; only the "verified spots" block inside it changes per round.
2. **the hint bank** — real file:line spots verified by reading the code, sorted by conflict risk. paste ~10 green ones into the prompt's "verified spots" block each round, cross off what got merged, refill next round.
3. **the free-hand rewrite** — a from-scratch "prompt of prompts" designed to work uniformly across jules / codex / copilot / devin / openhands / claude / qwen. same job, spec-shaped.

---

## part 1 — the prompt (copy from here)

```
an analysis suggested

Refactor Roadmap (High → Low Priority)
1. Finish Cleaning the Data Layer
2. Introduce Global Navigation Architecture
3. Expand ViewModel and UseCase Layers
4. Complete Dependency Injection Cleanup
5. Consolidate Sync and Upload Workflow
6. Migrate UI Incrementally to Compose
7. Optimize Remaining Performance Hotspots
8. Improve Code Health and Add Tests

north star (the long game — never jump ahead to it, but never block it either):
9. Kotlin Multiplatform (KMP) — carve out a platform-free kotlin core:
   repositories, sync/upload logic, models and use cases must end up with
   zero android.* / framework imports so they can move to a shared module later
10. Compose Multiplatform (CMP) — every screen migrated in step 6 must be
    CMP-ready: no android views inside composables, state hoisted into
    ViewModels, resources accessed through an abstraction not R.* directly
every task must say in one line which roadmap number it serves and,
if relevant, how it moves us toward 9/10

based on that tell me all the spots with tasks we should do to accomplish above suggestion
remember we can only review 9.99ish pr s a round/day
give me 10 tasks
Mostly we wanna avoid merge conflicts during this PR review merge round
so: no two tasks may touch the same file
and check the currently OPEN pull requests first — skip any file an open PR already touches

also this time focus specially on
performance quick wins
micro-optimizations that unblock bigger refactors later
anything that removes obvious inefficiencies without big rewrites

verified spots — prefer these, they were confirmed by reading the code:
<paste ~10 lines from the hint bank here, refresh every round>

consider though
di
data layers
diffutil / listadapter (also use our DiffUtils.itemCallback)
viewmodels
threading / dispatchers usage (inject DispatcherProvider, never hardcode Dispatchers.*)
long running observers or listeners (collectWhenStarted from utils/FlowExtensions.kt,
  viewLifecycleOwner scope in fragments, unregister receivers in onStop)
kmp readiness: android.* imports hiding in repository / model / utils code

we want low hanging fruits
no complicated stuff with many changes
so it is easily reviewable
also do not add unused code
keep it granular if possible

how a good task looks like (IMPORTANT — every task must follow this):
a task is a full work order, not a 2-liner
someone with zero repo context must be able to execute it without asking anything back
every task must contain these sections:
* title — one imperative line ("cache X in Y", not "improve Y")
* context — 2-3 sentences: why it matters + which roadmap number it serves
* files — exact file paths and class/function names to touch, AND files to leave alone
* steps — 3-6 numbered concrete edits
* acceptance — how to verify: the exact gradle command
  (./gradlew testDefaultDebugUnitTest must stay green), plus the behavior to check
* size budget — target under ~150 changed lines and under ~5 files
* out of scope — 1-2 lines on what NOT to do so nobody wanders
a task shorter than ~15 lines is a bad task — rewrite it before including it

here is one task at the required quality bar — match it:

---
### replace member-count list load with the existing count query (roadmap 1 + 7)
context: RequestsViewModel shows a member count by calling
getJoinedMembers(teamId).size — that loads every membership row AND runs one
user query per member, just to produce an Int. the repository already exposes
getJoinedMemberCount(teamId) backed by a SQL COUNT.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt
(around line 39). do NOT touch TeamsRepositoryImpl — open PRs own that file.
steps:
1. replace teamsRepository.getJoinedMembers(teamId).size with
   teamsRepository.getJoinedMemberCount(teamId)
2. remove any now-unused imports
3. run the unit tests
acceptance: ./gradlew testDefaultDebugUnitTest green; the requests screen
still shows the correct joined-member count
size budget: ~2 changed lines, 1 file
out of scope: no DAO changes, no repository changes
---

do not work on coding
focus on this report of 10 tasks

output format markdown:
split tasks with
markdown string for new section ---

ps there is no code output
we want an easy copyable plan
composed of at least 10 tasks
output into a markdown file
and/or in an easy copyable way
```

---

## part 2 — the hint bank (verified 2026-08-18)

every line below was confirmed by reading the actual code, not guessed.
🟢 = no overlap with any open PR, small blast radius. 🟡 = mild overlap risk, check open PRs first. 🔴 = skip, an open PR already owns the file.

### 🟢 one-file quick wins (perfect round-openers)

- ui/teams/members/RequestsViewModel.kt:39 — `.size` on full member load → existing `getJoinedMemberCount` (the exemplar task above)
- utils/TimeUtils.kt:136 — `formatDate(date, pattern)` builds a new `DateTimeFormatter.ofPattern` on every call; memoize pattern→formatter in a map. one file, fixes every adapter caller at once (EnterprisesReportsAdapter binds 4 formatters per row)
- ui/health/HealthExaminationActivity.kt:101 — the only unguarded infinite `viewModel.state.collect` in the app (runs while stopped, plus a nested launch per emission at :109) → `collectLatestWhenStarted` (lines 119/123 of the same file already use the helper)
- ui/surveys/SurveysAdapter.kt:67 — `tvDescription` visibility set to VISIBLE but never reset to GONE → recycled rows show the previous row's description (real user-visible bug). also add a NO_POSITION guard to the click listener at :57
- ui/voices/VoicesAdapter.kt:1004 — the app's only nested RecyclerView (chat inside news rows) has no shared `RecycledViewPool` (`setRecycledViewPool` appears zero times in the repo) → one pool field + one setter, ~2 lines
- ui/enterprises/EnterprisesFinancesAdapter.kt:75 — allocates GradientDrawable + LayerDrawable on every even-position bind AND has no else branch, so recycled odd rows keep the striped background (visible bug) → two cached drawables
- ui/feedback/FeedbackAdapter.kt:53 — per-bind ContextCompat.getColor ×2, getDrawable ×2, ColorStateList ×2, and the date formatted twice → cache as adapter fields
- ui/chat/ChatHistoryAdapter.kt:55/115 — `chatTitle` adapter field written during bind, read in click listener → wrong row's title; use the local item
- ui/ratings/RatingsFragment.kt:93/112/122 — bare `repeatOnLifecycle` gated on the FRAGMENT lifecycle while launched in the VIEW scope (only place in the repo) → 3× `collectWhenStarted`
- ui/dashboard/DashboardActivity.kt:187/965 — system-notification receiver registered in onCreate, unregistered in onDestroy; its only job is refreshing visible UI → move to onStart/onStop (both functions already idempotent)
- ui/dashboard/BellDashboardViewModel.kt:48 — `MutableSharedFlow(replay = 0)` + collectWhenStarted consumer = survey-prompt events silently dropped while user is in another tab → replay = 1 (DashboardViewModel.kt:84 already does the buffered variant)
- ui/surveys/SurveyFragment.kt:109/116 — loadSurveys called in BOTH onViewCreated and onResume (whole load runs twice on entry), and the submitList callback at :176 unconditionally scrollToPosition(0) on every emission incl. realtime sync → yanks user to top mid-scroll
- repository/NotificationsRepositoryImpl.kt:296 — dashboard chat badge counts by loading one string row per chat message → `GROUP BY` count query in NewsDao
- repository/ActivitiesRepositoryImpl.kt:165 — getMostOpenedResource loads every activity row then groups in Kotlin → `GROUP BY ... ORDER BY c DESC LIMIT 1`
- repository/NotificationsRepositoryImpl.kt:323 — markNotificationsSynced runs one UPDATE per notification after every sync → wrap in withTransaction (only one withTransaction exists in the whole app)
- MainApplication.kt:119 `showDownload` — written in TeamDetailFragment:311/318, NEVER read anywhere → delete (dead global)
- MainApplication.kt:118 `isCollectionSwitchOn` — global read/written only by CollectionsFragment → make it a fragment field

### 🟢 two-to-three-file wins (still granular)

- model/MyLibrary.kt:31 — missing `Index("courseId")` and `Index("stepId")`; five frequent MyLibraryDao queries (getByCourseId, getByStepId, getCourseResources…) full-scan the biggest table → add indices + bump AppDatabase version (drop-and-resync makes this cheap, but it discards unsynced local writes — ship carefully)
- model/News.kt:23 — missing index for `viewableBy`+`viewableId`; dashboard badge queries full-scan news → composite index + version bump (combine with the MyLibrary one in a single PR to bump the version once)
- repository/CoursesRepositoryImpl.kt:128 — inside a Flow map: one `getExamQuestionCount` query PER STEP per emission → one `stepId IN (...) GROUP BY` query
- repository/SubmissionsRepositoryImpl.kt:374/384 — hasUnfinishedSurveys/hasPendingSurvey issue 2×N queries per course → two batched IN queries
- repository/SurveysRepositoryImpl.kt:254/264 — getByType("surveys") then Kotlin-filter on teamId → `ExamDao.getByTeamIdAndType` already exists, push isTeamShareAllowed into the WHERE
- repository/VoicesRepositoryImpl.kt:159 — gson.fromJson per news row per Flow emission for community visibility; the team path already does it in SQL with a LIKE pattern (NewsDao.kt:35) → same trick, delete the parse
- base/BaseResourceFragment.kt:98 — `goAsync()` on a BroadcastReceiver that is never system-registered (invoked manually at :276) → pendingResult is null → NPE on every network-change broadcast; inline the receiver bodies as suspend functions
- base/BaseVoicesFragment.kt:75/81 — `submitList(currentList.toList())` is a no-op refresh (same item references, areContentsTheSame short-circuits) → onDataChanged silently does nothing; re-fetch or use the targeted payload notifies that already exist
- ui/resources/CollectionsFragment.kt:128 + model/TagData.kt:6 — `isExpanded` var mutated on an item the ListAdapter currently holds → DiffUtil never sees the change; track expansion in a Set owned by the fragment
- ui/voices/VoicesAdapter.kt:447 — full Markwon parse per bind per scroll + new CustomLinkMovementMethod per call (MarkdownUtils.kt:77) → LruCache the rendered Spanned by news.id+_rev, singleton movement method
- ui/surveys/ SurveyFragment+SurveysAdapter+SurveysViewModel — the LAST notifyDataSetChanged in the repo (:186/:193): survey info lives in mutable maps invisible to DiffUtil → combine the three flows into one immutable row model, submitList it (this is the biggest single UI task; it's 4 files but they are conflict-free today)
- services/ResourceDownloadCoordinator.kt:19 + repository/ResourcesRepositoryImpl.kt:402 — both reach MainApplication.applicationScope via EntryPointAccessors from Hilt-managed classes → inject `@ApplicationScope CoroutineScope` (provider exists, di/ServiceModule.kt:48); :402 is also a verbatim duplicate of the coordinator's method — inject the coordinator, delete the block (🟡 ResourcesRepositoryImpl also appears below — keep this PR to the DI lines only)
- MainApplication.kt:117 `syncFailedCount` — plain Int incremented on the sync thread, read from a worker: a real data race (sibling isSyncRunning IS an AtomicBoolean) → @Singleton AtomicInteger tracker, 4 files
- main-thread `File.exists()` in onBindViewHolder — EnterprisesReportsAdapter.kt:91, EnterprisesFinancesAdapter.kt:57, CoursesAdapter.kt:257, ResourcesAdapter.kt:332 (also file.length() per bind) → drop the guard, let Glide's .error() handle missing files / precompute into the row model. one adapter per task = four one-file tasks

### 🟡 check open PRs before assigning

- repository/UserRepositoryImpl.kt:130/144/187 — three getAll()-then-filter spots (getPendingSyncUsers loads every user to take(limit)) → targeted WHERE/LIMIT queries. 🟡 PR #15681 touches UserRepository dedup
- ui/teams/members/MembersAdapter.kt:198 — submitList immediately followed by full-range notify; isViewerLeader is adapter state → move into JoinedMemberData. 🟡 members UI is active territory
- ui/courses/CoursesRepositoryImpl.kt:99/244/290 — getMyCourses/search/filterCourses all hydrate EVERY course's steps then filter → `userId LIKE` pattern query (ResourcesRepositoryImpl.kt:62 already implements the exact trick). 🟡 3 files, courses UI has open PRs (#15683, #15772)
- setHasFixedSize(true) on VoicesFragment.kt:279, NotificationsFragment.kt:60, TeamsTasksFragment.kt:249 (all match_parent/0dp lists). 🟡 PR #15650 is titled "adapter swapping and fixed size" on BaseRecyclerFragment — read it first so the guidance matches

### 🔴 skip this round — an open PR already owns the file

- TeamsRepositoryImpl.kt — the single richest file (N+1 member lookups :1004/:1098/:1136 fixable with the existing getUsersByIds; getAll-then-filter :129/:1234/:1296 fixable with the existing getByIds; observeAll Flows :187/:278/:441/:784) — but PRs #15656, #15662, #15689 all touch it. harvest AFTER they merge; it alone can fill a future round
- ResourcesRepositoryImpl batch inserts (:531/:566 per-row upsert in the sync hot path) — PR #15687 "Fix N+1 query bottleneck in ResourcesRepository batch insertions" is exactly this
- SyncActivity.kt:648 onLogin network collector in applicationScope (leaks the Activity, duplicates uploads per login) — PR #15603 "Fix SyncActivity memory leak from network collector" is exactly this; also #15693 refactors SyncActivity provisioning
- UploadRepositoryImpl — PR #15682
- finances date-filter fragments — PRs #15768, #15773
- resources navigation / openWhichFile — PRs #15770, #15771

### already clean — do NOT let agents "fix" these (they will try)

- all ~50 adapters already extend ListAdapter via DiffUtils.itemCallback; zero hand-rolled ItemCallbacks; no submitList(null) anywhere
- no GlobalScope, no runBlocking, no hardcoded Dispatchers.* outside DispatcherProvider
- Converters.kt / JsonUtils.gson is a cached singleton — the cost is JSON volume per row (7 converter columns on MyLibrary), which is an argument for the SQL-filter tasks above, not for touching Converters
- Utilities.toast's Handler hop is correct and necessary (only nit: hoist the Handler to a field)
- all stateIn/shareIn use viewModelScope + WhileSubscribed(5000); adapter CoroutineScopes cancel/recreate correctly on detach/attach

---

## part 3 — the free-hand rewrite (prompt of prompts v2)

designed for the lowest common denominator across all agentic web platforms:
numbered binary rules any model can self-verify, a grounding process before
writing, a BAD/GOOD contrast pair (the single most portable instruction
technique — jules responds to examples where it ignores prose), graceful
degradation for platforms that can't list open PRs, and a final self-check
(models re-read the end). the frame "you are writing for other agents who
cannot ask questions" turns task quality from a style wish into a functional
requirement.

```
# TASK-GENERATION BRIEF — myPlanet refactor round

## role
you are generating work orders for OTHER coding agents (jules, codex, copilot,
devin, openhands, claude, qwen). they will execute your tasks verbatim and
cannot ask follow-up questions. your output is a plan, not code — you do not
modify any files in this run.

## goal
produce exactly 10 independent tasks that advance this roadmap:

1. finish cleaning the data layer
2. introduce global navigation architecture
3. expand viewmodel and use-case layers
4. complete dependency-injection cleanup
5. consolidate sync and upload workflow
6. migrate ui incrementally to compose
7. optimize remaining performance hotspots
8. improve code health and add tests
north star — never scheduled directly, never blocked:
9. kotlin multiplatform: a platform-free kotlin core — repositories, models,
   sync/upload logic and use cases end up with zero android.* imports
10. compose multiplatform: every compose screen from 6 stays portable — state
    hoisted into viewmodels, no android views inside composables, no direct R.*

every task states which roadmap number it serves and, where true, how it also
moves 9/10 forward.

## this round's focus  <swap this block each round, keep everything else stable>
performance quick wins · micro-optimizations that unblock bigger refactors ·
obvious inefficiencies removable without rewrites
verified spots to prefer:
<paste ~10 lines from the hint bank>

## hard rules — violating any one invalidates the whole plan
R1 exactly 10 tasks, each independently mergeable in any order
R2 no file appears in more than one task
R3 list the currently OPEN pull requests before writing; any file an open PR
   touches is off-limits. if your platform cannot see open PRs, write
   "could not check open PRs" in the header and avoid the hot areas named in
   the focus block instead of guessing
R4 every file path, class and function you cite must exist — open the file and
   confirm before citing. no invented paths, no "e.g." paths
R5 per task: under ~150 changed lines, under ~5 files, no new dependencies,
   no unused code, no TODO placeholders
R6 you write no implementation code — the plan is the deliverable

## process — do these in order, do not skip P1
P1 search the actual code for candidate spots matching the focus block
P2 check open PRs (R3), discard colliding candidates
P3 rank what remains by user impact divided by blast radius
P4 write the top 10 as full work orders using the template below
P5 run the self-check, fix violations silently, then output

## task template — every section, every task
### <n>. <imperative title> (roadmap <numbers>)
context: 2-3 sentences — what is wrong today, why it matters, evidence as file:line
files: exact paths + class/function names to touch, AND neighbors to leave alone
steps: 3-6 numbered concrete edits
acceptance: exact commands that must pass (./gradlew testDefaultDebugUnitTest
  stays green) + the user-visible behavior to verify
size budget: expected changed lines and file count
out of scope: 1-2 lines on what NOT to do

## quality bar
BAD — reject, not executable without questions:
    Optimize the members screen.
    Use a count query instead of loading all members.
GOOD — match this density:
    ### 1. replace member-count list load with the existing count query (roadmap 1+7)
    context: RequestsViewModel.kt:39 calls getJoinedMembers(teamId).size, which
    loads every membership row plus one user query per member, to produce an Int.
    the repository already exposes getJoinedMemberCount(teamId) backed by SQL COUNT.
    files: app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt
    (line 39). do NOT touch TeamsRepositoryImpl — open PRs own it.
    steps: 1. swap the call 2. remove unused imports 3. run the unit tests
    acceptance: ./gradlew testDefaultDebugUnitTest green; requests screen still
    shows the correct joined-member count
    size budget: ~2 changed lines, 1 file
    out of scope: no DAO changes, no repository changes

## output contract
- one markdown document; tasks separated by a line containing only ---
- header lines: date · base commit · open PRs checked (numbers) or "could not check"
- no implementation code blocks; code snippets only as short evidence quotes

## self-check — verify each box before answering; fix, don't explain
[ ] exactly 10 tasks
[ ] no file in two tasks
[ ] every cited path was opened and confirmed to exist
[ ] every task has all 7 template sections
[ ] no task under 15 lines — rewrite any that is
[ ] no task touches a file from the open-PR list
```
