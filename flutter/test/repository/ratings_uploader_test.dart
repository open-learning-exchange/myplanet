import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/repository/ratings_uploader.dart';

import 'device_identity_fixture.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late RatingsUploader uploader;
  late OutboxRepository outbox;
  late RatingsRepository repository;

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
    repository = RatingsRepository(api, database.ratingDao, database.userDao);
    uploader = RatingsUploader(
      api,
      repository,
      database.ratingDao,
      database.userDao,
      outbox,
      testDeviceIdentity,
    );
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: RatingsUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: RatingsUploader.endpointFor(config),
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  Future<void> seedPendingRating() => database.ratingDao.upsert(
    RatingsCompanion.insert(
      id: 'rating-1',
      time: DateTime.now().millisecondsSinceEpoch,
      userId: 'user-1',
      rate: 5,
      item: 'resource-1',
      type: 'resource',
    ),
  );

  test('the endpoint carries no credentials', () {
    // It is persisted in `outbox`, which survives schema upgrades; the PIN
    // travels as a header at send time instead.
    final endpoint = RatingsUploader.endpointFor(config);
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, endsWith('/ratings'));
  });

  test('queues only ratings that have not reached the server', () async {
    await seedPendingRating();
    await database.ratingDao.upsert(
      RatingsCompanion.insert(
        id: 'rating-2',
        time: DateTime.now().millisecondsSinceEpoch,
        userId: 'user-1',
        rate: 4,
        item: 'resource-2',
        type: 'resource',
        isUpdated: const Value(false),
      ),
    );

    expect(await uploader.queuePending(config: config), 1);
    // `enqueue` stamps `nextAttemptAt` with the wall clock, so the due
    // horizon has to be past it.
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((row) => row.itemId), ['rating-1']);
    final payload = jsonDecode(queued.single.payload) as Map<String, dynamic>;
    for (final field in testDeviceFields.entries) {
      expect(payload, containsPair(field.key, field.value));
    }
  });

  test('a successful upload marks the rating as uploaded', () async {
    await seedPendingRating();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'id': 'r1', 'rev': '1-a'}),
    );

    await uploader.handler(rowFor('rating-1'), {}, 'auth');

    final row = await database.ratingDao.findById('rating-1');
    expect(row?.isUpdated, isFalse);
  });

  test('a response without a rev is not treated as uploaded', () async {
    await seedPendingRating();
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor('rating-1'), {}, 'auth');

    // Reporting success would retire the outbox entry while the row stayed
    // pending, and the next `queuePending` would post the rating a second
    // time as a fresh document. This asserted the opposite.
    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    final row = await database.ratingDao.findById('rating-1');
    expect(row?.isUpdated, isTrue);
  });
  test('the queued document names its author', () async {
    // Kotlin sends the serialized user; Planet groups ratings by it, so a
    // document without one cannot be attributed or deduplicated. The port
    // omitted the field entirely.
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: 'user-1',
        couchId: const Value('org.couchdb.user:ada'),
        name: const Value('ada'),
        planetCode: const Value('planet-a'),
        parentCode: const Value('nation'),
      ),
    );
    await seedPendingRating();

    await uploader.queuePending(config: config);
    final entry = await database.outboxDao.findOpen(
      RatingsUploader.type,
      'rating-1',
    );

    final user = jsonDecode(entry!.payload)['user'] as Map<String, dynamic>;
    expect(user['_id'], 'org.couchdb.user:ada');
    expect(user['name'], 'ada');
  });

  test('the stored rater document is what uploads, not a rebuild', () async {
    // `Rating.serializeRating` sends `rating.user` — the JSON string the row
    // carries — rather than re-deriving it. For a rating pulled from the
    // server that string is the rater's own document, and the rater need not
    // be a `users` row on this device at all.
    await database.ratingDao.upsert(
      RatingsCompanion.insert(
        id: 'rating-1',
        time: 1750000000000,
        userId: 'org.couchdb.user:bob',
        rate: 5,
        item: 'resource-1',
        type: 'resource',
        parentCode: const Value('nation'),
        createdOn: const Value('nation-of-record'),
        user: const Value(
          '{"_id":"org.couchdb.user:bob","name":"bob","planetCode":"gua"}',
        ),
      ),
    );

    await uploader.queuePending(config: config);
    final entry = await database.outboxDao.findOpen(
      RatingsUploader.type,
      'rating-1',
    );
    final payload = jsonDecode(entry!.payload) as Map<String, dynamic>;

    expect(payload['user'], {
      '_id': 'org.couchdb.user:bob',
      'name': 'bob',
      'planetCode': 'gua',
    });
    // `createdOn` is not a timestamp: `setRatingData` assigns the rater's
    // `parentCode` to it, and a synced row carries whatever the document said.
    expect(payload['createdOn'], 'nation-of-record');
  });

  test('a row with no stored rater still names its author', () async {
    // The fallback: `_toDoc` rebuilds from the `users` table, which is what
    // every row did before v46.
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: 'user-1',
        couchId: const Value('org.couchdb.user:ada'),
        name: const Value('ada'),
      ),
    );
    await seedPendingRating();

    await uploader.queuePending(config: config);
    final entry = await database.outboxDao.findOpen(
      RatingsUploader.type,
      'rating-1',
    );
    final user = jsonDecode(entry!.payload)['user'] as Map<String, dynamic>;
    expect(user['_id'], 'org.couchdb.user:ada');
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
