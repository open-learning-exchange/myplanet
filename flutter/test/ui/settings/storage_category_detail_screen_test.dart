import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/offline_resource_item.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/repository/resources_repository.dart';
import 'package:myplanet/ui/settings/storage_breakdown_screen.dart';
import 'package:myplanet/ui/settings/storage_category_detail_screen.dart';

import '../../support/widget_harness.dart';

/// Mocks the repository so the screen's async load settles under
/// `pumpAndSettle` (a real repository walks the filesystem and queries drift
/// on a background isolate, which the binding's fake clock won't pump past an
/// indeterminate spinner). The real repository is covered by
/// `resources_repository_test.dart`.
class _MockResourcesRepository extends Mock implements ResourcesRepository {}

void main() {
  late _MockResourcesRepository repository;

  setUp(() {
    repository = _MockResourcesRepository();
    registerFallbackValue(
      const OfflineResourceItem(
        resourceId: '_',
        title: '_',
        filePaths: [],
        totalSizeBytes: 0,
      ),
    );
    when(
      () => repository.getOfflineResourceItems(
        extensions: any(named: 'extensions'),
        allKnownExtensions: any(named: 'allKnownExtensions'),
        unknownTitle: any(named: 'unknownTitle'),
      ),
    ).thenAnswer((_) async => const []);
  });

  testWidgets('lists the offline files in the category', (tester) async {
    when(
      () => repository.getOfflineResourceItems(
        extensions: any(named: 'extensions'),
        allKnownExtensions: any(named: 'allKnownExtensions'),
        unknownTitle: any(named: 'unknownTitle'),
      ),
    ).thenAnswer(
      (_) async => [
        const OfflineResourceItem(
          resourceId: 'res-1',
          title: 'Algebra',
          filePaths: ['/ole/res-1/lecture.mp4'],
          totalSizeBytes: 3,
        ),
        const OfflineResourceItem(
          resourceId: 'res-2',
          title: 'Biology',
          filePaths: ['/ole/res-2/lab.mp4'],
          totalSizeBytes: 3,
        ),
      ],
    );

    await tester.pumpWidget(
      wrapScreen(
        StorageCategoryDetailScreen(
          extra: StorageCategoryExtra(
            label: CategoryLabel.videos,
            extensions: videoExtensions.toList(),
          ),
        ),
        overrides: [
          resourcesRepositoryProvider.overrideWith((ref) => repository),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Algebra'), findsOneWidget);
    expect(find.text('Biology'), findsOneWidget);
  });

  testWidgets('select-all then delete forwards the items to the repository', (
    tester,
  ) async {
    final items = [
      const OfflineResourceItem(
        resourceId: 'res-1',
        title: 'Algebra',
        filePaths: ['/ole/res-1/lecture.mp4'],
        totalSizeBytes: 3,
      ),
    ];
    when(
      () => repository.getOfflineResourceItems(
        extensions: any(named: 'extensions'),
        allKnownExtensions: any(named: 'allKnownExtensions'),
        unknownTitle: any(named: 'unknownTitle'),
      ),
    ).thenAnswer((_) async => items);
    when(
      () => repository.deleteOfflineResources(any()),
    ).thenAnswer((_) async {});

    await tester.pumpWidget(
      wrapScreen(
        StorageCategoryDetailScreen(
          extra: StorageCategoryExtra(
            label: CategoryLabel.videos,
            extensions: videoExtensions.toList(),
          ),
        ),
        overrides: [
          resourcesRepositoryProvider.overrideWith((ref) => repository),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Select All'));
    await tester.pump();
    await tester.tap(find.text('Delete Selected'));
    await tester.pumpAndSettle();

    expect(find.text('Are you sure?'), findsOneWidget);
    await tester.tap(find.text('Yes'));
    await tester.pumpAndSettle();

    final captured = verify(
      () => repository.deleteOfflineResources(captureAny()),
    ).captured;
    expect(captured, hasLength(1));
    expect(
      (captured.single as List<OfflineResourceItem>).single.resourceId,
      'res-1',
    );
  });

  testWidgets('an empty category shows the empty state', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        StorageCategoryDetailScreen(
          extra: StorageCategoryExtra(
            label: CategoryLabel.pdfs,
            extensions: pdfExtensions.toList(),
          ),
        ),
        overrides: [
          resourcesRepositoryProvider.overrideWith((ref) => repository),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No downloaded files found'), findsOneWidget);
  });
}
