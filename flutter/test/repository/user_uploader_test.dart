import 'dart:convert';
import 'dart:io';

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
    final entry = await database.outboxDao.findOpen(
      UserUploader.type,
      'user-1',
    );

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
    final entry = await database.outboxDao.findOpen(
      UserUploader.type,
      'user-2',
    );

    final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(doc['name'], 'newbie');
    expect(doc['password'], 'plain-secret');
    expect(doc.containsKey('derived_key'), isFalse);
  });

  test(
    'a successful update stamps the latest rev and clears the flag',
    () async {
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

      final result = await uploader.handler(rowFor('user-1'), {
        'name': 'ada',
      }, 'auth');

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      final user = await database.userDao.getById('user-1');
      expect(user?.rev, '10-new');
      expect(user?.couchId, 'org.couchdb.user:ada');
      expect(user?.isUpdated, isFalse);
      expect(await database.userDao.pendingSyncUsers(), isEmpty);

      // The PUT carried the fetched rev, not the stale local one.
      final captured =
          verify(
                () => api.putJsonObject(
                  any(),
                  captureAny(),
                  authHeader: any(named: 'authHeader'),
                ),
              ).captured.single
              as Map<String, dynamic>;
      expect(captured['_rev'], '9-latest');
    },
  );

  test('a 404 on the GET falls back to a creation', () async {
    await seedEditedUser();
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(404, 'nf'),
    );
    when(
      () =>
          api.putJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'org.couchdb.user:ada',
        'rev': '1-rev',
      }),
    );

    final result = await uploader.handler(rowFor('user-1'), {
      'name': 'ada',
    }, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    final user = await database.userDao.getById('user-1');
    expect(user?.rev, '1-rev');
    expect(user?.isUpdated, isFalse);
  });

  test('a GET failure other than 404 is fatal for the attempt', () async {
    await seedEditedUser();
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(500, 'boom'),
    );

    final result = await uploader.handler(rowFor('user-1'), {
      'name': 'ada',
    }, 'auth');

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
      () =>
          api.putJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(409, 'conflict'),
    );

    final result = await uploader.handler(rowFor('user-1'), {
      'name': 'ada',
    }, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect((await database.userDao.getById('user-1'))?.isUpdated, isTrue);
  });

  test('a new account skips the GET and PUTs straight', () async {
    await seedNewUser();
    when(
      () =>
          api.putJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'org.couchdb.user:newbie',
        'rev': '1-first',
      }),
    );

    final result = await uploader.handler(rowFor('user-2'), {
      'name': 'newbie',
    }, 'auth');

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
      () =>
          api.putJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor('user-2'), {
      'name': 'newbie',
    }, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    // No id was returned, so the account was never recorded on the server:
    // it keeps no couchId and stays pending for the next drain.
    final user = await database.userDao.getById('user-2');
    expect(user?.couchId, isNull);
    expect(await database.userDao.pendingSyncUsers(), isNotEmpty);
  });

  test(
    'a queued payload embeds the photo bytes when the file exists',
    () async {
      // The image is read at queue time, not send time — the temp file the
      // picker wrote may be gone by the time the drain runs.
      final dir = await Directory.systemTemp.createTemp('user_upload_');
      addTearDown(() => dir.delete(recursive: true));
      final photo = File('${dir.path}/photo.jpg');
      await photo.writeAsBytes([1, 2, 3]);
      await seedEditedUser(userImage: photo.path);

      await uploader.queuePending(config: config);
      final entry = await database.outboxDao.findOpen(
        UserUploader.type,
        'user-1',
      );

      final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
      final attachments = doc['_attachments'] as Map<String, dynamic>;
      expect(attachments['img']['data'], base64Encode([1, 2, 3]));
    },
  );

  test('a queued payload omits the attachment when the file is gone', () async {
    await seedEditedUser(userImage: '/no/such/file.jpg');

    await uploader.queuePending(config: config);
    final entry = await database.outboxDao.findOpen(
      UserUploader.type,
      'user-1',
    );

    final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(doc.containsKey('_attachments'), isFalse);
  });

  test('skips a user whose name is blank', () async {
    // A nameless row cannot be addressed at `_users/org.couchdb.user:<name>`;
    // the Kotlin path skips it the same way. The row is still "pending" by the
    // couchId-null predicate, so `queuePending` counts it, but nothing is
    // enqueued — the outbox stays empty.
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: 'nameless',
        name: const Value(''),
        rolesList: const Value([]),
        userAdmin: const Value(false),
        joinDate: const Value(0),
        isUpdated: const Value(true),
      ),
    );

    await uploader.queuePending(config: config);
    expect(await database.outboxDao.due(9999999999999), isEmpty);
  });

  test('skips a user whose name is only whitespace', () async {
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: 'spacey',
        name: const Value('   '),
        rolesList: const Value([]),
        userAdmin: const Value(false),
        joinDate: const Value(0),
        isUpdated: const Value(true),
      ),
    );

    await uploader.queuePending(config: config);
    expect(await database.outboxDao.due(9999999999999), isEmpty);
  });

  test('skips a user with no name at all', () async {
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: 'noname',
        rolesList: const Value([]),
        userAdmin: const Value(false),
        joinDate: const Value(0),
        isUpdated: const Value(true),
      ),
    );

    await uploader.queuePending(config: config);
    expect(await database.outboxDao.due(9999999999999), isEmpty);
  });

  test('queues multiple pending users in one pass', () async {
    await seedEditedUser(id: 'user-a', name: 'ada');
    await seedEditedUser(id: 'user-b', name: 'bob');

    final count = await uploader.queuePending(config: config);
    expect(count, 2);
    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((row) => row.itemId).toSet(), {'user-a', 'user-b'});
  });

  test(
    're-queuing refreshes the payload rather than duplicating the entry',
    () async {
      // `enqueue` keys on (uploadType, itemId), so a second edit of the same
      // account replaces the snapshot instead of producing a second outbox row
      // that would POST the document twice.
      await seedEditedUser();
      await uploader.queuePending(config: config);
      await uploader.queuePending(config: config);

      final due = await database.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch + 1000,
      );
      expect(due.where((row) => row.itemId == 'user-1'), hasLength(1));
    },
  );

  test('the auth header carries the satellite credential and PIN', () {
    // The PIN is sent as a Basic header at drain time, never embedded in the
    // persisted endpoint.
    final header = UserUploader.authHeaderFor(config);
    expect(header, startsWith('Basic '));
    final decoded = utf8.decode(
      base64Decode(header.substring('Basic '.length)),
    );
    expect(decoded, 'satellite:1234');
  });

  test('the endpoint encodes a name containing a space', () {
    final endpoint = UserUploader.endpointFor(config, 'ada lovelace');
    expect(endpoint, contains('org.couchdb.user:ada%20lovelace'));
  });

  test('a user document carrying no rev is rejected for an update', () async {
    await seedEditedUser();
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'_id': 'x'}),
    );

    final result = await uploader.handler(rowFor('user-1'), {
      'name': 'ada',
    }, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    verifyNever(
      () =>
          api.putJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    );
  });

  test('a vanished user row aborts the upload', () async {
    // The row was deleted between queue and drain (a logout, say). The handler
    // must not POST a payload for an account that no longer exists.
    final result = await uploader.handler(rowFor('ghost'), {
      'name': 'ghost',
    }, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
