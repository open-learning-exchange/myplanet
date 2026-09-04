import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/providers/dashboard_sync_provider.dart';
import 'package:myplanet/providers/sync_state.dart';
import 'package:myplanet/ui/sync/sync_center_screen.dart';

import '../support/widget_harness.dart';

class _DashboardSyncFixture extends DashboardSyncNotifier {
  _DashboardSyncFixture(this.fixture);

  final DashboardSyncState fixture;
  DashboardSyncArea? retriedArea;
  int syncAllCalls = 0;

  @override
  DashboardSyncState build() => fixture;

  @override
  Future<void> retry(DashboardSyncArea area) async {
    retriedArea = area;
  }

  @override
  Future<void> syncAll() async {
    syncAllCalls++;
  }
}

class _LastSyncFixture extends LastSyncNotifier {
  _LastSyncFixture(this.timestamp);

  final int timestamp;

  @override
  int build() => timestamp;
}

void main() {
  DashboardSyncState stateWith({
    bool running = false,
    List<DashboardSyncItem>? items,
    bool hasRun = true,
  }) => DashboardSyncState(
    items:
        items ??
        [
          for (final area in DashboardSyncArea.values)
            DashboardSyncItem(
              area: area,
              status: DashboardSyncStatus.succeeded,
              savedCount: area.index,
            ),
        ],
    running: running,
    startedAt: hasRun ? DateTime.utc(2026, 8, 12) : null,
    finishedAt: hasRun ? DateTime.utc(2026, 8, 12, 1) : null,
  );

  Future<_DashboardSyncFixture> pump(
    WidgetTester tester,
    DashboardSyncState state, {
    int lastSync = 0,
  }) async {
    late _DashboardSyncFixture fixture;
    await tester.pumpWidget(
      wrapScreen(
        const SyncCenterScreen(),
        overrides: [
          dashboardSyncProvider.overrideWith(() {
            fixture = _DashboardSyncFixture(state);
            return fixture;
          }),
          lastSyncProvider.overrideWith(() => _LastSyncFixture(lastSync)),
        ],
      ),
    );
    if (state.running) {
      await tester.pump();
    } else {
      await tester.pumpAndSettle();
    }
    return fixture;
  }

  testWidgets('renders all foreground sync areas', (tester) async {
    await pump(tester, stateWith(hasRun: false));

    expect(find.text('Sync center'), findsOneWidget);
    expect(find.text('Ready to sync'), findsOneWidget);
    expect(find.text('Resources'), findsOneWidget);
    expect(find.text('Courses'), findsOneWidget);
    expect(find.text('Teams'), findsOneWidget);
    expect(find.text('Events'), findsOneWidget);
    expect(find.text('Surveys'), findsOneWidget);

    await tester.drag(find.byType(CustomScrollView), const Offset(0, -500));
    await tester.pumpAndSettle();

    expect(find.text('Voices'), findsOneWidget);
    expect(find.text('Feedback'), findsOneWidget);
    expect(find.text('AI chat'), findsOneWidget);
    expect(find.text('My health'), findsOneWidget);
  });

  testWidgets('summarizes a completely successful run', (tester) async {
    await pump(
      tester,
      stateWith(),
      lastSync: DateTime.utc(2026, 8, 12).millisecondsSinceEpoch,
    );

    // Derived from the enum: hard-coding the count made this fail the moment an
    // area was added, which says nothing about the summary being right.
    expect(
      find.text(
        'All ${DashboardSyncArea.values.length} areas synced successfully',
      ),
      findsOneWidget,
    );
    expect(find.textContaining('Up to date'), findsOneWidget);
    expect(find.byIcon(Icons.check_circle), findsWidgets);
  });

  testWidgets('shows aggregate failures and offers an isolated retry', (
    tester,
  ) async {
    final fixture = await pump(
      tester,
      stateWith(
        items: const [
          DashboardSyncItem(
            area: DashboardSyncArea.resources,
            status: DashboardSyncStatus.succeeded,
            savedCount: 4,
          ),
          DashboardSyncItem(
            area: DashboardSyncArea.courses,
            status: DashboardSyncStatus.failed,
            message: 'offline',
          ),
        ],
      ),
    );

    expect(find.text('Sync finished: 1 succeeded, 1 failed'), findsOneWidget);
    expect(find.textContaining('Sync failed: offline'), findsOneWidget);

    await tester.tap(find.byTooltip('Retry'));
    await tester.pump();

    expect(fixture.retriedArea, DashboardSyncArea.courses);
  });

  testWidgets('sync-all button invokes the coordinator', (tester) async {
    final fixture = await pump(tester, stateWith(hasRun: false));

    await tester.tap(find.text('Sync all'));
    await tester.pump();

    expect(fixture.syncAllCalls, 1);
  });

  testWidgets('running state disables sync all and displays progress', (
    tester,
  ) async {
    final fixture = await pump(
      tester,
      stateWith(
        running: true,
        items: const [
          DashboardSyncItem(
            area: DashboardSyncArea.resources,
            status: DashboardSyncStatus.succeeded,
          ),
          DashboardSyncItem(
            area: DashboardSyncArea.courses,
            status: DashboardSyncStatus.running,
          ),
          DashboardSyncItem(area: DashboardSyncArea.teams),
        ],
      ),
    );

    expect(find.text('Syncing 1 of 3 areas'), findsOneWidget);
    expect(find.text('Syncing…'), findsWidgets);

    await tester.tap(find.widgetWithText(FloatingActionButton, 'Syncing…'));
    await tester.pump();

    expect(fixture.syncAllCalls, 0);
  });
}
