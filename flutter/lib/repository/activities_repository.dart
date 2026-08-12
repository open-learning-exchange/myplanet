import 'package:drift/drift.dart';

import '../data/local/app_database.dart';

/// Port of the login/logout half of `repository/ActivitiesRepositoryImpl.kt`,
/// plus the two reads `UserRepositoryImpl.getDashboardProfile` and
/// `ActivitiesFragment` make of it.
///
/// The Kotlin repository also logs resource opens, ratings and course activity
/// and hands all of it to `UploadManager`'s `activities` upload. Only the
/// `login` rows are ported here, because only they have a reader on the
/// dashboard — and there is no activities uploader in the port yet, so these
/// rows stay on the device. That makes `offline_activity` a preserved table;
/// see `AppDatabase.localAuthorityTables`.
class ActivitiesRepository {
  ActivitiesRepository(this._dao);

  final OfflineActivityDao _dao;

  /// `UserSessionManager.KEY_LOGIN`.
  static const String loginType = 'login';

  /// The Kotlin's literal description, stored on every login row.
  static const String loginDescription = 'Member login on offline application';

  /// Port of `ActivitiesRepositoryImpl.logLogin`, called from
  /// `UserSessionManager.onLoginAsync` after a successful sign-in.
  ///
  /// [id] is the locally-minted key; the Kotlin mints a UUID internally, the
  /// port takes one from the caller so tests can predict the row.
  ///
  /// `_id`/`_rev` are left null deliberately — the Kotlin sets them to null
  /// explicitly, and they are what an uploader would fill in later.
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

  /// Port of `getOfflineLogins(userName)`, the stream `ActivitiesFragment`
  /// collects.
  Stream<List<OfflineActivityRow>> watchOfflineLogins(String userName) =>
      _dao.watchByUserNameAndType(userName, loginType);
}
