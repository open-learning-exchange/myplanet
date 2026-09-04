import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/notifications_provider.dart';
import 'package:myplanet/ui/notifications/notification_format.dart';
import 'package:myplanet/ui/notifications/notifications_screen.dart';

import '../support/widget_harness.dart';

void main() {
  // The row's icon and the group it lands in read the **resolved** type, like
  // every other reader. A server `replyMessage` document — outside KNOWN_TYPES,
  // reaching `voice_reply` only through its message — is the case that shows
  // it: against the raw type it takes the default bell and the "Other" group.
  //
  // The row itself no longer carries a type label: Kotlin's row is one line of
  // formatted text (`row_notifications.xml`), and the group header above it is
  // where the type is named.
  testWidgets('a server notification gets the icon and group of its resolved '
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

    expect(find.text('Voice Replies'), findsOneWidget);
    expect(find.text('Other'), findsNothing);
    // The server's own sentence is the row's text, unrewritten — the
    // `else -> notification.message` arm of `formatNotification`.
    expect(find.text('bob replied to your voice'), findsOneWidget);
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

  // The defect this phase exists for. `updateResourceNotification` stores the
  // count and nothing else, and the row drew `message` verbatim — so the bell
  // showed a learner the single character `7`.
  testWidgets('a resource notification reads as a sentence, not a bare digit', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(
                id: 'user-1:resource:count',
                message: '7',
                type: 'resource',
                relatedId: '7',
              ),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('You have 7 resources not downloaded'), findsOneWidget);
    expect(find.text('7'), findsNothing);
  });

  testWidgets('a storage warning reads as a sentence with one space', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(
                id: 'user-1:storage',
                message: '8%',
                type: 'storage',
                relatedId: 'storage',
              ),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // Kotlin composes a double space here and `Html.fromHtml` collapses it.
    expect(find.text('Storage running low: 8%'), findsOneWidget);
    expect(find.text('8%'), findsNothing);
  });

  testWidgets("a server message's markup is emphasis, not visible tags", (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(
                id: 'srv-1',
                message:
                    '<b>Jane</b> has requested to join <b>"My Team"</b> team.',
                type: 'team',
                subType: 'join_request',
                relatedId: 'team-1',
                isFromServer: true,
              ),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    final text = tester.widget<Text>(
      find.byWidgetPredicate(
        (widget) =>
            widget is Text &&
            (widget.textSpan?.toPlainText() ?? '').startsWith('Jane'),
      ),
    );
    expect(
      text.textSpan!.toPlainText(),
      'Jane has requested to join "My Team" team.',
    );
    final runs = (text.textSpan! as TextSpan).children!.cast<TextSpan>();
    expect(runs.first.text, 'Jane');
    expect(runs.first.style?.fontWeight, FontWeight.bold);
    expect(runs[1].style?.fontWeight, isNull);
  });

  testWidgets('a task notification carries its team name in bold', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(
                id: 'task-notif',
                message: 'Read chapter 3 Thu 12, August 2027',
                type: 'task',
                relatedId: 'task-9',
              ),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
          notificationFormatContextProvider.overrideWith(
            (ref) async => const NotificationFormatContext(
              taskTeamNames: {'task-9': 'Reading Club'},
              joinRequestDetails: {},
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    final text = tester.widget<Text>(
      find.byWidgetPredicate(
        (widget) =>
            widget is Text &&
            (widget.textSpan?.toPlainText() ?? '').startsWith('Reading Club'),
      ),
    );
    expect(
      text.textSpan!.toPlainText(),
      'Reading Club: Read chapter 3 is due in Thu 12, August 2027',
    );
    final runs = (text.textSpan! as TextSpan).children!.cast<TextSpan>();
    expect(runs.first.style?.fontWeight, FontWeight.bold);
    // The colon belongs to the unbolded run, as in the Kotlin.
    expect(runs[1].text, startsWith(': '));
  });

  testWidgets('a read row is dimmed rather than un-bolded', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(
                id: 'read-1',
                message: 'Something happened',
                type: 'somethingElse',
                isRead: true,
              ),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
          // A read-only group is collapsed by default, so open it.
          notificationExpansionProvider.overrideWith(
            (ref) =>
                NotificationExpansionNotifier()
                  ..toggle('notification', const []),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // `binding.root.alpha = if (notification.isRead) 0.6f else 1.0f`.
    final opacity = tester.widget<Opacity>(
      find.ancestor(
        of: find.text('Something happened'),
        matching: find.byType(Opacity),
      ),
    );
    expect(opacity.opacity, 0.6);
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

NotificationRow _row({
  required String id,
  required String message,
  required String type,
  String? subType,
  String? relatedId,
  bool isRead = false,
  bool isFromServer = false,
}) => NotificationRow(
  id: id,
  userId: 'user-1',
  message: message,
  type: type,
  subType: subType,
  relatedId: relatedId,
  isRead: isRead,
  createdAt: DateTime(2026, 8, 2, 12).millisecondsSinceEpoch,
  priority: 0,
  isFromServer: isFromServer,
  needsSync: false,
);
