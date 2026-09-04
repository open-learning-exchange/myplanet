import 'dart:convert';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/prefs/planet_prefs.dart';
import '../core/system/device_stats.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/user_mapper.dart';

/// Port of `ActivitiesRepositoryImpl.uploadMyPlanetActivities` — the
/// `myplanet_activities` document that records device identity and tablet
/// foreground usage.
///
/// This is the per-sync telemetry doc, distinct from the per-row activity
/// queue in [ActivitiesUploader]. The Kotlin posts it from
/// `AutoSyncWorker`/`UserDataWorker` via `UploadManager.uploadActivities`'s
/// `uploadMyPlanetActivities` half, gated on a signed-in non-manager user.
///
/// The two POST + one GET the Kotlin issues, in order:
///
/// 1. POST `getNormalMyPlanetActivities` — the "sync"-type document (no `_id`,
///    so CouchDB assigns one) carrying `last_synced`, `parentCode`,
///    `createdOn`, `version`/`versionName`, `androidId`/`uniqueAndroidId`,
///    `customDeviceName`, `deviceName`, `time`, and `type = sync`.
/// 2. GET `myplanet_activities/<androidId>@<uniqueIdentifier>` — the existing
///    "usages"-type document, if one was posted on a previous sync.
/// 3. POST the merged document: the fetched doc with this sync's
///    `getTabletUsages` rows appended to its `usages` array, or a fresh
///    "usages"-type document (carrying `_id = androidId@uniqueIdentifier`) if
///    step 2 found nothing.
///
/// After a successful merge POST, `lastUsageUploaded` advances to "now" so the
/// next upload's `UsageStatsManager` query starts from where this one left
/// off — the Kotlin does the same.
///
/// Authentication is the `satellite:PIN` Basic header, the credential
/// `UrlUtils.header` resolves in the Kotlin. [endpointFor] stores no
/// credential, matching the rest of the port's URL discipline.
class MyPlanetActivitiesUploader {
  MyPlanetActivitiesUploader(this._api, this._prefs, this._deviceStats);

  final PlanetApi _api;
  final PlanetPrefs _prefs;
  final DeviceStats _deviceStats;

  static const String _database = 'myplanet_activities';

  /// Credential-free, matching [ActivitiesUploader.endpointFor]. The PIN
  /// travels as the `Authorization` header at send time.
  static String endpointFor(ServerConfig config) =>
      '${UrlUtils.credentialFreeDbUrl(config)}/$_database';

  /// The `satellite:PIN` Basic auth header, port of `UrlUtils.header`.
  static String authHeaderFor(ServerConfig config) =>
      UrlUtils.authHeader(config);

  /// The document id of the per-device "usages" doc:
  /// `<androidId>@<uniqueIdentifier>`, matching `MyPlanet.getMyPlanetActivities`.
  static String docIdFor(String androidId, String uniqueIdentifier) =>
      '$androidId@$uniqueIdentifier';

  /// Runs the upload for [user]. Returns `true` only if the final merge POST
  /// succeeded; a failure on any step returns `false` and leaves
  /// `lastUsageUploaded` untouched, so the next attempt re-reads the same
  /// interval.
  ///
  /// Returns `true` vacuously when the user is a manager — the Kotlin skips
  /// the upload for managers entirely (`UploadManager.uploadActivities`).
  Future<bool> upload({
    required UserRow user,
    required ServerConfig config,
  }) async {
    if (UserMapper.isManager(user)) return true;

    final endpoint = endpointFor(config);
    final authHeader = authHeaderFor(config);

    final androidId = await _deviceStats.androidId();
    final uniqueIdentifier = await _deviceStats.uniqueIdentifier();
    final deviceName = await _deviceStats.deviceName();
    final versionCode = await _deviceStats.versionCode();
    final versionName = await _deviceStats.versionName() ?? '';

    // Step 1: POST the "sync" doc. The Kotlin fires this unconditionally; a
    // failure here does not abort the merge half, and neither does the Kotlin
    // — it lets the exception propagate out of `uploadMyPlanetActivities`,
    // which `UploadManager` logs and reports. We mirror that by best-efforting
    // it: a failure is swallowed so the usage merge still runs.
    final syncDoc = _normalMyPlanetActivities(
      user: user,
      androidId: uniqueIdentifier,
      uniqueAndroidId: androidId,
      deviceName: deviceName,
      versionCode: versionCode,
      versionName: versionName,
    );
    await _api.postJsonObject(endpoint, syncDoc, authHeader: authHeader);

    // Step 2: GET the existing per-device "usages" doc.
    final docId = docIdFor(androidId, uniqueIdentifier);
    final existing = await _api.getJsonObject(
      '$endpoint/$docId',
      authHeader: authHeader,
    );

    // Step 3: build the merged doc and POST it.
    final sinceMillis = _prefs.lastUsageUploaded;
    final usages = await _deviceStats.tabletUsageStats(
      sinceMillis: sinceMillis,
    );
    final now = DateTime.now().millisecondsSinceEpoch;

    final Map<String, dynamic> merged;
    switch (existing) {
      case NetworkSuccess(:final data):
        // Append to the existing `usages` array. The Kotlin reads `data`'s
        // `usages` as a JsonArray and addAll's into it; if the field is
        // missing or not a list, start from empty rather than throwing.
        final list = data['usages'];
        final existingUsages = list is List
            ? List<Map<String, dynamic>>.from(
                list.whereType<Map<String, dynamic>>(),
              )
            : <Map<String, dynamic>>[];
        merged = Map<String, dynamic>.from(data)
          ..['usages'] = [
            ...existingUsages,
            for (final u in usages)
              _usageDoc(u, androidId: uniqueIdentifier, deviceName: deviceName),
          ];
      default:
        // No existing doc: build a fresh "usages"-type doc carrying the
        // device's `_id`, matching `MyPlanet.getMyPlanetActivities`.
        merged = _myPlanetActivities(
          user: user,
          androidId: androidId,
          uniqueIdentifier: uniqueIdentifier,
          deviceName: deviceName,
          usages: usages,
        );
    }

    final result = await _api.postJsonObject(
      endpoint,
      merged,
      authHeader: authHeader,
    );

    if (result is! NetworkSuccess<Map<String, dynamic>>) return false;

    // Advance the cutoff so the next upload's usage query starts from now.
    await _prefs.setLastUsageUploaded(now);
    return true;
  }

  /// Port of `MyPlanet.getNormalMyPlanetActivities` — the "sync"-type doc.
  ///
  /// `androidId` here is the Kotlin's `NetworkUtils.getUniqueIdentifier`
  /// value (the field the Kotlin names `androidId` despite it being the
  /// `androidId_buildId` composite); `uniqueAndroidId` is the bare
  /// `Settings.Secure.ANDROID_ID`. The naming follows the Kotlin exactly so
  /// the server-side aggregation matches.
  Map<String, dynamic> _normalMyPlanetActivities({
    required UserRow user,
    required String androidId,
    required String uniqueAndroidId,
    required String deviceName,
    required int versionCode,
    required String versionName,
  }) {
    final doc = <String, dynamic>{
      'last_synced': _prefs.lastSync,
      'parentCode': user.parentCode,
      'createdOn': user.planetCode,
      'version': versionCode,
      'versionName': versionName,
      'androidId': androidId,
      'uniqueAndroidId': uniqueAndroidId,
      'customDeviceName': _prefs.customDeviceName,
      'deviceName': deviceName,
      'time': DateTime.now().millisecondsSinceEpoch,
      'type': 'sync',
    };
    final planetVersion = _planetVersion();
    if (planetVersion != null) doc['planetVersion'] = planetVersion;
    return doc;
  }

  /// Port of `MyPlanet.getMyPlanetActivities` — the fresh "usages"-type doc,
  /// built only when no per-device doc exists yet.
  Map<String, dynamic> _myPlanetActivities({
    required UserRow user,
    required String androidId,
    required String uniqueIdentifier,
    required String deviceName,
    required List<TabletUsageStats> usages,
  }) {
    final doc = <String, dynamic>{
      '_id': docIdFor(androidId, uniqueIdentifier),
      'last_synced': _prefs.lastSync,
      'parentCode': user.parentCode,
      'createdOn': user.planetCode,
      'type': 'usages',
      'usages': [
        for (final u in usages)
          _usageDoc(u, androidId: uniqueIdentifier, deviceName: deviceName),
      ],
    };
    final planetVersion = _planetVersion();
    if (planetVersion != null) doc['planetVersion'] = planetVersion;
    return doc;
  }

  /// Port of `MyPlanet.addStats` — one foreground-usage row.
  ///
  /// [androidId] must be the `getUniqueIdentifier()` composite, not the bare
  /// ANDROID_ID: `addStats` writes `NetworkUtils.getUniqueIdentifier()` into a
  /// field it names `androidId`, exactly as the doc-level serializer does. The
  /// server aggregates per device on this value, so sending the bare id here
  /// while the "sync" doc sends the composite would split one device into two.
  Map<String, dynamic> _usageDoc(
    TabletUsageStats u, {
    required String androidId,
    required String deviceName,
  }) => {
    'lastTimeUsed': u.lastTimeUsed,
    'firstTimeUsed': u.firstTimeUsed,
    'totalForegroundTime': u.totalForegroundTime,
    'totalUsed': u.totalUsed,
    'version': u.version,
    'versionName': u.versionName,
    'androidId': androidId,
    'customDeviceName': _prefs.customDeviceName,
    'deviceName': deviceName,
    'time': u.time,
  };

  /// Reads `planetVersion` off the cached `/versions` JSON, the way the
  /// Kotlin parses `getVersionDetail()` into a `MyPlanet` and reads
  /// `planet.planetVersion`. `null` when the cache is empty or the field is
  /// absent — the Kotlin then omits the property rather than writing null.
  String? _planetVersion() {
    final raw = _prefs.versionDetail;
    if (raw == null || raw.isEmpty) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map<String, dynamic>) {
        final value = decoded['planetVersion'];
        return value is String ? value : null;
      }
    } catch (_) {
      // A malformed cache is treated as absent, matching Gson's failure path
      // which the Kotlin swallows with `e.printStackTrace()`.
    }
    return null;
  }
}
