package org.ole.planet.myplanet

import io.flutter.embedding.android.FlutterActivity

/**
 * Nothing to configure: the `disk_stats` and `device_stats` method channels
 * that used to be registered here moved into the in-tree
 * `planet_platform_channels` plugin (`flutter/packages/planet_platform_channels`),
 * which `GeneratedPluginRegistrant` attaches to every engine — including the
 * headless ones the `workmanager` plugin starts, which never pass through an
 * Activity and used to get `MissingPluginException` from both channels.
 */
class MainActivity : FlutterActivity()
