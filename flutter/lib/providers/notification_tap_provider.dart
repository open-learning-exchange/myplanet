import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/notifications/notification_presenter.dart';
import '../core/notifications/notification_tap.dart';
import '../ui/notifications/notification_destination.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// Where taps on system notifications come from.
///
/// An interface for the same reason [DeepLinkSource] is one: the production
/// implementation calls into a method channel, which throws
/// `MissingPluginException` under `flutter test`, and the interesting half is
/// the policy in [NotificationTapHandler].
abstract interface class NotificationTapSource {
  /// The tap that launched the app, if it was launched by one.
  ///
  /// Port of nothing in particular and of the whole cold-start case at once:
  /// Android hands `DashboardActivity` the notification's `Intent` in
  /// `onCreate`, and `handleNotificationIntent(intent)` reads it there
  /// (`DashboardActivity.kt:188`). The plugin splits the same thing in two —
  /// this, and [taps] for a tap that arrives while the app is already up,
  /// which is Kotlin's `onNewIntent` (`:1106`).
  Future<NotificationTap?> launchTap();

  /// Taps delivered while the app is already running.
  Stream<NotificationTap> taps();
}

/// `flutter_local_notifications`-backed [NotificationTapSource].
class LocalNotificationsTapSource implements NotificationTapSource {
  LocalNotificationsTapSource([FlutterLocalNotificationsPlugin? plugin])
    : _plugin = plugin ?? FlutterLocalNotificationsPlugin();

  final FlutterLocalNotificationsPlugin _plugin;

  @override
  Future<NotificationTap?> launchTap() async {
    final details = await _plugin.getNotificationAppLaunchDetails();
    if (details == null || !details.didNotificationLaunchApp) return null;
    return notificationTapFrom(details.notificationResponse);
  }

  @override
  Stream<NotificationTap> taps() => LocalNotificationsPresenter.responses
      .map(notificationTapFrom)
      .where((tap) => tap != null)
      .cast<NotificationTap>();
}

/// Translates the plugin's response into the port's own value, or null when
/// there is nothing to act on.
///
/// Three things are dropped, each deliberately:
///
///   * a response with no payload this app wrote — an upgrade can leave an
///     older build's notification in the tray, and Kotlin's own extra-less
///     notifications (`ServerReachabilityWorker.kt:132-138`) reach
///     `handleNotificationIntent` and fall through both its branches, i.e. a
///     plain app launch;
///   * a *dismissal*. The plugin's `ActionBroadcastReceiver` does forward one
///     through this same callback, but only when `dismissIsolate` is set on the
///     notification, and [LocalNotificationsPresenter] never sets it — so no
///     dismissal reaches here today and this arm is defensive. Kept because the
///     cost of being wrong is asymmetric: a swipe-away treated as a tap marks a
///     notification read that the user deliberately ignored, and on the
///     stamping path reorders their whole list to say so;
///   * an action id this build does not know, for the same upgrade reason.
NotificationTap? notificationTapFrom(NotificationResponse? response) {
  if (response == null) return null;
  if (response.notificationResponseType ==
      NotificationResponseType.notificationDismissed) {
    return null;
  }
  final payload = NotificationTapPayload.decode(response.payload);
  if (payload == null) return null;
  final actionId = response.actionId;
  if (actionId == null || actionId.isEmpty) {
    return NotificationTap(payload: payload);
  }
  if (actionId != NotificationTapActions.markAsRead &&
      actionId != NotificationTapActions.open) {
    return null;
  }
  return NotificationTap(payload: payload, actionId: actionId);
}

final notificationTapSourceProvider = Provider<NotificationTapSource>(
  (ref) => LocalNotificationsTapSource(),
);

/// Handles one tap on a system notification, and says where to go.
///
/// Port of `services/NotificationActionReceiver.kt` together with
/// `DashboardActivity.handleNotificationIntent` (`:478-560`) — the Kotlin
/// splits them because a broadcast and an activity intent arrive at different
/// components, and here they are the same callback with a different `actionId`.
/// Shaped after [DeepLinkHandler]: returns a location for the caller to
/// navigate to rather than navigating itself, so the whole policy is testable
/// without a widget.
///
/// **The three gestures, and why they are not one branch.**
///
/// | gesture | Kotlin | marks read via | navigates |
/// |---|---|---|---|
/// | the body | `handleNotificationIntent`'s `from_notification` branch | `markNotificationAsRead(id, userId)` — **does not** stamp `createdAt` | yes |
/// | *Mark as Read* | `ACTION_MARK_AS_READ` (`:34-39`) | `markNotificationsAsRead({id})` — **stamps** | no |
/// | *View Task* | `ACTION_OPEN_NOTIFICATION` (`:51-66`) | `markNotificationsAsRead({id})` — **stamps** | yes |
///
/// The two read paths are two different DAO statements in the Kotlin and have
/// to stay two here: the stamping one rewrites `createdAt`, which is the list's
/// sort key *and* is uploaded back to the server document's `time` by
/// `syncNotificationReads`. Phase 127 separated them and left the non-stamping
/// one with no caller at all; this is its caller.
///
/// **What it does not do, on purpose.** Kotlin's read-marking here matches no
/// row for the only notification either app raises. `TaskDeadlineNotifier`
/// mints the tray notification with `id = task.id` (as
/// `TaskNotificationWorker.kt:48-53` does), while a `task` notification *row*
/// is a synced CouchDB notification document whose id is its own `_id` and
/// which carries the task id in `relatedId`. So `markNotificationAsRead` and
/// `markAsRead` both look up an id that is not in the table and update nothing
/// — `markAsRead` even early-returns on the empty `getByIds`. Resolving
/// `relatedId` back to a notification row would fix that, and would be an
/// improvement the Android app does not have. It is reproduced and recorded
/// instead; see `PHASE_130_NOTES.md`.
class NotificationTapHandler {
  NotificationTapHandler(this.ref);

  final Ref ref;

  /// Returns the location to navigate to, or null when the tap navigates
  /// nowhere (*Mark as Read*, or a type with no destination).
  Future<String?> handle(NotificationTap tap) async {
    final payload = tap.payload;
    final repository = ref.read(notificationsRepositoryProvider);
    try {
      if (tap.isBodyTap) {
        // `markDatabaseNotificationAsRead(it)` (`DashboardActivity.kt:486,
        // :701-703`). The user id is what routes a `summary_` id to
        // `markSummaryAsRead`; Kotlin passes `user?.id` and tolerates null —
        // `checkUser()` calls `logout()` without stopping the enclosing
        // coroutine, so the Android app reaches here with a null user too.
        //
        // Awaited rather than read: this handler runs from a scope that never
        // watches `sessionProvider`, and `ref.read(...).valueOrNull` would be
        // null until something else resolved it. The `await` is inside the
        // `try` because a future can reject where `valueOrNull` could not —
        // the correction Phase 100's fix needed on harvest.
        final user = await ref.read(sessionProvider.future);
        await repository.markNotificationAsRead(
          payload.notificationId,
          user?.id,
        );
      } else {
        // Both action arms of `NotificationActionReceiver` call
        // `notificationsRepository.markNotificationsAsRead(setOf(id))`
        // (`:81`), the bulk statement, which stamps.
        await repository.markAsRead({payload.notificationId});
      }
    } catch (error, stack) {
      // `NotificationActionReceiver` wraps its write in `try/catch` with an
      // `e.printStackTrace()` and carries on to the navigation (`:76-84`); the
      // dashboard's `markNotificationAsRead` does the same
      // (`DashboardViewModel.kt:246-254`). A failed read-mark must not cost the
      // user the navigation they asked for.
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stack,
          library: 'notification tap',
        ),
      );
    }

    // *Mark as Read* navigates nowhere. It does bring the app to the
    // foreground in the Android app — `markNotificationAsRead`'s tail runs
    // `startActivity(DashboardActivity, action = REFRESH_NOTIFICATION_BADGE)`
    // unconditionally (`:104-112`) — which here is what tapping the action does
    // by itself, since `showsUserInterface: true` launches the app and the
    // bell is a live drift stream that needs no explicit refresh.
    if (tap.actionId == NotificationTapActions.markAsRead) return null;

    // Its own guard, not the read-mark's. `resolveFor` reads two DAOs, and
    // Kotlin's equivalent lookups sit inside `viewModelScope.launch` blocks
    // whose failure leaves the fragment alone rather than crashing it
    // (`resolveAndOpenTeam`, `handleTaskNavigation`). A tap that cannot resolve
    // its destination should navigate nowhere, not throw out of the stream
    // listener that delivered it.
    try {
      final database = ref.read(appDatabaseProvider);
      final destination = await NotificationDestinationResolver(
        taskDao: database.teamTaskDao,
        teamDao: database.teamDao,
      ).resolveFor(type: payload.type, relatedId: payload.relatedId);
      if (destination == null) return null;
      return notificationDestinationLocation(destination);
    } catch (error, stack) {
      FlutterError.reportError(
        FlutterErrorDetails(
          exception: error,
          stack: stack,
          library: 'notification tap',
        ),
      );
      return null;
    }
  }
}

final notificationTapHandlerProvider = Provider(NotificationTapHandler.new);
