import 'package:drift/drift.dart' show Value;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/repository/progress_repository.dart';

class _MockPlanetApi extends Mock implements PlanetApi {}

void main() {
  late AppDatabase db;
  late ProgressRepository repo;

  setUp(() async {
    db = AppDatabase.memory();
    repo = ProgressRepository(
      _MockPlanetApi(),
      db.courseDao,
      db.courseProgressDao,
      db.examDao,
      db.submissionDao,
      db.certificationDao,
    );
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
        for (var i = 0; i < 3; i++)
          CourseStepsCompanion.insert(
            id: 'c:$i',
            courseId: const Value('c'),
            stepIndex: Value(i),
          ),
      ],
    );
  });
  tearDown(() => db.close());

  Future<void> exam(String id, int questions, {String step = 'c:1'}) =>
      db.examDao.upsertAll(
        [
          ExamsCompanion.insert(
            id: id,
            stepId: Value(step),
            courseId: const Value('c'),
          ),
        ],
        {
          id: [
            for (var i = 0; i < questions; i++)
              ExamQuestionsCompanion.insert(
                id: '$id-q$i',
                examId: id,
                position: i,
              ),
          ],
        },
      );

  Future<void> attempt(
    String sub,
    String examId,
    int answers, {
    String? parentId,
  }) => db.submissionDao.upsertAll(
    [
      SubmissionsCompanion.insert(
        id: sub,
        parentId: Value(parentId ?? '$examId@c'),
        userId: const Value('u'),
        type: const Value('exam'),
      ),
    ],
    answers: {
      sub: [
        for (var i = 0; i < answers; i++)
          SubmissionAnswersCompanion.insert(
            id: '$sub:a$i',
            submissionId: sub,
            examId: Value(examId),
            questionId: Value('$examId-q$i'),
          ),
      ],
    },
  );

  test('PROBE A two exams with questions on one step: which wins', () async {
    await exam('ex-a', 2);
    await exam('ex-b', 2);
    await attempt('s-a', 'ex-a', 2); // 100%
    await attempt('s-b', 'ex-b', 1); // 50%
    final d = await repo.courseProgress('c', 'u');
    // ignore: avoid_print
    print(
      'A: pct=${d.steps[1].percentage} label=${d.steps[1].percentageLabel} '
      'completed=${d.steps[1].completed}',
    );
    // ignore: avoid_print
    print(
      'A: exam row order = '
      '${(await db.examDao.getByStepIds(['c:1'])).map((e) => e.id).toList()}',
    );
  });

  test('PROBE B second exam unattempted keeps the first percentage', () async {
    await exam('ex-a', 2);
    await exam('ex-b', 2);
    await attempt('s-a', 'ex-a', 1);
    final d = await repo.courseProgress('c', 'u');
    // ignore: avoid_print
    print('B: pct=${d.steps[1].percentage} completed=${d.steps[1].completed}');
  });

  test('PROBE C more answers than questions', () async {
    await exam('ex-a', 2);
    await attempt('s-a', 'ex-a', 3);
    final d = await repo.courseProgress('c', 'u');
    // ignore: avoid_print
    print(
      'C: label=${d.steps[1].percentageLabel} '
      'completed=${d.steps[1].completed}',
    );
  });

  test('PROBE D thirds and other repeating strings', () async {
    for (final n in [3, 6, 7, 9, 11, 30, 1000000]) {
      // ignore: avoid_print
      print('D: 1/$n -> ${((1 / n) * 100)}   2/$n -> ${((2 / n) * 100)}');
    }
    await exam('ex-a', 3);
    await attempt('s-a', 'ex-a', 1);
    final d = await repo.courseProgress('c', 'u');
    // ignore: avoid_print
    print(
      'D: label="${d.steps[1].percentageLabel}" '
      'len=${d.steps[1].percentageLabel!.length}',
    );
  });

  test('PROBE E guest / null user id', () async {
    await exam('ex-a', 2);
    // A guest-style row keyed on a locally-minted id, progress written for it.
    await db.courseProgressDao.upsert(
      CourseProgressCompanion.insert(
        id: 'p1',
        stepNum: const Value(1),
        userId: const Value('1758000000000'),
        courseId: const Value('c'),
      ),
    );
    final asNull = await repo.courseProgress('c', null);
    final asLocal = await repo.courseProgress('c', '1758000000000');
    // ignore: avoid_print
    print('E: null.current=${asNull.current} local.current=${asLocal.current}');
  });

  test('PROBE F parentId oddities', () async {
    await exam('ex-a', 2);
    await attempt('s1', 'ex-a', 2, parentId: 'ex-a@c@extra');
    final d1 = await repo.courseProgress('c', 'u');
    // ignore: avoid_print
    print('F1 (ex-a@c@extra): ${d1.steps[1].percentageLabel}');

    await db.submissionDao.upsertAll([
      SubmissionsCompanion.insert(
        id: 's2',
        parentId: const Value('@ex-a'),
        userId: const Value('u'),
        type: const Value('exam'),
      ),
    ], answers: const {});
    final d2 = await repo.courseProgress('c', 'u');
    // ignore: avoid_print
    print('F2 (@ex-a present too): ${d2.steps[1].percentageLabel}');
  });

  test('PROBE G survey-typed submission for the same exam', () async {
    await exam('ex-a', 2);
    await db.submissionDao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: 's-survey',
          parentId: const Value('ex-a@c'),
          userId: const Value('u'),
          type: const Value('survey'),
        ),
      ],
      answers: {
        's-survey': [
          SubmissionAnswersCompanion.insert(
            id: 's-survey:a0',
            submissionId: 's-survey',
            examId: const Value('ex-a'),
          ),
        ],
      },
    );
    final d = await repo.courseProgress('c', 'u');
    // ignore: avoid_print
    print('G: ${d.steps[1].percentageLabel} (expect null: type != exam)');
  });
}
