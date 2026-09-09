import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/crypto/health_cipher.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:myplanet/repository/health_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late HealthRepository repository;
  late HealthUploader uploader;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() async {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
    repository = HealthRepository(
      api,
      database.healthExaminationDao,
      database.userDao,
    );
    uploader = HealthUploader(
      api,
      repository,
      database.healthExaminationDao,
      OutboxRepository(database.outboxDao),
    );
    await database.userDao.upsert(
      UsersCompanion.insert(id: 'user-1', name: const Value('ada')),
    );
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: HealthUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: HealthUploader.endpointFor(config),
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  test('the endpoint carries no credentials', () {
    final endpoint = HealthUploader.endpointFor(config);
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, endsWith('/health'));
  });

  test('a recorded examination is queued for the server', () async {
    // Recording one used to be the end of the story: `getUpdated()` existed
    // and nothing called it, so the reading never left the handset.
    await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 70,
      height: 170,
      weight: 65,
    );

    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    final entries = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(entries.map((row) => row.uploadType), [HealthUploader.type]);
  });

  test('what reaches the outbox is ciphertext, not the diagnosis', () async {
    final plain = jsonEncode({'diagnosis': 'asthma'});
    final encrypted = await repository.encryptData('user-1', plain);
    final id = await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 70,
      height: 170,
      weight: 65,
      data: encrypted,
    );

    await uploader.queuePending(config: config, userId: 'user-1');
    final entry = await database.outboxDao.findOpen(HealthUploader.type, id);

    expect(entry, isNotNull);
    // The payload is what travels to CouchDB and sits in its document store.
    expect(entry!.payload, isNot(contains('asthma')));
    final data = jsonDecode(entry.payload)['data'] as String;
    final user = await database.userDao.getById('user-1');
    expect(HealthCipher.decrypt(data, user!.key, user.iv), plain);
  });

  test(
    'a successful upload records the revision and stops re-queuing',
    () async {
      final id = await repository.createExamination(
        userId: 'user-1',
        temperature: 36.5,
        pulse: 70,
        height: 170,
        weight: 65,
      );
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({'rev': '1-a'}),
      );

      await uploader.handler(rowFor(id), {}, 'auth');

      final row = await repository.getById(id);
      expect(row!.rev, '1-a');
      // Without clearing `isUpdated` the same reading is posted on every drain.
      expect(row.isUpdated, isFalse);
      expect(await uploader.queuePending(config: config, userId: 'user-1'), 0);
    },
  );

  test(
    'a conflicting profile blob is re-sent under the server revision',
    () async {
      // **The reachable case, and not the one an earlier draft of this test
      // claimed.** An *examination* cannot conflict with a previous one:
      // `createExamination` defaults `userId` to the row's own generated id
      // (`health_repository.dart:95-100`) and the only production caller passes
      // none (`health_provider.dart:470`), so each examination is its own
      // document. That is verbatim what `PHASE_148_NOTES.md` was written to
      // retract, and this file had re-seeded it.
      //
      // What is keyed on the patient is the **profile blob**:
      // `saveHealthProfileBlob` writes `id = patientId` and
      // `userId = user.couchId` (`health_repository.dart:283-296`), it is
      // re-saved on every examination save, and `serialize` sends `userId` as
      // `_id`. So its `_id` differs from its row id *and* its next write after
      // publication is an update — while `cacheDocuments` skips a locally dirty
      // row (`health_repository.dart:772`), so no pull can refresh the revision
      // it carries. Before the recovery arm that edit stayed on the handset for
      // the life of the install.
      await database.userDao.upsert(
        UsersCompanion.insert(
          id: 'patient-1',
          name: const Value('bea'),
          couchId: const Value('org.couchdb.user:bea'),
        ),
      );
      final row = await repository.saveHealthProfileBlob(
        'patient-1',
        HealthRepository.initHealth(),
      );
      await (database.update(database.healthExaminations)
            ..where((h) => h.id.equals(row!.id)))
          .write(const HealthExaminationsCompanion(rev: Value('2-stale')));
      final stored = await repository.getById('patient-1');
      final payload = HealthRepository.serialize(stored!);
      // The two ids differ — which is the whole reason the recovery URL must
      // come from the payload rather than from `outbox.itemId`.
      expect(stored.id, 'patient-1');
      expect(payload['_id'], 'org.couchdb.user:bea');
      expect(payload['_rev'], '2-stale', reason: 'an update, not a create');

      final sent = <Map<String, dynamic>>[];
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((invocation) async {
        final body = Map<String, dynamic>.from(
          invocation.positionalArguments[1] as Map<String, dynamic>,
        );
        sent.add(body);
        return body['_rev'] == '7-a'
            ? NetworkSuccess<Map<String, dynamic>>({
                'id': 'org.couchdb.user:bea',
                'rev': '8-b',
              })
            : const NetworkError<Map<String, dynamic>>(409, 'conflict');
      });
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          '_id': 'org.couchdb.user:bea',
          '_rev': '7-a',
        }),
      );

      final result = await uploader.handler(
        rowFor('patient-1'),
        payload,
        'auth',
      );

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      // The profile reached the server, rather than the server's `_rev` being
      // adopted over a document that never carried this device's edit.
      expect(sent, hasLength(2));
      expect(sent[1]['data'], payload['data']);
      expect(sent[1]['_rev'], '7-a');
      final after = await repository.getById('patient-1');
      expect(after!.rev, '8-b');
      expect(after.isUpdated, isFalse);

      // Fetched at the *payload's* `_id`, not the row's. Keyed on the row this
      // would GET a document that does not exist, take the 404, and quietly
      // return the original conflict for ever.
      final url = verify(
        () => api.getJsonObject(
          captureAny(),
          authHeader: any(named: 'authHeader'),
        ),
      ).captured.single;
      expect(url, endsWith('/health/org.couchdb.user%3Abea'));
      expect(url, isNot(contains('patient-1')));
    },
  );

  test('a response without a revision is not treated as uploaded', () async {
    final id = await repository.createExamination(
      userId: 'user-1',
      temperature: 36.5,
      pulse: 70,
      height: 170,
      weight: 65,
    );
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );

    final result = await uploader.handler(rowFor(id), {}, 'auth');

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect((await repository.getById(id))!.isUpdated, isTrue);
  });

  test(
    'the same key is reused, so yesterday’s records stay readable',
    () async {
      final first = await repository.encryptData('user-1', 'one');
      final second = await repository.encryptData('user-1', 'two');

      expect(await repository.decryptData('user-1', first), 'one');
      expect(await repository.decryptData('user-1', second), 'two');
    },
  );

  test('health examinations survive a schema upgrade', () {
    // They are recorded on the device and no sync brings them back — the test
    // the preserved-table list applies is "can the next sync restore this?".
    expect(AppDatabase.localAuthorityTables, contains('health_examinations'));
    // And the key that decrypts them is generated here, never uploaded.
    expect(AppDatabase.localAuthorityTables, contains('users'));
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
