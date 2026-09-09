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

  /// Terminal, or attempts exhausted. The row is kept as `abandoned` so the
  /// failure is inspectable rather than silently discarded — and, since the
  /// row is also the memo [OutboxRepository.enqueue] reads, so that an
  /// identical request is not asked a second time.
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

/// The 409 arm: port of `UploadCoordinator.kt:169-204`, **corrected**.
///
/// Kotlin answers a conflict by GETting the document that already exists,
/// adopting its `_rev` and reporting the upload as a **success** — it runs the
/// same `afterUpload` callback the accepted path runs
/// (`UploadCoordinator.kt:169-186`). Ported literally that is a data-loss bug
/// for every uploader in this port but one, and the reason is worth stating
/// before the mechanism.
///
/// **A 409 never means "your write is already there".** It means the document
/// with this `_id` exists and the `_rev` you supplied — or the one you omitted
/// — is not its current revision, so *your bytes were not stored*. Adopting
/// the revision and reporting success marks the local row uploaded while the
/// server holds somebody else's content. The examination's readings, the
/// team document's edit, the achievement ledger: none of them ever leave the
/// device, and the outbox row is deleted as delivered.
///
/// The one place adopting is equivalent is a document whose content is a pure
/// function of shared inputs. `adopted_surveys_uploader.dart` is exactly that
/// — the clone id is `'${surveyId}_$teamId'` and two leaders adopting the same
/// survey for the same team author the *same* document — which is why the
/// port's single existing arm is correct where a general one would not be.
///
/// So the rule this class implements is the one that subsumes Kotlin's without
/// inheriting its loss: **GET the current revision and re-send the same
/// content under it.** The write lands. Where the content was idempotent
/// anyway the re-send is a harmless overwrite that yields the same `rev`
/// adopting would have.
///
/// ### Why this does not weaken Phase 148's policy
///
/// That policy is: *an `outbox` item owns exactly one row, for ever; a
/// terminal row is a memo, and nothing re-asks the identical question
/// unattended. What re-arms it is a changed request.*
///
/// The re-send here **is** a changed request — it carries a `_rev` the first
/// one did not, fetched from the server between the two. That is the recovery
/// route the policy names, taken inline instead of waiting for a pull to
/// supply the same revision. Three things keep it inside the policy rather
/// than around it:
///
/// * **It is bounded.** One GET and at most one re-send per drain attempt,
///   never a loop. A second 409 is returned as the refusal it is, and
///   [OutboxRepository.classifyStatus] makes it terminal exactly as before.
/// * **It never re-asks an identical question.** If the revision the server
///   reports is the one already in the payload, the re-send is skipped and the
///   original refusal is returned — there is nothing new to ask.
/// * **It cannot duplicate a document.** The arm fires only for a request that
///   names its own document, so the re-send is an update of that `_id`. An
///   append with a server-minted id cannot 409 in the first place, which is
///   why the eleven uploaders in that class are untouched and unaffected.
///
/// ### The trade it does make
///
/// Re-sending under the server's current revision is last-write-wins: a
/// concurrent edit made on another device is overwritten. That is the port's
/// existing stance everywhere else — `HealthRepository.cacheDocuments` skips a
/// locally dirty row (`health_repository.dart:772`), so the local copy is
/// already authoritative over the server's — and it is the better of the two
/// available mistakes. The alternative loses the *local* edit, silently, in a
/// queue whose entire purpose is delivering local writes; Kotlin's arm loses
/// it and reports success as well.
class ConflictRecovery {
  const ConflictRecovery._();

  /// Runs [attempt], and on a 409 fetches [documentUrl] and runs it once more
  /// under the revision that comes back.
  ///
  /// [attempt] is the uploader's own send, passed as a closure so the verb,
  /// the URL and any per-uploader decoration of the body stay where they
  /// belong. Everything the recovery itself decides lives here, once.
  ///
  /// A GET that fails returns the **original** 409 rather than inventing a
  /// verdict of its own — the same choice `adopted_surveys_uploader.dart`
  /// made, and the same one Kotlin makes for a non-successful fetch
  /// (`UploadCoordinator.kt:187-192`, `retryable = false`, `httpCode = 409`).
  static Future<NetworkResult<Map<String, dynamic>>> send({
    required PlanetApi api,
    required String documentUrl,
    required Map<String, dynamic> payload,
    required Future<NetworkResult<Map<String, dynamic>>> Function(
      Map<String, dynamic> body,
    )
    attempt,
    String? authHeader,
  }) async {
    final first = await attempt(payload);
    if (first is! NetworkError<Map<String, dynamic>> || first.code != 409) {
      return first;
    }

    // The drain's credential, not none: `outbox.endpoint` is stored
    // credential-free on purpose, so an unauthenticated read of a CouchDB
    // document is a 401 and this recovery would never fire.
    final existing = await api.getJsonObject(
      documentUrl,
      authHeader: authHeader,
    );
    if (existing is! NetworkSuccess<Map<String, dynamic>>) return first;

    final rev = existing.data['_rev'];
    if (rev is! String || rev.isEmpty) return first;
    // Nothing new to ask. Re-sending the bytes the server has just refused is
    // precisely what Phase 148's memo exists to stop.
    if (rev == payload['_rev']) return first;

    return attempt({...payload, '_rev': rev});
  }
}

/// Replaces `services/retry/RetryQueueWorker.kt`.
///
/// The queue remains SQLite-backed and therefore survives process death.
/// [drain] has two triggers: app startup/resume for low latency and the
/// constraint-aware headless job in `background_entrypoint.dart` for delivery
/// while the UI process is closed.
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
  ///
  /// [onlyTypes] restricts the pass to those `uploadType`s. It exists for one
  /// case: a public-survey respondent has no server configuration, so there is
  /// no credential for the rest of the queue and posting it unauthenticated
  /// would earn a 401 on writes that are perfectly deliverable later. Draining
  /// only the credential-free type leaves those rows untouched.
  ///
  /// A 401 is [OutboxRefusal.transient] now, so this is no longer the only
  /// thing standing between an unauthenticated pass and a queue of abandoned
  /// rows — it still spends attempts from those rows' ladders, which is reason
  /// enough to keep it.
  Future<List<OutboxOutcome>> drain({
    String? authHeader,
    Set<String>? onlyTypes,
  }) {
    final existing = _inFlight;
    if (existing != null) {
      return existing.then((_) => const <OutboxOutcome>[]);
    }

    final completer = Completer<List<OutboxOutcome>>();
    _inFlight = completer.future.then((_) {}, onError: (_) {});
    unawaited(
      _drain(authHeader: authHeader, onlyTypes: onlyTypes)
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

  Future<List<OutboxOutcome>> _drain({
    String? authHeader,
    Set<String>? onlyTypes,
  }) async {
    final outcomes = <OutboxOutcome>[];
    for (final row in await _outbox.due()) {
      if (onlyTypes != null && !onlyTypes.contains(row.uploadType)) continue;
      // Null means another isolate claimed the row first — see `_send`.
      final outcome = await _send(row, authHeader: authHeader);
      if (outcome != null) outcomes.add(outcome);
    }
    return outcomes;
  }

  Future<OutboxOutcome?> _send(OutboxRow row, {String? authHeader}) async {
    // The UI and WorkManager use separate Dart isolates and therefore have
    // separate in-memory single-flight guards. The status-scoped SQL update is
    // the cross-isolate lock: a losing drainer skips the row before sending.
    if (!await _outbox.markInProgress(row.id)) return null;

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
        refusal: OutboxRefusal.rejected,
      );
      return OutboxOutcome.abandoned;
    }

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
        // A **null** code can only have come from a handler. `PlanetApi`
        // builds a `NetworkError` only from a response it actually received,
        // and substitutes `0` for a missing status
        // (`planet_api.dart:249-252`), so every transport-authored error
        // carries an int. A handler-authored one usually means: the send
        // succeeded and the response was not something the handler could use.
        // The write may already be on the server, which is why that is
        // [OutboxRefusal.indeterminate] rather than a failure to deliver.
        //
        // *Usually*, not always — and the exception is worth knowing before
        // relying on this. Three handlers return a null code **before making
        // any request**: `user_uploader.dart:126` and `:143-147`, and
        // `achievements_uploader.dart:63`. For those the write certainly did
        // not land, so `indeterminate` overstates what is known; the effect is
        // the same either way (terminal, not re-sent) and the first of them is
        // genuinely terminal — the local row is gone — but a handler that has
        // sent nothing should say so with [OutboxRepository.notSent] rather
        // than let this branch guess. See `PHASE_148_NOTES.md`.
        //
        // The previous rule was `permanent = (code ?? 0) < 500`, a port of
        // `UploadCoordinator.kt:211`. It mapped null to 0 to "permanent" and
        // abandoned such a row on its first attempt, and — because
        // `enqueue` used to mint a fresh row per sweep — re-POSTed it forever
        // after. See [OutboxRefusal] and `PHASE_148_NOTES.md`.
        final refusal = code == null
            ? OutboxRefusal.indeterminate
            : OutboxRepository.classifyStatus(code);
        final abandoned = await _outbox.markFailed(
          row.id,
          errorMessage: message,
          httpCode: code,
          refusal: refusal,
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
