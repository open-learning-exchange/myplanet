import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/files/resource_files.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/repository/local_resource_request.dart';
import 'package:myplanet/repository/resources_uploader.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// The safety-net question, asked of the resource upload direction.
///
/// Kotlin reaches the server for a locally created resource from an
/// **unconditional sweep**: `UploadManager.uploadResource` reads
/// `getPendingResourceUploads()` — every row with no revision — and
/// `AutoSyncWorker:129`, `UserDataWorker:84` and `TeamsRepositoryImpl:928`
/// call it off nothing the resource itself did.
///
/// The port enqueues at write time, from `add_resource_screen._save`. Better
/// when it runs; nothing when it does not. Phase 134 is the standing lesson:
/// every layer had passing tests and **nothing asked whether the sweep knew
/// about the write**.
///
/// [sweepPendingResources] is the headless half, and these tests drive it
/// directly because `lib/background_entrypoint.dart` belongs to another lane
/// this round — so the call it needs is reported in the PR rather than made.
/// **The feature is incomplete without that wiring**, and these tests are what
/// make the missing call a one-liner instead of a rediscovery.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  late AppDatabase db;
  late _MockPlanetApi api;
  late Directory sandbox;
  late Directory source;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    api = _MockPlanetApi();
    sandbox = await Directory.systemTemp.createTemp('res-sweep-docs');
    source = await Directory.systemTemp.createTemp('res-sweep-pick');
    ResourceFiles.baseDirectory = () async => sandbox;
  });
  tearDown(() async {
    ResourceFiles.baseDirectory = () async => Directory.systemTemp;
    await db.close();
    await sandbox.delete(recursive: true);
    await source.delete(recursive: true);
  });

  Future<ProviderContainer> containerFor({
    DeviceIdentitySource identity = testDeviceIdentity,
  }) async {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        planetPrefsProvider.overrideWithValue(
          PlanetPrefs(await SharedPreferences.getInstance()),
        ),
        deviceIdentitySourceProvider.overrideWithValue(identity),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  void acceptPosts() {
    var posts = 0;
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer((_) async {
      posts++;
      return NetworkSuccess<Map<String, dynamic>>({
        'id': 'server-id-$posts',
        'rev': '$posts-rev',
      });
    });
    when(
      () => api.uploadAttachment(
        any(),
        bytes: any(named: 'bytes'),
        authHeader: any(named: 'authHeader'),
        contentType: any(named: 'contentType'),
        ifMatch: any(named: 'ifMatch'),
      ),
    ).thenAnswer(
      (_) async => const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
    );
  }

  /// The reported state: a `my_library` row the user created, with no `_id`
  /// and **no** outbox row — the resource whose one write-time enqueue never
  /// ran, which before this lane was every resource ever created.
  Future<String> seedStrandedResource(ProviderContainer container) async {
    final picked = File('${source.path}/well.pdf');
    await picked.writeAsString('water table falling');
    final error = await container
        .read(resourcesRepositoryProvider)
        .saveLocalResource(
          LocalResourceRequest(
            title: 'Well survey',
            userId: 'org.couchdb.user:ada',
            resourceUrl: picked.path,
            mediaType: 'pdf',
          ),
        );
    expect(error, isNull);
    final row = (await db.myLibraryDao.getAll()).single;
    expect(
      await db.outboxDao.forItem(ResourcesUploader.type, row.id),
      isEmpty,
      reason: 'the fixture must reproduce the stranded state, not assume it',
    );
    return row.id;
  }

  test('the sweep delivers a resource nothing enqueued', () async {
    final container = await containerFor();
    final id = await seedStrandedResource(container);
    acceptPosts();

    await sweepPendingResources(container, config: config, userId: null);
    await container.read(outboxDrainerProvider).drain();

    final row = await db.myLibraryDao.getById(id);
    expect(
      row?.couchId,
      'server-id-1',
      reason:
          'without a sweep this resource is invisible in Planet for ever, '
          'and reset-app can destroy it in the meantime',
    );
    expect(row?.rev, '1-rev');
  });

  test(
    'a null session still sends the resource, minus its attribution',
    () async {
      // Kotlin passes `user?.id` and `user?.planetCode` straight into the
      // serializer with no guard (`MyLibrary.kt:161`, `:181-182`), so a handset
      // whose session has gone still uploads — and that is precisely the handset
      // most likely to be carrying a stranded write.
      final container = await containerFor();
      await seedStrandedResource(container);
      acceptPosts();

      await sweepPendingResources(container, config: config, userId: null);

      final payload = (await db.outboxDao.due(
        DateTime.now().millisecondsSinceEpoch,
      )).single.payload;
      expect(payload, contains('"addedBy":null'));
      expect(payload, contains('"sourcePlanet":null'));
      expect(payload, contains('"title":"Well survey"'));
    },
  );

  test('a resolvable session supplies addedBy and the planet code', () async {
    final container = await containerFor();
    await db.userDao.upsertAll([
      buildUserRow(
        id: 'org.couchdb.user:ada',
        name: 'ada',
      ).copyWith(planetCode: const Value('guatemala')).toCompanion(true),
    ]);
    await seedStrandedResource(container);
    acceptPosts();

    await sweepPendingResources(
      container,
      config: config,
      userId: 'org.couchdb.user:ada',
    );

    final payload = (await db.outboxDao.due(
      DateTime.now().millisecondsSinceEpoch,
    )).single.payload;
    expect(payload, contains('"addedBy":"org.couchdb.user:ada"'));
    expect(payload, contains('"sourcePlanet":"guatemala"'));
    expect(payload, contains('"resideOn":"guatemala"'));
  });

  test('the write-time enqueue and the sweep send one document', () async {
    // The pair Phase 134 says to test. `OutboxRepository.enqueue` keys on
    // `(uploadType, itemId)`, so the sweep refreshes the queued row rather
    // than adding a second; and the pending predicate stops offering a row
    // once its `_id` is recorded. Two independent protections, which is one
    // more than Kotlin has — Kotlin relies on the predicate alone, and
    // `RetryQueueWorker` bypasses the mark that satisfies it.
    final container = await containerFor();
    final id = await seedStrandedResource(container);
    acceptPosts();

    await container
        .read(resourcesUploaderProvider)
        .queuePending(config: config, user: null);
    expect(
      await db.outboxDao.forItem(ResourcesUploader.type, id),
      hasLength(1),
    );

    await sweepPendingResources(container, config: config, userId: null);
    expect(
      await db.outboxDao.forItem(ResourcesUploader.type, id),
      hasLength(1),
      reason: 'a sweep over an already-queued resource must not add a row',
    );

    await container.read(outboxDrainerProvider).drain();
    await sweepPendingResources(container, config: config, userId: null);
    await container.read(outboxDrainerProvider).drain();

    verify(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).called(1);
  });

  test('the sweep swallows a device-identity failure', () async {
    // A throwing `drainOutbox` adds `outboxDrain` to the runner's
    // `failedSteps` and asks the OS to retry the whole task. No Kotlin caller
    // of `uploadResource` does that. `queuePending` reads device identity,
    // which rethrows on a headless engine with no channel and no primed
    // cache — the concrete way this fires.
    final container = await containerFor(identity: const _ThrowingIdentity());
    await seedStrandedResource(container);

    await expectLater(
      sweepPendingResources(container, config: config, userId: null),
      completes,
    );
  });
}

class _ThrowingIdentity implements DeviceIdentitySource {
  const _ThrowingIdentity();

  @override
  Future<DeviceIdentity> read() async =>
      throw StateError('no platform channel on this engine');
}
