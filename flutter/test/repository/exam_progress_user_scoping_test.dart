import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/progress_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Port of `ed5609f` ("courses: smoother repository base exam progress user
/// scoping", fixes #16695): `CourseProgressDao.updatePassedByCourseAndStep`
/// gains `AND userId IS :userId`, and `CoursesRepositoryImpl
/// .updateCourseProgress` threads the exam-taker's id into it.
///
/// The second group settles a claim rather than a defect — see the group's own
/// comment.
void main() {
  late AppDatabase db;
  late ProgressRepository repository;

  setUp(() async {
    db = AppDatabase.memory();
    repository = ProgressRepository(
      _MockPlanetApi(),
      db.courseDao,
      db.courseProgressDao,
      db.examDao,
      db.submissionDao,
      db.certificationDao,
    );
  });

  tearDown(() => db.close());

  Future<void> seedCourse(
    String courseId, {
    required int stepCount,
    List<String> shelfUserIds = const ['learner-a'],
  }) => db.courseDao.upsertAll(
    [
      CoursesCompanion.insert(
        id: courseId,
        courseId: Value(courseId),
        courseTitle: Value('Course $courseId'),
        userId: Value(shelfUserIds),
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

  Future<bool?> passedFor(String? userId, {int stepNum = 2}) async {
    final row = await db.courseProgressDao.findByCourseUserAndStep(
      'course-1',
      userId,
      stepNum,
    );
    return row?.passed;
  }

  group('updateCourseProgress is scoped to the exam-taker', () {
    setUp(() async {
      await seedCourse('course-1', stepCount: 3);
      // Learner A's step 2 came back graded from Planet, so their row is
      // passed. Learner B has reached the same step and has not passed it.
      await repository.saveCourseProgress(
        id: 'progress-a',
        courseId: 'course-1',
        userId: 'learner-a',
        stepNum: 2,
        passed: true,
      );
      await repository.saveCourseProgress(
        id: 'progress-b',
        courseId: 'course-1',
        userId: 'learner-b',
        stepNum: 2,
        passed: false,
      );
    });

    test('one learner taking the exam does not clear another learner\'s '
        'pass', () async {
      // B finishes the exam. `isGraded` is false — see the reachability group
      // below — so the write is `passed = false`, and unscoped it wiped A's
      // `true`: on a shared handset, B taking an exam un-completed A's course.
      await repository.updateCourseProgress(
        courseId: 'course-1',
        stepNum: 2,
        passed: false,
        userId: 'learner-b',
      );

      expect(await passedFor('learner-a'), isTrue);
      expect(await passedFor('learner-b'), isFalse);
    });

    test('the taker\'s own row is still written', () async {
      await repository.updateCourseProgress(
        courseId: 'course-1',
        stepNum: 2,
        passed: true,
        userId: 'learner-b',
      );

      expect(await passedFor('learner-b'), isTrue);
      expect(await passedFor('learner-a'), isTrue);
    });

    test('other steps of the same course are untouched', () async {
      await repository.saveCourseProgress(
        id: 'progress-b-1',
        courseId: 'course-1',
        userId: 'learner-b',
        stepNum: 1,
        passed: true,
      );

      await repository.updateCourseProgress(
        courseId: 'course-1',
        stepNum: 2,
        passed: false,
        userId: 'learner-b',
      );

      expect(await passedFor('learner-b', stepNum: 1), isTrue);
    });

    test('retaking your own exam still clears your own pass', () async {
      // `ed5609f` deliberately does not close the self case, so this quirk is
      // ported rather than fixed: A's step-2 pass came from Planet, A retakes
      // the exam, and the write is `false` again — the star goes dark until
      // Planet grades the new attempt.
      await repository.updateCourseProgress(
        courseId: 'course-1',
        stepNum: 2,
        passed: false,
        userId: 'learner-a',
      );

      expect(await passedFor('learner-a'), isFalse);
    });

    test('an empty course id writes nothing', () async {
      // `if (courseId.isNullOrEmpty()) return`
      // (`CoursesRepositoryImpl.kt:522`).
      await repository.updateCourseProgress(
        courseId: '',
        stepNum: 2,
        passed: true,
        userId: 'learner-b',
      );

      expect(await passedFor('learner-b'), isFalse);
    });

    test('no row for the taker means no row is created', () async {
      // Kotlin runs the `UPDATE` and nothing else. The port used to insert
      // here, under the user-less key `'${courseId}_$stepNum'` — so a second
      // learner would have overwritten the first learner's row outright, and
      // `getCurrentProgress`, which counts rows ignoring `passed`, would have
      // reported a step as reached that was never opened.
      await repository.updateCourseProgress(
        courseId: 'course-1',
        stepNum: 3,
        passed: true,
        userId: 'learner-c',
      );

      expect(await db.courseProgressDao.getByUser('learner-c'), isEmpty);
      final steps = await db.courseDao.getSteps('course-1');
      expect(
        await repository.getCurrentProgress(steps, 'learner-c', 'course-1'),
        0,
      );
    });

    test('a null user id matches the null-user rows, not every row', () async {
      // `userId IS :userId`, not `=`: SQLite's null-safe equality, so a null
      // argument matches rows whose `userId` is NULL and only those. Written
      // `=` it would match nothing at all.
      await db.courseProgressDao.upsert(
        CourseProgressCompanion.insert(
          id: 'progress-null',
          courseId: const Value('course-1'),
          userId: const Value(null),
          stepNum: const Value(2),
          passed: const Value(false),
        ),
      );

      await repository.updateCourseProgress(
        courseId: 'course-1',
        stepNum: 2,
        passed: true,
        userId: null,
      );

      expect(await passedFor(null), isTrue);
      expect(await passedFor('learner-a'), isTrue);
      expect(await passedFor('learner-b'), isFalse);
    });
  });

  /// Phase 133 reported that "in the port a course containing any exam step
  /// can never complete, and the completed-course stars can never light for
  /// it", calling it possibly the most expensive reachability row found so
  /// far. These two tests establish what is actually true, because the fix
  /// depends on it: **neither app grades an exam step locally**, and the port
  /// has the same server route to a passed step that Kotlin has.
  group('how an exam step actually becomes passed', () {
    setUp(() => seedCourse('course-1', stepCount: 2));

    test(
      'finishing the exam does not complete the course, in either app',
      () async {
        // The step-view write, `passed: exams.isEmpty ? true : null`
        // (`CourseStepFragment.kt:96`): step 1 has no exam, step 2 has one.
        await repository.saveCourseProgress(
          id: 'p1',
          courseId: 'course-1',
          userId: 'learner-a',
          stepNum: 1,
          passed: true,
        );
        await repository.saveCourseProgress(
          id: 'p2',
          courseId: 'course-1',
          userId: 'learner-a',
          stepNum: 2,
          passed: null,
        );

        // Kotlin's exam finish is `saveCourseProgress(courseId, stepNumber,
        // sub?.status == "graded", user?.id)` — and `"graded"` is a status no
        // local writer produces (`saveExamAnswer` writes `pending`, `complete`
        // or `requires grading`), on a submission the exam branch always
        // recreates. So the argument is false, and the step stays unpassed.
        await repository.updateCourseProgress(
          courseId: 'course-1',
          stepNum: 2,
          passed: false,
          userId: 'learner-a',
        );

        expect(await repository.completedCourseIds('learner-a'), isEmpty);
      },
    );

    test(
      'the graded `courses_progress` document from Planet completes it',
      () async {
        await repository.saveCourseProgress(
          id: 'p1',
          courseId: 'course-1',
          userId: 'learner-a',
          stepNum: 1,
          passed: true,
        );
        await repository.saveCourseProgress(
          id: 'p2',
          courseId: 'course-1',
          userId: 'learner-a',
          stepNum: 2,
          passed: null,
        );

        // What `syncCourseProgress` hands `insertCourseProgressFromSync` — the
        // walk `CourseSyncNotifier` runs on every course sync. `fromDoc` merges
        // onto the local `(courseId, userId, stepNum)` row rather than creating
        // a twin, so the server's `passed: true` lands on the step the learner
        // already reached.
        await repository.insertCourseProgressFromSync([
          {
            '_id': 'server-p2',
            '_rev': '1-abc',
            'courseId': 'course-1',
            'userId': 'learner-a',
            'stepNum': 2,
            'passed': true,
          },
        ]);

        expect(await repository.completedCourseIds('learner-a'), {'course-1'});
      },
    );
  });
}
