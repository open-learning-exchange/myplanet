import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:workmanager/workmanager.dart';

import 'core/background/background_task_runner.dart';
import 'core/background/background_task_names.dart';
import 'core/background/background_download_queue.dart';
import 'core/network/network_result.dart';
import 'core/prefs/planet_prefs.dart';
import 'core/sync/sync_result.dart';
import 'providers/app_providers.dart';
import 'repository/personals_uploader.dart';

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
      drainOutbox: () async {
        if (config == null) return;
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
  } catch (_) {
    return false;
  } finally {
    container.dispose();
  }
}
