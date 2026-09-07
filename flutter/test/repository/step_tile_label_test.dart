import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/exam_mapper.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/repository/submissions_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// Phase 128 — `CourseStepFragment.hideTestIfNoQuestion` (`:241-262`) and the
/// read it hangs off, `SubmissionsRepositoryImpl.hasSubmission` (`:176-192`).
///
/// This is the only **user-visible** reader in either tree whose predicate is
/// the whole composite `parentId`, and unlike the mandatory-survey gate it
/// works in the shipping Android app: `getCourseStepData` reads the
/// *plural*-typed rows. Phase 125 made every port writer agree on that key, so
/// the survey half of the swap is only portable now.
///
/// Every submission below is authored by the production writer
/// ([SubmissionsRepository.startExamSession] /
/// [SubmissionsRepository.createSurveyDraft]) and every exam and survey row
/// comes out of a mapper reading a document shaped like the server's. Nothing
/// hand-writes a `parentId` — that fixture shape is exactly what let the key
/// disagree for four phases.
void main() {
  late AppDatabase database;
  late SubmissionsRepository submissions;

  /// One step carrying one exam and one survey, both with a question, as the
  /// `courses` walk writes them.
  const courseDoc = {
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

  Future<void> seed(Map<String, Object?> doc) async {
    final parsed = CourseMapper.fromDoc(doc)!;
    await database.courseDao.upsertAll([parsed.course], parsed.steps);
    for (final mapping in ExamMapper.fromCourseDoc(
      doc,
      stepIdFor: CourseMapper.stepIdFor,
    )) {
      await database.examDao.upsertAll(
        [mapping.exam],
        {mapping.exam.id.value: mapping.questions},
      );
    }
    for (final mapping in SurveyMapper.fromCourseDoc(
      doc,
      stepIdFor: CourseMapper.stepIdFor,
    )) {
      await database.surveyDao.upsertAll(
        [mapping.survey],
        {mapping.survey.id.value: mapping.questions},
      );
    }
  }

  /// Opens an attempt at the step's exam the way `TakeExamScreen` does.
  Future<void> takeExam({String examId = 'exam-1'}) async {
    final exam = (await database.examDao.getById(examId))!;
    await submissions.startExamSession(
      exam: exam,
      questions: await database.examDao.questionsFor(examId),
      userId: 'user-1',
      courseId: 'course-1',
    );
  }

  /// Answers the step's survey the way `TakeSurveyScreen` does.
  Future<void> answerSurvey({String surveyId = 'survey-1'}) async {
    final survey = (await database.surveyDao.getByCourseId(
      'course-1',
    )).firstWhere((row) => row.id == surveyId);
    await submissions.createSurveyDraft(
      survey: survey,
      questions: await database.surveyDao.questionsFor(surveyId),
      userId: 'user-1',
    );
  }

  setUp(() async {
    database = AppDatabase.memory();
    submissions = SubmissionsRepository(
      MockPlanetApi(),
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
    );
    await seed(courseDoc);
  });

  tearDown(() => database.close());

  test(
    'the mapped exam and survey really are attached to the course',
    () async {
      // If this fails nothing below proves anything: `hasSubmission` builds
      // `"$examId@$courseId"`, so a mapper that dropped `courseId` would make
      // every assertion vacuous — and it is a mapper defect the port has had
      // before (Phase 113's unreachable exam screen).
      final stepId = CourseMapper.stepIdFor('course-1', 0);
      final exams = await database.examDao.getByStepIds([stepId]);
      expect(exams.single.courseId, 'course-1');
      final surveys = await database.surveyDao.getByStepId(stepId);
      expect(surveys.single.courseId, 'course-1');
    },
  );

  group('hasSubmission', () {
    test('is false before the learner has taken the exam', () async {
      expect(
        await submissions.hasSubmission(
          stepExamId: 'exam-1',
          courseId: 'course-1',
          userId: 'user-1',
          type: 'exam',
        ),
        isFalse,
      );
    });

    test('is true once the production writer has opened an attempt', () async {
      // The tile's whole purpose. Pre-Phase-125 this could not have worked for
      // a survey at all; for an exam the port simply never asked.
      await takeExam();
      expect(
        await submissions.hasSubmission(
          stepExamId: 'exam-1',
          courseId: 'course-1',
          userId: 'user-1',
          type: 'exam',
        ),
        isTrue,
      );
    });

    test('is true once the survey sheet exists, on the composite key', () async {
      await answerSurvey();
      // The row the writer authored carries the composite key, which is what
      // makes this reader's predicate matchable. Asserted directly so a writer
      // regression fails here rather than only at the label.
      final row = (await database.submissionDao.getSurveySubmissionsByUser(
        'user-1',
      )).single;
      expect(row.parentId, 'survey-1@course-1');
      expect(
        await submissions.hasSubmission(
          stepExamId: 'survey-1',
          courseId: 'course-1',
          userId: 'user-1',
          type: 'survey',
        ),
        isTrue,
      );
    });

    test('does not confuse the two types', () async {
      // Kotlin scopes the count by `type` as well as `parentId`
      // (`SubmissionDao.kt:23`). The exam and the survey on this step have
      // different ids, but a step whose exam and survey share an id is
      // reachable — `_liveParentDocument`'s comment says the two id spaces are
      // not disjoint — so the type clause is load-bearing, not decoration.
      await takeExam();
      expect(
        await submissions.hasSubmission(
          stepExamId: 'exam-1',
          courseId: 'course-1',
          userId: 'user-1',
          type: 'survey',
        ),
        isFalse,
      );
    });

    test('is false for another user', () async {
      await takeExam();
      expect(
        await submissions.hasSubmission(
          stepExamId: 'exam-1',
          courseId: 'course-1',
          userId: 'user-2',
          type: 'exam',
        ),
        isFalse,
      );
    });

    test('is false for a blank exam id, course id or user id', () async {
      await takeExam();
      // Kotlin's `isNullOrBlank` triple (`:182-184`). Here `false` means the
      // tile reads *take* rather than *retake*, so a guest browsing a joined
      // course, or a step whose `courseId` never synced, is offered a first
      // attempt.
      for (final blank in [null, '']) {
        expect(
          await submissions.hasSubmission(
            stepExamId: blank,
            courseId: 'course-1',
            userId: 'user-1',
            type: 'exam',
          ),
          isFalse,
        );
        expect(
          await submissions.hasSubmission(
            stepExamId: 'exam-1',
            courseId: blank,
            userId: 'user-1',
            type: 'exam',
          ),
          isFalse,
        );
        expect(
          await submissions.hasSubmission(
            stepExamId: 'exam-1',
            courseId: 'course-1',
            userId: blank,
            type: 'exam',
          ),
          isFalse,
        );
      }
    });

    test(
      'is false while the exam has no questions, submission or not',
      () async {
        // `questionDao.countByExamId(stepExamId) == 0 → false` (`:186-188`).
        // Ported here and deliberately *not* ported in `hasUnfinishedSurveys`,
        // where the same rule inverts into "a question-less survey blocks the
        // course forever". Reachable: the `exams` and `courses` walks run in the
        // same batch, so a step's exam can be on disk before its questions are.
        await takeExam();
        await database.delete(database.examQuestions).go();
        expect(
          await submissions.hasSubmission(
            stepExamId: 'exam-1',
            courseId: 'course-1',
            userId: 'user-1',
            type: 'exam',
          ),
          isFalse,
        );
      },
    );

    test('counts a survey question from the surveys table, not exams', () async {
      // Kotlin keeps every question in one `exam_questions` table
      // (`QuestionDao.kt:13`) because it keeps every exam and survey in one
      // `exams` table. The port split both pairs, so the count has to pick its
      // table from `type`. Reading `ExamQuestions` for a survey would have
      // found nothing and answered *not taken* on every survey ever answered.
      await answerSurvey();
      expect(await database.examDao.questionsFor('survey-1'), isEmpty);
      expect(await database.surveyDao.questionsFor('survey-1'), isNotEmpty);
      expect(
        await submissions.hasSubmission(
          stepExamId: 'survey-1',
          courseId: 'course-1',
          userId: 'user-1',
          type: 'survey',
        ),
        isTrue,
      );
    });
  });

  group('stepAssessmentProvider', () {
    late ProviderContainer container;

    setUp(() {
      container = ProviderContainer(
        overrides: [appDatabaseProvider.overrideWithValue(database)],
      );
      addTearDown(container.dispose);
    });

    Future<StepAssessment> read({String? userId = 'user-1'}) => container.read(
      stepAssessmentProvider((
        stepId: CourseMapper.stepIdFor('course-1', 0),
        courseId: 'course-1',
        userId: userId,
      )).future,
    );

    test('reports both lists and both booleans', () async {
      final before = await read();
      expect(before.exams.map((row) => row.id), ['exam-1']);
      expect(before.surveys.map((row) => row.id), ['survey-1']);
      expect(before.hasExam, isFalse);
      expect(before.hasSurvey, isFalse);
    });

    test('flips each boolean independently', () async {
      await takeExam();
      final afterExam = await read();
      expect(afterExam.hasExam, isTrue);
      expect(afterExam.hasSurvey, isFalse);
    });

    test('a guest sees the lists but never a retake', () async {
      // `getCourseStepData(stepId, user?.id)` passes a nullable id straight
      // through to `hasSubmission`, whose blank-user guard answers false.
      await takeExam();
      await answerSurvey();
      final assessment = await read(userId: null);
      expect(assessment.exams, isNotEmpty);
      expect(assessment.hasExam, isFalse);
      expect(assessment.hasSurvey, isFalse);
    });

    test(
      'only the first exam decides the label, though the count is the whole list',
      () async {
        // Kotlin's quirk, ported rather than corrected:
        // `hasSubmission(stepExams[0].id, …)` against
        // `getString(R.string.take_test, exams.size)`.
        //
        // **The state is port-only, and the test says so rather than
        // pretending otherwise.** Kotlin cannot reach a two-exam step: a step
        // document carries one `exam` object, read with `getAsJsonObject`, and
        // `bulkInsertExamsFromSync` calls `insertCourseStepsExams("", "", doc,
        // "")` so the standalone walk leaves `stepId` null. `ExamMapper.fromDoc`
        // *does* write a document's own `stepId`, which is the route below. So
        // this pins fidelity to what the Kotlin says, on a shape only the port
        // can produce — worth having, because the alternative reading ("any
        // exam counts") is the one a reimplementer would choose.
        final stepId = CourseMapper.stepIdFor('course-1', 0);
        final second = ExamMapper.fromDoc({
          '_id': 'exam-2',
          'type': 'courses',
          'name': 'Second test',
          'stepId': stepId,
          'courseId': 'course-1',
          'questions': [
            {'id': 'q2', 'title': 'Two?', 'type': 'input'},
          ],
        })!;
        await database.examDao.upsertAll(
          [second.exam],
          {second.exam.id.value: second.questions},
        );

        // Answering only the *second* exam leaves the label saying "take":
        // the boolean reads `stepExams[0]`.
        await takeExam(examId: 'exam-2');
        final assessment = await read();
        expect(assessment.exams.length, 2);
        expect(assessment.hasExam, isFalse);
      },
    );
  });
}
