import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/exam_mapper.dart';
import 'package:myplanet/repository/progress_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Exercises the local-progress half of `ProgressRepositoryImpl`. The sync
/// pulls are covered by the repository's own batch-insert tests; these pin the
/// invariants the take-course screen depends on: a step the user opens marks
/// the run contiguous, `passed` is the gate for completion, and a re-open
/// never clobbers a pass.
void main() {
  late AppDatabase database;
  late ProgressRepository repository;

  setUp(() async {
    database = AppDatabase.memory();
    repository = ProgressRepository(
      _MockPlanetApi(),
      database.courseDao,
      database.courseProgressDao,
      database.examDao,
      database.submissionDao,
      database.certificationDao,
    );
    await _seedCourse(database, 'course-1', stepCount: 3);
  });
  tearDown(() => database.close());

  test(
    'a step the user opens advances the contiguous current-progress run',
    () async {
      final steps = await database.courseDao.getSteps('course-1');

      expect(
        await repository.getCurrentProgress(steps, 'user-1', 'course-1'),
        0,
      );

      // Opening step 1 (passed=null: an exam will grade it later) creates a row.
      await repository.saveCourseProgress(
        id: 'p-1',
        courseId: 'course-1',
        userId: 'user-1',
        stepNum: 1,
      );
      expect(
        await repository.getCurrentProgress(steps, 'user-1', 'course-1'),
        1,
      );

      // Step 2 is not yet opened, so the run stops at 1 even though step 1's row
      // has passed=false — current-progress measures reach, not pass.
      final row = await database.courseProgressDao.findByCourseUserAndStep(
        'course-1',
        'user-1',
        1,
      );
      expect(row?.passed, isFalse);
    },
  );

  test('a re-open does not clobber a pass the exam previously set', () async {
    await repository.saveCourseProgress(
      id: 'p-1',
      courseId: 'course-1',
      userId: 'user-1',
      stepNum: 1,
      passed: true,
    );
    // The step is re-opened with passed=null (the step-view path). The existing
    // passed=true must survive — otherwise a re-open would un-grade the user.
    await repository.saveCourseProgress(
      id: 'p-1-dup',
      courseId: 'course-1',
      userId: 'user-1',
      stepNum: 1,
    );
    final row = await database.courseProgressDao.findByCourseUserAndStep(
      'course-1',
      'user-1',
      1,
    );
    expect(row?.passed, isTrue);
  });

  test('a course is complete only when every step is passed', () async {
    // No passes yet.
    expect(await repository.completedCourseIds('user-1'), isEmpty);

    // Two of three steps passed: not complete.
    await repository.saveCourseProgress(
      id: 'p-1',
      courseId: 'course-1',
      userId: 'user-1',
      stepNum: 1,
      passed: true,
    );
    await repository.saveCourseProgress(
      id: 'p-2',
      courseId: 'course-1',
      userId: 'user-1',
      stepNum: 2,
      passed: true,
    );
    expect(await repository.completedCourseIds('user-1'), isEmpty);

    // All three passed: complete.
    await repository.saveCourseProgress(
      id: 'p-3',
      courseId: 'course-1',
      userId: 'user-1',
      stepNum: 3,
      passed: true,
    );
    expect(await repository.completedCourseIds('user-1'), {'course-1'});
  });

  test('duplicate rows for one step do not complete a course', () async {
    // Sync can deliver several rows for the same step — one per device or
    // attempt. The Kotlin counts unique passed stepNums (`toSet()`), so two
    // passes of step 1 plus one of step 2 must NOT complete a 3-step course,
    // even though the passed-row count reaches the step count.
    Future<void> seedRow(String id, int stepNum) => database
        .into(database.courseProgress)
        .insert(
          CourseProgressCompanion.insert(
            id: id,
            courseId: const Value('course-1'),
            userId: const Value('user-1'),
            stepNum: Value(stepNum),
            passed: const Value(true),
          ),
        );

    await seedRow('device-a-step1', 1);
    await seedRow('device-b-step1', 1);
    await seedRow('device-a-step2', 2);

    expect(await repository.completedCourseIds('user-1'), isEmpty);
  });

  test('a server progress row preserves a local pass on merge', () async {
    await repository.saveCourseProgress(
      id: 'p-1',
      courseId: 'course-1',
      userId: 'user-1',
      stepNum: 1,
      passed: true,
    );

    // A sync arrives with the same (courseId, userId, stepNum) but passed=false.
    // The local pass wins — the merge rule is `OR`.
    await repository.insertCourseProgressFromSync([
      {
        '_id': 'server-1',
        'userId': 'user-1',
        'courseId': 'course-1',
        'stepNum': 1,
        'passed': false,
        'createdDate': 1,
        'updatedDate': 1,
      },
    ]);

    final row = await database.courseProgressDao.findByCourseUserAndStep(
      'course-1',
      'user-1',
      1,
    );
    expect(row?.passed, isTrue);
  });

  test('isCourseCertified reports a certification row', () async {
    expect(await repository.isCourseCertified('course-1'), isFalse);

    await database.certificationDao.upsertAll([
      CertificationsCompanion.insert(
        id: 'cert-1',
        courseIds: const Value('["course-1"]'),
      ),
    ]);
    expect(await repository.isCourseCertified('course-1'), isTrue);
  });

  test(
    'courseProgressSummary reports max as step count and current as the contiguous run',
    () async {
      // course-1 has 3 steps (seeded). The user has opened steps 1 and 2 only.
      await repository.saveCourseProgress(
        id: 'p-1',
        courseId: 'course-1',
        userId: 'user-1',
        stepNum: 1,
      );
      await repository.saveCourseProgress(
        id: 'p-2',
        courseId: 'course-1',
        userId: 'user-1',
        stepNum: 2,
      );

      final summary = await repository.courseProgressSummary([
        'course-1',
      ], 'user-1');
      expect(summary['course-1']?.max, 3);
      expect(summary['course-1']?.current, 2);

      // A course with no steps still appears, with max=0/current=0 — matching the
      // Kotlin, whose map always contains an entry per requested id.
      await _seedCourse(database, 'course-empty', stepCount: 0);
      final withEmpty = await repository.courseProgressSummary([
        'course-1',
        'course-empty',
      ], 'user-1');
      expect(withEmpty['course-empty']?.max, 0);
      expect(withEmpty['course-empty']?.current, 0);
      // The presence of step 3 only (a gap at 1) yields current=0 — the run walks
      // from step 1 and stops at the first missing step.
      await _seedCourse(database, 'course-gap', stepCount: 3);
      await repository.saveCourseProgress(
        id: 'gap-3',
        courseId: 'course-gap',
        userId: 'user-1',
        stepNum: 3,
      );
      final gap = await repository.courseProgressSummary([
        'course-gap',
      ], 'user-1');
      expect(gap['course-gap']?.current, 0);
    },
  );

  test(
    'a step exam written by the courses walk reaches the grid as a percentage',
    () async {
      // Phase 113 pinned this join at the repository's question/mistake
      // counters, which no view ever rendered. What `ProgressGridAdapter`
      // actually binds is the *share of the exam answered* and whether that
      // share is all of it, so that is what the join has to deliver.
      await _seedStepExam(database, questionIds: ['q1', 'q2']);
      await _seedAttempt(
        database,
        submissionId: 'sub-1',
        answeredQuestionIds: ['q1'],
      );

      final data = await repository.courseProgress('course-1', 'user-1');
      expect(data.steps, hasLength(3));
      expect(data.steps[1].percentage, 50.0);
      expect(data.steps[1].percentageLabel, '50.0');
      expect(data.steps[1].completed, isFalse);
      // A step with no exam carries no percentage key at all, which is the
      // state the adapter paints in the main colour with no text — distinct
      // from "0%".
      expect(data.steps[0].percentage, isNull);
      expect(data.steps[0].completed, isNull);
      expect(data.steps[2].percentage, isNull);
    },
  );

  test(
    'every question answered completes the cell, and reads "100.0%"',
    () async {
      await _seedStepExam(database, questionIds: ['q1', 'q2']);
      await _seedAttempt(
        database,
        submissionId: 'sub-1',
        answeredQuestionIds: ['q1', 'q2'],
      );

      final data = await repository.courseProgress('course-1', 'user-1');
      expect(data.steps[1].completed, isTrue);
      // Gson stringifies the Double the Kotlin stores, so the shipping app shows
      // "100.0%" rather than "100%". Reproduced deliberately; see
      // `CourseStepProgress.percentageLabel`.
      expect(data.steps[1].percentageLabel, '100.0');
    },
  );

  test('an exam nobody has attempted leaves its cell blank', () async {
    await _seedStepExam(database, questionIds: ['q1', 'q2']);

    final data = await repository.courseProgress('course-1', 'user-1');
    // `getExamObject` writes nothing when an exam has no submission, so the
    // cell is indistinguishable from a step with no exam — not 0%.
    expect(data.steps[1].percentage, isNull);
    expect(data.steps[1].completed, isNull);
  });

  test('an exam with no questions reads "0%", not "0.0%"', () async {
    await _seedStepExam(database, questionIds: const []);
    await _seedAttempt(
      database,
      submissionId: 'sub-1',
      answeredQuestionIds: const [],
    );

    final data = await repository.courseProgress('course-1', 'user-1');
    // The zero-question branch is `addProperty("percentage", 0)` — an
    // **integer**, where the other branch stores a Double. The rendered
    // strings differ, so the port keeps the value a `num`.
    expect(data.steps[1].percentage, 0);
    expect(data.steps[1].percentage, isA<int>());
    expect(data.steps[1].percentageLabel, '0');
    expect(data.steps[1].completed, isFalse);
  });

  test('a zero-question exam cannot overwrite a real percentage, in either '
      'order', () async {
    // `getExamObject`'s zero-question branch is guarded by `!ob.has(...)` while
    // the with-questions branch is not, so the guarded one never wins against
    // a value already present, and the unguarded one always wins. Both exams
    // hang off the same step; `getByStepIds` returns them in insertion order.
    await _seedStepExam(database, questionIds: ['q1', 'q2']);
    await _seedAttempt(
      database,
      submissionId: 'sub-1',
      answeredQuestionIds: ['q1'],
    );
    await database.examDao.upsertAll([
      ExamsCompanion.insert(
        id: 'exam-empty',
        stepId: const Value('course-1:1'),
        courseId: const Value('course-1'),
      ),
    ], const {});
    await _seedAttempt(
      database,
      submissionId: 'sub-empty',
      examId: 'exam-empty',
      answeredQuestionIds: const [],
    );

    final data = await repository.courseProgress('course-1', 'user-1');
    expect(data.steps[1].percentage, 50.0);
    expect(data.steps[1].completed, isFalse);
  });

  test('the last submission of an exam wins the cell', () async {
    await _seedStepExam(database, questionIds: ['q1', 'q2']);
    await _seedAttempt(
      database,
      submissionId: 'sub-old',
      answeredQuestionIds: ['q1', 'q2'],
    );
    await _seedAttempt(
      database,
      submissionId: 'sub-new',
      answeredQuestionIds: ['q1'],
    );

    final data = await repository.courseProgress('course-1', 'user-1');
    // Two attempts, one JsonObject: the with-questions branch overwrites, so
    // the cell shows whichever submission `groupBy` yields last — here the
    // half-finished retake, which un-completes the step.
    expect(data.steps[1].completed, isFalse);
    expect(data.steps[1].percentage, 50.0);
  });

  test('a submission for another course\'s exam is out of scope', () async {
    await _seedStepExam(database, questionIds: ['q1', 'q2']);
    await database.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 'sub-elsewhere',
        parentId: const Value('other-exam@other-course'),
        userId: const Value('user-1'),
        type: const Value('exam'),
      ),
    ], answers: const {});

    final data = await repository.courseProgress('course-1', 'user-1');
    // `examIdsSet.contains(getParentBaseId(...))` drops it before it can
    // reach a cell.
    expect(data.steps[1].percentage, isNull);
  });

  test('a parentId with no "@" is the bare exam id', () async {
    // `getParentBaseId` returns `parentId` untouched when it has no `@` —
    // which is the shape `examParentId` writes for an exam with no course.
    await _seedStepExam(database, questionIds: ['q1']);
    await _seedAttempt(
      database,
      submissionId: 'sub-1',
      parentId: 'exam-1',
      answeredQuestionIds: ['q1'],
    );

    final data = await repository.courseProgress('course-1', 'user-1');
    expect(data.steps[1].completed, isTrue);
  });

  test('the header reports the title, the run and a truncated ring', () async {
    await repository.saveCourseProgress(
      id: 'p-1',
      courseId: 'course-1',
      userId: 'user-1',
      stepNum: 1,
    );

    final data = await repository.courseProgress('course-1', 'user-1');
    expect(data.title, 'Course course-1');
    expect(data.current, 1);
    expect(data.max, 3);
    // `(current / max * 100).toInt()` truncates: 1 of 3 is 33, not 34.
    expect(data.ringPercent, 33);

    // `max == 0` takes the Activity's explicit zero branch rather than
    // dividing by zero.
    await _seedCourse(database, 'course-empty', stepCount: 0);
    final empty = await repository.courseProgress('course-empty', 'user-1');
    expect(empty.max, 0);
    expect(empty.ringPercent, 0);
    expect(empty.steps, isEmpty);
  });
}

/// Puts an exam on step 2 of `course-1` (`course-1:1`) through the mapper the
/// `courses` walk runs, so the `exams.stepId` → `course_steps.id` join under
/// test is the real one rather than a hand-faked `stepId`.
Future<void> _seedStepExam(
  AppDatabase database, {
  required List<String> questionIds,
}) async {
  final mapped = ExamMapper.fromCourseDoc({
    '_id': 'course-1',
    'steps': [
      {'stepTitle': 'Intro'},
      {
        'stepTitle': 'Assessment',
        'exam': {
          '_id': 'exam-1',
          'type': 'courses',
          'questions': [
            for (final id in questionIds)
              {'id': id, 'title': '$id?', 'type': 'input'},
          ],
        },
      },
      {'stepTitle': 'Outro'},
    ],
  }, stepIdFor: CourseMapper.stepIdFor);
  expect(mapped.single.exam.stepId.value, 'course-1:1');
  await database.examDao.upsertAll(
    [mapped.single.exam],
    {'exam-1': mapped.single.questions},
  );
}

/// One exam attempt with an answer row per id in [answeredQuestionIds].
Future<void> _seedAttempt(
  AppDatabase database, {
  required String submissionId,
  required List<String> answeredQuestionIds,
  String examId = 'exam-1',
  String? parentId,
  int mistakes = 0,
}) async {
  await database.submissionDao.upsertAll(
    [
      SubmissionsCompanion.insert(
        id: submissionId,
        parentId: Value(parentId ?? '$examId@course-1'),
        userId: const Value('user-1'),
        type: const Value('exam'),
        status: const Value('requires grading'),
      ),
    ],
    answers: {
      submissionId: [
        for (final questionId in answeredQuestionIds)
          SubmissionAnswersCompanion.insert(
            id: '$submissionId:$questionId',
            submissionId: submissionId,
            examId: Value(examId),
            questionId: Value(questionId),
            mistakes: Value(mistakes),
          ),
      ],
    },
  );
}

/// Seeds a course with [stepCount] steps, mirroring the `course_steps` shape
/// `CourseMapper` produces (0-based `stepIndex`, local id `<course>:<pos>`).
Future<void> _seedCourse(
  AppDatabase database,
  String courseId, {
  required int stepCount,
}) async {
  await database.courseDao.upsertAll(
    [
      CoursesCompanion.insert(
        id: courseId,
        courseId: Value(courseId),
        courseTitle: Value('Course $courseId'),
        // Put the course on the user's shelf so `completedCourseIds` — which
        // only walks the shelf — can see it.
        userId: const Value(['user-1']),
      ),
    ],
    [
      for (var i = 0; i < stepCount; i++)
        CourseStepsCompanion.insert(
          id: '$courseId:$i',
          courseId: Value(courseId),
          stepIndex: Value(i),
        ),
    ],
  );
}
