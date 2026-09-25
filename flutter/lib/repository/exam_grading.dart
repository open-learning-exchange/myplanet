import '../data/local/app_database.dart';

/// Marking rules for a graded exam — the port of `ExamAnswerUtils
/// .checkCorrectAnswer` and its three helpers.
///
/// This lives outside the widget because it is the part that has to be right.
/// Under Kotlin's exam model the verdict is not a score at the end: it is the
/// gate on every question. `saveExamAnswer` returns it, and `btnNext` /
/// `btnSubmit` refuse to advance when it is false
/// (`ExamTakingFragment.kt:196-204`, `:641-645`). So a rule that answers
/// "false" where Kotlin's author meant "true" does not cost a mark — it leaves
/// the learner on question one with no way forward.
class ExamGrading {
  const ExamGrading._();

  /// The verdict for one answer, dispatched on the question type the way
  /// `checkCorrectAnswer` dispatches (`ExamAnswerUtils.kt:54-68`).
  ///
  /// Kotlin reads `select`/`selectMultiple` and sends everything else —
  /// `input`, `textarea`, `ratingScale`, and any unrecognised type — through
  /// `checkTextAnswer`. The extra clause here is for a question whose document
  /// names no type but does offer choices: `take_exam_screen` renders a text
  /// field for an unknown type, so it cannot produce a selection, but
  /// [AnswerShape] treats choices-plus-a-pick as a choice question and it
  /// would be incoherent to store an answer as a choice and grade it as text.
  static bool isCorrect({
    required ExamQuestionRow question,
    List<String> choiceIds = const [],
    String? text,
  }) {
    final type = question.type?.toLowerCase();
    if (type == 'select' || type == 'selectmultiple' || choiceIds.isNotEmpty) {
      return isSelectionCorrect(question, choiceIds);
    }
    return isTextCorrect(question, text ?? '');
  }

  /// Grades a choice question.
  ///
  /// A selection and `correctChoices` are both choice **ids**, so this is a set
  /// comparison. `selectMultiple` requires the exact set — a subset does not
  /// pass, matching `checkMultipleSelectAnswer`'s sorted-list equality.
  ///
  /// The port normalises the answer key to choice **ids** where Kotlin
  /// normalises it to display **text**, and both are self-consistent.
  /// `CoursesRepositoryImpl.extractCorrectChoices` — the parser a course
  /// exam's questions actually go through, `CoursesRepositoryImpl.kt:804-821`
  /// — resolves each `correctChoice` entry to
  /// `ExamAnswerUtils.choiceDisplayValue(matched)`, so `correctChoiceList`
  /// holds labels; `saveExamAnswer` then passes
  /// `ansForCheck = getChoiceTextById(question, ans)` into `checkSelectAnswer`
  /// and compares label with label, and `listAnsForCheck = listAns.keys
  /// .associateWith { it }` does the same for `selectMultiple`. Kotlin's
  /// grading works; the two representations are mirror images.
  ///
  /// `ExamMapper._parseCorrectChoices` reduces the same entries to ids
  /// instead, so this compares ids. That is the more robust half of the
  /// mirror — an id is unique where two choices can share a label, which
  /// Kotlin's comparison silently collapses — and it is what the port has
  /// stored since Phase 106. What matters is that the mapper and this
  /// function agree: porting `checkCorrectAnswer` literally on top of an
  /// id-storing mapper would compare a label with an id and pass nothing,
  /// which under the gate means an exam nobody can finish.
  ///
  /// (`ExamQuestion.insertCorrectChoice` — the *other* Kotlin writer, on the
  /// `exams` walk — really does produce garbage for a `{id, text}` choice: it
  /// matches on `id` and then stores the matched choice's `res`, which such a
  /// document does not have, so the list is `[""]`. That walk is not how a
  /// course exam arrives, and the two writers racing over the same
  /// `exam_questions.id` is a Kotlin defect the port has no counterpart to.)
  static bool isSelectionCorrect(
    ExamQuestionRow question,
    List<String> choiceIds,
  ) {
    final correct = question.correctChoices
        .map((id) => id.toLowerCase())
        .toSet();
    if (correct.isEmpty) return _unkeyed(choiceIds.isNotEmpty);
    final chosen = choiceIds.map((id) => id.toLowerCase()).toSet();
    return chosen.length == correct.length && chosen.containsAll(correct);
  }

  /// Grades a text or rating answer against the recorded correct values.
  ///
  /// Containment, not equality: `checkTextAnswer` is
  /// `correctChoices.any { normalizedAns.contains(it.lowercase(locale)) }`
  /// (`ExamAnswerUtils.kt:88-95`), so "the expected word is here" passes a key
  /// of "expected word" — its own test asserts exactly that
  /// (`ExamAnswerUtilsTest.testCheckCorrectAnswer_InputText`). The port had
  /// exact equality, which under the gate would refuse a right answer that
  /// carried a stray word.
  static bool isTextCorrect(ExamQuestionRow question, String value) {
    final correct = question.correctChoices
        .map((id) => id.toLowerCase())
        .toList();
    final trimmed = value.trim().toLowerCase();
    if (correct.isEmpty) return _unkeyed(trimmed.isNotEmpty);
    if (trimmed.isEmpty) return false;
    return correct.any(trimmed.contains);
  }

  /// A question whose document carried no answer key.
  ///
  /// Kotlin fails it. `extractCorrectChoices` returns `emptyList()` when
  /// `correctChoice` is absent or blank, and every one of the three check
  /// helpers is then vacuously false — `checkTextAnswer` is
  /// `correctChoices.any { ... }` over an empty list, and the `null` guard
  /// above it never fires. Combined with the gate that traps the learner:
  /// every `ratingScale` question in an exam has no key (Planet's rating
  /// questions carry no `correctChoice`), and so does any `input` or
  /// `textarea` question whose author did not supply one. There is no answer
  /// that satisfies such a question and no way out of the exam but to abandon
  /// it.
  ///
  /// The port answers "any answer will do". This is the only deliberate
  /// divergence in this file that is not a correction: the gate presupposes
  /// that a right answer exists, and when the document names none the honest
  /// reading is that there is nothing to get wrong. An *unanswered* question
  /// still fails, so the gate keeps its other job of making the learner
  /// respond.
  static bool _unkeyed(bool answered) => answered;
}
