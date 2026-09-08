import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:myplanet/core/prefs/planet_prefs.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/exam_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/ui/courses/take_course_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../support/mock_planet_api.dart';
import '../../support/widget_harness.dart';

/// `TakeCourseFragment.changeNextButtonState` (`TakeCourseFragment.kt:315-338`)
/// — the per-step refusal to advance past an unanswered assessment on the
/// MyPlanet Onboarding course. Unported until Phase 139; reported by Phase 128
/// as its item 4.
///
/// **Distinct from the finish gate**, which is `onFinishStep`'s
/// `hasUnfinishedSurveys` and has its own tests in
/// `take_course_screen_test.dart`. That one fires on the *last* step, where
/// Next has already been replaced by Finish; this one fires on every earlier
/// step. Two independent reads, two independent messages.
///
/// Every course here is seeded from a real-shaped course document through the
/// real mappers, so `exams.stepId == course_steps.id` is the join under test
/// rather than a hand-written row — the Phase 113 lesson. Every submission is
/// authored by `SubmissionsRepository`, never inserted, because the whole
/// question is whether the lock reads what the writers write.
void main() {
  const mandatoryCourseId = '4e6b78800b6ad18b4e8b0e1e38a98cac';

  /// A two-step course. The **first** step carries the assessment, so the step
  /// on screen is one the lock can hold and there is a second step for Next to
  /// advance to — Kotlin's lock is computed for the displayed step
  /// (`steps.getOrNull(position - 1)`) and blocks moving away from it.
  Map<String, Object?> courseDoc({
    required String courseId,
    Map<String, Object?>? exam,
    Map<String, Object?>? survey,
  }) => {
    '_id': courseId,
    'courseTitle': 'Onboarding',
    'steps': [
      {'stepTitle': 'First', 'exam': ?exam, 'survey': ?survey},
      {'stepTitle': 'Second'},
    ],
  };

  Map<String, Object?> examDoc() => {
    '_id': 'exam-1',
    'type': 'courses',
    'name': 'Step test',
    'questions': [
      {'id': 'q1', 'title': 'One?', 'type': 'input'},
    ],
  };

  Map<String, Object?> surveyDoc() => {
    '_id': 'survey-1',
    'type': 'surveys',
    'name': 'Step survey',
    'questions': [
      {'id': 's1', 'title': 'How was it?', 'type': 'input'},
    ],
  };

  Future<AppDatabase> seed(Map<String, Object?> doc) async {
    final db = AppDatabase.memory();
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

  SubmissionsRepository repositoryFor(AppDatabase db) => SubmissionsRepository(
    MockPlanetApi(),
    db.submissionDao,
    db.submitPhotosDao,
    db.surveyDao,
    db.examDao,
    teamDao: db.teamDao,
  );

  /// Answers the step's exam through to `requires grading` — the status
  /// `saveExamAnswer` writes for the last answer of an explicit submission,
  /// and the one `isStepCompleted`'s `status != 'pending'` accepts.
  ///
  /// The question is an `input` with no answer key, which
  /// `ExamGrading.isTextCorrect` treats as "any answer will do", so one
  /// non-empty value finishes the attempt.
  Future<void> finishExam(AppDatabase db, {String userId = 'user-1'}) async {
    final submissions = repositoryFor(db);
    final exam = (await db.examDao.getById('exam-1'))!;
    final questions = await db.examDao.questionsFor('exam-1');
    final id = await submissions.startExamSession(
      exam: exam,
      questions: questions,
      userId: userId,
      courseId: mandatoryCourseId,
    );
    await submissions.saveExamAnswer(
      submissionId: id,
      question: questions.single,
      answer: const ExamDraftAnswer(value: 'because'),
      isFinal: true,
      isExplicitSubmission: true,
    );
  }

  Future<void> pumpCourse(
    WidgetTester tester, {
    required AppDatabase db,
    String courseId = mandatoryCourseId,
    bool joined = true,
  }) async {
    addTearDown(db.close);
    SharedPreferences.setMockInitialValues({});
    final prefs = PlanetPrefs(await SharedPreferences.getInstance());
    final steps = await db.courseDao.getSteps(courseId);

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
          '/take': (context) => TakeCourseScreen(courseId: courseId),
          '/courses/exam/:examId': (context) =>
              const Scaffold(body: Text('EXAM_ROUTE')),
          '/life/surveys/:surveyId': (context) =>
              const Scaffold(body: Text('SURVEY_ROUTE')),
        },
        overrides: [
          planetPrefsProvider.overrideWithValue(prefs),
          appDatabaseProvider.overrideWithValue(db),
          sessionProvider.overrideWith(
            () => _Session(buildUserRow(id: 'user-1', name: 'ada')),
          ),
          courseProvider(courseId).overrideWith(
            (ref) => Stream.value(
              buildCourseRow(
                id: courseId,
                courseTitle: 'Onboarding',
                userId: joined ? const ['user-1'] : const [],
              ),
            ),
          ),
          courseStepsProvider(
            courseId,
          ).overrideWith((ref) => Stream.value(steps)),
        ],
      ),
    );
    await tester.pumpAndSettle();
  }

  /// The step counter in `_ProgressSection`, which is the only widget that
  /// reflects the current step and nothing else.
  ///
  /// Deliberately not the step *title*: `PageView.builder` keeps an
  /// off-screen page in the element tree, and `find.text` searches the element
  /// tree, so "Second" can be found for a page the learner is not on. A test
  /// that asserted the title would pass whether or not Next was blocked.
  Finder onStep(int number, int total) => find.text('$number / $total');

  Future<void> tapNext(WidgetTester tester) async {
    await tester.tap(find.text('Next'));
    await tester.pumpAndSettle();
  }

  /// Runs the SnackBar's four-second timer out.
  ///
  /// Necessary rather than tidy, and it cost a debugging round: a SnackBar
  /// sits over the navigation bar, and `pumpAndSettle` does **not** clear it —
  /// it returns as soon as no frame is scheduled, and the dismiss timer needs
  /// wall-clock time to be pumped. So a second `tap(find.text('Next'))` lands
  /// on the SnackBar, the tap is silently swallowed (with only a `warnIfMissed`
  /// notice in the log), and the test reads as "the lock never released".
  Future<void> letSnackBarExpire(WidgetTester tester) async {
    await tester.pump(const Duration(seconds: 5));
    await tester.pumpAndSettle();
  }

  group('the mandatory course refuses Next until the step is answered', () {
    testWidgets('an unanswered step test blocks Next with the test message', (
      tester,
    ) async {
      await pumpCourse(
        tester,
        db: await seed(courseDoc(courseId: mandatoryCourseId, exam: examDoc())),
      );

      expect(onStep(1, 2), findsOneWidget);
      await tapNext(tester);

      // `getString(R.string.please_complete_test)` (`:328`), which is
      // "please complete the test to proceed" — a different string from the
      // finish gate's, and one the port had no ARB key for at all.
      expect(find.text('please complete the test to proceed'), findsOneWidget);
      // And the learner did not advance: `onClick` returns before
      // `viewPager2.currentItem += 1` (`:367-370`).
      expect(onStep(1, 2), findsOneWidget);
      expect(onStep(2, 2), findsNothing);
    });

    testWidgets('an unanswered step survey blocks Next with the survey '
        'message', (tester) async {
      await pumpCourse(
        tester,
        db: await seed(
          courseDoc(courseId: mandatoryCourseId, survey: surveyDoc()),
        ),
      );

      await tapNext(tester);

      // `:329` falls through to `please_complete_survey`, whose wording is
      // written for the *finish* gate — "…to finish the course" — because
      // Kotlin shares one string between the two sites. Faithful, odd, and
      // pinned so nobody "fixes" it into a second key.
      expect(
        find.text('please complete the survey to finish the course'),
        findsOneWidget,
      );
      expect(onStep(1, 2), findsOneWidget);
    });

    testWidgets('a step carrying both names the test, not the survey', (
      tester,
    ) async {
      // `:327-330` is `hasExam -> please_complete_test` with the survey as the
      // else, and `isStepCompleted` interrogates the exam row on such a step
      // (`getFirstByStepId` has no type filter and the exam is inserted
      // first), so message and remedy name the same assessment.
      await pumpCourse(
        tester,
        db: await seed(
          courseDoc(
            courseId: mandatoryCourseId,
            exam: examDoc(),
            survey: surveyDoc(),
          ),
        ),
      );

      await tapNext(tester);

      expect(find.text('please complete the test to proceed'), findsOneWidget);
      expect(
        find.text('please complete the survey to finish the course'),
        findsNothing,
      );
    });

    testWidgets('Next moves the page, not just the counter', (tester) async {
      // **A pre-existing defect this lane's own `stepNum` test surfaced, and
      // it made the lock meaningless.** The `PageController` was built inline
      // in `build` as `PageController(initialPage: currentStep)`; `Scrollable`
      // hands a replacement controller the previous `ScrollPosition`'s pixels,
      // so `initialPage` applies once and Previous/Next moved the step counter
      // while leaving the page where it was.
      //
      // Nothing here caught it because every navigation assertion read the
      // counter, which is driven by the same `currentStep` the controller was
      // being handed — the two agreed about the number and disagreed about
      // what was on screen. So this asserts on the *page*: pre-fix the second
      // step's content was never built at all.
      //
      // It matters for this file specifically: with `isUserInputEnabled =
      // false` ported, the buttons are the only way through a course, so a
      // lock guarding a button that does not navigate would have been the only
      // working half of the pair.
      await pumpCourse(
        tester,
        db: await seed(courseDoc(courseId: mandatoryCourseId)),
      );

      expect(find.text('First'), findsOneWidget);
      expect(find.text('Second'), findsNothing);

      await tapNext(tester);

      expect(onStep(2, 2), findsOneWidget);
      expect(find.text('Second'), findsOneWidget);
    });

    testWidgets('Previous moves the page back', (tester) async {
      await pumpCourse(
        tester,
        db: await seed(courseDoc(courseId: mandatoryCourseId)),
      );

      await tapNext(tester);
      expect(find.text('Second'), findsOneWidget);

      await tester.tap(find.text('Previous'));
      await tester.pumpAndSettle();

      expect(onStep(1, 2), findsOneWidget);
      expect(find.text('First'), findsOneWidget);
    });

    testWidgets('a step with no assessment advances', (tester) async {
      // `isStepCompleted` answers **true** for a step with no exam row at all
      // (`?: return true`, `SubmissionsRepositoryImpl.kt:361`), which is what
      // keeps every ordinary step of the onboarding course walkable.
      await pumpCourse(
        tester,
        db: await seed(courseDoc(courseId: mandatoryCourseId)),
      );

      await tapNext(tester);

      expect(onStep(2, 2), findsOneWidget);
      expect(find.textContaining('please complete'), findsNothing);
    });

    testWidgets('a finished attempt releases the lock', (tester) async {
      final db = await seed(
        courseDoc(courseId: mandatoryCourseId, exam: examDoc()),
      );
      await finishExam(db);
      await pumpCourse(tester, db: db);

      await tapNext(tester);

      expect(onStep(2, 2), findsOneWidget);
      expect(find.textContaining('please complete'), findsNothing);
    });

    testWidgets('an answered step survey releases the lock', (tester) async {
      final db = await seed(
        courseDoc(courseId: mandatoryCourseId, survey: surveyDoc()),
      );
      final submissions = repositoryFor(db);
      final survey = (await db.surveyDao.getByCourseId(
        mandatoryCourseId,
      )).single;
      // `createSurveyDraft` writes `status: 'complete'`, which is what
      // `saveExamAnswer` writes for a survey's last answer in Kotlin — and
      // `'complete' != 'pending'`, so the step counts as done.
      await submissions.createSurveyDraft(
        survey: survey,
        questions: await db.surveyDao.questionsFor('survey-1'),
        userId: 'user-1',
      );
      await pumpCourse(tester, db: db);

      await tapNext(tester);

      expect(onStep(2, 2), findsOneWidget);
    });

    testWidgets('a half-answered attempt still blocks', (tester) async {
      // **The difference between `isStepCompleted` and `hasSubmission`, at the
      // screen.** `hasSubmission` is status-blind, so it is satisfied the
      // moment `startExamSession` writes its `pending` row — which is what
      // makes the step tile read *Retake Test*. The lock needs
      // `status != 'pending'` (`SubmissionDao.kt:24`), so opening the exam and
      // answering nothing must not buy the learner the next step.
      final db = await seed(
        courseDoc(courseId: mandatoryCourseId, exam: examDoc()),
      );
      final submissions = repositoryFor(db);
      final exam = (await db.examDao.getById('exam-1'))!;
      await submissions.startExamSession(
        exam: exam,
        questions: await db.examDao.questionsFor('exam-1'),
        userId: 'user-1',
        courseId: mandatoryCourseId,
      );
      await pumpCourse(tester, db: db);

      await tapNext(tester);

      expect(find.text('please complete the test to proceed'), findsOneWidget);
      expect(onStep(1, 2), findsOneWidget);
    });

    testWidgets('another learner\'s finished attempt does not release it', (
      tester,
    ) async {
      // `userId IS :userId` (`SubmissionDao.kt:24`). A shared handset must not
      // let one learner through on somebody else's answers.
      final db = await seed(
        courseDoc(courseId: mandatoryCourseId, exam: examDoc()),
      );
      await finishExam(db, userId: 'someone-else');
      await pumpCourse(tester, db: db);

      await tapNext(tester);

      expect(find.text('please complete the test to proceed'), findsOneWidget);
      expect(onStep(1, 2), findsOneWidget);
    });

    testWidgets('any other course is never locked', (tester) async {
      // `:316` gates the whole body on one hardcoded course id and `:336` is
      // an unconditional `isNextStepLocked = false` for everything else. An
      // ordinary course's unanswered step test does not hold the learner up.
      await pumpCourse(
        tester,
        courseId: 'course-ordinary',
        db: await seed(courseDoc(courseId: 'course-ordinary', exam: examDoc())),
      );

      await tapNext(tester);

      expect(onStep(2, 2), findsOneWidget);
      expect(find.textContaining('please complete'), findsNothing);
    });

    testWidgets('a learner who has not joined is not locked', (tester) async {
      // A deviation, pinned so it is a decision rather than a drift.
      // `changeNextButtonState` has no membership test — Kotlin's is on the
      // button (`updateNavigationVisibility:256-270` hides Next for a
      // non-member) — but `onResume:154-160` puts Next back with no such test,
      // so a returning ex-member *can* meet Kotlin's lock. The port has no
      // membership gate on Next at all, so the gate went on the lock: a
      // learner cannot be blocked by an assessment `_StepContent` is not
      // offering them.
      await pumpCourse(
        tester,
        joined: false,
        db: await seed(courseDoc(courseId: mandatoryCourseId, exam: examDoc())),
      );

      // The tile is hidden for a non-member, which is the reason.
      expect(find.textContaining('take test'), findsNothing);
      await tapNext(tester);

      expect(onStep(2, 2), findsOneWidget);
    });
  });

  testWidgets('the lock releases on returning from the exam, without leaving '
      'the course', (tester) async {
    // **The reachability half, and the one Kotlin gets for free.** Kotlin
    // re-evaluates the lock only on a page-selection event
    // (`onPageSelected:309`; `onResume` does *not* call
    // `changeNextButtonState`), and stays correct anyway because
    // `openCallFragment`'s `replace()` destroys `TakeCourseFragment`'s view —
    // so returning from the exam rebuilds the pager and re-dispatches
    // `onPageSelected`. A `context.push` leaves this screen mounted, so
    // without sharing the step tile's post-return `invalidate` the lock would
    // still be holding a step the learner had just finished, until they left
    // the course entirely.
    final db = await seed(
      courseDoc(courseId: mandatoryCourseId, exam: examDoc()),
    );
    await pumpCourse(tester, db: db);

    await tapNext(tester);
    expect(find.text('please complete the test to proceed'), findsOneWidget);
    await letSnackBarExpire(tester);

    await tester.tap(find.textContaining('take test'));
    await tester.pumpAndSettle();
    expect(find.text('EXAM_ROUTE'), findsOneWidget);

    // The learner sits the exam on the pushed screen…
    await finishExam(db);

    // …and comes back to the step.
    GoRouter.of(tester.element(find.text('EXAM_ROUTE'))).pop();
    await tester.pumpAndSettle();

    await tapNext(tester);
    expect(onStep(2, 2), findsOneWidget);
  });

  testWidgets('the lock cannot be swiped past', (tester) async {
    // `binding.viewPager2.isUserInputEnabled = false`
    // (`TakeCourseFragment.kt:137`). A swipe reaches `onPageChanged`
    // directly, so while this `PageView` scrolled, the refusal to advance an
    // unanswered step was a suggestion.
    await pumpCourse(
      tester,
      db: await seed(courseDoc(courseId: mandatoryCourseId, exam: examDoc())),
    );

    await tester.drag(find.byType(PageView), const Offset(-600, 0));
    await tester.pumpAndSettle();

    expect(onStep(1, 2), findsOneWidget);
    expect(onStep(2, 2), findsNothing);
  });
}

class _Session extends SessionNotifier {
  _Session(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}
