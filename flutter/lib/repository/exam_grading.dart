import '../data/local/app_database.dart';

/// Marking rules for a graded exam, ported from `ExamTakingFragment`'s
/// answer checks.
///
/// This lives outside the widget because it is the part that has to be right:
/// an exam is scored on the device and the verdict is what gets uploaded.
class ExamGrading {
  const ExamGrading._();

  /// Grades a choice question.
  ///
  /// A selection and `correctChoices` are both choice **ids**, so this is a set
  /// comparison. `selectMultiple` requires the exact set — a subset does not
  /// pass, matching the Kotlin's all-or-nothing marking.
  static bool isSelectionCorrect(
    ExamQuestionRow question,
    List<String> choiceIds,
  ) {
    final correct =
        question.correctChoices.map((id) => id.toLowerCase()).toSet();
    // A question whose document carried no answer key cannot be passed;
    // treating "no key" as "anything goes" would score blank exams full marks.
    if (correct.isEmpty) return false;
    final chosen = choiceIds.map((id) => id.toLowerCase()).toSet();
    return chosen.length == correct.length && chosen.containsAll(correct);
  }

  /// Grades a text or rating answer against the recorded correct values.
  static bool isTextCorrect(ExamQuestionRow question, String value) {
    final trimmed = value.trim().toLowerCase();
    if (trimmed.isEmpty) return false;
    return question.correctChoices
        .map((id) => id.toLowerCase())
        .contains(trimmed);
  }
}
