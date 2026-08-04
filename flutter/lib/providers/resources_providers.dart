import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'sync_state.dart';

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

class ResourceSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) {
    return ref
        .read(resourcesRepositoryProvider)
        .sync(config: config, onProgress: onProgress);
  }
}

final resourceSyncProvider =
    NotifierProvider<ResourceSyncNotifier, SyncUiState>(
  ResourceSyncNotifier.new,
);
