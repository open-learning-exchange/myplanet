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

  /// [retryRefused] overrides the memo [OutboxRepository.enqueue] keeps against
  /// a request the server has already refused. The sweeps this method serves —
  /// a saved examination, a background pass — must not override it, which is
  /// the whole of Phase 148's fix. `MyHealthScreen`'s **Retry** must: it is
  /// offered next to a count of stranded records, and a button that provably
  /// does nothing is worse than one extra POST.
  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
    bool retryRefused = false,
  }) async {
    final rows = await _repository.getUpdated();
    for (final row in rows) {
      if (retryRefused) await _outbox.rearm(type, row.id);
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
        // Defensive, and deliberately kept. `PlanetApi` reaches this branch
        // only on a 2xx whose body decoded to a JSON object
        // (`planet_api.dart:249-253` checks the status *before* converting,
        // and a 2xx that will not decode becomes a `NetworkException`), and
        // CouchDB answers a create with `id` and no `rev` only for a
        // `?batch=ok` POST — which nothing in this port or in the Kotlin app
        // sends. So this is not a shape the server produces here; what it
        // catches is an intermediary that reshapes a 2xx body. Phase 148
        // audited that and says so rather than repeating the earlier claim
        // that `batch=ok` was the route.
        //
        // Kotlin gates on `has("id")` instead and tolerates a null rev
        // (`HealthRepositoryImpl.kt:103-131`), clearing `isUpdated` without
        // touching `_rev` (`HealthExaminationDao.kt:36-46`, which is what
        // `1004e90` added). The port cannot copy that half: its
        // `markUploaded` writes `rev: Value(rev)`, so passing the null we have
        // would erase the revision the row already holds — the pre-`1004e90`
        // defect exactly, and `app_database.dart` is not this lane's file.
        //
        // What *is* fixed here is the classification. A null code is how
        // [OutboxDrainer] recognises a handler's own verdict, and it reads it
        // as [OutboxRefusal.indeterminate]: the transport said the write
        // landed, so re-sending risks a second copy of a record that is
        // already filed. The row is abandoned once and never re-sent under the
        // same payload. Before Phase 148 the same return abandoned the row on
        // its first attempt *and* let the next sweep mint a fresh one,
        // re-POSTing the unchanged `_rev` into a 409 for the life of the
        // install. The record stays `isUpdated` and `OutboxDao.abandoned`
        // keeps reporting it as stranded, which is the honest answer: the app
        // does not know whether it arrived.
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
      // must go with it. Since Phase 148 an item owns a single outbox row that
      // `markCompleted` has just deleted, so this is belt-and-braces for the
      // pile an older build left behind — `outbox` is preserved across schema
      // bumps, so those rows outlive the upgrade. Without it `MyHealthScreen`
      // would warn about a record that is on the server, with no action to
      // offer. Delivery is the only event that makes an old refusal untrue.
      await _outbox.clearAbandoned(type, row.itemId);
    }
    return result;
  };
}
