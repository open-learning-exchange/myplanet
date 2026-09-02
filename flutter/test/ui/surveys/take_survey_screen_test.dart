import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/surveys/take_survey_screen.dart';

import '../../support/widget_harness.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// First tests for `TakeSurveyScreen` — the offline half of
/// `ExamTakingFragment`'s survey mode (Phase 104).
///
/// They exist because the screen was the visible end of the
/// `SurveyMapper.choices` corruption: every choice label it drew came out of
/// `JsonUtils.getStringList`, which had `toString()`d the choice object.
///
/// The real `SurveysRepository`/`SubmissionsRepository` run over an in-memory
/// database, so what lands in `submissions` is the real thing.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppDatabase db;

  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<void> seedSurvey({
    required String type,
    List<Object> choices = const [
      {'id': 'water', 'text': 'Water'},
      {'id': 'power', 'text': 'Power'},
    ],
  }) async {
    final mapping = SurveyMapper.fromDoc({
      '_id': 'survey-1',
      'type': 'surveys',
      'name': 'Community needs',
      'questions': [
        {
          'id': 'q1',
          'body': 'Which service?',
          'type': type,
          'choices': choices,
        },
      ],
    })!;
    await db.surveyDao.upsertAll(
      [mapping.survey],
      {'survey-1': mapping.questions},
    );
  }

  Future<void> pumpScreen(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1000, 3000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      wrapScreen(
        const TakeSurveyScreen(surveyId: 'survey-1'),
        pushTargets: {
          '/life/submissions/:id': (_) =>
              const Scaffold(body: Text('SUBMISSION_PAGE')),
        },
        overrides: [
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          planetApiProvider.overrideWithValue(MockPlanetApi()),
          sessionProvider.overrideWith(
            () => _StubSession(buildUserRow(id: 'user-a', name: 'jane')),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> tapSubmit(WidgetTester tester) async {
    await tester.tap(find.widgetWithText(FilledButton, 'Submit survey'));
    // Never pumpAndSettle here: while `submitting` is true the button holds an
    // indefinite CircularProgressIndicator.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
  }

  Future<List<SubmissionAnswerRow>> answers() =>
      db.select(db.submissionAnswers).get();

  testWidgets('a choice question renders the choice text, not the choice '
      'object', (tester) async {
    // The label used to be the Dart literal `{id: water, text: Water}`:
    // `SurveyMapper` put the choices through `JsonUtils.getStringList`, whose
    // `e.toString()` flattened the map.
    await seedSurvey(type: 'select');
    await pumpScreen(tester);

    expect(find.text('1. Which service?'), findsOneWidget);
    expect(find.text('Water'), findsOneWidget);
    expect(find.text('Power'), findsOneWidget);
    expect(find.textContaining('{id:'), findsNothing);
  });

  testWidgets('a bare-string choice renders as its own label', (tester) async {
    await seedSurvey(type: 'select', choices: const ['Yes', 'No']);
    await pumpScreen(tester);

    expect(find.text('Yes'), findsOneWidget);
    expect(find.text('No'), findsOneWidget);
  });

  testWidgets('a selectMultiple question renders checkboxes', (tester) async {
    await seedSurvey(type: 'selectMultiple');
    await pumpScreen(tester);

    expect(find.byType(CheckboxListTile), findsNWidgets(2));
    expect(find.byType(RadioListTile<ExamChoice?>), findsNothing);
  });

  testWidgets('a selectmultiple question renders checkboxes too', (
    tester,
  ) async {
    // `ExamTakingFragment.startExam` compares with
    // `equals("selectMultiple", ignoreCase = true)`. Matching case-sensitively
    // drew radios, so the respondent could pick exactly one of the answers
    // they meant to give.
    await seedSurvey(type: 'selectmultiple');
    await pumpScreen(tester);

    expect(find.byType(CheckboxListTile), findsNWidgets(2));
  });

  testWidgets('a picked choice is stored as the {id, text} object', (
    tester,
  ) async {
    // `Answer.valueChoicesArray` reads each stored entry straight back with
    // `gson.fromJson(choice, JsonObject::class.java)`, so an answer records
    // the whole choice object — the id included, which is what identifies the
    // answer to Planet. The screen used to store the flattened label.
    await seedSurvey(type: 'select');
    await pumpScreen(tester);

    await tester.tap(find.text('Water'));
    await tester.pumpAndSettle();
    await tapSubmit(tester);

    final stored = await answers();
    expect(stored, hasLength(1));
    expect(stored.single.valueChoices, [
      jsonEncode({'id': 'water', 'text': 'Water'}),
    ]);
  });

  testWidgets('every checked choice of a selectMultiple is stored', (
    tester,
  ) async {
    await seedSurvey(type: 'selectMultiple');
    await pumpScreen(tester);

    await tester.tap(find.text('Water'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Power'));
    await tester.pumpAndSettle();
    await tapSubmit(tester);

    final stored = await answers();
    expect(stored.single.valueChoices, [
      jsonEncode({'id': 'water', 'text': 'Water'}),
      jsonEncode({'id': 'power', 'text': 'Power'}),
    ]);
  });

  testWidgets('the submission question row carries the choice labels', (
    tester,
  ) async {
    // `SubmissionQuestions.choices` is the display list the detail screen
    // shows under "Choices", and the table is preserved across schema bumps,
    // so it stays a label list — `createExamDraft` already wrote it that way.
    await seedSurvey(type: 'select');
    await pumpScreen(tester);

    await tester.tap(find.text('Water'));
    await tester.pumpAndSettle();
    await tapSubmit(tester);

    final questions = await db.select(db.submissionQuestions).get();
    expect(questions.single.choices, ['Water', 'Power']);
  });

  testWidgets('a survey with no questions offers no submit button', (
    tester,
  ) async {
    // `ExamTakingFragment` hides the form and the button and labels the
    // counter `no_questions`.
    final mapping = SurveyMapper.fromDoc({
      '_id': 'survey-1',
      'type': 'surveys',
      'name': 'Community needs',
      'questions': const [],
    })!;
    await db.surveyDao.upsertAll([mapping.survey], {'survey-1': const []});
    await pumpScreen(tester);

    expect(find.text('This survey has no questions'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Submit survey'), findsNothing);
  });
}

class _StubSession extends SessionNotifier {
  _StubSession(this.user);
  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}
