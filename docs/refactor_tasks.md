### T001 — Propagate cancellation from submission payload assembly · 95/100

- Rethrow CancellationException from submissionPayload before ordinary payload-error fallback; mirror the existing team-lookup helper.
- Replace payload-build printStackTrace with a controlled diagnostic while retaining the existing ordinary-error fallback contract.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt`.

---

### T002 — Enforce the caller owner when reordering My Life items · 94/100

Normalize the supplied caller owner, select only stored rows belonging to that owner, and update/cache only that scope. Do not derive ownership from UI-supplied first-row data. Preserve duplicate-ID handling and unscoped NULL/empty/-- ownership semantics.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`.

---

### T003 — Await health uploads inside their owning workers · 93/100

Provide a suspend health-upload operation and await it from UserDataWorker/AutoSyncWorker child work before completion. Adapt callback bridges so they resume only after required work, preserving cancellation and existing retry/error policy rather than launching health work in applicationScope.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`, `app/src/main/java/org/ole/planet/myplanet/services/UserDataWorker.kt`, `app/src/main/java/org/ole/planet/myplanet/services/AutoSyncWorker.kt`.

---

### T004 — Make stale-question cleanup bounded and atomic · 93/100

- Run represented-exam stale cleanup in one transaction, retaining null-exam skipping and empty-input no-op behavior.
- Read stored question IDs per represented exam, subtract the full incoming keep set, then delete only stale IDs in bounded positive batches constrained to that exam. Never chunk a NOT IN keep-list; leave unrepresented exams untouched.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt`.

---

### T005 — Determine dictionary readiness from persisted rows first · 93/100

- Under the seed lock, check whether dictionary rows already exist before requiring the import file; preserve first-import, failure, and cancellation results.
- Remove the redundant DictionaryActivity loadCount launch and let loadDictionary publish the count.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`.

---

### T006 — Bound stale course-step deletion using stale positive IDs · 92/100

In one transaction, read stored step IDs for the course, subtract the complete keep set, and delete stale IDs in bounded positive batches. Preserve empty-keep delete-all semantics and course isolation; never split a NOT IN keep-list into independent deletes.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseStepDao.kt`.

---

### T007 — Parent course-step preparation under its load job · 91/100

Launch priority-resource preparation and next-step prefetch as structured children of loadDataJob, so a superseding step cancels their suspended preparation. Preserve downloads already handed to the independent download service and the current-step state guards.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`.

---

### T008 — Make retry cancellation-transparent and remove unstructured exhaustion reporting · 91/100

- Rethrow CancellationException before the generic catch, including the last-attempt case, and preserve retry predicates, count, and delay behavior.
- Remove the exhaustion printStackTrace. If reporting is required, use a platform-independent diagnostic hook or caller-owned reporting policy; do not add android.util.Log to this otherwise Android-free helper.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt`.

---

### T009 — Gate the expired challenge before starting repository work · 91/100

Evaluate the existing guest, URL, and exclusive date-window conditions before launching the five challenge reads. Emit the existing no-dialog result when gated out; retain behavior for dates within the configured window.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt`.

---

### T010 — Bound concurrent notification-read sync requests · 91/100

Use one semaphore for the notification-read sync batch and a small fixed request limit. Retain 404 handling, successful revision collection, cancellation propagation, and the single final database acknowledgement.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`.

---

### T011 — Bound text preview input before reading whole lines · 91/100

Read at most a fixed character budget before selecting the first eight preview lines. Preserve line ordering, empty-file behavior, charset handling, and cache keys; ensure a giant single-line file never enters the cache in full.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt`.

---

### T012 — Bind resource dialogs and path capture to the view lifetime · 90/100

- Launch the resource download dialog from viewLifecycleOwner.lifecycleScope so it cannot outlive onDestroyView.
- Capture a valid context/external path before the IO hop in openHtmlResource and handle unavailable storage explicitly, avoiding File(null, child) falling back to a relative path.

Scope: `app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt`.

---

### T013 — Coalesce duplicate and cancel superseded team-course loads · 90/100

Track the active team/user request and job, coalesce an identical active request, and cancel or reject stale results when inputs change. Retain the dependency from team course IDs to getCoursesByIds and the independent creator lookup.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt`.

---

### T014 — Preserve cancellation and explicit failure reporting during examination saves · 90/100

- Rethrow cancellation from saveExamination before sending a failed-save result; preserve ordinary failure reporting.
- Replace the save failure printStackTrace with the existing tagged logger.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt`.

---

### T015 — Preserve cancellation and diagnostics on resource reloads · 89/100

- Rethrow cancellation from loadResources before ordinary-error fallback.
- Replace the loadResources stack dump with tagged reporting while preserving ordinary failure behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`.

---

### T016 — Make course-load errors observable without swallowing cancellation · 88/100

- Replace the CoursesViewModel loadCourses stack dump with tagged diagnostics while preserving its public state contract.
- Replace TakeCourseViewModel.loadCourse stack dumps with tagged diagnostics.
- Rethrow cancellation in loadCourse before ordinary error handling.
- Rethrow cancellation from getRatingPromptDecision rather than returning its ordinary fallback.
- Move a failed ordinary course load out of Loading through an explicit existing-compatible failure state.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`.

---

### T017 — Avoid hydrating course steps for scalar title and shelf-ID reads · 88/100

Read bare course rows for getCourseTitleById, the title in getCourseProgress, and getMyCourseIds. Preserve blank-ID, membership-pattern, remote/local-ID, ordering, and null behavior; retain full hydration for callers that need steps.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`.

---

### T018 — Index resource and tag lookup paths on every list replacement · 87/100

Rebuild resource-ID, local-address, and tag-ID indexes whenever allResourceModels is assigned, including sorting. Replace click/selection scans and tag flattening while preserving the current first-match behavior for duplicate keys.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt`.

---

### T019 — Persist submission revision and upload-result batches transactionally · 87/100

- Wrap per-page revision updates in one Room transaction, preserving pending-local-change and duplicate-cleanup rules.
- Wrap markSubmissionsUploaded writes in one transaction and retain the failed-result list for missing local rows.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`.

---

### T020 — Charge every PDF thumbnail a positive rounded cache weight · 87/100

Use positive rounded-up KiB bitmap weights, with overflow-safe arithmetic. Preserve the existing cache budget and thumbnail key; verify sub-KiB and partial-KiB entries count toward eviction.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`.

---

### T021 — Use one normalized notification group key for expansion · 86/100

Share the existing lowercase/known-type fallback normalization between grouping and default-expansion checks. Reuse a current matching group unread count only when it corresponds to the current notification snapshot; preserve correct first-toggle behavior for unknown/mixed-case types.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`.

---

### T022 — Use grouped course-step counts for progress summaries · 86/100

Read course-ID/count projections instead of full CourseStep bodies when calculating per-course max/current progress. Preserve the existing step-count arithmetic, missing-course zeros, and passed-progress treatment.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`.

---

### T023 — Count survey questions in grouped SQL projections · 86/100

Add a chunked exam-ID/count projection and use it for survey-form badges, filling zero-question surveys with zero. Retain team submission mapping and avoid loading question bodies/choices merely to count them.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt`.

---

### T024 — Batch submission and answer reads for earned exam passes · 85/100

Fetch the relevant user attempts and their answers in bounded batches, then group by parent ID for saveEarnedExamPasses. Preserve complete-status selection, parent construction, passing-required rules, and answer-based grading. Use an exam-type-only batch API only if equivalent to the original parent/status selection; otherwise add a bounded matching query. Index required exams by step with first-valid-match semantics.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`.

---

### T025 — Commit upload acknowledgement batches transactionally · 85/100

Move per-row upload acknowledgement loops behind repository/DAO batch operations and one transaction per table/batch. Preserve each config failure-list contract, row-count handling, and existing revision merge behavior; reuse existing batch APIs rather than nesting network work inside a transaction.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`.

---

### T026 — Overlap independent course-opening reads · 84/100

- Start current-user and course reads concurrently; preserve NotFound handling and avoid publishing after cancellation.
- Overlap step and progress reads after their inputs are ready; compute earned passes and blocking-step state only after those results.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`.

---

### T027 — Batch survey question and submission existence checks · 83/100

Replace per-survey hasSubmission calls with bounded set/count queries for the requested course and user. Preserve blank/null-input exits, userId IS semantics where reached, survey parent IDs, question-count gating, status/type distinctions, and the difference between unfinished and pending checks.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt`.

---

### T028 — Batch initial automerge PR metadata reads · 83/100

Fetch state, reviewDecision, and initial mergeable together for a selected PR. Reuse that snapshot for the adjacent checks, preserving unknown-mergeability polling and refreshing metadata after head/base changes.

Scope: `.github/scripts/automerge.sh`.

---

### T029 — Avoid redundant user reads, writes, and key generation for health data · 83/100

- Generate missing security keys only when needed, skip upsert when both existing values are already present, and defer saveKeyIv generation until the corresponding nonempty value is absent. Preserve the existing null-versus-empty policies of each method.
- Use ensureUserSecurityKeys as the primary patient-user read and fall back only as required by the existing null behavior, avoiding the unconditional duplicate fetch.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt`.

---

### T030 — Prepare course-detail storage paths and markdown off main · 83/100

Resolve the external path on the injected IO dispatcher and rewrite course-description image markdown on the compute dispatcher. Publish state only for the active collection and retain null-directory and load-error behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt`.

---

### T031 — Load the team voice feed once and count for its read watermark · 83/100

Keep the discussions Flow as the feed source; replace the duplicate one-shot feed read with countTopLevelByTeam for the seen watermark. Preserve count-to-Int bounds, team identity, badge semantics, and initial empty rendering.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`.

---

### T032 — Measure quota epochs once per forecast · 83/100

Measure release epochs once per forecast and format both its report and future slots from that same snapshot instead of calling status and fetching again.

Scope: `.github/scripts/playstore-quota.sh`.

---

### T033 — Amortize UserRepositoryImpl test instrumentation with isolated stubs · 82/100

- Install recurring UrlUtils, NetworkUtils, and Log instrumentation once per class. Reset answers/calls before each test and retain the later Log.e assertions.
- Avoid repeatedly installing SecurePrefs instrumentation in individual cases; reset and restore it without leaking stubs or weakening error-path assertions.

Scope: `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt`.

---

### T034 — Apply selective resource predicates before ranked title search · 81/100

Filter by tag, facets, and download state before ranked title search. Preserve prefix/contains priority and stable order among surviving results for apply and countMatching.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt`.

---

### T035 — Move notification grouping and flattening off main · 81/100

Run notification grouping and combined row flattening on the injected compute dispatcher before stateIn, preserving order, selection/expansion updates, subscription behavior, and immutable snapshots.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`.

---

### T036 — Reduce repeated LoginSyncManager test instrumentation · 80/100

- Remove unused Android Log/Base64 instrumentation after checking indirect call paths; UrlUtils already uses java.util.Base64. Keep any needed logging assertion instrumentation.
- Install necessary UrlUtils/AndroidDecrypter object instrumentation at class scope and reset answers, recorded calls, repositories, and coroutine state per test; use targeted teardown.

Scope: `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt`.

---

### T037 — Sort resource snapshots on the compute dispatcher with one hop · 80/100

- Use the injected default dispatcher for the three pure sorting entry points.
- Capture sort settings on the owning thread, perform one compute hop through the synchronous helper, and publish results without racing sort-mode fields or nesting dispatcher hops.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`.

---

### T038 — Request only display-name metadata when resolving picked files · 80/100

Pass a DISPLAY_NAME-only projection to both content-provider queries. Preserve missing-column, null-cursor, URI, fallback-name, and traversal-sanitization behavior; update strict projection expectations in tests.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`.

---

### T039 — Inject the existing clock into public-survey recency checks · 80/100

Inject TimeProvider, replace the wall-clock read, and use a fixed clock in launch/recency tests. Preserve the existing stale-submission event and timestamp comparison exactly.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/di/TimeModule.kt`.

---

### T040 — Move retry-event Android construction to the service boundary · 79/100

- Give BroadcastService a retry-event operation that constructs the Intent and preserves encoded path, attempt, delay, and nonblocking emission. Keep interceptor retry/backoff policy independent of Intent construction.
- Exercise retry-event emission policy in the existing plain JVM interceptor tests without a separate one-test Robolectric class.
- Retain actual action/extras and credential-exclusion coverage at the Android BroadcastService boundary when relocating the old test.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt`, `app/src/main/java/org/ole/planet/myplanet/services/BroadcastService.kt`, `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorEventTest.kt`.

---

### T041 — Load collaborators only when coauthor filtering needs them · 79/100

Collect/filter candidate logins first and lazily fetch the paginated collaborator set only for a surviving candidate. Preserve case normalization, bot/author/owner filtering, deduplication, and API-failure behavior once the lookup is required.

Scope: `.github/scripts/coauthors.sh`.

---

### T042 — Rewrite course-step markdown on the compute dispatcher · 79/100

Resolve paths on IO, then rewrite image markdown and prepare resource subsets on the compute dispatcher before publishing active-step state. Preserve structured cancellation and existing download predicates.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`.

---

### T043 — Remove dead exam-test instrumentation and isolate the required device-ID mock · 79/100

- Remove the unused TextUtils static mock and stub from StepExamTest.
- Install the required NetworkUtils instrumentation at class scope with per-test resets, or scope it only to serialization cases; preserve the exact existing device-ID answer and targeted cleanup.

Scope: `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt`.

---

### T044 — Select the publish check directly instead of listing every run jobs · 78/100

Find completed matching publish checks from the SHA check-run list, then read annotations using the actual check-run ID. Preserve pagination, job-name/app selection, marker filtering, track verification, and fresh-state decisions; avoid treating an Actions job ID as guaranteed interchangeable with a check-run ID.

Scope: `.github/scripts/automerge.sh`.

---

### T045 — Overlap independent chat share-target reads · 78/100

- Start team and enterprise summary reads concurrently.
- Resolve the optional community ID first and overlap its summary lookup too, retaining the existing fallback TeamSummary and null-ID behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`.

---

### T046 — Prepare the team course picker off main · 78/100

Inject DispatcherProvider into TeamCoursesViewModel and move available-course loading/mapping off the fragment main dispatcher, using IO for blocking storage preparation and default for filtering. Preserve exclusion IDs and list order.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt`.

---

### T047 — Branch scoped My Life queries before entering SQL · 78/100

Dispatch nullable/unscoped and non-null exact-user lookups to separate DAO queries for observe/get/visible/count methods. Preserve the null branch matching NULL, empty, and -- owners and its weight order; repository normalization stays unchanged.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`.

---

### T048 — Move team-task filtering and assignee eligibility out of the fragment · 78/100

Extract pure task-state/current-user filters and assignable-member selection behind TeamsTasksViewModel or a pure collaborator. Retain sort/state/user rules and have the fragment consume prepared values.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksViewModel.kt`.

---

### T049 — Drain persisted download-link buffers safely · 77/100

Deduplicate pending links and stop re-merging an unchanged process-lifetime buffer on every page. Serialize snapshot/merge/write/drain across both writers of the same preference so concurrent arrivals and failed writes cannot lose links; skip empty snapshots.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/model/MyCourse.kt`.

---

### T050 — Reduce repeated news-sync test setup while keeping real Room coverage · 77/100

- Reuse the in-memory Room database within a compatible Robolectric test-class sandbox, clearing all relevant tables and state before each case and closing only at class teardown. Verify shuffled/repeated isolation before adopting the reuse.
- Install UrlUtils/NetworkUtils object instrumentation once per class, remove the redundant in-test installation, and reset answers/calls per case.

Scope: `app/src/test/java/org/ole/planet/myplanet/repository/VoicesRepositoryNewsSyncTest.kt`.

---

### T051 — Overlap independent course-detail reads · 77/100

Use structured child coroutines for independent user, exam-count, resource, and step reads in getCourseDetailModel. Keep question-count work dependent on step IDs and rating work dependent on the user; retain flow cancellation and null-course handling.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`.

---

### T052 — Remove Context-based notification text formatting from the ViewModel · 77/100

Move localized row text to a UI-owned formatter/string provider or explicit UI text descriptors. Keep grouping and raw enrichment in the ViewModel, preserve locale-aware arguments and existing formatting output, and make tests assert real text.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`.

---

### T053 — Simplify submission-detail author dependencies and state sharing · 77/100

- Inject UserReadRepository for the single submission-author lookup.
- Map the one-shot submission detail directly into uiState and share it once, retaining null/loading/error semantics and WhileSubscribed behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModel.kt`.

---

### T054 — Combine feedback sync identity lookups safely and use injected Gson · 76/100

- Combine local/remote-ID matching into one bounded query with at most half the SQLite variable budget per chunk because the list is bound twice. Preserve current row-precedence and pending-local-change deduplication rather than relying on unspecified OR result order.
- Use the existing PlainGson instance for messages serialization, preserving the exact payload format.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/FeedbackDao.kt`.

---

### T055 — Cache parsed voice reactions by their raw stored strings · 76/100

Cache reaction JSON/maps and pending-reaction maps using their raw-string inputs. Return defensive copies or immutable snapshots, invalidate on mutation, and preserve malformed/null fallbacks.

Scope: `app/src/main/java/org/ole/planet/myplanet/model/News.kt`.

---

### T056 — Avoid real drawable decoding in ImageUtils interaction tests · 76/100

Stub the image-resource setter on interaction-test spies while retaining clear-before-placeholder verification and resource IDs. Share static Glide instrumentation only with per-test call/answer resets and targeted teardown. Keep rendering tests separate where drawable decoding is the asserted behavior.

Scope: `app/src/test/java/org/ole/planet/myplanet/utils/ImageUtilsTest.kt`.

---

### T057 — Seed a compatible default-branch debug build cache · 75/100

Add a master/default-branch cache producer compatible with both debug flavor keys and restore paths, reusing existing Gradle setup and failure handling. Preserve branch isolation and validate actual warm task reuse before reporting compile savings.

Scope: `.github/workflows/build.yml`.

---

### T058 — Precompute user sort keys while preserving Kotlin ordering · 75/100

Normalize each selected string field once per user before stable sorting. Preserve the existing invariant-locale lowercase policy, empty/null treatment, boolean/numeric branches, descending behavior, and unsupported-field behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`.

---

### T059 — Parse in-memory chat JSON on the compute dispatcher · 75/100

Replace the IO dispatcher for already-in-memory conversation JSON parsing with the injected default dispatcher. Keep pagination state publication on its owner thread and preserve malformed/empty input behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatConversationPaginator.kt`.

---

### T060 — Narrow course-screen user reads and remove an unused session injection · 75/100

- Inject UserReadRepository into TakeCourseViewModel.
- Inject UserReadRepository into CoursesStepsViewModel.
- Remove the never-read UserSessionManager injection and import from TakeCourseFragment.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt`.

---

### T061 — Narrow home-area identity reads to UserReadRepository · 75/100

- Narrow CalendarViewModel to UserReadRepository; its user dependency only obtains the current user.
- Narrow CommunityTabViewModel to UserReadRepository.
- Narrow DashboardElementViewModel to UserReadRepository.
- Narrow ActivitiesViewModel to UserReadRepository.
- Narrow PersonalsViewModel to UserReadRepository.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`.

---

### T062 — Narrow resource-screen identity reads to UserReadRepository · 75/100

- Narrow ResourcesAccessViewModel while retaining its existing guest test and resource operations.
- Narrow ResourcesViewModel, whose user dependency only resolves the current user.
- Narrow ResourceViewerViewModel while preserving rating-prompt identity behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/base/ResourcesAccessViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt`.

---

### T063 — Make chunked team-log deletion atomic · 75/100

Annotate the chunk orchestration with Transaction, retain distinct IDs and bounded positive deletes, and return early for empty input. Verify rollback across more than one chunk.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt`.

---

### T064 — Compare multi-select answers as normalized multisets · 74/100

Replace dual list sorting with frequency counts under the same locale and size checks. Preserve duplicate multiplicity, null behavior, and normalized grading results.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt`.

---

### T065 — Extract pure survey ranking and sort-date rules · 74/100

Extract survey search matching and adopted-survey sort-date selection into a pure tested component. Preserve normalization, prefix-before-contains ranking, stable ties, and adoptionDate fallback.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt`.

---

### T066 — Centralize guest creation and authentication orchestration · 73/100

- Move the repeated guest-creation/session-save sequence behind LoginViewModel and return typed outcomes to the three UI entry points. Keep UI navigation, dialogs, and lifecycle ownership at the view boundary.
- Move authenticate/has-user/session-save orchestration out of SyncActivity into a reusable login-owned operation, preserving all AuthUtils callers, saved credentials, cancellation, and manager mode.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt`.

---

### T067 — Amortize reachability-worker test mocks while retaining log assertions · 73/100

Install logging/network/application instrumentation once per class, reset answers and calls per case, and preserve cancellation-is-silent and failure-is-logged assertions. Replace global teardown with target-specific cleanup.

Scope: `app/src/test/java/org/ole/planet/myplanet/services/ServerReachabilityWorkerTest.kt`.

---

### T068 — Overlap independent joined-member and current-user reads · 73/100

Use structured child coroutines for member and current-user reads in RequestsViewModel.loadJoinedMembers, then publish one state with the existing leadership predicate.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`.

---

### T069 — Use the shared Robolectric configuration for PDF thumbnail tests · 73/100

Replace the SDK-28-only pin with the project default sandbox and lightweight Application/manifest configuration, confirming the thumbnail tests do not rely on API-28 behavior. Retain IO-thread assertions and actual missing/non-PDF coverage.

Scope: `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`.

---

### T070 — Amortize image-encoding test instrumentation and restore context per case · 72/100

Install the needed URI/static instrumentation once per class and reset stubs/calls per test. Continue restoring MainApplication context for every case, including failures; remove unused instrumentation only after checking its verification sites.

Scope: `app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt`.

---

### T071 — Reuse the shared image viewer and image dimensions · 72/100

- Delegate VoicesAdapter zoomable images to ImageViewerUtils and remove the duplicate dialog implementation, preserving local/GIF/error handling.
- Resolve thumbnail dimensions and margins once per adapter display configuration and reuse them in the four image helpers.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/ImageViewerUtils.kt`.

---

### T072 — Seed My Life once and observe a single list source · 72/100

Use seedMyLifeIfEmpty followed by the DAO-backed Flow instead of a full get-list followed by the same observed list. Preserve default seeding, user normalization, de-duplication, and empty-state ordering; account for the seed method own count/return reads.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`.

---

### T073 — Filter empty community names in the DAO · 72/100

Move the exact TextUtils.isEmpty name predicate into CommunityDao.getAllSorted and remove the UI filter. Preserve weight ordering and whitespace-only names; TextUtils.isEmpty does not trim whitespace.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CommunityDao.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt`.

---

### T074 — Return the updated meetup without re-reading it · 72/100

Return the mutated entity after successful synchronous upsert in toggleAttendance. Preserve blank meetup/user handling and avoid claiming a concurrency snapshot stronger than the previous implementation.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt`.

---

### T075 — Promote the active-user ID read and narrow LifeViewModel · 72/100

Move getCurrentUserId to UserReadRepository and inject that contract into LifeViewModel. Preserve blank/-- normalization and getUserModel fallback; update the mock contract and rebuild callers rather than claiming binary compatibility automatically.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt`.

---

### T076 — Use the My Life visibility UPDATE result instead of a pre-read · 72/100

Return an affected-row count from the exact-ID visibility update and fall back to the existing unique-alias resolution only when zero rows match. Preserve the transaction, ambiguous-alias no-op, and already-equal-value behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`.

---

### T077 — Avoid re-encoding unchanged profile images during serialization · 71/100

Memoize avatar bytes/Base64 only with a bounded lifetime and a key that distinguishes path/URI content changes, user, and access mode. Do not cache unreadable results indefinitely; retain serializer output and malformed/missing-image behavior. Prefer an explicit batch snapshot where content URIs lack trustworthy metadata.

Scope: `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt`.

---

### T078 — Reuse the membership fetch when assembling team members · 71/100

Fetch membership rows once for member hydration and leader IDs. Preserve pending-deletion filtering and first-seen user order; retain the separate all-document admin-membership check unless equivalence is established.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`.

---

### T079 — Precompute chat sort keys and avoid revision splitting · 71/100

- Decorate each chat with its parsed maximum created/updated timestamp once, stable-sort those keys, and restore the chat list.
- Replace split-to-first-segment in revision generation extraction with substringBefore while retaining nullable, invalid, overflow, and first-winner tie semantics.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt`.

---

### T080 — Expose consumed user reads through the read contract · 71/100

Promote getUserProfile for RatingsViewModel, hasActiveUserSyncAction for MarkdownViewModel, and the sorted/search reads for HealthViewModel to UserReadRepository. Move declarations consistently rather than duplicating inherited signatures without override; retain current implementations and Hilt bindings.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt`.

---

### T081 — Resolve the active chat user through the repository boundary · 71/100

Remove ChatHistoryFragment preference-based user-ID orchestration and let ChatViewModel resolve the current user through getUserModel. Preserve explicit-user behavior where retained, account switching, null user handling, and refresh cancellation.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt`.

---

### T082 — Migrate four selected Mockito-based tests to the declared MockK stack · 71/100

Migrate SubmissionDetailViewModelTest, CoursesAdapterTest, ResourcesAdapterTest, and DashboardSurveysAdapterTest to MockK without weakening assertions, interaction counts, or callback capture. Retain any required Robolectric runner.

Scope: `app/src/test/java/org/ole/planet/myplanet/ui/submissions/SubmissionDetailViewModelTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesAdapterTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapterTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapterTest.kt`.

---

### T083 — Use injected scope and Activity resources in SyncActivity · 71/100

Inject the existing ApplicationScope for the post-login watcher instead of reading MainApplication.applicationScope, and use Activity resource lookup where appropriate. Preserve watcher lifetime, cancellation, and reachability behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt`.

---

### T084 — Index test filenames once for source-based timing diagnostics · 71/100

Build one source filename index and cache content reads within a diagnostic invocation. Preserve traversal precedence for duplicate filenames and all existing warnings/thresholds. This targets the explicit test-sources option rather than claiming the current workflow enables it.

Scope: `.github/scripts/test_timing_summary.py`.

---

### T085 — Narrow community-service team dependencies · 71/100

Expose getTeamLinks through the share contract and inject the share and member read contracts into CommunityServicesViewModel. Keep current list membership semantics and existing Hilt implementation bindings.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/TeamsShareRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/TeamsMembersRepository.kt`.

---

### T086 — Remove unasserted model-test logging instrumentation · 71/100

- Remove unused Log mocks from HealthExaminationTest and retain all malformed-data fallback assertions.
- Remove unused Log mocks from NewsTest while retaining targeted cleanup for its in-test FileUtils object mocks.

Scope: `app/src/test/java/org/ole/planet/myplanet/model/HealthExaminationTest.kt`, `app/src/test/java/org/ole/planet/myplanet/model/NewsTest.kt`.

---

### T087 — Move shelf-sync user reads onto the sync contract · 71/100

Move getSyncedUsers, getSyncedUserByName, and getPendingSyncUsers declarations to UserSyncRepository and rewire the three shelf-upload calls. Keep the remaining getUserByName dependency until a contract that preserves its semantics is available; update service mocks.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserSyncRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/services/UploadToShelfService.kt`.

---

### T088 — Fetch only release fields used by quota estimation · 70/100

Request only release draft/publication fields through a suitably paged server-side projection while preserving the current release selection, ordering, and injected RELEASES_JSON behavior. Do not drop republished releases by assuming publication and creation order are identical.

Scope: `.github/scripts/playstore-quota.sh`.

---

### T089 — Update one storage checkbox without a full recount · 70/100

Find the matching item, leave unknown IDs unchanged, copy only the list/item needed, and adjust checkedCount incrementally. Preserve behavior if duplicate resource IDs are possible, rather than assuming uniqueness without evidence.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt`.

---

### T090 — Reduce waste in submission flow comparison and survey-exam lookup · 70/100

- Add a nullable LIMIT 1 survey-exam lookup for isStepCompleted while retaining the list API used by courses.
- Compare equal-sized submission lists by index instead of allocating zip pairs, retaining the same id/lastUpdateTime equality rule.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`.

---

### T091 — Skip empty tag queries and share parent-tag resolution · 70/100

- Return the correct empty list/set boundary result before querying tag names when the input is empty.
- Share parent-tag lookup preparation between single/bulk paths without losing per-link ordering and deduplication; reuse parsed _id and attachedTo values during tag mapping.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TagDao.kt`.

---

### T092 — Use tagged diagnostics for sync failures · 70/100

Replace TransactionSyncManager printStackTrace calls with operation-specific tagged diagnostics, retaining cancellation rethrows and existing failure results. Avoid logging credentials or full payloads.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`.

---

### T093 — Remove unused logging instrumentation from worker and download tests · 70/100

- Remove unasserted Log instrumentation from FreeSpaceWorkerTest, retaining required FileUtils isolation and targeted cleanup.
- Remove unasserted Log instrumentation from RetryQueueWorkerTest, retaining WorkManager/application mocks.
- Remove unasserted Log instrumentation from DownloadServiceTest, retaining ContextCompat/DownloadUtils mocks.
- Remove unasserted Log instrumentation from DownloadServiceOnDownloadCompleteTest without dropping its required FileUtils/DownloadUtils teardown.

Scope: `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceOnDownloadCompleteTest.kt`.

---

### T094 — Precompute resource facet comparison keys · 69/100

Precompute selected language, subject, and level criteria once per filter pass. Use comparison keys proven equivalent to the existing ignoreCase matching, including non-ASCII edge cases; retain all-versus-any semantics and null behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt`.

---

### T095 — Separate optional personal-owner filtering and alias resolution · 69/100

- Use separate all-owner and exact-owner title-count queries so a real user ID can use its index; preserve title NOCASE and null/empty-owner behavior.
- Resolve all matching local/remote IDs before bounded positive-ID delete/update operations. Preserve the current match-all behavior when the two keys identify different rows and execute multi-step writes transactionally.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`, `app/src/main/java/org/ole/planet/myplanet/model/Personal.kt`.

---

### T096 — Index voice-feed leaders without changing first-match semantics · 69/100

Build an adapter-lifetime index from leader name to the first matching leader, including nullable names where the current equality permits them. Retain the userModel-null guard.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt`.

---

### T097 — Assemble ranked chat-search results in one destination · 69/100

Append title-prefix, title-contains, conversation-prefix, and conversation-contains buckets into one pre-sized result list; do the same for title-only results. Preserve ranking and stable order.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`.

---

### T098 — Use the user-read contract for Settings guest detection · 69/100

Replace SettingsViewModel UserSessionManager injection with UserReadRepository and preserve its exact guest-ID predicate. Update constructor tests and retain the existing singleton Hilt binding.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/services/UserSessionManager.kt`.

---

### T099 — Remove the RecyclerView dependency from stable ID generation · 69/100

Replace the RecyclerView.NO_ID reference with a local -1L constant and remove the import. Preserve FNV hashing, overflow behavior, synchronized fallback allocation, and all caller-visible IDs.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/StableIdGenerator.kt`.

---

### T100 — Reuse constant voice-label membership data · 68/100

Cache the constant label-value set, pass already-resolved labels into visibility calculation, and short-circuit too-short lists before building a used-label set. Preserve permission checks and duplicate-label semantics.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt`.

---

### T101 — Inject BroadcastService into the notification action receiver · 68/100

Field-inject BroadcastService through the existing AndroidEntryPoint and replace getBroadcastService(context). Preserve system/local broadcast actions, extras, threading, and receiver completion.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt`, `app/src/main/java/org/ole/planet/myplanet/services/BroadcastService.kt`.

---

### T102 — Move the saved-login profile lookup into LoginViewModel · 68/100

Have LoginViewModel resolve the current profile/name needed by saveUsers rather than LoginActivity reading profileDbHandler on its behalf. Keep password encryption and Activity-owned UI behavior at their existing boundary.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt`.

---

### T103 — Stream CSV speech formatting into one builder · 68/100

Build the same speech string directly without drop/map/join intermediate lists. Preserve row numbering, ragged-column fallback headers, punctuation, empty rows, and header-only output.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt`.

---

### T104 — Ask the resource contract for next-step resources needing download · 67/100

Expose the next-step not-offline selection on ResourcesRepository, using a default implementation if the existing implementation must remain untouched. Keep the exact resourceOffline/revision rule and leave URL handoff in CoursesStepsViewModel.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/model/MyLibrary.kt`.

---

### T105 — Use tagged diagnostics for attachment upload failures · 67/100

Replace FileUploader.uploadDoc stack dumps with a tagged diagnostic containing safe operation context, retaining listener/result behavior and cancellation handling.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/FileUploader.kt`.

---

### T106 — Expose community identifiers and title as derived state · 65/100

Add distinct planet/community view-in IDs and the existing planet-title fallback to CommunityTabState. Have both hosts consume the appropriate ID while preserving their different titles and filtering identities.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/community/HomeCommunityDialogFragment.kt`.

---

### T107 — Extract export exam IDs without splitting unused suffixes · 65/100

Use nullable substringBefore for getExamId, preserving null, empty, plain-ID, leading-delimiter, and multiple-delimiter results.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`.

---

### T108 — Replace redundant TextUtils checks in health item mapping · 65/100

Use Kotlin isNotEmpty and equality for the already non-null createdBy string. Preserve examiner resolution and self-examination behavior; remove only the direct TextUtils dependency.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationItemMapper.kt`.

---

### T109 — Reuse the resource prefix-result buffer · 65/100

Append contains results to the private prefix-result list and return it rather than allocating a third list. Preserve empty-query list identity and prefix priority.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesSearchUtils.kt`.

---

### T110 — Reuse English course-filter resources within one configuration · 64/100

Cache English configuration resources and grade/subject arrays for the current fragment configuration instead of recreating them on each spinner read. Preserve canonical persisted values and invalidate on configuration/view changes.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFilterFragment.kt`.

---

### T111 — Report PDF export failures without changing the fallback · 64/100

Replace the two silent outer export catches with safe tagged diagnostics and retain their null result, document closure, and output-file cleanup behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`.

---

### T112 — Share task-to-team-name batch mapping · 64/100

Extract the common team-ID collection and name-map construction from the ID- and title-keyed notification helpers. Preserve empty-input returns, ordering, and filtering.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`.

---

### T113 — Use controlled diagnostics for malformed model JSON · 63/100

Replace Feedback and HealthExamination serializer stack dumps with controlled reporting while retaining their malformed-message/condition fallbacks. Keep Android reporting outside portable model logic when introducing a reporting seam.

Scope: `app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt`, `app/src/main/java/org/ole/planet/myplanet/model/HealthExamination.kt`.

---

### T114 — Use a larger bounded checksum read buffer · 62/100

Increase the SHA-512 file-reading buffer from DEFAULT_BUFFER_SIZE to a modest larger constant such as 64 KiB. Retain the algorithm, digest representation, and missing-file behavior; compare representative file throughput before claiming a speedup.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt`.

---

### T115 — Move platform binding modules into the DI package · 60/100

Move DeviceNameModule and UserDataUploadSchedulerModule bindings into an appropriately scoped di module, retaining qualifiers, scopes, implementations, and graph uniqueness; remove obsolete module annotations/imports from the implementation files.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/DeviceNameProvider.kt`, `app/src/main/java/org/ole/planet/myplanet/services/UserDataUploadScheduler.kt`.

---

### T116 — Remove duplicate float and double parse fallbacks · 60/100

Use the typed nullable float/double conversion once and retain zero fallbacks, special values, and numeric-string behavior. Leave integer overflow semantics unchanged.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt`.

---

### T117 — Remove redundant Android logging from typed personal-upload results · 59/100

Remove direct Android logging where PersonalUploadResult already conveys the failure and cause to its caller. Preserve AttachmentFailed/DocumentFailed outcomes and existing fallback details.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`.

---

### T118 — Choose a deterministic representative opened-resource row · 59/100

Replace bare-column GROUP BY selection with a deterministic existing primary-key representative per resource, preserving the public Flow signature and resource-ID set. Do not change the separate most-opened aggregate or infer that the present consumer displays arbitrary representative titles.

Scope: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt`.

---

### T119 — Remove RetryRepositoryImpl direct Android logging dependency · 57/100

Use the existing persisted markFailed result as the reporting boundary and remove duplicate Android logging in executeOperation. Preserve error causes/messages and retry bookkeeping.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`.

---

### T120 — Memoize voice label display formatting with locale-aware bounds · 56/100

Cache fallback formatLabelValue results by raw label and locale, with an instance-lifetime bound; retain constant-name lookups and existing whitespace/case behavior.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt`.

---

### T121 — Separate News model fallbacks from Android logging · 54/100

Remove direct Android logging from News parsing and sort fallbacks, preserving false/time/empty-field behavior and arranging malformed-data diagnostics at the parsing boundary where needed. Do not claim that removing an import alone makes the full dependency graph portable.

Scope: `app/src/main/java/org/ole/planet/myplanet/model/News.kt`.

---

### T122 — Resolve fixed feedback labels once per display configuration · 54/100

Pre-resolve the fixed LABELS strings for FeedbackAdapter, preserving Locale.ROOT key normalization and raw-value fallback; refresh when the display configuration changes.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt`.

---

### T123 — Reuse constant exam-choice data and view-scoped inflation helpers · 53/100

Construct the constant Other choice once and reuse it read-only or copy before mutation; cache an inflater only for the current fragment view/context lifetime.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt`.

---

### T124 — Cache hostUrl with explicit invalidation · 52/100

Cache the derived host URL under the existing generation discipline and clear it from every cache-reset path. Preserve URL switching, fallback parsing, suffix rules, and concurrent invalidation. Verify all preference setters invalidate before eliminating preference reads.

Scope: `app/src/main/java/org/ole/planet/myplanet/utils/UrlUtils.kt`.

---

### T125 — Cache submission-row date formatting within the display configuration · 51/100

Cache repeated timestamp formatting in both submission adapters, bounding the cache and invalidating it when locale/time zone or the adapter data lifetime changes.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsListAdapter.kt`.

---

### T126 — Remove redundant date handling and quiet malformed meetup-day stack dumps · 51/100

Remove the redundant date-format catch only after retaining the formatter fallback. Keep the JSONArray exception boundary for malformed day data, replacing its printStackTrace with controlled diagnostics or the established fallback.

Scope: `app/src/main/java/org/ole/planet/myplanet/model/Meetup.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt`.

---

### T127 — Hoist repeated fragment resource strings within each render · 51/100

- Resolve the untitled-course fallback before mapping picker course names.
- Resolve the empty health label once per render/function and reuse it for the repeated fields, preserving locale changes between renders.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt`.

---

### T128 — Remove CommunityRepositoryImpl direct Android logging dependency · 50/100

Remove the direct android.util.Log use from community sync while retaining cancellation and the false failure result. Ensure existing caller reporting is sufficient before deleting the only operation diagnostic; keep any required reporter platform-independent.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/CommunityRepositoryImpl.kt`.

---

### T129 — Remove HealthRepositoryImpl direct Android logging dependency safely · 49/100

Separate platform logging from health parsing/upload/encryption fallbacks. Keep useful diagnostics at an existing caller or injected platform-neutral reporter rather than silently deleting the sole indication of a failed health operation.

Scope: `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt`.

---

### T130 — Encapsulate the plain-community TeamSummary fallback · 48/100

Provide a named TeamSummary factory for the exact plain-community fallback and use it from ChatViewModel, retaining all current nullable/default fields.

Scope: `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/model/TeamSummary.kt`.

---

### T131 — Name the network reachability delay accurately · 44/100

Rename UPLOAD_DELAY_SECONDS to REACHABILITY_DELAY_SECONDS and update its only use without changing the delay or WorkManager replacement policy.

Scope: `app/src/main/java/org/ole/planet/myplanet/services/NetworkMonitorWorker.kt`.
