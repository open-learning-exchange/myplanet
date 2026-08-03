import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'submissions_repository.dart';

/// Durable append-style port of the submissions `UploadConfig`.
class SubmissionsUploader {
  SubmissionsUploader(this._api, this._submissions, this._outbox);

  static const type = 'submissions';
  final PlanetApi _api;
  final SubmissionsRepository _submissions;
  final OutboxRepository _outbox;

  /// Credential-free — see [PersonalsUploader.endpointFor].
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/submissions';

  Future<int> queuePending({
    required ServerConfig config,
    required String userId,
  }) async {
    final rows = await _submissions.pendingUploads(userId);
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: await _submissions.serialize(row),
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
      if (data case {'id': final String id, 'rev': final String rev}) {
        await _submissions.markUploaded(row.itemId, id, rev);
      }
    }
    return result;
  };
}
