import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/prefs/planet_prefs.dart';
import '../core/system/device_stats.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import 'activities_repository.dart';
import 'outbox_drainer.dart';
import 'outbox_repository.dart';

/// Durable write-back for the activity log.
///
/// Kotlin sends this from `AutoSyncWorker`/`UserDataWorker` through
/// `UploadManager.uploadActivities`, which drives four destinations:
///
/// | Rows | CouchDB database | Kotlin |
/// |---|---|---|
/// | `offline_activity` logins | `login_activities` | `ActivitiesRepositoryImpl.uploadActivities` |
/// | `resource_activity` visits/downloads | `resource_activities` | `UploadConfigs.ResourceActivities` |
/// | `resource_activity` syncs | `admin_activities` | `UploadConfigs.ResourceActivitiesSync` |
/// | `course_activity` visits | `course_activities` | `UploadConfigs.CourseActivities` |
///
/// There is no background scheduling in the port, so the outbox carries them
/// instead: queued when the row is written and on a completed sync, drained on
/// app resume. Until this existed the rows accumulated on the device forever —
/// `offline_activity` was written by every sign-in and read only by the
/// dashboard.
///
/// Two deliberate differences from `uploadActivities`:
///
/// - **The response's `id`/`rev` are required.** `changeRev` reads `_id`/`_rev`
///   out of a CouchDB insert response, which carries `id`/`rev`; through
///   `JsonUtils.getString` those misses become `""`, so the Kotlin marks a row
///   uploaded with an empty revision. `_rev = ""` is not null, so the row does
///   drop out of `getPendingLoginUploads` and is not re-posted — but the
///   document can never be updated again. Recording the real values matches
///   every other uploader here, and a response without them is an error rather
///   than a success (see `RatingsUploader` for what that silence cost).
/// - **`serializeLoginActivities`' `_id` branch is not reproduced.** It writes
///   `ob.addProperty("_id", activity.logoutTime)` — the logout *timestamp* as
///   the document id. It is unreachable in practice, because the rows it
///   serializes are exactly those with a null `_rev`, and nothing sets `_id`
///   without also setting `_rev`. Reproducing a nonsense id on the chance the
///   branch is ever reached would corrupt the document.
///
/// The `androidId`/`deviceName`/`customDeviceName` telemetry every one of these
/// serializers adds comes from `NetworkUtils`/`SharedPrefManager`; the port
/// reads the same values through the [DeviceStats] seam and [PlanetPrefs],
/// captured once per `queuePending` pass (they are constant per device).
class ActivitiesUploader {
  ActivitiesUploader(
    this._api,
    this._activities,
    this._outbox,
    this._deviceStats,
    this._prefs,
  );

  /// One `uploadType` per destination database. They are separate types rather
  /// than one with a variable endpoint because the handler has to know which
  /// table to mark uploaded, and a `sync` row and a `visit` row live in the
  /// same table but go to different databases.
  static const loginType = 'login_activity';
  static const resourceType = 'resource_activity';
  static const resourceSyncType = 'resource_activity_sync';
  static const courseType = 'course_activity';

  final PlanetApi _api;
  final ActivitiesRepository _activities;
  final OutboxRepository _outbox;
  final DeviceStats _deviceStats;
  final PlanetPrefs _prefs;

  /// Credential-free, as `outbox.endpoint` is persisted; the PIN travels as the
  /// `Authorization` header at send time.
  static String endpointFor(ServerConfig config, String database) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/$database';

  /// Queues every pending activity row. Returns how many were queued.
  Future<int> queuePending({required ServerConfig config}) async {
    // Device identity is constant per device, so read it once for the whole
    // pass rather than per row.
    final androidId = await _deviceStats.uniqueIdentifier();
    final deviceName = await _deviceStats.deviceName();
    final customDeviceName = _prefs.customDeviceName;
    final device = DeviceTelemetry(androidId, deviceName, customDeviceName);

    var queued = 0;

    for (final row in await _activities.pendingLoginUploads()) {
      await _outbox.enqueue(
        uploadType: loginType,
        itemId: row.id,
        endpoint: endpointFor(config, 'login_activities'),
        payload: loginDoc(row, device),
        userId: row.userId,
      );
      queued++;
    }

    for (final row in await _activities.pendingResourceUploads()) {
      await _outbox.enqueue(
        uploadType: resourceType,
        itemId: row.id,
        endpoint: endpointFor(config, 'resource_activities'),
        payload: resourceDoc(row, device),
      );
      queued++;
    }

    for (final row in await _activities.pendingSyncUploads()) {
      await _outbox.enqueue(
        uploadType: resourceSyncType,
        itemId: row.id,
        endpoint: endpointFor(config, 'admin_activities'),
        payload: resourceDoc(row, device),
      );
      queued++;
    }

    for (final row in await _activities.pendingCourseUploads()) {
      await _outbox.enqueue(
        uploadType: courseType,
        itemId: row.id,
        endpoint: endpointFor(config, 'course_activities'),
        payload: courseDoc(row, device),
      );
      queued++;
    }

    return queued;
  }

  /// Handlers, one per upload type. Registered on the drainer in
  /// `app_providers.dart`.
  Map<String, OutboxHandler> get handlers => {
    loginType: _handlerFor(_activities.markLoginUploaded),
    resourceType: _handlerFor(_activities.markResourceUploaded),
    resourceSyncType: _handlerFor(_activities.markResourceUploaded),
    courseType: _handlerFor(_activities.markCourseUploaded),
  };

  OutboxHandler _handlerFor(
    Future<int> Function(String localId, String remoteId, String rev) mark,
  ) => (row, payload, authHeader) async {
    final result = await _api.postJsonObject(
      row.endpoint,
      payload,
      authHeader: authHeader,
    );
    if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
      final remoteId = data['id']?.toString();
      final rev = data['rev']?.toString();
      if (remoteId == null || remoteId.isEmpty || rev == null || rev.isEmpty) {
        // Retiring the outbox entry here would leave the row's `_rev` null, so
        // the next `queuePending` would post the same activity again as a
        // second document.
        return const NetworkError<Map<String, dynamic>>(
          null,
          'Upload response carried no id or rev',
        );
      }
      await mark(row.itemId, remoteId, rev);
    }
    return result;
  };

  /// Port of `ActivitiesRepositoryImpl.serializeLoginActivities`.
  ///
  /// `user` carries the user *name* — the Kotlin's key is `user` and its value
  /// is `activity.userName`, so the column named `userId` never reaches the
  /// server. The `androidId`/`deviceName`/`customDeviceName` fields mirror the
  /// Kotlin serializer, which writes all three on every login doc.
  static Map<String, dynamic> loginDoc(
    OfflineActivityRow row,
    DeviceTelemetry device,
  ) => {
    'user': row.userName,
    'type': row.type,
    'loginTime': row.loginTime,
    'logoutTime': row.logoutTime,
    'createdOn': row.createdOn,
    'parentCode': row.parentCode,
    'androidId': device.androidId,
    'deviceName': device.deviceName,
    'customDeviceName': device.customDeviceName,
  };

  /// Port of the top-level `serializeResourceActivities` in
  /// `ActivitiesRepositoryImpl.kt`, shared by the `resource_activities` and
  /// `admin_activities` configs. The Kotlin writes `androidId`/`deviceName`
  /// only (no `customDeviceName`), so this matches that shape.
  static Map<String, dynamic> resourceDoc(
    ResourceActivityRow row,
    DeviceTelemetry device,
  ) => {
    'user': row.user,
    'resourceId': row.resourceId,
    'type': row.type,
    'title': row.title,
    'time': row.time,
    'createdOn': row.createdOn,
    'parentCode': row.parentCode,
    'androidId': device.androidId,
    'deviceName': device.deviceName,
  };

  /// Port of `CourseActivity.serialize`. Writes `androidId`/`deviceName` only,
  /// matching the Kotlin.
  static Map<String, dynamic> courseDoc(
    CourseActivityRow row,
    DeviceTelemetry device,
  ) => {
    'user': row.user,
    'courseId': row.courseId,
    'type': row.type,
    'title': row.title,
    'time': row.time,
    'createdOn': row.createdOn,
    'parentCode': row.parentCode,
    'androidId': device.androidId,
    'deviceName': device.deviceName,
  };
}

/// The per-device telemetry captured once per `queuePending` pass, threaded
/// into the per-row serializers. Read through the [DeviceStats] seam and
/// [PlanetPrefs] rather than `NetworkUtils`/`SharedPrefManager`.
class DeviceTelemetry {
  const DeviceTelemetry(this.androidId, this.deviceName, this.customDeviceName);

  /// `NetworkUtils.getUniqueIdentifier` — the `androidId_buildId` composite
  /// the Kotlin writes as the `androidId` field.
  final String androidId;

  /// `NetworkUtils.getDeviceName` — `MANUFACTURER MODEL`, uppercased.
  final String deviceName;

  /// `NetworkUtils.getCustomDeviceName` — the user-editable label, or empty.
  final String customDeviceName;
}
