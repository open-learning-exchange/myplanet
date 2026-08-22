import 'package:flutter_test/flutter_test.dart';
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
  });
}
