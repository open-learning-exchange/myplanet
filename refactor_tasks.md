## 89/100 — Chunk every remaining unbounded Room IN query below SQLite bind limits

- **Provenance:** Devin SWE 2 (13.1), Claude Opus 5.5 (9.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/QuestionDao.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/UserDao.kt`.
- **Work order:** Chunk every remaining unbounded Room IN query below SQLite bind limits. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 84/100 — Consolidate and cache server-reachability probes without redundant network work

- **Provenance:** Copilot Gemini 3.8 Flash (4.2), Copilot Kimi K3 (5.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/ServerConfigUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`.
- **Work order:** Consolidate and cache server-reachability probes without redundant network work. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 84/100 — Make notification merge/read operations atomic and return typed presentation projections

- **Provenance:** Codex Sol 5.6 (8.1), Claude Opus 5.5 (9.1), Copilot Gemini 3.8 Flash (10.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NotificationDaoTest.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`.
- **Work order:** Make notification merge/read operations atomic and return typed presentation projections. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 82/100 — Make LifeCache immutable, bounded, invalidatable, and free of copy-on-read churn

- **Provenance:** Copilot Grok 4.6 (6.5), Codex Sol 5.6 (8.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/LifeCache.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeCacheTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`.
- **Work order:** Make LifeCache immutable, bounded, invalidatable, and free of copy-on-read churn. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 81/100 — move enterprises attachment image reads off the main thread

- **Provenance:** Devin SWE 2 (1.1).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** move enterprises attachment image reads off the main thread. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 81/100 — move the map-tile asset copy off the main thread at cold start

- **Provenance:** Claude Opus 5.5 (3.5).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** move the map-tile asset copy off the main thread at cold start. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 80/100 — Make retry persistence platform-neutral with injected JSON and no android.util.Log

- **Provenance:** Codex Sol 5.6 (8.5), Copilot Grok 4.6 (12.9), Copilot Gemini 3.8 Flash (16.7), Copilot Grok 4.6 (18.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/RetryRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/RetryRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt`.
- **Work order:** Make retry persistence platform-neutral with injected JSON and no android.util.Log. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 79/100 — Make diagnostics failure handling platform-neutral and batch typed APK-log operations

- **Provenance:** Codex Sol 5.6 (8.7), Copilot Gemini 3.8 Flash (10.8), Copilot Gemini 3.8 Flash (16.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/DiagnosticsRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/DiagnosticsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ApkLogDao.kt`.
- **Work order:** Make diagnostics failure handling platform-neutral and batch typed APK-log operations. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 78/100 — Compare multiple-choice answers in linear time while preserving duplicate semantics

- **Provenance:** Codex Sol 5.6 (2.10), Codex Sol 5.6 (14.5).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/ExamAnswerUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ExamAnswerUtilsTest.kt`.
- **Work order:** Compare multiple-choice answers in linear time while preserving duplicate semantics. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 78/100 — isNetworkConnectedFlow startup allocation

- **Provenance:** Copilot Kimi K3 (17.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`.
- **Work order:** isNetworkConnectedFlow startup allocation. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 76/100 — Batch deterministic dictionary seeding, cache its file lookup, and debounce search

- **Provenance:** Copilot Gemini 3.8 Flash (4.1), Copilot Grok 4.6 (6.10), Copilot Grok 4.6 (12.6), Copilot Grok 4.6 (18.5).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/DictionaryDao.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/entity/DictionaryEntity.kt`.
- **Work order:** Batch deterministic dictionary seeding, cache its file lookup, and debounce search. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 75/100 — run the AutoSyncWorker upload chain concurrently instead of strictly sequentially

- **Provenance:** Devin SWE 2 (1.2).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** run the AutoSyncWorker upload chain concurrently instead of strictly sequentially. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 73/100 — cache per-bind resource lookups in onboarding, courses-steps, and chat-share-target adapters

- **Provenance:** Devin SWE 2 (1.10).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** cache per-bind resource lookups in onboarding, courses-steps, and chat-share-target adapters. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 73/100 — Cache SecureRandom and Eliminate Redundant Arrays in AndroidDecrypter

- **Provenance:** Copilot Gemini 3.8 Flash (4.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/AndroidDecrypter.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/SecurePrefs.kt`.
- **Work order:** Cache SecureRandom and Eliminate Redundant Arrays in AndroidDecrypter. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 73/100 — cache the manifest permission set and drop the unused install-permission check

- **Provenance:** Claude Opus 5.5 (3.6).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** cache the manifest permission set and drop the unused install-permission check. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 73/100 — Make chat search allocation-light, pre-normalized, dispatcher-injected, and empty-query aware

- **Provenance:** Codex Sol 5.6 (2.2), Copilot Gemini 3.8 Flash (4.10), Copilot Grok 4.6 (6.8), Copilot Kimi K3 (11.6), Codex Sol 5.6 (14.4), Claude Opus 5.5 (15.6), Copilot Grok 4.6 (18.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/ChatSearch.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ChatSearchTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`.
- **Work order:** Make chat search allocation-light, pre-normalized, dispatcher-injected, and empty-query aware. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 73/100 — TimeUtils formatter cache audit

- **Provenance:** Copilot Kimi K3 (17.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/DownloadUtilsTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/TimeProvider.kt`.
- **Work order:** TimeUtils formatter cache audit. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 71/100 — Index calendar meetups by local date in the ViewModel and keep the UI reactive

- **Provenance:** Copilot Kimi K3 (5.3), Copilot Gemini 3.8 Flash (10.7), Codex Sol 5.6 (14.1), Copilot Grok 4.6 (18.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/calendar/CalendarViewModelTest.kt`.
- **Work order:** Index calendar meetups by local date in the ViewModel and keep the UI reactive. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 71/100 — Replace enterprise report read-modify-upsert with typed projections and targeted DAO updates

- **Provenance:** Devin SWE 2 (7.7), Codex Sol 5.6 (8.2), Claude Opus 5.5 (9.3), Copilot Gemini 3.8 Flash (10.2).
- **Verified working-tree evidence:** `app/src/test/java/org/ole/planet/myplanet/data/room/dao/TeamDaoTest.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TeamDao.kt`, `app/src/main/java/org/ole/planet/myplanet/model/MyTeam.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`.
- **Work order:** Replace enterprise report read-modify-upsert with typed projections and targeted DAO updates. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 70/100 — Batch chat typing updates and RecyclerView scrolling instead of updating per character

- **Provenance:** Claude Opus 5.5 (3.9), Copilot Kimi K3 (5.1), Copilot Grok 4.6 (6.1).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragmentTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatAdapterTest.kt`.
- **Work order:** Batch chat typing updates and RecyclerView scrolling instead of updating per character. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 70/100 — Remove redundant ViewModel dispatcher hops around suspend repository calls

- **Provenance:** Copilot Grok 4.6 (12.7), Copilot Grok 4.6 (12.8), Copilot Grok 4.6 (12.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/community/CommunityServicesViewModelTest.kt`.
- **Work order:** Remove redundant ViewModel dispatcher hops around suspend repository calls. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 70/100 — Return promptly from configuration probing after the first successful endpoint

- **Provenance:** Codex Sol 5.6 (2.1), Claude Opus 5.5 (3.2), Codex Sol 5.6 (14.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`.
- **Work order:** Return promptly from configuration probing after the first successful endpoint. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 70/100 — Slim retry-queue persistence and load only a counted, ten-row dialog preview

- **Provenance:** Claude Opus 5.5 (9.2), Copilot Gemini 3.8 Flash (10.6), Claude Opus 5.5 (15.5).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueueWorker.kt`, `app/src/main/java/org/ole/planet/myplanet/services/retry/RetryQueue.kt`, `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt`, `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RetryDao.kt`.
- **Work order:** Slim retry-queue persistence and load only a counted, ten-row dialog preview. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Batch join-request lookups and build notification details without intermediate tuples

- **Provenance:** Devin SWE 2 (7.6), Codex Sol 5.6 (14.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImplTest.kt`.
- **Work order:** Batch join-request lookups and build notification details without intermediate tuples. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Bound shelf-sync parallelism, serialize once, and route failures through SyncTimeLogger

- **Provenance:** Copilot Gemini 3.8 Flash (4.3), Codex Sol 5.6 (8.6).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt`, `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt`.
- **Work order:** Bound shelf-sync parallelism, serialize once, and route failures through SyncTimeLogger. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Deduplicate PDF first-page rendering and cap thumbnail bitmap dimensions

- **Provenance:** Claude Opus 5.5 (3.8), Copilot Grok 4.6 (6.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/PdfThumbnailLoader.kt`.
- **Work order:** Deduplicate PDF first-page rendering and cap thumbnail bitmap dimensions. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Delete course-detail provider pass-throughs and inject the repositories directly

- **Provenance:** Devin SWE 2 (7.1), Copilot Grok 4.6 (12.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailProvider.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/RatingSummaryProvider.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModelTest.kt`.
- **Work order:** Delete course-detail provider pass-throughs and inject the repositories directly. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Derive download diagnostics from requests without repeated regex parsing

- **Provenance:** Codex Sol 5.6 (8.3), Copilot Gemini 3.8 Flash (4.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/DownloadRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/DownloadRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/data/api/ApiInterface.kt`.
- **Work order:** Derive download diagnostics from requests without repeated regex parsing. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Extract health examination row mapping and remove redundant adapter work

- **Provenance:** Devin SWE 2 (7.10), Copilot Kimi K3 (5.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapterTest.kt`.
- **Work order:** Extract health examination row mapping and remove redundant adapter work. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Extract submission upload serialization and use the injected clock for report timestamps

- **Provenance:** Copilot Kimi K3 (11.8), Codex Sol 5.6 (14.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt`.
- **Work order:** Extract submission upload serialization and use the injected clock for report timestamps. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Fix ANRWatchdog timing/math and make its cross-thread state visible

- **Provenance:** Copilot Gemini 3.8 Flash (4.6), Devin SWE 2 (13.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/ANRWatchdog.kt`, `app/src/main/java/org/ole/planet/myplanet/MainApplication.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/CrashLogStore.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt`.
- **Work order:** Fix ANRWatchdog timing/math and make its cross-thread state visible. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Introduce a narrow resource-sync boundary that owns _all_docs fetching

- **Provenance:** Devin SWE 2 (7.8), Claude Opus 5.5 (9.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SyncRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/services/sync/SyncManager.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SyncRepositoryImplTest.kt`.
- **Work order:** Introduce a narrow resource-sync boundary that owns _all_docs fetching. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Move health-record encryption/entity mapping behind typed ViewModel APIs

- **Provenance:** Claude Opus 5.5 (9.6), Copilot Grok 4.6 (12.5).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthExaminationViewModelTest.kt`.
- **Work order:** Move health-record encryption/entity mapping behind typed ViewModel APIs. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Move media/file probing off main and buffer CSV preview reads

- **Provenance:** Copilot Gemini 3.8 Flash (4.5), Copilot Grok 4.6 (6.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/AudioRecorder.kt`, `app/src/test/java/org/ole/planet/myplanet/services/AudioRecorderTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoader.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ResourcesPreviewLoaderTest.kt`.
- **Work order:** Move media/file probing off main and buffer CSV preview reads. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Move ReplyActivity data loading into ReplyViewModel and cover reply aggregation

- **Provenance:** Copilot Kimi K3 (11.12), Copilot Kimi K3 (11.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/voices/ReplyActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt`.
- **Work order:** Move ReplyActivity data loading into ReplyViewModel and cover reply aggregation. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Remove redundant login initialization work and encapsulate dictionary download URL resolution

- **Provenance:** Claude Opus 5.5 (3.4), Copilot Gemini 3.8 Flash (10.1).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/sync/LoginViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`.
- **Work order:** Remove redundant login initialization work and encapsulate dictionary download URL resolution. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Remove the duplicate dictionary COUNT performed during screen startup

- **Provenance:** Copilot Grok 4.6 (6.6), Devin SWE 2 (13.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModelTest.kt`.
- **Work order:** Remove the duplicate dictionary COUNT performed during screen startup. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Replace hidden singleton/repository dependencies with narrow injectable actions

- **Provenance:** Copilot Kimi K3 (11.10), Copilot Kimi K3 (11.11).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/NotificationUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt`.
- **Work order:** Replace hidden singleton/repository dependencies with narrow injectable actions. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Reuse GsonUtils.gson in all model serialization hot paths

- **Provenance:** Copilot Kimi K3 (17.3), Copilot Kimi K3 (17.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/GsonUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/model/Answer.kt`, `app/src/main/java/org/ole/planet/myplanet/model/MyTeam.kt`, `app/src/main/java/org/ole/planet/myplanet/model/MyPlanet.kt`.
- **Work order:** Reuse GsonUtils.gson in all model serialization hot paths. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Route course visits and achievements through their domain repositories

- **Provenance:** Devin SWE 2 (7.3), Copilot Grok 4.6 (12.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/user/AchievementViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/user/AchievementViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/UserAchievementsRepository.kt`.
- **Work order:** Route course visits and achievements through their domain repositories. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 69/100 — Separate PDF extraction/loading from Android presentation and defer text extraction until requested

- **Provenance:** Claude Opus 5.5 (3.3), Copilot Gemini 3.8 Flash (10.5).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModelTest.kt`.
- **Work order:** Separate PDF extraction/loading from Android presentation and defer text extraction until requested. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — ActivitiesFragment: single rebuild-only chart configuration

- **Provenance:** Copilot Kimi K3 (5.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragmentTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/BellDashboardViewModel.kt`.
- **Work order:** ActivitiesFragment: single rebuild-only chart configuration. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Apply Consistent Coroutine Dispatcher Discipline in EnterprisesRepositoryImpl

- **Provenance:** Copilot Gemini 3.8 Flash (16.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/EnterprisesRepositoryImplTest.kt`.
- **Work order:** Apply Consistent Coroutine Dispatcher Discipline in EnterprisesRepositoryImpl. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Bind course covers with the ImageView Glide target

- **Provenance:** Copilot Grok 4.6 (6.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/CoursesItemUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/CoursesItemUtilsTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/GridSpanCalculator.kt`.
- **Work order:** Bind course covers with the ImageView Glide target. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Cache adapter date formatting and hoist feedback/submission click listeners

- **Provenance:** Devin SWE 2 (1.3), Copilot Kimi K3 (5.5).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackReplyAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/TimeUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/feedback/FeedbackAdapterTest.kt`.
- **Work order:** Cache adapter date formatting and hoist feedback/submission click listeners. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — collapse redundant TimeProvider.sleep default

- **Provenance:** Copilot Kimi K3 (17.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/TimeProvider.kt`.
- **Work order:** collapse redundant TimeProvider.sleep default. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — count the remaining download queue without copying it on every file

- **Provenance:** Claude Opus 5.5 (3.7).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** count the remaining download queue without copying it on every file. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Decode fullscreen images at screen size

- **Provenance:** Copilot Grok 4.6 (6.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/ImageViewerUtils.kt`.
- **Work order:** Decode fullscreen images at screen size. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — deduplicate DownloadUtils' twin URL-enqueue blocks

- **Provenance:** Devin SWE 2 (13.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/DownloadUtils.kt`.
- **Work order:** deduplicate DownloadUtils' twin URL-enqueue blocks. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — deduplicate library resource keys before bulk updates and title reads

- **Provenance:** Codex Sol 5.6 (2.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/MyLibraryDaoTest.kt`.
- **Work order:** deduplicate library resource keys before bulk updates and title reads. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — deduplicate notification IDs before Room chunking

- **Provenance:** Codex Sol 5.6 (2.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/NotificationDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/NotificationDaoTest.kt`.
- **Work order:** deduplicate notification IDs before Room chunking. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — deduplicate removed-document keys before cleanup chunks

- **Provenance:** Codex Sol 5.6 (2.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt`.
- **Work order:** deduplicate removed-document keys before cleanup chunks. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Deduplicate Step Status Queries in playstore.sh

- **Provenance:** Copilot Gemini 3.8 Flash (16.1).
- **Verified working-tree evidence:** `.github/scripts/playstore.sh`.
- **Work order:** Deduplicate Step Status Queries in playstore.sh. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — deduplicate submission IDs before answer reads and deletes

- **Provenance:** Codex Sol 5.6 (2.6).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/AnswerDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/AnswerDaoTest.kt`.
- **Work order:** deduplicate submission IDs before answer reads and deletes. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Drop Context from CourseDetailViewModel and CoursesStepsViewModel by resolving the files dir through StoragePathResolver

- **Provenance:** Claude Opus 5.5 (15.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/StoragePathResolver.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesStepsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/courses/CourseDetailViewModelTest.kt`.
- **Work order:** Drop Context from CourseDetailViewModel and CoursesStepsViewModel by resolving the files dir through StoragePathResolver. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — drop the double map lookup and per-call lambda allocation from the GsonUtils field getters

- **Provenance:** Claude Opus 5.5 (3.1).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** drop the double map lookup and per-call lambda allocation from the GsonUtils field getters. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — drop the per-event synchronizedList locking in SyncTimeLogger

- **Provenance:** Devin SWE 2 (1.6).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** drop the per-event synchronizedList locking in SyncTimeLogger. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — extract chat-conversation pagination into a platform-free pager

- **Provenance:** Devin SWE 2 (7.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`.
- **Work order:** extract chat-conversation pagination into a platform-free pager. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Extract Dialog Presentation from ThemeManager Service

- **Provenance:** Copilot Gemini 3.8 Flash (16.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/ThemeManager.kt`, `app/src/test/java/org/ole/planet/myplanet/services/ThemeManagerTest.kt`.
- **Work order:** Extract Dialog Presentation from ThemeManager Service. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Give CoursesRepository.getMyCoursesFlow a proper (non-suspend) return type

- **Provenance:** Copilot Kimi K3 (11.5).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** Give CoursesRepository.getMyCoursesFlow a proper (non-suspend) return type. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — give the course-progress grid a typed row model

- **Provenance:** Devin SWE 2 (7.5).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/model/CourseProgressData.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/ProgressGridAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CourseProgressActivity.kt`.
- **Work order:** give the course-progress grid a typed row model. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Health patient sync decoupling and UI state encapsulation

- **Provenance:** Copilot Gemini 3.8 Flash (10.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthViewModelTest.kt`.
- **Work order:** Health patient sync decoupling and UI state encapsulation. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — HealthViewModel loads patients from UserRepository

- **Provenance:** Copilot Grok 4.6 (12.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/health/HealthViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt`.
- **Work order:** HealthViewModel loads patients from UserRepository. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Hoist chat server prefs and AI-provider fetch into ChatViewModel

- **Provenance:** Copilot Grok 4.6 (12.1).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`.
- **Work order:** Hoist chat server prefs and AI-provider fetch into ChatViewModel. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — hoist the settings screen's guest-user check from the fragment into SettingsViewModel

- **Provenance:** Claude Opus 5.5 (9.10).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** hoist the settings screen's guest-user check from the fragment into SettingsViewModel. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — InlineResourceAdapter: skip redundant status resets on unchanged binds

- **Provenance:** Copilot Kimi K3 (5.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/InlineResourceAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatDetailFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt`.
- **Work order:** InlineResourceAdapter: skip redundant status resets on unchanged binds. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — introduce a narrow UserLookupRepository and move single-purpose callers onto it

- **Provenance:** Claude Opus 5.5 (9.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/di/RepositoryModule.kt`.
- **Work order:** introduce a narrow UserLookupRepository and move single-purpose callers onto it. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — LifeAdapter: replace reflection-based drawable lookup with compile-time map

- **Provenance:** Copilot Kimi K3 (5.6).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt`.
- **Work order:** LifeAdapter: replace reflection-based drawable lookup with compile-time map. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — LoginSyncManager.isManager: fail-fast role check

- **Provenance:** Copilot Grok 4.6 (18.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/sync/LoginSyncManager.kt`, `app/src/test/java/org/ole/planet/myplanet/services/sync/LoginSyncManagerTest.kt`.
- **Work order:** LoginSyncManager.isManager: fail-fast role check. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Make course pager stable-ID membership constant-time

- **Provenance:** Codex Sol 5.6 (14.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesPagerAdapter.kt`.
- **Work order:** Make course pager stable-ID membership constant-time. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Make team pager stable-ID membership constant-time

- **Provenance:** Codex Sol 5.6 (14.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamPagerAdapter.kt`.
- **Work order:** Make team pager stable-ID membership constant-time. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Markdown challenge dialog ViewModel state encapsulation

- **Provenance:** Copilot Gemini 3.8 Flash (10.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownViewModel.kt`.
- **Work order:** Markdown challenge dialog ViewModel state encapsulation. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — move respondent JSON parsing into SurveysPublicMapper

- **Provenance:** Devin SWE 2 (7.4).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/SurveysPublicMapper.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SurveysPublicMapperTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/surveys/PublicSurveyViewModelTest.kt`.
- **Work order:** move respondent JSON parsing into SurveysPublicMapper. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — move the course-step "download if server reachable" decision from CoursesStepsViewModel into ResourceDownloadCoordinator

- **Provenance:** Claude Opus 5.5 (9.9).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** move the course-step "download if server reachable" decision from CoursesStepsViewModel into ResourceDownloadCoordinator. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Move the playback-speed picker off material-dialogs and remove a redundant safe call in ResourceViewerFragment

- **Provenance:** Claude Opus 5.5 (15.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`.
- **Work order:** Move the playback-speed picker off material-dialogs and remove a redundant safe call in ResourceViewerFragment. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Narrow the VoicesRepository/UserRepository dependencies in NotificationsRepositoryImpl

- **Provenance:** Copilot Kimi K3 (11.2).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** Narrow the VoicesRepository/UserRepository dependencies in NotificationsRepositoryImpl. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Optimize Markdown Stripping and CSV Speech Formatting in TTSManager

- **Provenance:** Copilot Gemini 3.8 Flash (4.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/TTSManager.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/viewer/WebViewActivity.kt`.
- **Work order:** Optimize Markdown Stripping and CSV Speech Formatting in TTSManager. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Pool ServerUrlMapper reachability (drop HttpURLConnection GET)

- **Provenance:** Copilot Grok 4.6 (18.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/sync/ServerUrlMapper.kt`, `app/src/test/java/org/ole/planet/myplanet/services/sync/ServerUrlMapperTest.kt`, `app/src/main/java/org/ole/planet/myplanet/di/NetworkModule.kt`.
- **Work order:** Pool ServerUrlMapper reachability (drop HttpURLConnection GET). Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Prune proven-unused methods from the dictionary data path

- **Provenance:** Copilot Kimi K3 (11.7).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** Prune proven-unused methods from the dictionary data path. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Reactive Life item observation and ViewModel state hoisting

- **Provenance:** Copilot Gemini 3.8 Flash (10.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeViewModel.kt`.
- **Work order:** Reactive Life item observation and ViewModel state hoisting. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Remove redundant null-handling in personals, ratings and login-team UI code

- **Provenance:** Claude Opus 5.5 (15.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/ratings/RatingsViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/sync/LoginActivity.kt`.
- **Work order:** Remove redundant null-handling in personals, ratings and login-team UI code. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Remove redundant null-handling on non-null ids in the upload, notification and label code

- **Provenance:** Claude Opus 5.5 (15.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/services/VoicesLabelManager.kt`.
- **Work order:** Remove redundant null-handling on non-null ids in the upload, notification and label code. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Remove the cleanup worker's per-batch set copy

- **Provenance:** Codex Sol 5.6 (14.6).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/FreeSpaceWorker.kt`, `app/src/test/java/org/ole/planet/myplanet/services/FreeSpaceWorkerTest.kt`.
- **Work order:** Remove the cleanup worker's per-batch set copy. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — remove the fixed 200 ms sleep before notification read-receipt broadcasts

- **Provenance:** Devin SWE 2 (13.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/NotificationActionReceiver.kt`, `app/src/test/java/org/ole/planet/myplanet/services/NotificationActionReceiverTest.kt`.
- **Work order:** remove the fixed 200 ms sleep before notification read-receipt broadcasts. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — replace localized strings in ConfigurationsRepository's version-check contract with a typed error

- **Provenance:** Claude Opus 5.5 (9.5).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** replace localized strings in ConfigurationsRepository's version-check contract with a typed error. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — RetryInterceptor: cheaper retry-safe check, cheaper broadcast extras

- **Provenance:** Copilot Grok 4.6 (18.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt`, `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt`.
- **Work order:** RetryInterceptor: cheaper retry-safe check, cheaper broadcast extras. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — reuse a distinct APK-log ID set during upload acknowledgement

- **Provenance:** Codex Sol 5.6 (2.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ApkLogDao.kt`.
- **Work order:** reuse a distinct APK-log ID set during upload acknowledgement. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Reuse Empty JSON Singletons and Fast-Path Numeric Extraction in JsonUtils

- **Provenance:** Copilot Gemini 3.8 Flash (4.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/JsonUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/GsonUtils.kt`.
- **Work order:** Reuse Empty JSON Singletons and Fast-Path Numeric Extraction in JsonUtils. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Route all DictionaryActivity data access through DictionaryViewModel

- **Provenance:** Copilot Kimi K3 (11.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryActivity.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/dictionary/DictionaryViewModel.kt`.
- **Work order:** Route all DictionaryActivity data access through DictionaryViewModel. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — route SyncActivity's raw clock reads through the injected TimeProvider

- **Provenance:** Devin SWE 2 (13.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncActivity.kt`.
- **Work order:** route SyncActivity's raw clock reads through the injected TimeProvider. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Share PDF submission loading and page setup paths

- **Provenance:** Codex Sol 5.6 (8.10).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporter.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryExporterTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt`.
- **Work order:** Share PDF submission loading and page setup paths. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — skip the no-op notification upsert on every dashboard load

- **Provenance:** Claude Opus 5.5 (3.10).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** skip the no-op notification upsert on every dashboard load. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — snapshot sync timing aggregates once per report

- **Provenance:** Codex Sol 5.6 (2.3).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/SyncTimeLogger.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/SyncTimeLoggerConcurrencyTest.kt`.
- **Work order:** snapshot sync timing aggregates once per report. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Source the installed version through the version provider

- **Provenance:** Codex Sol 5.6 (8.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/AppVersionProvider.kt`.
- **Work order:** Source the installed version through the version provider. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Sparse-checkout playstore.yml (scripts only)

- **Provenance:** Copilot Grok 4.6 (18.1).
- **Verified working-tree evidence:** `.github/workflows/playstore.yml`, `.github/scripts/playstore.sh`, `.github/scripts/playstore-quota.sh`.
- **Work order:** Sparse-checkout playstore.yml (scripts only). Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Split chat share operations into a narrow ChatShareActions interface

- **Provenance:** Copilot Kimi K3 (11.1).
- **Verified working-tree evidence:** `app/src/test/java/org/ole/planet/myplanet/ui/chat/ChatViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`.
- **Work order:** Split chat share operations into a narrow ChatShareActions interface. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Stop ApiClient from retrying 4xx

- **Provenance:** Copilot Grok 4.6 (18.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/api/ApiClient.kt`, `app/src/test/java/org/ole/planet/myplanet/data/api/ApiClientTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/data/api/RetryInterceptor.kt`.
- **Work order:** Stop ApiClient from retrying 4xx. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — stop comparing URL schemes against localized strings in getMinApk

- **Provenance:** Devin SWE 2 (13.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/NetworkUtils.kt`.
- **Work order:** stop comparing URL schemes against localized strings in getMinApk. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Streamline Release Metadata Queries in playstore-quota.sh

- **Provenance:** Copilot Gemini 3.8 Flash (16.2).
- **Verified working-tree evidence:** `.github/scripts/playstore-quota.sh`.
- **Work order:** Streamline Release Metadata Queries in playstore-quota.sh. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Strip android.util.Log from GsonUtils

- **Provenance:** Copilot Grok 4.6 (18.6).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/GsonUtils.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/GsonUtilsNoLogStubTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/GsonUtilsTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/GsonUtilsCoercionTest.kt`.
- **Work order:** Strip android.util.Log from GsonUtils. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Suppress duplicate upload scheduler UI states

- **Provenance:** Codex Sol 5.6 (14.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/services/UserDataUploadScheduler.kt`.
- **Work order:** Suppress duplicate upload scheduler UI states. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Task 3b (alternative to 3 — pick exactly one) — Drop the redundant repository fields from ProcessUserDataActivity

- **Provenance:** Copilot Kimi K3 (11.4).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** Task 3b (alternative to 3 — pick exactly one) — Drop the redundant repository fields from ProcessUserDataActivity. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — unify leader hand-off before member removal in RequestsViewModel

- **Provenance:** Devin SWE 2 (7.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/teams/members/RequestsViewModelTest.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsAdapter.kt`.
- **Work order:** unify leader hand-off before member removal in RequestsViewModel. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — update one storage-selection row instead of recounting the full list

- **Provenance:** Codex Sol 5.6 (2.5).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModel.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/settings/StorageCategoryViewModelTest.kt`.
- **Work order:** update one storage-selection row instead of recounting the full list. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — Utilities object split prep: extract pure text helpers

- **Provenance:** Copilot Kimi K3 (17.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/UtilitiesTest.kt`.
- **Work order:** Utilities object split prep: extract pure text helpers. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 68/100 — VoicesAdapterHelper: same quadratic fix, independent implementation

- **Provenance:** Copilot Kimi K3 (5.2).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapterHelper.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesActions.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt`.
- **Work order:** VoicesAdapterHelper: same quadratic fix, independent implementation. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 65/100 — Replace stderr stack traces with structured logging across utilities, models, base, and UI

- **Provenance:** Devin SWE 2 (13.4), Devin SWE 2 (13.5), Devin SWE 2 (13.6).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/Utilities.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/KeyboardUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt`, `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt`.
- **Work order:** Replace stderr stack traces with structured logging across utilities, models, base, and UI. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 64/100 — Remove Android logging from the dictionary repository boundary

- **Provenance:** Codex Sol 5.6 (8.4), Copilot Gemini 3.8 Flash (16.6).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImpl.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/DictionaryRepositoryImplTest.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryRepository.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/DictionaryFileReader.kt`.
- **Work order:** Remove Android logging from the dictionary repository boundary. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 63/100 — Add unit tests for SyncConfigurationCoordinator failure branches

- **Provenance:** Copilot Kimi K3 (11.13).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/sync/SyncConfigurationCoordinator.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatViewModel.kt`, `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt`.
- **Work order:** Add unit tests for SyncConfigurationCoordinator failure branches. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 63/100 — de-Robolectricize RetryInterceptorTest

- **Provenance:** Copilot Kimi K3 (17.1).
- **Verified working-tree evidence:** `app/src/test/java/org/ole/planet/myplanet/data/api/RetryInterceptorTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/TimeProvider.kt`.
- **Work order:** de-Robolectricize RetryInterceptorTest. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 63/100 — Eliminate Per-Test Bytecode Re-instrumentation in ConfigurationsRepositoryImplTest.kt

- **Provenance:** Copilot Gemini 3.8 Flash (16.4).
- **Verified working-tree evidence:** `app/src/test/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImplTest.kt`.
- **Work order:** Eliminate Per-Test Bytecode Re-instrumentation in ConfigurationsRepositoryImplTest.kt. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 63/100 — Hoist personals/references adapter listeners and repeated context lookups

- **Provenance:** Devin SWE 2 (1.4), Copilot Kimi K3 (5.8).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapter.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/references/ReferencesAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/personals/PersonalsAdapterTest.kt`.
- **Work order:** Hoist personals/references adapter listeners and repeated context lookups. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 63/100 — Optimize MockK Initialization and Teardown in RetryQueueWorkerTest.kt

- **Provenance:** Copilot Gemini 3.8 Flash (16.5).
- **Verified working-tree evidence:** `app/src/test/java/org/ole/planet/myplanet/services/retry/RetryQueueWorkerTest.kt`.
- **Work order:** Optimize MockK Initialization and Teardown in RetryQueueWorkerTest.kt. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 63/100 — pure-JVM CourseRatingUtilsTest

- **Provenance:** Copilot Kimi K3 (17.6).
- **Verified working-tree evidence:** `app/src/test/java/org/ole/planet/myplanet/utils/CourseRatingUtilsTest.kt`, `app/src/main/java/org/ole/planet/myplanet/utils/CourseRatingUtils.kt`.
- **Work order:** pure-JVM CourseRatingUtilsTest. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 63/100 — UploadManagerTest runtime triage

- **Provenance:** Copilot Kimi K3 (17.5).
- **Verified working-tree evidence:** `app/src/test/java/org/ole/planet/myplanet/services/UploadManagerTest.kt`.
- **Work order:** UploadManagerTest runtime triage. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 62/100 — Hoist LifeAdapter bind listeners onto the ViewHolder

- **Provenance:** Copilot Grok 4.6 (6.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/life/LifeAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/life/LifeAdapterTest.kt`.
- **Work order:** Hoist LifeAdapter bind listeners onto the ViewHolder. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 62/100 — hoist per-bind listeners and a constant drawable in chat-history, checkbox, and team-selection adapters

- **Provenance:** Devin SWE 2 (1.8).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** hoist per-bind listeners and a constant drawable in chat-history, checkbox, and team-selection adapters. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 62/100 — hoist per-bind listeners and repeated styling in ResourcesTagsAdapter

- **Provenance:** Devin SWE 2 (1.7).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** hoist per-bind listeners and repeated styling in ResourcesTagsAdapter. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 62/100 — hoist per-bind listeners in NotificationsAdapter

- **Provenance:** Devin SWE 2 (1.9).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** hoist per-bind listeners in NotificationsAdapter. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 62/100 — hoist the startSurvey listener and its label strings in SurveysAdapter

- **Provenance:** Devin SWE 2 (1.5).
- **Verified working-tree evidence:** the named symbol and behavior were located with `rg` in the current checkout.
- **Work order:** hoist the startSurvey listener and its label strings in SurveysAdapter. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 60/100 — Add one shared search-debounce Flow operator and remove the two unopted FlowPreview warnings

- **Provenance:** Claude Opus 5.5 (15.7).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/utils/ViewExtensions.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/chat/ChatHistoryFragment.kt`, `app/src/main/java/org/ole/planet/myplanet/ui/resources/CollectionsFragment.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/ViewExtensionsTest.kt`.
- **Work order:** Add one shared search-debounce Flow operator and remove the two unopted FlowPreview warnings. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 60/100 — Make RemovedLogDao.getRemovedDocIds return non-null ids and clear its KSP warning

- **Provenance:** Claude Opus 5.5 (15.1).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDao.kt`, `app/src/test/java/org/ole/planet/myplanet/data/room/dao/RemovedLogDaoTest.kt`.
- **Work order:** Make RemovedLogDao.getRemovedDocIds return non-null ids and clear its KSP warning. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 57/100 — Improve CI test timing reports with warm-up-aware, sorted per-class warnings

- **Provenance:** Claude Opus 5.5 (15.10), Copilot Gemini 3.8 Flash (16.3), Copilot Kimi K3 (17.7).
- **Verified working-tree evidence:** `.github/scripts/test_timing_summary.py`, `.github/workflows/test.yml`.
- **Work order:** Improve CI test timing reports with warm-up-aware, sorted per-class warnings. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---

## 55/100 — Clear the compiler warnings in three unit-test files

- **Provenance:** Claude Opus 5.5 (15.9).
- **Verified working-tree evidence:** `app/src/main/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapter.kt`, `app/src/test/java/org/ole/planet/myplanet/repository/LifeRepositoryImplTest.kt`, `app/src/test/java/org/ole/planet/myplanet/ui/sync/ServerAddressAdapterTest.kt`, `app/src/test/java/org/ole/planet/myplanet/utils/FileUtilsTest.kt`.
- **Work order:** Clear the compiler warnings in three unit-test files. Preserve existing behavior, add focused regression coverage for the changed boundary or hot path, and run the narrow unit tests plus the default debug unit suite.

---
