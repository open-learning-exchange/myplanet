# myPlanet refactor round — work orders (performance quick wins)

Generated 2026-09-24. Each task is a self-contained work order for a coding agent; tasks are independently mergeable in any order.

## Header

**Open PRs checked (31 total)** — files they touch are excluded from all tasks below:

- **#17435** `ResourcesFragment.kt`
- **#17430** `MainApplication.kt`, `TeamDetailFragment.kt`, `TeamPagerAdapter.kt`
- **#17356** `ui/surveys/PublicSurvey{Activity,ViewModel,PayloadBuilder}.kt`
- **#17254** `ui/resources/ResourceCardHelper.kt`, `ui/resources/ResourcesAdapter.kt`, `ui/teams/resources/TeamResources{Adapter,Fragment}.kt`, `row_team_resource.xml`
- **#17187** `ResourcesFilterFragment.kt`, `ResourcesListFilter.kt`, `ResourcesViewModel.kt`, `utils/FileUtils.kt`, `utils/LibraryTypeClassifier.kt`, `utils/MediumUtils.kt`
- **#16624** `CoursesRepository{,Impl}.kt`, `utils/ResourcesSearchUtils.kt`
- **#16623** `callback/OnTaskCompletedListener.kt`, `model/TeamTask.kt`, `TeamsRepository{,Impl}.kt`, `ui/teams/tasks/*`, `row_task.xml`, `fragment_teams_tasks.xml`, `values*/strings.xml`
- **#16594** `.github/scripts/labels.*`, `.github/workflows/{labels,scripts}.yml`
- **#15951** `AppDatabase.kt`, `model/{MyTeam,CreateTeamRequest,TeamDetails,TeamUpdateRequest}.kt`, `TeamsRepository{,Impl}.kt`, `ui/teams/{PlanFragment,TeamFragment,TeamViewModel,TeamsAdapter}.kt`, `utils/TimeUtils.kt`, `fragment_{plan,team}.xml`, `item_team_list.xml`, `alert_create_team.xml`, `values*/strings.xml`
- **#15825** `AndroidManifest.xml`, `MainApplication.kt`, `AppDatabase.kt`, `MeetupDao.kt`, `RepositoryModule.kt`, `EventsRepositoryImpl.kt`, `TeamsRepository{,Impl}.kt`, `TaskNotificationWorker.kt`, `services/reminders/*`, `DashboardActivity.kt`, `ui/teams/tasks/*`, `utils/{ActivityTracker,InAppNotificationHelper,NotificationUtils}.kt`, `values/strings.xml`, `xml/pref.xml`
- **#15824** `app/build.gradle`, `dao/{CourseProgressDao,NewsDao,OfflineActivityDao,SubmissionDao,TeamTaskDao}.kt`, `RepositoryModule.kt`, `model/gamification/*`, `GamificationRepository{,Impl}.kt`, `ui/user/{AchievementFragment,CertificateDialogFragment,CourseCertificatesAdapter,GamificationBadgesAdapter,GamificationViewModel}.kt`, assorted res
- **#15820** `NewsDao.kt`, `EventsRepository{,Impl}.kt`, `TeamsRepository{,Impl}.kt`, `ui/events/EventsAdapter.kt`, `ui/teams/{InlineCommentsAdapter,TeamCalendarFragment,TeamCalendarViewModel}.kt`, `ui/teams/tasks/*`, `row_task.xml`, `item_meetup.xml`
- **#15808** `AppDatabase.kt`, `RoomModule.kt`, `ServiceModule.kt`, `model/SyncCursor.kt`, `dao/SyncCursorDao.kt`, sync-writer interfaces + `{Chat,Community,Courses,Feedback,Health,Progress,Ratings}Repository{,Impl}.kt`, several DAOs
- **#15559** `ui/exam/ExamTakingFragment.kt`, `fragment_exam_taking.xml`, `values*/strings.xml`, exam-related res
- **#15267** `base/BaseRecyclerFragment.kt`, `base/BaseResourceFragment.kt`, `ui/resources/Resources{Adapter,Fragment}.kt`, `fragment_my_library.xml` (+land/sw600dp), `my_library_alertdialog.xml`, `values/styles.xml`
- **#15266** `fragment_calendar.xml`, `fragment_enterprise_calendar.xml`
- **#15226** flutter port (no `app/` Kotlin overlap)
- **#15108** `model/Meetup.kt`, `model/MeetupCreationParams.kt`, `EventsRepository{,Impl}.kt`, `ui/events/EventsDetail{Fragment,ViewModel}.kt`, `ui/teams/TeamCalendar{Fragment,ViewModel}.kt`, `add_meetup.xml`, `values/strings.xml`
- **#14883** `model/TeamLeaderboardEntry.kt`, `ProgressRepositoryImpl.kt`, `SurveysRepository{,Impl}.kt`, `ui/teams/{TeamDetailFragment,TeamPageConfig}.kt`, `ui/teams/leaderboard/*`, leaderboard res
- **#14650** `base/BaseDashboardFragment.kt`, `model/AssignedSurvey.kt`, `SurveysRepository{,Impl}.kt`, `ui/dashboard/DashboardViewModel.kt`, `ui/exam/ExamTakingFragment.kt`, `ui/submissions/{SubmissionUiModel,SubmissionViewModel,SubmissionsAdapter}.kt`
- **#14427** `ActivitiesRepository{,Impl}.kt`, `ProgressRepositoryImpl.kt`, `SubmissionsRepositoryImpl.kt`, `ui/courses/Courses{Adapter,ViewModel}.kt`, `ui/dashboard/BellDashboard{Fragment,ViewModel}.kt`, `utils/StreakUtils.kt`, `card_profile_bell.xml`, `values*/strings.xml`
- **#13928** `app/build.gradle`, `build.gradle.kts`, `settings.gradle`, `gradle/libs.versions.toml`, `baselineprofile/*`
- **#13848** `model/{CourseLevel,GradeLevel}.kt`, `ui/courses/CourseFilterController.kt`, `ui/exam/UserInformationFragment.kt`, `ui/resources/AddResourceActivity.kt`, `ui/user/{BecomeMemberActivity,UserProfileFragment}.kt`, `values*/strings.xml`
- **#13657** `model/{Course,RealmMyCourse}.kt`, `CoursesRepository{,Impl}.kt`, `ui/courses/{CourseFilterController,CourseSelectionController,CoursesFragment,CoursesViewModel}.kt`, `fragment_my_course.xml`, `values*/strings.xml`
- **#13604** `ui/surveys/{SurveyFragment,SurveysViewModel}.kt`, `values*/strings.xml`
- **#13415** `base/BaseVoicesFragment.kt`, `data/DatabaseService.kt`, `data/RealmMigrations.kt`, `model/RealmNews.kt`, `VoicesRepository{,Impl}.kt`, `ui/voices/{VoicesAdapter,VoicesFragment}.kt`, `row_news.xml`, `values*/strings.xml`
- **#13355** `AndroidManifest.xml`, `callback/OnLibraryItemSelectedListener.kt`, `services/P2pTransferManager.kt`, `ui/resources/{P2pTransferActivity,ResourceDetailFragment,ResourcesAdapter,ResourcesFragment}.kt`, `row_library.xml`, `values*/strings.xml`
- **#13287** `activity_become_member.xml`, `edit_profile_dialog.xml`
- **#10993** `base/BaseVoicesFragment.kt`, `callback/OnNewsItemClickListener.kt`, `model/News.kt`, `VoicesRepository{,Impl}.kt`, `services/UploadManager.kt`, `ui/teams/voices/*`, `ui/voices/{ReplyActivity,VoicesAdapter,VoicesFragment,VoicesViewModel}.kt`, `row_news.xml`, `values*/strings.xml`
- **#8175**, **#4075** `ci/robo/*` only

**Consequences for the plan:** the whole `ui/resources/`, `ui/voices/` (incl. `VoicesAdapter.kt`, `ReplyActivity.kt`), `ui/teams/`, `ui/courses/`, `ui/submissions/` (VM/adapter side), `ui/surveys/`, `ui/exam/`, `ui/events/`, most base fragments, all `values*/strings.xml`, `AddResourceActivity.kt`, `MainApplication.kt`, and the sync/upload services are off-limits this round. All 10 tasks below avoid these files entirely. Roadmap-fit note: because the focus block was "performance quick wins", tasks serve **roadmap #7** (optimize remaining performance hotspots) with #8 (code health/tests) as supporting; several explicitly note how they unblock #2/#9/#10.

---

## Task 1 — Flatten `row_survey.xml` list-row hierarchy

**Roadmap served:** #7 (performance). Also moves #6/#10 forward: list rows with flat ConstraintLayout hierarchies map 1:1 to Compose `Row`/`Column` slots, making the eventual Compose row migration a mechanical port with no layout-behavior surprises.

**Files (1):** `app/src/main/res/layout/row_survey.xml`

**Problem:** The survey row inflated for every item in `SurveyFragment`'s RecyclerView nests 4 LinearLayouts deep (CardView → vertical LinearLayout → two horizontal LinearLayouts → a weighted vertical LinearLayout holding `tv_description`/`tv_no_submissions`/`tv_date`, plus two Buttons). LinearLayout with `layout_weight` forces double measurement of children on every bind/scroll.

**Work:**
1. Replace the root's inner content with a single `ConstraintLayout` (the CardView stays as root, including `app:cardUseCompatPadding` and `app:contentPadding`).
2. Place `tv_title`, `tv_date_completed`, `tv_description`, `tv_no_submissions`, `tv_date`, `send_survey`, `start_survey` directly in the ConstraintLayout. Reproduce current visual behavior exactly: title takes remaining horizontal space (0dp constrained between start and `tv_date_completed`), the three info TextViews stack vertically with visibility `gone` respected for `tv_description` (ConstraintLayout handles gone views via `app:layout_goneMargin*` where needed), the two buttons align to the end.
3. Preserve every existing view **id** unchanged — `SurveyFragment`/`SurveyAdapter` reference these ids through view binding; no Kotlin file may need editing.
4. Preserve all text colors, sizes, paddings, and the `@style/AccentButton` button themes.

**Verification:** `./gradlew assembleDefaultDebug`; open Survey screen, confirm rows render identically (title, date, description toggle, both buttons) and scroll; run existing `app/src/test` survey tests (`./gradlew testDefaultDebugUnitTest --tests "*Survey*"`).

---

## Task 2 — Flatten `item_library_grid.xml` badge FrameLayout chain

**Roadmap served:** #7 (performance). Also moves #6/#10 forward: the library grid card is a prime first Compose-screen candidate; a flat layout documents the intended visual layering without nested Android view-group semantics.

**Files (1):** `app/src/main/res/layout/item_library_grid.xml`

**Problem:** Inside `cover_container` (FrameLayout), the downloaded-badge is a nested `FrameLayout` (24dp, `bg_badge_circle` background) wrapping a 14dp `ImageView` (`iv_downloaded`). The inner FrameLayout exists only to center the icon over the circle background — a second measure/layout pass per badge per bind on one of the most-inflated layouts in the app.

**Work:**
1. Delete the inner `FrameLayout` (lines 42–56). Give `iv_downloaded` the badge FrameLayout's own attributes directly: `android:layout_width="24dp"`, `android:layout_height="24dp"`, `android:layout_gravity="top|end"`, `android:layout_margin="6dp"`, `android:background="@drawable/bg_badge_circle"`, plus `android:scaleType="centerInside"` and `android:padding="5dp"` so the 14dp icon content is centered inside the 24dp circle.
2. Keep all other views (`iv_cover_preview`, `iv_type_icon`, `checkbox`) and every id untouched. Do not change the outer vertical LinearLayout or the text section (that would exceed this task's blast radius).
3. Verify visually that the badge circle and checkmark render identically in both downloaded and non-downloaded states.

**Verification:** `./gradlew assembleDefaultDebug`; open the library in grid view on a device/emulator; toggle a resource between downloaded/not-downloaded and confirm the badge looks identical. Confirm no Kotlin references break (view binding regenerates cleanly since the id survives).

---

## Task 3 — Cap the memory spike in `ResourceViewerFragment.setupTextViewer`

**Roadmap served:** #7 (performance — OOM/jank risk on large text resources). Also moves #9 forward: the size-cap read policy is pure file-I/O logic that belongs in the platform-free core; implementing it now in one place means the KMP extraction later moves a finished algorithm, not Android-specific TODO behavior.

**Files (1):** `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`

**Problem:** `setupTextViewer()` (around line 587–616) calls `file.readText()` inside `withContext(dispatcherProvider.io)`, which materializes the **entire** file as a String before the `MAX_TEXT_VIEWER_CHARS` (500_000, line 723) truncation check runs. A 50 MB text/HTML resource allocates ~100 MB of heap (UTF-16) on the spot, then throws 99% of it away.

**Work:**
1. Replace the read-then-truncate logic with a bounded read: when `file.length()` (bytes) is greater than a conservative byte budget (e.g. `MAX_TEXT_VIEWER_CHARS * 4L` to cover worst-case UTF-8 expansion), read at most that many bytes via a bounded reader (e.g. read into a `CharArray(MAX_TEXT_VIEWER_CHARS + 1)`), otherwise keep `readText()`.
2. Determine `truncated` from the same condition (`file.length() > budget` or the bounded read filled the buffer), preserving the existing toast on `R.string.text_content_truncated`.
3. Keep the existing `withContext(dispatcherProvider.io)` wrapper, the `!file.exists()` early return, the `isAdded` guard, and the MARKDOWN vs plain-text rendering paths unchanged.
4. No new constants beyond the byte budget; no behavior change for files under the cap.

**Verification:** `./gradlew testDefaultDebugUnitTest --tests "*ResourceViewer*"`; build debug APK; open a small text resource (renders fully, no toast) and a >500k-char resource (truncated toast shows, content matches previous behavior's first 500k chars).

---

## Task 4 — Cap per-file memory in `CrashLogStore.loadPendingLogs`

**Roadmap served:** #7 (performance — startup memory) and #8 (code health — a silent OOM during crash-report upload defeats the crash reporter).

**Files (1):** `app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt`

**Problem:** `loadPendingLogs()` (lines 54–65) runs `file.readText()` per pending crash-log file inside a `mapNotNull` loop with no size limit. Several large stacked stacktraces/logs read at app startup can allocate many MB at once — exactly when the app just crashed and may be memory-stressed.

**Work:**
1. In the `try` block, cap the loaded content: read at most a fixed number of characters (e.g. 200_000) per file — e.g. via `file.bufferedReader().use { reader -> val buf = CharArray(MAX); val n = reader.read(buf); String(buf, 0, n) }` or equivalent.
2. Add a single private companion constant (e.g. `MAX_LOG_CHARS = 200_000`) in the existing companion object next to `TAG`.
3. Keep the existing exception handling (`Log.e(TAG, "failed to read pending crash log", e)` → skip file) and the `parseLogFile` gate exactly as-is; the `PendingLog` shape does not change.
4. Truncation must be silent (crash logs are best-effort diagnostics); do not add UI surfacing.
5. Test note: do not create a new test file here — `CrashLogStoreTest.kt` is owned by Task 10 (keeps tasks file-disjoint). If that file already exists on your base, you may add one case covering the cap; otherwise skip tests.

**Verification:** `./gradlew testDefaultDebugUnitTest --tests "*CrashLog*"`; build and confirm app starts normally with pending crash logs present.

---

## Task 5 — `setHasFixedSize(true)` on stable list RecyclerViews, batch A

**Roadmap served:** #7 (performance — skips redundant measure/layout passes on every bind).

**Files (3):**
- `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt` (line 35, `binding.rvMypersonal`)
- `app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesFragment.kt` (line 29, `binding.rvReferences` — fully static 2-item list)
- `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt` (line 47, `binding.rvFeedback`)

**Problem:** These three RecyclerViews get a `LinearLayoutManager`/`GridLayoutManager` but never `setHasFixedSize(true)` — the project already applies it in `ChatHistoryFragment.kt:172`, `BellDashboardFragment.kt:239`, `SurveyFragment.kt:115`, `LifeFragment.kt:77` (house precedent). Without it, every adapter update triggers a full layout pass because the RecyclerView can't assume its own size is content-independent.

**Work:**
1. In each fragment, immediately after the existing `layoutManager = …` assignment, add `setHasFixedSize(true)` on the same RecyclerView.
2. Precondition the implementer must confirm while editing: the RecyclerView's XML declares `android:layout_height="match_parent"` or a fixed dimension on these three views — if any is `wrap_content`, skip that view and note it in the PR description instead of applying the flag (fixed-size is only valid when size doesn't depend on content).
3. No other changes; adapters (`PersonalsAdapter`, `ReferencesAdapter`, `FeedbackAdapter`) are ListAdapters with row layouts of uniform height.

**Verification:** `./gradlew assembleDefaultDebug`; navigate to Personals, References (dashboard references), and Feedback lists; confirm rows render and scroll correctly with no layout gaps. Run existing fragment tests if present (`./gradlew testDefaultDebugUnitTest --tests "*Personals*" --tests "*References*" --tests "*Feedback*"`).

---

## Task 6 — `setHasFixedSize(true)` on stable list RecyclerViews, batch B

**Roadmap served:** #7 (performance). Kept as a separate task from Task 5 so either can merge independently.

**Files (3):**
- `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt` (line 65, `binding.rvNotifications`)
- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressFragment.kt` (line 20, `binding.rvMyprogress`)
- `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt` (line 120, `binding.rvReports`)

**Problem:** Same as Task 5 — ListAdapter-backed lists without the fixed-size hint.

**Work:**
1. Add `setHasFixedSize(true)` directly after each existing `layoutManager` assignment.
2. Same precondition check as Task 5: confirm `match_parent`/fixed height in the corresponding layout XML before applying; skip-and-note any `wrap_content` case.
3. No other edits. Do not touch `EnterprisesFinancesFragment.kt` or any other file — out of scope.

**Verification:** `./gradlew assembleDefaultDebug`; open Notifications, Course Progress, and Enterprise Reports screens; confirm lists render/scroll identically. Run `./gradlew testDefaultDebugUnitTest --tests "*Notification*" --tests "*Progress*"` for existing coverage.

---

## Task 7 — View Binding for `OnboardingAdapter`

**Roadmap served:** #7 (performance — kills 3 `findViewById` tree traversals per page inflation) and #8 (code health — project convention is View Binding everywhere; CLAUDE.md lists View Binding as enabled and expected). Also moves #6/#10 forward: a binding-based pager page has its view references explicit, which is the exact shape a Compose onboarding page will replicate.

**Files (1):** `app/src/main/java/org/ole/planet/myplanet/ui/onboarding/OnboardingAdapter.kt`

**Problem:** `instantiateItem()` (lines 23–38) inflates `R.layout.onboarding_item` and then performs three `findViewById` lookups (`iv_onboard`, `tv_header`, `tv_desc`) on every page creation. The layout (`app/src/main/res/layout/onboarding_item.xml`) is stable and view-binding-ready.

**Work:**
1. Replace the manual inflation + three `findViewById` calls with the generated binding: `val binding = OnboardingItemBinding.inflate(LayoutInflater.from(mContext), container, false)` and address views as `binding.ivOnboard`, `binding.tvHeader`, `binding.tvDesc`.
2. `container.addView(binding.root)`; return `binding.root` from `instantiateItem`.
3. Remove the now-unused imports (`android.widget.ImageView`, `android.widget.TextView`, `org.ole.planet.myplanet.R` if unused) — keep imports sorted per project style.
4. Keep `destroyItem`, `getCount`, `isViewFromObject`, and both `setTextColor` calls byte-for-byte equivalent in behavior.

**Verification:** `./gradlew assembleDefaultDebug` (view binding generation will fail the build if an id is wrong — that is the intended static check); fresh-install and swipe through all onboarding pages; confirm images, titles, and descriptions render with correct text colors in both light and dark theme.

---

## Task 8 — Parallelize the two independent batch lookups in `NotificationsRepositoryImpl.getJoinRequestDetailsBatch`

**Roadmap served:** #7 (performance — notification list loads) and #5 (sync/upload consolidation — this is a data-assembly seam on the notification pipeline; making its concurrency explicit is a precondition for moving the pipeline into a use-case). Also moves #9 forward: `coroutineScope { async … await … }` is pure kotlinx.coroutines with no `android.*` imports — exactly the shape the platform-free core needs.

**Files (1):** `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`

**Problem:** `getJoinRequestDetailsBatch()` (lines 302–340) performs three suspension points strictly sequentially: `getJoinRequestsInfo(relatedIds)` → `getTeamNamesByIds(teamIds)` → `getUsersByIds(userIds)`. The userIds set, however, is derivable from `joinRequests` alone (each `jr.userId`) — it does **not** depend on `teamMap`. So the team-name lookup and the user-name lookup are independent and can run concurrently. The file already imports and uses `kotlinx.coroutines.async` and `kotlinx.coroutines.coroutineScope` (lines 12–13, used at line 196), so the pattern is established in-file.

**Work:**
1. Compute `userIds` from `joinRequests` directly (before/independent of `teamMap`): collect non-empty `jr.userId` into a `LinkedHashSet`, exactly mirroring the current filtering semantics.
2. Wrap the `getTeamNamesByIds` and `getUsersByIds` calls in `coroutineScope { val teamDeferred = async { … }; val userDeferred = async { … }; … }` and `await()` both before building `intermediateList`/`map`. Preserve the `userIds.isNotEmpty()` guard and the `"Unknown Team"`/`"Unknown User"` fallbacks exactly.
3. Rebuild `intermediateList` after the awaits using the awaited `teamMap`, and fill `map` as today.
4. No signature changes, no new imports beyond what the file already has, no new dispatcher — the repository's existing dispatcher discipline is untouched.

**Verification:** `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepository*"` — rely on existing tests to prove identical output maps. Build and open the Notifications screen with join-request notifications present; confirm team/user names resolve correctly.

---

## Task 9 — Test coverage for `NotificationsRepositoryImpl.getJoinRequestDetailsBatch` batching behavior

**Roadmap served:** #8 (improve code health and add tests). Also moves #3 forward: pinning the notification detail-assembly contract in tests is what makes it safe to extract into a use-case class later. (Independent of Task 8: these tests pass against current sequential code too.)

**Files (1):** `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt` (extend the existing test file — do not create a parallel one)

**Problem:** `getJoinRequestDetailsBatch` is a private function reached through the repository's public notification-list API; its join-request → (userName, teamName) mapping — including the `"Unknown Team"` / `"Unknown User"` fallbacks and the empty-`userId` path (lines 313–337 of `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`) — currently has no direct behavioral pin, so concurrency or fallback regressions would ship silently.

**Work:**
1. Following the file's existing MockK + `runTest` + `MainDispatcherRule`/`TestDispatcherProvider` setup, add tests driving the public entry point that internally invokes `getJoinRequestDetailsBatch` (identify it from the existing test file's patterns).
2. Cases: (a) join request with known team and known user → maps to real names; (b) unknown team id → `"Unknown Team"`; (c) empty `userId` on the join request → `"Unknown User"` without querying `userRepository.getUsersByIds` (verify with MockK `coVerify(exactly = 0)` or a stubbed empty list); (d) empty `relatedIds` input short-circuits without touching teams/user repositories.
3. No production-code changes in this task. Under 150 lines added.

**Verification:** `./gradlew testDefaultDebugUnitTest --tests "*NotificationsRepositoryImplTest*"` passes, including the new cases.

---

## Task 10 — Test coverage for `CrashLogStore` parse-and-load contract

**Roadmap served:** #8 (improve code health and add tests). Also serves #7 indirectly: it pins `loadPendingLogs` behavior so Task 4's memory cap (or any future batching change) can't silently alter which logs load.

**Files (1):** `app/src/test/java/org/ole/planet/myplanet/utils/CrashLogStoreTest.kt` (new — only if it does not already exist; if it exists, extend that file instead and do not create a new one)

**Problem:** `CrashLogStore.loadPendingLogs()` (`app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt`, lines 54–65) decides which persisted crash logs get uploaded: files failing `parseLogFile` are skipped, IO failures are logged and skipped, valid files become `PendingLog(file, type, time, content)`. None of this is test-pinned, yet it guards crash-report delivery.

**Work:**
1. Create a Robolectric/JUnit4 test (matching the house stack: JUnit4, MockK, `runTest` if the store is suspending) using a temp directory (`@Rule TemporaryFolder` or Robolectric's sandboxed filesystem) as the crash-log directory.
2. Cases: (a) a well-formed log file loads into a `PendingLog` with parsed type/time and full content; (b) a file whose name/content fails `parseLogFile` is excluded from the result; (c) an unreadable/garbage file is skipped without throwing, remaining files still load; (d) empty directory returns an empty list.
3. Read `CrashLogStore.kt` first to match its actual constructor/directory-injection seam; if the directory is hard-wired, use Robolectric's filesystem shadowing as other util tests in `app/src/test` do. No production-code changes.

**Verification:** `./gradlew testDefaultDebugUnitTest --tests "*CrashLogStoreTest*"` passes.

---

## Self-check

- **R1** — 10 tasks, each independently mergeable: ✓ (no task depends on another's output; Tasks 9/10 pin behavior but don't require Tasks 8/4).
- **R2** — no file in two tasks: ✓ (all task file sets are disjoint; Task 4's test instruction explicitly defers to Task 10's file ownership).
- **R3** — all 31 open PRs listed above; every task file cross-checked against their file lists: ✓ no collisions (notably avoided: `ui/voices/*`, `ui/resources/*`, `ui/teams/*`, `base/Base*`, `AddResourceActivity.kt`, `row_news.xml`).
- **R4** — every cited path/class/function/line opened and confirmed: ✓.
- **R5** — each task ≤ ~50 changed lines, ≤ 3 files, no new dependencies, no TODOs: ✓.
- **R6** — no implementation code written; plan only: ✓.
