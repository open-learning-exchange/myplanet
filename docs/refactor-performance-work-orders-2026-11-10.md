# myPlanet performance refactor work orders

Date: 2026-11-10 (America/New_York).
Base commit: `3d02e24a44e36d8a08b26f83cfd60bb95df569b1`.
Open PRs checked: #18555, #18554, #18551, #18540, #18539, #18455, #18076, #18045, #18029, #17832, #17811, #17694, #16594, #15951, #15825, #15824, #15820, #15808, #15559, #15267, #15266, #15226, #15108, #14883, #14650, #14427, #13928, #13848, #13657, #13604, #13355, #13287, #10993, #8175, #4075.

The code search preceded the PR collision check. All 35 currently open PRs were checked through GitHub, including their complete changed-file lists: 1,317 distinct paths were excluded regardless of age, draft status, or label. The recent window is November 3–10, 2026; the open PRs updated in that window are #18555, #18554, #18551, #18540, #18539, and #18455, and none currently carries a `review ready` or `merge` label.

| Open PR | Title |
| --- | --- |
| #18555 | all: bump gradle-wrapper from 9.8.2 to 9.9.0 |
| #18554 | all: smoother claude codebase inventory documenting |
| #18551 | resources: resolve local WebView content off main thread (fixes #18547) |
| #18540 | resources: smoother download stopping (fixes #18538) |
| #18539 | resources: smoother download error closing (fixes #18525) |
| #18455 | actions: smoother workflow release obfuscating (fixes #18451) |
| #18076 | Ratings: fixed delayed rating updates (fixes #16958) |
| #18045 | Kmp phase4 |
| #18029 | Kmp phase3 |
| #17832 | Kmp phase2 |
| #17811 | mySurvey: survey number update |
| #17694 | Shared kmp module scaffold |
| #16594 | actions: smoother size labeller fetching (fixes #16344) |
| #15951 | teams: smoother repository update requesting (fixes #15568) |
| #15825 | local event task reminders workmanager notifications (address #15115) |
| #15824 | gamification achievement hub offline badges streaks (address #15114) |
| #15820 | teams: smoother task and meetup comment threads managing (fixes #15112) |
| #15808 | sync: intelligent incremental sync via couchdb changes feed (fixes #15807) |
| #15559 | exam: redesign UI with elapsed timer and cards (fixes #15558) |
| #15267 | prevent download popup dialog cropping when text size is large (fixes #15263) |
| #15266 | prevent team calendar cropping in landscape mode by using NestedScrollView (fixes #15265) |
| #15226 | feat(flutter): Flutter/Dart port of myPlanet (phases 1–28) |
| #15108 | fix event calendar marking (fixes #15107) |
| #14883 | team: add leaderboard tab (fixes #14880) |
| #14650 | survey: smoother submissions display (fixes #14619) |
| #14427 | Course streak |
| #13928 | Add baseline profile module and installer (fixes #13927) |
| #13848 | all: introduce Course/Grade models and wire into UI (fixes #13802) |
| #13657 | course: Archive course My Courses library (fixes #13559) |
| #13604 | teams: Add sort by completeness option in Survey section (fixes #13590) |
| #13355 | Add P2P resource sharing (Wi-Fi P2P) (fixes #13353) |
| #13287 | profile: no char limit for edit texts (fixes #13283) |
| #10993 | Voices video |
| #8175 | roboscript update (fixes #7986) |
| #4075 | robo movie (fixes #4074) |

Tasks are ordered by expected user impact divided by blast radius, not by measured speedup: bounded preview memory first, UI responsiveness and obsolete work next, selective filtering and smaller allocations afterward. Each task owns exactly its two listed existing files, including its test file; no task requires another task or a shared API change. Execute from the repository root against the base commit or a descendant containing any subset of these tasks. Recheck live open-PR files before implementation; a newly conflicting task must be replanned rather than silently expanded.

All sizes include production edits and tests and must stay below 150 changed lines and five files. Use existing dependencies, add no unused code or placeholders, and change no build configuration. Roadmap 9/10 notes describe incidental portability benefits only; neither north star is scheduled or made a prerequisite. Acceptance commands below are instructions for implementing agents and were not run for this documentation-only planning change.

---

### 1. Bound text-preview reads before splitting into lines (roadmap 7+8)

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

### 2. Prepare course-detail paths and markdown off the main thread (roadmap 3+7+8)

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

### 3. Move notification grouping and flattening onto the injected compute dispatcher (roadmap 3+7+8)

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

### 4. Cancel obsolete course-step download preparation with its load job (roadmap 3+5+7+8)

context: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:64` cancels the previous `loadDataJob`, but lines 110 and 125 launch priority-download preparation and next-step prefetch as separate children of `viewModelScope`. Those coroutines survive the load they belong to, allowing rapid step changes to keep preparing obsolete resource requests; parenting them under the load avoids that wasted work while retaining ViewModel ownership for roadmap 10.

files:

- `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt` — the two nested launches inside `CoursesStepsViewModel.loadStep`; leave `saveCourseProgress`, `refreshInlineResources`, and `CourseStepUiState` alone.
- `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt` — `CoursesStepsViewModelTest`; extend the existing step-change cancellation scenarios.

steps:

1. Launch current-step priority-download preparation and next-step prefetch as children of the active load coroutine rather than directly on `viewModelScope`. Preserve their concurrent execution after state publication.
2. Retain existing server checks, URL order, offline-resource exclusions, and step-ID guards. Let cancellation propagate naturally without catching it or translating it into failure state.
3. Add a deterministic test that suspends an old next-step resource lookup, loads another step, and confirms the old lookup is cancelled before it can enqueue a download.
4. Add a corresponding test for a suspended priority-download call, and retain the existing successful priority-download and prefetch tests. Verify that a request for the latest step still proceeds.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.courses.CoursesStepsViewModelTest'`
- `./gradlew testDefaultDebugUnitTest`
- Move rapidly between course steps on a slow connection: the latest step renders and downloads normally, while obsolete pending preparation stops issuing new work. Downloads already handed to the background scheduler remain governed by that scheduler.

size budget: approximately 75–120 changed lines, 2 files.

out of scope: Do not cancel already-enqueued background downloads, redesign download coordination, change repository cancellation contracts, or modify progress saving.

---

### 5. Apply selective resource filters before ranked title search (roadmap 7+8)

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

### 6. Request only display-name metadata from content providers (roadmap 7+8)

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

### 7. Charge every PDF thumbnail a positive cache weight (roadmap 7+8)

context: `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt:13` sizes the thumbnail cache in KiB, but line 14 floors each bitmap's byte count when calculating its weight. Bitmaps smaller than 1,024 bytes are charged zero and partial KiB are unaccounted for, weakening the intended memory bound when many small thumbnails are viewed.

files:

- `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt` — only the anonymous cache's `sizeOf` override; leave `firstPageBitmap`, cache keys, render dimensions, and `evictAll` alone.
- `app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt` — `PdfThumbnailLoaderTest`; extend cache-accounting coverage using the existing Robolectric setup.

steps:

1. Round each bitmap's byte count up to whole KiB, with a minimum charge of one. Use widened arithmetic before adding the rounding offset so the calculation cannot overflow an Int.
2. Keep the existing total cache budget and render path unchanged; do not recycle bitmaps during eviction because displayed views can still reference them.
3. Test a 1-by-1 bitmap, an exact 1,024-byte bitmap, and a bitmap just over that boundary, asserting cache charges of one, one, and two respectively.
4. Inspect the existing private cache from the test rather than adding a production accessor. Clear it after the accounting test and retain missing-file, invalid-width, and IO-dispatcher tests.

acceptance:

- `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.PdfThumbnailLoaderTest'`
- `./gradlew testDefaultDebugUnitTest`
- Scroll through PDF resources with small thumbnail widths: first-page images still render and cached small bitmaps now count toward eviction rather than accumulating with zero cost.

size budget: approximately 35–70 changed lines, 2 files.

out of scope: Do not change the cache budget, add a thumbnail cache, serialize rendering, or change bitmap ownership.

---

### 8. Assemble chat-search ranking buckets without intermediate concatenations (roadmap 7+8)

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

### 9. Extract export exam IDs without splitting entire parent strings (roadmap 1+7+8)

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

### 10. Remove repeated failed floating-point parsing in Kotlin JSON accessors (roadmap 1+7+8)

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

Signed: Codex · GPT-6 · high effort.
