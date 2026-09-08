import 'dart:convert';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/chat_repository.dart';
import 'package:myplanet/repository/voices_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// The pair, not the halves: a chat share is only worth anything if the row
/// the writer produces is the row the "already shared" readers select, and if
/// the uploader can find it. Every fixture here goes through
/// [buildChatShareMap] into [VoicesRepository.createFromShareMap] rather than
/// hand-faking the join.
void main() {
  late AppDatabase database;
  late VoicesRepository voices;
  var idCounter = 0;

  setUp(() {
    database = AppDatabase.memory();
    idCounter = 0;
    voices = VoicesRepository(
      MockPlanetApi(),
      database.newsDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(9000),
      createId: () => 'local-${++idCounter}',
    );
  });
  tearDown(() => database.close());

  ChatRow chat({
    String? docId = 'chat_1',
    String? title = 'Tech Discussion',
    String? conversations,
  }) => ChatRow(
    id: 'c1',
    docId: docId,
    rev: '2-rev',
    title: title,
    user: 'ada',
    aiProvider: 'openai',
    conversations:
        conversations ??
        jsonEncode([
          {'query': 'What is Kotlin?', 'response': 'A language.'},
        ]),
    lastUsed: 0,
    isUploaded: false,
  );

  const team = ChatShareTarget(
    id: 'team_123',
    name: 'Test Team',
    teamType: 'team',
    teamPlanetCode: 'planet_xyz',
  );

  Future<String> share({
    ChatRow? row,
    ChatShareTarget? target = team,
    String section = ChatShareSection.teams,
    String note = 'Have a look',
    String planetCode = 'lc',
  }) => voices.createFromShareMap(
    payload: buildChatShareMap(
      chat: row ?? chat(),
      note: note,
      target: target,
      section: section,
      nowMillis: 1700000000000,
    ),
    userId: 'u1',
    userName: 'ada',
    planetCode: planetCode,
    parentCode: 'pc',
  );

  test('the row carries the note, destination and nested chat fields', () async {
    final id = await share();
    final row = (await database.newsDao.getById(id))!;

    expect(row.message, 'Have a look');
    expect(row.chat, isTrue);
    expect(row.docType, 'message');
    expect(row.createdOn, 'lc');
    expect(row.parentCode, 'pc');
    expect(row.userId, 'u1');
    expect(row.userName, 'ada');
    expect(row.sharedBy, '');
    expect(row.replyTo, '');
    expect(row.messageType, 'team');
    expect(row.messagePlanetCode, 'planet_xyz');
    // 0, and deliberately: `News.createNews` reads `map["updatedDate"]` from
    // the *outer* map, which `buildShareMap` never writes — it only puts the
    // millis in the nested `news` object. So the row's own `updatedDate` stays
    // 0 while `newsUpdatedDate` carries the share time.
    expect(row.updatedDate, 0);
    // The row's own clock, not the payload's — `News.createNews` sets
    // `time = Date().time` independently of `map["updatedDate"]`.
    expect(row.time, 9000);

    expect(row.newsId, 'chat_1');
    expect(row.newsRev, '2-rev');
    expect(row.newsTitle, 'Tech Discussion');
    expect(row.newsUser, 'ada');
    expect(row.aiProvider, 'openai');
    expect(row.newsCreatedDate, 1700000000000);
    expect(row.newsUpdatedDate, 1700000000000);
    expect(jsonDecode(row.conversations!), [
      {'query': 'What is Kotlin?', 'response': 'A language.'},
    ]);

    // `name` is *absent*, not null: `getViewInJson` writes `map["name"]`,
    // which `buildShareMap` never sets, and Gson drops a null key.
    expect(jsonDecode(row.viewIn!), [
      {'_id': 'team_123', 'section': 'teams'},
    ]);
  });

  test(
    'an untitled chat is shared with an empty title, never "null"',
    () async {
      final id = await share(row: chat(title: null));
      expect((await database.newsDao.getById(id))!.newsTitle, '');
    },
  );

  // `String.toBoolean()` is case-insensitive, and false for anything else.
  test('the chat flag is read the way Kotlin reads it', () async {
    Future<bool> chatFlagFor(String raw) async {
      final payload = buildChatShareMap(
        chat: chat(),
        note: '',
        target: team,
        section: ChatShareSection.teams,
        nowMillis: 1700000000000,
      );
      final id = await voices.createFromShareMap(
        payload: {...payload, 'chat': raw},
        userId: 'u1',
        userName: 'ada',
        planetCode: 'lc',
      );
      return (await database.newsDao.getById(id))!.chat;
    }

    expect(await chatFlagFor('true'), isTrue);
    expect(await chatFlagFor('TRUE'), isTrue);
    expect(await chatFlagFor('false'), isFalse);
    expect(await chatFlagFor('yes'), isFalse);
  });

  test('an empty conversation list leaves the column null', () async {
    final id = await share(row: chat(conversations: '[]'));
    expect((await database.newsDao.getById(id))!.conversations, isNull);
  });

  // `News.kt:187` runs the nested JSON through `replace("=", ":")`. That is a
  // no-op in Kotlin only because Gson's html-safe default escapes `=` to
  // `\u003d`; `jsonEncode` does not, so a literal port would rewrite the
  // user's own text.
  test('an "=" in conversation text survives the share', () async {
    final id = await share(
      row: chat(
        conversations: jsonEncode([
          {'query': 'why is 2+2=4?', 'response': 'see https://x.test/?a=b'},
        ]),
      ),
    );
    final turns =
        jsonDecode((await database.newsDao.getById(id))!.conversations!)
            as List;
    expect(turns.single['query'], 'why is 2+2=4?');
    expect(turns.single['response'], 'see https://x.test/?a=b');
  });

  group('the already-shared readers see what the writer wrote', () {
    test(
      'a share is found by planetNewsMessages and mapped to its chat',
      () async {
        await share(target: team);

        final rows = await voices.planetNewsMessages('lc');
        expect(rows, hasLength(1));
        expect(VoicesRepository.extractSharedViewInIds(rows), {
          'chat_1': {'team_123'},
        });
      },
    );

    test(
      'planetNewsMessages matches the planet code case-insensitively',
      () async {
        await share(planetCode: 'LC');
        expect(await voices.planetNewsMessages('lc'), hasLength(1));
      },
    );

    test(
      'planetNewsMessages ignores another planet, and a null code',
      () async {
        await share(planetCode: 'other');
        expect(await voices.planetNewsMessages('lc'), isEmpty);
        expect(await voices.planetNewsMessages(null), isEmpty);
        expect(await voices.planetNewsMessages(''), isEmpty);
      },
    );

    test(
      'isAlreadyShared is true for the destination and false for another',
      () async {
        await share(target: team);

        expect(await voices.isAlreadyShared('chat_1', 'team_123'), isTrue);
        expect(await voices.isAlreadyShared('chat_1', 'team_999'), isFalse);
        expect(await voices.isAlreadyShared('chat_2', 'team_123'), isFalse);
      },
    );

    test('two destinations for one chat both register', () async {
      await share(target: team);
      await share(
        target: const ChatShareTarget(id: 'lc@pc', name: 'Community'),
        section: ChatShareSection.community,
      );

      final ids = VoicesRepository.extractSharedViewInIds(
        await voices.planetNewsMessages('lc'),
      );
      expect(ids['chat_1'], {'team_123', 'lc@pc'});
    });

    test('an ordinary voice post contributes no chat destinations', () async {
      await voices.createPost(
        message: 'hello',
        userId: 'u1',
        userName: 'ada',
        planetCode: 'lc',
      );
      expect(
        VoicesRepository.extractSharedViewInIds(
          await voices.planetNewsMessages('lc'),
        ),
        isEmpty,
      );
    });

    test('a row whose viewIn will not parse loses only its own ids', () async {
      await share(target: team);
      await database.newsDao.upsert(
        NewsEntriesCompanion.insert(
          id: 'broken',
          newsId: const Value('chat_1'),
          viewIn: const Value('{not json'),
          docType: const Value('message'),
          createdOn: const Value('lc'),
        ),
      );

      final ids = VoicesRepository.extractSharedViewInIds(
        await voices.planetNewsMessages('lc'),
      );
      expect(ids['chat_1'], {'team_123'});
    });
  });

  group('the viewIn entry the share writes', () {
    test('omits name, so a later community share can back-fill it', () async {
      final id = await share(target: team);

      final before =
          jsonDecode((await database.newsDao.getById(id))!.viewIn!) as List;
      expect((before.single as Map).containsKey('name'), isFalse);

      // `shareNewsToCommunity` back-fills the first entry's name only when the
      // key is absent — `!obj.has("name")` is false for an explicit null, so
      // writing one would silently disable this.
      await voices.shareToCommunity(
        newsId: id,
        userId: 'u1',
        planetCode: 'lc',
        parentCode: 'pc',
        teamName: 'Test Team',
      );

      final after =
          jsonDecode((await database.newsDao.getById(id))!.viewIn!) as List;
      expect((after.first as Map)['name'], 'Test Team');
    });

    test('a named target still records its name', () async {
      final id = await voices.createPost(
        message: 'hi',
        userId: 'u1',
        userName: 'ada',
        viewInId: 'team_1',
        viewInSection: 'teams',
        viewInName: 'Bricklayers',
      );
      final entries =
          jsonDecode((await database.newsDao.getById(id))!.viewIn!) as List;
      expect(entries.single, {
        '_id': 'team_1',
        'section': 'teams',
        'name': 'Bricklayers',
      });
    });

    test('no destination yields an empty array', () async {
      final id = await share(target: null);
      expect(
        jsonDecode((await database.newsDao.getById(id))!.viewIn!),
        isEmpty,
      );
    });
  });

  // `isAlreadyShared` is a case-*insensitive* substring match on the raw
  // `viewIn` text, where `extractSharedViewInIds` parses and compares ids
  // case-sensitively. The two disagreeing is the Kotlin's behaviour, not an
  // oversight to unify.
  test(
    'isAlreadyShared matches the destination id case-insensitively',
    () async {
      await voices.createFromShareMap(
        payload: buildChatShareMap(
          chat: chat(),
          note: '',
          target: const ChatShareTarget(id: 'Team_1', name: 'T'),
          section: ChatShareSection.teams,
          nowMillis: 1700000000000,
        ),
        userId: 'u1',
        userName: 'ada',
        planetCode: 'lc',
      );

      expect(await voices.isAlreadyShared('chat_1', 'team_1'), isTrue);
      expect(
        VoicesRepository.extractSharedViewInIds(
          await voices.planetNewsMessages('lc'),
        )['chat_1'],
        {'Team_1'},
      );
    },
  );

  // Planet-unscoped, unlike the checkmark cache: the Kotlin query has no
  // `createdOn` filter.
  test('isAlreadyShared ignores the planet the share was written on', () async {
    await share(target: team, planetCode: 'somewhere-else');
    expect(await voices.isAlreadyShared('chat_1', 'team_123'), isTrue);
  });

  test(
    'a chat the server has never seen shares under an empty newsId',
    () async {
      final id = await share(row: chat(docId: null));
      expect((await database.newsDao.getById(id))!.newsId, '');
    },
  );

  group('the upload path', () {
    test(
      'a shared chat is pending, and its wire document keeps the chat',
      () async {
        final id = await share(target: team);

        final pending = await voices.pendingUploads();
        expect(pending.map((r) => r.id), contains(id));

        final doc = VoicesRepository.serialize(
          pending.firstWhere((r) => r.id == id),
        );
        expect(doc['chat'], isTrue);
        expect(doc['message'], 'Have a look');
        expect(doc['messageType'], 'team');
        expect(doc['messagePlanetCode'], 'planet_xyz');
        expect(doc['viewIn'], [
          {'_id': 'team_123', 'section': 'teams'},
        ]);

        final news = doc['news'] as Map<String, dynamic>;
        expect(news['_id'], 'chat_1');
        expect(news['_rev'], '2-rev');
        expect(news['title'], 'Tech Discussion');
        expect(news['aiProvider'], 'openai');
        expect(news['createdDate'], 1700000000000);
        expect(news['conversations'], [
          {'query': 'What is Kotlin?', 'response': 'A language.'},
        ]);
      },
    );

    test(
      'a delivered share stops being pending and stays discoverable',
      () async {
        final id = await share(target: team);
        await voices.markUploaded(id, 'news_1', '1-abc');

        expect(await voices.pendingUploads(), isEmpty);
        expect(await voices.isAlreadyShared('chat_1', 'team_123'), isTrue);
      },
    );
  });
}
