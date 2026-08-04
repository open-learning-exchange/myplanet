import 'dart:io';

import 'package:drift/drift.dart';
import 'package:meta/meta.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

// Imported for the generated part file, which constructs the type converters
// declared on the table columns.
import 'converters.dart';
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
    NewsEntries,
    Teams,
    TeamTasks,
    ChatEntries,
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
    ChatDao,
  ],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  /// The on-device database, under the app's documents directory.
  AppDatabase.open() : super(_openConnection());

  /// An isolated in-memory database, for tests.
  AppDatabase.memory() : super(NativeDatabase.memory());

  @override
  int get schemaVersion => 22;

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
    // Not local-authority in principle — CouchDB holds every chat — but the
    // policy's premise is that *the next sync refills the table*, and nothing
    // calls `insertChatHistoryFromSync`. Until a chat sync exists, dropping
    // this discards the user's entire history with no way to get it back, so
    // the practical test ("can a sync restore this?") governs, not the
    // nominal one. A failed continuation also stores the query here with an
    // empty response, which never reaches the server at all.
    'chat_history',
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
    },
  );

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

  Future<UserRow?> getById(String id) =>
      (select(users)..where((u) => u.id.equals(id))).getSingleOrNull();

  /// Port of `UserRepositoryImpl.getSavedUsers` — the account picker on the
  /// login screen.
  Future<List<UserRow>> getSavedUsers() =>
      (select(users)..where((u) => u.isArchived.equals(false))).get();

  Future<int> count() async {
    final query = selectOnly(users)..addColumns([users.id.count()]);
    final row = await query.getSingle();
    return row.read(users.id.count()) ?? 0;
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
  /// [query] matches against [MyLibraryTable.titleNormal], the diacritic-folded
  /// column, so "cafe" finds "Café".
  Stream<List<MyLibraryRow>> watchResources({
    String? query,
    String? shelfUserId,
  }) {
    final statement = select(myLibraryTable);

    final trimmed = query?.trim().toLowerCase();
    if (trimmed != null && trimmed.isNotEmpty) {
      statement.where((r) => r.titleNormal.like('%$trimmed%'));
    }
    if (shelfUserId != null && shelfUserId.isNotEmpty) {
      // Mirrors the Room `LIKE` containment check against the JSON userId column.
      statement.where((r) => r.userId.like('%"$shelfUserId"%'));
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
  }) {
    final query = select(notifications)
      ..where((notification) => notification.userId.equals(userId));
    if (filter == 'read') {
      query.where((notification) => notification.isRead.equals(true));
    } else if (filter == 'unread') {
      query.where((notification) => notification.isRead.equals(false));
    }
    query.orderBy([
      (notification) => OrderingTerm(
        expression: notification.createdAt,
        mode: OrderingMode.desc,
      ),
    ]);
    return query.watch();
  }

  Stream<int> watchUnreadCount(String userId) {
    final count = notifications.id.count();
    final query = selectOnly(notifications)
      ..addColumns([count])
      ..where(
        notifications.userId.equals(userId) &
            notifications.isRead.equals(false),
      );
    return query.watchSingle().map((row) => row.read(count) ?? 0);
  }

  Future<void> upsert(NotificationsCompanion notification) =>
      into(notifications).insertOnConflictUpdate(notification);

  Future<NotificationRow?> getById(String id) => (select(
    notifications,
  )..where((row) => row.id.equals(id))).getSingleOrNull();

  Future<int> markAsRead(Iterable<String> ids) {
    final values = ids.toList(growable: false);
    if (values.isEmpty) return Future.value(0);
    return (update(notifications)..where((row) => row.id.isIn(values))).write(
      const NotificationsCompanion(isRead: Value(true)),
    );
  }

  Future<int> markAllAsRead(String userId) {
    return (update(
          notifications,
        )..where((row) => row.userId.equals(userId) & row.isRead.equals(false)))
        .write(const NotificationsCompanion(isRead: Value(true)));
  }

  Future<int> deleteById(String id) =>
      (delete(notifications)..where((row) => row.id.equals(id))).go();
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

  Future<int> setStatus(String id, String status) =>
      (update(outboxEntries)..where((row) => row.id.equals(id))).write(
        OutboxEntriesCompanion(status: Value(status)),
      );

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
  Future<int> recoverStuck() =>
      (update(outboxEntries)
            ..where((row) => row.status.equals(statusInProgress)))
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
}
