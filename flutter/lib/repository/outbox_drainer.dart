import 'dart:async';
import 'dart:convert';

import '../core/network/network_result.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_repository.dart';

/// What a drain attempt concluded about one operation.
enum OutboxOutcome {
  /// The server accepted it; the row is gone.
  completed,

  /// Transient — it will be retried after the backoff.
  retryScheduled,

  /// Permanent, or attempts exhausted. The row is kept as `abandoned` so the
  /// failure is inspectable rather than silently discarded.
  abandoned,
}

/// How one `uploadType` is sent.
///
/// Most write-backs are *appends* — a submission, a personal note — where the
/// stored payload is the record and replaying it verbatim is exactly right.
/// A few are *derived state*, most notably the shelf, where the correct body
/// is whatever the database says at send time; those register a handler that
/// rebuilds instead of replaying. Conflating the two is how a stale merge
/// silently reverts a change the user made after the failure.
typedef OutboxHandler =
    Future<NetworkResult<Map<String, dynamic>>> Function(
      OutboxRow row,
      Map<String, dynamic> payload,
      String? authHeader,
    );

/// Replaces `services/retry/RetryQueueWorker.kt`.
///
/// The Kotlin worker is a `CoroutineWorker` that `WorkManager` wakes on a
/// periodic schedule with a network constraint. Flutter has no equivalent that
/// runs while the app is closed, so the trigger changes rather than the queue:
/// [drain] is called on app resume and after a successful sync, and the queue
/// itself — being a SQLite table — carries pending work across process death
/// exactly as before.
///
/// The honest gap this leaves is documented in
/// `docs/kotlin-to-flutter-migration.md`: a write made offline is sent the next
/// time the app is opened with connectivity, not while it is closed.
class OutboxDrainer {
  OutboxDrainer(this._api, this._outbox, {Map<String, OutboxHandler>? handlers})
    : _handlers = handlers ?? const {};

  final PlanetApi _api;
  final OutboxRepository _outbox;
  final Map<String, OutboxHandler> _handlers;

  /// Single-flight guard, replacing `RetryQueue`'s `AtomicBoolean` + `Mutex`.
  ///
  /// Two overlapping drains would both claim the same due rows — the status
  /// flip to `in_progress` is not atomic against a concurrent `due()` — and
  /// double-post an append.
  Future<void>? _inFlight;

  bool get isDraining => _inFlight != null;

  /// Resets rows stranded `in_progress` by a kill mid-drain. Call once at
  /// startup, as `MainApplication` does via `recoverStuckOperations`.
  Future<void> recoverStuck() => _outbox.recoverStuck();

  /// Sends every operation whose backoff has elapsed.
  ///
  /// Concurrent calls join the run already in progress rather than starting a
  /// second one, so wiring this to both app-resume and sync-complete is safe.
  Future<List<OutboxOutcome>> drain({String? authHeader}) {
    final existing = _inFlight;
    if (existing != null) {
      return existing.then((_) => const <OutboxOutcome>[]);
    }

    final completer = Completer<List<OutboxOutcome>>();
    _inFlight = completer.future.then((_) {}, onError: (_) {});
    unawaited(
      _drain(authHeader: authHeader)
          .then(completer.complete)
          .catchError((Object e, StackTrace s) {
            completer.completeError(e, s);
          })
          .whenComplete(() {
            _inFlight = null;
          }),
    );
    return completer.future;
  }

  Future<List<OutboxOutcome>> _drain({String? authHeader}) async {
    final outcomes = <OutboxOutcome>[];
    for (final row in await _outbox.due()) {
      outcomes.add(await _send(row, authHeader: authHeader));
    }
    return outcomes;
  }

  Future<OutboxOutcome> _send(OutboxRow row, {String? authHeader}) async {
    Map<String, dynamic> payload;
    try {
      final decoded = jsonDecode(row.payload);
      if (decoded is! Map<String, dynamic>) throw const FormatException();
      payload = decoded;
    } on FormatException {
      // Kotlin abandons on an unparseable payload for the same reason: no
      // number of retries will make it parse.
      await _outbox.markFailed(
        row.id,
        errorMessage: 'Malformed payload',
        permanent: true,
      );
      return OutboxOutcome.abandoned;
    }

    await _outbox.markInProgress(row.id);

    final handler = _handlers[row.uploadType];
    final NetworkResult<Map<String, dynamic>> result;
    try {
      result = handler != null
          ? await handler(row, payload, authHeader)
          : await _api.sendJsonObject(
              row.endpoint,
              body: payload,
              method: row.httpMethod,
              authHeader: authHeader,
            );
    } catch (e) {
      // A handler runs repository and database code, so it can throw outside
      // NetworkResult. Left unguarded the row stays `in_progress` until the
      // next startup and the exception aborts the rest of the pass. A throw is
      // not evidence the server rejected the write, so it is retryable.
      final abandoned = await _outbox.markFailed(row.id, errorMessage: '$e');
      return abandoned ? OutboxOutcome.abandoned : OutboxOutcome.retryScheduled;
    }

    switch (result) {
      case NetworkSuccess<Map<String, dynamic>>():
        await _outbox.markCompleted(row.id);
        return OutboxOutcome.completed;

      case NetworkError<Map<String, dynamic>>(:final code, :final message):
        // `UploadCoordinator` treats `code >= 500` as retryable and everything
        // else — 409 conflict, 400, 401 — as permanent.
        final permanent = (code ?? 0) < 500;
        final abandoned = await _outbox.markFailed(
          row.id,
          errorMessage: message,
          httpCode: code,
          permanent: permanent,
        );
        return abandoned
            ? OutboxOutcome.abandoned
            : OutboxOutcome.retryScheduled;

      case NetworkException<Map<String, dynamic>>(:final error):
        // Transport failure: no response arrived, so the write may not have
        // been seen at all. Always retryable, as Kotlin does for IOException.
        final abandoned = await _outbox.markFailed(
          row.id,
          errorMessage: '$error',
        );
        return abandoned
            ? OutboxOutcome.abandoned
            : OutboxOutcome.retryScheduled;
    }
  }
}
