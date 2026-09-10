import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import 'events_repository.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Durable meetup upload, replacing the meetups branch of `UploadManager`.
class EventsUploader {
  EventsUploader(this._api, this._events, this._outbox, this._identity);

  static const type = 'meetups';
  final PlanetApi _api;
  final EventsRepository _events;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/meetups';

  /// Queues every meetup that has not reached the server.
  ///
  /// `Meetup.serialize` gained `addDocumentOrigin()` in `27c0470`, a pure
  /// addition — `androidId` and `app` are both new on the wire, with no
  /// device name ([DeviceIdentity.originFields], not `documentFields`). The
  /// identity read is guarded on an empty list so a pass with nothing to send
  /// makes no platform-channel call.
  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _events.pendingUploads();
    final identity = rows.isEmpty ? null : await _identity.read();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: {
          ...EventsRepository.serialize(row),
          ...identity!.originFields,
        },
        userId: userId,
      );
    }
    return rows.length;
  }

  OutboxHandler get handler => (row, payload, authHeader) async {
    // The 409 arm. An edited meetup is an update once `meetupId` is set; adopting would
    // report the edit delivered.
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
