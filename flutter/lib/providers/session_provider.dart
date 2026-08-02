import 'package:flutter_riverpod/flutter_riverpod.dart';

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
}

final sessionProvider = AsyncNotifierProvider<SessionNotifier, UserRow?>(
  SessionNotifier.new,
);
