import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/life_provider.dart';
import 'package:myplanet/ui/life/life_screen.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('renders ordered items and visible state', (tester) async {
    final rows = [
      _row('calendar', weight: 0),
      _row('health', weight: 1, visible: false),
      _row('references', weight: 2),
    ];
    await tester.pumpWidget(
      wrapScreen(
        const LifeScreen(),
        overrides: [
          lifeItemsProvider.overrideWith((ref) => Stream.value(rows)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('My life'), findsOneWidget);
    expect(find.text('Calendar'), findsOneWidget);
    expect(find.text('My health'), findsOneWidget);
    expect(find.text('References'), findsOneWidget);
    expect(find.text('Hidden'), findsOneWidget);
    expect(find.byIcon(Icons.drag_handle), findsNWidgets(3));
  });

  testWidgets('explains that an unported destination is pending', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(
        const LifeScreen(),
        overrides: [
          lifeItemsProvider.overrideWith(
            (ref) => Stream.value([_row('unknown_feature')]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('unknown_feature'));
    await tester.pump();
    expect(find.text('This feature is not ported yet'), findsOneWidget);
  });
}

MyLifeRow _row(String feature, {int weight = 0, bool visible = true}) {
  return MyLifeRow(
    id: 'user-1:$feature',
    feature: feature,
    userId: 'user-1',
    isVisible: visible,
    weight: weight,
  );
}
