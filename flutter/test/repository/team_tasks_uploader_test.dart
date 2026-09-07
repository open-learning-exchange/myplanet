import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
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
