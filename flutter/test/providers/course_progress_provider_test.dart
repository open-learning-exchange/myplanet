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
      await database.examDao.upsertAll(
        [
          ExamsCompanion.insert(id: 'alpha', courseId: const Value('course-1')),
          ExamsCompanion.insert(id: 'beta', courseId: const Value('course-1')),
        ],
        // Each answer below names one of these questions. Kotlin reaches an
        // answer's exam *through* its question row, so an answer with no
        // resolvable question is one it counts nothing for and draws no table
        // from — a fixture that cannot demonstrate the keying it claims.
        {
          'beta': [
            ExamQuestionsCompanion.insert(
              id: 'q1',
              examId: 'beta',
              position: 0,
            ),
            ExamQuestionsCompanion.insert(
              id: 'q2',
              examId: 'beta',
              position: 1,
            ),
          ],
        },
      );
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
              questionId: const Value('q1'),
              mistakes: const Value(2),
            ),
            SubmissionAnswersCompanion.insert(
              id: 'sub-1:q2',
              submissionId: 'sub-1',
              examId: const Value('beta'),
              questionId: const Value('q2'),
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
    'two attempts total together but only the last one breaks down',
    () async {
      // The quirk pair in `ProgressRepositoryImpl.submissionMap:153-171`:
      // `totalMistakes` is declared **outside** the per-submission loop while
      // `mistakesMap` is rebuilt inside it and written to the JsonObject there.
      // So `mistakes` accumulates over every attempt while `stepMistake`
      // survives as only the last attempt's breakdown. Both were already
      // reproduced here and neither was pinned — hoisting the map, or moving the
      // accumulator inside the loop, passed the entire suite.
      await database.courseDao.upsertAll([
        CoursesCompanion.insert(
          id: 'course-1',
          courseTitle: const Value('Mine'),
          userId: Value(const ['user-1']),
        ),
      ], const []);
      await database.examDao.upsertAll(
        [
          ExamsCompanion.insert(id: 'alpha', courseId: const Value('course-1')),
          ExamsCompanion.insert(id: 'beta', courseId: const Value('course-1')),
        ],
        {
          'alpha': [
            ExamQuestionsCompanion.insert(
              id: 'qa',
              examId: 'alpha',
              position: 0,
            ),
          ],
          'beta': [
            ExamQuestionsCompanion.insert(
              id: 'qb',
              examId: 'beta',
              position: 0,
            ),
          ],
        },
      );

      Future<void> attempt(String id, String examId, String q, int mistakes) =>
          database.submissionDao.upsertAll(
            [
              SubmissionsCompanion.insert(
                id: id,
                parentId: Value('$examId@course-1'),
                userId: const Value('user-1'),
                type: const Value('exam'),
              ),
            ],
            answers: {
              id: [
                SubmissionAnswersCompanion.insert(
                  id: '$id:$q',
                  submissionId: id,
                  examId: Value(examId),
                  questionId: Value(q),
                  mistakes: Value(mistakes),
                ),
              ],
            },
          );

      // Two mistakes on exam `alpha` (ordinal 0), then three on `beta` (1).
      await attempt('sub-1', 'alpha', 'qa', 2);
      await attempt('sub-2', 'beta', 'qb', 3);

      final row = (await container.read(
        courseProgressStreamProvider.future,
      )).single;
      // The oval totals both attempts...
      expect(row.mistakes, 5);
      // ...while the table breaks down only the last submission's exam.
      expect(row.stepMistakes, {1: 3});
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

  test(
    're-entering My Progress re-reads rather than serving a frozen list',
    () async {
      // `CoursesProgressFragment.onViewCreated` calls `loadCourseData()`
      // unconditionally (`:31`) — unlike `CourseProgressViewModel.loadProgress`
      // it has no `if (value != null) return` guard — and the fragment is pushed
      // with `addToBackStack`, so leaving to take an exam destroys it and
      // returning re-reads.
      //
      // A plain `StreamProvider` yields once and completes, and nothing in
      // `lib/` invalidates this one, so the first read used to be frozen for the
      // process lifetime: make two more mistakes and the row still showed the
      // old count — while the grid it opens, which *is* autoDispose, showed the
      // new one. Two halves of one screen disagreeing.
      await database.courseDao.upsertAll([
        CoursesCompanion.insert(
          id: 'course-1',
          courseTitle: const Value('Mine'),
          userId: Value(const ['user-1']),
        ),
      ], const []);
      await database.examDao.upsertAll([
        ExamsCompanion.insert(id: 'exam-1', courseId: const Value('course-1')),
      ], const {});

      Future<void> seedAttempt(String id, int mistakes) =>
          database.submissionDao.upsertAll(
            [
              SubmissionsCompanion.insert(
                id: id,
                parentId: const Value('exam-1@course-1'),
                userId: const Value('user-1'),
                type: const Value('exam'),
              ),
            ],
            answers: {
              id: [
                SubmissionAnswersCompanion.insert(
                  id: '$id:q1',
                  submissionId: id,
                  examId: const Value('exam-1'),
                  questionId: const Value('q1'),
                  mistakes: Value(mistakes),
                ),
              ],
            },
          );

      await seedAttempt('sub-1', 1);

      // A mounted screen holds a listener; navigating away drops it.
      final mounted = container.listen(courseProgressStreamProvider, (_, _) {});
      expect(
        (await container.read(
          courseProgressStreamProvider.future,
        )).single.mistakes,
        1,
      );
      mounted.close();
      // Let the autoDispose sweep run for the now-listenerless provider.
      await Future<void>.delayed(Duration.zero);

      // The learner retakes the exam and gets two more wrong, then comes back.
      await seedAttempt('sub-2', 2);
      final reopened = container.listen(
        courseProgressStreamProvider,
        (_, _) {},
      );
      addTearDown(reopened.close);
      expect(
        (await container.read(
          courseProgressStreamProvider.future,
        )).single.mistakes,
        3,
      );
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
