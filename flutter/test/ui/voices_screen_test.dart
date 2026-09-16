import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/ui/voices/voices_screen.dart';

import '../support/widget_harness.dart';

void main() {
  NewsRow post({
    required String id,
    String? message,
    String? userName,
    String? userId,
    String? docId,
    bool isEdited = false,
    List<String> labels = const [],
  }) => NewsRow(
    id: id,
    docId: docId,
    message: message,
    userName: userName,
    userId: userId,
    time: 1735689600000,
    updatedDate: 0,
    imageUrls: const [],
    labels: labels,
    newsCreatedDate: 0,
    newsUpdatedDate: 0,
    chat: false,
    isEdited: isEdited,
    editedTime: 0,
  );

  testWidgets('renders the community feed', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const VoicesScreen(),
        overrides: [
          communityFeedProvider.overrideWith(
            (ref) => Stream.value([
              post(
                id: 'n1',
                message: 'Water pump is fixed',
                userName: 'Ada',
                docId: 'server-1',
                labels: const ['announcement'],
              ),
            ]),
          ),
          voiceReplyCountProvider('n1').overrideWith((ref) async => 3),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Water pump is fixed'), findsOneWidget);
    expect(find.text('Ada'), findsOneWidget);
    expect(find.text('announcement'), findsOneWidget);
    expect(find.text('3 replies'), findsOneWidget);
  });

  testWidgets('marks a post that has not reached the server', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const VoicesScreen(),
        overrides: [
          communityFeedProvider.overrideWith(
            (ref) => Stream.value([
              post(id: 'n1', message: 'Queued offline', userName: 'Ada'),
            ]),
          ),
          voiceReplyCountProvider('n1').overrideWith((ref) async => 0),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The upload badge is the only signal that a post is still local.
    expect(find.byIcon(Icons.cloud_upload_outlined), findsOneWidget);
    expect(find.text('No replies'), findsOneWidget);
  });

  testWidgets('shows the empty state', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const VoicesScreen(),
        overrides: [
          communityFeedProvider.overrideWith((ref) => Stream.value(const [])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No voices yet'), findsOneWidget);
  });

  /// `VoicesAdapter.matchesCurrentUser` (`VoicesAdapter.kt:666-669`) is
  /// `id == currentUser?._id || id == currentUser?.id` — an **or** over both
  /// id columns, the same rule as `UserDao.getById`'s
  /// `WHERE id = :id OR _id = :id`, and for the same reason Phase 107
  /// established: a member registered on this device authors rows under the
  /// locally-minted `'<millis>'` key and gains a `couchId` only when the
  /// upload lands. The port preferred one column over the other, so the
  /// account's own earlier posts stopped being its own the moment it uploaded.
  group('author actions', () {
    UserRow user({required String id, String? couchId}) => UserRow(
      id: id,
      couchId: couchId,
      name: 'ada',
      rolesList: const ['learner'],
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );

    Future<void> pumpFeed(
      WidgetTester tester,
      UserRow session,
      String? author,
    ) async {
      await tester.pumpWidget(
        wrapScreen(
          const VoicesScreen(),
          overrides: [
            sessionProvider.overrideWith(() => _TestSessionNotifier(session)),
            communityFeedProvider.overrideWith(
              (ref) => Stream.value([
                post(id: 'n1', message: 'Water pump is fixed', userId: author),
              ]),
            ),
            voiceReplyCountProvider('n1').overrideWith((ref) async => 0),
          ],
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('a post keyed on the local id is still the author\'s once '
        'the account has uploaded', (tester) async {
      // The row was authored before the upload landed, so it carries the
      // local key; the session now also carries the server id.
      await pumpFeed(
        tester,
        user(id: '1700000000000', couchId: 'org.couchdb.user:ada'),
        '1700000000000',
      );

      expect(find.byTooltip('Edit'), findsOneWidget);
    });

    testWidgets('a post keyed on the couch id is the author\'s too', (
      tester,
    ) async {
      await pumpFeed(
        tester,
        user(id: '1700000000000', couchId: 'org.couchdb.user:ada'),
        'org.couchdb.user:ada',
      );

      expect(find.byTooltip('Edit'), findsOneWidget);
    });

    testWidgets('someone else\'s post offers no author actions', (
      tester,
    ) async {
      await pumpFeed(
        tester,
        user(id: '1700000000000', couchId: 'org.couchdb.user:ada'),
        'org.couchdb.user:zoe',
      );

      expect(find.byTooltip('Edit'), findsNothing);
    });

    /// `matchesCurrentUser` returns false for a null or empty id before it
    /// compares anything (`:667`) — otherwise a row with no author would
    /// match a session with no couch id.
    testWidgets('a post with no author id matches nobody', (tester) async {
      await pumpFeed(tester, user(id: '1700000000000'), null);

      expect(find.byTooltip('Edit'), findsNothing);
    });
  });
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}
