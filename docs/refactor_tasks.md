# myPlanet refactor mega plan

**Base:** master `14da1ba` · **assembled:** 2026-09-24 · **scope:** 76 tasks (60 carried from the Claude plan, 16 new)

This plan merges three backlogs built from the same 120 raw tasks (6 agents × 2 prompts × 10): [Claude Opus 5.5](https://github.com/open-learning-exchange/myplanet/blob/claude/zen-davinci-dp0yjn/docs/refactor_tasks.md), [Codex Sol 5.6](https://github.com/open-learning-exchange/myplanet/blob/codex/extract-and-merge-task-lists-from-agents/refactor_tasks.md) and [Devin SWE 2](https://github.com/open-learning-exchange/myplanet/blob/devin/1790259865-refactor-task-merge/docs/refactor_tasks.md). A fresh verification pass against the working tree added findings of its own.

## What's different from the three source plans

- **Base: the Claude plan's 60 specs, unchanged.** It was the only one of the three with steps, acceptance commands, size budgets and sequencing, and its drop decisions survived re-verification (see [Adjudication](#adjudication-of-contested-drops)). Each carried task gets a **mega-plan notes** line with lock changes, corrections and new sequencing.
- **16 new tasks (61–76), 5 of them in the top wave.** A sweep for SQLite's 999-variable limit (the bug behind task 1) found live instances in `TagDao` (the Resources list goes empty), `MyLibraryDao.deleteStalePublicNotIn` (deleted resources are never purged), and the Feedback, Meetup and News sync lookups. Checking the dropped tasks turned up four more bugs: the password is interpreted as a regex replacement string in `UserRepositoryImpl.replacedUrl`; `ServerAddressAdapter` reads a stale position and can raise the clear-data dialog wrongly; `HealthUsersAdapter`'s diff ignores name edits; and `VoicesLabelManager`'s close-chip removes the wrong label. Two of the Claude plan's drops are brought back in corrected form (tasks 70 and 72).
- **Open-PR rule: only PRs updated in the last 7 days count.** That cutoff is 2026-09-17. As of 2026-09-24, only 17187, 17254, 17435 and 15226 qualify, and 15226 touches nothing under `app/`. The other 24 open PRs are ignored. Where the Claude plan had docked a task one feasibility point for a stale PR, that point is restored and the rating recomputed; 13 tasks moved, and each shows `old → new`. "Leave alone" entries that existed only because of a stale PR are lifted. The rest stay as scope guidance.
- **The Codex and Devin plans' contributions** appear as cross-references (concordance column) and in the adjudication tables. Codex's per-task text was templated boilerplate with no task-specific content, so there was nothing to carry over.

## How to use this plan

1. Work wave by wave. Within a wave, take tasks in listed order unless a **lane** below says otherwise; tasks in different lanes can run in parallel.
2. One task per PR. The PR title follows the house style `scope: smoother thing doing (fixes #N)` (the `merge-prepping` skill).
3. Every task's acceptance includes the full `./gradlew testDefaultDebugUnitTest` run. Device checks marked **API 26–30** need an Android 8–11 emulator: Robolectric's host SQLite doesn't enforce the 999 limit, so those unit tests prove the chunking is correct, not that the crash is gone.

## Scoring

`rating = round(100 · (E/5)^0.25 · (I/5)^0.45 · (F/5)^0.30)`: E = evidence quality, I = impact, F = risk-adjusted feasibility, each scored 1–5. This is the Claude plan's formula, applied to every task. The one change is that F loses a point only for a file in an **active** PR (updated in the last 7 days), and then loses 2. A score of 94 is the ceiling that any realistic risk allows; 36 means correct but marginal.

## Execution waves

### Wave 1 — silent data loss and broken screens

Real bugs that users hit today, mostly on API 26–30 devices (Android 8–11). Each is small and isolated. Ship these first, one PR each.

| # | task | rating | Devin # · Codex rating |
|---|---|---|---|
| [61](#task-61) | chunk the TagDao link/tag lookups so the Resources and Courses lists stop coming up empty on API 26–30 | 94 | new |
| [1](#task-1) | chunk the notification IN-list lookups so notification sync stops failing on API 26–30 (roadmap 1+7) | 94 | D2 · C92 |
| [62](#task-62) | purge deleted resources without a 999-variable NOT IN (MyLibraryDao.deleteStalePublicNotIn) | 85 | new |
| [63](#task-63) | chunk the sync-page lookups in FeedbackDao, MeetupDao and NewsDao (the rest of task 1's bug) | 85 | new |
| [64](#task-64) | stop the password breaking the user-sync URL in UserRepositoryImpl.replacedUrl | 85 | new |
| [68](#task-68) | ServerAddressAdapter: resolve the clicked row at click time, not bind time | 75 | new |
| [3](#task-3) | return typed upload results from PersonalsRepository.uploadPersonal (roadmap 3+9, also fixes a bug) | 70 | D20 · C92 |
| [66](#task-66) | rethrow CancellationException in the sync, upload and download loops | 70 | new |
| [5](#task-5) | stop opening profile-image URLs as local files in UserEntity.serialize (roadmap 5+7) | 66 | D3 · C94 |
| [67](#task-67) | HealthUsersAdapter: rebind on first/last-name edits and stop the listener capturing a stale user | 66 | new |

### Wave 2 — measurable performance

Worthwhile gains in memory, IPC or I/O. Task 13 is the riskiest in the plan, so run it last in this wave, against a real server.

| # | task | rating | Devin # · Codex rating |
|---|---|---|---|
| [2](#task-2) | bound the text-viewer file read and parse Markdown off the main thread (roadmap 7+6) | 74 | D7 · C95 |
| [71](#task-71) | render PDF pages sharp, bounded and on a white background in the resource viewer | 70 | new |
| [12](#task-12) | check foreground eligibility once per DownloadWorker run, not on every 500 ms progress tick (roadmap 7) | 62 | D14 · C84 |
| [14](#task-14) | persist the DownloadService queue every N files instead of after every file (roadmap 7) | 59 | D8 · C91 |
| [16](#task-16) | batch the HTML-resource reconcile pass in ResourcesRepositoryImpl (roadmap 1+7) | 59 | D9 · C89 |
| [21](#task-21) | cache and file-guard the legacy EncryptedSharedPreferences in SecurePrefs (roadmap 7+4) | 59 | D10 · C84 |
| [15](#task-15) | single UPDATE queries for enterprise report archive and image attach (roadmap 1+7) | 59 | D5 · C88 |
| [45](#task-45) | fetch only the on-disk resource titles in getOfflineResourceItems (roadmap 7) | 46 | D36 · C86 |
| [53](#task-53) | skip the deep copy of exam questions in StepExam.insertCourseStepsExams (roadmap 1+9) | 43 | D16 · C87 |
| [44](#task-44) | parse retry payloads straight to kotlinx in RetryRepositoryImpl (roadmap 5+9) | 48 | D22 · C85 |
| [13](#task-13) | page the resource sync with a startkey cursor instead of CouchDB skip (roadmap 7+5) | 60 | D6 · C90 |

### Wave 3 — layer boundaries

Move data work out of the UI and services, and stop DAO types leaking. These are larger; the sequencing rules below apply.

| # | task | rating | Devin # · Codex rating |
|---|---|---|---|
| [6](#task-6) | stop ResourcesRepository leaking the Room LibraryTitleProjection, and trim its internal-only surface (roadmap 1+9) | 66 | D12 · C91 |
| [19](#task-19) | route LoginActivity's and GuestLoginExtensions' repository calls through LoginViewModel (roadmap 3+10) | 59 | D25 · C92 |
| [17](#task-17) | move the community-leaders fetch from LoginSyncManager into ConfigurationsRepository (roadmap 5+1) | 59 | D41 · C82 |
| [20](#task-20) | move the retry find-or-enqueue decision into RetryRepository.recordFailure (roadmap 5+1) | 59 | D15 · C87 |
| [70](#task-70) | make RetryQueueWorker's "already processing" guard atomic | 59 | new |
| [22](#task-22) | move shelf discovery out of SyncManager into SyncRepository (roadmap 5+1) | 59 | D23 · C85 |
| [28](#task-28) | route Progress/Courses submission reads and deletes through SubmissionsRepository (roadmap 1+4) | 57 → **64** | D34 · C89 |
| [31](#task-31) | strip UserRepositoryImpl's foreign MyLibraryDao and MeetupDao (roadmap 1+4) | 54 → **59** | D24 · C90 |
| [30](#task-30) | batch per-member visit lookups in TeamsRepositoryImpl.getJoinedMembersWithVisitInfo (roadmap 1+7) | 54 → **59** | D50 · C88 |
| [46](#task-46) | move NotificationsRepositoryImpl's team-task reads behind TeamsNotificationsRepository (roadmap 1+4) | 45 → **49** | D13 · C90 |
| [47](#task-47) | single-source the meetups sync write (drop CommunitySyncWriter) without swallowing errors (roadmap 1+5) | 44 → **50** | D4 · C86 |
| [18](#task-18) | LifeRepositoryImpl: take userId from the caller and drop SharedPrefManager (roadmap 1+3) | 59 | D81 · C88 |
| [11](#task-11) | route ActivitiesRepositoryImpl's user lookup through UserRepository (roadmap 1+4) | 62 → **66** | D21 · C82 |
| [4](#task-4) | move monthly login aggregation from ActivitiesFragment into ActivitiesViewModel (roadmap 3+10) | 66 | D42 · C80 |
| [34](#task-34) | BecomeMemberViewModel for BecomeMemberActivity's validation and create calls (roadmap 3+10) | 53 | D52 · C88 |
| [35](#task-35) | ResourceDetailViewModel for ResourceDetailFragment's data calls (roadmap 3+10) | 53 | D56 · C90 |
| [36](#task-36) | move AchievementFragment's remaining data calls into AchievementViewModel (roadmap 3+10) | 50 → **54** | D51 · C83 |
| [29](#task-29) | enterprise reports UI consumes a report DTO instead of MyTeam (roadmap 1+3) | 54 | D63 · C82 |
| [26](#task-26) | inject TimeProvider into PersonalsRepositoryImpl (roadmap 1+8) | 58 | D32 · C76 |
| [33](#task-33) | cancel stale loads in NotificationsViewModel and inject DispatcherProvider (roadmap 3+8) | 54 | D26 · C88 |
| [38](#task-38) | NotificationsRepository.updateTeamNotification takes a count, not List<News> (roadmap 1+3) | 50 → **54** | D35 · C78 |
| [40](#task-40) | let ChatViewModel resolve community config instead of ChatHistoryFragment (roadmap 3) | 48 | D28 · C84 |
| [41](#task-41) | one ConfigurationsRepository entry for Settings clear-data (roadmap 3) | 48 | D58 · C76 |
| [23](#task-23) | hoist loop-invariant patterns in VoicesRepositoryImpl.countTopLevelByTeams (roadmap 1+7) | 59 → **63** | D38 · C78 |
| [56](#task-56) | make ConfigurationsRepository.checkHealth return a result type instead of R-string text (roadmap 1+9) | 39 | D69 · C70 |
| [57](#task-57) | route the hand-rolled dataUpdateFlow filters through RealtimeSyncManager.updatesFor (roadmap 1+3) | 39 | D33 · C87 |
| [60](#task-60) | narrow ResourcesUploadSource interface for resource upload serialization (roadmap 1+4) | 36 | D57 · C85 |
| [72](#task-72) | let each upload config persist its own "uploaded" state; slim UploadRepository to network | 50 | new |
| [74](#task-74) | read community leaders through ConfigurationsRepository everywhere | 43 | new |

### Wave 4 — tests and test infrastructure

No production behaviour changes. Unpinning SDKs shrinks each CI shard's Robolectric sandboxes.

| # | task | rating | Devin # · Codex rating |
|---|---|---|---|
| [7](#task-7) | add DictionaryDao unit tests (roadmap 8) | 63 | D60 · C72 |
| [10](#task-10) | add RemovedLogDao unit tests (roadmap 8) | 63 | D40 · C79 |
| [24](#task-24) | pin getJoinRequestDetailsBatch fallbacks in NotificationsRepositoryImplTest (roadmap 8) | 58 | D70 · C74 |
| [8](#task-8) | unpin ResourcesFilterFragmentTest from Robolectric SDK 32 (roadmap 8) | 63 | D37 · C39 |
| [9](#task-9) | unpin ChatAdapterTest from Robolectric SDK 33 (roadmap 8) | 63 | D37 · C41 |
| [25](#task-25) | unpin the shard-2 SDK-32 test cohort together (roadmap 8) | 58 | D37 · C43–50 |
| [55](#task-55) | unpin the four reason-less SDK-34 adapter/UI tests together (roadmap 8) | 40 | D37 · C40–43 |
| [75](#task-75) | restore SDK_INT in DownloadService tests, try unpinning them, and trim unused Robolectric jars | 59 | new |

### Wave 5 — hardening and cleanups

999-variable hardening for lists that only overflow on unusually large planets (27, 65, 76), then small low-risk tidy-ups to pick up alongside other work.

| # | task | rating | Devin # · Codex rating |
|---|---|---|---|
| [27](#task-27) | chunk TeamLogDao.getRecentTeamVisits and delete the dead getByRemoteIds (roadmap 1+7) | 58 | D17 · C91 |
| [65](#task-65) | chunk the per-course IN lookups on the courses list | 63 | new |
| [76](#task-76) | chunk the survey-list IN lookups | 40 | new |
| [32](#task-32) | LifeCache: Gson to kotlinx.serialization (roadmap 1+9) | 54 | D71 · C74 |
| [50](#task-50) | hide SharedPreferences behind a string-store seam in LifeCache (roadmap 9) | 43 | D65 · C84 |
| [37](#task-37) | Dictionary parsing: Gson to kotlinx.serialization (roadmap 1+9) | 50 | D80 · C78 |
| [39](#task-39) | remove the R import from MyLife by moving default items to a UI-side object (roadmap 9) | 49 | D72 · C76 |
| [42](#task-42) | setHasFixedSize(true) on the Personals, References and Feedback lists (roadmap 7) | 48 | D85 · C52 |
| [59](#task-59) | setHasFixedSize(true) on the Notifications and Course Progress lists (roadmap 7) | 36 | D85 · C52 |
| [43](#task-43) | drop the dead pending-upload surface from PersonalsRepository (roadmap 1+8) | 48 | D19 · C71 |
| [49](#task-49) | drop identity .map { it } copies and hoist the loop-invariant split (roadmap 1) | 43 → **46** | D45 · C72 |
| [51](#task-51) | delete the dead MyLibraryDao.getAll (roadmap 1+8) | 43 | D30 · C73 |
| [52](#task-52) | view binding for OnboardingAdapter (roadmap 8) | 43 | D46 · C62 |
| [54](#task-54) | key ChatAdapter animation state by message id, not adapter position (roadmap 8) | 40 | D54 · C83 |
| [58](#task-58) | DiagnosticsRepositoryImpl: log with Log.w instead of printStackTrace, and rethrow CancellationException (roadmap 8) | 36 | D55 · C68 |
| [69](#task-69) | VoicesLabelManager: close-chip removes the chip's own label, not the first one with the same text | 63 | new |
| [73](#task-73) | delete verified dead code found during verification | 48 | new |

### Blocked by an active PR

The only task whose files are in a PR updated in the last 7 days.

| # | task | rating | Devin # · Codex rating |
|---|---|---|---|
| [48](#task-48) | fix MIME detection for upload file names with spaces or # (dedupe getMimeType) (roadmap 8+9) | 44 | — · C67 |

## Parallel lanes and sequencing

Tasks in the same lane edit the same files, so run them in the listed order. Different lanes can proceed at the same time.

| lane | order | shared files |
|---|---|---|
| Notifications | 1 → 24 → 27 → 38 → 46 → 33 | `NotificationDao`, `NotificationsRepositoryImpl`(+Test), `NotificationsViewModel` |
| Resources | 61 → 6 → 16 → 45 → 62 → 51 → 60 | `TagDao`, `ResourcesRepository(Impl)`, `MyLibraryDao` |
| Sync | 22 → 66a → 13 → (TransactionSyncManager keyset follow-up) | `SyncManager`, `SyncRepository(Impl)` |
| Sync-page DAOs | 63 (three commits) → 47 | `FeedbackDao`, `MeetupDao`, `NewsDao`, `CommunityRepositoryImpl`, `EventsRepositoryImpl` |
| Retry | 20 → 70 → 44 | `RetryRepository(Impl)`, `RetryQueue`, `RetryQueueWorker` |
| Personals | 3 → 26 → 43 | `PersonalsRepository(Impl)`, `PersonalsViewModel` |
| Life | 18 → 32 → 50 → 39 | `LifeRepositoryImpl`, `LifeCache`, `MyLife` |
| Viewer | 2 → 71 | `ResourceViewerFragment`, `PdfThumbnailLoader` |
| Download | 12 → 14 → 75 | `DownloadWorker`, `DownloadService`(+Tests) |
| Enterprises | 15 → 29 | `EnterprisesRepositoryImpl`, `TeamDao` |
| Configurations / login | 17 → 41 → 56 → 74; 19 in parallel | `ConfigurationsRepository(Impl)`, `LoginSyncManager`, `LoginViewModel` |
| Courses / submissions | 28 → 49 → 72; 65 and 53 in parallel | `SubmissionsRepository(Impl)`, `ProgressRepositoryImpl`, `CoursesRepositoryImpl`, `UploadRepositoryImpl` |
| User repository | 64 → 31 → 66b | `UserRepositoryImpl` |
| Teams | 30 → 46 (impl half) → 74 (repo half) | `TeamsRepositoryImpl` |
| Chat | 40 → 9 → 54 → 73 (chats subscription) | `ChatViewModel`, `ChatAdapter` |
| Test infrastructure | 8, 9, 25, 55 → 75 (jar trim last) | `@Config` pins, `app/build.gradle` `robolectricSdkJars`, `docs/TESTING.md` |

## Open-PR file locks (checked 2026-09-24)

**Active** (updated on or after 2026-09-17) and editing `app/`:

| PR | updated | files it locks |
|---|---|---|
| 17187 resources: refactored filter logic | 09-24 | `utils/FileUtils.kt`, `ui/resources/ResourcesAdapter.kt`, `ResourcesFilterFragment.kt`, `ResourcesListFilter.kt`, `ResourcesViewModel.kt`, `utils/LibraryTypeClassifier.kt` |
| 17254 teams: resources match library | 09-24 | `ui/resources/ResourcesAdapter.kt`, `ui/teams/resources/TeamResourcesAdapter.kt`, `TeamResourcesFragment.kt`, `row_team_resource.xml` |
| 17435 resources: filter labels indications (draft) | 09-18 | `ui/resources/ResourcesFragment.kt` |

Only task 48 (`FileUtils`) edits one of these. Tasks 8 and 25 edit only **test** files for `ResourcesFilterFragment` and `ResourcesAdapter`, which aren't locked. 15226 (Flutter port) is active but touches nothing under `app/`.

**Ignored** (stale, last updated before 2026-09-17): 15808, 15951, 15825, 15824, 15820, 16623, 16594, 15559, 15267, 15266, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13415, 13355, 13287, 10993, 8175, 4075. Closed since the source plans were written: 16624, 17430, 17356.

## Task specs

Tasks 1–60 are the Claude plan's specs, verbatim, each followed by its mega-plan notes. Their "leave alone (PR NNNN)" lines refer to PRs that are now ignored unless the PR is listed as active above. Tasks 61–76 are new.

<a id="task-1"></a>

### 1. chunk the notification IN-list lookups so notification sync stops failing on API 26–30 (roadmap 1+7)

rating: 94 (evidence 5 · impact 5 · feasibility 4) · proposed by: claude·repo#1 (0.625), devin·repo#7 (0.375)

context: `TransactionSyncManager.syncDb` pages `notifications` at `else -> 1000` (`services/sync/TransactionSyncManager.kt:208-213`). `NotificationsRepositoryImpl.bulkInsertFromSync` (`:507-521`) passes the whole page to `notificationDao.getByIds(parsedList.map { it.id })`, which is one `SELECT * FROM notifications WHERE id IN (:ids)` (`data/room/dao/NotificationDao.kt:39-40`). That binds up to 1000 variables. Android 8–11 (API 26–30) ship SQLite older than 3.32, where the limit is 999. `syncDb` catches the exception and returns 0, so on those devices notifications silently stop syncing once the server holds 1000 or more. The same unchunked pattern is in `getIdsByIds` (`:42-43`), `markAsRead(ids, createdAt)` (`:48-49`) and `deleteByIds` (`:102-103`), reached from `markNotificationsAsRead` (`:123-129`) and `deleteNotifications` (`:498-505`). `markSynced` in the same DAO already shows the house shape: a `@Transaction` default method that chunks by 900.

files:
- main: `data/room/dao/NotificationDao.kt`, `repository/NotificationsRepositoryImpl.kt` (`bulkInsertFromSync`, `markNotificationsAsRead`, `deleteNotifications`)
- test: `repository/NotificationsRepositoryImplTest.kt`, `data/room/dao/NotificationDaoTest.kt`
- leave alone: `TransactionSyncManager.kt` (PR 15808), the `NotificationsRepository` interface, the single-id `markAsRead(id: String)` overload

steps:
1. Rename the four list `@Query` methods to `*Internal`. Add same-named `@Transaction` default methods that `chunked(900)` and `flatMap`/`sumOf` over them, so every existing caller compiles unchanged. `markAsRead` binds `createdAt` too: 900 + 1 is still under the limit.
2. Optionally (as claude·repo#1 suggests), have `bulkInsertFromSync` read a `SELECT id, isRead FROM notifications WHERE needsSync = 1 AND id IN (:ids)` projection instead of full rows. It only reads `needsSync` and `isRead`.
3. Delete `getByIds` if nothing calls it after step 2.
4. Add a `NotificationDaoTest` case that inserts 1,200 rows and asserts `markAsRead`/`deleteByIds` affect all of them. Update the repository-test stubs for any renamed DAO method.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest" --tests "*NotificationDaoTest"` passes, and the full suite is green.
- On an API 26–30 emulator, a sync with more than 999 server notifications finishes without `SQLiteException`. The Robolectric host SQLite is newer, so the unit test proves chunk correctness, not the limit.
- Locally read notifications stay read after a re-sync, and "mark all as read" still clears the badge.

size budget: ~60–90 changed lines, 4 files.

out of scope / notes: No page-size, schema or interface change. The same bug class exists in `FeedbackRepositoryImpl.insertFeedbackList` → `feedbackDao.getByIds` (1000-doc page too). That file is locked by PR 15808, so do it as a follow-up.

**mega-plan notes:** also in: Devin #2 · Codex rating 92. Lock check: `NotificationDao.kt` and `NotificationsRepositoryImpl.kt` are in no open PR. The Feedback follow-up this task names is now **task 63** (with MeetupDao and NewsDao). The same chunking shape is reused by tasks 61, 62, 63, 65 and 76.

---

<a id="task-2"></a>

### 2. bound the text-viewer file read and parse Markdown off the main thread (roadmap 7+6)

rating: 74 (evidence 5 · impact 3 · feasibility 4) · proposed by: claude·perf#6 (0.625), kimi·perf#3 (0.375)

context: `ResourceViewerFragment.setupTextViewer()` (`ui/viewer/ResourceViewerFragment.kt:587-615`) calls `file.readText()` on the whole file, then truncates to `MAX_TEXT_VIEWER_CHARS = 500_000` (`:723`). A 50 MB resource materialises ~100 MB of UTF-16 before 99% is thrown away. For Markdown it then runs `MarkdownUtils.setMarkdownText` (`utils/MarkdownUtils.kt:66-70`), which is Markwon parse plus span building for up to 500k chars on the main thread. Markwon 4.6.2 has `toMarkdown()` and `setParsedMarkdown()` for off-thread parsing.

files:
- main: `ui/viewer/ResourceViewerFragment.kt` (`setupTextViewer` only), `utils/MarkdownUtils.kt` (two helpers next to `setMarkdownText`)
- test: `utils/MarkdownUtilsTest.kt`
- leave alone: the PDF/image/media/web branches, `ResourceViewerViewModel`, `buildMarkwon`, the other `setMarkdownText` callers

steps:
1. Inside the existing `withContext(dispatcherProvider.io)`, replace `readText()` with a bounded read. Use `bufferedReader().use { }` into a `CharArray(MAX_TEXT_VIEWER_CHARS + 1)` and loop `read(buf, off, len)` until full or -1, because one `read` may return fewer chars. Set `truncated = n > MAX_TEXT_VIEWER_CHARS`. Always do the bounded read; the byte-budget branch in kimi·perf#3 is unnecessary.
2. Add `MarkdownUtils.parseMarkdown(context, markdown): Spanned` (`create(context).toMarkdown(...)`) and `setParsedMarkdown(textView, spanned)`, which also sets `linkMovementMethod`.
3. For `ResourceType.MARKDOWN`: capture `requireContext().applicationContext` before the switch, parse in `withContext(dispatcherProvider.default)`, re-check `isAdded`, then apply on Main. Plain text keeps `textContent.text = text`.
4. Add a `MarkdownUtilsTest` case: `parseMarkdown(ctx, "**bold** text").toString() == "bold text"`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*MarkdownUtilsTest"` passes, and the full suite is green.
- A >1 MB `.md` stays responsive while rendering and shows the "content truncated" toast when over 500k chars. Links stay clickable, and a small `.txt` renders as before.

size budget: ~30 changed lines in main plus ~15 test lines, 3 files.

out of scope / notes: The layout of a 500k-char `Spanned` still runs on Main, so the gain is the parse and the memory spike, not the whole render. No paging, and no change to `MAX_TEXT_VIEWER_CHARS`.

**mega-plan notes:** also in: Devin #7 · Codex rating 95. Free. Land before **task 71**, which also edits `ResourceViewerFragment.kt` (`renderPdf`).

---

<a id="task-3"></a>

### 3. return typed upload results from PersonalsRepository.uploadPersonal (roadmap 3+9, also fixes a bug)

rating: 70 (evidence 4 · impact 3 · feasibility 4) · proposed by: grok·repo#4 (0.714), gemini·repo#2 (0.286)

context: `PersonalsRepository.uploadPersonal(personal): String` (`repository/PersonalsRepository.kt:27`) returns free-form English messages (`PersonalsRepositoryImpl.kt:115,125,145,150,156,159`) and logs with `android.util.Log.w` (`:144,149,158`). `PersonalsViewModel.uploadPersonal` (`ui/personals/PersonalsViewModel.kt:45-46`) wraps every result in `UploadState.Success(result)`. So today every failure string ("Unable to upload resource: …") reaches the Fragment as a success. The ViewModel lives in `ui/personals/`, not `ui/life/` as gemini·repo#2 says.

files:
- main: `repository/PersonalsRepository.kt`, `repository/PersonalsRepositoryImpl.kt`, `ui/personals/PersonalsViewModel.kt`
- test: `repository/PersonalsRepositoryImplTest.kt`, `ui/personals/PersonalsViewModelTest.kt` (`:79, 93, 106, 117` stub `returns "uploaded-id"`)
- leave alone: `PersonalDao`, `UploadRepository*`, DI modules, all `strings.xml` (15 open PRs touch them)

steps:
1. Declare `sealed interface PersonalUploadResult { Success(id); AlreadyUploaded; DocumentFailed(code, cause); AttachmentFailed(code, cause) }` in `PersonalsRepository.kt`. Failure cases carry the HTTP code and `Throwable?`, so stack traces aren't lost.
2. Return it from the Impl. Keep one `Log.w(TAG, …, e)` per failure path, as `docs/CODE_STYLE_GUIDE.md` prescribes, or move the logging into the VM. Don't silently drop the causes.
3. In the VM, map `Success`/`AlreadyUploaded` → `UploadState.Success` and the failures → `UploadState.Error`, reusing the message texts the repository produces today. That way no new string resources are needed.
4. Update both test classes. Add one VM test proving a failure now yields `UploadState.Error`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*PersonalsRepositoryImplTest" --tests "*PersonalsViewModelTest"` passes, and the full suite is green.
- A failed personal upload shows the error path, not the success toast.

size budget: ~60–90 changed lines, 5 files.

out of scope / notes: Sequence this with tasks 26 (TimeProvider) and 43 (dead surface); all three edit `PersonalsRepositoryImpl.kt`.

**mega-plan notes:** also in: Devin #20 · Codex rating 92. Free. Sequence 3 → 26 → 43 (same `PersonalsRepositoryImpl.kt`).

---

<a id="task-4"></a>

### 4. move monthly login aggregation from ActivitiesFragment into ActivitiesViewModel (roadmap 3+10)

rating: 66 (evidence 5 · impact 2 · feasibility 5) · proposed by: grok·repo#10 (1)

context: `ActivitiesFragment` collects `viewModel.offlineLogins` and runs `computeMonthlyCounts` (`ui/dashboard/ActivitiesFragment.kt:38-59`), a fold on `dispatcherProvider.default`, before `renderChart`. `DispatcherProvider` is injected into the Fragment only for this (`:21,29`). `ActivitiesViewModel` exposes only raw logins (`ActivitiesViewModel.kt:22-25`). The tests sit on the Fragment (`ActivitiesFragmentTest.kt:36,46`).

files:
- main: `ui/dashboard/ActivitiesFragment.kt`, `ui/dashboard/ActivitiesViewModel.kt`
- test: `ui/dashboard/ActivitiesFragmentTest.kt`, `ui/dashboard/ActivitiesViewModelTest.kt` (constructor at `:35,54`)
- leave alone: `ActivitiesRepository*` (PR 14427), layouts, MPAndroidChart

steps:
1. Inject `DispatcherProvider` (and `TimeProvider` for the one-year window) into the VM. Expose `monthlyLoginCounts: StateFlow<Map<Int, Int>>` derived from `offlineLogins`.
2. Move `computeMonthlyCounts` into the VM unchanged, including its current MONTH-only keying.
3. Have the Fragment collect the counts, render the chart or empty state only, and drop its `DispatcherProvider` injection.
4. Move the two Fragment tests into `ActivitiesViewModelTest`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Activities*"` passes, and the full suite is green.
- The Fragment has no aggregation logic, and the chart still groups the last year's logins by month.

size budget: ~50–90 changed lines, 4 files.

out of scope / notes: No chart styling change.

**mega-plan notes:** also in: Devin #42 · Codex rating 80.

---

<a id="task-5"></a>

### 5. stop opening profile-image URLs as local files in UserEntity.serialize (roadmap 5+7)

rating: 66 (evidence 5 · impact 2 · feasibility 5) · proposed by: claude·perf#1 (1)

context: `UserEntity.serialize()` calls `encodeImageToBase64(userImage)` every time (`model/UserEntity.kt:67`). For synced users, `userImage` is the http(s) URL built by `UrlUtils.getUserImageUrl` (`:168`). The non-`content://` branch then runs `File(imagePath).inputStream()` (`:124`), which throws `FileNotFoundException` and calls `printStackTrace()` (`:132`). `serialize()` runs per item on upload paths (`TeamTask.serialize`, `News.createNews`, submissions, ratings), so every upload batch pays one failed open plus one stack trace per item. The result is `null` either way.

files:
- main: `model/UserEntity.kt` (`encodeImageToBase64` only)
- test: `model/UserEntityEncodeImageTest.kt`
- leave alone: `serialize()` field layout, `addImageUrl()` and every caller

steps:
1. After the null/empty guard, return `null` for paths starting with `http://` or `https://` (ignore case).
2. In the file branch, return `null` when `!File(imagePath).isFile`, without opening a stream.
3. Leave the `content://` branch and the `catch` block as they are.
4. Add two tests: an https `_users/…/img.jpg` URL and a missing path both return `null`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*UserEntity*"` passes, and the full suite is green.
- After uploading a team task or rating for a synced user with a profile picture, logcat shows no `FileNotFoundException` from `UserEntity`. A `content://` photo still uploads as `_attachments.img`.

size budget: ~6 main lines plus ~20 test lines, 2 files.

out of scope / notes: No payload-shape change, and no `serialize(includeImage)` flag.

**mega-plan notes:** also in: Devin #3 · Codex rating 94.

---

<a id="task-6"></a>

### 6. stop ResourcesRepository leaking the Room LibraryTitleProjection, and trim its internal-only surface (roadmap 1+9)

rating: 66 (evidence 5 · impact 2 · feasibility 5) · proposed by: claude·repo#7 (0.625), grok·repo#1 (0.375)

context: `ResourcesRepository.getLibraryTitles(): List<LibraryTitleProjection>` (`repository/ResourcesRepository.kt:7,64`) exposes a DAO type (`data/room/dao/MyLibraryDao.kt:183`). The Impl returns it unchanged (`ResourcesRepositoryImpl.kt:83-84`), and it leaks into the only UI imports of the Room package: `ui/user/AchievementViewModel.kt:15,86` and `ui/user/EditAchievementFragment.kt:44,501`. Only `.id` and `.title` are used. Five interface methods have no caller outside the Impl and its test: `getMyLibrary`, `resolveLibraryItemByResourceId`, `updateUserLibrary`, `clearResourceListCache`, `getResourceTitlesMap`. `observeOpenedResourceIds` is `suspend fun …: Flow` over full `ResourceActivity` rows (`ResourcesRepositoryImpl.kt:544-548`).

files:
- main: `repository/ResourcesRepository.kt`, `repository/ResourcesRepositoryImpl.kt`, `ui/user/AchievementViewModel.kt`, `ui/user/EditAchievementFragment.kt`, optionally `data/room/dao/ResourceActivityDao.kt`
- test: `ui/user/AchievementViewModelTest.kt` (`:22, :145`), `repository/ResourcesRepositoryImplTest.kt` if you touch `observeOpenedResourceIds`
- leave alone: `MyLibraryDao.kt` (the projection stays the DAO's return type), `ResourcesViewModel.kt` (PRs 13415, 17187), `ActivitiesRepository*` (PR 14427), and `RemovedLogDao` usage

steps:
1. Add `data class LibraryTitle(val id: String, val title: String?)` next to `LibraryWithMetadata` in `ResourcesRepository.kt`, and return `List<LibraryTitle>`. Map it in the Impl, switch the two UI files and the test, and remove the `data.room.dao` import from the interface.
2. Drop the five internal-only methods from the interface. Keep them as `internal` (not `override`) in the Impl so the Impl tests still compile.
3. Optional (grok·repo#1): add `ResourceActivityDao` `SELECT resourceId … ` as a Flow query and make `observeOpenedResourceIds` non-suspend via `flow { emit(getUserById(...)) }.flatMapLatest { … }`. The Impl needs the suspend user lookup first.
4. Do **not** replace the `removedLogDao` batch calls with `ActivitiesRepository.markResourceAdded/Removed`. Those are per-item, and the batching is deliberate (commit 4345d871a; `ResourcesRepositoryImplTest.kt:895` asserts `insertAll` once for 50 rows).

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*AchievementViewModelTest" --tests "*ResourcesRepository*"` passes, and the full suite is green.
- `grep -rn "data.room" app/src/main/java/org/ole/planet/myplanet/ui` is empty.
- Edit Achievement → "add resources" still lists titles and pre-checks earlier picks.

size budget: ~30–60 changed lines, 5 files.

out of scope / notes: No DAO query change beyond the optional step 3.

**mega-plan notes:** also in: Devin #12 · Codex rating 91. Free. Same Impl as tasks 16, 45, 60 and 62; land 6 first, since it changes the interface.

---

<a id="task-7"></a>

### 7. add DictionaryDao unit tests (roadmap 8)

rating: 63 (evidence 4 · impact 2 · feasibility 5) · proposed by: gemini·repo#8 (1)

context: `data/room/dao/` has 17 DAO tests, but none for `DictionaryDao`. Its `count()`, `insertAll()` and `findByWord` exist. There is no NOCASE index on the dictionary table, so `findByWord` is an unindexed scan. gemini·repo#8 claims an index exists; it doesn't.

files:
- test (new): `data/room/dao/DictionaryDaoTest.kt`

steps:
1. Copy the in-memory Robolectric Room setup from `PersonalDaoTest.kt:29-45`.
2. Test `insertAll` then `count()`, `findByWord` exact and case-insensitive matches, and a miss returning null or empty.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*DictionaryDaoTest"` passes.

size budget: ~60–80 lines, 1 file.

out of scope / notes: No production change. Don't assert index usage.

**mega-plan notes:** also in: Devin #60 · Codex rating 72.

---

<a id="task-8"></a>

### 8. unpin ResourcesFilterFragmentTest from Robolectric SDK 32 (roadmap 8)

rating: 63 (evidence 4 · impact 2 · feasibility 5) · proposed by: codex·perf#6 (1)

context: `ui/resources/ResourcesFilterFragmentTest.kt:15` has `@Config(sdk = [32], application = Application::class)` for a smoke test that opens the dialog and finds `iv_close`. The default sandbox is SDK 36: targetSdk is 36 and `robolectric.properties` sets no sdk. CI shards classes by `path.hashCode() % 2` (`app/build.gradle:41-50`), and this is the **only** SDK-32 class in shard 1, so dropping the pin removes a whole sandbox build from that shard. `ResourcesFragmentActionTest` already opens this bottom sheet unpinned on 36. The pin was added with the file and has no stated reason.

files:
- test: `ui/resources/ResourcesFilterFragmentTest.kt` (class `@Config` only)
- leave alone: `ResourcesFilterFragment.kt` (PR 17187)

steps:
1. Remove `sdk = [32]` and keep `application = Application::class`.
2. Run the class, then the full suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ResourcesFilterFragmentTest"` and the full suite pass.

size budget: 1 line, 1 file.

out of scope / notes: Also drop the row from the pin table in `docs/TESTING.md:143-150`.

**mega-plan notes:** also in: Devin #37 · Codex rating 39. Free. After this, 25, 55 and 75, the 32/33/34 jars can go from `robolectricSdkJars` (task 75).

---

<a id="task-9"></a>

### 9. unpin ChatAdapterTest from Robolectric SDK 33 (roadmap 8)

rating: 63 (evidence 4 · impact 2 · feasibility 5) · proposed by: codex·perf#5 (1)

context: `ui/chat/ChatAdapterTest.kt:20` has `@Config(sdk = [33], …)` for clipboard-caching tests. It is the only SDK-33 class in CI shard 2; the other 33 pin, `CoursesItemUtilsTest`, hashes to shard 1. Dropping the pin therefore removes the 33 sandbox from that shard. Clipboard state goes through `ShadowClipboardManager`, which resets per test.

files:
- test: `ui/chat/ChatAdapterTest.kt` (class `@Config` only)

steps:
1. Remove `sdk = [33]` and keep `application = Application::class`.
2. Run the class, then the full suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ChatAdapterTest"` and the full suite pass.

size budget: 1 line, 1 file.

out of scope / notes: No clipboard or adapter change. If task 54 (ChatAdapter animation state) lands first, rebase.

**mega-plan notes:** also in: Devin #37 · Codex rating 41. Free. Rebase against task 54 if that lands first.

---

<a id="task-10"></a>

### 10. add RemovedLogDao unit tests (roadmap 8)

rating: 63 (evidence 4 · impact 2 · feasibility 5) · proposed by: gemini·repo#9 (1)

context: `RemovedLogDao` has no test file. Its `deleteByTypeUserAndDoc`, `deleteByTypeUserAndDocsChunked` (a `@Transaction` chunked delete) and `getRemovedDocIds` exist. The chunked delete backs the batched shelf add in `ResourcesRepositoryImpl`.

files:
- test (new): `data/room/dao/RemovedLogDaoTest.kt`

steps:
1. Use the in-memory Robolectric Room setup from `PersonalDaoTest.kt`.
2. Test `deleteByTypeUserAndDoc` with null and non-null `userId`, `deleteByTypeUserAndDocsChunked` across a chunk boundary (e.g. 1,200 ids) including the `userId IS NULL` path, and `getRemovedDocIds`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*RemovedLogDaoTest"` passes.

size budget: ~80 lines, 1 file.

out of scope / notes: The host SQLite allows 32,766 variables, so this proves the chunks are correct, not the 999-limit safety.

**mega-plan notes:** also in: Devin #40 · Codex rating 79.

---

<a id="task-11"></a>

### 11. route ActivitiesRepositoryImpl's user lookup through UserRepository (roadmap 1+4)

rating: 62 (evidence 5 · impact 2 · feasibility 4) · proposed by: devin·repo#2 (1)

context: `ActivitiesRepositoryImpl` injects `userDao: UserDao` (`:62`) only for `userDao.getByName(userId)` in `logCourseVisit` (`:100`), reading the user table directly. It already injects `userRepository: Lazy<UserRepository>` (`:52`) and calls `getUserById` through it (`:249`). `UserRepository.getUserByName` exists (`UserRepository.kt:32`).

files:
- main: `repository/ActivitiesRepositoryImpl.kt`
- test: `repository/ActivitiesRepositoryImplTest.kt` (constructor, and the `userDao` stub)

steps:
1. Replace `userDao.getByName(userId)` with `userRepository.get().getUserByName(userId)`.
2. Remove the `userDao` constructor parameter and its import.
3. Update the test's constructor and stubs.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ActivitiesRepositoryImplTest"` passes, and the full suite is green.
- Course visits still log with `parentCode`/`planetCode` populated.

size budget: ~10 lines, 2 files.

out of scope / notes: `ActivitiesRepositoryImpl.kt` is in PR 14427 (a draft "Course streak", last touched 2026-08-24). It's a trivial rebase either way.

**mega-plan notes:** also in: Devin #21 · Codex rating 82. re-scored 62 → **66** (feasibility 4 → 5: stale-PR penalty removed). Stale PRs: 14427, last touched 08-24. Verified: `UserRepository.getUserByName` is the same `userDao.getByName` exact-match query. Also rename `logCourseVisit`'s `userId` parameter to `userName`: callers pass a name (`TakeCourseViewModel:56`).

---

<a id="task-12"></a>

### 12. check foreground eligibility once per DownloadWorker run, not on every 500 ms progress tick (roadmap 7)

rating: 62 (evidence 5 · impact 2 · feasibility 4) · proposed by: claude·perf#9 (0.714), grok·perf#8 (0.286)

context: `DownloadWorker.downloadFileBody` calls `showProgressNotification` every `NOTIFICATION_UPDATE_INTERVAL_MS = 500L` while bytes stream (`services/DownloadWorker.kt:138-145, 212`). Each call runs `DownloadUtils.canStartForegroundService(context)` (`:166`), which calls `isAppInForeground` (`utils/DownloadUtils.kt:224-233`). That's a binder IPC to `ActivityManager.runningAppProcesses` plus a process-list scan, twice a second, for the whole download. Once `setForeground` succeeds, the worker stays foreground, so re-checking gains nothing.

files:
- main: `services/DownloadWorker.kt` (`showProgressNotification` plus one field)
- test: `services/DownloadWorkerTest.kt` (already spies `setForeground` at `:82` and `mockkObject(DownloadUtils)` at `:84`)
- leave alone: `utils/DownloadUtils.kt`, `services/DownloadService.kt`, the throttle logic

steps:
1. Add `private var isForegroundPromoted = false`.
2. Change the guard to `if (isForegroundPromoted || DownloadUtils.canStartForegroundService(context))`, and set the flag right after `setForeground` succeeds.
3. In the `catch`, leave the flag `false` so the next tick falls back to `notify`.
4. Add a test: a body long enough for 3 or more ticks, with `canStartForegroundService` called exactly once.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*DownloadWorkerTest"` passes, and the full suite is green.
- A long background download still updates its percentage and ends on "downloaded n/n".

size budget: ~6 main lines plus ~25 test lines, 2 files.

out of scope / notes: grok·perf#8's alternative is to swap `isAppInForeground` for `ProcessLifecycleOwner` (the precedent is `Utilities.kt:37-39`; `lifecycle-process` is already a dependency). It would also cheapen the check. But it changes semantics: `ProcessLifecycleOwner` dispatches ON_STOP ~700 ms late, and `IMPORTANCE_FOREGROUND` is not the same as "an activity is started". Keep it as a separate dedupe of the three `isAppInForeground` copies (`DownloadUtils`, `AutoSyncWorker:159`, `Utilities`) if wanted.

**mega-plan notes:** also in: Devin #14 · Codex rating 84. Free. The `isAppInForeground` copy in `AutoSyncWorker` is dead code (task 73). Keep the `DownloadUtils` copy: FGS eligibility needs process importance, not lifecycle state.

---

<a id="task-13"></a>

### 13. page the resource sync with a startkey cursor instead of CouchDB skip (roadmap 7+5)

rating: 60 (evidence 3 · impact 4 · feasibility 2) · proposed by: grok·perf#10 (1)

context: `SyncManager`'s resource pull (`services/sync/SyncManager.kt:313-405`) requests `resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip`. CouchDB `skip` costs O(skip) on the server, so late batches of a large catalogue get progressively slower. `skip` paging can also miss or duplicate ids if the server changes mid-sync. The loop feeds `removeDeletedResources` afterwards, where a missed id could delete a valid local resource. On failure the loop advances with `skip += batchSize` and sets `hadBatchFailure`.

files:
- main: `services/sync/SyncManager.kt` (the resource pull loop only)
- test: `services/sync/SyncManagerTest.kt`
- leave alone: `TransactionSyncManager.kt` (PR 15808), `AdaptiveBatchProcessor`

steps:
1. Page with `startkey=<URL-encoded JSON string of lastId>&skip=1&limit=N`, and send the first page without `startkey`. grok·perf#10's `startkey_docid` alone is meaningless on `_all_docs`, and an unquoted key gets a 400.
2. Loop until `rows.size() < N`, counting processed rows for progress. Keep `total_rows` from the `limit=0` call only as the progress denominator, and keep writing `ResourceSyncPosition` as the processed count.
3. On a failed batch, retry the same cursor up to K times, then break with `hadBatchFailure = true`. Advancing isn't possible without a cursor. Don't retry forever: the existing test `resourceTransactionSync skips removeDeletedResources when batch failure occurs` must still pass.
4. Add tests for two-page continuation, the encoded startkey, the first-row-duplicate skip, and failure termination. Stub URLs with `contains("include_docs=true")`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*SyncManagerTest"` passes, and the full suite is green.
- A full resource sync against a multi-page fixture inserts the same non-design docs, with no `skip=<n>` offsets in the URLs.

size budget: ~60 main plus ~80 test lines, 2 files.

out of scope / notes: Risk is high: it's the core sync loop. Verify against a real Planet server before merging. `TransactionSyncManager` uses the same `skip` pattern for every other table; that's a follow-up after PR 15808.

**mega-plan notes:** also in: Devin #6 · Codex rating 90. `TransactionSyncManager` uses the same `skip` paging at `:214-238`. Its PR (15808) hasn't been touched since 09-08, so that follow-up is now unblocked; do it as a separate task after this one proves out. Sequence with tasks 22 and 66a (`SyncManager.kt`). This is the riskiest task here, so verify against a real Planet server.

---

<a id="task-14"></a>

### 14. persist the DownloadService queue every N files instead of after every file (roadmap 7)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·perf#2 (1)

context: `processDownloadQueue` calls `cleanupProcessedUrls()` after every file (`services/DownloadService.kt:164`). That copies both URL sets out of SharedPreferences and rewrites both with `putStringSet` (`:184-194`): O(N²) bytes for an N-file queue, on the app's main prefs file. Reads already filter against the in-memory `processedUrls` (`getNextUrl :640`, `getRemainingCount :181`), and re-trying an already-downloaded URL is cheap (`FileUtils.checkFileExist`, `:224`).

files:
- main: `services/DownloadService.kt` (`processDownloadQueue`, `cleanupProcessedUrls`, `onDestroy`, one companion constant)
- test: `services/DownloadServiceTest.kt`
- leave alone: `getNextUrl`, `onDownloadComplete`, `DownloadUtils.kt`, `DownloadWorker.kt`, the other DownloadService tests

steps:
1. Add `QUEUE_PERSIST_INTERVAL = 10` and a `processedSinceLastPersist` counter.
2. Split out a private `persistProcessedUrls()` holding the current write. `cleanupProcessedUrls` always updates `cachedRemainingCount`, and persists only when the counter reaches the interval.
3. Persist in the `nextUrl == null` branch before `stopSelf()`, and in `onDestroy()`. Snapshot `processedUrls.toSet()` there, since `currentJob` may still be adding on another thread.
4. Test: drive `cleanupProcessedUrls` 9 times, then once more. Verify `edit()` ran 0 times, then once. Don't count `putStringSet`: each persist calls it twice.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*DownloadService*"` passes, and the full suite is green.
- Killing the app mid-queue and restarting re-downloads only missing files.

size budget: ~25 main plus ~30 test lines, 2 files.

out of scope / notes: No move of queue storage into Room.

**mega-plan notes:** also in: Devin #8 · Codex rating 91.

---

<a id="task-15"></a>

### 15. single UPDATE queries for enterprise report archive and image attach (roadmap 1+7)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#8 (1)

context: `EnterprisesRepositoryImpl.archiveReport` (`:79-85`) and the private `attachTeamImage` (`:127-139`) go through `updateTeamEntityById` (`:140-146`). That loads the whole `teams` row, flips one or two fields and `@Upsert`s every column back, so a sync landing in between gets overwritten by the stale copy. The column names are confirmed in `model/MyTeam.kt`: `_id`, `status`, `imageName`, and `@ColumnInfo(name = "isUpdated") var updated`.

files:
- main: `data/room/dao/TeamDao.kt`, `repository/EnterprisesRepositoryImpl.kt`
- test: `repository/EnterprisesRepositoryImplTest.kt`, `data/room/dao/TeamDaoTest.kt`
- leave alone: `model/MyTeam.kt` (PR 15951), `TeamsRepositoryImpl.kt`, `updateReport`

steps:
1. Add `UPDATE teams SET status = 'archived', isUpdated = 1 WHERE _id = :id` and `UPDATE teams SET imageName = :imageName, isUpdated = 1 WHERE _id = :id` (returning `Int`).
2. Call them from `archiveReport` and `attachTeamImage`. Keep `updateTeamEntityById` for `updateReport` only.
3. Image test: `addReport` uses a random UUID, so verify `setImageNameById(any(), "logo.png")`, not `"report-1"`. The short-circuit test becomes `coVerify(exactly = 0) { teamDao.setImageNameById(any(), any()) }`.
4. `TeamDaoTest`: archive hides the row from `observeNonArchivedReportsByTeamId` and sets `updated`. `setImageNameById` leaves other columns unchanged.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*EnterprisesRepositoryImplTest" --tests "*TeamDaoTest"` passes, and the full suite is green.
- Archive removes the row immediately, and both changes upload on the next sync.

size budget: ~45 lines, 4 files.

out of scope / notes: No schema change. `TeamDao.kt` is not in any open PR.

**mega-plan notes:** also in: Devin #5 · Codex rating 88. Free. Sequence 15 → 29 (same `EnterprisesRepositoryImpl.kt`).

---

<a id="task-16"></a>

### 16. batch the HTML-resource reconcile pass in ResourcesRepositoryImpl (roadmap 1+7)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·perf#3 (1)

context: After each resource sync page, `reconcileHtmlLibraries` (`repository/ResourcesRepositoryImpl.kt:724-735`) calls the public `reconcileHtmlResourceOffline(resourceId)` (`:400`) for every HTML row with no local address. Each call re-fetches the row it was just handed (`getByResourceId`, `:401`), hops to `dispatcherProvider.io` for a file check, and, if the entry file exists, does a single-row `upsert` plus `clearResourceListCache()` (`:418-419`). `MyLibrary.id == resourceId` and `getByResourceId` is `LIMIT 1`, so the in-hand object is equivalent.

files:
- main: `repository/ResourcesRepositoryImpl.kt` (`reconcileHtmlResourceOffline`, `reconcileHtmlLibraries`)
- test: `repository/ResourcesRepositoryImplTest.kt`
- leave alone: the interface, `CoursesRepositoryImpl.kt`'s copy of the loop (open PRs), `BaseContainerFragment.kt:200`, `markResourceOfflineByResourceId`

steps:
1. Extract a private helper that mutates a `MyLibrary` in place and returns whether it changed. The helper does no DAO writes.
2. The public `reconcileHtmlResourceOffline(resourceId)` becomes fetch → helper → upsert and clear cache if changed. Its behaviour is unchanged.
3. `reconcileHtmlLibraries` runs the helper on the in-hand rows, keeps the per-item try/catch and `Log.w`, and then does one `upsertAll(changed)`.
4. Test: two HTML docs with entry files on disk. Assert no `getByResourceId` call, and one extra `upsertAll`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ResourcesRepository*"` passes, with the existing `reconcileHtmlResourceOffline …` tests unchanged, and the full suite is green.
- After a fresh sync, HTML resources already under `ole/<id>/` open offline.

size budget: ~30 main plus ~35 test lines, 2 files.

out of scope / notes: The main saving is the per-item SELECT. The per-item UPSERT only happens for rows whose file is on disk.

**mega-plan notes:** also in: Devin #9 · Codex rating 89.

---

<a id="task-17"></a>

### 17. move the community-leaders fetch from LoginSyncManager into ConfigurationsRepository (roadmap 5+1)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#4 (1)

context: `LoginSyncManager.syncAdmin` (`services/sync/LoginSyncManager.kt:126-168`) builds a Gson `_find` selector, POSTs to `/_users/_find`, and writes `setCommunityLeaders(...)` plus the raw `"user_admin"` pref. That's persistence logic in a sync service. `ConfigurationsRepositoryImpl` already has `apiInterface`, `sharedPrefManager` and `@PlainGson gson`, and the read side `getCommunityLeaders()` (`:414-416`). There is no Hilt cycle.

files:
- main: `repository/ConfigurationsRepository.kt`, `repository/ConfigurationsRepositoryImpl.kt`, `services/sync/LoginSyncManager.kt`
- test: `services/sync/LoginSyncManagerTest.kt` (positional constructor at `:70-77`), `repository/ConfigurationsRepositoryImplTest.kt`
- leave alone: `SyncManager.kt` (its `loginSyncManager.syncAdmin()` call keeps compiling), `CheckVersionCallback`

steps:
1. Add `suspend fun syncCommunityLeaders()` to the interface. Implement it with the current body: blank-header early return, URL guard, the `docs[0]` → `"user_admin"` write, catch-and-log. Use the injected `gson`.
2. Inject `ConfigurationsRepository` into `LoginSyncManager`, reduce `syncAdmin()` to `applicationScope.launch { configurationsRepository.syncCommunityLeaders() }`, and drop unused imports.
3. Add repository tests for success (leaders plus `user_admin` written), non-2xx (nothing written) and blank header (no API call). Update the `LoginSyncManagerTest` constructor.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ConfigurationsRepositoryImplTest" --tests "*LoginSyncManagerTest" --tests "*SyncManagerTest"` passes, and the full suite is green.
- Community → Leaders still lists the server's admins.

size budget: ~90 lines, 5 files.

out of scope / notes: Four other places still read the leaders cache straight from prefs, and `"user_admin"` is written but never read. Both are follow-up cleanups. Sequence with task 39 (also `ConfigurationsRepository`).

**mega-plan notes:** also in: Devin #41 · Codex rating 82. Free. Change: **don't** carry the `"user_admin"` write across. Nothing reads it, in main or test (verified), so delete it here. Direct prefs reads of the leaders cache are **task 74**.

---

<a id="task-18"></a>

### 18. LifeRepositoryImpl: take userId from the caller and drop SharedPrefManager (roadmap 1+3)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: grok·repo#2 (1)

context: `LifeRepositoryImpl.updateVisibility` and `updateMyLifeListOrder` fall back to `sharedPrefManager.getUserId()` (`repository/LifeRepositoryImpl.kt:26,35`), which is the Impl's only prefs use (`:9,13`). `LifeViewModel` resolves the user for loads via `resolveUserId()` (`ui/life/LifeViewModel.kt:27-31`), but not for these two updates (`:43-59`). `resolveUserId()` reads the same pref through `UserRepository`, so this moves ownership to the user boundary rather than adding a new id source.

files:
- main: `repository/LifeRepository.kt`, `repository/LifeRepositoryImpl.kt`, `ui/life/LifeViewModel.kt`
- test: `repository/LifeRepositoryImplTest.kt`, `repository/LifeRepositoryTest.kt` (`:29-35` constructs the Impl; missing from grok's list), `ui/life/LifeViewModelTest.kt`
- leave alone: `MyLifeDao`, `LifeCache`, `MyLife`, `UserRepository*`

steps:
1. Add a `userId: String?` parameter to both repository methods. Use it when the rows carry no `userId`.
2. Remove `SharedPrefManager` from the Impl's constructor and imports.
3. Pass `resolveUserId()` from the VM.
4. Update the three test classes.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Life*"` passes, and the full suite is green.
- Hiding or reordering My Life tiles persists for the active user.

size budget: ~40–70 lines, 6 files.

out of scope / notes: Sequence with tasks 32 and 50 (`LifeCache`) and 39 (`MyLife` defaults).

**mega-plan notes:** also in: Devin #81 · Codex rating 88.

---

<a id="task-19"></a>

### 19. route LoginActivity's and GuestLoginExtensions' repository calls through LoginViewModel (roadmap 3+10)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#5 (0.625), devin·repo#9 (0.375)

context: `LoginActivity` bypasses its `LoginViewModel` (`by viewModels()`, `:87`): `userRepository.getUserByName` (`:278`), `communityRepository.syncCommunityDocs()` inside `withContext(io)` (`:373-375`), `userRepository.createGuestUser` (`:589, :631`), and `showGuestLoginDialog(userRepository)` (`:321`). `GuestLoginExtensions.kt` then calls `validateUsername`, `findUserByName` and `createGuestUser` on the raw repository, at 4 call sites in total.

files:
- main: `ui/sync/LoginViewModel.kt`, `ui/sync/LoginActivity.kt`, `ui/sync/GuestLoginExtensions.kt`
- test: `ui/sync/LoginViewModelTest.kt` (`createViewModel` at `:29-34`)
- leave alone: `ProcessUserDataActivity`/`SyncActivity`/`BecomeMemberActivity` base fields (subclasses use them), `ServerDialogExtensions.kt`, `LoginSyncManager.kt`

steps:
1. Inject `CommunityRepository` into `LoginViewModel`. Add pass-throughs: `isArchivedMember`, `validateUsername`, `findUserByName`, `createGuestUser`, and `syncCommunityDocs()` launched on `dispatcherProvider.io`.
2. Replace the Activity's direct calls with them.
3. Change the extension to `showGuestLoginDialog(viewModel: LoginViewModel)` and switch its call sites.
4. Add one VM test per new method.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*LoginViewModelTest"` passes, and the full suite is green.
- `grep -n "userRepository\.\|communityRepository\." ui/sync/LoginActivity.kt ui/sync/GuestLoginExtensions.kt` is empty.
- Archived member, guest create and "already a guest" dialogs all still work.

size budget: ~80 lines, 4 files.

out of scope / notes: Don't do devin·repo#9 step 4. `ServerDialogExtensions` extends `SyncActivity`, so it can't reach `LoginActivity`'s private `loginViewModel`. That needs its own VM.

**mega-plan notes:** also in: Devin #25 · Codex rating 92. Free. `ServerDialogExtensions` stays excluded: it's on `SyncActivity` and can't reach `loginViewModel`.

---

<a id="task-20"></a>

### 20. move the retry find-or-enqueue decision into RetryRepository.recordFailure (roadmap 5+1)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#2 (1)

context: `RetryQueue.queueFailedOperation` (`services/retry/RetryQueue.kt:26-58`) calls `retryRepository.getExistingOperation(...)`, then branches to `updateAttempt` or `enqueue`. New rows get random ids and there is no unique index, so two uploads failing on the same item at once can both see `null` and insert duplicates. The full row (including `serializedPayload`) is loaded just to read `.id`. `getPendingCount()` and `deletePendingAndAbandonedOperations()` are on the interface but only called inside the Impl.

files:
- main: `repository/RetryRepository.kt`, `repository/RetryRepositoryImpl.kt`, `services/retry/RetryQueue.kt`
- test: `repository/RetryRepositoryImplTest.kt` (incl. the `updateAttempt` test at `:91-97`, which claude's list misses), `services/retry/RetryQueueTest.kt` (`:88-121`)
- leave alone: `RetryDao.kt`, `RetryQueueWorker.kt`, the three `queueFailedOperation` callers (its signature doesn't change)

steps:
1. Replace `enqueue`/`updateAttempt`/`getExistingOperation` on the interface with `suspend fun recordFailure(...)`, taking `enqueue`'s parameters.
2. Implement it under the existing `mutex.withLock`: `retryDao.findExisting` → `markFailed`, otherwise `insert`.
3. Make `getPendingCount`/`deletePendingAndAbandonedOperations` private.
4. Reduce `queueFailedOperation` to the non-retryable early return plus one `recordFailure` call.
5. Rewrite the affected tests: "inserts when none exists" and "records attempt when one exists".

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Retry*"` passes, and the full suite is green.
- Two concurrent failures of the same item leave one retry row.

size budget: ~110 lines, 5 files.

out of scope / notes: The mutex is process-local, which is enough here (one process). Sequence with task 44, which also edits `RetryRepositoryImpl.kt`.

**mega-plan notes:** also in: Devin #15 · Codex rating 87. Free. Sequence **20 → 70 → 44** (all edit `RetryRepositoryImpl.kt`).

---

<a id="task-21"></a>

### 21. cache and file-guard the legacy EncryptedSharedPreferences in SecurePrefs (roadmap 7+4)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·perf#4 (1)

context: `SecurePrefs.getLegacyEncryptedPrefs()` (`utils/SecurePrefs.kt:152-168`) builds a Keystore `MasterKey` and calls `EncryptedSharedPreferences.create(…)` on every call. That's a Keystore round-trip plus a Tink keyset load. It runs on every login (`saveCredentials :212`, from `UserSessionManager.kt:34`), on logout (`clearCredentials :311`), and in `getUserName`/`getPassword` when the value is missing. It also creates `secure_prefs.xml` on devices that never had one. The non-legacy store is already cached (`cachedSecureStore`, `:29, 145-149`).

files:
- main: `utils/SecurePrefs.kt` (`getLegacyEncryptedPrefs` plus one `@Volatile` field)
- test: `utils/SecurePrefsTest.kt`
- leave alone: `buildSecureStore`, `getAead`, `performMigration`, public signatures, `UserSessionManager.kt`

steps:
1. Return `null` when `File(dataDir, "shared_prefs/$LEGACY_FILE_NAME.xml")` doesn't exist, mirroring `:82`.
2. Cache a successful instance with the same double-checked `synchronized` pattern as `getSecureStore`, using `applicationContext`. Don't cache a failure.
3. Test directly: call `getLegacyEncryptedPrefs` via reflection on a clean context and assert it returns `null` and creates no file. The list's `saveCredentials`-based test passes with or without the fix, because Robolectric has no AndroidKeyStore.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*SecurePrefsTest" --tests "*UserSessionManagerTest"` passes, and the full suite is green.
- A fresh install can log in, log out and log in again with "remember me". An upgrade that has `secure_prefs.xml` keeps its saved credentials.

size budget: ~15 main plus ~20 test lines, 2 files.

out of scope / notes: The file guard only helps fresh installs; existing installs already have the file, created by this bug. For them only the cache helps. Don't delete the legacy file.

**mega-plan notes:** also in: Devin #10 · Codex rating 84.

---

<a id="task-22"></a>

### 22. move shelf discovery out of SyncManager into SyncRepository (roadmap 5+1)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: claude·repo#3 (1)

context: `SyncManager.getShelvesWithDataBatchOptimized` (`services/sync/SyncManager.kt:449-485`) is data work inside the orchestrator. It reads the shelf cache (`syncRepository.getCachedShelvesWithData()`), calls `apiInterface.getDocuments(…/shelf/_all_docs)`, fans out `userSyncRepository.checkShelfBatchForDataOptimized` in `chunked(25)` with `Semaphore(8)`, and writes back `cacheShelvesWithData`. The cache halves live in `SyncRepositoryImpl` (`:204-220`) and are public only so `SyncManager` can stitch them around its network call. There is no Hilt cycle: `UserRepositoryImpl` doesn't depend on `SyncRepository`.

files:
- main: `repository/SyncRepository.kt`, `repository/SyncRepositoryImpl.kt`, `services/sync/SyncManager.kt` (delete `:449-485`, change the call at `:498`)
- test: `repository/SyncRepositoryImplTest.kt` (constructor at `:102-114`, and the 5 cache tests at `:247-305`)
- leave alone: `TransactionSyncManager.kt`, `UserRepositoryImpl.kt`, `SyncManagerTest.kt`

steps:
1. Add `suspend fun getShelvesWithData(): List<String>` and remove the two cache methods from the interface.
2. Inject `dagger.Lazy<UserSyncRepository>` into the Impl and move the body verbatim. Make the cache helpers `internal`, not `private`, so the 5 existing tests compile.
3. Call it from `SyncManager` and delete the moved code and the unused `Rows` import.
4. Add tests: cache hit (no API call), miss (API plus batch check plus write), API failure (empty list, no write; needs `mockkObject(ApiClient)`).

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*SyncRepositoryImplTest" --tests "*SyncManagerTest"` passes, and the full suite is green.
- "Library: Found N shelves with data" logs the same N, and a second sync within 6 h skips `_all_docs`.

size budget: ~100 lines, 4 files.

out of scope / notes: Sequence with task 13, which also edits `SyncManager.kt`.

**mega-plan notes:** also in: Devin #23 · Codex rating 85. `SyncRepository.kt` is only in a stale draft (15808), so no lock applies. Sequence with 13 and 66a.

---

<a id="task-23"></a>

### 23. hoist loop-invariant patterns in VoicesRepositoryImpl.countTopLevelByTeams (roadmap 1+7)

rating: 59 (evidence 4 · impact 2 · feasibility 4) · proposed by: devin·perf#9 (1)

context: `countTopLevelByTeams` loops every membership row and, inside it, every `teamId`, rebuilding `"\"_id\":\"$teamId\""` and re-running `equals(ignoreCase)` on the same row fields each time (`repository/VoicesRepositoryImpl.kt:496-502`). That's O(rows × teams) allocations. `VoicesRepositoryImplTest.kt:273-295` pins the semantics.

files:
- main: `repository/VoicesRepositoryImpl.kt` (`countTopLevelByTeams` only)

steps:
1. Precompute `teamIds.map { it to "\"_id\":\"$it\"" }` before the row loop.
2. Compute `row.viewableBy.equals("teams", true)` once per row.
3. Stronger version: when `viewableBy == "teams"`, look up the lowercased `viewableId` in a map of pre-lowercased team ids instead of scanning.
4. Keep the `matchesViewable || matchesViewIn` semantics.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*VoicesRepositoryImplTest"` passes, with per-team counts identical.

size budget: ~15–25 lines, 1 file.

out of scope / notes: `VoicesRepositoryImpl.kt` is in PRs 10993 (on hold), 13415 (brainstorm) and 15808 (draft). The edit is function-local, so expect a trivial rebase.

**mega-plan notes:** also in: Devin #38 · Codex rating 78. re-scored 59 → **63** (feasibility 4 → 5: stale-PR penalty removed). Stale PRs: 15808, 13415, 10993; none updated since 09-15, and 13415 still edits deleted Realm files.

---

<a id="task-24"></a>

### 24. pin getJoinRequestDetailsBatch fallbacks in NotificationsRepositoryImplTest (roadmap 8)

rating: 58 (evidence 3 · impact 2 · feasibility 5) · proposed by: kimi·perf#9 (1)

context: `NotificationsRepositoryImpl.getJoinRequestDetailsBatch` (`:302-340`) maps join requests to user and team names, with `"Unknown Team"`/`"Unknown User"` fallbacks and an empty-`userId` path. The known-team/known-user case is already tested (`NotificationsRepositoryImplTest.kt:506`); the fallbacks and the short-circuit are not.

files:
- test: `repository/NotificationsRepositoryImplTest.kt`

steps: Through the public entry point the existing tests use, add these cases:
- (b) an unknown team id → `"Unknown Team"`;
- (c) an empty `userId` → `"Unknown User"`, with `coVerify(exactly = 0) { userRepository.get().getUsersByIds(any()) }`;
- (d) no join-request notifications → `getJoinRequestsInfo` is not called;
- (e) a user id missing from the returned users → `"Unknown User"`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest"` passes.

size budget: ~80–120 test lines, 1 file.

out of scope / notes: No production change. Sequence with task 1 (same test file).

**mega-plan notes:** also in: Devin #70 · Codex rating 74.

---

<a id="task-25"></a>

### 25. unpin the shard-2 SDK-32 test cohort together (roadmap 8)

rating: 58 (evidence 3 · impact 2 · feasibility 5) · proposed by: codex·perf#7–10 (1, merged: four one-line fragments that save nothing individually)

context: Five test classes are pinned to `sdk = [32]` without a stated reason, and all hash to CI shard 2: `ui/resources/ResourcesAdapterTest.kt:24`, `ui/enterprises/EnterprisesReportsFragmentTest.kt:26`, `data/room/dao/ExamDaoTest.kt:18`, `data/room/dao/CourseDaoTest.kt:20`, and `data/room/dao/MyLibraryDaoTest.kt:18` (not in codex's list). While any one stays, shard 2 still builds a 32 sandbox, so codex's "do not combine" rule defeats the benefit. Equivalent unpinned tests already pass on 36: `CoursesAdapterTest` (same Mockito setup), `BaseTeamFragmentTest`, and more than 10 unpinned Room DAO tests.

files:
- test: the five classes above (class `@Config` only). For `ExamDaoTest`, `CourseDaoTest` and `MyLibraryDaoTest`, delete the whole `@Config` and the now-unused `org.robolectric.annotation.Config` import.

steps:
1. Remove the five pins together, keeping any `application =` override.
2. Update the pin table in `docs/TESTING.md:143-150`.
3. Run each class, then the full suite.

acceptance: `./gradlew testDefaultDebugUnitTest` passes, and `grep -rn "sdk = \[32\]" app/src/test` is empty once task 8 has also landed.

size budget: ~8 lines, 5 files plus docs.

out of scope / notes: Once 32 is gone from the suite, a follow-up can drop `32:` from `robolectricSdkJars` in `app/build.gradle`. That file is locked by PRs 13928, 15820, 15824 and 8175.

**mega-plan notes:** also in: Devin #37 · Codex rating 43–50. Free. Keys 30 and 31 of `robolectricSdkJars` are already unused; task 75 drops them. The `app/build.gradle` lock noted here is lifted (every PR on it is stale).

---

<a id="task-26"></a>

### 26. inject TimeProvider into PersonalsRepositoryImpl (roadmap 1+8)

rating: 58 (evidence 3 · impact 2 · feasibility 5) · proposed by: codex·repo#4 (1)

context: `PersonalsRepositoryImpl.savePersonalResource` uses `Date().time` and `serialize` uses `System.currentTimeMillis()` (`:37-47, 93-99`). The time contract can't be asserted in tests, unlike the other repositories that use the platform-free `TimeProvider` (`di/TimeModule`).

files:
- main: `repository/PersonalsRepositoryImpl.kt`
- test: `repository/PersonalsRepositoryImplTest.kt`, `data/room/dao/PersonalDaoTest.kt` (`:35` constructs the Impl; missing from codex's list)

steps:
1. Inject `TimeProvider` and replace both clock reads with `timeProvider.now()`.
2. Use a fixed fake in both tests. Assert that `Personal.date` and the serialized upload date equal the supplied value.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Personal*"` passes, and the full suite is green.

size budget: ~25–45 lines, 3 files.

out of scope / notes: Drop codex's "two clocks in one operation" rationale; they're separate operations. The value is testability and consistency.

**mega-plan notes:** also in: Devin #32 · Codex rating 76.

---

<a id="task-27"></a>

### 27. chunk TeamLogDao.getRecentTeamVisits and delete the dead getByRemoteIds (roadmap 1+7)

rating: 58 (evidence 3 · impact 2 · feasibility 5) · proposed by: devin·repo#6 (1)

context: `TeamLogDao.getRecentTeamVisits(cutoff, teamIds)` (`:15-16`) binds `teamId IN (:teamIds)` unchunked, and `TeamsRepositoryImpl.getRecentVisitCounts` passes the full team-id list. `getByRemoteIds` (`:24-25`) has zero callers. `getTeamVisitsForUsers` (`:18-19`, `user IN (:userNames)`) has the same exposure.

files:
- main: `data/room/dao/TeamLogDao.kt` only (public signatures unchanged)

steps:
1. Rename the query to `getRecentTeamVisitsInternal`. Add a same-named default method that `chunked(900)` and `flatMap`s; cutoff plus 900 ids is 901 variables.
2. Delete `getByRemoteIds`.
3. Consider the same treatment for `getTeamVisitsForUsers`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*TeamsRepositoryImplTest"` passes (mockk still intercepts default methods), and the full suite is green.

size budget: ~20–30 lines, 1 file.

out of scope / notes: A user needs roughly 1,000 teams to trip this, so it's hardening, not a live bug.

**mega-plan notes:** also in: Devin #17 · Codex rating 91. Free. **Extended:** also chunk the other all-team-ids lookups fed by `NotificationsRepositoryImpl.getTeamNotifications` (`:378-392`): `TeamNotificationDao.getByTypeAndParentIds` (`:14`) and `NewsDao.getTopLevelTeamMembership` (`:80-86`). Also delete `TeamLogDao.getByRemoteIds` (dead, confirmed).

---

<a id="task-28"></a>

### 28. route Progress/Courses submission reads and deletes through SubmissionsRepository (roadmap 1+4)

rating: 57 (evidence 4 · impact 3 · feasibility 2) · proposed by: devin·repo#5 (1)

context: `ProgressRepositoryImpl` injects submissions-domain DAOs and calls `submissionDao.getExamSubmissionsByUser` (`:72`) and `answerDao.getBySubmissionIds` (`:186`). `CoursesRepositoryImpl` already injects `SubmissionsRepository` (`:59`), yet still calls `submissionDao.getExamSubmissionsByUser` (`:469`), `answerDao.getBySubmissionIds` (`:480`), and the delete trio at `:619-626`. `SubmissionsRepositoryImpl` owns all four DAOs (`:52-55`).

files:
- main: `repository/SubmissionsRepository.kt`, `repository/SubmissionsRepositoryImpl.kt`, `repository/ProgressRepositoryImpl.kt`, `repository/CoursesRepositoryImpl.kt`
- test: `repository/ProgressRepositoryImplTest.kt`, `repository/CoursesRepositoryImplTest.kt`

steps:
1. Add `getExamSubmissionsByUser`, `getAnswersBySubmissionIds`, `getUnuploadedNonSurveySubmissionsByParentIds` and `deleteSubmissionsWithAnswers` as one-line delegates. `submissionDao.deleteByIds` is not chunked, so call the delete per chunk inside the existing loop, or chunk it yourself.
2. Swap the Progress and Courses call sites and drop `submissionDao`/`answerDao` from their constructors. Leave the exam and question reads where they are; they are course content, not submissions.
3. Keep `appDatabase.withTransaction` in `deleteCoursesProgress`. Room's transaction rides the coroutine context, so the repository call inside it is fine.
4. Update both tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ProgressRepositoryImplTest" --tests "*CoursesRepositoryImplTest" --tests "*SubmissionsRepository*"` passes, and the full suite is green.
- Course progress detail and course deletion behave as before.

size budget: ~90–110 lines, 6 files.

out of scope / notes: This is the most contended task in the backlog: PRs 15808, 14427, 14883, 13657 and 16624 touch these files. Land it after those settle.

**mega-plan notes:** also in: Devin #34 · Codex rating 89. re-scored 57 → **64** (feasibility 2 → 3: stale-PR penalty removed). None of the PRs on these files was updated after 09-10, and 16624 was closed on 09-24, so the note below about landing after them no longer applies.

---

<a id="task-29"></a>

### 29. enterprise reports UI consumes a report DTO instead of MyTeam (roadmap 1+3)

rating: 54 (evidence 4 · impact 2 · feasibility 3) · proposed by: grok·repo#5 (1)

context: `EnterprisesRepository.getReportsFlow` returns `Flow<List<MyTeam>>` (`:11`; Impl `:87-99`). `EnterprisesViewModel`, `EnterprisesReportsFragment` and `EnterprisesReportsAdapter` bind the teams Room entity's finance fields. The Impl dedupes with a 12-field `distinctByContent` comparator (`:88-97`).

files:
- main: `repository/EnterprisesRepository.kt`, `repository/EnterprisesRepositoryImpl.kt`, `ui/enterprises/EnterprisesViewModel.kt`, `ui/enterprises/EnterprisesReportsAdapter.kt`, `ui/enterprises/EnterprisesReportsFragment.kt`
- test: `EnterprisesViewModelTest`, `EnterprisesReportsAdapterTest`, `EnterprisesReportsFragmentTest`, `EnterprisesRepositoryImplTest`
- leave alone: `TeamDao`, `MyTeam` (PR 15951), the finances adapter and fragment

steps:
1. Add a `FinanceReport` data class with the fields the reports UI reads. The edit dialog reads nearly all of them (`Fragment :241-279`).
2. Map `MyTeam` → DTO in `getReportsFlow`, and replace the manual comparator with `distinctUntilChanged()` via data-class equality.
3. Retype the VM, Fragment and Adapter. Keep the attachment path through `MyTeam.getAttachmentFile(…)`, a static helper, or carry `imageName` plus `reportId`.
4. Update the four tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Enterprises*"` passes, and the full suite is green.
- CRUD, archive and CSV export still work.

size budget: ~120–150 lines, about 9 files. That's over a 5-file cap, so split it into (a) repository plus VM and (b) adapter plus fragment if the cap applies.

out of scope / notes: Sequence with task 15, which also edits `EnterprisesRepositoryImpl.kt`.

**mega-plan notes:** also in: Devin #63 · Codex rating 82.

---

<a id="task-30"></a>

### 30. batch per-member visit lookups in TeamsRepositoryImpl.getJoinedMembersWithVisitInfo (roadmap 1+7)

rating: 54 (evidence 4 · impact 2 · feasibility 3) · proposed by: devin·perf#1 (1)

context: `getJoinedMembersWithVisitInfo` builds `visitStatsMap` in one pass (`TeamsRepositoryImpl.kt:1015-1021`). devin calls it `getJoinedMembersWithStats`, which doesn't exist. Inside `orderedMembers.map` it still calls `activitiesRepository.getLastVisit(member.name)` (`:1027`) and `getOfflineVisitCount(member.id)` (`:1033`). Each is a single-row SQL query (`ActivitiesRepositoryImpl.kt:154, :66`), so the members screen issues 2N queries.

files:
- main: `repository/TeamsRepositoryImpl.kt`, `data/room/dao/OfflineActivityDao.kt`, `repository/ActivitiesRepository.kt`, `repository/ActivitiesRepositoryImpl.kt`
- test: `repository/TeamsRepositoryImplTest.kt` (stubs at `:626-628` become dead)

steps:
1. Add `GROUP BY` multimap queries: `Map<@MapColumn("userName") String, @MapColumn("last") Long>` for last visits, and per-`userId` login counts. Expose them on `ActivitiesRepository`.
2. Fetch both maps once before the `map`. Use `?: 0` for absent members, and keep "No logout record found" for null names.
3. Skip the `markMembershipsForLeave` batching devin also proposes. It normally sees one row per user per team, so it gains nothing.
4. Add a test asserting the batch methods are called once.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*TeamsRepositoryImplTest" --tests "*ActivitiesRepository*"` passes, and the full suite is green.
- Member visit counts and last-visit text are unchanged.

size budget: ~70–90 lines, 5 files.

out of scope / notes: Every file is locked. `TeamsRepositoryImpl` is in 5 PRs, `OfflineActivityDao` in 15824, `ActivitiesRepository*` in 14427. `userIds` is also exposed to the IN-list limit.

**mega-plan notes:** also in: Devin #50 · Codex rating 88. re-scored 54 → **59** (feasibility 3 → 4: stale-PR penalty removed). Stale PRs: TeamsRepositoryImpl's five PRs are all ≥ 1 week old.

---

<a id="task-31"></a>

### 31. strip UserRepositoryImpl's foreign MyLibraryDao and MeetupDao (roadmap 1+4)

rating: 54 (evidence 4 · impact 2 · feasibility 3) · proposed by: devin·repo#3 (1)

context: `UserRepositoryImpl` reads two foreign tables once each: `myLibraryDao.getByIds(resourceIds)` in `getAchievementData` (`:986`), where `ResourcesRepository.getLibraryItemsByIds` exists and `resourcesRepositoryLazy` is already injected, and `meetupDao.getByUserId(userId)` in `getShelfData` (`:1252`), which no repository method exposes.

files:
- main: `repository/UserRepositoryImpl.kt`, `repository/EventsRepository.kt`, `repository/EventsRepositoryImpl.kt`
- test: `repository/UserRepositoryImplTest.kt`, `repository/UserRepositoryBulkInsertTest.kt` (3 constructor sites; missing from devin's list)

steps:
1. Add `getMeetupsForUser(userId)` to `EventsRepository`/Impl, delegating to `meetupDao.getByUserId`.
2. In `UserRepositoryImpl`, drop both DAOs, inject `dagger.Lazy<EventsRepository>`, and swap the two call sites.
3. Fix the four test constructors.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*UserRepository*" --tests "*EventsRepository*"` passes, and the full suite is green.
- Achievements list resources, and the shelf payload has meetup ids.

size budget: ~45 lines, 5 files.

out of scope / notes: Locked by PR 15808 (`UserRepositoryImpl`) and 15108/15820/15825 (`EventsRepository*`). There is a pre-existing oddity, which this task doesn't change: achievements carry resource `_id`s, while `MyLibraryDao.getByIds` matches the local `id`.

**mega-plan notes:** also in: Devin #24 · Codex rating 90. re-scored 54 → **59** (feasibility 3 → 4: stale-PR penalty removed). Stale PRs: UserRepositoryImpl is in draft 15808 only.

---

<a id="task-32"></a>

### 32. LifeCache: Gson to kotlinx.serialization (roadmap 1+9)

rating: 54 (evidence 3 · impact 2 · feasibility 4) · proposed by: gemini·repo#4 (1)

context: `LifeCache` (`repository/LifeCache.kt`) serializes `CachedMyLifeItem` with Gson. The kotlinx serialization plugin is applied (`app/build.gradle:5`; CLAUDE.md's "only 3 plugins" line is out of date). Existing cached JSON stays readable: the injected Gson writes nulls, and the field names equal the property names. Any decode failure already returns `null` and falls back to the DB (`:41-43`).

files:
- main: `repository/LifeCache.kt`, `di/SharedPreferencesModule.kt` (`:57-61` builds `LifeCache` by hand; missing from gemini's list)
- test: `repository/LifeCacheTest.kt`, `repository/LifeRepositoryImplTest.kt` (`:46`; missing from gemini's list)

steps:
1. Mark `CachedMyLifeItem` `@Serializable` and give its nullable fields `= null` defaults.
2. Inject `Json` in place of `Gson`, and update the module and both tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*LifeCacheTest" --tests "*LifeRepository*"` passes.
- An existing cached menu survives the upgrade.

size budget: ~30–50 lines, 4 files.

out of scope / notes: `LifeCache` still depends on `SharedPreferences`; see task 50. Keep the key prefix `myLifeCache_`.

**mega-plan notes:** also in: Devin #71 · Codex rating 74.

---

<a id="task-33"></a>

### 33. cancel stale loads in NotificationsViewModel and inject DispatcherProvider (roadmap 3+8)

rating: 54 (evidence 3 · impact 2 · feasibility 4) · proposed by: claude·perf#7 (1)

context: `NotificationsViewModel.loadNotifications` (`ui/notifications/NotificationsViewModel.kt:69-78`) launches in `viewModelScope` without cancelling a previous load. `NotificationsFragment` calls it from three places (`:77, 87, 173`), so a slow load for an old filter can overwrite a newer one. The VM doesn't inject `DispatcherProvider`, which the style guide requires. Its perf claim is weak: task dates are already parsed in the repository, and `formatNotification` is cheap.

files:
- main: `ui/notifications/NotificationsViewModel.kt`
- test: `ui/notifications/NotificationsViewModelTest.kt` (constructor at `:46`)

steps:
1. Add `DispatcherProvider` as the last constructor parameter.
2. Keep a `loadJob` and cancel it before relaunching.
3. Optionally run the `formatNotification` map in `withContext(dispatcherProvider.default)` and add `.flowOn(default)` on the two grouped flows.
4. Test: two back-to-back loads where the first repository call is slower (`coAnswers { delay }`) end with the second result.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Notifications*"` passes, and the full suite is green.
- Rapid filter switching always ends on the last filter tapped.

size budget: ~15 main plus ~25 test lines, 2 files.

out of scope / notes: No change to the formatting or grouping rules.

**mega-plan notes:** also in: Devin #26 · Codex rating 88.

---

<a id="task-34"></a>

### 34. BecomeMemberViewModel for BecomeMemberActivity's validation and create calls (roadmap 3+10)

rating: 53 (evidence 3 · impact 3 · feasibility 2) · proposed by: kimi·repo#2 (1)

context: `BecomeMemberActivity` calls `userRepository.createMember`, `validateUsername` (twice, including a debounced `usernameValidationJob`) and `cleanupDuplicateUsers` inside `lifecycleScope.launch`. `userRepository` is inherited from `ProcessUserDataActivity` (`:59`), which other subclasses use, so it can't be removed.

files:
- main: `ui/user/BecomeMemberActivity.kt`, new `ui/user/BecomeMemberViewModel.kt`
- test (new): `ui/user/BecomeMemberViewModelTest.kt`

steps:
1. Create a `@HiltViewModel` injecting `UserRepository` and `DispatcherProvider`. Move the three calls and the debounce into it. The stale-input guard at `:243` becomes VM-side input tracking.
2. Expose a validation-error `StateFlow` and a create-result event.
3. Have the Activity collect them, and still call the inherited `startUpload(...)` and auto-login itself.
4. Unit-test the VM.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*BecomeMemberViewModelTest"` passes, and the full suite is green.
- Signing up still validates usernames live and creates a member.

size budget: ~120–150 lines, 3 files.

out of scope / notes: Blocked by PR 13848 (on hold), which edits `BecomeMemberActivity` and strings. Drop kimi's "remove the repository fields" step; they're inherited.

**mega-plan notes:** also in: Devin #52 · Codex rating 88. `BecomeMemberActivity.kt` is in PR 13848, which hasn't been touched since 08-24, so it's ignored. **Check while moving:** the offline path at `:118-128` calls `securityCallback.onChanged()` directly, and the upload chain may call it again, which would auto-login twice (unverified).

---

<a id="task-35"></a>

### 35. ResourceDetailViewModel for ResourceDetailFragment's data calls (roadmap 3+10)

rating: 53 (evidence 3 · impact 3 · feasibility 2) · proposed by: kimi·repo#1 (1)

context: `ResourceDetailFragment` calls `resourcesRepository.resolveLibraryItem`/`setUserLibrary`, `ratingsRepository.getRatingSummary` and `userRepository.getUserModel` from `viewLifecycleOwner.lifecycleScope.launch`. `resourcesRepository` and `userRepository` are declared in `BaseResourceFragment` (`:53-55`) and used there. Only `ratingsRepository` is the Fragment's own.

files:
- main: `ui/resources/ResourceDetailFragment.kt`, new `ui/resources/ResourceDetailViewModel.kt`
- test (new): `ui/resources/ResourceDetailViewModelTest.kt`

steps:
1. Create a `@HiltViewModel` with the three repositories plus `DispatcherProvider`. It owns the calls and exposes a `StateFlow` of library plus rating summary, and add/remove actions.
2. Have the Fragment observe it and delegate. Delete only the `ratingsRepository` field.
3. The base-class callback `onDownloadComplete()` (`:48`) triggers a VM refresh.
4. Unit-test the VM with `MainDispatcherRule`/`TestDispatcherProvider`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ResourceDetailViewModelTest"` passes, and the full suite is green.
- Resource detail shows its rating, and the library add/remove toggle works.

size budget: ~120–150 lines, 3 files.

out of scope / notes: Blocked by PR 13355 (P2P sharing, on hold), which edits this Fragment.

**mega-plan notes:** also in: Devin #56 · Codex rating 90. `ResourceDetailFragment.kt` is in PR 13355, which hasn't been touched since 08-24, so it's ignored.

---

<a id="task-36"></a>

### 36. move AchievementFragment's remaining data calls into AchievementViewModel (roadmap 3+10)

rating: 50 (evidence 3 · impact 2 · feasibility 3) · proposed by: kimi·repo#3 (1)

context: `AchievementViewModel` exists and already injects `userRepository` and `resourcesRepository`. `AchievementFragment` still calls `userRepository.getAchievementData(uId, pCode)` (~`:90`), `userRepository.getUserModel()` (~`:106`) and `resourcesRepository.downloadResources(listOf(lib))` (~`:205`) directly.

files:
- main: `ui/user/AchievementFragment.kt`, `ui/user/AchievementViewModel.kt`
- test: `ui/user/AchievementViewModelTest.kt` (constructs the VM at `:42`; missing from kimi's list)

steps:
1. Add `loadAchievementData()` and `downloadResource(lib)`. Populate the existing `_user` via a side-effect-free loader, not `loadUserAndAchievement`.
2. Switch the Fragment to them.
3. Extend the VM test. `EditAchievementFragment` shares this VM and is unaffected.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*AchievementViewModelTest"` passes, and the full suite is green.
- The achievements screen and resource download are unchanged.

size budget: ~50–80 lines, 3 files.

out of scope / notes: `AchievementFragment.kt` is in PR 15824 (gamification). Sequence with task 6, which also edits `AchievementViewModel`.

**mega-plan notes:** also in: Devin #51 · Codex rating 83. re-scored 50 → **54** (feasibility 3 → 4: stale-PR penalty removed). Stale PRs: 15824.

---

<a id="task-37"></a>

### 37. Dictionary parsing: Gson to kotlinx.serialization (roadmap 1+9)

rating: 50 (evidence 3 · impact 2 · feasibility 3) · proposed by: gemini·repo#5 (1)

context: `repository/DictionaryMapper.kt` and `DictionaryRepositoryImpl.kt` parse the dictionary file through Gson `JsonArray` plus `GsonUtils.getString`. That returns `""` for missing, null or non-string values, and reads the misspelled key `antonoym` (`DictionaryMapper.kt:21`).

files:
- main: `repository/DictionaryMapper.kt`, `repository/DictionaryRepositoryImpl.kt`
- test: `repository/DictionaryRepositoryImplTest.kt`, `repository/DictionaryMapperTest.kt` (missing from gemini's list)

steps:
1. Decode into a `@Serializable` DTO with `String = ""` defaults, `@SerialName("advance_code")` and `@SerialName("antonoym")`, using the provided `Json` (`coerceInputValues`/`ignoreUnknownKeys`, `NetworkModule.kt:86-89`).
2. Map the DTO to `DictionaryEntity`.
3. Add tests for null, missing and numeric fields. With `isLenient` a number decodes as `"1"`, not `""`; decide and pin that.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Dictionary*"` passes.
- The dictionary loads the same word count.

size budget: ~60–90 lines, 4 files.

out of scope / notes: The parse-failure path stays `Failed`.

**mega-plan notes:** also in: Devin #80 · Codex rating 78.

---

<a id="task-38"></a>

### 38. NotificationsRepository.updateTeamNotification takes a count, not List<News> (roadmap 1+3)

rating: 50 (evidence 3 · impact 2 · feasibility 3) · proposed by: codex·repo#1 (1)

context: `updateTeamNotification(teamId, news: List<News>)` (`NotificationsRepository.kt:31`, Impl `:366-378`) only reads `news.size`, so the notifications boundary imports a Voices entity, and callers materialise rows just to count them.

files:
- main: `repository/NotificationsRepository.kt`, `repository/NotificationsRepositoryImpl.kt`, `ui/teams/voices/TeamsVoicesViewModel.kt` (`:58`, `newsList.size`; missing from codex's list)
- test: `repository/NotificationsRepositoryImplTest.kt`, `repository/TeamChatBadgeIntegrationTest.kt` (`:106`), `ui/teams/voices/TeamsVoicesViewModelTest.kt` (`:67`)

steps:
1. Change the signature to `(teamId, count: Int)` and drop the `News` imports.
2. Update all 5 call sites.
3. In the integration test, use `countTopLevelByTeam` and cover zero and nonzero badge transitions.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest" --tests "*TeamChatBadgeIntegrationTest" --tests "*TeamsVoicesViewModelTest"` passes.
- The team chat badge appears and clears as before.

size budget: ~35–50 lines, 6 files.

out of scope / notes: `TeamsVoicesViewModel.kt` is in PR 10993 (on hold). Sequence with tasks 1 and 24 (same repository and test).

**mega-plan notes:** also in: Devin #35 · Codex rating 78. re-scored 50 → **54** (feasibility 3 → 4: stale-PR penalty removed). Stale PRs: 10993.

---

<a id="task-39"></a>

### 39. remove the R import from MyLife by moving default items to a UI-side object (roadmap 9)

rating: 49 (evidence 2 · impact 2 · feasibility 4) · proposed by: gemini·repo#1 (1)

context: The Room entity `model/MyLife.kt` imports `org.ole.planet.myplanet.R` for `defaultItemPairs`/`defaultItems` in its companion. `LifeRepository.getMyLifeByUserId(userId, defaultItems)` (`LifeRepository.kt:8`) already takes the defaults as a parameter so the repository stays resource-free. gemini's destination, `LifeRepositoryImpl`, would put `R` into the repository layer.

files:
- main: `model/MyLife.kt`, new `ui/life/LifeDefaults.kt`, `ui/dashboard/DashboardPluginFragment.kt` (`:145`), `ui/life/LifeViewModel.kt` (`:37`)
- test: `ui/life/LifeViewModelTest.kt` (`:77`)

steps:
1. Move `defaultItemPairs`/`defaultItems` into `object LifeDefaults` under `ui/life/`.
2. Update the three callers.
3. Confirm `MyLife.kt` has no `android.*` or `R` imports left.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*Life*"` passes.
- A new user's My Life menu is seeded as before.

size budget: ~30–40 lines, 5 files.

out of scope / notes: Sequence with task 18 (`LifeViewModel`).

**mega-plan notes:** also in: Devin #72 · Codex rating 76.

---

<a id="task-40"></a>

### 40. let ChatViewModel resolve community config instead of ChatHistoryFragment (roadmap 3)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: claude·repo#6 (1)

context: `ChatHistoryFragment.refreshChatHistory` (`:136-142`) reads `sharedPrefManager.getParentCode()`/`getCommunityName()` and passes them into `ChatViewModel.loadChatHistoryScreenData(userId, parentCode, communityName)` (`:126-130`). `ConfigurationsRepository.getCommunityConfiguration()` returns the same values.

files:
- main: `ui/chat/ChatHistoryFragment.kt`, `ui/chat/ChatViewModel.kt`
- test: `ui/chat/ChatViewModelTest.kt` (constructor at `:59`; calls at `:208, 242, 273, 288`)

steps:
1. Inject `ConfigurationsRepository` into the VM and change the method to `loadChatHistoryScreenData(userId)`.
2. Read the config inside and pass it to `loadShareTargets`.
3. Have the Fragment pass only the user id; `sharedPrefManager` stays, still used at `:49`.
4. Stub the config in the test so the existing assertions keep their meaning.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*ChatViewModelTest"` passes.
- The share dialog lists the same targets.

size budget: ~35 lines, 3 files.

out of scope / notes: No `ChatRepository` change.

**mega-plan notes:** also in: Devin #28 · Codex rating 84. Free. The `updatesFor("chats")` subscription in `ChatViewModel` (`:70`) is dead, because nothing emits "chats". It's deleted in task 73. Keep the replay-driven refresh: it's the only phone refresh (see the rejected chat-history task).

---

<a id="task-41"></a>

### 41. one ConfigurationsRepository entry for Settings clear-data (roadmap 3)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: grok·repo#7 (1)

context: `SettingsViewModel.clearAllData()` (`:42-45`) sequences `configurationsRepository.clearAllData()` then `clearPreferences()` itself.

files:
- main: `repository/ConfigurationsRepository.kt`, `repository/ConfigurationsRepositoryImpl.kt`, `ui/settings/SettingsViewModel.kt`
- test: `ui/settings/SettingsViewModelTest.kt`, `repository/ConfigurationsRepositoryImplTest.kt`

steps:
1. Add `suspend fun clearLocalAppData()` that calls the two existing methods in order (DB, then prefs).
2. Have the VM call only it, then emit `_clearDataEvent`.
3. Tests: the VM verifies one call, and the Impl test verifies the order.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*SettingsViewModelTest" --tests "*ConfigurationsRepositoryImplTest"` passes.

size budget: ~25–40 lines, 5 files.

out of scope / notes: Keep the two underlying methods. Sequence with tasks 17 and 56 (same repository).

**mega-plan notes:** also in: Devin #58 · Codex rating 76.

---

<a id="task-42"></a>

### 42. setHasFixedSize(true) on the Personals, References and Feedback lists (roadmap 7)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: kimi·perf#5 (1)

context: `binding.rvMypersonal` (`ui/personals/PersonalsFragment.kt:35`), `rvReferences` (`ui/references/ReferencesFragment.kt:29`) and `rvFeedback` (`ui/feedback/FeedbackListFragment.kt:47`) are `match_parent` and ListAdapter-backed, but never call `setHasFixedSize(true)`. The house precedent is `ChatHistoryFragment.kt:172`, `SurveyFragment.kt:115` and `LifeFragment.kt:77`.

files:
- main: the three Fragments

steps: Add `setHasFixedSize(true)` after each `layoutManager =`.

acceptance: `./gradlew assembleDefaultDebug`. Lists render and scroll unchanged.

size budget: 3 lines, 3 files.

out of scope / notes: The benefit is small: it only skips the parent `requestLayout` on granular updates. For the static References list it's zero.

**mega-plan notes:** also in: Devin #85 · Codex rating 52.

---

<a id="task-43"></a>

### 43. drop the dead pending-upload surface from PersonalsRepository (roadmap 1+8)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: claude·repo#10 (1)

context: `PersonalsRepository.getPendingPersonalUploads(userId)` (`:25`, Impl `:69-71`) has zero production callers. Only `PersonalsRepositoryImplTest` (`:156-164`) uses it, and it is the only user of `PersonalDao.getPendingUploads` (`:26-27`). `updatePersonalAfterSync` (`:26`, Impl `:73-75`) is public, but only `uploadPersonal` in the same Impl calls it (`:155`).

files:
- main: `repository/PersonalsRepository.kt`, `repository/PersonalsRepositoryImpl.kt`, `data/room/dao/PersonalDao.kt` (`getPendingUploads` only)
- test: `repository/PersonalsRepositoryImplTest.kt` (`:156-171`)

steps:
1. Delete `getPendingPersonalUploads` and `PersonalDao.getPendingUploads`.
2. Make `updatePersonalAfterSync` private.
3. Delete the two tests; `uploadPersonal`'s success tests already verify `updateUploadedStatus`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Personal*"` passes, and `grep -rn getPendingPersonalUploads app/src` is empty.

size budget: ~25 lines, 4 files.

out of scope / notes: Sequence with tasks 3 and 26 (same Impl).

**mega-plan notes:** also in: Devin #19 · Codex rating 71.

---

<a id="task-44"></a>

### 44. parse retry payloads straight to kotlinx in RetryRepositoryImpl (roadmap 5+9)

rating: 48 (evidence 5 · impact 1 · feasibility 5) · proposed by: claude·perf#10 (1)

context: `RetryRepositoryImpl.executeOperation` parses the payload with `JsonParser.parseString(...)` (`:74`), then deep-copies it with `payload.toKotlinx().jsonObject` (`:90`) because `putDoc`/`postDoc` take kotlinx. The Gson tree is used for nothing else.

files:
- main: `repository/RetryRepositoryImpl.kt`
- test: `repository/RetryRepositoryImplTest.kt`

steps:
1. Use `Json.parseToJsonElement(payload).jsonObject`. A non-object still throws into the existing `catch` → `TerminalFailure`.
2. Delete the `toKotlinx` line and the `JsonParser`/`toKotlinx` imports.
3. Add a `"[1,2]"` → `TerminalFailure` regression test. It passes before and after, so it guards rather than discriminates.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*RetryRepositoryImplTest" --tests "*retry*"` passes.

size budget: ~6 main plus ~15 test lines, 2 files.

out of scope / notes: kotlinx is strict where Gson is lenient; every producer writes strict JSON. Sequence with task 20.

**mega-plan notes:** also in: Devin #22 · Codex rating 85. Free. Sequence 20 → 70 → **44**.

---

<a id="task-45"></a>

### 45. fetch only the on-disk resource titles in getOfflineResourceItems (roadmap 7)

rating: 46 (evidence 4 · impact 1 · feasibility 5) · proposed by: grok·perf#5 (1)

context: `ResourcesRepositoryImpl.getOfflineResourceItems` always calls `getResourceTitlesMap()` → `myLibraryDao.getResourceTitles()`, a `SELECT resourceId, title` over the whole `my_library` table, to label the small on-disk subset.

files:
- main: `repository/ResourcesRepositoryImpl.kt`, `data/room/dao/MyLibraryDao.kt`
- test: `repository/ResourcesRepositoryImplTest.kt` (`:1422-1458` must be re-stubbed)

steps:
1. Add `getResourceTitlesByIds(ids)` (`IN`, chunked by 900).
2. Walk and group first. Return empty if nothing is grouped, otherwise fetch titles for `grouped.keys`.
3. Keep the sort and the "Unknown" fallback.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ResourcesRepositoryImplTest"` passes, and storage detail lists the same items.

size budget: ~30 lines, 3 files.

out of scope / notes: The file walk dominates; this saves a few ms of a 2-column scan. Sequence with tasks 6 and 16 (same Impl).

**mega-plan notes:** also in: Devin #36 · Codex rating 86. Free. Land after task 61 (the tag lookups on the same screen) and task 6.

---

<a id="task-46"></a>

### 46. move NotificationsRepositoryImpl's team-task reads behind TeamsNotificationsRepository (roadmap 1+4)

rating: 45 (evidence 2 · impact 2 · feasibility 3) · proposed by: devin·repo#1 (0.5), grok·repo#6 (0.5)

context: `NotificationsRepositoryImpl` injects `TeamTaskDao` (`:35`) and reads the teams feature's table directly: `getById` (`:244`), `getByIds` (`:282`), `getByTitles` (`:346`), `getTasksForUserBetween` (`:396`). Both proposals wrap the DAO in a new pass-through class (`TeamTasksReader` / `TaskNotificationLookup`). That still reads `team_tasks` directly and adds a second path into the teams domain beside the already-injected `Lazy<TeamsNotificationsRepository>` (`:31`, bound at `RepositoryModule.kt:201`).

files:
- main: `repository/TeamsNotificationsRepository.kt`, `repository/TeamsRepositoryImpl.kt` (it already injects `teamTaskDao`, `:77`), `repository/NotificationsRepositoryImpl.kt`
- test: `repository/NotificationsRepositoryImplTest.kt`, `repository/TeamChatBadgeIntegrationTest.kt`

steps:
1. Add the four task reads to `TeamsNotificationsRepository` and implement them in `TeamsRepositoryImpl`.
2. Drop `teamTaskDao` (and, per grok, look at `TeamNotificationDao` too) from `NotificationsRepositoryImpl`.
3. Update both tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Notifications*" --tests "*TeamChatBadge*"` passes, and `NotificationsRepositoryImpl` has no `TeamTaskDao` reference.

size budget: ~60–80 lines, 5 files.

out of scope / notes: The corrected route touches `TeamsRepositoryImpl` (5 open PRs) and `TeamsNotificationsRepository`, so land it after the teams PRs. The pass-through-class version both lists propose is not worth doing.

**mega-plan notes:** also in: Devin #13 · Codex rating 90. re-scored 45 → **49** (feasibility 3 → 4: stale-PR penalty removed). **Verified direction:** give `TeamsNotificationsRepository` real join methods, not pass-throughs. `NotificationsRepositoryImpl` already injects `Lazy<TeamsNotificationsRepository>` (`:31`), which is bound to `TeamsRepositoryImpl`, which owns `teamTaskDao`. The methods: `getTaskTeamLabel(taskId)` (keep the `link.teams` parsing from `:244-252`), `getTeamNamesByTaskIds` (`:278-299`), `getTeamNamesByTaskTitles` (`:342-363`), and `getTeamIdsWithTasksDue(userId, start, end): Set<String>` (`:396-397`). Then remove `teamTaskDao` (`:35`), and replace `Calendar.getInstance()` at `:395` with `timeProvider`.

---

<a id="task-47"></a>

### 47. single-source the meetups sync write (drop CommunitySyncWriter) without swallowing errors (roadmap 1+5)

rating: 44 (evidence 3 · impact 2 · feasibility 2) · proposed by: devin·repo#4 (1)

context: `CommunityRepositoryImpl.insertMeetupsFromSync` writes the events domain's meetups table. Its body matches `EventsRepositoryImpl.batchInsertMeetups` except error handling: the Events version wraps everything in `catch (e: Exception) { printStackTrace(); 0 }`. `TransactionSyncManager` injects `CommunitySyncWriter` only for the `"meetups"` table (`:80, :103`) and saves `heavy_sync_skip_<table>` after the handler (`:257-282`). A straight swap to `batchInsertMeetups` would swallow write failures, including `CancellationException`, and move the checkpoint past a page that was never written.

files:
- main: `services/sync/TransactionSyncManager.kt`, `di/ServiceModule.kt` (`:93`), `di/RepositoryModule.kt` (`bindCommunitySyncWriter`, `:113`), `repository/CommunitySyncWriter.kt` (delete), `repository/CommunityRepositoryImpl.kt`, `repository/EventsRepositoryImpl.kt`
- test: `TransactionSyncManagerTest`, `TransactionSyncManagerCheckpointTest`

steps:
1. Make `batchInsertMeetups` rethrow, or at least rethrow `CancellationException`. Its other caller, `SyncRepositoryImpl.kt:52`, must tolerate that. Alternatively, add a throwing variant to `EventsSyncWriter`.
2. Point `TransactionSyncManager` at `EventsSyncWriter`, and update `ServiceModule`.
3. Remove the binding, the interface, the Community copy and `meetupDao` from `CommunityRepositoryImpl`.
4. Update both TSM tests.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*TransactionSyncManager*" --tests "*Community*" --tests "*Events*"` passes.
- A failing meetups write fails the page instead of advancing the checkpoint.

size budget: ~70–90 lines, 8 files.

out of scope / notes: This collides head-on with PR 15808 (draft sync rewrite: TSM, `ServiceModule`, `CommunitySyncWriter`, `CommunityRepositoryImpl`) and with `RepositoryModule` (15824, 15825). Wait for them.

**mega-plan notes:** also in: Devin #4 · Codex rating 86. re-scored 44 → **50** (feasibility 2 → 3: stale-PR penalty removed). **Verified direction:** the two bodies are identical. Delete the Community copy, and make `EventsRepositoryImpl.batchInsertMeetups` **throw** instead of returning 0: its catch also swallows cancellation, and it lets `TransactionSyncManager` treat a failed page as done and advance the checkpoint. Point `TransactionSyncManager:80/:103` and `ServiceModule:93` at `EventsSyncWriter`. Delete `CommunitySyncWriter`, its binding (`RepositoryModule:113`) and `CommunityRepositoryImpl.meetupDao`. Update `EventsRepositoryImplTest` `batchInsertMeetupsException` (`:167`) to assert a throw. Caveat: on the shelf path (`SyncRepositoryImpl:189`), a throw now aborts the remaining meetup batches for that shelf.

---

<a id="task-48"></a>

### 48. fix MIME detection for upload file names with spaces or # (dedupe getMimeType) (roadmap 8+9)

rating: 44 (evidence 3 · impact 2 · feasibility 2) · proposed by: gemini·perf#1 (1)

context: `FileUtils.getMimeType` (`utils/FileUtils.kt:233`) uses `MimeTypeMap.getFileExtensionFromUrl`, which returns `""` for names containing spaces, `#` or non-ASCII characters. The upload callers pass decoded file names. `FileUtils.getFileExtension` exists (`:197`), and `Utilities.getMimeType` (`utils/Utilities.kt:89-91`) already implements the robust version. This is a robustness bug, not the performance gain gemini claims.

files:
- main: `utils/FileUtils.kt`, `utils/Utilities.kt`
- test: `utils/FileUtilsTest.kt`

steps:
1. Make `FileUtils.getMimeType` use `getFileExtension` (or delegate to or remove the `Utilities` duplicate, updating its callers).
2. Add a test that `"my photo.png"` maps to `image/png` (check the Robolectric `MimeTypeMap` shadow has the mapping).

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*FileUtilsTest" --tests "*UploadManagerTest"` passes.

size budget: ~10–20 lines, 2–3 files.

out of scope / notes: `FileUtils.kt` is in the **active** PR 17187 (updated 2026-09-24), so do this after it merges.

**mega-plan notes:** also in: Codex rating 67 (Devin dropped it). **Still blocked:** `utils/FileUtils.kt` is in **active** PR 17187 (updated 2026-09-24). Wait for it to merge, or fold the fix into it.

---

<a id="task-49"></a>

### 49. drop identity .map { it } copies and hoist the loop-invariant split (roadmap 1)

rating: 43 (evidence 4 · impact 1 · feasibility 4) · proposed by: devin·perf#4 (1)

context: `CoursesRepositoryImpl.kt:553-554, 912` and `SubmissionsRepositoryImpl.kt:292, 789` copy DAO results with no-op `.map { it }` / `.map { question -> question }`. The DAOs return read-only `List`s and nobody mutates the copies. `parentId.split("@").firstOrNull()` runs inside the answers loop (`SubmissionsRepositoryImpl.kt:743`), in the sync-download path, not upload as devin's acceptance says.

files:
- main: `repository/CoursesRepositoryImpl.kt`, `repository/SubmissionsRepositoryImpl.kt`

steps:
1. Delete the identity maps.
2. Hoist `val examIdPart = parentId.split("@").firstOrNull() ?: parentId` above the loop.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CoursesRepository*" --tests "*SubmissionsRepository*"` passes.

size budget: ~10 lines, 2 files.

out of scope / notes: Cleanup only. Both files are open-PR-locked (13657/15808/16624 and 14427/15808), so ride along with task 28 or land after them.

**mega-plan notes:** also in: Devin #45 · Codex rating 72. re-scored 43 → **46** (feasibility 4 → 5: stale-PR penalty removed).

---

<a id="task-50"></a>

### 50. hide SharedPreferences behind a string-store seam in LifeCache (roadmap 9)

rating: 43 (evidence 4 · impact 1 · feasibility 4) · proposed by: codex·repo#3 (1)

context: `repository/LifeCache.kt` imports Android `SharedPreferences` and AndroidX `edit` (`:3-5, 21-23`). That keeps the cache policy out of a platform-free core, and its tests mock `SharedPreferences.Editor` (`LifeCacheTest.kt:19-33`).

files:
- main: `repository/LifeCache.kt`
- test: `repository/LifeCacheTest.kt`

steps:
1. Add a minimal internal `StringStore` (`get(key): String?`, `put(key, value)`).
2. Give `LifeCache` an internal primary constructor that takes the store, plus a public secondary constructor that wraps `SharedPreferences`. Hilt keeps using the `@Provides` in `SharedPreferencesModule`.
3. Rewrite the tests on an in-memory fake, keeping the malformed-JSON, read-through, write-through and defensive-copy cases.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*LifeCacheTest"` passes, and My Life survives a restart.

size budget: ~60–90 lines, 2 files.

out of scope / notes: Speculative until a KMP module split exists. Do it together with task 32 or skip it.

**mega-plan notes:** also in: Devin #65 · Codex rating 84.

---

<a id="task-51"></a>

### 51. delete the dead MyLibraryDao.getAll (roadmap 1+8)

rating: 43 (evidence 3 · impact 1 · feasibility 5) · proposed by: devin·repo#8 (1)

context: `MyLibraryDao.getAll()` (`:25-26`) has no production caller. Its only use is `ResourcesRepositoryLibrarySyncTest.kt:124`, which counts rows.

files:
- main: `data/room/dao/MyLibraryDao.kt`
- test: `repository/ResourcesRepositoryLibrarySyncTest.kt`

steps:
1. Delete `getAll()`.
2. Assert with the existing `countByTitle(...)` or the test's `getById("res1")` checks. Don't add a test-only `countAll()` as devin proposes; that just swaps one test-only method for another.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ResourcesRepositoryLibrarySyncTest"` passes.

size budget: ~5 lines, 2 files.

out of scope / notes: devin says a `MyLibraryEntity` import would become unused; that import doesn't exist.

**mega-plan notes:** also in: Devin #30 · Codex rating 73. Free. Task 73 deletes the other dead `MyLibraryDao` list methods (`getByUnderscoreIds`, `deleteByIds`) in the same file.

---

<a id="task-52"></a>

### 52. view binding for OnboardingAdapter (roadmap 8)

rating: 43 (evidence 3 · impact 1 · feasibility 5) · proposed by: kimi·perf#7 (1)

context: `OnboardingAdapter.instantiateItem()` (`ui/onboarding/OnboardingAdapter.kt:23-38`) inflates `R.layout.onboarding_item` and does three `findViewById`s. It's the last manual-lookup adapter in a view-binding codebase. Generated binding does the same lookups, so there is no speed gain; the value is the convention.

files:
- main: `ui/onboarding/OnboardingAdapter.kt`

steps:
1. Use `OnboardingItemBinding.inflate(...)` and add and return `binding.root`.
2. Drop the unused `ImageView`/`TextView`/`R` imports.

acceptance: `./gradlew assembleDefaultDebug`. Onboarding pages render with the correct colours in light and dark themes.

size budget: ~15 lines, 1 file.

out of scope / notes: None.

**mega-plan notes:** also in: Devin #46 · Codex rating 62.

---

<a id="task-53"></a>

### 53. skip the deep copy of exam questions in StepExam.insertCourseStepsExams (roadmap 1+9)

rating: 43 (evidence 3 · impact 1 · feasibility 5) · proposed by: claude·perf#8 (1)

context: `StepExam.insertCourseStepsExams` starts with `exam.toKotlinx().jsonObject` (`model/StepExam.kt:43`). `toKotlinx()` (`utils/GsonUtils.kt:48-53`) recursively copies the whole tree, including `questions`, which is then read only for `.size` (`:60`). Its only caller is the exams sync (`SurveysRepositoryImpl.kt:392`), not course sync as claude says. The same questions are deep-copied again by `ExamQuestion.insertExamQuestions`.

files:
- main: `model/StepExam.kt`
- test: `model/StepExamTest.kt`

steps:
1. Build a shallow Gson `JsonObject` from `exam.entrySet()` minus `"questions"`, then call `.toKotlinx()` on it.
2. Set `noOfQuestions = GsonUtils.getJsonArray("questions", exam).size()`.
3. Add tests: 3 questions → 3, and malformed `questions` → 0.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*StepExam*"` passes, and exam question counts are unchanged after sync.

size budget: ~8 main plus ~30 test lines, 2 files.

out of scope / notes: A small saving. Better folded into a broader "stop Gson→kotlinx round-trips in sync parsers" pass that also covers `ExamQuestion.kt:69`.

**mega-plan notes:** also in: Devin #16 · Codex rating 87.

---

<a id="task-54"></a>

### 54. key ChatAdapter animation state by message id, not adapter position (roadmap 8)

rating: 40 (evidence 3 · impact 1 · feasibility 4) · proposed by: grok·perf#9 (1)

context: `ChatAdapter.animatedMessages` is a `HashMap<Int, Boolean>` keyed by adapter position. `prependMessages` remaps every key when history loads (`ui/chat/ChatAdapter.kt:132-145`), and the bind-time listeners capture `position`, which goes stale after a prepend. `ChatMessage` has a stable `id` (a UUID default). This is a correctness and readability fix, not the performance win grok claims.

files:
- main: `ui/chat/ChatAdapter.kt`, `ui/chat/ChatDetailFragment.kt` (`:514-515` reads `lastAnimatedPosition`)
- test: `ui/chat/ChatAdapterTest.kt`

steps:
1. Key the animation state by `ChatMessage.id` (e.g. `lastAnimatedId`) and drop the remap in `prependMessages`.
2. Resolve items at click time via `bindingAdapterPosition`.
3. Keep, or rename in lockstep, the members `ChatDetailFragment` uses.
4. Add tests: a prepend doesn't re-animate shown responses, and `clearData` resets state.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ChatAdapterTest"` passes. New responses animate once, and loading older history doesn't re-animate.

size budget: ~40–60 lines, 3 files.

out of scope / notes: `ChatMessage` ids are per-instance UUIDs, so re-created messages get new ids. Confirm history loads reuse instances, or key by server id.

**mega-plan notes:** also in: Devin #54 · Codex rating 83. Free.

---

<a id="task-55"></a>

### 55. unpin the four reason-less SDK-34 adapter/UI tests together (roadmap 8)

rating: 40 (evidence 3 · impact 1 · feasibility 4) · proposed by: codex·perf#1–4 (1, merged: four one-line fragments that save nothing individually)

context: `ui/life/LifeAdapterTest.kt:22`, `ui/sync/ServerAddressAdapterTest.kt:17`, `ui/personals/PersonalsAdapterTest.kt:22` and `ui/voices/VoicesActionsTest.kt:29` pin `sdk = [34]` with no stated reason, and all hash to CI shard 2. `DownloadServiceResumeTest` (also shard 2) keeps a 34 sandbox alive there, so the saving is only per fork: fewer forks need a 34 sandbox. It is never a whole-shard saving unless the DownloadService tests are unpinned too.

files:
- test: the four classes (class `@Config` only)

steps:
1. Remove the four pins together.
2. `VoicesActionsTest`: `VoicesActions` is an `object` whose `dateFormatter` captures `Locale.getDefault()` and `ZoneId.systemDefault()` on first load (`VoicesActions.kt:34-36`). On the shared 36 sandbox, the formatted-date test becomes order-dependent. Either make the formatter resolve the zone at format time, or note the assumption.
3. Update `docs/TESTING.md:143-150`; the table already omits `PersonalsAdapterTest`, `MyLibraryDaoTest` and `CoursesItemUtilsTest`.

acceptance: `./gradlew testDefaultDebugUnitTest` passes.

size budget: ~4–10 lines, 4–5 files plus docs.

out of scope / notes: Unpinning the three DownloadService tests is a separate, riskier call (FGS-type APIs).

**mega-plan notes:** also in: Devin #37 · Codex rating 40–43. Free. Task 75 handles the three DownloadService pins mentioned here.

---

<a id="task-56"></a>

### 56. make ConfigurationsRepository.checkHealth return a result type instead of R-string text (roadmap 1+9)

rating: 39 (evidence 2 · impact 1 · feasibility 5) · proposed by: gemini·repo#3 (1)

context: `ConfigurationsRepositoryImpl.checkHealth()` returns a `String` built partly from `context.getString(R.string.server_sync_successfully)`. Its only caller is `AutoSyncWorker`, which just logs it. `SyncActivity` doesn't call it, despite gemini's claim. `Context` stays in the class for about 12 other uses.

files:
- main: `repository/ConfigurationsRepository.kt`, `repository/ConfigurationsRepositoryImpl.kt`, `services/AutoSyncWorker.kt`
- test: `repository/ConfigurationsRepositoryImplTest.kt` (`:110-150`)

steps:
1. Return a small sealed or enum result that includes `NotConfigured` (for today's blank-URL `""`) and `InitFailed`.
2. Log its name in `AutoSyncWorker`.
3. Update the test assertions.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ConfigurationsRepositoryImplTest" --tests "*AutoSyncWorker*"` passes.

size budget: ~30–40 lines, 4 files.

out of scope / notes: This removes one R usage; it does not decouple Context. Sequence with tasks 17 and 41.

**mega-plan notes:** also in: Devin #69 · Codex rating 70. Free. Sequence with tasks 17, 41 and 74 (Configurations).

---

<a id="task-57"></a>

### 57. route the hand-rolled dataUpdateFlow filters through RealtimeSyncManager.updatesFor (roadmap 1+3)

rating: 39 (evidence 2 · impact 1 · feasibility 5) · proposed by: codex·repo#2 (1)

context: `RealtimeSyncManager` exposes `updatesFor(table)` (`services/sync/RealtimeSyncManager.kt:20-27`). `RealtimeSyncHelper.setupRealtimeSync` (`ui/sync/RealtimeSyncMixin.kt:29-38`) filters the raw `dataUpdateFlow` by a table set itself. `HealthViewModel:72` and `UserRepositoryImpl:90` do the same by hand for single tables.

files:
- main: `services/sync/RealtimeSyncManager.kt`, `ui/sync/RealtimeSyncMixin.kt`, `ui/health/HealthViewModel.kt`
- test: `services/sync/RealtimeSyncManagerTest.kt`

steps:
1. Add `updatesFor(tables: Set<String>)`, and make the string overload delegate to it.
2. Use it in `RealtimeSyncHelper` and `HealthViewModel`.
3. Add `updatesFor` tests: exclusion and ordering. None exist today.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*RealtimeSync*" --tests "*HealthViewModel*"` passes.

size budget: ~30–40 lines, 4 files.

out of scope / notes: For an empty set, prefer a never-emitting filter over `emptyFlow()`, which completes. `UserRepositoryImpl` is locked by 15808, so skip it there. No change to debounce or buffers.

**mega-plan notes:** also in: Devin #33 · Codex rating 87. Free. The `UserRepositoryImpl:90` hand-filter that was skipped because of 15808 can now be included.

---

<a id="task-58"></a>

### 58. DiagnosticsRepositoryImpl: log with Log.w instead of printStackTrace, and rethrow CancellationException (roadmap 8)

rating: 36 (evidence 2 · impact 1 · feasibility 4) · proposed by: codex·repo#7 (1)

context: `saveLogToRoom` and `saveLogsToRoom` catch `Exception` and call `printStackTrace` (`repository/DiagnosticsRepositoryImpl.kt:59-76, 79-95`). The `catch` also swallows `CancellationException` (`:73, :92`). codex's other steps (shared metadata, "no partial-row fallback") are already how the code works.

files:
- main: `repository/DiagnosticsRepositoryImpl.kt`
- test: `repository/DiagnosticsRepositoryImplTest.kt`

steps:
1. Replace `printStackTrace()` with `Log.w(TAG, …, e)`.
2. Rethrow `CancellationException`.
3. Add one test for the rethrow.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*DiagnosticsRepositoryImplTest"` passes.

size budget: ~10–20 lines, 2 files.

out of scope / notes: Don't remove the logging outright; this is the only diagnostic for a failed crash-log write.

**mega-plan notes:** also in: Devin #55 · Codex rating 68. Free. Task 66 applies the same rethrow in the sync, upload and download loops.

---

<a id="task-59"></a>

### 59. setHasFixedSize(true) on the Notifications and Course Progress lists (roadmap 7)

rating: 36 (evidence 2 · impact 1 · feasibility 4) · proposed by: kimi·perf#6 (1)

context: `binding.rvNotifications` (`ui/notifications/NotificationsFragment.kt:65`) and `rvMyprogress` (`ui/courses/CoursesProgressFragment.kt:20`) are `match_parent` and don't call `setHasFixedSize(true)`. kimi's third site, `rvReports`, is `wrap_content` (`fragment_reports.xml:52`), so the flag would be wrong there. That contradicts the list's own precondition check.

files:
- main: `ui/notifications/NotificationsFragment.kt`, `ui/courses/CoursesProgressFragment.kt`

steps: Add `setHasFixedSize(true)` after each `layoutManager =`. Leave `rvReports` alone.

acceptance: `./gradlew assembleDefaultDebug`. Both lists render and scroll unchanged.

size budget: 2 lines, 2 files.

out of scope / notes: Tiny benefit; see task 42.

**mega-plan notes:** also in: Devin #85 · Codex rating 52.

---

<a id="task-60"></a>

### 60. narrow ResourcesUploadSource interface for resource upload serialization (roadmap 1+4)

rating: 36 (evidence 2 · impact 1 · feasibility 4) · proposed by: kimi·repo#4 (1)

context: `ResourcesRepository.serializeForUpload(library, user)` (`:139`) is consumed only by `services/upload/UploadConfigs.kt:281`, yet sits on the general resources interface. kimi wants to move it into `UploadRepository`, which it says "already owns upload serialization". That's false: `UploadRepository` only sends data, and each domain repository owns its serializer. The serializer also needs `timeProvider` and `deviceNameProvider` from `ResourcesRepositoryImpl` (`:68-69`).

files:
- main: new `repository/ResourcesUploadSource.kt`, `repository/ResourcesRepository.kt`, `repository/ResourcesRepositoryImpl.kt`, `services/upload/UploadConfigs.kt`
- test: `services/upload/UploadConfigsTest.kt` (`:51`)

steps:
1. Following the `TeamsSyncRepository` pattern, declare `ResourcesUploadSource` with `getPendingResourceUploads`, `serializeForUpload` and `markResourceUploaded`, implemented by `ResourcesRepositoryImpl`.
2. Have `UploadConfigs` depend on it. That needs one `@Binds`, and `RepositoryModule` is locked by 15824/15825. Alternatively, inject the Impl-bound interface via an existing binding.
3. Update the test.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*UploadConfigsTest" --tests "*ResourcesRepository*"` passes.

size budget: ~40–60 lines, 5 files.

out of scope / notes: Small benefit. Sequence with tasks 6, 16 and 45 (same Impl).

**mega-plan notes:** also in: Devin #57 · Codex rating 85. The `RepositoryModule.kt` lock (15825, 15824) is lifted, so the `@Binds` can be added directly.

---

<a id="task-61"></a>

### 61. chunk the TagDao link/tag lookups so the Resources and Courses lists stop coming up empty on API 26–30

rating: 94 (evidence 5 · impact 5 · feasibility 4) · origin: mega-plan 999-variable sweep (same bug class as task 1; no source list had it)

context: `ResourcesRepositoryImpl.getEnrichedLibraries` (`repository/ResourcesRepositoryImpl.kt:790-800`) loads the whole public library (`myLibraryDao.getPublic()`) and passes every id to `getResourceTagsBulk`. That reaches `TagsRepositoryImpl.getLinkedTagsBulk` (`repository/TagsRepositoryImpl.kt:66-81`), which calls `TagDao.getByDbAndLinkIds(db, linkIds)` (`data/room/dao/TagDao.kt:27`, `db = :db AND linkId IN (:linkIds)`) and then `TagDao.getByIds(allTagIds)` (`:30`). Neither is chunked. On API 26–30 (SQLite < 3.32, limit 999), a planet with 999 or more public resources throws `SQLiteException`. `ResourcesViewModel.kt:102-107` catches it, so the Resources list just shows nothing. Course tags go through the same helper from `CoursesRepositoryImpl.mapCourses` (`:891-897`, all courses), so large course catalogues hit it too.

files:
- main: `data/room/dao/TagDao.kt` only
- test (new): `data/room/dao/TagDaoTest.kt`; `repository/TagsRepositoryImplTest.kt` only if a stub name changes
- leave alone: `TagsRepositoryImpl.kt` and every caller (public DAO signatures stay the same), `ResourcesViewModel.kt` (active PR 17187)

steps:
1. Rename both list queries to `getByDbAndLinkIdsInternal` / `getByIdsInternal`.
2. Add same-named default methods: `linkIds.distinct().chunked(900).flatMap { getByDbAndLinkIdsInternal(db, it) }` (900 ids plus `db` is 901 variables), and the same for `getByIds`. Return early on an empty input.
3. `TagDaoTest`: in-memory Room (copy the setup from `PersonalDaoTest.kt:29-45`). Insert 1,200 link rows and assert all 1,200 come back. Also test an empty list and duplicate ids.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*TagDaoTest" --tests "*TagsRepositoryImplTest"` passes, and the full suite is green.
- On an API 26–30 emulator against a planet with at least 1,000 public resources, the Resources tab lists items and shows their tags.

size budget: ~25 main plus ~60 test lines, 2 files.

out of scope / notes: Robolectric's host SQLite allows 32,766 variables, so the test proves the chunking is correct, not that the limit is enforced. Land this before task 45, which also touches the title lookups.

---

<a id="task-62"></a>

### 62. purge deleted resources without a 999-variable NOT IN (MyLibraryDao.deleteStalePublicNotIn)

rating: 85 (evidence 5 · impact 4 · feasibility 4) · origin: mega-plan 999-variable sweep

context: After a resource sync, `SyncManager` (`services/sync/SyncManager.kt:415`) passes **every** synced resource id to `ResourcesRepositoryImpl.removeDeletedResources` (`:563-567`), which calls `MyLibraryDao.deleteStalePublicNotIn(ids)` (`data/room/dao/MyLibraryDao.kt:168-172`): `DELETE … WHERE _rev IS NOT NULL AND _rev != '' AND isPrivate = 0 AND resourceId NOT IN (:currentResourceIds)`. Any real library has thousands of ids, so on API 26–30 this always throws. `SyncManager.kt:422` catches it and logs "Resource cleanup failed", so resources deleted on the server are **never** removed locally on those devices. A `NOT IN` can't simply be chunked, because each chunk would delete everything outside that chunk.

files:
- main: `data/room/dao/MyLibraryDao.kt`
- test: `data/room/dao/MyLibraryDaoTest.kt`, `repository/ResourcesRepositoryImplTest.kt` (only if its stub of `deleteStalePublicNotIn` changes)
- leave alone: `SyncManager.kt` (tasks 13, 22 and 66 own it), `removeDeletedResources`'s signature

steps:
1. Add `@Query("SELECT resourceId FROM my_library WHERE _rev IS NOT NULL AND _rev != '' AND isPrivate = 0 AND resourceId IS NOT NULL") suspend fun getStalePublicCandidateIds(): List<String>`.
2. Add `deleteStalePublicByResourceIdsInternal(ids)`, with the same predicate plus `AND resourceId IN (:ids)`.
3. Replace `deleteStalePublicNotIn` with a `@Transaction` default method of the same name: `(getStalePublicCandidateIds().toSet() - current.toSet()).chunked(900).forEach { deleteStalePublicByResourceIdsInternal(it) }`. Rows with a NULL `resourceId` aren't candidates, which keeps today's `NULL NOT IN (…)` behaviour (never deleted).
4. While in the file, chunk `markAsNotOfflineByResourceIds` (`:147`) the same way. `deleteOfflineResources` (`ResourcesRepositoryImpl.kt:887-899`) passes every selected offline resource, while `FreeSpaceWorker` already batches by 25.
5. `MyLibraryDaoTest`: seed 1,500 current rows plus 3 stale public rows, 1 private row, 1 unsynced row (`_rev` empty) and 1 NULL-`resourceId` row. Call the method with the 1,500 ids and assert that only the 3 stale public rows are gone.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*MyLibraryDaoTest" --tests "*ResourcesRepository*"` passes, and the full suite is green.
- On API 26–30, delete a resource on the server and sync: it disappears locally, and logcat has no "Resource cleanup failed".

size budget: ~30 main plus ~50 test lines, 2 files.

out of scope / notes: The `hadBatchFailure` guard in `SyncManager` stays; it protects against partial id lists. Sequence with tasks 51 and 6 (same DAO or repository).

---

<a id="task-63"></a>

### 63. chunk the sync-page lookups in FeedbackDao, MeetupDao and NewsDao (the rest of task 1's bug)

rating: 85 (evidence 5 · impact 4 · feasibility 4) · origin: claude·repo#1 follow-up note, widened by the mega-plan sweep

context: `TransactionSyncManager` pages these tables at 1000 docs (`else -> 1000`, `services/sync/TransactionSyncManager.kt:208-213`). Each insert handler looks up the whole page by id in one unchunked `IN`:
- `FeedbackRepositoryImpl.insertFeedbackList` (`:113-115`) → `FeedbackDao.getByIds` (`:26`)
- `CommunityRepositoryImpl.insertMeetupsFromSync` (`:66-69`) → `MeetupDao.getByMeetupIds` (`:40`)
- `VoicesRepositoryImpl.insertNewsList` (`:358-367`) → `NewsDao.getByUnderscoreIds` (`:24`)

So on API 26–30, feedback, meetups and news stop syncing once the server holds 1000 or more of them, and `syncDb` returns 0 without surfacing anything. The shelf path into `EventsRepositoryImpl.batchInsertMeetups` stays at 50 or fewer and is safe.

files:
- main: `data/room/dao/FeedbackDao.kt`, `data/room/dao/MeetupDao.kt`, `data/room/dao/NewsDao.kt`
- test: `data/room/dao/FeedbackDaoTest.kt`, `data/room/dao/MeetupDaoTest.kt`, `data/room/dao/NewsDaoTest.kt`
- leave alone: the three repositories and `TransactionSyncManager` (signatures unchanged)

steps:
1. For each of the three methods, apply the task 1 shape: rename the `@Query` to `…Internal`, add a same-named default method `ids.distinct().chunked(900).flatMap { …Internal(it) }`, and return early on empty input. `MeetupDao.getByTeamIds` (`:15-19`) already shows the pattern in the same file.
2. Add one 1,200-id test per DAO.
3. Land it as three commits, one per DAO, so each can be reverted on its own.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*FeedbackDaoTest" --tests "*MeetupDaoTest" --tests "*NewsDaoTest" --tests "*TransactionSyncManager*"` passes, and the full suite is green.
- On API 26–30, a planet with more than 999 news posts syncs Voices completely.

size budget: ~45 main plus ~90 test lines, 6 files.

out of scope / notes: If task 47 lands first, the meetup sync goes through `EventsRepositoryImpl.batchInsertMeetups`, which still calls `MeetupDao.getByMeetupIds`, so the DAO fix is still what matters.

---

<a id="task-64"></a>

### 64. stop the password breaking the user-sync URL in UserRepositoryImpl.replacedUrl

rating: 85 (evidence 5 · impact 4 · feasibility 4) · origin: mega-plan verification of devin·perf#10 (dropped for its perf claim; the real bug is here)

context: `replacedUrl` (`repository/UserRepositoryImpl.kt:716-723`) does `url.replaceFirst("[^:]+:[^@]+@".toRegex(), "${model.name}:${password}@")`. The replacement string is interpreted: `$` starts a group reference and `\` escapes. A password containing `$` therefore throws `IllegalArgumentException: Illegal group reference`, and one containing `\` is silently corrupted. The name and password aren't URL-encoded either, so `@ / : # % ?` in them produce a broken URL. It is used for every user check, create and update (`:742, 755, 790, 810, 822`), and `checkAndUploadUser` swallows the exception (`:736`), so the member's profile silently never uploads. The regex is also recompiled on every call.

files:
- main: `repository/UserRepositoryImpl.kt` (`replacedUrl` plus one companion `val`)
- test: `repository/UserRepositoryImplTest.kt`

steps:
1. Hoist `private val USERINFO_REGEX = Regex("[^:]+:[^@]+@")` into the companion.
2. Use the lambda overload, so nothing is interpreted: `url.replaceFirst(USERINFO_REGEX) { "${enc(model.name)}:${enc(password)}@" }`, where `enc` is `URLEncoder.encode(s, "UTF-8").replace("+", "%20")`.
3. Test: mock `SecurePrefs.getPassword` to return `"p\$ss@1\\"`, call `checkIfUserExists`, capture the URL passed to `apiInterface.getJsonObject`, and assert it contains `p%24ss%401%5C@`. Add a second case for a plain password, where the URL must be unchanged.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*UserRepositoryImplTest"` passes, and the full suite is green.
- A member whose password contains `$` or `@` edits their profile, syncs, and the change reaches the server.

size budget: ~8 main plus ~30 test lines, 2 files.

out of scope / notes: Check with the server team that CouchDB accepts percent-encoded userinfo (it does per RFC 3986; OkHttp passes it through). Don't touch `UrlUtils.getUrl()`, which builds the admin URL separately.

---

<a id="task-65"></a>

### 65. chunk the per-course IN lookups on the courses list

rating: 63 (evidence 4 · impact 2 · feasibility 5) · origin: mega-plan 999-variable sweep

context: `CoursesViewModel` (`:124-127`) → `ProgressRepositoryImpl.getCourseProgress` (`:36-42`) and `CoursesRepositoryImpl.mapCourses` (`:891-897`) pass **all** course ids to `CourseStepDao.getByCourseIds` (`:11`) and `CourseProgressDao.getByUserAndCourseIds` (`:13`, plus one `userId` bind). Both are unchecked `IN` lists. This breaks on API 26–30 once a planet has about 1,000 courses. That's rare, so this is hardening; `CourseDao.getByCourseIds` already chunks at 300.

files:
- main: `data/room/dao/CourseStepDao.kt`, `data/room/dao/CourseProgressDao.kt`
- test: `data/room/dao/CourseProgressDaoTest.kt`, new `data/room/dao/CourseStepDaoTest.kt`

steps:
1. Apply the `…Internal` + `chunked(900)` default-method shape. Use `chunked(899)` for the query that also binds `userId`.
2. Add 1,200-id tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*CourseProgressDaoTest" --tests "*CourseStepDaoTest" --tests "*Courses*"` passes.

size budget: ~20 main plus ~50 test lines, 4 files.

out of scope / notes: Don't touch `getByCourseUsersAndSteps` (`CourseProgressDao.kt:31-46`), which is already bounded at 250×3 and guarded by its cross-product test.

---

<a id="task-66"></a>

### 66. rethrow CancellationException in the sync, upload and download loops

rating: 70 (evidence 4 · impact 3 · feasibility 4) · origin: mega-plan sweep (widens task 58's point to the hot paths)

context: The house pattern is `catch (e: CancellationException) { throw e }` before `catch (e: Exception)` (`TransactionSyncManager.kt:301`, `UploadCoordinator.kt:193/214`, `DownloadRepositoryImpl.kt:66`). These sites skip it, so a cancelled sync, upload or download keeps running or reports a failure:
- `SyncManager.kt:222`: `startFullSync` reports a cancellation as a sync error via `handleException`.
- `SyncManager.kt:400`: the resource batch loop catches it, does `skip += batchSize`, and spins through every remaining batch.
- `SyncManager.kt:422` and `:544`: cleanup and `myLibraryTransactionSync`.
- `SyncRepositoryImpl.kt:97` and `:188`: `processShelfParallel` and the shelf batch loop.
- `UploadCoordinator.kt:87`: `runPipeline` turns a cancellation into `UploadResult.Failure(retryable = true)`.
- `TransactionSyncManager.kt:180` and `:468`: `syncHealthData` inside the per-user loop, and the notification-read `async`.
- `DownloadWorker.kt:68/116` (the per-URL loop keeps going after the worker is stopped) and `:89`.
- `UserDataWorker.kt:70-90`: the `runCatching {}` chain.
- `UserRepositoryImpl.fetchUserSecurityData` (`:381-399`).

files:
- main: `services/sync/SyncManager.kt`, `repository/SyncRepositoryImpl.kt`, `services/upload/UploadCoordinator.kt`, `services/sync/TransactionSyncManager.kt`, `services/DownloadWorker.kt`, `services/UserDataWorker.kt`, `repository/UserRepositoryImpl.kt`
- test: `SyncManagerTest.kt`, `UploadCoordinatorTest.kt`, `DownloadWorkerTest.kt`, `UserDataWorkerTest.kt`

steps:
1. Add the rethrow clause at each site. For `runCatching`, use `.onFailure { if (it is CancellationException) throw it }`, or replace it with try/catch.
2. Tests: cancel the job mid-loop under `runTest`, and assert the loop stops (the API mock is called once) and that no `Failure` or `handleException` is produced. Do one test per loop: resources, upload pipeline, download URLs.
3. Split it into two PRs: (a) `SyncManager`, `SyncRepositoryImpl`, `UploadCoordinator`; (b) the workers, `TransactionSyncManager` and `UserRepositoryImpl`.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests "*SyncManagerTest" --tests "*UploadCoordinatorTest" --tests "*DownloadWorkerTest" --tests "*UserDataWorkerTest"` passes, and the full suite is green.
- Logging out mid-sync no longer logs a stream of batch failures.

size budget: ~40 main plus ~80 test lines, about 11 files across the two PRs.

out of scope / notes: Sequence (a) with tasks 13 and 22 (`SyncManager`, `SyncRepositoryImpl`). A shared `rethrowIfCancellation()` helper is optional; the house style repeats the clause.

---

<a id="task-67"></a>

### 67. HealthUsersAdapter: rebind on first/last-name edits and stop the listener capturing a stale user

rating: 66 (evidence 5 · impact 2 · feasibility 5) · origin: gemini·perf#6 verification note (claude scoring) + mega-plan check

context: The row shows `getFullName()` (firstName plus lastName; `ui/health/HealthUsersAdapter.kt:48`, `UserEntity.kt:155`), but the DiffUtil callback compares only `name`, `userImage` and `joinDate` (`:22-32`). A first- or last-name edit never rebinds, so the row stays stale; this is visible. Separately, the click listener captures `user` in `bind` (`:42-44`), and the payload path (`:98-101`) never re-sets it, so a click passes the old instance. The current callers only read identity (`MyHealthFragment.kt:253`, `BaseDashboardFragment.kt:383`), so this second bug is latent today.

files:
- main: `ui/health/HealthUsersAdapter.kt`
- test: `ui/health/HealthUsersAdapterTest.kt`

steps:
1. Add `firstName` and `lastName` to `areContentsTheSame`, and emit the "name" payload when any of the three changes.
2. Move the click listener to the ViewHolder: in `init`, read `bindingAdapterPosition`, skip `NO_POSITION`, and invoke `clickListener?.invoke(getItem(p))`. Drop the listener parameter from `bind`.
3. Tests: (a) a firstName-only change yields payload `["name"]`; (b) after a payload bind with `u1'`, a click delivers `u1'`. Update the existing spy tests (`:51` and others) for the new `bind` signature.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*HealthUsersAdapterTest"` passes. Renaming a member in Health updates the list without reopening it.

size budget: ~20 main plus ~40 test lines, 2 files.

---

<a id="task-68"></a>

### 68. ServerAddressAdapter: resolve the clicked row at click time, not bind time

rating: 75 (evidence 4 · impact 3 · feasibility 5) · origin: gemini·perf#5 verification note + mega-plan check

context: `onBindViewHolder` captures `position` and `serverAddress` in the click listener (`ui/sync/ServerAddressAdapter.kt:84-94`). `refreshServerList` (`ServerDialogExtensions.kt:130-138`) moves the pinned server to index 0 and inserts rows. DiffUtil moves don't rebind, and the payload path (`:76-77`) doesn't re-set listeners. So a later click compares a stale `position` against `selectedPosition`. That either wrongly shows the **clear-data** dialog (`onClearDataDialog`) or highlights the wrong row via `setSelectedPosition(stalePos)`. The server picker is the first screen of setup, and the dialog offers to wipe local data.

files:
- main: `ui/sync/ServerAddressAdapter.kt`
- test: `ui/sync/ServerAddressAdapterTest.kt`

steps:
1. Set the listener once in the ViewHolder (or in `onCreateViewHolder`). At click time: `val pos = holder.bindingAdapterPosition; if (pos == NO_POSITION) return; val item = getItem(pos)`, then apply the existing branch logic with `pos` and `item`.
2. Test: submit `[A, B]`, then submit `[B, A, C]` and run the looper. Click A's holder and assert `onClearDataDialog(A, 1)` (not position 0).

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*ServerAddressAdapterTest"` passes. Choosing a server after the list reorders never raises a clear-data warning for the server that's already selected.

size budget: ~15 main plus ~30 test lines, 2 files.

out of scope / notes: The test currently pins SDK 34. Leave the pin to task 55.

---

<a id="task-69"></a>

### 69. VoicesLabelManager: close-chip removes the chip's own label, not the first one with the same text

rating: 63 (evidence 4 · impact 2 · feasibility 5) · origin: devin·perf#3 verification (dropped for perf; correctness bug found instead)

context: The close-icon handler does `Constants.LABELS[label] ?: labels.firstOrNull { getLabel(it) == text }` (`services/VoicesLabelManager.kt:82`). `LABELS` is keyed by display name (`Constants.kt:34-36`), but `label` is the stored value, so the lookup is almost always null. The fallback then picks the **first** label with the same display text, which is the wrong one when a post carries both "offer" and "Offer". The closure already has the exact `label`. Separately, `reverseLabels` (`:134`) duplicates `Constants.LABEL_VALUE_TO_NAME` (`Constants.kt:38`).

files:
- main: `services/VoicesLabelManager.kt`
- test: `services/VoicesLabelManagerTest.kt`

steps:
1. Call `removeLabelFn(voiceId, label)` directly with the captured value.
2. Replace `reverseLabels` with `Constants.LABEL_VALUE_TO_NAME`.
3. Test: labels `["Offer", "offer"]`; closing the second chip verifies `removeLabelFn(id, "offer")`. The existing test at `:127` must still pass.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*VoicesLabelManagerTest"` passes.

size budget: ~6 main plus ~20 test lines, 2 files.

---

<a id="task-70"></a>

### 70. make RetryQueueWorker's "already processing" guard atomic

rating: 59 (evidence 4 · impact 2 · feasibility 4) · origin: codex·repo#5 verification (the original premise was dropped; this wart is what's left)

context: `RetryQueueWorker` checks `isCurrentlyProcessing()` (`services/retry/RetryQueueWorker.kt:112`) and later calls `setProcessing(true)` (`:118`), a check-then-set. Runs can overlap: `triggerImmediateRetry` (`:86`) does a plain `enqueue` alongside the unique periodic work. If both runs pass the check, the first one's `finally` (`:171`) clears the flag while the second is still running. `safeClearQueue` (`RetryRepositoryImpl.kt:173-186`) takes a mutex that `setProcessing` never takes, so "Clear retry queue" in Settings can delete rows mid-run. The repository is a `@Singleton`, so the flag is process-wide.

files:
- main: `repository/RetryRepository.kt`, `repository/RetryRepositoryImpl.kt`, `services/retry/RetryQueue.kt` (`:20-24`), `services/retry/RetryQueueWorker.kt`
- test: `repository/RetryRepositoryImplTest.kt`, `services/retry/RetryQueueWorkerTest.kt` (mocks at `:159-303`)

steps:
1. Replace `setProcessing` with `tryStartProcessing(): Boolean = isProcessing.compareAndSet(false, true)` and `finishProcessing()`. Keep `isCurrentlyProcessing()` read-only for `SettingsViewModel:40`.
2. In the worker, `if (!retryQueue.tryStartProcessing()) return Result.success()` before the `try`, and call `finishProcessing()` in `finally`.
3. In `safeClearQueue`: `if (!isProcessing.compareAndSet(false, true)) return false; try { delete; true } finally { isProcessing.set(false) }`, then drop the mutex.
4. Tests: a second `tryStartProcessing()` returns false, and `safeClearQueue` is refused while processing.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Retry*"` passes, and the full suite is green.

size budget: ~30 main plus ~40 test lines, 6 files.

out of scope / notes: Sequence: task 20 → **70** → task 44. All three edit `RetryRepositoryImpl.kt`.

---

<a id="task-71"></a>

### 71. render PDF pages sharp, bounded and on a white background in the resource viewer

rating: 70 (evidence 4 · impact 3 · feasibility 4) · origin: grok·perf#4 verification (its "downscale" premise is wrong; three real defects are underneath)

context: `renderPdf` does `createBitmap(page.width, page.height)` (`ui/viewer/ResourceViewerFragment.kt:525`), one pixel per PDF point, with no cap and no fill. That causes three defects:
- **Blurry text.** A 595-pt A4 page is stretched about 1.8× by `FIT_CENTER` (`:543`) on a 1080-px screen, so text blurs.
- **Crash on large pages.** Large-format pages (maps, posters) can OOM or throw "Canvas: trying to draw too large bitmap".
- **Invisible text in dark theme.** PDFs without a page fill render onto a transparent bitmap, so their text vanishes.

`PdfThumbnailLoader.kt:26-30` already scales to a target width and fills white.

files:
- main: `ui/viewer/ResourceViewerFragment.kt` (`renderPdf` only), `utils/PdfThumbnailLoader.kt` (extract a shared pure function)
- test: new `utils/PdfRenderSizeTest.kt`

steps:
1. Extract a pure `computeRenderSize(pageW, pageH, targetW, maxDim = 2048, maxPixels = 16_000_000): Pair<Int, Int>`.
2. In `renderPdf`, use the container width in px as the target, create the bitmap at the computed size, call `eraseColor(Color.WHITE)`, and render with a scaling `Matrix` (or the `Rect` overload).
3. Use the same helper from `PdfThumbnailLoader`.
4. JVM tests: A4 at 1080 is upscaled, an A0 page is capped, extreme aspect ratios keep both sides at least 1, and a zero width falls back to the page size.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*PdfRenderSizeTest"` passes. A text PDF reads sharply in dark theme, and a poster-size PDF opens without crashing.

size budget: ~35 main plus ~30 test lines, 3 files.

out of scope / notes: Land after task 2 (same file). Don't add `recycle()`; see the PdfThumbnailLoader drop in the rejected list.

---

<a id="task-72"></a>

### 72. let each upload config persist its own "uploaded" state; slim UploadRepository to network

rating: 50 (evidence 3 · impact 2 · feasibility 3) · origin: grok·repo#8, corrected by mega-plan verification (Claude's drop reasons were wrong: `UploadManager` needs no change, no DAO moves into services, and the guest filter stays on `UploadConfig`)

context: `UploadRepositoryImpl.persistUploaded` switches on `modelClass` (`:50-55`) and writes through injected `examDao`/`submissionDao`. It is the only repository that knows other domains' "uploaded" semantics. Configs already carry repository lambdas: `UploadConfigs:66` uses `voicesRepository.markNewsLogUploaded`. `UploadConfig.additionalUpdates` (`UploadConfig.kt:34`, set at `UploadConfigs.kt:268`) is dead: the coordinator never reads it, and `SubmissionDao:45` already clears `isUpdated`.

files:
- main: `services/upload/UploadConfig.kt`, `services/upload/UploadConfigs.kt`, `repository/UploadRepository.kt`, `repository/UploadRepositoryImpl.kt`, `repository/SubmissionsRepository(Impl).kt`, `repository/SurveysRepository(Impl).kt`
- test: `repository/UploadRepositoryImplTest.kt` (`:61-90` move out), `SubmissionsRepositoryImplTest`, `SurveysRepositoryImplTest`, `services/upload/UploadConfigsTest.kt`

steps:
1. Add `markUploaded: suspend (List<UploadedItemResult>) -> List<UploadedItemResult>` to `UploadConfig`, and call it in place of the `modelClass` switch.
2. Move `markExamsUploaded` (`UploadRepositoryImpl.kt:85-107`) into `SurveysRepository`, and the submission branch (`:52-54`) into `SubmissionsRepository`.
3. Wire the lambdas for ExamResults, Submissions and AdoptedSurveys in `UploadConfigs`.
4. Delete `UploadRepository.markUploaded`, `UploadUpdateType`/`Contract`, the `examDao`/`submissionDao` constructor params, and `additionalUpdates`.
5. Move the tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Upload*" --tests "*SubmissionsRepository*" --tests "*SurveysRepository*"` passes, and the full suite is green. Submissions, exam results and adopted surveys still upload once and stop re-uploading.

size budget: ~120 lines, about 10 files. Split it in two if the PR is too large: (a) add the lambda and wire it; (b) delete the old switch.

out of scope / notes: The biggest refactor added by the mega plan. Do it after task 28 (same `SubmissionsRepository`).

---

<a id="task-73"></a>

### 73. delete verified dead code found during verification

rating: 48 (evidence 5 · impact 1 · feasibility 5) · origin: mega-plan sweep

context: Each item has no production reader or caller:
- `"user_admin"` pref: written at `services/sync/LoginSyncManager.kt:152-159`, never read in main or test.
- `AutoSyncWorker.isAppInForeground` (`:159`) plus its "Syncing started…" toast (`:62-65`): `Utilities.toast` needs an Activity context, and the worker passes the application context, so the toast never shows.
- `DownloadUtils.kt:214`: an `SDK_INT < O` branch, but minSdk is 26.
- `ChatViewModel`'s `updatesFor("chats")` subscription (`:70`): nothing ever emits "chats" (only "courses" is emitted), so it is dead. The replay-driven refresh stays; see the rejected chat-history task.
- `MyLibraryDao.getByUnderscoreIds` (`:40`) and `MyLibraryDao.deleteByIds` (`:156`): no callers in main or test.

files: `LoginSyncManager.kt`, `AutoSyncWorker.kt`, `DownloadUtils.kt`, `ChatViewModel.kt`, `MyLibraryDao.kt`, plus whichever tests reference them

steps: Delete each item, then fix any test that referenced it. Do one commit per item.

acceptance: `./gradlew testDefaultDebugUnitTest` passes, and `assembleDefaultDebug` and `assembleLiteDebug` build.

size budget: ~−40 lines, 5 or 6 files.

out of scope / notes: If task 17 lands first, it should already drop `"user_admin"`. `TeamLogDao.getByRemoteIds` is covered by task 27, and `MyLibraryDao.getAll` by task 51.

---

<a id="task-74"></a>

### 74. read community leaders through ConfigurationsRepository everywhere

rating: 43 (evidence 4 · impact 1 · feasibility 4) · origin: claude task 17 follow-up note, located by the mega-plan sweep

context: `ConfigurationsRepository.getCommunityLeaders()` exists (`ConfigurationsRepositoryImpl.kt:414-416`), but four sites read `sharedPrefManager.getCommunityLeaders()` and call `parseLeadersJson` themselves: `ui/voices/VoicesFragment.kt:248`, `ui/teams/voices/TeamsVoicesFragment.kt:257`, `ui/voices/ReplyActivity.kt:152`, and `repository/TeamsRepositoryImpl.kt:965`.

files: the four above, their ViewModels (`VoicesViewModel`/`NewsViewModel`, `ReplyViewModel`), and the tests `TeamsRepositoryImplTest.kt:618` plus the VM tests

steps:
1. Expose `leaders` from the three ViewModels via `ConfigurationsRepository`.
2. In `TeamsRepositoryImpl`, inject `dagger.Lazy<ConfigurationsRepository>` to avoid a Hilt cycle.
3. Delete the fragment-side parsing.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*Voices*" --tests "*Reply*" --tests "*TeamsRepositoryImplTest"` passes. Leader badges still show in Voices, team Voices and replies.

size budget: ~60 lines, about 8 files.

out of scope / notes: Land after task 17.

---

<a id="task-75"></a>

### 75. restore SDK_INT in DownloadService tests, try unpinning them, and trim unused Robolectric jars

rating: 59 (evidence 4 · impact 2 · feasibility 4) · origin: mega-plan sweep (extends tasks 8, 9, 25 and 55)

context:
- `DownloadServiceTest.kt:72`'s tearDown resets the faked `Build.VERSION.SDK_INT` to 0 instead of its original value. That leaks into later tests in the same fork.
- The three DownloadService test classes pin SDK 34 (`UPSIDE_DOWN_CAKE`) but fake `SDK_INT` via `ReflectionHelpers` for R, S and U. The default 36 sandbox likely suffices.
- `robolectricSdkJars` (`app/build.gradle:271-281`) stages jars for 26, 27, 28, 30, 31, 32, 33, 34 and 36. **Nothing pins 30 or 31.** 32, 33 and 34 become removable once tasks 8, 9, 25, 55 and this task land.
- The pin table in `docs/TESTING.md:144-150` misses `MyLibraryDaoTest`, `CoursesItemUtilsTest` and `PersonalsAdapterTest`.

files: `services/DownloadServiceTest.kt`, `DownloadServiceOnDownloadCompleteTest.kt`, `DownloadServiceResumeTest.kt`, `app/build.gradle` (`robolectricSdkJars` only), `docs/TESTING.md`

steps:
1. In each class, save `SDK_INT` in `@Before` and restore it in `@After`.
2. Remove the `sdk = [34]` pins and run the three classes. If they pass, keep the change; otherwise note why in the pin table and keep the pin.
3. Drop keys 30 and 31 from `robolectricSdkJars` now. Drop 32, 33 and 34 once no test pins them (`grep -rn "sdk = \[3[234]\]" app/src/test` is empty).
4. Rewrite the pin table from `grep -rn "@Config(.*sdk" app/src/test`.

acceptance: `./gradlew testDefaultDebugUnitTest -ProbolectricOffline=true` passes, which is how CI runs it.

size budget: ~15 lines, 5 files.

out of scope / notes: Do step 3's second half last in the test-infra wave (after 8, 9, 25, 55).

---

<a id="task-76"></a>

### 76. chunk the survey-list IN lookups

rating: 40 (evidence 3 · impact 1 · feasibility 4) · origin: mega-plan 999-variable sweep

context: `SurveysRepositoryImpl` (`:347-356`) passes every listed survey's id to `SubmissionDao.getByParentIdsAndTeamId` (plus a team bind) and `QuestionDao.getByExamIds`, both unchunked. Only a planet with about 1,000 surveys trips this, so it is hardening.

files: `data/room/dao/SubmissionDao.kt`, `data/room/dao/QuestionDao.kt`, and their DAO tests

steps: Apply the `…Internal` + `chunked(899/900)` shape, and add 1,200-id tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests "*SubmissionDao*" --tests "*QuestionDao*" --tests "*Surveys*"` passes.

size budget: ~20 main plus ~50 test lines, 4 files.

---

## Adjudication of contested drops

The Claude plan dropped 47 of the 120 raw tasks. Devin kept almost all of them (92 of 94), and Codex kept almost all too (108 of 113). Every drop that another plan kept was re-checked against `14da1ba`. The verdicts:

**Performance tasks**

| raw task | kept by | verdict | evidence | result |
|---|---|---|---|---|
| chat-history replay dedupe (claude·perf#5) | Devin 11, Codex 86 | drop holds | Nothing emits "chats"; the replay on STARTED is the only phone refresh (`ChatDetailFragment.kt:668` → `DashboardActivity.kt:734-738` only refreshes when `ChatHistoryFragment` is in the container) | dead subscription removed in 73 |
| PdfThumbnailLoader race + recycle (grok·perf#3) | Devin 47, Codex 85 | drop holds (harmful) | Bitmaps are live in ImageViews (`ResourcesAdapter.kt:398`, `InlineResourceAdapter.kt:239`); `evictAll` at `MainApplication.kt:477` would recycle on-screen bitmaps | — |
| downscale PDF render (grok·perf#4) | Devin 31, Codex 84 | **partial** | The premise is wrong (pages are in points, so the render is usually upscaled), but it has no cap, no white fill and blurs | **task 71** |
| CalendarFragment day index (devin·perf#2) | Devin 18, Codex 75 | drop holds | One O(n) filter per tap; the list is already walked per emission (`:48`) | — |
| VoicesLabelManager label map (devin·perf#3) | Devin 27, Codex 60 | **partial** (not perf) | `LABELS[label]` keys by display name, so the fallback removes the first label with the same text (`:82`) | **task 69** |
| bind-time getString/getColor hoists (devin·perf#6–8) | Devin 53, Codex 58–63 | drop holds | Almost all are formatted strings with arguments; `ChatShareTargetAdapter` already hoists; `ChatDetailFragment:525-537` runs on a tap; `MembersAdapter` already uses lazy labels | — |
| WebView buffer + regex const (devin·perf#10) | Devin 59, Codex 66 | **partial** (not perf) | WebView reads the stream itself; the real bug is regex-replacement interpretation of the password (`UserRepositoryImpl.kt:719`) | **task 64** |
| LifeCache copies (grok·perf#6) | Devin 79, Codex 79 | drop holds | `edit {}` uses apply; the copies are needed because `CachedMyLifeItem` has `var` fields (`:13-18`) | — |
| resolveType keyword map (grok·perf#7) | Devin 77, Codex 68 | drop holds | A handful of `contains` per notification | — |
| click listeners in bind, 8 adapters (gemini·perf#2–10) | Devin 74, Codex 43–48 | drop holds for perf; **two correctness bugs** | Stale captured user and a diff that ignores name edits in `HealthUsersAdapter`; stale position in `ServerAddressAdapter:86-93`. The other adapters already read `bindingAdapterPosition` | **tasks 67, 68** |
| row_survey / item_library_grid flatten (kimi·perf#1–2) | Devin 64/67, Codex 55/57 | drop holds | The weighted children are 0dp in an EXACTLY-measured row; the grid saving is one FrameLayout per cell. Open UX question: an opened+offline resource shows an empty badge circle (`ResourcesAdapter.kt:466`) | ask design |
| Sha256Utils → object (gemini·perf#7) | Devin 78, Codex 35 | drop holds | One call site; breaks three `mockkConstructor` tests (`ConfigurationsRepositoryImplTest.kt:497/528/560`) | — |
| parallelise join-request lookups (kimi·perf#8) | Devin 39, Codex 81 | drop holds | Already inside an `async` with three siblings (`:196-218`) | — |
| lowercase correctChoices at write (devin·perf#5) | Devin 61, Codex 69 | drop holds (harmful) | `correctChoiceList` is written directly in mixed case (`ExamQuestion.kt:114`, `CoursesRepositoryImpl.kt:800`), so grading would break | — |
| CrashLogStore cap / tests (kimi·perf#4, #10) | Devin 29/83, Codex 83 | drop holds | `MAX_PENDING_FILES = 20` exists (`:17`); `CrashLogStoreTest.kt` exists | — |
| Glide `.override()` (grok·perf#1–2) | Devin 43/44, Codex 72/73 | drop holds | Fixed 64/120 dp views; Glide already decodes at view size | — |

**Boundary tasks**

| raw task | kept by | verdict | evidence | result |
|---|---|---|---|---|
| My Life visibility re-read (claude·repo#9) | Devin **#1 (85)**, Codex 80 | drop holds (unreachable) | Every seeded row gets a UUID `_id` (`LifeRepositoryImpl.kt:131`); the imageId/title fallback never matches | — |
| SyncViewModel for ProcessUserDataActivity (devin·repo#10) | Devin 48, Codex 88 | drop holds | `ProcessUserDataActivity` is the root of every activity; `SyncActivity`, `LoginActivity` and `BecomeMemberActivity` use its fields directly | — |
| UploadRepository network-only (grok·repo#8) | Devin 73, Codex 89 | **partial**: the drop reasons were wrong | `UploadCoordinator` takes `UploadConfig`, so `UploadManager` needs no change; configs already call repositories; the guest filter stays on `UploadConfig:41-44` | **task 72** |
| retry flag from DAO (codex·repo#5) | Devin 84, Codex 90 | **partial** | The premise fails, but there's a check-then-set race at `RetryQueueWorker.kt:112/118` | **task 70** |
| Retry: remove `Log` (grok·repo#9) | Devin 49, Codex 61 | drop holds | `CODE_STYLE_GUIDE` "Logging" prescribes `Log.*` | — |
| Diagnostics without SharedPrefManager (grok·repo#3) | Devin 76, Codex 84 | drop holds (cosmetic) | `getParentCode()` just returns the pref | — |
| shelf-cache normalise (codex·repo#6) | Devin 68, Codex 82 | drop holds | `_all_docs` ids are unique; clear-data wipes the cache on server switch | — |
| dedupe upload successes (codex·repo#9) | Devin 87, Codex 77 | drop holds | One result per prepared item (`UploadCoordinator:124-265`) | — |
| AnswerDao hardening (codex·repo#10) | Devin 86, Codex 83 | drop holds | Already chunked at 900, with an empty short-circuit and 1,200-id tests | — |
| CoursesSteps download selection (codex·repo#8) | Devin 62, Codex 79 | drop holds | One duplicated filter predicate (`:82/:97`) | — |
| OfflineActivityDao consolidate (kimi·repo#5) | Devin 82, dropped by Codex | drop holds (would regress) | Different indexed columns, both used | — |
| NewsDao paged feed (kimi·repo#7) | dropped by Devin, Codex 81 | drop holds | The feed filters and re-sorts in Kotlin by `calculateSortDate()`; SQL paging returns wrong pages | — |
| CourseProgressDao parameterise (kimi·repo#9) | Devin 88, Codex 84 | drop holds | Already bound with `?`, 250×3 < 999, and a cross-product test | — |
| MyLifeDao / PersonalDao predicates (gemini·repo#6–7) | Devin 89/90, Codex 57/55 | drop holds (harmful) | NULL-userId seeding; blank means "any user" for the duplicate-title check | — |
| DeviceNameProviderTest (gemini·repo#10) | Devin 75, Codex 64 | drop holds | Pure delegation; `NetworkUtilsTest` covers it | — |
| ReplyViewModel (kimi·repo#10) | Devin 66, dropped by Codex | drop holds (already done) | `ReplyViewModel.kt` exists; `ReplyActivity.kt:52` uses it | — |
| MeetupDao projection / Dictionary composition (kimi·repo#6, #8) | Devin 91/92, dropped by Codex | drop holds (no concrete target) | — | — |

**Corrections the Claude plan made to tasks it kept, re-checked:** 46 (use `TeamsNotificationsRepository`, not a new port) holds; 47 (reuse the Events copy, but throw) holds; 11 (`getUserByName` is the same query) holds. The verified details are in each task's mega-plan notes.

## Rejected (not in this plan)

These stay out: their premise fails, they're harmful, or they're already done. Each row gives a one-line reason; the full reasons are in the Claude plan's `docs/refactor_tasks_scoring.md` and the tables above.

| source | task | reason |
|---|---|---|
| claude·perf#5 | chat-history generation counter | would stop the only phone refresh |
| claude·repo#9 | My Life visibility re-read | unreachable (UUID ids) |
| devin·perf#2 | CalendarFragment day index | no measurable win |
| devin·perf#5 | lowercase correct choices at write | breaks grading |
| devin·perf#6/7/8 | bind-time string/color hoists | negligible; locale hazard |
| devin·repo#10 | SyncViewModel for ProcessUserDataActivity | infeasible (base class of every activity) |
| kimi·perf#1/2 | flatten row_survey / item_library_grid | no measure-pass win; badge behaviour change |
| kimi·perf#4/10 | CrashLogStore cap and tests | already capped and tested |
| kimi·perf#8 | parallelise join-request lookups | already concurrent |
| kimi·repo#5 | consolidate OfflineActivityDao pairs | defeats indexes |
| kimi·repo#6/8 | MeetupDao projection; Dictionary composition | no concrete target |
| kimi·repo#7 | NewsDao paged feed | wrong pages (Kotlin-side sort) |
| kimi·repo#9 | CourseProgressDao parameterise | already parameterised, guarded |
| kimi·repo#10 | ReplyViewModel | already exists |
| grok·perf#1/2 | Glide `.override()` on finance/report thumbs | already view-sized |
| grok·perf#3 | PdfThumbnailLoader recycle on evict | would crash on-screen bitmaps |
| grok·perf#6 | LifeCache copies | copies needed; write already async |
| grok·perf#7 | resolveType keyword map | not a hotspot |
| grok·repo#3 | Diagnostics without SharedPrefManager | cosmetic |
| grok·repo#9 | Retry: remove `Log` | contradicts the style guide |
| gemini·perf#2–5, 8–10 | click listeners out of bind (7 adapters) | negligible; real bugs split out as 67/68 |
| gemini·perf#7 | Sha256Utils → object | breaks tests, one call site |
| gemini·repo#6/7 | MyLifeDao / PersonalDao predicates | harmful |
| gemini·repo#10 | DeviceNameProviderTest | nothing to test |
| codex·repo#6 | normalise shelf cache | unreachable state |
| codex·repo#8 | CoursesSteps download selection | nothing to hoist |
| codex·repo#9 | dedupe upload successes | pipeline never duplicates |
| codex·repo#10 | AnswerDao hardening | already done and tested |

Salvaged instead of rejected: grok·perf#4 → 71, devin·perf#3 → 69, devin·perf#10 → 64, gemini·perf#5/#6 → 68/67, grok·repo#8 → 72, codex·repo#5 → 70.

## How this was verified

- **Code reads only.** Everything was checked by reading code at `14da1ba` with grep; nothing was compiled or run, because there's no Android SDK in the verification container. Each new task's premise was confirmed by at least one direct read. The five highest-impact new findings (61, 62, 63, 64, 68) were re-read independently.
- **Open-PR locks** come from each open PR's full changed-file list, fetched from GitHub on 2026-09-24. Activity uses `updated_at`.
- **Re-scoring** only restores the stale-PR feasibility point the Claude plan had applied. For tasks 34 and 35, the only stale PRs involved (13848, 13355) weren't in the Claude plan's PR list, so no point had been deducted and none is restored.
