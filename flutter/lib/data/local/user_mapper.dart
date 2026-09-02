import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `UserEntity.populateUsersTable` / the `insert` path in
/// `repository/UserRepositoryImpl.kt`.
///
/// Maps a CouchDB `_users` document onto a [UsersCompanion].
class UserMapper {
  const UserMapper._();

  /// Port of `buildUserFromJson` + `applyJsonToUser`
  /// (`UserRepositoryImpl.kt:292-316` and `:180-276`).
  ///
  /// [existing] is the row the document already belongs to — Kotlin resolves
  /// it with `getUserByAnyId(_id)`, which is
  /// `SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1`, so a document
  /// finds a row through its `_id` column as readily as through its key. Pass
  /// it and the row **keeps its own `id`**: `applyJsonToUser` reassigns `id`
  /// only when it is blank.
  ///
  /// That is the whole identity rule, and it is load-bearing. A member
  /// registered on this device keeps a locally-minted `'<millis>'` id and
  /// gains a `couchId` only when the upload lands, so keying the cached row on
  /// the document `_id` gave that member a *second* row the moment they first
  /// signed in online — one carrying no `key`/`iv`, which
  /// `UserDao.getById(couchId)` matches as readily as the real one. Since the
  /// health records are encrypted with those two values and nothing else on
  /// the device or the server can reproduce them, resolving the member to the
  /// wrong row does not merely duplicate a name: it makes their medical
  /// records undecryptable.
  ///
  /// [generateLocalId] supplies the key for a document with no `_id` at all —
  /// Kotlin's `UUID.randomUUID()` fallback. It is only reachable for a
  /// malformed document; a real `_users` doc is always keyed
  /// `org.couchdb.user:<name>`.
  static UsersCompanion fromDoc(
    Map<String, dynamic> doc, {
    UserRow? existing,
    String Function()? generateLocalId,
  }) {
    final couchId = JsonUtils.getString('_id', doc);
    final rowId = (existing?.id.isNotEmpty ?? false)
        ? existing!.id
        : (couchId.isNotEmpty
              ? couchId
              : (generateLocalId ?? _defaultLocalId)());

    return UsersCompanion(
      id: Value(rowId),
      couchId: Value(couchId.isEmpty ? null : couchId),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      name: Value(JsonUtils.getStringOrNull('name', doc)),
      rolesList: Value(JsonUtils.getStringList('roles', doc)),
      userAdmin: Value(JsonUtils.getBool('isUserAdmin', doc)),
      // Guarded, `if (newJoinDate != 0L || joinDate == 0L)`: a document
      // without a join date leaves a recorded one alone.
      joinDate: _keepingStoredInt(
        JsonUtils.getLong('joinDate', doc),
        existing?.joinDate,
      ),
      // The eleven guarded string fields, in Kotlin's order. Each is
      // `if (new.isNotEmpty() || old.isNullOrEmpty()) field = new` — a
      // document that omits one keeps what the row already holds, which is
      // what stops a re-login wiping a profile the member filled in offline.
      firstName: _keepingStored(
        JsonUtils.getStringOrNull('firstName', doc),
        existing?.firstName,
      ),
      lastName: _keepingStored(
        JsonUtils.getStringOrNull('lastName', doc),
        existing?.lastName,
      ),
      middleName: _keepingStored(
        JsonUtils.getStringOrNull('middleName', doc),
        existing?.middleName,
      ),
      email: _keepingStored(
        JsonUtils.getStringOrNull('email', doc),
        existing?.email,
      ),
      phoneNumber: _keepingStored(
        JsonUtils.getStringOrNull('phoneNumber', doc),
        existing?.phoneNumber,
      ),
      level: _keepingStored(
        JsonUtils.getStringOrNull('level', doc),
        existing?.level,
      ),
      language: _keepingStored(
        JsonUtils.getStringOrNull('language', doc),
        existing?.language,
      ),
      gender: _keepingStored(
        JsonUtils.getStringOrNull('gender', doc),
        existing?.gender,
      ),
      dob: _keepingStored(
        JsonUtils.getStringOrNull('birthDate', doc),
        existing?.dob,
      ),
      birthPlace: _keepingStored(
        JsonUtils.getStringOrNull('birthPlace', doc),
        existing?.birthPlace,
      ),
      age: _keepingStored(JsonUtils.getStringOrNull('age', doc), existing?.age),
      // Unguarded in the Kotlin, and deliberately so: these four are the
      // credentials PBKDF2 verification reads, and the document is their
      // authority. (`updateUserSecurityData` is the path that must *not*
      // overwrite them with null — see `aa24dfa6c`/#15836 — but that is a
      // response to a POST, not the account document.)
      planetCode: Value(JsonUtils.getStringOrNull('planetCode', doc)),
      parentCode: Value(JsonUtils.getStringOrNull('parentCode', doc)),
      passwordScheme: Value(JsonUtils.getStringOrNull('password_scheme', doc)),
      iterations: Value(JsonUtils.getStringOrNull('iterations', doc)),
      derivedKey: Value(JsonUtils.getStringOrNull('derived_key', doc)),
      salt: Value(JsonUtils.getStringOrNull('salt', doc)),
      // `if (_id?.isEmpty() == true) password = getString("password", jsonDoc)`
      // — and that reads `_id` *after* `_id = newId`, so the plaintext
      // password is taken only for a document with no `_id`. That is **not**
      // the guest shape, which this comment used to claim: `createGuestUser`
      // supplies `_id = "guest_<username>"` (`buildGuestUserJson`), so a guest
      // row's password stays null and a guest is never verified by comparison.
      // The shape it is for is a member registered **offline**, whose
      // `createMember` document carries no `_id` at all
      // (`UserRepositoryImpl.kt:570-579`). Everyone else is verified against
      // `derived_key`/`salt`.
      password: couchId.isEmpty
          ? Value(JsonUtils.getStringOrNull('password', doc))
          : const Value.absent(),
      // Port of `UserEntity.addImageUrl`: a `_users` document stores the
      // profile photo as a CouchDB attachment under `_attachments`, not as a
      // top-level `userImage` field (the Kotlin doc never has one). We store
      // the attachment *name* here and build the full URL at display time via
      // `UrlUtils.userImageUrl`, so the persisted value carries no server
      // credentials and survives a server URL change.
      //
      // `addImageUrl` writes nothing when the document has no non-empty
      // `_attachments`, and the stored value it leaves alone can be a local
      // file path a queued photo upload has not sent yet — so an absent
      // attachment must not null the column.
      userImage: _imageName(doc),
      isArchived: Value(JsonUtils.getBool('isArchived', doc)),
    );
  }

  static String _defaultLocalId() => '${DateTime.now().microsecondsSinceEpoch}';

  /// `if (new.isNotEmpty() || old.isNullOrEmpty()) field = new`.
  ///
  /// An absent [Value] is what "leave the stored one alone" means to
  /// `insertOnConflictUpdate`: the column stays out of the `DO UPDATE SET`
  /// list. On an insert there is no stored value to keep, so [stored] is null
  /// and the incoming one is written either way.
  static Value<String?> _keepingStored(String? incoming, String? stored) {
    if (incoming != null && incoming.isNotEmpty) return Value(incoming);
    if (stored == null || stored.isEmpty) return Value(incoming);
    return const Value.absent();
  }

  /// The `joinDate` variant: `0` is the empty value rather than `null`.
  static Value<int> _keepingStoredInt(int incoming, int? stored) {
    if (incoming != 0 || (stored ?? 0) == 0) return Value(incoming);
    return const Value.absent();
  }

  /// The attachment name to store, or an absent [Value] where `addImageUrl`
  /// writes nothing.
  static Value<String?> _imageName(Map<String, dynamic> doc) {
    final name = _attachmentName(doc);
    return name == null ? const Value.absent() : Value(name);
  }

  /// The first `_attachments` key in [doc], or `null` - the slot Kotlin takes
  /// as the profile image name in `UserEntity.addImageUrl`.
  static String? _attachmentName(Map<String, dynamic> doc) {
    final attachments = JsonUtils.getObject('_attachments', doc);
    if (attachments == null || attachments.isEmpty) return null;
    return attachments.keys.first;
  }

  /// Port of `UserEntity.serialize()` — the body of the `_users` PUT.
  ///
  /// Mirrors the Kotlin field order and the two-branch password section: an
  /// account with no CouchDB id (`couchId` blank) is a local-only creation, so
  /// the document carries the plaintext password the server will hash; an
  /// existing account carries `derived_key`/`salt`/`password_scheme` instead.
  ///
  /// [imageBytes], when supplied, is embedded as a base64 `_attachments` entry
  /// under the `img` key, exactly as `encodeImageToBase64` + `serialize` do.
  /// The bytes are read at queue time rather than at send time so a photo the
  /// user has since deleted from the picker's cache still reaches the server —
  /// the outbox is durable, the temp file is not.
  ///
  /// One deliberate divergence from `serialize`: the `androidId`/
  /// `uniqueAndroidId`/`customDeviceName` trio is omitted, because the device
  /// telemetry that needs them is unported (see the migration tracker). The
  /// Kotlin includes them only on the creation branch, and Planet ignores them
  /// for account creation, so omitting them changes no document the server
  /// reads.
  static Map<String, dynamic> toDoc(UserRow user, {List<int>? imageBytes}) {
    final doc = <String, dynamic>{
      'name': user.name,
      'roles': user.rolesList,
      if (user.couchId == null || user.couchId!.isEmpty) ...{
        'password': user.password,
      } else ...{
        'derived_key': user.derivedKey,
        'salt': user.salt,
        'password_scheme': user.passwordScheme,
      },
      'isUserAdmin': user.userAdmin,
      'joinDate': user.joinDate,
      'firstName': user.firstName,
      'lastName': user.lastName,
      'middleName': user.middleName,
      'email': user.email,
      'language': user.language,
      'level': user.level,
      'type': 'user',
      'gender': user.gender,
      'phoneNumber': user.phoneNumber,
      'birthDate': user.dob,
      'age': user.age,
      'iterations': _iterationsOr(user.iterations, 10),
      'parentCode': user.parentCode,
      'planetCode': user.planetCode,
      'birthPlace': user.birthPlace,
      'isArchived': user.isArchived,
    };

    if (imageBytes != null && imageBytes.isNotEmpty) {
      doc['_attachments'] = {
        'img': {'content_type': 'image/jpeg', 'data': base64Encode(imageBytes)},
      };
    }
    return doc;
  }

  /// Port of the `iterations` try/catch in `UserEntity.serialize`: a blank or
  /// non-numeric stored value falls back to 10, the hard-coded count
  /// `AndroidDecrypter` uses (see *Faithful quirks* in the migration tracker).
  static int _iterationsOr(String? stored, int fallback) {
    final raw = stored?.trim();
    if (raw == null || raw.isEmpty) return fallback;
    return int.tryParse(raw) ?? fallback;
  }

  /// Port of `UserEntity.encodeImageToBase64`'s file-read half.
  ///
  /// Returns the raw bytes when [userImage] points at a readable local file,
  /// and `null` otherwise — a `content://` uri is an Android path the sandbox
  /// cannot open with `dart:io`, and an attachment name from a prior sync is
  /// not a file path at all. The image-picker path the upload slice writes is
  /// a real filesystem path, which is the only case that should embed bytes.
  static Future<List<int>?> readImageBytes(String? userImage) async {
    if (userImage == null || userImage.trim().isEmpty) return null;
    final path = userImage.trim();
    if (path.startsWith('content://')) return null;
    final file = File(path);
    if (!await file.exists()) return null;
    return await file.readAsBytes();
  }

  /// Port of `LoginSyncManager.isManager(jsonDoc)` — the same predicate as
  /// [isManager], read off the account **document** rather than a stored row.
  /// `checkManagerAndInsert` applies it before caching anything, so it has to
  /// be answerable without a row.
  static bool docIsManager(Map<String, dynamic> doc) =>
      JsonUtils.getBool('isUserAdmin', doc) ||
      JsonUtils.getStringList(
        'roles',
        doc,
      ).any((role) => role.toLowerCase() == 'manager');

  /// Port of `UserEntity.isManager()` - the manager/admin role check the login
  /// screen applies when `isManagerMode` is set.
  static bool isManager(UserRow user) =>
      user.userAdmin ||
      user.rolesList.any((role) => role.toLowerCase() == 'manager');

  /// The prefix `createGuestUser` keys a guest row on.
  ///
  /// `buildGuestUserJson` (`UserRepositoryImpl.kt:146-153`) mints
  /// `{"_id": "guest_$username", "name": username, "firstName": username,
  /// "roles": ["guest"]}` and hands it to `saveUser(jsonDoc)`, so
  /// `applyJsonToUser` writes **both** the row key (`id`, from `_id` because
  /// the fresh entity's is blank) and the `_id` column as `guest_<username>`.
  /// That equality is why Kotlin can spell the check five different ways —
  /// `_id?.startsWith("guest_")` (`UserEntity.isGuest`, `migrateGuestUser`,
  /// `cleanupDuplicateUsers`, `insertUsersFromSync`),
  /// `SUBSTR(_id, 1, 6) = 'guest_'` (`UserDao.getGuestUserByName`),
  /// `_id.orEmpty().startsWith("guest")` (`validateUsername`),
  /// `id.startsWith("guest")` (every UI gate, `getSyncedUserByName`) and
  /// `SUBSTR(id, 1, 5) != 'guest'` (`UserDao.getSyncedUsers`) — and still get
  /// one answer.
  ///
  /// Anything a guest row is created by must use this constant, so the
  /// equality holds here too.
  static const String guestIdPrefix = 'guest_';

  /// The guest rule, applied to one id string.
  ///
  /// **A deliberate deviation, recorded rather than hidden.** Six characters,
  /// not five — and five is what the Kotlin sites this replaced use
  /// (`user.id.startsWith("guest")` at every UI gate,
  /// `_id.orEmpty().startsWith("guest")` in `validateUsername`). `guest_` is
  /// what Kotlin's *authoritative* spellings use (the list above), and the
  /// five-character ones cannot disagree with it for any id either app
  /// writes: those are `guest_<username>` (the username is validated
  /// non-empty), `org.couchdb.user:<name>`, and a millisecond timestamp, so
  /// no id starts `guest` without starting `guest_`.
  ///
  /// The two error directions are not symmetric, which is why the tighter
  /// rule wins. A false positive — some future id that merely begins `guest`,
  /// a server document called `guestbook` — would gate a real member out of
  /// features they are entitled to. A false negative needs an id that starts
  /// `guest` and not `guest_`, which only a guest-creator that ignores this
  /// constant could mint; that is what exporting the constant prevents.
  static bool isGuestId(String? id) => id?.startsWith(guestIdPrefix) ?? false;

  /// Whether [user] is the local guest row — the single predicate every guest
  /// gate in the port reads.
  ///
  /// Both id columns are tested, and that is a deliberate widening of any one
  /// Kotlin spelling. Kotlin's spellings are interchangeable only because of
  /// the `id == _id` equality [guestIdPrefix] describes, and the port cannot
  /// yet enforce that equality: **nothing in the port creates a guest row at
  /// all** (see `PHASE_112_NOTES.md`). Reading one column would leave a future
  /// `createGuestUser` free to satisfy this predicate at some call sites and
  /// not others — which is exactly the state Phase 107 found and this helper
  /// exists to make unrepresentable. On every row Kotlin can produce the two
  /// columns agree, so the widening changes no answer; where they disagree it
  /// errs towards "guest", which withholds a privilege rather than granting
  /// one.
  ///
  /// **Not** a port of `UserEntity.isGuest()`, which is this rule *or* a
  /// `guest` role without a `learner` role. That role clause is unported
  /// because every gate the port has is a counterpart of Kotlin's narrower
  /// `user.id.startsWith("guest")` family; the gates that read the role clause
  /// (`TeamFragment:235`, `CoursesFragment:135`, `TakeCourseFragment:212`)
  /// have no port counterpart yet. When they land, the role clause belongs
  /// here as a second predicate beside this one — not folded into it, or the
  /// settings and voices gates silently widen past their Kotlin counterparts.
  static bool isGuest(UserRow user) =>
      isGuestId(user.id) || isGuestId(user.couchId);

  /// Port of `VoicesAdapter.matchesCurrentUser` (`VoicesAdapter.kt:666-669`):
  ///
  /// ```kotlin
  /// if (id.isNullOrEmpty()) return false
  /// return id == currentUser?._id || id == currentUser?.id
  /// ```
  ///
  /// An **or** over both id columns, and the same rule as
  /// `UserDao.getById`'s `WHERE id = :id OR _id = :id`, for the reason
  /// Phase 107 established: a member registered on this device authors rows
  /// under the locally-minted `'<millis>'` key and gains a `couchId` only once
  /// the upload lands. Preferring one column over the other makes the
  /// account's own earlier rows stop being its own the moment it uploads.
  ///
  /// The empty-or-null guard comes first in the Kotlin and is load-bearing:
  /// without it a row with no author would match a session that has no
  /// `couchId`.
  static bool matchesUser(UserRow user, String? id) {
    if (id == null || id.isEmpty) return false;
    return id == user.couchId || id == user.id;
  }
}
