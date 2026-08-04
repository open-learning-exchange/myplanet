import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
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
  VoicesUploader(this._api, this._voices, this._outbox);

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'voices';

  final PlanetApi _api;
  final VoicesRepository _voices;
  final OutboxRepository _outbox;

  /// Credential-free: this string is persisted in `outbox.endpoint`, and that
  /// table survives schema upgrades. The PIN travels as the `Authorization`
  /// header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/news';

  /// Queues every post that has not reached the server.
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so a post already queued has its payload refreshed
  /// rather than being posted twice.
  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final endpoint = endpointFor(config);
    final pending = await _voices.pendingUploads();
    for (final row in pending) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpoint,
        payload: VoicesRepository.serialize(row),
        userId: userId,
      );
    }
    return pending.length;
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
      );
    }
    return result;
  };
}
