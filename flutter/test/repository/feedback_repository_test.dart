import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/feedback_mapper.dart';
import 'package:myplanet/repository/feedback_repository_impl.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  late AppDatabase database;
  late FeedbackRepositoryImpl repository;
  late MockPlanetApi api;

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    repository = FeedbackRepositoryImpl(
      feedbackDao: database.feedbackDao,
      planetApi: api,
    );
  });

  tearDown(() async {
    await database.close();
  });

  test('watchAllSorted watches all feedback sorted by openTime desc', () async {
    final entry1 = FeedbackEntriesCompanion.insert(
      id: 'fb1',
      openTime: const Value(100),
      owner: const Value('user1'),
    );
    final entry2 = FeedbackEntriesCompanion.insert(
      id: 'fb2',
      openTime: const Value(200),
      owner: const Value('user2'),
    );

    await database.feedbackDao.upsert(entry1);
    await database.feedbackDao.upsert(entry2);

    final results = await repository.getFeedback(isManager: true).first;
    expect(results, hasLength(2));
    expect(results[0].id, 'fb2');
    expect(results[1].id, 'fb1');
  });

  test('watchByOwner watches feedback of a specific user', () async {
    final entry1 = FeedbackEntriesCompanion.insert(
      id: 'fb1',
      openTime: const Value(100),
      owner: const Value('user1'),
    );
    final entry2 = FeedbackEntriesCompanion.insert(
      id: 'fb2',
      openTime: const Value(200),
      owner: const Value('user2'),
    );

    await database.feedbackDao.upsert(entry1);
    await database.feedbackDao.upsert(entry2);

    final results = await repository.getFeedback(userName: 'user1').first;
    expect(results, hasLength(1));
    expect(results[0].id, 'fb1');
  });

  test('getPendingFeedback returns unuploaded feedback', () async {
    final entry1 = FeedbackEntriesCompanion.insert(
      id: 'fb1',
      isUploaded: const Value(false),
    );
    final entry2 = FeedbackEntriesCompanion.insert(
      id: 'fb2',
      isUploaded: const Value(true),
    );

    await database.feedbackDao.upsert(entry1);
    await database.feedbackDao.upsert(entry2);

    final pending = await repository.getPendingFeedback();
    expect(pending, hasLength(1));
    expect(pending[0].id, 'fb1');
  });

  test('createFeedback handles item and state', () async {
    await repository.createFeedback(
      user: 'user1',
      priority: 'Yes',
      type: 'Bug',
      message: 'Need help',
      item: 'item1',
      state: 'state1',
    );

    final list = await repository.getFeedback(userName: 'user1').first;
    expect(list, hasLength(1));
    expect(list[0].priority, 'Yes');
    expect(list[0].type, 'Bug');
    expect(list[0].item, 'item1');
    expect(list[0].state, 'state1');
    expect(list[0].isUploaded, isFalse);
  });

  test('addReply appends a reply and resets isUploaded to false', () async {
    final entry = FeedbackEntriesCompanion.insert(
      id: 'fb1',
      messages: const Value('[]'),
      isUploaded: const Value(true),
    );
    await database.feedbackDao.upsert(entry);

    await repository.addReply('fb1', 'Hello there', 'replyUser');

    final updated = await repository.getFeedbackById('fb1');
    expect(updated, isNotNull);
    expect(updated!.isUploaded, isFalse);
    final parsed = FeedbackMapper.parseMessages(updated.messages);
    expect(parsed, hasLength(1));
    expect(parsed[0].message, 'Hello there');
    expect(parsed[0].user, 'replyUser');
  });

  test(
    'sync keeps a pending local reply instead of the server thread',
    () async {
      // A synced row picks up a local reply the server has not confirmed…
      await repository.insertFromJson([
        {
          '_id': 'fb1',
          '_rev': '1-a',
          'messages': [
            {'message': 'original', 'time': '1', 'user': 'user1'},
          ],
        },
      ]);
      await repository.addReply('fb1', 'my pending reply', 'user1');

      // …then the next sync delivers the server's copy of the same document.
      await repository.insertFromJson([
        {
          '_id': 'fb1',
          '_rev': '2-b',
          'messages': [
            {'message': 'original', 'time': '1', 'user': 'user1'},
          ],
        },
      ]);

      final stored = await repository.getFeedbackById('fb1');
      expect(
        stored!.isUploaded,
        isFalse,
        reason: 'the row must stay queued for upload',
      );
      final parsed = FeedbackMapper.parseMessages(stored.messages);
      expect(parsed.map((m) => m.message), contains('my pending reply'));
    },
  );

  test('sync adopts the server thread once the row is uploaded', () async {
    await repository.insertFromJson([
      {
        '_id': 'fb1',
        '_rev': '1-a',
        'messages': [
          {'message': 'original', 'time': '1', 'user': 'user1'},
        ],
      },
    ]);

    await repository.insertFromJson([
      {
        '_id': 'fb1',
        '_rev': '2-b',
        'messages': [
          {'message': 'original', 'time': '1', 'user': 'user1'},
          {'message': 'server reply', 'time': '2', 'user': 'user2'},
        ],
      },
    ]);

    final stored = await repository.getFeedbackById('fb1');
    expect(stored!.isUploaded, isTrue);
    final parsed = FeedbackMapper.parseMessages(stored.messages);
    expect(parsed.map((m) => m.message), contains('server reply'));
  });

  test('the sync keeps a document the mapper stored under its `id`', () async {
    // Writer and reader of the same id, driven together. `deleteNotIn` spares
    // what the walk collected, so a document the mapper keys one way and the
    // keep set derives another way is inserted and deleted inside one sync —
    // and the row would be gone with no error anywhere. The second document
    // is what makes the keep set non-empty, since the cleanup is skipped when
    // it collected nothing at all.
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments[0] as String;
      if (url.contains('limit=0')) {
        return const NetworkSuccess<Map<String, dynamic>>({'total_rows': 2});
      }
      return const NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {'_id': 'fb-normal', '_rev': '1-a', 'title': 'ordinary'},
          },
          {
            'doc': {'id': 'fb-legacy', '_rev': '1-b', 'title': 'keyed by id'},
          },
        ],
      });
    });

    final result = await repository.sync(config: config);

    expect(result, isA<SyncComplete>());
    final stored = await database.feedbackDao.watchAllSorted().first;
    expect(
      stored.map((row) => row.id),
      containsAll(['fb-normal', 'fb-legacy']),
    );
  });

  test('the sync skips the `_design` documents CouchDB keeps', () async {
    // A `feedback` database holds CouchDB's own view documents alongside the
    // threads. Kotlin drops them before the insert
    // (`TransactionSyncManager.extractDocs:355-364`); the port did not, so a
    // manager — who reads `watchAllSorted()` — saw one bogus "Untitled
    // feedback / Open" row per design document, tappable and permanent.
    //
    // The ordinary document is here so the walk reaches `deleteNotIn` with a
    // non-empty keep set: the design row must be absent because it was never
    // inserted, not because the cleanup happened to sweep it.
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments[0] as String;
      if (url.contains('limit=0')) {
        return const NetworkSuccess<Map<String, dynamic>>({'total_rows': 3});
      }
      return const NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {'_id': '_design/feedback', '_rev': '1-a', 'views': {}},
          },
          {
            'doc': {'_id': 'fb-normal', '_rev': '1-b', 'title': 'ordinary'},
          },
          // No slash: `_design` is a prefix test in both apps, so an id that
          // is exactly `_design` is a view document too.
          {
            'doc': {'_id': '_design', '_rev': '1-c'},
          },
        ],
      });
    });

    final result = await repository.sync(config: config);

    expect(result, isA<SyncComplete>());
    expect((result as SyncComplete).savedCount, 1);
    final stored = await database.feedbackDao.watchAllSorted().first;
    expect(stored.map((row) => row.id), ['fb-normal']);
  });

  test(
    'the cleanup removes an uploaded row the server no longer has',
    () async {
      // The prune is a deliberate divergence — Kotlin's `FeedbackDao` has no
      // delete at all — so it is pinned rather than left to be "corrected" in
      // either direction. A row that reached the server and is then absent from
      // a complete walk is a thread deleted on the server.
      await database.feedbackDao.upsert(
        FeedbackEntriesCompanion.insert(
          id: 'fb-deleted-on-server',
          isUploaded: const Value(true),
        ),
      );
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer((invocation) async {
        final url = invocation.positionalArguments[0] as String;
        if (url.contains('limit=0')) {
          return const NetworkSuccess<Map<String, dynamic>>({'total_rows': 1});
        }
        return const NetworkSuccess<Map<String, dynamic>>({
          'rows': [
            {
              'doc': {'_id': 'fb-still-there', '_rev': '1-a'},
            },
          ],
        });
      });

      await repository.sync(config: config);

      final stored = await database.feedbackDao.watchAllSorted().first;
      expect(stored.map((row) => row.id), ['fb-still-there']);
    },
  );

  test('the cleanup spares a row uploaded while the walk was running', () async {
    // Writer and reader driven together. `deleteNotIn` spares `isUploaded =
    // false` as it is *when the cleanup runs*, but the outbox drains on its
    // own schedule: a thread that was pending when the walk began can be
    // uploaded while the pages are in flight, and those pages were read from a
    // server that did not have its document yet. Without the snapshot taken
    // before the walk, the thread the user filed a minute ago is deleted from
    // their own list.
    await database.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'fb-just-filed',
        isUploaded: const Value(false),
      ),
    );
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments[0] as String;
      if (url.contains('limit=0')) {
        return const NetworkSuccess<Map<String, dynamic>>({'total_rows': 1});
      }
      // The outbox drains mid-walk: the row is now uploaded, but the page
      // below was read before the server had it.
      await database.feedbackDao.markUploaded('fb-just-filed', '1-fresh');
      return const NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          {
            'doc': {'_id': 'fb-other', '_rev': '1-a'},
          },
        ],
      });
    });

    await repository.sync(config: config);

    final stored = await database.feedbackDao.watchAllSorted().first;
    expect(
      stored.map((row) => row.id),
      containsAll(['fb-just-filed', 'fb-other']),
    );
  });

  test(
    'a pull that omits `_rev` keeps the revision the row is holding',
    () async {
      // `upsertAll` is an insert-or-replace, so a `Value(null)` here would wipe
      // the revision a pending reply needs to update the document under — the
      // Phase 56 shape. Unreachable from `_all_docs?include_docs=true`; this
      // pins the guard for the next caller of `insertFromJson`.
      await repository.insertFromJson([
        {'_id': 'fb1', '_rev': '3-c', 'title': 'from the server'},
      ]);
      await repository.addReply('fb1', 'pending reply', 'user1');

      await repository.insertFromJson([
        {'_id': 'fb1', 'title': 'no revision on this one'},
      ]);

      final stored = await repository.getFeedbackById('fb1');
      expect(stored!.rev, '3-c');
      expect(stored.isUploaded, isFalse);
    },
  );

  test('a reply is signed by its author, not by the thread owner', () async {
    // Kotlin passes `feedback?.owner` (`FeedbackDetailActivity.kt:82`), so an
    // admin answering ada's question posts a reply that reads as ada's. The
    // port signs with the signed-in user; this is here so the divergence is
    // not "corrected" back by a later parity pass.
    await database.feedbackDao.upsert(
      FeedbackEntriesCompanion.insert(
        id: 'fb1',
        owner: const Value('ada'),
        messages: const Value('[]'),
        isUploaded: const Value(true),
      ),
    );

    await repository.addReply('fb1', 'looking into it', 'admin');

    final stored = await repository.getFeedbackById('fb1');
    final parsed = FeedbackMapper.parseMessages(stored!.messages);
    expect(parsed.single.user, 'admin');
    expect(stored.owner, 'ada');
  });

  test(
    'closeFeedback marks status as closed and resets isUploaded to false',
    () async {
      final entry = FeedbackEntriesCompanion.insert(
        id: 'fb1',
        status: const Value('Open'),
        isUploaded: const Value(true),
      );
      await database.feedbackDao.upsert(entry);

      await repository.closeFeedback('fb1');

      final updated = await repository.getFeedbackById('fb1');
      expect(updated, isNotNull);
      expect(updated!.status, 'Closed');
      expect(updated.isUploaded, isFalse);
    },
  );
}
