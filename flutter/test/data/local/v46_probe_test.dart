import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

void main() {
  late AppDatabase database;

  setUp(() {
    database = AppDatabase(NativeDatabase.memory());
  });
  tearDown(() => database.close());

  test('a v45 team_tasks table really gains sync and link at v46', () async {
    await database.customStatement('SELECT 1');
    // Rebuild `team_tasks` in its v45 shape: no `sync`, no `link`.
    await database.customStatement('DROP TABLE team_tasks');
    await database.customStatement(
      'CREATE TABLE team_tasks ('
      'id TEXT NOT NULL, _id TEXT NULL, _rev TEXT NULL, title TEXT NULL, '
      'description TEXT NULL, team_id TEXT NOT NULL, assignee TEXT NULL, '
      'deadline INTEGER NOT NULL DEFAULT 0, '
      'completed_time INTEGER NOT NULL DEFAULT 0, '
      "status TEXT NOT NULL DEFAULT 'active', "
      'completed INTEGER NOT NULL DEFAULT 0, '
      'is_updated INTEGER NOT NULL DEFAULT 0, '
      'is_notified INTEGER NOT NULL DEFAULT 0, '
      'PRIMARY KEY (id))',
    );
    await database.customStatement(
      "INSERT INTO team_tasks (id, team_id, title, is_updated) "
      "VALUES ('task-1', 'team-1', 'Older task', 1)",
    );

    final migrator = database.createMigrator();
    await database.migration.onUpgrade(migrator, 45, database.schemaVersion);

    final info = await database
        .customSelect('PRAGMA table_info(team_tasks)')
        .get();
    final names = info.map((r) => r.read<String>('name')).toSet();
    expect(names, contains('sync'));
    expect(names, contains('link'));

    // And the column has to be writable, not merely readable-as-null.
    await database.teamTaskDao.upsert(
      TeamTasksCompanion.insert(
        id: 'task-2',
        teamId: 'team-1',
        sync: const Value('{"type":"local","planetCode":"gua"}'),
      ),
    );
    expect(
      (await database.teamTaskDao.getById('task-2'))?.sync,
      '{"type":"local","planetCode":"gua"}',
    );
  });
}
