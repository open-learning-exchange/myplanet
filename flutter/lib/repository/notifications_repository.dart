import 'package:drift/drift.dart';

import '../data/local/app_database.dart';

/// Offline notification read/actions from `NotificationsRepositoryImpl`.
class NotificationsRepository {
  NotificationsRepository(this._dao, {DateTime Function()? now})
    : _now = now ?? DateTime.now;

  static const int storageWarningPercent = 10;

  final NotificationDao _dao;
  final DateTime Function() _now;

  Stream<List<NotificationRow>> watch(
    String userId, {
    String filter = 'all',
  }) => _dao.watchForUser(userId, filter: filter);

  Stream<int> watchUnreadCount(String userId) =>
      _dao.watchUnreadCount(userId);

  Future<int> markAsRead(Iterable<String> ids) => _dao.markAsRead(ids);
  Future<int> markAllAsRead(String userId) => _dao.markAllAsRead(userId);
  Future<int> delete(String id) => _dao.deleteById(id);

  Future<void> updateResourceNotification(String userId, int count) async {
    final id = '$userId:resource:count';
    if (count <= 0) {
      await _dao.deleteById(id);
      return;
    }
    final existing = await _dao.getById(id);
    if (existing?.message == '$count') return;
    await _dao.upsert(
      NotificationsCompanion.insert(
        id: id,
        userId: userId,
        message: Value('$count'),
        type: const Value('resource'),
        relatedId: Value('$count'),
        createdAt: _now().millisecondsSinceEpoch,
      ),
    );
  }

  Future<void> updateStorageNotification(
    String userId,
    int availablePercent,
  ) async {
    final id = '$userId:storage';
    if (availablePercent > storageWarningPercent) {
      await _dao.deleteById(id);
      return;
    }
    final existing = await _dao.getById(id);
    if (existing?.message == '$availablePercent%') return;
    await _dao.upsert(
      NotificationsCompanion.insert(
        id: id,
        userId: userId,
        message: Value('$availablePercent%'),
        type: const Value('storage'),
        relatedId: const Value('storage'),
        createdAt: _now().millisecondsSinceEpoch,
      ),
    );
  }
}
