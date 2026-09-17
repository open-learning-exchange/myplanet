import '../config/server_config.dart';
import '../network/network_result.dart';
import '../utils/json_utils.dart';
import '../utils/url_utils.dart';
import '../../data/api/planet_api.dart';
import 'adaptive_batch_processor.dart';
import 'sync_result.dart';

/// Shared `_all_docs` pagination for a CouchDB sync-in walk.
///
/// Port of the loop in `TransactionSyncManager.syncDb` (`services/sync/`),
/// which every table pull in the Kotlin runs: fetch a page with
/// `?include_docs=true&limit&skip`, hand its documents to the table's writer,
/// and stop on a short page. The port adds the `?limit=0` count query its
/// other walks already use, so the sync centre can show real progress rather
/// than an indeterminate spinner.
///
/// The Kotlin's `_design` filter lives here rather than in each writer —
/// `extractDocs` applies it to every table, and doing it once means a new walk
/// cannot forget it.
///
/// **No pruning.** A caller that needs `deleteNotIn` needs the keep set, and
/// the keep set is only trustworthy when every page landed; the walks that
/// prune (resources, courses, tags) therefore keep their own loops with their
/// own `hadBatchFailure` guards. Every walk built on this helper is one the
/// Kotlin does not prune either — see `PHASE_119_NOTES.md` for the per-table
/// reasoning.
Future<SyncResult> walkAllDocs({
  required PlanetApi api,
  required ServerConfig config,
  required String table,
  required int initialBatchSize,
  required Future<int> Function(List<Map<String, dynamic>> docs) insert,
  void Function(SyncProgress)? onProgress,
}) async {
  final dbUrl = UrlUtils.dbUrl(config);
  final authHeader = UrlUtils.authHeader(config);

  final countResult = await api.getJsonObject(
    '$dbUrl/$table/_all_docs?limit=0',
    authHeader: authHeader,
  );
  if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
    return SyncFailed(describeNetworkFailure(countResult));
  }

  // A response with no `total_rows` at all is a malformed one, not an empty
  // database. `JsonUtils.getInt` reads both as 0, and treating them alike would
  // put a green tick on a walk that never issued a page request — the Kotlin
  // has no count query and would simply page. `total_rows: 0` stays a
  // legitimate empty answer.
  if (!countResult.data.containsKey('total_rows')) {
    return SyncFailed('$table/_all_docs returned no total_rows');
  }
  final totalRows = JsonUtils.getInt('total_rows', countResult.data);
  if (totalRows == 0) {
    onProgress?.call(const SyncProgress(completed: 0, total: 0));
    return const SyncComplete(0);
  }

  final batchSizer = AdaptiveBatchProcessor(initialSize: initialBatchSize);
  var skip = 0;
  var saved = 0;

  while (skip < totalRows) {
    final size = batchSizer.currentSize;
    final stopwatch = Stopwatch()..start();
    final pageResult = await api.getJsonObject(
      '$dbUrl/$table/_all_docs?include_docs=true&limit=$size&skip=$skip',
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

    saved += await insert(extractDocs(rows));

    skip += rows.length;
    onProgress?.call(
      SyncProgress(
        completed: skip > totalRows ? totalRows : skip,
        total: totalRows,
      ),
    );
    if (rows.length < size) break;
  }

  return SyncComplete(saved);
}

/// Port of `TransactionSyncManager.extractDocs`: the `doc` of every row that
/// has one, minus `_design/*` documents. A row with no `doc` (a deleted or
/// missing key in a `keys` query) is dropped rather than passed on as an empty
/// map, which is what the Kotlin's `getJsonObject` fallback would produce.
List<Map<String, dynamic>> extractDocs(List<dynamic> rows) {
  final docs = <Map<String, dynamic>>[];
  for (final row in rows) {
    if (row is! Map<String, dynamic>) continue;
    final doc = JsonUtils.getObject('doc', row);
    if (doc == null || doc.isEmpty) continue;
    final id = JsonUtils.getString('_id', doc);
    if (id.isEmpty || id.startsWith('_design')) continue;
    docs.add(doc);
  }
  return docs;
}
