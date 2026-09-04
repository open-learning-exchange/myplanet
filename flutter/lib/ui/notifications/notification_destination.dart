import '../../data/local/app_database.dart';
import '../../repository/notifications_repository.dart';

enum NotificationDestinationKind {
  resources,
  storage,
  teamTasks,
  teamMembers,
  teamJoin,
  teamChat,
  voiceReply,
}

class NotificationDestination {
  const NotificationDestination(this.kind, {this.teamId, this.voiceId});

  final NotificationDestinationKind kind;
  final String? teamId;
  final String? voiceId;

  @override
  bool operator ==(Object other) =>
      other is NotificationDestination &&
      other.kind == kind &&
      other.teamId == teamId &&
      other.voiceId == voiceId;

  @override
  int get hashCode => Object.hash(kind, teamId, voiceId);
}

/// Resolves an in-app notification to the screen the Kotlin app opens from
/// `NotificationsFragment.handleNotificationClick`.
///
/// Keeping the database lookups out of the widget makes the navigation policy
/// independently testable. Task notifications carry a task id and therefore
/// need the cached task to discover their team; join requests carry the id of
/// the request document, whose `teamId` identifies the destination team.
///
/// `team_join` and `chat` carry the team id directly as `relatedId` (server
/// notifications set `item` to the team id), so no lookup is needed.
/// `voice_reply` carries the news/voice id as `relatedId`.
///
/// The switch is on the **resolved** type, matching Kotlin: the click handler
/// receives `Notification.type`, which `formatNotification` produced through
/// `resolveType`. Switching on the stored raw type instead — `team`,
/// `newTask`, `replyMessage` — matched no arm at all, so every notification the
/// server sent fell to `default` and tapping it did nothing.
class NotificationDestinationResolver {
  const NotificationDestinationResolver({
    required TeamTaskDao taskDao,
    required TeamDao teamDao,
  }) : _taskDao = taskDao,
       _teamDao = teamDao;

  final TeamTaskDao _taskDao;
  final TeamDao _teamDao;

  Future<NotificationDestination?> resolve(NotificationRow notification) async {
    switch (resolvedNotificationType(notification)) {
      case 'storage':
        return const NotificationDestination(
          NotificationDestinationKind.storage,
        );
      case 'resource':
        return const NotificationDestination(
          NotificationDestinationKind.resources,
        );
      case 'team_join':
        final teamId = _nonBlank(notification.relatedId);
        return teamId == null
            ? null
            : NotificationDestination(
                NotificationDestinationKind.teamJoin,
                teamId: teamId,
              );
      case 'chat':
        final teamId = _nonBlank(notification.relatedId);
        return teamId == null
            ? null
            : NotificationDestination(
                NotificationDestinationKind.teamChat,
                teamId: teamId,
              );
      case 'voice_reply':
        final voiceId = _nonBlank(notification.relatedId);
        return voiceId == null
            ? null
            : NotificationDestination(
                NotificationDestinationKind.voiceReply,
                voiceId: voiceId,
              );
      case 'task':
        final relatedId = _nonBlank(notification.relatedId);
        if (relatedId == null) return null;
        final task = await _taskDao.getById(relatedId);
        return NotificationDestination(
          NotificationDestinationKind.teamTasks,
          // `resolveAndOpenTeam` is `resolve(relatedId) ?: relatedId`
          // (`NotificationsFragment.kt:142-148`): an uncached task still opens
          // the team, using the id the notification carried. Returning null
          // instead made the tap silently do nothing — and a server task's row
          // is *never* cached, because the port has no `tasks` sync walk
          // (Phase 116's D16).
          teamId: _nonBlank(task?.teamId) ?? relatedId,
        );
      case 'join_request':
        final relatedId = _nonBlank(notification.relatedId);
        if (relatedId == null) return null;
        // `getJoinRequestTeamId` strips the `join_request_` prefix the
        // system-tray path puts on the id before looking the document up
        // (`NotificationsRepositoryImpl.kt:169-177`).
        final requestId = relatedId.startsWith('join_request_')
            ? relatedId.substring('join_request_'.length)
            : relatedId;
        final request = await _teamDao.getById(requestId);
        return NotificationDestination(
          NotificationDestinationKind.teamMembers,
          // The strip happens *inside* `getJoinRequestTeamId`, so
          // `resolveAndOpenTeam`'s `?: relatedId` falls back to the id the
          // notification carried, prefix and all — not the stripped one.
          teamId: _nonBlank(request?.teamId) ?? relatedId,
        );
      default:
        return null;
    }
  }

  static String? _nonBlank(String? value) {
    final trimmed = value?.trim();
    return trimmed == null || trimmed.isEmpty ? null : trimmed;
  }
}
