import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../repository/dictionary_repository.dart';
import 'app_providers.dart';

/// The state `ui/dictionary/DictionaryActivity.kt` keeps in fields.
///
/// The Activity queries Realm directly — it is the last raw-Realm caller in the
/// Kotlin app. Here the lookup goes through `DictionaryRepository`, so the
/// download-and-import path can report failure ([DictionaryState.importFailed])
/// instead of leaving a half-populated table behind.
class DictionaryState {
  const DictionaryState({
    this.count = 0,
    this.result,
    this.loading = false,
    this.importFailed = false,
  });

  final int count;
  final DictionaryRow? result;
  final bool loading;
  final bool importFailed;

  DictionaryState copyWith({
    int? count,
    DictionaryRow? result,
    bool clearResult = false,
    bool? loading,
    bool? importFailed,
  }) {
    return DictionaryState(
      count: count ?? this.count,
      result: clearResult ? null : result ?? this.result,
      loading: loading ?? this.loading,
      importFailed: importFailed ?? this.importFailed,
    );
  }
}

class DictionaryNotifier extends AsyncNotifier<DictionaryState> {
  @override
  Future<DictionaryState> build() async {
    final count = await ref.watch(dictionaryRepositoryProvider).localCount();
    return DictionaryState(count: count);
  }

  Future<bool> search(String query) async {
    final result = await ref.read(dictionaryRepositoryProvider).find(query);
    state = AsyncData(
      state.requireValue.copyWith(result: result, clearResult: result == null),
    );
    return result != null;
  }

  Future<void> download() async {
    final current = state.valueOrNull ?? const DictionaryState();
    state = AsyncData(current.copyWith(loading: true, importFailed: false));
    final result = await ref.read(dictionaryRepositoryProvider).download();
    state = AsyncData(
      current.copyWith(
        count: result is DictionaryImportSuccess ? result.count : null,
        loading: false,
        importFailed: result is DictionaryImportFailure,
      ),
    );
  }
}

final dictionaryProvider =
    AsyncNotifierProvider<DictionaryNotifier, DictionaryState>(
      DictionaryNotifier.new,
    );
