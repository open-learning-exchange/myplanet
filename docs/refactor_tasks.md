# myPlanet refactor backlog — merged and verified

Merged from 12 submitted lists (120 task headings, 110 distinct tasks: list 11 is a byte-identical copy of list 5) down to **98 shipped tasks**, sorted by rating.

Every task premise was re-checked against the working tree with grep and file reads. Ratings use evidence quality x impact x risk-adjusted feasibility (weights 0.35 / 0.35 / 0.30, geometric mean, 1-100).

---

## notif-team-task-badge — getTeamNotifications: compute the task badge per team (hasTask is currently global, so badges are wrong)

rating: 84  (evidence 1.00 x impact 0.70 x feasibility 0.85)
proposed by: devin swe 2 (L12#2)
duplicate credit: unique 1/1

---

## synctimelogger-dead-race — SyncTimeLogger: delete the write-only detailedLogs accumulator; make per-key appends thread-safe

rating: 82  (evidence 1.00 x impact 0.65 x feasibility 0.85)
proposed by: claude opus 5 (L2#1), devin swe 2 (L6#8)
duplicate credit: 1/2 per proposal

---

## voices-already-shared-exists — isAlreadyShared: answer with SQL EXISTS instead of hydrating every matching news row

rating: 77  (evidence 0.95 x impact 0.60 x feasibility 0.80)
proposed by: claude opus 5 (L8#9)
duplicate credit: unique 1/1

---

## chatsearch-ignorecase — ChatSearch: drop redundant ignoreCase on pre-normalized text and assemble buckets in one pass

rating: 76  (evidence 0.90 x impact 0.55 x feasibility 0.90)
proposed by: claude opus 5 (L2#3), codex sol 5.6 (L3#5), copilot grok 4.5 (L5#5)
duplicate credit: 1/3 per proposal

---

## inline-adapter-fs-cache — InlineResourceAdapter: resolve the File once and use FileExistenceCache instead of per-bind re-stats

rating: 74  (evidence 0.90 x impact 0.60 x feasibility 0.75)
proposed by: devin swe 2 (L6#6), copilot grok 4.5 (L5#1)
duplicate credit: 1/2 per proposal

---

## feedback-stale-cache — Feedback.messageList: memoize the derived list and invalidate parsedMessages in setMessages

rating: 73  (evidence 0.90 x impact 0.55 x feasibility 0.80)
proposed by: devin swe 2 (L6#9)
duplicate credit: unique 1/1

---

## activities-dead-overloads — ActivitiesRepository: delete the four unused resource-open overloads

rating: 71  (evidence 1.00 x impact 0.40 x feasibility 0.95)
proposed by: claude opus 5 (L8#4)
duplicate credit: unique 1/1

---

## checkversion-spm-param — ConfigurationsRepository.checkVersion: drop the SharedPrefManager parameter

rating: 71  (evidence 0.95 x impact 0.45 x feasibility 0.85)
proposed by: claude opus 5 (L8#1)
duplicate credit: unique 1/1

---

## notifrepository-dead-time — NotificationsRepository: remove dead getNotifications; route Date() through the injected TimeProvider

rating: 71  (evidence 0.95 x impact 0.50 x feasibility 0.75)
proposed by: claude opus 5 (L8#8)
duplicate credit: unique 1/1

---

## okhttp-pool — NetworkModule: share one OkHttp ConnectionPool between the standard and reachability clients

rating: 71  (evidence 0.90 x impact 0.50 x feasibility 0.80)
proposed by: claude opus 5 (L2#6)
duplicate credit: unique 1/1

---

## webview-dir-cache — WebViewActivity: resolve the local resource directory once instead of per navigation

rating: 71  (evidence 0.90 x impact 0.50 x feasibility 0.80)
proposed by: devin swe 2 (L6#5)
duplicate credit: unique 1/1

---

## health-upload-repo — HealthRepository: absorb the pending, upload, mark sequence out of UploadToShelfService

rating: 70  (evidence 0.90 x impact 0.55 x feasibility 0.70)
proposed by: claude opus 5 (L8#6)
duplicate credit: unique 1/1

---

## life-adapter-bind — LifeAdapter: move contentDescription strings from touch and click handlers to bind time

rating: 69  (evidence 0.90 x impact 0.45 x feasibility 0.85)
proposed by: devin swe 2 (L6#7)
duplicate credit: unique 1/1

---

## progress-typed-projection — ProgressRepository: return a typed CoursesProgressRow instead of JsonArray parsed in the ViewModel

rating: 69  (evidence 0.85 x impact 0.60 x feasibility 0.65)
proposed by: claude opus 5 (L8#7)
duplicate credit: unique 1/1

---

## challenge-prompter — ChallengePrompter: stop reaching into DashboardViewModel for the progress math

rating: 68  (evidence 0.90 x impact 0.50 x feasibility 0.70)
proposed by: codex sol 5.6 (L9#1)
duplicate credit: unique 1/1

---

## courses-steps-prefetch — CoursesStepsViewModel: move next-step prefetch and path lookup behind ResourcesRepository

rating: 68  (evidence 0.85 x impact 0.60 x feasibility 0.60)
proposed by: codex sol 5.6 (L9#2)
duplicate credit: unique 1/1

---

## userdataworker-helper — UserDataWorker: collapse six copy-pasted upload-await blocks into one helper

rating: 68  (evidence 0.90 x impact 0.45 x feasibility 0.80)
proposed by: devin swe 2 (L12#9)
duplicate credit: unique 1/1

---

## viewer-user-vm — Viewer activities and ResourcesExitCoordinator: resolve the current user inside ResourceViewerViewModel

rating: 68  (evidence 0.95 x impact 0.45 x feasibility 0.75)
proposed by: claude opus 5 (L8#2), devin swe 2 (L12#8)
duplicate credit: 1/2 per proposal

---

## calendar-viewmodel-distinct — CalendarViewModel.loadMeetups: distinctUntilChanged before re-querying meetups

rating: 67  (evidence 0.85 x impact 0.45 x feasibility 0.80)
proposed by: copilot kimi k3 (L4#4)
duplicate credit: unique 1/1

---

## downloadservice-prefs — DownloadService: stop SharedPreferences thrash in queue bookkeeping

rating: 67  (evidence 0.90 x impact 0.45 x feasibility 0.75)
proposed by: copilot grok 4.5 (L5#3)
duplicate credit: unique 1/1

---

## my-library-dedupe — MyLibrary.insertMyLibrary: hoist the SharedPreferences read and stop duplicating attachments

rating: 67  (evidence 0.80 x impact 0.55 x feasibility 0.70)
proposed by: devin swe 2 (L6#1)
duplicate credit: unique 1/1

---

## storage-scan — Storage breakdown disk scan: single-pass walk, moved out of the ViewModel into ResourcesRepository

rating: 67  (evidence 0.85 x impact 0.50 x feasibility 0.70)
proposed by: copilot kimi k3 (L4#9), claude opus 5 (L8#3)
duplicate credit: 1/2 per proposal

---

## chat-history-flow — ChatViewModel: observe chat history through ChatRepository instead of RealtimeSyncManager

rating: 66  (evidence 0.80 x impact 0.60 x feasibility 0.60)
proposed by: codex sol 5.6 (L9#3)
duplicate credit: unique 1/1

---

## process-user-data-vm — ProcessUserDataActivity: introduce a ViewModel for the member-upload callback chain

rating: 66  (evidence 0.85 x impact 0.60 x feasibility 0.55)
proposed by: copilot kimi k3 (L10#6)
duplicate credit: unique 1/1

---

## ratings-wrapper — RatingsRepository: delete the getRatingsById null-guard wrapper

rating: 66  (evidence 0.95 x impact 0.35 x feasibility 0.90)
proposed by: claude opus 5 (L8#10)
duplicate credit: unique 1/1

---

## surveys-raw-counts — SurveysRepository: return raw counts and dates, format in the adapter

rating: 66  (evidence 0.90 x impact 0.50 x feasibility 0.65)
proposed by: copilot kimi k3 (L10#2)
duplicate credit: unique 1/1

---

## team-vm-sync-events — TeamViewModel: remove sync-table events and collect repository flows only

rating: 66  (evidence 0.85 x impact 0.55 x feasibility 0.60)
proposed by: codex sol 5.6 (L9#4)
duplicate credit: unique 1/1

---

## userachievements-interface — Carve the achievement surface out of UserRepository

rating: 66  (evidence 0.90 x impact 0.50 x feasibility 0.65)
proposed by: devin swe 2 (L12#7)
duplicate credit: unique 1/1

---

## achievement-picker-projection — Achievement resource picker: load only id and title for the my_library dialog

rating: 65  (evidence 0.90 x impact 0.50 x feasibility 0.60)
proposed by: devin swe 2 (L12#1)
duplicate credit: unique 1/1

---

## bell-dashboard-reachability — BellDashboardViewModel: inject ServerReachabilityProvider instead of the static call

rating: 65  (evidence 0.90 x impact 0.40 x feasibility 0.80)
proposed by: jules gemini 3.1 pro (L7#2)
duplicate credit: unique 1/1

---

## chat-repo-reachability — ChatRepositoryImpl: inject ServerReachabilityProvider instead of the static call

rating: 65  (evidence 0.90 x impact 0.40 x feasibility 0.80)
proposed by: jules gemini 3.1 pro (L7#3)
duplicate credit: unique 1/1

---

## covers-existence-cache — CoursesItemUtils.bindCover: reuse FileExistenceCache for the main-thread exists() stat

rating: 65  (evidence 0.90 x impact 0.45 x feasibility 0.70)
proposed by: claude opus 5 (L2#5)
duplicate credit: unique 1/1

---

## feedback-flow-single-source — FeedbackListFragment: make the repository flow the only list refresh source

rating: 65  (evidence 0.85 x impact 0.50 x feasibility 0.65)
proposed by: codex sol 5.6 (L9#5)
duplicate credit: unique 1/1

---

## feedback-repo-tighten — FeedbackRepository: drop the internal-only builders and stop passing UserEntity into the list query

rating: 65  (evidence 0.90 x impact 0.45 x feasibility 0.70)
proposed by: claude opus 5 (L8#5)
duplicate credit: unique 1/1

---

## fileutils-caching — FileUtils: memoize findHtmlCoverImage, make getFileExtension string-only, cache a non-empty checkFileExist

rating: 65  (evidence 0.90 x impact 0.50 x feasibility 0.60)
proposed by: claude opus 5 (L2#4), copilot grok 4.5 (L5#2)
duplicate credit: 1/2 per proposal

---

## healthexam-current-user — HealthExaminationState: move the examiner lookup into state

rating: 65  (evidence 0.90 x impact 0.45 x feasibility 0.70)
proposed by: devin swe 2 (L12#4)
duplicate credit: unique 1/1

---

## newids-refilter — SyncManager resource cleanup: drop the re-filter and the always-true size guard

rating: 65  (evidence 0.95 x impact 0.35 x feasibility 0.85)
proposed by: claude opus 5 (L2#2)
duplicate credit: unique 1/1

---

## public-survey-builder — Extract PublicSurveyPayloadBuilder out of PublicSurveyActivity

rating: 65  (evidence 0.85 x impact 0.50 x feasibility 0.65)
proposed by: copilot kimi k3 (L10#5)
duplicate credit: unique 1/1

---

## reply-viewmodel — ReplyViewModel: absorb the last repository and session calls out of ReplyActivity

rating: 65  (evidence 0.90 x impact 0.50 x feasibility 0.60)
proposed by: copilot kimi k3 (L10#1)
duplicate credit: unique 1/1

---

## requests-current-user — RequestsUiState: carry the current user instead of a second session read in the fragment

rating: 65  (evidence 0.90 x impact 0.45 x feasibility 0.70)
proposed by: devin swe 2 (L12#3)
duplicate credit: unique 1/1

---

## achievement-batch-ack — AchievementUploader: batch successful upload acknowledgements instead of one Room update each

rating: 64  (evidence 0.85 x impact 0.45 x feasibility 0.70)
proposed by: codex sol 5.6 (L9#9)
duplicate credit: unique 1/1

---

## addresource-current-user — AddResourceViewModel: resolve the current user inside the ViewModel

rating: 64  (evidence 0.90 x impact 0.40 x feasibility 0.75)
proposed by: devin swe 2 (L12#5)
duplicate credit: unique 1/1

---

## collections-switch-state — CollectionsFragment: take isCollectionSwitchOn out of MainApplication

rating: 64  (evidence 0.90 x impact 0.40 x feasibility 0.75)
proposed by: jules gemini 3.1 pro (L7#4)
duplicate credit: unique 1/1

---

## courses-adapter-removal — CoursesAdapter.removeCourses: constant-time membership instead of an O(n*m) filter

rating: 64  (evidence 0.90 x impact 0.35 x feasibility 0.85)
proposed by: codex sol 5.6 (L3#1)
duplicate credit: unique 1/1

---

## healthexam-adapter-regex — HealthExaminationAdapter: drop the regex split and the no-op trimIndent on a single-line resource

rating: 64  (evidence 0.90 x impact 0.40 x feasibility 0.75)
proposed by: claude opus 5 (L2#10)
duplicate credit: unique 1/1

---

## lifecache-memory — LifeCache: add an in-memory layer in front of SharedPreferences and Gson

rating: 64  (evidence 0.85 x impact 0.45 x feasibility 0.70)
proposed by: copilot grok 4.5 (L5#6)
duplicate credit: unique 1/1

---

## activities-log-context — ActivitiesRepositoryImpl: drop android.util.Log and the dead Context parameter

rating: 63  (evidence 0.90 x impact 0.40 x feasibility 0.70)
proposed by: copilot kimi k3 (L10#3)
duplicate credit: unique 1/1

---

## retry-repo-boundary — RetryQueueWorker: move HTTP execution and response classification behind RetryRepository

rating: 63  (evidence 0.80 x impact 0.55 x feasibility 0.55)
proposed by: codex sol 5.6 (L9#8)
duplicate credit: unique 1/1

---

## answer-memoize — Answer.valueChoicesArray: memoize the re-parsing getter

rating: 62  (evidence 0.85 x impact 0.40 x feasibility 0.70)
proposed by: devin swe 2 (L6#4)
duplicate credit: unique 1/1

---

## health-refresh-vm — MyHealthFragment: route health refreshes through HealthViewModel refresh intents

rating: 62  (evidence 0.80 x impact 0.50 x feasibility 0.60)
proposed by: codex sol 5.6 (L9#7)
duplicate credit: unique 1/1

---

## notification-receiver-scope — NotificationActionReceiver: own its coroutine scope instead of MainApplication.applicationScope

rating: 62  (evidence 0.85 x impact 0.40 x feasibility 0.70)
proposed by: jules gemini 3.1 pro (L7#10)
duplicate credit: unique 1/1

---

## preview-loader-memo — ResourcesPreviewLoader: memoize audio, csv and text preview work

rating: 62  (evidence 0.85 x impact 0.40 x feasibility 0.70)
proposed by: copilot grok 4.5 (L5#8)
duplicate credit: unique 1/1

---

## coursedao-parameterized — CourseDao: replace the @RawQuery SimpleSQLiteQuery with a parameterized query

rating: 61  (evidence 0.80 x impact 0.50 x feasibility 0.55)
proposed by: copilot kimi k3 (L10#7)
duplicate credit: unique 1/1

---

## dashboard-element-vm — DashboardElementActivity: introduce DashboardElementViewModel

rating: 61  (evidence 0.85 x impact 0.45 x feasibility 0.60)
proposed by: devin swe 2 (L12#10)
duplicate credit: unique 1/1

---

## date-cache-healthusers — HealthUsersAdapter.bindDate: cache per-row formatted join dates

rating: 61  (evidence 0.85 x impact 0.35 x feasibility 0.80)
proposed by: copilot kimi k3 (L4#3)
duplicate credit: unique 1/1

---

## date-cache-personals — PersonalsAdapter: cache per-row formatted date strings

rating: 61  (evidence 0.85 x impact 0.35 x feasibility 0.80)
proposed by: copilot kimi k3 (L4#1)
duplicate credit: unique 1/1

---

## date-cache-userarray — UserArrayAdapter: cache per-row formatted join dates

rating: 61  (evidence 0.85 x impact 0.35 x feasibility 0.80)
proposed by: copilot kimi k3 (L4#2)
duplicate credit: unique 1/1

---

## dialogutils-checksum — DialogUtils: take a checkCheckSum lambda instead of ConfigurationsRepository

rating: 61  (evidence 0.90 x impact 0.35 x feasibility 0.75)
proposed by: devin swe 2 (L12#6)
duplicate credit: unique 1/1

---

## resources-search-single-pass — ResourcesRepositoryImpl.search: single-pass startsWith ranking instead of two lists plus concat

rating: 61  (evidence 0.85 x impact 0.35 x feasibility 0.80)
proposed by: copilot grok 4.5 (L5#4)
duplicate credit: unique 1/1

---

## surveys-reload-state — SurveyFragment and SurveysViewModel: centralize scope and reload state in the ViewModel

rating: 61  (evidence 0.80 x impact 0.50 x feasibility 0.55)
proposed by: codex sol 5.6 (L9#6)
duplicate credit: unique 1/1

---

## exam-multiselect-early — ExamAnswerUtils: reject mismatched multi-select answers before sorting both collections

rating: 60  (evidence 0.90 x impact 0.30 x feasibility 0.85)
proposed by: codex sol 5.6 (L3#9)
duplicate credit: unique 1/1

---

## examquestion-hoist — ExamQuestion.insertCorrectChoice: hoist the loop-invariant JSON read and short-circuit

rating: 60  (evidence 0.90 x impact 0.30 x feasibility 0.85)
proposed by: devin swe 2 (L6#2)
duplicate credit: unique 1/1

---

## maintapp-listener — Decouple TeamPagerAdapter and TeamDetailFragment from MainApplication.listener

rating: 60  (evidence 0.85 x impact 0.50 x feasibility 0.50)
proposed by: jules gemini 3.1 pro (L7#6)
duplicate credit: unique 1/1

---

## course-pending-buffer — CoursesRepositoryImpl: take the PendingCourseResource buffer out of the singleton

rating: 59  (evidence 0.80 x impact 0.50 x feasibility 0.50)
proposed by: copilot kimi k3 (L10#9)
duplicate credit: unique 1/1

---

## healthexam-activity-set — HealthExaminationActivity: Set lookup for diagnoses plus hoisted per-checkbox styling

rating: 59  (evidence 0.90 x impact 0.30 x feasibility 0.80)
proposed by: devin swe 2 (L6#3)
duplicate credit: unique 1/1

---

## logging-news — News: replace five printStackTrace() calls with tagged Log.w

rating: 59  (evidence 0.90 x impact 0.30 x feasibility 0.80)
proposed by: copilot kimi k3 (L4#8)
duplicate credit: unique 1/1

---

## logging-timeutils — TimeUtils: replace seven printStackTrace() calls with tagged Log.w

rating: 59  (evidence 0.90 x impact 0.30 x feasibility 0.80)
proposed by: copilot kimi k3 (L4#7)
duplicate credit: unique 1/1

---

## notifications-enriched-passes — getEnrichedNotifications: fewer passes over payloads and one less DAO round-trip

rating: 59  (evidence 0.80 x impact 0.40 x feasibility 0.65)
proposed by: copilot grok 4.5 (L5#7)
duplicate credit: unique 1/1

---

## submissions-context — SubmissionsRepositoryImpl and exporter: stop threading Context into the PDF exporter

rating: 59  (evidence 0.85 x impact 0.40 x feasibility 0.60)
proposed by: copilot kimi k3 (L10#8)
duplicate credit: unique 1/1

---

## synctimelogger-summary — SyncTimeLogger.generateSummary: aggregate each endpoint and model once instead of re-summing

rating: 59  (evidence 0.90 x impact 0.30 x feasibility 0.80)
proposed by: codex sol 5.6 (L3#6)
duplicate credit: unique 1/1

---

## chat-share-typeface — ChatShareTargetAdapter: stop allocating a Typeface on every group-header bind

rating: 58  (evidence 0.80 x impact 0.30 x feasibility 0.85)
proposed by: devin swe 2 (L6#10)
duplicate credit: unique 1/1

---

## downloadservice-nexturl — DownloadService.getNextUrl: single-pass minimum instead of an intermediate list

rating: 58  (evidence 0.80 x impact 0.30 x feasibility 0.85)
proposed by: codex sol 5.6 (L3#2)
duplicate credit: unique 1/1

---

## resource-filter-signature — ResourcesListFilter.toSignature: set-based tag signature without sorting

rating: 58  (evidence 0.85 x impact 0.30 x feasibility 0.80)
proposed by: codex sol 5.6 (L3#4)
duplicate credit: unique 1/1

---

## storage-category-counters — StorageCategoryUiState: compute checkedCount and allChecked in one stored pass

rating: 58  (evidence 0.85 x impact 0.30 x feasibility 0.80)
proposed by: claude opus 5 (L2#8)
duplicate credit: unique 1/1

---

## adapter-string-hoist — Hoist constant string resource lookups out of three adapter bind paths

rating: 57  (evidence 0.85 x impact 0.30 x feasibility 0.75)
proposed by: claude opus 5 (L2#7)
duplicate credit: unique 1/1

---

## courses-viewmodel-scope — CoursesViewModel.loadCourses: drop the pointless coroutineScope/async/await wrapper

rating: 57  (evidence 0.90 x impact 0.25 x feasibility 0.85)
proposed by: codex sol 5.6 (L3#3)
duplicate credit: unique 1/1

---

## notifications-distinct — NotificationsViewModel: drop the redundant distinct pass over ordered types

rating: 57  (evidence 0.90 x impact 0.25 x feasibility 0.85)
proposed by: codex sol 5.6 (L3#8)
duplicate credit: unique 1/1

---

## pending-submission-limit1 — SubmissionsRepositoryImpl: fetch one pending submission with LIMIT 1 instead of materializing a list

rating: 56  (evidence 0.75 x impact 0.40 x feasibility 0.60)
proposed by: codex sol 5.6 (L9#10)
duplicate credit: unique 1/1

---

## sync-repo-shelf-seam — SyncRepositoryImpl: push shelf-dispatch map construction behind an interface seam

rating: 56  (evidence 0.75 x impact 0.45 x feasibility 0.50)
proposed by: copilot kimi k3 (L10#4)
duplicate credit: unique 1/1

---

## tts-stripmarkdown — TTSManager.stripMarkdown: fewer passes and intermediate strings

rating: 56  (evidence 0.85 x impact 0.35 x feasibility 0.60)
proposed by: copilot grok 4.5 (L5#9)
duplicate credit: unique 1/1

---

## userrepository-static-context — UserRepositoryImpl: use the injected application context, not MainApplication.context

rating: 56  (evidence 0.80 x impact 0.30 x feasibility 0.75)
proposed by: jules gemini 3.1 pro (L7#9)
duplicate credit: unique 1/1

---

## myteam-serialize-iterator — MyTeam.serialize: strip null fields in one iterator pass instead of filter-then-remove

rating: 55  (evidence 0.90 x impact 0.25 x feasibility 0.80)
proposed by: codex sol 5.6 (L3#10)
duplicate credit: unique 1/1

---

## progress-viewmodel-fields — ProgressViewModel: read each progress JSON field once

rating: 55  (evidence 0.90 x impact 0.25 x feasibility 0.80)
proposed by: claude opus 5 (L2#9)
duplicate credit: unique 1/1

---

## feedback-adapter-date — FeedbackAdapter.onBindViewHolder: cache the formatted open date

rating: 54  (evidence 0.70 x impact 0.30 x feasibility 0.80)
proposed by: copilot kimi k3 (L4#5)
duplicate credit: unique 1/1

---

## logging-voiceslabel — VoicesLabelManager: replace printStackTrace() with tagged Log.w

rating: 54  (evidence 0.85 x impact 0.25 x feasibility 0.80)
proposed by: copilot kimi k3 (L4#10)
duplicate credit: unique 1/1

---

## storage-categories-indexof — StorageCategories.indexOf: check the raw key before lowercasing

rating: 52  (evidence 0.85 x impact 0.20 x feasibility 0.90)
proposed by: jules gemini 3.1 pro (L1#1)
duplicate credit: unique 1/1

---

## teams-mapnotnullto — TeamsRepositoryImpl: use mapNotNullTo for the joined-member name sets

rating: 51  (evidence 0.85 x impact 0.20 x feasibility 0.85)
proposed by: jules gemini 3.1 pro (L1#3)
duplicate credit: unique 1/1

---

## progress-passed-steps — ProgressRepositoryImpl: stop building a HashSet just to count passed steps

rating: 48  (evidence 0.70 x impact 0.20 x feasibility 0.85)
proposed by: jules gemini 3.1 pro (L1#6)
duplicate credit: unique 1/1

---

## uploadconfigs-lazy — UploadConfigs: resolve the lazy teams repository once per result batch

rating: 48  (evidence 0.70 x impact 0.20 x feasibility 0.85)
proposed by: codex sol 5.6 (L3#7)
duplicate credit: unique 1/1

---

## courses-areallselected — CoursesAdapter.areAllSelected: short-circuit the count when isMyCourseLib

rating: 47  (evidence 0.85 x impact 0.15 x feasibility 0.90)
proposed by: jules gemini 3.1 pro (L1#5)
duplicate credit: unique 1/1

---

## crashlogstore-sequence — CrashLogStore: replace the sequence pipeline in the pending-file cap check

rating: 47  (evidence 0.85 x impact 0.15 x feasibility 0.90)
proposed by: jules gemini 3.1 pro (L1#4)
duplicate credit: unique 1/1

---

## resources-fragment-size — ResourcesFragment.applyFiltersAndUpdateUI: cache the repeated filteredList.size

rating: 47  (evidence 0.85 x impact 0.15 x feasibility 0.90)
proposed by: jules gemini 3.1 pro (L1#2)
duplicate credit: unique 1/1

---

## teamcalendar-mapto — TeamCalendarFragment: use map instead of mapTo(mutableListOf())

rating: 47  (evidence 0.85 x impact 0.15 x feasibility 0.90)
proposed by: jules gemini 3.1 pro (L1#8)
duplicate credit: unique 1/1

---

## dashboard-courses-size — BaseDashboardFragment.renderMyCourses: cache filteredCourses.size and reuse it for isEmpty

rating: 46  (evidence 0.80 x impact 0.15 x feasibility 0.90)
proposed by: jules gemini 3.1 pro (L1#9)
duplicate credit: unique 1/1

---

## downloadutils-mapto — DownloadUtils.downloadAllFiles: pre-size or simplify the ArrayList mapping

rating: 43  (evidence 0.70 x impact 0.15 x feasibility 0.85)
proposed by: jules gemini 3.1 pro (L1#7)
duplicate credit: unique 1/1

---

## teams-split-consumers — Give the TeamsFinances, TeamsMembers and TeamsNotifications interfaces real consumers

rating: 43  (evidence 0.40 x impact 0.40 x feasibility 0.50)
proposed by: copilot kimi k3 (L10#10)
duplicate credit: unique 1/1

---

## chathistory-adapter — ChatHistoryAdapter: DiffUtil payloads and bind micro-costs

rating: 38  (evidence 0.40 x impact 0.25 x feasibility 0.60)
proposed by: copilot grok 4.5 (L5#10)
duplicate credit: unique 1/1

---

## membersfragment-size — MembersFragment: extract the repeated state.members.size

rating: 36  (evidence 0.60 x impact 0.10 x feasibility 0.90)
proposed by: jules gemini 3.1 pro (L1#10)
duplicate credit: unique 1/1

