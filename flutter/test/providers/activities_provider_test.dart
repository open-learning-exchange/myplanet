import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/system/device_stats.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/activities_provider.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../support/widget_harness.dart';

/// Covers the wiring between a screen and the activity log: the repository and
/// the uploader are tested on their own, and this is the layer where "the row is
/// written but nothing queues it" hides.
void main() {
  late AppDatabase db;

  UserRow user({String id = 'user-1', String name = 'ada'}) => UserRow(
    id: id,
    name: name,
    rolesList: const [],
    userAdmin: false,
    joinDate: 0,
    planetCode: 'planet-a',
    parentCode: 'nation',
    isArchived: false,
    isUpdated: false,
  );

  /// `sessionProvider` is an `AsyncNotifier`, so a synchronous `read` sees
  /// `AsyncLoading` and every log call would no-op on a null user. Screens are
  /// behind the router's session gate and never see that, but a container has
  /// to await the build.
  Future<ProviderContainer> containerFor(
    UserRow? current, {
    bool configured = true,
  }) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        deviceStatsProvider.overrideWithValue(_FakeDeviceStats()),
        planetPrefsProvider.overrideWithValue(prefs),
        sessionProvider.overrideWith(() => _TestSessionNotifier(current)),
        if (configured) serverConfigProvider.overrideWith(_TestConfig.new),
      ],
    );
    addTearDown(container.dispose);
    await container.read(sessionProvider.future);
    return container;
  }

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  test('an open is logged and queued in one step', () async {
    final container = await containerFor(user());

    await container
        .read(activityLogProvider)
        .logResourceOpen(buildLibraryRow(id: 'row-1', title: 'Algebra'));

    final rows = await db.resourceActivityDao.byUserAndType('ada', 'visit');
    expect(rows.single.title, 'Algebra');
    // The row on its own is what every previous slice shipped; the queue entry
    // is what gets it off the device.
    final queued = await db.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((row) => row.uploadType), ['resource_activity']);
  });

  test('a download is logged under its own type', () async {
    final container = await containerFor(user());

    await container
        .read(activityLogProvider)
        .logResourceDownload(buildLibraryRow(id: 'row-1', title: 'Algebra'));

    expect(await db.resourceActivityDao.byUserAndType('ada', 'visit'), isEmpty);
    expect(
      (await db.resourceActivityDao.byUserAndType('ada', 'download')).single.id,
      isNotEmpty,
    );
  });

  test('a guest logs nothing at all', () async {
    final container = await containerFor(
      user(id: 'guest_ada', name: 'guest_ada'),
    );

    await container
        .read(activityLogProvider)
        .logResourceOpen(buildLibraryRow(id: 'row-1', title: 'Algebra'));
    await container
        .read(activityLogProvider)
        .logCourseVisit(courseId: 'c1', title: 'Intro');
    await container.read(activityLogProvider).recordSyncActivity();

    expect(await db.resourceActivityDao.pendingUploads(), isEmpty);
    expect(await db.resourceActivityDao.pendingSyncUploads(), isEmpty);
    expect(await db.courseActivityDao.pendingUploads(), isEmpty);
    expect(await db.outboxDao.due(9999999999999), isEmpty);
  });

  test('nothing is logged without a session', () async {
    final container = await containerFor(null);

    await container
        .read(activityLogProvider)
        .logResourceOpen(buildLibraryRow(id: 'row-1', title: 'Algebra'));

    expect(await db.resourceActivityDao.pendingUploads(), isEmpty);
  });

  test('a row is still written when no server is configured', () async {
    // The queue step needs a server; the log step must not. A resource opened
    // before the app has ever been configured still has to be recorded, or the
    // count is permanently wrong.
    final container = await containerFor(user(), configured: false);

    await container
        .read(activityLogProvider)
        .logResourceOpen(buildLibraryRow(id: 'row-1', title: 'Algebra'));

    expect(await db.resourceActivityDao.pendingUploads(), hasLength(1));
    expect(await db.outboxDao.due(9999999999999), isEmpty);
  });

  test('a course visit records the course id and queues', () async {
    final container = await containerFor(user());

    await container
        .read(activityLogProvider)
        .logCourseVisit(courseId: 'course-9', title: 'Intro');

    final row = (await db.courseActivityDao.pendingUploads()).single;
    expect(row.courseId, 'course-9');
    expect(row.title, 'Intro');
    final queued = await db.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.map((row) => row.uploadType), ['course_activity']);
  });

  test('a completed sync records one admin activity row', () async {
    final container = await containerFor(user());

    await container.read(activityLogProvider).recordSyncActivity();

    final row = (await db.resourceActivityDao.pendingSyncUploads()).single;
    expect(row.type, 'sync');
    expect(row.resourceId, null);
    final queued = await db.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch + 1000,
    );
    expect(queued.single.endpoint, endsWith('/admin_activities'));
  });

  test('the profile stats read the rows the log wrote', () async {
    final container = await containerFor(user());
    final log = container.read(activityLogProvider);

    await log.logResourceOpen(buildLibraryRow(id: 'row-1', title: 'Algebra'));
    await log.logResourceOpen(buildLibraryRow(id: 'row-1', title: 'Algebra'));
    await log.logResourceOpen(buildLibraryRow(id: 'row-2', title: 'Botany'));
    await container
        .read(activitiesRepositoryProvider)
        .logLogin(
          id: 'login-1',
          userId: 'user-1',
          userName: 'ada',
          loginTime: 4000,
        );

    final stats = await container.read(profileActivityStatsProvider.future);
    expect(stats.lastVisit, 4000);
    expect(stats.offlineVisits, 1);
    expect(stats.mostOpened?.title, 'Algebra');
    expect(stats.mostOpened?.count, 2);
    expect(stats.resourceOpenCount, 3);
  });
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);

  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}

class _TestConfig extends ServerConfigNotifier {
  @override
  ServerConfig? build() => const ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );
}

class _FakeDeviceStats implements DeviceStats {
  @override
  Future<String> androidId() async => 'test-android-id';

  @override
  Future<String> uniqueIdentifier() async => 'test-unique-id';

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
