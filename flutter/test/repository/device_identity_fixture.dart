import 'package:myplanet/core/system/device_identity.dart';

const testDeviceIdentity = FixedDeviceIdentitySource(
  DeviceIdentity(
    androidId: 'android-id_build-id',
    deviceName: 'TEST DEVICE',
    customDeviceName: 'classroom tablet',
  ),
);

/// The four fields a locally-authored document carries: the device trio plus
/// the `app` origin marker `addDocumentOrigin` stamps (`utils/DocumentOrigin.kt`).
const testDeviceFields = <String, dynamic>{
  'androidId': 'android-id_build-id',
  'app': 'myplanet',
  'deviceName': 'TEST DEVICE',
  'customDeviceName': 'classroom tablet',
};
