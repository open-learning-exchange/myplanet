import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../repository/personals_uploader.dart';
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

    // First, as `startFullSync` has it -- and before the challenge write
    // below, which reads the session with `.valueOrNull`: resolving
    // `sessionProvider` here means that read finds a value rather than the
    // null it would otherwise see on the first pass.
    await pushCurrentUserShelf();

    // `DashboardElementActivity.logSyncInSharedPrefs` records the challenge
    // action right before the sync starts -- the challenge dialog's "sync"
    // checkbox reads it via `hasUserCompletedSync`. It stays ahead of the
    // sweep below, which is unbounded network work: a user who taps Sync and
    // backgrounds the app during it should still get the credit Kotlin gives
    // them for pressing the button.
    await ref.read(activityLogProvider).recordSyncChallengeAction();

    // Ahead of the submissions sweep, as both Kotlin workers have it
    // (`AutoSyncWorker:130` before `:136`, `UserDataWorker:40` before `:48`).
    // It queues only; the unscoped drain at the end of
    // [queuePendingSubmissions] is what carries these rows out in this same
    // pass — the coupling `syncAll delivers a voice nothing enqueued` pins.
    await queuePendingVoices();

    // With the shelf push, ahead of the pulls. See [queuePendingSubmissions].
    await queuePendingSubmissions();

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

  /// Port of `SyncManager.pushCurrentUserShelf`, which `startFullSync` runs
  /// *before* the parallel pull phase begins (upstream `9255eac`).
  ///
  /// The ordering is the whole point, and it is the opposite end of the pass
  /// from [DashboardSyncArea.shelf]: that area *pulls* the shelf document and
  /// therefore has to run last, after `resources` and `courses` have written
  /// and pruned the rows it stamps. This pushes the local shelf *first*, so a
  /// course or resource the user added or removed since the last sync reaches
  /// the server before the pull that would otherwise read the server's older
  /// copy back over it. Without it the only push was the one the add/remove
  /// UI fires inline (`ResourceShelfActions.setMemberships`,
  /// `CourseDetailScreen._setMembership`), which is lost if that attempt was
  /// offline — nothing rescanned it, and the next sync pulled the stale
  /// server document instead.
  ///
  /// Kotlin catches `Exception` (rethrowing `CancellationException`), logs and
  /// continues, so a shelf push that cannot reach the server must not stop the
  /// sync. `upload` also reports a reachability failure as a returned
  /// `SyncFailed` rather than a throw; both mean the same thing here, so the
  /// result is deliberately not inspected.
  ///
  /// The session is awaited rather than read: Kotlin resolves its own user
  /// (`userRepository.getUserModel()`) before deciding whether to push, and
  /// `ref.read(sessionProvider).valueOrNull` is null on any pass that reaches
  /// this before something else has resolved it — which would silently skip
  /// the push. The `await` sits inside the `try` because the future can reject
  /// where `valueOrNull` could not.
  Future<void> pushCurrentUserShelf() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return;
    try {
      final user = await ref.read(sessionProvider.future);
      if (user == null) return;
      final shelfDocId = user.couchId;
      if (shelfDocId == null || shelfDocId.isEmpty) return;
      await ref
          .read(shelfRepositoryProvider)
          .upload(config: config, userId: user.id, shelfDocId: shelfDocId);
    } catch (_) {
      // Deliberately ignored — see above.
    }
  }

  /// Port of `uploadManager.uploadSubmissions()` as the sync path's safety net.
  ///
  /// **Kotlin reaches the server for a finished answer sheet twice.** Once from
  /// the profile dialog's dismissal (`UserInformationFragment:303` →
  /// `SubmissionsUploader:84`), and once from
  /// `uploadManager.uploadSubmissions()`, which `AutoSyncWorker:136`,
  /// `UserDataWorker:48` and `ServerReachabilityWorker:196` run on a schedule
  /// rather than off anything the sheet did. The first two call it bare;
  /// `ServerReachabilityWorker` gates on `!isSyncRunning` and
  /// `hasPendingOfflineSubmissions()` (`ServerReachabilityWorker:190-200`),
  /// neither of which is a property of an individual sheet. A missed dismissal
  /// therefore costs nothing there: the next sync sweeps it up.
  ///
  /// `UserDataWorker:48` is what puts this in *this* method rather than only in
  /// the headless path. It is not a background job in the sense the name
  /// suggests: the dashboard's sync button reaches
  /// `SyncActivity.continueSyncProcess` with `forceSync`, which fires
  /// `isServerReachable(url, "upload")` **and** `startUpload("")`
  /// (`SyncActivity:813-815`) — the pull and, through `uploadBulkData()`, that
  /// worker. `SyncManager.startFullSync` itself sweeps nothing; the manual
  /// sweep is a sibling job launched beside it, which is exactly the relation
  /// this method has to the areas below.
  ///
  /// The port had only *write-time* call sites, so it had no net at all. A
  /// sheet whose one enqueue call never ran — process death on "Your
  /// information", the `if (!mounted) return` before the push, a queue call
  /// that threw on an exam attempt — stayed on the handset as a `complete`,
  /// `isUpdated`, `!uploaded` row with no outbox row, and stayed there until
  /// the same user happened to finish some *other* submission, because
  /// `queuePending` is an unscoped sweep that then rescues it incidentally.
  /// The answers were never lost, but for a field survey an indefinitely
  /// deferred delivery is the same outcome.
  ///
  /// Five things about the shape, each a reading of the Kotlin rather than a
  /// guess at it:
  ///
  /// * **Unscoped.** `SubmissionDao.getPendingSubmissions` (`SubmissionDao:41`)
  ///   carries no `userId` predicate and `uploadSubmissions()` takes no user —
  ///   the owner is resolved per row (`UploadConfigs:258`), so the session is
  ///   only the outbox row's tag. Hence the nullable `userId`.
  /// * **Ungated.** Unlike [_uploadMyPlanetActivities] and
  ///   [_queueSearchActivities], this does *not* wait for `successCount > 0`.
  ///   The manual sweep runs concurrently with the pull and inspects nothing
  ///   about it, and `ServerReachabilityWorker` sweeps with no pull at all. A
  ///   failed pull pass is not a reason to leave a finished sheet on the
  ///   device.
  /// * **Its own `try`, not a shared one.** `AutoSyncWorker:121-138` runs the
  ///   sweep as the sixteenth statement of one sequential `try`, so an earlier
  ///   upload throwing skips it silently — `uploadAchievement`, `uploadNews`
  ///   and `uploadTeams` each fetch outside their own guard and can. That is a
  ///   Kotlin weakness rather than a behaviour to reproduce;
  ///   `UserDataWorker`'s per-step `runCatching` is the shape to follow, and
  ///   it is this one.
  /// * **Ahead of the pulls.** Nothing in this pass pulls `submissions` at all
  ///   (a gap of its own — see the phase notes), so ordering is free here; the
  ///   headless path's is not, and `sweepPendingSubmissions` carries that
  ///   reasoning. Kotlin agrees by construction either way: its submissions
  ///   pull lives in `HeavyTableSyncWorker` (`HeavyTableSyncWorker:39-41`),
  ///   which `startFullSync` only *schedules* at its end (`SyncManager:209`).
  /// * **Drained, not merely queued, and drained whole.** Kotlin's sweep
  ///   *posts*; `queuePending` only queues, and the drain trigger is otherwise
  ///   app resume. `submissions_screen:186-193` is the one write-time site
  ///   that pairs the two — the other three (`take_survey_screen:276-278`,
  ///   `user_information_screen:471`, `take_exam_screen:542-547`) queue and
  ///   leave — and it drains **unscoped**, which is what this does too.
  ///   Scoping it to one `uploadType` was the first cut and was wrong twice
  ///   over: `OutboxDrainer.drain` satisfies a *joining* caller without doing
  ///   its work (`outbox_drainer.dart:79-82`), so a resume drain arriving
  ///   during a scoped pass would return having sent nothing and every queued
  ///   health record, rating and team write would wait for the next resume —
  ///   and Kotlin's manual sync is a whole-queue flush anyway,
  ///   `UserDataWorker`'s `UPLOAD_TYPE_BULK` running eleven upload arms of
  ///   which `uploadSubmissions()` is one.
  ///
  /// **It cannot double-post, and the protection is not new.** Kotlin's only
  /// defence is exclusion from that same predicate: `SubmissionDao:44` sets
  /// `_id`, `_rev` and `isUpdated = 0` after a successful send, and
  /// [SubmissionsUploader.handler]'s `markUploaded` is the port of exactly
  /// that statement. The port adds one Kotlin lacks —
  /// `OutboxRepository.enqueue` keys on `(uploadType, itemId)`, so a sweep
  /// that overlaps a write-time enqueue refreshes the queued row instead of
  /// adding a second one, and `OutboxDrainer`'s single-flight guard plus its
  /// status-scoped claim keep two drains off one row.
  ///
  /// Swallowed for the reason the neighbouring steps are, and it is not
  /// hypothetical: `queuePending` reads device identity before it enqueues
  /// anything, and `PlatformDeviceIdentitySource.read` rethrows on a handset
  /// with no channel and no primed cache. A sheet that cannot be queued must
  /// not flip the sync to failed — no Kotlin caller of `uploadSubmissions`
  /// reports a failure of it, and `UploadManager:236-252` swallows every
  /// exception before they could see one.
  Future<void> queuePendingSubmissions() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return;
    try {
      // Awaited rather than read: this notifier never watches
      // `sessionProvider`, so `.valueOrNull` would be null on any pass that
      // reached here first. The `await` is inside the `try` because the future
      // can reject where `valueOrNull` could not.
      final user = await ref.read(sessionProvider.future);
      // Ahead of the submissions sweep, as `SubmissionsUploader.kt:83-84` has
      // it — and, unlike in Kotlin, ahead of the surveys pull below, which is
      // what keeps `SurveyDao.deleteNotIn` from destroying a team's adopted
      // clone: the drain below records the clone's rev, so by the time the
      // prune runs the walk names it. A drain that fails leaves the row
      // `needsSync`, which that prune spares.
      //
      // **In its own `try`, for the reason this method's own doc gives for
      // not sharing one.** `UserDataWorker:47-48` wraps each arm in its own
      // `runCatching`, and `SubmissionsUploader.kt:80-88`'s shared `try` is
      // not a counter-example because `uploadAdoptedSurveys()` cannot throw —
      // `UploadCoordinator.runPipeline` catches `Exception` internally
      // (`UploadCoordinator.kt:87-92`). This one can: `queuePending` reads
      // device identity, and `PlatformDeviceIdentitySource.read` rethrows on
      // an engine with no channel and no primed cache. Sharing the `try` let
      // one adopted clone on such a handset skip the submissions safety net
      // Phase 134 added *and* the whole outbox drain.
      try {
        await ref
            .read(adoptedSurveysUploaderProvider)
            .queuePending(config: config, userId: user?.id);
      } catch (_) {
        // Deliberately ignored — see above.
      }
      await ref
          .read(submissionsUploaderProvider)
          .queuePending(config: config, userId: user?.id);
      await ref
          .read(outboxDrainerProvider)
          .drain(authHeader: PersonalsUploader.authHeaderFor(config));
    } catch (_) {
      // Deliberately ignored — see above.
    }
  }

  /// Port of `uploadManager.uploadNews()` as the sync path's safety net.
  ///
  /// **Kotlin has no write-time upload for a voice at all.** Composing one
  /// writes a `news` row and stops; the document reaches the server when
  /// `UploadManager.uploadNews()` next runs, and that runs from
  /// `AutoSyncWorker:130` and `UserDataWorker:40` off nothing the post did.
  /// `VoicesRepositoryImpl.getNewsForUpload()` reads the whole table minus
  /// guests, so a voice cannot be stranded there.
  ///
  /// The port inverted the relation: `VoicesActions` enqueues at write time —
  /// better when it runs — and nothing swept. `VoicesActions.queuePending`
  /// returns 0 when `serverConfigProvider` is null, and every caller reports
  /// success regardless, so a voice composed before the server was configured
  /// (or across a write that threw, or a process death between the row and the
  /// enqueue) sat on the handset until the same user happened to make some
  /// *other* voices write, because `queuePending` is an unscoped sweep that
  /// then rescues it incidentally. Phase 134 found and fixed exactly this shape
  /// for submissions; this is the voices half.
  ///
  /// The shape follows [queuePendingSubmissions], for the same readings:
  ///
  /// * **Unscoped.** `getNewsForUpload()` carries no `userId` predicate and
  ///   `uploadNews()` takes no user — the author travels in the document. The
  ///   session is only the outbox row's tag, hence the nullable `userId`.
  /// * **Ungated.** Not conditional on `successCount > 0`: `UserDataWorker`
  ///   sweeps inside its own `runCatching` and inspects no pull.
  /// * **Its own `try`, and swallowed.** `UserDataWorker:40`'s per-step
  ///   `runCatching` is the shape, not `AutoSyncWorker`'s shared `try` — and
  ///   this can throw where Kotlin's cannot, because `queuePending` reads
  ///   device identity and `PlatformDeviceIdentitySource.read` rethrows on an
  ///   engine with no channel and no primed cache. A voice that cannot be
  ///   queued must not flip the sync to failed.
  /// * **Queued, not drained, here.** [queuePendingSubmissions] follows
  ///   immediately and its drain is unscoped, so these rows go out in this same
  ///   pass. Draining twice would only add a redundant single-flight join.
  ///
  /// **It cannot double-post.** Two protections, one of them Kotlin's:
  /// `markUploaded` stamps `_id`/`_rev` and clears `isEdited`, which takes the
  /// row out of [VoicesRepository.pendingUploads] — the port of
  /// `markNewsUploaded`. The port adds two more:
  /// `OutboxRepository.enqueue` keys on `(uploadType, itemId)`, and
  /// [VoicesUploader.queuePending] skips a row whose send is in flight.
  ///
  /// **One divergence, now systematic rather than incidental, and deliberate.**
  /// `getNewsForUpload()` returns *every* non-guest row and re-sends it on each
  /// sync — a `_rev`-carrying update, so it is not a duplicate, but it churns a
  /// revision per post per sync. [VoicesRepository.pendingUploads] returns only
  /// rows that were never delivered or have been edited since. Every local
  /// mutation the port has sets `isEdited` (`editPost`, `shareToCommunity`, the
  /// un-share branch of `deletePost`, `toggleReaction`), so nothing a user can
  /// do leaves a changed row outside the set; what the narrower predicate gives
  /// up is Kotlin's incidental repair of a *server-side* divergence, which no
  /// reader on either side depends on. Widening it would refill the outbox with
  /// unchanged documents on every sync.
  Future<void> queuePendingVoices() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return;
    try {
      // Awaited rather than read, and inside the `try`: this notifier never
      // watches `sessionProvider`, so `.valueOrNull` would be null on any pass
      // that reached here first, and the future can reject where it could not.
      final user = await ref.read(sessionProvider.future);
      await ref
          .read(voicesUploaderProvider)
          .queuePending(config: config, userId: user?.id);
    } catch (_) {
      // Deliberately ignored — see above.
    }
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
