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

  /// Port of `UserRepositoryImpl.uploadNewUser`.
  ///
  /// Uploads a newly registered member to the CouchDB `_users` database so
  /// they can sign in on other devices and Planet knows they exist.
  ///
  /// After a successful upload, fetches the created document to retrieve
  /// the server-assigned `derived_key`, `salt`, `password_scheme`, and
  /// `iterations`, then updates the local row so subsequent logins use
  /// PBKDF2 verification.
  ///
  /// Returns `true` on success, `false` on failure. On failure the local
  /// account still exists (the BecomeMemberScreen wrote it), but the member
  /// cannot log in on another device until the account syncs.
  Future<bool> uploadNewUser({
    required String localId,
    required ServerConfig config,
    required String username,
    required String password,
  }) async {
    // Build the user document. Excludes _id/_rev because this is a new user;
    // CouchDB will assign them.
    final doc = _buildNewUserDoc(
      username: username,
      password: password,
      planetCode: config.code,
      parentCode: config.parentCode,
    );

    final url = UrlUtils.userDocUrl(config, username);

    // PUT the user document (no auth header — CouchDB accepts creation without it)
    final putResult = await _api.putJsonObject(url, doc);

    String? serverId;
    String? serverRev;
    switch (putResult) {
      case NetworkSuccess(data: final data):
        serverId = data['id'] as String?;
        serverRev = data['rev'] as String?;
      case NetworkError() || NetworkException():
        // PUT failed — the account exists locally but not on the server.
        return false;
    }

    if (serverId == null || serverId.isEmpty) {
      return false;
    }

    // Fetch the created document to get the server-side security data.
    final authHeader = UrlUtils.basicAuthHeader(username, password);
    final fetchResult = await _api.getJsonObject(
      '${UrlUtils.dbUrl(config)}/_users/$serverId',
      authHeader: authHeader,
    );

    String? passwordScheme;
    String? derivedKey;
    String? salt;
    String? iterations;

    switch (fetchResult) {
      case NetworkSuccess(data: final data):
        passwordScheme = JsonUtils.getStringOrNull('password_scheme', data);
        derivedKey = JsonUtils.getStringOrNull('derived_key', data);
        salt = JsonUtils.getStringOrNull('salt', data);
        iterations = JsonUtils.getStringOrNull('iterations', data);
      case NetworkError() || NetworkException():
        // Security data not retrieved — login will still work via server fetch.
        break;
    }

    // Update the local row with server data.
    await _userDao.updateUserSecurityData(
      localId: localId,
      couchId: serverId,
      rev: serverRev,
      passwordScheme: passwordScheme,
      derivedKey: derivedKey,
      salt: salt,
      iterations: iterations,
    );

    return true;
  }

  /// Builds the CouchDB user document for a new member.
  ///
  /// Matches the shape `UserEntity.serialize()` produces when `_id` is empty.
  Map<String, dynamic> _buildNewUserDoc({
    required String username,
    required String password,
    required String? planetCode,
    required String? parentCode,
  }) {
    return {
      'name': username,
      'password': password,
      'type': 'user',
      'roles': <String>[],
      'isUserAdmin': false,
      'joinDate': DateTime.now().millisecondsSinceEpoch,
      'planetCode': planetCode,
      'parentCode': parentCode,
    };
  }
}
