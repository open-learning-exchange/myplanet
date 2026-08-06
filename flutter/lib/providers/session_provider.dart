import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:drift/drift.dart';

import '../data/local/app_database.dart';
import 'app_providers.dart';

/// The signed-in user, replacing `services/UserSessionManager.kt`.
///
/// [build] restores the session on cold start from the persisted user id, so a
/// returning user lands straight on the resources list — the same behaviour
/// `DashboardActivity` gets from `UserSessionManager.getUserModel()`.
class SessionNotifier extends AsyncNotifier<UserRow?> {
  @override
  Future<UserRow?> build() async {
    final userId = ref.watch(planetPrefsProvider).loggedInUserId;
    if (userId == null) return null;
    return ref.watch(userDaoProvider).getById(userId);
  }

  Future<void> signIn(UserRow user, {String? password}) async {
    final prefs = ref.read(planetPrefsProvider);
    await prefs.setLoggedInUserId(user.id);
    if (password != null) {
      await prefs.savePassword(password);
    }
    state = AsyncData(user);
  }

  Future<void> signOut() async {
    await ref.read(planetPrefsProvider).clearSession();
    state = const AsyncData(null);
  }

  /// Offline-first profile update, porting the local half of
  /// `UserProfileViewModel.updateUserProfile`.
  ///
  /// The updated row is committed before it is published to the UI. A later
  /// profile-upload slice can sync the same cached row without making editing
  /// depend on connectivity.
  Future<void> updateProfile({
    required String firstName,
    required String middleName,
    required String lastName,
    required String email,
    required String phoneNumber,
    required String level,
    required String language,
    required String gender,
    required String dateOfBirth,
  }) async {
    final current = state.valueOrNull;
    if (current == null) return;

    final updated = current.copyWith(
      firstName: Value(_nullableText(firstName)),
      middleName: Value(_nullableText(middleName)),
      lastName: Value(_nullableText(lastName)),
      email: Value(_nullableText(email)),
      phoneNumber: Value(_nullableText(phoneNumber)),
      level: Value(_nullableText(level)),
      language: Value(_nullableText(language)),
      gender: Value(_nullableText(gender)),
      dob: Value(_nullableText(dateOfBirth)),
    );
    // Every column, not `nullToAbsent`: clearing a field (blanking a phone
    // number, say) has to null the column, and an absent value would leave the
    // old text in place.
    await ref.read(userDaoProvider).upsert(updated.toCompanion(false));
    state = AsyncData(updated);
  }
}

String? _nullableText(String value) {
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

final sessionProvider = AsyncNotifierProvider<SessionNotifier, UserRow?>(
  SessionNotifier.new,
);
