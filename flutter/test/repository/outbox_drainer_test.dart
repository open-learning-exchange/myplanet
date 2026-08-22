import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late OutboxRepository outbox;
  var clock = DateTime.fromMillisecondsSinceEpoch(1000);

  const endpoint = 'https://planet.example.org/db/resources';

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    clock = DateTime.fromMillisecondsSinceEpoch(1000);
    outbox = OutboxRepository(database.outboxDao, now: () => clock);
  });
  tearDown(() => database.close());

  Future<String> enqueue({String itemId = 'note-1'}) => outbox.enqueue(
    uploadType: 'personals',
    itemId: itemId,
    endpoint: endpoint,
    payload: const {'title': 'A note'},
  );

  void stubSend(NetworkResult<Map<String, dynamic>> result) {
    when(
      () => api.sendJsonObject(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async => result);
  }

  OutboxDrainer drainer({Map<String, OutboxHandler>? handlers}) =>
      OutboxDrainer(api, outbox, handlers: handlers);

  test('a 2xx completes the operation and clears the queue', () async {
    await enqueue();
    stubSend(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

    expect(await drainer().drain(), [OutboxOutcome.completed]);
    expect(await outbox.due(), isEmpty);

    final captured = verify(
      () => api.sendJsonObject(
        captureAny(),
        body: captureAny(named: 'body'),
        method: captureAny(named: 'method'),
        authHeader: any(named: 'authHeader'),
      ),
    ).captured;
    expect(captured[0], endpoint);
    expect(captured[1], {'title': 'A note'});
    expect(captured[2], 'POST');
  });

  test('a 5xx is retried, not abandoned', () async {
    await enqueue();
    stubSend(const NetworkError<Map<String, dynamic>>(503, 'unavailable'));

    expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);

    expect(await outbox.due(), isEmpty, reason: 'backing off');
    clock = clock.add(const Duration(minutes: 1));
    expect(await outbox.due(), hasLength(1));
  });

  test('a 409 conflict is permanent, matching UploadCoordinator', () async {
    final id = await enqueue();
    stubSend(const NetworkError<Map<String, dynamic>>(409, 'conflict'));

    expect(await drainer().drain(), [OutboxOutcome.abandoned]);

    clock = clock.add(const Duration(days: 1));
    expect(await outbox.due(), isEmpty);
    final row = await database.outboxDao.getById(id);
    expect(row?.status, OutboxDao.statusAbandoned);
    expect(row?.httpCode, 409);
  });

  test('a 400 is permanent — only 5xx is retryable', () async {
    await enqueue();
    stubSend(const NetworkError<Map<String, dynamic>>(400, 'bad request'));
    expect(await drainer().drain(), [OutboxOutcome.abandoned]);
  });

  test('a transport failure is always retryable', () async {
    await enqueue();
    stubSend(
      const NetworkException<Map<String, dynamic>>('connection timeout'),
    );

    expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);
    clock = clock.add(const Duration(minutes: 1));
    expect(await outbox.due(), hasLength(1));
  });

  test(
    'a malformed payload is abandoned rather than retried forever',
    () async {
      final id = await enqueue();
      // Corrupt the stored payload behind the repository's back.
      await database.outboxDao.patch(
        id,
        const OutboxEntriesCompanion(payload: Value('not json')),
      );

      expect(await drainer().drain(), [OutboxOutcome.abandoned]);
      verifyNever(
        () => api.sendJsonObject(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
          authHeader: any(named: 'authHeader'),
        ),
      );
    },
  );

  test('a registered handler takes over from the default replay', () async {
    await enqueue();
    var handled = 0;

    final outcomes = await drainer(
      handlers: {
        'personals': (row, payload, authHeader) async {
          handled++;
          expect(payload, {'title': 'A note'});
          expect(row.itemId, 'note-1');
          return const NetworkSuccess<Map<String, dynamic>>({
            'id': 'x',
            'rev': '1-a',
          });
        },
      },
    ).drain();

    expect(outcomes, [OutboxOutcome.completed]);
    expect(handled, 1);
    verifyNever(
      () => api.sendJsonObject(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
        authHeader: any(named: 'authHeader'),
      ),
    );
  });

  test('drains every due operation in one pass', () async {
    await enqueue(itemId: 'note-1');
    await enqueue(itemId: 'note-2');
    stubSend(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

    expect(await drainer().drain(), [
      OutboxOutcome.completed,
      OutboxOutcome.completed,
    ]);
    expect(await outbox.due(), isEmpty);
  });

  test('concurrent drains do not double-send', () async {
    await enqueue();
    stubSend(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));
    final subject = drainer();

    await Future.wait([subject.drain(), subject.drain()]);

    verify(
      () => api.sendJsonObject(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
        authHeader: any(named: 'authHeader'),
      ),
    ).called(1);
  });

  test('the credential reaches the API', () async {
    await enqueue();
    stubSend(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

    await drainer().drain(authHeader: 'Basic c2F0ZWxsaXRlOjEyMzQ=');

    // The stored endpoint is deliberately credential-free, so the header is
    // the only thing authenticating the request. Matching it with `any` — as
    // these tests used to — would pass even when it is null.
    final captured = verify(
      () => api.sendJsonObject(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
        authHeader: captureAny(named: 'authHeader'),
      ),
    ).captured;
    expect(captured.single, 'Basic c2F0ZWxsaXRlOjEyMzQ=');
  });

  test('a handler that throws is retried, not left in progress', () async {
    await enqueue();

    final outcomes = await drainer(
      handlers: {
        'personals': (row, payload, authHeader) async =>
            throw StateError('database went away'),
      },
    ).drain();

    expect(outcomes, [OutboxOutcome.retryScheduled]);
    clock = clock.add(const Duration(minutes: 1));
    expect(
      await outbox.due(),
      hasLength(1),
      reason:
          'an unguarded throw would strand the row in_progress until the '
          'next startup and abort the rest of the pass',
    );
  });

  test('a throw does not stop the remaining operations in the pass', () async {
    await enqueue(itemId: 'note-1');
    await enqueue(itemId: 'note-2');

    final outcomes = await drainer(
      handlers: {
        'personals': (row, payload, authHeader) async {
          if (row.itemId == 'note-1') throw StateError('boom');
          return const NetworkSuccess<Map<String, dynamic>>({'ok': true});
        },
      },
    ).drain();

    expect(outcomes, [OutboxOutcome.retryScheduled, OutboxOutcome.completed]);
  });

  test('recoverStuck requeues a drain that was killed mid-flight', () async {
    final id = await enqueue();
    await outbox.markInProgress(id);
    stubSend(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));
    final subject = drainer();

    expect(await subject.drain(), isEmpty, reason: 'claimed, so not due');
    clock = clock.add(OutboxRepository.stuckClaimTimeout);
    await subject.recoverStuck();
    expect(
      await subject.drain(),
      isEmpty,
      reason: 'a claim exactly at the lease boundary is still live',
    );
    clock = clock.add(const Duration(milliseconds: 1));
    await subject.recoverStuck();
    expect(await subject.drain(), [OutboxOutcome.completed]);
  });

  test('onlyTypes leaves every other type untouched', () async {
    // The public-survey case: a respondent with no server configuration has no
    // credential, and posting the rest of the queue unauthenticated would earn a
    // 401 — which the retry rule calls *permanent* and would abandon writes that
    // are perfectly deliverable once the app is configured.
    await enqueue(itemId: 'note-1');
    await outbox.enqueue(
      uploadType: 'public_survey',
      itemId: 'sheet-1',
      endpoint: endpoint,
      payload: const {'answers': <String>[]},
    );
    stubSend(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));
    clock = clock.add(const Duration(minutes: 5));

    final outcomes = await drainer().drain(onlyTypes: const {'public_survey'});

    expect(outcomes, [OutboxOutcome.completed]);
    // The personals row is still pending, not failed.
    final remaining = await outbox.due();
    expect(remaining.map((row) => row.uploadType), ['personals']);
    expect(remaining.single.attemptCount, 0);
  });
}
