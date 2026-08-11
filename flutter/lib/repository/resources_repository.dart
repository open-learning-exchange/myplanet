import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/my_library_mapper.dart';
import 'shelf_repository.dart';

/// Port of the resources phase of `services/sync/SyncManager.kt` (phase 2) plus
/// the read side of `repository/ResourcesRepositoryImpl.kt`.
///
/// Offline-first, exactly as the Kotlin is: [watchResources] streams straight
/// out of SQLite and never touches the network, so the list renders with no
/// connectivity. [sync] refills that table in the background, and Drift pushes
/// the updated rows into the open stream — which is what the Kotlin needed
/// `RealtimeSyncManager`'s `SharedFlow` to do by hand.
class ResourcesRepository {
  ResourcesRepository(this._api, this._dao, this._removedLogDao);

  /// The Kotlin seeds its `AdaptiveBatchProcessor` with the same page size.
  static const int initialBatchSize = 100;

  final PlanetApi _api;
  final MyLibraryDao _dao;
  final RemovedLogDao _removedLogDao;

  /// Reactive, offline-first resource list.
  Stream<List<MyLibraryRow>> watchResources({String? query}) =>
      _dao.watchResources(query: query);

  Future<int> localCount() => _dao.count();

  /// Gets a single resource by its local id.
  Future<MyLibraryRow?> getById(String id) => _dao.getById(id);

  /// Port of `ResourcesRepositoryImpl.removeResourcesFromShelf` (and the
  /// re-add path of `markResourcesAdded`).
  ///
  /// Leaving must write a `removed_log` row: the shelf upload merges local
  /// ids with the server's, so without the record the next push would simply
  /// re-add the resource — which is the bug the Kotlin fixed by making its
  /// removal insert `RemovedLog` rows. Joining clears any stale record so a
  /// re-add beats an old removal, matching the courses path.
  Future<void> setShelfMembership(
    String resourceId,
    String userId, {
    required bool joined,
  }) async {
    // Both writes together: if only one landed, the local shelf and the
    // removal log would disagree and the next upload would push the wrong
    // document.
    await _dao.transaction(() async {
      final row = await _dao.getById(resourceId);
      if (row != null) {
        final userIds = {
          ...row.userId.where((id) => id.isNotEmpty && id != userId),
          if (joined) userId,
        }.toList(growable: false);
        await _dao.upsertAll([row.copyWith(userId: userIds).toCompanion(true)]);
      }

      if (joined) {
        await _removedLogDao.clear(
          type: ShelfRepository.resourcesType,
          userId: userId,
          docId: resourceId,
        );
      } else {
        await _removedLogDao.record(
          type: ShelfRepository.resourcesType,
          userId: userId,
          docId: resourceId,
        );
      }
    });
  }

  /// Port of `SyncManager.syncResources`.
  ///
  /// Counts with `?limit=0`, then walks `?include_docs=true&limit&skip` pages,
  /// upserting each page and finally dropping local rows the server no longer
  /// lists.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);

    final countResult = await _api.getJsonObject(
      '$dbUrl/resources/_all_docs?limit=0',
      authHeader: authHeader,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }

    final totalRows = JsonUtils.getInt('total_rows', countResult.data);
    if (totalRows == 0) {
      await _dao.deleteNotIn(const []);
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: initialBatchSize);
    final savedIds = <String>[];
    var skip = 0;
    // A short page means the server changed under us mid-walk. `savedIds` is
    // then only a prefix of what exists, so the cleanup below must not run —
    // it would delete local rows the server still has.
    var walkedEveryPage = true;

    while (skip < totalRows) {
      final batchSize = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();

      final pageResult = await _api.getJsonObject(
        '$dbUrl/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip',
        authHeader: authHeader,
      );
      stopwatch.stop();

      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        return SyncFailed(describeNetworkFailure(pageResult));
      }
      batchSizer.recordSuccess(stopwatch.elapsedMilliseconds);

      final rows = pageResult.data['rows'];
      if (rows is! List || rows.isEmpty) {
        walkedEveryPage = false;
        break;
      }

      final companions = <MyLibraryTableCompanion>[];
      for (final row in rows) {
        if (row is! Map<String, dynamic>) continue;
        final doc = JsonUtils.getObject('doc', row);
        if (doc == null) continue;

        final companion = MyLibraryMapper.fromDoc(
          doc,
          couchDbUrl: config.couchDbUrl,
        );
        if (companion == null) continue;

        companions.add(companion);
        savedIds.add(companion.id.value);
      }

      if (companions.isNotEmpty) {
        await _dao.upsertAll(companions);
      }

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
    }

    // Port of `ResourcesRepositoryImpl.removeDeletedResources`.
    if (walkedEveryPage && savedIds.isNotEmpty) {
      await _dao.deleteNotIn(savedIds);
    }

    return SyncComplete(savedIds.length);
  }
}
