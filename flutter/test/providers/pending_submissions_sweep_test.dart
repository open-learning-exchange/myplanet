import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/background_entrypoint.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
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
      retry: noProviderRetry,
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

    test('a re-enqueue mid-flight does not post a second document', () async {
      // The Phase 134 implementation audit found this, and it is the failure
      // mode the phase most had to avoid. `OutboxRepository.enqueue` puts an
      // `in_progress` row back to `pending` so a payload edited mid-flight is
      // not lost, and `markCompleted` is `deleteIfInProgress` — so the send
      // that just succeeded deletes nothing and the row survives, `pending`,
      // with the same body. That is right for *derived state* (the shelf, whose
      // handler rebuilds); a submission is an append (`submissions_uploader:10`)
      // and replaying it is a second CouchDB document, not an edit.
      //
      // Reachable as soon as a sweep drains while the app is interactive:
      // tapping Sync puts the POST on the wire, and the respondent finishing a
      // survey during it calls the same `queuePending`.
      final id = await seedStrandedSheet();
      final container = await containerFor();
      final uploader = container.read(submissionsUploaderProvider);
      var posts = 0;
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async {
        posts++;
        // The interleaving: another write-time enqueue lands while this POST
        // is on the wire.
        await uploader.queuePending(
          config: config,
          userId: 'org.couchdb.user:ada',
        );
        return NetworkSuccess<Map<String, dynamic>>({
          'id': 'server-id-$posts',
          'rev': '$posts-rev',
        });
      });

      await container
          .read(dashboardSyncProvider.notifier)
          .queuePendingSubmissions();
      // Whatever the resume drain does next must not re-send it.
      await container
          .read(outboxDrainerProvider)
          .drain(authHeader: 'Basic test');

      expect(posts, 1, reason: 'the answer sheet was posted twice');
      expect(
        await db.outboxDao.forItem(SubmissionsUploader.type, id),
        isEmpty,
        reason: 'a delivered append must leave no replayable row behind',
      );
      expect(
        (await container.read(submissionsRepositoryProvider).getById(id))
            ?.couchId,
        'server-id-1',
      );
    });

    test('a sweep that throws does not fail the sync', () async {
      await seedStrandedSheet();
      final identity = _ThrowingIdentitySource();
      final container = await containerFor(identity: identity);

      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(
        identity.reads,
        1,
        reason: 'the sweep never got far enough to throw',
      );

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
      bool? challengeRecordedFirst;
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
        challengeRecordedFirst = await container
            .read(activitiesRepositoryProvider)
            .hasUserCompletedSync('org.couchdb.user:ada');
        return const NetworkSuccess<Map<String, dynamic>>({
          'id': 'server-id',
          'rev': '1-rev',
        });
      });

      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(statusesAtPushTime, isNotNull, reason: 'nothing was posted');
      expect(statusesAtPushTime, everyElement(DashboardSyncStatus.waiting));
      // And behind the challenge write, which is local and instant. Kotlin
      // records it "right before the sync starts"
      // (`DashboardElementActivity.logSyncInSharedPrefs`); a user who taps Sync
      // and backgrounds the app during the sweep's unbounded network work
      // should still get the credit for pressing the button.
      expect(
        challengeRecordedFirst,
        isTrue,
        reason: 'the sweep overtook recordSyncChallengeAction',
      );
    });

    test("delivers another user's sheet from this user's sync", () async {
      // The shared handset, which is the normal deployment for this app and
      // the case the unscoped design exists for: member B finishes a survey
      // and signs out without syncing, member A signs in and syncs.
      // `SubmissionDao.pendingUploads`' own doc comment names this flow, and
      // until now nothing drove it through a sync — a `pendingUploads()`
      // re-scoped to the session in SQL would have left every other test
      // green.
      final id = await seedStrandedSheet(userId: 'org.couchdb.user:bob');
      final container = await containerFor();
      Map<String, dynamic>? posted;
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((invocation) async {
        posted = invocation.positionalArguments[1] as Map<String, dynamic>;
        return const NetworkSuccess<Map<String, dynamic>>({
          'id': 'server-id',
          'rev': '1-rev',
        });
      });

      // Ada is the session (see `containerFor`).
      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(
        (await container.read(submissionsRepositoryProvider).getById(id))
            ?.uploaded,
        isTrue,
        reason: "the signed-in learner's sync must carry the whole handset",
      );
      expect(
        (posted?['user'] as Map<String, dynamic>?)?['_id'],
        'org.couchdb.user:bob',
        reason: 'each document is attributed to its own owner, not the session',
      );
    });

    test('the sync drains the whole queue, not just submissions', () async {
      // `drain(onlyTypes: …)` was the first cut and was wrong: a *joining*
      // caller gets `const []` without doing its own work
      // (`outbox_drainer.dart:79-82`), so a resume drain arriving during a
      // submissions-only pass would return having sent nothing and starve
      // every other queued write. Kotlin's manual sync is a whole-queue flush
      // anyway (`UserDataWorker`'s `UPLOAD_TYPE_BULK`).
      await seedStrandedSheet();
      final container = await containerFor();
      acceptOnePost();
      when(
        () => api.sendJsonObject(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer(
        (_) async => const NetworkSuccess<Map<String, dynamic>>({'ok': true}),
      );
      // A row of some other type, queued before the sync and handler-less, so
      // the drainer sends it through the generic path.
      await container
          .read(outboxRepositoryProvider)
          .enqueue(
            uploadType: 'audit_probe',
            itemId: 'probe-1',
            endpoint: 'https://planet.example.org/probe',
            payload: const {'hello': 'world'},
          );

      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(
        await db.outboxDao.forItem('audit_probe', 'probe-1'),
        isEmpty,
        reason: "the sync's drain left an unrelated queued write behind",
      );
    });

    test('no configured server means no sweep', () async {
      final id = await seedStrandedSheet();
      final container = ProviderContainer(
        retry: noProviderRetry,
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
    test('the sweep queues a stranded sheet', () async {
      final id = await seedStrandedSheet();
      final container = await containerFor();

      await sweepPendingSubmissions(
        container,
        config: config,
        userId: 'org.couchdb.user:ada',
      );

      expect(
        await db.outboxDao.forItem(SubmissionsUploader.type, id),
        hasLength(1),
      );
    });

    test('the sweep runs with no signed-in user', () async {
      // Kotlin's sweep is unscoped and takes no user
      // (`SubmissionDao.getPendingSubmissions`); the port's `userId` is only
      // the outbox row's session tag. A handset whose session is gone must
      // still deliver the sheet left on it.
      final id = await seedStrandedSheet(userId: 'org.couchdb.user:bob');
      final container = await containerFor();

      await sweepPendingSubmissions(container, config: config, userId: null);

      final queued = await db.outboxDao.forItem(SubmissionsUploader.type, id);
      expect(queued, hasLength(1));
      expect(queued.single.userId, isNull);
    });

    test('a throwing sweep does not escape', () async {
      // `drainOutbox` throwing adds `outboxDrain` to the runner's
      // `failedSteps`, which asks WorkManager to retry the whole task. Kotlin
      // wraps its sweep in `runCatching` (`UserDataWorker:48`) and still
      // returns `Result.success()`, so an undeliverable sheet must not re-run
      // the pulls.
      await seedStrandedSheet();
      final identity = _ThrowingIdentitySource();
      final container = await containerFor(identity: identity);

      await expectLater(
        sweepPendingSubmissions(
          container,
          config: config,
          userId: 'org.couchdb.user:ada',
        ),
        completes,
      );
      expect(
        identity.reads,
        1,
        reason:
            'the test proves nothing unless the throw actually happened — '
            '`queuePending` only reads identity when it has rows to queue',
      );
    });
  });

  /// **Reachability, not behaviour.** [sweepPendingSubmissions] is exercised
  /// directly above, which is exactly the shape Phase 113 warned about: a
  /// function can be ported, tested and green while nothing in the app calls
  /// it. `executeBackgroundTask` needs a Flutter binding, real preferences and
  /// a WorkManager engine, so its wiring cannot be built in a unit test — the
  /// source is read instead, the way `version_parity_test` reads
  /// `app/build.gradle`.
  ///
  /// It asserts the *call site*, not merely that the name occurs: the first cut
  /// searched from `syncSteps:` to end of file, which includes the function's
  /// own declaration, so its "nothing calls it" assertion could never fail.
  test('the headless path calls the sweep from drainOutbox', () {
    final source = File('lib/background_entrypoint.dart').readAsStringSync();
    // Bounded to the runner's argument list, which ends where the top-level
    // declarations begin.
    final wiring = source.substring(
      source.indexOf('BackgroundTaskRunner('),
      source.indexOf('@visibleForTesting'),
    );

    expect(
      wiring,
      contains('sweepPendingSubmissions('),
      reason: 'nothing in the headless path calls sweepPendingSubmissions',
    );

    final swept = wiring.indexOf('sweepPendingSubmissions(');
    final drained = wiring.indexOf('drainer.drain(');
    final pull = wiring.indexOf("'submissions',");

    expect(drained, greaterThan(-1), reason: 'drainer.drain moved');
    expect(pull, greaterThan(-1), reason: "the 'submissions' pull step moved");
    expect(
      swept,
      lessThan(drained),
      reason:
          'the sweep must queue before the drain that carries it, or the rows '
          'wait for the next invocation',
    );
    expect(
      swept,
      lessThan(pull),
      reason:
          'the sweep must precede the pull — see the test below for the '
          'window that ordering closes',
    );
    expect(
      swept,
      lessThan(wiring.indexOf('syncSteps:')),
      reason:
          'the sweep must stay in drainOutbox rather than move into '
          'syncSteps: those run only for an autoSync task, with auto-sync '
          'enabled, and only when the interval is due — three gates Kotlin '
          "ServerReachabilityWorker's network-reconnection sweep has none of",
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
  _ThrowingIdentitySource();

  /// Counted so a test can prove the throw happened. `queuePending` reads
  /// identity only when `pendingUploads()` returned rows, so a fixture that
  /// stopped producing one would leave these tests passing vacuously.
  int reads = 0;

  @override
  Future<DeviceIdentity> read() async {
    reads++;
    throw StateError('no platform channel and no primed cache');
  }
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
