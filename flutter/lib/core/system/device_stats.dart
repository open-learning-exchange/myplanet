import 'package:flutter/services.dart';

/// Port of the device-identity and tablet-usage telemetry the Kotlin app reads
/// through `NetworkUtils`, `VersionUtils`, and `MyPlanet.getTabletUsages`.
///
/// Every field here backs a value the pure-Dart SDK cannot produce:
///
/// - [androidId] -- `Settings.Secure.ANDROID_ID`, the per-app-install device
///   id. Used as the document id half (`androidId@uniqueIdentifier`) and as
///   the `androidId`/`uniqueAndroidId` fields on the `myplanet_activities` and
///   per-row activity documents.
/// - [uniqueIdentifier] -- `androidId + "_" + Build.ID`, the
///   `NetworkUtils.getUniqueIdentifier` value the Kotlin writes as `androidId`
///   on every activity serializer (despite the name, it is *not* the bare
///   ANDROID_ID).
/// - [deviceName] -- `Build.MANUFACTURER + " " + Build.MODEL`, uppercased.
/// - [versionCode] / [versionName] -- the installed app's package version, as
///   `VersionUtils.getVersionCode` / `getVersionName` read it.
/// - [tabletUsageStats] -- `UsageStatsManager.queryUsageStats` for this app's
///   own package since [sinceMillis], the rows `MyPlanet.getTabletUsages`
///   serializes into the `myplanet_activities` `usages` array. Needs the
///   `PACKAGE_USAGE_STATS` privileged permission.
///
/// The method-channel-backed implementation is used in production; tests inject
/// a fake directly, the same way [DiskStats] is injected. This is an interface
/// rather than a static helper for that reason.
abstract class DeviceStats {
  Future<String> androidId();

  Future<String> uniqueIdentifier();

  Future<String> deviceName();

  Future<int> versionCode();

  Future<String?> versionName();

  /// Per-app foreground usage rows since [sinceMillis], matching
  /// `MyPlanet.getTabletUsages`'s `UsageStatsManager.INTERVAL_DAILY` query.
  ///
  /// Returns an empty list when the user has not granted the
  /// `PACKAGE_USAGE_STATS` permission -- the Kotlin's own query simply yields
  /// no rows in that case, and the document posts with an empty `usages`
  /// array.
  Future<List<TabletUsageStats>> tabletUsageStats({required int sinceMillis});

  /// The method-channel-backed implementation used in production. Calls
  /// `device_stats/<method>`, answered by the Kotlin `MainActivity`.
  static DeviceStats instance = _MethodChannelDeviceStats();
}

/// One row of `MyPlanet.addStats`'s output: a single app's foreground usage
/// over a `UsageStatsManager` interval.
///
/// The row carries only what the usage query actually measures. `addStats` also
/// writes three device-identity fields onto every row — `androidId`,
/// `customDeviceName`, `deviceName` — but those are device-wide constants, not
/// per-row facts, and two of them cannot be answered correctly from the
/// platform side: the Kotlin's `androidId` field is `getUniqueIdentifier()` (the
/// `androidId_buildId` composite, *not* the bare ANDROID_ID the name suggests)
/// and `customDeviceName` is a stored preference, which on this side lives in
/// `PlanetPrefs`. `MyPlanetActivitiesUploader` fills all three when it
/// serializes a row, so they are deliberately absent here rather than being
/// carried across the channel where a plausible-looking wrong value already
/// slipped in once.
class TabletUsageStats {
  const TabletUsageStats({
    required this.lastTimeUsed,
    required this.firstTimeUsed,
    required this.totalForegroundTime,
    required this.totalUsed,
    required this.version,
    required this.versionName,
    required this.time,
  });

  final int lastTimeUsed;
  final int firstTimeUsed;
  final int totalForegroundTime;
  final int totalUsed;
  final int version;
  final String versionName;
  final int time;
}

class _MethodChannelDeviceStats implements DeviceStats {
  static const _channel = MethodChannel('device_stats');

  @override
  Future<String> androidId() async =>
      (await _channel.invokeMethod<String>('getAndroidId')) ?? '';

  @override
  Future<String> uniqueIdentifier() async =>
      (await _channel.invokeMethod<String>('getUniqueIdentifier')) ?? '';

  @override
  Future<String> deviceName() async =>
      (await _channel.invokeMethod<String>('getDeviceName')) ?? '';

  @override
  Future<int> versionCode() async =>
      (await _channel.invokeMethod<int>('getVersionCode')) ?? 0;

  @override
  Future<String?> versionName() async =>
      await _channel.invokeMethod<String>('getVersionName');

  @override
  Future<List<TabletUsageStats>> tabletUsageStats({
    required int sinceMillis,
  }) async {
    final result = await _channel.invokeMethod<List>('getTabletUsages', {
      'sinceMillis': sinceMillis,
    });
    final list = result ?? const [];
    return [
      for (final entry in list)
        if (entry is Map)
          TabletUsageStats(
            lastTimeUsed: _readInt(entry, 'lastTimeUsed'),
            firstTimeUsed: _readInt(entry, 'firstTimeUsed'),
            totalForegroundTime: _readInt(entry, 'totalForegroundTime'),
            totalUsed: _readInt(entry, 'totalUsed'),
            version: _readInt(entry, 'version'),
            versionName: _readString(entry, 'versionName'),
            time: _readInt(entry, 'time'),
          ),
    ];
  }

  int _readInt(Map map, String key) {
    final value = map[key];
    if (value is int) return value;
    if (value is num) return value.toInt();
    return 0;
  }

  String _readString(Map map, String key) {
    final value = map[key];
    return value is String ? value : '';
  }
}
