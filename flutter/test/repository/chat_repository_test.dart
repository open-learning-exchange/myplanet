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
        'couchdb': {'id': 'server-doc-1', 'rev': '1-a'},
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
        'couchdb': {'rev': '1-a'},
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
            'couchdb': {'id': 'server-doc-2', 'rev': '1-b'},
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
}

class MockPlanetApi extends Mock implements PlanetApi {}
