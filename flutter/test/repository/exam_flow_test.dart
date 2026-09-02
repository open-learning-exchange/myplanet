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

    /// `checkTextAnswer` is `correctChoices.any { normalizedAns.contains(it) }`
    /// — containment, not equality, and its own Kotlin test asserts exactly
    /// that ("the expected word is here" passes a key of "expected word",
    /// `ExamAnswerUtilsTest.testCheckCorrectAnswer_InputText`). The port had
    /// exact equality, which under the retry gate refuses a right answer with
    /// a stray word and leaves the learner stuck on the question.
    test('a text answer passes by containment, as Kotlin marks it', () {
      final q = question(type: 'input', correct: const ['paris']);
      expect(ExamGrading.isTextCorrect(q, '  Paris '), isTrue);
      expect(ExamGrading.isTextCorrect(q, 'It is Paris, I think'), isTrue);
      expect(ExamGrading.isTextCorrect(q, 'Lyon'), isFalse);
      expect(ExamGrading.isTextCorrect(q, '   '), isFalse);
    });

    /// The one deliberate divergence. Kotlin fails a keyless question —
    /// `extractCorrectChoices` returns `emptyList()` and every check helper is
    /// vacuously false — which under the gate is a question no answer can
    /// satisfy and no route past. Every `ratingScale` question in an exam is
    /// in that position.
    test('a question with no answer key accepts any answer, but not none', () {
      final choiceQuestion = question(type: 'select', correct: const []);
      expect(
        ExamGrading.isSelectionCorrect(choiceQuestion, const ['c1']),
        isTrue,
      );
      expect(ExamGrading.isSelectionCorrect(choiceQuestion, const []), isFalse);

      final rating = question(type: 'ratingScale', correct: const []);
      expect(ExamGrading.isTextCorrect(rating, '7'), isTrue);
      expect(ExamGrading.isTextCorrect(rating, ''), isFalse);
    });

    /// `checkCorrectAnswer` dispatches on the type; the port adds one clause
    /// for a question that names no type but carries a pick, because
    /// `AnswerShape` stores such an answer as a choice and grading it as text
    /// would contradict that.
    test('the dispatcher follows the answer when the type says nothing', () {
      final untyped = ExamQuestionRow(
        id: 'q1',
        examId: 'exam-1',
        correctChoices: const ['c1'],
        choices: const [ExamChoice(id: 'c1', text: 'Paris')],
        hasOtherOption: false,
        scaleMax: 9,
        position: 0,
      );
      expect(
        ExamGrading.isCorrect(question: untyped, choiceIds: const ['c1']),
        isTrue,
      );
      expect(
        ExamGrading.isCorrect(question: untyped, text: 'Paris'),
        isFalse,
        reason: 'the key holds an id, and a text answer is not one',
      );
    });
  });

  group('the attempt', () {
    Future<ExamRow> seedExam({String? courseId = 'course-1'}) async {
      await database.examDao.upsertExam(
        ExamsCompanion.insert(
          id: 'exam-1',
          name: const Value('Unit 1'),
          courseId: Value(courseId),
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

    /// Answers a whole attempt the way the screen does — save each question in
    /// order, the last one as the explicit submission — and hands back the
    /// verdicts.
    Future<List<bool>> answerAll(
      String id,
      List<ExamQuestionRow> questions,
      Map<String, ExamDraftAnswer> answers,
    ) async {
      final verdicts = <bool>[];
      for (var index = 0; index < questions.length; index++) {
        final isFinal = index == questions.length - 1;
        verdicts.add(
          await submissions.saveExamAnswer(
            submissionId: id,
            question: questions[index],
            answer: answers[questions[index].id] ?? const ExamDraftAnswer(),
            isFinal: isFinal,
            isExplicitSubmission: isFinal,
          ),
        );
      }
      return verdicts;
    }

    test('opens as a pending attempt that is not yet uploadable', () async {
      final exam = await seedExam();
      final id = await submissions.startExamSession(
        exam: exam,
        questions: twoQuestions(),
        userId: 'ada',
      );

      final row = await database.submissionDao.getById(id);
      expect(row, isNotNull);
      expect(row!.type, 'exam');
      expect(row.status, 'pending');
      expect(row.grade, 0);
      expect(row.uploaded, isFalse);
      expect(jsonDecode(row.parent!)['_id'], 'exam-1');
      // `"$examId@$courseId"` — the shape `createExamSubmission` writes and
      // `ProgressRepository._examIdFromParent` splits back to the exam id. The
      // port stored the bare course id, so the per-step mistake counts could
      // never find their exam.
      expect(row.parentId, 'exam-1@course-1');
      // The question rows the detail screen renders are written up front.
      expect(
        await database.submissionDao.watchQuestions(id).first,
        hasLength(2),
      );
      expect(
        await submissions.pendingUploads('ada'),
        isEmpty,
        reason: 'a half-finished attempt must not go up',
      );
    });

    test('an exam with no course is parented by the exam alone', () async {
      final exam = await seedExam(courseId: null);
      final id = await submissions.startExamSession(
        exam: exam,
        questions: twoQuestions(),
        userId: 'ada',
      );
      expect((await database.submissionDao.getById(id))!.parentId, 'exam-1');
    });

    /// `startExamSession(recreate = true)` with `deleteStale`, which
    /// `deleteExamSubmissions` implements without a status filter: re-entering
    /// an exam discards the previous local attempt, answers and all, so
    /// `mistakes` accumulates within one sitting and never across two.
    test('re-entering the exam discards the previous attempt', () async {
      final exam = await seedExam();
      final questions = twoQuestions();
      final first = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );
      await submissions.saveExamAnswer(
        submissionId: first,
        question: questions.first,
        answer: const ExamDraftAnswer(choiceIds: ['c9']),
        isFinal: false,
        isExplicitSubmission: false,
      );
      expect(await database.submissionDao.answersFor(first), hasLength(1));

      final second = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );

      expect(second, isNot(first));
      expect(await database.submissionDao.getById(first), isNull);
      expect(await database.submissionDao.answersFor(first), isEmpty);
      expect(await database.submissionDao.watchQuestions(first).first, isEmpty);
      // Another user's attempt at the same exam is untouched.
      expect(await database.submissionDao.getById(second), isNotNull);
    });

    test('another user\'s attempt at the same exam survives', () async {
      final exam = await seedExam();
      final questions = twoQuestions();
      final theirs = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'bob',
      );
      await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );
      expect(await database.submissionDao.getById(theirs), isNotNull);
    });

    /// The accumulator, and the reason this had to become an incremental
    /// write: `mistakes` is `(existing?.mistakes ?: 0) + 1`, so there has to
    /// be an `existing` row to add to. The port graded the whole attempt in
    /// widget state and wrote it once at the end, which left the column
    /// structurally stuck at its default of 0 — and `serialize` was uploading
    /// that 0 as if it meant something.
    test('a wrong answer counts a mistake and does not pass', () async {
      final exam = await seedExam();
      final questions = twoQuestions();
      final id = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );

      for (var attempt = 1; attempt <= 3; attempt++) {
        final correct = await submissions.saveExamAnswer(
          submissionId: id,
          question: questions.first,
          answer: const ExamDraftAnswer(choiceIds: ['c9']),
          isFinal: false,
          isExplicitSubmission: false,
        );
        expect(correct, isFalse);
        final row = (await database.submissionDao.answersFor(id)).single;
        expect(row.mistakes, attempt);
        expect(row.isPassed, isFalse);
      }

      // Getting it right keeps the count rather than clearing it — the
      // finished row carries the whole retry history.
      final correct = await submissions.saveExamAnswer(
        submissionId: id,
        question: questions.first,
        answer: const ExamDraftAnswer(choiceIds: ['c1']),
        isFinal: false,
        isExplicitSubmission: false,
      );
      expect(correct, isTrue);
      final row = (await database.submissionDao.answersFor(id)).single;
      expect(row.mistakes, 3);
      expect(row.isPassed, isTrue);
      // `grade = if (type == "exam") 1` — every exam answer, right or wrong.
      expect(row.grade, 1);
    });

    test('one question\'s mistakes do not touch another\'s', () async {
      final exam = await seedExam();
      final questions = twoQuestions();
      final id = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );
      await submissions.saveExamAnswer(
        submissionId: id,
        question: questions.first,
        answer: const ExamDraftAnswer(choiceIds: ['c9']),
        isFinal: false,
        isExplicitSubmission: false,
      );
      await submissions.saveExamAnswer(
        submissionId: id,
        question: questions[1],
        answer: const ExamDraftAnswer(value: 'red'),
        isFinal: true,
        isExplicitSubmission: false,
      );

      final byQuestion = {
        for (final row in await database.submissionDao.answersFor(id))
          row.questionId: row,
      };
      expect(byQuestion['q1']!.mistakes, 1);
      expect(byQuestion['q2']!.mistakes, 1);
      expect(byQuestion, hasLength(2));
    });

    test('a finished attempt is sent for grading and uploads', () async {
      final exam = await seedExam();
      final questions = twoQuestions();
      final id = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );
      final verdicts = await answerAll(id, questions, const {
        'q1': ExamDraftAnswer(choiceIds: ['c1']),
        'q2': ExamDraftAnswer(value: 'blue'),
      });

      expect(verdicts, [true, true]);
      final row = (await database.submissionDao.getById(id))!;
      // `saveExamAnswer`'s `when`: `complete` is the *survey* value; an exam's
      // final answer is `requires grading`, which is what Planet's grading
      // queue selects on.
      expect(row.status, 'requires grading');
      expect(row.grade, 0, reason: 'Planet marks it, not the device');
      expect(await submissions.pendingUploads('ada'), hasLength(1));

      final answers = await database.submissionDao.answersFor(id);
      expect(answers.every((answer) => answer.isPassed), isTrue);
      expect(answers.every((answer) => answer.grade == 1), isTrue);
    });

    /// `onClick` assigns `isExplicitSubmission = true` **before** calling
    /// `updateAnsDb` (`ExamTakingFragment.kt:630-632`), so a wrong press of
    /// Finish writes `requires grading` for an exam the learner has not
    /// finished — and `isStepCompleted` (`status != 'pending'`) then unlocks
    /// the next course step. Not ported: here the status also decides
    /// `isUpdated`, so it would queue a half-finished attempt for upload too.
    test('a wrong final answer leaves the attempt pending', () async {
      final exam = await seedExam();
      final questions = twoQuestions();
      final id = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );
      final verdicts = await answerAll(id, questions, const {
        'q1': ExamDraftAnswer(choiceIds: ['c1']),
        'q2': ExamDraftAnswer(value: 'red'),
      });

      expect(verdicts, [true, false]);
      expect((await database.submissionDao.getById(id))!.status, 'pending');
      expect(await submissions.pendingUploads('ada'), isEmpty);
    });

    test('the mistake counts and verdicts reach the upload payload', () async {
      final exam = await seedExam();
      final questions = twoQuestions();
      final id = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );
      await submissions.saveExamAnswer(
        submissionId: id,
        question: questions.first,
        answer: const ExamDraftAnswer(choiceIds: ['c9']),
        isFinal: false,
        isExplicitSubmission: false,
      );
      await answerAll(id, questions, const {
        'q1': ExamDraftAnswer(choiceIds: ['c1']),
        'q2': ExamDraftAnswer(value: 'blue'),
      });

      final payload = await submissions.serialize(
        (await submissions.getById(id))!,
      );
      final byQuestion = {
        for (final answer in payload['answers'] as List)
          (answer as Map)['questionId']: answer,
      };
      // `Answer.createObject` sends `mistakes` and `passed` per answer. The
      // field was always being sent; before the gate it was always 0.
      expect(byQuestion['q1']!['mistakes'], 1);
      expect(byQuestion['q1']!['passed'], isTrue);
      expect(byQuestion['q2']!['mistakes'], 0);
      expect(payload['status'], 'requires grading');
      // `grade` is not in the per-answer object at all — `createObject` emits
      // only value/mistakes/passed/questionId.
      expect(byQuestion['q1']!.containsKey('grade'), isFalse);
    });

    /// Phase 106's answer-shape findings, re-expressed against the
    /// incremental writer. `saveExamAnswer` records the answer whether or not
    /// it is right, so the shape assertions hold on a wrong answer too.
    group('answer shape', () {
      Future<SubmissionAnswerRow> saveOne(
        ExamQuestionRow question,
        ExamDraftAnswer answer,
      ) async {
        final exam = await seedExam();
        final id = await submissions.startExamSession(
          exam: exam,
          questions: [question],
          userId: 'ada',
        );
        await submissions.saveExamAnswer(
          submissionId: id,
          question: question,
          answer: answer,
          isFinal: true,
          isExplicitSubmission: true,
        );
        return (await database.submissionDao.answersFor(id)).single;
      }

      test(
        'a select answer is stored as the choice object, not its id',
        () async {
          // `saveExamAnswer` writes
          // `listOf("""{"id":"$ans","text":"$ansForCheck"}""")` for a `select`
          // question and sets `value` to `ansForCheck`, the choice's display
          // text via `ExamAnswerUtils.getChoiceTextById`. The port stored the
          // bare id and left `value` null.
          final row = await saveOne(
            twoQuestions().first,
            const ExamDraftAnswer(choiceIds: ['c1']),
          );
          expect(row.valueChoices, ['{"id":"c1","text":"Paris"}']);
          expect(row.value, 'Paris');
          expect(row.examId, 'exam-1');
        },
      );

      test(
        'a selectMultiple answer stores objects and an empty value',
        () async {
          // Kotlin's `selectMultiple` branch sets `value = ""` and maps
          // `listAns` (text -> id) to `{"id":"$id","text":"$text"}` per entry.
          final row = await saveOne(
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
            const ExamDraftAnswer(choiceIds: ['c1', 'c2']),
          );
          expect(row.valueChoices, [
            '{"id":"c1","text":"Paris"}',
            '{"id":"c2","text":"Lyon"}',
          ]);
          expect(row.value, '');
        },
      );

      test('an unknown choice id falls back to the id as its text', () async {
        // `getChoiceTextById` returns `map[id] ?: id`.
        final row = await saveOne(
          twoQuestions().first,
          const ExamDraftAnswer(choiceIds: ['ghost']),
        );
        expect(row.valueChoices, ['{"id":"ghost","text":"ghost"}']);
        expect(row.value, 'ghost');
      });

      test(
        'a select answer uploads its display text, as Kotlin does',
        () async {
          // `Answer.createObject` sends `value` whenever it is non-empty and
          // only falls back to `valueChoicesArray` when it is not — so a
          // `select` answer reaches Planet as a bare string, and
          // `valueChoicesArray` is reached only for `selectMultiple` (and an
          // unanswered select).
          final exam = await seedExam();
          final questions = twoQuestions();
          final id = await submissions.startExamSession(
            exam: exam,
            questions: questions,
            userId: 'ada',
          );
          await answerAll(id, questions, const {
            'q1': ExamDraftAnswer(choiceIds: ['c1']),
            'q2': ExamDraftAnswer(value: 'blue'),
          });
          final payload = await submissions.serialize(
            (await submissions.getById(id))!,
          );
          final byQuestion = {
            for (final answer in payload['answers'] as List)
              (answer as Map)['questionId']: answer['value'],
          };
          expect(byQuestion['q1'], 'Paris');
        },
      );
    });

    test('the profile screen attaches its answers to the attempt', () async {
      final exam = await seedExam();
      final questions = twoQuestions();
      final id = await submissions.startExamSession(
        exam: exam,
        questions: questions,
        userId: 'ada',
      );
      await answerAll(id, questions, const {
        'q1': ExamDraftAnswer(choiceIds: ['c1']),
        'q2': ExamDraftAnswer(value: 'blue'),
      });
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
