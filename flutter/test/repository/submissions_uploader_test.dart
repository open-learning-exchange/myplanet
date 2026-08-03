import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/submissions_uploader.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late SubmissionsRepository repository;
  late OutboxRepository outbox;
  late SubmissionsUploader uploader;
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = SubmissionsRepository(api, db.submissionDao);
    outbox = OutboxRepository(db.outboxDao);
    uploader = SubmissionsUploader(api, repository, outbox);
  });
  tearDown(() => db.close());

  test('queues a draft once and adopts CouchDB ids after upload', () async {
    final id = await repository.createDraft(
      userId: 'user-1',
      type: 'exam',
      title: 'Draft',
      answers: const [],
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
        'id': 'server-id',
        'rev': '1-rev',
      }),
    );

    final result = await uploader.handler(operation, const {});

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(await repository.pendingUploads('user-1'), isEmpty);
    expect((await repository.getById(id))?.couchId, 'server-id');
  });
}
