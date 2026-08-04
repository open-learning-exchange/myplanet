import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// The state `ui/personals/PersonalsViewModel.kt` holds.
///
/// `PersonalActions` replaces the ViewModel's `viewModelScope.launch` wrappers:
/// Riverpod already scopes the repository call to the widget that reads it, so
/// the actions only need to forward the signed-in user.
final personalsProvider = StreamProvider<List<PersonalRow>>((ref) {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) return Stream.value(const []);
  return ref.watch(personalsRepositoryProvider).watch(user.id);
});

class PersonalActions {
  const PersonalActions(this.ref);
  final Ref ref;

  Future<void> create({required String title, String? description}) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    await ref
        .read(personalsRepositoryProvider)
        .create(
          userId: user.id,
          userName: user.name,
          title: title,
          description: description,
        );
  }

  Future<void> update({
    required String id,
    required String title,
    String? description,
  }) => ref
      .read(personalsRepositoryProvider)
      .update(id: id, title: title, description: description);

  Future<void> delete(String id) async {
    await ref.read(personalsRepositoryProvider).delete(id);
  }
}

final personalActionsProvider = Provider(PersonalActions.new);
