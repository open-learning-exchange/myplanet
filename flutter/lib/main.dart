import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/background/background_scheduler.dart';
import 'core/background/background_work_coordinator.dart';
import 'core/notifications/notification_presenter.dart';
import 'core/prefs/planet_prefs.dart';
import 'core/system/device_stats.dart';
import 'core/system/disk_stats.dart';
import 'providers/app_providers.dart';

/// Entry point, replacing `MainApplication.kt` and the launcher Activity.
///
/// Only bootstrap lives here. Theme, locale and routing belong to `app.dart`.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // SharedPreferences is loaded up front so the rest of the graph can read the
  // server config synchronously — the same reason MainApplication initialises
  // SharedPrefManager before anything else.
  final prefs = await PlanetPrefs.load();

  // Scheduling is best-effort infrastructure, not an app-launch gate. A
  // plugin/OS registration failure must not leave the user staring at a blank
  // screen; WorkManager will be retried on the next cold start.
  unawaited(_startBackgroundWork(prefs));

  // `POST_NOTIFICATIONS` has to be asked for from the UI isolate — the
  // background isolate that shows deadline reminders has no Activity to prompt
  // from. Fire-and-forget for the same reason as scheduling: a refused or
  // failed request must not block launch, and the reminder path tolerates the
  // permission being absent.
  unawaited(_requestNotificationPermission());
  unawaited(_primeDeviceIdentity(prefs));

  runApp(
    ProviderScope(
      overrides: [planetPrefsProvider.overrideWithValue(prefs)],
      child: const MyPlanetApp(),
    ),
  );
}

Future<void> _primeDeviceIdentity(PlanetPrefs prefs) async {
  try {
    await prefs.cacheDeviceIdentity(
      uniqueIdentifier: await DeviceStats.instance.uniqueIdentifier(),
      deviceName: await DeviceStats.instance.deviceName(),
    );
    // Same reason, different channel: the deadline notifier's storage-warning
    // step runs headless, where `disk_stats` is unreachable. Priming it here is
    // what makes that step do anything at all.
    final stats = await DiskStats.instance.storageStats();
    if (stats.totalBytes > 0) {
      await prefs.cacheStorageAvailablePercent(
        (stats.availableBytes / stats.totalBytes * 100).round(),
      );
    }
  } catch (error, stack) {
    FlutterError.reportError(
      FlutterErrorDetails(
        exception: error,
        stack: stack,
        library: 'device identity bootstrap',
      ),
    );
  }
}

Future<void> _requestNotificationPermission() async {
  try {
    await LocalNotificationsPresenter().requestPermission();
  } catch (error, stack) {
    FlutterError.reportError(
      FlutterErrorDetails(
        exception: error,
        stack: stack,
        library: 'notification permission',
      ),
    );
  }
}

Future<void> _startBackgroundWork(PlanetPrefs prefs) async {
  try {
    await BackgroundWorkCoordinator(
      const WorkmanagerScheduler(),
      prefs,
    ).start();
  } catch (error, stack) {
    FlutterError.reportError(
      FlutterErrorDetails(
        exception: error,
        stack: stack,
        library: 'background scheduling',
      ),
    );
  }
}
