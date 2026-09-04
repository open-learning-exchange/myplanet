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
/// shape closely enough to share the same bugs, deliberately — including the
/// inconsistency between them: a step counts as "current" as soon as a
/// progress row exists for it, **whatever its `passed` flag**, while a course
/// is "complete" only when every step is `passed`. Step-exam submissions are
/// read for the share of each step exam the learner has **answered**, which is
/// the only number the Kotlin grid renders.
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

  /// Port of `CoursesRepositoryImpl.getCourseProgress(courseId, userId)`
  /// (`CoursesRepositoryImpl.kt:424-473`) — the data behind
  /// `CourseProgressActivity`'s per-step grid.
  ///
  /// One cell per step, in `stepIndex` order, carrying exactly what
  /// `ProgressGridAdapter` binds: the share of the step exam's questions the
  /// learner has answered, and whether that share is all of them. A step with
  /// no exam, or an exam with no submission, gets a cell with **no**
  /// percentage at all — `ProgressGridAdapter.onBindViewHolder` distinguishes
  /// that state from "0%" by `item.has("percentage")` and paints it in the
  /// main colour with no text.
  ///
  /// Deliberately *not* carried here: the per-step mistake total this method
  /// used to return. Nothing in the Kotlin grid renders mistakes — they belong
  /// to the My Progress **list** row (`ProgressRepositoryImpl.submissionMap` →
  /// `CoursesProgressAdapter.showStepMistakes`), which the port already draws
  /// from `courseProgressStreamProvider`. Returning them from here as well
  /// would recreate the "computed and rendered nowhere" shape this method was
  /// stuck in for three phases. Nor is `getExamObject`'s `status`, which the
  /// Kotlin writes into the JsonObject and no view ever reads.
  Future<CourseProgressData> courseProgress(
    String courseId,
    String? userId,
  ) async {
    final steps = await _courseDao.getSteps(courseId);
    // `getCourseProgress` reads `current` from the same
    // `ProgressRepositoryImpl.getCurrentProgress` the take-course bar uses, so
    // the ring and the bar cannot disagree.
    final current = await getCurrentProgress(steps, userId, courseId);
    final title = (await _courseDao.getById(courseId))?.courseTitle;

    final stepIds = [for (final step in steps) step.id];
    final stepExams = stepIds.isEmpty
        ? const <ExamRow>[]
        : await _examDao.getByStepIds(stepIds);
    // Kotlin groups (`allExams.groupBy { it.stepId }`) rather than keying one
    // exam per step: `adoptSurvey` copies `stepId` onto a new row, so a step
    // can legitimately carry more than one, and `getExamObject` folds all of
    // them into the single cell.
    final examsByStep = <String?, List<ExamRow>>{};
    for (final exam in stepExams) {
      examsByStep.putIfAbsent(exam.stepId, () => <ExamRow>[]).add(exam);
    }

    final examIds = [for (final exam in stepExams) exam.id];
    final questionCountByExam = <String, int>{};
    for (final question in await _examDao.questionsForExams(examIds)) {
      questionCountByExam[question.examId] =
          (questionCountByExam[question.examId] ?? 0) + 1;
    }

    // `submissionDao.getExamSubmissionsByUser(userId)` then
    // `.filter { examIdsSet.contains(getParentBaseId(sub.parentId)) }` —
    // every `exam`-typed submission of this user whose parent is one of these
    // exams. A submission for another course's exam is dropped here.
    final examIdSet = examIds.toSet();
    final relevantSubmissions = <SubmissionRow>[];
    for (final submission in await _submissionDao.getExamSubmissionsByUser(
      userId,
    )) {
      if (examIdSet.contains(_examIdFromParent(submission.parentId))) {
        relevantSubmissions.add(submission);
      }
    }
    final submissionsByExam = <String, List<SubmissionRow>>{};
    for (final submission in relevantSubmissions) {
      final examId = _examIdFromParent(submission.parentId);
      if (examId != null && examId.isNotEmpty) {
        submissionsByExam
            .putIfAbsent(examId, () => <SubmissionRow>[])
            .add(submission);
      }
    }

    final answerCountBySubmission = <String, int>{};
    for (final answer in await _submissionDao.answersForSubmissions([
      for (final submission in relevantSubmissions) submission.id,
    ])) {
      answerCountBySubmission[answer.submissionId] =
          (answerCountBySubmission[answer.submissionId] ?? 0) + 1;
    }

    return CourseProgressData(
      title: title,
      current: current,
      max: steps.length,
      steps: [
        for (final step in steps)
          _stepCell(
            step: step,
            exams: examsByStep[step.id] ?? const <ExamRow>[],
            submissionsByExam: submissionsByExam,
            questionCountByExam: questionCountByExam,
            answerCountBySubmission: answerCountBySubmission,
          ),
      ],
    );
  }

  /// Port of `CoursesRepositoryImpl.getExamObject` for one step
  /// (`CoursesRepositoryImpl.kt:476-503`), whose write semantics are the whole
  /// specification of a cell and are easy to get wrong:
  ///
  ///  * it loops exams, then that exam's submissions, writing into **one**
  ///    JsonObject — so with several submissions the **last** wins;
  ///  * the zero-question branch is guarded by `if (!ob.has(...))`, so it
  ///    cannot overwrite a real percentage an earlier exam contributed, while
  ///    the with-questions branch is unguarded and always overwrites;
  ///  * an exam with no submission writes nothing, so a step whose only exam
  ///    is unattempted keeps `percentage == null`.
  ///
  /// [CourseStepProgress.percentage] is a `num` rather than a `double` on
  /// purpose: the zero-question branch writes the **integer** `0` and the
  /// other a `Double`, and `ProgressGridAdapter` renders the value through
  /// `item["percentage"].asString`, i.e. Gson's `getAsNumber().toString()`.
  /// So an exam with no questions reads "0%" while a fully answered one reads
  /// "100.0%". See [CourseStepProgress.percentageLabel].
  CourseStepProgress _stepCell({
    required CourseStepRow step,
    required List<ExamRow> exams,
    required Map<String, List<SubmissionRow>> submissionsByExam,
    required Map<String, int> questionCountByExam,
    required Map<String, int> answerCountBySubmission,
  }) {
    num? percentage;
    bool? completed;
    for (final exam in exams) {
      for (final submission
          in submissionsByExam[exam.id] ?? const <SubmissionRow>[]) {
        final answerCount = answerCountBySubmission[submission.id] ?? 0;
        final questionCount = questionCountByExam[exam.id] ?? 0;
        if (questionCount == 0) {
          completed ??= false;
          percentage ??= 0;
        } else {
          completed = answerCount == questionCount;
          percentage = (answerCount / questionCount) * 100;
        }
      }
    }
    return CourseStepProgress(
      stepId: step.id,
      percentage: percentage,
      completed: completed,
    );
  }

  /// The leading `@`-delimited segment of `parentId`, matching the Kotlin's
  /// `getParentBaseId` — `if (parentId?.contains("@") == true)
  /// parentId.split("@")[0] else parentId`.
  String? _examIdFromParent(String? parentId) {
    if (parentId == null) return null;
    final at = parentId.indexOf('@');
    return at < 0 ? parentId : parentId.substring(0, at);
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

  /// Port of `ProgressRepositoryImpl.getCurrentProgress` — the take-course
  /// progress bar.
  ///
  /// Counts the contiguous run of steps **from step 1** that have a progress
  /// row, **ignoring `passed`** — a step the user merely opened counts as
  /// "current", which `ProgressRepositoryImplTest.kt:130-146` pins with two
  /// `passed = false` rows and an expected `current` of 2. It measures how far
  /// the learner has reached, not what they have passed, and so deliberately
  /// disagrees with [completedCourseIds], which does require `passed`.
  ///
  /// [courseProgress] calls this for the header of its grid, so the ring and
  /// the take-course bar cannot drift apart.
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

  /// Port of the `fetchCourseData` + `findProgressForCourse` pair the
  /// challenge dialog uses. The Kotlin builds a JsonArray of all the user's
  /// courses with their progress, then searches it for the challenge course;
  /// here the caller already knows the course id, so a single-course lookup
  /// is the same result without the array.
  ///
  /// Returns null when the course has no steps (the Kotlin's
  /// `findProgressForCourse` returns null when no entry matches).
  Future<CourseProgressSummary?> courseProgressForChallenge(
    String? userId,
    String courseId,
  ) async {
    final map = await courseProgressSummary([courseId], userId);
    final summary = map[courseId];
    if (summary == null || summary.max == 0) return null;
    return summary;
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
    final authHeader = UrlUtils.authHeader(config);

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
    final authHeader = UrlUtils.authHeader(config);

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

/// Port of `model/CourseProgressData.kt` — everything
/// `CourseProgressActivity.updateUI` binds.
///
/// The Kotlin carries [steps] as a `JsonArray` of loosely-typed objects whose
/// *absent* keys are load-bearing (`item.has("percentage")` selects the cell's
/// colour); [CourseStepProgress] makes that a nullable field instead.
class CourseProgressData {
  const CourseProgressData({
    required this.title,
    required this.current,
    required this.max,
    required this.steps,
  });

  final String? title;

  /// The contiguous run of opened steps, from `getCurrentProgress`.
  final int current;

  /// The course's step count.
  final int max;

  final List<CourseStepProgress> steps;

  /// The ring's value, as `CourseProgressActivity.updateUI` computes it:
  /// `(current / max * 100).toInt()`, and `0` when [max] is zero rather than
  /// a division by zero. The `toInt()` truncates — 1 of 3 steps shows 33.
  int get ringPercent => max == 0 ? 0 : (current / max * 100).truncate();
}

/// One grid cell: the step it belongs to, and the state
/// `ProgressGridAdapter.onBindViewHolder` reads.
///
/// Three distinguishable states, matching the adapter's branches exactly:
///
///  * [percentage] `null` — no exam, or none attempted. Painted in the main
///    colour with **no text** (the Kotlin's `else` branch never touches
///    `tvProgress`).
///  * [percentage] set and [completed] true — green.
///  * [percentage] set and [completed] false — yellow.
class CourseStepProgress {
  const CourseStepProgress({
    required this.stepId,
    required this.percentage,
    required this.completed,
  });

  final String stepId;

  /// The share of the step exam's questions answered, `null` when the Kotlin
  /// JsonObject would carry no `percentage` key. A `num`, not a `double`,
  /// because the zero-question branch writes an integer — see
  /// [percentageLabel].
  final num? percentage;

  /// Whether every question of the step's exam has an answer. `null` in
  /// lockstep with [percentage]: the Kotlin writes both keys or neither.
  final bool? completed;

  /// What `R.string.percentage` ("%s%%") is fed —
  /// `item["percentage"].asString`, i.e. Gson's `getAsNumber().toString()`.
  ///
  /// Reproduced rather than tidied: a Double stringifies with its fractional
  /// part, so a fully answered exam reads "100.0%" and two of three
  /// "66.66666666666666%" in the shipping app. Dart's `double.toString()` is
  /// the same shortest-round-trip algorithm as Java's `Double.toString`, and
  /// the integer `0` of the zero-question branch prints "0". Rounding here
  /// would make the port show numbers the Kotlin never shows.
  String? get percentageLabel => percentage?.toString();
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
