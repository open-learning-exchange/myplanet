import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';

/// The collection tree for one `dbType`: the named parent tags plus the
/// child tags each parent carries, keyed by the parent's id.
class TagTree {
  const TagTree({required this.parents, required this.children});

  final List<Tag> parents;
  final Map<String, List<Tag>> children;
}

/// Port of `repository/TagsRepositoryImpl.kt` plus the `tags` pull of
/// `services/sync/TransactionSyncManager.kt`.
///
/// The `tags` CouchDB database is a pure cache — the app never writes tags —
/// so [sync] refills with upserts and prunes with `deleteNotIn`, like the
/// resources pull.
class TagsRepository {
  TagsRepository(this._api, this._dao);

  static const int initialBatchSize = 100;

  static const String resourcesDb = 'resources';
  static const String coursesDb = 'courses';

  final PlanetApi _api;
  final TagDao _dao;

  /// Port of `TagsRepositoryImpl.insert`: skips `_design` documents and
  /// upserts the rest. `attachedTo` arrives as either a string or an array
  /// (`createUnmanagedTag` normalises both into the list the row stores).
  Future<void> insertDocs(List<Map<String, dynamic>> docs) {
    final rows = <TagsCompanion>[];
    for (final doc in docs) {
      final id = JsonUtils.getString('_id', doc);
      if (id.isEmpty || id.startsWith('_design')) continue;
      final attachedTo = _readAttachedTo(doc);
      rows.add(
        TagsCompanion(
          id: Value(id),
          couchId: Value(id),
          rev: Value(JsonUtils.getString('_rev', doc)),
          name: Value(JsonUtils.getString('name', doc)),
          linkId: Value(JsonUtils.getString('linkId', doc)),
          tagId: Value(JsonUtils.getString('tagId', doc)),
          attachedTo: Value(attachedTo),
          docType: Value(JsonUtils.getString('docType', doc)),
          db: Value(JsonUtils.getString('db', doc)),
          isAttached: Value(attachedTo.isNotEmpty),
        ),
      );
    }
    if (rows.isEmpty) return Future.value();
    return _dao.upsertAll(rows);
  }

  /// Port of `createUnmanagedTag`'s `attachedTo` read: a JSON array becomes
  /// the string list; a bare string becomes a one-element list.
  List<String> _readAttachedTo(Map<String, dynamic> doc) {
    final raw = doc['attachedTo'];
    if (raw is List) {
      return raw.map((e) => e.toString()).toList(growable: false);
    }
    if (raw is String && raw.isNotEmpty) return [raw];
    return const [];
  }

  /// Port of `TagsRepositoryImpl.getTagsWithChildren`: the named parents for
  /// [dbType], each with the tags whose `attachedTo` points at it.
  Future<TagTree> getTagsWithChildren(String? dbType) async {
    final parents = await _dao.parentTags(dbType);
    final all = await _dao.allTags();

    final childMap = <String, List<Tag>>{};
    for (final tag in all) {
      for (final parentId in tag.attachedTo) {
        final list = childMap.putIfAbsent(parentId, () => []);
        if (list.isEmpty || !identical(list.last, tag)) {
          list.add(tag);
        }
      }
    }

    return TagTree(
      parents: parents,
      children: {
        for (final parent in parents) parent.id: childMap[parent.id] ?? [],
      },
    );
  }

  /// Port of `TagsRepositoryImpl.getTagsForResources`: the named tags
  /// attached to each resource id, keyed by resource id.
  Future<Map<String, List<Tag>>> getTagsForResources(List<String> ids) =>
      _linkedTags(resourcesDb, ids);

  /// Port of `TagsRepositoryImpl.getTagsForCourses`.
  Future<Map<String, List<Tag>>> getTagsForCourses(List<String> ids) =>
      _linkedTags(coursesDb, ids);

  /// Port of `TagsRepositoryImpl.getLinkedTagsBulk`: link rows join a
  /// resource/course id to a tag id; the map carries each link's *named* tag
  /// exactly once.
  Future<Map<String, List<Tag>>> _linkedTags(
    String db,
    List<String> linkIds,
  ) async {
    if (linkIds.isEmpty) return const {};
    final links = await _dao.byDbAndLinkIds(db, linkIds);
    if (links.isEmpty) return const {};

    final tagIds = links.map((l) => l.tagId).where((id) => id.isNotEmpty);
    final tagsById = {
      for (final t in await _dao.byIds(tagIds.toList())) t.id: t,
    };

    final result = <String, List<Tag>>{};
    final seenByLinkId = <String, Set<String>>{};
    for (final link in links) {
      if (link.linkId.isEmpty || link.tagId.isEmpty) continue;
      final parentTag = tagsById[link.tagId];
      if (parentTag == null) continue;
      final seen = seenByLinkId.putIfAbsent(link.linkId, () => {});
      if (seen.add(parentTag.id)) {
        result.putIfAbsent(link.linkId, () => []).add(parentTag);
      }
    }
    return result;
  }

  /// Port of `TagsRepositoryImpl.getLinkIdsForTagNames`: the resource/course
  /// ids carrying any of the named tags. An empty name list is the caller's
  /// "no tag filter" case and returns empty; an unknown name yields nothing,
  /// which the Kotlin's `filterCourses` turns into an empty result.
  Future<List<String>> getLinkIdsForTagNames(
    String db,
    List<String> tagNames,
  ) async {
    if (tagNames.isEmpty) return const [];
    final matching = (await _dao.byNames(tagNames)).map((t) => t.id).toList();
    if (matching.isEmpty) return const [];
    return (await _dao.byDbAndTagIds(
      db,
      matching,
    )).map((t) => t.linkId).where((id) => id.isNotEmpty).toList();
  }

  /// Port of the `tags` branch of `TransactionSyncManager.syncTable`: a
  /// paginated `_all_docs` walk of the `tags` database, upserting each page
  /// and pruning rows the server no longer lists once the walk completes.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);

    final countResult = await _api.getJsonObject(
      '$dbUrl/tags/_all_docs?limit=0',
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
    var walkedEveryPage = true;
    // A failed batch leaves a gap in `savedIds`, so the cleanup must not run
    // either — the id list is incomplete and would delete valid rows.
    // Mirrors the resources walk's `hadBatchFailure` guard.
    var hadBatchFailure = false;

    while (skip < totalRows) {
      final batchSize = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();

      final pageResult = await _api.getJsonObject(
        '$dbUrl/tags/_all_docs?include_docs=true&limit=$batchSize&skip=$skip',
        authHeader: authHeader,
      );
      stopwatch.stop();

      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        hadBatchFailure = true;
        skip += batchSize;
        continue;
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
        final id = JsonUtils.getString('_id', doc);
        if (id.isEmpty || id.startsWith('_design')) continue;
        docs.add(doc);
        savedIds.add(id);
      }

      if (docs.isNotEmpty) {
        await insertDocs(docs);
      }

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
    }

    if (walkedEveryPage && savedIds.isNotEmpty && !hadBatchFailure) {
      await _dao.deleteNotIn(savedIds);
    }

    return SyncComplete(savedIds.length);
  }
}
