import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:meta/meta.dart';

import '../core/config/server_config.dart';
import '../core/network/network_result.dart';
import '../core/sync/sync_result.dart';
import '../core/utils/url_utils.dart';
import '../data/local/app_database.dart';
import 'app_providers.dart';
import 'session_provider.dart';
import 'sync_state.dart';

/// The two filter spinners and the search box on the courses screen, ported
/// from the fields `CoursesFragment` keeps for them.
class CourseFilter {
  const CourseFilter({
    this.query = '',
    this.gradeLevel,
    this.subjectLevel,
    this.myCoursesOnly = false,
  });

  final String query;
  final String? gradeLevel;
  final String? subjectLevel;

  /// The "my courses" / "all courses" toggle.
  final bool myCoursesOnly;

  CourseFilter copyWith({
    String? query,
    String? gradeLevel,
    String? subjectLevel,
    bool? myCoursesOnly,
    bool clearGradeLevel = false,
    bool clearSubjectLevel = false,
  }) {
    return CourseFilter(
      query: query ?? this.query,
      gradeLevel: clearGradeLevel ? null : (gradeLevel ?? this.gradeLevel),
      subjectLevel: clearSubjectLevel
          ? null
          : (subjectLevel ?? this.subjectLevel),
      myCoursesOnly: myCoursesOnly ?? this.myCoursesOnly,
    );
  }
}

class CourseFilterNotifier extends Notifier<CourseFilter> {
  @override
  CourseFilter build() => const CourseFilter();

  void setQuery(String query) => state = state.copyWith(query: query);

  void setGradeLevel(String? gradeLevel) => state = state.copyWith(
    gradeLevel: gradeLevel,
    clearGradeLevel: gradeLevel == null,
  );

  void setSubjectLevel(String? subjectLevel) => state = state.copyWith(
    subjectLevel: subjectLevel,
    clearSubjectLevel: subjectLevel == null,
  );

  void setMyCoursesOnly(bool value) =>
      state = state.copyWith(myCoursesOnly: value);

  void clear() => state = const CourseFilter();
}

/// The progress filter spinner on the courses screen - `""` means "All".
///
/// Port of `CourseFilterController`'s `spnProgress` selection, whose values
/// are the `progress_filter` array: All / Not Started / In Progress / Completed.
enum CourseProgressFilter {
  all,
  notStarted,
  inProgress,
  completed;

  static const _labels = {
    CourseProgressFilter.all: 'All',
    CourseProgressFilter.notStarted: 'Not Started',
    CourseProgressFilter.inProgress: 'In Progress',
    CourseProgressFilter.completed: 'Completed',
  };

  /// The Kotlin compares the spinner's `selectedItem.toString()` against these
  /// exact strings; the port keeps the same spelling so a future sync of
  /// server-side filters matches.
  String get label => _labels[this]!;
}

class CourseProgressFilterNotifier extends Notifier<CourseProgressFilter> {
  @override
  CourseProgressFilter build() => CourseProgressFilter.all;

  void set(CourseProgressFilter value) => state = value;
  void clear() => state = CourseProgressFilter.all;
}

final courseProgressFilterProvider =
    NotifierProvider<CourseProgressFilterNotifier, CourseProgressFilter>(
      CourseProgressFilterNotifier.new,
    );

/// Which column the courses list sorts by, and in which direction. Port of
/// `CoursesViewModel`'s `activeSort` / `isTitleAscending` / `isDateAscending`.
/// `field == null` means "no sort applied" - the stream's natural order,
/// matching the Kotlin's fall-through branch.
enum CourseSortField { title, date }

class CourseSortState {
  const CourseSortState({
    this.field,
    this.titleAscending = false,
    this.dateAscending = true,
  });

  final CourseSortField? field;

  // Per-field direction, persisted in state so a provider rebuild (hot reload,
  // dispose+recreate) does not reset a direction the user already chose. The
  // Kotlin keeps these as independent ViewModel fields for the same reason.
  final bool titleAscending;
  final bool dateAscending;

  /// The direction of the active field - what `sortCourses` reads.
  bool get ascending =>
      field == CourseSortField.title ? titleAscending : dateAscending;
}

class CourseSortNotifier extends Notifier<CourseSortState> {
  @override
  CourseSortState build() => const CourseSortState();

  /// Toggling a sort sets it active and flips its direction, exactly as
  /// `CoursesViewModel.toggleTitleSort`/`toggleDateSort` do: the same field
  /// flips asc/desc; the other field becomes active carrying its own last
  /// direction.
  void toggleTitle() {
    final s = state;
    state = CourseSortState(
      field: CourseSortField.title,
      titleAscending: !s.titleAscending,
      dateAscending: s.dateAscending,
    );
  }

  void toggleDate() {
    final s = state;
    state = CourseSortState(
      field: CourseSortField.date,
      titleAscending: s.titleAscending,
      dateAscending: !s.dateAscending,
    );
  }

  void clear() => state = const CourseSortState();
}

final courseSortProvider =
    NotifierProvider<CourseSortNotifier, CourseSortState>(
      CourseSortNotifier.new,
    );

final courseFilterProvider =
    NotifierProvider<CourseFilterNotifier, CourseFilter>(
      CourseFilterNotifier.new,
    );

/// Offline-first course list, filtered by [courseFilterProvider].
final coursesStreamProvider = StreamProvider<List<CourseRow>>((ref) {
  final filter = ref.watch(courseFilterProvider);
  final userId = ref.watch(sessionProvider).valueOrNull?.id;

  return ref
      .watch(coursesRepositoryProvider)
      .watchCourses(
        query: filter.query,
        shelfUserId: filter.myCoursesOnly ? userId : null,
        gradeLevel: filter.gradeLevel,
        subjectLevel: filter.subjectLevel,
      );
});

/// The courses list with the progress filter and sort applied. This is what
/// `CoursesScreen` renders.
///
/// Port of `CoursesViewModel`'s `filterCourses` + `sortCourses`, run together.
/// The sort state lives in [courseSortProvider] (surviving stream emissions,
/// not reset when the SQL-filtered list re-emits) and the progress filter in
/// [courseProgressFilterProvider]; both re-trigger this provider on change.
final filteredSortedCoursesProvider = StreamProvider<List<CourseRow>>((
  ref,
) async* {
  final courses = ref.watch(coursesStreamProvider);
  final sort = ref.watch(courseSortProvider);
  final progressFilter = ref.watch(courseProgressFilterProvider);

  final items = courses.valueOrNull;
  if (items == null || items.isEmpty) {
    yield items ?? const [];
    return;
  }

  // "All" is the common case - skip the progress query entirely, matching the
  // Kotlin's `if (progressFilter.isEmpty() || progressMap == null) baseCourses`.
  var filtered = items;
  if (progressFilter != CourseProgressFilter.all) {
    final userId = ref.watch(sessionProvider).valueOrNull?.id;
    final summary = await ref
        .watch(progressRepositoryProvider)
        .courseProgressSummary([for (final c in items) c.id], userId);
    filtered = items.where((course) {
      final s = summary[course.id];
      final current = s?.current ?? 0;
      final max = s?.effectiveMax ?? 0;
      switch (progressFilter) {
        case CourseProgressFilter.notStarted:
          return current == 0;
        case CourseProgressFilter.inProgress:
          return current > 0 && (max == 0 || current < max);
        case CourseProgressFilter.completed:
          return max > 0 && current >= max;
        case CourseProgressFilter.all:
          return true;
      }
    }).toList();
  }

  yield _sortCourses(filtered, sort);
});

List<CourseRow> _sortCourses(List<CourseRow> courses, CourseSortState sort) {
  final field = sort.field;
  if (field == null) return courses;
  final sorted = [...courses];
  switch (field) {
    case CourseSortField.title:
      sorted.sort(
        (a, b) => (a.courseTitle ?? '').toLowerCase().compareTo(
          (b.courseTitle ?? '').toLowerCase(),
        ),
      );
    case CourseSortField.date:
      sorted.sort((a, b) => a.createdDate.compareTo(b.createdDate));
  }
  return sort.ascending ? sorted : sorted.reversed.toList();
}

/// A single course, for the detail screen.
final courseProvider = StreamProvider.family<CourseRow?, String>((ref, id) {
  return ref.watch(coursesRepositoryProvider).watchCourse(id);
});

/// The steps of a single course, in order.
final courseStepsProvider = StreamProvider.family<List<CourseStepRow>, String>((
  ref,
  id,
) {
  return ref.watch(coursesRepositoryProvider).watchSteps(id);
});

/// The exam attached to a course step, or null if the step has none. Drives
/// the "Take test" button on the step content view and on the course detail
/// screen — both entries into `TakeExamScreen`.
///
/// Port of `ExamDao.getFirstByStepId`, which is `… WHERE stepId = :stepId
/// LIMIT 1`. Deliberately **not** `ExamDao.getByStepId`, whose
/// `getSingleOrNull` throws when two rows share a step id: the `courses` walk
/// writes one exam per step, but a standalone `exams` document is free to carry
/// a `stepId` key naming the same step, and a screen that throws out of `build`
/// is worse than one that takes the first row, which is what the Kotlin does.
final stepExamProvider = FutureProvider.family<ExamRow?, String>((
  ref,
  stepId,
) async {
  final db = ref.watch(appDatabaseProvider);
  final rows = await db.examDao.getByStepIds([stepId]);
  return rows.isEmpty ? null : rows.first;
});

/// The surveys attached to a course step. Drives the "Take survey" button.
final stepSurveysProvider = FutureProvider.family<List<SurveyRow>, String>((
  ref,
  stepId,
) async {
  final db = ref.watch(appDatabaseProvider);
  return db.surveyDao.getByStepId(stepId);
});

/// Distinct grade levels present locally, for the filter spinner.
///
/// Deliberately *not* derived from [coursesStreamProvider]: that watches
/// [courseFilterProvider], so choosing a grade would invalidate the option list
/// it was chosen from. While it reloaded the dropdown would briefly see an empty
/// item list with a non-null selection, which trips a `DropdownButton` assert.
final gradeLevelsProvider = StreamProvider<List<String>>((ref) {
  return ref.watch(coursesRepositoryProvider).watchGradeLevels();
});

/// Distinct subject levels present locally, for the filter spinner.
final subjectLevelsProvider = StreamProvider<List<String>>((ref) {
  return ref.watch(coursesRepositoryProvider).watchSubjectLevels();
});

class CourseSyncNotifier extends SyncNotifier {
  @override
  Future<SyncResult> runSync(
    ServerConfig config,
    void Function(SyncProgress) onProgress,
  ) async {
    final courses = ref.read(coursesRepositoryProvider);
    final progress = ref.read(progressRepositoryProvider);
    final tags = ref.read(tagsRepositoryProvider);

    // The four CouchDB caches are independent tables, so the pulls run
    // concurrently. A "sync courses" refreshes progress and certifications in
    // the same pass - the take-course view reads them together, and a stale
    // `certification` row is what gates the "certified" badge. Tags ride
    // along because the courses screen's collections filter reads them
    // (the Kotlin pulls `tags` in every full sync).
    final courseResult = courses.sync(config: config, onProgress: onProgress);
    final progressResult = progress.syncCourseProgress(
      config: config,
      onProgress: onProgress,
    );
    final certResult = progress.syncCertifications(
      config: config,
      onProgress: onProgress,
    );
    final tagsResult = tags.sync(config: config, onProgress: onProgress);

    final [a, b, c, d] = await Future.wait([
      courseResult,
      progressResult,
      certResult,
      tagsResult,
    ]);
    final totalSaved = [
      a,
      b,
      c,
      d,
    ].fold<int>(0, (sum, r) => sum + (r is SyncComplete ? r.savedCount : 0));
    final failed = [a, b, c, d].whereType<SyncFailed>().firstOrNull;
    return failed ?? SyncComplete(totalSaved);
  }
}

final courseSyncProvider = NotifierProvider<CourseSyncNotifier, SyncUiState>(
  CourseSyncNotifier.new,
);

/// Port of `model/CoursesProgressRow` from the Kotlin app.
///
/// Represents a single row in the course progress list.
class CourseProgressRow {
  const CourseProgressRow({
    required this.courseId,
    required this.courseName,
    this.progressCurrent,
    this.progressMax,
    this.mistakes,
    this.stepMistakes,
  });

  final String courseId;
  final String courseName;
  final int? progressCurrent;
  final int? progressMax;
  final int? mistakes;
  final Map<String, int>? stepMistakes;
}

/// Course progress data for the current user.
///
/// Combines course enrollment, step completion, and exam submissions
/// to compute progress statistics.
final courseProgressStreamProvider = StreamProvider<List<CourseProgressRow>>((
  ref,
) async* {
  final userId = ref.watch(sessionProvider).valueOrNull?.id;
  final coursesRepo = ref.watch(coursesRepositoryProvider);

  // Watch user's courses (shelf membership)
  final myCourses = await coursesRepo.watchCourses(shelfUserId: userId).first;
  if (myCourses.isEmpty) {
    yield [];
    return;
  }

  final courseIds = myCourses.map((c) => c.id).toList();

  // The contiguous-run progress (current) and step count (max) come from the
  // same repository method the courses list uses, so the "My Progress" grid and
  // the list's progress filter agree on what "current" means — a step the user
  // opened counts, regardless of `passed`, walked from step 1 until the first
  // gap. Previously this grid counted submissions, which over-reported
  // (re-taking an exam inflated "current") and bore no relation to the list.
  final progress = ref.watch(progressRepositoryProvider);
  final summary = await progress.courseProgressSummary(courseIds, userId);

  final db = ref.watch(appDatabaseProvider);

  // Get submissions for the user — used only for the per-step mistake counts.
  final submissions = await db.submissionDao.watchForUser(userId ?? '').first;
  final submissionsByCourse = <String, List<SubmissionRow>>{};
  for (final sub in submissions) {
    if (sub.parentId != null) {
      for (final courseId in courseIds) {
        if (sub.parentId!.contains(courseId)) {
          submissionsByCourse.putIfAbsent(courseId, () => []).add(sub);
        }
      }
    }
  }

  // Calculate mistakes per course
  final mistakesByCourse = <String, int>{};
  final stepMistakesByCourse = <String, Map<String, int>>{};
  for (final courseId in courseIds) {
    int totalMistakes = 0;
    final stepMistakes = <String, int>{};
    final subs = submissionsByCourse[courseId] ?? [];
    for (final sub in subs) {
      final answers = await db.submissionDao.answersFor(sub.id);
      for (final answer in answers) {
        totalMistakes += answer.mistakes;
        if (answer.examId != null) {
          stepMistakes[answer.examId!] = answer.mistakes;
        }
      }
    }
    mistakesByCourse[courseId] = totalMistakes;
    if (stepMistakes.isNotEmpty) {
      stepMistakesByCourse[courseId] = stepMistakes;
    }
  }

  // Build result rows
  final rows = <CourseProgressRow>[];
  for (final course in myCourses) {
    final s = summary[course.id];
    rows.add(
      CourseProgressRow(
        courseId: course.id,
        courseName: course.courseTitle ?? 'Untitled Course',
        progressCurrent: s?.current,
        progressMax: s?.max,
        mistakes: mistakesByCourse[course.id] ?? 0,
        stepMistakes: stepMistakesByCourse[course.id],
      ),
    );
  }

  yield rows;
});

/// A course cover image fetched from the CouchDB `courses` attachment endpoint
/// behind Basic auth — the same pattern as [profileImageProvider]. A course's
/// `coverFileName` is persisted on the row (mapped from the CouchDB doc by
/// `CourseMapper`); the URL is rebuilt here against the current
/// [serverConfigProvider] and fetched as bytes through [PlanetApi.getBytes].
///
/// Port of `CoursesAdapter.bindCover` (818732139). Returns `null` when there
/// is no attachment, the config is absent, or the fetch fails — the grid tile
/// falls back to the subject-tinted icon.
final courseCoverImageProvider =
    FutureProvider.family<Uint8List?, CourseCoverImageRequest>((
      ref,
      key,
    ) async {
      if (key.courseId.isEmpty || key.coverFileName.isEmpty) return null;
      final config = ref.watch(serverConfigProvider);
      if (config == null) return null;
      final url = UrlUtils.courseImageUrl(
        config,
        key.courseId,
        key.coverFileName,
      );
      if (url == null) return null;
      final authHeader = UrlUtils.authHeader(config);
      final result = await ref
          .watch(planetApiProvider)
          .getBytes(url, authHeader: authHeader);
      return switch (result) {
        NetworkSuccess<List<int>>(:final data) => Uint8List.fromList(data),
        NetworkError<List<int>>() => null,
        NetworkException<List<int>>() => null,
      };
    });

/// The key for [courseCoverImageProvider] — the course's local id and the
/// CouchDB attachment name.
@immutable
class CourseCoverImageRequest {
  const CourseCoverImageRequest({
    required this.courseId,
    required this.coverFileName,
  });

  final String courseId;
  final String coverFileName;

  @override
  bool operator ==(Object other) =>
      other is CourseCoverImageRequest &&
      other.courseId == courseId &&
      other.coverFileName == coverFileName;

  @override
  int get hashCode => Object.hash(courseId, coverFileName);
}
