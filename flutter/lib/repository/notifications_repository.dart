import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';

/// Offline notification read/actions from `NotificationsRepositoryImpl`.
class NotificationsRepository {
  NotificationsRepository(
    this._dao, {
    required TeamNotificationDao teamNotificationDao,
    required NewsDao newsDao,
    required TeamTaskDao teamTaskDao,
    required TeamDao teamDao,
    required UserDao userDao,
    DateTime Function()? now,
    PlanetApi? api,
  }) : _teamNotificationDao = teamNotificationDao,
       _newsDao = newsDao,
       _teamTaskDao = teamTaskDao,
       _teamDao = teamDao,
       _userDao = userDao,
       _now = now ?? DateTime.now,
       _api = api;

  static const int storageWarningPercent = 10;

  /// `type` of the per-team chat watermark rows.
  static const String chatNotificationType = 'chat';

  final NotificationDao _dao;
  final TeamNotificationDao _teamNotificationDao;
  final NewsDao _newsDao;
  final TeamTaskDao _teamTaskDao;
  final TeamDao _teamDao;
  final UserDao _userDao;
  final DateTime Function() _now;
  final PlanetApi? _api;

  Stream<List<NotificationRow>> watch(
    String userId, {
    String filter = 'all',
    bool isAdmin = false,
  }) => _dao.watchForUser(userId, filter: filter, isAdmin: isAdmin);

  Stream<int> watchUnreadCount(String userId, {bool isAdmin = false}) =>
      _dao.watchUnreadCount(userId, isAdmin: isAdmin);

  Future<int> markAsRead(Iterable<String> ids) => _dao.markAsRead(ids);

  /// Port of `NotificationsRepositoryImpl.markNotificationAsRead` — a
  /// `summary_`-prefixed id marks every notification of that type for [userId]
  /// read (the dashboard's "mark all" by type); any other id marks a single
  /// row. Server-originated rows are flagged for read-state upload.
  Future<int> markNotificationAsRead(String id, String? userId) {
    if (id.startsWith('summary_')) {
      return _dao.markSummaryAsRead(userId, id.substring('summary_'.length));
    }
    return _dao.markAsRead([id]);
  }

  Future<int> markAllAsRead(String userId) => _dao.markAllAsRead(userId);
  Future<int> delete(String id) => _dao.deleteById(id);

  /// Port of `TransactionSyncManager.syncNotificationReads`.
  ///
  /// PUTs each server-originated notification marked for read-state upload
  /// back to `{dbUrl}/notifications/{id}` and records the fresh `rev` the
  /// server returns. Without this the local "read" never reaches the server,
  /// so a notification read on one device re-surfaces as unread on the next
  /// sync. Swallows per-row failures (the Kotlin `e.printStackTrace()` does
  /// the same) so one bad row cannot strand the rest.
  Future<void> syncNotificationReads(ServerConfig config) async {
    final api = _api;
    if (api == null) return;
    final pending = await _dao.getPendingSyncNotifications();
    if (pending.isEmpty) return;

    final endpoint = Uri.parse(UrlUtils.credentialFreeDbUrl(config));
    final base =
        '${endpoint.scheme}://${endpoint.host}'
        '${endpoint.hasPort ? ':${endpoint.port}' : ''}/notifications';
    final authHeader = UrlUtils.authHeader(config);

    for (final notification in pending) {
      final rev = notification.rev;
      if (rev == null) continue;
      final body = <String, dynamic>{
        '_id': notification.id,
        '_rev': rev,
        'status': 'read',
        'user': notification.userId,
        'message': notification.message,
        'type': notification.type,
        'priority': notification.priority,
        'time': notification.createdAt,
        if (notification.link != null) 'link': notification.link,
      };
      try {
        final result = await api.putJsonObject(
          '$base/${notification.id}',
          body,
          authHeader: authHeader,
        );
        if (result case NetworkSuccess<Map<String, dynamic>>(:final data)) {
          await _dao.markSynced(notification.id, data['rev'] as String?);
        }
      } catch (_) {
        // Mirrors Kotlin's `e.printStackTrace()` — one failed row must not
        // abort the remaining read-state uploads.
      }
    }
  }

  /// Port of `TransactionSyncManager`'s `"notifications"` sync-in: a paginated
  /// `_all_docs` walk over the `notifications` database that upserts each
  /// server document. Unlike the other sync repositories this one does **not**
  /// run `deleteNotIn` afterwards — the Kotlin walk never does either, and a
  /// prune would evict the locally-authored `userId:resource:count` and
  /// `userId:storage` rows that have no server document. Stale server rows
  /// linger until individually deleted, matching Kotlin.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final api = _api;
    if (api == null) {
      return const SyncFailed('notifications sync not available offline');
    }
    final dbUrl = UrlUtils.dbUrl(config);
    final auth = UrlUtils.authHeader(config);
    final countResult = await api.getJsonObject(
      '$dbUrl/notifications/_all_docs?limit=0',
      authHeader: auth,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final total = JsonUtils.getInt('total_rows', countResult.data);
    if (total == 0) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: 100);
    var savedCount = 0;
    var skip = 0;
    while (skip < total) {
      final size = batchSizer.currentSize;
      final timer = Stopwatch()..start();
      final result = await api.getJsonObject(
        '$dbUrl/notifications/_all_docs?include_docs=true&limit=$size&skip=$skip',
        authHeader: auth,
      );
      timer.stop();
      if (result is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        return SyncFailed(describeNetworkFailure(result));
      }
      batchSizer.recordSuccess(timer.elapsedMilliseconds);
      final rows = result.data['rows'];
      if (rows is! List || rows.isEmpty) break;
      final documents = rows
          .whereType<Map<String, dynamic>>()
          .map((row) => JsonUtils.getObject('doc', row))
          .whereType<Map<String, dynamic>>()
          .where((doc) {
            final id = JsonUtils.getString('_id', doc);
            return id.isNotEmpty && !id.startsWith('_design');
          })
          .toList(growable: false);
      savedCount += await bulkInsertFromSync(documents);
      skip += rows.length;
      onProgress?.call(
        SyncProgress(completed: skip.clamp(0, total), total: total),
      );
    }
    return SyncComplete(savedCount);
  }

  /// Port of `NotificationsRepositoryImpl.bulkInsertFromSync` — upserts the
  /// parsed documents, preserving `needsSync`/`isRead` for a row whose read
  /// state changed locally but has not been uploaded yet (a re-pull would
  /// otherwise clobber the local "read" with the server's stale "unread").
  Future<int> bulkInsertFromSync(List<Map<String, dynamic>> documents) async {
    final parsed = documents
        .map(_parseNotification)
        .whereType<NotificationsCompanion>()
        .toList(growable: false);
    if (parsed.isEmpty) return 0;
    final existing = await _dao.getByIds(
      parsed.map((c) => c.id.value).toList(),
    );
    final merged = <NotificationsCompanion>[];
    for (final companion in parsed) {
      final id = companion.id.value;
      final prior = existing[id];
      if (prior != null && prior.needsSync) {
        merged.add(
          companion.copyWith(
            needsSync: const Value(true),
            isRead: Value(prior.isRead),
          ),
        );
      } else {
        merged.add(companion);
      }
    }
    await _dao.upsertAll(merged);
    return merged.length;
  }

  /// Port of `NotificationsRepositoryImpl.parseNotification`. Maps the
  /// CouchDB notification document to the local row, stamping `isFromServer`
  /// and carrying `_rev` so the read-state upload can PUT without a 409.
  NotificationsCompanion? _parseNotification(Map<String, dynamic> doc) {
    final id = JsonUtils.getString('_id', doc);
    if (id.isEmpty) return null;
    final rawType = JsonUtils.getString('type', doc);
    final message = JsonUtils.getString('message', doc);
    final link = JsonUtils.getStringOrNull('link', doc);
    return NotificationsCompanion.insert(
      id: id,
      userId: JsonUtils.getString('user', doc),
      message: Value(message),
      type: Value(rawType),
      subType: Value(extractTeamSubtype(rawType, doc)),
      relatedId: Value(extractRelatedId(rawType, link, doc)),
      link: link == null ? const Value.absent() : Value(link),
      isRead: Value(JsonUtils.getString('status', doc) != 'unread'),
      createdAt: _notificationCreatedAt(doc),
      priority: Value(JsonUtils.getInt('priority', doc)),
      isFromServer: const Value(true),
      rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
    );
  }

  // `time` is the server's ms epoch; missing/zero falls back to now, matching
  // Kotlin's `doc.get("time")?.let { Date(it.asLong) } ?: Date()`.
  int _notificationCreatedAt(Map<String, dynamic> doc) {
    final time = JsonUtils.getLong('time', doc);
    return time > 0 ? time : _now().millisecondsSinceEpoch;
  }

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

  /// Port of `NotificationsRepositoryImpl.getTaskTeamNamesByTaskIds`
  /// (`:191-213`) — task id → the name of the team that task belongs to, for
  /// the `<b>Team</b>:` prefix the task notification carries.
  ///
  /// A task whose team has no cached document is simply absent from the map,
  /// as in the Kotlin (`teamMap[teamId]?.let { … }`), which is what makes the
  /// prefix optional rather than showing an empty bold run.
  ///
  /// **One deliberate divergence.** Kotlin's map comes from
  /// `getTeamNamesByIds`, whose `associateBy` substitutes the literal
  /// `"Unknown Team"` for a null name (`TeamsRepositoryImpl.kt:433`), so a
  /// cached team row with no name still produces a prefix — a bold
  /// "Unknown Team:" ahead of the sentence. This drops the entry instead and
  /// renders the sentence unprefixed, which says less rather than something
  /// wrong. Pinned by a test, so the choice is visible rather than accidental.
  Future<Map<String, String>> taskTeamNamesByTaskIds(List<String> taskIds) =>
      _taskTeamNames(taskIds, keyOf: (task) => task.id);

  /// Port of `getTaskTeamNamesByTaskTitles` (`:255-280`) — the same map keyed
  /// by title, for a notification that carries no task id and can only be
  /// matched on the title parsed out of its message.
  Future<Map<String, String>> taskTeamNamesByTaskTitles(
    List<String> taskTitles,
  ) => _taskTeamNames(taskTitles, byTitle: true, keyOf: (task) => task.title);

  Future<Map<String, String>> _taskTeamNames(
    List<String> keys, {
    bool byTitle = false,
    required String? Function(TeamTaskRow task) keyOf,
  }) async {
    if (keys.isEmpty) return const {};
    final tasks = byTitle
        ? await _teamTaskDao.getByTitles(keys)
        : await _teamTaskDao.getByAnyIds(keys);
    // `teamId` is non-nullable in this schema (Kotlin's is `String?`), so the
    // Kotlin's null test is an emptiness test here.
    final teamIds = <String>{
      for (final task in tasks)
        if (task.teamId.isNotEmpty) task.teamId,
    };
    if (teamIds.isEmpty) return const {};
    final teams = await _teamDao.byIds(teamIds.toList());
    final names = <String, String>{};
    for (final task in tasks) {
      final key = keyOf(task);
      final teamId = task.teamId;
      if (key == null || key.isEmpty || teamId.isEmpty) {
        continue;
      }
      final name = teams[teamId]?.name;
      if (name != null && name.isNotEmpty) names[key] = name;
    }
    return names;
  }

  /// Port of `NotificationsRepositoryImpl.getJoinRequestDetailsBatch`
  /// (`:215-253`) — request document id → (requester, team).
  ///
  /// The Kotlin substitutes the literal English `"Unknown User"` /
  /// `"Unknown Team"` here; the port leaves the field null and lets the
  /// formatter fill it from `l10n`, so the fallback is translated rather than
  /// hardcoded (the same correction Phase 95 made to `MyHealthScreen`'s
  /// `'Unknown'`). Nothing else reads these fields.
  Future<Map<String, JoinRequestDetail>> joinRequestDetailsBatch(
    List<String> relatedIds,
  ) async {
    if (relatedIds.isEmpty) return const {};
    final requests = await _teamDao.byIds(relatedIds);
    if (requests.isEmpty) return const {};
    final teamIds = <String>{
      for (final request in requests.values)
        if (request.teamId != null && request.teamId!.isNotEmpty)
          request.teamId!,
    };
    final teams = teamIds.isEmpty
        ? const <String, TeamRow>{}
        : await _teamDao.byIds(teamIds.toList());

    final details = <String, JoinRequestDetail>{};
    for (final request in requests.values) {
      final userId = request.userId;
      // `getUsersByIds` in one call there, `getById` per requester here: this
      // map is at most as large as the join requests on screen, and adding a
      // batch method to `UserDao` would reach into another lane's section of
      // `app_database.dart` for no measurable gain.
      final user = (userId == null || userId.isEmpty)
          ? null
          : await _userDao.getById(userId);
      details[request.id] = JoinRequestDetail(
        requester: user?.name,
        team: teams[request.teamId]?.name,
      );
    }
    return details;
  }

  /// Port of `NotificationsRepositoryImpl.getJoinRequestDetails` (`:180-189`) —
  /// the single-row lookup `loadNotifications` uses for join requests that
  /// carry no `relatedId` at all. Kotlin passes the null straight through to
  /// `getJoinRequestInfo`, which returns null for a null or empty id, so the
  /// result is the unknown pair; the same holds here.
  Future<JoinRequestDetail> joinRequestDetails(String? relatedId) async {
    if (relatedId == null || relatedId.isEmpty) {
      return const JoinRequestDetail();
    }
    final request = await _teamDao.getById(relatedId);
    if (request == null) return const JoinRequestDetail();
    final userId = request.userId;
    final user = (userId == null || userId.isEmpty)
        ? null
        : await _userDao.getById(userId);
    final teamId = request.teamId;
    final team = (teamId == null || teamId.isEmpty)
        ? null
        : await _teamDao.getById(teamId);
    return JoinRequestDetail(requester: user?.name, team: team?.name);
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

/// The `Pair<String, String>` `getJoinRequestDetails` returns — the requester's
/// name and the team they asked to join.
///
/// Both are nullable where the Kotlin writes `"Unknown User"` / `"Unknown
/// Team"`, so the fallback can be localised at the point of display.
class JoinRequestDetail {
  const JoinRequestDetail({this.requester, this.team});

  final String? requester;
  final String? team;

  @override
  bool operator ==(Object other) =>
      other is JoinRequestDetail &&
      other.requester == requester &&
      other.team == team;

  @override
  int get hashCode => Object.hash(requester, team);
}

/// Port of `model/TeamNotificationInfo.kt`.
class TeamNotificationInfo {
  const TeamNotificationInfo({required this.hasTask, required this.hasChat});

  final bool hasTask;
  final bool hasChat;

  bool get hasAny => hasTask || hasChat;
}

/// The seven display types `NotificationsRepository.KNOWN_TYPES` names
/// (`NotificationsRepository.kt:35`). Everything else is either resolved onto
/// one of them by [resolveNotificationType] or grouped as "Other".
const Set<String> knownNotificationTypes = {
  'join_request',
  'team_join',
  'task',
  'chat',
  'voice_reply',
  'resource',
  'storage',
};

/// Port of `NotificationsRepositoryImpl.resolveType`
/// (`NotificationsRepositoryImpl.kt:337-363`).
///
/// The sync-in stores the server's **raw** `type` — `team`, `newTask`,
/// `replyMessage` — exactly as Kotlin's `parseNotification` does, and Kotlin
/// resolves it on the way out, in `NotificationsViewModel.formatNotification`
/// (`:359`). Every reader downstream of that — the click handler
/// (`NotificationsFragment.handleNotificationClick`), the grouping
/// (`buildNotificationGroups`) and the row's icon/title — sees the resolved
/// value, never the raw one. The port had this function and no caller, so all
/// three readers switched on the raw type and a join request landed in "Other"
/// under a generic bell, doing nothing when tapped.
///
/// A raw `"team"` covers join requests, membership changes and chat posts
/// alike. The locale-independent signal is `linkParams.activeTab ==
/// "applicantTab"` ([extractTeamSubtype], stored as `subType`); message
/// sniffing is the fallback and degrades to `team_join`.
///
/// One deliberate deviation: Kotlin returns `subType.lowercase()` whenever
/// `subType != null`, so a blank stored value would resolve to the empty
/// string. [extractTeamSubtype] only ever produces `join_request` or null, so
/// the case is unreachable through the pull; the non-empty guard keeps a row an
/// older build may have left behind out of a group with no name.
String resolveNotificationType(String type, String message, {String? subType}) {
  final lowerType = type.toLowerCase();
  if (knownNotificationTypes.contains(lowerType)) return lowerType;
  final lower = message.toLowerCase();
  if (lowerType == 'team') {
    if (subType != null && subType.isNotEmpty) return subType.toLowerCase();
    if (lower.contains('requested to join') ||
        lower.contains('wants to join') ||
        lower.contains('solicitado unirse')) {
      return 'join_request';
    }
    if (lower.contains('posted a message on') ||
        lower.contains('posted a new voice') ||
        lower.contains('new voice in') ||
        lower.contains('posted in')) {
      return 'chat';
    }
    return 'team_join';
  }
  if (lowerType == 'newtask') return 'task';
  if (lowerType == 'newresource') return 'resource';
  if (lower.contains('requested to join') || lower.contains('wants to join')) {
    return 'join_request';
  }
  if (lower.contains('added you to') ||
      lower.contains("you've been added") ||
      lower.contains('you have been added')) {
    return 'team_join';
  }
  if (lower.contains('replied to your') ||
      lower.contains('replied on your') ||
      lower.contains('new reply to')) {
    return 'voice_reply';
  }
  if (lower.contains('posted a new voice') ||
      lower.contains('new voice in') ||
      lower.contains('posted in')) {
    return 'chat';
  }
  if (lower.contains('is due') || lower.contains('due:')) return 'task';
  if (lower.contains('storage')) return 'storage';
  if (lower.contains('resource')) return 'resource';
  return 'notification';
}

/// [resolveNotificationType] for a stored row — the shape every reader wants.
String resolvedNotificationType(NotificationRow row) =>
    resolveNotificationType(row.type, row.message, subType: row.subType);

/// Port of `NotificationsRepositoryImpl.extractTeamSubtype` (`:386-390`).
///
/// Returns `"join_request"` for a raw `"team"` notification whose
/// `linkParams.activeTab` is `applicantTab`, otherwise null — the caller falls
/// through to [resolveNotificationType]'s message sniffing. The `"team"` test
/// is exact, not case-insensitive, matching the Kotlin.
String? extractTeamSubtype(String rawType, Map<String, dynamic>? doc) {
  if (rawType != 'team') return null;
  final linkParams = JsonUtils.getObject('linkParams', doc);
  final activeTab = JsonUtils.getString('activeTab', linkParams);
  return activeTab == 'applicantTab' ? 'join_request' : null;
}

/// Port of `NotificationsRepositoryImpl.extractRelatedId` (`:392-399`).
String? extractRelatedId(
  String rawType,
  String? link,
  Map<String, dynamic>? doc,
) {
  switch (rawType) {
    case 'team':
      return JsonUtils.getStringOrNull('item', doc);
    case 'replyMessage':
      return JsonUtils.getStringOrNull('replyTo', doc);
    case 'newTask':
      return extractIdFromLink(link);
    default:
      return null;
  }
}

/// Port of `NotificationsRepositoryImpl.extractIdFromLink` (`:401-406`).
///
/// Mirrors Kotlin's `link.trim('/').split('/')`: only leading and trailing
/// **slashes** are trimmed — not whitespace — and empty mid-segments are kept,
/// so the `view` index lines up with the Kotlin walk exactly. A copy of this
/// that filtered empty segments out disagreed on `/view//abc`, where Kotlin
/// yields the empty segment and the filtered version yields `abc`.
String? extractIdFromLink(String? link) {
  if (link == null || link.trim().isEmpty) return null;
  var trimmed = link;
  while (trimmed.startsWith('/')) {
    trimmed = trimmed.substring(1);
  }
  while (trimmed.endsWith('/')) {
    trimmed = trimmed.substring(0, trimmed.length - 1);
  }
  final segments = trimmed.split('/');
  final viewIndex = segments.indexOf('view');
  if (viewIndex < 0 || viewIndex >= segments.length - 1) return null;
  return segments[viewIndex + 1];
}
