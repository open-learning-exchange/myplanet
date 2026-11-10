# myPlanet refactor megaplan: Opus × Devin × Codex (2026-11-10, base `2093a62`)

This file merges three merged backlogs, each built from the same 18 agent lists (180 raw tasks):

| Source | Branch | Merged tasks | What it contributes here |
|---|---|---|---|
| Opus 5.5 | `claude/focused-gauss-satual` | 113 | **Backbone.** Its full work orders, checked against `2093a62`, are the task text below |
| Devin SWE 2 | `devin/1794309506-merged-refactor-backlog` | 140 | Second rating and cross-reference; full text for the contested tasks in Appendix A |
| Codex Sol 6.1 | `claude/verified-refactor-backlog-20261110-92606189` | 131 | Third rating; its scope lines and corrections become the **guardrails** on each task |

**Result: 113 tasks to do, ordered by cross-plan consensus, plus 7 optional contested tasks (Appendix A) and 17 rejected ones (Appendix B).** The full crosswalk and method are in `refactor_tasks_crosswalk.md`.

## How to read a task

- **score**: the mean of the task's percentile rank within each plan that kept it (100 = top of every plan). The three plans use different rating scales (Opus 27-78, Devin 34-95, Codex 44-95), so raw ratings are not averaged.
- **O / D / C**: the original Opus, Devin and Codex ratings. The `refs` line gives the original task ids, so you can open any plan's version.
- **guardrails**: Codex's scope lines (what to preserve) plus any part Opus or Codex cut from a raw task. They are constraints, not extra work.
- **shares files with**: other megaplan tasks whose `files:` section names the same file. Don't run those in parallel; land them in sequence or bundle them.
- The task body below the guardrails is Opus's canonical work order, unchanged except where a **Megaplan correction** says otherwise.

## Index


**Wave 1: highest consensus (all three plans rank these near the top)**

| M | score | O / D / C | task |
|---|---|---|---|
| [M001](#m001) | 97 | 76 / 91 / 91 | DashboardViewModel.evaluateChallengeDialog: evaluate the (expired) date window before launching its five queries |
| [M002](#m002) | 97 | 71 / 85 / 95 | SubmissionsPayloadBuilder.submissionPayload: rethrow CancellationException instead of returning a partial payload |
| [M003](#m003) | 95 | 69 / 95 / 91 | RetryUtils.retry: rethrow CancellationException and drop the stderr printStackTrace |
| [M004](#m004) | 93 | 71 / 75 / 93 | DictionaryRepositoryImpl.insertDictionaryData: check the DB count before the seed-file existence |
| [M005](#m005) | 92 | 55 / 85 / 93 | QuestionDao.deleteStaleQuestions: @Transaction + bounded positive delete per exam |
| [M006](#m006) | 91 | 69 / 74 / 93 | UserDataWorker / AutoSyncWorker: await healthRepository.syncPendingHealthExaminations instead of fire-and-forget into appScope |
| [M007](#m007) | 90 | 71 / 77 / 86 | SurveysRepositoryImpl.getSurveyFormState: grouped COUNT query instead of loading every ExamQuestion |
| [M008](#m008) | 89 | 71 / 72 / 88 | CoursesRepositoryImpl: read titles/ids from courseDao instead of mapCourse (which loads all steps) |
| [M009](#m009) | 89 | 55 / 86 / 87 | SubmissionsRepositoryImpl: @Transaction batch for rev updates and mark-uploaded |
| [M010](#m010) | 84 | 71 / 71 / 86 | ProgressRepositoryImpl.getCourseProgress: count course steps in SQL instead of loading step bodies |
| [M011](#m011) | 84 | 69 / 71 / 88 | TakeCourseViewModel.loadCourse (+ CoursesViewModel.loadCourses): map failures to NotFound, rethrow cancellation, log instead of printStackTrace |
| [M012](#m012) | 83 | 55 / 71 / 91 | ResourcesPreviewLoader.getTextPreview: cap the read at 4096 chars before the 8-line limit |
| [M013](#m013) | 83 | 50 / 77 / 87 | ResourcesFragment: first-wins id/localAddress/tag maps instead of linear find scans |
| [M014](#m014) | 83 | 50 / 80 / 85 | UploadConfigs: wrap each batch's mark-uploaded writes in AppDatabase.withTransaction |
| [M015](#m015) | 82 | 71 / 71 / 83 | TeamsVoicesFragment: drop the duplicate one-shot feed load; badge watermark via countTopLevelByTeam |
| [M016](#m016) | 80 | 78 / 74 / 75 | Warm a master debug build cache so first pushes of new branches stop cold-compiling |
| [M017](#m017) | 78 | 69 / 71 / 79 | Retire the single-test Robolectric RetryInterceptorEventTest into plain JUnit |
| [M018](#m018) | 78 | 55 / 77 / 77 | NotificationsViewModel: remove @ApplicationContext / R.string (strings holder or formatter) |
| [M019](#m019) | 77 | 55 / 82 / 75 | Narrow learning/resource ViewModels to UserReadRepository (ResourcesAccess, Resources, SubmissionDetail, ResourceViewer, TakeCourse, CoursesSteps) |
| [M020](#m020) | 77 | 55 / 82 / 75 | Narrow home/dashboard ViewModels to UserReadRepository (Calendar, CommunityTab, DashboardElement, Activities, Personals) |
| [M021](#m021) | 76 | 50 / 71 / 84 | TakeCourseViewModel.loadCourse: overlap independent reads inside coroutineScope; reuse course.courseSteps |
| [M022](#m022) | 74 | 58 / 71 / 77 | VoicesRepositoryImpl / MyCourse: drain the download-link buffers after each save |
| [M023](#m023) | 73 | 38 / 85 / 90 | HealthExaminationViewModel.saveExamination: rethrow CancellationException, Log.w instead of printStackTrace |
| [M024](#m024) | 72 | 48 / 66 / 85 | ProgressRepositoryImpl.saveEarnedExamPasses: one submissions+answers load instead of per-step queries |
| [M025](#m025) | 72 | 38 / 80 / 91 | CoursesStepsViewModel.loadStep: launch the priority download and next-step prefetch as children of loadDataJob |

**Wave 2: solid wins**

| M | score | O / D / C | task |
|---|---|---|---|
| [M026](#m026) | 70 | 67 / 71 / 73 | PdfThumbnailLoaderTest: drop the lone @Config(sdk=[28]) pin (and the 28 jar from robolectricSdkJars) |
| [M027](#m027) | 68 | 52 / 64 / 83 | UserRepositoryImpl.ensureUserSecurityKeys/saveKeyIv: skip the upsert when key/iv exist, lazy keygen, drop TextUtils; drop HealthExaminationViewModel's double user read |
| [M028](#m028) | 65 | 39 / 71 / 78 | MyLifeDao: replace parameter-OR userId predicates with a Kotlin branch so the userId index is usable |
| [M029](#m029) | 63 | 55 / 66 / 72 | Promote getCurrentUserId to UserReadRepository; narrow LifeViewModel |
| [M030](#m030) | 63 | 53 / 71 / 71 | Migrate the last free Mockito tests to MockK |
| [M031](#m031) | 63 | 38 / 72 / 82 | UserRepositoryImplTest: class-level static mocks, per-test stubs + clearMocks |
| [M032](#m032) | 63 | 34 / 85 / 86 | NotificationsViewModel.toggleGroupExpansion: shared groupTypeOf for default-expanded and unread |
| [M033](#m033) | 62 | 55 / 64 / 75 | UserRepositoryImpl.sortUsers: compute lowercase keys once per sort (decorate-sort) |
| [M034](#m034) | 62 | 38 / 72 / 80 | LoginSyncManagerTest: class-level static mocks, per-test stubs + clearMocks |
| [M035](#m035) | 61 | 48 / 71 / 71 | Move getSyncedUsers/getSyncedUserByName/getPendingSyncUsers to UserSyncRepository; rewire UploadToShelfService |
| [M036](#m036) | 61 | 45 / 48 / 94 | LifeRepositoryImpl.updateMyLifeListOrder: derive the owner from the caller, not the first item |
| [M037](#m037) | 61 | 37 / 77 / 83 | automerge.sh: one gh pr view for state/reviewDecision/mergeable per PR |
| [M038](#m038) | 60 | 39 / 64 / 80 | FileUtils.getDisplayName / resolveUriToPath: DISPLAY_NAME projection instead of null |
| [M039](#m039) | 59 | 39 / 64 / 79 | StepExamTest: delete the dead TextUtils mock; scope NetworkUtils to testSerializeExam |
| [M040](#m040) | 58 | 38 / 71 / 79 | CoursesStepsViewModel.loadStep: run the markdown rewrite and offline filter on io |
| [M041](#m041) | 57 | 38 / 64 / 89 | ResourcesViewModel.loadResources: rethrow cancellation, tagged log instead of printStackTrace |
| [M042](#m042) | 56 | 39 / 71 / 71 | ChatViewModel resolves the current user via getUserModel; ChatHistoryFragment stops reading prefs |
| [M043](#m043) | 56 | 39 / 61 / 81 | ResourcesListFilter.matching: apply tag/facet/download filters before the ranked title search |
| [M044](#m044) | 55 | 55 / 61 / 72 | VoicesAdapter: use ImageViewerUtils.showZoomableImage instead of a private copy |
| [M045](#m045) | 55 | 53 / 71 / 64 | NotificationsRepositoryImpl: one private helper for getTaskTeamNamesByTaskIds/ByTaskTitles |
| [M046](#m046) | 55 | 39 / 69 / 72 | CommunityDao.getAllSorted: filter blank names in SQL, drop the dialog-side TextUtils filter |
| [M047](#m047) | 55 | 39 / 61 / 80 | ResourcesViewModel sort wrappers: default dispatcher, mutate sort fields on the caller |
| [M048](#m048) | 55 | 38 / 71 / 77 | CoursesRepositoryImpl.getCourseDetailModel: async the five independent reads |
| [M049](#m049) | 54 | 55 / 60 / 73 | Consolidate guest-login and authenticate orchestration into LoginViewModel |
| [M050](#m050) | 54 | 39 / 66 / 72 | LifeViewModel.loadMyLifeList: seedMyLifeIfEmpty + observe instead of reading the list twice |
| [M051](#m051) | 54 | 39 / 64 / 75 | TakeCourseFragment: delete the unused UserSessionManager field injection |
| [M052](#m052) | 54 | 38 / 71 / 76 | News: raw-keyed parse cache for reactionsMap / pendingReactionsMap |
| [M053](#m053) | 53 | 55 / 64 / 69 | SettingsViewModel: UserReadRepository instead of the Context-backed UserSessionManager for isGuest |
| [M054](#m054) | 53 | 39 / 44 / 92 | CourseStepDao.deleteStaleSteps: stored-minus-keep positive delete in 900 batches |
| [M055](#m055) | 53 | 35 / 71 / 81 | NotificationsViewModel: flowOn(default) for grouping/flattening |

**Wave 3: worthwhile, lower impact**

| M | score | O / D / C | task |
|---|---|---|---|
| [M056](#m056) | 53 | 34 / 71 / 83 | CourseDetailViewModel.loadCourseDetail: run the markdown rewrite off Main |
| [M057](#m057) | 51 | 38 / 66 / 77 | VoicesRepositoryNewsSyncTest: class-level mockkObject(UrlUtils, NetworkUtils); keep the per-test Room DB |
| [M058](#m058) | 51 | 38 / 64 / 80 | PublicSurveyViewModel: inject TimeProvider for launchTime |
| [M059](#m059) | 50 | 39 / 56 / 78 | automerge.sh publish_failed: one commits/{sha}/check-runs call instead of runs+jobs N+1 |
| [M060](#m060) | 49 | 38 / 64 / 78 | ChatViewModel.loadShareTargets: run the three summary reads concurrently |
| [M061](#m061) | 46 | 46 / 60 / 71 | Hoist getUserProfile/hasActiveUserSyncAction/getUsersSortedBy/searchUsers onto UserReadRepository; narrow Ratings/Markdown/Health VMs |
| [M062](#m062) | 46 | 39 / 61 / 72 | EventsRepositoryImpl.toggleAttendance: return the mutated meetup instead of re-querying |
| [M063](#m063) | 43 | 39 / 64 / 68 | NotificationActionReceiver: @Inject BroadcastService instead of the EntryPoint lookup |
| [M064](#m064) | 43 | 38 / 64 / 73 | RequestsViewModel.loadJoinedMembers: run members and current-user reads concurrently |
| [M065](#m065) | 43 | 38 / 61 / 77 | SubmissionDetailViewModel: collapse the intermediate stateIn |
| [M066](#m066) | 43 | 33 / 64 / 83 | playstore-quota.sh forecast: measure once instead of twice |
| [M067](#m067) | 42 | 39 / 36 / 78 | TeamCoursesViewModel.getAvailableCourses: run the course-table mapping on io |
| [M068](#m068) | 42 | 32 / 66 / 78 | Move TeamsTasksFragment companion filters into TeamsTasksViewModel |
| [M069](#m069) | 40 | 27 / 66 / 76 | ImageUtilsTest: class-level Glide static mock + relaxed ImageView |
| [M070](#m070) | 39 | 34 / 71 / 71 | Delete dead android Log static mocks (RetryQueueWorkerTest, DownloadService*Test, HealthExaminationTest, NewsTest) |
| [M071](#m071) | 38 | 39 / 61 / 67 | ResourcesRepository: default stepResourcesNeedingDownload; CoursesStepsViewModel next-step prefetch uses it |
| [M072](#m072) | 37 | 39 / 61 / 65 | CommunityTabState: derived planetViewInId/communityViewInId/title used by both fragments |
| [M073](#m073) | 37 | 39 / 46 / 72 | MyLifeDao.updateVisibility: use the UPDATE row count instead of a pre-SELECT |
| [M074](#m074) | 37 | 38 / 64 / 70 | FreeSpaceWorkerTest: delete the unused Log mock; class-level FileUtils mock |
| [M075](#m075) | 37 | 38 / 64 / 70 | SubmissionsRepositoryImpl: LIMIT 1 survey lookup in isStepCompleted; index loop instead of zip in flow dedup |
| [M076](#m076) | 37 | 38 / 64 / 70 | TransactionSyncManager: replace four printStackTrace calls with Log.e (and rethrow cancel in authenticate) |
| [M077](#m077) | 36 | 39 / 56 / 68 | LoginViewModel.saveUsers reads the user itself; LoginActivity passes only name/password/source |
| [M078](#m078) | 35 | 39 / 61 / 60 | JsonUtils.getDouble/getFloat: drop the fallback that repeats the same parse |
| [M079](#m079) | 35 | 38 / 61 / 71 | SyncActivity: inject @ApplicationScope instead of MainApplication.applicationScope statics |
| [M080](#m080) | 34 | 38 / 64 / 67 | FileUploader.uploadDoc: Log.e instead of printStackTrace |
| [M081](#m081) | 34 | 38 / 56 / 72 | UserEntityEncodeImageTest: class-level Base64/Uri static mocks |
| [M082](#m082) | 33 | 39 / 49 / 68 | TTSManager.formatCsvForSpeech: single buildString |
| [M083](#m083) | 32 | 34 / 64 / 71 | ChatRepositoryImpl.sortChats: precompute the sort key (drop the getLatestRev half) |
| [M084](#m084) | 31 | 38 / 64 / 60 | Move the two @Module classes declared outside di/ into one di/ module |
| [M085](#m085) | 31 | 38 / 61 / 69 | VoicesAdapter.getCurrentLeader: first-wins name map instead of a linear scan |

**Wave 4: hygiene and micro-cleanups (batch these)**

| M | score | O / D / C | task |
|---|---|---|---|
| [M086](#m086) | 30 | 39 / 49 / 62 | Sha256Utils.getCheckSumFromFile: 64 KiB read buffer |
| [M087](#m087) | 30 | 39 / 46 / 68 | VoicesLabelManager.updateAddLabelVisibility: cache the LABELS value set |
| [M088](#m088) | 30 | 38 / 49 / 71 | test_timing_summary.py --test-sources: index basenames once |
| [M089](#m089) | 30 | 37 / 49 / 75 | ChatConversationPaginator: Gson parse on the default dispatcher |
| [M090](#m090) | 30 | 33 / 64 / 71 | CommunityServicesViewModel: TeamsShareRepository + TeamsMembersRepository instead of TeamsRepository |
| [M091](#m091) | 30 | 33 / 71 / 64 | SubmissionsRepositoryExporter: stop silently swallowing the two catch{null} blocks |
| [M092](#m092) | 29 | 39 / 46 / 65 | SubmissionsRepositoryExporter.getExamId: substringBefore('@') |
| [M093](#m093) | 29 | 38 / 64 / 53 | ExamTakingFragment: parse the constant 'Other' choice once; reuse the inflater |
| [M094](#m094) | 28 | 38 / 43 / 73 | ServerReachabilityWorkerTest: class-level mocks, keep the Log verifies |
| [M095](#m095) | 27 | 37 / 64 / 63 | Feedback.serializeFeedback / HealthExamination.formatConditions: stop printStackTrace |
| [M096](#m096) | 26 | 38 / 49 / 69 | StableIdGenerator: local NO_ID constant, drop the RecyclerView import |
| [M097](#m097) | 25 | 34 / 66 / 52 | UrlUtils.hostUrl: cache behind the existing generation counter |
| [M098](#m098) | 24 | 38 / 61 / 51 | SubmissionsAdapter / SubmissionsListAdapter: dateCache like sibling adapters |
| [M099](#m099) | 24 | 34 / 61 / 69 | PersonalDao.countByTitle: branch on empty userId (drop the delete/update OR split) |
| [M100](#m100) | 24 | 34 / 54 / 71 | TeamsRepositoryImpl.getJoinedMembersWithVisitInfo: reuse the membership rows for leaderIds |
| [M101](#m101) | 23 | 39 / 34 / 59 | PersonalsRepositoryImpl: remove android.util.Log |
| [M102](#m102) | 23 | 39 / 34 / 57 | RetryRepositoryImpl: remove android.util.Log |
| [M103](#m103) | 23 | 38 / 49 / 65 | HealthExaminationItemMapper: Kotlin stdlib instead of TextUtils |
| [M104](#m104) | 23 | 38 / 49 / 65 | ResourcesSearchUtils.searchList: append to the prefix list instead of + |
| [M105](#m105) | 22 | 39 / 34 / 54 | News model: remove android.util.Log |
| [M106](#m106) | 20 | 39 / 34 / 50 | CommunityRepositoryImpl: remove android.util.Log |
| [M107](#m107) | 20 | 38 / 46 / 65 | SubmissionsRepositoryExporter.drawMultilineText: measure space and word once |
| [M108](#m108) | 18 | 38 / 49 / 51 | Meetup.getHashMap: drop the unreachable date try/catch and the routine JSONArray stack trace |
| [M109](#m109) | 18 | 35 / 46 / 70 | playstore-quota.sh save_epochs: GraphQL isDraft/publishedAt instead of full release documents |
| [M110](#m110) | 18 | 34 / 49 / 69 | ChatSearch.fullConvoSearch: one presized result list instead of chained + |
| [M111](#m111) | 16 | 38 / 46 / 54 | FeedbackAdapter: resolve label strings once in onCreateViewHolder |
| [M112](#m112) | 9 | 34 / 49 / 44 | NetworkMonitorWorker: rename UPLOAD_DELAY_SECONDS to REACHABILITY_DELAY_SECONDS |
| [M113](#m113) | 6 | 36 / 34 / 49 | HealthRepositoryImpl: remove android.util.Log |

---

## Wave 1: highest consensus (all three plans rank these near the top)

<a id="m001"></a>

### M001. DashboardViewModel.evaluateChallengeDialog: evaluate the (expired) date window before launching its five queries

**score 97** · O 76 / D 91 / C 91 · kept by 3/3 plans · raw: L02.01, L03.01  
**refs:** Opus #2 · Devin R002 · Codex T009

**Megaplan note (verified in code):** `DashboardViewModelTest.kt:216-236` (`evaluateChallengeDialog uses voice date count for both user and community counts`) runs with the real clock and asserts `coVerify(exactly = 1)` on `getCommunityVoiceDateCount`. It fails once the gate lands. Rewrite it to assert zero repository calls outside the window, or inject `TimeProvider` so one case can sit inside the window. Devin R002 says only "tests stay green", which is wrong.

**guardrails:**
- Codex scope: Evaluate the existing guest, URL, and exclusive date-window conditions before launching the five challenge reads. Emit the existing no-dialog result when gated out; retain behavior for dates within the configured window.

**verified against tree:** Verified: fetchCourseData and four more reads run on every dashboard open, but the window ended on 2025-01-16. Neither version mentions that DashboardViewModelTest.kt:216-236 asserts the queries run with the real clock and must be rewritten (inject a clock or assert zero calls).

**canonical version — L02-T1 (Copilot·Grok), "skip expired challenge queries on dashboard open (roadmap 7)":**

context: DashboardActivity.kt:204 calls DashboardViewModel.evaluateChallengeDialog on every dashboard init, after notifications are checked.
The prompt is allowed only for a non-guest user, only when serverUrl is in validUrls, and only when today is after 2024-11-30 and before 2025-01-16.
Those checks sit at DashboardViewModel.kt:313-317, after five repository calls have already been started. Line 299 launches progressRepository.fetchCourseData before the date is considered.
On 2026-11-10 that window is closed, so every home open still pays for course, voice, and survey reads that cannot show a dialog.
This does not move roadmap 9 or 10: the viewmodel stays Android-bound, and this is not a Compose screen.
files: app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt, function evaluateChallengeDialog (lines 286-346).
Leave DashboardActivity.kt alone. Leave ProgressRepository, VoicesRepository, CoursesRepository, and SubmissionsRepository alone.
steps:
1. At the top of evaluateChallengeDialog, before viewModelScope.launch, compute the same shouldPrompt predicate the function already uses: not isGuest, today after 2024-11-30, today before 2025-01-16, and serverUrl in validUrls.
2. If that predicate is false, return immediately. Do not start the coroutine, and do not call fetchCourseData, getCommunityVoiceDateCount, getCourseTitleById, hasPendingSurvey, or hasUserCompletedSync.
3. Leave the existing query block at lines 298-333 in place for a device clock that is still inside the window, including the later hasUserCompletedSync call that runs only when prereqsMet is true.
4. Do not change ChallengeDialogData fields or the event emit.
5. Do not delete evaluateChallengeDialog. DashboardActivity.kt:204 still calls it.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open the dashboard as a signed-in user and as a guest. No challenge dialog appears, and notifications plus bottom navigation still load as before.
size budget: ~15 changed lines, 1 file
out of scope: do not retune the 2024-2025 dates, do not remove the query path, and do not migrate this screen to Compose.

---
<a id="m002"></a>

### M002. SubmissionsPayloadBuilder.submissionPayload: rethrow CancellationException instead of returning a partial payload

**score 97** · O 71 / D 85 / C 95 · kept by 3/3 plans · raw: L10.04, L13.01, L15.08  
**refs:** Opus #3 · Devin R005, R071 · Codex T001, T015, T008, T014

**guardrails:**
- Codex scope: Rethrow CancellationException from submissionPayload before ordinary payload-error fallback; mirror the existing team-lookup helper.
- Codex scope: Replace payload-build printStackTrace with a controlled diagnostic while retaining the existing ordinary-error fallback contract.
- L13.01 correction from Codex: Rejected adding android.util.Log to RetryUtils; structured reporting must preserve its existing platform-independent imports. stderr is not literally invisible to logcat.

**verified against tree:** Verified at SubmissionsPayloadBuilder.kt:162-165. The inner team lookup already rethrows (:110-116), but the outer catch swallows it again.

**contributor notes:** L13-T1: printStackTrace slice only

**canonical version — L10-T4 (Codex), "Propagate cancellation from submission payload assembly (roadmap 1+5+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt:110–115` correctly rethrows cancellation from team lookup, but `submissionPayload` catches it again at lines 162–165 and returns a partial JSON object. This breaks the repository's suspend-operation contract and can let cancelled work proceed as though payload assembly succeeded.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt`: `SubmissionsPayloadBuilder.submissionPayload` exception handling.
- Edit `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilderTest.kt`: `SubmissionsPayloadBuilderTest` cancellation cases.
- Leave upload coordination, repository callers, team lookup behavior, DTOs, and serialization fields alone.

steps:
1. Add a cancellation-specific catch that rethrows the same exception before the existing ordinary-exception catch; reuse the cancellation type already imported.
2. Preserve current handling of ordinary exceptions, JSON defaults, membership data, and successful payload fields.
3. Add a test where exam lookup throws a specific cancellation instance and assert that `submissionPayload` throws that identical instance.
4. Add a test reaching the existing team-lookup path with a nonblank team ID and incomplete metadata; assert cancellation escapes the outer payload operation. Retain the existing exact-payload expectations.

acceptance:
- Run `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.SubmissionsPayloadBuilderTest'`.
- Run `./gradlew testDefaultDebugUnitTest`.
- Verify the payload operation aborts when cancelled; a normal sync still serializes the same submissions. Do not claim an end-to-end upload cancellation guarantee from this local fix.

size budget: approximately 40–75 changed lines across 2 files, including tests; stay below 150.

out of scope: No error-result redesign, retry changes, Android-service edits, or replacement of the team dependency.

---
<a id="m003"></a>

### M003. RetryUtils.retry: rethrow CancellationException and drop the stderr printStackTrace

**score 95** · O 69 / D 95 / C 91 · kept by 3/3 plans · raw: L03.04, L13.01, L15.01, L17.07  
**refs:** Opus #9 · Devin R001, R071 · Codex T008, T015, T001, T014

**guardrails:**
- Codex scope: Rethrow CancellationException before the generic catch, including the last-attempt case, and preserve retry predicates, count, and delay behavior.
- Codex scope: Remove the exhaustion printStackTrace. If reporting is required, use a platform-independent diagnostic hook or caller-owned reporting policy; do not add android.util.Log to this otherwise Android-free helper.
- L03.04 correction from Codex: Rejected adding android.util.Log to RetryUtils; structured reporting must preserve its existing platform-independent imports. stderr is not literally invisible to logcat.
- L13.01 correction from Codex: Rejected adding android.util.Log to RetryUtils; structured reporting must preserve its existing platform-independent imports. stderr is not literally invisible to logcat.

**verified against tree:** Verified: `catch (e: Exception)` at RetryUtils.kt:20 swallows cancellation, and the last attempt returns null, which hides it. Five callers. Do not add `android.util.Log`, because the file is platform-free today.

**contributor notes:** L03-T4: logging swap only; misses the cancellation defect · L13-T1: RetryUtils slice of a 4-site printStackTrace bundle

**canonical version — L17-T7 (ClaudeCode), "Stop RetryUtils.retry from retrying and swallowing a cancelled coroutine (roadmap 8, moves 9)":**

context: `utils/RetryUtils.kt:20` catches `Exception`, which includes `CancellationException`. A cancelled caller is therefore treated as a failed attempt and retried. On the last attempt nothing re-throws, so `retry` returns `null` and the cancellation is lost, and line 32 prints a stack trace for it. Five call sites depend on this helper: `ChatViewModel.kt:140`, `SyncActivity.kt:505`, `DashboardViewModel.kt:244`, `UserRepositoryImpl.kt:696` and `ApiClient.kt:16`. The file itself is pure Kotlin, so fixing it here fixes all of them, and the helper stays KMP-ready.
files:
- `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt` and `app/src/test/java/org/ole/planet/myplanet/utils/RetryUtilsTest.kt`.
- Leave alone: all five callers.
- `ApiClient`, `UserRepositoryImpl` and `DashboardViewModel` belong to open PRs, and none of the callers needs a change.
steps:
1. Add `catch (e: CancellationException) { throw e }` ahead of the generic `catch (e: Exception)`, using `kotlin.coroutines.cancellation.CancellationException` so the file stays platform-free.
2. Remove `lastException?.printStackTrace()` (line 32) and the then-unused `lastException` variable. Do not add `android.util.Log`, because this file must stay free of Android imports.
3. Add a `runTest` case in which `block` throws `CancellationException`: `retry` must rethrow it after exactly one call to `block`, with no further attempts.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*RetryUtilsTest"` passes; the full suite stays green.
- Leaving the chat screen while a chat request is retrying no longer logs a `JobCancellationException` stack trace.
size budget: about 20 changed lines in 2 files.
out of scope: no changes to the retry, back-off or `shouldRetry` semantics for real failures.

---
<a id="m004"></a>

### M004. DictionaryRepositoryImpl.insertDictionaryData: check the DB count before the seed-file existence

**score 93** · O 71 / D 75 / C 93 · kept by 3/3 plans · raw: L10.05, L12.04  
**refs:** Opus #4 · Devin R018 · Codex T005

**Megaplan correction (verified in code):** Opus's "do NOT remove `DictionaryActivity.loadCount()`" is wrong, and Codex T005 is right. `DictionaryViewModel.loadDictionary()` also publishes `DictionaryLoadState.Populated`, and the activity wires the search button from that state too. Once the repository checks the row count first, `loadCount()` is redundant, and on an empty database it publishes `Populated(0)` and wires search before any data exists. Remove the `viewModel.loadCount()` call in `DictionaryActivity.onCreate` and the now-unused `DictionaryViewModel.loadCount()` in this same task, and update `DictionaryViewModelTest` to match.

**guardrails:**
- Codex scope: Under the seed lock, check whether dictionary rows already exist before requiring the import file; preserve first-import, failure, and cancellation results.
- Codex scope: Remove the redundant DictionaryActivity loadCount launch and let loadDictionary publish the count.
- L12.04 part dropped by Opus: DictionaryActivity half: loadCount() is the only path that wires the search button. **(overridden: see the Megaplan correction above)**

**verified against tree:** Verified: a populated DB with a cleaned-up seed file reports FileMissing and triggers a re-download. Do NOT remove DictionaryActivity.loadCount() (L12-T4's second half): it is the only path that wires the search button.

**contributor notes:** L12-T4: repository half only

**canonical version — L10-T5 (Codex), "Resolve dictionary readiness from persisted data before checking the seed file (roadmap 1+3+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt:36–45` checks seed-file existence before checking whether dictionary rows already exist. A populated offline dictionary is therefore reported as `FileMissing` after its import source disappears, leaking an implementation detail through the repository's load result.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`: `DictionaryRepositoryImpl.insertDictionaryData`.
- Edit `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`: `DictionaryRepositoryImplTest` load-state cases.
- Leave the repository interface, file-reader adapter, DAO, mapping, and dictionary UI alone.

steps:
1. Keep the IO dispatcher and seed mutex; put the persisted-row check first inside the mutex.
2. Return the existing populated result whenever the count is nonzero, without consulting or reading the seed source.
3. Only check file existence and read/parse/insert when the database is empty. Preserve existing missing-file, failure, and cancellation outcomes for that path.
4. Add a populated-database/missing-source regression with no file-reader calls or inserts; explicitly stub a zero count for the existing missing-file case.
5. Retain and run the concurrent-initialization test to verify exactly one insert still occurs.

acceptance:
- Run `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.DictionaryRepositoryImplTest'`.
- Run `./gradlew testDefaultDebugUnitTest`.
- Verify a populated dictionary reports readiness and remains searchable after its seed file is removed; an empty database without that file still reports the missing source.
- Repository-owned readiness remains expressible without Android APIs, supporting roadmap 9.

size budget: approximately 35–65 changed lines across 2 files, including tests; stay below 150.

out of scope: No new load-result types, reseeding policy, dictionary format changes, or UI error handling.

---
<a id="m005"></a>

### M005. QuestionDao.deleteStaleQuestions: @Transaction + bounded positive delete per exam

**score 92** · O 55 / D 85 / C 93 · kept by 3/3 plans · raw: L08.06, L10.03, L12.07  
**refs:** Opus #17 · Devin R006, R132 · Codex T004  
**shares files with:** M007

**guardrails:**
- Codex scope: Run represented-exam stale cleanup in one transaction, retaining null-exam skipping and empty-input no-op behavior.
- Codex scope: Read stored question IDs per represented exam, subtract the full incoming keep set, then delete only stale IDs in bounded positive batches constrained to that exam. Never chunk a NOT IN keep-list; leave unrepresented exams untouched.

**verified against tree:** Verified: there is no @Transaction, and the survey sync path (SurveysRepositoryImpl.kt:410-413) is not otherwise transactional. The unbounded NOT IN only matters above about 999 questions. L10-T3 is the superset.

**contributor notes:** L08-T6: @Transaction only · L12-T7: @Transaction + empty guard

**canonical version — L10-T3 (Codex), "Bound stale exam-question deletion within each exam (roadmap 1+5+7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt:27–32` passes every retained question ID for an exam into one unbounded negative predicate. Course and survey synchronization can therefore hit SQLite's bind limit even though the DAO's bulk reads already use bounded batches.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt`: `QuestionDao.deleteStaleQuestions` and replacement of `deleteStaleInternal`.
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/QuestionDaoTest.kt`: `QuestionDaoTest` deletion regressions.
- Leave survey/course repositories, question models, database configuration, and bulk-read helpers alone.

steps:
1. Replace the negative-list helper with an ID-only query for one exam and an exam-constrained positive-ID deletion query.
2. Retain grouping by exam and the null-exam skip. For each represented exam, subtract its complete incoming ID set from its stored ID set.
3. Delete the resulting stale IDs in batches of at most 900, with the exam ID as an additional parameter; perform no delete for an empty difference.
4. Make the orchestration method transactional. Empty input must remain a no-op, and exams absent from the input must remain untouched.
5. Add real Room cases with more than 33,000 retained IDs, retained IDs at both ends of that input, more than 900 stale rows, duplicate IDs, multiple represented exams, and an unrepresented exam. Keep assertions order-independent.

acceptance:
- Run `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.data.room.dao.QuestionDaoTest'`.
- Run `./gradlew testDefaultDebugUnitTest`.
- Verify syncing a large exam or survey retains all current questions, deletes only obsolete questions of represented exams, and preserves unrelated exams.
- Existing repository contracts remain usable without a platform-core extraction prerequisite.

size budget: approximately 95–140 changed lines across 2 files, including tests; stay below 150.

out of scope: Do not infer deletion of an exam from an empty list, change question ordering, or introduce schema migrations.

---
<a id="m006"></a>

### M006. UserDataWorker / AutoSyncWorker: await healthRepository.syncPendingHealthExaminations instead of fire-and-forget into appScope

**score 91** · O 69 / D 74 / C 93 · kept by 3/3 plans · raw: L17.01  
**refs:** Opus #12 · Devin R024 · Codex T003

**guardrails:**
- Codex scope: Provide a suspend health-upload operation and await it from UserDataWorker/AutoSyncWorker child work before completion. Adapt callback bridges so they resume only after required work, preserving cancellation and existing retry/error policy rather than launching health work in applicationScope.

**verified against tree:** Verified at UploadToShelfService.kt:75-78. The worker can report success before health data is pushed.

**canonical version — L17-T1 (ClaudeCode), "Await the health upload inside the workers instead of firing and forgetting it into the app scope (roadmap 5 + 7, moves 9)":**

context: `UploadToShelfService.uploadHealth()` (`services/UploadToShelfService.kt:75-79`) only calls `appScope.launch(dispatcherProvider.io) { healthRepository.syncPendingHealthExaminations() }`. Both callers are WorkManager workers: `services/UserDataWorker.kt:56` (inside the `uploadUserData` callback) and `services/AutoSyncWorker.kt:112` (inside a `workerScope.launch`). A worker can therefore report `Result.success()` while the health upload is still running in the app scope. If the process dies, the upload is lost silently, and its errors never reach the worker's `runCatchingRethrowCancellation`. Running the upload in the worker's own coroutine merges two upload paths into one.
files:
- `app/src/main/java/org/ole/planet/myplanet/services/UserDataWorker.kt`: the `UPLOAD_TYPE_BULK` branch of `doWork()`, and the `@AssistedInject` constructor.
- `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt`: `onError()` lines 104-116, and the constructor.
- `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`: delete `uploadHealth()`.
- `app/src/test/java/org/ole/planet/myplanet/services/UserDataWorkerTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/services/UploadToShelfServiceTest.kt`: the test `uploadHealth uploads and marks health records`, lines 166-173.
Leave these alone: `HealthRepository`/`HealthRepositoryImpl` (open PRs own them), `UploadManager`, `AutoSyncUploadRunner`, and `uploadSingleUserHealth()`.
steps:
1. Inject `HealthRepository` into both workers' `@AssistedInject` constructors. `HealthRepository.syncPendingHealthExaminations()` is already a `suspend fun` (`repository/HealthRepository.kt:13`).
2. In `UserDataWorker`, remove `uploadToShelfService.uploadHealth()` from the `uploadUserData` callback. Add a separate `runCatchingRethrowCancellation { healthRepository.syncPendingHealthExaminations() }` right after the `awaitUploadCompletion` block that wraps `uploadUserData`.
3. In `AutoSyncWorker.onError()`, replace `uploadToShelfService.uploadHealth()` (line 112) with a direct `healthRepository.syncPendingHealthExaminations()` call in the same `workerScope.launch`, after `checkHealth()`.
4. Delete `UploadToShelfService.uploadHealth()` and its test. Move the "syncs pending health" assertion into `UserDataWorkerTest`: pass a relaxed `HealthRepository` mock to the constructor at line 39 and `coVerify` it once for `UPLOAD_TYPE_BULK`.
5. Remove any imports that are now unused.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*UserDataWorkerTest" --tests "*UploadToShelfServiceTest"` passes, and then the full `./gradlew testDefaultDebugUnitTest` stays green.
- `./gradlew assembleDefaultDebug assembleLiteDebug` builds.
- On a device, a manual "upload" sync still pushes pending health examinations: the `health` docs reach the server, and `WorkManager` shows the `UserDataWorker` run finishing only after that upload.
size budget: about 40 changed lines in 5 files.
out of scope: no changes to `HealthRepositoryImpl`, no renaming of `UploadToShelfService`, and no change to the `MainApplication.isSyncRunning` handling in `AutoSyncWorker`.

---
<a id="m007"></a>

### M007. SurveysRepositoryImpl.getSurveyFormState: grouped COUNT query instead of loading every ExamQuestion

**score 90** · O 71 / D 77 / C 86 · kept by 3/3 plans · raw: L05.01  
**refs:** Opus #7 · Devin R017 · Codex T023  
**shares files with:** M005

**guardrails:**
- Codex scope: Add a chunked exam-ID/count projection and use it for survey-form badges, filling zero-question surveys with zero. Retain team submission mapping and avoid loading question bodies/choices merely to count them.

**verified against tree:** Verified at SurveysRepositoryImpl.kt:357. exam_questions.examId is already indexed, so GROUP BY uses the index.

**canonical version — L05-T1 (ClaudeCode), "Count survey questions in SQL instead of loading every question row (roadmap 1+7, moves 9)":**

**context:**
- `main/repository/SurveysRepositoryImpl.kt:357` builds the "N questions" badge for each survey card. To get one integer per survey it loads every full `ExamQuestion` row (body, choices JSON, marks) for all surveys on screen:
  `val questionCounts = questionDao.getByExamIds(surveyIds).groupingBy { it.examId }.eachCount()`
- `getSurveyFormState` runs on every load of the survey list and the team survey list, including realtime "exams" refreshes.
- `QuestionDao` already has a single-exam `countByExamId` at `main/data/room/dao/QuestionDao.kt:25`, but no grouped version.

**files:**
- touch:
  - `main/data/room/dao/QuestionDao.kt` (add a grouped count next to `countByExamId`, line 25)
  - `main/repository/SurveysRepositoryImpl.kt` (`getSurveyFormState`, lines 344-366)
  - `test/repository/SurveysRepositoryImplTest.kt` (it already mocks `questionDao` at line 55)
- leave alone:
  - `SubmissionsRepositoryImpl.kt` and `SubmissionDao.kt` (task 2)
  - `SubmissionsRepositoryExporter.kt`, which needs full question rows

**steps:**
1. In `QuestionDao`, add a projection data class (for example `ExamQuestionCount(examId: String?, count: Int)`, modelled on `RatingAggregate` in `main/data/room/dao/RatingDao.kt:11-14`).
2. Add `@Query("SELECT examId, COUNT(*) AS count FROM exam_questions WHERE examId IN (:examIds) GROUP BY examId")` as an `…Internal` method.
3. Add a public `suspend fun countByExamIds(examIds: List<String>): Map<String, Int>` default method. Copy the chunking of `getByExamIds` (lines 21-24): empty guard, `distinct()`, `chunked(900)`. Merge the chunks into one map.
4. In `getSurveyFormState`, replace the line 357 expression with `questionDao.countByExamIds(surveyIds)`. Keep the `questionCounts[surveyId] ?: 0` lookup unchanged.
5. Add two tests to `SurveysRepositoryImplTest`:
   - `getSurveyFormState` returns the counts that the mocked `countByExamIds` reports;
   - `getByExamIds` is never called (`coVerify(exactly = 0)`).

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green, including `SurveysRepositoryImplTest` and `ui/surveys/SurveysViewModelTest`.
- Manually, the Surveys tab and a team's Surveys tab show the same per-survey question counts as before.

**size budget:** ~45 changed lines, 3 files

**out of scope:**
- Do not touch the other `getByExamIds` callers.
- Do not add an index on `examId` (that needs a DB version bump).

---
<a id="m008"></a>

### M008. CoursesRepositoryImpl: read titles/ids from courseDao instead of mapCourse (which loads all steps)

**score 89** · O 71 / D 72 / C 88 · kept by 3/3 plans · raw: L05.05  
**refs:** Opus #5 · Devin R027 · Codex T017  
**shares files with:** M048, M075

**guardrails:**
- Codex scope: Read bare course rows for getCourseTitleById, the title in getCourseProgress, and getMyCourseIds. Preserve blank-ID, membership-pattern, remote/local-ID, ordering, and null behavior; retain full hydration for callers that need steps.

**verified against tree:** Verified: getCourseTitleById, getCourseProgress and getMyCourseIds go through mapCourse/mapCourses, which load steps (:900-909). This is reached on every shelf upload.

**canonical version — L05-T5 (ClaudeCode), "Stop loading every course step just to read a course title or id (roadmap 1+7, moves 9)":**

**context:**
- In `main/repository/CoursesRepositoryImpl.kt`, `getCourseById` (lines 125-128) always runs `mapCourse`, which loads all of that course's step rows (lines 900-909). Three callers only need a scalar:
  - `getCourseTitleById` (lines 525-527) is called from `ui/courses/CoursesStepsViewModel.kt:70` and the dashboard;
  - `getCourseProgress` (lines 441-444) has just loaded the steps and then calls `getCourseById(courseId)?.courseTitle`, so the same steps load a second time;
  - `getMyCourseIds` (lines 568-572) runs `getMyCourses(userId)`, which loads every course plus all of their steps (`mapCourses` at lines 885-898), only to read `courseId`. It runs at the start of every full sync (`main/services/sync/SyncManager.kt:277`) and once per synced user during shelf upload.
- `CourseDao.getByCourseId` (`main/data/room/dao/CourseDao.kt:14`) and `CourseDao.getForUserPattern` (line 28) return the bare rows.

**files:**
- touch:
  - `main/repository/CoursesRepositoryImpl.kt` (the three functions above)
  - `test/repository/CoursesRepositoryImplTest.kt`
- leave alone:
  - `CourseDao.kt`
  - `getCourseById` itself, which still needs steps for its other callers
  - `getCourseDetailModel`
  - `ProgressRepositoryImpl.kt` (task 4)

**steps:**
1. `getCourseTitleById`: return `if (courseId.isBlank()) null else courseDao.getByCourseId(courseId)?.courseTitle`.
2. `getCourseProgress`: replace `getCourseById(courseId)?.courseTitle` with `courseDao.getByCourseId(courseId)?.courseTitle`.
3. `getMyCourseIds`: build the array from `courseDao.getForUserPattern(userIdPattern(userId)).mapNotNull { it.courseId }`. This is the same SQL `getMyCourses(userId)` uses at line 112, minus `mapCourses`.
4. Add or adjust tests: each of the three functions returns the same value as before, and `courseStepDao.getByCourseId` / `getByCourseIds` are not called (`coVerify(exactly = 0)`).

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually:
  - the course steps screen still shows the course title;
  - the course progress screen still shows title and step counts;
  - a full sync still uploads the user's shelf with the right course ids.

**size budget:** ~35 changed lines, 2 files

**out of scope:**
- Do not add new DAO queries.
- Do not change `mapCourse` / `mapCourses`.

---
<a id="m009"></a>

### M009. SubmissionsRepositoryImpl: @Transaction batch for rev updates and mark-uploaded

**score 89** · O 55 / D 86 / C 87 · kept by 3/3 plans · raw: L05.02  
**refs:** Opus #22 · Devin R003 · Codex T019  
**shares files with:** M075

**guardrails:**
- Codex scope: Wrap per-page revision updates in one Room transaction, preserving pending-local-change and duplicate-cleanup rules.
- Codex scope: Wrap markSubmissionsUploaded writes in one transaction and retain the failed-result list for missing local rows.

**verified against tree:** Verified at :811 and :849-850. The rev-update path is usually empty, and the mark path saves up to 50 commits per batch.

**canonical version — L05-T2 (ClaudeCode), "Batch submission rev updates and upload marks into one transaction each (roadmap 5+7, moves 9)":**

**context:**
- `main/repository/SubmissionsRepositoryImpl.kt:811` issues one `UPDATE` per pending submission during every submissions sync page, outside any transaction, so each row is its own SQLite commit:
  `revUpdates.forEach { (id, rev) -> submissionDao.updateRev(id, rev) }`
- Line 850 does the same for every uploaded submission in a batch of up to 50:
  `results.filter { submissionDao.markUploaded(it.localId, it.remoteId, it.remoteRev) == 0 }`
- `SubmitPhotosDao.markUploadedBatch` (`main/data/room/dao/SubmitPhotosDao.kt:34-38`) and `ApkLogDao.markUploadedBatch` (`ApkLogDao.kt:30-37`) already show the `@Transaction` default-method pattern.

**files:**
- touch:
  - `main/data/room/dao/SubmissionDao.kt` (next to `updateRev` / `markUploaded`, lines 64-65)
  - `main/repository/SubmissionsRepositoryImpl.kt` (`upsertRoomSubmissionsFromSync` line 811; `markSubmissionsUploaded` lines 849-850)
  - `test/repository/SubmissionsRepositoryImplTest.kt` (tests at lines 1358-1375)
  - `test/repository/SubmissionsRepositorySyncTest.kt`
- leave alone:
  - `SubmissionsPayloadBuilder.kt` and `SubmissionsRepositoryExporter.kt`
  - `UploadConfigs.kt` (task 3)

**steps:**
1. In `SubmissionDao`, add `@Transaction suspend fun updateRevs(updates: List<Pair<String, String>>)`. It loops over `updateRev` inside the transaction and returns early on an empty list.
2. Add a DAO-local `data class UploadMark(val localId: String, val remoteId: String?, val remoteRev: String?)`, nested like `SubmitPhotosDao.UploadedPhoto`. Do not import the repository's `UploadedItemResult` (`main/repository/UploadRepository.kt:15`) into the DAO. Then add `@Transaction suspend fun markUploadedBatch(marks: List<UploadMark>): Set<String>`. It loops over `markUploaded` and returns the `localId`s whose update count is 0.
3. Replace line 811 with `if (revUpdates.isNotEmpty()) submissionDao.updateRevs(revUpdates)`. In `markSubmissionsUploaded`, map `results` to `UploadMark`s and call `markUploadedBatch`. Return `results.filter { it.localId in failedIds }`, which is the same contract as today.
4. Update the two existing `markSubmissionsUploaded` tests. The DAO is a MockK mock, so stub and verify the new batch method instead of the per-row method. Keep the "returns failure if dao returns 0" assertion.
5. In `SubmissionsRepositorySyncTest`, add or adjust a case where a pending (`isUpdated`) row receives a new `_rev`, and verify `updateRevs` is called once with that pair.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, finish a survey offline, then sync. The survey uploads once, is not re-uploaded on the next sync, and the submissions list still shows it as complete.

**size budget:** ~60 changed lines, 4 files

**out of scope:**
- Do not wrap the rest of `upsertRoomSubmissionsFromSync` in `withTransaction`.
- Do not change `UploadedItemResult`.

---
<a id="m010"></a>

### M010. ProgressRepositoryImpl.getCourseProgress: count course steps in SQL instead of loading step bodies

**score 84** · O 71 / D 71 / C 86 · kept by 3/3 plans · raw: L05.04  
**refs:** Opus #6 · Devin R041 · Codex T022  
**shares files with:** M024, M054

**guardrails:**
- Codex scope: Read course-ID/count projections instead of full CourseStep bodies when calculating per-course max/current progress. Preserve the existing step-count arithmetic, missing-course zeros, and passed-progress treatment.

**verified against tree:** Verified at ProgressRepositoryImpl.kt:38/:50/:206. The Courses screen loads every step's markdown just to take .size.

**canonical version — L05-T4 (ClaudeCode), "Count course steps in SQL for progress bars instead of loading step bodies (roadmap 1+7, moves 9)":**

**context:**
- `ProgressRepositoryImpl.getCourseProgress` (`main/repository/ProgressRepositoryImpl.kt:34-55`) loads every full `CourseStep` row, including the step description markdown, for every course id with `courseStepDao.getByCourseIds(courseIds)` (line 38).
- It then uses only `steps.size`: as `max` at line 50, and in `calculateCurrentProgress` (lines 205-221), whose only use of the list is `steps?.size`.
- It runs on every Courses screen load for all course ids (`main/ui/courses/CoursesViewModel.kt:125`), on Take Course open (`TakeCourseViewModel.kt:125`), and inside `fetchCourseData`.

**files:**
- touch:
  - `main/data/room/dao/CourseStepDao.kt` (lines 11-15)
  - `main/repository/ProgressRepositoryImpl.kt` (`getCourseProgress` 34-55, `getCurrentProgress` 147-152, `calculateCurrentProgress` 205-221)
  - `test/repository/ProgressRepositoryImplTest.kt`
- leave alone:
  - `ProgressRepository.kt` (keep every public signature, including `getCurrentProgress(steps, …)`)
  - `CoursesRepositoryImpl.kt` (task 5)
  - `saveEarnedExamPasses` / `getBlockingStepIndex`

**steps:**
1. In `CourseStepDao`, add a projection (`CourseStepCount(courseId: String?, count: Int)`) and `@Query("SELECT courseId, COUNT(*) AS count FROM course_steps WHERE courseId IN (:courseIds) GROUP BY courseId")`.
2. Add a public chunked default method, `countByCourseIds(courseIds): Map<String, Int>`, mirroring `getByCourseIds` (empty guard, `distinct()`, `chunked(900)`).
3. Change the private `calculateCurrentProgress` to take `stepsSize: Int` instead of the list. `getCurrentProgress` passes `steps?.size ?: 0`.
4. In `getCourseProgress`, replace the `getByCourseIds` and `groupBy` with `countByCourseIds`. Set `max = stepCounts[courseId] ?: 0` and pass that count to `calculateCurrentProgress`.
5. Add tests for `getCourseProgress`:
   - `max` and `current` come from the mocked counts and progresses, including the case where a progress `stepNum` is beyond the step count;
   - `courseStepDao.getByCourseIds` is not called.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, the course cards' progress bars show the same "x / y steps" before and after the change, both on Courses and in Take Course.

**size budget:** ~55 changed lines, 3 files

**out of scope:**
- Do not refactor the JSON round trip in `getCourseProgressRows`.
- Do not change `fetchCourseData`'s `getMyCourses` call.

---
<a id="m011"></a>

### M011. TakeCourseViewModel.loadCourse (+ CoursesViewModel.loadCourses): map failures to NotFound, rethrow cancellation, log instead of printStackTrace

**score 84** · O 69 / D 71 / C 88 · kept by 3/3 plans · raw: L03.08, L13.02, L15.06, L17.08  
**refs:** Opus #10 · Devin R028, R072, R009 · Codex T016, T026, T014  
**shares files with:** M019, M021, M023, M027, M041, M051

**guardrails:**
- Codex scope: Replace the CoursesViewModel loadCourses stack dump with tagged diagnostics while preserving its public state contract.
- Codex scope: Replace TakeCourseViewModel.loadCourse stack dumps with tagged diagnostics.
- Codex scope: Rethrow cancellation in loadCourse before ordinary error handling.
- Codex scope: Rethrow cancellation from getRatingPromptDecision rather than returning its ordinary fallback.
- Codex scope: Move a failed ordinary course load out of Loading through an explicit existing-compatible failure state.

**verified against tree:** Verified: TakeCourseViewModel.kt:130-132 leaves the UI in Loading forever on failure, and the fragment already handles NotFound. CoursesViewModel.kt:142 has the same printStackTrace.

**contributor notes:** L17-T8: TakeCourse part · L03-T8: logging only, but also covers CoursesViewModel · L15-T6: cancel+log part · L13-T2: printStackTrace part

**canonical version — L17-T8 (ClaudeCode), "Give TakeCourseViewModel and HealthExaminationViewModel cancellation-safe error paths (roadmap 3 + 8)":**

context: `ui/courses/TakeCourseViewModel.kt:130-132` catches every `Exception` in `loadCourse` with only `e.printStackTrace()`. A failed load leaves `_uiState` at `TakeCourseUiState.Loading` forever, so the spinner never stops. `TakeCourseFragment` already handles the failure UX for `NotFound`: it shows a `failed_to_load_course` toast and pops back. In the same file, `getRatingPromptDecision` (lines 99-104) and `HealthExaminationViewModel.saveExamination` (`ui/health/HealthExaminationViewModel.kt:119-121`) also catch `Exception`, which includes `CancellationException`. A save cancelled while leaving the screen is then reported as `_saveResult.send(false)`, so a "save failed" message appears even though nothing failed.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` (`loadCourse`, `getRatingPromptDecision`)
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt` (`saveExamination`)
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt`
- `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModelTest.kt`
Leave alone: `TakeCourseFragment`, `HealthExaminationActivity` (an open PR owns it), and all repositories.
steps:
1. In `loadCourse`, rethrow `CancellationException`. On any other exception, `Log.e(TAG, …)` and set `_uiState.value = TakeCourseUiState.NotFound`. `TakeCourseViewModel` has no `TAG` or `Log` import yet, so add `import android.util.Log` and a companion `private const val TAG = "TakeCourseViewModel"`.
2. In `getRatingPromptDecision`, rethrow `CancellationException` before the existing `false` fallback.
3. In `saveExamination`, add `catch (e: CancellationException) { throw e }` before the generic catch, and replace `e.printStackTrace()` with `Log.e(TAG, …)`. `TAG` already exists at line 141.
4. Add tests: a repository that throws in `getCourseById` drives the state to `NotFound`, and a `saveExamination` whose repository throws `IOException` still emits `false`.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*TakeCourseViewModelTest" --tests "*HealthExaminationViewModelTest"` passes; the full suite stays green.
- On a device, a course that fails to load shows the "failed to load course" toast and goes back instead of an endless spinner.
- Saving an examination still reports success or failure correctly.
size budget: about 45 changed lines in 4 files.
out of scope: no new UI states and no fragment changes.

---
<a id="m012"></a>

### M012. ResourcesPreviewLoader.getTextPreview: cap the read at 4096 chars before the 8-line limit

**score 83** · O 55 / D 71 / C 91 · kept by 3/3 plans · raw: L04.01  
**refs:** Opus #23 · Devin R048 · Codex T011

**guardrails:**
- Codex scope: Read at most a fixed character budget before selecting the first eight preview lines. Preserve line ordering, empty-file behavior, charset handling, and cache keys; ensure a giant single-line file never enters the cache in full.

**verified against tree:** Verified at :90. A single-line huge file is fully materialized and cached (the LruCache counts entries, not bytes).

**canonical version — L04-T1 (Codex), "Bound text-preview reads before splitting into lines (roadmap 7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt:84` implements `getTextPreview`, but line 90 limits only the number of lines after the reader has materialized each entire line. A large single-line text resource can therefore allocate and cache its full contents even though the UI needs a small preview; the 64-entry cache at lines 25–27 bounds entries rather than text bytes.

files:

- `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt` — `ResourcesPreviewLoader.getTextPreview` and a private preview-length limit only; leave `getAudioPreview`, `getCsvPreview`, cache keys, and eviction behavior alone.
- `app/src/test/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoaderTest.kt` — `ResourcesPreviewLoaderTest`; extend its existing text-preview and cache coverage.

steps:

1. Read at most 4,096 UTF-16 code units into a bounded buffer, looping to handle short reads and closing the original reader on every exit. Do this before any line-reading operation on the source file.
2. Apply the existing maximum of eight lines to the bounded in-memory text, preserving existing LF, CRLF, CR, empty-file, and final-line formatting for inputs below the limit. Keep the current null-on-error contract and injected IO dispatcher.
3. Cache only the bounded result through the existing metadata key; do not introduce a second cache or cache failures.
4. Add tests for a very long single line, a character limit reached before line eight, and ordinary short files with line endings. Retain the existing metadata-invalidation and eviction tests.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.ResourcesPreviewLoaderTest'`
- `./gradlew testDefaultDebugUnitTest`
- Browse an offline long-line text resource: its card shows a prefix of at most 4,096 code units and eight lines, while opening the resource still shows its complete contents. Short previews retain their current text.

size budget: approximately 60–100 changed lines, 2 files.

out of scope: Do not alter CSV/audio previews, file encodings, download behavior, or the full resource viewer.

---
<a id="m013"></a>

### M013. ResourcesFragment: first-wins id/localAddress/tag maps instead of linear find scans

**score 83** · O 50 / D 77 / C 87 · kept by 3/3 plans · raw: L02.05, L05.09  
**refs:** Opus #30 · Devin R016 · Codex T018

**guardrails:**
- Codex scope: Rebuild resource-ID, local-address, and tag-ID indexes whenever allResourceModels is assigned, including sorting. Replace click/selection scans and tag flattening while preserving the current first-match behavior for duplicate keys.
- L05.09 correction from Codex: Changed associateBy-style last-wins indexing to first-wins indexing to match find; include the sorting assignment as well as initial/cached loads.

**verified against tree:** Verified. onSelectedListChange is O(selected×N) on the UI thread. Use first-wins maps, because associateBy keeps the last duplicate. L02-T5 misses the sort-toggle assignment at :781-787.

**contributor notes:** L02-T5: misses an assignment site

**canonical version — L05-T9 (ClaudeCode), "Replace O(n²) id lookups in the Resources list with a map built once per list update (roadmap 7+8)":**

**context:**
- `ResourcesFragment.onSelectedListChange` (`main/ui/resources/ResourcesFragment.kt:572-580`) maps the adapter's whole selected list back to library models with `allResourceModels.find { it.item.id == item.id }`. It does this on every checkbox tap and on "select all", which over N resources is ~N²/2 string compares on the UI thread.
- `onTagClicked(TagItem)` (line 583) flattens the tags of every resource on each tag tap.
- `onSingleResourceDownloaded` (line 596) and `onResourceClicked` (line 602) also scan linearly.

**files:**
- touch:
  - `main/ui/resources/ResourcesFragment.kt`:
    - field `allResourceModels` (line 86) and its assignments at lines 212, 249 and 781-788
    - `onSelectedListChange`, `onTagClicked(TagItem)`, `onSingleResourceDownloaded`, `onResourceClicked`
- leave alone:
  - `ResourcesAdapter.kt` and `ResourcesCardBinder.kt` (open PR 18540)
  - `ResourcesViewModel.kt`
  - `ResourcesListFilter`

**steps:**
1. Turn `allResourceModels` into a property with a private setter-backed field. Each assignment rebuilds three maps (or computes them lazily per assignment):
   - `modelsById = value.associateBy { it.item.id }`
   - `modelsByLocalAddress = value.associateBy { it.item.resourceLocalAddress }`
   - `tagsById = value.asSequence().flatMap { it.tags.asSequence() }.associateBy { it.id }`
   The three existing assignment sites then need no other change.
2. `onSelectedListChange`: `list.mapNotNull { modelsById[it.id]?.library }`.
3. `onTagClicked(TagItem)`: `tagsById[tag.id]`. `onSingleResourceDownloaded`: `modelsByLocalAddress[localAddress]?.item?.id`. `onResourceClicked`: `modelsById[item.id]`.
4. Remove the now-unused linear scans. Do not leave the old code commented out.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green (`ResourcesFragmentActionTest`, `ResourcesFragmentStateTest`).
- Manually, in My Library with many resources:
  - "select all" is instant, and the Download/Remove buttons enable correctly;
  - tapping a tag chip filters by that tag;
  - tapping a card opens its detail;
  - a finished single download flips the card to offline.

**size budget:** ~35 changed lines, 1 file

**out of scope:**
- Do not move `applyFiltersAndUpdateUI` / `getFilteredCount` off the main thread. The `OnFilterListener.filter(): Int` contract makes that a bigger change.

---
<a id="m014"></a>

### M014. UploadConfigs: wrap each batch's mark-uploaded writes in AppDatabase.withTransaction

**score 83** · O 50 / D 80 / C 85 · kept by 3/3 plans · raw: L05.03  
**refs:** Opus #31 · Devin R013 · Codex T025

**guardrails:**
- Codex scope: Move per-row upload acknowledgement loops behind repository/DAO batch operations and one transaction per table/batch. Preserve each config failure-list contract, row-count handling, and existing revision merge behavior; reuse existing batch APIs rather than nesting network work inside a transaction.
- L05.03 correction from Codex: Use database/DAO transaction ownership rather than treating a loop inside an arbitrary service as a Room transaction. Existing batch methods must be reused where present.

**verified against tree:** Verified. The RoomDatabaseKt static mock must go in @Before for every UploadConfigsTest test, and injecting AppDatabase into a service bends the layering.

**canonical version — L05-T3 (ClaudeCode), "Commit each upload batch's "mark uploaded" writes in one transaction (roadmap 5+7)":**

**context:**
- `main/services/upload/UploadConfigs.kt` marks rows uploaded one at a time, and each row is a separate SQLite commit. The configs affected are:
  - NewsActivities (lines 58-62), CourseProgress (71-75), TeamTask (87-91), TeamActivities (100-104), SearchActivity (113-117)
  - ResourceActivities (130-134), ResourceActivitiesSync (143-147), CourseActivities (156-160)
  - Meetups (175-179), Feedback (201-204), SubmitPhotos (228-232), Rating (298-302)
- The pattern is `results.filter { result -> !repo.markXUploaded(result.localId, …) }`.
- `RoomUploadConfig` sends batches of up to 50 rows (`main/services/upload/RoomUploadConfig.kt:35`). These configs run on every auto-sync, login upload and bulk upload, so a single sync can do hundreds of separate commits.

**files:**
- touch:
  - `main/services/upload/UploadConfigs.kt` (constructor at lines 33-48, plus the `markUploaded` lambdas listed above)
  - `test/services/upload/UploadConfigsTest.kt` (constructor call at lines 73-88)
- leave alone:
  - `getResourcesConfig` (lines 267-289): `markResourceUploaded` moves folders on disk, and file IO must not run inside a DB transaction
  - CrashLog (already batched)
  - ExamResults and Submissions (task 2 owns `markSubmissionsUploaded`)
  - every `*RepositoryImpl`

**steps:**
1. Inject `org.ole.planet.myplanet.data.room.AppDatabase` (already provided by `main/di/RoomModule.kt:56`) into the `UploadConfigs` constructor.
2. Add one private helper, `suspend fun <T> inTransaction(block: suspend () -> T): T = appDatabase.withTransaction { block() }`. Wrap the `results.filter { … }` of each of the 12 lambdas listed in the context in it. Keep the filter logic identical.
3. In `UploadConfigsTest`, pass a relaxed `AppDatabase` mock. Stub `withTransaction` the way `test/repository/CoursesRepositoryImplTest.kt:469-487` does (`mockkStatic("androidx.room.RoomDatabaseKt")` plus `coAnswers` that invokes the captured block), and unmock it in `@After`.
4. Add one test proving that a config's `markUploaded` (for example `CourseProgress`) runs inside `withTransaction` and still returns only the failed results.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, create course progress and a feedback item offline, then sync. Both upload once and are not re-sent on the next sync.

**size budget:** ~60 changed lines, 2 files

**out of scope:**
- Do not move the batching into the repositories.
- Do not change `RoomUploadConfig` or `UploadCoordinator`.

---
<a id="m015"></a>

### M015. TeamsVoicesFragment: drop the duplicate one-shot feed load; badge watermark via countTopLevelByTeam

**score 82** · O 71 / D 71 / C 83 · kept by 3/3 plans · raw: L05.08  
**refs:** Opus #8 · Devin R044 · Codex T031

**guardrails:**
- Codex scope: Keep the discussions Flow as the feed source; replace the duplicate one-shot feed read with countTopLevelByTeam for the seen watermark. Preserve count-to-Int bounds, team identity, badge semantics, and initial empty rendering.

**verified against tree:** Verified: the one-shot query at TeamsVoicesFragment.kt:125-128 is the same SQL as the flow, and its result is used only for the size. After the change, getFilteredNews becomes dead code.

**canonical version — L05-T8 (ClaudeCode), "Open Team Voices with one feed load and a SQL count for the badge watermark (roadmap 3+7, moves 10)":**

**context:**
- When a team's Voices tab opens, `main/ui/teams/voices/TeamsVoicesFragment.kt:123-128` subscribes to `observeDiscussions` and also runs a one-shot load:
  `val realmNewsList = viewModel.getFilteredNews(getEffectiveTeamId()); showRecyclerView(realmNewsList)`
- That one-shot reads every top-level team post with full message text through `NewsDao.getTopLevelByTeam`, which is the same SQL the flow already runs. It renders the adapter twice and exists only so `TeamsVoicesViewModel.getFilteredNews` (`TeamsVoicesViewModel.kt:58-62`) can pass `newsList.size` to `updateTeamNotification`.
- `VoicesRepository.countTopLevelByTeam` (`main/repository/VoicesRepository.kt:46`, backed by `NewsDao.countTopLevelByTeam`) already returns that number. `test/repository/TeamChatBadgeIntegrationTest.kt:144-155` asserts that the count equals the displayed feed size.

**files:**
- touch:
  - `main/ui/teams/voices/TeamsVoicesFragment.kt` (`onViewCreated`, lines 123-128)
  - `main/ui/teams/voices/TeamsVoicesViewModel.kt` (`getFilteredNews`, lines 58-62)
  - `test/ui/teams/voices/TeamsVoicesViewModelTest.kt` (test at lines 62-74)
- leave alone:
  - `VoicesRepository.kt` / `VoicesRepositoryImpl.kt` (task 6 owns the Impl; `getFilteredNews` stays in the interface for its other users)
  - `VoicesAdapter.kt` (task 10)

**steps:**
1. In the ViewModel, replace `suspend fun getFilteredNews(teamId)` with `fun markDiscussionsSeen(teamId: String)`. It launches in `viewModelScope` and calls `notificationsRepository.updateTeamNotification(teamId, voicesRepository.countTopLevelByTeam(teamId).toInt())`.
2. In the fragment, delete the one-shot `lifecycleScope.launch { … showRecyclerView(realmNewsList) }` block and call `viewModel.markDiscussionsSeen(getEffectiveTeamId())` in its place. The `discussions` collector (line 133-135, `setData` → `showRecyclerView`) stays the only render path.
3. Rewrite the ViewModel test. It verifies:
   - `updateTeamNotification(teamId, count)` is called with the stubbed `countTopLevelByTeam` value;
   - `voicesRepository.getFilteredNews` is never called.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green, including `TeamChatBadgeIntegrationTest`.
- Manually:
  - opening a team's Voices tab shows the posts once, with no double flicker;
  - the team's chat badge clears after opening;
  - a new post from another member brings the badge back.

**size budget:** ~35 changed lines, 3 files

**out of scope:**
- Do not make `_discussions` nullable or change the empty-state handling.
- Do not change `NewsDao`.

---
<a id="m016"></a>

### M016. Warm a master debug build cache so first pushes of new branches stop cold-compiling

**score 80** · O 78 / D 74 / C 75 · kept by 3/3 plans · raw: L14.01  
**refs:** Opus #1 · Devin R022 · Codex T057

**Megaplan caution:** Codex T057 (L14.01 correction) points out that a missing `app/build` cache does not alone prove a cold compile, because branches also restore caches from the default branch, including the Gradle home that `test.yml` saves on master. Before building the new workflow, open one fresh-branch `build.yml` log and confirm that `compileDefaultDebugKotlin` actually executes rather than coming `FROM-CACHE`. If it already hits, drop this task. The combined ranking puts this task well below Opus's #1 for this reason.

**guardrails:**
- Codex scope: Add a master/default-branch cache producer compatible with both debug flavor keys and restore paths, reusing existing Gradle setup and failure handling. Preserve branch isolation and validate actual warm task reuse before reporting compile savings.
- L14.01 correction from Codex: Only the current workflow structure is verified. Branches can reuse eligible same-branch/default-branch caches; GitHub does not share only default-branch caches. A missing app/build cache also does not prove cold compilation when the existing Gradle remote cache is configured.

**verified against tree:** Verified: build.yml skips master, and nothing saves the build-dir-build-* prefix there. Watch the 10 GB cache quota and the runner cost per master push.

**canonical version — L14-T1 (Copilot·Grok), "save a master debug build cache so PR builds stop cold-compiling (roadmap 7)":**

context: `.github/workflows/build.yml:5-7` runs on every branch except `master`, and its actions cache key is `build-dir-build-${{ matrix.build }}-${{ github.sha }}` with restore prefix `build-dir-build-${{ matrix.build }}-` (lines 50-52). GitHub only shares caches saved on the default branch. Build run 42781301168 therefore logged `Cache not found for input keys: build-dir-build-default-…, build-dir-build-default-` and the lite twin, then executed every task (default 4m36s, lite 4m3s). A master push that writes that same key prefix is the collision-free fix. This does not move roadmap 9 or 10; it only shortens the compile that those refactors have to wait on.

files: add `.github/workflows/warm-debug-cache.yml` only. Neighbor to leave alone: `.github/workflows/build.yml` (open PR #15226 owns it; copy its cache key, do not modify it).

steps:
1. New workflow, push to `master` only, `permissions: contents: read`, concurrency group that is not `build-${{ github.ref }}`.
2. Matrix `build: [default, lite]` on `ubuntu-24.04`, timeout 10 minutes, `fail-fast: false`.
3. Copy the checkout, `gradle/actions/setup-gradle@v6.4.1` (`cache-read-only: false`), and `actions/cache@v6` steps from `build.yml`, including the same path exclusions and the exact key and restore-keys above. A different key will not be restored by PR builds.
4. Run the same `assemble${FLAVOR^}Debug` command, including the existing retry that deletes `app/build/kotlin` only when the log contains `Incremental compilation failed`. Pass the same `GRADLE_BUILD_CACHE_*` env vars `build.yml` passes; do not invent new secrets.
5. Do not upload artifacts. The actions cache save is the deliverable.

acceptance: the new file is valid YAML; its cache `key` and `restore-keys` strings match `build.yml:50-52` exactly; `on.push.branches` is only `master`. No change to `./gradlew testDefaultDebugUnitTest` behavior. After it has run once on master, a later PR build's "restore project build dir" step must no longer log `Cache not found` for the `build-dir-build-default-` / `build-dir-build-lite-` prefix.

size budget: ~70 lines, 1 new file.

out of scope: do not edit `build.yml`, do not enable the remote Gradle cache, do not change product flavors.

---
<a id="m017"></a>

### M017. Retire the single-test Robolectric RetryInterceptorEventTest into plain JUnit

**score 78** · O 69 / D 71 / C 79 · kept by 3/3 plans · raw: L14.02, L15.02  
**refs:** Opus #11 · Devin R030 · Codex T040

**guardrails:**
- Codex scope: Give BroadcastService a retry-event operation that constructs the Intent and preserves encoded path, attempt, delay, and nonblocking emission. Keep interceptor retry/backoff policy independent of Intent construction.
- Codex scope: Exercise retry-event emission policy in the existing plain JVM interceptor tests without a separate one-test Robolectric class.
- Codex scope: Retain actual action/extras and credential-exclusion coverage at the Android BroadcastService boundary when relocating the old test.
- L15.02 correction from Codex: Constructor-call verification covers interceptor policy, not the actual stored Intent extras. The merged task retains that Android-boundary coverage instead of relying only on anyConstructed<Intent>() interactions.

**verified against tree:** Verified: it is a one-test Robolectric class. L15-T2 keeps the extras assertions via mockkConstructor (unmock afterwards). L14-T2 also moves the Intent into BroadcastService but leaves the extras contract untested.

**canonical version — L14-T2 (Copilot·Grok), "move the retry Intent into BroadcastService and drop the Robolectric event test (roadmap 7+9)":**

context: `RetryInterceptor.kt:62-68` builds an `android.content.Intent` inside OkHttp `intercept` and is the class's only `android.*` import. The extras contract (`url`, `attempt`, `delay` on `Constants.ACTION_RETRY_EVENT`) is asserted only by `RetryInterceptorEventTest`, which is `@RunWith(RobolectricTestRunner)` for that one read. On master test run 42782082676 shard 2, `RetryInterceptorEventTest.testRetryEventUrlContainsOnlyEncodedPath` took 10.5s and the class took 12.4s. `RetryInterceptorTest` is already plain JUnit and sets `initialDelay = 10L` with `TestTimeProvider`. Moving Intent construction to the Android edge removes the platform import from the interceptor (roadmap 9) and deletes a Robolectric sandbox the shard is over budget for (roadmap 7).

files: `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt` (`intercept`), `app/src/main/java/org/ole/planet/myplanet/services/BroadcastService.kt` (`trySendBroadcast`), `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt`, and delete `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt`. Leave `app/src/test/java/org/ole/planet/myplanet/services/BroadcastServiceTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/Constants.kt`, and `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` alone.

steps:
1. Add `trySendRetryEvent(url: String, attempt: Int, delayMs: Long): Boolean` on `BroadcastService`. It must build `Intent(Constants.ACTION_RETRY_EVENT)` with the same three extra names and types the interceptor writes today, then call the existing `trySendBroadcast`.
2. Replace the Intent block in `RetryInterceptor.intercept` with that call. Delete the `android.content.Intent` import. Do not add any other `android.*` import.
3. Port the encoded-path assertion into `RetryInterceptorTest` by verifying `trySendRetryEvent("/db/_find", 1, 10L)` for the same `http://satellite:1234@host:5984/db/_find?x=y` POST that the Robolectric test uses. Keep `initialDelay = 10L`. Do not add `@RunWith` to `RetryInterceptorTest`.
4. Delete `RetryInterceptorEventTest.kt`. No suite list references it.

acceptance: `./gradlew testDefaultDebugUnitTest` green. `RetryInterceptor.kt` has zero `android.*` imports. The encoded path, attempt, and delay still match the old extras. Retrying a 5xx still broadcasts once per attempt; a successful response still does not.

size budget: ~40 changed lines, 3 files edited, 1 file deleted.

out of scope: do not change backoff, `maxRetries`, or which requests are retry-safe. Do not rename the action string or the extra keys.

---
<a id="m018"></a>

### M018. NotificationsViewModel: remove @ApplicationContext / R.string (strings holder or formatter)

**score 78** · O 55 / D 77 / C 77 · kept by 3/3 plans · raw: L11.03, L17.09  
**refs:** Opus #20 · Devin R015 · Codex T052  
**shares files with:** M032, M055

**guardrails:**
- Codex scope: Move localized row text to a UI-owned formatter/string provider or explicit UI text descriptors. Keep grouping and raw enrichment in the ViewModel, preserve locale-aware arguments and existing formatting output, and make tests assert real text.

**verified against tree:** Verified at :33 (seven getString calls). L17-T9's acceptance grep matches withContext and would never pass.

**canonical version — L11-T3 (ClaudeCode), "Remove Android Context and R.string from NotificationsViewModel (roadmap 3+6, moves 10)":**

context: `ui/notifications/NotificationsViewModel.kt:33` injects `@ApplicationContext context: Context`
only to call `context.getString(R.string.…)` while formatting rows (:298, :304-305, :320-323,
:347-349). A ViewModel that holds Context and `R.*` cannot move to a Compose Multiplatform screen,
and it ignores per-Activity locale overrides, because the application context's locale can differ
from the screen's. The unit test also has to pass `mockk<Context>(relaxed = true)` (`NotificationsViewModelTest.kt:49`),
so all formatted text in tests is an empty string.
files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`
(`loadNotifications`, `formatNotification`, `formatTaskNotification`),
`app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt`
(call sites :79, :89, :179), `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`.
Leave alone: `NotificationsRepository*` and `NotificationsAdapter.kt`.
steps:
1. In `NotificationsViewModel.kt` add a top-level `data class NotificationStrings` with
   `storageRunningLow: String`, `storageAvailable: String`, `joinRequestPrefix: String` and three
   function-typed fields: `resourceCount: (Int) -> String`, `taskDue: (title: String, date: String) -> String`,
   `userRequestedToJoin: (user: String, team: String) -> String`.
2. Change `loadNotifications(userId, filter, isAdmin)` to
   `loadNotifications(userId, filter, isAdmin, strings: NotificationStrings)`. Pass `strings` into
   `formatNotification`/`formatTaskNotification` and replace every `context.getString(...)` with the
   matching field. Delete the `context` constructor parameter and the `Context`, `ApplicationContext`
   and `R` imports.
3. In `NotificationsFragment.kt` add one `private val notificationStrings by lazy { NotificationStrings(...) }`
   built from `getString(R.string.storage_running_low)`, `R.string.storage_available`,
   `R.string.join_request_prefix`, `R.string.resource_notification`, `R.string.task_notification`
   and `R.string.user_requested_to_join_team`. Pass it at all three `loadNotifications` calls.
4. In the test, drop the Context mock from the constructor. Pass a fake `NotificationStrings` with
   fixed values (e.g. `resourceCount = { "res:$it" }`) from `loadNotificationsWithFilter` and from
   the direct `loadNotifications` calls. Add one assertion that a `resource` row with message `"3"` renders as `"res:3"`.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*NotificationsViewModelTest"` passes,
then `./gradlew testDefaultDebugUnitTest` stays green. `grep -n "android\.\|R\.string" NotificationsViewModel.kt` returns nothing.
On device: notifications show the same resource-count, storage, task-due and join-request texts in English and in Arabic.
size budget: ~90 changed lines, 3 files.
out of scope: do not change grouping or selection logic, `formatStorageNotification` /
`formatJoinRequestNotification` signatures, or the repository enrichment.

---
<a id="m019"></a>

### M019. Narrow learning/resource ViewModels to UserReadRepository (ResourcesAccess, Resources, SubmissionDetail, ResourceViewer, TakeCourse, CoursesSteps)

**score 77** · O 55 / D 82 / C 75 · kept by 3/3 plans · raw: L07.04, L07.10, L08.10, L10.06, L10.07, L10.08, L10.10, L11.02, L12.02, L12.08, L12.09  
**refs:** Opus #15 · Devin R011, R019, R091, R089, R079, R010 · Codex T062, T053, T060, T061, T037  
**shares files with:** M011, M021, M025, M040, M041, M047, M051, M065, M071

**guardrails:**
- Codex scope: Narrow ResourcesAccessViewModel while retaining its existing guest test and resource operations.
- Codex scope: Narrow ResourcesViewModel, whose user dependency only resolves the current user.
- Codex scope: Narrow ResourceViewerViewModel while preserving rating-prompt identity behavior.

**verified against tree:** Verified: each calls only getUserModel() or getUserById(). The tests still compile because a UserRepository mock is a subtype.

**canonical version — L11-T2 (ClaudeCode), "Narrow learning/resource ViewModels from UserRepository to UserReadRepository (roadmap 1+3, moves 9)":**

context: same leak as task 1, in the course and resource flows. Each of these injects the full
`UserRepository` but only reads the current user or one user by id:
`ui/courses/CoursesStepsViewModel.kt:49` (only :68 `getUserModel`), `ui/courses/TakeCourseViewModel.kt:42`
(only :117), `ui/resources/ResourcesViewModel.kt:31` (only :39), `ui/submissions/SubmissionDetailViewModel.kt:30`
(only :38 `getUserById`), `base/ResourcesAccessViewModel.kt:21` (only :49). Narrowing makes the
read-only contract explicit and stops screens that only read from also being able to write.
files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt`.
Leave alone: all their test files (task 6 rewrites `SubmissionDetailViewModelTest.kt`), and
`ui/viewer/ResourceViewerViewModel.kt` (it also reads `SharedPrefManager`; separate concern).
steps:
1. In each of the five files, change `userRepository: UserRepository` to `userRepository: UserReadRepository`
   and keep the parameter name.
2. Swap the import to `org.ole.planet.myplanet.repository.UserReadRepository`, keeping imports sorted.
3. `grep -n "userRepository\." <file>` must list only `getUserModel` / `getUserById` calls.
4. Run the affected test classes, then the full unit-test task.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CoursesStepsViewModelTest" --tests "*TakeCourseViewModelTest" --tests "*ResourcesViewModelTest" --tests "*SubmissionDetailViewModelTest" --tests "*ResourcesAccessViewModelTest"`
passes, `./gradlew testDefaultDebugUnitTest` stays green, `./gradlew assembleDefaultDebug` builds.
On device: opening a course step, taking a course, the resource library list, a submission
detail and opening an HTML resource all behave as before. A guest user still sees the guest gating.
size budget: ~10 changed lines, 5 files.
out of scope: no repository or DI changes. Do not touch task 1's ViewModels.

---
<a id="m020"></a>

### M020. Narrow home/dashboard ViewModels to UserReadRepository (Calendar, CommunityTab, DashboardElement, Activities, Personals)

**score 77** · O 55 / D 82 / C 75 · kept by 3/3 plans · raw: L07.03, L07.04, L08.10, L10.09, L10.10, L11.01, L11.08  
**refs:** Opus #16 · Devin R010, R011, R084 · Codex T061, T062, T053, T106  
**shares files with:** M099

**guardrails:**
- Codex scope: Narrow CalendarViewModel to UserReadRepository; its user dependency only obtains the current user.
- Codex scope: Narrow CommunityTabViewModel to UserReadRepository.
- Codex scope: Narrow DashboardElementViewModel to UserReadRepository.
- Codex scope: Narrow ActivitiesViewModel to UserReadRepository.
- Codex scope: Narrow PersonalsViewModel to UserReadRepository.
- L07.03 part dropped by Opus: FeedbackListViewModel target: already narrowed (FeedbackListViewModel.kt:20).
- L07.03 correction from Codex: FeedbackListViewModel already imports and injects UserReadRepository at lines 15/20, so that proposed fifth edit is dropped.

**verified against tree:** Verified: each calls only getUserModel(), and the binding exists (RepositoryModule.kt:229). FeedbackListViewModel is already narrowed.

**contributor notes:** L07-T3: Feedback target already done · L07-T4: Personals · L10-T10: Personals · L11-T8: CommunityTab narrowing

**canonical version — L11-T1 (ClaudeCode), "Narrow home-screen ViewModels from UserRepository to UserReadRepository (roadmap 1+3, moves 9)":**

context: four home-area ViewModels inject the full `UserRepository` (38 members of its own plus 8
inherited: login, sync, profile writes, guest handling) but only ever call `getUserModel()`. That gives a read-only
screen write access to the user store, and it ties these ViewModels to the largest repository
surface in the app, which a platform-free core will have to split anyway. Evidence:
`ui/personals/PersonalsViewModel.kt:31` (used only at :35), `ui/dashboard/DashboardElementViewModel.kt:13`
(only :19), `ui/dashboard/ActivitiesViewModel.kt:22` (only :39), `ui/calendar/CalendarViewModel.kt:24` (only :42).
files: `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`.
Leave alone: their `*ViewModelTest.kt` files (they still compile, see shared facts),
`di/RepositoryModule.kt` and `UserRepositoryImpl.kt` (both owned by open PRs), and the
`ActivitiesRepository`/`TeamsRepository`/`UserSessionManager` parameters in these constructors.
steps:
1. In each of the four files, change the constructor parameter type `userRepository: UserRepository`
   to `userRepository: UserReadRepository`. Keep the parameter name so named-argument call sites in tests still work.
2. Replace `import org.ole.planet.myplanet.repository.UserRepository` with
   `import org.ole.planet.myplanet.repository.UserReadRepository`. Keep imports lexicographically sorted.
3. Confirm that no other `userRepository.` call exists in these files (`grep -n "userRepository\." <file>`
   shows only `getUserModel`).
4. Run the four ViewModel test classes, then the full unit-test task.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*PersonalsViewModelTest" --tests "*DashboardElementViewModelTest" --tests "*ActivitiesViewModelTest" --tests "*CalendarViewModelTest"`
passes, then `./gradlew testDefaultDebugUnitTest` stays green and `./gradlew assembleDefaultDebug` builds
(Hilt graph resolves). On device: the Personals list, the dashboard activity tiles, the Calendar
events and the dashboard logout still show data for the logged-in user.
size budget: ~8 changed lines, 4 files.
out of scope: do not touch any other ViewModel (tasks 2, 8 and 10 own the others). Do not add
methods to `UserReadRepository`.

---
<a id="m021"></a>

### M021. TakeCourseViewModel.loadCourse: overlap independent reads inside coroutineScope; reuse course.courseSteps

**score 76** · O 50 / D 71 / C 84 · kept by 3/3 plans · raw: L02.04, L06.02, L13.02, L15.06  
**refs:** Opus #29 · Devin R028 · Codex T026, T016  
**shares files with:** M011, M019, M051

**guardrails:**
- Codex scope: Start current-user and course reads concurrently; preserve NotFound handling and avoid publishing after cancellation.
- Codex scope: Overlap step and progress reads after their inputs are ready; compute earned passes and blocking-step state only after those results.
- L06.02 correction from Codex: Removed the unsupported claim that concurrency doubles speed or finishes platform portability; Room scheduling and device latency must be measured.

**verified against tree:** The reads are sequential and independent. Two versions (L13-T2, L15-T6) put a bare async inside viewModelScope.launch, which turns a repository error into a crash, so wrap the work in coroutineScope. Bigger win missed by all four: getCourseById already loads the steps (mapCourse), so drop the second getCourseSteps.

**contributor notes:** L02-T4: coroutineScope not specified · L15-T6: bare async crash pitfall · L13-T2: bare async crash pitfall

**canonical version — L06-T2 (Antigravity), "parallelize independent course, user, step, and progress reads in TakeCourseViewModel.loadCourse (roadmap 3+7)":**

context: `TakeCourseViewModel.kt:117-127` (`loadCourse`) sequentially awaits `userRepository.getUserModel()`,
`coursesRepository.getCourseById(courseId)`, `coursesRepository.getCourseSteps(courseId)`, and
`progressRepository.getCourseProgress(listOf(courseId), userModel?.id)` inside `viewModelScope.launch`.
Because `getUserModel()` and `getCourseById(courseId)` have no dependency on each other, and
`getCourseSteps(courseId)` and `getCourseProgress(listOf(courseId), userModel?.id)` are likewise independent
once `course` and `userModel` are resolved, running them serially doubles the DAO wait latency on course open.
Running each pair concurrently with `coroutineScope` and `async` advances roadmap 9 and 10 by keeping all
state orchestration in pure `kotlinx.coroutines` inside `TakeCourseViewModel` with zero `android.*` imports.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` (`TakeCourseViewModel.loadCourse`, lines 108-134)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt` (`TakeCourseViewModelTest`)
- leave alone: `TakeCourseViewModel.getBlockingStepIndex`, `TakeCourseViewModel.getRatingPromptDecision`, and `TakeCourseFragment`
steps:
1. Open `TakeCourseViewModel.kt` and add `kotlinx.coroutines.async` and `kotlinx.coroutines.coroutineScope` imports in alphabetical order.
2. In `loadCourse` (lines 117-123), wrap `userRepository.getUserModel()` and `coursesRepository.getCourseById(courseId)` in a `coroutineScope` with two `async` coroutines and await both before checking `if (course == null) { _uiState.value = TakeCourseUiState.NotFound; return@launch }`.
3. Immediately after the `course == null` guard (lines 124-126), wrap `coursesRepository.getCourseSteps(courseId)` and `progressRepository.getCourseProgress(listOf(courseId), userModel?.id)` in a second `coroutineScope` with two `async` coroutines, await both, and pass `steps` and `userModel` into `getBlockingStepIndex(steps, userModel, courseId)` as before.
4. In `TakeCourseViewModelTest.kt`, add a concurrency test using `CompletableDeferred` gates asserting that `getUserModel` and `getCourseById` start concurrently and that `getCourseSteps` and `getCourseProgress` start concurrently after `course` is found.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.TakeCourseViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening a course in `TakeCourseFragment`
transitions through `TakeCourseUiState.Loading` to `TakeCourseUiState.Success` (or `NotFound` when missing)
with identical steps, progress, and blocking step index.
size budget: ~45 changed lines, 2 files
out of scope: do not change `getBlockingStepIndex` ordering (`saveEarnedExamPasses` must still complete before `getBlockingStepIndex` reads the lock), and do not touch `TakeCourseFragment` or repository implementations.

---
<a id="m022"></a>

### M022. VoicesRepositoryImpl / MyCourse: drain the download-link buffers after each save

**score 74** · O 58 / D 71 / C 77 · kept by 3/3 plans · raw: L05.06  
**refs:** Opus #14 · Devin R034 · Codex T049

**guardrails:**
- Codex scope: Deduplicate pending links and stop re-merging an unchanged process-lifetime buffer on every page. Serialize snapshot/merge/write/drain across both writers of the same preference so concurrent arrivals and failed writes cannot lose links; skip empty snapshots.
- L05.06 correction from Codex: Clearing before persistence under separate collection locks can lose links or race read-modify-write across writers. The shipped task requires shared serialization and failure-safe draining.

**verified against tree:** Premise verified: the buffers are never cleared, and the Voices ArrayList grows with duplicates. Missed hazard: news and courses sync in parallel and do an unsynchronized read-modify-write of the same pref key. Draining makes a lost update permanent, so add a shared Mutex.

**canonical version — L05-T6 (ClaudeCode), "Drain the download-link buffers after each save instead of rewriting them forever (roadmap 5+7, moves 9)":**

**context:**
- Two app-lifetime buffers collect download links during sync and are never cleared:
  - `VoicesRepositoryImpl` (a `@Singleton`) keeps `private val concatenatedLinks = ArrayList<String>()` (`main/repository/VoicesRepositoryImpl.kt:40`), so duplicates pile up. It is filled at lines 468-473 and saved after every news sync page (line 435).
  - `MyCourse`'s companion `HashSet` (`main/model/MyCourse.kt:81`) is saved per courses sync page, per shelf batch and at the end of sync.
- Each save (`VoicesRepositoryImpl.kt:553-567`, `MyCourse.kt:96-110`) parses the whole stored JSON from prefs, merges in the entire buffer again, and rewrites the SharedPreferences XML. So cost grows with every page for the life of the process.

**files:**
- touch:
  - `main/repository/VoicesRepositoryImpl.kt` (field at line 40, `saveConcatenatedLinksToPrefs` at lines 553-567)
  - `main/model/MyCourse.kt` (companion lines 81-110)
  - `test/repository/VoicesRepositoryImplTest.kt`
  - `test/model/MyCourseTest.kt`
- leave alone:
  - the call sites in `CoursesRepositoryImpl.kt` and `SyncManager.kt:499`
  - `SharedPrefManager.kt`
  - `ConfigurationsRepositoryImpl.getQueuedDownloads`

**steps:**
1. In both save functions, inside the existing `synchronized` block, copy the buffer and then `clear()` it.
2. Return early before touching prefs when the copied list is empty.
3. Change the `VoicesRepositoryImpl` field to a `LinkedHashSet<String>` so a page with repeated links doesn't buffer duplicates. Keep the `synchronized(concatenatedLinks)` usage at line 468.
4. Keep the merge-with-existing-prefs logic, so links saved earlier are not lost. Only the in-memory buffer is drained.
5. Tests:
   - two consecutive saves with no new links call `setConcatenatedLinks` once (`VoicesRepositoryImplTest`, via `insertNewsList` / bulk insert with a link in the message);
   - `MyCourse.saveConcatenatedLinksToPrefs` called twice after one `addConcatenatedLink` writes once and keeps the link (`MyCourseTest`).

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- Manually, after a fresh full sync, the "queued downloads" list (read by `ConfigurationsRepositoryImpl.getQueuedDownloads`) contains the same links as before, with no duplicates.

**size budget:** ~50 changed lines, 4 files

**out of scope:**
- Do not remove the per-shelf-batch save in `CoursesRepositoryImpl`.
- Do not move link storage to Room.

---
<a id="m023"></a>

### M023. HealthExaminationViewModel.saveExamination: rethrow CancellationException, Log.w instead of printStackTrace

**score 73** · O 38 / D 85 / C 90 · kept by 3/3 plans · raw: L03.07, L13.01, L15.07, L17.08, L18.10  
**refs:** Opus #63 · Devin R004, R081, R071, R009 · Codex T014, T015, T029, T001, T016, T008  
**shares files with:** M011, M027, M041

**guardrails:**
- Codex scope: Rethrow cancellation from saveExamination before sending a failed-save result; preserve ordinary failure reporting.
- Codex scope: Replace the save failure printStackTrace with the existing tagged logger.
- L13.01 correction from Codex: Rejected adding android.util.Log to RetryUtils; structured reporting must preserve its existing platform-independent imports. stderr is not literally invisible to logcat.

**verified against tree:** Verified at :119-121. Hygiene only, because viewModelScope is cancelled only at onCleared. The CancellationException import already exists (:8).

**contributor notes:** L15-T7: Health half · L17-T8: Health half · L18-T10: save-cancel step · L13-T1: printStackTrace slice

**canonical version — L03-T7 (Copilot·Kimi), "Stop swallowing CancellationException in HealthExaminationViewModel.saveExamination (roadmap 3+8)":**

context: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:120 catches generic `Exception` in `saveExamination`, prints the stack trace, and pushes `_saveResult.send(false)`. Because `CancellationException` is an `Exception`, leaving the screen mid-save reports a spurious "save failed" and breaks structured concurrency — a correctness micro-fix on the ViewModel layer (roadmap 3). The class already has `TAG` and uses `Log.w` in `encryptSign`, so the fix follows the file's own convention.

files: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt` — the `saveExamination` catch block only. Leave `loadData` and `encryptSign` untouched.

steps:
1. Add `catch (e: CancellationException) { throw e }` ahead of the generic catch, importing `kotlinx.coroutines.CancellationException`.
2. Replace `e.printStackTrace()` with `Log.w(TAG, "Saving examination failed", e)`.
3. Verify the `finally` block still resets `_isSaving` on both paths, and `_saveResult` still receives `false` only for real failures.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; navigating away mid-save no longer emits a false save result; a forced repository failure still emits `false`.

size budget: ~5 changed lines, 1 file.

out of scope: no changes to `HealthRepository`; no UI or navigation changes.

---
<a id="m024"></a>

### M024. ProgressRepositoryImpl.saveEarnedExamPasses: one submissions+answers load instead of per-step queries

**score 72** · O 48 / D 66 / C 85 · kept by 3/3 plans · raw: L02.02  
**refs:** Opus #32 · Devin R056 · Codex T024  
**shares files with:** M010

**guardrails:**
- Codex scope: Fetch the relevant user attempts and their answers in bounded batches, then group by parent ID for saveEarnedExamPasses. Preserve complete-status selection, parent construction, passing-required rules, and answer-based grading. Use an exam-type-only batch API only if equivalent to the original parent/status selection; otherwise add a bounded matching query. Index required exams by step with first-valid-match semantics.
- L02.02 correction from Codex: The suggested getExamSubmissionsByUser API filters type=exam, while the old parent/user/status lookup has no type predicate. Equivalence must be established or the batch query must preserve the broader selection.

**verified against tree:** Verified at :170. Omitted: ProgressRepositoryImplTest.kt:1075-1115 verifies the per-step calls and must be rewritten. The new path adds a type='exam' filter.

**canonical version — L02-T2 (Copilot·Grok), "batch earned-exam pass lookups when a course opens (roadmap 1+7)":**

context: TakeCourseViewModel.kt:67 calls ProgressRepositoryImpl.saveEarnedExamPasses before the blocking step can be shown.
saveEarnedExamPasses (ProgressRepositoryImpl.kt:166-174) then calls submissionsRepository.getSubmissionsByParentId once per step that is not already passed.
That repository method hydrates answers (SubmissionsRepositoryImpl.kt:365-366) because isPassingAttempt reads attempt.answers.
passedStepNums is already one query. requiredExamsByStepId (lines 192-197) still scans exams with firstOrNull once per required step.
getExamSubmissionsByUser (SubmissionsRepository.kt:81) and getAnswersBySubmissionIds (SubmissionsRepository.kt:82) already exist, and the DAO method does not hydrate answers.
The progress file still has other Android calls, so this task does not finish roadmap 9.
files: app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt, functions saveEarnedExamPasses and requiredExamsByStepId.
Do not edit TakeCourseViewModel.kt, SubmissionDao.kt, SubmissionsRepository.kt, SubmissionsRepositoryImpl.kt, or Answer.kt.
steps:
1. After the existing early returns, collect parent ids of the form "${exam.id}@$courseId" only for steps whose step number is not already in passedStepNums.
2. Load that user's exam rows once with the existing getExamSubmissionsByUser. Keep rows whose status is exactly "complete" and whose parentId is in the collected set.
3. Load answers for those row ids once with getAnswersBySubmissionIds. Group by Answer.submissionId and assign answers as a MutableList before isPassingAttempt, matching the assignment shape in hydrateSubmissions.
4. Keep the current pass rule: exam.isPassingAttempt(attempt.grade, answers non-empty and every answer grade == 1), then the existing saveCourseProgress call.
5. In requiredExamsByStepId, walk the exam list once and keep the first exam per stepId that is in requiredStepIds and has noOfQuestions > 0. Do not use associateBy. It keeps the last exam, and firstOrNull keeps the first.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open a course that has passing-required steps, including one already passed and one not passed. The blocking step index and any newly saved pass match the pre-change behavior, with one exam-submission query and one answer query instead of one submission query per step.
size budget: ~45 changed lines, 1 file
out of scope: no new DAO query, no status widening to "requires grading", and no change to the getBlockingStepIndex return rule.

---
<a id="m025"></a>

### M025. CoursesStepsViewModel.loadStep: launch the priority download and next-step prefetch as children of loadDataJob

**score 72** · O 38 / D 80 / C 91 · kept by 3/3 plans · raw: L04.04, L16.01  
**refs:** Opus #72 · Devin R012 · Codex T007  
**shares files with:** M019, M040, M071

**guardrails:**
- Codex scope: Launch priority-resource preparation and next-step prefetch as structured children of loadDataJob, so a superseding step cancels their suspended preparation. Preserve downloads already handed to the independent download service and the current-step state guards.

**verified against tree:** The code shape is verified (:110, :125), but the 'rapid step changes' scenario does not occur: the VM is per-fragment with a fixed stepId.

**contributor notes:** L04-T4: motivating scenario wrong

**canonical version — L16-T1 (Codex), "Cancel obsolete course-step download preparation with its load (roadmap 3+5+7)":**

context: `CoursesStepsViewModel.kt:64` cancels the previous load, but the launches at lines 110 and 125 use the broader ViewModel scope, so download preparation already launched for the old step survives that cancellation. The existing regression at `CoursesStepsViewModelTest.kt:430` covers a pending availability probe, before those launches; it does not cover suspended downstream work after the launches have started. This wastes preparation work when a learner changes steps rapidly.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` — `CoursesStepsViewModel.loadStep` only.
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt` — `CoursesStepsViewModelTest`, extending its deferred-response cancellation cases.
- Leave `saveCourseProgress`, `refreshInlineResources`, download-service internals, and repository implementations alone.

steps:
1. Make the two nested launches children of the active load coroutine, preserving their concurrency and the immediate publication of step state.
2. Retain the existing step-ID checks around download-state updates; allow cancellation to propagate normally.
3. Add a deferred next-resource lookup test: start step A's prefetch, load step B, then release A's lookup; A must not call the background-download coordinator.
4. Add a suspended priority-download preparation test proving that changing the step cancels that coroutine and leaves B's state intact; retain the existing successful download and prefetch assertions.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.courses.CoursesStepsViewModelTest'` must pass.
- `./gradlew testDefaultDebugUnitTest` must stay green.
- Navigate rapidly between course steps: the latest description/resources remain visible and obsolete suspended preparation is cancelled. An already-enqueued background download is outside this cancellation guarantee.

size budget: approximately 90–125 changed lines across 2 files.

out of scope: Do not cancel globally queued downloads, change download priorities, or redesign sync. Keeping cancellation and state ownership in the ViewModel supports roadmap 10's hoisted-state direction without claiming this Android UI is portable yet.

---

## Wave 2: solid wins

<a id="m026"></a>

### M026. PdfThumbnailLoaderTest: drop the lone @Config(sdk=[28]) pin (and the 28 jar from robolectricSdkJars)

**score 70** · O 67 / D 71 / C 73 · kept by 3/3 plans · raw: L18.04  
**refs:** Opus #13 · Devin R046 · Codex T069

**guardrails:**
- Codex scope: Replace the SDK-28-only pin with the project default sandbox and lightweight Application/manifest configuration, confirming the thumbnail tests do not rely on API-28 behavior. Retain IO-thread assertions and actual missing/non-PDF coverage.

**verified against tree:** Verified: it is the only SDK-28 class, so removing it eliminates a whole sandbox. Just delete the @Config line, because robolectric.properties already sets the application.

**canonical version — L18-T4 (Antigravity), "drop non-default SDK 28 Robolectric pin in PdfThumbnailLoaderTest (roadmap 7+8)":**

context: `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt:21`
annotates `PdfThumbnailLoaderTest` with `@Config(sdk = [28])` and does not set
`application = Application::class, manifest = Config.NONE`. As documented in
`docs/TESTING.md:135-149` and observed in `myPlanet test` CI logs (run `42782082676`), pinning
a non-default SDK forces Robolectric to boot a separate API 28 sandbox and load an additional
`android-all-instrumented` jar in the Gradle test fork, even though
`PdfThumbnailLoader.kt:12-44` has zero SDK-version branching. Serving roadmap 7 and 8,
switching to the shared default SDK sandbox with lightweight `Application` and `Config.NONE`
removes an entire Robolectric sandbox initialization in CI.
files:
- touch: `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt` (`PdfThumbnailLoaderTest` class annotations at lines 19-22)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`
steps:
1. Open `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt` and import `android.app.Application`.
2. Replace `@Config(sdk = [28])` at line 21 with `@Config(application = Application::class, manifest = Config.NONE)` so the test class reuses the shared default SDK 36 Robolectric sandbox without initializing `MainApplication`.
3. Run `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.PdfThumbnailLoaderTest` to confirm all 3 tests (`targetWidthPxZeroReturnsNull`, `missingOrNonPdfFileReturnsNullAndDoesNotThrow`, `statCallsRunOnIo`) pass.
acceptance: `./gradlew testDefaultDebugUnitTest` passes and `PdfThumbnailLoaderTest` completes
without booting a separate API 28 Robolectric sandbox.
size budget: ~4 changed lines, 1 file
out of scope: do not modify `PdfThumbnailLoader.kt` or add new test dependencies.

---
<a id="m027"></a>

### M027. UserRepositoryImpl.ensureUserSecurityKeys/saveKeyIv: skip the upsert when key/iv exist, lazy keygen, drop TextUtils; drop HealthExaminationViewModel's double user read

**score 68** · O 52 / D 64 / C 83 · kept by 3/3 plans · raw: L18.10  
**refs:** Opus #28 · Devin R081 · Codex T029, T014  
**shares files with:** M011, M023, M031, M033, M041

**guardrails:**
- Codex scope: Generate missing security keys only when needed, skip upsert when both existing values are already present, and defer saveKeyIv generation until the corresponding nonempty value is absent. Preserve the existing null-versus-empty policies of each method.
- Codex scope: Use ensureUserSecurityKeys as the primary patient-user read and fall back only as required by the existing null behavior, avoiding the unconditional duplicate fetch.

**verified against tree:** Verified at :409 and :669-675, and the VM at :70/:73. No UserRepositoryImplTest is added for the skip path.

**canonical version — L18-T10 (Antigravity), "skip redundant user upserts and remove TextUtils in UserRepositoryImpl and HealthExaminationViewModel (roadmap 1+3+7, advances 9)":**

context: `UserRepositoryImpl.ensureUserSecurityKeys`
(`app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:402-411`) calls
`upsertUser(user)` (`userDao.upsert` + `userDao.getById` at lines 333-337) even when `user.key`
and `user.iv` are already populated, and `saveKeyIv` at lines 669-677 eagerly generates an AES
key and IV via `AndroidDecrypter` before checking `model.key`/`model.iv` with
`android.text.TextUtils.isEmpty` (the only use of `android.text.TextUtils` in
`UserRepositoryImpl.kt`). Meanwhile, `HealthExaminationViewModel.loadData`
(`HealthExaminationViewModel.kt:70-76`) calls `userRepository.getUserById(userId)` immediately
before `userRepository.ensureUserSecurityKeys(userId)` (which calls `getUserById(userId)`
again), and `saveExamination` (`HealthExaminationViewModel.kt:119-121`) catches `Exception`
without rethrowing `CancellationException`. Serving roadmap 1, 3, and 7 while advancing north
star 9 (removing `android.text.TextUtils` from `UserRepositoryImpl`), this eliminates redundant
Room reads/writes and eager key generation on health examination loads.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt` (`import android.text.TextUtils` at line 5, `ensureUserSecurityKeys` at lines 402-411, `saveKeyIv` at lines 669-677), `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt` (`loadData` at lines 69-77, `saveExamination` at lines 119-121), `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModelTest.kt` (`loadData` tests at lines 56-117 and 285-332)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationActivity.kt`
steps:
1. In `UserRepositoryImpl.ensureUserSecurityKeys` (lines 402-411), track whether `user.key == null` or `user.iv == null` required generating a new value, and only call `upsertUser(user)` when a key or IV was generated (returning `user` directly when both already exist).
2. In `UserRepositoryImpl.saveKeyIv` (lines 669-677), replace the eager `AndroidDecrypter.generateKey()` / `generateIv()` calls and `!TextUtils.isEmpty(...)` checks with `val keyString = model.key?.takeIf { it.isNotEmpty() } ?: AndroidDecrypter.generateKey()` and `val iv = model.iv?.takeIf { it.isNotEmpty() } ?: AndroidDecrypter.generateIv()`, and remove `import android.text.TextUtils` at line 5.
3. In `HealthExaminationViewModel.loadData` (lines 69-77), call `user = userRepository.ensureUserSecurityKeys(userId) ?: userRepository.getUserById(userId)` so `getUserById(userId)` is not called twice when `ensureUserSecurityKeys` succeeds, and in `saveExamination` (lines 119-121), rethrow `if (e is CancellationException) throw e` before sending `false` to `_saveResult`.
4. Update `HealthExaminationViewModelTest.kt` to add a test verifying `saveExamination` rethrows `CancellationException` and confirm all `loadData` and `saveExamination` tests pass.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.health.HealthExaminationViewModelTest --tests org.ole.planet.myplanet.repository.UserRepositoryImplTest`
and `./gradlew testDefaultDebugUnitTest` pass; opening a patient's health examination screen no
longer writes to the `users` table when security keys already exist.
size budget: ~35 changed lines, 3 files
out of scope: do not modify `HealthRepositoryImpl.kt` or `HealthExaminationActivity.kt`.

---
<a id="m028"></a>

### M028. MyLifeDao: replace parameter-OR userId predicates with a Kotlin branch so the userId index is usable

**score 65** · O 39 / D 71 / C 78 · kept by 3/3 plans · raw: L08.03  
**refs:** Opus #52 · Devin R036 · Codex T047  
**shares files with:** M073

**guardrails:**
- Codex scope: Dispatch nullable/unscoped and non-null exact-user lookups to separate DAO queries for observe/get/visible/count methods. Preserve the null branch matching NULL, empty, and -- owners and its weight order; repository normalization stays unchanged.

**verified against tree:** Verified at MyLifeDao.kt:14-24. Marginal, because the table holds a handful of rows per user.

**canonical version — L08-T3 (Copilot·Grok), "make my-life user lookups use the userId index (roadmap 1+7+9)":**

context: `MyLifeDao.observeByUserId`, `getByUserId`, `getVisibleByUserId`, and
`countByUserId` (`MyLifeDao.kt:14-24`) all use
`(:userId IS NULL AND ...) OR (:userId IS NOT NULL AND userId = :userId)`.
`userId` is indexed (`MyLife.kt:14`), but the parameter OR is not sargable,
so every my-life load scans the table. `LifeRepositoryImpl.normalizeUserId`
(`LifeRepositoryImpl.kt:19-21`) already turns blank and `"--"` into null
before calling the DAO. The DAO itself must keep a null parameter meaning
"unscoped" and a non-null parameter meaning exact `userId` equality.
`MyLifeDaoTest.observeByUserId_withNullMatchesRowsWithNullEmptyOrPlaceholderUserId`
locks the null behavior. The DAO already has no `android.*` import; this
rewrite must not add one.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`
only. Do not edit `LifeRepositoryImpl.kt`, `LifeRepository.kt`, `MyLife.kt`,
or `MyLifeDaoTest.kt`.

steps: 1. Keep the four public signatures, including nullable `userId` and the
existing `ORDER BY weight ASC` on the three list queries. 2. Replace each
parameterized OR with a Kotlin branch inside the DAO: if `userId == null`,
run a query whose predicate is `userId IS NULL OR userId = '' OR userId = '--'`;
otherwise run `userId = :userId`. Name the new `@Query` methods with an
`Internal` suffix, matching `getByIdsInternal`. 3. Do not treat a blank or
`"--"` argument as unscoped. Only a null argument matches null, empty, and
`"--"` rows. `getVisibleByUserId` keeps `AND isVisible = 1` on both branches.
4. Run `MyLifeDaoTest` without changing its expectations.
`observeByUserId(null)` must still return the three unscoped rows and exclude
`user123`.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.MyLifeDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. My Life still
lists the signed-in user's rows in weight order, and a null user still sees
the shared null/blank/`"--"` rows.

size budget: ~45 changed lines, 1 file

out of scope: no repository normalization change, no new index, no viewmodel change

---
<a id="m029"></a>

### M029. Promote getCurrentUserId to UserReadRepository; narrow LifeViewModel

**score 63** · O 55 / D 66 / C 72 · kept by 3/3 plans · raw: L08.08, L12.10  
**refs:** Opus #18 · Devin R053 · Codex T075  
**shares files with:** M035, M050, M061

**guardrails:**
- Codex scope: Move getCurrentUserId to UserReadRepository and inject that contract into LifeViewModel. Preserve blank/-- normalization and getUserModel fallback; update the mock contract and rebuild callers rather than claiming binary compatibility automatically.
- L12.10 correction from Codex: Moving an interface method preserves rebuilt source callers but is not a universal binary-compatibility guarantee for precompiled implementations.

**verified against tree:** Verified. LifeViewModel cannot be narrowed without the promotion. L08-T8 forbids touching UserRepository.kt:80, but redeclaring it without `override` is a Kotlin compile error.

**contributor notes:** L08-T8: does not compile under its own constraints

**canonical version — L12-T10 (Antigravity), "promote getCurrentUserId to UserReadRepository and narrow LifeViewModel off UserRepository (roadmap 1+3+4)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt:20` injects the full 50-method `UserRepository` (`LifeViewModel.kt:15`) only because `resolveUserId()` (`LifeViewModel.kt:28-32`) calls `userRepository.getCurrentUserId()` alongside `userRepository.getUserModel()`.
However, `getCurrentUserId(): String?` is a pure read operation declared on `UserRepository` (`app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt:80`) rather than on its super-interface `UserReadRepository` (`app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5-9`).
Moving `suspend fun getCurrentUserId(): String?` from `UserRepository` up to `UserReadRepository` (which `UserRepository` already extends at `UserRepository.kt:23`) preserves source and binary compatibility for all `UserRepository` callers while allowing `LifeViewModel` to depend strictly on `UserReadRepository`, advancing roadmap 9.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt` (interface `UserReadRepository` at lines 5-9)
- touch `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt` (interface `UserRepository`, function `getCurrentUserId` at line 80)
- touch `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt` (class `LifeViewModel`, import at line 15 and constructor parameter at line 20)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt` (class `LifeViewModelTest`, import at line 22 and mock field at line 28)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt` untouched; do NOT touch `UserRepositoryImpl` — open PRs own it
steps:
1. In `UserReadRepository.kt` (lines 5-9), add `suspend fun getCurrentUserId(): String?` to `interface UserReadRepository`.
2. In `UserRepository.kt`, remove the duplicate `suspend fun getCurrentUserId(): String?` declaration at line 80 (since `UserRepository` extends `UserReadRepository` at line 23).
3. In `LifeViewModel.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 15) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change constructor parameter `private val userRepository: UserRepository` (line 20) to `private val userRepository: UserReadRepository`.
4. In `LifeViewModelTest.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 22) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change `private lateinit var userRepository: UserRepository` (line 28) to `private lateinit var userRepository: UserReadRepository`.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.life.LifeViewModelTest"` and confirm all 10 unit tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the MyLife screen loads, reorders, and toggles visibility for the active user via `UserReadRepository`.
size budget: ~12 changed lines, 4 files
out of scope: Do not modify `UserRepositoryImpl` or `LifeFragment.kt`.

---
<a id="m030"></a>

### M030. Migrate the last free Mockito tests to MockK

**score 63** · O 53 / D 71 / C 71 · kept by 3/3 plans · raw: L11.06  
**refs:** Opus #26 · Devin R047 · Codex T082  
**shares files with:** M065

**guardrails:**
- Codex scope: Migrate SubmissionDetailViewModelTest, CoursesAdapterTest, ResourcesAdapterTest, and DashboardSurveysAdapterTest to MockK without weakening assertions, interaction counts, or callback capture. Retain any required Robolectric runner.
- L11.06 correction from Codex: These are four selected tests, not the last Mockito tests: SubmissionViewModelTest also imports Mockito in this working tree. No CI/transitive-dependency failure is asserted without a build.

**verified against tree:** Verified: Mockito is not declared and arrives only transitively. Sequence this with the SubmissionDetailViewModelTest edits.

**canonical version — L11-T6 (ClaudeCode), "Migrate the last free Mockito unit tests to MockK (roadmap 8)":**

context: the project convention is MockK (`docs/TESTING.md`, `CLAUDE.md` "MockK (not Mockito)").
Mockito is not declared in `app/build.gradle` or `gradle/libs.versions.toml`, so these tests compile
only against a transitive jar that could disappear in any dependency bump. Four tests that no open
PR touches still use it: `ui/submissions/SubmissionDetailViewModelTest.kt:16-17,35-36,47,94-96`,
`ui/courses/CoursesAdapterTest.kt:16-21,33-43,62-112` (also the deprecated `MockitoAnnotations.initMocks`),
`ui/resources/ResourcesAdapterTest.kt:11-14,29-45,67,85`, `ui/dashboard/DashboardSurveysAdapterTest.kt:15-17,34,73,88`.
files: `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt`,
`app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesAdapterTest.kt`,
`app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapterTest.kt`,
`app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapterTest.kt`.
Leave alone: `ui/submissions/SubmissionViewModelTest.kt` (owned by an open PR) and every main-source file.
steps:
1. `SubmissionDetailViewModelTest`: `mock(X::class.java)` → `mockk()`, and ``` `when`(…).thenReturn(…) ``` → `coEvery { … } returns …`.
   Keep the user mock typed `UserRepository`. It compiles whether or not task 2 has merged.
2. `CoursesAdapterTest` / `ResourcesAdapterTest`: drop `@Mock` + `MockitoAnnotations.initMocks(this)`. Create
   the observer/listener with `mockk(relaxed = true)` in `@Before`. Rewrite
   `verify(m, times(n)).f(eq(a), eq(b), any())` as `verify(exactly = n) { m.f(a, b, any()) }`.
3. `DashboardSurveysAdapterTest`: `mock(AlertDialog::class.java)` → `mockk(relaxed = true)`, and `never()` → `verify(exactly = 0)`.
4. Remove every `org.mockito.*` import from the four files and sort imports. `grep -rn "org.mockito" <these files>` must return nothing.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*SubmissionDetailViewModelTest" --tests "*CoursesAdapterTest" --tests "*ResourcesAdapterTest" --tests "*DashboardSurveysAdapterTest"`
passes with the same number of test cases as before, and the full `./gradlew testDefaultDebugUnitTest` stays green.
No user-visible change.
size budget: ~70 changed lines, 4 files.
out of scope: do not add or remove test cases, do not touch main sources, and do not touch the Gradle files.

---
<a id="m031"></a>

### M031. UserRepositoryImplTest: class-level static mocks, per-test stubs + clearMocks

**score 63** · O 38 / D 72 / C 82 · kept by 3/3 plans · raw: L13.07, L15.03  
**refs:** Opus #70 · Devin R026 · Codex T033  
**shares files with:** M027, M033

**guardrails:**
- Codex scope: Install recurring UrlUtils, NetworkUtils, and Log instrumentation once per class. Reset answers/calls before each test and retain the later Log.e assertions.
- Codex scope: Avoid repeatedly installing SecurePrefs instrumentation in individual cases; reset and restore it without leaking stubs or weakening error-path assertions.

**verified against tree:** Verified. UrlUtils.getUrl is re-stubbed at :558/:583/:622/:649, so L13-T7's class-level stubs leak.

**contributor notes:** L13-T7: leaks re-stubs

**canonical version — L15-T3 (Copilot·Kimi), "hoist per-test static mocks in UserRepositoryImplTest to class-level setup (roadmap 8)":**

context:
UserRepositoryImplTest was the slowest class on master shard 1/2 at 33.7s across 30 tests (1.03s median).
Its @Before (app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt:82) re-instruments
`mockkObject(UrlUtils)` (line 84), `mockkObject(NetworkUtils)` (line 88) and `mockkStatic(Log::class)` (line 91) before every test
and tears them down in @After (lines 150-154) — the static-mock churn the CI timing report heuristics flag.
The same per-test `mockkObject(SecurePrefs)` pattern repeats inline at lines 555, 580, 601 and 618.
files:
app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt only.
Do NOT touch UserRepositoryImpl.kt (open PRs own it).
steps:
1. move the `mockkObject(UrlUtils)`, `mockkObject(NetworkUtils)` and `mockkStatic(Log::class)` registration into a `@BeforeClass @JvmStatic` companion function,
   and the matching `unmockkObject`/`unmockkStatic` into `@AfterClass @JvmStatic`.
2. in @Before keep the per-test `every { ... }` stubbing and add `clearMocks(...)`-style resets for the hoisted objects so tests stay isolated.
3. apply the same hoist for the repeated `mockkObject(SecurePrefs)` blocks, keeping each test's own stubs.
4. run the class and compare its summed time in `app/build/test-results/testDefaultDebugUnitTest/` against the 33.7s baseline.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*UserRepositoryImplTest*"` green with all 30 tests still running;
class total time in the JUnit XML measurably below 33.7s;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~60 changed lines, 1 file.
out of scope: no production code changes, no test deletion or weakening of assertions.

---
<a id="m032"></a>

### M032. NotificationsViewModel.toggleGroupExpansion: shared groupTypeOf for default-expanded and unread

**score 63** · O 34 / D 85 / C 86 · kept by 3/3 plans · raw: L06.07  
**refs:** Opus #105 · Devin R008 · Codex T021  
**shares files with:** M018, M055

**guardrails:**
- Codex scope: Share the existing lowercase/known-type fallback normalization between grouping and default-expansion checks. Reuse a current matching group unread count only when it corresponds to the current notification snapshot; preserve correct first-toggle behavior for unknown/mixed-case types.

**verified against tree:** A real but rare bug: it only hits team notifications whose subType is outside KNOWN_TYPES. The performance part is negligible.

**canonical version — L06-T7 (Antigravity), "reuse precomputed group unread state and normalize group keys in NotificationsViewModel.toggleGroupExpansion (roadmap 3+7+8)":**

context: `NotificationsViewModel.kt:99-105` (`toggleGroupExpansion`) falls back to
`isGroupDefaultExpanded(type, notifications.value)` (`NotificationsViewModel.kt:206-208`), which scans the
entire `notifications.value` list (`notifications.any { it.type == type && !it.isRead }`) even though
`groupedNotifications` (`NotificationsViewModel.kt:61-63`) already groups items and precomputes
`unreadCount` per group. Worse, `buildNotificationGroups` (`NotificationsViewModel.kt:212-215`) normalizes
unknown or mixed-case types to `val t = notif.type.lowercase(Locale.ROOT); if (t in NotificationsRepository.KNOWN_TYPES) t else "notification"`,
whereas `isGroupDefaultExpanded` compares raw `it.type == type`, so toggling the default-expanded
`"notification"` fallback group fails to recognize that it is currently expanded. Reusing
`groupedNotifications.value` first and sharing the group-key normalization helper eliminates the linear
rescan and fixes fallback group toggling while advancing roadmap 10.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` (`NotificationsViewModel.toggleGroupExpansion`, `NotificationsViewModel.isGroupDefaultExpanded`, and `NotificationsViewModel.buildNotificationGroups`, lines 99-115 and 206-227)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt` (`NotificationsViewModelTest`)
- leave alone: `NotificationsViewModel.markSelectedAsRead`, `NotificationsViewModel.formatNotification`, `NotificationsFragment`, and `NotificationsRepository`
steps:
1. Open `NotificationsViewModel.kt` and extract a private helper `private fun groupTypeOf(rawType: String): String { val t = rawType.lowercase(Locale.ROOT); return if (t in NotificationsRepository.KNOWN_TYPES) t else "notification" }`.
2. Update `buildNotificationGroups` (lines 212-215) to group notifications by `groupTypeOf(notif.type)`.
3. Update `isGroupDefaultExpanded(type: String, notifications: List<Notification>): Boolean` (lines 206-208) to first check `groupedNotifications.value.firstOrNull { it.type == type }?.let { return it.unreadCount > 0 }` and otherwise fall back to `notifications.any { groupTypeOf(it.type) == type && !it.isRead }`.
4. In `NotificationsViewModelTest.kt`, add a test verifying that an unread notification with an unknown type (such as `type = "unknown_type"`) renders expanded under the `"notification"` header by default and collapses on the first `viewModel.toggleGroupExpansion("notification")` call.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; tapping any notification group header
(including the `"notification"` fallback group) expands and collapses the section on the first tap.
size budget: ~30 changed lines, 2 files
out of scope: do not change `NotificationsFragment`, `NotificationsRepository`, or notification text formatting methods.

---
<a id="m033"></a>

### M033. UserRepositoryImpl.sortUsers: compute lowercase keys once per sort (decorate-sort)

**score 62** · O 55 / D 64 / C 75 · kept by 3/3 plans · raw: L02.09  
**refs:** Opus #24 · Devin R082 · Codex T058  
**shares files with:** M027, M031

**guardrails:**
- Codex scope: Normalize each selected string field once per user before stable sorting. Preserve the existing invariant-locale lowercase policy, empty/null treatment, boolean/numeric branches, descending behavior, and unsupported-field behavior.

**verified against tree:** Verified at :340-357. The only caller is HealthViewModel.

**canonical version — L02-T9 (Copilot·Grok), "lowercase user sort keys once per sort (roadmap 7+9)":**

context: UserRepositoryImpl.getUsersSortedBy (lines 170-172) loads every user through getAllUsers and then calls sortUsers.
sortUsers (lines 339-360) defines value() as value.orEmpty().lowercase() and calls it from inside the comparator for name, firstName, lastName, middleName, email, planetCode, parentCode, level, language, gender, dob, age, and birthPlace.
A name sort of the health or member list therefore allocates a lowercased string on every comparison.
joinDate, isUpdated, and isArchived do not allocate that way.
sortUsers itself makes no android.* call. Keep it that way so the function can move with the user repository into the KMP core (roadmap 9). Do not try to strip the file's other android imports in this task.
files: app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt, function sortUsers only.
Do not edit UserDao, HealthViewModel, or MembersAdapter.
steps:
1. For each string-field branch, compute value.orEmpty().lowercase() once per user, then sort the decorated list.
2. Use the same default-locale lowercase() the comparator uses today. Do not switch to Locale.ROOT. That would change sort order for some locales.
3. Leave the joinDate, isUpdated, and isArchived branches as direct field sorts.
4. The else branch must still return the input list in its existing order.
5. Ascending and descending must match today's sortedBy and sortedByDescending, including stability when two keys are equal.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open a user list sorted by name ascending, then descending, then by join date. Order matches the pre-change list, including mixed-case names.
size budget: ~35 changed lines, 1 file
out of scope: no SQL ORDER BY, and no change to which users are loaded.

---
<a id="m034"></a>

### M034. LoginSyncManagerTest: class-level static mocks, per-test stubs + clearMocks

**score 62** · O 38 / D 72 / C 80 · kept by 3/3 plans · raw: L13.06, L15.04, L18.05  
**refs:** Opus #64 · Devin R025, R061 · Codex T036, T086, T043

**guardrails:**
- Codex scope: Remove unused Android Log/Base64 instrumentation after checking indirect call paths; UrlUtils already uses java.util.Base64. Keep any needed logging assertion instrumentation.
- Codex scope: Install necessary UrlUtils/AndroidDecrypter object instrumentation at class scope and reset answers, recorded calls, repositories, and coroutine state per test; use targeted teardown.

**verified against tree:** Verified. The stubs must stay per-test, because :258 re-stubs AndroidDecrypter to false. L13-T6 and L18-T5 leak them as written.

**contributor notes:** L13-T6: leaks re-stubs · L18-T5: LoginSyncManagerTest part; unmockkAll teardown breaks it

**canonical version — L15-T4 (Copilot·Kimi), "hoist per-test static mocks in LoginSyncManagerTest to class-level setup (roadmap 8)":**

context:
LoginSyncManagerTest cost 24.2s for 19 tests (1.18s median) on master shard 1/2.
Its @Before (app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt:54-76) re-instruments four statics per test —
`mockkStatic(Log::class)` (line 56), `mockkStatic(Base64::class)` (line 60), `mockkObject(UrlUtils)` (line 65), `mockkObject(AndroidDecrypter.Companion)` (line 69) —
and @After (lines 83-86) calls `unmockkAll()`. This is the same churn pattern as task 3, in a different file.
files:
app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt only.
Do NOT touch services/sync/LoginSyncManager.kt (open PRs own it).
steps:
1. hoist the four static registrations into a `@BeforeClass @JvmStatic` companion function
   and replace `unmockkAll()` with `@AfterClass @JvmStatic` unmocks of exactly those four.
2. keep the per-test `every { ... }` stubbing in @Before, adding `clearMocks(...)` for the hoisted objects so stubs do not leak between tests.
3. run the class and compare summed time against the 24.2s baseline.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*LoginSyncManagerTest*"` green with all 19 tests still running;
class total time in the JUnit XML measurably below 24.2s;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~40 changed lines, 1 file.
out of scope: no production code changes, no changes to other sync tests (SyncManagerTest.kt is owned by an open PR).

---
<a id="m035"></a>

### M035. Move getSyncedUsers/getSyncedUserByName/getPendingSyncUsers to UserSyncRepository; rewire UploadToShelfService

**score 61** · O 48 / D 71 / C 71 · kept by 3/3 plans · raw: L07.01  
**refs:** Opus #33 · Devin R050 · Codex T087  
**shares files with:** M029

**guardrails:**
- Codex scope: Move getSyncedUsers, getSyncedUserByName, and getPendingSyncUsers declarations to UserSyncRepository and rewire the three shelf-upload calls. Keep the remaining getUserByName dependency until a contract that preserves its semantics is available; update service mocks.
- L07.01 correction from Codex: The service still calls the wide getUserByName API at line 58, as the steps acknowledge. Moving the three sync reads does not remove both user contracts.

**verified against tree:** Premise verified. It misses UploadToShelfServiceTest (about 15 stubs to move) and misstates the hierarchy (UserRepository does not extend UserSyncRepository).

**canonical version — L07-T1 (Devin), "Re-scope sync-only user reads onto UserSyncRepository and rewire UploadToShelfService (roadmap 1, 4, 9)":**

context: `UserRepository` is the last god-interface left (~70 suspend functions). Three of them are consumed only by shelf-upload sync: `getSyncedUsers` (UserRepository.kt:32), `getSyncedUserByName` (UserRepository.kt:34) and `getPendingSyncUsers` (UserRepository.kt:38) are all called exclusively from `services/UploadToShelfService.kt:35,102,125` — the same service that already injects `UserSyncRepository` for `checkAndUploadUser`. Moving them makes `UserRepository` strictly app-facing and lets the upload path hold one user contract instead of two.

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt` — delete the three declarations at lines 32, 34, 38.
- `app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt` — add the three declarations. Neighbor caveat: open draft PR #15808 also edits this file; append the new members, do not reorder existing ones.
- `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt` — rewire the call sites at lines 35, 102, 125 (and `getUserByName` at line 58 stays on `userRepository` — leave it).

steps:
1. Delete `getSyncedUsers()`, `getSyncedUserByName(name)`, `getPendingSyncUsers(limit)` declarations from `UserRepository`.
2. Add the same three signatures to `UserSyncRepository` (they are sync-facing reads; `UserRepositoryImpl` already implements `UserSyncRepository`, so no impl or DI change is needed — `repository/UserRepositoryImpl.kt:86`, `di/RepositoryModule.kt` binds `UserSyncRepository` to the same impl).
3. In `UploadToShelfService` change `userRepository.getPendingSyncUsers(100)` → `userSyncRepository.getPendingSyncUsers(100)`, `userRepository.getSyncedUsers()` → `userSyncRepository.getSyncedUsers()`, `userRepository.getSyncedUserByName(...)` → `userSyncRepository.getSyncedUserByName(...)`.
4. Keep `userRepository.getUserByName` at line 58 on `userRepository` (it is app-facing, not sync-facing); leave the field order and imports consistent with the file's existing style.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the "upload to shelf" sync path is unchanged (it calls the same impl); `UserRepository` no longer exposes sync-only reads.

size budget: ~12 changed lines, 3 files.

out of scope: `getUsersForHealthSync` (consumed by `TransactionSyncManager`, owned by the open KMP draft PRs); any UserRepositoryImpl edits.

---
<a id="m036"></a>

### M036. LifeRepositoryImpl.updateMyLifeListOrder: derive the owner from the caller, not the first item

**score 61** · O 45 / D 48 / C 94 · kept by 3/3 plans · raw: L10.01  
**refs:** Opus #35 · Devin R120 · Codex T002

**guardrails:**
- Codex scope: Normalize the supplied caller owner, select only stored rows belonging to that owner, and update/cache only that scope. Do not derive ownership from UI-supplied first-row data. Preserve duplicate-ID handling and unscoped NULL/empty/-- ownership semantics.

**verified against tree:** Premise verified (:39, :49-61). The proposed reject rule breaks LifeRepositoryImplTest :188-232: a null or blank stored owner must count as the caller's.

**canonical version — L10-T1 (Codex), "Enforce caller ownership when reordering My Life items (roadmap 1+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:37–50` chooses the cache owner from the first UI-supplied item and fetches rows solely by ID. It then updates every matching row without validating its owner, so a stale or mixed-user list can modify another learner's ordering and refresh the wrong cache.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`: `LifeRepositoryImpl.updateMyLifeListOrder`, reusing `normalizeUserId`.
- Edit `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryTest.kt`: `LifeRepositoryTest` reorder cases and cache mock setup.
- Leave the DAO, cache implementation, visibility operations, seeding, and UI callers alone.

steps:
1. Use the normalized method argument as the authoritative owner; remove the first item's ownership override.
2. Before loading rows, reject the entire operation as a no-op if any submitted item's normalized owner differs from that owner. Preserve the existing empty-list and empty-ID guards.
3. After loading rows, reject the entire operation before mutating any object if any returned row belongs to a different normalized owner. Preserve the current ID-to-index and unchanged-weight behavior for valid rows.
4. Reload and write only the authoritative owner's cache after a valid reorder; invalid input must produce neither DAO updates nor cache writes.
5. Add compact cases for a valid reorder, a foreign first item, a mixed-owner stored result, and equivalent null/blank/`--` ownership. Assert unchanged foreign weights and absent cache writes for rejected cases.

acceptance:
- Run `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.LifeRepositoryTest'`.
- Run `./gradlew testDefaultDebugUnitTest`.
- Verify a learner can reorder their own My Life tiles and retain the order after reopening; a list left over from another learner changes neither account.
- This places the ownership rule in repository logic using Kotlin comparisons, supporting roadmap 9 without a platform migration.

size budget: approximately 95–135 changed lines across 2 files, including tests; stay below 150.

out of scope: Do not change visibility matching, database schema, session resolution, or the cache format.

---
<a id="m037"></a>

### M037. automerge.sh: one gh pr view for state/reviewDecision/mergeable per PR

**score 61** · O 37 / D 77 / C 83 · kept by 3/3 plans · raw: L13.05, L18.03  
**refs:** Opus #94 · Devin R014 · Codex T028  
**shares files with:** M059

**guardrails:**
- Codex scope: Fetch state, reviewDecision, and initial mergeable together for a selected PR. Reuse that snapshot for the adjacent checks, preserving unknown-mergeability polling and refreshing metadata after head/base changes.
- L18.03 part dropped by Opus: Step 3 (skip the second publish_failed): the release run can complete between :470 and :605.
- L18.03 correction from Codex: Dropped indefinite publish_failed memoization by base SHA: workflow completion, annotations, and Play Store state can change while the SHA stays fixed. A cache needs a decision-pass lifetime or explicit state/version invalidation.

**verified against tree:** Verified at :104-110/:502-518. Keep the mergeable poll and the `|| echo ''` soft-fail. Do NOT skip the second publish_failed (L18-T3 step 3): the release can finish in between.

**contributor notes:** L18-T3: batching part only

**canonical version — L13-T5 (Devin), "Collapse three `gh pr view` calls into one in `automerge.sh` (roadmap 8 — workflow quick win)":**

**context:** `pr_state`, `pr_review`, and `check_mergeable` in `.github/scripts/automerge.sh` (`:104-112`) each run a separate `gh pr view … --json` call, and `check_mergeable` alone polls `mergeable` up to 5 times. On every drain iteration the drainer makes 3+ GraphQL round-trips where one suffices — a queue of 35 PRs burns 100+ extra API calls. Evidence: three helpers each shelling out to `gh pr view` for fields (`state`, `reviewDecision`, `mergeable`) that `gh pr view --json state,reviewDecision,mergeable` returns in one response; call sites at `:174`, `:502`, `:509`, `:518`.

**files:** `.github/scripts/automerge.sh` — the helpers `pr_state`, `pr_review`, `check_mergeable` and their call sites. Leave alone: `automerge.yml`, `coauthors.sh`, `labels.sh`, all polling sleep timings and retry loops.

**steps:**
1. Add a `pr_meta` helper that runs `gh pr view "$pr" --json state,reviewDecision,mergeable` once and emits the three fields on separate lines (or a single parseable line).
2. Rewrite `pr_state` / `pr_review` / `check_mergeable` call sites in the drain loop to read from one `pr_meta` invocation per iteration instead of three `gh pr view` calls.
3. Keep `check_mergeable`'s existing poll loop, but have each poll iteration reuse the single-field query only when `pr_meta` data is stale — preserve the same retry count and sleep duration.
4. Re-verify every field name against `gh pr view --json` output (`mergeable` vs `mergeStateStatus` — use whichever the script already consumes).

**acceptance:** `bash -n .github/scripts/automerge.sh` parses; `shellcheck` (if present) reports no new warnings; a manual `gh pr view` test of the combined `--json` fetch returns all three fields. User-visible: fewer API calls per drain cycle, identical merge decisions.

**size budget:** ~25 changed lines, 1 file.

**out of scope:** no timing/threshold changes, no new GraphQL fields, no changes to `automerge.yml`, no behavior change in merge order.

---
<a id="m038"></a>

### M038. FileUtils.getDisplayName / resolveUriToPath: DISPLAY_NAME projection instead of null

**score 60** · O 39 / D 64 / C 80 · kept by 3/3 plans · raw: L04.06, L14.03, L16.08  
**refs:** Opus #39 · Devin R060 · Codex T038

**guardrails:**
- Codex scope: Pass a DISPLAY_NAME-only projection to both content-provider queries. Preserve missing-column, null-cursor, URI, fallback-name, and traversal-sanitization behavior; update strict projection expectations in tests.

**verified against tree:** Verified at :217/:243. This is hygiene, not a measurable performance change.

**canonical version — L04-T6 (Codex), "Request only display-name metadata from content providers (roadmap 7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt:217` and line 243 pass a null projection to content-provider queries although both consumers only read the display-name column. This requests every available column while preparing selected attachments, so narrowing the projection avoids unnecessary provider work and cursor data without changing the streamed file copy.

files:

- `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` — `FileUtils.getDisplayName` and the metadata query in `resolveUriToPath`; leave path validation, `copyUriToFile`, storage statistics, and `FileExistenceCache` alone.
- `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt` — `FileUtilsTest` and its existing anonymous content-provider fixtures.

steps:

1. Set both metadata-query projections to the single existing display-name column. Preserve all other query arguments and cursor-closing behavior.
2. Keep existing missing-column, empty-cursor, last-path-segment, generated-name, file-URI, and null-URI behavior, including filename sanitization before copying.
3. Extend the existing provider fixture to assert the exact requested projection for `resolveUriToPath`, while retaining the copied-content and traversal-name assertions.
4. Exercise `getDisplayName` with a recording provider and a missing-column or empty-cursor result; verify the projection and the existing fallback result without adding a fallback full-column query.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.FileUtilsTest'`
- `./gradlew testDefaultDebugUnitTest`
- Select a named attachment from a document provider: its displayed name and copied bytes are unchanged. Providers without a display name still produce the existing fallback filename.

size budget: approximately 55–95 changed lines, 2 files.

out of scope: Do not refactor URI helpers, alter exception handling, change permissions, or load attachment bytes into an additional buffer.

---
<a id="m039"></a>

### M039. StepExamTest: delete the dead TextUtils mock; scope NetworkUtils to testSerializeExam

**score 59** · O 39 / D 64 / C 79 · kept by 3/3 plans · raw: L14.10, L18.05  
**refs:** Opus #41 · Devin R061 · Codex T043, T086, T036

**guardrails:**
- Codex scope: Remove the unused TextUtils static mock and stub from StepExamTest.
- Codex scope: Install the required NetworkUtils instrumentation at class scope with per-test resets, or scope it only to serialization cases; preserve the exact existing device-ID answer and targeted cleanup.

**verified against tree:** Verified: neither StepExam nor ExamQuestion uses TextUtils.

**contributor notes:** L18-T5: StepExamTest part (adds the NetworkUtils scoping) · L16-T5: rejected: hoists the dead mock instead of deleting it

**canonical version — L14-T10 (Copilot·Grok), "drop the unused TextUtils static mock from StepExamTest (roadmap 7+8)":**

context: `StepExamTest.setup` (`app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt:21-28`) calls `mockkStatic(TextUtils::class)` and stubs `TextUtils.isEmpty` before every test, and `tearDown` calls `unmockkAll()`. `StepExam.kt` has no `TextUtils` import and no `isEmpty` call; its only Android-adjacent call on this path is `addDocumentOrigin()` at `StepExam.kt:108`, which defaults to `NetworkUtils.getUniqueIdentifier()`. On master test run 42782082676 shard 2 this class took 6.6s excluding 0.1s of first-test excess, across 12 tests that are otherwise plain JUnit. Paying for an `android.text` static mock that production no longer uses is a shard tax. Removing it does not make `StepExam` platform-free — that file is owned by an open PR — so this is roadmap 7+8 only.

files: `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt` only. Do not edit `app/src/main/java/org/ole/planet/myplanet/model/StepExam.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/DocumentOrigin.kt`, or `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`.

steps:
1. Delete the `TextUtils` import, the `mockkStatic(TextUtils::class)` call, and the `every { TextUtils.isEmpty(any()) }` stub.
2. Keep `mockkObject(NetworkUtils)` and `every { NetworkUtils.getUniqueIdentifier() } returns "uniqueIdentifier"`. `insertCourseStepsExams` calls `addDocumentOrigin()` with no argument.
3. Keep `unmockkAll()` in `tearDown` so the `NetworkUtils` object mock does not leak into the next class on the shard.
4. Do not add Robolectric, and do not stub `TextUtils` from another test helper.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including `testInsertCourseStepsExams` and `testInsertCourseStepsExams_withParentId`. Those tests still assert the same ids, names, and `isFromNation` flags. The class no longer imports `android.text.TextUtils`.

size budget: ~8 changed lines, 1 file.

out of scope: do not remove the `NetworkUtils` mock, do not change how exams are parsed, do not touch the Room entity.

---
<a id="m040"></a>

### M040. CoursesStepsViewModel.loadStep: run the markdown rewrite and offline filter on io

**score 58** · O 38 / D 71 / C 79 · kept by 3/3 plans · raw: L14.05  
**refs:** Opus #73 · Devin R033 · Codex T042  
**shares files with:** M019, M025, M071

**guardrails:**
- Codex scope: Resolve paths on IO, then rewrite image markdown and prepare resource subsets on the compute dispatcher before publishing active-step state. Preserve structured cancellation and existing download predicates.

**verified against tree:** Verified at :76-82. It saves microseconds, because prependBaseUrlToImages short-circuits without '!['.

**canonical version — L14-T5 (Copilot·Grok), "rewrite course-step markdown off the main dispatcher (roadmap 7+10)":**

context: `CoursesStepsViewModel.loadStep` hops to `dispatcherProvider.io` only to resolve the files dir (`CoursesStepsViewModel.kt:72-74`), then runs `MarkdownUtils.prependBaseUrlToImages` (line 76) and `data.resources.filter { !it.isResourceOffline() }` (line 82) back on the view-model dispatcher, which is Main. `prependBaseUrlToImages` scans the whole step body for `![…](…)`. The next-step prefetch (line 124) also filters `getAllStepResources` on that same dispatcher. Room already suspends inside the repositories; this task only moves the CPU that resumes on Main. Roadmap 10: the screen state is already a `StateFlow`. A later Compose step screen can collect it without inheriting a main-thread rewrite. Do not add Compose here.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` (`loadStep`). Leave `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt` alone unless a test hangs, in which case only add the `advanceUntilIdle()` that file already uses. Do not edit `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseStepFragment.kt`, or the courses and resources repositories (open PRs own those repositories).

steps:
1. Build the markdown string and the `notDownloaded` list inside `withContext(dispatcherProvider.io)`, using the base dir already resolved there. Keep the `file://${baseDirPath}/ole/` prefix and the 600 by 350 arguments.
2. Assign `_uiState.value` only after that `withContext` returns, with the same `CourseStepUiState` fields as today, including `isLoading = false`.
3. Inside the existing `if (nextStepId != null)` launch, call `getAllStepResources` and filter `isResourceOffline()` on `dispatcherProvider.io` before `UrlUtils.getUrl`. Keep `startBackgroundDownload` and the empty-list skip.
4. Do not cancel or merge `loadDataJob` differently. The download launch at line 111 stays as it is.

acceptance: `./gradlew testDefaultDebugUnitTest` green, including `CoursesStepsViewModelTest.loadStep_whenResolverReturnsFile_buildsMarkdownDescriptionWithLocalOlePath`. Opening a step still shows the same local image paths and still prefetches the next step's missing resources. The step title and exam flags do not flicker to empty while the rewrite runs.

size budget: ~25 changed lines, 1 file.

out of scope: no new repository method, no change to image width/height, no Compose screen.

---
<a id="m041"></a>

### M041. ResourcesViewModel.loadResources: rethrow cancellation, tagged log instead of printStackTrace

**score 57** · O 38 / D 64 / C 89 · kept by 3/3 plans · raw: L13.01, L15.07, L17.03  
**refs:** Opus #65 · Devin R077, R071, R004 · Codex T015, T014, T037, T001, T008  
**shares files with:** M011, M019, M023, M027, M047, M095

**guardrails:**
- Codex scope: Rethrow cancellation from loadResources before ordinary-error fallback.
- Codex scope: Replace the loadResources stack dump with tagged reporting while preserving ordinary failure behavior.
- L13.01 correction from Codex: Rejected adding android.util.Log to RetryUtils; structured reporting must preserve its existing platform-independent imports. stderr is not literally invisible to logcat.
- L17.03 part dropped by Opus: step 3 (drop the IO wrapper in loadResources) would move whole-library mapping to Main.

**verified against tree:** Verified at ResourcesViewModel.kt:107-110. Keep the fetch-and-map inside withContext. L17-T3's 'no IO wrapper needed' is wrong, because the repository maps the whole library on the caller's thread.

**contributor notes:** L15-T7: Resources half · L17-T3: cancel part · L13-T1: printStackTrace slice

**canonical version — L15-T7 (Copilot·Kimi), "replace printStackTrace with tagged logging and rethrow cancellation in two viewmodels (roadmap 8)":**

context:
two ViewModels still report failures via `e.printStackTrace()`, which bypasses logcat tagging and swallows `CancellationException`.
HealthExaminationViewModel is internally inconsistent: app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:120 prints the stack trace
while the same file's `encryptSign` (line 136) already does it right with `catch (e: CancellationException) { throw e }` plus `Log.w(TAG, ...)`,
and a `TAG` companion already exists at the bottom of the file.
ResourcesViewModel.loadResources (app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:109) catches `Exception`
inside `withContext(dispatcherProvider.io)`, consuming cancellation of the load job it manages via `loadJob?.cancel()`.
files:
app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt (line 120, inside `saveExamination`).
app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt (line 109, inside `loadResources`).
Do NOT touch HealthExamination.kt or other health/resources classes (open PRs own several).
steps:
1. in HealthExaminationViewModel.saveExamination, add `catch (e: CancellationException) { throw e }` before the existing catch
   and replace the printStackTrace with `Log.e(TAG, "Saving examination failed", e)` (reuse the existing TAG; add the `kotlinx.coroutines.CancellationException` import).
2. in ResourcesViewModel.loadResources, add the CancellationException rethrow before the existing catch
   and replace printStackTrace with `Log.e(TAG, "Loading resources failed", e)`, adding a `private const val TAG = "ResourcesViewModel"` companion.
3. run the health and resources viewmodel tests.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*HealthExamination*" --tests "*ResourcesViewModel*"` green;
`grep -rn "printStackTrace" app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` returns nothing;
`./gradlew testDefaultDebugUnitTest` green.
size budget: ~14 changed lines, 2 files.
out of scope: no behavior change to success paths, no retry logic, no new UiState fields.

---
<a id="m042"></a>

### M042. ChatViewModel resolves the current user via getUserModel; ChatHistoryFragment stops reading prefs

**score 56** · O 39 / D 71 / C 71 · kept by 3/3 plans · raw: L11.10, L12.03  
**refs:** Opus #36 · Devin R029 · Codex T081  
**shares files with:** M060, M090

**guardrails:**
- Codex scope: Remove ChatHistoryFragment preference-based user-ID orchestration and let ChatViewModel resolve the current user through getUserModel. Preserve explicit-user behavior where retained, account switching, null user handling, and refresh cancellation.

**verified against tree:** Verified. L11-T10 is cleaner because it removes the parameter. L12-T3 keeps a dual path.

**canonical version — L11-T10 (ClaudeCode), "Let ChatViewModel resolve the current chat user instead of ChatHistoryFragment reading prefs (roadmap 1+3, moves 9)":**

context: `ui/chat/ChatHistoryFragment.kt:135-138` (`refreshChatHistory`) reads the user id straight from
`sharedPrefManager.getUserId()` and passes it to `ChatViewModel.loadChatHistoryScreenData(userId)` (`ChatViewModel.kt:134`).
The ViewModel then does `userRepository.getUserById(userId)` (:187-191). That is exactly what
`UserReadRepository.getUserModel()` does (`UserRepositoryImpl.getUserModel` reads the same pref and
calls `userDao.getById`), so the session-identity lookup has leaked into the Fragment. The Fragment is
the only production caller of `loadChatHistoryScreenData`.
files: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (`loadChatHistoryScreenData`, `loadCurrentUser`),
`app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt` (`refreshChatHistory`),
`app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`.
Leave alone: `fetchAiProviders(serverUrl, …)` and its signature (`ChatDetailFragment.kt:444`, owned by an
open PR, calls it), the `serverUrl` getter at `ChatHistoryFragment.kt:47-48` (so `sharedPrefManager` stays
injected), and `ChatViewModel.getUserById` (:296).
steps:
1. Change `loadChatHistoryScreenData(userId: String?)` to `loadChatHistoryScreenData()`. Replace
   `loadCurrentUser(userId)` with `userRepository.getUserModel()`, keeping the `cachedUser ?: … .also { cachedUser = it }` caching.
2. Delete the private `loadCurrentUser` helper.
3. In `refreshChatHistory()` call `sharedViewModel.loadChatHistoryScreenData()`.
4. In the test, replace `coEvery { userRepository.getUserById("user123") } returns …` stubs used by screen-data
   tests (:126, :232, :342, :562, :596) with `coEvery { userRepository.getUserModel() } returns …`. Drop the
   `"user123"`/`userId =` argument at every `loadChatHistoryScreenData` call. Change `coVerify` at :261 and :378 to `getUserModel`.
   Leave the proxy test at :507-515 untouched.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ChatViewModelTest"` passes, then the full suite
stays green. On device: open Chat history while logged in and the list, search and "share to team/enterprise"
targets appear as before. After logging out and in as another user, that user's history appears.
size budget: ~10 changed lines in main, ~30 in the test, 3 files.
out of scope: do not remove `SharedPrefManager` from the Fragment, do not touch `ChatDetailFragment`, and no repository changes.

---
<a id="m043"></a>

### M043. ResourcesListFilter.matching: apply tag/facet/download filters before the ranked title search

**score 56** · O 39 / D 61 / C 81 · kept by 3/3 plans · raw: L04.05  
**refs:** Opus #54 · Devin R090 · Codex T034

**guardrails:**
- Codex scope: Filter by tag, facets, and download state before ranked title search. Preserve prefix/contains priority and stable order among surviving results for apply and countMatching.

**verified against tree:** Verified at :64/:85-88. The search is a stable partition, so reordering preserves the output.

**canonical version — L04-T5 (Codex), "Apply selective resource filters before ranked title search (roadmap 7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:64` runs ranked title search over every model before applying tags, facets, or download status at lines 85–88. Selective offline/tag filters therefore still search titles and build ranked result buckets for rows they immediately discard; moving those pure predicates earlier reduces that work while retaining the Kotlin-only filter boundary useful to roadmap 9.

files:

- `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt` — `ResourcesListFilter.matching`; leave `filterIfChanged`, `apply`, signature handling, and the definitions of `matchesFacets` and `matchesMedium` alone.
- `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilterTest.kt` — `ResourcesListFilterTest`; extend combined-filter, ordering, and count parity coverage.

steps:

1. Preserve the existing one-time tag-ID and medium-criteria preparation, then apply download, tag, and facet predicates to candidates before invoking ranked title search.
2. For nonempty search queries, materialize only surviving candidates and pass them to the existing search helper. For an empty query, return the candidate sequence directly without an extra materialized search input.
3. Preserve prefix-before-contains ranking and original order within each rank, including duplicate input occurrences. Keep case handling and the exact empty-versus-whitespace query behavior.
4. Add regressions mixing offline exclusions, tags, facets, prefix hits, and contains hits; assert ordered identities as well as `countMatching` equality with the applied list size. Retain reset/signature and medium-alias coverage.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.resources.ResourcesListFilterTest'`
- `./gradlew testDefaultDebugUnitTest`
- Search a large library with a selective downloaded/tag filter: the displayed resources, prefix ranking, and filter count match previous behavior, while excluded candidates no longer enter title search.

size budget: approximately 55–100 changed lines, 2 files.

out of scope: Do not change search normalization, facet semantics, filter-cache invalidation, resource models, or repository queries.

---
<a id="m044"></a>

### M044. VoicesAdapter: use ImageViewerUtils.showZoomableImage instead of a private copy

**score 55** · O 55 / D 61 / C 72 · kept by 3/3 plans · raw: L05.10  
**refs:** Opus #25 · Devin R096 · Codex T071  
**shares files with:** M085

**guardrails:**
- Codex scope: Delegate VoicesAdapter zoomable images to ImageViewerUtils and remove the duplicate dialog implementation, preserving local/GIF/error handling.
- Codex scope: Resolve thumbnail dimensions and margins once per adapter display configuration and reuse them in the four image helpers.

**verified against tree:** Verified at :1168-1190. The value is the dedup plus the double-tap fix; the dimension hoist is negligible.

**canonical version — L05-T10 (ClaudeCode), "Use the shared zoomable-image viewer in VoicesAdapter and hoist its per-bind dimension lookups (roadmap 6+7+8, moves 10)":**

**context:**
- `main/ui/voices/VoicesAdapter.kt:1168-1190` has a private `showZoomableImage` that copies `utils/ImageViewerUtils.showZoomableImage` (`main/utils/ImageViewerUtils.kt:17-58`), except that the shared one:
  - dismisses an already-open dialog, so the copy can stack dialogs on a double tap;
  - handles `http(s)` URLs;
  - inflates through view binding.
- The Enterprises adapters and `MarkdownUtils` already use the shared one.
- `loadSingleImage`, `addImageToContainer`, `loadLibraryImage` and `addLibraryImageToContainer` (lines 1094-1164) call `getDimensionPixelSize(R.dimen.image_thumbnail_size_120/100)` and recompute the 4dp margin for every image on every bind of every news row.

**files:**
- touch:
  - `main/ui/voices/VoicesAdapter.kt`: the four image helpers at lines 1094-1164, the private `showZoomableImage` at 1168-1190, and imports at lines 4, 19, 20, 26 and 44 (only if they become unused)
  - `test/ui/voices/VoicesAdapterImagesTest.kt`
- leave alone:
  - `ImageViewerUtils.kt` (reuse it unchanged)
  - `VoicesRepositoryImpl.kt` (task 6)
  - `TeamsVoicesFragment.kt` (task 8)
  - `loadGlideImage` / `isGif`

**steps:**
1. Delete the private `showZoomableImage` and route its four call sites to `ImageViewerUtils.showZoomableImage(context, path)`.
2. Add three adapter-level `private val … by lazy` values read from `context.resources`: `thumbnailSize120`, `thumbnailSize100` and `thumbnailMarginPx` (`(4 * displayMetrics.density).toInt()`). Use them in the four helpers instead of the per-call lookups.
3. Remove only the imports that become unused: `Dialog`, `PhotoView`, `toDrawable`, `ContextCompat`, `EdgeToEdgeUtils`. Grep the file first, because `ContextCompat` and `EdgeToEdgeUtils` may still be used elsewhere in it.
4. Extend `VoicesAdapterImagesTest` so a bound row with two images still adds two thumbnails of the expected size to `llNewsImages`.

**acceptance:**
- `./gradlew testDefaultDebugUnitTest` is green.
- `python3 .agents/skills/kotlin-importing/kotlin-importing.py --check app/src` reports nothing for `VoicesAdapter.kt` (only if the submodule is initialised).
- Manually, in community and team Voices:
  - tapping a post image opens the full-screen viewer;
  - double-tapping does not stack two viewers;
  - close returns to the feed;
  - thumbnails keep their size.

**size budget:** ~50 changed lines (net negative), 2 files

**out of scope:**
- Do not touch the emoji/label popup dimension lookups (lines 839-964).
- Do not change Glide options.

---
<a id="m045"></a>

### M045. NotificationsRepositoryImpl: one private helper for getTaskTeamNamesByTaskIds/ByTaskTitles

**score 55** · O 53 / D 71 / C 64 · kept by 3/3 plans · raw: L01.10  
**refs:** Opus #27 · Devin R040 · Codex T112

**guardrails:**
- Codex scope: Extract the common team-ID collection and name-map construction from the ID- and title-keyed notification helpers. Preserve empty-input returns, ordering, and filtering.

**verified against tree:** Verified: :255-276 and :318-339 are near-identical. This is a maintainability dedup, not a performance change.

**canonical version — L01-T10 (Devin), "collapse the duplicated task→team-name batch helpers in `NotificationsRepositoryImpl` (roadmap 1+8)":**

context: `getTaskTeamNamesByTaskIds` (~lines 256-277) and `getTaskTeamNamesByTaskTitles` (~lines 312-338) are the same routine modulo the task fetch (`getTasksByIds` vs `getTasksByTitles`) and the map key (`task.id` vs `task.title`). Maintaining two copies invites drift on one of the most-touched repository files.

files: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` — the two private helpers and their call sites only. Do NOT touch `getJoinRequestDetailsBatch`, `teamsRepository` accessor, or notification DAO queries.

steps:
1. Extract a private `getTaskTeamNames(tasks: List<TeamTask>, keySelector: (TeamTask) -> String?)` (use the actual task model type used in the file) that does teamIds collection → `getTeamNamesByIds` → map build exactly once.
2. Re-implement `getTaskTeamNamesByTaskIds` as `getTaskTeamNames(teamsRepository.get().getTasksByIds(taskIds)) { it.id }`.
3. Re-implement `getTaskTeamNamesByTaskTitles` as `getTaskTeamNames(teamsRepository.get().getTasksByTitles(taskTitles)) { it.title }`.
4. Keep the empty-input early returns and `LinkedHashSet`/`teamId.isNotEmpty()` filters identical.

acceptance: `./gradlew testDefaultDebugUnitTest` green; notification titles still resolve to the right team names for both id- and title-keyed lookups.

size budget: ~30 changed lines (net negative), 1 file.

out of scope: no DAO changes, no notification-payload changes, no dedup beyond these two helpers.

---
<a id="m046"></a>

### M046. CommunityDao.getAllSorted: filter blank names in SQL, drop the dialog-side TextUtils filter

**score 55** · O 39 / D 69 / C 72 · kept by 3/3 plans · raw: L07.06, L11.05  
**refs:** Opus #37 · Devin R051 · Codex T073

**guardrails:**
- Codex scope: Move the exact TextUtils.isEmpty name predicate into CommunityDao.getAllSorted and remove the UI filter. Preserve weight ordering and whitespace-only names; TextUtils.isEmpty does not trim whitespace.

**verified against tree:** Verified. Use `name != ''` (name is non-null), not TRIM, for exact parity. L07-T6's claim that LoginViewModel also calls getAllSorted is false.

**canonical version — L11-T5 (ClaudeCode), "Move the blank-community filter from the server dialog into CommunityDao SQL (roadmap 1+7, moves 9)":**

context: `ui/sync/ServerDialogExtensions.kt:298-299` loads every community row through
`communityRepository.getAllSorted()` and then filters out blank names in UI code with
`android.text.TextUtils.isEmpty`. That filtering is data selection and belongs in SQL.
`CommunityDao.getAllSorted()` (`data/room/dao/CommunityDao.kt:12-13`) has exactly one consumer
chain (`CommunityRepositoryImpl.getAllSorted` → this dialog), so the predicate can move into the
query without changing any other screen. `Community.name` is a non-null `String` (`model/Community.kt:18`).
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CommunityDao.kt` (`getAllSorted`),
`app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt` (`setupManualConfigEnabled`),
new `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CommunityDaoTest.kt`.
Leave alone: `CommunityRepositoryImpl.kt` and `CommunityRepository.kt` (open PRs), `model/Community.kt`, and `AppDatabase.kt` (no schema change, so no version bump).
steps:
1. Change the `@Query` on `getAllSorted` to `SELECT * FROM community WHERE name != '' ORDER BY weight ASC`
   and add a one-line KDoc saying rows with an empty name are never returned.
2. In `setupManualConfigEnabled` delete the `nonEmptyCommunities` filter and pass `communities` straight to the `ArrayAdapter`.
   Keep the `android.text.TextUtils` import because :93 still uses `TextUtils.equals`.
3. Add `CommunityDaoTest` (Robolectric `@RunWith(AndroidJUnit4::class)`, in-memory `AppDatabase` exactly as
   `DictionaryDaoTest.kt` builds it). Insert three communities (weights 5/"b", 0/"learning", 1/""). Assert
   `getAllSorted()` returns `["learning", "b"]` in that order, and that `replaceAll` replaces the previous rows.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CommunityDaoTest"` passes. The full
`./gradlew testDefaultDebugUnitTest` stays green. On device: Settings → server dialog → enable
manual config, and the community spinner lists the same named communities in the same order, with no blank entry.
size budget: ~5 changed lines in main, ~60-line new test, 3 files.
out of scope: no new DAO method, no repository changes, no tie-break ordering change.

---
<a id="m047"></a>

### M047. ResourcesViewModel sort wrappers: default dispatcher, mutate sort fields on the caller

**score 55** · O 39 / D 61 / C 80 · kept by 3/3 plans · raw: L12.08, L17.03  
**refs:** Opus #40 · Devin R089, R077 · Codex T037, T015, T062  
**shares files with:** M019, M041

**guardrails:**
- Codex scope: Use the injected default dispatcher for the three pure sorting entry points.
- Codex scope: Capture sort settings on the owning thread, perform one compute hop through the synchronous helper, and publish results without racing sort-mode fields or nesting dispatcher hops.
- L17.03 part dropped by Opus: step 3 (drop the IO wrapper in loadResources) would move whole-library mapping to Main.

**verified against tree:** Verified at :177-195 (pure in-memory sorts on io). L17-T3's step 3 would move whole-library mapping to Main, so drop that step.

**canonical version — L12-T8 (Antigravity), "narrow ResourcesViewModel user dependency to UserReadRepository and move in-memory sorting to default dispatcher (roadmap 1+3+7)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:31` injects the full `UserRepository` interface (`ResourcesViewModel.kt:25`) even though `ResourcesViewModel` only calls `userRepository.getUserModel()` in `userDeferred` (`ResourcesViewModel.kt:39`), which is already provided by `UserReadRepository`.
In addition, `ResourcesViewModel.toggleSortOrder` (`ResourcesViewModel.kt:177`), `toggleTitleSortOrder` (`ResourcesViewModel.kt:185`), and `applyCurrentSort` (`ResourcesViewModel.kt:193`) switch to `dispatcherProvider.io` to perform pure in-memory list sorting in `applyCurrentSortSynchronous` (`ResourcesViewModel.kt:197-214`), whereas `getFilterFacets` (`ResourcesViewModel.kt:137`) properly uses `dispatcherProvider.default`.
Narrowing `userRepository` to `UserReadRepository` tightens the repository boundary (advancing roadmap 9), and moving the three sort functions onto `dispatcherProvider.default` keeps CPU-bound sorting off the I/O thread pool.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` (class `ResourcesViewModel`, import at line 25, constructor parameter at line 31, and sort functions at lines 177-195)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt` (class `ResourcesViewModelTest`, import at line 26 and mock field at line 35)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesSortFragment.kt` untouched; do NOT touch `ResourcesRepositoryImpl` or `ResourcesFragment` — open PRs own them
steps:
1. In `ResourcesViewModel.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 25) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change constructor parameter `private val userRepository: UserRepository` (line 31) to `private val userRepository: UserReadRepository`.
2. In `ResourcesViewModel.kt`, change `withContext(dispatcherProvider.io)` to `withContext(dispatcherProvider.default)` in `toggleSortOrder` (line 177), `toggleTitleSortOrder` (line 185), and `applyCurrentSort` (line 193).
3. In `ResourcesViewModelTest.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 26) with `import org.ole.planet.myplanet.repository.UserReadRepository`.
4. In `ResourcesViewModelTest.kt`, change `private val userRepository = mockk<UserRepository>(relaxed = true)` (line 35) to `private val userRepository = mockk<UserReadRepository>(relaxed = true)`.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.resources.ResourcesViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; sorting resources by date or title in the Library screen produces the same ascending/descending order on `dispatcherProvider.default`.
size budget: ~14 changed lines, 2 files
out of scope: Do not modify `ResourcesRepositoryImpl`, `ResourcesFragment`, or `ResourcesSortFragment.kt`.

---
<a id="m048"></a>

### M048. CoursesRepositoryImpl.getCourseDetailModel: async the five independent reads

**score 55** · O 38 / D 71 / C 77 · kept by 3/3 plans · raw: L02.03  
**refs:** Opus #71 · Devin R031 · Codex T051  
**shares files with:** M008, M075

**guardrails:**
- Codex scope: Use structured child coroutines for independent user, exam-count, resource, and step reads in getCourseDetailModel. Keep question-count work dependent on step IDs and rating work dependent on the user; retain flow cancellation and null-course handling.

**verified against tree:** Verified at :134-138. These are cheap local Room reads, so the saving is a few ms.

**canonical version — L02-T3 (Copilot·Grok), "parallelize independent course-detail reads (roadmap 1+7)":**

context: CoursesRepositoryImpl.getCourseDetailModel (lines 130-179) loads the course through a Flow, then inside the map runs five independent suspend reads one after another.
Those reads are getUserModel, getCourseExamCount, getCourseOnlineResources, getCourseOfflineResources, and getCourseSteps (lines 134-138).
Question counts need the step ids from getCourseSteps. The rating summary needs the user id.
Opening course detail therefore waits on the sum of those reads.
The repository already imports android.content.Context, so this task does not finish roadmap 9. The edited function must not add any new android.* use.
files: app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt, function getCourseDetailModel only.
Do not edit getCourseStepData, getMyCourses, TakeCourseViewModel.kt, or any DAO.
steps:
1. After the null-course return, wrap the rest of the map body in coroutineScope. The flow map lambda is already a suspend context.
2. Start the five independent reads with async and await them before building step ids.
3. After user and rawSteps are available, start question-count assembly and getRatingSummary together when userId is non-null. Rating still stays null when userId is null.
4. Build the same CourseDetailModel fields, in the same meaning: course, user, ratingSummary, examCount, resources, downloadedResources, steps.
5. If a child call throws, let await surface that failure. Do not catch and swallow it. Add only the coroutine imports this needs. Do not wrap the reads in a Room transaction.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open a course detail screen. Title, exam count, online resources, downloaded resources, steps, question counts, and rating match the pre-change screen, with no new stuck spinner.
size budget: ~30 changed lines, 1 file
out of scope: do not parallelize getCourseStepData, and do not change userId.contains matching in getMyCourses.

---
<a id="m049"></a>

### M049. Consolidate guest-login and authenticate orchestration into LoginViewModel

**score 54** · O 55 / D 60 / C 73 · kept by 3/3 plans · raw: L07.05  
**refs:** Opus #19 · Devin R098 · Codex T066  
**shares files with:** M077, M079

**guardrails:**
- Codex scope: Move the repeated guest-creation/session-save sequence behind LoginViewModel and return typed outcomes to the three UI entry points. Keep UI navigation, dialogs, and lifecycle ownership at the view boundary.
- Codex scope: Move authenticate/has-user/session-save orchestration out of SyncActivity into a reusable login-owned operation, preserving all AuthUtils callers, saved credentials, cancellation, and manager mode.

**verified against tree:** The three guest blocks exist but are not identical. authenticateUser belongs in LoginActivity, not the abstract SyncActivity. It needs a tri-state result, and LoginViewModelTest must change.

**canonical version — L07-T5 (Devin), "Consolidate guest-login and authenticate orchestration into LoginViewModel (roadmap 3, 9)":**

context: The identical `createGuestUser(...) → profileDbHandler.saveUserInfoPref("", model) → onLogin()` block is written out three times — `ui/sync/GuestLoginExtensions.kt:68-74`, `ui/sync/LoginActivity.kt:597-604` and `ui/sync/LoginActivity.kt:639-647`. Separately, `SyncActivity.authenticateUser` (SyncActivity.kt:390-407) does repository orchestration inline: `userRepository.hasAtLeastOneUser()` → `userRepository.authenticateUser(...)` → `profileDbHandler.saveUserInfoPref(password, user)` with a dialog + try/catch — data-layer work living in a base activity, reachable from `AuthUtils.login` (AuthUtils.kt:19,49). Both belong behind `LoginViewModel`.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt` — add `loginAsGuest(username)` (wrapping `createGuestUser` + `saveUserInfoPref` + returning the model for `onLogin`) and `authenticate(username, password, isManagerMode): Boolean` (the hasAtLeastOneUser → authenticateUser → saveUserInfoPref sequence, minus the dialog).
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt` — replace lines 68-74 with `viewModel.loginAsGuest(username)`.
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt` — replace both duplicated blocks (597-604, 639-647) with the same VM call; also update the `AuthUtils` call sites if signatures require it.
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` — turn `authenticateUser` into a thin delegate to `loginViewModel.authenticate(...)` (or expose the VM where it is defined); keep the `server_not_configured…` alert on the caller side.
- `app/src/main/java/org/ole/planet/myplanet/utils/AuthUtils.kt` — neighbor caveat: KMP draft PRs #18045/#18029/#17832 touch this file; only adjust the `authenticateUser` call sites, leave everything else alone.

steps:
1. In `LoginViewModel`, add `suspend fun loginAsGuest(username: String): UserEntity?` that calls `userRepository.createGuestUser(username)`, then `profileDbHandler.saveUserInfoPref("", model)` on success and returns the model (null on failure). `profileDbHandler` is `UserSessionManager` — inject it or reuse the field the VM already exposes.
2. Add `suspend fun authenticate(username: String?, password: String?, isManagerMode: Boolean): Boolean` implementing the try/catch + `hasAtLeastOneUser` + `authenticateUser` + `saveUserInfoPref(password, user)` flow from `SyncActivity.kt:390-407`; return `false` on any failure and let the caller keep showing the alert/toast.
3. Replace the three guest-login blocks with `viewModel.loginAsGuest(username)` followed by the existing `onLogin()` / `saveUsers(username, "", "guest")` calls.
4. Update `SyncActivity.authenticateUser` (or its `AuthUtils` call sites) to delegate to the VM; keep the dialog string in the activity layer.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; guest login from the login dialog and from the guest-login path still land on `onLogin()` with prefs saved; member login via `AuthUtils.login` still saves credentials and greets the user; the server-not-configured alert still fires when no users exist.

size budget: ~90 changed lines, 4-5 files.

out of scope: the `loginNetworkJob` reconnect-upload block in `SyncActivity` (~l640-660) — leave it; `LoginSyncManager` (owned by KMP drafts); any `MainApplication` changes (owned by open PR #18540).

---
<a id="m050"></a>

### M050. LifeViewModel.loadMyLifeList: seedMyLifeIfEmpty + observe instead of reading the list twice

**score 54** · O 39 / D 66 / C 72 · kept by 3/3 plans · raw: L11.04  
**refs:** Opus #49 · Devin R055 · Codex T072  
**shares files with:** M029

**guardrails:**
- Codex scope: Use seedMyLifeIfEmpty followed by the DAO-backed Flow instead of a full get-list followed by the same observed list. Preserve default seeding, user normalization, de-duplication, and empty-state ordering; account for the seed method own count/return reads.
- L11.04 correction from Codex: StateFlow suppresses equal values, and seedMyLifeIfEmpty itself reads/counts. Removed the claim of exactly two emissions or a guaranteed one-query total; validate the actual query-count reduction.

**verified against tree:** Verified at :38/:41. It removes one full SELECT per open.

**canonical version — L11-T4 (ClaudeCode), "Stop LifeViewModel reading the My Life list twice per open (roadmap 1+3+7)":**

context: `ui/life/LifeViewModel.kt:38` calls `lifeRepository.getMyLifeByUserId(userId, defaults)`,
which runs a full list query and seeds defaults when empty. :41 then subscribes to
`observeMyLifeByUserId(userId)`, which immediately runs the same query again and emits the same
list. So every open costs two identical DB reads and two StateFlow emissions, and the ViewModel
has to coordinate two sources of truth. The repository already exposes the seed step on its own:
`repository/LifeRepository.kt` `seedMyLifeIfEmpty(userId, items)`.
files: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt` (`loadMyLifeList`),
`app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`.
Leave alone: `repository/LifeRepositoryImpl.kt`, `repository/LifeRepository.kt`, `LifeCache.kt`
and `LifeRepositoryImplTest.kt` (open PRs touch the cache and its tests). Also leave
`updateVisibility` / `updateMyLifeListOrder` in the ViewModel.
steps:
1. In `loadMyLifeList`, replace the `getMyLifeByUserId(...)` call and the `_myLifeList.value = initialList`
   assignment with `lifeRepository.seedMyLifeIfEmpty(userId, LifeItemDefaults.forUser(userId, resolveLabel))`
   (ignore its return value). Keep the existing `observeMyLifeByUserId(userId).distinctUntilChanged().collect { … }` as the only writer of `_myLifeList`.
2. Keep `resolveUserId()` unchanged.
3. Update the test: replace `coEvery { lifeRepository.getMyLifeByUserId(...) }` stubs (lines ~54, 69, 103, 125, 146, 177, 191, 204)
   with `coEvery { lifeRepository.seedMyLifeIfEmpty(...) } returns emptyList()` plus an
   `observeMyLifeByUserId` flow stub where one is missing. Change the `coVerify` checks (lines ~60-61, 79-80, 93, 183, 197, 210)
   to assert `seedMyLifeIfEmpty` is called exactly once and `getMyLifeByUserId` never.
4. Keep the test that checks the captured defaults list (line ~69), now capturing from `seedMyLifeIfEmpty`.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*LifeViewModelTest"` passes, then the full
`./gradlew testDefaultDebugUnitTest` stays green. On device: a fresh user opening "myLife" sees the
seven default tiles. Toggling visibility and drag-reordering still persist after leaving and reopening the screen.
size budget: ~6 changed lines in the ViewModel, ~40 in the test, 2 files.
out of scope: do not remove `getMyLifeByUserId` from the interface (the off-limits impl test uses it), and do not touch `BaseDashboardFragment`.

---
<a id="m051"></a>

### M051. TakeCourseFragment: delete the unused UserSessionManager field injection

**score 54** · O 39 / D 64 / C 75 · kept by 3/3 plans · raw: L12.02  
**refs:** Opus #59 · Devin R079 · Codex T060  
**shares files with:** M011, M019, M021

**guardrails:**
- Codex scope: Inject UserReadRepository into TakeCourseViewModel.
- Codex scope: Inject UserReadRepository into CoursesStepsViewModel.
- Codex scope: Remove the never-read UserSessionManager injection and import from TakeCourseFragment.

**verified against tree:** Verified: it is injected at :42-43 and never read.

**canonical version — L12-T2 (Antigravity), "remove unused UserSessionManager injection from TakeCourseFragment and narrow TakeCourseViewModel to UserReadRepository (roadmap 1+3+4)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt:42-43` field-injects `@Inject lateinit var userSessionManager: UserSessionManager` (`TakeCourseFragment.kt:30`) even though `userSessionManager` is never read anywhere in the 533-line fragment.
Meanwhile, `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:42` injects the full 50-method `UserRepository` interface, leaking user authentication, security-key management, and sync-upload operations into the courses feature when `TakeCourseViewModel.loadCourse` (`TakeCourseViewModel.kt:117`) only calls `userRepository.getUserModel()` (unlike neighbor `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt:18`, which already injects `UserReadRepository`).
Removing the dead service injection from `TakeCourseFragment` and narrowing `TakeCourseViewModel` to `UserReadRepository` tightens the cross-feature boundary and advances roadmaps 9 and 10.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt` (class `TakeCourseFragment`, unused imports at lines 19, 30 and `@Inject lateinit var userSessionManager` at lines 42-43)
- touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` (class `TakeCourseViewModel`, import at line 18, constructor parameter at line 42, and `loadCourse` at line 117)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModelTest.kt` (class `TakeCourseViewModelTest`, import at line 26 and mock field at line 40)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt` untouched; do NOT touch `CoursesRepositoryImpl` or `ProgressRepositoryImpl` — open PRs own them
steps:
1. In `TakeCourseFragment.kt`, delete `@Inject` and `lateinit var userSessionManager: UserSessionManager` (lines 42-43).
2. In `TakeCourseFragment.kt`, remove the unused `javax.inject.Inject` (line 19) and `org.ole.planet.myplanet.services.UserSessionManager` (line 30) imports.
3. In `TakeCourseViewModel.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 18) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change constructor parameter `private val userRepository: UserRepository` (line 42) to `private val userRepository: UserReadRepository`.
4. In `TakeCourseViewModelTest.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 26) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change `private val userRepository: UserRepository = mockk()` (line 40) to `private val userRepository: UserReadRepository = mockk()`.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.courses.TakeCourseViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening a course in `TakeCourseFragment` still loads the active user, course steps, progress, and blocking-step index identically.
size budget: ~12 changed lines, 3 files
out of scope: Do not touch `CoursesRepositoryImpl`, `ProgressRepositoryImpl`, or `ProgressViewModel.kt`.

---
<a id="m052"></a>

### M052. News: raw-keyed parse cache for reactionsMap / pendingReactionsMap

**score 54** · O 38 / D 71 / C 76 · kept by 3/3 plans · raw: L02.07  
**refs:** Opus #81 · Devin R037 · Codex T055  
**shares files with:** M105

**guardrails:**
- Codex scope: Cache reaction JSON/maps and pending-reaction maps using their raw-string inputs. Return defensive copies or immutable snapshots, invalidate on mutation, and preserve malformed/null fallbacks.

**verified against tree:** Verified at :125-156. It only helps while the same instance is rebound.

**canonical version — L02-T7 (Copilot·Grok), "cache voice reaction JSON the way images are cached (roadmap 7)":**

context: News.imagesArray (lines 95-114) parses the images column once and stores an ImagesCache keyed by the raw string, then deepCopy on a hit.
reactionsJson (lines 125-130), reactionsMap (lines 133-144), and pendingReactionsMap (lines 147-156) parse on every get.
updateReaction (line 158) reads reactionsMap, setReaction reads reactionsMap again (line 173), and updateReaction then reads pendingReactionsMap (line 162).
One reaction tap parses the same JSON three times, and every voice row does it again on rebind.
News still imports android.util.Log, Room, and FileUtils, so this does not finish roadmap 9. The new cache fields must be plain Kotlin and must not add an android.* type.
files: app/src/main/java/org/ole/planet/myplanet/model/News.kt, the three reaction getters and the writers that assign reactions and pendingReactions.
Do not edit voices adapters, VoicesRepository, or imagesArray.
steps:
1. Add @Ignore cache holders, matching the imagesCache fields at lines 91-93, keyed by the raw reactions string and the raw pendingReactions string.
2. On get, return the cached parse when the raw string is unchanged.
3. reactionsJson returns a mutable JsonObject today. Return a deepCopy on a cache hit, as imagesArray does at line 101, so a caller cannot corrupt the cache.
4. reactionsMap may return the cached immutable map. setReaction already copies lists before it mutates them.
5. Assigning reactions or pendingReactions must make the next get miss, because the raw key changed. Do not remove the imagesArray deepCopy.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
On a voice post, add a reaction, remove it, and reopen the list. The same emoji counts show, pending reactions still apply, and images on the post are unchanged.
size budget: ~40 changed lines, 1 file
out of scope: do not change the reaction toggle rule, and do not persist the cache columns through Room.

---
<a id="m053"></a>

### M053. SettingsViewModel: UserReadRepository instead of the Context-backed UserSessionManager for isGuest

**score 53** · O 55 / D 64 / C 69 · kept by 3/3 plans · raw: L12.01  
**refs:** Opus #21 · Devin R078 · Codex T098

**guardrails:**
- Codex scope: Replace SettingsViewModel UserSessionManager injection with UserReadRepository and preserve its exact guest-ID predicate. Update constructor tests and retain the existing singleton Hilt binding.

**verified against tree:** Verified at :25/:41. UserSessionManager.getUserModel is a pass-through.

**canonical version — L12-T1 (Antigravity), "replace UserSessionManager dependency in SettingsViewModel with UserReadRepository (roadmap 1+3+4)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt:25` injects `UserSessionManager` (`SettingsViewModel.kt:16`), an Android service-layer class backed by `Context`, `SharedPrefManager`, `ActivitiesRepository`, and `TimeProvider`, solely to evaluate `userSessionManager.getUserModel()?.id?.startsWith("guest") == true` in `SettingsViewModel.isGuest()` (`SettingsViewModel.kt:41`).
`UserSessionManager.getUserModel()` is a pass-through to `userRepository.getUserModel()`, which is already exposed on the narrow `UserReadRepository` contract.
Swapping `UserSessionManager` for `UserReadRepository` removes the service-layer leak from `SettingsViewModel` and advances roadmap 9 (Kotlin Multiplatform) by keeping the ViewModel bound to platform-free repository interfaces.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt` (class `SettingsViewModel`, constructor parameter at line 25 and function `isGuest` at line 41)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/settings/SettingsViewModelTest.kt` (class `SettingsViewModelTest`, mock field at line 44 and test cases at lines 47-142)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt` untouched; do NOT touch `UserSessionManager` — an open PR owns it
steps:
1. In `SettingsViewModel.kt`, replace `import org.ole.planet.myplanet.services.UserSessionManager` (line 16) with `import org.ole.planet.myplanet.repository.UserReadRepository`.
2. In `SettingsViewModel` constructor (`SettingsViewModel.kt:20-26`), replace `private val userSessionManager: UserSessionManager` (line 25) with `private val userRepository: UserReadRepository`.
3. In `SettingsViewModel.isGuest()` (`SettingsViewModel.kt:41`), replace `userSessionManager.getUserModel()?.id?.startsWith("guest") == true` with `userRepository.getUserModel()?.id?.startsWith("guest") == true`.
4. In `SettingsViewModelTest.kt`, replace `import org.ole.planet.myplanet.services.UserSessionManager` (line 22) and `private val userSessionManager: UserSessionManager = mockk(relaxed = true)` (line 44) with `UserReadRepository` (`private val userRepository: UserReadRepository = mockk(relaxed = true)`).
5. In `SettingsViewModelTest.kt` (lines 50-140), update the `coEvery { ... }` stubs and `SettingsViewModel(...)` constructor calls to pass `userRepository`.
6. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.settings.SettingsViewModelTest"` and confirm all 6 unit tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening Settings as a guest or registered learner still accurately detects guest status via `SettingsViewModel.isGuest()`.
size budget: ~20 changed lines, 2 files
out of scope: Do not modify `UserSessionManager`, `SettingsActivity.kt`, or any Hilt binding modules.

---
<a id="m054"></a>

### M054. CourseStepDao.deleteStaleSteps: stored-minus-keep positive delete in 900 batches

**score 53** · O 39 / D 44 / C 92 · kept by 3/3 plans · raw: L10.02  
**refs:** Opus #45 · Devin R131 · Codex T006  
**shares files with:** M010

**guardrails:**
- Codex scope: In one transaction, read stored step IDs for the course, subtract the complete keep set, and delete stale IDs in bounded positive batches. Preserve empty-keep delete-all semantics and course isolation; never split a NOT IN keep-list into independent deletes.

**verified against tree:** Verified at CourseStepDao.kt:18. It only overflows above about 998 steps in one course, and the caller is already inside withTransaction.

**canonical version — L10-T2 (Codex), "Bound stale course-step deletion by actual stale IDs (roadmap 1+5+7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt:18–22` expands the complete keep-list into one `NOT IN` query, although reads in the same DAO already chunk IDs. Large synced courses can exceed SQLite's parameter limit; splitting the keep-list into separate negative queries would incorrectly delete retained steps.

files:
- Edit `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt`: `CourseStepDao.deleteStaleSteps` and replacement of `deleteStaleInternal`.
- Edit `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseStepDaoTest.kt`: `CourseStepDaoTest` stale-deletion coverage.
- Leave repository sync callers, course models, database configuration, and all read methods alone.

steps:
1. Add an ID-only query for a single course and a positive-ID deletion query constrained by that same course ID; remove the obsolete negative-list helper.
2. Keep the public signature and empty keep-list behavior. For nonempty input, compute the stored-ID set minus the complete deduplicated keep-set.
3. Delete only that difference in batches of at most 900 IDs, leaving room for the course parameter; skip deletion when the difference is empty.
4. Annotate the orchestration method with Room's transaction annotation so its read, difference calculation, and deletions run in one transaction.
5. Extend the existing in-memory Room tests: retain real IDs at the beginning and end of a keep-list containing over 33,000 unique IDs; remove over 900 stale rows; preserve a second course; retain empty-input coverage.

acceptance:
- Run `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.data.room.dao.CourseStepDaoTest'`.
- Run `./gradlew testDefaultDebugUnitTest`.
- Verify resyncing a large course removes obsolete steps, preserves every retained step, and leaves other courses unchanged.
- The repository-facing operation stays stable; this is Android data-adapter hardening rather than a roadmap 9 migration.

size budget: approximately 85–125 changed lines across 2 files, including tests; stay below 150.

out of scope: No schema/index changes, repository edits, shared batching utility, or batching of negative predicates.

---
<a id="m055"></a>

### M055. NotificationsViewModel: flowOn(default) for grouping/flattening

**score 53** · O 35 / D 71 / C 81 · kept by 3/3 plans · raw: L04.03  
**refs:** Opus #98 · Devin R039 · Codex T035  
**shares files with:** M018, M032

**guardrails:**
- Codex scope: Run notification grouping and combined row flattening on the injected compute dispatcher before stateIn, preserving order, selection/expansion updates, subscription behavior, and immutable snapshots.

**verified against tree:** Verified at :61-69. These are O(n) passes over tens or hundreds of items, and the tests need advanceUntilIdle. Same file as notifications-toggle-group-unread.

**canonical version — L04-T3 (Codex), "Move notification grouping and flattening onto the injected compute dispatcher (roadmap 3+7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:61` groups notifications in a flow shared in `viewModelScope`, and lines 65–69 rebuild flattened rows whenever selection or expansion changes. The synchronous grouping at lines 210–225 and row building at lines 229–251 consequently run on main, despite payload formatting already using the default dispatcher at line 79; keeping these transformations inside the ViewModel also supports roadmap 10's state-hoisting direction.

files:

- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` — the `groupedNotifications` and `groupedItems` transformations around `buildNotificationGroups` and `buildGroupedList`; leave loading, formatting, read/delete mutations, and group-order constants alone.
- `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt` — `NotificationsViewModelTest`; extend grouped-list behavior and dispatcher coverage.

steps:

1. Execute the grouping transformation on `dispatcherProvider.default` before its existing sharing boundary, using a flow context boundary or a suspending transformation.
2. Execute only flattened-row construction on the same injected dispatcher using the immutable snapshots passed to the combine transformation. Preserve upstream order, the existing sharing timeout, and initial values.
3. Keep selection membership, group expansion precedence, unread counts, and known/unknown group ordering identical. Do not add concurrent jobs or mutate backing StateFlows from the transformation.
4. Add a regression with a distinct compute dispatcher and an actively collected grouped-items flow; assert equivalent headers and rows after selecting, expanding, and marking notifications read.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest'`
- `./gradlew testDefaultDebugUnitTest`
- With a large notification list, select rows and expand/collapse groups: row order, checks, and unread badges remain correct while interactions stay responsive.

size budget: approximately 55–100 changed lines, 2 files.

out of scope: Do not alter notification persistence, localization, formatting, filtering semantics, adapters, or screen navigation.

---

## Wave 3: worthwhile, lower impact

<a id="m056"></a>

### M056. CourseDetailViewModel.loadCourseDetail: run the markdown rewrite off Main

**score 53** · O 34 / D 71 / C 83 · kept by 3/3 plans · raw: L04.02  
**refs:** Opus #103 · Devin R032 · Codex T030

**guardrails:**
- Codex scope: Resolve the external path on the injected IO dispatcher and rewrite course-description image markdown on the compute dispatcher. Publish state only for the active collection and retain null-directory and load-error behavior.

**verified against tree:** The directory resolve is already cached (FileUtils.kt:38). Only a single regex pass moves.

**canonical version — L04-T2 (Codex), "Prepare course-detail paths and markdown off the main thread (roadmap 3+7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt:53` collects course details in `viewModelScope` on the main dispatcher. Lines 68–72 synchronously resolve the external-files directory and rewrite image markdown for every emission, so opening an image-heavy course can block input; using the existing dispatcher abstraction keeps this work in the ViewModel and supports roadmap 10's state-hoisting direction without adding platform UI dependencies.

files:

- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt` — `CourseDetailViewModel` constructor and `loadCourseDetail`; leave `toggleStepDescription`, `refreshRatings`, and the UI-state types alone.
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModelTest.kt` — `CourseDetailViewModelTest` setup and course-detail loading coverage; this is the only direct constructor call site found in app sources.

steps:

1. Inject the existing `DispatcherProvider` into the ViewModel constructor and supply a test provider at its existing direct test call site. Use the existing Hilt binding without modifying DI modules.
2. Resolve the directory with the injected IO dispatcher, then compute the rewritten description with the injected default dispatcher. Keep state publication in the collecting coroutine.
3. Preserve both existing markdown base-path outcomes, including the current null-directory string, and preserve cancellation of an earlier `loadCourseDetail` call.
4. Add a dispatcher-routing regression using distinguishable injected dispatchers and retain all existing populated, missing-course, and image-path assertions. Make no wall-clock timing assertions.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.courses.CourseDetailViewModelTest'`
- `./gradlew testDefaultDebugUnitTest`
- Open an image-heavy course and switch to another course while it loads: the screen remains interactive and shows the latest course's title, steps, and correctly rewritten local images.

size budget: approximately 55–100 changed lines, 2 files.

out of scope: Do not change course repository contracts, cache descriptions across emissions, change image rendering, or introduce a Compose screen.

---
<a id="m057"></a>

### M057. VoicesRepositoryNewsSyncTest: class-level mockkObject(UrlUtils, NetworkUtils); keep the per-test Room DB

**score 51** · O 38 / D 66 / C 77 · kept by 3/3 plans · raw: L14.04, L15.05, L16.03  
**refs:** Opus #66 · Devin R052 · Codex T050

**guardrails:**
- Codex scope: Reuse the in-memory Room database within a compatible Robolectric test-class sandbox, clearing all relevant tables and state before each case and closing only at class teardown. Verify shuffled/repeated isolation before adopting the reuse.
- Codex scope: Install UrlUtils/NetworkUtils object instrumentation once per class, remove the redundant in-test installation, and reset answers/calls per case.
- L14.04 part dropped by Opus: Shared Room DB half: unsafe under Robolectric's per-test Application reset.

**verified against tree:** Verified. Do NOT share the Room DB across Robolectric tests (L14-T4's other half). The gain is small, because the DB build dominates.

**contributor notes:** L14-T4: mock-hoist half only

**canonical version — L16-T3 (Codex), "Install Voices sync test object mocks once per test class (roadmap 5+8)":**

context: `VoicesRepositoryNewsSyncTest.kt:69–99` instruments two singleton objects before every test and removes both afterward; line 302 redundantly instruments the network object inside an individual test. The latest shard-1 log reports 18 tests taking 14.7 seconds, 13.9 seconds after subtracting first-test excess. Removing repeated instrumentation preserves valuable real-database sync coverage while reducing its setup cost.

files:
- `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt` — `VoicesRepositoryNewsSyncTest.setUp`, `tearDown`, and the existing reaction-upload test's mock setup.
- Leave message/reply fixtures, all assertions, database construction/closure, and production sync/DAO code alone.

steps:
1. Install the existing two object mocks in static JUnit class lifecycle setup and remove exactly those mocks in class teardown.
2. Reset their stubs and recorded calls between tests without uninstrumenting them, then reinstall the per-test URL/device answers in `setUp` so the upload test's override cannot leak.
3. Remove the redundant in-test instrumentation call while retaining that test's device-ID answer.
4. Continue creating a fresh in-memory database per test and closing it in `tearDown`, including cleanup when a test fails.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.VoicesRepositoryNewsSyncTest' --rerun-tasks` must pass; capture before/after test timings under the same conditions.
- `./gradlew testDefaultDebugUnitTest` must stay green, with all 18 tests retained.
- User-visible behavior remains unchanged: synced posts, replies, deletions, and local reactions retain their existing persistence/upload guarantees.

size budget: approximately 35–60 changed lines in 1 file.

out of scope: Do not share database instances, convert Room assertions to mocked DAO results, or change sync semantics. No direct roadmap 9/10 migration is claimed for a test-only lifecycle change.

---
<a id="m058"></a>

### M058. PublicSurveyViewModel: inject TimeProvider for launchTime

**score 51** · O 38 / D 64 / C 80 · kept by 3/3 plans · raw: L15.10, L18.07  
**refs:** Opus #69 · Devin R063 · Codex T039

**guardrails:**
- Codex scope: Inject TimeProvider, replace the wall-clock read, and use a fixed clock in launch/recency tests. Preserve the existing stale-submission event and timestamp comparison exactly.
- L18.07 correction from Codex: PublicSurveyEvent.UploadSkipped does not exist in this checkout; the stale path emits NavigateOnward at line 76. The shipped verification uses the existing event.

**verified against tree:** Verified at :62. Both versions overstate the motivation: the test does not wait real time, and there is no UploadSkipped event.

**canonical version — L15-T10 (Copilot·Kimi), "inject TimeProvider into PublicSurveyViewModel instead of System.currentTimeMillis (roadmap 3+4)":**

context:
app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt:62 stamps `launchTime = System.currentTimeMillis()` directly,
while the DI graph already provides `TimeProvider` via di/TimeModule.kt and other ViewModels (e.g. BellDashboardViewModel) already consume it.
Hard-coding the clock makes the upload-recency check at line 74 (`submission.lastUpdateTime < launchTime`) untestable without waiting real time —
the existing test asserts only `viewModel.launchTime > 0` (app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt:69)
and adds a real second at line 104.
files:
app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt (constructor + line 62).
app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt (constructor call at line 43, assertions at lines 69 and 104).
Do NOT edit utils/TimeProvider.kt (an open PR owns it) — only inject the existing interface.
steps:
1. add `private val timeProvider: TimeProvider` as a constructor parameter of PublicSurveyViewModel and import org.ole.planet.myplanet.utils.TimeProvider.
2. replace `System.currentTimeMillis()` with `timeProvider.now()`.
3. in the test, pass the existing deterministic `TestTimeProvider` (app/src/test/java/org/ole/planet/myplanet/utils/TestTimeProvider.kt) as the new constructor argument.
4. set the provider's `currentTime` explicitly in the affected tests, assert launchTime against that fixed value instead of `> 0`,
   and compute the `lastUpdateTime` at line 104 relative to it.
5. run the survey tests.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests "*PublicSurveyViewModelTest"` green with deterministic time assertions;
`./gradlew testDefaultDebugUnitTest` green;
public-survey flow still stamps launch time on survey load and skips stale submissions on upload.
size budget: ~20 changed lines, 2 files.
out of scope: no changes to TimeModule, TimeProvider or other ViewModels; no behavior change beyond the injected clock.

---
<a id="m059"></a>

### M059. automerge.sh publish_failed: one commits/{sha}/check-runs call instead of runs+jobs N+1

**score 50** · O 39 / D 56 / C 78 · kept by 3/3 plans · raw: L14.08  
**refs:** Opus #42 · Devin R100 · Codex T044  
**shares files with:** M037

**guardrails:**
- Codex scope: Find completed matching publish checks from the SHA check-run list, then read annotations using the actual check-run ID. Preserve pagination, job-name/app selection, marker filtering, track verification, and fresh-state decisions; avoid treating an Actions job ID as guaranteed interchangeable with a check-run ID.

**verified against tree:** Verified at :339-344.

**canonical version — L14-T8 (Copilot·Grok), "replace the automerge publish-failure N+1 with one check-run list (roadmap 7)":**

context: `publish_failed` in `.github/scripts/automerge.sh:336-360` lists every completed Actions run for the SHA, then calls the jobs API once per run, then the annotations API for the first job named `$PUBLISH_JOB`. A master SHA also has the test workflow, so this is one request plus two more per unrelated run, on the drain path that decides whether a playstore warning is still live. `PUBLISH_JOB` defaults empty and is set to `myPlanet release (lite)` by `automerge.yml`. The check-runs API returns that job id directly. This is a workflow quick win only; it does not move roadmap 9 or 10.

files: `.github/scripts/automerge.sh` (`publish_failed` only). Do not edit the workflow that sets `PUBLISH_JOB`.

steps:
1. Replace the runs loop with one `gh api repos/$REPO/commits/$sha/check-runs?per_page=100` filtered to `status == "completed"` and `name == $PUBLISH_JOB`. Use those ids as today's `job_id`.
2. Keep the annotations request and the `PUBLISH_FAIL_MARKER` prefix check. Keep the `PLAYSTORE_SH pending` stale-warning branch, including the `pending=false` plus `track_code=` escape.
3. Keep the return codes: 0 when a live publish-failure warning is found, 1 when `PUBLISH_JOB` is empty, no matching check run exists, or the warning is stale.
4. Do not change `POLL_INTERVAL_SEC`, `rerun_failed_runs`, or the callers at lines 470 and 605.

acceptance: `bash -n .github/scripts/automerge.sh` passes. A dry read of `publish_failed` shows one check-runs list per call, not one jobs list per workflow run. Return codes and log lines (`the $BASE track carries it now`) are unchanged. `./gradlew testDefaultDebugUnitTest` is untouched and must stay green.

size budget: ~20 changed lines, 1 file.

out of scope: do not retry annotations, do not change which job name counts as a publish failure, do not edit the workflow that sets `PUBLISH_JOB`.

---
<a id="m060"></a>

### M060. ChatViewModel.loadShareTargets: run the three summary reads concurrently

**score 49** · O 38 / D 64 / C 78 · kept by 3/3 plans · raw: L06.04, L13.03  
**refs:** Opus #67 · Devin R062 · Codex T045  
**shares files with:** M042, M090

**guardrails:**
- Codex scope: Start team and enterprise summary reads concurrently.
- Codex scope: Resolve the optional community ID first and overlap its summary lookup too, retaining the existing fallback TeamSummary and null-ID behavior.

**verified against tree:** Verified, but cached after the first load (:147). L13-T3 contradicts its own 'no new scopes' rule and misses the third call.

**canonical version — L06-T4 (Antigravity), "parallelize team, enterprise, and community summary queries in ChatViewModel.loadShareTargets (roadmap 3+7)":**

context: `ChatViewModel.kt:193-217` (`loadShareTargets`) sequentially awaits
`teamsRepository.getTeamSummaries(userId)`, `teamsRepository.getShareableEnterpriseSummaries(userId)`, and
`teamsRepository.getTeamSummaryById(id)` when building `ChatShareTargets` during chat history screen
initialization (`loadChatHistoryScreenData` at line 147). All three `TeamsShareRepository` queries are
read-only and independent of one another, and `ChatViewModel` already imports `async` and `coroutineScope`
(lines 8 and 10) for news and chat history reads. Running the three share-target reads concurrently in
`coroutineScope` reduces cold-load latency on the AI chat history screen and advances roadmap 9 and 10 by
keeping `ChatViewModel` platform-free.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` (`ChatViewModel.loadShareTargets`, lines 193-217)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt` (`ChatViewModelTest`)
- leave alone: `ChatViewModel.loadChatHistoryScreenData`, `ChatConversationPaginator`, and `TeamsRepositoryImpl` (owned by open PRs)
steps:
1. Open `ChatViewModel.kt` and locate `private suspend fun loadShareTargets(parentCode: String?, communityName: String?, userId: String?): ChatShareTargets` at lines 193-217.
2. Compute `communityId` upfront as `if (!communityName.isNullOrBlank() && !parentCode.isNullOrBlank()) "$communityName@$parentCode" else null`.
3. Wrap the body of `loadShareTargets` in `coroutineScope` and launch `val teamsDeferred = async { teamsRepository.getTeamSummaries(userId) }`, `val enterprisesDeferred = async { teamsRepository.getShareableEnterpriseSummaries(userId) }`, and `val communityDeferred = async { communityId?.let { id -> teamsRepository.getTeamSummaryById(id) ?: TeamSummary(...) } }`.
4. Return `ChatShareTargets(communityDeferred.await(), teamsDeferred.await(), enterprisesDeferred.await())`.
5. In `ChatViewModelTest.kt`, add a test verifying that `getTeamSummaries`, `getShareableEnterpriseSummaries`, and `getTeamSummaryById` start concurrently before any of their `CompletableDeferred` gates complete.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening the chat history share sheet lists
the same community, team, and enterprise targets.
size budget: ~45 changed lines, 2 files
out of scope: do not change `cachedShareTargets` invalidation in `loadChatHistoryScreenData` or modify `TeamsShareRepository`.

---
<a id="m061"></a>

### M061. Hoist getUserProfile/hasActiveUserSyncAction/getUsersSortedBy/searchUsers onto UserReadRepository; narrow Ratings/Markdown/Health VMs

**score 46** · O 46 / D 60 / C 71 · kept by 3/3 plans · raw: L07.02  
**refs:** Opus #34 · Devin R099 · Codex T080  
**shares files with:** M029

**guardrails:**
- Codex scope: Promote getUserProfile for RatingsViewModel, hasActiveUserSyncAction for MarkdownViewModel, and the sorted/search reads for HealthViewModel to UserReadRepository. Move declarations consistently rather than duplicating inherited signatures without override; retain current implementations and Hilt bindings.
- L07.02 correction from Codex: Do not retain identical inherited declarations without override in UserRepository; that is not a valid Kotlin redeclaration. The unused getUserImageUrl promotion is omitted because no consuming production call was found.

**verified against tree:** Premise verified. As written it does not compile: the redeclarations in UserRepository.kt need `override` or deletion. Delete getUserImageUrl instead of hoisting it, because it has zero callers.

**canonical version — L07-T2 (Devin), "Hoist profile/sync-status reads onto UserReadRepository and slim three view models (roadmap 1, 4, 9)":**

context: `UserReadRepository` exposes only `getUserById`, `getUsersByIds`, `getUserModel` (UserReadRepository.kt:6-8), so consumers of any other read still inject the 70-function `UserRepository`. Four functions are pure reads that view models already use: `getUserProfile` (UserRepository.kt:82; used by `ui/ratings/RatingsViewModel.kt:55,85`), `getUserImageUrl` (UserRepository.kt:83), `hasActiveUserSyncAction` (UserRepository.kt:90; used by `ui/components/MarkdownViewModel.kt:16-17` which forwards it to `MarkdownDialogFragment.kt:119`), and the sorted/search reads `getUsersSortedBy` + `searchUsers` (UserRepository.kt:37,39; used by `ui/health/HealthViewModel.kt:109`).

files:
- `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt` — add the five declarations.
- `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt` — inject `UserReadRepository` instead of `UserRepository`.
- `app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownViewModel.kt` — inject `UserReadRepository`.
- `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt` — inject `UserReadRepository`.

steps:
1. Add `getUserProfile()`, `getUserImageUrl()`, `hasActiveUserSyncAction()`, `getUsersSortedBy(fieldName, descending)`, `searchUsers(query, sortField, descending)` to `UserReadRepository`. Because `UserRepository : UserReadRepository` (UserRepository.kt:23) the declarations are still visible through the wide interface; do **not** remove them from `UserRepository.kt` (another work order owns that file).
2. In each of the three view models, change the constructor parameter type from `UserRepository` to `UserReadRepository` and fix the import.
3. In `HealthViewModel` the `userRepository` field serves all four call sites (109, 124, 159, 201) — all are reads, so the single swap covers it; keep `healthRepository` and `realtimeSyncManager` untouched.

acceptance: `./gradlew testDefaultDebugUnitTest` stays green; ratings profile load, markdown challenge-completion check and health patient search behave identically; the three view models can no longer reach user writes.

size budget: ~18 changed lines, 4 files.

out of scope: removing the (now redundant) declarations from `UserRepository.kt`; `RatingsRepositoryImpl` (owned by open PR #18076); the SQL-side sort fix (separate work order).

---
<a id="m062"></a>

### M062. EventsRepositoryImpl.toggleAttendance: return the mutated meetup instead of re-querying

**score 46** · O 39 / D 61 / C 72 · kept by 3/3 plans · raw: L09.08  
**refs:** Opus #47 · Devin R086 · Codex T074

**guardrails:**
- Codex scope: Return the mutated entity after successful synchronous upsert in toggleAttendance. Preserve blank meetup/user handling and avoid claiming a concurrency snapshot stronger than the previous implementation.

**verified against tree:** Verified at :100-102. It saves one indexed point SELECT.

**canonical version — L09-T8 (Copilot·Kimi), "drop the redundant re-query in EventsRepositoryImpl.toggleAttendance (roadmap 7+1)":**

context: In `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt`, `toggleAttendance(meetupId, userId)` fetches the row with `meetupDao.getByMeetupId(meetupId)`, mutates `userId` on the in-memory instance, calls `meetupDao.upsert(meetup)`, and then runs `return getMeetupById(meetupId)` — which is the identical getByMeetupId query again, a second database round-trip returning the row already in hand. On slow devices this doubles the join/leave tap latency for no consistency benefit (upsert is synchronous within the same suspend fun).
files: app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt only. Leave alone: data/room/dao/MeetupDao.kt, EventsRepository.kt, model/Meetup.kt, and the meetup UI callers (the returned Meetup instance carries the same data they receive today).
steps:
1. In `toggleAttendance`, after `meetupDao.upsert(meetup)`, return the mutated `meetup` instance directly instead of calling `getMeetupById(meetupId)`.
2. Confirm the early-return branch (`userId.isBlank()` → `getMeetupById(meetupId)`) is untouched — that path performs no mutation and still needs the fetch.
3. Run events repository tests; if any test asserts the re-fetch (e.g., verifies getByMeetupId called twice), update the verification to once.
acceptance: ./gradlew testDefaultDebugUnitTest green; tapping join/leave on a meetup still flips attendance and returns the updated meetup with the correct userId set or cleared.
size budget: ~3 changed lines, 1 file
out of scope: no DAO changes, no transaction annotation changes, no changes to insertMeetupsFromSync or batchInsertMeetups.

---
<a id="m063"></a>

### M063. NotificationActionReceiver: @Inject BroadcastService instead of the EntryPoint lookup

**score 43** · O 39 / D 64 / C 68 · kept by 3/3 plans · raw: L09.10  
**refs:** Opus #53 · Devin R069 · Codex T101

**guardrails:**
- Codex scope: Field-inject BroadcastService through the existing AndroidEntryPoint and replace getBroadcastService(context). Preserve system/local broadcast actions, extras, threading, and receiver completion.

**verified against tree:** Verified at :110. Keep the helper for DownloadService.

**canonical version — L09-T10 (Copilot·Kimi), "inject BroadcastService into NotificationActionReceiver instead of the EntryPoint lookup (roadmap 4)":**

context: `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt` is already `@AndroidEntryPoint` and field-injects NotificationsRepository, DispatcherProvider and the application scope — yet inside `markNotificationAsRead(...)` it still resolves BroadcastService through the manual entry-point helper `getBroadcastService(context)` (imported from di/ServiceDependenciesEntryPoint.kt, used at `val broadcastService = getBroadcastService(context)`). BroadcastService is a plain `@Singleton` with an `@Inject constructor()` (services/BroadcastService.kt:10), so the indirection is leftover DI debt that roadmap 4 wants gone.
files: app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt and app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt. Leave alone: di/ServiceDependenciesEntryPoint.kt — its `broadcastService()`/`getBroadcastService` must stay because DownloadService.kt still uses them and DownloadService.kt is owned by open PR 18540.
steps:
1. Add `@Inject lateinit var broadcastService: BroadcastService` to NotificationActionReceiver alongside the existing injected fields.
2. Replace the `getBroadcastService(context)` call in `markNotificationAsRead` with the injected field; delete the `import org.ole.planet.myplanet.di.getBroadcastService` line.
3. In NotificationActionReceiverTest, replace the `every { getBroadcastService(any()) } returns mockk(relaxed = true)` static mock with a relaxed BroadcastService mock set on the receiver, matching how the test already handles notificationsRepository.
4. Run the receiver tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; marking a notification read from a system notification still emits the NOTIFICATION_READ_FROM_SYSTEM_LOCAL broadcast and clears the badge as before.
size budget: ~15 changed lines, 2 files
out of scope: do not remove or alter ServiceDependenciesEntryPoint, do not touch DownloadService or the retry queue entry point, no behavior changes to notification actions.

---
<a id="m064"></a>

### M064. RequestsViewModel.loadJoinedMembers: run members and current-user reads concurrently

**score 43** · O 38 / D 64 / C 73 · kept by 3/3 plans · raw: L06.03  
**refs:** Opus #83 · Devin R076 · Codex T068

**guardrails:**
- Codex scope: Use structured child coroutines for member and current-user reads in RequestsViewModel.loadJoinedMembers, then publish one state with the existing leadership predicate.

**verified against tree:** Verified at :69-70. getUserModel is a primary-key lookup.

**canonical version — L06-T3 (Antigravity), "parallelize joined-members and current-user reads in RequestsViewModel.loadJoinedMembers (roadmap 3+7)":**

context: `RequestsViewModel.kt:67-78` (`loadJoinedMembers`) sequentially awaits
`val members = teamsRepository.getJoinedMembersWithVisitInfo(teamId)` and
`val currentUserId = userRepository.getUserModel()?.id` before publishing `MembersUiState`. In the same
class, `fetchMembers` (`RequestsViewModel.kt:131-149`) already uses `coroutineScope` and `async` to run
`teamsRepository` and `userRepository.getUserModel()` queries concurrently. Running the two independent
reads in `loadJoinedMembers` concurrently removes serial wait latency on opening the team members tab and
after leadership or membership mutations while keeping `RequestsViewModel` free of `android.*` imports
(advancing roadmap 9 and 10).
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt` (`RequestsViewModel.loadJoinedMembers`, lines 67-78)
- touch: `app/src/test/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModelTest.kt` (`RequestsViewModelTest`)
- leave alone: `RequestsViewModel.fetchMembers`, `RequestsViewModel.removeMember`, `RequestsViewModel.leaveTeam`, `MembersFragment`, and `TeamsRepositoryImpl` (owned by open PRs)
steps:
1. Open `RequestsViewModel.kt` and inspect `loadJoinedMembers(teamId: String)` at lines 67-78 (`async` and `coroutineScope` are already imported at lines 7-8).
2. Inside `viewModelScope.launch` in `loadJoinedMembers`, wrap the two repository calls in `coroutineScope` with `val membersDeferred = async { teamsRepository.getJoinedMembersWithVisitInfo(teamId) }` and `val userDeferred = async { userRepository.getUserModel() }`.
3. Await `val members = membersDeferred.await()` and `val currentUserId = userDeferred.await()?.id`, then construct and assign `_membersState.value = MembersUiState(...)` with the existing `isLeader = members.any { it.user.id == currentUserId && it.isLeader }` calculation.
4. In `RequestsViewModelTest.kt`, add a test verifying that `getJoinedMembersWithVisitInfo` and `getUserModel` both start before either finishes during `loadJoinedMembers`, while keeping all existing `coVerifySequence` assertions in `removeMember` green.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.teams.members.RequestsViewModelTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; the team Joined Members screen displays the
member list, current user leadership controls, and visit info identically.
size budget: ~35 changed lines, 2 files
out of scope: do not alter `removeMember`, `leaveTeam`, `makeLeader`, `fetchMembers`, or any DAO/repository classes.

---
<a id="m065"></a>

### M065. SubmissionDetailViewModel: collapse the intermediate stateIn

**score 43** · O 38 / D 61 / C 77 · kept by 3/3 plans · raw: L12.09  
**refs:** Opus #87 · Devin R091 · Codex T053  
**shares files with:** M019, M030

**guardrails:**
- Codex scope: Inject UserReadRepository for the single submission-author lookup.
- Codex scope: Map the one-shot submission detail directly into uiState and share it once, retaining null/loading/error semantics and WhileSubscribed behavior.

**verified against tree:** Verified at :43/:59. It conflicts with the MockK migration of the same test, so sequence the two.

**canonical version — L12-T9 (Antigravity), "narrow SubmissionDetailViewModel to UserReadRepository and collapse duplicate stateIn pipeline (roadmap 1+3+7)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt:30` injects the full `UserRepository` interface (`SubmissionDetailViewModel.kt:16`) solely to call `userRepository.getUserById(it)` (`SubmissionDetailViewModel.kt:38`), which is already part of `UserReadRepository`.
In addition, `SubmissionDetailViewModel` materializes a private intermediate `StateFlow<SubmissionDetail?>` (`submissionDetailState` at lines 35-43) with `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)` and immediately maps it into a second `StateFlow<SubmissionDetailUiState>` (`uiState` at lines 45-59) with a second `.stateIn(...)`, maintaining two `StateFlow` coroutines when `submissionDetailState` has no other consumers.
Narrowing `userRepository` to `UserReadRepository` and mapping the upstream `flow { ... }` directly into `uiState` tightens the repository boundary (advancing roadmap 9) and simplifies the repository-to-ViewModel reactive pipeline.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt` (class `SubmissionDetailViewModel`, import at line 16, constructor at line 30, and flows at lines 35-59)
- touch `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt` (class `SubmissionDetailViewModelTest`, import at line 23 and mock setup at lines 30, 36)
- leave `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailFragment.kt` untouched; do NOT touch `SubmissionsRepositoryImpl` — open PRs own it
steps:
1. In `SubmissionDetailViewModel.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 16) with `import org.ole.planet.myplanet.repository.UserReadRepository` and change `private val userRepository: UserRepository` (line 30) to `private val userRepository: UserReadRepository`.
2. In `SubmissionDetailViewModel.kt` (lines 35-59), remove the intermediate `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)` on `submissionDetailState` (and remove unused `import org.ole.planet.myplanet.model.SubmissionDetail` at line 14 if inlined) so only `uiState` calls `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubmissionDetailUiState())`.
3. In `SubmissionDetailViewModelTest.kt`, replace `import org.ole.planet.myplanet.repository.UserRepository` (line 23) with `import org.ole.planet.myplanet.repository.UserReadRepository`.
4. In `SubmissionDetailViewModelTest.kt`, update `private lateinit var userRepository: UserReadRepository` (line 30) and `mock(UserReadRepository::class.java)` (line 36).
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.ui.submissions.SubmissionDetailViewModelTest"` and confirm both unit tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; opening `SubmissionDetailFragment` renders the submission title, status, formatted date, submitter name, and question-answer list identically.
size budget: ~20 changed lines, 2 files
out of scope: Do not modify `SubmissionsRepositoryImpl` or `SubmissionDetailFragment.kt`.

---
<a id="m066"></a>

### M066. playstore-quota.sh forecast: measure once instead of twice

**score 43** · O 33 / D 64 / C 83 · kept by 3/3 plans · raw: L18.01  
**refs:** Opus #111 · Devin R070 · Codex T032  
**shares files with:** M109

**guardrails:**
- Codex scope: Measure release epochs once per forecast and format both its report and future slots from that same snapshot instead of calling status and fetching again.

**verified against tree:** Premise verified (:117-119, and :84 measures again). The steps name variables and a subcommand that don't exist, and the acceptance command dies (RELEASES_JSON is a path, and REPO is required).

**canonical version — L18-T1 (Antigravity), "eliminate duplicate release API fetch in playstore quota forecast (roadmap 5+8)":**

context: `.github/scripts/playstore-quota.sh:117-119` runs `measure` in `forecast()` and then
immediately runs `report=$(status | sed -n "s/^report='\(.*\)'$/\1/p")`, where `status()` at
line 82 calls `measure` a second time inside a command-substitution subshell. Because
`save_epochs()` at lines 33-44 fetches `gh api "repos/$REPO/releases?per_page=100"` whenever
`RELEASES_JSON` is unset, `playstore-quota.sh forecast` makes two identical GitHub Releases API
calls and runs `jq` over 100 releases twice during every Play Store and release workflow check.
Serving roadmap 5 and 8, this trims redundant GitHub API traffic and latency from release and
publishing automation.
files:
- touch: `.github/scripts/playstore-quota.sh` (`save_epochs` at lines 33-44, `status` at lines 81-112, `forecast` at lines 114-133)
- leave alone: `.github/scripts/playstore.sh`
steps:
1. Extract a helper function `build_status_report()` in `.github/scripts/playstore-quota.sh` that formats the `report` string from the already-computed `measure` variables (`used`, `SPARSE_SLOTS`, `remaining`, `status_word`, `next_slot_in`, `sparse_48h`, `sparse_week`) without re-invoking `measure`.
2. Update `status()` (lines 81-112) to call `measure`, call `build_status_report` to populate `report`, and emit the `GITHUB_OUTPUT`-compatible key-value lines.
3. Update `forecast()` (lines 114-133) to call `measure` once, call `build_status_report` directly to obtain `report` instead of spawning `status | sed ...`, and print the single-line summary.
4. Verify with `RELEASES_JSON` fixtures that `status`, `forecast`, and `gate` produce identical output before and after the change.
acceptance: `bash -n .github/scripts/playstore-quota.sh` and
`RELEASES_JSON='[]' NOW_EPOCH=1700000000 bash .github/scripts/playstore-quota.sh forecast` and
`./gradlew testDefaultDebugUnitTest` pass; `forecast` invokes `save_epochs` once per run and
prints the same quota summary string.
size budget: ~25 changed lines, 1 file
out of scope: do not change Play Store slot thresholds (`DAILY_CAP`, `SPARSE_SLOTS`) or
`.github/scripts/playstore.sh`.

---
<a id="m067"></a>

### M067. TeamCoursesViewModel.getAvailableCourses: run the course-table mapping on io

**score 42** · O 39 / D 36 / C 78 · kept by 3/3 plans · raw: L14.06  
**refs:** Opus #60 · Devin R135 · Codex T046

**guardrails:**
- Codex scope: Inject DispatcherProvider into TeamCoursesViewModel and move available-course loading/mapping off the fragment main dispatcher, using IO for blocking storage preparation and default for filtering. Preserve exclusion IDs and list order.

**verified against tree:** Verified: getAllCourses → mapCourses runs a groupBy over the whole table on Main (TeamCoursesFragment.kt:107).

**canonical version — L14-T6 (Copilot·Grok), "run the team course picker off the main dispatcher (roadmap 7+10)":**

context: `TeamCoursesViewModel.getAvailableCourses` (`TeamCoursesViewModel.kt:55-59`) loads `coursesRepository.getAllCourses()` and then filters out ids from `teamsRepository.getTeamCourseIds`. `getAllCourses` maps the full course table on the caller thread. `TeamCoursesFragment.kt:109` calls it from `lifecycleScope.launch`, which is Main, before showing the add-course dialog. The repositories are owned by open PRs, so a SQL exclusion cannot be added in this round. Moving the existing calls onto `DispatcherProvider.io` is the unblock: the fragment can stay a thin collector, which is what a later Compose dialog needs (roadmap 10). The view model stays an Android `ViewModel`, so this is not roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt` and `app/src/test/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModelTest.kt`. Do not edit `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt`, the courses or teams repositories, or `app/src/main/java/org/ole/planet/myplanet/di/DispatcherModule.kt` (`provideDispatcherProvider` already exists).

steps:
1. Inject `DispatcherProvider` as a third constructor parameter. Hilt already binds it; do not add a module.
2. Wrap the two repository calls and the `courseId !in existingIds` filter in `withContext(dispatcherProvider.io)`. Return the same `List<MyCourse>`.
3. Update `TeamCoursesViewModelTest` to pass `TestDispatcherProvider(testDispatcher)`. The class already runs those tests with `runTest(testDispatcher)`.
4. Keep `loadCourses`, `addCourses`, and `removeCourse` on their current dispatchers.

acceptance: `./gradlew testDefaultDebugUnitTest` green. `getAvailableCourses filters out existing course ids` and `getAvailableCourses returns all when none are linked` still pass. The add-course dialog still lists courses that are not already on the team, and lists all courses when the team has none.

size budget: ~20 changed lines, 2 files.

out of scope: do not add `getCoursesExcluding`, do not change the dialog UI, do not parallelize `loadCourses`.

---
<a id="m068"></a>

### M068. Move TeamsTasksFragment companion filters into TeamsTasksViewModel

**score 42** · O 32 / D 66 / C 78 · kept by 3/3 plans · raw: L09.06  
**refs:** Opus #112 · Devin R057 · Codex T048

**guardrails:**
- Codex scope: Extract pure task-state/current-user filters and assignable-member selection behind TeamsTasksViewModel or a pure collaborator. Retain sort/state/user rules and have the fragment consume prepared values.

**verified against tree:** The filters exist, but 'untestable' is false: TeamsTasksFilterAndStatusTest calls them directly and must be migrated, not duplicated.

**canonical version — L09-T6 (Copilot·Kimi), "hoist task filtering and assignee filtering out of TeamsTasksFragment into TeamsTasksViewModel (roadmap 3+10, helps 1)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt` holds five data-shaping functions in its companion object (`filterAllTasks`, `filterTodoTasks`, `filterInProgressTasks`, `filterCompletedTasks`, `filterMyTasks`) plus member filtering inside `loadAssignableMembers()` (`userList.filter { user.getFullName().isNotBlank() || !user.name.isNullOrBlank() }`). This is domain logic living in the view layer: it is untestable without a fragment, and roadmap 10 needs state and shaping hoisted so a future Compose screen stays portable. TeamsTasksViewModel (same package) already exposes repository passthroughs `getJoinedMembers(teamId)` and `fetchAssigneeNames(...)`, so it is the natural home.
files: app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt and app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksViewModel.kt; optionally one new test file app/src/test/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksViewModelFilterTest.kt. Leave alone: TeamsTasksAdapter.kt, ui/teams/TeamViewModel.kt, repository/TeamsRepository.kt, repository/TeamsRepositoryImpl.kt.
steps:
1. Add to TeamsTasksViewModel: a pure `filterTasks(tasks: List<TeamTask>, tab: TaskTab, userId: String?): List<TeamTask>` (introduce a small `enum class TaskTab { ALL, TODO, IN_PROGRESS, COMPLETED, MINE }` in the viewmodel file) implementing exactly the five current companion functions' filter+sort rules.
2. Add `suspend fun getAssignableMembers(teamId: String): List<UserEntity>` to TeamsTasksViewModel applying the current name/non-blank filter.
3. In TeamsTasksFragment, map the checked toggle id to TaskTab, call `teamsTasksViewModel.filterTasks(...)` inside `updateTasks()`, and delegate `loadAssignableMembers()` to `getAssignableMembers(teamId)`.
4. Delete the fragment companion object with the five filter functions and the private allTasks/todoTasks/inProgressTasks/completedTasks/myTasks wrappers.
5. Add the focused unit test for filterTasks (all five tabs, completed ordering last, my-tasks excludes completed) if the test file is created.
acceptance: ./gradlew testDefaultDebugUnitTest green; the teams tasks screen shows identical lists for all five tabs (All/Todo/In progress/Completed/Mine) and the assign-member dialog still lists only members with a usable name.
size budget: ~120 changed lines, 2-3 files
out of scope: no Compose migration, no changes to task creation/deadline handling, no repository or DAO changes, no adapter changes.

---
<a id="m069"></a>

### M069. ImageUtilsTest: class-level Glide static mock + relaxed ImageView

**score 40** · O 27 / D 66 / C 76 · kept by 3/3 plans · raw: L14.09  
**refs:** Opus #113 · Devin R058 · Codex T056

**guardrails:**
- Codex scope: Stub the image-resource setter on interaction-test spies while retaining clear-before-placeholder verification and resource IDs. Share static Glide instrumentation only with per-test call/answer resets and targeted teardown. Keep rendering tests separate where drawable decoding is the asserted behavior.

**verified against tree:** The per-test mockkStatic(Glide) is verified. As written the stubs leak into the real-Glide tests, so clear them in @Before.

**canonical version — L14-T9 (Copilot·Grok), "stop ImageUtils clear-tests from decoding ole_logo under a spy (roadmap 7+8)":**

context: Master test run 42782082676 shard 1 recorded `ImageUtilsTest` at 7.0s excluding first-test excess, of which `testLoadImage_ClearsInFlightRequest_WhenEmptyUrl` (`ImageUtilsTest.kt:233`) took 4.6s. That test, and five siblings (lines 149, 169, 190, 215, 256), each call `mockkStatic(Glide::class)` and `spyk(ImageView(context))`. `ImageUtils.loadImage` then calls the real `setImageResource(R.drawable.ole_logo)` on the spy. The class is already Robolectric; the spy-plus-static-mock inside each test is the extra cost. Speeding this class is part of getting shard 1 back under 400s. It does not move roadmap 9 or 10: production `ImageUtils` still takes an `ImageView`.

files: `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt` only. Do not edit `app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt`.

steps:
1. Install `mockkStatic(Glide::class)` in `@BeforeClass` and clear it in `@AfterClass`. Remove the per-test `mockkStatic` / `unmockkStatic` pairs in this file.
2. In the six clear-in-flight tests, replace `spyk(ImageView(context))` with `mockk<ImageView>(relaxed = true)`. Stub `context` to the existing Robolectric context and stub `isOpaque` the way the spy tests do today.
3. Keep every `verify` and `verifyOrder`, including `requestManager.clear(...)` before `setImageResource(R.drawable.ole_logo)` for the empty and null URL cases, and the "does not clear" cases.
4. Leave the `@Before` real `ImageView` in place for the tests that still need `shadowOf`.

acceptance: `./gradlew testDefaultDebugUnitTest` green. The empty-URL test still fails if `loadImage` stops clearing the request or stops setting `ole_logo`. It must not decode the drawable to do that.

size budget: ~50 changed lines, 1 file.

out of scope: do not remove `@RunWith(RobolectricTestRunner)`, do not change Glide `override` behavior, do not add a test dependency.

---
<a id="m070"></a>

### M070. Delete dead android Log static mocks (RetryQueueWorkerTest, DownloadService*Test, HealthExaminationTest, NewsTest)

**score 39** · O 34 / D 71 / C 71 · kept by 3/3 plans · raw: L17.10, L18.05  
**refs:** Opus #100 · Devin R045, R061 · Codex T086, T093, T043, T036

**guardrails:**
- Codex scope: Remove unused Log mocks from HealthExaminationTest and retain all malformed-data fallback assertions.
- Codex scope: Remove unused Log mocks from NewsTest while retaining targeted cleanup for its in-test FileUtils object mocks.
- L17.10 correction from Codex: RetryQueueWorkerTest and DownloadServiceTest already install Log at BeforeClass, not per test. Removing their dead mock remains valid, but the alleged per-test setup savings there are dropped.

**verified against tree:** Verified dead: no Log verify, and returnDefaultValues=true. RetryQueueWorkerTest and DownloadServiceTest already install once per class, so only the per-test stubs repeat.

**contributor notes:** L18-T5: Health/News part

**canonical version — L17-T10 (ClaudeCode), "Drop redundant `android.util.Log` static mocks from four test classes (roadmap 8, CI wall-clock)":**

context: `app/build.gradle:22` sets `unitTests.returnDefaultValues = true`, so on the JVM every `Log.d/i/w/e` call already returns `0`. Under Robolectric, `ShadowLog` handles them. Four test classes still `mockkStatic(Log::class)` only to stub `Log.* returns 0`. None of them stubs `isLoggable` or `verify`s a Log call. Each `mockkStatic` re-instruments `android.util.Log` bytecode, and that cost appears in the shard timing tables. `DownloadServiceTest` takes 11.5 s for 11 tests (0.57 s median per test) in shard 1, while both shards are over their 400 s budget (499 s and 436 s, run 42782082676). The four classes:
- `services/FreeSpaceWorkerTest.kt:69-70`
- `services/retry/RetryQueueWorkerTest.kt:68` and `85-89`
- `services/DownloadServiceTest.kt:66`, `74` and `86-90`
- `services/DownloadServiceOnDownloadCompleteTest.kt:50-55`
files:
- those 4 files under `app/src/test/java/org/ole/planet/myplanet/`.
- Leave alone: tests that `verify` Log calls or stub `isLoggable` (`ServerReachabilityWorkerTest`, `ServiceModuleTest`, `NotificationActionReceiverTest`, `SyncTimeLoggerTest`, `HealthExaminationTest`, `GsonUtilsCoercionTest` and `SyncTimeLoggerConcurrencyTest`), and `app/build.gradle`.
steps:
1. Delete the `mockkStatic(Log::class)` / `mockkStatic(android.util.Log::class)` lines and every `every { Log.… } returns 0` stub in the four files.
2. Delete `unmockkStatic(Log::class)` in `DownloadServiceTest.kt:74`. Keep the `unmockkAll()` / `clearAllMocks()` calls, because they still reset the other mocks.
3. Remove `import android.util.Log` and any mockk imports that are now unused.
4. Record each class's time before and after the change from `python3 .github/scripts/test_timing_summary.py app/build/test-results/testDefaultDebugUnitTest` and put the numbers in the PR description.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*FreeSpaceWorkerTest" --tests "*RetryQueueWorkerTest" --tests "*DownloadServiceTest" --tests "*DownloadServiceOnDownloadCompleteTest"` passes with the same test count as before; the full `./gradlew testDefaultDebugUnitTest` stays green.
- The timing summary shows no class slower than before.
size budget: about 30 removed lines and 0 added, in 4 files.
out of scope: no production code changes and no Gradle `testOptions` edits.

---
<a id="m071"></a>

### M071. ResourcesRepository: default stepResourcesNeedingDownload; CoursesStepsViewModel next-step prefetch uses it

**score 38** · O 39 / D 61 / C 67 · kept by 3/3 plans · raw: L08.09  
**refs:** Opus #46 · Devin R085 · Codex T104  
**shares files with:** M019, M025, M040

**guardrails:**
- Codex scope: Expose the next-step not-offline selection on ResourcesRepository, using a default implementation if the existing implementation must remain untouched. Keep the exact resourceOffline/revision rule and leave URL handoff in CoursesStepsViewModel.

**verified against tree:** Verified at CoursesStepsViewModel.kt:126-127. The current-step filter at :82 keeps the rule in the VM, so the goal is only half met.

**canonical version — L08-T9 (Copilot·Grok), "ask the resources repository which next-step files still need download (roadmap 1+3+10)":**

context: `CoursesStepsViewModel.loadStep` (`CoursesStepsViewModel.kt:126-127`)
loads every resource for the next step with
`resourcesRepository.getAllStepResources` (`ResourcesRepository.kt:76`) and
then applies `!it.isResourceOffline()` in the viewmodel. `isResourceOffline`
(`MyLibrary.kt:144-145`) is the persistence rule
(`resourceOffline && _rev == downloadedRev`), not screen state. A future
Compose step screen should ask the repository for the download set and keep
only URL handoff in the viewmodel. `ResourcesRepositoryImpl` is in an open
PR, so the new method must be a default on the interface and must not require
an impl override. The prefetch test at `CoursesStepsViewModelTest.kt:214`
currently stubs `getAllStepResources` and will miss the new call unless it is
updated.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`
(add the default next to `getAllStepResources`).
`app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`
(`loadStep` next-step block only).
`app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt`
(`loadStep_whenNextStepIdProvided_prefetchesNextStepResources` at line 214).
Do not touch `ResourcesRepositoryImpl.kt`, `MyLibrary.kt`, or
`ResourceDownloadCoordinator`.

steps: 1. Add a default method
`suspend fun stepResourcesNeedingDownload(stepId: String?): List<MyLibrary>`
on `ResourcesRepository` that returns `getAllStepResources(stepId)` filtered
to items where `isResourceOffline()` is false. A body is required so the
blocked impl does not need a new override. 2. In the `nextStepId != null`
block, replace `getAllStepResources` plus the filter with
`stepResourcesNeedingDownload`. Keep `UrlUtils.getUrl` and
`resourceDownloadCoordinator.startBackgroundDownload`. 3. Do not change the
current-step filter at line 82, `downloadResourcesPriority`, or
`refreshInlineResources` at line 163 (that path must still show every
resource, including ones already offline). 4. Update the prefetch test to
stub `stepResourcesNeedingDownload`, because a mock does not run the
interface default. Keep asserting the background download starts for the
returned item. Add a sibling test where the method returns empty and
`startBackgroundDownload` is not called.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.courses.CoursesStepsViewModelTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. Opening a step
still prefetches the next step's not-yet-offline resources and does not
prefetch resources that are already offline. The current step still downloads
through `downloadResourcesPriority`.

size budget: ~35 changed lines, 3 files

out of scope: no SQL exclusion query, no change to how the current step's already-loaded resources are filtered

---
<a id="m072"></a>

### M072. CommunityTabState: derived planetViewInId/communityViewInId/title used by both fragments

**score 37** · O 39 / D 61 / C 65 · kept by 3/3 plans · raw: L11.08  
**refs:** Opus #44 · Devin R084 · Codex T106, T061

**guardrails:**
- Codex scope: Add distinct planet/community view-in IDs and the existing planet-title fallback to CommunityTabState. Have both hosts consume the appropriate ID while preserving their different titles and filtering identities.

**verified against tree:** Verified at CommunityTabFragment.kt:23/:27 and HomeCommunityDialogFragment.kt:87.

**canonical version — L11-T8 (ClaudeCode), "Hoist community view ids and title into CommunityTabState (roadmap 3+6, moves 10)":**

context: the two community hosts compose data identifiers in the view layer from raw state fields:
`ui/community/CommunityTabFragment.kt:23` builds `"${state.planetCode}@${state.parentCode}"` and :27 picks
the title with `if (state.planetCode.isEmpty()) state.communityName else state.planetCode`.
`ui/community/HomeCommunityDialogFragment.kt:87` builds `"${state.communityName}@${state.parentCode}"`.
The id format is a Voices data contract and should not be repeated in Fragments. A Compose screen
should receive ready values. The shared ViewModel also injects the full `UserRepository`
(`CommunityTabViewModel.kt:24`) only for `getUserModel()` (:33).
files: `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt` (`CommunityTabState`, constructor),
`app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabFragment.kt`,
`app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt`,
`app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModelTest.kt`.
Leave alone: `CommunityPagerAdapter.kt` (its `id` parameter stays a `String`) and `ConfigurationsRepository`.
steps:
1. Add three derived read-only properties in the body of `CommunityTabState`: `planetViewInId`
   (`"$planetCode@$parentCode"`), `communityViewInId` (`"$communityName@$parentCode"`) and `title`
   (`planetCode.ifEmpty { communityName }`).
2. Change the constructor parameter to `userRepository: UserReadRepository` and swap the import.
3. `CommunityTabFragment`: pass `state.planetViewInId` to `CommunityPagerAdapter` and set `binding.title.text = state.title`.
4. `HomeCommunityDialogFragment`: pass `state.communityViewInId` to `CommunityPagerAdapter`. Leave its own title line (:92) as is.
5. Test: assert the three new properties in both existing cases. With a null user, `title` must equal `"community_abc"`.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CommunityTabViewModelTest"` passes, the full
`./gradlew testDefaultDebugUnitTest` stays green. On device: Community tab and the home-community dialog
show the same title, and their Voices tab lists the same posts as before.
size budget: ~25 changed lines, 4 files.
out of scope: do not change `CommunityPagerAdapter` or the Voices filtering, and no repository changes.

---
<a id="m073"></a>

### M073. MyLifeDao.updateVisibility: use the UPDATE row count instead of a pre-SELECT

**score 37** · O 39 / D 46 / C 72 · kept by 3/3 plans · raw: L12.05  
**refs:** Opus #51 · Devin R126 · Codex T076  
**shares files with:** M028

**guardrails:**
- Codex scope: Return an affected-row count from the exact-ID visibility update and fall back to the existing unique-alias resolution only when zero rows match. Preserve the transaction, ambiguous-alias no-op, and already-equal-value behavior.

**verified against tree:** Verified at :45-47. getIdsByIdInternal becomes unused and is deleted.

**canonical version — L12-T5 (Antigravity), "replace pre-update SELECT with UPDATE row-count check in MyLifeDao.updateVisibility (roadmap 1+7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:43-54` implements `updateVisibility(id: String, isVisible: Boolean)` by first executing `getIdsByIdInternal(id)` (`SELECT _id FROM my_life WHERE _id = :id` at lines 34-35) solely to check `if (exactMatches.isNotEmpty())` before executing `updateVisibilityByIdInternal(id, isVisible)` (`UPDATE my_life SET isVisible = :isVisible WHERE _id = :id` at lines 40-41).
Because Room `@Query("UPDATE ...")` functions can return `Int` (the number of updated rows), `updateVisibilityByIdInternal` can return the affected row count directly and `updateVisibility` can short-circuit whenever `updateVisibilityByIdInternal(id, isVisible) > 0`.
This eliminates the pre-update `SELECT` query on every exact-ID hit and removes the unused `getIdsByIdInternal` DAO method.
files:
- touch `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt` (interface `MyLifeDao`, functions `getIdsByIdInternal`, `updateVisibilityByIdInternal`, and `updateVisibility` at lines 34-54)
- touch `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt` (class `MyLifeDaoTest`, visibility update tests at lines 164-244)
- leave `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` untouched; do NOT touch `LifeRepositoryImplTest` — an open PR owns it
steps:
1. In `MyLifeDao.kt`, remove `getIdsByIdInternal(id: String): List<String>` (lines 34-35).
2. Change `suspend fun updateVisibilityByIdInternal(id: String, isVisible: Boolean)` (`MyLifeDao.kt:40-41`) to return `Int`.
3. In `MyLifeDao.updateVisibility(id: String, isVisible: Boolean)` (`MyLifeDao.kt:43-54`), call `if (updateVisibilityByIdInternal(id, isVisible) > 0) return`, then fall back to `val aliasMatches = getIdsByAliasInternal(id)` and `if (aliasMatches.size == 1) updateVisibilityByIdInternal(aliasMatches[0], isVisible)` as before.
4. In `MyLifeDaoTest.kt`, add a test verifying that `updateVisibilityByIdInternal` returns the updated row count (`1` on an existing `_id` and `0` on a missing `_id`) while keeping all existing `updateVisibility_*` tests at lines 164-244 green.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.data.room.dao.MyLifeDaoTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; toggling MyLife item visibility in the Life screen updates the target row in a single SQLite `UPDATE` without a preceding `SELECT` when matched by `_id`.
size budget: ~20 changed lines, 2 files
out of scope: Do not modify `LifeRepositoryImpl.kt` or `LifeRepositoryImplTest`.

---
<a id="m074"></a>

### M074. FreeSpaceWorkerTest: delete the unused Log mock; class-level FileUtils mock

**score 37** · O 38 / D 64 / C 70 · kept by 3/3 plans · raw: L16.04, L17.10  
**refs:** Opus #68 · Devin R080, R045 · Codex T093

**guardrails:**
- Codex scope: Remove unasserted Log instrumentation from FreeSpaceWorkerTest, retaining required FileUtils isolation and targeted cleanup.
- Codex scope: Remove unasserted Log instrumentation from RetryQueueWorkerTest, retaining WorkManager/application mocks.
- Codex scope: Remove unasserted Log instrumentation from DownloadServiceTest, retaining ContextCompat/DownloadUtils mocks.
- Codex scope: Remove unasserted Log instrumentation from DownloadServiceOnDownloadCompleteTest without dropping its required FileUtils/DownloadUtils teardown.
- L17.10 correction from Codex: RetryQueueWorkerTest and DownloadServiceTest already install Log at BeforeClass, not per test. Removing their dead mock remains valid, but the alleged per-test setup savings there are dropped.

**verified against tree:** Verified at :63-77.

**contributor notes:** L17-T10: FreeSpace part

**canonical version — L16-T4 (Codex), "Remove unnecessary logging instrumentation from free-space tests (roadmap 7+8)":**

context: `FreeSpaceWorkerTest.kt:63–77` recreates a file-helper object mock and an Android logging static mock for each test, then globally uninstalls mocks. No test in this file verifies logging, so the logging instrumentation adds no assertion value; the project's JVM tests already allow default framework return values. The latest shard-2 log reports 12 tests taking 7.0 seconds, including 6.6 seconds excluding first-test excess.

files:
- `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt` — `FreeSpaceWorkerTest.setup` and `tearDown`, plus class lifecycle setup/cleanup.
- Leave `createWorker`, filesystem fixtures, progress/count assertions, production cleanup logic, and other test files alone.

steps:
1. Remove the unused logging mock, its stub, and imports that become unused.
2. Install the file-helper object mock once per class and remove exactly that mock in class teardown.
3. Clear that object's answers/call history between tests and re-stub it to the newly created per-test directory; retain fresh worker/collaborator construction and directory cleanup.
4. Preserve every cancellation, batch-boundary, protected-directory, and progress-throttling assertion; ensure teardown cleanup runs even after assertion failure.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.services.FreeSpaceWorkerTest' --rerun-tasks` must pass; compare the class's timing before/after.
- `./gradlew testDefaultDebugUnitTest` must stay green, with all 12 tests retained.
- User-visible storage cleanup behavior remains unchanged, including preserved pending-upload files and accurate freed-byte/progress reporting.

size budget: approximately 35–60 changed lines in 1 file.

out of scope: No production worker, storage policy, timing thresholds, shared fixtures, or Gradle-setting edits. This is a CI-cost improvement, not a roadmap 9/10 migration.

---
<a id="m075"></a>

### M075. SubmissionsRepositoryImpl: LIMIT 1 survey lookup in isStepCompleted; index loop instead of zip in flow dedup

**score 37** · O 38 / D 64 / C 70 · kept by 3/3 plans · raw: L01.03  
**refs:** Opus #88 · Devin R066 · Codex T090  
**shares files with:** M008, M009, M048

**guardrails:**
- Codex scope: Add a nullable LIMIT 1 survey-exam lookup for isStepCompleted while retaining the list API used by courses.
- Codex scope: Compare equal-sized submission lists by index instead of allocating zip pairs, retaining the same id/lastUpdateTime equality rule.

**verified against tree:** Verified at :401 and :81. Micro-optimization.

**canonical version — L01-T3 (Devin), "tighten the submissions read path: LIMIT 1 exam lookup and allocation-free flow dedup (roadmap 1+7)":**

context: two small wastes in the submissions path. `ExamDao.getByStepIdAndType` (`ExamDao.kt:38`) returns `List<StepExam>` but `SubmissionsRepositoryImpl.kt:401` only needs `.firstOrNull()`. Separately, `getSubmissionsFlow` (`SubmissionsRepositoryImpl.kt` ~line 81) does `old.zip(new).all { (o, n) -> ... }` inside `distinctUntilChanged`, allocating a `List<Pair>` on every emission.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt` and `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (`isStepCompleted` ~line 401, `getSubmissionsFlow` ~line 81). Do NOT touch `CoursesRepositoryImpl.kt` — it legitimately uses both full lists at lines 543-544.

steps:
1. Add `getFirstByStepIdAndType(stepId: String, type: String): StepExam?` to `ExamDao` with `SELECT * FROM exams WHERE stepId = :stepId AND type = :type LIMIT 1` (mirror the `getByTypeAndName` LIMIT 1 style already at `ExamDao.kt:43`).
2. In `SubmissionsRepositoryImpl.isStepCompleted`, replace `examDao.getByStepIdAndType(stepId, "surveys").firstOrNull()` with `examDao.getFirstByStepIdAndType(stepId, "surveys")`.
3. In `getSubmissionsFlow`, replace the `zip` comparison with an indexed `for` loop comparing `id` and `lastUpdateTime` pairwise, same semantics.
4. Leave the list-returning `getByStepIdAndType` in place for `CoursesRepositoryImpl`.

acceptance: `./gradlew testDefaultDebugUnitTest` green; step-completion checks and the submissions list behave identically.

size budget: ~25 changed lines, 2 files.

out of scope: no change to `getCourseStepData`, no Room schema changes, no new entities.

---
<a id="m076"></a>

### M076. TransactionSyncManager: replace four printStackTrace calls with Log.e (and rethrow cancel in authenticate)

**score 37** · O 38 / D 64 / C 70 · kept by 3/3 plans · raw: L03.05  
**refs:** Opus #91 · Devin R075 · Codex T092

**guardrails:**
- Codex scope: Replace TransactionSyncManager printStackTrace calls with operation-specific tagged diagnostics, retaining cancellation rethrows and existing failure results. Avoid logging credentials or full payloads.

**verified against tree:** Verified at :135/:187/:318/:465. authenticate() (:134) also swallows CancellationException; add the rethrow while there.

**canonical version — L03-T5 (Copilot·Kimi), "Replace printStackTrace in TransactionSyncManager catch blocks (roadmap 5+8)":**

context: app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt swallows failures with `e.printStackTrace()` at :135 (`authenticate`), :187 (`syncHealthData`), :318 (main sync catch) and :465. This is the tablet-sync core: real sync failures currently bypass logcat tagging entirely, which makes field debugging of the sync/upload workflow (roadmap 5) unnecessarily hard. `android.util.Log` is already imported in this file.

files: `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt` — the four catch blocks only. Leave the `SystemClock` timing instrumentation, the batch loops, and the `CancellationException` rethrow in `syncHealthData` untouched.

steps:
1. Add a `TAG` constant on the existing companion if none exists.
2. Replace each `e.printStackTrace()` with `Log.e(TAG, "<operation> failed", e)`, naming the operation (`authenticate`, `syncHealthData`, etc.).
3. Keep every existing return value and fallback identical (`false`, `null`, duration logging).
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; an offline forced sync logs tagged errors and the sync UI still reports failure exactly as before.

size budget: ~10 changed lines, 1 file.

out of scope: no retry or transaction-scope changes; do not touch `LoginSyncManager` or `SyncRepositoryImpl`.

---
<a id="m077"></a>

### M077. LoginViewModel.saveUsers reads the user itself; LoginActivity passes only name/password/source

**score 36** · O 39 / D 56 / C 68 · kept by 3/3 plans · raw: L11.09  
**refs:** Opus #50 · Devin R103 · Codex T102  
**shares files with:** M049

**guardrails:**
- Codex scope: Have LoginViewModel resolve the current profile/name needed by saveUsers rather than LoginActivity reading profileDbHandler on its behalf. Keep password encryption and Activity-owned UI behavior at their existing boundary.

**verified against tree:** Verified at LoginActivity.kt:677-680.

**canonical version — L11-T9 (ClaudeCode), "Move the saved-user lookup out of LoginActivity into LoginViewModel (roadmap 3, moves 9)":**

context: `ui/sync/LoginActivity.kt:668-682` (`saveUsers`) reads the current user in the Activity through
`profileDbHandler.getUserModel()` (:677), copies `userImage`/`name` out of it and passes them to
`loginViewModel.saveUsers(name, encryptedPassword, source, userProfile, userName)` (:680). The ViewModel
already holds `UserRepository` (`LoginViewModel.kt:23`), so the Activity is doing a data read on the
ViewModel's behalf. Keeping that read in the VM gives the saved-users list one owner.
files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt` (`saveUsers` only),
`app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt` (`saveUsers`, :69-74),
`app/src/test/java/org/ole/planet/myplanet/ui/sync/LoginViewModelTest.kt` (test at :105-116).
Leave alone: the Activity-level `saveUsers(name, password, source)` signature (called from
`GuestLoginExtensions.kt:72` and `utils/AuthUtils.kt:23,53`), the `SecurePrefs.encryptString` step, and
`profileDbHandler.saveUserInfoPref` calls at :601 and :644.
steps:
1. Change `LoginViewModel.saveUsers` to `saveUsers(name: String?, encryptedPassword: String?, source: String)`.
   Inside the existing `viewModelScope.launch(dispatcherProvider.io)` block, read `val user = userRepository.getUserModel()`
   and call `userRepository.upsertSavedUser(name, encryptedPassword, source, user?.userImage ?: "", user?.name)`, then `loadSavedUsers()`.
2. In `LoginActivity.saveUsers`, delete the `profileDbHandler.getUserModel()` lookup and the two derived locals,
   and call `loginViewModel.saveUsers(name, encryptedPassword, source)`.
3. Update the test: stub `coEvery { userRepository.getUserModel() } returns null`, call `saveUsers("guest1", "encrypted", "guest")`
   and verify `upsertSavedUser("guest1", "encrypted", "guest", "", null)`. Add one case where the user model has `userImage = "img"`, `name = "n"` and verify they are forwarded.
acceptance: `./gradlew testDefaultDebugUnitTest --tests "*LoginViewModelTest"` passes, and the full suite stays green.
On device: log in as a member, log out, and the saved-user chip on the login screen shows the
avatar and name. A guest login still adds the guest chip.
size budget: ~25 changed lines, 3 files.
out of scope: no changes to `UserRepository`/`UserSessionManager`, and do not move password encryption.

---
<a id="m078"></a>

### M078. JsonUtils.getDouble/getFloat: drop the fallback that repeats the same parse

**score 35** · O 39 / D 61 / C 60 · kept by 3/3 plans · raw: L04.10  
**refs:** Opus #48 · Devin R087 · Codex T116

**guardrails:**
- Codex scope: Use the typed nullable float/double conversion once and retain zero fallbacks, special values, and numeric-string behavior. Leave integer overflow semantics unchanged.

**verified against tree:** Verified at :46/:51. A cleanup.

**canonical version — L04-T10 (Codex), "Remove repeated failed floating-point parsing in Kotlin JSON accessors (roadmap 1+7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt:46` and line 51 retry floating-point conversion from primitive content after the corresponding typed nullable conversion fails. The floating-point accessors already delegate to those same content conversions, so malformed values are parsed twice; simplifying these two methods removes redundant failure-path work in an already Android-free Kotlin serialization utility, preserving a useful boundary for roadmap 9.

files:

- `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt` — `JsonUtils.getDouble` and `getFloat`; leave `getLong`, `rawLong`, `getInt`, `rawInt`, string/Boolean accessors, and shared empty containers alone.
- `app/src/test/java/org/ole/planet/myplanet/utils/JsonUtilsTest.kt` — `JsonUtilsTest`; extend numeric conversion contract coverage.

steps:

1. Use the typed nullable primitive conversion once in each of the two listed accessors and retain its current zero fallback. Do not replace typed conversion with exception-throwing getters.
2. Preserve support for quoted numeric primitives as well as numeric literals. Keep Long parsing untouched: its typed conversion has a different implementation and is outside this task.
3. Add compact table-driven tests for valid quoted/literal values, malformed strings, missing fields, JSON null, and nonprimitive values. Include exponent notation, quoted NaN/infinity, and overflow-to-infinity cases so existing floating-point outcomes remain explicit.
4. Retain the shared-empty-container identity assertions and the existing nullable accessor tests; remove an import only if this focused change actually makes it unused.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.JsonUtilsTest'`
- `./gradlew testDefaultDebugUnitTest`
- Valid numeric data displays as before; missing and malformed floating-point values still yield zero, while accepted special values retain their existing results, without a second failed parse.

size budget: approximately 35–75 changed lines, 2 files.

out of scope: Do not migrate serializers, change the accepted numeric grammar, alter Int overflow semantics, or touch call sites.

---
<a id="m079"></a>

### M079. SyncActivity: inject @ApplicationScope instead of MainApplication.applicationScope statics

**score 35** · O 38 / D 61 / C 71 · kept by 3/3 plans · raw: L17.02  
**refs:** Opus #89 · Devin R094 · Codex T083  
**shares files with:** M049

**guardrails:**
- Codex scope: Inject the existing ApplicationScope for the post-login watcher instead of reading MainApplication.applicationScope, and use Activity resource lookup where appropriate. Preserve watcher lifetime, cancellation, and reachability behavior.

**verified against tree:** Verified at :649/:662. It is the same singleton.

**canonical version — L17-T2 (ClaudeCode), "Use the injected application scope in SyncActivity instead of `MainApplication` statics (roadmap 4)":**

context: `ui/sync/SyncActivity.kt:645-662` launches the post-login network watcher with `MainApplication.applicationScope.launch { … }` (line 649) and `.launchIn(MainApplication.applicationScope)` (line 662). These reach a static service locator, although Hilt already provides the same scope through the `@ApplicationScope` qualifier (`di/ServiceModule.kt:45`, provided at line 62). Lines 346-347 call `context.getString(...)` on the static `MainApplication.context` (imported at line 47) from inside an Activity instance method, where the Activity's own `getString` is available. With static access, tests cannot substitute the scope, and Hilt cannot see the dependency.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt` only.
- Touch only the `@Inject` field block around lines 105-145, the `when (protocol)` block at lines 345-349, and the login-watcher block at lines 640-662.
- Leave alone: the companion `restartApp()` (lines 830-835, it is static on purpose and `SettingsActivity` calls it), `ProcessUserDataActivity` and `LoginActivity` (open PRs own them), and `MainApplication.isServerReachable`.
steps:
1. Add a field `@Inject @ApplicationScope lateinit var applicationScope: CoroutineScope`, importing `org.ole.planet.myplanet.di.ApplicationScope` and `kotlinx.coroutines.CoroutineScope`.
2. Replace `MainApplication.applicationScope` at lines 649 and 662 with that field. Capture it in a local `val` next to the existing captured locals at lines 641-644, as the block already does for `syncRepository` and the others.
3. At lines 346-347, replace `context.getString(R.string.…)` with the Activity's own `getString(R.string.…)`.
4. Keep the `MainApplication.Companion.context` import only if `restartApp()` still needs it; remove any other import that is now unused.
acceptance:
- `./gradlew testDefaultDebugUnitTest` green;
- `./gradlew assembleDefaultDebug` builds.
- On a device: log in while online and check that a `login_activities` sync still runs after login.
- Enter an unreachable server URL and check that the "couldn't reach local/nation server" message still appears for http and https.
size budget: about 12 changed lines in 1 file.
out of scope: do not restructure the nested `launch` inside `onEach`, and do not move this logic into `LoginViewModel`.

---
<a id="m080"></a>

### M080. FileUploader.uploadDoc: Log.e instead of printStackTrace

**score 34** · O 38 / D 64 / C 67 · kept by 3/3 plans · raw: L03.06  
**refs:** Opus #77 · Devin R074 · Codex T105

**guardrails:**
- Codex scope: Replace FileUploader.uploadDoc stack dumps with a tagged diagnostic containing safe operation context, retaining listener/result behavior and cancellation handling.

**verified against tree:** Verified at FileUploader.kt:64. The catch also reports a failure on cancel, which is not addressed.

**canonical version — L03-T6 (Copilot·Kimi), "Replace printStackTrace in FileUploader's upload catch (roadmap 5+8)":**

context: app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt:64 calls `t.printStackTrace()` when an attachment upload throws, inside `uploadDoc`, which serves both the personals and library-resource upload paths. The failure already surfaces via `params.listener.onSuccess("Unable to upload resource")`; the untagged stack dump adds stderr noise without actionable context like the file name.

files: `app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt` — the catch in `uploadDoc` only. Leave `getHeaderMap`, `onDataReceived`, and the three `uploadAttachment` overloads alone.

steps:
1. Import `android.util.Log` and add a `TAG` to the existing companion object.
2. Replace `t.printStackTrace()` with `Log.e(TAG, "Attachment upload failed: ${params.name}", t)`.
3. Keep the listener fallback invocation byte-identical.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; a failed attachment upload still surfaces the same user-visible failure message and now logs a tagged error including the file name.

size budget: ~5 changed lines, 1 file.

out of scope: no retry, queueing, or upload-format changes; do not touch `UploadRepository` or its implementations.

---
<a id="m081"></a>

### M081. UserEntityEncodeImageTest: class-level Base64/Uri static mocks

**score 34** · O 38 / D 56 / C 72 · kept by 3/3 plans · raw: L16.06  
**refs:** Opus #92 · Devin R104 · Codex T070

**guardrails:**
- Codex scope: Install the needed URI/static instrumentation once per class and reset stubs/calls per test. Continue restoring MainApplication context for every case, including failures; remove unused instrumentation only after checking its verification sites.

**verified against tree:** Verified at :47-61.

**canonical version — L16-T6 (Codex), "Amortize image-encoding test static mocks while restoring application state (roadmap 1+8)":**

context: `UserEntityEncodeImageTest.kt:35–63` instruments three static targets for each test and uninstalls all mocks afterward. The latest shard-2 log reports seven tests taking 5.8 seconds, 5.5 seconds excluding first-test excess. These tests also temporarily replace application context, so reducing setup cost must preserve per-test context restoration.

files:
- `app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt` — `UserEntityEncodeImageTest.setup` and `tearDown`, plus class lifecycle hooks.
- Leave the seven encoding scenarios, temporary-file cleanup, expected encoded values, and production user/image code alone.

steps:
1. Move installation of the existing Base64, URI, and URI-extension static targets into static class setup; uninstall exactly those targets in class teardown.
2. Clear answers and recorded calls on all three targets between tests, then retain each scenario's current URI-specific stubs.
3. Keep annotation-based collaborator initialization, a fresh user instance, and context snapshot/replacement in per-test setup.
4. Restore application context in per-test teardown even on failure; keep file and stream lifecycle assertions intact and remove global mock uninstallation from per-test cleanup.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.model.UserEntityEncodeImageTest' --rerun-tasks` must pass; compare the seven-test class before/after.
- `./gradlew testDefaultDebugUnitTest` must stay green.
- User-visible image upload preparation retains its current file/content-URI encoding, remote-URL skipping, and unreadable-input behavior; context from one test never reaches another.

size budget: approximately 35–65 changed lines in 1 file.

out of scope: No production encoding, Base64 format, context-access, or file-reading refactor. This protects existing data-layer behavior and makes no direct roadmap 9/10 portability claim.

---
<a id="m082"></a>

### M082. TTSManager.formatCsvForSpeech: single buildString

**score 33** · O 39 / D 49 / C 68 · kept by 3/3 plans · raw: L06.09  
**refs:** Opus #61 · Devin R119 · Codex T103

**guardrails:**
- Codex scope: Build the same speech string directly without drop/map/join intermediate lists. Preserve row numbering, ragged-column fallback headers, punctuation, empty rows, and header-only output.

**verified against tree:** Verified at :122-128. It runs once per Read Aloud tap, so it is not a hot path.

**canonical version — L06-T9 (Antigravity), "stream CSV speech formatting into a single StringBuilder in TTSManager.formatCsvForSpeech (roadmap 7+8)":**

context: `TTSManager.kt:119-129` (`formatCsvForSpeech`) formats parsed CSV rows for text-to-speech by
calling `rows.drop(1).mapIndexed { index, row -> ... row.mapIndexed { col, value -> ... }.joinToString(", ") ... }.joinToString(". ")`.
For a CSV with $R$ data rows and $C$ columns, this allocates a sliced `List<Array<String>>`, an outer
`List<String>` of size $R - 1$, $R - 1$ inner `List<String>` instances of size $C$, and $R \times C$
intermediate cell strings before joining. Streaming directly into a single `buildString` block eliminates
all intermediate collection allocations and advances roadmap 9 by keeping `TTSManager`'s pure-Kotlin text
formatting helpers self-contained.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt` (`TTSManager.Companion.formatCsvForSpeech`, lines 119-129)
- touch: `app/src/test/java/org/ole/planet/myplanet/utils/TTSManagerTest.kt` (`TTSManagerTest`)
- leave alone: `TTSManager.Companion.stripMarkdown`, `TTSManager.ensureTts`, and `ResourceViewerFragment`
steps:
1. Open `TTSManager.kt` and locate `fun formatCsvForSpeech(rows: List<Array<String>>): String` at lines 119-129.
2. Replace `if (rows.isEmpty()) return ""` with `if (rows.size <= 1) return ""` so header-only CSVs return `""` immediately without allocating a builder.
3. Replace `rows.drop(1).mapIndexed { ... }.joinToString(". ")` with `buildString` iterating `for (rowIndex in 1 until rows.size)`: append `". "` when `rowIndex > 1`, append `"Row $rowIndex. "`, and iterate `for (col in row.indices)` appending `", "` when `col > 0` followed by `header.getOrElse(col) { "column ${col + 1}" }`, `": "`, and `row[col]`.
4. In `TTSManagerTest.kt`, add a test verifying `formatCsvForSpeech` on a single-row (header-only) CSV and a multi-row CSV with ragged column counts.
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.utils.TTSManagerTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; tapping Read Aloud on a CSV in
`ResourceViewerFragment` speaks the exact same `"Row 1. Header: Value, ..."` text.
size budget: ~25 changed lines, 2 files
out of scope: do not modify `stripMarkdown`, `formatCsvTextForSpeech` CSV parser settings, or `TextToSpeech` lifecycle code.

---
<a id="m083"></a>

### M083. ChatRepositoryImpl.sortChats: precompute the sort key (drop the getLatestRev half)

**score 32** · O 34 / D 64 / C 71 · kept by 3/3 plans · raw: L02.08  
**refs:** Opus #101 · Devin R064 · Codex T079

**guardrails:**
- Codex scope: Decorate each chat with its parsed maximum created/updated timestamp once, stable-sort those keys, and restore the chat list.
- Codex scope: Replace split-to-first-segment in revision generation extraction with substringBefore while retaining nullable, invalid, overflow, and first-winner tie semantics.
- L02.08 part dropped by Opus: getLatestRev half: maxByOrNull evaluates the selector once per element, and there is at most one row per _id.
- L02.08 correction from Codex: maxByOrNull already evaluates its selector once per element; only the split allocation is redundant there. Comparator timestamp parsing is the repeated work.

**verified against tree:** sortChats parses dates per comparison (:145). The getLatestRev half is wrong: maxByOrNull already evaluates the selector once, and there is at most one row per _id.

**contributor notes:** L02-T8: sortChats half

**canonical version — L02-T8 (Copilot·Grok), "precompute chat sort and revision keys (roadmap 7+9)":**

context: ChatRepositoryImpl.sortChats (lines 144-147) calls createdDate.toLongOrNull and updatedDate.toLongOrNull inside the sort comparator.
A history open therefore parses those strings once per comparison, not once per chat.
getLatestRev (lines 150-152) calls split("-") inside maxByOrNull for every revision compared.
ChatRepositoryImpl has no android.* import; the only Android-adjacent import is androidx.annotation.VisibleForTesting.
Keeping these two functions free of android.* is the KMP chat core (roadmap 9). Do not add an android.* import.
files: app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt, functions sortChats and getLatestRev.
Do not edit ChatDao, ChatViewModel, or any chat adapter.
steps:
1. In sortChats, map each chat once to maxOf(createdDate?.toLongOrNull() ?: 0L, updatedDate?.toLongOrNull() ?: 0L), then sort that decorated list descending.
2. Equal keys must keep today's stable order from sortedByDescending.
3. In getLatestRev, parse the numeric prefix once per revision with indexOf('-') and substring, not split, then select with maxByOrNull on that key.
4. Keep maxByOrNull's first-maximum tie break. A null revision still counts as 0.
5. Do not change the empty-user early return in getChatHistoryForUser.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Open chat history for a user with several chats, and resolve the latest rev of a document that has more than one rev. History order is unchanged, and the stored latest rev string is the same document rev as before.
size budget: ~25 changed lines, 1 file
out of scope: no pagination changes, and no new chat dependency.

---
<a id="m084"></a>

### M084. Move the two @Module classes declared outside di/ into one di/ module

**score 31** · O 38 / D 64 / C 60 · kept by 3/3 plans · raw: L11.07  
**refs:** Opus #79 · Devin R068 · Codex T115

**guardrails:**
- Codex scope: Move DeviceNameModule and UserDataUploadSchedulerModule bindings into an appropriately scoped di module, retaining qualifiers, scopes, implementations, and graph uniqueness; remove obsolete module annotations/imports from the implementation files.

**verified against tree:** Verified: UserDataUploadScheduler.kt and DeviceNameProvider.kt hold the only @Module classes outside di/. The CLAUDE.md module count goes stale.

**canonical version — L11-T7 (ClaudeCode), "Move the two Hilt modules declared outside di/ into one di/ module (roadmap 4)":**

context: two `@Module` classes sit in non-DI packages: `UserDataUploadSchedulerModule` at
`services/UserDataUploadScheduler.kt:67-75` and `DeviceNameModule` at `utils/DeviceNameProvider.kt:24-30`.
The bindings graph is therefore not discoverable from `di/`, and the `utils`/`services` files depend
on `dagger.*` annotations they otherwise don't need, which blocks moving them to a platform-free
module. `grep -rn "DeviceNameModule\|UserDataUploadSchedulerModule" app/src` finds no other references,
so no `@UninstallModules` will break.
files: `app/src/main/java/org/ole/planet/myplanet/services/UserDataUploadScheduler.kt`,
`app/src/main/java/org/ole/planet/myplanet/utils/DeviceNameProvider.kt`,
new `app/src/main/java/org/ole/planet/myplanet/di/PlatformBindingsModule.kt`.
Leave alone: `di/ServiceModule.kt`, `di/RepositoryModule.kt`, `di/NetworkModule.kt` (open PRs), and the
`UserDataUploadScheduler` / `DeviceNameProvider` interfaces and their `@Inject` implementations.
steps:
1. Create `di/PlatformBindingsModule.kt`: `@Module @InstallIn(SingletonComponent::class) abstract class PlatformBindingsModule`
   with the two existing `@Binds @Singleton` functions copied unchanged (`bindUserDataUploadScheduler(impl: WorkManagerUserDataUploadScheduler)`,
   `bindDeviceNameProvider(impl: SharedPrefDeviceNameProvider)`).
2. Delete `UserDataUploadSchedulerModule` from `UserDataUploadScheduler.kt` and `DeviceNameModule` from `DeviceNameProvider.kt`.
3. Remove the now-unused `dagger.Binds`, `dagger.Module`, `dagger.hilt.InstallIn` and `dagger.hilt.components.SingletonComponent`
   imports from both files. Keep `javax.inject.*` and `dagger.hilt.android.qualifiers.ApplicationContext`.
acceptance: `./gradlew assembleDefaultDebug` builds (Hilt validates the graph) and `./gradlew testDefaultDebugUnitTest`
stays green. On device: Settings → user upload (UserData upload) still enqueues and reports progress, and
submissions still carry the device name.
size budget: ~45 changed lines, 3 files.
out of scope: do not rename the interfaces or implementations, and do not touch the other di/ modules or `CLAUDE.md`.

---
<a id="m085"></a>

### M085. VoicesAdapter.getCurrentLeader: first-wins name map instead of a linear scan

**score 31** · O 38 / D 61 / C 69 · kept by 3/3 plans · raw: L01.06  
**refs:** Opus #93 · Devin R095 · Codex T096  
**shares files with:** M044

**guardrails:**
- Codex scope: Build an adapter-lifetime index from leader name to the first matching leader, including nullable names where the current equality permits them. Retain the userModel-null guard.
- L01.06 correction from Codex: associateBy keeps the last duplicate and excluding null names changes current behavior; the shipped index preserves first-match and null equality.

**verified against tree:** Verified at :592. The leader list is tiny, and associateBy would change first-match semantics.

**canonical version — L01-T6 (Devin), "index `leadersList` by name in `VoicesAdapter` (roadmap 7)":**

context: `VoicesAdapter.getCurrentLeader` (~lines 590-599) linearly scans `leadersList` comparing `leader.name == news.userName` on every bind of a news row where `userModel == null`. On the voices feed this runs once per bound row: O(rows × leaders).

files: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt` — constructor field `leadersList` (~line 72) and `getCurrentLeader`. Do NOT touch `leadersList` construction in the caller, `VoicesLabelManager`, or any other adapter method.

steps:
1. Add a private `leadersByName: Map<String, UserEntity>` built lazily once from `leadersList` (`associateBy { it.name }` with `it.name` nulls excluded or keyed safely — `UserEntity.name` is nullable; filter or use `filterNotNull`+`associate`).
2. Rewrite `getCurrentLeader` to `leadersByName[news.userName]` preserving the `userModel == null` guard and the `null` return.
3. If `leadersList` is ever reassigned post-construction, reset the map at that assignment site; otherwise a `by lazy` field suffices.

acceptance: `./gradlew testDefaultDebugUnitTest` green; voices feed still shows the leader context for community-authored posts.

size budget: ~15 changed lines, 1 file.

out of scope: no changes to news payload logic, markdown caches, or chip rendering.

---

## Wave 4: hygiene and micro-cleanups (batch these)

<a id="m086"></a>

### M086. Sha256Utils.getCheckSumFromFile: 64 KiB read buffer

**score 30** · O 39 / D 49 / C 62 · kept by 3/3 plans · raw: L02.10  
**refs:** Opus #43 · Devin R115 · Codex T114

**guardrails:**
- Codex scope: Increase the SHA-512 file-reading buffer from DEFAULT_BUFFER_SIZE to a modest larger constant such as 64 KiB. Retain the algorithm, digest representation, and missing-file behavior; compare representative file throughput before claiming a speedup.

**verified against tree:** Verified at :16. It runs once per update check.

**canonical version — L02-T10 (Copilot·Grok), "enlarge the checksum read buffer (roadmap 7+9)":**

context: Sha256Utils.getCheckSumFromFile reads the whole file through a ByteArray(DEFAULT_BUFFER_SIZE) loop. That constant is 8 KiB, so a large APK or resource pays one read per 8 KiB while MessageDigest is updated.
ConfigurationsRepositoryImpl.checkCheckSum (lines 256-257) is a caller: it hashes the resolved download and compares the hex string.
Sha256Utils has zero android.* imports. A larger buffer, with the algorithm left alone, is what the platform-free checksum core keeps (roadmap 9).
The digest is SHA-512 despite the class name. Checksum equality depends on that algorithm.
files: app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt, function getCheckSumFromFile only.
Do not edit ConfigurationsRepositoryImpl.kt.
steps:
1. Replace the 8 KiB DEFAULT_BUFFER_SIZE array with a 64 KiB buffer.
2. Keep MessageDigest.getInstance("SHA-512").
3. Keep the existing hex encoding and the null return on any exception.
4. Do not reuse one MessageDigest instance across calls. Each call must still create its own.
5. Do not add an android.* import, and do not rename the class or the method.
acceptance: ./gradlew testDefaultDebugUnitTest stays green.
Checksum one known local file before and after the buffer change. The hex string is identical, and a missing file still returns null.
size budget: ~2 changed lines, 1 file
out of scope: do not switch the algorithm to SHA-256, and do not add a new call site that hashes on the main thread.

---
<a id="m087"></a>

### M087. VoicesLabelManager.updateAddLabelVisibility: cache the LABELS value set

**score 30** · O 39 / D 46 / C 68 · kept by 3/3 plans · raw: L06.10  
**refs:** Opus #62 · Devin R130 · Codex T100

**guardrails:**
- Codex scope: Cache the constant label-value set, pass already-resolved labels into visibility calculation, and short-circuit too-short lists before building a used-label set. Preserve permission checks and duplicate-label semantics.

**verified against tree:** Verified at :118-119.

**canonical version — L06-T10 (Antigravity), "short-circuit label set allocations in VoicesLabelManager.updateAddLabelVisibility and cache label values (roadmap 7+8)":**

context: `VoicesLabelManager.kt:108-122` (`updateAddLabelVisibility`) is called from `showChips` on every
RecyclerView row bind whose label state changed, and unconditionally allocates two `Set<String>` instances:
`val usedLabels = voice?.labels?.toSet() ?: emptySet()` and `val labels = Constants.LABELS.values.toSet()`,
then checks `usedLabels.containsAll(labels)`. Because `Constants.LABELS` is a static map (`Constants` lines
32-36), converting its `.values` to a new `Set` on every row bind is redundant, and converting
`voice.labels` to a `Set` is unnecessary whenever `labels.size < Constants.LABELS.size` (which is true for
the vast majority of Voices posts that have 0, 1, or 2 labels). Caching the constant label-value set in
`VoicesLabelManager.Companion` and short-circuiting on list size removes both allocations from the common
RecyclerView bind path.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt` (`VoicesLabelManager.showChips`, `VoicesLabelManager.updateAddLabelVisibility`, and `VoicesLabelManager.Companion`, lines 64-92 and 108-132)
- touch: `app/src/test/java/org/ole/planet/myplanet/services/VoicesLabelManagerTest.kt` (`VoicesLabelManagerTest`)
- leave alone: `VoicesLabelManager.setupAddLabelMenu`, `VoicesLabelManager.launchLabelWrite`, and `Constants`
steps:
1. Open `VoicesLabelManager.kt` and add `private val allLabelValues: Set<String> = Constants.LABELS.values.toSet()` inside `companion object` (lines 128-132).
2. Change `updateAddLabelVisibility(binding: RowNewsBinding, voice: News?, canManageLabels: Boolean)` (lines 108-122) to accept `labels: List<String>` directly instead of `voice: News?`, and update its call site in `showChips` (line 91) to pass `labels` (already resolved as `val labels = voice.labels ?: emptyList()` at line 65).
3. In `updateAddLabelVisibility`, replace `val usedLabels = ...` and `val labels = Constants.LABELS.values.toSet()` with `val allUsed = labels.size >= allLabelValues.size && labels.toSet().containsAll(allLabelValues)` and set `binding.btnAddLabel.visibility = if (allUsed) View.GONE else View.VISIBLE`.
4. In `VoicesLabelManagerTest.kt`, add a test verifying `btnAddLabel.visibility` when `voice.labels` has duplicate entries equal in count to `Constants.LABELS.size` (such as `listOf("offer", "offer", "offer")` staying `View.VISIBLE`) versus all distinct labels (`View.GONE`).
5. Run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.services.VoicesLabelManagerTest"` and confirm all tests pass.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; Voices feed items show or hide the Add Label
button identically based on whether all available labels are attached.
size budget: ~25 changed lines, 2 files
out of scope: do not modify `Constants`, `setupAddLabelMenu`, or chip view creation in `showChips`.

---
<a id="m088"></a>

### M088. test_timing_summary.py --test-sources: index basenames once

**score 30** · O 38 / D 49 / C 71 · kept by 3/3 plans · raw: L16.10  
**refs:** Opus #90 · Devin R118 · Codex T084

**guardrails:**
- Codex scope: Build one source filename index and cache content reads within a diagnostic invocation. Preserve traversal precedence for duplicate filenames and all existing warnings/thresholds. This targets the explicit test-sources option rather than claiming the current workflow enables it.

**verified against tree:** Verified at :244-256, but CI never passes --test-sources.

**canonical version — L16-T10 (Codex), "Index test source filenames once during timing diagnostics (roadmap 7+8)":**

context: `.github/scripts/test_timing_summary.py:243–252` walks the entire source tree separately for every qualifying class when source-based mock diagnostics are requested, and lines 255–257 reread matching sources. This makes the existing diagnostic option scale as candidates times source-tree size; a filename index and per-path read cache remove repeated traversal and reads. The inspected workflow does not currently enable this option, so this improves explicit diagnostic runs rather than promising a reduction in today's CI wall time.

files:
- `.github/scripts/test_timing_summary.py` — `main`, limited to source lookup/content reuse inside the existing optional diagnostic branch.
- Leave `_first_test_excess`, `_parse_time`, XML aggregation, ranking, thresholds, and the nested `_extract_bodies` heuristic unchanged; do not edit any workflow file.

steps:
1. Build a filename-to-path index with one source-tree traversal only when source-based diagnostics are requested; preserve the current first-encountered match for duplicate basenames.
2. Use that index for qualified class lookups, retaining nested-class outer-name handling and missing-source behavior.
3. Cache successfully read text by resolved path so multiple qualifying classes sharing a source do not reread it; retain current decoding and unreadable-file handling.
4. Capture baseline and changed stdout/stderr/exit status against identical result XML, both with and without source diagnostics. Include missing sources, duplicate basenames, and nested classes in temporary validation fixtures; keep those fixtures outside tracked files.
5. Profile a diagnostic run with multiple qualifying classes and verify one source-tree traversal; do not change or add Python dependencies.

acceptance:
- `python3 -m py_compile .github/scripts/test_timing_summary.py` must pass.
- `./gradlew testDefaultDebugUnitTest` must stay green and produce the results consumed below.
- `python3 .github/scripts/test_timing_summary.py app/build/test-results/testDefaultDebugUnitTest --shard 1/1 --warn-over 400 --test-sources app/src/test/java` must exit successfully; its report must match the baseline for identical XML/source inputs. The result directory here is generated by the preceding Gradle command.
- `python3 -m cProfile -s ncalls .github/scripts/test_timing_summary.py app/build/test-results/testDefaultDebugUnitTest --test-sources app/src/test/java` must confirm that source lookup no longer restarts a tree walk for each qualifying class.
- Maintainers see the same timing rankings and mock-churn hints with less diagnostic filesystem work; app behavior is unchanged.

size budget: approximately 30–60 changed lines in 1 file; no tracked fixture or helper files.

out of scope: No threshold increases, dropped slow-test warnings, heuristic rewrites, new CI steps, or dependency changes. This supports later measured refactors and adds no roadmap 9/10 prerequisite.

---
<a id="m089"></a>

### M089. ChatConversationPaginator: Gson parse on the default dispatcher

**score 30** · O 37 / D 49 / C 75 · kept by 3/3 plans · raw: L17.04  
**refs:** Opus #95 · Devin R106 · Codex T059

**guardrails:**
- Codex scope: Replace the IO dispatcher for already-in-memory conversation JSON parsing with the injected default dispatcher. Keep pagination state publication on its owner thread and preserve malformed/empty input behavior.

**verified against tree:** Verified at :20. The malformed-JSON test already exists.

**canonical version — L17-T4 (ClaudeCode), "Parse chat conversations on the default dispatcher (roadmap 7, moves 9)":**

context: `ui/chat/ChatConversationPaginator.kt:20` parses the stored conversation JSON with `withContext(dispatcherProvider.io) { GsonUtils.gson.fromJson(newsConversations, Array<Conversation>::class.java) }`. That is CPU-bound deserialisation of a string already in memory, with no I/O, so it occupies an IO-pool thread that disk and network work need. The class has no `android.*` imports, which makes it one of the easy candidates for the KMP core. It should state the right dispatcher now.
files:
- `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginator.kt` (`parseAndBuildInitialPage`, line 20)
- `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginatorTest.kt`
- Leave alone: `ChatDetailFragment` and `ChatRepositoryImpl` (open PRs own them), and `ChatViewModel`.
steps:
1. At line 20, change `dispatcherProvider.io` to `dispatcherProvider.default`.
2. The test already builds a `TestDispatcherProvider(testDispatcher)` (lines 22-23). Add one case where the malformed-JSON path returns an empty page, if no such case exists yet.
3. Remove the `kotlinx.coroutines.Dispatchers` import from the test if it becomes unused.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ChatConversationPaginatorTest"` passes; the full suite stays green.
- On a device, opening a shared chat from Voices still shows the last 20 exchanges and the "load more" row.
size budget: about 15 changed lines in 2 files.
out of scope: do not switch from Gson to kotlinx-serialization here, and do not change the page size.

---
<a id="m090"></a>

### M090. CommunityServicesViewModel: TeamsShareRepository + TeamsMembersRepository instead of TeamsRepository

**score 30** · O 33 / D 64 / C 71 · kept by 3/3 plans · raw: L08.07  
**refs:** Opus #109 · Devin R065 · Codex T085  
**shares files with:** M042, M060, M100

**guardrails:**
- Codex scope: Expose getTeamLinks through the share contract and inject the share and member read contracts into CommunityServicesViewModel. Keep current list membership semantics and existing Hilt implementation bindings.

**verified against tree:** Premise verified. It needs `override` on TeamsRepository.kt:54, which the task forbids.

**canonical version — L08-T7 (Copilot·Grok), "stop community services depending on the fat teams repository (roadmap 1+4+9)":**

context: `CommunityServicesViewModel` (`CommunityServicesViewModel.kt:15-16`)
injects `TeamsRepository`, the write/sync aggregate. It only calls
`getTeamLinks()` (`line 28`, declared at `TeamsRepository.kt:54`) and
`isMember` (`line 32`). `isMember` is already on `TeamsMembersRepository`
(`TeamsMembersRepository.kt:7`). `getTeamLinks` is not on
`TeamsShareRepository` (`TeamsShareRepository.kt:5-8`), even though community
links are share data. Both narrow interfaces are already bound to
`TeamsRepositoryImpl` (`RepositoryModule.kt:201` and `:213`). The viewmodel
therefore sees leave-team, finance, and notification methods it must not call.
Moving the read onto the narrow ports is what a platform-free core can
implement without dragging the fat Android repository along.

files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsShareRepository.kt`.
`app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt`.
`app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModelTest.kt`.
Do not touch `TeamsRepository.kt`, `TeamsRepositoryImpl.kt`,
`RepositoryModule.kt`, `TeamsMembersRepository.kt`, or `ChatViewModel.kt`
(it already depends on `TeamsShareRepository` and does not call `getTeamLinks`).

steps: 1. Add `suspend fun getTeamLinks(): List<MyTeam>` to
`TeamsShareRepository`. Import `MyTeam`. Do not give it a body.
`TeamsRepository` already extends `TeamsShareRepository` and already declares
the same method, so the existing `override` in `TeamsRepositoryImpl` satisfies
both. Do not edit the impl. 2. Change `CommunityServicesViewModel`'s
constructor to take `TeamsShareRepository` and `TeamsMembersRepository`.
`loadTeamLinks` calls `getTeamLinks` on the share port. `isMember` calls
`isMember` on the members port. No other behavior change. 3. Update
`CommunityServicesViewModelTest` to pass two mocks. Keep the three existing
tests: init populates `teamLinks`, `isMember` forwards user id and team id,
and a null user id is forwarded unchanged. 4. Run the viewmodel test.
`ChatViewModelTest` must still compile without edits because it never calls
the new method.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.community.CommunityServicesViewModelTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. The community
services screen still lists link teams and still answers membership checks.

size budget: ~35 changed lines, 3 files

out of scope: do not move `getTeamById` or any other fat-interface method; do not add a Hilt binding

---
<a id="m091"></a>

### M091. SubmissionsRepositoryExporter: stop silently swallowing the two catch{null} blocks

**score 30** · O 33 / D 71 / C 64 · kept by 3/3 plans · raw: L13.04  
**refs:** Opus #110 · Devin R035 · Codex T111  
**shares files with:** M092, M107

**guardrails:**
- Codex scope: Replace the two silent outer export catches with safe tagged diagnostics and retain their null result, document closure, and output-file cleanup behavior.
- L13.04 part dropped by Opus: Batch-exam-lookup half: the N+1 does not exist (generateMultipleSubmissionsPdf already batches).
- L13.04 correction from Codex: Dropped the alleged N exam lookups in generateMultipleSubmissionsPdf: that function does not query examDao at all; the one getById at line 79 is in generateSubmissionPdf, which the proposal excludes. Only the independently verified silent catches remain.

**verified against tree:** Only the logging half survives. The batch-exam-lookup half targets an N+1 that does not exist, because the multi-submission path already batches via getQuestionsByExam.

**contributor notes:** L13-T4: logging half only

**canonical version — L13-T4 (Devin), "Batch exam lookups and log swallowed catches in `SubmissionsRepositoryExporter` (roadmap 1, 7)":**

**context:** `generateMultipleSubmissionsPdf` calls `examDao.getById(it)` once per submission and recomputes `getExamId(submission.parentId)` (which re-splits `parentId` on `@`) even though `getQuestionsByExam` already builds a deduplicated `examIds` map (`~:299-303`). Two `catch (e: Exception) { null }` blocks silently discard failures and are also misindented (`~:130-132`, `~:242-244`). Evidence: `ExamDao.getByIds(ids: List<String>)` already exists at `ExamDao.kt:11-13` but is not used here.

**files:** `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt` — `generateMultipleSubmissionsPdf`, `getExamId`, `getQuestionsByExam`, and the two `catch { null }` sites. Leave alone: `ExamDao.kt` (the bulk accessor already exists), `drawMultilineText`'s algorithm, the PDF layout constants.

**steps:**
1. In `generateMultipleSubmissionsPdf`, compute the distinct exam ids up front by mapping submissions through `getExamId(parentId)` once, then call `examDao.getByIds(ids)` a single time and index the result into `examsById: Map<String, StepExam>`.
2. Replace the per-submission `examDao.getById(examId)` call with `examsById[examId]`.
3. In both `catch (e: Exception) { null }` sites, fix the indentation and add `Log.w(TAG, "…", e)` (or `Log.d`) before returning `null` so the failure is not silent; add a `TAG` and `import android.util.Log` if absent.
4. Keep `getQuestionsByExam`'s existing `questionDao.getByExamIds(examIds)` batching untouched.

**acceptance:** `./gradlew :app:compileDefaultDebugKotlin` and `./gradlew testDefaultDebugUnitTest` green. User-visible: PDF export of N submissions issues 1 exam query instead of N, and export failures appear in logcat.

**size budget:** ~35 changed lines, 1 file.

**out of scope:** no changes to `generateSubmissionPdf`'s single-exam path, no `drawMultilineText` rewrite, no new DAO methods.

---
<a id="m092"></a>

### M092. SubmissionsRepositoryExporter.getExamId: substringBefore('@')

**score 29** · O 39 / D 46 / C 65 · kept by 3/3 plans · raw: L04.09, L06.08  
**refs:** Opus #38 · Devin R124, R108 · Codex T107  
**shares files with:** M091, M107

**guardrails:**
- Codex scope: Use nullable substringBefore for getExamId, preserving null, empty, plain-ID, leading-delimiter, and multiple-delimiter results.
- L06.08 correction from Codex: Dropped the additive Paint.measureText(space)+measureText(word) rewrite: width additivity is not established for shaped text and can change wrapping. Only the verified parent-ID split removal is retained.

**verified against tree:** Verified at :307-312.

**contributor notes:** L06-T8: getExamId part

**canonical version — L04-T9 (Codex), "Extract export exam IDs without splitting entire parent strings (roadmap 1+7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:307` checks for an at-sign and then splits the complete parent string merely to keep its first component at lines 308–309. The helper runs while collecting unique exam IDs at line 302 and again for each submission at line 200, creating unused suffix strings and lists during large reports.

files:

- `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt` — `SubmissionsRepositoryExporter.getExamId` only; leave question batching, PDF drawing, filenames, and date formatting alone.
- `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt` — `SubmissionsRepositoryExporterTest`; exercise ID extraction through existing `getQuestionsByExam` coverage.

steps:

1. Replace the contains-and-split branch with null-safe extraction before the first at-sign, returning the entire original parent ID when no delimiter exists.
2. Preserve null, empty-string, leading-at-sign, trailing-at-sign, and multiple-at-sign results exactly. Do not introduce trimming or discard blank IDs as part of this task.
3. Extend the existing question-batching tests with those delimiter edge cases and a long multi-component parent ID; capture the IDs passed to the mocked DAO.
4. Retain the existing assertion that repeated references to the same exam produce one batched lookup and keep first-seen distinct-ID ordering.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.SubmissionsRepositoryExporterTest'`
- `./gradlew testDefaultDebugUnitTest`
- Export single and combined submission reports containing course-qualified exam parents: question text, answers, and submission ordering remain unchanged.

size budget: approximately 30–65 changed lines, 2 files.

out of scope: Do not change PDF layout, database queries, blank-ID policy, exception handling, or identifier formats.

---
<a id="m093"></a>

### M093. ExamTakingFragment: parse the constant 'Other' choice once; reuse the inflater

**score 29** · O 38 / D 64 / C 53 · kept by 3/3 plans · raw: L01.05  
**refs:** Opus #74 · Devin R067 · Codex T123

**guardrails:**
- Codex scope: Construct the constant Other choice once and reuse it read-only or copy before mutation; cache an inflater only for the current fragment view/context lifetime.

**verified against tree:** Verified at :579/:599/:609. Prefer a per-call local over an activity-bound lazy.

**canonical version — L01-T5 (Devin), "hoist the constant `"Other"` choice parse and per-button inflater in `ExamTakingFragment` (roadmap 7)":**

context: `ExamTakingFragment` parses the identical JSON literal `"""{"text":"Other","id":"other"}"""` through Gson every time a question renders an "Other" option — once in `showCheckBoxes` (~line 579) and once in `selectQuestion` (~line 599). `addRadioButton` (~line 608) also calls `LayoutInflater.from(activity)` for every radio button it inflates.

files: `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt` — `showCheckBoxes`, `selectQuestion`, `addRadioButton`. Do NOT touch `addCompoundButton`, `BaseExamFragment.kt`, or the answer-restoration block (~line 815+) that parses per-answer choice JSON — that data is per-answer and must stay dynamic.

steps:
1. Add a private lazy/val holding the parsed `JsonObject` for the `"Other"` choice (a fresh copy or `.deepCopy()` if Gson mutation semantics require it — prefer constructing one immutable instance and passing it read-only to `addCompoundButton`).
2. Replace both `GsonUtils.gson.fromJson(...)` calls with that field.
3. Hoist `LayoutInflater.from(activity)` in `addRadioButton` to a fragment-level lazy inflater.
4. Remove no longer-needed local variables.

acceptance: `./gradlew testDefaultDebugUnitTest` green; exams and surveys still render the "Other" option as a checkbox or radio matching `question.hasOtherOption`.

size budget: ~15 changed lines, 1 file.

out of scope: no UI redesign, no changes to answer submission/serialization.

---
<a id="m094"></a>

### M094. ServerReachabilityWorkerTest: class-level mocks, keep the Log verifies

**score 28** · O 38 / D 43 / C 73 · kept by 3/3 plans · raw: L16.07  
**refs:** Opus #82 · Devin R133 · Codex T067

**guardrails:**
- Codex scope: Install logging/network/application instrumentation once per class, reset answers and calls per case, and preserve cancellation-is-silent and failure-is-logged assertions. Replace global teardown with target-specific cleanup.

**verified against tree:** Verified, but it has only 4 tests.

**canonical version — L16-T7 (Codex), "Hoist reachability-worker test mocks while retaining logging assertions (roadmap 5+8)":**

context: `ServerReachabilityWorkerTest.kt:47–67` reinstalls a logging static and two object mocks before every test. Here logging instrumentation is necessary: lines 94 and 125–127 verify cancellation is silent and ordinary failure is logged. The latest shard-2 log reports four tests taking 4.1 seconds with a 0.90-second median test time.

files:
- `app/src/test/java/org/ole/planet/myplanet/services/ServerReachabilityWorkerTest.kt` — `ServerReachabilityWorkerTest.setUp`, `tearDown`, and its existing companion object.
- Leave `createWorker`, all cancellation/retry/upload assertions, and production network/worker logic alone.

steps:
1. Install the three existing mock targets once in static class setup and uninstall exactly them in class teardown.
2. Reset their answers and recorded calls between tests, retaining the per-test log stub, connectivity default, and all instance collaborator defaults.
3. Preserve the test-specific reachability sequence and network-down override; neither may leak into the next test.
4. Keep the existing zero-log and one-log verifications unchanged, so cancellation and ordinary exceptions remain distinguishable.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.services.ServerReachabilityWorkerTest' --rerun-tasks` must pass; capture before/after timings.
- `./gradlew testDefaultDebugUnitTest` must stay green, with all four tests retained.
- Background reachability checks still skip unavailable networks, propagate cancellation, and retry/log ordinary failures without starting an obsolete upload.

size budget: approximately 30–55 changed lines in 1 file.

out of scope: Do not remove verified logging, change cooldowns, or alter worker scheduling/network behavior. This keeps consolidation tests cheap without scheduling roadmap 9/10 work.

---
<a id="m095"></a>

### M095. Feedback.serializeFeedback / HealthExamination.formatConditions: stop printStackTrace

**score 27** · O 37 / D 64 / C 63 · kept by 3/3 plans · raw: L03.10  
**refs:** Opus #96 · Devin R073 · Codex T113  
**shares files with:** M041

**guardrails:**
- Codex scope: Replace Feedback and HealthExamination serializer stack dumps with controlled reporting while retaining their malformed-message/condition fallbacks. Keep Android reporting outside portable model logic when introducing a reporting seam.

**verified against tree:** Verified at Feedback.kt:103 and HealthExamination.kt:69. Its justification contradicts itself: adding android.util.Log makes these models less platform-free, so use silent fallbacks.

**canonical version — L03-T10 (Copilot·Kimi), "Replace printStackTrace in the Feedback and HealthExamination serializers (roadmap 1+8, supports 9)":**

context: app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt:103 (`serializeFeedback`) and app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt:69 (`formatConditions`) print stack traces on malformed JSON input. These are data-layer models squarely inside the roadmap-1 cleanup and the roadmap-9 platform-free core; untagged stderr dumps are exactly the pattern the cleanup removes, and both functions already have safe fallbacks (`null` messages JSON and `""` respectively) that make the trace pure noise.

files: `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt` (`serializeFeedback` catch only) and `app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt` (`formatConditions` catch only). Leave `cachedMessages`/`messageList`, `fromJson`, and `serialize` untouched in both files.

steps:
1. In `serializeFeedback`, replace `err.printStackTrace()` with `Log.w(TAG, "Failed to parse feedback messages", err)`, adding the import and a `TAG`; keep the `null` fallback so `messages` is simply omitted.
2. In `formatConditions`, replace `e.printStackTrace()` with `Log.w(TAG, "Failed to parse conditions", e)`; keep the `""` fallback.
3. Run the existing model tests (`FeedbackTest` and any `HealthExamination` tests) and confirm identical serialization for valid and malformed input.
4. Run the full unit test task.

acceptance: `./gradlew testDefaultDebugUnitTest` green; feedback whose `messages` field contains malformed JSON still serializes and uploads without the `messages` key instead of crashing or dumping to stderr.

size budget: ~8 changed lines, 2 files.

out of scope: no kotlinx-serialization migration of these models; no behavior change for valid payloads.

---
<a id="m096"></a>

### M096. StableIdGenerator: local NO_ID constant, drop the RecyclerView import

**score 26** · O 38 / D 49 / C 69 · kept by 3/3 plans · raw: L17.05  
**refs:** Opus #85 · Devin R116 · Codex T099

**guardrails:**
- Codex scope: Replace the RecyclerView.NO_ID reference with a local -1L constant and remove the import. Preserve FNV hashing, overflow behavior, synchronized fallback allocation, and all caller-visible IDs.

**verified against tree:** Verified. The value (-1L) is unchanged.

**canonical version — L17-T5 (ClaudeCode), "Make StableIdGenerator platform-free (roadmap 8, moves 9)":**

context: `utils/StableIdGenerator.kt` is pure Kotlin: an FNV-1a hash and a `WeakHashMap` fallback. Its only Android dependency is `import androidx.recyclerview.widget.RecyclerView` (line 3), used just for the constant `RecyclerView.NO_ID` (line 11). That one import keeps an otherwise portable ID helper inside the Android-only layer. The callers (`TeamsAdapter.kt:41`, `VoicesAdapter.kt:214`, `CoursesAdapter.kt:100`, `ResourcesAdapter`) compare against `RecyclerView.NO_ID` themselves, so a local constant with the same value (`-1L`) is fully compatible.
files:
- `app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt` (`generateStringId`, lines 3 and 11)
- `app/src/test/java/org/ole/planet/myplanet/utils/StableIdGeneratorTest.kt` (lines 3 and 11-12)
- Leave alone: all four adapters, which open PRs own and which need no change.
steps:
1. Add `const val NO_ID = -1L` to `StableIdGenerator`, return it from `generateStringId` for null or empty keys, and delete the RecyclerView import.
2. In the test, replace the `RecyclerView.NO_ID` assertions (lines 11-12) with `StableIdGenerator.NO_ID`. Add one assertion that `StableIdGenerator.NO_ID == RecyclerView.NO_ID`, keeping the RecyclerView import in the test only, so the two values cannot drift apart.
3. Confirm that `grep -n "^import android" app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt` prints nothing.
acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*StableIdGeneratorTest"` passes; the full suite stays green.
- Lists that use stable IDs (teams, courses, resources, voices) still scroll and update without flicker.
size budget: about 8 changed lines in 2 files.
out of scope: no changes to the hash or the fallback-ID scheme, and no adapter edits.

---
<a id="m097"></a>

### M097. UrlUtils.hostUrl: cache behind the existing generation counter

**score 25** · O 34 / D 66 / C 52 · kept by 3/3 plans · raw: L01.04  
**refs:** Opus #108 · Devin R059 · Codex T124

**guardrails:**
- Codex scope: Cache the derived host URL under the existing generation discipline and clear it from every cache-reset path. Preserve URL switching, fallback parsing, suffix rules, and concurrent invalidation. Verify all preference setters invalidate before eliminating preference reads.
- L01.04 correction from Codex: A tuple check still reads preferences on every access. Eliminating those reads requires audited invalidation and edits to reset paths, beyond the original getter-only restriction.

**verified against tree:** Verified, but the only callers precede an HTTP request, so the saving is noise. Use only the generation design (steps 1-2 re-read all prefs).

**canonical version — L01-T4 (Devin), "cache `UrlUtils.hostUrl` like `baseUrl`/`header` (roadmap 7)":**

context: `UrlUtils.hostUrl` (lines 83-108) reads four SharedPreferences values and runs `alternativeUrl.toUri()` on every access, while sibling getters `baseUrl` and `header` in the same file already cache behind a generation check. `ChatApiService` reads `hostUrl` once or twice per chat request (`ChatApiService.kt` lines 25-26 and ~48-49) and once more in its error log path, so every AI interaction re-reads prefs.

files: `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt` — `hostUrl` getter only. Do NOT touch `baseUrl`, `header`, `getUrl`, `dbUrl`, `spm`, or any caller including `ChatApiService.kt`.

steps:
1. Introduce a private cached value plus the four inputs it was computed from (scheme, hostIp, isAlternativeUrl, processedAlternativeUrl) — mirror the existing generation-check pattern used by `baseUrl` in this file.
2. On each `hostUrl` get, recompute only when any of the four inputs differ from the cached tuple.
3. Keep the `".org"`/`".gt"` suffix logic and the `try/catch` fallback warning identical.
4. If the file already holds a private generation field for `baseUrl`, reuse the same invalidation trigger so `hostUrl` refreshes whenever prefs change.

acceptance: `./gradlew testDefaultDebugUnitTest` green; chat still reaches the server after a URL switch (alternative URL toggle honored the next call after prefs change).

size budget: ~30 changed lines, 1 file.

out of scope: no API changes to `UrlUtils`, no caller edits, no persistence changes.

---
<a id="m098"></a>

### M098. SubmissionsAdapter / SubmissionsListAdapter: dateCache like sibling adapters

**score 24** · O 38 / D 61 / C 51 · kept by 3/3 plans · raw: L01.07  
**refs:** Opus #86 · Devin R092 · Codex T125

**guardrails:**
- Codex scope: Cache repeated timestamp formatting in both submission adapters, bounding the cache and invalidating it when locale/time zone or the adapter data lifetime changes.

**verified against tree:** Verified at :78 and :49.

**canonical version — L01-T7 (Devin), "cache formatted dates in the two submission list adapters (roadmap 7)":**

context: `SubmissionsAdapter.bind` (~line 78) calls `getFormattedDate(submission.startTime)` and `SubmissionsListAdapter` (~line 49) calls `TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime)` on every bind. Sibling adapters (`TeamsAdapter`, `NotificationsAdapter`, `HealthUsersAdapter`, `FeedbackAdapter`, `PersonalsAdapter`) already keep a `dateCache` `HashMap<Long, String>`; these two do not.

files: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt` and `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsListAdapter.kt`. Do NOT touch `TimeUtils.kt` — the formatter cache there is orthogonal and stays.

steps:
1. Add `private val dateCache = HashMap<Long, String>()` to each adapter (copy the `HealthUsersAdapter`/`TeamsAdapter` idiom).
2. Wrap each call site: `dateCache.getOrPut(submission.startTime) { getFormattedDate(submission.startTime) }` and `dateCache.getOrPut(submission.lastUpdateTime) { TimeUtils.getFormattedDateWithTime(submission.lastUpdateTime) }`.
3. No other changes to bind logic.

acceptance: `./gradlew testDefaultDebugUnitTest` green; submission rows show the same formatted dates.

size budget: ~8 changed lines, 2 files.

out of scope: no DiffUtil/payload changes, no layout changes.

---
<a id="m099"></a>

### M099. PersonalDao.countByTitle: branch on empty userId (drop the delete/update OR split)

**score 24** · O 34 / D 61 / C 69 · kept by 3/3 plans · raw: L08.04  
**refs:** Opus #106 · Devin R088 · Codex T095  
**shares files with:** M020, M101

**guardrails:**
- Codex scope: Use separate all-owner and exact-owner title-count queries so a real user ID can use its index; preserve title NOCASE and null/empty-owner behavior.
- Codex scope: Resolve all matching local/remote IDs before bounded positive-ID delete/update operations. Preserve the current match-all behavior when the two keys identify different rows and execute multi-step writes transactionally.
- L08.04 part dropped by Opus: delete/update OR split: `_id` is unindexed, so two statements are never cheaper.
- L08.04 correction from Codex: Do not generalize the indexed-OR rejection to every OR query. Personal remote IDs lack the matching index shown for users/teams; preserve updates to both matches and do not replace them with first-row precedence.

**verified against tree:** Only the countByTitle branch survives (use isEmpty, not isBlank). `_id` is unindexed, so splitting delete/update gains nothing.

**contributor notes:** L08-T4: countByTitle part

**canonical version — L08-T4 (Copilot·Grok), "make personal title-count and id updates index-friendly (roadmap 1+7)":**

context: `PersonalDao.countByTitle` (`PersonalDao.kt:14-18`) filters with
`(:userId IS NULL OR :userId = '' OR userId = :userId)`. `userId` is indexed
(`Personal.kt:7`), but the parameter OR prevents that index from being used
when a real user id is passed, which is the save-path duplicate-title check.
`deleteByIdOrDocId` (`PersonalDao.kt:32-33`) and `updateFields`
(`PersonalDao.kt:41-42`) use `_id = :id OR id = :id`. `id` is the primary key
(`Personal.kt:9-11`), so the OR blocks the primary-key lookup.
`updateFields_resolvesByIdAndDocId` in `PersonalDaoTest.kt:91` already
requires both keys to work.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`
(`countByTitle`, `deleteByIdOrDocId`, `updateFields`).
`app/src/test/java/org/ole/planet/myplanet/data/room/dao/PersonalDaoTest.kt`
(`createPersonal` at line 53). Do not touch `PersonalsRepositoryImpl.kt`,
`Personal.kt`, or `PersonalsViewModel.kt` (task 10 owns the viewmodel).

steps: 1. Keep `countByTitle(title, userId)` public. If `userId` is null or
blank, count `title = :title COLLATE NOCASE` with no user filter. Otherwise
count `title = :title COLLATE NOCASE AND userId = :userId`. Preserve the
case-insensitive title match and the "blank user means any user" rule from
the comment at lines 12-13. 2. Replace the OR delete with a `@Transaction`
method that deletes `WHERE id = :id` and then `WHERE _id = :id`. Same rows
removed. 3. Replace the OR update with a `@Transaction` method that runs the
same COALESCE assignments twice, once `WHERE id = :id` and once
`WHERE _id = :id`. A row matching both is updated twice with the same values.
Leave `updateUploadedStatus` and `updateRemoteDocRef` alone. 4. Add tests:
blank and null `userId` count every matching title; a non-blank `userId` does
not count another user's title; delete and update still hit a row by `id` and
a different row by `_id` only. `createPersonal` sets `id` and `_id` equal, so
a doc-id-only row must set those fields differently. Keep
`updateFields_resolvesByIdAndDocId` green.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.PersonalDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. Saving a personal
resource still rejects a duplicate title for that user and still allows the
same title for a different user. Editing or deleting by local id or document
id still updates the right row.

size budget: ~50 changed lines, 2 files

out of scope: no new index, no repository implementation change

---
<a id="m100"></a>

### M100. TeamsRepositoryImpl.getJoinedMembersWithVisitInfo: reuse the membership rows for leaderIds

**score 24** · O 34 / D 54 / C 71 · kept by 3/3 plans · raw: L01.01  
**refs:** Opus #107 · Devin R105 · Codex T078  
**shares files with:** M090

**guardrails:**
- Codex scope: Fetch membership rows once for member hydration and leader IDs. Preserve pending-deletion filtering and first-seen user order; retain the separate all-document admin-membership check unless equivalence is established.
- L01.01 correction from Codex: Discarded the unproven claim that all team-document user IDs equal membership-only user IDs; the shipped scope reuses only the two identical membership reads.

**verified against tree:** Membership is fetched twice (:983, :1028). Keep getAllByTeamId: step 3 of the original would drop admins whose only row is a join request.

**canonical version — L01-T1 (Devin), "fetch team memberships once in `getJoinedMembersWithVisitInfo` (roadmap 1+7)":**

context: `TeamsRepositoryImpl.getJoinedMembersWithVisitInfo` reads the same `teams` rows three times: `getJoinedMembers(teamId)` internally queries `teamDao.getByTeamIdAndDocType(teamId, "membership")`, then `teamDao.getAllByTeamId(teamId)` pulls every doc type for the team just to build `teamUserIds`, then `getByTeamIdAndDocType(teamId, "membership")` runs a third time for `leaderIds`. One membership fetch yields all three sets. Fewer Room round-trips on a screen that lists every member.

files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` — function `getJoinedMembersWithVisitInfo` (starts ~line 1001) and `getJoinedMembers` (~line 982). Do NOT touch `TeamDao.kt` — no new queries are needed; `getByTeamIdAndDocType` already exists. Do NOT touch `getJoinedMembers`'s other callers.

steps:
1. In `getJoinedMembersWithVisitInfo`, call `teamDao.getByTeamIdAndDocType(teamId, "membership")` once and keep the result in a local `membershipRows`.
2. Extract the member-hydration body of `getJoinedMembers` into a private `getJoinedMembers(membershipRows)` overload (or inline it) so the public `getJoinedMembers(teamId)` signature and behavior stay identical for its other callers.
3. Derive `teamUserIds` from `membershipRows.mapNotNull { it.userId }` instead of `teamDao.getAllByTeamId(teamId)` (the admin-check only needs membership userIds; membership rows already carry them).
4. Derive `leaderIds` from `membershipRows.filter { it.isLeader }` instead of the second `getByTeamIdAndDocType` call.

acceptance: `./gradlew testDefaultDebugUnitTest` green; team members screen shows members and valid community-leader admins in leader-first order, identical to before.

size budget: ~35 changed lines, 1 file.

out of scope: no DAO/interface signature changes, no changes to visit-info or join-request logic.

---
<a id="m101"></a>

### M101. PersonalsRepositoryImpl: remove android.util.Log

**score 23** · O 39 / D 34 / C 59 · kept by 3/3 plans · raw: L09.04  
**refs:** Opus #57 · Devin R139 · Codex T117  
**shares files with:** M099

**guardrails:**
- Codex scope: Remove direct Android logging where PersonalUploadResult already conveys the failure and cause to its caller. Preserve AttachmentFailed/DocumentFailed outcomes and existing fallback details.

**verified against tree:** Verified. The catches also swallow CancellationException (a missed fix).

**canonical version — L09-T4 (Copilot·Kimi), "strip android.util.Log from PersonalsRepositoryImpl (roadmap 1+9)":**

context: `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt:3` imports android.util.Log for three Log.w calls, all inside `uploadPersonal()`. Every failure path there already returns a typed `PersonalUploadResult` (`AttachmentFailed`/`DocumentFailed`) carrying the message and cause, so the log lines duplicate information the caller surfaces in the UI. It is the file's only android import.
files: app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt only. Leave alone: PersonalsRepository.kt, PersonalDao.kt, ui/personals/PersonalsViewModel.kt, ui/personals/PersonalsFragment.kt.
steps:
1. Delete `import android.util.Log` (line 3).
2. In `uploadPersonal()`, remove the Log.w in the attachment catch block, the Log.w for the unsuccessful attachment response, and the Log.w in the outer catch — keep all three return values unchanged.
3. Remove the now-unused `TAG` constant and companion object.
4. Run personals repository unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; no android imports remain in the file; uploading a personal resource still returns Success/AlreadyUploaded/AttachmentFailed/DocumentFailed with the same messages.
size budget: ~6 changed lines, 1 file
out of scope: do not change upload ordering, attachment naming, or the PersonalUploadResult contract; no logger injection.

---
<a id="m102"></a>

### M102. RetryRepositoryImpl: remove android.util.Log

**score 23** · O 39 / D 34 / C 57 · kept by 3/3 plans · raw: L09.02  
**refs:** Opus #58 · Devin R140 · Codex T119

**guardrails:**
- Codex scope: Use the existing persisted markFailed result as the reporting boundary and remove duplicate Android logging in executeOperation. Preserve error causes/messages and retry bookkeeping.

**verified against tree:** Verified. markFailed already records the error.

**canonical version — L09-T2 (Copilot·Kimi), "strip android.util.Log from RetryRepositoryImpl (roadmap 1+9)":**

context: `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt:3` imports android.util.Log for two log calls inside `executeOperation()`: `Log.w(TAG, "Network error during retry for ${operation.id}", e)` (~line 130) and `Log.e(TAG, "Unexpected error during retry for ${operation.id}", e)` (~line 134). Both catch blocks already call `markFailed(operation.id, e.message, null)`, which persists the error message in the retry_operations table — the Log lines are redundant diagnostics and the file's only android import.
files: app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt only. Leave alone: RetryDao.kt, model/RetryOperation.kt, services/retry/ (the queue processor), di/RepositoryModule.kt.
steps:
1. Delete `import android.util.Log` (line 3).
2. Remove the Log.w line in the `catch (e: IOException)` block; keep the markFailed + RetryableFailure returns.
3. Remove the Log.e line in the generic `catch (e: Exception)` block; keep the markFailed + RetryableFailure returns.
4. Remove the unused `TAG` constant from the companion object (keep `PREVIEW_LIMIT`).
5. Run retry repository tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; no android imports remain in the file; failed operations still record their error via markFailed and surface as RetryableFailure/TerminalFailure exactly as before.
size budget: ~6 changed lines, 1 file
out of scope: no changes to retry backoff, attempt counters, or the RetryQueue worker; no new logging framework.

---
<a id="m103"></a>

### M103. HealthExaminationItemMapper: Kotlin stdlib instead of TextUtils

**score 23** · O 38 / D 49 / C 65 · kept by 3/3 plans · raw: L18.06  
**refs:** Opus #78 · Devin R109 · Codex T108

**guardrails:**
- Codex scope: Use Kotlin isNotEmpty and equality for the already non-null createdBy string. Preserve examiner resolution and self-examination behavior; remove only the direct TextUtils dependency.
- L18.06 correction from Codex: This does not make the mapper fully platform-free: it still returns HealthExaminationAdapter.HealthExaminationItem and uses platform-linked models/formatting. Only the redundant direct import is removed.

**verified against tree:** Verified at :21. The class still depends on TimeUtils (android), so it is not fully platform-free.

**canonical version — L18-T6 (Antigravity), "remove android TextUtils dependency from HealthExaminationItemMapper (roadmap 3+8, advances 9+10)":**

context: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt`
is a pure Kotlin domain-to-UI mapper except for `import android.text.TextUtils` at line 3 and
`!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)` at line 21.
Because `GsonUtils.getString("createdBy", json)` at line 15 returns a non-null `String`
(defaulting to `""`), `TextUtils` is completely unnecessary and forces unit callers to either
run under Robolectric or rely on stubbed Android framework classes. Serving roadmap 3 and 8
while directly advancing north stars 9 (KMP platform-free Kotlin core/mappers with zero
`android.*` imports) and 10 (CMP portable UI state mapping), replacing `TextUtils` with Kotlin
stdlib expressions makes `HealthExaminationItemMapper` 100% platform-free.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt` (import at line 3, `map` at line 21)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt`
steps:
1. Open `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt` and remove `import android.text.TextUtils` at line 3.
2. At line 21 inside `HealthExaminationItemMapper.map`, replace `!TextUtils.isEmpty(createdBy) && !TextUtils.equals(createdBy, userModel?.id)` with `createdBy.isNotEmpty() && createdBy != userModel?.id`.
3. Verify that no `android.*` imports remain in `HealthExaminationItemMapper.kt` and run the unit test suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes;
`grep "^import android\." app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt`
returns no matches, and health examination rows still resolve examiner names and
`isSelfExamination` identically.
size budget: ~3 changed lines, 1 file
out of scope: do not change `HealthExaminationAdapter.kt`, `HealthExamination.kt`, or date
formatting behavior.

---
<a id="m104"></a>

### M104. ResourcesSearchUtils.searchList: append to the prefix list instead of +

**score 23** · O 38 / D 49 / C 65 · kept by 3/3 plans · raw: L16.09  
**refs:** Opus #84 · Devin R114 · Codex T109

**guardrails:**
- Codex scope: Append contains results to the private prefix-result list and return it rather than allocating a third list. Preserve empty-query list identity and prefix priority.

**verified against tree:** Verified at :21.

**canonical version — L16-T9 (Codex), "Reuse the prefix-result buffer instead of allocating a third search list (roadmap 7+8)":**

context: `ResourcesSearchUtils.kt:13–21` fills separate prefix and contains-match lists, then allocates another list to concatenate both on every nonempty search. The prefix list is private to the call and can safely become the output by appending contains matches. Existing ordering tests at `ResourcesSearchUtilsTest.kt:48–70` establish prefix priority and multiword matching.

files:
- `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt` — `ResourcesSearchUtils.searchList` final result assembly only.
- `app/src/test/java/org/ole/planet/myplanet/utils/ResourcesSearchUtilsTest.kt` — `ResourcesSearchUtilsTest` ordering/non-mutation coverage.
- Leave normalization, `searchLocalModels`, model construction, selectors, and UI filtering/sorting alone.

steps:
1. Append contains matches to the already-built prefix buffer and return that buffer, eliminating the final concatenation allocation and prefix copy.
2. Preserve the empty-query fast path, null-title exclusion, matching predicates, duplicates, and encounter order within each result group.
3. Add a compact generic-list test with multiple prefix and contains matches interleaved in the input; assert exact output order and unchanged input.
4. Retain all existing case, accent, and multiword tests; do not introduce caching or a sequence-based rewrite.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.ResourcesSearchUtilsTest'` must pass.
- `./gradlew testDefaultDebugUnitTest` must stay green.
- Resource searches show identical results with prefix matches first and stable order, including accent/case normalization and empty searches.

size budget: approximately 20–40 changed lines across 2 files.

out of scope: No ranking, normalization, return-type contract, or search-cache changes. The generic search operation remains free of Android calls, preserving the direction of roadmap 9 without claiming the surrounding models are platform-free.

---
<a id="m105"></a>

### M105. News model: remove android.util.Log

**score 22** · O 39 / D 34 / C 54 · kept by 3/3 plans · raw: L09.05  
**refs:** Opus #56 · Devin R138 · Codex T121  
**shares files with:** M052

**guardrails:**
- Codex scope: Remove direct Android logging from News parsing and sort fallbacks, preserving false/time/empty-field behavior and arranging malformed-data diagnostics at the parsing boundary where needed. Do not claim that removing an import alone makes the full dependency graph portable.

**verified against tree:** Verified. There are five Log.w calls, not six.

**canonical version — L09-T5 (Copilot·Kimi), "strip android.util.Log from the News model (roadmap 1+9)":**

context: `app/src/main/java/org/ole/planet/myplanet/model/News.kt:3` imports android.util.Log, used in six catch blocks across `isCommunityNews`, `calculateSortDate()` and `createNews(...)` — every one of which already falls back to a safe default (false, `time`, empty fields). Roadmap 9 requires models to end with zero android.* imports; Log is the only android.* import in this file (the androidx.room annotations stay until the KMP module exists).
files: app/src/main/java/org/ole/planet/myplanet/model/News.kt only. Leave alone: data/room/dao/NewsDao.kt, repository/VoicesRepositoryImpl.kt, ui/voices/ (all of it).
steps:
1. Delete `import android.util.Log` (line 3).
2. Remove the Log.w in `isCommunityNews` catch, keeping `return false` behavior.
3. Remove the Log.w in `calculateSortDate()` catch, keeping the `return time` fallback.
4. Remove the three Log.w lines inside `createNews(...)` (updatedDate parse, conversation parse, news json parse catches), keeping the existing fallbacks.
5. Remove the now-unused `TAG` constant from the companion object.
6. Run model/news unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; News.kt contains no android.* import (androidx.room imports remain); voices feed, community detection, and news creation behave as before.
size budget: ~8 changed lines, 1 file
out of scope: no Room/@Entity changes, no viewIn format changes, no logging replacement.

---
<a id="m106"></a>

### M106. CommunityRepositoryImpl: remove android.util.Log

**score 20** · O 39 / D 34 / C 50 · kept by 3/3 plans · raw: L09.01  
**refs:** Opus #55 · Devin R136 · Codex T128

**guardrails:**
- Codex scope: Remove the direct android.util.Log use from community sync while retaining cancellation and the false failure result. Ensure existing caller reporting is sufficient before deleting the only operation diagnostic; keep any required reporter platform-independent.

**verified against tree:** Verified. It trades a diagnostic for platform-freeness, so a logging abstraction would be better long term.

**canonical version — L09-T1 (Copilot·Kimi), "strip android.util.Log from CommunityRepositoryImpl (roadmap 1+9)":**

context: `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt:3` has `import android.util.Log`, the file's only android import. It is used exactly once, in `syncCommunityDocs()` at line ~57 (`Log.w(TAG, "syncCommunityDocs failed", e)`), where the failure is already signaled to the caller by returning `false`. Roadmap 9 requires repositories to end with zero android.* imports; this file is one import away from clean.
files: app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt only. Leave alone: CommunityDao.kt, model/Community.kt, di/RepositoryModule.kt, and the callers of syncCommunityDocs.
steps:
1. Delete the `import android.util.Log` line.
2. In `syncCommunityDocs()` catch block, drop the Log.w line and keep `false` as the return value.
3. Delete the now-unused `TAG` constant and the `companion object` block.
4. Run the repository unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; `grep -n "android" app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt` returns nothing; a failed community sync still returns false (unchanged behavior, no crash path added).
size budget: ~5 changed lines, 1 file
out of scope: do not introduce a logger abstraction or Timber; do not change retry or sync scheduling behavior.

---
<a id="m107"></a>

### M107. SubmissionsRepositoryExporter.drawMultilineText: measure space and word once

**score 20** · O 38 / D 46 / C 65 · kept by 3/3 plans · raw: L06.08  
**refs:** Opus #75 · Devin R124 · Codex T107  
**shares files with:** M091, M092

**guardrails:**
- Codex scope: Use nullable substringBefore for getExamId, preserving null, empty, plain-ID, leading-delimiter, and multiple-delimiter results.
- L06.08 correction from Codex: Dropped the additive Paint.measureText(space)+measureText(word) rewrite: width additivity is not established for shaped text and can change wrapping. Only the verified parent-ID split removal is retained.

**verified against tree:** Verified at :258-265. Kerning may shift wrap points by sub-pixel amounts.

**canonical version — L06-T8 (Antigravity), "eliminate per-word string concatenation and duplicate text measurement in SubmissionsRepositoryExporter (roadmap 1+7)":**

context: `SubmissionsRepositoryExporter.kt:247-279` (`drawMultilineText`) splits every question and answer
body on `" "` and, for every word after the first on a line, allocates a `" $word"` string
(`val addition = " $word"`) and calls `paint.measureText(addition)`; when the line wraps (lines 260-266),
it calls `paint.measureText(word)` a second time for that same word. In the same file, `getExamId`
(`SubmissionsRepositoryExporter.kt:307-313`) checks `parentId?.contains("@") == true` and then allocates
a `List<String>` via `parentId.split("@")[0]` on every submission instead of using `substringBefore('@')`.
Measuring `" "` once per `drawMultilineText` invocation and measuring each word once removes per-word
string allocations and duplicate JNI `Paint.measureText` calls during single- and multi-submission PDF exports.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt` (`SubmissionsRepositoryExporter.drawMultilineText`, lines 247-279, and `SubmissionsRepositoryExporter.getExamId`, lines 307-313)
- touch: `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt` (`SubmissionsRepositoryExporterTest`)
- leave alone: `SubmissionsRepositoryExporter.generateSubmissionPdf`, `SubmissionsRepositoryExporter.generateMultipleSubmissionsPdf`, and `SubmissionsRepositoryImpl` (owned by open PRs)
steps:
1. Open `SubmissionsRepositoryExporter.kt` and locate `drawMultilineText` at lines 247-279 and `getExamId` at lines 307-313.
2. In `drawMultilineText`, measure `val spaceWidth = paint.measureText(" ")` once before iterating `words`, and inside `words.forEach { word -> ... }` compute `val wordWidth = paint.measureText(word)` once per word.
3. When `line.isEmpty()`, append `word` and set `lineWidth = wordWidth`; otherwise compare `lineWidth + spaceWidth + wordWidth > maxWidth`: on wrap, draw `line.toString()`, advance `currentY += LINE_HEIGHT`, clear `line`, append `word`, and set `lineWidth = wordWidth`; when it fits, call `line.append(' ').append(word)` and `lineWidth += spaceWidth + wordWidth`.
4. Replace the body of `getExamId(parentId: String?): String?` (lines 307-313) with `return parentId?.substringBefore('@')` and mark `getExamId` as `internal` so it can be unit-tested directly alongside `getQuestionsByExam`.
5. In `SubmissionsRepositoryExporterTest.kt`, add unit tests for `getExamId` (null, plain ID, and `examId@courseId` inputs) and run `./gradlew testDefaultDebugUnitTest --tests "org.ole.planet.myplanet.repository.SubmissionsRepositoryExporterTest"`.
acceptance: `./gradlew testDefaultDebugUnitTest` stays green; exporting single or multiple exam/survey
submissions to PDF wraps multiline questions and answers identically without extra string or list allocations.
size budget: ~30 changed lines, 2 files
out of scope: do not alter PDF page dimensions, font sizes, or `SubmissionsRepositoryImpl`.

---
<a id="m108"></a>

### M108. Meetup.getHashMap: drop the unreachable date try/catch and the routine JSONArray stack trace

**score 18** · O 38 / D 49 / C 51 · kept by 3/3 plans · raw: L03.09  
**refs:** Opus #80 · Devin R111 · Codex T126

**guardrails:**
- Codex scope: Remove the redundant date-format catch only after retaining the formatter fallback. Keep the JSONArray exception boundary for malformed day data, replacing its printStackTrace with controlled diagnostics or the established fallback.
- L03.09 correction from Codex: The day-JSON catch is reachable and must remain. Removed only the claim that both catches are dead.

**verified against tree:** Verified: getFormattedDate cannot throw, and a non-recurring meetup throws and logs on every detail open. Prefer the silent option to keep the model platform-free.

**canonical version — L03-T9 (Copilot·Kimi), "Drop the dead try/catch and printStackTrace calls in Meetup.getHashMap (roadmap 1+8, supports 9)":**

context: app/src/main/java/org/ole/planet/myplanet/model/Meetup.kt:107 wraps `TimeUtils.getFormattedDate(...)` in try/catch with `e.printStackTrace()`, but `getFormattedDate` already catches all exceptions internally and returns `"N/A"` (see `formatInstant` in app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt), so the catch is unreachable dead code. Line :118 repeats the pattern around `JSONArray(meetups.day)` parsing, where a silent fallback is the existing behavior anyway. Removing dead code from a Room entity model directly serves the data-layer cleanup and the platform-free-core north star.

files: `app/src/main/java/org/ole/planet/myplanet/model/Meetup.kt` — companion `getHashMap` only. Leave `fromJson`, `serialize`, and `parseJson` untouched.

steps:
1. Remove the try/catch around the `"Meetup Date"` entry and call `TimeUtils.getFormattedDate` directly for `startDate`/`endDate`.
2. In the `"Recurring Days"` block, replace `e.printStackTrace()` with either a silent continuation (keeping `recurringDays` empty) or a `Log.w` with a `TAG` — output map content for valid input must stay byte-identical.
3. Remove any imports orphaned by the edit.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; the meetup detail dialog shows the same date range and recurring-days rows for meetups with and without recurrence.

size budget: ~12 changed lines, 1 file.

out of scope: do not migrate `Meetup` off `org.json.JSONArray` in this task; no Room schema or annotation changes.

---
<a id="m109"></a>

### M109. playstore-quota.sh save_epochs: GraphQL isDraft/publishedAt instead of full release documents

**score 18** · O 35 / D 46 / C 70 · kept by 3/3 plans · raw: L14.07  
**refs:** Opus #99 · Devin R127 · Codex T088  
**shares files with:** M066

**guardrails:**
- Codex scope: Request only release draft/publication fields through a suitably paged server-side projection while preserving the current release selection, ordering, and injected RELEASES_JSON behavior. Do not drop republished releases by assuming publication and creation order are identical.

**verified against tree:** Verified at :38/:41. Needs an explicit orderBy CREATED_AT DESC.

**canonical version — L14-T7 (Copilot·Grok), "stop the playstore quota script from downloading full release documents (roadmap 7)":**

context: `playstore-quota.sh` `save_epochs` (`.github/scripts/playstore-quota.sh:36-44`) calls `gh api "repos/$REPO/releases?per_page=100"` whenever `RELEASES_JSON` is unset. That payload includes every release body and asset list. The function only reads `published_at` of non-drafts, then `measure` keeps epochs inside 24h plus `PLAYSTORE_SLOT_LAG`. `automerge.yml` and the playstore workflow both invoke this on the release path. Cutting the payload is a workflow quick win. It does not move roadmap 9 or 10.

files: `.github/scripts/playstore-quota.sh` (`save_epochs`) only. Do not edit any workflow YAML.

steps:
1. Leave the `RELEASES_JSON` branch unchanged so a fixture file still feeds the existing `jq` epoch pipeline.
2. Replace only the live `gh api repos/…/releases` call with one GraphQL request for the 100 newest releases, requesting `isDraft` and `publishedAt` and nothing else. Split `REPO` on `/` into owner and name.
3. Keep the epoch stream identical: non-drafts only, `fromdateiso8601`, newest first, one epoch per line. Drop nodes whose `publishedAt` is null.
4. Do not change `status`, `report`, or `forecast` output keys (`limit`, `used`, `held`, `free`, `next_free_epoch`, `next_free_local`, `frees_within_hour`, `report`).

acceptance: `bash -n .github/scripts/playstore-quota.sh` passes. With `RELEASES_JSON` set to a two-release fixture and `NOW_EPOCH` fixed, `status` still prints the same keys and the same `used` count as before this change. No Gradle test depends on this script. `./gradlew testDefaultDebugUnitTest` is untouched and must stay green.

size budget: ~25 changed lines, 1 file.

out of scope: do not change `LIMIT`, `LAG`, or the 24h window. Do not add a new dependency.

---
<a id="m110"></a>

### M110. ChatSearch.fullConvoSearch: one presized result list instead of chained +

**score 18** · O 34 / D 49 / C 69 · kept by 3/3 plans · raw: L04.08  
**refs:** Opus #102 · Devin R107 · Codex T097

**guardrails:**
- Codex scope: Append title-prefix, title-contains, conversation-prefix, and conversation-contains buckets into one pre-sized result list; do the same for title-only results. Preserve ranking and stable order.
- L04.08 part dropped by Opus: searchByTitle half: a single + already allocates exactly one list.

**verified against tree:** The fullConvoSearch half is verified (:89). The searchByTitle half is a no-op.

**canonical version — L04-T8 (Codex), "Assemble chat-search ranking buckets without intermediate concatenations (roadmap 7+8)":**

context: `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt:89` concatenates four result lists with chained plus operations, copying earlier buckets into multiple intermediate lists on each full-conversation search. Line 110 similarly concatenates title results; assembling one destination removes avoidable result-copy allocations while preserving this search utility's Android-free boundary for roadmap 9.

files:

- `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt` — only result assembly in `ChatSearch.fullConvoSearch` and `searchByTitle`; leave `Index`, normalization, matching loops, and dispatcher routing alone.
- `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt` — `ChatSearchTest`; extend multi-bucket ordering coverage.

steps:

1. Allocate one result destination with capacity equal to the populated buckets' total size and append each bucket directly into it. Apply this separately to full-conversation and title search.
2. Preserve the exact full-search bucket order: first-conversation prefix, first-conversation contains, later-conversation prefix, later-conversation contains. Preserve title prefix-before-contains order.
3. Keep first-matching-conversation short-circuit behavior, input order within buckets, null handling, and duplicate input occurrences unchanged; do not convert results into a set.
4. Add a fixture containing all four full-search buckets in mixed input order, exercise question and response modes, and assert the complete ordered identities. Retain existing accent/case and title-mode tests.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.ChatSearchTest'`
- `./gradlew testDefaultDebugUnitTest`
- Search chat titles, questions, and responses: the same chats appear in the same ranking, including chats with multiple matching conversations, with one final result assembly instead of chained intermediate copies.

size budget: approximately 55–100 changed lines, 2 files.

out of scope: Do not change search indexing, tokenization, cancellation policy, ranking rules, or chat-history loading.

---
<a id="m111"></a>

### M111. FeedbackAdapter: resolve label strings once in onCreateViewHolder

**score 16** · O 38 / D 46 / C 54 · kept by 3/3 plans · raw: L01.08  
**refs:** Opus #76 · Devin R125 · Codex T122

**guardrails:**
- Codex scope: Pre-resolve the fixed LABELS strings for FeedbackAdapter, preserving Locale.ROOT key normalization and raw-value fallback; refresh when the display configuration changes.

**verified against tree:** Verified at :61-62/:86. Status is not localized.

**canonical version — L01-T8 (Devin), "resolve `FeedbackAdapter` label strings once instead of per bind (roadmap 7)":**

context: `FeedbackAdapter.onBindViewHolder` calls `localizedLabel(context, feedback.type)` and `localizedLabel(context, feedback.priority)` per bind; `localizedLabel` does `context.getString(resId)` for one of only five fixed `LABELS` entries. The adapter already caches `statusText`/`priorityText`/`openDateText` in `onCreateViewHolder` — the label strings should join that cache.

files: `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt` — `localizedLabel` and `onCreateViewHolder`. Do NOT touch the `LABELS` map keys or the content-description format string.

steps:
1. In `onCreateViewHolder` (where `statusText` etc. are resolved), populate a `Map<Int, String>` (or `Map<String, String>` keyed on the lowercase label) resolving every `LABELS` value to its string.
2. Rewrite `localizedLabel` to consult the pre-resolved map first, falling back to the raw `value` when absent.
3. Keep `value?.lowercase(Locale.ROOT)` normalization semantics identical.
4. Remove the per-call `context.getString` from `localizedLabel` (the map is built once per adapter use).

acceptance: `./gradlew testDefaultDebugUnitTest` green; feedback rows still show localized type/priority/status.

size budget: ~20 changed lines, 1 file.

out of scope: no new strings, no layout changes.

---
<a id="m112"></a>

### M112. NetworkMonitorWorker: rename UPLOAD_DELAY_SECONDS to REACHABILITY_DELAY_SECONDS

**score 9** · O 34 / D 49 / C 44 · kept by 3/3 plans · raw: L13.09  
**refs:** Opus #104 · Devin R112 · Codex T131

**guardrails:**
- Codex scope: Rename UPLOAD_DELAY_SECONDS to REACHABILITY_DELAY_SECONDS and update its only use without changing the delay or WorkManager replacement policy.
- L13.09 part dropped by Opus: KEEP→REPLACE half: inputData is constant; REPLACE starves under reconnect flaps and breaks a test.
- L13.09 correction from Codex: Dropped KEEP-to-REPLACE: every request contains the same true boolean, and ServerReachabilityWorker reads the current server URL when it runs. No stale dynamic input exists; REPLACE can repeatedly postpone or cancel useful work.

**verified against tree:** Only the rename survives. The KEEP→REPLACE half is wrong: inputData is constant, REPLACE debounces forever on flapping networks, and it breaks NetworkMonitorWorkerTest.

**contributor notes:** L13-T9: rename half only

**canonical version — L13-T9 (Devin), "Rename and de-stale `UPLOAD_DELAY_SECONDS` in `NetworkMonitorWorker` (roadmap 5, 8)":**

**context:** `UPLOAD_DELAY_SECONDS = 30L` at `NetworkMonitorWorker.kt:26` is misnamed — it delays `ServerReachabilityWorker` (`:61`), not an upload. Worse, that worker is enqueued with `ExistingWorkPolicy.KEEP` (`:67`), so a connectivity event that fires while a stale request is pending silently keeps the old `inputData` — the new trigger's parameters never reach the worker. Evidence: the constant's name says "upload" while its only consumer is `ServerReachabilityWorker`, and `KEEP` drops incoming work wholesale.

**files:** `app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt` — the constant, the `enqueueUniqueWork` call, and its `inputData`. Leave alone: `ServerReachabilityWorker.kt`, `MainApplication`'s scheduling of `NetworkMonitorWorker` itself, `UploadManager`.

**steps:**
1. Rename `UPLOAD_DELAY_SECONDS` to `REACHABILITY_DELAY_SECONDS` and update its reference at `:61`.
2. Change `ExistingWorkPolicy.KEEP` to `ExistingWorkPolicy.REPLACE` so a fresh connectivity event re-arms the reachability check with current `inputData` (REPLACE is safe here — the work is a 30s-delayed one-shot check, and a superseded check has no value).
3. If the diff reveals `inputData` carries nothing dynamic, still keep REPLACE — it is the semantically correct policy for a "check now" trigger.

**acceptance:** `./gradlew :app:compileDefaultDebugKotlin` and `./gradlew testDefaultDebugUnitTest` green; no other references to `UPLOAD_DELAY_SECONDS` remain. User-visible: none in normal use; a reconnect now schedules a fresh reachability check rather than being dropped when one is pending.

**size budget:** ~8 changed lines, 1 file.

**out of scope:** no delay-duration change, no WorkManager chain restructuring, no `ServerReachabilityWorker` edits.

---
<a id="m113"></a>

### M113. HealthRepositoryImpl: remove android.util.Log

**score 6** · O 36 / D 34 / C 49 · kept by 3/3 plans · raw: L09.03  
**refs:** Opus #97 · Devin R137 · Codex T129

**guardrails:**
- Codex scope: Separate platform logging from health parsing/upload/encryption fallbacks. Keep useful diagnostics at an existing caller or injected platform-neutral reporter rather than silently deleting the sole indication of a failed health operation.
- L09.03 correction from Codex: The literal delete-all-logs version suppresses sole diagnostics on swallowed failures. The valid boundary cleanup needs reporting preserved, increasing scope and lowering feasibility.

**verified against tree:** Verified, but :237-240 is the only trace of a failed health-profile encryption, so surface it instead of silencing it.

**canonical version — L09-T3 (Copilot·Kimi), "strip android.util.Log from HealthRepositoryImpl (roadmap 1+9)":**

context: `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt:3` imports android.util.Log, used in five catch blocks (`getExaminationConditions`, `uploadHealthData`, `decodeHealth`, `updateUserHealthProfile`, `getPatientHealthRecords`), each of which already degrades gracefully (empty map, skipped upload, null return). This android import blocks roadmap 9's zero-android-imports goal for the repository layer.
files: app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt only. Leave alone: HealthExaminationDao.kt, utils/AndroidDecrypter.kt (its Android coupling is a separate, larger problem — do not touch), services/UploadToShelfService.kt.
steps:
1. Delete `import android.util.Log` (line 3).
2. Remove all five Log.e lines, keeping each catch block's existing fallback behavior identical.
3. Verify no leftover unused imports or constants (there is no TAG constant in this file; confirm nothing else referenced Log).
4. Run health repository unit tests.
acceptance: ./gradlew testDefaultDebugUnitTest green; no android imports remain in HealthRepositoryImpl.kt; health profile save/load and pending-examination sync behave as before (failures still return null/empty results, never throw).
size budget: ~7 changed lines, 1 file
out of scope: do not refactor AndroidDecrypter, encryption key handling, or the userRepository Lazy dependency; do not add a logging abstraction.

---

## Appendix A: optional, contested (Opus dropped them; Devin and/or Codex kept them)

Each one is real but small, or only valid in Codex's corrected form. Take them only alongside a neighbouring task, and only with the stated corrections.

<a id="a01"></a>

### A01. Bind BaseContainerFragment's view-touching coroutines to the view lifecycle (roadmap 8 + 7)

O dropped / D 85 / C 90 · raw: L17.06 · refs: Devin R007 · Codex T012

**Megaplan verdict, optional:** Opus: `showDownloadDialog` is not suspend and checks `isAdded`, so the click-launched coroutine never outlives the view. Codex's second half has some merit: `openHtmlResource` builds `File(context?.getExternalFilesDir(null), …)`, and a null context yields a relative path. `BaseContainerFragment` changed in 7 commits after Codex's base, so re-read it first. Keep only the null-context guard, as a few-line hardening.

**Codex scope (use this, not the raw version):**
- Launch the resource download dialog from viewLifecycleOwner.lifecycleScope so it cannot outlive onDestroyView.
- Capture a valid context/external path before the IO hop in openHtmlResource and handle unavailable storage explicitly, avoiding File(null, child) falling back to a relative path.

**Opus objection:** Premise false: showDownloadDialog is not suspend and checks isAdded, so the coroutine never outlives the view.

<details><summary>Devin's full work order (apply the corrections above)</summary>

**Rating:** 85 (evidence 4/5 · impact 4/5 · risk-adjusted feasibility 5/5)  
**Proposed by (1 list):** L17.6 (Claude Code, P3, completeness 4/5)  
**Canonical text:** L17.6 (most complete)

**Verified against `2093a6224`:**
- L17.6: lifecycleScope.launch showDownloadDialog BaseContainerFragment:357 while siblings use viewLifecycleOwner

context: `base/BaseContainerFragment.kt:357` starts `showDownloadDialog(resources)` from a button click with the *fragment's* `lifecycleScope`. That coroutine outlives `onDestroyView`, so it can show a dialog for a view that no longer exists. Nearby code in the same class already uses `viewLifecycleOwner.lifecycleScope` (lines 117, 148, 187, 226). In `openHtmlResource` (lines 187-190), `context?.getExternalFilesDir(null)` is read *inside* the IO block. If the fragment detaches first, `File(null, "ole/…")` resolves against the process working directory and wrongly reports the entry file as missing. `onDownloadComplete` (lines 117-125) already shows the safe pattern of capturing `context ?: return@launch` first.
files:
- `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt` only (`setResourceButton` and `openHtmlResource`).
- Leave alone: `BaseResourceFragment` and `BaseRecyclerFragment` (open PRs own them), and every subclass.
steps:
1. At line 357, use `viewLifecycleOwner.lifecycleScope.launch` in place of `lifecycleScope.launch`.
2. In `openHtmlResource`, capture `val ctx = context ?: return@launch` before the `withContext(dispatcherProvider.io)` block and use `ctx.getExternalFilesDir(null)` inside it.
3. Check that no other `lifecycleScope.launch` in this file touches views. Leave any non-view ones unchanged.
acceptance:
- `./gradlew testDefaultDebugUnitTest` green;
- `./gradlew assembleDefaultDebug` builds.
- On a device: open a course with resources, tap "Resources", then rotate the screen or press back right away; the app does not crash and no orphan dialog stays.
- Opening a downloaded HTML resource still starts `WebViewActivity`.
size budget: about 6 changed lines in 1 file.
out of scope: do not move the download dialog into a ViewModel, and do not change `prgDialog` handling.

</details>

---

<a id="a02"></a>

### A02. make opened-resource observation return one stable row per resource (roadmap 1+7+8)

O dropped / D 74 / C 59 · raw: L08.05 · refs: Devin R023 · Codex T118

**Megaplan verdict, optional:** Opus: no consumer reads title or time, because the only consumer maps rows to a resourceId set. Codex keeps a deterministic primary-key representative per `GROUP BY` resource as a correctness tidy-up. Low value, so do it only if you are already in that DAO.

**Codex scope (use this, not the raw version):**
- Replace bare-column GROUP BY selection with a deterministic existing primary-key representative per resource, preserving the public Flow signature and resource-ID set. Do not change the separate most-opened aggregate or infer that the present consumer displays arbitrary representative titles.

**Opus objection:** No consumer reads title or time: the only consumer maps to a resourceId set. The subquery makes the Flow more expensive.

<details><summary>Devin's full work order (apply the corrections above)</summary>

**Rating:** 74 (evidence 4/5 · impact 4/5 · risk-adjusted feasibility 3/5)  
**Proposed by (1 list):** L08.5 (Copilot/Grok-4.7, P2, completeness 4/5)  
**Canonical text:** L08.5 (most complete)

**Verified against `2093a6224`:**
- L08.5: GROUP BY resourceId with SELECT * no aggregate (ResourceActivityDao:29) -> arbitrary row

context: `ResourceActivityDao.observeByUserAndType` (`ResourceActivityDao.kt:29-30`)
is `SELECT * ... GROUP BY resourceId` with no aggregate. SQLite returns an
arbitrary row per group, so the opened-resource list can change title or time
between reads. `ResourceActivity.id` is the primary key
(`ResourceActivity.kt:12-14`). The existing test
(`ResourceActivityDaoTest.kt:117`) only asserts the set of resource ids and a
size of 2. `getMostOpenedResource` already picks `MIN(title)` and must not be
changed. Callers in the blocked resources repository map this Flow to resource
ids, so the public signature must stay a `Flow<List<ResourceActivity>>`.

files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt`
(`observeByUserAndType`).
`app/src/test/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDaoTest.kt`
(`createActivity` at line 39, the test at line 117). Do not touch
`ResourcesRepositoryImpl.kt` or `ResourceActivity.kt`.

steps: 1. Keep the Flow signature
`observeByUserAndType(userName, type): Flow<List<ResourceActivity>>`.
2. Replace the bare GROUP BY with a query that returns the row whose `id` is
`MIN(id)` inside each `resourceId` group, still filtered by `user`, `type`,
and `resourceId IS NOT NULL`. Apply those filters in the subquery and again
on the outer select so another user's row cannot leak in through a colliding
id. `ORDER BY resourceId` so the list order is stable. 3. Extend the test at
line 117: insert two rows with the same `resourceId` and different titles, set
`id` explicitly to `"a"` and `"b"` (`createActivity` assigns a random UUID, so
do not rely on insertion order), and assert the returned row is the `"a"` row.
Keep the existing assertions that jane, a null resource id, and `other_type`
are excluded. 4. Run the unit tests named below.

acceptance: `./gradlew :app:testDefaultDebugUnitTest --tests org.ole.planet.myplanet.data.room.dao.ResourceActivityDaoTest`
green, and `./gradlew testDefaultDebugUnitTest` stays green. The
opened-resources list still shows one entry per resource for that user and
type, and repeating the read returns the same row.

size budget: ~30 changed lines, 2 files

out of scope: no repository implementation change, no change to `getMostOpenedResource` or upload queries

</details>

---

<a id="a03"></a>

### A03. Collapse the per-survey submission-existence loops into single COUNT queries (roadmap 1+7)

O dropped / D 71 / C 83 · raw: L03.02 · refs: Devin R042 · Codex T027

**Megaplan verdict, optional:** Opus rejected the raw COUNT SQL because it flips the result for zero-question surveys and blank ids. Codex's corrected scope keeps question-count gating and blank/null exits. N is usually 0-2, so do it only with a test per edge case.

**Codex scope (use this, not the raw version):**
- Replace per-survey hasSubmission calls with bounded set/count queries for the requested course and user. Preserve blank/null-input exits, userId IS semantics where reached, survey parent IDs, question-count gating, status/type distinctions, and the difference between unfinished and pending checks.

**Opus objection:** The proposed COUNT SQL flips the result for zero-question surveys and blank ids (:215-221). N is usually 0-2.

<details><summary>Devin's full work order (apply the corrections above)</summary>

**Rating:** 71 (evidence 4/5 · impact 3/5 · risk-adjusted feasibility 4/5)  
**Proposed by (1 list):** L03.2 (Copilot/Kimi K3, P1, completeness 4/5)  
**Canonical text:** L03.2 (most complete)

**Verified against `2093a6224`:**
- L03.2: hasUnfinishedSurveys/hasPendingSurvey loop hasSubmission (2 queries each) SubmissionsRepositoryImpl:412-432

context: `SubmissionsRepositoryImpl.hasUnfinishedSurveys` (app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:412) and `hasPendingSurvey` (:422) loop over every survey of a course and call `hasSubmission` per survey, and `hasSubmission` (:209-231) itself runs `questionDao.countByExamId` plus a `submissionDao` count. That is 2N+1 sequential Room queries for a course with N surveys, on the take-course and dashboard evaluation paths. The DAO layer already exposes the exact count semantics to compose from (`SubmissionDao.countByUserParentAndType`, app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt:34).

files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (the two functions only), `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt` (two new `@Query` count methods), `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImplTest.kt` (extend the existing mocks near line 510). Leave `SubmissionsRepository.kt` interface and `hasSubmission` untouched, and do not touch `CoursesRepositoryImpl.hasUnfinishedSurveys` (:590).

steps:
1. In `SubmissionDao`, add a suspend count query over `exams` (type `survey`, `courseId = :courseId`) requiring at least one matching `questions` row and no matching `submissions` row, with `parentId` built as `examId || '@' || :courseId` and the same nullable-user semantics (`userId IS :userId`, `type = 'survey'`) as `countByUserParentAndType`.
2. Add a sibling query identical except `parentId` is built from the exam row's own `courseId` column — this preserves current `hasPendingSurvey` behavior, which passes `survey.courseId` per row (SubmissionsRepositoryImpl.kt:425) instead of the parameter.
3. Rewrite both repository functions to return `daoCount > 0` and delete the loops; keep early-return semantics (true as soon as one unanswered survey exists) intact via the count.
4. Update `SubmissionsRepositoryImplTest`: mock the two new DAO methods; cover zero surveys, all-answered, and one-unanswered cases.

acceptance: `./gradlew testDefaultDebugUnitTest` green; course detail still flags unfinished surveys correctly; the dashboard challenge check returns identical results for fixtures with 0, 1 and N surveys.

size budget: ~80 changed lines, 3 files.

out of scope: no interface signature changes; no batching of other `hasSubmission` callers; no changes to `CoursesRepositoryImpl`.

</details>

---

<a id="a04"></a>

### A04. extract survey search matching out of SurveysViewModel into a tested pure component (roadmap 3+8)

O dropped / D 61 / C 74 · raw: L09.07 · refs: Devin R093 · Codex T065

**Megaplan verdict, optional:** Opus: `SurveysViewModelTest.kt:154-245` already covers the ranking, so this is a pure relocation. It does serve roadmap 10 (state and logic hoisted out of ViewModels for a future Compose screen), so keep it as a roadmap-10 enabler, not as a quality fix.

**Codex scope (use this, not the raw version):**
- Extract survey search matching and adopted-survey sort-date selection into a pure tested component. Preserve normalization, prefix-before-contains ranking, stable ties, and adoptionDate fallback.

**Opus objection:** Premise false: SurveysViewModelTest.kt:154-245 already covers the ranking, so this is a pure relocation.

<details><summary>Devin's full work order (apply the corrections above)</summary>

**Rating:** 61 (evidence 4/5 · impact 2/5 · risk-adjusted feasibility 4/5)  
**Proposed by (1 list):** L09.7 (Copilot/Kimi K3, P2, completeness 3/5)  
**Canonical text:** L09.7 (most complete)

**Verified against `2093a6224`:**
- L09.7: private filter/getSortDate SurveysViewModel:201,209; SurveysViewModelTest exists (claim "no unit test" for ranking unverified)

context: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt` embeds two pieces of domain logic: `private fun filter(s: String, list: List<StepExam>)` (normalize query, rank startsWith matches above contains matches) and `private fun getSortDate(survey: StepExam)` (adopted surveys sort by adoptionDate when set). Both are pure functions of model data trapped in a Hilt viewmodel, untested, and unavailable to a future use-case layer (roadmap 3). There is no unit test covering the ranking behavior today.
files: app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt (edit); new app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveySearchMatcher.kt; new app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveySearchMatcherTest.kt. Leave alone: SurveysRepository.kt, SurveysRepositoryImpl.kt, ui/surveys fragment and adapter, utils/StringUtils.kt.
steps:
1. Create `internal object SurveySearchMatcher` in the new file with `fun sortDateOf(survey: StepExam): Long` and `fun filter(query: String, list: List<StepExam>): List<StepExam>` copied verbatim in behavior (same StringUtils.normalizeText usage, same startsWith-then-contains ranking).
2. In SurveysViewModel, replace the bodies of `getSortDate` and `filter` with delegation to SurveySearchMatcher (or delete the privates and call the matcher directly from applyFilterAndSort).
3. Keep the four SortOption branches and their ordering semantics exactly as-is.
4. Add SurveySearchMatcherTest covering: empty query returns input order, startsWith outranks contains, multi-word query requires all parts, adopted survey with adoptionDate > 0 sorts by adoptionDate, non-adopted sorts by createdDate.
5. Run the new test plus the existing surveys tests.
acceptance: ./gradlew testDefaultDebugUnitTest green (including the new SurveySearchMatcherTest); survey search and the date/title sort toggles behave identically on the surveys screen.
size budget: ~90 changed lines, 3 files
out of scope: no repository/DAO changes, no changes to adoptSurvey/sendSurveyToUsers, no UI layout changes, no new dependencies.

</details>

---

<a id="a05"></a>

### A05. combine dual ID/remoteId lookup and use injected Gson in FeedbackRepositoryImpl (roadmap 1+7, advances 9)

O dropped / D 56 / C 76 · raw: L18.08 · refs: Devin R101 · Codex T054

**Megaplan verdict, optional:** Opus rejected the raw version (binding `:ids` twice in 900-chunks exceeds 999 variables, and the injected Gson has `serializeNulls`). Codex's corrected scope (chunks of at most half the variable budget; use the existing `@PlainGson` instance, which `VoicesRepositoryImpl` / `SubmissionsRepositoryImpl` / `HealthRepositoryImpl` already inject) fixes both objections. Only do it with those corrections, and add a payload-equality test.

**Codex scope (use this, not the raw version):**
- Combine local/remote-ID matching into one bounded query with at most half the SQLite variable budget per chunk because the list is bound twice. Preserve current row-precedence and pending-local-change deduplication rather than relying on unspecified OR result order.
- Use the existing PlainGson instance for messages serialization, preserving the exact payload format.

**Opus objection:** Both parts are wrong as written: binding `:ids` twice in 900-chunks exceeds the 999-variable limit, and the injected Gson (serializeNulls) changes output.

<details><summary>Devin's full work order (apply the corrections above)</summary>

**Rating:** 56 (evidence 4/5 · impact 2/5 · risk-adjusted feasibility 3/5)  
**Proposed by (1 list):** L18.8 (Antigravity, P3, completeness 3/5)  
**Canonical text:** L18.8 (most complete)

**Verified against `2093a6224`:**
- L18.8: getByIds + getByRemoteIds two queries FeedbackRepositoryImpl:109

context: In `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt:109`,
`insertFeedbackList` executes two separate chunked Room queries back-to-back
(`(feedbackDao.getByIds(ids) + feedbackDao.getByRemoteIds(ids)).distinctBy { it.id }`), scanning
the `feedback` table twice for every synced batch of feedback documents. In addition, `fromJson`
at line 145 calls the static singleton `GsonUtils.gson.toJson(...)` even though
`@PlainGson private val gson: Gson` is already constructor-injected at line 18. Serving roadmap
1 and 7 while advancing north star 9 (clean, DI-backed repository layer), adding a single
chunked `getByIdsOrRemoteIds` query in `FeedbackDao` halves the SQLite queries during feedback
sync and eliminates the static `GsonUtils.gson` access.
files:
- touch: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt` (lookup queries at lines 45-63), `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt` (`insertFeedbackList` at lines 107-111, `fromJson` at line 145), `app/src/test/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImplTest.kt` (`insertFeedbackList` tests at lines 84-190)
- leave alone: `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackSyncWriter.kt`, `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt`
steps:
1. In `FeedbackDao.kt`, add `@Query("SELECT * FROM feedback WHERE id IN (:ids) OR _id IN (:ids)") suspend fun getByIdsOrRemoteIdsChunk(ids: List<String>): List<Feedback>` and a chunked wrapper `suspend fun getByIdsOrRemoteIds(ids: List<String>): List<Feedback> = ids.chunked(SQLITE_MAX_VARIABLE_NUMBER).flatMap { getByIdsOrRemoteIdsChunk(it) }.distinctBy { it.id }`.
2. In `FeedbackRepositoryImpl.kt:109`, replace `(feedbackDao.getByIds(ids) + feedbackDao.getByRemoteIds(ids)).distinctBy { it.id }` with `feedbackDao.getByIdsOrRemoteIds(ids)`.
3. In `FeedbackRepositoryImpl.kt:145`, replace `GsonUtils.gson.toJson(GsonUtils.getJsonArray("messages", act))` with `gson.toJson(GsonUtils.getJsonArray("messages", act))`.
4. Update the five `insertFeedbackList_*` tests in `FeedbackRepositoryImplTest.kt` (lines 84-190) to stub `feedbackDao.getByIdsOrRemoteIds(listOf("fb1"))` and verify all 9 tests in `FeedbackRepositoryImplTest` pass.
acceptance:
`./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.FeedbackRepositoryImplTest`
and `./gradlew testDefaultDebugUnitTest` pass; feedback sync resolves local and remote feedback
matches in a single chunked Room query per batch.
size budget: ~35 changed lines, 3 files
out of scope: do not remove `getByIds` or `getByRemoteIds` from `FeedbackDao.kt` and do not
modify `Feedback.kt`.

</details>

---

<a id="a06"></a>

### A06. Hoist invariant `getString`/`Calendar` calls out of hot lambdas (roadmap 7)

O dropped / D 49 / C 51 · raw: L13.10 · refs: Devin R110 · Codex T127

**Megaplan verdict, optional:** Opus rejected the raw version (sharing one `Calendar` collapses every marked day onto the last date). Codex keeps only the string hoist: resolve the untitled-course fallback once in `TeamCoursesFragment` and the empty health label once per render in `MyHealthFragment`. This is a micro-cleanup. Do NOT touch the `Calendar` part.

**Codex scope (use this, not the raw version):**
- Resolve the untitled-course fallback before mapping picker course names.
- Resolve the empty health label once per render/function and reuse it for the repeated fields, preserving locale changes between renders.

**Opus objection:** Its Calendar reuse would collapse every marked day onto the last meetup's date (each CalendarDay keeps its own Calendar). The getString premise is false (elvis-only).

<details><summary>Devin's full work order (apply the corrections above)</summary>

**Rating:** 49 (evidence 4/5 · impact 1/5 · risk-adjusted feasibility 5/5)  
**Proposed by (1 list):** L13.10 (Devin, P3, completeness 2/5)  
**Canonical text:** L13.10 (most complete)

**Verified against `2093a6224`:**
- L13.10: getString inside map TeamCoursesFragment:147, Calendar.getInstance per meetup CalendarFragment:48, empty_text x8 MyHealthFragment; micro

**context:** three fragments rebuild the same per-item constant inside `map`/bind lambdas: `TeamCoursesFragment.kt:147` calls `getString(…)` inside a `map` over courses, `CalendarFragment.kt:47-52` calls `Calendar.getInstance()` once per meetup in a `map`, and `MyHealthFragment.kt:150-153` + `:215-218` repeat `getString(R.string.empty_text)` six times. Each is an allocation or resource lookup repeated per item where one hoisted call suffices. Evidence: the arguments are identical on every iteration.

**files:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt` (`map` at `:147`), `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt` (`map` at `:47-52`), `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt` (`:150-153`, `:215-218`). Leave alone: every other statement in these functions, `HealthExaminationViewModel.kt` (task 1 owns it).

**steps:**
1. `TeamCoursesFragment`: hoist the `getString(R.string…)` above the `map` into a `val` and reference it inside the lambda.
2. `CalendarFragment`: hoist `Calendar.getInstance()` above the `map`; reuse the instance with `timeInMillis = …` per iteration (or swap to `Calendar.getInstance().timeInMillis` semantics the loop actually needs — keep the produced values identical).
3. `MyHealthFragment`: hoist `getString(R.string.empty_text)` into a single `val` near the top of each enclosing function (or a `private val` if the string is fragment-scoped) and reuse it at `:150-153` and `:215-218`.
4. Do not reorder anything else in the lambdas.

**acceptance:** `./gradlew :app:compileDefaultDebugKotlin` and `./gradlew testDefaultDebugUnitTest` green. User-visible: identical labels/dates; marginally fewer allocations on list binds and the health screen.

**size budget:** ~20 changed lines, 3 files.

**out of scope:** no adapter-level caching, no `ViewModel` changes, no other fragments, no new resource ids.

</details>

---

<a id="a07"></a>

### A07. Encapsulate the plain-community TeamSummary fallback

O dropped / D — / C 48 · raw: L07.08 · refs: Devin — · Codex T130

**Megaplan verdict, optional:** Opus and Devin: the main narrowing is already done (`ChatViewModel` injects `UserReadRepository` and `TeamsShareRepository`). Codex keeps only a named `TeamSummary` factory for the plain-community fallback. Cosmetic.

**Codex scope (use this, not the raw version):**
- Provide a named TeamSummary factory for the exact plain-community fallback and use it from ChatViewModel, retaining all current nullable/default fields.

**Opus objection:** Already done: ChatViewModel injects UserReadRepository and TeamsShareRepository (ChatViewModel.kt:56-57).

---

## Appendix B: rejected (do not implement)

| raw | task | kept by | megaplan reason |
|---|---|---|---|
| L15.09 | rethrow cancellation instead of Result.retry() in NetworkMonitorWorker (roadmap 5+8) | Devin | Dropped by Opus and Codex (2 of 3). Opus: Premise false: the try block contains no suspension point, and WorkManager ignores a stopped worker's Result. Codex: NetworkMonitorWorker.doWork calls only a synchronous scheduling function with no suspension or explicit cancellation check. Stopping a worker does not inject CancellationException into this code, and a stopped WorkManager result is ignored rather than automatically re-enqueueing itself as claimed. A defensive catch alone does not address a demonstrated cancellation bug here. |
| L12.06 | wrap TeamLogDao.deleteByIds in @Transaction and short-circuit on empty input (roadmap 1+7+8) | Devin, Codex | Verified: the only `teamLogDao.deleteByIds` caller (`TeamsRepositoryImpl.kt:1173`) already runs inside the enclosing transaction block, and `chunked(emptyList())` issues no SQL. |
| L05.07 | Cap the notification read sync at the file's existing concurrency limit (roadmap 5+7) | Devin, Codex | Verified: `NetworkModule.kt:101/117` already caps OkHttp at `MAX_REQUESTS_PER_HOST = 20`, so the 'hundreds of concurrent requests' premise does not hold. A semaphore would only duplicate an existing limit. |
| L16.02 | Coalesce active team-course loads and cancel superseded requests (roadmap 3+7) | Devin, Codex | Verified: `TeamCoursesFragment.updateCoursesList()` calls `loadCourses(teamId, …)` with the fragment's fixed `teamId`, and the ViewModel is fragment-scoped, so the 'switch teams, stale result wins' race cannot happen. Coalescing identical calls risks swallowing the refresh after add/remove. Codex's 90/100 is its largest overrating. |
| L01.02 | hoist shared reads out of upload serializer lambdas (roadmap 5+7) | Devin | Dropped by Opus and Codex (2 of 3). Opus: Its per-run snapshot is captured by @Singleton `val` configs, so it would freeze the device name and planet codes for the process lifetime. androidId is already cached (VersionUtils.kt:55). Codex: The hotspot is real, but the proposed per-run capture/memoization cannot be scoped inside the existing serializer lambda: UploadConfigs is a singleton and its configs are val instances reused between runs. Capturing at construction risks stale user/device/server data; changing the lifecycle plumbing is expressly excluded. |
| L07.09 | Push patient-list sorting into SQLite ORDER BY (roadmap 1, 7, 9) | Devin | Dropped by Opus and Codex (2 of 3). Opus: SQL NOCASE/NULL ordering differs from Kotlin lowercase and stable sort, and the table is small. It also states several wrong facts (dob/age are strings; the full list is still returned). Codex: SQLite COLLATE NOCASE is not equivalent to the existing Kotlin Unicode-aware lowercase/string ordering, including null/empty and Unicode cases. The proposed SQL rewrite changes the patient-list sort contract without an equivalent collation; precomputed Kotlin keys already address the verified repeated normalization. |
| L09.09 | resolve latest chat revision with a SQL MAX query instead of loading all revs (roadmap 7+1) | Devin | Dropped by Opus and Codex (2 of 3). Opus: Premise false: ChatHistory id == _id (ChatRepositoryImpl.kt:186-187), so there is at most one rev row. It would also move logic out of five unit tests. Codex: The suggested CAST/substr query does not preserve the existing maxByOrNull/toIntOrNull contract: null candidates are removed, plain numeric revisions lack a delimiter, malformed/overflow generations differ, and ties have no equivalent first-row rule. No schema constraint guarantees the narrower input grammar. Retain the safe Kotlin extraction cleanup instead. |
| L01.09 | memoize `formatLabelValue` in `VoicesLabelManager` (roadmap 7) | Devin, Codex | Opus: known labels short-circuit via `LABEL_VALUE_TO_NAME`, so `formatLabelValue` only runs for custom labels. Codex adds that a locale-independent cache would be wrong. Not a hot path. |
| L02.06 | cache downloaded-file hits while course-step resources scroll (roadmap 7) | Devin | Dropped by Opus and Codex (2 of 3). Opus: The cache semantics are wrong: the two exists checks use different paths (resourceId vs id), so one hit cannot stand in for the other. Codex: A positive filesystem-hit cache invalidated only by list/address changes and detach cannot preserve the existing fresh existence check after a file is deleted or replaced without a list change. The list/revision key does not establish file existence. |
| L04.07 | Charge every PDF thumbnail a positive cache weight (roadmap 7+8) | Devin, Codex | Opus: thumbnails render at about 240dp (hundreds of KiB), so the KiB-floor error is under 0.5% of the budget. This is a correctness nit with no observable effect. |
| L18.09 | deduplicate single-link tag lookup and short-circuit empty tag names in TagsRepositoryImpl (roadmap 1+7, advances 9) | Devin, Codex | Opus: the caller already guards empty names, the proposed step returns the wrong type, and the shared helper changes tag order. Codex's corrected scope is a micro-cleanup with ordering risk. |
| L18.02 | defer collaborator API pagination until non-filtered coauthors exist (roadmap 8) | Devin, Codex | Verified: `coauthors.sh` documents the up-front collaborator fetch as a deliberate fail-fast ('Needs push access; failing here beats crediting nobody'). Making it lazy hides a missing-permission failure. |
| L13.08 | Cache `englishValues` locale contexts in `CoursesFilterFragment` (roadmap 7) | Devin, Codex | Opus: `englishValues` is a companion function with 5 external callers and runs 2-4 times per tap. A configuration cache adds invalidation risk for no measurable gain. |
| L06.01 | replace multi-select answer dual-list sort with frequency-map comparison (roadmap 7+8) | Devin, Codex | Opus: `groupingBy/eachCount` allocates two `LinkedHashMap`s with boxed counts, versus sorting 2-6 strings. Not cheaper. |
| L06.05 | pre-normalize facet filter sets once per pass in ResourcesListFilter (roadmap 3+7) | Devin, Codex | Opus and Codex agree that lowercase-set membership is not equivalent to `equals(ignoreCase = true)` for all Unicode, and it needs per-item lowercasing. No win over 0-3 criteria. |
| L06.06 | avoid full-list remap and recount on single-item toggle in StorageCategoryViewModel (roadmap 3+7) | Devin, Codex | Verified: `toggleItemChecked` maps to an equal list for an unknown id, and `MutableStateFlow.update` suppresses equal values, so the 'still emits' premise is false. Swapping one O(n) pass for another on a settings screen is not worth a PR. |
| L03.03 | Memoize profile-image Base64 encoding behind UserEntity.serialize (roadmap 5+7, supports 9) | Devin, Codex | Opus: the per-instance memo never hits, because each upload gets a fresh `UserEntity` from Room (`UploadConfigs.kt:242/257`). Codex's batch-snapshot rescue is a different, unspecified design. |

Raw tasks that no plan shipped: L07.07, L08.01, L08.02.

