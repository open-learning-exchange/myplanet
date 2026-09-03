import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/achievements_provider.dart';
import 'package:myplanet/repository/achievements_repository.dart';
import 'package:myplanet/ui/achievements/achievements_screen.dart';

import '../../support/widget_harness.dart';
import '../../support/mock_planet_api.dart';

Widget harness(AchievementRow? row) {
  return wrapScreen(
    const AchievementsScreen(),
    overrides: [
      achievementEntryProvider.overrideWith(
        (ref) => Future<AchievementRow?>.value(row),
      ),
    ],
    pushTargets: {
      '/life/achievements/edit': (context) =>
          const Scaffold(body: Text('edit target')),
    },
  );
}

void main() {
  testWidgets('shows the empty state the Kotlin does', (tester) async {
    await tester.pumpWidget(harness(null));
    await tester.pumpAndSettle();
    expect(find.text('Summary of achievements'), findsOneWidget);
    expect(find.text('Add Achievement'), findsOneWidget);
  });

  testWidgets('renders goals, purpose, entries and references', (tester) async {
    final database = AppDatabase.memory();
    final repository = AchievementsRepository(
      MockPlanetApi(),
      database.achievementDao,
    );
    await repository.update(
      'ada@earth',
      const AchievementInput(
        goals: 'learn dart',
        purpose: 'teach',
        achievementsHeader: 'my header',
        achievementsJson: '[{"title":"First summit"}]',
        referencesJson: '[{"name":"Mo","relationship":"teammate"}]',
      ),
    );
    final row = await repository.getOrInitialize('ada@earth');
    await tester.pumpWidget(harness(row));
    await tester.pumpAndSettle();
    addTearDown(database.close);
    expect(find.text('learn dart'), findsWidgets);
    expect(find.text('teach'), findsWidgets);
    expect(find.text('First summit'), findsOneWidget);
    expect(find.text('Mo'), findsOneWidget);
  });

  testWidgets('the edit action pushes the edit route', (tester) async {
    await tester.pumpWidget(harness(null));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(IconButton).first);
    await tester.pumpAndSettle();
    expect(find.text('edit target'), findsOneWidget);
  });
}
