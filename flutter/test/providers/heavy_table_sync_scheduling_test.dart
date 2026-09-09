import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/background/heavy_table_scheduler.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/sync/heavy_table_sync.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_sync_provider.dart';
import 'package:myplanet/providers/heavy_sync_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/shelf_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

/// The interactive sync's end of the heavy-table path: the flag the background
/// worker reads to stand down, and the scheduling that is the port's
/// `SyncManager:209`.
///
/// Driven for real rather than asserted structurally, for the reason the shelf
/// push tests give: the defect this covers is "the tables are never
/// scheduled", which only running `syncAll` can show. The table pulls
/// themselves are left to fail — an unusable `PlanetApi` keeps them off the
/// network — because what is under test is what happens around them.
void main() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  late AppDatabase db;
  late PlanetPrefs prefs;
  late _RecordingScheduler scheduler;

  setUpAll(() => registerFallbackValue(config));

  setUp(() async {
    SharedPreferences.setMockInitialValues(const {});
    db = AppDatabase.memory();
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
    scheduler = _RecordingScheduler(prefs);
  });
  tearDown(() => db.close());

  Future<ProviderContainer> containerFor({
    ServerConfig? serverConfig = config,
    MockShelfRepository? shelf,
  }) async {
    final container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(_UnusablePlanetApi()),
        planetPrefsProvider.overrideWithValue(prefs),
        deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
        heavyTableWorkSchedulerProvider.overrideWithValue(scheduler),
        if (shelf != null) shelfRepositoryProvider.overrideWithValue(shelf),
        serverConfigProvider.overrideWith(
          () => _TestServerConfigNotifier(serverConfig),
        ),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(
            buildUserRow(
              id: 'user-ada',
            ).copyWith(couchId: const Value('org.couchdb.user:ada')),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  test('syncAll schedules every heavy table when the pass ends', () async {
    final container = await containerFor();

    await container.read(dashboardSyncProvider.notifier).syncAll();

    // Port of `HeavyTableSyncWorker.schedule(context)` at `SyncManager:209`.
    // Remove the call from `syncAll` and this fails; it is the only thing
    // standing between the port and the state it was in before this phase,
    // where `courses_progress` and `login_activities` were pulled by nothing
    // at all.
    expect(scheduler.enqueued, HeavyTableSync.tables);
  });

  test('it schedules even when every area failed', () async {
    // Every area errors here (the api is unusable), and the tables are still
    // scheduled. Gating on `successCount > 0` — the rule the telemetry uploads
    // follow — would be worse than useless: a pass that failed everywhere is
    // exactly when a resumable background walk should be queued.
    final container = await containerFor();

    await container.read(dashboardSyncProvider.notifier).syncAll();

    expect(container.read(dashboardSyncProvider).successCount, 0);
    expect(scheduler.enqueued, isNotEmpty);
  });

  test('every scheduled table has a writer wired to it', () async {
    // The reachability question, asked of this slice: a table `scheduleAll`
    // enqueues but `heavyTableSyncProvider` has no writer for is a job that
    // runs, walks nothing, and reports success for ever. Add a table to
    // `HeavyTableSync.tables` without wiring its repository and this fails.
    final container = await containerFor();

    expect(
      container.read(heavyTableSyncProvider).writableTables,
      containsAll(HeavyTableSync.tables),
    );
  });

  test('no configured server schedules nothing', () async {
    final container = await containerFor(serverConfig: null);

    await container.read(dashboardSyncProvider.notifier).syncAll();

    expect(scheduler.enqueued, isEmpty);
  });

  group('the headless entry point', _headlessEntryPointTests);

  group('the interactive-sync flag', () {
    test('is set for the duration of the pass and cleared after it', () async {
      final shelf = MockShelfRepository();
      DateTime? flagDuringPass;
      when(
        () => shelf.upload(
          config: any(named: 'config'),
          userId: any(named: 'userId'),
          shelfDocId: any(named: 'shelfDocId'),
        ),
      ).thenAnswer((_) async {
        flagDuringPass = prefs.interactiveSyncStartedAt;
        return const SyncComplete(1);
      });
      final container = await containerFor(shelf: shelf);

      await container.read(dashboardSyncProvider.notifier).syncAll();

      // The shelf push is the first thing `syncAll` does, so the flag has to
      // be set before it. This is what makes a heavy worker starting mid-sync
      // return retry instead of walking a table the sync is writing.
      expect(flagDuringPass, isNotNull);
      expect(prefs.interactiveSyncStartedAt, isNull);
    });

    test('is cleared before the tables are scheduled', () async {
      // Deliberately the opposite of the Kotlin's order, which enqueues at
      // `SyncManager:209` inside the `try` and only clears `isSyncing` in the
      // `finally` at `:233` — so a worker WorkManager starts promptly sees
      // `isMainSyncActive()` true and burns a `Result.retry()` on a
      // 30-second backoff having done nothing. Swap the two statements back
      // and this fails.
      final container = await containerFor();

      await container.read(dashboardSyncProvider.notifier).syncAll();

      expect(scheduler.enqueued, isNotEmpty);
      expect(
        scheduler.flagWhenEnqueued,
        everyElement(isNull),
        reason: 'a heavy job must not be enqueued while the flag still stands',
      );
    });
  });
}

void _headlessEntryPointTests() {
  final source = File('lib/background_entrypoint.dart').readAsStringSync();

  /// `executeBackgroundTask` needs a Flutter binding, real preferences and a
  /// WorkManager engine, so its dispatch cannot be driven from a unit test —
  /// which is why the sweeps in that file are extracted behind
  /// `@visibleForTesting` and why these three claims are pinned against the
  /// source instead. Each is a wiring fact whose loss is silent.
  test('dispatches a heavy task before anything else', () {
    final heavy = source.indexOf('heavyTableSyncTable(taskName)');
    final runner = source.indexOf('BackgroundTaskRunner(');

    expect(heavy, isNonNegative);
    // Ahead of the runner, which answers `true` for any task name it does not
    // recognise — a heavy task reaching it would be silently discarded.
    expect(heavy, lessThan(runner));
    // The reload is what lets this isolate see the UI isolate's writes: the
    // interactive-sync flag and the checkpoints. Without it `PlanetPrefs`
    // serves the snapshot it cached at `getInstance()`.
    expect(source, contains('prefs.reload()'));
  });

  test('resumes pending walks on every invocation', () {
    // Port of `ServerReachabilityWorker:201`.
    expect(source, contains('scheduleIfPending()'));
  });

  test('schedules all heavy tables after a clean sync', () {
    // Port of `SyncManager:209`, on the hook that fires only when every step
    // succeeded — the runner's closest thing to "the sync finished".
    expect(source, contains('scheduleAll()'));
  });

  test('keeps the submissions pull inline, and out of the heavy set', () {
    // The one heavy table the port deliberately does not move. Moving it was
    // the first cut, and `pending_submissions_sweep_test.dart` caught it: that
    // file pins the submissions *sweep* running before the submissions *pull*
    // in the same invocation, because `upsertDocuments` writes
    // `isUpdated: false` over every row it writes and keys each on the server
    // `_id` — so a pull landing first strips the dirty flag from a
    // server-originated sheet the user edited locally and it never uploads. A
    // background walk cannot be ordered against this invocation.
    //
    // Both halves are pinned, because either alone can drift: the step must
    // exist, and the table must stay out of the scheduled set — in it, it
    // would be walked twice per run and once without the ordering.
    final steps = source.substring(
      source.indexOf('syncSteps:'),
      source.indexOf('recordLastSync:'),
    );
    expect(steps, contains("'submissions'"));
    expect(steps, contains("'health'"));
    expect(HeavyTableSync.tables, isNot(contains('submissions')));
  });
}

/// Records what was enqueued, and what the interactive-sync flag said at that
/// moment — the ordering assertion above needs the second half.
class _RecordingScheduler implements HeavyTableWorkScheduler {
  _RecordingScheduler(this._prefs);

  final PlanetPrefs _prefs;
  final enqueued = <String>[];
  final flagWhenEnqueued = <DateTime?>[];

  @override
  Future<void> enqueueUnique(String table) async {
    enqueued.add(table);
    flagWhenEnqueued.add(_prefs.interactiveSyncStartedAt);
  }
}

class MockShelfRepository extends Mock implements ShelfRepository {}

/// Every call returns null, which the implicit downcast to
/// `Future<NetworkResult<…>>` turns into a `TypeError` — that is what keeps
/// the table pulls off the network here.
class _UnusablePlanetApi extends Mock implements PlanetApi {}

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
