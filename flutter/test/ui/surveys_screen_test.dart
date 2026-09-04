import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/surveys_provider.dart';
import 'package:myplanet/ui/surveys/surveys_screen.dart';

import '../support/widget_harness.dart';

void main() {
  testWidgets('renders the offline survey catalog', (tester) async {
    final survey = SurveyRow(
      id: 'survey-1',
      name: 'Community needs',
      description: 'Tell us what matters',
      createdDate: 10,
      updatedDate: 0,
      adoptionDate: 0,
      totalMarks: 0,
      isFromNation: false,
      teamShareAllowed: false,
    );
    await tester.pumpWidget(
      wrapScreen(
        const SurveysScreen(),
        overrides: [
          surveysProvider.overrideWith((ref) => Stream.value([survey])),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('My surveys'), findsOneWidget);
    expect(find.text('Community needs'), findsOneWidget);
    expect(find.text('Tell us what matters'), findsOneWidget);
    expect(find.text('Search surveys'), findsOneWidget);
  });

  testWidgets('renders a refreshable empty state', (tester) async {
    await tester.pumpWidget(
      wrapScreen(
        const SurveysScreen(),
        overrides: [
          surveysProvider.overrideWith((ref) => Stream.value(const [])),
        ],
      ),
    );
    await tester.pump();
    expect(find.text('No surveys available'), findsOneWidget);
  });
}
