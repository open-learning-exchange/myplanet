import 'dart:convert';
import 'dart:io';

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

import 'device_identity_fixture.dart';

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
    uploader = PersonalsUploader(api, personals, outbox, testDeviceIdentity);
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

  test('serialize includes filename when a local path is present', () {
    final row = PersonalRow(
      id: 'note-2',
      isUploaded: false,
      title: 'Field notes',
      titleNormalized: 'field notes',
      date: 555,
      userId: 'user-1',
      userName: 'ada',
      path: '/storage/notes/photo.jpg',
    );

    final serialized = PersonalsRepository.serialize(row);
    expect(serialized['filename'], 'photo.jpg');
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

  test('the endpoint carries no credentials', () {
    expect(
      PersonalsUploader.endpointFor(config),
      // `Uri.replace(userInfo: '')` also normalizes away :443, the default
      // port for https — same normalization as `credentialFreeBase`.
      'https://planet.example.org/db/resources',
      reason:
          'outbox.endpoint is persisted and survives schema upgrades, so '
          'a satellite:PIN@ userinfo would leave the server PIN in plaintext '
          'SQLite until the upload succeeds',
    );
    expect(PersonalsUploader.endpointFor(config), isNot(contains(config.pin)));
    expect(PersonalsUploader.endpointFor(config), isNot(contains('@')));
  });

  test('the queued row stores no credentials either', () async {
    await personals.create(userId: 'user-1', userName: 'ada', title: 'One');
    await uploader.queuePending(config: config, userId: 'user-1');

    final row = (await outbox.due()).single;
    expect(row.endpoint, isNot(contains(config.pin)));
    expect(row.payload, isNot(contains(config.pin)));
    final payload = jsonDecode(row.payload) as Map<String, dynamic>;
    for (final field in testDeviceFields.entries) {
      expect(payload, containsPair(field.key, field.value));
    }
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

  test('uploads the file attachment after the document succeeds', () async {
    final tempDir = await Directory.systemTemp.createTemp('personals_test');
    addTearDown(() => tempDir.delete(recursive: true));
    final file = File('${tempDir.path}/attach.txt')
      ..writeAsBytesSync(utf8.encode('hello'));

    await personals.create(
      userId: 'user-1',
      userName: 'ada',
      title: 'Note with file',
      path: file.path,
    );
    await uploader.queuePending(config: config, userId: 'user-1');

    stubPost(
      const NetworkSuccess<Map<String, dynamic>>({'id': 'srv-1', 'rev': '1-a'}),
    );
    when(
      () => api.uploadAttachment(
        'https://planet.example.org/db/resources/srv-1/attach.txt',
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: 'text/plain',
        ifMatch: '1-a',
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({
        'ok': true,
        'id': 'srv-1',
        'rev': '2-b',
      }),
    );

    expect(await drainer().drain(), [OutboxOutcome.completed]);
    verify(
      () => api.uploadAttachment(
        'https://planet.example.org/db/resources/srv-1/attach.txt',
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: 'text/plain',
        ifMatch: '1-a',
      ),
    ).called(1);

    final saved = await database.personalDao.getById('note-0');
    expect(saved?.isUploaded, isTrue);
    expect(saved?.couchId, 'srv-1');
    expect(saved?.rev, '1-a');
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
