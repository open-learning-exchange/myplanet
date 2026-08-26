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

/// Sort state for the resources list — a port of `ResourcesViewModel`'s
/// `sortMode`/`isAscending`/`isTitleAscending` fields. Each sort action
/// switches the mode *and* flips that mode's own direction flag, so switching
/// between modes never disturbs the other mode's direction. The initial
/// directions are the post-`14a9f14` ones: the first date toggle sorts
/// newest-first, the first title toggle sorts A–Z.
enum ResourceSortMode { none, date, title }

class ResourceSortState {
  const ResourceSortState({
    this.mode = ResourceSortMode.none,
    this.dateAscending = true,
    this.titleAscending = false,
  });

  final ResourceSortMode mode;
  final bool dateAscending;
  final bool titleAscending;

  ResourceSortState toggleDate() => ResourceSortState(
    mode: ResourceSortMode.date,
    dateAscending: !dateAscending,
    titleAscending: titleAscending,
  );

  ResourceSortState toggleTitle() => ResourceSortState(
    mode: ResourceSortMode.title,
    dateAscending: dateAscending,
    titleAscending: !titleAscending,
  );
}

final resourceSortProvider = StateProvider<ResourceSortState>(
  (ref) => const ResourceSortState(),
);

/// Port of `ResourcesViewModel.applyCurrentSort`, applied to the filtered
/// list at build time so a sync pushing fresh rows into the stream keeps the
/// chosen order — the same thing the Kotlin gets by re-sorting on every
/// `getLibraryListModels` refresh. The title key is the lower-cased title
/// with a null title sorting as "" (first when ascending); the date key is
/// `createdDate`. Kotlin's `sortedBy` is stable and Dart's `List.sort` is
/// not, so equal keys are tie-broken on the original index to keep the
/// stream order between them, matching the Kotlin exactly.
List<MyLibraryRow> applyResourceSort(
  List<MyLibraryRow> items,
  ResourceSortState sort,
) {
  if (sort.mode == ResourceSortMode.none || items.length < 2) return items;
  final direction = switch (sort.mode) {
    ResourceSortMode.date => sort.dateAscending ? 1 : -1,
    ResourceSortMode.title => sort.titleAscending ? 1 : -1,
    ResourceSortMode.none => 1,
  };
  int keyCompare(MyLibraryRow a, MyLibraryRow b) => switch (sort.mode) {
    ResourceSortMode.date => a.createdDate.compareTo(b.createdDate),
    ResourceSortMode.title => (a.title ?? '').toLowerCase().compareTo(
      (b.title ?? '').toLowerCase(),
    ),
    ResourceSortMode.none => 0,
  };
  final indexed = items.indexed.toList()
    ..sort((a, b) {
      final result = keyCompare(a.$2, b.$2) * direction;
      return result != 0 ? result : a.$1.compareTo(b.$1);
    });
  return [for (final entry in indexed) entry.$2];
}

/// Offline-first resource list.
///
/// Reads only from SQLite, so it emits immediately with whatever was last
/// synced and never blocks on the network. Rebuilding on
/// [resourceSearchQueryProvider] re-runs the query; a background sync writing to
/// `my_library` pushes a fresh list into the same stream. When
/// [resourceShelfOnlyProvider] is on, the list is scoped to the signed-in
/// user's shelf (`watchResources(shelfUserId:, myLibrary:)`, the `isMyCourseLib`
/// view, private team resources included); otherwise it is the public catalog
/// (`getPublicNotUserPattern`), which excludes resources already on the user's
/// shelf so they are not duplicated between the catalog and My Library.
final resourcesStreamProvider = StreamProvider<List<MyLibraryRow>>((ref) {
  final query = ref.watch(resourceSearchQueryProvider);
  final shelfOnly = ref.watch(resourceShelfOnlyProvider);
  final userId = ref.watch(sessionProvider).valueOrNull?.id;
  return ref
      .watch(resourcesRepositoryProvider)
      .watchResources(query: query, shelfUserId: userId, myLibrary: shelfOnly);
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
