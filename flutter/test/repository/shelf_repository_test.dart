import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/shelf_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late ShelfRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  const shelfUrl =
      'https://satellite:1234@planet.example.org:443/db/shelf/org.couchdb.user:ada';

  setUpAll(() {
    registerFallbackValue(<String, dynamic>{});
  });

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = ShelfRepository(
      api,
      db.courseDao,
      db.myLibraryDao,
      db.removedLogDao,
      db.meetupDao,
    );
  });

  tearDown(() => db.close());

  Future<void> addCourse(String id, {List<String> shelf = const []}) {
    return db.courseDao.upsertAll([
      CoursesCompanion.insert(
        id: id,
        courseId: Value(id),
        userId: Value(shelf),
      ),
    ], const []);
  }

  group('mergeIds', () {
    test('keeps every local id', () {
      expect(ShelfRepository.mergeIds(const ['a', 'b'], const [], const []), [
        'a',
        'b',
      ]);
    });

    test('adds server ids the user has not removed', () {
      expect(
        ShelfRepository.mergeIds(const ['a'], const ['b', 'c'], const []),
        ['a', 'b', 'c'],
      );
    });

    test('drops server ids the user removed', () {
      expect(
        ShelfRepository.mergeIds(const ['a'], const ['b', 'c'], const ['b']),
        ['a', 'c'],
      );
    });

    test('does not duplicate an id present on both sides', () {
      expect(
        ShelfRepository.mergeIds(const ['a'], const ['a', 'b'], const []),
        ['a', 'b'],
      );
    });

    /// The removal list filters only the server side, so re-adding something
    /// beats a stale removal record.
    test('a local id survives even if it is also in the removed list', () {
      expect(ShelfRepository.mergeIds(const ['a'], const ['a'], const ['a']), [
        'a',
      ]);
    });
  });

  group('buildShelfDocument', () {
    test('includes locally joined courses', () async {
      await addCourse('course-1', shelf: const ['user-1']);
      await addCourse('course-2');

      final doc = await repository.buildShelfDocument(
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
        serverDoc: const {},
      );

      expect(doc['courseIds'], ['course-1']);
      expect(doc['_id'], 'org.couchdb.user:ada');
    });

    test('carries the server _rev so CouchDB accepts the update', () async {
      final doc = await repository.buildShelfDocument(
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
        serverDoc: const {'_rev': '3-abc'},
      );

      expect(doc['_rev'], '3-abc');
    });

    test('omits _rev when the shelf document does not exist yet', () async {
      final doc = await repository.buildShelfDocument(
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
        serverDoc: const {},
      );

      expect(doc.containsKey('_rev'), isFalse);
    });

    test('preserves server ids the user never touched', () async {
      await addCourse('course-1', shelf: const ['user-1']);

      final doc = await repository.buildShelfDocument(
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
        serverDoc: const {
          'courseIds': ['course-9'],
        },
      );

      expect(doc['courseIds'], containsAll(['course-1', 'course-9']));
    });

    /// Without the removed log the merge would re-add what the user just left.
    test('a leave survives the merge with the server copy', () async {
      await addCourse('course-1');
      await db.removedLogDao.record(
        type: ShelfRepository.coursesType,
        userId: 'user-1',
        docId: 'course-1',
      );

      final doc = await repository.buildShelfDocument(
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
        serverDoc: const {
          'courseIds': ['course-1'],
        },
      );

      expect(doc['courseIds'], isEmpty);
    });

    test('re-joining clears the removal and the id comes back', () async {
      await db.removedLogDao.record(
        type: ShelfRepository.coursesType,
        userId: 'user-1',
        docId: 'course-1',
      );
      await db.removedLogDao.clear(
        type: ShelfRepository.coursesType,
        userId: 'user-1',
        docId: 'course-1',
      );

      final doc = await repository.buildShelfDocument(
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
        serverDoc: const {
          'courseIds': ['course-1'],
        },
      );

      expect(doc['courseIds'], ['course-1']);
    });

    test('one user removal does not affect another user', () async {
      await addCourse('course-1');
      await db.removedLogDao.record(
        type: ShelfRepository.coursesType,
        userId: 'user-2',
        docId: 'course-1',
      );

      final doc = await repository.buildShelfDocument(
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
        serverDoc: const {
          'courseIds': ['course-1'],
        },
      );

      expect(doc['courseIds'], ['course-1']);
    });

    test('merges locally joined meetups with the server shelf', () async {
      await db.meetupDao.upsert(
        MeetupsCompanion.insert(
          id: 'local-meetup',
          meetupId: const Value('meetup-local'),
          userId: const Value('user-1'),
        ),
      );
      final doc = await repository.buildShelfDocument(
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
        serverDoc: const {
          'meetupIds': ['meetup-1', 'meetup-2'],
        },
      );

      expect(doc['meetupIds'], ['meetup-local', 'meetup-1', 'meetup-2']);
    });
  });

  group('upload', () {
    void stubGet(NetworkResult<Map<String, dynamic>> result) {
      when(
        () => api.getJsonObject(shelfUrl, authHeader: any(named: 'authHeader')),
      ).thenAnswer((_) async => result);
    }

    void stubPut(NetworkResult<Map<String, dynamic>> result) {
      when(
        () => api.putJsonObject(
          shelfUrl,
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => result);
    }

    test('reads the shelf, merges, and puts it back', () async {
      await addCourse('course-1', shelf: const ['user-1']);
      stubGet(
        const NetworkSuccess<Map<String, dynamic>>({
          '_rev': '2-abc',
          'courseIds': ['course-9'],
        }),
      );
      stubPut(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

      final result = await repository.upload(
        config: config,
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
      );

      expect(result, isA<SyncComplete>());

      final sent =
          verify(
                () => api.putJsonObject(
                  shelfUrl,
                  captureAny(),
                  authHeader: any(named: 'authHeader'),
                ),
              ).captured.single
              as Map<String, dynamic>;
      expect(sent['_rev'], '2-abc');
      expect(sent['courseIds'], containsAll(['course-1', 'course-9']));
    });

    /// A user who has never had a shelf document gets a 404, which is normal.
    test('creates the document when the server has none', () async {
      stubGet(const NetworkError<Map<String, dynamic>>(404, 'not_found'));
      stubPut(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

      final result = await repository.upload(
        config: config,
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
      );

      expect(result, isA<SyncComplete>());
      final sent =
          verify(
                () => api.putJsonObject(
                  shelfUrl,
                  captureAny(),
                  authHeader: any(named: 'authHeader'),
                ),
              ).captured.single
              as Map<String, dynamic>;
      expect(sent.containsKey('_rev'), isFalse);
    });

    test('does not write when the read fails', () async {
      stubGet(const NetworkError<Map<String, dynamic>>(500, 'boom'));

      final result = await repository.upload(
        config: config,
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
      );

      expect(result, isA<SyncFailed>());
      verifyNever(
        () => api.putJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      );
    });

    test('reports a failed write', () async {
      stubGet(const NetworkSuccess<Map<String, dynamic>>({}));
      stubPut(const NetworkError<Map<String, dynamic>>(409, 'conflict'));

      final result = await repository.upload(
        config: config,
        userId: 'user-1',
        shelfDocId: 'org.couchdb.user:ada',
      );

      expect((result as SyncFailed).message, contains('409'));
    });

    test('refuses a user with no CouchDB id', () async {
      final result = await repository.upload(
        config: config,
        userId: 'user-1',
        shelfDocId: '',
      );

      expect(result, isA<SyncFailed>());
      verifyZeroInteractions(api);
    });
  });
}
