import 'package:drift/drift.dart' hide isNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/chat_mapper.dart';
import 'package:myplanet/repository/chat_repository.dart';
import 'package:myplanet/repository/chat_repository_impl.dart';
import 'package:myplanet/core/network/network_result.dart';

void main() {
  late AppDatabase database;
  late ChatRepositoryImpl repository;

  late MockPlanetApi api;

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

  Future<void> seedRevisions(List<String> revs) async {
    await database.chatDao.upsertAll([
      for (var i = 0; i < revs.length; i++)
        ChatEntriesCompanion.insert(
          id: 'row-$i',
          docId: const Value('chat-1'),
          rev: Value(revs[i]),
        ),
    ]);
  }

  test('getLatestRev orders by generation, not lexicographically', () async {
    // `_rev` is `<generation>-<hash>`, and the hash carries no ordering. String
    // comparison puts '9-...' above '10-...', so the newest revision would be
    // skipped the moment a document reached ten generations — and an upload
    // sent with a stale `_rev` is rejected as a conflict.
    await seedRevisions(['9-aaa', '10-bbb', '2-zzz']);

    expect(await repository.getLatestRev('chat-1'), '10-bbb');
  });

  test('getLatestRev tolerates an unparseable revision', () async {
    await seedRevisions(['not-a-rev', '3-ccc']);

    expect(await repository.getLatestRev('chat-1'), '3-ccc');
  });

  test('getLatestRev returns null when the document is unknown', () async {
    expect(await repository.getLatestRev('missing'), isNull);
  });
  group('a new chat adopts the id CouchDB assigned', () {
    void stubChatResponse(Map<String, dynamic> body) {
      when(
        () => api.sendJsonObject(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
        ),
      ).thenAnswer((_) async => NetworkSuccess<Map<String, dynamic>>(body));
    }

    test('stores the row under the server id, not a local one', () async {
      stubChatResponse({
        'status': 'Success',
        'chat': 'Reykjavik.',
        'couchDBResponse': {'id': 'server-doc-1', 'rev': '1-a'},
      });

      final result = await repository.sendNewChatRequest(
        query: 'Capital of Iceland?',
        user: 'ada',
        aiProvider: const AiProviderConfig(name: 'default', model: ''),
      );

      // A locally-minted id addresses no document: the follow-up request sends
      // this value as `_id`, so the whole conversation continues into nothing.
      expect((result as ChatSuccess).id, 'server-doc-1');
      expect(await repository.getLatestRev('server-doc-1'), '1-a');
      expect((await database.chatDao.getByDocId('server-doc-1')), hasLength(1));
    });

    test('fails rather than storing an unaddressable row', () async {
      stubChatResponse({
        'status': 'Success',
        'chat': 'Reykjavik.',
        'couchDBResponse': {'rev': '1-a'},
      });

      final result = await repository.sendNewChatRequest(
        query: 'Capital of Iceland?',
        user: 'ada',
        aiProvider: const AiProviderConfig(name: 'default', model: ''),
      );

      expect(result, isA<ChatError>());
      expect(await repository.getChatHistoryForUser('ada'), isEmpty);
    });
  });

  group('retry while composing', () {
    void stubSequential(List<NetworkResult<Map<String, dynamic>>> responses) {
      when(
        () => api.sendJsonObject(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
        ),
      ).thenAnswer((_) async => responses.removeAt(0));
    }

    test(
      'retries transient failures and succeeds on the third attempt',
      () async {
        stubSequential([
          const NetworkError<Map<String, dynamic>>(null, 'timeout'),
          const NetworkError<Map<String, dynamic>>(503, 'unavailable'),
          NetworkSuccess<Map<String, dynamic>>({
            'status': 'Success',
            'chat': 'Reykjavik.',
            'couchDBResponse': {'id': 'server-doc-2', 'rev': '1-b'},
          }),
        ]);

        final result = await repository.sendNewChatRequest(
          query: 'Capital of Iceland?',
          user: 'ada',
          aiProvider: const AiProviderConfig(name: 'default', model: ''),
        );

        expect((result as ChatSuccess).response, 'Reykjavik.');
        verify(
          () => api.sendJsonObject(
            any(),
            body: any(named: 'body'),
            method: any(named: 'method'),
          ),
        ).called(3);
      },
    );

    test('gives up after maxAttempts and returns ChatError', () async {
      when(
        () => api.sendJsonObject(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
        ),
      ).thenAnswer(
        (_) async => const NetworkError<Map<String, dynamic>>(500, 'error'),
      );

      final result = await repository.sendNewChatRequest(
        query: 'Capital of Iceland?',
        user: 'ada',
        aiProvider: const AiProviderConfig(name: 'default', model: ''),
      );

      expect(result, isA<ChatError>());
      verify(
        () => api.sendJsonObject(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
        ),
      ).called(3);
    });

    test('retries on NetworkException too', () async {
      when(
        () => api.sendJsonObject(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
        ),
      ).thenAnswer(
        (_) async =>
            NetworkException<Map<String, dynamic>>(Exception('no route')),
      );

      final result = await repository.sendContinueChatRequest(
        query: 'Follow-up',
        user: 'ada',
        aiProvider: const AiProviderConfig(name: 'default', model: ''),
        id: 'chat-1',
        rev: '1-a',
      );

      expect(result, isA<ChatError>());
      verify(
        () => api.sendJsonObject(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
        ),
      ).called(3);
    });
  });

  group('searchChats', () {
    // Two conversations: the title (first query) is "math help"; the second
    // turn's question is "fractions" and its response is "halves and quarters".
    const mathConvo =
        '[{"query":"math help","response":"yes?"},'
        '{"query":"fractions","response":"halves and quarters"}]';
    // The title is "science chat"; its only response mentions "gravity".
    const scienceConvo =
        '[{"query":"science chat","response":"gravity holds things down"}]';

    late List<ChatRow> chats;

    setUp(() {
      chats = [
        ChatRow(
          id: 'math',
          title: 'math help',
          conversations: mathConvo,
          createdDate: '100',
          updatedDate: '200',
          lastUsed: 0,
          isUploaded: false,
        ),
        ChatRow(
          id: 'science',
          title: 'science chat',
          conversations: scienceConvo,
          createdDate: '50',
          updatedDate: '300',
          lastUsed: 0,
          isUploaded: false,
        ),
      ];
    });

    test('empty query returns the chats unchanged', () {
      final result = repository.searchChats('', ChatSearchMode.title, chats);
      expect(result.length, 2);
    });

    test('title search matches the first query, ranked prefix-first', () {
      // "math" prefixes the title of the first chat.
      var result = repository.searchChats('math', ChatSearchMode.title, chats);
      expect(result.map((c) => c.id), ['math']);

      // "chat" is a substring of the second chat's title, not a prefix.
      result = repository.searchChats('chat', ChatSearchMode.title, chats);
      expect(result.map((c) => c.id), ['science']);
    });

    test('question search walks every turn and ranks in-title hits first', () {
      // "fractions" is the second turn's question of the first chat.
      var result = repository.searchChats(
        'fractions',
        ChatSearchMode.question,
        chats,
      );
      expect(result.map((c) => c.id), ['math']);

      // "math" prefixes the first turn's question (the title), so the first
      // chat ranks ahead of a substring match in a later turn.
      result = repository.searchChats('math', ChatSearchMode.question, chats);
      expect(result.map((c) => c.id), ['math']);
    });

    test('response search matches response text', () {
      final result = repository.searchChats(
        'gravity',
        ChatSearchMode.response,
        chats,
      );
      expect(result.map((c) => c.id), ['science']);
    });

    test('diacritics are stripped so an accent matches its plain form', () {
      final accented = [
        ChatRow(
          id: 'cafe',
          title: 'café',
          conversations: '[{"query":"café","response":"coffee"}]',
          lastUsed: 0,
          isUploaded: false,
        ),
      ];
      final result = repository.searchChats(
        'cafe',
        ChatSearchMode.title,
        accented,
      );
      expect(result.map((c) => c.id), ['cafe']);
    });

    test('multi-word queries match when all parts appear', () {
      final result = repository.searchChats(
        'halves quarters',
        ChatSearchMode.response,
        chats,
      );
      expect(result.map((c) => c.id), ['math']);
    });
  });

  test(
    'getChatHistoryForUser sorts by the newest of created/updated',
    () async {
      await database.chatDao.upsertAll([
        ChatEntriesCompanion.insert(
          id: 'old',
          docId: const Value('old'),
          user: const Value('ada'),
          title: const Value('older chat'),
          createdDate: const Value('100'),
          updatedDate: const Value('100'),
        ),
        ChatEntriesCompanion.insert(
          id: 'fresh',
          docId: const Value('fresh'),
          user: const Value('ada'),
          title: const Value('freshly updated'),
          createdDate: const Value('50'),
          updatedDate: const Value('300'),
        ),
      ]);

      final result = await repository.getChatHistoryForUser('ada');
      expect(result.map((c) => c.id), ['fresh', 'old']);
    },
  );
}

class MockPlanetApi extends Mock implements PlanetApi {}
