import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import 'events_repository.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Durable meetup upload, replacing the meetups branch of `UploadManager`.
class EventsUploader {
  EventsUploader(this._api, this._events, this._outbox);

  static const type = 'meetups';
  final PlanetApi _api;
  final EventsRepository _events;
  final OutboxRepository _outbox;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/meetups';

  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _events.pendingUploads();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: EventsRepository.serialize(row),
        userId: userId,
      );
    }
    return rows.length;
  }

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
        // Reporting success would drop the outbox row while the meetup stays
        // `isUploaded == false`, so the next `queuePending` would POST it
        // again — one duplicate meetup document per drain.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      await _events.markUploaded(row.itemId, couchId, rev);
    }
    return result;
  };
}
