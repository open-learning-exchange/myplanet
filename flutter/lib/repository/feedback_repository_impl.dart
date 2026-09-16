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

  /// Closes the thread **and queues the close for upload** — a deliberate
  /// divergence, not an oversight to be tidied away.
  ///
  /// Kotlin writes the status and stops (`FeedbackDao.kt:29-30`,
  /// `UPDATE feedback SET status = 'Closed' WHERE id = :id`, and
  /// `FeedbackRepositoryImpl.closeFeedback` calls nothing else). The row keeps
  /// `isUploaded = true`, so the upload sweep — which selects on
  /// `isUploaded = 0` — never sees it: the close never reaches the server, and
  /// the next pull maps the server's still-open document back over the row and
  /// reverts it on the device too. Closing a thread in the Android app is a
  /// gesture that undoes itself.
  ///
  /// Marking the row pending is what makes the close mean something. The
  /// caller queues it (`feedback_detail_screen._closeFeedback`), the outbox
  /// sends the document back under its carried `_rev`, and the thread is
  /// closed for the manager and every other device.
  ///
  /// The two writes are one transaction, and the order inside it still
  /// matters. A row that reads `Closed` while `isUploaded` is still true is
  /// exactly the Kotlin bug above — a close nothing will upload and the next
  /// pull reverts — so it must not be observable and must not survive a crash
  /// between the two statements.
  @override
  Future<void> closeFeedback(String id) async {
    await feedbackDao.transaction(() async {
      await feedbackDao.closeById(id);
      // No existence check: `updateRow` is an `UPDATE … WHERE id` (its
      // `where` clause is `f.id.equals(row.id.value)`), so a missing row is
      // zero rows written rather than a row conjured up.
      await feedbackDao.updateRow(
        FeedbackEntriesCompanion(id: Value(id), isUploaded: const Value(false)),
      );
    });
  }

  /// Appends a reply signed by [user] — **the signed-in user**, which is the
  /// second deliberate divergence in this file.
  ///
  /// Kotlin signs every reply with the thread's *owner*:
  /// `FeedbackDetailActivity.kt:82` passes `feedback?.owner` to
  /// `viewModel.addReply`, so an admin answering ada's question posts a reply
  /// that reads as if ada wrote it — to the manager list, to the web UI, and to
  /// ada. The port's detail screen passes `session.name`
  /// (`feedback_detail_screen.dart:137-141`).
  ///
  /// Nothing downstream depends on the two matching: the reply's `user` is a
  /// display field, `watchByOwner` filters on the row's `owner` column and not
  /// on any reply, and the merge in [FeedbackMapper._mergePendingReplies]
  /// compares a reply's own `message`/`user`/`time` on both sides. So the only
  /// effect of the divergence is that the name on a reply is the name of who
  /// wrote it.
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
    // The id is derived through `FeedbackMapper.idOf` on both sides. A raw
    // `as String?` here threw on a document whose `_id` was not a plain
    // string, and deriving it twice by hand risked the lookup missing the
    // very row whose pending reply the mapper has to keep.
    final ids = docs.map(FeedbackMapper.idOf).toList(growable: false);
    final existingById = {
      for (final row in await feedbackDao.getByIds(
        ids.where((id) => id.isNotEmpty).toList(growable: false),
      ))
        row.id: row,
    };
    final companions = [
      for (var i = 0; i < docs.length; i++)
        FeedbackMapper.fromDoc(docs[i], existingById[ids[i]]),
    ];
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
    // The rows the server already had a document for when the walk began.
    // Everything else is out of the cleanup's reach — see the comment at
    // `deleteNotIn` below.
    final syncedAtStart = {
      for (final row in await feedbackDao.watchAllSorted().first)
        if (row.isUploaded) row.id,
    };
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
        if (doc == null) continue;
        // Read through `idOf`, the derivation the keep set uses: `deleteNotIn`
        // spares what this walk collected, so a document the mapper stores
        // under one key and the filter judges by another is inserted and
        // deleted in the same sync — or skipped and kept.
        final id = FeedbackMapper.idOf(doc);
        // CouchDB's own view documents are not feedback. Kotlin drops them
        // before the insert (`TransactionSyncManager.extractDocs:355-364`,
        // `!getString("_id", doc).startsWith("_design")`); the port spells it
        // the same way in `notifications_repository.dart:241`,
        // `tags_repository.dart:45` and `chat_repository_impl.dart:286`, with
        // **no trailing slash**, which also covers the `_design` id CouchDB
        // itself never writes but a hand-edited database can hold.
        //
        // Without this a `_design/…` document became a row, and since a
        // manager reads `watchAllSorted()` it drew as an "Untitled feedback /
        // Open" thread they could tap — kept for ever, because the same walk
        // put it in the keep set `deleteNotIn` spares. Rows a pre-fix build
        // stored need no separate cleanup: they carry a `_rev`, so the first
        // walk that completes prunes them like any other stale row.
        //
        // A document `idOf` cannot read is dropped for the same reason and is
        // the worse half of it: the row lands under the empty primary key, and
        // if the document also carried no `_rev` it is `isUploaded = false` —
        // so the cleanup spares it for ever, the outbox queues it, and the
        // manager's list shows the same phantom thread by the adjacent door.
        // Kotlin stores such a row (`""` is not a `_design` prefix); the two
        // siblings cited above skip it, and so does this. Unreachable from
        // `_all_docs`, where every row carries an `_id`.
        if (id.isEmpty || id.startsWith('_design')) continue;
        docs.add(doc);
        savedIds.add(id);
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

    // The prune Kotlin does not have, kept deliberately, on three conditions.
    //
    // `FeedbackDao.kt` carries no delete of any kind and the Kotlin walk only
    // inserts (`TransactionSyncManager.kt:224-226`), so a thread deleted on
    // the server lives on an Android handset for ever. That is an omission
    // rather than a decision, and the rows this cleanup can reach really are
    // a cache of the `feedback` database — so the port prunes, but only where
    // all three of these hold:
    //
    // 1. **Only after a walk that read every page.** A failed page returns
    //    `SyncFailed` above and a page that comes back empty leaves
    //    `walkedEveryPage` false, so a short walk cannot reach this line.
    //    That is the Phase 52 shape: a `deleteNotIn` over a keep set the walk
    //    never finished filling is how a sync deletes what it merely failed to
    //    read.
    //    Same reason the empty keep set below is not read as "the server has
    //    no feedback": a walk that collected nothing has more likely gone
    //    wrong than found an empty database. Since the `_design` filter
    //    landed that guard is no longer hypothetical: a `feedback` database
    //    holding only view documents now yields an empty keep set, where it
    //    used to yield one full of design ids.
    // 2. **Only rows whose every column a pull can rewrite.** This is *not*
    //    true of the table — `isUploaded` is written here and nowhere else,
    //    which is why `feedback` is in `localAuthorityTables`
    //    (`app_database.dart:164-168`) — but it is true of the rows
    //    `deleteNotIn` can reach, since it deletes only `isUploaded = true`
    //    ones and every other column on those is derived from the document.
    //    So a row wrongly removed here is restored whole by the next walk.
    // 3. **Never a row whose document the walk could not have seen.**
    //    `deleteNotIn` spares `isUploaded = false`, which covers all three of
    //    the port's local writes — a thread filed offline, a reply, and a
    //    close. But it spares them as they are *when the cleanup runs*, and
    //    the outbox drains on its own schedule, unserialised against this
    //    walk. A thread that was pending when the pages were read, or that
    //    did not exist yet, can be `isUploaded = true` by the time the
    //    cleanup runs, against pages fetched from a server that had no
    //    document for it — collected by nothing, and deleted. That is the
    //    thread the user filed a minute ago vanishing from their own list.
    //    Hence `syncedAtStart`: the walk may only delete what the server
    //    already had when it began, so everything the device has authored or
    //    uploaded since is out of reach by construction.
    if (walkedEveryPage && savedIds.isNotEmpty) {
      final keep = {...savedIds};
      for (final row in await feedbackDao.watchAllSorted().first) {
        if (!syncedAtStart.contains(row.id)) keep.add(row.id);
      }
      await feedbackDao.deleteNotIn(keep.toList(growable: false));
    }

    return SyncComplete(savedIds.length);
  }
}
