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
  );

  static const int initialBatchSize = 100;

  final PlanetApi _api;
  final SubmissionDao _dao;
  final SubmitPhotosDao _photosDao;
  final SurveyDao _surveyDao;

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
  Future<String> createSurveyDraft({
    required SurveyRow survey,
    required List<SurveyQuestionRow> questions,
    required String userId,
    Map<String, SubmissionDraftAnswer> answers = const {},
    DateTime? now,
  }) async {
    final timestamp = (now ?? DateTime.now()).millisecondsSinceEpoch;
    final id = sha1
        .convert(utf8.encode('$userId:$timestamp:${survey.id}'))
        .toString();
    await _dao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: id,
          parentId: Value(survey.id),
          parent: Value(jsonEncode({'_id': survey.id, 'name': survey.name})),
          userId: Value(userId),
          type: const Value('survey'),
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
  Future<String> startExamSession({
    required ExamRow exam,
    required List<ExamQuestionRow> questions,
    required String userId,
    String? courseId,
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
    DateTime? now,
  }) async {
    await _deleteExamSubmissions(parentId: parentId, userId: userId);
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
  /// The port used to store the bare `courseId` here, which
  /// `ProgressRepository._examIdFromParent` reads as the exam id — it takes
  /// the leading `@`-delimited segment — so the per-step mistake counts could
  /// never find their exam even once `mistakes` was populated.
  static String examParentId({required String examId, String? courseId}) =>
      (courseId?.isNotEmpty ?? false) ? '$examId@$courseId' : examId;

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
  Future<void> createBulkSurveySubmissions(
    String surveyId,
    List<String> userIds, {
    DateTime? now,
    String Function()? createId,
  }) async {
    for (final userId in userIds) {
      await getOrCreateSurveySubmission(
        userId: userId,
        parentId: surveyId,
        now: now,
        createId: createId,
      );
    }
  }

  /// Port of `SubmissionsRepositoryImpl.getOrCreateSubmission`.
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

  Future<List<SubmissionRow>> pendingUploads(String userId) =>
      _dao.pendingUploads(userId);

  /// Port of `SubmissionsRepositoryImpl.hasUnfinishedSurveys`. Returns true
  /// if the course has any attached survey the user has not yet submitted.
  ///
  /// Also aliased as `hasPendingSurvey` — the challenge dialog's name for the
  /// same check. The Kotlin has both methods; they are identical.
  Future<bool> hasUnfinishedSurveys(String courseId, String? userId) async {
    if (courseId.isEmpty || (userId ?? '').isEmpty) return false;
    final surveys = await _surveyDao.getByCourseId(courseId);
    for (final survey in surveys) {
      final parentId = '${survey.id}@$courseId';
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
  /// the original. This is reachable: [upsertDocuments] takes `isUpdated`
  /// straight from the server document, and [pendingUploads] selects on it.
  ///
  /// Device telemetry is added by [SubmissionsUploader], where the platform
  /// seam is available. The `source`/`parentCode` planet identifiers Kotlin
  /// also sends remain part of the community-code parity gap.
  Future<Map<String, dynamic>> serialize(SubmissionRow row) async {
    final answers = await _dao.answersFor(row.id);
    return {
      if (row.couchId != null && row.couchId!.isNotEmpty) '_id': row.couchId,
      if (row.rev != null && row.rev!.isNotEmpty) '_rev': row.rev,
      'type': row.type,
      'userId': row.userId,
      'user': _asDocument(row.user),
      'parentId': row.parentId,
      'parent': _asDocument(row.parent),
      'startTime': row.startTime,
      'lastUpdateTime': row.lastUpdateTime,
      'status': row.status,
      'grade': row.grade,
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

  /// Stores a CouchDB page. Answers and exam details remain with the later
  /// surveys/exam slice; this preserves every field used by the list screen.
  Future<void> upsertDocuments(Iterable<Map<String, dynamic>> documents) {
    final rows = <SubmissionsCompanion>[];
    final answers = <String, List<SubmissionAnswersCompanion>>{};
    final questions = <String, List<SubmissionQuestionsCompanion>>{};
    for (final json in documents) {
      final id =
          JsonUtils.getStringOrNull('id', json) ??
          JsonUtils.getStringOrNull('_id', json);
      if (id == null || id.isEmpty) {
        throw const FormatException('Submission is missing an id');
      }
      rows.add(
        SubmissionsCompanion.insert(
          id: id,
          couchId: Value(JsonUtils.getStringOrNull('_id', json)),
          rev: Value(JsonUtils.getStringOrNull('_rev', json)),
          parentId: Value(JsonUtils.getStringOrNull('parentId', json)),
          type: Value(JsonUtils.getStringOrNull('type', json)),
          userId: Value(JsonUtils.getStringOrNull('userId', json)),
          user: Value(JsonUtils.getStringOrNull('user', json)),
          startTime: Value(JsonUtils.getLong('startTime', json)),
          lastUpdateTime: Value(JsonUtils.getLong('lastUpdateTime', json)),
          grade: Value(JsonUtils.getLong('grade', json)),
          status: Value(JsonUtils.getStringOrNull('status', json)),
          uploaded: Value(JsonUtils.getBool('uploaded', json)),
          sender: Value(JsonUtils.getStringOrNull('sender', json)),
          source: Value(JsonUtils.getStringOrNull('source', json)),
          parentCode: Value(JsonUtils.getStringOrNull('parentCode', json)),
          parent: Value(JsonUtils.getStringOrNull('parent', json)),
          teamId: Value(JsonUtils.getStringOrNull('teamId', json)),
          isUpdated: Value(JsonUtils.getBool('isUpdated', json)),
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
                examId: JsonUtils.getStringOrNull(
                  'parentId',
                  json,
                )?.split('@').first,
              ),
      ];
      final parent = json['parent'];
      final rawQuestions = parent is Map<String, dynamic>
          ? parent['questions']
          : null;
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
    return _dao.upsertAll(rows, answers: answers, questions: questions);
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
      await _dao.deleteNotIn(const []);
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final sizer = AdaptiveBatchProcessor(initialSize: initialBatchSize);
    final savedIds = <String>[];
    var skip = 0;
    var walkedEveryPage = true;
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
      if (rows is! List || rows.isEmpty) {
        walkedEveryPage = false;
        break;
      }
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

    if (walkedEveryPage) await _dao.deleteNotIn(savedIds);
    return SyncComplete(savedIds.length);
  }
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
  /// `ExamQuestions` has but `SurveyQuestions` does not. That table is not in
  /// `localAuthorityTables`, so `createAll` rebuilds it and the column costs
  /// only a schema bump, no hand-written step.
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
