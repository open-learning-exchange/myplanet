import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../data/local/app_database.dart';

/// Port of `repository/FeedbackRepository.kt` interface.
abstract class FeedbackRepository {
  /// Gets feedback for a user (all if manager, own feedback otherwise).
  Stream<List<FeedbackRow>> getFeedback({
    String? userName,
    bool isManager = false,
  });

  /// Gets pending feedback that hasn't been uploaded.
  Future<List<FeedbackRow>> getPendingFeedback();

  /// Gets a single feedback by id.
  Future<FeedbackRow?> getFeedbackById(String id);

  /// Creates and saves new feedback.
  Future<void> createFeedback({
    required String user,
    required String priority,
    required String type,
    required String message,
    String? item,
    String? state,
  });

  /// Closes feedback by id.
  Future<void> closeFeedback(String id);

  /// Adds a reply to feedback.
  Future<void> addReply(String id, String message, String user);

  /// Saves a feedback entry.
  Future<void> saveFeedback(FeedbackEntriesCompanion feedback);

  /// Inserts feedback from JSON list (for sync).
  Future<void> insertFromJson(List<Map<String, dynamic>> docs);

  /// Marks feedback as uploaded.
  Future<void> markUploaded(String id, String rev);

  /// Syncs feedback from CouchDB.
  ///
  /// Counts with `?limit=0`, then walks pages, upserting each feedback document.
  /// Port of the `feedback` table pull in `TransactionSyncManager.kt`.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  });
}
