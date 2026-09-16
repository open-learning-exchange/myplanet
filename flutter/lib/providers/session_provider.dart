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
    await _logLogin(user);
    state = AsyncData(user);
  }

  /// Port of `UserSessionManager.onLoginAsync`, which every login path calls
  /// (`LoginActivity`, `SyncActivity`, and the guest path) once credentials
  /// check out.
  ///
  /// Failure is swallowed: the Kotlin runs this on `applicationScope` with its
  /// own try/catch, so a failed write never blocks the sign-in. Losing an
  /// activity row costs a number on the dashboard; failing the login would cost
  /// the session.
  Future<void> _logLogin(UserRow user) async {
    final userName = user.name;
    if (userName == null || userName.isEmpty) return;
    try {
      await ref
          .read(activitiesRepositoryProvider)
          .logLogin(
            id: 'login:${DateTime.now().microsecondsSinceEpoch}',
            userId: user.id,
            userName: userName,
            parentCode: user.parentCode,
            planetCode: user.planetCode,
            loginTime: DateTime.now().millisecondsSinceEpoch,
          );
    } catch (_) {
      // Deliberately ignored — see above.
    }
  }

  Future<void> signOut() async {
    // Stamps the logout time before the session goes, mirroring
    // `UserSessionManager.logoutAsync`. Swallowed for the same reason as the
    // login write: a failed stamp must not strand the user in a signed-in
    // state they asked to leave.
    try {
      await ref
          .read(activitiesRepositoryProvider)
          .logLogout(DateTime.now().millisecondsSinceEpoch);
    } catch (_) {
      // Deliberately ignored.
    }
    await ref.read(planetPrefsProvider).clearSession();
    state = const AsyncData(null);
  }

  /// Offline-first profile update, porting the local half of
  /// `UserProfileViewModel.updateUserProfile`.
  ///
  /// The updated row is committed before it is published to the UI, and
  /// `isUpdated` is set so [UserUploader.queuePending] picks it up on the next
  /// drain. A later profile-upload slice can sync the same cached row without
  /// making editing depend on connectivity.
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
    final current = state.value;
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
    // old text in place. `isUpdated` flags the row for the user-document upload.
    await ref
        .read(userDaoProvider)
        .upsert(updated.copyWith(isUpdated: true).toCompanion(false));
    state = AsyncData(updated);
    await _queueUserUpload();
  }

  /// Port of the photo half of `UserProfileViewModel.updateUserProfile` /
  /// `UserRepositoryImpl.updateUserImage` — stores the picked image path and
  /// flags the row for upload, exactly as a profile-field edit does.
  Future<void> setUserImage(String path) async {
    final current = state.value;
    if (current == null) return;
    final updated = current.copyWith(userImage: Value(path));
    await ref
        .read(userDaoProvider)
        .upsert(updated.copyWith(isUpdated: true).toCompanion(false));
    state = AsyncData(updated);
    await _queueUserUpload();
  }

  /// Enqueues the just-edited user document for upload.
  ///
  /// Best-effort, like the other write sites: a queue failure must not undo
  /// the edit. The drain on app resume is the reliable fallback — the row is
  /// dirty, so the next drain picks it up even if this enqueue throws.
  Future<void> _queueUserUpload() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return;
    try {
      await ref.read(userUploaderProvider).queuePending(config: config);
    } catch (_) {
      // Deliberately ignored — the dirty flag survives, so the next drain
      // retry carries the edit.
    }
  }
}

String? _nullableText(String value) {
  final trimmed = value.trim();
  return trimmed.isEmpty ? null : trimmed;
}

final sessionProvider = AsyncNotifierProvider<SessionNotifier, UserRow?>(
  SessionNotifier.new,
);

/// The signed-in user, **resolved** rather than read.
///
/// This is the port's standing answer to its most-repeated defect: *a provider
/// a screen reads but never watches is null.* An action class is a plain
/// `Provider`, so its `ref` never *watches* `sessionProvider`; in Riverpod 3
/// `AsyncValue.value` is "previous value, else null"
/// (`async_value.dart:557`), so `ref.read(sessionProvider).value` is null on
/// any pass that reaches it before something else has resolved the provider.
/// Every caller then took its silent `return` branch — and because the branch
/// is the same one a genuinely signed-out user takes, nothing logged, nothing
/// told the user, and the action simply did not happen.
///
/// Found by hand five times before it was swept: `take_exam_screen` (Phase
/// 100, a graded exam attempt discarded with no dialog, no snackbar and no
/// row), `add_resource_screen` (102), `public_survey_screen`'s `_submit` and
/// `_leave` (103), and `take_survey_screen` (104/105, found separately by two
/// lanes in one round). It is latent in the *app* only because the router
/// holds a `ref.listen(sessionProvider, …)` — a property of one caller, not a
/// guarantee the callee can rely on, and one `background_entrypoint.dart`
/// does not provide at all.
///
/// **The `await` is inside the `try`, and the rejection is swallowed.** Both
/// are deliberate. `.future` can reject where `.value` could only be null, so
/// an await outside the `try` reproduces the same silence from a new trigger
/// (the amendment Phase 100's own fix needed on harvest). Swallowing to null
/// then keeps each caller's existing contract — it reports failure the way it
/// always did — rather than escaping as an uncaught async error out of a
/// fire-and-forget `onPressed`, which is strictly worse than the `false` it
/// would replace. A caller that wants to *distinguish* "not signed in" from
/// "session failed to load" must read `sessionProvider.future` itself.
///
/// `test/core/unwatched_provider_reads_test.dart` is the guard that keeps new
/// occurrences of the read shape from landing.
Future<UserRow?> resolveSession(Ref ref) =>
    _resolveSession(() => ref.read(sessionProvider.future));

/// [resolveSession] for a caller holding a [ProviderContainer] rather than a
/// `Ref` — `search_activity_providers.dart`, whose three entry points are
/// called from a screen's `dispose()`, where the widget's own `ref` is
/// already torn down.
Future<UserRow?> resolveSessionIn(ProviderContainer container) =>
    _resolveSession(() => container.read(sessionProvider.future));

/// The signed-in user's id — see [resolveSession] for why the rejection is
/// swallowed.
Future<String?> resolveSessionUserId(Ref ref) async =>
    (await resolveSession(ref))?.id;

/// The `read` happens *inside* the `try`, not just the `await`: `ref.read`
/// can throw synchronously — a `ProviderException`, since Riverpod 3 wraps at
/// the `ref.watch`/`ref.read` boundary — where the `.value` it replaces could
/// only ever have been null.
Future<UserRow?> _resolveSession(Future<UserRow?> Function() read) async {
  try {
    return await read();
  } catch (_) {
    return null;
  }
}
