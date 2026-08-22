import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/feedback_mapper.dart';
import 'feedback_repository.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Durable write-back for the `feedback` database.
///
/// Kotlin uploads feedback from `AutoSyncWorker` through
/// `UploadManager.uploadFeedback`. The outbox carries it here: queued on write,
/// then drained on app resume or by the constraint-aware background job.
/// Without this the port filed feedback that never left the device —
/// `getPendingFeedback` existed but nothing called it.
class FeedbackUploader {
  FeedbackUploader(this._api, this._repository, this._dao, this._outbox);

  static const type = 'feedback';

  final PlanetApi _api;
  final FeedbackRepository _repository;
  final FeedbackDao _dao;
  final OutboxRepository _outbox;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/feedback';

  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _repository.getPendingFeedback();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: FeedbackMapper.toDoc(row),
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
      // Recording the revision is what lets a later reply update the same
      // document instead of conflicting against a stale `_rev`.
      await _dao.markUploaded(row.itemId, rev);
    }
    return result;
  };
}
