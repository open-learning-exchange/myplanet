import '../../data/local/app_database.dart';
import '../../repository/notifications_repository.dart';
import '../router.dart';

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

  /// Resolves an in-app notification row.
  Future<NotificationDestination?> resolve(NotificationRow notification) =>
      resolveFor(
        type: resolvedNotificationType(notification),
        relatedId: notification.relatedId,
      );

  /// The same policy over the two fields it actually reads.
  ///
  /// Split out so the **system-tray** tap can share it. A tray tap carries no
  /// row — Kotlin puts `notification_type` and `related_id` on the intent
  /// (`NotificationUtils.kt:401-406`) and `handleNotificationIntent`'s
  /// `auto_navigate` branch switches on those two alone — and the type it
  /// carries is already `NotificationUtils.TYPE_*`, i.e. the same *resolved*
  /// spelling `resolvedNotificationType` produces. So one switch serves both,
  /// which is the point: a tray tap and a row tap on the same notification
  /// should not land in different places, and Kotlin's own two paths agree on
  /// the destination (`NotificationsFragment.kt:122-124` and
  /// `DashboardViewModel.kt:216-223` both open the team's tasks page).
  ///
  /// They do differ in one respect, and this keeps the *row* tap's behaviour
  /// for both. Kotlin's button path goes through
  /// `TeamsRepositoryImpl.getTaskTeamInfo`, which returns null when the team
  /// row is absent and therefore navigates nowhere at all; the row path goes
  /// through `getTaskDetails` and `resolveAndOpenTeam`'s `?: relatedId`, which
  /// still opens something. Sharing the forgiving one means a tray tap
  /// navigates in a case where the Android app silently does nothing — a
  /// divergence recorded in `PHASE_130_NOTES.md`, not an accident.
  Future<NotificationDestination?> resolveFor({
    required String type,
    String? relatedId,
  }) async {
    switch (type) {
      case 'storage':
        return const NotificationDestination(
          NotificationDestinationKind.storage,
        );
      case 'resource':
        return const NotificationDestination(
          NotificationDestinationKind.resources,
        );
      case 'team_join':
        final teamId = _nonBlank(relatedId);
        return teamId == null
            ? null
            : NotificationDestination(
                NotificationDestinationKind.teamJoin,
                teamId: teamId,
              );
      case 'chat':
        final teamId = _nonBlank(relatedId);
        return teamId == null
            ? null
            : NotificationDestination(
                NotificationDestinationKind.teamChat,
                teamId: teamId,
              );
      case 'voice_reply':
        final voiceId = _nonBlank(relatedId);
        return voiceId == null
            ? null
            : NotificationDestination(
                NotificationDestinationKind.voiceReply,
                voiceId: voiceId,
              );
      case 'task':
        final id = _nonBlank(relatedId);
        if (id == null) return null;
        final task = await _taskDao.getById(id);
        return NotificationDestination(
          NotificationDestinationKind.teamTasks,
          // `resolveAndOpenTeam` is `resolve(relatedId) ?: relatedId`
          // (`NotificationsFragment.kt:142-148`): an uncached task still opens
          // the team, using the id the notification carried. Returning null
          // instead made the tap silently do nothing — and a server task's row
          // is *never* cached, because the port has no `tasks` sync walk
          // (Phase 116's D16).
          //
          // The tray path is the one case where the row *is* there:
          // `TaskDeadlineNotifier` selects the task out of `team_tasks` to
          // notify about it, so a tray tap resolves a real team id.
          teamId: _nonBlank(task?.teamId) ?? id,
        );
      case 'join_request':
        final id = _nonBlank(relatedId);
        if (id == null) return null;
        // `getJoinRequestTeamId` strips the `join_request_` prefix the
        // system-tray path puts on the id before looking the document up
        // (`NotificationsRepositoryImpl.kt:169-177`).
        final requestId = id.startsWith('join_request_')
            ? id.substring('join_request_'.length)
            : id;
        final request = await _teamDao.getById(requestId);
        return NotificationDestination(
          NotificationDestinationKind.teamMembers,
          // The strip happens *inside* `getJoinRequestTeamId`, so
          // `resolveAndOpenTeam`'s `?: relatedId` falls back to the id the
          // notification carried, prefix and all — not the stripped one.
          teamId: _nonBlank(request?.teamId) ?? id,
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

/// The in-app location a [NotificationDestination] opens.
///
/// Lifted out of `notifications_screen.dart`'s `_openNotification`, where it
/// was a `switch` inside a private widget method, for two reasons.
///
/// The tray tap needs the same mapping, and a second copy of it is how a tray
/// tap and a row tap on the same notification come to disagree — the shape
/// Phase 124 avoided by building the row formatter on this same enum.
///
/// And it is now *enumerable*. `route_reachability_test.dart` had
/// `notifications_screen.dart` in its `declared` blind-spot map, because the
/// location was built in a switch and handed to `context.go` as a variable, so
/// the only rule that could see the arms was the one that scans for `Routes.x`
/// mentions anywhere in a file. A total function over the enum can be walked
/// exhaustively instead: every kind, every location, checked against the real
/// route table. A new kind with no arm will not compile, and an arm naming a
/// path the router does not serve fails that test.
String notificationDestinationLocation(NotificationDestination destination) =>
    switch (destination.kind) {
      NotificationDestinationKind.resources => Routes.resources,
      // Kotlin opens the *system* storage settings here
      // (`ACTION_INTERNAL_STORAGE_SETTINGS`, `NotificationsFragment.kt:131`).
      // The port has its own free-up-space screen and no such channel, so this
      // is the established divergence rather than a new one.
      NotificationDestinationKind.storage => Routes.storageManagement,
      NotificationDestinationKind.teamTasks =>
        '${Routes.teams}/${destination.teamId}/tasks',
      NotificationDestinationKind.teamMembers =>
        '${Routes.teams}/${destination.teamId}/members?tab=requests',
      NotificationDestinationKind.teamJoin =>
        '${Routes.teams}/${destination.teamId}',
      // The Flutter port has no team-chat tab yet (the upstream opens the
      // team's ChatPage), so the team detail is the closest destination.
      NotificationDestinationKind.teamChat =>
        '${Routes.teams}/${destination.teamId}',
      NotificationDestinationKind.voiceReply =>
        '${Routes.voices}/${destination.voiceId}',
    };
