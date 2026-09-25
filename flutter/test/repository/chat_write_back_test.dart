import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/chat_mapper.dart';
import 'package:myplanet/repository/chat_repository.dart';
import 'package:myplanet/repository/chat_repository_impl.dart';

/// The chat write-back paths: the shape the AI endpoint actually answers with,
/// what a failed send leaves behind, and the rows `savePendingChat` hands to
/// the outbox.
///
/// The wire format here is taken from the Kotlin `model/ChatResponse.kt`, whose
/// Gson `@SerializedName` annotations are the only authority on what the server
/// sends: `couchDBResponse`, not `couchdb`.
void main() {
  late AppDatabase database;
  late ChatRepositoryImpl repository;
  late MockPlanetApi api;

  const provider = AiProviderConfig(name: 'openai', model: 'gpt-4o');

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
    repository = ChatRepositoryImpl(
      planetApi: api,
      chatDao: database.chatDao,
      serverUrl: 'https://planet.example',
      retryDelay: Duration.zero,
    );
  });
  tearDown(() => database.close());

  void stubChat(Map<String, dynamic> body) {
    when(
      () => api.sendJsonObject(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
      ),
    ).thenAnswer((_) async => NetworkSuccess<Map<String, dynamic>>(body));
  }

  void stubFailure() {
    when(
      () => api.sendJsonObject(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
      ),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(503, 'offline'),
    );
  }

  Future<void> seedConversation({
    required String id,
    String? rev = '1-a',
    List<Map<String, String>> conversations = const [
      {'query': 'Capital of Iceland?', 'response': 'Reykjavik.'},
    ],
  }) => database.chatDao.upsertAll([
    ChatEntriesCompanion.insert(
      id: id,
      docId: Value(id),
      rev: Value(rev),
      user: const Value('ada'),
      title: const Value('Capital of Iceland?'),
      createdDate: const Value('1000'),
      updatedDate: const Value('1000'),
      conversations: Value(jsonEncode(conversations)),
    ),
  ]);

  Future<List<ChatConversation>> turnsOf(String docId) async {
    final row = await database.chatDao.findByDocId(docId);
    return ChatMapper.parseConversations(row?.conversations);
  }

  group('the response envelope the server actually sends', () {
    test('a new chat adopts the id from couchDBResponse', () async {
      // `ChatResponse.couchDBResponse` is `@SerializedName("couchDBResponse")`
      // in the Kotlin, so that — not `couchdb` — is the key on the wire. Read
      // under the wrong name the object is always absent, the id is always
      // missing, and every successful answer is reported to the user as
      // "Chat was saved without a document id" with nothing stored.
      stubChat({
        'status': 'Success',
        'chat': 'Reykjavik.',
        'couchDBResponse': {'ok': true, 'id': 'server-doc-1', 'rev': '1-a'},
      });

      final result = await repository.sendNewChatRequest(
        query: 'Capital of Iceland?',
        user: 'ada',
        aiProvider: provider,
      );

      expect(result, isA<ChatSuccess>());
      expect((result as ChatSuccess).id, 'server-doc-1');
      expect(result.rev, '1-a');
      expect(await database.chatDao.getByDocId('server-doc-1'), hasLength(1));
    });

    test('a continuation advances the rev from couchDBResponse', () async {
      // Missing this leaves the row on its old `_rev`. The next follow-up
      // sends that stale revision as `_rev` and CouchDB rejects it as a
      // conflict, so a conversation can never get past its second turn.
      await seedConversation(id: 'chat-1');
      stubChat({
        'status': 'Success',
        'chat': 'About 380,000 people.',
        'couchDBResponse': {'ok': true, 'id': 'chat-1', 'rev': '2-b'},
      });

      final result = await repository.sendContinueChatRequest(
        query: 'How many live there?',
        user: 'ada',
        aiProvider: provider,
        id: 'chat-1',
        rev: '1-a',
      );

      expect((result as ChatSuccess).rev, '2-b');
      expect(await repository.getLatestRev('chat-1'), '2-b');
    });
  });

  group('a continuation the server never answered', () {
    // Kotlin `ChatRepositoryImpl.sendContinueChatRequest` calls
    // `continueConversation(id, message, "", rev)` on the non-success branch
    // *and* the exception branch. Only the exception branch was ported — and
    // `_postChat` swallows network failures into `null` rather than throwing,
    // so the offline case, the one this exists for, took the unported path and
    // dropped the user's question entirely.

    test('keeps the question when every retry fails', () async {
      await seedConversation(id: 'chat-1');
      stubFailure();

      final result = await repository.sendContinueChatRequest(
        query: 'How many live there?',
        user: 'ada',
        aiProvider: provider,
        id: 'chat-1',
        rev: '1-a',
      );

      expect(result, isA<ChatError>());
      final turns = await turnsOf('chat-1');
      expect(turns, hasLength(2));
      expect(turns.last.query, 'How many live there?');
      expect(turns.last.response, isEmpty);
    });

    test('keeps the question when the server answers a failure', () async {
      await seedConversation(id: 'chat-1');
      stubChat({'status': 'Error', 'message': 'No provider available'});

      final result = await repository.sendContinueChatRequest(
        query: 'How many live there?',
        user: 'ada',
        aiProvider: provider,
        id: 'chat-1',
        rev: '1-a',
      );

      expect((result as ChatError).message, 'No provider available');
      final turns = await turnsOf('chat-1');
      expect(turns, hasLength(2));
      expect(turns.last.query, 'How many live there?');
    });

    test('does not overwrite a good revision with an empty one', () async {
      // Kotlin's `addConversation` assigns `_rev` only
      // `if (!newRev.isNullOrEmpty())`. Writing the empty string over a real
      // revision loses the handle the next upload needs, and `deleteNotIn`
      // treats a revision-less row as never confirmed by the server.
      await seedConversation(id: 'chat-1');
      stubChat({
        'status': 'Success',
        'chat': 'About 380,000 people.',
        'couchDBResponse': {'id': 'chat-1', 'rev': ''},
      });

      await repository.sendContinueChatRequest(
        query: 'How many live there?',
        user: 'ada',
        aiProvider: provider,
        id: 'chat-1',
        rev: '1-a',
      );

      expect(await repository.getLatestRev('chat-1'), '1-a');
    });
  });

  group('savePendingChat', () {
    test('gives each queued chat its own id', () async {
      // The id was built as '...\${DateTime.now()...}' inside single quotes,
      // which escapes the `$` instead of interpolating: every call produced the
      // same literal primary key, so `upsertAll` replaced the previous row and
      // the earlier message was gone before the outbox ever drained.
      final first = await repository.savePendingChat(
        user: 'ada',
        query: 'First question',
        aiProvider: provider,
      );
      final second = await repository.savePendingChat(
        user: 'ada',
        query: 'Second question',
        aiProvider: provider,
      );

      expect(first, isNot(second));
      expect(first, isNot(contains(r'${')));
      expect(await database.chatDao.getPending(), hasLength(2));
    });

    test('sorts to the top of the history it was just typed into', () async {
      // Without a created date `sortChatsByRecency` scores the row 0, so the
      // message the user just sent appears below every conversation they have
      // ever had — including the one it belongs after.
      await seedConversation(id: 'older');
      await repository.savePendingChat(
        user: 'ada',
        query: 'Just now',
        aiProvider: provider,
      );

      final history = await repository.getChatHistoryForUser('ada');
      expect(history.first.title, 'Just now');
    });

    test('appends to the conversation it continues', () async {
      await seedConversation(id: 'chat-1');

      final id = await repository.savePendingChat(
        user: 'ada',
        query: 'How many live there?',
        aiProvider: provider,
        existingId: 'chat-1',
        existingRev: '1-a',
      );

      expect(id, 'chat-1');
      final turns = await turnsOf('chat-1');
      expect(turns.map((t) => t.query), [
        'Capital of Iceland?',
        'How many live there?',
      ]);
      expect(await database.chatDao.getPending(), hasLength(1));
    });
  });

  group('fetchAiProviders', () {
    test('decodes the provider availability map', () async {
      when(() => api.getRaw(any())).thenAnswer(
        (_) async => const NetworkSuccess<String>(
          '{"openai": true, "perplexity": false}',
        ),
      );

      expect(await repository.fetchAiProviders(), {
        'openai': true,
        'perplexity': false,
      });
    });

    test('returns null rather than throwing on an unusable body', () async {
      when(
        () => api.getRaw(any()),
      ).thenAnswer((_) async => const NetworkSuccess<String>('not json'));

      expect(await repository.fetchAiProviders(), isNull);
    });

    test('returns null when the endpoint is unreachable', () async {
      when(
        () => api.getRaw(any()),
      ).thenAnswer((_) async => const NetworkError<String>(null, 'no route'));

      expect(await repository.fetchAiProviders(), isNull);
    });
  });

  group('mapper round trip', () {
    test('a new chat document reads back as the turn it recorded', () async {
      final doc = ChatMapper.buildNewChatDoc(
        id: 'doc-1',
        rev: '1-a',
        user: 'ada',
        query: 'Capital of Iceland?',
        response: 'Reykjavik.',
        aiProvider: 'openai',
      );

      await database.chatDao.upsertAll([ChatMapper.fromDoc(doc)]);

      final turns = await turnsOf('doc-1');
      expect(turns, hasLength(1));
      expect(turns.single.query, 'Capital of Iceland?');
      expect(turns.single.response, 'Reykjavik.');
      final row = await database.chatDao.findByDocId('doc-1');
      expect(row!.isUploaded, isTrue);
      expect(row.rev, '1-a');
    });

    test('a synced document does not queue itself for upload', () async {
      await repository.insertChatHistoryFromSync([
        {
          '_id': 'doc-1',
          'doc': {
            '_id': 'doc-1',
            '_rev': '1-a',
            'user': 'ada',
            'conversations': [
              {'query': 'q', 'response': 'r'},
            ],
          },
        },
        {'_id': '_design/chats', 'doc': <String, dynamic>{}},
      ]);

      expect(await database.chatDao.getPending(), isEmpty);
      expect(await database.chatDao.findByDocId('_design/chats'), isNull);
      expect(await database.chatDao.findByDocId('doc-1'), isNotNull);
    });
  });

  test('history for a missing user is empty, not everyone', () async {
    await seedConversation(id: 'chat-1');

    expect(await repository.getChatHistoryForUser(null), isEmpty);
    expect(await repository.getChatHistoryForUser(''), isEmpty);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
