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
  String? _authHeader;

  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.dbUrl(config)}/meetups';

  set authHeader(String? value) => _authHeader = value;

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

  OutboxHandler get handler => (row, payload) async {
    final result = await _api.postJsonObject(
      row.endpoint,
      payload,
      authHeader: _authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      if (data case {'id': final String id, 'rev': final String rev}) {
        await _events.markUploaded(row.itemId, id, rev);
      }
    }
    return result;
  };
}
