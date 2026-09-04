import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/dictionary_mapper.dart';

void main() {
  late AppDatabase database;

  setUp(() => database = AppDatabase.memory());
  tearDown(() => database.close());

  test('replaces and searches dictionary entries case-insensitively', () async {
    final first = DictionaryMapper.fromJson({
      'word': 'Planet',
      'definition': 'A world orbiting a star.',
    })!;
    await database.dictionaryDao.replaceAll([first]);

    expect(await database.dictionaryDao.count(), 1);
    final result = await database.dictionaryDao.findByWord('  pLaNeT ');
    expect(result?.definition, 'A world orbiting a star.');

    final replacement = DictionaryMapper.fromJson({
      'word': 'Community',
      'definition': 'People learning together.',
    })!;
    await database.dictionaryDao.replaceAll([replacement]);

    expect(await database.dictionaryDao.count(), 1);
    expect(await database.dictionaryDao.findByWord('Planet'), isNull);
    expect(await database.dictionaryDao.findByWord('community'), isNotNull);
  });
}
