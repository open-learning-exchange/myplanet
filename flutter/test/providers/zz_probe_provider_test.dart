import 'package:drift/drift.dart' show Value;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/providers/app_providers.dart';
import 'package:myplanet/providers/courses_providers.dart';
import 'package:myplanet/providers/session_provider.dart';

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
  late ProviderContainer container;

  setUp(() async {
    db = AppDatabase.memory();
    container = ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        sessionProvider.overrideWith(() => _TestSessionNotifier(_user())),
      ],
    );
    await db.courseDao.upsertAll([
      CoursesCompanion.insert(
        id: 'c',
        courseId: const Value('c'),
        courseTitle: const Value('C'),
        userId: const Value(['u']),
      ),
    ], const []);
  });
  tearDown(() async {
    container.dispose();
    await db.close();
  });

  Future<void> exams(List<String> ids) => db.examDao.upsertAll(
    [
      for (final id in ids)
        ExamsCompanion.insert(id: id, courseId: const Value('c')),
    ],
    {
      for (final id in ids)
        id: [
          ExamQuestionsCompanion.insert(id: '$id-q0', examId: id, position: 0),
        ],
    },
  );

  Future<void> sub(
    String id,
    String examId,
    List<({String? questionId, String? examId, int mistakes})> answers,
  ) => db.submissionDao.upsertAll(
    [
      SubmissionsCompanion.insert(
        id: id,
        parentId: Value('$examId@c'),
        userId: const Value('u'),
        type: const Value('exam'),
      ),
    ],
    answers: {
      id: [
        for (var i = 0; i < answers.length; i++)
          SubmissionAnswersCompanion.insert(
            id: '$id:a$i',
            submissionId: id,
            examId: Value(answers[i].examId),
            questionId: Value(answers[i].questionId),
            mistakes: Value(answers[i].mistakes),
          ),
      ],
    },
  );

  test(
    'PROBE M answer with no questionId (the fixture the phase ships)',
    () async {
      await exams(['alpha', 'beta']);
      await sub('s1', 'beta', [
        (questionId: null, examId: 'beta', mistakes: 5),
      ]);
      final row = (await container.read(
        courseProgressStreamProvider.future,
      )).single;
      // ignore: avoid_print
      print(
        'M: mistakes=${row.mistakes} stepMistakes=${row.stepMistakes} '
        '(Kotlin: questionId null -> nothing counted, so 0 / no table)',
      );
    },
  );

  test('PROBE N answer whose question row is absent locally', () async {
    await exams(['alpha']);
    await sub('s1', 'alpha', [
      (questionId: 'a-question-that-was-deleted', examId: 'alpha', mistakes: 7),
    ]);
    final row = (await container.read(
      courseProgressStreamProvider.future,
    )).single;
    // ignore: avoid_print
    print(
      'N: mistakes=${row.mistakes} stepMistakes=${row.stepMistakes} '
      '(Kotlin: questionsMap miss -> 0 / no table)',
    );
  });

  test('PROBE O answer with a null examId but a real question row', () async {
    await exams(['alpha']);
    await sub('s1', 'alpha', [
      (questionId: 'alpha-q0', examId: null, mistakes: 9),
    ]);
    final row = (await container.read(
      courseProgressStreamProvider.future,
    )).single;
    // ignore: avoid_print
    print(
      'O: mistakes=${row.mistakes} stepMistakes=${row.stepMistakes} '
      '(Kotlin: question.examId=alpha -> 9 / {0:9})',
    );
  });

  test('PROBE P two submissions: accumulate vs last-wins', () async {
    await exams(['alpha', 'beta']);
    await sub('s1', 'alpha', [
      (questionId: 'alpha-q0', examId: 'alpha', mistakes: 2),
    ]);
    await sub('s2', 'beta', [
      (questionId: 'beta-q0', examId: 'beta', mistakes: 3),
    ]);
    final row = (await container.read(
      courseProgressStreamProvider.future,
    )).single;
    // ignore: avoid_print
    print('P: mistakes=${row.mistakes} stepMistakes=${row.stepMistakes}');
  });

  test('PROBE Q no submissions at all', () async {
    await exams(['alpha']);
    final row = (await container.read(
      courseProgressStreamProvider.future,
    )).single;
    // ignore: avoid_print
    print(
      'Q: mistakes=${row.mistakes} stepMistakes=${row.stepMistakes} '
      '(Kotlin: both keys absent -> mistakes null, renders "0")',
    );
  });

  test('PROBE R stepMistakes iteration order', () async {
    await exams(['alpha', 'beta', 'gamma']);
    await db.submissionDao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: 's1',
          parentId: const Value('gamma@c'),
          userId: const Value('u'),
          type: const Value('exam'),
        ),
      ],
      answers: {
        's1': [
          // answer ids order the read; gamma (index 2) first, alpha (0) second
          SubmissionAnswersCompanion.insert(
            id: 's1:a0',
            submissionId: 's1',
            examId: const Value('gamma'),
            questionId: const Value('gamma-q0'),
            mistakes: const Value(1),
          ),
          SubmissionAnswersCompanion.insert(
            id: 's1:a1',
            submissionId: 's1',
            examId: const Value('alpha'),
            questionId: const Value('alpha-q0'),
            mistakes: const Value(2),
          ),
        ],
      },
    );
    final row = (await container.read(
      courseProgressStreamProvider.future,
    )).single;
    // ignore: avoid_print
    print(
      'R: stepMistakes entries in order = '
      '${row.stepMistakes!.entries.map((e) => "${e.key + 1}:${e.value}").toList()}  (Kotlin HashMap<String,Int> "0","2" -> 1 then 3)',
    );
  });

  test('PROBE S the list is cached for the process lifetime', () async {
    await exams(['alpha']);
    await sub('s1', 'alpha', [
      (questionId: 'alpha-q0', examId: 'alpha', mistakes: 1),
    ]);
    final first = (await container.read(
      courseProgressStreamProvider.future,
    )).single;
    // The user takes the exam again and makes two more mistakes.
    await db.submissionDao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: 's2',
          parentId: const Value('alpha@c'),
          userId: const Value('u'),
          type: const Value('exam'),
        ),
      ],
      answers: {
        's2': [
          SubmissionAnswersCompanion.insert(
            id: 's2:a0',
            submissionId: 's2',
            examId: const Value('alpha'),
            questionId: const Value('alpha-q0'),
            mistakes: const Value(2),
          ),
        ],
      },
    );
    final second = (await container.read(
      courseProgressStreamProvider.future,
    )).single;
    // ignore: avoid_print
    print(
      'S: first=${first.mistakes} second=${second.mistakes} '
      '(Kotlin re-reads on every onViewCreated -> 3)',
    );
  });
}
