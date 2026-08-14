import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/user_uploader.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late UserUploader uploader;
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
    uploader = UserUploader(api, database.userDao, outbox);
  });
  tearDown(() => database.close());

  /// A freshly edited account: it has a CouchDB id (so the handler GETs the
  /// live doc for its `_rev`) and `isUpdated` set (so `pendingSyncUsers`
  /// returns it). This is the shape a profile edit or photo change leaves.
  Future<void> seedEditedUser({
    String id = 'user-1',
    String name = 'ada',
    String? userImage,
  }) => database.userDao.upsert(
    UsersCompanion.insert(
      id: id,
      couchId: Value('org.couchdb.user:$name'),
      rev: const Value('1-a'),
      name: Value(name),
      rolesList: const Value(['learner']),
      userAdmin: const Value(false),
      joinDate: const Value(0),
      isUpdated: const Value(true),
      userImage: userImage == null ? const Value.absent() : Value(userImage),
    ),
  );

  /// A brand-new account created on this device: no CouchDB id yet, so the
  /// handler skips the GET and PUTs a creation carrying `password`.
  Future<void> seedNewUser() => database.userDao.upsert(
    UsersCompanion.insert(
      id: 'user-2',
      name: const Value('newbie'),
      rolesList: const Value([]),
      userAdmin: const Value(false),
      joinDate: const Value(0),
      password: const Value('plain-secret'),
    ),
  );

  OutboxRow rowFor(String itemId, {String endpoint = 'u'}) => OutboxRow(
    id: 'op-1',
    uploadType: UserUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: endpoint,
    httpMethod: 'PUT',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  test('the endpoint carries no credentials', () {
    final endpoint = UserUploader.endpointFor(config, 'ada');
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, contains('org.couchdb.user:ada'));
  });

  test('queues only users flagged as pending', () async {
    await seedEditedUser();
    // A synced, unedited account is not pending.
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: 'user-synced',
        couchId: const Value('org.couchdb.user:synced'),
        rev: const Value('3-c'),
        name: const Value('synced'),
        rolesList: const Value(['learner']),
        userAdmin: const Value(false),
        joinDate: const Value(0),
        isUpdated: const Value(false),
      ),
    );

    expect(await uploader.queuePending(config: config), 1);
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((row) => row.itemId), ['user-1']);
    expect(queued.single.httpMethod, 'PUT');
  });

  test('a queued payload serializes the user document', () async {
    await seedEditedUser();

    await uploader.queuePending(config: config);
    final entry = await database.outboxDao.findOpen(UserUploader.type, 'user-1');

    final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(doc['name'], 'ada');
    expect(doc['roles'], ['learner']);
    expect(doc['type'], 'user');
    // A user with a CouchDB id sends PBKDF2 fields, never the plaintext password.
    expect(doc.containsKey('password'), isFalse);
  });

  test('a new account queues its plaintext password', () async {
    await seedNewUser();

    await uploader.queuePending(config: config);
    final entry = await database.outboxDao.findOpen(UserUploader.type, 'user-2');

    final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(doc['name'], 'newbie');
    expect(doc['password'], 'plain-secret');
    expect(doc.containsKey('derived_key'), isFalse);
  });

  test('a successful update stamps the latest rev and clears the flag', () async {
    await seedEditedUser();
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        '_id': 'org.couchdb.user:ada',
        '_rev': '9-latest',
      }),
    );
    when(
      () => api.putJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'org.couchdb.user:ada',
        'rev': '10-new',
      }),
    );

    final result = await uploader.handler(rowFor('user-1'), {'name': 'ada'}, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    final user = await database.userDao.getById('user-1');
    expect(user?.rev, '10-new');
    expect(user?.couchId, 'org.couchdb.user:ada');
    expect(user?.isUpdated, isFalse);
    expect(await database.userDao.pendingSyncUsers(), isEmpty);

    // The PUT carried the fetched rev, not the stale local one.
    final captured = verify(
      () => api.putJsonObject(
        any(),
        captureAny(),
        authHeader: any(named: 'authHeader'),
      ),
    ).captured.single as Map<String, dynamic>;
    expect(captured['_rev'], '9-latest');
  });

  test('a 404 on the GET falls back to a creation', () async {
    await seedEditedUser();
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => const NetworkError<Map<String, dynamic>>(404, 'nf'));
    when(
      () => api.putJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'org.couchdb.user:ada',
        'rev': '1-rev',
      }),
    );

    final result = await uploader.handler(rowFor('user-1'), {'name': 'ada'}, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    final user = await database.userDao.getById('user-1');
    expect(user?.rev, '1-rev');
    expect(user?.isUpdated, isFalse);
  });

  test('a GET failure other than 404 is fatal for the attempt', () async {
    await seedEditedUser();
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => const NetworkError<Map<String, dynamic>>(500, 'boom'));

    final result = await uploader.handler(rowFor('user-1'), {'name': 'ada'}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    final user = await database.userDao.getById('user-1');
    expect(user?.isUpdated, isTrue);
  });

  test('a PUT failure leaves the row pending', () async {
    await seedEditedUser();
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'_rev': '9-latest'}),
    );
    when(
      () => api.putJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(409, 'conflict'),
    );

    final result = await uploader.handler(rowFor('user-1'), {'name': 'ada'}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect((await database.userDao.getById('user-1'))?.isUpdated, isTrue);
  });

  test('a new account skips the GET and PUTs straight', () async {
    await seedNewUser();
    when(
      () => api.putJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'org.couchdb.user:newbie',
        'rev': '1-first',
      }),
    );

    final result = await uploader.handler(rowFor('user-2'), {'name': 'newbie'}, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    final user = await database.userDao.getById('user-2');
    expect(user?.couchId, 'org.couchdb.user:newbie');
    expect(user?.rev, '1-first');
    expect(user?.isUpdated, isFalse);
    verifyNever(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    );
  });

  test('a response without an id is not treated as uploaded', () async {
    await seedNewUser();
    when(
      () => api.putJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor('user-2'), {'name': 'newbie'}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    // No id was returned, so the account was never recorded on the server:
    // it keeps no couchId and stays pending for the next drain.
    final user = await database.userDao.getById('user-2');
    expect(user?.couchId, isNull);
    expect(await database.userDao.pendingSyncUsers(), isNotEmpty);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
