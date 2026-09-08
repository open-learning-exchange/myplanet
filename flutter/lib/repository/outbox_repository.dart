import 'dart:convert';
import 'dart:math';

import 'package:drift/drift.dart';

import '../data/local/app_database.dart';

/// Why a drain attempt failed, and therefore what a later enqueue of the *same
/// request* is entitled to do about it.
///
/// The port's outbox admits every write and classifies at drain time; Kotlin's
/// `RetryQueue.queueFailedOperation` does the opposite and refuses to queue a
/// non-retryable error at all (`RetryQueue.kt:36-39`). Because a refused write
/// therefore leaves no row in Kotlin, Kotlin needs no policy for one. This
/// enum is that policy — the port's structural equivalent of that early
/// return, applied at re-enqueue instead of at admission.
enum OutboxRefusal {
  /// The failure was about the transport, the moment, or the caller — not the
  /// bytes. 5xx, a timeout, a rate limit, a missing or rejected credential, a
  /// handler that threw. Retried under the backoff, and a later sweep may
  /// re-arm it: the same request could well succeed once the environment
  /// changes.
  transient,

  /// The server considered this exact request and refused it — a 400, a 413, a
  /// 409 against a stale `_rev`. Resending the same bytes cannot succeed, so
  /// nothing but a *changed* request re-arms it. (A 404 is deliberately not
  /// here; see [OutboxRepository.retryableStatuses].)
  rejected,

  /// The transport reported success and the handler could not use what came
  /// back — a 2xx whose body carries no usable `rev`/`id`. The write may
  /// already be on the server, which makes this the one class where resending
  /// is worse than not: it risks a second copy of a document that is already
  /// filed. Terminal for the same reason as [rejected], for a different
  /// reason.
  indeterminate,
}

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

  /// Recorded in `outbox.httpCode` when an attempt ended terminally without an
  /// HTTP status of its own — a handler's verdict on a 2xx body, or a payload
  /// that will not parse. Negative, so it cannot collide with a status code,
  /// and distinct from `null`, which keeps its existing meaning: *no response
  /// arrived*, which says nothing about the request and stays retryable.
  static const int noUsableResponse = -1;

  /// The statuses that say something about the caller, the moment or the
  /// server's configuration rather than about the request. Retried, and
  /// re-armed by a later sweep.
  ///
  /// 401 and 403 are a credential or a role, 408 and 429 are the moment, and
  /// 404 on a CouchDB *write* is the database not being there — a document
  /// that does not exist is what a POST or a PUT is for, so a 404 can only be
  /// about the endpoint. Every one of those is fixed on the server or in the
  /// app's configuration, and none of those fixes changes the request; if they
  /// were terminal, [enqueue]'s memo would have nothing to re-arm them with.
  /// What remains terminal — 400, 409, 413, 415, 422 — is about the bytes, and
  /// each has a local repair that *does* change them: an edit, or a pull that
  /// supplies the revision the conflict was about.
  ///
  /// **This is a deliberate deviation from Kotlin**, whose one rule is
  /// `retryable = response.code() >= 500` (`UploadCoordinator.kt:211`). Under
  /// that rule a 401 is non-retryable, so `RetryQueue` drops it — and the
  /// write is re-attempted anyway, because Kotlin's sweep re-reads the live
  /// table on every sync. The port's sweep re-*enqueues*, so reaching the same
  /// outcome needs the status classified as retryable here instead. The
  /// drainer's `onlyTypes` guard, which exists because a 401 used to abandon
  /// deliverable writes, is now belt-and-braces rather than the only defence.
  static const Set<int> retryableStatuses = {401, 403, 404, 408, 429};

  /// The verdict [enqueue] reaches from the code a previous drain recorded.
  ///
  /// It has to agree with the drainer's own reading, so both go through here.
  /// `null` is *no status recorded* — a transport failure, or a row that has
  /// never been attempted.
  static OutboxRefusal classifyStatus(int? httpCode) {
    if (httpCode == null) return OutboxRefusal.transient;
    if (httpCode == noUsableResponse) return OutboxRefusal.indeterminate;
    if (httpCode == 0 || httpCode >= 500) return OutboxRefusal.transient;
    if (retryableStatuses.contains(httpCode)) return OutboxRefusal.transient;
    return OutboxRefusal.rejected;
  }

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

  /// Queues a write against the single row this `(uploadType, itemId)` owns.
  ///
  /// Kotlin's `queueFailedOperation` keys on `(itemId, uploadType)` for the
  /// same reason: retrying a submission must not enqueue a second copy of it.
  /// The port holds the stronger invariant — **at most one `outbox` row per
  /// `(uploadType, itemId)`, whatever its status** — because unlike Kotlin it
  /// keeps a refused operation rather than declining to queue it.
  ///
  /// Before this, `findOpen` was the lookup, and it deliberately ignores an
  /// `abandoned` row so a fresh enqueue is never blocked by one. The
  /// consequence was that every sweep after a permanent refusal minted a
  /// *new* row and made one more doomed POST, for the life of an install, in
  /// a table that survives schema bumps. See `PHASE_148_NOTES.md`.
  ///
  /// What re-arms a terminal row is a **changed request** — a different
  /// payload, endpoint or verb. That is the whole recovery story, and it is
  /// enough: a user edit changes the payload, a pull that supplies the
  /// revision a 409 was about changes the payload, a reconfigured server
  /// changes the endpoint. What does *not* re-arm one is asking the server the
  /// identical question a second time.
  Future<String> enqueue({
    required String uploadType,
    required String itemId,
    required String endpoint,
    required Map<String, dynamic> payload,
    String httpMethod = 'POST',
    String? userId,
    int maxAttempts = defaultMaxAttempts,
  }) async {
    final encoded = jsonEncode(payload);
    final existing = await _soleRowFor(uploadType, itemId);

    if (existing != null) {
      // Refresh the request rather than keeping the first one: the row may
      // have been edited between the failure and this call.
      final base = OutboxEntriesCompanion(
        payload: Value(encoded),
        endpoint: Value(endpoint),
        httpMethod: Value(httpMethod),
      );

      switch (existing.status) {
        // A drain may already have claimed this row and read the *old*
        // payload. Putting it back to `pending` is what stops `markCompleted`
        // deleting it when that in-flight send succeeds, so the edit still
        // goes out on the next pass instead of vanishing with the row.
        case OutboxDao.statusInProgress:
          await _dao.patch(
            existing.id,
            base.copyWith(
              status: const Value(OutboxDao.statusPending),
              nextAttemptAt: Value(_now().millisecondsSinceEpoch),
            ),
          );

        // Merely backing off. `nextAttemptAt` is deliberately left alone:
        // `queuePending` re-enqueues every pending item after each user write,
        // so making the row due here would retry a failing server on every
        // keystroke-driven save and defeat the backoff outright.
        case OutboxDao.statusPending:
          await _dao.patch(existing.id, base);

        // Terminal — abandoned, or the `completed` status nothing writes.
        default:
          final sameRequest =
              existing.payload == encoded &&
              existing.endpoint == endpoint &&
              existing.httpMethod == httpMethod;
          if (sameRequest &&
              classifyStatus(existing.httpCode) != OutboxRefusal.transient) {
            // The memo. The server has already answered this exact question,
            // or answered in a way that leaves the write's fate unknown;
            // asking again cannot help and may file a second copy. The row
            // stays where `OutboxDao.abandoned` can still read it, which is
            // what the health screen's caution banner counts.
            return existing.id;
          }
          await _dao.patch(
            existing.id,
            base.copyWith(
              status: const Value(OutboxDao.statusPending),
              // A fresh ladder: the previous attempts were spent on a request
              // that is no longer the one being made, or on a failure that was
              // never about the request.
              attemptCount: const Value(0),
              nextAttemptAt: Value(_now().millisecondsSinceEpoch),
              errorMessage: const Value(null),
              httpCode: const Value(null),
              maxAttempts: Value(maxAttempts),
              userId: Value(userId),
            ),
          );
      }
      return existing.id;
    }

    final id = _createId();
    final nowMs = _now().millisecondsSinceEpoch;
    await _dao.upsert(
      OutboxEntriesCompanion.insert(
        id: id,
        uploadType: uploadType,
        itemId: itemId,
        payload: encoded,
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

  /// The one row this item owns, collapsing any surplus an older build left.
  ///
  /// An install upgrading into this policy can already carry a pile of
  /// abandoned rows for a single item — one per sweep since the first refusal
  /// — and `outbox` is preserved across schema bumps, so they do not go away
  /// on their own. Keeping the newest and dropping the rest bounds the table
  /// retroactively without losing the diagnostic: what a clinician needs is
  /// *which record is stranded and why*, and the newest row carries the most
  /// recent answer to both.
  ///
  /// An open row always wins over a terminal one, whatever their ages: it is
  /// the live operation, and there can be at most one because every path here
  /// goes through this method.
  Future<OutboxRow?> _soleRowFor(String uploadType, String itemId) async {
    final rows = await _dao.forItem(uploadType, itemId);
    if (rows.isEmpty) return null;
    if (rows.length == 1) return rows.single;

    final keep = rows.firstWhere(
      (row) =>
          row.status == OutboxDao.statusPending ||
          row.status == OutboxDao.statusInProgress,
      // `forItem` orders by `createdAt` ascending.
      orElse: () => rows.last,
    );
    for (final row in rows) {
      if (row.id != keep.id) await _dao.deleteById(row.id);
    }
    return keep;
  }

  Future<List<OutboxRow>> due() => _dao.due(_now().millisecondsSinceEpoch);

  /// Whether a send for this item is on the wire right now.
  ///
  /// [enqueue] deliberately puts an `in_progress` row back to `pending` so a
  /// payload edited mid-flight is not lost with the row `markCompleted`
  /// deletes — and `markCompleted` is `deleteIfInProgress`, so the send that
  /// just succeeded then deletes nothing and the row survives, `pending`, with
  /// the same body. For *derived state* that is exactly right: the shelf's
  /// handler rebuilds from the database, so a replay re-sends current truth.
  ///
  /// For an **append** it is a duplicate. A submission is an append, so its
  /// uploader asks this first and leaves an in-flight row alone rather than
  /// re-enqueueing over it. Kept here rather than as a bare `findOpen`
  /// passthrough so the distinction that makes it necessary has one home; see
  /// [OutboxHandler] for the append-versus-derived-state split it turns on.
  Future<bool> isInFlight(String uploadType, String itemId) async =>
      (await _dao.findOpen(uploadType, itemId))?.status ==
      OutboxDao.statusInProgress;

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

  /// Puts a terminal row back on the wire even though nothing about the
  /// request has changed — the deliberate override of [enqueue]'s memo.
  ///
  /// The memo is right for a sweep, which is the app guessing that something
  /// might have changed. It is wrong for a person tapping *Retry* on a screen
  /// that has just told them a record is stranded: they may know something the
  /// device does not (an administrator fixed the server, another handset's
  /// conflicting edit was withdrawn), and refusing to ask would leave them
  /// with a warning and no action. One extra POST is the right price for an
  /// explicit instruction; it is only the *unattended* repetition that had to
  /// stop.
  ///
  /// A no-op when the item has no terminal row. Returns whether anything was
  /// re-armed.
  Future<bool> rearm(String uploadType, String itemId) async {
    final row = await _soleRowFor(uploadType, itemId);
    if (row == null) return false;
    if (row.status == OutboxDao.statusPending ||
        row.status == OutboxDao.statusInProgress) {
      return false;
    }
    final nowMs = _now().millisecondsSinceEpoch;
    await _dao.patch(
      row.id,
      OutboxEntriesCompanion(
        status: const Value(OutboxDao.statusPending),
        attemptCount: const Value(0),
        nextAttemptAt: Value(nowMs),
        errorMessage: const Value(null),
        httpCode: const Value(null),
      ),
    );
    return true;
  }

  /// Forgets the permanent failures recorded against one item.
  ///
  /// Called when a later attempt succeeds. [enqueue] now reuses the item's one
  /// row rather than minting another, so on a current install `markCompleted`
  /// has already removed it; this clears the pile an older build left behind,
  /// which `outbox` being a preserved table would otherwise carry across every
  /// future upgrade. [cleanup] still has no caller — see `PHASE_148_NOTES.md`
  /// for why deleting the diagnostic wholesale is not the answer.
  Future<int> clearAbandoned(String uploadType, String itemId) =>
      _dao.clearAbandonedFor(uploadType, itemId);

  /// Records a failed attempt and schedules the next one.
  ///
  /// Returns `true` if the operation was abandoned — attempts exhausted, or
  /// [refusal] was terminal. `RetryQueue` refuses to queue a non-retryable
  /// error at all; the equivalent here is the terminal [refusal], since by this
  /// point the operation is already queued.
  ///
  /// A terminal refusal is recorded so that [enqueue] can read it back later:
  /// that is what [noUsableResponse] is for. Without it a handler's verdict on
  /// a 2xx body and a transport failure would both store a null `httpCode`,
  /// and the two want opposite treatment — the first must never be sent again,
  /// the second must be.
  Future<bool> markFailed(
    String id, {
    String? errorMessage,
    int? httpCode,
    OutboxRefusal refusal = OutboxRefusal.transient,
  }) async {
    final row = await _dao.getById(id);
    if (row == null) return false;

    final attempts = row.attemptCount + 1;
    final nowMs = _now().millisecondsSinceEpoch;
    final terminal = refusal != OutboxRefusal.transient;
    final abandoned = terminal || attempts >= row.maxAttempts;

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
        httpCode: Value(httpCode ?? (terminal ? noUsableResponse : null)),
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
