import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/course_mapper.dart';
import '../data/local/exam_mapper.dart';
import '../data/local/survey_mapper.dart';
import 'shelf_repository.dart';

/// Port of the courses read/sync surface of
/// `repository/CoursesRepositoryImpl.kt` plus the `courses` table pull that
/// `services/sync/TransactionSyncManager.kt` drives.
///
/// The Kotlin interface has 40+ methods; this covers the list, detail, search,
/// filter and shelf-membership subset the courses UI needs. Progress tracking,
/// exams, surveys, certification and ratings arrive with their own packages —
/// see `docs/kotlin-to-flutter-migration.md`.
///
/// Offline-first on the same terms as `ResourcesRepository`: reads come from
/// SQLite and never touch the network; [sync] refills the table and Drift pushes
/// the change into any open stream.
class CoursesRepository {
  CoursesRepository(
    this._api,
    this._dao,
    this._removedLogDao,
    this._examDao,
    this._surveyDao,
  );

  /// Courses carry embedded steps, so documents are much larger than resource
  /// documents — a smaller starting page keeps the first batch responsive on a
  /// weak link. The adaptive sizer takes over from there.
  static const int initialBatchSize = 50;

  final PlanetApi _api;
  final CourseDao _dao;
  final RemovedLogDao _removedLogDao;

  /// A course document carries its steps' tests and surveys inline, so the
  /// `courses` walk writes the exams and surveys tables too — see
  /// [ExamMapper.fromCourseDoc]. Kotlin's `upsertRoomCoursesFromSync` does the
  /// same, through `examDao`/`questionDao` (`CoursesRepositoryImpl.kt:650-651`).
  final ExamDao _examDao;
  final SurveyDao _surveyDao;

  /// Reactive, offline-first course list.
  Stream<List<CourseRow>> watchCourses({
    String? query,
    String? shelfUserId,
    String? gradeLevel,
    String? subjectLevel,
  }) {
    return _dao.watchCourses(
      query: query,
      shelfUserId: shelfUserId,
      gradeLevel: gradeLevel,
      subjectLevel: subjectLevel,
    );
  }

  Stream<CourseRow?> watchCourse(String courseId) => _dao.watchCourse(courseId);

  Stream<List<CourseStepRow>> watchSteps(String courseId) =>
      _dao.watchSteps(courseId);

  Future<CourseRow?> getCourseById(String courseId) => _dao.getById(courseId);

  /// Port of `CoursesRepositoryImpl.getCourseTitleById`. The challenge dialog
  /// uses it to label the course-status row.
  Future<String?> getCourseTitleById(String courseId) async =>
      (await _dao.getById(courseId))?.courseTitle;

  Future<List<CourseStepRow>> getCourseSteps(String courseId) =>
      _dao.getSteps(courseId);

  Future<int> localCount() => _dao.count();

  Future<List<String>> gradeLevels() => _dao.distinctGradeLevels();

  Future<List<String>> subjectLevels() => _dao.distinctSubjectLevels();

  /// Reactive filter options, independent of the active filter.
  Stream<List<String>> watchGradeLevels() => _dao.watchDistinctGradeLevels();

  Stream<List<String>> watchSubjectLevels() =>
      _dao.watchDistinctSubjectLevels();

  /// Port of `CoursesRepositoryImpl.isMyCourse`.
  Future<bool> isMyCourse(String courseId, String userId) =>
      _dao.isMyCourse(courseId, userId);

  /// Port of `joinCourse` / `leaveCourse`.
  ///
  /// Writes local shelf membership and records (or clears) the removal so the
  /// shelf upload can tell a deliberate "leave" from a document the server
  /// simply still lists. Pushing the shelf itself is [ShelfRepository.upload];
  /// this stays a local, synchronous-feeling write so the UI responds offline.
  Future<void> setShelfMembership(
    String courseId,
    String userId, {
    required bool joined,
  }) async {
    // Both writes together: if only one landed, the local shelf and the removal
    // log would disagree and the next upload would push the wrong document.
    await _dao.transaction(() async {
      await _dao.setShelfMembership(courseId, userId, joined: joined);

      if (joined) {
        await _removedLogDao.clear(
          type: ShelfRepository.coursesType,
          userId: userId,
          docId: courseId,
        );
      } else {
        await _removedLogDao.record(
          type: ShelfRepository.coursesType,
          userId: userId,
          docId: courseId,
        );
      }
    });
  }

  /// Port of the `courses` table pull.
  ///
  /// Same shape as the resources pull: count with `?limit=0`, then walk
  /// `?include_docs=true&limit&skip` pages, upserting each page's courses and
  /// their embedded steps together.
  Future<SyncResult> sync({
    required ServerConfig config,
    String? shelfId,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.authHeader(config);

    final countResult = await _api.getJsonObject(
      '$dbUrl/courses/_all_docs?limit=0',
      authHeader: authHeader,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }

    final totalRows = JsonUtils.getInt('total_rows', countResult.data);
    if (totalRows == 0) {
      await _dao.deleteNotIn(const []);
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: initialBatchSize);
    final savedIds = <String>[];
    var skip = 0;
    // A short page means the server changed under us mid-walk. `savedIds` is
    // then only a prefix of what exists, so the cleanup below must not run —
    // it would delete local rows the server still has.
    var walkedEveryPage = true;

    while (skip < totalRows) {
      final batchSize = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();

      final pageResult = await _api.getJsonObject(
        '$dbUrl/courses/_all_docs?include_docs=true&limit=$batchSize&skip=$skip',
        authHeader: authHeader,
      );
      stopwatch.stop();

      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        return SyncFailed(describeNetworkFailure(pageResult));
      }
      batchSizer.recordSuccess(stopwatch.elapsedMilliseconds);

      final rows = pageResult.data['rows'];
      if (rows is! List || rows.isEmpty) {
        walkedEveryPage = false;
        break;
      }

      final courseRows = <CoursesCompanion>[];
      final stepRows = <CourseStepsCompanion>[];
      final examRows = <ExamsCompanion>[];
      final examQuestionRows = <String, List<ExamQuestionsCompanion>>{};
      final surveyRows = <SurveysCompanion>[];
      final surveyQuestionRows = <String, List<SurveyQuestionsCompanion>>{};

      // Preserve shelf membership already recorded for these courses. Fetched
      // once per page rather than once per row — a large sync would otherwise
      // issue thousands of single-row reads.
      final docs = <Map<String, dynamic>>[
        for (final row in rows)
          if (row is Map<String, dynamic>) ?JsonUtils.getObject('doc', row),
      ];
      final existingById = {
        for (final course in await _dao.getByIds([
          for (final doc in docs)
            if (JsonUtils.getString('_id', doc) case final id
                when id.isNotEmpty)
              id,
        ]))
          course.id: course,
      };

      for (final doc in docs) {
        final courseId = JsonUtils.getString('_id', doc);
        final existing = existingById[courseId];

        final parsed = CourseMapper.fromDoc(
          doc,
          existingUserIds: existing?.userId ?? const [],
          shelfId: shelfId,
        );
        if (parsed == null) continue;

        courseRows.add(parsed.course);
        stepRows.addAll(parsed.steps);
        savedIds.add(parsed.course.id.value);

        for (final mapping in ExamMapper.fromCourseDoc(
          doc,
          stepIdFor: CourseMapper.stepIdFor,
        )) {
          examRows.add(mapping.exam);
          examQuestionRows[mapping.exam.id.value] = mapping.questions;
        }
        for (final mapping in SurveyMapper.fromCourseDoc(
          doc,
          stepIdFor: CourseMapper.stepIdFor,
        )) {
          surveyRows.add(mapping.survey);
          surveyQuestionRows[mapping.survey.id.value] = mapping.questions;
        }
      }

      if (courseRows.isNotEmpty) {
        await _dao.upsertAll(courseRows, stepRows);
      }
      // Written after the courses, so a step row always exists by the time an
      // exam claims to belong to it. Neither table is pruned here: the `exams`
      // database walk owns their `deleteNotIn`, and a course test is a document
      // of that database too, so it is in that walk's keep set.
      if (examRows.isNotEmpty) {
        await _examDao.upsertAll(examRows, examQuestionRows);
      }
      if (surveyRows.isNotEmpty) {
        await _surveyDao.upsertAll(surveyRows, surveyQuestionRows);
      }

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
    }

    if (walkedEveryPage && savedIds.isNotEmpty) {
      await _dao.deleteNotIn(savedIds);
    }

    return SyncComplete(savedIds.length);
  }
}
