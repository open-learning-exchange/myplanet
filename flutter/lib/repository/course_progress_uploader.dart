import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
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
  CourseProgressUploader(this._api, this._dao, this._outbox, this._identity);

  static const type = 'course_progress';

  final PlanetApi _api;
  final CourseProgressDao _dao;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/courses_progress';

  /// Queues all pending progress rows for upload.
  ///
  /// The identity read is guarded on an empty pending list so a pass with
  /// nothing to send makes no platform-channel call, matching
  /// [PersonalsUploader.queuePending].
  Future<int> queuePending({required ServerConfig config}) async {
    final rows = await _dao.getPendingUploads();
    final identity = rows.isEmpty ? null : await _identity.read();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: _toDoc(row, identity!),
        userId: row.userId,
      );
    }
    return rows.length;
  }

  OutboxHandler get handler => (row, payload, authHeader) async {
    // **Deliberately not armed**, and the reason is a reachability finding
    // rather than a preference. `CourseProgressDao.getPendingUploads` is
    // `couchId IS NULL`, so a row the server has acknowledged is never
    // re-offered — which makes `_toDoc`'s `_id`/`_rev` branch dead on this
    // path and a 409 unreachable. An arm here would be inert code claiming a
    // recovery that cannot happen. See `PHASE_152_NOTES.md`; the dead branch
    // is reported there too, because it means a progress row's later edits
    // never upload at all.
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
  ///
  /// `27c0470` added `addDocumentOrigin()` to it — a pure addition, so
  /// `androidId` *and* `app` are both new on the wire and no device name
  /// accompanies them ([DeviceIdentity.originFields], not `documentFields`).
  Map<String, dynamic> _toDoc(CourseProgressRow row, DeviceIdentity identity) {
    final doc = <String, dynamic>{
      'userId': row.userId,
      'parentCode': row.parentCode,
      'courseId': row.courseId,
      'passed': row.passed,
      'stepNum': row.stepNum,
      'createdOn': row.createdOn,
      'createdDate': row.createdDate,
      'updatedDate': row.updatedDate,
      ...identity.originFields,
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
