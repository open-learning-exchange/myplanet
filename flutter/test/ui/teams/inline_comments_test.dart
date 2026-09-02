import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/repository/voices_repository.dart';
import 'package:myplanet/ui/teams/inline_comments.dart';

import '../../support/widget_harness.dart';

class _MockVoicesRepository extends Mock implements VoicesRepository {}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

/// A session that resolves only after a delay, reproducing the window a real
/// app has before `SessionNotifier.build` completes. A screen that reads the
/// provider without watching it sees `null` for the whole window.
class _DelayedSessionNotifier extends SessionNotifier {
  _DelayedSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() =>
      Future.delayed(const Duration(seconds: 1), () => user);
}

NewsRow _comment({
  String id = 'c1',
  String? userName = 'alice',
  String? message = 'looks good',
  int time = 0,
}) => NewsRow(
  id: id,
  message: message,
  userName: userName,
  docType: 'message',
  messageType: 'comment',
  replyTo: 'task-1',
  viewableBy: 'teams',
  viewableId: 'team-1',
  updatedDate: 0,
  time: time,
  imageUrls: const [],
  labels: const [],
  newsCreatedDate: 0,
  newsUpdatedDate: 0,
  chat: false,
  isEdited: false,
  editedTime: 0,
);

Widget _harness({
  required List<NewsRow> comments,
  UserRow? user,
  VoicesRepository? repository,
  bool delayedSession = false,
  Stream<List<NewsRow>>? stream,
}) => wrapScreen(
  const Scaffold(
    body: InlineComments(parentId: 'task-1', teamId: 'team-1'),
  ),
  overrides: [
    commentsForParentProvider.overrideWith(
      (ref, parentId) => stream ?? Stream.value(comments),
    ),
    if (delayedSession)
      sessionProvider.overrideWith(() => _DelayedSessionNotifier(user))
    else
      sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
    if (repository != null)
      voicesRepositoryProvider.overrideWithValue(repository),
  ],
);

UserRow get _user => buildUserRow(id: 'u1', name: 'alice');

void main() {
  setUpAll(() {
    registerFallbackValue('');
  });

  testWidgets('the header shows the comment count and starts collapsed', (
    tester,
  ) async {
    await tester.pumpWidget(
      _harness(
        comments: [
          _comment(id: 'c1'),
          _comment(id: 'c2'),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('2 comments'), findsOneWidget);
    // Collapsed: neither the comment bodies nor the input are on screen.
    expect(find.text('looks good'), findsNothing);
    expect(find.text('Add a comment'), findsNothing);
  });

  testWidgets('tapping the header expands the thread and the input', (
    tester,
  ) async {
    await tester.pumpWidget(_harness(comments: [_comment()]));
    await tester.pumpAndSettle();

    await tester.tap(find.text('1 comments'));
    await tester.pumpAndSettle();

    expect(find.text('looks good'), findsOneWidget);
    expect(find.text('alice'), findsOneWidget);
    expect(find.text('Add a comment'), findsOneWidget);
  });

  testWidgets('an expanded empty thread shows the empty-state line', (
    tester,
  ) async {
    await tester.pumpWidget(_harness(comments: const []));
    await tester.pumpAndSettle();

    await tester.tap(find.text('0 comments'));
    await tester.pumpAndSettle();

    expect(find.text('No comments yet'), findsOneWidget);
  });

  testWidgets('a comment whose author name is empty still renders', (
    tester,
  ) async {
    // A synced `News` row can carry `"user": {"name": ""}` — the mapper writes
    // whatever the server sent. The avatar must not take the thread down.
    await tester.pumpWidget(
      _harness(
        comments: [_comment(userName: '', message: 'anonymous note')],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('1 comments'));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('anonymous note'), findsOneWidget);
  });

  testWidgets('submitting a comment reaches the repository', (tester) async {
    final repository = _MockVoicesRepository();
    when(
      () => repository.addComment(
        parentId: any(named: 'parentId'),
        teamId: any(named: 'teamId'),
        message: any(named: 'message'),
        userId: any(named: 'userId'),
        userName: any(named: 'userName'),
        planetCode: any(named: 'planetCode'),
        parentCode: any(named: 'parentCode'),
      ),
    ).thenAnswer((_) async => _comment());

    await tester.pumpWidget(
      _harness(comments: const [], user: _user, repository: repository),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('0 comments'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'a new comment');
    await tester.tap(find.byIcon(Icons.send_outlined));
    await tester.pumpAndSettle();

    verify(
      () => repository.addComment(
        parentId: 'task-1',
        teamId: 'team-1',
        message: 'a new comment',
        userId: 'u1',
        userName: 'alice',
        planetCode: any(named: 'planetCode'),
        parentCode: any(named: 'parentCode'),
      ),
    ).called(1);
    // The field is cleared so the next comment starts empty.
    expect(find.text('a new comment'), findsNothing);
  });

  testWidgets('a whitespace-only comment is not submitted', (tester) async {
    final repository = _MockVoicesRepository();

    await tester.pumpWidget(
      _harness(comments: const [], user: _user, repository: repository),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('0 comments'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), '   ');
    await tester.tap(find.byIcon(Icons.send_outlined));
    await tester.pumpAndSettle();

    verifyNever(
      () => repository.addComment(
        parentId: any(named: 'parentId'),
        teamId: any(named: 'teamId'),
        message: any(named: 'message'),
        userId: any(named: 'userId'),
        userName: any(named: 'userName'),
        planetCode: any(named: 'planetCode'),
        parentCode: any(named: 'parentCode'),
      ),
    );
  });

  testWidgets(
    'submitting before the session resolves still posts the comment',
    (tester) async {
      // `InlineComments` never *watches* `sessionProvider`, so a bare
      // `ref.read(...).valueOrNull` is null until something else resolves it.
      // In the app the router holds a `ref.listen`, which is why this is
      // latent there; the comment must not be silently dropped.
      final repository = _MockVoicesRepository();
      when(
        () => repository.addComment(
          parentId: any(named: 'parentId'),
          teamId: any(named: 'teamId'),
          message: any(named: 'message'),
          userId: any(named: 'userId'),
          userName: any(named: 'userName'),
          planetCode: any(named: 'planetCode'),
          parentCode: any(named: 'parentCode'),
        ),
      ).thenAnswer((_) async => _comment());

      await tester.pumpWidget(
        _harness(
          comments: const [],
          user: _user,
          repository: repository,
          delayedSession: true,
        ),
      );
      // Deliberately do not settle: the session future is still in flight.
      await tester.pump();
      await tester.tap(find.text('0 comments'));
      await tester.pump();

      await tester.enterText(find.byType(TextField), 'early comment');
      await tester.tap(find.byIcon(Icons.send_outlined));
      // Advance past the session's own delay. `pumpAndSettle` alone returns as
      // soon as no frame is scheduled, which is before the future resolves.
      await tester.pump(const Duration(seconds: 2));
      await tester.pumpAndSettle();

      verify(
        () => repository.addComment(
          parentId: 'task-1',
          teamId: 'team-1',
          message: 'early comment',
          userId: 'u1',
          userName: 'alice',
          planetCode: any(named: 'planetCode'),
          parentCode: any(named: 'parentCode'),
        ),
      ).called(1);
    },
  );

  testWidgets('double-tapping Send posts the comment once', (tester) async {
    // The clear moved to *after* the write so a failure leaves the text to
    // retry — but the send button is never disabled, so the text now
    // survives the in-flight write and a second tap re-sent it. Before the
    // move the second tap was a no-op (`text.isEmpty`), so the retry fix
    // needs an in-flight guard to hold both properties at once.
    final repository = _MockVoicesRepository();
    final gate = Completer<NewsRow>();
    when(
      () => repository.addComment(
        parentId: any(named: 'parentId'),
        teamId: any(named: 'teamId'),
        message: any(named: 'message'),
        userId: any(named: 'userId'),
        userName: any(named: 'userName'),
        planetCode: any(named: 'planetCode'),
        parentCode: any(named: 'parentCode'),
      ),
    ).thenAnswer((_) => gate.future);

    await tester.pumpWidget(
      _harness(comments: const [], user: _user, repository: repository),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('0 comments'));
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(TextField), 'once please');
    await tester.tap(find.byIcon(Icons.send_outlined));
    await tester.pump();
    // The first write has not landed, so the field still holds the text.
    await tester.tap(find.byIcon(Icons.send_outlined));
    await tester.pump();

    gate.complete(_comment());
    await tester.pumpAndSettle();

    verify(
      () => repository.addComment(
        parentId: any(named: 'parentId'),
        teamId: any(named: 'teamId'),
        message: 'once please',
        userId: any(named: 'userId'),
        userName: any(named: 'userName'),
        planetCode: any(named: 'planetCode'),
        parentCode: any(named: 'parentCode'),
      ),
    ).called(1);
  });

  testWidgets('a failed comment stream shows an error, not an empty thread', (
    tester,
  ) async {
    await tester.pumpWidget(
      _harness(
        comments: const [],
        stream: Stream.error(StateError('database unavailable')),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('0 comments'));
    await tester.pumpAndSettle();

    // Without an error branch the thread renders as if it simply had no
    // comments, which reads as "nobody has commented" rather than "we could
    // not load the comments".
    expect(find.text('No comments yet'), findsNothing);
    expect(find.text('Comments are unavailable'), findsOneWidget);
  });
}
