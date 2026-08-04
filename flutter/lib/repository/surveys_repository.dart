import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/exam_mapper.dart';
import '../data/local/survey_mapper.dart';
import 'submissions_repository.dart';

/// Port of the individual-survey path in `SurveysRepositoryImpl.kt`.
class SurveysRepository {
  SurveysRepository(this._api, this._dao, this._examDao, this._submissions);

  final PlanetApi _api;
  final SurveyDao _dao;
  final ExamDao _examDao;
  final SubmissionsRepository _submissions;

  Stream<List<SurveyRow>> watchAll() => _dao.watchAll();
  Future<SurveyRow?> getById(String id) => _dao.getById(id);
  Future<List<SurveyQuestionRow>> questionsFor(String id) =>
      _dao.questionsFor(id);

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

  Future<SyncResult> sync({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final auth = UrlUtils.basicAuthHeader('satellite', config.pin);
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
}
