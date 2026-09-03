import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/notifications/notification_config.dart';
import 'package:myplanet/core/notifications/notification_presenter.dart';
import 'package:myplanet/core/notifications/task_deadline_notifier.dart';
import 'package:myplanet/core/system/disk_stats.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/notifications_repository.dart';
import 'package:myplanet/repository/team_tasks_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../support/mock_planet_api.dart';

/// Port coverage for `TaskNotificationWorker.doWork`. The whole reason the
/// policy is a plain Dart class is that the worker's contract — notify once,
/// only for this user, only inside the window — is invisible from a widget test
/// and impossible from an isolate.
void main() {
  late AppDatabase db;
  late TeamTasksRepository tasks;
  late NotificationsRepository notifications;
  late _RecordingPresenter presenter;
  late PlanetPrefs prefs;

  /// A fixed "now" so the window arithmetic is not clock-dependent.
  final now = DateTime(2026, 8, 19, 9);

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
    db = AppDatabase.memory();
    tasks = TeamTasksRepository(MockPlanetApi(), db.teamTaskDao);
    notifications = NotificationsRepository(
      db.notificationDao,
      teamNotificationDao: db.teamNotificationDao,
      newsDao: db.newsDao,
      teamTaskDao: db.teamTaskDao,
    );
    presenter = _RecordingPresenter();
  });

  tearDown(() => db.close());

  UserRow user({String id = 'user-1'}) => UserRow(
    id: id,
    name: 'ada',
    rolesList: const [],
    userAdmin: false,
    joinDate: 0,
    isArchived: false,
    isUpdated: false,
  );

  Future<void> insertTask({
    required String id,
    required DateTime deadline,
    String assignee = 'user-1',
    bool completed = false,
    bool isNotified = false,
  }) => db.teamTaskDao.upsert(
    TeamTasksCompanion.insert(
      id: id,
      teamId: 'team-1',
      title: Value('Task $id'),
      assignee: Value(assignee),
      deadline: Value(deadline.millisecondsSinceEpoch),
      completed: Value(completed),
      isNotified: Value(isNotified),
    ),
  );

  TaskDeadlineNotifier notifier({DiskStats? diskStats}) => TaskDeadlineNotifier(
    tasks: tasks,
    notifications: notifications,
    presenter: presenter,
    diskStats: diskStats,
    prefs: prefs,
    now: () => now,
  );

  test('notifies a task due inside the window', () async {
    await insertTask(id: 'task-1', deadline: now.add(const Duration(hours: 5)));

    final shown = await notifier().run(user: user());

    expect(shown, 1);
    expect(presenter.shown.single.id, 'task-1');
    expect(presenter.shown.single.type, NotificationTypes.task);
    // `"$taskTitle\nDue: $deadline"` — the body carries the formatted deadline,
    // which is what makes the reminder actionable on the lock screen.
    expect(presenter.shown.single.message, startsWith('Task task-1\nDue: '));
  });

  test('marks the task notified so a later run stays silent', () async {
    await insertTask(id: 'task-1', deadline: now.add(const Duration(hours: 5)));

    expect(await notifier().run(user: user()), 1);
    // The flag, not the OS, is what makes it once-only — this is the whole
    // reason the worker can run every 15 minutes.
    expect(await notifier().run(user: user()), 0);
    expect(presenter.shown, hasLength(1));

    final row = await db.teamTaskDao.getById('task-1');
    expect(row!.isNotified, isTrue);
    // Never uploaded, so marking must not make the row look edited.
    expect(row.isUpdated, isFalse);
  });

  test('marks notified even when the OS refuses the notification', () async {
    presenter.succeed = false;
    await insertTask(id: 'task-1', deadline: now.add(const Duration(hours: 5)));

    expect(await notifier().run(user: user()), 0);
    // A muted channel must not mean re-notifying every 15 minutes forever.
    expect((await db.teamTaskDao.getById('task-1'))!.isNotified, isTrue);
  });

  test('skips a completed task', () async {
    await insertTask(
      id: 'done',
      deadline: now.add(const Duration(hours: 5)),
      completed: true,
    );

    expect(await notifier().run(user: user()), 0);
    expect(presenter.shown, isEmpty);
  });

  test('skips a task assigned to somebody else', () async {
    await insertTask(
      id: 'theirs',
      deadline: now.add(const Duration(hours: 5)),
      assignee: 'user-2',
    );

    expect(await notifier().run(user: user()), 0);
  });

  test('skips an already-overdue task, as the Kotlin window does', () async {
    // The window starts at *now*, so a deadline in the past falls outside it.
    // Reproduced rather than fixed: the dashboard's task badge uses the same
    // window, and the two disagreeing would be worse than both being narrow.
    await insertTask(
      id: 'late',
      deadline: now.subtract(const Duration(hours: 2)),
    );

    expect(await notifier().run(user: user()), 0);
  });

  test('skips a task due after tomorrow', () async {
    await insertTask(id: 'later', deadline: now.add(const Duration(days: 3)));

    expect(await notifier().run(user: user()), 0);
  });

  test('does nothing without a signed-in user', () async {
    await insertTask(id: 'task-1', deadline: now.add(const Duration(hours: 5)));

    expect(await notifier().run(user: null), 0);
    expect(presenter.shown, isEmpty);
    // Crucially the row is left unnotified, so the reminder still arrives once
    // somebody signs in.
    expect((await db.teamTaskDao.getById('task-1'))!.isNotified, isFalse);
  });

  test('notifies every due task in one pass', () async {
    await insertTask(id: 'a', deadline: now.add(const Duration(hours: 1)));
    await insertTask(id: 'b', deadline: now.add(const Duration(hours: 20)));

    expect(await notifier().run(user: user()), 2);
    expect(presenter.shown.map((c) => c.id), containsAll(['a', 'b']));
  });

  group('the storage notification step', () {
    test('writes a row when space is low', () async {
      await notifier(
        diskStats: _FakeDiskStats(total: 1000, available: 50),
      ).run(user: user());

      // 5% available is under the 10% threshold
      // `NotificationsRepository.storageWarningPercent` uses.
      final row = await db.notificationDao.getById('user-1:storage');
      expect(row, isNotNull);
      expect(row!.message, '5%');
    });

    test('writes no row when there is plenty of space', () async {
      await notifier(
        diskStats: _FakeDiskStats(total: 1000, available: 900),
      ).run(user: user());

      expect(await db.notificationDao.getById('user-1:storage'), isNull);
    });

    test('a failing disk read still lets the reminders through', () async {
      await insertTask(
        id: 'task-1',
        deadline: now.add(const Duration(hours: 5)),
      );

      final shown = await notifier(
        diskStats: _ThrowingDiskStats(),
      ).run(user: user());

      // Kotlin wraps the storage half in its own `runCatching`; the task half
      // is what the worker exists for.
      expect(shown, 1);
    });

    test('the headless case falls back to the primed figure', () async {
      // The real headless case: `disk_stats` is registered by `MainActivity`, so
      // a WorkManager engine gets `MissingPluginException` on every call. This
      // step used to swallow that and write nothing — and since it is the only
      // caller of `updateStorageNotification`, the row was never written at all.
      await prefs.cacheStorageAvailablePercent(4);

      await notifier(diskStats: _ThrowingDiskStats()).run(user: user());

      final row = await db.notificationDao.getById('user-1:storage');
      expect(row, isNotNull);
      expect(row!.message, '4%');
    });

    test('a live reading refreshes the primed figure', () async {
      await prefs.cacheStorageAvailablePercent(90);

      await notifier(
        diskStats: _FakeDiskStats(total: 1000, available: 70),
      ).run(user: user());

      // So the next headless run falls back to something recent rather than to
      // whatever was true at first launch.
      expect(prefs.storageAvailablePercent, 7);
    });

    test('nothing is written when there is no figure at all', () async {
      await notifier(diskStats: _ThrowingDiskStats()).run(user: user());

      // Never measured — guessing a percentage would be worse than staying
      // quiet.
      expect(prefs.storageAvailablePercent, isNull);
      expect(await db.notificationDao.getById('user-1:storage'), isNull);
    });

    test('a zero total is not divided by', () async {
      await notifier(
        diskStats: _FakeDiskStats(total: 0, available: 0),
      ).run(user: user());

      expect(await db.notificationDao.getById('user-1:storage'), isNull);
    });
  });

  group('isTaskUrgent', () {
    test('is true for a deadline within two days', () {
      expect(
        TaskDeadlineNotifier.isTaskUrgent(
          deadlineMillis: now
              .add(const Duration(days: 1))
              .millisecondsSinceEpoch,
          now: now,
        ),
        isTrue,
      );
    });

    test('is false for a deadline further out', () {
      expect(
        TaskDeadlineNotifier.isTaskUrgent(
          deadlineMillis: now
              .add(const Duration(days: 5))
              .millisecondsSinceEpoch,
          now: now,
        ),
        isFalse,
      );
    });

    test('compares against the deadline day midnight, not the deadline', () {
      // This is the quirk, and it widens the urgent band rather than narrowing
      // it. `now` is 09:00 on the 19th; a deadline of 23:00 on the 22nd is
      // 3 days 14 hours away, which would *not* be urgent measured directly.
      // The Kotlin formats the deadline to `"EEE dd, MMMM yyyy"` and parses it
      // back through `atStartOfDay`, so the comparison is against midnight on
      // the 22nd — 2 days 15 hours, truncating to 2, which clears the
      // `<= 2` threshold.
      expect(
        TaskDeadlineNotifier.isTaskUrgent(
          deadlineMillis: DateTime(2026, 8, 22, 23).millisecondsSinceEpoch,
          now: now,
        ),
        isTrue,
      );
      // One more day out and even the truncated distance is 3.
      expect(
        TaskDeadlineNotifier.isTaskUrgent(
          deadlineMillis: DateTime(2026, 8, 23, 23).millisecondsSinceEpoch,
          now: now,
        ),
        isFalse,
      );
    });

    test('every task the notifier selects is urgent', () async {
      // Not a rule worth relying on, but worth pinning: the window is one day
      // wide and the threshold is two, so the `PRIORITY_DEFAULT` branch is
      // unreachable from `run`. If a future caller widens the window, this test
      // failing is the signal that the branch became live.
      await insertTask(id: 'a', deadline: now.add(const Duration(hours: 23)));

      await notifier().run(user: user());

      expect(presenter.shown.single.priority, NotificationPriority.high);
    });
  });

  test('the deadline label matches TimeUtils.dateOnlyFormatter', () {
    expect(
      TaskDeadlineNotifier.deadlineLabel(
        DateTime(2026, 8, 19).millisecondsSinceEpoch,
      ),
      'Wed 19, August 2026',
    );
  });
}

/// Stands in for the platform notification surface, recording what it was asked
/// to show.
class _RecordingPresenter implements NotificationPresenter {
  final List<NotificationConfig> shown = [];
  bool succeed = true;

  @override
  Future<bool> show(NotificationConfig config) async {
    shown.add(config);
    return succeed;
  }
}

class _FakeDiskStats implements DiskStats {
  _FakeDiskStats({required this.total, required this.available});

  final int total;
  final int available;

  @override
  Future<({int totalBytes, int availableBytes})> storageStats() async =>
      (totalBytes: total, availableBytes: available);
}

class _ThrowingDiskStats implements DiskStats {
  @override
  Future<({int totalBytes, int availableBytes})> storageStats() async =>
      throw StateError('no channel');
}
