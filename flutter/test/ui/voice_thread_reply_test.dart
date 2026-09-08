import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/ui/voices/voice_thread_screen.dart';

import '../support/widget_harness.dart';

class _MockVoicesActions extends Mock implements VoicesActions {}

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

class _RejectingSessionNotifier extends SessionNotifier {
  @override
  Future<UserRow?> build() => Future.error(StateError('no session'));
}

NewsRow _post(String id) => NewsRow(
  id: id,
  message: 'the well is dry',
  docType: 'message',
  updatedDate: 0,
  time: 0,
  imageUrls: const [],
  labels: const [],
  newsCreatedDate: 0,
  newsUpdatedDate: 0,
  chat: false,
  isEdited: false,
  editedTime: 0,
);

/// Phase 147, Job 4 (`PHASE_144_NOTES.md` item 3).
///
/// The fifth-plus instance of the class: **a provider a screen reads but never
/// watches is null.** `voice_thread_screen._reply` opened with
/// `ref.read(sessionProvider).valueOrNull` and returned early on null, so the
/// tap was dropped before the composer even opened — no dialog, no snackbar,
/// no row. Phase 144 made `VoicesActions.postReply` itself safe, which is why
/// the screen's own early return was the last thing standing between the tap
/// and a reply.
///
/// Latent in the shipping app only because the router holds a
/// `ref.listen(sessionProvider, …)` that keeps it resolved. It is real for any
/// entry that does not — a deep link into a thread, or a rebuild after the
/// session provider is invalidated.
void main() {
  late _MockVoicesActions actions;

  setUp(() {
    actions = _MockVoicesActions();
    when(
      () => actions.postReply(
        parentId: any(named: 'parentId'),
        message: any(named: 'message'),
      ),
    ).thenAnswer((_) async => 'reply-1');
  });

  Future<void> pumpThread(
    WidgetTester tester, {
    required SessionNotifier Function() session,
  }) async {
    await tester.pumpWidget(
      wrapScreen(
        const VoiceThreadScreen(newsId: 'post-1'),
        overrides: [
          sessionProvider.overrideWith(session),
          voiceProvider.overrideWith((ref, id) async => _post(id)),
          voiceRepliesProvider.overrideWith(
            (ref, id) => Stream.value(const []),
          ),
          voiceReplyCountProvider.overrideWith((ref, id) async => 0),
          voicesActionsProvider.overrideWithValue(actions),
        ],
      ),
    );
    await tester.pump();
    await tester.pump();
  }

  testWidgets('a reply lands while the session is still resolving', (
    tester,
  ) async {
    await pumpThread(
      tester,
      session: () => _DelayedSessionNotifier(buildUserRow(id: 'ada')),
    );

    await tester.tap(find.byType(FloatingActionButton));
    // The session resolves during the composer's own round trip, which is
    // exactly what awaiting it buys: the tap waits instead of being dropped.
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();

    expect(
      find.byType(TextField),
      findsOneWidget,
      reason: 'the composer never opened — the tap was dropped',
    );
    await tester.enterText(find.byType(TextField), 'since when?');
    await tester.tap(find.text('Post'));
    await tester.pumpAndSettle();

    verify(
      () => actions.postReply(parentId: 'post-1', message: 'since when?'),
    ).called(1);
  });

  testWidgets('a rejecting session drops the tap instead of throwing', (
    tester,
  ) async {
    // A future can reject where `valueOrNull` could not, which is why the
    // `await` has to sit inside the `try` rather than in front of it.
    await pumpThread(tester, session: _RejectingSessionNotifier.new);

    await tester.tap(find.byType(FloatingActionButton));
    await tester.pump(const Duration(seconds: 2));
    await tester.pumpAndSettle();

    expect(find.byType(TextField), findsNothing);
    expect(tester.takeException(), isNull);
    verifyNever(
      () => actions.postReply(
        parentId: any(named: 'parentId'),
        message: any(named: 'message'),
      ),
    );
  });
}
