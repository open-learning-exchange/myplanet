import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/user_mapper.dart';

/// Pins the `_attachments` -> `userImage` port of `UserEntity.addImageUrl`.
/// The Kotlin reads the *first* attachment key as the profile-photo name; the
/// doc itself never carries a top-level `userImage` field, so a stray value
/// there must not shadow the attachment.
void main() {
  group('UserMapper.fromDoc userImage', () {
    test('takes the first attachment key as the image name', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        '_attachments': {
          'ada.png': {'content_type': 'image/png', 'length': 1234},
          'thumb.png': {'content_type': 'image/png', 'length': 100},
        },
      });

      expect(companion.userImage.value, 'ada.png');
    });

    test('is null when there are no attachments', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
      });

      expect(companion.userImage.value, isNull);
    });

    test('ignores a top-level userImage field in favour of attachments', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        'userImage': 'should-be-ignored',
        '_attachments': {
          'real.png': {'content_type': 'image/png'},
        },
      });

      expect(companion.userImage.value, 'real.png');
    });

    test('is null for an empty attachments object', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        '_attachments': <String, dynamic>{},
      });

      expect(companion.userImage.value, isNull);
    });
  });

  group('UserMapper.toDoc', () {
    UserRow user({
      String? couchId,
      String? password,
      String? iterations,
      List<String> roles = const ['learner'],
      bool admin = false,
    }) => UserRow(
      id: 'user-1',
      couchId: couchId,
      name: 'ada',
      rolesList: roles,
      userAdmin: admin,
      joinDate: 123,
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.org',
      language: 'English',
      level: 'a1',
      gender: 'female',
      dob: '1815-12-10',
      age: '36',
      birthPlace: 'London',
      parentCode: 'nation',
      planetCode: 'planet-a',
      password: password,
      iterations: iterations,
      isArchived: false,
      isUpdated: true,
    );

    test('serializes the profile fields and the new v30 columns', () {
      final doc = UserMapper.toDoc(user(couchId: 'org.couchdb.user:ada'));

      expect(doc['name'], 'ada');
      expect(doc['roles'], ['learner']);
      expect(doc['type'], 'user');
      expect(doc['firstName'], 'Ada');
      expect(doc['lastName'], 'Lovelace');
      expect(doc['birthDate'], '1815-12-10');
      expect(doc['age'], '36');
      expect(doc['birthPlace'], 'London');
      expect(doc['parentCode'], 'nation');
      expect(doc['planetCode'], 'planet-a');
      // A synced user sends PBKDF2 fields, never the plaintext password.
      expect(doc.containsKey('password'), isFalse);
    });

    test('a new account sends its plaintext password and no derived key', () {
      final doc = UserMapper.toDoc(user(password: 'plain-secret'));

      expect(doc['password'], 'plain-secret');
      expect(doc.containsKey('derived_key'), isFalse);
      expect(doc.containsKey('salt'), isFalse);
      expect(doc.containsKey('password_scheme'), isFalse);
    });

    test('iterations falls back to 10 when blank or non-numeric', () {
      expect(UserMapper.toDoc(user(iterations: ''))['iterations'], 10);
      expect(
        UserMapper.toDoc(user(iterations: 'not-a-number'))['iterations'],
        10,
      );
      expect(UserMapper.toDoc(user(iterations: '1000'))['iterations'], 1000);
    });

    test('embeds the image bytes as a base64 attachment under img', () {
      final bytes = [1, 2, 3, 4];
      final doc = UserMapper.toDoc(user(), imageBytes: bytes);

      final attachments = doc['_attachments'] as Map<String, dynamic>;
      final img = attachments['img'] as Map<String, dynamic>;
      expect(img['content_type'], 'image/jpeg');
      expect(img['data'], base64Encode(bytes));
    });

    test('omits the attachment when there are no image bytes', () {
      final doc = UserMapper.toDoc(user());
      expect(doc.containsKey('_attachments'), isFalse);
    });

    test('omits the attachment when the byte list is empty', () {
      final doc = UserMapper.toDoc(user(), imageBytes: const []);
      expect(doc.containsKey('_attachments'), isFalse);
    });

    test('a manager role list is serialized verbatim', () {
      final doc = UserMapper.toDoc(user(roles: const ['manager', 'leader']));
      expect(doc['roles'], ['manager', 'leader']);
    });

    test('isUserAdmin and isArchived round-trip as booleans', () {
      final admin = user(admin: true);
      expect(UserMapper.toDoc(admin)['isUserAdmin'], true);
      expect(UserMapper.toDoc(admin)['isArchived'], false);
    });
  });

  group('UserMapper.fromDoc fields', () {
    test('maps every profile field from a CouchDB document', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        '_rev': '3-abc',
        'name': 'ada',
        'roles': ['learner', 'manager'],
        'isUserAdmin': true,
        'joinDate': 999,
        'firstName': 'Ada',
        'lastName': 'Lovelace',
        'middleName': 'Augusta',
        'email': 'ada@example.org',
        'planetCode': 'planet-a',
        'parentCode': 'nation',
        'phoneNumber': '+1 555',
        'password_scheme': 'pbkdf2',
        'iterations': '1000',
        'derived_key': 'dk',
        'salt': 'salty',
        'level': 'a1',
        'language': 'English',
        'gender': 'female',
        'birthDate': '1815-12-10',
        'age': '36',
        'birthPlace': 'London',
        'isArchived': false,
      });

      expect(companion.id.value, 'org.couchdb.user:ada');
      expect(companion.couchId.value, 'org.couchdb.user:ada');
      expect(companion.rev.value, '3-abc');
      expect(companion.name.value, 'ada');
      expect(companion.rolesList.value, ['learner', 'manager']);
      expect(companion.userAdmin.value, true);
      expect(companion.joinDate.value, 999);
      expect(companion.firstName.value, 'Ada');
      expect(companion.lastName.value, 'Lovelace');
      expect(companion.middleName.value, 'Augusta');
      expect(companion.email.value, 'ada@example.org');
      expect(companion.planetCode.value, 'planet-a');
      expect(companion.parentCode.value, 'nation');
      expect(companion.phoneNumber.value, '+1 555');
      expect(companion.passwordScheme.value, 'pbkdf2');
      expect(companion.iterations.value, '1000');
      expect(companion.derivedKey.value, 'dk');
      expect(companion.salt.value, 'salty');
      expect(companion.level.value, 'a1');
      expect(companion.language.value, 'English');
      expect(companion.gender.value, 'female');
      expect(companion.dob.value, '1815-12-10');
      expect(companion.age.value, '36');
      expect(companion.birthPlace.value, 'London');
      expect(companion.isArchived.value, false);
    });

    test('keys the row on the document id when present', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
      });
      expect(companion.id.value, 'org.couchdb.user:ada');
      expect(companion.couchId.value, 'org.couchdb.user:ada');
    });

    test('defaults booleans to false and joinDate to 0', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
      });
      expect(companion.userAdmin.value, false);
      expect(companion.isArchived.value, false);
      expect(companion.joinDate.value, 0);
    });

    test('treats an empty roles list as empty, not null', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        'roles': <String>[],
      });
      expect(companion.rolesList.value, isEmpty);
    });
  });

  group('UserMapper.readImageBytes', () {
    test('returns the bytes of a readable local file', () async {
      final dir = await Directory.systemTemp.createTemp('user_image_');
      addTearDown(() => dir.delete(recursive: true));
      final file = File('${dir.path}/photo.jpg');
      await file.writeAsBytes([10, 20, 30]);

      expect(await UserMapper.readImageBytes(file.path), [10, 20, 30]);
    });

    test('returns null for a content:// uri the sandbox cannot open', () async {
      expect(
        await UserMapper.readImageBytes('content://media/photo/1'),
        isNull,
      );
    });

    test('returns null for a nonexistent file path', () async {
      expect(await UserMapper.readImageBytes('/no/such/file/here.jpg'), isNull);
    });

    test('returns null for a null or blank path', () async {
      expect(await UserMapper.readImageBytes(null), isNull);
      expect(await UserMapper.readImageBytes(''), isNull);
      expect(await UserMapper.readImageBytes('   '), isNull);
    });
  });

  group('UserMapper.isManager', () {
    UserRow userWith({bool admin = false, List<String> roles = const []}) =>
        UserRow(
          id: 'u',
          name: 'ada',
          rolesList: roles,
          userAdmin: admin,
          joinDate: 0,
          isArchived: false,
          isUpdated: false,
        );

    test('true when the user is an admin', () {
      expect(UserMapper.isManager(userWith(admin: true)), isTrue);
    });

    test('true when a role is manager (case-insensitive)', () {
      expect(UserMapper.isManager(userWith(roles: ['Manager'])), isTrue);
      expect(UserMapper.isManager(userWith(roles: ['manager'])), isTrue);
    });

    test('false for a plain learner', () {
      expect(UserMapper.isManager(userWith(roles: ['learner'])), isFalse);
    });
  });
}
