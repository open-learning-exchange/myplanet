## Bound text-viewer reads and parse Markdown off the main thread

**Rating:** 95/100

**Provenance:** Claude Opus 5.5 — performance list task 6; Copilot Kimi K3 — performance list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/MarkdownUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/MarkdownUtilsTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## stop reading profile-image URLs as local files in UserEntity.serialize

**Rating:** 94/100

**Provenance:** Claude Opus 5.5 — performance list task 1

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt`, `app/src/test/java/org/ole/planet/myplanet/model/UserEntityEncodeImageTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Chunk unbounded NotificationDao IN-list reads

**Rating:** 92/100

**Provenance:** Claude Opus 5.5 — boundaries list task 1; Devin SWE 2 — boundaries list task 7

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NotificationDaoTest.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## Return typed results from personal-resource uploads

**Rating:** 92/100

**Provenance:** Copilot Grok 4.5 — boundaries list task 4; Copilot Gemini 3.5 Flash — boundaries list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Route LoginActivity's repository calls through LoginViewModel

**Rating:** 92/100

**Provenance:** Claude Opus 5.5 — boundaries list task 5; Devin SWE 2 — boundaries list task 9

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/GuestLoginExtensions.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/sync/LoginViewModelTest.kt`; move behavior without changing rendered state or navigation.

**Task:** Move the named repository/orchestration calls into the ViewModel, expose lifecycle-safe state/events, remove direct UI-layer dependencies, and add focused ViewModel tests.

---

## chunk the IN-clause reads in TeamLogDao

**Rating:** 91/100

**Provenance:** Devin SWE 2 — boundaries list task 6

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamLogDao.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## Hide Room projections and removed-log persistence behind resource boundaries

**Rating:** 91/100

**Provenance:** Claude Opus 5.5 — boundaries list task 7; Copilot Grok 4.5 — boundaries list task 1

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/user/EditAchievementFragment.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## persist the DownloadService queue in batches instead of after every file

**Rating:** 91/100

**Provenance:** Claude Opus 5.5 — performance list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/services/DownloadService.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadServiceTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Hide TeamTaskDao behind a notification task-lookup boundary

**Rating:** 90/100

**Provenance:** Devin SWE 2 — boundaries list task 1; Copilot Grok 4.5 — boundaries list task 6

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## Introduce `ResourceDetailViewModel` (data access out of fragment)

**Rating:** 90/100

**Provenance:** Copilot Kimi K3 — boundaries list task 1

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourceDetailFragment.kt`; move behavior without changing rendered state or navigation.

**Task:** Move the named repository/orchestration calls into the ViewModel, expose lifecycle-safe state/events, remove direct UI-layer dependencies, and add focused ViewModel tests.

---

## Make retry queue state derive from the DAO instead of mutable process flags

**Rating:** 90/100

**Provenance:** Codex Sol 5.6 — boundaries list task 5

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## Resource sync pagination without CouchDB `skip`

**Rating:** 90/100

**Provenance:** Copilot Grok 4.5 — performance list task 10

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, `app/src/test/java/org/ole/planet/myplanet/services/sync/SyncManagerTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## strip UserRepositoryImpl's foreign DAOs behind the resources and events boundaries

**Rating:** 90/100

**Provenance:** Devin SWE 2 — boundaries list task 3

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/repository/UserRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## batch the HTML-resource reconcile pass in ResourcesRepositoryImpl

**Rating:** 89/100

**Provenance:** Claude Opus 5.5 — performance list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## route ProgressRepositoryImpl and CoursesRepositoryImpl submissions-domain access through SubmissionsRepository

**Rating:** 89/100

**Provenance:** Devin SWE 2 — boundaries list task 5

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Upload: make `UploadRepository` network-only; finish Room upload configs for exams/submissions

**Rating:** 89/100

**Provenance:** Copilot Grok 4.5 — boundaries list task 8

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`, `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfig.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## batch member-visit and membership writes in TeamsRepositoryImpl

**Rating:** 88/100

**Provenance:** Devin SWE 2 — performance list task 1

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Introduce `BecomeMemberViewModel` (form data access out of activity)

**Rating:** 88/100

**Provenance:** Copilot Kimi K3 — boundaries list task 2

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at `app/src/main/java/org/ole/planet/myplanet/ui/user/BecomeMemberActivity.kt`; move behavior without changing rendered state or navigation.

**Task:** Move the named repository/orchestration calls into the ViewModel, expose lifecycle-safe state/events, remove direct UI-layer dependencies, and add focused ViewModel tests.

---

## Life repository: require caller `userId`, drop `SharedPrefManager`

**Rating:** 88/100

**Provenance:** Copilot Grok 4.5 — boundaries list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## move notification formatting and grouping off the main thread

**Rating:** 88/100

**Provenance:** Claude Opus 5.5 — performance list task 7

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModelTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/TestDispatcherProvider.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## move ProcessUserDataActivity's sync/upload calls into a new SyncViewModel

**Rating:** 88/100

**Provenance:** Devin SWE 2 — boundaries list task 10

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at `app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt`; move behavior without changing rendered state or navigation.

**Task:** Move the named repository/orchestration calls into the ViewModel, expose lifecycle-safe state/events, remove direct UI-layer dependencies, and add focused ViewModel tests.

---

## replace enterprise report read-modify-REPLACE with single UPDATE queries

**Rating:** 88/100

**Provenance:** Claude Opus 5.5 — boundaries list task 8

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Move multi-table sync filtering behind `RealtimeSyncManager`

**Rating:** 87/100

**Provenance:** Codex Sol 5.6 — boundaries list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/services/sync/RealtimeSyncManager.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/RealtimeSyncMixin.kt`, `app/src/test/java/org/ole/planet/myplanet/services/sync/RealtimeSyncManagerTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## move the retry find-or-enqueue decision from RetryQueue into RetryRepository

**Rating:** 87/100

**Provenance:** Claude Opus 5.5 — boundaries list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## stop deep-copying the whole exam (questions included) in StepExam.insertCourseStepsExams

**Rating:** 87/100

**Provenance:** Claude Opus 5.5 — performance list task 8

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/model/StepExam.kt`, `app/src/test/java/org/ole/planet/myplanet/model/StepExamTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Avoid full-library title map on offline storage listing

**Rating:** 86/100

**Provenance:** Copilot Grok 4.5 — performance list task 5

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## delete the duplicated meetups sync write by reusing EventsSyncWriter.batchInsertMeetups

**Rating:** 86/100

**Provenance:** Devin SWE 2 — boundaries list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`, `app/src/main/java/org/ole/planet/myplanet/di/ServiceModule.kt`, `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/CommunitySyncWriter.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## stop the chat history from reloading on every return to STARTED

**Rating:** 86/100

**Provenance:** Claude Opus 5.5 — performance list task 5

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## move shelf discovery out of SyncManager into SyncRepository

**Rating:** 85/100

**Provenance:** Claude Opus 5.5 — boundaries list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## parse retry payloads straight to kotlinx instead of Gson → kotlinx

**Rating:** 85/100

**Provenance:** Claude Opus 5.5 — performance list task 10

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Split resource-upload serialization out of `ResourcesRepository` (tighten the 79-method interface)

**Rating:** 85/100

**Provenance:** Copilot Kimi K3 — boundaries list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/UploadRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Thread-safe PDF thumbnail cache with eviction recycle

**Rating:** 85/100

**Provenance:** Copilot Grok 4.5 — performance list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Cache foreground-service eligibility during each download run

**Rating:** 84/100

**Provenance:** Claude Opus 5.5 — performance list task 9; Copilot Grok 4.5 — performance list task 8

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/services/DownloadWorker.kt`, `app/src/test/java/org/ole/planet/myplanet/services/DownloadWorkerTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Diagnostics: drop `SharedPrefManager`; resolve planet codes via existing repositories

**Rating:** 84/100

**Provenance:** Copilot Grok 4.5 — boundaries list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Downscale first-page PDF preview in resource viewer

**Rating:** 84/100

**Provenance:** Copilot Grok 4.5 — performance list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Hide `SharedPreferences` behind the Life cache boundary

**Rating:** 84/100

**Provenance:** Codex Sol 5.6 — boundaries list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## let ChatViewModel resolve community config instead of ChatHistoryFragment

**Rating:** 84/100

**Provenance:** Claude Opus 5.5 — boundaries list task 6

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`; move behavior without changing rendered state or navigation.

**Task:** Move the named repository/orchestration calls into the ViewModel, expose lifecycle-safe state/events, remove direct UI-layer dependencies, and add focused ViewModel tests.

---

## Replace `CourseProgressDao`'s hand-built dynamic query with a parameterized one

**Rating:** 84/100

**Provenance:** Copilot Kimi K3 — boundaries list task 9

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDaoTest.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## stop rebuilding the legacy EncryptedSharedPreferences on every credential call

**Rating:** 84/100

**Provenance:** Claude Opus 5.5 — performance list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/SecurePrefsTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Cap per-file memory in `CrashLogStore.loadPendingLogs`

**Rating:** 83/100

**Provenance:** Copilot Kimi K3 — performance list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Chat list append/animation without position-map thrash

**Rating:** 83/100

**Provenance:** Copilot Grok 4.5 — performance list task 9

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Make chunked answer DAO operations stable and no-op on empty IDs

**Rating:** 83/100

**Provenance:** Codex Sol 5.6 — boundaries list task 10

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## Move remaining data calls in `AchievementFragment` into `AchievementViewModel`

**Rating:** 83/100

**Provenance:** Copilot Kimi K3 — boundaries list task 3

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`; move behavior without changing rendered state or navigation.

**Task:** Move the named repository/orchestration calls into the ViewModel, expose lifecycle-safe state/events, remove direct UI-layer dependencies, and add focused ViewModel tests.

---

## Enterprises reports: stop UI depending on raw `MyTeam` report rows

**Rating:** 82/100

**Provenance:** Copilot Grok 4.5 — boundaries list task 5

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## move the community-leaders fetch from LoginSyncManager into ConfigurationsRepository

**Rating:** 82/100

**Provenance:** Claude Opus 5.5 — boundaries list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt`, `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Normalize shelf cache values inside `SyncRepositoryImpl`

**Rating:** 82/100

**Provenance:** Codex Sol 5.6 — boundaries list task 6

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## route ActivitiesRepositoryImpl's user lookup through UserRepository

**Rating:** 82/100

**Provenance:** Devin SWE 2 — boundaries list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepositoryImpl.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Add a paged top-level query to `NewsDao` for the voices feed (DAO optimization)

**Rating:** 81/100

**Provenance:** Copilot Kimi K3 — boundaries list task 7

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NewsDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## Parallelize the two independent batch lookups in `NotificationsRepositoryImpl.getJoinRequestDetailsBatch`

**Rating:** 81/100

**Provenance:** Copilot Kimi K3 — performance list task 8

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Activities chart: move monthly aggregation out of the Fragment

**Rating:** 80/100

**Provenance:** Copilot Grok 4.5 — boundaries list task 10

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragmentTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesViewModelTest.kt`; move behavior without changing rendered state or navigation.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## make the My Life visibility toggle re-read the row it updated

**Rating:** 80/100

**Provenance:** Claude Opus 5.5 — boundaries list task 9

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Create a database unit test suite for `RemovedLogDao`

**Rating:** 79/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 9

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in the named production surface.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Cut LifeCache read copies and blocking preference writes

**Rating:** 79/100

**Provenance:** Copilot Grok 4.5 — performance list task 6

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Hoist course resource download selection out of the ViewModel

**Rating:** 79/100

**Provenance:** Codex Sol 5.6 — boundaries list task 8

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModelTest.kt`; move behavior without changing rendered state or navigation.

**Task:** Move the named repository/orchestration calls into the ViewModel, expose lifecycle-safe state/events, remove direct UI-layer dependencies, and add focused ViewModel tests.

---

## Migrate `DictionaryRepository` and `DictionaryMapper` parsing to Kotlin Serialization

**Rating:** 78/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 5

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryMapper.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## precompute team-match patterns outside the row loop in countTopLevelByTeams

**Rating:** 78/100

**Provenance:** Devin SWE 2 — performance list task 9

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Replace the notifications repository’s cross-feature `News` list with a count

**Rating:** 78/100

**Provenance:** Codex Sol 5.6 — boundaries list task 1

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/TeamChatBadgeIntegrationTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Deduplicate upload-success identifiers before DAO updates

**Rating:** 77/100

**Provenance:** Codex Sol 5.6 — boundaries list task 9

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/UploadRepositoryImplTest.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## Decouple `MyLife.kt` model from Android R resource strings

**Rating:** 76/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 1

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/model/MyLife.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Make personal-resource timestamps deterministic at the repository boundary

**Rating:** 76/100

**Provenance:** Codex Sol 5.6 — boundaries list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Settings clear-data: one repository orchestration entry

**Rating:** 76/100

**Provenance:** Copilot Grok 4.5 — boundaries list task 7

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/settings/SettingsViewModelTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## precompute the day-to-meetups index in CalendarFragment

**Rating:** 75/100

**Provenance:** Devin SWE 2 — performance list task 2

**Verified premise:** Working-tree inspection confirms direct data/orchestration work remains in the UI path at the named production surface; move behavior without changing rendered state or navigation.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Consolidate `LifeCache` serialization from Gson to Kotlin Serialization

**Rating:** 74/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Test coverage for `NotificationsRepositoryImpl.getJoinRequestDetailsBatch` batching behavior

**Rating:** 74/100

**Provenance:** Copilot Kimi K3 — performance list task 9

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Cap Glide decode size in finance row thumbnails

**Rating:** 73/100

**Provenance:** Copilot Grok 4.5 — performance list task 1

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesFinancesAdapterTest.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## remove dead MyLibraryDao.getAll and give the test a count query

**Rating:** 73/100

**Provenance:** Devin SWE 2 — boundaries list task 8

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/test/java/org/ole/planet/myplanet/repository/ResourcesRepositoryLibrarySyncTest.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Cap Glide decode size in report row thumbnails

**Rating:** 72/100

**Provenance:** Copilot Grok 4.5 — performance list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsAdapterTest.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Create a database unit test suite for `DictionaryDao`

**Rating:** 72/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 8

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in the named production surface.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## drop redundant list copies and hoist a loop-invariant split in repository impls

**Rating:** 72/100

**Provenance:** Devin SWE 2 — performance list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## drop the dead pending-upload surface from PersonalsRepository

**Rating:** 71/100

**Provenance:** Claude Opus 5.5 — boundaries list task 10

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/PersonalsRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Decouple `ConfigurationsRepository` from Android Context and R resources

**Rating:** 70/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## lowercase correct choices once at write time, not per comparison

**Rating:** 69/100

**Provenance:** Devin SWE 2 — performance list task 5

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Faster notification type resolution

**Rating:** 68/100

**Provenance:** Copilot Grok 4.5 — performance list task 7

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Stop diagnostics persistence from printing exceptions across its boundary

**Rating:** 68/100

**Provenance:** Codex Sol 5.6 — boundaries list task 7

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Optimize MimeType Resolution Performance and Robustness

**Rating:** 67/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 1

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/utils/FileUtils.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## buffer the asset stream and compile the credential regex once

**Rating:** 66/100

**Provenance:** Devin SWE 2 — performance list task 10

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Create a comprehensive unit test suite for `DeviceNameProvider`

**Rating:** 64/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 10

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in the named production surface.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## hoist styling lookups out of chat UI bind paths

**Rating:** 63/100

**Provenance:** Devin SWE 2 — performance list task 6

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## View Binding for `OnboardingAdapter`

**Rating:** 62/100

**Provenance:** Copilot Kimi K3 — performance list task 7

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/onboarding/OnboardingAdapter.kt`, `app/src/main/res/layout/onboarding_item.xml`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## hoist getString/getColor calls out of member and user adapters

**Rating:** 61/100

**Provenance:** Devin SWE 2 — performance list task 8

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Retry repository: remove `android.util.Log` boundary noise

**Rating:** 61/100

**Provenance:** Copilot Grok 4.5 — boundaries list task 9

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## replace the per-close-click label scan in VoicesLabelManager

**Rating:** 60/100

**Provenance:** Devin SWE 2 — performance list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## hoist getString calls out of finance, life, and exam adapters

**Rating:** 58/100

**Provenance:** Devin SWE 2 — performance list task 7

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in the named production surface; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Flatten `item_library_grid.xml` badge FrameLayout chain

**Rating:** 57/100

**Provenance:** Copilot Kimi K3 — performance list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/res/layout/item_library_grid.xml`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---

## Optimize `MyLifeDao` query complexity by normalizing userId in Repository

**Rating:** 57/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 6

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLifeDaoTest.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## Flatten `row_survey.xml` list-row hierarchy

**Rating:** 55/100

**Provenance:** Copilot Kimi K3 — performance list task 1

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/res/layout/row_survey.xml`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Optimize `PersonalDao` query complexity by normalizing userId in Repository

**Rating:** 55/100

**Provenance:** Copilot Gemini 3.5 Flash — boundaries list task 7

**Verified premise:** Working-tree inspection confirms the cited DAO shape/query and its caller in `app/src/main/java/org/ole/planet/myplanet/data/room/dao/PersonalDao.kt`; preserve current null, empty-list, ordering, and chunking semantics.

**Task:** Introduce the smallest typed/chunked/parameterized DAO operation, update its repository caller, and add Room tests for normal, empty, null, and boundary-size inputs.

---

## `setHasFixedSize(true)` on stable list RecyclerViews, batch A

**Rating:** 52/100

**Provenance:** Copilot Kimi K3 — performance list task 5

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListFragment.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## `setHasFixedSize(true)` on stable list RecyclerViews, batch B

**Rating:** 52/100

**Provenance:** Copilot Kimi K3 — performance list task 6

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesProgressFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragment.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Run the course DAO tests in the shared default Robolectric sandbox

**Rating:** 50/100

**Provenance:** Codex Sol 5.6 — performance list task 10

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/data/room/dao/CourseDaoTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Run the exam DAO tests in the shared default Robolectric sandbox

**Rating:** 50/100

**Provenance:** Codex Sol 5.6 — performance list task 9

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/data/room/dao/ExamDaoTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Eliminate On-Scroll Click Listener Allocations in ChatAdapter

**Rating:** 48/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 3

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Eliminate On-Scroll Click Listener Allocations in LifeAdapter

**Rating:** 48/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 8

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Eliminate Allocation Churn in HealthUsersAdapter View Binding

**Rating:** 46/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 6

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthUsersAdapter.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Optimize Interaction Handlers in SubmissionsAdapter

**Rating:** 46/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 9

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionsAdapter.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Eliminate On-Scroll Click Listener Allocations in UserArrayAdapter

**Rating:** 45/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 2

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/user/UserArrayAdapter.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Eliminate On-Scroll Click Listener Allocations in ServerAddressAdapter

**Rating:** 44/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 5

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Eliminate On-Scroll Click Listener Allocations in UsersAdapter

**Rating:** 44/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 10

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/user/UsersAdapter.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Run the enterprise report fragment tests in the shared default Robolectric sandbox

**Rating:** 44/100

**Provenance:** Codex Sol 5.6 — performance list task 8

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/ui/enterprises/EnterprisesReportsFragmentTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Eliminate On-Scroll Click Listener Allocations in CheckboxAdapter

**Rating:** 43/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 4

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/ui/components/CheckboxAdapter.kt`; retain observable behavior while removing it.

**Task:** Apply the localized UI/allocation optimization without changing binding or click semantics, and add or retain a regression test for recycled views and edge states.

---

## Run the resources adapter tests in the shared default Robolectric sandbox

**Rating:** 43/100

**Provenance:** Codex Sol 5.6 — performance list task 7

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesAdapterTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Run the voices actions tests in the shared default Robolectric sandbox

**Rating:** 43/100

**Provenance:** Codex Sol 5.6 — performance list task 4

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/ui/voices/VoicesActionsTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Run the life adapter tests in the shared default Robolectric sandbox

**Rating:** 42/100

**Provenance:** Codex Sol 5.6 — performance list task 1

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Run the server address adapter tests in the shared default Robolectric sandbox

**Rating:** 42/100

**Provenance:** Codex Sol 5.6 — performance list task 2

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapterTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Run the chat adapter clipboard tests in the shared default Robolectric sandbox

**Rating:** 41/100

**Provenance:** Codex Sol 5.6 — performance list task 5

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Run the personals adapter cache test in the shared default Robolectric sandbox

**Rating:** 40/100

**Provenance:** Codex Sol 5.6 — performance list task 3

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapterTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Run the resources filter dialog smoke test in the shared default Robolectric sandbox

**Rating:** 39/100

**Provenance:** Codex Sol 5.6 — performance list task 6

**Verified premise:** Working-tree inspection confirms the named test target and production contract; the requested coverage/configuration gap remains in `app/src/test/java/org/ole/planet/myplanet/ui/resources/ResourcesFilterFragmentTest.kt`.

**Task:** Add or adjust the focused regression tests only, then run the focused class and the complete default-debug unit suite.

---

## Convert Sha256Utils Utility Class to Kotlin Object Singleton

**Rating:** 35/100

**Provenance:** Copilot Gemini 3.5 Flash — performance list task 7

**Verified premise:** Working-tree inspection confirms the described allocation, coupling, repeated work, or boundary leak in `app/src/main/java/org/ole/planet/myplanet/utils/Sha256Utils.kt`; retain observable behavior while removing it.

**Task:** Make the smallest behavior-preserving production change, add focused regression coverage for the verified failure/performance case, and keep unrelated surfaces unchanged.

---
