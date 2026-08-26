# planet_platform_channels

In-tree plugin carrying the `disk_stats` and `device_stats` method channels.

It exists for its Kotlin, not its Dart: being a plugin on a path dependency
puts `PlanetPlatformChannelsPlugin` into `GeneratedPluginRegistrant`, so every
engine — the Activity's and the headless ones the `workmanager` plugin starts —
gets both channels. When they were registered in
`MainActivity.configureFlutterEngine`, background work threw
`MissingPluginException` and fell back to UI-primed preference caches.

The app's Dart wrappers stay in `lib/core/system/`; this package's Dart library
deliberately exports nothing.
