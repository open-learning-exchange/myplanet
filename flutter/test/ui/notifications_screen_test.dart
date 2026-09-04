import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
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

  // The whole fill chain, with nothing overridden between the screen and the
  // database: `notificationFormatContextProvider` → the repository lookups →
  // the DAOs → the row. Every other test here supplies the context directly,
  // so each half was covered and the join between them was not.
  testWidgets('the team prefix is resolved from the database, end to end', (
    tester,
  ) async {
    final database = AppDatabase.memory();
    addTearDown(database.close);
    await database.teamDao.upsert(
      TeamsCompanion.insert(id: 'team-1', name: const Value('Reading Club')),
    );
    await database.teamTaskDao.upsert(
      TeamTasksCompanion.insert(
        id: 'task-9',
        teamId: 'team-1',
        title: const Value('Read chapter 3'),
      ),
    );

    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          appDatabaseProvider.overrideWithValue(database),
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
  });

  testWidgets('an unread row is not dimmed', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'unread-1', message: '7', type: 'resource'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    final opacity = tester.widget<Opacity>(
      find.ancestor(
        of: find.text('You have 7 resources not downloaded'),
        matching: find.byType(Opacity),
      ),
    );
    expect(opacity.opacity, 1);
  });

  testWidgets('an unread row offers Mark as read without navigating', (
    tester,
  ) async {
    // `btn_mark_as_read` (`NotificationsAdapter.kt:137-141`). The port had no
    // per-row action at all, so the only way to mark one notification read was
    // to tap it — which navigates away from the list.
    final read = <String>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'unread-1', message: '7', type: 'resource'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
          notificationActionsProvider.overrideWithValue(
            _RecordingActions(onRead: read.add),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Mark as read'));
    await tester.pump();
    expect(read, ['unread-1']);
  });

  testWidgets('a read row offers no Mark as read button', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'read-1', message: '7', type: 'resource', isRead: true),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
          notificationExpansionProvider.overrideWith(
            (ref) =>
                NotificationExpansionNotifier()..toggle('resource', const []),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('You have 7 resources not downloaded'), findsOneWidget);
    expect(find.text('Mark as read'), findsNothing);
  });

  testWidgets('Mark all read is hidden on the Read tab', (tester) async {
    // `count > 0 && currentFilter != "read"` (`NotificationsFragment.kt:101`).
    final markedAll = <bool>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationFilterProvider.overrideWith(
            (ref) => NotificationFilter.read,
          ),
          notificationsProvider.overrideWith((ref) => Stream.value(const [])),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(3),
          ),
          notificationActionsProvider.overrideWithValue(
            _RecordingActions(onMarkAll: () => markedAll.add(true)),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Mark all read'), findsNothing);
  });

  testWidgets('Mark all read fires on a tab that has unread rows', (
    tester,
  ) async {
    final markedAll = <bool>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'unread-1', message: '7', type: 'resource'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
          notificationActionsProvider.overrideWithValue(
            _RecordingActions(onMarkAll: () => markedAll.add(true)),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Mark all read'));
    await tester.pump();
    expect(markedAll, [true]);
  });

  testWidgets('swiping a row deletes it', (tester) async {
    final deleted = <String>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'unread-1', message: '7', type: 'resource'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
          notificationActionsProvider.overrideWithValue(
            _RecordingActions(onDelete: deleted.add),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.drag(
      find.text('You have 7 resources not downloaded'),
      const Offset(-500, 0),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();

    expect(deleted, ['unread-1']);
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

/// Records the row actions instead of touching a repository, so a tap on
/// *Mark as read*, *Mark all read* or a swipe is observable.
class _RecordingActions implements NotificationActions {
  _RecordingActions({this.onRead, this.onDelete, this.onMarkAll});

  final void Function(String id)? onRead;
  final void Function(String id)? onDelete;
  final void Function()? onMarkAll;

  @override
  Ref get ref => throw UnsupportedError('the fake talks to no providers');

  @override
  Future<void> markAsRead(String id) async => onRead?.call(id);

  @override
  Future<void> delete(String id) async => onDelete?.call(id);

  @override
  Future<void> markAllAsRead() async => onMarkAll?.call();
}
