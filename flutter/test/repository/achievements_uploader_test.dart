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
    repository = AchievementsRepository(database.achievementDao);
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

  test('handler abandons when the ledger has no couch id', () async {
    await repository.update(
      'ada@earth',
      const AchievementInput(achievementsJson: '[]'),
    );

    await uploader.queuePending(config: config);
    final outcomes = await drainer().drain();
    expect(outcomes, [OutboxOutcome.abandoned]);
    expect(
      (await database.achievementDao.getById('ada@earth'))?.uploaded,
      isFalse,
    );
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
