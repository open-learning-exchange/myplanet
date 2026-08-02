import 'package:meta/meta.dart';

import '../core/config/server_config.dart';
import '../core/crypto/android_decrypter.dart';
import '../core/network/network_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/user_mapper.dart';

/// Why a login attempt failed. Replaces the English string literals
/// `LoginSyncManager.login` hands to `OnSyncListener.onSyncFailed`, so the
/// message can be localised at the UI layer.
enum LoginFailureReason {
  emptyCredentials,
  invalidCredentials,
  userNotFound,
  missingAuthData,
  serverError,
  network,
  notAManager,
}

@immutable
sealed class LoginResult {
  const LoginResult();
}

class LoginSuccess extends LoginResult {
  const LoginSuccess(this.user);

  final UserRow user;
}

class LoginFailure extends LoginResult {
  const LoginFailure(this.reason);

  final LoginFailureReason reason;
}

/// Port of the authentication surface of `services/sync/LoginSyncManager.kt` and
/// `repository/UserRepositoryImpl.authenticateUser`.
///
/// Both paths of the Kotlin flow are kept:
/// * [loginOnline] fetches the CouchDB `_users` document over Basic auth and
///   verifies `derived_key`/`salt`, then caches the user locally.
/// * [loginOffline] runs the identical PBKDF2 check against the cached row, so
///   a device that has synced once can sign in with no network — the property
///   the whole app exists for.
class UserRepository {
  UserRepository(this._api, this._userDao);

  final PlanetApi _api;
  final UserDao _userDao;

  /// Port of `LoginSyncManager.login`.
  Future<LoginResult> loginOnline({
    required ServerConfig config,
    required String username,
    required String password,
    bool isManagerMode = false,
  }) async {
    if (username.trim().isEmpty || password.isEmpty) {
      return const LoginFailure(LoginFailureReason.emptyCredentials);
    }

    final result = await _api.getJsonObject(
      UrlUtils.userDocUrl(config, username),
      authHeader: UrlUtils.basicAuthHeader(username, password),
    );

    switch (result) {
      case NetworkSuccess(:final data):
        return _verifyAndCache(
          doc: data,
          username: username,
          password: password,
          isManagerMode: isManagerMode,
        );
      case NetworkError(:final code):
        return LoginFailure(_reasonForStatus(code));
      case NetworkException():
        return const LoginFailure(LoginFailureReason.network);
    }
  }

  Future<LoginResult> _verifyAndCache({
    required Map<String, dynamic> doc,
    required String username,
    required String password,
    required bool isManagerMode,
  }) async {
    final derivedKey = JsonUtils.getStringOrNull('derived_key', doc);
    final salt = JsonUtils.getStringOrNull('salt', doc);
    if (derivedKey == null || salt == null) {
      return const LoginFailure(LoginFailureReason.missingAuthData);
    }

    final authenticated = AndroidDecrypter.androidDecrypter(
      username,
      password,
      derivedKey,
      salt,
    );
    if (!authenticated) {
      return const LoginFailure(LoginFailureReason.invalidCredentials);
    }

    // Port of `checkManagerAndInsert`: cache the account so the next sign-in
    // works offline.
    await _userDao.upsert(UserMapper.fromDoc(doc));

    final stored = await _userDao.getByName(username);
    if (stored == null) {
      return const LoginFailure(LoginFailureReason.userNotFound);
    }
    if (isManagerMode && !UserMapper.isManager(stored)) {
      return const LoginFailure(LoginFailureReason.notAManager);
    }
    return LoginSuccess(stored);
  }

  /// Port of `UserRepositoryImpl.authenticateUser`.
  ///
  /// Guest accounts have an empty `_id` and store their password in plaintext;
  /// everyone else is checked with the same PBKDF2 comparison as the online
  /// path.
  Future<LoginResult> loginOffline({
    required String username,
    required String password,
    bool isManagerMode = false,
  }) async {
    if (username.trim().isEmpty || password.isEmpty) {
      return const LoginFailure(LoginFailureReason.emptyCredentials);
    }

    final user = await _userDao.getByName(username);
    if (user == null) {
      return const LoginFailure(LoginFailureReason.userNotFound);
    }

    final isGuest = user.couchId == null || user.couchId!.isEmpty;
    final authenticated = isGuest
        ? user.password == password
        : AndroidDecrypter.androidDecrypter(
            username,
            password,
            user.derivedKey,
            user.salt,
          );

    if (!authenticated) {
      return const LoginFailure(LoginFailureReason.invalidCredentials);
    }
    if (isManagerMode && !UserMapper.isManager(user)) {
      return const LoginFailure(LoginFailureReason.notAManager);
    }
    return LoginSuccess(user);
  }

  /// The account picker on the login screen.
  Future<List<UserRow>> getSavedUsers() => _userDao.getSavedUsers();

  Future<bool> hasAnyUser() async => await _userDao.count() > 0;

  static LoginFailureReason _reasonForStatus(int? code) {
    switch (code) {
      case 401:
        return LoginFailureReason.invalidCredentials;
      case 404:
        return LoginFailureReason.userNotFound;
      default:
        return LoginFailureReason.serverError;
    }
  }
}
