import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/chat_provider.dart';
import 'package:myplanet/ui/chat/chat_history_screen.dart';

import '../../support/widget_harness.dart';

ChatRow _chat({
  required String id,
  String? title,
  String? conversations,
  String? updatedDate,
}) => ChatRow(
  id: id,
  title: title,
  conversations: conversations,
  updatedDate: updatedDate,
  lastUsed: 0,
  isUploaded: false,
);

const _conversations = '[{"query":"hi","response":"hello there"}]';

void main() {
  testWidgets('shows the empty state when there is no chat history', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [
          chatHistoryProvider.overrideWith((ref) async => const <ChatRow>[]),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No chats yet'), findsOneWidget);
    expect(find.byIcon(Icons.chat_bubble_outline), findsOneWidget);
  });

  testWidgets('lists each chat with its title, last message, and avatar', (
    tester,
  ) async {
    final chats = [
      _chat(id: 'c1', title: 'Math help', conversations: _conversations),
      _chat(id: 'c2'),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [chatHistoryProvider.overrideWith((ref) async => chats)],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Math help'), findsOneWidget);
    // The last conversation response is shown as the subtitle.
    expect(find.text('hello there'), findsOneWidget);
    // A chat without a title falls back to "Untitled chat".
    expect(find.text('Untitled chat'), findsOneWidget);
    // The avatar shows the first letter of the title, or "?" when untitled.
    expect(find.text('M'), findsOneWidget);
    expect(find.text('?'), findsOneWidget);
  });

  testWidgets('malformed conversation JSON shows no subtitle, not a crash', (
    tester,
  ) async {
    final chats = [_chat(id: 'c1', title: 'Broken', conversations: 'not json')];

    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [chatHistoryProvider.overrideWith((ref) async => chats)],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Broken'), findsOneWidget);
    // The malformed JSON does not surface an error; the subtitle is simply
    // absent.
    expect(find.text('not json'), findsNothing);
  });

  testWidgets('searching filters chats by title', (tester) async {
    final chats = [
      _chat(id: 'c1', title: 'Math help'),
      _chat(id: 'c2', title: 'Science chat'),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [chatHistoryProvider.overrideWith((ref) async => chats)],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Math help'), findsOneWidget);
    expect(find.text('Science chat'), findsOneWidget);

    await tester.enterText(find.byType(SearchBar), 'math');
    await tester.pumpAndSettle();

    expect(find.text('Math help'), findsOneWidget);
    expect(find.text('Science chat'), findsNothing);
    // A search with no matches shows the "no search results" message.
    expect(find.text('No matching chats'), findsNothing);
  });

  testWidgets('a search with no matches shows the no-results message', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [
          chatHistoryProvider.overrideWith(
            (ref) async => [_chat(id: 'c1', title: 'Math help')],
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(SearchBar), 'astronomy');
    await tester.pumpAndSettle();

    expect(find.text('No matching chats'), findsOneWidget);
    expect(find.text('Math help'), findsNothing);
  });
}
