import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/background/background_task_names.dart';
import 'package:myplanet/core/background/heavy_table_scheduler.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/sync/heavy_table_sync.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RecordingScheduler implements HeavyTableWorkScheduler {
  final taskNames = <String>[];

  /// The tables the recorded task names parse back to — the round trip the
  /// dispatcher performs. A `null` here is a name `executeBackgroundTask`
  /// would fail to route, which is why the assertions read this rather than
  /// the raw strings.
  List<String?> get enqueued =>
      taskNames.map(BackgroundTaskNames.heavyTableSyncTable).toList();

  @override
  Future<void> enqueueUnique(String taskName) async => taskNames.add(taskName);
}

void main() {
  late _RecordingScheduler scheduler;
  late PlanetPrefs prefs;

  setUp(() async {
    SharedPreferences.setMockInitialValues(const {});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
    scheduler = _RecordingScheduler();
  });

  HeavyTableSyncScheduler build() => HeavyTableSyncScheduler(scheduler, prefs);

  group('task names', () {
    test('round-trip a table', () {
      final name = BackgroundTaskNames.heavyTableSyncTask('courses_progress');

      expect(name, 'myplanet.heavy.courses_progress');
      expect(BackgroundTaskNames.heavyTableSyncTable(name), 'courses_progress');
    });

    test('are one per table, so each gets its own unique work', () {
      // The unique name is what makes `keep` collapse a second enqueue into
      // the walk already running; one shared name would make the tables
      // compete for a single job and all but one would never run. The
      // uniqueness itself is injective by construction — the interesting half
      // is that a *scheduled* name round-trips through the dispatcher's
      // parser, which is what the source assertion below adds.
      for (final table in HeavyTableSync.tables) {
        final name = BackgroundTaskNames.heavyTableSyncTask(table);
        expect(BackgroundTaskNames.heavyTableSyncTable(name), table);
      }
    });

    test('reject a name that is not a heavy task', () {
      expect(
        BackgroundTaskNames.heavyTableSyncTable(BackgroundTaskNames.autoSync),
        isNull,
      );
      expect(
        BackgroundTaskNames.heavyTableSyncTable(BackgroundTaskNames.download),
        isNull,
      );
      // The bare prefix carries no table. Returning `''` here would have the
      // entrypoint run a walk for a table with no writer, which is harmless
      // but reports a verdict for a table that does not exist.
      expect(
        BackgroundTaskNames.heavyTableSyncTable(
          BackgroundTaskNames.heavyPrefix,
        ),
        isNull,
      );
    });
  });

  group('scheduleAll', () {
    test('enqueues every port heavy table, checkpoints unread', () async {
      await build().scheduleAll();

      expect(scheduler.enqueued, HeavyTableSync.tables);
      // The round trip, which is the half that was pinned by nothing: an audit
      // made the scheduler pass the bare table as the task name and the whole
      // suite stayed green, while in production the dispatcher parsed no table
      // and the walk never ran.
      expect(
        scheduler.taskNames,
        HeavyTableSync.tables
            .map(BackgroundTaskNames.heavyTableSyncTask)
            .toList(),
      );
    });

    test('is what recovers a table whose walk died on page 1', () async {
      // Checkpoint 0 with an unfinished walk is invisible to
      // `scheduleIfPending`; this is the only path that re-enqueues it. Gate
      // `scheduleAll` on the checkpoint and this fails.
      await prefs.setHeavyTableSkip('courses_progress', 0);

      await build().scheduleAll();

      expect(scheduler.enqueued, contains('courses_progress'));
    });
  });

  group('scheduleIfPending', () {
    test('enqueues only the tables with a checkpoint above zero', () async {
      await prefs.setHeavyTableSkip('courses_progress', 23600);
      await prefs.setHeavyTableSkip('login_activities', 0);

      await build().scheduleIfPending();

      expect(scheduler.enqueued, ['courses_progress']);
    });

    test('enqueues nothing when no walk is pending', () async {
      await build().scheduleIfPending();

      expect(scheduler.enqueued, isEmpty);
    });

    test('treats a completed walk as not pending', () async {
      await prefs.setHeavyTableSkip('login_activities', 10400);
      await prefs.clearHeavyTableSkip('login_activities');

      await build().scheduleIfPending();

      expect(scheduler.enqueued, isEmpty);
    });
  });

  /// The `workmanager` call itself cannot be driven from a unit test — there
  /// is no plugin channel here — so the two settings whose loss is silent and
  /// expensive are pinned against the source.
  group('the WorkManager request', () {
    final source = File(
      'lib/core/background/heavy_table_scheduler.dart',
    ).readAsStringSync();
    // The concrete implementation's body: from `enqueueUnique` *inside*
    // `WorkmanagerHeavyTableScheduler`, so the name construction a line above
    // `registerOneOffTask` is in scope while the doc comments — which discuss
    // `append` and `expedited` by name — are not. Anchoring on the first
    // `enqueueUnique` in the file finds the abstract member and swallows the
    // comment that explains why the flag is absent, which then reads as the
    // flag being present.
    final call = source.substring(
      source.indexOf(
        'Future<void> enqueueUnique',
        source.indexOf('class WorkmanagerHeavyTableScheduler'),
      ),
    );

    test('keeps existing work rather than appending to it', () {
      // `append` (the policy the download queue needs, and the only one
      // `BackgroundScheduler.scheduleOneOff` offers) would queue a *further*
      // complete walk behind the one in flight for every enqueue — and both
      // entry points enqueue freely. On `courses_progress` that is another
      // 114,219 documents per sync.
      expect(call, contains('ExistingWorkPolicy.keep'));
      expect(call, isNot(contains('ExistingWorkPolicy.append')));
    });

    test('retries on the Kotlin linear 30-second backoff', () {
      expect(call, contains('BackoffPolicy.linear'));
      expect(call, contains('Duration(seconds: 30)'));
    });

    test('requires only a network, as the Kotlin constraints do', () {
      expect(call, contains('NetworkType.connected'));
      // `HeavyTableSyncWorker` sets no battery constraint, unlike the port's
      // other jobs: a walk resuming across sessions must not also wait for a
      // charged battery.
      expect(call, isNot(contains('requiresBatteryNotLow')));
      // `setExpedited` is deliberately not ported — see the doc comment: on
      // API 26-30 it makes WorkManager call a `getForegroundInfo()` that
      // `CoroutineWorker` implements by throwing, and no override exists.
      expect(call, isNot(contains('expedited')));
    });
  });
}
