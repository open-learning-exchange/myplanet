import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/outbox_repository.dart';

void main() {
  late AppDatabase database;
  late OutboxRepository repository;
  var clock = DateTime.fromMillisecondsSinceEpoch(1000);
  var nextId = 0;

  setUp(() {
    database = AppDatabase.memory();
    clock = DateTime.fromMillisecondsSinceEpoch(1000);
    nextId = 0;
    repository = OutboxRepository(
      database.outboxDao,
      now: () => clock,
      createId: () => 'op-${nextId++}',
    );
  });
  tearDown(() => database.close());

  group('backoff', () {
    test('matches RetryOperation.calculateNextRetryTime', () {
      // min(30s * 2^attempt, 30min), the Kotlin constants exactly.
      expect(OutboxRepository.backoffFor(1), const Duration(minutes: 1));
      expect(OutboxRepository.backoffFor(2), const Duration(minutes: 2));
      expect(OutboxRepository.backoffFor(3), const Duration(minutes: 4));
      expect(OutboxRepository.backoffFor(4), const Duration(minutes: 8));
      expect(OutboxRepository.backoffFor(5), const Duration(minutes: 16));
    });

    test('saturates at maxDelay instead of overflowing the shift', () {
      expect(OutboxRepository.backoffFor(6), OutboxRepository.maxDelay);
      expect(OutboxRepository.backoffFor(64), OutboxRepository.maxDelay);
      expect(OutboxRepository.backoffFor(1 << 20), OutboxRepository.maxDelay);
    });
  });

  test('a queued operation is due immediately', () async {
    await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'https://example.org/db/resources',
      payload: const {'title': 'First note'},
    );

    final due = await repository.due();
    expect(due, hasLength(1));
    expect(due.single.uploadType, 'personals');
    expect(due.single.itemId, 'note-1');
    expect(jsonDecode(due.single.payload), {'title': 'First note'});
    expect(due.single.status, OutboxDao.statusPending);
  });

  test(
    're-enqueuing the same item refreshes it instead of duplicating',
    () async {
      final first = await repository.enqueue(
        uploadType: 'personals',
        itemId: 'note-1',
        endpoint: 'https://example.org/db/resources',
        payload: const {'title': 'First'},
      );
      final second = await repository.enqueue(
        uploadType: 'personals',
        itemId: 'note-1',
        endpoint: 'https://example.org/db/resources',
        payload: const {'title': 'Edited'},
      );

      expect(
        second,
        first,
        reason: 'same (uploadType, itemId) is one operation',
      );
      final due = await repository.due();
      expect(due, hasLength(1));
      expect(
        jsonDecode(due.single.payload),
        {'title': 'Edited'},
        reason: 'an edit between failure and retry must be what gets sent',
      );
    },
  );

  test('a different item, or a different type, is its own operation', () async {
    await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-2',
      endpoint: 'e',
      payload: const {},
    );
    await repository.enqueue(
      uploadType: 'submissions',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );

    expect(await repository.due(), hasLength(3));
  });

  test('a failure backs the operation off and it returns when due', () async {
    final id = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );

    final abandoned = await repository.markFailed(
      id,
      errorMessage: 'boom',
      httpCode: 503,
    );
    expect(abandoned, isFalse);
    expect(
      await repository.due(),
      isEmpty,
      reason: 'inside the backoff window',
    );

    clock = clock.add(const Duration(minutes: 1));
    final due = await repository.due();
    expect(due, hasLength(1));
    expect(due.single.attemptCount, 1);
    expect(due.single.errorMessage, 'boom');
    expect(due.single.httpCode, 503);
  });

  test('abandons after maxAttempts and stops coming back', () async {
    final id = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
      maxAttempts: 3,
    );

    expect(await repository.markFailed(id), isFalse);
    expect(await repository.markFailed(id), isFalse);
    expect(await repository.markFailed(id), isTrue, reason: '3rd of 3');

    clock = clock.add(const Duration(days: 1));
    expect(await repository.due(), isEmpty);
    expect(
      (await database.outboxDao.getById(id))?.status,
      OutboxDao.statusAbandoned,
      reason: 'kept for inspection rather than deleted',
    );
  });

  test('a permanent failure abandons on the first attempt', () async {
    final id = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );

    expect(
      await repository.markFailed(
        id,
        httpCode: 409,
        refusal: OutboxRefusal.rejected,
      ),
      isTrue,
    );
    clock = clock.add(const Duration(days: 1));
    expect(await repository.due(), isEmpty);
  });

  test('completing removes the operation', () async {
    final id = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    // The drainer always claims a row before sending, so `markCompleted`
    // never sees a `pending` row in production — and it must not delete one,
    // because that is how a mid-flight edit is preserved.
    await repository.markInProgress(id);
    await repository.markCompleted(id);

    expect(await repository.due(), isEmpty);
    expect(await database.outboxDao.getById(id), isNull);
  });

  test(
    'recoverStuck returns a killed in-progress drain to the queue',
    () async {
      final id = await repository.enqueue(
        uploadType: 'personals',
        itemId: 'note-1',
        endpoint: 'e',
        payload: const {},
      );
      await repository.markInProgress(id);
      expect(await repository.due(), isEmpty, reason: 'claimed by a drain');

      clock = clock.add(OutboxRepository.stuckClaimTimeout);
      await repository.recoverStuck();
      expect(await repository.due(), isEmpty, reason: 'boundary is still live');

      clock = clock.add(const Duration(milliseconds: 1));
      await repository.recoverStuck();
      expect(
        await repository.due(),
        hasLength(1),
        reason: 'without this the operation is invisible forever',
      );
    },
  );

  test('only one drainer can claim a pending operation', () async {
    final id = await repository.enqueue(
      uploadType: 'submission',
      itemId: 'claim-once',
      payload: const {'answer': 42},
      endpoint: 'https://planet.test/db/submissions',
    );

    expect(await repository.markInProgress(id), isTrue);
    expect(await repository.markInProgress(id), isFalse);
  });

  test('recovery does not steal a live claim from another isolate', () async {
    final id = await repository.enqueue(
      uploadType: 'submission',
      itemId: 'still-sending',
      payload: const {'answer': 42},
      endpoint: 'https://planet.test/db/submissions',
    );
    await repository.markInProgress(id);

    clock = clock.add(const Duration(minutes: 19));
    expect(await repository.recoverStuck(), 0);
    expect(
      (await database.outboxDao.getById(id))?.status,
      OutboxDao.statusInProgress,
    );
  });

  test('recovery repairs a legacy claim with a zero lease timestamp', () async {
    final id = await repository.enqueue(
      uploadType: 'submission',
      itemId: 'legacy-claim',
      payload: const {'answer': 42},
      endpoint: 'https://planet.test/db/submissions',
    );
    await database.outboxDao.patch(
      id,
      const OutboxEntriesCompanion(
        status: Value(OutboxDao.statusInProgress),
        lastAttemptAt: Value(0),
      ),
    );

    clock = clock.add(OutboxRepository.stuckClaimTimeout);
    expect(await repository.recoverStuck(), 1);
    expect(await repository.due(), hasLength(1));
  });

  test('a completed item can be queued again', () async {
    final first = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    await repository.markInProgress(first);
    await repository.markCompleted(first);

    final second = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    expect(second, isNot(first));
    expect(await repository.due(), hasLength(1));
  });

  test('a rejected item is not re-armed by the identical request', () async {
    // This used to read "an abandoned item does not block a fresh enqueue",
    // and it asserted `second` was a *different* row. That is the accretion:
    // `queuePending` runs every sync, so one 409 became one more dead row and
    // one more doomed POST per sync, for ever, in a table that survives schema
    // bumps.
    final first = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    await repository.markFailed(
      first,
      httpCode: 409,
      refusal: OutboxRefusal.rejected,
    );

    final second = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    expect(second, first, reason: 'one row per (uploadType, itemId)');
    expect(await repository.due(), isEmpty, reason: 'nothing left to ask');
    expect(
      await database.outboxDao.forItem('personals', 'note-1'),
      hasLength(1),
    );
  });

  test('a changed request re-arms the same row', () async {
    final first = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {'body': 'first'},
    );
    await repository.markFailed(
      first,
      httpCode: 409,
      refusal: OutboxRefusal.rejected,
    );

    // The user edited the note; a 409 against a stale `_rev` says nothing
    // about a body the server has not seen.
    final second = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {'body': 'edited'},
    );

    expect(second, first);
    final due = await repository.due();
    expect(due, hasLength(1));
    expect(due.single.attemptCount, 0, reason: 'a fresh ladder');
    expect(due.single.httpCode, isNull);
    expect(due.single.errorMessage, isNull);
  });

  test('a reconfigured endpoint re-arms a rejected row', () async {
    // The outbox freezes `endpoint` at enqueue time, so moving the app to the
    // clone URL changes the request even when the body is byte-identical.
    final first = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'https://primary.example/db',
      payload: const {},
    );
    await repository.markFailed(
      first,
      httpCode: 400,
      refusal: OutboxRefusal.rejected,
    );

    await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'https://clone.example/db',
      payload: const {},
    );
    expect(await repository.due(), hasLength(1));
  });

  test('an exhausted transient failure is re-armed unchanged', () async {
    // Nothing about a 503 or a dropped connection is a verdict on the
    // request, so a later sweep is entitled to ask again — which is how a
    // record queued during a server outage eventually lands.
    final id = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
      maxAttempts: 1,
    );
    expect(
      await repository.markFailed(id, httpCode: 503, errorMessage: 'down'),
      isTrue,
    );
    expect(await repository.due(), isEmpty);

    final second = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    expect(second, id);
    expect(await repository.due(), hasLength(1));
  });

  test('a rejection without a status is still terminal', () async {
    // `markFailed` records [OutboxRepository.noUsableResponse] for a terminal
    // refusal that carries no HTTP status of its own — a handler's verdict on
    // a 2xx body, or a payload that will not parse. Storing plain `null` would
    // make it indistinguishable from a transport failure, which must be
    // retried.
    final id = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    await repository.markFailed(
      id,
      errorMessage: 'no rev',
      refusal: OutboxRefusal.indeterminate,
    );
    expect(
      (await database.outboxDao.getById(id))?.httpCode,
      OutboxRepository.noUsableResponse,
    );

    await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    expect(await repository.due(), isEmpty);
  });

  test('a transport failure keeps a null status and stays retryable', () async {
    final id = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
      maxAttempts: 1,
    );
    await repository.markFailed(id, errorMessage: 'connection reset');
    expect((await database.outboxDao.getById(id))?.httpCode, isNull);

    await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    expect(await repository.due(), hasLength(1));
  });

  test('surplus rows an older build left are collapsed to one', () async {
    // An install upgrading into this policy carries one abandoned row per
    // sweep since its first refusal, and `outbox` survives schema bumps.
    for (var i = 0; i < 4; i++) {
      await database.outboxDao.upsert(
        OutboxEntriesCompanion.insert(
          id: 'legacy-$i',
          uploadType: 'personals',
          itemId: 'note-1',
          payload: '{}',
          endpoint: 'e',
          status: const Value(OutboxDao.statusAbandoned),
          httpCode: const Value(409),
          createdAt: 1000 + i,
        ),
      );
    }

    await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );

    final rows = await database.outboxDao.forItem('personals', 'note-1');
    expect(rows, hasLength(1));
    expect(rows.single.id, 'legacy-3', reason: 'the newest answer is kept');
    expect(rows.single.status, OutboxDao.statusAbandoned);
  });

  test('classifyStatus splits the caller from the request', () {
    for (final code in [null, 0, 500, 503, 401, 403, 404, 408, 429]) {
      expect(
        OutboxRepository.classifyStatus(code),
        OutboxRefusal.transient,
        reason: '$code',
      );
    }
    for (final code in [400, 409, 412, 413, 415, 422]) {
      expect(
        OutboxRepository.classifyStatus(code),
        OutboxRefusal.rejected,
        reason: '$code',
      );
    }
    expect(
      OutboxRepository.classifyStatus(OutboxRepository.noUsableResponse),
      OutboxRefusal.indeterminate,
    );
  });

  test('watchPendingCount tracks the queue', () async {
    expect(await repository.watchPendingCount().first, 0);
    final id = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    expect(await repository.watchPendingCount().first, 1);
    await repository.markInProgress(id);
    await repository.markCompleted(id);
    expect(await repository.watchPendingCount().first, 0);
  });

  test('cleanup drops finished rows but keeps pending work', () async {
    final abandoned = await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-1',
      endpoint: 'e',
      payload: const {},
    );
    await repository.markFailed(
      abandoned,
      httpCode: 409,
      refusal: OutboxRefusal.rejected,
    );
    await repository.enqueue(
      uploadType: 'personals',
      itemId: 'note-2',
      endpoint: 'e',
      payload: const {},
    );

    await repository.cleanup();
    expect(await database.outboxDao.getById(abandoned), isNull);
    expect(await repository.due(), hasLength(1));
  });

  group('an edit racing an in-flight drain', () {
    Future<String> queue(Map<String, dynamic> payload) => repository.enqueue(
      uploadType: 'voices',
      itemId: 'post-1',
      endpoint: 'https://planet.example/db/news',
      payload: payload,
    );

    test('a completing send does not delete a re-queued edit', () async {
      final id = await queue({'message': 'original'});
      // The drainer claims the row and has already read the old payload.
      await repository.markInProgress(id);

      // The user edits; `queuePending` re-enqueues with the new body.
      expect(await queue({'message': 'edited'}), id);

      // The in-flight send (carrying the OLD body) now succeeds.
      await repository.markCompleted(id);

      // Deleting here would drop the edit with no operation left to carry it,
      // and the post's `isEdited` marker is cleared by `markUploaded` — so the
      // edit would be lost outright rather than merely delayed.
      final remaining = await repository.due();
      expect(remaining, hasLength(1));
      expect(jsonDecode(remaining.single.payload), {'message': 'edited'});
    });

    test('an uncontested send still trims the row', () async {
      final id = await queue({'message': 'original'});
      await repository.markInProgress(id);

      await repository.markCompleted(id);

      expect(await database.outboxDao.getById(id), equals(null));
    });

    test('re-enqueuing does not reset a backing-off row', () async {
      final id = await queue({'message': 'original'});
      await repository.markInProgress(id);
      await repository.markFailed(id, errorMessage: 'server down');
      final backedOff = (await database.outboxDao.getById(id))!.nextAttemptAt;
      expect(await repository.due(), isEmpty);

      // `queuePending` runs after every user write, so this happens constantly.
      // Making the row due again here would retry a failing server on each
      // save and defeat the backoff entirely.
      await queue({'message': 'edited again'});

      expect((await database.outboxDao.getById(id))!.nextAttemptAt, backedOff);
      expect(await repository.due(), isEmpty);
    });
  });
}
