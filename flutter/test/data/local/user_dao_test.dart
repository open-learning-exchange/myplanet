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
  }) => db.userDao.upsert(
    UsersCompanion.insert(
      id: id,
      couchId: couchId == null ? const Value.absent() : Value(couchId),
      name: const Value('ada'),
      rolesList: const Value(['learner']),
      userAdmin: const Value(false),
      joinDate: const Value(0),
      isUpdated: Value(isUpdated),
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
}
