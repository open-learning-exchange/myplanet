import 'core/providers/provider_retry.dart';
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';
import 'core/background/background_scheduler.dart';
import 'core/background/background_work_coordinator.dart';
import 'core/notifications/notification_presenter.dart';
import 'core/prefs/planet_prefs.dart';
import 'core/system/crash_log_store.dart';
import 'core/system/device_stats.dart';
import 'core/system/disk_stats.dart';
import 'providers/app_providers.dart';
import 'repository/apk_log_recorder.dart';

/// Entry point, replacing `MainApplication.kt` and the launcher Activity.
///
/// Only bootstrap lives here. Theme, locale and routing belong to `app.dart`.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // SharedPreferences is loaded up front so the rest of the graph can read the
  // server config synchronously — the same reason MainApplication initialises
  // SharedPrefManager before anything else.
  final prefs = await PlanetPrefs.load();

  // Resolved before anything else can fail, because a crash report has to be
  // written *synchronously* from the error hook and `getApplicationDocuments
  // Directory` is a platform channel. Kotlin gets `context.filesDir` for free
  // off the Application object; this is the port's equivalent, and the same
  // UI-primed-cache shape the device identity and `disk_stats` values use.
  await CrashLogStore.prime();

  // An explicit container, rather than letting the widget-tree scope build one
  // from `overrides:`, so the error hooks and the startup sweep — neither of
  // which lives in a widget — can reach the graph. The scope below is the same
  // root the tree would have built; nothing about the app's providers changes,
  // and the retry policy moves onto the container, which is where
  // `provider_retry_policy_test.dart` already expects to find it for an
  // `UncontrolledProviderScope`.
  //
  // (That guard scans source text **without stripping comments**, so naming
  // the widget it exempts — in the obvious `Name(` form — inside a comment
  // makes it fire. Reported rather than worked around in its own file, which
  // this lane does not own.)
  final container = ProviderContainer(
    retry: noProviderRetry,
    overrides: [planetPrefsProvider.overrideWithValue(prefs)],
  );

  final recorder = ApkLogRecorder(container);
  // Installed synchronously and early, as `MainApplication.onCreate` installs
  // its handler at `:229` before the deferred initialisation it schedules.
  // Until this existed the port had **no** uncaught-error handler at all: a
  // crash in the field printed to a console nobody was holding and reached
  // neither the database nor the server.
  recorder.installErrorHandlers();

  // `MainApplication.sweepPendingLogs`, which runs after
  // `initializeDatabaseConnection()` in the same sequential body. Unawaited
  // for the reason the three calls below are: telemetry must never be a gate
  // on the first frame.
  unawaited(recorder.sweepPendingFiles());

  // `MainApplication.onAppStarted` (`:470`) — **once per process start**, not
  // once per login, whatever the type string says.
  unawaited(recorder.log(type: ApkLogRecorder.newLoginType));
  _observeForeground(recorder);

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
    UncontrolledProviderScope(container: container, child: const MyPlanetApp()),
  );
}

/// `MainApplication.onAppForegrounded` (`:463`), behind the `isFirstLaunch`
/// gate at `:459-465`.
///
/// An [AppLifecycleListener] rather than a `WidgetsBindingObserver` because
/// there is no widget here to hang one off — the same reason Kotlin puts this
/// on `ProcessLifecycleOwner` rather than an Activity. The listener is
/// deliberately never disposed: it lives exactly as long as the process, which
/// is what `ProcessLifecycleOwner` gives the Kotlin.
///
/// The skip-the-first gate is load-bearing rather than cosmetic. `resumed`
/// fires on the first foreground too, so without it every launch would file
/// both a `"new login"` and a `"foreground"` row and Planet's aggregation
/// would read twice the sessions there were.
void _observeForeground(ApkLogRecorder recorder) {
  var seenFirstResume = false;
  AppLifecycleListener(
    onResume: () {
      if (!seenFirstResume) {
        seenFirstResume = true;
        return;
      }
      unawaited(recorder.log(type: ApkLogRecorder.foregroundType));
    },
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
