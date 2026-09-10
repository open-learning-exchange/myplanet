import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/core/providers/provider_retry.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_sync_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/shelf_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../repository/device_identity_fixture.dart';
import '../support/widget_harness.dart';

void main() {
  group('DashboardSyncState', () {
    test('idle state contains every area in declaration order', () {
      final state = DashboardSyncState.idle();

      expect(state.items, hasLength(DashboardSyncArea.values.length));
      expect(state.items.map((item) => item.area), DashboardSyncArea.values);
      expect(
        state.items.every((item) => item.status == DashboardSyncStatus.waiting),
        isTrue,
      );
      expect(state.completedCount, 0);
      expect(state.progress, 0);
    });

    test('the two load-bearing area orderings hold', () {
      // `syncAll` iterates `DashboardSyncArea.values`, so declaration order is
      // execution order, and two positions carry real behaviour:
      //
      // * `tabletUsers` before `shelf` — a shelf document is keyed by its
      //   owner's CouchDB id, and `ShelfSyncRepository._localUserId` resolves
      //   that to the local `users` row every reader scopes by. With no row
      //   the stamp falls back to the raw id, which for a member registered on
      //   this device matches nothing.
      // * `shelf` after `resources` and `courses` — both of those prune with
      //   `deleteNotIn`, so a stamp written before them can be deleted.
      //
      // The test above (`state.items.map(…) == DashboardSyncArea.values`) is
      // tautological with respect to order: an alphabetising refactor would
      // pass it and break both invariants silently.
      int index(DashboardSyncArea area) =>
          DashboardSyncArea.values.indexOf(area);

      expect(
        index(DashboardSyncArea.tabletUsers),
        lessThan(index(DashboardSyncArea.shelf)),
      );
      expect(
        index(DashboardSyncArea.resources),
        lessThan(index(DashboardSyncArea.shelf)),
      );
      expect(
        index(DashboardSyncArea.courses),
        lessThan(index(DashboardSyncArea.shelf)),
      );
    });

    test('aggregate counts distinguish success and failure', () {
      final state = DashboardSyncState(
        items: const [
          DashboardSyncItem(
            area: DashboardSyncArea.resources,
            status: DashboardSyncStatus.succeeded,
            savedCount: 12,
          ),
          DashboardSyncItem(
            area: DashboardSyncArea.courses,
            status: DashboardSyncStatus.failed,
            message: 'offline',
          ),
          DashboardSyncItem(
            area: DashboardSyncArea.teams,
            status: DashboardSyncStatus.running,
          ),
          DashboardSyncItem(area: DashboardSyncArea.events),
        ],
        running: true,
      );

      expect(state.completedCount, 2);
      expect(state.successCount, 1);
      expect(state.failureCount, 1);
      expect(state.progress, 0.5);
      expect(state.running, isTrue);
    });

    test('copyWith retains timestamps unless explicitly cleared', () {
      final started = DateTime.utc(2026, 8, 12, 10);
      final finished = DateTime.utc(2026, 8, 12, 11);
      final state = DashboardSyncState.idle().copyWith(
        startedAt: started,
        finishedAt: finished,
      );

      final running = state.copyWith(running: true);
      expect(running.startedAt, started);
      expect(running.finishedAt, finished);

      final restarted = running.copyWith(clearFinishedAt: true);
      expect(restarted.startedAt, started);
      expect(restarted.finishedAt, isNull);
    });
  });

  group('shelf push before the pull phase', _shelfPushTests);

  group('DashboardSyncItem', () {
    test('copyWith keeps its area and supports clearing an error', () {
      const failed = DashboardSyncItem(
        area: DashboardSyncArea.health,
        status: DashboardSyncStatus.failed,
        message: 'server unavailable',
      );

      final retrying = failed.copyWith(
        status: DashboardSyncStatus.running,
        clearMessage: true,
      );

      expect(retrying.area, DashboardSyncArea.health);
      expect(retrying.status, DashboardSyncStatus.running);
      expect(retrying.message, isNull);
    });

    test('copyWith records a terminal saved count', () {
      const running = DashboardSyncItem(
        area: DashboardSyncArea.surveys,
        status: DashboardSyncStatus.running,
      );

      final complete = running.copyWith(
        status: DashboardSyncStatus.succeeded,
        savedCount: 37,
      );

      expect(complete.savedCount, 37);
      expect(complete.status, DashboardSyncStatus.succeeded);
    });
  });
}

/// `syncAll`'s shelf push, ported from `SyncManager.pushCurrentUserShelf`
/// (upstream `9255eac`).
///
/// The pass is driven for real here rather than asserted structurally: the
/// defect this covers is not "the push is wrong", it is "the push is not
/// called", which only running `syncAll` can show. The sixteen table pulls are
/// left to fail, because what is under test is the step that runs *before*
/// them.
///
/// What stops them reaching the network is worth stating precisely, because
/// the obvious explanation is wrong: mocktail's default for an unstubbed call
/// is to **return null**, not to throw. The pulls die on the implicit-downcast
/// `TypeError` that null produces where a `Future<NetworkResult<…>>` is
/// declared, which `SyncNotifier.sync`'s `catch (error)` records as errored.
/// So this only works while every mocked member returns a non-nullable type —
/// a `void` or nullable-returning one would quietly hand back null and let the
/// code under test carry on past where it looks like it stops.
void _shelfPushTests() {
  const config = ServerConfig(
    serverUrl: 'https://planet.example.org',
    pin: '1234',
    couchDbUrl: 'https://satellite:1234@planet.example.org:443',
  );

  late AppDatabase db;

  setUpAll(() => registerFallbackValue(config));

  late PlanetPrefs prefs;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    db = AppDatabase.memory();
    prefs = PlanetPrefs(await SharedPreferences.getInstance());
  });
  tearDown(() => db.close());

  Future<ProviderContainer> containerFor({
    required MockShelfRepository shelf,
    String? couchId = 'org.couchdb.user:ada',
    ServerConfig? serverConfig = config,
  }) async {
    final container = ProviderContainer(
      retry: noProviderRetry,
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        planetApiProvider.overrideWithValue(_UnusablePlanetApi()),
        // Phase 134: `syncAll` now sweeps pending submissions, and
        // `outboxDrainerProvider` builds every registered handler — several of
        // which reach `planetPrefsProvider`. Without this the sweep throws
        // `UnimplementedError`, its own `catch` swallows it, and these two
        // tests stop covering the second half of the pass while still passing.
        planetPrefsProvider.overrideWithValue(prefs),
        deviceIdentitySourceProvider.overrideWithValue(testDeviceIdentity),
        shelfRepositoryProvider.overrideWithValue(shelf),
        serverConfigProvider.overrideWith(
          () => _TestServerConfigNotifier(serverConfig),
        ),
        sessionProvider.overrideWith(
          () => _TestSessionNotifier(
            buildUserRow(id: 'user-ada').copyWith(couchId: Value(couchId)),
          ),
        ),
      ],
    );
    addTearDown(container.dispose);
    return container;
  }

  test('syncAll pushes the local shelf before the first table pull', () async {
    final shelf = MockShelfRepository();
    final container = await containerFor(shelf: shelf);
    List<DashboardSyncStatus>? statusesAtPushTime;
    List<Object?>? pushedWith;
    when(
      () => shelf.upload(
        config: any(named: 'config'),
        userId: any(named: 'userId'),
        shelfDocId: any(named: 'shelfDocId'),
      ),
    ).thenAnswer((invocation) async {
      pushedWith = [
        invocation.namedArguments[const Symbol('config')],
        invocation.namedArguments[const Symbol('userId')],
        invocation.namedArguments[const Symbol('shelfDocId')],
      ];
      statusesAtPushTime = container
          .read(dashboardSyncProvider)
          .items
          .map((item) => item.status)
          .toList(growable: false);
      return const SyncComplete(1);
    });

    await container.read(dashboardSyncProvider.notifier).syncAll();

    expect(pushedWith, [
      config,
      'user-ada',
      'org.couchdb.user:ada',
    ], reason: 'syncAll never pushed the shelf');
    // Every area still waiting when the push ran is the ordering assertion:
    // Kotlin runs `pushCurrentUserShelf()` before the parallel table set, so a
    // shelf change made since the last sync reaches the server before the pull
    // that would otherwise read the server's older copy back over it.
    expect(statusesAtPushTime, isNotNull);
    expect(
      statusesAtPushTime,
      everyElement(DashboardSyncStatus.waiting),
      reason: 'the shelf push must run before any area starts',
    );
  });

  test('a failing shelf push does not stop the sync', () async {
    final shelf = MockShelfRepository();
    final container = await containerFor(shelf: shelf);
    when(
      () => shelf.upload(
        config: any(named: 'config'),
        userId: any(named: 'userId'),
        shelfDocId: any(named: 'shelfDocId'),
      ),
    ).thenThrow(StateError('offline'));

    await container.read(dashboardSyncProvider.notifier).syncAll();

    // Kotlin logs and continues; the pass must still reach its terminal state.
    final state = container.read(dashboardSyncProvider);
    expect(state.running, isFalse);
    expect(state.finishedAt, isNotNull);
    expect(state.completedCount, DashboardSyncArea.values.length);
  });

  test('a user with no CouchDB id is not pushed', () async {
    final shelf = MockShelfRepository();
    final container = await containerFor(shelf: shelf, couchId: null);

    await container.read(dashboardSyncProvider.notifier).pushCurrentUserShelf();

    verifyNever(
      () => shelf.upload(
        config: any(named: 'config'),
        userId: any(named: 'userId'),
        shelfDocId: any(named: 'shelfDocId'),
      ),
    );
  });

  test('no configured server means no push', () async {
    final shelf = MockShelfRepository();
    final container = await containerFor(shelf: shelf, serverConfig: null);

    await container.read(dashboardSyncProvider.notifier).pushCurrentUserShelf();

    verifyNever(
      () => shelf.upload(
        config: any(named: 'config'),
        userId: any(named: 'userId'),
        shelfDocId: any(named: 'shelfDocId'),
      ),
    );
  });

  group('the area order is the sync schedule', () {
    // Pinned because the order *is* behaviour: the notifier walks
    // `DashboardSyncArea.values` sequentially, so a hanging walk stops every
    // area after it. With `courses` second, a courses walk that never
    // completed took teams, events and surveys down with it on a real device.
    test('shelf runs last, after the areas whose rows it stamps', () {
      expect(DashboardSyncArea.values.last, DashboardSyncArea.shelf);
    });

    test('courses runs late, and before shelf', () {
      final order = DashboardSyncArea.values;
      final courses = order.indexOf(DashboardSyncArea.courses);
      final shelf = order.indexOf(DashboardSyncArea.shelf);
      expect(courses, lessThan(shelf));
      // Kotlin has courses 13th of 14 in its parallel batch. "Late" here means
      // after the cheap areas, so one slow walk cannot hide the rest.
      expect(courses, greaterThan(order.length ~/ 2));
    });

    test('resources runs before both courses and shelf', () {
      final order = DashboardSyncArea.values;
      expect(
        order.indexOf(DashboardSyncArea.resources),
        lessThan(order.indexOf(DashboardSyncArea.courses)),
      );
      expect(
        order.indexOf(DashboardSyncArea.resources),
        lessThan(order.indexOf(DashboardSyncArea.shelf)),
      );
    });

    test('every area is scheduled exactly once', () {
      expect(
        DashboardSyncArea.values.toSet(),
        hasLength(DashboardSyncArea.values.length),
      );
    });
  });
}

class MockShelfRepository extends Mock implements ShelfRepository {}

/// Every call throws, which is what keeps the sixteen table pulls off the
/// network: `SyncNotifier.sync` catches it and records the area as errored.
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
