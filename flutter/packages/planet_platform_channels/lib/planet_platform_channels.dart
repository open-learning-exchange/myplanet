/// The Android side of the `disk_stats` and `device_stats` method channels.
///
/// This package exists for its Kotlin, not its Dart: being a real plugin on a
/// path dependency puts `PlanetPlatformChannelsPlugin` into
/// `GeneratedPluginRegistrant`, so **every** engine — the Activity's and the
/// headless ones the `workmanager` plugin starts — gets both channels. When
/// they lived in `MainActivity.configureFlutterEngine`, background work threw
/// `MissingPluginException` and fell back to the UI-primed preference caches
/// (Phases 45–46); those caches remain as a fallback for values cached before
/// this plugin existed.
///
/// The app's Dart side keeps its own `MethodChannel('disk_stats')` /
/// `MethodChannel('device_stats')` wrappers in `core/system/` — this library
/// deliberately exports nothing.
library;
