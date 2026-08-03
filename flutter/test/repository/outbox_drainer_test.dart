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
        'personals': (row, payload) async {
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

  test('recoverStuck requeues a drain that was killed mid-flight', () async {
    final id = await enqueue();
    await outbox.markInProgress(id);
    stubSend(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));
    final subject = drainer();

    expect(await subject.drain(), isEmpty, reason: 'claimed, so not due');
    await subject.recoverStuck();
    expect(await subject.drain(), [OutboxOutcome.completed]);
  });
}
