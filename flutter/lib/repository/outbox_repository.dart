import 'dart:convert';
import 'dart:math';

import 'package:drift/drift.dart';

import '../data/local/app_database.dart';

/// Port of `services/retry/RetryQueue.kt` and the `RetryOperation` companion.
///
/// This is the durable half of the Kotlin write-back path. `RetryQueue` owns
/// the queue; `RetryQueueWorker` only decides when to drain it. Because the
/// queue lives in SQLite, it already survives process death on its own — so
/// the absent Flutter `WorkManager` equivalent costs *scheduling*, not
/// durability. See [OutboxDrainer] for the replacement trigger.
class OutboxRepository {
  OutboxRepository(
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _randomId;

  /// `RetryOperation.BASE_DELAY_MS`.
  static const Duration baseDelay = Duration(seconds: 30);

  /// `RetryOperation.MAX_DELAY_MS`.
  static const Duration maxDelay = Duration(minutes: 30);

  /// `RetryOperation.DEFAULT_MAX_ATTEMPTS`.
  static const int defaultMaxAttempts = 5;

  /// A worker gets ten minutes on Android. Twice that window avoids stealing
  /// a live claim when a foreground drain overlaps a headless isolate, while
  /// still recovering promptly after a killed process.
  static const Duration stuckClaimTimeout = Duration(minutes: 20);

  final OutboxDao _dao;
  final DateTime Function() _now;
  final String Function() _createId;

  Stream<int> watchPendingCount() => _dao.watchPendingCount();

  /// Exponential backoff, matching `RetryOperation.calculateNextRetryTime`:
  /// `min(BASE * 2^attempt, MAX)`. With the Kotlin constants the first retry
  /// waits a minute and the fifth caps out at thirty.
  ///
  /// The shift is clamped before it is applied. Kotlin bounds it implicitly
  /// through `maxAttempts`, but a caller passing a large [attemptCount] here
  /// would otherwise overflow the shift rather than saturating at [maxDelay].
  static Duration backoffFor(int attemptCount) {
    final safeAttempt = attemptCount.clamp(0, 32);
    final scaled = baseDelay.inMilliseconds * (1 << safeAttempt);
    return Duration(milliseconds: min(scaled, maxDelay.inMilliseconds));
  }

  /// Queues a write, or records another failed attempt against the one already
  /// queued for this item.
  ///
  /// Kotlin's `queueFailedOperation` keys on `(itemId, uploadType)` for the
  /// same reason: retrying a submission must not enqueue a second copy of it.
  Future<String> enqueue({
    required String uploadType,
    required String itemId,
    required String endpoint,
    required Map<String, dynamic> payload,
    String httpMethod = 'POST',
    String? userId,
    int maxAttempts = defaultMaxAttempts,
  }) async {
    final existing = await _dao.findOpen(uploadType, itemId);
    if (existing != null) {
      // Refresh the payload rather than keeping the first one: the row may have
      // been edited between the failure and this call.
      final base = OutboxEntriesCompanion(
        payload: Value(jsonEncode(payload)),
        endpoint: Value(endpoint),
        httpMethod: Value(httpMethod),
      );
      // A drain may already have claimed this row and read the *old* payload.
      // Putting it back to `pending` is what stops `markCompleted` deleting it
      // when that in-flight send succeeds, so the edit still goes out on the
      // next pass instead of vanishing with the row.
      //
      // `nextAttemptAt` is only made due on that transition. Resetting it for a
      // row that is merely backing off would defeat the backoff outright —
      // `queuePending` re-enqueues every pending item after each user write, so
      // a failing operation would be retried on every keystroke-driven save.
      await _dao.patch(
        existing.id,
        existing.status == OutboxDao.statusInProgress
            ? base.copyWith(
                status: const Value(OutboxDao.statusPending),
                nextAttemptAt: Value(_now().millisecondsSinceEpoch),
              )
            : base,
      );
      return existing.id;
    }

    final id = _createId();
    final nowMs = _now().millisecondsSinceEpoch;
    await _dao.upsert(
      OutboxEntriesCompanion.insert(
        id: id,
        uploadType: uploadType,
        itemId: itemId,
        payload: jsonEncode(payload),
        endpoint: endpoint,
        httpMethod: Value(httpMethod),
        status: const Value(OutboxDao.statusPending),
        maxAttempts: Value(maxAttempts),
        createdAt: nowMs,
        // Due immediately: the first send has not been tried yet, so there is
        // nothing to back off from.
        nextAttemptAt: Value(nowMs),
        userId: Value(userId),
      ),
    );
    return id;
  }

  Future<List<OutboxRow>> due() => _dao.due(_now().millisecondsSinceEpoch);

  /// Withdraws a queued operation whose subject no longer exists.
  ///
  /// The Kotlin upload path has no equivalent because it reads the live table
  /// at send time — a record deleted before the upload runs is simply not in
  /// the list. A durable queue outlives its subject, so deleting a locally
  /// composed post that is already queued would otherwise still POST it and
  /// resurrect it on the server. Returns whether anything was withdrawn.
  /// An operation mid-flight is left alone: the request may already have
  /// reached the server, and the drainer still needs the row to record the
  /// outcome against. That exclusion is expressed as part of the delete rather
  /// than checked first — a separate check would let a drain claim the row
  /// between the two statements.
  Future<bool> cancel(String uploadType, String itemId) async =>
      await _dao.deletePending(uploadType, itemId) > 0;

  Future<bool> markInProgress(String id) =>
      _dao.claim(id, _now().millisecondsSinceEpoch);

  /// Drops the row outright rather than marking it `completed`.
  ///
  /// Kotlin marks completed and sweeps later in `cleanup()`. Deleting here
  /// makes the queue self-trimming, so a long-lived install cannot accumulate
  /// a table of successful uploads it will never read again.
  ///
  /// Scoped to rows still `in_progress`: if [enqueue] refreshed the payload
  /// mid-flight it also put the row back to `pending`, and the send that just
  /// succeeded carried the superseded body.
  Future<void> markCompleted(String id) => _dao.deleteIfInProgress(id);

  /// Forgets the permanent failures recorded against one item.
  ///
  /// Called when a later attempt succeeds: [enqueue] never reuses an abandoned
  /// row and [cleanup] has no caller, so the record of a refusal outlives the
  /// refusal itself unless something withdraws it.
  Future<int> clearAbandoned(String uploadType, String itemId) =>
      _dao.clearAbandonedFor(uploadType, itemId);

  /// Records a failed attempt and schedules the next one.
  ///
  /// Returns `true` if the operation was abandoned — attempts exhausted, or the
  /// failure was permanent. `RetryQueue` refuses to queue a non-retryable error
  /// at all; the equivalent here is [permanent], since by this point the
  /// operation is already queued.
  Future<bool> markFailed(
    String id, {
    String? errorMessage,
    int? httpCode,
    bool permanent = false,
  }) async {
    final row = await _dao.getById(id);
    if (row == null) return false;

    final attempts = row.attemptCount + 1;
    final nowMs = _now().millisecondsSinceEpoch;
    final abandoned = permanent || attempts >= row.maxAttempts;

    await _dao.patch(
      id,
      OutboxEntriesCompanion(
        status: Value(
          abandoned ? OutboxDao.statusAbandoned : OutboxDao.statusPending,
        ),
        attemptCount: Value(attempts),
        lastAttemptAt: Value(nowMs),
        nextAttemptAt: Value(nowMs + backoffFor(attempts).inMilliseconds),
        errorMessage: Value(errorMessage),
        httpCode: Value(httpCode),
      ),
    );
    return abandoned;
  }

  /// Port of `recoverStuckOperations`, for the startup path.
  Future<int> recoverStuck() => _dao.recoverStuck(
    _now().subtract(stuckClaimTimeout).millisecondsSinceEpoch,
  );

  Future<int> cleanup() => _dao.cleanup();
}

String _randomId() {
  final timestamp = DateTime.now().microsecondsSinceEpoch;
  final random = Random.secure().nextInt(1 << 32);
  return '$timestamp-$random';
}
