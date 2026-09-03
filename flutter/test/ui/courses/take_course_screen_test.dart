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
import 'package:myplanet/repository/ratings_repository.dart';
import 'package:myplanet/ui/courses/take_course_screen.dart';

import '../../support/widget_harness.dart';
import '../../support/mock_planet_api.dart';

/// Mirrors the test-session notifier in `session_provider_test.dart`: returns a
/// fixed user (or none) without touching the database or prefs, so the
/// take-course screen has a `userId` for the rating check.
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
  }) async {
    final db = await seedStepAssessments();
    addTearDown(db.close);
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

      expect(find.text('Take test'), findsOneWidget);
      expect(find.text('Record survey'), findsOneWidget);

      // **Rendering is not reachability**, which is what the first cut of this
      // test asserted and all it asserted. Both buttons concatenated the route
      // *pattern* — `Routes.exam` is `/courses/exam/:examId`, so the push was
      // `/courses/exam/:examId/exam-1` — which matches no route and drops the
      // learner on go_router's error page.
      await tester.tap(find.text('Take test'));
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
    expect(find.text('Take test'), findsNothing);
    expect(find.text('Record survey'), findsNothing);
  });
}
