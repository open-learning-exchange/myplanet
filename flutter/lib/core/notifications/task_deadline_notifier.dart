import 'package:intl/intl.dart';

import '../../data/local/app_database.dart';
import '../../repository/notifications_repository.dart';
import '../../repository/team_tasks_repository.dart';
import '../prefs/planet_prefs.dart';
import '../system/disk_stats.dart';
import 'notification_config.dart';
import 'notification_presenter.dart';

/// Port of `services/TaskNotificationWorker.kt`.
///
/// The last of the three `WorkManager` jobs that needed a home in this port.
/// `AutoSyncWorker` landed in Phase 38; this is the deadline-reminder half, and
/// it is the one job whose *point* is to run with no user present — a reminder
/// that only arrives while you have the app open is not a reminder.
///
/// The policy lives here as ordinary Dart, taking the platform pieces as
/// injected seams ([NotificationPresenter], [DiskStats], a clock), so the
/// ordering and the once-only guarantee are unit-tested rather than asserted.
/// `BackgroundTaskRunner` follows the same split.
///
/// Kotlin's `doWork` in order, all of it reproduced:
///
/// 1. Read the signed-in user. No user, or a blank id, and the run does nothing.
/// 2. Refresh the storage notification row from the available-space percentage,
///    wrapped so a failure cannot stop step 3.
/// 3. Query tasks assigned to the user, not completed, not yet notified, with a
///    deadline between now and this time tomorrow.
/// 4. Show one notification per task, then mark them all notified.
///
/// Every step in the Kotlin is inside `runCatching` and the worker always
/// returns `Result.success()`; nothing here throws for the same reason. A
/// notification is not worth an OS retry.
class TaskDeadlineNotifier {
  TaskDeadlineNotifier({
    required TeamTasksRepository tasks,
    required NotificationsRepository notifications,
    required NotificationPresenter presenter,
    DiskStats? diskStats,
    PlanetPrefs? prefs,
    DateTime Function()? now,
  }) : _tasks = tasks,
       _notifications = notifications,
       _presenter = presenter,
       _diskStats = diskStats,
       _prefs = prefs,
       _now = now ?? DateTime.now;

  final TeamTasksRepository _tasks;
  final NotificationsRepository _notifications;
  final NotificationPresenter _presenter;
  final DiskStats? _diskStats;
  final PlanetPrefs? _prefs;
  final DateTime Function() _now;

  /// `TimeUtils.dateOnlyFormatter` — `"EEE dd, MMMM yyyy"` in the device zone,
  /// the string `formatDate(task.deadline)` produces for the notification body.
  static final DateFormat _deadlineFormat = DateFormat('EEE dd, MMMM yyyy');

  /// Runs one pass. Returns the number of notifications actually shown, which
  /// the Kotlin discards but makes this testable without inspecting the fake.
  Future<int> run({UserRow? user}) async {
    final userId = user?.id;
    if (userId == null || userId.trim().isEmpty) return 0;

    await _refreshStorageNotification(userId);

    final now = _now();
    // `Calendar.getInstance().apply { add(DAY_OF_YEAR, 1) }` — this same instant
    // tomorrow. Note the window *starts* at now, so an already-overdue task is
    // outside it and never notified. That is the Kotlin's behaviour and the
    // dashboard's team task badge uses the same window, so the badge and the
    // notification agree.
    final List<TeamTaskRow> due;
    try {
      due = await _tasks.pendingDeadlineTasks(
        userId: userId,
        start: now.millisecondsSinceEpoch,
        end: now.add(const Duration(days: 1)).millisecondsSinceEpoch,
      );
    } catch (_) {
      // Kotlin: `.getOrElse { emptyList() }`.
      return 0;
    }
    if (due.isEmpty) return 0;

    var shown = 0;
    for (final task in due) {
      final config = NotificationConfig.task(
        taskId: task.id,
        taskTitle: task.title ?? '',
        deadlineLabel: deadlineLabel(task.deadline),
        urgent: isTaskUrgent(deadlineMillis: task.deadline, now: now),
      );
      if (await _presenter.show(config)) shown++;
    }

    // Marked regardless of whether the OS accepted the notification — the
    // Kotlin marks on the same unconditional path. Showing is best-effort;
    // re-notifying every 15 minutes because a channel was muted would be worse
    // than missing one reminder.
    try {
      await _tasks.markNotified(due.map((task) => task.id));
    } catch (_) {
      // Kotlin wraps this in `runCatching` too.
    }
    return shown;
  }

  /// Step 2. `FileUtils.totalAvailableMemoryRatio` rounded to a percentage, fed
  /// to `updateStorageNotification`.
  ///
  /// This is the only caller of that method, and it runs where the live read
  /// usually cannot work: `disk_stats` is registered by `MainActivity`, so a
  /// headless WorkManager engine has no handler for it and `invokeMethod`
  /// throws `MissingPluginException`. Swallowing that — which the Kotlin's
  /// `runCatching` shape invites — made the whole step silently do nothing in
  /// the background, which is the only place it runs.
  ///
  /// So the live read is attempted first and [PlanetPrefs.storageAvailablePercent]
  /// is the fallback, primed from the UI isolate at launch. The same mechanism
  /// `PlatformDeviceIdentitySource` uses, with the same caveat: the figure is as
  /// old as the last app launch, where Kotlin's `FileUtils` reads it live
  /// because it needs no Activity. A warning that is a few hours stale still
  /// tells the user their device is filling up; no warning at all does not.
  Future<void> _refreshStorageNotification(String userId) async {
    final percent = await _availableStoragePercent();
    if (percent == null) return;
    try {
      await _notifications.updateStorageNotification(userId, percent);
    } catch (_) {
      // Kotlin: `runCatching { … }` with the result discarded. A storage write
      // failure must not cost the user their task reminders.
    }
  }

  /// Live figure where the channel exists, cached figure where it does not,
  /// `null` when neither is available.
  Future<int?> _availableStoragePercent() async {
    final diskStats = _diskStats;
    if (diskStats != null) {
      try {
        final stats = await diskStats.storageStats();
        if (stats.totalBytes > 0) {
          final percent = (stats.availableBytes / stats.totalBytes * 100)
              .round();
          // Refresh the cache while a live reading is in hand, so a later
          // headless run has something recent to fall back to.
          await _prefs?.cacheStorageAvailablePercent(percent);
          return percent;
        }
      } catch (_) {
        // Fall through to the cache — see [_refreshStorageNotification].
      }
    }
    return _prefs?.storageAvailablePercent;
  }

  /// `TimeUtils.formatDate(deadline)`. A zero deadline formats as the epoch in
  /// the Kotlin too — `Instant.ofEpochMilli(0)` — so it is not special-cased.
  static String deadlineLabel(int deadlineMillis) => _deadlineFormat.format(
    DateTime.fromMillisecondsSinceEpoch(deadlineMillis),
  );

  /// Port of `NotificationUtils.isTaskUrgent`, quirk included.
  ///
  /// The Kotlin takes the *formatted* deadline string and parses it back with
  /// `TimeUtils.parseDate`, which uses the same `"EEE dd, MMMM yyyy"` pattern
  /// and then calls `atStartOfDay`. So the comparison is against the deadline's
  /// **midnight**, not the deadline itself, and the time of day is discarded.
  /// This reproduces that by truncating rather than by round-tripping a display
  /// string, which is the same arithmetic without the locale dependency.
  ///
  /// Worth knowing: on the path that actually calls this, it is always true.
  /// The caller only selects tasks due between now and this time tomorrow, so
  /// `daysUntilDeadline` can never exceed 1, and the threshold is 2 — every task
  /// the notifier shows gets `PRIORITY_HIGH`. The branch is kept because it is
  /// the Kotlin's, and because a future caller with a wider window would need
  /// it, but no test can distinguish the two priorities through [run].
  static bool isTaskUrgent({
    required int deadlineMillis,
    required DateTime now,
  }) {
    final deadline = DateTime.fromMillisecondsSinceEpoch(deadlineMillis);
    final startOfDeadlineDay = DateTime(
      deadline.year,
      deadline.month,
      deadline.day,
    );
    final difference =
        startOfDeadlineDay.millisecondsSinceEpoch - now.millisecondsSinceEpoch;
    // Integer division, as in the Kotlin's `timeDiff / (1000 * 60 * 60 * 24)`,
    // which truncates toward zero for negatives as well.
    final daysUntilDeadline = difference ~/ Duration.millisecondsPerDay;
    return daysUntilDeadline <= 2;
  }
}
