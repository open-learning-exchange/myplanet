import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';

/// Port of `UserRepositoryImpl.uploadShelfData` and the shelf half of
/// `services/UploadToShelfService.kt`.
///
/// A user's "shelf" is a single CouchDB document listing the ids they have
/// added — `courseIds`, `resourceIds`, `meetupIds`. Pushing it is a read,
/// merge, write:
///
/// 1. `GET {db}/shelf/{userId}` for the current document and its `_rev`.
/// 2. Union the local ids with the server's, minus anything in [RemovedLogs].
///    The subtraction is what makes "leave" stick — a plain union would let the
///    server's copy silently re-add it.
/// 3. `PUT` the merged document back with that `_rev`.
///
/// This is the first piece of the write-back path. It is deliberately *derived
/// state*, not a queue: the payload is recomputed from the database each time,
/// so a failed upload needs no retry bookkeeping — the next call simply sends
/// the current truth. Guaranteed background scheduling (the `WorkManager` gap)
/// is still unsolved; see `docs/kotlin-to-flutter-migration.md`.
class ShelfRepository {
  ShelfRepository(
    this._api,
    this._courseDao,
    this._libraryDao,
    this._removedLogDao,
  );

  /// CouchDB table names, used as the `type` in the removed log.
  static const String coursesType = 'courses';
  static const String resourcesType = 'resources';

  final PlanetApi _api;
  final CourseDao _courseDao;
  final MyLibraryDao _libraryDao;
  final RemovedLogDao _removedLogDao;

  /// Pushes [userId]'s shelf to the server.
  ///
  /// [shelfDocId] is the CouchDB `_id` of the user document, which is also the
  /// shelf document id — distinct from the local row id in [userId].
  Future<SyncResult> upload({
    required ServerConfig config,
    required String userId,
    required String shelfDocId,
  }) async {
    if (shelfDocId.isEmpty) {
      return const SyncFailed('User has no CouchDB id; nothing to push');
    }

    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);
    final shelfUrl = '$dbUrl/shelf/$shelfDocId';

    // A 404 is normal for a user who has never had a shelf document.
    final existing = await _api.getJsonObject(shelfUrl, authHeader: authHeader);
    final Map<String, dynamic> serverDoc;
    switch (existing) {
      case NetworkSuccess(:final data):
        serverDoc = data;
      case NetworkError(:final code) when code == 404:
        serverDoc = const {};
      case NetworkError():
      case NetworkException():
        return SyncFailed(describeNetworkFailure(existing));
    }

    final payload = await buildShelfDocument(
      userId: userId,
      shelfDocId: shelfDocId,
      serverDoc: serverDoc,
    );

    final result = await _api.putJsonObject(
      shelfUrl,
      payload,
      authHeader: authHeader,
    );

    return switch (result) {
      NetworkSuccess() => const SyncComplete(1),
      _ => SyncFailed(describeNetworkFailure(result)),
    };
  }

  /// Port of `getShelfData`. Exposed for testing — it is the whole of the merge
  /// logic, and the part worth pinning down.
  ///
  /// `meetupIds` is passed through from the server untouched: meetups are not
  /// ported yet, and dropping the key would delete the user's meetups server-side.
  Future<Map<String, dynamic>> buildShelfDocument({
    required String userId,
    required String shelfDocId,
    required Map<String, dynamic> serverDoc,
  }) async {
    final localCourseIds = await _localCourseIds(userId);
    final localResourceIds = await _localResourceIds(userId);

    final removedCourses = await _removedLogDao.removedDocIds(
      coursesType,
      userId,
    );
    final removedResources = await _removedLogDao.removedDocIds(
      resourcesType,
      userId,
    );

    final doc = <String, dynamic>{
      '_id': shelfDocId,
      'courseIds': mergeIds(
        localCourseIds,
        JsonUtils.getStringList('courseIds', serverDoc),
        removedCourses,
      ),
      'resourceIds': mergeIds(
        localResourceIds,
        JsonUtils.getStringList('resourceIds', serverDoc),
        removedResources,
      ),
      'meetupIds': JsonUtils.getStringList('meetupIds', serverDoc),
    };

    final rev = JsonUtils.getStringOrNull('_rev', serverDoc);
    if (rev != null) doc['_rev'] = rev;

    return doc;
  }

  /// Port of `mergeJsonArray`: everything local, plus anything the server knows
  /// that the user has not explicitly removed.
  ///
  /// Note the asymmetry, which is the Kotlin's and is deliberate — [removed]
  /// filters only the *server* side. A locally-present id always survives, so
  /// re-adding something beats a stale removal record.
  static List<String> mergeIds(
    List<String> local,
    List<String> fromServer,
    List<String> removed,
  ) {
    final merged = <String>[...local];
    for (final id in fromServer) {
      if (!merged.contains(id) && !removed.contains(id)) {
        merged.add(id);
      }
    }
    return merged;
  }

  Future<List<String>> _localCourseIds(String userId) async {
    final courses = await _courseDao.watchCourses(shelfUserId: userId).first;
    return courses
        .map((c) => c.courseId ?? c.id)
        .where((id) => id.isNotEmpty)
        .toList(growable: false);
  }

  Future<List<String>> _localResourceIds(String userId) async {
    final resources = await _libraryDao
        .watchResources(shelfUserId: userId)
        .first;
    return resources
        .map((r) => r.resourceId ?? r.id)
        .where((id) => id.isNotEmpty)
        .toList(growable: false);
  }
}
