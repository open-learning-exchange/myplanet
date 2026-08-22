import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/chat_mapper.dart';
import 'package:myplanet/repository/chat_repository_impl.dart';

/// A chat sync is new, and chat is the one table with no uploader at all —
/// every conversation exists only on the device it was typed on. So the sync
/// has to be read as a thing that can *destroy* data, not only supply it.
void main() {
  late AppDatabase database;
  late ChatRepositoryImpl repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = ChatRepositoryImpl(
      planetApi: MockPlanetApi(),
      chatDao: database.chatDao,
      serverUrl: 'https://planet.example',
    );
  });
  tearDown(() => database.close());

  Future<void> seed({
    required String id,
    String? rev,
    required List<Map<String, String>> conversations,
  }) => database.chatDao.upsertAll([
    ChatEntriesCompanion.insert(
      id: id,
      docId: Value(id),
      rev: Value(rev),
      user: const Value('ada'),
      title: const Value('Capital of Iceland?'),
      conversations: Value(jsonEncode(conversations)),
    ),
  ]);

  Map<String, dynamic> serverDoc(String id) => {
    '_id': id,
    '_rev': '2-b',
    'user': 'ada',
    'title': 'Capital of Iceland?',
    'conversations': [
      {'query': 'Capital of Iceland?', 'response': 'Reykjavik.'},
    ],
  };

  test('a question still awaiting its answer is not overwritten', () async {
    // `_continueConversation(id, query, '', rev)` on the error path stores the
    // question with an empty response. It exists nowhere else — letting the
    // sync upsert over it drops what the user asked.
    await seed(
      id: 'chat-1',
      rev: '2-b',
      conversations: [
        {'query': 'Capital of Iceland?', 'response': 'Reykjavik.'},
        {'query': 'And its population?', 'response': ''},
      ],
    );

    await repository.insertChatHistoryFromSync([serverDoc('chat-1')]);

    final row = await database.chatDao.findByDocId('chat-1');
    final conversations = ChatMapper.parseConversations(row!.conversations);
    expect(conversations, hasLength(2));
    expect(conversations.last.query, 'And its population?');
  });

  test('a settled conversation is refreshed from the server', () async {
    // The guard must not turn the sync into a no-op for ordinary rows.
    await seed(
      id: 'chat-1',
      rev: '1-a',
      conversations: [
        {'query': 'Capital of Iceland?', 'response': 'Reykjavik.'},
      ],
    );

    await repository.insertChatHistoryFromSync([serverDoc('chat-1')]);

    expect((await database.chatDao.findByDocId('chat-1'))!.rev, '2-b');
  });

  test('a conversation the server never confirmed is not deleted', () async {
    // No `_rev` means this device never saw the document acknowledged, and
    // there is no uploader to send it. Deleting it destroys the only copy.
    await seed(
      id: 'local-only',
      rev: null,
      conversations: [
        {'query': 'Offline question', 'response': 'An answer'},
      ],
    );
    await seed(
      id: 'server-gone',
      rev: '1-a',
      conversations: [
        {'query': 'Old', 'response': 'Older'},
      ],
    );

    await database.chatDao.deleteNotIn(const ['still-there']);

    expect(await database.chatDao.findByDocId('local-only'), isNotNull);
    expect(await database.chatDao.findByDocId('server-gone'), equals(null));
  });

  test('an empty keep list does not empty the table', () async {
    // A server whose `chat_history` has been emptied, or a sync that walked no
    // pages, produced exactly this call — and it used to delete everything.
    await seed(
      id: 'local-only',
      rev: null,
      conversations: [
        {'query': 'Offline question', 'response': 'An answer'},
      ],
    );

    await database.chatDao.deleteNotIn(const []);

    expect(await database.chatDao.findByDocId('local-only'), isNotNull);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
