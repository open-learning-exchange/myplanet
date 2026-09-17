import 'package:drift/drift.dart' hide isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/adopted_surveys_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// The 409 arm this uploader had first, and which Phase 152 moved into
/// [ConflictRecovery] so every uploader shares it.
///
/// It had **no test coverage at all** before this file: the sweep tests drive
/// `queuePending`, and the handler's conflict branch was reachable from
/// nothing. That is why the conversion needed pinning rather than trusting.
void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late AdoptedSurveysUploader uploader;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );
  const cloneId = 'survey-1_team-9';

  setUp(() async {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
    uploader = AdoptedSurveysUploader(
      api,
      database.surveyDao,
      OutboxRepository(database.outboxDao),
      testDeviceIdentity,
    );
    await database.surveyDao.upsertAll([
      SurveysCompanion.insert(
        id: cloneId,
        name: const Value('Water access'),
        sourceSurveyId: const Value('survey-1'),
        teamId: const Value('team-9'),
        needsSync: const Value(true),
      ),
    ], const {});
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: AdoptedSurveysUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: AdoptedSurveysUploader.endpointFor(config),
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  test('an accepted clone records its revision', () async {
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        'id': cloneId,
        'rev': '1-a',
      }),
    );

    final result = await uploader.handler(rowFor(cloneId), const {
      '_id': cloneId,
    }, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    final row = await (database.select(
      database.surveys,
    )..where((s) => s.id.equals(cloneId))).getSingle();
    expect(row.rev, '1-a');
    expect(row.needsSync, isFalse);
  });

  test('the leader who loses the race still publishes the clone', () async {
    // Two leaders adopt the same survey for the same team. The port's clone id
    // is deterministic (`'${surveyId}_$teamId'`) where Kotlin's is a random
    // UUID, so the second POST conflicts instead of writing a second document.
    // Before the arm existed the loser's row was abandoned terminally, and no
    // later walk could rescue it — a mapper's companion leaves `needsSync`
    // absent, so a re-pull hands the row a `rev` and leaves the flag set.
    //
    // This is the one uploader that *adopts*: the clone's content is a pure
    // function of the survey and the team, so the winner's document is the
    // loser's document and taking its revision loses nothing. Note the single
    // POST — no second write is made, which is what distinguishes this arm
    // from the update arm every other uploader gets.
    final sent = <Map<String, dynamic>>[];
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      sent.add(
        Map<String, dynamic>.from(
          invocation.positionalArguments[1] as Map<String, dynamic>,
        ),
      );
      return const NetworkError<Map<String, dynamic>>(409, 'conflict');
    });
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        '_id': cloneId,
        '_rev': '1-winner',
      }),
    );

    final result = await uploader.handler(rowFor(cloneId), const {
      '_id': cloneId,
      'name': 'Water access',
    }, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(sent, hasLength(1), reason: 'adopted, not re-sent');
    final row = await (database.select(
      database.surveys,
    )..where((s) => s.id.equals(cloneId))).getSingle();
    expect(row.rev, '1-winner');
    // The flag is what keeps the clone out of `SurveyDao.deleteNotIn`'s reach
    // once it is published; a stuck clone loses its members' answer sheets.
    expect(row.needsSync, isFalse);

    final url = verify(
      () =>
          api.getJsonObject(captureAny(), authHeader: any(named: 'authHeader')),
    ).captured.single;
    expect(url, endsWith('/exams/$cloneId'));
  });

  test('an edited clone is re-sent rather than adopted', () async {
    // Once the clone has a revision the request is an *update*, and adopting
    // would clear `needsSync` with this device's edit still on the handset.
    // The update arm applies here as it does everywhere else — `adoptExisting`
    // only changes what a create does.
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
      return body['_rev'] == '3-server'
          ? const NetworkSuccess<Map<String, dynamic>>({
              'id': cloneId,
              'rev': '4-b',
            })
          : const NetworkError<Map<String, dynamic>>(409, 'conflict');
    });
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        '_id': cloneId,
        '_rev': '3-server',
      }),
    );

    final result = await uploader.handler(rowFor(cloneId), const {
      '_id': cloneId,
      '_rev': '2-stale',
      'name': 'Water access, revised',
    }, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(sent, hasLength(2));
    expect(sent[1]['name'], 'Water access, revised');
    final row = await (database.select(
      database.surveys,
    )..where((s) => s.id.equals(cloneId))).getSingle();
    expect(row.rev, '4-b');
  });

  test(
    'a conflict the fetch cannot resolve leaves the clone unpublished',
    () async {
      // Clearing `needsSync` without a revision would drop the clone out of the
      // prune exemption while it is still, as far as this device knows, unsent.
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => const NetworkError<Map<String, dynamic>>(409, 'conflict'),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => const NetworkError<Map<String, dynamic>>(500, 'boom'),
      );

      final result = await uploader.handler(rowFor(cloneId), const {
        '_id': cloneId,
      }, 'auth');

      expect((result as NetworkError).code, 409);
      final row = await (database.select(
        database.surveys,
      )..where((s) => s.id.equals(cloneId))).getSingle();
      expect(row.needsSync, isTrue);
      expect(row.rev, isNull);
    },
  );
}
