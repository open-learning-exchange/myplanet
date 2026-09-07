/// Port of the `NotificationConfig` data class and the one factory
/// `TaskNotificationWorker` uses, from `utils/NotificationUtils.kt`.
///
/// `NotificationUtils` also builds survey, join-request, storage, resource and
/// summary configs. None of them is ported here: nothing in the Kotlin calls
/// those factories from a path this port has, so porting them would add library
/// code with no caller — the thing this port keeps finding and removing. They
/// are a small, mechanical addition when a caller appears.
library;

/// The Android notification channels `NotificationUtils.createNotificationChannels`
/// registers. Only the ones a ported path uses are declared.
///
/// The ids are the Kotlin's own strings: a channel id is persisted by the OS
/// once created, and the two apps may sit on the same handset during the
/// migration, so a renamed id would silently create a second channel with
/// different importance.
abstract final class NotificationChannels {
  static const tasks = 'task_notifications';
  static const tasksName = 'Task Notifications';
  static const tasksDescription = 'Task assignments and deadlines';
}

/// `NotificationUtils.TYPE_*`. Only `task` has a ported producer.
///
/// `route_reachability_test.dart` drives **every** constant here through
/// `NotificationDestinationResolver.resolveFor` and requires a destination, so
/// a new type cannot be added without an arm and fall silently to the
/// resolver's `default`. That is a live risk rather than a theoretical one: the
/// resolver is a port of the bell row's click handler, which has no `survey` or
/// `course` arm at all, and those are two of Kotlin's six `TYPE_*` values.
abstract final class NotificationTypes {
  static const task = 'task';
}

/// `NotificationCompat.PRIORITY_DEFAULT` / `PRIORITY_HIGH`, the two values the
/// ported factory produces.
enum NotificationPriority { defaultPriority, high }

/// One notification to show, independent of the plugin that shows it.
///
/// Field-for-field the subset of Kotlin's `NotificationConfig` that the task
/// path sets. `bigTextStyle`, `autoCancel`, `actionable` and `category` are
/// carried because the Kotlin builder applies them and they change what the
/// user sees; the unported `silent`/`targetActivity` fields are omitted rather
/// than carried unused.
class NotificationConfig {
  const NotificationConfig({
    required this.id,
    required this.type,
    required this.title,
    required this.message,
    required this.priority,
    this.actionable = false,
    this.bigTextStyle = true,
    this.autoCancel = true,
    this.relatedId,
  });

  /// The stable string key. The OS notification id is derived from it the same
  /// way the Kotlin does (`id.hashCode()`), so re-showing the same logical
  /// notification replaces rather than stacks.
  final String id;
  final String type;
  final String title;
  final String message;
  final NotificationPriority priority;

  /// `buildNotification`'s `if (config.actionable) addNotificationActions(…)`
  /// (`:357-359`). Kotlin defaults it false and every `create*Notification`
  /// factory that a user is meant to act on sets it true.
  final bool actionable;
  final bool bigTextStyle;
  final bool autoCancel;
  final String? relatedId;

  /// Port of `NotificationUtils.createTaskNotification`.
  ///
  /// The title and body are the Kotlin's own hardcoded English. `NotificationUtils`
  /// does not read `strings.xml` for these, so there is nothing translated to
  /// port; a background isolate has no `BuildContext` to resolve `.arb` lookups
  /// against either. Localising them would be a divergence from the Kotlin, and
  /// is noted in the migration doc as an open item rather than done silently.
  factory NotificationConfig.task({
    required String taskId,
    required String taskTitle,
    required String deadlineLabel,
    required bool urgent,
  }) => NotificationConfig(
    id: taskId,
    type: NotificationTypes.task,
    title: '✅ New Task Assigned',
    message: '$taskTitle\nDue: $deadlineLabel',
    priority: urgent
        ? NotificationPriority.high
        : NotificationPriority.defaultPriority,
    // `createTaskNotification` sets `actionable = true`, so the notification
    // carries *Mark as Read* and *View Task*. The port dropped both until
    // Phase 130 — see `notificationActionsFor`.
    actionable: true,
    relatedId: taskId,
  );
}
