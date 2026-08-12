import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/activities_repository.dart';

void main() {
  late AppDatabase database;
  late ActivitiesRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = ActivitiesRepository(database.offlineActivityDao);
  });
  tearDown(() => database.close());

  Future<void> logLogin(String id, String userName, {int loginTime = 1000}) =>
      repository.logLogin(
        id: id,
        userId: 'user-$userName',
        userName: userName,
        parentCode: 'parent',
        planetCode: 'planet',
        loginTime: loginTime,
      );

  test('a login is recorded with the Kotlin type and description', () async {
    await logLogin('login-1', 'ada');

    final row = (await database.offlineActivityDao.latestByType('login'))!;
    expect(row.type, 'login');
    expect(row.description, 'Member login on offline application');
    expect(row.userName, 'ada');
    expect(row.userId, 'user-ada');
    expect(row.parentCode, 'parent');
    // The Kotlin stores the planet code in `createdOn`, despite the name.
    expect(row.createdOn, 'planet');
    expect(row.loginTime, 1000);
    // Left null for an uploader to fill in; the Kotlin nulls them explicitly.
    expect(row.couchId, null);
    expect(row.rev, null);
  });

  test('counts logins for one user, not the whole device', () async {
    await logLogin('login-1', 'ada');
    await logLogin('login-2', 'ada', loginTime: 2000);
    await logLogin('login-3', 'grace', loginTime: 3000);

    expect(await repository.offlineLoginCount('ada'), 2);
    expect(await repository.offlineLoginCount('grace'), 1);
    expect(await repository.offlineLoginCount('unknown'), 0);
  });

  test('the login stream is ordered oldest first', () async {
    await logLogin('login-2', 'ada', loginTime: 2000);
    await logLogin('login-1', 'ada', loginTime: 1000);

    final rows = await repository.watchOfflineLogins('ada').first;
    expect([for (final row in rows) row.loginTime], [1000, 2000]);
  });

  test('a logout stamps the newest login row', () async {
    await logLogin('login-1', 'ada', loginTime: 1000);
    await logLogin('login-2', 'ada', loginTime: 2000);

    await repository.logLogout(5000);

    final rows = await repository.watchOfflineLogins('ada').first;
    final byId = {for (final row in rows) row.id: row};
    expect(byId['login-2']!.logoutTime, 5000);
    expect(byId['login-1']!.logoutTime, null);
  });

  test(
    'a logout attributes to the newest login even from another user',
    () async {
      // Reproduces the Kotlin's `getLatestByType(KEY_LOGIN)`, which ignores the
      // user name it is handed. On a shared handset the logout lands on
      // whichever login is newest — documented on `logLogout`.
      await logLogin('login-ada', 'ada', loginTime: 1000);
      await logLogin('login-grace', 'grace', loginTime: 2000);

      await repository.logLogout(5000);

      final ada = await repository.watchOfflineLogins('ada').first;
      final grace = await repository.watchOfflineLogins('grace').first;
      expect(ada.single.logoutTime, null);
      expect(grace.single.logoutTime, 5000);
    },
  );

  test('a logout with no login recorded does nothing', () async {
    await repository.logLogout(5000);
    expect(await repository.offlineLoginCount('ada'), 0);
  });

  test('rows of other types are not counted as logins', () async {
    await database.offlineActivityDao.insert(
      OfflineActivitiesCompanion.insert(
        id: 'open-1',
        userName: const Value('ada'),
        type: const Value('resource_open'),
        loginTime: const Value(1000),
      ),
    );
    await logLogin('login-1', 'ada');

    expect(await repository.offlineLoginCount('ada'), 1);
    expect(
      (await repository.watchOfflineLogins('ada').first).single.id,
      'login-1',
    );
  });
}
