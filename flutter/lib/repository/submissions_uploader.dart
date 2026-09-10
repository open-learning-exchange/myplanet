import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'submissions_repository.dart';

/// Durable append-style port of the submissions `UploadConfig`.
class SubmissionsUploader {
  SubmissionsUploader(
    this._api,
    this._submissions,
    this._outbox,
    this._identity,
  );

  static const type = 'submissions';
  final PlanetApi _api;
  final SubmissionsRepository _submissions;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free — see [PersonalsUploader.endpointFor].
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/submissions';

  /// Queues every submission on the handset that still owes an upload.
  ///
  /// [userId] is the signed-in session, recorded on the outbox row — it is
  /// **not** a filter. Kotlin's two submission upload configs are both
  /// handset-wide (see [SubmissionDao.pendingUploads]), and the rows go up on
  /// the session's credentials the way `UploadCoordinator` sends them; each
  /// row's own owner rides in the document's `user` object, which is where
  /// Planet and the sync-in both read it from.
  ///
  /// It is nullable for the same reason it is not a filter. Kotlin's sweep
  /// takes no user at all — `uploadManager.uploadSubmissions()` is a bare call
  /// from `AutoSyncWorker:136`, `UserDataWorker:48` and
  /// `ServerReachabilityWorker:196` — so the sync-path safety net has to be
  /// able to run on a handset whose session has gone, which is precisely the
  /// handset with somebody else's finished sheet stranded on it. The column is
  /// nullable and nothing reads it back (`tables.dart:368`), so a null tag
  /// costs the row nothing.
  Future<int> queuePending({
    required ServerConfig config,
    required String? userId,
  }) async {
    final rows = await _submissions.pendingUploads();
    final identity = rows.isEmpty ? null : await _identity.read();
    var queued = 0;
    for (final row in rows) {
      // A send already on the wire is left alone. `OutboxRepository.enqueue`
      // would put it back to `pending` to preserve a mid-flight payload edit,
      // and `markCompleted` only deletes an `in_progress` row — so the send
      // that succeeds moments later deletes nothing, the row survives with the
      // same body, and the next drain posts a **second** CouchDB document.
      // That reset is right for derived state, whose handler rebuilds; a
      // submission is an append and replaying it duplicates it.
      //
      // The cost is the narrow window where the sheet is edited while its POST
      // is in flight: `markUploaded` clears `isUpdated` on success, so that
      // edit leaves the pending set. Kotlin loses it identically —
      // `SubmissionDao:44` clears `isUpdated` in the same statement — and a
      // later edit is a `_rev`-carrying PUT rather than a new document, so an
      // update is recoverable where a duplicate is not.
      if (await _outbox.isInFlight(type, row.id)) continue;
      queued++;
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: {
          ...await _submissions.serialize(row),
          ...identity!.documentFields,
        },
        userId: userId,
      );
    }
    return queued;
  }

  OutboxHandler get handler => (row, payload, authHeader) async {
    // The 409 arm. An answer sheet edited after its first upload is an update, and
    // adopting would clear `uploaded`/`isUpdated` with the learner's added
    // answers still on the handset.
    // See [ConflictRecovery] for why a create stands as a refusal instead.
    final result = await ConflictRecovery.send(
      api: _api,
      documentUrl: ConflictRecovery.documentUrlUnder(row.endpoint, payload),
      payload: payload,
      authHeader: authHeader,
      attempt: (body) =>
          _api.postJsonObject(row.endpoint, body, authHeader: authHeader),
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final couchId = data['id'];
      final rev = data['rev'];
      if (couchId is! String || rev is! String) {
        // Same reasoning as `PersonalsUploader.handler`: reporting success
        // drops the outbox row while the submission stays `isUploaded ==
        // false`, so the next `queuePending` posts a duplicate document.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      await _submissions.markUploaded(row.itemId, couchId, rev);
    }
    return result;
  };
}
