import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/team_tasks_repository.dart';
import 'package:myplanet/repository/team_tasks_uploader.dart';

import '../support/mock_planet_api.dart';
import 'device_identity_fixture.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late TeamTasksRepository tasks;
  late OutboxRepository outbox;
  late TeamTasksUploader uploader;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    tasks = TeamTasksRepository(
      api,
      database.teamTaskDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(500),
      createId: () => 'local-task',
    );
    outbox = OutboxRepository(database.outboxDao);
    uploader = TeamTasksUploader(api, tasks, outbox, testDeviceIdentity);
  });
  tearDown(() => database.close());

  Future<void> seedPending() => tasks.create(
    teamId: 'team-1',
    title: 'Fix the pump',
    description: 'Before the rains',
    deadline: 900,
  );

  test('the endpoint carries no credentials', () {
    // It is persisted in `outbox`, which survives schema upgrades; the PIN
    // travels as a header at send time instead.
    final endpoint = TeamTasksUploader.endpointFor(config);
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, endsWith('/tasks'));
  });

  test('an edited task conflicting on its revision is re-sent', () async {
    // `pending()` is `isUpdated = true` unfiltered, so a task already carrying
    // a `docId` is re-offered after any edit — and that request is an update
    // whose `_rev` can be stale.
    await seedPending();
    final created = (await tasks.pending()).single;
    await tasks.markUploaded(created.id, 'task-couch', '1-stale');
    await tasks.update(
      created.id,
      title: 'Fix the pump today',
      description: 'Before the rains',
      deadline: 900,
    );

    await uploader.queuePending(config: config);
    final operation = (await outbox.due()).single;
    final payload = jsonDecode(operation.payload) as Map<String, dynamic>;
    expect(payload['_id'], 'task-couch');
    expect(payload['_rev'], '1-stale');

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
      return body['_rev'] == '2-server'
          ? NetworkSuccess<Map<String, dynamic>>({
              'id': 'task-couch',
              'rev': '3-g',
            })
          : const NetworkError<Map<String, dynamic>>(409, 'conflict');
    });
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        '_id': 'task-couch',
        '_rev': '2-server',
      }),
    );

    final result = await uploader.handler(operation, payload, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(sent, hasLength(2));
    expect(sent[1]['title'], 'Fix the pump today');
  });

  test('the queued task is stamped with its origin', () async {
    // `TeamTask.serialize` gained `addDocumentOrigin()` in `27c0470` — a
    // new-stamp site, so both `androidId` and `app` are new on the wire and
    // the Kotlin sends no device name alongside them.
    await seedPending();

    await uploader.queuePending(config: config, userId: 'user-1');
    final entry = await database.outboxDao.findOpen(
      TeamTasksUploader.type,
      'local-task',
    );

    final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(doc['title'], 'Fix the pump');
    expect(doc['androidId'], 'android-id_build-id');
    expect(doc['app'], 'myplanet');
    expect(doc.containsKey('deviceName'), isFalse);
    expect(doc.containsKey('customDeviceName'), isFalse);
  });
}
