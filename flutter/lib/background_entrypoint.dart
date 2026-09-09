import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:workmanager/workmanager.dart';

import 'core/background/background_task_runner.dart';
import 'core/background/background_task_names.dart';
import 'core/background/background_download_queue.dart';
import 'core/config/server_config.dart';
import 'core/network/network_result.dart';
import 'core/prefs/planet_prefs.dart';
import 'core/sync/sync_result.dart';
import 'providers/app_providers.dart';
import 'providers/heavy_sync_providers.dart';
import 'repository/personals_uploader.dart';
import 'repository/resources_uploader.dart';

/// WorkManager launches this in a new isolate, so it must be a retained
/// top-level entry point rather than a closure installed by the UI isolate.
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((taskName, _) => executeBackgroundTask(taskName));
}

/// Executes one WorkManager invocation in a fresh Riverpod graph.
///
/// The graph is always disposed, which closes Drift and Dio resources even if
/// Android stops the worker after its execution window.
Future<bool> executeBackgroundTask(String taskName) async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await PlanetPrefs.load();
  final container = ProviderContainer(
    overrides: [planetPrefsProvider.overrideWithValue(prefs)],
  );
  try {
    // Port of `HeavyTableSyncWorker.doWork`, ahead of everything else because
    // a heavy task is not a sync run: it walks exactly one table from its
    // checkpoint and reports the retry verdict that checkpoint implies.
    //
    // The reload is the port's tax for the isolate boundary. `PlanetPrefs`
    // caches every value at `getInstance()`, and this is the one path that
    // reads state the *UI* isolate authored — the interactive-sync flag
    // standing in for `SyncManager.isSyncing`, plus any checkpoint a previous
    // engine wrote. Kotlin's worker shares the process and the
    // `SharedPreferences` object with the UI and needs no equivalent.
    final heavyTable = BackgroundTaskNames.heavyTableSyncTable(taskName);
    if (heavyTable != null) {
      await prefs.reload();
      return await container
          .read(heavyTableSyncProvider)
          .run(heavyTable, config: prefs.serverConfig);
    }

    final config = prefs.serverConfig;
    if (taskName == BackgroundTaskNames.download) {
      if (config == null) return true;
      var succeeded = true;
      final downloader = container.read(resourceDownloaderProvider);
      final queue = BackgroundDownloadQueue(
        container.read(downloadQueueDaoProvider),
        container.read(backgroundSchedulerProvider),
      );
      for (final id in await queue.pending()) {
        final resource = await container.read(myLibraryDaoProvider).getById(id);
        if (resource == null) {
          await queue.complete(id);
          continue;
        }
        final result = await downloader.download(
          resource,
          config: config,
          persistInBackground: false,
        );
        if (result is! NetworkSuccess<String>) succeeded = false;
      }
      return succeeded;
    }
    final drainer = container.read(outboxDrainerProvider);
    bool completed(SyncResult result) => result is SyncComplete;

    // The headless half of the interactive-sync flag `HeavyTableSync` reads to
    // stand down. Kotlin needs no second site because both of
    // `syncManager.start`'s callers set the one `AtomicBoolean` —
    // `SyncActivity:415` and `AutoSyncWorker:106` — so `isMainSyncActive()` is
    // true during a headless sync as well as an interactive one. The port's
    // first cut wrote the flag only in `DashboardSyncNotifier.syncAll`, which
    // left the *usual* case unguarded: a heavy task started while this
    // invocation was walking `resources` or `courses`, in a second engine with
    // its own `AppDatabase` on the same file.
    //
    // Set before the runner and cleared in a `finally`, as
    // `SyncManager.destroy` is (`SyncManager:233`). Both writes are swallowed:
    // a preference failure must not fail the run, and the flag decays anyway.
    //
    // Set for **every** invocation rather than only for an `autoSync` that is
    // actually due, which is one step broader than Kotlin, where only
    // `AutoSyncWorker` sets the flag. The reason is the port's hazard rather
    // than the Kotlin's: `drainOutbox` writes the database on a `maintenance`
    // run too, and the thing being avoided is two Flutter engines writing one
    // SQLite file — a question Kotlin's single process never has to ask. The
    // cost is that a heavy task firing during a short maintenance run defers
    // by one 30-second backoff.
    await _markSyncActive(prefs);
    try {
      return await BackgroundTaskRunner(
        configured: config != null,
        autoSyncEnabled: prefs.autoSyncEnabled,
        autoSyncInterval: prefs.autoSyncInterval,
        // `PlanetPrefs.lastSync` is epoch millis with 0 meaning never — the
        // representation `SharedPrefManager.LAST_SYNC` uses and the dashboard's
        // last-sync strip reads. The runner speaks `DateTime?`, so the two are
        // bridged here rather than changing either side.
        lastSync: prefs.lastSync == 0
            ? null
            : DateTime.fromMillisecondsSinceEpoch(prefs.lastSync, isUtc: true),
        recoverOutbox: drainer.recoverStuck,
        // The submissions safety net rides here rather than in `syncSteps`, and
        // the placement is the whole point — see [sweepPendingSubmissions]. The
        // drain that follows is unscoped, so the swept rows go out in this same
        // invocation without the sweep needing a drain of its own.
        drainOutbox: () async {
          if (config == null) return;
          // Ahead of the submissions sweep, as both Kotlin workers have it
          // (`AutoSyncWorker:130` before `:136`, `UserDataWorker:40` before
          // `:48`). See [sweepPendingVoices].
          await sweepPendingVoices(
            container,
            config: config,
            userId: prefs.loggedInUserId,
          );
          await sweepPendingSubmissions(
            container,
            config: config,
            userId: prefs.loggedInUserId,
          );
          // The third sweep, wired at integration because the lane that wrote
          // it did not own this file. Ordering against the two above is free:
          // no sync step writes a `my_library` row over a locally authored one
          // (`MyLibraryMapper.fromDoc` keys on the CouchDB `_id`, which a
          // pending resource has none of). See [sweepPendingResources] for why
          // this belongs here rather than in `syncSteps` -- those run only for
          // a due `autoSync` task with auto-sync enabled, which is precisely
          // not the user most likely to be holding an undelivered write.
          await sweepPendingResources(
            container,
            config: config,
            userId: prefs.loggedInUserId,
          );
          await drainer.drain(
            authHeader: PersonalsUploader.authHeaderFor(config),
          );
        },
        syncSteps: config == null
            ? const []
            : [
                // First, as `SyncManager.startFullSync` has it: the shelf is
                // *pushed* before the pull phase, so a course or resource added
                // or removed since the last sync reaches the server before the
                // walks that would otherwise read the server's older copy back
                // over it. Kotlin reaches this from both of `syncManager.start`'s
                // callers — `SyncActivity` and `AutoSyncWorker` — so a port that
                // has it only in the sync centre leaves the offline-then-closed
                // case, which is the one the step exists for, unfixed.
                //
                // A failure is swallowed by returning true rather than
                // requesting a retry: Kotlin logs and continues, and a shelf that
                // cannot reach the server must not stop the pulls or re-run the
                // whole task.
                BackgroundSyncStep('shelf_push', () async {
                  final userId = prefs.loggedInUserId;
                  if (userId == null) return true;
                  try {
                    final user = await container
                        .read(userDaoProvider)
                        .getById(userId);
                    final shelfDocId = user?.couchId;
                    if (shelfDocId == null || shelfDocId.isEmpty) return true;
                    await container
                        .read(shelfRepositoryProvider)
                        .upload(
                          config: config,
                          userId: userId,
                          shelfDocId: shelfDocId,
                        );
                  } catch (_) {
                    // Deliberately ignored — see above.
                  }
                  return true;
                }),
                BackgroundSyncStep(
                  'resources',
                  () async => completed(
                    await container
                        .read(resourcesRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                BackgroundSyncStep(
                  'courses',
                  () async => completed(
                    await container
                        .read(coursesRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                BackgroundSyncStep(
                  'teams',
                  () async => completed(
                    await container
                        .read(teamsRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                BackgroundSyncStep(
                  'events',
                  () async => completed(
                    await container
                        .read(eventsRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                BackgroundSyncStep(
                  'surveys',
                  () async => completed(
                    await container
                        .read(surveysRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                BackgroundSyncStep(
                  'voices',
                  () async => completed(
                    await container
                        .read(voicesRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                BackgroundSyncStep(
                  'feedback',
                  () async => completed(
                    await container
                        .read(feedbackRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                BackgroundSyncStep(
                  'chat',
                  () async => completed(
                    await container
                        .read(chatRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                // **`submissions` stays inline, and that is a deliberate
                // divergence from Kotlin**, which walks it in
                // `HeavyTableSyncWorker` like the other four heavy tables. The
                // reason is the ordering immediately above: this step must run
                // *after* `sweepPendingSubmissions`, because `upsertDocuments`
                // writes `isUpdated: false` over every row it writes and keys
                // each on the server `_id`, so a pull that lands before the
                // sweep strips the dirty flag from a server-originated sheet the
                // user edited locally and it never uploads. A background walk
                // cannot be ordered against this invocation at all. The table is
                // 1,975 documents in 20 pages and was never one of the two that
                // aborted at depth, so it gains little from the checkpoint —
                // see [HeavyTableSync.tables], and
                // `test/providers/pending_submissions_sweep_test.dart`, which is
                // what caught the first cut moving it.
                BackgroundSyncStep(
                  'submissions',
                  () async => completed(
                    await container
                        .read(submissionsRepositoryProvider)
                        .sync(config: config),
                  ),
                ),
                BackgroundSyncStep(
                  'health',
                  () async => completed(
                    await container.read(healthRepositoryProvider).sync(),
                  ),
                ),
              ],
        recordLastSync: (value) =>
            prefs.setLastSync(value.millisecondsSinceEpoch),
        // Port of `AutoSyncWorker`'s `uploadActivities` call after a clean sync:
        // posts the `myplanet_activities` telemetry doc. Skipped when no user is
        // signed in (the Kotlin's `uploadActivities` does the same) or when the
        // upload throws — losing telemetry must not flip the run to retry.
        onSyncComplete: config == null
            ? null
            : () async {
                // Port of `SyncManager:209`'s `HeavyTableSyncWorker.schedule`,
                // and it belongs on this hook rather than in the step list for
                // the same reason: the Kotlin call sits at the end of
                // `startFullSync`'s `try`, so a sync that threw never reaches
                // it. `onSyncComplete` fires only when every step succeeded,
                // which is the closest this runner has to "the sync finished".
                //
                // Unconditional over the tables, checkpoints unread: that is
                // what recovers a table whose walk died on its first page,
                // which `scheduleIfPending` structurally cannot see.
                //
                // Ahead of the telemetry upload, so a throwing upload cannot
                // cost the heavy tables their scheduling — this whole closure is
                // swallowed by the runner as one unit.
                try {
                  await container
                      .read(heavyTableSyncSchedulerProvider)
                      .scheduleAll();
                } catch (_) {
                  // Deliberately ignored, as above.
                }
                final userId = prefs.loggedInUserId;
                if (userId == null) return;
                final user = await container
                    .read(userDaoProvider)
                    .getById(userId);
                if (user == null) return;
                await container
                    .read(myPlanetActivitiesUploaderProvider)
                    .upload(user: user, config: config);
              },
        // Port of `TaskNotificationWorker`, which the Kotlin schedules as its own
        // 900-second periodic worker. It reads only local state, so unlike the
        // sync steps it needs no server config — but it does need a signed-in
        // user, which cannot exist without one anyway.
        onMaintenance: () async {
          final userId = prefs.loggedInUserId;
          if (userId == null) return;
          final user = await container.read(userDaoProvider).getById(userId);
          if (user == null) return;
          await container.read(taskDeadlineNotifierProvider).run(user: user);
        },
        recordRun: (record) => prefs.recordBackgroundRun(
          taskName: record.taskName,
          attemptedAt: record.attemptedAt,
          status: record.status.name,
          failedSteps: record.failedSteps,
          skipReason: record.skipReason,
        ),
      ).run(taskName);
    } finally {
      await _clearSyncActive(prefs);
      // Port of `ServerReachabilityWorker:201`'s `scheduleIfPending`, which
      // resumes a walk that stopped mid-table.
      //
      // **After the run, not before it.** Kotlin's call lives in a *different*
      // worker from its sync, so it can never start a heavy walk alongside the
      // sync in its own process; and where it does overlap one,
      // `isMainSyncActive()` sees it. The port's first cut called this at the
      // top of the invocation, before the runner's own table walks, where
      // `keep` has nothing to keep and the network constraint is satisfied by
      // definition — so WorkManager was free to start walking
      // `courses_progress` in a second engine while this one wrote `resources`.
      // Two writers on one SQLite file with no WAL and no busy timeout is
      // `database is locked` for whichever loses.
      //
      // In the `finally` rather than after it, so a resumable walk is still
      // picked up on a run that threw; and after the flag is cleared, so the
      // task it enqueues does not immediately stand down.
      if (config != null) {
        try {
          await container
              .read(heavyTableSyncSchedulerProvider)
              .scheduleIfPending();
        } catch (_) {
          // Swallowed: losing the scheduling of a background walk must not
          // turn a completed run into an OS retry. The next invocation, and
          // the next completed sync's unconditional `scheduleAll`, try again.
        }
      }
    }
  } catch (_) {
    return false;
  } finally {
    container.dispose();
  }
}

/// Stands in for `SyncManager.start`'s `isSyncing.compareAndSet(false, true)`
/// on the headless path — see the call site for why the port needs two sites
/// where Kotlin needs none.
Future<void> _markSyncActive(PlanetPrefs prefs) async {
  try {
    await prefs.markInteractiveSyncStarted(DateTime.now());
  } catch (_) {
    // Deliberately ignored — a heavy walk may then overlap this run, which is
    // what the Kotlin also permits for a sync started mid-walk.
  }
}

Future<void> _clearSyncActive(PlanetPrefs prefs) async {
  try {
    await prefs.clearInteractiveSyncStarted();
  } catch (_) {
    // Deliberately ignored — the flag decays after
    // `HeavyTableSync.interactiveSyncTimeout` rather than blocking heavy walks
    // for ever, which is exactly what that bound is for.
  }
}

/// The headless half of the voices safety net — see
/// `DashboardSyncNotifier.queuePendingVoices`, which carries the Kotlin
/// reading this is built on.
///
/// **Called from `drainOutbox`, deliberately not from `syncSteps`**, for the
/// reason [sweepPendingSubmissions] gives at length: the step list runs only
/// for an `autoSync` task, only with auto-sync enabled, and only when the
/// interval is due, and `BackgroundWorkCoordinator` cancels the `autoSync` job
/// outright for a user who turns auto-sync off — so a step there would never
/// run at all for exactly the user most likely to have an undelivered post.
/// Kotlin's `uploadNews()` has none of those gates.
///
/// Ordering is free here in a way it is not for submissions: nothing in the
/// step list pulls `news` before this, and the voices pull that does exist
/// (`DashboardSyncArea.voices`) writes a pulled document beside a locally
/// authored row rather than over it — `NewsMapper.fromDoc` keys on the CouchDB
/// `_id`, and an undelivered post has none. It is kept ahead of the drain for
/// the reason that always holds: a row queued after the drain waits for the
/// next invocation.
///
/// [userId] is nullable because Kotlin's sweep takes no user at all — the
/// author travels in the document, and the outbox tag is nothing anyone reads
/// back. A handset whose session has gone is precisely the one with an
/// undelivered post on it.
///
/// Exposed because `executeBackgroundTask` needs a Flutter binding, real
/// preferences and a WorkManager engine, so a body written inline in that
/// closure is unreachable from a unit test.
@visibleForTesting
Future<void> sweepPendingVoices(
  ProviderContainer container, {
  required ServerConfig config,
  required String? userId,
}) async {
  try {
    await container
        .read(voicesUploaderProvider)
        .queuePending(config: config, userId: userId);
  } catch (_) {
    // Swallowed for the reason `sweepPendingSubmissions` is: a throwing
    // `drainOutbox` adds `outboxDrain` to the runner's `failedSteps` and asks
    // the OS to retry the whole task, and no Kotlin caller of `uploadNews()`
    // does that — `UserDataWorker:40` wraps it in `runCatching` and still
    // returns `Result.success()`. It is not hypothetical: `queuePending` reads
    // device identity, which rethrows on an engine with no channel and no
    // primed cache.
  }
}

/// The headless half of the submissions safety net — see
/// `DashboardSyncNotifier.queuePendingSubmissions`, which carries the Kotlin
/// reading this is built on.
///
/// **Called from `drainOutbox`, deliberately not from `syncSteps`.** The first
/// cut put it in the step list beside `shelf_push`, and the implementation
/// audit found that this hides it behind three gates Kotlin's sweeper has none
/// of: `BackgroundTaskRunner.run` returns before the steps for a `maintenance`
/// task, when `autoSyncEnabled` is false, and when the interval is not yet due
/// (`background_task_runner.dart:102-136`). Worse, `BackgroundWorkCoordinator`
/// cancels the `autoSync` job outright when the user turns auto-sync off
/// (`background_work_coordinator.dart:27-30`), so for that user only
/// `maintenance` ever fires and a step in `syncSteps` would never run at all.
///
/// Kotlin's closest sweeper is `ServerReachabilityWorker`, scheduled by
/// `NetworkMonitorWorker.start` from `MainApplication:520-522` with no
/// reference to any sync setting or cadence, sweeping on **network
/// reconnection** — which is exactly the case this net exists for: the handset
/// was offline when the sheet was finished, and the network came back.
/// `drainOutbox` runs ahead of all three gates and on every invocation, so it
/// is the closest the port has to that worker.
///
/// Two properties survive the move. It is still ahead of the `'submissions'`
/// pull, which matters because `SubmissionsRepository.upsertDocuments` writes
/// `isUpdated: false` over every row it writes and keys each on the server
/// `_id` (`submissions_repository.dart`, the `upsertDocuments` row build): a *locally authored* sheet
/// carries a sha1 local id, so a pulled document lands beside it as a separate
/// row and cannot touch its flags — Kotlin identically
/// (`SubmissionsRepositoryImpl:669-670`) — while a sheet that **arrived from
/// the server and was then edited locally** (survey resume's `markComplete`)
/// has the server `_id` for a primary key, so a pull running first overwrites
/// the edit and it never uploads. And the sweep is swallowed rather than
/// reported: a throwing `drainOutbox` adds `outboxDrain` to the runner's
/// `failedSteps` and asks the OS to retry the task, and no Kotlin caller of
/// `uploadSubmissions` does that — `UserDataWorker:48` wraps it in
/// `runCatching` and still returns `Result.success()`, and
/// `UploadManager:236-252` swallows the exception before either could see one.
/// It is not hypothetical: `queuePending` reads device identity before it
/// enqueues anything, and `PlatformDeviceIdentitySource.read` rethrows on a
/// handset with no channel and no primed cache.
///
/// [userId] is nullable because Kotlin's sweep takes no user at all. A handset
/// whose session has gone is precisely the one with somebody else's finished
/// sheet stranded on it, and `SubmissionDao.getPendingSubmissions`
/// (`SubmissionDao:41`) is handset-wide.
///
/// Exposed because `executeBackgroundTask` needs a Flutter binding, real
/// preferences and a WorkManager engine, so a body written inline in that
/// closure is unreachable from a unit test.
@visibleForTesting
Future<void> sweepPendingSubmissions(
  ProviderContainer container, {
  required ServerConfig config,
  required String? userId,
}) async {
  try {
    // Immediately ahead of the submissions sweep, which is exactly where
    // Kotlin has it: `SubmissionsUploader.uploadSubmissionsWithTiming` calls
    // `uploadAdoptedSurveys()` then `uploadSubmissions(...)`
    // (`SubmissionsUploader.kt:83-84`), and `UserDataWorker:47-48` runs the
    // same pair in the same order. The order is load-bearing for the port in a
    // way it is not for Kotlin: the drain that follows records each clone's
    // rev before the surveys pull's `deleteNotIn` runs, so the clone is either
    // named by the walk or still `rev IS NULL` and spared. Either way it
    // survives — see [SurveyDao.deleteNotIn].
    // In its own `try`: `UserDataWorker:47-48` wraps each arm in its own
    // `runCatching`, and this arm can throw where the Kotlin's cannot —
    // `queuePending` reads device identity, which rethrows on an engine with
    // no channel and no primed cache, and a headless engine is exactly that
    // case. Sharing the `try` let one adopted clone skip the submissions
    // safety net entirely.
    try {
      await container
          .read(adoptedSurveysUploaderProvider)
          .queuePending(config: config, userId: userId);
    } catch (_) {
      // Deliberately ignored — see above.
    }
    await container
        .read(submissionsUploaderProvider)
        .queuePending(config: config, userId: userId);
  } catch (_) {
    // Deliberately ignored — see above.
  }
}
