import '../core/config/server_config.dart';
import '../core/files/team_attachments.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'dart:developer';

/// Completes the write-back for documents in the `teams` database.
///
/// The enqueue side lives in `providers/teams_provider.dart`, and without a
/// handler the drainer's generic fallback would still POST the payload — but
/// nothing would record the outcome. That matters because a locally-written
/// team document is marked [Teams.isUpdated], which makes it authoritative
/// over the server copy and exempt from stale-row cleanup. Left set forever,
/// the row would freeze at its local version and never be evicted once the
/// document is gone server-side.
///
/// Unlike meetups and voices, the id is not reassigned on upload:
/// `serializeTeamDocument` sends the device-generated `_id`, so CouchDB stores
/// the document under it and the next sync recognises the row as its own.
///
/// Finance documents (transactions and reports) may carry a binary
/// attachment — a receipt image. The bytes live under
/// `team_attachments/<docId>/<imageName>` (see [TeamAttachments]) and are PUT
/// to `teams/<id>/<imageName>` after the document is acknowledged, mirroring
/// `UploadManager.uploadTeamImageAttachment` and [PersonalsUploader]'s
/// two-step pattern. The attachment step is best-effort: the document is
/// already uploaded before the bytes are attempted, and an attachment failure
/// does not roll the document back.
class TeamsUploader {
  TeamsUploader(this._api, this._dao, this._identity);

  /// The upload types enqueued by the team providers, all handled the same way.
  static const membershipType = 'teamMembership';
  static const resourceType = 'teamResource';
  static const coursesType = 'teamCourses';
  static const reportsType = 'teamReports';
  static const financesType = 'teamFinances';
  static const types = {
    membershipType,
    resourceType,
    coursesType,
    reportsType,
    financesType,
  };

  final PlanetApi _api;
  final TeamDao _dao;
  final DeviceIdentitySource _identity;

  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/teams';

  OutboxHandler get handler => (row, payload, authHeader) async {
    final document = {...payload, ...(await _identity.read()).documentFields};
    final result = await _api.postJsonObject(
      row.endpoint,
      document,
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      // A tombstone's subject was already deleted locally; there is no row
      // left to stamp, and treating the missing revision as a failure would
      // retry a delete that already succeeded.
      if (document['_deleted'] == true) return result;
      final rev = data['rev'];
      if (rev is! String) {
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no rev',
        );
      }
      await _dao.markUploaded(row.itemId, rev);
      await _uploadAttachment(row, rev, authHeader);
    }
    return result;
  };

  /// PUTs the receipt image named on the uploaded document, if any.
  ///
  /// The document id is not reassigned (see the class docs), so the row's
  /// own `id` is the CouchDB id the attachment is stored under. The file lives
  /// under `team_attachments/<id>/<imageName>`, keyed on the document id the
  /// same way [TeamAttachments.fileFor] is — a team accrues many finance
  /// documents, so keying on the team id would collide identically-named
  /// receipts. A missing file or a missing name is a no-op, not an error.
  Future<void> _uploadAttachment(
    OutboxRow row,
    String rev,
    String? authHeader,
  ) async {
    final team = await _dao.getById(row.itemId);
    final imageName = team?.imageName;
    if (imageName == null || imageName.isEmpty) return;
    final docId = team?.id ?? row.itemId;
    final file = await TeamAttachments.existingFileFor(
      docId: docId,
      filename: imageName,
    );
    if (file == null) return;

    late final List<int> bytes;
    try {
      bytes = await file.readAsBytes();
    } on Exception catch (e, stack) {
      log('Could not read team attachment', error: e, stackTrace: stack);
      return;
    }

    final encodedName = Uri.encodeComponent(imageName);
    final attachmentUrl =
        '${row.endpoint}/${Uri.encodeComponent(docId)}/$encodedName';
    final contentType = _contentTypeFor(imageName);

    final attachResult = await _api.uploadAttachment(
      attachmentUrl,
      bytes: bytes,
      authHeader: authHeader,
      contentType: contentType,
      ifMatch: rev,
    );
    if (attachResult is! NetworkSuccess<Map<String, dynamic>>) {
      log('Team attachment upload failed: $attachResult');
    }
  }

  /// Best-effort MIME guess, the way `FileUtils.getMimeType` falls back to
  /// `image/*` and `UploadManager` falls back to the same.
  String _contentTypeFor(String imageName) {
    final lower = imageName.toLowerCase();
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) {
      return 'image/jpeg';
    }
    if (lower.endsWith('.gif')) return 'image/gif';
    if (lower.endsWith('.webp')) return 'image/webp';
    return 'image/*';
  }
}
