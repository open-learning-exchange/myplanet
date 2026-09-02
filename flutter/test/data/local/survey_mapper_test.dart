import 'package:flutter_test/flutter_test.dart';
import 'package:myplanet/data/local/converters.dart';
import 'package:myplanet/data/local/survey_mapper.dart';

void main() {
  group('SurveyMapper.fromDoc', () {
    test('maps courseId and stepId from the CouchDB document', () {
      final mapping = SurveyMapper.fromDoc({
        '_id': 'survey-1',
        'type': 'surveys',
        'name': 'Onboarding survey',
        'courseId': 'course-abc',
        'stepId': 'step-2',
      });
      expect(mapping, isNotNull);
      expect(mapping!.survey.courseId.value, 'course-abc');
      expect(mapping.survey.stepId.value, 'step-2');
    });

    test('leaves courseId null when absent', () {
      final mapping = SurveyMapper.fromDoc({
        '_id': 'survey-1',
        'type': 'surveys',
        'name': 'Standalone survey',
      });
      expect(mapping, isNotNull);
      expect(mapping!.survey.courseId.value, isNull);
      expect(mapping.survey.stepId.value, isNull);
    });

    test('returns null for non-survey documents', () {
      expect(SurveyMapper.fromDoc({'_id': 'exam-1', 'type': 'exam'}), isNull);
    });

    test('keeps a choice object as an id/text pair', () {
      // `ExamQuestion.insertExamQuestions` stores the choices array verbatim
      // (`gson.toJson(getJsonArray("choices", question))`) and every consumer
      // parses it back. Going through `JsonUtils.getStringList` instead called
      // `toString()` on each choice, so the id was destroyed and the label
      // became the Dart literal `{id: water, text: Water}`.
      final mapping = SurveyMapper.fromDoc({
        '_id': 'survey-1',
        'type': 'surveys',
        'questions': [
          {
            'id': 'q1',
            'body': 'Which service?',
            'type': 'select',
            'choices': [
              {'id': 'water', 'text': 'Water'},
              {'id': 'power', 'text': 'Power'},
            ],
          },
        ],
      });

      expect(mapping!.questions.single.choices.value, [
        const ExamChoice(id: 'water', text: 'Water'),
        const ExamChoice(id: 'power', text: 'Power'),
      ]);
    });

    test('keeps a bare-string choice, with the text doubling as the id', () {
      // `ExamTakingFragment.selectQuestion` falls through to `addRadioButton`
      // for a choices entry that is not an object.
      final mapping = SurveyMapper.fromDoc({
        '_id': 'survey-1',
        'type': 'surveys',
        'questions': [
          {
            'id': 'q1',
            'type': 'select',
            'choices': ['Yes', 'No'],
          },
        ],
      });

      expect(mapping!.questions.single.choices.value, [
        const ExamChoice(id: 'Yes', text: 'Yes'),
        const ExamChoice(id: 'No', text: 'No'),
      ]);
    });

    test('reads the question label out of `title`', () {
      // `ExamQuestion.insertExamQuestions` — which is what
      // `StepExam.insertCourseStepsExams` runs for a survey document too —
      // does `header = getString("title", question)`. Reading `header`
      // instead left every question of every real survey unlabelled; the
      // `ExamMapper` half of the same defect was already fixed.
      final mapping = SurveyMapper.fromDoc({
        '_id': 'survey-1',
        'type': 'surveys',
        'questions': [
          {'id': 'q1', 'title': 'Which service?', 'type': 'input'},
        ],
      });

      expect(mapping!.questions.single.header.value, 'Which service?');
    });
  });
}
