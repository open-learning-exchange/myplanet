import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

/// Pins the `pendingSyncUsers` / `markUploaded` dirty-flag cycle that
/// [UserUploader] relies on. The predicate mirrors the Kotlin
/// `getPendingSyncUsers`: a row is pending when it has no CouchDB id yet (a
/// freshly created local account) or its `isUpdated` flag is set.
void main() {
  late AppDatabase db;

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<void> seedUser({
    String id = 'user-1',
    String? couchId,
    bool isUpdated = false,
    bool isArchived = false,
    String? passwordScheme,
    String? derivedKey,
    String? salt,
    String? iterations,
  }) => db.userDao.upsert(
    UsersCompanion.insert(
      id: id,
      couchId: couchId == null ? const Value.absent() : Value(couchId),
      name: const Value('ada'),
      rolesList: const Value(['learner']),
      userAdmin: const Value(false),
      joinDate: const Value(0),
      isUpdated: Value(isUpdated),
      isArchived: Value(isArchived),
      passwordScheme: passwordScheme == null
          ? const Value.absent()
          : Value(passwordScheme),
      derivedKey: derivedKey == null ? const Value.absent() : Value(derivedKey),
      salt: salt == null ? const Value.absent() : Value(salt),
      iterations: iterations == null ? const Value.absent() : Value(iterations),
    ),
  );

  group('UserDao.pendingSyncUsers', () {
    test('returns a brand-new account with no couchId', () async {
      await seedUser();

      final pending = await db.userDao.pendingSyncUsers();
      expect(pending.map((u) => u.id), ['user-1']);
    });

    test('returns a synced account whose isUpdated flag is set', () async {
      await seedUser(couchId: 'org.couchdb.user:ada', isUpdated: true);

      final pending = await db.userDao.pendingSyncUsers();
      expect(pending.map((u) => u.id), ['user-1']);
    });

    test('excludes a synced, unedited account', () async {
      await seedUser(couchId: 'org.couchdb.user:ada', isUpdated: false);

      expect(await db.userDao.pendingSyncUsers(), isEmpty);
    });

    test('treats an empty-string couchId as not yet uploaded', () async {
      // A row whose couchId is the empty string — not null — must still be
      // pending, the way the Kotlin `if (id == null || id.isEmpty)` does.
      await seedUser(couchId: '');

      final pending = await db.userDao.pendingSyncUsers();
      expect(pending.map((u) => u.id), ['user-1']);
    });

    test('returns only the dirty rows out of a mixed set', () async {
      await seedUser(
        id: 'synced',
        couchId: 'org.couchdb.user:ada',
        isUpdated: false,
      );
      await seedUser(
        id: 'edited',
        couchId: 'org.couchdb.user:bob',
        isUpdated: true,
      );
      await seedUser(id: 'new');

      final pending = await db.userDao.pendingSyncUsers();
      expect(pending.map((u) => u.id).toSet(), {'edited', 'new'});
    });
  });

  group('UserDao.markUploaded', () {
    test('records the couchId and rev and clears the dirty flag', () async {
      await seedUser();

      await db.userDao.markUploaded(
        'user-1',
        couchId: 'org.couchdb.user:ada',
        rev: '1-abc',
      );

      final saved = await db.userDao.getById('user-1');
      expect(saved?.couchId, 'org.couchdb.user:ada');
      expect(saved?.rev, '1-abc');
      expect(saved?.isUpdated, isFalse);
      expect(await db.userDao.pendingSyncUsers(), isEmpty);
    });

    test(
      'updates the rev without touching the couchId when rev-only',
      () async {
        await seedUser(couchId: 'org.couchdb.user:ada', isUpdated: true);

        await db.userDao.markUploaded('user-1', rev: '2-next');

        final saved = await db.userDao.getById('user-1');
        expect(saved?.couchId, 'org.couchdb.user:ada');
        expect(saved?.rev, '2-next');
        expect(saved?.isUpdated, isFalse);
      },
    );

    test('clears isUpdated even when no id or rev is supplied', () async {
      // A no-op rev update still retires the row from the pending set.
      await seedUser(isUpdated: true);

      await db.userDao.markUploaded('user-1');

      expect((await db.userDao.getById('user-1'))?.isUpdated, isFalse);
    });

    test('leaves other users untouched', () async {
      await seedUser(id: 'user-a', isUpdated: true);
      await seedUser(id: 'user-b', isUpdated: true);

      await db.userDao.markUploaded('user-a', rev: '1-a');

      expect((await db.userDao.getById('user-a'))?.isUpdated, isFalse);
      expect((await db.userDao.getById('user-b'))?.isUpdated, isTrue);
    });
  });

  group('UserDao.upsert', () {
    test('replaces a row on the same id', () async {
      await seedUser(id: 'user-1');
      await seedUser(id: 'user-1', couchId: 'org.couchdb.user:ada');

      final saved = await db.userDao.getById('user-1');
      expect(saved?.couchId, 'org.couchdb.user:ada');
      expect(await db.userDao.count(), 1);
    });
  });

  group('UserDao.getByName', () {
    test('returns the matching user', () async {
      await seedUser(id: 'u1', couchId: 'org.couchdb.user:ada');

      final found = await db.userDao.getByName('ada');
      expect(found?.id, 'u1');
    });

    test('returns null when no user has that name', () async {
      expect(await db.userDao.getByName('nobody'), isNull);
    });

    test('returns only the first when two users share a name', () async {
      // The Kotlin getSingleOrNull would throw on two rows; the port limits
      // to one instead, so login never crashes on a duplicate name.
      await seedUser(id: 'u1', couchId: 'org.couchdb.user:ada');
      await seedUser(id: 'u2', couchId: 'org.couchdb.user:ada2');

      final found = await db.userDao.getByName('ada');
      expect(found, isNotNull);
      expect({'u1', 'u2'}, contains(found?.id));
    });
  });

  group('UserDao.getById', () {
    test('returns the matching user', () async {
      await seedUser(id: 'u1');

      expect((await db.userDao.getById('u1'))?.name, 'ada');
    });

    test('returns null for an unknown id', () async {
      expect(await db.userDao.getById('nope'), isNull);
    });
  });

  group('UserDao.getSavedUsers', () {
    test('excludes archived accounts', () async {
      await seedUser(id: 'u1');
      await seedUser(id: 'u2', isArchived: true);

      final saved = await db.userDao.getSavedUsers();
      expect(saved.map((u) => u.id), ['u1']);
    });

    test('returns all non-archived accounts in insertion order', () async {
      await seedUser(id: 'u1');
      await seedUser(id: 'u2');
      await seedUser(id: 'u3', isArchived: true);

      final saved = await db.userDao.getSavedUsers();
      expect(saved.map((u) => u.id), ['u1', 'u2']);
    });
  });

  group('UserDao.getAllUsers', () {
    test('includes archived accounts, unlike getSavedUsers', () async {
      await seedUser(id: 'u1');
      await seedUser(id: 'u2', isArchived: true);

      final all = await db.userDao.getAllUsers();
      expect(all.map((u) => u.id).toSet(), {'u1', 'u2'});
    });
  });

  group('UserDao.search', () {
    test('matches on the username', () async {
      await seedUser(id: 'u1');

      final results = await db.userDao.search('ada');
      expect(results.map((u) => u.id), ['u1']);
    });

    test('matches on the first name', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'u1',
          name: const Value('ada'),
          firstName: const Value('Augusta'),
          rolesList: const Value([]),
          userAdmin: const Value(false),
          joinDate: const Value(0),
        ),
      );

      expect((await db.userDao.search('gust')).map((u) => u.id), ['u1']);
    });

    test('matches on the last name', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'u1',
          name: const Value('ada'),
          lastName: const Value('Lovelace'),
          rolesList: const Value([]),
          userAdmin: const Value(false),
          joinDate: const Value(0),
        ),
      );

      expect((await db.userDao.search('love')).map((u) => u.id), ['u1']);
    });

    test('returns an empty list when nothing matches', () async {
      await seedUser(id: 'u1');

      expect(await db.userDao.search('xyz'), isEmpty);
    });

    test('matches case-insensitively', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'u1',
          name: const Value('Ada'),
          rolesList: const Value([]),
          userAdmin: const Value(false),
          joinDate: const Value(0),
        ),
      );

      expect((await db.userDao.search('aDA')).map((u) => u.id), ['u1']);
    });
  });

  group('UserDao.ensureSecurityKeys', () {
    test('generates a key and iv when neither is present', () async {
      await seedUser(id: 'u1');

      final result = await db.userDao.ensureSecurityKeys(
        'u1',
        createKey: () => 'test-key',
        createIv: () => 'test-iv',
      );

      expect(result?.key, 'test-key');
      expect(result?.iv, 'test-iv');
      // Persisted to the row.
      final saved = await db.userDao.getById('u1');
      expect(saved?.key, 'test-key');
      expect(saved?.iv, 'test-iv');
    });

    test('returns the existing keys without regenerating', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'u1',
          name: const Value('ada'),
          key: const Value('old-key'),
          iv: const Value('old-iv'),
          rolesList: const Value([]),
          userAdmin: const Value(false),
          joinDate: const Value(0),
        ),
      );

      final result = await db.userDao.ensureSecurityKeys(
        'u1',
        createKey: () => 'should-not-be-used',
        createIv: () => 'should-not-be-used',
      );

      expect(result?.key, 'old-key');
      expect(result?.iv, 'old-iv');
    });

    test('returns null for an unknown user', () async {
      expect(
        await db.userDao.ensureSecurityKeys(
          'nope',
          createKey: () => 'k',
          createIv: () => 'i',
        ),
        isNull,
      );
    });

    test('fills in only the missing key when the iv already exists', () async {
      await db.userDao.upsert(
        UsersCompanion.insert(
          id: 'u1',
          name: const Value('ada'),
          iv: const Value('old-iv'),
          rolesList: const Value([]),
          userAdmin: const Value(false),
          joinDate: const Value(0),
        ),
      );

      final result = await db.userDao.ensureSecurityKeys(
        'u1',
        createKey: () => 'new-key',
        createIv: () => 'should-not-be-used',
      );

      expect(result?.key, 'new-key');
      expect(result?.iv, 'old-iv');
    });
  });

  group('UserDao.updateUserSecurityData', () {
    test('writes all the PBKDF2 fields', () async {
      await seedUser(id: 'u1');

      await db.userDao.updateUserSecurityData(
        localId: 'u1',
        couchId: 'org.couchdb.user:ada',
        rev: '1-abc',
        passwordScheme: 'pbkdf2',
        derivedKey: 'key123',
        salt: 'salt456',
        iterations: '10',
      );

      final saved = await db.userDao.getById('u1');
      expect(saved?.couchId, 'org.couchdb.user:ada');
      expect(saved?.rev, '1-abc');
      expect(saved?.passwordScheme, 'pbkdf2');
      expect(saved?.derivedKey, 'key123');
      expect(saved?.salt, 'salt456');
      expect(saved?.iterations, '10');
    });

    test('preserves existing credentials when the server omits them', () async {
      // #15836: a failed/incomplete server fetch used to write `null` over a
      // previously stored `derived_key`/`salt`, locking the user out of
      // offline PBKDF2 verification. The null fields must be left untouched.
      await seedUser(
        id: 'u1',
        derivedKey: 'oldDerivedKey',
        salt: 'oldSalt',
        passwordScheme: 'oldScheme',
        iterations: '10000',
      );

      await db.userDao.updateUserSecurityData(
        localId: 'u1',
        couchId: 'org.couchdb.user:ada',
        rev: '1-abc',
        passwordScheme: null,
        derivedKey: null,
        salt: null,
        iterations: null,
      );

      final saved = await db.userDao.getById('u1');
      expect(saved?.couchId, 'org.couchdb.user:ada');
      expect(saved?.rev, '1-abc');
      expect(saved?.derivedKey, 'oldDerivedKey');
      expect(saved?.salt, 'oldSalt');
      expect(saved?.passwordScheme, 'oldScheme');
      expect(saved?.iterations, '10000');
    });
  });

  group('UserDao.count', () {
    test('counts zero on an empty table', () async {
      expect(await db.userDao.count(), 0);
    });

    test('counts every row including archived', () async {
      await seedUser(id: 'u1');
      await seedUser(id: 'u2');
      await seedUser(id: 'u3', isArchived: true);

      expect(await db.userDao.count(), 3);
    });
  });

  group('UserDao.getUsersForHealthSync', () {
    test('returns only users with a non-blank couch id', () async {
      await seedUser(id: 'synced', couchId: 'org.couchdb.user:alice');
      await seedUser(id: 'guest-1'); // null couchId
      await seedUser(id: 'blank', couchId: '');
      await seedUser(id: 'whitespace', couchId: '  ');

      final users = await db.userDao.getUsersForHealthSync();

      expect(users.map((u) => u.id), ['synced']);
    });
  });

  group('UserDao.markUserKeyIvSaved', () {
    test('writes the key and iv onto the named row only', () async {
      await seedUser(id: 'user-1');
      await seedUser(id: 'user-2');

      await db.userDao.markUserKeyIvSaved('user-1', 'KEY', 'IV');

      final updated = await db.userDao.getById('user-1');
      expect(updated?.key, 'KEY');
      expect(updated?.iv, 'IV');
      final untouched = await db.userDao.getById('user-2');
      expect(untouched?.key, isNull);
      expect(untouched?.iv, isNull);
    });

    test('is a no-op for an unknown id', () async {
      await db.userDao.markUserKeyIvSaved('missing', 'KEY', 'IV');
      expect(await db.userDao.getById('missing'), isNull);
    });
  });
}
