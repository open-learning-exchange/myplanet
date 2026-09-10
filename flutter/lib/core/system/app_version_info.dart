import 'package:package_info_plus/package_info_plus.dart';

/// The app's version name and build number at runtime.
///
/// Mirrors `BuildConfig.VERSION_NAME` / `BuildConfig.VERSION_CODE` in the
/// Kotlin app, which the About and Settings screens read to render their
/// version line. The port had been hardcoding `0.62.97` / `Build 6297`,
/// which drifts from the pubspec version on every release.
///
/// `package_info_plus` reads the same values the build bakes into the
/// Android manifest; under `flutter test` it returns a placeholder
/// (`0.0.0` / `0`), so widget tests inject a fake via [appVersionInfoProvider].
typedef AppVersionInfo = ({String version, String buildNumber});

/// `PackageInfo.version` is an empty string rather than null when the
/// platform reports nothing; the build number is the same. Normalise both so
/// callers never see an empty string: `0.0.0` / `0` read sanely in the UI.
AppVersionInfo _normalize(PackageInfo info) => (
  version: info.version.isEmpty ? '0.0.0' : info.version,
  buildNumber: info.buildNumber.isEmpty ? '0' : info.buildNumber,
);

/// Production reads through `package_info_plus`. Tests override
/// [appVersionInfoProvider] rather than mocking this static.
Future<AppVersionInfo> loadAppVersionInfo() async =>
    _normalize(await PackageInfo.fromPlatform());
