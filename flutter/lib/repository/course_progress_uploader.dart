import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Durable write-back for the `courses_progress` database.
///
/// Kotlin uploads course progress from `AutoSyncWorker` through
/// `UploadManager.uploadCourseProgress`, which drives `UploadConfigs.CourseProgress`
/// — a `RoomUploadConfig` that fetches `courseProgressDao.getPendingUploads()`
/// and serializes each with `CourseProgress.serializeProgress`. There is no
/// background scheduling here yet, so the outbox carries it instead: queued on
/// write, drained on app resume.
class CourseProgressUploader {
  CourseProgressUploader(this._api, this._dao, this._outbox);

  static const type = 'course_progress';

  final PlanetApi _api;
  final CourseProgressDao _dao;
  final OutboxRepository _outbox;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/courses_progress';

  /// Queues all pending progress rows for upload.
  Future<int> queuePending({required ServerConfig config}) async {
    final rows = await _dao.getPendingUploads();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: _toDoc(row),
        userId: row.userId,
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
      final remoteId = data['id']?.toString();
      final rev = data['rev']?.toString();
      if (remoteId == null || remoteId.isEmpty || rev is! String) {
        // Reporting success here would retire the outbox entry while the row
        // stayed pending, so the next `queuePending` would post the same
        // progress again as a second document.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id or rev',
        );
      }
      await _dao.markUploaded(row.itemId, remoteId, rev);
    }
    return result;
  };

  /// Port of `CourseProgress.serializeProgress`.
  Map<String, dynamic> _toDoc(CourseProgressRow row) {
    final doc = <String, dynamic>{
      'userId': row.userId,
      'parentCode': row.parentCode,
      'courseId': row.courseId,
      'passed': row.passed,
      'stepNum': row.stepNum,
      'createdOn': row.createdOn,
      'createdDate': row.createdDate,
      'updatedDate': row.updatedDate,
    };
    if (row.couchId != null) {
      doc['_id'] = row.couchId;
    }
    if (row.rev != null) {
      doc['_rev'] = row.rev;
    }
    return doc;
  }
}
