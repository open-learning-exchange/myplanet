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
  const SubmissionsRepository(this._api, this._dao);

  static const int initialBatchSize = 100;

  final PlanetApi _api;
  final SubmissionDao _dao;

  Stream<List<SubmissionRow>> watchForUser(String userId) =>
      _dao.watchForUser(userId);

  Stream<SubmissionRow?> watchById(String id) => _dao.watchById(id);
  Stream<List<SubmissionAnswerRow>> watchAnswers(String submissionId) =>
      _dao.watchAnswers(submissionId);
  Stream<List<SubmissionQuestionRow>> watchQuestions(String submissionId) =>
      _dao.watchQuestions(submissionId);
  Future<SubmissionRow?> getById(String id) => _dao.getById(id);
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

  Future<List<SubmissionRow>> pendingUploads(String userId) =>
      _dao.pendingUploads(userId);

  Future<void> markUploaded(String id, String couchId, String rev) async {
    await _dao.markUploaded(id, couchId, rev);
  }

  Future<Map<String, dynamic>> serialize(SubmissionRow row) async {
    final answers = await _dao.watchAnswers(row.id).first;
    return {
      'type': row.type,
      'userId': row.userId,
      'user': row.user,
      'parentId': row.parentId,
      'parent': row.parent,
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
    final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);
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
