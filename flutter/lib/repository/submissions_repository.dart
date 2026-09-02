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
              choices: Value(question.choices),
              position: question.position,
            ),
        ],
      },
      answers: {
        id: [
          for (final question in questions)
            SubmissionAnswersCompanion.insert(
              id: '$id:${_rawQuestionId(question)}',
              submissionId: id,
              questionId: Value(_rawQuestionId(question)),
              value: Value(answers[question.id]?.value),
              valueChoices: Value(answers[question.id]?.choices ?? const []),
            ),
        ],
      },
    );
    return id;
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
            SubmissionAnswersCompanion.insert(
              id: '$submissionId:${_rawQuestionId(question)}',
              submissionId: submissionId,
              questionId: Value(_rawQuestionId(question)),
              value: Value(answers[question.id]?.value),
              valueChoices: Value(answers[question.id]?.choices ?? const []),
            ),
        ],
      },
    );
  }

  /// The server-assigned question id, or the synthetic `surveyId:index` when
  /// the document carried none. Either way it is what the answer is keyed by.
  static String _rawQuestionId(SurveyQuestionRow question) =>
      question.questionId ?? question.id;

  /// Records a completed exam attempt, with its grade, as an uploadable
  /// submission.
  ///
  /// Kotlin's `ExamTakingFragment` writes a `RealmSubmission` per attempt and
  /// `UploadManager` sends it. Without this the port graded an exam into a
  /// dialog and dropped the result when the dialog closed — the attempt never
  /// reached the device, let alone the server.
  Future<String> createExamDraft({
    required ExamRow exam,
    required List<ExamQuestionRow> questions,
    required String userId,
    required Map<String, ExamDraftAnswer> answers,
    String? courseId,
    DateTime? now,
  }) async {
    final timestamp = (now ?? DateTime.now()).millisecondsSinceEpoch;
    final id = sha1
        .convert(utf8.encode('$userId:$timestamp:${exam.id}'))
        .toString();
    final correct = questions
        .where((question) => answers[question.id]?.isCorrect ?? false)
        .length;
    final grade = questions.isEmpty ? 0 : (correct * 100) ~/ questions.length;
    await _dao.upsertAll(
      [
        SubmissionsCompanion.insert(
          id: id,
          parentId: Value(courseId ?? exam.courseId),
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
          grade: Value(grade),
          status: const Value('complete'),
          uploaded: const Value(false),
          isUpdated: const Value(true),
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
            SubmissionAnswersCompanion.insert(
              id: '$id:${question.id}',
              submissionId: id,
              examId: Value(exam.id),
              questionId: Value(question.id),
              value: Value(answers[question.id]?.value),
              valueChoices: Value(answers[question.id]?.choiceIds ?? const []),
              isPassed: Value(answers[question.id]?.isCorrect ?? false),
              grade: Value((answers[question.id]?.isCorrect ?? false) ? 1 : 0),
            ),
        ],
      },
    );
    return id;
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
            'value': answer.valueChoices.isNotEmpty
                ? answer.valueChoices
                : answer.value,
            'mistakes': answer.mistakes,
            'passed': answer.isPassed,
          },
      ],
    };
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
          choiceLabels.add(JsonUtils.getString('res', choice));
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
    final choices = rawValue is List
        ? rawValue.map((value) => value.toString()).toList(growable: false)
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
  final List<String> choices;
}

/// One question's answer on an exam attempt.
///
/// Unlike a survey answer this carries a verdict: exams are graded on the
/// device against the correct-choice ids that came down with the question.
class ExamDraftAnswer {
  const ExamDraftAnswer({
    this.value,
    this.choiceIds = const [],
    this.isCorrect = false,
  });

  /// Free text, or the selected rating rendered as a string.
  final String? value;

  /// Ids of the selected choices — ids, not labels, because that is what
  /// `correctChoices` holds and what the server's answer documents store.
  final List<String> choiceIds;

  final bool isCorrect;

  bool get isEmpty => (value == null || value!.isEmpty) && choiceIds.isEmpty;
}
