import 'dart:io';

import 'package:drift/drift.dart';
import 'package:meta/meta.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

// Imported for the generated part file, which constructs the type converters
// declared on the table columns.
import 'converters.dart';
import '../../core/crypto/health_cipher.dart';
import 'tables.dart';

part 'app_database.g.dart';

/// Port of `data/room/AppDatabase.kt`.
///
/// Drift is the closest analogue to Room: SQLite underneath, DAOs on top,
/// queries validated at build time, and `Stream` results in place of Room's
/// `Flow`. The Kotlin database uses `fallbackToDestructiveMigration(true)` under
/// the drop-and-resync policy documented in `docs/realm-to-room-migration.md`;
/// [_migration] keeps that policy so a schema bump re-pulls from the server
/// rather than needing a hand-written migration.
@DriftDatabase(
  tables: [
    Users,
    MyLibraryTable,
    Courses,
    CourseSteps,
    RemovedLogs,
    DictionaryEntries,
    Notifications,
    MyLifeEntries,
    PersonalEntries,
    Ratings,
    OutboxEntries,
    Submissions,
    SubmissionAnswers,
    SubmissionQuestions,
    Meetups,
    Surveys,
    SurveyQuestions,
    Exams,
    ExamQuestions,
    NewsEntries,
    Teams,
    TeamTasks,
    ChatEntries,
    FeedbackEntries,
    HealthExaminations,
    CourseProgress,
    Certifications,
    OfflineActivities,
    ResourceActivities,
    CourseActivities,
    TeamNotifications,
    DownloadQueueEntries,
    SubmitPhotosTable,
    TeamLogTable,
    SearchActivities,
    Tags,
    Achievements,
    UserChallengeActions,
  ],
  daos: [
    UserDao,
    MyLibraryDao,
    CourseDao,
    RemovedLogDao,
    DictionaryDao,
    NotificationDao,
    MyLifeDao,
    PersonalDao,
    RatingDao,
    OutboxDao,
    NewsDao,
    TeamDao,
    TeamTaskDao,
    SubmissionDao,
    MeetupDao,
    SurveyDao,
    ExamDao,
    ChatDao,
    FeedbackDao,
    HealthExaminationDao,
    CourseProgressDao,
    CertificationDao,
    OfflineActivityDao,
    ResourceActivityDao,
    CourseActivityDao,
    TeamNotificationDao,
    DownloadQueueDao,
    SubmitPhotosDao,
    TeamLogDao,
    SearchActivityDao,
    TagDao,
    AchievementDao,
    UserChallengeActionDao,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  /// The on-device database, under the app's documents directory.
  AppDatabase.open() : super(_openConnection());

  /// An isolated in-memory database, for tests.
  AppDatabase.memory() : super(NativeDatabase.memory());

  @override
  int get schemaVersion => 49;

  /// Tables holding local intent the server cannot give back.
  ///
  /// Everything else is a cache of CouchDB and can be dropped and re-synced.
  /// These cannot: [OutboxEntries] is the un-pushed write queue, [PersonalEntries]
  /// holds private notes that may never have been uploaded, [RemovedLogs] is how
  /// a "leave" survives the shelf merge, and [MyLifeEntries] carries the user's
  /// own ordering and visibility choices. Dropping any of them on a schema bump
  /// would silently discard work the user did offline.
  ///
  /// [Meetups] is a hybrid, like [Submissions]: mostly a mirror of the
  /// `meetups` database, but `EventsRepository.create` writes meetups that
  /// exist nowhere else until the outbox drains, and `toggleAttendance` records
  /// a join the shelf has not yet pushed. Preserving it leaves stale server
  /// rows behind, which the next sync prunes through `deleteNotIn` — a cost
  /// worth paying to keep un-uploaded meetups. [Surveys] and [SurveyQuestions]
  /// are the same hybrid for the same reason — see their entry below.
  ///
  /// [NewsEntries] is the same hybrid: a post or reply composed offline exists
  /// only in this table until the outbox delivers it and it adopts a CouchDB
  /// `_id`. `deleteNotIn` prunes the stale server rows on the next sync and
  /// deliberately spares rows that still have no `_id`.
  /// Exposed so `migration_test.dart` can assert that every preserved table
  /// actually has a preservation test. Adding a name here without one is how
  /// `my_life` and the submissions tables went uncovered.
  @visibleForTesting
  static const Set<String> localAuthorityTables = _localAuthorityTables;

  static const Set<String> _localAuthorityTables = {
    'outbox',
    'my_personal',
    'removed_log',
    'my_life',
    'submissions',
    'submission_answers',
    'submission_questions',
    'meetups',
    'news',
    'team_tasks',
    // Mixed: mostly the CouchDB team catalog, but the same table stores the
    // documents the user authors offline (join requests, memberships,
    // financial reports, resource links). Preserving the whole table and
    // letting the next sync's `deleteNotIn` evict the stale cache rows keeps
    // the drop-and-resync policy without discarding the local writes.
    'teams',
    // A chat sync exists now, so most of this table can be refilled. It stays
    // preserved for the rows that cannot be: a continuation whose answer never
    // arrived is stored here as a trailing query with an empty response, and
    // there is no chat uploader to carry it anywhere. Dropping the table would
    // discard the question outright.
    'chat_history',
    // Filed offline and uploaded by the outbox. Until the drain succeeds the
    // row exists only here, so a schema bump would discard a report the user
    // wrote. A feedback sync exists now, but it only refills what already
    // reached the server — which is precisely not these rows.
    'feedback',
    // Health examinations are recorded on the device — a clinician entering a
    // reading offline is the whole point of the screen. There is no health
    // sync running yet either, so the drop-and-resync premise fails twice
    // over: dropping this table destroys a medical record outright.
    'health_examinations',
    // Not a CouchDB cache in the part that matters. `key`/`iv` are generated
    // on this device and never sent anywhere, so a sync cannot give them back
    // — and losing them makes every health record already encrypted with them
    // permanently unreadable. Dropping this table also signs the user out,
    // since the session restores by looking their id up here.
    'users',
    // Mixed authority: rows pulled from the `courses_progress` CouchDB
    // database are a cache, but rows the user authored offline — a step viewed
    // or an exam passed with no connectivity — carry no `_id` and exist nowhere
    // else until the uploader delivers them. A sync can refill the cache half
    // but not the other, so dropping the table would discard progress made
    // offline. `insertCourseProgressFromSync` merges by (courseId, userId,
    // stepNum) so a refilled cache row adopts the local `passed` flag rather
    // than overwriting it.
    'course_progress',
    // The device's own log of offline logins, written by `SessionNotifier`.
    // `ActivitiesUploader` now carries them to `login_activities`, but nothing
    // syncs that database back in, so an uploaded row still only exists here —
    // and a row that has not drained yet exists nowhere at all. Dropping the
    // table would reset the user's offline-login count to zero and empty the
    // activity chart.
    'offline_activity',
    // Resource opens/downloads and completed syncs, and course visits. Same
    // shape as `offline_activity`: pending rows exist nowhere else, and
    // `resource_activities`, `admin_activities` and `course_activities` are
    // write-only from this app's side — no sync pulls them back.
    'resource_activity',
    'course_activity',
    // Resource ids awaiting a network-constrained one-shot worker. This is
    // local intent, not a server cache; dropping it silently loses downloads.
    'download_queue',
    // A captured exam-verification photo exists only on this device until the
    // `SubmitPhotosUploader` delivers it, and the bytes it points at live only
    // on this device's filesystem. No sync refills either, so a schema bump
    // would discard a photo (and orphan its file) the user was never warned
    // had not reached the server.
    'submit_photos',
    // One row per `teamVisit` the user makes. The Kotlin writes the row at
    // open time (`logTeamVisit`) and `UploadManager.uploadTeamActivities`
    // carries it to `team_activities` on the next sync. Until the upload
    // succeeds the row exists only here, so a schema bump would silently lose
    // an action the user took.
    'team_log',
    // One row per filtered search the user runs. The Kotlin writes the row
    // from `CoursesFragment.onPause` / `ResourcesFragment.onPause` and
    // `UploadManager.uploadSearchActivity` carries it to `search_activities`
    // on the next sync. Until the upload succeeds the row exists only here, so
    // a schema bump would silently lose the analytics event.
    'search_activity',
    // `achievements` rows are locally authored (the edit screen's lists,
    // serialized the way `Achievement.serialize` rebuilds them), and exist
    // only here until the `AchievementsUploader` delivers them.
    'achievements',
    // One row per challenge action the user completes (currently only
    // `"sync"`). Locally authored and never synced back — no CouchDB
    // counterpart exists — so a schema bump would silently discard the
    // user's completed-challenge state.
    'user_challenge_actions',
    // Mixed, and the mixture is the whole argument. Almost every row is the
    // `exams` cache, but `SurveysRepository.adoptSurvey` mints a team's
    // private clone of a shared survey locally — a row the server has never
    // seen, carrying [Surveys.needsSync] until `AdoptedSurveysUploader`
    // publishes it — and writes its question set alongside. By the operative
    // test (*can a sync restore this?*, not *is it local?*) the answer splits
    // exactly there: a published clone comes back on the next `exams` walk, an
    // unpublished one exists nowhere else.
    //
    // Phase 138 spared that clone from [SurveyDao.deleteNotIn] and left the
    // identical loss reachable by an upgrade: `submissions`,
    // `submission_answers` and `submission_questions` are all preserved, so
    // dropping these two destroyed the survey and **kept** the members'
    // answer sheets. Preserving them takes the `teams` route — keep the whole
    // table, let the next walk's `deleteNotIn` evict the stale cache half,
    // which it already does for the question rows in the same transaction.
    //
    // The price is that `createAll` cannot ALTER a preserved table, so every
    // future column on either one needs a hand-written [_addColumnIfMissing]
    // step below — and for `surveys` that step must run *before* `createAll`.
    // See the reconciliation block for why, and
    // `migration_test.dart`'s frozen-DDL guard for what fails if it is
    // forgotten.
    //
    // A second, smaller price: a **step-joined** row is now unbounded.
    // [SurveyDao.deleteNotIn] only ever considers rows with `stepId IS NULL`,
    // and a step join is released only by
    // [SurveyDao.releaseStepJoinsForCourse], which the courses walk calls for
    // the course documents on the current page. A course deleted server-side
    // is on no page, so nothing nulls its surveys' `stepId` and the prune can
    // never reach them — the bump used to sweep them and no longer does.
    //
    // **They are not unreachable from the UI**, which is what this comment
    // used to claim ("the step tile is gone with the course"). Phase 143's own
    // implementation audit retracted that, and the retraction reached the
    // notes and not this copy of the sentence: `surveysProvider` filters
    // its `watchAll()` stream against the ids [SurveysRepository
    // .individualSurveys] returns (which reads `allRows()`, not the stream —
    // the composition is the provider's), whose
    // predicate is `!row.teamShareAllowed && (row.teamId ?? '').isEmpty` —
    // **no `stepId` or `courseId` test at all**. A course-embedded survey
    // arrives with neither key set (`SurveyMapper.fromCourseDoc` reads
    // `teamShareAllowed` as false and `teamId` as null when the sub-object
    // omits them), so it satisfies that predicate exactly: once its course is
    // deleted server-side the orphan is listed at `/life/surveys` and
    // tappable, forever.
    //
    // The *visibility* is parity — `ExamDao.getByType("surveys")` has no
    // filter either — but Kotlin's Room bump drops the table and self-cleans
    // where the port's no longer does. So this is a user-visible accretion,
    // not a hidden one, and it is the argument for giving
    // `releaseStepJoinsForCourse` a whole-table sweep rather than a per-page
    // one.
    'surveys',
    'survey_questions',
    // Mixed, like `teams` and `surveys`, and it took until Phase 150 to say
    // so: almost every row is the `resources` cache, but two kinds of local
    // state live in the same table and **no sync can give either back**, which
    // is the operative test (*can a sync restore this?*, not *is it local?*).
    //
    //  * A resource the user created offline.
    //    [ResourcesRepository.saveLocalResource] writes a row with no `_rev`,
    //    no CouchDB document and no outbox entry, plus the file it copied into
    //    `<base>/ole/<id>/`. Kotlin uploads its equivalent —
    //    `MyLibraryDao.getPendingUploads` is `WHERE _rev IS NULL`
    //    (`MyLibraryDao.kt:94-95`), reached from `UploadManager.uploadResource`
    //    — so in Kotlin the row converges on a document and the loss window is
    //    the gap before the next sweep. **The port has no resources uploader at
    //    all**, so the window never closes and the row is the only copy that
    //    will ever exist. Dropping it deleted the user's own resource and the
    //    pointer to the file they picked.
    //  * `resource_offline` / `resource_local_address` / `downloaded_rev` on
    //    *any* row. Kotlin re-derives the flag from the disk on every pull
    //    that carries an attachment (`insertMyLibrary` calls
    //    `FileUtils.checkFileExist` inside `if (doc.has("_attachments"))` and
    //    `if (key.indexOf("/") < 0)`, `MyLibrary.kt:243,261-265` — so a course
    //    step's sub-object re-derives nothing, and the `resources` walk is the
    //    only place the flag can become true), so its rebuilt table repairs
    //    itself on the
    //    next sync. [MyLibraryMapper.fromDoc] names none of the three, so the
    //    port's rebuilt table cannot: the bytes stayed on disk and the row said
    //    they were not there, and the bell asked the user to download every
    //    resource they already had.
    //
    // The price is the `teams`/`surveys` one — a row the server has deleted
    // survives until the next walk prunes it — and Phase 146's `deleteNotIn`
    // fix is what makes that safe rather than lossy: the prune's eligibility is
    // `is_private = 0 AND _rev IS NOT NULL AND _rev != ''`, so it still evicts
    // the stale cache half and cannot touch either kind of local state.
    // **Verified rather than inherited, and "spares exactly the rows
    // preservation is for" is too strong**: `_rev IS NULL` in the port is not
    // authorship (a course-step sub-object carries no `_rev`, which is why
    // [MyLibraryMapper] now leaves the column absent instead of blanking it),
    // and a *downloaded* public row is still prunable, so a prune can still
    // orphan a file on disk. Preservation is necessary, and the prune is not
    // sufficient on its own.
    //
    // Unlike every other table here, a new column on this one needs no
    // hand-written `_addColumnIfMissing` step: the reconciliation loop below
    // adds whatever the running database is missing. **That is not permission
    // to add columns freely.** The loop runs inside `onUpgrade`, so a new
    // column still needs a `schemaVersion` bump to reach any existing install
    // — and it still needs a *backfill* decision if its default is wrong for
    // rows already there, which no loop can make. `migration_test.dart`'s
    // column inventory reds on a new column so that decision is taken rather
    // than skipped; read the loop's own comment for the two column shapes
    // (`clientDefault`, `currentDateAndTime`) that SQLite refuses in an
    // `ALTER TABLE ADD COLUMN` and would abort the upgrade on every install.
    'my_library',
  };

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onUpgrade: (m, from, to) async {
      // Preserved-table columns an *index* names have to be reconciled
      // before `createAll` — not with the rest of the hand-written steps
      // below.
      //
      // `createAll` emits `CREATE TABLE IF NOT EXISTS`, which no-ops on a
      // preserved table, but a **bare** `CREATE INDEX` (drift's generated
      // `Index` carries the literal statement; see `createIndex`). Every index
      // is dropped further down and recreated by `createAll` — and
      // `surveys_course_id` names `course_id`, which `surveys` only gained at
      // v35. On a device older than that the preserved table has no such
      // column and the `CREATE INDEX` aborts the whole upgrade. Adding the
      // column first is the fix; ordering, not the statement, was the bug.
      //
      // **These blocks run first of all, ahead of the drop loop, and the
      // order is deliberate.** Drift does not wrap a migration in a
      // transaction, so an `ALTER TABLE` that throws leaves `onUpgrade`
      // abandoned wherever it failed. Reconciling before anything is dropped
      // makes that failure non-destructive: the caches are still there, the
      // next open retries the same `from`, and every step here is idempotent.
      // Reconciling *after* the drop loop would leave a database with its
      // caches gone and `createAll` never reached. Costs nothing, since
      // nothing here depends on the drop.
      //
      // No other preserved table is exposed to this: of the columns added by
      // hand below, none of `chat_history.is_uploaded`, `users.is_updated`,
      // `users.age`, `users.birth_place`, `teams.image_name`,
      // `team_tasks.is_notified`, `team_tasks.sync`, `team_tasks.link` or
      // `news.reactions` appears in a `@TableIndex`. Check that before adding
      // a step after `createAll`.
      //
      // `my_library` **is** exposed to it, which is why it is reconciled here
      // and not below. `my_library_course_id` names `course_id`, a column the
      // table only gained at v48, so on an older install the recreated
      // `CREATE INDEX` would abort the entire upgrade — not merely omit a
      // column. (A comment further down used to say this table "has no
      // `@TableIndex`". It has had one since Phase 146 added it in the same
      // commit as that sentence.) `step_id` has to be here too, for a second
      // reason: [CourseDao.upsertAll] runs
      // `UPDATE my_library SET step_id = NULL, course_id = NULL` inside the
      // courses-walk transaction, so a missing column takes the whole walk
      // with it.
      //
      // **Every column, not a version-gated list.** The table has always been
      // dropped and rebuilt, so what is on disk is exactly the shape of the
      // version installed — and this repository's history is shallow, so there
      // is no way to establish from git when each column arrived. (v36's
      // `open_which_file` is known from `PHASE_53`; older columns are not
      // knowable here.) Enumerating versions would therefore be guessing,
      // where the loop is exhaustive by construction and idempotent:
      // [_addColumnIfMissing] no-ops on a present column and on an absent
      // table. It also retires, for this table only, the failure Phase 143
      // warned about — a forgotten step that is silent until a query names the
      // column.
      //
      // `id` is skipped explicitly. It is the primary key, present in every
      // shape the table has ever had, so the loop would never reach it — but
      // `ALTER TABLE ADD COLUMN` on a `NOT NULL` column with no default is
      // illegal in SQLite, and relying on the guard to hide an illegal
      // statement is safe by accident rather than by construction. Every other
      // column is nullable or carries a literal default, which
      // `Migrator.addColumn` emits unparenthesised and SQLite accepts (the
      // `CHECK (… IN (0, 1))` on the two booleans is fine; `surveys.needs_sync`
      // and `users.is_updated` are the shipping precedent).
      //
      // Two column shapes would make this loop **abort the upgrade on every
      // existing install**, and neither exists on this table today:
      // a `NOT NULL` column with a `clientDefault` (applied in Dart, so
      // `Migrator.addColumn` emits `NOT NULL` with no `DEFAULT`, which SQLite
      // rejects), and `withDefault(currentDateAndTime)` (`DEFAULT
      // CURRENT_TIMESTAMP`, which `ADD COLUMN` forbids outright). A new column
      // of either shape needs a rebuild-and-copy step, not this loop.
      //
      // What this loop cannot do is **backfill**. No `my_library` column needs
      // one today, and the reason is structural rather than lucky: the table
      // has no authorship flag, so the state that marks a row local is
      // `_rev IS NULL` — which a preserved row already carries, and which is
      // Kotlin's own `getPendingUploads` predicate. `step_id`/`course_id`
      // arrive NULL, which is the truthful value ("in no course document") and
      // is what the courses walk overwrites on its next page. A future column
      // whose default is *wrong* for existing rows is the `surveys.needs_sync`
      // shape and needs a hand-written `UPDATE` beside this loop; see that
      // backfill for what getting it wrong costs.
      if (await _tableExists('my_library')) {
        for (final column in myLibraryTable.$columns) {
          if (column.name == myLibraryTable.id.name) continue;
          await _addColumnIfMissing(m, myLibraryTable, column);
        }

        // v49 repairs the rows preserving this table would otherwise keep
        // *wrong*, and it is the reason this phase spends a version number at
        // all. Before Phase 150 `saveLocalResource` copied no file: it stored
        // the raw `FilePicker` path in `resource_local_address` and set
        // `resource_offline = 1` over the top. The bump used to delete that
        // row; preserving the table keeps it, and it is then a permanent dead
        // end — My Library shows the offline pin, the detail screen offers
        // **View** rather than Download (`_shouldShowDownloadButton` sees a
        // non-empty address, `_isResourceOffline` is true), the viewer finds
        // nothing under `ole/<id>/`, and the Download it falls back to cannot
        // work either because `urlFor` needs a `couchId` a locally created row
        // has never had.
        //
        // **A dead end that lies is worse than the absence it replaced**, so
        // the repair is the difference between removing this phase's data loss
        // and merely relocating it — Phase 143's lesson, aimed at the fix that
        // quotes it. The metadata the user typed is kept; only the claim goes,
        // which lands the row on exactly the shape the fixed writer produces
        // for a fileless save.
        //
        // **The discriminator is `_id IS NULL`, not `_rev IS NULL`.** Every
        // mapper-written row carries a `couchId`; only [saveLocalResource]
        // leaves it unset, and a random local id can never match a server
        // document, so the clause selects exactly the locally created rows.
        // `_rev IS NULL` looks equivalent and is not: a resource embedded only
        // in a course document arrives rev-less (a step sub-object carries
        // none) while still getting its `couchId`, so it is downloadable, and
        // `markDownloaded` then writes an absolute path onto a rev-less row —
        // which that clause would have cleared, losing a real download. Third
        // instance this round of *`_rev IS NULL` is authorship in Kotlin and
        // is not in the port*.
        //
        // `LIKE '%/%'` is the other half: `MyLibraryMapper._primaryAttachment`
        // skips any attachment key containing a separator, and the fixed
        // writer stores a basename, so only a picker path has one. Without it
        // the repair would un-flag every locally created resource on every
        // future bump.
        if (from < 49) {
          await customStatement(
            'UPDATE my_library '
            'SET resource_offline = 0, resource_local_address = NULL '
            "WHERE _id IS NULL AND resource_local_address LIKE '%/%'",
          );
        }
      }

      // Drop-and-resync for the CouchDB caches only; the next sync refills
      // them. Locally-authored tables are stepped over and left in place.
      for (final table in allTables) {
        if (_localAuthorityTables.contains(table.actualTableName)) continue;
        await m.deleteTable(table.actualTableName);
      }

      // Indexes are dropped first so `createAll` can recreate them. It emits
      // `CREATE TABLE IF NOT EXISTS` but a bare `CREATE INDEX`, so an index
      // belonging to a preserved table would collide and abort the upgrade.
      // They are pure derived data, so dropping and rebuilding costs nothing.
      for (final entity in allSchemaEntities) {
        if (entity is Index) {
          await customStatement('DROP INDEX IF EXISTS ${entity.entityName}');
        }
      }

      // Both blocks below run before `createAll`, so neither may assume
      // `surveys` exists: an upgrade from a version predating the table would
      // find nothing to alter or update, and `createAll` is about to build it
      // complete. [_addColumnIfMissing] tolerates that on its own; the raw
      // `UPDATE` in the second block does not, which is what this guards.
      if (await _tableExists('surveys')) {
        // v35 added `courseId` and `stepId` to `Surveys`, with the
        // `surveys_course_id` index on the first, so course-attached surveys
        // could be modelled. Both are nullable with no backfill to do: a row
        // written before v35 belonged to no course document, which is what NULL
        // says.
        if (from < 35) {
          await _addColumnIfMissing(m, surveys, surveys.courseId);
          await _addColumnIfMissing(m, surveys, surveys.stepId);
        }

        // v47 added `Surveys.needsSync`, the local-authorship flag an adopted
        // team clone is published on. The column default is `false`, which is
        // right for every row the server sent — marking those pending would POST
        // the whole survey cache to `exams` under the user's credentials, the
        // leak [Surveys.needsSync] exists to prevent.
        //
        // **But the default alone would defer this phase's data loss rather than
        // prevent it.** Preserving the table saves a clone adopted on a pre-v47
        // build from the bump, and then `needsSync = false` puts it in the worst
        // of both states: invisible to [SurveyDao.pendingAdoptedSurveys], so it
        // never publishes, and outside [SurveyDao.deleteNotIn]'s spare clause
        // (`stepId IS NULL AND needsSync = 0`), so the very next surveys walk
        // deletes it and its questions and orphans the members' answer sheets.
        //
        // So the flag is backfilled for the rows this device can *positively*
        // identify as its own. Two facts do that together, and the second is
        // the load-bearing one:
        //
        //  * the port's clone id is deterministic — `'${surveyId}_$teamId'`
        //    (`SurveysRepository.adoptSurvey`, whose one production caller
        //    passes no `createId`) — where Kotlin mints `UUID.randomUUID()`
        //    (`SurveysRepositoryImpl.kt:86`), so the shape is at least
        //    *recognisable*; and
        //  * `adoptSurvey` leaves an **adoption marker** submission beside the
        //    clone, which is the actual evidence of local authorship.
        //
        // The id shape alone is not authorship, and the first cut of this
        // backfill assumed it was — see the `EXISTS` conjunct below for the
        // document that shape belongs to and what POSTing it would have done.
        // Phase 138 rejected `_rev IS NULL` as an authorship test and was right
        // to; the rev clause here only keeps an already-published clone from
        // being re-queued.
        //
        // Every conjunct earns its place, and two are deliberately redundant:
        //
        //  * `id = source_survey_id || '_' || team_id` — the identification.
        //    Only `adoptSurvey` mints that shape. A Kotlin-authored clone is a
        //    UUID and fails it.
        //  * `_rev IS NULL` — a clone [SurveyDao.markUploaded] has recorded is a
        //    cache row again, and re-flagging it would POST a second copy.
        //  * `step_id IS NULL` — `adoptSurvey` withholds `stepId` deliberately
        //    (see the call), and this is what excludes a course-embedded survey,
        //    whose `_rev` is NULL for a reason that has nothing to do with
        //    authorship (a sub-object carries none).
        //  * `source_survey_id IS NOT NULL` and `team_id IS NOT NULL` add
        //    nothing — `x || NULL` is NULL, so the id equality already fails for
        //    either — and are kept to say plainly that this repair is about a
        //    team adoption and nothing else.
        //
        // **One row satisfies all of it without being local work, and it is
        // accepted rather than predicated away.** A clone another handset
        // published, attached to a course step on Planet, has its
        // authoritative `_rev` clobbered to NULL by the courses walk
        // (`SurveyMapper._build` assigns `rev` unconditionally and a course
        // sub-object carries none — the two-writer conflict Phase 138
        // recorded); once the course document stops naming it,
        // `releaseStepJoinsForCourse` nulls `stepId` too, and every conjunct
        // holds. `rev` is the only column that records "the server has this",
        // and it is exactly the column those two writers disagree about, so
        // there is no local discriminator left: `courseId IS NULL` would miss
        // a genuine clone of a course-attached survey, since `adoptSurvey`
        // copies `courseId`. The cost is bounded and self-clearing — the POST
        // takes a 409 and `AdoptedSurveysUploader._adoptExistingDocument`
        // records the winning rev and clears the flag, once. Pinned as a pair
        // in `survey_clone_survives_schema_bump_test.dart`.
        if (from < 47) {
          await _addColumnIfMissing(m, surveys, surveys.needsSync);
          await customStatement(
            'UPDATE surveys SET needs_sync = 1 '
            'WHERE source_survey_id IS NOT NULL '
            'AND team_id IS NOT NULL '
            'AND _rev IS NULL '
            'AND step_id IS NULL '
            "AND id = source_survey_id || '_' || team_id "
            'AND EXISTS (SELECT 1 FROM submissions AS marker '
            'WHERE marker.parent_id = surveys.source_survey_id '
            'AND marker.team_id = surveys.team_id)',
          );
        }
      }

      // `SurveyQuestions` has never gained or lost a column. The `choices`
      // converter swap (Phase 104) changed the Dart type and not the DDL —
      // `TEXT NOT NULL DEFAULT '[]'` before and after — so it needs no step,
      // and `survey_id`/`position` are structural, present in every shape the
      // table has ever had. A future column here needs a step of its own.

      // Recreates the dropped caches and anything newly added. Note this does
      // not *alter* a preserved table, so changing the shape of one of
      // [_localAuthorityTables] needs a hand-written step here.
      await m.createAll();

      // Hand-written steps for preserved tables, which `createAll` skips.
      //
      // `chat_history` gained `is_uploaded` in v25. Without this the column
      // simply never appears on an existing install and every chat query
      // fails on it — `createAll` emits `CREATE TABLE IF NOT EXISTS`, and the
      // table already exists.
      if (from < 25) {
        await _addColumnIfMissing(m, chatEntries, chatEntries.isUploaded);
        // Backfill: a chat carries a `_rev` only once the server has
        // acknowledged it. Leaving those at the column default would mark
        // every conversation already on the server as pending and post a
        // duplicate of each one on the next drain.
        await customStatement(
          "UPDATE chat_history SET is_uploaded = 1 "
          "WHERE _rev IS NOT NULL AND _rev != ''",
        );
      }

      // `users` is preserved, so `createAll` does not alter it. v30 adds the
      // `isUpdated` dirty flag and the `age`/`birthPlace` profile fields that
      // `UserEntity.serialize` writes. `isUpdated` defaults to false: a row
      // already on the server is not pending just because the column appeared.
      if (from < 30) {
        await _addColumnIfMissing(m, users, users.isUpdated);
        await _addColumnIfMissing(m, users, users.age);
        await _addColumnIfMissing(m, users, users.birthPlace);
      }

      // `teams` is preserved, so `createAll` does not alter it. v31 adds the
      // `imageName` attachment column that `TeamsRepository.createTransaction`
      // and `saveReport` set when a receipt image is attached. Existing rows
      // have no attachment, so the nullable column's default is correct and no
      // backfill is needed.
      if (from < 31) {
        await _addColumnIfMissing(m, teams, teams.imageName);
      }

      // `team_tasks` is preserved too. v32 adds `isNotified`, the once-only flag
      // behind the deadline notifications. Defaulting existing rows to false is
      // the right migration even though it can re-notify a task the Kotlin app
      // already notified about on the same device: the alternative — defaulting
      // to true — would silently swallow the first notification for every task
      // already on the device, and a duplicate reminder is the cheaper mistake.
      if (from < 32) {
        await _addColumnIfMissing(m, teamTasks, teamTasks.isNotified);
      }

      // `news` is preserved, so `createAll` does not alter it. v42 adds the
      // `reactions` column — a JSON-encoded map of emoji to user ids, porting
      // the upstream `13357` emoji-reactions feature. Existing rows have no
      // reactions, so the nullable column's default is correct.
      if (from < 42) {
        await _addColumnIfMissing(m, newsEntries, newsEntries.reactions);
      }

      // v45 is a one-time data repair, not a shape change: see
      // `PHASE_111_NOTES.md`. Before Phase 105 the examination form wrote the
      // *patient's* id into a new examination row's `userId`, and
      // `HealthRepository.serialize` keys the uploaded document on `userId` —
      // so such a row claims the same CouchDB `_id` as the patient's own
      // profile row. The profile is written first, wins, and the examination
      // takes a 409, which [OutboxDrainer] classifies as permanent. The
      // clinician's reading then never reaches the server.
      //
      // Setting `userId` to the row's own id is what Phase 105 made every new
      // examination do (`createExamination`: `userId ?? id`), and what
      // `_docToCompanion` maintains for every synced row — so the repaired row
      // is indistinguishable from one recorded today, and posts as its own
      // document.
      //
      // The predicate is deliberately narrow, because re-keying the wrong row
      // would create the same conflict in the other direction. Every conjunct
      // excludes something real:
      //
      //  * `is_updated = 1` — a clean row is not queued for upload at all, so
      //    it cannot be in the collision. Phase 107 drew the same boundary.
      //  * `user_id <> id` — the post-Phase-105 shape and every synced row
      //    have them equal, and are untouched.
      //  * `profile_id IS NULL` — the pre-Phase-105 form wrote no `profileId`
      //    (that was the same phase's headline finding), while every row from
      //    the server carries the one Planet stored. This is the positive
      //    identification, not an id-prefix guess: Phase 107 declined to write
      //    the guess, and it was right to.
      //  * `_rev IS NULL` — a row that already uploaded owns a revision, and
      //    nothing on the row records *which document* it is a revision of.
      //    Before Phase 107 `serialize` keyed the upload on the row's own `id`
      //    (`713c5ad` is the line that changed it to `userId`), and
      //    `markUploaded` writes `rev` and nothing else — so an old `_rev` may
      //    belong to the `health-…` document or to the patient's, and the
      //    repair is right for one and wrong for the other. Left alone rather
      //    than guessed at; these rows remain a loss path, recorded in
      //    `PHASE_111_NOTES.md`. `_id IS NULL` rides along: `couchId` is only
      //    ever written by `_docToCompanion`, whose rows have `userId == id`
      //    and are already excluded, so it adds nothing and is kept only to
      //    say plainly that a row with a server identity is not repairable.
      //  * `EXISTS (… p.id = user_id)` — the collision must actually exist.
      //    This is the conjunct the first cut of the repair lacked, and its
      //    absence was not theoretical: `saveHealthProfileBlob` resolves the
      //    profile row with `getByIdOrUserId`, which matches a legacy
      //    examination row *through its `userId` column*, and the else-branch
      //    then keeps `id: Value(existing.id)` — so on a device with no
      //    separate profile row the first post-Phase-105 save turns the legacy
      //    examination row into the patient's profile row, `id = 'health-…'`
      //    and all. Every other conjunct here still matches it, and re-keying
      //    it would publish the health profile under a millisecond timestamp
      //    no server can resolve to a person: the exact harm this repair
      //    exists to prevent, caused by the repair. A converted row is the
      //    only row for its patient, so requiring a row keyed on the `userId`
      //    being replaced excludes it and keeps the true positive, where the
      //    profile row is what the examination is colliding with.
      //  * `id NOT IN (users)` — a *profile* row's id is the patient's user
      //    row key (`saveHealthProfileBlob`), and `UserDao.getById` matches
      //    `_id` as well as `id`, so both columns are checked. This is what
      //    keeps the repair off the profile row of a member registered on this
      //    device, whose `userId` is legitimately the CouchDB id its own key
      //    is not.
      if (from < 45) {
        await customStatement(
          'UPDATE health_examinations SET user_id = id '
          'WHERE is_updated = 1 '
          'AND user_id IS NOT NULL '
          'AND user_id <> id '
          'AND profile_id IS NULL '
          'AND _id IS NULL '
          'AND _rev IS NULL '
          'AND id NOT IN (SELECT id FROM users) '
          'AND id NOT IN (SELECT _id FROM users WHERE _id IS NOT NULL) '
          'AND EXISTS (SELECT 1 FROM health_examinations AS p '
          'WHERE p.id = health_examinations.user_id)',
        );
      }

      // `team_tasks` is preserved, so `createAll` does not alter it. v46 adds
      // `sync` and `link`, the two JSON sub-objects `TeamTask.fromJson` stores
      // verbatim and `TeamTask.serialize` re-emits verbatim.
      //
      // **No backfill, deliberately.** The server's `sync` names the planet
      // that authored the task and nothing on an existing row records it, so
      // there is nothing truthful to write; `link` could be reconstructed as
      // `{"teams": <team_id>}` — that is where `fromJson` read `teamId` from —
      // but only for rows whose `link` carried nothing else. Leaving both null
      // costs nothing, because [TeamTasksRepository.serialize] falls back to
      // rebuilding them exactly as `upsertTask` does, which is what the port
      // did for every row before this version. So an existing task uploads
      // byte-for-byte as it would have; only a task pulled *after* the upgrade
      // gains the server's own values.
      if (from < 46) {
        await _addColumnIfMissing(m, teamTasks, teamTasks.sync);
        await _addColumnIfMissing(m, teamTasks, teamTasks.link);
      }

      // `my_library`'s columns — v48's `step_id`/`course_id` among them — are
      // reconciled in the pre-`createAll` block above, not here, because the
      // table is preserved as of Phase 150 *and* carries a `@TableIndex` on
      // `course_id`. See there.
      //
      // The sentence this replaces claimed the opposite on both counts ("not
      // in [_localAuthorityTables], so the drop loop above deleted it … also
      // has no `@TableIndex`"), and the second half was already false when it
      // was written: Phase 146 added the index in the same commit. It is the
      // exact shape of sentence a later lane relies on, which is why it is
      // corrected rather than deleted.
    },
  );

  /// Whether the running database has a table of this name.
  ///
  /// `PRAGMA table_info` returns no rows for a table SQLite does not have, and
  /// SQLite has no zero-column table, so an empty result is the test. Needed by
  /// the reconciliation steps that run *before* `createAll` — see there.
  Future<bool> _tableExists(String name) async {
    final rows = await customSelect('PRAGMA table_info($name)').get();
    return rows.isNotEmpty;
  }

  /// Adds [column] to [table] unless the running database already has it.
  ///
  /// A preserved table is skipped by the drop-and-recreate loop, so a column
  /// added to one only exists on installs created after the change. Re-running
  /// `ALTER TABLE ADD COLUMN` on a database that already has it is an error,
  /// hence the check — an upgrade that spans several versions would otherwise
  /// abort partway.
  ///
  /// An absent table is also a no-op, which is what makes a call safe *before*
  /// `createAll` — `PRAGMA table_info` on a table SQLite does not have returns
  /// no rows, and `createAll` is about to create it complete. Without this an
  /// upgrade from a version predating the table would `ALTER` something that
  /// does not exist and abort.
  Future<void> _addColumnIfMissing(
    Migrator m,
    TableInfo<Table, dynamic> table,
    GeneratedColumn<Object> column,
  ) async {
    final existing = await customSelect(
      'PRAGMA table_info(${table.actualTableName})',
    ).get();
    if (existing.isEmpty) return;
    final names = existing.map((row) => row.read<String>('name')).toSet();
    if (names.contains(column.name)) return;
    await m.addColumn(table, column);
  }

  /// Port of `ConfigurationsRepositoryImpl.clearAllData`, which the settings
  /// screen's "Reset app" preference calls via `SettingsViewModel.clearAllData`.
  /// The Kotlin uses `RoomDatabase.clearAllTables()`; drift's equivalent is a
  /// batched `DELETE FROM` across every table. Both wipe all local data — the
  /// next sync re-pulls the CouchDB caches; locally-authored rows are lost,
  /// matching the Kotlin's drop-and-resync policy.
  Future<void> clearAllData() async {
    await transaction(() async {
      for (final table in allTables) {
        await delete(table).go();
      }
    });
  }

  static LazyDatabase _openConnection() {
    return LazyDatabase(() async {
      final dir = await getApplicationDocumentsDirectory();
      return NativeDatabase.createInBackground(
        File(p.join(dir.path, 'myplanet.sqlite')),
      );
    });
  }
}

@DriftAccessor(tables: [TeamTasks])
class TeamTaskDao extends DatabaseAccessor<AppDatabase>
    with _$TeamTaskDaoMixin {
  TeamTaskDao(super.db);

  /// Port of `TeamTaskDao.getByTeamId`:
  /// `WHERE teamId = :teamId AND (status IS NULL OR status != 'archived')`.
  ///
  /// **Not `status = 'active'`**, which is what this was. `TeamTask.serialize`
  /// emits no `status` field at all, so `TeamTask.fromJson` reads the missing
  /// key as `""` and every task the server sends arrives with an empty status.
  /// Under the old predicate the `tasks` walk could land a full page of rows
  /// and the screen would still be empty — the walk working and the list
  /// blank. The column is non-nullable here (default `'active'`), so the
  /// Kotlin's `IS NULL` arm has nothing to match and is dropped.
  Stream<List<TeamTaskRow>> watchForTeam(String teamId) =>
      (select(teamTasks)
            ..where(
              (t) =>
                  t.teamId.equals(teamId) & t.status.equals('archived').not(),
            )
            ..orderBy([
              (t) => OrderingTerm.asc(t.completed),
              (t) => OrderingTerm.asc(t.deadline),
            ]))
          .watch();
  Future<TeamTaskRow?> getById(String id) =>
      (select(teamTasks)..where((t) => t.id.equals(id))).getSingleOrNull();
  Future<void> upsert(TeamTasksCompanion row) =>
      into(teamTasks).insertOnConflictUpdate(row);
  Future<void> upsertAll(List<TeamTasksCompanion> rows) async =>
      batch((b) => b.insertAllOnConflictUpdate(teamTasks, rows));
  Future<List<TeamTaskRow>> pending() =>
      (select(teamTasks)..where((t) => t.isUpdated.equals(true))).get();

  /// The local rows for a page of server documents, keyed by whichever column
  /// carries the CouchDB id.
  ///
  /// A task created in this app keeps its locally-minted `task-<micros>` id
  /// for life — `markUploaded` fills in `_id`/`_rev` and leaves `id` alone —
  /// so a sync-in keyed on the document `_id` (which is what `TeamTask.fromJson`
  /// does: `task.id = _id`) would insert a *second* row for a task the device
  /// already has. Resolving through `_id` first lets the walk update the row
  /// that exists.
  Future<List<TeamTaskRow>> getByAnyIds(List<String> ids) async {
    if (ids.isEmpty) return const [];
    return (select(
      teamTasks,
    )..where((t) => t.id.isIn(ids) | t.docId.isIn(ids))).get();
  }

  /// Port of `TeamTaskDao.getByTitles` (`TeamTaskDao.kt:44-45`):
  /// `SELECT * FROM team_tasks WHERE title IN (:titles)`.
  ///
  /// Added for the notification list's team-name prefix, which resolves a task
  /// by its title when the notification carries no task id
  /// (`NotificationsViewModel.formatTaskNotification`).
  Future<List<TeamTaskRow>> getByTitles(List<String> titles) async {
    if (titles.isEmpty) return const [];
    final rows = <TeamTaskRow>[];
    for (final chunk in _chunked(titles, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(teamTasks)..where((t) => t.title.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  Future<void> markUploaded(String id, String docId, String rev) =>
      (update(teamTasks)..where((t) => t.id.equals(id))).write(
        TeamTasksCompanion(
          docId: Value(docId),
          rev: Value(rev),
          isUpdated: const Value(false),
        ),
      );

  /// Stamps the CouchDB id and rev onto a task the user has edited but not yet
  /// uploaded, without disturbing the edit or the flag that carries it. An
  /// UPDATE rather than an upsert, for the same reason [OutboxDao.patch] is.
  Future<int> recordServerIdentity(String id, String docId, String? rev) =>
      (update(teamTasks)..where((t) => t.id.equals(id))).write(
        TeamTasksCompanion(docId: Value(docId), rev: Value(rev)),
      );
  Future<void> deleteById(String id) =>
      (delete(teamTasks)..where((t) => t.id.equals(id))).go();

  /// Port of `TeamTaskDao.getTasksForUserBetween` — the deadline window behind
  /// the dashboard's team task badge.
  ///
  /// `BETWEEN` in SQL is inclusive on both ends, and Drift's `isBetweenValues`
  /// generates the same, so the boundary behaviour matches.
  Future<List<TeamTaskRow>> tasksForUserBetween(
    String userId,
    int start,
    int end,
  ) =>
      (select(teamTasks)..where(
            (t) =>
                t.assignee.equals(userId) &
                t.deadline.isBetweenValues(start, end),
          ))
          .get();

  /// Port of `TeamTaskDao.getPendingTasksForUser` — the deadline-notification
  /// query. Narrower than [tasksForUserBetween] in two ways that matter:
  /// `completed = 0` skips a task the user already finished, and
  /// `isNotified = 0` is what makes the notification once-only.
  Future<List<TeamTaskRow>> pendingDeadlineTasks(
    String userId,
    int start,
    int end,
  ) =>
      (select(teamTasks)..where(
            (t) =>
                t.completed.equals(false) &
                t.assignee.equals(userId) &
                t.isNotified.equals(false) &
                t.deadline.isBetweenValues(start, end),
          ))
          .get();

  /// Port of `TeamTaskDao.markTasksNotified`.
  ///
  /// Writes only `isNotified`, deliberately leaving `isUpdated` alone: the flag
  /// is device-local and never uploaded, so marking it must not make the row
  /// look like it has an edit to push.
  Future<void> markNotified(List<String> taskIds) async {
    if (taskIds.isEmpty) return;
    await (update(teamTasks)..where((t) => t.id.isIn(taskIds))).write(
      const TeamTasksCompanion(isNotified: Value(true)),
    );
  }
}

/// Port of the team catalog queries in `data/room/dao/MyTeamDao.kt`.
@DriftAccessor(tables: [Teams])
class TeamDao extends DatabaseAccessor<AppDatabase> with _$TeamDaoMixin {
  TeamDao(super.db);

  Future<void> upsertAll(List<TeamsCompanion> rows) async =>
      batch((b) => b.insertAllOnConflictUpdate(teams, rows));

  Stream<List<TeamRow>> watchCatalog({String type = 'team'}) {
    final query = select(teams)
      ..where(
        (t) =>
            t.type.equals(type) &
            t.docType.isNull() &
            (t.status.isNull() | t.status.equals('archived').not()),
      )
      ..orderBy([(t) => OrderingTerm.asc(t.name)]);
    return query.watch();
  }

  /// Strictly the document's own `_id`. Matching `teamId` as well made this
  /// ambiguous: every membership, request, report and resource link carries
  /// the team's id in that column, so `getById(teamId)` could return one of
  /// them instead of the team, picked by scan order. `addCourses` guards on
  /// `docType`, so the result was an intermittent silent no-op.
  Future<TeamRow?> getById(String id) =>
      (select(teams)..where((t) => t.id.equals(id))).getSingleOrNull();

  Stream<List<TeamRow>> watchMemberships(String userId) =>
      (select(teams)..where(
            (t) => t.docType.equals('membership') & t.userId.equals(userId),
          ))
          .watch();

  /// One-shot sibling of [watchMemberships]: every `membership` row for
  /// [userId], used to compute the catalog's membership rank (leader/member/
  /// non-member) the way `TeamsRepositoryImpl.getTeamMemberStatuses` does.
  Future<List<TeamRow>> membershipsForUser(String userId) =>
      (select(teams)..where(
            (t) => t.docType.equals('membership') & t.userId.equals(userId),
          ))
          .get();

  /// The team documents behind a set of membership rows, for the home
  /// dashboard's myTeams card: real team documents only (no `docType`
  /// sub-documents), not archived, sorted by name. Chunked like every other
  /// id-list query so a planet with many teams stays under the variable cap.
  Future<List<TeamRow>> teamsByIds(List<String> ids) async {
    final rows = <TeamRow>[];
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(teams)..where(
              (t) =>
                  t.id.isIn(chunk) &
                  t.docType.isNull() &
                  (t.status.isNull() | t.status.equals('archived').not()),
            ))
            .get(),
      );
    }
    rows.sort((a, b) => (a.name ?? '').compareTo(b.name ?? ''));
    return rows;
  }

  Stream<int> watchMemberCount(String teamId) {
    final count = teams.id.count();
    final query = selectOnly(teams)
      ..addColumns([count])
      ..where(teams.docType.equals('membership') & teams.teamId.equals(teamId));
    return query.watchSingle().map((row) => row.read(count) ?? 0);
  }

  Stream<List<TeamRow>> watchTeamDocuments(String teamId, String docType) =>
      (select(teams)
            ..where((t) => t.teamId.equals(teamId) & t.docType.equals(docType))
            ..orderBy([(t) => OrderingTerm.asc(t.userId)]))
          .watch();

  Stream<List<TeamRow>> watchResourceLinks(String teamId) =>
      watchTeamDocuments(teamId, 'resourceLink');

  /// Watch all documents of a specific docType (e.g., 'service').
  Stream<List<TeamRow>> watchTeamDocumentsByType(String docType) =>
      (select(teams)
            ..where((t) => t.docType.equals(docType))
            ..orderBy([(t) => OrderingTerm.asc(t.title)]))
          .watch();

  Stream<List<TeamRow>> watchReports(String teamId) =>
      (select(teams)
            ..where(
              (t) =>
                  t.teamId.equals(teamId) &
                  t.docType.equals('report') &
                  (t.status.isNull() | t.status.equals('archived').not()),
            )
            ..orderBy([(t) => OrderingTerm.desc(t.createdDate)]))
          .watch();

  /// Watch all transactions for a team, optionally filtered by date range.
  Stream<List<TeamRow>> watchTransactions(
    String teamId, {
    int? startDate,
    int? endDate,
    bool ascending = false,
  }) {
    return (select(teams)
          ..where((t) {
            var condition =
                t.teamId.equals(teamId) &
                t.docType.equals('transaction') &
                (t.status.isNull() | t.status.equals('archived').not());
            if (startDate != null) {
              condition = condition & t.date.isBiggerOrEqualValue(startDate);
            }
            if (endDate != null) {
              condition = condition & t.date.isSmallerOrEqualValue(endDate);
            }
            return condition;
          })
          ..orderBy([
            (t) => ascending
                ? OrderingTerm.asc(t.date)
                : OrderingTerm.desc(t.date),
          ]))
        .watch();
  }

  Future<TeamRow?> getTeamDocument(
    String teamId,
    String userId,
    String docType,
  ) =>
      (select(teams)
            ..where(
              (t) =>
                  t.teamId.equals(teamId) &
                  t.userId.equals(userId) &
                  t.docType.equals(docType),
            )
            ..limit(1))
          .getSingleOrNull();

  Future<void> upsert(TeamsCompanion row) =>
      into(teams).insertOnConflictUpdate(row);

  /// Chunked so a full sync page cannot exceed SQLite's variable limit.
  Future<Map<String, TeamRow>> byIds(List<String> ids) async {
    final found = <String, TeamRow>{};
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      final rows = await (select(teams)..where((t) => t.id.isIn(chunk))).get();
      for (final row in rows) {
        found[row.id] = row;
      }
    }
    return found;
  }

  Future<void> deleteById(String id) =>
      (delete(teams)..where((t) => t.id.equals(id))).go();

  /// Hands the row back to the server: it adopts the new revision and stops
  /// being treated as a local edit, so later refreshes and the stale-row
  /// cleanup apply to it again.
  Future<int> markUploaded(String id, String rev) =>
      (update(teams)..where((t) => t.id.equals(id))).write(
        TeamsCompanion(rev: Value(rev), isUpdated: const Value(false)),
      );

  /// Stale-row cleanup, restricted to rows the server is authoritative for.
  ///
  /// Locally-authored documents are skipped: their ids were generated on the
  /// device, so they are *always* absent from the synced set, and the previous
  /// unfiltered `isNotIn` deleted every one of them on the next sync — a
  /// financial report or an offline join request has no second copy to
  /// recover from. The `NOT IN` is also replaced by a difference computed in
  /// Dart, because `teams` holds a row per membership and per report and the
  /// id list runs well past SQLite's variable limit.
  Future<int> deleteNotIn(List<String> keepIds) async {
    final keep = keepIds.toSet();
    final rows = await (select(
      teams,
    )..where((t) => t.isUpdated.equals(false))).get();
    final stale = rows
        .map((row) => row.id)
        .where((id) => !keep.contains(id))
        .toList(growable: false);
    var deleted = 0;
    for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
      deleted += await (delete(teams)..where((t) => t.id.isIn(chunk))).go();
    }
    return deleted;
  }
}

/// Port of `data/room/dao/UserDao.kt`.
@DriftAccessor(tables: [Users])
class UserDao extends DatabaseAccessor<AppDatabase> with _$UserDaoMixin {
  UserDao(super.db);

  Future<void> upsert(UsersCompanion user) =>
      into(users).insertOnConflictUpdate(user);

  /// Port of `UserRepositoryImpl.getUserByName`.
  ///
  /// `name` carries no unique constraint — two planets can legitimately hold
  /// accounts with the same name and different `_id`s — so this limits to one
  /// row rather than letting `getSingleOrNull` throw and take down login.
  Future<UserRow?> getByName(String name) =>
      (select(users)
            ..where((u) => u.name.equals(name))
            ..limit(1))
          .getSingleOrNull();

  /// `UserDao.getById` is
  /// `SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1`, and the
  /// `_id` half is load-bearing: a member registered on this device keeps its
  /// local `id` (`'<millis>'`) and gains a `couchId` only once the upload
  /// succeeds, while every caller that resolves a user from a document — the
  /// health screens' `patientIdOf` among them — hands over the `_id` in
  /// preference to it. Matching `id` alone left those accounts unfindable.
  /// `limit(1)` keeps `getSingleOrNull` from throwing where both halves match
  /// different rows, exactly as the Kotlin's `LIMIT 1` does.
  Future<UserRow?> getById(String id) =>
      (select(users)
            ..where((u) => u.id.equals(id) | u.couchId.equals(id))
            ..limit(1))
          .getSingleOrNull();

  /// Port of `UserRepositoryImpl.getSavedUsers` — the account picker on the
  /// login screen.
  Future<List<UserRow>> getSavedUsers() =>
      (select(users)..where((u) => u.isArchived.equals(false))).get();

  /// Returns all users (for sending surveys to selected users).
  Future<List<UserRow>> getAllUsers() => select(users).get();

  /// Port of `UserRepositoryImpl.getUsersForHealthSync` — every user with a
  /// server id, the set whose per-user `userdb-*` key document is worth
  /// probing. The Kotlin filters in memory after `userDao.getAll()`
  /// (`!it._id.isNullOrBlank()`), and blank couch ids are excluded the same
  /// way here.
  Future<List<UserRow>> getUsersForHealthSync() async {
    final all = await getAllUsers();
    return [
      for (final user in all)
        if (user.couchId?.trim().isNotEmpty ?? false) user,
    ];
  }

  /// Port of `UserRepositoryImpl.markUserKeyIvSaved` — record the health
  /// AES key/IV pulled from the user's `userdb-*` database. A missing row
  /// makes the Kotlin return early; the where-clause update is the same
  /// no-op.
  Future<void> markUserKeyIvSaved(String id, String key, String? iv) async {
    await (update(users)..where((u) => u.id.equals(id))).write(
      UsersCompanion(key: Value(key), iv: Value(iv)),
    );
  }

  /// Port of `UserDao.search` — name/firstName/lastName containment, matching
  /// the Kotlin `LIKE '%' || :query || '%'` predicate.
  Future<List<UserRow>> search(String query) {
    final pattern = '%$query%';
    return (select(users)..where(
          (u) =>
              u.name.like(pattern) |
              u.firstName.like(pattern) |
              u.lastName.like(pattern),
        ))
        .get();
  }

  /// Port of `UserRepositoryImpl.ensureUserSecurityKeys`.
  ///
  /// The health screens call this before encrypting. Generating lazily rather
  /// than at sign-in means users synced before health was ported still get a
  /// key, and re-using the stored one is what keeps yesterday's records
  /// readable.
  Future<UserRow?> ensureSecurityKeys(
    String id, {
    String Function()? createKey,
    String Function()? createIv,
  }) async {
    final user = await getById(id);
    if (user == null) return null;
    if (user.key != null && user.iv != null) return user;
    // Keyed on the row that was found, not on the id that found it:
    // `ensureUserSecurityKeys` upserts the entity it resolved, and [getById]
    // resolves a `_id` as readily as an `id`, so writing back by the argument
    // silently updated nothing for a locally-registered member addressed by
    // their CouchDB id.
    await (update(users)..where((u) => u.id.equals(user.id))).write(
      UsersCompanion(
        key: Value(user.key ?? (createKey ?? HealthCipher.generateKey)()),
        iv: Value(user.iv ?? (createIv ?? HealthCipher.generateIv)()),
      ),
    );
    return getById(user.id);
  }

  Future<int> count() async {
    final query = selectOnly(users)..addColumns([users.id.count()]);
    final row = await query.getSingle();
    return row.read(users.id.count()) ?? 0;
  }

  /// Port of `UserRepositoryImpl.updateSecurityData`.
  ///
  /// Updates a newly uploaded user with the server-assigned `_id`, `_rev`, and
  /// the PBKDF2 security data (`password_scheme`, `derived_key`, `salt`,
  /// `iterations`) so subsequent logins can verify the password with PBKDF2
  /// rather than requiring a server fetch.
  ///
  /// When the server response omits a credential (it is `null`), the existing
  /// value is preserved rather than overwritten -- porting `aa24dfa6c` (#15836),
  /// which guards each assignment with `?.let {}`. Writing `Value(null)` here
  /// would clear a previously stored `derived_key`/`salt` the moment the fetch
  /// failed, locking the user out of offline PBKDF2 verification.
  Future<void> updateUserSecurityData({
    required String localId,
    required String couchId,
    required String? rev,
    required String? passwordScheme,
    required String? derivedKey,
    required String? salt,
    required String? iterations,
  }) async {
    await (update(users)..where((u) => u.id.equals(localId))).write(
      UsersCompanion(
        couchId: Value(couchId),
        rev: Value(rev),
        passwordScheme: passwordScheme == null
            ? const Value.absent()
            : Value(passwordScheme),
        derivedKey: derivedKey == null
            ? const Value.absent()
            : Value(derivedKey),
        salt: salt == null ? const Value.absent() : Value(salt),
        iterations: iterations == null
            ? const Value.absent()
            : Value(iterations),
      ),
    );
  }

  /// Port of `UserRepositoryImpl.getPendingSyncUsers` — accounts whose local
  /// edits have not reached the server. Matches the Kotlin predicate: a row is
  /// pending when it has no CouchDB id yet (a freshly created local account) or
  /// its `isUpdated` flag is set.
  Future<List<UserRow>> pendingSyncUsers() =>
      (select(users)..where(
            (u) =>
                u.couchId.isNull() |
                u.couchId.equals('') |
                u.isUpdated.equals(true),
          ))
          .get();

  /// Port of `UserDao.getUsersByAnyIds` — the batched form of [getById], so a
  /// page of documents costs a handful of reads rather than one per row.
  /// Matches either identity column, as [getById] does, because a member
  /// registered on this device keeps a locally-minted `id` and carries the
  /// server's key in `couchId`.
  ///
  /// Chunked for the same reason as [MyLibraryDao.deleteNotIn], and at half the
  /// usual size because each id is bound twice.
  Future<List<UserRow>> getByAnyIds(List<String> ids) async {
    if (ids.isEmpty) return const [];
    final rows = <UserRow>[];
    for (final chunk in _chunked(ids, _sqliteVariableChunk ~/ 2)) {
      rows.addAll(
        await (select(
          users,
        )..where((u) => u.id.isIn(chunk) | u.couchId.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  /// Port of `UserDao.getGuestUsersByNames` — the guest rows a
  /// `tablet_users` page might be about to adopt. A guest is keyed
  /// `guest_<username>` in `couchId` (`UserMapper.guestIdPrefix`), so the
  /// prefix is what distinguishes one from a real account with the same name.
  Future<List<UserRow>> getGuestUsersByNames(List<String> names) async {
    if (names.isEmpty) return const [];
    final matches = await (select(
      users,
    )..where((u) => u.name.isIn(names))).get();
    // The prefix test is in Dart, not SQL: `LIKE 'guest_%'` would treat the
    // underscore as a single-character wildcard and match `guestX…` too.
    return [
      for (final user in matches)
        if (user.couchId?.startsWith('guest_') ?? false) user,
    ];
  }

  /// Batched upsert for the `tablet_users` walk.
  ///
  /// Every companion comes from [UserMapper.fromDoc], which leaves the columns
  /// a `_users` document does not carry — `key`, `iv`, `isUpdated`, and a
  /// `userImage` holding a not-yet-uploaded local path — as `Value.absent()`.
  /// Drift's `insertAllOnConflictUpdate` writes only the present columns, so
  /// the walk cannot erase them. Losing `key`/`iv` would make every health
  /// record already encrypted with them permanently unreadable.
  Future<void> upsertAll(List<UsersCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(users, rows));
  }

  /// Port of `UserDao.deleteByIds`, used only by the guest migration in the
  /// `tablet_users` walk: a guest row is re-keyed to the account that has just
  /// appeared server-side, and its old row has to go with it.
  Future<void> deleteByIds(List<String> ids) async {
    if (ids.isEmpty) return;
    await (delete(users)..where((u) => u.id.isIn(ids))).go();
  }

  /// Port of `UserRepositoryImpl.markUserUploaded` / `markUserRevUpdated` —
  /// records the server-assigned id/rev and clears the dirty flag so the row
  /// drops out of [pendingSyncUsers] until the next local edit.
  Future<void> markUploaded(
    String userId, {
    String? couchId,
    String? rev,
  }) async {
    await (update(users)..where((u) => u.id.equals(userId))).write(
      UsersCompanion(
        couchId: couchId == null ? const Value.absent() : Value(couchId),
        rev: rev == null ? const Value.absent() : Value(rev),
        isUpdated: const Value(false),
      ),
    );
  }
}

/// Port of `ResourcesRepositoryImpl.userIdPattern` (`:64-70`) — a user id as
/// the `LIKE` pattern that finds it inside the serialized JSON list
/// `my_library.user_id` holds. `CoursesRepositoryImpl.userIdPattern`
/// (`:94-100`) is character-for-character the same function over
/// `courses.user_id`, so both tables share this one.
///
/// The three replacements are in the Kotlin's order, and the order matters:
/// escaping `\` first means the backslashes this function itself introduces
/// are not escaped again. The result is only valid against a `LIKE` carrying
/// `ESCAPE '\'`.
///
/// A user id is `org.couchdb.user:ada` in practice, which contains no `LIKE`
/// metacharacter — but `_` matches any single character, so an id containing
/// one would otherwise match a *different* user's shelf entry.
String likeEscapedUserPattern(String userId) {
  final escaped = userId
      .replaceAll('\\', '\\\\')
      .replaceAll('%', '\\%')
      .replaceAll('_', '\\_');
  return '%"$escaped"%';
}

/// Shelf membership: `user_id LIKE '%"<id>"%' ESCAPE '\'`, the predicate
/// behind `MyLibraryDao.getForUserPattern` (`MyLibraryDao.kt:103`) and
/// `CourseDao.getForUserPattern` (`CourseDao.kt:17`).
///
/// The `ESCAPE` clause is the whole point, and it is why this is a function
/// rather than an inline `like()`. Five call sites interpolated the raw id
/// into the pattern, which leaves LIKE's own metacharacters live: `_` matches
/// any single character, so a shelf query for `u_1` also returned `ux1`'s
/// rows — and the catalog arm, which is the same predicate negated, dropped
/// them from the catalog at the same time, so the resource went missing from
/// both views. `%` is worse: it matches any run, so one id could claim
/// almost every shelf. Kotlin escapes at every one of these sites; the port
/// escaped at exactly one, the count added in Phase 131.
///
/// [likeEscapedUserPattern] escapes the id and drift's `escapeChar` writes
/// the matching `ESCAPE` clause, binding the pattern as a variable rather
/// than splicing it into the SQL.
/// A `LIKE '%<text>%'` that matches [text] **literally**, escaping LIKE's own
/// metacharacters.
///
/// Kotlin's course search is not a `LIKE` at all: `CourseDao` carries no title
/// query and `CoursesRepositoryImpl.filterCourses` (`:304`) filters in memory
/// with `courseTitle?.contains(searchText, ignoreCase = true)`. `contains` is
/// a literal substring test, so a learner typing `intro_a` in Kotlin matches
/// only a course actually containing `intro_a`. The port pushed the filter
/// into SQL and, unescaped, `_` became "any character" and `%` "anything at
/// all" — so `intro_a` also matched `IntroXA`, and a lone `%` matched every
/// course. This is the one site in the class where the port *invented* the
/// `LIKE`, which is why matching Kotlin means escaping rather than copying an
/// unescaped pattern.
Expression<bool> _literalContains(
  GeneratedColumn<String> column,
  String text,
) => column.like(likeEscapedLiteral(text), escapeChar: r'\');

/// The escape half of [_literalContains], exposed for tests.
String likeEscapedLiteral(String text) {
  final escaped = text
      .replaceAll('\\', '\\\\')
      .replaceAll('%', '\\%')
      .replaceAll('_', '\\_');
  return '%$escaped%';
}

Expression<bool> _shelfMembership(
  GeneratedColumn<String> userIdColumn,
  String userId,
) => userIdColumn.like(likeEscapedUserPattern(userId), escapeChar: r'\');

/// Port of `data/room/dao/MyLibraryDao.kt`.
@DriftAccessor(tables: [MyLibraryTable])
class MyLibraryDao extends DatabaseAccessor<AppDatabase>
    with _$MyLibraryDaoMixin {
  MyLibraryDao(super.db);

  /// Port of `ResourcesRepositoryImpl.batchInsertResources`' upsert loop. Drift
  /// batches the whole page into one transaction, which is what the Kotlin
  /// `dbWriteMutex` in `TransactionSyncManager` exists to approximate.
  Future<void> upsertAll(List<MyLibraryTableCompanion> rows) async {
    await batch((b) => b.insertAllOnConflictUpdate(myLibraryTable, rows));
  }

  /// Chunked for the same reason as [deleteNotIn]: a caller can pass more ids
  /// than SQLite will bind in one statement.
  Future<List<MyLibraryRow>> getByIds(List<String> ids) async {
    final rows = <MyLibraryRow>[];
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(myLibraryTable)..where((r) => r.id.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  /// Port of `MyLibraryDao.getAll` — the achievement editor's resource
  /// picker lists the whole catalog.
  Future<List<MyLibraryRow>> getAll() => select(myLibraryTable).get();

  Future<int> count() async {
    final query = selectOnly(myLibraryTable)
      ..addColumns([myLibraryTable.id.count()]);
    final row = await query.getSingle();
    return row.read(myLibraryTable.id.count()) ?? 0;
  }

  /// Reactive resource list. Replaces the Kotlin `queryListFlow` /
  /// `RealtimeSyncManager.dataUpdateFlow` pairing: Drift re-runs the query and
  /// pushes a new list whenever `my_library` changes, so a background sync
  /// updates the UI with no explicit notification channel.
  ///
  /// Text search is *not* applied here. The Kotlin (`ResourcesSearchUtils`
  /// `searchList`) ranks prefix matches ahead of contains-all-words matches and
  /// splits the query on spaces, neither of which a SQL `LIKE` can express, so
  /// the matching lives in `ResourcesRepository.searchResources` against
  /// [MyLibraryTable.titleNormal].
  ///
  /// Visibility mirrors `ResourcesRepositoryImpl.getEnrichedLibraries`: in
  /// [myLibrary] mode the shelf is shown (`userId LIKE %"userId"%`, including
  /// the user's private team resources); in catalog mode only public resources
  /// not already on the signed-in user's shelf are shown (`isPrivate = 0 AND
  /// (userId IS NULL OR userId NOT LIKE %"userId"%)`), and with no user (guest)
  /// every public resource is shown.
  Stream<List<MyLibraryRow>> watchResources({
    String? shelfUserId,
    bool myLibrary = false,
  }) {
    final statement = select(myLibraryTable);

    if (myLibrary && shelfUserId != null && shelfUserId.isNotEmpty) {
      // `getMyLibrary` — the user's shelf, private team resources included.
      statement.where((r) => _shelfMembership(r.userId, shelfUserId));
    } else {
      // `getPublic` / `getPublicNotUserPattern` — the catalog.
      statement.where((r) => r.isPrivate.equals(false));
      if (shelfUserId != null && shelfUserId.isNotEmpty) {
        statement.where(
          (r) =>
              r.userId.isNull() | _shelfMembership(r.userId, shelfUserId).not(),
        );
      }
    }

    statement.orderBy([
      // Offline-available resources first, as `getResourceListModels` does with
      // `sortedByDescending { it.isResourceOffline() }`.
      (r) =>
          OrderingTerm(expression: r.resourceOffline, mode: OrderingMode.desc),
      (r) => OrderingTerm(expression: r.titleNormal),
    ]);

    return statement.watch();
  }

  /// The user's shelf, read once. [watchResources] would set up and tear down a
  /// query stream for a single value.
  Future<List<MyLibraryRow>> resourcesOnShelf(String userId) => (select(
    myLibraryTable,
  )..where((r) => _shelfMembership(r.userId, userId))).get();

  /// Port of `MyLibraryDao.countPublicNeedingUpdateForUserPattern`
  /// (`MyLibraryDao.kt:119-124`) — how many of the user's shelf resources are
  /// not on the device, or are on it but stale. Streams rather than reads once,
  /// because in the port the stream is also the trigger: the Kotlin recomputes
  /// on every emission of `DashboardViewModel.dashboardDataFlow` (`:207-214`),
  /// two of whose four merged flows are `my_library` queries, and `readsFrom`
  /// makes drift re-run this on any write to that table. Same substitution
  /// [watchResources] makes for `RealtimeSyncManager.dataUpdateFlow`.
  ///
  /// This is the **shelf** predicate (`userId LIKE`), not the catalog one
  /// (`userId IS NULL OR userId NOT LIKE`) that sits immediately below it in
  /// the Kotlin DAO and is easy to mistake for it. The count answers "how many
  /// of *your* resources still need downloading", so a resource nobody put on
  /// your shelf is not your problem.
  ///
  /// The third clause is two cases, not one:
  ///
  /// * `resource_offline = 0` — not downloaded.
  /// * `resource_local_address IS NOT NULL AND _rev IS NOT downloaded_rev` —
  ///   the document has an attachment and the server has moved past the
  ///   revision we fetched. Note `resource_local_address` is **not** evidence
  ///   of a file on disk: `MyLibraryMapper._attachmentOf` writes the CouchDB
  ///   attachment *name* there on every sync, downloaded or not, exactly as
  ///   `MyLibrary.kt:262` does. What makes the comparison meaningful is
  ///   `downloadedRev`, which [markDownloaded] stamps from `rev` when the file
  ///   actually lands and which then stays put while `rev` follows the sync.
  ///
  /// `IS NOT` is SQLite's **null-safe** inequality, not `!=`: two NULLs compare
  /// equal (so the row is excluded) and NULL against a value compares unequal
  /// (so the row is counted). That is load-bearing — a row marked offline by an
  /// older build that never wrote `downloadedRev` carries NULL there against a
  /// non-null `_rev`, and the Kotlin counts it as needing an update. Written
  /// with `!=` it would be SQL NULL, hence false, and those rows would vanish
  /// from the count.
  ///
  /// Raw SQL rather than the query builder so the statement can be read side
  /// by side with the `@Query` it ports — `IS NOT` in particular has no
  /// query-builder spelling. The original reason given here was that "drift's
  /// `like()` takes no `ESCAPE`", which is not true: it takes an `escapeChar`
  /// and binds the pattern as a variable, which is how the five shelf
  /// predicates escape theirs ([_shelfMembership]). The rationale is corrected
  /// rather than the code, because the `IS NOT` reason stands on its own.
  Stream<int> watchResourcesNeedingUpdateCount(String userId) => customSelect(
    'SELECT COUNT(*) AS c FROM my_library '
    'WHERE is_private = 0 '
    "AND user_id LIKE ?1 ESCAPE '\\' "
    'AND (resource_offline = 0 '
    'OR (resource_local_address IS NOT NULL AND _rev IS NOT downloaded_rev))',
    variables: [Variable<String>(likeEscapedUserPattern(userId))],
    readsFrom: {myLibraryTable},
  ).watchSingle().map((row) => row.read<int>('c'));

  /// Port of `ResourcesRepositoryImpl.removeDeletedResources` — drops local rows
  /// the server no longer lists.
  ///
  /// The stale set is computed in Dart and deleted in chunks rather than
  /// binding every kept id into one `NOT IN`: SQLite caps bound variables at
  /// `SQLITE_MAX_VARIABLE_NUMBER` (999 on older builds), and a library easily
  /// exceeds that, which would fail the whole sync.
  ///
  /// **Only rows the server has actually seen are eligible**, which this had
  /// never implemented. Kotlin's prune is not "delete what the walk did not
  /// list": `deleteStalePublicNotIn` is `WHERE _rev IS NOT NULL AND _rev != ''
  /// AND isPrivate = 0 AND resourceId NOT IN (...)`
  /// (`MyLibraryDao.kt:171-175`). Without those guards the port deleted:
  ///
  /// * **a resource the user created offline.** [saveLocalResource] writes a
  ///   row with no `rev`, no CouchDB document and no outbox entry, so nothing
  ///   could give it back — and the very first resources sync removed it,
  ///   along with the pointer to the file they picked.
  /// * **a private team resource**, which the public `resources` walk never
  ///   lists and so could never keep.
  /// * **a course step's resource whose embedded sub-object carries no
  ///   `_rev`.** That one is why the guard is load-bearing for the course
  ///   stamp above rather than only adjacent to it: the courses walk would
  ///   ingest the row and the next resources walk would delete it, making the
  ///   join a one-sync artefact.
  ///
  /// Matching on `id` rather than Kotlin's `resourceId` is kept, but not for
  /// the reason first given here — no port writer makes the two differ at all
  /// ([saveLocalResource] sets `resourceId` to its own generated `id`). The
  /// column choice is therefore immaterial for every row the port can produce,
  /// and `id` is the primary key. Where they *could* differ the port is
  /// strictly the more aggressive of the two: Kotlin's `resourceId NOT IN (…)`
  /// spares a row whose `resourceId` is NULL, and a primary key never is.
  /// **The empty-list branch is not a port of anything Kotlin runs**, and
  /// calling it one (as this comment first did) invited the next reader to stop
  /// questioning it. Kotlin's `deleteAllStalePublic` (`MyLibraryDao.kt:177-178`)
  /// carries the same two guards but is **unreachable**: its only caller is the
  /// `else` of `removeDeletedResources`' `if (validCurrentIds.isNotEmpty())`
  /// (`ResourcesRepositoryImpl.kt:537-544`), and `removeDeletedResources` is
  /// itself called only when `validNewIds.isNotEmpty()` (`SyncManager.kt:416`).
  /// So when the `resources` database reports zero documents Kotlin deletes
  /// nothing, while the port deletes every public synced row — a re-provisioned
  /// satellite, or any 200 whose body lacks `total_rows`, empties the library.
  /// The guards narrow that from the whole table to the synced-and-public part;
  /// closing it entirely means not pruning on an empty walk at all, which is a
  /// behaviour change beyond the phase that found it. Recorded as a divergence,
  /// not dressed up as parity.
  Future<int> deleteNotIn(List<String> keepIds) async {
    // `isPrivate = 0 AND (_rev IS NOT NULL AND _rev != '')` — Kotlin's
    // eligibility half, applied to both branches.
    Expression<bool> prunable(MyLibraryTable r) =>
        r.isPrivate.equals(false) & r.rev.isNotNull() & r.rev.equals('').not();

    if (keepIds.isEmpty) {
      return (delete(myLibraryTable)..where(prunable)).go();
    }

    return transaction(() async {
      final query = selectOnly(myLibraryTable)
        ..addColumns([myLibraryTable.id])
        ..where(prunable(myLibraryTable));
      final eligibleIds = await query
          .map((row) => row.read(myLibraryTable.id)!)
          .get();

      final keep = keepIds.toSet();
      final stale = eligibleIds.where((id) => !keep.contains(id)).toList();

      var deleted = 0;
      for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
        deleted += await (delete(
          myLibraryTable,
        )..where((r) => r.id.isIn(chunk))).go();
      }
      return deleted;
    });
  }

  /// Port of `MyLibraryDao.getByStepId` — a course step's embedded resources.
  ///
  /// Feeds `CourseStepFragment.setupInlineResources` / `autoDownloadResources`
  /// / `prefetchNextStepResources` through
  /// `ResourcesRepositoryImpl.getAllStepResources`.
  Future<List<MyLibraryRow>> getByStepId(String stepId) =>
      (select(myLibraryTable)..where((r) => r.stepId.equals(stepId))).get();

  /// Port of `MyLibraryDao.getCourseResources(courseId, isOffline)` — the
  /// course-level download button's two lists
  /// (`CoursesRepositoryImpl.getCourseOnlineResources` / `…OfflineResources`).
  ///
  /// `resourceLocalAddress IS NOT NULL` is the Kotlin's, and it is not a test
  /// for a file on disk: the column holds the CouchDB attachment *name*,
  /// written on every sync. It says "this resource has an attachment to
  /// download at all".
  Future<List<MyLibraryRow>> getCourseResources(
    String courseId, {
    required bool isOffline,
  }) =>
      (select(myLibraryTable)..where(
            (r) =>
                r.courseId.equals(courseId) &
                r.resourceOffline.equals(isOffline) &
                r.resourceLocalAddress.isNotNull(),
          ))
          .get();

  /// Gets a single resource by its local id.
  Future<MyLibraryRow?> getById(String id) =>
      (select(myLibraryTable)..where((r) => r.id.equals(id))).getSingleOrNull();

  /// Port of `MyLibraryDao.countByTitle` — the duplicate-title guard
  /// `resourceTitleExists` reads. Counts rows whose `titleNormal` matches
  /// the normalized title exactly.
  Future<bool> countByTitle(String normalizedTitle) =>
      (select(myLibraryTable)
            ..where((r) => r.titleNormal.equals(normalizedTitle))
            ..limit(1))
          .map((row) => row.id)
          .get()
          .then((rows) => rows.isNotEmpty);

  /// Records that the attachment is now on disk.
  ///
  /// `downloadedRev` is what lets a later sync notice the server replaced the
  /// attachment: the row's `rev` moves on while this stays put.
  Future<int> markDownloaded(String id, String path, String? rev) =>
      (update(myLibraryTable)..where((r) => r.id.equals(id))).write(
        MyLibraryTableCompanion(
          resourceLocalAddress: Value(path),
          resourceOffline: const Value(true),
          downloadedRev: Value(rev),
        ),
      );

  /// Clears the offline flag when the file is gone or unusable.
  Future<int> markNotDownloaded(String id) =>
      (update(myLibraryTable)..where((r) => r.id.equals(id))).write(
        const MyLibraryTableCompanion(
          resourceLocalAddress: Value(null),
          resourceOffline: Value(false),
        ),
      );

  /// Port of `MyLibraryDao.getWithResourceId` — every library row that carries
  /// a `resourceId`, so storage management can resolve a file's on-disk
  /// `docId` directory back to a title.
  Future<List<MyLibraryRow>> getWithResourceId() =>
      (select(myLibraryTable)..where((r) => r.resourceId.isNotNull())).get();

  /// Port of `MyLibraryDao.getOfflineByResourceIds` — rows currently marked
  /// offline for a set of resource ids, so a delete can clear exactly those
  /// flags. Chunked for the same reason as [deleteNotIn].
  Future<List<MyLibraryRow>> getOfflineByResourceIds(
    List<String> resourceIds,
  ) async {
    final rows = <MyLibraryRow>[];
    for (final chunk in _chunked(resourceIds, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(myLibraryTable)..where(
              (r) => r.resourceId.isIn(chunk) & r.resourceOffline.equals(true),
            ))
            .get(),
      );
    }
    return rows;
  }

  /// Clears the offline flag for a set of resource ids. Port of
  /// `ResourcesRepositoryImpl.markResourcesAsNotOffline`, which upserts the
  /// fetched rows with `resourceOffline = false`. Done in chunks because the
  /// set of ids can exceed SQLite's bound-variable limit.
  Future<void> markResourcesNotOffline(List<String> resourceIds) async {
    if (resourceIds.isEmpty) return;
    final rows = await getOfflineByResourceIds(resourceIds);
    if (rows.isEmpty) return;
    await upsertAll(
      rows
          .map((r) => r.copyWith(resourceOffline: false).toCompanion(true))
          .toList(growable: false),
    );
  }
}

/// Comfortably under SQLite's 999-variable floor.
const int _sqliteVariableChunk = 500;

Iterable<List<T>> _chunked<T>(List<T> items, int size) sync* {
  for (var i = 0; i < items.length; i += size) {
    yield items.sublist(i, i + size > items.length ? items.length : i + size);
  }
}

/// Port of `data/room/dao/MyCourseDao.kt` and `CourseStepDao.kt`.
@DriftAccessor(tables: [Courses, CourseSteps, MyLibraryTable])
class CourseDao extends DatabaseAccessor<AppDatabase> with _$CourseDaoMixin {
  CourseDao(super.db);

  /// Port of `CoursesRepositoryImpl.batchInsertMyCourses`' upsert loop. Courses
  /// and their steps go in as one transaction so a course is never visible
  /// without its steps.
  Future<void> upsertAll(
    List<CoursesCompanion> courseRows,
    List<CourseStepsCompanion> stepRows,
  ) async {
    await transaction(() async {
      await batch((b) {
        b.insertAllOnConflictUpdate(courses, courseRows);
        b.insertAllOnConflictUpdate(courseSteps, stepRows);
      });

      // Upserting alone cannot shrink a course: if it drops from five steps to
      // three, steps 3 and 4 are simply never written again and would linger.
      // Step ids are position-derived, so anything past the new length is stale.
      final keptByCourse = <String, Set<String>>{};
      for (final step in stepRows) {
        final courseId = step.courseId.value;
        if (courseId == null) continue;
        keptByCourse.putIfAbsent(courseId, () => <String>{}).add(step.id.value);
      }

      for (final course in courseRows) {
        final courseId = course.id.value;
        final kept = keptByCourse[courseId];
        final statement = delete(courseSteps)
          ..where((s) => s.courseId.equals(courseId));
        if (kept != null && kept.isNotEmpty) {
          // Step counts per course are small, so no chunking is needed here.
          statement.where((s) => s.id.isNotIn(kept.toList()));
        }
        await statement.go();

        // Every `my_library` stamp for this course is released here, and the
        // courses walk re-writes the current ones immediately afterwards
        // ([upsertCourseResources]).
        //
        // Clearing *all* of them rather than only those whose step id has
        // vanished is the whole point, and the narrower version was tried
        // first and was wrong: step ids are positional, so deleting a step
        // hands its id to whichever step shifted up, and a resource left
        // pointing at that id is not dangling — it is attached to the wrong
        // live step, which `getByStepId` will happily return.
        //
        // The rule this encodes is that **only a writer that has the step's
        // resources may leave a stamp standing**. The courses walk has them;
        // `ShelfSyncRepository._pullShelfCourses` writes the same rows through
        // this method and discards them, so after a shelf-only sync (the sync
        // centre's per-tile retry) the stamps are simply absent until the next
        // courses walk. An empty inline resource list is a cost worth paying
        // to make a wrong one unreachable.
        await (update(
          myLibraryTable,
        )..where((r) => r.courseId.equals(courseId))).write(
          const MyLibraryTableCompanion(
            stepId: Value(null),
            courseId: Value(null),
          ),
        );
      }
    });
  }

  /// The courses walk's third table.
  ///
  /// A course document carries its steps' resources inline, and Kotlin's walk
  /// writes them into `my_library` from inside the same batch
  /// (`TransactionSyncManager.kt:302`). The port reaches that table through the
  /// sibling accessor rather than giving [CoursesRepository] a second DAO,
  /// because its constructor is built in `app_providers.dart`: an added
  /// parameter there is a file this lane does not own, and an *optional* one
  /// that production never passes is how a ported feature ends up green and
  /// dead.
  MyLibraryDao get _library => attachedDatabase.myLibraryDao;

  /// The `my_library` rows these ids already have, so the caller can hand
  /// [MyLibraryMapper.fromDoc] the columns the server does not own — the shelf
  /// membership above all. See `mapper_preserves_local_columns_test.dart`.
  Future<List<MyLibraryRow>> existingResources(List<String> ids) =>
      _library.getByIds(ids);

  /// Port of `flushPendingCourseResources` (`CoursesRepositoryImpl.kt:819-863`)
  /// minus the buffer: the port parses and writes in one page loop, so the rows
  /// arrive built.
  ///
  /// No release step of its own. [upsertAll] has already cleared every stamp
  /// belonging to the courses on this page, so writing the current rows here is
  /// the whole of the update: a resource the document no longer claims is
  /// simply one that is not re-stamped. A resource appearing in two steps of
  /// one course keeps the **last** step, because there is one row per resource
  /// id and the later companion wins the upsert — which is what Kotlin's
  /// `REPLACE` over the twice-mutated entity does.
  Future<void> upsertCourseResources(
    List<MyLibraryTableCompanion> resourceRows,
  ) async {
    if (resourceRows.isEmpty) return;
    await _library.upsertAll(resourceRows);
  }

  Future<CourseRow?> getById(String courseId) =>
      (select(courses)..where((c) => c.id.equals(courseId))).getSingleOrNull();

  /// Chunked for the same reason as [deleteNotIn].
  Future<List<CourseRow>> getByIds(List<String> ids) async {
    final rows = <CourseRow>[];
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(courses)..where((c) => c.id.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  Future<int> count() async {
    final query = selectOnly(courses)..addColumns([courses.id.count()]);
    final row = await query.getSingle();
    return row.read(courses.id.count()) ?? 0;
  }

  /// Port of `CoursesRepositoryImpl.getCourseSteps` — ordered by position within
  /// the course.
  Future<List<CourseStepRow>> getSteps(String courseId) {
    return (select(courseSteps)
          ..where((s) => s.courseId.equals(courseId))
          ..orderBy([(s) => OrderingTerm(expression: s.stepIndex)]))
        .get();
  }

  Stream<CourseRow?> watchCourse(String courseId) => (select(
    courses,
  )..where((c) => c.id.equals(courseId))).watchSingleOrNull();

  Stream<List<CourseStepRow>> watchSteps(String courseId) {
    return (select(courseSteps)
          ..where((s) => s.courseId.equals(courseId))
          ..orderBy([(s) => OrderingTerm(expression: s.stepIndex)]))
        .watch();
  }

  /// Step count per course, batched — the `max` behind the courses list's
  /// progress filter and the take-course progress bar. Mirrors
  /// `CourseStepDao.getByCourseIds(courseIds).groupBy { it.courseId }` without
  /// materializing every step row; only the count is needed here.
  Future<Map<String, int>> stepCountsByCourseIds(List<String> courseIds) async {
    if (courseIds.isEmpty) return const {};
    final counts = <String, int>{};
    for (final chunk in _chunked(courseIds, _sqliteVariableChunk)) {
      final stmt = selectOnly(courseSteps)
        ..addColumns([courseSteps.courseId, courseSteps.id.count()])
        ..where(courseSteps.courseId.isIn(chunk))
        ..groupBy([courseSteps.courseId]);
      for (final row in await stmt.get()) {
        final courseId = row.read(courseSteps.courseId);
        if (courseId != null) {
          counts[courseId] = row.read(courseSteps.id.count()) ?? 0;
        }
      }
    }
    return counts;
  }

  /// Reactive course list. Combines the read paths of
  /// `CoursesRepositoryImpl.getMyCoursesFlow`, `search` and `filterCourses`.
  ///
  /// [query] matches the diacritic-folded title. [shelfUserId] restricts to
  /// courses the user has added ("my courses"). [gradeLevel] / [subjectLevel]
  /// are the two filter spinners on the courses screen.
  Stream<List<CourseRow>> watchCourses({
    String? query,
    String? shelfUserId,
    String? gradeLevel,
    String? subjectLevel,
  }) {
    final statement = select(courses);

    final trimmed = query?.trim().toLowerCase();
    if (trimmed != null && trimmed.isNotEmpty) {
      statement.where((c) => _literalContains(c.courseTitleNormal, trimmed));
    }
    if (shelfUserId != null && shelfUserId.isNotEmpty) {
      statement.where((c) => _shelfMembership(c.userId, shelfUserId));
    }
    if (gradeLevel != null && gradeLevel.isNotEmpty) {
      statement.where((c) => c.gradeLevel.equals(gradeLevel));
    }
    if (subjectLevel != null && subjectLevel.isNotEmpty) {
      statement.where((c) => c.subjectLevel.equals(subjectLevel));
    }

    statement.orderBy([(c) => OrderingTerm(expression: c.courseTitleNormal)]);
    return statement.watch();
  }

  /// The distinct values behind the grade/subject filters.
  Future<List<String>> distinctGradeLevels() => _distinct(courses.gradeLevel);

  Future<List<String>> distinctSubjectLevels() =>
      _distinct(courses.subjectLevel);

  /// Reactive variants. The filter dropdowns watch these rather than deriving
  /// their options from the *filtered* course list — that would make selecting
  /// a level invalidate the very options it was chosen from.
  Stream<List<String>> watchDistinctGradeLevels() =>
      _watchDistinct(courses.gradeLevel);

  Stream<List<String>> watchDistinctSubjectLevels() =>
      _watchDistinct(courses.subjectLevel);

  Stream<List<String>> _watchDistinct(GeneratedColumn<String> column) {
    return (_distinctQuery(column)).watch().map(
      (rows) => rows
          .map((row) => row.read(column))
          .whereType<String>()
          .toList(growable: false),
    );
  }

  JoinedSelectStatement<HasResultSet, dynamic> _distinctQuery(
    GeneratedColumn<String> column,
  ) {
    return selectOnly(courses, distinct: true)
      ..addColumns([column])
      ..where(column.isNotNull() & column.equals('').not())
      ..orderBy([OrderingTerm(expression: column)]);
  }

  Future<List<String>> _distinct(GeneratedColumn<String> column) async {
    final rows = await _distinctQuery(column).get();
    return rows
        .map((row) => row.read(column))
        .whereType<String>()
        .toList(growable: false);
  }

  /// The user's shelf, read once — see [MyLibraryDao.resourcesOnShelf].
  Future<List<CourseRow>> coursesOnShelf(String userId) =>
      (select(courses)..where((c) => _shelfMembership(c.userId, userId))).get();

  /// Port of `CoursesRepositoryImpl.isMyCourse`.
  Future<bool> isMyCourse(String courseId, String userId) async {
    final course = await getById(courseId);
    return course?.userId.contains(userId) ?? false;
  }

  /// Port of `joinCourse` / `leaveCourse` — local shelf membership only. The
  /// server-side shelf write travels with the upload framework, which is not
  /// ported yet.
  Future<void> setShelfMembership(
    String courseId,
    String userId, {
    required bool joined,
  }) async {
    final course = await getById(courseId);
    if (course == null) return;

    final updated = joined
        ? ({...course.userId, userId}.toList(growable: false))
        : course.userId.where((id) => id != userId).toList(growable: false);

    await (update(courses)..where((c) => c.id.equals(courseId))).write(
      CoursesCompanion(userId: Value(updated)),
    );
  }

  /// Drops local courses (and their steps) the server no longer lists.
  ///
  /// Chunked for the same reason as [MyLibraryDao.deleteNotIn].
  Future<void> deleteNotIn(List<String> keepIds) async {
    await transaction(() async {
      if (keepIds.isEmpty) {
        await delete(courseSteps).go();
        await delete(courses).go();
        return;
      }

      final localIds = await (selectOnly(
        courses,
      )..addColumns([courses.id])).map((row) => row.read(courses.id)!).get();

      final keep = keepIds.toSet();
      final stale = localIds.where((id) => !keep.contains(id)).toList();

      for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
        await (delete(courseSteps)..where((s) => s.courseId.isIn(chunk))).go();
        await (delete(courses)..where((c) => c.id.isIn(chunk))).go();
      }
    });
  }
}

/// Port of `data/room/dao/RemovedLogDao.kt`.
@DriftAccessor(tables: [RemovedLogs])
class RemovedLogDao extends DatabaseAccessor<AppDatabase>
    with _$RemovedLogDaoMixin {
  RemovedLogDao(super.db);

  /// Keyed on type+user+doc so recording the same removal twice is a no-op.
  ///
  /// Components are percent-encoded before joining: CouchDB user ids contain a
  /// colon (`org.couchdb.user:name`), so a raw join would let two different
  /// tuples produce the same key and overwrite each other's removal record.
  static String keyFor(String type, String userId, String docId) =>
      '${Uri.encodeComponent(type)}:'
      '${Uri.encodeComponent(userId)}:'
      '${Uri.encodeComponent(docId)}';

  Future<void> record({
    required String type,
    required String userId,
    required String docId,
  }) {
    return into(removedLogs).insertOnConflictUpdate(
      RemovedLogsCompanion.insert(
        id: keyFor(type, userId, docId),
        type: type,
        docId: docId,
        userId: userId,
      ),
    );
  }

  /// Called when the user re-adds something they had removed.
  Future<void> clear({
    required String type,
    required String userId,
    required String docId,
  }) {
    return (delete(
      removedLogs,
    )..where((r) => r.id.equals(keyFor(type, userId, docId)))).go();
  }

  /// Port of `RemovedLogDao.getRemovedDocIds`.
  Future<List<String>> removedDocIds(String type, String userId) async {
    final rows = await (select(
      removedLogs,
    )..where((r) => r.type.equals(type) & r.userId.equals(userId))).get();
    return rows.map((r) => r.docId).toList(growable: false);
  }
}

/// Port of `data/room/dao/DictionaryDao.kt`.
@DriftAccessor(tables: [DictionaryEntries])
class DictionaryDao extends DatabaseAccessor<AppDatabase>
    with _$DictionaryDaoMixin {
  DictionaryDao(super.db);

  Future<int> count() async {
    final query = selectOnly(dictionaryEntries)
      ..addColumns([dictionaryEntries.id.count()]);
    final row = await query.getSingle();
    return row.read(dictionaryEntries.id.count()) ?? 0;
  }

  Future<void> replaceAll(List<DictionaryEntriesCompanion> entries) {
    return transaction(() async {
      await delete(dictionaryEntries).go();
      await batch((batch) {
        batch.insertAll(dictionaryEntries, entries);
      });
    });
  }

  Future<DictionaryRow?> findByWord(String word) {
    final normalized = word.trim().toLowerCase();
    if (normalized.isEmpty) return Future.value(null);
    return (select(dictionaryEntries)
          ..where((entry) => entry.wordNormalized.equals(normalized))
          ..limit(1))
        .getSingleOrNull();
  }
}

/// Port of `data/room/dao/NotificationDao.kt`.
@DriftAccessor(tables: [Notifications])
class NotificationDao extends DatabaseAccessor<AppDatabase>
    with _$NotificationDaoMixin {
  NotificationDao(super.db);

  Stream<List<NotificationRow>> watchForUser(
    String userId, {
    String filter = 'all',
    bool isAdmin = false,
  }) {
    final query = select(notifications)
      ..where(
        (notification) =>
            _userMatch(notification, userId, isAdmin) &
            notification.message.equals('INVALID').not() &
            notification.message.equals('').not(),
      );
    if (filter == 'read') {
      query.where((notification) => notification.isRead.equals(true));
    } else if (filter == 'unread') {
      query.where((notification) => notification.isRead.equals(false));
    }
    query.orderBy([
      (notification) => OrderingTerm(expression: notification.isRead),
      (notification) => OrderingTerm(
        expression: notification.createdAt,
        mode: OrderingMode.desc,
      ),
    ]);
    return query.watch();
  }

  Stream<int> watchUnreadCount(String userId, {bool isAdmin = false}) {
    final count = notifications.id.count();
    final query = selectOnly(notifications)
      ..addColumns([count])
      ..where(
        _userMatch(notifications, userId, isAdmin) &
            notifications.isRead.equals(false),
      );
    return query.watchSingle().map((row) => row.read(count) ?? 0);
  }

  // The user match: the user's own rows, plus `SYSTEM` rows when [isAdmin]
  // (Kotlin's admin notification scope). Built outside the `where` lambda
  // because drift's `|`/`&` operators are between `Expression<bool>`, not a
  // bare `bool` and an expression.
  Expression<bool> _userMatch(
    $NotificationsTable n,
    String userId,
    bool admin,
  ) {
    var match = n.userId.equals(userId);
    if (admin) match = match | n.userId.equals('SYSTEM');
    return match;
  }

  Future<void> upsert(NotificationsCompanion notification) =>
      into(notifications).insertOnConflictUpdate(notification);

  /// Port of `NotificationDao.upsertAll` — the sync-in batch write. Chunked
  /// so a planet with many notifications stays under SQLite's variable cap.
  Future<void> upsertAll(List<NotificationsCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(notifications, rows));
  }

  /// Port of `NotificationDao.getByIds` — used by the sync-in to preserve the
  /// `needsSync`/`isRead` of a row whose read state was changed locally but
  /// not yet uploaded, so a re-pull does not clobber it.
  Future<Map<String, NotificationRow>> getByIds(List<String> ids) async {
    final found = <String, NotificationRow>{};
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      final rows = await (select(
        notifications,
      )..where((n) => n.id.isIn(chunk))).get();
      for (final row in rows) {
        found[row.id] = row;
      }
    }
    return found;
  }

  Future<NotificationRow?> getById(String id) => (select(
    notifications,
  )..where((row) => row.id.equals(id))).getSingleOrNull();

  /// Port of `NotificationDao.markSummaryAsRead(userId, type)` — marks the
  /// **unread** rows for [userId] of [type] read, flagging server-originated
  /// ones for read-state upload. Used when a notification id starts with
  /// `summary_`.
  ///
  /// One statement with the Kotlin's `CASE WHEN`, not two updates. Splitting it
  /// loses the `is_read = 0` scope on the second half — once the first update
  /// has flipped the rows there is no way to tell which ones it changed — so
  /// every already-read server row gets re-flagged and re-uploaded on the next
  /// sync. The `WHERE` also drives the returned count, which is "how many were
  /// marked", not "how many exist".
  Future<int> markSummaryAsRead(String? userId, String type) async {
    if (userId == null || userId.isEmpty) return 0;
    return customUpdate(
      'UPDATE notifications SET is_read = 1, '
      'needs_sync = CASE WHEN is_from_server = 1 THEN 1 ELSE needs_sync END '
      'WHERE user_id = ? AND type = ? AND is_read = 0',
      variables: [Variable.withString(userId), Variable.withString(type)],
      updates: {notifications},
    );
  }

  /// Port of `NotificationDao.markAsRead(notificationId)`
  /// (`NotificationDao.kt:15-16`) — the **single-row** overload, whose SQL does
  /// not mention `createdAt`:
  ///
  ///   `UPDATE notifications SET isRead = 1, needsSync = CASE … WHERE id = :id`
  ///
  /// It exists separately from [markAsRead] because the two Kotlin queries
  /// differ in exactly that column, and the port had collapsed both onto the
  /// stamping one. That rewrote a row's `createdAt` to now whenever a learner
  /// tapped it or its *Mark as read* button — invisible while the row drew an
  /// absolute date, loud once it draws a relative one (a week-old notification
  /// reads "Just now"), and destructive either way: `watchForUser` sorts on
  /// `createdAt DESC`, and the row's real age is gone.
  Future<int> markOneAsRead(String id) => customUpdate(
    'UPDATE notifications SET is_read = 1, '
    'needs_sync = CASE WHEN is_from_server = 1 THEN 1 ELSE needs_sync END '
    'WHERE id = ?',
    variables: [Variable.withString(id)],
    updates: {notifications},
  );

  /// Port of `NotificationDao.markAsRead(ids, createdAt)`
  /// (`NotificationDao.kt:45-46`) — the **bulk** overload, which does stamp a
  /// fresh `createdAt` (Kotlin's `Date`), and flags server-originated rows for
  /// read-state upload via `needsSync`.
  ///
  /// Two callers, both stamping deliberately: selection mode's "mark selected
  /// as read", and the tray's *Mark as Read* / *View Task* action buttons
  /// (`NotificationActionReceiver.kt:81` calls the same bulk overload). The
  /// stamp moves the marked rows to the top of the list in the Android app, so
  /// the port has to do the same or the two lists order differently. See
  /// [markOneAsRead] for the single-row path, which is the tray *body* tap.
  Future<int> markAsRead(Iterable<String> ids, {int? createdAt}) async {
    final values = ids.toList(growable: false);
    if (values.isEmpty) return 0;
    final now = createdAt ?? DateTime.now().millisecondsSinceEpoch;
    final count =
        await (update(
          notifications,
        )..where((row) => row.id.isIn(values))).write(
          NotificationsCompanion(
            isRead: const Value(true),
            createdAt: Value(now),
          ),
        );
    await (update(notifications)
          ..where((row) => row.id.isIn(values) & row.isFromServer.equals(true)))
        .write(const NotificationsCompanion(needsSync: Value(true)));
    return count;
  }

  /// Port of `NotificationDao.markAllAsRead(userId, createdAt)`.
  ///
  /// One statement with the Kotlin's `CASE WHEN`, for the reason spelled out on
  /// [markSummaryAsRead]: as two updates, the second could only re-select by
  /// `is_read = 1` — which by then matches every row the user had *ever* read —
  /// so one "mark all read" tap re-flagged the whole history and the next sync
  /// PUT every one of those documents back to CouchDB.
  Future<int> markAllAsRead(String userId, {int? createdAt}) async {
    final now = createdAt ?? DateTime.now().millisecondsSinceEpoch;
    return customUpdate(
      'UPDATE notifications SET is_read = 1, created_at = ?, '
      'needs_sync = CASE WHEN is_from_server = 1 THEN 1 ELSE needs_sync END '
      'WHERE user_id = ? AND is_read = 0',
      variables: [Variable.withInt(now), Variable.withString(userId)],
      updates: {notifications},
    );
  }

  Future<int> deleteById(String id) =>
      (delete(notifications)..where((row) => row.id.equals(id))).go();

  /// Port of `NotificationDao.deleteByIds` (`NotificationDao.kt:64-65`), which
  /// selection mode's *Delete* uses.
  ///
  /// A plain local `DELETE`: nothing is enqueued, no tombstone is written, and
  /// the notifications sync-in runs no `deleteNotIn`, so a deleted
  /// *server-originated* row comes back on the next sync. That is the Kotlin's
  /// behaviour too — `deleteNotifications` reaches the same statement and
  /// `UploadConfigs` mentions notifications nowhere — so it is reproduced, not
  /// fixed.
  Future<int> deleteByIds(Iterable<String> ids) async {
    final values = ids.toList(growable: false);
    if (values.isEmpty) return 0;
    var deleted = 0;
    for (final chunk in _chunked(values, _sqliteVariableChunk)) {
      deleted += await (delete(
        notifications,
      )..where((row) => row.id.isIn(chunk))).go();
    }
    return deleted;
  }

  /// Port of `NotificationDao.getPendingSyncNotifications` — server-originated
  /// rows flagged for read-state upload. Only rows with a server `rev` can be
  /// PUT back (no rev means the row was authored locally, not yet on the
  /// server).
  Future<List<NotificationRow>> getPendingSyncNotifications() => (select(
    notifications,
  )..where((row) => row.needsSync.equals(true) & row.rev.isNotNull())).get();

  /// Port of `NotificationDao.markSynced(id, rev)` — clears `needsSync` and
  /// records the fresh `rev` the server returned after a read-state PUT.
  Future<int> markSynced(String id, String? rev) =>
      (update(notifications)..where((row) => row.id.equals(id))).write(
        NotificationsCompanion(
          needsSync: const Value(false),
          rev: rev == null ? const Value.absent() : Value(rev),
        ),
      );
}

/// Port of `data/room/dao/MyLifeDao.kt`.
@DriftAccessor(tables: [MyLifeEntries])
class MyLifeDao extends DatabaseAccessor<AppDatabase> with _$MyLifeDaoMixin {
  MyLifeDao(super.db);

  Stream<List<MyLifeRow>> watchForUser(String userId) =>
      (select(myLifeEntries)
            ..where((row) => row.userId.equals(userId))
            ..orderBy([(row) => OrderingTerm(expression: row.weight)]))
          .watch();

  Future<void> seedIfEmpty(
    String userId,
    List<MyLifeEntriesCompanion> entries,
  ) async {
    await transaction(() async {
      final countColumn = myLifeEntries.id.count();
      final countQuery = selectOnly(myLifeEntries)
        ..addColumns([countColumn])
        ..where(myLifeEntries.userId.equals(userId));
      final count = (await countQuery.getSingle()).read(countColumn) ?? 0;
      if (count == 0) {
        await batch((batch) => batch.insertAll(myLifeEntries, entries));
      }
    });
  }

  Future<void> setVisibility(String id, {required bool visible}) =>
      (update(myLifeEntries)..where((row) => row.id.equals(id))).write(
        MyLifeEntriesCompanion(isVisible: Value(visible)),
      );

  Future<void> reorder(List<String> orderedIds) async {
    await transaction(() async {
      for (var index = 0; index < orderedIds.length; index++) {
        await (update(myLifeEntries)
              ..where((row) => row.id.equals(orderedIds[index])))
            .write(MyLifeEntriesCompanion(weight: Value(index)));
      }
    });
  }
}

/// Port of `data/room/dao/PersonalDao.kt`.
@DriftAccessor(tables: [PersonalEntries])
class PersonalDao extends DatabaseAccessor<AppDatabase>
    with _$PersonalDaoMixin {
  PersonalDao(super.db);

  Stream<List<PersonalRow>> watchForUser(String userId) =>
      (select(personalEntries)
            ..where((row) => row.userId.equals(userId))
            ..orderBy([
              (row) =>
                  OrderingTerm(expression: row.date, mode: OrderingMode.desc),
            ]))
          .watch();

  Future<bool> titleExists(
    String userId,
    String normalizedTitle, {
    String? excludingId,
  }) async {
    final query = select(personalEntries)
      ..where(
        (row) =>
            row.userId.equals(userId) &
            row.titleNormalized.equals(normalizedTitle),
      );
    if (excludingId != null) {
      query.where((row) => row.id.equals(excludingId).not());
    }
    return (await query.get()).isNotEmpty;
  }

  Future<void> upsert(PersonalEntriesCompanion row) =>
      into(personalEntries).insertOnConflictUpdate(row);

  Future<PersonalRow?> getById(String id) => (select(
    personalEntries,
  )..where((row) => row.id.equals(id))).getSingleOrNull();

  Future<int> deleteById(String id) =>
      (delete(personalEntries)..where((row) => row.id.equals(id))).go();

  Future<List<PersonalRow>> pendingUploads(String userId) =>
      (select(personalEntries)..where(
            (row) => row.userId.equals(userId) & row.isUploaded.equals(false),
          ))
          .get();
}

/// Port of `data/room/dao/RatingDao.kt`.
@DriftAccessor(tables: [Ratings])
class RatingDao extends DatabaseAccessor<AppDatabase> with _$RatingDaoMixin {
  RatingDao(super.db);

  Stream<List<RatingRow>> watchForItem(String type, String itemId) => (select(
    ratings,
  )..where((row) => row.type.equals(type) & row.item.equals(itemId))).watch();

  /// One-shot read of the same rows [watchForItem] streams, for the
  /// completion-rating check (`RatingsRepository.summary`) that needs a single
  /// answer rather than a subscription.
  Future<List<RatingRow>> forItem(String type, String itemId) => (select(
    ratings,
  )..where((row) => row.type.equals(type) & row.item.equals(itemId))).get();

  Future<RatingRow?> findUserRating(
    String type,
    String itemId,
    String userId,
  ) =>
      (select(ratings)
            ..where(
              (row) =>
                  row.type.equals(type) &
                  row.item.equals(itemId) &
                  row.userId.equals(userId),
            )
            ..limit(1))
          .getSingleOrNull();

  Future<void> upsert(RatingsCompanion rating) =>
      into(ratings).insertOnConflictUpdate(rating);

  Future<List<RatingRow>> pendingUploads() =>
      (select(ratings)..where(
            (row) =>
                row.isUpdated.equals(true) & row.userId.like('guest%').not(),
          ))
          .get();

  Future<int> markUploaded(String id) =>
      (update(ratings)..where((row) => row.id.equals(id))).write(
        const RatingsCompanion(isUpdated: Value(false)),
      );

  Future<RatingRow?> findById(String id) =>
      (select(ratings)..where((row) => row.id.equals(id))).getSingleOrNull();

  /// The local rows a page of `ratings` documents might already be about.
  ///
  /// Two lookups because a rating has two identities. A rating submitted on
  /// this device is keyed by a locally-minted id and has no `_id` until a walk
  /// supplies one, so the server's copy of it is found by `(type, item,
  /// userId)`; a rating already synced once is found by its CouchDB id.
  /// Without the first, the walk would insert a duplicate row alongside the
  /// user's own and the average would count their rating twice.
  Future<List<RatingRow>> getByCouchIds(List<String> ids) async {
    if (ids.isEmpty) return const [];
    return (select(
      ratings,
    )..where((row) => row.id.isIn(ids) | row.couchId.isIn(ids))).get();
  }

  /// Every rating this device authored that has not been given a CouchDB id
  /// yet — the candidates for the `(type, item, userId)` match above.
  Future<List<RatingRow>> unsyncedLocalRatings() => (select(
    ratings,
  )..where((row) => row.couchId.isNull() | row.couchId.equals(''))).get();

  Future<void> upsertAll(List<RatingsCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(ratings, rows));
  }

  /// Stamps the CouchDB id and rev onto a row without touching the rating the
  /// user is still waiting to upload. An UPDATE rather than an upsert, because
  /// `insertOnConflictUpdate` validates its companion against the insert path
  /// and would reject one that carries only the identity columns.
  Future<int> recordServerIdentity(String id, String couchId, String? rev) =>
      (update(ratings)..where((row) => row.id.equals(id))).write(
        RatingsCompanion(couchId: Value(couchId), rev: Value(rev)),
      );
}

/// Port of `data/room/dao/RetryDao.kt`, backing [OutboxEntries].
///
/// The status transitions mirror `RetryQueue`: `pending` → `in_progress` while
/// a drain holds it → `completed`, or back to `pending` with a later
/// [OutboxEntries.nextAttemptAt], or `abandoned` once the attempts run out.
@DriftAccessor(tables: [OutboxEntries])
class OutboxDao extends DatabaseAccessor<AppDatabase> with _$OutboxDaoMixin {
  OutboxDao(super.db);

  static const String statusPending = 'pending';
  static const String statusInProgress = 'in_progress';
  static const String statusCompleted = 'completed';
  static const String statusAbandoned = 'abandoned';

  Future<void> upsert(OutboxEntriesCompanion entry) =>
      into(outboxEntries).insertOnConflictUpdate(entry);

  /// Writes only the fields [values] carries.
  ///
  /// Not [upsert]: `insertOnConflictUpdate` validates the companion against the
  /// *insert* path, so a partial one is rejected for the required columns it
  /// omits even though the row already exists.
  Future<int> patch(String id, OutboxEntriesCompanion values) =>
      (update(outboxEntries)..where((row) => row.id.equals(id))).write(values);

  Future<OutboxRow?> getById(String id) => (select(
    outboxEntries,
  )..where((row) => row.id.equals(id))).getSingleOrNull();

  /// The open operation for an item, if one is already queued.
  ///
  /// Kotlin's `getExistingOperation`. Only `pending`/`in_progress` count — a
  /// completed or abandoned row must not block a fresh enqueue.
  Future<OutboxRow?> findOpen(String uploadType, String itemId) {
    return (select(outboxEntries)
          ..where(
            (row) =>
                row.uploadType.equals(uploadType) &
                row.itemId.equals(itemId) &
                row.status.isIn([statusPending, statusInProgress]),
          )
          ..limit(1))
        .getSingleOrNull();
  }

  /// Every operation recorded for one item, whatever its status.
  ///
  /// [findOpen] deliberately ignores an `abandoned` row so a fresh enqueue is
  /// never blocked by one; the consequence is that a permanently-refused write
  /// leaves a row behind that nothing else selects. This is how the record of
  /// that refusal is read back.
  Future<List<OutboxRow>> forItem(String uploadType, String itemId) =>
      (select(outboxEntries)
            ..where(
              (row) =>
                  row.uploadType.equals(uploadType) & row.itemId.equals(itemId),
            )
            ..orderBy([(row) => OrderingTerm(expression: row.createdAt)]))
          .get();

  /// Drops the abandoned rows recorded against one item.
  ///
  /// An abandoned row is never reused — [findOpen] ignores it — and `cleanup()`
  /// has no caller, so without this a record that is refused once is reported
  /// as stranded for the life of the install, including after a later attempt
  /// delivers it. The uploader calls this when the server finally accepts the
  /// record, which is the only event that makes the old refusals untrue.
  Future<int> clearAbandonedFor(String uploadType, String itemId) =>
      (delete(outboxEntries)..where(
            (row) =>
                row.uploadType.equals(uploadType) &
                row.itemId.equals(itemId) &
                row.status.equals(statusAbandoned),
          ))
          .go();

  /// Operations of [uploadType] the queue has given up on.
  ///
  /// A 409 is `permanent` to [OutboxDrainer] — `code < 500` — so a conflicting
  /// write is abandoned on its first attempt and never retried under that row.
  /// Nothing read these until now, which is why a health record the server
  /// refused could vanish with no error anywhere: the row was kept, and the
  /// keeping was the whole of the "observability".
  Future<List<OutboxRow>> abandoned(String uploadType) =>
      (select(outboxEntries)
            ..where(
              (row) =>
                  row.uploadType.equals(uploadType) &
                  row.status.equals(statusAbandoned),
            )
            ..orderBy([(row) => OrderingTerm(expression: row.createdAt)]))
          .get();

  /// Pending operations whose backoff has elapsed, oldest first.
  Future<List<OutboxRow>> due(int now) {
    return (select(outboxEntries)
          ..where(
            (row) =>
                row.status.equals(statusPending) &
                row.nextAttemptAt.isSmallerOrEqualValue(now),
          )
          ..orderBy([(row) => OrderingTerm(expression: row.createdAt)]))
        .get();
  }

  Stream<int> watchPendingCount() {
    final count = outboxEntries.id.count();
    final query = selectOnly(outboxEntries)
      ..addColumns([count])
      ..where(outboxEntries.status.equals(statusPending));
    return query.watchSingle().map((row) => row.read(count) ?? 0);
  }

  /// Atomically claims a pending row. Two isolates may both have selected the
  /// same due row, but only one conditional update can win.
  Future<bool> claim(String id, int now) async =>
      await (update(outboxEntries)..where(
            (row) => row.id.equals(id) & row.status.equals(statusPending),
          ))
          .write(
            OutboxEntriesCompanion(
              status: const Value(statusInProgress),
              lastAttemptAt: Value(now),
            ),
          ) ==
      1;

  /// Deletes only while the row is still claimed by a drain.
  ///
  /// A completing drain must not remove a row that [OutboxRepository.enqueue]
  /// has since handed a fresh payload and put back to `pending` — the send that
  /// just succeeded carried the *old* body, and deleting would drop the new one
  /// with no operation left to carry it.
  Future<int> deleteIfInProgress(String id) =>
      (delete(outboxEntries)..where(
            (row) => row.id.equals(id) & row.status.equals(statusInProgress),
          ))
          .go();

  /// Status-scoped delete, so a cancel cannot race a drain.
  ///
  /// Checking the status and deleting in two statements leaves a window: the
  /// drainer interleaves at every `await`, so it can flip the row to
  /// `in_progress` between them, and the row would then be deleted while its
  /// request is on the wire — leaving the drainer's `markCompleted` with
  /// nothing to write to.
  Future<int> deletePending(String uploadType, String itemId) =>
      (delete(outboxEntries)..where(
            (row) =>
                row.uploadType.equals(uploadType) &
                row.itemId.equals(itemId) &
                row.status.equals(statusPending),
          ))
          .go();
  Future<int> deleteById(String id) =>
      (delete(outboxEntries)..where((row) => row.id.equals(id))).go();

  /// Resets rows stranded `in_progress` by a crash or a kill mid-drain.
  ///
  /// Kotlin calls this `recoverStuckOperations` and runs it at startup for the
  /// same reason: without it a killed drain leaves the operation permanently
  /// invisible to [due].
  Future<int> recoverStuck(int claimedBefore) =>
      (update(outboxEntries)..where(
            (row) =>
                row.status.equals(statusInProgress) &
                row.lastAttemptAt.isSmallerThanValue(claimedBefore),
          ))
          .write(const OutboxEntriesCompanion(status: Value(statusPending)));

  /// Drops rows that are finished with — completed, or given up on.
  Future<int> cleanup() => (delete(
    outboxEntries,
  )..where((row) => row.status.isIn([statusCompleted, statusAbandoned]))).go();
}

/// Port of `data/room/dao/SubmissionDao.kt` for the offline submissions list.
@DriftAccessor(tables: [Submissions, SubmissionAnswers, SubmissionQuestions])
class SubmissionDao extends DatabaseAccessor<AppDatabase>
    with _$SubmissionDaoMixin {
  SubmissionDao(super.db);

  Stream<List<SubmissionRow>> watchForUser(String userId) =>
      (select(submissions)
            ..where((row) => row.userId.equals(userId))
            ..orderBy([
              (row) => OrderingTerm(
                expression: row.lastUpdateTime,
                mode: OrderingMode.desc,
              ),
            ]))
          .watch();

  Future<void> upsertAll(
    List<SubmissionsCompanion> rows, {
    Map<String, List<SubmissionAnswersCompanion>> answers = const {},
    Map<String, List<SubmissionQuestionsCompanion>> questions = const {},
  }) async {
    await transaction(() async {
      await batch(
        (batch) => batch.insertAllOnConflictUpdate(submissions, rows),
      );
      for (final entry in answers.entries) {
        await (delete(
          submissionAnswers,
        )..where((row) => row.submissionId.equals(entry.key))).go();
        if (entry.value.isNotEmpty) {
          await batch(
            (batch) => batch.insertAll(submissionAnswers, entry.value),
          );
        }
      }
      for (final entry in questions.entries) {
        await (delete(
          submissionQuestions,
        )..where((row) => row.submissionId.equals(entry.key))).go();
        if (entry.value.isNotEmpty) {
          await batch(
            (batch) => batch.insertAll(submissionQuestions, entry.value),
          );
        }
      }
    });
  }

  Future<SubmissionRow?> getById(String id) =>
      (select(submissions)
            ..where((row) => row.id.equals(id) | row.couchId.equals(id))
            ..limit(1))
          .getSingleOrNull();

  Stream<SubmissionRow?> watchById(String id) =>
      (select(submissions)
            ..where((row) => row.id.equals(id) | row.couchId.equals(id))
            ..limit(1))
          .watchSingleOrNull();

  Future<SubmissionRow?> latestPendingByUserAndParent(
    String userId,
    String parentId,
  ) =>
      (select(submissions)
            ..where(
              (row) =>
                  row.userId.equals(userId) &
                  row.parentId.equals(parentId) &
                  row.type.equals('survey') &
                  row.status.equals('pending'),
            )
            ..orderBy([
              (row) => OrderingTerm(
                expression: row.lastUpdateTime,
                mode: OrderingMode.desc,
              ),
            ])
            ..limit(1))
          .getSingleOrNull();

  Stream<List<SubmissionAnswerRow>> watchAnswers(String submissionId) =>
      (select(submissionAnswers)
            ..where((row) => row.submissionId.equals(submissionId))
            ..orderBy([(row) => OrderingTerm(expression: row.id)]))
          .watch();

  /// The same rows, read once. Serializing for upload wants a value, not a
  /// subscription; `watchAnswers(...).first` would build and tear down a query
  /// stream to get it.
  Future<List<SubmissionAnswerRow>> answersFor(String submissionId) =>
      (select(submissionAnswers)
            ..where((row) => row.submissionId.equals(submissionId))
            ..orderBy([(row) => OrderingTerm(expression: row.id)]))
          .get();

  Stream<List<SubmissionQuestionRow>> watchQuestions(String submissionId) =>
      (select(submissionQuestions)
            ..where((row) => row.submissionId.equals(submissionId))
            ..orderBy([(row) => OrderingTerm(expression: row.position)]))
          .watch();

  Future<int> count() async {
    final count = submissions.id.count();
    final row = await (selectOnly(
      submissions,
    )..addColumns([count])).getSingle();
    return row.read(count) ?? 0;
  }

  Future<List<SubmissionRow>> byTeam(String teamId) =>
      (select(submissions)..where((row) => row.teamId.equals(teamId))).get();

  Future<List<SubmissionRow>> byUserWithoutTeam(String userId) =>
      (select(submissions)..where(
            (row) =>
                row.userId.equals(userId) &
                (row.teamId.isNull() | row.teamId.equals('')),
          ))
          .get();

  /// Port of `SubmissionDao.getExamSubmissionsByUser` — every `exam`-typed
  /// submission for a user. The course progress calc maps each onto an exam
  /// by stripping the `@user` suffix the Kotlin stores in `parentId`.
  Future<List<SubmissionRow>> getExamSubmissionsByUser(String? userId) =>
      (select(submissions)..where(
            (row) => row.userId.equals(userId ?? '') & row.type.equals('exam'),
          ))
          .get();

  /// Survey submissions for a user (for leaderboard survey-completion
  /// counting). Port of the survey half of `getExamSubmissionsByUser`.
  Future<List<SubmissionRow>> getSurveySubmissionsByUser(String userId) =>
      (select(submissions)..where(
            (row) => row.userId.equals(userId) & row.type.equals('survey'),
          ))
          .get();

  /// Port of `SubmissionDao.countByUserParentAndType` — whether the user has
  /// any submission (of any status) for the given parent and type. Used by
  /// the mandatory-survey check on course finish.
  Future<int> countByUserParentAndType(
    String userId,
    String parentId,
    String type,
  ) async {
    final count = submissions.id.count();
    final row =
        await (selectOnly(submissions)
              ..addColumns([count])
              ..where(
                submissions.userId.equals(userId) &
                    submissions.parentId.equals(parentId) &
                    submissions.type.equals(type),
              ))
            .getSingle();
    return row.read(count) ?? 0;
  }

  /// Port of `SubmissionDao.countCompletedByUserAndExamId`
  /// (`SubmissionDao.kt:24`), which `SubmissionsRepositoryImpl.isStepCompleted`
  /// reads to decide whether a course step's assessment has been answered:
  ///
  /// ```sql
  /// SELECT COUNT(*) FROM submissions WHERE userId IS :userId
  ///   AND parentId LIKE '%' || :examId || '%' AND status != 'pending'
  /// ```
  ///
  /// Three things about it are deliberate, and each was got wrong in a draft
  /// somewhere:
  ///
  ///  * **No `type` predicate.** Kotlin's count has none, so a *survey*
  ///    submission whose `parentId` contains the exam id satisfies it. The
  ///    port's caller picks its table by type and then filtered in Dart, which
  ///    is stricter than Kotlin; taking the query as written closes that
  ///    divergence rather than preserving it.
  ///  * **The `LIKE` pattern is not escaped.** Kotlin concatenates the raw exam
  ///    id, so `%` and `_` inside one are wildcards and the match is
  ///    ASCII-case-insensitive. Escaping would make the port *stricter* than
  ///    Kotlin — the opposite of the looseness a `contains` was chosen to
  ///    preserve. Unreachable in practice while `parentId` is minted from the
  ///    same `exam.id` through `examParentId`, which is why it is a comment and
  ///    not a defect.
  ///  * **A NULL `status` does not count.** `NOT (status = 'pending')` is NULL
  ///    for a NULL status, and `WHERE NULL` excludes the row — the same
  ///    three-valued outcome `status != 'pending'` gives. A Dart
  ///    `status != 'pending'` filter would have counted it.
  ///
  /// `userId IS :userId` is null-safe, so a null argument matches the rows with
  /// no user rather than nothing at all.
  Future<int> countCompletedByUserAndExamId(
    String? userId,
    String examId,
  ) async {
    final count = submissions.id.count();
    final userMatch = userId == null
        ? submissions.userId.isNull()
        : submissions.userId.equals(userId);
    final row =
        await (selectOnly(submissions)
              ..addColumns([count])
              ..where(
                userMatch &
                    submissions.parentId.like('%$examId%') &
                    submissions.status.equals('pending').not(),
              ))
            .getSingle();
    return row.read(count) ?? 0;
  }

  /// Port of `AnswerDao.getBySubmissionIds` — every answer for [submissionIds]
  /// in one read, so the progress calc totalises mistakes without N queries.
  Future<List<SubmissionAnswerRow>> answersForSubmissions(
    List<String> submissionIds,
  ) async {
    if (submissionIds.isEmpty) return const [];
    final rows = <SubmissionAnswerRow>[];
    for (final chunk in _chunked(submissionIds, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(submissionAnswers)
              ..where((row) => row.submissionId.isIn(chunk))
              ..orderBy([(row) => OrderingTerm(expression: row.id)]))
            .get(),
      );
    }
    return rows;
  }

  /// Port of `SubmissionDao.getUniquePendingSurveyCandidates` — the home
  /// dashboard's "you have N surveys to complete" check. Individual surveys
  /// only, matching the Kotlin `teamId IS NULL`.
  ///
  /// Adoption records use an empty status and describe the act of copying a
  /// survey, not an answer sheet assigned to the user. Only explicit pending
  /// submissions belong in the dashboard prompt, matching Kotlin.
  Future<List<SubmissionRow>> pendingSurveySubmissions(String userId) =>
      (select(submissions)
            ..where(
              (row) =>
                  row.userId.equals(userId) &
                  row.type.equals('survey') &
                  row.status.equals('pending') &
                  (row.teamId.isNull() | row.teamId.equals('')),
            )
            ..orderBy([(row) => OrderingTerm(expression: row.startTime)]))
          .get();

  /// Everything on this handset that still owes the server an upload —
  /// **not** just the signed-in learner's rows.
  ///
  /// Kotlin reaches the `submissions` endpoint through two upload configs, and
  /// neither is scoped to a user:
  ///
  ///  * `UploadConfigs.Submissions` -> `SubmissionDao.getPendingSubmissions()`
  ///    (`SubmissionDao.kt:41`), `status = 'complete' AND (isUpdated = 1 OR
  ///    _id IS NULL OR _id = '')`;
  ///  * `UploadConfigs.ExamResults` -> `getPendingExamResults()` (`:40`),
  ///    `type = 'exam' AND parentId IS NOT NULL AND userId IS NOT NULL AND
  ///    (_id IS NULL OR _id = '')`, with `filterGuests = true`
  ///    (`UploadConfigs.kt:249-250`, `UploadConfig.shouldFilter`:
  ///    `userId.startsWith("guest")`).
  ///
  /// The port had `userId = ? AND isUpdated = true`, which is the opposite
  /// scoping. **A shared handset is the normal deployment for this app**, and
  /// that is the flow that needs this: member A finishes a survey
  /// (`status = 'complete'`, `isUpdated = 1`, no `_id`) and signs out without
  /// syncing; member B signs in and syncs; arm B, being unscoped, sends A's
  /// sheet. Under the session scope it never left the device — and nothing in
  /// the port rescans, so never was literal.
  ///
  /// It is **not** the bulk-survey send that depends on this, though that is
  /// the easy claim to make: `createBulkSurveySubmissions` writes each member's
  /// sheet `pending` and not yet updated, and the only thing that flags one is
  /// that member answering it while signed in. On the answering turn scoped and
  /// unscoped agree.
  ///
  /// `isUpdated` stays the *primary* gate rather than Kotlin's
  /// `status = 'complete'`, because the port merges the two configs into one
  /// uploader: an exam is never `complete` (it finishes at
  /// `requires grading`), so filtering on that alone would strand every exam
  /// attempt. The exam arm it stands in for is `ExamResults`, whose guest
  /// filter is ported alongside it — a guest's attempt is answer-sheet
  /// practice, and Kotlin does not send it. Both operands are coalesced so a
  /// null `type` or `userId` cannot turn the test into SQL NULL and drop a row
  /// that is nobody's guest exam.
  ///
  /// **On top of it there is now one status exclusion, and it is a behaviour
  /// decision rather than a repair.** Having no status test at all was a
  /// defensible trade while this ran from four deliberate write-time sites;
  /// Phase 134 gave it a sweep on every sync, for every row on the handset,
  /// and named two rows Kotlin's sweeps never send that therefore went up
  /// systematically. **Only one of the two should stop.**
  ///
  /// The **team-adoption marker** does. `createSurveyAdoptionSubmission` writes
  /// `status: ''` with `isUpdated: true`; Kotlin's `getPendingSubmissions`
  /// wants `complete` and its `getPendingExamResults` wants `type = 'exam'`, so
  /// neither selects it. It is local bookkeeping that a team has taken a copy
  /// of a shared survey — no learner authored it and it carries no answers —
  /// and uploading it put an answerless `submissions` document into Planet's
  /// response set for every adoption.
  ///
  /// What makes withholding it *safe* rather than a data-sharing regression is
  /// the other half of Phase 138: with [SurveyDao.pendingAdoptedSurveys] and
  /// `AdoptedSurveysUploader` the clone itself now reaches the server carrying
  /// `teamId` and `sourceSurveyId`, so a second handset in the team learns of
  /// the adoption from the survey document — the route Kotlin has always used.
  /// Before that the marker was the port's only server-side trace of an
  /// adoption, and dropping it would have lost the team's adoption off-device.
  ///
  /// The **`pending` free-form draft does not**, and Phase 134's note that it
  /// should was checked and is wrong. `createDraft` is the submissions screen's
  /// New-submission button, and `submissions_screen.dart:172-194` runs
  /// `queuePending` *and* `drain` on the very next lines: the learner filled in
  /// a title and an answer and pressed Save, so the row is a deliberate
  /// submission, not a bookkeeping artefact. Excluding it would have made that
  /// button write to the device and nothing else — silently, since the sweep
  /// would return zero and the drain would find nothing to send. That it has no
  /// Kotlin writer makes the *document shape* port-only; it does not make the
  /// user's intent absent, and "Planet may refuse this shape" is a reason to
  /// fix the shape, not to strand the data. The divergence is recorded in
  /// `docs/kotlin-to-flutter-migration.md`. (Its `status: 'pending'` is
  /// arguably the wrong value for something the user just submitted, but that
  /// string also drives the screen's own Pending/finished split, so it is a UI
  /// change and not this predicate's to make.)
  ///
  /// The exclusion is written as an **exclusion** rather than an allow-list of
  /// `complete`/`requires grading`/`pending`. The two forms select identically
  /// today — every local writer that sets `isUpdated: true` sets one of those
  /// four statuses, and `upsertDocuments` writes `isUpdated: false` on every
  /// pulled row, so a server-authored status can never reach this predicate —
  /// but they fail differently. An unanticipated status under an allow-list is
  /// silently stranded on the handset, which is the failure this project keeps
  /// paying for; under the exclusion it is an extra document. `coalesce`
  /// because a null status is the empty one (`SurveysRepositoryImpl.kt:206`
  /// reads `it.status.orEmpty().isEmpty()`, and `json_utils.dart:19-22` turns
  /// the empty string back into null on a re-pull) — which is exactly the
  /// state a marker that has round-tripped is in.
  ///
  /// Two things it deliberately does **not** change. A `pending` exam attempt
  /// still does not upload, because `saveExamAnswer` only sets `isUpdated` at
  /// `requires grading` — Kotlin's `ExamResults` config has no status test and
  /// does send a half-finished attempt, a divergence recorded in
  /// `docs/kotlin-to-flutter-migration.md` rather than closed here. And a bulk
  /// `pending` sheet from `createBulkSurveySubmissions` was never selected
  /// either way (`getOrCreateSurveySubmission` writes `isUpdated: false`),
  /// which matches Kotlin, whose sweep wants `complete`.
  ///
  /// The one exclusion with no Kotlin counterpart is the **anonymous
  /// public-survey answer sheet**. `public_survey_screen` mints a
  /// `public_<millis>` owner for a respondent who has no account, a sentinel
  /// the Kotlin has no equivalent of (`PublicSurveyActivity` runs the ordinary
  /// exam fragment, so its sheet is authored by the signed-in user or nobody).
  /// That sheet is `status = 'complete'`, `isUpdated = 1` and belongs to the
  /// **public** endpoint, which is not a CouchDB insert; unscoping without this
  /// would hand it to the authenticated uploader on the next `queuePending` —
  /// and worse, `markUploaded` would then set `uploaded`, which is exactly what
  /// `PublicSurveyUploader.queue` refuses to queue on. The respondent's answers
  /// would go to the wrong database and the right one would decline them.
  Future<List<SubmissionRow>> pendingUploads() {
    // Coalesced: `type = 'exam' AND userId LIKE 'guest%'` is SQL NULL on a null
    // column and `NOT NULL` is NULL, which drops a row that is nobody's guest
    // exam rather than keeping it.
    Expression<String> owner($SubmissionsTable row) =>
        coalesce([row.userId, const Constant('')]);
    Expression<bool> isGuestExamAttempt($SubmissionsTable row) =>
        coalesce([row.type, const Constant('')]).equals('exam') &
        owner(row).like('guest%');
    // `status IS NOT NULL AND status = ''`, and the first clause is
    // load-bearing in the direction opposite to `isGuestExamAttempt`'s
    // `coalesce`. A **null** status is not a marker: `pendingUploads` was
    // status-blind until Phase 138, so two of this table's own tests build a
    // row with no status at all to probe the guest test, and the sync-in
    // stores null for a document that omits the key. Coalescing null to `''`
    // here — the reading `SurveysRepositoryImpl.kt:206` uses for the *adoption
    // guard*, where a round-tripped marker really has read back as null —
    // would silently strand every such row instead, which is the failure this
    // predicate's shape is chosen to avoid. It is safe to be strict because a
    // marker is only ever in this set as `createSurveyAdoptionSubmission`
    // wrote it, with the literal empty string: the re-pull that turns `''`
    // into null also writes `isUpdated: false`.
    //
    // `FALSE AND NULL` is `FALSE` in SQL, so a null status fails the
    // conjunction rather than poisoning it to NULL and dropping the row.
    Expression<bool> isATeamAdoptionMarker($SubmissionsTable row) =>
        row.status.isNotNull() & row.status.equals('');
    return (select(submissions)..where(
          (row) =>
              row.isUpdated.equals(true) &
              isATeamAdoptionMarker(row).not() &
              isGuestExamAttempt(row).not() &
              // The `_` is LIKE's single-character wildcard, not an escaped
              // literal, and nothing turns on that: the two patterns differ
              // only on the bare string `public`, and no id the port mints —
              // `org.couchdb.user:<name>`, `guest_<name>`, a millisecond
              // string — is either one.
              owner(row).like('public_%').not(),
        ))
        .get();
  }

  Future<int> markUploaded(String id, String couchId, String rev) =>
      (update(submissions)..where((row) => row.id.equals(id))).write(
        SubmissionsCompanion(
          couchId: Value(couchId),
          rev: Value(rev),
          uploaded: const Value(true),
          isUpdated: const Value(false),
        ),
      );

  /// Records that a public-survey answer sheet reached the public API.
  ///
  /// No `_id`/`_rev`: the public endpoint is not a CouchDB insert and its
  /// response carries no document handle, so there is nothing to store. What
  /// matters is clearing `isUpdated`, which is what keeps `PublicSurveyUploader`
  /// from queueing the same answer sheet twice.
  Future<int> markPublicSubmitted(String id) =>
      (update(submissions)..where((row) => row.id.equals(id))).write(
        const SubmissionsCompanion(
          uploaded: Value(true),
          isUpdated: Value(false),
        ),
      );

  /// Attaches the demographic profile collected after an attempt and marks it
  /// complete — the port of `markSubmissionComplete`. Clearing `uploaded`
  /// puts the row back in `pendingUploads` so the edit is actually sent.
  Future<int> markComplete(String id, String userJson) =>
      (update(submissions)..where((row) => row.id.equals(id))).write(
        SubmissionsCompanion(
          user: Value(userJson),
          status: const Value('complete'),
          uploaded: const Value(false),
          isUpdated: const Value(true),
          lastUpdateTime: Value(DateTime.now().millisecondsSinceEpoch),
        ),
      );

  // There is deliberately no `deleteNotIn` here. The Kotlin submissions walk
  // never prunes (`TransactionSyncManager.syncDb`'s `"submissions"` arm only
  // inserts), and a prune over this table is destructive rather than merely
  // unfaithful: the keep set is CouchDB `_id`s while a locally authored row is
  // keyed by a sha1, so `markUploaded` clearing `isUpdated` handed the
  // learner's own attempt — and its answers — to the next sync to delete. See
  // `SubmissionsRepository.sync`.
}

/// Port of `data/room/dao/MeetupDao.kt`.
@DriftAccessor(tables: [Meetups])
class MeetupDao extends DatabaseAccessor<AppDatabase> with _$MeetupDaoMixin {
  MeetupDao(super.db);

  Future<void> upsert(MeetupsCompanion row) =>
      into(meetups).insertOnConflictUpdate(row);

  Future<void> upsertAll(List<MeetupsCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((batch) => batch.insertAllOnConflictUpdate(meetups, rows));
  }

  Future<MeetupRow?> getById(String id) =>
      (select(meetups)..where((row) => row.id.equals(id))).getSingleOrNull();

  Future<MeetupRow?> getByMeetupId(String id) =>
      (select(meetups)
            ..where((row) => row.meetupId.equals(id))
            ..limit(1))
          .getSingleOrNull();

  Future<List<MeetupRow>> getByMeetupIds(List<String> ids) =>
      (select(meetups)..where((row) => row.meetupId.isIn(ids))).get();

  Stream<List<MeetupRow>> watchForTeam(String teamId) =>
      (select(meetups)
            ..where((row) => row.teamId.equals(teamId))
            ..orderBy([(row) => OrderingTerm(expression: row.startDate)]))
          .watch();

  Stream<List<MeetupRow>> watchAll() => (select(
    meetups,
  )..orderBy([(row) => OrderingTerm(expression: row.startDate)])).watch();

  /// Port of `MeetupDao.getByUserId`, including its `AND userId != ''` guard.
  ///
  /// The guard is load-bearing: `EventsRepository.toggleAttendance` writes an
  /// empty string when a user *leaves* a meetup, so without it a blank
  /// [userId] would select precisely the meetups the user has left and push
  /// them back onto their shelf.
  Future<List<MeetupRow>> meetupsOnShelf(String userId) =>
      (select(meetups)..where(
            (row) => row.userId.equals(userId) & row.userId.equals('').not(),
          ))
          .get();

  Future<List<MeetupRow>> pendingUploads() =>
      (select(meetups)..where((row) => row.updated.equals(true))).get();

  Future<int> count() async {
    final count = meetups.id.count();
    final row = await (selectOnly(meetups)..addColumns([count])).getSingle();
    return row.read(count) ?? 0;
  }

  /// Removes stale server rows without discarding locally edited meetups.
  /// Drops stale server rows, sparing anything locally edited.
  ///
  /// The set difference is computed in Dart rather than with `isNotIn`: that
  /// would bind one variable per synced id in one statement and fail past
  /// `SQLITE_MAX_VARIABLE_NUMBER`. A `NOT IN` also cannot be chunked directly,
  /// since each chunk would match rows the other chunks keep.
  Future<int> deleteNotIn(List<String> keepIds) async {
    final keep = keepIds.toSet();
    final rows = await (select(
      meetups,
    )..where((row) => row.updated.equals(false))).get();
    final stale = rows
        .map((row) => row.id)
        .where((id) => !keep.contains(id))
        .toList(growable: false);
    var deleted = 0;
    for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
      deleted += await (delete(meetups)..where((r) => r.id.isIn(chunk))).go();
    }
    return deleted;
  }

  Future<int> markUploaded(String id, String remoteId, String remoteRev) =>
      (update(meetups)..where((row) => row.id.equals(id))).write(
        MeetupsCompanion(
          meetupId: Value(remoteId),
          meetupIdRev: Value(remoteRev),
          updated: const Value(false),
        ),
      );
}

/// Port of the survey subset of `ExamDao` and `QuestionDao`.
@DriftAccessor(tables: [Surveys, SurveyQuestions])
class SurveyDao extends DatabaseAccessor<AppDatabase> with _$SurveyDaoMixin {
  SurveyDao(super.db);

  Stream<List<SurveyRow>> watchAll() =>
      (select(surveys)..orderBy([
            (row) => OrderingTerm(
              expression: row.createdDate,
              mode: OrderingMode.desc,
            ),
          ]))
          .watch();

  Future<SurveyRow?> getById(String id) =>
      (select(surveys)..where((row) => row.id.equals(id))).getSingleOrNull();

  /// Batch read for the dashboard's pending-survey dialog, chunked to stay
  /// under SQLite's variable cap.
  Future<List<SurveyRow>> getByIds(List<String> ids) async {
    final rows = <SurveyRow>[];
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(surveys)..where((row) => row.id.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  Future<List<SurveyRow>> allRows() => select(surveys).get();

  Future<SurveyRow?> adoptedTeamSurvey(String teamId, String sourceSurveyId) =>
      (select(surveys)
            ..where(
              (row) =>
                  row.teamId.equals(teamId) &
                  row.sourceSurveyId.equals(sourceSurveyId),
            )
            ..limit(1))
          .getSingleOrNull();

  /// Port of `ExamDao.getByCourseIdAndType(courseId, "survey")` — every
  /// course-attached survey. Used by the mandatory-survey check on course
  /// finish.
  Future<List<SurveyRow>> getByCourseId(String courseId) =>
      (select(surveys)..where((row) => row.courseId.equals(courseId))).get();

  /// Port of `ExamDao.getByStepIdAndType(stepId, "survey")` — every
  /// course-attached survey for a step. Used by the take-course step view's
  /// "Take survey" button.
  Future<List<SurveyRow>> getByStepId(String stepId) =>
      (select(surveys)..where((row) => row.stepId.equals(stepId))).get();

  Future<List<SurveyQuestionRow>> questionsFor(String surveyId) =>
      (select(surveyQuestions)
            ..where((row) => row.surveyId.equals(surveyId))
            ..orderBy([(row) => OrderingTerm(expression: row.position)]))
          .get();

  /// The survey half of [ExamDao.releaseStepJoinsForCourse]; see it for why
  /// positional step ids make this necessary.
  Future<void> releaseStepJoinsForCourse(
    String courseId,
    Set<String> keepIds,
  ) async {
    final stale =
        await (selectOnly(surveys)
              ..addColumns([surveys.id])
              ..where(
                surveys.courseId.equals(courseId) & surveys.stepId.isNotNull(),
              ))
            .map((row) => row.read(surveys.id)!)
            .get();
    final drop = stale.where((id) => !keepIds.contains(id)).toList();
    for (final chunk in _chunked(drop, _sqliteVariableChunk)) {
      await (update(surveys)..where((row) => row.id.isIn(chunk))).write(
        const SurveysCompanion(stepId: Value(null), courseId: Value(null)),
      );
    }
  }

  Future<void> upsertAll(
    List<SurveysCompanion> rows,
    Map<String, List<SurveyQuestionsCompanion>> questions,
  ) => transaction(() async {
    if (rows.isNotEmpty) {
      await batch((batch) => batch.insertAllOnConflictUpdate(surveys, rows));
    }
    for (final entry in questions.entries) {
      await (delete(
        surveyQuestions,
      )..where((row) => row.surveyId.equals(entry.key))).go();
      if (entry.value.isNotEmpty) {
        // See `ExamDao.upsertAll`. Survey question ids are namespaced by their
        // survey (`'$surveyId:$remoteId'`) so they cannot collide across
        // surveys, but the positional fallback can still collide within one —
        // and matching Kotlin's `@Upsert` costs nothing.
        await batch(
          (batch) =>
              batch.insertAllOnConflictUpdate(surveyQuestions, entry.value),
        );
      }
    }
  });

  /// Every locally minted team-adoption clone that has not reached the server.
  ///
  /// Port of `ExamDao.getPendingAdoptedSurveys()` (`ExamDao.kt:21`),
  /// `sourceSurveyId IS NOT NULL AND _rev IS NULL`, which feeds
  /// `UploadConfigs.AdoptedSurveys` (`UploadConfigs.kt:186-195`) to the `exams`
  /// endpoint. Kotlin carries no `type` clause because it has one `exams`
  /// table; the port's split puts the whole query here, and the `exams` table
  /// needs no counterpart because `adoptSurvey` writes only to [Surveys].
  ///
  /// **The port asks neither of Kotlin's clauses, and that is the point.**
  /// `sourceSurveyId` is not a local-authorship marker: the courses walk reads
  /// it straight off a server-embedded survey (`survey_mapper.dart:163-167`),
  /// so an adopted copy Planet itself published carries it too. And `rev IS
  /// NULL` cannot stand in for "nobody else has it" here the way it does in
  /// Kotlin, because the port's mappers use `getStringOrNull`: absent becomes
  /// NULL, which is true of every course-embedded and every public-API survey.
  /// Phase 138 found that predicate POSTing another team's private copy under
  /// the signed-in user's credentials, which is why [Surveys.needsSync] exists
  /// and why the body below is one clause on a flag with one writer.
  ///
  /// (An earlier revision of this comment ended "Only `rev IS NULL` separates
  /// this device minted it from the server sent it" — arguing for the very
  /// predicate the column beneath it was introduced to replace, directly above
  /// a body that does not use it.)
  Future<List<SurveyRow>> pendingAdoptedSurveys() =>
      (select(surveys)..where((row) => row.needsSync.equals(true))).get();

  /// Records that an adopted clone became a real CouchDB document.
  ///
  /// **This is at parity with Kotlin, and an earlier revision of this comment
  /// said the opposite.** `UploadConfigs.AdoptedSurveys` declares no
  /// `markUploaded` and no `responseHandler`, which is what the declaration
  /// looks like — but `UploadConfig` carries a **default** `persistUploaded`
  /// keyed on the model class (`UploadConfig.kt:46-56`,
  /// `StepExam::class -> UploadUpdateType.Exams`), which
  /// `UploadRepositoryImpl.markExamsUploaded` resolves to
  /// `exam._rev = result.remoteRev` (`UploadRepositoryImpl.kt:76`), and
  /// `runPipeline` calls it on every successful batch
  /// (`UploadCoordinator.kt:63-68`). So Kotlin records the rev, its row leaves
  /// `getPendingAdoptedSurveys()`, and there is no second POST. The `Meetups`
  /// contrast was wrong twice over: `Meetups` is a `RoomUploadConfig`, whose
  /// `persistUploaded` *is* only that lambda (`RoomUploadConfig.kt:42-45`) so
  /// it has no default to fall back on, and its
  /// `ResponseHandler.Custom("id", "rev")` is byte-identical to the `Standard`
  /// default (`UploadConfig.kt:24`).
  ///
  /// Recorded here because the port needs it for the same reason Kotlin does,
  /// plus one Kotlin does not have: [deleteNotIn] prunes this table where
  /// Kotlin never deletes from `exams` at all, so clearing
  /// [Surveys.needsSync] is what hands a published clone back to the walk that
  /// now names it. Kotlin's `_rev` write and this one are the same statement
  /// serving two purposes.
  Future<int> markUploaded(String id, String rev) =>
      (update(surveys)..where((row) => row.id.equals(id))).write(
        SurveysCompanion(rev: Value(rev), needsSync: const Value(false)),
      );

  /// See [MeetupDao.deleteNotIn] for why the difference is taken in Dart and
  /// the deletes are chunked. Rows with a `stepId` are spared for the reason
  /// [ExamDao.deleteNotIn] gives: since Phase 113 a course step's survey is
  /// written by the *courses* walk and cannot be in this walk's keep set.
  ///
  /// **An unpublished team-adoption clone is spared too.** It is minted on this
  /// handset by [SurveysRepository.adoptSurvey], carries no `stepId`
  /// (deliberately — see that method) and is in no document the server can
  /// name, so before this exemption the very next surveys sync deleted it and
  /// its question rows and orphaned every answer sheet its members had filled
  /// in. Kotlin cannot reach that outcome from either side: it uploads the
  /// clone (`UploadConfigs.AdoptedSurveys`) and its `exams` walk is
  /// insert-only — `bulkInsertExamsFromSync` upserts and nothing in the tree
  /// ever deletes from that table.
  ///
  /// The exemption is scoped to `needsSync` rather than to every clone, so it
  /// lapses the moment [markUploaded] runs — that call clears the flag as it
  /// records the rev: once the document exists, the walk that names it keeps it
  /// and a walk that stops naming it is reporting a deletion this prune should
  /// honour. (This said "scoped to `rev IS NULL`" until Phase 143; the body has
  /// been `stepId IS NULL AND needsSync = 0` since Phase 138 shipped the flag,
  /// so the file carried two descriptions of one predicate.)
  Future<int> deleteNotIn(List<String> ids) => transaction(() async {
    final keep = ids.toSet();
    final all =
        await (selectOnly(surveys)
              ..addColumns([surveys.id])
              ..where(
                surveys.stepId.isNull() & surveys.needsSync.equals(false),
              ))
            .map((row) => row.read(surveys.id)!)
            .get();
    final stale = all.where((id) => !keep.contains(id)).toList(growable: false);
    var deleted = 0;
    for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
      await (delete(
        surveyQuestions,
      )..where((row) => row.surveyId.isIn(chunk))).go();
      deleted += await (delete(
        surveys,
      )..where((row) => row.id.isIn(chunk))).go();
    }
    return deleted;
  });
}

/// Port of exam queries for graded course exams.
@DriftAccessor(tables: [Exams, ExamQuestions])
class ExamDao extends DatabaseAccessor<AppDatabase> with _$ExamDaoMixin {
  ExamDao(super.db);

  Future<ExamRow?> getById(String id) =>
      (select(exams)..where((row) => row.id.equals(id))).getSingleOrNull();

  Future<ExamRow?> getByStepId(String stepId) => (select(
    exams,
  )..where((row) => row.stepId.equals(stepId))).getSingleOrNull();

  Future<List<ExamRow>> getByCourseId(String courseId) =>
      (select(exams)..where((row) => row.courseId.equals(courseId))).get();

  Stream<List<ExamRow>> watchByCourseId(String courseId) =>
      (select(exams)..where((row) => row.courseId.equals(courseId))).watch();

  /// Port of `ExamDao.getByCourseIds` — exams attached to any of [courseIds].
  /// Chunked for the same `SQLITE_MAX_VARIABLE_NUMBER` reason as the rest.
  Future<List<ExamRow>> getByCourseIds(List<String> courseIds) async {
    final rows = <ExamRow>[];
    for (final chunk in _chunked(courseIds, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(exams)..where((row) => row.courseId.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  /// Port of `ExamDao.getByStepIds` — the per-step exam lookup the course
  /// progress detail uses to map each step to its exam.
  Future<List<ExamRow>> getByStepIds(List<String> stepIds) async {
    final rows = <ExamRow>[];
    for (final chunk in _chunked(stepIds, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(exams)..where((row) => row.stepId.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  /// Port of `QuestionDao.getByExamIds` — every question for [examIds] in one
  /// read, so the progress calc groups mistakes by exam without N queries.
  Future<List<ExamQuestionRow>> questionsForExams(List<String> examIds) async {
    final rows = <ExamQuestionRow>[];
    for (final chunk in _chunked(examIds, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(examQuestions)
              ..where((row) => row.examId.isIn(chunk))
              ..orderBy([(row) => OrderingTerm(expression: row.position)]))
            .get(),
      );
    }
    return rows;
  }

  Future<List<ExamQuestionRow>> questionsFor(String examId) =>
      (select(examQuestions)
            ..where((row) => row.examId.equals(examId))
            ..orderBy([(row) => OrderingTerm(expression: row.position)]))
          .get();

  Stream<List<ExamQuestionRow>> watchQuestionsFor(String examId) =>
      (select(examQuestions)
            ..where((row) => row.examId.equals(examId))
            ..orderBy([(row) => OrderingTerm(expression: row.position)]))
          .watch();

  Future<void> upsertExam(ExamsCompanion row) =>
      into(exams).insertOnConflictUpdate(row);

  Future<void> upsertQuestion(ExamQuestionsCompanion row) =>
      into(examQuestions).insertOnConflictUpdate(row);

  /// See [MeetupDao.deleteNotIn] for why the difference is taken in Dart and
  /// the deletes are chunked.
  ///
  /// **Rows with a `stepId` are spared.** This prune belongs to the `exams`
  /// database walk, but since Phase 113 it is no longer that walk's table
  /// alone: a course step's test is written by the *courses* walk, from the
  /// copy embedded in the course document, and that is the only walk that
  /// knows the step. Ids it minted itself (Kotlin's
  /// `ifBlank { "$courseId-$stepId-$examKey" }` fallback), or that name a
  /// document this satellite has not replicated, are structurally absent from
  /// the exams walk's keep set — so without this the courses area would write
  /// a step exam and the surveys area would delete it minutes later, in the
  /// same sync pass. Kotlin has no delete-except-ids on this table at all, so
  /// sparing is if anything the closer behaviour.
  ///
  /// A spared row is not immortal: [releaseStepJoinsForCourse] nulls the
  /// `stepId` of any row the course document no longer claims, which hands it
  /// back to this prune.
  Future<int> deleteNotIn(List<String> ids) => transaction(() async {
    final keep = ids.toSet();
    final all =
        await (selectOnly(exams)
              ..addColumns([exams.id])
              ..where(exams.stepId.isNull()))
            .map((row) => row.read(exams.id)!)
            .get();
    final stale = all.where((id) => !keep.contains(id)).toList(growable: false);
    var deleted = 0;
    for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
      await (delete(
        examQuestions,
      )..where((row) => row.examId.isIn(chunk))).go();
      deleted += await (delete(exams)..where((row) => row.id.isIn(chunk))).go();
    }
    return deleted;
  });

  /// Detaches every exam of [courseId] that [keepIds] does not name.
  ///
  /// The courses walk owns the `stepId`/`courseId` join and is the only writer
  /// that can retire it. It has to, because the port's step id is positional
  /// (`CourseMapper.stepIdFor`, `'$courseId:$index'`): deleting a step shifts
  /// every later step's id down one, so an exam left attached to `c1:0` would
  /// silently reappear under whatever step took that slot. Kotlin cannot reach
  /// that — its step ids are a hash of the step's own JSON — and its exams walk
  /// nulls a stale `stepId` wholesale on the next pull anyway, which the port
  /// deliberately no longer does.
  Future<void> releaseStepJoinsForCourse(
    String courseId,
    Set<String> keepIds,
  ) async {
    final stale =
        await (selectOnly(exams)
              ..addColumns([exams.id])
              ..where(
                exams.courseId.equals(courseId) & exams.stepId.isNotNull(),
              ))
            .map((row) => row.read(exams.id)!)
            .get();
    final drop = stale.where((id) => !keepIds.contains(id)).toList();
    for (final chunk in _chunked(drop, _sqliteVariableChunk)) {
      await (update(exams)..where((row) => row.id.isIn(chunk))).write(
        const ExamsCompanion(stepId: Value(null), courseId: Value(null)),
      );
    }
  }

  Future<void> upsertAll(
    List<ExamsCompanion> rows,
    Map<String, List<ExamQuestionsCompanion>> questions,
  ) => transaction(() async {
    if (rows.isNotEmpty) {
      await batch((batch) => batch.insertAllOnConflictUpdate(exams, rows));
    }
    for (final entry in questions.entries) {
      await (delete(
        examQuestions,
      )..where((row) => row.examId.equals(entry.key))).go();
      if (entry.value.isNotEmpty) {
        // `insertAllOnConflictUpdate`, not `insertAll`: `exam_questions.id` is
        // the question's own document id, which Planet only makes unique
        // *within* an exam, so two exams in one batch can carry the same one.
        // Kotlin's `questionDao.upsertAll` is `@Upsert` and lets the later row
        // win; a plain insert raises `UNIQUE constraint failed` instead, and
        // neither `CoursesRepository.sync` nor `SurveysRepository.sync` catches
        // it — so one such pair aborted the whole walk, skipped its remaining
        // pages and its cleanup, and failed the sync area.
        await batch(
          (batch) =>
              batch.insertAllOnConflictUpdate(examQuestions, entry.value),
        );
      }
    }
  });
}

/// Port of `data/room/dao/NewsDao.kt`.
@DriftAccessor(tables: [NewsEntries])
class NewsDao extends DatabaseAccessor<AppDatabase> with _$NewsDaoMixin {
  NewsDao(super.db);

  Future<void> upsert(NewsEntriesCompanion row) =>
      into(newsEntries).insertOnConflictUpdate(row);

  Future<void> upsertAll(List<NewsEntriesCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(newsEntries, rows));
  }

  /// Port of `NewsDao.getCommentsForParentFlow` — the inline comments on a
  /// team task or meetup. Comments are `News` rows with
  /// `messageType = 'comment'` and `replyTo = parentId`.
  Stream<List<NewsRow>> watchCommentsForParent(String parentId) =>
      (select(newsEntries)
            ..where(
              (row) =>
                  row.replyTo.equals(parentId) &
                  row.messageType.equals('comment'),
            )
            ..orderBy([(row) => OrderingTerm(expression: row.time)]))
          .watch();

  Future<List<NewsRow>> getCommentsForParent(String parentId) =>
      (select(newsEntries)
            ..where(
              (row) =>
                  row.replyTo.equals(parentId) &
                  row.messageType.equals('comment'),
            )
            ..orderBy([(row) => OrderingTerm(expression: row.time)]))
          .get();

  /// Port of `NewsDao.deleteById` — removes a comment or voice.
  Future<int> deleteById(String id) =>
      (delete(newsEntries)..where((row) => row.id.equals(id))).go();

  Future<NewsRow?> getById(String id) =>
      (select(newsEntries)..where((r) => r.id.equals(id))).getSingleOrNull();

  Future<NewsRow?> getByDocId(String docId) => (select(
    newsEntries,
  )..where((r) => r.docId.equals(docId))).getSingleOrNull();

  Future<List<NewsRow>> getByDocIds(List<String> docIds) async {
    final rows = <NewsRow>[];
    for (final chunk in _chunked(docIds, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(newsEntries)..where((r) => r.docId.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  Future<List<NewsRow>> getAll() => select(newsEntries).get();

  /// The rows in a time window, for the challenge dialog's voice tally.
  ///
  /// **Not** a port of a `NewsDao.getInTimeRange` — Kotlin has no such method,
  /// and three revisions of this comment said it did. The counterpart is
  /// `NewsDao.countDistinctCommunityVoiceDates(startTime, endTime)`
  /// (`NewsDao.kt:60-65`), which returns a *count of distinct local days* from
  /// one SQL statement:
  ///
  /// ```sql
  /// SELECT COUNT(*) FROM (SELECT DISTINCT
  ///   strftime('%Y-%m-%d', time / 1000, 'unixepoch', 'localtime')
  ///   FROM news WHERE time >= :startTime AND time <= :endTime
  ///   AND viewIn LIKE '%"section":"community"%')
  /// ```
  ///
  /// The port splits that in two: this query supplies the window, and
  /// `VoicesRepository.getCommunityVoiceDates` applies the community predicate
  /// and the day bucketing. So the result is **not** "community voices" —
  /// naming it that (as this comment also used to) invites a reader to assume
  /// a filter that is not here, and its caller is the only thing keeping a team
  /// post out of the tally.
  ///
  /// There is deliberately **no top-level predicate**. Kotlin's statement has
  /// exactly three: the window, the optional `userId`, and the `viewIn LIKE`.
  /// A `_isTopLevel(r)` conjunct used to sit here and it made the port
  /// *under*-count: `postReply` copies the parent's `viewIn` verbatim
  /// (`VoicesRepositoryImpl.kt:319`, and the port's `postReply` does the same),
  /// so in Kotlin a day on which the user only answered a community voice
  /// counts, and here it did not — the dialog told a learner they had not done
  /// something they had. See `challenge_tally_counts_replies_test.dart`.
  Future<List<NewsRow>> getInTimeRange(int startTime, int endTime) {
    return (select(newsEntries)..where(
          (r) =>
              r.time.isBiggerOrEqualValue(startTime) &
              r.time.isSmallerOrEqualValue(endTime),
        ))
        .get();
  }

  /// The per-user slice of [getInTimeRange], for the arm of the tally that
  /// passes a non-null `userId`.
  ///
  /// Counterpart of `NewsDao.countDistinctCommunityVoiceDatesForUser`
  /// (`NewsDao.kt:67-72`) — the same statement with `AND userId = :userId`.
  /// Everything [getInTimeRange]'s comment says about the missing community
  /// filter and the absent top-level predicate applies here too; the two
  /// statements are separate in both apps.
  Future<List<NewsRow>> getInTimeRangeForUser(
    int startTime,
    int endTime,
    String userId,
  ) {
    return (select(newsEntries)..where(
          (r) =>
              r.time.isBiggerOrEqualValue(startTime) &
              r.time.isSmallerOrEqualValue(endTime) &
              r.userId.equals(userId),
        ))
        .get();
  }

  /// Port of `NewsDao.getTeamChatViewableIds` — the team-visible post count per
  /// team, for the dashboard's chat badge.
  ///
  /// The Kotlin selects the raw `viewableId` column and counts duplicates in
  /// Dart-equivalent code (`chatCountsById[viewableId] + 1`); this groups in
  /// SQL instead and returns the same tallies. Teams with no posts are absent
  /// from the map, as they are absent from the Kotlin's map.
  Future<Map<String, int>> teamChatCounts(List<String> teamIds) async {
    if (teamIds.isEmpty) return const {};
    final counts = <String, int>{};
    final total = newsEntries.id.count();
    for (final chunk in _chunked(teamIds, _sqliteVariableChunk)) {
      final rows =
          await (selectOnly(newsEntries)
                ..addColumns([newsEntries.viewableId, total])
                ..where(
                  newsEntries.viewableBy.equals('teams') &
                      newsEntries.viewableId.isIn(chunk),
                )
                ..groupBy([newsEntries.viewableId]))
              .get();
      for (final row in rows) {
        final teamId = row.read(newsEntries.viewableId);
        if (teamId == null) continue;
        counts[teamId] = (counts[teamId] ?? 0) + (row.read(total) ?? 0);
      }
    }
    return counts;
  }

  /// `replyTo IS NULL OR replyTo = ''` — the Kotlin's definition of a top-level
  /// post, kept verbatim because a reply written by this app stores `''` while
  /// one synced from a server that omitted the field stores null.
  Expression<bool> _isTopLevel($NewsEntriesTable r) =>
      r.replyTo.isNull() | r.replyTo.equals('');

  /// Port of `getTopLevelMessagesFlow`, the community feed's source. Visibility
  /// is *not* filtered here: `VoicesRepositoryImpl` does it in memory because
  /// the rule reads inside the `viewIn` JSON.
  Stream<List<NewsRow>> watchTopLevelMessages() =>
      (select(newsEntries)
            ..where((r) => _isTopLevel(r) & r.docType.lower().equals('message'))
            ..orderBy([(r) => OrderingTerm.desc(r.time)]))
          .watch();

  Stream<List<NewsRow>> watchReplies(String newsId) =>
      (select(newsEntries)
            ..where((r) => r.replyTo.lower().equals(newsId.toLowerCase()))
            ..orderBy([(r) => OrderingTerm.desc(r.time)]))
          .watch();

  Future<List<NewsRow>> replies(String newsId) =>
      (select(newsEntries)
            ..where((r) => r.replyTo.lower().equals(newsId.toLowerCase()))
            ..orderBy([(r) => OrderingTerm.desc(r.time)]))
          .get();

  /// Port of `NewsDao.getByNewsId` (`NewsDao.kt:74`),
  /// `WHERE newsId = :chatId` — every `news` row carrying a chat's id.
  /// Case-**sensitive**: that query has no `COLLATE NOCASE`, unlike the
  /// `docType` ones below it. `VoicesRepository.isAlreadyShared` walked
  /// `getAll()` and filtered in Dart for want of this.
  Future<List<NewsRow>> getByNewsId(String chatId) =>
      (select(newsEntries)..where((r) => r.newsId.equals(chatId))).get();

  /// Port of `NewsDao.getPlanetMessages` (`NewsDao.kt:57-58`),
  /// `WHERE docType = 'message' COLLATE NOCASE AND createdOn = :planetCode
  /// COLLATE NOCASE`. Both comparisons are folded with `lower()`, the idiom the
  /// rest of this DAO uses for `COLLATE NOCASE` — equivalent for ASCII, which
  /// is all NOCASE itself folds.
  Future<List<NewsRow>> getPlanetMessages(String planetCode) =>
      (select(newsEntries)..where(
            (r) =>
                r.docType.lower().equals('message') &
                r.createdOn.lower().equals(planetCode.toLowerCase()),
          ))
          .get();

  /// Case-sensitive, unlike [replies] — `getDirectReplies` omits the
  /// `COLLATE NOCASE` that `getReplies` applies. Only the recursive delete
  /// walk uses it.
  Future<List<NewsRow>> directReplies(String newsId) =>
      (select(newsEntries)..where((r) => r.replyTo.equals(newsId))).get();

  Future<int> replyCount(String newsId) async {
    final count = newsEntries.id.count();
    final row =
        await (selectOnly(newsEntries)
              ..addColumns([count])
              ..where(newsEntries.replyTo.lower().equals(newsId.toLowerCase())))
            .getSingle();
    return row.read(count) ?? 0;
  }

  Future<void> deleteByIds(List<String> ids) async {
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      await (delete(newsEntries)..where((r) => r.id.isIn(chunk))).go();
    }
  }

  /// Drops server rows that no longer exist, leaving anything still local-only
  /// (`_id IS NULL`) alone — that is a post the outbox has not delivered yet.
  Future<void> deleteNotIn(List<String> docIds) async {
    if (docIds.isEmpty) {
      await (delete(newsEntries)..where((r) => r.docId.isNotNull())).go();
      return;
    }
    // `isNotIn` would bind one variable per synced id in a single statement,
    // which SQLite rejects past `SQLITE_MAX_VARIABLE_NUMBER` — 999 on older
    // builds — and a busy `news` database passes that easily. Unlike `isIn`,
    // a `NOT IN` cannot simply be chunked: each chunk would match rows the
    // other chunks keep. So the set difference is computed in Dart and the
    // deletion goes through the already-chunked [deleteByIds].
    final keep = docIds.toSet();
    final rows = await (select(
      newsEntries,
    )..where((r) => r.docId.isNotNull())).get();
    final stale = rows
        .where((row) => !keep.contains(row.docId))
        .map((row) => row.id)
        .toList(growable: false);
    await deleteByIds(stale);
  }

  Future<int> count() async {
    final count = newsEntries.id.count();
    final row = await (selectOnly(
      newsEntries,
    )..addColumns([count])).getSingle();
    return row.read(count) ?? 0;
  }
}

/// Port of `data/room/dao/ChatDao.kt`.
@DriftAccessor(tables: [ChatEntries])
class ChatDao extends DatabaseAccessor<AppDatabase> with _$ChatDaoMixin {
  ChatDao(super.db);

  /// Port of `ChatRepositoryImpl.getChatHistoryForUser`.
  /// Returns all chat history for a user, sorted by id descending.
  Future<List<ChatRow>> getByUser(String user) =>
      (select(chatEntries)
            ..where((c) => c.user.equals(user))
            ..orderBy([(c) => OrderingTerm.desc(c.id)]))
          .get();

  /// Port of `ChatDao.getByDocId`.
  Future<List<ChatRow>> getByDocId(String docId) =>
      (select(chatEntries)..where((c) => c.docId.equals(docId))).get();

  /// Port of `ChatDao.findByDocId`.
  Future<ChatRow?> findByDocId(String docId) =>
      (select(chatEntries)
            ..where((c) => c.docId.equals(docId))
            ..limit(1))
          .getSingleOrNull();

  Future<void> upsertAll(List<ChatEntriesCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(chatEntries, rows));
  }

  /// Update a chat row with new conversation data.
  Future<void> updateConversation(
    String docId,
    String conversationsJson,
    String updatedDate,
    String rev,
  ) => (update(chatEntries)..where((c) => c.docId.equals(docId))).write(
    ChatEntriesCompanion(
      conversations: Value(conversationsJson),
      updatedDate: Value(updatedDate),
      rev: Value(rev),
      lastUsed: Value(DateTime.now().millisecondsSinceEpoch),
    ),
  );

  /// Deletes all chat entries whose `id` is not in [keepIds].
  /// Removes cached conversations the server no longer has.
  ///
  /// Rows without a server revision are spared. A conversation is created by
  /// the AI endpoint, so a row with no `_rev` is one whose document this device
  /// never saw confirmed — deleting it would discard the only copy, and there
  /// is no chat uploader to put it back. The unguarded version of this deleted
  /// the whole table when `keepIds` was empty, which is what a server with an
  /// emptied `chat_history` looks like.
  Future<int> deleteNotIn(List<String> keepIds) async {
    final keep = keepIds.toSet();
    final rows = await (select(
      chatEntries,
    )..where((c) => c.rev.isNotNull() & c.rev.equals('').not())).get();
    final stale = rows
        .map((row) => row.id)
        .where((id) => !keep.contains(id))
        .toList(growable: false);
    var deleted = 0;
    for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
      deleted += await (delete(
        chatEntries,
      )..where((c) => c.id.isIn(chunk))).go();
    }
    return deleted;
  }

  /// Returns pending chats that need to be uploaded.
  Future<List<ChatRow>> getPending() =>
      (select(chatEntries)..where((c) => c.isUploaded.equals(false))).get();

  /// Marks a chat as uploaded with the server-assigned id and rev.
  Future<void> markUploaded(String id, String docId, String rev) =>
      (update(chatEntries)..where((c) => c.id.equals(id))).write(
        ChatEntriesCompanion(
          docId: Value(docId),
          rev: Value(rev),
          isUploaded: const Value(true),
        ),
      );
}

/// Port of `data/room/dao/FeedbackDao.kt`.
@DriftAccessor(tables: [FeedbackEntries])
class FeedbackDao extends DatabaseAccessor<AppDatabase>
    with _$FeedbackDaoMixin {
  FeedbackDao(super.db);

  /// Returns all feedback sorted by open time descending.
  Stream<List<FeedbackRow>> watchAllSorted() => (select(
    feedbackEntries,
  )..orderBy([(f) => OrderingTerm.desc(f.openTime)])).watch();

  /// Returns feedback for a specific owner.
  Stream<List<FeedbackRow>> watchByOwner(String? owner) =>
      (select(feedbackEntries)
            ..where((f) => f.owner.equals(owner ?? ''))
            ..orderBy([(f) => OrderingTerm.desc(f.openTime)]))
          .watch();

  /// Pending feedback that needs to be uploaded.
  Future<List<FeedbackRow>> getPending() =>
      (select(feedbackEntries)..where((f) => f.isUploaded.equals(false))).get();

  Future<FeedbackRow?> getById(String id) => (select(
    feedbackEntries,
  )..where((f) => f.id.equals(id))).getSingleOrNull();

  /// Batch read for the sync path, chunked to stay under SQLite's
  /// variable cap on a large planet.
  Future<List<FeedbackRow>> getByIds(List<String> ids) async {
    final rows = <FeedbackRow>[];
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(feedbackEntries)..where((f) => f.id.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  /// Close feedback by setting status to 'Closed'.
  Future<int> closeById(String id) =>
      (update(feedbackEntries)..where((f) => f.id.equals(id))).write(
        const FeedbackEntriesCompanion(status: Value('Closed')),
      );

  /// Marks the row uploaded and stores the revision CouchDB returned.
  ///
  /// Without the `_rev`, the next reply on this feedback would PUT against a
  /// missing revision and be rejected as a conflict.
  Future<int> markUploaded(String id, String rev) =>
      (update(feedbackEntries)..where((f) => f.id.equals(id))).write(
        FeedbackEntriesCompanion(
          isUploaded: const Value(true),
          rev: Value(rev),
        ),
      );

  /// Stale-row cleanup that spares anything not yet on the server.
  ///
  /// Difference computed in Dart and deleted in chunks: `NOT IN` cannot be
  /// split across chunks, since each chunk matches the rows the others keep.
  Future<int> deleteNotIn(List<String> keepIds) async {
    final keep = keepIds.toSet();
    final rows = await (select(
      feedbackEntries,
    )..where((f) => f.isUploaded.equals(true))).get();
    final stale = rows
        .map((row) => row.id)
        .where((id) => !keep.contains(id))
        .toList(growable: false);
    var deleted = 0;
    for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
      deleted += await (delete(
        feedbackEntries,
      )..where((f) => f.id.isIn(chunk))).go();
    }
    return deleted;
  }

  Future<void> upsert(FeedbackEntriesCompanion row) =>
      into(feedbackEntries).insertOnConflictUpdate(row);

  Future<void> upsertAll(List<FeedbackEntriesCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(feedbackEntries, rows));
  }

  /// Update a feedback row (e.g., adding a reply).
  Future<void> updateRow(FeedbackEntriesCompanion row) => (update(
    feedbackEntries,
  )..where((f) => f.id.equals(row.id.value))).write(row);
}

/// Port of `data/room/dao/HealthExaminationDao.kt`.
@DriftAccessor(tables: [HealthExaminations])
class HealthExaminationDao extends DatabaseAccessor<AppDatabase>
    with _$HealthExaminationDaoMixin {
  HealthExaminationDao(super.db);

  /// Get health examination by id or userId.
  ///
  /// `HealthExaminationDao.getByIdOrUserId` ends in `LIMIT 1`, and that is not
  /// decoration: where two rows match — a device that carries rows from an
  /// older build, or two profile rows for one patient — Kotlin picks one and
  /// keeps working while `getSingleOrNull` throws `Bad state: Too many
  /// elements` out of every caller, which `selectPatient` then swallows into a
  /// screen that silently stops updating.
  Future<HealthExaminationRow?> getByIdOrUserId(String id) =>
      (select(healthExaminations)
            ..where((h) => h.id.equals(id) | h.userId.equals(id))
            ..limit(1))
          .getSingleOrNull();

  /// Get health examination by id.
  Future<HealthExaminationRow?> getById(String id) => (select(
    healthExaminations,
  )..where((h) => h.id.equals(id))).getSingleOrNull();

  /// Get all updated examinations that need syncing.
  ///
  /// `WHERE isUpdated = 1 AND userId != ''`, and the second half is not
  /// `IS NOT NULL`: in SQL `userId != ''` is false for NULL as well, so Kotlin
  /// excludes a blank `userId` along with a missing one. That is what keeps
  /// `HealthRepository.serialize`'s omit-`_id` branch off the upload path —
  /// a POST with no `_id` makes CouchDB mint a fresh document every drain, so
  /// one examination would become a new record on every sync.
  Future<List<HealthExaminationRow>> getUpdated() =>
      (select(healthExaminations)..where(
            (h) =>
                h.isUpdated.equals(true) &
                h.userId.isNotNull() &
                h.userId.equals('').not(),
          ))
          .get();

  /// Get updated examinations for a specific user.
  Future<List<HealthExaminationRow>> getUpdatedForUser(String userId) =>
      (select(healthExaminations)
            ..where((h) => h.isUpdated.equals(true) & h.userId.equals(userId)))
          .get();

  /// Get examinations by profileId.
  Future<List<HealthExaminationRow>> getByProfileId(String profileId) =>
      (select(
        healthExaminations,
      )..where((h) => h.profileId.equals(profileId))).get();

  /// Get examinations for a user, ordered by date descending.
  Future<List<HealthExaminationRow>> getForUser(String userId) =>
      (select(healthExaminations)
            ..where((h) => h.userId.equals(userId))
            ..orderBy([(h) => OrderingTerm.desc(h.date)]))
          .get();

  /// Insert or update a health examination.
  Future<void> upsert(HealthExaminationsCompanion row) =>
      into(healthExaminations).insertOnConflictUpdate(row);

  /// Insert or update multiple health examinations.
  Future<void> upsertAll(List<HealthExaminationsCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(healthExaminations, rows));
  }

  /// Marks the examination uploaded, recording the server revision when there
  /// is one.
  ///
  /// **A null [rev] leaves the stored revision alone.** This method collapses
  /// Kotlin's two overloads into one, and the collapse has to keep the
  /// null-rev semantics of the one the app actually calls:
  /// `HealthRepositoryImpl.markHealthExaminationsUploaded` (`:67-69`) passes a
  /// `Map<String, String?>` to the `@Transaction`
  /// `HealthExaminationDao.markUploaded(idToRevMap)` (`:36-46`), which
  /// **partitions** the map and sends the null-rev ids to
  /// `UPDATE health_examinations SET isUpdated = 0 WHERE _id IN (:ids)` —
  /// clearing the dirty flag without touching `_rev`. Commit `1004e90` added
  /// that partition, and its test is
  /// `markUploaded_rowsWithoutRev_clearsIsUpdatedAndPreservesExistingRev`.
  /// Kotlin's *single-id* overload does still write `_rev = :rev`, but its only
  /// caller is the non-null half of that partition, so a null never reaches it
  /// — which is why this is parity rather than an improvement on the Kotlin.
  ///
  /// Writing `Value(rev)` here erased the revision instead, the pre-`1004e90`
  /// defect: `_rev` is the only thing that lets the next edit PUT rather than
  /// conflict, and `HealthRepository.cacheDocuments` skips a locally dirty row,
  /// so no pull would supply it again either — the record would 409 for the
  /// life of the install.
  ///
  /// **The hole is latent, not live, and an earlier revision of this comment
  /// claimed the opposite.** No port caller passes a null today:
  /// `HealthUploader`'s handler returns `NetworkError(null, …)` when the 2xx
  /// body's `rev` `is! String` (`health_uploader.dart:70-72`), so the call
  /// below is only reached with a promoted `String`. What this method being
  /// correct buys is the *precondition* for removing that early return, which
  /// is the one thing standing between the port and Kotlin's `has("id")` gate
  /// (`HealthRepositoryImpl.kt:118-120`): with the erase gone, an
  /// `id`-without-`rev` 2xx can clear `isUpdated` and keep `_rev`, instead of
  /// being classified `indeterminate` and abandoned.
  ///
  /// The blank case is deliberately *not* folded in with the null one: Kotlin
  /// partitions on `it.value == null`, so an empty string is written.
  ///
  /// [Value.absent] is the port's spelling of "leave the column alone" — the
  /// same pattern as [UserDao.markUploaded] and [AchievementDao.markUploaded].
  Future<int> markUploaded(String id, String? rev) =>
      (update(healthExaminations)..where((h) => h.id.equals(id))).write(
        HealthExaminationsCompanion(
          rev: rev == null ? const Value.absent() : Value(rev),
          isUpdated: const Value(false),
        ),
      );

  /// Update the userId for an examination.
  Future<int> updateUserId(String id, String userId) =>
      (update(healthExaminations)..where((h) => h.id.equals(id))).write(
        HealthExaminationsCompanion(userId: Value(userId)),
      );
}

/// Port of `data/room/dao/CourseProgressDao.kt`.
///
/// One row per (course, user, step). Rows authored on-device carry a
/// locally-minted `id` and a null `couchId`; rows pulled from the
/// `courses_progress` CouchDB database carry the server `_id`/`_rev`. The
/// uploader keys the pending set off `couchId IS NULL` to tell them apart, as
/// the Kotlin does.
@DriftAccessor(tables: [CourseProgress])
class CourseProgressDao extends DatabaseAccessor<AppDatabase>
    with _$CourseProgressDaoMixin {
  CourseProgressDao(super.db);

  /// The four user-scoped reads below are `userId IS :userId` in the Kotlin
  /// DAO (`CourseProgressDao.kt:10-20`), not `=`. The port had them as
  /// `equals(userId ?? '')`, which coerces a null argument into the empty
  /// string: it then matched rows whose `userId` is literally `''` and no row
  /// whose `userId` is NULL — where Kotlin matches exactly the NULL rows.
  /// `ProgressRepositoryImpl.saveCourseProgress` (`:234`) writes NULL for a
  /// null argument, so those rows exist.
  ///
  /// Not to be justified by the *synced* case, which is where this comment
  /// first pointed: a document omitting `userId` gives the port NULL
  /// (`CourseProgressMapper.fromDoc` passes `getStringOrNull`) but gives
  /// Kotlin `''` (`ProgressRepositoryImpl.kt:260` uses `JsonUtils.getString`),
  /// so on that input `IS` makes the port *diverge* until the mapper is
  /// aligned. Recorded in `PHASE_135_NOTES.md`.
  /// `equalsNullable` is drift's spelling of `IS`: `IS NULL` for a null
  /// argument, `=` otherwise.
  Future<List<CourseProgressRow>> getByUserAndCourseIds(
    String? userId,
    List<String> courseIds,
  ) async {
    if (courseIds.isEmpty) return const [];
    final rows = <CourseProgressRow>[];
    for (final chunk in _chunked(courseIds, _sqliteVariableChunk)) {
      final stmt = select(
        courseProgress,
      )..where((r) => r.userId.equalsNullable(userId) & r.courseId.isIn(chunk));
      rows.addAll(await stmt.get());
    }
    return rows;
  }

  Future<List<CourseProgressRow>> getByUserAndCourse(
    String? userId,
    String? courseId,
  ) =>
      (select(courseProgress)..where(
            (r) =>
                r.userId.equalsNullable(userId) &
                r.courseId.equalsNullable(courseId),
          ))
          .get();

  Future<List<CourseProgressRow>> getByUser(String? userId) => (select(
    courseProgress,
  )..where((r) => r.userId.equalsNullable(userId))).get();

  Future<CourseProgressRow?> findByCourseUserAndStep(
    String? courseId,
    String? userId,
    int stepNum,
  ) =>
      (select(courseProgress)
            ..where(
              (r) =>
                  r.courseId.equalsNullable(courseId) &
                  r.userId.equalsNullable(userId) &
                  r.stepNum.equals(stepNum),
            )
            ..limit(1))
          .getSingleOrNull();

  Future<List<CourseProgressRow>> getByIds(List<String> ids) async {
    final rows = <CourseProgressRow>[];
    for (final chunk in _chunked(ids, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(
          courseProgress,
        )..where((r) => r.id.isIn(chunk) | r.couchId.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  /// Kotlin matches on `(courseId, userId, stepNum)` triples to find the local
  /// row a synced document corresponds to. Chunked per dimension for the same
  /// `SQLITE_MAX_VARIABLE_NUMBER` reason as the rest of this file.
  Future<List<CourseProgressRow>> getByCourseUsersAndSteps(
    List<String> courseIds,
    List<String> userIds,
    List<int> stepNums,
  ) async {
    if (courseIds.isEmpty || userIds.isEmpty || stepNums.isEmpty) {
      return const [];
    }
    final rows = <CourseProgressRow>[];
    for (final courseChunk in _chunked(courseIds, _sqliteVariableChunk)) {
      for (final userChunk in _chunked(userIds, _sqliteVariableChunk)) {
        for (final stepChunk in _chunked(stepNums, _sqliteVariableChunk)) {
          rows.addAll(
            await (select(courseProgress)..where(
                  (r) =>
                      r.courseId.isIn(courseChunk) &
                      r.userId.isIn(userChunk) &
                      r.stepNum.isIn(stepChunk),
                ))
                .get(),
          );
        }
      }
    }
    return rows;
  }

  /// Rows authored here — `couchId IS NULL` — excluding guest accounts, which
  /// have no CouchDB user document and so cannot upload. Mirrors
  /// `CourseProgressDao.getPendingUploads`.
  Future<List<CourseProgressRow>> getPendingUploads() =>
      (select(courseProgress)..where(
            (r) =>
                r.couchId.isNull() &
                r.userId.isNotNull() &
                r.userId.like('guest%').not(),
          ))
          .get();

  Future<int> markUploaded(String localId, String remoteId, String rev) =>
      (update(courseProgress)..where((r) => r.id.equals(localId))).write(
        CourseProgressCompanion(couchId: Value(remoteId), rev: Value(rev)),
      );

  /// Port of `CourseProgressDao.updatePassedByCourseAndStep`
  /// (`CourseProgressDao.kt:34-35`) — the exam path's write, reached from
  /// `ProgressRepository.updateCourseProgress`.
  ///
  /// `AND userId IS :userId` is upstream `ed5609f` (fixes #16695). Without it
  /// the update hit **every** row on `(courseId, stepNum)` whatever its owner,
  /// so on a shared handset one learner finishing an exam rewrote another
  /// learner's flag for that step. The direction that actually bites is the
  /// clearing one: the value written here is `sub?.status == "graded"`, which
  /// no local writer can make true (see [ProgressRepository
  /// .updateCourseProgress]), so the unscoped statement wrote `passed = 0`
  /// over a peer's server-granted pass and un-completed their course.
  ///
  /// [userId] is nullable with `IS` semantics, exactly as the `@Query` has it:
  /// a null argument matches the rows whose `userId` is NULL rather than
  /// matching nothing.
  Future<int> updatePassedByCourseAndStep(
    String courseId,
    int stepNum,
    bool passed,
    String? userId,
  ) =>
      (update(courseProgress)..where(
            (r) =>
                r.courseId.equals(courseId) &
                r.stepNum.equals(stepNum) &
                r.userId.equalsNullable(userId),
          ))
          .write(CourseProgressCompanion(passed: Value(passed)));

  Future<void> upsert(CourseProgressCompanion row) =>
      into(courseProgress).insertOnConflictUpdate(row);

  Future<void> upsertAll(List<CourseProgressCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(courseProgress, rows));
  }
}

/// Port of `data/room/dao/CertificationDao.kt`. Read-only sync data; the only
/// query is the `LIKE` membership check behind `isCourseCertified`.
@DriftAccessor(tables: [Certifications])
class CertificationDao extends DatabaseAccessor<AppDatabase>
    with _$CertificationDaoMixin {
  CertificationDao(super.db);

  /// `courseIds` is a JSON array string; a substring match mirrors Realm's
  /// `contains("courseIds", id)` and the Kotlin's `LIKE '%id%'`.
  Future<int> countByCourseId(String courseId) async {
    final count = certifications.id.count();
    final row =
        await (selectOnly(certifications)
              ..addColumns([count])
              ..where(certifications.courseIds.like('%$courseId%')))
            .getSingle();
    return row.read(count) ?? 0;
  }

  Future<void> upsertAll(List<CertificationsCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(certifications, rows));
  }

  /// Drops stale certification rows the server no longer lists. Certifications
  /// are a pure server cache, so nothing here needs sparing.
  Future<int> deleteNotIn(List<String> keepIds) async {
    final keep = keepIds.toSet();
    final all =
        await (selectOnly(certifications)..addColumns([certifications.id]))
            .map((row) => row.read(certifications.id)!)
            .get();
    final stale = all.where((id) => !keep.contains(id)).toList(growable: false);
    var deleted = 0;
    for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
      deleted += await (delete(
        certifications,
      )..where((row) => row.id.isIn(chunk))).go();
    }
    return deleted;
  }
}

/// Port of `data/room/dao/OfflineActivityDao.kt`.
///
/// The `login` rows drive the dashboard's offline-login count, the activity
/// chart's monthly buckets, the profile's last-login and total-visits rows, and
/// the `login_activities` upload.
@DriftAccessor(tables: [OfflineActivities])
class OfflineActivityDao extends DatabaseAccessor<AppDatabase>
    with _$OfflineActivityDaoMixin {
  OfflineActivityDao(super.db);

  Future<void> insert(OfflineActivitiesCompanion row) =>
      into(offlineActivities).insertOnConflictUpdate(row);

  /// Port of `countByUserNameAndType`, the number behind the dashboard's
  /// "(n)" login count. Keyed on `userName`, not `userId`, exactly as the
  /// Kotlin's `getOfflineLoginCount(userName)` is.
  Future<int> countByUserNameAndType(String userName, String type) async {
    final count = offlineActivities.id.count();
    final row =
        await (selectOnly(offlineActivities)
              ..addColumns([count])
              ..where(
                offlineActivities.userName.equals(userName) &
                    offlineActivities.type.equals(type),
              ))
            .getSingle();
    return row.read(count) ?? 0;
  }

  /// Port of `observeByUserNameAndType` — the flow `ActivitiesFragment`
  /// collects to build its chart.
  Stream<List<OfflineActivityRow>> watchByUserNameAndType(
    String userName,
    String type,
  ) =>
      (select(offlineActivities)
            ..where(
              (row) => row.userName.equals(userName) & row.type.equals(type),
            )
            ..orderBy([(row) => OrderingTerm.asc(row.loginTime)]))
          .watch();

  /// Port of `getLatestByType`, used by `logLogout` to stamp the session that
  /// is ending. Ordered by `loginTime` descending — the Kotlin orders by the
  /// same column.
  Future<OfflineActivityRow?> latestByType(String type) =>
      (select(offlineActivities)
            ..where((row) => row.type.equals(type))
            ..orderBy([(row) => OrderingTerm.desc(row.loginTime)])
            ..limit(1))
          .getSingleOrNull();

  /// Port of `updateLogoutTime`.
  Future<void> updateLogoutTime(String id, int logoutTime) =>
      (update(offlineActivities)..where((row) => row.id.equals(id))).write(
        OfflineActivitiesCompanion(logoutTime: Value(logoutTime)),
      );

  /// Port of `countByUserIdAndType`, behind the profile's "Total visits" row
  /// and the member-details sheet. Keyed on `userId`, where the dashboard's
  /// count keys on `userName` — the Kotlin really does use both columns for
  /// what reads as the same number, so both queries are kept.
  Future<int> countByUserIdAndType(String userId, String type) async {
    final count = offlineActivities.id.count();
    final row =
        await (selectOnly(offlineActivities)
              ..addColumns([count])
              ..where(
                offlineActivities.userId.equals(userId) &
                    offlineActivities.type.equals(type),
              ))
            .getSingle();
    return row.read(count) ?? 0;
  }

  /// Port of `getPendingLoginUploads` — `_rev IS NULL AND type = 'login'`.
  ///
  /// The guest exclusion is `getUnuploadedLoginActivities`', which drops rows
  /// with a null or `guest`-prefixed `userId` after the query rather than in
  /// it; expressed in SQL here as `CourseProgressDao.getPendingUploads`
  /// already does. A guest has no CouchDB user document, so the server has
  /// nothing to attribute the session to.
  Future<List<OfflineActivityRow>> pendingLoginUploads() =>
      (select(offlineActivities)..where(
            (row) =>
                row.rev.isNull() &
                row.type.equals(ActivityTypes.login) &
                row.userId.isNotNull() &
                row.userId.like('guest%').not(),
          ))
          .get();

  /// Port of `getGlobalLastVisit` — `MAX(loginTime)` over the whole table, with
  /// no user predicate. That is what `UserProfileViewModel` shows as "Last
  /// login", so on a shared handset it is the newest login by anyone.
  Future<int?> globalLastVisit() async {
    final max = offlineActivities.loginTime.max();
    final row = await (selectOnly(
      offlineActivities,
    )..addColumns([max])).getSingle();
    return row.read(max);
  }

  /// Port of `getLastVisit(userName)`.
  Future<int?> lastVisit(String userName) async {
    final max = offlineActivities.loginTime.max();
    final row =
        await (selectOnly(offlineActivities)
              ..addColumns([max])
              ..where(offlineActivities.userName.equals(userName)))
            .getSingle();
    return row.read(max);
  }

  /// Port of `getByRemoteIds` — the rows a synced page may already have local
  /// counterparts for, keyed by the server `_id`. Chunked for the same
  /// `SQLITE_MAX_VARIABLE_NUMBER` reason as every other `IN` in this file.
  Future<List<OfflineActivityRow>> getByCouchIds(List<String> couchIds) async {
    if (couchIds.isEmpty) return const [];
    final rows = <OfflineActivityRow>[];
    for (final chunk in _chunked(couchIds, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(
          offlineActivities,
        )..where((row) => row.couchId.isIn(chunk))).get(),
      );
    }
    return rows;
  }

  /// Port of `getByLoginTimesAndUserNames`, the fallback match for a row this
  /// device authored offline and is now seeing come back from the server with
  /// an `_id` it does not know yet.
  Future<List<OfflineActivityRow>> getByLoginTimesAndUserNames(
    List<int> loginTimes,
    List<String> userNames,
  ) async {
    if (loginTimes.isEmpty || userNames.isEmpty) return const [];
    final rows = <OfflineActivityRow>[];
    for (final times in _chunked(loginTimes, _sqliteVariableChunk)) {
      for (final names in _chunked(userNames, _sqliteVariableChunk)) {
        rows.addAll(
          await (select(offlineActivities)..where(
                (row) => row.loginTime.isIn(times) & row.userName.isIn(names),
              ))
              .get(),
        );
      }
    }
    return rows;
  }

  Future<void> upsertAll(List<OfflineActivitiesCompanion> rows) async {
    if (rows.isEmpty) return;
    await batch((b) => b.insertAllOnConflictUpdate(offlineActivities, rows));
  }

  /// The `_id`/`_rev` half of `markActivitiesUploaded`. Returns the number of
  /// rows written so the uploader can tell a vanished row from a stored one.
  Future<int> markUploaded(String localId, String remoteId, String rev) =>
      (update(offlineActivities)..where((row) => row.id.equals(localId))).write(
        OfflineActivitiesCompanion(couchId: Value(remoteId), rev: Value(rev)),
      );
}

/// The `type` values shared by the activity tables, from
/// `UserSessionManager`'s companion object plus the `sync` literal
/// `recordSyncActivity` writes.
abstract final class ActivityTypes {
  /// `UserSessionManager.KEY_LOGIN`.
  static const String login = 'login';

  /// `UserSessionManager.KEY_RESOURCE_OPEN` — note the value is `visit`, not
  /// `open`, and `CourseActivity` rows use the same literal.
  static const String visit = 'visit';

  /// `UserSessionManager.KEY_RESOURCE_DOWNLOAD`.
  static const String download = 'download';

  /// Written by `recordSyncActivity`; routed to `admin_activities` rather than
  /// `resource_activities`.
  static const String sync = 'sync';
}

/// Port of `data/room/dao/ResourceActivityDao.kt`.
@DriftAccessor(tables: [ResourceActivities])
class ResourceActivityDao extends DatabaseAccessor<AppDatabase>
    with _$ResourceActivityDaoMixin {
  ResourceActivityDao(super.db);

  Future<void> insert(ResourceActivitiesCompanion row) =>
      into(resourceActivities).insertOnConflictUpdate(row);

  /// Port of `getPendingUploads` — everything unsent that is *not* a sync row.
  /// The two predicates partition the table, so no row is posted twice.
  Future<List<ResourceActivityRow>> pendingUploads() =>
      (select(resourceActivities)..where(
            (row) =>
                row.rev.isNull() & row.type.equals(ActivityTypes.sync).not(),
          ))
          .get();

  /// Port of `getPendingSyncUploads` — the `sync` rows, bound for
  /// `admin_activities`.
  Future<List<ResourceActivityRow>> pendingSyncUploads() =>
      (select(resourceActivities)..where(
            (row) => row.rev.isNull() & row.type.equals(ActivityTypes.sync),
          ))
          .get();

  /// Port of `getByUserAndType`, which `getMostOpenedResource` groups in Dart.
  Future<List<ResourceActivityRow>> byUserAndType(
    String userName,
    String type,
  ) => (select(
    resourceActivities,
  )..where((row) => row.user.equals(userName) & row.type.equals(type))).get();

  /// Port of `countByUserAndType`.
  Future<int> countByUserAndType(String userName, String type) async {
    final count = resourceActivities.id.count();
    final row =
        await (selectOnly(resourceActivities)
              ..addColumns([count])
              ..where(
                resourceActivities.user.equals(userName) &
                    resourceActivities.type.equals(type),
              ))
            .getSingle();
    return row.read(count) ?? 0;
  }

  /// Port of `observeByUserAndType`, the flow `ResourcesRepositoryImpl` exposes
  /// for the `resource_opened` count.
  Stream<List<ResourceActivityRow>> watchByUserAndType(
    String userName,
    String type,
  ) => (select(
    resourceActivities,
  )..where((row) => row.user.equals(userName) & row.type.equals(type))).watch();

  Future<int> markUploaded(String localId, String remoteId, String rev) =>
      (update(
        resourceActivities,
      )..where((row) => row.id.equals(localId))).write(
        ResourceActivitiesCompanion(couchId: Value(remoteId), rev: Value(rev)),
      );
}

/// Port of `data/room/dao/CourseActivityDao.kt`.
@DriftAccessor(tables: [CourseActivities])
class CourseActivityDao extends DatabaseAccessor<AppDatabase>
    with _$CourseActivityDaoMixin {
  CourseActivityDao(super.db);

  Future<void> insert(CourseActivitiesCompanion row) =>
      into(courseActivities).insertOnConflictUpdate(row);

  /// Port of `getPendingUploads`. The `type != 'sync'` filter is the Kotlin's;
  /// nothing writes a `sync` course activity, but the predicate is kept so the
  /// two DAOs agree.
  Future<List<CourseActivityRow>> pendingUploads() =>
      (select(courseActivities)..where(
            (row) =>
                row.rev.isNull() & row.type.equals(ActivityTypes.sync).not(),
          ))
          .get();

  Future<List<CourseActivityRow>> byUserAndCourse(
    String userName,
    String courseId,
  ) =>
      (select(courseActivities)..where(
            (row) => row.user.equals(userName) & row.courseId.equals(courseId),
          ))
          .get();

  Future<int> markUploaded(String localId, String remoteId, String rev) =>
      (update(courseActivities)..where((row) => row.id.equals(localId))).write(
        CourseActivitiesCompanion(couchId: Value(remoteId), rev: Value(rev)),
      );
}

/// Port of `data/room/dao/TeamNotificationDao.kt` — the per-team "seen" chat
/// watermark behind the dashboard's team chat badge.
@DriftAccessor(tables: [TeamNotifications])
class TeamNotificationDao extends DatabaseAccessor<AppDatabase>
    with _$TeamNotificationDaoMixin {
  TeamNotificationDao(super.db);

  /// Port of `findByParentAndType`.
  Future<TeamNotificationRow?> findByParentAndType(
    String parentId,
    String type,
  ) =>
      (select(teamNotifications)..where(
            (row) => row.parentId.equals(parentId) & row.type.equals(type),
          ))
          .getSingleOrNull();

  /// Port of `getByTypeAndParentIds`, the single query
  /// `getTeamNotifications` runs for the whole team list.
  Future<List<TeamNotificationRow>> byTypeAndParentIds(
    String type,
    List<String> parentIds,
  ) async {
    if (parentIds.isEmpty) return const [];
    final rows = <TeamNotificationRow>[];
    for (final chunk in _chunked(parentIds, _sqliteVariableChunk)) {
      rows.addAll(
        await (select(
              teamNotifications,
            )..where((row) => row.type.equals(type) & row.parentId.isIn(chunk)))
            .get(),
      );
    }
    return rows;
  }

  Future<void> upsert(TeamNotificationsCompanion row) =>
      into(teamNotifications).insertOnConflictUpdate(row);
}

@DriftAccessor(tables: [DownloadQueueEntries])
class DownloadQueueDao extends DatabaseAccessor<AppDatabase>
    with _$DownloadQueueDaoMixin {
  DownloadQueueDao(super.db);

  Future<void> enqueue(String resourceId, {int? createdAt}) =>
      into(downloadQueueEntries).insertOnConflictUpdate(
        DownloadQueueEntriesCompanion.insert(
          resourceId: resourceId,
          createdAt: createdAt ?? DateTime.now().millisecondsSinceEpoch,
        ),
      );

  Future<List<DownloadQueueRow>> pending() => (select(
    downloadQueueEntries,
  )..orderBy([(row) => OrderingTerm.asc(row.createdAt)])).get();

  Future<int> complete(String resourceId) => (delete(
    downloadQueueEntries,
  )..where((row) => row.resourceId.equals(resourceId))).go();
}

@DriftAccessor(tables: [SubmitPhotosTable])
class SubmitPhotosDao extends DatabaseAccessor<AppDatabase>
    with _$SubmitPhotosDaoMixin {
  SubmitPhotosDao(super.db);

  /// Rows whose document has not been acknowledged by CouchDB.
  ///
  /// Port of `SubmitPhotosDao.getUnuploaded` — `PhotoUploader` selects these,
  /// serializes each, and POSTs it to the `submissions` database.
  Future<List<SubmitPhotosRow>> unuploaded() => (select(
    submitPhotosTable,
  )..where((row) => row.uploaded.equals(false))).get();

  Future<SubmitPhotosRow?> getById(String id) => (select(
    submitPhotosTable,
  )..where((row) => row.id.equals(id))).getSingleOrNull();

  Future<List<SubmitPhotosRow>> getByIds(Iterable<String> ids) {
    final list = ids.where((id) => id.isNotEmpty).toList();
    if (list.isEmpty) return Future.value(const <SubmitPhotosRow>[]);
    return (select(submitPhotosTable)..where((row) => row.id.isIn(list))).get();
  }

  Future<void> insert(SubmitPhotosTableCompanion row) =>
      into(submitPhotosTable).insertOnConflictUpdate(row);

  /// Records that the document POST landed, mirroring
  /// `SubmitPhotosDao.markUploaded`. Returns the rows changed so a caller can
  /// tell a stale outbox replay from a live write.
  Future<int> markUploaded(String id, String couchId, String rev) =>
      (update(submitPhotosTable)..where((row) => row.id.equals(id))).write(
        SubmitPhotosTableCompanion(
          couchId: Value(couchId),
          rev: Value(rev),
          uploaded: const Value(true),
        ),
      );
}

/// Port of `data/room/dao/TeamLogDao.kt`.
@DriftAccessor(tables: [TeamLogTable])
class TeamLogDao extends DatabaseAccessor<AppDatabase> with _$TeamLogDaoMixin {
  TeamLogDao(super.db);

  Future<void> insert(TeamLogTableCompanion row) =>
      into(teamLogTable).insertOnConflictUpdate(row);

  /// Rows whose `teamVisit` has not yet reached `team_activities`.
  ///
  /// Port of `TeamsRepositoryImpl.getPendingTeamLogUploads` — the uploader
  /// selects these, serializes each, and POSTs it to `team_activities`.
  Future<List<TeamLogRow>> pendingUploads() =>
      (select(teamLogTable)..where((row) => row.uploaded.equals(false))).get();

  /// Records that the document POST landed, mirroring
  /// `TeamLogDao.markUploaded`.
  Future<int> markUploaded(String id, String couchId, String rev) =>
      (update(teamLogTable)..where((row) => row.id.equals(id))).write(
        TeamLogTableCompanion(
          couchId: Value(couchId),
          rev: Value(rev),
          uploaded: const Value(true),
        ),
      );

  /// Port of `TeamLogDao.getTeamVisitsForUsers` — the per-team visit count
  /// `MembersDetailFragment` shows. `type = 'teamVisit'` filters out any
  /// other team-log rows, exactly as the Kotlin query does.
  Future<List<TeamLogRow>> teamVisitsForUsers(
    String teamId,
    List<String> userNames,
  ) {
    if (userNames.isEmpty) return Future.value(const <TeamLogRow>[]);
    final query = select(teamLogTable)
      ..where(
        (row) =>
            row.type.equals('teamVisit') &
            row.teamId.equals(teamId) &
            row.user.isIn(userNames),
      );
    return query.get();
  }

  /// Port of `TeamLogDao.getLastVisit` — the most recent `teamVisit` time for
  /// a user in a team, or null if they have never visited. The Kotlin uses
  /// `SELECT MAX(time) ...`; drift's `selectOnly` + `Expression.max()` is the
  /// same query shape.
  Future<int?> lastTeamVisit(String? userName, String? teamId) async {
    final query = selectOnly(teamLogTable, distinct: false)
      ..addColumns([teamLogTable.time.max()])
      ..where(teamLogTable.type.equals('teamVisit'));
    if (userName != null) {
      query.where(teamLogTable.user.equals(userName));
    }
    if (teamId != null) {
      query.where(teamLogTable.teamId.equals(teamId));
    }
    final row = await query
        .map((r) => r.read(teamLogTable.time.max()))
        .getSingleOrNull();
    return row;
  }

  /// Port of `TeamLogDao.getRecentTeamVisits` + `getRecentVisitCounts` — the
  /// per-team count of `teamVisit` rows newer than [cutoffMillis] (the Kotlin
  /// uses a 30-day window). Drives the catalog's membership-rank / visit-count
  /// sort, mirroring `TeamsRepositoryImpl.getRecentVisitCounts`.
  Future<Map<String, int>> recentVisitCounts(
    List<String> teamIds,
    int cutoffMillis,
  ) async {
    final valid = teamIds.where((id) => id.isNotEmpty).toSet().toList();
    if (valid.isEmpty) return const {};
    final query = selectOnly(teamLogTable, distinct: false)
      ..addColumns([teamLogTable.teamId])
      ..where(
        teamLogTable.type.equals('teamVisit') &
            teamLogTable.time.isBiggerOrEqualValue(cutoffMillis) &
            teamLogTable.teamId.isIn(valid),
      );
    final rows = await query.map((r) => r.read(teamLogTable.teamId)).get();
    final counts = <String, int>{};
    for (final teamId in rows) {
      if (teamId == null) continue;
      counts[teamId] = (counts[teamId] ?? 0) + 1;
    }
    return counts;
  }
}

/// Port of `data/room/dao/SearchActivityDao.kt`.
@DriftAccessor(tables: [SearchActivities])
class SearchActivityDao extends DatabaseAccessor<AppDatabase>
    with _$SearchActivityDaoMixin {
  SearchActivityDao(super.db);

  Future<void> insert(SearchActivitiesCompanion row) =>
      into(searchActivities).insertOnConflictUpdate(row);

  /// Rows whose filtered search has not yet reached `search_activities`.
  ///
  /// Port of `SearchActivityDao.getPendingUploads` — the uploader selects
  /// these, serializes each, and POSTs it to `search_activities`.
  Future<List<SearchActivityRow>> pendingUploads() =>
      (select(searchActivities)..where((row) => row.rev.equals(''))).get();

  /// Records that the document POST landed, mirroring
  /// `SearchActivityDao.markUploaded`.
  Future<int> markUploaded(String id, String couchId, String rev) =>
      (update(searchActivities)..where((row) => row.id.equals(id))).write(
        SearchActivitiesCompanion(couchId: Value(couchId), rev: Value(rev)),
      );
}

/// Port of `data/room/dao/TagDao.kt`.
@DriftAccessor(tables: [Tags])
class TagDao extends DatabaseAccessor<AppDatabase> with _$TagDaoMixin {
  TagDao(super.db);

  /// Parent tags for a collections dialog: named, not attached, optionally
  /// scoped to a `db`. Port of `TagDao.getParentTags`.
  Future<List<Tag>> parentTags(String? db) {
    final query = select(tags)
      ..where((row) => row.isAttached.equals(false))
      ..where((row) => row.name.isNotValue(''));
    if (db != null) {
      query.where((row) => row.db.equals(db));
    }
    return query.get();
  }

  Future<List<Tag>> allTags() => select(tags).get();

  Future<void> upsertAll(List<TagsCompanion> rows) =>
      batch((b) => b.insertAllOnConflictUpdate(tags, rows));

  /// Link rows for a set of resource/course ids. Port of
  /// `TagDao.getByDbAndLinkIds`.
  Future<List<Tag>> byDbAndLinkIds(String db, List<String> linkIds) => (select(
    tags,
  )..where((row) => row.db.equals(db) & row.linkId.isIn(linkIds))).get();

  Future<List<Tag>> byIds(List<String> ids) =>
      (select(tags)..where((row) => row.id.isIn(ids))).get();

  Future<List<Tag>> byNames(List<String> names) =>
      (select(tags)..where((row) => row.name.isIn(names))).get();

  /// Link rows whose `tagId` matches a tag. Port of
  /// `TagDao.getByDbAndTagIds`.
  Future<List<Tag>> byDbAndTagIds(String db, List<String> tagIds) => (select(
    tags,
  )..where((row) => row.db.equals(db) & row.tagId.isIn(tagIds))).get();

  /// Drops cache rows the server no longer lists. Pure cache, so the cleanup
  /// runs unconditionally on a complete walk, like `MyLibraryDao.deleteNotIn`.
  Future<void> deleteNotIn(List<String> ids) {
    if (ids.isEmpty) return delete(tags).go();
    return (delete(tags)..where((row) => row.id.isNotIn(ids))).go();
  }
}

/// Port of `AchievementDao.kt` — the ledger for one user's achievements and
/// references.
@DriftAccessor(tables: [Achievements])
class AchievementDao extends DatabaseAccessor<AppDatabase>
    with _$AchievementDaoMixin {
  AchievementDao(super.db);

  Future<AchievementRow?> getById(String id) =>
      (select(achievements)..where((a) => a.id.equals(id))).getSingleOrNull();

  /// Port of the `updateAchievement` upsert — Room's `insertOrUpdate`.
  Future<void> upsert(AchievementsCompanion row) =>
      into(achievements).insertOnConflictUpdate(row);

  /// Port of `AchievementDao.getPendingUploads` — non-guest rows the update
  /// flagged unsynced (`_id NOT LIKE 'guest%' AND isUpdated = 1`; `uploaded`
  /// is the port's inverted name for `isUpdated`).
  Future<List<AchievementRow>> pendingUploads() => (select(
    achievements,
  )..where((a) => a.id.like('guest%').not() & a.uploaded.equals(false))).get();

  /// Port of `AchievementDao.markUploaded` — stamps the server rev and
  /// clears the pending flag once the PUT succeeds (`_rev = COALESCE(:rev,
  /// _rev)`, so a null rev keeps the old one).
  Future<int> markUploaded(String id, String couchId, String? rev) =>
      (update(achievements)..where((a) => a.id.equals(id))).write(
        AchievementsCompanion(
          couchId: Value(couchId),
          rev: rev != null ? Value(rev) : const Value.absent(),
          uploaded: const Value(true),
        ),
      );

  /// The local rows a page of `achievements` documents is about, so the walk
  /// can tell a ledger the user has edited but not yet uploaded from one it is
  /// merely refreshing.
  Future<List<AchievementRow>> getByIds(List<String> ids) async {
    if (ids.isEmpty) return const [];
    return (select(achievements)..where((a) => a.id.isIn(ids))).get();
  }

  /// Port of the `bulkInsertAchievementsFromSync` upsert — a server document
  /// adopts its CouchDB id.
  Future<void> insertDocs(List<AchievementsCompanion> rows) =>
      batch((b) => b.insertAllOnConflictUpdate(achievements, rows));

  /// Stamps the server `_rev` onto a ledger the user has edited but not
  /// uploaded, leaving the edit and its pending flag alone. An UPDATE rather
  /// than an upsert, for the same reason [OutboxDao.patch] is.
  Future<int> recordServerRev(String id, String rev) =>
      (update(achievements)..where((a) => a.id.equals(id))).write(
        AchievementsCompanion(couchId: Value(id), rev: Value(rev)),
      );
}

/// Port of `data/room/dao/UserChallengeActionsDao.kt`. One row per challenge
/// action the user completes (currently only `"sync"` when a full sync
/// finishes).
@DriftAccessor(tables: [UserChallengeActions])
class UserChallengeActionDao extends DatabaseAccessor<AppDatabase>
    with _$UserChallengeActionDaoMixin {
  UserChallengeActionDao(super.db);

  /// Port of `insert(action)` — a single REPLACE insert.
  Future<void> insert(UserChallengeActionsCompanion row) =>
      into(userChallengeActions).insertOnConflictUpdate(row);

  /// Port of `countByUserAndType(userId, actionType)` — used by
  /// `hasUserCompletedSync` to check whether a `"sync"` action exists.
  Future<int> countByUserAndType(String userId, String actionType) async {
    final countExpr = userChallengeActions.id.count();
    final row =
        await (selectOnly(userChallengeActions)
              ..addColumns([countExpr])
              ..where(
                userChallengeActions.userId.equals(userId) &
                    userChallengeActions.actionType.equals(actionType),
              ))
            .getSingle();
    return row.read(countExpr) ?? 0;
  }
}
