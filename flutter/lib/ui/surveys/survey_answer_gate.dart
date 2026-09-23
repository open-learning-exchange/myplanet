import '../../data/local/app_database.dart';
import '../../data/local/converters.dart';

/// The rules both survey forms answer to, held in one place because they were
/// held in two and disagreed.
///
/// `ui/exam/ExamTakingFragment.kt` is a single fragment serving exams *and*
/// surveys — its `type` only picks the grading and the status
/// (`:200`, `:642`) — so everything below is one Kotlin implementation, and
/// the port having two screens (`TakeSurveyScreen`, `PublicSurveyScreen`) is
/// the port's own arrangement. Two copies of a rule is how they drift, which
/// is the Phase 78 `normalizeText` lesson; these are the two that had.

/// Whether [question] counts as answered, the port of
/// `ExamTakingFragment.isQuestionAnswered` (`:289`).
///
/// **Kotlin has no notion of an optional question.** `isQuestionAnswered`
/// branches only on the question's `type` and on the `hasOtherOption` case;
/// there is no `required` test in it, and `model/ExamQuestion.kt` carries no
/// such field — `insertExamQuestions` never reads a `"required"` key off the
/// document, so nothing in the Kotlin app could honour one if Planet sent it.
/// Every question must be answered, survey or exam: `:284` hides `btnNext`
/// while the current one is unanswered, and Submit toasts
/// *please select/write your answer to continue* and returns (`:635-637`).
///
/// `TakeSurveyScreen` used to gate on `question.required &&` instead. The port
/// *does* have that column — `SurveyQuestions.required`
/// (`tables.dart:707`) defaults false and `survey_mapper.dart:51` fills it
/// from `JsonUtils.getBool('required', question)`, which is false for a
/// document with no such key — so the conjunct was false for every question of
/// a survey Planet does not mark up, `missing` was false whatever the
/// respondent had typed, and an untouched sheet went to the server as a row of
/// empty strings. It cost nothing locally and uploaded garbage to Planet,
/// where Kotlin structurally cannot. Its sibling had already dropped the
/// conjunct for exactly this reason; the disagreement between the two was the
/// finding.
///
/// [text] is the typed answer and [selected] the picked choices. The branch is
/// on the question rather than on both inputs at once: a question that offers
/// choices has no text field on either card, so `text.isEmpty && choices
/// .isEmpty` would agree with this today and stop agreeing the moment one
/// grew one.
///
/// **Two deliberate tightenings, recorded because an undocumented improvement
/// is a finding.** Both predate this function — the public screen already had
/// them — and neither is a port of a Kotlin line:
///
/// * **`.trim()`.** Kotlin's `input`/`textarea` arm is
///   `binding.etAnswer.text.toString().isNotEmpty()` — `isNotEmpty`, never
///   `isNotBlank` — so a single space is an accepted answer there. Here it is
///   not. Uploading a space is uploading nothing with extra steps.
/// * **Branching on the question rather than on `type`.** Kotlin's `when`
///   matches the type string **exactly** (`"select"`, `"selectMultiple"`,
///   `"input"`, `"textarea"`, `"ratingScale"`, `else -> false`) while
///   everything that *renders* the same question folds case
///   (`startExam` uses `equals(…, ignoreCase = true)`). So a document spelling
///   the type `selectmultiple` draws checkboxes in the Kotlin app and is then
///   permanently unanswerable: Next stays hidden and Submit toasts for ever.
///   The same is true of any type Planet adds later. Branching on whether the
///   question offers choices has no such dead end, and the port's cards make
///   the same choice when they render.
bool surveyQuestionAnswered(
  SurveyQuestionRow question, {
  required String text,
  required Set<ExamChoice> selected,
}) => question.choices.isEmpty ? text.trim().isNotEmpty : selected.isNotEmpty;

/// True when any of [questions] is still unanswered — what both forms check
/// before building a payload.
///
/// Vacuously false for an empty list, which is safe only because neither
/// screen reaches its Submit button with no questions: both render
/// `surveyHasNoQuestions` instead. That is `ExamTakingFragment`'s own
/// behaviour (it hides the form and labels the counter `no_questions`) and it
/// is load-bearing here — a Submit button on an empty page would pass this
/// gate and post a sheet with no answers in it.
bool surveyHasUnansweredQuestion(
  Iterable<SurveyQuestionRow> questions, {
  required String Function(SurveyQuestionRow question) textFor,
  required Set<ExamChoice> Function(SurveyQuestionRow question) selectedFor,
}) => questions.any(
  (question) => !surveyQuestionAnswered(
    question,
    text: textFor(question),
    selected: selectedFor(question),
  ),
);

/// `ExamTakingFragment.startExam` compares the question type with
/// `equals("selectMultiple", ignoreCase = true)`, and so does
/// `SurveysRepository._buildPublicAnswers`. Matching case-sensitively drew
/// radio buttons for a document spelling it `selectmultiple`, so the
/// respondent could pick exactly one of several intended answers — fixed once
/// in each screen, in different rounds, which is why it lives here now.
bool isSelectMultiple(String? type) => type?.toLowerCase() == 'selectmultiple';
