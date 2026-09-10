# Performance quick-win work orders

**Date:** 2026-09-10  
**Base commit:** `022ee7d1f50f1453b1741aee391f3be5c619b4eb`  
**Open PRs checked:** could not check open PRs (the checkout has no Git remote, `gh` is unauthenticated, and direct GitHub API access is blocked). The work orders therefore stay in ten isolated implementation/test pairs and avoid the roadmap's named high-blast-radius files (`AppDatabase.kt`, `SyncManager.kt`, and `TeamsRepositoryImpl.kt`).

### 1. Normalize each chat conversation in one traversal (roadmap 1+7+9)

context: `ChatRepositoryImpl.buildPrecomputedChats` traverses every conversation twice to create normalized question and response lists at `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt:151-152`. It also re-reads the first conversation to derive the title at lines 146-149, so long chat histories incur avoidable list traversal and normalization setup; combining the work makes this data transformation easier to move into a platform-free core for roadmap 9.

files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/ChatRepositoryImpl.kt` (`ChatRepositoryImpl.buildPrecomputedChats`) and `app/src/test/java/org/ole/planet/myplanet/repository/ChatRepositoryImplTest.kt` (`ChatRepositoryImplTest`). Leave `ChatRepository`, `ChatDao`, `ChatViewModel`, and all chat UI files alone.

steps:
1. Build the normalized question and response collections together in one traversal of each chat's conversations.
2. Reuse the first normalized question for the conversation-backed title, while retaining the existing fallback to the normalized chat title.
3. Preserve null conversation entries, output order, and the `PrecomputedChat` collection shapes expected by `fullConvoSearch`.
4. Extend the existing search tests with mixed null question/response entries and confirm title, question, and response modes return the same ordered matches.
5. Remove imports made unused by the change and keep normalization on `dispatcherProvider.default`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.ChatRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass. Searching a long chat by title, question, or response shows the same result ordering and no missing matches.

size budget: about 35 changed lines across 2 files; no dependency or schema changes.

out of scope: Do not cache normalized chats across calls, alter matching semantics, or change coroutine dispatchers.

---

### 2. Count passed course steps without intermediate collections (roadmap 1+7+9)

context: `ProgressRepositoryImpl.getCompletedCourses` filters, maps, and converts every course's progress records to a set at `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt:193-198` solely to obtain a unique count. A direct unique-step accumulation removes two temporary lists in a path used to build completion badges, and keeps the computation as platform-free Kotlin suitable for roadmap 9.

files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt` (`ProgressRepositoryImpl.getCompletedCourses`) and `app/src/test/java/org/ole/planet/myplanet/repository/ProgressRepositoryImplTest.kt` (`ProgressRepositoryImplTest.testGetCompletedCourses`). Leave `CoursesRepository`, `CourseProgressDao`, `CourseCompletion`, and course UI files alone.

steps:
1. Replace the filter-map-toSet chain with a single-pass accumulation of passed `stepNum` values.
2. Keep duplicate passed records counted once and ignore records whose `passed` value is false.
3. Preserve the existing nonzero-step, valid course ID, and valid title gates exactly.
4. Add coverage containing duplicate passed records and a failed record for the same course.
5. Keep repository return ordering unchanged and clean any now-unused imports.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.ProgressRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass. A completed-course badge still appears only when every course step has passed, including when duplicate progress rows exist.

size budget: about 25 changed lines across 2 files; no new files or dependencies.

out of scope: Do not alter progress persistence, completion rules, DAO queries, or course badge presentation.

---

### 3. Aggregate sync timing summaries without flattening logs (roadmap 5+7+9)

context: `SyncTimeLogger.generateSummary` copies all API and database log entries into two flattened lists at `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt:229-230`, then scans those copies repeatedly at lines 255-278 and 296-301. Sync sessions can contain many batches, so computing aggregate counters directly avoids peak allocations and moves the pure reporting logic toward the platform-free sync core in roadmap 9.

files: Touch `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt` (`SyncTimeLogger.generateSummary`) and `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt` (`SyncTimeLoggerTest.testGenerateSummary`). Leave `SyncManager`, logger call sites, upload timing, and log persistence alone.

steps:
1. Remove the two `flatten()` snapshots from summary generation.
2. Compute total duration, success count, and item totals by folding the existing map values without creating aggregate lists.
3. Reuse each computed aggregate for the statistics and performance-percentage sections instead of summing again.
4. Preserve endpoint/model ranking, formatting, labels, rounding, and empty-log behavior byte for byte.
5. Strengthen the summary test with multiple endpoints and database models, including a failed API call.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.utils.SyncTimeLoggerTest` and `./gradlew testDefaultDebugUnitTest` pass. A completed sync displays the same totals, percentages, per-endpoint ordering, and success/failure counts.

size budget: about 45 changed lines across 2 files; no public API changes.

out of scope: Do not redesign logging, persist timing data, modify timestamp formats, or touch sync orchestration.

---

### 4. Deduplicate notification lookup keys during collection (roadmap 3+7+10)

context: `NotificationsViewModel.loadNotifications` builds lists and then calls `distinct()` for task IDs, task titles, and join-request IDs at `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:85-98`. These transient collections are rebuilt on every notification refresh; collecting unique values directly reduces work while retaining ViewModel-owned state that keeps a future Compose screen portable for roadmap 10.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` (`NotificationsViewModel.loadNotifications`) and `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt` (`NotificationsViewModelTest.testLoadNotificationsExtractsRelevantTypesCaseInsensitivelyInOrder`). Leave `NotificationsRepository`, fragments, adapters, and Room notification DAOs alone.

steps:
1. Accumulate task IDs, task titles, and join-request IDs into insertion-ordered unique collections rather than map/filter lists followed by `distinct()`.
2. Keep task-date parsing exactly once per task notification and reuse `parsedTaskDates` when collecting titles.
3. Convert to the repository parameter type only at the lookup boundary if required, preserving first-seen order.
4. Extend tests with duplicate related IDs and duplicate parsed titles, capturing lookup arguments and their order.
5. Retain the current parallel `async` lookup structure and formatted notification order.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.notifications.NotificationsViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass. Refreshing notifications shows identical grouping, unread counts, task team names, and join-request details.

size budget: about 40 changed lines across 2 files; no new dependencies.

out of scope: Do not change repository queries, grouping UX, read-state rules, or convert the screen to Compose.

---

### 5. Index collection tags once when reconciling selections (roadmap 6+7+10)

context: `CollectionsFragment.onViewCreated` flattens all child lists and calls `find` over the combined list for every selected tag at `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt:70-73`. Building an ID lookup once changes reconciliation from repeated linear scans to linear work and reduces UI-thread allocations; keeping selection logic explicit also prepares state for later ViewModel hoisting under roadmap 10.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt` (`CollectionsFragment.onViewCreated`) and `app/src/test/java/org/ole/planet/myplanet/ui/resources/CollectionsFragmentTest.kt` (`CollectionsFragmentTest`). Leave `CollectionsViewModel`, `ResourcesTagsAdapter`, `TagEntity`, layouts, and resources alone.

steps:
1. Build a lookup from the loaded parent and child tags in a single pass without concatenating and flattening lists.
2. Reconcile `selectedItemsList` through that lookup while preserving its order and retaining the prior selected object when no loaded match exists.
3. Use the same identity semantics as `TagEntity.matches`; handle any IDs or attachment fields needed to avoid changing behavior.
4. Add focused reconciliation coverage for parent tags, child tags, stale selections, and selected-order preservation.
5. Keep adapter submissions and visibility changes exactly where they are today.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.resources.CollectionsFragmentTest` and `./gradlew testDefaultDebugUnitTest` pass. Opening the collections dialog keeps prior parent/child selections checked and in the same order, including selections absent from refreshed data.

size budget: about 50 changed lines across 2 files; no resource or dependency changes.

out of scope: Do not move the dialog to Compose, change tag identity rules, or modify repository loading.

---

### 6. Sort course titles without allocating lowercase copies (roadmap 3+7+10)

context: `CoursesViewModel.sortCourses` lowercases every title used by title sorting at `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt:75-81`. Case-insensitive comparison can avoid temporary lowercase strings, and retaining sorting in the ViewModel advances portable UI state for roadmap 10.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt` (`CoursesViewModel.sortCourses`) and `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesViewModelTest.kt` (`CoursesViewModelTest.testToggleSort_correctlySortsCourses`). Leave `CoursesRepository`, `CoursesFragment`, `CoursesAdapter`, models, and locale configuration alone.

steps:
1. Replace lowercase-key title sorts with ascending and descending comparators based on Kotlin's case-insensitive string ordering.
2. Preserve the current treatment of equal case-folded titles and the stable ordering supplied by Kotlin sorting.
3. Do not change date sorting or the cancellation/stale-result guard in `applySort`.
4. Expand sorting coverage with mixed-case titles whose original strings must remain unchanged.
5. Verify repeated sort toggles still alternate ascending and descending results.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.courses.CoursesViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass. Toggling title sort produces the same case-insensitive order without mutating displayed course titles.

size budget: about 25 changed lines across 2 files; no public state changes.

out of scope: Do not change default ordering, add locale-specific collation, or modify course filtering and loading.

---

### 7. Tokenize survey searches without temporary split lists (roadmap 3+7+10)

context: `SurveysViewModel.filter` materializes a split list and then a second normalized list for each search at `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt:164-167`. Using a sequence for tokenization removes one intermediate collection on every debounced query while keeping filtering in ViewModel state for a portable Compose UI under roadmap 10.

files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt` (`SurveysViewModel.filter`) and `app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModelTest.kt` (`SurveysViewModelTest.test normalized search behavior with diacritics and multi-tokens`). Leave `SurveysRepository`, survey fragments/activities, `Utilities.normalizeText`, and sort behavior alone.

steps:
1. Tokenize the query with `splitToSequence`, discard empty tokens, normalize once per token, and materialize only the final token collection.
2. Preserve whole-query normalization and the starts-with-before-contains result buckets.
3. Keep whitespace, diacritic, case-insensitive, and empty-query behavior unchanged.
4. Add test cases with repeated spaces, leading/trailing spaces, diacritics, and multiple tokens.
5. Remove imports made unused and retain work on `dispatcherProvider.default`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.ui.surveys.SurveysViewModelTest` and `./gradlew testDefaultDebugUnitTest` pass. Survey search returns the same ordered results while typing queries containing extra spaces or accented text.

size budget: about 25 changed lines across 2 files; no new dependencies.

out of scope: Do not change ranking, debounce timing, sorting, repository APIs, or migrate the screen.

---

### 8. Share resource attachment upload handling across result branches (roadmap 5+7+9)

context: `UploadManager.uploadResource` independently maps IDs, fetches libraries, builds an ID map, and iterates uploads for success and partial success at `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt:173-203`. Extracting one result-item path removes duplicated collection work and prevents the two upload outcomes from drifting, a small consolidation toward platform-free upload workflow logic for roadmap 9.

files: Touch `app/src/main/java/org/ole/planet/myplanet/services/UploadManager.kt` (`UploadManager.uploadResource` plus one private helper) and `app/src/test/java/org/ole/planet/myplanet/services/UploadManagerTest.kt` (`UploadManagerTest`). Leave `UploadCoordinator`, `PhotoUploader`, `ResourcesRepository`, network APIs, and notification strings alone.

steps:
1. Extract the shared succeeded-item attachment path used by both `UploadResult.Success` and `UploadResult.PartialSuccess`.
2. Return immediately for a null listener or empty item collection before allocating IDs or querying library items.
3. Fetch libraries once per result, index them once, and invoke `uploadAttachment` in result-item order.
4. Preserve each result branch's existing listener message and exception behavior.
5. Add tests for success and partial success asserting one library lookup, ordered attachment attempts, and no lookup when the listener is null.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.services.UploadManagerTest` and `./gradlew testDefaultDebugUnitTest` pass. Resource upload notifications and attachment uploads remain identical for full, partial, empty, and failed results.

size budget: about 60 changed lines across 2 files; no API or dependency changes.

out of scope: Do not alter retry policy, parallelize attachment uploads, change messages, or redesign `UploadResult`.

---

### 9. Select duplicate-user survivors without sorting each group (roadmap 1+7+9)

context: `UserRepositoryImpl.cleanupDuplicateUsers` groups duplicate rows, sorts every group using only two preferred ID prefixes, then drops and maps the remainder at `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:842-859`. A single-pass survivor selection avoids sorting and temporary tail lists in data cleanup, and isolating the deterministic selection rule supports eventual extraction to the platform-free repository core in roadmap 9.

files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt` (`UserRepositoryImpl.cleanupDuplicateUsers`) and `app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt` (`UserRepositoryImplTest`). Leave `UserDao`, authentication, username validation, saved-user preferences, and DI modules alone.

steps:
1. Replace per-name sorting with a single traversal that selects the same survivor: prefer an `org.couchdb.user:` row over a `guest_` row and otherwise retain first-seen order.
2. Collect all other IDs for that name directly without `drop(1).map` intermediates.
3. Keep one `deleteByIds` call per duplicate name group and skip deletion for singleton groups.
4. Add tests for member-versus-guest, guest-versus-member input order, equal-priority rows, and singleton input.
5. Preserve nullable-name grouping and existing prefix matching exactly.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.UserRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass. Duplicate cleanup retains the same preferred account and removes the same duplicate IDs regardless of member/guest row order.

size budget: about 55 changed lines across 2 files; no DAO or schema changes.

out of scope: Do not merge user fields, change duplicate detection SQL, modify authentication, or batch across name groups.

---

### 10. Accumulate offline resource metadata in one filesystem pass (roadmap 1+7+9)

context: `ResourcesRepositoryImpl.getOfflineResourceItems` first groups every matching `File`, then maps each group again to sum sizes and map paths at `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt:846-874`. Accumulating paths and byte totals during traversal removes a second scan and retained `File` collections on storage screens; the resulting plain metadata transformation also narrows work needed for roadmap 9, although filesystem access itself remains platform-specific.

files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt` (`ResourcesRepositoryImpl.getOfflineResourceItems`) and `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt` (`ResourcesRepositoryImplTest`). Leave `FileUtils`, storage UI classes, resource DAOs, deletion behavior, and title loading alone.

steps:
1. Introduce a private in-file accumulator holding mutable absolute paths and a running byte total for each resource ID.
2. Update that accumulator while walking matching files instead of retaining `File` objects for a later `sumOf` and `map`.
3. Convert each accumulator once to `OfflineResourceItem`, preserving title fallback and final title sort.
4. Add tests using nested temporary directories with included and excluded extensions, multiple files per resource, byte totals, and unknown titles.
5. Preserve missing-directory behavior and execution on `dispatcherProvider.io`.

acceptance: `./gradlew testDefaultDebugUnitTest --tests org.ole.planet.myplanet.repository.ResourcesRepositoryImplTest` and `./gradlew testDefaultDebugUnitTest` pass. Storage details show the same resource titles, file paths, sizes, category filtering, and alphabetical ordering.

size budget: about 65 changed lines across 2 files; no schema or dependency changes.

out of scope: Do not change deletion, follow symlinks differently, add filesystem caching, or move Android filesystem access into shared code.
