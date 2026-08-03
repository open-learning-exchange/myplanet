import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `StepExam.insertCourseStepsExams` and
/// `ExamQuestion.insertExamQuestions` for survey documents.
class SurveyMapper {
  const SurveyMapper._();

  static SurveyMapping? fromDoc(Map<String, dynamic> doc) {
    final id = JsonUtils.getString('_id', doc);
    final type = JsonUtils.getString('type', doc);
    if (id.isEmpty || id.startsWith('_design/') || type != 'surveys') {
      return null;
    }
    final rawQuestions = doc['questions'];
    final questions = <SurveyQuestionsCompanion>[];
    if (rawQuestions is List) {
      for (var index = 0; index < rawQuestions.length; index++) {
        final question = rawQuestions[index];
        if (question is! Map<String, dynamic>) continue;
        final remoteId = JsonUtils.getString('_id', question);
        questions.add(
          SurveyQuestionsCompanion.insert(
            id: remoteId.isEmpty ? '$id:$index' : '$id:$remoteId',
            surveyId: id,
            questionId: Value(remoteId.isEmpty ? null : remoteId),
            header: Value(JsonUtils.getStringOrNull('header', question)),
            body: Value(JsonUtils.getStringOrNull('body', question)),
            type: Value(JsonUtils.getStringOrNull('type', question)),
            choices: Value(JsonUtils.getStringList('choices', question)),
            required: Value(JsonUtils.getBool('required', question)),
            position: index,
          ),
        );
      }
    }
    return SurveyMapping(
      survey: SurveysCompanion.insert(
        id: id,
        rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
        name: Value(JsonUtils.getStringOrNull('name', doc)),
        description: Value(JsonUtils.getStringOrNull('description', doc)),
        createdDate: Value(JsonUtils.getLong('createdDate', doc)),
        updatedDate: Value(JsonUtils.getLong('updatedDate', doc)),
        adoptionDate: Value(JsonUtils.getLong('adoptionDate', doc)),
        createdBy: Value(JsonUtils.getStringOrNull('createdBy', doc)),
        totalMarks: Value(JsonUtils.getInt('totalMarks', doc)),
        passingPercentage: Value(
          JsonUtils.getStringOrNull('passingPercentage', doc),
        ),
        sourcePlanet: Value(JsonUtils.getStringOrNull('sourcePlanet', doc)),
        isFromNation: Value(JsonUtils.getBool('isFromNation', doc)),
        teamId: Value(JsonUtils.getStringOrNull('teamId', doc)),
        teamShareAllowed: Value(JsonUtils.getBool('teamShareAllowed', doc)),
        sourceSurveyId: Value(JsonUtils.getStringOrNull('sourceSurveyId', doc)),
      ),
      questions: questions,
    );
  }
}

class SurveyMapping {
  const SurveyMapping({required this.survey, required this.questions});
  final SurveysCompanion survey;
  final List<SurveyQuestionsCompanion> questions;
}
