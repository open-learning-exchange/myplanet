import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/voices_repository.dart';
import 'package:myplanet/repository/voices_uploader.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late VoicesRepository voices;
  late OutboxRepository outbox;
  late VoicesUploader uploader;
  var idCounter = 0;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    idCounter = 0;
    voices = VoicesRepository(
      api,
      database.newsDao,
      createId: () => 'local-${++idCounter}',
    );
    outbox = OutboxRepository(database.outboxDao);
    uploader = VoicesUploader(api, voices, outbox);
  });
  tearDown(() => database.close());

  Future<String> seedPost() =>
      voices.createPost(message: 'Hello', userId: 'user-1', userName: 'Ada');

  test('queues an endpoint that carries no credentials', () async {
    // Persisted in `outbox.endpoint`, a table that survives schema upgrades.
    final endpoint = VoicesUploader.endpointFor(config);
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, 'https://planet.example/db/news');

    await seedPost();
    await uploader.queuePending(config: config, userId: 'user-1');
    expect((await outbox.due()).single.endpoint, isNot(contains('1234')));
  });

  test('queues once and adopts the CouchDB identity on success', () async {
    final id = await seedPost();
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    // Re-queuing must not double-post: `enqueue` keys on (uploadType, itemId).
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    expect(await outbox.due(), hasLength(1));

    final operation = (await outbox.due()).single;
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>(const {
        'id': 'remote-1',
        'rev': '1-abc',
      }),
    );

    await uploader.handler(operation, const {}, 'Basic dGVzdA==');

    final row = await voices.getById(id);
    expect(row?.docId, 'remote-1');
    expect(row?.rev, '1-abc');
    expect(await voices.pendingUploads(), isEmpty);
  });

  test('a success without id/rev fails rather than dropping the row', () async {
    await seedPost();
    await uploader.queuePending(config: config, userId: 'user-1');
    final operation = (await outbox.due()).single;
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async => NetworkSuccess<Map<String, dynamic>>(const {}));

    final result = await uploader.handler(operation, const {}, null);

    expect(result, isA<NetworkError<Map<String, dynamic>>>());
    expect(await voices.pendingUploads(), hasLength(1));
  });

  test('forwards the auth header the drainer supplies', () async {
    await seedPost();
    await uploader.queuePending(config: config, userId: 'user-1');
    final operation = (await outbox.due()).single;
    String? seen;
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((invocation) async {
      seen = invocation.namedArguments[#authHeader] as String?;
      return NetworkSuccess<Map<String, dynamic>>(const {
        'id': 'remote-1',
        'rev': '1-abc',
      });
    });

    // The endpoint no longer authenticates, so the header is the only
    // credential; matching it with `any()` would hide a null.
    await uploader.handler(operation, const {}, 'Basic dGVzdA==');

    expect(seen, 'Basic dGVzdA==');
  });

  group('cancellation', () {
    test('deleting a queued post withdraws its upload', () async {
      // Without this the drain would POST a post the user deleted, recreating
      // it on the server with no local row left to record the result against.
      final id = await seedPost();
      await uploader.queuePending(config: config, userId: 'user-1');
      expect(await outbox.due(), hasLength(1));

      expect(await outbox.cancel(VoicesUploader.type, id), isTrue);

      expect(await outbox.due(), isEmpty);
      verifyNever(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      );
    });

    test('an in-flight operation is left for the drainer to finish', () async {
      final id = await seedPost();
      await uploader.queuePending(config: config, userId: 'user-1');
      final operation = (await outbox.due()).single;
      await outbox.markInProgress(operation.id);

      // The request may already be on the wire; the drainer still needs the
      // row to record the outcome against.
      expect(await outbox.cancel(VoicesUploader.type, id), isFalse);
    });

    test('cancel excludes in-flight rows in the delete itself', () async {
      // Checking the status and deleting in two statements leaves a window:
      // the drainer interleaves at every await, so it can claim the row in
      // between and the delete would then remove a request already on the
      // wire. The exclusion has to be part of the delete.
      final id = await seedPost();
      await uploader.queuePending(config: config, userId: 'user-1');
      final operation = (await outbox.due()).single;
      await outbox.markInProgress(operation.id);

      expect(await outbox.cancel(VoicesUploader.type, id), isFalse);
      // The row must still be there for the drainer to record against.
      expect(await database.outboxDao.getById(operation.id), isNotNull);
    });

    test('cancelling an unqueued item is a no-op', () async {
      expect(await outbox.cancel(VoicesUploader.type, 'never-queued'), isFalse);
    });
  });
}
