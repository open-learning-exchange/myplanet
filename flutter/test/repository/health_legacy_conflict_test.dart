import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/health_repository.dart';
import 'package:myplanet/repository/health_uploader.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';

/// Phase 107 found that a pre-Phase-105 examination row collides with the
/// patient's profile document, and that the loser of that collision takes a
/// 409 the outbox classifies as permanent. These tests hold the whole path —
/// two rows, one queue, one drain against a CouchDB that enforces `_id`
/// uniqueness — so the *record loss* is what is asserted, not the status code.
void main() {
  late AppDatabase database;
  late _FakeCouchDb couch;
  late HealthRepository repository;
  late HealthUploader uploader;
  late OutboxRepository outbox;
  late OutboxDrainer drainer;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  /// The signed-in health provider, synced, so their row key *is* their
  /// CouchDB id — the ordinary case, and the one that collides.
  const patientId = 'org.couchdb.user:ada';

  setUp(() async {
    database = AppDatabase.memory();
    couch = _FakeCouchDb();
    repository = HealthRepository(
      couch,
      database.healthExaminationDao,
      database.userDao,
    );
    outbox = OutboxRepository(database.outboxDao);
    uploader = HealthUploader(
      couch,
      repository,
      database.healthExaminationDao,
      outbox,
    );
    drainer = OutboxDrainer(
      couch,
      outbox,
      handlers: {HealthUploader.type: uploader.handler},
    );
    await database.userDao.upsert(
      UsersCompanion.insert(
        id: patientId,
        name: const Value('ada'),
        couchId: const Value(patientId),
      ),
    );
  });
  tearDown(() => database.close());

  /// The two rows a pre-Phase-105 device carries: the patient's profile row,
  /// and an examination whose `userId` was set to the patient rather than to
  /// its own id. Both serialize `_id` from `userId`, so both claim the same
  /// CouchDB document.
  Future<String> seedLegacyPair() async {
    await repository.saveHealthProfileBlob(
      patientId,
      HealthRepository.initHealth(),
    );
    const legacyId = 'health-1725000000000000';
    await database.healthExaminationDao.upsert(
      HealthExaminationsCompanion.insert(
        id: legacyId,
        // The defect: the patient's id, not the row's own.
        userId: const Value(patientId),
        pulse: const Value(72),
        temperature: const Value(36.6),
        data: Value(jsonEncode({'diagnosis': 'asthma'})),
        isUpdated: const Value(true),
      ),
    );
    return legacyId;
  }

  Future<void> queueAndDrain() async {
    await uploader.queuePending(config: config, userId: patientId);
    await drainer.drain(authHeader: 'auth');
  }

  test(
    'the legacy examination never reaches the server and nothing says so',
    () async {
      final legacyId = await seedLegacyPair();

      await queueAndDrain();

      // One document on the server, and it is the profile — the examination
      // lost the race for the same `_id`.
      expect(couch.documents.keys, [patientId]);
      expect(couch.documents[patientId]!['pulse'], isNot(72));

      // The row is still dirty, so the device believes it has work to do...
      expect((await repository.getById(legacyId))!.isUpdated, isTrue);
      // ...but the queue has given up on it permanently.
      final entries = await database.outboxDao.forItem(
        HealthUploader.type,
        legacyId,
      );
      expect(entries.single.status, OutboxDao.statusAbandoned);
      expect(entries.single.httpCode, 409);
    },
  );

  test('after the v45 repair the same reading reaches the server', () async {
    final legacyId = await seedLegacyPair();

    await database.customStatement('SELECT 1');
    await database.migration.onUpgrade(database.createMigrator(), 44, 45);

    await queueAndDrain();

    // Two documents now: the profile under the patient's id, and the
    // examination under its own.
    expect(couch.documents.keys, containsAll([patientId, legacyId]));
    expect(couch.documents[legacyId]!['pulse'], 72);
    // And the row is clean, so it is not queued again.
    expect((await repository.getById(legacyId))!.isUpdated, isFalse);
    expect(
      await database.outboxDao.forItem(HealthUploader.type, legacyId),
      isEmpty,
    );
  });

  test('a refused record is countable instead of silent', () async {
    final legacyId = await seedLegacyPair();

    await queueAndDrain();

    // What the health screen's caution banner reads. Before this there was no
    // query that selected an abandoned row at all, so the refusal existed only
    // as a status column nothing looked at.
    final abandoned = await database.outboxDao.abandoned(HealthUploader.type);
    expect(abandoned.map((row) => row.itemId), [legacyId]);
    expect(abandoned.single.httpCode, 409);
  });

  test('a record refused twice is one stranded record, not two', () async {
    // `enqueue` only looks for an *open* operation, so an abandoned row never
    // blocks a fresh one — a save re-queues the same doomed record and earns a
    // second abandoned row. Counting rows would tell the clinician the wrong
    // number and keep growing.
    final legacyId = await seedLegacyPair();

    await queueAndDrain();
    await queueAndDrain();

    final abandoned = await database.outboxDao.abandoned(HealthUploader.type);
    expect(abandoned.length, 2);
    expect(abandoned.map((row) => row.itemId).toSet(), {legacyId});
  });

  test('creatorId is serialized from profileId, as Kotlin does', () async {
    // `JsonUtils.addString(object, "creatorId", health.profileId)`
    // (`HealthExamination.kt:110`). The columns are equal for anything either
    // app authors, so this only bites on a document Planet wrote and this
    // device edited — where the port used to send the server's `creatorId`
    // back and Kotlin replaces it.
    final row = HealthExaminationRow(
      id: 'health-1',
      userId: 'health-1',
      temperature: 0,
      pulse: 0,
      height: 0,
      weight: 0,
      selfExamination: false,
      hasInfo: false,
      profileId: 'profile-key',
      creatorId: 'someone-else',
      age: 0,
      date: 0,
      isUpdated: true,
    );

    expect(HealthRepository.serialize(row)['creatorId'], 'profile-key');

    // And a row with no `profileId` sends no `creatorId` at all, because
    // `addString` skips a null.
    final orphan = HealthExaminationRow(
      id: 'health-2',
      userId: 'health-2',
      temperature: 0,
      pulse: 0,
      height: 0,
      weight: 0,
      selfExamination: false,
      hasInfo: false,
      creatorId: 'someone-else',
      age: 0,
      date: 0,
      isUpdated: true,
    );
    expect(
      HealthRepository.serialize(orphan).containsKey('creatorId'),
      isFalse,
    );
  });

  test('the repair leaves the patient profile row where it belongs', () async {
    await seedLegacyPair();

    await database.customStatement('SELECT 1');
    await database.migration.onUpgrade(database.createMigrator(), 44, 45);

    final profile = await repository.getById(patientId);
    expect(
      profile!.userId,
      patientId,
      reason: 'the profile document is keyed on the patient, not on a row id',
    );
  });
}

/// A CouchDB that enforces what the real one enforces: a POST naming an `_id`
/// that already exists, without the current `_rev`, is a 409.
class _FakeCouchDb extends Mock implements PlanetApi {
  _FakeCouchDb() {
    registerFallbackValue(<String, dynamic>{});
    when(
      () => postJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (invocation) async =>
          _post(invocation.positionalArguments[1] as Map<String, dynamic>),
    );
  }

  final Map<String, Map<String, dynamic>> documents = {};
  var _rev = 0;

  NetworkResult<Map<String, dynamic>> _post(Map<String, dynamic> body) {
    final id = body['_id'] as String?;
    if (id == null) {
      final minted = 'minted-${documents.length}';
      documents[minted] = {...body};
      return NetworkSuccess({'id': minted, 'rev': '1-${++_rev}'});
    }
    final existing = documents[id];
    if (existing != null && existing['_rev'] != body['_rev']) {
      return const NetworkError(409, 'conflict');
    }
    final rev = '${++_rev}-x';
    documents[id] = {...body, '_rev': rev};
    return NetworkSuccess({'id': id, 'rev': rev});
  }
}
