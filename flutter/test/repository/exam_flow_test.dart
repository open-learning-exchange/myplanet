import 'dart:convert';

import 'package:drift/drift.dart' hide isNull, isNotNull;
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/core/config/server_config.dart';
import 'package:myplanet/core/network/network_result.dart';
import 'package:myplanet/core/sync/sync_result.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/repository/submissions_repository.dart';
import 'package:myplanet/repository/surveys_repository.dart';
import 'package:myplanet/repository/exam_grading.dart';

void main() {
  late AppDatabase database;
  late MockPlanetApi api;
  late SurveysRepository surveys;
  late SubmissionsRepository submissions;

  const config = ServerConfig(
    serverUrl: 'https://planet.example',
    couchDbUrl: 'https://satellite:1234@planet.example:443',
    pin: '1234',
  );

  setUp(() {
    database = AppDatabase.memory();
    api = MockPlanetApi();
    submissions = SubmissionsRepository(
      api,
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
    );
    surveys = SurveysRepository(
      api,
      database.surveyDao,
      database.examDao,
      submissions,
    );
  });
  tearDown(() => database.close());

  void stubExamsDatabase(List<Map<String, dynamic>> docs) {
    when(
      () => api.getJsonObject(any(), authHeader: any(named: 'authHeader')),
    ).thenAnswer((invocation) async {
      final url = invocation.positionalArguments.single as String;
      if (url.endsWith('limit=0')) {
        return NetworkSuccess<Map<String, dynamic>>({
          'total_rows': docs.length,
        });
      }
      return NetworkSuccess<Map<String, dynamic>>({
        'rows': [
          for (final doc in docs) {'doc': doc},
        ],
      });
    });
  }

  Map<String, dynamic> examDoc(String id, {String? stepId}) => {
    '_id': id,
    'type': 'exam',
    'name': 'Exam $id',
    'courseId': 'course-1',
    'stepId': stepId ?? 'step-1',
    'questions': [
      {
        'id': '$id-q1',
        'title': 'Capital of France?',
        'type': 'select',
        'choices': [
          {'id': 'c1', 'text': 'Paris'},
          {'id': 'c2', 'text': 'Lyon'},
        ],
        'correctChoice': 'c1',
      },
    ],
  };

  group('the exams database pull', () {
    test('caches exams alongside surveys from the same page', () async {
      // Both live in the CouchDB `exams` database. The exam documents were
      // already being downloaded and then dropped on the floor.
      stubExamsDatabase([
        {
          '_id': 'survey-1',
          'type': 'surveys',
          'name': 'Feedback',
          'questions': const [],
        },
        examDoc('exam-1'),
      ]);

      final result = await surveys.sync(config: config);

      expect(result, isA<SyncComplete>());
      expect(await database.surveyDao.getById('survey-1'), isNotNull);
      final exam = await database.examDao.getById('exam-1');
      expect(exam, isNotNull);
      expect(exam!.stepId, 'step-1');
      expect(await database.examDao.questionsFor('exam-1'), hasLength(1));
    });

    test('an exam is reachable from its step', () async {
      stubExamsDatabase([examDoc('exam-1', stepId: 'step-7')]);
      await surveys.sync(config: config);
      expect((await database.examDao.getByStepId('step-7'))?.id, 'exam-1');
    });

    test('a stale exam and its questions are evicted', () async {
      stubExamsDatabase([examDoc('exam-1')]);
      await surveys.sync(config: config);

      stubExamsDatabase([examDoc('exam-2')]);
      await surveys.sync(config: config);

      expect(await database.examDao.getById('exam-1'), isNull);
      expect(await database.examDao.questionsFor('exam-1'), isEmpty);
      expect(await database.examDao.getById('exam-2'), isNotNull);
    });
  });

  group('grading', () {
    ExamQuestionRow question({
      required String type,
      required List<String> correct,
    }) => ExamQuestionRow(
      id: 'q1',
      examId: 'exam-1',
      type: type,
      correctChoices: correct,
      choices: const [
        ExamChoice(id: 'c1', text: 'Paris'),
        ExamChoice(id: 'c2', text: 'Lyon'),
      ],
      hasOtherOption: false,
      scaleMax: 9,
      position: 0,
    );

    test('a single choice is graded by id', () {
      final q = question(type: 'select', correct: const ['c1']);
      expect(ExamGrading.isSelectionCorrect(q, const ['c1']), isTrue);
      expect(ExamGrading.isSelectionCorrect(q, const ['c2']), isFalse);
      expect(ExamGrading.isSelectionCorrect(q, const []), isFalse);
    });

    test('multi-select requires the exact set', () {
      final q = question(type: 'selectMultiple', correct: const ['c1', 'c2']);
      expect(ExamGrading.isSelectionCorrect(q, const ['c2', 'c1']), isTrue);
      // A subset is not a pass — matching the Kotlin's all-or-nothing marking.
      expect(ExamGrading.isSelectionCorrect(q, const ['c1']), isFalse);
    });

    test('a question with no recorded answer key never scores', () {
      final q = question(type: 'select', correct: const []);
      expect(ExamGrading.isSelectionCorrect(q, const ['c1']), isFalse);
    });

    test('text answers are compared case- and space-insensitively', () {
      final q = question(type: 'input', correct: const ['paris']);
      expect(ExamGrading.isTextCorrect(q, '  Paris '), isTrue);
      expect(ExamGrading.isTextCorrect(q, 'Lyon'), isFalse);
      expect(ExamGrading.isTextCorrect(q, '   '), isFalse);
    });
  });

  group('the attempt', () {
    Future<ExamRow> seedExam() async {
      await database.examDao.upsertExam(
        ExamsCompanion.insert(
          id: 'exam-1',
          name: const Value('Unit 1'),
          courseId: const Value('course-1'),
          passingPercentage: const Value('80'),
        ),
      );
      return (await database.examDao.getById('exam-1'))!;
    }

    List<ExamQuestionRow> twoQuestions() => [
      ExamQuestionRow(
        id: 'q1',
        examId: 'exam-1',
        type: 'select',
        correctChoices: const ['c1'],
        choices: const [ExamChoice(id: 'c1', text: 'Paris')],
        hasOtherOption: false,
        scaleMax: 9,
        position: 0,
      ),
      ExamQuestionRow(
        id: 'q2',
        examId: 'exam-1',
        type: 'input',
        correctChoices: const ['blue'],
        choices: const [],
        hasOtherOption: false,
        scaleMax: 9,
        position: 1,
      ),
    ];

    test('is persisted as an uploadable submission', () async {
      final exam = await seedExam();
      final id = await submissions.createExamDraft(
        exam: exam,
        questions: twoQuestions(),
        userId: 'ada',
        answers: const {
          'q1': ExamDraftAnswer(choiceIds: ['c1'], isCorrect: true),
          'q2': ExamDraftAnswer(value: 'red'),
        },
      );

      final row = await database.submissionDao.getById(id);
      expect(row, isNotNull);
      expect(row!.type, 'exam');
      // Half right, so 50 — and it is on disk, not in a dismissed dialog.
      expect(row.grade, 50);
      expect(row.uploaded, isFalse);
      expect(await submissions.pendingUploads('ada'), hasLength(1));
      expect(jsonDecode(row.parent!)['_id'], 'exam-1');
    });

    test('records each answer with its verdict', () async {
      final exam = await seedExam();
      final id = await submissions.createExamDraft(
        exam: exam,
        questions: twoQuestions(),
        userId: 'ada',
        answers: const {
          'q1': ExamDraftAnswer(choiceIds: ['c1'], isCorrect: true),
          'q2': ExamDraftAnswer(value: 'blue', isCorrect: true),
        },
      );

      final answers = await database.submissionDao.answersFor(id);
      expect(answers, hasLength(2));
      final byQuestion = {for (final a in answers) a.questionId: a};
      expect(byQuestion['q1']!.valueChoices, ['{"id":"c1","text":"Paris"}']);
      expect(byQuestion['q1']!.isPassed, isTrue);
      expect(byQuestion['q2']!.value, 'blue');
      expect(byQuestion['q2']!.examId, 'exam-1');
    });

    test(
      'a select answer is stored as the choice object, not its id',
      () async {
        // `SubmissionsRepositoryImpl.saveExamAnswer` writes
        // `listOf("""{"id":"$ans","text":"$ansForCheck"}""")` for a `select`
        // question and sets `value` to `ansForCheck`, the choice's display text
        // via `ExamAnswerUtils.getChoiceTextById`. The port stored the bare id
        // and left `value` null.
        final exam = await seedExam();
        final id = await submissions.createExamDraft(
          exam: exam,
          questions: twoQuestions(),
          userId: 'ada',
          answers: const {
            'q1': ExamDraftAnswer(choiceIds: ['c1'], isCorrect: true),
          },
        );
        final answers = await database.submissionDao.answersFor(id);
        final q1 = answers.firstWhere((a) => a.questionId == 'q1');
        expect(q1.valueChoices, ['{"id":"c1","text":"Paris"}']);
        expect(q1.value, 'Paris');
      },
    );

    test('a selectMultiple answer stores objects and an empty value', () async {
      // Kotlin's `selectMultiple` branch sets `value = ""` and maps `listAns`
      // (text -> id) to `{"id":"$id","text":"$text"}` per entry.
      final exam = await seedExam();
      final questions = [
        ExamQuestionRow(
          id: 'q1',
          examId: 'exam-1',
          type: 'selectMultiple',
          correctChoices: const ['c1', 'c2'],
          choices: const [
            ExamChoice(id: 'c1', text: 'Paris'),
            ExamChoice(id: 'c2', text: 'Lyon'),
          ],
          hasOtherOption: false,
          scaleMax: 9,
          position: 0,
        ),
      ];
      final id = await submissions.createExamDraft(
        exam: exam,
        questions: questions,
        userId: 'ada',
        answers: const {
          'q1': ExamDraftAnswer(choiceIds: ['c1', 'c2'], isCorrect: true),
        },
      );
      final answers = await database.submissionDao.answersFor(id);
      expect(answers.single.valueChoices, [
        '{"id":"c1","text":"Paris"}',
        '{"id":"c2","text":"Lyon"}',
      ]);
      expect(answers.single.value, '');
    });

    test('an unknown choice id falls back to the id as its text', () async {
      // `getChoiceTextById` returns `map[id] ?: id`.
      final exam = await seedExam();
      final id = await submissions.createExamDraft(
        exam: exam,
        questions: twoQuestions(),
        userId: 'ada',
        answers: const {
          'q1': ExamDraftAnswer(choiceIds: ['ghost']),
        },
      );
      final answers = await database.submissionDao.answersFor(id);
      final q1 = answers.firstWhere((a) => a.questionId == 'q1');
      expect(q1.valueChoices, ['{"id":"ghost","text":"ghost"}']);
      expect(q1.value, 'ghost');
    });

    test('a select answer uploads its display text, as Kotlin does', () async {
      // `Answer.createObject` sends `value` whenever it is non-empty and only
      // falls back to `valueChoicesArray` when it is not — so a `select`
      // answer reaches Planet as a bare string, and `valueChoicesArray` is
      // reached only for `selectMultiple` (and an unanswered select).
      final exam = await seedExam();
      final id = await submissions.createExamDraft(
        exam: exam,
        questions: twoQuestions(),
        userId: 'ada',
        answers: const {
          'q1': ExamDraftAnswer(choiceIds: ['c1'], isCorrect: true),
        },
      );
      final payload = await submissions.serialize(
        (await submissions.getById(id))!,
      );
      final byQuestion = {
        for (final answer in payload['answers'] as List)
          (answer as Map)['questionId']: answer['value'],
      };
      expect(byQuestion['q1'], 'Paris');
    });

    test('an unanswered exam scores zero rather than failing', () async {
      final exam = await seedExam();
      final id = await submissions.createExamDraft(
        exam: exam,
        questions: twoQuestions(),
        userId: 'ada',
        answers: const {},
      );
      expect((await database.submissionDao.getById(id))!.grade, 0);
    });

    test('the profile screen attaches its answers to the attempt', () async {
      final exam = await seedExam();
      final id = await submissions.createExamDraft(
        exam: exam,
        questions: twoQuestions(),
        userId: 'ada',
        answers: const {},
      );
      await database.submissionDao.markUploaded(id, 'couch-1', '1-a');

      await submissions.markSubmissionComplete(id, {'gender': 'female'});

      final row = await database.submissionDao.getById(id);
      expect(jsonDecode(row!.user!)['gender'], 'female');
      // Back in the pending set, or the edit would never be sent.
      expect(row.uploaded, isFalse);
      expect(await submissions.pendingUploads('ada'), hasLength(1));
    });
  });
}

class MockPlanetApi extends Mock implements PlanetApi {}
