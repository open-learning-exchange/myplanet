import '../../data/local/app_database.dart';

enum NotificationDestinationKind { resources, storage, teamTasks, teamMembers }

class NotificationDestination {
  const NotificationDestination(this.kind, {this.teamId});

  final NotificationDestinationKind kind;
  final String? teamId;

  @override
  bool operator ==(Object other) =>
      other is NotificationDestination &&
      other.kind == kind &&
      other.teamId == teamId;

  @override
  int get hashCode => Object.hash(kind, teamId);
}

/// Resolves an in-app notification to the screen the Kotlin app opens from
/// `NotificationsFragment.handleNotificationClick`.
///
/// Keeping the database lookups out of the widget makes the navigation policy
/// independently testable. Task notifications carry a task id and therefore
/// need the cached task to discover their team; join requests carry the id of
/// the request document, whose `teamId` identifies the destination team.
class NotificationDestinationResolver {
  const NotificationDestinationResolver({
    required TeamTaskDao taskDao,
    required TeamDao teamDao,
  }) : _taskDao = taskDao,
       _teamDao = teamDao;

  final TeamTaskDao _taskDao;
  final TeamDao _teamDao;

  Future<NotificationDestination?> resolve(NotificationRow notification) async {
    switch (notification.type.trim().toLowerCase()) {
      case 'storage':
        return const NotificationDestination(
          NotificationDestinationKind.storage,
        );
      case 'resource':
        return const NotificationDestination(
          NotificationDestinationKind.resources,
        );
      case 'task':
        final relatedId = _nonBlank(notification.relatedId);
        if (relatedId == null) return null;
        final task = await _taskDao.getById(relatedId);
        final teamId = _nonBlank(task?.teamId);
        return teamId == null
            ? null
            : NotificationDestination(
                NotificationDestinationKind.teamTasks,
                teamId: teamId,
              );
      case 'join_request':
        final relatedId = _nonBlank(notification.relatedId);
        if (relatedId == null) return null;
        final request = await _teamDao.getById(relatedId);
        final teamId = _nonBlank(request?.teamId);
        return teamId == null
            ? null
            : NotificationDestination(
                NotificationDestinationKind.teamMembers,
                teamId: teamId,
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
