import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/ui/calendar/calendar_screen.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('shows the calendar initialized to the supplied date', (
    tester,
  ) async {
    await tester.pumpWidget(
      wrapScreen(CalendarScreen(initialDate: DateTime(2026, 8, 2))),
    );

    expect(find.text('Calendar'), findsOneWidget);
    expect(find.byType(CalendarDatePicker), findsOneWidget);
    expect(find.text('August 2026'), findsOneWidget);
    final picker = tester.widget<CalendarDatePicker>(
      find.byType(CalendarDatePicker),
    );
    expect(picker.initialDate, DateTime(2026, 8, 2));
  });

  testWidgets('localizes the screen title', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        CalendarScreen(initialDate: DateTime(2026, 8, 2)),
        locale: const Locale('es'),
      ),
    );

    expect(find.text('Calendario'), findsOneWidget);
    expect(find.text('agosto de 2026'), findsOneWidget);
  });
}
