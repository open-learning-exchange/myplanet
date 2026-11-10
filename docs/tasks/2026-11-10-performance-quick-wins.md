# myPlanet refactor round — performance quick wins (10 work orders)

- date: 2026-11-10
- base commit: 2093a6224b221079f1413e2ab1f22cebec8cf736 (master)
- open PRs checked: 18555, 18554, 18551, 18540, 18539, 18455, 18076, 18045, 18029, 17832, 17811, 17694, 16594, 15951, 15825, 15824, 15820, 15808, 15559, 15267, 15266, 15226, 15108, 14883, 14650, 14427, 13928, 13848, 13657, 13604, 13355, 13287, 10993, 8175, 4075
- recent-PR rule: no PR updated within the last 7 days carries a "review ready" or "merge" label. Regardless, every file touched by the six PRs updated in the last week (18555 gradle wrapper, 18554 CLAUDE.md, 18551 WebViewActivity.kt, 18540 resources/download area, 18539 BaseResourceFragment.kt, 18455 release workflows + app/build.gradle) is excluded from all tasks below.
- every cited path, class and function was opened and confirmed against the base commit.
- executors: run each task independently; all are mergeable in any order.

---

### 1. Gate the expired challenge-dialog evaluation before its five database queries (roadmap 7, supports 3)

context: `DashboardViewModel.evaluateChallengeDialog` (app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt:286) launches a coroutine that fires five parallel repository queries (`progressRepository.fetchCourseData`, `voicesRepository.getCommunityVoiceDateCount` twice, `coursesRepository.getCourseTitleById`, `submissionsRepository.hasPendingSurvey`) before computing `shouldPrompt` at line 315, which is hard-gated to 2024-11-30..2025-01-16 (`today.isAfter(LocalDate.of(2024, 11, 30)) && today.isBefore(endDate)` with `endDate = LocalDate.of(2025, 1, 16)`). That window closed almost two years ago, so every evaluation burns 5+ Room queries to always produce `null`. Removing the dead work is a pure quick win and keeps the ViewModel lean for the roadmap-3 expansion.

files: `app/src/main/java/org/ole/planet/myplanet/ui/dashboard/DashboardViewModel.kt` — function `evaluateChallengeDialog` only. Leave the four `e.printStackTrace()` sites and every other function in this class untouched.

steps:
1. At the top of `evaluateChallengeDialog`, evaluate the full gate (`!isGuest`, `today` within 2024-12-01..2025-01-15 inclusive as the current code expresses it, `serverUrl in validUrls`) and `return` early when it fails — before `viewModelScope.launch`.
2. Keep the existing in-window logic byte-for-byte identical inside the coroutine, including the `ChallengeDialogData` emission and the `hasValidSync` lookup.
3. Collapse the now-duplicated `today`/`shouldPrompt` computation inside the coroutine so the gate has a single source of truth.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; on any current date the dashboard opens without the challenge dialog and no challenge-related queries run (verifiable via logcat/Room logging); the code path inside the (currently unreachable) window is unchanged.

size budget: ~15 changed lines, 1 file.

out of scope: do not delete the challenge feature, its strings, or its resources; do not touch the `printStackTrace` call sites in this file.

---

### 2. Collapse the per-survey submission-existence loops into single COUNT queries (roadmap 1+7)

context: `SubmissionsRepositoryImpl.hasUnfinishedSurveys` (app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt:412) and `hasPendingSurvey` (:422) loop over every survey of a course and call `hasSubmission` per survey, and `hasSubmission` (:209-231) itself runs `questionDao.countByExamId` plus a `submissionDao` count. That is 2N+1 sequential Room queries for a course with N surveys, on the take-course and dashboard evaluation paths. The DAO layer already exposes the exact count semantics to compose from (`SubmissionDao.countByUserParentAndType`, app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt:34).

files: `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (the two functions only), `app/src/main/java/org/ole/planet/myplanet/data/room/dao/SubmissionDao.kt` (two new `@Query` count methods), `app/src/test/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImplTest.kt` (extend the existing mocks near line 510). Leave `SubmissionsRepository.kt` interface and `hasSubmission` untouched, and do not touch `CoursesRepositoryImpl.hasUnfinishedSurveys` (:590).

steps:
1. In `SubmissionDao`, add a suspend count query over `exams` (type `survey`, `courseId = :courseId`) requiring at least one matching `questions` row and no matching `submissions` row, with `parentId` built as `examId || '@' || :courseId` and the same nullable-user semantics (`userId IS :userId`, `type = 'survey'`) as `countByUserParentAndType`.
2. Add a sibling query identical except `parentId` is built from the exam row's own `courseId` column — this preserves current `hasPendingSurvey` behavior, which passes `survey.courseId` per row (SubmissionsRepositoryImpl.kt:425) instead of the parameter.
3. Rewrite both repository functions to return `daoCount > 0` and delete the loops; keep early-return semantics (true as soon as one unanswered survey exists) intact via the count.
4. Update `SubmissionsRepositoryImplTest`: mock the two new DAO methods; cover zero surveys, all-answered, and one-unanswered cases.

acceptance: `./gradlew testDefaultDebugUnitTest` green; course detail still flags unfinished surveys correctly; the dashboard challenge check returns identical results for fixtures with 0, 1 and N surveys.

size budget: ~80 changed lines, 3 files.

out of scope: no interface signature changes; no batching of other `hasSubmission` callers; no changes to `CoursesRepositoryImpl`.

---

### 3. Memoize profile-image Base64 encoding behind UserEntity.serialize (roadmap 5+7, supports 9)

context: `UserEntity.serialize` (app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt:61) calls `encodeImageToBase64` (:118), which re-reads the entire image file and Base64-encodes it on every call. `serialize()` is invoked once per pending submission by `SubmissionsPayloadBuilder.examUploadPayload` / `submissionPayload`, so an upload batch of N submissions re-encodes the same avatar N times on the upload path. Caching per path is a self-contained micro-optimization, and dropping the `printStackTrace` calls moves the model toward the platform-free core of roadmap 9.

files: `app/src/main/java/org/ole/planet/myplanet/model/UserEntity.kt` — `serialize()` and `encodeImageToBase64()` only. Leave `parseLeadersJson`, `addImageUrl`, and all field definitions alone.

steps:
1. Add a private `@Ignore @Transient` cache pair (`cachedImagePath`, `cachedBase64`) on the entity, mirroring the `cachedMessages`/`cachedMessageList` pattern already used in `model/Feedback.kt`.
2. Return the cached value when `userImage` is unchanged; invalidate the cache wherever `userImage` is reassigned inside this class.
3. Replace the `e.printStackTrace()` in `encodeImageToBase64`'s catch (:137) with `android.util.Log.w` plus a `TAG`; do the same for the `NumberFormatException` catch in `serialize` (:64) which falls back to `10`.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; a user with a profile image uploads with byte-identical `_attachments` content; encoding happens once per unique path (assert via a small unit test if a seam exists, otherwise verify manually with two consecutive `serialize()` calls).

size budget: ~35 changed lines, 1 file.

out of scope: no changes to `SubmissionsPayloadBuilder` or the upload pipeline; no image downsampling or format changes.

---

### 4. Replace printStackTrace with structured logging in RetryUtils (roadmap 8)

context: app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt:32 calls `lastException?.printStackTrace()` after the retry loop exhausts. `RetryUtils.retry` sits on hot paths — `DashboardViewModel.refreshNotificationsWithRetry` and the chat read path — so an exhausted retry dumps an untagged stack trace to stderr instead of a filterable logcat entry, and stderr writes are synchronized I/O. This is the kind of micro-fix that unblocks the broader code-health pass.

files: `app/src/main/java/org/ole/planet/myplanet/utils/RetryUtils.kt` — the tail of `retry()` only. Leave the loop, delay, and `shouldRetry` semantics alone. Do not modify `app/src/test/java/org/ole/planet/myplanet/utils/RetryUtilsTest.kt` unless it asserts on stderr output.

steps:
1. Add a `private const val TAG` and the `android.util.Log` import.
2. Replace `lastException?.printStackTrace()` with a single `Log.w(TAG, "retry exhausted", lastException)` guarded so a null exception logs nothing (current behavior).
3. Run `RetryUtilsTest` to confirm retry counts, delays and return values are unchanged.

acceptance: `./gradlew testDefaultDebugUnitTest` green; forcing an always-failing retry chain (e.g. notifications refresh offline) emits exactly one tagged warning with the cause attached.

size budget: ~4 changed lines, 1 file.

out of scope: no retry-policy, backoff, or signature changes; no new dependencies.

---

### 5. Replace printStackTrace in TransactionSyncManager catch blocks (roadmap 5+8)

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

### 6. Replace printStackTrace in FileUploader's upload catch (roadmap 5+8)

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

### 7. Stop swallowing CancellationException in HealthExaminationViewModel.saveExamination (roadmap 3+8)

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

### 8. Replace printStackTrace in the two course ViewModels (roadmap 3+8)

context: app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt:142 (`loadCourses`) and app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:131 (`loadCourse`) swallow failures with `e.printStackTrace()`. When a course load fails, the user sees a stuck/empty list and support gets an untagged stderr dump. Both classes lack a `TAG`, so the fix also brings them in line with the codebase's logging convention used by e.g. `HealthExaminationViewModel`.

files: `app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesViewModel.kt` (`loadCourses` catch only) and `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt` (`loadCourse` catch only). Leave sorting, filtering, and all StateFlow handling in both classes untouched.

steps:
1. Add the `android.util.Log` import and a `private const val TAG` companion to each class.
2. Replace both `e.printStackTrace()` calls with `Log.w(TAG, "<load failed>", e)`.
3. Keep the existing fallbacks identical: `loadCourses` keeps `newState` null so `_coursesState` is untouched; `loadCourse` leaves `_uiState` as-is.
4. Run the unit tests.

acceptance: `./gradlew testDefaultDebugUnitTest` green; with the device offline and data cleared, the courses tab still renders its empty state instead of crashing, and failures appear in logcat under the class tags.

size budget: ~8 changed lines, 2 files.

out of scope: no restructuring of `loadCourses`/`filterCourses`; no new error UI or retry behavior.

---

### 9. Drop the dead try/catch and printStackTrace calls in Meetup.getHashMap (roadmap 1+8, supports 9)

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

### 10. Replace printStackTrace in the Feedback and HealthExamination serializers (roadmap 1+8, supports 9)

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

signed: github copilot · kimi k3 · high effort
