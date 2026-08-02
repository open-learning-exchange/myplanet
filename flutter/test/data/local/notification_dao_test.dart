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
      (await database.notificationDao.watchForUser('user-1').first)
          .map((row) => row.id),
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
}
