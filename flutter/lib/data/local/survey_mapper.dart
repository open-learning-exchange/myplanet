import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';
import 'converters.dart';
import 'exam_mapper.dart';

/// Port of `StepExam.insertCourseStepsExams` and
/// `ExamQuestion.insertExamQuestions` for survey documents.
class SurveyMapper {
  const SurveyMapper._();

  /// Port of `ExamQuestion.insertExamQuestions` for a survey document — shared
  /// by the `exams` database walk and by the `steps[i].survey` walk.
  static List<SurveyQuestionsCompanion> _parseQuestions(
    String surveyId,
    Object? rawQuestions,
  ) {
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
            id: remoteId.isEmpty ? '$surveyId:$index' : '$surveyId:$remoteId',
            surveyId: surveyId,
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
    return questions;
  }

  static SurveyMapping? fromDoc(Map<String, dynamic> doc) {
    final id = JsonUtils.getString('_id', doc);
    final type = JsonUtils.getString('type', doc);
    if (id.isEmpty || id.startsWith('_design/') || type != 'surveys') {
      return null;
    }
    final questions = _parseQuestions(id, doc['questions']);
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
        // Absent, not `Value(null)`, when the document omits them — see the
        // note on `ExamMapper.fromDoc`. The `exams` database walk is called
        // with a blank course and step id, so it must not overwrite the join
        // the `courses` walk owns.
        courseId: _presentOrAbsent('courseId', doc),
        stepId: _presentOrAbsent('stepId', doc),
      ),
      questions: questions,
    );
  }

  /// Port of `CoursesRepositoryImpl.collectRoomExam(stepJson, "survey", …)` —
  /// the surveys embedded in a course document under `steps[i].survey`.
  ///
  /// Kotlin keeps these in the same `exams` table as the tests and separates
  /// them at query time (`getByStepIdAndType(stepId, "surveys")`); the port has
  /// a separate `surveys` table, so the split happens here.
  ///
  /// **One deliberate divergence.** Kotlin types a `steps[i].survey` with no
  /// `type` key as `"survey"` — singular — and then only ever queries for
  /// `"surveys"`, so such a row is unreachable from both the Take Test and the
  /// Take Survey button. The port files every `steps[i].survey` as a survey.
  /// Reproducing the quirk would mean putting a survey in the exams table and
  /// offering it as a graded test, which is worse than Kotlin's silence rather
  /// than equal to it.
  static List<SurveyMapping> fromCourseDoc(
    Map<String, dynamic> doc, {
    required String Function(String courseId, int stepIndex) stepIdFor,
  }) => ExamMapper.mapStepExams(
    doc,
    stepIdFor: stepIdFor,
    examKey: 'survey',
    build: (surveyId, stepId, courseId, surveyJson) => SurveyMapping(
      survey: SurveysCompanion.insert(
        id: surveyId,
        rev: Value(JsonUtils.getStringOrNull('_rev', surveyJson)),
        name: Value(JsonUtils.getStringOrNull('name', surveyJson)),
        description: Value(
          JsonUtils.getStringOrNull('description', surveyJson),
        ),
        createdDate: Value(JsonUtils.getLong('createdDate', surveyJson)),
        updatedDate: Value(JsonUtils.getLong('updatedDate', surveyJson)),
        adoptionDate: Value(JsonUtils.getLong('adoptionDate', surveyJson)),
        createdBy: Value(JsonUtils.getStringOrNull('createdBy', surveyJson)),
        totalMarks: Value(JsonUtils.getInt('totalMarks', surveyJson)),
        passingPercentage: Value(
          JsonUtils.getStringOrNull('passingPercentage', surveyJson),
        ),
        sourcePlanet: Value(
          JsonUtils.getStringOrNull('sourcePlanet', surveyJson),
        ),
        teamId: Value(JsonUtils.getStringOrNull('teamId', surveyJson)),
        teamShareAllowed: Value(
          JsonUtils.getBool('teamShareAllowed', surveyJson),
        ),
        sourceSurveyId: Value(
          JsonUtils.getStringOrNull('sourceSurveyId', surveyJson),
        ),
        courseId: Value(courseId),
        stepId: Value(stepId),
      ),
      questions: _parseQuestions(surveyId, surveyJson['questions']),
    ),
  );

  /// `Value(doc[key])` when the document carries the key, `Value.absent()`
  /// otherwise, so an upsert leaves a column another writer owns alone.
  static Value<String?> _presentOrAbsent(
    String key,
    Map<String, dynamic> doc,
  ) => doc.containsKey(key)
      ? Value(JsonUtils.getStringOrNull(key, doc))
      : const Value.absent();
}

class SurveyMapping {
  const SurveyMapping({required this.survey, required this.questions});
  final SurveysCompanion survey;
  final List<SurveyQuestionsCompanion> questions;
}
