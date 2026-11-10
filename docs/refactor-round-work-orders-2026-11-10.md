# myPlanet refactor round — work orders

- **date**: 2026-11-10
- **base commit**: `2093a6224b221079f1413e2ab1f22cebec8cf736` (master)
- **open PRs checked**: #18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075 — 35 open PRs, ~561 touched files excluded
- **focus**: github workflows quick wins · micro-optimizations that unblock bigger refactors · obvious inefficiencies removable without rewrites
- every task was verified by opening the cited file on the base commit; no task touches a file an open PR modifies, and no file appears in two tasks

---

### 1. Replace `printStackTrace` calls with `Log.w` in production files (roadmap 8, feeds 9/10)

**context:** four production files still call `e.printStackTrace()` inside `catch` blocks. The output goes to stderr — invisible to logcat filters and crash tooling, and it forces Android-only `Throwable` handling into files that could otherwise run platform-free. Evidence: `e.printStackTrace()` at `SubmissionsPayloadBuilder.kt:163`, `lastException?.printStackTrace()` at `RetryUtils.kt:32`, `e.printStackTrace()` at `ResourcesViewModel.kt:109` and `HealthExaminationViewModel.kt:120`.

**files:** `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsPayloadBuilder.kt` (catch in `build`/`buildPayload`), `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt` (catch in `withRetry`), `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` (`loadResources`), `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt`. Leave alone: `TakeCourseViewModel.kt`'s `printStackTrace` — it is covered by task 2. All other `catch` blocks in these files — they already log or rethrow correctly.

**steps:**
1. In each file, replace `e.printStackTrace()` / `lastException?.printStackTrace()` with `Log.w(TAG, "…", e)` (or `Log.e` where the surrounding `catch` already signals a real failure).
2. Use each class's existing `TAG` if present; otherwise add `private val TAG: String = "<ClassName>"` — no `companion object` needed beyond the file's current convention.
3. Make sure `import android.util.Log` is present in each file.
4. Run `python3 .agents/skills/kotlin-importing/kotlin-importing.py --check app/src` to confirm the import set stays clean.

**acceptance:** `./gradlew :app:compileDefaultDebugKotlin` passes; `./gradlew testDefaultDebugUnitTest` stays green; `grep -rn "printStackTrace" app/src/main/java` returns nothing outside `TakeCourseViewModel.kt`. User-visible: none — log output moves to logcat under the class tag.

**size budget:** ~15 changed lines, 4 files.

**out of scope:** no restructuring of the catch blocks, no logging framework, no new tags elsewhere, no changes to `TakeCourseViewModel.kt`.

---

### 2. Parallelize `TakeCourseViewModel.loadCourse` and fix its `printStackTrace` (roadmap 7, 3)

**context:** `loadCourse` in `TakeCourseViewModel.kt` awaits `getCourseSteps` then `getCourseProgress` sequentially (`:124-125`), although both are independent suspend calls keyed by the same `courseId` — opening a course pays two sequential Room round-trips. The enclosing `catch` also calls `e.printStackTrace()` at `:131`. Evidence: `val steps = …getCourseSteps(courseId)` followed by `val progress = …getCourseProgress(courseId)` as two straight-line awaits.

**files:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` — `loadCourse` only. Leave alone: `getBlockingStepIndex` (depends on `steps`, must stay after it), the surrounding state-flow emission order, `stepFormatPattern`-style lazy fields elsewhere.

**steps:**
1. Inside `loadCourse`, wrap the two independent repository calls in `val stepsD = async { …getCourseSteps(courseId) }` and `val progressD = async { …getCourseProgress(courseId) }`.
2. Await them (`val steps = stepsD.await(); val progress = progressD.await()`) before any logic that reads either result, keeping `getBlockingStepIndex(steps, …)` and downstream calls in their current order.
3. Replace `e.printStackTrace()` at `:131` with `Log.w(TAG, "Failed to load course", e)` (add `import android.util.Log` and a `TAG` if the class lacks one).
4. Keep the existing error-handling path — parallelization must not swallow or reorder state updates.

**acceptance:** `./gradlew :app:compileDefaultDebugKotlin` and `./gradlew testDefaultDebugUnitTest` green. User-visible: course detail opens with steps + progress loading concurrently; a failing call still lands in the existing error branch.

**size budget:** ~20 changed lines, 1 file.

**out of scope:** no changes to `CourseStepDao`/`CourseProgressDao`, no caching layer, no other functions in this file.

---

### 3. Parallelize `ChatViewModel.loadShareTargets` (roadmap 7, 3)

**context:** `loadShareTargets` in `ChatViewModel.kt` awaits `getTeamSummaries` and then `getShareableEnterpriseSummaries` sequentially (`:193-195`). Both are independent Room-backed lookups that feed the same share sheet, so the sheet pays their latency serially. Evidence: two consecutive suspend calls with no data dependency between them.

**files:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt` — `loadShareTargets` only. Leave alone: the rest of `ChatViewModel` (AI-provider handling, conversation state), the repositories it calls.

**steps:**
1. In `loadShareTargets`, launch `val teams = async { …getTeamSummaries() }` and `val enterprises = async { …getShareableEnterpriseSummaries() }` in the same coroutine scope already used by the function.
2. Replace the sequential awaits with `teams.await()` / `enterprises.await()` before the merge that builds the share-target list.
3. Preserve the current result ordering and the existing exception path — if either call throws, the function must behave exactly as today.

**acceptance:** `./gradlew :app:compileDefaultDebugKotlin` and `./gradlew testDefaultDebugUnitTest` green. User-visible: the share-target list in chat populates after a single round-trip's latency instead of two.

**size budget:** ~12 changed lines, 1 file.

**out of scope:** no API surface changes, no new coroutine scopes, no caching, no changes to chat fragments.

---

### 4. Batch exam lookups and log swallowed catches in `SubmissionsRepositoryExporter` (roadmap 1, 7)

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

### 5. Collapse three `gh pr view` calls into one in `automerge.sh` (roadmap 8 — workflow quick win)

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

### 6. Hoist static mocks out of `LoginSyncManagerTest.setup` (roadmap 8 — test speed)

**context:** `LoginSyncManagerTest` re-runs `mockkStatic(Log::class)`, `mockkStatic(Base64::class)`, and `mockkObject(…)` in `@Before` for all 19 tests, then `unmockkAll()` in `@After` (`:54-85`). Static-mock setup/teardown is the single biggest per-test cost flagged by `test_timing_summary.py`'s "static-mock churn" heuristic — the class spends 24.2s total on mocks that never change between tests. Evidence: identical `mockkStatic`/`mockkObject`/`every` stubs recreated per test.

**files:** `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt` — `setup`/`tearDown` only. Leave alone: all test bodies, the `TestScope`/dispatcher wiring, per-test `mockk(relaxed = true)` fields.

**steps:**
1. Move `mockkStatic(Log::class)` + its `every` stubs, `mockkStatic(Base64::class)` + stubs, and the `mockkObject(…)` registrations into a `companion object` `@JvmStatic @BeforeClass fun` (or a lazily-initialized `init` holder that runs once per class).
2. Replace `unmockkAll()` in `@After` with a matching `@JvmStatic @AfterClass` that unmockks only the class-level mocks; keep per-test mocks untouched.
3. If a mock needs per-test re-stubbing, keep just that `every { }` line in `@Before` — hoist only the `mockk*`/`unmockk*` scaffolding.
4. Confirm `JavaBase64` import and stub behavior remain identical.

**acceptance:** `./gradlew :app:testDefaultDebugUnitTest --tests "*LoginSyncManagerTest*"` passes; total class time drops measurably in the timing report. User-visible: none — CI faster.

**size budget:** ~25 changed lines, 1 file.

**out of scope:** no changes to test assertions or ordering, no mockk version bump, no shared test fixtures between classes.

---

### 7. Hoist static mocks out of `UserRepositoryImplTest.setup` (roadmap 8 — test speed)

**context:** `UserRepositoryImplTest` runs `mockkObject(UrlUtils)`, `mockkObject(NetworkUtils)`, `mockkStatic(Log::class)` plus their `every` stubs in `@Before` for all 30 tests, then `unmockkObject`/`unmockkStatic` in `@After` (`:80-150`). Same static-mock churn pattern as task 6 — 33.7s of class time, none of it test-specific. Evidence: the `setup` block rebuilds identical object/static mocks before every test.

**files:** `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt` — `setup`/`tearDown` only. Leave alone: test bodies, `StandardTestDispatcher`/`TestScope`, per-test `mockk(relaxed = true)` fields, `LoginSyncManagerTest.kt` (owned by task 6).

**steps:**
1. Move the `mockkObject(UrlUtils)`/`mockkObject(NetworkUtils)`/`mockkStatic(Log::class)` calls — and their invariant `every` stubs (`UrlUtils.header`, `UrlUtils.getUrl()`, `NetworkUtils.getUniqueIdentifier()`, `Log.e` overloads) — into a `companion object` `@JvmStatic @BeforeClass fun`.
2. Replace per-test `unmockkObject`/`unmockkStatic` in `@After` with an `@AfterClass` teardown covering only the class-level mocks.
3. Keep `activitiesRepository`, `resourcesRepository`, and other per-test mocks in `@Before` as they are.
4. Keep `testDispatcher`/`appScope` per-test — the dispatcher must not be hoisted.

**acceptance:** `./gradlew :app:testDefaultDebugUnitTest --tests "*UserRepositoryImplTest*"` passes with a measurable time drop. User-visible: none — CI faster.

**size budget:** ~30 changed lines, 1 file.

**out of scope:** no assertion changes, no fixture extraction shared with other test classes, no dispatcher changes.

---

### 8. Cache `englishValues` locale contexts in `CoursesFilterFragment` (roadmap 7)

**context:** `englishValues(context, arrayRes)` in `CoursesFilterFragment.kt` (`:116-118`) builds a fresh `Configuration`, calls `createConfigurationContext`, and reads `getStringArray` every time it runs — it is called from `setupSpinner` and `selectedValue` for each of the filter's spinners, so a single filter interaction rebuilds the same localized context several times. Evidence: the helper is invoked per spinner call site rather than once per `arrayRes`.

**files:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFilterFragment.kt` — `englishValues` and its call sites. Leave alone: the spinner listeners' logic, the resource ids, other fragments using similar patterns (not in this task).

**steps:**
1. Introduce a `private val englishValuesCache = mutableMapOf<Int, Array<String>>` on the fragment (cleared implicitly with the fragment lifecycle).
2. Rewrite `englishValues(context, arrayRes)` to `getOrPut(arrayRes) { …existing context-build + getStringArray… }`.
3. Keep the returned array as the source of truth for labels exactly as today — no change to ordering or fallback behavior.
4. Ensure no other caller in the file bypasses the cache.

**acceptance:** `./gradlew :app:compileDefaultDebugKotlin` and `./gradlew testDefaultDebugUnitTest` green. User-visible: filter dropdowns populate with identical labels; slightly faster open of the filter sheet.

**size budget:** ~15 changed lines, 1 file.

**out of scope:** no localization behavior changes, no cache invalidation logic, no changes to `strings.xml`.

---

### 9. Rename and de-stale `UPLOAD_DELAY_SECONDS` in `NetworkMonitorWorker` (roadmap 5, 8)

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

### 10. Hoist invariant `getString`/`Calendar` calls out of hot lambdas (roadmap 7)

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

---

## self-check

- 10 tasks, each independently mergeable in any order — no two touch the same file.
- every cited path/class/function was opened and confirmed on `2093a6224` ; line numbers match base commit.
- no task touches a file modified by any of the 35 open PRs (verified against the full changed-file list).
- every task has context / files / steps / acceptance / size budget / out of scope; each is under ~150 changed lines and under ~5 files, with no new dependencies, no unused code, no TODO placeholders.

*generated by devin · cognition ai · normal effort*
