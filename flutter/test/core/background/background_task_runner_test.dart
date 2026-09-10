import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/background/background_task_names.dart';
import 'package:myplanet/core/background/background_task_runner.dart';

void main() {
  final now = DateTime.utc(2026, 8, 16, 12);

  late List<String> calls;
  late List<DateTime> recorded;
  late List<BackgroundRunRecord> runs;

  setUp(() {
    calls = [];
    recorded = [];
    runs = [];
  });

  BackgroundTaskRunner runner({
    bool configured = true,
    bool enabled = true,
    Duration interval = const Duration(hours: 1),
    DateTime? lastSync,
    Future<void> Function()? recover,
    Future<void> Function()? drain,
    List<BackgroundSyncStep>? syncs,
    Future<void> Function()? onSyncComplete,
    Future<void> Function()? onMaintenance,
  }) => BackgroundTaskRunner(
    configured: configured,
    autoSyncEnabled: enabled,
    autoSyncInterval: interval,
    lastSync: lastSync,
    recoverOutbox:
        recover ??
        () async {
          calls.add('recover');
        },
    drainOutbox:
        drain ??
        () async {
          calls.add('drain');
        },
    syncSteps:
        syncs ??
        [
          BackgroundSyncStep('default', () async {
            calls.add('sync');
            return true;
          }),
        ],
    recordLastSync: (value) async => recorded.add(value),
    onSyncComplete: onSyncComplete,
    onMaintenance: onMaintenance,
    recordRun: (record) async => runs.add(record),
    now: () => now,
  );

  test(
    'unknown persisted work succeeds without touching dependencies',
    () async {
      expect(await runner().run('old.task.name'), isTrue);
      expect(calls, isEmpty);
      expect(recorded, isEmpty);
      expect(runs, isEmpty, reason: 'unknown work is not this version’s run');
    },
  );

  test('an unconfigured app has nothing to deliver or synchronize', () async {
    expect(
      await runner(configured: false).run(BackgroundTaskNames.autoSync),
      isTrue,
    );
    expect(calls, isEmpty);
    expect(runs.single.status, BackgroundRunStatus.skipped);
    expect(runs.single.skipReason, 'notConfigured');
  });

  test('maintenance only recovers and drains the outbox', () async {
    expect(await runner().run(BackgroundTaskNames.maintenance), isTrue);
    expect(calls, ['recover', 'drain']);
    expect(recorded, isEmpty);
    expect(runs.single.status, BackgroundRunStatus.succeeded);
  });

  test('disabled auto sync still delivers durable writes', () async {
    expect(
      await runner(enabled: false).run(BackgroundTaskNames.autoSync),
      isTrue,
    );
    expect(calls, ['recover', 'drain']);
    expect(runs.single.skipReason, 'autoSyncDisabled');
  });

  test('a recent sync suppresses pulls but not outbox delivery', () async {
    expect(
      await runner(
        lastSync: now.subtract(const Duration(minutes: 59)),
      ).run(BackgroundTaskNames.autoSync),
      isTrue,
    );
    expect(calls, ['recover', 'drain']);
    expect(recorded, isEmpty);
    expect(runs.single.skipReason, 'notDue');
  });

  test('the exact interval boundary is due', () async {
    expect(
      await runner(
        lastSync: now.subtract(const Duration(hours: 1)),
      ).run(BackgroundTaskNames.autoSync),
      isTrue,
    );
    expect(calls, ['recover', 'drain', 'sync']);
    expect(recorded, [now]);
    expect(runs.single.status, BackgroundRunStatus.succeeded);
  });

  test('a future wall-clock timestamp does not suppress sync', () async {
    expect(
      await runner(
        lastSync: now.add(const Duration(days: 1)),
      ).run(BackgroundTaskNames.autoSync),
      isTrue,
    );
    expect(calls, ['recover', 'drain', 'sync']);
  });

  test('sync steps run sequentially in declaration order', () async {
    var firstFinished = false;
    final subject = runner(
      syncs: [
        BackgroundSyncStep('first', () async {
          calls.add('first:start');
          await Future<void>.delayed(Duration.zero);
          firstFinished = true;
          calls.add('first:end');
          return true;
        }),
        BackgroundSyncStep('second', () async {
          expect(firstFinished, isTrue);
          calls.add('second');
          return true;
        }),
      ],
    );

    expect(await subject.run(BackgroundTaskNames.autoSync), isTrue);
    expect(calls, ['recover', 'drain', 'first:start', 'first:end', 'second']);
  });

  test(
    'a failed table does not prevent later tables from refreshing',
    () async {
      final subject = runner(
        syncs: [
          BackgroundSyncStep('failedTable', () async {
            calls.add('failed');
            return false;
          }),
          BackgroundSyncStep('laterTable', () async {
            calls.add('later');
            return true;
          }),
        ],
      );

      expect(await subject.run(BackgroundTaskNames.autoSync), isFalse);
      expect(calls, ['recover', 'drain', 'failed', 'later']);
      expect(recorded, isEmpty);
      expect(runs.single.status, BackgroundRunStatus.retryRequested);
      expect(runs.single.failedSteps, ['failedTable']);
    },
  );

  test('a throwing table is isolated and requests an OS retry', () async {
    final subject = runner(
      syncs: [
        BackgroundSyncStep(
          'throwingTable',
          () async => throw StateError('broken mapper'),
        ),
        BackgroundSyncStep('laterTable', () async {
          calls.add('later');
          return true;
        }),
      ],
    );

    expect(await subject.run(BackgroundTaskNames.autoSync), isFalse);
    expect(calls, ['recover', 'drain', 'later']);
    expect(recorded, isEmpty);
    expect(runs.single.failedSteps, ['throwingTable']);
  });

  test('outbox failure requests retry but still refreshes tables', () async {
    final subject = runner(
      drain: () async => throw StateError('database busy'),
    );

    expect(await subject.run(BackgroundTaskNames.autoSync), isFalse);
    expect(calls, ['recover', 'sync']);
    expect(recorded, isEmpty);
    expect(runs.single.failedSteps, ['outboxDrain']);
  });

  test('recovery failure does not prevent a drain or table pulls', () async {
    final subject = runner(
      recover: () async => throw StateError('recovery failed'),
    );

    expect(await subject.run(BackgroundTaskNames.autoSync), isFalse);
    expect(calls, ['drain', 'sync']);
    expect(runs.single.failedSteps, ['outboxRecovery']);
  });

  test(
    'diagnostic persistence failure does not retry successful work',
    () async {
      final subject = BackgroundTaskRunner(
        configured: true,
        autoSyncEnabled: true,
        autoSyncInterval: const Duration(hours: 1),
        lastSync: null,
        recoverOutbox: () async {},
        drainOutbox: () async {},
        syncSteps: [BackgroundSyncStep('resources', () async => true)],
        recordLastSync: (_) async {},
        recordRun: (_) async => throw StateError('preferences unavailable'),
        now: () => now,
      );

      expect(await subject.run(BackgroundTaskNames.autoSync), isTrue);
    },
  );

  test('last-sync persistence failure is diagnosed and retried', () async {
    final subject = BackgroundTaskRunner(
      configured: true,
      autoSyncEnabled: true,
      autoSyncInterval: const Duration(hours: 1),
      lastSync: null,
      recoverOutbox: () async {},
      drainOutbox: () async {},
      syncSteps: [BackgroundSyncStep('resources', () async => true)],
      recordLastSync: (_) async => throw StateError('disk full'),
      recordRun: (record) async => runs.add(record),
      now: () => now,
    );

    expect(await subject.run(BackgroundTaskNames.autoSync), isFalse);
    expect(runs.single.failedSteps, ['lastSyncWrite']);
    expect(runs.single.status, BackgroundRunStatus.retryRequested);
  });

  test('onMaintenance fires on the maintenance run', () async {
    // Stands in for Kotlin's separate 900-second `TaskNotificationWorker`, so it
    // has to run on the maintenance cadence rather than the sync one.
    await runner(
      onMaintenance: () async {
        calls.add('deadlines');
      },
    ).run(BackgroundTaskNames.maintenance);

    expect(calls, ['recover', 'drain', 'deadlines']);
    expect(runs.single.status, BackgroundRunStatus.succeeded);
  });

  test('onMaintenance fires even when the outbox steps fail', () async {
    // Deadline reminders read only local state, so a dead network must not cost
    // the user their reminders.
    await runner(
      recover: () async => throw Exception('offline'),
      drain: () async => throw Exception('offline'),
      onMaintenance: () async {
        calls.add('deadlines');
      },
    ).run(BackgroundTaskNames.maintenance);

    expect(calls, ['deadlines']);
    expect(runs.single.status, BackgroundRunStatus.retryRequested);
    expect(runs.single.failedSteps, ['outboxRecovery', 'outboxDrain']);
  });

  test('a throwing onMaintenance does not flip the run', () async {
    await runner(
      onMaintenance: () async {
        calls.add('deadlines');
        throw Exception('no notification channel');
      },
    ).run(BackgroundTaskNames.maintenance);

    expect(calls, ['recover', 'drain', 'deadlines']);
    expect(runs.single.status, BackgroundRunStatus.succeeded);
    expect(runs.single.failedSteps, isEmpty);
  });

  test('onMaintenance does not fire on the sync run', () async {
    // Otherwise the reminder pass would run twice per quarter hour.
    await runner(
      onMaintenance: () async {
        calls.add('deadlines');
      },
    ).run(BackgroundTaskNames.autoSync);

    expect(calls, ['recover', 'drain', 'sync']);
  });

  test('onSyncComplete fires after a clean sync', () async {
    await runner(
      onSyncComplete: () async {
        calls.add('telemetry');
      },
    ).run(BackgroundTaskNames.autoSync);

    expect(calls, ['recover', 'drain', 'sync', 'telemetry']);
    expect(runs.single.status, BackgroundRunStatus.succeeded);
  });

  test(
    'a throwing onSyncComplete is swallowed and the run still succeeds',
    () async {
      await runner(
        onSyncComplete: () async {
          calls.add('telemetry');
          throw Exception('network');
        },
      ).run(BackgroundTaskNames.autoSync);

      expect(calls, ['recover', 'drain', 'sync', 'telemetry']);
      expect(runs.single.status, BackgroundRunStatus.succeeded);
      expect(runs.single.failedSteps, isEmpty);
    },
  );

  test('onSyncComplete does not fire when a sync step fails', () async {
    await runner(
      syncs: [BackgroundSyncStep('resources', () async => false)],
      onSyncComplete: () async {
        calls.add('telemetry');
      },
    ).run(BackgroundTaskNames.autoSync);

    expect(calls, ['recover', 'drain']);
    expect(runs.single.status, BackgroundRunStatus.retryRequested);
  });
}
