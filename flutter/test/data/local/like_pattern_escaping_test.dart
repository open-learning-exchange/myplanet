import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// The five shelf-membership `LIKE` predicates, one test per call site.
///
/// `my_library.user_id` and `courses.user_id` hold a JSON list, and shelf
/// membership is the substring `"<userId>"` inside it. The Kotlin builds that
/// pattern through `ResourcesRepositoryImpl.userIdPattern` (`:64-70`) and
/// `CoursesRepositoryImpl.userIdPattern`, which escape `\`, `%` and `_` and
/// pair the pattern with `ESCAPE '\'`. Interpolating the raw id instead leaves
/// LIKE's own metacharacters live:
///
/// * `_` matches **any single character**, so `u_1` also matches `ux1` — one
///   learner's shelf leaking another's rows, and the catalog arm losing rows
///   it should show;
/// * `%` matches any run of characters, so `a%` matches almost everything.
///
/// The ids used here are deliberately minimal (`u_1` / `ux1`) so that a match
/// can only come from the wildcard: they differ at exactly the escaped
/// character and nowhere else.
void main() {
  late AppDatabase db;

  /// The escaped user, and a *different* user whose id the unescaped pattern
  /// `%"u_1"%` matches because `_` is a wildcard.
  const escaped = 'u_1';
  const collider = 'ux1';

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<void> resource(
    String id, {
    required List<String> userId,
    bool isPrivate = false,
  }) => db.myLibraryDao.upsertAll([
    MyLibraryTableCompanion.insert(
      id: id,
      userId: Value(userId),
      isPrivate: Value(isPrivate),
      titleNormal: Value(id),
    ),
  ]);

  Future<void> course(String id, {required List<String> userId}) =>
      db.courseDao.upsertAll([
        CoursesCompanion.insert(
          id: id,
          userId: Value(userId),
          courseTitle: Value(id),
          courseTitleNormal: Value(id),
        ),
      ], const []);

  Future<void> seedResources() async {
    await resource('mine', userId: const [escaped]);
    await resource('theirs', userId: const [collider]);
  }

  Future<void> seedCourses() async {
    await course('mine', userId: const [escaped]);
    await course('theirs', userId: const [collider]);
  }

  group('MyLibraryDao', () {
    test('watchResources shelf arm does not leak a colliding shelf', () async {
      await seedResources();

      final rows = await db.myLibraryDao
          .watchResources(shelfUserId: escaped, myLibrary: true)
          .first;

      expect(rows.map((r) => r.id), ['mine']);
    });

    test(
      'watchResources catalog arm excludes only the signed-in user',
      () async {
        await seedResources();

        final rows = await db.myLibraryDao
            .watchResources(shelfUserId: escaped)
            .first;

        // `theirs` is public and not on this user's shelf, so the catalog shows
        // it. Under the unescaped pattern the `.not()` arm excluded it too, and
        // the resource became invisible in both views at once.
        expect(rows.map((r) => r.id), ['theirs']);
      },
    );

    test('resourcesOnShelf does not leak a colliding shelf', () async {
      await seedResources();

      expect(
        (await db.myLibraryDao.resourcesOnShelf(escaped)).map((r) => r.id),
        ['mine'],
      );
    });

    test('a `%` in the user id does not match every shelf', () async {
      await resource('mine', userId: const ['a%']);
      await resource('theirs', userId: const ['ab']);

      expect((await db.myLibraryDao.resourcesOnShelf('a%')).map((r) => r.id), [
        'mine',
      ]);
    });
  });

  group('CourseDao', () {
    test('watchCourses does not leak a colliding shelf', () async {
      await seedCourses();

      final rows = await db.courseDao.watchCourses(shelfUserId: escaped).first;

      expect(rows.map((c) => c.id), ['mine']);
    });

    test('coursesOnShelf does not leak a colliding shelf', () async {
      await seedCourses();

      expect((await db.courseDao.coursesOnShelf(escaped)).map((c) => c.id), [
        'mine',
      ]);
    });

    test('a `%` in the user id does not match every shelf', () async {
      await course('mine', userId: const ['a%']);
      await course('theirs', userId: const ['ab']);

      expect((await db.courseDao.coursesOnShelf('a%')).map((c) => c.id), [
        'mine',
      ]);
    });
  });
}
