import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/repository/courses_repository.dart';
import 'package:myplanet/repository/resources_repository.dart';

/// The courses walk's third table.
///
/// Kotlin knows a course step's resources because `queueCourseResources`
/// buffers each embedded resource **document** and
/// `flushPendingCourseResources` writes it into `my_library` stamped with the
/// step and course it came from (`CoursesRepositoryImpl.kt:792-798`,
/// `:819-863`). `CourseMapper._parseSteps` kept `resources.length` and threw
/// the documents away, so the port held a count of rows it did not have — and
/// every reader of a step's resources was unreachable, not merely unwritten.
///
/// These drive the walk with a document shaped the way the server sends one,
/// and read back through the DAO predicates the Kotlin readers use. A fixture
/// that inserts a stamped row directly would prove nothing: the question is
/// whether the writer can produce values the reader's `WHERE` matches.
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
  late CoursesRepository repository;
  late ResourcesRepository resources;

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = CoursesRepository(
      api,
      db.courseDao,
      db.removedLogDao,
      db.examDao,
      db.surveyDao,
    );
    resources = ResourcesRepository(api, db.myLibraryDao, db.removedLogDao);
  });
  tearDown(() => db.close());

  void stubCoursesWalk(List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': docs.length}),
    );
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?include_docs=true&limit=50&skip=0',
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

  /// A resource sub-object shaped the way Planet embeds one in a course step —
  /// `MyCourse.serialize` rebuilds exactly this from `serializeResource`
  /// (`MyCourse.kt:135-137`, `MyLibrary.kt:82-84`).
  Map<String, dynamic> resourceDoc(
    String id, {
    String title = 'Rainfall',
    String rev = '1-a',
    String mediaType = 'pdf',
  }) => {
    '_id': id,
    '_rev': rev,
    'title': title,
    'mediaType': mediaType,
    '_attachments': {
      'rainfall.pdf': {'content_type': 'application/pdf', 'length': 10},
    },
  };

  Map<String, dynamic> courseDoc(
    String id, {
    required List<Map<String, dynamic>> steps,
  }) => {'_id': id, 'courseTitle': 'Water', 'steps': steps};

  group('ingestion', () {
    test(
      'a step resource lands in my_library keyed by its document id',
      () async {
        stubCoursesWalk([
          courseDoc(
            'course-1',
            steps: [
              {
                'stepTitle': 'Intro',
                'resources': [resourceDoc('res-1')],
              },
            ],
          ),
        ]);

        await repository.sync(config: config);

        final row = await db.myLibraryDao.getById('res-1');
        expect(row, isNotNull);
        expect(row!.title, 'Rainfall');
        expect(row.rev, '1-a');
        // The attachment is resolved the same way the resources walk resolves it,
        // so the download path works for a course-only resource too.
        expect(row.resourceLocalAddress, 'rainfall.pdf');
      },
    );

    test(
      'the stamp makes the step reachable through the Kotlin reader',
      () async {
        stubCoursesWalk([
          courseDoc(
            'course-1',
            steps: [
              {
                'stepTitle': 'Intro',
                'resources': [resourceDoc('res-1')],
              },
              {
                'stepTitle': 'Deeper',
                'resources': [resourceDoc('res-2', title: 'Aquifers')],
              },
            ],
          ),
        ]);

        await repository.sync(config: config);

        // `getAllStepResources` is the port of the call three Kotlin readers
        // make: the inline list, the auto-download and the next-step prefetch.
        final stepOne = await resources.getAllStepResources(
          CourseMapper.stepIdFor('course-1', 0),
        );
        final stepTwo = await resources.getAllStepResources(
          CourseMapper.stepIdFor('course-1', 1),
        );
        expect(stepOne.map((r) => r.id), ['res-1']);
        expect(stepTwo.map((r) => r.id), ['res-2']);

        // And the course-level download button's own predicate.
        final notDownloaded = await resources.getCourseResources(
          'course-1',
          isOffline: false,
        );
        expect(notDownloaded.map((r) => r.id).toSet(), {'res-1', 'res-2'});
      },
    );

    test('the count on the tile now matches the rows held', () async {
      // `course_detail_screen` renders `resourcesInStep(step.noOfResources)`.
      // That number was previously unsubstantiated; pin the two together.
      stubCoursesWalk([
        courseDoc(
          'course-1',
          steps: [
            {
              'stepTitle': 'Intro',
              'resources': [
                resourceDoc('res-1'),
                resourceDoc('res-2', title: 'Aquifers'),
              ],
            },
          ],
        ),
      ]);

      await repository.sync(config: config);

      final step = (await repository.getCourseSteps('course-1')).single;
      final held = await resources.getAllStepResources(step.id);
      expect(held, hasLength(step.noOfResources));
    });

    test('a step with no resources writes none', () async {
      stubCoursesWalk([
        courseDoc(
          'course-1',
          steps: [
            {'stepTitle': 'Intro'},
          ],
        ),
      ]);

      await repository.sync(config: config);

      expect(await db.myLibraryDao.getAll(), isEmpty);
    });

    test(
      'a malformed resources array cannot abort the walk or write a phantom',
      () async {
        // Kotlin calls `.asJsonObject` on every element and the sync walk's
        // `continueOnError` is false, so one bad element throws out of the
        // whole page. And an `_id`-less object writes `id = ""`, where the
        // primary key collides and the last such resource wins.
        stubCoursesWalk([
          courseDoc(
            'course-1',
            steps: [
              {
                'stepTitle': 'Intro',
                'resources': [
                  'not-an-object',
                  {'title': 'no id at all'},
                  {'_id': '', 'title': 'blank id'},
                  // Whitespace is the case `MyLibraryMapper.fromDoc`'s own
                  // `isEmpty` check does *not* catch, so only the courses
                  // walk's trim-filter keeps this junk row out of the table.
                  {'_id': '   ', 'title': 'whitespace id'},
                  {'_id': '_design/thing', 'title': 'a design doc'},
                  resourceDoc('res-1'),
                ],
              },
            ],
          ),
        ]);

        final result = await repository.sync(config: config);

        expect(result, isA<SyncComplete>());
        expect(
          (await db.myLibraryDao.getAll()).map((r) => r.id),
          ['res-1'],
          reason: 'the good resource survives and no blank-id row is written',
        );
        expect(await repository.getCourseSteps('course-1'), hasLength(1));
      },
    );

    test('a resource in two steps of one course keeps the last step', () async {
      stubCoursesWalk([
        courseDoc(
          'course-1',
          steps: [
            {
              'stepTitle': 'Intro',
              'resources': [resourceDoc('res-1')],
            },
            {
              'stepTitle': 'Again',
              'resources': [resourceDoc('res-1')],
            },
          ],
        ),
      ]);

      await repository.sync(config: config);

      // One row per resource id, so the later companion wins the upsert —
      // what Kotlin's REPLACE over the twice-mutated entity does. The keep
      // set is a union across the document, so the release must not undo it.
      final row = await db.myLibraryDao.getById('res-1');
      expect(row!.stepId, CourseMapper.stepIdFor('course-1', 1));
      expect(row.courseId, 'course-1');
    });
  });

  group('the stamp is retired when the course stops claiming it', () {
    test(
      'deleting a step releases its resource rather than re-binding it',
      () async {
        stubCoursesWalk([
          courseDoc(
            'course-1',
            steps: [
              {
                'stepTitle': 'Intro',
                'resources': [resourceDoc('res-1')],
              },
              {
                'stepTitle': 'Deeper',
                'resources': [resourceDoc('res-2', title: 'Aquifers')],
              },
            ],
          ),
        ]);
        await repository.sync(config: config);

        // The author removes step 0. Step ids are positional, so what was
        // `course-1:1` becomes `course-1:0` — the id `res-1` still carries.
        stubCoursesWalk([
          courseDoc(
            'course-1',
            steps: [
              {
                'stepTitle': 'Deeper',
                'resources': [resourceDoc('res-2', title: 'Aquifers')],
              },
            ],
          ),
        ]);
        await repository.sync(config: config);

        final surviving = await resources.getAllStepResources(
          CourseMapper.stepIdFor('course-1', 0),
        );
        expect(
          surviving.map((r) => r.id),
          ['res-2'],
          reason:
              'the deleted step\'s resource must not surface under the step '
              'that took its slot',
        );

        final released = await db.myLibraryDao.getById('res-1');
        expect(released!.stepId, isNull);
        expect(
          released.courseId,
          isNull,
          reason:
              'getCourseResources reads courseId alone, so a stale one would '
              'keep over-filling the course download dialog',
        );
      },
    );

    test('removing a course\'s last resource clears the old join', () async {
      stubCoursesWalk([
        courseDoc(
          'course-1',
          steps: [
            {
              'stepTitle': 'Intro',
              'resources': [resourceDoc('res-1')],
            },
          ],
        ),
      ]);
      await repository.sync(config: config);

      stubCoursesWalk([
        courseDoc(
          'course-1',
          steps: [
            {'stepTitle': 'Intro'},
          ],
        ),
      ]);
      await repository.sync(config: config);

      expect(
        await resources.getCourseResources('course-1', isOffline: false),
        isEmpty,
      );
      expect((await db.myLibraryDao.getById('res-1'))!.stepId, isNull);
    });

    test('another course\'s stamp is left alone', () async {
      stubCoursesWalk([
        courseDoc(
          'course-1',
          steps: [
            {
              'stepTitle': 'Intro',
              'resources': [resourceDoc('res-1')],
            },
          ],
        ),
        courseDoc(
          'course-2',
          steps: [
            {
              'stepTitle': 'Other',
              'resources': [resourceDoc('res-2', title: 'Aquifers')],
            },
          ],
        ),
      ]);
      await repository.sync(config: config);

      expect((await db.myLibraryDao.getById('res-1'))!.courseId, 'course-1');
      expect((await db.myLibraryDao.getById('res-2'))!.courseId, 'course-2');
    });
  });

  group('the course link and the shelf survive a resources re-pull', () {
    test(
      'the resources walk cannot clear a stamp the courses walk wrote',
      () async {
        stubCoursesWalk([
          courseDoc(
            'course-1',
            steps: [
              {
                'stepTitle': 'Intro',
                'resources': [resourceDoc('res-1')],
              },
            ],
          ),
        ]);
        await repository.sync(config: config);

        // The user then adds the same resource to their shelf.
        await db.myLibraryDao.upsertAll([
          (await db.myLibraryDao.getById(
            'res-1',
          ))!.toCompanion(false).copyWith(userId: const Value(['user-1'])),
        ]);

        // Now the `resources` database walk pulls the same document. It knows
        // nothing about courses and passes no stamp.
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
            '$dbUrl/resources/_all_docs?include_docs=true&limit=100&skip=0',
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer(
          (_) async => NetworkSuccess<Map<String, dynamic>>({
            'rows': [
              {'id': 'res-1', 'doc': resourceDoc('res-1', rev: '2-b')},
            ],
          }),
        );

        await resources.sync(config: config);

        final row = await db.myLibraryDao.getById('res-1');
        expect(row!.rev, '2-b', reason: 'the re-pull still updates the row');
        expect(
          row.stepId,
          CourseMapper.stepIdFor('course-1', 0),
          reason: 'Value.absent() must not be written over the stamp',
        );
        expect(row.courseId, 'course-1');
        expect(row.userId, [
          'user-1',
        ], reason: 'and the shelf the user built is still there');
      },
    );

    test(
      'ingesting a course resource does not retract a shelf membership',
      () async {
        await db.myLibraryDao.upsertAll([
          MyLibraryTableCompanion.insert(
            id: 'res-1',
            rev: const Value('1-a'),
            userId: const Value(['user-1']),
          ),
        ]);

        stubCoursesWalk([
          courseDoc(
            'course-1',
            steps: [
              {
                'stepTitle': 'Intro',
                'resources': [resourceDoc('res-1')],
              },
            ],
          ),
        ]);
        await repository.sync(config: config);

        final row = await db.myLibraryDao.getById('res-1');
        expect(
          row!.userId,
          ['user-1'],
          reason:
              'Kotlin passes no userId to the drain, so setUserId returns '
              'early and the stored shelf is untouched',
        );
        expect(row.stepId, CourseMapper.stepIdFor('course-1', 0));
      },
    );

    test('a course-only resource is not put on anybody\'s shelf', () async {
      stubCoursesWalk([
        courseDoc(
          'course-1',
          steps: [
            {
              'stepTitle': 'Intro',
              'resources': [resourceDoc('res-1')],
            },
          ],
        ),
      ]);
      await repository.sync(config: config);

      expect((await db.myLibraryDao.getById('res-1'))!.userId, isEmpty);
      expect(await db.myLibraryDao.resourcesOnShelf('user-1'), isEmpty);
    });
  });
}
