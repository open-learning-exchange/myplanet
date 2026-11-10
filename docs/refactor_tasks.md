# myPlanet merged refactor backlog — 2026-11-10 (base 2093a62)

113 merged tasks from 180 raw (18 lists), sorted by rating. Scoring, provenance and the drop log are in `refactor_tasks_scoring.md`.

---

### 1. Warm a master debug build cache so first pushes of new branches stop cold-compiling

**rating 78** (evidence 0.90 · impact 4/5 · risk M) · **proposed by 1 agent(s):** Copilot·Grok (L14-T1)

**credit:** L14-T1 1.00

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

### 2. DashboardViewModel.evaluateChallengeDialog: evaluate the (expired) date window before launching its five queries

**rating 76** (evidence 0.80 · impact 4/5 · risk M) · **proposed by 2 agent(s):** Copilot·Grok (L02-T1); Copilot·Kimi (L03-T1)

**credit:** L02-T1 0.50, L03-T1 0.50

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

### 3. SubmissionsPayloadBuilder.submissionPayload: rethrow CancellationException instead of returning a partial payload

**rating 71** (evidence 1.00 · impact 3/5 · risk L) · **proposed by 3 agent(s):** Codex (L10-T4); Copilot·Kimi (L15-T8); Devin (L13-T1)

**credit:** L10-T4 0.48, L15-T8 0.48, L13-T1 0.04

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

### 4. DictionaryRepositoryImpl.insertDictionaryData: check the DB count before the seed-file existence

**rating 71** (evidence 1.00 · impact 3/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L12-T4); Codex (L10-T5)

**credit:** L10-T5 0.64, L12-T4 0.36

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

### 5. CoursesRepositoryImpl: read titles/ids from courseDao instead of mapCourse (which loads all steps)

**rating 71** (evidence 1.00 · impact 3/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L05-T5)

**credit:** L05-T5 1.00

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

### 6. ProgressRepositoryImpl.getCourseProgress: count course steps in SQL instead of loading step bodies

**rating 71** (evidence 1.00 · impact 3/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L05-T4)

**credit:** L05-T4 1.00

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

### 7. SurveysRepositoryImpl.getSurveyFormState: grouped COUNT query instead of loading every ExamQuestion

**rating 71** (evidence 1.00 · impact 3/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L05-T1)

**credit:** L05-T1 1.00

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

### 8. TeamsVoicesFragment: drop the duplicate one-shot feed load; badge watermark via countTopLevelByTeam

**rating 71** (evidence 1.00 · impact 3/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L05-T8)

**credit:** L05-T8 1.00

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

### 9. RetryUtils.retry: rethrow CancellationException and drop the stderr printStackTrace

**rating 69** (evidence 0.90 · impact 3/5 · risk L) · **proposed by 3 agent(s):** ClaudeCode (L17-T7); Copilot·Kimi (L15-T1, L03-T4); Devin (L13-T1)

**credit:** L15-T1 0.43, L17-T7 0.43, L03-T4 0.11, L13-T1 0.04

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

### 10. TakeCourseViewModel.loadCourse (+ CoursesViewModel.loadCourses): map failures to NotFound, rethrow cancellation, log instead of printStackTrace

**rating 69** (evidence 0.90 · impact 3/5 · risk L) · **proposed by 3 agent(s):** ClaudeCode (L17-T8); Copilot·Kimi (L03-T8, L15-T6); Devin (L13-T2)

**credit:** L17-T8 0.42, L03-T8 0.28, L15-T6 0.21, L13-T2 0.08

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

### 11. Retire the single-test Robolectric RetryInterceptorEventTest into plain JUnit

**rating 69** (evidence 0.90 · impact 3/5 · risk L) · **proposed by 2 agent(s):** Copilot·Grok (L14-T2); Copilot·Kimi (L15-T2)

**credit:** L15-T2 0.50, L14-T2 0.50

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

### 12. UserDataWorker / AutoSyncWorker: await healthRepository.syncPendingHealthExaminations instead of fire-and-forget into appScope

**rating 69** (evidence 0.90 · impact 3/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L17-T1)

**credit:** L17-T1 1.00

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

### 13. PdfThumbnailLoaderTest: drop the lone @Config(sdk=[28]) pin (and the 28 jar from robolectricSdkJars)

**rating 67** (evidence 0.80 · impact 3/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L18-T4)

**credit:** L18-T4 1.00

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

### 14. VoicesRepositoryImpl / MyCourse: drain the download-link buffers after each save

**rating 58** (evidence 0.60 · impact 3/5 · risk M) · **proposed by 1 agent(s):** ClaudeCode (L05-T6)

**credit:** L05-T6 1.00

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

### 15. Narrow learning/resource ViewModels to UserReadRepository (ResourcesAccess, Resources, SubmissionDetail, ResourceViewer, TakeCourse, CoursesSteps)

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 5 agent(s):** Antigravity (L12-T2, L12-T8, L12-T9); ClaudeCode (L11-T2); Codex (L10-T7, L10-T8, L10-T6, L10-T10); Copilot·Grok (L08-T10); Devin (L07-T4, L07-T10)

**credit:** L11-T2 0.23, L07-T4 0.18, L07-T10 0.09, L10-T7 0.09, L10-T8 0.09, L08-T10 0.06, L12-T2 0.06, L12-T8 0.06, L10-T6 0.05, L10-T10 0.05, L12-T9 0.05

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

### 16. Narrow home/dashboard ViewModels to UserReadRepository (Calendar, CommunityTab, DashboardElement, Activities, Personals)

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 4 agent(s):** ClaudeCode (L11-T1, L11-T8); Codex (L10-T9, L10-T10); Copilot·Grok (L08-T10); Devin (L07-T3, L07-T4)

**credit:** L11-T1 0.24, L07-T3 0.24, L08-T10 0.22, L10-T9 0.12, L11-T8 0.07, L07-T4 0.06, L10-T10 0.06

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

### 17. QuestionDao.deleteStaleQuestions: @Transaction + bounded positive delete per exam

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 3 agent(s):** Antigravity (L12-T7); Codex (L10-T3); Copilot·Grok (L08-T6)

**credit:** L10-T3 0.53, L08-T6 0.26, L12-T7 0.21

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

### 18. Promote getCurrentUserId to UserReadRepository; narrow LifeViewModel

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L12-T10); Copilot·Grok (L08-T8)

**credit:** L12-T10 0.71, L08-T8 0.29

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

### 19. Consolidate guest-login and authenticate orchestration into LoginViewModel

**rating 55** (evidence 0.50 · impact 3/5 · risk M) · **proposed by 1 agent(s):** Devin (L07-T5)

**credit:** L07-T5 1.00

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

### 20. NotificationsViewModel: remove @ApplicationContext / R.string (strings holder or formatter)

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L11-T3, L17-T9)

**credit:** L11-T3 0.62, L17-T9 0.38

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

### 21. SettingsViewModel: UserReadRepository instead of the Context-backed UserSessionManager for isGuest

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L12-T1)

**credit:** L12-T1 1.00

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

### 22. SubmissionsRepositoryImpl: @Transaction batch for rev updates and mark-uploaded

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L05-T2)

**credit:** L05-T2 1.00

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

### 23. ResourcesPreviewLoader.getTextPreview: cap the read at 4096 chars before the 8-line limit

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 1 agent(s):** Codex (L04-T1)

**credit:** L04-T1 1.00

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

### 24. UserRepositoryImpl.sortUsers: compute lowercase keys once per sort (decorate-sort)

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L02-T9)

**credit:** L02-T9 1.00

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

### 25. VoicesAdapter: use ImageViewerUtils.showZoomableImage instead of a private copy

**rating 55** (evidence 1.00 · impact 2/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L05-T10)

**credit:** L05-T10 1.00

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

### 26. Migrate the last free Mockito tests to MockK

**rating 53** (evidence 0.90 · impact 2/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L11-T6)

**credit:** L11-T6 1.00

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

### 27. NotificationsRepositoryImpl: one private helper for getTaskTeamNamesByTaskIds/ByTaskTitles

**rating 53** (evidence 0.90 · impact 2/5 · risk L) · **proposed by 1 agent(s):** Devin (L01-T10)

**credit:** L01-T10 1.00

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

### 28. UserRepositoryImpl.ensureUserSecurityKeys/saveKeyIv: skip the upsert when key/iv exist, lazy keygen, drop TextUtils; drop HealthExaminationViewModel's double user read

**rating 52** (evidence 0.80 · impact 2/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L18-T10)

**credit:** L18-T10 1.00

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

### 29. TakeCourseViewModel.loadCourse: overlap independent reads inside coroutineScope; reuse course.courseSteps

**rating 50** (evidence 0.90 · impact 2/5 · risk M) · **proposed by 4 agent(s):** Antigravity (L06-T2); Copilot·Grok (L02-T4); Copilot·Kimi (L15-T6); Devin (L13-T2)

**credit:** L06-T2 0.36, L02-T4 0.27, L13-T2 0.21, L15-T6 0.16

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

### 30. ResourcesFragment: first-wins id/localAddress/tag maps instead of linear find scans

**rating 50** (evidence 0.90 · impact 2/5 · risk M) · **proposed by 2 agent(s):** ClaudeCode (L05-T9); Copilot·Grok (L02-T5)

**credit:** L05-T9 0.67, L02-T5 0.33

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

### 31. UploadConfigs: wrap each batch's mark-uploaded writes in AppDatabase.withTransaction

**rating 50** (evidence 0.90 · impact 2/5 · risk M) · **proposed by 1 agent(s):** ClaudeCode (L05-T3)

**credit:** L05-T3 1.00

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

### 32. ProgressRepositoryImpl.saveEarnedExamPasses: one submissions+answers load instead of per-step queries

**rating 48** (evidence 0.80 · impact 2/5 · risk M) · **proposed by 1 agent(s):** Copilot·Grok (L02-T2)

**credit:** L02-T2 1.00

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

### 33. Move getSyncedUsers/getSyncedUserByName/getPendingSyncUsers to UserSyncRepository; rewire UploadToShelfService

**rating 48** (evidence 0.60 · impact 2/5 · risk L) · **proposed by 1 agent(s):** Devin (L07-T1)

**credit:** L07-T1 1.00

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

### 34. Hoist getUserProfile/hasActiveUserSyncAction/getUsersSortedBy/searchUsers onto UserReadRepository; narrow Ratings/Markdown/Health VMs

**rating 46** (evidence 0.50 · impact 2/5 · risk L) · **proposed by 1 agent(s):** Devin (L07-T2)

**credit:** L07-T2 1.00

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

### 35. LifeRepositoryImpl.updateMyLifeListOrder: derive the owner from the caller, not the first item

**rating 45** (evidence 0.60 · impact 2/5 · risk M) · **proposed by 1 agent(s):** Codex (L10-T1)

**credit:** L10-T1 1.00

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

### 36. ChatViewModel resolves the current user via getUserModel; ChatHistoryFragment stops reading prefs

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L12-T3); ClaudeCode (L11-T10)

**credit:** L11-T10 0.56, L12-T3 0.44

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

### 37. CommunityDao.getAllSorted: filter blank names in SQL, drop the dialog-side TextUtils filter

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 2 agent(s):** ClaudeCode (L11-T5); Devin (L07-T6)

**credit:** L11-T5 0.56, L07-T6 0.44

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

### 38. SubmissionsRepositoryExporter.getExamId: substringBefore('@')

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L06-T8); Codex (L04-T9)

**credit:** L04-T9 0.81, L06-T8 0.19

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

### 39. FileUtils.getDisplayName / resolveUriToPath: DISPLAY_NAME projection instead of null

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Codex (L04-T6, L16-T8); Copilot·Grok (L14-T3)

**credit:** L04-T6 0.36, L14-T3 0.36, L16-T8 0.29

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

### 40. ResourcesViewModel sort wrappers: default dispatcher, mutate sort fields on the caller

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L12-T8); ClaudeCode (L17-T3)

**credit:** L12-T8 0.59, L17-T3 0.41

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

### 41. StepExamTest: delete the dead TextUtils mock; scope NetworkUtils to testSerializeExam

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L18-T5); Copilot·Grok (L14-T10)

**credit:** L14-T10 0.87, L18-T5 0.13 · rejected variant: L16-T5

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

### 42. automerge.sh publish_failed: one commits/{sha}/check-runs call instead of runs+jobs N+1

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L14-T8)

**credit:** L14-T8 1.00

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

### 43. Sha256Utils.getCheckSumFromFile: 64 KiB read buffer

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L02-T10)

**credit:** L02-T10 1.00

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

### 44. CommunityTabState: derived planetViewInId/communityViewInId/title used by both fragments

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L11-T8)

**credit:** L11-T8 1.00

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

### 45. CourseStepDao.deleteStaleSteps: stored-minus-keep positive delete in 900 batches

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L10-T2)

**credit:** L10-T2 1.00

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

### 46. ResourcesRepository: default stepResourcesNeedingDownload; CoursesStepsViewModel next-step prefetch uses it

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L08-T9)

**credit:** L08-T9 1.00

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

### 47. EventsRepositoryImpl.toggleAttendance: return the mutated meetup instead of re-querying

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L09-T8)

**credit:** L09-T8 1.00

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

### 48. JsonUtils.getDouble/getFloat: drop the fallback that repeats the same parse

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L04-T10)

**credit:** L04-T10 1.00

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

### 49. LifeViewModel.loadMyLifeList: seedMyLifeIfEmpty + observe instead of reading the list twice

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L11-T4)

**credit:** L11-T4 1.00

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

### 50. LoginViewModel.saveUsers reads the user itself; LoginActivity passes only name/password/source

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L11-T9)

**credit:** L11-T9 1.00

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

### 51. MyLifeDao.updateVisibility: use the UPDATE row count instead of a pre-SELECT

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L12-T5)

**credit:** L12-T5 1.00

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

### 52. MyLifeDao: replace parameter-OR userId predicates with a Kotlin branch so the userId index is usable

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L08-T3)

**credit:** L08-T3 1.00

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

### 53. NotificationActionReceiver: @Inject BroadcastService instead of the EntryPoint lookup

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L09-T10)

**credit:** L09-T10 1.00

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

### 54. ResourcesListFilter.matching: apply tag/facet/download filters before the ranked title search

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L04-T5)

**credit:** L04-T5 1.00

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

### 55. CommunityRepositoryImpl: remove android.util.Log

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L09-T1)

**credit:** L09-T1 1.00

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

### 56. News model: remove android.util.Log

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L09-T5)

**credit:** L09-T5 1.00

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

### 57. PersonalsRepositoryImpl: remove android.util.Log

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L09-T4)

**credit:** L09-T4 1.00

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

### 58. RetryRepositoryImpl: remove android.util.Log

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L09-T2)

**credit:** L09-T2 1.00

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

### 59. TakeCourseFragment: delete the unused UserSessionManager field injection

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L12-T2)

**credit:** L12-T2 1.00

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

### 60. TeamCoursesViewModel.getAvailableCourses: run the course-table mapping on io

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L14-T6)

**credit:** L14-T6 1.00

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

### 61. TTSManager.formatCsvForSpeech: single buildString

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L06-T9)

**credit:** L06-T9 1.00

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

### 62. VoicesLabelManager.updateAddLabelVisibility: cache the LABELS value set

**rating 39** (evidence 1.00 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L06-T10)

**credit:** L06-T10 1.00

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

### 63. HealthExaminationViewModel.saveExamination: rethrow CancellationException, Log.w instead of printStackTrace

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 4 agent(s):** Antigravity (L18-T10); ClaudeCode (L17-T8); Copilot·Kimi (L03-T7, L15-T7); Devin (L13-T1)

**credit:** L03-T7 0.46, L15-T7 0.23, L17-T8 0.18, L18-T10 0.09, L13-T1 0.05

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

### 64. LoginSyncManagerTest: class-level static mocks, per-test stubs + clearMocks

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 3 agent(s):** Antigravity (L18-T5); Copilot·Kimi (L15-T4); Devin (L13-T6)

**credit:** L15-T4 0.62, L13-T6 0.31, L18-T5 0.08

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

### 65. ResourcesViewModel.loadResources: rethrow cancellation, tagged log instead of printStackTrace

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 3 agent(s):** ClaudeCode (L17-T3); Copilot·Kimi (L15-T7); Devin (L13-T1)

**credit:** L15-T7 0.61, L17-T3 0.27, L13-T1 0.12

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

### 66. VoicesRepositoryNewsSyncTest: class-level mockkObject(UrlUtils, NetworkUtils); keep the per-test Room DB

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 3 agent(s):** Codex (L16-T3); Copilot·Grok (L14-T4); Copilot·Kimi (L15-T5)

**credit:** L15-T5 0.43, L16-T3 0.43, L14-T4 0.13

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

### 67. ChatViewModel.loadShareTargets: run the three summary reads concurrently

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L06-T4); Devin (L13-T3)

**credit:** L06-T4 0.74, L13-T3 0.26

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

### 68. FreeSpaceWorkerTest: delete the unused Log mock; class-level FileUtils mock

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 2 agent(s):** ClaudeCode (L17-T10); Codex (L16-T4)

**credit:** L16-T4 0.80, L17-T10 0.20

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

### 69. PublicSurveyViewModel: inject TimeProvider for launchTime

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L18-T7); Copilot·Kimi (L15-T10)

**credit:** L15-T10 0.57, L18-T7 0.43

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

### 70. UserRepositoryImplTest: class-level static mocks, per-test stubs + clearMocks

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Copilot·Kimi (L15-T3); Devin (L13-T7)

**credit:** L15-T3 0.67, L13-T7 0.33

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

### 71. CoursesRepositoryImpl.getCourseDetailModel: async the five independent reads

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L02-T3)

**credit:** L02-T3 1.00

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

### 72. CoursesStepsViewModel.loadStep: launch the priority download and next-step prefetch as children of loadDataJob

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L16-T1, L04-T4)

**credit:** L16-T1 0.57, L04-T4 0.43

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

### 73. CoursesStepsViewModel.loadStep: run the markdown rewrite and offline filter on io

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L14-T5)

**credit:** L14-T5 1.00

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

### 74. ExamTakingFragment: parse the constant 'Other' choice once; reuse the inflater

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L01-T5)

**credit:** L01-T5 1.00

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

### 75. SubmissionsRepositoryExporter.drawMultilineText: measure space and word once

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L06-T8)

**credit:** L06-T8 1.00

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

### 76. FeedbackAdapter: resolve label strings once in onCreateViewHolder

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L01-T8)

**credit:** L01-T8 1.00

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

### 77. FileUploader.uploadDoc: Log.e instead of printStackTrace

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L03-T6)

**credit:** L03-T6 1.00

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

### 78. HealthExaminationItemMapper: Kotlin stdlib instead of TextUtils

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L18-T6)

**credit:** L18-T6 1.00

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

### 79. Move the two @Module classes declared outside di/ into one di/ module

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L11-T7)

**credit:** L11-T7 1.00

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

### 80. Meetup.getHashMap: drop the unreachable date try/catch and the routine JSONArray stack trace

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L03-T9)

**credit:** L03-T9 1.00

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

### 81. News: raw-keyed parse cache for reactionsMap / pendingReactionsMap

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L02-T7)

**credit:** L02-T7 1.00

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

### 82. ServerReachabilityWorkerTest: class-level mocks, keep the Log verifies

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L16-T7)

**credit:** L16-T7 1.00

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

### 83. RequestsViewModel.loadJoinedMembers: run members and current-user reads concurrently

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L06-T3)

**credit:** L06-T3 1.00

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

### 84. ResourcesSearchUtils.searchList: append to the prefix list instead of +

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L16-T9)

**credit:** L16-T9 1.00

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

### 85. StableIdGenerator: local NO_ID constant, drop the RecyclerView import

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L17-T5)

**credit:** L17-T5 1.00

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

### 86. SubmissionsAdapter / SubmissionsListAdapter: dateCache like sibling adapters

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L01-T7)

**credit:** L01-T7 1.00

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

### 87. SubmissionDetailViewModel: collapse the intermediate stateIn

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L12-T9)

**credit:** L12-T9 1.00

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

### 88. SubmissionsRepositoryImpl: LIMIT 1 survey lookup in isStepCompleted; index loop instead of zip in flow dedup

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L01-T3)

**credit:** L01-T3 1.00

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

### 89. SyncActivity: inject @ApplicationScope instead of MainApplication.applicationScope statics

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L17-T2)

**credit:** L17-T2 1.00

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

### 90. test_timing_summary.py --test-sources: index basenames once

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L16-T10)

**credit:** L16-T10 1.00

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

### 91. TransactionSyncManager: replace four printStackTrace calls with Log.e (and rethrow cancel in authenticate)

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L03-T5)

**credit:** L03-T5 1.00

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

### 92. UserEntityEncodeImageTest: class-level Base64/Uri static mocks

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L16-T6)

**credit:** L16-T6 1.00

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

### 93. VoicesAdapter.getCurrentLeader: first-wins name map instead of a linear scan

**rating 38** (evidence 0.90 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L01-T6)

**credit:** L01-T6 1.00

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

### 94. automerge.sh: one gh pr view for state/reviewDecision/mergeable per PR

**rating 37** (evidence 0.80 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L18-T3); Devin (L13-T5)

**credit:** L13-T5 0.59, L18-T3 0.41

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

### 95. ChatConversationPaginator: Gson parse on the default dispatcher

**rating 37** (evidence 0.80 · impact 1/5 · risk L) · **proposed by 1 agent(s):** ClaudeCode (L17-T4)

**credit:** L17-T4 1.00

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

### 96. Feedback.serializeFeedback / HealthExamination.formatConditions: stop printStackTrace

**rating 37** (evidence 0.80 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Kimi (L03-T10)

**credit:** L03-T10 1.00

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

### 97. HealthRepositoryImpl: remove android.util.Log

**rating 36** (evidence 1.00 · impact 1/5 · risk M) · **proposed by 1 agent(s):** Copilot·Kimi (L09-T3)

**credit:** L09-T3 1.00

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

### 98. NotificationsViewModel: flowOn(default) for grouping/flattening

**rating 35** (evidence 0.90 · impact 1/5 · risk M) · **proposed by 1 agent(s):** Codex (L04-T3)

**credit:** L04-T3 1.00

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

### 99. playstore-quota.sh save_epochs: GraphQL isDraft/publishedAt instead of full release documents

**rating 35** (evidence 0.90 · impact 1/5 · risk M) · **proposed by 1 agent(s):** Copilot·Grok (L14-T7)

**credit:** L14-T7 1.00

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

### 100. Delete dead android Log static mocks (RetryQueueWorkerTest, DownloadService*Test, HealthExaminationTest, NewsTest)

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 2 agent(s):** Antigravity (L18-T5); ClaudeCode (L17-T10)

**credit:** L17-T10 0.55, L18-T5 0.45

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

### 101. ChatRepositoryImpl.sortChats: precompute the sort key (drop the getLatestRev half)

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L02-T8)

**credit:** L02-T8 1.00

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

### 102. ChatSearch.fullConvoSearch: one presized result list instead of chained +

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L04-T8)

**credit:** L04-T8 1.00

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

### 103. CourseDetailViewModel.loadCourseDetail: run the markdown rewrite off Main

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Codex (L04-T2)

**credit:** L04-T2 1.00

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

### 104. NetworkMonitorWorker: rename UPLOAD_DELAY_SECONDS to REACHABILITY_DELAY_SECONDS

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L13-T9)

**credit:** L13-T9 1.00

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

### 105. NotificationsViewModel.toggleGroupExpansion: shared groupTypeOf for default-expanded and unread

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L06-T7)

**credit:** L06-T7 1.00

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

### 106. PersonalDao.countByTitle: branch on empty userId (drop the delete/update OR split)

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L08-T4)

**credit:** L08-T4 1.00

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

### 107. TeamsRepositoryImpl.getJoinedMembersWithVisitInfo: reuse the membership rows for leaderIds

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L01-T1)

**credit:** L01-T1 1.00

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

### 108. UrlUtils.hostUrl: cache behind the existing generation counter

**rating 34** (evidence 0.60 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L01-T4)

**credit:** L01-T4 1.00

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

### 109. CommunityServicesViewModel: TeamsShareRepository + TeamsMembersRepository instead of TeamsRepository

**rating 33** (evidence 0.50 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Copilot·Grok (L08-T7)

**credit:** L08-T7 1.00

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

### 110. SubmissionsRepositoryExporter: stop silently swallowing the two catch{null} blocks

**rating 33** (evidence 0.50 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Devin (L13-T4)

**credit:** L13-T4 1.00

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

### 111. playstore-quota.sh forecast: measure once instead of twice

**rating 33** (evidence 0.50 · impact 1/5 · risk L) · **proposed by 1 agent(s):** Antigravity (L18-T1)

**credit:** L18-T1 1.00

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

### 112. Move TeamsTasksFragment companion filters into TeamsTasksViewModel

**rating 32** (evidence 0.60 · impact 1/5 · risk M) · **proposed by 1 agent(s):** Copilot·Kimi (L09-T6)

**credit:** L09-T6 1.00

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

### 113. ImageUtilsTest: class-level Glide static mock + relaxed ImageView

**rating 27** (evidence 0.50 · impact 1/5 · risk H) · **proposed by 1 agent(s):** Copilot·Grok (L14-T9)

**credit:** L14-T9 1.00

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
