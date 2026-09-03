import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/session_provider.dart';
import 'package:myplanet/ui/courses/course_progress_screen.dart';

import '../../support/widget_harness.dart';

class _TestSessionNotifier extends SessionNotifier {
  _TestSessionNotifier(this.user);
  final UserRow? user;
  @override
  Future<UserRow?> build() async => user;
}

UserRow _user() => UserRow(
  id: 'u',
  name: 'ada',
  rolesList: const ['learner'],
  userAdmin: false,
  joinDate: 0,
  isArchived: false,
  isUpdated: false,
);

void main() {
  late AppDatabase db;
  setUp(() => db = AppDatabase.memory());
  tearDown(() => db.close());

  Future<void> seed({
    required int steps,
    required int questionsOnEach,
    required int answered,
  }) async {
    await db.courseDao.upsertAll(
      [
        CoursesCompanion.insert(
          id: 'c',
          courseId: const Value('c'),
          courseTitle: const Value('C'),
          userId: const Value(['u']),
        ),
      ],
      [
        for (var i = 0; i < steps; i++)
          CourseStepsCompanion.insert(
            id: 'c:$i',
            courseId: const Value('c'),
            stepIndex: Value(i),
          ),
      ],
    );
    for (var i = 0; i < steps; i++) {
      if (questionsOnEach == 0) continue;
      await db.examDao.upsertAll(
        [
          ExamsCompanion.insert(
            id: 'ex$i',
            stepId: Value('c:$i'),
            courseId: const Value('c'),
          ),
        ],
        {
          'ex$i': [
            for (var q = 0; q < questionsOnEach; q++)
              ExamQuestionsCompanion.insert(
                id: 'ex$i-q$q',
                examId: 'ex$i',
                position: q,
              ),
          ],
        },
      );
      await db.submissionDao.upsertAll(
        [
          SubmissionsCompanion.insert(
            id: 's$i',
            parentId: Value('ex$i@c'),
            userId: const Value('u'),
            type: const Value('exam'),
          ),
        ],
        answers: {
          's$i': [
            for (var q = 0; q < answered; q++)
              SubmissionAnswersCompanion.insert(
                id: 's$i:a$q',
                submissionId: 's$i',
                examId: Value('ex$i'),
                questionId: Value('ex$i-q$q'),
              ),
          ],
        },
      );
    }
  }

  Future<void> pump(WidgetTester tester) => tester.pumpWidget(
    wrapScreen(
      const CourseProgressScreen(courseId: 'c'),
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
      ],
    ),
  );

  testWidgets('PROBE H a 17-character percentage in a 60px cell', (
    tester,
  ) async {
    await seed(steps: 1, questionsOnEach: 3, answered: 1);
    await pump(tester);
    await tester.pumpAndSettle();
    expect(find.text('33.33333333333333%'), findsOneWidget);
    final box = tester.renderObject<RenderBox>(find.text('33.33333333333333%'));
    // ignore: avoid_print
    print(
      'H: paragraph size = ${box.size}  '
      'exception=${tester.takeException()}',
    );
  });

  testWidgets('PROBE I nine steps: last-row alignment', (tester) async {
    await seed(steps: 9, questionsOnEach: 2, answered: 1);
    await pump(tester);
    await tester.pumpAndSettle();
    final cells = find.text('50.0%');
    expect(cells, findsNWidgets(9));
    for (var i = 0; i < 9; i++) {
      final rect = tester.getRect(cells.at(i));
      // ignore: avoid_print
      print(
        'I: cell $i left=${rect.left.toStringAsFixed(1)} '
        'top=${rect.top.toStringAsFixed(1)}',
      );
    }
    // ignore: avoid_print
    print('I: exception=${tester.takeException()}');
  });

  testWidgets('PROBE J narrow screen', (tester) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    await seed(steps: 4, questionsOnEach: 2, answered: 1);
    await pump(tester);
    await tester.pumpAndSettle();
    // ignore: avoid_print
    print('J: exception=${tester.takeException()}');
  });

  testWidgets('PROBE K very narrow screen (240dp)', (tester) async {
    tester.view.physicalSize = const Size(240, 640);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.resetPhysicalSize);
    await seed(steps: 4, questionsOnEach: 2, answered: 1);
    await pump(tester);
    await tester.pumpAndSettle();
    // ignore: avoid_print
    print('K: exception=${tester.takeException()}');
  });

  testWidgets('PROBE L 24 steps, all blank: are they all found?', (
    tester,
  ) async {
    await seed(steps: 24, questionsOnEach: 0, answered: 0);
    await pump(tester);
    await tester.pumpAndSettle();
    // ignore: avoid_print
    print(
      'L: containers=${find.byType(Container).evaluate().length} '
      'exception=${tester.takeException()}',
    );
  });
}
