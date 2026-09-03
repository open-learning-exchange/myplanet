import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../repository/notifications_repository.dart';

/// Port of the grouping model added to
/// `ui/notifications/NotificationsViewModel.kt` (commit 8f4d06d5d).
///
/// Notifications are grouped by `type`, and each group is rendered as an
/// expandable header followed by its items. A group is **expanded by default
/// only when it has at least one unread notification**; a manual toggle on a
/// header overrides that default in either direction, and toggling twice
/// restores the default. The two override sets (`collapsed`/`expanded`) make
/// "user said so" outrank "unread says so".
///
/// Unrecognized types collapse into a single `notification` ("Other") group,
/// matching the Kotlin's `KNOWN_TYPES` guard. The display order is the
/// Kotlin's `typeOrder` first, then any remaining (normalized) types.

/// A node in the grouped notifications list, porting `NotificationListItem`.
sealed class NotificationListItem {
  const NotificationListItem();
}

class NotificationHeaderItem extends NotificationListItem {
  const NotificationHeaderItem({
    required this.type,
    required this.unreadCount,
    required this.isExpanded,
  });

  /// The normalized type (`normalizeNotificationType`); the screen resolves it
  /// to a localized label through [groupLabelFor].
  final String type;
  final int unreadCount;
  final bool isExpanded;

  @override
  bool operator ==(Object other) =>
      other is NotificationHeaderItem &&
      type == other.type &&
      unreadCount == other.unreadCount &&
      isExpanded == other.isExpanded;

  @override
  int get hashCode => Object.hash(type, unreadCount, isExpanded);
}

class NotificationEntryItem extends NotificationListItem {
  const NotificationEntryItem({required this.notification});

  final NotificationRow notification;

  @override
  bool operator ==(Object other) =>
      other is NotificationEntryItem && notification == other.notification;

  @override
  int get hashCode => notification.hashCode;
}

/// The Kotlin's fixed group order; unknown types follow in their insertion
/// order.
const notificationTypeOrder = <String>[
  'join_request',
  'team_join',
  'task',
  'chat',
  'voice_reply',
  'resource',
  'storage',
];

/// Normalizes a **resolved** `type` the way `buildNotificationGroups`'s
/// `groupBy` does: a known type keeps its (lower-cased) value, anything else
/// becomes `notification`.
///
/// Kotlin groups `Notification.type`, which `formatNotification` has already
/// put through `resolveType` — so this must never be handed a raw server type.
/// [notificationGroupType] is the entry point that gets that ordering right.
String normalizeNotificationType(String type) {
  final t = type.toLowerCase();
  return knownNotificationTypes.contains(t) ? t : 'notification';
}

/// The group a stored row belongs to: resolve the server's raw type first, then
/// normalize. Grouping the raw type instead put every server notification in
/// "Other".
String notificationGroupType(NotificationRow row) =>
    normalizeNotificationType(resolvedNotificationType(row));

/// The default expansion rule: a group is expanded only when it has unread
/// items (`isGroupDefaultExpanded`).
bool groupIsDefaultExpanded(List<NotificationRow> notifications, String type) =>
    notifications.any((n) => notificationGroupType(n) == type && !n.isRead);

/// Builds the grouped list, porting `buildGroupedList`. A group's expansion is
/// the explicit override if present, otherwise the unread-driven default.
List<NotificationListItem> buildGroupedList(
  List<NotificationRow> notifications, {
  required Set<String> collapsedGroups,
  required Set<String> expandedGroups,
}) {
  if (notifications.isEmpty) return const [];
  final grouped = <String, List<NotificationRow>>{};
  for (final n in notifications) {
    (grouped[notificationGroupType(n)] ??= []).add(n);
  }
  final orderedTypes = <String>[
    for (final t in notificationTypeOrder)
      if (grouped.containsKey(t)) t,
    for (final t in grouped.keys)
      if (!notificationTypeOrder.contains(t)) t,
  ];
  return [
    for (final type in orderedTypes)
      ...() {
        final items = grouped[type]!;
        final unreadCount = items.where((n) => !n.isRead).length;
        final isExpanded = expandedGroups.contains(type)
            ? true
            : collapsedGroups.contains(type)
            ? false
            : unreadCount > 0;
        return [
          NotificationHeaderItem(
            type: type,
            unreadCount: unreadCount,
            isExpanded: isExpanded,
          ),
          if (isExpanded)
            for (final n in items) NotificationEntryItem(notification: n),
        ];
      }(),
  ];
}

/// The fixed group labels, porting `NotificationsViewModel.typeLabelFor`.
/// Keyed on the normalized type; the collective labels ("Join Requests",
/// "Tasks") differ from the per-notification titles in `NotificationsScreen`.
String groupLabelFor(AppLocalizations l10n, String type) {
  switch (type) {
    case 'join_request':
      return l10n.notifGroupJoinRequests;
    case 'team_join':
      return l10n.notifGroupTeamUpdates;
    case 'task':
      return l10n.tasks;
    case 'chat':
      return l10n.notifGroupNewVoices;
    case 'voice_reply':
      return l10n.notifGroupVoiceReplies;
    case 'resource':
      return l10n.resources;
    case 'storage':
      return l10n.notificationGroupSystem;
    default:
      return l10n.notificationGroupOther;
  }
}

/// The ARB key for a group label, for tests that have no [AppLocalizations].
String groupLabelKey(String type) {
  switch (type) {
    case 'join_request':
      return 'notifGroupJoinRequests';
    case 'team_join':
      return 'notifGroupTeamUpdates';
    case 'task':
      return 'tasks';
    case 'chat':
      return 'notifGroupNewVoices';
    case 'voice_reply':
      return 'notifGroupVoiceReplies';
    case 'resource':
      return 'resources';
    case 'storage':
      return 'notificationGroupSystem';
    default:
      return 'notificationGroupOther';
  }
}
