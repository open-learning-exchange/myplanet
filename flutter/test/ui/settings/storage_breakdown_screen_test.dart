import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/system/disk_stats.dart';
import 'package:myplanet/data/local/offline_resource_item.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/resources_providers.dart';
import 'package:myplanet/repository/resources_repository.dart';
import 'package:myplanet/ui/settings/storage_breakdown_screen.dart';

import '../../support/widget_harness.dart';

class _FakeDiskStats implements DiskStats {
  _FakeDiskStats(this.stats);
  final ({int totalBytes, int availableBytes}) stats;

  @override
  Future<({int totalBytes, int availableBytes})> storageStats() async => stats;
}

class _MockResourcesRepository extends Mock implements ResourcesRepository {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _MockResourcesRepository repo;

  setUp(() {
    repo = _MockResourcesRepository();
    // The breakdown screen sizes each category through the repository seam —
    // a real `ole/` walk hangs under the test binding's fake clock, so an empty
    // answer (no offline files) is what an empty device looks like.
    registerFallbackValue(
      const OfflineResourceItem(
        resourceId: '_',
        title: '_',
        filePaths: [],
        totalSizeBytes: 0,
      ),
    );
    when(
      () => repo.getOfflineResourceItems(
        extensions: any(named: 'extensions'),
        allKnownExtensions: any(named: 'allKnownExtensions'),
        unknownTitle: any(named: 'unknownTitle'),
      ),
    ).thenAnswer((_) async => const []);
  });

  testWidgets('shows available/total space and a free-up-space button', (
    tester,
  ) async {
    final disk = _FakeDiskStats((totalBytes: 1000, availableBytes: 400));
    when(
      () => repo.freeUpSpace(),
    ).thenAnswer((_) async => (deletedFiles: 0, freedBytes: 0));

    await tester.pumpWidget(
      wrapScreen(
        const StorageBreakdownScreen(),
        overrides: [
          diskStatsProvider.overrideWithValue(disk),
          resourcesRepositoryProvider.overrideWithValue(repo),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The available/total line is rendered from the disk-stats seam — the
    // exact byte formatting is `formatFileSize`'s concern (covered by its
    // own tests), so here we only assert the label and that both figures
    // appear, not the compact-number form `NumberFormat.compact` picks.
    expect(find.textContaining('Available space'), findsOneWidget);
    expect(find.textContaining('400'), findsOneWidget);
    expect(find.textContaining('1K'), findsOneWidget);
    expect(find.text('Free up space'), findsOneWidget);
  });

  testWidgets('free up space confirms before clearing', (tester) async {
    final disk = _FakeDiskStats((totalBytes: 1000, availableBytes: 400));
    when(
      () => repo.freeUpSpace(),
    ).thenAnswer((_) async => (deletedFiles: 2, freedBytes: 7));

    await tester.pumpWidget(
      wrapScreen(
        const StorageBreakdownScreen(),
        overrides: [
          diskStatsProvider.overrideWithValue(disk),
          resourcesRepositoryProvider.overrideWithValue(repo),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Free up space'));
    await tester.pumpAndSettle();

    // The confirmation dialog gates the destructive action — matching the
    // Kotlin `setPositiveButton(R.string.yes) { … freeUpSpace() }`.
    expect(
      find.textContaining('delete all downloaded resources'),
      findsOneWidget,
    );
    expect(find.text('Yes'), findsOneWidget);
    expect(find.text('No'), findsOneWidget);

    await tester.tap(find.text('Yes'));
    await tester.pumpAndSettle();

    verify(() => repo.freeUpSpace()).called(1);
    // The summary snackbar carries the freed-bytes count back, the way the
    // Kotlin's `WorkInfo.outputData` fed `storage_freed_summary`.
    expect(find.textContaining('Freed'), findsOneWidget);
  });
}
