import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/dictionary_mapper.dart';

void main() {
  test('maps the legacy antonoym key and normalizes lookup text', () {
    final row = DictionaryMapper.fromJson({
      'word': 'Learning',
      'language': 'en',
      'code': '42',
      'definition': 'The acquisition of knowledge.',
      'synonym': 'education',
      'antonoym': 'ignorance',
    });

    expect(row, isNotNull);
    expect(row!.word.value, 'Learning');
    expect(row.wordNormalized.value, 'learning');
    expect(row.antonym.value, 'ignorance');
    expect(row.id.value, isNotEmpty);
  });

  test('accepts the correctly spelled antonym key', () {
    final row = DictionaryMapper.fromJson({
      'word': 'open',
      'antonym': 'closed',
    });

    expect(row!.antonym.value, 'closed');
  });

  test('rejects entries without a word', () {
    expect(DictionaryMapper.fromJson({'definition': 'invalid'}), isNull);
  });
}
