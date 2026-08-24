import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/system/disk_stats.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

/// The resource-list search box. Replaces the `searchTags`/`etSearch` state that
/// `BaseRecyclerFragment` keeps as fields.
final resourceSearchQueryProvider = StateProvider<String>((ref) => '');

/// Whether the resources screen shows only the user's shelf (joined resources)
/// or the full catalog. Mirrors the Kotlin `isMyCourseLib` argument that
/// `ResourcesFragment` reads (`08e18ffdc`): the dashboard library card sets
/// this to `true` when the user has shelf items and `false` when they do not,
/// so the card lands on the shelf or the catalog respectively.
final resourceShelfOnlyProvider = StateProvider<bool>((ref) => false);

/// Offline-first resource list.
///
/// Reads only from SQLite, so it emits immediately with whatever was last
/// synced and never blocks on the network. Rebuilding on
/// [resourceSearchQueryProvider] re-runs the query; a background sync writing to
/// `my_library` pushes a fresh list into the same stream. When
/// [resourceShelfOnlyProvider] is on, the list is scoped to the signed-in
/// user's shelf (`watchResources(shelfUserId:)`), the `isMyCourseLib` view.
final resourcesStreamProvider = StreamProvider<List<MyLibraryRow>>((ref) {
  final query = ref.watch(resourceSearchQueryProvider);
  final shelfOnly = ref.watch(resourceShelfOnlyProvider);
  if (shelfOnly) {
    final userId = ref.watch(sessionProvider).valueOrNull?.id;
    if (userId != null && userId.isNotEmpty) {
      return ref
          .watch(resourcesRepositoryProvider)
          .watchResources(query: query, shelfUserId: userId);
    }
  }
  return ref.watch(resourcesRepositoryProvider).watchResources(query: query);
});

class ResourceShelfActions {
  const ResourceShelfActions(this.ref);
  final Ref ref;

  Future<void> setMemberships(
    Iterable<String> resourceIds, {
    required bool joined,
  }) async {
    final user = ref.read(sessionProvider).valueOrNull;
    if (user == null) return;
    await ref
        .read(resourcesRepositoryProvider)
        .setShelfMemberships(resourceIds, user.id, joined: joined);

    final config = ref.read(serverConfigProvider);
    final couchId = user.couchId;
    if (config == null || couchId == null || couchId.isEmpty) return;
    await ref
        .read(shelfRepositoryProvider)
        .upload(config: config, userId: user.id, shelfDocId: couchId);
  }
}

final resourceShelfActionsProvider = Provider(ResourceShelfActions.new);

class ResourceSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) async {
    // The two CouchDB caches are independent tables, so the pulls run
    // concurrently. Tags ride along with resources because the resources
    // screen's collections filter reads them together (the Kotlin pulls
    // `tags` as part of every full sync; see `SyncManager`'s table list).
    final resourcesResult = ref
        .read(resourcesRepositoryProvider)
        .sync(config: config, onProgress: onProgress);
    final tagsResult = ref
        .read(tagsRepositoryProvider)
        .sync(config: config, onProgress: onProgress);

    final [a, b] = await Future.wait([resourcesResult, tagsResult]);
    final totalSaved = [
      a,
      b,
    ].fold<int>(0, (sum, r) => sum + (r is SyncComplete ? r.savedCount : 0));
    final failed = [a, b].whereType<SyncFailed>().firstOrNull;
    return failed ?? SyncComplete(totalSaved);
  }
}

final resourceSyncProvider =
    NotifierProvider<ResourceSyncNotifier, SyncUiState>(
      ResourceSyncNotifier.new,
    );

/// The device's total and available storage. Backed by the `disk_stats` method
/// channel in production (`DiskStats.instance`); tests override this with a
/// fake so the storage breakdown screen can render a known `available/total`
/// string without a platform call.
final diskStatsProvider = Provider<DiskStats>((ref) => DiskStats.instance);

/// The result of a "free up space" pass — bytes freed and files deleted — kept
/// on a provider so the storage breakdown screen can show the summary snackbar
/// the way the Kotlin's `WorkInfo.outputData` fed it back to
/// `StorageBreakdownFragment`. `null` until the first run.
typedef FreeSpaceResult = ({int deletedFiles, int freedBytes});

final freeSpaceResultProvider = StateProvider<FreeSpaceResult?>((ref) => null);
