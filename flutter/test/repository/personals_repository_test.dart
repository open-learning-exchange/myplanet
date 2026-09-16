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

  /// Harvested from master `7a9bb12`, which gave `PersonalDao.kt:24` an
  /// `ORDER BY date DESC, title COLLATE NOCASE ASC` where it had none at all.
  ///
  /// The tie-break is the point, and it is the common case rather than the
  /// rare one: a note's date is the day it was written, so every note written
  /// on one day ties. Without the second term SQLite picks, and one device's
  /// list is not another's.
  ///
  /// Two decoys, both found by mutating the fix rather than by writing the
  /// test:
  ///
  /// * `zebra` is seeded **first** so insertion order and title order
  ///   disagree — otherwise the assertion holds whether or not the sort
  ///   exists at all.
  /// * The titles straddle the ASCII case boundary (`Banana` is `B`=66,
  ///   `apple` is `a`=97). A first cut used `Apple`/`mango`/`zebra`, which
  ///   sort **identically** with and without `COLLATE NOCASE`, so making the
  ///   tie-break case-sensitive left the suite green and the collation half
  ///   of the claim was pinned by nothing.
  test('orders same-date notes by title, case-insensitively', () async {
    for (final title in ['zebra', 'apple', 'Banana']) {
      await database.personalDao.upsert(
        PersonalEntriesCompanion.insert(
          id: 'p-$title',
          userId: 'user-1',
          title: title,
          titleNormalized: title.toLowerCase(),
          date: 5000,
        ),
      );
    }

    final rows = await repository.watch('user-1').first;
    expect(rows.map((row) => row.title), ['apple', 'Banana', 'zebra']);
  });

  test('a newer date still outranks the title tie-break', () async {
    await database.personalDao.upsert(
      PersonalEntriesCompanion.insert(
        id: 'p-old',
        userId: 'user-1',
        title: 'Apple',
        titleNormalized: 'apple',
        date: 1000,
      ),
    );
    await database.personalDao.upsert(
      PersonalEntriesCompanion.insert(
        id: 'p-new',
        userId: 'user-1',
        title: 'zebra',
        titleNormalized: 'zebra',
        date: 9000,
      ),
    );

    final rows = await repository.watch('user-1').first;
    expect(rows.map((row) => row.title), ['zebra', 'Apple']);
  });

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
