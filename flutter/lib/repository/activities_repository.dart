import 'package:drift/drift.dart';

import '../data/local/app_database.dart';

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
/// kinds to the server; nothing syncs them back, so every table here is
/// preserved across a schema bump (see `AppDatabase.localAuthorityTables`).
///
/// Not ported: `myplanet_activities` (device/tablet usage telemetry, which
/// needs a device-info plugin the port does not have) and
/// `user_challenge_actions` (the challenge feature is unported).
class ActivitiesRepository {
  ActivitiesRepository(this._dao, this._resourceDao, this._courseDao);

  final OfflineActivityDao _dao;
  final ResourceActivityDao _resourceDao;
  final CourseActivityDao _courseDao;

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

  /// `UserSessionManager`'s test: a `guest`-prefixed id, case-sensitively.
  static bool _isGuest(String? userId) =>
      userId != null && userId.startsWith('guest');
}
