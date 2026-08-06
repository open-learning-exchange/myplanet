import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/personals_repository.dart';

void main() {
  late AppDatabase database;
  late PersonalsRepository repository;
  var nextId = 0;

  setUp(() {
    database = AppDatabase.memory();
    nextId = 0;
    repository = PersonalsRepository(
      database.personalDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(1000 + nextId),
      createId: () => 'personal-${nextId++}',
    );
  });
  tearDown(() => database.close());

  test('creates, edits, orders, and deletes offline personal items', () async {
    await repository.create(
      userId: 'user-1',
      userName: 'learner',
      title: ' First note ',
      description: ' private ',
    );
    await repository.create(
      userId: 'user-1',
      userName: 'learner',
      title: 'Second note',
    );

    var rows = await repository.watch('user-1').first;
    expect(rows.map((row) => row.id), ['personal-1', 'personal-0']);
    expect(rows.last.title, 'First note');
    expect(rows.last.description, 'private');
    expect(rows.every((row) => !row.isUploaded), isTrue);

    await repository.update(
      id: 'personal-0',
      title: 'Updated note',
      description: '',
    );
    rows = await repository.watch('user-1').first;
    final updated = rows.singleWhere((row) => row.id == 'personal-0');
    expect(updated.title, 'Updated note');
    expect(updated.description, isNull);

    expect(await repository.pendingUploads('user-1'), hasLength(2));
    await repository.delete('personal-1');
    expect(await repository.watch('user-1').first, hasLength(1));
  });

  test('enforces case-insensitive title uniqueness per user', () async {
    await repository.create(userId: 'user-1', userName: null, title: 'Journal');

    await expectLater(
      repository.create(userId: 'user-1', userName: null, title: ' journal '),
      throwsA(isA<DuplicatePersonalTitle>()),
    );
    await repository.create(userId: 'user-2', userName: null, title: 'Journal');
    expect(await repository.watch('user-2').first, hasLength(1));
  });
}
