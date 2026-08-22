import 'package:myplanet/core/system/device_identity.dart';

const testDeviceIdentity = FixedDeviceIdentitySource(
  DeviceIdentity(
    androidId: 'android-id_build-id',
    deviceName: 'TEST DEVICE',
    customDeviceName: 'classroom tablet',
  ),
);

const testDeviceFields = <String, dynamic>{
  'androidId': 'android-id_build-id',
  'deviceName': 'TEST DEVICE',
  'customDeviceName': 'classroom tablet',
};
