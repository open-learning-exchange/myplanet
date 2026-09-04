import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:myplanet/data/api/planet_api.dart';
import 'package:myplanet/data/local/app_database.dart';
import 'package:myplanet/data/local/survey_mapper.dart';
import 'package:myplanet/repository/submissions_repository.dart';

class MockPlanetApi extends Mock implements PlanetApi {}

/// Pins the id convention that links a submitted survey's questions to its
/// answers.
///
/// `SubmissionsExporter` recovers a question's raw id by stripping the
/// submission-id prefix from the composite row id, then looks the answer up by
/// it. A survey question row id is *itself* composite (`surveyId:questionId`),
/// so nesting it produced a three-part key whose stripped form no longer
/// matched the answer's `questionId` — and every answer rendered blank in the
/// exported PDF.
void main() {
  late AppDatabase database;
  late SubmissionsRepository submissions;
  late SurveyDao surveyDao;

  setUp(() {
    database = AppDatabase.memory();
    surveyDao = database.surveyDao;
    submissions = SubmissionsRepository(
      MockPlanetApi(),
      database.submissionDao,
      database.submitPhotosDao,
      database.surveyDao,
      database.examDao,
    );
  });
  tearDown(() => database.close());

  Future<void> seedSurvey({required String questionId}) async {
    final mapped = SurveyMapper.fromDoc({
      '_id': 'survey-1',
      'type': 'surveys',
      'name': 'Survey',
      'questions': [
        {'_id': questionId, 'header': 'H', 'body': 'Body', 'type': 'input'},
      ],
    })!;
    await surveyDao.upsertAll([mapped.survey], {'survey-1': mapped.questions});
  }

  test(
    'a submitted answer is reachable by the question row it belongs to',
    () async {
      await seedSurvey(questionId: 'q-1');
      final questions = await surveyDao.questionsFor('survey-1');
      final id = await submissions.createSurveyDraft(
        survey: (await surveyDao.getById('survey-1'))!,
        questions: questions,
        userId: 'user-1',
        answers: {
          questions.single.id: const SubmissionDraftAnswer(value: 'Yes'),
        },
      );

      final storedQuestions = await submissions.watchQuestions(id).first;
      final storedAnswers = await submissions.watchAnswers(id).first;
      final answersByQuestion = {
        for (final answer in storedAnswers) answer.questionId: answer,
      };

      // The exporter's key derivation: strip the submission-id prefix.
      final rawId = storedQuestions.single.id.substring(id.length + 1);
      expect(
        answersByQuestion[rawId],
        isNotNull,
        reason:
            'the exported PDF renders this answer blank when it does not '
            'resolve',
      );
      expect(answersByQuestion[rawId]!.value, 'Yes');
    },
  );

  test('holds for a question the server gave no id', () async {
    // Falls back to `surveyId:index`, so the composite nests either way.
    await seedSurvey(questionId: '');
    final questions = await surveyDao.questionsFor('survey-1');
    final id = await submissions.createSurveyDraft(
      survey: (await surveyDao.getById('survey-1'))!,
      questions: questions,
      userId: 'user-1',
      answers: {questions.single.id: const SubmissionDraftAnswer(value: 'No')},
    );

    final storedQuestions = await submissions.watchQuestions(id).first;
    final storedAnswers = await submissions.watchAnswers(id).first;
    final answersByQuestion = {
      for (final answer in storedAnswers) answer.questionId: answer,
    };
    final rawId = storedQuestions.single.id.substring(id.length + 1);
    expect(answersByQuestion[rawId]?.value, 'No');
  });
}
