import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/references/references_screen.dart';

import 'package:myplanet/ui/router.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('shows the reference destinations', (tester) async {
    await tester.pumpWidget(wrapScreen(const ReferencesScreen()));

    expect(find.text('References'), findsOneWidget);
    expect(find.text('Maps'), findsOneWidget);
    expect(find.text('English dictionary'), findsOneWidget);
    expect(find.byIcon(Icons.map_outlined), findsOneWidget);
    expect(find.byIcon(Icons.menu_book_outlined), findsOneWidget);
  });

  testWidgets('the maps tile opens the map, not an apology', (tester) async {
    // This asserted the "not ported yet" snackbar. `OfflineMapsScreen` was
    // built in the same round that left this tile unchanged, so the screen
    // existed with nothing able to reach it.
    await tester.pumpWidget(
      wrapScreen(
        const ReferencesScreen(),
        pushTargets: {
          Routes.offlineMaps: (_) => const Scaffold(body: Text('the map')),
        },
      ),
    );

    await tester.tap(find.text('Maps'));
    await tester.pumpAndSettle();

    expect(find.text('Offline maps are not ported yet'), findsNothing);
    expect(find.text('the map'), findsOneWidget);
  });
}
