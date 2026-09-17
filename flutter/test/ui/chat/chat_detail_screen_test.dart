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
  _TestChatNotifier(this.initial);

  final ChatConversationState initial;
  final sent = <String>[];

  @override
  ChatConversationState build() => initial;

  @override
  Future<void> sendMessage(String message) async {
    sent.add(message);
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
  }) async {
    final notifier = _TestChatNotifier(state);
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

  testWidgets('submitting from the keyboard does not take the screen down', (
    tester,
  ) async {
    // `onSubmitted` bypasses the disabled send button, so pressing enter with
    // no session reached `_sendMessage` anyway. Nothing was added to the
    // thread, so the `ListView` was never built — and scrolling to the bottom
    // of a `ScrollController` with no attached view asserts, taking the screen
    // down on a keystroke.
    await pumpDetail(tester);

    await tester.enterText(find.byType(TextField), 'Capital of Iceland?');
    await tester.testTextInput.receiveAction(TextInputAction.send);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(tester.takeException(), isNull);
    expect(find.byType(ChatDetailScreen), findsOneWidget);
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
