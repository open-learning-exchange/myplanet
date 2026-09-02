import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'health_repository.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Durable write-back for the `health` database.
///
/// Kotlin uploads examinations from `UploadManager.uploadExamResult` via
/// `AutoSyncWorker`. The outbox carries it here: queued on write, then drained
/// on app resume or by the constraint-aware background job.
///
/// Without this the port recorded examinations that never left the device —
/// `getUpdated()`, `markUploaded()` and `serialize()` all existed and none of
/// them had a caller. A clinic's readings would have lived on one handset.
class HealthUploader {
  HealthUploader(this._api, this._repository, this._dao, this._outbox);

  static const type = 'health';

  final PlanetApi _api;
  final HealthRepository _repository;
  final HealthExaminationDao _dao;
  final OutboxRepository _outbox;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/health';

  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _repository.getUpdated();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        // `data` is already ciphertext by the time it is stored, so the
        // payload leaves the device encrypted. Serializing the row rather than
        // the form state is what keeps that true.
        payload: HealthRepository.serialize(row),
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
      final rev = data['rev'];
      if (rev is! String) {
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no rev',
        );
      }
      // Recording the revision is what lets the next examination update the
      // same document instead of conflicting against a stale `_rev`; clearing
      // `isUpdated` is what stops it being queued a second time.
      await _dao.markUploaded(row.itemId, rev);
      // And the record is no longer stranded, so the refusals that said it was
      // must go with it. An abandoned row is never reused and nothing sweeps
      // them, so leaving them would have `MyHealthScreen` warn about a record
      // that is on the server — for the life of the install, with no action to
      // offer. This is the only event that makes an old refusal untrue.
      await _outbox.clearAbandoned(type, row.itemId);
    }
    return result;
  };
}
