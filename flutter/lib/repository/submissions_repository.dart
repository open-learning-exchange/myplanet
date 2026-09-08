import 'package:drift/drift.dart';
import 'package:crypto/crypto.dart';
import 'dart:convert';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/converters.dart';
import 'exam_grading.dart';

/// Offline list portion of `repository/SubmissionsRepositoryImpl.kt`.
class SubmissionsRepository {
  const SubmissionsRepository(
    this._api,
    this._dao,
    this._photosDao,
    this._surveyDao,
    this._examDao, {
    required TeamDao teamDao,
  }) : _teamDao = teamDao;

  static const int initialBatchSize = 100;

  final PlanetApi _api;
  final SubmissionDao _dao;
  final SubmitPhotosDao _photosDao;
  final SurveyDao _surveyDao;

  /// Kotlin's `SubmissionsRepositoryImpl` injects `examDao` and `questionDao`
  /// alongside its own, because [serialize] resolves the **live** exam for the
  /// `parent` object it uploads (`getPayloadData`,
  /// `SubmissionsRepositoryImpl.kt:756-761`). The port splits Kotlin's single
  /// `exams` table into [Exams] and [Surveys], so the lookup needs both this
  /// and `_surveyDao`.
  final ExamDao _examDao;

  /// The team a submission is attributed to, for the `team` object every
  /// upload carries (see [_resolveTeamJson]).
  ///
  /// Kotlin reaches the same row through `Provider<TeamsRepository>`, whose
  /// one use is `getTeamById(teamId)` -> `teamDao.getById(teamId)`
  /// (`TeamsRepositoryImpl.kt:375-378`); the lazy `Provider` wrapper is a Hilt
  /// cycle-breaker, not behaviour, so the port injects the DAO the repository
  /// would have delegated to. `TeamDao.getById` matches it exactly — the
  /// document's own `_id`, never the `teamId` column that memberships,
  /// requests and reports all carry.
  final TeamDao _teamDao;

  Stream<List<SubmissionRow>> watchForUser(String userId) =>
      _dao.watchForUser(userId);

  Stream<SubmissionRow?> watchById(String id) => _dao.watchById(id);
  Stream<List<SubmissionAnswerRow>> watchAnswers(String submissionId) =>
      _dao.watchAnswers(submissionId);
  Stream<List<SubmissionQuestionRow>> watchQuestions(String submissionId) =>
      _dao.watchQuestions(submissionId);
  Future<SubmissionRow?> getById(String id) => _dao.getById(id);
  Future<List<SubmissionAnswerRow>> answersFor(String submissionId) =>
      _dao.answersFor(submissionId);
  Future<int> localCount() => _dao.count();

  Future<String> createDraft({
    required String userId,
    required String type,
    required String title,
    required List<SubmissionDraftAnswer> answers,
    DateTime? now,
  }) async {
    final timestamp = (now ?? DateTime.now()).millisecondsSinceEpoch;
    final id = sha1
        .convert(utf8.encode('$userId:$timestamp:$title'))
        .toString();
    await _dao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: id,
          userId: Value(userId),
          type: Value(type),
          parent: Value(title.trim()),
          startTime: Value(timestamp),
          lastUpdateTime: Value(timestamp),
          status: const Value('pending'),
          uploaded: const Value(false),
          isUpdated: const Value(true),
        ),
      ],
      answers: {
        id: [
          for (var index = 0; index < answers.length; index++)
            SubmissionAnswersCompanion.insert(
              id: '$id:${answers[index].questionId ?? index}',
              submissionId: id,
              questionId: Value(answers[index].questionId),
              value: Value(answers[index].value),
              valueChoices: Value(answers[index].choices),
            ),
        ],
      },
    );
    return id;
  }

  /// Creates the empty answer sheet used when a user starts an offline survey.
  ///
  /// **This is the branch of `createExamSubmission` a team can actually reach**,
  /// and the port splitting one Kotlin writer in two is what hid that. Every
  /// Kotlin site that sets `isTeam = true` also sets `type = "survey"` —
  /// `SubmissionsAdapter.openSurvey` (`:114-121`) for the team's surveys tab
  /// and `PublicSurveyActivity` (`:99-107`) for a deep link — so
  /// `ExamTakingFragment`'s exam branch passes a `teamId` that is always null
  /// and a graded course exam can never carry a team. [teamId] therefore
  /// belongs here as much as on [startExamSession], with the same
  /// blank-guarded, unconditional write.
  ///
  /// **Two callers pass it, one per Kotlin site** (Phase 132, which closed the
  /// gap Phase 129 recorded here): `TeamSurveysScreen` -> the survey route's
  /// `?teamId=` -> `TakeSurveyScreen` -> `SurveysRepository.submitResponse`
  /// -> here, and `public_survey_screen` for the deep link. Every other caller
  /// of this method is team-less in Kotlin too — the individual survey list,
  /// the dashboard's pending-survey prompt and a course step all pass
  /// `isTeam = false` — so a null here is a positive answer, not a hole.
  Future<String> createSurveyDraft({
    required SurveyRow survey,
    required List<SurveyQuestionRow> questions,
    required String userId,
    Map<String, SubmissionDraftAnswer> answers = const {},
    String? teamId,
    DateTime? now,
  }) async {
    final timestamp = (now ?? DateTime.now()).millisecondsSinceEpoch;
    // `teamId?.takeIf { it.isNotBlank() }` (`createExamSubmission:440`).
    final persistedTeamId = (teamId == null || teamId.trim().isEmpty)
        ? null
        : teamId;
    final id = sha1
        .convert(utf8.encode('$userId:$timestamp:${survey.id}'))
        .toString();
    await _dao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: id,
          // `"$surveyId@$courseId"` for a course-attached survey, as
          // `createExamSubmission` writes it — see [examParentId]. Storing the
          // bare id here is what made the mandatory-survey gate unsatisfiable.
          parentId: Value(
            examParentId(examId: survey.id, courseId: survey.courseId),
          ),
          parent: Value(jsonEncode({'_id': survey.id, 'name': survey.name})),
          userId: Value(userId),
          type: const Value('survey'),
          teamId: Value(persistedTeamId),
          startTime: Value(timestamp),
          lastUpdateTime: Value(timestamp),
          status: const Value('complete'),
          uploaded: const Value(false),
          isUpdated: const Value(true),
        ),
      ],
      // A submission question row id is `submissionId:rawQuestionId`, and the
      // exporter recovers `rawQuestionId` by stripping that prefix to look up
      // the answer. A survey question's own row id is already composite
      // (`surveyId:questionId`), so it must be reduced to the same raw id used
      // for the answer's `questionId` — nesting the composite instead yields a
      // key the answer map has no entry for, and every answer exports blank.
      questions: {
        id: [
          for (final question in questions)
            SubmissionQuestionsCompanion.insert(
              id: '$id:${_rawQuestionId(question)}',
              submissionId: id,
              header: Value(question.header),
              body: Value(question.body),
              type: Value(question.type),
              // The labels only, matching `createExamDraft`: this column is a
              // display list (`availableChoices`), and `SubmissionQuestions`
              // is a preserved table whose converter cannot change here.
              choices: Value(
                question.choices.map((choice) => choice.text).toList(),
              ),
              position: question.position,
            ),
        ],
      },
      answers: {
        id: [
          for (final question in questions)
            _surveyAnswer(
              submissionId: id,
              question: question,
              draft: answers[question.id],
            ),
        ],
      },
    );
    return id;
  }

  /// One survey answer row, shaped by [AnswerShape] so it matches what the
  /// exam path writes — Kotlin has a single `saveExamAnswer` for both.
  SubmissionAnswersCompanion _surveyAnswer({
    required String submissionId,
    required SurveyQuestionRow question,
    required SubmissionDraftAnswer? draft,
  }) {
    final shape = AnswerShape.forQuestion(
      type: question.type,
      choices: question.choices,
      selected: (draft?.choices ?? const <String>[])
          .map(ExamChoice.decode)
          .whereType<ExamChoice>(),
      text: draft?.value,
    );
    return SubmissionAnswersCompanion.insert(
      id: '$submissionId:${_rawQuestionId(question)}',
      submissionId: submissionId,
      questionId: Value(_rawQuestionId(question)),
      value: Value(shape.value),
      valueChoices: Value(shape.valueChoices),
    );
  }

  /// Replaces the answer rows on an existing survey submission (resuming a
  /// pending attempt) and marks it complete + locally updated. Unlike
  /// [createSurveyDraft] it does not re-insert the submission or question rows
  /// — those already exist from the original draft.
  Future<void> updateSurveyAnswers({
    required String submissionId,
    required List<SurveyQuestionRow> questions,
    required Map<String, SubmissionDraftAnswer> answers,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await _dao.upsertAll(
      [
        SubmissionsCompanion(
          id: Value(submissionId),
          status: const Value('complete'),
          lastUpdateTime: Value(now),
          isUpdated: const Value(true),
          uploaded: const Value(false),
        ),
      ],
      answers: {
        submissionId: [
          for (final question in questions)
            _surveyAnswer(
              submissionId: submissionId,
              question: question,
              draft: answers[question.id],
            ),
        ],
      },
    );
  }

  /// The server-assigned question id, or the synthetic `surveyId:index` when
  /// the document carried none. Either way it is what the answer is keyed by.
  static String _rawQuestionId(SurveyQuestionRow question) =>
      question.questionId ?? question.id;

  /// Opens an exam attempt, the port of `SubmissionsRepositoryImpl
  /// .startExamSession` + `createExamSubmission` as `ExamTakingFragment
  /// .initializeExamData` calls them for `type == "exam"`.
  ///
  /// Kotlin creates the submission **before** the first question is shown and
  /// writes each answer as the learner passes it ([saveExamAnswer]), because
  /// `mistakes` is an accumulator: it is `(existing?.mistakes ?: 0) + 1` per
  /// wrong attempt, and there is no "existing" to add to unless the row is
  /// already on disk. The port used to grade the whole attempt in widget state
  /// and write it once at the end, which is why `mistakes` was structurally
  /// stuck at its column default.
  ///
  /// The exam branch passes `recreate = true` with `deleteStale`, so a second
  /// entry to the same exam discards the previous local attempt rather than
  /// resuming it — the survey branch is the one that offers resume. The
  /// `pending` status plus `isUpdated: false` is what keeps a half-finished
  /// attempt out of `pendingUploads`; Kotlin gets the same effect from
  /// `getPendingSubmissions`' `WHERE status = 'complete'` filter.
  ///
  /// [teamId] mirrors `createExamSubmission`'s own parameter, which
  /// `ExamTakingFragment` fills from `if (isTeam) teamId else null` on both
  /// its branches (`:124-125` exam, `:151-152` survey). It is stored
  /// **whether or not the team document is on this handset**, which is the
  /// `aca425a` fix. `Submission.teamObject` and `Submission.membershipDoc` are
  /// both `@Ignore`d, so gating the write on a successful lookup left the
  /// attribution nowhere durable except inside the `user` JSON blob the same
  /// block wrote — and that blob is overwritten by the respondent profile
  /// (`UserInformationFragment` -> `SubmissionDao.markComplete`) and dropped
  /// on upload whenever the live `users` row resolves. The column is the only
  /// carrier that survives both. See [_resolveTeamJson].
  ///
  /// **On the exam arm that parameter is dead in Kotlin too, and saying
  /// otherwise was this phase's own mistake.** Every Kotlin site that sets
  /// `isTeam = true` also sets `type = "survey"`
  /// (`SubmissionsAdapter.openSurvey:114-121`, `PublicSurveyActivity:99-107`);
  /// `CourseStepFragment`'s exam launch (`:277-289`) passes no team argument
  /// at all, so `type` keeps `BaseExamFragment`'s `"exam"` default and a
  /// graded course exam can never carry a team. It is kept here because this
  /// method is `createExamSubmission`'s exam arm and dropping it would make
  /// the port's split unable to express its own specification — but the arm a
  /// team reaches is [createSurveyDraft], and that is where the port's gap
  /// actually is.
  ///
  /// **The survey arm now has its callers** (Phase 132: the team surveys tab
  /// and the public-survey deep link, the two sites Kotlin sets `isTeam` at).
  /// **This one still has none, and that is the answer rather than the gap** —
  /// the port's only exam route is `/courses/exam/:examId`, reached from a
  /// course step, where no team context exists or can, exactly as in Kotlin.
  /// So a `null` arriving here is a fact about the journey, not a value
  /// somebody forgot to thread; see `PHASE_132_NOTES.md` for the six Kotlin
  /// journeys and which two carry a team.
  Future<String> startExamSession({
    required ExamRow exam,
    required List<ExamQuestionRow> questions,
    required String userId,
    String? courseId,
    String? teamId,
    DateTime? now,
  }) async {
    final resolvedCourseId = (courseId?.isNotEmpty ?? false)
        ? courseId
        : exam.courseId;
    final parentId = examParentId(examId: exam.id, courseId: resolvedCourseId);
    // Retried three times like `startExamSession`'s own loop
    // (`SubmissionsRepositoryImpl.kt:420-435`, whose comment names transient
    // SQLite constraints during rapid operations). Without it a single failed
    // write ends the exam: the screen has one caller and nothing else opens an
    // attempt.
    Object? lastError;
    for (var attempt = 0; attempt < 3; attempt++) {
      try {
        return await _openExamSession(
          exam: exam,
          questions: questions,
          userId: userId,
          parentId: parentId,
          teamId: teamId,
          now: now,
        );
      } on Exception catch (error) {
        lastError = error;
      }
    }
    throw StateError(
      'Failed to start exam session after 3 attempts: $lastError',
    );
  }

  Future<String> _openExamSession({
    required ExamRow exam,
    required List<ExamQuestionRow> questions,
    required String userId,
    required String parentId,
    String? teamId,
    DateTime? now,
  }) async {
    await _deleteExamSubmissions(parentId: parentId, userId: userId);
    // `teamId?.takeIf { it.isNotBlank() }` (`createExamSubmission:440`), and
    // written to the column **unconditionally** — see [_resolveTeamJson] and
    // the doc comment above for why the team must not depend on the lookup.
    final persistedTeamId = (teamId == null || teamId.trim().isEmpty)
        ? null
        : teamId;
    final timestamp = (now ?? DateTime.now()).millisecondsSinceEpoch;
    final id = sha1
        .convert(utf8.encode('$userId:$timestamp:${exam.id}'))
        .toString();
    await _dao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: id,
          parentId: Value(parentId),
          parent: Value(
            jsonEncode({
              '_id': exam.id,
              '_rev': exam.rev,
              'name': exam.name,
              'courseId': exam.courseId,
              'totalMarks': exam.totalMarks,
            }),
          ),
          userId: Value(userId),
          type: const Value('exam'),
          teamId: Value(persistedTeamId),
          startTime: Value(timestamp),
          lastUpdateTime: Value(timestamp),
          // Deliberately left at 0. `createExamSubmission` never sets a
          // submission-level grade for an exam and neither does
          // `saveExamAnswer`: the attempt goes up as `requires grading` and
          // Planet's grading queue writes the mark, which the sync-in reads
          // back into this column. The port used to compute a percentage here
          // on the device, which under the retry gate would be 100% every
          // time — every answer in a finished attempt is correct by
          // construction.
          status: const Value('pending'),
          uploaded: const Value(false),
          isUpdated: const Value(false),
        ),
      ],
      questions: {
        id: [
          for (final question in questions)
            SubmissionQuestionsCompanion.insert(
              id: '$id:${question.id}',
              submissionId: id,
              header: Value(question.header),
              body: Value(question.body),
              type: Value(question.type),
              // The labels only: this column is a display list
              // (`availableChoices`), and `SubmissionQuestions` is a preserved
              // table whose converter cannot change here.
              choices: Value(
                question.choices.map((choice) => choice.text).toList(),
              ),
              position: question.position,
            ),
        ],
      },
    );
    return id;
  }

  /// `"$examId@$courseId"`, or the bare exam id when the exam belongs to no
  /// course — the shape `createExamSubmission` writes
  /// (`SubmissionsRepositoryImpl.kt:449-456`) and `deleteExamSubmissions`
  /// searches by (`:344-350`).
  ///
  /// **This is the key for a survey as much as for an exam.** Kotlin has one
  /// `exams` table and one writer: `createExamSubmission` is what
  /// `ExamTakingFragment` reaches for whether the step carries an exam or a
  /// survey (`type` is the only difference), and `createBulkSurveySubmissions`
  /// (`:201-206`) resolves `examDao.getById(examId)?.courseId` to build the
  /// same string. `hasSubmission` (`:176-192`) then queries exactly it. The
  /// port had split the two apart: every survey writer stored the bare id
  /// while [hasUnfinishedSurveys] looked for the composite, so a learner who
  /// *had* answered a course-attached survey was still told they had not — and
  /// since that check is what gates finishing `MANDATORY_SURVEY_COURSE_ID`,
  /// the onboarding course could not be completed at all. Phase 125.
  ///
  /// The port used to store the bare `courseId` here, which
  /// `ProgressRepository._examIdFromParent` reads as the exam id — it takes
  /// the leading `@`-delimited segment — so the per-step mistake counts could
  /// never find their exam even once `mistakes` was populated.
  static String examParentId({required String examId, String? courseId}) =>
      (courseId?.isNotEmpty ?? false) ? '$examId@$courseId' : examId;

  /// The exam/survey id inside a `parentId` — Kotlin's
  /// `Submission.examIdFromParentId()`, `parentId?.substringBefore("@")`
  /// (`SubmissionsRepositoryImpl.kt:64-66`).
  ///
  /// Every Kotlin reader that needs to map a submission back to its exam goes
  /// through this rather than comparing the whole column: `getUniquePendingSurveys`
  /// (`:108-121`), `getSurveyTitlesFromSubmissions` (`:129-139`), `getExamMap`
  /// (`:146`) and `ProgressRepositoryImpl.getParentBaseId`. Only two readers in
  /// either tree compare the whole value — `hasSubmission`'s count, which builds
  /// the composite itself, and `SurveysRepositoryImpl.findExistingAdoption`
  /// (`:205-207`), whose rows are written with the bare id on purpose.
  ///
  /// `substringBefore` returns the whole string when the delimiter is absent,
  /// so a bare id passes through unchanged — which is what makes this safe on
  /// rows an older build of the port wrote.
  static String? parentBaseId(String? parentId) {
    if (parentId == null) return null;
    final at = parentId.indexOf('@');
    return at < 0 ? parentId : parentId.substring(0, at);
  }

  /// Records one answer and returns whether it was **correct** — the verdict
  /// `updateAnsDb` hands back to `btnNext`/`btnSubmit`, which is what makes a
  /// wrong answer refuse to advance.
  ///
  /// Port of the exam half of `SubmissionsRepositoryImpl.saveExamAnswer`
  /// (`:491-579`). Every field it writes is Kotlin's:
  ///
  ///  * `mistakes` — `(existing?.mistakes ?: 0) + 1` when this attempt is
  ///    wrong, otherwise the count so far. It accumulates across retries of
  ///    the same question within one attempt, is uploaded by
  ///    [serialize] (`Answer.createObject` sends `"mistakes"`), and is what
  ///    the courses-progress screen totals per step.
  ///  * `isPassed` — the verdict for this attempt. In a *finished* attempt
  ///    every answer is `true`, but as a consequence rather than an
  ///    assertion: the learner cannot leave a question until it is right.
  ///  * `grade` — `1` for every exam answer, right or wrong. It is a
  ///    "worth one mark" marker, not a score, and `createObject` does not
  ///    upload it. See `submission_detail_screen` for why nothing renders it.
  ///  * the submission's `status` — `requires grading` on the final answer of
  ///    an explicit submission, `pending` otherwise. `complete` is the survey
  ///    value; an exam is not complete until somebody marks it.
  Future<bool> saveExamAnswer({
    required String submissionId,
    required ExamQuestionRow question,
    required ExamDraftAnswer answer,
    required bool isFinal,
    required bool isExplicitSubmission,
    DateTime? now,
  }) async {
    final isCorrect = ExamGrading.isCorrect(
      question: question,
      choiceIds: answer.choiceIds,
      text: answer.value,
    );
    final shape = AnswerShape.forQuestion(
      type: question.type,
      choices: question.choices,
      selected: answer.choiceIds.map((id) => ExamChoice(id: id, text: '')),
      text: answer.value,
    );
    final answerId = '$submissionId:${question.id}';
    // `isCorrect` is part of the status condition, which Kotlin's is not:
    // `onClick` assigns `isExplicitSubmission = true`
    // (`ExamTakingFragment.kt:630-632`) *before* it calls `updateAnsDb`, so a
    // **wrong** press of Finish writes `requires grading` for an exam the
    // learner has not finished. Not ported, and the port-specific reason is
    // the strong one: here the status also decides `isUpdated`, and
    // `submissions_screen`'s draft flow sweeps `queuePending` for the whole
    // user, so reproducing it would leak a half-finished attempt onto the wire
    // on any unrelated upload. (Kotlin's own consequence is narrower than it
    // looks — `isStepCompleted` is read from one place,
    // `TakeCourseFragment.changeNextButtonState`, whose body is wrapped in a
    // single hardcoded `courseId` — and it does not self-heal the way a first
    // reading suggests: `btnBack` saves at the still-final index with the
    // sticky flag still set, so it re-writes `requires grading`; only the next
    // save at a non-final index returns it to `pending`.)
    final status = isFinal && isExplicitSubmission && isCorrect
        ? 'requires grading'
        : 'pending';
    // One transaction around the read *and* the write, because `upsertAll`
    // replaces a submission's whole answer set: a read outside it lets two
    // concurrent saves interleave read/read/write/write and revert the first,
    // losing a `mistakes` increment. Drift nests this as a savepoint inside
    // `upsertAll`'s own transaction.
    //
    // Reading the set and rewriting it is itself a workaround: `SubmissionDao`
    // has no way to touch one answer row, and adding one would mean editing
    // `app_database.dart`, which another lane owns this round. The rows are one
    // per question, so the read is small; a follow-up should add
    // `SubmissionDao.upsertAnswer` and drop the rewrite.
    await _dao.transaction(() async {
      final existing = await _dao.answersFor(submissionId);
      final previous = existing.where((row) => row.id == answerId).firstOrNull;
      final companions = [
        for (final row in existing)
          if (row.id != answerId) row.toCompanion(false),
        SubmissionAnswersCompanion.insert(
          id: answerId,
          submissionId: submissionId,
          examId: Value(question.examId),
          questionId: Value(question.id),
          value: Value(shape.value),
          valueChoices: Value(shape.valueChoices),
          mistakes: Value((previous?.mistakes ?? 0) + (isCorrect ? 0 : 1)),
          isPassed: Value(isCorrect),
          grade: const Value(1),
        ),
      ];
      await _dao.upsertAll(
        [
          SubmissionsCompanion(
            id: Value(submissionId),
            status: Value(status),
            lastUpdateTime: Value(
              (now ?? DateTime.now()).millisecondsSinceEpoch,
            ),
            // Kotlin's `updateStatusAndLastUpdate` sets `isUpdated = 1` on
            // every save, and its exam-specific upload config
            // (`UploadConfigs.ExamResults` -> `getPendingExamResults`) has no
            // status filter at all — so a half-finished Kotlin attempt goes up
            // as `pending` on the next sync, and `deleteExamSubmissions` then
            // POSTs a second document for the retake. The port's
            // `pendingUploads` is likewise status-blind (`isUpdated == true`,
            // deliberately: filtering on `status = 'complete'` the way
            // `getPendingSubmissions` does would strand every exam, since an
            // exam is never `complete`), so this flag is the gate instead: an
            // attempt becomes uploadable exactly when it is submitted.
            isUpdated: Value(status == 'requires grading'),
          ),
        ],
        answers: {submissionId: companions},
      );
    });
    return isCorrect;
  }

  /// Port of `SubmissionsRepositoryImpl.deleteExamSubmissions` — every local
  /// attempt at this exam for this user, answers and questions included.
  ///
  /// Kotlin's `deleteByParentAndUser` has no status filter, so an already
  /// uploaded attempt goes too: the server keeps its document and the device
  /// keeps only the attempt in progress.
  ///
  /// The query is built here rather than in `SubmissionDao` because
  /// `app_database.dart` belongs to another lane this round and the DAO has no
  /// delete-by-parent. It should move there — it is the only place in this
  /// repository that reaches for a table directly.
  Future<void> _deleteExamSubmissions({
    required String parentId,
    required String userId,
  }) async {
    final stale = await _dao.getExamSubmissionsByUser(userId);
    final ids = stale
        .where((row) => row.parentId == parentId)
        .map((row) => row.id)
        .toList();
    if (ids.isEmpty) return;
    await _dao.transaction(() async {
      await (_dao.delete(
        _dao.submissionAnswers,
      )..where((row) => row.submissionId.isIn(ids))).go();
      await (_dao.delete(
        _dao.submissionQuestions,
      )..where((row) => row.submissionId.isIn(ids))).go();
      await (_dao.delete(
        _dao.submissions,
      )..where((row) => row.id.isIn(ids))).go();
    });
  }

  /// Attaches the profile collected by `UserInformationFragment` to an attempt.
  Future<void> markSubmissionComplete(
    String id,
    Map<String, dynamic> user,
  ) async {
    await _dao.markComplete(id, jsonEncode(user));
  }

  /// Creates empty pending survey submissions for each selected user.
  /// Port of `SubmissionsRepositoryImpl.createBulkSurveySubmissions`.
  ///
  /// The course id is resolved **once**, before the per-user loop, exactly as
  /// the Kotlin does (`:201-206`: `val courseId = examDao.getById(examId)?.courseId`
  /// then one `parentId` for the whole batch). Passing the bare `surveyId`
  /// through, as this used to, wrote a key [hasUnfinishedSurveys] cannot match.
  Future<void> createBulkSurveySubmissions(
    String surveyId,
    List<String> userIds, {
    DateTime? now,
    String Function()? createId,
  }) async {
    final survey = await _surveyDao.getById(surveyId);
    final parentId = examParentId(examId: surveyId, courseId: survey?.courseId);
    // The existence check below is `latestPendingByUserAndParent`, a comparison
    // of the **whole** `parentId` — so a pending sheet an earlier build stored
    // under the bare id would not be found, and re-sending the survey would
    // give the member a *second* pending sheet. Their dashboard prompt dedupes
    // to the older one, they answer it, and the leftover row re-offers the same
    // survey until they answer it twice. Repairing first is what keeps the
    // writer and its own lookup on one key; pre-Phase-125 they agreed on the
    // bare one, so this gap is the writer fix's to close.
    if (survey != null) await _repairSurveyParentId(survey);
    for (final userId in userIds) {
      await getOrCreateSurveySubmission(
        userId: userId,
        parentId: parentId,
        now: now,
        createId: createId,
      );
    }
  }

  /// Port of `SubmissionsRepositoryImpl.getOrCreateSubmission`.
  ///
  /// [parentId] is taken whole, as Kotlin's is: `getOrCreateSubmission`
  /// (`:624-642`) stores its argument unchanged and leaves building the
  /// `"$examId@$courseId"` shape to the caller. Callers must pass what
  /// [examParentId] returns, not a bare survey id.
  Future<SubmissionRow> getOrCreateSurveySubmission({
    required String userId,
    required String parentId,
    DateTime? now,
    String Function()? createId,
  }) async {
    final existing = await _dao.latestPendingByUserAndParent(userId, parentId);
    if (existing != null) return existing;

    final timestamp = (now ?? DateTime.now()).millisecondsSinceEpoch;
    final id =
        createId?.call() ??
        sha1.convert(utf8.encode('$userId:$timestamp:$parentId')).toString();
    await _dao.upsertAll([
      SubmissionsCompanion.insert(
        id: id,
        userId: Value(userId),
        parentId: Value(parentId),
        type: const Value('survey'),
        startTime: Value(timestamp),
        lastUpdateTime: Value(timestamp),
        status: const Value('pending'),
        uploaded: const Value(false),
        isUpdated: const Value(false),
      ),
    ]);
    return (await _dao.getById(id))!;
  }

  Future<List<SubmissionRow>> submissionsForTeam(String teamId) =>
      _dao.byTeam(teamId);

  Future<List<SubmissionRow>> submissionsForUserWithoutTeam(String userId) =>
      _dao.byUserWithoutTeam(userId);

  /// The team-survey adoption marker. **Its `parentId` is deliberately the
  /// bare survey id** and must stay that way: `createMappedSubmission`
  /// (`SurveysRepositoryImpl.kt:210-241`) writes `parentId = examId` with no
  /// course suffix, and `findExistingAdoption` (`:205-207`) recognises the row
  /// by comparing the whole column to that bare id. Running it through
  /// [examParentId] like the answer-sheet writers would make every adoption
  /// invisible to its own lookup, and re-adopt on every load.
  ///
  /// It follows that a bare-id, `type = 'survey'` row is not *always* a
  /// stale answer sheet, which is why [repairCourseSurveyParentIds] skips
  /// `status = ''`.
  Future<void> createSurveyAdoptionSubmission({
    required String id,
    required String surveyId,
    required String? userId,
    required String parentJson,
    required String userJson,
    required String source,
    required String parentCode,
    String? teamId,
    DateTime? now,
  }) async {
    final timestamp = (now ?? DateTime.now()).millisecondsSinceEpoch;
    await _dao.upsertAll([
      SubmissionsCompanion.insert(
        id: id,
        parentId: Value(surveyId),
        parent: Value(parentJson),
        userId: Value(userId),
        user: Value(userJson),
        type: const Value('survey'),
        status: const Value(''),
        uploaded: const Value(false),
        source: Value(source),
        parentCode: Value(parentCode),
        teamId: Value(teamId),
        startTime: Value(timestamp),
        lastUpdateTime: Value(timestamp),
        isUpdated: const Value(true),
      ),
    ]);
  }

  /// Every row on the handset that still owes the server an upload — see
  /// [SubmissionDao.pendingUploads] for why this is not scoped to the
  /// signed-in learner.
  Future<List<SubmissionRow>> pendingUploads() => _dao.pendingUploads();

  /// Rewrites a course-attached survey's answer sheets from the bare survey id
  /// to `"$surveyId@$courseId"`, and returns how many rows it changed.
  ///
  /// This is the field-data half of the Phase 125 fix. Correcting the writers
  /// leaves every row an earlier build already stored keyed the old way, and
  /// **those rows do not heal on their own**: the port uploads a submission
  /// with whatever `parentId` the row carries, and [upsertDocuments] writes
  /// `parentId` straight back from the document, so a pull re-asserts the bare
  /// id rather than correcting it. Left alone, a learner who has already
  /// answered the mandatory survey would be told to answer it again — which is
  /// the entire bug, survived once more, plus a duplicate answer sheet in the
  /// reporting the survey exists to produce.
  ///
  /// It is **not** a schema migration and needs no `schemaVersion`: no DDL
  /// changes, `parentId` has always been on the table, and a bump would be the
  /// wrong tool anyway — `submissions` is in `localAuthorityTables`, so an
  /// upgrade preserves exactly the rows that need repairing and repairs none
  /// of them.
  ///
  /// Two rows it must not touch, both `type = 'survey'` with a bare id:
  ///
  /// * **the adoption marker**, whose bare id is Kotlin's and is how
  ///   `findExistingAdoption` finds it — see
  ///   [createSurveyAdoptionSubmission];
  /// * a submission for a survey with no `courseId`, whose bare id is already
  ///   what [examParentId] would produce. Reachable through
  ///   [_repairSurveyParentId]'s other caller, which is handed an arbitrary
  ///   survey rather than one selected by `courseId`.
  ///
  /// Idempotent: after the first pass no row matches the bare id any more.
  Future<int> repairCourseSurveyParentIds(String courseId) async {
    if (courseId.isEmpty) return 0;
    var repaired = 0;
    for (final survey in await _surveyDao.getByCourseId(courseId)) {
      repaired += await _repairSurveyParentId(survey);
    }
    return repaired;
  }

  /// One survey's share of [repairCourseSurveyParentIds].
  ///
  /// **The status test is `coalesce(status, '') != ''`, not `status IS NOT ''`,
  /// and the difference was a live defect.** Kotlin's predicate is
  /// `it.status.orEmpty().isEmpty()` (`SurveysRepositoryImpl.kt:203-207`), so
  /// negating it has to treat a null status and an empty one alike. Drift's
  /// `isNotValue` emits SQL `IS NOT` rather than `!=`
  /// (`expression.dart:118-120` → `:148-154`), and `NULL IS NOT ''` is **true**
  /// — so the first cut of this admitted exactly the rows it existed to skip.
  ///
  /// A marker reaches null status by an ordinary route, not a corner case.
  /// [createSurveyAdoptionSubmission] writes `status: ''` **and**
  /// `isUpdated: true`, so the generic `pendingUploads` sweep uploads it;
  /// [serialize] sends `'status': ''`; and [upsertDocuments] reads it back
  /// through `JsonUtils.getStringOrNull`, which maps the empty string to null
  /// (`json_utils.dart:19-22`). Every marker that has round-tripped — and
  /// every marker a Kotlin handset in the same deployment published, since
  /// Kotlin writes them bare on purpose — arrives with `status = NULL`. In a
  /// real mixed fleet that is the repair's *most likely* match, where a bare-id
  /// answer sheet can only come from a pre-Phase-125 build.
  ///
  /// `coalesce` on the status is the same idiom, for the same reason, as
  /// `SubmissionDao.pendingUploads`' coalesced guest operands.
  Future<int> _repairSurveyParentId(SurveyRow survey) async {
    final target = examParentId(examId: survey.id, courseId: survey.courseId);
    // A survey with no `courseId` of its own keys to the bare id already.
    if (target == survey.id) return 0;
    // Built here rather than in `SubmissionDao` for the same reason
    // `_deleteExamSubmissions` is: `app_database.dart` belongs to another
    // lane this round. It should move there.
    return (_dao.update(_dao.submissions)..where(
          (row) =>
              row.parentId.equals(survey.id) &
              row.type.equals('survey') &
              coalesce([row.status, const Constant('')]).equals('').not(),
        ))
        .write(SubmissionsCompanion(parentId: Value(target)));
  }

  /// Port of `SubmissionsRepositoryImpl.hasSubmission`
  /// (`SubmissionsRepositoryImpl.kt:176-192`) — whether [userId] has any
  /// submission of [type] for the exam or survey [stepExamId] within
  /// [courseId].
  ///
  /// Its one Kotlin caller is `CoursesRepositoryImpl.getCourseStepData`
  /// (`:537-546`), which calls it twice — once with `"exam"` and once with
  /// `"survey"` — to produce `CourseStepData.hasExam`/`hasSurvey`. Those two
  /// booleans have exactly one use: `CourseStepFragment.hideTestIfNoQuestion`
  /// (`:241-260`) swaps the step tile's button labels off them, `take_test` →
  /// `retake_test` and `record_survey` → `redo_survey`. So this is the only
  /// **user-visible** reader in either tree whose predicate is the whole
  /// composite `parentId` — and unlike [hasUnfinishedSurveys], whose Kotlin
  /// original is almost certainly dead (see its doc), this one works in the
  /// shipping Android app: `getCourseStepData` reads the *plural*-typed rows
  /// (`getByStepIdAndType(stepId, "surveys")`, `:534`), which is what a survey
  /// document actually carries.
  ///
  /// **Every guard inverts into a label here**, which is the opposite of how
  /// the same guards read at [hasUnfinishedSurveys]' call site, so they are
  /// worth stating one at a time:
  ///
  /// * a blank exam id, course id or user id returns false → the tile says
  ///   *take* / *record*. A guest browsing a joined course therefore never
  ///   sees "Retake", and a step whose `courseId` never synced never does
  ///   either. Kotlin's guard is `isNullOrBlank` on all three (`:182-184`).
  /// * **`questionDao.countByExamId(stepExamId) == 0` returns false**
  ///   (`:186-188`) — an exam whose questions have not synced yet reads as
  ///   *not taken* however many submissions the learner has. Kotlin's comment
  ///   for this is `getCourseStepData`'s own sibling rule (`:97`: a step with
  ///   no exam is passed by reaching it), and here it reads sanely: do not
  ///   claim an attempt for an exam we cannot show. This is the one guard
  ///   [hasUnfinishedSurveys] deliberately does **not** port, because there it
  ///   inverts into "a question-less survey blocks the course forever".
  ///
  /// **The question count is the one place the port cannot copy the Kotlin
  /// literally.** Kotlin keeps every question, exam and survey alike, in one
  /// `exam_questions` table keyed by `examId` (`QuestionDao.kt:13`), because it
  /// keeps every exam and survey in one `exams` table distinguished by `type`.
  /// The port split both pairs — [Exams]/[Surveys] and
  /// [ExamQuestions]/[SurveyQuestions] — so the count has to pick its table
  /// from [type]. **No Kotlin line authorises that branch**: it is a forced
  /// deviation, and copying the Kotlin literally (always [ExamQuestions])
  /// reads as *not taken* on every survey ever answered.
  ///
  /// The discriminator is the **submission**'s type, `'survey'` singular —
  /// what both callers pass and what the `submissions.type` column holds. It
  /// is deliberately not the *exam row's* type, which for a survey document is
  /// `'surveys'` plural; a future caller forwarding a document's own type
  /// would fall through to [ExamQuestions] silently.
  ///
  /// The `parentId` is [examParentId]'s, i.e. Kotlin's
  /// `"$stepExamId@$courseId"` (`:190`) — the composite key Phase 125 made
  /// every port writer agree on, and the reason this label swap could not have
  /// been ported before it.
  Future<bool> hasSubmission({
    required String? stepExamId,
    required String? courseId,
    required String? userId,
    required String type,
  }) async {
    // `isNullOrBlank`, not `isEmpty`: Kotlin's guard rejects a whitespace-only
    // argument too (`:182-184`), and the first cut of this diverged from its
    // own doc comment on exactly that. Binding all three to locals also keeps
    // the `!` out of the count call, so deleting the guard changes the
    // *answer* rather than throwing a null-check error — which is what makes
    // the guard testable.
    final examId = (stepExamId ?? '').trim();
    final course = (courseId ?? '').trim();
    final user = (userId ?? '').trim();
    if (examId.isEmpty || course.isEmpty || user.isEmpty) return false;
    if (await _questionCount(examId, type) == 0) return false;
    final count = await _dao.countByUserParentAndType(
      user,
      examParentId(examId: examId, courseId: course),
      type,
    );
    return count > 0;
  }

  /// `SELECT COUNT(*)`, the shape of Kotlin's `QuestionDao.countByExamId`
  /// (`QuestionDao.kt:13`) — deliberately not `questionsFor(id).length`.
  ///
  /// Fetching the rows in order to count them decodes every question's
  /// `choices` blob through `ExamChoiceListConverter`, whose `jsonDecode` is
  /// unguarded, so one malformed blob would throw out of a **label** read.
  /// [hasSubmission] feeds a single `AsyncValue` covering both step tiles, so
  /// that throw would take the survey tile down with the exam one — a bad row
  /// on one question hiding the buttons. A `COUNT(*)` decodes nothing.
  ///
  /// Built here rather than on the DAOs for the same reason
  /// [_repairSurveyParentId]'s `UPDATE` is: `app_database.dart` belongs to
  /// another lane this round. Both should move there.
  Future<int> _questionCount(String examId, String type) async {
    if (type == 'survey') {
      final table = _surveyDao.surveyQuestions;
      final total = table.id.count();
      final row =
          await (_surveyDao.selectOnly(table)
                ..addColumns([total])
                ..where(table.surveyId.equals(examId)))
              .getSingle();
      return row.read(total) ?? 0;
    }
    final table = _examDao.examQuestions;
    final total = table.id.count();
    final row =
        await (_examDao.selectOnly(table)
              ..addColumns([total])
              ..where(table.examId.equals(examId)))
            .getSingle();
    return row.read(total) ?? 0;
  }

  /// Port of `SubmissionsRepositoryImpl.hasUnfinishedSurveys`. Returns true
  /// if the course has any attached survey the user has not yet submitted.
  ///
  /// Also aliased as `hasPendingSurvey` — the challenge dialog's name for the
  /// same check. The Kotlin has both methods; they are identical.
  ///
  /// [repairCourseSurveyParentIds] runs first. This is the one read in either
  /// tree whose predicate is the whole `parentId`, so it is the one read that
  /// a stale bare-id row can answer wrongly — and it runs at exactly the
  /// moment the answer matters, when the learner taps Finish. Kotlin needs no
  /// such call because it never wrote the bare id.
  ///
  /// **A deliberate deviation, found by the Phase 125 ground-truth audit and
  /// worth knowing before anyone "fixes" this method toward Kotlin.** This gate
  /// is live in the port and almost certainly dead in the shipping Kotlin app.
  /// `getSurveysByCourseId` filters `examDao.getByCourseIdAndType(courseId,
  /// "survey")` — **singular** — while `StepExam.type` is the embedded
  /// document's own `type` (`CoursesRepositoryImpl.kt:744`:
  /// `if (examJson.has("type")) … else examKey`), which for a survey is
  /// `"surveys"`. Every other Kotlin reader uses the plural: the Take Survey
  /// button itself is `getByStepIdAndType(stepId, "surveys")` (`:534`), the
  /// surveys list is `getByType("surveys")`, and `ExamDao`'s defaults are
  /// `"surveys"`. So the two queries are mutually exclusive and at most one of
  /// the button and this block can ever see a given survey: a document carrying
  /// `type: "surveys"` gets a button and never blocks, a document carrying no
  /// `type` blocks forever with no button that could satisfy it.
  ///
  /// The port cannot reproduce that split *on `type`*: `Surveys` has no `type`
  /// column, because `ExamMapper.fromDoc` uses the document's `type` to
  /// *choose the table* and then discards it. Its reader is
  /// `SurveyDao.getByCourseId` with no type filter, so it finds the survey the
  /// button opens — which is the behaviour the Kotlin feature was written to
  /// have. Making the writers agree with it (Phase 125) is therefore internal
  /// consistency, not restored parity, and the composite key is still the right
  /// one to converge on: it is what Kotlin uploads, what Planet joins on, and
  /// what the port's own exam path has always written.
  ///
  /// It can reproduce the *shape* by another route, though, so this is not a
  /// class of bug the port has escaped. The gate reads `courseId` and the
  /// button reads `stepId`, and `SurveyMapper.fromDoc` writes each
  /// independently with `_presentOrAbsent` (`survey_mapper.dart:93-94`) — so a
  /// document carrying `courseId` and no `stepId` lands with a course join and
  /// no step join: found here, offered by no button. Kotlin genuinely cannot do
  /// that, since `StepExam.insertCourseStepsExams` never reads either key from
  /// the document (`StepExam.kt:36-58`) and the exams walk passes
  /// `("", "", doc, "")`.
  ///
  /// **And since Phase 136 the port manufactures that shape itself**, on every
  /// team adoption: `adoptSurvey` copies `courseId` and deliberately not
  /// `stepId`. So this is no longer a hypothetical waiting on Planet's
  /// document shapes — it is the normal state of an adopted clone, and the
  /// skip below is what keeps it out of this reader.
  ///
  /// Two things the audit established that keep this reader safe, both pinned
  /// by tests in `mandatory_survey_round_trip_test.dart`:
  ///
  /// * Kotlin's `createMappedSurvey` copies the source survey's `courseId` and
  ///   `stepId` into an adopted team clone (`SurveysRepositoryImpl.kt:181-182`).
  ///   Since Phase 136 the port copies the `courseId` too — Send has to, since
  ///   `createBulkSurveySubmissions` folds it into every member's key — so
  ///   `getByCourseId` now returns clones and **this loop skips them
  ///   explicitly**. It used to be safe by accident, on the omission.
  ///
  ///   Skipping is not a divergence from a live Kotlin behaviour, and the note
  ///   this replaced implied it was. Kotlin's own gate reaches **no** survey at
  ///   all: `getSurveysByCourseId` (`:367-369`) filters
  ///   `getByCourseIdAndType(courseId, "survey")`, singular, while every
  ///   survey — clone included, since `createMappedSurvey` assigns
  ///   `type = exam.type` — carries `"surveys"`. So there is no Kotlin
  ///   behaviour here to match, only a live port gate to keep satisfiable: a
  ///   learner outside the adopting team has no sheet for that team's copy and
  ///   never can have one, so counting it would make the course unfinishable —
  ///   Phase 125's bug, re-entered from the writer's side.
  ///
  ///   And a `type = "survey"` **clone** cannot exist at all, which an earlier
  ///   draft of this bullet got wrong: the singular value is written only by
  ///   `CoursesRepositoryImpl.kt:746`'s `else examKey`, while every list
  ///   `adoptSurvey` is reachable from is plural — `getAdoptableTeamSurveys`,
  ///   `getTeamOwnedSurveys` and `getIndividualSurveys` are all
  ///   `type = "surveys"` (`ExamDao.kt:29-31`), and `createMappedSurvey`
  ///   copies `type = exam.type`. So Kotlin's gate cannot see a clone under
  ///   *any* document shape. That makes the case for skipping stronger than
  ///   the one this replaced, not weaker — but it is also the sentence a later
  ///   round would reach for to remove the skip, so it is worth stating
  ///   correctly.
  ///
  ///   [repairCourseSurveyParentIds] deliberately does **not** skip them: a
  ///   clone's member sheets are exactly the rows a pre-Phase-136 build keyed
  ///   bare, so the sweep is how they heal. The two loops want opposite things
  ///   from the same query.
  /// * `countByUserParentAndType` is status-blind, as Kotlin's is
  ///   (`SubmissionDao.kt:23`) — a sheet the learner has merely *opened*
  ///   satisfies the gate. That is also why the adoption marker, which is
  ///   `type = 'survey'` with a bare id, must never be repaired into this key.
  ///
  /// **Two further deviations from `hasSubmission`, both deliberate.**
  ///
  /// 1. The blank-user guard below returns `false`. Kotlin has no guard in
  ///    `hasUnfinishedSurveys`; it lives in `hasSubmission` (`:182-184`), which
  ///    returns `false` for a blank `userId` and so inverts to *unfinished* —
  ///    Kotlin **blocks** a null user on a course that has surveys. Reachable
  ///    here through `challenge_provider`, which passes a nullable id. Blocking
  ///    a learner whose id we do not have cannot be satisfied by answering, and
  ///    the Kotlin behaviour is unobservable anyway (its survey list is always
  ///    empty, above). The blank-*course* half is not a divergence at all:
  ///    `getByCourseIdAndType("", …)` matches nothing either.
  /// 2. `questionDao.countByExamId(stepExamId) == 0 → false` (`:186-188`) is
  ///    **not** ported. In `getCourseStepData` that rule reads sanely — do not
  ///    claim a submission exists for an exam whose questions have not synced.
  ///    Here it inverts into "a question-less survey blocks the course
  ///    forever", and because the short-circuit precedes the count, no
  ///    submission can ever satisfy it. Porting it would reintroduce the exact
  ///    user-blocking bug this phase removes, for a survey nobody can answer.
  Future<bool> hasUnfinishedSurveys(String courseId, String? userId) async {
    if (courseId.isEmpty || (userId ?? '').isEmpty) return false;
    await repairCourseSurveyParentIds(courseId);
    final surveys = await _surveyDao.getByCourseId(courseId);
    for (final survey in surveys) {
      // An adopted team clone joins the course as of Phase 136, and the course
      // must not demand it — see this method's doc comment.
      //
      // **Both halves of the predicate are load-bearing**, and the first cut
      // of this had only the first. `sourceSurveyId` is not a local-authorship
      // marker in the port: the courses walk reads it straight off the
      // server's embedded survey (`survey_mapper.dart:163-167`), so a step
      // survey that is itself an adopted copy arrives with `courseId`,
      // `stepId` and `sourceSurveyId` — it has a Take Survey button and a
      // working retake label, and skipping it let a learner finish
      // `MANDATORY_SURVEY_COURSE_ID` having answered nothing.
      //
      // **The second half is `stepId`, not `rev`, and the difference is a
      // user-blocking bug.** Phase 136 wrote `rev == null` from `ExamDao.kt:21`
      // — Kotlin's *upload sweep* — and it was exact only for as long as
      // nothing ever published a clone. Phase 138 publishes it, and the moment
      // `AdoptedSurveysUploader` records the rev this guard stops firing: the
      // clone still carries the source's `courseId`, so `getByCourseId` hands
      // it to every learner on the course, and nobody outside the adopting
      // team has — or can have — a sheet keyed `"<cloneId>@<courseId>"`. On
      // `MANDATORY_SURVEY_COURSE_ID` that is Phase 125's outcome reached by a
      // new route: the course becomes uncompletable for everyone outside
      // whichever team adopted its survey.
      //
      // `stepId` asks the question the guard actually means — *does the
      // courses walk own this row's course join?* A server-authored
      // course-step survey always gets one from `SurveyMapper._build`
      // (positional, never blank); a clone never has one, deliberately, and
      // `releaseStepJoinsForCourse` can only null it *together with*
      // `courseId`, which drops the row out of this query altogether. So the
      // conjunction keeps both properties without depending on publication
      // state, and it is a **prune**-time discriminator that `rev` remains
      // correct for (`SurveyDao.deleteNotIn`) but a **gate**-time one it never
      // was.
      //
      // Kotlin needs no such guard and cannot be read for one:
      // `getSurveysByCourseId` filters `getByCourseIdAndType(courseId,
      // "survey")` — **singular** (`SubmissionsRepositoryImpl.kt:367-379`) —
      // while a clone copies the source's `type`
      // (`SurveysRepositoryImpl.kt:180`) and every list adoption is reachable
      // from queries `type = "surveys"` (`ExamDao.kt:29-31`). A Kotlin clone is
      // never in that result set at all. This is a port-invented guard on
      // port-invented breadth, which is exactly why it must not borrow its
      // predicate from a Kotlin query written for another purpose.
      if (survey.sourceSurveyId != null && survey.stepId == null) continue;
      // Routed through the writers' own derivation so the two cannot drift
      // apart again. `courseId` is non-empty by the guard above and equal to
      // `survey.courseId` by the query that produced the row, so this is
      // `hasSubmission`'s literal `"$stepExamId@$courseId"`.
      final parentId = examParentId(examId: survey.id, courseId: courseId);
      final count = await _dao.countByUserParentAndType(
        userId!,
        parentId,
        'survey',
      );
      if (count == 0) return true;
    }
    return false;
  }

  /// Port of `SubmissionsRepositoryImpl.hasPendingSurvey` — identical to
  /// [hasUnfinishedSurveys]. The challenge dialog calls this name.
  Future<bool> hasPendingSurvey(String courseId, String? userId) =>
      hasUnfinishedSurveys(courseId, userId);

  Future<void> markUploaded(String id, String couchId, String rev) async {
    await _dao.markUploaded(id, couchId, rev);
  }

  /// A public-survey answer sheet that reached the public API. See
  /// `SubmissionDao.markPublicSubmitted` for why no revision is recorded.
  Future<void> markPublicSubmitted(String id) => _dao.markPublicSubmitted(id);

  /// The id [addSubmissionPhoto] files a capture under.
  ///
  /// Public because the JPEG has to be written under *this* id and no other:
  /// `SubmitPhotosUploader._uploadAttachment` reads the bytes back with
  /// `SubmitPhotosFiles.existingFileFor(photoId: <row id>)`, so a caller that
  /// saves them under some other key — the submission id, say — leaves the
  /// lookup to miss and the document to upload without its attachment. A
  /// caller therefore derives the id first, writes the bytes, and passes the
  /// same [capturedAt] back in so the row lands on the same key.
  static String photoIdFor({
    required String submissionId,
    required DateTime capturedAt,
    String? examId,
    String? courseId,
  }) => sha1
      .convert(
        utf8.encode(
          'photo:$submissionId:$examId:$courseId:${capturedAt.millisecondsSinceEpoch}',
        ),
      )
      .toString();

  /// Port of `SubmissionsRepositoryImpl.addSubmissionPhoto`.
  ///
  /// The id is a sha1 of the row's identifying tuple so a re-capture after a
  /// failed drain is idempotent: `insertOnConflictUpdate` re-stamps the same
  /// row rather than cloning it, and `unuploaded` does not double-count it.
  /// The Kotlin source generates a fresh UUID per capture; the device-identity
  /// `uniqueId` Kotlin persists here is layered onto the document at upload
  /// time instead (see [SubmitPhotosUploader]), the way [SubmissionsUploader]
  /// layers telemetry onto a submission.
  Future<String> addSubmissionPhoto({
    required String submissionId,
    String? examId,
    String? courseId,
    String? memberId,
    String? photoLocation,
    DateTime? now,
  }) async {
    final capturedAt = now ?? DateTime.now();
    final id = photoIdFor(
      submissionId: submissionId,
      capturedAt: capturedAt,
      examId: examId,
      courseId: courseId,
    );
    await _photosDao.insert(
      SubmitPhotosTableCompanion.insert(
        id: id,
        submissionId: Value(submissionId),
        courseId: Value(courseId),
        examId: Value(examId),
        memberId: Value(memberId),
        date: Value(capturedAt.toString()),
        photoLocation: Value(photoLocation),
        uploaded: const Value(false),
      ),
    );
    return id;
  }

  /// Rows awaiting their document POST, each paired with the JSON the
  /// uploader sends — the port of `SubmissionsRepositoryImpl.getUnuploadedPhotos`.
  ///
  /// The document is built here rather than in the uploader so the outbox
  /// payload is self-contained: a drainer replay does not need to re-read the
  /// row, and a row edited after the enqueue (there is no edit path yet, but
  /// the contract is what matters) is sent as it was when queued.
  Future<List<({String id, Map<String, dynamic> document})>>
  unuploadedPhotos() async {
    final rows = await _photosDao.unuploaded();
    return rows
        .map((row) => (id: row.id, document: serializePhoto(row)))
        .toList();
  }

  Future<SubmitPhotosRow?> photoById(String id) => _photosDao.getById(id);
  Future<List<SubmitPhotosRow>> photosByIds(Iterable<String> ids) =>
      _photosDao.getByIds(ids);

  Future<int> markPhotoUploaded(String id, String couchId, String rev) =>
      _photosDao.markUploaded(id, couchId, rev);

  /// Port of `SubmitPhotos.serialize`.
  ///
  /// Field-for-field with the Kotlin JSON. The device identity Kotlin writes
  /// as `macAddress` is layered onto the document by [SubmitPhotosUploader],
  /// the way every other uploader layers telemetry onto its doc, so it does
  /// not appear here. The local `photoLocation` is sent so a server-side join
  /// can resolve the bytes — the attachment itself is PUT separately after the
  /// POST lands.
  static Map<String, dynamic> serializePhoto(SubmitPhotosRow row) => {
    'id': row.id,
    'submissionId': row.submissionId,
    'type': 'photo',
    'courseId': row.courseId,
    'examId': row.examId,
    'memberId': row.memberId,
    'date': row.date,
    'photoLocation': row.photoLocation,
  };

  /// Port of `SubmissionsRepositoryImpl.serializeSubmission`.
  ///
  /// `_id`/`_rev` are included whenever the row already exists on the server,
  /// exactly as the Kotlin does. Without them CouchDB treats the POST as a new
  /// document, so re-uploading an existing submission would create a duplicate
  /// and [markUploaded] would then point the local row at the copy, orphaning
  /// the original. `_id`/`_rev` are set by [markUploaded] and by the sync-in,
  /// which is what makes the branch reachable — [upsertDocuments] itself always
  /// writes `isUpdated: false`, because a document that arrived from the server
  /// owes it nothing.
  ///
  /// Device telemetry is added by [SubmissionsUploader], where the platform
  /// seam is available. The `source`/`parentCode` planet identifiers Kotlin
  /// also sends remain part of the community-code parity gap.
  ///
  /// There is deliberately **no top-level `userId`**. Kotlin's
  /// `serializeSubmission` emits none (`SubmissionsRepositoryImpl.kt:820-865`)
  /// and its sync-in derives the owner from `user._id`, so the key the port
  /// used to send was one only the port could read — which is exactly why the
  /// sync-in's matching read of it went unnoticed. See [_userDocument].
  Future<Map<String, dynamic>> serialize(SubmissionRow row) async {
    final answers = await _dao.answersFor(row.id);
    final parent = await _parentDocument(row);
    final team = await _resolveTeamJson(row);
    return {
      if (row.couchId != null && row.couchId!.isNotEmpty) '_id': row.couchId,
      if (row.rev != null && row.rev!.isNotEmpty) '_rev': row.rev,
      // Kotlin's own defaults, `serializeSubmission:827-832`: Planet reads
      // these unconditionally, so a null would arrive as a missing field.
      'parentId': row.parentId ?? '',
      'type': row.type ?? 'survey',
      // Straight after `type`, where both Kotlin serializers add it
      // (`getExamUploadPayload:764`, `serializeSubmission:836`).
      'team': ?team,
      'status': row.status ?? 'pending',
      'startTime': row.startTime,
      'lastUpdateTime': row.lastUpdateTime,
      'grade': row.grade,
      // Omitted rather than sent as null when there is nothing to send —
      // `serializeSubmission` guards both with an `if` and has no `else`
      // (`:840-857`).
      'parent': ?parent,
      'user': ?_userDocument(row),
      'answers': [
        for (final answer in answers)
          {
            if (answer.questionId != null) 'questionId': answer.questionId,
            // `Answer.createObject` sends `value` whenever it is non-empty
            // and only falls back to `valueChoicesArray` when it is not — so
            // a `select` answer reaches Planet as the bare display text and
            // the object array is what a `selectMultiple` (whose `value` is
            // the empty string) sends. The port had this precedence the other
            // way round, which sent an array where Kotlin sends a string.
            'value': (answer.value?.isNotEmpty ?? false)
                ? answer.value
                : _answerChoices(answer.valueChoices),
            'mistakes': answer.mistakes,
            'passed': answer.isPassed,
          },
      ],
    };
  }

  /// The `parent` object the upload carries: the **live** exam or survey when
  /// this device still has it, and only otherwise the blob stored on the row.
  ///
  /// That order is Kotlin's. `serializeSubmission` asks `getPayloadData`
  /// (`SubmissionsRepositoryImpl.kt:756-761`) for
  /// `examDao.getById(parentId.substringBefore("@"))` plus
  /// `questionDao.getByExamId`, and emits
  /// `StepExam.serializeExam(exam, questions)` when the exam is there
  /// (`:840-841`); `submission.parent` is the `else if` for an exam this
  /// device no longer has (`:842`).
  ///
  /// The port used to send the blob unconditionally, and the blob is tiny:
  /// [_openExamSession] writes `{_id,_rev,name,courseId,totalMarks}` and
  /// [createSurveyDraft] writes `{_id,name}`. Neither carries `questions` —
  /// and the port's own pull fills `submission_questions` from
  /// `parent['questions']` ([upsertDocuments]), so an answer sheet uploaded
  /// here and pulled on a second device rendered answers with no questions to
  /// put them against. A writer and a reader disagreeing about a key, each
  /// half passing its own test: Phase 74's reactions, Phase 100's photo id.
  ///
  /// **Kotlin keeps surveys and tests in one `exams` table; the port splits
  /// them**, so one `examDao.getById` in the Kotlin is two lookups here — see
  /// [_liveParentDocument] for why which one goes first is load-bearing.
  Future<Object?> _parentDocument(SubmissionRow row) async {
    final live = await _liveParentDocument(row.parentId, row.type);
    if (live != null) return live;
    // `!submission.parent.isNullOrEmpty()` (`:842`) — an empty blob is
    // omitted, not sent as an empty string.
    return (row.parent?.isEmpty ?? true) ? null : _asDocument(row.parent);
  }

  /// The live parent, looked up in whichever of the two tables the submission
  /// says it was answering, and in the other only as a fallback.
  ///
  /// **The two id spaces are not disjoint**, which is why the order is a
  /// decision rather than a formality. `ExamMapper.mapStepExams` synthesizes
  /// `'$courseId-$stepId-$examKey'` for an embedded step document with no
  /// `_id`, and `SurveyMapper.fromCourseDoc`'s first pass uses the same
  /// `examKey: 'exam'` — so one `steps[i].exam` produces the *same id string*
  /// whether the document's `type` files it under [Exams] or [Surveys]. Flip
  /// that `type` on Planet and the next courses walk writes the row to the
  /// other table while the first one's copy survives: neither `deleteNotIn`
  /// prunes a row that still has a `stepId`, so the loser lingers until a
  /// later completed exams walk.
  ///
  /// Kotlin cannot have this problem — one `exams` table, one row, `@Upsert`
  /// overwrites `type` in place — so there is no Kotlin order to port. The
  /// submission's own `type` is the disambiguator the port has and Kotlin does
  /// not need: an answer sheet knows whether it was answering a survey or a
  /// test, and the row it should upload is the one of that kind. Trying
  /// [Exams] first unconditionally uploaded the stale test to a survey's
  /// answer sheet.
  ///
  /// `type` defaults to the survey side, matching `serializeSubmission:828`'s
  /// own `submission.type ?: "survey"`.
  Future<Map<String, dynamic>?> _liveParentDocument(
    String? parentId,
    String? type,
  ) async {
    // `parentId.substringBefore("@")` — the parent id is `examId@courseId`
    // for a course-attached exam or survey and the bare id otherwise. Routed
    // through [parentBaseId] so there is one derivation, not a re-implemented
    // split per reader.
    final examId = parentBaseId(parentId) ?? '';
    if (examId.isEmpty) return null;

    Future<Map<String, dynamic>?> fromExams() async {
      final exam = await _examDao.getById(examId);
      return exam == null
          ? null
          : examParentDocument(exam, await _examDao.questionsFor(examId));
    }

    Future<Map<String, dynamic>?> fromSurveys() async {
      final survey = await _surveyDao.getById(examId);
      return survey == null
          ? null
          : surveyParentDocument(survey, await _surveyDao.questionsFor(examId));
    }

    return type == 'exam'
        ? await fromExams() ?? await fromSurveys()
        : await fromSurveys() ?? await fromExams();
  }

  /// Port of `StepExam.serializeExam` (`StepExam.kt:70-94`), field for field
  /// and in its order, with two deliberate departures.
  ///
  /// **`type` is not emitted.** Kotlin writes `exam.type` from the single
  /// `exams` table; the port's [Exams] has no such column, because
  /// `ExamMapper.fromDoc` uses the document's `type` to *choose the table* and
  /// then discards it. Adding the column is a schema change, which this round
  /// is not this lane's to make — and the value is not recoverable from the
  /// table anyway: a course test carries `'courses'`
  /// (`CoursesRepositoryImpl.kt:744` keeps the document's own key), a
  /// standalone one with no `type` becomes `'exam'` (`StepExam.kt:39`), and
  /// nothing on [ExamRow] tells the two apart. Omitting the key leaves it
  /// undefined on Planet rather than guessing; nothing in either tree reads a
  /// submission's `parent.type`.
  ///
  /// **Each question carries its `id`, and its label under `title` as well as
  /// `header`.** `ExamQuestion.serializeQuestions` (`ExamQuestion.kt:110-124`)
  /// emits seven keys, no id, and the label under `header`. Both are Kotlin
  /// quirks the port cannot afford.
  ///
  /// The id, because `_questionFromJson` keys the pulled row on it and falls
  /// back to a positional `<submissionId>-q<index>`, while the answers
  /// alongside it carry the real question id — so a faithful upload hands the
  /// second device a question set its own answers cannot be joined to, and
  /// that is *worse* than sending no questions at all: `submissions_exporter`
  /// prints the answers only through its `questions.isEmpty` fallback, so
  /// unjoinable questions print every prompt with no answer and drop the
  /// answers entirely.
  ///
  /// The label, because `header` is a key no real document has. Every reader
  /// of a question in either tree takes it from `title` — Kotlin's
  /// `ExamQuestion.insertExamQuestions` (`:76`), `ExamMapper.parseQuestions`,
  /// `SurveyMapper` — so the snapshot `serializeQuestions` uploads cannot be
  /// read back by the code that reads every other copy of the same question.
  /// Kotlin demonstrates that on itself: `SurveysRepositoryImpl.adoptSurvey`
  /// (`:90-93`) feeds `serializeQuestions` straight into
  /// `insertExamQuestions`, and every adopted team survey loses each
  /// question's id *and* its label. Sending both keys costs nothing.
  ///
  /// Same call as Phase 120's `answers.examId`: keep the quirk unless it
  /// breaks a reader the port has and Kotlin does not.
  static Map<String, dynamic> examParentDocument(
    ExamRow exam,
    List<ExamQuestionRow> questions,
  ) => {
    '_id': exam.id,
    // `if (exam._rev != null)` (`:73-75`).
    if (exam.rev != null) '_rev': exam.rev,
    'name': exam.name,
    'description': exam.description,
    'passingPercentage': exam.passingPercentage,
    'updatedDate': exam.updatedDate,
    'createdDate': exam.createdDate,
    'adoptionDate': exam.adoptionDate,
    'sourcePlanet': exam.sourcePlanet,
    'totalMarks': exam.totalMarks,
    'createdBy': exam.createdBy,
    // Both conditional in the Kotlin (`:86-91`).
    if (exam.sourceSurveyId != null) 'sourceSurveyId': exam.sourceSurveyId,
    if (exam.teamId != null) 'teamId': exam.teamId,
    'questions': [
      for (final question in questions)
        {
          'id': question.id,
          'title': question.header,
          'header': question.header,
          'body': question.body,
          'type': question.type,
          'marks': question.marks,
          'choices': [for (final choice in question.choices) choice.toJson()],
          'correctChoice': question.correctChoices,
          'hasOtherOption': question.hasOtherOption,
        },
    ],
  };

  /// The same document for a row the port filed under [Surveys].
  ///
  /// `type` is omitted here too, and for the same reason as
  /// [examParentDocument] rather than the one it first looks like. The
  /// *exams-database* walk does route on `type == 'surveys'`, so a row from
  /// that walk could recover the value — but `SurveyMapper.fromCourseDoc`'s
  /// second pass files every `steps[i].survey` here with **no** type filter at
  /// all, and Kotlin types one of those `"survey"`, singular. Emitting
  /// `'surveys'` would be a guess for exactly the rows the exam branch refuses
  /// to guess for.
  ///
  /// [SurveyQuestions] has no `marks`, `correctChoice` or `hasOtherOption`
  /// column — Kotlin's one `ExamQuestion` type carries all three for a survey
  /// too, but the port's survey pull has never stored them and there is
  /// nothing to send. The keys are omitted rather than sent as invented
  /// defaults; a survey question has no correct answer to lose.
  ///
  /// The question `id` is [_rawQuestionId], not the row's own composite key,
  /// because that is what the answer's `questionId` carries — the same
  /// identity rule [createSurveyDraft] uses when it keys the local
  /// `submission_questions` rows.
  static Map<String, dynamic> surveyParentDocument(
    SurveyRow survey,
    List<SurveyQuestionRow> questions,
  ) => {
    '_id': survey.id,
    if (survey.rev != null) '_rev': survey.rev,
    'name': survey.name,
    'description': survey.description,
    'passingPercentage': survey.passingPercentage,
    'updatedDate': survey.updatedDate,
    'createdDate': survey.createdDate,
    'adoptionDate': survey.adoptionDate,
    'sourcePlanet': survey.sourcePlanet,
    'totalMarks': survey.totalMarks,
    'createdBy': survey.createdBy,
    if (survey.sourceSurveyId != null) 'sourceSurveyId': survey.sourceSurveyId,
    if (survey.teamId != null) 'teamId': survey.teamId,
    'questions': [
      for (final question in questions)
        {
          'id': _rawQuestionId(question),
          'title': question.header,
          'header': question.header,
          'body': question.body,
          'type': question.type,
          'choices': [for (final choice in question.choices) choice.toJson()],
        },
    ],
  };

  /// Port of `Answer.valueChoicesArray`, which sends each stored choice as the
  /// object it came from (`gson.fromJson(choice, JsonObject::class.java)`):
  /// Planet expects `answers[].value` to be `{id, text}` objects for a choice
  /// question, not strings.
  ///
  /// Every entry is now a choice object — both write paths go through
  /// [AnswerShape], and the sync-in stores the server's own objects — so the
  /// "send a non-JSON entry through untouched" branch this used to carry for
  /// the exam path's bare ids is gone. A bare entry an earlier build left
  /// behind still uploads as an object rather than poisoning the whole queue
  /// (`SubmissionsUploader.queuePending` serializes every pending row in one
  /// unguarded loop, so throwing the way `gson.fromJson` does would block
  /// every other submission): [ExamChoice.decode] resolves it to
  /// `{id: raw, text: raw}`, which is what Kotlin's own unresolvable-id
  /// fallback produces.
  static List<Object?> _answerChoices(List<String> raw) => [
    for (final entry in raw)
      if (ExamChoice.decode(entry) case final choice?) choice.toJson(),
  ];

  /// The `user` object Planet attributes the submission by, and the port's own
  /// sync-in reads the owner back out of.
  ///
  /// Kotlin resolves the live `UserEntity` for `submission.userId` and uploads
  /// `UserEntity.serialize()` (`SubmissionsRepositoryImpl.kt:846-857`), whose
  /// first key is `_id`; the stored `user` blob is only its fallback. This
  /// repository has no user lookup, so it does the reverse — the stored blob,
  /// with `_id` supplied from the row when the blob has none. That is enough
  /// for the round trip (`normalizeSubmissionUserId(user._id)`) and enough for
  /// Planet to attribute the answer sheet; the fuller profile Kotlin sends is
  /// still a gap, recorded in the phase notes.
  ///
  /// `membershipDoc` rides along the same way Kotlin adds it, from the row's
  /// `teamId`. It used to be the *only* place a team survived an upload, and
  /// this comment used to say so; [serialize] now emits a top-level `team`
  /// object as well (`aca425a`), so `membershipDoc` is the fallback the
  /// sync-in reads when `team` is absent rather than the sole carrier. The
  /// port sends both, and the sync-in prefers `team._id` ([upsertDocuments]).
  ///
  /// Sending both is a **superset** on the exam arm, not a Kotlin quirk:
  /// `serializeSubmission` merges `membershipDoc` into the user object
  /// (`:851-856`) while `getExamUploadPayload` (`:749-790`) sends `team` and
  /// never touches `membershipDoc`. The port has one [serialize] for both
  /// Kotlin serializers, so an exam-typed row carries a key Kotlin's exam
  /// path omits. Harmless — it is the same team id, in the field the port's
  /// own sync-in already falls back to.
  ///
  /// It also covers the case the writer cannot: a row whose `teamId` came from
  /// the server rather than from this device still gets its `membershipDoc`,
  /// which is why Kotlin rebuilds the field on every read
  /// (`hydrateSubmissions`, `SubmissionsRepositoryImpl.kt:73`) instead of
  /// persisting it — `Submission.membershipDoc` is `@Ignore`d.
  static Object? _userDocument(SubmissionRow row) {
    final userId = row.userId;
    final stored = _asDocument(row.user);
    final base = <String, dynamic>{
      if (stored is Map<String, dynamic>) ...stored,
      // Last, so the row's normalized id wins over the raw `ada@lea` a synced
      // blob carries — that normalized form is what `watchForUser` matches and
      // what Kotlin's resolved `UserEntity._id` would have supplied.
      if (userId != null && userId.isNotEmpty) '_id': userId,
    };
    if (base.isEmpty) return null;
    final teamId = row.teamId;
    if (teamId != null && teamId.isNotEmpty) {
      // Overwrites, as `serializeSubmission:851-855` does: the row's `teamId`
      // is the authority, not whatever an older blob happens to carry.
      base['membershipDoc'] = {'teamId': teamId};
    }
    return base;
  }

  /// The `team` object the upload carries, or null when this submission has no
  /// team to attribute — a port of `resolveTeamJson`
  /// (`SubmissionsRepositoryImpl.kt:791-810`, new in `aca425a`).
  ///
  /// The id is the authority and the name and type are decoration: `_id` is
  /// always written when there is an id at all, while `name` and `type` are
  /// **omitted rather than sent as null** when the team document is not on
  /// this handset. That asymmetry is the whole point of the upstream change.
  /// Planet attributes a submission by `team._id`, so a document with the id
  /// and no name is attributable and one with neither is not; the previous
  /// Kotlin emitted the object only when the team resolved, which silently
  /// dropped the association exactly when the local cache was incomplete —
  /// the normal state of a handset that has not finished syncing.
  ///
  /// **Kotlin's `teamObject` branch has no port counterpart, and needs none.**
  /// `resolveTeamJson` prefers `submission.teamObject?._id` over
  /// `submission.teamId`, but `Submission.teamObject` is `@Ignore`d
  /// (`Submission.kt:20`) — it is never persisted, and both Kotlin serializers
  /// are reached from `UploadConfigs` with rows fetched through
  /// `getPendingExamResults`/`getPendingSubmissionsForUpload`
  /// (`UploadConfigs.kt:242,256`), which rehydrate `membershipDoc` and not
  /// `teamObject`. So the field is always null on the upload path and the
  /// `submission.teamId` fallback is the branch that runs. The port's
  /// [serialize] likewise only ever sees a persisted [SubmissionRow].
  Future<Map<String, dynamic>?> _resolveTeamJson(SubmissionRow row) async {
    final teamId = row.teamId;
    // `?.takeIf { it.isNotBlank() } ?: return null` (`:793-795`) — blank, not
    // merely empty, so a whitespace column cannot produce `{"_id": " "}`.
    if (teamId == null || teamId.trim().isEmpty) return null;

    final team = await _teamByIdOrNull(teamId);
    final name = team?.name;
    final type = team?.type;
    return {
      '_id': teamId,
      // The stored value, not the trimmed one: Kotlin's `takeIf` decides
      // whether to write the string, it does not rewrite it.
      if (name != null && name.trim().isNotEmpty) 'name': name,
      if (type != null && type.trim().isNotEmpty) 'type': type,
    };
  }

  /// Port of `getTeamByIdOrNull` (`SubmissionsRepositoryImpl.kt:812-818`): the
  /// team if this handset has it, null if the lookup fails.
  ///
  /// A failed lookup must not fail the upload. Before `aca425a` the Kotlin
  /// called `getTeamById` bare, so a throwing read took the whole
  /// `createExamSubmission` down and `startExamSession`'s three retries burned
  /// on an error retrying cannot fix.
  ///
  /// **Drift has a `CancellationException` and it implements `Exception`**, so
  /// the rethrow ports literally rather than by analogy. Kotlin has to name
  /// its own `CancellationException` for exactly this reason — a cancellation
  /// *is* an `Exception` there, so a lone `catch (_: Exception)` would swallow
  /// a cancelled scope and turn it into a silent null. The port's production
  /// executor is `NativeDatabase.createInBackground`, whose isolate server
  /// wraps every query in drift's `runCancellable`, so the same type is on the
  /// table here; no path reaches this one-shot read from inside a cancellation
  /// zone today, and that is not a reason to leave the hazard unhandled. An
  /// earlier revision of this comment claimed "Dart has no cancellation
  /// exception"; it was wrong.
  ///
  /// Every other `Exception` — a drift or IO failure, the thing Kotlin is
  /// absorbing — becomes a cache miss, and the upload keeps its `team._id`.
  ///
  /// An `Error` propagates, which is **deliberately stricter than Kotlin** in
  /// one case: reading a closed database throws Dart `StateError` where Room
  /// throws `IllegalStateException`, an `Exception` that Kotlin's catch
  /// absorbs. Nothing in `lib/` ever closes the database, so in production the
  /// case does not arise; when it does arise it is a defect in the caller, and
  /// reporting it as a team that is merely missing would hide it behind a
  /// document that looks under-populated.
  Future<TeamRow?> _teamByIdOrNull(String teamId) async {
    try {
      return await _teamDao.getById(teamId);
    } on CancellationException {
      rethrow;
    } on Exception catch (_) {
      return null;
    }
  }

  /// `parent` and `user` are stored locally as JSON *text*, but Kotlin uploads
  /// them as nested objects — `object.add("parent", ...)` and
  /// `object.add("user", JsonParser.parseString(submission.user))` in
  /// `SubmissionsRepositoryImpl`. Posting the string instead produces a
  /// document whose `parent._id` and `user.name` do not exist as far as Planet
  /// is concerned, so it can attribute the submission neither to its survey nor
  /// to its respondent. Survey adoption makes this unmissable: the whole point
  /// of the adoption document is the user object it carries.
  ///
  /// `createDraft` stores a plain title in `parent` rather than JSON, so
  /// anything that does not decode to an object is sent through untouched.
  static Object? _asDocument(String? raw) {
    if (raw == null || raw.isEmpty) return raw;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map<String, dynamic>) return decoded;
    } on FormatException {
      // Not JSON — fall through and send the raw string.
    }
    return raw;
  }

  /// Port of `SubmissionsRepositoryImpl.upsertRoomSubmissionsFromSync`
  /// (`SubmissionsRepositoryImpl.kt:662-736`).
  ///
  /// The columns this fills are **not** the keys the document carries at the
  /// top level. Three of them are derived, and reading them as plain keys is
  /// what made the port's submissions list unable to show anything the server
  /// sent:
  ///
  /// * `userId` is `normalizeSubmissionUserId(user._id)` — the owner lives in
  ///   the nested user object, and `serializeSubmission` (`:820-865`) emits no
  ///   top-level `userId` at all. The port's own uploader used to emit one,
  ///   which is the only reason anything ever appeared here: the pair agreed
  ///   with itself and disagreed with Planet.
  /// * `uploaded` is `_rev.isNotEmpty()`, not a stored flag. A document that
  ///   came back from CouchDB has by definition been uploaded, so reading a
  ///   top-level `uploaded` key rendered every synced submission "not turned
  ///   in".
  /// * `teamId` is `team._id`, falling back to `user.membershipDoc.teamId`.
  ///
  /// `user` and `parent` are stored as **JSON text** (`gson.toJson`). Passing
  /// the decoded map through `JsonUtils.getStringOrNull` instead fell through
  /// to `Map.toString()` and stored `{_id: exam-1, name: Week 1 quiz}`, which
  /// is not JSON: `jsonDecode` threw on it wherever it was read back (silently,
  /// in `SurveysRepository._parentSurveyId`, so team-survey adoption saw
  /// nothing), the list drew it as a title, and a re-upload replaced Planet's
  /// objects with the literal. Same shape as Phase 104's `SurveyMapper.choices`.
  ///
  /// `isUpdated` is hard-`false`: a document that arrived from the server has
  /// nothing pending. Reading it from the document would let the server decide
  /// what this device still owes it.
  ///
  /// A document carrying `_attachments` is skipped whole, and the *user*
  /// object's `_attachments` are stripped before it is stored — a base64
  /// profile photo inside the blob can push one row past SQLite's cursor
  /// window. `membershipDoc` is read **before** the strip, as the Kotlin
  /// comment insists, because the two keys are independent.
  Future<void> upsertDocuments(Iterable<Map<String, dynamic>> documents) {
    final rows = <SubmissionsCompanion>[];
    final answers = <String, List<SubmissionAnswersCompanion>>{};
    final questions = <String, List<SubmissionQuestionsCompanion>>{};
    for (final json in documents) {
      if (json.containsKey('_attachments')) continue;
      final id = JsonUtils.getString('_id', json);
      // Kotlin's `if (id.isBlank()) return@forEach` — one unusable document
      // must not abort the page around it.
      if (id.trim().isEmpty) continue;
      final rev = JsonUtils.getString('_rev', json);
      final parentId = JsonUtils.getString('parentId', json);
      final userJson = JsonUtils.getObject('user', json);
      final membershipJson = JsonUtils.getObject('membershipDoc', userJson);
      final storedUser = userJson == null
          ? null
          : {
              for (final entry in userJson.entries)
                if (entry.key != '_attachments') entry.key: entry.value,
            };
      final teamJson = JsonUtils.getObject('team', json);
      final teamId = JsonUtils.getString('_id', teamJson);
      final parentJson = JsonUtils.getObject('parent', json);
      rows.add(
        SubmissionsCompanion.insert(
          id: id,
          couchId: Value(id),
          rev: Value(rev.isEmpty ? null : rev),
          parentId: Value(parentId.isEmpty ? null : parentId),
          type: Value(JsonUtils.getStringOrNull('type', json)),
          userId: Value(
            normalizeSubmissionUserId(JsonUtils.getString('_id', userJson)),
          ),
          user: Value(storedUser == null ? null : jsonEncode(storedUser)),
          startTime: Value(JsonUtils.getLong('startTime', json)),
          lastUpdateTime: Value(JsonUtils.getLong('lastUpdateTime', json)),
          grade: Value(JsonUtils.getLong('grade', json)),
          status: Value(JsonUtils.getStringOrNull('status', json)),
          uploaded: Value(rev.isNotEmpty),
          sender: Value(JsonUtils.getStringOrNull('sender', json)),
          source: Value(JsonUtils.getStringOrNull('source', json)),
          parentCode: Value(JsonUtils.getStringOrNull('parentCode', json)),
          parent: Value(parentJson == null ? null : jsonEncode(parentJson)),
          teamId: Value(
            teamId.isNotEmpty
                ? teamId
                : JsonUtils.getStringOrNull('teamId', membershipJson),
          ),
          isUpdated: const Value(false),
        ),
      );
      final rawAnswers = json['answers'];
      answers[id] = [
        if (rawAnswers is List)
          for (var index = 0; index < rawAnswers.length; index++)
            if (rawAnswers[index] is Map<String, dynamic>)
              _answerFromJson(
                rawAnswers[index] as Map<String, dynamic>,
                submissionId: id,
                index: index,
                // Deliberately the base exam id rather than Kotlin's whole
                // `parentId`. Kotlin never joins on `Answer.examId` — it
                // reaches the exam through the answer's question row — while
                // the port's course-progress calc looks the answer's exam up
                // directly (`courses_providers.dart:528`), against bare exam
                // ids. Storing
                // `exam-1@course-1` here would make a synced attempt's
                // mistakes silently uncountable.
                examId: parentId.isEmpty ? null : parentId.split('@').first,
              ),
      ];
      final rawQuestions = parentJson?['questions'];
      questions[id] = [
        if (rawQuestions is List)
          for (var index = 0; index < rawQuestions.length; index++)
            if (rawQuestions[index] is Map<String, dynamic>)
              _questionFromJson(
                rawQuestions[index] as Map<String, dynamic>,
                submissionId: id,
                index: index,
              ),
      ];
    }
    if (rows.isEmpty) return Future.value();
    return _dao.upsertAll(rows, answers: answers, questions: questions);
  }

  /// Port of `SubmissionsRepositoryImpl.normalizeSubmissionUserId`
  /// (`:741-748`). A `user._id` of `ada@lea` or
  /// `org.couchdb.user:ada@lea` reduces to `org.couchdb.user:ada`, which is
  /// the id the session carries; anything without an `@` is left alone.
  static String? normalizeSubmissionUserId(String userId) {
    if (userId.isEmpty) return null;
    if (!userId.contains('@')) return userId;
    final local = userId.substring(0, userId.indexOf('@'));
    return local.startsWith('org.couchdb.user:')
        ? local
        : 'org.couchdb.user:$local';
  }

  SubmissionQuestionsCompanion _questionFromJson(
    Map<String, dynamic> json, {
    required String submissionId,
    required int index,
  }) {
    final questionId =
        JsonUtils.getStringOrNull('id', json) ?? '$submissionId-q$index';
    final rawChoices = json['choices'];
    final choiceLabels = <String>[];
    if (rawChoices is List) {
      for (final choice in rawChoices) {
        if (choice is Map<String, dynamic>) {
          // `ExamAnswerUtils.choiceDisplayValue` is `text` first with `res`
          // only as a fallback, and `ExamTakingFragment.addCompoundButton`
          // reads `text` alone. Reading only `res` left this display column
          // empty for every ordinary `{"id":…,"text":…}` choice, so the detail
          // screen's "Choices:" row came out as a row of commas.
          final text = JsonUtils.getString('text', choice);
          choiceLabels.add(
            text.isNotEmpty ? text : JsonUtils.getString('res', choice),
          );
        } else if (choice != null) {
          choiceLabels.add(choice.toString());
        }
      }
    }
    final correct = json['correctChoice'];
    return SubmissionQuestionsCompanion.insert(
      id: '$submissionId:$questionId',
      submissionId: submissionId,
      header: Value(
        JsonUtils.getStringOrNull('title', json) ??
            JsonUtils.getStringOrNull('header', json),
      ),
      body: Value(JsonUtils.getStringOrNull('body', json)),
      type: Value(JsonUtils.getStringOrNull('type', json)),
      correctChoices: Value(
        correct is List
            ? correct.map((e) => e.toString().toLowerCase()).toList()
            : [if (correct != null) correct.toString().toLowerCase()],
      ),
      choices: Value(choiceLabels),
      marks: Value(JsonUtils.getStringOrNull('marks', json)),
      position: index,
    );
  }

  SubmissionAnswersCompanion _answerFromJson(
    Map<String, dynamic> json, {
    required String submissionId,
    required int index,
    required String? examId,
  }) {
    final rawValue = json['value'];
    // Kotlin stores `valueElement.asJsonArray.map { it.toString() }`, and
    // Gson's `JsonElement.toString()` emits **JSON**. Dart's `Map.toString()`
    // emits `{id: paris, text: Paris}`, which is not JSON — so a synced choice
    // answer was cached in a form nothing downstream could read back: the
    // re-upload sent that literal as a quoted string where Planet expects the
    // object, the detail screen and the PDF export printed it verbatim, and
    // the correctness check could not recover the id. A bare-string element
    // is stored unquoted; Gson keeps its quotes there, but then Kotlin's own
    // `valueChoicesArray` throws casting the primitive to a `JsonObject`, so
    // the unquoted form is the better of the two.
    final choices = rawValue is List
        ? rawValue
              .map((value) => value is Map ? jsonEncode(value) : '$value')
              .toList(growable: false)
        : const <String>[];
    final questionId = JsonUtils.getStringOrNull('questionId', json);
    return SubmissionAnswersCompanion.insert(
      id: '$submissionId:${questionId ?? index}',
      submissionId: submissionId,
      examId: Value(examId),
      questionId: Value(questionId),
      value: Value(rawValue is List ? null : rawValue?.toString()),
      valueChoices: Value(choices),
      mistakes: Value(JsonUtils.getInt('mistakes', json)),
      isPassed: Value(JsonUtils.getBool('passed', json)),
      grade: Value(JsonUtils.getInt('grade', json)),
    );
  }

  /// Pulls the complete `submissions` CouchDB table into the offline cache.
  /// Pages are committed independently, matching Kotlin's partial-sync rule.
  ///
  /// There is deliberately **no `deleteNotIn`**, on a complete walk or an empty
  /// database, because the Kotlin walk has none: `TransactionSyncManager.syncDb`
  /// (`:277`) hands each page to `bulkInsertFromSync` and never prunes, and
  /// nothing else in the Kotlin tree deletes a stale submission on a sync.
  /// Running one here was destructive rather than merely unfaithful. The keep
  /// set is CouchDB `_id`s while a locally authored row is keyed by a sha1, and
  /// `deleteNotIn` only spares `isUpdated` rows — so the moment
  /// [markUploaded] cleared that flag, the learner's own attempt (and its
  /// answers) became eligible for deletion by the very next sync. Same rule as
  /// the notifications walk, for the same reason: a prune over a table with two
  /// authors evicts the author that has no server document.
  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);
    final countResult = await _api.getJsonObject(
      '$dbUrl/submissions/_all_docs?limit=0',
      authHeader: authHeader,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }

    final total = JsonUtils.getInt('total_rows', countResult.data);
    if (total == 0) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final sizer = AdaptiveBatchProcessor(initialSize: initialBatchSize);
    final savedIds = <String>[];
    var skip = 0;
    while (skip < total) {
      final limit = sizer.currentSize;
      final stopwatch = Stopwatch()..start();
      final pageResult = await _api.getJsonObject(
        '$dbUrl/submissions/_all_docs?include_docs=true&limit=$limit&skip=$skip',
        authHeader: authHeader,
      );
      stopwatch.stop();
      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        sizer.recordFailure();
        return SyncFailed(describeNetworkFailure(pageResult));
      }
      sizer.recordSuccess(stopwatch.elapsedMilliseconds);

      final rows = pageResult.data['rows'];
      if (rows is! List || rows.isEmpty) break;
      final docs = <Map<String, dynamic>>[];
      for (final row in rows) {
        if (row is! Map<String, dynamic>) continue;
        final doc = JsonUtils.getObject('doc', row);
        final id = JsonUtils.getString('_id', doc);
        if (doc == null || id.isEmpty || id.startsWith('_design/')) continue;
        docs.add(doc);
        savedIds.add(id);
      }
      if (docs.isNotEmpty) await upsertDocuments(docs);
      skip += rows.length;
      onProgress?.call(
        SyncProgress(completed: skip > total ? total : skip, total: total),
      );
    }

    return SyncComplete(savedIds.length);
  }
}

/// One row of the submissions list: the attempt to show, and how many attempts
/// there are for the same parent.
class SubmissionListEntry {
  const SubmissionListEntry(this.row, this.count);

  final SubmissionRow row;

  /// How many submissions share this row's `parentId`. `1` for a lone attempt.
  final int count;

  @override
  bool operator ==(Object other) =>
      other is SubmissionListEntry && row == other.row && count == other.count;

  @override
  int get hashCode => Object.hash(row, count);
}

/// Port of the collapse in `SubmissionViewModel.kt:67-73`:
///
/// ```kotlin
/// for (group in filtered.groupBy { it.parentId }.values) {
///     val newest = group.maxByOrNull { it.lastUpdateTime } ?: continue
///     uniqueRawSubmissions.add(newest); submissionCountMap[newest.id] = group.size
/// }
/// ```
///
/// This is what hides a duplicate the `submissions` table genuinely holds. A
/// locally authored submission keeps its sha1 primary key after
/// [SubmissionsRepository.markUploaded] stamps the CouchDB id onto it, so when
/// the walk pulls that same document back it lands as a **second** row keyed by
/// `_id`. Kotlin has exactly the same duplicate (its local key is a UUID) and
/// never prunes it; the list collapses it instead. The port used to hide it by
/// deleting the local row in `deleteNotIn`, which also deleted attempts that had
/// never been uploaded at all — see [SubmissionsRepository.sync].
///
/// One deliberate departure: a row with **no** `parentId` is never grouped.
/// Kotlin's `groupBy` would fold every null together, but Kotlin has no writer
/// that leaves it null; the port's `createDraft` (the list's own New submission
/// button) does, and folding those would hide one ad-hoc draft behind another.
List<SubmissionListEntry> collapseSubmissionsByParent(
  List<SubmissionRow> rows,
) {
  final grouped = <String, List<SubmissionRow>>{};
  final ungrouped = <SubmissionRow>[];
  for (final row in rows) {
    final parentId = row.parentId;
    if (parentId == null || parentId.isEmpty) {
      ungrouped.add(row);
    } else {
      (grouped[parentId] ??= []).add(row);
    }
  }
  return [
    for (final group in grouped.values)
      SubmissionListEntry(
        group.reduce((a, b) => b.lastUpdateTime > a.lastUpdateTime ? b : a),
        group.length,
      ),
    for (final row in ungrouped) SubmissionListEntry(row, 1),
  ]..sort((a, b) => b.row.lastUpdateTime.compareTo(a.row.lastUpdateTime));
}

/// Port of `SubmissionsRepositoryImpl.getNormalizedSubmitterName` (`:316-323`)
/// — the `name` inside the stored `user` JSON, or null.
///
/// The detail screen used to print the `user` column itself, which is a
/// serialized object, not a name.
String? submissionSubmitterName(SubmissionRow row) {
  final user = row.user;
  if (user == null || user.trim().isEmpty) return null;
  try {
    final decoded = jsonDecode(user);
    if (decoded is Map<String, dynamic>) {
      // `takeIf { it.isNotBlank() }` — a whitespace-only name is no name.
      final name = JsonUtils.getString('name', decoded).trim();
      return name.isEmpty ? null : name;
    }
  } on FormatException {
    // `runCatching { ... }.getOrNull()`.
  }
  return null;
}

/// The title the submissions list and detail screen show for a row.
///
/// `parent` is JSON text (`gson.toJson` on the pull, `jsonEncode` on every
/// local write), so drawing the column raw put a serialized object on screen.
/// Kotlin reads the exam's own `name` (`SubmissionViewModel.kt:89`,
/// `examsMap[parentId]?.name`); the blob carries that same name, which reaches
/// it without the extra lookup. Falls back to the raw column, which is what
/// `createDraft` stores — a plain user-typed title, not JSON.
String? submissionDisplayTitle(SubmissionRow row) {
  final parent = row.parent;
  if (parent == null || parent.trim().isEmpty) return null;
  try {
    final decoded = jsonDecode(parent);
    if (decoded is Map<String, dynamic>) {
      // Null rather than a rung of its own: the callers already fall back to
      // the type and then to a localized "Submission", which is where Kotlin's
      // `?: "Submissions"` (`SubmissionViewModel.kt:89`) lands.
      return JsonUtils.getStringOrNull('name', decoded);
    }
  } on FormatException {
    // Not JSON — `createDraft` stores the title itself.
  }
  return parent;
}

class SubmissionDraftAnswer {
  const SubmissionDraftAnswer({
    this.questionId,
    this.value,
    this.choices = const [],
  });
  final String? questionId;
  final String? value;

  /// The picked choices as stored answer entries — see [ExamChoice.encode].
  /// [AnswerShape.forQuestion] re-resolves them against the question, so a
  /// caller that only has ids may pass those instead.
  final List<String> choices;
}

/// The `value` / `valueChoices` pair one answered question is stored as.
///
/// Port of the branch at the top of `SubmissionsRepositoryImpl.saveExamAnswer`,
/// which is the **single** writer for both exams and surveys in the Kotlin app
/// (`ExamTakingFragment` runs both; its `type` only picks the grading and the
/// status). The port has three writers — [SubmissionsRepository.createExamDraft],
/// [SubmissionsRepository.createSurveyDraft] and
/// [SubmissionsRepository.updateSurveyAnswers] — so the shape lives here, once,
/// rather than being re-derived per screen.
///
/// The two halves are not independent: `Answer.createObject` sends `value`
/// whenever it is non-empty and only falls back to `valueChoicesArray` when it
/// is not, so which of them Kotlin fills decides what reaches Planet.
class AnswerShape {
  const AnswerShape({required this.value, required this.valueChoices});

  final String? value;
  final List<String> valueChoices;

  /// [selected] are the picked choices; [text] is whatever a text field,
  /// textarea or rating scale holds.
  ///
  /// * `select` — `value` is the choice's display text (`ansForCheck`, i.e.
  ///   `getChoiceTextById`) and `valueChoices` the one choice object, or `[]`
  ///   when nothing is picked. Kotlin's radio group records a single id, so
  ///   only the first selection is read.
  /// * `selectMultiple` — `value` is the **empty string**, which is what makes
  ///   `createObject` send the object array, and `valueChoices` one object per
  ///   pick.
  /// * anything else — `value` is [text] and `valueChoices` is empty
  ///   (`valueChoices = null` in Kotlin; the column here is not nullable).
  ///
  /// The `hasOtherOption` branches are not ported: the port renders no "Other"
  /// choice on either screen, so `otherVisible` is never true and there is
  /// nothing to carry. It stays part of the open `hasOtherOption` gap — and
  /// closing it needs a `hasOtherOption` column on `survey_questions`, which
  /// `ExamQuestions` has but `SurveyQuestions` does not. That table is in
  /// `localAuthorityTables` since Phase 143, so `createAll` will not alter it:
  /// the column costs a schema bump **and** a hand-written
  /// `_addColumnIfMissing` step. `migration_test.dart` enforces both — its
  /// frozen `survey_questions` DDL reds the moment a column lands (those
  /// literals are deliberately not kept in step with the table, which is what
  /// makes them able to fail), and the post-upgrade `containsAll` over
  /// `database.surveyQuestions.$columns` stays red until the step adds it.
  /// `PHASE_123_NOTES.md:108-110` records `marks`, `correctChoice` and
  /// `hasOtherOption` as the three columns still owed, so this is a live
  /// target rather than a hypothetical one.
  static AnswerShape forQuestion({
    required String? type,
    required List<ExamChoice> choices,
    required Iterable<ExamChoice> selected,
    String? text,
  }) {
    final normalized = type?.toLowerCase();
    // `ExamTakingFragment.startExam` and `saveExamAnswer` both compare the
    // type with `ignoreCase = true`.
    if (normalized == 'selectmultiple') {
      return AnswerShape(
        value: '',
        valueChoices: [
          for (final choice in selected) _resolve(choices, choice).encode(),
        ],
      );
    }
    // A question that offers choices and got a pick is a choice question even
    // when its type says nothing. `saveExamAnswer` has no such clause and
    // needs none — `ExamTakingFragment.startExam` renders no input at all for
    // an unrecognised type, so the answer cannot exist — but the port's survey
    // card renders a radio group off `choices.isNotEmpty` and reads the type
    // only to pick checkboxes over radios. Falling through to the plain-text
    // branch here would discard a pick the screen had just accepted.
    if (normalized == 'select' || (choices.isNotEmpty && selected.isNotEmpty)) {
      final picked = selected.isEmpty
          ? null
          : _resolve(choices, selected.first);
      return AnswerShape(
        value: picked?.text ?? '',
        valueChoices: picked == null ? const [] : [picked.encode()],
      );
    }
    return AnswerShape(value: text, valueChoices: const []);
  }

  /// The choice as the question defines it.
  ///
  /// `saveExamAnswer` resolves the text from the question
  /// (`getChoiceTextById`), not from whatever the screen was holding, and
  /// falls back to the id when the question no longer offers the choice. An
  /// entry that carries its own text is honoured before that last fallback —
  /// that only happens for a stored answer whose question has since changed,
  /// where the recorded label is better than the raw id.
  static ExamChoice _resolve(List<ExamChoice> choices, ExamChoice selected) {
    final text = ExamChoice.textById(choices, selected.id);
    if (text != selected.id) return ExamChoice(id: selected.id, text: text);
    return selected.text.isEmpty
        ? ExamChoice(id: selected.id, text: selected.id)
        : selected;
  }
}

/// One question's answer on an exam attempt.
///
/// It carries no verdict. `saveExamAnswer` grades the answer itself, from the
/// question's own `correctChoices`, exactly as Kotlin does — the value written
/// to the row and the value returned to the gate are then one computation
/// rather than two that can disagree.
class ExamDraftAnswer {
  const ExamDraftAnswer({this.value, this.choiceIds = const []});

  /// Free text, or the selected rating rendered as a string.
  final String? value;

  /// Ids of the selected choices — ids, not labels, because that is what
  /// `correctChoices` holds and what a radio/checkbox records
  /// (`addCompoundButton` puts the choice's `id` in the button's tag).
  /// [AnswerShape] pairs each one with the label the question gives it, which
  /// is the `{id, text}` object a stored answer and an uploaded document
  /// actually carry.
  final List<String> choiceIds;

  bool get isEmpty => (value == null || value!.isEmpty) && choiceIds.isEmpty;
}
