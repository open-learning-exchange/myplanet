import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/health_models.dart';
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

  test('a record refused ten times leaves one row and one POST', () async {
    // The accretion. `enqueue` used to look only for an *open* operation, so
    // an abandoned row never blocked a fresh one and every sweep minted
    // another dead row and made another doomed POST — in `outbox`, a table
    // schema bumps preserve. Ten sweeps rather than two, because one repeat is
    // not a demonstration of unboundedness.
    final legacyId = await seedLegacyPair();

    // `seedLegacyPair` leaves two dirty rows — the patient's profile and the
    // examination that collides with it — so the first sweep legitimately
    // POSTs twice. What must not grow is everything after it.
    await queueAndDrain();
    final afterFirstSweep = couch.postCount;
    expect(afterFirstSweep, 2);

    for (var sweep = 0; sweep < 9; sweep++) {
      await queueAndDrain();
    }

    expect(
      couch.postCount,
      afterFirstSweep,
      reason: 'the server was asked the identical question exactly once',
    );
    final rows = await database.outboxDao.forItem(
      HealthUploader.type,
      legacyId,
    );
    expect(rows, hasLength(1));
    expect(rows.single.status, OutboxDao.statusAbandoned);
    expect(rows.single.httpCode, 409, reason: 'still diagnosable');

    final abandoned = await database.outboxDao.abandoned(HealthUploader.type);
    expect(abandoned.map((row) => row.itemId), [legacyId]);
  });

  test('the clinician tapping Retry does override the memo', () async {
    // `MyHealthScreen`'s banner counts stranded records and offers Retry next
    // to the count. A sweep must not re-ask; a person who has just been told
    // something is wrong must be able to.
    final legacyId = await seedLegacyPair();

    await queueAndDrain();
    final afterFirstSweep = couch.postCount;
    await queueAndDrain();
    expect(couch.postCount, afterFirstSweep, reason: 'a sweep does not re-ask');

    await uploader.queuePending(
      config: config,
      userId: patientId,
      retryRefused: true,
    );
    await drainer.drain(authHeader: 'auth');

    expect(couch.postCount, afterFirstSweep + 1);
    // Still refused, so still one row and still reported.
    final rows = await database.outboxDao.forItem(
      HealthUploader.type,
      legacyId,
    );
    expect(rows, hasLength(1));
    expect(rows.single.status, OutboxDao.statusAbandoned);
  });

  test('an accepted response with no revision is never re-sent', () async {
    // Job 1. The transport called the send a success, so the examination may
    // be on the server; asking again could file it twice. The row is abandoned
    // once, with a code that says no HTTP status was the reason, and no sweep
    // re-arms it.
    final legacyId = await seedLegacyPair();
    // The v45 repair first, so the examination owns its own document id and
    // the response shape is the only thing under test — otherwise it collides
    // with the profile and earns a 409 instead.
    await database.customStatement('SELECT 1');
    await database.migration.onUpgrade(database.createMigrator(), 44, 45);
    couch.omitRevOnPost = true;

    await queueAndDrain();
    final afterFirstSweep = couch.postCount;

    for (var sweep = 0; sweep < 4; sweep++) {
      await queueAndDrain();
    }

    expect(couch.postCount, afterFirstSweep);
    expect(
      couch.documents.containsKey(legacyId),
      isTrue,
      reason: 'the server did accept it — that is what makes re-sending unsafe',
    );
    final rows = await database.outboxDao.forItem(
      HealthUploader.type,
      legacyId,
    );
    expect(rows, hasLength(1));
    expect(rows.single.status, OutboxDao.statusAbandoned);
    expect(rows.single.httpCode, OutboxRepository.noUsableResponse);
    expect(rows.single.errorMessage, 'Upload response carried no rev');

    // And the revision the row already held is untouched: `markUploaded` is
    // not called with a null, which would erase it.
    expect((await repository.getById(legacyId))!.isUpdated, isTrue);
  });

  test('a delivered record stops being reported as stranded', () async {
    // The upgrade sequence a real device sees: the record is refused first, the
    // repair lands later. Nothing sweeps an abandoned row — `enqueue` never
    // reuses one and `cleanup()` has no caller — so without withdrawing them on
    // success the screen would warn about a record that is on the server, for
    // the life of the install, with no action to offer.
    final legacyId = await seedLegacyPair();

    await queueAndDrain();
    expect(await database.outboxDao.abandoned(HealthUploader.type), isNotEmpty);

    await database.customStatement('SELECT 1');
    await database.migration.onUpgrade(database.createMigrator(), 44, 45);
    await queueAndDrain();

    expect(couch.documents.containsKey(legacyId), isTrue);
    expect(
      await database.outboxDao.abandoned(HealthUploader.type),
      isEmpty,
      reason:
          'the record arrived, so the refusals that said it had not must go',
    );
  });

  test('a stale profile revision is recovered instead of stranded', () async {
    // Phase 152's headline, end to end through the real drain path.
    //
    // The profile blob is re-saved on every examination save under a stable
    // `_id`, so once published its next write is an update — and
    // `cacheDocuments` skips a locally dirty row
    // (`health_repository.dart:772`), so nothing on the device can refresh
    // the revision it carries. Before the recovery arm, a revision that went
    // stale on another handset stranded every subsequent edit on this one,
    // for the life of the install.
    await repository.saveHealthProfileBlob(
      patientId,
      HealthRepository.initHealth(),
    );
    await queueAndDrain();
    expect((await repository.getById(patientId))!.rev, isNotNull);

    // Another clinician writes the same document, so this device's stored
    // revision is now behind.
    couch.documents[patientId] = {
      ...couch.documents[patientId]!,
      '_rev': '99-elsewhere',
    };

    // This device edits the profile again.
    await repository.saveHealthProfileBlob(
      patientId,
      MyHealth(
        profile: MyHealthProfile(emergencyContactName: 'Grace'),
        userKey: HealthRepository.initHealth().userKey,
        lastExamination: DateTime.now().millisecondsSinceEpoch,
      ),
    );
    final postsBefore = couch.postCount;

    await queueAndDrain();

    // One GET and one re-send: the edit is on the server, not merely
    // reported as delivered.
    expect(couch.getCount, 1);
    expect(couch.postCount, postsBefore + 2);
    expect(couch.documents[patientId]!['_rev'], isNot('99-elsewhere'));
    final stored = await repository.getById(patientId);
    expect(stored!.isUpdated, isFalse);
    expect(stored.rev, couch.documents[patientId]!['_rev']);
    // And the queue is empty — no memo, because nothing was refused.
    expect(
      await database.outboxDao.forItem(HealthUploader.type, patientId),
      isEmpty,
    );
  });

  test('creatorId is serialized from profileId, as Kotlin does', () async {
    // `JsonUtils.addString(object, "creatorId", health.profileId)`
    // (`HealthExamination.kt:111`). The columns are equal for anything either
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
    // The document read the 409 recovery arm makes. Without it the arm's
    // fetch would throw and the tests below would pass because of
    // `ConflictRecovery`'s guard rather than because a create-conflict stands
    // as a refusal — which is the thing they are meant to pin.
    when(
      () => getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      getCount++;
      final url = invocation.positionalArguments[0] as String;
      final id = Uri.decodeComponent(url.split('/').last);
      final doc = documents[id];
      return doc == null
          ? const NetworkError(404, 'not_found')
          : NetworkSuccess({'_id': id, ...doc});
    });
  }

  final Map<String, Map<String, dynamic>> documents = {};
  var _rev = 0;

  /// Every POST that reached this fake, so a test can assert that a refused
  /// request was not asked a second time.
  var postCount = 0;

  /// Every document read, so a test can tell a recovery that was attempted
  /// from one that never fired.
  var getCount = 0;

  /// Answers an accepted write with `id` and no `rev` — the shape
  /// `HealthUploader`'s handler refuses to interpret.
  var omitRevOnPost = false;

  NetworkResult<Map<String, dynamic>> _post(Map<String, dynamic> body) {
    postCount++;
    final id = body['_id'] as String?;
    if (id == null) {
      final minted = 'minted-${documents.length}';
      documents[minted] = {...body};
      if (omitRevOnPost) return NetworkSuccess({'id': minted});
      return NetworkSuccess({'id': minted, 'rev': '1-${++_rev}'});
    }
    final existing = documents[id];
    if (existing != null && existing['_rev'] != body['_rev']) {
      return const NetworkError(409, 'conflict');
    }
    final rev = '${++_rev}-x';
    documents[id] = {...body, '_rev': rev};
    if (omitRevOnPost) return NetworkSuccess({'id': id});
    return NetworkSuccess({'id': id, 'rev': rev});
  }
}
