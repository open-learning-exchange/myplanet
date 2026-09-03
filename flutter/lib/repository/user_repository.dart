import 'package:drift/drift.dart';
import 'package:meta/meta.dart';

import '../core/config/server_config.dart';
import '../core/crypto/android_decrypter.dart';
import '../core/crypto/health_cipher.dart';
import '../core/network/network_result.dart';
import '../core/sync/sync_result.dart';
import '../core/sync/table_walk.dart';
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

  /// Page size for the `tablet_users` walk. `TransactionSyncManager.syncDb`
  /// uses its 1000-document default for this table; the port starts smaller and
  /// lets `AdaptiveBatchProcessor` grow it, because a `_users` document can
  /// carry a base64 profile photo in `_attachments` and a thousand of those in
  /// one response is a large body on a weak link.
  static const int tabletUsersBatchSize = 100;

  /// Port of the `"tablet_users"` arm of `TransactionSyncManager.syncDb`
  /// (`services/sync/TransactionSyncManager.kt:230-232`), which the Kotlin runs
  /// in phase 1 of every full sync.
  ///
  /// Without it the `users` table holds only accounts that have signed in on
  /// this device, so member detail says "Unknown member" for everyone else, the
  /// team leaderboard silently drops every member it cannot resolve, and the
  /// team member list falls back to rendering `org.couchdb.user:bob`.
  ///
  /// **Never prunes.** The Kotlin's walk issues no delete of any kind, and it
  /// could not: this table also holds accounts registered offline that have
  /// never reached the server, and the session itself is restored by looking
  /// the signed-in user up here. A `deleteNotIn` would sign the user out.
  Future<SyncResult> syncTabletUsers({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) => walkAllDocs(
    api: _api,
    config: config,
    table: 'tablet_users',
    initialBatchSize: tabletUsersBatchSize,
    onProgress: onProgress,
    insert: insertUsersFromSync,
  );

  /// Port of `UserRepositoryImpl.insertUsersFromSync`
  /// (`UserRepositoryImpl.kt:1061-1147`).
  ///
  /// Two things beyond a plain upsert, both the Kotlin's:
  ///
  /// * **Existing rows are resolved by either identity column** before mapping,
  ///   so a member registered on this device keeps their locally-minted `id`
  ///   and their `key`/`iv` rather than gaining a second row keyed by the
  ///   CouchDB `_id`. [UserMapper.fromDoc] does the rest — every column a
  ///   `_users` document does not carry is left `Value.absent()`, so the walk
  ///   cannot overwrite it.
  /// * **A guest is adopted, not duplicated.** When a document keyed
  ///   `org.couchdb.user:<name>` has no row of its own but a guest row carries
  ///   that name, the Kotlin re-keys the guest to the new id and deletes the
  ///   old row. The port has to rebuild the row rather than mutate it, so the
  ///   device-only columns are carried across by hand — dropping `key`/`iv`
  ///   here would make anything already encrypted with them unreadable.
  Future<int> insertUsersFromSync(List<Map<String, dynamic>> docs) async {
    if (docs.isEmpty) return 0;

    final ids = <String>{};
    final names = <String>{};
    for (final doc in docs) {
      final id = JsonUtils.getString('_id', doc);
      if (id.isNotEmpty) ids.add(id);
      final name = JsonUtils.getString('name', doc);
      if (name.isNotEmpty) names.add(name);
    }

    final found = [
      ...await _userDao.getByAnyIds(ids.toList(growable: false)),
      ...await _userDao.getGuestUsersByNames(names.toList(growable: false)),
    ];

    // Kotlin's `distinctBy { it.id }` — the two reads above overlap for a guest
    // whose name is also one of the ids.
    final usersById = <String, UserRow>{};
    final guestsByName = <String, UserRow>{};
    final seen = <String>{};
    for (final user in found) {
      if (!seen.add(user.id)) continue;
      usersById[user.id] = user;
      final couchId = user.couchId;
      if (couchId != null) usersById[couchId] = user;
      if ((user.name ?? '').isNotEmpty && _isGuest(couchId)) {
        guestsByName[user.name!] = user;
      }
    }

    final toDelete = <String>{};
    final toUpsert = <String, UsersCompanion>{};

    for (final doc in docs) {
      final couchId = JsonUtils.getString('_id', doc);
      final name = JsonUtils.getString('name', doc);
      final existing = usersById[couchId];
      final guest =
          (existing == null &&
              couchId.startsWith('org.couchdb.user:') &&
              name.isNotEmpty)
          ? guestsByName[name]
          : null;

      // One call site, and `existing:` is always passed — the mapper-preservation
      // guard (`test/data/local/mapper_preserves_local_columns_test.dart`) is
      // right to insist: every column a `_users` document does not carry is
      // `Value.absent()` only because `existing` reached the mapper.
      var companion = UserMapper.fromDoc(doc, existing: existing ?? guest);

      if (guest != null) {
        // Re-key the guest onto the account that has just appeared
        // server-side, exactly as `applyJsonToUser` does after
        // `this.id = id; this._id = id`.
        companion = _adoptGuest(companion, guest, couchId);
        toDelete.add(guest.id);
        if ((guest.name ?? '').isNotEmpty) guestsByName.remove(guest.name);
        usersById.remove(guest.id);
        final guestCouchId = guest.couchId;
        if (guestCouchId != null) usersById.remove(guestCouchId);
      }

      final rowId = companion.id.value;
      // A guest that has just been re-keyed must not be deleted again by a
      // later document on the same page.
      toDelete.remove(rowId);
      toUpsert[rowId] = companion;
    }

    if (toDelete.isNotEmpty) {
      await _userDao.deleteByIds(toDelete.toList(growable: false));
    }
    await _userDao.upsertAll(toUpsert.values.toList(growable: false));
    return toUpsert.length;
  }

  static bool _isGuest(String? couchId) =>
      couchId?.startsWith(UserMapper.guestIdPrefix) ?? false;

  /// Carries a guest row's device-only columns onto the companion that will
  /// replace it under the server's id.
  ///
  /// [UserMapper.fromDoc] leaves these `Value.absent()` because a `_users`
  /// document does not carry them, and absent means "keep what is stored" —
  /// but the adopted row is stored under a *different* key, so there is
  /// nothing to keep and each column would silently take its default.
  static UsersCompanion _adoptGuest(
    UsersCompanion mapped,
    UserRow guest,
    String newId,
  ) => mapped.copyWith(
    // [UserMapper.fromDoc] keys the companion on `existing.id`, which here
    // is still `guest_<name>`. The Kotlin assigns `this.id = id` *before*
    // calling `applyJsonToUser` (`UserRepositoryImpl.kt:1126-1131`), so the
    // adopted row is written under the server's id and the guest row is
    // deleted. Leaving the guest key in place would update the guest in
    // situ and leave the account still unresolvable by its CouchDB id.
    id: Value(newId),
    key: Value(guest.key),
    iv: Value(guest.iv),
    password: mapped.password.present ? mapped.password : Value(guest.password),
    userImage: mapped.userImage.present
        ? mapped.userImage
        : Value(guest.userImage),
    isUpdated: Value(guest.isUpdated),
  );

  /// Port of `UserRepositoryImpl.authenticateUser`.
  ///
  /// The branch is `if (it._id?.isEmpty() == true)`
  /// (`UserRepositoryImpl.kt:862`), and despite how it reads it is **not** a
  /// guest check — a guest row's `_id` is `guest_<username>`, not empty
  /// (`buildGuestUserJson`), so a guest takes the *other* branch and fails
  /// against a null `derived_key`. That is correct: guest re-entry never comes
  /// through here, it goes back through `showGuestLoginDialog`, which
  /// recognises the stored row and re-offers it without a password
  /// (`GuestLoginExtensions.kt:64`).
  ///
  /// What the branch actually selects is an account with **no server
  /// identity** — a member registered offline, whose document had no `_id`, so
  /// `applyJsonToUser` wrote `""` and the plaintext password it typed. The
  /// port stores that absent id as `null` rather than `''`, so both are
  /// tested. Everyone with a server identity is checked with the same PBKDF2
  /// comparison as the online path.
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

    final hasNoServerIdentity = user.couchId == null || user.couchId!.isEmpty;
    final authenticated = hasNoServerIdentity
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
    // Kotlin reads `_id.orEmpty().startsWith("guest")` here
    // (`UserRepositoryImpl.kt:832`) — a guest may re-take their own name,
    // because becoming a member *reuses* that row (`migrateGuestUser`) rather
    // than adding one. `UserMapper.isGuest` is the same rule read off both id
    // columns; see its doc comment for why both.
    final taken = existing != null && !UserMapper.isGuest(existing);
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
