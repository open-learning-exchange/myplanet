import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/course_progress_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late CourseProgressUploader uploader;
  late OutboxRepository outbox;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
    registerFallbackValue(config);
    outbox = OutboxRepository(database.outboxDao);
    uploader = CourseProgressUploader(api, database.courseProgressDao, outbox);
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: CourseProgressUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: CourseProgressUploader.endpointFor(config),
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  Future<void> seedPending({String id = 'progress-1'}) =>
      database.courseProgressDao.upsert(
        CourseProgressCompanion.insert(
          id: id,
          courseId: const Value('course-1'),
          userId: const Value('user-1'),
          stepNum: const Value(1),
          passed: const Value(true),
        ),
      );

  test('the endpoint carries no credentials', () {
    // It is persisted in `outbox`, which survives schema upgrades; the PIN
    // travels as a header at send time instead.
    final endpoint = CourseProgressUploader.endpointFor(config);
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, endsWith('/courses_progress'));
  });

  test('queues only progress rows that have not reached the server', () async {
    await seedPending();
    // A row the server already acknowledged carries a `couchId` and is not
    // pending — re-queueing it would post a second document.
    await database.courseProgressDao.upsert(
      CourseProgressCompanion.insert(
        id: 'progress-2',
        couchId: const Value('server-2'),
        rev: const Value('1-a'),
        courseId: const Value('course-1'),
        userId: const Value('user-1'),
        stepNum: const Value(2),
        passed: const Value(true),
      ),
    );

    expect(await uploader.queuePending(config: config), 1);
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((row) => row.itemId), ['progress-1']);
  });

  test('a successful upload marks the progress row as uploaded', () async {
    await seedPending();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'id': 'p1', 'rev': '1-a'}),
    );

    await uploader.handler(rowFor('progress-1'), {}, 'auth');

    final survivor = await database.courseProgressDao.findByCourseUserAndStep(
      'course-1',
      'user-1',
      1,
    );
    expect(survivor?.couchId, 'p1');
    expect(survivor?.rev, '1-a');
  });

  test('a response without a rev is not treated as uploaded', () async {
    await seedPending();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor('progress-1'), {}, 'auth');

    // Reporting success would retire the outbox entry while the row stayed
    // pending, and the next `queuePending` would post the progress a second
    // time as a fresh document. This asserts the opposite.
    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    final survivor = await database.courseProgressDao.findByCourseUserAndStep(
      'course-1',
      'user-1',
      1,
    );
    expect(survivor?.couchId, isNull);
  });

  test('the queued document carries the step and pass state', () async {
    // The server keys progress by (userId, courseId, stepNum); a document
    // missing `stepNum` or `passed` cannot be graded or deduped.
    await seedPending();

    await uploader.queuePending(config: config);
    final entry = await database.outboxDao.findOpen(
      CourseProgressUploader.type,
      'progress-1',
    );

    final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(doc['userId'], 'user-1');
    expect(doc['courseId'], 'course-1');
    expect(doc['stepNum'], 1);
    expect(doc['passed'], isTrue);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
