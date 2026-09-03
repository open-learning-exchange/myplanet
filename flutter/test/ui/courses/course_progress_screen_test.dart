import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/course_mapper.dart';
import 'package:myplanet/data/local/exam_mapper.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/ui/courses/course_progress_screen.dart';
import 'package:myplanet/ui/courses/courses_progress_screen.dart';

import '../../support/widget_harness.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

/// Returns a fixed user without touching prefs — the screen awaits
/// `sessionProvider.future`, so an unresolved session would hang it.
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

/// Tests for the port of `CourseProgressActivity` + `ProgressGridAdapter`.
///
/// The grid is deliberately laid out as explicit rows rather than a lazy
/// `GridView`, so every cell is mounted and a "this cell has no text"
/// assertion means what it says. If that ever changes, these tests need
/// `scrollUntilVisible` — a lazy grid mounts only the viewport and turns the
/// negative assertions here into passes for the wrong reason.
void main() {
  late AppDatabase database;

  setUp(() async {
    database = AppDatabase.memory();
  });
  tearDown(() => database.close());

  Future<void> pumpScreen(WidgetTester tester, {String courseId = 'course-1'}) {
    return tester.pumpWidget(
      wrapScreen(
        CourseProgressScreen(courseId: courseId),
        overrides: [
          appDatabaseProvider.overrideWithValue(database),
          sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
        ],
      ),
    );
  }

  testWidgets('the header shows the title, the run and the truncated ring', (
    tester,
  ) async {
    await _seedCourse(database, questionIds: const []);
    // Steps 1 and 2 opened of 3.
    for (final stepNum in [1, 2]) {
      await database.courseProgressDao.upsert(
        CourseProgressCompanion.insert(
          id: 'p-$stepNum',
          stepNum: Value(stepNum),
          userId: const Value('user-1'),
          courseId: const Value('course-1'),
        ),
      );
    }

    await pumpScreen(tester);
    await tester.pumpAndSettle();

    expect(find.text('Intro to Water'), findsOneWidget);
    expect(find.text('Progress 2 of 3'), findsOneWidget);
    expect(find.text('Steps'), findsOneWidget);
    // `(2 / 3 * 100).toInt()` — the ring's centre label is the bare value,
    // `app:progressTextType="progress"`.
    expect(find.text('66'), findsOneWidget);
  });

  testWidgets('one cell per step, blank when the step has no exam', (
    tester,
  ) async {
    await _seedCourse(database, questionIds: const []);

    await pumpScreen(tester);
    await tester.pumpAndSettle();

    // Three steps, none carrying an exam: exactly three cells, no percentage
    // text on any of them. `greaterThanOrEqualTo(3)`, which this used to
    // assert, cannot fail on the four cells an off-by-one would draw — which
    // is the bug the test is named for. Only the cells are `Container`s; the
    // ring is a `SizedBox` + `DecoratedBox`.
    expect(find.byType(Container), findsNWidgets(3));
    expect(find.textContaining('%'), findsNothing);
  });

  testWidgets('a half-answered exam paints a yellow cell with its percentage', (
    tester,
  ) async {
    await _seedCourse(database, questionIds: ['q1', 'q2']);
    await _seedAttempt(database, answeredQuestionIds: ['q1']);

    await pumpScreen(tester);
    await tester.pumpAndSettle();

    expect(find.text('50.0%'), findsOneWidget);
    expect(
      _cellColour(tester, '50.0%'),
      const Color(0xFFFFEB3B), // md_yellow_500
    );
  });

  testWidgets('a fully answered exam paints a green cell', (tester) async {
    await _seedCourse(database, questionIds: ['q1', 'q2']);
    await _seedAttempt(database, answeredQuestionIds: ['q1', 'q2']);

    await pumpScreen(tester);
    await tester.pumpAndSettle();

    // "100.0%", not "100%": the Kotlin renders `JsonPrimitive.getAsString()`
    // over a Double.
    expect(find.text('100.0%'), findsOneWidget);
    expect(
      _cellColour(tester, '100.0%'),
      const Color(0xFF4CAF50), // md_green_500
    );
  });

  testWidgets('a partial last grid row is left-aligned, not centred', (
    tester,
  ) async {
    // `GridLayoutManager(this, 4)` puts item n in span `n % 4`, so a ninth
    // step sits in the **first** column of a new row. Rows centred inside the
    // enclosing Column instead put it under the gap between columns 1 and 2.
    await database.courseDao.upsertAll(
      [CoursesCompanion.insert(id: 'c9', courseTitle: const Value('Nine'))],
      [
        for (var i = 0; i < 9; i++)
          CourseStepsCompanion.insert(
            id: 'c9:$i',
            courseId: const Value('c9'),
            stepIndex: Value(i),
          ),
      ],
    );

    await pumpScreen(tester, courseId: 'c9');
    await tester.pumpAndSettle();

    final cells = find.byType(Container);
    expect(cells, findsNWidgets(9));
    // The ninth cell starts at the same x as the first, not in the middle.
    expect(
      tester.getTopLeft(cells.at(8)).dx,
      tester.getTopLeft(cells.at(0)).dx,
    );
  });

  testWidgets('a long percentage clips inside its 60dp cell', (tester) async {
    // One of three answered is `33.33333333333333%` — the Kotlin renders the
    // Double verbatim into a 60dp card at 18sp and the TextView wraps and is
    // clipped to the card. This pins that the port does the same rather than
    // bleeding over its neighbours: the text lays out to exactly the cell's
    // 60x60 box (`Text`'s default `TextOverflow.clip` cuts the last line),
    // and nothing reports an overflow.
    await _seedCourse(database, questionIds: ['q1', 'q2', 'q3']);
    await _seedAttempt(database, answeredQuestionIds: ['q1']);

    await pumpScreen(tester);
    await tester.pumpAndSettle();

    final label = find.text('33.33333333333333%');
    expect(label, findsOneWidget);
    expect(tester.getSize(label), const Size(60, 60));
    expect(tester.takeException(), isNull);
  });

  testWidgets('an unattempted exam is blank, not 0%', (tester) async {
    await _seedCourse(database, questionIds: ['q1', 'q2']);

    await pumpScreen(tester);
    await tester.pumpAndSettle();

    expect(find.textContaining('%'), findsNothing);
  });

  testWidgets('a course with no steps renders an empty grid', (tester) async {
    await database.courseDao.upsertAll([
      CoursesCompanion.insert(
        id: 'course-empty',
        courseTitle: const Value('Empty'),
      ),
    ], const []);

    await pumpScreen(tester, courseId: 'course-empty');
    await tester.pumpAndSettle();

    // The Kotlin submits an empty list to the adapter: header, no cells, no
    // empty-state message of its own.
    expect(find.text('Empty'), findsOneWidget);
    expect(find.text('Progress 0 of 0'), findsOneWidget);
    expect(find.text('0'), findsOneWidget);
    expect(find.textContaining('%'), findsNothing);
  });

  testWidgets(
    'answering a question in the real exam flow reaches the grid — and a '
    'wrong attempt is counted as a mistake on the way',
    (tester) async {
      // The end-to-end link the last three phases could not demonstrate:
      // `saveExamAnswer` writes the answer row, `courseProgress` joins it back
      // through `exams.stepId` -> `course_steps.id`, and the cell renders it.
      // Nothing is faked between the answer and the pixel except the network.
      await _seedCourse(
        database,
        questionIds: ['q1', 'q2'],
        correctChoice: 'paris',
      );
      final exam = (await database.examDao.getByStepIds(['course-1:1'])).single;
      final questions = await database.examDao.questionsForExams([exam.id]);
      final repository = SubmissionsRepository(
        _MockPlanetApi(),
        database.submissionDao,
        database.submitPhotosDao,
        database.surveyDao,
      );

      final submissionId = await repository.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'user-1',
        courseId: 'course-1',
      );

      // A wrong answer first: it must not advance, and it must be recorded.
      final wrong = await repository.saveExamAnswer(
        submissionId: submissionId,
        question: questions.first,
        answer: const ExamDraftAnswer(value: 'london'),
        isFinal: false,
        isExplicitSubmission: false,
      );
      expect(wrong, isFalse);
      // Then the right one.
      final right = await repository.saveExamAnswer(
        submissionId: submissionId,
        question: questions.first,
        answer: const ExamDraftAnswer(value: 'paris'),
        isFinal: false,
        isExplicitSubmission: false,
      );
      expect(right, isTrue);

      final answers = await database.submissionDao.answersFor(submissionId);
      expect(answers, hasLength(1));
      // The mistake survives the retry that corrected it — the accumulator
      // Phase 110 restored.
      expect(answers.single.mistakes, 1);
      expect(answers.single.examId, exam.id);

      await pumpScreen(tester);
      await tester.pumpAndSettle();

      // One of the exam's two questions answered.
      expect(find.text('50.0%'), findsOneWidget);
    },
  );

  testWidgets('the My Progress row opens the grid for its course', (
    tester,
  ) async {
    // `courseProgressStreamProvider` is overridden rather than fed from the
    // database: it is a `StreamProvider` over a drift `watch()` query, and
    // `widget_harness.dart` is explicit that a screen test should override the
    // provider a screen reads instead of opening a live drift stream inside
    // the fake-async zone. Its data path is covered without a widget tree in
    // `test/providers/course_progress_provider_test.dart`.
    await tester.pumpWidget(
      wrapScreen(
        const CoursesProgressScreen(),
        overrides: [
          courseProgressStreamProvider.overrideWith(
            (ref) => Stream.value(const [
              CoursesProgressRow(
                courseId: 'course-1',
                courseName: 'Intro to Water',
                progressCurrent: 1,
                progressMax: 3,
                mistakes: 5,
                stepMistakes: {1: 5},
              ),
            ]),
          ),
        ],
        pushTargets: {
          // The Kotlin row starts `CourseProgressActivity`, not the course
          // detail screen.
          '/courses/progress/:courseId': (context) =>
              const Text('GRID_ROUTE course-1'),
        },
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Intro to Water'), findsOneWidget);
    // `showStepMistakes` labels the row `stepKey + 1`, and the oval carries
    // the total.
    expect(find.text('2'), findsOneWidget);
    expect(find.text('5'), findsNWidgets(2));

    await tester.tap(find.text('Intro to Water'));
    await tester.pumpAndSettle();

    expect(find.text('GRID_ROUTE course-1'), findsOneWidget);
  });

  testWidgets('the mistakes table lists its exams in ascending order', (
    tester,
  ) async {
    // Kotlin's `mistakesMap` is a `HashMap<String, Int>` keyed by the exam
    // ordinal as a string, round-tripped through Gson into a `LinkedTreeMap`,
    // so the adapter walks it in bucket order — which for the keys "0".."9"
    // is ascending. A Dart map preserves the order the answers came off
    // `answersFor` instead, so a submission whose first answer belongs to a
    // later exam listed its rows backwards.
    await tester.pumpWidget(
      wrapScreen(
        const CoursesProgressScreen(),
        overrides: [
          courseProgressStreamProvider.overrideWith(
            (ref) => Stream.value(const [
              CoursesProgressRow(
                courseId: 'course-1',
                courseName: 'Ordering',
                progressCurrent: 1,
                progressMax: 3,
                mistakes: 33,
                // Built in the order the answers arrived: exam 2 first. The
                // counts are two-digit so they cannot be confused with the
                // step labels the assertion looks for.
                stepMistakes: {2: 11, 0: 22},
              ),
            ]),
          ),
        ],
      ),
    );
    await tester.pumpAndSettle();

    // The step column reads `key + 1`, so ascending keys give 1 then 3.
    final labels = tester
        .widgetList<Text>(find.byType(Text))
        .map((text) => text.data)
        .whereType<String>()
        .toList();
    expect(labels, containsAllInOrder(['1', '22', '3', '11']));
  });

  testWidgets('a My Progress row with no progress figures is inert', (
    tester,
  ) async {
    // `CoursesProgressAdapter` installs the click listener inside
    // `if (progressCurrent != null && progressMax != null)`.
    //
    // Reached only through this override: `courseProgressSummary` writes an
    // entry for every requested id, so the real provider never produces a null
    // pair. Kotlin's branch is dead too — its `else` writes `JsonNull` and
    // `ProgressViewModel`'s `getAsJsonObject("progress")` would throw before
    // the adapter saw it. Ported and covered anyway because the gate is one
    // line and its absence is invisible; recorded here so nobody mistakes this
    // for a live state.
    await tester.pumpWidget(
      wrapScreen(
        const CoursesProgressScreen(),
        overrides: [
          courseProgressStreamProvider.overrideWith(
            (ref) => Stream.value(const [
              CoursesProgressRow(
                courseId: 'course-1',
                courseName: 'No Figures',
              ),
            ]),
          ),
        ],
        pushTargets: {
          '/courses/progress/:courseId': (context) =>
              const Text('GRID_ROUTE course-1'),
        },
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('No Figures'));
    await tester.pumpAndSettle();

    expect(find.text('GRID_ROUTE course-1'), findsNothing);
    expect(find.text('No Figures'), findsOneWidget);
  });
}

/// The background colour of the grid cell whose text is [label].
Color _cellColour(WidgetTester tester, String label) {
  final container = tester.widget<Container>(
    find.ancestor(of: find.text(label), matching: find.byType(Container)).first,
  );
  return (container.decoration! as BoxDecoration).color!;
}

/// A three-step course whose second step carries an exam with [questionIds],
/// built through the real `CourseMapper`/`ExamMapper` pair the `courses` sync
/// walk runs — so the `exams.stepId` join under test is the produced one.
Future<void> _seedCourse(
  AppDatabase database, {
  required List<String> questionIds,
  String? correctChoice,
}) async {
  final doc = <String, dynamic>{
    '_id': 'course-1',
    'courseTitle': 'Intro to Water',
    'steps': [
      {'stepTitle': 'Intro'},
      {
        'stepTitle': 'Assessment',
        if (questionIds.isNotEmpty)
          'exam': {
            '_id': 'exam-1',
            'type': 'courses',
            'questions': [
              for (final id in questionIds)
                {
                  'id': id,
                  'title': '$id?',
                  'type': 'input',
                  'correctChoice': ?correctChoice,
                },
            ],
          },
      },
      {'stepTitle': 'Outro'},
    ],
  };

  final course = CourseMapper.fromDoc(doc)!;
  await database.courseDao.upsertAll([course.course], course.steps);
  final mapped = ExamMapper.fromCourseDoc(
    doc,
    stepIdFor: CourseMapper.stepIdFor,
  );
  for (final entry in mapped) {
    await database.examDao.upsertAll(
      [entry.exam],
      <String, List<ExamQuestionsCompanion>>{
        entry.exam.id.value: entry.questions,
      },
    );
  }
}

Future<void> _seedAttempt(
  AppDatabase database, {
  required List<String> answeredQuestionIds,
}) async {
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
        for (final questionId in answeredQuestionIds)
          SubmissionAnswersCompanion.insert(
            id: 'sub-1:$questionId',
            submissionId: 'sub-1',
            examId: const Value('exam-1'),
            questionId: Value(questionId),
          ),
      ],
    },
  );
}
