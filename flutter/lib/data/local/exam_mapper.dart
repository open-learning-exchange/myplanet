import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';

/// Port of `StepExam.insertCourseStepsExams` and `ExamQuestion.insertExamQuestions`.
class ExamMapper {
  const ExamMapper._();

  /// Parses exam documents with questions from CouchDB.
  /// Exam documents have type='exam' (not 'surveys').
  static ExamMapping? fromDoc(Map<String, dynamic> doc) {
    final id = JsonUtils.getString('_id', doc);
    final type = JsonUtils.getString('type', doc);
    // Exams have type='exam', surveys have type='surveys'
    if (id.isEmpty || id.startsWith('_design/') || type != 'exam') {
      return null;
    }
    final rawQuestions = doc['questions'];
    final questions = <ExamQuestionsCompanion>[];
    if (rawQuestions is List) {
      for (var index = 0; index < rawQuestions.length; index++) {
        final question = rawQuestions[index];
        if (question is! Map<String, dynamic>) continue;
        final questionId = JsonUtils.getString('id', question);
        final questionIdValue = questionId.isEmpty ? '${id}_$index' : questionId;
        questions.add(
          ExamQuestionsCompanion.insert(
            id: questionIdValue,
            examId: id,
            header: Value(JsonUtils.getStringOrNull('header', question)),
            body: Value(JsonUtils.getStringOrNull('body', question)),
            type: Value(JsonUtils.getStringOrNull('type', question)),
            choices: Value(JsonUtils.getStringList('choices', question)),
            correctChoices:
                Value(_parseCorrectChoices(question['choices'], question)),
            marks: Value(JsonUtils.getStringOrNull('marks', question)),
            hasOtherOption: Value(JsonUtils.getBool('hasOtherOption', question)),
            scaleMax: Value(JsonUtils.getInt('scaleMax', question).let((v) => v <= 0 ? 9 : v)),
            position: index,
          ),
        );
      }
    }
    return ExamMapping(
      exam: ExamsCompanion.insert(
        id: id,
        rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
        stepId: Value(JsonUtils.getStringOrNull('stepId', doc)),
        courseId: Value(JsonUtils.getStringOrNull('courseId', doc)),
        name: Value(JsonUtils.getStringOrNull('name', doc)),
        description: Value(JsonUtils.getStringOrNull('description', doc)),
        createdDate: Value(JsonUtils.getLong('createdDate', doc)),
        updatedDate: Value(JsonUtils.getLong('updatedDate', doc)),
        adoptionDate: Value(JsonUtils.getLong('adoptionDate', doc)),
        createdBy: Value(JsonUtils.getStringOrNull('createdBy', doc)),
        totalMarks: Value(JsonUtils.getInt('totalMarks', doc)),
        passingPercentage: Value(JsonUtils.getStringOrNull('passingPercentage', doc)),
        sourcePlanet: Value(JsonUtils.getStringOrNull('sourcePlanet', doc)),
        isFromNation: Value(JsonUtils.getBool('isFromNation', doc)),
        teamId: Value(JsonUtils.getStringOrNull('teamId', doc)),
        teamShareAllowed: Value(JsonUtils.getBool('teamShareAllowed', doc)),
        sourceSurveyId: Value(JsonUtils.getStringOrNull('sourceSurveyId', doc)),
        noOfQuestions: Value(questions.length),
      ),
      questions: questions,
    );
  }

  /// Parse correct choices from the question's choices array.
  /// The correctChoice is a list of IDs that match choice IDs.
  static List<String> _parseCorrectChoices(
    Object? choicesObj,
    Map<String, dynamic> question,
  ) {
    final correctChoice = question['correctChoice'];
    if (correctChoice == null) return [];

    final List<String> result = [];

    // If correctChoice is an array of IDs
    if (correctChoice is List) {
      for (var i = 0; i < correctChoice.length; i++) {
        final id = correctChoice[i]?.toString()?.toLowerCase() ?? '';
        if (id.isNotEmpty) result.add(id);
      }
      return result;
    }

    // If correctChoice is a single string ID
    if (correctChoice is String && correctChoice.isNotEmpty) {
      // Try to find the matching choice in the choices array
      if (choicesObj is List) {
        for (var i = 0; i < choicesObj.length; i++) {
          final choice = choicesObj[i];
          if (choice is Map<String, dynamic>) {
            final choiceId = JsonUtils.getString('id', choice);
            if (choiceId == correctChoice) {
              final res = JsonUtils.getString('text', choice);
              if (res.isNotEmpty) result.add(res.toLowerCase());
            }
          }
        }
      }
      if (result.isEmpty) {
        result.add(correctChoice.toLowerCase());
      }
    }

    return result;
  }
}

class ExamMapping {
  const ExamMapping({required this.exam, required this.questions});
  final ExamsCompanion exam;
  final List<ExamQuestionsCompanion> questions;
}

extension _IntExt on int {
  int let(int Function(int) fn) => fn(this);
}
