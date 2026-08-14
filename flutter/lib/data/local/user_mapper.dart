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

  static UsersCompanion fromDoc(Map<String, dynamic> doc) {
    final couchId = JsonUtils.getString('_id', doc);
    return UsersCompanion(
      // The Kotlin keys the row on the document `_id` when there is one, so a
      // re-login updates the existing row instead of duplicating it.
      id: Value(couchId),
      couchId: Value(couchId.isEmpty ? null : couchId),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
      name: Value(JsonUtils.getStringOrNull('name', doc)),
      rolesList: Value(JsonUtils.getStringList('roles', doc)),
      userAdmin: Value(JsonUtils.getBool('isUserAdmin', doc)),
      joinDate: Value(JsonUtils.getLong('joinDate', doc)),
      firstName: Value(JsonUtils.getStringOrNull('firstName', doc)),
      lastName: Value(JsonUtils.getStringOrNull('lastName', doc)),
      middleName: Value(JsonUtils.getStringOrNull('middleName', doc)),
      email: Value(JsonUtils.getStringOrNull('email', doc)),
      planetCode: Value(JsonUtils.getStringOrNull('planetCode', doc)),
      parentCode: Value(JsonUtils.getStringOrNull('parentCode', doc)),
      phoneNumber: Value(JsonUtils.getStringOrNull('phoneNumber', doc)),
      passwordScheme: Value(JsonUtils.getStringOrNull('password_scheme', doc)),
      iterations: Value(JsonUtils.getStringOrNull('iterations', doc)),
      derivedKey: Value(JsonUtils.getStringOrNull('derived_key', doc)),
      salt: Value(JsonUtils.getStringOrNull('salt', doc)),
      level: Value(JsonUtils.getStringOrNull('level', doc)),
      language: Value(JsonUtils.getStringOrNull('language', doc)),
      gender: Value(JsonUtils.getStringOrNull('gender', doc)),
      dob: Value(JsonUtils.getStringOrNull('birthDate', doc)),
      age: Value(JsonUtils.getStringOrNull('age', doc)),
      birthPlace: Value(JsonUtils.getStringOrNull('birthPlace', doc)),
      // Port of `UserEntity.addImageUrl`: a `_users` document stores the
      // profile photo as a CouchDB attachment under `_attachments`, not as a
      // top-level `userImage` field (the Kotlin doc never has one). We store
      // the attachment *name* here and build the full URL at display time via
      // `UrlUtils.userImageUrl`, so the persisted value carries no server
      // credentials and survives a server URL change.
      userImage: Value(_attachmentName(doc)),
      isArchived: Value(JsonUtils.getBool('isArchived', doc)),
    );
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

  /// Port of `UserEntity.isManager()` - the manager/admin role check the login
  /// screen applies when `isManagerMode` is set.
  static bool isManager(UserRow user) =>
      user.userAdmin ||
      user.rolesList.any((role) => role.toLowerCase() == 'manager');
}
