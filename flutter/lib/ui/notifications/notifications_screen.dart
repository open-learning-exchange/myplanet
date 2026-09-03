import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/notifications_provider.dart';
import '../../repository/notifications_repository.dart';
import '../router.dart';
import 'notification_destination.dart';
import 'notification_grouping.dart';

/// Port of `ui/notifications/NotificationsFragment.kt`.
///
/// Notifications are grouped by type with expandable headers, porting the
/// grouping model added to `NotificationsViewModel` (commit 8f4d06d5d). A
/// group is expanded by default only while it has unread items; tapping a
/// header overrides that, and *Mark all read* collapses every group.
class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final filter = ref.watch(notificationFilterProvider);
    final notifications = ref.watch(notificationsProvider);
    final unread = ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;
    final expansion = ref.watch(notificationExpansionProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.notifications),
        actions: [
          if (unread > 0)
            TextButton(
              onPressed: () =>
                  ref.read(notificationActionsProvider).markAllAsRead(),
              child: Text(l10n.markAllRead),
            ),
        ],
      ),
      body: Column(
        children: [
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.all(12),
            child: SegmentedButton<NotificationFilter>(
              segments: [
                ButtonSegment(
                  value: NotificationFilter.all,
                  label: Text(l10n.all),
                ),
                ButtonSegment(
                  value: NotificationFilter.unread,
                  label: Text(l10n.unreadCount(unread)),
                ),
                ButtonSegment(
                  value: NotificationFilter.read,
                  label: Text(l10n.read),
                ),
              ],
              selected: {filter},
              onSelectionChanged: (selected) {
                ref.read(notificationFilterProvider.notifier).state =
                    selected.single;
              },
            ),
          ),
          Expanded(
            child: notifications.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, _) =>
                  Center(child: Text(l10n.notificationsUnavailable)),
              data: (items) => items.isEmpty
                  ? _EmptyNotifications(filter: filter)
                  : _GroupedList(items: items, expansion: expansion),
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyNotifications extends StatelessWidget {
  const _EmptyNotifications({required this.filter});
  final NotificationFilter filter;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final message = switch (filter) {
      NotificationFilter.unread => l10n.noUnreadNotifications,
      NotificationFilter.read => l10n.noReadNotifications,
      NotificationFilter.all => l10n.noNotifications,
    };
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.notifications_none,
              size: 56,
              color: Theme.of(context).colorScheme.outline,
            ),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}

/// Renders the grouped list, porting `NotificationsAdapter`'s header/item
/// view types. A header tap toggles that group's expansion.
class _GroupedList extends ConsumerWidget {
  const _GroupedList({required this.items, required this.expansion});

  final List<NotificationRow> items;
  final NotificationExpansionState expansion;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final grouped = buildGroupedList(
      items,
      collapsedGroups: expansion.collapsed,
      expandedGroups: expansion.expanded,
    );
    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 24),
      itemCount: grouped.length,
      itemBuilder: (context, index) {
        final node = grouped[index];
        return switch (node) {
          NotificationHeaderItem() => _GroupHeader(header: node),
          NotificationEntryItem(:final notification) => _NotificationTile(
            notification: notification,
          ),
        };
      },
    );
  }
}

class _GroupHeader extends ConsumerWidget {
  const _GroupHeader({required this.header});

  final NotificationHeaderItem header;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final colors = Theme.of(context).colorScheme;
    return InkWell(
      onTap: () => ref
          .read(notificationExpansionProvider.notifier)
          .toggle(
            header.type,
            ref.read(notificationsProvider).valueOrNull ?? const [],
          ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(_iconFor(header.type), size: 22, color: colors.primary),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                _groupLabel(l10n, header.type),
                style: Theme.of(context).textTheme.titleSmall,
              ),
            ),
            if (header.unreadCount > 0)
              Padding(
                padding: const EdgeInsetsDirectional.only(end: 8),
                child: Badge(
                  isLabelVisible: true,
                  label: Text('${header.unreadCount}'),
                ),
              ),
            Icon(
              header.isExpanded
                  ? Icons.keyboard_arrow_up
                  : Icons.keyboard_arrow_down,
              color: colors.outline,
            ),
          ],
        ),
      ),
    );
  }
}

class _NotificationTile extends ConsumerWidget {
  const _NotificationTile({required this.notification});
  final NotificationRow notification;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final colors = Theme.of(context).colorScheme;
    return Dismissible(
      key: ValueKey(notification.id),
      direction: DismissDirection.endToStart,
      confirmDismiss: (_) => _confirmDelete(context),
      onDismissed: (_) =>
          ref.read(notificationActionsProvider).delete(notification.id),
      background: Container(
        color: colors.errorContainer,
        alignment: AlignmentDirectional.centerEnd,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Icon(Icons.delete_outline, color: colors.onErrorContainer),
      ),
      child: ListTile(
        leading: Badge(
          isLabelVisible: !notification.isRead,
          child: CircleAvatar(
            child: Icon(_iconFor(resolvedNotificationType(notification))),
          ),
        ),
        title: Text(
          notification.title?.trim().isNotEmpty == true
              ? notification.title!
              : _titleFor(l10n, resolvedNotificationType(notification)),
          style: TextStyle(
            fontWeight: notification.isRead
                ? FontWeight.normal
                : FontWeight.bold,
          ),
        ),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(notification.message),
            const SizedBox(height: 4),
            Text(
              DateFormat.yMMMd().add_jm().format(
                DateTime.fromMillisecondsSinceEpoch(notification.createdAt),
              ),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
        // Read notifications remain actionable. Kotlin marks an unread row and
        // navigates on the same tap; making `onTap` null after that first tap
        // prevented learners from ever reopening its destination in Flutter.
        onTap: () => _openNotification(context, ref),
      ),
    );
  }

  Future<void> _openNotification(BuildContext context, WidgetRef ref) async {
    if (!notification.isRead) {
      await ref.read(notificationActionsProvider).markAsRead(notification.id);
    }

    final database = ref.read(appDatabaseProvider);
    final destination = await NotificationDestinationResolver(
      taskDao: database.teamTaskDao,
      teamDao: database.teamDao,
    ).resolve(notification);
    if (destination == null || !context.mounted) return;
    final path = switch (destination.kind) {
      NotificationDestinationKind.resources => Routes.resources,
      NotificationDestinationKind.storage => Routes.storageManagement,
      NotificationDestinationKind.teamTasks =>
        '${Routes.teams}/${destination.teamId}/tasks',
      NotificationDestinationKind.teamMembers =>
        '${Routes.teams}/${destination.teamId}/members?tab=requests',
      NotificationDestinationKind.teamJoin =>
        '${Routes.teams}/${destination.teamId}',
      // The Flutter port has no team-chat tab yet (the upstream opens the
      // team's ChatPage), so the team detail is the closest destination.
      NotificationDestinationKind.teamChat =>
        '${Routes.teams}/${destination.teamId}',
      NotificationDestinationKind.voiceReply =>
        '${Routes.voices}/${destination.voiceId}',
    };
    context.go(path);
  }

  Future<bool> _confirmDelete(BuildContext context) async {
    final l10n = AppLocalizations.of(context);
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(l10n.deleteNotification),
            content: Text(l10n.deleteNotificationConfirmation),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: Text(l10n.cancel),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: Text(l10n.delete),
              ),
            ],
          ),
        ) ??
        false;
  }
}

IconData _iconFor(String type) => switch (type.toLowerCase()) {
  'task' => Icons.event_available_outlined,
  'chat' || 'voice_reply' => Icons.chat_bubble_outline,
  'resource' => Icons.folder_outlined,
  'storage' => Icons.storage_outlined,
  'join_request' || 'team_join' => Icons.group_add_outlined,
  _ => Icons.notifications_outlined,
};

String _titleFor(AppLocalizations l10n, String type) =>
    switch (type.toLowerCase()) {
      'task' => l10n.taskNotification,
      'chat' => l10n.chatNotification,
      'voice_reply' => l10n.replyNotification,
      'resource' => l10n.resourceNotification,
      'storage' => l10n.storageNotification,
      'join_request' => l10n.joinRequestNotification,
      'team_join' => l10n.teamNotification,
      _ => l10n.notification,
    };

/// The group-header label, delegating to [groupLabelFor]. Differs from
/// [_titleFor]: the grouping labels are collective ("Join Requests",
/// "Tasks"), not the per-notification titles.
String _groupLabel(AppLocalizations l10n, String type) =>
    groupLabelFor(l10n, type);
