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

    test('writes nothing when there are no attachments', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
      });

      // `addImageUrl` touches `userImage` only inside
      // `if (jsonDoc?.has("_attachments") == true)`, so an absent attachments
      // block leaves the stored value alone. Asserting on `.value` cannot see
      // that: `Value.absent().value` is also null, so the assertion passed
      // identically while the mapper was nulling the column — including a
      // local file path a queued photo upload had not sent yet.
      expect(companion.userImage.present, isFalse);
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

    test('writes nothing for an empty attachments object', () {
      final companion = UserMapper.fromDoc({
        '_id': 'org.couchdb.user:ada',
        'name': 'ada',
        '_attachments': <String, dynamic>{},
      });

      // `firstOrNull()?.key` is null, and the assignment is behind
      // `if (key1 != null)`.
      expect(companion.userImage.present, isFalse);
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

    test('a role merely containing "manager" does not count', () {
      // Pins Kotlin `770d6608c` (#16154): `LoginSyncManager.isManager` used to
      // stringify the roles array and substring-match "manager", so a role
      // like "comanager" — or any future role with the word in it — opened the
      // manager door. The port always element-matched, which meant the two
      // apps *disagreed* about who a manager was until that fix; they agree
      // now, and this keeps a refactor toward `contains` from re-splitting
      // them.
      expect(UserMapper.isManager(userWith(roles: ['comanager'])), isFalse);
      expect(UserMapper.isManager(userWith(roles: ['managers'])), isFalse);
      expect(UserMapper.isManager(userWith(roles: ['team manager'])), isFalse);
    });
  });
  group('UserMapper.fromDoc identity and field guards', () {
    UserRow row({
      String id = '1700000000000',
      String? couchId = 'org.couchdb.user:ada',
      String? email,
      String? middleName,
      String? gender,
      String? dob,
      String? age,
    }) {
      return UserRow(
        id: id,
        couchId: couchId,
        name: 'ada',
        rolesList: const [],
        userAdmin: false,
        joinDate: 0,
        email: email,
        middleName: middleName,
        gender: gender,
        dob: dob,
        age: age,
        isArchived: false,
        isUpdated: false,
      );
    }

    Map<String, dynamic> doc([Map<String, dynamic> extra = const {}]) => {
      '_id': 'org.couchdb.user:ada',
      'name': 'ada',
      ...extra,
    };

    test('an existing row keeps its own id', () {
      // `applyJsonToUser` reassigns `id` only `if (id.isNullOrBlank())`.
      expect(
        UserMapper.fromDoc(doc(), existing: row()).id.value,
        '1700000000000',
      );
    });

    test('with no existing row the document id is the key', () {
      expect(UserMapper.fromDoc(doc()).id.value, 'org.couchdb.user:ada');
    });

    test('a document with no _id falls back to a generated key', () {
      // Kotlin's `takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString()`.
      final companion = UserMapper.fromDoc({
        'name': 'ada',
      }, generateLocalId: () => 'generated-1');
      expect(companion.id.value, 'generated-1');
      expect(companion.couchId.value, isNull);
    });

    test('a document with no _id takes the plaintext password', () {
      // `if (_id?.isEmpty() == true) password = …`, read *after*
      // `_id = newId` — so the document's `_id`, not the row's prior one.
      final guest = UserMapper.fromDoc({
        'name': 'ada',
        'password': 'plain',
      }, generateLocalId: () => 'generated-1');
      expect(guest.password.value, 'plain');

      // A real `_users` document never has its password read.
      final normal = UserMapper.fromDoc(doc({'password': 'plain'}));
      expect(normal.password.present, isFalse);
    });

    test('every guarded field keeps a stored value the document omits', () {
      final companion = UserMapper.fromDoc(
        doc(),
        existing: row(
          email: 'ada@example.org',
          middleName: 'Augusta',
          gender: 'female',
          dob: '1815-12-10',
          age: '36',
        ),
      );

      // The six the repository-level tests do not reach.
      expect(companion.email.present, isFalse);
      expect(companion.middleName.present, isFalse);
      expect(companion.gender.present, isFalse);
      expect(companion.dob.present, isFalse);
      expect(companion.age.present, isFalse);
    });

    test('a guarded field is written when the stored one is empty', () {
      // `old.isNullOrEmpty()` — an empty stored value is overwritten with the
      // document's, even when that is empty too.
      final companion = UserMapper.fromDoc(doc(), existing: row(email: ''));
      expect(companion.email.present, isTrue);
      expect(companion.email.value, isNull);
    });

    test('an unguarded field is written even when the document omits it', () {
      // `derived_key`/`salt`/`roles` are assigned unconditionally: the account
      // document is their authority, and an online login has to be able to
      // revoke a role or reset a credential.
      final companion = UserMapper.fromDoc(doc(), existing: row());
      expect(companion.derivedKey.present, isTrue);
      expect(companion.derivedKey.value, isNull);
      expect(companion.rolesList.value, isEmpty);
    });

    test('joinDate treats 0 as the empty value', () {
      final stored = UserRow(
        id: '1700000000000',
        name: 'ada',
        rolesList: const [],
        userAdmin: false,
        joinDate: 1600000000000,
        isArchived: false,
        isUpdated: false,
      );
      expect(
        UserMapper.fromDoc(doc(), existing: stored).joinDate.present,
        isFalse,
      );
      expect(
        UserMapper.fromDoc(
          doc({'joinDate': 1700000000001}),
          existing: stored,
        ).joinDate.value,
        1700000000001,
      );
    });
  });

  group('UserMapper.docIsManager', () {
    test('reads the manager role case-insensitively', () {
      expect(
        UserMapper.docIsManager({
          'roles': ['Manager'],
        }),
        isTrue,
      );
      expect(
        UserMapper.docIsManager({
          'roles': ['learner'],
        }),
        isFalse,
      );
    });

    test('isUserAdmin alone is enough', () {
      expect(
        UserMapper.docIsManager({'roles': <String>[], 'isUserAdmin': true}),
        isTrue,
      );
    });

    test('a document with neither is not a manager', () {
      expect(UserMapper.docIsManager({'name': 'ada'}), isFalse);
    });
  });

  /// Phase 112. `UserEntity.isGuest()`, `UserDao.getGuestUserByName`,
  /// `validateUsername` and every Kotlin UI gate spell this check differently
  /// and still agree, because `createGuestUser` writes the same string to both
  /// id columns. The port had three of those spellings and no path that
  /// creates a guest row, so nothing held them together.
  group('UserMapper.isGuest', () {
    UserRow user({
      String id = '1700000000000',
      String? couchId,
      List<String> roles = const ['learner'],
    }) => UserRow(
      id: id,
      couchId: couchId,
      name: 'ada',
      rolesList: roles,
      userAdmin: false,
      joinDate: 0,
      isArchived: false,
      isUpdated: false,
    );

    /// The row `createGuestUser('ada')` actually produces.
    test('the row createGuestUser writes reads as a guest', () {
      // Built through the production mapper from Kotlin's own
      // `buildGuestUserJson` output, so the shape is not asserted by hand.
      final companion = UserMapper.fromDoc({
        '_id': 'guest_ada',
        'name': 'ada',
        'firstName': 'ada',
        'roles': ['guest'],
      });
      expect(companion.id.value, 'guest_ada');
      expect(companion.couchId.value, 'guest_ada');
      expect(
        UserMapper.isGuest(user(id: 'guest_ada', couchId: 'guest_ada')),
        isTrue,
      );
    });

    test('a member is not a guest by either column', () {
      expect(
        UserMapper.isGuest(user(couchId: 'org.couchdb.user:ada')),
        isFalse,
      );
      // A member registered on this device, before their upload lands.
      expect(UserMapper.isGuest(user()), isFalse);
    });

    /// This is the row the three pre-unification spellings disagreed on, and
    /// the port's *own* code produces it: `_cacheUserDoc` resolves the row a
    /// document belongs to and `fromDoc` keeps that row's `id`, so caching a
    /// guest document against a member registered offline on this device
    /// leaves the guest prefix in `_id` alone.
    test('a row with the prefix in only one column still reads as a guest', () {
      final companion = UserMapper.fromDoc({
        '_id': 'guest_ada',
        'name': 'ada',
        'roles': ['guest'],
      }, existing: user(id: '1700000000000'));
      // Produced, not asserted: the key stays local, the couch id is the
      // guest one.
      expect(companion.id.value, '1700000000000');
      expect(companion.couchId.value, 'guest_ada');

      // The two spellings this phase replaced would split on it — the screens
      // read `id` ("not a guest", so reset-app and voice posting are open),
      // `validateUsername` reads `_id` ("a guest", so the name is
      // re-takeable). One row, two answers.
      const row = ('1700000000000', 'guest_ada');
      expect(row.$1.startsWith('guest'), isFalse);
      expect(row.$2.startsWith('guest'), isTrue);

      // The unified rule gives one answer, and it is the safe one: a
      // privilege withheld, never granted.
      expect(UserMapper.isGuest(user(id: row.$1, couchId: row.$2)), isTrue);
      expect(UserMapper.isGuest(user(id: 'guest_ada', couchId: null)), isTrue);
    });

    /// Six characters, not five. Every id either app writes is
    /// `guest_<username>`, `org.couchdb.user:<name>` or a millisecond
    /// timestamp, so this narrowing changes no producible row — but it does
    /// mean a fixture has to spell the prefix the way `createGuestUser` does.
    test('the prefix is guest_, not guest', () {
      expect(UserMapper.guestIdPrefix, 'guest_');
      expect(UserMapper.isGuestId('guest_ada'), isTrue);
      expect(UserMapper.isGuestId('guest-ada'), isFalse);
      expect(UserMapper.isGuestId('guestbook'), isFalse);
      expect(UserMapper.isGuestId(null), isFalse);
      expect(UserMapper.isGuestId(''), isFalse);
    });

    /// The half of `UserEntity.isGuest()` this helper deliberately does not
    /// carry. Kotlin returns true for a `guest` role without a `learner` role
    /// (`UserEntity.kt:177-181`), and the gates that read it — `TeamFragment`,
    /// `CoursesFragment`, `TakeCourseFragment` — have no port counterpart, so
    /// porting the clause here would widen the settings and voices gates past
    /// their Kotlin originals. Pinned so the omission is visible rather than
    /// forgotten.
    test('a role-only guest is not caught: isGuest() role clause unported', () {
      final roleOnly = user(
        id: 'org.couchdb.user:ada',
        couchId: 'org.couchdb.user:ada',
        roles: const ['guest'],
      );
      expect(UserMapper.isGuest(roleOnly), isFalse);
      // What Kotlin's `isGuest()` would answer for the same row.
      expect(
        roleOnly.rolesList.any((r) => r.toLowerCase() == 'guest') &&
            !roleOnly.rolesList.any((r) => r.toLowerCase() == 'learner'),
        isTrue,
      );
    });
  });
}
