import 'package:flutter_test/flutter_test.dart';
import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/feedback_mapper.dart';
import 'package:myplanet/repository/feedback_repository_impl.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

void main() {
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
