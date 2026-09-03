import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_providers.dart';
import 'activities_provider.dart';
import 'session_provider.dart';
import 'chat_provider.dart';
import 'courses_providers.dart';
import 'events_provider.dart';
import 'feedback_provider.dart';
import 'health_provider.dart';
import 'notifications_provider.dart';
import 'resources_providers.dart';
import 'surveys_provider.dart';
import 'sync_state.dart';
import 'sync_walk_providers.dart';
import 'teams_provider.dart';
import 'voices_provider.dart';

/// The order is the order [DashboardSyncNotifier.syncAll] runs them in, and two
/// positions are load-bearing, matching `SyncManager.startFullSync`:
///
/// * [tabletUsers] before [shelf], because a shelf document is keyed by its
///   owner's CouchDB id and the stamp has to be resolved to the local `users`
///   row that carries it.
/// * [shelf] last, because it augments `my_library` and `courses` rows that
///   [resources] and [courses] write — and prune — earlier in the same pass.
///   Kotlin runs it as phase 3, after the whole parallel set and the resources
///   pull, for the same reason.
enum DashboardSyncArea {
  resources,
  courses,
  teams,
  events,
  surveys,
  voices,
  feedback,
  chat,
  health,
  activities,
  notifications,
  tabletUsers,
  ratings,
  tasks,
  achievements,
  shelf,
}

enum DashboardSyncStatus { waiting, running, succeeded, failed }

class DashboardSyncItem {
  const DashboardSyncItem({
    required this.area,
    this.status = DashboardSyncStatus.waiting,
    this.savedCount = 0,
    this.message,
  });

  final DashboardSyncArea area;
  final DashboardSyncStatus status;
  final int savedCount;
  final String? message;

  DashboardSyncItem copyWith({
    DashboardSyncStatus? status,
    int? savedCount,
    String? message,
    bool clearMessage = false,
  }) => DashboardSyncItem(
    area: area,
    status: status ?? this.status,
    savedCount: savedCount ?? this.savedCount,
    message: clearMessage ? null : (message ?? this.message),
  );
}

class DashboardSyncState {
  const DashboardSyncState({
    required this.items,
    this.running = false,
    this.startedAt,
    this.finishedAt,
  });

  factory DashboardSyncState.idle() => DashboardSyncState(
    items: [
      for (final area in DashboardSyncArea.values)
        DashboardSyncItem(area: area),
    ],
  );

  final List<DashboardSyncItem> items;
  final bool running;
  final DateTime? startedAt;
  final DateTime? finishedAt;

  int get completedCount => items
      .where(
        (item) =>
            item.status == DashboardSyncStatus.succeeded ||
            item.status == DashboardSyncStatus.failed,
      )
      .length;

  int get successCount => items
      .where((item) => item.status == DashboardSyncStatus.succeeded)
      .length;

  int get failureCount =>
      items.where((item) => item.status == DashboardSyncStatus.failed).length;

  double get progress => items.isEmpty ? 0 : completedCount / items.length;

  DashboardSyncState copyWith({
    List<DashboardSyncItem>? items,
    bool? running,
    DateTime? startedAt,
    DateTime? finishedAt,
    bool clearFinishedAt = false,
  }) => DashboardSyncState(
    items: items ?? this.items,
    running: running ?? this.running,
    startedAt: startedAt ?? this.startedAt,
    finishedAt: clearFinishedAt ? null : (finishedAt ?? this.finishedAt),
  );
}

class DashboardSyncNotifier extends Notifier<DashboardSyncState> {
  @override
  DashboardSyncState build() => DashboardSyncState.idle();

  Future<void> syncAll() async {
    if (state.running) return;

    state = DashboardSyncState.idle().copyWith(
      running: true,
      startedAt: DateTime.now(),
      clearFinishedAt: true,
    );

    // `DashboardElementActivity.logSyncInSharedPrefs` records the challenge
    // action right before the sync starts -- the challenge dialog's "sync"
    // checkbox reads it via `hasUserCompletedSync`.
    await ref.read(activityLogProvider).recordSyncChallengeAction();

    for (final area in DashboardSyncArea.values) {
      await _syncArea(area);
    }

    // Port of `SyncManager`'s `transactionSyncManager.syncNotificationReads()`
    // phase - runs after the table pulls and before `recordSyncActivity`,
    // so a read state that landed during the sync uploads in the same pass.
    // Swallowed: a failed upload must not flip a successful sync to failed
    // (the Kotlin calls it in a fire-and-collect `async`/`awaitAll`).
    await _syncNotificationReads();

    await _recordSyncActivity();
    await _uploadMyPlanetActivities();
    await _queueSearchActivities();

    state = state.copyWith(running: false, finishedAt: DateTime.now());
  }

  Future<void> retry(DashboardSyncArea area) async {
    if (state.running) return;
    state = state.copyWith(
      running: true,
      startedAt: DateTime.now(),
      clearFinishedAt: true,
    );
    await _syncArea(area);
    state = state.copyWith(running: false, finishedAt: DateTime.now());
  }

  /// Port of `SyncManager`'s `recordSyncActivity` call.
  ///
  /// Kotlin records one row per `SyncManager` run; the port's equivalent of a
  /// run is this whole pass, not an individual table pull, so it is recorded
  /// here rather than inside `SyncNotifier.sync`. Recorded when at least one
  /// area succeeded: the Kotlin records unconditionally at the end of its sync,
  /// but its sync aborts on failure, so a pass where every area failed has no
  /// Kotlin counterpart to be faithful to.
  Future<void> _recordSyncActivity() async {
    if (state.successCount == 0) return;
    await ref.read(activityLogProvider).recordSyncActivity();
  }

  /// Port of the `myplanet_activities` upload `AutoSyncWorker` /
  /// `UserDataWorker` fire at the end of a completed sync via
  /// `UploadManager.uploadActivities`'s `uploadMyPlanetActivities` half.
  ///
  /// Posted only when at least one area succeeded — the Kotlin aborts its sync
  /// on failure, so a fully-failed pass has no Kotlin counterpart to be
  /// faithful to. Swallowed on error for the same reason `_recordSyncActivity`
  /// is: losing telemetry must not flip the sync itself to failed.
  Future<void> _uploadMyPlanetActivities() async {
    if (state.successCount == 0) return;
    final config = ref.read(serverConfigProvider);
    final user = ref.read(sessionProvider).valueOrNull;
    if (config == null || user == null) return;
    try {
      await ref
          .read(myPlanetActivitiesUploaderProvider)
          .upload(user: user, config: config);
    } catch (_) {
      // Deliberately ignored — see above.
    }
  }

  /// Port of `UploadManager.uploadSearchActivity`, which
  /// `AutoSyncWorker`/`UserDataWorker` fire at the end of a completed sync.
  /// Queues pending search-activity rows into the outbox; the drainer sends
  /// them on app resume. Swallowed on error for the same reason
  /// `_uploadMyPlanetActivities` is: losing telemetry must not flip the sync
  /// itself to failed.
  Future<void> _queueSearchActivities() async {
    if (state.successCount == 0) return;
    final config = ref.read(serverConfigProvider);
    final user = ref.read(sessionProvider).valueOrNull;
    if (config == null) return;
    try {
      await ref
          .read(searchActivityUploaderProvider)
          .queuePending(config: config, userId: user?.id);
    } catch (_) {
      // Deliberately ignored - see above.
    }
  }

  /// Port of `SyncManager`'s `syncNotificationReads` phase. Unlike
  /// [_uploadMyPlanetActivities] this runs even on a fully-failed pull pass:
  /// read-state upload is independent of whether any table pulled, so a row
  /// marked read before a failed sync still uploads. Swallowed on error for
  /// the same reason as the telemetry uploads above.
  Future<void> _syncNotificationReads() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return;
    try {
      await ref
          .read(notificationsRepositoryProvider)
          .syncNotificationReads(config);
    } catch (_) {
      // Deliberately ignored - see above.
    }
  }

  Future<void> _syncArea(DashboardSyncArea area) async {
    _replace(
      area,
      (item) => item.copyWith(
        status: DashboardSyncStatus.running,
        savedCount: 0,
        clearMessage: true,
      ),
    );

    await switch (area) {
      DashboardSyncArea.resources =>
        ref.read(resourceSyncProvider.notifier).sync(),
      DashboardSyncArea.courses => ref.read(courseSyncProvider.notifier).sync(),
      DashboardSyncArea.teams => ref.read(teamsSyncProvider.notifier).sync(),
      DashboardSyncArea.events => ref.read(eventsSyncProvider.notifier).sync(),
      DashboardSyncArea.surveys =>
        ref.read(surveysSyncProvider.notifier).sync(),
      DashboardSyncArea.voices => ref.read(voicesSyncProvider.notifier).sync(),
      DashboardSyncArea.feedback =>
        ref.read(feedbackSyncProvider.notifier).sync(),
      DashboardSyncArea.chat => ref.read(chatSyncProvider.notifier).sync(),
      DashboardSyncArea.health => ref.read(healthSyncProvider.notifier).sync(),
      DashboardSyncArea.activities =>
        ref.read(activitiesSyncProvider.notifier).sync(),
      DashboardSyncArea.notifications =>
        ref.read(notificationsSyncProvider.notifier).sync(),
      DashboardSyncArea.tabletUsers =>
        ref.read(tabletUsersSyncProvider.notifier).sync(),
      DashboardSyncArea.ratings =>
        ref.read(ratingsSyncProvider.notifier).sync(),
      DashboardSyncArea.tasks =>
        ref.read(teamTasksSyncProvider.notifier).sync(),
      DashboardSyncArea.achievements =>
        ref.read(achievementsSyncProvider.notifier).sync(),
      DashboardSyncArea.shelf => ref.read(shelfSyncProvider.notifier).sync(),
    };

    final result = switch (area) {
      DashboardSyncArea.resources => ref.read(resourceSyncProvider),
      DashboardSyncArea.courses => ref.read(courseSyncProvider),
      DashboardSyncArea.teams => ref.read(teamsSyncProvider),
      DashboardSyncArea.events => ref.read(eventsSyncProvider),
      DashboardSyncArea.surveys => ref.read(surveysSyncProvider),
      DashboardSyncArea.voices => ref.read(voicesSyncProvider),
      DashboardSyncArea.feedback => ref.read(feedbackSyncProvider),
      DashboardSyncArea.chat => ref.read(chatSyncProvider),
      DashboardSyncArea.health => ref.read(healthSyncProvider),
      DashboardSyncArea.activities => ref.read(activitiesSyncProvider),
      DashboardSyncArea.notifications => ref.read(notificationsSyncProvider),
      DashboardSyncArea.tabletUsers => ref.read(tabletUsersSyncProvider),
      DashboardSyncArea.ratings => ref.read(ratingsSyncProvider),
      DashboardSyncArea.tasks => ref.read(teamTasksSyncProvider),
      DashboardSyncArea.achievements => ref.read(achievementsSyncProvider),
      DashboardSyncArea.shelf => ref.read(shelfSyncProvider),
    };

    _replace(
      area,
      (item) => switch (result) {
        SyncSucceeded(:final savedCount) => item.copyWith(
          status: DashboardSyncStatus.succeeded,
          savedCount: savedCount,
          clearMessage: true,
        ),
        SyncErrored(:final message) => item.copyWith(
          status: DashboardSyncStatus.failed,
          message: message,
        ),
        _ => item.copyWith(
          status: DashboardSyncStatus.failed,
          message: 'Sync did not reach a terminal state',
        ),
      },
    );
  }

  void _replace(
    DashboardSyncArea area,
    DashboardSyncItem Function(DashboardSyncItem item) update,
  ) {
    state = state.copyWith(
      items: [
        for (final item in state.items)
          if (item.area == area) update(item) else item,
      ],
    );
  }
}

final dashboardSyncProvider =
    NotifierProvider<DashboardSyncNotifier, DashboardSyncState>(
      DashboardSyncNotifier.new,
    );
