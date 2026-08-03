import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/team_tasks_repository.dart';

void main() {
  late AppDatabase database;
  late TeamTasksRepository repository;
  setUp(() {
    database = AppDatabase.memory();
    repository = TeamTasksRepository(
      database.teamTaskDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(500),
      createId: () => 'local-task',
    );
  });
  tearDown(() => database.close());

  test('creates, edits, completes and serializes an offline task', () async {
    expect(
      await repository.create(
        teamId: 'team-1',
        title: ' Build ',
        description: 'Do it',
        deadline: 100,
        assignee: 'user-1',
      ),
      'local-task',
    );
    expect(
      await repository.update(
        'local-task',
        title: 'Ship',
        description: 'Now',
        deadline: 200,
      ),
      isTrue,
    );
    expect(await repository.setCompleted('local-task', true), isTrue);
    final row = await repository.getById('local-task');
    expect(row?.completedTime, 500);
    expect(await repository.pending(), hasLength(1));
    expect(TeamTasksRepository.serialize(row!, planetCode: 'p')['link'], {
      'teams': 'team-1',
    });
    expect(TeamTasksRepository.serialize(row)['assignee'], '');
  });

  test('adopts CouchDB identity and stops being pending', () async {
    await repository.create(
      teamId: 'team-1',
      title: 'Task',
      description: '',
      deadline: 0,
    );
    await repository.markUploaded('local-task', 'remote-task', '1-a');
    final row = await repository.getById('local-task');
    expect(row?.docId, 'remote-task');
    expect(row?.rev, '1-a');
    expect(await repository.pending(), isEmpty);
  });

  test('rejects invalid tasks and supports deletion', () async {
    expect(
      await repository.create(
        teamId: '',
        title: 'x',
        description: '',
        deadline: 0,
      ),
      isNull,
    );
    expect(
      await repository.create(
        teamId: 't',
        title: ' ',
        description: '',
        deadline: 0,
      ),
      isNull,
    );
    await repository.create(
      teamId: 't',
      title: 'x',
      description: '',
      deadline: 0,
    );
    expect(await repository.delete('local-task'), isTrue);
    expect(await repository.getById('local-task'), isNull);
  });
}
