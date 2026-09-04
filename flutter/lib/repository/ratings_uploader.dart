import 'dart:convert';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'ratings_repository.dart';

/// Durable write-back for the `ratings` database.
///
/// Kotlin uploads ratings from `AutoSyncWorker` through `UploadManager.uploadRating`.
/// The outbox carries it instead: queued on write, then drained on app resume
/// or by the constraint-aware background job.
///
/// Without this the port saved ratings locally but they never left the device —
/// `RatingsRepository.pendingUploads()` existed but nothing called it.
class RatingsUploader {
  RatingsUploader(
    this._api,
    this._repository,
    this._dao,
    this._userDao,
    this._outbox,
    this._identity,
  );

  static const type = 'rating';

  final PlanetApi _api;
  final RatingsRepository _repository;
  final RatingDao _dao;
  final UserDao _userDao;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Credential-free: this string is persisted in `outbox.endpoint`, a table
  /// that deliberately survives schema upgrades. The PIN travels as the
  /// `Authorization` header at send time instead.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/ratings';

  /// Queues all pending ratings for upload.
  Future<int> queuePending({required ServerConfig config}) async {
    final rows = await _repository.pendingUploads();
    final identity = rows.isEmpty ? null : await _identity.read();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: {
          ..._toDoc(row, await _userDao.getById(row.userId)),
          ...identity!.documentFields,
        },
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
      if (data['rev'] is! String) {
        // Reporting success here would retire the outbox entry while the row
        // stayed pending, so the next `queuePending` would post the same
        // rating again as a second document.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no rev',
        );
      }
      await _dao.markUploaded(row.itemId);
    }
    return result;
  };

  /// Converts a rating row to a CouchDB document.
  ///
  /// Port of `model/Rating.serializeRating`.
  Map<String, dynamic> _toDoc(RatingRow row, UserRow? user) {
    final doc = <String, dynamic>{
      // `serializeRating` sends the stored `rating.user` string parsed back
      // into an object — it does not re-derive the rater. Planet groups
      // ratings by it, so a document without one cannot be shown back to its
      // author or deduplicated against their next rating; it is not optional.
      //
      // Since schema v46 both writers of the row fill the column, so the
      // `users`-table rebuild below is a fallback rather than the path: it
      // covers a rating whose rater is not a row on this device at all, which
      // the stored string handles and a lookup cannot.
      'user':
          _storedRater(row.user) ??
          RatingsRepository.raterDocument(user, row.parentCode, row.planetCode),
      'item': row.item,
      'type': row.type,
      'title': row.title,
      'time': row.time,
      // Kotlin's `createdOn` is the user's parent code, not a timestamp
      // (`RatingsRepositoryImpl`: `createdOn = resolvedUser.parentCode`), and
      // a row pulled from the server carries whatever the document said.
      'rate': row.rate,
      'createdOn': row.createdOn ?? row.parentCode,
      'parentCode': row.parentCode,
      'planetCode': row.planetCode,
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

  /// The rater object stored on the row, or `null` when there is none to read.
  ///
  /// An empty object counts as none: `JsonUtils.getJsonObject` returns one for
  /// a document with no `user` key, so `"{}"` is what such a document stores,
  /// and sending it would name nobody where the rebuild can still name the
  /// signed-in user.
  static Map<String, dynamic>? _storedRater(String? stored) {
    if (stored == null || stored.trim().isEmpty) return null;
    try {
      final decoded = jsonDecode(stored);
      if (decoded is! Map<String, dynamic> || decoded.isEmpty) return null;
      return decoded;
    } on FormatException {
      return null;
    }
  }
}
