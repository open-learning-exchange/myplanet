import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/session_provider.dart';

/// Returns a fixed user, resolving on a microtask like the real notifier.
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

/// Covers the two providers behind the My Progress list and the grid it opens.
void main() {
  late AppDatabase database;
  late ProviderContainer container;

  setUp(() async {
    database = AppDatabase.memory();
    container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(database),
        sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
      ],
    );
  });
  tearDown(() async {
    container.dispose();
    await database.close();
  });

  test('the list is the user\'s shelf, never the whole catalogue', () async {
    await database.courseDao.upsertAll([
      CoursesCompanion.insert(
        id: 'mine',
        courseTitle: const Value('Mine'),
        userId: Value(const ['user-1']),
      ),
      CoursesCompanion.insert(id: 'theirs', courseTitle: const Value('Theirs')),
    ], const []);

    // Read without resolving `sessionProvider` first. This used to read the
    // session as `.valueOrNull`, so the first pass ran with a null user — and
    // `CourseDao.watchCourses` drops the `shelfUserId` predicate when it is
    // null, which is not "no courses" but "every course". Before the fix this
    // call never completed at all (the pending first future is discarded when
    // the session lands and the provider rebuilds), which is how the wrong
    // first pass shows up in a test.
    final rows = await container
        .read(courseProgressStreamProvider.future)
        .timeout(const Duration(seconds: 10));

    expect(rows.map((row) => row.courseId), ['mine']);
  });

  test(
    'per-step mistakes are keyed by the exam\'s ordinal in the course',
    () async {
      await database.courseDao.upsertAll(
        [
          CoursesCompanion.insert(
            id: 'course-1',
            courseTitle: const Value('Mine'),
            userId: Value(const ['user-1']),
          ),
        ],
        [
          CourseStepsCompanion.insert(
            id: 'course-1:0',
            courseId: const Value('course-1'),
            stepIndex: const Value(0),
          ),
        ],
      );
      // Two exams on the course. `submissionMap` numbers them by position in
      // `examDao.getByCourseIds`, and the row renders `position + 1`. Their ids
      // carry no digits, which is what made the old regex-the-id approach
      // report 0 for every row.
      await database.examDao.upsertAll([
        ExamsCompanion.insert(id: 'alpha', courseId: const Value('course-1')),
        ExamsCompanion.insert(id: 'beta', courseId: const Value('course-1')),
      ], const {});
      await database.submissionDao.upsertAll(
        [
          SubmissionsCompanion.insert(
            id: 'sub-1',
            parentId: const Value('beta@course-1'),
            userId: const Value('user-1'),
            type: const Value('exam'),
          ),
        ],
        answers: {
          'sub-1': [
            SubmissionAnswersCompanion.insert(
              id: 'sub-1:q1',
              submissionId: 'sub-1',
              examId: const Value('beta'),
              mistakes: const Value(2),
            ),
            SubmissionAnswersCompanion.insert(
              id: 'sub-1:q2',
              submissionId: 'sub-1',
              examId: const Value('beta'),
              mistakes: const Value(3),
            ),
          ],
        },
      );

      final row = (await container.read(
        courseProgressStreamProvider.future,
      )).single;
      // Exam `beta` is second, so its key is 1 and the row shows "2".
      expect(row.stepMistakes, {1: 5});
      expect(row.mistakes, 5);
    },
  );

  test(
    'a submission whose parent names another course is not counted',
    () async {
      // `fetchCourseData` matches an `@`-delimited **segment**
      // (`parts.lastOrNull { courseIdsSet.contains(it) }`). A `contains`
      // substring test, which this used to do, attributes `exam@course-10` to
      // `course-1` as well.
      await database.courseDao.upsertAll([
        CoursesCompanion.insert(
          id: 'course-1',
          courseTitle: const Value('One'),
          userId: Value(const ['user-1']),
        ),
      ], const []);
      await database.examDao.upsertAll([
        ExamsCompanion.insert(id: 'exam-1', courseId: const Value('course-1')),
      ], const {});
      await database.submissionDao.upsertAll(
        [
          SubmissionsCompanion.insert(
            id: 'sub-1',
            parentId: const Value('exam-1@course-10'),
            userId: const Value('user-1'),
            type: const Value('exam'),
          ),
        ],
        answers: {
          'sub-1': [
            SubmissionAnswersCompanion.insert(
              id: 'sub-1:q1',
              submissionId: 'sub-1',
              examId: const Value('exam-1'),
              mistakes: const Value(4),
            ),
          ],
        },
      );

      final row = (await container.read(
        courseProgressStreamProvider.future,
      )).single;
      expect(row.mistakes, 0);
      expect(row.stepMistakes, isNull);
    },
  );

  test('the grid provider resolves the signed-in user itself', () async {
    await database.courseDao.upsertAll(
      [
        CoursesCompanion.insert(
          id: 'course-1',
          courseTitle: const Value('Mine'),
          userId: Value(const ['user-1']),
        ),
      ],
      [
        CourseStepsCompanion.insert(
          id: 'course-1:0',
          courseId: const Value('course-1'),
          stepIndex: const Value(0),
        ),
      ],
    );
    await database.courseProgressDao.upsert(
      CourseProgressCompanion.insert(
        id: 'p-1',
        stepNum: const Value(1),
        userId: const Value('user-1'),
        courseId: const Value('course-1'),
      ),
    );

    // Read cold: the screen never watches `sessionProvider`, so the provider
    // has to await it rather than read a null and report an empty grid.
    final data = await container
        .read(courseProgressGridProvider('course-1').future)
        .timeout(const Duration(seconds: 10));

    expect(data.title, 'Mine');
    expect(data.current, 1);
    expect(data.max, 1);
    expect(data.ringPercent, 100);
  });
}
