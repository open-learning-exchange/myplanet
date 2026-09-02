import 'dart:convert';

import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/server_url_mapper.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/text_utils.dart' as text;
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/exam_mapper.dart';
import '../data/local/survey_mapper.dart';
import 'submissions_repository.dart';

/// Port of the individual-survey and public-survey paths in
/// `SurveysRepositoryImpl.kt`.
class SurveysRepository {
  SurveysRepository(
    this._api,
    this._dao,
    this._examDao,
    this._submissions, {
    ServerUrlMapper? urlMapper,
  }) : _urlMapper = urlMapper ?? ServerUrlMapper();

  final PlanetApi _api;
  final SurveyDao _dao;
  final ExamDao _examDao;
  final SubmissionsRepository _submissions;
  final ServerUrlMapper _urlMapper;

  Stream<List<SurveyRow>> watchAll() => _dao.watchAll();
  Future<SurveyRow?> getById(String id) => _dao.getById(id);
  Future<List<SurveyQuestionRow>> questionsFor(String id) =>
      _dao.questionsFor(id);

  Future<List<SurveyRow>> individualSurveys() async {
    final rows = await _dao.allRows();
    return rows
        .where((row) => !row.teamShareAllowed && (row.teamId ?? '').isEmpty)
        .toList();
  }

  Future<List<SurveyRow>> teamOwnedSurveys(String teamId) async {
    if (teamId.isEmpty) return const [];
    final rows = await _dao.allRows();
    final teamSubmissionIds = await _teamSubmissionSurveyIds(teamId);
    final adoptedSourceIds = rows
        .where((row) => row.teamId == teamId)
        .map((row) => row.sourceSurveyId)
        .whereType<String>()
        .toSet();
    final visibleIds = teamSubmissionIds.difference(adoptedSourceIds);
    return rows
        .where((row) => row.teamId == teamId || visibleIds.contains(row.id))
        .toList();
  }

  Future<List<SurveyRow>> adoptableTeamSurveys(String teamId) async {
    if (teamId.isEmpty) return const [];
    final rows = await _dao.allRows();
    final excluded = <String>{
      ...await _teamSubmissionSurveyIds(teamId),
      ...rows
          .where((row) => row.teamId == teamId)
          .map((row) => row.sourceSurveyId)
          .whereType<String>(),
    };
    return rows
        .where((row) => row.teamShareAllowed && !excluded.contains(row.id))
        .toList();
  }

  Future<void> adoptSurvey({
    required String surveyId,
    required String? userId,
    String? teamId,
    bool isTeam = false,
    String? teamName,
    String planetCode = '',
    String parentCode = '',
    DateTime? now,
    String Function()? createId,
  }) async {
    final survey = await _dao.getById(surveyId);
    if (survey == null) return;
    final timestamp = (now ?? DateTime.now()).millisecondsSinceEpoch;
    if (isTeam && teamId != null && teamId.isNotEmpty) {
      final existing = await _dao.adoptedTeamSurvey(teamId, surveyId);
      if (existing == null) {
        final adoptedId = createId?.call() ?? '${surveyId}_$teamId';
        final questions = await _dao.questionsFor(surveyId);
        await _dao.upsertAll(
          [
            SurveysCompanion.insert(
              id: adoptedId,
              name: Value(
                (teamName == null || teamName.isEmpty)
                    ? survey.name
                    : '${survey.name} - $teamName',
              ),
              description: Value(survey.description),
              createdDate: Value(timestamp),
              updatedDate: Value(timestamp),
              adoptionDate: Value(timestamp),
              createdBy: Value(userId),
              totalMarks: Value(survey.totalMarks),
              passingPercentage: Value(survey.passingPercentage),
              sourcePlanet: Value(survey.sourcePlanet),
              isFromNation: Value(survey.isFromNation),
              teamId: Value(teamId),
              teamShareAllowed: const Value(false),
              sourceSurveyId: Value(surveyId),
            ),
          ],
          {
            adoptedId: [
              for (final question in questions)
                SurveyQuestionsCompanion.insert(
                  id: '$adoptedId:${question.questionId ?? question.id}',
                  surveyId: adoptedId,
                  questionId: Value(question.questionId),
                  header: Value(question.header),
                  body: Value(question.body),
                  type: Value(question.type),
                  choices: Value(question.choices),
                  required: Value(question.required),
                  position: question.position,
                ),
            ],
          },
        );
      }
    }

    if (userId == null || userId.isEmpty) return;
    final candidates = isTeam && teamId != null && teamId.isNotEmpty
        ? await _submissions.submissionsForTeam(teamId)
        : await _submissions.submissionsForUserWithoutTeam(userId);
    final exists = candidates.any(
      (row) =>
          row.userId == userId && row.parentId == surveyId && row.status == '',
    );
    if (exists) return;
    final parentJson = jsonEncode({
      '_id': survey.id,
      'name': survey.name,
      'courseId': '',
      'sourcePlanet': survey.sourcePlanet ?? '',
      'teamShareAllowed': survey.teamShareAllowed,
      'noOfQuestions': (await _dao.questionsFor(surveyId)).length,
      'isFromNation': survey.isFromNation,
    });
    final userJson = jsonEncode({
      'doc': {
        '_id': userId,
        'userId': userId,
        'teamPlanetCode': planetCode,
        'status': 'active',
        'type': 'team',
        'createdBy': userId,
      },
      if (isTeam && teamId != null) 'membershipDoc': {'teamId': teamId},
    });
    await _submissions.createSurveyAdoptionSubmission(
      id: createId?.call() ?? '${surveyId}_${userId}_adoption',
      surveyId: surveyId,
      userId: userId,
      parentJson: parentJson,
      userJson: userJson,
      source: planetCode,
      parentCode: parentCode,
      teamId: isTeam ? teamId : null,
      now: DateTime.fromMillisecondsSinceEpoch(timestamp),
    );
  }

  Future<Set<String>> _teamSubmissionSurveyIds(String teamId) async {
    final submissions = await _submissions.submissionsForTeam(teamId);
    return submissions
        .map((row) => _parentSurveyId(row.parent))
        .whereType<String>()
        .toSet();
  }

  String? _parentSurveyId(String? parent) {
    if (parent == null || parent.isEmpty) return null;
    try {
      final decoded = jsonDecode(parent);
      if (decoded is! Map<String, dynamic>) return null;
      final id = decoded['_id'];
      return id is String && id.isNotEmpty ? id : null;
    } on FormatException {
      return null;
    }
  }

  Future<String?> submitResponse(
    String surveyId,
    String userId,
    Map<String, SubmissionDraftAnswer> answers,
  ) async {
    final survey = await _dao.getById(surveyId);
    if (survey == null) return null;
    final questions = await _dao.questionsFor(surveyId);
    return _submissions.createSurveyDraft(
      survey: survey,
      questions: questions,
      userId: userId,
      answers: answers,
    );
  }

  /// Resumes a pending survey submission: replaces the answer rows on the
  /// existing submission (rather than creating a new one) and marks it
  /// complete. Port of `ExamTakingFragment` reusing the `sub` loaded by
  /// `BaseExamFragment.checkId` when `isMySurvey` is true.
  Future<String?> updateSurveyResponse(
    String submissionId, {
    required Map<String, SubmissionDraftAnswer> answers,
  }) async {
    final submission = await _submissions.getById(submissionId);
    if (submission == null) return null;
    final surveyId = submission.parentId;
    if (surveyId == null) return null;
    final questions = await _dao.questionsFor(surveyId);
    await _submissions.updateSurveyAnswers(
      submissionId: submissionId,
      questions: questions,
      answers: answers,
    );
    return submissionId;
  }

  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final auth = UrlUtils.authHeader(config);
    final count = await _api.getJsonObject(
      '$dbUrl/exams/_all_docs?limit=0',
      authHeader: auth,
    );
    if (count is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(count));
    }
    final total = JsonUtils.getInt('total_rows', count.data);
    final ids = <String>[];
    final examIds = <String>[];
    var skip = 0;
    var complete = true;
    final sizer = AdaptiveBatchProcessor(initialSize: 100);
    while (skip < total) {
      final limit = sizer.currentSize;
      final timer = Stopwatch()..start();
      final result = await _api.getJsonObject(
        '$dbUrl/exams/_all_docs?include_docs=true&limit=$limit&skip=$skip',
        authHeader: auth,
      );
      timer.stop();
      if (result is! NetworkSuccess<Map<String, dynamic>>) {
        sizer.recordFailure();
        return SyncFailed(describeNetworkFailure(result));
      }
      sizer.recordSuccess(timer.elapsedMilliseconds);
      final rows = result.data['rows'];
      if (rows is! List || rows.isEmpty) {
        complete = false;
        break;
      }
      final surveys = <SurveysCompanion>[];
      final questions = <String, List<SurveyQuestionsCompanion>>{};
      final exams = <ExamsCompanion>[];
      final examQuestions = <String, List<ExamQuestionsCompanion>>{};
      for (final row in rows.whereType<Map<String, dynamic>>()) {
        final doc = JsonUtils.getObject('doc', row);
        if (doc == null) continue;
        // The `exams` database holds both: surveys are `type: 'surveys'` and
        // graded course exams are `type: 'exam'`. Each mapper returns null for
        // the other's documents, so one pass over the page feeds both tables —
        // the exam documents were already on the wire and were being discarded.
        final mapped = SurveyMapper.fromDoc(doc);
        if (mapped != null) {
          final id = mapped.survey.id.value;
          ids.add(id);
          surveys.add(mapped.survey);
          questions[id] = mapped.questions;
          continue;
        }
        final exam = ExamMapper.fromDoc(doc);
        if (exam == null) continue;
        final examId = exam.exam.id.value;
        examIds.add(examId);
        exams.add(exam.exam);
        examQuestions[examId] = exam.questions;
      }
      await _dao.upsertAll(surveys, questions);
      await _examDao.upsertAll(exams, examQuestions);
      skip += rows.length;
      onProgress?.call(
        SyncProgress(completed: skip.clamp(0, total), total: total),
      );
    }
    if (total == 0 || complete) {
      await _dao.deleteNotIn(ids);
      await _examDao.deleteNotIn(examIds);
    }
    return SyncComplete(ids.length + examIds.length);
  }

  // Public surveys ----------------------------------------------------------------

  /// Fetches a public-access survey from the server's public API, trying the
  /// configured alternative mirror if the primary is unreachable.
  ///
  /// The response is either the survey document directly or wrapped under the
  /// `survey` key; this matches `SurveysRepositoryImpl.fetchPublicSurvey`.
  Future<Map<String, dynamic>?> fetchPublicSurvey(
    String baseUrl,
    String teamId,
    String surveyId,
  ) async {
    final mapping = _urlMapper.processUrl(baseUrl);
    final doc = await _fetchPublicSurveyFrom(
      mapping.primaryUrl,
      teamId,
      surveyId,
    );
    if (doc != null) return doc;
    if (mapping.alternativeUrl case final alt?) {
      return _fetchPublicSurveyFrom(alt, teamId, surveyId);
    }
    return null;
  }

  Future<Map<String, dynamic>?> _fetchPublicSurveyFrom(
    String baseUrl,
    String teamId,
    String surveyId,
  ) async {
    if (baseUrl.isEmpty) return null;
    final url =
        '${_trimTrailingSlash(baseUrl)}/api/public/surveys/$teamId/$surveyId';
    final result = await _api.getJsonObject(url);
    if (result is! NetworkSuccess<Map<String, dynamic>>) return null;
    final data = result.data;
    final survey = data['survey'];
    if (survey is Map<String, dynamic>) return survey;
    return data;
  }

  /// Stores a survey fetched from the public API so the local survey form can
  /// read it. Returns the persisted row, or `null` if the document was not a
  /// valid survey.
  Future<SurveyRow?> saveSurveyFromPublicApi(Map<String, dynamic> doc) async {
    final mapped = SurveyMapper.fromDoc(doc);
    if (mapped == null) return null;
    final surveyId = mapped.survey.id.value;
    await _dao.upsertAll([mapped.survey], {surveyId: mapped.questions});
    return _dao.getById(surveyId);
  }

  /// Submits a completed anonymous public survey response to the server. The
  /// [submissionId] must already have been marked complete with the respondent's
  /// profile (via `UserInformationScreen` / `markSubmissionComplete`).
  ///
  /// This is the live attempt. It is the whole story in the Kotlin, where a
  /// failed post loses the answers; here `PublicSurveyUploader` keeps them, so a
  /// caller that gets `false` back should queue rather than report a loss.
  Future<bool> submitPublicSurvey({
    required String baseUrl,
    required String teamId,
    required String surveyId,
    required String submissionId,
  }) async {
    final body = await buildPublicSubmissionBody(
      surveyId: surveyId,
      submissionId: submissionId,
    );
    if (body == null) return false;
    return _submitPublicSurveyTo(baseUrl, teamId, surveyId, body);
  }

  /// The document the public API expects, or null when the submission is gone.
  ///
  /// Split out of [submitPublicSurvey] so the same body can be stored in the
  /// outbox and replayed verbatim. It is built once, at send time, from rows
  /// that never change afterwards — an anonymous respondent cannot come back and
  /// edit an answer sheet — so replaying it is exactly right.
  Future<Map<String, dynamic>?> buildPublicSubmissionBody({
    required String surveyId,
    required String submissionId,
  }) async {
    final submission = await _submissions.getById(submissionId);
    if (submission == null) return null;

    final answers = await _buildPublicAnswers(surveyId, submissionId);
    final userJson = submission.user;
    Map<String, dynamic>? respondent;
    if (userJson != null && userJson.isNotEmpty && userJson != '{}') {
      try {
        respondent = jsonDecode(userJson) as Map<String, dynamic>;
      } on FormatException {
        respondent = null;
      }
    }
    _sanitizeRespondent(respondent);

    final body = <String, dynamic>{'answers': answers};
    if (respondent != null && respondent.isNotEmpty) {
      body['user'] = respondent;
    }
    return body;
  }

  Future<List<dynamic>> _buildPublicAnswers(
    String surveyId,
    String submissionId,
  ) async {
    final questions = await _dao.questionsFor(surveyId);
    final answers = await _submissions.answersFor(submissionId);
    final byQuestion = <String, SubmissionAnswerRow>{};
    for (final answer in answers) {
      byQuestion[answer.questionId ?? answer.id] = answer;
    }

    final payload = <dynamic>[];
    for (final question in questions) {
      final key = question.questionId ?? question.id;
      final answer = byQuestion[key];
      final choices = answer?.valueChoices ?? const [];
      if (_typeEquals(question.type, 'selectMultiple')) {
        payload.add(
          choices.map(_choiceObject).whereType<Map<String, dynamic>>().toList(),
        );
      } else if (_typeEquals(question.type, 'select') && choices.isNotEmpty) {
        payload.add(_choiceObject(choices.first) ?? choices.first);
      } else {
        payload.add(answer?.value ?? '');
      }
    }
    return payload;
  }

  Map<String, dynamic>? _choiceObject(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map<String, dynamic>) return decoded;
    } on FormatException {
      // Fall through and send the raw string.
    }
    return null;
  }

  /// The public API validates `age` as an integer, while the profile carries
  /// it as the string `UserSurveyProfile.toJson` writes. Coerce it, and coerce
  /// the `birthYear`/`dob` pair a submission stored by an earlier build still
  /// carries — the screen writes `age`/`birthDate` now, but a row completed
  /// before that fix can still be waiting in the outbox.
  void _sanitizeRespondent(Map<String, dynamic>? user) {
    if (user == null) return;
    final age = _computeAge(user);
    if (age != null) {
      user['age'] = age;
    } else if (user.containsKey('age')) {
      final parsed = int.tryParse(user['age'].toString().trim());
      if (parsed != null) {
        user['age'] = parsed;
      } else {
        user.remove('age');
      }
    }
    user.remove('birthYear');
    if (user.containsKey('dob')) {
      final dob = user['dob'];
      if (dob is String && dob.isNotEmpty) {
        user['birthDate'] = dob;
      }
      user.remove('dob');
    }
  }

  int? _computeAge(Map<String, dynamic> user) {
    final birthYear = user['birthYear'];
    if (birthYear is String && birthYear.isNotEmpty) {
      final year = int.tryParse(birthYear.trim());
      if (year != null) {
        return DateTime.now().year - year;
      }
    }
    final dob = user['dob'];
    if (dob is String && dob.isNotEmpty) {
      final parsed = DateTime.tryParse(dob);
      if (parsed != null) {
        final now = DateTime.now();
        var age = now.year - parsed.year;
        if (now.month < parsed.month ||
            (now.month == parsed.month && now.day < parsed.day)) {
          age--;
        }
        return age;
      }
    }
    final ageValue = user['age'];
    if (ageValue is int) return ageValue;
    if (ageValue is String && ageValue.isNotEmpty) {
      return int.tryParse(ageValue.trim());
    }
    return null;
  }

  Future<bool> _submitPublicSurveyTo(
    String baseUrl,
    String teamId,
    String surveyId,
    Map<String, dynamic> body,
  ) async {
    final mapping = _urlMapper.processUrl(baseUrl);
    if (mapping.primaryUrl.isEmpty) return false;
    if (await _postPublicSurvey(mapping.primaryUrl, teamId, surveyId, body)) {
      return true;
    }
    if (mapping.alternativeUrl case final alt?) {
      return _postPublicSurvey(alt, teamId, surveyId, body);
    }
    return false;
  }

  /// The public API's submissions endpoint. Shared with
  /// `PublicSurveyUploader`, which stores it on the outbox row.
  ///
  /// No credentials appear in it: this is the *public* API, reached by a
  /// respondent who has none. That is also why the drain sends this one upload
  /// type without an `Authorization` header.
  static String publicSubmissionsUrl(
    String baseUrl,
    String teamId,
    String surveyId,
  ) =>
      '${_trimTrailingSlash(baseUrl)}/api/public/surveys/$teamId/$surveyId/submissions';

  Future<bool> _postPublicSurvey(
    String baseUrl,
    String teamId,
    String surveyId,
    Map<String, dynamic> body,
  ) async {
    final url = publicSubmissionsUrl(baseUrl, teamId, surveyId);
    final result = await _api.sendJsonDynamic(url, body: body, method: 'POST');
    return result is NetworkSuccess<dynamic>;
  }
}

String _trimTrailingSlash(String url) {
  if (url.isEmpty) return url;
  if (url.endsWith('/')) return url.substring(0, url.length - 1);
  return url;
}

bool _typeEquals(String? type, String other) =>
    type?.toLowerCase() == other.toLowerCase();

/// Port of `SurveysViewModel.filter` — the same ranked algorithm
/// `ResourcesSearchUtils.searchList` / `CoursesRepositoryImpl.search` use:
/// titles whose normalized form *starts with* the whole query rank ahead of
/// titles that merely *contain every whitespace-separated word*. Only `name`
/// is searched (the Kotlin never searches `description`), and accents fold via
/// `normalizeText` so "cafe" finds "Café".
List<SurveyRow> searchSurveys(List<SurveyRow> items, String query) {
  final trimmed = query.trim();
  if (trimmed.isEmpty) return List.of(items);

  final queryParts = trimmed.split(RegExp(r'\s+')).where((p) => p.isNotEmpty);
  final normalizedParts = queryParts.map(text.normalizeText).toList();
  final normalizedQuery = text.normalizeText(trimmed);

  final startsWithQuery = <SurveyRow>[];
  final containsQuery = <SurveyRow>[];
  for (final item in items) {
    final name = item.name;
    if (name == null || name.isEmpty) continue;
    final title = text.normalizeText(name);
    if (title.startsWith(normalizedQuery)) {
      startsWithQuery.add(item);
    } else if (normalizedParts.every((part) => title.contains(part))) {
      containsQuery.add(item);
    }
  }
  return startsWithQuery..addAll(containsQuery);
}

/// Port of `SurveysViewModel.getSortDate`: an adopted survey (one with a
/// `sourceSurveyId`) sorts by its `adoptionDate` when set, falling back to
/// `createdDate`; a native survey sorts by `createdDate`.
int surveySortDate(SurveyRow survey) {
  if (survey.sourceSurveyId != null && survey.adoptionDate > 0) {
    return survey.adoptionDate;
  }
  return survey.createdDate;
}
