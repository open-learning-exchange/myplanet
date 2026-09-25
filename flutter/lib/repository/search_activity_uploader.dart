import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/system/device_identity.dart';
import '../core/system/device_stats.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';
import 'search_activity_repository.dart';

/// Port of `UploadManager.uploadSearchActivity` — the `search_activities`
/// half of `UploadConfigs.SearchActivity`.
///
/// One row per filtered search the user runs on the courses or resources
/// list, queued at write time and drained to `search_activities` on the next
/// sync/app resume. Without this the port logged searches that never left the
/// device.
///
/// The Kotlin sends these directly through `UploadCoordinator.uploadRoom`;
/// the port routes them through the outbox so a process death between
/// `saveSearchActivity` and the sync does not lose the row (the same reason
/// every other locally-authored upload here uses the outbox).
class SearchActivityUploader {
  SearchActivityUploader(
    this._api,
    this._repo,
    this._dao,
    this._outbox,
    this._identity,
    this._deviceStats,
  );

  static const type = 'searchActivity';

  final PlanetApi _api;
  final SearchActivityRepository _repo;
  final SearchActivityDao _dao;
  final OutboxRepository _outbox;
  final DeviceIdentitySource _identity;

  /// Read for the bare `Settings.Secure.ANDROID_ID` alone — see [serialize].
  final DeviceStats _deviceStats;

  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/search_activities';

  /// Serializes a row for upload. Port of `SearchActivity.serialize`, with
  /// the device identity layered on at queue time (matching how the other
  /// uploaders add it).
  ///
  /// [androidId] is the **bare** `Settings.Secure.ANDROID_ID`, and this is the
  /// one document in either tree that carries it. `SearchActivity.serialize` is
  /// the single `addDocumentOrigin` call site that passes the argument
  /// explicitly — `addDocumentOrigin(VersionUtils.getAndroidId(context))` —
  /// where every other site takes the parameter's default,
  /// `NetworkUtils.getUniqueIdentifier()`, which is the `androidId + "_" +
  /// Build.ID` composite. [DeviceIdentity.androidId] is that composite (its own
  /// doc comment says so), so spreading [DeviceIdentity.documentFields] alone
  /// sent the wrong id here: Planet saw one handset as two devices depending on
  /// which document it was aggregating. The two device names still come from
  /// the identity, matching the `addProperty` calls Kotlin makes immediately
  /// after `addDocumentOrigin`.
  static Map<String, dynamic> serialize(
    SearchActivityRow row, {
    required DeviceIdentity identity,
    required String androidId,
  }) {
    return {
      ...SearchActivityRepository.serialize(row),
      ...identity.documentFields,
      'androidId': androidId,
    };
  }

  Future<int> queuePending({
    required ServerConfig config,
    String? userId,
  }) async {
    final rows = await _repo.pendingUploads();
    if (rows.isEmpty) return 0;
    final identity = await _identity.read();
    final androidId = await _deviceStats.androidId();
    for (final row in rows) {
      await _outbox.enqueue(
        uploadType: type,
        itemId: row.id,
        endpoint: endpointFor(config),
        payload: serialize(row, identity: identity, androidId: androidId),
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
      final id = data['id'];
      final rev = data['rev'];
      if (id is! String || rev is! String) {
        return const NetworkError(null, 'Upload response carried no id/rev');
      }
      await _dao.markUploaded(row.itemId, id, rev);
    }
    return result;
  };
}
