import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/system/device_stats.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/activities_repository.dart';
import 'package:myplanet/repository/activities_uploader.dart';
import 'package:myplanet/repository/outbox_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late ActivitiesRepository activities;
  late ActivitiesUploader uploader;
  late OutboxRepository outbox;
  late _FakeDeviceStats deviceStats;
  late PlanetPrefs prefs;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() async {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
    outbox = OutboxRepository(database.outboxDao);
    activities = ActivitiesRepository(
      api,
      database.offlineActivityDao,
      database.resourceActivityDao,
      database.courseActivityDao,
      database.userChallengeActionDao,
    );
    deviceStats = _FakeDeviceStats();
    SharedPreferences.setMockInitialValues({});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
    uploader = ActivitiesUploader(api, activities, outbox, deviceStats, prefs);
  });
  tearDown(() => database.close());

  OutboxRow rowFor(String uploadType, String itemId, String database) =>
      OutboxRow(
        id: 'op-$itemId',
        uploadType: uploadType,
        itemId: itemId,
        payload: '{}',
        endpoint: ActivitiesUploader.endpointFor(config, database),
        httpMethod: 'POST',
        status: 'in_progress',
        attemptCount: 0,
        maxAttempts: 5,
        createdAt: 0,
        lastAttemptAt: 0,
        nextAttemptAt: 0,
      );

  void respondWith(Map<String, dynamic> body) {
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async => NetworkSuccess<Map<String, dynamic>>(body));
  }

  Future<void> seedLogin() => activities.logLogin(
    id: 'login-1',
    userId: 'user-ada',
    userName: 'ada',
    parentCode: 'nation',
    planetCode: 'planet-a',
    loginTime: 1000,
  );

  test('the endpoints carry no credentials', () {
    for (final db in const [
      'login_activities',
      'resource_activities',
      'admin_activities',
      'course_activities',
    ]) {
      final endpoint = ActivitiesUploader.endpointFor(config, db);
      expect(endpoint, isNot(contains('satellite')));
      expect(endpoint, isNot(contains('1234')));
      expect(endpoint, endsWith('/$db'));
    }
  });

  test('each activity kind is queued to its own database', () async {
    await seedLogin();
    await activities.logResourceOpen(
      id: 'visit-1',
      userId: 'user-ada',
      userName: 'ada',
      type: ActivityTypes.visit,
      time: 1,
    );
    await activities.recordSyncActivity(
      id: 'sync-1',
      userId: 'user-ada',
      userName: 'ada',
      time: 2,
    );
    await activities.logCourseVisit(
      id: 'course-1',
      userId: 'user-ada',
      userName: 'ada',
      courseId: 'c1',
      time: 3,
    );

    expect(await uploader.queuePending(config: config), 4);

    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    final endpoints = {
      for (final row in queued) row.itemId: row.endpoint.split('/').last,
    };
    expect(endpoints, {
      'login-1': 'login_activities',
      'visit-1': 'resource_activities',
      // The Kotlin's `ResourceActivitiesSync` config posts the same table's
      // `sync` rows to a different database.
      'sync-1': 'admin_activities',
      'course-1': 'course_activities',
    });
  });

  test('a login document sends the user name, not the id', () async {
    // `serializeLoginActivities` writes `ob.addProperty("user", userName)`, so
    // the `userId` column never reaches the server.
    await seedLogin();
    await uploader.queuePending(config: config);

    final entry = await database.outboxDao.findOpen(
      ActivitiesUploader.loginType,
      'login-1',
    );
    final doc = jsonDecode(entry!.payload) as Map<String, dynamic>;
    expect(doc['user'], 'ada');
    expect(doc.containsKey('userId'), isFalse);
    expect(doc['type'], ActivityTypes.login);
    expect(doc['loginTime'], 1000);
    expect(doc['createdOn'], 'planet-a');
    expect(doc['parentCode'], 'nation');
    // Device telemetry, ported through the DeviceStats seam.
    expect(doc['androidId'], 'unique-id');
    expect(doc['deviceName'], 'TEST DEVICE');
    expect(doc['customDeviceName'], '');
    // The Kotlin's `_id` branch — which writes the logout *timestamp* as the
    // document id — is deliberately not reproduced; see the uploader.
    expect(doc.containsKey('_id'), isFalse);
  });

  test(
    'a successful upload records the id and rev the server assigned',
    () async {
      await seedLogin();
      respondWith({'ok': true, 'id': 'srv-1', 'rev': '1-a'});

      final handler = uploader.handlers[ActivitiesUploader.loginType]!;
      final result = await handler(
        rowFor(ActivitiesUploader.loginType, 'login-1', 'login_activities'),
        {},
        'auth',
      );

      expect(result, isA<NetworkSuccess<Map<String, dynamic>>>());
      final row = (await database.offlineActivityDao.latestByType('login'))!;
      // Kotlin's `changeRev` reads `_id`/`_rev` from a response carrying `id`/
      // `rev` and stores empty strings; the real values are recorded here.
      expect(row.couchId, 'srv-1');
      expect(row.rev, '1-a');
      expect(await activities.pendingLoginUploads(), isEmpty);
    },
  );

  test(
    'a response without an id or rev is an error, not a silent success',
    () async {
      await seedLogin();
      respondWith({'ok': true});

      final handler = uploader.handlers[ActivitiesUploader.loginType]!;
      final result = await handler(
        rowFor(ActivitiesUploader.loginType, 'login-1', 'login_activities'),
        {},
        'auth',
      );

      // Reporting success would retire the outbox entry while the row stayed
      // pending, so the next queue would post the same session again.
      expect(result, isA<NetworkError<Map<String, dynamic>>>());
      expect((await activities.pendingLoginUploads()).single.id, 'login-1');
    },
  );

  test('each handler marks its own table', () async {
    await activities.logResourceOpen(
      id: 'visit-1',
      userId: 'user-ada',
      userName: 'ada',
      type: ActivityTypes.visit,
      time: 1,
    );
    await activities.recordSyncActivity(
      id: 'sync-1',
      userId: 'user-ada',
      userName: 'ada',
      time: 2,
    );
    await activities.logCourseVisit(
      id: 'course-1',
      userId: 'user-ada',
      userName: 'ada',
      courseId: 'c1',
      time: 3,
    );
    respondWith({'id': 'srv', 'rev': '1-a'});

    await uploader.handlers[ActivitiesUploader.resourceType]!(
      rowFor(ActivitiesUploader.resourceType, 'visit-1', 'resource_activities'),
      {},
      null,
    );
    await uploader.handlers[ActivitiesUploader.resourceSyncType]!(
      rowFor(ActivitiesUploader.resourceSyncType, 'sync-1', 'admin_activities'),
      {},
      null,
    );
    await uploader.handlers[ActivitiesUploader.courseType]!(
      rowFor(ActivitiesUploader.courseType, 'course-1', 'course_activities'),
      {},
      null,
    );

    expect(await activities.pendingResourceUploads(), isEmpty);
    expect(await activities.pendingSyncUploads(), isEmpty);
    expect(await activities.pendingCourseUploads(), isEmpty);
  });

  test('queuing twice does not duplicate the operation', () async {
    await seedLogin();
    expect(await uploader.queuePending(config: config), 1);
    expect(await uploader.queuePending(config: config), 1);

    final queued = await database.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.where((row) => row.itemId == 'login-1'), hasLength(1));
  });

  test('the resource document carries the fields Planet keys on', () async {
    await activities.logResourceOpen(
      id: 'visit-1',
      userId: 'user-ada',
      userName: 'ada',
      parentCode: 'nation',
      planetCode: 'planet-a',
      title: 'Algebra',
      resourceId: 'res-1',
      type: ActivityTypes.download,
      time: 4000,
    );
    await uploader.queuePending(config: config);

    final entry = await database.outboxDao.findOpen(
      ActivitiesUploader.resourceType,
      'visit-1',
    );
    expect(jsonDecode(entry!.payload), {
      'user': 'ada',
      'resourceId': 'res-1',
      'type': ActivityTypes.download,
      'title': 'Algebra',
      'time': 4000,
      'createdOn': 'planet-a',
      'parentCode': 'nation',
      // The Kotlin's `serializeResourceActivities` writes androidId/deviceName
      // but no customDeviceName — the resource doc matches that shape.
      'androidId': 'unique-id',
      'deviceName': 'TEST DEVICE',
    });
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}

/// Test double for [DeviceStats] — returns fixed device-identity values so
/// the serializer output is deterministic. No platform channel is invoked.
class _FakeDeviceStats implements DeviceStats {
  @override
  Future<String> androidId() async => 'android-id';

  @override
  Future<String> uniqueIdentifier() async => 'unique-id';

  @override
  Future<String> deviceName() async => 'TEST DEVICE';

  @override
  Future<int> versionCode() async => 6342;

  @override
  Future<String?> versionName() async => '0.63.42';

  @override
  Future<List<TabletUsageStats>> tabletUsageStats({
    required int sinceMillis,
  }) async => const [];
}
