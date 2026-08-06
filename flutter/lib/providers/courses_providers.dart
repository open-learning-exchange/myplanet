import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/config/server_config.dart';
import '../core/sync/sync_result.dart';
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
  ) {
    return ref
        .read(coursesRepositoryProvider)
        .sync(config: config, onProgress: onProgress);
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

  // Get steps and submissions in parallel
  final db = ref.watch(appDatabaseProvider);

  // Get all steps for these courses
  final allStepsFutures = courseIds.map((id) => coursesRepo.getCourseSteps(id));
  final allStepsList = await Future.wait(allStepsFutures);
  final stepsByCourse = <String, int>{};
  for (final steps in allStepsList) {
    for (final step in steps) {
      if (step.courseId != null) {
        stepsByCourse[step.courseId!] =
            (stepsByCourse[step.courseId!] ?? 0) + 1;
      }
    }
  }

  // Get submissions for the user
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
    final stepCount = stepsByCourse[course.id] ?? 0;
    final submissionCount = submissionsByCourse[course.id]?.length ?? 0;
    final progressCurrent = stepCount > 0
        ? submissionCount.clamp(0, stepCount)
        : 0;

    rows.add(
      CourseProgressRow(
        courseId: course.id,
        courseName: course.courseTitle ?? 'Untitled Course',
        progressCurrent: progressCurrent,
        progressMax: stepCount,
        mistakes: mistakesByCourse[course.id] ?? 0,
        stepMistakes: stepMistakesByCourse[course.id],
      ),
    );
  }

  yield rows;
});
