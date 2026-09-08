import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'voices_repository.dart';

/// Durable upload for voices posts and replies, replacing the news branch of
/// `UploadManager`.
///
/// A post is an append: it exists only on this device until it is delivered, so
/// a push lost to a dead network is lost outright unless the outbox remembers.
class VoicesUploader {
  VoicesUploader(this._api, this._voices, this._outbox, this._identity);

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'voices';

  final PlanetApi _api;
  final VoicesRepository _voices;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`, and that
  /// table survives schema upgrades. The PIN travels as the `Authorization`
  /// header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/news';

  /// Queues every post that has not reached the server.
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so a post already queued has its payload refreshed
  /// rather than being posted twice, and a post whose send is already on the
  /// wire is skipped outright — see the loop.
  ///
  /// Returns the number of posts queued by *this* call, so a caller can tell
  /// "nothing to send" from "everything was already in flight".
  ///
  /// `VoicesRepositoryImpl.serializeNews` gained `addDocumentOrigin()` in
  /// `27c0470`, a pure addition — `androidId` and `app` are both new on the
  /// wire, with no device name ([DeviceIdentity.originFields], not
  /// `documentFields`). It stamps the **outer** document, not the nested
  /// `news` sub-object: the Kotlin calls it on `` `object` ``, and Phase 74's
  /// reactions bug is what a writer choosing the wrong level costs. The
  /// identity read is guarded on an empty list so a pass with nothing to send
  /// makes no platform-channel call.
  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final endpoint = endpointFor(config);
    final pending = await _voices.pendingUploads();
    final identity = pending.isEmpty ? null : await _identity.read();
    var queued = 0;
    for (final row in pending) {
      // A send already on the wire is left alone. [OutboxRepository.enqueue]
      // would put it back to `pending` to preserve a mid-flight payload edit,
      // and `markCompleted` only deletes an `in_progress` row — so the send
      // that succeeds moments later deletes nothing, the row survives with the
      // same body, and the next drain posts a **second** `news` document. That
      // reset is right for derived state, whose handler rebuilds from the
      // database; a voice with no `_id` yet is an append and replaying it
      // duplicates the post.
      //
      // Reachable without the sweep and systematic with it: [queuePending] is
      // unscoped, so any second write — another post, an edit, a reaction —
      // re-enqueues every undelivered row, including one whose POST is in
      // flight. The cost is the narrow window where the post is edited while
      // its POST is on the wire: `markUploaded` clears `isEdited` on success,
      // so that edit leaves the pending set. Kotlin loses it identically
      // (`markNewsUploaded` writes the row back with no edited flag at all),
      // and a later edit is a `_rev`-carrying update rather than a new
      // document, so a lost edit is recoverable where a duplicate is not.
      if (await _outbox.isInFlight(type, row.id)) continue;
      queued++;
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpoint,
        payload: {
          ...VoicesRepository.serialize(row),
          ...identity!.originFields,
        },
        userId: userId,
      );
    }
    return queued;
  }

  /// The [OutboxHandler] for [type].
  ///
  /// Adopting `id`/`rev` on success is what stops the next [queuePending] from
  /// posting the same message again.
  OutboxHandler get handler => (row, payload, authHeader) async {
    final result = await _api.postJsonObject(
      row.endpoint,
      payload,
      authHeader: authHeader,
    );

    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final couchId = data['id'];
      final rev = data['rev'];
      if (couchId is! String || rev is! String) {
        // Reporting success would drop the outbox row while the post stays
        // undelivered, so the next `queuePending` would post a duplicate.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      final images = payload['images'];
      await _voices.markUploaded(
        row.itemId,
        couchId,
        rev,
        images: images is List ? images : const [],
        // The body that actually went out. A row mutated while this POST was
        // on the wire keeps its `isEdited` flag, because [queuePending]
        // declined to re-queue it and nothing else would.
        delivered: payload,
      );
    }
    return result;
  };
}
