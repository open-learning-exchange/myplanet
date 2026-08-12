import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/notifications_repository.dart';

void main() {
  late AppDatabase database;
  late NotificationsRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = NotificationsRepository(
      database.notificationDao,
      teamNotificationDao: database.teamNotificationDao,
      newsDao: database.newsDao,
      teamTaskDao: database.teamTaskDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(1234),
    );
  });
  tearDown(() => database.close());

  test('maintains resource count notification lifecycle', () async {
    await repository.updateResourceNotification('user-1', 3);
    var rows = await repository.watch('user-1').first;
    expect(rows.single.message, '3');
    expect(rows.single.type, 'resource');
    expect(rows.single.createdAt, 1234);

    await repository.markAsRead([rows.single.id]);
    await repository.updateResourceNotification('user-1', 3);
    rows = await repository.watch('user-1').first;
    expect(rows.single.isRead, isTrue, reason: 'unchanged counts stay read');

    await repository.updateResourceNotification('user-1', 4);
    rows = await repository.watch('user-1').first;
    expect(rows.single.isRead, isFalse, reason: 'changed counts become unread');

    await repository.updateResourceNotification('user-1', 0);
    expect(await repository.watch('user-1').first, isEmpty);
  });

  test('warns only at or below ten percent storage availability', () async {
    await repository.updateStorageNotification('user-1', 10);
    expect((await repository.watch('user-1').first).single.message, '10%');

    await repository.updateStorageNotification('user-1', 11);
    expect(await repository.watch('user-1').first, isEmpty);
  });

  group('team notifications', () {
    /// A team-visible post, which is what the chat count counts.
    Future<void> addTeamPost(String id, String teamId) =>
        database.newsDao.upsert(
          NewsEntriesCompanion.insert(
            id: id,
            viewableBy: const Value('teams'),
            viewableId: Value(teamId),
          ),
        );

    Future<void> addTask(
      String id,
      String teamId, {
      required String assignee,
      required int deadline,
    }) => database.teamTaskDao.upsert(
      TeamTasksCompanion.insert(
        id: id,
        teamId: teamId,
        assignee: Value(assignee),
        deadline: Value(deadline),
      ),
    );

    test('an empty team list short-circuits', () async {
      expect(
        await repository.getTeamNotifications(const [], 'user-1'),
        isEmpty,
      );
    });

    test('no chat badge without a watermark row, however many posts', () async {
      await addTeamPost('post-1', 'team-1');
      await addTeamPost('post-2', 'team-1');

      final info = await repository.getTeamNotifications(['team-1'], 'user-1');

      // The dot means "new since you last looked"; never-looked reads as
      // nothing new, exactly as the Kotlin's `notification != null` guard does.
      expect(info['team-1']!.hasChat, isFalse);
    });

    test('a chat badge appears once posts outrun the watermark', () async {
      await addTeamPost('post-1', 'team-1');
      await addTeamPost('post-2', 'team-1');
      await repository.updateTeamNotification('team-1', 2);

      var info = await repository.getTeamNotifications(['team-1'], 'user-1');
      expect(info['team-1']!.hasChat, isFalse, reason: 'caught up');

      await addTeamPost('post-3', 'team-1');
      info = await repository.getTeamNotifications(['team-1'], 'user-1');
      expect(info['team-1']!.hasChat, isTrue);

      // Opening the team's voices moves the watermark and clears the dot.
      await repository.updateTeamNotification('team-1', 3);
      info = await repository.getTeamNotifications(['team-1'], 'user-1');
      expect(info['team-1']!.hasChat, isFalse);
    });

    test('the watermark is one row per team, not one per update', () async {
      await repository.updateTeamNotification('team-1', 1);
      await repository.updateTeamNotification('team-1', 2);
      await repository.updateTeamNotification('team-1', 3);

      final rows = await database.teamNotificationDao.byTypeAndParentIds(
        'chat',
        ['team-1'],
      );
      expect(rows.single.lastCount, 3);
    });

    test('posts are counted per team, and only team-visible ones', () async {
      await addTeamPost('post-1', 'team-1');
      await addTeamPost('post-2', 'team-2');
      // A community post carries no team visibility and must not count.
      await database.newsDao.upsert(
        NewsEntriesCompanion.insert(
          id: 'post-3',
          viewableBy: const Value('community'),
          viewableId: const Value('team-1'),
        ),
      );
      await repository.updateTeamNotification('team-1', 0);
      await repository.updateTeamNotification('team-2', 1);

      final info = await repository.getTeamNotifications([
        'team-1',
        'team-2',
      ], 'user-1');

      expect(info['team-1']!.hasChat, isTrue, reason: '1 post beats 0 seen');
      expect(info['team-2']!.hasChat, isFalse, reason: '1 post, 1 seen');
    });

    test(
      'a task due within a day lights every team, as the Kotlin does',
      () async {
        await addTask(
          'task-1',
          'team-1',
          assignee: 'user-1',
          deadline: DateTime.fromMillisecondsSinceEpoch(
            1234,
          ).add(const Duration(hours: 5)).millisecondsSinceEpoch,
        );

        final info = await repository.getTeamNotifications([
          'team-1',
          'team-2',
        ], 'user-1');

        // `hasTask` is computed once and written onto every team — team-2 has no
        // tasks at all and still shows the dot. Reproduced deliberately.
        expect(info['team-1']!.hasTask, isTrue);
        expect(info['team-2']!.hasTask, isTrue);
      },
    );

    test(
      'a task outside the window or for someone else lights nothing',
      () async {
        // Past its deadline: the window starts at *now*, so an overdue task falls
        // outside it.
        await addTask('task-past', 'team-1', assignee: 'user-1', deadline: 1);
        // Further out than a day.
        await addTask(
          'task-far',
          'team-1',
          assignee: 'user-1',
          deadline: DateTime.fromMillisecondsSinceEpoch(
            1234,
          ).add(const Duration(days: 3)).millisecondsSinceEpoch,
        );
        // Someone else's task, due inside the window.
        await addTask(
          'task-other',
          'team-1',
          assignee: 'user-2',
          deadline: DateTime.fromMillisecondsSinceEpoch(
            1234,
          ).add(const Duration(hours: 2)).millisecondsSinceEpoch,
        );

        final info = await repository.getTeamNotifications([
          'team-1',
        ], 'user-1');
        expect(info['team-1']!.hasTask, isFalse);
      },
    );
  });
}
