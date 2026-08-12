import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/dashboard_sync_provider.dart';
import 'package:myplanet/providers/health_provider.dart';
import 'package:myplanet/providers/sync_state.dart';
import 'package:myplanet/repository/outbox_drainer.dart';
import 'package:myplanet/repository/outbox_repository.dart';

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

  group('drainOutbox', () {
    /// "Sync now" has to push as well as pull: Kotlin's forced sync calls
    /// `startUpload`, and before this the outbox waited for an app resume, so a
    /// user who reconnected and pressed Sync sent nothing.
    late _RecordingDrainer drainer;

    ProviderContainer containerWith({
      required DashboardSyncState state,
      ServerConfig? config = const ServerConfig(
        serverUrl: 'http://planet.example',
        pin: '1234',
        couchDbUrl: 'http://planet.example:5984',
      ),
    }) {
      drainer = _RecordingDrainer();
      final container = ProviderContainer(
        overrides: [
          dashboardSyncProvider.overrideWith(() => _SeededSync(state)),
          serverConfigProvider.overrideWith(() => _StubConfig(config)),
          outboxDrainerProvider.overrideWithValue(drainer),
        ],
      );
      addTearDown(container.dispose);
      return container;
    }

    DashboardSyncState stateWhere(DashboardSyncStatus status) =>
        DashboardSyncState(
          items: [
            for (final area in DashboardSyncArea.values)
              DashboardSyncItem(area: area, status: status),
          ],
        );

    test('drains once a pull has reached the server', () async {
      final container = containerWith(
        state: stateWhere(DashboardSyncStatus.succeeded),
      );

      await container.read(dashboardSyncProvider.notifier).drainOutbox();

      expect(drainer.calls, 1);
      // The credential must travel as a header: the stored endpoint is
      // deliberately credential-free, so an unauthenticated send would come
      // back 401 and be classified permanent.
      expect(drainer.authHeaders.single, isNotNull);
      expect(drainer.authHeaders.single, startsWith('Basic '));
    });

    test('does not spend a retry attempt when every area failed', () async {
      final container = containerWith(
        state: stateWhere(DashboardSyncStatus.failed),
      );

      await container.read(dashboardSyncProvider.notifier).drainOutbox();

      // A drain with no route to the server would burn one of the five
      // attempts and push the backoff out, delaying the write once the network
      // does come back.
      expect(drainer.calls, 0);
    });

    test('does nothing before the server handshake', () async {
      final container = containerWith(
        state: stateWhere(DashboardSyncStatus.succeeded),
        config: null,
      );

      await container.read(dashboardSyncProvider.notifier).drainOutbox();

      expect(drainer.calls, 0);
    });

    test('a real sync run reaches the drain', () async {
      // The gate tests above call drainOutbox directly; this one drives the
      // wiring, which is what was actually missing — the run finished and
      // nothing pushed. `retry` exercises the same tail as `syncAll` with one
      // area provider instead of nine.
      drainer = _RecordingDrainer();
      final container = ProviderContainer(
        overrides: [
          serverConfigProvider.overrideWith(
            () => _StubConfig(
              const ServerConfig(
                serverUrl: 'http://planet.example',
                pin: '1234',
                couchDbUrl: 'http://planet.example:5984',
              ),
            ),
          ),
          outboxDrainerProvider.overrideWithValue(drainer),
          healthSyncProvider.overrideWith(_SucceedingHealthSync.new),
          lastSyncProvider.overrideWith(_StubLastSync.new),
        ],
      );
      addTearDown(container.dispose);

      await container
          .read(dashboardSyncProvider.notifier)
          .retry(DashboardSyncArea.health);

      expect(
        container.read(dashboardSyncProvider).successCount,
        1,
        reason: 'the health pull should have succeeded',
      );
      expect(drainer.calls, 1, reason: 'a finished sync run must push too');
    });

    test('a drain failure does not surface as a sync failure', () async {
      final container = containerWith(
        state: stateWhere(DashboardSyncStatus.succeeded),
      );
      drainer.throwOnDrain = true;

      await expectLater(
        container.read(dashboardSyncProvider.notifier).drainOutbox(),
        completes,
      );
      expect(
        container.read(dashboardSyncProvider).failureCount,
        0,
        reason: 'the pulls succeeded; only the push failed',
      );
    });
  });
}

/// Seeds the notifier with a finished run so [DashboardSyncNotifier.drainOutbox]
/// can be exercised without standing up all nine area sync providers.
class _SeededSync extends DashboardSyncNotifier {
  _SeededSync(this.seed);

  final DashboardSyncState seed;

  @override
  DashboardSyncState build() => seed;
}

class _StubConfig extends ServerConfigNotifier {
  _StubConfig(this.config);

  final ServerConfig? config;

  @override
  ServerConfig? build() => config;
}

class _SucceedingHealthSync extends HealthSyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) async => const SyncComplete(3);
}

/// `SyncNotifier.sync` records a successful pull's timestamp through
/// `lastSyncProvider`, which would reach `SharedPreferences` under `flutter
/// test`. This keeps it in memory.
class _StubLastSync extends LastSyncNotifier {
  @override
  int build() => 0;

  @override
  Future<void> recordSuccess({DateTime? at}) async {
    state =
        (at ?? DateTime.fromMillisecondsSinceEpoch(1)).millisecondsSinceEpoch;
  }
}

class _MockPlanetApi extends Mock implements PlanetApi {}

class _MockOutboxRepository extends Mock implements OutboxRepository {}

/// [drain] is overridden wholesale, so the api and repository handed to `super`
/// are never touched — they exist only to satisfy the constructor.
class _RecordingDrainer extends OutboxDrainer {
  _RecordingDrainer() : super(_MockPlanetApi(), _MockOutboxRepository());

  int calls = 0;
  final List<String?> authHeaders = [];
  bool throwOnDrain = false;

  @override
  Future<List<OutboxOutcome>> drain({String? authHeader}) async {
    calls++;
    authHeaders.add(authHeader);
    if (throwOnDrain) throw StateError('network gone');
    return const [];
  }
}
