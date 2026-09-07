import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/life_repository.dart';

void main() {
  late AppDatabase database;
  late LifeRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = LifeRepository(database.myLifeDao);
  });
  tearDown(() => database.close());

  test('seeds once and persists visibility and ordering', () async {
    await repository.seed('user-1');
    await repository.seed('user-1');
    var rows = await repository.watch('user-1').first;

    expect(rows, hasLength(LifeRepository.defaultFeatures.length));
    expect(rows.map((row) => row.feature), LifeRepository.defaultFeatures);
    expect(rows.every((row) => row.isVisible), isTrue);

    await repository.setVisibility(rows.first.id, visible: false);
    rows = await repository.watch('user-1').first;
    expect(rows.first.isVisible, isFalse);

    final reversed = rows.reversed.toList();
    await repository.reorder(reversed);
    rows = await repository.watch('user-1').first;
    expect(rows.map((row) => row.id), reversed.map((row) => row.id));
  });

  test('keeps different users isolated', () async {
    await repository.seed('user-1');
    await repository.seed('user-2');

    final first = await repository.watch('user-1').first;
    final second = await repository.watch('user-2').first;
    expect(
      first
          .map((row) => row.id)
          .toSet()
          .intersection(second.map((row) => row.id).toSet()),
      isEmpty,
    );
  });

  test('a guest-bucket row is not visible to a signed-in user', () async {
    // Why upstream `fdf474d` needs no port. Kotlin's `MyLifeDao` predicate used
    // to be `(:userId IS NULL OR userId IS NULL OR userId = :userId)`, which
    // for a *real* id also matched rows with a null `userId` — so a signed-in
    // user saw the shared/guest rows as well as their own. That commit
    // tightened it to an exact match for a real id and a
    // null/empty/`"--"` bucket for no user, and added a `normalizeUserId`
    // collapsing all three placeholder shapes.
    //
    // The port never had the loose predicate — `MyLifeDao.watchForUser` has
    // always been `row.userId.equals(userId)` — and it cannot produce the
    // placeholders either: `"--"` is a Kotlin *preferences* sentinel
    // (`sharedPrefManager.getUserId().ifEmpty { "--" }`), while a guest here
    // carries a real `guest_<username>` row id. This pins the reader's half of
    // that: even with the placeholder rows present, they stay out.
    await repository.seed('user-1');
    await database.myLifeDao.seedIfEmpty('--', [
      MyLifeEntriesCompanion.insert(
        id: '--:health',
        feature: 'health',
        userId: '--',
        weight: 0,
      ),
    ]);
    await database.myLifeDao.seedIfEmpty('', [
      MyLifeEntriesCompanion.insert(
        id: ':health',
        feature: 'health',
        userId: '',
        weight: 0,
      ),
    ]);

    final rows = await repository.watch('user-1').first;

    expect(rows.map((row) => row.userId), everyElement('user-1'));
    expect(rows, hasLength(LifeRepository.defaultFeatures.length));
  });
}
