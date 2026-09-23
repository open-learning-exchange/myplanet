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
  // which lives in a widget — can reach the graph. The scope below wraps this
  // same container, registers the same vsync and builds the same inherited
  // widget, so every `containerOf` call site still resolves and scheduling is
  // unchanged. The retry policy the scope would have supplied is passed here
  // instead.
  //
  // **One thing does change and cannot be matched**, so it is written down
  // rather than claimed away: the scope passes its container an `onError` that
  // routes an uncaught provider error to `FlutterError.reportError` with
  // `library: 'riverpod'` (`provider_scope.dart:161-175`), and
  // `ProviderContainer.onError` is `@internal` — application code cannot pass
  // it (the analyzer refuses). Without it the container falls back to
  // `Zone.current.handleUncaughtError` (`provider_container.dart:901`), so
  // such an error leaves through the root zone and loses the label. It is
  // still *recorded*, because `installErrorHandlers` below hooks that zone as
  // well as `FlutterError.onError` — this phase closing the hole it opened,
  // which is worth stating rather than relying on quietly.
  //
  // (`provider_retry_policy_test.dart` scans source text **without stripping
  // comments**, so writing either root's constructor call — the bare class
  // name followed by an open paren — inside a comment makes it fire, and this
  // paragraph is deliberately phrased to avoid doing so. Its negative
  // lookbehind exempts the `Uncontrolled…` spelling wherever that appears.
  // `unwatched_provider_reads_test.dart` strips comments before scanning for
  // exactly this reason; reported rather than worked around, since that file
  // is outside this lane's set.)
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
  recorder.observeForeground();

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
