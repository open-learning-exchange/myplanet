import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/core/system/device_stats.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Stats extends Mock implements DeviceStats {}

class _SecureStorage extends Mock implements FlutterSecureStorage {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late PlanetPrefs prefs;
  late _Stats stats;

  setUp(() async {
    SharedPreferences.setMockInitialValues({
      'customDeviceName': 'library tablet',
    });
    prefs = PlanetPrefs(
      await SharedPreferences.getInstance(),
      secureStorage: _SecureStorage(),
    );
    stats = _Stats();
  });

  test('reads the platform identity and caches it for headless work', () async {
    when(() => stats.uniqueIdentifier()).thenAnswer((_) async => 'id_build');
    when(() => stats.deviceName()).thenAnswer((_) async => 'TEST DEVICE');

    final identity = await PlatformDeviceIdentitySource(stats, prefs).read();

    expect(identity.androidId, 'id_build');
    expect(identity.deviceName, 'TEST DEVICE');
    expect(identity.customDeviceName, 'library tablet');
    expect(prefs.deviceUniqueIdentifier, 'id_build');
    expect(prefs.deviceModelName, 'TEST DEVICE');
  });

  test('uses the cache when the background engine has no channel', () async {
    await prefs.cacheDeviceIdentity(
      uniqueIdentifier: 'cached_build',
      deviceName: 'CACHED DEVICE',
    );
    when(() => stats.uniqueIdentifier()).thenThrow(StateError('no channel'));

    final identity = await PlatformDeviceIdentitySource(stats, prefs).read();

    expect(identity.androidId, 'cached_build');
    expect(identity.deviceName, 'CACHED DEVICE');
    expect(identity.customDeviceName, 'library tablet');
  });

  test('does not fabricate an identity when platform and cache are absent', () {
    when(() => stats.uniqueIdentifier()).thenThrow(StateError('no channel'));

    expect(
      () => PlatformDeviceIdentitySource(stats, prefs).read(),
      throwsStateError,
    );
  });
}
