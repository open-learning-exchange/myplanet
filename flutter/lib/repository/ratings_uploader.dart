import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'ratings_repository.dart';

/// Durable write-back for the `ratings` database.
///
/// Kotlin uploads ratings from `AutoSyncWorker` through `UploadManager.uploadRating`.
/// There is no background scheduling here yet, so the outbox carries it instead:
/// queued on write, drained on app resume.
///
/// Without this the port saved ratings locally but they never left the device —
/// `RatingsRepository.pendingUploads()` existed but nothing called it.
class RatingsUploader {
  RatingsUploader(
    this._api,
    this._repository,
    this._dao,
    this._outbox,
  );

  static const type = 'rating';

  final PlanetApi _api;
  final RatingsRepository _repository;
  final RatingDao _dao;
  final OutboxRepository _outbox;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/ratings';

  /// Queues all pending ratings for upload.
  Future<int> queuePending({
    required ServerConfig config,
  }) async {
    final rows = await _repository.pendingUploads();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: _toDoc(row),
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
      final id = data['id'];
      final rev = data['rev'];
      if (id is String && rev is String) {
        await _dao.markUploaded(row.itemId);
      }
    }
    return result;
  };

  /// Converts a rating row to a CouchDB document.
  ///
  /// Port of `model/Rating.serializeRating`.
  Map<String, dynamic> _toDoc(RatingRow row) {
    final doc = <String, dynamic>{
      'item': row.item,
      'type': row.type,
      'title': row.title,
      'time': row.time,
      'rate': row.rate,
      'createdOn': row.parentCode,
      'parentCode': row.parentCode,
      'planetCode': row.planetCode,
      // Device info would go here in the Kotlin, but Flutter doesn't have
      // access to the same device identification utilities. The server accepts
      // ratings without these fields.
    };
    if (row.couchId != null) {
      doc['_id'] = row.couchId;
    }
    if (row.rev != null) {
      doc['_rev'] = row.rev;
    }
    if (row.comment != null) {
      doc['comment'] = row.comment;
    }
    return doc;
  }
}
