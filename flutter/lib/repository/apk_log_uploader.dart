import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'diagnostics_repository.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Port of `UploadConfigs.CrashLog` — the `apk_logs` upload direction.
///
/// Kotlin runs this through `UploadCoordinator.runPipeline`: a `POST` to
/// `<server>/db/apk_logs` with no document id in the path and none in the body,
/// batched 50 at a time, six concurrent. The port routes it through the outbox
/// instead, as every other locally-authored upload here does, so a process
/// death between the row and the send does not lose the report.
///
/// ### Three places this deliberately differs from the Kotlin
///
///  * **A retried log is not duplicated.** Kotlin's `RetryRepositoryImpl`
///    re-POSTs the stored payload and marks only its *own* retry row complete
///    — it never writes `_rev` back to `apk_log`, so the row is still pending
///    and the next `uploadCrashLog()` POSTs it again. Since no `_id` is sent,
///    CouchDB mints a fresh document each time, so one 5xx yields two or more
///    identical documents on the server. The port's [handler] writes the
///    revision back on the drain that succeeds, which is the whole point of
///    the handler existing.
///  * **A refusal does not accrete a row per sweep.** That is Phase 148's
///    outbox policy, not something this uploader decides: an `outbox` item
///    owns exactly one row for ever, and what re-arms a terminal one is a
///    changed request. Nothing here opts out of it.
///  * **A serialization failure is not silent.** `UploadCoordinator:110-113`
///    drops an item whose serializer throws out of the batch via `mapNotNull`,
///    recording neither an upload nor a failure, and the row stays pending for
///    ever. [DiagnosticsRepository.serialize] is total — it reads eight
///    non-nullable columns and cannot throw — so the port has no such hole to
///    reproduce.
///
/// One Kotlin behaviour is kept on purpose: **the document carries no id.**
/// Not `_id`, not `_rev`, not even the local row key. `ApkLog.serialize` puts
/// none of them and `UploadConfigs.CrashLog` discards the `remoteId` the POST
/// returns (`:223`, `ApkLogUpload(it.localId, it.remoteRev)`), because the
/// entity has no `_id` column to keep it in. The revision is stored solely as
/// the "already sent" marker, which is why [ApkLogs.rev] is the pending
/// predicate and nothing else reads it.
class ApkLogUploader {
  ApkLogUploader(this._repo, this._api, this._outbox, this._identity);

  static const type = 'apkLog';

  final DiagnosticsRepository _repo;
  final PlanetApi _api;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/apk_logs';

  /// Port of `ApkLog.serialize(log, customDeviceName)`.
  ///
  /// [DeviceIdentity.documentFields] is exactly the four keys Kotlin adds at
  /// serialize time: `androidId` and `app` from the no-argument
  /// `addDocumentOrigin()` — so the **composite** `<ANDROID_ID>_<Build.ID>`,
  /// not the bare id `SearchActivity.serialize` passes — plus the two device
  /// names. `customDeviceName` is the raw preference, defaulting to the empty
  /// string; the `ifEmpty { getDeviceName() }` fallback belongs to
  /// `LoginActivity:686` alone and must not be pulled into the serializer.
  static Map<String, dynamic> serialize(
    ApkLog row, {
    required DeviceIdentity identity,
  }) {
    return {
      ...DiagnosticsRepository.serialize(row),
      ...identity.documentFields,
    };
  }

  /// Queues every unsent report. Port of `fetchPendingItems` →
  /// `ApkLogDao.getPending`, which has no `ORDER BY` and no `LIMIT`: all of
  /// them, every time.
  ///
  /// Guests are included, as Kotlin's are — `RoomUploadConfig` has no
  /// `filterGuests` field, so `shouldFilter` takes the interface default
  /// `false`. A crash is worth reporting whoever hit it.
  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _repo.pendingUploads();
    if (rows.isEmpty) return 0;
    final identity = await _identity.read();
    final endpoint = endpointFor(config);
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpoint,
        payload: serialize(row, identity: identity),
        userId: userId,
      );
    }
    return rows.length;
  }

  /// Writes the revision back so the row stops being pending.
  ///
  /// The `id` the response carries is deliberately dropped, matching
  /// `UploadConfigs.CrashLog`. A 2xx whose body has no usable `rev` is
  /// reported as an error rather than silently dequeuing the row: Kotlin's
  /// `JsonUtils.getString` returns `""` for a missing field, which is non-null,
  /// so `normalizeUploadResult` dequeues a row whose document may not exist.
  /// The port refuses instead — the same call the sibling uploaders make.
  OutboxHandler get handler => (row, payload, authHeader) async {
    final result = await _api.postJsonObject(
      row.endpoint,
      payload,
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final rev = data['rev'];
      if (rev is! String || rev.isEmpty) {
        return const NetworkError(null, 'Upload response carried no rev');
      }
      // `markUploaded` reports back the ids that matched no row, which is the
      // contract `ApkLogDao.markUploadedBatch` holds so a vanished row is not
      // recorded as delivered. One id at a time here, because the outbox
      // drains per row.
      final unapplied = await _repo.markUploaded({row.itemId: rev});
      if (unapplied.isNotEmpty) {
        return const NetworkError(
          null,
          'Local apk_log update applied to no row',
        );
      }
    }
    return result;
  };
}
