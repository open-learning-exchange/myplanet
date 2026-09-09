import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/background/background_task_names.dart';
import 'package:myplanet/core/background/heavy_table_scheduler.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/sync/heavy_table_sync.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RecordingScheduler implements HeavyTableWorkScheduler {
  final enqueued = <String>[];

  @override
  Future<void> enqueueUnique(String table) async => enqueued.add(table);
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
      // the walk already running. One shared name would make the five tables
      // compete for a single job and four of them would never run.
      final names = {
        for (final table in HeavyTableSync.tables)
          BackgroundTaskNames.heavyTableSyncTask(table),
      };

      expect(names, hasLength(HeavyTableSync.tables.length));
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
    final call = source.substring(source.indexOf('registerOneOffTask'));

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
