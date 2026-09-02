import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';
import 'converters.dart';

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
        // `$examId-$index`, matching `ExamQuestion.insertExamQuestions`.
        final questionIdValue = questionId.isEmpty ? '$id-$index' : questionId;
        final choices = ExamChoice.listFromJson(question['choices']);
        questions.add(
          ExamQuestionsCompanion.insert(
            id: questionIdValue,
            examId: id,
            // The Kotlin reads `title` here, not `header`; a document has no
            // `header` key, so reading one left every question unlabelled.
            header: Value(JsonUtils.getStringOrNull('title', question)),
            body: Value(JsonUtils.getStringOrNull('body', question)),
            type: Value(JsonUtils.getStringOrNull('type', question)),
            choices: Value(choices),
            correctChoices: Value(_parseCorrectChoices(choices, question)),
            marks: Value(JsonUtils.getStringOrNull('marks', question)),
            hasOtherOption: Value(
              JsonUtils.getBool('hasOtherOption', question),
            ),
            scaleMax: Value(
              JsonUtils.getInt('scaleMax', question).let((v) => v <= 0 ? 9 : v),
            ),
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
        passingPercentage: Value(
          JsonUtils.getStringOrNull('passingPercentage', doc),
        ),
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

  /// The lowercased choice **ids** that count as correct.
  ///
  /// `correctChoice` is either a list of ids (multi-select) or a single id
  /// (single-select). Kotlin's single-id branch stores the choice's `"res"`
  /// field, but the choice objects carry the display label under `"text"` —
  /// `ExamTakingFragment.addCompoundButton` reads `getString("text", choice)`
  /// — so that lookup returns `""` and the question becomes ungradeable.
  /// Recording the id in both branches keeps grading consistent with what an
  /// answer actually stores: the id of the chosen option.
  static List<String> _parseCorrectChoices(
    List<ExamChoice> choices,
    Map<String, dynamic> question,
  ) {
    final correctChoice = question['correctChoice'];
    if (correctChoice == null) return const [];

    if (correctChoice is List) {
      return correctChoice
          .map((entry) => _correctChoiceId(entry, choices))
          .where((id) => id.isNotEmpty)
          .toList(growable: false);
    }

    final id = _correctChoiceId(correctChoice, choices);
    return id.isEmpty ? const [] : [id];
  }

  /// An entry of `correctChoice` is usually the id, but some documents inline
  /// the whole `{id, text}` object. Both reduce to the id.
  static String _correctChoiceId(Object? entry, List<ExamChoice> choices) {
    if (entry is Map) {
      final parsed = ExamChoice.fromJson(entry);
      if (parsed != null) return parsed.id.toLowerCase();
    }
    final raw = entry?.toString() ?? '';
    if (raw.isEmpty) return '';
    // Tolerate a document that names the correct answer by its label.
    for (final choice in choices) {
      if (choice.id == raw) return choice.id.toLowerCase();
      if (choice.text == raw) return choice.id.toLowerCase();
    }
    return raw.toLowerCase();
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
