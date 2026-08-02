import 'dart:io';

import 'package:drift/drift.dart';
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
  tables: [Users, MyLibraryTable, Courses, CourseSteps, RemovedLogs],
  daos: [UserDao, MyLibraryDao, CourseDao, RemovedLogDao],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  /// The on-device database, under the app's documents directory.
  AppDatabase.open() : super(_openConnection());

  /// An isolated in-memory database, for tests.
  AppDatabase.memory() : super(NativeDatabase.memory());

  @override
  int get schemaVersion => 3;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onUpgrade: (m, from, to) async {
      // Drop-and-resync: local rows are a cache of CouchDB, so a schema
      // change recreates the tables and the next sync refills them.
      for (final table in allTables) {
        await m.deleteTable(table.actualTableName);
      }
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
