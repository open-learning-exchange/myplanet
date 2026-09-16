import 'dart:async';

import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'notification_config.dart';
import 'notification_tap.dart';

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

  /// Every tap the plugin delivers while the app is running, from whichever
  /// presenter happened to initialize the plugin.
  ///
  /// A library-level broadcast sink rather than a callback passed in at
  /// construction, because `initialize` is the *only* place the plugin accepts
  /// a response handler and it is called lazily by whichever presenter shows or
  /// requests first — `main.dart` builds one to ask for the permission, and
  /// `notificationPresenterProvider` builds another. Passing the handler per
  /// instance meant whichever initialized second silently overwrote the first
  /// with null. Registering the same sink from every instance has no such
  /// ordering.
  ///
  /// Process-lifetime, so never closed. Broadcast, so a tap with no listener is
  /// dropped — which is correct: the tap that *launched* the app does not come
  /// through here at all but through `getNotificationAppLaunchDetails`, which is
  /// the plugin's documented split (see `initialize`'s dartdoc: the callback
  /// "is fired when the user selects a notification … [while the] application
  /// was running").
  static final _responses = StreamController<NotificationResponse>.broadcast();

  static Stream<NotificationResponse> get responses => _responses.stream;

  /// `NotificationUtils.getInstance` + `createNotificationChannels`, minus the
  /// channels no ported path produces.
  Future<void> _ensureInitialized() async {
    if (_initialized) return;
    await _plugin.initialize(
      settings: const InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      ),
      onDidReceiveNotificationResponse: _responses.add,
      // No `onDidReceiveBackgroundNotificationResponse`, and that is a
      // *faithfulness* decision rather than an omission. Kotlin's action
      // buttons are `PendingIntent.getBroadcast` into
      // `NotificationActionReceiver`, which looks like a background handler —
      // but every one of its three arms calls `markNotificationAsRead`, whose
      // tail unconditionally does
      // `startActivity(DashboardActivity, action = REFRESH_NOTIFICATION_BADGE)`
      // (`NotificationActionReceiver.kt:104-112`). So *Mark as Read* brings the
      // dashboard to the foreground in the Android app too, and every action
      // here is `showsUserInterface: true` — which routes it to the callback
      // above, in this isolate, with the Riverpod graph and the session already
      // built.
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
        // Kotlin's three intent extras, in one string. Without it a tap can
        // say *that* a notification was tapped and nothing about which one, so
        // neither the read-marking nor the navigation has an id to work with —
        // which is exactly the state the port shipped in until Phase 130.
        payload: NotificationTapPayload.forConfig(config).encode(),
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
            actions: [
              for (final action in notificationActionsFor(config))
                AndroidNotificationAction(
                  action.id,
                  action.title,
                  // See `_ensureInitialized`: both of Kotlin's actions end up
                  // launching the dashboard, so both belong in this isolate.
                  showsUserInterface: true,
                  // `NotificationActionReceiver` calls `clearNotification(it)`
                  // on every arm (`:38,47,65`). `autoCancel` covers the *body*
                  // tap only, so without this an action would mark the
                  // notification read and leave it sitting in the tray.
                  cancelNotification: true,
                ),
            ],
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
