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
  Future<DeviceIdentity> read() async {
    String androidId;
    String deviceName;
    try {
      // Kotlin calls NetworkUtils.getUniqueIdentifier(), not bare ANDROID_ID.
      androidId = await _stats.uniqueIdentifier();
      deviceName = await _stats.deviceName();
      await _prefs.cacheDeviceIdentity(
        uniqueIdentifier: androidId,
        deviceName: deviceName,
      );
    } catch (_) {
      // WorkManager may launch a Flutter engine without MainActivity, which is
      // where this app registers its custom channel. Bootstrap primes these
      // values from the UI engine so headless outbox drains retain parity.
      androidId = _prefs.deviceUniqueIdentifier ?? '';
      deviceName = _prefs.deviceModelName ?? '';
      if (androidId.isEmpty && deviceName.isEmpty) rethrow;
    }
    return DeviceIdentity(
      androidId: androidId,
      deviceName: deviceName,
      customDeviceName: _prefs.customDeviceName,
    );
  }
}
