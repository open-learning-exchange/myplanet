import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
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
    expect(find.text('Mark all as read'), findsOneWidget);
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

    expect(find.text('Mark all as read'), findsNothing);
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

    await tester.tap(find.text('Mark all as read'));
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
  // ------------------------------------------------------------------
  // The row's timestamp (Phase 127 item 1).
  //
  // `NotificationsAdapter.formatRelativeTime` (`:152-163`) transforms
  // `createdAt` into one of five relative strings, or an absolute `MMM d, yyyy`
  // beyond a week. The port drew `DateFormat.yMMMd().add_jm()` for every row —
  // the same class of defect as the bare `7` the phase before this one fixed: a
  // stored value drawn where Kotlin transforms it.
  //
  // Both offsets below are computed from the wall clock so the bucket is fixed
  // whatever day the suite runs on; the *formatter's* own boundaries are pinned
  // exhaustively against a fixed instant in
  // `test/ui/notification_timestamp_test.dart`.
  // ------------------------------------------------------------------

  testWidgets('a recent row reads as a relative time, not an absolute date', (
    tester,
  ) async {
    final createdAt = DateTime.now()
        .subtract(const Duration(minutes: 5))
        .millisecondsSinceEpoch;
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              NotificationRow(
                id: 'reply-1',
                userId: 'user-1',
                message: 'bob replied to your voice',
                type: 'replyMessage',
                isRead: false,
                createdAt: createdAt,
                priority: 0,
                isFromServer: true,
                needsSync: false,
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

    expect(find.text('5 min ago'), findsOneWidget);
    // The absolute rendering the row used to draw, with its time of day, is
    // gone for anything inside the week.
    expect(
      find.textContaining(
        DateFormat.yMMMd().format(
          DateTime.fromMillisecondsSinceEpoch(createdAt),
        ),
      ),
      findsNothing,
    );
  });

  testWidgets('a row older than a week reads as a date with no time of day', (
    tester,
  ) async {
    final createdAt = DateTime.now()
        .subtract(const Duration(days: 30))
        .millisecondsSinceEpoch;
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              NotificationRow(
                id: 'reply-1',
                userId: 'user-1',
                message: 'bob replied to your voice',
                type: 'replyMessage',
                isRead: false,
                createdAt: createdAt,
                priority: 0,
                isFromServer: true,
                needsSync: false,
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

    final created = DateTime.fromMillisecondsSinceEpoch(createdAt);
    expect(
      find.text(DateFormat('MMM d, yyyy').format(created)),
      findsOneWidget,
    );
    // `getDateFormatter()` is `MMM d, yyyy` and nothing else — the port's
    // `add_jm()` appended a clock time the Kotlin never shows.
    expect(
      find.text(DateFormat.yMMMd().add_jm().format(created)),
      findsNothing,
    );
  });
  // ------------------------------------------------------------------
  // Selection mode (Phase 127 item 2).
  //
  // `NotificationsAdapter.bind`'s `isSelectionMode` branch (`:129-149`),
  // `_selectedIds` and the three actions over it in `NotificationsViewModel`
  // (`:39,145-153,173-205`), and the bulk bar that replaces the top bar
  // (`NotificationsFragment.kt:104-107`, `fragment_notifications.xml:44-93`).
  //
  // Kotlin has exactly one test for any of this
  // (`NotificationsViewModelTest.kt:216-233`, the toggle), so there is little to
  // mirror; everything below is read from the Kotlin source, with the quirks it
  // has rather than the ones it ought to.
  // ------------------------------------------------------------------

  /// Long-presses the one row on screen, which is the only way into selection
  /// mode in either app.
  Future<void> enterSelection(WidgetTester tester, String message) async {
    await tester.longPress(find.text(message));
    await tester.pumpAndSettle();
  }

  testWidgets('a long press enters selection mode and swaps the top bar', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'n-1', message: 'first', type: 'replyMessage'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // Before: the title, the filter spinner's segments, Mark all read; no
    // checkbox.
    expect(find.text('Notifications'), findsOneWidget);
    expect(find.text('Mark all as read'), findsOneWidget);
    expect(find.text('Unread (1)'), findsOneWidget);
    expect(find.byType(Checkbox), findsNothing);

    await enterSelection(tester, 'first');

    // After: `tvSelectedCount`, `btnBulkMarkAsRead`, `btnBulkDelete`,
    // `btnCancelSelection` — and `ltTopBar` gone, which takes the filter and
    // Mark-all with it.
    expect(find.text('1 selected'), findsOneWidget);
    expect(find.text('Mark read'), findsOneWidget);
    expect(find.text('Delete'), findsOneWidget);
    expect(find.byIcon(Icons.close), findsOneWidget);
    expect(find.byType(Checkbox), findsOneWidget);
    expect(find.text('Notifications'), findsNothing);
    expect(find.text('Mark all as read'), findsNothing);
    expect(find.text('Unread (1)'), findsNothing);
    // The row's own Mark-as-read button is hidden in the selection branch.
    expect(find.text('Mark as read'), findsNothing);
  });

  testWidgets('a tap toggles while selecting, and never navigates', (
    tester,
  ) async {
    final read = <String>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'n-1', message: 'first', type: 'replyMessage'),
              _row(id: 'n-2', message: 'second', type: 'replyMessage'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(2),
          ),
          notificationActionsProvider.overrideWithValue(
            _RecordingActions(onRead: read.add),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await enterSelection(tester, 'first');
    await tester.tap(find.text('second'));
    await tester.pumpAndSettle();
    expect(find.text('2 selected'), findsOneWidget);

    // Tapping a selected row deselects it, and the *tap* never marks anything
    // read — `onNotificationClick` is not wired in the selection branch.
    await tester.tap(find.text('second'));
    await tester.pumpAndSettle();
    expect(find.text('1 selected'), findsOneWidget);
    expect(read, isEmpty);

    // Deselecting the last row leaves selection mode: there is no separate
    // mode flag in either app, only `_selectedIds.isNotEmpty()`.
    await tester.tap(find.text('first'));
    await tester.pumpAndSettle();
    expect(find.text('Notifications'), findsOneWidget);
    expect(find.byType(Checkbox), findsNothing);
    expect(read, isEmpty);
  });

  testWidgets('the close button clears the selection', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'n-1', message: 'first', type: 'replyMessage'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await enterSelection(tester, 'first');
    await tester.tap(find.byIcon(Icons.close));
    await tester.pumpAndSettle();

    expect(find.text('Notifications'), findsOneWidget);
    expect(find.text('1 selected'), findsNothing);
  });

  testWidgets('Mark read acts on the whole selection and then clears it', (
    tester,
  ) async {
    final marked = <Set<String>>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'n-1', message: 'first', type: 'replyMessage'),
              _row(id: 'n-2', message: 'second', type: 'replyMessage'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(2),
          ),
          notificationActionsProvider.overrideWithValue(
            _RecordingActions(onMarkSelected: marked.add),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await enterSelection(tester, 'first');
    await tester.tap(find.text('second'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Mark read'));
    await tester.pumpAndSettle();

    expect(marked, [
      {'n-1', 'n-2'},
    ]);
    expect(find.text('Notifications'), findsOneWidget);
  });

  testWidgets('Delete acts on the whole selection, with no confirmation', (
    tester,
  ) async {
    // Kotlin's `btnBulkDelete` goes straight to `deleteSelected`. The row's
    // swipe *does* confirm, but that swipe is the port's own affordance and
    // Kotlin has no equivalent to copy a dialog from.
    final deleted = <Set<String>>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'n-1', message: 'first', type: 'replyMessage'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
          notificationActionsProvider.overrideWithValue(
            _RecordingActions(onDeleteSelected: deleted.add),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await enterSelection(tester, 'first');
    await tester.tap(find.text('Delete'));
    await tester.pumpAndSettle();

    expect(deleted, [
      {'n-1'},
    ]);
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.text('Notifications'), findsOneWidget);
  });

  testWidgets('an already-read row can be selected', (tester) async {
    // Kotlin arms the long-press on every row, read or not
    // (`NotificationsAdapter.kt:145`), and `markNotificationsAsRead` does not
    // filter by `isRead`. Gating selection on unread would be an improvement,
    // not a port.
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              // The group is resolved from the *message* for a
              // `replyMessage` row (`resolvedNotificationType` sniffs it), so
              // this reads as a reply rather than landing in "Other".
              _row(
                id: 'n-1',
                message: 'bob replied to your voice',
                type: 'replyMessage',
                isRead: true,
              ),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(0),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
    // The read group is collapsed by default, so open it first.
    await tester.tap(find.text('Voice Replies'));
    await tester.pumpAndSettle();

    await enterSelection(tester, 'bob replied to your voice');

    expect(find.text('1 selected'), findsOneWidget);
    expect(find.byType(Checkbox), findsOneWidget);
  });

  testWidgets('a group header still collapses while a selection is open', (
    tester,
  ) async {
    // The header is not selection-aware — `NotificationListItem.Header` carries
    // neither `isSelected` nor `isSelectionMode`, and `HeaderViewHolder.bind`
    // wires only the expansion toggle. So a selected row can be collapsed out
    // of sight while the bulk bar still counts it, and the bulk action still
    // acts on it. Reproduced deliberately; it is how the Kotlin behaves.
    final marked = <Set<String>>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(
                id: 'n-1',
                message: 'bob replied to your voice',
                type: 'replyMessage',
              ),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
          notificationActionsProvider.overrideWithValue(
            _RecordingActions(onMarkSelected: marked.add),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await enterSelection(tester, 'bob replied to your voice');
    await tester.tap(find.text('Voice Replies'));
    await tester.pumpAndSettle();

    expect(
      find.text('bob replied to your voice'),
      findsNothing,
      reason: 'collapsed out of sight',
    );
    expect(find.text('1 selected'), findsOneWidget);

    await tester.tap(find.text('Mark read'));
    await tester.pumpAndSettle();
    expect(marked, [
      {'n-1'},
    ]);
  });

  testWidgets('the checkbox is decoration, and the row owns the tap', (
    tester,
  ) async {
    // `cbSelect` is `clickable="false"` `focusable="false"`
    // (`row_notifications.xml:41-42`) — the row's own click listener is what
    // toggles. A Checkbox with a non-null `onChanged` would *absorb* the tap
    // and do nothing with it, giving the one control that looks most tappable
    // in selection mode no effect at all.
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'n-1', message: 'first', type: 'replyMessage'),
            ]),
          ),
          unreadNotificationCountProvider.overrideWith(
            (ref) => Stream.value(1),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await enterSelection(tester, 'first');
    await tester.tap(find.byType(Checkbox));
    await tester.pumpAndSettle();

    // The tap reached the row, which deselected the last item and left
    // selection mode.
    expect(find.text('Notifications'), findsOneWidget);
    expect(find.byType(Checkbox), findsNothing);
  });

  testWidgets('swiping is disabled while selecting', (tester) async {
    // The swipe is the port's own, and it yields rather than competing: Kotlin
    // disarms the row's long-press in selection mode for the same reason.
    final deleted = <String>[];
    await tester.pumpWidget(
      wrapScreen(
        const NotificationsScreen(),
        overrides: [
          notificationsProvider.overrideWith(
            (ref) => Stream.value([
              _row(id: 'n-1', message: 'first', type: 'replyMessage'),
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

    // Swipe outside selection mode: the confirmation dialog opens. This is the
    // control, and it is the whole point of the test below — `deleted` being
    // empty proves nothing on its own, because `confirmDismiss` returning false
    // also leaves it empty. The *dialog* is what distinguishes "the swipe was
    // never armed" from "the swipe was armed and then declined".
    await tester.drag(find.text('first'), const Offset(-500, 0));
    await tester.pumpAndSettle();
    expect(find.byType(AlertDialog), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(deleted, isEmpty);

    await enterSelection(tester, 'first');
    await tester.drag(find.text('first'), const Offset(-500, 0));
    await tester.pumpAndSettle();

    // Armed, the swipe would reach `confirmDismiss` and put this dialog up
    // over a selection the user is in the middle of making.
    expect(find.byType(AlertDialog), findsNothing);
    expect(deleted, isEmpty);
    expect(find.text('1 selected'), findsOneWidget);
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
  _RecordingActions({
    this.onRead,
    this.onDelete,
    this.onMarkAll,
    this.onMarkSelected,
    this.onDeleteSelected,
  });

  final void Function(String id)? onRead;
  final void Function(String id)? onDelete;
  final void Function()? onMarkAll;
  final void Function(Set<String> ids)? onMarkSelected;
  final void Function(Set<String> ids)? onDeleteSelected;

  @override
  Ref get ref => throw UnsupportedError('the fake talks to no providers');

  @override
  Future<void> markAsRead(String id) async => onRead?.call(id);

  @override
  Future<void> delete(String id) async => onDelete?.call(id);

  @override
  Future<void> markAllAsRead() async => onMarkAll?.call();

  /// Reports every id back as acted on, which is what the repository does for
  /// ids that exist — the screen clears its selection only on a non-empty
  /// result.
  @override
  Future<Set<String>> markSelectedAsRead(Set<String> ids) async {
    onMarkSelected?.call(ids);
    return ids;
  }

  @override
  Future<Set<String>> deleteSelected(Set<String> ids) async {
    onDeleteSelected?.call(ids);
    return ids;
  }
}
