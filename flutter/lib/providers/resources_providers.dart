import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/local/app_database.dart';
import '../repository/resources_repository.dart';
import 'app_providers.dart';

/// The resource-list search box. Replaces the `searchTags`/`etSearch` state that
/// `BaseRecyclerFragment` keeps as fields.
final resourceSearchQueryProvider = StateProvider<String>((ref) => '');

/// Offline-first resource list.
///
/// Reads only from SQLite, so it emits immediately with whatever was last
/// synced and never blocks on the network. Rebuilding on
/// [resourceSearchQueryProvider] re-runs the query; a background sync writing to
/// `my_library` pushes a fresh list into the same stream.
final resourcesStreamProvider = StreamProvider<List<MyLibraryRow>>((ref) {
  final query = ref.watch(resourceSearchQueryProvider);
  return ref.watch(resourcesRepositoryProvider).watchResources(query: query);
});

/// State of a manual resources pull, driving the sync button and progress bar.
sealed class ResourceSyncState {
  const ResourceSyncState();
}

class SyncIdle extends ResourceSyncState {
  const SyncIdle();
}

class Syncing extends ResourceSyncState {
  const Syncing(this.progress);

  final SyncProgress progress;
}

class SyncSucceeded extends ResourceSyncState {
  const SyncSucceeded(this.savedCount);

  final int savedCount;
}

class SyncError extends ResourceSyncState {
  const SyncError(this.message);

  final String message;
}

/// Port of the `SyncStatus` StateFlow in `services/sync/SyncManager.kt`.
class ResourceSyncNotifier extends Notifier<ResourceSyncState> {
  @override
  ResourceSyncState build() => const SyncIdle();

  Future<void> sync() async {
    if (state is Syncing) return;

    final config = ref.read(serverConfigProvider);
    if (config == null) return;

    state = const Syncing(SyncProgress(completed: 0, total: 0));

    final result = await ref
        .read(resourcesRepositoryProvider)
        .sync(
          config: config,
          onProgress: (progress) => state = Syncing(progress),
        );

    state = switch (result) {
      SyncComplete(:final savedCount) => SyncSucceeded(savedCount),
      SyncFailed(:final message) => SyncError(message),
    };
  }
}

final resourceSyncProvider =
    NotifierProvider<ResourceSyncNotifier, ResourceSyncState>(
      ResourceSyncNotifier.new,
    );
