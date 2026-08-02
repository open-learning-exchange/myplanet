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
