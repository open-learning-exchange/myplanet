import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/resources_providers.dart';
import 'package:myplanet/ui/components/view_mode_toggle.dart';
import 'package:myplanet/ui/resources/resources_screen.dart';

import '../support/widget_harness.dart';

class MockResourceShelfActions extends Mock implements ResourceShelfActions {}

void main() {
  testWidgets('renders the resources returned by the stream', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value([
              buildLibraryRow(id: 'r1', title: 'Álgebra Básica', author: 'Ada'),
              buildLibraryRow(id: 'r2', title: 'Biology', offline: true),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Álgebra Básica'), findsOneWidget);
    expect(find.text('Biology'), findsOneWidget);
    // The offline resource is badged; the other is not.
    expect(find.byIcon(Icons.offline_pin_outlined), findsOneWidget);
  });

  testWidgets('toggles between list and grid view', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value([
              buildLibraryRow(id: 'r1', title: 'Algebra', author: 'Ada'),
              buildLibraryRow(id: 'r2', title: 'Biology', offline: true),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // Default is list mode in tests — tiles are ListTile widgets.
    expect(find.byType(ListTile), findsNWidgets(2));

    // Tap the grid-view toggle.
    await tester.tap(find.byTooltip('Grid view'));
    await tester.pumpAndSettle();

    // Now the list renders as a GridView with Card-based tiles.
    expect(find.byType(GridView), findsOneWidget);
    expect(find.byType(ListTile), findsNothing);

    // Tap the list-view toggle to go back.
    await tester.tap(find.byTooltip('List view'));
    await tester.pumpAndSettle();

    expect(find.byType(ListTile), findsNWidgets(2));
    expect(find.byType(GridView), findsNothing);
  });

  testWidgets('long press selects multiple resources for one shelf action', (
    tester,
  ) async {
    final actions = MockResourceShelfActions();
    when(
      () => actions.setMemberships(any(), joined: true),
    ).thenAnswer((_) async {});
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value([
              buildLibraryRow(id: 'r1', title: 'Algebra'),
              buildLibraryRow(id: 'r2', title: 'Biology'),
            ]),
          ),
          resourceShelfActionsProvider.overrideWithValue(actions),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.longPress(find.text('Algebra'));
    await tester.tap(find.text('Biology'));
    await tester.pumpAndSettle();

    expect(find.text('2 selected'), findsOneWidget);
    expect(find.byIcon(Icons.check_circle), findsNWidgets(2));
    await tester.tap(find.byTooltip('Add to My Library'));
    await tester.pumpAndSettle();

    final captured =
        verify(
              () => actions.setMemberships(captureAny(), joined: true),
            ).captured.single
            as Iterable<String>;
    expect(captured, containsAll(['r1', 'r2']));
    expect(find.text('Added to My Library'), findsOneWidget);
  });

  testWidgets('shows the empty state when nothing is synced', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value(const <MyLibraryRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No data available'), findsOneWidget);
  });

  // #15572: when the shelf is empty, the search bar, view-mode toggle, and
  // filter button are hidden — only the sync button remains.
  testWidgets('hides search and view toggle when shelf is empty', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value(const <MyLibraryRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(SearchBar), findsNothing);
    expect(find.byType(ViewModeToggle), findsNothing);
    expect(find.byTooltip('Filter Resources'), findsNothing);
    // Sync stays available.
    expect(find.byIcon(Icons.sync), findsOneWidget);
  });

  testWidgets('typing in the search box updates the query provider', (
    tester,
  ) async {
    late WidgetRef capturedRef;

    await tester.pumpWidget(
      wrapScreen(
        Consumer(
          builder: (context, ref, _) {
            capturedRef = ref;
            return const ResourcesScreen();
          },
        ),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) =>
                Stream.value([buildLibraryRow(id: 'r1', title: 'Algebra')]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.enterText(find.byType(SearchBar), 'algebra');
    await tester.pump();

    expect(capturedRef.read(resourceSearchQueryProvider), 'algebra');
  });

  testWidgets('renders Spanish strings under the es locale', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value(const <MyLibraryRow>[]),
          ),
        ],
        locale: const Locale('es'),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Recursos'), findsOneWidget);
    expect(find.text('No hay datos disponibles.'), findsOneWidget);
  });

  testWidgets('shelf toggle switches the resourceShelfOnlyProvider state', (
    tester,
  ) async {
    late WidgetRef capturedRef;

    await tester.pumpWidget(
      wrapScreen(
        Consumer(
          builder: (context, ref, _) {
            capturedRef = ref;
            return const ResourcesScreen();
          },
        ),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value(const <MyLibraryRow>[]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // Default is the full catalog.
    expect(capturedRef.read(resourceShelfOnlyProvider), isFalse);
    expect(find.byTooltip('My Library'), findsOneWidget);

    await tester.tap(find.byTooltip('My Library'));
    await tester.pumpAndSettle();

    expect(capturedRef.read(resourceShelfOnlyProvider), isTrue);
    expect(find.byTooltip('All Resources'), findsOneWidget);
  });

  testWidgets('sort sheet toggles title order A-Z then Z-A', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value([
              buildLibraryRow(id: 'b', title: 'Banana'),
              buildLibraryRow(id: 'a', title: 'Apple'),
              buildLibraryRow(id: 'c', title: 'Cherry'),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    double topOf(String title) => tester.getTopLeft(find.text(title)).dy;
    expect(topOf('Banana'), lessThan(topOf('Apple')));

    await tester.tap(find.byTooltip('Sort resources'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Order by Title'));
    await tester.pumpAndSettle();

    // First toggle sorts A→Z (the upstream `14a9f14` fix).
    expect(topOf('Apple'), lessThan(topOf('Banana')));
    expect(topOf('Banana'), lessThan(topOf('Cherry')));

    await tester.tap(find.byTooltip('Sort resources'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Order by Title'));
    await tester.pumpAndSettle();

    expect(topOf('Cherry'), lessThan(topOf('Banana')));
    expect(topOf('Banana'), lessThan(topOf('Apple')));
  });

  testWidgets('sort sheet toggles date order newest-first then oldest-first', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourcesStreamProvider.overrideWith(
            (ref) => Stream.value([
              buildLibraryRow(id: 'old', title: 'Old', createdDate: 100),
              buildLibraryRow(id: 'new', title: 'New', createdDate: 300),
              buildLibraryRow(id: 'mid', title: 'Mid', createdDate: 200),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    double topOf(String title) => tester.getTopLeft(find.text(title)).dy;

    await tester.tap(find.byTooltip('Sort resources'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Order by Date'));
    await tester.pumpAndSettle();

    expect(topOf('New'), lessThan(topOf('Mid')));
    expect(topOf('Mid'), lessThan(topOf('Old')));

    await tester.tap(find.byTooltip('Sort resources'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Order by Date'));
    await tester.pumpAndSettle();

    expect(topOf('Old'), lessThan(topOf('Mid')));
    expect(topOf('Mid'), lessThan(topOf('New')));
  });

  // `ResourcesFragment.setupAddResourceButtonListener` does
  // `addResource.visibility = if (isMyCourseLib) VISIBLE else GONE`
  // (ResourcesFragment.kt:454-455). `isMyCourseLib` is the port's
  // `resourceShelfOnlyProvider`, so the add button is the My Library
  // affordance, not the catalog's.
  Future<void> pumpWithShelf(
    WidgetTester tester, {
    required bool shelfOnly,
  }) async {
    await tester.pumpWidget(
      wrapScreen(
        const ResourcesScreen(),
        overrides: [
          resourceShelfOnlyProvider.overrideWith((ref) => shelfOnly),
          resourcesStreamProvider.overrideWith(
            (ref) =>
                Stream.value([buildLibraryRow(id: 'r1', title: 'Algebra')]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('My Library offers the add-resource button', (tester) async {
    await pumpWithShelf(tester, shelfOnly: true);
    expect(find.byType(FloatingActionButton), findsOneWidget);
  });

  testWidgets('the catalog does not offer the add-resource button', (
    tester,
  ) async {
    await pumpWithShelf(tester, shelfOnly: false);
    expect(find.byType(FloatingActionButton), findsNothing);
  });
}
