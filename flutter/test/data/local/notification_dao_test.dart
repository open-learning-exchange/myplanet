import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';

void main() {
  late AppDatabase database;

  setUp(() => database = AppDatabase.memory());
  tearDown(() => database.close());

  test('filters, orders, marks, and deletes notifications', () async {
    await database.notificationDao.upsert(
      NotificationsCompanion.insert(
        id: 'older',
        userId: 'user-1',
        message: const Value('Older'),
        createdAt: 10,
      ),
    );
    await database.notificationDao.upsert(
      NotificationsCompanion.insert(
        id: 'newer',
        userId: 'user-1',
        message: const Value('Newer'),
        createdAt: 20,
      ),
    );
    await database.notificationDao.upsert(
      NotificationsCompanion.insert(
        id: 'other-user',
        userId: 'user-2',
        createdAt: 30,
      ),
    );

    expect(
      (await database.notificationDao.watchForUser('user-1').first).map(
        (row) => row.id,
      ),
      ['newer', 'older'],
    );
    expect(await database.notificationDao.watchUnreadCount('user-1').first, 2);

    await database.notificationDao.markAsRead(['newer']);
    expect(
      (await database.notificationDao
              .watchForUser('user-1', filter: 'read')
              .first)
          .single
          .id,
      'newer',
    );
    expect(await database.notificationDao.watchUnreadCount('user-1').first, 1);

    await database.notificationDao.markAllAsRead('user-1');
    expect(await database.notificationDao.watchUnreadCount('user-1').first, 0);
    await database.notificationDao.deleteById('older');
    expect(
      await database.notificationDao.watchForUser('user-1').first,
      hasLength(1),
    );
  });

  group('read-state flagging for upload', () {
    /// Seeds one already-read-and-synced server notification plus one unread
    /// one, both for `user-1`.
    Future<void> seed({String type = 'task'}) async {
      await database.notificationDao.upsert(
        NotificationsCompanion.insert(
          id: 'read-long-ago',
          userId: 'user-1',
          type: Value(type),
          createdAt: 10,
          isRead: const Value(true),
          isFromServer: const Value(true),
          needsSync: const Value(false),
          rev: const Value('1-abc'),
        ),
      );
      await database.notificationDao.upsert(
        NotificationsCompanion.insert(
          id: 'still-unread',
          userId: 'user-1',
          type: Value(type),
          createdAt: 20,
          isFromServer: const Value(true),
          rev: const Value('1-def'),
        ),
      );
    }

    /// Regression: `markAllAsRead` ran as two updates, and the second could
    /// only re-select the rows it wanted by `is_read = 1` — which, after the
    /// first update, matches every row the user had *ever* read. So one "mark
    /// all read" tap re-flagged the entire history and the next
    /// `syncNotificationReads` PUT every one of those documents back to
    /// CouchDB. The Kotlin does it in one statement with a `CASE WHEN`, whose
    /// `WHERE ... AND isRead = 0` scopes both effects together.
    test('markAllAsRead flags only the rows it just marked', () async {
      await seed();

      expect(await database.notificationDao.markAllAsRead('user-1'), 1);

      final pending = await database.notificationDao
          .getPendingSyncNotifications();
      expect(
        pending.map((row) => row.id),
        ['still-unread'],
        reason:
            'a notification read and synced earlier must not be re-uploaded',
      );
    });

    test('markSummaryAsRead flags only the rows it just marked', () async {
      await seed();

      expect(
        await database.notificationDao.markSummaryAsRead('user-1', 'task'),
        1,
      );

      final pending = await database.notificationDao
          .getPendingSyncNotifications();
      expect(pending.map((row) => row.id), ['still-unread']);
    });

    /// The count is "how many were marked", not "how many exist" — the port's
    /// `markSummaryAsRead` had dropped the `is_read = 0` filter from its first
    /// update too, so it reported every row of the type.
    test('markSummaryAsRead counts only the previously-unread rows', () async {
      await seed();
      expect(
        await database.notificationDao.markSummaryAsRead('user-1', 'task'),
        1,
      );
      expect(
        await database.notificationDao.markSummaryAsRead('user-1', 'task'),
        0,
      );
    });

    test('a locally-authored row is never flagged for upload', () async {
      await database.notificationDao.upsert(
        NotificationsCompanion.insert(
          id: 'user-1:storage',
          userId: 'user-1',
          type: const Value('storage'),
          createdAt: 30,
        ),
      );

      await database.notificationDao.markAllAsRead('user-1');

      expect(
        await database.notificationDao.getPendingSyncNotifications(),
        isEmpty,
      );
    });
  });
}
