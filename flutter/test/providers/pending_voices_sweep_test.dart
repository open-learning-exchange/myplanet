import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/background_entrypoint.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/system/device_identity.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_sync_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/providers/voices_provider.dart';
import 'package:myplanet/repository/voices_uploader.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

/// Phase 144, Job 1. Kotlin reaches the server for a locally authored voice
/// from a **sweep**: `UploadManager.uploadNews()` reads
/// `VoicesRepositoryImpl.getNewsForUpload()` — the whole `news` table minus
/// guests — and `AutoSyncWorker:130` and `UserDataWorker:40` run it on a
/// schedule, off nothing the post itself did. There is no write-time upload in
/// the Kotlin at all: composing a voice writes a row and waits for the sync.
///
/// The port inverted that. `VoicesActions` enqueues at write time — better
/// when it runs — and **nothing swept**, so a voice whose one enqueue never
/// ran sat on the handset indefinitely. `VoicesActions.queuePending` returns 0
/// when `serverConfigProvider` is null (there is no endpoint to queue
/// against) while its callers still report success, which is one way to reach
/// that state without any failure being visible.
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

  Future<PlanetPrefs> testPrefs() async =>
      PlanetPrefs(await SharedPreferences.getInstance());

  Future<ProviderContainer> containerFor({
    DeviceIdentitySource identity = testDeviceIdentity,
    ServerConfig? serverConfig = config,
  }) async {
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(api),
        planetPrefsProvider.overrideWithValue(await testPrefs()),
        deviceIdentitySourceProvider.overrideWithValue(identity),
        serverConfigProvider.overrideWith(
          () => _TestServerConfigNotifier(serverConfig),
        ),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(
            buildUserRow(id: 'org.couchdb.user:ada', name: 'ada'),
          ),
        ),
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
  }

  /// The reported state: a voice row on the handset with no `_id` and **no**
  /// outbox row — the post whose one write-time enqueue never ran.
  Future<String> seedStrandedVoice(ProviderContainer container) async {
    final id = await container
        .read(voicesRepositoryProvider)
        .createPost(
          message: 'The well is dry',
          userId: 'org.couchdb.user:ada',
          userName: 'ada',
        );
    expect(await db.outboxDao.forItem(VoicesUploader.type, id), isEmpty);
    return id;
  }

  group('foreground sync centre', () {
    test('syncAll delivers a voice nothing enqueued', () async {
      final container = await containerFor();
      final id = await seedStrandedVoice(container);
      acceptPosts();

      await container.read(dashboardSyncProvider.notifier).syncAll();

      final row = await container.read(voicesRepositoryProvider).getById(id);
      expect(
        row?.docId,
        'server-id-1',
        reason:
            'the sync path is the safety net Kotlin has in uploadNews(); the '
            'voice must reach the server without another write happening to '
            'sweep it up',
      );
      expect(row?.rev, '1-rev');
    });

    test('a voice composed with no server configured still goes out', () async {
      // `VoicesActions.queuePending` returns 0 when `serverConfigProvider` is
      // null, and `createPost` reports success anyway. That is the state the
      // gap leaves behind, reached the way a user reaches it.
      final unconfigured = await containerFor(serverConfig: null);
      final id = await unconfigured
          .read(voicesActionsProvider)
          .createPost('The well is dry');
      expect(id, isNotNull, reason: 'the post was reported as saved');
      expect(await db.outboxDao.forItem(VoicesUploader.type, id!), isEmpty);

      final configured = await containerFor();
      acceptPosts();
      await configured.read(dashboardSyncProvider.notifier).syncAll();

      expect(
        (await configured.read(voicesRepositoryProvider).getById(id))?.docId,
        'server-id-1',
      );
    });

    test('the sweep runs even when every table pull fails', () async {
      // `UserDataWorker:40` sweeps inside its own `runCatching` and inspects
      // no pull, so the sweep is not conditional on one succeeding — unlike
      // the telemetry uploads beside it, which are gated on `successCount`.
      final container = await containerFor();
      final id = await seedStrandedVoice(container);
      acceptPosts();

      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(container.read(dashboardSyncProvider).successCount, 0);
      expect(
        (await container.read(voicesRepositoryProvider).getById(id))?.docId,
        'server-id-1',
      );
    });

    test('the write-time enqueue and the sweep post one document', () async {
      final container = await containerFor();
      acceptPosts();
      // The write-time call site, exactly as `voices_screen:88` makes it.
      final id = await container
          .read(voicesActionsProvider)
          .createPost('The well is dry');
      expect(
        await db.outboxDao.forItem(VoicesUploader.type, id!),
        hasLength(1),
      );

      // The sweep's own enqueue over the same still-undelivered row, taken at
      // the uploader rather than through `queuePendingVoices`, which drains as
      // well as queues. The first of the three protections:
      // `OutboxRepository.enqueue` keys on `(uploadType, itemId)`, so it
      // refreshes the queued row rather than adding a second. Kotlin has no
      // equivalent — its only defence is the predicate below.
      await container
          .read(voicesUploaderProvider)
          .queuePending(config: config, userId: 'ada');
      expect(
        await db.outboxDao.forItem(VoicesUploader.type, id),
        hasLength(1),
        reason: 'a sweep over an already-queued voice must not add a row',
      );

      await container
          .read(outboxDrainerProvider)
          .drain(authHeader: 'Basic test');
      verify(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).called(1);

      // The second protection, and Kotlin's only one: `markNewsUploaded`
      // stamps `_id`/`_rev` and clears the edited flag, which takes the row
      // out of `pendingUploads`. A later sweep is a no-op, not a duplicate.
      expect(
        await container.read(voicesRepositoryProvider).pendingUploads(),
        isEmpty,
      );
      await container.read(dashboardSyncProvider.notifier).queuePendingVoices();
      verifyNoMoreInteractions(api);
    });

    test('a re-enqueue mid-flight does not post a second voice', () async {
      // `OutboxRepository.enqueue` puts an `in_progress` row back to `pending`
      // so a payload edited mid-flight is not lost, and `markCompleted` is
      // `deleteIfInProgress` — so the send that just succeeded deletes
      // nothing, the row survives `pending` with the same body, and the next
      // drain posts a **second** `news` document. Right for derived state,
      // wrong for an append, and a voice with no `_id` is an append.
      //
      // Reachable as soon as a sweep drains while the app is interactive: the
      // sync centre puts the POST on the wire and the user composes another
      // voice during it, whose `VoicesActions.queuePending` is unscoped.
      final container = await containerFor();
      final id = await seedStrandedVoice(container);
      final uploader = container.read(voicesUploaderProvider);
      var posts = 0;
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((_) async {
        posts++;
        await uploader.queuePending(config: config, userId: 'ada');
        return NetworkSuccess<Map<String, dynamic>>({
          'id': 'server-id-$posts',
          'rev': '$posts-rev',
        });
      });

      await container.read(dashboardSyncProvider.notifier).queuePendingVoices();
      await container
          .read(outboxDrainerProvider)
          .drain(authHeader: 'Basic test');
      // Whatever the resume drain does next must not re-send it.
      await container
          .read(outboxDrainerProvider)
          .drain(authHeader: 'Basic test');

      expect(posts, 1, reason: 'the voice was posted twice');
      expect(
        await db.outboxDao.forItem(VoicesUploader.type, id),
        isEmpty,
        reason: 'a delivered append must leave no replayable row behind',
      );
      expect(
        (await container.read(voicesRepositoryProvider).getById(id))?.docId,
        'server-id-1',
      );
    });

    test('an edit made while the POST is on the wire still goes out', () async {
      // The cost of the in-flight guard, and it must not be a loss.
      //
      // Kotlin does **not** lose this edit: `getNewsForUpload()` has no
      // predicate beyond the guest prefix, and `markNewsUploaded`
      // (`VoicesRepositoryImpl.kt:50-66`) writes back `imageUrls`, `_id`,
      // `_rev` and `images` and touches no edit marker — so the next
      // `uploadNews()` re-serializes the edited message with the fresh `_rev`
      // and it reaches the server as an update. The port's narrower
      // `pendingUploads` predicate means `markUploaded` clearing `isEdited`
      // would drop the row out of the pending set for good, and
      // `NewsMapper.fromDoc` writes `message` unconditionally, so the next
      // pull would overwrite the user's text with the server's older copy —
      // the edit destroyed on the device as well as never sent.
      final container = await containerFor();
      final id = await seedStrandedVoice(container);
      final repository = container.read(voicesRepositoryProvider);
      final posted = <Map<String, dynamic>>[];
      var posts = 0;
      when(
        () => api.postJsonObject(
          any(),
          any(),
          authHeader: any(named: 'authHeader'),
        ),
      ).thenAnswer((invocation) async {
        posts++;
        posted.add(invocation.positionalArguments[1] as Map<String, dynamic>);
        if (posts == 1) {
          // The user edits the post while its POST is in flight, exactly as
          // `VoicesActions.editPost` does — repository write, then the
          // unscoped re-queue the guard declines.
          await repository.editPost(newsId: id, message: 'The pump is broken');
          await container
              .read(voicesUploaderProvider)
              .queuePending(config: config, userId: 'ada');
        }
        return NetworkSuccess<Map<String, dynamic>>({
          'id': 'server-id',
          'rev': '$posts-rev',
        });
      });

      await container.read(dashboardSyncProvider.notifier).queuePendingVoices();

      expect(posts, 1, reason: 'the guard let a duplicate onto the wire');
      expect(
        (await repository.getById(id))?.message,
        'The pump is broken',
        reason: 'the local text is the fixture for the rest of this test',
      );
      expect(
        (await repository.pendingUploads()).map((row) => row.id),
        contains(id),
        reason:
            'the send carried a superseded body, so the row is not in sync '
            'with the server and must stay queueable',
      );

      // And the next sweep delivers it as an **update**, not a duplicate:
      // the row now carries the server id, so the payload does too.
      await container.read(dashboardSyncProvider.notifier).queuePendingVoices();

      expect(posts, 2);
      expect(posted.last['message'], 'The pump is broken');
      expect(
        posted.last['_id'],
        'server-id',
        reason: 'a second create would fork the post on the server',
      );
      expect(posted.last['_rev'], '1-rev');
      expect(await repository.pendingUploads(), isEmpty);
    });

    test('a sweep that throws does not fail the sync', () async {
      final container = await containerFor(identity: _ThrowingIdentitySource());
      await seedStrandedVoice(container);
      final identity =
          container.read(deviceIdentitySourceProvider)
              as _ThrowingIdentitySource;

      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(
        identity.reads,
        greaterThan(0),
        reason: 'the sweep never got far enough to throw',
      );
      final state = container.read(dashboardSyncProvider);
      expect(state.running, isFalse);
      expect(state.finishedAt, isNotNull);
      expect(state.completedCount, DashboardSyncArea.values.length);
    });

    test('the sweep reads device identity only when it has rows', () async {
      final identity = _CountingIdentitySource();
      final container = await containerFor(identity: identity);

      await container.read(dashboardSyncProvider.notifier).queuePendingVoices();

      expect(
        identity.reads,
        0,
        reason:
            '`queuePending` only reads identity when it has rows to queue, so '
            'a pass with nothing to send makes no platform-channel call',
      );
    });
  });

  /// **Reachability, not behaviour.** [sweepPendingVoices] is exercised
  /// directly below, which is the shape Phase 113 warned about: a function can
  /// be ported, tested and green while nothing in the app calls it.
  /// `executeBackgroundTask` needs a Flutter binding, real preferences and a
  /// WorkManager engine, so its wiring is read from source instead.
  ///
  /// The bounds matter: the slice ends at `@visibleForTesting`, which is where
  /// the top-level declarations begin, so the function's *own* declaration
  /// cannot satisfy the `contains` — the failure mode Phase 134 found in its
  /// own first cut.
  test('the headless path calls the voices sweep from drainOutbox', () {
    final source = File('lib/background_entrypoint.dart').readAsStringSync();
    // Bounded to the runner's argument list. The end bound is the *first*
    // `@visibleForTesting`, which is where the top-level declarations begin,
    // so `sweepPendingVoices`'s own declaration cannot satisfy the `contains`
    // below — the failure mode Phase 134 found in its own first cut, where the
    // range ran to end of file and the assertion could never fail. It stays
    // sound only while the doc comment above that annotation does not itself
    // write `sweepPendingVoices(`; the assertion on `sweepPendingSubmissions(`
    // below would catch a slip, since both names live under the same rule.
    final wiring = source.substring(
      source.indexOf('BackgroundTaskRunner('),
      source.indexOf('@visibleForTesting'),
    );

    expect(
      wiring,
      contains('sweepPendingVoices('),
      reason: 'nothing in the headless path calls sweepPendingVoices',
    );

    final swept = wiring.indexOf('sweepPendingVoices(');
    final drained = wiring.indexOf('drainer.drain(');
    final submissions = wiring.indexOf('sweepPendingSubmissions(');
    expect(drained, greaterThan(-1), reason: 'drainer.drain moved');
    expect(
      swept,
      lessThan(submissions),
      reason:
          'both Kotlin workers run uploadNews() before uploadSubmissions() '
          '(AutoSyncWorker:130 before :136, UserDataWorker:40 before :48), '
          'and the doc comments cite that ordering as the reason for the '
          'placement — a claim nothing asserted until now',
    );
    expect(
      swept,
      lessThan(drained),
      reason:
          'the sweep must queue before the drain that carries it, or the rows '
          'wait for the next invocation',
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

  group('headless path', () {
    test('sweepPendingVoices queues a stranded voice', () async {
      final container = await containerFor();
      final id = await seedStrandedVoice(container);

      await sweepPendingVoices(container, config: config, userId: null);

      expect(await db.outboxDao.forItem(VoicesUploader.type, id), hasLength(1));
    });

    test('a throwing sweep is swallowed', () async {
      final container = await containerFor(identity: _ThrowingIdentitySource());
      await seedStrandedVoice(container);

      await expectLater(
        sweepPendingVoices(container, config: config, userId: null),
        completes,
      );
    });
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}

/// Reproduces the one failure `PlatformDeviceIdentitySource.read` really has:
/// a headless WorkManager engine with no primed cache rethrows
/// (`device_identity.dart:89`), and `queuePending` reads identity before it
/// enqueues anything.
class _ThrowingIdentitySource implements DeviceIdentitySource {
  int reads = 0;

  @override
  Future<DeviceIdentity> read() async {
    reads++;
    throw StateError('no platform channel and no primed cache');
  }
}

class _CountingIdentitySource implements DeviceIdentitySource {
  int reads = 0;

  @override
  Future<DeviceIdentity> read() async {
    reads++;
    return testDeviceIdentity.read();
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
