import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/personals_repository.dart';
import 'package:myplanet/repository/personals_uploader.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late PersonalsRepository personals;
  late OutboxRepository outbox;
  late PersonalsUploader uploader;
  var clock = DateTime.fromMillisecondsSinceEpoch(1000);

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    clock = DateTime.fromMillisecondsSinceEpoch(1000);
    var nextId = 0;
    personals = PersonalsRepository(
      database.personalDao,
      now: () => clock,
      createId: () => 'note-${nextId++}',
    );
    outbox = OutboxRepository(database.outboxDao, now: () => clock);
    uploader = PersonalsUploader(api, personals, outbox);
  });
  tearDown(() => database.close());

  void stubPost(NetworkResult<Map<String, dynamic>> result) {
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async => result);
  }

  OutboxDrainer drainer() => OutboxDrainer(
    api,
    outbox,
    handlers: {PersonalsUploader.type: uploader.handler},
  );

  test('serialize carries the fields Personal.serialize sends', () {
    final row = PersonalRow(
      id: 'note-1',
      isUploaded: false,
      title: 'Field notes',
      titleNormalized: 'field notes',
      description: 'What I saw',
      date: 555,
      userId: 'user-1',
      userName: 'ada',
    );

    expect(
      PersonalsRepository.serialize(
        row,
        uploadedAt: DateTime.fromMillisecondsSinceEpoch(999),
      ),
      {
        'title': 'Field notes',
        'uploadDate': 999,
        'createdDate': 555,
        'author': 'ada',
        'addedBy': 'ada',
        'description': 'What I saw',
        'resourceType': 'Activities',
        'private': true,
        'privateFor': {'users': 'user-1'},
      },
    );
  });

  test('queuePending queues each un-uploaded note exactly once', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await personals.create(userId: 'user-1', userName: 'ada', title: 'Two');

    expect(await uploader.queuePending(config: config, userId: 'user-1'), 2);
    expect(await outbox.due(), hasLength(2));

    // Calling again must not double-queue: `enqueue` keys on (type, itemId).
    await uploader.queuePending(config: config, userId: 'user-1');
    expect(await outbox.due(), hasLength(2));
  });

  test('endpoint is the CouchDB resources database', () {
    expect(
      PersonalsUploader.endpointFor(config),
      'https://satellite:1234@planet.example.org:443/db/resources',
    );
  });

  test('a successful upload adopts the ids CouchDB assigned', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({
        'id': 'srv-1',
        'rev': '1-abc',
      }),
    );

    expect(await drainer().drain(), [OutboxOutcome.completed]);

    final saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isTrue);
    expect(saved?.couchId, 'srv-1');
    expect(saved?.rev, '1-abc');
    expect(
      await personals.pendingUploads('user-1'),
      isEmpty,
      reason:
          'without adopting the ids this note would be posted again '
          'on every drain, one duplicate per drain',
    );
  });

  test('a failed upload leaves the note pending for the next drain', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(const NetworkError<Map<String, dynamic>>(503, 'unavailable'));

    expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);

    final saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isFalse);
    expect(await personals.pendingUploads('user-1'), hasLength(1));

    // The retry succeeds and the note settles.
    clock = clock.add(const Duration(minutes: 1));
    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );
    expect(await drainer().drain(), [OutboxOutcome.completed]);
    expect(await personals.pendingUploads('user-1'), isEmpty);
  });

  test('a response without ids does not mark the note uploaded', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await uploader.queuePending(config: config, userId: 'user-1');
    stubPost(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

    await drainer().drain();

    expect(
      (await database.personalDao.getById('note-0'))?.isUploaded,
      isFalse,
      reason:
          'nothing to adopt, so the note stays pending rather than '
          'claiming a sync that cannot be proven',
    );
  });

  test('the queued payload survives a restart of the queue object', () async {
    await personals.create(
      userId: 'user-1',
      userName: 'ada',
      title: 'Durable',
      description: 'still here',
    );
    await uploader.queuePending(config: config, userId: 'user-1');

    // A new repository over the same database is what a fresh process sees.
    final reopened = OutboxRepository(database.outboxDao, now: () => clock);
    final due = await reopened.due();
    expect(due, hasLength(1));
    expect(due.single.endpoint, PersonalsUploader.endpointFor(config));
  });
}
