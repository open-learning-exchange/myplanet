import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/courses_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late CoursesRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = CoursesRepository(api, db.courseDao, db.removedLogDao);
  });

  tearDown(() => db.close());

  Map<String, dynamic> row(
    String id,
    String title, {
    String? grade,
    String? subject,
    List<Map<String, dynamic>>? steps,
  }) {
    return {
      'id': id,
      'doc': {
        '_id': id,
        'courseTitle': title,
        'gradeLevel': ?grade,
        'subjectLevel': ?subject,
        'steps': ?steps,
      },
    };
  }

  void stubCount(int totalRows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': totalRows}),
    );
  }

  void stubPage(int skip, int limit, List<Map<String, dynamic>> rows) {
    when(
      () => api.getJsonObject(
        '$dbUrl/courses/_all_docs?include_docs=true&limit=$limit&skip=$skip',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rows': rows}),
    );
  }

  group('sync', () {
    test('stores courses and their embedded steps together', () async {
      stubCount(1);
      stubPage(0, 50, [
        row(
          'course-1',
          'Algebra',
          steps: [
            {'stepTitle': 'One'},
            {'stepTitle': 'Two'},
          ],
        ),
      ]);

      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 1);
      expect(await repository.localCount(), 1);
      expect((await repository.getCourseSteps('course-1')).length, 2);
    });

    test('returns steps in document order', () async {
      stubCount(1);
      stubPage(0, 50, [
        row(
          'course-1',
          'Algebra',
          steps: [
            {'stepTitle': 'First'},
            {'stepTitle': 'Second'},
            {'stepTitle': 'Third'},
          ],
        ),
      ]);
      await repository.sync(config: config);

      final steps = await repository.getCourseSteps('course-1');
      expect(steps.map((s) => s.stepTitle), ['First', 'Second', 'Third']);
    });

    test('walks multiple pages until the total is reached', () async {
      stubCount(75);
      stubPage(0, 50, List.generate(50, (i) => row('course-$i', 'Title $i')));
      stubPage(
        50,
        50,
        List.generate(25, (i) => row('course-${50 + i}', 'Title')),
      );

      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 75);
      expect(await repository.localCount(), 75);
    });

    test('reports progress as pages land', () async {
      stubCount(75);
      stubPage(0, 50, List.generate(50, (i) => row('course-$i', 'T')));
      stubPage(50, 50, List.generate(25, (i) => row('course-${50 + i}', 'T')));

      final progress = <int>[];
      await repository.sync(
        config: config,
        onProgress: (p) => progress.add(p.completed),
      );

      expect(progress, [50, 75]);
    });

    test('records the shelf id against synced courses', () async {
      stubCount(1);
      stubPage(0, 50, [row('course-1', 'Algebra')]);

      await repository.sync(config: config, shelfId: 'user-1');

      expect(await repository.isMyCourse('course-1', 'user-1'), isTrue);
      expect(await repository.isMyCourse('course-1', 'user-2'), isFalse);
    });

    test('preserves existing shelf membership across a resync', () async {
      stubCount(1);
      stubPage(0, 50, [row('course-1', 'Algebra')]);
      await repository.sync(config: config, shelfId: 'user-1');

      // A later sync for a different user must not evict the first.
      await repository.sync(config: config, shelfId: 'user-2');

      expect(await repository.isMyCourse('course-1', 'user-1'), isTrue);
      expect(await repository.isMyCourse('course-1', 'user-2'), isTrue);
    });

    test(
      'drops courses and their steps when the server stops listing them',
      () async {
        stubCount(2);
        stubPage(0, 50, [
          row(
            'course-1',
            'Kept',
            steps: [
              {'stepTitle': 'One'},
            ],
          ),
          row(
            'course-2',
            'Removed',
            steps: [
              {'stepTitle': 'Two'},
            ],
          ),
        ]);
        await repository.sync(config: config);
        expect(await repository.localCount(), 2);

        stubCount(1);
        stubPage(0, 50, [
          row(
            'course-1',
            'Kept',
            steps: [
              {'stepTitle': 'One'},
            ],
          ),
        ]);
        await repository.sync(config: config);

        expect(await repository.localCount(), 1);
        expect(await repository.getCourseById('course-2'), isNull);
        expect(await repository.getCourseSteps('course-2'), isEmpty);
      },
    );

    test('clears the table when the server reports no courses', () async {
      stubCount(1);
      stubPage(0, 50, [row('course-1', 'Algebra')]);
      await repository.sync(config: config);

      stubCount(0);
      final result = await repository.sync(config: config);

      expect((result as SyncComplete).savedCount, 0);
      expect(await repository.localCount(), 0);
    });

    test(
      'fails without touching the database when the count call fails',
      () async {
        when(
          () => api.getJsonObject(
            '$dbUrl/courses/_all_docs?limit=0',
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer(
          (_) async =>
              const NetworkError<Map<String, dynamic>>(401, 'Unauthorized'),
        );

        final result = await repository.sync(config: config);

        expect(result, isA<SyncFailed>());
        expect((result as SyncFailed).message, contains('401'));
        expect(await repository.localCount(), 0);
      },
    );
  });

  group('watchCourses', () {
    Future<void> seed() async {
      stubCount(3);
      stubPage(0, 50, [
        row('c1', 'Álgebra', grade: 'Primary', subject: 'Mathematics'),
        row('c2', 'Biology', grade: 'Secondary', subject: 'Science'),
        row('c3', 'Chemistry', grade: 'Secondary', subject: 'Science'),
      ]);
      await repository.sync(config: config);
    }

    test('filters on the diacritic-folded title', () async {
      await seed();

      final matches = await repository.watchCourses(query: 'algebra').first;
      expect(matches.map((c) => c.courseTitle), ['Álgebra']);
    });

    test('filters by grade level', () async {
      await seed();

      final matches = await repository
          .watchCourses(gradeLevel: 'Secondary')
          .first;
      expect(matches.map((c) => c.id), ['c2', 'c3']);
    });

    test('filters by subject level', () async {
      await seed();

      final matches = await repository
          .watchCourses(subjectLevel: 'Mathematics')
          .first;
      expect(matches.map((c) => c.id), ['c1']);
    });

    test('combines filters', () async {
      await seed();

      final matches = await repository
          .watchCourses(query: 'biology', gradeLevel: 'Secondary')
          .first;
      expect(matches.map((c) => c.id), ['c2']);

      final none = await repository
          .watchCourses(query: 'biology', gradeLevel: 'Primary')
          .first;
      expect(none, isEmpty);
    });

    test('restricts to the shelf when a user id is given', () async {
      stubCount(2);
      stubPage(0, 50, [row('c1', 'Mine'), row('c2', 'Theirs')]);
      await repository.sync(config: config);
      await repository.setShelfMembership('c1', 'user-1', joined: true);

      final mine = await repository.watchCourses(shelfUserId: 'user-1').first;
      expect(mine.map((c) => c.id), ['c1']);
    });

    test('emits an updated list when a sync writes new rows', () async {
      stubCount(1);
      stubPage(0, 50, [row('c1', 'Algebra')]);

      final emissions = <int>[];
      final subscription = repository.watchCourses().listen(
        (rows) => emissions.add(rows.length),
      );

      await pumpEventQueue();
      await repository.sync(config: config);
      await pumpEventQueue();

      expect(emissions.first, 0);
      expect(emissions.last, 1);
      await subscription.cancel();
    });
  });

  group('shelf membership', () {
    setUp(() async {
      stubCount(1);
      stubPage(0, 50, [row('c1', 'Algebra')]);
      await repository.sync(config: config);
    });

    test('joining and leaving toggles membership', () async {
      expect(await repository.isMyCourse('c1', 'user-1'), isFalse);

      await repository.setShelfMembership('c1', 'user-1', joined: true);
      expect(await repository.isMyCourse('c1', 'user-1'), isTrue);

      await repository.setShelfMembership('c1', 'user-1', joined: false);
      expect(await repository.isMyCourse('c1', 'user-1'), isFalse);
    });

    test('joining twice does not duplicate the member', () async {
      await repository.setShelfMembership('c1', 'user-1', joined: true);
      await repository.setShelfMembership('c1', 'user-1', joined: true);

      final course = await repository.getCourseById('c1');
      expect(course!.userId, ['user-1']);
    });

    test('is a no-op for an unknown course', () async {
      await repository.setShelfMembership('missing', 'user-1', joined: true);
      expect(await repository.isMyCourse('missing', 'user-1'), isFalse);
    });
  });

  group('filter options', () {
    test(
      'lists the distinct grade and subject levels present locally',
      () async {
        stubCount(3);
        stubPage(0, 50, [
          row('c1', 'A', grade: 'Primary', subject: 'Mathematics'),
          row('c2', 'B', grade: 'Secondary', subject: 'Science'),
          row('c3', 'C', grade: 'Secondary', subject: 'Science'),
        ]);
        await repository.sync(config: config);

        expect(await repository.gradeLevels(), ['Primary', 'Secondary']);
        expect(await repository.subjectLevels(), ['Mathematics', 'Science']);
      },
    );

    test('omits courses with no level set', () async {
      stubCount(2);
      stubPage(0, 50, [row('c1', 'A', grade: 'Primary'), row('c2', 'B')]);
      await repository.sync(config: config);

      expect(await repository.gradeLevels(), ['Primary']);
    });
  });
}
