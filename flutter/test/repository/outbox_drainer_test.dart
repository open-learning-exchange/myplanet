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

  test('a 409 rejects the request the payload made', () async {
    // Renamed from "a 409 conflict is permanent, matching UploadCoordinator",
    // which misread the Kotlin: `UploadCoordinator.kt:169-204` intercepts a
    // 409 *before* its `code >= 500` rule, GETs the document, adopts its
    // `_rev` and reports success. `retryable = false` appears only when that
    // recovery GET itself fails. The port has that arm for exactly one
    // uploader (`adopted_surveys_uploader.dart:219-242`); everywhere else a
    // 409 lands here. What this test pins is the narrower true thing: the
    // request as sent has been refused, so nothing re-sends it unchanged.
    final id = await enqueue();
    stubSend(const NetworkError<Map<String, dynamic>>(409, 'conflict'));

    expect(await drainer().drain(), [OutboxOutcome.abandoned]);

    clock = clock.add(const Duration(days: 1));
    expect(await outbox.due(), isEmpty);
    final row = await database.outboxDao.getById(id);
    expect(row?.status, OutboxDao.statusAbandoned);
    expect(row?.httpCode, 409);
  });

  test('a 400 is a rejection of the request', () async {
    await enqueue();
    stubSend(const NetworkError<Map<String, dynamic>>(400, 'bad request'));
    expect(await drainer().drain(), [OutboxOutcome.abandoned]);
  });

  test('a 401 is retried — it is about the caller, not the request', () async {
    // Kotlin's one rule is `retryable = response.code() >= 500`
    // (`UploadCoordinator.kt:211`), which makes a 401 non-retryable — but
    // `RetryQueue` then declines to queue it at all and Kotlin's sweep
    // re-reads the live table on the next sync, so the write *is* re-attempted
    // there. The port's sweep re-enqueues rather than re-posts, so reaching
    // the same outcome needs 401 classified as transient here.
    await enqueue();
    stubSend(const NetworkError<Map<String, dynamic>>(401, 'unauthorized'));

    expect(await drainer().drain(), [OutboxOutcome.retryScheduled]);
    clock = clock.add(const Duration(minutes: 1));
    expect(await outbox.due(), hasLength(1));
  });

  test('403, 408 and 429 are retried for the same reason', () async {
    for (final code in [403, 408, 429]) {
      await outbox.enqueue(
        uploadType: 'personals',
        itemId: 'note-$code',
        endpoint: endpoint,
        payload: const {'title': 'A note'},
      );
      stubSend(NetworkError<Map<String, dynamic>>(code, 'refused'));
      // Rows queued by an earlier turn of this loop are due again, so the
      // assertion is on the kind of every outcome rather than on the count.
      expect(
        await drainer().drain(),
        everyElement(OutboxOutcome.retryScheduled),
        reason: '$code',
      );
      clock = clock.add(const Duration(minutes: 1));
    }
    for (final code in [403, 408, 429]) {
      final rows = await database.outboxDao.forItem('personals', 'note-$code');
      expect(rows.single.status, OutboxDao.statusPending, reason: '$code');
    }
  });

  test("a handler's verdict on a 2xx body is not a server refusal", () async {
    // `PlanetApi` only builds a `NetworkError` from a response it received
    // and substitutes 0 for a missing status, so a *null* code can only have
    // come from a handler: the send succeeded and the body was unusable. The
    // write may already be on the server, so it is recorded terminally —
    // never re-sent under the same payload — but with a code that says no
    // HTTP status was the reason.
    final id = await enqueue();

    final outcomes = await drainer(
      handlers: {
        'personals': (row, payload, authHeader) async =>
            const NetworkError<Map<String, dynamic>>(null, 'no rev'),
      },
    ).drain();

    expect(outcomes, [OutboxOutcome.abandoned]);
    final row = await database.outboxDao.getById(id);
    expect(row?.status, OutboxDao.statusAbandoned);
    expect(row?.httpCode, OutboxRepository.noUsableResponse);
    expect(
      OutboxRepository.classifyStatus(row?.httpCode),
      OutboxRefusal.indeterminate,
    );
  });

  test('a rejected request is asked once, however many sweeps run', () async {
    // The accretion Phase 134 reported and Phase 138 sharpened. One sweep is
    // not evidence — the row and the POST used to be minted afresh on *every*
    // sweep, so the demonstration has to be several. The cadence differs per
    // upload type (per sync for submissions and voices, per user write for
    // health, per team-detail mount for `teamLog`); what they share is that
    // nothing about it is bounded.
    stubSend(const NetworkError<Map<String, dynamic>>(409, 'conflict'));

    for (var sync = 0; sync < 5; sync++) {
      // What `queuePending` does on every sweep: re-offer every locally dirty
      // record, whose serialization has not changed because nothing has
      // successfully uploaded it.
      await enqueue();
      await drainer().drain();
      clock = clock.add(const Duration(hours: 1));
    }

    verify(
      () => api.sendJsonObject(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
        authHeader: any(named: 'authHeader'),
      ),
    ).called(1);
    expect(
      await database.outboxDao.forItem('personals', 'note-1'),
      hasLength(1),
      reason: 'one row for the item, not one per sweep',
    );
    expect(
      await database.outboxDao.abandoned('personals'),
      hasLength(1),
      reason: 'still diagnosable, just not repeated',
    );
  });

  test('an unusable 2xx response is not asked again either', () async {
    // Job 1: the same unboundedness, reached through a handler's own verdict
    // rather than through a status code — and the case where re-asking risks
    // a *second* copy of a document that is already filed.
    var sends = 0;

    for (var sync = 0; sync < 5; sync++) {
      await enqueue();
      await drainer(
        handlers: {
          'personals': (row, payload, authHeader) async {
            sends++;
            return const NetworkError<Map<String, dynamic>>(null, 'no rev');
          },
        },
      ).drain();
      clock = clock.add(const Duration(hours: 1));
    }

    expect(sends, 1);
    expect(
      await database.outboxDao.forItem('personals', 'note-1'),
      hasLength(1),
    );
  });

  test('a transient failure keeps being offered across sweeps', () async {
    // The other half of the policy: bounding the *rows* must not bound the
    // *retries* of a write that is still deliverable. A server that is down
    // for a week is not a verdict on the record — and Kotlin agrees, since
    // `RetryQueue` never abandons an *item*, only an operation: a refused
    // item's local flag is untouched (`UploadCoordinator.kt:63-68, 241-257`)
    // and the next sweep re-offers it.
    stubSend(const NetworkError<Map<String, dynamic>>(503, 'unavailable'));

    for (var sync = 0; sync < 3; sync++) {
      await enqueue();
      await drainer().drain();
      clock = clock.add(const Duration(hours: 1));
    }

    expect(
      await database.outboxDao.forItem('personals', 'note-1'),
      hasLength(1),
      reason: 'one row, still',
    );
    verify(
      () => api.sendJsonObject(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
        authHeader: any(named: 'authHeader'),
      ),
    ).called(3);
  });

  test('a rejected row is re-armed by an edited payload', () async {
    stubSend(const NetworkError<Map<String, dynamic>>(409, 'conflict'));
    await enqueue();
    await drainer().drain();
    clock = clock.add(const Duration(hours: 1));

    // The pull that follows a sync hands the row the revision the 409 was
    // about, so the next serialization differs.
    await outbox.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: endpoint,
      payload: const {'title': 'A note', '_rev': '2-b'},
    );
    stubSend(const NetworkSuccess<Map<String, dynamic>>({'ok': true}));

    expect(await drainer().drain(), [OutboxOutcome.completed]);
    expect(await database.outboxDao.forItem('personals', 'note-1'), isEmpty);
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
