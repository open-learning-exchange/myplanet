import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/courses_repository.dart';
import 'package:myplanet/repository/resources_repository.dart';

/// A second sync must not undo what the first one plus the user's own taps
/// built up.
///
/// This is the data-path half of the Phase 116 reachability audit, and it is a
/// shape the suite had no coverage for at all. Every shelf test in the tree
/// runs *sync, then join, then assert* — the one order in which the defect
/// cannot appear. `my_library.userId` is written by two parties (the server
/// walk and the user's own shelf tap) over one column, so the walk has to carry
/// the local value forward; `MyLibraryMapper.fromDoc` takes `existingUserIds`
/// for exactly that, and its only production caller was not passing it.
///
/// Kotlin cannot have this bug: `ResourcesRepositoryImpl.batchInsertResources`
/// (`:667-704`) pre-loads the existing rows and hands them to
/// `MyLibrary.insertMyLibrary`, which only ever *adds* to `userId`
/// (`MyLibrary.kt:122-130`, `:218-228`).
///
/// The same test runs against courses, where the pattern was already right
/// (`courses_repository.dart` reads `existingById` and passes
/// `existingUserIds:`), so a regression on either side fails here.
class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  late AppDatabase db;
  late MockPlanetApi api;

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
  });
  tearDown(() => db.close());

  void stubWalk(String database, List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(
        '$dbUrl/$database/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': docs.length}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('$dbUrl/$database/_all_docs?include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final doc in docs) {'id': doc['_id'], 'doc': doc},
        ],
      }),
    );
  }

  test('a resource stays on the shelf across a second sync', () async {
    final repository = ResourcesRepository(
      api,
      db.myLibraryDao,
      db.removedLogDao,
    );
    stubWalk('resources', [
      {'_id': 'res-1', 'title': 'Algebra'},
    ]);

    await repository.sync(config: config);
    await repository.setShelfMemberships(['res-1'], 'user-1', joined: true);
    expect((await db.myLibraryDao.getById('res-1'))!.userId, [
      'user-1',
    ], reason: 'the tap itself');

    // The server document is unchanged; it carries no shelf information at all,
    // because a shelf lives in its own `shelf/<user>` document. A walk that
    // knows nothing about membership must not be able to retract it.
    await repository.sync(config: config);

    expect(
      (await db.myLibraryDao.getById('res-1'))!.userId,
      ['user-1'],
      reason:
          'the resources walk cleared a shelf membership it has no way to know '
          'about, so tapping the sync icon emptied My Library',
    );
    expect(
      await repository
          .watchResources(shelfUserId: 'user-1', myLibrary: true)
          .first,
      isNotEmpty,
      reason: 'and the My Library view is what the user sees empty',
    );
  });

  test('a course stays on the shelf across a second sync', () async {
    final repository = CoursesRepository(
      api,
      db.courseDao,
      db.removedLogDao,
      db.examDao,
      db.surveyDao,
    );
    stubWalk('courses', [
      {'_id': 'course-1', 'courseTitle': 'Algebra', 'steps': []},
    ]);

    await repository.sync(config: config);
    await repository.setShelfMembership('course-1', 'user-1', joined: true);
    expect((await db.courseDao.getById('course-1'))!.userId, ['user-1']);

    await repository.sync(config: config);

    expect(
      (await db.courseDao.getById('course-1'))!.userId,
      ['user-1'],
      reason: 'courses already carried existingUserIds; keep it that way',
    );
  });
}
