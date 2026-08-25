import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/activities_repository.dart';

void main() {
  late AppDatabase database;
  late ActivitiesRepository repository;

  setUp(() {
    database = AppDatabase.memory();
    repository = ActivitiesRepository(
      MockPlanetApi(),
      database.offlineActivityDao,
      database.resourceActivityDao,
      database.courseActivityDao,
      database.userChallengeActionDao,
    );
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

  test('a resource open is recorded against the user name', () async {
    await repository.logResourceOpen(
      id: 'visit-1',
      userId: 'user-ada',
      userName: 'ada',
      parentCode: 'parent',
      planetCode: 'planet',
      title: 'Algebra',
      resourceId: 'res-1',
      type: ActivityTypes.visit,
      time: 4000,
    );

    final rows = await database.resourceActivityDao.byUserAndType(
      'ada',
      ActivityTypes.visit,
    );
    final row = rows.single;
    // The column is `user` and it holds the name, not the id — every read keys
    // on it, so storing the id here would make all of them return nothing.
    expect(row.user, 'ada');
    expect(row.title, 'Algebra');
    expect(row.resourceId, 'res-1');
    expect(row.parentCode, 'parent');
    expect(row.createdOn, 'planet');
    expect(row.time, 4000);
    expect(row.rev, null);
  });

  test('a guest opens nothing the server could attribute', () async {
    // `UserSessionManager.setResourceOpenCount` returns early for a
    // guest-prefixed id: the server has no user document to hang the row on.
    await repository.logResourceOpen(
      id: 'visit-1',
      userId: 'guest_ada',
      userName: 'guest_ada',
      type: ActivityTypes.visit,
      time: 4000,
    );
    await repository.logCourseVisit(
      id: 'course-1',
      userId: 'guest_ada',
      userName: 'guest_ada',
      courseId: 'c1',
      time: 4000,
    );
    await repository.recordSyncActivity(
      id: 'sync-1',
      userId: 'guest_ada',
      userName: 'guest_ada',
      time: 4000,
    );

    expect(await repository.pendingResourceUploads(), isEmpty);
    expect(await repository.pendingSyncUploads(), isEmpty);
    expect(await repository.pendingCourseUploads(), isEmpty);
  });

  test('sync rows and visit rows partition the pending lists', () async {
    // The Kotlin's two configs differ only in this predicate, and they post to
    // different databases. A row appearing in both lists would be uploaded
    // twice, to `resource_activities` and `admin_activities`.
    await repository.logResourceOpen(
      id: 'visit-1',
      userId: 'user-ada',
      userName: 'ada',
      type: ActivityTypes.visit,
      time: 1,
    );
    await repository.logResourceOpen(
      id: 'download-1',
      userId: 'user-ada',
      userName: 'ada',
      type: ActivityTypes.download,
      time: 2,
    );
    await repository.recordSyncActivity(
      id: 'sync-1',
      userId: 'user-ada',
      userName: 'ada',
      time: 3,
    );

    expect(
      (await repository.pendingResourceUploads()).map((row) => row.id),
      containsAll(['visit-1', 'download-1']),
    );
    expect(
      (await repository.pendingResourceUploads()).map((row) => row.id),
      isNot(contains('sync-1')),
    );
    expect((await repository.pendingSyncUploads()).single.id, 'sync-1');
  });

  test('an uploaded row stops being pending', () async {
    await repository.logResourceOpen(
      id: 'visit-1',
      userId: 'user-ada',
      userName: 'ada',
      type: ActivityTypes.visit,
      time: 1,
    );

    expect(await repository.markResourceUploaded('visit-1', 'r1', '1-a'), 1);
    expect(await repository.pendingResourceUploads(), isEmpty);
  });

  test('a pending login upload excludes guests and uploaded rows', () async {
    await logLogin('login-1', 'ada');
    await repository.logLogin(
      id: 'login-guest',
      userId: 'guest_bob',
      userName: 'guest_bob',
      loginTime: 2000,
    );
    await logLogin('login-2', 'grace', loginTime: 3000);
    await repository.markLoginUploaded('login-2', 'r1', '1-a');

    final pending = await repository.pendingLoginUploads();
    expect(pending.map((row) => row.id), ['login-1']);
  });

  test(
    'the most opened resource wins on count, ignoring untitled rows',
    () async {
      Future<void> open(String id, String resourceId, String? title) =>
          repository.logResourceOpen(
            id: id,
            userId: 'user-ada',
            userName: 'ada',
            title: title,
            resourceId: resourceId,
            type: ActivityTypes.visit,
            time: 1,
          );

      await open('a1', 'res-1', 'Algebra');
      await open('a2', 'res-1', 'Algebra');
      await open('b1', 'res-2', 'Botany');
      // Three opens, but no title: the Kotlin filters these out rather than
      // showing a blank winner.
      await open('c1', 'res-3', null);
      await open('c2', 'res-3', null);
      await open('c3', 'res-3', null);

      final best = await repository.mostOpenedResource(
        'ada',
        ActivityTypes.visit,
      );
      expect(best?.title, 'Algebra');
      expect(best?.count, 2);
      expect(await repository.resourceOpenCount('ada', ActivityTypes.visit), 6);
      expect(
        await repository.mostOpenedResource('grace', ActivityTypes.visit),
        isNull,
      );
    },
  );

  test('a course visit records the visit type and the course id', () async {
    await repository.logCourseVisit(
      id: 'course-1',
      userId: 'user-ada',
      userName: 'ada',
      parentCode: 'parent',
      planetCode: 'planet',
      title: 'Intro',
      courseId: 'c1',
      time: 7000,
    );

    final row = (await repository.pendingCourseUploads()).single;
    expect(row.user, 'ada');
    // `logCourseVisit` hard-codes `visit`, the same literal a resource open
    // uses.
    expect(row.type, ActivityTypes.visit);
    expect(row.courseId, 'c1');
    expect(row.title, 'Intro');
    expect(row.time, 7000);
  });

  test('visit counts key on the id, login counts on the name', () async {
    // `getOfflineVisitCount(userId)` and `getOfflineLoginCount(userName)` are
    // two different queries over the same rows in the Kotlin; keeping both is
    // deliberate.
    await logLogin('login-1', 'ada');
    await logLogin('login-2', 'ada', loginTime: 2000);

    expect(await repository.offlineVisitCount('user-ada'), 2);
    expect(await repository.offlineVisitCount('ada'), 0);
    expect(await repository.offlineLoginCount('ada'), 2);
  });

  test('the last visit is global, and per user on request', () async {
    await logLogin('login-1', 'ada', loginTime: 1000);
    await logLogin('login-2', 'grace', loginTime: 9000);

    // `getGlobalLastVisit()` has no user predicate — this is what the profile
    // shows as "Last login".
    expect(await repository.globalLastVisit(), 9000);
    expect(await repository.lastVisit('ada'), 1000);
    expect(await repository.lastVisit('nobody'), isNull);
  });

  group('login_activities sync-in', () {
    Map<String, dynamic> doc({
      required String id,
      String rev = '1-a',
      String user = 'ada',
      int loginTime = 1000,
      int logoutTime = 0,
    }) => {
      '_id': id,
      '_rev': rev,
      'type': 'login',
      'user': user,
      'loginTime': loginTime,
      'logoutTime': logoutTime,
      'parentCode': 'nation',
      'createdOn': 'planet-a',
    };

    test('a document with no local counterpart is inserted', () async {
      final saved = await repository.insertLoginActivitiesFromSync([
        doc(id: 'srv-1'),
      ]);

      expect(saved, 1);
      final row = (await repository.watchOfflineLogins('ada').first).single;
      expect(row.id, 'srv-1');
      expect(row.couchId, 'srv-1');
      expect(row.rev, '1-a');
      expect(row.loginTime, 1000);
    });

    test('design documents are skipped', () async {
      expect(
        await repository.insertLoginActivitiesFromSync([
          doc(id: '_design/activities'),
        ]),
        0,
      );
      expect(await repository.offlineLoginCount('ada'), 0);
    });

    test('a re-sync updates in place rather than duplicating', () async {
      await repository.insertLoginActivitiesFromSync([doc(id: 'srv-1')]);
      await repository.insertLoginActivitiesFromSync([
        doc(id: 'srv-1', rev: '2-b', logoutTime: 5000),
      ]);

      final rows = await repository.watchOfflineLogins('ada').first;
      expect(rows, hasLength(1));
      expect(rows.single.rev, '2-b');
      expect(rows.single.logoutTime, 5000);
    });

    test('this device\'s own uploaded login is adopted, not twinned', () async {
      // The (loginTime, userName) fallback: the row was authored here, uploaded,
      // and is now coming back with an `_id` this row does not carry. Without
      // the fallback the chart would count the same session twice.
      await logLogin('local-1', 'ada', loginTime: 1000);

      await repository.insertLoginActivitiesFromSync([doc(id: 'srv-1')]);

      final rows = await repository.watchOfflineLogins('ada').first;
      expect(rows, hasLength(1));
      expect(rows.single.id, 'local-1', reason: 'kept the local row key');
      expect(rows.single.couchId, 'srv-1');
    });

    test('the merge keeps local columns the document does not carry', () async {
      // `login_activities` has no `description`/`userId`, and the companion
      // carries both — so without passing the stored values through, a sync
      // would blank the `userId` that `offlineVisitCount` keys on.
      await logLogin('local-1', 'ada', loginTime: 1000);

      await repository.insertLoginActivitiesFromSync([doc(id: 'srv-1')]);

      final row = (await repository.watchOfflineLogins('ada').first).single;
      expect(row.userId, 'user-ada');
      expect(row.description, ActivitiesRepository.loginDescription);
      expect(await repository.offlineVisitCount('user-ada'), 1);
    });

    test('a synced row is not queued for upload again', () async {
      await repository.insertLoginActivitiesFromSync([doc(id: 'srv-1')]);

      // `pendingLoginUploads` selects on a null `_rev`; a row that arrived from
      // the server has one, so it is already delivered.
      expect(await repository.pendingLoginUploads(), isEmpty);
    });

    test('another member\'s sessions arrive without touching ours', () async {
      await logLogin('local-1', 'ada', loginTime: 1000);

      await repository.insertLoginActivitiesFromSync([
        doc(id: 'srv-2', user: 'grace', loginTime: 2000),
      ]);

      expect(await repository.offlineLoginCount('ada'), 1);
      expect(await repository.offlineLoginCount('grace'), 1);
      // The global last visit is what the profile shows, so it moves.
      expect(await repository.globalLastVisit(), 2000);
    });
  });

  group('user_challenge_actions', () {
    test('a sync action is recorded and counted', () async {
      await repository.recordSyncUserChallengeAction('user-ada');
      expect(await repository.hasUserCompletedSync('user-ada'), isTrue);
    });

    test('hasUserCompletedSync is false before any sync', () async {
      expect(await repository.hasUserCompletedSync('user-ada'), isFalse);
    });

    test('a sync action is not shared across users', () async {
      await repository.recordSyncUserChallengeAction('user-ada');
      expect(await repository.hasUserCompletedSync('user-grace'), isFalse);
    });

    test('an empty userId is never completed', () async {
      expect(await repository.hasUserCompletedSync(''), isFalse);
    });

    test('repeated syncs do not flip the count back to false', () async {
      await repository.recordSyncUserChallengeAction('user-ada');
      await repository.recordSyncUserChallengeAction('user-ada');
      expect(await repository.hasUserCompletedSync('user-ada'), isTrue);
    });
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
