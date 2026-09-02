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
      final grid = await repository.courseProgress('course-1', 'user-1');
      expect(grid[0].passed, isFalse);
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
    final grid = await repository.courseProgress('course-1', 'user-1');
    expect(grid[0].passed, isTrue);
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

    final grid = await repository.courseProgress('course-1', 'user-1');
    expect(grid[0].passed, isTrue);
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

  test('a step exam written by the courses walk carries its question count and '
      'mistakes into the progress grid', () async {
    // Phase 113. `courseProgress` joins `exams.stepId` to `course_steps.id`.
    // Nothing in the port used to write that join, so `questionCount` was 0
    // and `totalMistakes` 0 for every step regardless of what the learner
    // had done. The exam row here comes from the mapper the `courses` walk
    // now runs, not from a hand-faked `stepId`.
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
              {'id': 'q1', 'title': 'One?', 'type': 'input'},
              {'id': 'q2', 'title': 'Two?', 'type': 'input'},
            ],
          },
        },
      ],
    }, stepIdFor: CourseMapper.stepIdFor);
    expect(mapped.single.exam.stepId.value, 'course-1:1');
    await database.examDao.upsertAll(
      [mapped.single.exam],
      {'exam-1': mapped.single.questions},
    );

    // One attempt with two mistakes. `parentId` is `"$examId@$courseId"`,
    // the shape Phase 110 restored.
    await database.submissionDao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: 'sub-1',
          parentId: const Value('exam-1@course-1'),
          userId: const Value('user-1'),
          type: const Value('exam'),
          status: const Value('requires grading'),
        ),
      ],
      answers: {
        'sub-1': [
          SubmissionAnswersCompanion.insert(
            id: 'sub-1:q1',
            submissionId: 'sub-1',
            examId: const Value('exam-1'),
            questionId: const Value('q1'),
            mistakes: const Value(2),
          ),
        ],
      },
    );

    final grid = await repository.courseProgress('course-1', 'user-1');
    expect(grid, hasLength(3));
    expect(grid[1].questionCount, 2);
    expect(grid[1].totalMistakes, 2);
    // A step with no exam is untouched.
    expect(grid[0].questionCount, 0);
    expect(grid[0].totalMistakes, 0);
  });
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
