import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';

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
      .watch(user.id, filter: filter.name);
});

final unreadNotificationCountProvider = StreamProvider<int>((ref) {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) return Stream.value(0);
  return ref.watch(notificationsRepositoryProvider).watchUnreadCount(user.id);
});

class NotificationActions {
  const NotificationActions(this.ref);

  final Ref ref;

  Future<void> markAsRead(String id) async {
    await ref.read(notificationsRepositoryProvider).markAsRead([id]);
  }

  Future<void> markAllAsRead() async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    await ref.read(notificationsRepositoryProvider).markAllAsRead(user.id);
  }

  Future<void> delete(String id) async {
    await ref.read(notificationsRepositoryProvider).delete(id);
  }
}

final notificationActionsProvider = Provider(NotificationActions.new);
