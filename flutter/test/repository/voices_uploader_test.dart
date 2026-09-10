import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:myplanet/repository/voices_repository.dart';
import 'package:myplanet/repository/voices_uploader.dart';

import 'device_identity_fixture.dart';

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
    uploader = VoicesUploader(api, voices, outbox, testDeviceIdentity);
  });
  tearDown(() => database.close());

  /// What `OutboxDrainer` hands the handler: the operation's own stored
  /// payload, decoded. Passing `const {}` instead would read as "the row
  /// changed while the POST was on the wire", which is a real branch of
  /// `markUploaded` and not what these tests are about.
  Map<String, dynamic> payloadOf(OutboxRow operation) =>
      jsonDecode(operation.payload) as Map<String, dynamic>;

  Future<String> seedPost() =>
      voices.createPost(message: 'Hello', userId: 'user-1', userName: 'Ada');

  test('an edited post conflicting on its revision is re-sent', () async {
    // `pendingUploads` includes a row with a `docId` when `isEdited`, so an
    // edit is an update whose `_rev` can be stale. Adopting would be worse
    // here than anywhere else: `markUploaded` clears `imageUrls` and deletes
    // the local image bytes, so the edit *and* the images would be
    // unrecoverable while the server still shows the other device's text.
    final id = await seedPost();
    await voices.markUploaded(id, 'news-couch', '1-stale');
    await voices.editPost(newsId: id, message: 'Hello again');

    await uploader.queuePending(config: config, userId: 'user-1');
    final operation = (await outbox.due()).single;
    final payload = payloadOf(operation);
    expect(payload['_id'], 'news-couch');
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
              'id': 'news-couch',
              'rev': '3-h',
            })
          : const NetworkError<Map<String, dynamic>>(409, 'conflict');
    });
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        '_id': 'news-couch',
        '_rev': '2-server',
      }),
    );

    final result = await uploader.handler(operation, payload, 'auth');

    expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
    expect(sent, hasLength(2));
    expect(sent[1]['message'], 'Hello again');
    // The DAO write, asserted on the values the *recovered* send produced.
    // An earlier cut of this test read `newsRev ?? rev` and asserted only
    // `isNotNull` — but the fixture's own `markUploaded` above had already set
    // `rev`, so it held whether or not the handler ran at all. Mutation-proven:
    // skipping `markUploaded` in the success branch left it green.
    final stored = await voices.getById(id);
    expect(stored?.rev, '3-h');
    expect(stored?.docId, 'news-couch');
    expect(stored?.isEdited, isFalse);
    // The half this uploader's own comment calls the highest-stakes in the
    // port: `markUploaded` clears `imageUrls` and deletes the local bytes, so
    // a recovered send must reach it rather than leaving the post half-done.
    expect(stored?.imageUrls, isEmpty);
  });

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

  test('the queued post is stamped with its origin', () async {
    // `VoicesRepositoryImpl.serializeNews` gained `addDocumentOrigin()` in
    // `27c0470`, and it stamps the *outer* document, not the nested `news`
    // sub-object — the distinction Phase 74's reactions bug turned on.
    await seedPost();
    await uploader.queuePending(config: config, userId: 'user-1');

    final doc =
        jsonDecode((await outbox.due()).single.payload) as Map<String, dynamic>;
    expect(doc['androidId'], 'android-id_build-id');
    expect(doc['app'], 'myplanet');
    expect(doc.containsKey('deviceName'), isFalse);
    expect(doc.containsKey('customDeviceName'), isFalse);
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

    await uploader.handler(operation, payloadOf(operation), 'Basic dGVzdA==');

    final row = await voices.getById(id);
    expect(row?.docId, 'remote-1');
    expect(row?.rev, '1-abc');
    expect(await voices.pendingUploads(), isEmpty);
  });

  test('a send already on the wire is neither counted nor re-queued', () async {
    // Phase 144. `OutboxRepository.enqueue` puts an `in_progress` row back to
    // `pending` so a payload edited mid-flight is not lost, and `markCompleted`
    // is `deleteIfInProgress` — so the send that succeeds moments later deletes
    // nothing, the row survives with the same body, and the next drain posts a
    // **second** `news` document. A voice with no `_id` is an append, so that
    // is a duplicate rather than an edit.
    final id = await seedPost();
    expect(await uploader.queuePending(config: config, userId: 'user-1'), 1);
    final claimed = (await outbox.due()).single;
    await outbox.markInProgress(claimed.id);

    expect(
      await uploader.queuePending(config: config, userId: 'user-1'),
      0,
      reason: 'a post whose POST is on the wire must be left alone',
    );
    final rows = await database.outboxDao.forItem(VoicesUploader.type, id);
    expect(rows, hasLength(1));
    expect(
      rows.single.status,
      'in_progress',
      reason:
          'the re-enqueue would have reset it to pending, which is what makes '
          'the row survive `markCompleted` and replay',
    );
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

    final result = await uploader.handler(
      operation,
      payloadOf(operation),
      null,
    );

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
    await uploader.handler(operation, payloadOf(operation), 'Basic dGVzdA==');

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
