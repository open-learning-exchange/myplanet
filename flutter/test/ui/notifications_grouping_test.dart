import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/notifications_provider.dart';
import 'package:myplanet/ui/notifications/notification_grouping.dart';

NotificationRow _notification({
  required String id,
  required String type,
  bool isRead = false,
}) => NotificationRow(
  id: id,
  userId: 'user-1',
  message: 'message $id',
  type: type,
  isRead: isRead,
  createdAt: 0,
  priority: 0,
  isFromServer: false,
  needsSync: false,
);

NotificationHeaderItem _header(List<NotificationListItem> list, String type) =>
    list.whereType<NotificationHeaderItem>().firstWhere((h) => h.type == type);

void main() {
  group('buildGroupedList', () {
    test('a group is expanded by default only when it has unread items', () {
      final items = [
        _notification(id: '1', type: 'task', isRead: false),
        _notification(id: '2', type: 'resource', isRead: true),
      ];
      final list = buildGroupedList(
        items,
        collapsedGroups: const {},
        expandedGroups: const {},
      );

      expect(_header(list, 'task').isExpanded, isTrue);
      expect(_header(list, 'resource').isExpanded, isFalse);
    });

    test('an override flips the default in either direction', () {
      final items = [
        _notification(id: '1', type: 'task', isRead: false),
        _notification(id: '2', type: 'resource', isRead: true),
      ];
      // Manually collapse the unread task group and expand the read resource
      // group — the opposite of their defaults.
      final list = buildGroupedList(
        items,
        collapsedGroups: const {'task'},
        expandedGroups: const {'resource'},
      );

      expect(_header(list, 'task').isExpanded, isFalse);
      expect(_header(list, 'resource').isExpanded, isTrue);
    });

    test('the header unread count reflects only unread items', () {
      final items = [
        _notification(id: '1', type: 'task', isRead: false),
        _notification(id: '2', type: 'task', isRead: true),
        _notification(id: '3', type: 'task', isRead: false),
      ];
      final list = buildGroupedList(
        items,
        collapsedGroups: const {},
        expandedGroups: const {},
      );

      expect(_header(list, 'task').unreadCount, 2);
    });

    test('unrecognized types collapse into a single Other group', () {
      final items = [
        _notification(id: '1', type: 'weird', isRead: false),
        _notification(id: '2', type: 'other', isRead: false),
      ];
      final list = buildGroupedList(
        items,
        collapsedGroups: const {},
        expandedGroups: const {},
      );

      final headers = list.whereType<NotificationHeaderItem>().toList();
      expect(headers, hasLength(1));
      expect(headers.single.type, 'notification');
      expect(groupLabelKey(headers.single.type), 'notificationGroupOther');
    });

    test('groups follow the Kotlin type order, known types first', () {
      final items = [
        _notification(id: '1', type: 'resource', isRead: false),
        _notification(id: '2', type: 'task', isRead: false),
        _notification(id: '3', type: 'join_request', isRead: false),
      ];
      final list = buildGroupedList(
        items,
        collapsedGroups: const {},
        expandedGroups: const {},
      );

      final types = list.whereType<NotificationHeaderItem>().map((h) => h.type);
      expect(types, ['join_request', 'task', 'resource']);
    });

    test('a collapsed group hides its items', () {
      final items = [_notification(id: '1', type: 'task', isRead: false)];
      final list = buildGroupedList(
        items,
        collapsedGroups: const {'task'},
        expandedGroups: const {},
      );

      expect(list.whereType<NotificationEntryItem>(), isEmpty);
    });
  });

  group('toggleExpansion', () {
    test('toggling an expanded-by-default group collapses it', () {
      final items = [_notification(id: '1', type: 'task', isRead: false)];
      const state = NotificationExpansionState();
      final next = toggleExpansion(state, 'task', items);

      expect(next.collapsed, {'task'});
      expect(next.expanded, isEmpty);
    });

    test('toggling a collapsed-by-default group expands it', () {
      final items = [_notification(id: '1', type: 'resource', isRead: true)];
      const state = NotificationExpansionState();
      final next = toggleExpansion(state, 'resource', items);

      expect(next.expanded, {'resource'});
      expect(next.collapsed, isEmpty);
    });

    test('toggling twice restores the default expansion state', () {
      final items = [_notification(id: '1', type: 'task', isRead: false)];
      final defaultExpanded = _header(
        buildGroupedList(
          items,
          collapsedGroups: const {},
          expandedGroups: const {},
        ),
        'task',
      ).isExpanded;
      var state = const NotificationExpansionState();
      state = toggleExpansion(state, 'task', items);
      state = toggleExpansion(state, 'task', items);

      // The sets need not be empty — Kotlin keeps the type in `expanded` after
      // the second toggle — but the *effective* state is the default again.
      final restoredExpanded = _header(
        buildGroupedList(
          items,
          collapsedGroups: state.collapsed,
          expandedGroups: state.expanded,
        ),
        'task',
      ).isExpanded;
      expect(restoredExpanded, defaultExpanded);
    });
  });

  group('NotificationExpansionNotifier', () {
    test('resetOverrides clears both override sets', () {
      final notifier = NotificationExpansionNotifier();
      final items = [_notification(id: '1', type: 'task', isRead: false)];
      notifier.toggle('task', items);
      expect(notifier.state.collapsed, {'task'});

      notifier.resetOverrides();
      expect(notifier.state.collapsed, isEmpty);
      expect(notifier.state.expanded, isEmpty);
    });
  });
}
