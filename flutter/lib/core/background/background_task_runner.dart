import 'background_task_names.dart';

typedef BackgroundStep = Future<void> Function();

class BackgroundSyncStep {
  const BackgroundSyncStep(this.name, this.run);

  final String name;
  final Future<bool> Function() run;
}

enum BackgroundRunStatus { succeeded, skipped, retryRequested }

class BackgroundRunRecord {
  const BackgroundRunRecord({
    required this.taskName,
    required this.attemptedAt,
    required this.status,
    this.failedSteps = const [],
    this.skipReason,
  });

  final String taskName;
  final DateTime attemptedAt;
  final BackgroundRunStatus status;
  final List<String> failedSteps;
  final String? skipReason;
}

/// Pure-Dart policy engine for a single headless invocation.
///
/// Plugin bootstrap and Riverpod graph construction stay in the entrypoint;
/// this class owns ordering, cadence, failure isolation, and retry semantics so
/// those decisions can be tested without launching a Flutter background
/// isolate.
class BackgroundTaskRunner {
  BackgroundTaskRunner({
    required this.configured,
    required this.autoSyncEnabled,
    required this.autoSyncInterval,
    required this.lastSync,
    required this.recoverOutbox,
    required this.drainOutbox,
    required this.syncSteps,
    required this.recordLastSync,
    this.onSyncComplete,
    this.onMaintenance,
    this.recordRun,
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final bool configured;
  final bool autoSyncEnabled;
  final Duration autoSyncInterval;
  final DateTime? lastSync;
  final BackgroundStep recoverOutbox;
  final BackgroundStep drainOutbox;
  final List<BackgroundSyncStep> syncSteps;
  final Future<void> Function(DateTime value) recordLastSync;

  /// Fires once after every [syncSteps] step succeeds — the hook the
  /// `myplanet_activities` telemetry upload hangs off, mirroring
  /// `AutoSyncWorker`'s `uploadActivities` call after a completed sync. A
  /// failure here is swallowed by the runner (it never contributes to
  /// [failedSteps]); losing telemetry must not request an OS retry the way a
  /// failed table pull would.
  final BackgroundStep? onSyncComplete;

  /// Fires on every [BackgroundTaskNames.maintenance] run — the hook
  /// `TaskDeadlineNotifier` hangs off, standing in for Kotlin's separate
  /// 900-second `TaskNotificationWorker`. Maintenance is this port's job with
  /// the same cadence and the same independence from whether a sync was due, so
  /// the reminder arrives on the schedule the Kotlin gives it.
  ///
  /// Swallowed like [onSyncComplete] and for the same reason: the Kotlin worker
  /// wraps every step in `runCatching` and always returns `Result.success()`.
  final BackgroundStep? onMaintenance;
  final Future<void> Function(BackgroundRunRecord record)? recordRun;
  final DateTime Function() _now;

  /// Returns WorkManager's success flag. `false` asks the OS to retry.
  Future<bool> run(String taskName) async {
    if (taskName != BackgroundTaskNames.autoSync &&
        taskName != BackgroundTaskNames.maintenance) {
      // A renamed task may remain persisted across an upgrade. Retrying a task
      // this version cannot execute would create an infinite retry loop.
      return true;
    }
    if (!configured) {
      await _finish(
        taskName,
        BackgroundRunStatus.skipped,
        skipReason: 'notConfigured',
      );
      return true;
    }

    final failed = <String>[];
    if (!await _attempt(recoverOutbox)) failed.add('outboxRecovery');
    if (!await _attempt(drainOutbox)) failed.add('outboxDrain');

    if (taskName == BackgroundTaskNames.maintenance) {
      // Deadline reminders — see [onMaintenance]. Runs whether or not the
      // outbox steps above succeeded, since it reads only local state.
      final maintenance = onMaintenance;
      if (maintenance != null) {
        try {
          await maintenance();
        } catch (_) {
          // Deliberately ignored — see [onMaintenance].
        }
      }
      return _complete(taskName, failed);
    }
    if (!autoSyncEnabled) {
      await _finish(
        taskName,
        failed.isEmpty
            ? BackgroundRunStatus.skipped
            : BackgroundRunStatus.retryRequested,
        failedSteps: failed,
        skipReason: 'autoSyncDisabled',
      );
      return failed.isEmpty;
    }
    if (!_isDue()) {
      await _finish(
        taskName,
        failed.isEmpty
            ? BackgroundRunStatus.skipped
            : BackgroundRunStatus.retryRequested,
        failedSteps: failed,
        skipReason: 'notDue',
      );
      return failed.isEmpty;
    }

    // Run independently and in order. Future.wait made all ten repositories
    // write Drift concurrently and an early exception discarded the remaining
    // outcomes. A failed table should request a retry without preventing an
    // unrelated table from refreshing.
    for (final sync in syncSteps) {
      try {
        if (!await sync.run()) failed.add(sync.name);
      } catch (_) {
        failed.add(sync.name);
      }
    }

    if (failed.isEmpty) {
      try {
        await recordLastSync(_now().toUtc());
      } catch (_) {
        failed.add('lastSyncWrite');
      }
      // Telemetry upload after a clean sync — see [onSyncComplete]. Not a
      // retryable step; a failure is swallowed so it cannot flip the run.
      final complete = onSyncComplete;
      if (complete != null) {
        try {
          await complete();
        } catch (_) {
          // Deliberately ignored — see [onSyncComplete].
        }
      }
    }
    return _complete(taskName, failed);
  }

  bool _isDue() {
    final previous = lastSync;
    if (previous == null) return true;
    final elapsed = _now().difference(previous);
    // A wall clock corrected backwards must not suppress sync until it catches
    // up with a future timestamp.
    return elapsed.isNegative || elapsed >= autoSyncInterval;
  }

  Future<bool> _attempt(BackgroundStep step) async {
    try {
      await step();
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> _complete(String taskName, List<String> failed) async {
    await _finish(
      taskName,
      failed.isEmpty
          ? BackgroundRunStatus.succeeded
          : BackgroundRunStatus.retryRequested,
      failedSteps: failed,
    );
    return failed.isEmpty;
  }

  Future<void> _finish(
    String taskName,
    BackgroundRunStatus status, {
    List<String> failedSteps = const [],
    String? skipReason,
  }) async {
    final writer = recordRun;
    if (writer == null) return;
    try {
      await writer(
        BackgroundRunRecord(
          taskName: taskName,
          attemptedAt: _now().toUtc(),
          status: status,
          failedSteps: List.unmodifiable(failedSteps),
          skipReason: skipReason,
        ),
      );
    } catch (_) {
      // Telemetry must never turn successful domain work into an OS retry.
    }
  }
}
