import 'package:drift/drift.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/certification_mapper.dart';
import '../data/local/course_progress_mapper.dart';

/// Port of `repository/ProgressRepository.kt` /
/// `repository/CoursesRepositoryImpl.kt`'s progress subset.
///
/// Owns the read paths the course-progress UI and the dashboard's
/// "completed courses" stars depend on, the local write paths the take-course
/// and exam screens use to record progress, and the merge path the sync calls
/// when it pulls `courses_progress` documents from CouchDB.
///
/// The "current progress" / "completed courses" calculations follow the
/// Kotlin's `CoursesRepositoryImpl.getCourseProgress`/`getCompletedCourses`
/// shape closely enough to share the same bugs, deliberately: a step is
/// "current" when no progress row marks it passed, and a course is "complete"
/// when every one of its steps is passed. Step-exam submissions are read only
/// to display the running mistake count, matching the Kotlin grid.
class ProgressRepository {
  ProgressRepository(
    this._api,
    this._courseDao,
    this._progressDao,
    this._examDao,
    this._submissionDao,
    this._certificationDao,
  );

  final PlanetApi _api;
  final CourseDao _courseDao;
  final CourseProgressDao _progressDao;
  final ExamDao _examDao;
  final SubmissionDao _submissionDao;
  final CertificationDao _certificationDao;

  /// All progress rows for [userId] across [courseIds], keyed for lookup.
  ///
  /// Mirrors `ProgressRepositoryImpl.getCourseProgress(userId, courseIds)`,
  /// but returns the rows rather than a `Map<String, List<CourseProgress>>` —
  /// the callers here group in Dart.
  Future<List<CourseProgressRow>> progressForUser(
    String? userId,
    List<String> courseIds,
  ) => _progressDao.getByUserAndCourseIds(userId, courseIds);

  /// Port of `CoursesRepositoryImpl.getCourseProgress(courseId, userId)`.
  ///
  /// Returns, per step, the progress row (if any) and the running total of
  /// exam-question mistakes the user has accrued on that step's exam. A step
  /// with no exam contributes no mistakes; a step with an exam the user has
  /// not attempted still appears, with `progress = null` and `mistakes = 0`.
  Future<List<CourseStepProgress>> courseProgress(
    String courseId,
    String? userId,
  ) async {
    final steps = await _courseDao.getSteps(courseId);
    final progressRows = await _progressDao.getByUserAndCourse(
      userId,
      courseId,
    );
    final progressByStep = {for (final row in progressRows) row.stepNum: row};

    final stepExams = await _examDao.getByStepIds([
      for (final s in steps) s.id,
    ]);
    final examByStep = {for (final exam in stepExams) exam.stepId: exam};
    final examIds = [for (final exam in stepExams) exam.id];
    final questionCountByExam = <String, int>{};
    for (final q in await _examDao.questionsForExams(examIds)) {
      questionCountByExam[q.examId] = (questionCountByExam[q.examId] ?? 0) + 1;
    }

    final submissions = await _submissionDao.getExamSubmissionsByUser(userId);
    final submissionIds = [for (final s in submissions) s.id];
    final answers = await _submissionDao.answersForSubmissions(submissionIds);
    final mistakesByExam = _totalMistakesByExam(submissions, answers);

    return [
      for (final step in steps)
        CourseStepProgress(
          step: step,
          progress: progressByStep[step.stepIndex + 1],
          questionCount: _questionCountForStep(
            step,
            examByStep,
            questionCountByExam,
          ),
          totalMistakes: _mistakesForStep(step, examByStep, mistakesByExam),
        ),
    ];
  }

  Map<String, int> _totalMistakesByExam(
    List<SubmissionRow> submissions,
    List<SubmissionAnswerRow> answers,
  ) {
    // A submission's `parentId` is the exam id with `@user`/`@timestamp` style
    // suffixes the Kotlin attaches; the leading segment is the exam id.
    final submissionByExam = <String, List<String>>{};
    for (final s in submissions) {
      final examId = _examIdFromParent(s.parentId);
      if (examId != null) {
        submissionByExam.putIfAbsent(examId, () => <String>[]).add(s.id);
      }
    }
    final bySubmission = <String, List<SubmissionAnswerRow>>{};
    for (final a in answers) {
      bySubmission
          .putIfAbsent(a.submissionId, () => <SubmissionAnswerRow>[])
          .add(a);
    }
    final result = <String, int>{};
    for (final entry in submissionByExam.entries) {
      var total = 0;
      for (final id in entry.value) {
        for (final a in bySubmission[id] ?? const <SubmissionAnswerRow>[]) {
          total += a.mistakes;
        }
      }
      result[entry.key] = total;
    }
    return result;
  }

  /// The leading `@`-delimited segment of `parentId`, matching the Kotlin's
  /// `split("@")[0]` used to map a submission back to its exam.
  String? _examIdFromParent(String? parentId) {
    if (parentId == null || parentId.isEmpty) return null;
    final at = parentId.indexOf('@');
    return at <= 0 ? parentId : parentId.substring(0, at);
  }

  int _questionCountForStep(
    CourseStepRow step,
    Map<String?, ExamRow> examByStep,
    Map<String, int> questionCountByExam,
  ) {
    final exam = examByStep[step.id];
    if (exam == null) return 0;
    return questionCountByExam[exam.id] ?? 0;
  }

  int _mistakesForStep(
    CourseStepRow step,
    Map<String?, ExamRow> examByStep,
    Map<String, int> mistakesByExam,
  ) {
    final exam = examByStep[step.id];
    if (exam == null) return 0;
    return mistakesByExam[exam.id] ?? 0;
  }

  /// Port of `ProgressRepositoryImpl.getCompletedCourses(userId)`.
  ///
  /// A course is complete when every step is `passed` and the course has at
  /// least one step. The Kotlin counts **unique** passed `stepNum`s
  /// (`passedStepNumbers.toSet()`, "matches web: step.passed === true"):
  /// sync can deliver several progress rows for the same step — one per
  /// device or attempt — so counting rows instead of steps would let a
  /// twice-passed step 1 complete a two-step course.
  Future<Set<String>> completedCourseIds(String? userId) async {
    final shelf = await _courseDao.coursesOnShelf(userId ?? '');
    if (shelf.isEmpty) return <String>{};
    final courseIds = [for (final c in shelf) c.id];
    final stepCounts = <String, int>{};
    for (final course in shelf) {
      stepCounts[course.id] = (await _courseDao.getSteps(course.id)).length;
    }
    final progress = await _progressDao.getByUserAndCourseIds(
      userId,
      courseIds,
    );
    final passedStepsByCourse = <String, Set<int>>{};
    for (final row in progress) {
      if (row.passed) {
        passedStepsByCourse
            .putIfAbsent(row.courseId ?? '', () => <int>{})
            .add(row.stepNum);
      }
    }
    final completed = <String>{};
    for (final course in shelf) {
      final stepCount = stepCounts[course.id] ?? 0;
      if (stepCount == 0) continue;
      if ((passedStepsByCourse[course.id]?.length ?? 0) >= stepCount) {
        completed.add(course.id);
      }
    }
    return completed;
  }

  /// Port of `ProgressRepositoryImpl.getCompletedCourses(userId)` — the
  /// completed-course list the home dashboard renders as a star badge row.
  ///
  /// Returns `(courseId, courseTitle)` pairs, unlike [completedCourseIds]
  /// (which the courses screen's progress filter reads and which therefore
  /// omits the title). The Kotlin guards each entry with `hasValidId` and
  /// `hasValidTitle` before showing a badge, so a course whose title never
  /// synced does not appear here — `BellDashboardFragment.showBadges` would
  /// otherwise render a star with an empty content description and no label.
  /// [completedCourseIds] deliberately drops those guards because its callers
  /// (the progress filter) only need the id; keep the two in step if those
  /// guards ever change upstream.
  Future<List<CourseCompletion>> completedCourses(String? userId) async {
    final shelf = await _courseDao.coursesOnShelf(userId ?? '');
    if (shelf.isEmpty) return const <CourseCompletion>[];
    final progress = await _progressDao.getByUserAndCourseIds(userId, [
      for (final c in shelf) c.id,
    ]);
    final passedStepsByCourse = <String, Set<int>>{};
    for (final row in progress) {
      if (row.passed) {
        passedStepsByCourse
            .putIfAbsent(row.courseId ?? '', () => <int>{})
            .add(row.stepNum);
      }
    }
    final completed = <CourseCompletion>[];
    for (final course in shelf) {
      final id = course.id;
      final title = course.courseTitle;
      if (id.isEmpty || title == null || title.trim().isEmpty) continue;
      final stepCount = await _courseDao.getSteps(id).then((s) => s.length);
      if (stepCount == 0) continue;
      if ((passedStepsByCourse[id]?.length ?? 0) >= stepCount) {
        completed.add(CourseCompletion(courseId: id, courseTitle: title));
      }
    }
    return completed;
  }

  /// Port of `ProgressRepositoryImpl.getCurrentProgress` — the take-course
  /// progress bar.
  ///
  /// Counts the contiguous run of steps **from step 1** that have a progress
  /// row, **ignoring `passed`** — a step the user merely opened counts as
  /// "current". This deliberately diverges from [courseProgress], whose grid
  /// shows `passed`; the bar measures how far the user has reached, not what
  /// they have passed.
  Future<int> getCurrentProgress(
    List<CourseStepRow> steps,
    String? userId,
    String? courseId,
  ) async {
    final stepsSize = steps.length;
    final progresses = await _progressDao.getByUserAndCourse(userId, courseId);
    final completed = List<bool>.filled(stepsSize + 1, false);
    for (final progress in progresses) {
      final stepNum = progress.stepNum;
      if (stepNum >= 1 && stepNum <= stepsSize) {
        completed[stepNum] = true;
      }
    }
    var i = 1;
    while (i <= stepsSize && completed[i]) {
      i++;
    }
    return i - 1;
  }

  /// Port of `ProgressRepositoryImpl.getProgressRecords`.
  Future<List<CourseProgressRow>> getProgressRecords(String? userId) =>
      _progressDao.getByUser(userId);

  /// Port of `ProgressRepositoryImpl.getCourseProgress(courseIds, userId)` —
  /// the list-view progress map the courses screen's progress filter and the
  /// "my progress" grid read.
  ///
  /// Returns `{courseId: CourseProgressSummary}` where `max` is the course's
  /// step count and `current` is the contiguous run of steps **from step 1**
  /// that have a progress row, **ignoring `passed`** — a step the user merely
  /// opened counts as "current". This matches the Kotlin's
  /// `calculateCurrentProgress`, which marks `completed[stepNum] = true` for
  /// any progress row regardless of its `passed` flag, and deliberately differs
  /// from [completedCourseIds] (which requires `passed`). A course with no
  /// steps still appears, with `max = 0` and `current = 0`.
  Future<Map<String, CourseProgressSummary>> courseProgressSummary(
    List<String> courseIds,
    String? userId,
  ) async {
    if (courseIds.isEmpty) return const {};
    final stepCounts = await _courseDao.stepCountsByCourseIds(courseIds);
    final progress = await _progressDao.getByUserAndCourseIds(
      userId,
      courseIds,
    );
    final byCourse = <String, List<CourseProgressRow>>{};
    for (final row in progress) {
      final cid = row.courseId;
      if (cid != null) {
        byCourse.putIfAbsent(cid, () => <CourseProgressRow>[]).add(row);
      }
    }
    return {
      for (final courseId in courseIds)
        courseId: CourseProgressSummary(
          max: stepCounts[courseId] ?? 0,
          current: _contiguousCurrent(
            stepCounts[courseId] ?? 0,
            byCourse[courseId] ?? const [],
          ),
        ),
    };
  }

  /// The contiguous-run calculation shared by [courseProgressSummary] and
  /// [getCurrentProgress]. Marks a step complete when *any* progress row
  /// exists for it (no `passed` check), then walks from 1 until the first gap.
  int _contiguousCurrent(int stepsSize, List<CourseProgressRow> progresses) {
    if (stepsSize == 0) return 0;
    final completed = List<bool>.filled(stepsSize + 1, false);
    for (final progress in progresses) {
      final stepNum = progress.stepNum;
      if (stepNum >= 1 && stepNum <= stepsSize) {
        completed[stepNum] = true;
      }
    }
    var i = 1;
    while (i <= stepsSize && completed[i]) {
      i++;
    }
    return i - 1;
  }

  /// Port of `ProgressRepositoryImpl.isCourseCertified(courseId)`.
  Future<bool> isCourseCertified(String courseId) =>
      _certificationDao.countByCourseId(courseId).then((n) => n > 0);

  /// Port of `ProgressRepositoryImpl.saveCourseProgress`.
  ///
  /// Finds the row for `(courseId, userId, stepNum)` and refreshes its
  /// metadata (the Kotlin upserts even when the row exists, so a re-open
  /// bumps `updatedDate`). The `passed` flag is set only when [passed] is
  /// non-null: the step-view path passes `null` (an exam will grade it later)
  /// and a step with no exam passes `true` (auto-passed). An existing
  /// `passed=true` is therefore never clobbered by a re-open.
  ///
  /// [planetCode] is stored in the `createdOn` column, matching the Kotlin's
  /// `courseProgress.createdOn = planetCode` — the field is the user's planet
  /// code, not a timestamp, despite the name.
  ///
  /// [id] is the locally-minted key for a new row. The Kotlin mints a UUID
  /// internally; the port takes one from the caller so the take-course screen
  /// (and tests) can predict the row's identity.
  Future<void> saveCourseProgress({
    required String id,
    required String courseId,
    required String? userId,
    required int stepNum,
    String? planetCode,
    String? parentCode,
    bool? passed,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final existing = await _progressDao.findByCourseUserAndStep(
      courseId,
      userId,
      stepNum,
    );
    await _progressDao.upsert(
      CourseProgressCompanion.insert(
        id: existing?.id ?? id,
        couchId: Value(existing?.couchId),
        rev: Value(existing?.rev),
        // `passed` is set only when the caller says so; otherwise the existing
        // value is preserved (or the column default `false` for a new row).
        passed: Value(passed ?? existing?.passed ?? false),
        createdOn: Value(planetCode),
        createdDate: Value(existing?.createdDate ?? now),
        updatedDate: Value(now),
        stepNum: Value(stepNum),
        userId: Value(userId),
        courseId: Value(courseId),
        parentCode: Value(parentCode),
      ),
    );
  }

  /// Port of `CoursesRepositoryImpl.updateCourseProgress`.
  ///
  /// Flips `passed` to [passed] for every user's row on `(courseId, stepNum)`,
  /// matching the Kotlin `updatePassedByCourseAndStep`. An exam result is not
  /// per-user, so the row the take-course screen created for the current user
  /// is updated in place; a row that does not exist yet (the exam was taken
  /// before the step was opened) is created keyed by the current user.
  Future<void> updateCourseProgress({
    required String courseId,
    required int stepNum,
    required bool passed,
    String? userId,
    String? parentCode,
  }) async {
    await _progressDao.updatePassedByCourseAndStep(courseId, stepNum, passed);
    if (userId != null && userId.isNotEmpty) {
      final existing = await _progressDao.findByCourseUserAndStep(
        courseId,
        userId,
        stepNum,
      );
      if (existing == null) {
        final now = DateTime.now().millisecondsSinceEpoch;
        await _progressDao.upsert(
          CourseProgressCompanion.insert(
            id: '${courseId}_$stepNum',
            couchId: const Value(null),
            createdOn: Value(parentCode),
            createdDate: Value(now),
            updatedDate: Value(now),
            stepNum: Value(stepNum),
            passed: Value(passed),
            userId: Value(userId),
            courseId: Value(courseId),
            parentCode: Value(parentCode),
          ),
        );
      }
    }
  }

  /// Port of `ProgressRepositoryImpl.insertCourseProgressFromSync`.
  ///
  /// Merges each synced document onto an existing local row (by `_id`, else by
  /// the `(courseId, userId, stepNum)` triple) rather than blind-upserting, so
  /// a re-sync does not create twin rows and a local `passed=true` survives.
  /// There is intentionally **no** `deleteNotIn` cleanup: the Kotlin
  /// `courses_progress` sync (`TransactionSyncManager`) does not run one, so a
  /// row that drops off the server lingers locally — replicating that here.
  Future<int> insertCourseProgressFromSync(
    List<Map<String, dynamic>> docs,
  ) async {
    final companions = <CourseProgressCompanion>[];
    for (final doc in docs) {
      final docId = doc['_id']?.toString() ?? '';
      if (docId.isEmpty || docId.startsWith('_design/')) continue;

      final courseId = doc['courseId']?.toString();
      final userId = doc['userId']?.toString();
      final stepNum = (doc['stepNum'] is int)
          ? doc['stepNum'] as int
          : int.tryParse('${doc['stepNum']}') ?? 0;

      final existing = (await _progressDao.getByIds([docId])).firstOrNull;
      final localRecord =
          existing ??
          (courseId != null && userId != null
              ? (await _progressDao.findByCourseUserAndStep(
                  courseId,
                  userId,
                  stepNum,
                ))
              : null);

      companions.add(
        CourseProgressMapper.fromDoc(
          doc,
          existing: existing,
          localRecord: localRecord,
        ),
      );
    }
    if (companions.isEmpty) return 0;
    await _progressDao.upsertAll(companions);
    return companions.length;
  }

  Future<void> upsertCertifications(List<CertificationsCompanion> rows) =>
      _certificationDao.upsertAll(rows);

  /// Port of the `courses_progress` pull in
  /// `services/sync/TransactionSyncManager.kt`'s `syncDb`.
  ///
  /// Paginates `_all_docs` with a batch size of 200 (the Kotlin's page size for
  /// this table) and merges each page via [insertCourseProgressFromSync]. There
  /// is deliberately **no** `deleteNotIn` cleanup — the Kotlin does not run one
  /// for `courses_progress`, so a row that drops off the server lingers locally,
  /// and replicating that here keeps the behaviour identical. A locally-authored
  /// row that the server has not echoed back would be discarded by a cleanup,
  /// which is exactly the data the preserved-table rule exists to protect.
  Future<SyncResult> syncCourseProgress({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    return _pullTable(
      config: config,
      table: 'courses_progress',
      batchSize: 200,
      onProgress: onProgress,
      insert: (docs) => insertCourseProgressFromSync(docs),
    );
  }

  /// Port of the `certifications` pull in `TransactionSyncManager.syncDb`.
  ///
  /// Certifications are a pure server cache, so unlike `courses_progress` the
  /// stale-row cleanup **does** run: a certification the server no longer lists
  /// is dropped, and the next sync refills it. The default page size of 1000
  /// matches the Kotlin's `else` branch.
  Future<SyncResult> syncCertifications({
    required ServerConfig config,
    void Function(SyncProgress)? onProgress,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);

    final countResult = await _api.getJsonObject(
      '$dbUrl/certifications/_all_docs?limit=0',
      authHeader: authHeader,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final totalRows = JsonUtils.getInt('total_rows', countResult.data);
    if (totalRows == 0) {
      await _certificationDao.deleteNotIn(const []);
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: 1000);
    final savedIds = <String>[];
    var skip = 0;
    var walkedEveryPage = true;

    while (skip < totalRows) {
      final batchSize = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();
      final pageResult = await _api.getJsonObject(
        '$dbUrl/certifications/_all_docs?include_docs=true'
        '&limit=$batchSize&skip=$skip',
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

      final companions = <CertificationsCompanion>[];
      for (final row in rows) {
        if (row is! Map<String, dynamic>) continue;
        final doc = JsonUtils.getObject('doc', row);
        if (doc == null) continue;
        final parsed = CertificationMapper.fromDoc(doc);
        if (parsed == null) continue;
        companions.add(parsed);
        savedIds.add(parsed.id.value);
      }
      if (companions.isNotEmpty) {
        await _certificationDao.upsertAll(companions);
      }

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
      if (rows.length < batchSize) {
        walkedEveryPage = false;
        break;
      }
    }

    if (walkedEveryPage && savedIds.isNotEmpty) {
      await _certificationDao.deleteNotIn(savedIds);
    }
    return SyncComplete(savedIds.length);
  }

  /// Shared pagination loop for `_all_docs` pulls whose insert step takes raw
  /// docs. Used by [syncCourseProgress]; `syncCertifications` has its own body
  /// because it counts ids for the cleanup and parses with a different mapper.
  Future<SyncResult> _pullTable({
    required ServerConfig config,
    required String table,
    required int batchSize,
    required void Function(SyncProgress)? onProgress,
    required Future<int> Function(List<Map<String, dynamic>> docs) insert,
  }) async {
    final dbUrl = UrlUtils.dbUrl(config);
    final authHeader = UrlUtils.basicAuthHeader('satellite', config.pin);

    final countResult = await _api.getJsonObject(
      '$dbUrl/$table/_all_docs?limit=0',
      authHeader: authHeader,
    );
    if (countResult is! NetworkSuccess<Map<String, dynamic>>) {
      return SyncFailed(describeNetworkFailure(countResult));
    }
    final totalRows = JsonUtils.getInt('total_rows', countResult.data);
    if (totalRows == 0) {
      onProgress?.call(const SyncProgress(completed: 0, total: 0));
      return const SyncComplete(0);
    }

    final batchSizer = AdaptiveBatchProcessor(initialSize: batchSize);
    var skip = 0;
    var totalSaved = 0;

    while (skip < totalRows) {
      final size = batchSizer.currentSize;
      final stopwatch = Stopwatch()..start();
      final pageResult = await _api.getJsonObject(
        '$dbUrl/$table/_all_docs?include_docs=true&limit=$size&skip=$skip',
        authHeader: authHeader,
      );
      stopwatch.stop();

      if (pageResult is! NetworkSuccess<Map<String, dynamic>>) {
        batchSizer.recordFailure();
        return SyncFailed(describeNetworkFailure(pageResult));
      }
      batchSizer.recordSuccess(stopwatch.elapsedMilliseconds);

      final rows = pageResult.data['rows'];
      if (rows is! List || rows.isEmpty) break;

      final docs = <Map<String, dynamic>>[
        for (final row in rows)
          if (row is Map<String, dynamic>)
            JsonUtils.getObject('doc', row) ?? const <String, dynamic>{},
      ];
      totalSaved += await insert(docs);

      skip += rows.length;
      onProgress?.call(
        SyncProgress(
          completed: skip > totalRows ? totalRows : skip,
          total: totalRows,
        ),
      );
      if (rows.length < size) break;
    }
    return SyncComplete(totalSaved);
  }
}

/// One step's worth of progress, for the course-progress grid.
class CourseStepProgress {
  const CourseStepProgress({
    required this.step,
    required this.progress,
    required this.questionCount,
    required this.totalMistakes,
  });

  final CourseStepRow step;
  final CourseProgressRow? progress;
  final int questionCount;
  final int totalMistakes;

  bool get passed => progress?.passed ?? false;
}

/// The list-view progress summary for one course: [max] is its step count,
/// [current] the contiguous run of steps (from step 1) that have a progress
/// row. Mirrors the Kotlin `progressMap[courseId] = {max, current}`.
class CourseProgressSummary {
  const CourseProgressSummary({required this.max, required this.current});

  final int max;
  final int current;

  /// `max` falling back to the course's step count when the server map carried
  /// none — the Kotlin's `p?.get("max")?.takeIf { it > 0 } ?: course.getNumberOfSteps()`.
  /// Here `max` is already the step count, so the fallback is implicit; this
  /// exists so the filter predicates read identically to the Kotlin source.
  int get effectiveMax => max;
}

/// A completed course: its id and title, for the home dashboard's star badge
/// row. Port of `model/CourseCompletion.kt`.
class CourseCompletion {
  const CourseCompletion({required this.courseId, required this.courseTitle});

  final String courseId;
  final String courseTitle;
}
