import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/offline_activity_mapper.dart';

/// Port of `repository/ActivitiesRepositoryImpl.kt`'s login-activity subset.
///
/// Owns the read paths the "My Activity" chart depends on and the merge path
/// the sync calls when it pulls `login_activities` documents from CouchDB. The
/// write path (`logLogin`) and the upload path live elsewhere; this repository
/// is the read + sync slice the dashboard chart needs.
class ActivitiesRepository {
  ActivitiesRepository(this._api, this._dao);

  final PlanetApi _api;
  final OfflineActivityDao _dao;

  /// The chart's data source: a live stream of the user's login rows.
  Stream<List<OfflineActivityRow>> loginActivities(String userName) =>
      _dao.watchLoginsByUserName(userName);

  /// Total login count for [userName].
  Future<int> loginCount(String userName) => _dao.loginCount(userName);

  /// Port of `insertLoginActivitiesFromSync`.
  ///
  /// Merges each document by `_id`, falling back to a `(loginTime, userName)`
  /// pair so an offline-authored row that has no `_id` yet is adopted rather
  /// than duplicated. Design documents (`_design/...`) are skipped, matching the
  /// Kotlin's filter.
  Future<int> insertLoginActivitiesFromSync(
    List<Map<String, dynamic>> docs,
  ) async {
    final documents = docs
        .where((d) => !JsonUtils.getString('_id', d).startsWith('_design'))
        .toList();
    if (documents.isEmpty) return 0;

    final ids = documents
        .map((d) => JsonUtils.getString('_id', d))
        .where((id) => id.isNotEmpty)
        .toSet()
        .toList(growable: false);
    final existingById = ids.isEmpty
        ? <String, OfflineActivityRow>{}
        : {for (final r in await _dao.getByCouchIds(ids)) r.couchId ?? '': r};

    final loginTimes = documents
        .map((d) => JsonUtils.getLong('loginTime', d))
        .where((t) => t > 0)
        .toSet()
        .toList(growable: false);
    final userNames = documents
        .map((d) => JsonUtils.getString('user', d))
        .where((u) => u.isNotEmpty)
        .toSet()
        .toList(growable: false);
    final fallbackByKey = (loginTimes.isEmpty || userNames.isEmpty)
        ? <String, OfflineActivityRow>{}
        : {
            for (final r in await _dao.getByLoginTimesAndUserNames(
              loginTimes,
              userNames,
            ))
              '${r.loginTime}_${r.userName}': r,
          };

    final companions = <OfflineActivitiesCompanion>[];
    for (final doc in documents) {
      final docId = JsonUtils.getString('_id', doc);
      final loginTime = JsonUtils.getLong('loginTime', doc);
      final userName = JsonUtils.getString('user', doc);
      final existing = existingById[docId];
      final fallback = fallbackByKey['${loginTime}_$userName'];
      companions.add(
        OfflineActivityMapper.fromDoc(
          doc,
          existing: existing,
          fallback: fallback,
        ),
      );
    }
    await _dao.upsertAll(companions);
    return companions.length;
  }

  /// Port of the `login_activities` pull in
  /// `services/sync/TransactionSyncManager.kt`'s `syncDb`.
  ///
  /// Paginates `_all_docs` with a batch size of 200 (the Kotlin's page size for
  /// this table) and merges each page via [insertLoginActivitiesFromSync].
  /// There is deliberately **no** `deleteNotIn` cleanup — the Kotlin does not
  /// run one for `login_activities`, so a row that drops off the server lingers
  /// locally, and replicating that here keeps the behaviour identical. A
  /// locally-authored login row the server has not echoed back would be
  /// discarded by a cleanup, which is exactly the data the preserved-table rule
  /// exists to protect.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);

    final countResult = await _api.getJsonObject(
      '$dbUrl/login_activities/_all_docs?limit=0',
      authHeader: authHeader,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final totalRows = JsonUtils.getInt('total_rows', countResult.data);
    if (totalRows == 0) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: 200);
    var skip = 0;
    var totalSaved = 0;

    while (skip < totalRows) {
      final size = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();
      final pageResult = await _api.getJsonObject(
        '$dbUrl/login_activities/_all_docs?include_docs=true&limit=$size&skip=$skip',
        authHeader: authHeader,
      );
      stopwatch.stop();

      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        return SyncFailed(describeNetworkFailure(pageResult));
      }
      batchSizer.recordSuccess(stopwatch.elapsedMilliseconds);

      final rows = pageResult.data['rows'];
      if (rows is! List || rows.isEmpty) break;

      final docs = <Map<String, dynamic>>[
        for (final row in rows)
          if (row is Map<String, dynamic>)
            JsonUtils.getObject('doc', row) ?? const <String, dynamic>{},
      ];
      totalSaved += await insertLoginActivitiesFromSync(docs);

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
      if (rows.length < size) break;
    }
    return SyncComplete(totalSaved);
  }
}
