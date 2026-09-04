import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/notifications_provider.dart';
import 'package:myplanet/ui/notifications/notifications_screen.dart';

import '../support/widget_harness.dart';

void main() {
  // The row's icon and title read the **resolved** type, like every other
  // reader. A server `replyMessage` document — outside KNOWN_TYPES, reaching
  // `voice_reply` only through its message — is the case that shows it: against
  // the raw type it takes the default bell and the generic "Notification".
  testWidgets('a server notification gets the icon and title of its resolved '
      'type', (tester) async {
    final row = NotificationRow(
      id: 'reply-1',
      userId: 'user-1',
      message: 'bob replied to your voice',
      type: 'replyMessage',
      relatedId: 'news-3',
      isRead: false,
      createdAt: DateTime(2026, 8, 2, 12).millisecondsSinceEpoch,
      priority: 0,
      isFromServer: true,
      needsSync: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith((ref) => Stream.value([row])),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('New reply'), findsOneWidget);
    expect(find.text('Notification'), findsNothing);
    expect(find.byIcon(Icons.chat_bubble_outline), findsNWidgets(2));
    expect(find.byIcon(Icons.notifications_outlined), findsNothing);
  });

  testWidgets('renders grouped unread notification with header and content', (
    tester,
  ) async {
    final row = NotificationRow(
      id: 'resource-1',
      userId: 'user-1',
      message: '4 new resources are available',
      type: 'resource',
      isRead: false,
      createdAt: DateTime(2026, 8, 2, 12).millisecondsSinceEpoch,
      priority: 0,
      isFromServer: false,
      needsSync: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith((ref) => Stream.value([row])),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Notifications'), findsOneWidget);
    expect(find.text('Unread (1)'), findsOneWidget);
    expect(find.text('Mark all read'), findsOneWidget);
    // The unread resource group is expanded by default, so its header and the
    // notification content are both present.
    expect(find.text('4 new resources are available'), findsOneWidget);
    expect(find.byIcon(Icons.folder_outlined), findsNWidgets(2));
    // The expanded group shows an up arrow.
    expect(find.byIcon(Icons.keyboard_arrow_up), findsOneWidget);
  });

  testWidgets('a group with only read notifications is collapsed by default', (
    tester,
  ) async {
    final row = NotificationRow(
      id: 'resource-1',
      userId: 'user-1',
      message: '4 new resources are available',
      type: 'resource',
      isRead: true,
      createdAt: DateTime(2026, 8, 2, 12).millisecondsSinceEpoch,
      priority: 0,
      isFromServer: false,
      needsSync: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith((ref) => Stream.value([row])),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The collapsed group header is present, but its item is hidden.
    expect(find.byIcon(Icons.keyboard_arrow_down), findsOneWidget);
    expect(find.text('4 new resources are available'), findsNothing);
  });

  testWidgets('tapping a collapsed group header expands it', (tester) async {
    final row = NotificationRow(
      id: 'resource-1',
      userId: 'user-1',
      message: '4 new resources are available',
      type: 'resource',
      isRead: true,
      createdAt: DateTime(2026, 8, 2, 12).millisecondsSinceEpoch,
      priority: 0,
      isFromServer: false,
      needsSync: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith((ref) => Stream.value([row])),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('4 new resources are available'), findsNothing);
    await tester.tap(find.byIcon(Icons.keyboard_arrow_down));
    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.keyboard_arrow_up), findsOneWidget);
    expect(find.text('4 new resources are available'), findsOneWidget);
  });

  testWidgets('renders the filter-specific empty state', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationFilterProvider.overrideWith(
            (ref) => NotificationFilter.unread,
          ),
          notificationsProvider.overrideWith((ref) => Stream.value(const [])),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No unread notifications'), findsOneWidget);
  });
}
