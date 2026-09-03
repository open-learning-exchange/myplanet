import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/dashboard_sync_provider.dart';

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
