### R001 · 96/100 · Resolve My Life visibility updates to one persisted row

Replace the unrestricted ID/image/title OR update with a transaction that prefers an exact primary key, otherwise accepts only one unique alias match, and updates by that resolved key. Treat ambiguous or missing aliases as no-ops. Preserve the two-argument DAO API and test cross-user alias collisions.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt:33](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt#L33); [app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt:52](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/life/LifeFragment.kt#L52).

---

### R002 · 95/100 · Pair each exported submission with its own exam questions

Resolve each submission's exam ID, fetch distinct exams' questions in batches, and select the matching question list inside the submission render loop. Preserve submission/answer ordering, parent-ID parsing, labels and pagination; cover mixed-exam, same-exam and missing-parent inputs.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:184](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt#L184); [app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:205](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt#L205); [app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt:305](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt#L305).

---

### R003 · 93/100 · Publish local course-step content before network reachability completes

Publish the loaded title, markdown, resources and exam state immediately, then patch download state after reachability completes. Keep network work owned by the active step-load job so cancellation or switching steps prevents stale results and downloads.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:61](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L61); [app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:86](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L86); [app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt:108](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt#L108).

---

### R004 · 93/100 · Clear obsolete Glide requests before empty-image placeholders

Clear the target view's previous Glide request before setting the empty-source placeholder in loadProfileImage and loadImage. Preserve all nonempty-source options and prevent a recycled row's earlier request from overwriting its placeholder.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt:10](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt#L10); [app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt:25](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/ImageUtils.kt#L25).

---

### R005 · 91/100 · Cancel superseded patient-list loads

Track initial loadPatients work with the same ownership as searchPatients so a newer search/sort request cancels older list work. Reset loading state in cancellation-safe cleanup and ensure a stale initial load cannot overwrite the newest query result.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt:95](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt#L95); [app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt:101](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt#L101).

---

### R006 · 86/100 · Avoid replay-driven chat reloads on every lifecycle restart

Collect refresh signals once per fragment view rather than restarting the collector on every STARTED transition, while retaining replay for late subscribers. Fetch independent news/history data concurrently and build shared-view IDs on the default dispatcher. Preserve view-destruction cancellation, search state and sharing behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt:205](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt#L205); [app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:67](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L67); [app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:139](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L139).

---

### R007 · 86/100 · Enforce team read/delete bind budgets inside TeamDao

Wrap TeamDao getByIds/deleteByIds with empty-safe deduplicated chunks of at most 900 IDs. Flatten reads and sum delete counts, and wrap multi-chunk deletion in a Room transaction. Preserve public signatures and validate duplicate, unknown and unrelated IDs.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt:13](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt#L13); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt:35](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt#L35); [app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt:134](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L134).

---

### R008 · 86/100 · Limit UrlUtils object mocking to the tests that need it

Remove the per-test blanket UrlUtils object mock and scope instrumentation to the two getUrl-stubbing tests with explicit cleanup. Retain resetForTesting/init before every test and clean up the local Uri mock. Preserve real-method and cache assertions.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt:28](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt#L28); [app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt:98](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt#L98); [app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt:131](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt#L131).

---

### R009 · 86/100 · Use the clicked chat row for its accessibility title

Remove the adapter-wide chatTitle field. Compute the clicked card's accessibility description from that row's first query or fallback title, so binding a second row cannot change the title used when the first row is clicked. Retain existing share-dialog behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt:96](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt#L96); [app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt:131](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryAdapter.kt#L131).

---

### R010 · 85/100 · Replace full-table team scans with targeted membership reads

Read only membership rows and eligible root teams needed for the current user and team type. Preserve Kotlin isNullOrBlank root semantics, pending-delete/archive filtering and enterprise behavior. Batch leave-membership deletes/upserts transactionally using bind-safe queries rather than per-row calls.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt:209](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L209); [app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt:295](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L295); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt:19](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt#L19); [app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt:1367](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt#L1367).

---

### R011 · 85/100 · Reduce MyLibrary DAO fixture writes while retaining chunk boundaries

Keep 1,200-element request lists and seed sparse matching rows at indices 1, 900, 901 and 1200. Preserve row ordering, exclusion, duplicate handling and offline-flag assertions, including unrelated rows. Consolidate only scenarios with equivalent assertions.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt:38](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt#L38); [app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt:224](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt#L224); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt:38](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt#L38).

---

### R012 · 85/100 · Avoid duplicate patient-dialog loads and repeated RecyclerView setup

Use one initial patient-list request matching the spinner's selected sort order. Initialize the records RecyclerView layout manager and adapter once per view, then update adapter data on patient-state changes. Preserve search, record refresh and view-recreation behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt:272](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L272); [app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt:294](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L294); [app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt:194](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt#L194).

---

### R013 · 85/100 · Hide the keyboard once per touch-down with one shared tree listener

Create one listener for each setupUI traversal and share it across eligible views. Invoke hideSoftKeyboard only on ACTION_DOWN and return false so gestures propagate. Preserve EditText exclusions and child traversal.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt:20](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt#L20).

---

### R014 · 85/100 · Coalesce inline-resource payloads into one preview update

Flatten and combine address/status/title payloads before binding; update the title once and refresh preview/status at most once for the final resource. Preserve full-bind fallback, click behavior and cancellation of the previous preview job.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt:113](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L113); [app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt:122](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt#L122).

---

### R015 · 85/100 · Reuse adaptive text reveal for long voice replies

Delegate typing updates to TextRevealer.reveal while retaining the supplied scope/main dispatcher, cancellation closure and completion callback. Verify empty strings, long replies and cancellation do not emit extra completion callbacks.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt:17](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt#L17); [app/src/main/java/org/ole/planet/myplanet/utils/TextRevealer.kt:14](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/TextRevealer.kt#L14).

---

### R016 · 84/100 · Reduce opened-resource flow rows and skip unused cached sorting

Return one nonnull resource-ID representative per user/type from the DAO flow, preserving the repository's resulting ID set. Guard cached sorting so it runs only when its output initializes empty resource state. Preserve other activity queries and resource sorting behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt:29](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/ResourceActivityDao.kt#L29); [app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt:554](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L554); [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:97](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L97).

---

### R017 · 84/100 · Reuse UploadConfigsTest object instrumentation

Instrument VersionUtils and NetworkUtils once per class. Construct UploadConfigs and establish fresh answers and call histories per test; remove instrumentation at class teardown. Preserve upload payload and invocation assertions.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt:44](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt#L44); [app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt:73](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/services/upload/UploadConfigsTest.kt#L73).

---

### R018 · 84/100 · Prepare medium criteria once and short-circuit rejected resources

Canonicalize selected medium criteria once per matching call and return immediately when an earlier facet rejects a resource. Preserve existing equals(ignoreCase=true) behavior for subject, level and language, and preserve all book/non-book and unknown-medium rules.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:54](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt#L54); [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:71](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt#L71); [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt:86](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesListFilter.kt#L86).

---

### R019 · 83/100 · Move exam textarea markdown preparation off the UI thread

Use the existing Markwon editor pre-render API with a lifecycle-owned executor for textarea answers. Remove old watchers and stop obsolete work on view teardown, prevent late writes to destroyed views, and preserve text/cursor/answer submission behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt:185](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/base/BaseExamFragment.kt#L185).

---

### R020 · 83/100 · Reuse DownloadServiceTest static instrumentation

Instrument ContextCompat, DownloadUtils and Log once per class; reset stubs and recordings per test. Explicitly clean up test-local WorkManagerImpl and NotificationManagerCompat mocks, and preserve the SDK_INT restoration after each test.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt:59](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt#L59); [app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt:127](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt#L127).

---

### R021 · 83/100 · Remove incidental Robolectric API 27 and 28 sandboxes

Run SecurePrefsTest and PdfThumbnailLoaderTest on the existing default SDK and remove the API-28-only annotation from the VersionUtils post-P branch test. Preserve the API-26 pre-P case and restore any pin proven necessary by focused tests. Keep unrelated SDK annotations and the staged jar map unchanged.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/utils/SecurePrefsTest.kt:20](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/SecurePrefsTest.kt#L20); [app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt:21](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/PdfThumbnailLoaderTest.kt#L21); [app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt:179](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt#L179).

---

### R022 · 83/100 · Reduce answer fixture writes while preserving deletion counts

Seed sparse answer rows for large submission-ID requests. Keep requests crossing the 900-ID boundary, duplicate IDs across chunks, exact affected-row counts, empty inputs, and an unrelated surviving answer.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt:44](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt#L44); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt:12](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt#L12).

---

### R023 · 83/100 · Use sparse exam fixtures for large request lists

Seed boundary matches for the five dense exam-query cases while retaining 1,200 distinct request values, duplicate inputs, team-owned survey union behavior and unrelated control rows. Consolidate only the duplicated course lookup scenario.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/dao/ExamDaoTest.kt:36](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/ExamDaoTest.kt#L36); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt:12](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt#L12).

---

### R024 · 83/100 · Use sparse rows for large underscore-ID news lookups

Keep 1,200 underscore-ID requests while seeding rows at positions 1, 900, 901 and 1200. Preserve distinct primary/remote IDs, empty-input behavior and exact returned identities.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/dao/NewsDaoTest.kt:323](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/NewsDaoTest.kt#L323); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt:12](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt#L12).

---

### R025 · 83/100 · Use progress fixtures at the actual tuple and bind boundaries

Retain large request lists but seed matches around tuple positions 250/251 and course positions 899/900. Preserve null-user behavior, exact tuple matching and cross-product exclusion controls.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDaoTest.kt:91](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDaoTest.kt#L91); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDao.kt:12](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDao.kt#L12).

---

### R026 · 83/100 · Use sparse removed-log fixtures with null-user isolation

Keep large document-ID inputs but seed only chunk-boundary targets and type/user controls. Preserve null-user matching, duplicate handling and evidence that rows of another type or user survive.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt:85](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt#L85); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt:12](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt#L12).

---

### R027 · 83/100 · Use sparse task fixtures across the 900-key boundary

Seed task rows at indices 1, 900, 901 and 1200 for ID, title and notified-flag cases. Keep full request lists, duplicate inputs, exact matched identities and an unrelated unmodified control row.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamTaskDaoTest.kt:36](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamTaskDaoTest.kt#L36); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamTaskDao.kt:12](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamTaskDao.kt#L12).

---

### R028 · 83/100 · Reduce team-visit fixtures while retaining cutoff and scope coverage

Keep 1,200-element team/user request lists but seed boundary matches and rows at, below and above the cutoff. Preserve visit type, requested-team and user scoping, plus exact returned identities.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamLogDaoTest.kt:37](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamLogDaoTest.kt#L37); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt:12](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt#L12).

---

### R029 · 83/100 · Add enforceable suite and class timing budgets

Extend test_timing_summary.py with optional --fail-over and --fail-class-over exit statuses, package totals, and an always-visible budget footer. Preserve advisory flags and default exit behavior. Document that totals sum test seconds across forks; they are not job wall time. Exercise under-budget, over-budget, missing-result and malformed-result inputs without changing workflow invocation.

Verified locations: [.github/scripts/test_timing_summary.py:66](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/.github/scripts/test_timing_summary.py#L66); [.github/scripts/test_timing_summary.py:153](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/.github/scripts/test_timing_summary.py#L153).

---

### R030 · 82/100 · Probe primary and alternate reachability concurrently

Probe distinct candidate URLs concurrently and return on the first successful result, cancelling and closing remaining OkHttp calls. Keep HEAD-to-GET fallback, response closure, exception/cancellation semantics and cache TTL. Verify a slow primary cannot delay a fast successful alternate.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt:31](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt#L31); [app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt:80](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/ServerReachabilityProvider.kt#L80).

---

### R031 · 82/100 · Coalesce burst survey refreshes

Filter for refresh-worthy exam updates before debouncing the SurveysViewModel collector. Keep the initial load immediate, preserve the final refresh after a burst, and retain existing survey filtering and visibility rules.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt:99](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt#L99); [app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt:84](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt#L84).

---

### R032 · 82/100 · Stop CSV preview reads after the fifth record

Check the five-row limit before requesting another CSV record. Preserve output formatting, multiline CSV semantics, cache behavior and resource closure; malformed records beyond the fifth displayed row must not invalidate the preview.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt:65](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt#L65).

---

### R033 · 81/100 · Record one visit per course-open session

Deduplicate visit logging for the same course/user within a course-open session, allowing a new visit for a new session or different course/user. Mark success after the repository write and prevent concurrent duplicates; retain retry after failure and ordinary lifecycle refreshes.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:57](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt#L57); [app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt:241](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseFragment.kt#L241).

---

### R034 · 81/100 · Remove the assertion-free StepExam benchmark

Delete StepExamBenchmarkTest.kt, which maps 500 documents ten times, discards the results, and only prints elapsed time. Retain the mapper assertions in StepExamTest.kt.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt:31](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/model/StepExamBenchmarkTest.kt#L31); [app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt:60](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt#L60).

---

### R035 · 80/100 · Coalesce chat sync refresh signals

Filter refresh-worthy chat updates before debouncing their refresh signal. Keep the cold-start signal and replay behavior so a late subscriber receives the latest update; validate burst and late-subscriber cases with the existing test scheduler.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:73](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L73); [app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:136](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L136).

---

### R036 · 80/100 · Exclude stale reports from the build cache and retry stale Kotlin caches

Exclude app/build/test-results/** and app/build/reports/** from the build workflow cache. Add one narrowly triggered retry for the existing incremental-compilation failure pattern, preserving the first failure status for all other errors and preserving flavor selection. Validate the workflow YAML and shell failure propagation.

Verified locations: [.github/workflows/build.yml:41](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/.github/workflows/build.yml#L41).

---

### R037 · 80/100 · Coalesce burst team-detail refreshes

Debounce refresh-worthy team updates inside the existing lifecycle-aware team-detail collector while preserving the immediate initial detail load and the final update. Keep membership state and leadership behavior unchanged.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt:416](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt#L416); [app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt:263](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamDetailFragment.kt#L263).

---

### R038 · 80/100 · Enforce user-lookup bind budgets inside UserDao

Deduplicate and chunk any-ID lookups at 450 inputs because each appears in two IN predicates; chunk guest-name lookups at 900. Deduplicate combined users by local ID, preserve distinct users sharing a name, and retain guest-prefix/case behavior and empty-input short-circuits.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt:13](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt#L13); [app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt:45](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt#L45).

---

### R039 · 79/100 · Remove unused Feedback mocks and reuse model-test instrumentation

Remove the unused FeedbackTest TextUtils mock and reuse NetworkUtils instrumentation across FeedbackTest and MeetupTest. Keep fresh identifier stubs and histories per test, clean up local JsonParser instrumentation, and retain MeetupTest Robolectric coverage.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/model/FeedbackTest.kt:24](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/model/FeedbackTest.kt#L24); [app/src/test/java/org/ole/planet/myplanet/model/MeetupTest.kt:25](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/model/MeetupTest.kt#L25); [app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt:1](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/model/Feedback.kt#L1).

---

### R040 · 78/100 · Reuse BasePermissionActivityTest static instrumentation

Hoist ContextCompat and Process instrumentation to class setup while keeping fresh per-test stubs and call records. Preserve permission cases and explicitly retain cleanup of NotificationManagerCompat and ActivityCompat mocks created inside individual tests.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/base/BasePermissionActivityTest.kt:24](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/base/BasePermissionActivityTest.kt#L24); [app/src/test/java/org/ole/planet/myplanet/base/BasePermissionActivityTest.kt:36](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/base/BasePermissionActivityTest.kt#L36).

---

### R041 · 77/100 · Reuse DownloadUtilsTest object instrumentation without leaking state

Instrument Utilities, DownloadService.Companion and DownloadUtils once per class. Keep context/preferences setup and channel resets per test. Clear object stubs where tests currently unmock to reach real code, reinstall private-call answers where needed, and clean up all class-owned instrumentation afterwards.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt:37](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt#L37); [app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt:208](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt#L208).

---

### R042 · 76/100 · Reuse NotificationActionReceiverTest static instrumentation

Hoist NotificationUtils and ServiceDependenciesEntryPointKt instrumentation to class setup. Keep receiver/context spies, Hilt injection reflection, answers and call histories fresh per test; remove instrumentation in class teardown.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt:51](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt#L51); [app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt:85](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt#L85).

---

### R043 · 76/100 · Throttle FreeSpaceWorker progress publication

Publish cleanup progress at a bounded time/count cadence and once on completion instead of every ten deleted entries. Preserve cancellation, cv-directory exclusion, deletion/freed-byte accounting and batched offline-flag updates.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt:66](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt#L66); [app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt:77](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt#L77).

---

### R044 · 76/100 · Make the closed-port reachability test fail promptly

Replace the bound-but-never-started HttpServer fixture in the closed-port case with a localhost endpoint verified to refuse connections in the test environment. Preserve assertFalse and the responding-server cases; do not change production network timeouts. Record the closed-port case duration.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/services/sync/ServerUrlMapperReachabilityTest.kt:50](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/services/sync/ServerUrlMapperReachabilityTest.kt#L50); [app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt:115](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt#L115).

---

### R045 · 75/100 · Share primary/alternate URL probing between upload callers

Extract the repeated URL mapping/probe/preference-update sequence into ServerUrlMapper with an explicit result. Preserve per-probe timeouts, exception/cancellation behavior and preference updates. Keep each caller's distinct total-failure policy: the worker still uploads, while the uploader skips.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/services/ServerReachabilityWorker.kt:167](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/services/ServerReachabilityWorker.kt#L167); [app/src/main/java/org/ole/planet/myplanet/services/SubmissionsUploader.kt:26](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/services/SubmissionsUploader.kt#L26); [app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt:56](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt#L56).

---

### R046 · 75/100 · Correct unnecessary Log-mocking guidance

Correct both Log-mocking warnings and the worker example to reflect unitTests.returnDefaultValues=true. Explain when logging interaction assertions still require a mock and document class-owned instrumentation with per-test stub/history isolation.

Verified locations: [docs/TESTING.md:341](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/docs/TESTING.md#L341); [docs/TESTING.md:468](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/docs/TESTING.md#L468); [app/build.gradle:22](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/build.gradle#L22).

---

### R047 · 74/100 · Own examination signature encryption in HealthRepository

Move signature encryption/key preparation into the repository save boundary and pass the sign payload through its API. Preserve current nullable-user behavior, key/IV reuse, cancellation and failure semantics, and retain encryption before user/examination persistence.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:112](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt#L112); [app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt:128](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt#L128); [app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt:80](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt#L80).

---

### R048 · 74/100 · Diff join-request rows together with capacity and leadership state

Use immutable row snapshots carrying the user and the capacity/leader/limit values needed for request actions. Derive changes and payloads from those snapshots, preserve the public setData API, and remove the unconditional full-range rebind after a list diff. Retain guest and accept/reject rules.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt:36](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt#L36); [app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt:55](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt#L55).

---

### R049 · 73/100 · Move bell dashboard URL mapping and fallback probing into its ViewModel

Give BellDashboardViewModel a mapping-aware connection check and let the fragment only render its result. Preserve primary/alternate order, preference reads, network indicator state and lifecycle cancellation; remove the fragment's now-unused mapper dependency.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt:109](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L109); [app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt:123](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt#L123); [app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt:136](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt#L136).

---

### R050 · 73/100 · Bound growing adapter date caches

Replace the four growing timestamp-to-string maps with bounded access-order caches. Preserve locale/date formatting and clear cached strings when relevant configuration changes; omit MembersAdapter, which has no such cache.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt:23](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt#L23); [app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt:36](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt#L36); [app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt:17](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt#L17); [app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt:28](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt#L28).

---

### R051 · 73/100 · Guard dashboard survey clicks and set invariant color once

Set the invariant survey text color during holder creation and ignore clicks with NO_POSITION before invoking the callback or dismissing the dialog. Retain ReferencesAdapter's required row-specific image and navigation binding.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapter.kt:24](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapter.kt#L24); [app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapter.kt:42](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardSurveysAdapter.kt#L42).

---

### R052 · 72/100 · Reuse static instrumentation in logger and utility tests

Move repeated Log, Looper, Toast and Uri instrumentation to class setup in the three named test files. Keep differing stubs and call-history reset per test, clean up test-local mocks, and remove instrumentation in class teardown. Preserve test isolation rather than retaining per-test unmockkAll on class-owned mocks.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt:20](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt#L20); [app/src/test/java/org/ole/planet/myplanet/utils/UtilitiesTest.kt:26](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/UtilitiesTest.kt#L26); [app/src/test/java/org/ole/planet/myplanet/utils/WebViewSafetyTest.kt:18](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/WebViewSafetyTest.kt#L18).

---

### R053 · 72/100 · Normalize dictionary lookup input at the repository boundary

Trim lookup input in DictionaryRepositoryImpl and short-circuit empty normalized words. Keep the ViewModel's blank-input no-op, delegate its nonblank raw input, and preserve DAO case matching and dictionary-field mapping.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt:25](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt#L25); [app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt:57](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt#L57).

---

### R054 · 72/100 · Narrow chat user dependencies and fixture capabilities

Replace broad UserRepository constructor dependencies and corresponding existing test mocks with UserReadRepository. Preserve all forwarded IDs, nullable-user behavior, public methods, state and events; keep the existing DI binding. Match team/voice fixture mocks to the existing TeamsShareRepository and VoicesShareRepository constructor types.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt:53](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt#L53); [app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt#L5).

---

### R055 · 71/100 · Move achievement-entry state and serialization into existing non-view owners

Build achievement entries through an Achievement helper and hold add/remove/edit state in AchievementViewModel. Rewire rendering and saves to that state, preserving initialization on edit, resource lists and reference handling across view recreation.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt:268](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L268); [app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt:404](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt#L404); [app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt:31](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt#L31).

---

### R056 · 71/100 · Centralize duplicated search-activity writes and removed-log reads

Add the needed search-recording and removed-log read capabilities to ActivitiesRepository, delegate the duplicated callers, and remove the unused UserRepositoryImpl offlineActivityDao dependency. Preserve timestamps, activity payloads and the existing lazy injection boundary.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt:341](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt#L341); [app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt:477](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt#L477); [app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt:81](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt#L81); [app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt:60](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt#L60).

---

### R057 · 71/100 · Diff member menu state and reuse menu text color

Include leadership/menu-availability values in immutable member row snapshots so their changes generate the existing payload instead of a post-diff full-range notification. Preserve updateData's public API and member actions. Resolve MemberMenuAdapter's text color once per adapter.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt:182](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt#L182); [app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt:200](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersAdapter.kt#L200).

---

### R058 · 71/100 · Remove the unreachable automatic diff-refresh hook

Remove OnDiffRefreshListener, the helper cast path, and getSyncRecyclerView/shouldAutoRefresh members and overrides. Preserve onDataUpdated callbacks and the existing flow filters/debounce; both implementers already opt out and no adapter implements the listener.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt:15](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L15); [app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt:39](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt#L39); [app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt:566](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt#L566); [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt:800](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt#L800).

---

### R059 · 71/100 · Build Gson tag-ID arrays directly

Construct the required Gson JsonArray directly in getTagsArray instead of constructing and converting a kotlinx JSON tree. Preserve ID order, duplicate IDs, nulls and empty-list behavior, and remove only unused bridge imports.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/model/TagEntity.kt:63](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/model/TagEntity.kt#L63).

---

### R060 · 70/100 · Share draft-image serialization and duplicate detection

Use shared model helpers for imageUrl/fileName entry serialization and duplicate-path checks in BaseVoicesFragment and ReplyActivity. Preserve serialized keys, filename extraction, malformed-entry handling and existing draft/image UI behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/base/BaseVoicesFragment.kt:161](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/base/BaseVoicesFragment.kt#L161); [app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt:222](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt#L222).

---

### R061 · 70/100 · Skip unchanged team page configurations

Return from updatePages when the new configuration equals the current pages. Preserve stable IDs, changed-config diff dispatch, fragment creation and current-ID rebuilding for real changes.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt:37](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt#L37).

---

### R062 · 69/100 · Remove version tests subsumed by cache scenarios

Delete the standalone version-code NameNotFoundException, version-name NameNotFoundException and successful version-name cases whose results are already asserted by stronger cache/reset tests. Preserve both SDK branch tests and all cache retry/reset assertions.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt:61](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt#L61); [app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt:90](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt#L90); [app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt:146](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/VersionUtilsTest.kt#L146).

---

### R063 · 68/100 · Route cross-feature exam and submission reads through repository capabilities

Expose the required read capabilities from SubmissionsRepository and delegate the identified Surveys/Progress reads through them. Preserve batching, null/ID semantics and result mapping; remove a DAO dependency only after all remaining uses are accounted for, and keep dependency cycles behind existing lazy/provider boundaries.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt:207](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt#L207); [app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt:65](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L65); [app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt:186](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt#L186); [app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:43](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt#L43).

---

### R064 · 68/100 · Add evidence-labelled mock-churn hints to timing reports

Augment slow-class timing output with optional source-based hints when per-test setup instruments objects/statics and teardown unmocks them. Label this as a heuristic, avoid claiming causality from timing alone, and preserve existing tables and flags.

Verified locations: [.github/scripts/test_timing_summary.py:139](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/.github/scripts/test_timing_summary.py#L139); [app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt:28](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/UrlUtilsTest.kt#L28).

---

### R065 · 68/100 · Limit feedback-composer user access to reads

Replace broad UserRepository constructor dependencies and corresponding existing test mocks with UserReadRepository. Preserve all forwarded IDs, nullable-user behavior, public methods, state and events; keep the existing DI binding.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackComposerViewModel.kt:20](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackComposerViewModel.kt#L20); [app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt#L5).

---

### R066 · 68/100 · Limit membership ViewModel user access to reads

Replace broad UserRepository constructor dependencies and corresponding existing test mocks with UserReadRepository. Preserve all forwarded IDs, nullable-user behavior, public methods, state and events; keep the existing DI binding.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt:52](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt#L52); [app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt#L5).

---

### R067 · 68/100 · Limit course-progress ViewModel user access to reads

Replace broad UserRepository constructor dependencies and corresponding existing test mocks with UserReadRepository. Preserve all forwarded IDs, nullable-user behavior, public methods, state and events; keep the existing DI binding. Preserve ProgressViewModel local-id forwarding and CourseProgressViewModel remote-_id forwarding.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt:18](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressViewModel.kt#L18); [app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModel.kt:17](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressViewModel.kt#L17); [app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt#L5).

---

### R068 · 68/100 · Limit resource creation/detail user access to reads

Replace broad UserRepository constructor dependencies and corresponding existing test mocks with UserReadRepository. Preserve all forwarded IDs, nullable-user behavior, public methods, state and events; keep the existing DI binding.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceViewModel.kt:18](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/AddResourceViewModel.kt#L18); [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailViewModel.kt:15](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailViewModel.kt#L15); [app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt:5](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/repository/UserReadRepository.kt#L5).

---

### R069 · 67/100 · Reuse the DashboardActivity TabLayout reference

Use the existing tl reference in topBarVisible instead of finding the TabLayout on every global layout. Avoid writing unchanged visibility values and preserve orientation-sensitive behavior and listener cleanup.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt:277](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L277); [app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt:800](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardActivity.kt#L800).

---

### R070 · 67/100 · Sort resource title decorations in place

Build a mutable title-key decoration list and sort that newly allocated list in place before projecting models. Preserve stable ordering for equal and null titles, ascending/descending behavior and input-list immutability.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:202](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L202).

---

### R071 · 66/100 · Reuse a navigation-test activity within one Hilt test invocation

Use the source proposal's single-test-method option to exercise all four navigation scenarios with one activity. Reset the back stack and execute pending transactions between independent scenarios, preserve every original assertion, and explicitly destroy the activity afterwards. Keep setup/injection within that Hilt test invocation.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivityNavigationTest.kt:47](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivityNavigationTest.kt#L47); [app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivityNavigationTest.kt:54](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivityNavigationTest.kt#L54); [app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivityNavigationTest.kt:68](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/ui/dashboard/DashboardElementActivityNavigationTest.kt#L68).

---

### R072 · 65/100 · Reuse the Room fixture within each round-trip test class

Build and close one in-memory database per round-trip class, with explicit table clearing between cases. Preserve all converter and query assertions. Verify the shared database remains compatible with Robolectric application resets before retaining the fixture change; close it explicitly after the class.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/data/room/AppDatabaseRoundTripTest.kt:35](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/AppDatabaseRoundTripTest.kt#L35); [app/src/test/java/org/ole/planet/myplanet/data/room/CollapsedEntitiesRoundTripTest.kt:41](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/data/room/CollapsedEntitiesRoundTripTest.kt#L41); [app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt:42](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt#L42).

---

### R073 · 65/100 · Use the existing MainDispatcherRule in three ViewModel test fixtures

Replace manual setMain/resetMain setup in ChatViewModelTest, ResourcesViewModelTest and SurveysViewModelTest with MainDispatcherRule using each fixture's existing dispatcher. Preserve scheduler sharing and test semantics; limit the change to these fixtures.

Verified locations: [app/src/test/java/org/ole/planet/myplanet/utils/MainDispatcherRule.kt:13](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/utils/MainDispatcherRule.kt#L13); [app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt:56](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt#L56); [app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt:41](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModelTest.kt#L41); [app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModelTest.kt:46](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/test/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModelTest.kt#L46).

---

### R074 · 64/100 · Reuse resource filter facets for unchanged content

Cache the facet result using immutable snapshots of IDs and the subject, level, language and medium fields. Invalidate on any contributing content change, retain display spelling and medium canonicalization, and bound cache retention to the latest snapshot. Compare the signature cost with rebuilding before claiming a speedup.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt:135](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt#L135).

---

### R075 · 63/100 · Reuse normalized collection names during repeated filtering

Use a small bounded fragment-local cache keyed by the raw parent-tag name for Locale.ROOT search normalization. Clear it on new tag data and view teardown, retain null-name behavior and do not retain tag entities or alter selection/expansion semantics.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt:110](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt#L110).

---

### R076 · 61/100 · Install the UsersAdapter row listener once per holder

Move the UsersAdapter click listener into holder creation, reading the current bindingAdapterPosition and retaining the NO_POSITION guard. Keep existing avatar-size caching; no HealthUsersAdapter size change is needed.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt:27](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt#L27); [app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt:18](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt#L18).

---

### R077 · 60/100 · Use tagged exception logging in FileUtils and KeyboardUtils

Use tagged errors for FileUtils and KeyboardUtils exceptions, preserving their existing fallbacks. Keep RetryUtils unchanged because its null result does not surface the swallowed exception; removing its final trace would lose the only error diagnostic.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt:255](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt#L255); [app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt:16](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt#L16); [app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt:31](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt#L31).

---

### R078 · 60/100 · Use tagged exception logging in shared base fragments

Replace printStackTrace in BaseTeamFragment and BaseContainerFragment with stable tags and operation-specific Log.e messages, retaining the existing fallback and exception behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt:62](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/base/BaseTeamFragment.kt#L62); [app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt:173](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/base/BaseContainerFragment.kt#L173).

---

### R079 · 60/100 · Use tagged exception logging in ExamTakingFragment

Replace the three printStackTrace calls with operation-specific Log.e messages and a stable tag. Preserve existing failure paths, toasts and coroutine behavior; do not claim that tagged logging removes stack-trace cost.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt:142](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L142); [app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt:177](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L177); [app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt:664](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/exam/ExamTakingFragment.kt#L664).

---

### R080 · 59/100 · Remove noisy UserInformationFragment debug messages

Remove repetitive happy-path dialog/submission debug messages and use one tag for retained diagnostics. Guard any retained interpolated debug messages with Log.isLoggable. Preserve the survey sync timer and upload behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt:92](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L92); [app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt:279](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/exam/UserInformationFragment.kt#L279).

---

### R081 · 58/100 · Bound repeated resource media-classification work

Cache classification by local address, remote address and media type in a bounded map. Preserve all extension and media fallback rules, avoid retaining mutable library entities, and verify changed fields miss the cache. Measure hit rate and classification cost before claiming a filtering speedup.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesMediaType.kt:20](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesMediaType.kt#L20); [app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesMediaType.kt:69](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesMediaType.kt#L69).

---

### R082 · 58/100 · Reuse survey action labels and the guest-user flag

Resolve adopt/take/record labels once for each holder/configuration and precompute the immutable guest-user flag. Preserve current label selection, survey adoption, hidden actions and submission-state behavior.

Verified locations: [app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt:109](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt#L109); [app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt:115](https://github.com/open-learning-exchange/myplanet/blob/897cdbdb42429b014ed9ff4dce688877b35a00a4/app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysAdapter.kt#L115).
