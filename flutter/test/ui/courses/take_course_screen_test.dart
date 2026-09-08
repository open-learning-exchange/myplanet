import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:drift/drift.dart' show Value;
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/exam_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/ratings_provider.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/ui/courses/take_course_screen.dart';
import 'package:myplanet/ui/surveys/take_survey_screen.dart';

import '../../support/widget_harness.dart';
import '../../support/mock_planet_api.dart';

/// Mirrors the test-session notifier in `session_provider_test.dart`: returns a
/// fixed user (or none) without touching the database or prefs, so the
/// take-course screen has a `userId` for the rating check.
class _TestServerConfig extends ServerConfigNotifier {
  _TestServerConfig(this.config);
  final ServerConfig? config;
  @override
  ServerConfig? build() => config;
}

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

UserRow _user() => UserRow(
  id: 'user-1',
  couchId: 'org.couchdb.user:ada',
  rev: '1-a',
  name: 'ada',
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

/// `TakeCourseScreen` records a `course_progress` row for the step it opens on
/// (Kotlin's `withResumed { launchSaveCourseProgress() }`), and that queues an
/// outbox row — which reaches `serverConfigProvider` and through it
/// `planetPrefsProvider`, the harness trap that throws `UnimplementedError`
/// unless overridden. Before the on-mount recording existed, no test here ever
/// reached it. With no server configured the queueing is skipped, which is all
/// these tests need.
Future<Override> _prefsOverride() async {
  SharedPreferences.setMockInitialValues({});
  return planetPrefsProvider.overrideWithValue(
    PlanetPrefs(await SharedPreferences.getInstance()),
  );
}

void main() {
  /// Pushes the take-course screen onto a router so `context.pop()` (the finish
  /// handler and the back button) has somewhere to return to — the pattern
  /// `become_member_screen_test.dart` uses for the same reason.
  Future<void> pumpScreen(
    WidgetTester tester, {
    List<Override> overrides = const [],
    Map<String, WidgetBuilder> extraTargets = const {},
  }) async {
    await tester.pumpWidget(
      wrapScreen(
        Builder(
          builder: (context) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              final router = GoRouter.of(context);
              final location = router
                  .routerDelegate
                  .currentConfiguration
                  .last
                  .matchedLocation;
              if (location != '/take') {
                router.push('/take');
              }
            });
            return const Scaffold(body: Text('ROOT_PAGE'));
          },
        ),
        pushTargets: {
          '/take': (context) => const TakeCourseScreen(courseId: 'course-1'),
          ...extraTargets,
        },
        overrides: [
          await _prefsOverride(),
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
          courseProvider('course-1').overrideWith(
            (ref) => Stream.value(
              buildCourseRow(id: 'course-1', courseTitle: 'Algebra'),
            ),
          ),
          courseStepsProvider('course-1').overrideWith(
            (ref) => Stream.value([buildStepRow(id: 's1', stepTitle: 'First')]),
          ),
          ...overrides,
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  /// The single step's Finish button, located by its label. With one step the
  /// Next button is absent and Finish is shown directly.
  final finishButton = find.text('Finish');

  testWidgets('finishing an unrated course offers the rating dialog', (
    tester,
  ) async {
    final db = AppDatabase.memory();
    addTearDown(db.close);

    await pumpScreen(
      tester,
      overrides: [
        await _prefsOverride(),
        appDatabaseProvider.overrideWith((ref) {
          ref.onDispose(db.close);
          return db;
        }),
        // No existing rating → the dialog's summary stream resolves empty.
        ratingSummaryProvider((
          type: 'course',
          itemId: 'course-1',
        )).overrideWith(
          (ref) => Stream.value(
            const RatingSummary(average: 0, total: 0, userRating: null),
          ),
        ),
      ],
    );

    expect(find.text('Algebra'), findsOneWidget);
    await tester.tap(finishButton);
    await tester.pumpAndSettle();

    // The completion-rating dialog appeared: the dialog asks the user to rate.
    expect(find.byType(AlertDialog), findsOneWidget);
    expect(find.byIcon(Icons.star_border), findsWidgets);
  });

  testWidgets('dismissing the rating dialog pops back to the source', (
    tester,
  ) async {
    final db = AppDatabase.memory();
    addTearDown(db.close);

    await pumpScreen(
      tester,
      overrides: [
        await _prefsOverride(),
        appDatabaseProvider.overrideWith((ref) {
          ref.onDispose(db.close);
          return db;
        }),
        ratingSummaryProvider((
          type: 'course',
          itemId: 'course-1',
        )).overrideWith(
          (ref) => Stream.value(
            const RatingSummary(average: 0, total: 0, userRating: null),
          ),
        ),
      ],
    );

    await tester.tap(finishButton);
    await tester.pumpAndSettle();

    // Cancel the dialog → the course screen pops, returning to ROOT_PAGE.
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(find.byType(TakeCourseScreen), findsNothing);
    expect(find.text('ROOT_PAGE'), findsOneWidget);
  });

  testWidgets('finishing an already-rated course skips the dialog', (
    tester,
  ) async {
    final db = AppDatabase.memory();
    addTearDown(db.close);

    await pumpScreen(
      tester,
      overrides: [
        await _prefsOverride(),
        appDatabaseProvider.overrideWith((ref) {
          ref.onDispose(db.close);
          return db;
        }),
        // The one-shot finish check reads the repo, not this stream; but the
        // dialog (if it had opened) would watch it. Seed a real rating so the
        // repo's `summary` reports the user has rated.
      ],
    );

    // Seed an existing course rating for the signed-in user.
    await RatingsRepository(MockPlanetApi(), db.ratingDao, db.userDao).submit(
      type: 'course',
      itemId: 'course-1',
      title: 'Algebra',
      userId: 'user-1',
      rate: 4,
    );

    await tester.tap(finishButton);
    await tester.pumpAndSettle();

    // No rating dialog: the course was already rated, so Finish pops directly.
    expect(find.byType(AlertDialog), findsNothing);
    expect(find.byType(TakeCourseScreen), findsNothing);
    expect(find.text('ROOT_PAGE'), findsOneWidget);
  });

  /// The mandatory-survey gate: finishing the MyPlanet Onboarding course
  /// (course id `4e6b…`) with an unsubmitted course-attached survey shows a
  /// toast and blocks the finish — a port of `TakeCourseFragment`'s
  /// `MANDATORY_SURVEY_COURSE_ID` check.
  const mandatoryCourseId = '4e6b78800b6ad18b4e8b0e1e38a98cac';

  testWidgets('mandatory survey blocks finish with a toast', (tester) async {
    final db = AppDatabase.memory();
    addTearDown(db.close);

    // Attach an unsubmitted survey to the mandatory course.
    await db.surveyDao.upsertAll([
      SurveysCompanion.insert(
        id: 'survey-mandatory',
        courseId: const Value(mandatoryCourseId),
        name: const Value('Onboarding survey'),
      ),
    ], {});

    await tester.pumpWidget(
      wrapScreen(
        Builder(
          builder: (context) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              final router = GoRouter.of(context);
              final location = router
                  .routerDelegate
                  .currentConfiguration
                  .last
                  .matchedLocation;
              if (location != '/take') {
                router.push('/take');
              }
            });
            return const Scaffold(body: Text('ROOT_PAGE'));
          },
        ),
        pushTargets: {
          '/take': (context) =>
              const TakeCourseScreen(courseId: mandatoryCourseId),
        },
        overrides: [
          await _prefsOverride(),
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
          courseProvider(mandatoryCourseId).overrideWith(
            (ref) => Stream.value(
              buildCourseRow(id: mandatoryCourseId, courseTitle: 'Onboarding'),
            ),
          ),
          courseStepsProvider(mandatoryCourseId).overrideWith(
            (ref) => Stream.value([buildStepRow(id: 's1', stepTitle: 'First')]),
          ),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          ratingSummaryProvider((
            type: 'course',
            itemId: mandatoryCourseId,
          )).overrideWith(
            (ref) => Stream.value(
              const RatingSummary(average: 0, total: 0, userRating: null),
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Finish'));
    await tester.pumpAndSettle();

    // The toast appeared and the rating dialog did not — the finish was blocked.
    expect(
      find.text('please complete the survey to finish the course'),
      findsOneWidget,
    );
    expect(find.byType(AlertDialog), findsNothing);
  });

  testWidgets('a completed attached survey lets the course finish', (
    tester,
  ) async {
    // **The round trip, at the screen the learner actually taps.** Phase 125.
    //
    // Pre-fix this test failed with the toast still on screen and no rating
    // dialog: `createSurveyDraft` stored `parentId: 'survey-mandatory'` while
    // `hasUnfinishedSurveys` counted `'survey-mandatory@<course>'`, so the
    // MyPlanet Onboarding course could not be finished however many times the
    // learner answered its survey.
    //
    // The submission is authored by the production writer through
    // `SurveysRepository.submitResponse`, not inserted by hand — a fixture
    // that writes the key itself is what let the defect survive four phases of
    // green tests.
    final db = AppDatabase.memory();
    addTearDown(db.close);
    await db.surveyDao.upsertAll([
      SurveysCompanion.insert(
        id: 'survey-mandatory',
        courseId: const Value(mandatoryCourseId),
        name: const Value('Onboarding survey'),
      ),
    ], {});
    final api = MockPlanetApi();
    final submissions = SubmissionsRepository(
      api,
      db.submissionDao,
      db.submitPhotosDao,
      db.surveyDao,
      db.examDao,
      teamDao: db.teamDao,
    );
    final answered = await SurveysRepository(
      api,
      db.surveyDao,
      db.examDao,
      submissions,
    ).submitResponse('survey-mandatory', 'user-1', const {});
    expect(answered, isNotNull);

    await tester.pumpWidget(
      wrapScreen(
        Builder(
          builder: (context) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              final router = GoRouter.of(context);
              final location = router
                  .routerDelegate
                  .currentConfiguration
                  .last
                  .matchedLocation;
              if (location != '/take') {
                router.push('/take');
              }
            });
            return const Scaffold(body: Text('ROOT_PAGE'));
          },
        ),
        pushTargets: {
          '/take': (context) =>
              const TakeCourseScreen(courseId: mandatoryCourseId),
        },
        overrides: [
          await _prefsOverride(),
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
          courseProvider(mandatoryCourseId).overrideWith(
            (ref) => Stream.value(
              buildCourseRow(id: mandatoryCourseId, courseTitle: 'Onboarding'),
            ),
          ),
          courseStepsProvider(mandatoryCourseId).overrideWith(
            (ref) => Stream.value([buildStepRow(id: 's1', stepTitle: 'First')]),
          ),
          appDatabaseProvider.overrideWith((ref) {
            ref.onDispose(db.close);
            return db;
          }),
          ratingSummaryProvider((
            type: 'course',
            itemId: mandatoryCourseId,
          )).overrideWith(
            (ref) => Stream.value(
              const RatingSummary(average: 0, total: 0, userRating: null),
            ),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Finish'));
    await tester.pumpAndSettle();

    // No toast, and the finish reached the rating dialog it is gated in front
    // of — the learner's answer counted.
    expect(
      find.text('please complete the survey to finish the course'),
      findsNothing,
    );
    expect(find.byType(AlertDialog), findsOneWidget);
  });

  /// Fills an in-memory database from a real-shaped course document through
  /// the real mappers, so the `exams.stepId == course_steps.id` join is what
  /// the test exercises rather than a hand-faked row.
  Future<AppDatabase> seedStepAssessments() async {
    final db = AppDatabase.memory();
    const doc = {
      '_id': 'course-1',
      'courseTitle': 'Algebra',
      'steps': [
        {
          'stepTitle': 'First',
          'exam': {
            '_id': 'exam-1',
            'type': 'courses',
            'name': 'Step test',
            'questions': [
              {'id': 'q1', 'title': 'One?', 'type': 'input'},
            ],
          },
          'survey': {
            '_id': 'survey-1',
            'type': 'surveys',
            'name': 'Step survey',
            'questions': [
              {'id': 's1', 'title': 'How was it?', 'type': 'input'},
            ],
          },
        },
      ],
    };
    final parsed = CourseMapper.fromDoc(doc)!;
    await db.courseDao.upsertAll([parsed.course], parsed.steps);
    for (final mapping in ExamMapper.fromCourseDoc(
      doc,
      stepIdFor: CourseMapper.stepIdFor,
    )) {
      await db.examDao.upsertAll(
        [mapping.exam],
        {mapping.exam.id.value: mapping.questions},
      );
    }
    for (final mapping in SurveyMapper.fromCourseDoc(
      doc,
      stepIdFor: CourseMapper.stepIdFor,
    )) {
      await db.surveyDao.upsertAll(
        [mapping.survey],
        {mapping.survey.id.value: mapping.questions},
      );
    }
    return db;
  }

  Future<void> pumpSeededStep(
    WidgetTester tester, {
    required bool joined,
    Future<void> Function(AppDatabase db, SubmissionsRepository submissions)?
    afterSeed,
  }) async {
    final db = await seedStepAssessments();
    addTearDown(db.close);
    if (afterSeed != null) {
      await afterSeed(
        db,
        SubmissionsRepository(
          MockPlanetApi(),
          db.submissionDao,
          db.submitPhotosDao,
          db.surveyDao,
          db.examDao,
          teamDao: db.teamDao,
        ),
      );
    }
    final steps = await db.courseDao.getSteps('course-1');
    await pumpScreen(
      tester,
      // Sentinels rather than the real screens: what is under test is that the
      // push matches a route at all, and both destinations need a provider
      // graph of their own.
      extraTargets: {
        '/courses/exam/:examId': (context) => Scaffold(
          body: Text(
            'EXAM_ROUTE ${GoRouterState.of(context).pathParameters['examId']}',
          ),
        ),
        '/life/surveys/:surveyId': (context) => Scaffold(
          body: Text(
            'SURVEY_ROUTE '
            '${GoRouterState.of(context).pathParameters['surveyId']}',
          ),
        ),
      },
      overrides: [
        await _prefsOverride(),
        appDatabaseProvider.overrideWithValue(db),
        courseProvider('course-1').overrideWith(
          (ref) => Stream.value(
            buildCourseRow(
              id: 'course-1',
              courseTitle: 'Algebra',
              userId: joined ? const ['user-1'] : const [],
            ),
          ),
        ),
        courseStepsProvider(
          'course-1',
        ).overrideWith((ref) => Stream.value(steps)),
      ],
    );
  }

  testWidgets(
    'the step view offers Take test and Take survey for an embedded pair',
    (tester) async {
      // Phase 113. Same reachability proof as `course_detail_screen_test`, on
      // the other entry into `TakeExamScreen`. Neither `stepExamProvider` nor
      // `stepSurveysProvider` is overridden: both do their own lookup against
      // a database filled by the mappers the courses walk now runs.
      await pumpSeededStep(tester, joined: true);

      // The label is Kotlin's `take_test`, "take test [%d]", where `%d` is
      // `exams.size` — not the count-less "Take test" the port used to
      // hardcode. Phase 128.
      expect(find.text('take test [1]'), findsOneWidget);
      expect(find.text('Record survey'), findsOneWidget);

      // **Rendering is not reachability**, which is what the first cut of this
      // test asserted and all it asserted. Both buttons concatenated the route
      // *pattern* — `Routes.exam` is `/courses/exam/:examId`, so the push was
      // `/courses/exam/:examId/exam-1` — which matches no route and drops the
      // learner on go_router's error page.
      await tester.tap(find.text('take test [1]'));
      await tester.pumpAndSettle();
      expect(find.text('EXAM_ROUTE exam-1'), findsOneWidget);
    },
  );

  testWidgets('Record survey opens the signed-in survey screen', (
    tester,
  ) async {
    // Kotlin's `btnTakeSurvey` opens `ExamTakingFragment` against the user's
    // own submission (`SubmissionsAdapter.openSurvey`). The port aimed at the
    // anonymous public-survey screen, on a route that could not match anyway.
    await pumpSeededStep(tester, joined: true);
    await tester.tap(find.text('Record survey'));
    await tester.pumpAndSettle();
    expect(find.text('SURVEY_ROUTE survey-1'), findsOneWidget);
  });

  testWidgets('a course the learner has not joined offers neither', (
    tester,
  ) async {
    // `CourseStepFragment.onViewCreated` hides both after
    // `hideTestIfNoQuestion` has shown them, when `!userHasCourse`.
    await pumpSeededStep(tester, joined: false);
    expect(find.text('take test [1]'), findsNothing);
    expect(find.text('Record survey'), findsNothing);
  });

  // ---------------------------------------------------------------------------
  // Phase 128 — `hideTestIfNoQuestion`'s label swap
  // ---------------------------------------------------------------------------
  //
  // `CourseStepFragment.hideTestIfNoQuestion` (`:241-262`) picks each button's
  // wording off `getCourseStepData`'s `hasExam`/`hasSurvey`, which are
  // `hasSubmission(stepExams[0].id, step.courseId, userId, "exam"/"survey")`
  // on the composite `parentId`. The port hardcoded `l10n.takeTest` and
  // `l10n.recordSurvey`, so a learner who had already sat the test was invited
  // to take it for the first time, every time.
  //
  // Both submissions here are authored by the production writer against rows
  // the mappers produced from a document shaped like the server's, so the key
  // the writer stores and the key this reader asks for are the same value for
  // the same reason they are in the field.

  testWidgets('the test tile says Retake once the learner has an attempt', (
    tester,
  ) async {
    // Pre-fix: `Expected: exactly one matching candidate / Actual: zero` for
    // "Retake Test [1]", with "Take test" on screen instead.
    await pumpSeededStep(
      tester,
      joined: true,
      afterSeed: (db, submissions) async {
        final exam = (await db.examDao.getById('exam-1'))!;
        await submissions.startExamSession(
          exam: exam,
          questions: await db.examDao.questionsFor('exam-1'),
          userId: 'user-1',
          courseId: 'course-1',
        );
      },
    );

    expect(find.text('Retake Test [1]'), findsOneWidget);
    expect(find.text('take test [1]'), findsNothing);
    // The survey is untouched, so its own label must not have moved with it.
    expect(find.text('Record survey'), findsOneWidget);
  });

  testWidgets('the survey tile says Redo once the sheet exists', (
    tester,
  ) async {
    // The half that Phase 125's writer fix is the precondition for: before it
    // the sheet carried the bare `survey-1` and this reader's
    // `survey-1@course-1` could never match, so the label could not have
    // swapped however many times the learner answered.
    await pumpSeededStep(
      tester,
      joined: true,
      afterSeed: (db, submissions) async {
        final survey = (await db.surveyDao.getByCourseId('course-1')).single;
        await submissions.createSurveyDraft(
          survey: survey,
          questions: await db.surveyDao.questionsFor('survey-1'),
          userId: 'user-1',
        );
      },
    );

    expect(find.text('redo survey'), findsOneWidget);
    expect(find.text('Record survey'), findsNothing);
    expect(find.text('take test [1]'), findsOneWidget);
  });

  testWidgets('Redo survey still opens the survey screen', (tester) async {
    // A swapped label is not a swapped destination. `btnTakeSurvey`'s listener
    // is set once and reads `stepSurvey[0].id` either way.
    await pumpSeededStep(
      tester,
      joined: true,
      afterSeed: (db, submissions) async {
        final survey = (await db.surveyDao.getByCourseId('course-1')).single;
        await submissions.createSurveyDraft(
          survey: survey,
          questions: await db.surveyDao.questionsFor('survey-1'),
          userId: 'user-1',
        );
      },
    );

    await tester.tap(find.text('redo survey'));
    await tester.pumpAndSettle();
    expect(find.text('SURVEY_ROUTE survey-1'), findsOneWidget);
  });

  testWidgets('the label refreshes when the learner returns from the exam', (
    tester,
  ) async {
    // **The reachability question, for a label whose only trigger is a
    // submission made on another screen.** Kotlin recomputes on every view
    // creation: `btnTakeTest` → `openCallFragment` →
    // `FragmentNavigator.replaceFragment(addToBackStack = true)` is a
    // `replace()`, so popping back recreates `CourseStepFragment`,
    // `onViewCreated` runs again and `getCourseStepData` re-queries.
    //
    // `context.push` does not: it leaves `TakeCourseScreen` mounted, so
    // `_StepContent` keeps its listener, the `autoDispose` family member is
    // never disposed and its future never re-runs. Pre-fix this was
    // `Expected: exactly one matching candidate / Actual: Found 0 widgets with
    // text "Retake Test [1]"` — the swap this whole phase exists for could not
    // be observed without leaving the course and re-entering it.
    late AppDatabase database;
    late SubmissionsRepository submissions;
    await pumpSeededStep(
      tester,
      joined: true,
      afterSeed: (db, repo) async {
        database = db;
        submissions = repo;
      },
    );

    expect(find.text('take test [1]'), findsOneWidget);
    await tester.tap(find.text('take test [1]'));
    await tester.pumpAndSettle();
    expect(find.text('EXAM_ROUTE exam-1'), findsOneWidget);

    // The learner sits the exam on the pushed screen.
    final exam = (await database.examDao.getById('exam-1'))!;
    await submissions.startExamSession(
      exam: exam,
      questions: await database.examDao.questionsFor('exam-1'),
      userId: 'user-1',
      courseId: 'course-1',
    );

    // …and comes back to the step.
    GoRouter.of(tester.element(find.text('EXAM_ROUTE exam-1'))).pop();
    await tester.pumpAndSettle();

    expect(find.text('Retake Test [1]'), findsOneWidget);
    expect(find.text('take test [1]'), findsNothing);
  });

  testWidgets('answering a step survey returns to the step and relabels the '
      'tile', (tester) async {
    // **Phase 128 built this refresh and Phase 128's own item 6 recorded that
    // half of it was unreachable.** `TakeSurveyScreen._submit` ended with
    // `context.go('${Routes.submissions}/<id>')` — `go`, not a pop — so
    // answering a course-step survey unmounted `TakeCourseScreen` outright,
    // the tile's `await context.push(…)` never resumed, and
    // `refreshAssessment` short-circuited on `context.mounted`. The *redo
    // survey* relabel was therefore unreachable on the one path that produces
    // the submission it reads: the learner had to leave the course and come
    // back to see it.
    //
    // The survey target here is the **real** `TakeSurveyScreen`, not a
    // sentinel. That is the whole point — the existing test that looks like it
    // covers this path stubs the route with a `Text` widget, so the real
    // screen's exit was never exercised by anything. A fixture that fakes the
    // return proves nothing about whether the return happens.
    tester.view.physicalSize = const Size(1000, 3000);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);

    final db = await seedStepAssessments();
    addTearDown(db.close);
    final steps = await db.courseDao.getSteps('course-1');

    await tester.pumpWidget(
      wrapScreen(
        Builder(
          builder: (context) {
            WidgetsBinding.instance.addPostFrameCallback((_) {
              final router = GoRouter.of(context);
              final location = router
                  .routerDelegate
                  .currentConfiguration
                  .last
                  .matchedLocation;
              if (location != '/take') router.push('/take');
            });
            return const Scaffold(body: Text('ROOT_PAGE'));
          },
        ),
        pushTargets: {
          '/take': (context) => const TakeCourseScreen(courseId: 'course-1'),
          '/life/surveys/:surveyId': (context) => TakeSurveyScreen(
            surveyId: GoRouterState.of(context).pathParameters['surveyId']!,
          ),
        },
        overrides: [
          await _prefsOverride(),
          appDatabaseProvider.overrideWithValue(db),
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
          serverConfigProvider.overrideWith(() => _TestServerConfig(null)),
          courseProvider('course-1').overrideWith(
            (ref) => Stream.value(
              buildCourseRow(
                id: 'course-1',
                courseTitle: 'Algebra',
                userId: const ['user-1'],
              ),
            ),
          ),
          courseStepsProvider(
            'course-1',
          ).overrideWith((ref) => Stream.value(steps)),
        ],
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Record survey'), findsOneWidget);
    await tester.tap(find.text('Record survey'));
    await tester.pumpAndSettle();

    // The real survey screen, answered and submitted.
    expect(find.byType(TakeSurveyScreen), findsOneWidget);
    await tester.enterText(find.byType(TextField), 'it was fine');
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Submit survey'));
    // Never pumpAndSettle straight after Submit: while `submitting` is true
    // the button holds an indefinite CircularProgressIndicator.
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    await tester.tap(find.widgetWithText(TextButton, 'Finish'));
    await tester.pumpAndSettle();

    // Back on the step — and the tile now reads *redo survey*, which is only
    // observable because the pop let the `await` resume.
    expect(find.byType(TakeSurveyScreen), findsNothing);
    expect(find.text('redo survey'), findsOneWidget);
    expect(find.text('Record survey'), findsNothing);
  });

  testWidgets('an attempt by another learner does not swap the label', (
    tester,
  ) async {
    // `countByUserParentAndType` is user-scoped (`SubmissionDao.kt:23`), so a
    // shared handset must not tell the signed-in learner they have already
    // sat a test somebody else sat.
    await pumpSeededStep(
      tester,
      joined: true,
      afterSeed: (db, submissions) async {
        final exam = (await db.examDao.getById('exam-1'))!;
        await submissions.startExamSession(
          exam: exam,
          questions: await db.examDao.questionsFor('exam-1'),
          userId: 'someone-else',
          courseId: 'course-1',
        );
      },
    );

    expect(find.text('take test [1]'), findsOneWidget);
    expect(find.text('Retake Test [1]'), findsNothing);
  });
}
