# Realm → Room migration

Tracking document for migrating myPlanet's local persistence from **Realm-Java 10.19.0 (EOL)**
to **AndroidX Room 2.8.4**.

## Strategy

- **Coexistence, green at every commit.** Room is added *alongside* Realm. Each domain is moved
  to Room independently; the build compiles and unit tests pass after every step. Realm (plugin,
  deps, `RealmMigrations`, `DatabaseService`, `RealmRepository`, and the now-dead `Realm*` model
  classes) is removed only in the final commit, once nothing references it.
- **Drop-and-resync, no data copy.** Existing on-device Realm data is *not* migrated into Room.
  On first launch after upgrade the Realm file is discarded and Room starts empty, then re-pulls
  from the Planet/CouchDB server. Room is therefore configured with
  `fallbackToDestructiveMigration(true)`. (User-decision: acceptable loss of unsynced local-only
  data such as drafts.)
- **Leave dead Realm models in place until the end.** Removing a `RealmObject` subclass changes
  Realm's derived schema and would force a Realm schema-version bump + migration mid-transition.
  To avoid destabilising the still-live Realm store, each migrated `Realm*` model class is left
  untouched (dead code) until Realm is deleted wholesale in the final commit.

## The hard part: three generic subsystems

The bulk of the work is *not* the 38 model classes — it is that the data layer is built on
Realm's **generic runtime APIs**. Three subsystems are generic over `RealmObject`/`Realm` and are
shared by many domains, so they must be re-architected (not just ported per-model):

1. **`repository/RealmRepository`** — base class for all 23 repositories. Exposes
   `queryList(clazz){ equalTo(...) }`, `findByField`, `count`, `queryListFlow`, `save/update/delete`,
   `withRealm`, `executeTransaction` — generic over any `RealmObject`. Room validates SQL at compile
   time against per-entity DAOs, so each of the **207 `.where()` call sites** becomes a concrete DAO
   query. Reactive `queryListFlow` maps to Room `Flow<List<T>>` DAO queries.
2. **`services/upload/UploadConfig` + `UploadCoordinator`** — generic upload framework keyed by
   `modelClass: KClass<T : RealmObject>` with `queryBuilder: (RealmQuery<T>) -> RealmQuery<T>` and
   `additionalUpdates: (Realm, T, …)`. Must become database-agnostic (e.g. `fetchPendingItems` +
   DAO-backed update hooks) before any uploadable model can move.
3. **`services/sync/TransactionSyncManager`** — pulls CouchDB docs and writes them into Realm via
   `createObject`/`RealmList` inside each repository's `insert()`. Each `insert()` is rewritten to
   build Room entities and call DAO upserts.

Because these are shared, **no synced/uploaded model is cleanly separable** until the generic
frameworks are ported. Recommended order:

1. Foundation (done) + self-contained domains that bypass all three frameworks (Dictionary).
2. Re-architect `RealmRepository` → `RoomRepository` surface (or move repos to direct DAO
   injection) and port the reactive flow helper.
3. Re-architect the upload framework to be Room-based.
4. Port `TransactionSyncManager` + per-repository `insert()`.
5. Convert the remaining model domains + their DAOs + repositories + tests, one at a time.
6. Delete Realm (plugin, deps, `RealmMigrations`, `DatabaseService`, `RealmRepository`, all
   `Realm*` model classes, `librealm-jni.so` `doNotStrip`, `enableJetifier`).

## Entity mapping rules

| Realm | Room |
|-------|------|
| `open class X : RealmObject()` | `@Entity(tableName="…") class X` (or `data class`) |
| `io.realm.annotations.PrimaryKey` | `androidx.room.PrimaryKey` |
| `io.realm.annotations.Index` (field) | `@Entity(indices=[Index("field")])` |
| `io.realm.annotations.Ignore` | `androidx.room.Ignore` |
| `RealmList<String>?` | `List<String>?` + `Converters` (JSON) |
| `RealmList<RealmChild>?` (nested) | child is its own `@Entity` with a parent-id column; the parent's list is an `@Ignore` field populated by the repository (or a Room `@Relation` POJO). Applies to `courseSteps`, `attachments`, `answers`, `conversations`. |

New Room code lives under `data/room/` (`entity/`, `dao/`, `AppDatabase`, `Converters`) and is
wired through `di/RoomModule`.

## Progress

- [x] Build env verified (Gradle 9.6.1 + Android SDK; `compileDefaultDebugKotlin` green).
- [x] Room deps added (coexisting with Realm).
- [x] Foundation: `Converters`, `AppDatabase`, `RoomModule`.
- [x] **Dictionary** domain migrated end-to-end (`DictionaryEntity`, `DictionaryDao`,
      `DictionaryActivity`; legacy `RealmDictionary` is now a plain compatibility DTO). First proven template (raw-Realm activity).
- [x] Realm transition config: `DatabaseService` now uses `deleteRealmIfMigrationNeeded()` so a
      model leaving the Realm schema recreates the Realm file instead of crashing (drop-and-resync).
- [x] **Life** domain migrated end-to-end (`MyLife` is now a Room `@Entity`, `MyLifeDao`,
      `LifeRepositoryImpl` off `RealmRepository`, both Life test files ported). First proven
      *repository* template (in-place model conversion, class name kept so UI is untouched).
- [x] **Personals** domain migrated end-to-end (`Personal` now a Room `@Entity` with
      `@JvmField` on `id`/`_id` to avoid Room's ambiguous-accessor error, `PersonalDao` with a
      reactive `Flow` query, `PersonalsRepositoryImpl` off `RealmRepository`, test ported).
- [x] **Retry** domain migrated end-to-end (`RetryOperation` now a Room `@Entity`;
      Realm-based `createFromRetryFailure` factory replaced with a pure one; `RetryDao` with
      status-filtered queries/updates; `RetryRepositoryImpl` off `RealmRepository`; test ported).
- [x] **Community** domain migrated (`Community` now a Room `@Entity`; `CommunityDao` with a
      `@Transaction replaceAll`; `CommunityRepositoryImpl` keeps `RealmRepository` only for the
      still-Realm `Meetup` insert, using `CommunityDao` for community rows). First proven
      *coexistence-within-one-repo* template. Dropped a Realm-only `isValid` check in the sync UI.
- [x] **Upload framework made Room-capable**: added `RoomUploadConfig<T: Any>` + a parallel
      `UploadCoordinator.uploadRoom()` path (DB-agnostic: `fetchPendingItems` + DAO `markUploaded`),
      relaxed `UploadSerializer`/`PreparedUpload` to `T: Any`. The 16 Realm `UploadConfig`s are
      untouched. **Finding:** the sync side (`TransactionSyncManager`) already delegates to each
      repo's `insert()`, so synced-only domains need no framework change — only uploaded ones did.
- [x] **UserChallengeActions** migrated (single-`id` Room `@Entity`, `UserChallengeActionsDao`,
      2 sites in `ActivitiesRepositoryImpl` off the generic Realm API; repo keeps `RealmRepository`
      for its other models). NB: transient KSP `"Dao could not be resolved"` failures during this
      work were **corrupted incremental/daemon build state** from rapid iterative builds, not a
      real blocker — `./gradlew --stop` + a clean build clears them.
- [x] **ApkLog** migrated through the new path (`ApkLog` now a Room `@Entity`, `ApkLogDao`,
      `CrashLog` is a `RoomUploadConfig`, `MainApplication.saveLogToRoom` + crash-log sweep write
      via the DAO, `uploadCrashLog()` uses `uploadRoom`). First uploadable model on Room.
- [x] **TeamNotification** migrated (single-id Room `@Entity`, `TeamNotificationDao`; converted
      3 sites across `NotificationsRepositoryImpl` + `VoicesRepositoryImpl`, both kept on
      `RealmRepository` for their other models; test constructors updated).
- [x] **Certification** migrated (`Certification` Room `@Entity`, `CertificationDao` with a
      LIKE-based `countByCourseId`; `isCourseCertified` + the sync bulk-insert converted). First
      proven **sync-bulk-insert** conversion: the per-table `insert` moves from the shared Realm
      `executeTransaction` dispatch in `TransactionSyncManager` to the outer suspend `when` +
      a DAO upsert. Template for the remaining synced models.
- [x] **Chat** migrated (`ChatHistory` Room `@Entity`, `ChatDao`; `ChatRepositoryImpl` off
      `RealmRepository` entirely). First proven **nested-relationship** pattern: `RealmConversation`
      (no independent identity, never queried alone) becomes a plain class stored as an embedded
      JSON `List<RealmConversation>` via a `Converters` type converter — the right mapping for
      value-object child lists. (Child types that ARE queried independently — courseSteps,
      answers, attachments, examQuestions — will instead need their own tables.) Both Chat test
      files ported to mock `ChatDao`.
- [x] **Feedback** migrated (both uploaded AND synced). `Feedback` Room `@Entity`
      (@JvmField id/_id; messages JSON string; @Ignore on computed messageList/message);
      `FeedbackDao` with two reactive `Flow` queries; `FeedbackRepositoryImpl` off
      `RealmRepository`; `Feedback` upload config -> `RoomUploadConfig` (markUploaded sets
      isUploaded via DAO). Sync path unchanged (already suspend/outer). Model test +
      repo test + UploadManager test updated.
- [x] **Rating** migrated (uploaded + synced). `Rating` Room `@Entity` (@JvmField id/_id);
      `RatingDao` (by-type, by-type-and-item, find-by-type-user-item, pending-uploads with the
      guest filter folded into the query, markUploaded); `RatingsRepositoryImpl` keeps
      `RealmRepository` only for still-Realm `RealmUser` lookups; upload config ->
      `RoomUploadConfig`. Repo aggregation moved from Realm `average()`/`RealmResults` to
      in-memory over DAO lists. Repo test + model test + UploadManager test updated.
- [x] **Tag** migrated (synced-only). `TagEntity` Room `@Entity` (@JvmField id/_id; `attachedTo`
      `RealmList<String>` → `List<String>?` via `Converters`; `@Index` on name/tagId/db; kept the
      `toTag()`/`getTagsArray` POJO helpers); `TagDao` (parent-tag filter, by-db-and-linkId(s),
      by-ids, by-names, by-db-and-tagIds, upsert); `TagsRepositoryImpl` off `RealmRepository`
      entirely (child aggregation, linked-tag resolution, and sync `insert` all via the DAO).
      Sync `"tags"` dispatch moved to the outer suspend `when` (drop `bulkInsertFromSync`).
      `CoursesRepositoryImpl.filterCourses` now resolves tag→course links via `TagDao` before the
      Realm course query (injects `tagDao`). First proven **`RealmList<String>` → JSON `List`**
      mapping. Repo test rewritten to mock `TagDao`; `CoursesRepositoryImplTest` constructor updated.
- [x] **Meetup** migrated (synced + uploaded, cross-repo). `Meetup` Room `@Entity`
      (non-null PK `id`; `@Index` on meetupId/teamId/userId; all-scalar fields, no converters);
      the Realm `insert`/`insertList`/`getMyMeetUpIds(realm,…)` companions replaced with pure
      `fromJson(...)` + `getMyMeetUpIds(List)`. `MeetupDao` (by-team/meetupId/id/user, members,
      by-meetupIds, pending-uploads, upsert). `EventsRepositoryImpl` off the generic Realm API
      (keeps `RealmRepository` only to resolve `RealmUser` members, which is still Realm);
      `CommunityRepositoryImpl` now fully off `RealmRepository` (sync insert merges via the DAO);
      `UserRepositoryImpl.getShelfData` fetches meetup ids via `MeetupDao` before its Realm block.
      `Meetups` upload config → `RoomUploadConfig` (markUploaded sets meetupId/rev + clears
      `updated` via the repo); `UploadManager.uploadMeetups` uses `uploadRoom`. Model + repo +
      upload-manager + user-repo constructor tests updated. Also fixed a latent Tag-migration
      fallout: `ResourcesViewModelTest` can no longer mockk `TagEntity.id` (now a `@JvmField`), so
      it uses a real instance.
- [x] **SearchActivity** migrated (uploaded-only local activity log). `SearchActivity` is now
      a Room `@Entity`; `SearchActivityDao` owns pending upload lookup, inserts, and upload
      acknowledgements; course/resource search logging now writes through the DAO; upload config
      uses `RoomUploadConfig`.
- [x] **CourseActivity** migrated (uploaded-only course visit log). `CourseActivity` is now
      a Room `@Entity`; `CourseActivityDao` owns visit inserts, pending upload lookup, and upload
      acknowledgements; course visit logging writes through the DAO; upload config uses
      `RoomUploadConfig`.
- [x] **ResourceActivity** migrated (uploaded + local read paths). `ResourceActivity` is
      now a Room `@Entity`; `ResourceActivityDao` owns resource-open/sync inserts, counts,
      most-opened lookups, opened-resource observation, pending upload lookup, and upload
      acknowledgements; both regular and sync upload configs use `RoomUploadConfig`.
- [x] **SubmitPhotos** migrated (uploaded photo submissions). `SubmitPhotos` is now a
      Room `@Entity`; `SubmitPhotosDao` owns photo inserts, pending lookup, id lookups, and
      upload acknowledgements; repository/photo uploader paths use the DAO, and the legacy upload
      config is Room-capable.
- [x] **NewsLog** migrated (uploaded-only activity log). `NewsLog` is now a Room
      `@Entity`; `NewsLogDao` owns pending lookup, inserts, and upload acknowledgements; upload
      config uses `RoomUploadConfig`.
- [x] **TeamActivities / TeamLog** migrated (uploaded + synced team visit log). `TeamLog` is
      now a Room `@Entity`; `TeamLogDao` owns visit counts, last-visit lookups, sync upserts,
      pending uploads, and upload acknowledgements; team activity sync now runs outside the legacy
      Realm transaction path and upload config uses `RoomUploadConfig`.
- [x] **OfflineActivity / login activities** migrated (custom uploaded + synced login log).
      `OfflineActivity` is now a Room `@Entity`; `OfflineActivityDao` owns offline login
      counts/flows, last-login lookups, pending upload reads, upload acknowledgements, and sync
      upserts with the existing `_id` plus `loginTime`/`user` fallback dedupe behavior.
- [x] **CourseProgress** migrated (uploaded + synced progress records). `CourseProgress` is
      now a Room `@Entity`; `CourseProgressDao` owns user/course progress reads, completion
      records, save/update paths, pending upload reads, upload acknowledgements, and sync upserts
      that preserve locally-passed steps when server progress lags.
- [x] **RemovedLog** migrated (local shelf tombstones). `RemovedLog` is now a Room
      `@Entity`; `RemovedLogDao` owns add/remove tombstone writes, bulk cleanup when resources or
      courses are re-added, and shelf merge filtering for removed resources/courses.
- [x] **TeamTask** migrated (uploaded + synced task rows). `TeamTask` is now a Room
      `@Entity`; `TeamTaskDao` owns task flows, due-task notification lookups, title/id lookups,
      sync upserts, pending-upload reads, and upload acknowledgements; task repository and
      notification lookups use the DAO, and the upload config now uses `RoomUploadConfig`.
- [ ] Migrate the remaining ~7 uploadable models to `RoomUploadConfig` + the synced-only domains.
- [x] **Notification** migrated (synced local notification rows). `AppNotification` is now a
      Room `@Entity`; `NotificationDao` owns unread counts, read/sync marking, list filters,
      deletes, single-doc upserts, and sync bulk upserts. Notification sync now runs outside the
      legacy Realm transaction path.
- [x] **Achievement** migrated (synced + custom uploaded profile achievement docs).
      `Achievement` is now a Room `@Entity` with JSON-backed `List<String>` fields;
      `AchievementDao` owns initialization, edits, pending-upload reads, upload acknowledgements,
      and sync bulk upserts. Achievement sync now runs outside the legacy Realm transaction path.
- [x] **HealthExamination** migrated (synced + custom uploaded health records).
      `HealthExamination` is now a Room `@Entity`; `HealthExaminationDao` owns profile/id
      lookups, pending upload reads, upload acknowledgements, user-id fixes after user upload,
      and sync bulk upserts. Health sync now runs outside the legacy Realm transaction path.
- [x] **News / Voices** migrated (synced + custom uploaded discussion posts — the largest single
      domain, ~35 referencing files). `RealmNews` is now a Room `@Entity` (class name kept so the
      voices/teams/community UI is untouched; `@JvmField` id/_id; `imageUrls`/`labels`
      `RealmList<String>` → `List<String>?` via `Converters`; computed getters marked
      `@get:Ignore`; the Realm-managed `createNews(...,Realm,...)` companion replaced with a pure
      unmanaged factory). `NewsDao` owns by-id/_id/newsId reads, top-level + top-level-message
      lists and their `Flow`s, replies, reply counts, planet/team/time-range queries, team-chat
      counts, upserts and cascade deletes. `VoicesRepositoryImpl` moved off the generic Realm API
      onto `NewsDao` (keeps `RealmRepository` only for the still-Realm `RealmMyLibrary` lookups);
      team/community visibility filtering runs in-memory (mirrors `isVisibleToUser`) instead of
      Realm `contains()`; recursive reply deletion collects ids then `deleteByIds`. Sync
      `insertNewsList`/`insertNewsFromJson` build entities + DAO upsert; the custom news upload
      path (`getNewsForUpload`/`markNewsUploaded`) uses the DAO. `NotificationsRepositoryImpl`
      team-chat counts moved to `NewsDao`; `VoicesAdapter` drops Realm-only `isValid` liveness
      checks (POJOs are always valid). Repo/label-manager tests rewritten to mock `NewsDao` (and
      use real `RealmNews` instances since `id` is now a `@JvmField`).
- [x] **MyLibrary / Resources** migrated (synced + uploaded — the largest, most-coupled domain,
      queried across 5 repos). `RealmMyLibrary` is now a Room `@Entity` (class name kept; `@JvmField`
      id/_id; the six `RealmList<String>` fields → `List<String>?`; `attachments`
      `RealmList<RealmAttachment>` → embedded JSON `List<RealmAttachment>` with `RealmAttachment` now
      a plain value class + a `Converters` type converter; `isResourceOffline()` `@Ignore`d and the
      duplicate list-setter dropped to resolve Room accessor ambiguity; the Realm-managed
      `insertMyLibrary(...,Realm,...)`/`save(...)` companions replaced with pure factories that
      merge into a passed-in `existing`). `MyLibraryDao` (~30 queries). **Key decision:** shelf
      membership (formerly a `RealmList<String>` `equalTo`) is matched with `userId LIKE :pattern
      ESCAPE '\'` where the repo builds `%"<escaped-id>"%` so the quotes delimit exact entries.
      `ResourcesRepositoryImpl` fully rewritten onto the DAO (title/facet filtering in-memory);
      `CoursesRepositoryImpl`, `TeamsRepositoryImpl`, `VoicesRepositoryImpl`, `UserRepositoryImpl`
      moved their library reads to the DAO (keeping `RealmRepository` for their still-Realm models).
      **Sync coupling:** resources embedded in synced course steps can't be DAO-written inside the
      Realm course transaction, so they're queued during `bulkInsertFromSync` and flushed via
      `flushPendingCourseResources()` (called after the courses batch in `TransactionSyncManager`),
      preserving the find-and-merge semantics. **Upload:** `getResourcesConfig` → `RoomUploadConfig`;
      the private-resource team-link creation (a still-Realm `RealmMyTeam`) moves into
      `markResourceUploaded`, which updates the library via the DAO then does the team write in a
      Realm transaction. `BaseRecyclerFragment`/`BaseResourceFragment` type checks updated (library
      is no longer a `RealmObject`). Resource/upload/repo tests rewritten to mock `MyLibraryDao`.
- [x] **CourseStep + course exam read paths moved to Room**: `CourseStepDao` now serves
      per-course, batched course-id, and single-step lookups; course step progress and course-step
      detail screens hydrate legacy `RealmCourseStep` compatibility objects from
      `RoomCourseStepEntity` instead of querying Realm. `ExamDao` now serves course/step exam and
      survey reads for course progress, course-step detail, exam counts, and local-progress cleanup,
      hydrating legacy `RealmStepExam` compatibility objects from `RoomExamEntity`. Sync writes for
      both tables were already landing in Room.
- [x] **Submission survey/exam metadata reads moved to Room**: `SubmissionsRepositoryImpl` now
      resolves survey titles/maps, exam question counts, bulk-survey parent ids, step completion,
      unfinished/pending survey metadata, and submission-detail question rows through `ExamDao` and
      `QuestionDao` instead of Realm queries. `RoomQuestionEntity` hydrates legacy
      `RealmExamQuestion` compatibility objects, including correct-choice data for grading display.
- [x] **Submission list/detail reads moved to Room**: `SubmissionDao` and `AnswerDao` now back
      submission lookup by id/user/ids, pending-survey lists, pending-offline counts, pending exam
      result counts, parent/status submission lists, submission item summaries, and completion checks.
      `RoomSubmissionEntity` and `RoomAnswerEntity` hydrate legacy `RealmSubmission`/`RealmAnswer`
      compatibility objects with child answers grouped in one DAO batch per submission list.
- [x] **Submission write helpers moved to Room**: `saveSubmission`, `markSubmissionComplete`,
      `deleteExamSubmissions`, and `getOrCreateSubmission` now use `SubmissionDao`/`AnswerDao`
      instead of Realm transactions. This keeps survey-response creation, completion marking, and
      local exam-submission cleanup on the same Room tables used by the read paths.
- [ ] Remaining ~26 model domains.
- [ ] Migrate 39 Realm-based test files.
- [ ] Remove Realm; full `assembleDefaultDebug` + `testDefaultDebugUnitTest` green.

## Local build environment (ephemeral sessions)

This repo needs Gradle 9.6.1 (AGP 9.2.1) + Android SDK (`platforms;android-37.0`,
`build-tools;37.0.0`). In restricted environments where `services.gradle.org` redirects to a
GitHub-gated release, fetch the Gradle distribution from a non-GitHub mirror
(e.g. `https://mirrors.cloud.tencent.com/gradle/gradle-9.6.1-bin.zip`) into the wrapper cache, and
install the SDK from `dl.google.com` via `cmdline-tools`. Set `sdk.dir` in `local.properties`.
