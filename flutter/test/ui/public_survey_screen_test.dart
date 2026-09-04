import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/surveys/public_survey_screen.dart';

import '../support/widget_harness.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// First tests for `PublicSurveyScreen` (Phase 102).
///
/// The Kotlin specification is `ui/surveys/PublicSurveyActivity.kt`, which
/// hosts the shared `ExamTakingFragment` — so the question rendering and the
/// "you must answer this" rule come from `ExamTakingFragment`, and the
/// fetch/POST envelope from the Activity.
///
/// Every test drives the real `SurveysRepository` and `SubmissionsRepository`
/// over an in-memory database, with only `PlanetApi` mocked, so what the
/// screen writes to `submissions` is the real thing.
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppDatabase db;
  late MockPlanetApi api;

  setUp(() {
    db = AppDatabase.memory();
    api = MockPlanetApi();
  });
  tearDown(() => db.close());

  Map<String, dynamic> surveyDoc(List<Map<String, dynamic>> questions) => {
    '_id': 'survey-1',
    'type': 'surveys',
    'name': 'Community needs',
    'description': 'Tell us what you need',
    'questions': questions,
  };

  Map<String, dynamic> textQuestion(String id, String body) => {
    'id': id,
    'body': body,
    'type': 'input',
  };

  Map<String, dynamic> choiceQuestion(String id, String body, String type) => {
    'id': id,
    'body': body,
    'type': type,
    'choices': [
      {'id': 'a', 'text': 'Water'},
      {'id': 'b', 'text': 'Power'},
    ],
  };

  /// Answers the fetch with [doc], or fails it when [doc] is null.
  void stubFetch(Map<String, dynamic>? doc) {
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer(
      (_) async => doc == null
          ? const NetworkError<Map<String, dynamic>>(404, 'not found')
          : NetworkSuccess<Map<String, dynamic>>(doc),
    );
  }

  void stubPost({required bool succeeds}) {
    when(
      () => api.sendJsonDynamic(
        any(),
        body: any(named: 'body'),
        method: any(named: 'method'),
        authHeader: any(named: 'authHeader'),
      ),
    ).thenAnswer(
      (_) async => succeeds
          ? const NetworkSuccess<dynamic>({'ok': true})
          : const NetworkError<dynamic>(500, 'boom'),
    );
  }

  /// Mounts the screen as the *root* route, which is how a deep link opens it:
  /// there is nothing underneath to go back to.
  Future<void> pumpScreen(WidgetTester tester, {UserRow? user}) async {
    tester.view.physicalSize = const Size(1000, 3000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      wrapScreen(
        const PublicSurveyScreen(
          baseUrl: 'https://planet.example',
          teamId: 'team-1',
          surveyId: 'survey-1',
        ),
        pushTargets: {
          '/resources': (_) => const Scaffold(body: Text('RESOURCES_PAGE')),
          '/login': (_) => const Scaffold(body: Text('LOGIN_PAGE')),
        },
        overrides: [
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          planetApiProvider.overrideWithValue(api),
          sessionProvider.overrideWith(() => _StubSession(user)),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> tapSubmit(WidgetTester tester) async {
    await tester.tap(find.widgetWithText(FilledButton, 'Submit survey'));
    // Never pumpAndSettle here: while `_submitting` is true the button holds an
    // indefinite CircularProgressIndicator.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
  }

  Future<int> submissionCount() async =>
      (await db.select(db.submissions).get()).length;

  testWidgets('an unreachable survey shows the load-failure state', (
    tester,
  ) async {
    stubFetch(null);
    await pumpScreen(tester);

    expect(find.text('Survey could not be loaded'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Close'), findsOneWidget);
  });

  testWidgets('closing the load-failure state leaves the screen', (
    tester,
  ) async {
    // `PublicSurveyActivity` calls `finish()`. A deep link puts this screen
    // first on the stack, so `Navigator.maybePop` has nothing to pop and the
    // respondent was stranded on the failure card.
    stubFetch(null);
    await pumpScreen(tester);

    await tester.tap(find.widgetWithText(FilledButton, 'Close'));
    await tester.pumpAndSettle();

    expect(find.text('LOGIN_PAGE'), findsOneWidget);
  });

  testWidgets('a document that is not a survey shows the load-failure state', (
    tester,
  ) async {
    // `SurveyMapper.fromDoc` returns null unless `type == "surveys"`.
    stubFetch({'_id': 'survey-1', 'name': 'Community needs'});
    await pumpScreen(tester);

    expect(find.text('Survey could not be loaded'), findsOneWidget);
  });

  testWidgets('a survey with no questions cannot be submitted', (tester) async {
    // `ExamTakingFragment` hides the form and the submit button outright and
    // labels the counter `no_questions`. The port offered Submit on an empty
    // page, and `_submit`'s answered-everything guard is vacuously true for
    // zero questions — so it created and POSTed an answer-less submission.
    stubFetch(surveyDoc(const []));
    await pumpScreen(tester);

    expect(find.text('This survey has no questions'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Submit survey'), findsNothing);
    expect(await submissionCount(), 0);
  });

  testWidgets('a choice question renders the choice text', (tester) async {
    // The screen used to re-parse the fetched document because
    // `SurveyMapper` flattened `choices` through `JsonUtils.getStringList`.
    // With the mapper fixed it reads the rows the mapper wrote, so this pins
    // that the label survives that route too.
    stubFetch(surveyDoc([choiceQuestion('q1', 'Which service?', 'select')]));
    await pumpScreen(tester);

    expect(find.text('Water'), findsOneWidget);
    expect(find.text('Power'), findsOneWidget);
    expect(find.textContaining('{id:'), findsNothing);
  });

  testWidgets('a question labelled with `title` renders that label', (
    tester,
  ) async {
    // `ExamQuestion.insertExamQuestions` reads `title`, not `header`; the
    // screen's own parser had a body/header/title fallback chain that the
    // mapper lacked, so removing it depended on fixing the mapper's read.
    stubFetch(
      surveyDoc([
        {'id': 'q1', 'title': 'Which service?', 'type': 'input'},
      ]),
    );
    await pumpScreen(tester);

    expect(find.text('1. Which service?'), findsOneWidget);
  });

  testWidgets('the survey renders its name, description and numbered '
      'questions', (tester) async {
    stubFetch(
      surveyDoc([
        textQuestion('q1', 'What do you need?'),
        choiceQuestion('q2', 'Which service?', 'select'),
      ]),
    );
    await pumpScreen(tester);

    expect(find.text('Community needs'), findsOneWidget);
    expect(find.text('Tell us what you need'), findsOneWidget);
    expect(find.text('1. What do you need?'), findsOneWidget);
    expect(find.text('2. Which service?'), findsOneWidget);
  });

  testWidgets('a survey wrapped in a "survey" envelope still loads', (
    tester,
  ) async {
    // `PublicSurveyActivity.loadSurvey` unwraps `response.get("survey")`.
    stubFetch({
      'survey': surveyDoc([textQuestion('q1', 'What do you need?')]),
    });
    await pumpScreen(tester);

    expect(find.text('Community needs'), findsOneWidget);
  });

  testWidgets('a text question renders a free-text field', (tester) async {
    stubFetch(surveyDoc([textQuestion('q1', 'What do you need?')]));
    await pumpScreen(tester);

    expect(find.byType(TextField), findsOneWidget);
    expect(find.byType(RadioListTile<ExamChoice?>), findsNothing);
  });

  testWidgets('a select question renders one radio per choice', (tester) async {
    stubFetch(surveyDoc([choiceQuestion('q1', 'Which service?', 'select')]));
    await pumpScreen(tester);

    expect(find.byType(RadioListTile<ExamChoice?>), findsNWidgets(2));
    expect(find.byType(CheckboxListTile), findsNothing);
  });

  testWidgets('a selectMultiple question renders one checkbox per choice', (
    tester,
  ) async {
    stubFetch(
      surveyDoc([choiceQuestion('q1', 'Which services?', 'selectMultiple')]),
    );
    await pumpScreen(tester);

    expect(find.byType(CheckboxListTile), findsNWidgets(2));
  });

  testWidgets('the question type is matched case-insensitively', (
    tester,
  ) async {
    // `ExamTakingFragment.startExam` compares with
    // `equals("selectMultiple", ignoreCase = true)`, and so does this port's
    // own `SurveysRepository._buildPublicAnswers`. A document whose type is
    // spelled `selectmultiple` must still get checkboxes: rendered as radios
    // the respondent can pick exactly one, and every other answer they meant
    // to give is gone before the POST is built.
    stubFetch(
      surveyDoc([choiceQuestion('q1', 'Which services?', 'selectmultiple')]),
    );
    await pumpScreen(tester);

    expect(find.byType(CheckboxListTile), findsNWidgets(2));
  });

  group('answer requirement (ExamTakingFragment.isQuestionAnswered)', () {
    testWidgets('an untouched survey cannot be submitted', (tester) async {
      // `ExamTakingFragment` hides Next until the current question is answered
      // and toasts on submit otherwise — there is no `required` flag, every
      // question must be answered. A Planet survey document carries no
      // `required` key at all, so a guard keyed on one never fires and an
      // empty answer sheet reaches the server.
      stubPost(succeeds: true);
      stubFetch(
        surveyDoc([
          textQuestion('q1', 'What do you need?'),
          choiceQuestion('q2', 'Which service?', 'select'),
        ]),
      );
      await pumpScreen(tester);

      await tapSubmit(tester);

      expect(find.text('Answer all required questions'), findsOneWidget);
      expect(await submissionCount(), 0);
      verifyNever(
        () => api.sendJsonDynamic(
          any(),
          body: any(named: 'body'),
          method: any(named: 'method'),
          authHeader: any(named: 'authHeader'),
        ),
      );
    });

    testWidgets('a partly answered survey cannot be submitted', (tester) async {
      stubPost(succeeds: true);
      stubFetch(
        surveyDoc([
          textQuestion('q1', 'What do you need?'),
          choiceQuestion('q2', 'Which service?', 'select'),
        ]),
      );
      await pumpScreen(tester);

      await tester.enterText(find.byType(TextField), 'Clean water');
      await tester.pump();
      await tapSubmit(tester);

      expect(find.text('Answer all required questions'), findsOneWidget);
      expect(await submissionCount(), 0);
    });

    testWidgets('a whitespace-only text answer does not count', (tester) async {
      stubPost(succeeds: true);
      stubFetch(surveyDoc([textQuestion('q1', 'What do you need?')]));
      await pumpScreen(tester);

      await tester.enterText(find.byType(TextField), '   ');
      await tester.pump();
      await tapSubmit(tester);

      expect(find.text('Answer all required questions'), findsOneWidget);
      expect(await submissionCount(), 0);
    });
  });

  group('submitting', () {
    /// Answers the one text question, submits, and dismisses the respondent
    /// information screen the way a respondent who declines it would.
    /// Answers the one question, submits, and completes the respondent
    /// profile step the way a respondent does.
    ///
    /// The profile step has to be *saved*, not backed out of: a dismissed
    /// dialog leaves `uploadCompletedSubmission` nothing to POST, which is why
    /// the screen returns early on anything but a `true` pop. Backing out —
    /// which this helper used to do — therefore stopped sending the answer
    /// sheet at all, and the five tests that go through here have been failing
    /// on it. Never `pumpAndSettle` after Save: the button holds a
    /// `CircularProgressIndicator` while it works.
    Future<void> completeProfileStep(WidgetTester tester) async {
      expect(find.text('Your information'), findsOneWidget);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Year of birth'),
        '1990',
      );
      await tester.pump();
      await tester.tap(find.widgetWithText(FilledButton, 'Save'));
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      await tester.pump(const Duration(milliseconds: 400));
    }

    Future<void> answerAndSubmit(WidgetTester tester) async {
      await tester.enterText(find.byType(TextField), 'Clean water');
      await tester.pump();
      await tapSubmit(tester);
      await completeProfileStep(tester);
    }

    testWidgets('a delivered answer sheet is thanked for and marked sent', (
      tester,
    ) async {
      stubPost(succeeds: true);
      stubFetch(surveyDoc([textQuestion('q1', 'What do you need?')]));
      await pumpScreen(tester);

      await answerAndSubmit(tester);

      expect(find.text('Thank you for taking this survey'), findsOneWidget);
      final submission = (await db.select(db.submissions).get()).single;
      expect(submission.uploaded, isTrue);
    });

    testWidgets('a failed post keeps the answers and queues them', (
      tester,
    ) async {
      stubPost(succeeds: false);
      stubFetch(surveyDoc([textQuestion('q1', 'What do you need?')]));
      await pumpScreen(tester);

      await answerAndSubmit(tester);
      // The profile step shows its own snackbar and this one is *queued*
      // behind it, so it cannot appear until that one's four seconds are up.
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }
      await tester.pump(const Duration(seconds: 5));
      await tester.pump(const Duration(milliseconds: 400));

      expect(find.text('Saved offline — pending upload'), findsOneWidget);
      expect(await submissionCount(), 1);
      expect((await db.select(db.outboxEntries).get()).length, 1);
    });

    testWidgets(
      'every checked choice of a selectMultiple reaches the payload',
      (tester) async {
        stubPost(succeeds: true);
        stubFetch(
          surveyDoc([
            choiceQuestion('q1', 'Which services?', 'selectmultiple'),
          ]),
        );
        await pumpScreen(tester);

        await tester.tap(find.widgetWithText(CheckboxListTile, 'Water'));
        await tester.pump();
        await tester.tap(find.widgetWithText(CheckboxListTile, 'Power'));
        await tester.pump();
        await tapSubmit(tester);
        await completeProfileStep(tester);

        final body =
            verify(
                  () => api.sendJsonDynamic(
                    any(),
                    body: captureAny(named: 'body'),
                    method: any(named: 'method'),
                    authHeader: any(named: 'authHeader'),
                  ),
                ).captured.single
                as Map<String, dynamic>;
        final answers = body['answers'] as List<dynamic>;
        expect(answers.single, hasLength(2));
      },
    );

    testWidgets('a signed-in respondent lands back on the resources list', (
      tester,
    ) async {
      // `navigateOnwardAndFinish` branches on `prefData.isLoggedIn()`. The
      // screen never watches `sessionProvider`, so reading it with
      // `.valueOrNull` yielded null and sent a signed-in respondent to the
      // login screen — the Phase 100 shape again, now awaited.
      stubPost(succeeds: true);
      stubFetch(surveyDoc([textQuestion('q1', 'What do you need?')]));
      await pumpScreen(
        tester,
        user: buildUserRow(id: 'user-a', name: 'jane'),
      );

      await answerAndSubmit(tester);
      await tester.pumpAndSettle();

      expect(find.text('RESOURCES_PAGE'), findsOneWidget);
    });

    testWidgets('a signed-out respondent lands on the login screen', (
      tester,
    ) async {
      stubPost(succeeds: true);
      stubFetch(surveyDoc([textQuestion('q1', 'What do you need?')]));
      await pumpScreen(tester);

      await answerAndSubmit(tester);
      await tester.pumpAndSettle();

      expect(find.text('LOGIN_PAGE'), findsOneWidget);
    });
  });
}

class _StubSession extends SessionNotifier {
  _StubSession(this.user);
  final UserRow? user;

  @override
  Future<UserRow?> build() async => user;
}
