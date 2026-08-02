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
      userImage: Value(JsonUtils.getStringOrNull('userImage', doc)),
      isArchived: Value(JsonUtils.getBool('isArchived', doc)),
    );
  }

  /// Port of `UserEntity.isManager()` — the manager/admin role check the login
  /// screen applies when `isManagerMode` is set.
  static bool isManager(UserRow user) =>
      user.userAdmin ||
      user.rolesList.any((role) => role.toLowerCase() == 'manager');
}
