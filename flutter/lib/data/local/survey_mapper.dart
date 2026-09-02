import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';
import 'converters.dart';

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
        final remoteId = JsonUtils.getString('id', question).isNotEmpty
            ? JsonUtils.getString('id', question)
            : JsonUtils.getString('_id', question);
        questions.add(
          SurveyQuestionsCompanion.insert(
            id: remoteId.isEmpty ? '$id:$index' : '$id:$remoteId',
            surveyId: id,
            questionId: Value(remoteId.isEmpty ? null : remoteId),
            // `ExamQuestion.insertExamQuestions` — which is what
            // `StepExam.insertCourseStepsExams` runs for a survey document too
            // — reads `title` here, not `header`. Reading only `header` left
            // every question of every real survey unlabelled, the defect
            // `ExamMapper` already carries a note about; `header` stays as a
            // fallback because the port's own public-survey screen read it.
            header: Value(
              JsonUtils.getStringOrNull('title', question) ??
                  JsonUtils.getStringOrNull('header', question),
            ),
            body: Value(JsonUtils.getStringOrNull('body', question)),
            type: Value(JsonUtils.getStringOrNull('type', question)),
            // Kotlin keeps the choices array verbatim
            // (`gson.toJson(getJsonArray("choices", question))`) and every
            // consumer parses it back. `JsonUtils.getStringList` instead called
            // `toString()` on each choice object, so `{"id":"water"...}` was
            // stored as the Dart literal `{id: water, text: Water}` — no id to
            // answer with and a label nobody would write.
            choices: Value(ExamChoice.listFromJson(question['choices'])),
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
        courseId: Value(JsonUtils.getStringOrNull('courseId', doc)),
        stepId: Value(JsonUtils.getStringOrNull('stepId', doc)),
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
