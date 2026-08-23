import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/team_log_uploader.dart';
import 'package:myplanet/repository/teams_repository.dart';

import 'device_identity_fixture.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

const config = ServerConfig(
  serverUrl: 'https://planet.example',
  couchDbUrl: 'https://satellite:1234@planet.example:443',
  pin: '1234',
);

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late TeamsRepository repository;
  late OutboxRepository outbox;
  late TeamLogUploader uploader;

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = TeamsRepository(api, database.teamDao, database.teamLogDao);
    outbox = OutboxRepository(database.outboxDao);
    uploader = TeamLogUploader(
      api,
      repository,
      database.teamLogDao,
      outbox,
      testDeviceIdentity,
    );
    registerFallbackValue(<String, dynamic>{});
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String itemId) => OutboxRow(
    id: 'op-1',
    uploadType: TeamLogUploader.type,
    itemId: itemId,
    payload: '{}',
    endpoint: 'https://planet.example/db/team_activities',
    httpMethod: 'POST',
    status: 'in_progress',
    attemptCount: 0,
    maxAttempts: 5,
    createdAt: 0,
    lastAttemptAt: 0,
    nextAttemptAt: 0,
  );

  test('the endpoint is credential-free and points at team_activities', () {
    expect(
      TeamLogUploader.endpointFor(config),
      'https://planet.example/db/team_activities',
    );
  });

  test(
    'queuePending enqueues every un-uploaded visit with device identity',
    () async {
      await repository.logTeamVisit(
        teamId: 'team-1',
        userName: 'ada',
        userPlanetCode: 'earth',
        userParentCode: 'sol',
        teamType: 'team',
      );
      // An already-uploaded row is not re-queued.
      final second = await repository.logTeamVisit(
        teamId: 'team-2',
        userName: 'ada',
        teamType: 'team',
      );
      await database.teamLogDao.markUploaded(second!, 'couch-1', '1-a');

      final queued = await uploader.queuePending(config: config, userId: 'u-1');

      expect(queued, 1);
      final due = await database.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch + 1000,
      );
      final entry = due.single;
      expect(entry.uploadType, TeamLogUploader.type);
      expect(entry.endpoint, 'https://planet.example/db/team_activities');
      expect(entry.userId, 'u-1');
      final payload = jsonDecode(entry.payload) as Map<String, dynamic>;
      expect(payload['type'], 'teamVisit');
      expect(payload['teamId'], 'team-1');
      expect(payload['user'], 'ada');
      expect(payload['createdOn'], 'earth');
      expect(payload['parentCode'], 'sol');
      expect(payload['androidId'], 'android-id_build-id');
      expect(payload['deviceName'], 'TEST DEVICE');
      expect(payload['customDeviceName'], 'classroom tablet');
    },
  );

  test(
    'handler POSTs and marks the row uploaded with the returned id/rev',
    () async {
      final id = await repository.logTeamVisit(
        teamId: 'team-1',
        userName: 'ada',
        teamType: 'team',
      );
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => NetworkSuccess<Map<String, dynamic>>({
          'id': 'couch-1',
          'rev': '1-a',
          'ok': true,
        }),
      );

      final result = await uploader.handler(rowFor(id!), {
        'teamId': 'team-1',
      }, 'auth');

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      final rows = await repository.pendingTeamLogUploads();
      expect(rows, isEmpty);
    },
  );

  test(
    'handler does not mark uploaded when the response lacks id/rev',
    () async {
      final id = await repository.logTeamVisit(
        teamId: 'team-1',
        userName: 'ada',
        teamType: 'team',
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

      final result = await uploader.handler(rowFor(id!), {
        'teamId': 'team-1',
      }, 'auth');

      expect(result, isA<NetworkError<Map<String, dynamic>>>());
      final rows = await repository.pendingTeamLogUploads();
      expect(rows.length, 1);
    },
  );
}
