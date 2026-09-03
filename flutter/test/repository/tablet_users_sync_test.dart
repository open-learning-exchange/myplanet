import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/user_repository.dart';

import '../support/mock_planet_api.dart';

/// The `tablet_users` walk — `TransactionSyncManager.syncDb("tablet_users")`
/// plus `UserRepositoryImpl.insertUsersFromSync`.
///
/// Phase 116's D15: the port had no walk at all, so `users` only ever held
/// accounts that had signed in on this device. Member detail said "Unknown
/// member" for everyone else, the leaderboard silently dropped every member it
/// could not resolve, and the team member list rendered the raw
/// `org.couchdb.user:bob`.
///
/// The documents below are shaped like real `_users` documents, not like
/// fixtures: `_id` is `org.couchdb.user:<name>`, the profile photo is an
/// `_attachments` entry rather than a `userImage` field, and the credentials
/// are the PBKDF2 quartet CouchDB actually stores.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late UserRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = UserRepository(api, db.userDao);
  });

  tearDown(() => db.close());

  Map<String, dynamic> userDoc(
    String name, {
    String? firstName,
    String? lastName,
    Map<String, dynamic>? attachments,
  }) => {
    '_id': 'org.couchdb.user:$name',
    '_rev': '3-abc',
    'name': name,
    'type': 'user',
    'roles': <String>['learner'],
    'isUserAdmin': false,
    'joinDate': 1700000000000,
    'firstName': ?firstName,
    'lastName': ?lastName,
    'planetCode': 'gua',
    'parentCode': 'ole',
    'password_scheme': 'pbkdf2',
    'iterations': '10',
    'derived_key': 'deadbeef',
    'salt': 'cafe',
    '_attachments': ?attachments,
  };

  void stubWalk(List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(
        '$dbUrl/tablet_users/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': docs.length}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('tablet_users/_all_docs?include_docs=true')),
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

  test('pulls the planet accounts a device has never signed in as', () async {
    stubWalk([
      userDoc('ada', firstName: 'Ada', lastName: 'Lovelace'),
      userDoc('bob', firstName: 'Bob'),
    ]);

    final result = await repository.syncTabletUsers(config: config);

    expect(result, isA<SyncComplete>());
    final ada = await db.userDao.getById('org.couchdb.user:ada');
    expect(ada, isNotNull);
    expect(ada!.firstName, 'Ada');
    expect(ada.lastName, 'Lovelace');
    expect(ada.rolesList, ['learner']);
    expect(ada.derivedKey, 'deadbeef');
    expect(await db.userDao.count(), 2);
  });

  test('skips _design documents', () async {
    stubWalk([
      {'_id': '_design/users', 'name': 'design'},
      userDoc('ada'),
    ]);

    await repository.syncTabletUsers(config: config);

    expect(await db.userDao.count(), 1);
    expect(await db.userDao.getById('_design/users'), isNull);
  });

  test('stores the profile photo as its attachment name', () async {
    stubWalk([
      userDoc(
        'ada',
        attachments: {
          'img': {'content_type': 'image/png', 'length': 12, 'stub': true},
        },
      ),
    ]);

    await repository.syncTabletUsers(config: config);

    expect(
      (await db.userDao.getById('org.couchdb.user:ada'))!.userImage,
      'img',
    );
  });

  test('a member registered on this device keeps their row, their local id and '
      'their health key', () async {
    // The Phase 107 identity rule: an offline registration keeps a
    // locally-minted id and gains a `couchId` when the upload lands. Keying
    // the pulled document on `_id` would give them a second row carrying no
    // `key`/`iv`, and `UserDao.getById` matches either — which makes their
    // encrypted health records unreadable.
    await db.userDao.upsert(
      UsersCompanion.insert(
        id: '1756900000000',
        couchId: const Value('org.couchdb.user:ada'),
        name: const Value('ada'),
        key: const Value('local-aes-key'),
        iv: const Value('local-iv'),
      ),
    );

    stubWalk([userDoc('ada', firstName: 'Ada')]);
    await repository.syncTabletUsers(config: config);

    expect(await db.userDao.count(), 1);
    final row = await db.userDao.getById('org.couchdb.user:ada');
    expect(row!.id, '1756900000000');
    expect(row.firstName, 'Ada');
    expect(row.key, 'local-aes-key');
    expect(row.iv, 'local-iv');
  });

  test('a document that omits a profile field keeps the stored one', () async {
    await db.userDao.upsert(
      UsersCompanion.insert(
        id: 'org.couchdb.user:ada',
        couchId: const Value('org.couchdb.user:ada'),
        name: const Value('ada'),
        phoneNumber: const Value('555-0100'),
      ),
    );

    // `applyJsonToUser`'s `if (new.isNotEmpty() || old.isNullOrEmpty())`.
    stubWalk([userDoc('ada', firstName: 'Ada')]);
    await repository.syncTabletUsers(config: config);

    expect(
      (await db.userDao.getById('org.couchdb.user:ada'))!.phoneNumber,
      '555-0100',
    );
  });

  test('adopts a guest row rather than duplicating the account', () async {
    // `UserRepositoryImpl.kt:1114-1128`: the guest is re-keyed to the server id
    // and its old row deleted, so the person keeps one row across the
    // transition from guest to member.
    await db.userDao.upsert(
      UsersCompanion.insert(
        id: 'guest_ada',
        couchId: const Value('guest_ada'),
        name: const Value('ada'),
        key: const Value('guest-key'),
        iv: const Value('guest-iv'),
      ),
    );

    stubWalk([userDoc('ada', firstName: 'Ada')]);
    await repository.syncTabletUsers(config: config);

    expect(await db.userDao.count(), 1);
    final rows = await db.userDao.getAllUsers();
    expect(rows.single.id, 'org.couchdb.user:ada');
    expect(rows.single.firstName, 'Ada');
    // The device-only columns survive the re-key: the row is rebuilt under a
    // new key, so anything `Value.absent()` would silently take its default.
    expect(rows.single.key, 'guest-key');
    expect(rows.single.iv, 'guest-iv');
  });

  test('never prunes: an account the walk did not list survives', () async {
    // Kotlin's walk issues no delete, and it could not — this table also holds
    // accounts registered offline, and the session is restored by looking the
    // signed-in user up here.
    await db.userDao.upsert(
      UsersCompanion.insert(
        id: '1756900000000',
        name: const Value('offline-only'),
      ),
    );

    stubWalk([userDoc('ada')]);
    await repository.syncTabletUsers(config: config);

    expect(await db.userDao.getById('1756900000000'), isNotNull);
    expect(await db.userDao.count(), 2);
  });
}
