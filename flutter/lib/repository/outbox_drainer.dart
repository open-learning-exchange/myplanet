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

/// The 409 arm: port of `UploadCoordinator.kt:169-204`, **split in two**.
///
/// Kotlin answers a conflict by GETting the document that already exists,
/// adopting its `_rev` and reporting the upload as a **success** — the
/// recovered item joins `succeeded`, so `updateDatabaseBatch` runs the
/// config's `markUploaded` exactly as an accepted write would
/// (`UploadCoordinator.kt:169-204`, `:241-257`). It never compares the
/// document it fetched with the one it was sending, and that is the half a
/// port cannot take on trust.
///
/// **A 409 does not mean "your write is already there".** It means a document
/// with this `_id` exists and the `_rev` supplied — or omitted — is not its
/// current revision, so *these bytes were not stored*. Whether adopting is
/// harmless therefore depends on something Kotlin never asks: is the document
/// already on the server the document being sent?
///
/// The payload answers it well enough to act on, and the two answers need
/// opposite treatment:
///
/// * **An update** — the payload carries a `_rev`, so this device has
///   published this document before and is sending a change to it. The 409
///   says that revision is stale. Adopting would clear the dirty flag with the
///   change still on the handset: the learner's added answers, the clinician's
///   edited profile, the achievement entry. So an update is **re-sent under
///   the revision the GET reports**. The write lands.
/// * **A create** — no `_rev`, so as far as this device knows the document has
///   never been published, and the one on the server is content it has never
///   seen. Re-sending would overwrite that; adopting would report a delivery
///   that did not happen. Neither is safe in general, so the default is
///   **neither**: the refusal stands, terminal, and `OutboxDao.abandoned`
///   keeps reporting the row as stranded — which is the honest answer.
///   [adoptExisting] is the opt-in for the one uploader that can prove the
///   question away.
///
/// ### Where this sits relative to Kotlin
///
/// Only six of the port's twenty uploaders have a Kotlin counterpart that runs
/// through `UploadCoordinator` at all. Kotlin's own health, achievements, news
/// and teams paths swallow a 409 in silence
/// (`HealthRepositoryImpl.kt:110-124`, `AchievementUploader.kt:32-42`,
/// `UploadManager.kt:369-372`, `TeamsUploader.kt:73-75`), and
/// `RetryQueueWorker.kt:234-238` discards the edit with a success log. So for
/// most uploaders here this is **new behaviour, not restored parity**, and the
/// update arm has no Kotlin precedent anywhere.
///
/// ### Why this does not weaken Phase 148's policy
///
/// That policy is: *an `outbox` item owns exactly one row, for ever; a
/// terminal row is a memo, and nothing re-asks the identical question
/// unattended. What re-arms it is a changed request.*
///
/// The re-send **is** a changed request — it carries a `_rev` the first one
/// did not, fetched from the server between the two. That is the recovery
/// route the policy names, taken inline instead of waiting for a pull to
/// supply the same revision. Three things keep it inside the policy rather
/// than around it:
///
/// * **It is bounded.** One GET and at most one re-send per drain attempt,
///   never a loop. A second 409 is returned as the refusal it is, and
///   [OutboxRepository.classifyStatus] makes it terminal exactly as before.
/// * **It never re-asks an identical question.** If the revision the server
///   reports is the one already in the payload, the re-send is skipped and the
///   original refusal stands — there is nothing new to ask.
/// * **It cannot duplicate a document.** The arm fires only for a request that
///   names its own document, so a re-send is an update of that `_id`. An
///   append with a server-minted id cannot 409 in the first place, which is
///   why the eight uploaders in that class are deliberately not armed —
///   see [documentUrlUnder].
///
/// ### The trade the update arm makes
///
/// Re-sending under the server's current revision is last-write-wins: a
/// concurrent edit made on another device is overwritten. That is the port's
/// existing stance elsewhere — `HealthRepository.cacheDocuments` skips a
/// locally dirty row (`health_repository.dart:772`), so the local copy is
/// already authoritative over the server's — and it is the better of the two
/// available mistakes, because the alternative loses the *local* edit silently
/// in a queue whose whole purpose is delivering local writes.
///
/// **One case deserves naming rather than falling out of the rule.** A team
/// tombstone is `{_id, _rev, _deleted: true}` (`teams_provider.dart:295`), so
/// it is an update and re-sending deletes a document whose latest content this
/// device has never seen. That is accepted deliberately: the user asked for
/// the membership to be removed, and a revision bump from another device does
/// not revoke that instruction. The alternative — adopting — would report the
/// delete as done while the document is still on the server *and* hand the row
/// back to `deleteNotIn`.
class ConflictRecovery {
  const ConflictRecovery._();

  /// Where CouchDB keeps the document [payload] names, given the URL the write
  /// was sent to.
  ///
  /// Null when the payload names no document, which is the guard that keeps
  /// the arm off appends — see [send].
  ///
  /// **The id is the payload's, not the outbox row's**, and conflating the two
  /// is the trap here. `outbox.itemId` is the *local* row's key; the CouchDB
  /// `_id` is whatever the serializer chose, and for health those are
  /// different values — the row's key is the examination's, while
  /// `HealthRepository.serialize` writes the **patient's** user id as `_id`
  /// (`health_repository.dart:845-849`). A recovery keyed on `itemId` would
  /// GET a document that does not exist, take the 404, and quietly return the
  /// original conflict for ever.
  static String? documentUrlUnder(
    String sendUrl,
    Map<String, dynamic> payload,
  ) {
    final id = payload['_id'];
    if (id is! String || id.isEmpty) return null;
    return '$sendUrl/${Uri.encodeComponent(id)}';
  }

  /// Runs [attempt], and on a 409 decides between re-sending and standing
  /// down, per the class docs.
  ///
  /// [attempt] is the uploader's own send, passed as a closure so the verb,
  /// the URL and any per-uploader decoration of the body stay where they
  /// belong. Everything the recovery itself decides lives here, once — and
  /// because a recovered result comes back through this method's return, the
  /// handler's existing `if (result case NetworkSuccess…)` branch runs on it
  /// unchanged. No uploader duplicates its own `markUploaded`, and none can
  /// forget to run it.
  ///
  /// [adoptExisting] opts one uploader into Kotlin's original semantics for a
  /// **create**: fetch the revision and report success without sending
  /// anything. It is sound only where the document already on the server is
  /// necessarily the document being sent, which is a property of the content,
  /// not of the verb. `adopted_surveys_uploader.dart` has it — a team's clone
  /// is a pure function of the survey and the team, and its id
  /// (`'${surveyId}_$teamId'`) makes two leaders author the same document.
  /// Nothing else in the port can make that claim, so nothing else sets it.
  ///
  /// A GET that fails returns the **original** 409 rather than inventing a
  /// verdict of its own — the same choice Kotlin makes for a non-successful
  /// fetch (`UploadCoordinator.kt:185-192`).
  static Future<NetworkResult<Map<String, dynamic>>> send({
    required PlanetApi api,
    required String? documentUrl,
    required Map<String, dynamic> payload,
    required Future<NetworkResult<Map<String, dynamic>>> Function(
      Map<String, dynamic> body,
    )
    attempt,
    String? authHeader,
    bool adoptExisting = false,
  }) async {
    final first = await attempt(payload);
    if (first is! NetworkError<Map<String, dynamic>> || first.code != 409) {
      return first;
    }
    // A request that does not name its own document cannot be recovered and
    // must not be: without an `_id` the re-send would be an append, and a
    // second copy of a document with a server-minted id is undetectable
    // afterwards. [documentUrlUnder] returns null for exactly that case.
    if (documentUrl == null) return first;

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

    final sentRev = payload['_rev'];
    final isUpdate = sentRev is String && sentRev.isNotEmpty;
    if (!isUpdate) {
      if (!adoptExisting) return first;
      // Kotlin's arm, shaped like the create response the handler expects: it
      // reads `id`/`rev` from a write, while a document read carries `_id` and
      // `_rev` (`UploadCoordinator.kt:177-182` does the same translation).
      final id = existing.data['_id'] ?? payload['_id'];
      return NetworkSuccess<Map<String, dynamic>>({'id': id, 'rev': rev});
    }

    // Nothing new to ask. Re-sending bytes the server has just refused is
    // precisely what Phase 148's memo exists to stop.
    if (rev == sentRev) return first;

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
