# Repository-boundary work orders — 2026-09-10

Date: 2026-09-10
Base commit: `022ee7d1f50f1453b1741aee391f3be5c619b4eb`
Open PRs checked: could not check open PRs (GitHub CLI had no authentication, the checkout has no remote, and GitHub network access was unavailable).
Selection note: candidates were ranked by user impact divided by blast radius; the known hot files `TeamsRepositoryImpl.kt`, `SyncManager.kt`, and `UploadManager.kt` are deliberately excluded.

### 1. Remove the Room upload type from `PhotoUploader` (roadmap 1+5+9)
context: `PhotoUploader.uploadSubmitPhotos` imports `SubmitPhotosDao.UploadedPhoto` at `app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt:13` and constructs that DAO-owned type at line 69. This leaks Room through the service boundary and prevents the upload workflow from becoming platform-free; the repository already owns persistence after successful uploads.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/services/upload/PhotoUploader.kt` (`PhotoUploader.uploadSubmitPhotos`); `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepository.kt` (`UploadRepository` and `UploadedItemResult`); `app/src/main/java/org/ole/planet/myplanet/repository/UploadRepositoryImpl.kt` (`UploadRepositoryImpl`).
files: leave `SubmissionsRepository`, `SubmissionsRepositoryImpl`, `SubmitPhotosDao`, `FileUploader`, and every upload coordinator/manager unchanged.
steps:
1. Add an upload-repository operation that accepts the existing repository-owned successful-upload value and persists successful submit-photo identifiers and revisions.
2. Implement that operation in `UploadRepositoryImpl` by injecting `SubmitPhotosDao`; keep that DAO private and do not expose its type in its signature.
3. Change `uploadSubmitPhotos` to map successful network responses to the repository-owned value and call the new operation once per non-empty batch.
4. Remove the `SubmitPhotosDao.UploadedPhoto` import and preserve cancellation, concurrency limiting, attachment upload ordering, and listener callbacks.
5. Add or adjust focused unit coverage only if it already exists in one of the listed files; otherwise rely on compilation and the full unit suite.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes, and uploading submission photos still marks each successful row before uploading its attachment.
size budget: about 45 changed lines across 3 files.
out of scope: Do not redesign `FileUploader`, alter the HTTP endpoints, change batch size/concurrency, or touch the submissions repository.

---

### 2. Join meetup members in Room instead of assembling identities in Kotlin (roadmap 1+7+9)
context: `EventsRepositoryImpl.getJoinedMembers` at `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt:60` fetches member IDs, chunks them, then deduplicates users in Kotlin. `MeetupDao.getMemberUserIdsByMeetupId` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MeetupDao.kt:24` proves the relationship belongs in the data query, while callers only need the final members.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MeetupDao.kt` (`MeetupDao.getMemberUserIdsByMeetupId`); `app/src/main/java/org/ole/planet/myplanet/repository/EventsRepositoryImpl.kt` (`EventsRepositoryImpl.getJoinedMembers`).
files: leave `EventsRepository`, `EventsDetailViewModel`, `UserDao`, `CommunityRepositoryImpl`, and all team repositories unchanged.
steps:
1. Replace the ID-only meetup query with a DAO query that joins `meetup` to `users` by either local or remote user identifier and returns distinct user rows.
2. Preserve the existing exclusions for null and blank meetup membership user IDs in SQL.
3. Simplify `getJoinedMembers` to validate blank input and delegate once to the new DAO operation.
4. Remove no-longer-used `UserDao` injection/import from `EventsRepositoryImpl`, without changing its public repository contract.
5. Confirm duplicate meetup membership rows still result in one displayed member.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; an event detail screen shows the same joined members, including users referenced by `_id`, without duplicate rows.
size budget: about 25 changed lines across 2 files.
out of scope: Do not alter attendance semantics, meetup sync, event UI state, or the `users`/`meetup` schemas.

---

### 3. Query exact course-progress keys without a Cartesian match (roadmap 1+7+9)
context: `CourseProgressDao.getByCourseUsersAndSteps` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDao.kt:25` uses three independent `IN` clauses, so it can return combinations that were never requested. `ProgressRepositoryImpl` calls it at line 300 while reconciling specific progress records, creating both excess reads and a cross-user correctness risk.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/CourseProgressDao.kt` (`CourseProgressDao.getByCourseUsersAndSteps`); `app/src/main/java/org/ole/planet/myplanet/repository/ProgressRepositoryImpl.kt` (the caller around line 300).
files: leave `ProgressRepository`, `CourseStepDao`, `SubmissionDao`, `CoursesRepositoryImpl`, and course UI/viewmodels unchanged.
steps:
1. Inspect the reconciliation caller and preserve its exact definition of a course/user/step identity tuple.
2. Replace the broad DAO call with bounded exact-key lookups using the existing `findByCourseUserAndStep`, or another Room-supported exact predicate that introduces no new type/file.
3. Deduplicate requested tuples before querying so repeated sync records do not repeat database work.
4. Preserve null-user behavior and ensure records from another requested course, user, or step cannot be paired accidentally.
5. Remove `getByCourseUsersAndSteps` if it has no remaining callers.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; syncing progress for multiple users/courses updates only matching course-user-step records.
size budget: about 35 changed lines across 2 files.
out of scope: Do not change progress scoring, upload payloads, course completion rules, or database entities.

---

### 4. Aggregate rating lists in SQL before repository mapping (roadmap 1+7+9)
context: `RatingsRepositoryImpl.getRatings` at `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt:20` loads every rating of a type via `RatingDao.getByType` and reduces the rows in memory. The same DAO already uses SQL `COUNT` and `AVG` for one item in `getAggregate` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RatingDao.kt:24`, showing that bulk aggregation can stay behind Room.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/RatingDao.kt` (`RatingDao.getByType`, `RatingAggregate`, and a new bulk aggregate query); `app/src/main/java/org/ole/planet/myplanet/repository/RatingsRepositoryImpl.kt` (`RatingsRepositoryImpl.getRatings`).
files: leave `RatingsRepository`, `RatingsViewModel`, `ResourceViewerViewModel`, `Rating` entities, and upload methods unchanged.
steps:
1. Extend the DAO projection with the item key and add one grouped query returning count and average for every item of the requested type.
2. Fetch the current user's ratings for that type in one bounded DAO query when a user ID is present, rather than loading unrelated full rows.
3. Rebuild the existing `HashMap<String?, JsonObject>` contract from the aggregate projections plus the per-user rows.
4. Preserve null item/type semantics and the exact JSON fields produced by the current aggregation.
5. Remove `getByType` only if no other caller remains.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; course and resource rating lists show unchanged averages, totals, and the signed-in user's rating.
size budget: about 70 changed lines across 2 files.
out of scope: Do not change rating submission, rounding rules, prompt logging, sync parsing, or UI models.

---

### 5. Make My Life ordering and visibility a DAO responsibility (roadmap 1+7+9)
context: `LifeRepositoryImpl.getMyLifeByUserId` and `getMyLifeList` repeatedly call `distinctBy { it.dedupKey() }.sortedBy { it.weight }` at `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt:82` and line 90. `MyLifeDao` is the persistence boundary and can return stable ordered rows, reducing repeated allocation while keeping UI callers unaware of Room.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/MyLifeDao.kt` (`MyLifeDao` user and visible queries); `app/src/main/java/org/ole/planet/myplanet/repository/LifeRepositoryImpl.kt` (`getMyLifeByUserId`, `getMyLifeList`, and `getVisibleMyLifeList`).
files: leave `LifeRepository`, `LifeViewModel`, `MyLife`, adapters, and default-item resource construction unchanged.
steps:
1. Add deterministic `ORDER BY weight` clauses to the DAO queries used by the three repository reads.
2. Review `dedupKey` fields and express safe duplicate suppression in the DAO only if SQLite can preserve the same winning-row behavior deterministically.
3. Otherwise retain deduplication in the repository but remove all redundant Kotlin sorting now guaranteed by Room.
4. Keep seeded-default ordering identical on the first empty read and all later reads.
5. Ensure visible-only reads still exclude hidden items before ordering.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; the My Life screen retains saved order after restart and hidden entries stay absent.
size budget: about 25 changed lines across 2 files.
out of scope: Do not alter drag-and-drop writes, default labels/icons, seeding rules, or the entity schema.

---

### 6. Replace whole-tag-table scans with a scoped DAO query (roadmap 1+7+9)
context: `TagsRepositoryImpl.getTagsForIds` starts with `tagDao.getAll()` at `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt:19`, then filters and relates tags in Kotlin. `TagDao` already has scoped operations such as `getByDbAndLinkIds` and `getByIds` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TagDao.kt:24-28`, so callers should not pay for unrelated feature tags.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/TagDao.kt` (`TagDao` scoped link/parent queries); `app/src/main/java/org/ole/planet/myplanet/repository/TagsRepositoryImpl.kt` (`TagsRepositoryImpl.getTagsForIds`).
files: leave `TagsRepository`, courses/resources viewmodels, tag entities, sync insertion, and database configuration unchanged.
steps:
1. Trace the exact database type and link IDs accepted by `getTagsForIds`, including empty and duplicate IDs.
2. Use the existing scoped link query first and collect only its referenced parent tag IDs.
3. Resolve those parents with the existing ID query, preserving current output grouping/order and missing-parent behavior.
4. Delete the `getAll` dependency from this path; remove the DAO method only if repository-wide search confirms no caller.
5. Short-circuit empty inputs without a Room query.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; resource/course tag filters display the same labels and never include tags from another database type.
size budget: about 35 changed lines across 2 files.
out of scope: Do not modify tag sync JSON parsing, screen filtering UI, or database indices/schema.

---

### 7. Move submission-list enrichment behind `SubmissionsRepository` (roadmap 1+3+9+10)
context: `SubmissionViewModel` groups raw submissions, queries `UserRepository`, and joins fallback user names at `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt:64-82`. This cross-feature data leak makes the UI understand submission/user storage relationships instead of consuming repository-ready view state.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/ui/submissions/SubmissionViewModel.kt` (`filteredSubmissionsRaw` and `SubmissionViewData`); `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepository.kt` (`SubmissionsRepository`); `app/src/main/java/org/ole/planet/myplanet/repository/SubmissionsRepositoryImpl.kt` (`SubmissionsRepositoryImpl`).
files: leave `UserRepository`, `SubmissionDao`, `UserDao`, `SubmissionUiModel`, fragments/adapters, and PDF exporter unchanged.
steps:
1. Add a repository-owned, platform-free list-row projection in `SubmissionsRepository.kt` containing the submission, resolved submitter name, and group count.
2. Add one repository operation that filters by user/type/query, groups by parent, selects the newest submission, batch-resolves fallback users, and returns sorted projections.
3. Inject `UserDao` into `SubmissionsRepositoryImpl` for one batch fallback-name lookup; avoid per-row lookups and do not expose the DAO.
4. Simplify `SubmissionViewModel` to combine filter inputs with the new operation and map projections to `SubmissionUiModel`.
5. Remove its direct `UserRepository` dependency only if active-user resolution can remain correct through the submissions boundary; otherwise retain it solely for the active ID.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; submission tabs, search, newest-row selection, counts, titles, and fallback submitter names remain unchanged.
size budget: about 120 changed lines across 3 files.
out of scope: Do not change submission creation, upload/sync, detail/PDF generation, DAO schemas, or UI layout.

---

### 8. Collapse notification relationship lookups into one repository call (roadmap 1+3+7+9+10)
context: `NotificationsViewModel.loadNotifications` partitions payloads and coordinates four repository calls at `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt:73-137`. The repository already owns task-team and join-request relationships in `NotificationsRepositoryImpl` lines 175-282, so exposing those joins separately leaks data assembly into UI state code.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/ui/notifications/NotificationsViewModel.kt` (`loadNotifications`); `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepository.kt` (`NotificationsRepository`); `app/src/main/java/org/ole/planet/myplanet/repository/NotificationsRepositoryImpl.kt` (`NotificationsRepositoryImpl`).
files: leave notification/team DAOs, `TeamsRepositoryImpl`, notification fragments/adapters, workers, and sync code unchanged.
steps:
1. Define a repository-owned enrichment result containing payloads, task-team names, join-request details, and unread count.
2. Add one repository method accepting user ID, filter, and admin flag and returning that complete enrichment result.
3. Move ID/title collection, fallback join-request lookup, batching, and independent concurrency from `loadNotifications` into the implementation.
4. Keep message/date parsing and final `NotificationUiModel` formatting in the ViewModel because those are presentation concerns.
5. Remove superseded public lookup methods only when repository-wide search shows no other callers.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; task and join-request notifications retain team/member text, unread totals, filters, and selection behavior.
size budget: about 110 changed lines across 3 files.
out of scope: Do not change notification persistence, type inference, read/delete behavior, team repository internals, or navigation.

---

### 9. Read community configuration as one repository snapshot (roadmap 3+4+9+10)
context: `CommunityTabViewModel` calls `getParentCode`, `getCommunityName`, and `getPlanetType` separately at `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt:32-34`. Those values all come from one configuration boundary in `ConfigurationsRepositoryImpl` lines 393-402, and separate getters allow an internally inconsistent screen state if preferences change between reads.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/ui/community/CommunityTabViewModel.kt` (`CommunityTabViewModel.init`); `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepository.kt` (`ConfigurationsRepository` and a platform-free snapshot value); `app/src/main/java/org/ole/planet/myplanet/repository/ConfigurationsRepositoryImpl.kt` (`getPlanetType`, `getParentCode`, and `getCommunityName`).
files: leave `UserRepository`, `CommunityTabState`, `SharedPrefManager`, community fragments, DI modules, and server configuration workflows unchanged.
steps:
1. Define an immutable repository snapshot containing parent code, community name, and planet type.
2. Add one `getCommunityConfiguration` operation and implement it by reading the three existing preference values together.
3. Update `CommunityTabViewModel` to consume that snapshot while continuing to resolve planet code from the active user.
4. Retain the individual getters because confirmed callers elsewhere still use their narrower contracts.
5. Preserve nullable planet-type behavior and current empty-string defaults.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; the Community tab shows the same planet, parent, community name, and planet type after login and configuration changes.
size budget: about 35 changed lines across 3 files.
out of scope: Do not move active-user ownership into configurations, alter preferences, change DI bindings, or redesign `CommunityTabState`.

---

### 10. Filter individual surveys in Room instead of the repository (roadmap 1+7+9)
context: `SurveysRepositoryImpl.getIndividualSurveys` loads every survey and filters team-share and team ownership flags in Kotlin at `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt:268-271`. `ExamDao.getByType` at `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt:24` is broader than this repository contract, causing unnecessary entities to cross the data boundary.
priority: High user impact with a contained data-path change and no schema migration.
files: `app/src/main/java/org/ole/planet/myplanet/data/room/dao/ExamDao.kt` (`ExamDao.getByType` and a new individual-survey query); `app/src/main/java/org/ole/planet/myplanet/repository/SurveysRepositoryImpl.kt` (`SurveysRepositoryImpl.getIndividualSurveys`).
files: leave `SurveysRepository`, survey viewmodels/fragments, `SubmissionDao`, team/adoption queries, entities, and sync code unchanged.
steps:
1. Add a DAO query constrained to type `surveys`, `isTeamShareAllowed = 0`, and a null-or-empty `teamId`.
2. Match Room's stored Boolean representation and preserve the current handling of nullable/blank team IDs exactly.
3. Change `getIndividualSurveys` to delegate directly to the scoped DAO method with no Kotlin filter.
4. Keep the general `getByType` method because other repository operations still require all surveys.
5. Preserve the database's current row ordering rather than introducing a new sort policy.
acceptance: `./gradlew testDefaultDebugUnitTest` passes.
acceptance: `./gradlew compileDefaultDebugKotlin` passes; the individual Surveys screen shows only non-shareable surveys with no team while team-owned/adoptable lists remain unchanged.
size budget: about 12 changed lines across 2 files.
out of scope: Do not change survey adoption, submission counts, formatting, reminders, sync, or the Room schema.
