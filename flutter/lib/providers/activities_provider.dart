import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import '../data/local/user_mapper.dart';
import '../repository/activities_repository.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

/// Writes the device's activity log and hands it to the outbox.
///
/// Port of the calls `UserSessionManager.setResourceOpenCount`,
/// `CoursesRepositoryImpl.logCourseVisit` and `SyncManager` make into
/// `ActivitiesRepositoryImpl`. It lives in one place because all three need the
/// same three things — the signed-in user, a locally-minted row id, and a queue
/// step afterwards — and the Kotlin spreads them across a service, a repository
/// and a sync manager.
class ActivityLog {
  const ActivityLog(this.ref);

  final Ref ref;

  /// Port of `setResourceOpenCount(item, KEY_RESOURCE_OPEN)`, reached from
  /// `ResourcesRepositoryImpl.trackResourceOpen` when a resource is opened.
  Future<void> logResourceOpen(MyLibraryRow resource) =>
      _logResource(resource, ActivityTypes.visit);

  /// Port of `setResourceOpenCount(items, KEY_RESOURCE_DOWNLOAD)`, which
  /// `BaseContainerFragment` calls when a download starts.
  Future<void> logResourceDownload(MyLibraryRow resource) =>
      _logResource(resource, ActivityTypes.download);

  Future<void> _logResource(MyLibraryRow resource, String type) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    // Failure is swallowed for the same reason `UserSessionManager` swallows
    // it: losing a telemetry row costs a number on the profile, and letting the
    // exception through would break opening the resource.
    try {
      await ref
          .read(activitiesRepositoryProvider)
          .logResourceOpen(
            id: _mintId(type),
            userId: user.id,
            userName: user.name,
            parentCode: user.parentCode,
            planetCode: user.planetCode,
            title: resource.title,
            // The Kotlin passes `item.resourceId`, the server's id for the
            // resource, not the local row key.
            resourceId: resource.resourceId ?? resource.id,
            type: type,
            time: now,
          );
      await queuePending();
    } catch (_) {
      // Deliberately ignored — see above.
    }
  }

  /// Port of `logCourseVisit`, called when the take-course view opens a course.
  Future<void> logCourseVisit({
    required String courseId,
    required String? title,
  }) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    try {
      await ref
          .read(activitiesRepositoryProvider)
          .logCourseVisit(
            id: _mintId('course'),
            userId: user.id,
            userName: user.name,
            parentCode: user.parentCode,
            planetCode: user.planetCode,
            title: title,
            courseId: courseId,
            time: DateTime.now().millisecondsSinceEpoch,
          );
      await queuePending();
    } catch (_) {
      // Deliberately ignored — see above.
    }
  }

  /// Port of `recordSyncActivity`, which `SyncManager` runs once per completed
  /// sync. The port's equivalent of one `SyncManager` run is the dashboard Sync
  /// center's whole pass, not a single table pull, so it is called from there.
  Future<void> recordSyncActivity() async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    try {
      await ref
          .read(activitiesRepositoryProvider)
          .recordSyncActivity(
            id: _mintId(ActivityTypes.sync),
            userId: user.id,
            userName: user.name,
            parentCode: user.parentCode,
            planetCode: user.planetCode,
            time: DateTime.now().millisecondsSinceEpoch,
          );
      await queuePending();
    } catch (_) {
      // Deliberately ignored — see above.
    }
  }

  /// Port of `recordSyncUserChallengeAction`, which the dashboard calls right
  /// before the manual-sync flow begins. The row is the challenge dialog's
  /// source of truth for whether the user has done a sync: `hasUserCompletedSync`
  /// counts it. Failure is swallowed for the same reason `recordSyncActivity`
  /// swallows it — a lost row costs a checkbox, not a sync.
  Future<void> recordSyncChallengeAction() async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null || UserMapper.isGuest(user)) return;
    try {
      await ref
          .read(activitiesRepositoryProvider)
          .recordSyncUserChallengeAction(user.id);
    } catch (_) {
      // Deliberately ignored — see above.
    }
  }

  /// Hands every un-uploaded activity row to the durable outbox.
  Future<int> queuePending() async {
    final config = ref.read(serverConfigProvider);
    if (config == null) return 0;
    return ref.read(activitiesUploaderProvider).queuePending(config: config);
  }

  /// Counter that makes [_mintId] unique for two rows minted in the same
  /// microsecond — `DateTime.now()` does not guarantee distinct values between
  /// two adjacent calls, and the row key is a primary key, so a collision means
  /// the second `insertOnConflictUpdate` silently overwrites the first open
  /// instead of recording a second one. This is the same defect the chat and
  /// feedback `_generateId` helpers shipped with, one layer up.
  static int _sequence = 0;

  /// Locally-minted row key. The Kotlin uses `UUID.randomUUID()`; a prefixed
  /// stamp plus [_sequence] is unique on one device without pulling in a uuid
  /// dependency, and matches how `SessionNotifier` mints its login row id.
  static String _mintId(String kind) =>
      '$kind:${DateTime.now().microsecondsSinceEpoch}:${_sequence++}';
}

final activityLogProvider = Provider(ActivityLog.new);

/// The profile's activity rows — `UserProfileViewModel`'s `lastVisit`,
/// `offlineVisits`, `maxOpenedResource` and `numberOfResourceOpen`, resolved
/// together because the screen shows them as one block.
class ProfileActivityStats {
  const ProfileActivityStats({
    this.lastVisit,
    this.offlineVisits = 0,
    this.mostOpened,
    this.resourceOpenCount = 0,
  });

  final int? lastVisit;
  final int offlineVisits;
  final MostOpenedResource? mostOpened;
  final int resourceOpenCount;
}

final profileActivityStatsProvider = FutureProvider<ProfileActivityStats>((
  ref,
) async {
  final user = ref.watch(sessionProvider).valueOrNull;
  if (user == null) return const ProfileActivityStats();
  final activities = ref.watch(activitiesRepositoryProvider);
  final name = user.name ?? '';
  return ProfileActivityStats(
    // `getGlobalLastVisit()` has no user predicate; that is the Kotlin's.
    lastVisit: await activities.globalLastVisit(),
    offlineVisits: await activities.offlineVisitCount(user.id),
    mostOpened: await activities.mostOpenedResource(name, ActivityTypes.visit),
    resourceOpenCount: await activities.resourceOpenCount(
      name,
      ActivityTypes.visit,
    ),
  );
});

/// Drives the `login_activities` pull.
///
/// A sync with no caller is the failure this port has shipped three times (see
/// the migration doc), so the pull is registered as a Sync center area rather
/// than left as library code.
class ActivitiesSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) => ref
      .read(activitiesRepositoryProvider)
      .sync(config: config, onProgress: onProgress);
}

final activitiesSyncProvider =
    NotifierProvider<ActivitiesSyncNotifier, SyncUiState>(
      ActivitiesSyncNotifier.new,
    );
