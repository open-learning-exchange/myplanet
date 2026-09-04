import 'package:drift/drift.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../repository/achievements_repository.dart';
import 'app_providers.dart';
import 'session_provider.dart';

/// The session user's achievements ledger row — the
/// `userRepository.getUserModel() → getAchievementData` chain the Kotlin
/// screen wires together each load. The ledger row is auto-initialized here
/// so the edit form always opens against a row.
final achievementEntryProvider = FutureProvider<AchievementRow?>((ref) async {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) return null;
  final id = AchievementsRepository.idFor(user.id, user.planetCode ?? '');
  return ref.read(achievementsRepositoryProvider).getOrInitialize(id);
});

/// The save path `EditAchievementFragment.btnUpdate` runs: the ledger update,
/// the partial user profile fields (`updateProfileFields`), and a queued
/// upload.
class AchievementActions {
  const AchievementActions(this.ref);
  final Ref ref;

  /// Port of the `btnUpdate` listener in `EditAchievementFragment` — the
  /// ledger write, then the partial user update marked `isUpdated`, then a
  /// queued ledger upload. The uploader scan commits before the drain, so
  /// ordering matches the Kotlin repo-write → user-write → upload chain.
  Future<void> save({
    required AchievementInput input,
    String? firstName,
    String? lastName,
    String? middleName,
    String? birthPlace,
    String? birthDate,
  }) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    final repository = ref.read(achievementsRepositoryProvider);
    final id = AchievementsRepository.idFor(user.id, user.planetCode ?? '');

    await repository.update(id, input);

    // `UserRepositoryImpl.updateProfileFields`: only the provided keys shift,
    // `isUpdated` flags the row for the user-document upload.
    final userDao = ref.read(userDaoProvider);
    await userDao.upsert(
      user
          .toCompanion(false)
          .copyWith(
            firstName: Value(firstName ?? user.firstName),
            lastName: Value(lastName ?? user.lastName),
            middleName: Value(
              middleName != null && middleName.isNotEmpty
                  ? middleName
                  : user.middleName,
            ),
            birthPlace: Value(
              birthPlace != null && birthPlace.isNotEmpty
                  ? birthPlace
                  : user.birthPlace,
            ),
            dob: Value(birthDate ?? user.dob),
            isUpdated: const Value(true),
          ),
    );

    final config = ref.read(serverConfigProvider);
    if (config == null) return;
    await ref.read(achievementsUploaderProvider).queuePending(config: config);
  }
}

final achievementActionsProvider = Provider(AchievementActions.new);
