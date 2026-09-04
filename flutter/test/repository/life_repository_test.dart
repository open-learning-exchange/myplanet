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
}
