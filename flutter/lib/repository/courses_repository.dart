import '../core/config/server_config.dart';
import '../core/files/markdown_image_prefetcher.dart';
import '../core/network/network_result.dart';
import '../core/sync/adaptive_batch_processor.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/json_utils.dart';
import '../core/utils/markdown_links.dart';
import '../core/utils/url_utils.dart';
import '../data/api/planet_api.dart';
import '../data/local/app_database.dart';
import '../data/local/course_mapper.dart';
import '../data/local/exam_mapper.dart';
import '../data/local/my_library_mapper.dart';
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
    this._surveyDao, {
    MarkdownImagePrefetcher? markdownImages,
  }) : _markdownImages = markdownImages;

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

  /// Downloads the images a course or step description references, so they
  /// render offline. Optional so the repository's other tests need no stub;
  /// `coursesRepositoryProvider` always supplies one.
  final MarkdownImagePrefetcher? _markdownImages;

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
    // The markdown images this walk's descriptions reference. A `Set` because
    // one course commonly repeats a cover image across its steps, and because
    // Kotlin's collector is a `HashSet` too (`MyCourse.concatenatedLinks`).
    final markdownImageLinks = <String>{};
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
      // The resource documents embedded in this page's course steps.
      // `CourseDao.upsertAll` has already released every stamp these courses
      // held by the time these are written, so there is no keep set to carry:
      // what is not re-stamped stays released.
      final parsedResources = <CourseResourceDoc>[];
      final examRows = <ExamsCompanion>[];
      final examQuestionRows = <String, List<ExamQuestionsCompanion>>{};
      final surveyRows = <SurveysCompanion>[];
      final surveyQuestionRows = <String, List<SurveyQuestionsCompanion>>{};
      // Per course, the assessments its document still claims — anything else
      // attached to it is a step the author has removed.
      final examIdsByCourse = <String, Set<String>>{};
      final surveyIdsByCourse = <String, Set<String>>{};

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

        // Port of `buildCoursePayload`'s two `extractLinks` calls
        // (`CoursesRepositoryImpl:670` for the course description, `:684` for
        // each step's). Read from the raw document rather than from `parsed`
        // for the same reason the Kotlin reads its `JsonObject`: the step
        // description is on the document whether or not the mapper keeps it.
        markdownImageLinks.addAll(
          extractImageLinks(JsonUtils.getString('description', doc)),
        );
        final steps = doc['steps'];
        if (steps is List) {
          for (final step in steps) {
            if (step is! Map<String, dynamic>) continue;
            markdownImageLinks.addAll(
              extractImageLinks(JsonUtils.getString('description', step)),
            );
          }
        }

        parsedResources.addAll(parsed.resources);

        // A question map entry is written only when the embedded object
        // actually carried questions. `ExamDao.upsertAll` deletes an exam's
        // questions before reinserting the entry's list, so passing an empty
        // one for an embedded object with no `questions` array would wipe the
        // questions the `exams` database walk had written for the same exam —
        // leaving it openable with nothing in it. Kotlin's `questionDao` never
        // deletes, so skipping is the faithful half as well as the safe one.
        for (final mapping in ExamMapper.fromCourseDoc(
          doc,
          stepIdFor: CourseMapper.stepIdFor,
        )) {
          examRows.add(mapping.exam);
          examIdsByCourse
              .putIfAbsent(courseId, () => <String>{})
              .add(mapping.exam.id.value);
          if (mapping.questions.isNotEmpty) {
            examQuestionRows[mapping.exam.id.value] = mapping.questions;
          }
        }
        for (final mapping in SurveyMapper.fromCourseDoc(
          doc,
          stepIdFor: CourseMapper.stepIdFor,
        )) {
          surveyRows.add(mapping.survey);
          surveyIdsByCourse
              .putIfAbsent(courseId, () => <String>{})
              .add(mapping.survey.id.value);
          if (mapping.questions.isNotEmpty) {
            surveyQuestionRows[mapping.survey.id.value] = mapping.questions;
          }
        }
      }

      if (courseRows.isNotEmpty) {
        await _dao.upsertAll(courseRows, stepRows);
      }
      // Kotlin drains its buffered course resources here, per batch, from
      // inside the walk itself (`TransactionSyncManager.kt:302`) — not once at
      // the end — so a step's resources are readable before the sync finishes.
      await _ingestCourseResources(
        config: config,
        parsedResources: parsedResources,
      );
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
      // Retire the joins this page's course documents no longer claim. Runs
      // for every course on the page, not only those that still have an
      // assessment, so removing the last test from a course clears it too.
      for (final doc in docs) {
        final courseId = JsonUtils.getString('_id', doc);
        if (courseId.isEmpty) continue;
        await _examDao.releaseStepJoinsForCourse(
          courseId,
          examIdsByCourse[courseId] ?? const <String>{},
        );
        await _surveyDao.releaseStepJoinsForCourse(
          courseId,
          surveyIdsByCourse[courseId] ?? const <String>{},
        );
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

    // Kotlin queues each link as it parses the document and downloads the
    // whole set once the sync finishes (`SyncActivity:566`); the same order
    // here, so a course whose description names an image is readable offline
    // rather than only while the device is online. Runs after the walk even if
    // it ended short, because the links collected so far are still valid — and
    // never on the early-return failure paths above, where the next sync
    // re-collects them from the same documents.
    if (markdownImageLinks.isNotEmpty) {
      await _markdownImages?.prefetch(markdownImageLinks, config: config);
    }

    return SyncComplete(savedIds.length);
  }

  /// Writes one page's embedded step resources into `my_library`, stamped with
  /// the step and course they came from.
  ///
  /// Port of `flushPendingCourseResources` (`CoursesRepositoryImpl.kt:819-863`).
  /// The stamp is what makes a step's resources readable at all —
  /// `MyLibraryDao.getByStepId` and `getCourseResources` are the Kotlin
  /// readers, and before this the port held the *count* of a step's resources
  /// and none of the rows.
  ///
  /// Two properties are deliberate:
  ///
  /// * **The existing row is read first and its six merge lists passed back
  ///   in.** `my_library.userId` is the shelf, written by the user's own tap as
  ///   well as by a walk, and `MyLibraryMapper.fromDoc` writes
  ///   `Value(existingUserIds)` unconditionally — so ingesting a course
  ///   resource without this would retract a membership, the Phase 116 defect
  ///   in a new caller. No `shelfId` is passed, matching Kotlin: the drain
  ///   sends no `userId` at all, so `setUserId` returns early
  ///   (`MyLibrary.kt:123-130`) and course ingestion never *adds* membership
  ///   either.
  /// * **It runs after `CourseDao.upsertAll`, which has released every stamp
  ///   these courses held.** So this writes the current set and nothing has to
  ///   compute which old stamps to retire — a resource the document dropped is
  ///   one this pass does not re-stamp.
  Future<void> _ingestCourseResources({
    required ServerConfig config,
    required List<CourseResourceDoc> parsedResources,
  }) async {
    if (parsedResources.isEmpty) return;

    final existingById = {
      for (final row in await _dao.existingResources([
        for (final resource in parsedResources) resource.resourceId,
      ]))
        row.id: row,
    };

    final rows = <MyLibraryTableCompanion>[];
    for (final resource in parsedResources) {
      final existing = existingById[resource.resourceId];
      final companion = MyLibraryMapper.fromDoc(
        resource.doc,
        couchDbUrl: config.couchDbUrl,
        existingUserIds: existing?.userId ?? const [],
        existingResourceFor: existing?.resourceFor ?? const [],
        existingSubject: existing?.subject ?? const [],
        existingLevel: existing?.level ?? const [],
        existingTag: existing?.tag ?? const [],
        existingLanguages: existing?.languages ?? const [],
        stepId: resource.stepId,
        courseId: resource.courseId,
      );
      if (companion == null) continue;
      rows.add(companion);
    }

    await _dao.upsertCourseResources(rows);
  }
}
