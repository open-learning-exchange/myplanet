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

    test('leaves courseId and stepId out of the write when absent', () {
      final mapping = SurveyMapper.fromDoc({
        '_id': 'survey-1',
        'type': 'surveys',
        'name': 'Standalone survey',
      });
      expect(mapping, isNotNull);
      // `Value.absent()`, so an upsert of the standalone document does not
      // clear a join written by the `courses` walk — see the fromDoc note.
      expect(mapping!.survey.courseId.present, isFalse);
      expect(mapping.survey.stepId.present, isFalse);
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

  group('SurveyMapper.fromCourseDoc', () {
    String stepIdFor(String courseId, int index) => '$courseId:$index';

    // Phase 113. A survey attached to a course step arrives embedded in the
    // course document, exactly as a test does — `collectRoomExam(stepJson,
    // "survey", …)`. The port had no counterpart, so `SurveyDao.getByStepId`
    // could never match and the step view's Take Survey button was dead.
    test('attaches the step survey to the step id the course mapper mints', () {
      final mapped = SurveyMapper.fromCourseDoc({
        '_id': 'course-1',
        'steps': [
          {
            'stepTitle': 'Step one',
            'survey': {
              '_id': 'survey-1',
              'type': 'surveys',
              'name': 'How was it?',
              'questions': [
                {'id': 's1', 'title': 'Rate the step', 'type': 'input'},
              ],
            },
          },
        ],
      }, stepIdFor: stepIdFor);

      expect(mapped, hasLength(1));
      expect(mapped.single.survey.id.value, 'survey-1');
      expect(mapped.single.survey.stepId.value, 'course-1:0');
      expect(mapped.single.survey.courseId.value, 'course-1');
      expect(mapped.single.questions.single.surveyId.value, 'survey-1');
      expect(mapped.single.questions.single.header.value, 'Rate the step');
    });

    test('files a type-less step survey as a survey', () {
      // Kotlin types it `"survey"` — singular — and then only queries for
      // `"surveys"`, so such a row is reachable from neither button. See the
      // divergence note on `fromCourseDoc`.
      final mapped = SurveyMapper.fromCourseDoc({
        '_id': 'course-1',
        'steps': [
          {
            'survey': {'_id': 'survey-1', 'name': 'How was it?'},
          },
        ],
      }, stepIdFor: stepIdFor);
      expect(mapped, hasLength(1));
      expect(mapped.single.survey.stepId.value, 'course-1:0');
    });
  });

  group('SurveyMapper.fromDoc, step columns', () {
    test('records stepId and courseId when the document has them', () {
      final mapped = SurveyMapper.fromDoc({
        '_id': 'survey-1',
        'type': 'surveys',
        'courseId': 'course-1',
        'stepId': 'step-1',
      })!;
      expect(mapped.survey.stepId.value, 'step-1');
      expect(mapped.survey.courseId.value, 'course-1');
    });
  });
}
