import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/personals_provider.dart';
import 'package:myplanet/ui/personals/personals_screen.dart';
import 'package:myplanet/ui/viewer/path_resource_viewer_screen.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('renders offline personal metadata', (tester) async {
    final row = PersonalRow(
      id: 'personal-1',
      couchId: 'personal-1',
      isUploaded: false,
      title: 'Learning journal',
      titleNormalized: 'learning journal',
      description: 'Notes from today',
      date: DateTime(2026, 8, 2, 12).millisecondsSinceEpoch,
      userId: 'user-1',
    );
    await tester.pumpWidget(
      wrapScreen(
        const PersonalsScreen(),
        overrides: [
          personalsProvider.overrideWith((ref) => Stream.value([row])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('My personals'), findsOneWidget);
    expect(find.text('Learning journal'), findsOneWidget);
    expect(find.text('Notes from today'), findsOneWidget);
    expect(find.text('Saved offline — pending upload'), findsOneWidget);
    expect(find.byIcon(Icons.description_outlined), findsOneWidget);
  });

  testWidgets('validates title in the add dialog', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const PersonalsScreen(),
        overrides: [
          personalsProvider.overrideWith((ref) => Stream.value(const [])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('No personal items yet'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, 'Add personal item'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Save'));
    await tester.pump();
    expect(find.text('Enter a title'), findsOneWidget);
  });

  testWidgets('tapping a card with an attachment opens the viewer', (
    tester,
  ) async {
    final row = PersonalRow(
      id: 'personal-2',
      couchId: 'personal-2',
      isUploaded: true,
      title: 'Scanned doc',
      titleNormalized: 'scanned doc',
      description: null,
      date: DateTime(2026, 8, 2, 12).millisecondsSinceEpoch,
      userId: 'user-1',
      path: '/tmp/notes.unsupported',
    );
    await tester.pumpWidget(
      wrapScreen(
        const PersonalsScreen(),
        overrides: [
          personalsProvider.overrideWith((ref) => Stream.value([row])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // Unsupported extension avoids file I/O in the headless test, so the
    // viewer renders its "cannot open" message.
    await tester.tap(find.text('Scanned doc'));
    await tester.pumpAndSettle();

    expect(find.byType(PathResourceViewerScreen), findsOneWidget);
    expect(find.textContaining('cannot be opened'), findsOneWidget);
  });
}
