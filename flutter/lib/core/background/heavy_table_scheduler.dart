import 'package:workmanager/workmanager.dart';

import '../prefs/planet_prefs.dart';
import '../sync/heavy_table_sync.dart';
import 'background_task_names.dart';

/// Enqueues one heavy table's background walk.
///
/// **Why this is not a method on `BackgroundScheduler`.** That seam's
/// `scheduleOneOff` is hard-wired to `ExistingWorkPolicy.append`, and the
/// comment there explains why it must stay that way: the download queue
/// enqueues after inserting a row, and `keep` would drop the registration that
/// tells a worker about an id added after it took its snapshot, stranding the
/// download.
///
/// Heavy tables need the opposite policy — `keep`, which is what
/// `HeavyTableSyncWorker.schedule` uses
/// (`enqueueUniqueWork("heavy_sync_$table", ExistingWorkPolicy.KEEP, …)`) —
/// and for a sharper reason than tidiness. Both entry points fire freely:
/// every completed sync schedules all tables, and every background invocation
/// resumes the pending ones. Under `append` each of those enqueues would
/// become a *further complete walk* of the table, queued behind the one
/// already running, and a `courses_progress` walk is 114,219 documents.
/// `keep` collapses the pile into the one walk that is already in flight,
/// which is also what makes a retry-pending walk immune to being restarted
/// from zero by an unrelated sync.
abstract interface class HeavyTableWorkScheduler {
  Future<void> enqueueUnique(String table);
}

class WorkmanagerHeavyTableScheduler implements HeavyTableWorkScheduler {
  const WorkmanagerHeavyTableScheduler();

  /// `HeavyTableSyncWorker.schedule`'s request, setting for setting:
  /// `NetworkType.CONNECTED` and nothing else (`:45-47`),
  /// `BackoffPolicy.LINEAR` at 30 seconds (`:52`), and the table in the name.
  ///
  /// Two deliberate departures.
  ///
  /// **No `requiresBatteryNotLow`**, unlike the port's other jobs, because the
  /// Kotlin constraint set is network-only. A walk that has to resume across
  /// many sessions should not also wait for a charged battery.
  ///
  /// **Not expedited.** The Kotlin asks for
  /// `setExpedited(RUN_AS_NON_EXPEDITED_WORK_REQUEST)`, and that call is a
  /// latent bug on this app's whole supported floor: below API 31 WorkManager
  /// serves an expedited request as a foreground service and calls
  /// `getForegroundInfo()`, which `CoroutineWorker` implements by throwing —
  /// and `HeavyTableSyncWorker` overrides neither it nor
  /// `setForeground`. With `minSdk` 26 that throws before `doWork` runs
  /// whenever expedited quota is available. Copying the flag would reproduce
  /// a bug rather than a behaviour, and non-expedited is the fallback the flag
  /// itself names.
  @override
  Future<void> enqueueUnique(String table) {
    final name = BackgroundTaskNames.heavyTableSyncTask(table);
    return Workmanager().registerOneOffTask(
      name,
      name,
      existingWorkPolicy: ExistingWorkPolicy.keep,
      backoffPolicy: BackoffPolicy.linear,
      backoffPolicyDelay: const Duration(seconds: 30),
      constraints: Constraints(networkType: NetworkType.connected),
    );
  }
}

/// Port of `HeavyTableSyncWorker.schedule` / `scheduleIfPending`.
class HeavyTableSyncScheduler {
  const HeavyTableSyncScheduler(this._scheduler, this._prefs);

  final HeavyTableWorkScheduler _scheduler;
  final PlanetPrefs _prefs;

  /// Port of `schedule(context, tables = ALL_HEAVY_TABLES)`, called by
  /// `SyncManager:209` at the end of a completed full sync — the port's
  /// analogue being the end of `DashboardSyncNotifier.syncAll` and of a clean
  /// headless run.
  ///
  /// Unconditional: it re-enqueues every table whatever their checkpoints say,
  /// which is the only thing that recovers a table whose walk died on its
  /// first page (see [HeavyTableSync.run] for why that state is invisible to
  /// [scheduleIfPending]).
  Future<void> scheduleAll({
    List<String> tables = HeavyTableSync.tables,
  }) async {
    for (final table in tables) {
      await _scheduler.enqueueUnique(table);
    }
  }

  /// Port of `scheduleIfPending`, whose one Kotlin call site is
  /// `ServerReachabilityWorker:201` — a network-reconnection-triggered run,
  /// deliberately *not* gated on whether a sync is in flight, because `keep`
  /// and the worker's own guard already make an overlapping call harmless.
  ///
  /// The predicate is the Kotlin's: a table is pending when its checkpoint is
  /// `> 0`, and nothing is enqueued when none is. It shares the worker's blind
  /// spot exactly — a checkpoint of `0` means "never walked", "finished" and
  /// "died on page 1" alike — which is a quirk, not an oversight, and the two
  /// predicates agreeing is what keeps the behaviour consistent.
  Future<void> scheduleIfPending({
    List<String> tables = HeavyTableSync.tables,
  }) async {
    final pending = [
      for (final table in tables)
        if (_prefs.heavyTableSkip(table) > 0) table,
    ];
    if (pending.isEmpty) return;
    await scheduleAll(tables: pending);
  }
}
