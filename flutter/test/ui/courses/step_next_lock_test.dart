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

  /// A three-step course whose assessment sits on the **middle** step.
  ///
  /// This is what distinguishes `steps[currentStep]` from `steps[0]`, and
  /// nothing else in this file does: in a two-step course the lock is only
  /// ever *consulted* at index 0, because at index 1 `onNext` is null and
  /// `_NavigationBar` renders Finish, so whatever the lock returns for the
  /// last step is computed and discarded. Kotlin reads the displayed step
  /// (`steps.getOrNull(position - 1)`, `TakeCourseFragment.kt:317`).
  Map<String, Object?> middleStepCourseDoc() => {
    '_id': mandatoryCourseId,
    'courseTitle': 'Onboarding',
    'steps': [
      {'stepTitle': 'First'},
      {'stepTitle': 'Second', 'exam': examDoc()},
      {'stepTitle': 'Third'},
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

  /// The step counter in `_ProgressSection`.
  ///
  /// Paired with an assertion on the step *title* wherever the page itself
  /// matters, because the two can disagree and did: the counter is driven by
  /// `currentStep` and the title by what the `PageView` actually built, which
  /// is how the `PageController` defect hid — see *Next moves the page*.
  ///
  /// An earlier version of this comment said the title was unreliable because
  /// `PageView.builder` leaves an off-screen page in the element tree. **That
  /// was a misdiagnosis.** `PageView`'s `cacheExtent` is
  /// `allowImplicitScrolling ? 1.0 : 0.0`, and that flag defaults false, so
  /// once a scroll settles only the visible page is in the tree — which the
  /// `findsNothing` assertion in *Next moves the page* relies on. The first
  /// cut of the second-step test failed because the page never moved, not
  /// because a stale page lingered.
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

    testWidgets('the lock reads the displayed step, not the first one', (
      tester,
    ) async {
      // The assessment is on the **middle** step of three, so the two
      // readings disagree in both directions: reading `steps[0]` would let
      // the learner off the locked step and block them on the unlocked one.
      //
      // Every other test here puts the assessment on step 1 of two, where
      // `steps[currentStep]`, `steps[0]` and "the only step the lock is
      // consulted for" are the same thing — so the whole file was green
      // against `steps[0]`. Found by the implementation audit.
      await pumpCourse(tester, db: await seed(middleStepCourseDoc()));

      // Step 1 carries nothing, so Next goes through.
      expect(onStep(1, 3), findsOneWidget);
      await tapNext(tester);
      expect(onStep(2, 3), findsOneWidget);
      expect(find.text('Second'), findsOneWidget);
      expect(find.textContaining('please complete'), findsNothing);

      // Step 2 carries the unanswered test, so Next is refused here.
      await tapNext(tester);
      expect(find.text('please complete the test to proceed'), findsOneWidget);
      expect(onStep(2, 3), findsOneWidget);
      expect(find.text('Third'), findsNothing);
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

    testWidgets('a learner who has not joined never meets the lock', (
      tester,
    ) async {
      // **This test changed shape in Phase 145 and the change is the point.**
      // It used to tap Next and assert the page moved, pinning a membership
      // test that Phase 139 had put on the *lock* — a deviation, since
      // `changeNextButtonState` has no membership test of its own. Kotlin's
      // membership test is on the button
      // (`updateNavigationVisibility:256-270`), and Phase 145 moved the port's
      // there too. So the lock is no longer reachable for a non-member for
      // the reason Kotlin makes it unreachable: there is no Next.
      //
      // Asserting on the *absence of Next* rather than on a page move is what
      // keeps this able to fail. Re-adding a membership test to
      // `_nextStepLock` would leave every assertion here green, because the
      // lock is dead code on this path either way; dropping the gate in
      // `_NavigationBar` reds it immediately.
      await pumpCourse(
        tester,
        joined: false,
        db: await seed(courseDoc(courseId: mandatoryCourseId, exam: examDoc())),
      );

      // The tile is hidden for a non-member too (`CourseStepFragment
      // .onViewCreated`'s `!userHasCourse` arm), so nothing on this screen
      // offers the assessment the lock would be holding them to.
      expect(find.textContaining('take test'), findsNothing);
      expect(find.text('Next'), findsNothing);
      expect(onStep(1, 2), findsOneWidget);
    });

    testWidgets('a synced submission with no type releases the lock', (
      tester,
    ) async {
      // **The `type` predicate the port had and Kotlin does not.**
      // `SubmissionDao.countCompletedByUserAndExamId` (`SubmissionDao.kt:24`)
      // counts on `userId`, `parentId LIKE` and `status != 'pending'` and
      // nothing else; the port read `getExamSubmissionsByUser`, whose
      // `type = 'exam'` is the port's own addition, and filtered the rest in
      // Dart. So a submission Kotlin counts was invisible here.
      //
      // Reachable, and through a production writer rather than a fixture:
      // `upsertDocuments` stores `type` as
      // `JsonUtils.getStringOrNull('type', json)`, i.e. **null when Planet's
      // document omits the key** — the same null-`type` sync-in row
      // `submissions_repository_test.dart` already pins on the upload side.
      // The document below is shaped the way the server sends one: the owner
      // in the nested `user` object, no top-level `userId`, a `_rev` because
      // it came back from CouchDB.
      final db = await seed(
        courseDoc(courseId: mandatoryCourseId, exam: examDoc()),
      );
      await repositoryFor(db).upsertDocuments([
        {
          '_id': 'sub-typeless',
          '_rev': '1-abc',
          'parentId': SubmissionsRepository.examParentId(
            examId: 'exam-1',
            courseId: mandatoryCourseId,
          ),
          'user': {'_id': 'user-1'},
          'status': 'complete',
        },
      ]);
      expect(
        (await db.submissionDao.getById('sub-typeless'))?.type,
        isNull,
        reason: 'the fixture is only evidence while the sync-in stores no type',
      );

      await pumpCourse(tester, db: db);
      await tapNext(tester);

      // Kotlin counts it, so the step is answered and Next advances.
      expect(onStep(2, 2), findsOneWidget);
      expect(find.text('please complete the test to proceed'), findsNothing);
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
