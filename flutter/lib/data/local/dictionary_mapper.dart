import 'package:drift/drift.dart';

// Companions are generated into app_database.g.dart, which is part of
// app_database.dart — not tables.dart.
import 'app_database.dart';

/// Port of the JSON mapping in `ui/dictionary/DictionaryActivity.kt`.
class DictionaryMapper {
  const DictionaryMapper._();

  static DictionaryEntriesCompanion? fromJson(Map<String, dynamic> json) {
    final word = _text(json['word']).trim();
    if (word.isEmpty) return null;
    final language = _text(json['language']);
    final code = _text(json['code']);

    return DictionaryEntriesCompanion.insert(
      id: Uri.encodeComponent('$language:$word:$code'),
      code: Value(code),
      language: Value(language),
      advanceCode: Value(_text(json['advance_code'])),
      word: Value(word),
      wordNormalized: word.toLowerCase(),
      meaning: Value(_text(json['meaning'])),
      definition: Value(_text(json['definition'])),
      synonym: Value(_text(json['synonym'])),
      // The upstream file and Kotlin mapper misspell this key.
      antonym: Value(_text(json['antonym'] ?? json['antonoym'])),
    );
  }

  static String _text(Object? value) => value?.toString() ?? '';
}
