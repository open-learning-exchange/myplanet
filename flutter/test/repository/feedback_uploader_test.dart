import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/feedback_mapper.dart';
import 'package:myplanet/repository/feedback_repository_impl.dart';
import 'package:myplanet/repository/feedback_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late FeedbackUploader uploader;
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
    outbox = OutboxRepository(database.outboxDao);
    uploader = FeedbackUploader(
      api,
      FeedbackRepositoryImpl(feedbackDao: database.feedbackDao, planetApi: api),
      database.feedbackDao,
      outbox,
    );
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: FeedbackUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: FeedbackUploader.endpointFor(config),
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  Future<void> seedPending() => database.feedbackDao.upsert(
    FeedbackEntriesCompanion.insert(
      id: 'feedback-1',
      title: const Value('Sync fails offline'),
      owner: const Value('ada'),
      isUploaded: const Value(false),
    ),
  );

  test('the endpoint carries no credentials', () {
    // It is persisted in `outbox`, which survives schema upgrades; the PIN
    // travels as a header at send time instead.
    final endpoint = FeedbackUploader.endpointFor(config);
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, endsWith('/feedback'));
  });

  test('queues only feedback that has not reached the server', () async {
    await seedPending();
    await database.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'feedback-2',
        owner: const Value('ada'),
        isUploaded: const Value(true),
      ),
    );

    expect(await uploader.queuePending(config: config, userId: 'ada'), 1);
    // `enqueue` stamps `nextAttemptAt` with the wall clock, so the due
    // horizon has to be past it.
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((row) => row.itemId), ['feedback-1']);
  });

  test('a successful upload records the revision', () async {
    await seedPending();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '1-a'}),
    );

    await uploader.handler(rowFor('feedback-1'), {}, 'auth');

    final row = await database.feedbackDao.getById('feedback-1');
    expect(row?.isUploaded, isTrue);
    // Without the revision, adding a reply later PUTs against a missing `_rev`
    // and CouchDB rejects it as a conflict.
    expect(row?.rev, '1-a');
  });

  test('a response without a revision is not treated as uploaded', () async {
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

    final result = await uploader.handler(rowFor('feedback-1'), {}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect(
      (await database.feedbackDao.getById('feedback-1'))?.isUploaded,
      isFalse,
    );
  });

  test('a reply re-queues the feedback as an update, not a copy', () async {
    await seedPending();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '1-a'}),
    );
    await uploader.handler(rowFor('feedback-1'), {}, 'auth');

    final repository = FeedbackRepositoryImpl(
      feedbackDao: database.feedbackDao,
      planetApi: api,
    );
    await repository.addReply('feedback-1', 'Still broken', 'ada');

    expect(await uploader.queuePending(config: config, userId: 'ada'), 1);
    final payload = FeedbackMapper.toDoc(
      (await database.feedbackDao.getById('feedback-1'))!,
    );
    // Both keys matter: without `_id` the upload creates a second thread, and
    // without `_rev` it is rejected as a conflict.
    expect(payload['_id'], 'feedback-1');
    expect(payload['_rev'], '1-a');
  });

  test('stale cleanup spares feedback that never uploaded', () async {
    await seedPending();
    await database.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'server-gone',
        owner: const Value('ada'),
        isUploaded: const Value(true),
      ),
    );

    expect(await database.feedbackDao.deleteNotIn(const []), 1);
    expect(await database.feedbackDao.getById('feedback-1'), isNotNull);
    expect(await database.feedbackDao.getById('server-gone'), equals(null));
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
