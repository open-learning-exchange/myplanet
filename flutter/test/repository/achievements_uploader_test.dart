import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/achievements_repository.dart';
import 'package:myplanet/repository/achievements_uploader.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late AchievementsRepository repository;
  late OutboxRepository outbox;
  late AchievementsUploader uploader;
  var resumeBytes = <String, List<int>?>{};

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = AchievementsRepository(api, database.achievementDao);
    outbox = OutboxRepository(database.outboxDao);
    uploader = AchievementsUploader(
      api,
      repository,
      database.achievementDao,
      outbox,
      readResumeBytes: (name) async => resumeBytes[name],
    );
    resumeBytes = {};
  });

  tearDown(() => database.close());

  void stubPut(NetworkResult<Map<String, dynamic>> result) {
    when(
      () =>
          api.putJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((_) async => result);
  }

  /// Queues the pending ledger and returns the single outbox row it made.
  Future<OutboxRow> queueAndTake() async {
    await uploader.queuePending(config: config);
    return (await outbox.due()).single;
  }

  OutboxDrainer drainer() => OutboxDrainer(
    api,
    outbox,
    handlers: {AchievementsUploader.type: uploader.handler},
  );

  test(
    'queuePending enqueues the serialized ledger for each pending row',
    () async {
      await repository.update(
        'ada@earth',
        const AchievementInput(
          achievementsJson: '[{"title":"First"}]',
          resumeFileName: 'cv.pdf',
        ),
      );
      await repository.update(
        'mo@earth',
        const AchievementInput(achievementsJson: '[{"title":"Other"}]'),
      );
      await database.achievementDao.markUploaded('mo@earth', 'couch-mo', '1-r');

      final queued = await uploader.queuePending(config: config);
      expect(queued, 1);

      final rows = await outbox.due();
      expect(rows, hasLength(1));
      final payload = jsonDecode(rows.single.payload) as Map<String, dynamic>;
      expect(rows.single.itemId, 'ada@earth');
      expect(payload['achievements'], isList);
      expect(payload['resumeFileName'], 'cv.pdf');
      expect(rows.single.endpoint, AchievementsUploader.endpointFor(config));
    },
  );

  test('a conflicting ledger is re-sent under the server revision', () async {
    // The ledger is PUT to `achievements/<couchId>`, one document per user, so
    // a second device editing the same account conflicts. Adopting the
    // server's revision — Kotlin's arm — would clear `uploaded` with this
    // device's entries still on the handset.
    await repository.update(
      'ada@earth',
      const AchievementInput(achievementsJson: '[{"title":"First"}]'),
    );
    await (database.update(
      database.achievements,
    )..where((a) => a.id.equals('ada@earth'))).write(
      const AchievementsCompanion(
        couchId: Value('couch-ada'),
        rev: Value('1-stale'),
        uploaded: Value(false),
      ),
    );

    final sent = <Map<String, dynamic>>[];
    when(
      () =>
          api.putJsonObject(any(), any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final body = Map<String, dynamic>.from(
        invocation.positionalArguments[1] as Map<String, dynamic>,
      );
      sent.add(body);
      return body['_rev'] == '6-server'
          ? const NetworkSuccess<Map<String, dynamic>>({
              'id': 'couch-ada',
              'rev': '7-r',
            })
          : const NetworkError<Map<String, dynamic>>(409, 'conflict');
    });
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        '_id': 'couch-ada',
        '_rev': '6-server',
      }),
    );

    final row = (await outbox.due()).isEmpty
        ? await queueAndTake()
        : (await outbox.due()).single;
    final payload = jsonDecode(row.payload) as Map<String, dynamic>;
    final result = await uploader.handler(row, payload, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(sent, hasLength(2));
    expect(sent[1]['_rev'], '6-server');
    expect(sent[1]['achievements'], sent[0]['achievements']);
    final after = await database.achievementDao.getById('ada@earth');
    expect(after?.rev, '7-r');
    expect(after?.uploaded, isTrue);

    // The ledger's own URL is fetched — the PUT target, not a derivation.
    final url = verify(
      () =>
          api.getJsonObject(captureAny(), authHeader: any(named: 'authHeader')),
    ).captured.single;
    expect(
      url,
      '${AchievementsUploader.endpointFor(config)}/achievements/couch-ada',
    );
  });

  test(
    'handler PUTs the ledger, marks uploaded, and PUTs the resume bytes',
    () async {
      await repository.update(
        'ada@earth',
        const AchievementInput(
          achievementsJson: '[{"title":"First"}]',
          resumeFileName: 'cv.pdf',
        ),
      );
      // The edit arrived after the row already adopted a couch id and was
      // uploaded — the re-marked pend here is what drains.
      await (database.update(
        database.achievements,
      )..where((a) => a.id.equals('ada@earth'))).write(
        const AchievementsCompanion(
          couchId: Value('couch-ada'),
          rev: Value('1-r'),
          uploaded: Value(false),
        ),
      );

      resumeBytes['cv.pdf'] = [9, 9];
      stubPut(const NetworkSuccess({'id': 'couch-ada', 'rev': '2-r'}));
      when(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          ifMatch: any(named: 'ifMatch'),
          contentType: any(named: 'contentType'),
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess({'id': 'couch-ada', 'rev': '3-r'}),
      );

      await uploader.queuePending(config: config);
      await drainer().drain();

      final row = await database.achievementDao.getById('ada@earth');
      expect(row?.uploaded, isTrue);
      expect(row?.rev, '3-r');

      verify(
        () => api.uploadAttachment(
          '${AchievementsUploader.endpointFor(config)}/achievements/couch-ada/resume.pdf',
          bytes: [9, 9],
          authHeader: any(named: 'authHeader'),
          ifMatch: '2-r',
          contentType: 'application/pdf',
        ),
      ).called(1);
    },
  );

  test(
    'handler skips the resume attachment when the file is not on the device',
    () async {
      await repository.update(
        'mo@earth',
        const AchievementInput(
          achievementsJson: '[{"title":"Other"}]',
          resumeFileName: 'cv.pdf',
        ),
      );
      await (database.update(
        database.achievements,
      )..where((a) => a.id.equals('mo@earth'))).write(
        const AchievementsCompanion(
          couchId: Value('couch-mo'),
          rev: Value('1-r'),
          uploaded: Value(false),
        ),
      );

      stubPut(const NetworkSuccess({'id': 'couch-mo', 'rev': '2-r'}));

      await uploader.queuePending(config: config);
      final outcomes = await drainer().drain();

      expect(outcomes, [OutboxOutcome.completed]);
      verifyNever(
        () => api.uploadAttachment(
          any(),
          bytes: any(named: 'bytes'),
          authHeader: any(named: 'authHeader'),
          ifMatch: any(named: 'ifMatch'),
          contentType: any(named: 'contentType'),
        ),
      );
      expect(
        (await database.achievementDao.getById('mo@earth'))?.uploaded,
        isTrue,
      );
    },
  );

  test('handler PUTs a never-synced ledger to its derived id', () async {
    // The state the edit screen actually produces: `getOrInitialize` creates
    // the row with an empty couch id, and nothing fills it in before the
    // first upload. Kotlin's `_id` is the derived id, so this is the PUT that
    // creates the document — it used to be rejected as carrying no id, which
    // meant a first-time achievement never reached the server.
    await repository.update(
      'ada@earth',
      const AchievementInput(achievementsJson: '[{"title":"First"}]'),
    );

    stubPut(const NetworkSuccess({'id': 'ada@earth', 'rev': '1-r'}));

    await uploader.queuePending(config: config);
    final outcomes = await drainer().drain();

    expect(outcomes, [OutboxOutcome.completed]);
    verify(
      () => api.putJsonObject(
        '${AchievementsUploader.endpointFor(config)}/achievements/ada@earth',
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).called(1);
    final row = await database.achievementDao.getById('ada@earth');
    expect(row?.uploaded, isTrue);
    expect(row?.couchId, 'ada@earth');
    expect(row?.rev, '1-r');
  });

  test('handler abandons a payload that names no document', () async {
    // The guard still matters for a row an older build left in the outbox
    // with an empty `_id`.
    await outbox.enqueue(
      uploadType: AchievementsUploader.type,
      itemId: 'ada@earth',
      endpoint: AchievementsUploader.endpointFor(config),
      payload: const {'_id': ''},
    );

    final outcomes = await drainer().drain();
    expect(outcomes, [OutboxOutcome.abandoned]);
  });

  test('handler surfaces failure when the ledger PUT fails', () async {
    await repository.update('ada@earth', const AchievementInput());
    await (database.update(
      database.achievements,
    )..where((a) => a.id.equals('ada@earth'))).write(
      const AchievementsCompanion(
        couchId: Value('couch-ada'),
        rev: Value('1-r'),
        uploaded: Value(false),
      ),
    );

    stubPut(const NetworkError(500, 'server down'));

    await uploader.queuePending(config: config);
    final outcomes = await drainer().drain();
    expect(outcomes, [OutboxOutcome.retryScheduled]);
    expect(
      (await database.achievementDao.getById('ada@earth'))?.uploaded,
      isFalse,
    );
  });
}
