import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/repository/tags_repository.dart';
import 'package:myplanet/ui/resources/collections_dialog.dart';

import '../support/widget_harness.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// Opens [CollectionsDialog] via a button so the test can capture the popped
/// result, instead of asserting on the dialog route directly.
class _Harness extends ConsumerWidget {
  const _Harness({required this.dbType, required this.results});

  final String dbType;
  final List<List<Tag>?> results;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: Center(
        child: ElevatedButton(
          child: const Text('open'),
          onPressed: () async {
            final picked = await showCollectionsDialog(context, dbType: dbType);
            results.add(picked);
          },
        ),
      ),
    );
  }
}

void main() {
  late AppDatabase db;
  late TagsRepository repository;

  setUp(() {
    db = AppDatabase.memory();
    // The repository never touches the network on reads; a bare Mock is the
    // guard — an unstubbed `getJsonObject` would throw.
    repository = TagsRepository(MockPlanetApi(), db.tagDao);
  });

  tearDown(() => db.close());

  Future<void> pump(
    WidgetTester tester, {
    required List<List<Tag>?> results,
    String dbType = 'resources',
    List<Override> overrides = const [],
  }) {
    return tester.pumpWidget(
      wrapScreen(
        _Harness(dbType: dbType, results: results),
        overrides: [
          tagsRepositoryProvider.overrideWithValue(repository),
          ...overrides,
        ],
      ),
    );
  }

  Future<void> open(WidgetTester tester) async {
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
  }

  testWidgets('single-select: tapping a parent returns it and dismisses', (
    tester,
  ) async {
    await repository.insertDocs([
      {'_id': 'tag-math', 'name': 'Math', 'db': 'resources'},
    ]);
    final results = <List<Tag>?>[];
    await pump(tester, results: results);
    await tester.pumpAndSettle();
    await open(tester);

    expect(find.text('Math'), findsOneWidget);
    await tester.tap(find.text('Math'));
    await tester.pumpAndSettle();

    // A leaf parent picked singly: one tag returned, dialog gone.
    expect(results.single!.single.name, 'Math');
    expect(results.single!.single.id, 'tag-math');
    expect(find.text('Collections'), findsNothing);
  });

  testWidgets('expands a parent to reach its children', (tester) async {
    await repository.insertDocs([
      {'_id': 'tag-math', 'name': 'Math', 'db': 'resources'},
      {
        '_id': 'tag-algebra',
        'name': 'Algebra',
        'attachedTo': <dynamic>['tag-math'],
      },
    ]);
    final results = <List<Tag>?>[];
    await pump(tester, results: results);
    await tester.pumpAndSettle();
    await open(tester);

    expect(find.text('Algebra'), findsNothing);
    await tester.tap(find.text('Math'));
    await tester.pumpAndSettle();

    expect(find.text('Algebra'), findsOneWidget);
    await tester.tap(find.text('Algebra'));
    await tester.pumpAndSettle();

    expect(results.single!.single.name, 'Algebra');
  });

  testWidgets('the filter box narrows the parent list after a debounce', (
    tester,
  ) async {
    await repository.insertDocs([
      {'_id': 'tag-math', 'name': 'Math', 'db': 'resources'},
      {'_id': 'tag-science', 'name': 'Science', 'db': 'resources'},
    ]);
    final results = <List<Tag>?>[];
    await pump(tester, results: results);
    await open(tester);

    expect(find.text('Math'), findsOneWidget);
    expect(find.text('Science'), findsOneWidget);

    await tester.enterText(find.byType(TextField), 'scien');
    // Past the 300ms debounce of `CollectionsFragment.addSearchTextWatcher`.
    await tester.pump(const Duration(milliseconds: 350));
    await tester.pump();

    expect(find.text('Science'), findsOneWidget);
    expect(find.text('Math'), findsNothing);
  });

  testWidgets('multi-select: checked tags come back through the OK button', (
    tester,
  ) async {
    await repository.insertDocs([
      {'_id': 'tag-math', 'name': 'Math', 'db': 'resources'},
      {'_id': 'tag-science', 'name': 'Science', 'db': 'resources'},
    ]);
    final results = <List<Tag>?>[];
    await pump(tester, results: results);
    await open(tester);

    // Flip "Select Many Collections" on, mirroring
    // `MainApplication.isCollectionSwitchOn`.
    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Math'));
    await tester.tap(find.text('Science'));
    await tester.pump();
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();

    expect(
      results.single!.map((t) => t.name),
      containsAll(['Math', 'Science']),
    );
  });

  testWidgets('an empty tag cache toasts and dismisses, like the Kotlin', (
    tester,
  ) async {
    final results = <List<Tag>?>[];
    await pump(tester, results: results);
    await open(tester);

    expect(find.byType(SnackBar), findsOneWidget);
    expect(find.text('No data available'), findsOneWidget);
    expect(results.single, isNull);
  });
}
