import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/references/references_screen.dart';

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

  testWidgets('makes the offline maps gap explicit', (tester) async {
    await tester.pumpWidget(wrapScreen(const ReferencesScreen()));

    await tester.tap(find.text('Maps'));
    await tester.pump();
    expect(find.text('Offline maps are not ported yet'), findsOneWidget);
  });
}
