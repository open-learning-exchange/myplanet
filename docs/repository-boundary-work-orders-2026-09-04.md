# Repository-boundary work orders — 2026-09-04

Date: 2026-09-04  
Base commit: `9ff1273dc95f8cbd3590fca12ca821454b2e27bc`  
Open PRs checked: **could not check open PRs** (the checkout has no Git remote and `gh` has no authenticated host).  
Ranking: ordered by estimated user impact divided by blast radius after searching repository, DAO, service, and ViewModel call sites. Because PR files could not be enumerated, every order is deliberately narrow and agents must stop rather than edit a file that becomes owned by a review-ready or merge-ready PR.

### 1. Remove the Android text helper from voice persistence (roadmap 1+9)
context: `VoicesRepositoryImpl` imports `android.text.TextUtils` at app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt:3 and uses it in `getFilteredNews` at lines 454 and 459. A repository should not need an Android framework helper for null/empty String checks; using Kotlin semantics is behavior-preserving and directly removes one `android.*` dependency on the path toward roadmap 9.
files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt`, class `VoicesRepositoryImpl`, function `getFilteredNews`. Leave `VoicesRepository`, `NewsDao`, `NewsLogDao`, serialization, visibility parsing, and all voice UI files alone.
steps:
1. Replace both `TextUtils.isEmpty` checks with null-safe Kotlin String checks that retain the current null-and-empty behavior.
2. Remove only the now-unused `android.text.TextUtils` import and run the Kotlin import sorter.
3. Add no helper, API surface, or alternate filtering branch; keep the existing database query and result ordering.
4. Compile and run the default unit suite to catch nullability or overload-resolution regressions.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; community and team voices still include/exclude records exactly as before for null, empty, and populated `viewableId`/`viewIn` values.
size budget: about 3 changed lines in 1 file, comfortably below 150 lines and 5 files.
out of scope: Do not redesign voice visibility, Gson parsing, sync, upload, DAO queries, or introduce a multiplatform module.

---

### 2. Stop exporting a Room DAO projection through submissions (roadmap 1+5+9)
context: `SubmissionsRepository` imports `SubmitPhotosDao.UploadedPhoto` at app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt:7 and exposes it from `markPhotosUploadedBatch` at line 56. `PhotoUploader.uploadSubmitPhotos` constructs that DAO-owned type at app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt:62, leaking Room across the service/repository boundary and blocking a platform-free repository contract.
files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt` (`SubmissionsRepository.markPhotosUploadedBatch` and a repository-owned value type), `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (`markPhotosUploadedBatch`), and `app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt` (`uploadSubmitPhotos`). Leave `SubmitPhotosDao`, all other uploaders, `UploadRepository`, and submission UI files alone.
steps:
1. Define a small immutable repository-layer batch-update value type beside `SubmissionsRepository` with the existing local id, revision, and remote id fields.
2. Change `markPhotosUploadedBatch` to accept that type and remove the DAO nested-type import from the repository interface.
3. Map repository values to `SubmitPhotosDao.UploadedPhoto` only inside `SubmissionsRepositoryImpl` immediately before invoking the DAO.
4. Update `PhotoUploader` to construct the repository value type while preserving chunking, callback timing, and attachment uploads.
5. Clean imports without changing any public method beyond the batch parameter type.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; uploading multiple submission photos marks every successful row once and still uploads attachments after the batch mark.
size budget: about 20 changed lines in 3 files, below 150 lines and 5 files.
out of scope: Do not alter SQL, network payloads, retry rules, batch size, attachment behavior, or migrate other DAO projections.

---

### 3. Read course-session state through UserRepository (roadmap 3+4+9+10)
context: `TakeCourseViewModel` imports and injects `UserSessionManager` at app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt:17 and lines 32-35, then calls it in `loadCourse` at line 103. The same project already exposes current-user access on `UserRepository`; keeping service state out of the ViewModel tightens layering and makes its state suitable for portable Compose consumers under roadmaps 9 and 10.
files: Touch only `app/src/main/java/org/ole/planet/myplanet/ui/courses/TakeCourseViewModel.kt`, class `TakeCourseViewModel`, constructor and `loadCourse`. Leave `CoursesRepository`, `ProgressRepository`, `RatingsRepository`, `UserRepository`, course fragments/activities, and Hilt modules alone.
steps:
1. Replace the `UserSessionManager` constructor dependency with the existing `UserRepository` interface.
2. Load the current `UserEntity` through `UserRepository.getUserModel()` inside the existing coroutine.
3. Preserve the current load guard, loading/not-found states, course query, progress query, and error behavior.
4. Remove the obsolete service import and sort imports.
5. Run the ViewModel compilation and default tests; do not manually construct a second session abstraction.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; opening a course still displays the active user's progress and the not-found state remains unchanged.
size budget: about 4 changed lines in 1 file, below 150 lines and 5 files.
out of scope: Do not change course joining, rating prompts, progress calculations, UI rendering, or repository implementations.

---

### 4. Read community identity through UserRepository (roadmap 3+4+9+10)
context: `CommunityTabViewModel` imports and injects `UserSessionManager` at app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt:10 and lines 21-23, while configuration values already come through `ConfigurationsRepository`. Fetching the user through its repository removes the cross-layer service dependency and keeps the complete tab state behind repository contracts for future portable UI.
files: Touch only `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt`, class `CommunityTabViewModel`, constructor and initializer. Leave `CommunityTabState`, `ConfigurationsRepository`, `UserRepository`, community fragments, and DI modules alone.
steps:
1. Inject `UserRepository` in place of `UserSessionManager`.
2. Replace the initializer's `getUserModel` call with the equivalent existing repository call.
3. Keep parent code, community name, planet type, and planet-code fallback semantics identical.
4. Remove the service import, add the repository import, and sort imports.
5. Verify initialization still emits one complete non-null state rather than partial updates.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; the community tab still shows the active user's planet code and the configured parent/community identity.
size budget: about 4 changed lines in 1 file, below 150 lines and 5 files.
out of scope: Do not alter configuration storage, add navigation work, change `CommunityTabState`, or edit community UI.

---

### 5. Consolidate survey user lookup on UserRepository (roadmap 3+4+9+10)
context: `SurveysViewModel` already injects `UserRepository` at app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt:31-36 but also injects `UserSessionManager` and calls it from `loadSurveys` and `adoptSurvey` at lines 89 and 173. Two current-user sources make tests and portable state harder to reason about; one repository-backed source removes the service leak without changing survey behavior.
files: Touch only `app/src/main/java/org/ole/planet/myplanet/ui/surveys/SurveysViewModel.kt`, class `SurveysViewModel`, constructor, `loadSurveys`, and `adoptSurvey`. Leave `SurveysRepository`, `SubmissionsRepository`, `UserRepository`, survey fragments/adapters, and utility filtering alone.
steps:
1. Remove the `UserSessionManager` constructor parameter and import.
2. Use the already injected `UserRepository.getUserModel()` at both current-user call sites.
3. Preserve nullable user-id handling in survey-info and adoption calls.
4. Keep loading, success, failure, search, sort, and reloading state transitions unchanged.
5. Sort imports and run compilation plus the default unit suite.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; individual/team survey lists and adoption still use the active user's id and emit the same messages.
size budget: about 5 changed lines in 1 file, below 150 lines and 5 files.
out of scope: Do not modify survey SQL, adoption payloads, filtering performance, strings, or any composable/fragment.

---

### 6. Move the resource-open activity key behind the repository contract (roadmap 1+3+9)
context: `UserProfileViewModel` imports `UserSessionManager` solely for `KEY_RESOURCE_OPEN` at app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt:16 and uses it in activity queries at lines 131 and 133. A ViewModel should not know a session-service storage token; the activity repository can own the resource-open category while retaining its general query methods.
files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/ActivitiesRepository.kt`, interface `ActivitiesRepository`, and `app/src/main/java/org/ole/planet/myplanet/ui/user/UserProfileViewModel.kt`, class `UserProfileViewModel` initializer. Leave `ActivitiesRepositoryImpl`, `UserSessionManager`, profile fragments, activity DAOs, and upload methods alone.
steps:
1. Add repository-level convenience methods for most-opened resource and resource-open count that require only the user name.
2. Implement those convenience methods as interface defaults delegating to the existing typed methods with the current resource-open token represented inside the repository contract.
3. Switch `UserProfileViewModel` to the convenience methods and remove its service import.
4. Preserve concurrent `async` loading and all displayed string formatting.
5. Compile and run the default unit tests to validate default-interface dispatch and mocks.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; profile statistics still show identical most-opened-resource and total-open values.
size budget: about 12 changed lines in 2 files, below 150 lines and 5 files.
out of scope: Do not rename persisted activity types, rewrite uploads, touch DAOs, or change profile presentation text.

---

### 7. Make My Life seeding return the inserted dashboard rows (roadmap 1+7+9)
context: `LifeRepositoryImpl.getMyLifeByUserId` reads through `MyLifeDao.getByUserId`, calls `seedMyLifeIfEmpty`, and then performs the same query again at app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:80-86. `seedMyLifeIfEmpty` already constructs the complete inserted list at lines 139-154, so returning that result avoids a Room round trip on first dashboard load and clarifies the repository/DAO relationship.
files: Touch `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt`, class `LifeRepositoryImpl`, functions `getMyLifeByUserId` and `seedMyLifeIfEmpty`; and `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt`, function `insertAll`, only if Room must return inserted row ids. Leave `LifeRepository`, dashboard UI, preferences cache schema, and `MyLife` entity alone.
steps:
1. Refactor the private seeding path to return the exact newly inserted detached `MyLife` values when it wins the mutex, or an empty/no-result signal when data already exists.
2. In `getMyLifeByUserId`, use the returned seeded values instead of issuing the unconditional second `getByUserId` query.
3. Preserve deduplication, weight ordering, null-user normalization, mutex protection, and the public `seedMyLifeIfEmpty` contract.
4. If DAO insert ids are unnecessary, leave `MyLifeDao` byte-for-byte unchanged; otherwise use Room's supported bulk-insert return and map without another SELECT.
5. Run tests and compilation, including an empty-table and concurrent-seed mental/automated check.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; a first-time dashboard shows the same ordered default Life cards and concurrent loads do not create duplicates.
size budget: about 25 changed lines in 1-2 files, below 150 lines and 5 files.
out of scope: Do not change cache JSON, default card content, visibility rules, entity schema, migrations, or preference ownership.

---

### 8. Expose team update signals through TeamsRepository (roadmap 1+3+5+9+10)
context: `TeamViewModel` directly imports and injects `RealtimeSyncManager` at app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt:20 and lines 30-41. This makes UI state depend on a sync service rather than its feature repository; a repository update flow provides a stable boundary for Android UI today and portable state collection later.
files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/teams/TeamViewModel.kt`, `TeamViewModel.getTeamUpdateFlow`; `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepository.kt`, interface `TeamsRepository`; and `app/src/main/java/org/ole/planet/myplanet/repository/TeamsRepositoryImpl.kt`, class `TeamsRepositoryImpl`. Leave `RealtimeSyncManager`, team fragments, all team DAOs, `TeamsSyncRepository`, and sync workers alone.
steps:
1. Add a narrowly named team-update Flow method to `TeamsRepository` whose return type matches the existing `updatesFor("teams")` stream.
2. Inject/use the existing realtime manager inside `TeamsRepositoryImpl` and delegate with the fixed team collection key.
3. Remove `RealtimeSyncManager` from `TeamViewModel` and delegate `getTeamUpdateFlow` to `TeamsRepository`.
4. Preserve all task/team loading, filtering, join, leave, and create behavior.
5. Clean imports and verify Hilt constructor resolution plus unit compilation.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; team screens still refresh after realtime team updates without the ViewModel importing a sync service.
size budget: about 15 changed lines in 3 files, below 150 lines and 5 files.
out of scope: Do not redesign realtime sync, generalize collection names, alter WorkManager, or modify team database queries.

---

### 9. Move viewer file operations behind ResourcesRepository (roadmap 1+3+9+10)
context: `ResourceViewerViewModel` injects Android `Context`, initializes Android PDFBox, checks files, and starts a download service at app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt:3-20 and lines 79-95. These data/file operations bypass the repository boundary and keep a prospective Compose screen tied to Android despite already having `ResourcesRepository`.
files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/viewer/ResourceViewerViewModel.kt`, functions `getExternalFilesDir`, `downloadResource`, and `extractPdfText`; `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepository.kt`, interface `ResourcesRepository`; and `app/src/main/java/org/ole/planet/myplanet/repository/ResourcesRepositoryImpl.kt`, class `ResourcesRepositoryImpl`. Leave viewer Activity/Fragment, playback preferences, `AuthSessionUpdater`, `DownloadUtils`, and resource DAOs alone.
steps:
1. Add repository operations matching the three existing ViewModel file behaviors and return the same values.
2. Move Context, file-existence/download-service, and PDF extraction work into `ResourcesRepositoryImpl` using its application context and dispatcher conventions.
3. Make the ViewModel delegate to the repository and remove file/PDF/Android context dependencies that become unused.
4. Preserve empty-string-on-PDF-error behavior and the no-download-when-file-exists guard.
5. Sort imports, compile, and run the default tests.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; opening downloaded PDFs still extracts text, missing resources still enqueue once, and playback behavior is unchanged.
size budget: about 45 changed lines in 3 files, below 150 lines and 5 files.
out of scope: Do not replace PDFBox, change storage locations, migrate playback preferences, alter authentication, or convert the viewer UI to Compose.

---

### 10. Hoist notification text resolution out of the ViewModel (roadmap 3+6+10)
context: `NotificationsViewModel` injects Android `Context` and reads `R.string` directly at app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:3-31 and lines 311-319, 378-405, and 428-430. This couples repository-fed notification state to Android resources and prevents the ViewModel state from being consumed unchanged by portable Compose UI.
files: Touch `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt`, class `NotificationsViewModel`, `NotificationGroup`, `typeLabelFor`, `formatNotification`, and `formatTaskNotification`; `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsFragment.kt`, notification-state collection; and `app/src/main/java/org/ole/planet/myplanet/model/Notification.kt`, notification presentation text type. Leave `NotificationsRepository`, notification DAO/entity files, adapters, XML strings, and navigation alone.
steps:
1. Introduce a small ViewModel-owned text descriptor that represents either raw text or a string resource plus format arguments without resolving it.
2. Replace Context-produced labels/messages in internal notification presentation data with descriptors while keeping raw server messages raw.
3. Change the notification presentation model to carry the descriptor, remove Context injection and all direct `getString` calls from the ViewModel.
4. Resolve descriptors with Fragment resources immediately before submitting notification items to the existing adapter.
5. Preserve grouping order, HTML emphasis inputs, storage thresholds, selection state, unread counts, repository calls, and adapter behavior.
independence: This order owns only the files named above and must not absorb adjacent cleanup.
merge order: No prerequisite task is required; implement and review this order on its own.
risk control: Preserve current public behavior and stop if a newly open review-ready or merge-ready PR owns a listed file.
acceptance: `./gradlew testDefaultDebugUnitTest` and `./gradlew compileDefaultDebugKotlin` pass; notification groups, task/resource/storage text, selection, and read/delete actions display identically after the existing UI resolves descriptors.
size budget: about 100 changed lines in 3 files, below 150 lines and 5 files.
out of scope: Do not edit XML resources, notification persistence/sync, adapters, navigation, or build a Compose screen in this task.
