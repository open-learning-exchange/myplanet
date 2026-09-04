import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'notification_config.dart';

/// Shows an OS notification. Port of the surface
/// `NotificationUtils.NotificationManager.showNotification` exposes.
///
/// A seam rather than a direct plugin call for the usual reason: the deadline
/// policy in [TaskDeadlineNotifier] is ordinary Dart with unit tests, and it
/// runs in a background isolate where a plugin channel is the one thing a test
/// cannot have.
abstract interface class NotificationPresenter {
  /// Returns whether the notification was actually shown, matching the Kotlin's
  /// boolean return. `false` covers a suppressed duplicate and a platform
  /// failure alike — the caller uses it only for logging, the same as Kotlin.
  Future<bool> show(NotificationConfig config);
}

/// `flutter_local_notifications`-backed implementation.
///
/// Channel creation happens on first [show] rather than in a constructor: the
/// Kotlin creates channels in `NotificationManager`'s `init`, which runs the
/// first time `getInstance` is called, and doing it lazily keeps construction
/// free of platform calls so the class can be built in a test that never shows
/// anything.
class LocalNotificationsPresenter implements NotificationPresenter {
  LocalNotificationsPresenter([FlutterLocalNotificationsPlugin? plugin])
    : _plugin = plugin ?? FlutterLocalNotificationsPlugin();

  final FlutterLocalNotificationsPlugin _plugin;
  bool _initialized = false;

  /// `NotificationUtils.getInstance` + `createNotificationChannels`, minus the
  /// channels no ported path produces.
  Future<void> _ensureInitialized() async {
    if (_initialized) return;
    await _plugin.initialize(
      settings: const InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      ),
    );
    await _plugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.createNotificationChannel(
          const AndroidNotificationChannel(
            NotificationChannels.tasks,
            NotificationChannels.tasksName,
            description: NotificationChannels.tasksDescription,
            // `IMPORTANCE_HIGH` + vibration + badge, as the Kotlin's
            // `ChannelConfig(CHANNEL_TASKS, …, IMPORTANCE_HIGH, true, true)`.
            importance: Importance.high,
            enableVibration: true,
            enableLights: true,
            showBadge: true,
          ),
        );
    _initialized = true;
  }

  @override
  Future<bool> show(NotificationConfig config) async {
    try {
      await _ensureInitialized();
      await _plugin.show(
        // `config.id.hashCode()` in the Kotlin. Dart's `String.hashCode` is a
        // different function, but the only property that matters is that the
        // same string maps to the same int within one app, so re-showing
        // replaces instead of stacking.
        id: config.id.hashCode,
        title: config.title,
        body: config.message,
        notificationDetails: NotificationDetails(
          android: AndroidNotificationDetails(
            NotificationChannels.tasks,
            NotificationChannels.tasksName,
            channelDescription: NotificationChannels.tasksDescription,
            priority: switch (config.priority) {
              NotificationPriority.high => Priority.high,
              NotificationPriority.defaultPriority => Priority.defaultPriority,
            },
            importance: Importance.high,
            category: AndroidNotificationCategory.reminder,
            autoCancel: config.autoCancel,
            styleInformation: config.bigTextStyle
                ? BigTextStyleInformation(config.message)
                : null,
          ),
        ),
      );
      return true;
    } catch (_) {
      // The Kotlin catches and prints, returning false: a notification failure
      // must not abort the worker, which still has tasks to mark notified.
      return false;
    }
  }

  /// Asks for `POST_NOTIFICATIONS` (Android 13+). Called from the UI isolate at
  /// startup — a background isolate has no Activity to prompt from, which is
  /// why this is separate from [show] rather than folded into it.
  ///
  /// Returns false when the permission was refused or the platform call failed.
  /// The deadline path runs either way; without the grant the OS simply drops
  /// the notification, and the in-app notification row is still written.
  Future<bool> requestPermission() async {
    try {
      await _ensureInitialized();
      final granted = await _plugin
          .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin
          >()
          ?.requestNotificationsPermission();
      return granted ?? false;
    } catch (_) {
      return false;
    }
  }
}
