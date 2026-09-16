import 'dart:convert';

import 'package:drift/drift.dart' show Value;

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/feedback_mapper.dart';
import 'feedback_repository.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Durable write-back for the `feedback` database.
///
/// Kotlin uploads feedback from `AutoSyncWorker` through
/// `UploadManager.uploadFeedback`. The outbox carries it here: queued on write,
/// then drained on app resume or by the constraint-aware background job.
/// Without this the port filed feedback that never left the device —
/// `getPendingFeedback` existed but nothing called it.
class FeedbackUploader {
  FeedbackUploader(
    this._api,
    this._repository,
    this._dao,
    this._outbox,
    this._identity,
  );

  static const type = 'feedback';

  final PlanetApi _api;
  final FeedbackRepository _repository;
  final FeedbackDao _dao;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/feedback';

  /// Queues every filed-but-unsent feedback thread.
  ///
  /// `Feedback.serializeFeedback` gained `addDocumentOrigin()` in `27c0470`,
  /// a pure addition — so `androidId` and `app` are both new on the wire, and
  /// the Kotlin sends no device name here ([DeviceIdentity.originFields], not
  /// `documentFields`). The read is guarded on an empty list so a pass with
  /// nothing to send makes no platform-channel call.
  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _repository.getPendingFeedback();
    final identity = rows.isEmpty ? null : await _identity.read();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: {...FeedbackMapper.toDoc(row), ...identity!.originFields},
        userId: userId,
      );
    }
    return rows.length;
  }

  OutboxHandler get handler => (row, payload, authHeader) async {
    // The 409 arm: `FeedbackMapper.toDoc` sends a device-generated `_id` so a
    // reply updates the thread rather than duplicating it, which also means a
    // stale `_rev` conflicts. See [ConflictRecovery].
    final documentUrl = ConflictRecovery.documentUrlUnder(
      row.endpoint,
      payload,
    );
    final sentRev = payload['_rev'];
    // What the reconcile actually put on the wire, when it ran. Held so the
    // row can be brought back into step with it below.
    Map<String, dynamic>? sent;
    final result = await ConflictRecovery.send(
      api: _api,
      documentUrl: documentUrl,
      payload: payload,
      authHeader: authHeader,
      attempt: (body) async {
        // The first send, and every send that is not a conflict recovery, goes
        // as it is. [ConflictRecovery] only ever calls back a second time with
        // a revision it read off the server, and it returns the original 409
        // rather than calling back when that revision equals the one we sent —
        // so a `_rev` that differs from ours is exactly "this is the recovery
        // re-send", with no flag to thread through.
        if (documentUrl == null || body['_rev'] == sentRev) {
          return _api.postJsonObject(
            row.endpoint,
            body,
            authHeader: authHeader,
          );
        }
        final merged = await _reconcile(body, documentUrl, authHeader);
        if (merged == null) {
          // **Fail closed.** The alternative — sending `body` — puts the stale
          // array on the server under a revision [ConflictRecovery] has just
          // read, which is precisely the loss this arm exists to prevent, on a
          // path with no error and no log. A null `httpCode` classifies
          // `transient` (`OutboxRepository.classifyStatus`), so the row keeps
          // its place in the queue and the reply waits on the handset rather
          // than the admin's reply being destroyed for everyone.
          return const NetworkError<Map<String, dynamic>>(
            null,
            'Could not read the conflicting feedback document',
          );
        }
        sent = merged;
        return _api.postJsonObject(
          row.endpoint,
          merged,
          authHeader: authHeader,
        );
      },
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final rev = data['rev'];
      if (rev is! String) {
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no rev',
        );
      }
      // Recording the revision is what lets a later reply update the same
      // document instead of conflicting against a stale `_rev`.
      //
      // **And the thread, when the reconcile changed it — without which the
      // reconcile is half a fix.** `markUploaded` writes two columns, which
      // was right while the handler always sent the row's own array. The
      // reconcile breaks that invariant: it can leave the server holding a
      // reply the row does not have, and the screen reads the row. ada, still
      // seeing no answer, replies again; that reply is built on the local
      // array, goes out under a revision that now *matches*, and CouchDB takes
      // it with no 409 and nothing to reconcile against — the admin's answer
      // destroyed on a clean 201. The second reply is not a contrived step; it
      // is made *likely* by a fix that leaves the handset unaware of what it
      // has just preserved.
      //
      // Merged rather than written over, through the same
      // [FeedbackMapper.mergeThreads] the send used: the drain is
      // asynchronous, so a reply written between the send and here would be
      // lost by an overwrite. Merging keeps it and adds whatever the send
      // delivered that the row lacks. One transaction, because a row reading
      // `isUploaded = true` with a thread the server does not have is the
      // state this whole block exists to prevent.
      await _dao.transaction(() async {
        await _dao.markUploaded(row.itemId, rev);
        final delivered = sent?['messages'];
        if (delivered is! List) return;
        final current = await _dao.getById(row.itemId);
        if (current == null) return;
        await _dao.updateRow(
          FeedbackEntriesCompanion(
            id: Value(row.itemId),
            messages: Value(
              jsonEncode(
                FeedbackMapper.mergeThreads(
                  FeedbackMapper.decodeMessages(current.messages),
                  delivered,
                ),
              ),
            ),
          ),
        );
      });
    }
    return result;
  };

  /// Puts a conflicted send back together with the thread it is about to
  /// overwrite.
  ///
  /// **This is where the port stops losing an admin's reply, and the re-queue
  /// after a pull is not what closes it.** [ConflictRecovery]'s update arm
  /// re-sends the *stored* payload under a revision it has just read
  /// (`outbox_drainer.dart`, `return attempt({...payload, '_rev': rev})`),
  /// which is last-write-wins — correct for every other uploader, because a
  /// pull re-arms the memo with the server's revision and the local content
  /// goes over one sync later anyway. `feedback` is the documented exception:
  /// `messages` is an append array that **Planet's web UI also writes**, so
  /// the payload cannot reconstruct what the server holds and "one sync later"
  /// is a permanent loss.
  ///
  /// `FeedbackMapper._mergePendingReplies` closes that at pull time and
  /// `FeedbackSyncNotifier` (and now the headless `'feedback'` step) re-queues
  /// so the arm re-sends the merged array. But every drain trigger in the port
  /// runs **before** any feedback pull — `OutboxDrainScope` on startup and on
  /// resume pulls nothing at all, `DashboardSyncNotifier._runPass` drains
  /// three times before its area loop reaches feedback, and
  /// `BackgroundTaskRunner` drains at `:100` and syncs at `:142`. So on the
  /// ordinary path the conflicted send happens while this device has never
  /// seen the admin's reply, and no re-queue can help: the row is as unaware
  /// of it as the snapshot is.
  ///
  /// Kotlin does not have this loss, and it is worth knowing why before
  /// anyone "restores parity": `UploadCoordinator.kt:169-186`'s 409 arm
  /// fetches the document and reports **Success without sending anything**,
  /// then `markFeedbackUploaded` flags the row — so Kotlin keeps the admin's
  /// reply on the server and silently destroys *the handset's own* reply at
  /// the next pull instead. Adopting is not the better trade, it is the other
  /// one. Merging is the only arm that keeps both.
  ///
  /// **Of `messages` only.** Every other field still goes as the stale payload
  /// had it, `status` included — so an admin who closes a thread through the
  /// web UI has it re-opened by the drain of a reply queued before the close,
  /// where Kotlin's adopt-without-sending would not. Last-write-wins is the
  /// right default for a scalar a single writer owns; `messages` is the one
  /// field here that two writers append to, which is why it is the one field
  /// reconciled.
  ///
  /// The rule is **"append what the server does not already have"**, not the
  /// shared-prefix rule the mapper uses, and the difference is load-bearing.
  /// A send that reached CouchDB but whose response was lost leaves the row
  /// pending with the same stored payload; the server then already holds our
  /// reply, *after* the admin's, so a prefix scan diverges at index 1 and
  /// would append our reply a second time. Matching anywhere in the server's
  /// array makes a re-send a no-op. Replies are identified by
  /// [FeedbackMapper.sameMessage] — message, user and a millisecond `time` —
  /// so two replies collide only if a user sent the same text twice inside one
  /// millisecond — or if both elements carry none of those three fields, which
  /// [FeedbackMapper.sameMessage] documents as comparing equal. Treating
  /// either as already-delivered drops it; treating an echo as new duplicates
  /// it, permanently and for everyone, so the comparison is deliberately the
  /// looser one.
  ///
  /// A failed or unusable read returns the body untouched. That is the
  /// pre-existing behaviour, and the alternative — refusing to send — would
  /// strand a reply on a handset over a transient read.
  Future<Map<String, dynamic>?> _reconcile(
    Map<String, dynamic> body,
    String documentUrl,
    String? authHeader,
  ) async {
    final NetworkResult<Map<String, dynamic>> existing;
    try {
      existing = await _api.getJsonObject(documentUrl, authHeader: authHeader);
    } catch (_) {
      // `PlanetApi` returns a `NetworkException` rather than throwing, so this
      // guards a fake or a future transport, as the `try` in
      // `ConflictRecovery.send` does.
      return null;
    }
    if (existing is! NetworkSuccess<Map<String, dynamic>>) return null;

    final serverMessages = existing.data['messages'];
    if (serverMessages is! List) return body;

    final ours = body['messages'];
    if (ours is! List) return {...body, 'messages': serverMessages};

    return {
      ...body,
      'messages': FeedbackMapper.mergeThreads(ours, serverMessages),
    };
  }
}
