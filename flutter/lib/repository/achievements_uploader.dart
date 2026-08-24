import '../core/config/server_config.dart';
import '../core/files/achievement_files.dart';
import '../core/network/network_result.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'achievements_repository.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Durable write-back for `model/Achievement.kt` — port of
/// `services/upload/AchievementUploader.kt` and
/// `UploadManager.uploadAchievement`.
///
/// The Kotlin loops `getAchievementForUpload` and PUTs the whole ledger to
/// `achievements/<couchId>`, then PUTs the resume the row names as a CouchDB
/// attachment, the same two-step shape as the photo outbox uploader. The port
/// routes both through the outbox: the ledger is enqueued on any edit, and
/// the drainer delivers it on app resume so an offline edit survives process
/// death the way every other one does.
class AchievementsUploader {
  AchievementsUploader(
    this._api,
    this._repository,
    this._dao,
    this._outbox, {
    this.readResumeBytes = AchievementFiles.readResumeBytes,
  });

  static const type = 'achievements';

  final PlanetApi _api;
  final AchievementsRepository _repository;
  final AchievementDao _dao;
  final OutboxRepository _outbox;

  /// Overridable for the test suite (see `AchievementFiles`).
  final Future<List<int>?> Function(String resumeFileName) readResumeBytes;

  /// The base CouchDB URL. The ledger PUT runs against the shared
  /// `achievements` table, like the other write-back uploaders.
  static String endpointFor(ServerConfig config) =>
      UrlUtils.credentialFreeDbUrl(config);

  /// Enqueues every achievement ledger not yet synced, the way
  /// [SearchActivityUploader.queuePending] does for searches.
  Future<int> queuePending({required ServerConfig config}) async {
    final pending = await _repository.pendingUploads();
    for (final row in pending) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: AchievementsRepository.serialize(row),
      );
    }
    return pending.length;
  }

  OutboxHandler get handler => (row, payload, authHeader) async {
    final couchId = payload['_id'];
    if (couchId is! String || couchId.isEmpty) {
      return const NetworkError(null, 'Achievement ledger carried no _id');
    }
    final result = await _api.putJsonObject(
      '${row.endpoint}/achievements/$couchId',
      payload,
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final rev = data['rev'];
      if (rev is! String) {
        return const NetworkError(null, 'Upload response carried no rev');
      }
      await _dao.markUploaded(row.itemId, couchId, rev);
      // The document lands without the resume attachment when the file is not
      // on the device — the same "document first, attachment second" contract
      // `AchievementUploader.uploadResumeAttachment` assumes.
      await _uploadResumeAttachment(
        row.itemId,
        payload,
        couchId,
        rev,
        row.endpoint,
        authHeader,
      );
    }
    return result;
  };

  /// Port of `AchievementUploader.uploadCvAttachment`: when the ledger names
  /// a resume whose bytes are on this device, PUT it and carry the document's
  /// `rev` forward. Best-effort — the document already landed before any
  /// bytes move, and the Kotlin swallows attachment failures the same way.
  Future<void> _uploadResumeAttachment(
    String rowId,
    Map<String, dynamic> payload,
    String couchId,
    String rev,
    String baseUrl,
    String? authHeader,
  ) async {
    final resumeFileName = payload['resumeFileName'];
    if (resumeFileName is! String || resumeFileName.isEmpty) return;
    final bytes = await readResumeBytes(resumeFileName);
    if (bytes == null || bytes.isEmpty) return;
    // CouchDB attachment key is always `resume.pdf` — the picker only admits
    // PDFs, and the attachment name survives renames of the local file.
    final result = await _api.uploadAttachment(
      '$baseUrl/achievements/$couchId/resume.pdf',
      bytes: bytes,
      ifMatch: rev,
      contentType: 'application/pdf',
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final newRev = data['rev'];
      if (newRev is String) {
        await _dao.markUploaded(rowId, couchId, newRev);
      }
    }
  }
}
