import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
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

// `ChatHistoryAdapter` names a row by its first query and falls back to the
// stored title, so a fixture that means to exercise the title keeps the two in
// agreement. The two tests at the bottom of this file are the ones that make
// them disagree on purpose.
/// The tap handler calls `loadChat`, which reaches `chatRepositoryProvider`
/// and through it `planetPrefsProvider` — `UnimplementedError` under a widget
/// test. Only the navigation is under test here, so the notifier is stubbed.
class _StubChatNotifier extends ChatConversationNotifier {
  @override
  ChatConversationState build() => const ChatConversationState();

  @override
  Future<void> loadChat(String chatId) async {}
}

const _conversations = '[{"query":"Math help","response":"hello there"}]';

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

  testWidgets('full-search toggle reveals the question/response switch', (
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

    // The toggle is off by default, so the question/response switch is absent.
    expect(find.text('Question'), findsNothing);
    expect(find.text('Response'), findsNothing);

    await tester.tap(find.text('Full conversation response'));
    await tester.pumpAndSettle();

    // Toggling it on reveals the segmented switch.
    expect(find.text('Question'), findsOneWidget);
    expect(find.text('Response'), findsOneWidget);
  });

  testWidgets('full response search matches a response, not the title', (
    tester,
  ) async {
    final chats = [
      _chat(
        id: 'c1',
        title: 'greetings',
        conversations: '[{"query":"greetings","response":"hello there"}]',
      ),
      _chat(
        id: 'c2',
        title: 'science',
        conversations: '[{"query":"science","response":"because"}]',
      ),
    ];

    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [chatHistoryProvider.overrideWith((ref) async => chats)],
      ),
    );
    await tester.pumpAndSettle();

    // Turn on full-conversation search and switch to response mode.
    await tester.tap(find.text('Full conversation response'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Response'));
    await tester.pumpAndSettle();

    // Search a response only the second chat has.
    await tester.enterText(find.byType(SearchBar), 'because');
    await tester.pumpAndSettle();

    expect(find.text('science'), findsOneWidget);
    expect(find.text('greetings'), findsNothing);
  });
  testWidgets('tapping a chat opens that conversation', (tester) async {
    // The tap built its path from `Routes.chat`, which is already the
    // *template* `/life/chat/:chatId`, so it pushed
    // `/life/chat/:chatId/<id>` — three segments where the router defines
    // two. No route matched, and every conversation in the list opened
    // go_router's error page instead. The list one route down, feedback,
    // shows the convention: build the child path from the list's own route.
    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [
          chatHistoryProvider.overrideWith(
            (ref) async => [
              _chat(
                id: 'c1',
                title: 'Math help',
                conversations: _conversations,
              ),
            ],
          ),
          chatConversationProvider.overrideWith(_StubChatNotifier.new),
        ],
        pushTargets: {
          '/life/chat/:chatId': (context) => Text(
            'detail:${GoRouterState.of(context).pathParameters['chatId']}',
          ),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Math help'));
    await tester.pumpAndSettle();

    expect(find.text('detail:c1'), findsOneWidget);
  });

  testWidgets('a chat with no title is named by its first question', (
    tester,
  ) async {
    // `ChatHistoryAdapter` prefers `conversations[0].query` and only falls
    // back to `title`. A synced document need not carry a title at all — the
    // port showed those rows as "Untitled chat" with a "?" avatar even though
    // the question was right there in the conversation.
    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [
          chatHistoryProvider.overrideWith(
            (ref) async => [
              _chat(
                id: 'c1',
                conversations:
                    '[{"query":"How do I plant maize?","response":"Deeply."}]',
              ),
            ],
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('How do I plant maize?'), findsOneWidget);
    expect(find.text('Untitled chat'), findsNothing);
    expect(find.text('H'), findsOneWidget);
  });

  testWidgets('the first question outranks a stored title', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [
          chatHistoryProvider.overrideWith(
            (ref) async => [
              _chat(
                id: 'c1',
                title: 'stale title',
                conversations: '[{"query":"the real question","response":"x"}]',
              ),
            ],
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('the real question'), findsOneWidget);
    expect(find.text('stale title'), findsNothing);
  });
}
