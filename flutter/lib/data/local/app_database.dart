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
  int get schemaVersion => 44;

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
  /// carry no local writes at all and are dropped with the rest.
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
  };

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onUpgrade: (m, from, to) async {
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
    },
  );

  /// Adds [column] to [table] unless the running database already has it.
  ///
  /// A preserved table is skipped by the drop-and-recreate loop, so a column
  /// added to one only exists on installs created after the change. Re-running
  /// `ALTER TABLE ADD COLUMN` on a database that already has it is an error,
  /// hence the check — an upgrade that spans several versions would otherwise
  /// abort partway.
  Future<void> _addColumnIfMissing(
    Migrator m,
    TableInfo<Table, dynamic> table,
    GeneratedColumn<Object> column,
  ) async {
    final existing = await customSelect(
      'PRAGMA table_info(${table.actualTableName})',
    ).get();
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

  Stream<List<TeamTaskRow>> watchForTeam(String teamId) =>
      (select(teamTasks)
            ..where((t) => t.teamId.equals(teamId) & t.status.equals('active'))
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
  Future<void> markUploaded(String id, String docId, String rev) =>
      (update(teamTasks)..where((t) => t.id.equals(id))).write(
        TeamTasksCompanion(
          docId: Value(docId),
          rev: Value(rev),
          isUpdated: const Value(false),
        ),
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
      statement.where((r) => r.userId.like('%"$shelfUserId"%'));
    } else {
      // `getPublic` / `getPublicNotUserPattern` — the catalog.
      statement.where((r) => r.isPrivate.equals(false));
      if (shelfUserId != null && shelfUserId.isNotEmpty) {
        statement.where(
          (r) => r.userId.isNull() | r.userId.like('%"$shelfUserId"%').not(),
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
  )..where((r) => r.userId.like('%"$userId"%'))).get();

  /// Port of `ResourcesRepositoryImpl.removeDeletedResources` — drops local rows
  /// the server no longer lists.
  ///
  /// The stale set is computed in Dart and deleted in chunks rather than
  /// binding every kept id into one `NOT IN`: SQLite caps bound variables at
  /// `SQLITE_MAX_VARIABLE_NUMBER` (999 on older builds), and a library easily
  /// exceeds that, which would fail the whole sync.
  Future<int> deleteNotIn(List<String> keepIds) async {
    if (keepIds.isEmpty) return delete(myLibraryTable).go();

    return transaction(() async {
      final localIds =
          await (selectOnly(myLibraryTable)..addColumns([myLibraryTable.id]))
              .map((row) => row.read(myLibraryTable.id)!)
              .get();

      final keep = keepIds.toSet();
      final stale = localIds.where((id) => !keep.contains(id)).toList();

      var deleted = 0;
      for (final chunk in _chunked(stale, _sqliteVariableChunk)) {
        deleted += await (delete(
          myLibraryTable,
        )..where((r) => r.id.isIn(chunk))).go();
      }
      return deleted;
    });
  }

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
@DriftAccessor(tables: [Courses, CourseSteps])
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
      }
    });
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
      statement.where((c) => c.courseTitleNormal.like('%$trimmed%'));
    }
    if (shelfUserId != null && shelfUserId.isNotEmpty) {
      statement.where((c) => c.userId.like('%"$shelfUserId"%'));
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
      (select(courses)..where((c) => c.userId.like('%"$userId"%'))).get();

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

  /// Port of `NotificationDao.markAsRead(ids, createdAt)`. Sets `isRead`,
  /// stamps a fresh `createdAt` (Kotlin's `Date`), and flags server-originated
  /// rows for read-state upload via `needsSync`.
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

  Future<List<SubmissionRow>> pendingUploads(String userId) =>
      (select(submissions)..where(
            (row) => row.userId.equals(userId) & row.isUpdated.equals(true),
          ))
          .get();

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

  /// Removes stale server-cache rows without binding an unbounded `NOT IN`.
  Future<int> deleteNotIn(List<String> keepIds) async {
    return transaction(() async {
      final localIds =
          await (selectOnly(submissions)
                ..addColumns([submissions.id])
                ..where(submissions.isUpdated.equals(false)))
              .map((row) => row.read(submissions.id)!)
              .get();
      final keep = keepIds.toSet();
      var deleted = 0;
      for (final chunk in _chunked(
        localIds.where((id) => !keep.contains(id)).toList(),
        _sqliteVariableChunk,
      )) {
        await (delete(
          submissionAnswers,
        )..where((row) => row.submissionId.isIn(chunk))).go();
        await (delete(
          submissionQuestions,
        )..where((row) => row.submissionId.isIn(chunk))).go();
        deleted += await (delete(
          submissions,
        )..where((row) => row.id.isIn(chunk))).go();
      }
      return deleted;
    });
  }
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
        await batch((batch) => batch.insertAll(surveyQuestions, entry.value));
      }
    }
  });

  /// See [MeetupDao.deleteNotIn] for why the difference is taken in Dart and
  /// the deletes are chunked.
  Future<int> deleteNotIn(List<String> ids) => transaction(() async {
    final keep = ids.toSet();
    final all = await (selectOnly(
      surveys,
    )..addColumns([surveys.id])).map((row) => row.read(surveys.id)!).get();
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
  /// the deletes are chunked. Exams are a pure server cache — every row is
  /// restorable by the next sync — so nothing here needs sparing.
  Future<int> deleteNotIn(List<String> ids) => transaction(() async {
    final keep = ids.toSet();
    final all = await (selectOnly(
      exams,
    )..addColumns([exams.id])).map((row) => row.read(exams.id)!).get();
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
        await batch((batch) => batch.insertAll(examQuestions, entry.value));
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

  /// Port of `NewsDao.getInTimeRange(startTime, endTime)` — all top-level
  /// community voices posted within a time window. Used by the challenge
  /// dialog's `getCommunityVoiceDates` to count unique posting days.
  Future<List<NewsRow>> getInTimeRange(int startTime, int endTime) {
    return (select(newsEntries)..where(
          (r) =>
              _isTopLevel(r) &
              r.time.isBiggerOrEqualValue(startTime) &
              r.time.isSmallerOrEqualValue(endTime),
        ))
        .get();
  }

  /// Port of `NewsDao.getInTimeRangeForUser(startTime, endTime, userId)` —
  /// the per-user slice the challenge dialog passes a non-null `userId`.
  Future<List<NewsRow>> getInTimeRangeForUser(
    int startTime,
    int endTime,
    String userId,
  ) {
    return (select(newsEntries)..where(
          (r) =>
              _isTopLevel(r) &
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

  /// Mark examination as uploaded with the server revision.
  Future<int> markUploaded(String id, String? rev) =>
      (update(healthExaminations)..where((h) => h.id.equals(id))).write(
        HealthExaminationsCompanion(
          rev: Value(rev),
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

  Future<List<CourseProgressRow>> getByUserAndCourseIds(
    String? userId,
    List<String> courseIds,
  ) async {
    if (courseIds.isEmpty) return const [];
    final rows = <CourseProgressRow>[];
    for (final chunk in _chunked(courseIds, _sqliteVariableChunk)) {
      final stmt = select(courseProgress)
        ..where((r) => r.userId.equals(userId ?? '') & r.courseId.isIn(chunk));
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
                r.userId.equals(userId ?? '') &
                r.courseId.equals(courseId ?? ''),
          ))
          .get();

  Future<List<CourseProgressRow>> getByUser(String? userId) => (select(
    courseProgress,
  )..where((r) => r.userId.equals(userId ?? ''))).get();

  Future<CourseProgressRow?> findByCourseUserAndStep(
    String? courseId,
    String? userId,
    int stepNum,
  ) =>
      (select(courseProgress)
            ..where(
              (r) =>
                  r.courseId.equals(courseId ?? '') &
                  r.userId.equals(userId ?? '') &
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

  /// Port of `CourseProgressDao.updatePassedByCourseAndStep`. Used by the exam
  /// path (`CoursesRepository.updateCourseProgress`) to flip the `passed` flag
  /// for every user who reached a step, since an exam result is not per-user.
  Future<int> updatePassedByCourseAndStep(
    String courseId,
    int stepNum,
    bool passed,
  ) =>
      (update(courseProgress)..where(
            (r) => r.courseId.equals(courseId) & r.stepNum.equals(stepNum),
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

  /// Port of the `bulkInsertAchievementsFromSync` upsert — a server document
  /// adopts its CouchDB id.
  Future<void> insertDocs(List<AchievementsCompanion> rows) =>
      batch((b) => b.insertAllOnConflictUpdate(achievements, rows));
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
