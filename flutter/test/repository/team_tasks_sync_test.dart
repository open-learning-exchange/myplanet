import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/team_tasks_repository.dart';

import '../support/mock_planet_api.dart';

/// The `tasks` walk — `TransactionSyncManager.syncDb("tasks")` plus
/// `TeamsRepositoryImpl.bulkInsertTasksFromSync` / `TeamTask.fromJson`.
///
/// Phase 116's D16 came with a trap attached, and it is the first test here:
/// the walk can land a full page of rows and the screen still shows nothing,
/// because `TeamTask.serialize` emits **no `status` field** while the port's
/// `watchForTeam` required `status = 'active'`.
///
/// The documents are shaped the way `TeamTask.serialize` actually writes them —
/// `link: {teams: …}`, `assignee` as an object or the empty string, no
/// `status` — rather than flattened to the columns the row happens to have.
void main() {
  late AppDatabase db;
  late MockPlanetApi api;
  late TeamTasksRepository repository;

  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );
  const dbUrl = 'https://satellite:1234@planet.example.org:443/db';

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
    repository = TeamTasksRepository(api, db.teamTaskDao);
  });

  tearDown(() => db.close());

  /// A document in the shape `TeamTask.serialize` produces.
  Map<String, dynamic> taskDoc(
    String id, {
    String teamId = 'team-1',
    String title = 'Fix the well pump',
    Object? assignee = '',
    bool completed = false,
    int deadline = 1800000000000,
    String? status,
  }) => {
    '_id': id,
    '_rev': '2-abc',
    'title': title,
    'deadline': deadline,
    'description': 'Ordered the seal, arriving Tuesday',
    'completed': completed,
    'completedTime': 0,
    'assignee': assignee,
    'sync': {'type': 'local', 'planetCode': 'gua'},
    'link': {'teams': teamId},
    'status': ?status,
  };

  void stubWalk(List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(
        '$dbUrl/tasks/_all_docs?limit=0',
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async =>
          NetworkSuccess<Map<String, dynamic>>({'total_rows': docs.length}),
    );
    when(
      () => api.getJsonObject(
        any(that: contains('tasks/_all_docs?include_docs=true')),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final doc in docs) {'id': doc['_id'], 'doc': doc},
        ],
      }),
    );
  }

  test('a task the server sent reaches the team list, though its document '
      'carries no status', () async {
    // The defect: `TeamTask.serialize` emits no `status`, `fromJson` reads
    // the missing key as `""`, and the port asked for `status = 'active'`.
    // Reverting `TeamTaskDao.watchForTeam` to that predicate fails this test
    // while everything else here still passes — the walk works and the
    // screen is empty.
    stubWalk([taskDoc('task-server-1')]);

    await repository.sync(config: config);

    final rows = await repository.watchForTeam('team-1').first;
    expect(rows.single.id, 'task-server-1');
    expect(rows.single.status, '');
    expect(rows.single.title, 'Fix the well pump');
  });

  test(
    'an archived task is excluded, matching TeamTaskDao.getByTeamId',
    () async {
      stubWalk([taskDoc('task-live'), taskDoc('task-old', status: 'archived')]);

      await repository.sync(config: config);

      final rows = await repository.watchForTeam('team-1').first;
      expect(rows.map((r) => r.id), ['task-live']);
      // Excluded from the list, not deleted — the walk prunes nothing.
      expect(await db.teamTaskDao.getById('task-old'), isNotNull);
    },
  );

  test(
    'reads teamId out of link.teams and assignee out of assignee._id',
    () async {
      stubWalk([
        taskDoc('task-1', teamId: 'team-9', assignee: {'_id': 'user-7'}),
      ]);

      await repository.sync(config: config);

      final row = await db.teamTaskDao.getById('task-1');
      expect(row!.teamId, 'team-9');
      expect(row.assignee, 'user-7');
    },
  );

  test('an empty-string assignee leaves the column null', () async {
    // `if (user.has("_id"))`. Writing `""` would make the row match
    // `pendingDeadlineTasks` for a user whose id is empty.
    stubWalk([taskDoc('task-1')]);

    await repository.sync(config: config);

    expect((await db.teamTaskDao.getById('task-1'))!.assignee, isNull);
  });

  test('skips _design documents', () async {
    stubWalk([
      {'_id': '_design/tasks'},
      taskDoc('task-1'),
    ]);

    await repository.sync(config: config);

    expect(await db.teamTaskDao.getById('_design/tasks'), isNull);
  });

  test('a re-pull does not re-fire a deadline notification', () async {
    // `isNotified` is device-local — it is on neither the document nor
    // `TeamTask.serialize` — and it is the only thing making the reminder
    // once-only. Kotlin's `fromJson` builds a fresh entity and resets it.
    stubWalk([
      taskDoc('task-1', assignee: {'_id': 'user-7'}),
    ]);
    await repository.sync(config: config);
    await repository.markNotified(['task-1']);

    await repository.sync(config: config);

    expect((await db.teamTaskDao.getById('task-1'))!.isNotified, isTrue);
  });

  test(
    'an edit made offline survives the pull that carries its old copy',
    () async {
      // The row was created here, uploaded, and edited again before the next
      // sync. The server's copy is the pre-edit one; adopting it would discard
      // the edit *and* clear the flag that makes it upload.
      await db.teamTaskDao.upsert(
        TeamTasksCompanion.insert(
          id: 'task-local-1',
          teamId: 'team-1',
          docId: const Value('task-server-1'),
          rev: const Value('1-old'),
          title: const Value('Fix the well pump today'),
          isUpdated: const Value(true),
        ),
      );

      stubWalk([taskDoc('task-server-1', title: 'Fix the well pump')]);
      await repository.sync(config: config);

      // One row, not two: resolving through `_id` finds the local row rather
      // than inserting a second under the CouchDB id.
      expect((await db.teamTaskDao.getByAnyIds(['task-server-1'])).length, 1);
      final row = await db.teamTaskDao.getById('task-local-1');
      expect(row!.title, 'Fix the well pump today');
      expect(row.isUpdated, isTrue);
      // …and it gains the rev its next upload needs.
      expect(row.rev, '2-abc');
    },
  );

  test(
    'a synced task the device already holds is updated, not duplicated',
    () async {
      await db.teamTaskDao.upsert(
        TeamTasksCompanion.insert(
          id: 'task-local-1',
          teamId: 'team-1',
          docId: const Value('task-server-1'),
          title: const Value('Fix the well pump'),
          isUpdated: const Value(false),
        ),
      );

      stubWalk([taskDoc('task-server-1', title: 'Fix the well pump (urgent)')]);
      await repository.sync(config: config);

      final rows = await repository.watchForTeam('team-1').first;
      expect(rows.length, 1);
      expect(rows.single.id, 'task-local-1');
      expect(rows.single.title, 'Fix the well pump (urgent)');
    },
  );

  test('never prunes: a task created offline survives the walk', () async {
    await repository.create(
      teamId: 'team-1',
      title: 'Draft the budget',
      description: '',
      deadline: 1800000000000,
    );

    stubWalk([taskDoc('task-server-1')]);
    await repository.sync(config: config);

    final rows = await repository.watchForTeam('team-1').first;
    expect(rows.map((r) => r.title), containsAll(<String>['Draft the budget']));
  });
}
