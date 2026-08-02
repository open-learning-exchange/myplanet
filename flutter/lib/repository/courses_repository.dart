import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/course_mapper.dart';

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
  CoursesRepository(this._api, this._dao);

  /// Courses carry embedded steps, so documents are much larger than resource
  /// documents — a smaller starting page keeps the first batch responsive on a
  /// weak link. The adaptive sizer takes over from there.
  static const int initialBatchSize = 50;

  final PlanetApi _api;
  final CourseDao _dao;

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

  Future<List<CourseStepRow>> getCourseSteps(String courseId) =>
      _dao.getSteps(courseId);

  Future<int> localCount() => _dao.count();

  Future<List<String>> gradeLevels() => _dao.distinctGradeLevels();

  Future<List<String>> subjectLevels() => _dao.distinctSubjectLevels();

  /// Port of `CoursesRepositoryImpl.isMyCourse`.
  Future<bool> isMyCourse(String courseId, String userId) =>
      _dao.isMyCourse(courseId, userId);

  /// Port of `joinCourse` / `leaveCourse`.
  ///
  /// Local-only: this writes shelf membership to SQLite but does **not** push
  /// the shelf document back to CouchDB, because the upload framework
  /// (`services/upload/`) is not ported yet. Until it is, a join made here is
  /// lost on the next full resync.
  Future<void> setShelfMembership(
    String courseId,
    String userId, {
    required bool joined,
  }) {
    return _dao.setShelfMembership(courseId, userId, joined: joined);
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
    final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);

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

      for (final row in rows) {
        if (row is! Map<String, dynamic>) continue;
        final doc = JsonUtils.getObject('doc', row);
        if (doc == null) continue;

        final courseId = JsonUtils.getString('_id', doc);
        // Preserve shelf membership already recorded for this course.
        final existing = courseId.isEmpty ? null : await _dao.getById(courseId);

        final parsed = CourseMapper.fromDoc(
          doc,
          existingUserIds: existing?.userId ?? const [],
          shelfId: shelfId,
        );
        if (parsed == null) continue;

        courseRows.add(parsed.course);
        stepRows.addAll(parsed.steps);
        savedIds.add(parsed.course.id.value);
      }

      if (courseRows.isNotEmpty) {
        await _dao.upsertAll(courseRows, stepRows);
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
