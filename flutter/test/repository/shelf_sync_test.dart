import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/my_library_mapper.dart';
import 'package:myplanet/repository/courses_repository.dart';
import 'package:myplanet/repository/resources_repository.dart';
import 'package:myplanet/repository/shelf_sync_repository.dart';

import '../support/mock_planet_api.dart';

/// The shelf walk — phase 3 of `SyncManager.startFullSync`.
///
/// Phase 116's D1/D2, and the reason the whole "my" half of the app was dead:
/// `my_library.userId` and `courses.userId` are shelf membership, every "my"
/// view reads them with `LIKE '%"<uid>"%'`, and with no shelf pull the only
/// writer was a tap on this device. A shelf built on Planet web or on another
/// handset never arrived, and `CoursesRepository.sync`'s `shelfId` parameter —
/// threaded end to end — had no production caller at all.
///
/// The shelf documents here are shaped as CouchDB stores them: keyed by the
/// owner's `org.couchdb.user:<name>` id, with `resourceIds`/`courseIds` arrays
/// and the `myTeamIds` key the probe list does *not* look at.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late ShelfSyncRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = ShelfSyncRepository(
      api,
      db.myLibraryDao,
      db.courseDao,
      db.userDao,
    );
  });

  tearDown(() => db.close());

  Map<String, dynamic> resourceDoc(String id, {String title = 'Water pump'}) =>
      {
        '_id': id,
        '_rev': '2-abc',
        'title': title,
        'description': 'How to service the hand pump',
        'isPrivate': false,
        'languages': <String>['English'],
        'subject': <String>['Practical skills'],
        'level': <String>['Beginner'],
        'resourceFor': <String>['Learner'],
      };

  Map<String, dynamic> courseDoc(String id, {String title = 'Well repair'}) => {
    '_id': id,
    '_rev': '2-abc',
    'courseTitle': title,
    'description': 'Five steps',
    'steps': <dynamic>[],
  };

  /// `GET shelf/_all_docs`, then the `keys` POSTs the walk makes.
  void stubShelves(Map<String, Map<String, dynamic>> shelvesById) {
    when(
      () => api.getJsonObject(
        '$dbUrl/shelf/_all_docs',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final id in shelvesById.keys) {'id': id, 'key': id},
        ],
      }),
    );
    when(
      () => api.postJsonObject(
        '$dbUrl/shelf/_all_docs?include_docs=true',
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      final keys =
          (invocation.positionalArguments[1] as Map<String, dynamic>)['keys']
              as List;
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final key in keys)
            if (shelvesById[key] case final doc?) {'id': key, 'doc': doc},
        ],
      });
    });
  }

  void stubDocs(String table, Map<String, Map<String, dynamic>> docsById) {
    when(
      () => api.postJsonObject(
        '$dbUrl/$table/_all_docs?include_docs=true',
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      final keys =
          (invocation.positionalArguments[1] as Map<String, dynamic>)['keys']
              as List;
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final key in keys)
            if (docsById[key] case final doc?)
              {'id': key, 'doc': doc}
            else
              // CouchDB answers a missing key with an `error` row and no `doc`.
              {'key': key, 'error': 'not_found'},
        ],
      });
    });
  }

  test(
    'a shelf built elsewhere puts the resource in that user\'s My Library',
    () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'org.couchdb.user:ada',
          couchId: const Value('org.couchdb.user:ada'),
          name: const Value('ada'),
        ),
      );
      stubShelves({
        'org.couchdb.user:ada': {
          '_id': 'org.couchdb.user:ada',
          '_rev': '5-abc',
          'resourceIds': <String>['resource-1'],
          'courseIds': <String>['course-1'],
          'meetupIds': <String>[],
        },
      });
      stubDocs('resources', {'resource-1': resourceDoc('resource-1')});
      stubDocs('courses', {'course-1': courseDoc('course-1')});

      final result = await repository.sync(config: config);
      expect(result, isA<SyncComplete>());

      final library = await db.myLibraryDao
          .watchResources(shelfUserId: 'org.couchdb.user:ada', myLibrary: true)
          .first;
      expect(library.map((r) => r.id), ['resource-1']);

      final courses = await db.courseDao
          .watchCourses(shelfUserId: 'org.couchdb.user:ada')
          .first;
      expect(courses.map((c) => c.id), ['course-1']);
    },
  );

  test(
    'a member registered on this device gets the stamp their screens read',
    () async {
      // The shelf document is keyed by the CouchDB id; every reader passes
      // `session.user.id`. For a member registered offline those differ — the
      // row keeps its locally-minted id and gains a `couchId` when the upload
      // lands — so stamping the raw shelf id writes a `userId` no reader can
      // match, and My Library stays empty with the walk reporting success.
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: '1756900000000',
          couchId: const Value('org.couchdb.user:ada'),
          name: const Value('ada'),
        ),
      );
      stubShelves({
        'org.couchdb.user:ada': {
          '_id': 'org.couchdb.user:ada',
          'resourceIds': <String>['resource-1'],
        },
      });
      stubDocs('resources', {'resource-1': resourceDoc('resource-1')});
      stubDocs('courses', const {});

      await repository.sync(config: config);

      final library = await db.myLibraryDao
          .watchResources(shelfUserId: '1756900000000', myLibrary: true)
          .first;
      expect(library.map((r) => r.id), ['resource-1']);
    },
  );

  test('membership is a union across shelves, never a replacement', () async {
    stubShelves({
      'org.couchdb.user:ada': {
        '_id': 'org.couchdb.user:ada',
        'resourceIds': <String>['resource-1'],
      },
      'org.couchdb.user:bob': {
        '_id': 'org.couchdb.user:bob',
        'resourceIds': <String>['resource-1'],
      },
    });
    stubDocs('resources', {'resource-1': resourceDoc('resource-1')});
    stubDocs('courses', const {});

    await repository.sync(config: config);

    final row = await db.myLibraryDao.getById('resource-1');
    expect(
      row!.userId,
      containsAll(<String>['org.couchdb.user:ada', 'org.couchdb.user:bob']),
    );
  });

  test('a shelf that lists nothing is never fetched', () async {
    // `hasShelfDataUltraFast`. An empty shelf costs one probe, not two more
    // requests per data type.
    stubShelves({
      'org.couchdb.user:ada': {
        '_id': 'org.couchdb.user:ada',
        'resourceIds': <String>[],
        'courseIds': <String>[],
        'meetupIds': <String>[],
      },
    });

    final result = await repository.sync(config: config);

    expect((result as SyncComplete).savedCount, 0);
    verifyNever(
      () => api.postJsonObject(
        any(that: contains('/resources/')),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    );
  });

  test('a shelf id that resolves to no document stamps nothing', () async {
    stubShelves({
      'org.couchdb.user:ada': {
        '_id': 'org.couchdb.user:ada',
        'resourceIds': <String>['resource-gone'],
      },
    });
    stubDocs('resources', const {});
    stubDocs('courses', const {});

    final result = await repository.sync(config: config);

    expect((result as SyncComplete).savedCount, 0);
    expect(await db.myLibraryDao.getById('resource-gone'), isNull);
  });

  test('the walk does not retract what the resources walk stored', () async {
    // `MyLibraryMapper.fromDoc` writes every list column unconditionally, so a
    // caller that omits one replaces the stored list with `const []` — the D1
    // defect, which emptied My Library on every sync. The shelf pass runs the
    // same mapper, so it inherits the same requirement.
    final resourcesRepository = ResourcesRepository(
      api,
      db.myLibraryDao,
      db.removedLogDao,
    );
    when(
      () => api.getJsonObject(
        '$dbUrl/resources/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 1}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('resources/_all_docs?include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {'id': 'resource-1', 'doc': resourceDoc('resource-1')},
        ],
      }),
    );
    await resourcesRepository.sync(config: config);
    // The user also added it by hand on this device.
    await resourcesRepository.setShelfMembership(
      'resource-1',
      'local-user',
      joined: true,
    );

    stubShelves({
      'org.couchdb.user:ada': {
        '_id': 'org.couchdb.user:ada',
        'resourceIds': <String>['resource-1'],
      },
    });
    stubDocs('resources', {'resource-1': resourceDoc('resource-1')});
    stubDocs('courses', const {});
    await repository.sync(config: config);

    final row = await db.myLibraryDao.getById('resource-1');
    expect(
      row!.userId,
      containsAll(<String>['local-user', 'org.couchdb.user:ada']),
    );
    expect(row.languages, ['English']);
    expect(row.subject, ['Practical skills']);
    expect(row.level, ['Beginner']);
    expect(row.resourceFor, ['Learner']);
  });

  test('a course keeps its steps and gains the shelf owner', () async {
    final coursesRepository = CoursesRepository(
      api,
      db.courseDao,
      db.removedLogDao,
      db.examDao,
      db.surveyDao,
    );
    final withSteps = {
      ...courseDoc('course-1'),
      'steps': [
        {'stepTitle': 'Turn off the water', 'description': 'At the valve'},
        {'stepTitle': 'Remove the seal', 'description': 'Two bolts'},
      ],
    };
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'total_rows': 1}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('courses/_all_docs?include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {'id': 'course-1', 'doc': withSteps},
        ],
      }),
    );
    await coursesRepository.sync(config: config);

    stubShelves({
      'org.couchdb.user:ada': {
        '_id': 'org.couchdb.user:ada',
        'courseIds': <String>['course-1'],
      },
    });
    stubDocs('resources', const {});
    stubDocs('courses', {'course-1': withSteps});
    await repository.sync(config: config);

    expect(await db.courseDao.getSteps('course-1'), hasLength(2));
    expect(
      (await db.courseDao.getById('course-1'))!.userId,
      contains('org.couchdb.user:ada'),
    );
  });

  test('mergeUserIds drops blanks and never duplicates', () {
    // `MyLibrary.setUserId` returns early on a blank id, so `[""]` is a value
    // the Kotlin cannot write — and it is the one value that fails the My
    // Library predicate and passes the catalog one.
    expect(MyLibraryMapper.mergeUserIds(const ['', 'a'], 'b'), ['a', 'b']);
    expect(MyLibraryMapper.mergeUserIds(const ['a'], 'a'), ['a']);
    expect(MyLibraryMapper.mergeUserIds(const ['a'], null), ['a']);
    expect(MyLibraryMapper.mergeUserIds(const [''], ''), isEmpty);
  });
}
