/// Port of the tray-side of `utils/NotificationUtils.kt` and of
/// `services/NotificationActionReceiver.kt` — what a tap on a system
/// notification carries back into the app, and what buttons the notification
/// offers.
///
/// Pure Dart on purpose. The routing decision this feeds (which screen a tap
/// opens, and which of the two read-marking paths runs) is the part worth
/// testing, and none of it needs a platform channel. The plugin side is in
/// `providers/notification_tap_provider.dart`, the same split
/// `core/deeplinks/deep_link.dart` uses.
library;

import 'dart:convert';

import 'notification_config.dart';

/// `NotificationUtils.ACTION_*` (`:57-59`), verbatim.
///
/// These strings cross a process boundary — the OS holds them while the app is
/// dead — so they are the Kotlin's own, not renamed. A handset carrying both
/// apps during the migration is a real case.
///
/// `ACTION_STORAGE_SETTINGS` is deliberately absent: it is only ever attached
/// to a `storage` notification, and nothing in either app raises one in the
/// tray (`createStorageWarningNotification` has no live caller in the Kotlin
/// either — the storage row is written straight to the `notifications` table by
/// `TaskDeadlineNotifier`). See the header of `notification_config.dart` for the
/// standing rule this follows.
abstract final class NotificationTapActions {
  /// `ACTION_MARK_AS_READ`. Marks read and does not navigate.
  static const markAsRead = 'mark_as_read';

  /// `ACTION_OPEN_NOTIFICATION`. Marks read and opens the notification's
  /// destination.
  static const open = 'open_notification';
}

/// What the OS hands back when a notification is tapped.
///
/// Kotlin puts three extras on both of its intents — `notification_type`,
/// `notification_id`, `related_id` (`NotificationUtils.kt:60-62`,
/// `:401-406`, `:413-424`) — where the plugin gives one opaque `payload`
/// string. The JSON below uses the Kotlin's own extra names so the two are
/// readable side by side.
class NotificationTapPayload {
  const NotificationTapPayload({
    required this.type,
    required this.notificationId,
    this.relatedId,
  });

  /// `NotificationUtils.TYPE_*`, i.e. `config.type`. This is the *raw* tray
  /// type, which for the one producer is already `task` — the value
  /// `NotificationDestinationResolver` switches on.
  final String type;

  /// `config.id`. For the one live producer this is the **task** id, not the
  /// id of any row in the `notifications` table — see
  /// `NotificationTapHandler` for what that costs.
  final String notificationId;

  /// `config.relatedId`.
  final String? relatedId;

  String encode() => jsonEncode({
    'notification_type': type,
    'notification_id': notificationId,
    if (relatedId != null) 'related_id': relatedId,
  });

  /// The inverse, returning null for anything this app did not write.
  ///
  /// A payload can be absent (a notification an older build of the app posted,
  /// still sitting in the tray after an upgrade) or unparseable, and neither is
  /// worth an exception on the launch path — the tap simply opens the app, which
  /// is what Kotlin's own extra-less notifications do
  /// (`ServerReachabilityWorker.kt:132-138` posts a tappable notification with
  /// no extras at all, and `handleNotificationIntent` then does nothing).
  static NotificationTapPayload? decode(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map) return null;
      final type = decoded['notification_type'];
      final id = decoded['notification_id'];
      if (type is! String || id is! String || id.isEmpty) return null;
      final related = decoded['related_id'];
      return NotificationTapPayload(
        type: type,
        notificationId: id,
        relatedId: related is String && related.isNotEmpty ? related : null,
      );
    } catch (_) {
      return null;
    }
  }

  /// The payload for a notification about to be shown.
  factory NotificationTapPayload.forConfig(NotificationConfig config) =>
      NotificationTapPayload(
        type: config.type,
        notificationId: config.id,
        relatedId: config.relatedId,
      );

  @override
  bool operator ==(Object other) =>
      other is NotificationTapPayload &&
      other.type == type &&
      other.notificationId == notificationId &&
      other.relatedId == relatedId;

  @override
  int get hashCode => Object.hash(type, notificationId, relatedId);

  @override
  String toString() =>
      'NotificationTapPayload($type, $notificationId, $relatedId)';
}

/// One tap. [actionId] is null when the notification's **body** was tapped,
/// which is the distinction the whole of `handleNotificationIntent` turns on:
/// the body reaches `DashboardActivity` directly with `from_notification`, and
/// an action button reaches `NotificationActionReceiver` first.
class NotificationTap {
  const NotificationTap({required this.payload, this.actionId});

  final NotificationTapPayload payload;
  final String? actionId;

  bool get isBodyTap => actionId == null;

  @override
  bool operator ==(Object other) =>
      other is NotificationTap &&
      other.payload == payload &&
      other.actionId == actionId;

  @override
  int get hashCode => Object.hash(payload, actionId);

  @override
  String toString() => 'NotificationTap($actionId, $payload)';
}

/// One action button, independent of the plugin that renders it.
///
/// Port of an entry `NotificationUtils.addNotificationActions` adds
/// (`:373-399`).
class NotificationAction {
  const NotificationAction({required this.id, required this.title});

  final String id;

  /// The Kotlin's own hardcoded English. `addNotificationActions` passes string
  /// literals, not `getString(R.string...)`, so there is nothing translated to
  /// port — and the producer runs in a background isolate with no
  /// `BuildContext` to resolve an `.arb` lookup against. Same reasoning as
  /// [NotificationConfig.task]'s title and body.
  final String title;

  @override
  bool operator ==(Object other) =>
      other is NotificationAction && other.id == id && other.title == title;

  @override
  int get hashCode => Object.hash(id, title);

  @override
  String toString() => 'NotificationAction($id, $title)';
}

/// Port of `NotificationUtils.addNotificationActions` (`:373-399`), for the
/// types this port produces.
///
/// Kotlin adds *Mark as Read* to every actionable notification and then one
/// type-specific action in a `when`. Only the `task` arm has a live producer in
/// either app — the other four (`TYPE_SURVEY`, `TYPE_STORAGE`,
/// `TYPE_JOIN_REQUEST`, `TYPE_RESOURCE`) hang off factories nothing calls, so
/// they are left out rather than shipped with no caller. Add the arm when a
/// producer appears; it is one line each.
///
/// A non-actionable config gets none, as `buildNotification`'s
/// `if (config.actionable)` decides (`:357-359`).
List<NotificationAction> notificationActionsFor(NotificationConfig config) {
  if (!config.actionable) return const [];
  return [
    const NotificationAction(
      id: NotificationTapActions.markAsRead,
      title: 'Mark as Read',
    ),
    if (config.type == NotificationTypes.task)
      const NotificationAction(
        id: NotificationTapActions.open,
        title: 'View Task',
      ),
  ];
}
