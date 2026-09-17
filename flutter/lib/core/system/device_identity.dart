import '../prefs/planet_prefs.dart';
import 'device_stats.dart';

/// The device fields Kotlin appends to locally-authored CouchDB docs.
class DeviceIdentity {
  const DeviceIdentity({
    required this.androidId,
    required this.deviceName,
    required this.customDeviceName,
  });

  /// `DOCUMENT_ORIGIN` in `utils/DocumentOrigin.kt` — the value Planet reads
  /// to tell a document this app authored from one Planet itself wrote.
  static const String documentOrigin = 'myplanet';

  final String androidId;
  final String deviceName;
  final String customDeviceName;

  /// Port of `JsonObject.addDocumentOrigin` (`utils/DocumentOrigin.kt`): the
  /// authoring device's id plus the `app` marker, and nothing else.
  ///
  /// Kotlin stamps *only* these two at the serializers that had no device
  /// telemetry before `27c0470` — `CourseProgress.serializeProgress`,
  /// `Feedback.serializeFeedback`, `Meetup.serialize`, `TeamTask.serialize`,
  /// `StepExam.serializeExam`, `SubmitPhotos.serialize` and
  /// `VoicesRepositoryImpl.serializeNews` — so a caller porting one of those
  /// wants this rather than [documentFields], which would over-send the two
  /// device names.
  Map<String, dynamic> get originFields => {
    'androidId': androidId,
    'app': documentOrigin,
  };

  /// The origin pair plus the two device names, for the serializers that
  /// carried all of them before `27c0470` (`Personal.serialize`,
  /// `Rating.serialize`, `SearchActivity.serialize`,
  /// `TeamsRepositoryImpl.serializeTeamActivities`, `serializeSubmission`).
  /// Those sites called `addDocumentOrigin()` in place of their existing
  /// `addProperty("androidId", …)`, so `app` is the only field new on the
  /// wire — which is why it belongs here and not at each call site.
  Map<String, dynamic> get documentFields => {
    ...originFields,
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
