import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/system/device_stats.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/myplanet_activities_uploader.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  late MockPlanetApi api;
  late _FakeDeviceStats deviceStats;
  late PlanetPrefs prefs;
  late MyPlanetActivitiesUploader uploader;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  UserRow user({
    List<String> roles = const ['learner'],
    bool admin = false,
    String parentCode = 'nation',
    String planetCode = 'planet-a',
  }) => UserRow(
    id: 'user-1',
    name: 'ada',
    rolesList: roles,
    userAdmin: admin,
    joinDate: 123,
    parentCode: parentCode,
    planetCode: planetCode,
    isArchived: false,
    isUpdated: false,
  );

  setUp(() async {
    api = MockPlanetApi();
    registerFallbackValue(<String, dynamic>{});
    SharedPreferences.setMockInitialValues({});
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
    deviceStats = _FakeDeviceStats();
    uploader = MyPlanetActivitiesUploader(api, prefs, deviceStats);
  });

  /// Stubs the merge POST (any POST to the activities endpoint) to succeed.
  void stubMergeSuccess() {
    when(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );
  }

  test('the endpoint carries no credentials', () {
    final endpoint = MyPlanetActivitiesUploader.endpointFor(config);
    expect(endpoint, isNot(contains('satellite')));
    expect(endpoint, isNot(contains('1234')));
    expect(endpoint, endsWith('/myplanet_activities'));
  });

  test('the auth header is the satellite:PIN basic credential', () {
    expect(
      MyPlanetActivitiesUploader.authHeaderFor(config),
      startsWith('Basic '),
    );
  });

  test('the doc id is androidId@uniqueIdentifier', () {
    expect(
      MyPlanetActivitiesUploader.docIdFor('android-id', 'unique-id'),
      'android-id@unique-id',
    );
  });

  test('a manager user skips the upload entirely', () async {
    stubMergeSuccess();
    final result = await uploader.upload(
      user: user(roles: ['manager']),
      config: config,
    );
    expect(result, isTrue);
    verifyNever(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    );
  });

  test('a usage row carries the stored customDeviceName, not a blank', () async {
    // `addStats` writes `NetworkUtils.getCustomDeviceName()` onto every row.
    // That is a preference, so only the Dart side can supply it — the platform
    // channel used to hardcode an empty string here, which meant a user who had
    // named their device uploaded rows that claimed they had not.
    await prefs.setCustomDeviceName('front-desk tablet');
    when(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(null, 'not found'),
    );

    await uploader.upload(user: user(), config: config);

    final calls = verify(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        captureAny(),
        authHeader: any(named: 'authHeader'),
      ),
    ).captured;
    final syncDoc = calls.first as Map<String, dynamic>;
    final merged = calls.last as Map<String, dynamic>;
    // Both the doc-level field and the per-row field, so the two cannot drift
    // apart again.
    expect(syncDoc['customDeviceName'], 'front-desk tablet');
    expect(
      (merged['usages'] as List).single['customDeviceName'],
      'front-desk tablet',
    );
  });

  test(
    'posts a fresh usages doc when none exists, then advances the cutoff',
    () async {
      // Step 1: the "sync" doc POST.
      when(
        () => api.postJsonObject(
          MyPlanetActivitiesUploader.endpointFor(config),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
      );
      // Step 2: GET finds nothing.
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async =>
            const NetworkError<Map<String, dynamic>>(null, 'not found'),
      );

      final result = await uploader.upload(user: user(), config: config);

      expect(result, isTrue);

      // Two POSTs happened: the sync doc and the merged usages doc. Capture
      // their bodies in one verify pass — mocktail consumes invocations.
      final calls = verify(
        () => api.postJsonObject(
          MyPlanetActivitiesUploader.endpointFor(config),
          captureAny(),
          authHeader: any(named: 'authHeader'),
        ),
      ).captured;
      expect(calls, hasLength(2));

      final merged = calls.last as Map<String, dynamic>;
      expect(merged['_id'], 'android-id@unique-id');
      expect(merged['type'], 'usages');
      expect(merged['parentCode'], 'nation');
      expect(merged['createdOn'], 'planet-a');
      expect(merged['usages'], isA<List>());
      // The fake returns one usage row. Its `androidId` is the
      // `getUniqueIdentifier()` composite, matching what `addStats` writes and
      // what the "sync" doc above sends — not the bare ANDROID_ID, which would
      // make the server see one device as two.
      expect((merged['usages'] as List).single['androidId'], 'unique-id');
      expect((merged['usages'] as List).single['deviceName'], 'TEST DEVICE');

      // The cutoff advanced so the next upload starts from "now".
      expect(prefs.lastUsageUploaded, greaterThan(0));
    },
  );

  test('appends to an existing usages doc when one is found', () async {
    when(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );
    // Step 2: GET finds an existing doc with one prior usage.
    final existingUsages = [
      {'androidId': 'old', 'time': 1},
    ];
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        '_id': 'android-id@unique-id',
        '_rev': '1-abc',
        'type': 'usages',
        'parentCode': 'nation',
        'createdOn': 'planet-a',
        'usages': existingUsages,
      }),
    );

    await uploader.upload(user: user(), config: config);

    final calls = verify(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        captureAny(),
        authHeader: any(named: 'authHeader'),
      ),
    ).captured;
    final merged = calls.last as Map<String, dynamic>;
    // The existing `_rev` and `_id` are preserved for the update.
    expect(merged['_rev'], '1-abc');
    expect(merged['_id'], 'android-id@unique-id');
    final usages = merged['usages'] as List;
    // One prior + one new.
    expect(usages, hasLength(2));
    expect(usages.first['androidId'], 'old');
    expect(usages.last['androidId'], 'unique-id');
  });

  test('planetVersion is read from the cached versionDetail', () async {
    await prefs.setVersionDetail(jsonEncode({'planetVersion': '0.65.46'}));
    when(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(null, 'none'),
    );

    await uploader.upload(user: user(), config: config);

    final calls = verify(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        captureAny(),
        authHeader: any(named: 'authHeader'),
      ),
    ).captured;
    final syncDoc = calls.first as Map<String, dynamic>;
    expect(syncDoc['planetVersion'], '0.65.46');
    final usagesDoc = calls.last as Map<String, dynamic>;
    expect(usagesDoc['planetVersion'], '0.65.46');
  });

  test(
    'a failed merge POST leaves the cutoff untouched and returns false',
    () async {
      when(
        () => api.postJsonObject(
          MyPlanetActivitiesUploader.endpointFor(config),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((inv) async {
        // First POST (sync doc) succeeds; the merge (second) fails. `body` is
        // the first positional arg of `postJsonObject`.
        final body = inv.positionalArguments[1] as Map<String, dynamic>;
        if (body['type'] == 'sync') {
          return const NetworkSuccess<Map<String, dynamic>>({'ok': true});
        }
        return const NetworkError<Map<String, dynamic>>(null, 'boom');
      });
      when(
        () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
      ).thenAnswer(
        (_) async => const NetworkError<Map<String, dynamic>>(null, 'none'),
      );

      expect(prefs.lastUsageUploaded, 0);
      final result = await uploader.upload(user: user(), config: config);
      expect(result, isFalse);
      // Not advanced — the next attempt re-reads the same interval.
      expect(prefs.lastUsageUploaded, 0);
    },
  );

  test('a malformed versionDetail cache is treated as absent', () async {
    await prefs.setVersionDetail('not-json');
    when(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => const NetworkError<Map<String, dynamic>>(null, 'none'),
    );

    await uploader.upload(user: user(), config: config);

    final calls = verify(
      () => api.postJsonObject(
        MyPlanetActivitiesUploader.endpointFor(config),
        captureAny(),
        authHeader: any(named: 'authHeader'),
      ),
    ).captured;
    final syncDoc = calls.first as Map<String, dynamic>;
    expect(syncDoc.containsKey('planetVersion'), isFalse);
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}

/// Test double for [DeviceStats] — fixed identity plus a single usage row so
/// the merge path has something deterministic to append.
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
  }) async => [
    const TabletUsageStats(
      lastTimeUsed: 2000,
      firstTimeUsed: 1000,
      totalForegroundTime: 1000,
      totalUsed: 1000,
      version: 6342,
      versionName: '0.63.42',
      time: 2000,
    ),
  ];
}
