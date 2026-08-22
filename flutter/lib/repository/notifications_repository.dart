import 'package:drift/drift.dart';

import '../data/local/app_database.dart';

/// Offline notification read/actions from `NotificationsRepositoryImpl`.
class NotificationsRepository {
  NotificationsRepository(
    this._dao, {
    required TeamNotificationDao teamNotificationDao,
    required NewsDao newsDao,
    required TeamTaskDao teamTaskDao,
    DateTime Function()? now,
  }) : _teamNotificationDao = teamNotificationDao,
       _newsDao = newsDao,
       _teamTaskDao = teamTaskDao,
       _now = now ?? DateTime.now;

  static const int storageWarningPercent = 10;

  /// `type` of the per-team chat watermark rows.
  static const String chatNotificationType = 'chat';

  final NotificationDao _dao;
  final TeamNotificationDao _teamNotificationDao;
  final NewsDao _newsDao;
  final TeamTaskDao _teamTaskDao;
  final DateTime Function() _now;

  Stream<List<NotificationRow>> watch(String userId, {String filter = 'all'}) =>
      _dao.watchForUser(userId, filter: filter);

  Stream<int> watchUnreadCount(String userId) => _dao.watchUnreadCount(userId);

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
    // Reaching here means the count changed, which is what
    // `NotificationsRepositoryImpl.updateResourceNotification` treats as
    // "resurface this": it resets `isRead` and stamps a new `createdAt`.
    // `isRead` has to be passed explicitly — `insertOnConflictUpdate` only
    // writes the columns the companion actually carries, so an absent value
    // would leave a previous `markAsRead` in place.
    await _dao.upsert(
      NotificationsCompanion.insert(
        id: id,
        userId: userId,
        message: Value('$count'),
        type: const Value('resource'),
        relatedId: Value('$count'),
        isRead: const Value(false),
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
        isRead: const Value(false),
        createdAt: _now().millisecondsSinceEpoch,
      ),
    );
  }

  /// Port of `NotificationsRepositoryImpl.getTeamNotifications` — the chat and
  /// task alert dots the dashboard draws on each team tile.
  ///
  /// Two quirks are reproduced rather than fixed, because the badges are
  /// cosmetic and diverging would make the two apps disagree:
  ///
  /// * **`hasTask` is not per-team.** The Kotlin queries the user's tasks due
  ///   between now and this time tomorrow *once*, then writes that single
  ///   boolean onto every team in the map. So a task due in one team lights the
  ///   task dot on all of them.
  /// * **`hasChat` needs a watermark row to exist.** It is
  ///   `notification != null && notification.lastCount < chatCount`, so a team
  ///   whose voices the user has never opened has no row and shows no dot, no
  ///   matter how many posts it has. The dot means "new since you last looked",
  ///   and never-looked reads as nothing new.
  Future<Map<String, TeamNotificationInfo>> getTeamNotifications(
    List<String> teamIds,
    String userId,
  ) async {
    if (teamIds.isEmpty) return const {};

    final watermarks = await _teamNotificationDao.byTypeAndParentIds(
      chatNotificationType,
      teamIds,
    );
    final watermarkByTeam = <String, TeamNotificationRow>{
      for (final row in watermarks)
        if (row.parentId != null) row.parentId!: row,
    };

    final chatCounts = await _newsDao.teamChatCounts(teamIds);

    // `Calendar.getInstance().apply { add(DAY_OF_YEAR, 1) }` — the same instant
    // tomorrow, so the window is "overdue or due within a day". Note the start
    // is *now*, meaning a task whose deadline has already passed is outside the
    // window and lights nothing.
    final now = _now();
    final tasks = await _teamTaskDao.tasksForUserBetween(
      userId,
      now.millisecondsSinceEpoch,
      now.add(const Duration(days: 1)).millisecondsSinceEpoch,
    );
    final hasTask = tasks.isNotEmpty;

    return {
      for (final teamId in teamIds)
        teamId: TeamNotificationInfo(
          hasTask: hasTask,
          hasChat:
              watermarkByTeam[teamId] != null &&
              watermarkByTeam[teamId]!.lastCount < (chatCounts[teamId] ?? 0),
        ),
    };
  }

  /// Port of `VoicesRepositoryImpl.updateTeamNotification` — moves a team's
  /// "seen" watermark to [count].
  ///
  /// Called when the user opens a team's voices, exactly where
  /// `TeamsVoicesViewModel` calls it with the loaded post count. The row id is
  /// derived from the team rather than a fresh UUID: the Kotlin mints a UUID
  /// but always looks the row up by `(parentId, type)` first, so a derived key
  /// is the same row with one fewer way to end up with duplicates.
  Future<void> updateTeamNotification(String teamId, int count) async {
    final existing = await _teamNotificationDao.findByParentAndType(
      teamId,
      chatNotificationType,
    );
    await _teamNotificationDao.upsert(
      TeamNotificationsCompanion.insert(
        id: existing?.id ?? '$teamId:$chatNotificationType',
        parentId: Value(teamId),
        type: const Value(chatNotificationType),
        lastCount: Value(count),
      ),
    );
  }
}

/// Port of `model/TeamNotificationInfo.kt`.
class TeamNotificationInfo {
  const TeamNotificationInfo({required this.hasTask, required this.hasChat});

  final bool hasTask;
  final bool hasChat;

  bool get hasAny => hasTask || hasChat;
}
