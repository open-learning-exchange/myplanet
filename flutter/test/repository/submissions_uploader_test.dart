import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/submissions_uploader.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late SubmissionsRepository repository;
  late OutboxRepository outbox;
  late SubmissionsUploader uploader;
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = SubmissionsRepository(
      api,
      db.submissionDao,
      db.submitPhotosDao,
      db.surveyDao,
      db.examDao,
    );
    outbox = OutboxRepository(db.outboxDao);
    uploader = SubmissionsUploader(api, repository, outbox, testDeviceIdentity);
  });
  tearDown(() => db.close());

  test('queues a draft once and adopts CouchDB ids after upload', () async {
    final id = await repository.createDraft(
      userId: 'user-1',
      type: 'exam',
      title: 'Draft',
      answers: const [],
    );
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    final operation = (await outbox.due()).single;
    final queuedDoc = jsonDecode(operation.payload) as Map<String, dynamic>;
    for (final field in testDeviceFields.entries) {
      expect(queuedDoc, containsPair(field.key, field.value));
    }
    when(
      () => api.postJsonObject(
        operation.endpoint,
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'server-id',
        'rev': '1-rev',
      }),
    );

    final result = await uploader.handler(operation, const {}, 'Basic test');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(await repository.pendingUploads(), isEmpty);
    expect((await repository.getById(id))?.couchId, 'server-id');
  });

  test('an already-uploaded submission is updated, not duplicated', () async {
    // A submission the server already holds, marked dirty again — which is
    // exactly what `upsertDocuments` produces when a synced document carries
    // `isUpdated: true`.
    await db.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 'local-1',
        couchId: const Value('srv-1'),
        rev: const Value('3-abc'),
        userId: const Value('user-1'),
        type: const Value('survey'),
        uploaded: const Value(true),
        isUpdated: const Value(true),
      ),
    ]);

    final payload = await repository.serialize(
      (await db.submissionDao.pendingUploads()).single,
    );

    // Kotlin's serializeSubmission adds both when present; CouchDB needs them
    // to treat the POST as an update rather than minting a second document.
    expect(payload['_id'], 'srv-1');
    expect(payload['_rev'], '3-abc');
  });

  test('a never-uploaded draft carries no _id or _rev', () async {
    final id = await repository.createDraft(
      userId: 'user-1',
      type: 'exam',
      title: 'Fresh',
      answers: const [],
    );

    final row = await db.submissionDao.getById(id);
    final payload = await repository.serialize(row!);

    expect(payload.containsKey('_id'), isFalse);
    expect(
      payload.containsKey('_rev'),
      isFalse,
      reason: 'sending a null _rev would make CouchDB reject the insert',
    );
  });
}
