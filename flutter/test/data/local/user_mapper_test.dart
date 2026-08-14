import 'dart:convert';

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
    UserRow user({String? couchId, String? password, String? iterations}) =>
        UserRow(
          id: 'user-1',
          couchId: couchId,
          name: 'ada',
          rolesList: const ['learner'],
          userAdmin: false,
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
      expect(UserMapper.toDoc(user(iterations: 'not-a-number'))['iterations'], 10);
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
  });
}
