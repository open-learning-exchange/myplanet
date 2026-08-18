import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/system/disk_stats.dart';
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
