import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/notifications_provider.dart';
import 'package:myplanet/ui/notifications/notifications_screen.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('renders unread notification content and type presentation', (
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
    expect(find.text('Resources'), findsOneWidget);
    expect(find.text('4 new resources are available'), findsOneWidget);
    expect(find.byIcon(Icons.folder_outlined), findsOneWidget);
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
