import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/apk_log_uploader.dart';
import 'package:myplanet/repository/diagnostics_repository.dart';
import 'package:myplanet/repository/outbox_repository.dart';

import 'device_identity_fixture.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// `ApkLogUploader` — the port of `UploadConfigs.CrashLog`.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  late AppDatabase db;
  late _MockPlanetApi api;
  late DiagnosticsRepository repo;
  late OutboxRepository outbox;
  late ApkLogUploader uploader;

  setUpAll(() => registerFallbackValue(<String, dynamic>{}));

  setUp(() {
    db = AppDatabase.memory();
    api = _MockPlanetApi();
    repo = DiagnosticsRepository(db.apkLogDao);
    outbox = OutboxRepository(db.outboxDao);
    uploader = ApkLogUploader(repo, api, outbox, testDeviceIdentity);
  });
  tearDown(() => db.close());

  Future<void> seed(String id, {String rev = ''}) => db.apkLogDao.insert(
    ApkLogsCompanion.insert(
      id: id,
      rev: Value(rev),
      userId: const Value('u1'),
      type: const Value('crash'),
      error: const Value('boom'),
      parentCode: const Value('parent'),
      version: const Value('0.72.69'),
      createdOn: const Value('planet'),
      time: const Value('1700'),
    ),
  );

  test('the endpoint is the credential-free db url plus apk_logs', () {
    // Kotlin builds `"$baseUrl/${config.endpoint}"` where `baseUrl` is
    // `UrlUtils.dbUrl` — the configured server URL with `/db` appended if
    // absent (`UrlUtils:139-142`, `:182-184`). The credential belongs on the
    // header, never in the URL, which is what `credentialFreeDbUrl` is for.
    expect(
      ApkLogUploader.endpointFor(config),
      'https://planet.example.org/db/apk_logs',
    );
  });

  test('serialize is the row plus the four device fields', () async {
    await seed('a');
    final row = (await repo.pendingUploads()).single;

    final doc = ApkLogUploader.serialize(
      row,
      identity: await testDeviceIdentity.read(),
    );

    expect(doc, {
      'type': 'crash',
      'error': 'boom',
      'page': '',
      'time': '1700',
      'userId': 'u1',
      'version': '0.72.69',
      'createdOn': 'planet',
      'parentCode': 'parent',
      ...testDeviceFields,
    });
    // `addDocumentOrigin()` with no argument, so the **composite**
    // `<ANDROID_ID>_<Build.ID>` — unlike `SearchActivity.serialize`, which
    // passes the bare id. The two shapes are deliberately different and
    // Planet aggregates by this field.
    expect(doc['androidId'], 'android-id_build-id');
    // The raw preference, with no `ifEmpty { getDeviceName() }` fallback —
    // that belongs to `LoginActivity:686` alone.
    expect(doc['customDeviceName'], 'classroom tablet');
  });

  group('queuePending', () {
    test('queues every unsent row and nothing else', () async {
      await seed('a');
      await seed('b', rev: '1-already-sent');
      await seed('c');

      expect(await uploader.queuePending(config: config, userId: 'u1'), 2);

      expect(
        await db.outboxDao.forItem(ApkLogUploader.type, 'a'),
        hasLength(1),
      );
      expect(
        await db.outboxDao.forItem(ApkLogUploader.type, 'c'),
        hasLength(1),
      );
      expect(await db.outboxDao.forItem(ApkLogUploader.type, 'b'), isEmpty);
    });

    test('queues with no user, because Kotlin does not filter', () async {
      // `RoomUploadConfig` has no `filterGuests` field, so `shouldFilter`
      // takes the interface default `false`. A handset whose session has gone
      // is exactly the one holding a report nobody has seen.
      await seed('a');

      expect(await uploader.queuePending(config: config, userId: null), 1);
    });

    test('is a no-op with nothing pending', () async {
      await seed('a', rev: '1-sent');

      expect(await uploader.queuePending(config: config, userId: 'u1'), 0);
      expect(await db.outboxDao.due(9999), isEmpty);
    });
  });

  group('handler', () {
    OutboxRow rowFor(String itemId) => OutboxRow(
      id: 'op-1',
      uploadType: ApkLogUploader.type,
      itemId: itemId,
      endpoint: ApkLogUploader.endpointFor(config),
      payload: '{}',
      httpMethod: 'POST',
      attemptCount: 0,
      maxAttempts: 5,
      status: 'pending',
      createdAt: 0,
      nextAttemptAt: 0,
      lastAttemptAt: 0,
    );

    test('writes the revision back so the row stops being pending', () async {
      // The whole reason this handler exists. Kotlin's retry path
      // (`RetryRepositoryImpl:67-125`) re-POSTs and marks only its own retry
      // row complete, never writing `_rev`, so the log is still pending and
      // the next sweep files a **second** document — CouchDB mints a fresh id
      // each time because no `_id` is sent.
      await seed('a');
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess({'id': 'x', 'rev': '1-abc'}),
      );

      final result = await uploader.handler(rowFor('a'), const {}, 'Basic x');

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      expect((await db.apkLogDao.getById('a'))!.rev, '1-abc');
      expect(await repo.pendingUploads(), isEmpty);
    });

    // **The empty-string case is the one Kotlin actually produces**, and the
    // first cut of this group tested only the missing-key case — which is
    // green whether the `rev.isEmpty` half of the guard exists or not, because
    // a missing key is already `is! String`. Mutating the guard is what found
    // that; re-reading the test would not have. Both cases run now, and the
    // empty one is the load-bearing member of the pair.
    for (final (label, body) in [
      ('carries no rev at all', {'ok': true}),
      (
        'carries an empty rev, as JsonUtils.getString produces',
        {'id': 'doc-1', 'rev': ''},
      ),
    ]) {
      test('refuses a 2xx that $label', () async {
        // Kotlin's `JsonUtils.getString` returns `""` for a missing or
        // non-string field, which is non-null, so `normalizeUploadResult`
        // dequeues a row whose document may not exist. The port refuses
        // instead, as the sibling uploaders do.
        await seed('a');
        when(
          () => api.postJsonObject(
            any(),
            any(),
            authHeader: any(named: 'authHeader'),
          ),
        ).thenAnswer((_) async => NetworkSuccess(body));

        final result = await uploader.handler(rowFor('a'), const {}, null);

        expect(result, isA<NetworkError>());
        expect(
          await repo.pendingUploads(),
          hasLength(1),
          reason: 'a row must not be dequeued by a response that said nothing',
        );
        expect(
          (await db.apkLogDao.getById('a'))!.rev,
          '',
          reason: 'an empty rev must not be written as if it were one',
        );
      });
    }

    test('refuses when the local update applied to no row', () async {
      // `markUploadedBatch`'s contract: the ids it could not apply come back,
      // and `UploadConfigs.CrashLog` turns them into upload failures rather
      // than recording a delivery against a row that is gone.
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess({'id': 'x', 'rev': '1-abc'}),
      );

      final result = await uploader.handler(rowFor('vanished'), const {}, null);

      expect(result, isA<NetworkError>());
    });

    test('passes a transport failure through untouched', () async {
      await seed('a');
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async => const NetworkError(503, 'unavailable'));

      final result = await uploader.handler(rowFor('a'), const {}, null);

      expect(result, isA<NetworkError>());
      expect(
        (await db.apkLogDao.getById('a'))!.rev,
        '',
        reason: 'a refused send must leave the row pending',
      );
    });
  });
}
