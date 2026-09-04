import 'dart:developer';
import 'dart:io';

import 'package:mime/mime.dart';
import 'package:path/path.dart' as p;

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'personals_repository.dart';

/// Port of `PersonalsRepositoryImpl.uploadPersonalDocument`.
///
/// This is the first *append* write-back in the port, and the reason the
/// outbox had to exist. The shelf could stay queue-free because its payload is
/// derived state — recomputing it from the database always yields current
/// truth, so a dropped push costs nothing. A personal note is the opposite: it
/// exists only locally until it is POSTed, and a push lost to a dead network is
/// lost outright unless something durable remembers to send it again.
///
/// When a note carries a local [PersonalRow.path] the Kotlin flow POSTs the
/// document and then PUTs the file as a CouchDB attachment. That second step
/// is best-effort in the Kotlin source: the document is already uploaded before
/// the attachment is attempted, and an attachment failure does not roll the
/// document back. This handler mirrors that ordering.
class PersonalsUploader {
  PersonalsUploader(this._api, this._personals, this._outbox, this._identity);

  /// The `uploadType` these operations carry in the outbox.
  static const String type = 'personals';

  final PlanetApi _api;
  final PersonalsRepository _personals;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`.
  /// The PIN travels as the `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/resources';

  /// Queues every not-yet-uploaded note for [userId].
  ///
  /// Safe to call repeatedly: [OutboxRepository.enqueue] keys on
  /// `(uploadType, itemId)`, so a note already queued has its payload
  /// refreshed rather than being posted twice.
  Future<int> queuePending({
    required ServerConfig config,
    required String userId,
  }) async {
    final endpoint = endpointFor(config);
    final pending = await _personals.pendingUploads(userId);
    final identity = pending.isEmpty ? null : await _identity.read();
    for (final row in pending) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpoint,
        payload: {
          ...PersonalsRepository.serialize(row),
          ...identity!.documentFields,
        },
        userId: userId,
      );
    }
    return pending.length;
  }

  /// The [OutboxHandler] for [type].
  ///
  /// A plain replay would POST correctly but drop the ids CouchDB assigns, so
  /// the note would stay `isUploaded == false` and be posted again on the next
  /// drain — one duplicate per drain, forever. Adopting `id`/`rev` on success
  /// is what closes the loop.
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
        // Reporting success here would delete the outbox row while the note
        // stays `isUploaded == false`, so the next `queuePending` would POST
        // it again — a fresh duplicate document on every drain.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id/rev',
        );
      }
      await _personals.markUploaded(row.itemId, couchId, rev);
      await _uploadAttachment(row, couchId, rev, authHeader);
    }
    return result;
  };

  Future<void> _uploadAttachment(
    OutboxRow row,
    String couchId,
    String rev,
    String? authHeader,
  ) async {
    final localPath = (await _personals.getById(row.itemId))?.path;
    if (localPath == null || localPath.isEmpty) return;

    final file = File(localPath);
    if (!await file.exists()) return;

    late final List<int> bytes;
    try {
      bytes = await file.readAsBytes();
    } on FileSystemException catch (e, stack) {
      log('Could not read personal attachment', error: e, stackTrace: stack);
      return;
    }

    final filename = p.basename(localPath);
    if (filename.isEmpty) return;

    final base = row.endpoint.endsWith('/resources')
        ? row.endpoint.substring(0, row.endpoint.length - 10)
        : row.endpoint;
    final attachmentUrl =
        '$base/resources'
        '/${Uri.encodeComponent(couchId)}/${Uri.encodeComponent(filename)}';
    final contentType = lookupMimeType(localPath) ?? 'application/octet-stream';

    final attachResult = await _api.uploadAttachment(
      attachmentUrl,
      bytes: bytes,
      authHeader: authHeader,
      contentType: contentType,
      ifMatch: rev,
    );
    if (attachResult is! NetworkSuccess<Map<String, dynamic>>) {
      log('Personal attachment upload failed: $attachResult');
    }
  }

  static String authHeaderFor(ServerConfig config) =>
      UrlUtils.authHeader(config);
}
