import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/notifications_repository.dart';

void main() {
  late AppDatabase database;
  late NotificationsRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = NotificationsRepository(
      database.notificationDao,
      now: () => DateTime.fromMillisecondsSinceEpoch(1234),
    );
  });
  tearDown(() => database.close());

  test('maintains resource count notification lifecycle', () async {
    await repository.updateResourceNotification('user-1', 3);
    var rows = await repository.watch('user-1').first;
    expect(rows.single.message, '3');
    expect(rows.single.type, 'resource');
    expect(rows.single.createdAt, 1234);

    await repository.markAsRead([rows.single.id]);
    await repository.updateResourceNotification('user-1', 3);
    rows = await repository.watch('user-1').first;
    expect(rows.single.isRead, isTrue, reason: 'unchanged counts stay read');

    await repository.updateResourceNotification('user-1', 4);
    rows = await repository.watch('user-1').first;
    expect(rows.single.isRead, isFalse, reason: 'changed counts become unread');

    await repository.updateResourceNotification('user-1', 0);
    expect(await repository.watch('user-1').first, isEmpty);
  });

  test('warns only at or below ten percent storage availability', () async {
    await repository.updateStorageNotification('user-1', 10);
    expect((await repository.watch('user-1').first).single.message, '10%');

    await repository.updateStorageNotification('user-1', 11);
    expect(await repository.watch('user-1').first, isEmpty);
  });
}
