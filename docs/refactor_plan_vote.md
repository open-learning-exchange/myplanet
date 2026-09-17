# Refactor backlog: comparing the five merged plans

Five agents each merged the same 12 task lists (6 agents × 2 prompts, 120 raw tasks) into a verified, rated backlog.
This document compares those five plans, then treats agreement between them as a vote to find the consensus backlog
and the disputed edge cases. All five verified against `c33390b`, which is the current `master` head.

| Plan | Branch / file | Tasks |
|---|---|---|
| OpenHands DeepSeek v4.1 flash (OH) | `refactor-task-backlog-merged-2026-09-17` · `docs/refactor_tasks.md` | 101 |
| Claude Opus 5, run A (Opus A) | `claude/dazzling-gauss-736kap` · `refactor_tasks.md` | 97 |
| Claude Opus 5, run B (Opus B) | `claude/hopeful-pasteur-gsto9a` · `refactor_tasks.md` | 90 |
| Codex Sol 5.6 (Codex) | `codex/extract-tasks-and-generate-reports` · `refactor_tasks.md` | 104 |
| Devin SWE 2 (Devin) | `devin/refactor-tasks-benchmark-merged` · `docs/refactor_tasks.md` | 108 |

## Which plan to follow

Execute from **Devin's** list. Keep **Opus A** open as the risk and sequencing companion for any task whose file appears
more than once in the backlog, or anything under `ui/teams/`. Use **Codex** only for its step-by-step work lists once a
task is chosen, never for its ranking. Ignore **OpenHands**.

Two corrections to apply to Devin as you go: delete `MainApplication.showDownload` instead of relocating it (nothing reads
it), and treat the `Feedback.messageList` item as memoization only (the stale-cache claim is false, the `messages` setter
already clears the cache).

### Scores by point of view (1–100)

| Point of view | OH | Opus A | Opus B | Codex | Devin |
|---|---|---|---|---|---|
| Verification rigor (caught false premises) | 25 | 78 | 78 | 40 | 90 |
| Prioritization (real bugs on top, junk out) | 55 | 85 | 78 | 35 | 88 |
| Actionability from the doc alone | 15 | 80 | 78 | 88 | 75 |
| Risk and sequencing notes (task conflicts, PR collisions) | 10 | 92 | 60 | 45 | 60 |
| Merge quality (duplicates reconciled, not concatenated) | 40 | 85 | 88 | 30 | 75 |
| Token cost to work from | 45 | 65 | 75 | 40 | 85 |
| **Overall** | **30** | **82** | **76** | **48** | **81** |

### What a code check of the contested claims showed

- **Two real bugs exist and belong first in any order.** `getTeamNotifications` computes `hasTask` once outside the per-team
  loop, and `MyLibrary.insertMyLibrary` appends attachments unconditionally on re-sync. Devin, both Opus lists and OpenHands
  put them at the top. Codex has them at 73 and 64.
- **Codex ranks three invalid tasks at 82–83.** The Jules "replace list size with a COUNT query" items are wrong: the fragments
  already render that same list, so a count query adds a DB round trip and removes nothing. Codex also kept the
  NotificationDao `userId IS` task, but every `=` site there already takes a non-null `String`. Devin dropped all four with
  reasons in its `agent_scoring.md`.
- **The Feedback stale-cache claim is false.** The `messages` setter already nulls `cachedMessages`. Devin caught this;
  Opus A, Codex and OpenHands repeat the false half.
- **`showDownload` has no reader anywhere.** Opus A and B say delete it. Devin and Codex say move it into the fragment, which
  preserves dead code.
- **Codex's "verified evidence" is boilerplate.** Every task carries the same sentence plus a file list and no line numbers,
  and its merged tasks concatenate both agents' steps (the ResourceViewer task adds the same constructor parameter twice and
  prescribes two contradictory API changes).
- **OpenHands has no file paths, no evidence and no steps.** It is a ranked title list with provenance.
- **Opus A is the only plan that flags cross-task conflicts consistently**: it sequences the tasks sharing `SyncTimeLogger`,
  `NotificationsRepositoryImpl`, `DownloadService` and `LifeCache`, notes that the notification-formatting task and the
  surveys task encode opposite policies, and marks which files open PRs own.

## Agreement as a vote

Each shipped task was mapped back to its raw source (`agent r<round> #<n>`, round 1 = performance prompt, round 2 =
repository-boundaries prompt, rounds by list position). A plan that kept a raw task, alone or inside a merged task, counts as
one vote. Ratings are the plan's own scale; Codex runs hot at the top and OpenHands runs compressed, so compare ranks
within a plan rather than raw numbers across plans.

| Kept by | Raw tasks |
|---|---|
| 5 of 5 | 95 |
| 4 of 5 | 10 |
| 3 of 5 | 8 |
| 2 of 5 | 2 |
| 1 of 5 | 4 |
| 0 of 5 | 1 (Kimi r1 #6, the JsonUtils/Gson audit) |

### Consensus backlog: unanimous, sorted by average rating

The first ~25 rows are the backlog everyone agrees on; work them top down.

| Votes | Avg | Spread | Raw task | OH | Opus A | Opus B | Codex | Devin | Task |
|---|---|---|---|---|---|---|---|---|---|
| 5 | 87 | 23 | devin r2 #2 | 84 | 96 | 93 | 73 | 90 | getTeamNotifications: compute the task badge per team (hasTask is currently global, so badges are wrong) |
| 5 | 86 | 7 | claude r1 #1 | 82 | 89 | 84 | 86 | 88 | SyncTimeLogger: delete the write-only detailedLogs accumulator; make per-key appends thread-safe |
| 5 | 86 | 7 | devin r1 #8 | 82 | 89 | 84 | 86 | 88 | SyncTimeLogger: delete the write-only detailedLogs accumulator; make per-key appends thread-safe |
| 5 | 83 | 10 | claude r2 #9 | 77 | 87 | 83 | 84 | 84 | isAlreadyShared: answer with SQL EXISTS instead of hydrating every matching news row |
| 5 | 81 | 15 | claude r2 #1 | 71 | 86 | 81 | 81 | 84 | ConfigurationsRepository.checkVersion: drop the SharedPrefManager parameter |
| 5 | 81 | 28 | devin r1 #1 | 67 | 92 | 90 | 64 | 90 | MyLibrary.insertMyLibrary: hoist the SharedPreferences read and stop duplicating attachments |
| 5 | 80 | 20 | claude r2 #2 | 68 | 85 | 76 | 88 | 82 | Viewer activities and ResourcesExitCoordinator: resolve the current user inside ResourceViewerViewModel |
| 5 | 80 | 20 | devin r2 #8 | 68 | 85 | 76 | 88 | 82 | Viewer activities and ResourcesExitCoordinator: resolve the current user inside ResourceViewerViewModel |
| 5 | 79 | 12 | claude r2 #4 | 71 | 83 | 78 | 81 | 82 | ActivitiesRepository: delete the four unused resource-open overloads |
| 5 | 79 | 21 | claude r2 #3 | 67 | 81 | 77 | 88 | 80 | Storage breakdown disk scan: single-pass walk, moved out of the ViewModel into ResourcesRepository |
| 5 | 77 | 13 | claude r1 #3 | 76 | 83 | 70 | 78 | 78 | ChatSearch: drop redundant ignoreCase on pre-normalized text and assemble buckets in one pass |
| 5 | 77 | 13 | codex r1 #5 | 76 | 83 | 70 | 78 | 78 | ChatSearch: drop redundant ignoreCase on pre-normalized text and assemble buckets in one pass |
| 5 | 77 | 13 | grok r2 #5 | 76 | 83 | 70 | 78 | 78 | ChatSearch: drop redundant ignoreCase on pre-normalized text and assemble buckets in one pass |
| 5 | 77 | 19 | devin r2 #1 | 65 | 84 | 81 | 73 | 82 | Achievement resource picker: load only id and title for the my_library dialog |
| 5 | 76 | 10 | claude r2 #8 | 71 | 76 | 76 | 81 | 78 | NotificationsRepository: remove dead getNotifications; route Date() through the injected TimeProvider |
| 5 | 76 | 15 | claude r2 #10 | 66 | 81 | 75 | 81 | 77 | RatingsRepository: delete the getRatingsById null-guard wrapper |
| 5 | 76 | 28 | claude r1 #2 | 65 | 88 | 82 | 60 | 84 | SyncManager resource cleanup: drop the re-filter and the always-true size guard |
| 5 | 75 | 12 | codex r2 #1 | 68 | 80 | 75 | 80 | 74 | ChallengePrompter: stop reaching into DashboardViewModel for the progress math |
| 5 | 75 | 20 | claude r2 #7 | 69 | 74 | 68 | 88 | 77 | ProgressRepository: return a typed CoursesProgressRow instead of JsonArray parsed in the ViewModel |
| 5 | 75 | 16 | claude r2 #5 | 65 | 78 | 74 | 81 | 76 | FeedbackRepository: drop the internal-only builders and stop passing UserEntity into the list query |
| 5 | 75 | 11 | claude r2 #6 | 70 | 75 | 72 | 81 | 75 | HealthRepository: absorb the pending, upload, mark sequence out of UploadToShelfService |
| 5 | 74 | 18 | codex r2 #5 | 65 | 68 | 83 | 80 | 75 | FeedbackListFragment: make the repository flow the only list refresh source |
| 5 | 74 | 14 | devin r2 #7 | 66 | 70 | 74 | 80 | 78 | Carve the achievement surface out of UserRepository |
| 5 | 73 | 13 | grok r2 #1 | 74 | 76 | 63 | 75 | 76 | InlineResourceAdapter: resolve the File once and use FileExistenceCache instead of per-bind re-stats |
| 5 | 73 | 15 | jules r2 #3 | 65 | 77 | 67 | 80 | 74 | ChatRepositoryImpl: inject ServerReachabilityProvider instead of the static call |
| 5 | 72 | 15 | jules r2 #2 | 65 | 75 | 67 | 80 | 74 | BellDashboardViewModel: inject ServerReachabilityProvider instead of the static call |
| 5 | 72 | 15 | devin r2 #3 | 65 | 80 | 69 | 73 | 74 | RequestsUiState: carry the current user instead of a second session read in the fragment |
| 5 | 72 | 19 | devin r2 #6 | 61 | 73 | 73 | 80 | 74 | DialogUtils: take a checkCheckSum lambda instead of ConfigurationsRepository |
| 5 | 72 | 16 | devin r2 #5 | 64 | 78 | 65 | 80 | 73 | AddResourceViewModel: resolve the current user inside the ViewModel |
| 5 | 72 | 14 | kimi r2 #2 | 66 | 63 | 77 | 77 | 76 | SurveysRepository: return raw counts and dates, format in the adapter |
| 5 | 72 | 22 | kimi r2 #3 | 63 | 74 | 63 | 85 | 73 | ActivitiesRepositoryImpl: drop android.util.Log and the dead Context parameter |
| 5 | 71 | 21 | kimi r2 #8 | 67 | 65 | 64 | 85 | 74 | Take Context off the submissions PDF path and split PDF rendering into a writer helper |
| 5 | 71 | 12 | devin r2 #4 | 65 | 77 | 67 | 73 | 73 | HealthExaminationState: move the examiner lookup into state |
| 5 | 71 | 25 | kimi r2 #1 | 65 | 61 | 66 | 86 | 77 | ReplyViewModel: absorb the last repository and session calls out of ReplyActivity |
| 5 | 71 | 24 | codex r2 #10 | 56 | 78 | 68 | 73 | 80 | SubmissionsRepositoryImpl: fetch one pending submission with LIMIT 1 instead of materializing a list |
| 5 | 71 | 25 | codex r2 #2 | 66 | 62 | 62 | 87 | 76 | CoursesStepsViewModel: move Context/path work and next-step prefetch behind a coordinator or ResourcesRepository |
| 5 | 71 | 25 | grok r1 #9 | 66 | 62 | 62 | 87 | 76 | CoursesStepsViewModel: move Context/path work and next-step prefetch behind a coordinator or ResourcesRepository |
| 5 | 71 | 17 | kimi r2 #7 | 61 | 72 | 70 | 72 | 78 | CourseDao: replace the @RawQuery SimpleSQLiteQuery with a parameterized query |
| 5 | 70 | 8 | devin r2 #9 | 68 | 66 | 71 | 73 | 74 | UserDataWorker: collapse six copy-pasted upload-await blocks into one helper |
| 5 | 70 | 27 | claude r1 #5 | 65 | 84 | 57 | 63 | 81 | CoursesItemUtils.bindCover: reuse FileExistenceCache for the main-thread exists() stat |
| 5 | 70 | 36 | codex r1 #6 | 59 | 89 | 53 | 86 | 62 | SyncTimeLogger.generateSummary: aggregate each endpoint and model once instead of re-summing |
| 5 | 70 | 17 | devin r1 #6 | 74 | 76 | 63 | 59 | 76 | InlineResourceAdapter: resolve the File once and use FileExistenceCache instead of per-bind re-stats |
| 5 | 69 | 20 | claude r1 #6 | 71 | 77 | 59 | 60 | 79 | NetworkModule: share one OkHttp ConnectionPool between the standard and reachability clients |
| 5 | 69 | 12 | codex r2 #9 | 64 | 71 | 63 | 73 | 75 | AchievementUploader: batch successful upload acknowledgements instead of one Room update each |
| 5 | 69 | 38 | devin r1 #7 | 69 | 91 | 53 | 57 | 75 | LifeAdapter: move contentDescription strings from touch and click handlers to bind time |
| 5 | 69 | 8 | codex r2 #4 | 66 | 66 | 66 | 73 | 74 | TeamViewModel: remove sync-table events and collect repository flows only |
| 5 | 69 | 18 | codex r2 #3 | 66 | 62 | 65 | 80 | 71 | ChatViewModel: observe chat history through ChatRepository instead of RealtimeSyncManager |
| 5 | 69 | 19 | codex r2 #8 | 63 | 69 | 61 | 80 | 71 | RetryQueueWorker: move HTTP execution and response classification behind RetryRepository |
| 5 | 69 | 24 | jules r2 #9 | 56 | 79 | 57 | 80 | 72 | UserRepositoryImpl: use the injected application context, not MainApplication.context |
| 5 | 68 | 10 | kimi r2 #5 | 67 | 64 | 65 | 72 | 74 | PublicSurveyViewModel plus payload builder: get buildPublicAnswers and sanitizeRespondent out of the Activity |
| 5 | 68 | 10 | grok r1 #8 | 67 | 64 | 65 | 72 | 74 | PublicSurveyViewModel plus payload builder: get buildPublicAnswers and sanitizeRespondent out of the Activity |
| 5 | 68 | 23 | codex r1 #1 | 64 | 82 | 73 | 59 | 64 | CoursesAdapter.removeCourses: constant-time membership instead of an O(n*m) filter |
| 5 | 68 | 20 | codex r2 #7 | 62 | 67 | 60 | 80 | 72 | MyHealthFragment: route health refreshes through HealthViewModel refresh intents |
| 5 | 68 | 36 | kimi r2 #9 | 59 | 50 | 72 | 86 | 72 | CoursesRepositoryImpl: take the PendingCourseResource buffer out of the singleton |
| 5 | 67 | 21 | kimi r2 #6 | 66 | 58 | 60 | 79 | 74 | ProcessUserDataActivity: introduce a ViewModel for the member-upload callback chain |
| 5 | 67 | 26 | kimi r1 #4 | 63 | 66 | 54 | 80 | 73 | CalendarViewModel: extract meetup loading into a loader helper and apply distinctUntilChanged to the teams flow |
| 5 | 67 | 19 | devin r2 #10 | 61 | 61 | 62 | 80 | 72 | DashboardElementActivity: introduce DashboardElementViewModel |
| 5 | 67 | 19 | claude r1 #10 | 64 | 79 | 60 | 60 | 72 | HealthExaminationAdapter: drop the regex split and the no-op trimIndent on a single-line resource |
| 5 | 67 | 22 | codex r2 #6 | 61 | 64 | 58 | 80 | 72 | SurveyFragment and SurveysViewModel: centralize scope and reload state in the ViewModel |
| 5 | 67 | 22 | claude r1 #8 | 58 | 80 | 64 | 60 | 73 | StorageCategoryUiState: compute checkedCount and allChecked in one stored pass |
| 5 | 67 | 13 | grok r1 #3 | 71 | 70 | 58 | 64 | 71 | DictionaryFileReaderImpl: inject StoragePathResolver instead of @ApplicationContext Context |
| 5 | 67 | 20 | devin r1 #5 | 71 | 75 | 55 | 59 | 74 | WebViewActivity: resolve the local resource directory once instead of per navigation |
| 5 | 67 | 14 | grok r1 #5 | 68 | 58 | 70 | 65 | 72 | ResourcesRepositoryImpl: remove MainApplication.context and MainApplication.applicationScope usages |
| 5 | 67 | 15 | jules r2 #4 | 64 | 68 | 58 | 73 | 70 | CollectionsFragment: take isCollectionSwitchOn out of MainApplication |
| 5 | 67 | 21 | grok r2 #6 | 64 | 70 | 54 | 75 | 70 | LifeCache: add an in-memory layer in front of SharedPreferences and Gson |
| 5 | 66 | 27 | grok r2 #3 | 67 | 50 | 64 | 77 | 72 | DownloadService: stop SharedPreferences thrash in queue bookkeeping |
| 5 | 66 | 9 | grok r1 #4 | 67 | 65 | 61 | 65 | 70 | Take Context off the submissions PDF path and split PDF rendering into a writer helper |
| 5 | 65 | 11 | kimi r1 #1 | 61 | 69 | 68 | 59 | 70 | PersonalsAdapter: cache per-row formatted date strings |
| 5 | 65 | 32 | grok r2 #4 | 61 | 72 | 47 | 79 | 66 | ResourcesRepositoryImpl.search: single-pass startsWith ranking instead of two lists plus concat |
| 5 | 65 | 9 | kimi r1 #3 | 61 | 68 | 68 | 59 | 68 | HealthUsersAdapter.bindDate: cache per-row formatted join dates |
| 5 | 64 | 37 | grok r1 #10 | 63 | 66 | 43 | 80 | 70 | CalendarViewModel: extract meetup loading into a loader helper and apply distinctUntilChanged to the teams flow |
| 5 | 64 | 26 | devin r1 #3 | 59 | 82 | 56 | 59 | 66 | HealthExaminationActivity: Set lookup for diagnoses plus hoisted per-checkbox styling |
| 5 | 64 | 21 | grok r2 #8 | 62 | 63 | 51 | 72 | 71 | ResourcesPreviewLoader: memoize audio, csv and text preview work |
| 5 | 63 | 14 | kimi r1 #5 | 54 | 67 | 68 | 58 | 68 | FeedbackAdapter.onBindViewHolder: cache the formatted open date |
| 5 | 63 | 20 | jules r2 #6 | 60 | 53 | 62 | 73 | 66 | Decouple TeamPagerAdapter and TeamDetailFragment from MainApplication.listener |
| 5 | 63 | 21 | kimi r1 #7 | 59 | 65 | 71 | 50 | 69 | TimeUtils: replace seven printStackTrace() calls with tagged Log.w |
| 5 | 62 | 21 | jules r2 #10 | 62 | 55 | 52 | 73 | 70 | NotificationActionReceiver: own its coroutine scope instead of MainApplication.applicationScope |
| 5 | 62 | 37 | grok r2 #7 | 66 | 54 | 42 | 79 | 70 | getEnrichedNotifications: single pass plus ready-to-bind display strings built in the repository DTO |
| 5 | 62 | 34 | devin r1 #2 | 60 | 79 | 45 | 59 | 67 | ExamQuestion.insertCorrectChoice: hoist the loop-invariant JSON read and short-circuit |
| 5 | 62 | 25 | codex r1 #9 | 60 | 75 | 50 | 59 | 65 | ExamAnswerUtils: reject mismatched multi-select answers before sorting both collections |
| 5 | 61 | 30 | codex r1 #3 | 57 | 76 | 46 | 59 | 68 | CoursesViewModel.loadCourses: drop the pointless coroutineScope/async/await wrapper |
| 5 | 61 | 23 | claude r1 #9 | 55 | 74 | 51 | 60 | 66 | ProgressViewModel: read each progress JSON field once |
| 5 | 61 | 29 | kimi r2 #4 | 56 | 52 | 50 | 79 | 68 | SyncRepositoryImpl: push shelf-dispatch map construction behind an interface seam |
| 5 | 61 | 17 | codex r1 #4 | 58 | 72 | 55 | 59 | 60 | ResourcesListFilter.toSignature: set-based tag signature without sorting |
| 5 | 61 | 31 | grok r1 #7 | 66 | 52 | 41 | 72 | 72 | getEnrichedNotifications: single pass plus ready-to-bind display strings built in the repository DTO |
| 5 | 59 | 24 | kimi r1 #10 | 54 | 63 | 71 | 47 | 62 | VoicesLabelManager: replace printStackTrace() with tagged Log.w |
| 5 | 59 | 26 | codex r1 #8 | 57 | 70 | 44 | 59 | 63 | NotificationsViewModel: drop the redundant distinct pass over ordered types |
| 5 | 59 | 18 | codex r1 #10 | 55 | 65 | 48 | 59 | 66 | MyTeam.serialize: strip null fields in one iterator pass instead of filter-then-remove |
| 5 | 57 | 29 | grok r2 #9 | 56 | 56 | 40 | 69 | 63 | TTSManager.stripMarkdown: fewer passes and intermediate strings |
| 5 | 57 | 10 | codex r1 #2 | 58 | 59 | 49 | 59 | 58 | DownloadService.getNextUrl: single-pass minimum instead of an intermediate list |
| 5 | 55 | 42 | devin r1 #4 | 62 | 57 | 30 | 55 | 72 | Answer.valueChoicesArray: memoize the re-parsing getter |
| 5 | 53 | 36 | devin r1 #10 | 58 | 48 | 35 | 52 | 71 | ChatShareTargetAdapter: stop allocating a Typeface on every group-header bind |
| 5 | 50 | 33 | codex r1 #7 | 48 | 45 | 33 | 66 | 57 | UploadConfigs: resolve the lazy teams repository once per result batch |
| 5 | 49 | 17 | jules r1 #5 | 47 | 44 | 42 | 59 | 52 | CoursesAdapter.areAllSelected: short-circuit the count when isMyCourseLib |
| 5 | 44 | 37 | jules r1 #8 | 47 | 42 | 22 | 59 | 50 | TeamCalendarFragment: use map instead of mapTo(mutableListOf()) |

### Not unanimous

The four single-plan tasks are all Codex-only and all invalid on inspection. The two-plan tasks are Jules micro-optimizations
rated in the 40s. Skip all six.

| Votes | Avg | Spread | Raw task | OH | Opus A | Opus B | Codex | Devin | Task |
|---|---|---|---|---|---|---|---|---|---|
| 4 | 68 | 27 | devin r1 #9 | 73 | 85 | – | 58 | 58 | Feedback.messageList: memoize the derived list and invalidate parsedMessages in setMessages |
| 4 | 64 | 18 | claude r1 #7 | 57 | 71 | 56 | – | 74 | Hoist constant string resource lookups out of three adapter bind paths |
| 4 | 64 | 9 | kimi r1 #2 | 61 | 68 | 68 | 59 | – | UserArrayAdapter: cache per-row formatted join dates |
| 4 | 64 | 18 | jules r2 #5 | – | 60 | 55 | 73 | 68 | `MainApplication.showDownload` is write-only state |
| 4 | 64 | 12 | kimi r1 #8 | 59 | 64 | 71 | – | 60 | News: replace five printStackTrace() calls with tagged Log.w |
| 4 | 63 | 20 | grok r2 #2 | 65 | 62 | 52 | 72 | – | FileUtils: memoize findHtmlCoverImage, make getFileExtension string-only, cache a non-empty checkFileExist |
| 4 | 54 | 34 | grok r2 #10 | 38 | 60 | – | 72 | 45 | ChatHistoryAdapter: DiffUtil payloads and bind micro-costs |
| 4 | 53 | 13 | jules r1 #1 | 52 | 46 | – | 59 | 55 | StorageCategories.indexOf: check the raw key before lowercasing |
| 4 | 48 | 21 | jules r1 #4 | 47 | – | 38 | 59 | 50 | CrashLogStore: replace the sequence pipeline in the pending-file cap check |
| 4 | 44 | 28 | jules r1 #7 | 43 | – | 28 | 56 | 50 | DownloadUtils.downloadAllFiles: pre-size or simplify the ArrayList mapping |
| 3 | 70 | 19 | claude r1 #4 | 65 | – | – | 63 | 82 | FileUtils: memoize findHtmlCoverImage, make getFileExtension string-only, cache a non-empty checkFileExist |
| 3 | 68 | 26 | kimi r1 #9 | 67 | 81 | – | – | 55 | Storage breakdown disk scan: single-pass walk, moved out of the ViewModel into ResourcesRepository |
| 3 | 66 | 15 | grok r1 #1 | – | 73 | – | 58 | 68 | `PersonalsRepositoryImpl` mixes a static device-name call with the injected provider |
| 3 | 64 | 11 | grok r1 #2 | 57 | 67 | – | 68 | – | Move LifeCache out of the repository package into a data/cache package |
| 3 | 53 | 17 | kimi r2 #10 | 43 | 55 | – | – | 60 | Give the TeamsFinances, TeamsMembers and TeamsNotifications interfaces real consumers |
| 3 | 51 | 17 | jules r1 #3 | 51 | – | – | 59 | 42 | TeamsRepositoryImpl: use mapNotNullTo for the joined-member name sets |
| 3 | 44 | 10 | jules r1 #9 | 46 | 38 | – | – | 48 | BaseDashboardFragment.renderMyCourses: cache filteredCourses.size and reuse it for isEmpty |
| 3 | 43 | 13 | jules r1 #2 | 47 | 35 | – | – | 48 | ResourcesFragment.applyFiltersAndUpdateUI: cache the repeated filteredList.size |
| 2 | 44 | 8 | jules r1 #6 | 48 | – | – | – | 40 | ProgressRepositoryImpl: stop building a HashSet just to count passed steps |
| 2 | 42 | 12 | jules r1 #10 | 36 | – | – | – | 48 | MembersFragment: extract the repeated state.members.size |
| 1 | 83 | 0 | jules r2 #7 | – | – | – | 83 | – | replace members list load with count query in MembersFragment (roadmap 1+7) |
| 1 | 82 | 0 | jules r2 #8 | – | – | – | 82 | – | replace list-size load with the existing count query in NotificationsViewModel (roadmap 1+7) |
| 1 | 82 | 0 | jules r2 #1 | – | – | – | 82 | – | replace list-size load with the existing count query in RequestsViewModel (roadmap 1+7) |
| 1 | 68 | 0 | grok r1 #6 | – | – | – | 68 | – | Fix nullable `userId` SQL predicates on `NotificationDao` |

### Disputed among the unanimous (rating spread ≥ 25)

| Votes | Avg | Spread | Raw task | OH | Opus A | Opus B | Codex | Devin | Task |
|---|---|---|---|---|---|---|---|---|---|
| 5 | 55 | 42 | devin r1 #4 | 62 | 57 | 30 | 55 | 72 | Answer.valueChoicesArray: memoize the re-parsing getter |
| 5 | 69 | 38 | devin r1 #7 | 69 | 91 | 53 | 57 | 75 | LifeAdapter: move contentDescription strings from touch and click handlers to bind time |
| 5 | 64 | 37 | grok r1 #10 | 63 | 66 | 43 | 80 | 70 | CalendarViewModel: extract meetup loading into a loader helper and apply distinctUntilChanged to the teams flow |
| 5 | 62 | 37 | grok r2 #7 | 66 | 54 | 42 | 79 | 70 | getEnrichedNotifications: single pass plus ready-to-bind display strings built in the repository DTO |
| 5 | 44 | 37 | jules r1 #8 | 47 | 42 | 22 | 59 | 50 | TeamCalendarFragment: use map instead of mapTo(mutableListOf()) |
| 5 | 70 | 36 | codex r1 #6 | 59 | 89 | 53 | 86 | 62 | SyncTimeLogger.generateSummary: aggregate each endpoint and model once instead of re-summing |
| 5 | 68 | 36 | kimi r2 #9 | 59 | 50 | 72 | 86 | 72 | CoursesRepositoryImpl: take the PendingCourseResource buffer out of the singleton |
| 5 | 53 | 36 | devin r1 #10 | 58 | 48 | 35 | 52 | 71 | ChatShareTargetAdapter: stop allocating a Typeface on every group-header bind |
| 5 | 62 | 34 | devin r1 #2 | 60 | 79 | 45 | 59 | 67 | ExamQuestion.insertCorrectChoice: hoist the loop-invariant JSON read and short-circuit |
| 5 | 50 | 33 | codex r1 #7 | 48 | 45 | 33 | 66 | 57 | UploadConfigs: resolve the lazy teams repository once per result batch |
| 5 | 65 | 32 | grok r2 #4 | 61 | 72 | 47 | 79 | 66 | ResourcesRepositoryImpl.search: single-pass startsWith ranking instead of two lists plus concat |
| 5 | 61 | 31 | grok r1 #7 | 66 | 52 | 41 | 72 | 72 | getEnrichedNotifications: single pass plus ready-to-bind display strings built in the repository DTO |
| 5 | 61 | 30 | codex r1 #3 | 57 | 76 | 46 | 59 | 68 | CoursesViewModel.loadCourses: drop the pointless coroutineScope/async/await wrapper |
| 5 | 61 | 29 | kimi r2 #4 | 56 | 52 | 50 | 79 | 68 | SyncRepositoryImpl: push shelf-dispatch map construction behind an interface seam |
| 5 | 57 | 29 | grok r2 #9 | 56 | 56 | 40 | 69 | 63 | TTSManager.stripMarkdown: fewer passes and intermediate strings |
| 5 | 81 | 28 | devin r1 #1 | 67 | 92 | 90 | 64 | 90 | MyLibrary.insertMyLibrary: hoist the SharedPreferences read and stop duplicating attachments |
| 5 | 76 | 28 | claude r1 #2 | 65 | 88 | 82 | 60 | 84 | SyncManager resource cleanup: drop the re-filter and the always-true size guard |
| 5 | 70 | 27 | claude r1 #5 | 65 | 84 | 57 | 63 | 81 | CoursesItemUtils.bindCover: reuse FileExistenceCache for the main-thread exists() stat |
| 5 | 66 | 27 | grok r2 #3 | 67 | 50 | 64 | 77 | 72 | DownloadService: stop SharedPreferences thrash in queue bookkeeping |
| 5 | 67 | 26 | kimi r1 #4 | 63 | 66 | 54 | 80 | 73 | CalendarViewModel: extract meetup loading into a loader helper and apply distinctUntilChanged to the teams flow |
| 5 | 64 | 26 | devin r1 #3 | 59 | 82 | 56 | 59 | 66 | HealthExaminationActivity: Set lookup for diagnoses plus hoisted per-checkbox styling |
| 5 | 59 | 26 | codex r1 #8 | 57 | 70 | 44 | 59 | 63 | NotificationsViewModel: drop the redundant distinct pass over ordered types |
| 5 | 71 | 25 | kimi r2 #1 | 65 | 61 | 66 | 86 | 77 | ReplyViewModel: absorb the last repository and session calls out of ReplyActivity |
| 5 | 71 | 25 | codex r2 #2 | 66 | 62 | 62 | 87 | 76 | CoursesStepsViewModel: move Context/path work and next-step prefetch behind a coordinator or ResourcesRepository |
| 5 | 71 | 25 | grok r1 #9 | 66 | 62 | 62 | 87 | 76 | CoursesStepsViewModel: move Context/path work and next-step prefetch behind a coordinator or ResourcesRepository |
| 5 | 62 | 25 | codex r1 #9 | 60 | 75 | 50 | 59 | 65 | ExamAnswerUtils: reject mismatched multi-select answers before sorting both collections |

Reading of the biggest gaps:

- **Answer.valueChoicesArray memoize** (spread 42): Opus B rated it 30 because the getter has two call sites that each read once. Low priority.
- **LifeAdapter contentDescription in listeners** (spread 38): Opus A rates it 91 because it is also an accessibility defect. Cheap, do it.
- **CalendarViewModel meetup loading** (spread 37): the split is scope, `distinctUntilChanged` only versus extracting a loader class. Do the small one.
- **getEnrichedNotifications single pass plus formatting in DTOs** (spread 37): pushes string resources into the data layer, the opposite policy from the Surveys raw-values task. Decide the policy before touching either.
- **SyncTimeLogger summary aggregation** (spread 36): bundle it into the main SyncTimeLogger task.
- **PendingCourseResource buffer out of the singleton** (spread 36): the task starts with "trace the lifecycle first", so it is unspecified. Scope it before scheduling.
