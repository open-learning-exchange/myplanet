import 'package:drift/drift.dart';

import '../data/local/app_database.dart';
import '../data/local/feedback_mapper.dart';
import 'feedback_repository.dart';

/// Port of `repository/FeedbackRepositoryImpl.kt`.
class FeedbackRepositoryImpl implements FeedbackRepository {
  FeedbackRepositoryImpl({required this.feedbackDao});

  final FeedbackDao feedbackDao;

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
    final companions = docs.map(FeedbackMapper.fromDoc).toList();
    await feedbackDao.upsertAll(companions);
  }

  @override
  Future<void> markUploaded(String id) async {
    await feedbackDao.markUploaded(id);
  }
}
