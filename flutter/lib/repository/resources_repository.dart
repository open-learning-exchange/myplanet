import 'dart:io';
import 'dart:math';

import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/files/resource_files.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/text_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/my_library_mapper.dart';
import '../data/local/offline_resource_item.dart';
import 'local_resource_request.dart';
import 'shelf_repository.dart';

/// Port of the resources phase of `services/sync/SyncManager.kt` (phase 2) plus
/// the read side of `repository/ResourcesRepositoryImpl.kt`.
///
/// Offline-first, exactly as the Kotlin is: [watchResources] streams straight
/// out of SQLite and never touches the network, so the list renders with no
/// connectivity. [sync] refills that table in the background, and Drift pushes
/// the updated rows into the open stream — which is what the Kotlin needed
/// `RealtimeSyncManager`'s `SharedFlow` to do by hand.
class ResourcesRepository {
  ResourcesRepository(this._api, this._dao, this._removedLogDao);

  /// The Kotlin seeds its `AdaptiveBatchProcessor` with the same page size.
  static const int initialBatchSize = 100;

  final PlanetApi _api;
  final MyLibraryDao _dao;
  final RemovedLogDao _removedLogDao;

  /// Reactive, offline-first resource list. Pass [shelfUserId] to scope the
  /// stream: in [myLibrary] mode it is the user's shelf (`isMyCourseLib`),
  /// private team resources included; in catalog mode it is the public list
  /// minus the signed-in user's shelf items. Text matching uses
  /// [searchResources] (a port of `ResourcesSearchUtils`), not a SQL `LIKE`,
  /// so prefix matches rank ahead of contains-all-words matches and the query
  /// is split on spaces — the same ranking the Kotlin applies in memory.
  Stream<List<MyLibraryRow>> watchResources({
    String? query,
    String? shelfUserId,
    bool myLibrary = false,
  }) => _dao
      .watchResources(shelfUserId: shelfUserId, myLibrary: myLibrary)
      .map((items) => searchResources(items, query ?? ''));

  Future<int> localCount() => _dao.count();

  /// Gets a single resource by its local id.
  Future<MyLibraryRow?> getById(String id) => _dao.getById(id);

  /// Port of `ResourcesRepositoryImpl.resourceTitleExists` — a duplicate-title
  /// guard the add-resource form runs as the user types.
  Future<bool> resourceTitleExists(String title) {
    final normalized = MyLibraryMapper.normalizeTitle(title);
    if (normalized.isEmpty) return Future.value(false);
    return _dao.countByTitle(normalized);
  }

  /// Port of `ResourcesRepositoryImpl.saveLocalResource`. Creates a new
  /// `my_library` row from the form fields and a picked file path, marks it
  /// offline-available, and adds it to the user's shelf unless it is a
  /// private team resource. Returns `null` on success or an error message.
  Future<String?> saveLocalResource(LocalResourceRequest request) async {
    final title = request.title?.trim() ?? '';
    if (title.isEmpty) return 'Title is missing';
    if (await resourceTitleExists(title)) {
      return 'Resource title already exists';
    }
    final id = _randomResourceId();
    final now = DateTime.now().millisecondsSinceEpoch;
    final companion = MyLibraryTableCompanion.insert(
      id: id,
      resourceId: Value(id),
      title: Value(title),
      titleNormal: Value(MyLibraryMapper.normalizeTitle(title)),
      addedBy: Value(request.addedBy),
      author: Value(request.author),
      year: Value(request.year),
      description: Value(request.description),
      publisher: Value(request.publisher),
      linkToLicense: Value(request.linkToLicense),
      openWith: Value(request.openWith),
      language: Value(request.language),
      mediaType: Value(request.mediaType),
      resourceType: Value(request.resourceType),
      subject: Value(request.subjects ?? const []),
      level: Value(request.levels ?? const []),
      resourceFor: Value(request.resourceFor ?? const []),
      createdDate: Value(now),
      resourceLocalAddress: Value(request.resourceUrl),
      resourceOffline: const Value(true),
      filename: Value(request.resourceUrl?.split('/').last),
      isPrivate: Value(request.isPrivateTeamResource),
      privateFor: Value(request.isPrivateTeamResource ? request.teamId : null),
      // `MyLibrary.setUserId` returns early on a null or blank id, so the
      // shelf list stays empty. A `[""]` entry is not a harmless placeholder:
      // it fails the My Library predicate and passes the catalog one, putting
      // the row everywhere except the library of the user who made it.
      userId: Value(
        request.isPrivateTeamResource || (request.userId ?? '').isEmpty
            ? const []
            : [request.userId!],
      ),
    );
    await _dao.upsertAll([companion]);
    return null;
  }

  /// Port of `ResourcesRepositoryImpl.updateLocalResource` — edits the
  /// metadata of an existing row. Returns `null` on success or an error
  /// message.
  Future<String?> updateLocalResource({
    required String resourceId,
    required String title,
    String? author,
    String? year,
    String? description,
    String? publisher,
    String? linkToLicense,
    List<String>? subjects,
    List<String>? levels,
  }) async {
    final row = await _dao.getById(resourceId);
    if (row == null) return 'Resource not found';
    await _dao.upsertAll([
      row
          .toCompanion(false)
          .copyWith(
            title: Value(title),
            titleNormal: Value(MyLibraryMapper.normalizeTitle(title)),
            author: Value(author),
            year: Value(year),
            description: Value(description),
            publisher: Value(publisher),
            linkToLicense: Value(linkToLicense),
            subject: Value(subjects ?? const []),
            level: Value(levels ?? const []),
          ),
    ]);
    return null;
  }

  /// Port of `ResourcesRepositoryImpl.getAllLibraries` — the achievement
  /// editor's resource picker lists the whole catalog.
  Future<List<MyLibraryRow>> getAllLibraries() => _dao.getAll();

  /// Port of `MyLibrary.serializeResource` — the compact resource document
  /// an achievement entry attaches.
  ///
  /// The Drift table never persisted `needsOptimization` or `sum`, so the
  /// document emits the Kotlin entity defaults (`false`/`0`) for them.
  static Map<String, dynamic> serializeResource(MyLibraryRow row) => {
    '_id': row.couchId,
    '_rev': row.rev,
    'need_optimization': false,
    'resourceFor': row.resourceFor,
    'publisher': row.publisher,
    'linkToLicense': row.linkToLicense,
    'addedBy': row.addedBy,
    'uploadDate': row.uploadDate,
    'openWith': row.openWith,
    'subject': row.subject,
    'kind': row.kind,
    'medium': row.medium,
    'language': row.language,
    'author': row.author,
    'sum': 0,
    'createdDate': row.uploadDate,
    'level': row.level,
    'languages': row.languages,
    'tag': row.tag,
    'timesRated': row.timesRated,
    'year': row.year,
    'title': row.title,
    'averageRating': row.averageRating,
    'filename': row.filename,
    'mediaType': row.mediaType,
    'description': row.description,
    '_attachments': {
      if (row.resourceLocalAddress != null) row.resourceLocalAddress!: {},
    },
  };

  /// Port of `ResourcesRepositoryImpl.getResourceTitlesMap` — maps a resource's
  /// `resourceId` (the on-disk `docId` directory name) to its title, so storage
  /// management can label a downloaded file. Rows without a `resourceId` are
  /// excluded, matching `MyLibraryDao.getWithResourceId`.
  Future<Map<String, String>> getResourceTitlesMap() async {
    final rows = await _dao.getWithResourceId();
    return {
      for (final r in rows)
        if (r.resourceId != null) r.resourceId!: r.title ?? '',
    };
  }

  /// Port of `ResourcesRepositoryImpl.markResourcesAsNotOffline` — clears the
  /// offline flag on library rows whose files were just deleted, so the
  /// resources list stops offering them as available offline before the next
  /// sync re-checks file existence.
  Future<void> markResourcesAsNotOffline(Iterable<String> resourceIds) =>
      _dao.markResourcesNotOffline(resourceIds.toList());

  /// Port of `ResourcesRepositoryImpl.removeResourcesFromShelf` (and the
  /// re-add path of `markResourcesAdded`).
  ///
  /// Leaving must write a `removed_log` row: the shelf upload merges local
  /// ids with the server's, so without the record the next push would simply
  /// re-add the resource — which is the bug the Kotlin fixed by making its
  /// removal insert `RemovedLog` rows. Joining clears any stale record so a
  /// re-add beats an old removal, matching the courses path.
  ///
  /// Idempotent as of `ef80dda52` (#16143): when the row already reflects the
  /// desired membership — e.g. another device synced the same state first —
  /// the write (and the `removed_log` record/clear it would fire) is skipped,
  /// so a no-op remove no longer leaves a spurious `removed_log` entry that
  /// the next shelf push would carry to the server. Phase 80 ported the
  /// toast-on-change the same commit drives; this closes the underlying write.
  Future<void> setShelfMembership(
    String resourceId,
    String userId, {
    required bool joined,
  }) async {
    final row = await _dao.getById(resourceId);
    if (row != null) {
      final contains = row.userId.contains(userId);
      if (joined == contains) return;
    }
    await setShelfMemberships([resourceId], userId, joined: joined);
  }

  /// Applies a catalog multi-selection as one atomic shelf mutation.
  Future<void> setShelfMemberships(
    Iterable<String> resourceIds,
    String userId, {
    required bool joined,
  }) async {
    final ids = resourceIds.where((id) => id.trim().isNotEmpty).toSet();
    if (ids.isEmpty) return;
    // Both writes together: if only one landed, the local shelf and the
    // removal log would disagree and the next upload would push the wrong
    // document.
    await _dao.transaction(() async {
      final rows = await _dao.getByIds(ids.toList(growable: false));
      await _dao.upsertAll([
        for (final row in rows)
          row
              .copyWith(
                userId: {
                  ...row.userId.where((id) => id.isNotEmpty && id != userId),
                  if (joined) userId,
                }.toList(growable: false),
              )
              .toCompanion(true),
      ]);

      for (final resourceId in ids) {
        if (joined) {
          await _removedLogDao.clear(
            type: ShelfRepository.resourcesType,
            userId: userId,
            docId: resourceId,
          );
        } else {
          await _removedLogDao.record(
            type: ShelfRepository.resourcesType,
            userId: userId,
            docId: resourceId,
          );
        }
      }
    });
  }

  /// Port of `SyncManager.syncResources`.
  ///
  /// Counts with `?limit=0`, then walks `?include_docs=true&limit&skip` pages,
  /// upserting each page and finally dropping local rows the server no longer
  /// lists.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);

    final countResult = await _api.getJsonObject(
      '$dbUrl/resources/_all_docs?limit=0',
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
    // A short page means the server changed under us mid-walk. `savedIds` is
    // then only a prefix of what exists, so the cleanup below must not run —
    // it would delete local rows the server still has.
    var walkedEveryPage = true;
    // A failed batch leaves a gap in `savedIds`, so the cleanup must not run
    // either — the id list is incomplete and would delete valid resources.
    // Port of `SyncManager`'s `hadBatchFailure` flag (commit 2ec7e3187).
    var hadBatchFailure = false;

    while (skip < totalRows) {
      final batchSize = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();

      final pageResult = await _api.getJsonObject(
        '$dbUrl/resources/_all_docs?include_docs=true&limit=$batchSize&skip=$skip',
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

      final companions = <MyLibraryTableCompanion>[];
      for (final row in rows) {
        if (row is! Map<String, dynamic>) continue;
        final doc = JsonUtils.getObject('doc', row);
        if (doc == null) continue;

        final companion = MyLibraryMapper.fromDoc(
          doc,
          couchDbUrl: config.couchDbUrl,
        );
        if (companion == null) continue;

        companions.add(companion);
        savedIds.add(companion.id.value);
      }

      if (companions.isNotEmpty) {
        await _dao.upsertAll(companions);
      }

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
    }

    // Port of `ResourcesRepositoryImpl.removeDeletedResources`. Skipped when a
    // batch failed mid-walk — the id list is then incomplete and would delete
    // valid resources (commit 2ec7e3187).
    if (walkedEveryPage && savedIds.isNotEmpty && !hadBatchFailure) {
      await _dao.deleteNotIn(savedIds);
    }

    return SyncComplete(savedIds.length);
  }

  /// Port of `ResourcesRepositoryImpl.getOfflineResourceItems`.
  ///
  /// Walks the `ole` tree, grouping files by their `docId` directory (the
  /// parent folder name, which is the resource id), keeping only those whose
  /// extension matches the requested category. An empty [extensions] matches
  /// every extension *not* in [allKnownExtensions] — the "other" bucket.
  /// Titles come from [getResourceTitlesMap], falling back to [unknownTitle]
  /// exactly as `R.string.storage_unknown_resource` does. Sorted by title.
  Future<List<OfflineResourceItem>> getOfflineResourceItems({
    required Set<String> extensions,
    required Set<String> allKnownExtensions,
    required String unknownTitle,
  }) async {
    final oleDir = await _oleDir;
    if (!await oleDir.exists()) return const [];

    final titleMap = await getResourceTitlesMap();
    final grouped = <String, List<File>>{};

    await for (final entity in oleDir.list(recursive: true)) {
      if (entity is! File) continue;
      final ext = entity.path.split('.').last.toLowerCase();
      final matchesCategory = extensions.isEmpty
          ? !allKnownExtensions.contains(ext)
          : extensions.contains(ext);
      if (!matchesCategory) continue;

      final resourceId = entity.parent.path.split(Platform.pathSeparator).last;
      if (resourceId.isEmpty) continue;
      grouped.putIfAbsent(resourceId, () => []).add(entity);
    }

    return grouped.entries.map((entry) {
      final resourceId = entry.key;
      final files = entry.value;
      final totalSize = files.fold<int>(0, (sum, f) => sum + f.lengthSync());
      final stored = titleMap[resourceId];
      final title = (stored != null && stored.isNotEmpty)
          ? stored
          : unknownTitle;
      return OfflineResourceItem(
        resourceId: resourceId,
        title: title,
        filePaths: files.map((f) => f.path).toList(growable: false),
        totalSizeBytes: totalSize,
      );
    }).toList()..sort((a, b) => a.title.compareTo(b.title));
  }

  /// Port of `ResourcesRepositoryImpl.deleteOfflineResources`.
  ///
  /// Deletes each item's files, removes the `docId` directory when it is left
  /// empty, then clears the offline flag on the matching library rows so the
  /// resources list reflects the deletion before the next sync.
  Future<void> deleteOfflineResources(List<OfflineResourceItem> items) async {
    final oleDir = await _oleDir;
    for (final item in items) {
      for (final path in item.filePaths) {
        final file = File(path);
        if (await file.exists()) await file.delete();
      }
      final parentDir = Directory(
        '${oleDir.path}${Platform.pathSeparator}${item.resourceId}',
      );
      if (await parentDir.exists()) {
        final contents = await parentDir.list().isEmpty;
        if (contents) await parentDir.delete();
      }
    }
    final deletedIds = items.map((i) => i.resourceId).toSet();
    await markResourcesAsNotOffline(deletedIds);
  }

  /// Port of `FreeSpaceWorker.doWork` — the "free up space" pass that clears
  /// every downloaded resource under the `ole` directory and, for each removed
  /// resource-id directory, clears the offline flag on the matching library
  /// rows so the list stops offering them as available offline before the next
  /// sync re-checks file existence.
  ///
  /// The Kotlin walks `ole/`'s direct children, recursing past each; a child
  /// that is itself a directory is a resource id, so its name is what
  /// `markResourcesAsNotOffline` keys on. Files that landed directly in `ole/`
  /// (no enclosing resource dir) are still deleted but contribute no id. The
  /// Kotlin skips the `cv` resume directory; the Flutter port has no resume
  /// feature yet, so there is nothing to spare — but [spareDirectoryNames] is
  /// kept on the signature so a future slice can exclude its store without
  /// touching the call sites.
  ///
  /// Returns the bytes freed and the file count, the two values the Kotlin
  /// surfaces through `WorkInfo.outputData` for the summary snackbar.
  Future<({int deletedFiles, int freedBytes})> freeUpSpace({
    Set<String> spareDirectoryNames = const {},
  }) async {
    final oleDir = await _oleDir;
    if (!await oleDir.exists()) {
      return (deletedFiles: 0, freedBytes: 0);
    }
    var deletedFiles = 0;
    var freedBytes = 0;
    final clearedIds = <String>{};
    await for (final child in oleDir.list()) {
      final name = child.path.split(Platform.pathSeparator).last;
      final isDir = child is Directory;
      if (isDir && spareDirectoryNames.contains(name)) continue;
      final before = deletedFiles;
      final (childFiles, childBytes) = await _deleteRecursive(child);
      deletedFiles += childFiles;
      freedBytes += childBytes;
      if (isDir && (deletedFiles > before || !await child.exists())) {
        clearedIds.add(name);
      }
    }
    if (clearedIds.isNotEmpty) {
      await markResourcesAsNotOffline(clearedIds);
    }
    return (deletedFiles: deletedFiles, freedBytes: freedBytes);
  }

  /// Recursive delete mirroring `FreeSpaceWorker.deleteRecursive`. Returns the
  /// file count and byte count it freed, the values the worker accumulates for
  /// its summary. A missing entity is a no-op, the way the Kotlin's
  /// `!child.exists()` early-out would treat it.
  Future<(int, int)> _deleteRecursive(FileSystemEntity entity) async {
    if (!await entity.exists()) return (0, 0);
    if (entity is File) {
      final size = entity.lengthSync();
      await entity.delete();
      return (1, size);
    }
    if (entity is! Directory) return (0, 0);
    var files = 0;
    var bytes = 0;
    try {
      await for (final child in entity.list()) {
        final (f, b) = await _deleteRecursive(child);
        files += f;
        bytes += b;
      }
    } catch (_) {
      // A vanished child or permission error is best-effort, like the Kotlin.
    }
    try {
      await entity.delete();
    } catch (_) {
      // Leaving an empty directory behind is harmless.
    }
    return (files, bytes);
  }

  /// The `ole` directory resources are downloaded into. Resolved through
  /// `ResourceFiles` so this stays in step with the downloader and viewer,
  /// which are the only writers to that tree.
  Future<Directory> get _oleDir => ResourceFiles.oleDirectory();
}

String _randomResourceId() =>
    '${DateTime.now().microsecondsSinceEpoch}-${Random.secure().nextInt(1 << 32)}';

/// Port of `ResourcesSearchUtils.searchList` / `searchLocalModels`
/// (`49617105e`, refined `1e41d3353`): rank resources whose normalized title
/// *starts with* the whole query ahead of those that merely *contain* every
/// whitespace-separated word.
///
/// The Kotlin loads the full resource list and filters in memory; the port
/// does the same against the diacritic-folded `titleNormal` column. A flat SQL
/// `LIKE '%query%'` cannot split the query into words (so "math basic" would
/// only match the literal contiguous substring) nor rank a prefix match above a
/// substring match, so the matching is done here, in Dart, after the reactive
/// SQL read. Within each bucket the input order — offline-first, then
/// alphabetical — is preserved, matching `getResourceListModels`'s sort.
List<MyLibraryRow> searchResources(List<MyLibraryRow> items, String query) {
  final trimmed = query.trim();
  if (trimmed.isEmpty) return items;

  final normalizedQuery = normalizeText(trimmed);
  final parts = trimmed
      .split(' ')
      .where((s) => s.isNotEmpty)
      .map(normalizeText)
      .toList();

  final startsWith = <MyLibraryRow>[];
  final contains = <MyLibraryRow>[];
  for (final item in items) {
    final title = item.titleNormal ?? normalizeText(item.title ?? '');
    if (title.startsWith(normalizedQuery)) {
      startsWith.add(item);
    } else if (parts.every(title.contains)) {
      contains.add(item);
    }
  }
  return [...startsWith, ...contains];
}
