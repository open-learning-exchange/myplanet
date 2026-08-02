import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/notifications_provider.dart';

/// Port of `ui/notifications/NotificationsFragment.kt`.
class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final filter = ref.watch(notificationFilterProvider);
    final notifications = ref.watch(notificationsProvider);
    final unread = ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.notifications),
        actions: [
          if (unread > 0)
            TextButton(
              onPressed: () => ref
                  .read(notificationActionsProvider)
                  .markAllAsRead(),
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
              error: (_, _) => Center(
                child: Text(l10n.notificationsUnavailable),
              ),
              data: (items) => items.isEmpty
                  ? _EmptyNotifications(filter: filter)
                  : ListView.builder(
                      padding: const EdgeInsets.only(bottom: 24),
                      itemCount: items.length,
                      itemBuilder: (context, index) => _NotificationTile(
                        notification: items[index],
                      ),
                    ),
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
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Icon(Icons.delete_outline, color: colors.onErrorContainer),
      ),
      child: ListTile(
        leading: Badge(
          isLabelVisible: !notification.isRead,
          child: CircleAvatar(child: Icon(_iconFor(notification.type))),
        ),
        title: Text(
          notification.title?.trim().isNotEmpty == true
              ? notification.title!
              : _titleFor(l10n, notification.type),
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
        onTap: notification.isRead
            ? null
            : () => ref
                  .read(notificationActionsProvider)
                  .markAsRead(notification.id),
      ),
    );
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
