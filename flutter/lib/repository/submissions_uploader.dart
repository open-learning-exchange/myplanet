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

  Future<int> queuePending({
    required ServerConfig config,
    required String userId,
  }) async {
    final rows = await _submissions.pendingUploads(userId);
    final identity = rows.isEmpty ? null : await _identity.read();
    for (final row in rows) {
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
