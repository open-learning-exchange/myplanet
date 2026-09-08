import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/background_entrypoint.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_sync_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/submissions_uploader.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

/// Phase 134. Kotlin reaches the server for a finished answer sheet **twice**:
/// once from the profile dialog's dismissal (`UserInformationFragment:303`)
/// and once from `uploadManager.uploadSubmissions()`, which
/// `AutoSyncWorker:136`, `UserDataWorker:48` and
/// `ServerReachabilityWorker:183,186` all run unconditionally. A missed
/// dismissal therefore costs nothing there.
///
/// The port had only write-time call sites, so a sheet whose one enqueue call
/// never ran — process death on "Your information", an `if (!mounted) return`,
/// a throwing queue call on an exam attempt — stayed on the handset until the
/// same user happened to finish some *other* submission, because
/// `queuePending` is an unscoped sweep that then rescues it incidentally.
///
/// These tests drive the safety net from both sync paths.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  late AppDatabase db;
  late MockPlanetApi api;

  setUpAll(() {
    registerFallbackValue(config);
    registerFallbackValue(<String, dynamic>{});
  });

  setUp(() {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    api = MockPlanetApi();
  });
  tearDown(() => db.close());

  /// The reported state: `complete`, `isUpdated`, `!uploaded`, and **no**
  /// outbox row — an answer sheet whose profile screen was force-stopped
  /// between `markSubmissionComplete` and the push.
  Future<String> seedStrandedSheet({
    String userId = 'org.couchdb.user:ada',
  }) async {
    final repository = SubmissionsRepository(
      api,
      db.submissionDao,
      db.submitPhotosDao,
      db.surveyDao,
      db.examDao,
      teamDao: db.teamDao,
    );
    final id = await repository.createDraft(
      userId: userId,
      type: 'survey',
      title: 'Water access',
      answers: const [],
    );
    await repository.markSubmissionComplete(id, {'_id': userId, 'name': 'ada'});
    return id;
  }

  /// `outboxDrainerProvider` builds every registered handler, and several of
  /// those uploaders reach `planetPrefsProvider`, which is
  /// `UnimplementedError` by default. Without this the sweep's drain throws,
  /// its own `catch` swallows it, and the test would report the gap as
  /// unfixed — the Phase 75 harness trap, arriving through a new door.
  Future<PlanetPrefs> testPrefs() async =>
      PlanetPrefs(await SharedPreferences.getInstance());

  Future<ProviderContainer> containerFor({
    DeviceIdentitySource identity = testDeviceIdentity,
    String? couchId,
  }) async {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        planetPrefsProvider.overrideWithValue(await testPrefs()),
        deviceIdentitySourceProvider.overrideWithValue(identity),
        serverConfigProvider.overrideWith(
          () => _TestServerConfigNotifier(config),
        ),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(
            buildUserRow(
              id: 'org.couchdb.user:ada',
            ).copyWith(couchId: Value(couchId)),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  void acceptOnePost() {
    when(
      () => api.postJsonObject(
        any(),
        any(),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => NetworkSuccess<Map<String, dynamic>>({
        'id': 'server-id',
        'rev': '1-rev',
      }),
    );
  }

  group('foreground sync centre', () {
    test('syncAll delivers an answer sheet nothing enqueued', () async {
      final id = await seedStrandedSheet();
      final container = await containerFor();
      acceptOnePost();

      // Nothing has queued it: this is the state the gap leaves behind.
      expect(await db.outboxDao.forItem(SubmissionsUploader.type, id), isEmpty);

      await container.read(dashboardSyncProvider.notifier).syncAll();

      final row = await container
          .read(submissionsRepositoryProvider)
          .getById(id);
      expect(
        row?.uploaded,
        isTrue,
        reason:
            'the sync path is the safety net; the sheet must reach the '
            'server without a second submission being completed first',
      );
      expect(row?.couchId, 'server-id');
      verify(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).called(1);
    });

    test('the sweep runs even when every table pull fails', () async {
      // `ServerReachabilityWorker` sweeps with no pull at all and
      // `UserDataWorker` sweeps inside its own `runCatching`, so the sweep is
      // not conditional on a pull succeeding — unlike the telemetry uploads
      // beside it, which are gated on `successCount`.
      final id = await seedStrandedSheet();
      final container = await containerFor();
      acceptOnePost();

      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(container.read(dashboardSyncProvider).successCount, 0);
      expect(
        (await container.read(submissionsRepositoryProvider).getById(id))
            ?.uploaded,
        isTrue,
      );
    });

    test('the write-time enqueue and the sweep post one document', () async {
      final id = await seedStrandedSheet();
      final container = await containerFor();
      acceptOnePost();
      final uploader = container.read(submissionsUploaderProvider);

      // The write-time call site, exactly as `user_information_screen:471`
      // makes it.
      await uploader.queuePending(
        config: config,
        userId: 'org.couchdb.user:ada',
      );
      expect(
        await db.outboxDao.forItem(SubmissionsUploader.type, id),
        hasLength(1),
      );

      // The sweep's own enqueue over the same still-undelivered row. This is
      // the first of the two protections: `OutboxRepository.enqueue` keys on
      // `(uploadType, itemId)`, so it refreshes the queued row rather than
      // adding a second one. Kotlin has no equivalent — its only defence is
      // the query predicate below — so the port cannot double-post here even
      // where Kotlin's two paths race.
      await uploader.queuePending(
        config: config,
        userId: 'org.couchdb.user:ada',
      );
      expect(
        await db.outboxDao.forItem(SubmissionsUploader.type, id),
        hasLength(1),
        reason: 'a sweep over an already-queued submission must not add a row',
      );

      // Now let it go out.
      await container
          .read(dashboardSyncProvider.notifier)
          .queuePendingSubmissions();
      verify(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).called(1);
      expect(
        await db.outboxDao.forItem(SubmissionsUploader.type, id),
        isEmpty,
        reason: 'a delivered operation is dropped rather than marked completed',
      );

      // The second protection, and Kotlin's only one: `markUploaded` clears
      // `isUpdated` and stamps `_id`/`_rev`, which takes the row out of
      // `pendingUploads` — the port of `SubmissionDao:44`. A later sweep is a
      // no-op, not a duplicate.
      expect(
        await container.read(submissionsRepositoryProvider).pendingUploads(),
        isEmpty,
      );
      await container
          .read(dashboardSyncProvider.notifier)
          .queuePendingSubmissions();
      verifyNoMoreInteractions(api);
    });

    test('a sweep that throws does not fail the sync', () async {
      await seedStrandedSheet();
      final container = await containerFor(
        identity: const _ThrowingIdentitySource(),
      );

      await container.read(dashboardSyncProvider.notifier).syncAll();

      final state = container.read(dashboardSyncProvider);
      expect(state.running, isFalse);
      expect(state.finishedAt, isNotNull);
      expect(state.completedCount, DashboardSyncArea.values.length);
    });

    test('the sweep runs before the first table pull', () async {
      // Same assertion shape as the shelf push's: every area still `waiting`
      // when the POST goes out. Ordering is free in this pass (nothing here
      // pulls `submissions`), but the headless path's is not, and the two must
      // not disagree about which side of the pulls a push belongs on.
      await seedStrandedSheet();
      final container = await containerFor();
      List<DashboardSyncStatus>? statusesAtPushTime;
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async {
        statusesAtPushTime = container
            .read(dashboardSyncProvider)
            .items
            .map((item) => item.status)
            .toList(growable: false);
        return const NetworkSuccess<Map<String, dynamic>>({
          'id': 'server-id',
          'rev': '1-rev',
        });
      });

      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(statusesAtPushTime, isNotNull, reason: 'nothing was posted');
      expect(statusesAtPushTime, everyElement(DashboardSyncStatus.waiting));
    });

    test('no configured server means no sweep', () async {
      final id = await seedStrandedSheet();
      final container = ProviderContainer(
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          planetApiProvider.overrideWithValue(api),
          planetPrefsProvider.overrideWithValue(await testPrefs()),
          deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
          serverConfigProvider.overrideWith(
            () => _TestServerConfigNotifier(null),
          ),
          sessionProvider.overrideWith(
            () =>
                _TestSessionNotifier(buildUserRow(id: 'org.couchdb.user:ada')),
          ),
        ],
      );
      addTearDown(container.dispose);

      await container
          .read(dashboardSyncProvider.notifier)
          .queuePendingSubmissions();

      expect(await db.outboxDao.forItem(SubmissionsUploader.type, id), isEmpty);
      verifyNever(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      );
    });
  });

  group('headless path', () {
    test('the push step queues and delivers a stranded sheet', () async {
      final id = await seedStrandedSheet();
      final container = await containerFor();
      acceptOnePost();

      final step = submissionsPushStep(
        container,
        config: config,
        userId: 'org.couchdb.user:ada',
      );

      expect(step.name, 'submissions_push');
      expect(await step.run(), isTrue);
      expect(
        (await container.read(submissionsRepositoryProvider).getById(id))
            ?.uploaded,
        isTrue,
      );
    });

    test('the push step sweeps with no signed-in user', () async {
      // Kotlin's sweep is unscoped and takes no user
      // (`SubmissionDao.pendingUploads`); the port's `userId` is only the
      // outbox row's session tag. A handset whose session is gone must still
      // deliver the sheet left on it.
      final id = await seedStrandedSheet();
      final container = await containerFor();
      acceptOnePost();

      expect(
        await submissionsPushStep(
          container,
          config: config,
          userId: null,
        ).run(),
        isTrue,
      );

      expect(
        (await container.read(submissionsRepositoryProvider).getById(id))
            ?.uploaded,
        isTrue,
      );
    });

    test('a throwing push step does not request an OS retry', () async {
      // `BackgroundTaskRunner` adds a step that throws or returns false to
      // `failedSteps`, which makes `run` return false and WorkManager retry
      // the whole task. Kotlin wraps its sweep in `runCatching`
      // (`UserDataWorker:48`) and `AutoSyncWorker` always reports success, so
      // an undeliverable sheet must not re-run the pulls.
      await seedStrandedSheet();
      final container = await containerFor(
        identity: const _ThrowingIdentitySource(),
      );

      expect(
        await submissionsPushStep(
          container,
          config: config,
          userId: 'org.couchdb.user:ada',
        ).run(),
        isTrue,
      );
    });
  });

  /// **Reachability, not behaviour.** [submissionsPushStep] is exercised
  /// directly above, which is exactly the shape Phase 113 warned about: a
  /// step can be ported, tested and green while nothing in the app builds it.
  /// `executeBackgroundTask` needs a Flutter binding, real preferences and a
  /// WorkManager engine, so its `syncSteps` list cannot be built in a unit
  /// test — the source is read instead, the way `version_parity_test` reads
  /// `app/build.gradle`.
  test('the headless path builds the step, ahead of the submissions pull', () {
    final source = File('lib/background_entrypoint.dart').readAsStringSync();
    final steps = source.substring(source.indexOf('syncSteps:'));

    final built = steps.indexOf('submissionsPushStep(');
    final pull = steps.indexOf(
      "BackgroundSyncStep(\n                'submissions',",
    );

    expect(
      built,
      greaterThan(-1),
      reason: 'nothing builds submissionsPushStep',
    );
    expect(pull, greaterThan(-1), reason: "the 'submissions' pull step moved");
    expect(
      built,
      lessThan(pull),
      reason:
          'the sweep must precede the pull — see the test below for the '
          'window that ordering closes',
    );
  });

  /// Why the order above is load-bearing rather than tidy, and the narrow
  /// shape of it. `upsertDocuments` keys each row on the server `_id`, so a
  /// pulled document lands *beside* a locally authored sheet (sha1 local id)
  /// and cannot touch its flags — Kotlin behaves the same way
  /// (`SubmissionsRepositoryImpl:669-670`). The row it can clobber is one that
  /// arrived from the server and was then edited locally, which survey resume
  /// produces: `markComplete` sets `uploaded: false, isUpdated: true` on a row
  /// whose primary key *is* the server `_id`.
  test('a pull clears the local edit on a server-originated sheet', () async {
    final db = AppDatabase.memory();
    addTearDown(db.close);
    final api = MockPlanetApi();
    final repository = SubmissionsRepository(
      api,
      db.submissionDao,
      db.submitPhotosDao,
      db.surveyDao,
      db.examDao,
      teamDao: db.teamDao,
    );

    await repository.upsertDocuments([
      {
        '_id': 'server-1',
        '_rev': '1-rev',
        'parentId': 'survey-1',
        'type': 'survey',
        'status': 'pending',
        'user': {'_id': 'org.couchdb.user:ada'},
      },
    ]);
    // The local edit — the shape survey resume leaves behind.
    await repository.markSubmissionComplete('server-1', {
      '_id': 'org.couchdb.user:ada',
    });
    expect(
      (await repository.pendingUploads()).map((row) => row.id),
      contains('server-1'),
    );

    // The same document arrives again on the next walk.
    await repository.upsertDocuments([
      {
        '_id': 'server-1',
        '_rev': '2-rev',
        'parentId': 'survey-1',
        'type': 'survey',
        'status': 'pending',
        'user': {'_id': 'org.couchdb.user:ada'},
      },
    ]);

    expect(
      await repository.pendingUploads(),
      isEmpty,
      reason:
          'the pull wrote isUpdated: false over the local edit, so a sweep '
          'running after it would find nothing to send',
    );
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}

/// Reproduces the one failure `PlatformDeviceIdentitySource.read` really has:
/// a headless WorkManager engine with no primed cache rethrows
/// (`device_identity.dart:89`), and `SubmissionsUploader.queuePending` reads
/// identity before it enqueues anything.
class _ThrowingIdentitySource implements DeviceIdentitySource {
  const _ThrowingIdentitySource();

  @override
  Future<DeviceIdentity> read() async =>
      throw StateError('no platform channel and no primed cache');
}

class _TestServerConfigNotifier extends ServerConfigNotifier {
  _TestServerConfigNotifier(this._config);

  final ServerConfig? _config;

  @override
  ServerConfig? build() => _config;
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this._user);

  final UserRow? _user;

  @override
  Future<UserRow?> build() async => _user;
}
