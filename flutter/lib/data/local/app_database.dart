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
  tables: [Users, MyLibraryTable, Courses, CourseSteps],
  daos: [UserDao, MyLibraryDao, CourseDao],
)
class AppDatabase extends _$AppDatabase {
  AppDatabase(super.e);

  /// The on-device database, under the app's documents directory.
  AppDatabase.open() : super(_openConnection());

  /// An isolated in-memory database, for tests.
  AppDatabase.memory() : super(NativeDatabase.memory());

  @override
  int get schemaVersion => 2;

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
  Future<UserRow?> getByName(String name) =>
      (select(users)..where((u) => u.name.equals(name))).getSingleOrNull();

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

  Future<List<MyLibraryRow>> getByIds(List<String> ids) =>
      (select(myLibraryTable)..where((r) => r.id.isIn(ids))).get();

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

  /// Port of `ResourcesRepositoryImpl.removeDeletedResources` — drops local rows
  /// the server no longer lists.
  Future<int> deleteNotIn(List<String> keepIds) {
    if (keepIds.isEmpty) return delete(myLibraryTable).go();
    return (delete(myLibraryTable)..where((r) => r.id.isNotIn(keepIds))).go();
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
    });
  }

  Future<CourseRow?> getById(String courseId) =>
      (select(courses)..where((c) => c.id.equals(courseId))).getSingleOrNull();

  Future<List<CourseRow>> getByIds(List<String> ids) =>
      (select(courses)..where((c) => c.id.isIn(ids))).get();

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

  Future<List<String>> _distinct(GeneratedColumn<String> column) async {
    final query = selectOnly(courses, distinct: true)
      ..addColumns([column])
      ..where(column.isNotNull() & column.equals('').not())
      ..orderBy([OrderingTerm(expression: column)]);
    final rows = await query.get();
    return rows
        .map((row) => row.read(column))
        .whereType<String>()
        .toList(growable: false);
  }

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
  Future<void> deleteNotIn(List<String> keepIds) async {
    await transaction(() async {
      if (keepIds.isEmpty) {
        await delete(courseSteps).go();
        await delete(courses).go();
        return;
      }
      await (delete(
        courseSteps,
      )..where((s) => s.courseId.isNotIn(keepIds))).go();
      await (delete(courses)..where((c) => c.id.isNotIn(keepIds))).go();
    });
  }
}
