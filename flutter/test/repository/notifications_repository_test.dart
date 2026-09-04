import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/notifications_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

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
      teamDao: database.teamDao,
      userDao: database.userDao,
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

  group('the formatting lookups', () {
    // Port of `getTaskTeamNamesByTaskIds` / `getTaskTeamNamesByTaskTitles` /
    // `getJoinRequestDetailsBatch` / `getJoinRequestDetails` — the four calls
    // `loadNotifications` makes before formatting a page of notifications.
    setUp(() async {
      await database.teamDao.upsert(
        TeamsCompanion.insert(id: 'team-1', name: const Value('Reading Club')),
      );
      await database.teamTaskDao.upsert(
        TeamTasksCompanion.insert(
          id: 'task-1',
          teamId: 'team-1',
          title: const Value('Read chapter 3'),
        ),
      );
    });

    test('resolves a task team name by task id', () async {
      expect(await repository.taskTeamNamesByTaskIds(['task-1']), {
        'task-1': 'Reading Club',
      });
    });

    test('resolves a task team name by task title', () async {
      expect(await repository.taskTeamNamesByTaskTitles(['Read chapter 3']), {
        'Read chapter 3': 'Reading Club',
      });
    });

    test(
      'a task whose team has no cached document is absent, not blank',
      () async {
        await database.teamTaskDao.upsert(
          TeamTasksCompanion.insert(
            id: 'task-2',
            teamId: 'team-missing',
            title: const Value('Orphan'),
          ),
        );
        final names = await repository.taskTeamNamesByTaskIds([
          'task-1',
          'task-2',
        ]);
        expect(names.containsKey('task-2'), isFalse);
        expect(names['task-1'], 'Reading Club');
      },
    );

    test('an empty request list runs no query and returns nothing', () async {
      expect(await repository.taskTeamNamesByTaskIds(const []), isEmpty);
      expect(await repository.taskTeamNamesByTaskTitles(const []), isEmpty);
      expect(await repository.joinRequestDetailsBatch(const []), isEmpty);
    });

    test('resolves a join request to its requester and team', () async {
      await database.teamDao.upsert(
        TeamsCompanion.insert(
          id: 'req-1',
          docType: const Value('request'),
          teamId: const Value('team-1'),
          userId: const Value('user-9'),
        ),
      );
      await database.userDao.upsert(
        UsersCompanion.insert(id: 'user-9', name: const Value('Jane')),
      );

      expect(await repository.joinRequestDetailsBatch(['req-1']), {
        'req-1': const JoinRequestDetail(
          requester: 'Jane',
          team: 'Reading Club',
        ),
      });
      expect(
        await repository.joinRequestDetails('req-1'),
        const JoinRequestDetail(requester: 'Jane', team: 'Reading Club'),
      );
    });

    test('an unresolvable requester or team is left null for the caller to '
        'localise', () async {
      await database.teamDao.upsert(
        TeamsCompanion.insert(
          id: 'req-2',
          docType: const Value('request'),
          teamId: const Value('team-missing'),
          userId: const Value('user-missing'),
        ),
      );
      final details = await repository.joinRequestDetailsBatch(['req-2']);
      // Kotlin substitutes the English "Unknown User"/"Unknown Team" here; the
      // port leaves them null so `formatNotification` can use the ARB.
      expect(details['req-2'], const JoinRequestDetail());
    });

    test('a null related id yields the unknown pair, as getJoinRequestInfo '
        'returns null for it', () async {
      expect(
        await repository.joinRequestDetails(null),
        const JoinRequestDetail(),
      );
      expect(
        await repository.joinRequestDetails(''),
        const JoinRequestDetail(),
      );
    });
  });

  group('server notification sync-in', () {
    late MockPlanetApi api;

    setUp(() {
      api = MockPlanetApi();
      repository = NotificationsRepository(
        database.notificationDao,
        teamNotificationDao: database.teamNotificationDao,
        newsDao: database.newsDao,
        teamTaskDao: database.teamTaskDao,
        teamDao: database.teamDao,
        userDao: database.userDao,
        now: () => DateTime.fromMillisecondsSinceEpoch(1234),
        api: api,
      );
    });

    const config = ServerConfig(
      serverUrl: 'https://planet.example',
      couchDbUrl: 'https://satellite:1234@planet.example:443',
      pin: '1234',
    );

    test(
      'parses server documents and stamps isFromServer with the rev',
      () async {
        when(
          () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
        ).thenAnswer((invocation) async {
          final url = invocation.positionalArguments.single as String;
          if (url.endsWith('limit=0')) {
            return NetworkSuccess<Map<String, dynamic>>({'total_rows': 2});
          }
          return NetworkSuccess<Map<String, dynamic>>({
            'rows': [
              {
                'doc': {
                  '_id': 'team-join-1',
                  'type': 'team',
                  'user': 'user-1',
                  'message': 'A join request',
                  'link': '/teams/view/team-1/applicantTab',
                  'linkParams': {'activeTab': 'applicantTab'},
                  'item': 'team-1',
                  'priority': 5,
                  'time': 99000,
                  '_rev': '1-abc',
                  'status': 'unread',
                },
              },
              {
                'doc': {'_id': '_design/someview', 'type': 'team'},
              },
              {
                'doc': {
                  '_id': 'newtask-1',
                  'type': 'newTask',
                  'user': 'user-1',
                  'message': 'A new task',
                  'link': '/tasks/view/task-9/details',
                  'priority': 0,
                  'time': 88000,
                  '_rev': '2-def',
                  'status': 'read',
                },
              },
            ],
          });
        });

        final result = await repository.sync(config: config);

        expect(result, isA<SyncComplete>());
        expect(
          (result as SyncComplete).savedCount,
          2,
          reason: '_design docs are skipped',
        );

        final join = await database.notificationDao.getById('team-join-1');
        expect(join, isNot(isNull));
        expect(join!.isFromServer, isTrue);
        expect(join.rev, '1-abc');
        expect(join.isRead, isFalse, reason: 'status "unread" -> not read');
        expect(join.subType, 'join_request');
        expect(join.relatedId, 'team-1');
        expect(join.createdAt, 99000);

        final task = await database.notificationDao.getById('newtask-1');
        expect(task, isNot(isNull));
        expect(task!.isRead, isTrue);
        expect(
          task.relatedId,
          'task-9',
          reason: 'newTask pulls the id from link',
        );
      },
    );

    test(
      'preserves a local read state that has not been uploaded yet',
      () async {
        // First sync pulls the row as unread.
        when(
          () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
        ).thenAnswer((invocation) async {
          final url = invocation.positionalArguments.single as String;
          if (url.endsWith('limit=0')) {
            return NetworkSuccess<Map<String, dynamic>>({'total_rows': 1});
          }
          return NetworkSuccess<Map<String, dynamic>>({
            'rows': [
              {
                'doc': {
                  '_id': 'n-1',
                  'type': 'team',
                  'user': 'user-1',
                  'message': 'm',
                  'status': 'unread',
                  '_rev': '1-a',
                  'time': 1000,
                },
              },
            ],
          });
        });
        await repository.sync(config: config);
        await repository.markNotificationAsRead('n-1', 'user-1');
        expect((await database.notificationDao.getById('n-1'))!.isRead, isTrue);
        expect(
          (await database.notificationDao.getById('n-1'))!.needsSync,
          isTrue,
        );

        // A re-sync must not clobber the local read with the server's stale
        // "unread" — the row stays read and still flagged for upload.
        await repository.sync(config: config);
        final row = await database.notificationDao.getById('n-1');
        expect(row!.isRead, isTrue);
        expect(row.needsSync, isTrue);
      },
    );

    test(
      'does not evict locally-authored notifications (no deleteNotIn)',
      () async {
        await repository.updateResourceNotification('user-1', 7);
        when(
          () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
        ).thenAnswer((invocation) async {
          final url = invocation.positionalArguments.single as String;
          if (url.endsWith('limit=0')) {
            return NetworkSuccess<Map<String, dynamic>>({'total_rows': 1});
          }
          return NetworkSuccess<Map<String, dynamic>>({
            'rows': [
              {
                'doc': {
                  '_id': 'server-1',
                  'type': 'team',
                  'user': 'user-1',
                  'message': 'm',
                  'status': 'unread',
                  '_rev': '1-a',
                  'time': 1000,
                },
              },
            ],
          });
        });

        await repository.sync(config: config);

        expect(
          await database.notificationDao.getById('user-1:resource:count'),
          isNot(isNull),
          reason: 'the local resource-count row survives the sync',
        );
      },
    );
  });

  group('markNotificationAsRead', () {
    test('a summary id marks every notification of that type read', () async {
      await database.notificationDao.upsert(
        NotificationsCompanion.insert(
          id: 's-1',
          userId: 'user-1',
          type: const Value('team'),
          message: const Value('m1'),
          createdAt: 1,
          isFromServer: const Value(true),
        ),
      );
      await database.notificationDao.upsert(
        NotificationsCompanion.insert(
          id: 's-2',
          userId: 'user-1',
          type: const Value('team'),
          message: const Value('m2'),
          createdAt: 2,
          isFromServer: const Value(true),
        ),
      );

      await repository.markNotificationAsRead('summary_team', 'user-1');

      expect((await database.notificationDao.getById('s-1'))!.isRead, isTrue);
      expect((await database.notificationDao.getById('s-2'))!.isRead, isTrue);
      expect(
        (await database.notificationDao.getById('s-1'))!.needsSync,
        isTrue,
        reason: 'server-originated rows are flagged for read-state upload',
      );
    });
  });
}
