import 'package:meta/meta.dart';

import '../core/config/server_config.dart';
import '../core/crypto/android_decrypter.dart';
import '../core/crypto/health_cipher.dart';
import '../core/network/network_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/text_utils.dart';
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

/// Localised strings [UserRepository.validateUsername] resolves to. Keeping the
/// strings in a value object (rather than passing `BuildContext` into the
/// repository) lets the repository stay a plain data/service class that the
/// widget layer feeds localisations into.
class UsernameValidationMessages {
  const UsernameValidationMessages({
    required this.cannotBeEmpty,
    required this.invalid,
    required this.mustStartWithLetterOrNumber,
    required this.onlyLettersNumbers,
    required this.taken,
  });

  final String cannotBeEmpty;
  final String invalid;
  final String mustStartWithLetterOrNumber;
  final String onlyLettersNumbers;
  final String taken;
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

    // `checkManagerAndInsert` (`LoginSyncManager.kt:167-175`) tests the
    // **document** and returns before `saveUser`, so a manager-mode sign-in by
    // a non-manager caches nothing. Reading the stored row instead and
    // rejecting afterwards left an account behind that the Kotlin declines to
    // keep.
    if (isManagerMode && !UserMapper.docIsManager(doc)) {
      return const LoginFailure(LoginFailureReason.notAManager);
    }

    // Port of `checkManagerAndInsert`'s `saveUser`: cache the account so the
    // next sign-in works offline.
    final stored = await _cacheUserDoc(doc);
    if (stored == null) {
      return const LoginFailure(LoginFailureReason.userNotFound);
    }
    return LoginSuccess(stored);
  }

  /// Port of `buildUserFromJson` + `upsertUser`
  /// (`UserRepositoryImpl.kt:292-322`).
  ///
  /// Resolves the row the document belongs to *before* writing it, so a
  /// document whose `_id` already sits in some row's `couchId` updates that
  /// row instead of adding a second one keyed on the id. See
  /// [UserMapper.fromDoc] for why a duplicate here costs a member their health
  /// records.
  ///
  /// The lookup is skipped for a document with no `_id`, matching Kotlin:
  /// there the id it searches by is a freshly minted UUID, which can never
  /// match a stored row.
  ///
  /// Returns the stored row, re-read by the key it was written under —
  /// `upsertUser` ends in `userDao.getById(entity.id)`. Re-reading by *name*
  /// is what this used to do, and a name carries no unique constraint: two
  /// planets can hold an `ada`, and `LIMIT 1` then picks the session user by
  /// scan order.
  Future<UserRow?> _cacheUserDoc(Map<String, dynamic> doc) async {
    final couchId = JsonUtils.getString('_id', doc);
    final existing = couchId.isEmpty ? null : await _userDao.getById(couchId);
    final companion = UserMapper.fromDoc(doc, existing: existing);
    await _userDao.upsert(companion);
    return _userDao.getById(companion.id.value);
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

  /// Port of `UserRepositoryImpl.validateUsername`. Returns a localised error
  /// string when [username] is rejected, or `null` when it is usable. The
  /// [messages] argument carries the localised strings so the repository stays
  /// free of `BuildContext` — the caller (UI) owns localisation, mirroring the
  /// Kotlin `context.getString(...)` resolution that lives at the call site.
  ///
  /// The Kotlin blocklist relies on `Character.isLetter` plus an ICU
  /// `Normalizer.NFD` pass to reject accented Latin letters; Dart has no
  /// pure-Dart NFD normaliser, so the rule here is the stricter (and
  /// equivalent-in-intent) "ASCII letter, digit, `_`, `.`, `-` only" — exactly
  /// what the Kotlin user-facing message advertises.
  Future<String?> validateUsername(
    String username,
    UsernameValidationMessages messages,
  ) async {
    if (username.isEmpty) return messages.cannotBeEmpty;
    if (username.contains(' ')) return messages.invalid;
    final first = username.runes.first;
    if (!_isLetterOrDigit(first)) {
      return messages.mustStartWithLetterOrNumber;
    }
    if (!username.runes.every(_isAllowed)) {
      return messages.onlyLettersNumbers;
    }

    final existing = await _userDao.getByName(username);
    final taken =
        existing != null && !(existing.couchId?.startsWith('guest') ?? false);
    return taken ? messages.taken : null;
  }

  static bool _isLetterOrDigit(int rune) =>
      (rune >= 0x30 && rune <= 0x39) || // 0-9
      (rune >= 0x41 && rune <= 0x5A) || // A-Z
      (rune >= 0x61 && rune <= 0x7A); // a-z

  static bool _isAllowed(int rune) =>
      _isLetterOrDigit(rune) || rune == 0x5F || rune == 0x2E || rune == 0x2D;
  // `_` (0x5F), `.` (0x2E), `-` (0x2D)

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

    // Publish the health key/IV to the user's per-user database so records
    // written on this device decrypt on another — port of `saveKeyIv`,
    // the upload direction Phase 61's sync-in reads back. Failures are
    // swallowed to match the Kotlin's `catch (_: Exception) { }` in
    // `saveUserToDb`: the account is already created; a missing key doc just
    // means the key stays device-local until the next attempt.
    try {
      await saveKeyIv(
        localId: localId,
        config: config,
        username: username,
        password: password,
      );
    } catch (_) {}

    return true;
  }

  /// Port of `UserRepositoryImpl.saveKeyIv` — publish the health AES key/IV to
  /// the user's per-user CouchDB database (`userdb-`+hex(planetCode)+`-`+hex(
  /// name)), so health records written on this device decrypt on another.
  ///
  /// The flow, matching the Kotlin:
  /// 1. Generate a key/IV, reusing any already stored on the row (a re-upload
  ///    must not rotate the key, or the records encrypted with the old one
  ///    become unreadable).
  /// 2. PUT an empty document to `${dbUrl}/$table` to create the database —
  ///    best-effort, failure swallowed (it may already exist).
  /// 3. POST `{key, iv, createdOn}` to that database, retried up to 3 times
  ///    with a 2 s backoff (`RetryUtils.retry`).
  /// 4. On success, call [changeUserSecurity] (grant the `health` role on the
  ///    database's `_security` doc) and [UserDao.markUserKeyIvSaved].
  ///
  /// Throws on a POST that fails all 3 attempts, as the Kotlin throws
  /// `IOException("Failed to save key/IV after $maxAttempts attempts")`. The
  /// caller ([uploadNewUser]) swallows that, so the user-facing upload still
  /// reports success.
  Future<void> saveKeyIv({
    required String localId,
    required ServerConfig config,
    required String username,
    required String password,
  }) async {
    final user = await _userDao.getById(localId);
    if (user == null) return;

    final table =
        'userdb-${user.planetCode == null ? 'null' : toHexString(user.planetCode!)}-'
        '${user.name == null ? 'null' : toHexString(user.name!)}';
    final authHeader = UrlUtils.basicAuthHeader(username, password);
    final dbUrl = UrlUtils.dbUrl(config);

    var key = user.key;
    var iv = user.iv;
    key ??= HealthCipher.generateKey();
    iv ??= HealthCipher.generateIv();

    final body = <String, dynamic>{
      'key': key,
      'iv': iv,
      'createdOn': DateTime.now().millisecondsSinceEpoch,
    };

    // Best-effort database creation — the Kotlin wraps it in its own
    // try/catch and ignores the result.
    try {
      await _api.putJsonObject('$dbUrl/$table', <String, dynamic>{});
    } catch (_) {}

    final maxAttempts = 3;
    const retryDelay = Duration(seconds: 2);
    Map<String, dynamic>? posted;
    for (var attempt = 0; attempt < maxAttempts; attempt++) {
      final result = await _api.postJsonObject(
        '$dbUrl/$table',
        body,
        authHeader: authHeader,
      );
      if (result is NetworkSuccess<Map<String, dynamic>>) {
        posted = result.data;
        break;
      }
      if (attempt < maxAttempts - 1) {
        await Future<void>.delayed(retryDelay);
      }
    }

    if (posted == null) {
      throw Exception('Failed to save key/IV after $maxAttempts attempts');
    }

    await changeUserSecurity(
      table: table,
      dbUrl: dbUrl,
      authHeader: authHeader,
    );
    await _userDao.markUserKeyIvSaved(localId, key, iv);
  }

  /// Port of `UserRepositoryImpl.changeUserSecurity` — grant the `health` role
  /// on the user's per-user database `_security` document, so the user can
  /// read their own key/IV doc. Best-effort: the Kotlin wraps the GET-and-PUT
  /// in `try/catch { e.printStackTrace() }`, so a failure here is swallowed
  /// and the key/IV is still recorded locally.
  Future<void> changeUserSecurity({
    required String table,
    required String dbUrl,
    required String authHeader,
  }) async {
    try {
      final result = await _api.getJsonObject(
        '$dbUrl/$table/_security',
        authHeader: authHeader,
      );
      if (result is! NetworkSuccess<Map<String, dynamic>>) return;
      final security = Map<String, dynamic>.from(result.data);
      final members = security['members'] is Map<String, dynamic>
          ? Map<String, dynamic>.from(
              security['members'] as Map<String, dynamic>,
            )
          : <String, dynamic>{};
      final roles = members['roles'] is List
          ? List<Object>.from(members['roles'] as List)
          : <Object>[];
      if (!roles.contains('health')) {
        roles.add('health');
      }
      members['roles'] = roles;
      security['members'] = members;
      await _api.putJsonObject(
        '$dbUrl/$table/_security',
        security,
        authHeader: authHeader,
      );
    } catch (_) {
      // Swallowed — see the doc comment.
    }
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
