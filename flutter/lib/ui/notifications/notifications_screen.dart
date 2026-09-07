import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/local/app_database.dart';
import '../../l10n/app_localizations.dart';
import '../../providers/app_providers.dart';
import '../../providers/notifications_provider.dart';
import '../../repository/notifications_repository.dart';
import '../components/relative_time.dart';
import 'notification_destination.dart';
import 'notification_format.dart';
import 'notification_grouping.dart';

/// Port of `ui/notifications/NotificationsFragment.kt`.
///
/// Notifications are grouped by type with expandable headers, porting the
/// grouping model added to `NotificationsViewModel` (commit 8f4d06d5d). A
/// group is expanded by default only while it has unread items; tapping a
/// header overrides that, and *Mark all read* collapses every group.
class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() =>
      _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen> {
  /// `NotificationsViewModel._selectedIds` (`:39`). Local widget state rather
  /// than a provider, following the port's own multi-select (Phase 50's
  /// `resources_screen.dart:35`) — and, like the Kotlin, there is no separate
  /// mode flag: `isSelectionMode` is `_selectedIds.isNotEmpty()` (`:44-46`).
  final Set<String> _selectedIds = {};

  bool get _selecting => _selectedIds.isNotEmpty;

  /// `toggleSelection` (`:145-149`).
  void _toggleSelection(String id) {
    setState(() {
      if (!_selectedIds.add(id)) _selectedIds.remove(id);
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final filter = ref.watch(notificationFilterProvider);
    final notifications = ref.watch(notificationsProvider);
    final unread = ref.watch(unreadNotificationCountProvider).valueOrNull ?? 0;
    final expansion = ref.watch(notificationExpansionProvider);

    return Scaffold(
      appBar: AppBar(
        // `ltBulkActionBar` replaces `ltTopBar` outright while selecting
        // (`NotificationsFragment.kt:104-107`). In Kotlin that bar is a
        // LinearLayout above the list holding the count, *Mark read*, *Delete*
        // and a close button; here it is the AppBar, which is where the port
        // already puts *Mark all read* and where Phase 50 put the resource
        // catalog's equivalent.
        leading: _selecting
            ? IconButton(
                icon: const Icon(Icons.close),
                tooltip: l10n.cancelSelection,
                onPressed: () => setState(_selectedIds.clear),
              )
            : null,
        title: Text(
          _selecting
              ? l10n.selectedCount(_selectedIds.length)
              : l10n.notifications,
        ),
        actions: [
          if (_selecting) ...[
            TextButton(
              onPressed: _markSelectedAsRead,
              child: Text(l10n.markSelectedAsRead),
            ),
            // No confirmation, because Kotlin's `btnBulkDelete` has none — and
            // the row's swipe, which does confirm, is the port's own addition
            // rather than a port of anything.
            TextButton(onPressed: _deleteSelected, child: Text(l10n.delete)),
          ]
          // `count > 0 && currentFilter != "read"`
          // (`NotificationsFragment.kt:101-102`) — offering "mark all read" on
          // the Read tab, which the port did, is an action with nothing to act
          // on. It sits inside `ltTopBar`, so selection mode hides it too.
          else if (unread > 0 && filter != NotificationFilter.read)
            TextButton(
              onPressed: () =>
                  ref.read(notificationActionsProvider).markAllAsRead(),
              child: Text(l10n.markAllAsRead),
            ),
        ],
      ),
      body: Column(
        children: [
          // The all/unread/read spinner is inside `ltTopBar` too, so it goes
          // with it. That is not only cosmetic: `loadNotifications` never
          // clears `_selectedIds`, so a filter change during selection would
          // leave ids selected whose rows have left the list — see the quirk
          // reproduced in `_markSelectedAsRead`.
          if (!_selecting)
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
                  : _GroupedList(
                      items: items,
                      expansion: expansion,
                      selectedIds: _selectedIds,
                      selecting: _selecting,
                      onToggleSelection: _toggleSelection,
                    ),
            ),
          ),
        ],
      ),
    );
  }

  /// `markSelectedAsRead` (`:173-191`), which clears the selection only when
  /// the repository reports that something was marked.
  ///
  /// The ids are taken from `_selectedIds` as-is, with no check that each is
  /// still in the visible list. That is Kotlin's behaviour and it is reachable:
  /// a group can be collapsed while its rows are selected (the header stays
  /// selection-unaware and still toggles), so the action can act on rows the
  /// user cannot see. The repository filters out ids that are not in the table
  /// at all, which is a different thing.
  Future<void> _markSelectedAsRead() async {
    final marked = await ref
        .read(notificationActionsProvider)
        .markSelectedAsRead(Set.of(_selectedIds));
    if (!mounted || marked.isEmpty) return;
    setState(_selectedIds.clear);
  }

  /// `deleteSelected` (`:193-205`). A local delete with no tombstone, so a
  /// server-originated notification returns on the next sync — see
  /// `NotificationDao.deleteByIds`.
  Future<void> _deleteSelected() async {
    final deleted = await ref
        .read(notificationActionsProvider)
        .deleteSelected(Set.of(_selectedIds));
    if (!mounted || deleted.isEmpty) return;
    setState(_selectedIds.clear);
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
  const _GroupedList({
    required this.items,
    required this.expansion,
    required this.selectedIds,
    required this.selecting,
    required this.onToggleSelection,
  });

  final List<NotificationRow> items;
  final NotificationExpansionState expansion;
  final Set<String> selectedIds;
  final bool selecting;
  final void Function(String id) onToggleSelection;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final grouped = buildGroupedList(
      items,
      collapsedGroups: expansion.collapsed,
      expandedGroups: expansion.expanded,
    );
    // The team names and join-request details `loadNotifications` gathers
    // before formatting. Watched, not read: while it resolves the rows still
    // render, just without the `<b>Team</b>:` prefix, which is what an
    // uncached row shows in the Kotlin too.
    final formatContext =
        ref.watch(notificationFormatContextProvider).valueOrNull ??
        const NotificationFormatContext.empty();
    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 24),
      itemCount: grouped.length,
      itemBuilder: (context, index) {
        final node = grouped[index];
        return switch (node) {
          NotificationHeaderItem() => _GroupHeader(header: node),
          // Only `NotificationListItem.Item` carries `isSelected`/
          // `isSelectionMode` in the Kotlin model — a header is never
          // selectable, and stays tappable-to-collapse even mid-selection.
          NotificationEntryItem(:final notification) => _NotificationTile(
            notification: notification,
            formatContext: formatContext,
            selected: selectedIds.contains(notification.id),
            selecting: selecting,
            onToggleSelection: onToggleSelection,
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
  const _NotificationTile({
    required this.notification,
    required this.formatContext,
    required this.selected,
    required this.selecting,
    required this.onToggleSelection,
  });
  final NotificationRow notification;
  final NotificationFormatContext formatContext;
  final bool selected;
  final bool selecting;
  final void Function(String id) onToggleSelection;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context);
    final colors = Theme.of(context).colorScheme;
    return Dismissible(
      key: ValueKey(notification.id),
      // Swipe-to-delete is the port's own affordance — Kotlin has no
      // `ItemTouchHelper` anywhere and deletes only through the bulk bar — so
      // it yields to selection mode rather than competing with it. Kotlin
      // disarms the row's long-press there for the same reason: while a
      // selection is open, the row's gestures belong to the selection.
      direction: selecting
          ? DismissDirection.none
          : DismissDirection.endToStart,
      confirmDismiss: (_) => _confirmDelete(context),
      onDismissed: (_) =>
          ref.read(notificationActionsProvider).delete(notification.id),
      background: Container(
        color: colors.errorContainer,
        alignment: AlignmentDirectional.centerEnd,
        padding: const EdgeInsets.symmetric(horizontal: 24),
        child: Icon(Icons.delete_outline, color: colors.onErrorContainer),
      ),
      // `NotificationsAdapter.bind` dims a read row to `alpha = 0.6` rather
      // than un-bolding its text (`:127`), and the formatted text carries its
      // own emphasis — the join-request prefix, the task's team name — which
      // bolding the whole line for unread would swallow.
      child: Opacity(
        opacity: notification.isRead ? 0.6 : 1,
        child: ListTile(
          // The icon is the port's own: Kotlin draws one per *group* header,
          // not per row (`iconResFor` is called only from
          // `HeaderViewHolder.bind`). It stays because it makes a long list
          // scannable — but it is an addition, which is why the type *label*
          // was the thing removed: that duplicated the group header outright.
          // `cbSelect` (`row_notifications.xml:33-42`), visible only in
          // selection mode. It is `clickable="false"` there — the row handles
          // the tap — so this one is not interactive either.
          leading: selecting
              ? Checkbox(value: selected, onChanged: null)
              : Badge(
                  isLabelVisible: !notification.isRead,
                  child: CircleAvatar(
                    child: Icon(
                      _iconFor(resolvedNotificationType(notification)),
                    ),
                  ),
                ),
          // The row's one line of text, as `row_notifications.xml` has it: the
          // rewritten message, not a type label above the raw one. The group
          // header above already names the type, exactly as in the Kotlin — and
          // `AppNotification.title`, which this used to prefer, is a column
          // nothing in either app ever writes.
          title: _FormattedNotificationText(
            formatNotification(
              notification,
              l10n: l10n,
              context: formatContext,
            ),
          ),
          // `NotificationsAdapter.formatRelativeTime` (`:152-163`), not the
          // stored millisecond value formatted. `System.currentTimeMillis()` is
          // read once per bind there and once per build here: neither app runs
          // a ticker, so a row's timestamp text is frozen until it rebuilds.
          subtitle: Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              notificationTimestampLabel(
                l10n,
                createdAtMillis: notification.createdAt,
                now: DateTime.now().millisecondsSinceEpoch,
              ),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          // `btn_mark_as_read`, visible on every unread row outside selection
          // mode (`NotificationsAdapter.kt:137-141`). Without it the port's
          // only way to mark a single row read was to tap it — which also
          // navigates away from the list, so a learner could not clear one
          // notification and keep reading the rest.
          // `binding.btnMarkAsRead.visibility = View.GONE` in the
          // selection branch (`NotificationsAdapter.kt:132`).
          trailing: notification.isRead || selecting
              ? null
              : TextButton(
                  onPressed: () => ref
                      .read(notificationActionsProvider)
                      .markAsRead(notification.id),
                  child: Text(l10n.markAsRead),
                ),
          // A read row keeps its tint, in both modes, so the selection
          // affordance does not fight the read one
          // (`NotificationsAdapter.kt:127` sets `alpha` before the branch).
          selected: selected,
          // In selection mode a tap toggles instead of navigating, and the
          // long-press listener is set to null
          // (`NotificationsAdapter.kt:133-134`); outside it, long-press is how
          // selection starts (`:145-148`).
          //
          // Read notifications remain actionable. Kotlin marks an unread row and
          // navigates on the same tap; making `onTap` null after that first tap
          // prevented learners from ever reopening its destination in Flutter.
          onTap: selecting
              ? () => onToggleSelection(notification.id)
              : () => _openNotification(context, ref),
          // No `isRead` gate: Kotlin arms the long-press on every row, so an
          // already-read notification can be selected (and re-marked, which
          // re-stamps it — its own quirk, reproduced).
          onLongPress: selecting
              ? null
              : () => onToggleSelection(notification.id),
        ),
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
    // The mapping itself lives beside the resolver, so the system-tray tap
    // reaches the same screen this row does — see
    // `notificationDestinationLocation`.
    context.go(notificationDestinationLocation(destination));
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

/// The group-header label, delegating to [groupLabelFor] — collective
/// ("Join Requests", "Tasks"), and now the only place a type is named, since
/// the row itself carries the formatted message.
String _groupLabel(AppLocalizations l10n, String type) =>
    groupLabelFor(l10n, type);

/// Draws a [FormattedNotification]'s runs, with Kotlin's `<b>` as a bold span.
///
/// This is what stands in for the `TextView` the adapter hands
/// `Html.fromHtml`'s `Spanned` to: the markup in these strings is emphasis, so
/// a `Text.rich` reproduces it exactly without an HTML widget.
class _FormattedNotificationText extends StatelessWidget {
  const _FormattedNotificationText(this.formatted);

  final FormattedNotification formatted;

  @override
  Widget build(BuildContext context) {
    if (formatted.spans.length == 1 &&
        !formatted.spans.first.bold &&
        !formatted.spans.first.italic) {
      return Text(formatted.text);
    }
    return Text.rich(
      TextSpan(
        children: [
          for (final span in formatted.spans)
            TextSpan(
              text: span.text,
              style: (span.bold || span.italic)
                  ? TextStyle(
                      fontWeight: span.bold ? FontWeight.bold : null,
                      fontStyle: span.italic ? FontStyle.italic : null,
                    )
                  : null,
            ),
        ],
      ),
    );
  }
}
