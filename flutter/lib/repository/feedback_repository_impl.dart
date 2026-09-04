import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/feedback_mapper.dart';
import 'feedback_repository.dart';

/// Port of `repository/FeedbackRepositoryImpl.kt`.
class FeedbackRepositoryImpl implements FeedbackRepository {
  FeedbackRepositoryImpl({required this.feedbackDao, required this.planetApi});

  final FeedbackDao feedbackDao;
  final PlanetApi planetApi;

  /// Feedback documents are smaller, so a standard starting page size works well.
  static const int initialBatchSize = 100;

  @override
  Stream<List<FeedbackRow>> getFeedback({
    String? userName,
    bool isManager = false,
  }) {
    if (isManager) {
      return feedbackDao.watchAllSorted();
    }
    return feedbackDao.watchByOwner(userName);
  }

  @override
  Future<List<FeedbackRow>> getPendingFeedback() {
    return feedbackDao.getPending();
  }

  @override
  Future<FeedbackRow?> getFeedbackById(String id) {
    return feedbackDao.getById(id);
  }

  @override
  Future<void> createFeedback({
    required String user,
    required String priority,
    required String type,
    required String message,
    String? item,
    String? state,
  }) async {
    final feedback = FeedbackMapper.createFeedback(
      user: user,
      priority: priority,
      type: type,
      message: message,
      item: item,
      state: state,
    );
    await feedbackDao.upsert(feedback);
  }

  @override
  Future<void> closeFeedback(String id) async {
    await feedbackDao.closeById(id);
    final existing = await feedbackDao.getById(id);
    if (existing != null) {
      // Mark as needing re-upload after closing
      await feedbackDao.updateRow(
        FeedbackEntriesCompanion(id: Value(id), isUploaded: const Value(false)),
      );
    }
  }

  @override
  Future<void> addReply(String id, String message, String user) async {
    final existing = await feedbackDao.getById(id);
    if (existing == null) return;

    final updatedMessages = FeedbackMapper.addReply(
      existing.messages,
      message,
      user,
    );

    await feedbackDao.updateRow(
      FeedbackEntriesCompanion(
        id: Value(id),
        messages: Value(updatedMessages),
        rev: Value(existing.rev),
        title: Value(existing.title),
        source: Value(existing.source),
        status: Value(existing.status),
        priority: Value(existing.priority),
        owner: Value(existing.owner),
        openTime: Value(existing.openTime),
        type: Value(existing.type),
        url: Value(existing.url),
        parentCode: Value(existing.parentCode),
        isUploaded: Value(false), // Mark as needing re-upload after reply
        item: Value(existing.item),
        state: Value(existing.state),
      ),
    );
  }

  @override
  Future<void> saveFeedback(FeedbackEntriesCompanion feedback) {
    return feedbackDao.upsert(feedback);
  }

  @override
  Future<void> insertFromJson(List<Map<String, dynamic>> docs) async {
    // A row with an unconfirmed local reply must keep its messages through
    // the sync, so the mapper needs the stored row to compare against.
    final ids = docs
        .map((doc) => doc['_id'] as String? ?? doc['id'] as String? ?? '')
        .where((id) => id.isNotEmpty)
        .toList(growable: false);
    final existingById = {
      for (final row in await feedbackDao.getByIds(ids)) row.id: row,
    };
    final companions = docs
        .map(
          (doc) => FeedbackMapper.fromDoc(
            doc,
            existingById[doc['_id'] as String? ?? doc['id'] as String? ?? ''],
          ),
        )
        .toList();
    await feedbackDao.upsertAll(companions);
  }

  @override
  Future<void> markUploaded(String id, String rev) async {
    await feedbackDao.markUploaded(id, rev);
  }

  @override
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);

    final countResult = await planetApi.getJsonObject(
      '$dbUrl/feedback/_all_docs?limit=0',
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

    final batchSizer = AdaptiveBatchProcessor(initialSize: initialBatchSize);
    final savedIds = <String>[];
    var skip = 0;
    var walkedEveryPage = true;

    while (skip < totalRows) {
      final batchSize = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();

      final pageResult = await planetApi.getJsonObject(
        '$dbUrl/feedback/_all_docs?include_docs=true&limit=$batchSize&skip=$skip',
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

      final docs = <Map<String, dynamic>>[];
      for (final row in rows) {
        if (row is! Map<String, dynamic>) continue;
        final doc = JsonUtils.getObject('doc', row);
        if (doc != null) {
          docs.add(doc);
          final id = JsonUtils.getString('_id', doc);
          if (id.isNotEmpty) savedIds.add(id);
        }
      }

      if (docs.isNotEmpty) {
        await insertFromJson(docs);
      }

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
    }

    // Clean up local rows not present on server if we walked all pages.
    if (walkedEveryPage && savedIds.isNotEmpty) {
      await feedbackDao.deleteNotIn(savedIds);
    }

    return SyncComplete(savedIds.length);
  }
}
