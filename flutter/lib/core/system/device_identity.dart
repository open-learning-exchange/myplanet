import '../prefs/planet_prefs.dart';
import 'device_stats.dart';

/// The three device fields Kotlin appends to locally-authored CouchDB docs.
class DeviceIdentity {
  const DeviceIdentity({
    required this.androidId,
    required this.deviceName,
    required this.customDeviceName,
  });

  final String androidId;
  final String deviceName;
  final String customDeviceName;

  Map<String, dynamic> get documentFields => {
    'androidId': androidId,
    'deviceName': deviceName,
    'customDeviceName': customDeviceName,
  };
}

abstract interface class DeviceIdentitySource {
  Future<DeviceIdentity> read();
}

/// Deterministic source for tests and non-platform embedders.
class FixedDeviceIdentitySource implements DeviceIdentitySource {
  const FixedDeviceIdentitySource(this.identity);

  final DeviceIdentity identity;

  @override
  Future<DeviceIdentity> read() async => identity;
}

/// Reads identity at queue time rather than caching it: Android's identity is
/// stable, but the user-editable custom device name is not.
class PlatformDeviceIdentitySource implements DeviceIdentitySource {
  const PlatformDeviceIdentitySource(this._stats, this._prefs);

  final DeviceStats _stats;
  final PlanetPrefs _prefs;

  @override
  Future<DeviceIdentity> read() async => DeviceIdentity(
    // Kotlin calls NetworkUtils.getUniqueIdentifier(), not bare ANDROID_ID.
    androidId: await _stats.uniqueIdentifier(),
    deviceName: await _stats.deviceName(),
    customDeviceName: _prefs.customDeviceName,
  );
}
