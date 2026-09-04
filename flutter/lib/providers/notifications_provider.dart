import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import '../ui/notifications/notification_format.dart';
import '../ui/notifications/notification_grouping.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

/// Port of `ui/notifications/NotificationsViewModel.kt`.
///
/// [NotificationFilter] replaces the Fragment's three-way tab index with a
/// named enum, so the DAO query and the UI cannot disagree about which tab is
/// which.
enum NotificationFilter { all, unread, read }

final notificationFilterProvider = StateProvider<NotificationFilter>(
  (ref) => NotificationFilter.all,
);

final notificationsProvider = StreamProvider<List<NotificationRow>>((ref) {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) return Stream.value(const []);
  final filter = ref.watch(notificationFilterProvider);
  return ref
      .watch(notificationsRepositoryProvider)
      .watch(user.id, filter: filter.name, isAdmin: user.userAdmin);
});

final unreadNotificationCountProvider = StreamProvider<int>((ref) {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) return Stream.value(0);
  return ref
      .watch(notificationsRepositoryProvider)
      .watchUnreadCount(user.id, isAdmin: user.userAdmin);
});

class NotificationActions {
  const NotificationActions(this.ref);

  final Ref ref;

  Future<void> markAsRead(String id) async {
    final userId = ref.read(sessionProvider).valueOrNull?.id;
    await ref
        .read(notificationsRepositoryProvider)
        .markNotificationAsRead(id, userId);
  }

  Future<void> markAllAsRead() async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    await ref.read(notificationsRepositoryProvider).markAllAsRead(user.id);
    // Port of `markAllAsRead`: clearing both override sets collapses every
    // group back to its (now-all-read) default of collapsed.
    ref.read(notificationExpansionProvider.notifier).resetOverrides();
  }

  Future<void> delete(String id) async {
    await ref.read(notificationsRepositoryProvider).delete(id);
  }
}

final notificationActionsProvider = Provider(NotificationActions.new);

/// Holds the manual expand/collapse overrides, porting the
/// `_collapsedGroups`/`_expandedGroups` pair in `NotificationsViewModel`.
/// A group's effective state is the explicit override if present, otherwise
/// the unread-driven default computed in `buildGroupedList`.
class NotificationExpansionState {
  const NotificationExpansionState({
    this.collapsed = const {},
    this.expanded = const {},
  });

  final Set<String> collapsed;
  final Set<String> expanded;
}

/// `toggleGroupExpansion`: if the group is currently expanded (explicitly, or
/// by the unread default) collapse it; otherwise expand it. The current
/// notifications are needed to evaluate the default, so they are passed in.
NotificationExpansionState toggleExpansion(
  NotificationExpansionState state,
  String type,
  List<NotificationRow> notifications,
) {
  final isExpanded = state.expanded.contains(type)
      ? true
      : state.collapsed.contains(type)
      ? false
      : notifications.any((n) => notificationGroupType(n) == type && !n.isRead);
  if (isExpanded) {
    return NotificationExpansionState(
      expanded: state.expanded.where((t) => t != type).toSet(),
      collapsed: {...state.collapsed, type},
    );
  }
  return NotificationExpansionState(
    collapsed: state.collapsed.where((t) => t != type).toSet(),
    expanded: {...state.expanded, type},
  );
}

class NotificationExpansionNotifier
    extends StateNotifier<NotificationExpansionState> {
  NotificationExpansionNotifier() : super(const NotificationExpansionState());

  void toggle(String type, List<NotificationRow> notifications) {
    state = toggleExpansion(state, type, notifications);
  }

  void resetOverrides() {
    state = const NotificationExpansionState();
  }
}

final notificationExpansionProvider =
    StateNotifierProvider<
      NotificationExpansionNotifier,
      NotificationExpansionState
    >((ref) => NotificationExpansionNotifier());

/// Port of `TransactionSyncManager`'s `"notifications"` sync-in direction:
/// pulls server notification documents into the local cache so they surface in
/// the bell list, and so read-state upload (`syncNotificationReads`) has rows
/// with a `rev` to PUT back.
class NotificationsSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(config, void Function(SyncProgress) onProgress) =>
      ref
          .read(notificationsRepositoryProvider)
          .sync(config: config, onProgress: onProgress);
}

final notificationsSyncProvider =
    NotifierProvider<NotificationsSyncNotifier, SyncUiState>(
      NotificationsSyncNotifier.new,
    );

/// The team names and join-request details the notification list formats with
/// — port of the lookup half of `NotificationsViewModel.loadNotifications`.
///
/// Rebuilt whenever the list changes, like the Kotlin, which re-runs the whole
/// lookup on every `loadNotifications` call.
final notificationFormatContextProvider =
    FutureProvider<NotificationFormatContext>((ref) async {
      final rows = await ref.watch(notificationsProvider.future);
      return buildNotificationFormatContext(
        rows,
        ref.watch(notificationsRepositoryProvider),
      );
    });
