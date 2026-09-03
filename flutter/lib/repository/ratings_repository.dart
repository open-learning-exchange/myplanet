import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
import '../core/sync/table_walk.dart';
import '../core/utils/json_utils.dart';
import '../data/api/planet_api.dart';
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
    this._api,
    this._dao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _defaultId;

  /// `TransactionSyncManager.syncDb` gives `ratings` a page size of 20 — the
  /// smallest of any table, because a rating document embeds the rater's whole
  /// `_users` document. The port keeps the number.
  static const int initialBatchSize = 20;

  final PlanetApi _api;
  final RatingDao _dao;
  final DateTime Function() _now;
  final String Function() _createId;

  Stream<RatingSummary> watchSummary(
    String type,
    String itemId,
    String? userId,
  ) => _dao.watchForItem(type, itemId).map((rows) => _summarize(rows, userId));

  /// One-shot port of `RatingsRepositoryImpl.getRatingSummary` — the
  /// completion-rating flow (`TakeCourseFragment.showCourseRatingDialogAndFinish`)
  /// needs a single answer, not a stream, to decide whether to show the
  /// dialog. Shares the aggregation with [watchSummary] so the two agree.
  Future<RatingSummary> summary(
    String type,
    String itemId,
    String? userId,
  ) async {
    final rows = await _dao.forItem(type, itemId);
    return _summarize(rows, userId);
  }

  RatingSummary _summarize(List<RatingRow> rows, String? userId) {
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
  }

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
        rate: rate.clamp(1, 5),
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

  /// Port of the `"ratings"` arm of `TransactionSyncManager.syncDb`
  /// (`:242-244`), which `HeavyTableSyncWorker` runs after a full sync
  /// (`HeavyTableSyncWorker.ALL_HEAVY_TABLES`).
  ///
  /// The port has no background heavy-table worker, so this runs as an area of
  /// the sync centre instead. Without it the only writer of the table is the
  /// local [submit], whose one caller always passes the signed-in user — so
  /// the read predicate (`type = ? AND item = ?`, deliberately unscoped by
  /// user, because it computes a community average) could only ever see this
  /// device's own rating, and every "average" was a single number the user had
  /// typed themselves.
  ///
  /// **Never prunes.** The Kotlin's walk issues no delete, and a prune here
  /// would destroy the user's own unsent ratings: a rating submitted on this
  /// device is keyed by a locally-minted id that appears in no `_all_docs`
  /// keep set until it has been uploaded *and* pulled back.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) => walkAllDocs(
    api: _api,
    config: config,
    table: 'ratings',
    initialBatchSize: initialBatchSize,
    onProgress: onProgress,
    insert: insertRatingsFromSync,
  );

  /// Port of `RatingsRepositoryImpl.insertRatingsFromSync`
  /// (`RatingsRepositoryImpl.kt:95-134`).
  ///
  /// Two divergences, both deliberate:
  ///
  /// * **A document is reconciled with the local row it is about**, rather than
  ///   always keying the row by the CouchDB `_id` as `Rating().apply { id =
  ///   _id }` does. A rating submitted here keeps its locally-minted id for
  ///   life — `markRatingUploaded` only clears the dirty flag, it never
  ///   records the server id — so the Kotlin ends up holding *two* rows for
  ///   one rating as soon as the walk pulls it back, and `getRatingSummary`
  ///   averages over both. Matching on `(type, item, userId)` for a row that
  ///   has no CouchDB id yet keeps it to one row, and hands that row the
  ///   `_rev` its next upload needs to be a PUT rather than a second POST.
  /// * **A pending local edit wins.** When the matched row is still flagged
  ///   `isUpdated`, only the identity columns are taken from the document; the
  ///   rate, comment and timestamp the user has just entered stay, and so does
  ///   the flag that makes them upload. The Kotlin overwrites them and clears
  ///   the flag, which silently discards a rating made offline — the same
  ///   shape as the read state Phase 98 had to preserve and the reactions
  ///   Phase 74 did.
  ///
  /// The document's `createdOn` and its embedded `user` object are dropped:
  /// neither has a column on this table, and neither is read by any port
  /// screen or by `RatingsUploader`.
  Future<int> insertRatingsFromSync(List<Map<String, dynamic>> docs) async {
    if (docs.isEmpty) return 0;

    final ids = <String>[
      for (final doc in docs)
        if (JsonUtils.getString('_id', doc) case final id when id.isNotEmpty)
          id,
    ];
    final byId = <String, RatingRow>{};
    for (final row in await _dao.getByCouchIds(ids)) {
      byId[row.id] = row;
      final couchId = row.couchId;
      if (couchId != null && couchId.isNotEmpty) byId[couchId] = row;
    }
    final unsyncedByRatingKey = <String, RatingRow>{
      for (final row in await _dao.unsyncedLocalRatings())
        _ratingKey(row.type, row.item, row.userId): row,
    };

    final companions = <RatingsCompanion>[];
    final identityPatches = <(String, String, String?)>[];
    for (final doc in docs) {
      final couchId = JsonUtils.getString('_id', doc);
      if (couchId.isEmpty) continue;
      final user = JsonUtils.getObject('user', doc) ?? const {};
      final userId = JsonUtils.getString('_id', user);
      final type = JsonUtils.getString('type', doc);
      final item = JsonUtils.getString('item', doc);

      final existing =
          byId[couchId] ?? unsyncedByRatingKey[_ratingKey(type, item, userId)];
      final rowId = existing?.id ?? couchId;

      if (existing != null && existing.isUpdated) {
        // An UPDATE, not a partial companion through the upsert: Drift
        // validates an upsert's companion against the *insert* path, so one
        // that omits the required columns is rejected even though the row
        // already exists.
        identityPatches.add((
          rowId,
          couchId,
          JsonUtils.getStringOrNull('_rev', doc),
        ));
        continue;
      }

      companions.add(
        RatingsCompanion(
          id: Value(rowId),
          couchId: Value(couchId),
          rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
          time: Value(JsonUtils.getLong('time', doc)),
          title: Value(JsonUtils.getStringOrNull('title', doc)),
          type: Value(type),
          item: Value(item),
          rate: Value(JsonUtils.getInt('rate', doc)),
          comment: Value(JsonUtils.getStringOrNull('comment', doc)),
          userId: Value(userId),
          parentCode: Value(JsonUtils.getStringOrNull('parentCode', doc)),
          planetCode: Value(JsonUtils.getStringOrNull('planetCode', doc)),
          isUpdated: const Value(false),
        ),
      );
    }

    await _dao.upsertAll(companions);
    for (final (id, couchId, rev) in identityPatches) {
      await _dao.recordServerIdentity(id, couchId, rev);
    }
    return companions.length + identityPatches.length;
  }

  static String _ratingKey(String type, String item, String userId) =>
      '$type\u0000$item\u0000$userId';

  Future<List<RatingRow>> pendingUploads() => _dao.pendingUploads();
  Future<int> markUploaded(String id) => _dao.markUploaded(id);
}

String? _nullable(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

String _defaultId() => DateTime.now().microsecondsSinceEpoch.toString();
