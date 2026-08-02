import 'package:drift/drift.dart';

import '../data/local/app_database.dart';

class RatingSummary {
  const RatingSummary({
    required this.average,
    required this.total,
    this.userRating,
    this.userComment,
  });
  final double average;
  final int total;
  final int? userRating;
  final String? userComment;
}

/// Offline-first portion of `repository/RatingsRepositoryImpl.kt`.
class RatingsRepository {
  RatingsRepository(
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _defaultId;

  final RatingDao _dao;
  final DateTime Function() _now;
  final String Function() _createId;

  Stream<RatingSummary> watchSummary(
    String type,
    String itemId,
    String? userId,
  ) => _dao.watchForItem(type, itemId).map((rows) {
    RatingRow? user;
    if (userId != null) {
      for (final row in rows) {
        if (row.userId == userId) {
          user = row;
          break;
        }
      }
    }
    final total = rows.length;
    final average = total == 0
        ? 0.0
        : rows.fold<int>(0, (sum, row) => sum + row.rate) / total;
    return RatingSummary(
      average: average,
      total: total,
      userRating: user?.rate,
      userComment: user?.comment,
    );
  });

  Future<void> submit({
    required String type,
    required String itemId,
    required String title,
    required String userId,
    required int rate,
    String? comment,
    String? parentCode,
    String? planetCode,
  }) async {
    final existing = await _dao.findUserRating(type, itemId, userId);
    final id = existing?.id ?? _createId();
    await _dao.upsert(
      RatingsCompanion.insert(
        id: id,
        time: _now().millisecondsSinceEpoch,
        title: Value(title),
        userId: userId,
        isUpdated: const Value(true),
        rate: rate.clamp(1, 5) as int,
        item: itemId,
        comment: Value(_nullable(comment)),
        parentCode: Value(parentCode),
        planetCode: Value(planetCode),
        type: type,
        couchId: Value(existing?.couchId),
        rev: Value(existing?.rev),
      ),
    );
  }

  Future<List<RatingRow>> pendingUploads() => _dao.pendingUploads();
  Future<int> markUploaded(String id) => _dao.markUploaded(id);
}

String? _nullable(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

String _defaultId() => DateTime.now().microsecondsSinceEpoch.toString();
