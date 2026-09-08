import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/chat_provider.dart';
import 'package:myplanet/repository/chat_repository.dart';
import 'package:myplanet/ui/chat/chat_history_screen.dart';

import '../../support/widget_harness.dart';

/// Records what the screen asked to share, and answers with a fixed outcome.
class _FakeShareActions implements ChatShareActions {
  _FakeShareActions(this.outcome);

  final ChatShareOutcome outcome;

  @override
  Ref get ref => throw UnimplementedError();
  final calls = <Map<String, Object?>>[];

  @override
  Future<ChatShareOutcome> share({
    required ChatRow chat,
    required ChatShareTarget? target,
    required String section,
    required String note,
  }) async {
    calls.add({
      'chat': chat.docId,
      'target': target?.id,
      'section': section,
      'note': note,
    });
    return outcome;
  }
}

/// The tap handler reaches `chatRepositoryProvider` and through it
/// `planetPrefsProvider`, which is unimplemented in this harness.
class _StubChatNotifier extends ChatConversationNotifier {
  @override
  ChatConversationState build() => const ChatConversationState();

  @override
  Future<void> loadChat(String chatId) async {}
}

ChatRow _chat({String id = 'c1', String? docId = 'chat_1'}) => ChatRow(
  id: id,
  docId: docId,
  title: 'Math help',
  conversations: '[{"query":"Math help","response":"hello there"}]',
  lastUsed: 0,
  isUploaded: false,
);

const _team = ChatShareTarget(id: 'team_1', name: 'Bricklayers');
const _enterprise = ChatShareTarget(id: 'ent_1', name: 'Bakery');
const _community = ChatShareTarget(id: 'lc@pc', name: 'Community');

void main() {
  Future<_FakeShareActions> pump(
    WidgetTester tester, {
    ChatShareTargets targets = const ChatShareTargets(
      community: _community,
      teams: [_team],
      enterprises: [_enterprise],
    ),
    Map<String, Set<String>> destinations = const {},
    ChatShareOutcome outcome = ChatShareOutcome.shared,
    List<ChatRow>? chats,
  }) async {
    final actions = _FakeShareActions(outcome);
    await tester.pumpWidget(
      wrapScreen(
        const ChatHistoryScreen(),
        overrides: [
          chatHistoryProvider.overrideWith((ref) async => chats ?? [_chat()]),
          chatConversationProvider.overrideWith(_StubChatNotifier.new),
          chatShareTargetsProvider.overrideWith((ref) async => targets),
          sharedChatDestinationsProvider.overrideWith(
            (ref) async => destinations,
          ),
          chatShareActionsProvider.overrideWithValue(actions),
        ],
      ),
    );
    await tester.pumpAndSettle();
    return actions;
  }

  Future<void> openShareSheet(WidgetTester tester) async {
    await tester.tap(find.byKey(const Key('share-chat-c1')));
    await tester.pumpAndSettle();
  }

  // Reachability: the whole slice below is dead without this button.
  testWidgets('every chat row offers a share action', (tester) async {
    await pump(
      tester,
      chats: [
        _chat(),
        _chat(id: 'c2', docId: 'chat_2'),
      ],
    );

    expect(find.byKey(const Key('share-chat-c1')), findsOneWidget);
    expect(find.byKey(const Key('share-chat-c2')), findsOneWidget);
  });

  testWidgets('the share sheet offers the community and team groups', (
    tester,
  ) async {
    await pump(tester);
    await openShareSheet(tester);

    expect(find.text('Share with community'), findsOneWidget);
    expect(find.text('Share with team/enterprise'), findsOneWidget);
    // Collapsed to start with, exactly as `generateFlatList` builds it with an
    // empty `expandedGroups`.
    expect(find.byKey(const Key('share-child-teams')), findsNothing);
  });

  testWidgets('sharing to a team carries the team, section and note', (
    tester,
  ) async {
    final actions = await pump(tester);
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-team')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-teams')));
    await tester.pumpAndSettle();

    expect(find.text('Bricklayers'), findsOneWidget);
    await tester.tap(find.byKey(const Key('share-target-team_1')));
    await tester.pumpAndSettle();

    await tester.enterText(
      find.byKey(const Key('share-note-field')),
      'worth a read',
    );
    await tester.tap(find.byKey(const Key('share-note-submit')));
    await tester.pumpAndSettle();

    expect(actions.calls, [
      {
        'chat': 'chat_1',
        'target': 'team_1',
        'section': 'teams',
        'note': 'worth a read',
      },
    ]);
    expect(find.text('Chat shared'), findsOneWidget);
  });

  testWidgets('sharing to an enterprise uses the enterprises section', (
    tester,
  ) async {
    final actions = await pump(tester);
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-team')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-enterprises')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-target-ent_1')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-note-submit')));
    await tester.pumpAndSettle();

    expect(actions.calls.single['target'], 'ent_1');
    expect(actions.calls.single['section'], 'enterprises');
    // `add_note` is optional; an untouched field still shares.
    expect(actions.calls.single['note'], '');
  });

  testWidgets('sharing to the community uses the community section', (
    tester,
  ) async {
    final actions = await pump(tester);
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-community')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-community')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-note-submit')));
    await tester.pumpAndSettle();

    expect(actions.calls.single['target'], 'lc@pc');
    expect(actions.calls.single['section'], 'community');
  });

  testWidgets('cancelling the note dialog shares nothing', (tester) async {
    final actions = await pump(tester);
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-community')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-community')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(actions.calls, isEmpty);
  });

  testWidgets('an empty team list explains how to get one', (tester) async {
    final actions = await pump(
      tester,
      targets: const ChatShareTargets(community: _community),
    );
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-team')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-teams')));
    await tester.pumpAndSettle();

    expect(find.text('Please join a Team to share this chat'), findsOneWidget);
    expect(actions.calls, isEmpty);
  });

  testWidgets('an empty enterprise list has its own message', (tester) async {
    await pump(tester, targets: const ChatShareTargets(community: _community));
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-team')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-enterprises')));
    await tester.pumpAndSettle();

    expect(
      find.text('Please join an Enterprise to share this chat'),
      findsOneWidget,
    );
  });

  testWidgets('a destination the chat already reached cannot be picked', (
    tester,
  ) async {
    final actions = await pump(
      tester,
      destinations: const {
        'chat_1': {'team_1', 'lc@pc'},
      },
    );
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-community')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-community')));
    await tester.pumpAndSettle();
    // Still on the branch dialog: the disabled tile swallowed the tap.
    expect(find.byKey(const Key('share-note-field')), findsNothing);

    await tester.tap(find.byKey(const Key('share-group-team')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-teams')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-target-team_1')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('share-note-field')), findsNothing);

    expect(actions.calls, isEmpty);
  });

  testWidgets('another chat is unaffected by this one being shared', (
    tester,
  ) async {
    await pump(
      tester,
      chats: [_chat(id: 'c2', docId: 'chat_2')],
      destinations: const {
        'chat_1': {'team_1'},
      },
    );
    await tester.tap(find.byKey(const Key('share-chat-c2')));
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('share-group-team')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-teams')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-target-team_1')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('share-note-field')), findsOneWidget);
  });

  testWidgets('a duplicate share is reported rather than posted twice', (
    tester,
  ) async {
    await pump(tester, outcome: ChatShareOutcome.alreadyShared);
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-community')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-child-community')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('share-note-submit')));
    await tester.pumpAndSettle();

    expect(
      find.text('This chat has already been shared to this destination'),
      findsOneWidget,
    );
  });

  testWidgets('a planet with no community offers no community entry', (
    tester,
  ) async {
    await pump(tester, targets: const ChatShareTargets(teams: [_team]));
    await openShareSheet(tester);

    await tester.tap(find.byKey(const Key('share-group-community')));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('share-child-community')), findsNothing);
  });
}
