import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/offline_activity_mapper.dart';
import '../data/local/user_mapper.dart';

/// One resource and how many times it was opened — the pair
/// `getMostOpenedResource` returns.
class MostOpenedResource {
  const MostOpenedResource(this.title, this.count);

  final String title;
  final int count;
}

/// Port of `repository/ActivitiesRepositoryImpl.kt`.
///
/// The device's own activity log: offline sessions (`offline_activity`),
/// resource opens/downloads and completed syncs (`resource_activity`), and
/// course visits (`course_activity`). `ActivitiesUploader` carries all four
/// kinds to the server. Only `login_activities` comes back ([sync]); the
/// resource and course activity databases are write-only from this app's side,
/// so every table here stays preserved across a schema bump (see
/// `AppDatabase.localAuthorityTables`).
///
/// `user_challenge_actions` is ported now (Phase 81): the challenge dialog
/// reads it to check whether the user has completed a `"sync"` action, and
/// `recordSyncUserChallengeAction` writes the row when a full sync finishes.
/// `myplanet_activities` is ported separately (`myplanet_activities_uploader.dart`,
/// Phase 41).
class ActivitiesRepository {
  ActivitiesRepository(
    this._api,
    this._dao,
    this._resourceDao,
    this._courseDao,
    this._challengeActionDao, {
    DateTime Function()? now,
    String Function()? createId,
  }) : _now = now ?? DateTime.now,
       _createId = createId ?? _defaultId;

  final PlanetApi _api;
  final OfflineActivityDao _dao;
  final ResourceActivityDao _resourceDao;
  final CourseActivityDao _courseDao;
  final UserChallengeActionDao _challengeActionDao;
  final DateTime Function() _now;
  final String Function() _createId;

  static String _defaultId() =>
      'challenge-${DateTime.now().microsecondsSinceEpoch}';

  /// `UserSessionManager.KEY_LOGIN`.
  static const String loginType = ActivityTypes.login;

  /// The Kotlin's literal description, stored on every login row.
  static const String loginDescription = 'Member login on offline application';

  /// Port of `ActivitiesRepositoryImpl.logLogin`, called from
  /// `UserSessionManager.onLoginAsync` after a successful sign-in.
  ///
  /// [id] is the locally-minted key; the Kotlin mints a UUID internally, the
  /// port takes one from the caller so tests can predict the row.
  ///
  /// `_id`/`_rev` are left null deliberately — the Kotlin sets them to null
  /// explicitly, and they are what marks the row as still pending upload.
  Future<void> logLogin({
    required String id,
    String? userId,
    String? userName,
    String? parentCode,
    String? planetCode,
    required int loginTime,
  }) => _dao.insert(
    OfflineActivitiesCompanion.insert(
      id: id,
      userId: Value(userId),
      userName: Value(userName),
      parentCode: Value(parentCode),
      // The Kotlin stores the planet code in `createdOn`.
      createdOn: Value(planetCode),
      type: const Value(loginType),
      description: const Value(loginDescription),
      loginTime: Value(loginTime),
    ),
  );

  /// Port of `ActivitiesRepositoryImpl.logLogout`.
  ///
  /// Stamps the most recent `login` row **globally**, not the most recent row
  /// for this user: the Kotlin takes `getLatestByType(KEY_LOGIN)` and ignores
  /// the `userName` it was handed. Reproduced rather than fixed — on a shared
  /// handset where two members sign in and out, both apps attribute the logout
  /// to whichever login is newest. Fixing it here would make the two apps
  /// disagree about the same table.
  Future<void> logLogout(int logoutTime) async {
    final latest = await _dao.latestByType(loginType);
    if (latest == null) return;
    await _dao.updateLogoutTime(latest.id, logoutTime);
  }

  /// Port of `getOfflineLoginCount(userName)` — the "(n)" beside the user's
  /// name on the dashboard. Keyed on the user *name*, as the Kotlin is.
  Future<int> offlineLoginCount(String userName) =>
      _dao.countByUserNameAndType(userName, loginType);

  /// Port of `getOfflineVisitCount(userId)` — the profile's "Total visits" row.
  /// Keyed on the user *id*, which is what makes it a different query from
  /// [offlineLoginCount] rather than a duplicate of it.
  Future<int> offlineVisitCount(String userId) =>
      _dao.countByUserIdAndType(userId, loginType);

  /// Port of `getGlobalLastVisit()`. Deliberately user-agnostic; see the DAO.
  Future<int?> globalLastVisit() => _dao.globalLastVisit();

  /// Port of `getLastVisit(userName)`.
  Future<int?> lastVisit(String userName) => _dao.lastVisit(userName);

  /// Port of `getOfflineLogins(userName)`, the stream `ActivitiesFragment`
  /// collects.
  Stream<List<OfflineActivityRow>> watchOfflineLogins(String userName) =>
      _dao.watchByUserNameAndType(userName, loginType);

  /// Port of `logResourceOpen`, called from `UserSessionManager
  /// .setResourceOpenCount` — `visit` when a resource is opened
  /// (`ResourcesRepositoryImpl.trackResourceOpen`) and `download` when one is
  /// fetched (`BaseContainerFragment`).
  ///
  /// The guest check lives at the caller in Kotlin (`setResourceOpenCount`
  /// returns early for a `guest`-prefixed id) and is repeated here so a caller
  /// that forgets cannot write an unattributable row: the server has no user
  /// document to hang it on either way.
  Future<void> logResourceOpen({
    required String id,
    String? userId,
    String? userName,
    String? parentCode,
    String? planetCode,
    String? title,
    String? resourceId,
    required String type,
    required int time,
  }) async {
    if (_isGuest(userId)) return;
    await _resourceDao.insert(
      ResourceActivitiesCompanion.insert(
        id: id,
        user: Value(userName),
        parentCode: Value(parentCode),
        createdOn: Value(planetCode),
        type: Value(type),
        title: Value(title),
        resourceId: Value(resourceId),
        time: Value(time),
      ),
    );
  }

  /// Port of `recordSyncActivity`, which `SyncManager` calls once per completed
  /// sync. A `sync` row carries no resource: only the user, the codes and the
  /// time, and it is posted to `admin_activities` rather than
  /// `resource_activities`.
  Future<void> recordSyncActivity({
    required String id,
    String? userId,
    String? userName,
    String? parentCode,
    String? planetCode,
    required int time,
  }) async {
    if (_isGuest(userId)) return;
    await _resourceDao.insert(
      ResourceActivitiesCompanion.insert(
        id: id,
        user: Value(userName),
        parentCode: Value(parentCode),
        createdOn: Value(planetCode),
        type: const Value(ActivityTypes.sync),
        time: Value(time),
      ),
    );
  }

  /// Port of `recordSyncUserChallengeAction`, which the dashboard calls right
  /// before the manual-sync flow begins (not on auto-sync). The row is the
  /// challenge dialog's source of truth for whether the user has done a sync:
  /// `hasUserCompletedSync` counts it.
  Future<void> recordSyncUserChallengeAction(String userId) async {
    await _challengeActionDao.insert(
      UserChallengeActionsCompanion.insert(
        id: _createId(),
        userId: Value(userId),
        actionType: const Value('sync'),
        time: Value(_now().millisecondsSinceEpoch),
      ),
    );
  }

  /// Port of `ActivitiesRepositoryImpl.hasUserCompletedSync`. Returns true if
  /// the user has at least one `"sync"` action in `user_challenge_actions`.
  Future<bool> hasUserCompletedSync(String userId) async {
    if (userId.isEmpty) return false;
    final count = await _challengeActionDao.countByUserAndType(userId, 'sync');
    return count > 0;
  }

  /// Port of `logCourseVisit`, called when the take-course view opens a course.
  ///
  /// The Kotlin looks the user up by name to fill `parentCode`/`createdOn` and
  /// leaves both null when there is no such row; the port takes them from the
  /// session the caller already holds, which is the same values without the
  /// extra query.
  Future<void> logCourseVisit({
    required String id,
    String? userId,
    String? userName,
    String? parentCode,
    String? planetCode,
    String? title,
    required String courseId,
    required int time,
  }) async {
    if (_isGuest(userId)) return;
    await _courseDao.insert(
      CourseActivitiesCompanion.insert(
        id: id,
        user: Value(userName),
        parentCode: Value(parentCode),
        createdOn: Value(planetCode),
        // `logCourseVisit` hard-codes `visit`, the same literal a resource open
        // uses.
        type: const Value(ActivityTypes.visit),
        title: Value(title),
        courseId: Value(courseId),
        time: Value(time),
      ),
    );
  }

  /// Port of `getResourceOpenCount(userName, type)`.
  Future<int> resourceOpenCount(String userName, String type) =>
      _resourceDao.countByUserAndType(userName, type);

  /// Port of `getMostOpenedResource(userName, type)` — the profile's "Most
  /// opened resource" row.
  ///
  /// Groups by `resourceId`, takes the title from the first row of each group
  /// and drops groups whose title is null, exactly as the Kotlin does. The
  /// Kotlin then returns `null` when the winning count is zero, which cannot
  /// happen for a non-empty group; the guard is dropped rather than reproduced
  /// because it is unreachable, not because the behaviour differs.
  Future<MostOpenedResource?> mostOpenedResource(
    String userName,
    String type,
  ) async {
    final rows = await _resourceDao.byUserAndType(userName, type);
    if (rows.isEmpty) return null;

    final grouped = <String?, List<ResourceActivityRow>>{};
    for (final row in rows) {
      grouped.putIfAbsent(row.resourceId, () => []).add(row);
    }

    MostOpenedResource? best;
    for (final group in grouped.values) {
      final title = group.first.title;
      if (title == null) continue;
      if (best == null || group.length > best.count) {
        best = MostOpenedResource(title, group.length);
      }
    }
    return best;
  }

  /// Rows awaiting upload, one list per destination database.
  Future<List<OfflineActivityRow>> pendingLoginUploads() =>
      _dao.pendingLoginUploads();

  Future<List<ResourceActivityRow>> pendingResourceUploads() =>
      _resourceDao.pendingUploads();

  Future<List<ResourceActivityRow>> pendingSyncUploads() =>
      _resourceDao.pendingSyncUploads();

  Future<List<CourseActivityRow>> pendingCourseUploads() =>
      _courseDao.pendingUploads();

  Future<int> markLoginUploaded(String localId, String remoteId, String rev) =>
      _dao.markUploaded(localId, remoteId, rev);

  Future<int> markResourceUploaded(
    String localId,
    String remoteId,
    String rev,
  ) => _resourceDao.markUploaded(localId, remoteId, rev);

  Future<int> markCourseUploaded(String localId, String remoteId, String rev) =>
      _courseDao.markUploaded(localId, remoteId, rev);

  /// Pulls the `login_activities` database, the direction this port lacked:
  /// Phase 33 wrote login rows and Phase 34 uploaded them, but nothing brought
  /// back the ones other devices had already sent, so a member's history was
  /// whatever this handset happened to observe. Harvested from
  /// `flutter-openhands4`.
  ///
  /// Deliberately no `deleteNotIn`: this table is preserved and holds rows that
  /// have never been uploaded, so pruning against a synced id set would delete
  /// exactly the logins the server has not seen yet. The Kotlin's
  /// `insertLoginActivitiesFromSync` does not prune either.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);

    final countResult = await _api.getJsonObject(
      '$dbUrl/login_activities/_all_docs?limit=0',
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

    final batchSizer = AdaptiveBatchProcessor(initialSize: 200);
    var skip = 0;
    var totalSaved = 0;

    while (skip < totalRows) {
      final size = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();
      final pageResult = await _api.getJsonObject(
        '$dbUrl/login_activities/_all_docs'
        '?include_docs=true&limit=$size&skip=$skip',
        authHeader: authHeader,
      );
      stopwatch.stop();

      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        // Partial syncs are not rolled back, matching `SyncManager`; earlier
        // pages stay persisted.
        return SyncFailed(describeNetworkFailure(pageResult));
      }
      batchSizer.recordSuccess(stopwatch.elapsedMilliseconds);

      final rows = pageResult.data['rows'];
      if (rows is! List || rows.isEmpty) break;

      final docs = <Map<String, dynamic>>[
        for (final row in rows)
          if (row is Map<String, dynamic>)
            JsonUtils.getObject('doc', row) ?? const <String, dynamic>{},
      ];
      totalSaved += await insertLoginActivitiesFromSync(docs);

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
      if (rows.length < size) break;
    }
    return SyncComplete(totalSaved);
  }

  /// Port of `insertLoginActivitiesFromSync`.
  ///
  /// Merges each document by `_id`, falling back to a `(loginTime, userName)`
  /// pair so a row this device authored offline is adopted rather than
  /// duplicated. Design documents are skipped, matching the Kotlin's filter.
  Future<int> insertLoginActivitiesFromSync(
    List<Map<String, dynamic>> docs,
  ) async {
    final documents = docs
        .where((doc) => !JsonUtils.getString('_id', doc).startsWith('_design'))
        .toList();
    if (documents.isEmpty) return 0;

    final ids = documents
        .map((doc) => JsonUtils.getString('_id', doc))
        .where((id) => id.isNotEmpty)
        .toSet()
        .toList(growable: false);
    final existingById = {
      for (final row in await _dao.getByCouchIds(ids)) row.couchId ?? '': row,
    };

    final loginTimes = documents
        .map((doc) => JsonUtils.getLong('loginTime', doc))
        .where((time) => time > 0)
        .toSet()
        .toList(growable: false);
    final userNames = documents
        .map((doc) => JsonUtils.getString('user', doc))
        .where((name) => name.isNotEmpty)
        .toSet()
        .toList(growable: false);
    final fallbackByKey = <String, OfflineActivityRow>{};
    for (final row in await _dao.getByLoginTimesAndUserNames(
      loginTimes,
      userNames,
    )) {
      // `putIfAbsent`, as the Kotlin does: with two local rows sharing a
      // (time, name) pair the first one wins rather than the last.
      fallbackByKey.putIfAbsent('${row.loginTime}_${row.userName}', () => row);
    }

    final companions = <OfflineActivitiesCompanion>[];
    for (final doc in documents) {
      final docId = JsonUtils.getString('_id', doc);
      final key =
          '${JsonUtils.getLong('loginTime', doc)}_'
          '${JsonUtils.getString('user', doc)}';
      companions.add(
        OfflineActivityMapper.fromDoc(
          doc,
          existing: existingById[docId],
          fallback: fallbackByKey[key],
        ),
      );
    }
    await _dao.upsertAll(companions);
    return companions.length;
  }

  /// `UserSessionManager`'s test: a `guest`-prefixed id, case-sensitively.
  static bool _isGuest(String? userId) => UserMapper.isGuestId(userId);
}
