import 'package:drift/drift.dart';

import '../../core/utils/json_utils.dart';
import 'app_database.dart';
import 'converters.dart';

/// Port of `StepExam.insertCourseStepsExams` / `ExamQuestion.insertExamQuestions`
/// (the `exams` database walk) and of `CoursesRepositoryImpl.collectRoomExam`
/// (the `courses` walk's embedded step tests).
class ExamMapper {
  const ExamMapper._();

  /// Parses one document of the `exams` database.
  ///
  /// **The port splits into two tables what the Kotlin keeps in one.** Kotlin's
  /// `bulkInsertExamsFromSync` runs `StepExam.insertCourseStepsExams` over
  /// *every* document of that database and never filters on `type`; the split
  /// between a survey and a test happens later, at query time
  /// (`ExamDao.getByType("surveys")`, `getByStepIdAndType(stepId, "courses")`).
  /// The port has no `type` column, so the split has to happen here instead —
  /// and the rule is therefore `type == 'surveys'` goes to [SurveyMapper],
  /// **everything else is an exam**.
  ///
  /// It used to require `type == 'exam'`, which is the value
  /// `insertCourseStepsExams` only ever uses as a *fallback* for a document
  /// with no `type` key at all. No Kotlin query anywhere selects the exams
  /// table on `type = "exam"`; a course test carries `type: "courses"`
  /// (`CoursesRepositoryImpl.kt:196`, `:530`), so every real one was dropped on
  /// the floor here.
  static ExamMapping? fromDoc(Map<String, dynamic> doc) {
    final id = JsonUtils.getString('_id', doc);
    final type = JsonUtils.getString('type', doc);
    if (id.isEmpty || id.startsWith('_design/') || type == 'surveys') {
      return null;
    }
    final questions = parseQuestions(id, doc['questions']);
    return ExamMapping(
      exam: ExamsCompanion.insert(
        id: id,
        rev: Value(JsonUtils.getStringOrNull('_rev', doc)),
        // **Absent, not `Value(null)`, when the document does not carry them.**
        // This walk knows nothing about course steps: `insertCourseStepsExams`
        // is called with `("", "", doc, "")` from `bulkInsertExamsFromSync`
        // (`SurveysRepositoryImpl.kt:387`) and `checkIdsAndInsert` skips a
        // blank id, so the Kotlin leaves both null — and because `@Upsert` is a
        // full-row replace, whichever of the two walks lands last decides
        // whether a course test still knows its step. Phase 110 recorded that
        // race as a Kotlin defect. Writing the columns absent means a re-pull
        // of the standalone document cannot wipe the join the `courses` walk
        // owns; same shape as the Phase 56 security-data fix.
        stepId: _presentOrAbsent('stepId', doc),
        courseId: _presentOrAbsent('courseId', doc),
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

  /// Port of `ExamQuestion.insertExamQuestions` and of the question half of
  /// `CoursesRepositoryImpl.collectRoomExam` — one parser, because the port
  /// stores one representation.
  ///
  /// The two Kotlin parsers differ in two places, and only the first is
  /// portable. `collectRoomExam` falls back to the question's `title` when
  /// `body` is blank ([bodyFallsBackToTitle]); `insertExamQuestions` does not.
  /// They also disagree about the answer key — `collectRoomExam` resolves it to
  /// display **text** and `insertExamQuestions` to a choice's `res` — and the
  /// port deliberately normalises both to **ids**, which is what
  /// [ExamGrading] compares. See [_parseCorrectChoices].
  static List<ExamQuestionsCompanion> parseQuestions(
    String examId,
    Object? rawQuestions, {
    bool bodyFallsBackToTitle = false,
  }) {
    final questions = <ExamQuestionsCompanion>[];
    if (rawQuestions is! List) return questions;
    for (var index = 0; index < rawQuestions.length; index++) {
      final question = rawQuestions[index];
      if (question is! Map<String, dynamic>) continue;
      final questionId = JsonUtils.getString('id', question);
      // `$examId-$index`, matching `ExamQuestion.insertExamQuestions`.
      final questionIdValue = questionId.isEmpty
          ? '$examId-$index'
          : questionId;
      final choices = ExamChoice.listFromJson(question['choices']);
      // The Kotlin reads `title` here, not `header`; a document has no
      // `header` key, so reading one left every question unlabelled.
      final title = JsonUtils.getStringOrNull('title', question);
      final body = JsonUtils.getStringOrNull('body', question);
      questions.add(
        ExamQuestionsCompanion.insert(
          id: questionIdValue,
          examId: examId,
          header: Value(title),
          body: Value(
            bodyFallsBackToTitle && (body == null || body.isEmpty)
                ? title
                : body,
          ),
          type: Value(JsonUtils.getStringOrNull('type', question)),
          choices: Value(choices),
          correctChoices: Value(_parseCorrectChoices(choices, question)),
          marks: Value(JsonUtils.getStringOrNull('marks', question)),
          hasOtherOption: Value(JsonUtils.getBool('hasOtherOption', question)),
          scaleMax: Value(
            JsonUtils.getInt('scaleMax', question).let((v) => v <= 0 ? 9 : v),
          ),
          position: index,
        ),
      );
    }
    return questions;
  }

  /// Port of `CoursesRepositoryImpl.collectRoomExam(stepJson, "exam", …)` —
  /// the tests embedded in a course document under `steps[i].exam`.
  ///
  /// **This is how a course test actually reaches the exams table.** The
  /// `exams` database walk never learns which step a test belongs to; the
  /// `courses` walk does, because it mints the step's own id and hands it
  /// straight to the exam (`CoursesRepositoryImpl.kt:679`, `:745`). The port
  /// had no counterpart at all, so nothing ever wrote an `exams.stepId` equal
  /// to a `course_steps.id` and both entries into the exam screen were dead.
  ///
  /// [stepIdFor] is [CourseMapper.stepIdFor], passed in rather than imported so
  /// the two mappers stay independent.
  static List<ExamMapping> fromCourseDoc(
    Map<String, dynamic> doc, {
    required String Function(String courseId, int stepIndex) stepIdFor,
  }) => mapStepExams(
    doc,
    stepIdFor: stepIdFor,
    examKey: 'exam',
    // Kotlin files an embedded document by its own `type` when it has one
    // (`type = if (examJson.has("type")) … else examKey`), so a `steps[i].exam`
    // that declares itself a survey is a survey there too and
    // `getByStepIdAndType(stepId, "courses")` would not return it.
    accept: (examJson) => JsonUtils.getString('type', examJson) != 'surveys',
    build: (examId, stepId, courseId, examJson) => ExamMapping(
      exam: ExamsCompanion.insert(
        id: examId,
        rev: Value(JsonUtils.getStringOrNull('_rev', examJson)),
        stepId: Value(stepId),
        courseId: Value(courseId),
        name: Value(JsonUtils.getStringOrNull('name', examJson)),
        description: Value(JsonUtils.getStringOrNull('description', examJson)),
        createdDate: Value(JsonUtils.getLong('createdDate', examJson)),
        updatedDate: Value(JsonUtils.getLong('updatedDate', examJson)),
        adoptionDate: Value(JsonUtils.getLong('adoptionDate', examJson)),
        createdBy: Value(JsonUtils.getStringOrNull('createdBy', examJson)),
        totalMarks: Value(JsonUtils.getInt('totalMarks', examJson)),
        passingPercentage: Value(
          JsonUtils.getStringOrNull('passingPercentage', examJson),
        ),
        sourcePlanet: Value(
          JsonUtils.getStringOrNull('sourcePlanet', examJson),
        ),
        teamId: Value(JsonUtils.getStringOrNull('teamId', examJson)),
        teamShareAllowed: Value(
          JsonUtils.getBool('teamShareAllowed', examJson),
        ),
        sourceSurveyId: Value(
          JsonUtils.getStringOrNull('sourceSurveyId', examJson),
        ),
        noOfQuestions: Value(
          examJson['questions'] is List
              ? (examJson['questions']! as List).length
              : 0,
        ),
      ),
      questions: parseQuestions(
        examId,
        examJson['questions'],
        bodyFallsBackToTitle: true,
      ),
    ),
  );

  /// The shared `steps[i].<examKey>` walk, used by [fromCourseDoc] and by
  /// `SurveyMapper.fromCourseDoc` — the Kotlin runs one `collectRoomExam` for
  /// each of `"exam"` and `"survey"` and puts both in the same table, which the
  /// port has to split across its two.
  static List<T> mapStepExams<T>(
    Map<String, dynamic> doc, {
    required String Function(String courseId, int stepIndex) stepIdFor,
    required String examKey,
    bool Function(Map<String, dynamic> examJson)? accept,
    required T Function(
      String examId,
      String stepId,
      String courseId,
      Map<String, dynamic> examJson,
    )
    build,
  }) {
    final courseId = JsonUtils.getString('_id', doc);
    if (courseId.isEmpty || courseId.startsWith('_design')) return const [];
    final rawSteps = doc['steps'];
    if (rawSteps is! List) return const [];

    final mapped = <T>[];
    for (var index = 0; index < rawSteps.length; index++) {
      final step = rawSteps[index];
      if (step is! Map<String, dynamic>) continue;
      final examJson = step[examKey];
      if (examJson is! Map<String, dynamic>) continue;
      if (accept != null && !accept(examJson)) continue;
      final stepId = stepIdFor(courseId, index);
      // `getString("_id", examJson).ifBlank { "$courseId-$stepId-$examKey" }`.
      final docId = JsonUtils.getString('_id', examJson);
      final examId = docId.isEmpty ? '$courseId-$stepId-$examKey' : docId;
      mapped.add(build(examId, stepId, courseId, examJson));
    }
    return mapped;
  }

  /// `Value(doc[key])` when the document carries the key, `Value.absent()` when
  /// it does not — so an upsert leaves a column another writer owns alone.
  static Value<String?> _presentOrAbsent(
    String key,
    Map<String, dynamic> doc,
  ) => doc.containsKey(key)
      ? Value(JsonUtils.getStringOrNull(key, doc))
      : const Value.absent();
}

class ExamMapping {
  const ExamMapping({required this.exam, required this.questions});
  final ExamsCompanion exam;
  final List<ExamQuestionsCompanion> questions;
}

extension _IntExt on int {
  int let(int Function(int) fn) => fn(this);
}
