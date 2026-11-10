# myPlanet refactor work orders — 2026-11-10

Date: 2026-11-10 (America/New_York).
Base commit: `2093a6224b221079f1413e2ab1f22cebec8cf736` (`master`).
Open PRs checked: #18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075.

All 35 open PRs were enumerated, including drafts, old experiments, and held PRs; their paginated changed-file lists and previous names exclude 1,366 paths. The protected set is broader than just recently updated review-ready PRs. None of the files assigned below intersects that set. Each work order starts independently from the base commit; execute them in any order, without relying on another task's changes. Before implementation, refresh open PRs and stop an individual task if its assigned files have become owned by a new PR.

## Workflow evidence and selection

The code search preceded PR exclusion. All six job logs from the following latest completed runs were inspected:

| Run | Result and observed cost | Consequence for this round |
| --- | --- | --- |
| [Default-branch tests, 42782082676](https://github.com/open-learning-exchange/myplanet/actions/runs/42782082676) | Success at the base commit. Shard 1: 1,663 tests / 182 classes, 499.3 summed test seconds; shard 2: 1,523 tests / 206 classes, 436.2 summed test seconds. Both exceed the 400-second warning. Gradle elapsed times were 3m34s and 3m23s. | Prefer removing repeated test instrumentation where the source confirms it. Summed test seconds are across forks, not job wall time. |
| [Latest release, 42782082588](https://github.com/open-learning-exchange/myplanet/actions/runs/42782082588) | Success on `master` at the base commit; Gradle elapsed times were 2m27s for default and 2m55s for lite. | Release-workflow edits are excluded by open PRs; no release fix is scheduled from a passing run. |
| [Latest build, 42781301168](https://github.com/open-learning-exchange/myplanet/actions/runs/42781301168) | Success on branch `5278-download-suggestion-dialog-appears-on-every-visit`, commit `b14b2a1db76795c5f453a3ac544d49c60e867200`; Gradle elapsed times were 4m36s for default and 4m03s for lite. | The build workflow excludes `master`; this is the latest branch build, not a default-branch build. Workflow/cache changes are excluded by open PRs. |

The test, build, release, Play Store, and labels workflow files all appear in open-PR file lists. The largest measured test classes are also largely protected. The remaining tasks are ordered by user impact relative to change scope: prevent obsolete work and stale UI first, remove measured repeated instrumentation next, then take small allocation/query and diagnostic wins. Timing observations support prioritization, not promised speedups.

Only the work-order document is changed in this planning run. Source evidence, file ownership, task structure, and file disjointness were checked; the acceptance commands below are requirements for implementing agents and were not run during this documentation-only pass. Implementation budgets include production edits, imports, and tests. Every task uses existing dependencies, retains all unrelated assertions, and adds no placeholders. Roadmap 9 and 10 are architectural constraints, never standalone scheduled work or prerequisites; only supported contributions are claimed below.

---

### 1. Cancel obsolete course-step download preparation with its load (roadmap 3+5+7)

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

### 2. Coalesce active team-course loads and cancel superseded requests (roadmap 3+7)

context: `TeamCoursesViewModel.kt:31–43` launches a fresh pair of repository requests for every call and allows all callers to publish to the same state. Repeated requests for the same team duplicate work; a slow request for a previous team or user can overwrite a newer result. `TeamCoursesViewModelTest.kt:151` already checks the course-ID dependency edge and must remain valid.

files:
- `app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt` — `TeamCoursesViewModel.loadCourses` and private load bookkeeping.
- `app/src/test/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModelTest.kt` — `TeamCoursesViewModelTest`.
- Leave `getAvailableCourses`, `addCourses`, `removeCourse`, and both repository contracts and implementations alone.

steps:
1. Track the active load and its complete team/user argument pair; return early only when an identical request is still active.
2. Cancel a previous active load before launching a request with different arguments; retain the existing structured parallel creator/course reads.
3. Permit an identical request after completion so ordinary refreshes still reload changed data.
4. Test duplicate calls during suspension, a delayed A followed by B, a user-only argument change, and a same-key refresh after completion; verify repository call counts and final state.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.teams.courses.TeamCoursesViewModelTest'` must pass.
- `./gradlew testDefaultDebugUnitTest` must stay green.
- Open one team's courses and immediately switch teams: the current team's courses and removal permission win, while a later refresh still reflects additions/removals.

size budget: approximately 100–135 changed lines across 2 files.

out of scope: No persistent result cache, repository changes, navigation redesign, or new public refresh API. This keeps loading/state coordination in the ViewModel, supporting roadmap 10's state-hoisting constraint.

---

### 3. Install Voices sync test object mocks once per test class (roadmap 5+8)

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

### 4. Remove unnecessary logging instrumentation from free-space tests (roadmap 7+8)

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

### 5. Reuse class-level exam test instrumentation without leaking stubs (roadmap 1+8)

context: `StepExamTest.kt:21–35` installs text-framework and device-ID mocks in every `setup` call and globally uninstalls them in every `tearDown`. The latest shard-2 log reports 12 tests taking 6.7 seconds, with 6.6 seconds remaining after first-test excess. The parsing, serialization, and exact-pass-mark tests need their existing answers, not repeated bytecode instrumentation.

files:
- `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt` — `StepExamTest.setup` and `tearDown`, plus class lifecycle hooks.
- Leave all parsing, serialization, and passing-mark test bodies and the production exam model alone.

steps:
1. Install the existing static and object mocks once in static class setup; uninstall exactly those targets in class teardown.
2. Clear their answers and recorded calls between tests without uninstalling instrumentation.
3. Reapply the existing null-safe text answer and fixed device identifier in per-test `setup`.
4. Retain every assertion and test method; remove only imports made unused by the lifecycle adjustment.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.model.StepExamTest' --rerun-tasks` must pass; capture comparable class timings.
- `./gradlew testDefaultDebugUnitTest` must stay green, with all 12 tests retained.
- Learners' exam passing thresholds and serialized exam fields remain unchanged, including exact boundary marks and missing-field behavior.

size budget: approximately 30–55 changed lines in 1 file.

out of scope: No model/schema changes, altered test inputs, removed framework behavior, or new test dependencies. Faster model tests support later data-layer work; no platform-removal change is claimed.

---

### 6. Amortize image-encoding test static mocks while restoring application state (roadmap 1+8)

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

### 7. Hoist reachability-worker test mocks while retaining logging assertions (roadmap 5+8)

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

### 8. Request only the display-name column when resolving picked files (roadmap 7+8)

context: `FileUtils.kt:217` and `FileUtils.kt:243` pass a null content-provider projection even though the callers only read the display-name column. A null projection requests all provider columns; limiting it avoids unnecessary metadata work during file selection/import while retaining the current fallbacks. `FileUtilsTest.kt:339–429` already exercises content-provider resolution and traversal sanitization.

files:
- `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` — `FileUtils.getDisplayName` and `resolveUriToPath` query arguments only.
- `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt` — `FileUtilsTest`, its existing provider query fixtures, and focused display-name coverage.
- Leave byte copying, URL parsing, filename sanitization, external-directory caching, and installation helpers alone.

steps:
1. Give each of the two queries an explicit one-column display-name projection; preserve selection arguments, cursor closure, and all fallback branches.
2. Extend the existing provider fixture to assert the requested projection and retain its byte-copy and sanitized-filename assertions.
3. Add focused coverage of `getDisplayName` with a provider name and with an absent name column, using the existing test-time provider pattern for a deterministic timestamp fallback where needed.
4. Keep null/file-URI and traversal cases green; do not consolidate the two functions or change their exception policies.

acceptance:
- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.FileUtilsTest'` must pass.
- `./gradlew testDefaultDebugUnitTest` must stay green.
- Pick a document/image from a content provider: its displayed/imported name and copied bytes remain correct, with existing fallback and path-sanitization behavior preserved.

size budget: approximately 65–110 changed lines across 2 files.

out of scope: No storage permissions, provider APIs, threading, import workflow, or cache-policy changes. Android-specific metadata stays at the utility boundary; this task does not claim to remove Android dependencies from the core.

---

### 9. Reuse the prefix-result buffer instead of allocating a third search list (roadmap 7+8)

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

### 10. Index test source filenames once during timing diagnostics (roadmap 7+8)

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

Signed: OpenAI Codex · GPT-6 · high effort.
