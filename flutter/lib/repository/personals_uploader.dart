import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'personals_repository.dart';

/// Port of `PersonalsRepositoryImpl.uploadPersonalDocument`.
///
/// This is the first *append* write-back in the port, and the reason the
/// outbox had to exist. The shelf could stay queue-free because its payload is
/// derived state — recomputing it from the database always yields current
/// truth, so a dropped push costs nothing. A personal note is the opposite: it
/// exists only locally until it is POSTed, and a push lost to a dead network is
/// lost outright unless something durable remembers to send it again.
class PersonalsUploader {
  PersonalsUploader(this._api, this._personals, this._outbox);

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'personals';

  final PlanetApi _api;
  final PersonalsRepository _personals;
  final OutboxRepository _outbox;

  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.dbUrl(config)}/resources';

  /// Queues every not-yet-uploaded note for [userId].
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so a note already queued has its payload
  /// refreshed rather than being posted twice.
  Future<int> queuePending({
    required ServerConfig config,
    required String userId,
  }) async {
    final endpoint = endpointFor(config);
    final pending = await _personals.pendingUploads(userId);
    for (final row in pending) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpoint,
        payload: PersonalsRepository.serialize(row),
        userId: userId,
      );
    }
    return pending.length;
  }

  /// The [OutboxHandler] for [type].
  ///
  /// A plain replay would POST correctly but drop the ids CouchDB assigns, so
  /// the note would stay `isUploaded == false` and be posted again on the next
  /// drain — one duplicate per drain, forever. Adopting `id`/`rev` on success
  /// is what closes the loop.
  OutboxHandler get handler => (row, payload) async {
    final result = await _api.postJsonObject(
      row.endpoint,
      payload,
      authHeader: _authHeader,
    );

    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final couchId = data['id'];
      final rev = data['rev'];
      if (couchId is String && rev is String) {
        await _personals.markUploaded(row.itemId, couchId, rev);
      }
    }
    return result;
  };

  String? _authHeader;

  /// The credentials the next drain should use.
  ///
  /// Held separately from [endpointFor] because the endpoint is frozen into the
  /// operation at enqueue time, whereas the PIN must be whatever is configured
  /// when the send actually happens — which may be a later session.
  set authHeader(String? value) => _authHeader = value;

  static String authHeaderFor(ServerConfig config) =>
      UrlUtils.basicAuthHeader('satellite', config.pin);
}
