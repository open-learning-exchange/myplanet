import '../core/config/server_config.dart';
import '../core/files/submit_photos_files.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'dart:developer';

import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'submissions_repository.dart';

/// Durable two-step write-back for `submit_photos`, porting
/// `services/upload/PhotoUploader.kt`.
///
/// The Kotlin source loops `getUnuploadedPhotos()` and POSTs each document to
/// the `submissions` database, then PUTs the captured JPEG as an attachment
/// to `submissions/<id>/<name>` once CouchDB acknowledges it. This port routes
/// the same two steps through the [OutboxDrainer] so a capture survives
/// process death the way every other write-back does: the row is enqueued on
/// the capture, and the drainer delivers it on app resume or the headless job.
///
/// The attachment step is best-effort, mirroring `TeamsUploader`'s receipt
/// upload: the document is already uploaded before the bytes are attempted, and
/// an attachment failure does not roll the document back. A photo whose bytes
/// are missing on a replay (the file was cleared by storage management) still
/// succeeds as a document; the bytes are the part that can be re-sent.
class SubmitPhotosUploader {
  SubmitPhotosUploader(
    this._api,
    this._submissions,
    this._outbox,
    this._identity,
  );

  static const type = 'submit_photos';

  final PlanetApi _api;
  final SubmissionsRepository _submissions;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free — see [SubmissionsUploader.endpointFor]. Photo documents
  /// are not CouchDB `_security` reads; the server-side `submissions` write
  /// path is the same append the submissions uploader uses.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/submissions';

  /// Enqueues every captured photo that has not reached the server.
  ///
  /// Device identity is layered onto the document here, the way
  /// [SubmissionsUploader.queuePending] layers it onto a submission: the row
  /// stores no device id, so a capture made under one device name and drained
  /// under another reports the latter — which is the current identity, not a
  /// stale one baked in at capture time.
  Future<int> queuePending({required ServerConfig config}) async {
    final pending = await _submissions.unuploadedPhotos();
    if (pending.isEmpty) return 0;
    final identity = await _identity.read();
    for (final entry in pending) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: entry.id,
        endpoint: endpointFor(config),
        payload: {...entry.document, ...identity.documentFields},
      );
    }
    return pending.length;
  }

  OutboxHandler get handler => (row, payload, authHeader) async {
    final document = {...payload, ...(await _identity.read()).documentFields};
    final result = await _api.postJsonObject(
      row.endpoint,
      document,
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final couchId = data['id'];
      final rev = data['rev'];
      if (couchId is! String || rev is! String) {
        // Reporting success here would drop the outbox row while the photo
        // stays `uploaded == false`, so the next `queuePending` would re-POST a
        // duplicate document — the same hazard [SubmissionsUploader] guards.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      await _submissions.markPhotoUploaded(row.itemId, couchId, rev);
      await _uploadAttachment(row, couchId, rev, authHeader);
    }
    return result;
  };

  /// PUTs the captured JPEG to `submissions/<id>/<name>` after the document
  /// POST lands — the port of `PhotoUploader`'s `uploadAttachment` step.
  ///
  /// The photo row carries only `photoLocation`; the attachment name is derived
  /// from it so a capture saved as `<timestamp>.jpg` is filed under that name.
  /// A missing file (cleared by storage management, or the capture wrote
  /// nothing) is a no-op, not an error: the document is already uploaded, and
  /// the bytes are the only part that can be re-sent.
  Future<void> _uploadAttachment(
    OutboxRow row,
    String couchId,
    String rev,
    String? authHeader,
  ) async {
    final photo = await _submissions.photoById(row.itemId);
    final photoLocation = photo?.photoLocation;
    if (photoLocation == null || photoLocation.isEmpty) return;
    final name = SubmitPhotosFiles.attachmentNameFor(photoLocation, row.itemId);
    final file = await SubmitPhotosFiles.existingFileFor(
      photoId: row.itemId,
      filename: name,
    );
    if (file == null) return;

    late final List<int> bytes;
    try {
      bytes = await file.readAsBytes();
    } on Exception catch (e, stack) {
      log('Could not read submit photo', error: e, stackTrace: stack);
      return;
    }

    final encodedName = Uri.encodeComponent(name);
    final attachmentUrl =
        '${row.endpoint}/${Uri.encodeComponent(couchId)}/$encodedName';
    final attachResult = await _api.uploadAttachment(
      attachmentUrl,
      bytes: bytes,
      authHeader: authHeader,
      contentType: 'image/jpeg',
      ifMatch: rev,
    );
    if (attachResult is! NetworkSuccess<Map<String, dynamic>>) {
      log('Submit photo attachment upload failed: $attachResult');
    }
  }
}
