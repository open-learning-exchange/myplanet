import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/chat_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/chat/chat_detail_screen.dart';

import '../../support/widget_harness.dart';

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

/// Stands in for the real notifier so the screen can be driven without the
/// repository, the outbox, or a server. Records what the send button asked for.
class _TestChatNotifier extends ChatConversationNotifier {
  _TestChatNotifier(this.initial, {this.outcome = ChatSendOutcome.sent});

  final ChatConversationState initial;

  /// What the conversation reports back. [ChatSendOutcome.declined] is the one
  /// that means the message exists nowhere, and so the one the composer must
  /// not clear for.
  final ChatSendOutcome outcome;
  final sent = <String>[];

  @override
  ChatConversationState build() => initial;

  @override
  Future<ChatSendOutcome> sendMessage(String message) async {
    sent.add(message);
    return outcome;
  }
}

UserRow _user() => buildUserRow(id: 'u1', name: 'ada');

void main() {
  Future<_TestChatNotifier> pumpDetail(
    WidgetTester tester, {
    ChatConversationState state = const ChatConversationState(),
    UserRow? user,
    Map<String, bool>? providers,
    String? chatId,
    ChatSendOutcome outcome = ChatSendOutcome.sent,
  }) async {
    final notifier = _TestChatNotifier(state, outcome: outcome);
    await tester.pumpWidget(
      wrapScreen(
        ChatDetailScreen(chatId: chatId),
        overrides: [
          sessionProvider.overrideWith(() => _TestSessionNotifier(user)),
          chatConversationProvider.overrideWith(() => notifier),
          aiProvidersProvider.overrideWith((ref) async => providers),
        ],
      ),
    );
    await tester.pump();
    await tester.pump();
    return notifier;
  }

  testWidgets('an unstarted conversation invites one', (tester) async {
    await pumpDetail(tester, user: _user());

    expect(find.text('New chat'), findsOneWidget);
    expect(find.text('Start a new conversation'), findsOneWidget);
  });

  testWidgets('a loaded conversation is titled and rendered in turn order', (
    tester,
  ) async {
    await pumpDetail(
      tester,
      user: _user(),
      state: const ChatConversationState(
        id: 'chat-1',
        rev: '1-a',
        messages: [
          ChatMessage(content: 'Capital of Iceland?', isUser: true),
          ChatMessage(content: 'Reykjavik.', isUser: false),
        ],
      ),
    );

    expect(find.text('Conversation'), findsOneWidget);
    expect(find.text('Capital of Iceland?'), findsOneWidget);
    expect(find.text('Reykjavik.'), findsOneWidget);
    expect(find.text('Start a new conversation'), findsNothing);
  });

  testWidgets('the failure the send reported stays on screen', (tester) async {
    await pumpDetail(
      tester,
      user: _user(),
      state: const ChatConversationState(error: 'No provider available'),
    );

    expect(find.text('No provider available'), findsOneWidget);
  });

  testWidgets('the send button hands the trimmed message over and clears', (
    tester,
  ) async {
    final notifier = await pumpDetail(tester, user: _user());

    await tester.enterText(find.byType(TextField), '  Capital of Iceland?  ');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();

    expect(notifier.sent, ['Capital of Iceland?']);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller!.text,
      '',
    );
  });

  testWidgets('a blank message is not sent', (tester) async {
    final notifier = await pumpDetail(tester, user: _user());

    await tester.enterText(find.byType(TextField), '   ');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();

    expect(notifier.sent, isEmpty);
  });

  testWidgets('without a session there is nobody to send as', (tester) async {
    await pumpDetail(tester);

    final button = tester.widget<IconButton>(
      find.ancestor(
        of: find.byIcon(Icons.send),
        matching: find.byType(IconButton),
      ),
    );
    expect(button.onPressed, isNull);
  });

  testWidgets('with no session there is nothing to type into', (tester) async {
    // `onSubmitted` bypassed the disabled send button, so pressing enter with
    // no session reached `_sendMessage` anyway: nothing was added to the
    // thread, the `ListView` was never built, and scrolling to the bottom of a
    // `ScrollController` with no attached view asserted — the screen went down
    // on a keystroke. The crash was patched in `_scrollToBottom`; the route to
    // it stayed open, and it was also the route by which the composer was
    // cleared into nothing.
    //
    // `ChatDetailFragment.refreshInputState:596-602` disables
    // `editGchatMessage` itself, so Kotlin has neither the text nor the
    // keystroke. Asserting on `enabled` rather than on the absence of a crash
    // is what makes this a test of the gate instead of a test of the patch.
    final notifier = await pumpDetail(tester);

    expect(
      tester.widget<TextField>(find.byType(TextField)).enabled,
      isFalse,
      reason: 'the composer is live with nobody to send as',
    );

    await tester.testTextInput.receiveAction(TextInputAction.send);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(tester.takeException(), isNull);
    expect(notifier.sent, isEmpty);
    expect(find.byType(ChatDetailScreen), findsOneWidget);
  });

  testWidgets('a declined send leaves the typed text in the composer', (
    tester,
  ) async {
    // The whole defect in one assertion. `_messageController.clear()` used to
    // run *before* the await, so a send the conversation refused — the
    // `session == null` arm of `ChatConversationNotifier.sendMessage`, reached
    // from the soft keyboard — consumed the text with no bubble, no banner and
    // no pending row.
    //
    // Mutation: put the clear back above the await and this fails on the text,
    // while every other test in this file stays green.
    final notifier = await pumpDetail(
      tester,
      user: _user(),
      outcome: ChatSendOutcome.declined,
    );

    await tester.enterText(find.byType(TextField), 'Capital of Iceland?');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();

    expect(notifier.sent, ['Capital of Iceland?']);
    expect(
      tester.widget<TextField>(find.byType(TextField)).controller!.text,
      'Capital of Iceland?',
    );
  });

  testWidgets('a failed send clears the composer, because the bubble has it', (
    tester,
  ) async {
    // `ChatSendOutcome.failed` is not a discard: the query bubble is on
    // screen, `state.error` is in the banner and `savePendingChat` has written
    // a retry row. Keeping the text in the field as well would leave the
    // person looking at it twice with no way to tell which one is real.
    await pumpDetail(tester, user: _user(), outcome: ChatSendOutcome.failed);

    await tester.enterText(find.byType(TextField), 'Capital of Iceland?');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();

    expect(
      tester.widget<TextField>(find.byType(TextField)).controller!.text,
      '',
    );
  });

  testWidgets('Send with an empty field says so', (tester) async {
    // `setupSendButton:299-302` shows `kindly_enter_message` in
    // `textGchatIndicator`. The port returned in silence, so the button
    // visibly did nothing.
    final notifier = await pumpDetail(tester, user: _user());

    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();

    expect(notifier.sent, isEmpty);
    expect(find.text('Write something first'), findsOneWidget);
  });

  testWidgets('an unavailable provider is shown but cannot be picked', (
    tester,
  ) async {
    await pumpDetail(
      tester,
      user: _user(),
      providers: const {'openai': true, 'perplexity': false},
    );

    await tester.tap(find.byIcon(Icons.psychology));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(find.text('openai'), findsOneWidget);
    expect(find.text('perplexity (unavailable)'), findsOneWidget);
    final disabled = tester.widget<PopupMenuItem<String>>(
      find.ancestor(
        of: find.text('perplexity (unavailable)'),
        matching: find.byType(PopupMenuItem<String>),
      ),
    );
    expect(disabled.enabled, isFalse);
  });

  testWidgets('no provider menu appears when the server offers none', (
    tester,
  ) async {
    await pumpDetail(tester, user: _user(), providers: const {});

    expect(find.byIcon(Icons.psychology), findsNothing);
  });
  testWidgets('a newline in the field is flattened to a space', (tester) async {
    // `ChatDetailFragment` sends
    // `"${binding.editGchatMessage.text}".replace("\n", " ")`. Trimming alone
    // leaves an embedded newline in the request body.
    final notifier = await pumpDetail(tester, user: _user());

    await tester.enterText(find.byType(TextField), 'first line\nsecond line');
    await tester.tap(find.byIcon(Icons.send));
    await tester.pump();

    expect(notifier.sent, ['first line second line']);
  });
}
