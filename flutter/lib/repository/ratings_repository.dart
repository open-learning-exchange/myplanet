import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart' show visibleForTesting;

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
    this._dao,
    this._userDao, {
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

  /// Only for the sync-in: a rating document names its rater by CouchDB id,
  /// while every reader of this table passes `session.user.id`. See
  /// [_localUserIds].
  final UserDao _userDao;
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
    final rater = await _userDao.getById(userId);
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
        // `setRatingData` writes `createdOn = resolvedUser.parentCode` on the
        // line above `parentCode = resolvedUser.parentCode`
        // (`RatingsRepositoryImpl.kt:159-160`) — one object, so the two columns
        // are equal by construction. They must therefore share a source here
        // too: reading `createdOn` from the `users` row while `parentCode`
        // comes from the caller's session breaks the invariant whenever the
        // two disagree, which the `tablet_users` walk can arrange by rewriting
        // the stored row under a session that is never re-read. It is not a
        // timestamp despite the name.
        createdOn: Value(parentCode),
        // `user = gson.toJson(resolvedUser.serialize())` — the rater as they
        // were at the moment they rated, snapshotted rather than re-derived
        // at upload time, which is what the Kotlin does and what
        // [RatingsUploader] now reads.
        user: Value(jsonEncode(raterDocument(rater, parentCode, planetCode))),
        type: type,
        couchId: Value(existing?.couchId),
        rev: Value(existing?.rev),
      ),
    );
  }

  /// The rater object a ratings document carries.
  ///
  /// **Deliberately narrower than `UserEntity.serialize()`**, which also
  /// writes `derived_key`, `salt` and `password_scheme` into every document it
  /// builds — so a Kotlin ratings document publishes the rater's password
  /// verifier to a database any member of the planet can read. Planet groups
  /// and attributes ratings by `_id` and `name`; none of the credential fields
  /// is read back by either app. The same judgement `MyLibraryMapper` makes
  /// about the satellite PIN in a resource URL.
  /// The codes come from the caller rather than the row, for the same
  /// single-source reason `createdOn` does: the Kotlin builds all four values
  /// from one `resolvedUser`, so a document whose `user.parentCode` disagrees
  /// with its own top-level `parentCode` is a shape neither app should
  /// produce. The row supplies only the identity the caller does not carry.
  static Map<String, dynamic> raterDocument(
    UserRow? user,
    String? parentCode,
    String? planetCode,
  ) => {
    if (user?.couchId != null) '_id': user!.couchId,
    'name': user?.name,
    'planetCode': planetCode ?? user?.planetCode,
    'parentCode': parentCode ?? user?.parentCode,
  };

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
  /// The document's `createdOn` and its embedded `user` object are stored
  /// as of schema v46, the way `RatingsRepositoryImpl.kt:114-118` stores them
  /// — `user` as a JSON string, reduced by [storableRater] first.
  ///
  /// Nothing in either app reads these two back for a *synced* row — the only
  /// reader is `Rating.serializeRating`, which runs on `isUpdated = 1` rows
  /// whose values `setRatingData` has just overwritten. They are stored so
  /// that the row is a faithful copy of the document, and so the two writers
  /// of the column agree; the user-visible half of this change is the
  /// **upload**, which now sends the stored rater rather than rebuilding one.
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
    final localUserIds = await _localUserIds(docs);

    final companions = <RatingsCompanion>[];
    final identityPatches = <(String, String, String?)>[];
    for (final doc in docs) {
      final couchId = JsonUtils.getString('_id', doc);
      if (couchId.isEmpty) continue;
      final user = JsonUtils.getObject('user', doc) ?? const {};
      final raterId = JsonUtils.getString('_id', user);
      final userId = localUserIds[raterId] ?? raterId;
      final type = JsonUtils.getString('type', doc);
      final item = JsonUtils.getString('item', doc);

      final ratingKey = _ratingKey(type, item, userId);
      final existing = byId[couchId] ?? unsyncedByRatingKey[ratingKey];
      // Consumed, so a second document for the same (type, item, user) does
      // not collapse onto the same row: it takes the CouchDB id as its key and
      // both survive, which is what the Kotlin stores and what the average
      // has to count.
      if (byId[couchId] == null) unsyncedByRatingKey.remove(ratingKey);
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
          createdOn: Value(JsonUtils.getStringOrNull('createdOn', doc)),
          user: Value(jsonEncode(storableRater(user))),
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

  /// The rater object as it is stored on a synced row.
  ///
  /// `RatingsRepositoryImpl.kt:98-102` removes `_attachments` before
  /// persisting, because the rater's base64 profile photo rides along inside
  /// the rating document; the Kotlin's stated motive is SQLite's ~2MB
  /// `CursorWindow`, and here it would simply be re-encoded into every rating
  /// row.
  ///
  /// The credential fields go with it, which the Kotlin does **not** do.
  /// `UserEntity.serialize()` writes `derived_key`, `salt` and
  /// `password_scheme` into every document it builds (`UserEntity.kt:66-70`),
  /// and a plaintext `password` for a member registered offline (`:61-65`), so
  /// a `ratings` document carries the rater's password verifier. Storing it
  /// verbatim would keep a copy for every rater on the planet in this device's
  /// database file — one per rating row — for a value nothing reads back: the
  /// only reader of this column is the upload, and a synced row is
  /// `isUpdated = false` so it never reaches one, while a row the user
  /// re-rates has its `user` overwritten by [submit] first.
  ///
  /// This is the same judgement [raterDocument] makes about what the port
  /// *sends*, applied to what it *stores*. Making one and not the other was
  /// the inconsistency a review pass caught: a threat model that distinguishes
  /// a document from a database has to say why, and there is no why here.
  @visibleForTesting
  static Map<String, dynamic> storableRater(Map<String, dynamic> user) =>
      {...user}
        ..remove('_attachments')
        ..remove('derived_key')
        ..remove('salt')
        ..remove('password_scheme')
        ..remove('password');

  /// Maps each rater's CouchDB id to the local `users` row id, for the raters
  /// this page names.
  ///
  /// `insertRatingsFromSync` reads `userId` out of the embedded `user` object,
  /// which is a CouchDB id; `submitRating` writes `user.id`, the local row id,
  /// and every reader passes `session.user.id`. The two are the same string
  /// for an account that first appeared server-side and **different** for a
  /// member registered on this device, whose row keeps a locally-minted id
  /// until its upload lands. Storing the raw id for such a member hides their
  /// own rating from `userRating` and defeats the `(type, item, userId)` match
  /// that stops the walk duplicating it — the same identity rule the shelf
  /// walk follows.
  Future<Map<String, String>> _localUserIds(
    List<Map<String, dynamic>> docs,
  ) async {
    final raterIds = <String>{
      for (final doc in docs)
        if (JsonUtils.getObject('user', doc) case final user?)
          if (JsonUtils.getString('_id', user) case final id when id.isNotEmpty)
            id,
    };
    if (raterIds.isEmpty) return const {};
    final rows = await _userDao.getByAnyIds(raterIds.toList(growable: false));
    return {
      for (final row in rows)
        if (row.couchId case final couchId?)
          if (raterIds.contains(couchId)) couchId: row.id,
      for (final row in rows)
        if (raterIds.contains(row.id)) row.id: row.id,
    };
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
