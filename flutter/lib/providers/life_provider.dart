import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// The state `ui/life/LifeFragment.kt` and `LifeAdapter.kt` share.
///
/// Seeding runs before the first read, mirroring the Fragment's behaviour of
/// creating the default My life rows for a user who has none yet.
final lifeItemsProvider = StreamProvider<List<MyLifeRow>>((ref) async* {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) {
    yield const [];
    return;
  }
  final repository = ref.watch(lifeRepositoryProvider);
  await repository.seed(user.id);
  yield* repository.watch(user.id);
});

class LifeActions {
  const LifeActions(this.ref);
  final Ref ref;

  Future<void> setVisibility(MyLifeRow row, {required bool visible}) =>
      ref.read(lifeRepositoryProvider).setVisibility(row.id, visible: visible);

  Future<void> reorder(List<MyLifeRow> rows) =>
      ref.read(lifeRepositoryProvider).reorder(rows);
}

final lifeActionsProvider = Provider(LifeActions.new);
