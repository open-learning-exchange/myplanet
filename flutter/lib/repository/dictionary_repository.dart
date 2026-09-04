import '../core/network/network_result.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/dictionary_mapper.dart';

/// Offline-first port of `ui/dictionary/DictionaryActivity.kt`.
class DictionaryRepository {
  DictionaryRepository(this._api, this._dao);

  static const sourceUrl = 'http://157.245.241.39:8000/output.json';

  final PlanetApi _api;
  final DictionaryDao _dao;

  Future<int> localCount() => _dao.count();

  Future<DictionaryRow?> find(String word) => _dao.findByWord(word);

  Future<DictionaryImportResult> download() async {
    final response = await _api.getJsonList(sourceUrl);
    if (response is! NetworkSuccess<List<dynamic>>) {
      return const DictionaryImportFailure();
    }

    final entries = response.data
        .whereType<Map<String, dynamic>>()
        .map(DictionaryMapper.fromJson)
        .whereType<DictionaryEntriesCompanion>()
        .toList(growable: false);
    if (entries.isEmpty) return const DictionaryImportFailure();

    await _dao.replaceAll(entries);
    return DictionaryImportSuccess(entries.length);
  }
}

sealed class DictionaryImportResult {
  const DictionaryImportResult();
}

class DictionaryImportSuccess extends DictionaryImportResult {
  const DictionaryImportSuccess(this.count);
  final int count;
}

class DictionaryImportFailure extends DictionaryImportResult {
  const DictionaryImportFailure();
}
