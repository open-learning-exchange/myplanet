# Performance quick wins work orders — 2026-09-04

date: 2026-09-04  
base commit: `9ff1273dc95f8cbd3590fca12ca821454b2e27bc`  
open PRs checked: could not check open PRs (the checkout has no remote and GitHub CLI is unauthenticated)

The work orders below are ranked by expected user impact divided by blast radius. Each owns a disjoint file set, is independently mergeable in any order, adds no dependency, and stays below the stated size limits.

### 1. Parse course-progress sync keys once per document (roadmap 1+5+7)

context: `ProgressRepositoryImpl.insertCourseProgressFromSync` makes four complete passes over every incoming document to collect IDs and step numbers, then parses the same four fields again while constructing records (`ProgressRepositoryImpl.kt:269-293`). This adds JSON lookups and temporary lists on a sync hot path; consolidating extraction also advances north-star 9 by making the platform-free sync transform more explicit.

files: `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt` — `ProgressRepositoryImpl.insertCourseProgressFromSync`. Leave `courseProgressFromJson`, every DAO interface, and `app/src/test/java/org/ole/planet/myplanet/repository/ProgressRepositoryImplTest.kt` unchanged.

steps:
1. Introduce a private, file-local value representation for the four parsed lookup keys associated with one `JsonObject`.
2. Build that parsed representation in one pass over `docs`, preserving input order and the existing treatment of blank IDs and duplicate query keys.
3. Derive DAO query arguments from the parsed rows and reuse each row's values when resolving `existingProgress` and `localRecord`.
4. Keep `courseProgressFromJson` as the single place that hydrates the final entity, and remove imports made unused by the refactor.
5. Run the focused repository test and the required default-flavor unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.ProgressRepositoryImplTest'` and `./gradlew testDefaultDebugUnitTest` pass. A sync batch with duplicate documents, blank IDs, and locally passed progress produces the same persisted records while doing only one key-extraction pass.

size budget: about 35 changed lines in 1 file.

out of scope: Do not change Room queries, transaction boundaries, conflict semantics, or the `ProgressRepository` API.

---

### 2. Remove identity collection copies from course-detail hydration (roadmap 1+3+7)

context: `CoursesRepositoryImpl.getCourseProgress` copies three DAO results through identity `map { it }` calls before grouping or filtering (`CoursesRepositoryImpl.kt:440-449` and `CoursesRepositoryImpl.kt:456-460`). Those allocations occur whenever course details and progress are assembled, without changing type, order, or values; removing them also keeps the eventual platform-free use-case pipeline simpler for north-star 9.

files: `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt` — `CoursesRepositoryImpl.getCourseProgress`. Leave `filterCourses`, `flushPendingCourseResources`, DAO declarations, and `app/src/test/java/org/ole/planet/myplanet/repository/CoursesRepositoryImplTest.kt` unchanged.

steps:
1. Feed `questionDao.getByExamIds(examIds)` directly into `groupBy` instead of identity-mapping it.
2. Filter `submissionDao.getExamSubmissionsByUser(userId)` directly without copying the list first.
3. Feed `answerDao.getBySubmissionIds(submissionIds)` directly into its existing grouping operation.
4. Preserve empty-input short circuits, key filtering, ordering, and nullable-key behavior exactly.
5. Remove only imports that become unused, then run focused and full unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.CoursesRepositoryImplTest'` and `./gradlew testDefaultDebugUnitTest` pass. Opening a course still shows the same steps, exams, questions, submissions, answers, and completion state.

size budget: about 6 changed lines in 1 file.

out of scope: Do not alter database access, introduce a use case, change sorting, or modify Android Base64 handling elsewhere in this repository.

---

### 3. Collapse member visit aggregation into one pass (roadmap 1+7)

context: `TeamsRepositoryImpl.getJoinedMembersWithVisitInfo` traverses visit logs once for `groupingBy().eachCount()` and again through `groupBy().mapValues()` to find the latest visit (`TeamsRepositoryImpl.kt:1022-1035`). The second pipeline also retains a list per user, so one accumulator map lowers CPU and peak memory on large teams; the accumulator is plain Kotlin and is suitable for eventual extraction under north-star 9.

files: `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt` — `TeamsRepositoryImpl.getJoinedMembersWithVisitInfo` visit-log aggregation only. Leave membership ordering, `activitiesRepository` calls, formatting, all DAO interfaces, and benchmark files unchanged.

steps:
1. Replace the currently unused local `MemberStats` class with a small local aggregate holding a visit count and greatest non-null visit timestamp.
2. Traverse `logs` once and update a map keyed by the same nullable user key currently used by both collections.
3. Read count and latest timestamp from that aggregate while mapping `orderedMembers`.
4. Preserve the current defaults of zero visits and no latest timestamp when no matching log exists.
5. Run the teams repository tests and the default-flavor suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.TeamsRepositoryImplTest'` and `./gradlew testDefaultDebugUnitTest` pass. The members screen shows unchanged visit counts and latest-visit values for users with zero, one, and multiple logs.

size budget: about 25 changed lines in 1 file.

out of scope: Do not tackle per-member activity repository calls, membership queries, team sorting, or broader decomposition of `TeamsRepositoryImpl`.

---

### 4. Select HTML cover images without materializing the directory walk (roadmap 7+8)

context: `FileUtils.findHtmlCoverImage` walks up to four directory levels, materializes every matching image with `toList()`, scans again for a hinted filename, and may scan a third time for the largest file (`FileUtils.kt:142-151`). Large offline HTML resources can contain many images, so a single traversal reduces allocations and allows an immediate return for the first preferred cover.

files: `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt` — `FileUtils.findHtmlCoverImage` and its existing preview constants. Leave extraction, URI streaming, path validation, and `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt` unchanged.

steps:
1. Keep the existing depth limit, extension allowlist, walk order, and case-insensitive extension behavior.
2. Iterate lazily over candidate files instead of converting the walk to a list.
3. Compute each filename's lowercase form once and return the first candidate matching any existing name hint.
4. Track the largest candidate during traversal for the unchanged fallback, including current tie behavior.
5. Run the focused utility tests and the complete required suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.FileUtilsTest'` and `./gradlew testDefaultDebugUnitTest` pass. An HTML package still chooses the first hinted image, otherwise the largest supported image, and returns null for an empty or non-directory input.

size budget: about 20 changed lines in 1 file.

out of scope: Do not change supported extensions, hint priority, maximum depth, archive extraction, or add filesystem caching.

---

### 5. Avoid temporary union sets when counting queued downloads (roadmap 5+7)

context: `DownloadService.getRemainingCount` creates `priority + pendingUrls`, allocating a new set before counting unprocessed URLs (`DownloadService.kt:171-175`), and it is called from queue and notification updates (`DownloadService.kt:147-151`, `DownloadService.kt:501-515`). Count the two existing sets directly while preserving deduplication so frequent notification refreshes allocate less.

files: `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt` — `DownloadService.getRemainingCount` only. Leave queue selection, preference cleanup, file I/O, notification wording, and all tests unchanged.

steps:
1. Count unprocessed priority URLs directly from the supplied or preference-backed priority set.
2. Count unprocessed pending URLs only when they are not already in the priority set, preserving the union's deduplication semantics.
3. Continue excluding every URL present in `processedUrls` and preserve behavior for null or empty preference sets.
4. Do not change call sites or queue ordering.
5. Run both DownloadService test classes relevant to URL selection and completion, then the full suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.services.DownloadServiceTest' --tests 'org.ole.planet.myplanet.services.DownloadServiceOnDownloadCompleteTest'` and `./gradlew testDefaultDebugUnitTest` pass. Batch-download progress and completion notifications report the same remaining count when an URL occurs in priority, pending, both, or neither set.

size budget: about 10 changed lines in 1 file.

out of scope: Do not redesign SharedPreferences queue storage, alter notification frequency, or change download concurrency.

---

### 6. Build survey reminder IDs and pending rows in single-pass collections (roadmap 3+7)

context: `BellDashboardViewModel.handleDueReminders` chains `flatMap`, `filter`, and `distinct` for all IDs, then chains `mapNotNull` and `filter` for every reminder group (`BellDashboardViewModel.kt:68-83`). Reminder delivery runs on UI-owned coroutine state, so eliminating intermediate lists lowers churn without changing repository calls; keeping the transformation in the ViewModel also supports state hoisting for north-star 10.

files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt` — `BellDashboardViewModel.handleDueReminders`. Leave network observation, completed-course checks, repositories, fragments, and `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModelTest.kt` unchanged.

steps:
1. Build all nonblank survey IDs into an insertion-ordered set in one traversal of reminder strings.
2. Pass those IDs to the existing bulk submission lookup without changing their encounter order.
3. Build each reminder group's pending submission list in one pass, combining lookup and status filtering.
4. Preserve duplicate suppression for the bulk query, per-group order, empty-group skipping, and emitted prompt contents.
5. Run the focused ViewModel tests and the full default unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.dashboard.BellDashboardViewModelTest'` and `./gradlew testDefaultDebugUnitTest` pass. Duplicate and blank reminder IDs still cause one bulk fetch, and each prompt contains only pending submissions in reminder order.

size budget: about 20 changed lines in 1 file.

out of scope: Do not change reminder scheduling, prompt timing, repository contracts, or introduce Compose UI.

---

### 7. Reorder the server list without parallel pair collections (roadmap 2+7)

context: `SyncActivity.refreshServerList` maps every server to a `Pair`, searches that list, then filters and maps it again solely to pin one server (`ServerDialogExtensions.kt:116-143`). This runs whenever server choices refresh and creates several short-lived collections; a direct index lookup plus a single mutable copy preserves navigation-adjacent UI behavior with less churn.

files: `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerDialogExtensions.kt` — `refreshServerList`. Leave `SyncActivity`, `ServerAddressAdapter`, `ServerConfigUtils`, protocol listeners, and adapter tests unchanged.

steps:
1. Find the pinned server index directly in `filteredList` by applying the existing protocol-stripping regex.
2. When pinning is enabled and a match exists, make one mutable list copy and move that existing element to index zero.
3. Otherwise submit `filteredList` unchanged, preserving its object identities and ordering.
4. Reuse the computed match state in the submit callback instead of searching a derived pair list again.
5. Run sync UI unit tests and the required complete unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.ui.sync.ServerAddressAdapterTest'` and `./gradlew testDefaultDebugUnitTest` pass. The configured server remains selected and appears first only when additional servers are shown, while the list scrolls to the same server in all other modes.

size budget: about 18 changed lines in 1 file.

out of scope: Do not introduce the global navigation host, change URL normalization, alter pin persistence, or redesign the server dialog.

---

### 8. Short-circuit crash-log capacity checks (roadmap 7+8)

context: `CrashLogStore.save` parses every file in the pending-log directory via `count` even though it only needs to know whether 20 valid reports exist (`CrashLogStore.kt:36-42`). Crash and ANR persistence is latency-sensitive, so stopping at the cap avoids unnecessary filename parsing while retaining this deliberately synchronous safety path.

files: `app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt` — `CrashLogStore.save` capacity check. Leave filename parsing, report contents, loading behavior, `TimeProvider`, and `app/src/test/java/org/ole/planet/myplanet/utils/CrashLogStoreTest.kt` unchanged.

steps:
1. Convert the nullable `listFiles` result to a lazy sequence only for the capacity check.
2. Filter with the existing `isValidLogFile` predicate and stop inspection as soon as `MAX_PENDING_FILES` valid entries are observed.
3. Preserve the rule that unrelated or malformed files do not consume capacity.
4. Preserve exception handling and the exact create-or-return-null behavior.
5. Run the crash-store tests and the full unit suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.utils.CrashLogStoreTest'` and `./gradlew testDefaultDebugUnitTest` pass. Saving still succeeds below 20 valid logs, returns null at the cap, and ignores malformed neighboring files.

size budget: about 6 changed lines in 1 file.

out of scope: Do not make crash persistence asynchronous, change the cap, rotate existing reports, or alter report naming.

---

### 9. Stream news sync ID collection into the existing lookup (roadmap 1+5+7)

context: `VoicesRepositoryImpl.insertNewsList` first creates a list of document/ID pairs, then maps that whole list again and filters it to obtain lookup IDs (`VoicesRepositoryImpl.kt:358-365`). The mapping already removed an N+1 query, but it still allocates two intermediate collections on potentially hundreds of news records; tightening it continues the platform-neutral sync cleanup envisioned by north-star 9.

files: `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt` — `VoicesRepositoryImpl.insertNewsList`. Leave `buildNewsFromJson`, post editing, preference writes, `NewsDao`, and repository tests unchanged.

steps:
1. Keep one ordered document/ID representation so `_id` is parsed only once per document.
2. Populate the nonblank underscore-ID query list during that same traversal rather than with a second `map` plus `filter` pipeline.
3. Preserve duplicate IDs, blank-ID handling, input order, the single bulk DAO lookup, and `associateBy` conflict behavior.
4. Continue building every `News` item through `buildNewsFromJson` and performing one `upsertAll`.
5. Run news-sync and repository-focused tests followed by the full suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.VoicesRepositoryNewsSyncTest' --tests 'org.ole.planet.myplanet.repository.VoicesRepositoryImplTest'` and `./gradlew testDefaultDebugUnitTest` pass. Syncing news with blank or duplicate `_id` fields persists the same ordered entities and performs the same single existing-row query.

size budget: about 15 changed lines in 1 file.

out of scope: Do not change image removal, JSON schema interpretation, DAO signatures, realtime emissions, or concatenated-link persistence.

---

### 10. Pre-size upload result buckets for exam batches (roadmap 5+7)

context: `UploadRepositoryImpl.markExamsUploaded` creates default-capacity mutable lists and grows them while partitioning every successful network result into updated exams or local lookup failures (`UploadRepositoryImpl.kt:63-85`). Upload batches can be large, so capacity hints remove backing-array growth while leaving the established bulk lookup and write workflow intact; the partition itself remains plain Kotlin for eventual north-star 9 extraction.

files: `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt` — `UploadRepositoryImpl.markExamsUploaded`. Leave API calls, `ExamDao`, resource upload handling, upload result models, and `app/src/test/java/org/ole/planet/myplanet/repository/UploadRepositoryImplTest.kt` unchanged.

steps:
1. Allocate the updated-exam bucket with capacity equal to the incoming `succeeded` count.
2. Allocate the failure bucket with `minOf(succeeded.size, 16)` capacity to help mixed-result batches without reserving another full batch.
3. Use explicit `add` operations so no operator ambiguity can create replacement collections.
4. Preserve input ordering, missing-local-row reporting, revision assignment, and the single `upsertAll` call.
5. Run the focused upload repository tests and the full required suite.

acceptance: `./gradlew testDefaultDebugUnitTest --tests 'org.ole.planet.myplanet.repository.UploadRepositoryImplTest'` and `./gradlew testDefaultDebugUnitTest` pass. A mixed batch still updates every found exam once, returns missing items in input order, and skips the DAO write for an empty update bucket.

size budget: about 8 changed lines in 1 file.

out of scope: Do not change upload batching, retry policy, response parsing, repository interfaces, or add benchmark infrastructure.
