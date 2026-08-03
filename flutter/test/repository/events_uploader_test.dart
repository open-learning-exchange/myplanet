import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/events_repository.dart';
import 'package:myplanet/repository/events_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late EventsRepository events;
  late OutboxRepository outbox;
  late EventsUploader uploader;
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    events = EventsRepository(
      api,
      database.meetupDao,
      createId: () => 'local-1',
    );
    outbox = OutboxRepository(database.outboxDao);
    uploader = EventsUploader(api, events, outbox);
  });
  tearDown(() => database.close());

  test('queues once and adopts CouchDB identity after upload', () async {
    await events.create(
      title: 'Meetup',
      description: '',
      startDate: 0,
      endDate: 0,
      startTime: '',
      endTime: '',
      location: '',
      link: '',
      recurring: 'none',
      creator: 'Ada',
    );

    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    final operation = (await outbox.due()).single;
    when(
      () => api.postJsonObject(
        operation.endpoint,
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'remote-1',
        'rev': '1-abc',
      }),
    );

    await uploader.handler(operation, const {});

    expect(await events.pendingUploads(), isEmpty);
    final uploaded = await events.getById('local-1');
    expect(uploaded?.meetupId, 'remote-1');
    expect(uploaded?.meetupIdRev, '1-abc');
  });
}
