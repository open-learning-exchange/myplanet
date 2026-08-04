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
